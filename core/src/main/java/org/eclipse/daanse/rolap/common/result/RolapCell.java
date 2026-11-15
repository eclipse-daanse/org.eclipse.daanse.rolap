/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
 * Copyright (C) 2021 Sergei Semenkov
 * All Rights Reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.rolap.common.result;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.execution.Execution.Purpose;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.query.component.Literal;
import org.eclipse.daanse.olap.api.relational.RelationalCell;
import org.eclipse.daanse.olap.api.relational.RelationalContext;
import org.eclipse.daanse.olap.api.relational.SqlStatementI;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.result.DrillThroughExpressionCheck;
import org.eclipse.daanse.olap.result.ResultCell;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.result.CellInfo;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapAggregationManager;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.SqlStatement;
import org.eclipse.daanse.rolap.common.agg.AndPredicate;
import org.eclipse.daanse.rolap.common.agg.DrillThroughCellRequest;
import org.eclipse.daanse.rolap.common.agg.MemberColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.OrPredicate;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.common.writeback.ScenarioImpl;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapPhysicalCube;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.eclipse.daanse.rolap.element.RolapVirtualCube;
import org.eclipse.daanse.rolap.element.RolapVirtualCubeMeasure;
import org.slf4j.Logger;

/**
 * RolapCell implements {@link org.eclipse.daanse.olap.api.result.Cell} within a
 * {@link RolapResult}.
 */
public class RolapCell extends ResultCell implements RelationalCell {
    /**
 */
    private static final int MDACTION_TYPE_DRILLTHROUGH = 0x100;

    /** The result the base holds, narrowed to what the relational parts ask of it. */
    private final RolapResult rolapResult;
    private final static String drillthroughDisabled =
        "Can''t perform drillthrough operations because ''{0}'' is set to false.";

    /**
     * Creates a RolapCell.
     *
     * @param result Result cell belongs to
     * @param pos Coordinates of cell
     * @param ci Cell information, containing value et cetera
 */
    RolapCell(RolapResult result, int[] pos, CellInfo ci) {
        super(result, pos, ci);
        this.rolapResult = result;
    }


    @Override
	public String getDrillThroughSQL(
        boolean extendedContext)
    {
        return getDrillThroughSQL(
            new ArrayList<OlapElement>(), extendedContext, 0);
    }

    public String getDrillThroughSQL(
            List<OlapElement> fields,
            boolean extendedContext)
    {
        return getDrillThroughSQL(fields, extendedContext, 0);
    }

    public String getDrillThroughSQL(
        List<OlapElement> fields,
        boolean extendedContext,
        int maxRowCount)
    {
        if (!drillThroughAllowed()) {
            throw new OlapRuntimeException(MessageFormat.format(
                drillthroughDisabled,
                        "enableDrillThrough"));
        }
        final Member[] currentMembers = drillThroughCoordinates();
        // Create a StarPredicate to represent the compound slicer
        // (if necessary)
        // NOTE: the method buildDrillthroughSlicerPredicate modifies
        // the array of members, so it MUST be called before calling
        // RolapAggregationManager.makeDrillThroughRequest
        StarPredicate starPredicateSlicer =
            buildDrillthroughSlicerPredicate(
                currentMembers,
                rolapResult.getSlicerAxis());
        DrillThroughCellRequest cellRequest =
            RolapAggregationManager.makeDrillThroughRequest(
                currentMembers, extendedContext, rolapResult.getCube(),
                fields);
        if (cellRequest == null) {
            return null;
        }
        cellRequest.setMaxRowCount(maxRowCount);
        final Connection connection = connection();
        AbstractBasicContext abc = (AbstractBasicContext) connection.getContext();
        final RolapAggregationManager aggMgr =
            (RolapAggregationManager)abc.getAggregationManager();
        return aggMgr.getDrillThroughSql(
            cellRequest,
            starPredicateSlicer,
            fields,
            false);
    }

