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

package org.eclipse.daanse.rolap.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.ISqlStatement;
import org.eclipse.daanse.olap.api.Statement;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementEvent;
import org.eclipse.daanse.olap.api.query.component.DimensionExpression;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.api.query.component.HierarchyExpression;
import org.eclipse.daanse.olap.api.query.component.Id;
import org.eclipse.daanse.olap.api.query.component.LevelExpression;
import org.eclipse.daanse.olap.api.query.component.Literal;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.api.query.component.NamedSetExpression;
import org.eclipse.daanse.olap.api.query.component.ParameterExpression;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.query.component.QueryAxis;
import org.eclipse.daanse.olap.api.query.component.ResolvedFunCall;
import org.eclipse.daanse.olap.api.query.component.UnresolvedFunCall;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.function.def.aggregate.AggregateFunDef;
import org.eclipse.daanse.olap.function.def.set.SetFunDef;
import org.eclipse.daanse.olap.query.component.MdxVisitorImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import  org.eclipse.daanse.olap.server.ExecutionImpl;
import  org.eclipse.daanse.olap.server.LocusImpl;
import org.eclipse.daanse.rolap.common.agg.AndPredicate;
import org.eclipse.daanse.rolap.common.agg.DrillThroughCellRequest;
import org.eclipse.daanse.rolap.common.agg.MemberColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.OrPredicate;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.eclipse.daanse.rolap.element.RolapPhysicalCube;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.eclipse.daanse.rolap.element.RolapVirtualCube;
import org.eclipse.daanse.rolap.element.RolapVirtualCubeMeasure;
import org.slf4j.Logger;

/**
 * RolapCell implements {@link org.eclipse.daanse.olap.api.result.Cell} within a
 * {@link RolapResult}.
 */
public class RolapCell implements Cell {
    /**
     * @see mondrian.util.Bug#olap4jUpgrade Use
     * {@link mondrian.xmla.XmlaConstants}.ActionType.DRILLTHROUGH when present
     */
    private static final int MDACTION_TYPE_DRILLTHROUGH = 0x100;

    private final RolapResult result;
    protected final int[] pos;
    protected RolapResult.CellInfo ci;
    private final static String drillthroughDisabled =
        "Can''t perform drillthrough operations because ''{0}'' is set to false.";

    /**
     * Creates a RolapCell.
     *
     * @param result Result cell belongs to
     * @param pos Coordinates of cell
     * @param ci Cell information, containing value et cetera
     */
    RolapCell(RolapResult result, int[] pos, RolapResult.CellInfo ci) {
        this.result = result;
        this.pos = pos;
        this.ci = ci;
    }

    @Override
	public List<Integer> getCoordinateList() {
        return new AbstractList<>() {
            @Override
			public Integer get(int index) {
                return pos[index];
            }

            @Override
			public int size() {
                return pos.length;
            }
        };
    }

    @Override
	public Object getValue() {
        if (ci.value == Util.nullValue) {
            return null;
        }
        return ci.value;
    }

    @Override
	public String getCachedFormatString() {
        return ci.formatString;
    }

    @Override
	public String getFormattedValue() {
        return ci.getFormatValue();
    }

    @Override
	public boolean isNull() {
        return (Objects.equals(ci.value ,Util.nullValue));
    }

