/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * jhyde, 30 August, 2001
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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.CacheControl;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.agg.DrillThroughCellRequest;
import org.eclipse.daanse.rolap.common.agg.ListPredicate;
import org.eclipse.daanse.rolap.common.agg.MemberColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.OrPredicate;
import org.eclipse.daanse.rolap.common.agg.RangeColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.ValueColumnPredicate;
import org.eclipse.daanse.rolap.element.RolapBaseCubeMeasure;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeDimension;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.eclipse.daanse.rolap.function.def.visualtotals.VisualTotalMember;


/**
 * RolapAggregationManager manages all
 * {@link org.eclipse.daanse.rolap.common.agg.Segment}s in the system.
 *
 *  The bits of the implementation which depend upon dimensional concepts
 * RolapMember, etc.) live in this class, and the other bits live
 * in the derived class, {@link org.eclipse.daanse.rolap.common.agg.AggregationManager}.
 *
 * @author jhyde
 * @since 30 August, 2001
 */
public abstract class RolapAggregationManager {

    private final static String drillthroughCalculatedMember =
        "Can''t perform drillthrough operations because ''{0}'' is a calculated member.";

    /**
     * Creates the RolapAggregationManager.
     */
    protected RolapAggregationManager() {
    }

    /**
     * Creates a request to evaluate the cell identified by
     * members.
     *
     * If any of the members is the null member, returns
     * null, since there is no cell. If the measure is calculated, returns
     * null.
     *
     * @param members Set of members which constrain the cell
     * @return Cell request, or null if the requst is unsatisfiable
     */
    public static CellRequest makeRequest(final Member[] members) {
        return makeCellRequest(
            members, false, false, null, null, null,
            Collections.<OlapElement>emptyList());
    }

    /**
     * Creates a request for the fact-table rows underlying the cell identified
     * by members.
     *
     * If any of the members is the null member, returns null, since there
     * is no cell. If the measure is calculated, returns null.
     *
     * @param members           Set of members which constrain the cell
     *
     * @param extendedContext   If true, add non-constraining columns to the
     *                          query for levels below each current member.
     *                          This additional context makes the drill-through
     *                          queries easier for humans to understand.
     *
     * @param cube              Cube
     * @return Cell request, or null if the requst is unsatisfiable
     */
    public static DrillThroughCellRequest makeDrillThroughRequest(
        final Member[] members,
        final boolean extendedContext,
        RolapCube cube,
        List<OlapElement> returnClauseMembers)
    {
        assert cube != null;

        List<OlapElement> applicableMembers = getApplicableReturnClauseMembers(
            cube, members, returnClauseMembers, extendedContext);

        List<OlapElement> nonApplicableMembers =new ArrayList<OlapElement>();
        nonApplicableMembers.addAll(returnClauseMembers);
        nonApplicableMembers.removeAll(applicableMembers);

        return (DrillThroughCellRequest) makeCellRequest(
            members, true, extendedContext, cube, null, applicableMembers,
            nonApplicableMembers);
    }

    /**
     * Returns a List of OlapElement consisting of elements present on
     * the base cube in context.  If drillthrough is happening on a virtual
     * cube this may exclude non-conforming dimensions, for example.
     */
    private static List<OlapElement> getApplicableReturnClauseMembers(
        RolapCube cube, Member[] members,
        List<OlapElement> returnClauseMembers, boolean extendedContext)
    {
        if (!extendedContext || returnClauseMembers == null) {
            return Collections.emptyList();
        }
        List<OlapElement> applicableReturnClauseMembers =
            new ArrayList<>();
        final RolapCube drillthroughCube = getDrillthroughCube(cube, members);
        final Map<String, OlapElement> measureUniqueNameMap =
            getMeasureUniqueNameMap(drillthroughCube);

        for (OlapElement olapElement : returnClauseMembers) {
            if (isClosureFor(olapElement)) {
                continue;
            }
            if (olapElement instanceof RolapProperty) {
            	applicableReturnClauseMembers.add(olapElement);
            }
            if (hierarchyPresentOnCube(drillthroughCube, olapElement)) {
                applicableReturnClauseMembers.add(olapElement);
            } else if (measureUniqueNameMap
                .containsKey(olapElement.getUniqueName()))
            {
                // add the drillthrough cubes measure, not the one
                // potentially from a virtual cube
                applicableReturnClauseMembers.add(
                    measureUniqueNameMap.get(olapElement.getUniqueName()));
            }
        }
        return applicableReturnClauseMembers;
    }