    @Override
	public int getDrillThroughCount() {
        final Member[] currentMembers = drillThroughCoordinates();
        // Create a StarPredicate to represent the compound
        // slicer (if necessary)
        // NOTE: the method buildDrillthroughSlicerPredicate modifies
        // the array of members, so it MUST be called before calling
        // RolapAggregationManager.makeDrillThroughRequest
        StarPredicate starPredicateSlicer =
            buildDrillthroughSlicerPredicate(
                currentMembers,
                rolapResult.getSlicerAxis());
        DrillThroughCellRequest cellRequest =
            RolapAggregationManager.makeDrillThroughRequest(
                currentMembers, false, rolapResult.getCube(),
                Collections.<OlapElement>emptyList());
        if (cellRequest == null) {
            return -1;
        }
        final Connection connection = connection();
        AbstractBasicContext abc = (AbstractBasicContext)  connection.getContext();
        final RolapAggregationManager aggMgr =
                (RolapAggregationManager)abc.getAggregationManager();
        final String sql =
            aggMgr.getDrillThroughSql(
                cellRequest,
                starPredicateSlicer,
                new ArrayList<OlapElement>(),
                true);

        ExecutionImpl execution = new ExecutionImpl(connection.getInternalStatement(), ExecuteDurationUtil.executeDurationValue(connection.getContext()));
        ExecutionMetadata metadata = ExecutionMetadata.of(
            "RolapCell.getCellValue",
            "Error getting cell value",
            Purpose.OTHER,
            0
        );
        ExecutionContext execContext = execution.asContext().createChild(metadata, Optional.empty());
        final SqlStatement stmt =
            RolapUtil.executeQuery(
                connection.getContext(),
                sql,
                execContext);
        try {
            ResultSet rs = stmt.getResultSet();
            assert rs.getMetaData().getColumnCount() == 1;
            rs.next();
            ++stmt.rowCount;
            return rs.getInt(1);
        } catch (SQLException e) {
            throw stmt.handle(e);
        } finally {
            stmt.close();
        }
    }

    /**
     * This method handles the case of a compound slicer with more than one
     * {@link Position}. In this case, a simple array of {@link Member}s is not
     * sufficient to express the set of drill through rows. If the slicer axis
     * does have multiple positions, this method will do two things:
     * <ol>
     *  Modify the passed-in array if any Member is overly restrictive.
     *  This can happen if the slicer specifies multiple members in the same
     *  hierarchy. In this scenario, the array of Members will contain an
     *  element for only the last selected member in the hierarchy. This method
     *  will replace that Member with the "All" Member from that hierarchy.
     *
     *  Create a {@link StarPredicate} representing the Positions indicated
     *  by the slicer axis.
     *
     * </ol>
     *
     * @param membersForDrillthrough the array of Members returned by
     * {@link #drillThroughCoordinates()}
     * @param slicerAxis the slicer {@link Axis}
     * @return an instance of StarPredicate representing all
     * of the the positions from the slicer if it has more than one,
     * or null otherwise.
 */
    private StarPredicate buildDrillthroughSlicerPredicate(
        Member[] membersForDrillthrough,
        Axis slicerAxis)
    {
        List<Position> listOfPositions = slicerAxis.getPositions();
        // With zero or one position the array of Members already says everything the
        // slicer says, so there is no predicate to build
        if (listOfPositions.size() <= 1) {
            return null;
        }
        loosenForCompoundSlicer(membersForDrillthrough, slicerAxis);
        // This is a list containing an AndPredicate for each position in the
        // slicer axis
        List<StarPredicate> listOfStarPredicatesForSlicerPositions =
            new ArrayList<>();
        // Now we re-iterate the positions' members,
        // creating the slicer constraint
        for (Position position : listOfPositions) {
            // This is a list of the predicates required to select the
            // current position (excluding the members of the position
            // that are already constrained in the membersForDrillthrough array)
            List<StarPredicate> listOfStarPredicatesForCurrentPosition =
                new ArrayList<>();
            // Iterate the members of the current position
            for (Member member : position) {
                RolapHierarchy rolapHierarchy =
                    (RolapHierarchy) member.getHierarchy();
                // If the membersForDrillthrough is already constraining to
                // this member, then there is no need to create additional
                // predicate(s) for this member
                if (!membersForDrillthrough[rolapHierarchy.getOrdinalInCube()]
                   .equals(member))
                {
                    // Walk up the member's hierarchy, adding a
                    // predicate for each level
                    Member memberWalk = member;
                    Level levelLast = null;
                    while (memberWalk != null && ! memberWalk.isAll()) {
                        // Only create a predicate for this member if we
                        // are at a new level. This is for parent-child levels,
                        // however it still suffers from the following bug:
                        //  http://jira.pentaho.com/browse/MONDRIAN-318
                        if (memberWalk.getLevel() != levelLast) {
                            RolapCubeMember rolapCubeMember =
                                (RolapCubeMember) memberWalk;
                            RolapStar.Column column =
                                rolapCubeMember.getLevel()
                                    .getBaseStarKeyColumn(
                                        getDrillThroughBaseCube());
                            // Add a predicate for the member at this level
                            listOfStarPredicatesForCurrentPosition.add(
                                new MemberColumnPredicate(
                                    column,
                                    rolapCubeMember));
                        }
                        levelLast = memberWalk.getLevel();
                        // Walk up the hierarchy
                        memberWalk = memberWalk.getParentMember();
                    }
                }
            }
            // AND together all of the predicates that specify
            // the current position
            StarPredicate starPredicateForCurrentSlicerPosition =
                new AndPredicate(listOfStarPredicatesForCurrentPosition);
            // Add this position's predicate to the list
            listOfStarPredicatesForSlicerPositions
                .add(starPredicateForCurrentSlicerPosition);
        }
        // OR together the predicates for all of the slicer's
        // positions and return
        return new OrPredicate(listOfStarPredicatesForSlicerPositions);
    }

