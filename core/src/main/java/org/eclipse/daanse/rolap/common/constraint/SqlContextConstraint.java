/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.common.constraint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapAggregationManager;
import org.eclipse.daanse.rolap.common.agg.AndPredicate;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.agg.ListColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.MemberColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.OrPredicate;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner;
import org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.util.SqlExpressionResolver;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.common.sql.AggPlan;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution;
import org.eclipse.daanse.rolap.common.sql.ContributionResult;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.MultiCardinalityDefaultMember;
import org.eclipse.daanse.rolap.element.RolapCalculatedMember;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy.LimitedRollupMember;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.eclipse.daanse.rolap.element.RolapVirtualCube;

/**
 * limits the result of a Member SQL query to the current evaluation context.
 * All Members of the current context are joined against the fact table and only
 * those rows are returned, that have an entry in the fact table.
 *
 * For example, if you have two dimensions, "invoice" and "time", and the
 * current context (e.g. the slicer) contains a day from the "time" dimension,
 * then only the invoices of that day are found. Used to optimize NON EMPTY.
 *
 *  The {@link TupleConstraint} methods may silently ignore calculated
 * members (depends on the strict c'tor argument), so these may
 * return more members than the current context restricts to. The
 * MemberChildren methods will never accept calculated members as parents,
 * these will cause an exception.
 *
 * @author av
 * @since Nov 2, 2005
 */