    private static Map<String, OlapElement> getMeasureUniqueNameMap(
        RolapCube drillthroughCube)
    {
        final Map<String, OlapElement> measureUniqueNameMap =
            new HashMap<>();
        for (Member measureMember : drillthroughCube.getMeasures())  {
            measureUniqueNameMap
                .put(measureMember.getUniqueName(), measureMember);
        }
        return measureUniqueNameMap;
    }

    private static boolean hierarchyPresentOnCube(
        RolapCube cube, OlapElement olapElement)
    {
        return cube.getHierarchies().contains(olapElement.getHierarchy());
    }

    private static RolapCube getDrillthroughCube(
        RolapCube cube, Member[] members)
    {
        RolapCube drillthroughCube =
            RolapCell.chooseDrillThroughCube(members, cube);
        if (drillthroughCube == null) {
            drillthroughCube = cube;
        }
        return drillthroughCube;
    }

    private static boolean isClosureFor(OlapElement olapElement) {
        return olapElement.getHierarchy() instanceof RolapCubeHierarchy
            && ((RolapCubeHierarchy)olapElement.getHierarchy())
            .getRolapHierarchy().getClosureFor() != null;
    }

    /**
     * Creates a request to evaluate the cell identified by the context
     * specified in evaluator.
     *
     * If any of the members from the context is the null member, returns
     * null, since there is no cell. If the measure is calculated, returns
     * null.
     *
     * @param evaluator the cell specified by the evaluator context
     * @return Cell request, or null if the requst is unsatisfiable
     */
    public static CellRequest makeRequest(
        RolapEvaluator evaluator)
    {
        final CellRequest request =
            makeCellRequest(
                evaluator.getNonAllMembers(),
                false,
                false,
                null,
                evaluator, null,
                Collections.<OlapElement>emptyList());
        if (request == null) {
            // Current request cannot be processed. Per API, return null.
            return null;
        }
		List<?> aggList = evaluator.getAggregationLists();
		if (aggList == null || aggList.isEmpty()) {
            return request;
        }
        // convert aggregation lists to compound predicates
        if (!applyCompoundPredicates(evaluator, request)) {
            // unsatisfiable
            return null;
        }
        return request;
    }

    /**
     * Retrieves CompoundPredicateInfo for each tuple in the evaluator's
     * aggregationLists.  Uses this to add the predicate (and its string
     * representation) to the cell request.
     * Returns false if any predicate results in an unsatisfiable request.
     */
    private static boolean applyCompoundPredicates(
        RolapEvaluator evaluator, CellRequest request)
    {
        final Member[] currentMembers = evaluator.getNonAllMembers();
        final RolapStoredMeasure measure =
            (RolapStoredMeasure)currentMembers[0];
        for (List<List<Member>> aggregationList
            : evaluator.getAggregationLists())
        {
            CompoundPredicateInfo predicateInfo =
                    getCompoundPredicateInfo(
                        evaluator, measure, aggregationList);
            if (!predicateInfo.isSatisfiable()) {
                return false;
            }
            if (predicateInfo.getPredicate() != null) {
                request.addAggregateList(
                    predicateInfo.getBitKey(), predicateInfo.getPredicate());
                request.addPredicateString(predicateInfo.getPredicateString());
            }
        }
        return true;
    }

    /**
     * Retrieves CompoundPredicateInfo for the aggregationList.
     * If the aggregationList is the slicer, will attempt to retrieve the
     * pre-built slicer predicate info from the evaluator.  This avoids the
     * overhead of building the slicer predicates for every cell request.
     */
    private static CompoundPredicateInfo getCompoundPredicateInfo(
        RolapEvaluator evaluator,
        RolapStoredMeasure measure,
        List<List<Member>> aggregationList)
    {
        CompoundPredicateInfo predicateInfo;
        if (aggregationList.equals(evaluator.getSlicerTuples())) {
            // slicer predicate is built once in the evaluator to
            // avoid unnecessary duplicate effort
            predicateInfo = evaluator.getSlicerPredicateInfo();
            if (!measure.getCube().equalsOlapElement(predicateInfo.getCube())) {
                predicateInfo = new CompoundPredicateInfo(
                    aggregationList, measure, evaluator);
                evaluator.slicerPredicateInfo = predicateInfo;
            }
        } else {
            predicateInfo =  new CompoundPredicateInfo(
                aggregationList, measure, evaluator);
        }
        return predicateInfo;
    }