    /**
     * Returns appropriate non-virtual cube that will be used
     * for retrieving base star key column.
 */
    private RolapCube getDrillThroughBaseCube() {
        if (rolapResult.getCube() instanceof RolapVirtualCube) {
            Member[] membersForDrillThrough = this.drillThroughCoordinates();
            for (Member m : membersForDrillThrough) {
                if (m instanceof RolapVirtualCubeMeasure) {
                    return ((RolapVirtualCubeMeasure) m).getCube();
                }
            }
        }
        return rolapResult.getCube();
    }

    /**
     * Returns whether it is possible to drill through this cell.
     * Drill-through is possible if the measure is a stored measure
     * and not possible for calculated measures.
     *
     * @return true if can drill through
 */
    /** The connection this cell was worked out for. */
    private Connection connection() {
        return rolapResult.getExecution().getDaanseStatement().getDaanseConnection();
    }

    /** A drill-through reaches rows when a cube can be named for the measure behind the cell. */
    @Override
    protected boolean rowsLieBehind(Member[] coordinates) {
        return chooseDrillThroughCube(coordinates, rolapResult.getCube()) != null;
    }

    public static RolapCube chooseDrillThroughCube(
        Member[] currentMembers,
        RolapCube defaultCube)
    {
        if (defaultCube != null && defaultCube instanceof RolapVirtualCube) {
            List<RolapCube> cubes = new ArrayList<>();
            for (RolapMember member : defaultCube.getMeasuresMembers()) {
                if (member instanceof RolapVirtualCubeMeasure measure) {
                    cubes.add(measure.getCube());
                }
            }
            defaultCube = cubes.getFirst();
            assert defaultCube instanceof RolapPhysicalCube;
        }
        final DrillThroughVisitor visitor =
            new DrillThroughVisitor();
        try {
            for (Member member : currentMembers) {
                visitor.handleMember(member);
            }
        } catch (RuntimeException e) {
            if (DrillThroughExpressionCheck.isTooComplex(e)) {
                // No cubes left
                return null;
            } else {
                throw e;
            }
        }
        return visitor.cube == null
             ? defaultCube
             : visitor.cube;
    }

    /** A formula that only names another member stands for that member when rows are looked for. */
    @Override
    protected Member[] drillThroughCoordinates() {
        final Member[] currentMembers = rolapResult.getCellMembers(pos);
        List<Member> memberList = Arrays.asList(currentMembers);
        for (int i = 0; i < currentMembers.length; i++) {
            replaceTrivialCalcMember(i, memberList);
        }
        return currentMembers;
    }

    /**
     * Replaces a coordinate that is only another name for a member by that member. A cube member is
     * stripped first, because what the formula names is the member of the shared hierarchy behind it.
     */
    private void replaceTrivialCalcMember(int i, List<Member> members) {
        Member member = members.get(i);
        if (!member.isCalculated()) {
            return;
        }
        memberBehindTrivialFormula(RolapUtil.strip((RolapMember) member))
                .ifPresent(named -> members.set(i, named));
    }