    @Override
	public boolean isError() {
        return (ci.value instanceof Throwable);
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
        if (!result.getExecution().getMondrianStatement().getMondrianConnection().getContext()
                .getConfigValue(ConfigConstants.ENABLE_DRILL_THROUGH, ConfigConstants.ENABLE_DRILL_THROUGH_DEFAULT_VALUE, Boolean.class))
        {
            throw new OlapRuntimeException(MessageFormat.format(
                drillthroughDisabled,
                        "enableDrillThrough"));
        }
        final Member[] currentMembers = getMembersForDrillThrough();
        // Create a StarPredicate to represent the compound slicer
        // (if necessary)
        // NOTE: the method buildDrillthroughSlicerPredicate modifies
        // the array of members, so it MUST be called before calling
        // RolapAggregationManager.makeDrillThroughRequest
        StarPredicate starPredicateSlicer =
            buildDrillthroughSlicerPredicate(
                currentMembers,
                result.getSlicerAxis());
        DrillThroughCellRequest cellRequest =
            RolapAggregationManager.makeDrillThroughRequest(
                currentMembers, extendedContext, result.getCube(),
                fields);
        if (cellRequest == null) {
            return null;
        }
        cellRequest.setMaxRowCount(maxRowCount);
        final Connection connection =
            result.getExecution().getMondrianStatement()
                .getMondrianConnection();
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
        final Member[] currentMembers = getMembersForDrillThrough();
        // Create a StarPredicate to represent the compound
        // slicer (if necessary)
        // NOTE: the method buildDrillthroughSlicerPredicate modifies
        // the array of members, so it MUST be called before calling
        // RolapAggregationManager.makeDrillThroughRequest
        StarPredicate starPredicateSlicer =
            buildDrillthroughSlicerPredicate(
                currentMembers,
                result.getSlicerAxis());
        DrillThroughCellRequest cellRequest =
            RolapAggregationManager.makeDrillThroughRequest(
                currentMembers, false, result.getCube(),
                Collections.<OlapElement>emptyList());
        if (cellRequest == null) {
            return -1;
        }
        final Connection connection =
            result.getExecution().getMondrianStatement()
                .getMondrianConnection();
        AbstractBasicContext abc = (AbstractBasicContext)  connection.getContext();
        final RolapAggregationManager aggMgr =
                (RolapAggregationManager)abc.getAggregationManager();
        final String sql =
            aggMgr.getDrillThroughSql(
                cellRequest,
                starPredicateSlicer,
                new ArrayList<OlapElement>(),
                true);

        final SqlStatement stmt =
            RolapUtil.executeQuery(
                connection.getContext(),
                sql,
                new LocusImpl(
                    new ExecutionImpl(connection.getInternalStatement(), ExecuteDurationUtil.executeDurationValue(connection.getContext())),
                    "RolapCell.getDrillThroughCount",
                    "Error while counting drill-through"));
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
     * {@link #getMembersForDrillThrough()}
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
        // If the slicer has zero or one position(s),
        // then there is no need to do
        // anything; the array of Members is correct as-is
        if (listOfPositions.size() <= 1) {
            return null;
        }
        // First, iterate through the positions' members, un-constraining the
        // "membersForDrillthrough" array if any position member is not
        // in the array
        for (Position position : listOfPositions) {
            for (Member member : position) {
                RolapHierarchy rolapHierarchy =
                    (RolapHierarchy) member.getHierarchy();
                // Check if the membersForDrillthrough constraint is identical
                // to that of the position member
                if (!membersForDrillthrough[rolapHierarchy.getOrdinalInCube()]
                    .equals(member))
                {
                    // There is a discrepancy, so un-constrain the
                    // membersForDrillthrough array
                    membersForDrillthrough[rolapHierarchy.getOrdinalInCube()] =
                        rolapHierarchy.getAllMember();
                }
            }
        }
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
        if (result.getCube() instanceof RolapVirtualCube) {
            Member[] membersForDrillThrough = this.getMembersForDrillThrough();
            for (Member m : membersForDrillThrough) {
                if (m instanceof RolapVirtualCubeMeasure) {
                    return ((RolapVirtualCubeMeasure) m).getCube();
                }
            }
        }
        return result.getCube();
    }

    /**
     * Returns whether it is possible to drill through this cell.
     * Drill-through is possible if the measure is a stored measure
     * and not possible for calculated measures.
     *
     * @return true if can drill through
     */
    @Override
	public boolean canDrillThrough() {
        if (!((Context<?>)(result.getExecution().getMondrianStatement().getMondrianConnection().getContext()))
                .getConfigValue(ConfigConstants.ENABLE_DRILL_THROUGH, ConfigConstants.ENABLE_DRILL_THROUGH_DEFAULT_VALUE, Boolean.class))
        {
            return false;
        }
        // get current members
        final Member[] currentMembers = getMembersForDrillThrough();
        if (containsCalcMembers(currentMembers)) {
            return false;
        }
        Cube x = chooseDrillThroughCube(currentMembers, result.getCube());
        return x != null;
    }

    private boolean containsCalcMembers(Member[] currentMembers) {
        // Any calculated members which are not measures, we can't drill
        // through. Trivial calculated members should have been converted
        // already. We allow simple calculated measures such as
        // [Measures].[Unit Sales] / [Measures].[Store Sales] provided that both
        // are from the same cube.
        for (int i = 1; i < currentMembers.length; i++) {
            final Member currentMember = currentMembers[i];
            if (currentMember.isCalculated()) {
                return true;
            }
        }
        return false;
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
            defaultCube = cubes.get(0);
            assert defaultCube instanceof RolapPhysicalCube;
        }
        final DrillThroughVisitor visitor =
            new DrillThroughVisitor();
        try {
            for (Member member : currentMembers) {
                visitor.handleMember(member);
            }
        } catch (RuntimeException e) {
            if (e == DrillThroughVisitor.bomb) {
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

    private Member[] getMembersForDrillThrough() {
        final Member[] currentMembers = result.getCellMembers(pos);

        // replace member if we're dealing with a trivial formula
        List<Member> memberList = Arrays.asList(currentMembers);
        for (int i = 0; i < currentMembers.length; i++) {
            replaceTrivialCalcMember(i, memberList);
        }
        return currentMembers;
    }

    private void replaceTrivialCalcMember(int i, List<Member> members) {
        Member member = members.get(i);
        if (!member.isCalculated()) {
            return;
        }
        member = RolapUtil.strip((RolapMember) member);
        // if "cm" is a calc member defined by
        // "with member cm as m" then
        // "cm" is equivalent to "m"
        final Expression expr = member.getExpression();
        if (expr instanceof MemberExpression) {
            members.set(
                i,
                ((MemberExpression) expr).getMember());
            return;
        }
        // "Aggregate({m})" is equivalent to "m"
        if (expr instanceof ResolvedFunCallImpl call) {
            if (call.getFunDef() instanceof AggregateFunDef) {
                final Expression[] args = call.getArgs();
                if (args[0] instanceof ResolvedFunCallImpl arg0) {
                    if (arg0.getFunDef() instanceof SetFunDef) {
                        if (arg0.getArgCount() == 1
                            && arg0.getArg(0) instanceof MemberExpression)
                        {
                            final MemberExpression memberExpr =
                                (MemberExpression) arg0.getArg(0);
                            members.set(i, memberExpr.getMember());
                        }
                    }
                }
            }
        }
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
    public ISqlStatement drillThroughInternal(
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
            result.getExecution().getMondrianStatement();
        final ExecutionImpl execution = new ExecutionImpl(statement, ExecuteDurationUtil.executeDurationValue(statement.getConnection().getContext()));

        final Connection connection = statement.getMondrianConnection();
        int resultSetType = ResultSet.TYPE_SCROLL_INSENSITIVE;
        int resultSetConcurrency = ResultSet.CONCUR_READ_ONLY;

        Dialect dialect = execution.getMondrianStatement().getMondrianConnection().getContext().getDialect();
        if (!dialect.supportsResultSetConcurrency(
                resultSetType, resultSetConcurrency)
            || firstRowOrdinal <= 1)
        {
            // downgrade to non-scroll cursor, since we can
            // fake absolute() via forward fetch
            resultSetType = ResultSet.TYPE_FORWARD_ONLY;
        }
        Context context= connection.getContext();
        return
            RolapUtil.executeQuery(
                context,
                sql,
                null,
                maxRowCount,
                firstRowOrdinal,
                new SqlStatement.StatementLocus(
                    execution,
                    "RolapCell.drillThrough",
                    "Error in drill through",
                    SqlStatementEvent.Purpose.DRILL_THROUGH, 0),
                resultSetType,
                resultSetConcurrency,
                null);
    }

    @Override
	public Object getPropertyValue(String propertyName) {
        final boolean matchCase =
            SystemWideProperties.instance().CaseSensitive;
        StandardProperty property = StandardProperty.lookup(propertyName, matchCase);
        Object defaultValue = null;
        String formatString = null;
        if (property != null) {
            if(property == StandardProperty.CELL_ORDINAL) {
            	                return result.getCellOrdinal(pos);

            }else if(property == StandardProperty.VALUE) {
                return getValue();
            }else if(property == StandardProperty.FORMAT_STRING) {
                if (ci.formatString == null) {
                    final Evaluator evaluator = result.getRootEvaluator();
                    final int savepoint = evaluator.savepoint();
                    try {
                        result.populateEvaluator(evaluator, pos);
                        ci.formatString = evaluator.getFormatString();
                    } finally {
                        evaluator.restore(savepoint);
                    }
                }
                return ci.formatString;
            }else if(property == StandardProperty.FORMATTED_VALUE) {
                return getFormattedValue();
            }else if(property == StandardProperty.FONT_FLAGS) {
                defaultValue = 0;
            }else if(property == StandardProperty.SOLVE_ORDER) {
                defaultValue = 0;
            }else if(property == StandardProperty.ACTION_TYPE) {
                return canDrillThrough() ? MDACTION_TYPE_DRILLTHROUGH : 0;
            }else if(property == StandardProperty.DRILLTHROUGH_COUNT) {
                return canDrillThrough() ? getDrillThroughCount() : -1;
            }else if(property == StandardProperty.BACK_COLOR) {
                formatString = (String)getPropertyValue(StandardProperty.FORMAT_STRING.getName());
                if(formatString == null) {
                    return null;
                }
                for(String part: formatString.split(";")) {
                    String[] pair = part.split("=");
                    if(pair.length == 2
                            && pair[0] != null
                            && pair[0].toUpperCase().equals(StandardProperty.BACK_COLOR.getName())) {
                        return pair[1];
                    }
                }
                return null;
// To option when backColor belongs to real measure
//                Object backColor = null;
//                //final RolapEvaluator rolapEvaluator = (RolapEvaluator)result.getEvaluator(pos);
//                final RolapEvaluator rolapEvaluator = (RolapEvaluator)result.getRootEvaluator();
//                final int savepoint = rolapEvaluator.savepoint();
//                try {
//                    result.populateEvaluator(rolapEvaluator, pos);
//                    org.apache.log4j.Logger LOGGER = org.apache.log4j.Logger.getLogger( RolapCell.class );
//                    backColor = rolapEvaluator.getBackColor();
//                } finally {
//                    rolapEvaluator.restore(savepoint);
//                }
//                return backColor;
            }else if(property == StandardProperty.FORE_COLOR) {
                formatString = (String)getPropertyValue(StandardProperty.FORMAT_STRING.getName());
                if(formatString == null) {
                    return null;
                }
                for(String part: formatString.split(";")) {
                    String[] pair = part.split("=");
                    if(pair.length == 2
                            && pair[0] != null
                            && pair[0].toUpperCase().equals(StandardProperty.FORE_COLOR.getName())) {
                        return pair[1];
                    }
                }
                return null;
                }
                else {
                	// fall through
                }

        }
        final Evaluator evaluator = result.getRootEvaluator();
        final int savepoint = evaluator.savepoint();
        try {
            result.populateEvaluator(evaluator, pos);
            return evaluator.getProperty(propertyName, defaultValue);
        } finally {
            evaluator.restore(savepoint);
        }
    }

    @Override
	public Member getContextMember(Hierarchy hierarchy) {
        return result.getMember(pos, hierarchy);
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
        final RolapMember[] members = result.getCellMembers(pos);
        for (int i = 0; i < members.length; i++) {
            Member member = members[i];
            if (ScenarioImpl.isScenario(member.getHierarchy())) {
                scenario =
                    (org.eclipse.daanse.olap.api.result.Scenario) member.getPropertyValue(StandardProperty.SCENARIO.getName());
                members[i] = (RolapMember) member.getHierarchy().getAllMember();
            } else if (member.isCalculated()) {
                throw Util.newError(
                    new StringBuilder("Cannot write to cell: one of the coordinates (")
                        .append(member.getUniqueName())
                        .append(") is a calculated member").toString());
            }
        }
        if (scenario == null) {
            throw Util.newError("No active scenario");
        }
        if (allocationArgs == null) {
            allocationArgs = new Object[0];
        }
        final Object currentValue = getValue();
        double doubleCurrentValue;
        if (currentValue == null) {
            doubleCurrentValue = 0d;
        } else if (currentValue instanceof Number) {
            doubleCurrentValue = ((Number) currentValue).doubleValue();
        } else {
            // Cell is not a number. Likely it is a string or a
            // MondrianEvaluationException. Do not attempt to change the value
            // in this case. (REVIEW: Is this the correct behavior?)
            return;
        }
        double doubleNewValue = ((Number) newValue).doubleValue();
        scenario.setCellValue(
            result.getExecution().getMondrianStatement()
                .getMondrianConnection(),
            Arrays.asList(members),
            doubleNewValue,
            doubleCurrentValue,
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
    private static class DrillThroughVisitor extends MdxVisitorImpl {
        static final RuntimeException bomb = new RuntimeException();
        RolapCube cube = null;

        DrillThroughVisitor() {
        }

        @Override
		public Object visitMemberExpression(MemberExpression memberExpr) {
            handleMember(memberExpr.getMember());
            return null;
        }

        @Override
		public Object visitResolvedFunCall(ResolvedFunCall call) {
            final FunctionDefinition def = call.getFunDef();
            final Expression[] args = call.getArgs();
            final String name = def.getFunctionMetaData().operationAtom().name();
            if (name.equals("+")
                || name.equals("-")
                || name.equals("/")
                || name.equals("*")
                || name.equals("CoalesceEmpty")
                // Allow parentheses but don't allow tuple
                || name.equals("()") && args.length == 1)
            {
                return null;
            }
            throw bomb;
        }

        public void handleMember(Member member) {
            if (member instanceof RolapStoredMeasure) {
                // If this member is in a different cube that previous members
                // we've seen, we cannot drill through.
                final RolapCube cube = ((RolapStoredMeasure) member).getCube();
                if (this.cube == null) {
                    this.cube = cube;
                } else if (this.cube != cube) {
                    // this measure lives in a different cube than previous
                    // measures we have seen
                    throw bomb;
                }
            } else if (member instanceof RolapCubeMember) {
                handleMember(((RolapCubeMember) member).member);
            } else if (member instanceof RolapHierarchy.RolapCalculatedMeasure measure)
            {
                measure.getFormula().getExpression().accept(this);
            } else if (member instanceof RolapMember) {
                // regular RolapMember - fine
            } else {
                // don't know what this is!
                throw bomb;
            }
        }

        @Override
		public Object visitNamedSetExpression(NamedSetExpression namedSetExpr) {
            throw Util.newInternal("not valid here: " + namedSetExpr);
        }

        @Override
		public Object visitLiteral(Literal literal) {
            return null; // literals are drillable
        }

        @Override
		public Object visitQuery(Query query) {
            throw Util.newInternal("not valid here: " + query);
        }

        @Override
		public Object visitQueryAxis(QueryAxis queryAxis) {
            throw Util.newInternal("not valid here: " + queryAxis);
        }

        @Override
		public Object visitFormula(Formula formula) {
            throw Util.newInternal("not valid here: " + formula);
        }

        @Override
		public Object visitUnresolvedFunCall(UnresolvedFunCall call) {
            throw Util.newInternal("expected resolved expression");
        }

        @Override
		public Object visitId(Id id) {
            throw Util.newInternal("expected resolved expression");
        }

        @Override
		public Object visitParameterExpression(ParameterExpression parameterExpr) {
            // Not valid in general; might contain complex expression
            throw bomb;
        }

        @Override
		public Object visitDimensionExpression(DimensionExpression dimensionExpr) {
            // Not valid in general; might be part of complex expression
            throw bomb;
        }

        @Override
		public Object visitHierarchyExpression(HierarchyExpression hierarchyExpr) {
            // Not valid in general; might be part of complex expression
            throw bomb;
        }

        @Override
		public Object visitLevelExpression(LevelExpression levelExpr) {
            // Not valid in general; might be part of complex expression
            throw bomb;
        }
    }
}