    private static CellRequest makeCellRequest(
        final Member[] members,
        boolean drillThrough,
        final boolean extendedContext,
        RolapCube cube,
        Evaluator evaluator, List<OlapElement> fieldsList,
        List<OlapElement> nonApplicableFields)
    {
        // Need cube for drill-through requests
        assert drillThrough == (cube != null);

        if (extendedContext) {
            assert (drillThrough);
        }

        final RolapStoredMeasure measure;
        if (drillThrough) {
            cube = RolapCell.chooseDrillThroughCube(members, cube);
            if (cube == null) {
                return null;
            }
            if (members.length > 0
                && members[0] instanceof RolapStoredMeasure)
            {
                measure = (RolapStoredMeasure) members[0];
            } else {
                measure = (RolapStoredMeasure) cube.getMeasures().get(0);
            }
        } else {
            if (members.length > 0
                && members[0] instanceof RolapStoredMeasure)
            {
                measure = (RolapStoredMeasure) members[0];
            } else {
                return null;
            }
        }

        final RolapStar.Measure starMeasure =
            (RolapStar.Measure) measure.getStarMeasure();
        assert starMeasure != null;
        final CellRequest request;
        if (drillThrough) {
            request =
                new DrillThroughCellRequest(
                    starMeasure, extendedContext, nonApplicableFields);
        } else {
            request =
                new CellRequest(starMeasure, extendedContext, drillThrough);
        }

        // Since 'request.extendedContext == false' is a well-worn code path,
        // we have moved the test outside the loop.
        if (extendedContext) {
            boolean isAllowDefaultMembers = true;
            if (fieldsList != null && !fieldsList.isEmpty()) {
                // If a field list was specified, there will be some columns
                // to include in the result set, other that we don't. This
                // happens when the MDX is a DRILLTHROUGH operation and
                // includes a RETURN clause.
                for (OlapElement member : fieldsList) {
                    addNonConstrainingColumns(member, cube, request);
                }
                isAllowDefaultMembers = false;
            }
            for (int i = 1; i < members.length; i++) {
                final RolapCubeMember member = (RolapCubeMember) members[i];
                if (member.getHierarchy().getRolapHierarchy().getClosureFor()
                    != null)
                {
                    continue;
                }

                if (!isAllowDefaultMembers && member.isAllMember()) {
                    continue;
                }

                addNonConstrainingColumns(member, cube, request);

                final RolapCubeLevel level = member.getLevel();
                final boolean needToReturnNull =
                    level.getLevelReader().constrainRequest(
                        member, measure.getCube(), request);
                if (needToReturnNull) {
                    return null;
                }
            }

        } else {
            for (int i = 1; i < members.length; i++) {
                if (!(members[i] instanceof RolapCubeMember member)) {
                    continue;
                }
                final RolapCubeLevel level = member.getLevel();
                final boolean needToReturnNull =
                    level.getLevelReader().constrainRequest(
                        member, measure.getCube(), request);
                if (needToReturnNull) {
                    // check to see if the current member is part of an ignored
                    // unrelated dimension
                    if (evaluator == null
                        || !evaluator.mightReturnNullForUnrelatedDimension()
                        || evaluator.needToReturnNullForUnrelatedDimension(
                            new Member[] {member})) {
                        return null;
                    }
                }
            }
        }
        return request;
    }

    /**
     * Adds the key columns as non-constraining columns. For
     * example, if they asked for [Gender].[M], [Store].[USA].[CA]
     * then the following levels are in play:
     *   Gender = 'M'
     *   Marital Status not constraining
     *   Nation = 'USA'
     *   State = 'CA'
     *   City not constraining
     *
     *
     * Note that [Marital Status] column is present by virtue of
     * the implicit [Marital Status].[All] member. Hence the SQL
     *
     *
     *   select [Marital Status], [City]
     *   from [Star]
     *   where [Gender] = 'M'
     *   and [Nation] = 'USA'
     *   and [State] = 'CA'
     *
     *
     * @param member Member to constraint
     * @param baseCube base cube if virtual
     * @param request Cell request
     */
    private static void addNonConstrainingColumns(
        final RolapCubeMember member,
        final RolapCube baseCube,
        final CellRequest request)
    {
        final RolapCubeHierarchy hierarchy = member.getHierarchy();
        final List<RolapCubeLevel> levels = (List<RolapCubeLevel>) hierarchy.getLevels();
        for (int j = levels.size() - 1, depth = member.getLevel().getDepth();
             j > depth; j--)
        {
            final RolapCubeLevel level = levels.get(j);
            RolapStar.Column column = level.getBaseStarKeyColumn(baseCube);
            if (column != null) {
                request.addConstrainedColumn(column, null);
                if (request.extendedContext
                    && level.getNameExp() != null)
                {
                    final RolapStar.Column nameColumn = column.getNameColumn();
                    Util.assertTrue(nameColumn != null);
                    request.addConstrainedColumn(nameColumn, null);
                }
            }
        }
    }