    /**
     * Generates an executes a SQL statement to drill through this cell.
     *
     * Throws if this cell is not drillable.
     *
     * Enforces limits on the starting and last row.
     *
     * If tabFields is not null, returns the specified columns. (This option
     * is deprecated.)
     *
     * @param maxRowCount Maximum number of rows to retrieve, less or = 0 if unlimited
     * @param firstRowOrdinal Ordinal of row to skip to (1-based), or 0 to
     *   start from beginning
     * @param firstRowOrdinal firstRowOrdinal
     * @param fields            List of field expressions to return as the
     *                          result set columns.
     * @param extendedContext   If true, add non-constraining columns to the
     *                          query for levels below each current member.
     *                          This additional context makes the drill-through
     *                          queries easier for humans to understand.
     * @param logger Logger. If not null and debug is enabled, log SQL here
     * @return executed SQL statement
 */
    public SqlStatementI drillThroughInternal(
        int maxRowCount,
        int firstRowOrdinal,
        List<OlapElement> fields,
        boolean extendedContext,
        Logger logger)
    {
        if (!canDrillThrough()) {
            throw Util.newError("Cannot do DrillThrough operation on the cell");
        }

        // Generate SQL.
        String sql = getDrillThroughSQL(fields, extendedContext,  maxRowCount);
        if (logger != null && logger.isDebugEnabled()) {
            logger.debug("drill through sql: " + sql);
        }

        // Choose the appropriate scrollability. If we need to start from an
        // offset row, it is useful that the cursor is scrollable, but not
        // essential.
        final Statement statement =
            rolapResult.getExecution().getDaanseStatement();
        final ExecutionImpl execution = new ExecutionImpl(statement, ExecuteDurationUtil.executeDurationValue(statement.getConnection().getContext()));

        final Connection connection = statement.getDaanseConnection();
        int resultSetType = ResultSet.TYPE_SCROLL_INSENSITIVE;
        int resultSetConcurrency = ResultSet.CONCUR_READ_ONLY;

        Dialect dialect = RelationalContext.of(execution.getDaanseStatement().getDaanseConnection().getContext()).getDialect();
        if (!dialect.supportsResultSetConcurrency(
                resultSetType, resultSetConcurrency)
            || firstRowOrdinal <= 1)
        {
            // downgrade to non-scroll cursor, since we can
            // fake absolute() via forward fetch
            resultSetType = ResultSet.TYPE_FORWARD_ONLY;
        }
        Context context= connection.getContext();
        ExecutionMetadata metadata = ExecutionMetadata.of(
            "RolapCell.drillThrough",
            "Error in drill through",
            Purpose.DRILL_THROUGH,
            0
        );
        ExecutionContext execContext = execution.asContext().createChild(metadata, Optional.empty());
        return
            RolapUtil.executeQuery(
                context,
                sql,
                null,
                maxRowCount,
                firstRowOrdinal,
                execContext,
                resultSetType,
                resultSetConcurrency,
                null);
    }

    @Override
    public void setValue(
        org.eclipse.daanse.olap.api.result.Scenario scenario,
        Object newValue,
        org.eclipse.daanse.olap.api.result.AllocationPolicy allocationPolicy,
        Object... allocationArgs)
    {
        if (allocationPolicy == null) {
            // user error
            throw Util.newError(
                "Allocation policy must not be null");
        }
        final Member[] members = rolapResult.getCellMembers(pos);
        org.eclipse.daanse.olap.api.result.Scenario named = prepareForWriteback(members);
        if (named != null) {
            scenario = named;
        }
        if (scenario == null) {
            throw Util.newError("No active scenario");
        }
        if (allocationArgs == null) {
            allocationArgs = new Object[0];
        }
        // The Scenario.setCellValue signature accepts Object for both the new
        // and current values. We pass them through verbatim — the numeric
        // narrowing happens inside ScenarioImpl on the numeric branch only,
        // where a null currentValue is interpreted as 0. Text writeback
        // ignores currentValue entirely, so leaving it null/String here is
        // the right shape.
        scenario.setCellValue(
            rolapResult.getExecution().getDaanseStatement()
                .getDaanseConnection(),
            Arrays.asList(members),
            newValue,
            getValue(),
            allocationPolicy,
            allocationArgs);
    }

    /**
     * Visitor that walks over a cell's expression and checks whether the
     * cell should allow drill-through. If not, throws the {@link #bomb}
     * exception.
     *
     * Examples:
     *
     * Literal 1 is drillable
     * Member [Measures].[Unit Sales] is drillable
     * Calculated member with expression [Measures].[Unit Sales] +
     *     1 is drillable
     * Calculated member with expression
     *     ([Measures].[Unit Sales], [Time].PrevMember) is not drillable
     *
 */
    /**
     * What a member means when the rows behind a cell are looked for: a stored measure names the cube
     * the rows live in, a cube member stands for the member of the shared hierarchy behind it, and a
     * calculated measure is followed into its formula. Anything else is not something this catalog can
     * name rows for.
     */
    private static class DrillThroughVisitor extends DrillThroughExpressionCheck {
        RolapCube cube = null;

        @Override
        protected void handleMember(Member member) {
            switch (member) {
                case RolapStoredMeasure storedMeasure -> {
                    // If this member is in a different cube than previous members
                    // we've seen, we cannot drill through.
                    final RolapCube memberCube = storedMeasure.getCube();
                    if (this.cube == null) {
                        this.cube = memberCube;
                    } else if (this.cube != memberCube) {
                        throw tooComplex();
                    }
                }
                case RolapCubeMember cubeMember -> handleMember(cubeMember.member);
                case RolapHierarchy.RolapCalculatedMeasure measure ->
                    measure.getFormula().getExpression().accept(this);
                case RolapMember ignored -> { } // regular RolapMember - fine
                default -> throw tooComplex(); // don't know what this is!
            }
        }
    }

}
