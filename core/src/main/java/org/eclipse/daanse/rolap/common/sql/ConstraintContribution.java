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
package org.eclipse.daanse.rolap.common.sql;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;

/**
 * A constraint's contribution to a generic-builder query: an optional {@code WHERE} predicate and
 * the {@link RolapStar.Table}s it needs joined (e.g. the fact table for a context/slicer/role
 * constraint).
 * <p>
 * Constraints expose this via {@code toContribution(...)} on {@code MemberChildrenConstraint} /
 * {@code TupleConstraint}. The SPI method returns {@code Optional<ConstraintContribution>}: an
 * <em>empty Optional</em> means "this constraint cannot (yet) be expressed on the builder — use the
 * legacy the retired query facade path"; a present value (possibly {@link #EMPTY}) is the contribution. An
 * {@code EMPTY} contribution means the constraint adds nothing (e.g. drilling the all member).
 *
 * @param where            the {@code WHERE} predicate (AND-combined), if any
 * @param joinTables       the star tables the predicate references and that must be in {@code FROM}
 * @param orderedPredicates per-column {@code (table, predicate)} pairs in the legacy add order; lets a
 *                          fact-join consumer interleave each table's join with its value-constraints
 *                          exactly as {@code the retired query facade.addToFrom}+{@code addWhere} did (empty for
 *                          dimension-only / member-children constraints, which need no interleaving)
 * @param factJoinRequired  the target level's own existence join to the fact is required even when no
 *                          context column constrains it (a non-empty set: {@code SqlContextConstraint
 *                          .isJoinRequired()} / {@code addLevelConstraint}→{@code joinLevelTableToFactTable}).
 *                          The mapper must add the fact + {@code fact.fk = level.key} on this signal even
 *                          when {@code joinTables} / {@code orderedPredicates} are empty.
 */
public record ConstraintContribution(Optional<Predicate> where, List<RolapStar.Table> joinTables,
        List<ColumnPredicate> orderedPredicates, Optional<Predicate> memberKeyGroup,
        Optional<NativeOrder> nativeOrder, Optional<Predicate> nativeHaving, boolean factJoinRequired) {

    public ConstraintContribution {
        joinTables = List.copyOf(joinTables);
        orderedPredicates = List.copyOf(orderedPredicates);
    }

    /** No existence/fact join required (the prior canonical form). */
    public ConstraintContribution(Optional<Predicate> where, List<RolapStar.Table> joinTables,
            List<ColumnPredicate> orderedPredicates, Optional<Predicate> memberKeyGroup,
            Optional<NativeOrder> nativeOrder, Optional<Predicate> nativeHaving) {
        this(where, joinTables, orderedPredicates, memberKeyGroup, nativeOrder, nativeHaving, false);
    }

    /** No native HAVING. */
    public ConstraintContribution(Optional<Predicate> where, List<RolapStar.Table> joinTables,
            List<ColumnPredicate> orderedPredicates, Optional<Predicate> memberKeyGroup,
            Optional<NativeOrder> nativeOrder) {
        this(where, joinTables, orderedPredicates, memberKeyGroup, nativeOrder, Optional.empty(), false);
    }

    /** No native order (the common case). */
    public ConstraintContribution(Optional<Predicate> where, List<RolapStar.Table> joinTables,
            List<ColumnPredicate> orderedPredicates, Optional<Predicate> memberKeyGroup) {
        this(where, joinTables, orderedPredicates, memberKeyGroup, Optional.empty(), Optional.empty(), false);
    }

    /** No member-key group (tuple / level-members constraints have no children-of restriction). */
    public ConstraintContribution(Optional<Predicate> where, List<RolapStar.Table> joinTables,
            List<ColumnPredicate> orderedPredicates) {
        this(where, joinTables, orderedPredicates, Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    /** Backward-compatible: no per-column ordering (dimension-only / member-children constraints). */
    public ConstraintContribution(Optional<Predicate> where, List<RolapStar.Table> joinTables) {
        this(where, joinTables, List.of(), Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    /** This contribution with the target-level existence/fact join requirement set. */
    public ConstraintContribution withFactJoinRequired(boolean required) {
        return required == factJoinRequired ? this
                : new ConstraintContribution(where, joinTables, orderedPredicates, memberKeyGroup,
                        nativeOrder, nativeHaving, required);
    }

    /**
     * True when this contribution forces a join to the fact table — either a context column already
     * does so (non-empty {@code joinTables}) or the target level's own non-empty existence join is
     * required ({@code factJoinRequired}). This is the single fact-join decision point (the new-world
     * analog of the legacy {@code SqlContextConstraint.isJoinRequired()}): every build-authoritative-
     * vs-guarded gate routes through it, so a new "force the fact join" reason is added here once
     * rather than at each fast-path. NOTE: it deliberately does NOT include {@code where().isEmpty()} —
     * the member-children fast path builds authoritatively WITH a parent-key WHERE, so that term stays
     * inline at the level-members sites only.
     */
    public boolean requiresFactJoin() {
        return !joinTables.isEmpty() || factJoinRequired;
    }

    /**
     * True when the plain (1-arg, unconstrained) {@code TupleSqlMapper.levelMembersSql} fully reproduces
     * this contribution — i.e. it adds nothing: no {@code WHERE}, no fact/context join
     * ({@code requiresFactJoin}), no native ORDER ({@code nativeOrder}, a TopCount/Order measure
     * ordering) and no native HAVING ({@code nativeHaving}, a native Filter). The level-members fast
     * paths may build authoritatively only when this holds; otherwise the constrained mapper is required
     * so the order/having/join is not silently dropped. This is the single level-members "is it plain?"
     * decision point — adding a new clause the plain mapper can't express is handled here once.
     */
    public boolean producesPlainLevelMembers() {
        return where.isEmpty() && !requiresFactJoin() && nativeOrder.isEmpty() && nativeHaving.isEmpty();
    }

    /**
     * A native {@code TopCount}/{@code Order} measure projection plus the order to apply to it: the measure
     * expression is projected (after the level columns, getting its own alias) and that alias is ordered by
     * {@code direction} <em>before</em> the level ORDER BY — reproducing
     * {@code RolapNativeTopCount.TopCountConstraint.addConstraint}'s {@code addSelect}+{@code addOrderBy}.
     */
    public record NativeOrder(org.eclipse.daanse.sql.statement.api.expression.SqlExpression measureExpr,
            org.eclipse.daanse.olap.api.sql.SortingDirection direction, boolean nullable) {}

    /**
     * One context column's value-restriction paired with the star table it lives on, in the legacy
     * {@code addConstraint} order. The tuple mapper walks these to interleave {@code [table join, then
     * that table's where(s)]} — each table joined once (on first appearance), its value-constraints
     * emitted right after — reproducing the retired query facade's output byte-for-byte.
     */
    public record ColumnPredicate(RolapStar.Table table, Predicate predicate) {}

    /** A contribution that adds no predicate and no extra tables. */
    public static final ConstraintContribution EMPTY = new ConstraintContribution(Optional.empty(), List.of());
}