    private static void addNonConstrainingColumns(
        final OlapElement member,
        final RolapCube baseCube,
        final CellRequest request)
    {
        RolapCubeLevel level;
        if (member instanceof RolapCubeLevel) {
            level = (RolapCubeLevel) member;
        } else if (member instanceof RolapCubeHierarchy
            || member instanceof RolapCubeDimension)
        {
            level = (RolapCubeLevel) member.getHierarchy().getLevels().getFirst();
            if (level.isAll()) {
                level = level.getChildLevel();
            }
        } else if (member instanceof RolapProperty property) {
            RolapStar.Column propertyColumn = property.getColumn();
            if (propertyColumn != null) {
                request.addConstrainedColumn(propertyColumn, null);
                ((DrillThroughCellRequest)request).addDrillThroughColumn(propertyColumn);
            }
            return;
        } else if (member instanceof RolapStar.Measure) {
            ((DrillThroughCellRequest)request)
                .addDrillThroughMeasure((RolapStar.Measure)member);
            return;
        } else if (member instanceof RolapBaseCubeMeasure) {
            ((DrillThroughCellRequest)request)
                .addDrillThroughMeasure(
                    (RolapStar.Measure)
                        ((RolapBaseCubeMeasure)member).getStarMeasure());
            return;
        } else if (member instanceof RolapHierarchy.RolapCalculatedMeasure) {
            throw new OlapRuntimeException(MessageFormat.format(drillthroughCalculatedMember,
                member.getUniqueName()));
        } else {
            throw new OlapRuntimeException(
                "Unknown member type in DRILLTHROUGH operation.");
        }
        RolapStar.Column column = level.getBaseStarKeyColumn(baseCube);
        if (column != null) {
            request.addConstrainedColumn(column, null);
            ((DrillThroughCellRequest)request).addDrillThroughColumn(column);
            if (request.extendedContext
                && level.getNameExp() != null)
            {
                final RolapStar.Column nameColumn = column.getNameColumn();
                Util.assertTrue(nameColumn != null);
                request.addConstrainedColumn(nameColumn, null);
                ((DrillThroughCellRequest)request).addDrillThroughColumn(nameColumn);
            }

        }
    }

