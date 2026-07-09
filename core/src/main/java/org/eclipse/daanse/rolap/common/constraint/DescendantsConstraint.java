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

import java.util.List;

import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;

/**
 * TupleConstaint which restricts the result of a tuple sqlQuery to a
 * set of parents.  All parents must belong to the same level.
 *
 * @author av
 * @since Nov 10, 2005
 */
public class DescendantsConstraint implements TupleConstraint {
    List<RolapMember> parentMembers;
    MemberChildrenConstraint mcc;

    /**
     * Creates a DescendantsConstraint.
     *
     * @param parentMembers list of parents all from the same level
     * @param mcc the constraint that would return the children for each single
     * parent
     */
    public DescendantsConstraint(
        List<RolapMember> parentMembers,
        MemberChildrenConstraint mcc)
    {
        this.parentMembers = parentMembers;
        this.mcc = mcc;
    }

    @Override
	public MemberChildrenConstraint getMemberChildrenConstraint(
        RolapMember parent)
    {
        return mcc;
    }

    /**
     * {@inheritDoc}
     *
     * This implementation returns null, because descendants is not cached.
     */
    @Override
	public Object getCacheKey() {
        return null;
    }

    @Override
	public Evaluator getEvaluator() {
        return null;
    }

    @Override
    public boolean supportsAggTables() {
        return true;
    }

    /**
     * Delegates to the wrapped {@link MemberChildrenConstraint} for a single parent (the common
     * descend-one-member case): {@code addConstraint} is {@code mcc.addMemberConstraint(parents)}, so a
     * one-element parent list is exactly that constraint's children contribution. Multiple parents (an
     * {@code IN}/{@code OR} over parent keys) are not yet expressible — returns empty.
     */
    /**
     * True when {@link #toContribution}'s WHERE reproduces the recorder's factored per-level IN
     * ({@code generateSingleValueInExpr}), so a level-members consumer may build it
     * authoritatively. A single (or no) parent delegates to the wrapped member-children constraint's
     * single-member contribution — always reproduced. Multiple parents reproduce the recorder ONLY when
     * they form a RECTANGLE (the distinct per-level key values cross EXACTLY to the parent set, e.g. all
     * cities in one state): the factored {@code (city IN (..) AND state = X)} form then matches. A
     * NON-rectangle multi-level parent set (e.g. cities spread across several states) makes the builder's
     * {@code memberConstraintContribution} emit the exact tuple form while the descendants recorder keeps
     * the factored bounding box, so the two diverge and the read stays on the recorder.
     */
    public boolean contributionReproducesRecorder() {
        if (parentMembers.size() <= 1) {
            return true;
        }
        RolapMember firstUniqueParent = parentMembers.get(0);
        for (; firstUniqueParent != null && !firstUniqueParent.getLevel().isUnique();
                firstUniqueParent = firstUniqueParent.getParentMember()) {
            // advance to the first unique parent level, as memberConstraintContribution does
        }
        RolapLevel fromLevel = (firstUniqueParent != null) ? firstUniqueParent.getLevel() : null;
        return MemberConstraintWriter.membersFormRectangle(parentMembers, fromLevel);
    }

    @Override
    public java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> toContribution(
        RolapCube baseCube, AggStar aggStar)
    {
        return toContribution(baseCube, aggStar, false);
    }

    /**
     * Factored-member-form twin of {@link #toContribution}: the multi-parent member restriction is
     * built via {@link MemberConstraintWriter#memberConstraintContributionFactored} — the
     * recorder's factored per-level IN form ({@code (city in (..) and state in (..))}, the
     * non-crossjoin {@code addMemberConstraint} bounding box) instead of the exact tuple IN, so it
     * reproduces the recorder's form on a NON-rectangle parent set. AUTHORITATIVE for the
     * computed-expression tuple route ({@code SqlTupleReader.computedTupleSql}) — that recorder path
     * IS the factored form, so the rectangle guard
     * of {@link #contributionReproducesRecorder} does not apply there. Other authoritative renders
     * keep {@link #toContribution} (the cross-join tuple form). Single-parent and context
     * composition are identical to {@link #toContribution}.
     */
    public java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution>
        toContributionFactoredMemberForm(RolapCube baseCube, AggStar aggStar)
    {
        return toContribution(baseCube, aggStar, true);
    }

