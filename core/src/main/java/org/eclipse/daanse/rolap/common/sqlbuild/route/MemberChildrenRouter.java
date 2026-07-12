/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.sqlbuild.route;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.constraint.ChildByNameConstraint;
import org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint;
import org.eclipse.daanse.rolap.common.member.SqlMemberSource;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution;
import org.eclipse.daanse.rolap.common.sql.ContributionResult;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sqlbuild.MemberSqlMapper;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;

/**
 * Routes a member-children read (children of one parent member) to its authoritative
 * {@link MemberSqlMapper} build — the routing ladder formerly inlined in
 * {@code SqlMemberSource.makeChildMemberSql}, moved verbatim so the decision is a testable value:
 * the generic mapper is authoritative when it supports the level and the constraint translates to
 * a contribution — dimension-only (no fact join) via the plain child SELECT, otherwise via the
 * star-join variant. There is NO recorder tail on this path: a shape outside the mapper's scope
 * (an untranslatable aggregate-table contribution, a level the mapper does not support, a
 * constraint whose restriction is not expressible as a contribution) routes to {@link Route.Throw}
 * — a defensive guard for a member-children read that cannot be modelled — each decline logged as
 * {@code path=throw} with its reason.
 */
public final class MemberChildrenRouter {

    private MemberChildrenRouter() {
    }

    /** The routing decision: an authoritative build, or the defensive throw with its reason. */
    public sealed interface Route {
        record Builder(Supplier<SelectStatement> statement) implements Route {
        }

        record Throw(String reason) implements Route {
        }
    }