    /**
     * Groups members (or tuples) from the same compound (i.e. hierarchy) into
     * groups that are constrained by the same set of columns.
     *
     * E.g.
     *
     * Members
     *     [USA].[CA],
     *     [Canada].[BC],
     *     [USA].[CA].[San Francisco],
     *     [USA].[OR].[Portland]
     *
     * will be grouped into
     *
     * Group 1:
     *     {[USA].[CA], [Canada].[BC]}
     * Group 2:
     *     {[USA].[CA].[San Francisco], [USA].[OR].[Portland]}
     *
     * This helps with generating optimal form of sql.
     *
     * In case of aggregating over a list of tuples, similar logic also
     * applies.
     *
     * For example:
     *
     * Tuples:
     *     ([Gender].[M], [Store].[USA].[CA])
     *     ([Gender].[F], [Store].[USA].[CA])
     *     ([Gender].[M], [Store].[USA])
     *     ([Gender].[F], [Store].[Canada])
     *
     * will be grouped into
     *
     * Group 1:
     *     {([Gender].[M], [Store].[USA].[CA]),
     *      ([Gender].[F], [Store].[USA].[CA])}
     * Group 2:
     *     {([Gender].[M], [Store].[USA]),
     *      ([Gender].[F], [Store].[Canada])}
     *
     * This function returns a boolean value indicating if any constraint
     * can be created from the aggregationList. It is possible that only part
     * of the aggregationList can be applied, which still leads to a (partial)
     * constraint that is represented by the compoundGroupMap.
     */
    private static boolean makeCompoundGroup(
        int starColumnCount,
        RolapCube baseCube,
        List<List<RolapMember>> aggregationList,
        Map<BitKey, List<RolapCubeMember[]>> compoundGroupMap)
    {
        // The more generalized aggregation as aggregating over tuples.
        // The special case is a tuple defined by only one member.
        int unsatisfiableTupleCount = 0;
        for (List<RolapMember> aggregation : aggregationList) {
            boolean isTuple;
            if (aggregation.size() > 0
                && (aggregation.get(0) instanceof RolapCubeMember
                    || aggregation.get(0) instanceof VisualTotalMember))
            {
                isTuple = true;
            } else {
                ++unsatisfiableTupleCount;
                continue;
            }

            BitKey bitKey = BitKey.Factory.makeBitKey(starColumnCount);
            RolapCubeMember[] tuple;

            tuple = new RolapCubeMember[aggregation.size()];
            int i = 0;
            for (Member member : aggregation) {
                if (member instanceof VisualTotalMember) {
                    tuple[i] = (RolapCubeMember)
                        ((VisualTotalMember) member).getMember();
                } else {
                    tuple[i] = (RolapCubeMember)member;
                }
                i++;
            }

            boolean tupleUnsatisfiable = false;
            for (RolapCubeMember member : tuple) {
                // Tuple cannot be constrained if any of the member cannot be.
                tupleUnsatisfiable =
                    makeCompoundGroupForMember(member, baseCube, bitKey);
                if (tupleUnsatisfiable) {
                    // If this tuple is unsatisfiable, skip it and try to
                    // constrain the next tuple.
                    unsatisfiableTupleCount ++;
                    break;
                }
            }

            if (!tupleUnsatisfiable && !bitKey.isEmpty()) {
                // Found tuple(columns) to constrain,
                // now add it to the compoundGroupMap
                addTupleToCompoundGroupMap(tuple, bitKey, compoundGroupMap);
            }
        }

        return (unsatisfiableTupleCount == aggregationList.size());
    }

    private static void addTupleToCompoundGroupMap(
        RolapCubeMember[] tuple,
        BitKey bitKey,
        Map<BitKey, List<RolapCubeMember[]>> compoundGroupMap)
    {
        List<RolapCubeMember[]> compoundGroup = compoundGroupMap.get(bitKey);
        if (compoundGroup == null) {
            compoundGroup = new ArrayList<>();
            compoundGroupMap.put(bitKey, compoundGroup);
        }
        compoundGroup.add(tuple);
    }

    private static boolean makeCompoundGroupForMember(
        RolapCubeMember member,
        RolapCube baseCube,
        BitKey bitKey)
    {
        RolapCubeMember levelMember = member;
        boolean memberUnsatisfiable = false;
        while (levelMember != null) {
            RolapCubeLevel level = levelMember.getLevel();
            // Only need to constrain the nonAll levels
            if (!level.isAll()) {
                RolapStar.Column column = level.getBaseStarKeyColumn(baseCube);
                if (column != null) {
                    bitKey.set(column.getBitPosition());
                } else {
                    // One level in a member causes the member to be
                    // unsatisfiable.
                    memberUnsatisfiable = true;
                    break;
                }
            }

            levelMember = levelMember.getParentMember();
        }
        return memberUnsatisfiable;
    }