public class SqlContextConstraint
    implements MemberChildrenConstraint, TupleConstraint
{
    private final List<Object> cacheKey;
    private RolapEvaluator evaluator;
    private boolean strict;

    /** Diagnostic: why a context contribution bailed to the recorder (reference) path. */
    private static final org.slf4j.Logger BAIL_LOG =
        org.slf4j.LoggerFactory.getLogger("daanse.sql.gen.bail");

    private ContributionResult bail(String reason) {
        if (BAIL_LOG.isDebugEnabled()) {
            try {
                BAIL_LOG.debug("contextContribution bail reason={} cube={} members={} slicer={}",
                    reason, evaluator.getCube().getName(),
                    java.util.Arrays.toString(evaluator.getMembers()),
                    evaluator.getSlicerMembers());
            } catch (RuntimeException ignore) {
                BAIL_LOG.debug("contextContribution bail reason={}", reason);
            }
        }
        return ContributionResult.unsupported(reason);
    }

    /**
     * Fail-fast for a defensive-impossible context shape: the reader recorder is runtime-dead
     * for the constraint SPI, so a producer that would have
     * bailed here to the recorder path is a genuine, unmodelled read that must surface
     * loudly rather than silently drop its WHERE.
     */
    protected IllegalStateException dead(String reason) {
        String ctx;
        try {
            ctx = "cube=" + evaluator.getCube().getName()
                + " members=" + java.util.Arrays.toString(evaluator.getMembers())
                + " slicer=" + evaluator.getSlicerMembers();
        } catch (RuntimeException ignore) {
            ctx = "<context-unavailable>";
        }
        return new IllegalStateException(
            "context read shape not modellable by the builder: " + reason + " (" + ctx + ")");
    }

    /**
     * @param context evaluation context
     * @param strict false if more rows than requested may be returned
     * (i.e. the constraint is incomplete)
     *
     * @return false if this contstraint will not work for the current context
     */
    public static boolean isValidContext(Evaluator context, boolean strict) {
        return isValidContext(context, true, null, strict);
    }

    /**
     * @param context evaluation context
     * @param disallowVirtualCube if true, check for virtual cubes
     * @param levels levels being referenced in the current context
     * @param strict false if more rows than requested may be returned
     * (i.e. the constraint is incomplete)
     *
     * @return false if constraint will not work for current context
     */
    public static boolean isValidContext(
        Evaluator context,
        boolean disallowVirtualCube,
        Level [] levels,
        boolean strict)
    {
        if (context == null) {
            return false;
        }
        RolapCube cube = (RolapCube) context.getCube();
        if (disallowVirtualCube && cube instanceof RolapVirtualCube) {
            return false;
        }
        if (cube instanceof RolapVirtualCube) {
            Query query = context.getQuery();
            Set<Cube> baseCubes = new HashSet<>();
            List<Cube> baseCubeList = new ArrayList<>();
            if (!findVirtualCubeBaseCubes(query, baseCubes, baseCubeList)) {
                return false;
            }
            if (levels == null) {
                throw new IllegalArgumentException("levels should not be null");
            }
            query.setBaseCubes(baseCubeList);
        }

        if (MeasureConflictDetector.measuresConflictWithMembers(
                context.getQuery().getMeasuresMembers(), context.getMembers()))
        {
            // one or more dimension members referenced within measure calcs
            // conflict with the context members.  Not safe to apply
            // SqlContextConstraint.
            return false;
        }

        // may return more rows than requested?
        if (!strict) {
            return true;
        }

        // we can not handle all calc members in slicer. Calc measure and some
        // like aggregates are exceptions
        Member[] members = context.getMembers();
        for (int i = 1; i < members.length; i++) {
            if (members[i].isCalculated()
                && !CalculatedMemberExpander.isSupportedCalculatedMember(members[i]))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Locates base cubes related to the measures referenced in the query.
     *
     * @param query query referencing the virtual cube
     * @param baseCubes set of base cubes
     *
     * @return true if valid measures exist
     */
    private static boolean findVirtualCubeBaseCubes(
        Query query,
        Set<Cube> baseCubes,
        List<Cube> baseCubeList)
    {
        // Gather the unique set of level-to-column maps corresponding
        // to the underlying star/cube where the measure column
        // originates from.
        Set<Member> measureMembers = query.getMeasuresMembers();
        // if no measures are explicitly referenced, just use the default
        // measure
        if (measureMembers.isEmpty()) {
            Cube cube = query.getCube();
            Dimension dimension = cube.getDimensions().getFirst();
            query.addMeasuresMembers(
                dimension.getHierarchy().getDefaultMember());
        }
        for (Member member : query.getMeasuresMembers()) {
            if (member instanceof RolapStoredMeasure rolapStoredMeasure) {
                addMeasure(
                    rolapStoredMeasure, baseCubes, baseCubeList);
            } else if (member instanceof RolapCalculatedMember) {
                findMeasures(member.getExpression(), baseCubes, baseCubeList);
            }
        }

        return !baseCubes.isEmpty();
    }

    /**
     * Adds information regarding a stored measure to maps
     *
     * @param measure the stored measure
     * @param baseCubes set of base cubes
     */
    private static void addMeasure(
        RolapStoredMeasure measure,
        Set<Cube> baseCubes,
        List<Cube> baseCubeList)
    {
        RolapCube baseCube = measure.getCube();
        if (baseCubes.add(baseCube)) {
            baseCubeList.add(baseCube);
        }
    }

    /**
     * Extracts the stored measures referenced in an expression
     *
     * @param exp expression
     * @param baseCubes set of base cubes
     */
    private static void findMeasures(
        Expression exp,
        Set<Cube> baseCubes,
        List<Cube> baseCubeList)
    {
        if (exp instanceof MemberExpression memberExpr) {
            Member member = memberExpr.getMember();
            if (member instanceof RolapStoredMeasure rolapStoredMeasure) {
                addMeasure(
                    rolapStoredMeasure, baseCubes, baseCubeList);
            } else if (member instanceof RolapCalculatedMember) {
                findMeasures(member.getExpression(), baseCubes, baseCubeList);
            }
        } else if (exp instanceof ResolvedFunCallImpl funCall) {
            Expression [] args = funCall.getArgs();
            for (Expression arg : args) {
                findMeasures(arg, baseCubes, baseCubeList);
            }
        }
    }

    /**
    * Creates a SqlContextConstraint.
    *
    * @param evaluator Evaluator
    * @param strict defines the behaviour if the evaluator context
    * contains calculated members. If true, an exception is thrown,
    * otherwise calculated members are silently ignored. The
    * {@link org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint} member-constraint
    * methods will never accept a calculated member as parent.
    */
    public SqlContextConstraint(RolapEvaluator evaluator, boolean strict) {
        this.evaluator = evaluator.push();
        this.strict = strict;
        cacheKey = new ArrayList<>();
        cacheKey.add(getClass());
        cacheKey.add(strict);

        List<Member> members = new ArrayList<>();
        List<Member> expandedMembers = new ArrayList<>();

        members.addAll(
            Arrays.asList(
                CalculatedMemberExpander.expandMultiPositionSlicerMembers(
                    evaluator.getMembers(), evaluator)));

        // Now we'll need to expand the aggregated members
        expandedMembers.addAll(
            CalculatedMemberExpander.expandSupportedCalculatedMembers(
                members,
                evaluator).getMembers());
        cacheKey.add(expandedMembers);
        cacheKey.add(evaluator.getSlicerTuples());

        // Add restrictions imposed by Role based access filtering
        Map<Level, List<RolapMember>> roleMembers =
            ContextConstraintWriter.getRoleConstraintMembers(
                this.getEvaluator().getCatalogReader(),
                this.getEvaluator().getMembers());
        for (List<RolapMember> list : roleMembers.values()) {
            cacheKey.addAll(list);
        }

        // MONDRIAN-2597
        //For virtual cube we add all base cubes
        //associated with this virtual cube to the key
        if (evaluator.getCube() instanceof RolapVirtualCube) {
            cacheKey.addAll(evaluator.getCube().getBaseCubes());
        }
    }


    /**
     * The generic-builder counterpart of the member-children recorder SPI: the parent's
     * children restricted to the current context, as a
     * {@link ConstraintContribution} (builder {@code WHERE} predicate + the star tables to join).
     * <p>
     * Mirrors the recorded sequence — {@code addContextConstraint} (one {@code col = value} per
     * single-valued context column) then the parent-key restriction — and lists the columns' tables
     * so the mapper joins them to the fact. A virtual-cube context is translated against the base
     * cube identified by the context measure. An aggregate-table routing (non-null {@code aggStar})
     * is translated agg-substituted with an attached {@link AggPlan} (the agg-join channel — dormant
     * until the agg routing). Returns {@link java.util.Optional#empty()} (→ the
     * recorder path) for everything outside that scope: a calculated parent, a
     * calculated dimension member the class's {@link #executedCalcLift()} mode bails on, an
     * agg-unresolvable column, or a
     * slicer/role shape the pure predicate builders cannot express. A present contribution is executed authoritatively
     * ({@code QueryBuildContext} renders without runtime fallback — the routing condition IS the
     * guard); an empty one routes to the recorder.
     */
    @Override
    public ContributionResult toContribution(
        RolapCube baseCube,
        AggStar aggStar,
        RolapMember parent)
    {
        if (parent.isCalculated()) {
            return ContributionResult.unsupported("calculated parent member");
        }
        // The member-children form takes no set composition (mirrored by the lifted parent twin),
        // so the class's executed calc mode is applied to the plain context leg directly: LIFTED
        // for EVERY family (plain context, Filter, TopCount and the SetConstraint/NECJ family).
        return contextContribution(baseCube, aggStar, java.util.Optional.of(parent),
            executedCalcLift());
    }

    /**
     * The generic-builder counterpart of {@code addConstraint} (level members): the current context
     * as a {@link ConstraintContribution}, no parent-key restriction.
     */
    @Override
    public ContributionResult toContribution(
        RolapCube baseCube,
        AggStar aggStar)
    {
        // ONE executed dispatch for the whole family: the class's executed calc mode is
        // threaded through the VIRTUAL mode-threaded chain, so a SET composition
        // (Filter/TopCount/CrossJoin — subclasses of RolapNativeSet.SetConstraint) composes its
        // cross-join args / HAVING / native order over the base leg in its own mode. This 2-arg
        // variant is consumed only by the tuple reader.
        return toContribution(baseCube, aggStar, executedCalcLift());
    }

    /**
     * The calculated-member gate mode this class's EXECUTED {@code toContribution} routes run with
     * — the per-family switch (the routing condition IS the guard; no runtime
     * fallback beyond the documented bails). EVERY family runs
     * {@link CalcLift#LIFTED}: this base class ({@code calc-context}), the
     * {@code RolapNativeFilter.FilterConstraint} / {@code RolapNativeTopCount.TopCountConstraint}
     * overrides ({@code calc-filter} / {@code calc-topcount}) and
     * {@code RolapNativeSet.SetConstraint} ({@code calc-set}, chain-contiguous). The enum +
     * per-class override stay as the one-override REVERT switch. Public
     * for the cross-package overrides and the unit pins.
     */
    public CalcLift executedCalcLift() {
        return CalcLift.LIFTED;
    }

    /**
     * The calculated-member gate mode of {@link #contextContribution}, threaded through the whole
     * {@code toContribution} composition chain. The executed mode is per
     * constraint class ({@link #executedCalcLift()} — {@code LIFTED} for every family).
     */
    public enum CalcLift {
        /**
         * The SET-composition mode: BOTH calculated-dimension-member classes bail — a
         * supported calc member in context or slicer routes the whole composition to the
         * recorder. No executed caller (every family runs
         * {@link #LIFTED}); kept as the documented one-override REVERT value. The
         * {@code calc-*-member-supported:*} bail lines fire ONLY in this mode, so with every
         * family LIFTED they are structurally unreached — the calc-set reads that still decline do
         * so via their deeper composition bails (the {@code unliftable} residue, e.g.
         * {@code set-base-context-empty}).
         */
        COMPOSED,
        /**
         * The expanded calc gate (the executed mode — plain context,
         * Filter, TopCount both channels): a SUPPORTED calc
         * member falls through into the expansion (which reproduces the recorder's calc handling,
         * with the writer-parity LAZY slicer expansion); an UNSUPPORTED calc member on a
         * NON-strict constraint is DROPPED instead of bailing (writer parity:
         * {@code makeContextConstraintSet} → {@code removeCalculatedAndDefaultMembers} simply
         * removes it; the strict form keeps bailing — the writer throws there).
         */
        LIFTED
    }

    /**
     * The mode-threaded chain behind the {@code toContribution} family: subclasses
     * (Set/TopCount/Filter) override it so the {@link CalcLift} mode reaches the inherited base
     * context THROUGH their own full composition (args, HAVING, native order, AggPlan). The base
     * implementation is the plain context leg. The executed dispatch passes
     * {@link #executedCalcLift()} (per-class — the one-override revert switch).
     */
    protected ContributionResult toContribution(
        RolapCube baseCube, AggStar aggStar, CalcLift lift)
    {
        return contextContribution(baseCube, aggStar, java.util.Optional.empty(), lift);
    }

    /**
     * The authoritative builder WHERE for the collapsed single-column aggStar member-children
     * read (executed via {@code QueryBuildContext} when present). Reproduces the WHERE that the
     * member-children recorder route built for a collapsed level (the context columns after
     * {@code setContext(parent)} PLUS the
     * parenthesised parent-key group) as one builder {@link org.eclipse.daanse.sql.statement.api.expression.Predicate},
     * but with every column resolved to its AGGREGATE-table node
     * ({@code aggStar.lookupColumn(bitPos).toSqlExpression()}) instead of the base column — mirroring
     * the recorder's agg substitution in {@code ContextConstraintWriter.getColumnNode} /
     * {@code MemberConstraintWriter.dialectFreeColumnNode}.
     * <p>
     * {@link java.util.Optional#empty()} (→ the recorder path) whenever the shape is
     * outside this simplified twin: a null aggStar, a calculated parent, a virtual cube, a calculated
     * context/slicer dimension member, any slicer tuple / disjoint-tuple / multi-valued-slicer /
     * role-access restriction (not reproduced here), a column with no agg node, or an unresolvable
     * parent-key column.
     */
    public java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate>
        collapsedAggWhere(AggStar aggStar, RolapMember parent)
    {
        return collapsedAggWhereState(aggStar, parent).where();
    }

    /**
     * The empty-WHERE outcome of {@link #collapsedAggWhereState} / {@link #levelMembersAggWhereState}
     * disambiguated: {@code bailed} distinguishes a shape the agg twin does NOT reproduce (the
     * recorder MUST stay — {@code aggContextColumnPredicates} returned {@code null}) from a genuinely
     * UNCONSTRAINED read (the context contributes no column, so an empty WHERE is the faithful
     * translation). The consumers' decline-attribution logs use the distinction; {@code where()}
     * carries exactly what the pre-tri-state methods returned, so existing callers keep their
     * behavior by delegation.
     */
    public record AggWhereResult(
            boolean bailed,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where)
    {
        /** Shape not reproduced — the recorder MUST stay. Public for the candidate twin
         *  ({@code RolapNativeSet.SetConstraint.levelMembersAggWhereCandidateState}). */
        public static final AggWhereResult BAIL =
            new AggWhereResult(true, java.util.Optional.empty());
        /** Genuinely unconstrained — an empty WHERE is the faithful translation. */
        public static final AggWhereResult UNCONSTRAINED =
            new AggWhereResult(false, java.util.Optional.empty());

        public static AggWhereResult of(
                org.eclipse.daanse.sql.statement.api.expression.Predicate predicate)
        {
            return new AggWhereResult(false, java.util.Optional.of(predicate));
        }
    }

    /**
     * Tri-state variant of {@link #collapsedAggWhere(AggStar, RolapMember)}: same translation, but an
     * empty result reports whether the twin BAILED (shape not reproduced → recorder mandatory) or the
     * read is genuinely UNCONSTRAINED (context + parent group contribute no predicate). The consumers'
     * decline-attribution logs use the distinction; {@link #collapsedAggWhere} delegates here so
     * nothing else changes.
     */
    public AggWhereResult collapsedAggWhereState(AggStar aggStar, RolapMember parent)
    {
        if (aggStar == null || parent.isCalculated()) {
            return AggWhereResult.BAIL;
        }
        // The agg-substituted context columns (after setContext(parent)); null == a shape outside the
        // twin (virtual cube, calc member, slicer tuple, role access, missing agg node).
        List<AggPlan.AggColumnPredicate> predicates =
            aggContextColumnPredicates(aggStar, parent);
        if (predicates == null) {
            return AggWhereResult.BAIL;
        }
        try {
            RolapCube cube = (RolapCube) evaluator.getCube();
            // The parenthesised parent-key group (addMemberConstraint), agg-substituted. A null return
            // signals an unresolvable / missing-agg column -> recorder path.
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> parentGroup =
                aggMemberKeyGroup(aggStar, cube, parent);
            if (parentGroup == null) {
                return AggWhereResult.BAIL;
            }
            List<org.eclipse.daanse.sql.statement.api.expression.Predicate> all = new ArrayList<>();
            for (AggPlan.AggColumnPredicate acp : predicates) {
                all.add(acp.predicate());
            }
            parentGroup.ifPresent(all::add);
            if (all.isEmpty()) {
                // Reproduced faithfully AND empty: all-member parent (no key group) with no context
                // column — an unconstrained children read, not a bail.
                return AggWhereResult.UNCONSTRAINED;
            }
            return AggWhereResult.of(
                org.eclipse.daanse.sql.statement.api.Predicates.and(all));
        } catch (RuntimeException e) {
            return AggWhereResult.BAIL;
        }
    }

    /**
     * The authoritative builder WHERE for the collapsed single-column aggStar LEVEL-MEMBERS
     * read — the tuple-reader twin of {@link
     * #collapsedAggWhere(AggStar, RolapMember)}. Reproduces the WHERE that the tuple-reader
     * recorder route built for an all-collapsed
     * level-members read ({@code ContextConstraintWriter.addContextConstraint} — the context columns
     * only, NO parent-key group, since a level read has no parent member) as one builder
     * {@link org.eclipse.daanse.sql.statement.api.expression.Predicate}, with every column resolved to
     * its AGGREGATE-table node ({@code aggStar.lookupColumn(bitPos).toSqlExpression()}).
     * <p>
     * {@link java.util.Optional#empty()} whenever the shape is outside this simplified twin (the same
     * classes {@link #collapsedAggWhere} bails on) OR the context contributes no column (a
     * fully unconstrained read has no WHERE).
     */
    public java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate>
        levelMembersAggWhere(AggStar aggStar)
    {
        return levelMembersAggWhereState(aggStar).where();
    }

    /**
     * Tri-state variant of {@link #levelMembersAggWhere(AggStar)}: same translation, but an empty
     * result reports whether the twin BAILED ({@code aggContextColumnPredicates} returned {@code null}
     * — virtual cube, calc/slicer exotic, role access, missing agg node, builder-time exception) or
     * the read is genuinely UNCONSTRAINED (the context contributes no column, so an empty WHERE is the
     * faithful translation). The consumers' decline-attribution logs use the distinction;
     * {@link #levelMembersAggWhere} delegates here so nothing else changes.
     */
    public AggWhereResult levelMembersAggWhereState(AggStar aggStar)
    {
        if (aggStar == null) {
            return AggWhereResult.BAIL;
        }
        List<AggPlan.AggColumnPredicate> predicates =
            aggContextColumnPredicates(aggStar, null);
        if (predicates == null) {
            return AggWhereResult.BAIL;
        }
        if (predicates.isEmpty()) {
            return AggWhereResult.UNCONSTRAINED;
        }
        List<org.eclipse.daanse.sql.statement.api.expression.Predicate> conjuncts = new ArrayList<>();
        for (AggPlan.AggColumnPredicate acp : predicates) {
            conjuncts.add(acp.predicate());
        }
        return AggWhereResult.of(
            org.eclipse.daanse.sql.statement.api.Predicates.and(conjuncts));
    }

    /**
     * The candidate twin of {@link #levelMembersAggWhereState(AggStar)}: the
     * aggStar collapsed level-members WHERE <em>including</em> the constraint's own restrictions
     * beyond the evaluation context. For a plain context constraint the two are the same — this
     * delegates. {@code RolapNativeSet.SetConstraint} overrides it to append its per-arg member
     * restrictions (the recorder's {@code arg.addConstraint} leg that the live twin does not
     * model, misclassifying such reads as UNCONSTRAINED — e.g. a {@code DescendantsCrossJoinArg}
     * parent's {@code the_year = 1997}).
     * <p>
     * Consumed AUTHORITATIVELY by the level-members collapsed-aggStar route
     * ({@code SqlTupleReader.aggCollapsedLevelMembersSql}): when the live tri-state
     * carries no WHERE, a non-bailed candidate decides the read. The member-children branch
     * stays a documented recorder decline.
     */
    public AggWhereResult levelMembersAggWhereCandidateState(AggStar aggStar)
    {
        return levelMembersAggWhereState(aggStar);
    }

    /**
     * The agg-substituted native HAVING for the collapsed single-column
     * level-members read. A plain context constraint carries no native HAVING
     * ({@link java.util.Optional#empty()}); a native-filter subclass
     * ({@code RolapNativeFilter.FilterConstraint}) overrides it to compile its filter condition against
     * the agg columns — the SAME {@code RolapNativeSql}+{@code aggStar} channel its own
     * {@code toContribution} uses, which cannot serve here because the context contribution bails on a
     * non-null aggStar ({@code aggStar-mapper-has-no-agg-column-projection}). Reproduces the recorder's
     * HAVING that the WHERE-only route would otherwise drop.
     */
    public java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate>
        levelMembersAggHaving(AggStar aggStar)
    {
        return java.util.Optional.empty();
    }

    /**
     * The agg-substituted native HAVING TOGETHER with the FROM joins its compile performs on the
     * recorder path: {@code RolapNativeSql}'s Caption/Name
     * MATCHES compiler joins the referenced dimension table into the executing query's FROM
     * ({@code ctx.addToFrom} — the whole snowflake subset under
     * {@code FilterChildlessSnowflakeMembers}) whenever the filter source does not resolve to an
     * agg column. A plain context constraint compiles nothing — no HAVING, no joins; the
     * native-filter subclass ({@code RolapNativeFilter.FilterConstraint}) overrides this with a
     * collecting scratch compile ({@code NativeSqlContext.scratchCollecting}). A consumer building
     * an authoritative/candidate agg statement must replay {@code joins()} into its FROM
     * ({@code AggTupleQueries.aggTupleLevelMembersSql}) or the byte contract breaks whenever the
     * filter references a dim-table caption/name.
     */
    public AggHaving levelMembersAggHavingWithJoins(AggStar aggStar) {
        return new AggHaving(levelMembersAggHaving(aggStar), java.util.List.of());
    }

    /** The result pair of {@link #levelMembersAggHavingWithJoins}: the compiled HAVING predicate
     *  (empty when the constraint carries none) plus the FROM registrations its compile performed. */
    public record AggHaving(
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> having,
        java.util.List<org.eclipse.daanse.rolap.common.sqlbuild.AggTupleQueries.HavingJoin> joins) {
    }

    /**
     * The agg-substituted native measure ORDER for the collapsed
     * single-column level-members read. A plain context constraint carries no
     * native order ({@link java.util.Optional#empty()}); a native TopCount/BottomCount subclass
     * ({@code RolapNativeTopCount.TopCountConstraint}) overrides it to compile its order expression
     * against the agg columns.
     */
    public java.util.Optional<ConstraintContribution.NativeOrder>
        levelMembersAggOrder(AggStar aggStar)
    {
        return java.util.Optional.empty();
    }

    /**
     * The agg substitution rule of {@code ContextConstraintWriter.getColumnNode} as ONE
     * implementation: the aggregate-table column registered for the base column's
     * bit position, or {@code null} when the agg star has no usable node for it (no column, or a
     * column without a dialect-free expression) — the caller's bail signal.
     */
    private static AggStar.Table.Column resolvedAggColumn(AggStar aggStar, RolapStar.Column column) {
        AggStar.Table.Column aggColumn = aggStar.lookupColumn(column.getBitPosition());
        return (aggColumn == null || aggColumn.toSqlExpression() == null) ? null : aggColumn;
    }

    /**
     * One context column's value restriction as a builder predicate. The null sentinel
     * {@code Util.sqlNullValue} is NOT Java null (its {@code toString()} is {@code "#null"}, which
     * must render as {@code IS NULL}, never leak as a literal) — matching
     * {@code StarPredicateTranslator}.
     */
    private static org.eclipse.daanse.sql.statement.api.expression.Predicate contextValuePredicate(
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression col, Object value,
        org.eclipse.daanse.sql.model.type.Datatype datatype)
    {
        return (value == null || value == Util.sqlNullValue)
            ? org.eclipse.daanse.sql.statement.api.Predicates.isNull(col)
            : org.eclipse.daanse.sql.statement.api.Predicates.comparison(
                col,
                org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                org.eclipse.daanse.sql.statement.api.Expressions.literal(value, datatype));
    }

    /**
     * ONE context column's agg-substituted value restriction WITH its aggregate-table provenance —
     * the per-column unit both {@link #aggContextColumnPredicates} and the {@code
     * contextContribution} agg mode consume (the shared implementation): the base
     * column resolved to its agg node ({@link #resolvedAggColumn}), the value restriction built on
     * it ({@link #contextValuePredicate}), paired with the agg table carrying the column. Returns
     * {@code null} (the caller's bail signal) when the agg star has no usable node for the column.
     * Package-private for the unit-test fixtures.
     */
    static AggPlan.AggColumnPredicate aggContextColumnPredicate(
        AggStar aggStar, RolapStar.Column column, Object value)
    {
        AggStar.Table.Column aggColumn = resolvedAggColumn(aggStar, column);
        if (aggColumn == null) {
            return null;
        }
        return new AggPlan.AggColumnPredicate(aggColumn.getTable(),
            contextValuePredicate(aggColumn.toSqlExpression(), value, column.getDatatype()));
    }

    /**
     * The shared context-column core of {@link #collapsedAggWhere(AggStar, RolapMember)} and
     * {@link #levelMembersAggWhere(AggStar)}: the current context (optionally after {@code
     * setContext(parent)}) as a list of agg-substituted {@code col = value} / {@code col IS NULL}
     * predicates, each carrying its aggregate-table provenance
     * ({@link AggPlan.AggColumnPredicate} — the agg-join channel's per-column unit).
     * {@code parent} is {@code null} for the level-members twin (no parent context).
     * Returns {@code null} (→ the recorder path) for every shape the twins do not reproduce — a virtual
     * cube, a calculated context/slicer dimension member, a slicer tuple / disjoint tuple /
     * multi-valued slicer, a role-access restriction, a missing agg node, or any builder-time
     * exception. A non-null (possibly empty) list carries the reproduced context conjuncts, in the
     * recorder's column order.
     * <p>
     * Protected (not private) for the candidate twin:
     * {@code RolapNativeSet.SetConstraint.levelMembersAggWhereCandidateState} composes its per-arg
     * member groups over exactly this context-conjunct list (list form, so the arg groups append in
     * the recorder's emission order).
     */
    protected List<AggPlan.AggColumnPredicate>
        aggContextColumnPredicates(AggStar aggStar, RolapMember parent)
    {
        RolapCube cube = (RolapCube) evaluator.getCube();
        if (cube instanceof RolapVirtualCube) {
            // The base cube identification (a stored context measure) is not reproduced in this twin.
            return null;
        }
        // Calculated context / slicer dimension members: the calc-member expansion applies constraints
        // this translation does not capture — same class the real path bails on. (A calc MEASURE is fine.)
        Member[] ctxMembers = evaluator.getMembers();
        for (int i = 1; i < ctxMembers.length; i++) {
            if (ctxMembers[i].isCalculated() && !ctxMembers[i].isMeasure()) {
                return null;
            }
        }
        for (Member sm : evaluator.getSlicerMembers()) {
            if (sm.isCalculated() && !sm.isMeasure()) {
                return null;
            }
        }
        try {
            // Throwaway push() copy — never mutate this constraint's (possibly cached) evaluator.
            RolapEvaluator ev = evaluator.push();
            if (parent != null) {
                ev.setContext(parent);
            }
            // Slicer tuples (disjoint / multi-level) and their per-column IN factoring are NOT reproduced
            // in this collapsed twin — bail if any are present.
            org.eclipse.daanse.olap.api.calc.tuple.TupleList slicerTuples =
                ev.getOptimizedSlicerTuples(cube);
            if (slicerTuples != null && !slicerTuples.isEmpty()) {
                return null;
            }
            TupleConstraintStruct expanded =
                ContextConstraintWriter.makeContextConstraintSet(ev, strict, false);
            if (!expanded.getDisjoinedTupleLists().isEmpty()) {
                return null;
            }
            Member[] members = expanded.getMembersArray();
            if (members.length > 0 && !(members[0] instanceof RolapStoredMeasure)) {
                RolapStoredMeasure measure = (RolapStoredMeasure) cube.getMeasures().getFirst();
                List<Member> memberList = new ArrayList<>(Arrays.asList(members));
                memberList.add(0, measure);
                members = memberList.toArray(new Member[0]);
            }
            CellRequest request = RolapAggregationManager.makeRequest(members);
            if (request == null) {
                return null;
            }
            // Role-based access is not reproduced in this twin.
            if (!ContextConstraintWriter.getRoleConstraintMembers(
                    getEvaluator().getCatalogReader(), getEvaluator().getMembers()).isEmpty()) {
                return null;
            }
            Map<org.eclipse.daanse.olap.api.sql.SqlExpression, Set<RolapMember>> slicerMap =
                SlicerAnalyzer.getSlicerMemberMap(ev);
            RolapStar.Column[] columns = request.getConstrainedColumns();
            Object[] values = request.getSingleValues();
            List<AggPlan.AggColumnPredicate> predicates = new ArrayList<>();
            for (int i = 0; i < columns.length; i++) {
                RolapStar.Column column = columns[i];
                // Multi-valued slicer columns (per-column IN factoring) are not reproduced here.
                if (slicerMap.containsKey(column.getExpression())
                        && ContextConstraintWriter.getNonAllMembers(
                            slicerMap.get(column.getExpression())).size() > 1) {
                    return null;
                }
                // The AGG node in place of the base column node — the collapsed substitution,
                // with the agg table carried as provenance for the agg-join channel.
                AggPlan.AggColumnPredicate acp = aggContextColumnPredicate(aggStar, column, values[i]);
                if (acp == null) {
                    return null;
                }
                predicates.add(acp);
            }
            return predicates;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The parenthesised parent-key group for {@link #collapsedAggWhere}, with agg-substituted
     * column nodes — the dialect-free counterpart of {@code addMemberConstraint} for a single parent
     * (an equality {@code aggCol = key} for the parent and each non-all ancestor up to the first unique
     * level, AND-combined). Returns {@code null} (a BAIL signal, distinct from an empty-but-present
     * result) when a level has no base star key column or that column has no agg node;
     * {@link java.util.Optional#empty()} when the parent is the all member (no key restriction).
     * <p>
     * Protected (not private) for the candidate twin: a {@code DescendantsCrossJoinArg}'s
     * single-member restriction is exactly this agg-substituted single-parent form (the recorder's
     * {@code MemberConstraintWriter.addMemberConstraint} for one member).
     */
    protected java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate>
        aggMemberKeyGroup(AggStar aggStar, RolapCube cube, RolapMember parent)
    {
        List<org.eclipse.daanse.sql.statement.api.expression.Predicate> equalities = new ArrayList<>();
        for (RolapMember m = parent; m != null && !m.isAll(); m = m.getParentMember()) {
            if (m.isNull()) {
                // Always-false (1 = 0) — a null member has no children.
                return java.util.Optional.of(
                    org.eclipse.daanse.sql.statement.api.Predicates.or(List.of()));
            }
            RolapLevel level = m.getLevel();
            RolapStar.Column column = (level instanceof RolapCubeLevel rcl)
                ? rcl.getBaseStarKeyColumn(cube) : null;
            if (column == null) {
                return null;
            }
            AggStar.Table.Column aggColumn = aggStar.lookupColumn(column.getBitPosition());
            if (aggColumn == null) {
                return null;
            }
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression col = aggColumn.toSqlExpression();
            if (col == null) {
                return null;
            }
            if (m.getKey() == Util.sqlNullValue) {
                equalities.add(org.eclipse.daanse.sql.statement.api.Predicates.isNull(col));
            } else {
                equalities.add(org.eclipse.daanse.sql.statement.api.Predicates.comparison(
                    col,
                    org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                    org.eclipse.daanse.sql.statement.api.Expressions.literal(m.getKey(), level.getDatatype())));
            }
            if (level.isUnique()) {
                break;
            }
        }
        return equalities.isEmpty() ? java.util.Optional.empty()
            : java.util.Optional.of(org.eclipse.daanse.sql.statement.api.Predicates.and(equalities));
    }

    /**
     * The calculated-DIMENSION-member gate of {@link #contextContribution}, applied identically to
     * the context axis and the slicer: a supported calc member yields a bail reason only in
     * {@link CalcLift#COMPOSED} mode (the {@link CalcLift#LIFTED} mode — executed for every
     * family — lets it fall
     * through into the expansion, which reproduces the recorder's calc handling); an unsupported
     * one bails in {@code COMPOSED} and, in
     * lifted mode on a NON-strict constraint, is DROPPED instead (writer
     * parity: {@code ContextConstraintWriter.makeContextConstraintSet} →
     * {@code removeCalculatedAndDefaultMembers} removes the member; a strict constraint keeps
     * bailing, the writer throws there). {@code kind} keeps the grep-stable reason prefixes
     * ({@code calc-context-member-*} / {@code calc-slicer-member-*}) distinct. Static +
     * package-visible for the unit pin.
     */
    static java.util.Optional<String> calcGateBailReason(
        Iterable<? extends Member> members, String kind, CalcLift lift, boolean strict)
    {
        for (Member m : members) {
            if (m.isCalculated() && !m.isMeasure()) {
                if (!CalculatedMemberExpander.isSupportedCalculatedMember(m)) {
                    if (lift != CalcLift.COMPOSED && !strict) {
                        // The writer drops the member (removeCalculatedAndDefaultMembers) —
                        // the lifted twin lets it fall through into the same expansion.
                        continue;
                    }
                    return java.util.Optional.of(
                        "calc-" + kind + "-member-unsupported:" + m.getUniqueName());
                }
                if (lift == CalcLift.COMPOSED) {
                    return java.util.Optional.of(
                        "calc-" + kind + "-member-supported:" + m.getUniqueName());
                }
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Attaches the agg-join-channel plan when (and only when) the read is agg-routed. A present
     * plan with an EMPTY predicate list is the VALID unconstrained translation
     * — never collapse it into an absent plan: absence means "could not
     * translate" (→ recorder), emptiness means "translated, and unconstrained".
     */
    private static ConstraintContribution withAggPlanIfRouted(ConstraintContribution c,
        AggStar aggStar, List<AggPlan.AggColumnPredicate> aggPredicates)
    {
        return aggStar == null ? c : c.withAggPlan(new AggPlan(aggStar, aggPredicates));
    }

    /**
     * Shared context translation for both SPI variants: one {@code col = value} per single-valued
     * context column (+ the parent key when {@code parent} is present), with the columns' tables to
     * join. In agg mode (a non-null {@code aggStar}) every conjunct is built on the aggregate
     * column nodes and the contribution carries the {@link AggPlan} provenance channel (the
     * agg-join channel — dormant until the agg routing). {@link java.util.Optional#empty()} for anything
     * outside the simple scope.
     */
    private ContributionResult contextContribution(
        RolapCube baseCube,
        AggStar aggStar,
        java.util.Optional<RolapMember> parent,
        CalcLift lift)
    {
        // AGG MODE: a non-null aggStar does not blanket-bail. The context columns are
        // resolved via the agg substitution (aggContextColumnPredicate — the getColumnNode rule),
        // the same predicates are carried agg-substituted in `where`/`ordered`, and the agg-table
        // provenance is attached as the AggPlan (the agg-join channel). An untranslatable agg shape
        // bails with a precise reason below ("agg-missing-column" / "agg-slicer-tuple" /
        // "agg-role-access" / "agg-parent-key"). A consumer that hard-routes `aggStar != null` to
        // the QueryRecorder BEFORE calling toContribution passes a null aggStar here.
        RolapCube cube = (baseCube != null) ? baseCube : (RolapCube) evaluator.getCube();
        if (cube instanceof RolapVirtualCube) {
            // A virtual cube owns no star of its own: every column resolution below must run
            // against a base cube (RolapCubeLevel.getBaseStarKeyColumn resolves a virtual level
            // through baseCube.findBaseCubeLevel). The level-reading callers pin a stored measure
            // of the target base cube into the context before building (one union arm per base
            // cube), so the context measure identifies the base cube this translation must use —
            // the same cube the recorded arm was handed. A non-stored context measure leaves the
            // base cube unidentifiable: bail.
            if (!(evaluator.getMembers()[0] instanceof RolapStoredMeasure storedMeasure)) {
                return bail("virtual-cube-context-measure");
            }
            cube = storedMeasure.getCube();
            if (cube == null || cube instanceof RolapVirtualCube) {
                return bail("virtual-cube-base-unresolved");
            }
        }
        // Tuple / disjoint slicers and role-based access are out of the simple scope.
        org.eclipse.daanse.olap.api.calc.tuple.TupleList slicerTuples =
            evaluator.getOptimizedSlicerTuples(cube);
        // A slicer tuple list — single OR multi-level — is reproduced below as the
        // addContextConstraintTuples Or(And(...)) predicate, which StarPredicateTranslator collapses to a
        // single-column IN or a multi-column tuple-IN (Predicates.inTuple). A shape the dialect can't
        // tuple-IN, or a genuinely divergent optimization (constant-level extraction), falls back via
        // the guard.
        // Role-based access is reproduced as a contribution after the context column loop below
        // (context columns first, then addRoleAccessConstraints — addContextConstraint's order).
        // Calculated context OR slicer members (e.g. *FILTER_MEMBER calc members from
        // optimizer-generated MDX) — the calc-member expansion applies constraints this translation
        // does not capture, producing a wrong (over/under-constrained) query. Out of scope: bail to
        // the recorder path.
        // (members[0] is the measure; a calc measure is fine. Slicer calc members live in
        // getSlicerMembers(), NOT getMembers().)
        // Measures never contribute a context COLUMN (the context loop below reads only
        // dimension members), so a calculated measure in the context or slicer is harmless —
        // same skip as SlicerAnalyzer. Calculated DIMENSION members still bail: their
        // expansion applies constraints this translation does not capture.
        //
        // The bail reason splits the calculated DIMENSION members into two permanently distinct
        // classes (grep-stable suffixes):
        //   -supported:   an Aggregate/'+'/parentheses/member-expression calc member (or a compound
        //                 slicer placeholder) that makeContextConstraintSet + getSlicerMemberMap DO
        //                 expand on the recorder path (e.g. [Time].[Date Range] -> the_year = 1997
        //                 AND quarter IN ('Q1','Q2','Q3')). The downstream machinery this method
        //                 already calls (makeContextConstraintSet at the try below; getSlicerMemberMap)
        //                 reproduces that expansion, so this class is liftable IN PRINCIPLE — but only
        //                 with a full-TCK validation, because QueryBuildContext.build has no runtime fallback:
        //                 a returned contribution is executed authoritatively. Deferred, NOT
        //                 declined-forever.
        //   -unsupported: an arbitrary calc member no expander can reduce to plain-member predicates —
        //                 a TopCount-defined member ([Top Drinks]), a writeback scenario member
        //                 ([Scenario].[1]), a bare calc member ([Time].[x], [Jan], [First Term],
        //                 [FILTER1]). Only reachable on a NON-strict constraint (strict isValidContext
        //                 rejects unsupported calc context members before the constraint is built).
        //                 Principally not expressible as a pure predicate contribution: permanent rest.
        // isSupportedCalculatedMember is the same check isValidContext (context) and
        // expandSupportedCalculatedMembers (slicer) already run on these members, so it is safe here.
        // The CalcLift mode decides which class bails (see the enum + calcGateBailReason): the
        // executed LIFTED mode drops the -supported: bail and, on a
        // non-strict constraint, the -unsupported: bail too (writer parity); COMPOSED keeps both.
        Member[] ctxMembers = evaluator.getMembers();
        java.util.Optional<String> calcBail =
            calcGateBailReason(
                Arrays.asList(ctxMembers).subList(1, ctxMembers.length), "context", lift, strict)
            .or(() -> calcGateBailReason(
                evaluator.getSlicerMembers(), "slicer", lift, strict));
        if (calcBail.isPresent()) {
            return bail(calcBail.get());
        }

        // The one exception the shared catch below must NOT swallow into a bail: the strict
        // null-request internal error (identity-tracked, see the request == null branch).
        RuntimeException strictNullRequestThrow = null;
        try {
            // Throwaway push() copy: never mutate this constraint's (possibly cached, reused) evaluator —
            // a mutation would corrupt member/level enumeration for subsequent queries. Copy discarded;
            // no savepoint/restore.
            RolapEvaluator ev = evaluator.push();
            parent.ifPresent(ev::setContext);
            // Slicer routing — mirror ContextConstraintWriter.addContextConstraint: use the
            // tuple-IN Or(And(...)) predicate ONLY when the tuple list is disjoint OR multi-level. A plain
            // non-disjoint single-level slicer ({1997}×{Q1,Q2}) is FACTORED per column below
            // (the_year = 1997 AND quarter IN ('Q1','Q2')) — two separate conjuncts, not one tuple-IN.
            // Computed BEFORE the expansion because the recorder threads it as
            // makeContextConstraintSet's isTuple argument (writer parity — a compound-slicer
            // placeholder is DROPPED when the tuple predicate covers it, not replaced by the
            // first slicer member; output-equal on the certified single-list corpus, whose
            // placeholder column is always covered by the tuple bitkey).
            boolean useSlicerTuplePred = slicerTuples != null && !slicerTuples.isEmpty()
                && (SlicerAnalyzer.isDisjointTuple(slicerTuples) || evaluator.isMultiLevelSlicerTuple());
            TupleConstraintStruct expanded = ContextConstraintWriter.makeContextConstraintSet(
                ev, strict, useSlicerTuplePred);
            // Disjoined slicer tuple lists — the calc-set expansion product (e.g. TWO
            // *SLICER_MEMBER Aggregate calc slicer members, each materializing a disjoint
            // TupleList). They are reproduced
            // below as one addContextConstraintTuples replay per list
            // (slicerTupleListsContribution).
            List<org.eclipse.daanse.olap.api.calc.tuple.TupleList> disjoinedTupleLists =
                expanded.getDisjoinedTupleLists();
            Member[] members = expanded.getMembersArray();
            if (members.length > 0 && !(members[0] instanceof RolapStoredMeasure)) {
                RolapStoredMeasure measure = (RolapStoredMeasure) cube.getMeasures().getFirst();
                List<Member> memberList = new ArrayList<>(Arrays.asList(members));
                memberList.add(0, measure);
                members = memberList.toArray(new Member[0]);
            }
            CellRequest request = RolapAggregationManager.makeRequest(members);
            if (request == null) {
                // Mirrors the recorder (ContextConstraintWriter.addContextConstraint): a null cell
                // request on a NON-strict
                // constraint means "no cell can satisfy this context" — the recorder adds NO context
                // constraint at all and proceeds unrestricted. Same here: an EMPTY (present,
                // unrestricted) contribution carrying the existence-join decision.
                // The strict form THROWS — the exact exception the recorder writer raises later in
                // the same call path. A defensive assert: never
                // live (strict isValidContext rejects unsupported calc members before the constraint
                // is built).
                if (strict) {
                    strictNullRequestThrow = Util.newInternal("CellRequest is null - why?");
                    throw strictNullRequestThrow;
                }
                // Under an agg routing this unrestricted contribution still carries a PRESENT,
                // empty-predicates AggPlan — the valid unconstrained translation, never collapsed
                // into "absent".
                return ContributionResult.of(withAggPlanIfRouted(
                    ConstraintContribution.EMPTY.withFactJoinRequired(isJoinRequired()),
                    aggStar, List.of()));
            }
            RolapStar.Column[] columns = request.getConstrainedColumns();
            Object[] values = request.getSingleValues();
            // LAZY slicer expansion (writer parity — ContextConstraintWriter's
            // mapOfSlicerMembers is computed only inside the constrained-columns loop): the
            // expansion EVALUATES supported calc slicer members through the per-query
            // set-evaluator caches, so it must fire ONLY in the states the recorder fires it —
            // the first processed column. With zero constrained columns (or all covered by the
            // slicer-tuple bitkey) nothing expands: an unconditional pre-loop call would poison
            // the set-evaluator caches. The executed reads expand exactly where the recorder's
            // writer twin does.
            Map<org.eclipse.daanse.olap.api.sql.SqlExpression, Set<RolapMember>> slicerMap = null;
            if ((useSlicerTuplePred || !disjoinedTupleLists.isEmpty()) && aggStar != null) {
                // Slicer-tuple-IN under an agg routing stays on the recorder (deliberately not
                // agg-substituted). Covers the disjoined-lists lift too: the recorder's tuple
                // branch renders it there.
                throw dead("agg-slicer-tuple");
            }
            if (!disjoinedTupleLists.isEmpty()) {
                // The recorder's `!slicerTupleList.isEmpty()` tuple branch — the expansion's
                // disjoined lists first, the optimized slicer tuples appended LAST when the
                // tuple routing is active (addContextConstraint's exact composition), then one
                // addContextConstraintTuples replay per list. The factored per-column loop below
                // is NOT taken on that recorder branch.
                List<org.eclipse.daanse.olap.api.calc.tuple.TupleList> slicerTupleLists =
                    new ArrayList<>(disjoinedTupleLists);
                if (useSlicerTuplePred) {
                    slicerTupleLists.add(slicerTuples);
                }
                return slicerTupleListsContribution(cube, slicerTupleLists, request, parent);
            }
            org.eclipse.daanse.rolap.common.star.StarPredicate slicerTuplePred =
                useSlicerTuplePred ? ContextConstraintWriter.getSlicerTuplesPredicatePure(slicerTuples, cube) : null;
            org.eclipse.daanse.olap.key.BitKey slicerBitKey =
                (slicerTuplePred != null) ? slicerTuplePred.getConstrainedColumnBitKey() : null;

            List<org.eclipse.daanse.sql.statement.api.expression.Predicate> predicates = new ArrayList<>();
            List<RolapStar.Table> joinTables = new ArrayList<>();
            List<ConstraintContribution.ColumnPredicate> ordered = new ArrayList<>();
            // The agg-join channel: the SAME conjuncts with their aggregate-table
            // provenance, collected in the same order as `ordered` and attached as the AggPlan below.
            // Only populated in agg mode (aggStar != null).
            List<AggPlan.AggColumnPredicate> aggPredicates = new ArrayList<>();
            for (int i = 0; i < columns.length; i++) {
                RolapStar.Column column = columns[i];
                if (slicerBitKey != null && slicerBitKey.get(column.getBitPosition())) {
                    // constrained by the slicer tuple predicate (added after this loop) — skip here.
                    continue;
                }
                if (slicerMap == null) {
                    // First processed column — the writer's exact expansion point (lazy).
                    slicerMap = SlicerAnalyzer.getSlicerMemberMap(ev);
                }
                if (slicerMap.containsKey(column.getExpression())) {
                    java.util.List<RolapMember> colSlicerMembers =
                        ContextConstraintWriter.getNonAllMembers(slicerMap.get(column.getExpression()));
                    if (colSlicerMembers.size() > 1) {
                        // Multi-valued slicer column => the per-column factored IN, via the pure twin
                        // (quarter IN ('Q1','Q2')). Only reached when the tuple-IN path is NOT used (gated
                        // above); each part is added as its own WHERE conjunct. A shape the pure twin
                        // can't express (computed/null key) bails to the recorder path.
                        // AGG MODE (e.g. week_of_year IN (1..52)): the
                        // same factored IN built on the AGG column nodes — the pure twin is agg-aware
                        // via its threaded aggStar (dialectFreeColumnNode).
                        AggStar.Table.Column slicerAggColumn = null;
                        if (aggStar != null) {
                            slicerAggColumn = resolvedAggColumn(aggStar, column);
                            if (slicerAggColumn == null) {
                                throw dead("agg-missing-column");
                            }
                        }
                        java.util.Optional<java.util.List<
                            org.eclipse.daanse.sql.statement.api.expression.Predicate>> inParts =
                            MemberConstraintWriter.generateSingleValueInPredicatePure(
                                cube, aggStar, colSlicerMembers,
                                (RolapLevel) colSlicerMembers.get(0).getLevel(),
                                false, false, false);
                        if (inParts.isEmpty()) {
                            throw dead("multi-value-slicer-pure:" + column.getExpression());
                        }
                        for (org.eclipse.daanse.sql.statement.api.expression.Predicate p : inParts.get()) {
                            predicates.add(p);
                            joinTables.add(column.getTable());
                            ordered.add(new ConstraintContribution.ColumnPredicate(column.getTable(), p));
                            if (slicerAggColumn != null) {
                                aggPredicates.add(
                                    new AggPlan.AggColumnPredicate(slicerAggColumn.getTable(), p));
                            }
                        }
                        continue;
                    }
                }
                Object value = values[i];
                org.eclipse.daanse.sql.statement.api.expression.Predicate p;
                if (aggStar != null) {
                    // AGG MODE: the agg node in place of the base column node (the shared
                    // aggContextColumnPredicate rule) — provenance collected for the AggPlan.
                    AggPlan.AggColumnPredicate acp = aggContextColumnPredicate(aggStar, column, value);
                    if (acp == null) {
                        throw dead("agg-missing-column");
                    }
                    aggPredicates.add(acp);
                    p = acp.predicate();
                } else {
                    p = contextValuePredicate(JoinPlanner.expressionFor(column), value,
                        column.getDatatype());
                }
                predicates.add(p);
                joinTables.add(column.getTable());
                ordered.add(new ConstraintContribution.ColumnPredicate(column.getTable(), p));
            }
            // Slicer tuple list (after the non-slicer context columns, matching addContextConstraintTuples):
            // one dialect-free Or(And(...)) predicate via StarPredicateTranslator, with the slicer columns'
            // tables joined. The whole predicate attaches to its first column's table for the mapper's
            // [join, where] interleaving (single-dimension slicers — the common case — are one table).
            if (slicerTuplePred != null) {
                org.eclipse.daanse.sql.statement.api.expression.Predicate slicerWhere =
                    org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toPredicate(slicerTuplePred);
                java.util.LinkedHashSet<RolapStar.Table> slicerTables =
                    slicerTupleJoinTables(slicerTuples, cube);
                if (slicerTables.isEmpty()) {
                    throw dead("slicer-tuple-no-columns");
                }
                predicates.add(slicerWhere);
                joinTables.addAll(slicerTables);
                ordered.add(new ConstraintContribution.ColumnPredicate(
                    slicerTables.iterator().next(), slicerWhere));
            }
            // Role-based access (addRoleAccessConstraints, applied after the context columns).
            java.util.Optional<String> roleBail =
                appendRoleAccessParts(cube, aggStar, predicates, joinTables, ordered, aggPredicates);
            if (roleBail.isPresent()) {
                throw dead(roleBail.get());
            }

            // children-of-parent restriction (a nested AND so it renders as one parenthesised group).
            // AGG MODE: the parenthesised parent-key group built on the AGG column nodes — the
            // existing aggMemberKeyGroup twin of JoinPlanner.memberKeyConstraint (its null return is
            // a BAIL signal, distinct from the empty all-member group).
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> parentPredicate;
            if (aggStar != null) {
                parentPredicate = parent.isPresent()
                    ? aggMemberKeyGroup(aggStar, cube, parent.get())
                    : java.util.Optional.empty();
                if (parentPredicate == null) {
                    throw dead("agg-parent-key");
                }
            } else {
                parentPredicate = parent.flatMap(p -> JoinPlanner.memberKeyConstraint(p));
            }
            parentPredicate.ifPresent(predicates::add);

            if (predicates.isEmpty()) {
                if (columns.length == 0 && parent.map(Member::isAll).orElse(true)) {
                    // Genuinely unconstrained context (all-[All] members, or the only non-all member is a
                    // hierarchy default member the non-strict expansion drops → no constrained columns) —
                    // return an EMPTY (present, unrestricted) contribution rather than bailing, so a wrapping
                    // SetConstraint/native constraint still contributes its own args. Otherwise its
                    // base.isEmpty() check makes the whole set fall back, dropping the set's WHERE (e.g. a
                    // {[Time].[Q1],[Time].[Q2]} × level crossjoin). A pure level read with this shape is
                    // reproduced unrestricted by the mapper anyway. An all-member parent is the same shape
                    // for the member-children form: it adds no key restriction (children of [All] are just
                    // the level's members — memberKeyConstraint is empty), so the context alone decides.
                    // Carry isJoinRequired() (a non-empty set, e.g. a crossjoin, or a non-all context member
                    // whose column set is empty, still needs the target level's existence join to the fact) —
                    // the shared EMPTY constant is false, so build a fresh contribution for it.
                    // AGG MODE: the empty-predicates case STILL carries a PRESENT AggPlan — the valid
                    // unconstrained translation, never collapsed into "absent".
                    return ContributionResult.of(withAggPlanIfRouted(
                        ConstraintContribution.EMPTY.withFactJoinRequired(isJoinRequired()),
                        aggStar, aggPredicates));
                }
                // A non-empty constrained-column set that yielded no predicate = "could not express it", NOT
                // "no restriction"; bail so the real context is not dropped.
                throw dead("empty-predicates");
            }
            // ALWAYS the And wrap, even for one conjunct: the mapper splits the contribution's
            // top-level And into separate WHERE conjuncts, so a single GROUPED conjunct (the
            // parenthesised parent-key/member-set And) must sit one level below the split or it
            // loses its parentheses. A plain single comparison is unaffected (split unwraps it).
            org.eclipse.daanse.sql.statement.api.expression.Predicate where =
                org.eclipse.daanse.sql.statement.api.Predicates.and(predicates);
            // Per-column ordering lets the mapper interleave [join, that table's where(s)] exactly like
            // the recorded path. The parent-key restriction (member-children) is redundant in the
            // fact-join case: it is added both via addContextConstraint (after setContext(parent), so
            // the parent's level key is already a constrained context column) and via
            // addMemberConstraint, and the WHERE dedup collapses the second add. So the context columns
            // in `ordered` already carry the parent key; the member mapper appends only the child level
            // -> fact join (addLevelConstraint) after them. Keep `ordered` even when a parent is present.
            List<ConstraintContribution.ColumnPredicate> orderedToUse = ordered;
            // The parent-key group (addMemberConstraint) is rendered parenthesized, so it is NOT a
            // duplicate of the unparenthesised context columns and survives as a trailing WHERE conjunct.
            // The member mapper appends it after the interleaved context (tuple path: empty parent).
            return ContributionResult.of(withAggPlanIfRouted(
                new ConstraintContribution(java.util.Optional.of(where), joinTables, orderedToUse,
                    parentPredicate).withFactJoinRequired(isJoinRequired()),
                aggStar, aggPredicates));
        } catch (RuntimeException e) {
            if (e == strictNullRequestThrow) {
                // The strict null-request internal error propagates (the recorder writer throws the
                // SAME exception later in the same call path — never a silent bail).
                throw e;
            }
            return bail("exception:" + e.getClass().getSimpleName());
        }
    }

    /**
     * The slicer-tuple lift: the
     * contribution form of the recorder's tuple branch — {@code
     * ContextConstraintWriter.addContextConstraint}'s {@code !slicerTupleList.isEmpty()} path, one
     * {@code addContextConstraintTuples} replay per tuple list, in list order. Per list, exactly
     * the recorder's emission sequence:
     * <ol>
     * <li>the context columns NOT constrained by THIS list's tuple predicate as simple
     *     {@code col = value} conjuncts ({@code addSimpleColumnConstraint} — NO slicer-map
     *     factoring on this branch), re-emitted PER LIST like the recorder (its
     *     {@code WherePredicate} ops are not deduped);</li>
     * <li>the list's {@code Or(And(MemberColumnPredicate))} predicate
     *     ({@code getSlicerTuplesPredicatePure} → {@code StarPredicateTranslator}), its columns'
     *     tables joined in the predicate-build iteration order and the whole predicate attached
     *     to its first table for the mapper's [join, where] interleaving;</li>
     * <li>the role-access parts ({@code addRoleAccessConstraints} runs once per
     *     {@code addContextConstraintTuples} call).</li>
     * </ol>
     * Non-agg only (the caller bails {@code agg-slicer-tuple} first). The tail mirrors
     * {@code contextContribution}'s non-agg tail: parent-key group appended, ALWAYS the And wrap,
     * {@code factJoinRequired} carried.
     */
    private ContributionResult slicerTupleListsContribution(
        RolapCube cube,
        List<org.eclipse.daanse.olap.api.calc.tuple.TupleList> slicerTupleLists,
        CellRequest request,
        java.util.Optional<RolapMember> parent)
    {
        List<org.eclipse.daanse.sql.statement.api.expression.Predicate> predicates = new ArrayList<>();
        List<RolapStar.Table> joinTables = new ArrayList<>();
        List<ConstraintContribution.ColumnPredicate> ordered = new ArrayList<>();
        RolapStar.Column[] columns = request.getConstrainedColumns();
        Object[] values = request.getSingleValues();
        for (org.eclipse.daanse.olap.api.calc.tuple.TupleList tupleList : slicerTupleLists) {
            org.eclipse.daanse.rolap.common.star.StarPredicate listPred =
                ContextConstraintWriter.getSlicerTuplesPredicatePure(tupleList, cube);
            org.eclipse.daanse.olap.key.BitKey listBitKey = listPred.getConstrainedColumnBitKey();
            for (int i = 0; i < columns.length; i++) {
                RolapStar.Column column = columns[i];
                if (listBitKey.get(column.getBitPosition())) {
                    // constrained by this list's tuple predicate (added below) — skip here.
                    continue;
                }
                org.eclipse.daanse.sql.statement.api.expression.Predicate p = contextValuePredicate(
                    JoinPlanner.expressionFor(column), values[i], column.getDatatype());
                predicates.add(p);
                joinTables.add(column.getTable());
                ordered.add(new ConstraintContribution.ColumnPredicate(column.getTable(), p));
            }
            java.util.LinkedHashSet<RolapStar.Table> listTables =
                slicerTupleJoinTables(tupleList, cube);
            if (listTables.isEmpty()) {
                throw dead("slicer-tuple-no-columns");
            }
            org.eclipse.daanse.sql.statement.api.expression.Predicate listWhere =
                org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toPredicate(listPred);
            predicates.add(listWhere);
            joinTables.addAll(listTables);
            ordered.add(new ConstraintContribution.ColumnPredicate(
                listTables.iterator().next(), listWhere));
            java.util.Optional<String> roleBail =
                appendRoleAccessParts(cube, null, predicates, joinTables, ordered, null);
            if (roleBail.isPresent()) {
                throw dead(roleBail.get());
            }
        }
        // Tail parity with contextContribution (non-agg): the parent-key group, the And wrap
        // (predicates are never empty here — each list contributed its tuple predicate).
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> parentPredicate =
            parent.flatMap(p -> JoinPlanner.memberKeyConstraint(p));
        parentPredicate.ifPresent(predicates::add);
        org.eclipse.daanse.sql.statement.api.expression.Predicate where =
            org.eclipse.daanse.sql.statement.api.Predicates.and(predicates);
        return ContributionResult.of(
            new ConstraintContribution(java.util.Optional.of(where), joinTables, ordered,
                parentPredicate).withFactJoinRequired(isJoinRequired()));
    }

    /**
     * JOIN registration order for a slicer tuple predicate = the predicate-build iteration (per
     * tuple, per member, level walk) — the order the writer's {@code getLevelColumn} registers
     * each level column's table via {@code addToFrom}. The predicate's own constrained-column
     * list is differently ordered, which renders the joins in the wrong sequence for
     * multi-dimension tuples.
     */
    private static java.util.LinkedHashSet<RolapStar.Table> slicerTupleJoinTables(
        org.eclipse.daanse.olap.api.calc.tuple.TupleList slicerTuples, RolapCube cube)
    {
        java.util.LinkedHashSet<RolapStar.Table> slicerTables = new java.util.LinkedHashSet<>();
        for (List<Member> tuple : slicerTuples) {
            for (Member tupleMember : tuple) {
                for (RolapMember m = (RolapMember) tupleMember; m != null; m = m.getParentMember()) {
                    if (m.isAll()) {
                        continue;
                    }
                    RolapStar.Column mc = (m.getLevel() instanceof RolapCubeLevel mcl)
                        ? mcl.getBaseStarKeyColumn(cube) : null;
                    if (mc != null) {
                        slicerTables.add(mc.getTable());
                    }
                    if (((RolapLevel) m.getLevel()).isUnique()) {
                        break;
                    }
                }
            }
        }
        return slicerTables;
    }

    /**
     * Role-based access ({@code addRoleAccessConstraints}, applied after the context columns): per
     * (level, accessible members) an IN predicate (per level, parent levels included) plus the
     * level's table joined to the fact. Reproduces each part as a WHERE conjunct + an ordered
     * (table, predicate) pair so the mapper joins the level table once and emits the parts in
     * order. The role-IN agg
     * substitution runs in the EXECUTED agg mode — the role key resolved to its agg node via the
     * SAME {@code resolvedAggColumn} rule the context columns use
     * ({@code ContextConstraintWriter.getColumnNode} parity); an UNRESOLVABLE role key keeps the
     * grep-stable {@code agg-role-access} reason. Returns a present BAIL REASON when a role shape
     * is not expressible; empty = all parts appended.
     */
    private java.util.Optional<String> appendRoleAccessParts(
        RolapCube cube,
        AggStar aggStar,
        List<org.eclipse.daanse.sql.statement.api.expression.Predicate> predicates,
        List<RolapStar.Table> joinTables,
        List<ConstraintContribution.ColumnPredicate> ordered,
        List<AggPlan.AggColumnPredicate> aggPredicates)
    {
        java.util.Map<Level, List<RolapMember>> roleMembers =
            ContextConstraintWriter.getRoleConstraintMembers(
                getEvaluator().getCatalogReader(), getEvaluator().getMembers());
        for (java.util.Map.Entry<Level, List<RolapMember>> entry : roleMembers.entrySet()) {
            if (!(entry.getKey() instanceof RolapCubeLevel roleLevel)) {
                return java.util.Optional.of("role-level-not-cube-level");
            }
            RolapStar.Column roleKey = roleLevel.getBaseStarKeyColumn(cube);
            AggStar.Table.Column roleAggColumn = null;
            if (aggStar != null) {
                roleAggColumn = (roleKey == null) ? null : resolvedAggColumn(aggStar, roleKey);
                if (roleAggColumn == null) {
                    return java.util.Optional.of("agg-role-access");
                }
            }
            java.util.Optional<List<org.eclipse.daanse.sql.statement.api.expression.Predicate>> roleParts =
                MemberConstraintWriter.generateSingleValueInPredicatePure(
                    cube, aggStar, entry.getValue(), roleLevel, strict, false, true);
            if (roleParts.isEmpty() || roleKey == null) {
                return java.util.Optional.of(
                    "role-constraint-unresolved:" + roleLevel.getUniqueName());
            }
            RolapStar.Table roleTable = roleKey.getTable();
            for (org.eclipse.daanse.sql.statement.api.expression.Predicate part : roleParts.get()) {
                predicates.add(part);
                joinTables.add(roleTable);
                ordered.add(new ConstraintContribution.ColumnPredicate(roleTable, part));
                if (roleAggColumn != null) {
                    // Provenance for the agg-join channel — the agg table carrying the
                    // substituted role key column (mirrors the multi-valued-slicer IN).
                    aggPredicates.add(
                        new AggPlan.AggColumnPredicate(roleAggColumn.getTable(), part));
                }
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Returns whether a join with the fact table is required. A join is
     * required if the context contains members from dimensions other than
     * level. If we are interested in the members of a level or a members
     * children then it does not make sense to join only one dimension (the one
     * that contains the requested members) with the fact table for NON EMPTY
     * optimization.
     */
    public boolean isJoinRequired() {
        Member[] members = evaluator.getMembers();
        // members[0] is the Measure, so loop starts at 1
        for (int i = 1; i < members.length; i++) {
            if (!members[i].isAll()
                || members[i] instanceof LimitedRollupMember
                || members[i] instanceof MultiCardinalityDefaultMember)
            {
                return true;
            }
        }
        return false;
    }

    @Override
	public MemberChildrenConstraint getMemberChildrenConstraint(
        RolapMember parent)
    {
        return this;
    }

    @Override
	public Object getCacheKey() {
        return cacheKey;
    }

    @Override
	public RolapEvaluator getEvaluator() {
        return evaluator;
    }

    @Override
    public boolean supportsAggTables() {
        return true;
    }
}

// End SqlContextConstraint.java