    private java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> toContribution(
        RolapCube baseCube, AggStar aggStar, boolean factoredMemberForm)
    {
        if (parentMembers.size() == 1) {
            return mcc.toContribution(baseCube, aggStar, parentMembers.get(0));
        }
        // Multiple parents, all from the same level (e.g. a role-restricted descendants set {[OR],[WA]} →
        // store_state IN ('OR','WA')): build the multi-member IN via memberConstraintContribution (a
        // single-level multi-member set → one IN). Guarded at the consumer, so a shape it can't express (calc
        // member, computed / snowflake multi-table key) falls back to the reference query.
        if (!parentMembers.isEmpty()) {
            // AGG MODE: with a non-null aggStar the member
            // restriction is built AGG-SUBSTITUTED via the agg-threaded memberConstraintContribution (the
            // same channel MemberConstraintWriter's SetConstraint args use) and the contribution carries
            // the AggPlan provenance (aggMemberTable — the agg table of the parents' substituted key
            // column). The FACTORED form stays base-only: its sole authoritative consumer
            // (SqlTupleReader.computedTupleSql → toContributionFactoredMemberForm) requires
            // aggStar == null, so the computed-tuple route is untouched. An agg-unresolvable
            // member set (bailed threaded producer / no agg provenance) falls back to the BASE form
            // WITHOUT a plan — the router keeps harvesting agg-unavailable:no-plan and the recorder
            // keeps the read.
            boolean aggMode = aggStar != null && !factoredMemberForm;
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> cp =
                factoredMemberForm
                    ? MemberConstraintWriter.memberConstraintContributionFactored(
                        baseCube, parentMembers, false, false)
                    : MemberConstraintWriter.memberConstraintContribution(
                        baseCube, aggMode ? aggStar : null, parentMembers, false, false);
            java.util.Optional<AggStar.Table> aggMemberTable = aggMode && cp.isPresent()
                ? MemberConstraintWriter.aggMemberTable(baseCube, aggStar, parentMembers.get(0).getLevel())
                : java.util.Optional.empty();
            if (aggMode && aggMemberTable.isEmpty()) {
                aggMode = false;
                cp = MemberConstraintWriter.memberConstraintContribution(baseCube, parentMembers, false, false);
            }
            if (cp.isEmpty()) {
                return java.util.Optional.empty();
            }
            // Compose the wrapped constraint's CONTEXT: the list-parents member constraint is
            // addContextConstraint + addMemberConstraint, so a context-bearing mcc (SqlContextConstraint)
            // must contribute its slicer columns + fact join FIRST, then the parent IN — otherwise the
            // slicer WHERE (e.g. promotion_name = '…') and its joins are silently dropped.
            // Mirrors RolapNativeSet's composition.
            if (mcc instanceof SqlContextConstraint contextConstraint) {
                java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> base =
                    contextConstraint.toContribution(baseCube, aggStar);
                if (base.isEmpty()) {
                    return java.util.Optional.empty();
                }
                org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = base.get();
                java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> wheres =
                    new java.util.ArrayList<>();
                c.where().ifPresent(wheres::add);
                // The parent-set conjunct is one parenthesised group; the mapper splits the top-level
                // And, so the group must sit one level below the split (single-operand And wrap).
                wheres.add(org.eclipse.daanse.sql.statement.api.Predicates.and(
                    java.util.List.of(cp.get().predicate())));
                java.util.List<org.eclipse.daanse.rolap.common.star.RolapStar.Table> joinTables =
                    new java.util.ArrayList<>(c.joinTables());
                if (cp.get().table() != null) {
                    joinTables.add(cp.get().table());
                }
                java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> ordered =
                    new java.util.ArrayList<>(c.orderedPredicates());
                ordered.add(cp.get());
                org.eclipse.daanse.rolap.common.sql.ConstraintContribution composed =
                    new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
                        java.util.Optional.of(wheres.size() == 1 ? wheres.get(0)
                            : org.eclipse.daanse.sql.statement.api.Predicates.and(wheres)),
                        joinTables, ordered, c.memberKeyGroup()).withFactJoinRequired(c.factJoinRequired());
                // AGG MODE: the composed plan is the context constraint's agg predicates (its own
                // AggPlan — present whenever the agg-routed SCC translation succeeded) followed by
                // the parent-set group, mirroring the where composition order (context first, then
                // the member IN — addContextConstraint → addMemberConstraint). A plan-less base
                // (defensive; the SCC agg mode bails to an empty Optional instead) stays plan-less.
                if (aggMode && c.aggPlan().isPresent()) {
                    java.util.List<org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate> aggPredicates =
                        new java.util.ArrayList<>(c.aggPlan().get().orderedAggPredicates());
                    aggPredicates.add(new org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate(
                        aggMemberTable.get(), cp.get().predicate()));
                    composed = composed.withAggPlan(
                        new org.eclipse.daanse.rolap.common.sql.AggPlan(aggStar, aggPredicates));
                }
                return java.util.Optional.of(composed);
            }
            org.eclipse.daanse.rolap.common.sql.ConstraintContribution flat =
                new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
                    // Single-operand And wrap: the parent-set group keeps its parentheses through the
                    // mapper's top-level And split.
                    java.util.Optional.of(org.eclipse.daanse.sql.statement.api.Predicates.and(
                        java.util.List.of(cp.get().predicate()))),
                    cp.get().table() != null ? java.util.List.of(cp.get().table()) : java.util.List.of(),
                    java.util.List.of(cp.get()));
            if (aggMode) {
                flat = flat.withAggPlan(new org.eclipse.daanse.rolap.common.sql.AggPlan(aggStar,
                    java.util.List.of(new org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate(
                        aggMemberTable.get(), cp.get().predicate()))));
            }
            return java.util.Optional.of(flat);
        }
        return java.util.Optional.empty();
    }
}