    /**
     * Translates a Map&lt;BitKey, List&lt;RolapMember&gt;&gt; of the same
     * compound member into {@link ListPredicate} by traversing a list of
     * members or tuples.
     *
     * 1. The example below is for list of tuples
     *
     *
     * group 1: [Gender].[M], [Store].[USA].[CA]<br/>
     * group 2: [Gender].[F], [Store].[USA].[CA]
     *
     *
     * is translated into
     *
     *
     * (Gender=M AND Store_State=CA AND Store_Country=USA)<br/>
     * OR<br/>
     * (Gender=F AND Store_State=CA AND Store_Country=USA)
     *
     *
     * The caller of this method will translate this representation into
     * appropriate SQL form as
     *
     * where (gender = 'M'<br/>
     *        and Store_State = 'CA'<br/>
     *        AND Store_Country = 'USA')<br/>
     *     OR (Gender = 'F'<br/>
     *         and Store_State = 'CA'<br/>
     *         AND Store_Country = 'USA')
     *
     *
     * 2. The example below for a list of members
     *
     * group 1: [USA].[CA], [Canada].[BC]<br/>
     * group 2: [USA].[CA].[San Francisco], [USA].[OR].[Portland]
     *
     *
     * is translated into:
     *
     *
     * (Country=USA AND State=CA)<br/>
     * OR (Country=Canada AND State=BC)<br/>
     * OR (Country=USA AND State=CA AND City=San Francisco)<br/>
     * OR (Country=USA AND State=OR AND City=Portland)
     *
     *
     * The caller of this method will translate this representation into
     * appropriate SQL form. For exmaple, if the underlying DB supports multi
     * value IN-list, the second group will turn into this predicate:
     *
     *
     * where (country, state, city) IN ((USA, CA, San Francisco),
     *                                      (USA, OR, Portland))
     *
     *
     * or, if the DB does not support multi-value IN list:
     *
     *
     * where country=USA AND
     *           ((state=CA AND city = San Francisco) OR
     *            (state=OR AND city=Portland))
     *
     *
     * @param compoundGroupMap Map from dimensionality to groups
     * @param baseCube base cube if virtual
     * @return compound predicate for a tuple or a member
     */
    private static StarPredicate makeCompoundPredicate(
        Map<BitKey, List<RolapCubeMember[]>> compoundGroupMap,
        RolapCube baseCube)
    {
        List<StarPredicate> compoundPredicateList =
            new ArrayList<> ();
        for (List<RolapCubeMember[]> group : compoundGroupMap.values()) {
             // e.g {[USA].[CA], [Canada].[BC]}
            StarPredicate compoundGroupPredicate = null;
            for (RolapCubeMember[] tuple : group) {
                // [USA].[CA]
                StarPredicate tuplePredicate = null;

                for (RolapCubeMember member : tuple) {
                    tuplePredicate = makeCompoundPredicateForMember(
                        member, baseCube, tuplePredicate);
                }
                if (tuplePredicate != null) {
                    if (compoundGroupPredicate == null) {
                        compoundGroupPredicate = tuplePredicate;
                    } else {
                        compoundGroupPredicate =
                            compoundGroupPredicate.or(tuplePredicate);
                    }
                }
            }

            if (compoundGroupPredicate != null) {
                // Sometimes the compound member list does not constrain any
                // columns; for example, if only AllLevel is present.
                compoundPredicateList.add(compoundGroupPredicate);
            }
        }

        StarPredicate compoundPredicate = null;

        if (compoundPredicateList.size() > 1) {
            compoundPredicate = new OrPredicate(compoundPredicateList);
        } else if (compoundPredicateList.size() == 1) {
            compoundPredicate = compoundPredicateList.get(0);
        }

        return compoundPredicate;
    }

    private static StarPredicate makeCompoundPredicateForMember(
        RolapCubeMember member,
        RolapCube baseCube,
        StarPredicate memberPredicate)
    {
        while (member != null) {
            RolapCubeLevel level = member.getLevel();
            if (!level.isAll()) {
                RolapStar.Column column = level.getBaseStarKeyColumn(baseCube);
                if (memberPredicate == null) {
                    memberPredicate =
                        new ValueColumnPredicate(column, member.getKey());
                } else {
                    memberPredicate =
                        memberPredicate.and(
                            new ValueColumnPredicate(column, member.getKey()));
                }
            }
            // Don't need to constrain USA if CA is unique
            if (member.getLevel().isUnique()) {
                break;
            }
            member = member.getParentMember();
        }
        return memberPredicate;
    }

    /**
     * Retrieves the value of a cell from the cache.
     *
     * @param request Cell request
     *  request != null and !request.isUnsatisfiable()
     * @return Cell value, or null if cell is not in any aggregation in cache,
     *   or {@link Util#nullValue} if cell's value is null
     */
    public abstract Object getCellFromCache(CellRequest request);

    public abstract Object getCellFromCache(
        CellRequest request,
        PinSet pinSet);

    /**
     * Generates a SQL statement which will return the rows which contribute to
     * this request.
     *
     * @param request Cell request
     * @param countOnly If true, return a statment which returns only the count
     * @param starPredicateSlicer A StarPredicate representing slicer positions
     * that could not be represented by the CellRequest, or
     * null if no additional predicate is necessary.
     * @return SQL statement
     */
    public abstract String getDrillThroughSql(
        DrillThroughCellRequest request,
        StarPredicate starPredicateSlicer,
        List<OlapElement> fields,
        boolean countOnly);