    public static Route route(
        RolapHierarchy hierarchy,
        RolapMember member,
        AggStar aggStar,
        MemberChildrenConstraint constraint)
    {
        RolapLevel level = (RolapLevel) member.getLevel().getChildLevel();
        if (aggStar != null) {
            // An aggregate-table children read OUTSIDE the collapsed single-column shape (that shape
            // keeps its dedicated shortcut inside the branch below) builds through
            // MemberSqlMapper.aggChildMemberSql: the agg-mode contribution's orderedAggPredicates +
            // memberKeyGroup cover every conjunct (passing where() too would double-emit them). An
            // untranslatable contribution (empty, or no AggPlan) keeps the recorder.
            if (!(SqlMemberSource.isLevelCollapsed(aggStar, (RolapCubeLevel) level)
                    && !SqlMemberSource.levelContainsMultipleColumns(level))) {
                ContributionResult contribution =
                    constraint.toContribution(null, aggStar, member);
                if (contribution.isSupported() && contribution.contribution().aggPlan().isPresent()) {
                    final ConstraintContribution c = contribution.contribution();
                    final org.eclipse.daanse.rolap.common.sql.AggPlan plan = c.aggPlan().get();
                    boolean ngb = needsGroupBy(hierarchy, level);
                    RolapUtil.SQL_GEN_LOGGER.debug(
                        "member-children level={}: {} -> builder authoritative",
                        level.getUniqueName(),
                        SqlMemberSource.isLevelCollapsed(aggStar, (RolapCubeLevel) level)
                            ? "agg-mc-dimjoin" : "agg-mc-factjoin");
                    return new Route.Builder(
                        () -> MemberSqlMapper.aggChildMemberSql(level, aggStar, ngb,
                            java.util.Optional.empty(), plan.orderedAggPredicates(),
                            c.memberKeyGroup()));
                }
                RolapUtil.SQL_GEN_LOGGER.debug(
                    "member children {}: path=throw reason=aggregate-table contribution untranslatable",
                    level.getUniqueName());
            } else if (constraint instanceof SqlContextConstraint scc) {
                // The collapsed single-column aggStar member-children read: when the agg-substituted
                // WHERE (context + parent-key group) is captured, it builds through
                // MemberSqlMapper.collapsedSingleColumnSql (same needsGroupBy). An empty
                // (unconstrained) tri-state falls through to the recorder tail below.
                SqlContextConstraint.AggWhereResult aggWhereState =
                    scc.collapsedAggWhereState(aggStar, member);
                java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> aggWhere =
                    aggWhereState.where();
                if (aggWhere.isPresent()) {
                    boolean ngb = needsGroupBy(hierarchy, level);
                    RolapUtil.SQL_GEN_LOGGER.debug(
                        "member-children level={}: agg-mc-collapsed-single -> builder authoritative",
                        level.getUniqueName());
                    return new Route.Builder(
                        () -> MemberSqlMapper.collapsedSingleColumnSql(level, aggStar, ngb, aggWhere));
                }
            }
        } else if (!MemberSqlMapper.supports(level)) {
            // A COMPUTED-expression level (plain-column supports() rejects it, but the relaxed
            // supportsComputed() accepts a renderable, non-parent-child relation): build the whole
            // contribution-expressible enumeration on the builder. The child SELECT carries the
            // computed caption/ordinal as a per-dialect RawVariant (JoinPlanner.expressionFor). Both
            // a plain DefaultMemberChildrenConstraint and a ChildByNameConstraint by-name lookup are
            // routed here (splitConjuncts below). A VIEW/INLINE-relation level (supportsComputed
            // rejects it) stays on the recorder.
            if (MemberSqlMapper.supportsComputed(level)) {
                ContributionResult contribution =
                    constraint.toContribution(null, null, member);
                if (contribution.isSupported()) {
                    boolean ngb = needsGroupBy(hierarchy, level);
                    final ConstraintContribution c = contribution.contribution();
                    if (!c.requiresFactJoin()) {
                        RolapUtil.SQL_GEN_LOGGER.debug(
                            "member-children level={}: computed dimension-only constraint -> builder authoritative",
                            level.getUniqueName());
                        // A by-name lookup (ChildByNameConstraint) contributes the parent-key group AND the
                        // UPPER(name)=... filter as one And; the recorder emits them as two separate WHERE
                        // conjuncts, so split the top-level And. A plain enumeration keeps its single
                        // grouped conjunct (splitConjuncts=false).
                        final boolean splitConjuncts = constraint instanceof ChildByNameConstraint;
                        return new Route.Builder(
                            () -> MemberSqlMapper.childMemberSql(level, ngb, c.where(), splitConjuncts));
                    }
                    // A FACT-JOIN contribution on a computed level builds through the SAME star-join
                    // childMemberSql used for a mapper-supported level: non-empty orderedPredicates
                    // take the fact-rooted ordered form, empty ones the dimension-rooted ANSI
                    // delegate. The computed key/caption/ordinal/property projections travel through
                    // the shared projectLevel RawVariant channel.
                    RolapUtil.SQL_GEN_LOGGER.debug(
                        "member-children level={}: computed fact-join constraint -> builder authoritative",
                        level.getUniqueName());
                    return new Route.Builder(
                        () -> MemberSqlMapper.childMemberSql(level, ngb, c.where(), c.joinTables(),
                            c.orderedPredicates(), c.memberKeyGroup()));
                }
            } else if (MemberSqlMapper.supportsExoticSingleRelation(level)
                    || MemberSqlMapper.supportsInlineJoinRelation(level)) {
                // A SINGLE view/inline-relation level (plain supports() AND relaxed supportsComputed() both
                // reject it — RelationFromMapper.supports declines a view/inline — but its WHOLE relation IS
                // the view/inline): build the dimension-only enumeration on the builder. The plain child
                // SELECT's exotic FROM (FromVariant/FromInline) renders the view/inline (e.g. Warehouse ID,
                // Store Type, [Shared] Alternative Promotion). A JOIN of plain tables that CONTAINS an
                // inline-table leaf joins this route too (supportsInlineJoinRelation — [Store].[Store
                // Country] = store JOIN inline-nation, InlineTableTest#testInlineTableSnowflake): the
                // per-level subset FROM reaches the inline leaf (FromJoin with a FromInline operand,
                // rendered like any derived table). A join containing a VIEW leaf stays on the recorder.
                ContributionResult contribution =
                    constraint.toContribution(null, null, member);
                if (contribution.isSupported() && !contribution.contribution().requiresFactJoin()) {
                    RolapUtil.SQL_GEN_LOGGER.debug(
                        "member-children level={}: exotic single-relation dimension-only constraint -> builder authoritative",
                        level.getUniqueName());
                    boolean ngb = needsGroupBy(hierarchy, level);
                    ContributionResult c = contribution;
                    final boolean splitConjuncts = constraint instanceof ChildByNameConstraint;
                    return new Route.Builder(
                        () -> MemberSqlMapper.childMemberSql(level, ngb, c.contribution().where(), splitConjuncts));
                }
            }
            // Out of (relaxed) mapper scope: a level whose relation the mapper cannot render
            // (a JOIN containing a VIEW leaf — a genuine multi-relation view shape outside
            // supportsExoticSingleRelation/supportsInlineJoinRelation), a parent-child level, or
            // an inexpressible constraint (empty contribution).
            RolapUtil.SQL_GEN_LOGGER.debug(
                "member children {}: path=throw reason=level unsupported by mapper",
                level.getUniqueName());
        } else {
            // Compute the contribution once; it steers every branch below.
            ContributionResult contribution =
                constraint.toContribution(null, null, member);
            boolean ngb = needsGroupBy(hierarchy, level);
            if (contribution.isSupported() && !contribution.contribution().requiresFactJoin()) {
                // A dimension-only restriction (no fact join) is fully reproduced by the mapper's
                // plain child SELECT.
                RolapUtil.SQL_GEN_LOGGER.debug(
                        "member-children level={}: dimension-only constraint -> builder authoritative",
                        level.getUniqueName());
                java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where =
                    contribution.contribution().where();
                return new Route.Builder(
                    () -> MemberSqlMapper.childMemberSql(level, ngb, where));
            }
            // Routing decision: star-join build or recorder — attributable per constraint class.
            RolapUtil.SQL_GEN_LOGGER.debug(
                "member children {} constraint={} present={} factJoin={}",
                level.getUniqueName(), constraint.getClass().getSimpleName(), contribution.isSupported(),
                contribution.isSupported() && contribution.contribution().requiresFactJoin());
            if (contribution.isSupported()) {
                // A context/NON-EMPTY restriction with a fact join — the star-join variant,
                // authoritative.
                final ConstraintContribution c = contribution.contribution();
                return new Route.Builder(
                    () -> MemberSqlMapper.childMemberSql(level, ngb, c.where(), c.joinTables(),
                        c.orderedPredicates(), c.memberKeyGroup()));
            }
            // The constraint's restriction is not expressible as a contribution — the recorder
            // query carries it (building the plain parent-key SELECT would drop the context
            // restriction).
            RolapUtil.SQL_GEN_LOGGER.debug(
                "member children {}: path=throw reason=constraint {} not expressible as a contribution",
                level.getUniqueName(), constraint.getClass().getSimpleName());
        }

        // Every reachable member-children read builds above (the collapsed-single-column agg lift,
        // plus the dimension-only / star-join / computed / exotic-single-relation builder routes) or
        // falls to a documented decline.
        return new Route.Throw(
            "member children read not modellable: level=" + level.getUniqueName()
                + " constraint=" + constraint.getClass().getSimpleName()
                + " aggStar=" + (aggStar != null));
    }

    @SuppressWarnings("unchecked")
    private static boolean needsGroupBy(RolapHierarchy hierarchy, RolapLevel level) {
        return RolapUtil.isGroupByNeeded(hierarchy, (List<RolapLevel>) hierarchy.getLevels(), level.getDepth());
    }
}
