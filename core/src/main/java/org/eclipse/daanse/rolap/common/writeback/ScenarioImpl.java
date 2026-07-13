/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.writeback;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.olap.api.result.NotLoaded;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.AllocationPolicy;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.api.result.Scenario;
import org.eclipse.daanse.olap.api.result.WritebackCell;
import org.eclipse.daanse.olap.api.type.ScalarType;
import org.eclipse.daanse.olap.calc.base.nested.AbstractProfilingNestedUnknownCalc;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCalculatedMember;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScenarioImpl implements Scenario {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioImpl.class);

    private final int id;

    private final List<WritebackCell> writebackCells =
        new ArrayList<>();

    private String cubeName;

    private Member member;

    private static int nextId;

    private List<Map<String, Map.Entry<DataTypeJdbc, Object>>> sessionValues = new ArrayList<>();

    /**
     * Creates a ScenarioImpl.
 */
    public ScenarioImpl() {
        id = nextId++;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ScenarioImpl
            && id == ((ScenarioImpl) obj).id;
    }

    @Override
    public String toString() {
        return "scenario #" + id;
    }

    public String getCubeName() {
        return cubeName;
    }

    public void setCubeName(String cubeName) {
        this.cubeName = cubeName;
    }

    /**
     * Sets the value of a cell.
     *
     * @param connection Connection (not currently used)
     * @param members Coordinates of cell
     * @param newValue New value
     * @param currentValue Current value
     * @param allocationPolicy Allocation policy
     * @param allocationArgs Additional arguments of allocation policy
 */
    @Override
    public void setCellValue(
        Connection connection,
        List<Member> members,
        Object newValue,
        Object currentValue,
        AllocationPolicy allocationPolicy,
        Object[] allocationArgs)
    {
        LOGGER.debug("Writeback[scenario.setCellValue] members={} newValue={} currentValue={} policy={}",
                members.stream().map(Member::getUniqueName).toList(),
                newValue, currentValue, allocationPolicy);
        assert allocationArgs != null;

        // Resolve the writeback measure that backs this cell so we know its bind
        // datatype. If the target is text (TextAggMeasure → VARCHAR) we take a
        // short path that bypasses allocation and writes one row directly to
        // sessionValues.
        final RolapStoredMeasure measure = (RolapStoredMeasure) members.getFirst();
        final RolapCube baseCube = measure.getCube();
        final Optional<RolapWritebackTable> oWritebackTable = baseCube.getWritebackTable();
        RolapWritebackMeasure targetWb = null;
        if (oWritebackTable.isPresent()) {
            for (RolapWritebackColumn col : oWritebackTable.get().getColumns()) {
                if (col instanceof RolapWritebackMeasure rwm
                        && rwm.getMeasure().getUniqueName().equals(measure.getUniqueName())) {
                    targetWb = rwm;
                    break;
                }
            }
            if (targetWb == null) {
                LOGGER.warn(
                        "Writeback[scenario.setCellValue] cube '{}' has a writebackTable but no WritebackMeasure"
                                + " matches '{}'. Falling back to the numeric allocation path — if the measure is"
                                + " not numeric this is almost certainly a misconfigured catalog.",
                        baseCube.getName(), measure.getUniqueName());
            }
        }
        // INFO so the operator can see, per cell edit, whether the TEXT short
        // path or the NUMERIC allocation path is taken. Diagnoses "I typed
        // text in Excel but it went to the numeric path" misconfigurations.
        LOGGER.info(
                "Writeback[scenario.setCellValue] measure='{}' newValue='{}' resolvedWritebackMeasure={} datatype={}",
                measure.getUniqueName(), newValue,
                targetWb == null ? "<none>" : targetWb.getColumn().getName(),
                targetWb == null ? "<none>" : targetWb.getDatatype());
        if (targetWb != null && targetWb.getDatatype() == Datatype.VARCHAR) {
            LOGGER.info("Writeback[scenario.setCellValue] -> TEXT path for measure '{}' (column '{}')",
                    measure.getUniqueName(), targetWb.getColumn().getName());
            writeTextRow(oWritebackTable.get(), targetWb, members, newValue);
            return;
        }

        // Numeric path — keep allocation behavior unchanged.
        if (allocationPolicy == null) {
            // user error
            throw Util.newError("Allocation policy must not be null");
        }
        switch (allocationPolicy) {
            case EQUAL_ALLOCATION:
            case EQUAL_INCREMENT:
                if (allocationArgs.length != 0) {
                    throw Util.newError(
                        new StringBuilder("Allocation policy ").append(allocationPolicy)
                            .append(" takes 0 arguments; ").append(allocationArgs.length)
                            .append(" were supplied").toString());
                }
                break;
            default:
                throw Util.newError(
                    new StringBuilder("Allocation policy ")
                        .append(allocationPolicy).append(" is not supported").toString());
        }
        // Numeric narrowing only on this branch. RolapCell now hands us the raw
        // cell value untouched, so null / non-Number means "treat as zero" for
        // the purposes of allocation maths.
        final double doubleNewValue = newValue instanceof Number n ? n.doubleValue() : 0d;
        final double doubleCurrentValue = currentValue instanceof Number n ? n.doubleValue() : 0d;
        LOGGER.info("Writeback[scenario.setCellValue] -> NUMERIC path for measure '{}' newValue={} currentValue={}",
                measure.getUniqueName(), doubleNewValue, doubleCurrentValue);

        // Compute the set of columns which are constrained by the cell's
        // coordinates.
        //
        // NOTE: This code is very similar to code in
        // RolapAggregationManager.makeCellRequest. Consider creating a
        // CellRequest then mining it. It will work better in the presence of
        // calculated members, compound members, parent-child hierarchies,
        // hierarchies whose default member is not the 'all' member, and so
        // forth.
        // (`measure` and `baseCube` were resolved at the top of this method when
        // we looked up the target writeback measure's datatype.)
        final RolapStar.Measure starMeasure =
            (RolapStar.Measure) measure.getStarMeasure();
        assert starMeasure != null;
        int starColumnCount = starMeasure.getStar().getColumnCount();
        final BitKey constrainedColumnsBitKey =
            BitKey.Factory.makeBitKey(starColumnCount);
        Object[] keyValues = new Object[starColumnCount];
        for (int i = 1; i < members.size(); i++) {
            Member member = members.get(i);
            for (RolapCubeMember m = (RolapCubeMember) member;
                 m != null && !m.isAll();
                 m = m.getParentMember())
            {
                final RolapCubeLevel level = m.getLevel();
                RolapStar.Column column = level.getBaseStarKeyColumn(baseCube);
                if (column != null) {
                    final int bitPos = column.getBitPosition();
                    keyValues[bitPos] = m.getKey();
                    constrainedColumnsBitKey.set(bitPos);
                }
                if (level.areMembersUnique()) {
                    break;
                }
            }
        }

        // Squish the values down. We want the compactKeyValues[i] to correspond
        // to the i'th set bit in the key. This is the same format used by
        // CellRequest.
        Object[] compactKeyValues =
            new Object[constrainedColumnsBitKey.cardinality()];
        int k = 0;
        for (int bitPos : constrainedColumnsBitKey) {
            compactKeyValues[k++] = keyValues[bitPos];
        }

        // Record the override.
        //
        // TODO: add a mechanism for persisting the overrides to a file.
        //
        // FIXME: make thread-safe
        WritebackCellImpl writebackCell =
            new WritebackCellImpl(
                baseCube,
                new ArrayList<Member>(members),
                constrainedColumnsBitKey,
                compactKeyValues,
                doubleNewValue,
                doubleCurrentValue,
                allocationPolicy);
        writebackCells.add(writebackCell);
    }

    /**
     * Short-circuit writeback path for text-typed measures (e.g. a
     * TextAggMeasure). Builds a single row tied to the cell's exact dimensional
     * coordinates, fills only the target text column plus the writeback
     * attributes, and pushes it onto {@code sessionValues}. Allocation policies
     * are ignored — text cells are not "spread" across descendants the way
     * numeric values are; the per-level rollup happens on the read side via
     * the {@code TextAggMeasure}'s ListAgg aggregator.
 */
    private void writeTextRow(
            RolapWritebackTable writebackTable,
            RolapWritebackMeasure target,
            List<Member> members,
            Object value)
    {
        // SimpleEntry (not Map.entry) so that NULL values for non-target measures
        // are allowed — Map.entry() rejects null values.
        Map<String, Map.Entry<DataTypeJdbc, Object>> row = new LinkedHashMap<>();
        for (RolapWritebackColumn col : writebackTable.getColumns()) {
            if (col instanceof RolapWritebackAttribute attr) {
                // Find the member on members[1..N] that belongs to the attribute's
                // dimension. We walk parent chains so that members at any level of
                // a multi-level hierarchy still resolve to the leaf-key value the
                // fact table joins on.
                Object key = findKeyForDimension(attr, members);
                row.put(attr.getColumn().getName(),
                        new AbstractMap.SimpleEntry<>(DataTypeJdbc.VARCHAR, key));
                LOGGER.debug("Writeback[text]   attribute column='{}' key={}",
                        attr.getColumn().getName(), key);
            } else if (col instanceof RolapWritebackMeasure m && m == target) {
                row.put(m.getColumn().getName(),
                        new AbstractMap.SimpleEntry<>(DataTypeJdbc.VARCHAR, value));
                LOGGER.debug("Writeback[text]   measure   column='{}' value='{}'",
                        m.getColumn().getName(), value);
            } else if (col instanceof RolapWritebackMeasure other) {
                // Other measures must still appear in the row so the INSERT's
                // column/value lists line up. We write NULL using their native
                // bind datatype.
                DataTypeJdbc otherBind = other.getDatatype() == Datatype.VARCHAR
                        ? DataTypeJdbc.VARCHAR
                        : DataTypeJdbc.NUMERIC;
                row.put(other.getColumn().getName(),
                        new AbstractMap.SimpleEntry<>(otherBind, null));
                LOGGER.debug("Writeback[text]   measure   column='{}' value=NULL (other measure, datatype={})",
                        other.getColumn().getName(), otherBind);
            }
        }
        sessionValues.add(row);
        LOGGER.debug("Writeback[text] row pushed to sessionValues (now {} pending rows)",
                sessionValues.size());
    }

    /**
     * Resolves the database key value to write for a writeback attribute, given
     * the cell's coordinate. The cell's selected member <em>is</em> the
     * coordinate — we just unwrap its key.
     *
     * Returning {@code null} for the "all" level (or for a missing member)
     * produces a {@code NULL} value in the INSERT, which the fact-side FK
     * column must allow if that path is exercised.
     *
     * Works uniformly for single-level explicit hierarchies, multi-level
     * snowflake hierarchies and parent-child hierarchies — for parent-child,
     * intermediate (non-leaf) members are written verbatim because that's
     * exactly what the user clicked on; the read-side {@code TextAggMeasure}
     * aggregator rolls descendants in via the standard SQL aggregation path.
 */
    private Object findKeyForDimension(RolapWritebackAttribute attr, List<Member> members) {
        String dimName = attr.getDimension().getName();
        for (int i = 1; i < members.size(); i++) {
            Member member = members.get(i);
            if (member.getDimension().getName().equals(dimName)) {
                if (member.isAll()) {
                    return null;
                }
                return (member instanceof RolapCubeMember rcm) ? rcm.getKey() : member.getName();
            }
        }
        return null;
    }

    @Override
    public String getId() {
        return Integer.toString(id);
    }

    @Override
    public List<WritebackCell> getWritebackCells() {
        return writebackCells;
    }

    @Override
    public List<Map<String, Map.Entry<DataTypeJdbc, Object>>> getSessionValues() {
        return sessionValues;
    }

    /**
     * Returns the scenario inside a calculated member in the scenario
     * dimension. For example, applied to [Scenario].[1], returns the Scenario
     * object representing scenario #1.
     *
     * @param member Wrapper member
     * @return Wrapped scenario
 */
    public static Scenario forMember(final Member member) {
        if (isScenario(member.getHierarchy())) {
            final Formula formula = ((RolapCalculatedMember) member)
                .getFormula();
            final ResolvedFunCallImpl resolvedFunCall =
                (ResolvedFunCallImpl) formula.getExpression();
            final Calc calc = resolvedFunCall.getFunDef()
                .compileCall(null, null);
            return ((ScenarioImpl.ScenarioCalc) calc).getScenario();
        } else {
            return null;
        }
    }

    /**
     * Registers this Scenario with a Schema, creating a calulated member
     * [Scenario].[{id}] for each cube that has writeback enabled. (Currently
     * a cube has writeback enabled iff it has a dimension called "Scenario".)
     *
     * @param schema Schema
 */
    public void register(RolapCatalog schema) {
        // Add a value to the [Scenario] dimension of every cube that has
        // writeback enabled.
        for (RolapCube cube : schema.getCubeList()) {
            for (Hierarchy hierarchy : cube.getHierarchies()) {
                if (isScenario(hierarchy)) {
                    member =
                        cube.createCalculatedMember(
                            hierarchy,
                            new StringBuilder(getId()).toString(),
                            new ScenarioImpl.ScenarioCalc(this));
                    assert member != null;
                }
            }
        }
    }

    /**
     * Returns whether a hierarchy is the [Scenario] hierarchy.
     *
     * TODO: use a flag
     *
     * @param hierarchy Hierarchy
     * @return Whether hierarchy is the scenario hierarchy
 */
    public static boolean isScenario(Hierarchy hierarchy) {
        return hierarchy.getName().equals("Scenario");
    }

    /**
     * Returns the number of atomic cells that contribute to the current
     * cell.
     *
     * @param evaluator Evaluator
     * @return Number of atomic cells in the current cell
 */
    private static double evaluateAtomicCellCount(RolapEvaluator evaluator) {
        final int savepoint = evaluator.savepoint();
        try {
            evaluator.setContext(
                evaluator.getCube().getAtomicCellCountMeasure());
            final Object o = evaluator.evaluateCurrent();
            if (o == NotLoaded.INSTANCE) {
                // dirty batching pass whose results are discarded - the
                // legacy Double(0) marker used to yield a count of 0 here
                return 0;
            }
            return ((Number) o).doubleValue();
        } finally {
            evaluator.restore(savepoint);
        }
    }

    /**
     * Computes the number of atomic cells in a cell identified by a list
     * of members.
     *
     * The method may be expensive. If the value is not in the cache,
     * computes it immediately using a database access. It uses an aggregate
     * table if applicable, and puts the value into the cache.
     *
     * Compare with {@link #evaluateAtomicCellCount(RolapEvaluator)}, which
     * gets the value from the cache but may lie (and generate a cache miss) if
     * the value is not present.
     *
     * @param cube Cube
     * @param memberList Coordinate members of cell
     * @return Number of atomic cells in cell
 */
    private static double computeAtomicCellCount(
        RolapCube cube, List<Member> memberList)
    {
        // Implementation generates and executes a recursive MDX query. This
        // may not be the most efficient implementation, but achieves the
        // design goals of (a) immediacy, (b) cache use, (c) aggregate table
        // use.
        final StringBuilder buf = new StringBuilder();
        buf.append("select from ");
        buf.append(cube.getUniqueName());
        int k = 0;
        for (Member member : memberList) {
            if (member.isMeasure()) {
                member = cube.factCountMeasure;
                assert member != null
                    : "fact count measure is required for writeback cubes";
            }
            if (!member.equals(member.getHierarchy().getDefaultMember())) {
                if (k++ > 0) {
                    buf.append(", ");
                } else {
                    buf.append(" where (");
                }
                buf.append(member.getUniqueName());
            }
        }
        if (k > 0) {
            buf.append(")");
        }
        final String mdx = buf.toString();
        final Connection connection =
            cube.getCatalog().getInternalConnection();
        final Query query = connection.parseQuery(mdx);
        final Result result = connection.execute(query);
        final Object o = result.getCell(new int[0]).getValue();
        return o instanceof Number n
            ? n.doubleValue()
            : 0d;
    }

    /**
     * Returns the member of the [Scenario] dimension that represents this
     * scenario. Including that member in the slicer will automatically use
     * this scenario.
     *
     * The result is not null, provided that {@link #register(RolapCatalog)}
     * has been called.
     *
     * @return Scenario member
 */
    public Member getMember() {
        return member;
    }

    /**
     * Created by a call to
     * Cell#setValue(Object, AllocationPolicy, Object...),
     * records that a cell's value has been changed.
     *
     * From this, other cell values can be modified as they are read into
     * cache. Only the cells specifically modified by the client have a
     * {@code CellValueOverride}.
     *
     * In future, a {@link ScenarioImpl} could be persisted by
     * serializing all {@code WritebackCell}s to a file.
 */
    public static class WritebackCellImpl implements WritebackCell {


        private final double newValue;
        private final double currentValue;
        private final AllocationPolicy allocationPolicy;
        private Member[] membersByOrdinal;
        private final double atomicCellCount;

        /**
         * Creates a WritebackCell.
         *
         * @param cube Cube
         * @param members Members that form context
         * @param constrainedColumnsBitKey Bitmap of columns which have values
         * @param keyValues List of values, by bit position
         * @param newValue New value
         * @param currentValue Current value
         * @param allocationPolicy Allocation policy
 */
        WritebackCellImpl(
            RolapCube cube,
            List<Member> members,
            BitKey constrainedColumnsBitKey,
            Object[] keyValues,
            double newValue,
            double currentValue,
            AllocationPolicy allocationPolicy)
        {
            assert keyValues.length == constrainedColumnsBitKey.cardinality();
//            discard(cube); // not used currently
//            discard(constrainedColumnsBitKey); // not used currently
//            discard(keyValues); // not used currently
            this.newValue = newValue;
            this.currentValue = currentValue;
            this.allocationPolicy = allocationPolicy;
            this.atomicCellCount = computeAtomicCellCount(cube, members);

            // Build the array of members by ordinal. If a member is not
            // specified for a particular dimension, use the 'all' member (not
            // necessarily the same as the default member).
            final List<Hierarchy> hierarchyList = cube.getHierarchies();
            this.membersByOrdinal = new Member[hierarchyList.size()];
            for (int i = 0; i < membersByOrdinal.length; i++) {
                membersByOrdinal[i] = hierarchyList.get(i).getDefaultMember();
            }
            for (Member member : members) {
                final Hierarchy hierarchy = member.getHierarchy();
                if (isScenario(hierarchy)) {
                    assert member.isAll();
                }
                // REVIEW The following works because Measures is the only
                // dimension whose members do not belong to RolapCubeDimension,
                // just a regular RolapDimension, but has ordinal 0.
                final int ordinal = hierarchy.getOrdinalInCube();
                membersByOrdinal[ordinal] = member;
            }
        }

        @Override
        public double getNewValue() {
            return newValue;
        }

        @Override
        public double getCurrentValue() {
            return currentValue;
        }

        @Override
        public AllocationPolicy getAllocationPolicy() {
            return allocationPolicy;
        }

        @Override
        public Member[] getMembersByOrdinal() {
            return membersByOrdinal;
        }

        @Override
        public double getAtomicCellCount() {
            return atomicCellCount;
        }

        /**
         * Returns the amount by which the cell value has increased with this
         * override.
         *
         * @return Amount by which value has increased
 */
        @Override
        public double getOffset() {
            return newValue - currentValue;
        }

        /**
         * Returns the position of this writeback cell relative to another
         * co-ordinate.
         *
         * Assumes that {@code members} contains an entry for each dimension
         * in the cube.
         *
         * @param members Co-ordinates of another cell
         * @return Relation of this writeback cell to other co-ordinate, never
         * null
 */
        @Override
        public WritebackCell.CellRelation getRelationTo(Member[] members) {
            int aboveCount = 0;
            int belowCount = 0;
            for (int i = 0; i < members.length; i++) {
                Member thatMember = members[i];
                Member thisMember = membersByOrdinal[i];
                // FIXME: isChildOrEqualTo is very inefficient. It should use
                // level depth as a guideline, at least.
                if (thatMember.isChildOrEqualTo(thisMember)) {
                    if (thatMember.equals(thisMember)) {
                        // thisMember equals member
                    } else {
                        // thisMember is ancestor of member
                        ++aboveCount;
                        if (belowCount > 0) {
                            return WritebackCell.CellRelation.NONE;
                        }
                    }
                } else if (thisMember.isChildOrEqualTo(thatMember)) {
                    // thisMember is descendant of member
                    ++belowCount;
                    if (aboveCount > 0) {
                        return WritebackCell.CellRelation.NONE;
                    }
                } else {
                    return WritebackCell.CellRelation.NONE;
                }
            }
            assert aboveCount == 0 || belowCount == 0;
            if (aboveCount > 0) {
                return WritebackCell.CellRelation.ABOVE;
            } else if (belowCount > 0) {
                return WritebackCell.CellRelation.BELOW;
            } else {
                return WritebackCell.CellRelation.EQUAL;
            }
        }
    }



    /**
     * Compiled expression to implement a [Scenario].[{name}] calculated member.
     *
     * When evaluated, replaces the value of a cell with the value overridden
     * by a writeback value, per
     * {@link Cell#setValue(Object, AllocationPolicy, Object...)},
     * and modifies the values of ancestors or descendants of such cells
     * according to the allocation policy.
 */
    private static class ScenarioCalc extends AbstractProfilingNestedUnknownCalc {
        private final ScenarioImpl scenario;

        /**
         * Creates a ScenarioCalc.
         *
         * @param scenario Scenario whose writeback values should be substituted
         * for the values stored in the database.
 */
        public ScenarioCalc(ScenarioImpl scenario) {
            super(ScalarType.INSTANCE);
            this.scenario = scenario;
        }

        /**
         * Returns the Scenario this writeback cell belongs to.
         *
         * @return Scenario, never null
 */
        private Scenario getScenario() {
            return scenario;
        }

        @Override
        public Object evaluateInternal(Evaluator evaluator) {
            // Evaluate current member in the given scenario by expanding in
            // terms of the writeback cells.

            // First, evaluate in the null scenario.
            final Member defaultMember =
                scenario.member.getHierarchy().getDefaultMember();
            final int savepoint = evaluator.savepoint();
            try {
                evaluator.setContext(defaultMember);
                final Object o = evaluator.evaluateCurrent();
                double d =
                    o instanceof Number n
                        ? n.doubleValue()
                        : 0d;

                // Look for writeback cells which are equal to, ancestors of,
                // or descendants of, the current cell. Modify the value
                // accordingly.
                //
                // It is possible that the value is modified by several
                // writebacks. If so, order is important.
                int changeCount = 0;
                for (WritebackCell writebackCell
                    : scenario.getWritebackCells())
                {
                    WritebackCell.CellRelation relation =
                        writebackCell.getRelationTo(evaluator.getMembers());
                    switch (relation) {
                        case ABOVE:
                            // This cell is below the writeback cell. Value is
                            // determined by allocation policy.
                            double atomicCellCount =
                                evaluateAtomicCellCount((RolapEvaluator) evaluator);
                            if (atomicCellCount == 0d) {
                                // Sometimes the value comes back zero if the cache
                                // is not ready. Switch to 1, which at least does
                                // not give divide-by-zero. We will be invoked again
                                // for the correct answer when the cache has been
                                // populated.
                                atomicCellCount = 1d;
                            }
                            switch (writebackCell.getAllocationPolicy()) {
                                case EQUAL_ALLOCATION:
                                    d = writebackCell.getNewValue()
                                        * atomicCellCount
                                        / writebackCell.getAtomicCellCount();
                                    break;
                                case EQUAL_INCREMENT:
                                    d += writebackCell.getOffset()
                                        * atomicCellCount
                                        / writebackCell.getAtomicCellCount();
                                    break;
                                default:
                                    throw Util.unexpected(
                                        writebackCell.getAllocationPolicy());
                            }
                            ++changeCount;
                            break;
                        case EQUAL:
                            // This cell is the writeback cell. Value is the value
                            // written back.
                            d = writebackCell.getNewValue();
                            ++changeCount;
                            break;
                        case BELOW:
                            // This cell is above the writeback cell. Value is the
                            // current value plus the change in the writeback cell.
                            d += writebackCell.getOffset();
                            ++changeCount;
                            break;
                        case NONE:
                            // Writeback cell is unrelated. It has no effect on
                            // cell's value.
                            break;
                        default:
                            throw Util.unexpected(relation);
                    }
                }
                // Don't create a new object if value has not changed.
                if (changeCount == 0) {
                    return o;
                } else {
                    return d;
                }
            } finally {
                evaluator.restore(savepoint);
            }
        }
    }

    @Override
    public void clear() {
        getWritebackCells().clear();
        sessionValues.clear();
    }

}