    public static RolapCacheRegion makeCacheRegion(
        final RolapStar star,
        final CacheControl.CellRegion region)
    {
        final List<Member> measureList = CacheControlImpl.findMeasures(region);
        final List<RolapStar.Measure> starMeasureList =
            new ArrayList<>();
        RolapCube baseCube = null;
        for (Member measure : measureList) {
            if (!(measure instanceof RolapStoredMeasure storedMeasure)) {
                continue;
            }
            final RolapStar.Measure starMeasure =
                (RolapStar.Measure) storedMeasure.getStarMeasure();
            assert starMeasure != null;
            if (star != starMeasure.getStar()) {
                continue;
            }
            // TODO: each time this code executes, baseCube is set.
            // Should there be a 'break' here? Are all of the
            // storedMeasure cubes the same cube? Is the measureList always
            // non-empty so that baseCube is always set?
            baseCube = storedMeasure.getCube();
            starMeasureList.add(starMeasure);
        }
        final RolapCacheRegion cacheRegion =
            new RolapCacheRegion(star, starMeasureList);
        if (region instanceof CacheControlImpl.CrossjoinCellRegion crossjoin) {
            for (CacheControl.CellRegion component
                : crossjoin.getComponents())
            {
                constrainCacheRegion(cacheRegion, baseCube, component);
            }
        } else {
            constrainCacheRegion(cacheRegion, baseCube, region);
        }
        return cacheRegion;
    }

    private static void constrainCacheRegion(
        final RolapCacheRegion cacheRegion,
        final RolapCube baseCube,
        final CacheControl.CellRegion region)
    {
        if (region instanceof CacheControlImpl.MemberCellRegion memberCellRegion) {
            final List<Member> memberList = memberCellRegion.getMemberList();
            for (Member member : memberList) {
                if (member.isMeasure()) {
                    continue;
                }
                final RolapCubeMember rolapMember;
                if (member instanceof RolapCubeMember) {
                    rolapMember = (RolapCubeMember) member;
                } else {
                    rolapMember =
                        (RolapCubeMember) baseCube.getCatalogReader()
                            .getMemberByUniqueName(
                                Util.parseIdentifier(member.getUniqueName()),
                                true);
                }
                final RolapCubeLevel level = rolapMember.getLevel();
                RolapStar.Column column = level.getBaseStarKeyColumn(baseCube);

                level.getLevelReader().constrainRegion(
                    new MemberColumnPredicate(column, rolapMember),
                    baseCube,
                    cacheRegion);
            }
        } else if (region instanceof CacheControlImpl.MemberRangeCellRegion rangeRegion) {
            final RolapCubeLevel level = (RolapCubeLevel)rangeRegion.getLevel();
            RolapStar.Column column = level.getBaseStarKeyColumn(baseCube);

            level.getLevelReader().constrainRegion(
                new RangeColumnPredicate(
                    column,
                    rangeRegion.getLowerInclusive(),
                    (rangeRegion.getLowerBound() == null
                     ? null
                     : new MemberColumnPredicate(
                         column, rangeRegion.getLowerBound())),
                    rangeRegion.getUpperInclusive(),
                    (rangeRegion.getUpperBound() == null
                     ? null
                     : new MemberColumnPredicate(
                         column, rangeRegion.getUpperBound()))),
                baseCube,
                cacheRegion);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns a {@link org.eclipse.daanse.rolap.common.CellReader} which reads cells from cache.
     */
    public CellReader getCacheCellReader() {
        return new CellReader() {
            // implement CellReader
            @Override
			public Object get(RolapEvaluator evaluator) {
                CellRequest request = makeRequest(evaluator);
                if (request == null || request.isUnsatisfiable()) {
                    // request out of bounds
                    return Util.nullValue;
                }
                return getCellFromCache(request);
            }

            @Override
			public int getMissCount() {
                return 0; // RolapAggregationManager never lies
            }

            @Override
			public boolean isDirty() {
                return false;
            }
        };
    }

    /**
     * Creates a {@link PinSet}.
     *
     * @return a new PinSet
     */
    public abstract PinSet createPinSet();

    /**
     * A set of segments which are pinned (prevented from garbage collection)
     * for a short duration as a result of a cache inquiry.
     */
    public interface PinSet {
    }
}
