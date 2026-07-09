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
package org.eclipse.daanse.rolap.common.sqlbuild;

import java.util.List;

import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.NullOrder;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SortDirection;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;

/**
 * Builds the level/tuple-members SELECT with the generic, dialect-free statement builder: the
 * single-target standalone case ({@link #levelMembersSql}), the multi-target tuple read and the
 * virtual-cube union arm ({@link #tupleLevelMembersSql} — arm = same SELECT without ORDER BY,
 * gated by {@link #supportsTupleRead}).
 * <p>
 * Differs from {@link MemberSqlMapper} (a single level's children) in two ways: it emits
 * <em>every</em> non-all level from the hierarchy root down to the target depth (ancestor keys
 * make the row unique), and after each non-key ordinal it adds the level key as an ORDER BY
 * tiebreaker.
 * <p>
 * Scope ({@link #supports}): plain-column key/caption/ordinal/property expressions, no parent-child
 * level, no aggregate-table (collapsed) join. Property GROUP BY is emitted whenever
 * {@code needsGroupBy}.
 */
public final class TupleSqlMapper {

    private TupleSqlMapper() {
    }

    /** True if {@link #levelMembersSql} can build {@code targetLevel} and all its ancestors. */
    public static boolean supports(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        return hierarchy.getRelation() != null
                && RelationFromMapper.supports(hierarchy.getRelation())
                && plainColumns(targetLevel);
    }

    /**
     * True if {@link #levelMembersSql(RolapLevel, boolean)} can
     * build {@code targetLevel}: the relation is any composition of table / join / view / inline that
     * the dialect-aware FROM can render (incl. a view nested inside a join), with plain columns. The
     * dialect overload builds the snowflake <em>subset</em> (not the whole relation), so this is safe
     * to use directly — it joins only the tables the selected columns need, without over-joining.
     */
    public static boolean supportsViaDialectFrom(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        return hierarchy.getRelation() != null
                && renderableWithDialect(hierarchy.getRelation())
                && plainColumns(targetLevel);
    }

    private static boolean renderableWithDialect(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource
                || relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource
                || relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource) {
            return true;
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            return renderableWithDialect(join.getLeft().getSource())
                    && renderableWithDialect(join.getRight().getSource());
        }
        return false;
    }

    /** Plain-column check for every non-all level from the root down to {@code targetLevel}. */
    private static boolean plainColumns(RolapLevel targetLevel) {
        return plainColumns(targetLevel, false);
    }

    /**
     * As {@link #plainColumns(RolapLevel)}; {@code allowParentChild=true} relaxes the check: a
     * parent-child level is accepted when its parent key is a PLAIN column
     * (the {@link #supportsParentChild} criterion — the emission machinery in
     * {@code projectTargetLevels} handles null and non-null {@code nullParentValue} alike).
     * Executed routes gate on the strict form; the relaxed form feeds the parent-child tuple
     * read only.
     */
    private static boolean plainColumns(RolapLevel targetLevel, boolean allowParentChild) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        for (int i = 0; i <= targetLevel.getDepth(); i++) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            if (level.isParentChild()
                    && (!allowParentChild || !isPlainColumn(level.getParentExp()))) {
                return false;
            }
            for (SqlExpression e : levelExpressions(level)) {
                if (!isPlainColumn(e)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The level's projected expressions in emission order — parent key (parent-child level only),
     * key, caption (when present), ordinals, property columns. The single home of that
     * enumeration: the plain-column checks and the FROM alias collection iterate the same list,
     * so a new expression kind is added once, not per loop copy. The parent key leads the list
     * because it enters the FROM FIRST ({@code addToFrom(parentExp)} precedes the key) — a
     * non-PC level contributes no entry for it.
     */
    private static List<SqlExpression> levelExpressions(RolapLevel level) {
        List<SqlExpression> out = new java.util.ArrayList<>();
        if (level.getParentExp() != null) {
            out.add(level.getParentExp());
        }
        out.add(level.getKeyExp());
        if (level.hasCaptionColumn()) {
            out.add(level.getCaptionExp());
        }
        out.addAll(level.getOrdinalExps());
        for (RolapProperty p : level.getProperties()) {
            out.add(p.getExp());
        }
        return out;
    }

    /** The level/tuple-members SELECT for {@code targetLevel} (see {@link #supports}). */
    public static SelectStatement levelMembersSql(RolapLevel targetLevel) {
        return buildLevelSelect(targetLevel, fromForLevels(targetLevel, false), false,
                java.util.List.of(), java.util.Optional.empty());
    }

    /**
     * As {@link #levelMembersSql(RolapLevel)} but renders dialect-aware: a view/inline-table relation
     * (also when nested in a join) becomes a {@code From.raw} (or converted) FROM, and computed
     * (expression) columns render with their dialect-specific SQL — see {@link #supportsViaDialectFrom}
     * and {@link #supportsAllowingExpressions}.
     */
    public static SelectStatement levelMembersSql(RolapLevel targetLevel,
            boolean viewAware) {
        return buildLevelSelect(targetLevel, fromForLevels(targetLevel, viewAware), viewAware,
                java.util.List.of(), java.util.Optional.empty());
    }

    /**
     * The context-constrained level-members SELECT (tuple path): the level relation joined to
     * the fact (and any context dimension tables) so a NON-EMPTY / context restriction applies.
     * FROM = the level relation subset, then the fact, then the context tables (the order the
     * reference query adds them, so the rendered SQL matches);
     * WHERE = the star join predicates then {@code where} (the translated context predicate, each its
     * own conjunct). The fact is derived from {@code joinTables} (each star table knows its star).
     */
    public static SelectStatement levelMembersSql(RolapLevel targetLevel,
            boolean viewAware,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<RolapStar.Table> joinTables) {
        return levelMembersSql(targetLevel, viewAware, where, joinTables, java.util.List.of(),
                java.util.Optional.empty(), java.util.Optional.empty(), false);
    }

    /**
     * As above, but with the context constraint's per-column {@code (table, predicate)} pairs so the
     * fact-join WHERE is interleaved {@code [target-dim join, then per context table: its join (once)
     * then its where(s)]} exactly like the reference path ({@code addLevelMemberSql + addConstraint}). Empty
     * {@code orderedPredicates} builds the same dimension-rooted JOIN…ON shape with the grouped
     * {@code where} split into conjuncts (the reference emits ANSI joins there too); only a shape with
     * no resolvable fact-adjacent table falls back to the comma-join assembly (guarded use).
     */
    public static SelectStatement levelMembersSql(RolapLevel targetLevel,
            boolean viewAware,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            boolean factJoinRequired) {
        return levelMembersSql(targetLevel, viewAware, where, joinTables, orderedPredicates, nativeOrder,
                nativeHaving, factJoinRequired, null);
    }

    /**
     * As above with the union arm's base cube: a virtual-cube level resolves its star key (fact
     * derivation and fact-adjacent table) through {@code getBaseStarKeyColumn(baseCube)} — the
     * star that union arm resolves against. {@code null} keeps the level's own star key.
     */
    public static SelectStatement levelMembersSql(RolapLevel targetLevel,
            boolean viewAware,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            boolean factJoinRequired,
            org.eclipse.daanse.rolap.element.RolapCube baseCube) {
        return tupleLevelMembersSql(List.of(targetLevel), viewAware, where, joinTables, orderedPredicates,
                nativeOrder, nativeHaving, factJoinRequired, baseCube, true);
    }

    /**
     * The multi-target generalization of {@link #levelMembersSql(RolapLevel,
     * boolean, java.util.Optional, java.util.List,
     * java.util.List, java.util.Optional, java.util.Optional, boolean,
     * org.eclipse.daanse.rolap.element.RolapCube)} — emits each target's per-level joins plus the
     * constraint ops:
     * <ul>
     * <li>the FIRST target's relation subset is the FROM root; the fact joins INTO its
     *     fact-adjacent table (the {@code joinLevelTableToFactTable} for target 1);</li>
     * <li>every FURTHER target joins to the fact through its star chains — one chain per projected
     *     level's star key table, registered self-first BEFORE the contribution's context chains, so
     *     the breadth-first fold attaches fact-adjacent tables first (the join order the per-level
     *     fact joins produce: {@code join product on fact… join product_class on product…});
     *     tables already inside the first target's relation (a shared dimension table, e.g. Gender x
     *     Marital Status both on customer) contribute no duplicate join;</li>
     * <li>each target's levels are projected/grouped/ordered in target order
     *     (see {@code buildLevelSelect});</li>
     * <li>{@code emitOrderBy=false} is the union arm ({@code whichSelect != ONLY}): the SELECT/GROUP BY
     *     are identical, the LEVEL ORDER BY (and the parent-key projection) is dropped — the union
     *     wrapper orders by ordinals. A native TopCount/Order measure is STILL projected and ordered:
     *     {@code addConstraintOps} runs without arm knowledge and emits
     *     it on every arm, reproduced here too.</li>
     * </ul>
     * Callers must gate multi-target inputs through {@link #supportsTupleRead} — a multi-target read
     * without a resolvable fact join has no valid shape here (the targets would not correlate).
     */
    public static SelectStatement tupleLevelMembersSql(List<RolapLevel> targetLevels,
            boolean viewAware,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            boolean factJoinRequired,
            org.eclipse.daanse.rolap.element.RolapCube baseCube,
            boolean emitOrderBy) {
        return tupleLevelMembersSql(targetLevels, viewAware, where, joinTables, orderedPredicates,
                nativeOrder, nativeHaving, factJoinRequired, baseCube, emitOrderBy, JoinOrder.FOLD);
    }

    /**
     * Chain-contiguous join-order variant of {@link #tupleLevelMembersSql}: identical SELECT/WHERE/GROUP
     * BY/ORDER BY, but the fact-side joins are emitted with each registered
     * table's snowflake chain contiguous, fact-adjacent-first ({@code join product … join
     * product_class} immediately together), chains in registration order (further targets' chains,
     * then constraint tables) — instead of the breadth-first {@link JoinPlanner#foldJoinSteps}
     * order, which parks a deeper snowflake table (product_class) until after every
     * later-registered fact-adjacent table. Same steps, same ON conditions, only the sequence
     * differs.
     * <p>
     * Used by the computed-expression tuple route
     * ({@code SqlTupleReader.computedTupleSql}): the reference query emits each hierarchy's chain
     * as one unit, so this contiguous order matches it. The other tuple callers use the fold order.
     */
    public static SelectStatement tupleLevelMembersSqlRecorderJoinOrder(List<RolapLevel> targetLevels,
            boolean viewAware,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            boolean factJoinRequired,
            org.eclipse.daanse.rolap.element.RolapCube baseCube,
            boolean emitOrderBy) {
        return tupleLevelMembersSql(targetLevels, viewAware, where, joinTables, orderedPredicates,
                nativeOrder, nativeHaving, factJoinRequired, baseCube, emitOrderBy,
                JoinOrder.CHAIN_CONTIGUOUS);
    }

    /**
     * How the fact-side pending joins are sequenced: {@link #FOLD} is the breadth-first
     * {@link JoinPlanner#foldJoinSteps} (used by most tuple callers);
     * {@link #CHAIN_CONTIGUOUS} keeps each chain contiguous,
     * fact-adjacent-first, in registration order — used by the computed-expression tuple route.
     */
    private enum JoinOrder {
        FOLD, CHAIN_CONTIGUOUS
    }

    private static SelectStatement tupleLevelMembersSql(List<RolapLevel> targetLevels,
            boolean viewAware,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            boolean factJoinRequired,
            org.eclipse.daanse.rolap.element.RolapCube baseCube,
            boolean emitOrderBy,
            JoinOrder joinOrder) {
        RolapLevel targetLevel = targetLevels.get(0);
        org.eclipse.daanse.rolap.element.RolapCubeLevel cubeLevel0 =
                (targetLevel instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel cl) ? cl : null;
        // The fact comes from the first join table's star; or — when there are no join tables but the target
        // level still needs its OWN non-empty existence join (factJoinRequired: a NonEmptyCrossJoin with an
        // all-member co-arg — the addLevelConstraint→joinLevelTableToFactTable case) — from the target
        // level's own star-key table's star.
        RolapStar.Column starKey0 = (cubeLevel0 != null) ? cubeLevel0.getBaseStarKeyColumn(baseCube) : null;
        RolapStar.Table fact = !joinTables.isEmpty()
                ? joinTables.get(0).getStar().getFactTable()
                : (factJoinRequired && starKey0 != null)
                        ? starKey0.getTable().getStar().getFactTable()
                        : null;
        if (fact == null) {
            // A dimension-only restriction (member-key / child-by-name), or factJoinRequired but the target
            // has no resolvable star key: the plain level FROM with the WHERE predicate, no fact join.
            // A multi-target read can never take this branch (supportsTupleRead requires the star key
            // and the routing requires a fact-join contribution) — reaching it is a caller bug.
            if (targetLevels.size() > 1) {
                throw new IllegalStateException(
                        "multi-target tuple read requires a resolvable fact join: " + targetLevels);
            }
            return buildLevelSelect(targetLevels, fromForLevels(targetLevel, viewAware), viewAware,
                    java.util.List.of(), where, nativeOrder, nativeHaving, emitOrderBy, true);
        }
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        java.util.Set<String> dimAliases = RelationFromMapper.tableAliases(hierarchy.getRelation());
        java.util.List<org.eclipse.daanse.sql.statement.api.model.FromClause> items =
                new java.util.ArrayList<>();
        items.add(fromForLevels(targetLevel, viewAware));
        // Add the fact table UNLESS it is already the dimension relation (a degenerate dimension whose own
        // table carries the measure, e.g. a store-grain sum(store.store_sqft) TopCount): fromForLevels above
        // already put it in items.get(0), so adding it again would emit the same alias twice in the comma-join
        // FROM (`store as store, store as store`). Mirrors the
        // context-table dedup below (the !dimAliases.contains check at the joinTables loop).
        if (!dimAliases.contains(fact.getAlias())) {
            items.add(starTableFrom(fact));
        }
        java.util.Set<RolapStar.Table> referenced = new java.util.LinkedHashSet<>();
        // Dedup the FROM by alias: the same context table can appear in joinTables via several join
        // paths (e.g. one per slicer member); adding it more than once would cartesian-explode the
        // result.
        java.util.Set<String> addedAliases = new java.util.HashSet<>();
        addedAliases.add(fact.getAlias());
        for (RolapStar.Table t : joinTables) {
            referenced.add(t);
            if (!dimAliases.contains(t.getAlias()) && addedAliases.add(t.getAlias())) {
                items.add(starTableFrom(t));
            }
        }
        // A degenerate dimension (fact == relation) with no extra context tables leaves one item —
        // that item IS the FROM (FromProduct requires two).
        org.eclipse.daanse.sql.statement.api.model.FromClause from = items.size() == 1 ? items.get(0)
                : new org.eclipse.daanse.sql.statement.api.model.FromClause.FromProduct(items);
        // The constrained level-members query is dimension-rooted: the target relation is the FROM
        // ROOT (its own snowflake JOIN…ON tree intact, NOT flattened to comma+WHERE), the fact joins
        // INTO that root's fact-adjacent table, then each context table joins to the fact — all ANSI
        // JOIN…ON — with the context restrictions in WHERE. Shape:
        //   from <dim relation> join <fact> on <fact.fk = dim.key> join <ctx> on … where <constraints>
        // Rooting at the fact instead would re-join the dimension under a duplicate alias and produce
        // a wrong shape on every constrained query.
        // This applies to BOTH contribution forms: per-column orderedPredicates (WHERE in column
        // order) and the grouped where (split into conjuncts by buildLevelSelect).
        RolapStar.Table factAdjacent = (starKey0 != null)
                ? factAdjacentTable(starKey0.getTable(), fact) : null;
        // Non-first targets reach the FROM through the fact: one star-key table per projected level
        // (root..depth, non-all), skipping tables already inside the first target's relation or the
        // fact itself. These chains are the per-level fact joins (joinLevelTableToFactTable).
        java.util.List<RolapStar.Table> targetChainTables = new java.util.ArrayList<>();
        for (int t = 1; t < targetLevels.size(); t++) {
            RolapLevel target = targetLevels.get(t);
            List<RolapLevel> tLevels = (List<RolapLevel>) target.getHierarchy().getLevels();
            for (int i = 0; i <= target.getDepth(); i++) {
                RolapLevel lvl = tLevels.get(i);
                if (lvl.isAll()
                        || !(lvl instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel tcl)) {
                    continue;
                }
                RolapStar.Column key = tcl.getBaseStarKeyColumn(baseCube);
                if (key != null && !dimAliases.contains(key.getTable().getAlias())
                        && !key.getTable().getAlias().equals(fact.getAlias())) {
                    targetChainTables.add(key.getTable());
                }
            }
        }
        if (factAdjacent != null) {
            java.util.List<JoinPlanner.JoinStep> joinSteps = new java.util.ArrayList<>();
            java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> wheres =
                    new java.util.ArrayList<>();
            // The fact is joined INTO the dimension root ONLY when genuinely needed: a non-empty
            // existence restriction (factJoinRequired), a FURTHER target on another dimension (it can
            // only reach the first target through the fact), or a context/cross-join table on a
            // DIFFERENT dimension. An ancestor-only slicer —
            // every constrained table is part of the target's OWN dimension relation (in dimAliases) —
            // is a plain WHERE on that relation; the recorder adds no fact join there, so
            // emitting one would over-join and diverge. Skip it then.
            boolean crossDimension = !targetChainTables.isEmpty();
            for (org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate cp
                    : orderedPredicates) {
                // A table-less predicate (key-expression constraint on the target's own dimension)
                // never forces the fact join.
                if (cp.table() != null && !dimAliases.contains(cp.table().getAlias())) {
                    crossDimension = true;
                    break;
                }
            }
            if (!crossDimension) {
                for (RolapStar.Table t : referenced) {
                    if (!dimAliases.contains(t.getAlias())) {
                        crossDimension = true;
                        break;
                    }
                }
            }
            if (factJoinRequired || crossDimension) {
                // Join the fact INTO the dimension root using the dimension's own join condition
                // (fact.fk = dim.key) — the fact is the joined table, the dimension stays the FROM root.
                for (org.eclipse.daanse.sql.statement.api.expression.Predicate fp
                        : JoinPlanner.joinChainFor(factAdjacent, fact, new java.util.LinkedHashSet<>())) {
                    joinSteps.add(new JoinPlanner.JoinStep(fact, fp));
                }
            }
            // Context tables join to the fact in the assembler's fold order: register each table's
            // chain (self-first) in encounter order — per-column predicates first, then join-only
            // tables (an all-member cross-join arg's existence join) — and breadth-attach from the
            // fact. A fact-adjacent table lands on its first eligible pass; a deeper snowflake table
            // (e.g. product_class) waits for its parent, so it lands AFTER later-registered
            // fact-adjacent tables — the reference's join order for a cross-dimension mix. A context
            // table that is part of the dimension relation (already in the root) contributes only its
            // WHERE predicate, not a duplicate join.
            java.util.LinkedHashSet<RolapStar.Table> pendingJoins = new java.util.LinkedHashSet<>();
            // Chain tables whose alias is ALREADY in the FROM (the root relation, or the fact):
            // seeded as placed for the fold — joined NOWHERE a second time (the same alias twice is
            // invalid SQL: two target chains sharing an intermediate table, e.g. HR's store→employee
            // →salary and position→employee→salary, re-joined "employee" before this dedup), while
            // their children still attach to them — the recorder assembler joins each table once.
            java.util.Set<RolapStar.Table> prePlaced = new java.util.HashSet<>();
            java.util.Set<String> placedAliases = new java.util.HashSet<>(dimAliases);
            placedAliases.add(fact.getAlias());
            // Further targets' chains FIRST: the recorder registers each target's relation tables
            // during its addLevelMemberSql call, BEFORE the constraint ops run — so their joins
            // precede any foreign context table's. A context predicate on one of these tables then
            // finds it already pending (the set dedups) and contributes only its WHERE conjunct.
            for (RolapStar.Table t : targetChainTables) {
                addChainAliasAware(pendingJoins, prePlaced, placedAliases, t, fact);
            }
            for (org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate cp
                    : orderedPredicates) {
                if (cp.table() != null && !dimAliases.contains(cp.table().getAlias())) {
                    addChainAliasAware(pendingJoins, prePlaced, placedAliases, cp.table(), fact);
                }
                wheres.add(cp.predicate());
            }
            for (RolapStar.Table t : referenced) {
                if (!dimAliases.contains(t.getAlias())) {
                    addChainAliasAware(pendingJoins, prePlaced, placedAliases, t, fact);
                }
            }
            joinSteps.addAll(joinOrder == JoinOrder.CHAIN_CONTIGUOUS
                    ? chainContiguousJoinSteps(pendingJoins, fact, prePlaced)
                    : JoinPlanner.foldJoinSteps(pendingJoins, fact, prePlaced));
            // WHERE: with per-column predicates, a flat top-level AND — buildLevelSelect splits it back
            // into one q.where(...) per operand (a nested AND, e.g. a tuple key, stays grouped), one
            // conjunct per column predicate in column order. A grouped contribution (no per-column
            // ordering) passes its grouped predicate through — same split, same conjunct order.
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where2 =
                    orderedPredicates.isEmpty() ? where
                            : java.util.Optional.of(org.eclipse.daanse.sql.statement.api.Predicates.and(wheres));
            // items.get(0) is the target relation (the snowflake FromJoin tree) — the FROM root.
            return buildLevelSelect(targetLevels, items.get(0), viewAware, joinSteps, where2, nativeOrder,
                    nativeHaving, emitOrderBy, true);
        }
        // No resolvable fact-adjacent table (e.g. a degenerate dimension keyed on the fact) — fall back
        // to the grouped comma-join assembly (single-target only: supportsTupleRead requires the
        // fact-adjacent table for multi-target reads, so reaching this with several targets is a
        // caller bug — the comma join would drop the further targets' joins).
        if (targetLevels.size() > 1) {
            throw new IllegalStateException(
                    "multi-target tuple read requires a fact-adjacent first target: " + targetLevels);
        }
        return buildLevelSelect(targetLevels, from, viewAware,
                JoinPlanner.joinSteps(fact, referenced), where, nativeOrder, nativeHaving, emitOrderBy, true);
    }

    /**
     * A star table as a comma-FROM item, carrying its schema SQL filter (when declared) in the
     * {@code FromTable.filter} slot — the {@code addFromTable} semantics. A
     * {@code buildLevelSelect} consumer LIFTS the slot into a leading explicit WHERE conjunct
     * ({@link RelationFromMapper#liftTableFilters} — the FROM-entry conjunct order);
     * elsewhere the renderer emits the slot as a trailing WHERE conjunct. A star table without a
     * relation-level filter renders as a plain {@code From.table}.
     */
    private static org.eclipse.daanse.sql.statement.api.model.FromClause starTableFrom(
            RolapStar.Table table) {
        return org.eclipse.daanse.sql.statement.api.From.table(
                org.eclipse.daanse.sql.statement.api.From.tableRef(null, table.getTableName()),
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of(table.getAlias()),
                RelationFromMapper.tableFilter(table.getRelation()), java.util.Map.of());
    }

    /**
     * Registers {@code table}'s star chain (self-first, up to but excluding the fact), routing each
     * chain table by alias: a table already present in the caller's FROM ({@code placedAliases} —
     * the root relation's tables and the fact) goes into {@code prePlaced} so the fold treats it as
     * a join ANCHOR without re-joining it; every other table goes into {@code pending}. This is the
     * shared-table dedup of the recorder assembler: two chains crossing the same intermediate table
     * (HR: store→employee→salary and position→employee→salary over an employee⋈store root) join it
     * exactly once.
     */
    private static void addChainAliasAware(java.util.LinkedHashSet<RolapStar.Table> pending,
            java.util.Set<RolapStar.Table> prePlaced, java.util.Set<String> placedAliases,
            RolapStar.Table table, RolapStar.Table fact) {
        for (RolapStar.Table t = table; t != null && t != fact; t = t.getParentTable()) {
            if (placedAliases.contains(t.getAlias())) {
                prePlaced.add(t);
            } else {
                pending.add(t);
            }
        }
    }

    /**
     * The {@link JoinOrder#CHAIN_CONTIGUOUS} sequencing: for each pending table in
     * registration order, its whole chain parent-first — so a snowflake chain stays contiguous
     * ({@code product, product_class}) instead of the fold parking the deeper table behind
     * later-registered fact-adjacent tables. The recorder emits joins this way because each
     * hierarchy's relation is registered (and assembled) as one unit during its
     * {@code addLevelMemberSql} / constraint call. Same steps and ON conditions as the fold —
     * only the sequence differs; {@code prePlaced} tables anchor without being re-joined.
     */
    private static java.util.List<JoinPlanner.JoinStep> chainContiguousJoinSteps(
            java.util.LinkedHashSet<RolapStar.Table> pending, RolapStar.Table fact,
            java.util.Set<RolapStar.Table> prePlaced) {
        java.util.Set<RolapStar.Table> emitted = new java.util.HashSet<>(prePlaced);
        java.util.List<JoinPlanner.JoinStep> steps = new java.util.ArrayList<>();
        for (RolapStar.Table t : pending) {
            steps.addAll(JoinPlanner.joinStepsFor(t, fact, emitted));
        }
        return steps;
    }

    /**
     * Builds the {@code no-fact-adjacent} same-table multi-target shape: a tuple read whose EVERY
     * target hierarchy is degenerate on ONE shared single-table relation (FoodMart's Store cube:
     * {@code [Store] x [Store Type]} both on {@code store}, which IS the fact — the degenerate
     * self-join case), with no fact join contributed — the recorder emits a single-table SELECT:
     * <ul>
     * <li>projection: each target's non-all levels root..target (key + properties), concatenated in
     *     target order; a column projected by two targets keeps BOTH select items under distinct
     *     aliases (e.g. {@code store_type} as {@code c4} property and again {@code c12} as the
     *     second target's key);</li>
     * <li>GROUP BY: the distinct underlying columns in first-occurrence projection order (the
     *     renderer dedups group keys on the rendered string — {@code store_type} once);</li>
     * <li>ORDER BY: the level KEY columns of all targets in target order (the cross-target
     *     {@code orderedColumns} dedup is by column, and a property is never ordered, so the
     *     duplicate second-target key IS ordered — on its own {@code c12} alias);</li>
     * <li>a native Filter HAVING / TopCount ORDER from the contribution is carried through
     *     (every reader path carries nativeHaving+nativeOrder).</li>
     * </ul>
     * Routing MUST check {@link #supportsSameTableTupleRead} first — an input outside the shape
     * throws {@link IllegalStateException} with the decline reason (a caller bug, not a runtime
     * fallback). {@link #supportsTupleRead} still declines the family; this is its dedicated
     * degenerate-shape sibling.
     */
    public static SelectStatement sameTableTupleLevelMembersSql(List<RolapLevel> targetLevels,
            boolean viewAware,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            org.eclipse.daanse.rolap.element.RolapCube baseCube,
            boolean emitOrderBy) {
        String declineReason = sameTableDeclineReason(targetLevels, baseCube, joinTables, orderedPredicates);
        if (declineReason != null) {
            throw new IllegalStateException("same-table decline reason=" + declineReason);
        }
        RolapLevel first = targetLevels.get(0);
        java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> wheres =
                new java.util.ArrayList<>();
        for (org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate cp
                : orderedPredicates) {
            wheres.add(cp.predicate());
        }
        // WHERE assembly mirrors the fact-joined path: per-column predicates as a flat AND
        // (buildLevelSelect splits it into one conjunct per predicate), else the grouped where.
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where2 =
                orderedPredicates.isEmpty() ? where
                        : java.util.Optional.of(org.eclipse.daanse.sql.statement.api.Predicates.and(wheres));
        return buildLevelSelect(targetLevels, fromForLevels(first, viewAware), viewAware,
                java.util.List.of(), where2, nativeOrder, nativeHaving, emitOrderBy, true);
    }

    /**
     * The routing gate for {@link #sameTableTupleLevelMembersSql}: true exactly when the tuple
     * read has the same-table degenerate shape that method builds (every target's projection on
     * ONE shared single-table relation, no fact-adjacent first target, no contribution table or
     * predicate outside the shared table). A decline is logged (grep-stable
     * {@code same-table decline reason=} line) and keeps the recorder — a documented decline, not
     * a runtime fallback.
     */
    public static boolean supportsSameTableTupleRead(List<RolapLevel> targetLevels,
            org.eclipse.daanse.rolap.element.RolapCube baseCube,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates) {
        String declineReason = sameTableDeclineReason(targetLevels, baseCube, joinTables, orderedPredicates);
        if (declineReason != null) {
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                    "same-table decline reason={}", declineReason);
            return false;
        }
        return true;
    }

    /**
     * The shared shape check behind {@link #supportsSameTableTupleRead} (gate) and
     * {@link #sameTableTupleLevelMembersSql} (defensive re-check): {@code null} when the read is
     * the same-table degenerate shape, else the decline reason.
     */
    private static String sameTableDeclineReason(List<RolapLevel> targetLevels,
            org.eclipse.daanse.rolap.element.RolapCube baseCube,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates) {
        if (targetLevels.size() < 2) {
            return "single-target (served elsewhere): " + targetLevels;
        }
        RolapLevel first = targetLevels.get(0);
        java.util.Set<String> dimAliases =
                RelationFromMapper.tableAliases(first.getHierarchy().getRelation());
        if (dimAliases.size() != 1) {
            return "multi-table-first-relation: " + dimAliases;
        }
        for (RolapLevel target : targetLevels) {
            if (!supports(target)) {
                return "level-unsupported: " + target.getUniqueName();
            }
            List<RolapLevel> levels = (List<RolapLevel>) target.getHierarchy().getLevels();
            for (int i = 0; i <= target.getDepth(); i++) {
                RolapLevel lvl = levels.get(i);
                if (lvl.isAll()) {
                    continue;
                }
                for (SqlExpression e : levelExpressions(lvl)) {
                    // supports() above guarantees plain columns, so every expression is a RolapColumn.
                    if (!(e instanceof org.eclipse.daanse.rolap.element.RolapColumn c)
                            || c.getTable() == null || !dimAliases.contains(c.getTable())) {
                        return "projection-outside-shared-table: " + lvl.getUniqueName();
                    }
                }
            }
        }
        // This builder serves ONLY the topology supportsTupleRead declines with
        // no-fact-adjacent-table: a first target whose star chain has a fact-adjacent table belongs
        // to the authoritative fact-joined read, not here.
        if (first instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel firstCube) {
            RolapStar.Column firstKey = firstCube.getBaseStarKeyColumn(baseCube);
            if (firstKey != null && factAdjacentTable(firstKey.getTable(),
                    firstKey.getTable().getStar().getFactTable()) != null) {
                return "fact-adjacent-first-target (authoritative shape)";
            }
        }
        // The contribution must not pull any table outside the shared relation: a foreign context
        // table would require the fact join this shape does not have.
        for (RolapStar.Table t : joinTables) {
            if (!dimAliases.contains(t.getAlias())) {
                return "join-table-outside-shared-table: " + t.getAlias();
            }
        }
        for (org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate cp
                : orderedPredicates) {
            if (cp.table() != null && !dimAliases.contains(cp.table().getAlias())) {
                return "predicate-outside-shared-table: " + cp.table().getAlias();
            }
        }
        return null;
    }

    /**
     * The comma-product tuple read — the NO-FACT-JOIN multi-target assembly
     * ({@code ContributionAssembler.buildFromClause} with
     * disconnected FROM components): each target registers its own relation subset, no fact joins
     * them, so the assembler's fold makes no progress across targets and emits
     * <ul>
     * <li>FROM: the FIRST target's subset intact (its internal snowflake edges fold into ANSI
     *     JOIN…ON — they connect to the base item), then each FURTHER target's subset FLATTENED to
     *     its tables, comma-appended in registration order (per-target {@code addToFrom} order =
     *     the relation tree left-to-right), deduped by alias across targets;</li>
     * <li>WHERE: the context conjuncts first (the replayed constraint ops), then each further
     *     target's INTERNAL join equality (the assembler appends unused edges after the replayed
     *     WHERE ops), one conjunct per edge in registration order — an edge both of whose tables
     *     were already placed by earlier targets is dropped (the recorder records an edge only
     *     when a side was newly added);</li>
     * <li>HAVING/native order carried on every path; ORDER BY per {@code emitOrderBy}
     *     ({@code whichSelect == ONLY}).</li>
     * </ul>
     * Callers must gate through {@link #supportsProductTupleRead} AND require a contribution with
     * NO fact join ({@code !requiresFactJoin()}) and no native order; an input outside the shape
     * throws {@link IllegalStateException}. When the alias dedup collapses everything into the
     * first item, the read IS the same-table shape and delegates to
     * {@link #sameTableTupleLevelMembersSql}. Example shape: {@code store as store, product as
     * product, product_class as product_class …
     * where product.product_class_id = product_class.product_class_id … having … REGEXP …}.
     */
    public static SelectStatement productTupleLevelMembersSql(List<RolapLevel> targetLevels,
            boolean viewAware,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            org.eclipse.daanse.rolap.element.RolapCube baseCube,
            boolean emitOrderBy) {
        String declineReason = productTupleDeclineReason(targetLevels, java.util.List.of(), orderedPredicates);
        if (declineReason != null) {
            throw new IllegalStateException("product-tuple decline reason=" + declineReason);
        }
        RolapLevel first = targetLevels.get(0);
        java.util.List<org.eclipse.daanse.sql.statement.api.model.FromClause> items =
                new java.util.ArrayList<>();
        java.util.Set<String> placedAliases = new java.util.LinkedHashSet<>();
        items.add(fromForLevels(first, viewAware));
        placedAliases.addAll(targetFromTables(first));
        java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> joinPreds =
                new java.util.ArrayList<>();
        for (int t = 1; t < targetLevels.size(); t++) {
            RolapLevel target = targetLevels.get(t);
            collectProductItems(target.getHierarchy().getRelation(), targetFromTables(target),
                    placedAliases, items, joinPreds);
        }
        if (items.size() == 1 && targetLevels.size() > 1 && joinPreds.isEmpty()) {
            // Every further target collapsed into the first item — the same-table degenerate shape.
            return sameTableTupleLevelMembersSql(targetLevels, viewAware, where, java.util.List.of(),
                    orderedPredicates, nativeOrder, nativeHaving, baseCube, emitOrderBy);
        }
        // WHERE: context conjuncts (per-column order, or the grouped where split by
        // buildLevelSelect), then the further targets' internal join equalities — the assembler
        // appends unused edges AFTER the replayed WHERE ops.
        java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> conjuncts =
                new java.util.ArrayList<>();
        if (!orderedPredicates.isEmpty()) {
            for (org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate cp
                    : orderedPredicates) {
                conjuncts.add(cp.predicate());
            }
        } else {
            where.ifPresent(p -> {
                if (p instanceof org.eclipse.daanse.sql.statement.api.expression.Predicate.And and) {
                    conjuncts.addAll(and.operands());
                } else {
                    conjuncts.add(p);
                }
            });
        }
        conjuncts.addAll(joinPreds);
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where2 =
                conjuncts.isEmpty() ? java.util.Optional.empty()
                        : java.util.Optional.of(org.eclipse.daanse.sql.statement.api.Predicates.and(conjuncts));
        org.eclipse.daanse.sql.statement.api.model.FromClause from = items.size() == 1
                ? items.get(0)
                : new org.eclipse.daanse.sql.statement.api.model.FromClause.FromProduct(items);
        return buildLevelSelect(targetLevels, from, viewAware, java.util.List.of(), where2,
                nativeOrder, nativeHaving, emitOrderBy, true);
    }

    /**
     * The routing gate for {@link #productTupleLevelMembersSql}: true exactly when every
     * target is strictly buildable with its projections inside its OWN relation and no
     * contribution table/predicate references a table outside the targets' alias union. The
     * CALLER additionally requires the no-fact-join contribution ({@code !requiresFactJoin()})
     * and an empty native order — those live on the contribution, not the levels. A decline logs
     * a grep-stable {@code product-tuple decline reason=} line.
     */
    public static boolean supportsProductTupleRead(List<RolapLevel> targetLevels,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates) {
        String declineReason = productTupleDeclineReason(targetLevels, joinTables, orderedPredicates);
        if (declineReason != null) {
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                    "product-tuple decline reason={}", declineReason);
            return false;
        }
        return true;
    }

    /** The shared shape check behind {@link #supportsProductTupleRead} (gate) and
     *  {@link #productTupleLevelMembersSql} (defensive re-check): {@code null} when the read fits
     *  the comma-product shape, else the decline reason. */
    private static String productTupleDeclineReason(List<RolapLevel> targetLevels,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates) {
        if (targetLevels.size() < 2) {
            return "single-target (served elsewhere): " + targetLevels;
        }
        java.util.Set<String> unionAliases = new java.util.LinkedHashSet<>();
        for (RolapLevel target : targetLevels) {
            if (!supports(target)) {
                return "level-unsupported: " + target.getUniqueName();
            }
            java.util.Set<String> ownAliases =
                    RelationFromMapper.tableAliases(target.getHierarchy().getRelation());
            List<RolapLevel> levels = (List<RolapLevel>) target.getHierarchy().getLevels();
            for (int i = 0; i <= target.getDepth(); i++) {
                RolapLevel lvl = levels.get(i);
                if (lvl.isAll()) {
                    continue;
                }
                for (SqlExpression e : levelExpressions(lvl)) {
                    // supports() above guarantees plain columns, so every expression is a RolapColumn.
                    if (!(e instanceof org.eclipse.daanse.rolap.element.RolapColumn c)
                            || c.getTable() == null || !ownAliases.contains(c.getTable())) {
                        return "projection-outside-own-relation: " + lvl.getUniqueName();
                    }
                }
            }
            unionAliases.addAll(ownAliases);
        }
        // Without a fact join no table outside the targets' relations can reach the FROM: a
        // contribution referencing one is outside this shape.
        for (RolapStar.Table t : joinTables) {
            if (!unionAliases.contains(t.getAlias())) {
                return "join-table-outside-targets: " + t.getAlias();
            }
        }
        for (org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate cp
                : orderedPredicates) {
            if (cp.table() != null && !unionAliases.contains(cp.table().getAlias())) {
                return "predicate-outside-targets: " + cp.table().getAlias();
            }
        }
        return null;
    }

    /** The FROM-table aliases of one target's relation subset — the tables its
     *  {@link #fromForLevels} FROM contains (levelExpressions' aliases widened by
     *  {@link RelationFromMapper#memberFromTables}). */
    private static java.util.Set<String> targetFromTables(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        java.util.Set<String> levelTables = new java.util.LinkedHashSet<>();
        for (int i = 0; i <= targetLevel.getDepth(); i++) {
            RolapLevel lvl = levels.get(i);
            if (lvl.isAll()) {
                continue;
            }
            for (SqlExpression e : levelExpressions(lvl)) {
                addAlias(levelTables, e);
            }
        }
        return RelationFromMapper.memberFromTables(hierarchy.getRelation(), levelTables);
    }

    /**
     * Flattens a further target's relation subset for the comma-product FROM: each included table
     * not yet placed is appended as its own item (registration order = the relation tree
     * left-to-right, the recorder's {@code addFrom} recursion), and each join whose BOTH sides are
     * included contributes its equality to {@code joinPreds} when a side newly entered the FROM —
     * the recorder records an edge only when {@code addFrom} added a side ({@code addJoin}), and
     * the assembler pushes edges of disconnected components into WHERE.
     */
    private static void collectProductItems(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            java.util.Set<String> included, java.util.Set<String> placedAliases,
            java.util.List<org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> joinPreds) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            boolean leftIncluded = hasIncludedTable(join.getLeft().getSource(), included);
            boolean rightIncluded = hasIncludedTable(join.getRight().getSource(), included);
            int before = items.size();
            if (leftIncluded) {
                collectProductItems(join.getLeft().getSource(), included, placedAliases, items, joinPreds);
            }
            if (rightIncluded) {
                collectProductItems(join.getRight().getSource(), included, placedAliases, items, joinPreds);
            }
            if (leftIncluded && rightIncluded && items.size() > before) {
                joinPreds.add(RelationFromMapper.joinOn(join));
            }
            return;
        }
        String alias = org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias(relation);
        if (included.contains(alias) && placedAliases.add(alias)) {
            // fromReferenced on the single leaf yields the FromTable (incl. the filter slot).
            items.add(RelationFromMapper.fromReferenced(relation, java.util.Set.of(alias)));
        }
    }

    /** True when the relation subtree contains an included table alias. */
    private static boolean hasIncludedTable(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            java.util.Set<String> included) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            return hasIncludedTable(join.getLeft().getSource(), included)
                    || hasIncludedTable(join.getRight().getSource(), included);
        }
        return included.contains(org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias(relation));
    }

    /**
     * True if {@link #tupleLevelMembersSql} can build a fact-joined tuple read (a multi-target group,
     * or a single-target union arm resolved against {@code baseCube}):
     * <ul>
     * <li>every target level is buildable by the strict {@link #supports} (plain columns, no
     *     parent-child, subset-resolvable relation) — the wider expression/view scopes stay on the
     *     recorder here, matching the single-target constrained gate;</li>
     * <li>the FIRST target resolves a star key on {@code baseCube} whose table chain reaches the fact
     *     (it anchors the FROM root and the fact join);</li>
     * <li>every FURTHER target's projected levels resolve star keys whose chains reach the SAME fact,
     *     and every projected expression lands on a chain table, inside the first target's relation,
     *     or on the fact — those chains are that target's entire FROM contribution, so an expression
     *     on any other table would reference an alias missing from the FROM.</li>
     * </ul>
     * A level not on {@code baseCube} (its star key does not resolve) fails the check — the recorder
     * keeps that arm.
     */
    public static boolean supportsTupleRead(List<RolapLevel> targetLevels,
            org.eclipse.daanse.rolap.element.RolapCube baseCube) {
        return supportsTupleRead(targetLevels, baseCube, false, false);
    }

    /**
     * As {@link #supportsTupleRead}, but a parent-child level
     * whose parent key is a plain column ({@link #plainColumns(RolapLevel, boolean)} relaxation)
     * passes the per-level check — the emission ({@code projectTargetLevels} parent handling incl.
     * null-collation) already exists. Currently unused by executed routes; it scopes the
     * parent-child tuple read to cases where PC was the only blocker. Logs nothing (the strict
     * gate already attributed the decline — the log lines must not double-fire).
     */
    public static boolean supportsTupleReadAllowingParentChild(List<RolapLevel> targetLevels,
            org.eclipse.daanse.rolap.element.RolapCube baseCube) {
        return supportsTupleRead(targetLevels, baseCube, true, false);
    }

    /**
     * QUIET variant of {@link #supportsTupleRead}: the SAME gate without the
     * {@code supportsTupleRead decline reason=} log lines. Used by the {@code projection-scope}
     * route ({@code SqlTupleReader}'s T4 branch): the loud gate already attributed the decline for
     * the original levels at the route decision, so the base-mapped re-walk must not
     * double-fire the greps.
     */
    public static boolean supportsTupleReadQuiet(List<RolapLevel> targetLevels,
            org.eclipse.daanse.rolap.element.RolapCube baseCube) {
        return supportsTupleRead(targetLevels, baseCube, false, true);
    }

    /** A level buildable when parent-child is allowed: strict relation scope, plain columns with
     *  the PC parent-key relaxation. */
    private static boolean supportsWithParentChild(RolapLevel level) {
        RolapHierarchy hierarchy = level.getHierarchy();
        return hierarchy.getRelation() != null
                && RelationFromMapper.supports(hierarchy.getRelation())
                && plainColumns(level, true);
    }

    private static boolean supportsTupleRead(List<RolapLevel> targetLevels,
            org.eclipse.daanse.rolap.element.RolapCube baseCube, boolean allowParentChild,
            boolean quiet) {
        if (targetLevels.isEmpty()) {
            if (!allowParentChild && !quiet) {
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                        "supportsTupleRead decline reason=empty-target-list");
            }
            return false;
        }
        for (RolapLevel level : targetLevels) {
            if (!(allowParentChild ? supportsWithParentChild(level) : supports(level))) {
                if (!allowParentChild && !quiet) {
                    org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                            "supportsTupleRead decline reason=level-unsupported level={} pc={} exprOk={}",
                            level.getUniqueName(), level.isParentChild(),
                            supportsAllowingExpressions(level));
                }
                return false;
            }
        }
        if (!(targetLevels.get(0) instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel firstCube)) {
            if (!allowParentChild && !quiet) {
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                        "supportsTupleRead decline reason=first-target-not-cube-level");
            }
            return false;
        }
        RolapStar.Column firstKey = firstCube.getBaseStarKeyColumn(baseCube);
        if (firstKey == null) {
            if (!allowParentChild && !quiet) {
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                        "supportsTupleRead decline reason=first-target-no-star-key");
            }
            return false;
        }
        RolapStar.Table fact = firstKey.getTable().getStar().getFactTable();
        if (factAdjacentTable(firstKey.getTable(), fact) == null) {
            if (!allowParentChild && !quiet) {
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                        "supportsTupleRead decline reason=no-fact-adjacent-table");
            }
            return false;
        }
        java.util.Set<String> firstAliases =
                RelationFromMapper.tableAliases(targetLevels.get(0).getHierarchy().getRelation());
        for (int t = 1; t < targetLevels.size(); t++) {
            RolapLevel target = targetLevels.get(t);
            List<RolapLevel> levels = (List<RolapLevel>) target.getHierarchy().getLevels();
            java.util.Set<String> chainAliases = new java.util.HashSet<>();
            for (int i = 0; i <= target.getDepth(); i++) {
                RolapLevel lvl = levels.get(i);
                if (lvl.isAll()) {
                    continue;
                }
                if (!(lvl instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel cl)) {
                    if (!allowParentChild && !quiet) {
                        org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                                "supportsTupleRead decline reason=further-target-not-cube-level");
                    }
                    return false;
                }
                RolapStar.Column key = cl.getBaseStarKeyColumn(baseCube);
                if (key == null) {
                    if (!allowParentChild && !quiet) {
                        org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                                "supportsTupleRead decline reason=further-no-star-key");
                    }
                    return false;
                }
                // A chain table SHARED with the root relation (its alias in the first target's
                // relation — HR: position→employee where the root is employee⋈store) is fine: it
                // still counts as coverage here, and the mapper's alias-aware registration joins it
                // exactly once (prePlaced anchor, never a duplicate alias).
                RolapStar.Table tab = key.getTable();
                while (tab != null && tab != fact) {
                    chainAliases.add(tab.getAlias());
                    tab = tab.getParentTable();
                }
                if (tab != fact) {
                    if (!allowParentChild && !quiet) {
                        org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                                "supportsTupleRead decline reason=chain-other-fact");
                    }
                    return false; // the chain does not reach the SAME fact table
                }
            }
            for (int i = 0; i <= target.getDepth(); i++) {
                RolapLevel lvl = levels.get(i);
                if (lvl.isAll()) {
                    continue;
                }
                for (SqlExpression e : levelExpressions(lvl)) {
                    // supports() above guarantees plain columns, so every expression is a RolapColumn.
                    if (e instanceof org.eclipse.daanse.rolap.element.RolapColumn c
                            && c.getTable() != null
                            && !chainAliases.contains(c.getTable())
                            && !firstAliases.contains(c.getTable())
                            && !fact.getAlias().equals(c.getTable())) {
                        if (!allowParentChild && !quiet) {
                            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                                    "supportsTupleRead decline reason=projection-outside-scope");
                        }
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Sub-classification for the SURVIVING T4 recorder events (the
     * {@code "tuple/arm shape outside the builder's authoritative scope"} declines that none of
     * the fallback routes — {@code firstTargetNoStarKeySql},
     * {@code noFactAdjacentTupleSql}, {@code computedTupleSql} — took): re-derives WHICH sub-gate
     * of {@link #supportsTupleRead} fails and logs ONE grep-stable line
     * ({@code t4-census sub=<token> …}). Classification only. The walk is QUIET: it must not re-fire the
     * {@code supportsTupleRead decline reason=} lines (those already fired at the route decision,
     * and this would double-count them).
     * <p>
     * Token set (grep-stable):
     * <ul>
     *   <li>{@code t4-arm-computed} — a union arm ({@code whichSelect != ONLY}) with a
     *       computed-expression level: the ONLY-route case goes through {@code computedTupleSql},
     *       the arm has no builder form yet;</li>
     *   <li>{@code t4-computed-residue} — an ONLY-route computed read {@code computedTupleSql}
     *       still declined (its constraint did not contribute, or a co-level is outside the
     *       expression scope);</li>
     *   <li>{@code t4-no-star-key-survivor[:first|:further|:first-not-cube-level|
     *       :further-not-cube-level]} — a star key unresolvable on the base cube that
     *       {@code firstTargetNoStarKeySql} did not handle;</li>
     *   <li>{@code t4-no-fact-adjacent:<condition>} — the first target's chain does not reach the
     *       fact AND the same-table gate misses; {@code <condition>} is the same-table gate's
     *       failing condition ({@link #sameTableDeclineReason});</li>
     *   <li>{@code t4-projection-scope} — a further target's projection lands outside its chain /
     *       the first relation / the fact;</li>
     *   <li>{@code t4-other:*} — residue (parent-child with a second blocker, view/inline level,
     *       chain-other-fact, empty target list);</li>
     *   <li>{@code t4-none} — defensive: the strict gate passes, so the event was no T4 decline.</li>
     * </ul>
     * SqlTupleReader wiring ({@code generateSelectForLevels}): three locals capture
     * the T4 decline context where the reason is set; the single call sits AFTER the
     * {@code noFactAdjacentTupleSql}/{@code computedTupleSql} routes, immediately before the
     * recorder construction — so it fires exactly for the surviving events.
     */
    public static void logT4SubCensus(List<RolapLevel> targetLevels,
            org.eclipse.daanse.rolap.element.RolapCube baseCube, boolean unionArm,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates) {
        if (!org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.isDebugEnabled()) {
            return;
        }
        String token = t4SubToken(targetLevels, baseCube, unionArm, joinTables, orderedPredicates);
        org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                "t4-census sub={} arm={} targets={} levels={}",
                token, unionArm, targetLevels.size(),
                targetLevels.stream().map(RolapLevel::getUniqueName).toList());
    }

    /**
     * The token derivation behind {@link #logT4SubCensus} — a QUIET copy of the
     * {@link #supportsTupleRead} sub-gate walk (keep in lockstep with that method; extracted so
     * the unit test exercises the classification without the log side effects). Package-visible
     * for the test.
     */
    static String t4SubToken(List<RolapLevel> targetLevels,
            org.eclipse.daanse.rolap.element.RolapCube baseCube, boolean unionArm,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates) {
        if (targetLevels.isEmpty()) {
            return "t4-other:empty-target-list";
        }
        for (RolapLevel level : targetLevels) {
            if (!supports(level)) {
                if (level.isParentChild()) {
                    // The PC-only blocker is handled by the PC-relaxed gate; a surviving PC read
                    // has a SECOND blocker — residue, not one of the four sub-shapes.
                    return "t4-other:parent-child";
                }
                if (supportsAllowingExpressions(level)) {
                    return unionArm ? "t4-arm-computed" : "t4-computed-residue";
                }
                return "t4-other:level-unsupported";
            }
        }
        if (!(targetLevels.get(0) instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel firstCube)) {
            return "t4-no-star-key-survivor:first-not-cube-level";
        }
        RolapStar.Column firstKey = firstCube.getBaseStarKeyColumn(baseCube);
        if (firstKey == null) {
            return "t4-no-star-key-survivor:first";
        }
        RolapStar.Table fact = firstKey.getTable().getStar().getFactTable();
        if (factAdjacentTable(firstKey.getTable(), fact) == null) {
            // The no-fact-adjacent family: the (All)-drop and same-table sub-shapes are handled in
            // noFactAdjacentTupleSql — a SURVIVOR missed the same-table gate; attribute WHICH
            // condition.
            String condition = sameTableDeclineReason(targetLevels, baseCube, joinTables,
                    orderedPredicates);
            return "t4-no-fact-adjacent:" + (condition == null ? "same-table-gate-pass" : condition);
        }
        java.util.Set<String> firstAliases =
                RelationFromMapper.tableAliases(targetLevels.get(0).getHierarchy().getRelation());
        for (int t = 1; t < targetLevels.size(); t++) {
            RolapLevel target = targetLevels.get(t);
            List<RolapLevel> levels = (List<RolapLevel>) target.getHierarchy().getLevels();
            java.util.Set<String> chainAliases = new java.util.HashSet<>();
            for (int i = 0; i <= target.getDepth(); i++) {
                RolapLevel lvl = levels.get(i);
                if (lvl.isAll()) {
                    continue;
                }
                if (!(lvl instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel cl)) {
                    return "t4-no-star-key-survivor:further-not-cube-level";
                }
                RolapStar.Column key = cl.getBaseStarKeyColumn(baseCube);
                if (key == null) {
                    return "t4-no-star-key-survivor:further";
                }
                RolapStar.Table tab = key.getTable();
                while (tab != null && tab != fact) {
                    chainAliases.add(tab.getAlias());
                    tab = tab.getParentTable();
                }
                if (tab != fact) {
                    return "t4-other:chain-other-fact";
                }
            }
            for (int i = 0; i <= target.getDepth(); i++) {
                RolapLevel lvl = levels.get(i);
                if (lvl.isAll()) {
                    continue;
                }
                for (SqlExpression e : levelExpressions(lvl)) {
                    if (e instanceof org.eclipse.daanse.rolap.element.RolapColumn c
                            && c.getTable() != null
                            && !chainAliases.contains(c.getTable())
                            && !firstAliases.contains(c.getTable())
                            && !fact.getAlias().equals(c.getTable())) {
                        return "t4-projection-scope";
                    }
                }
            }
        }
        return "t4-none";
    }

    /**
     * The table in {@code table}'s parent chain that joins directly to {@code fact} (its parent is the
     * fact) — the dimension's fact-adjacent table, the one the fact's foreign key references. The rest of
     * the dimension's snowflake hangs off it inside the relation. Returns {@code null} when the chain does
     * not reach the fact (e.g. a degenerate dimension keyed on the fact table itself).
     */
    // Package-visible: MemberSqlMapper derives the same dimension→fact edge for member-children queries.
    static RolapStar.Table factAdjacentTable(RolapStar.Table table, RolapStar.Table fact) {
        RolapStar.Table t = table;
        while (t != null && t.getParentTable() != null && t.getParentTable() != fact) {
            t = t.getParentTable();
        }
        return (t != null && t.getParentTable() == fact) ? t : null;
    }

    /**
     * Broadest predicate: like {@link #supportsViaDialectFrom} but also allows computed (expression)
     * key/caption/ordinal/property columns, which the dialect {@code levelMembersSql} overload renders
     * via each expression's dialect-specific SQL. Guarded-only: a computed column adds no table to the
     * FROM subset, so on a multi-table relation the subset may be wrong — must be verified per case.
     */
    public static boolean supportsAllowingExpressions(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        if (hierarchy.getRelation() == null || !renderableWithDialect(hierarchy.getRelation())) {
            return false;
        }
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        for (int i = 0; i <= targetLevel.getDepth(); i++) {
            RolapLevel level = levels.get(i);
            if (!level.isAll() && level.isParentChild()) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if {@link #levelMembersSql(RolapLevel, boolean)} can
     * build a parent-child level: a plain parent key, a null {@code nullParentValue} (so the parent
     * ORDER BY is the nulls-first form a {@code SortSpec} expresses), plain key/caption/ordinal/property
     * columns, and a renderable relation. Guarded-only until verified per dialect.
     */
    public static boolean supportsParentChild(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        if (hierarchy.getRelation() == null || !renderableWithDialect(hierarchy.getRelation())) {
            return false;
        }
        boolean sawParentChild = false;
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        for (int i = 0; i <= targetLevel.getDepth(); i++) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            if (level.isParentChild()) {
                sawParentChild = true;
                // both null and non-null nullParentValue are handled (the latter via the dialect
                // order-value generator through SortSpec.withNullSortValue).
                if (!isPlainColumn(level.getParentExp())) {
                    return false;
                }
            }
            for (SqlExpression e : levelExpressions(level)) {
                if (!isPlainColumn(e)) {
                    return false;
                }
            }
        }
        return sawParentChild;
    }

    /**
     * As {@link #supportsParentChild}, but the PARENT key
     * may be a COMPUTED ({@code <SQL>}) expression — the OrderByAliasTest#testSqlInParentExpression
     * shape ({@code RTRIM(supervisor_id)} as the PC parent key). Requires at least one such
     * computed parent (a plain-parent PC read is the already-served {@code supportsParentChild}
     * family); every OTHER projected expression stays plain. The emission machinery already
     * carries the shape: {@code projectTargetLevels} projects the parent through the
     * {@link JoinPlanner#expressionFor} RawVariant channel and collates a non-null
     * {@code nullParentValue} via {@code SortSpec.withNullSortValue}; {@link #fromForLevels} falls
     * back to the whole relation for the computed expression. {@code SqlTupleReader}'s standalone
     * unconstrained level-members route gates on it. Logs nothing.
     */
    public static boolean supportsParentChildComputedParent(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        if (hierarchy.getRelation() == null || !renderableWithDialect(hierarchy.getRelation())) {
            return false;
        }
        boolean sawComputedParent = false;
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        for (int i = 0; i <= targetLevel.getDepth(); i++) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            if (level.isParentChild()) {
                if (level.getParentExp() == null) {
                    return false;
                }
                if (!isPlainColumn(level.getParentExp())) {
                    sawComputedParent = true;
                }
            }
            // Every projected expression EXCEPT the parent key stays plain — the strict
            // supportsParentChild criteria minus the parent-key column check.
            if (!isPlainColumn(level.getKeyExp())) {
                return false;
            }
            if (level.hasCaptionColumn() && !isPlainColumn(level.getCaptionExp())) {
                return false;
            }
            for (SqlExpression e : level.getOrdinalExps()) {
                if (!isPlainColumn(e)) {
                    return false;
                }
            }
            for (RolapProperty p : level.getProperties()) {
                if (!isPlainColumn(p.getExp())) {
                    return false;
                }
            }
        }
        return sawComputedParent;
    }

    /**
     * The BASE-table provenance comment for a level query: the dimension plus the DEEPEST projected
     * level (root..{@code targetLevel}) whose key column lives on the base table — the level whose
     * relation anchors the FROM (e.g. {@code dimension [Customer] level table [Gender].[Gender]}).
     * When no projected level anchors there (snowflake base above every projected level's table, or
     * an unknown base alias) it falls back to the hierarchy form
     * ({@code dimension [X] hierarchy table [Y]}). Render-only vocabulary; never part of executed SQL.
     */
    public static String baseTableComment(RolapLevel targetLevel,
            org.eclipse.daanse.sql.statement.api.model.TableAlias baseAlias) {
        return baseTableComment(targetLevel, baseAlias == null ? null : baseAlias.name());
    }

    /** As {@link #baseTableComment(RolapLevel, org.eclipse.daanse.sql.statement.api.model.TableAlias)}
     *  for callers holding the base table alias as a plain string (the QueryRecorder path). */
    public static String baseTableComment(RolapLevel targetLevel, String baseAlias) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        // Mock-built hierarchies may carry no dimension: fall back to the hierarchy name.
        org.eclipse.daanse.olap.api.element.Dimension dim = hierarchy.getDimension();
        String dimension = "dimension " + (dim != null ? dim.getUniqueName() : hierarchy.getUniqueName());
        if (baseAlias != null) {
            List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
            for (int i = Math.min(targetLevel.getDepth(), levels.size() - 1); i >= 0; i--) {
                RolapLevel level = levels.get(i);
                if (level.isAll()) {
                    continue;
                }
                if (level.getKeyExp() instanceof org.eclipse.daanse.rolap.element.RolapColumn col
                        && baseAlias.equals(col.getTable())) {
                    return dimension + " level table " + level.getUniqueName();
                }
            }
        }
        return dimension + " hierarchy table " + hierarchy.getUniqueName();
    }

    /**
     * The collapsed single-column aggStar LEVEL-MEMBERS SELECT — the tuple-reader counterpart of
     * {@link MemberSqlMapper#collapsedSingleColumnSql}, the
     * {@code addAggColumnToSql} branch: every projected level (root..target) has a single key
     * column present IN the aggregate table, so no dimension join is needed. Projects each level's
     * AGGREGATE column ({@code aggStar.lookupColumn(bitPos).toSqlExpression()}) from the single agg
     * table, root down to target, each added to GROUP BY (every collapsed column is grouped
     * unconditionally) and ordered ASC nulls-last (the standalone
     * {@code WhichSelect.ONLY} read this path is restricted to). {@code where} (the agg-substituted
     * context — {@link SqlContextConstraint#levelMembersAggWhere}) is split into per-conjunct
     * WHERE clauses (a nested {@code And} stays grouped).
     * <p>
     * {@code nativeHaving} (a native Filter measure condition) and {@code nativeOrder} (a native
     * TopCount/BottomCount measure order) are carried the SAME way {@link #buildLevelSelect} does — the
     * HAVING after the GROUP BY, the measure order projected and ORDERed BEFORE the level ordering — but
     * both already agg-substituted by the constraint's agg counterparts
     * ({@link SqlContextConstraint#levelMembersAggHaving} /
     * {@link SqlContextConstraint#levelMembersAggOrder}), since the aggStar read has no base-star
     * contribution to resolve them through. A plain (non-native) context constraint passes both empty and
     * the body is identical to the pure-projection shape.
     */
    public static SelectStatement collapsedSingleColumnSql(
            List<RolapCubeLevel> collapsedLevels,
            AggStar aggStar,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving) {
        return collapsedTupleLevelMembersSql(List.of(collapsedLevels), aggStar, where, nativeOrder,
                nativeHaving);
    }

    /**
     * The MULTI-TARGET generalization of {@link #collapsedSingleColumnSql}
     * (agg-only multi-target projection): every projected level of every target is COLLAPSED on
     * the aggregate table and needs no dimension join, so the whole read is a single-agg-table
     * SELECT. Two per-level branches ({@code addLevelMemberSql}):
     * <ul>
     * <li>single-column collapsed ({@code !levelContainsMultipleColumns}) — the
     *     {@code addAggColumnToSql} branch: the agg key column, SELECT + unconditional GROUP BY,
     *     ORDER BY key ASC;</li>
     * <li>multi-column collapsed with EVERY extra column substituted on the agg table
     *     ({@code !requiresJoinToDim}) — the {@code targetExp} branch: key/caption/ordinals/
     *     properties each projected via {@link AggJoinPlanner#levelTargetExpMap}, GROUP BY per the
     *     hierarchy's {@code isGroupByNeeded}, a non-key ordinal ORDERed BEFORE the key (e.g.
     *     {@code testmonthord} before {@code testmonthname}) with the key as
     *     tiebreaker — the {@code AggStar.Table.Level.getOrdinalExps()} channel.</li>
     * </ul>
     * A level outside those branches (not collapsed, or collapsed but requiring the dimension join)
     * is a caller bug here — it belongs to {@link #aggTupleLevelMembersSql} — and throws.
     * {@code where}/{@code nativeOrder}/{@code nativeHaving} are carried exactly like the
     * single-target form (already agg-substituted by the constraint's agg counterparts). The multi-target
     * path is currently unused by executed routes; the single-target delegation keeps the
     * {@code aggstar-collapsed-levelmembers} route unchanged.
     */
    public static SelectStatement collapsedTupleLevelMembersSql(
            List<List<RolapCubeLevel>> targetCollapsedLevels,
            AggStar aggStar,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving) {
        RolapCubeLevel firstLevel = targetCollapsedLevels.get(0).get(0);
        AggStar.Table.Column firstAgg =
                aggStar.lookupColumn(firstLevel.getStarKeyColumn().getBitPosition());
        SelectStatementBuilder q = SelectStatementBuilder.create();
        // FROM the single agg table (collapsed = every level's key column IS a column of the agg fact).
        q.from(org.eclipse.daanse.sql.statement.api.From.table(firstAgg.getTable().getName(),
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of(
                        org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.getTableAlias(
                                firstAgg.getExpression()))));
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        if (targetCollapsedLevels.size() > 1) {
            q.header("tuples " + String.join(" x ", targetCollapsedLevels.stream()
                    .map(t -> t.get(t.size() - 1).getUniqueName()).toList()));
            q.footerComment("tuple members (aggstar collapsed)");
        } else {
            List<RolapCubeLevel> only = targetCollapsedLevels.get(0);
            q.header("members " + only.get(only.size() - 1).getUniqueName());
            q.footerComment("level members (aggstar collapsed)");
        }
        // Each recorded context restriction is its own WHERE conjunct (a nested AND stays grouped) —
        // the same split MemberSqlMapper.collapsedSingleColumnSql applies.
        where.ifPresent(p -> {
            if (p instanceof org.eclipse.daanse.sql.statement.api.expression.Predicate.And and) {
                and.operands().forEach(op -> q.where(op, "context"));
            } else {
                q.where(p, "context");
            }
        });
        // Native Filter HAVING (a measure-comparison condition), emitted after the WHERE (it follows
        // GROUP BY in SQL). The predicate already carries its `((agg(measure) op value))` parenthesisation
        // AND is already agg-substituted (compiled through RolapNativeSql with this aggStar).
        nativeHaving.ifPresent(p -> q.having(p, "native filter"));
        // Project each target's collapsed levels root..target, in the loop order addLevelMemberSql
        // emits. Defer the level ORDER BY so a native measure order can be PREPENDED below.
        java.util.Set<String> orderedColumns = new java.util.LinkedHashSet<>();
        java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders = new java.util.ArrayList<>();
        for (List<RolapCubeLevel> target : targetCollapsedLevels) {
            // needsGroupBy is only consulted by the multi-column (targetExp) branch; computing it
            // lazily keeps the single-column route free of new hierarchy reads. The
            // null-tolerant pre-check (not the recorder predicate) decides the EMISSION branch
            // here because the caller has already routed with the real predicates — the
            // single-column branch performs no ordinal/caption/property reads, and the delegation
            // must not add any for single-column inputs.
            boolean anyMultiCol = target.stream()
                    .anyMatch(TupleSqlMapper::collapsedLevelMultipleColumns);
            boolean needsGroupBy = true;
            if (anyMultiCol) {
                RolapCubeLevel targetLevel = target.get(target.size() - 1);
                RolapHierarchy hierarchy = targetLevel.getHierarchy();
                needsGroupBy = RolapUtil.isGroupByNeeded(hierarchy,
                        (List<RolapLevel>) hierarchy.getLevels(), targetLevel.getDepth());
            }
            for (RolapCubeLevel level : target) {
                if (collapsedLevelMultipleColumns(level)) {
                    java.util.Map<SqlExpression, SqlExpression> targetExp =
                            AggJoinPlanner.levelTargetExpMap(level, aggStar);
                    if (AggJoinPlanner.requiresJoinToDim(targetExp)) {
                        throw new IllegalStateException(
                                "collapsed-tuple read requires agg-substituted levels (dim join needed): "
                                        + level.getUniqueName());
                    }
                    projectAggSubstitutedLevel(q, level, aggStar, needsGroupBy, orderedColumns,
                            levelOrders, true);
                } else {
                    projectAggCollapsedColumn(q, level, aggStar, orderedColumns, levelOrders);
                }
            }
        }
        // Native TopCount/Order measure: project it (trailing alias) and order by it FIRST, then the
        // deferred level ORDER BY — the emission order buildLevelSelect uses. The measure expression is
        // already agg-substituted (compiled through RolapNativeSql with this aggStar).
        nativeOrder.ifPresent(no -> {
            ProjectionRef measureRef = q.project(no.measureExpr(), null, null, "measure (native order)");
            // The native order measure is an aggregate the renderer's structural check cannot see; exempt
            // it from GROUP-BY completion (the recorder never groups the order expression either).
            q.excludeFromGroupByCompletion(measureRef);
            SortDirection dir = no.direction() == SortingDirection.DESC ? SortDirection.DESC : SortDirection.ASC;
            q.orderOn(measureRef, new SortSpec(dir, no.nullable(), NullOrder.LAST, false));
        });
        levelOrders.forEach(e -> q.orderOn(e.getKey(), e.getValue()));
        return q.build();
    }

    // ---- the agg tuple mapper ---------------------------------------------

    /**
     * The AGGREGATE-TABLE level/tuple-members SELECT — the
     * {@code addLevelMemberSql} loop with a non-null {@code aggStar} (agg-fact-join and
     * agg-dim-join), emitted in the reference query's REGISTRATION order,
     * because the assembler folds the FROM left-deep from that order and demotes
     * cycle edges to WHERE. Per target, per non-all level root→depth:
     * <ol>
     * <li>{@code isLevelCollapsed && !levelContainsMultipleColumns} (the SAME
     *     {@code SqlMemberSource} predicates, including the ordinal quirk: ANY
     *     non-empty {@code ordinalExps} ⇒ multiple columns) — the {@code addAggColumnToSql} branch:
     *     project the agg column only and register {@link AggJoinPlanner#aggTableChain};</li>
     * <li>else substitute {@link AggJoinPlanner#levelTargetExpMap} at every projection point
     *     ({@link #projectAggLevel}); a NON-collapsed level registers its base dimension subset per
     *     mapped expression ({@code hierarchy.addToFrom});</li>
     * <li>{@code factJoinRequired}: the per-level existence join — the agg arm of
     *     {@code joinLevelTableToFactTable}: the chain of
     *     {@code aggStar.lookupColumn(starKey.getBitPosition()).getTable()};</li>
     * <li>({@code collapsed && requiresJoinToDim}): the INVERSE dimension subset
     *     ({@link RelationFromMapper#fromInverse} semantics), then the agg chain, then the
     *     {@link AggJoinPlanner#dimToAggEdge} edge;</li>
     * <li>after all targets: {@code havingJoins} — the native-HAVING compile FROM side
     *     effects ({@link HavingJoin}), replayed in tape position (the filter compile runs in the
     *     constraint fork after the level loop and BEFORE the inherited context ops);</li>
     * <li>then {@code orderedAggPredicates} in order — each predicate's agg-table
     *     chain registered on first appearance, then its WHERE conjunct (the {@code [join, wheres]}
     *     interleaving of the context constraint).</li>
     * </ol>
     * The FROM is PRE-FOLDED here with the assembler's exact algorithm ({@link #foldAggFrom}):
     * items in registration order, each pending item joined by its FIRST recorded edge into the
     * already-placed set (multi-pass, alias-tracked like {@code addChainAliasAware}), unused edges
     * appended as WHERE conjuncts AFTER the context conjuncts (dedup by predicate value), leftovers
     * comma-joined. {@link AggJoinPlanner.AggJoinEdge} is the agg-chain transport into the fold —
     * pre-folding was chosen over threading a parallel edge list through
     * {@code buildLevelSelect} because ON-vs-WHERE placement needs the GLOBAL registration order
     * (dim leaves, agg chains and cycle edges interleave), which a per-list join emission alongside
     * {@code JoinStep} cannot express; {@code buildLevelSelect} receives the finished tree.
     * <p>
     * {@code nativeHaving}/{@code nativeOrder} are carried exactly like {@code buildLevelSelect}
     * does (every reader path carries both); {@code emitOrderBy=false} is the
     * union arm. Currently unused by executed routes.
     */
    public static SelectStatement aggTupleLevelMembersSql(List<RolapLevel> targetLevels,
            AggStar aggStar,
            boolean viewAware,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate> orderedAggPredicates,
            java.util.List<HavingJoin> havingJoins,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            boolean factJoinRequired,
            org.eclipse.daanse.rolap.element.RolapCube baseCube,
            boolean emitOrderBy) {
        java.util.LinkedHashMap<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items =
                new java.util.LinkedHashMap<>();
        java.util.List<FoldEdge> edges = new java.util.ArrayList<>();
        for (RolapLevel target : targetLevels) {
            RolapHierarchy hierarchy = target.getHierarchy();
            List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
            for (int i = 0; i <= target.getDepth(); i++) {
                RolapLevel lvl = levels.get(i);
                if (lvl.isAll()) {
                    continue;
                }
                RolapCubeLevel cubeLevel = (RolapCubeLevel) lvl;
                boolean collapsed = org.eclipse.daanse.rolap.common.member.SqlMemberSource
                        .isLevelCollapsed(aggStar, cubeLevel);
                boolean multipleCols = org.eclipse.daanse.rolap.common.member.SqlMemberSource
                        .levelContainsMultipleColumns(lvl);
                if (collapsed && !multipleCols) {
                    // addAggColumnToSql: agg column projection only (see projectAggLevel) + the agg
                    // chain FROM. The recorder CONTINUEs here — no per-level constraint join.
                    registerAggChain(items, edges, aggStar
                            .lookupColumn(cubeLevel.getStarKeyColumn().getBitPosition()).getTable());
                    continue;
                }
                java.util.Map<SqlExpression, SqlExpression> targetExp =
                        AggJoinPlanner.levelTargetExpMap(lvl, aggStar);
                SqlExpression keyExp = targetExp.get(lvl.getKeyExp());
                // The recorder's FROM-side ordinal list: filtered by targetExp membership, mapped.
                List<SqlExpression> ordinalExps = lvl.getOrdinalExps().stream()
                        .filter(targetExp::containsKey).map(targetExp::get).toList();
                SqlExpression captionExp = targetExp.get(lvl.getCaptionExp());
                SqlExpression parentExp = lvl.getParentExp();
                if (parentExp != null && !collapsed) {
                    registerLevelRelation(items, edges, hierarchy, parentExp);
                }
                if (!collapsed) {
                    registerLevelRelation(items, edges, hierarchy, keyExp);
                    for (SqlExpression oe : ordinalExps) {
                        registerLevelRelation(items, edges, hierarchy, oe);
                    }
                    if (captionExp != null) {
                        registerLevelRelation(items, edges, hierarchy, captionExp);
                    }
                }
                // Per-level constraint ops (addLevelConstraintOps): the existence join to the agg
                // fact — joinLevelTableToFactTable's agg arm, registered BETWEEN the level's own
                // FROM adds and the inverse arm.
                if (factJoinRequired) {
                    RolapStar.Column starKey = cubeLevel.getBaseStarKeyColumn(baseCube);
                    if (starKey != null) {
                        registerAggChain(items, edges,
                                aggStar.lookupColumn(starKey.getBitPosition()).getTable());
                    }
                }
                if (collapsed && AggJoinPlanner.requiresJoinToDim(targetExp)) {
                    // inverse dimension subset first (leaf-ward), then the agg chain, then the
                    // dimKey = aggColumn edge — the addToFromInverse/addToFrom/
                    // addJoinCondition order.
                    registerInverseRelation(items, edges, hierarchy, lvl.getKeyExp());
                    AggStar.Table.Column aggColumn =
                            aggStar.lookupColumn(cubeLevel.getStarKeyColumn().getBitPosition());
                    registerAggChain(items, edges, aggColumn.getTable());
                    edges.add(new FoldEdge(
                            org.eclipse.daanse.rolap.common.util.SqlExpressionResolver
                                    .getTableAlias(lvl.getKeyExp()),
                            org.eclipse.daanse.rolap.common.util.SqlExpressionResolver
                                    .getTableAlias(aggColumn.getExpression()),
                            AggJoinPlanner.dimToAggEdge(cubeLevel, aggStar)));
                } else if (collapsed) {
                    registerAggChain(items, edges, aggStar
                            .lookupColumn(cubeLevel.getStarKeyColumn().getBitPosition()).getTable());
                }
            }
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("agg tuple read produced no FROM items: " + targetLevels);
        }
        // The native-HAVING compile FROM side effects (FilterConstraint.addConstraintOps
        // -> RolapNativeSql MatchingSqlCompiler -> ctx.addToFrom), replayed in tape position: the
        // filter compile runs in the constraint fork AFTER the level loop and BEFORE the inherited
        // context ops, so its registrations land here — after all targets, before the context
        // predicate chains. Each is the hierarchy.addToFrom form (relationSubset — the WHOLE join
        // kept under FilterChildlessSnowflakeMembers), which is how the FROM gains the
        // filter-referenced table's full snowflake subtree (e.g.
        // cat→product_cat→product_csv). Registering an already-present subset is a no-op
        // (alias dedup + edge value dedup in the fold).
        for (HavingJoin hj : havingJoins) {
            registerLevelRelation(items, edges, hj.hierarchy(), hj.expression());
        }
        // Context constraint (after ALL targets — addConstraintOps order): per column, the table's
        // agg chain on first appearance, then its WHERE conjunct.
        java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> conjuncts =
                new java.util.ArrayList<>();
        if (!orderedAggPredicates.isEmpty()) {
            for (org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate cp : orderedAggPredicates) {
                if (cp.table() != null) {
                    registerAggChain(items, edges, cp.table());
                }
                conjuncts.add(cp.predicate());
            }
        } else {
            where.ifPresent(p -> {
                if (p instanceof org.eclipse.daanse.sql.statement.api.expression.Predicate.And and) {
                    conjuncts.addAll(and.operands());
                } else {
                    conjuncts.add(p);
                }
            });
        }
        // Assembler fold: builds the left-deep FROM tree and APPENDS the unused (cycle) edges
        // to the conjunct list — after the context conjuncts, deduped by predicate value.
        org.eclipse.daanse.sql.statement.api.model.FromClause from = foldAggFrom(items, edges, conjuncts);
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where2 =
                conjuncts.isEmpty() ? java.util.Optional.empty()
                        : java.util.Optional.of(org.eclipse.daanse.sql.statement.api.Predicates.and(conjuncts));
        return buildLevelSelect(targetLevels, from, viewAware, java.util.List.of(), where2, nativeOrder,
                nativeHaving, emitOrderBy, true, aggStar);
    }

    /**
     * One recorded join EDGE of the agg FROM graph — the mapper-side form of
     * {@code QueryTape.JoinEdge}: both endpoint aliases (undirected; schema-traversal order) plus
     * the structured ON predicate. The fold consumes these exactly like the assembler consumes the
     * tape's edges.
     */
    private record FoldEdge(String leftAlias, String rightAlias,
            org.eclipse.daanse.sql.statement.api.expression.Predicate on) {
    }

    /**
     * One FROM registration the native-HAVING compile performs as a side effect
     * ({@code RolapNativeSql}'s Caption/Name MATCHES compiler under an aggStar calls
     * {@code ctx.addToFrom(hierarchy, expression)} whenever the filter source does NOT resolve to
     * an agg column — the referenced dimension table must be joinable): the hierarchy whose
     * relation subset ({@code hierarchy.addToFrom} semantics —
     * {@link RelationFromMapper#relationSubset}, whole join kept under
     * {@code FilterChildlessSnowflakeMembers}) joins the FROM. Collected by
     * {@code NativeSqlContext.scratchCollecting} during the constraint's scratch compile
     * ({@code SqlContextConstraint.levelMembersAggHavingWithJoins}) and replayed by
     * {@link #aggTupleLevelMembersSql} in tape position.
     */
    public record HavingJoin(RolapHierarchy hierarchy, SqlExpression expression) {
    }

    /**
     * The {@code AggStar.Table.addToFrom(query, failIfExists=false, joinToParent=true)} form:
     * FROM items SELF-FIRST up the chain (alias-deduped — {@code putIfAbsent} is the
     * {@code addFromTable} dedup), join CONDITIONS replayed in REVERSE (parent-first — the
     * recursion unwinds; see {@link AggJoinPlanner#aggTableChain}). Conditions are recorded on
     * EVERY call, exactly like the unconditional {@code addJoinCondition} — the fold's
     * value dedup absorbs the duplicates.
     */
    private static void registerAggChain(
            java.util.Map<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<FoldEdge> edges, AggStar.Table table) {
        List<AggJoinPlanner.AggJoinEdge> chain = AggJoinPlanner.aggTableChain(table);
        for (AggJoinPlanner.AggJoinEdge e : chain) {
            items.putIfAbsent(e.fromAlias(), e.from());
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            AggJoinPlanner.AggJoinEdge e = chain.get(i);
            if (e.on() != null) {
                edges.add(new FoldEdge(e.fromAlias(), chain.get(i + 1).fromAlias(), e.on()));
            }
        }
    }

    /**
     * The {@code RolapHierarchy.addToFrom(query, expression)} form: the smallest relation subset
     * reaching the expression's table ({@link RelationFromMapper#relationSubset}, whole relation
     * when unresolved), registered leaf-per-leaf via {@link #walkRelation}.
     */
    private static void registerLevelRelation(
            java.util.Map<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<FoldEdge> edges, RolapHierarchy hierarchy, SqlExpression exp) {
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation =
                hierarchy.getRelation();
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource sub = relation;
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource
                && exp != null) {
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource subset =
                    RelationFromMapper.relationSubset(relation,
                            org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.getTableAlias(exp));
            sub = subset != null ? subset : relation;
        }
        walkRelation(items, edges, sub);
    }

    /**
     * The {@code RolapHierarchy.addToFromInverse(query, expression)} form: the INVERSE
     * subset ({@link RelationFromMapper#relationSubsetInverse} — leaf-ward subtree kept, root-ward
     * ancestors dropped, no childless-filter consultation), registered leaf-per-leaf. An
     * unresolved subset is a caller bug, exactly as {@code addFrom(null)} would be.
     */
    private static void registerInverseRelation(
            java.util.Map<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<FoldEdge> edges, RolapHierarchy hierarchy, SqlExpression exp) {
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation =
                hierarchy.getRelation();
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource sub = relation;
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource
                && exp != null) {
            sub = RelationFromMapper.relationSubsetInverse(relation,
                    org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.getTableAlias(exp));
        }
        if (sub == null) {
            throw new IllegalStateException("inverse relation subset unresolved for " + exp);
        }
        walkRelation(items, edges, sub);
    }

    /**
     * The {@code QueryRecorder.addFrom(relation, alias, failIfExists=false)} form: leaves register
     * left-to-right (alias-deduped), a join records its edge only when a side was NEWLY added (the
     * {@code addJoin} rule), post-order per join node. Returns whether anything was
     * added.
     */
    private static boolean walkRelation(
            java.util.Map<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<FoldEdge> edges,
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            boolean addedLeft = walkRelation(items, edges, join.getLeft().getSource());
            boolean addedRight = walkRelation(items, edges, join.getRight().getSource());
            if (addedLeft || addedRight) {
                edges.add(new FoldEdge(
                        org.eclipse.daanse.rolap.common.util.JoinUtil.getLeftAlias(join),
                        org.eclipse.daanse.rolap.common.util.JoinUtil.getRightAlias(join),
                        RelationFromMapper.joinOn(join)));
            }
            return addedLeft || addedRight;
        }
        String alias = org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias(relation);
        if (!items.containsKey(alias)) {
            items.put(alias, RelationFromMapper.fromReferenced(relation, java.util.Set.of(alias)));
            return true;
        }
        return false;
    }

    /**
     * The {@code ContributionAssembler.buildFromClause} form: folds the registered items into a
     * LEFT-DEEP join tree in insertion order — each pending item joined by the FIRST recorded edge
     * connecting it to an already-placed alias, multi-pass until no progress — then appends every
     * edge NOT used as a tree ON to {@code outConjuncts} (cycle edges → WHERE, dedup by predicate
     * value), and comma-joins any disconnected leftovers. A single item (or an edge-less
     * registration) short-circuits exactly like the assembler.
     */
    private static org.eclipse.daanse.sql.statement.api.model.FromClause foldAggFrom(
            java.util.LinkedHashMap<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<FoldEdge> edges,
            java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> outConjuncts) {
        java.util.List<String> aliases = List.copyOf(items.keySet());
        java.util.List<org.eclipse.daanse.sql.statement.api.model.FromClause> itemList =
                List.copyOf(items.values());
        if (itemList.size() == 1 || edges.isEmpty()) {
            return itemList.size() == 1 ? itemList.get(0)
                    : new org.eclipse.daanse.sql.statement.api.model.FromClause.FromProduct(itemList);
        }
        java.util.Map<String, java.util.List<FoldEdge>> byAlias = new java.util.HashMap<>();
        for (FoldEdge e : edges) {
            byAlias.computeIfAbsent(e.leftAlias(), k -> new java.util.ArrayList<>()).add(e);
            byAlias.computeIfAbsent(e.rightAlias(), k -> new java.util.ArrayList<>()).add(e);
        }
        org.eclipse.daanse.sql.statement.api.model.FromClause acc = itemList.get(0);
        java.util.Set<String> placed = new java.util.HashSet<>();
        placed.add(aliases.get(0));
        java.util.Set<org.eclipse.daanse.sql.statement.api.expression.Predicate> treeOns =
                new java.util.HashSet<>();
        java.util.List<Integer> pending = new java.util.ArrayList<>();
        for (int i = 1; i < itemList.size(); i++) {
            pending.add(i);
        }
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            java.util.List<Integer> stillPending = new java.util.ArrayList<>();
            for (Integer idx : pending) {
                String a = aliases.get(idx);
                org.eclipse.daanse.sql.statement.api.expression.Predicate on = null;
                for (FoldEdge e : byAlias.getOrDefault(a, List.of())) {
                    String other = e.leftAlias().equals(a) ? e.rightAlias() : e.leftAlias();
                    if (placed.contains(other)) {
                        on = e.on();
                        break;
                    }
                }
                if (on != null) {
                    acc = new org.eclipse.daanse.sql.statement.api.model.FromClause.FromJoin(
                            acc, org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER,
                            itemList.get(idx), on);
                    placed.add(a);
                    treeOns.add(on);
                    progress = true;
                } else {
                    stillPending.add(idx);
                }
            }
            pending = stillPending;
        }
        for (FoldEdge e : edges) {
            if (treeOns.add(e.on())) {
                outConjuncts.add(e.on());
            }
        }
        if (pending.isEmpty()) {
            return acc;
        }
        java.util.List<org.eclipse.daanse.sql.statement.api.model.FromClause> product =
                new java.util.ArrayList<>();
        product.add(acc);
        for (Integer idx : pending) {
            product.add(itemList.get(idx));
        }
        return new org.eclipse.daanse.sql.statement.api.model.FromClause.FromProduct(product);
    }

    /**
     * The AGG counterpart of {@link #projectTargetLevels} — one target's projection in the
     * {@code addLevelMemberSql} agg branches, every non-all level root→depth through
     * {@link #projectAggLevel}. GROUP BY need is this hierarchy's own {@code isGroupByNeeded}; the
     * ORDER BY dedup set spans all targets; {@code projectParent=false} is the union arm (level
     * orders deferred and dropped by the caller).
     */
    private static void projectAggTargetLevels(SelectStatementBuilder q, RolapLevel targetLevel,
            AggStar aggStar,
            java.util.Set<String> orderedColumns,
            java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders,
            boolean projectParent) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        int levelDepth = targetLevel.getDepth();
        boolean needsGroupBy = RolapUtil.isGroupByNeeded(hierarchy, levels, levelDepth);
        for (int i = 0; i <= levelDepth; i++) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            projectAggLevel(q, level, aggStar, needsGroupBy, orderedColumns, levelOrders, projectParent);
        }
    }

    /**
     * ONE level's agg projection — branch parity with the {@code addLevelMemberSql}
     * body (calls the SAME
     * {@code isLevelCollapsed}/{@code levelContainsMultipleColumns} predicates):
     * <ul>
     * <li>collapsed single-column — the {@code addAggColumnToSql} branch: the agg column
     *     ({@code toSqlExpression}, usage-prefix aware) with the star column's internal type,
     *     SELECT + UNCONDITIONAL GROUP BY ({@code addSelectGroupBy}), key ORDER BY deferred;</li>
     * <li>else the {@code targetExp} branch: key/caption projected from
     *     {@link AggJoinPlanner#levelTargetExpMap} (an identity entry projects the DIMENSION
     *     column — the {@code aggLevel == null} "no extra columns" drop rule leaves
     *     caption/ordinals/properties on the dimension), ordinals iterated on the LEVEL list with
     *     the recorder's key-equality check and mapped per ordinal (a non-key ordinal ORDERs
     *     before the key tiebreaker), properties per the recorder's RolapColumn-or-raw rule,
     *     GROUP BY gated on {@code needsGroupBy}.</li>
     * </ul>
     */
    private static void projectAggLevel(SelectStatementBuilder q, RolapLevel level, AggStar aggStar,
            boolean needsGroupBy,
            java.util.Set<String> orderedColumns,
            java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders,
            boolean projectParent) {
        RolapCubeLevel cubeLevel = (RolapCubeLevel) level;
        if (org.eclipse.daanse.rolap.common.member.SqlMemberSource.isLevelCollapsed(aggStar, cubeLevel)
                && !org.eclipse.daanse.rolap.common.member.SqlMemberSource
                        .levelContainsMultipleColumns(level)) {
            projectAggCollapsedColumn(q, cubeLevel, aggStar, orderedColumns, levelOrders);
            return;
        }
        projectAggSubstitutedLevel(q, level, aggStar, needsGroupBy, orderedColumns, levelOrders,
                projectParent);
    }

    /**
     * Null-tolerant emission-branch check for {@link #collapsedTupleLevelMembersSql} ONLY: a level
     * exposing NO ordinal/caption/property columns takes the single-column branch without any
     * further reads (the single-column branch reads nothing but the key/agg column, and the
     * delegation must stay behavior-preserving); a level with extra columns defers to the recorder
     * predicate for quirk parity (ANY non-empty ordinal list ⇒ multiple columns). The
     * recorder-loop counterpart {@link #projectAggLevel} always calls the recorder predicates directly.
     */
    private static boolean collapsedLevelMultipleColumns(RolapCubeLevel level) {
        List<? extends SqlExpression> ordinals = level.getOrdinalExps();
        SqlExpression captionExp = level.getCaptionExp();
        RolapProperty[] properties = level.getProperties();
        boolean anyExtra = (ordinals != null && !ordinals.isEmpty())
                || captionExp != null
                || (properties != null && properties.length > 0);
        return anyExtra
                && org.eclipse.daanse.rolap.common.member.SqlMemberSource.levelContainsMultipleColumns(level);
    }

    /**
     * The {@code addAggColumnToSql} branch: SELECT + GROUP BY the agg column (unconditionally —
     * {@code addSelectGroupBy}), ORDER BY key ASC (deferred; dropped on a union arm).
     */
    private static void projectAggCollapsedColumn(SelectStatementBuilder q, RolapCubeLevel level,
            AggStar aggStar,
            java.util.Set<String> orderedColumns,
            java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders) {
        RolapStar.Column starColumn = level.getStarKeyColumn();
        AggStar.Table.Column aggColumn = aggStar.lookupColumn(starColumn.getBitPosition());
        ProjectionRef ref = q.project(aggColumn.toSqlExpression(), starColumn.getInternalType(),
                null, "level key " + level.getUniqueName());
        q.groupOn(ref);
        orderOnce(levelOrders, orderedColumns, ref, aggColumn.getExpression(),
                sortSpec(SortingDirection.ASC));
    }

    /**
     * The {@code targetExp} branch of the recorder loop: every projection point substituted via
     * {@link AggJoinPlanner#levelTargetExpMap} (see {@link #projectAggLevel}'s javadoc for the
     * branch rules).
     */
    private static void projectAggSubstitutedLevel(SelectStatementBuilder q, RolapLevel level,
            AggStar aggStar, boolean needsGroupBy,
            java.util.Set<String> orderedColumns,
            java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders,
            boolean projectParent) {
        java.util.Map<SqlExpression, SqlExpression> targetExp =
                AggJoinPlanner.levelTargetExpMap(level, aggStar);
        SqlExpression keyExp = targetExp.get(level.getKeyExp());
        SqlExpression captionExp = targetExp.get(level.getCaptionExp());
        SqlExpression parentExp = level.getParentExp();
        if (parentExp != null && projectParent) {
            // Parent-child level (recorder: projected for LAST/ONLY regardless of collapsed —
            // only the FROM registration is collapse-gated). Raw parent expression, never mapped.
            ProjectionRef parentRef = q.project(JoinPlanner.expressionFor(parentExp),
                    level.getInternalType(), null, "parent key " + level.getUniqueName());
            if (needsGroupBy) {
                q.groupOn(parentRef);
            }
            SortSpec parentSort = sortSpecNullsFirst(SortingDirection.ASC);
            String npv = level.getNullParentValue();
            if (npv != null && !"null".equalsIgnoreCase(npv)) {
                parentSort = parentSort.withNullSortValue(npv, level.getDatatype());
            }
            levelOrders.add(java.util.Map.entry(parentRef, parentSort));
        }
        ProjectionRef keyRef = q.project(JoinPlanner.expressionFor(keyExp), level.getInternalType(),
                null, "level key " + level.getUniqueName());
        if (needsGroupBy) {
            q.groupOn(keyRef);
        }
        if (captionExp != null) {
            ProjectionRef captionRef = q.project(JoinPlanner.expressionFor(captionExp), null, null,
                    "level caption " + level.getUniqueName());
            if (needsGroupBy) {
                q.groupOn(captionRef);
            }
        }
        List<? extends SqlExpression> ordinals = level.getOrdinalExps();
        if (ordinals != null && !ordinals.isEmpty()) {
            for (SqlExpression ordinalExp : ordinals) {
                // The recorder compares the LEVEL expressions (keyExp.equals(ordinalExp)) and
                // projects/orders the MAPPED ones.
                if (level.getKeyExp().equals(ordinalExp)) {
                    orderOnce(levelOrders, orderedColumns, keyRef, keyExp,
                            sortSpec(ordinalExp.getSortingDirection()));
                } else {
                    SqlExpression oe = targetExp.get(ordinalExp);
                    ProjectionRef ref = q.project(JoinPlanner.expressionFor(oe), null, null,
                            "level ordinal " + level.getUniqueName());
                    if (needsGroupBy) {
                        q.groupOn(ref);
                    }
                    orderOnce(levelOrders, orderedColumns, ref, oe,
                            sortSpec(ordinalExp.getSortingDirection()));
                    // the level key is a tiebreaker after a non-key ordinal.
                    orderOnce(levelOrders, orderedColumns, keyRef, keyExp, sortSpec(SortingDirection.ASC));
                }
            }
        } else {
            orderOnce(levelOrders, orderedColumns, keyRef, keyExp, sortSpec(SortingDirection.ASC));
        }
        for (RolapProperty property : level.getProperties()) {
            SqlExpression propExp = targetExp.get(property.getExp());
            // The recorder's rule: a mapped plain column projects (with the agg/level table alias);
            // anything else falls back to the raw property expression.
            SqlExpression toProject = (propExp instanceof org.eclipse.daanse.rolap.element.RolapColumn)
                    ? propExp : property.getExp();
            ProjectionRef ref = q.project(JoinPlanner.expressionFor(toProject), null, null,
                    "member property " + property.getName());
            if (!property.dependsOnLevelValue()) {
                q.groupOn(ref);
            }
        }
        if (needsGroupBy) {
            q.completeNonAggregatesGroupBy();
        }
    }

    /**
     * The FROM for a level query: the smallest relation subset reaching each
     * selected level's columns (childless-snowflake semantics). A top-level view/inline relation
     * (which has no joinable subset) is rendered via {@code from} when {@code dialect} is
     * non-null. Falls back to the whole relation if none resolve.
     */
    // Package-visible: reused by MemberSqlMapper.childMemberSql so member-children FROM uses the same
    // proven per-level snowflake subset (honoring FilterChildlessSnowflakeMembers + the computed-column
    // whole-relation fallback) instead of the whole relation.
    static org.eclipse.daanse.sql.statement.api.model.FromClause fromForLevels(
            RolapLevel targetLevel, boolean viewAware) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        if (viewAware && !RelationFromMapper.supports(hierarchy.getRelation())) {
            // view / inline-table relation — no snowflake subset applies; render it whole.
            return RelationFromMapper.from(hierarchy.getRelation());
        }
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        int levelDepth = targetLevel.getDepth();
        java.util.Set<String> levelTables = new java.util.LinkedHashSet<>();
        boolean hasExpression = false;
        for (int i = 0; i <= levelDepth; i++) {
            RolapLevel lvl = levels.get(i);
            if (lvl.isAll()) {
                continue;
            }
            for (SqlExpression e : levelExpressions(lvl)) {
                hasExpression |= !isPlainColumn(e);
                addAlias(levelTables, e);
            }
        }
        if (hasExpression) {
            // A computed column has no resolvable table alias, so no snowflake subset can be derived;
            // use the whole relation (the guard requires the FROM to match the reference exactly).
            return RelationFromMapper.from(hierarchy.getRelation());
        }
        java.util.Set<String> fromTables = RelationFromMapper.memberFromTables(hierarchy.getRelation(), levelTables);
        if (viewAware && !RelationFromMapper.supports(hierarchy.getRelation())) {
            // view/inline participates: build the dialect-aware subset (renders view leaves via raw),
            // falling back to the whole relation if the subset doesn't resolve.
            org.eclipse.daanse.sql.statement.api.model.FromClause sub =
                    RelationFromMapper.fromReferenced(hierarchy.getRelation(), fromTables);
            return sub != null ? sub : RelationFromMapper.from(hierarchy.getRelation());
        }
        org.eclipse.daanse.sql.statement.api.model.FromClause fromClause =
                RelationFromMapper.fromReferenced(hierarchy.getRelation(), fromTables);
        return fromClause != null ? fromClause : RelationFromMapper.from(hierarchy.getRelation());
    }

    /**
     * Builds the level-members projection/group/order over a pre-resolved {@code from} clause.
     * {@code dialect} (nullable) selects how computed columns render: non-null uses each expression's
     * dialect-specific SQL, null uses the generic fragment.
     */
    private static SelectStatement buildLevelSelect(RolapLevel targetLevel,
            org.eclipse.daanse.sql.statement.api.model.FromClause from,
            boolean viewAware,
            java.util.List<JoinPlanner.JoinStep> joinSteps,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where) {
        return buildLevelSelect(targetLevel, from, viewAware, joinSteps, where, java.util.Optional.empty(),
                java.util.Optional.empty());
    }

    private static SelectStatement buildLevelSelect(RolapLevel targetLevel,
            org.eclipse.daanse.sql.statement.api.model.FromClause from,
            boolean viewAware,
            java.util.List<JoinPlanner.JoinStep> joinSteps,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving) {
        return buildLevelSelect(List.of(targetLevel), from, viewAware, joinSteps, where, nativeOrder,
                nativeHaving, true, true);
    }

    /**
     * The multi-target core of {@link #buildLevelSelect(RolapLevel,
     * org.eclipse.daanse.sql.statement.api.model.FromClause,
     * boolean, java.util.List, java.util.Optional,
     * java.util.Optional, java.util.Optional)}: each target's non-all levels (root down to the
     * target depth) are projected/grouped/ordered in target order — the emission order of the
     * recorder's per-target {@code addLevelMemberSql} calls. GROUP BY need is decided PER TARGET
     * (each hierarchy's own {@code isGroupByNeeded}), the ORDER BY dedup set spans all targets.
     * {@code emitLevelOrderBy=false} reproduces a union arm ({@code whichSelect != ONLY} in
     * {@code SqlTupleReader.addLevelMemberSql}): the LEVEL ordering (ordinal/key/parent order-bys,
     * and the parent-key PROJECTION — all gated on {@code whichSelect==ONLY/LAST} in the recorder)
     * is dropped; the union wrapper orders by ordinals instead. {@code emitNativeOrder}
     * splits that out: a native TopCount/Order measure comes from {@code addConstraintOps}, which
     * runs WITHOUT arm knowledge — the measure is projected and ORDERed BY on EVERY
     * arm (illegal-but-emitted SQL the renderer reproduces verbatim inside a set-operation input;
     * fidelity over cleanliness) — so a native-order arm is {@code (false, true)}: measure
     * projection + measure ORDER BY, level orders dropped.
     */
    private static SelectStatement buildLevelSelect(List<RolapLevel> targetLevels,
            org.eclipse.daanse.sql.statement.api.model.FromClause from,
            boolean viewAware,
            java.util.List<JoinPlanner.JoinStep> joinSteps,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            boolean emitLevelOrderBy, boolean emitNativeOrder) {
        return buildLevelSelect(targetLevels, from, viewAware, joinSteps, where, nativeOrder,
                nativeHaving, emitLevelOrderBy, emitNativeOrder, null);
    }

    /**
     * As the 9-arg core, plus the AGG substitution channel: a non-null {@code aggStar} routes each
     * target's projection through {@link #projectAggTargetLevels} (the
     * {@code addLevelMemberSql} agg branches — collapsed agg columns and
     * {@code levelTargetExpMap}-substituted expressions) instead of the plain
     * {@link #projectTargetLevels}. {@code null} uses the non-agg path.
     */
    private static SelectStatement buildLevelSelect(List<RolapLevel> targetLevels,
            org.eclipse.daanse.sql.statement.api.model.FromClause from,
            boolean viewAware,
            java.util.List<JoinPlanner.JoinStep> joinSteps,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            boolean emitLevelOrderBy, boolean emitNativeOrder,
            AggStar aggStar) {
        RolapLevel firstTarget = targetLevels.get(0);

        SelectStatementBuilder q = SelectStatementBuilder.create();
        // Table-level schema SQL filters are LIFTED out of the FromTable slots into leading
        // explicit WHERE conjuncts, in FROM-entry order (root relation left-deep, then the join
        // steps below): each filter is appended as its table enters the FROM — BEFORE the
        // constraint conjuncts — while the renderer emits slot filters AFTER them. Lifting clears
        // the slot, so nothing is emitted twice; a filter-free read renders the same as the slot form.
        java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> tableFilters =
                new java.util.ArrayList<>();
        // Base-FROM provenance: name the dimension + the deepest projected level whose relation table
        // anchors the FROM (rendered only when comments are on; never part of the executed SQL).
        q.from(org.eclipse.daanse.sql.statement.api.From.commentBase(
                RelationFromMapper.liftTableFilters(from, tableFilters),
                baseTableComment(firstTarget,
                        org.eclipse.daanse.sql.statement.api.From.baseAlias(from))));
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        boolean constrained = where.isPresent() || !joinSteps.isEmpty()
                || nativeOrder.isPresent() || nativeHaving.isPresent();
        if (targetLevels.size() > 1) {
            q.header("tuples " + String.join(" x ",
                    targetLevels.stream().map(RolapLevel::getUniqueName).toList()));
            q.footerComment(constrained ? "tuple members (constrained)" : "tuple members (unconstrained)");
        } else {
            q.header("members " + firstTarget.getUniqueName());
            q.footerComment((constrained ? "level members (constrained)" : "level members (unconstrained)")
                    + (emitLevelOrderBy ? "" : ", union arm (order by on the wrapper)"));
        }
        // Context constraint: the star join steps as ANSI JOIN…ON (the join predicate goes
        // into the FROM tree, not WHERE), then each context restriction as its own WHERE conjunct
        // (a nested AND — e.g. a tuple key — stays one grouped conjunct).
        for (JoinPlanner.JoinStep s : joinSteps) {
            q.join(org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER,
                    RelationFromMapper.liftTableFilters(
                            JoinPlanner.tableFromClause(s.table()), tableFilters),
                    s.on(), "fact join (context)");
        }
        // The lifted table filters LEAD the WHERE (recorder FROM-entry order — see the lift above).
        tableFilters.forEach(f -> q.where(f, "table filter"));
        where.ifPresent(p -> {
            if (p instanceof org.eclipse.daanse.sql.statement.api.expression.Predicate.And and) {
                and.operands().forEach(op -> q.where(op, "context"));
            } else {
                q.where(p, "context");
            }
        });
        // Native Filter HAVING (a measure-comparison condition), emitted after the WHERE (it follows GROUP BY
        // in SQL). The predicate already carries its `((agg(measure) op value))` parenthesisation.
        nativeHaving.ifPresent(p -> q.having(p, "native filter"));

        // ORDER BY is deduped by the rendered column, so a level whose ordinal is its own key
        // (and the key tiebreaker that follows a non-key ordinal) is not emitted twice. The dedup
        // set spans all targets (the recorder's order-by dedup is per query, not per target).
        java.util.Set<String> orderedColumns = new java.util.LinkedHashSet<>();
        // Defer the level ORDER BY entries so a native TopCount/Order measure order can be PREPENDED below
        // (the measure ORDER BY must precede the level ordering).
        java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders = new java.util.ArrayList<>();
        for (RolapLevel targetLevel : targetLevels) {
            if (aggStar != null) {
                projectAggTargetLevels(q, targetLevel, aggStar, orderedColumns, levelOrders,
                        emitLevelOrderBy);
            } else {
                projectTargetLevels(q, targetLevel, viewAware, orderedColumns, levelOrders, emitLevelOrderBy);
            }
        }
        // Native TopCount/Order: project the measure (after the level columns, so it gets the trailing
        // alias) and order by it FIRST, then apply the deferred level ORDER BY — the emission order of
        // TopCountConstraint.addConstraint (project the measure, order by it, before the level order).
        // Emitted on a union arm too ((false, true) — see the method javadoc): addConstraintOps runs
        // without arm knowledge, so the recorder's arm carries the measure projection + ORDER BY.
        if (emitNativeOrder) {
            nativeOrder.ifPresent(no -> {
                ProjectionRef measureRef = q.project(no.measureExpr(), null, null, "measure (native order)");
                // The native order measure is an aggregate (often arithmetic-wrapped, e.g.
                // (sum(a)-sum(b))/sum(b)) the renderer's structural aggregate check cannot see, so on a
                // restrictive dialect the GROUP-BY completion would wrongly group it. Exempt it — the
                // recorder never groups the order expression either.
                q.excludeFromGroupByCompletion(measureRef);
                SortDirection dir = no.direction() == SortingDirection.DESC ? SortDirection.DESC : SortDirection.ASC;
                // The measure ORDER BY collates nulls last: DESC renders plain `c DESC`
                // (MySQL DESC is naturally nulls-last), ASC adds an ISNULL term.
                q.orderOn(measureRef, new SortSpec(dir, no.nullable(), NullOrder.LAST, false));
            });
        }
        // A union arm carries no LEVEL ORDER BY: the deferred entries are simply dropped.
        if (emitLevelOrderBy) {
            levelOrders.forEach(e -> q.orderOn(e.getKey(), e.getValue()));
        }
        return q.build();
    }

    /**
     * One target's projection/GROUP BY/deferred-ORDER BY emission — one
     * {@code addLevelMemberSql} call: every non-all level from the hierarchy root down to
     * {@code targetLevel} (parent key for a parent-child level, key, caption, ordinals with the key
     * tiebreaker, properties). GROUP BY need is this hierarchy's own {@code isGroupByNeeded};
     * ORDER BY entries are deferred into {@code levelOrders} (deduped across targets via
     * {@code orderedColumns}) so the caller can prepend a native measure order or drop them (union arm).
     * {@code projectParent=false} is the NOT_LAST union arm: the recorder projects the parent key
     * ONLY for {@code whichSelect} LAST/ONLY ({@code addLevelMemberSql}), so an arm suppresses the
     * parent SELECT/GROUP BY/ORDER BY entirely (the parent table still reaches the FROM via
     * {@code levelExpressions} — the recorder registers it unconditionally).
     */
    private static void projectTargetLevels(SelectStatementBuilder q, RolapLevel targetLevel,
            boolean viewAware,
            java.util.Set<String> orderedColumns,
            java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders,
            boolean projectParent) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        int levelDepth = targetLevel.getDepth();
        boolean needsGroupBy = RolapUtil.isGroupByNeeded(hierarchy, levels, levelDepth);
        for (int i = 0; i <= levelDepth; i++) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            // Parent-child level: the parent key column is emitted ahead of the level key —
            // SELECT + GROUP BY + ORDER BY (nulls first for a null nullParentValue).
            SqlExpression parentExp = level.getParentExp();
            if (parentExp != null && projectParent) {
                ProjectionRef parentRef = q.project(JoinPlanner.expressionFor(parentExp),
                        level.getInternalType(), null, "parent key " + level.getUniqueName());
                if (needsGroupBy) {
                    q.groupOn(parentRef);
                }
                // Parent ORDER BY (nulls first). A null nullParentValue uses plain
                // null-first ordering; a non-null one orders nulls as if they held that value, via the
                // dialect's order-value generator.
                SortSpec parentSort = sortSpecNullsFirst(SortingDirection.ASC);
                String npv = level.getNullParentValue();
                if (npv != null && !"null".equalsIgnoreCase(npv)) {
                    parentSort = parentSort.withNullSortValue(npv, level.getDatatype());
                }
                levelOrders.add(java.util.Map.entry(parentRef, parentSort));
            }
            SqlExpression keyExp = level.getKeyExp();
            ProjectionRef keyRef = q.project(JoinPlanner.expressionFor(keyExp), level.getInternalType(),
                    null, "level key " + level.getUniqueName());
            if (needsGroupBy) {
                q.groupOn(keyRef);
            }
            if (level.hasCaptionColumn()) {
                ProjectionRef captionRef = q.project(JoinPlanner.expressionFor(level.getCaptionExp()), null,
                        null, "level caption " + level.getUniqueName());
                if (needsGroupBy) {
                    q.groupOn(captionRef);
                }
            }

            List<? extends SqlExpression> ordinals = level.getOrdinalExps();
            if (ordinals != null && !ordinals.isEmpty()) {
                for (SqlExpression ordinalExp : ordinals) {
                    if (sameColumn(keyExp, ordinalExp)) {
                        orderOnce(levelOrders, orderedColumns, keyRef, keyExp, sortSpec(ordinalExp.getSortingDirection()));
                    } else {
                        ProjectionRef ref = q.project(JoinPlanner.expressionFor(ordinalExp), null,
                                null, "level ordinal " + level.getUniqueName());
                        if (needsGroupBy) {
                            q.groupOn(ref);
                        }
                        orderOnce(levelOrders, orderedColumns, ref, ordinalExp, sortSpec(ordinalExp.getSortingDirection()));
                        // the level key is a tiebreaker after a non-key ordinal (deterministic member order).
                        orderOnce(levelOrders, orderedColumns, keyRef, keyExp, sortSpec(SortingDirection.ASC));
                    }
                }
            } else {
                orderOnce(levelOrders, orderedColumns, keyRef, keyExp, sortSpec(SortingDirection.ASC));
            }

            for (RolapProperty property : level.getProperties()) {
                ProjectionRef ref = q.project(JoinPlanner.expressionFor(property.getExp()), null,
                        null, "member property " + property.getName());
                // Canonical (permissive-dialect) form: group a property only when NOT functionally
                // dependent on the level value; the renderer completes the group by on restrictive
                // dialects (completeNonAggregatesGroupBy below).
                if (!property.dependsOnLevelValue()) {
                    q.groupOn(ref);
                }
            }
            if (needsGroupBy) {
                q.completeNonAggregatesGroupBy();
            }
        }
    }

    /**
     * True if {@link #levelMemberCountSql} can build the count query for {@code targetLevel}: a
     * supported relation. The {@code SELECT … FROM (SELECT DISTINCT …)} subquery shape additionally
     * requires {@code dialect.allowsFromQuery()} — check that at the call site.
     * <p>
     * The per-level parent-child and plain-column gates do not apply here: the count projects each
     * counted level's key via {@link JoinPlanner#expressionFor} (a computed key travels as a
     * {@code RawVariant}), and a parent-child level projects its key like any other — the parent
     * column is ignored. The only remaining limit is a relation {@link RelationFromMapper} cannot
     * build (a view/inline shape), which keeps the recorder residue at the call site.
     */
    public static boolean countSupports(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation =
                hierarchy.getRelation();
        if (relation == null) {
            return false;
        }
        if (RelationFromMapper.supports(relation)) {
            return true;
        }
        // A SINGLE view/inline relation is mapped by levelMemberCountSql via
        // RelationFromMapper.from (SqlSelectSource -> FromVariant, InlineTableSource -> FromInline),
        // so its "select count(*) from (select distinct <keys>) init" builds. GUARD: the writeback
        // [Scenario] dimension's DEGENERATE view has an empty body (renders "from () as foo", broken
        // even on the recorder), so resolvesSingleViewOrInline keeps it declined. Parent-child on a
        // single view/inline is deliberately excluded (matches supportsExoticSingleRelation).
        return MemberSqlMapper.supportsExoticSingleRelation(targetLevel)
                && RelationFromMapper.resolvesSingleViewOrInline(relation);
    }

    /**
     * The {@code SELECT count(*) FROM (SELECT DISTINCT <keys>) "init"} cardinality query for
     * {@code targetLevel} — the {@code allowsFromQuery} shape of
     * {@code SqlMemberSource.makeLevelMemberCountSql}. The inner SELECT projects each non-all level's
     * key from the target depth up to (and including) the first {@code unique} level, in that order.
     */
    public static SelectStatement levelMemberCountSql(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();

        List<RolapLevel> keyLevels = new java.util.ArrayList<>();
        java.util.Set<String> keyTables = new java.util.LinkedHashSet<>();
        for (int i = targetLevel.getDepth(); i >= 0; i--) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            keyLevels.add(level);
            addAlias(keyTables, level.getKeyExp());
            if (level.isUnique()) {
                break;
            }
        }

        java.util.Set<String> fromTables = RelationFromMapper.memberFromTables(hierarchy.getRelation(), keyTables);
        org.eclipse.daanse.sql.statement.api.model.FromClause from =
                RelationFromMapper.fromReferenced(hierarchy.getRelation(), fromTables);
        if (from == null) {
            from = RelationFromMapper.from(hierarchy.getRelation());
        }

        SelectStatementBuilder inner = SelectStatementBuilder.create();
        // Diagnostic provenance (rendered only when comments are on — the derived table renders its
        // comments only in the formatted diagnostic mode; the executed SQL is unchanged).
        inner.header("distinct level keys");
        inner.distinct(true);
        inner.from(org.eclipse.daanse.sql.statement.api.From.commentBase(from,
                baseTableComment(targetLevel,
                        org.eclipse.daanse.sql.statement.api.From.baseAlias(from))));
        for (RolapLevel level : keyLevels) {
            inner.project(JoinPlanner.expressionFor(level.getKeyExp()), null, null,
                    "level key " + level.getUniqueName());
        }

        SelectStatementBuilder outer = SelectStatementBuilder.create();
        outer.header("level cardinality " + targetLevel.getUniqueName());
        outer.footerComment("cardinality probe (count distinct keys)");
        // Structured node that renders exactly as lower-case "count(*)": the renderer's Call
        // branch emits the aggregate NAME verbatim (no dialect consulted), and Raw("*") verbatim.
        outer.project(org.eclipse.daanse.sql.statement.api.Expressions.aggregate(
                "count", org.eclipse.daanse.sql.statement.api.Expressions.star()), null);
        outer.from(org.eclipse.daanse.sql.statement.api.From.subquery(inner.build(),
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of("init")));
        return outer.build();
    }

    /**
     * True when the leaf-level {@code count(*) FROM <table>} cardinality shape (the
     * {@code levelDepth == levels.size()} branch of {@code SqlMemberSource.makeLevelMemberCountSql})
     * can be rendered by the builder: renderable relation and a plain-column key (so the FROM table
     * can be derived the same way the generic count does).
     */
    public static boolean leafCountSupports(RolapLevel level) {
        RolapHierarchy hierarchy = level.getHierarchy();
        return hierarchy.getRelation() != null
                && RelationFromMapper.supports(hierarchy.getRelation())
                && isPlainColumn(level.getKeyExp());
    }

    /**
     * The leaf-level {@code SELECT count(*) FROM <table>} cardinality query — the full-depth branch of
     * {@code SqlMemberSource.makeLevelMemberCountSql} (no DISTINCT, no sub-query). FROM is derived from
     * the key's table exactly as the generic {@link #levelMemberCountSql} derives its inner FROM.
     */
    public static SelectStatement leafCountSql(RolapLevel level) {
        RolapHierarchy hierarchy = level.getHierarchy();
        java.util.Set<String> keyTables = new java.util.LinkedHashSet<>();
        addAlias(keyTables, level.getKeyExp());
        java.util.Set<String> fromTables = RelationFromMapper.memberFromTables(hierarchy.getRelation(), keyTables);
        org.eclipse.daanse.sql.statement.api.model.FromClause from =
                RelationFromMapper.fromReferenced(hierarchy.getRelation(), fromTables);
        if (from == null) {
            from = RelationFromMapper.from(hierarchy.getRelation());
        }
        SelectStatementBuilder b = SelectStatementBuilder.create();
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        b.header("level cardinality " + level.getUniqueName());
        b.footerComment("cardinality probe (count rows)");
        // Structured node that renders exactly as lower-case "count(*)" (see levelMemberCountSql).
        b.project(org.eclipse.daanse.sql.statement.api.Expressions.aggregate(
                "count", org.eclipse.daanse.sql.statement.api.Expressions.star()), null);
        b.from(org.eclipse.daanse.sql.statement.api.From.commentBase(from,
                baseTableComment(level, org.eclipse.daanse.sql.statement.api.From.baseAlias(from))));
        return b.build();
    }

    /**
     * The aggregate-table row-count cardinality probe — {@code SELECT count(*) FROM <table> AS
     * <alias>} — {@code AggStar.Table.makeNumberOfRows} (
     * {@code addSelect("count(*)")} + {@code addFrom(relation, name)}). {@code alias} is the AggStar
     * table's name (always equal to the physical table name for an aggregate table), so the rendered
     * FROM is {@code "agg_x" as "agg_x"} — the {@code select count(*) from "agg_x"
     * as "agg_x"} shape.
     * <p>
     * Returns {@code null} — the caller keeps the recorder — when {@code relation} is not a named
     * {@code TableSource} (a view / inline aggregate fact is outside this trivial builder's scope).
     */
    public static SelectStatement aggTableCountSql(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            String alias) {
        if (!(relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource ts)
                || ts.getTable() == null
                || ts.getTable().getName() == null || ts.getTable().getName().isBlank()) {
            return null;
        }
        String schema = ts.getTable().getNamespace()
                instanceof org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema s ? s.getName() : null;
        org.eclipse.daanse.sql.statement.api.model.TableAlias tableAlias =
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of(alias);
        org.eclipse.daanse.sql.statement.api.model.FromClause from = schema == null
                ? org.eclipse.daanse.sql.statement.api.From.table(ts.getTable().getName(), tableAlias)
                : org.eclipse.daanse.sql.statement.api.From.table(schema, ts.getTable().getName(), tableAlias);
        SelectStatementBuilder b = SelectStatementBuilder.create();
        // Structured node that renders exactly as lower-case "count(*)" (see levelMemberCountSql):
        // the aggregate NAME is emitted verbatim and Raw("*") verbatim, no dialect consulted.
        b.project(org.eclipse.daanse.sql.statement.api.Expressions.aggregate(
                "count", org.eclipse.daanse.sql.statement.api.Expressions.star()), null);
        b.from(from);
        return b.build();
    }

    // ---- helpers ---------------------------------------------------------------

    /**
     * The whole-hierarchy member enumeration of {@code SqlMemberSource.makeKeysSql}: for each non-all
     * level, the key (SELECT + GROUP BY), then each ordinal (SELECT + GROUP BY + ORDER BY) or — when the
     * level has no ordinal — the key as ORDER BY, then each property (SELECT, and GROUP BY unless the
     * the renderer completes the group by per dialect).
     * No caption and no parent-child key (makeKeysSql omits both); every column is projected with a null
     * type.
     */
    public static SelectStatement keysSql(RolapHierarchy hierarchy,
            boolean viewAware) {
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        java.util.Set<String> keyTables = new java.util.LinkedHashSet<>();
        for (RolapLevel level : levels) {
            if (level.isAll()) {
                continue;
            }
            addAlias(keyTables, level.getKeyExp());
            if (level.getOrdinalExps() != null) {
                for (SqlExpression ordinalExp : level.getOrdinalExps()) {
                    addAlias(keyTables, ordinalExp);
                }
            }
            for (RolapProperty property : level.getProperties()) {
                addAlias(keyTables, property.getExp());
            }
        }
        java.util.Set<String> fromTables = RelationFromMapper.memberFromTables(hierarchy.getRelation(), keyTables);
        org.eclipse.daanse.sql.statement.api.model.FromClause from =
                RelationFromMapper.fromReferenced(hierarchy.getRelation(), fromTables);
        if (from == null) {
            from = RelationFromMapper.from(hierarchy.getRelation());
        }

        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(from);
        for (RolapLevel level : levels) {
            if (level.isAll()) {
                continue;
            }
            SqlExpression keyExp = level.getKeyExp();
            ProjectionRef keyRef = q.project(JoinPlanner.expressionFor(keyExp), null);
            q.groupOn(keyRef);

            List<? extends SqlExpression> ordinals = level.getOrdinalExps();
            if (ordinals != null && !ordinals.isEmpty()) {
                for (SqlExpression ordinalExp : ordinals) {
                    ProjectionRef ref = q.project(JoinPlanner.expressionFor(ordinalExp), null);
                    q.groupOn(ref);
                    q.orderOn(ref, sortSpec(ordinalExp.getSortingDirection()));
                }
            } else {
                q.orderOn(keyRef, sortSpec(keyExp.getSortingDirection()));
            }

            for (RolapProperty property : level.getProperties()) {
                ProjectionRef ref = q.project(JoinPlanner.expressionFor(property.getExp()), null);
                if (!property.dependsOnLevelValue()) {
                    q.groupOn(ref);
                }
            }
            q.completeNonAggregatesGroupBy();
        }
        return q.build();
    }

    /**
     * The children of a parent-child hierarchy member ({@code SqlMemberSource.makeChildMemberSqlPC}):
     * {@code SELECT key[, ordinals][, properties] FROM <rel> WHERE (parentExp = member.key)
     * GROUP BY … ORDER BY …}. Single (unique) PC level: key projected with internalType (SELECT+GROUP
     * BY), ordinals deduped by rendered SQL against the key (matching ordinal → ORDER BY key; otherwise
     * SELECT+GROUP BY+ORDER BY the ordinal; no extra key tiebreaker), or ORDER BY key when there is no
     * ordinal, then properties (SELECT; GROUP BY only when the property is NOT level-value-dependent —
     * functionally dependent on the level value). The parent predicate is a single-element AND so it
     * renders parenthesised: {@code WHERE (parent = key)}.
     */
    public static SelectStatement parentChildChildrenSql(
            org.eclipse.daanse.rolap.api.element.RolapMember member,
            boolean viewAware) {
        RolapLevel level = (RolapLevel) member.getLevel();
        return pcLevelSelect(level, level.getParentExp(),
                parentEqualsKey(level.getParentExp(), member.getKey(), level, viewAware), viewAware);
    }

    /**
     * Roots of a parent-child hierarchy ({@code SqlMemberSource.makeChildMemberSqlPCRoot}):
     * {@code WHERE parentExp IS NULL} (or {@code = nullParentValue}), projecting the single child PC
     * level. The condition is emitted bare (no AND wrap, no parens), rendering the canonical
     * lower-case {@code is null} or {@code = <value>} with numbers unquoted and other values
     * single-quoted.
     */
    public static SelectStatement parentChildRootSql(
            org.eclipse.daanse.rolap.api.element.RolapMember member,
            boolean viewAware) {
        RolapLevel level = (RolapLevel) member.getLevel().getChildLevel();
        SqlExpression parentExp = level.getParentExp();
        String npv = level.getNullParentValue();
        // The root predicate renders bare — no AND wrap, no parens (unlike PC/PCForLevel, whose
        // parent predicate is wrapped in a one-element AND so it renders parenthesised).
        org.eclipse.daanse.sql.statement.api.expression.Predicate where;
        if (npv == null || "NULL".equalsIgnoreCase(npv)) {
            // Structured is-null → the renderer's canonical lower-case " is null", consistent with every
            // other keyword the builder emits.
            where = org.eclipse.daanse.sql.statement.api.Predicates.isNull(
                    JoinPlanner.expressionFor(parentExp));
        } else {
            // = nullParentValue: a number is left unquoted, otherwise single-quoted. The parent column is a
            // node (so a COMPUTED parent expression renders as a RawVariant); the value is a raw literal
            // preserving the config string's formatting.
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression parentNode =
                    JoinPlanner.expressionFor(parentExp);
            String valueSql;
            try {
                Double.parseDouble(npv);
                valueSql = npv;
            } catch (NumberFormatException e) {
                StringBuilder b = new StringBuilder();
                org.eclipse.daanse.olap.common.Util.singleQuoteString(npv, b);
                valueSql = b.toString();
            }
            // residual: pc-null-parent literal formatting — a Literal node cannot guarantee the config
            // string's exact numeric formatting, so a raw literal preserves it.
            where = org.eclipse.daanse.sql.statement.api.Predicates.comparison(parentNode,
                    org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                    org.eclipse.daanse.sql.statement.api.Expressions.raw(valueSql));
        }
        return pcLevelSelect(level, parentExp, where, viewAware);
    }

    /**
     * Children of a PC member queried via an intermediate parent-child level
     * ({@code SqlMemberSource.makeChildMemberSqlPCForLevel}): {@code WHERE parentChildLevel.parentExp =
     * member.key}, projecting the member's own level.
     */
    public static SelectStatement parentChildChildrenForLevelSql(
            org.eclipse.daanse.rolap.api.element.RolapMember member, RolapLevel parentChildLevel,
            boolean viewAware) {
        RolapLevel level = (RolapLevel) member.getLevel();
        return pcLevelSelect(level, parentChildLevel.getParentExp(),
                parentEqualsKey(parentChildLevel.getParentExp(), member.getKey(), level, viewAware), viewAware);
    }

    /** {@code (parentExp = key)} as a single-element AND so it renders parenthesised. */
    private static org.eclipse.daanse.sql.statement.api.expression.Predicate parentEqualsKey(
            SqlExpression parentExp, Object key, RolapLevel keyTypeLevel,
            boolean viewAware) {
        return org.eclipse.daanse.sql.statement.api.Predicates.and(java.util.List.of(
                org.eclipse.daanse.sql.statement.api.Predicates.comparison(
                        JoinPlanner.expressionFor(parentExp),
                        org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                        org.eclipse.daanse.sql.statement.api.Expressions.literal(key, keyTypeLevel.getDatatype()))));
    }

    /** Shared parent-child level projection: FROM (parentExp + the projected level's columns), the given
     *  WHERE, then key (SELECT+GROUP BY, internalType), ordinals (deduped vs key), properties. */
    private static SelectStatement pcLevelSelect(RolapLevel level, SqlExpression parentExp,
            org.eclipse.daanse.sql.statement.api.expression.Predicate where,
            boolean viewAware) {
        RolapHierarchy hierarchy = level.getHierarchy();
        java.util.Set<String> keyTables = new java.util.LinkedHashSet<>();
        addAlias(keyTables, parentExp);
        addAlias(keyTables, level.getKeyExp());
        if (level.getOrdinalExps() != null) {
            for (SqlExpression oe : level.getOrdinalExps()) {
                addAlias(keyTables, oe);
            }
        }
        for (RolapProperty property : level.getProperties()) {
            addAlias(keyTables, property.getExp());
        }
        java.util.Set<String> fromTables = RelationFromMapper.memberFromTables(hierarchy.getRelation(), keyTables);
        org.eclipse.daanse.sql.statement.api.model.FromClause from =
                RelationFromMapper.fromReferenced(hierarchy.getRelation(), fromTables);
        if (from == null) {
            from = RelationFromMapper.from(hierarchy.getRelation());
        }

        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(from);
        q.where(where);

        SqlExpression keyExp = level.getKeyExp();
        ProjectionRef keyRef = q.project(JoinPlanner.expressionFor(keyExp), level.getInternalType());
        q.groupOn(keyRef);

        List<? extends SqlExpression> ordinals = level.getOrdinalExps();
        if (ordinals != null && !ordinals.isEmpty()) {
            for (SqlExpression ordinalExp : ordinals) {
                if (orderSig(ordinalExp).equals(orderSig(keyExp))) {
                    q.orderOn(keyRef, sortSpec(ordinalExp.getSortingDirection()));
                } else {
                    ProjectionRef ref = q.project(JoinPlanner.expressionFor(ordinalExp), null);
                    q.groupOn(ref);
                    q.orderOn(ref, sortSpec(ordinalExp.getSortingDirection()));
                }
            }
        } else {
            q.orderOn(keyRef, sortSpec(keyExp.getSortingDirection()));
        }

        for (RolapProperty property : level.getProperties()) {
            ProjectionRef ref = q.project(JoinPlanner.expressionFor(property.getExp()), null);
            if (!property.dependsOnLevelValue()) {
                q.groupOn(ref);
            }
        }
        q.completeNonAggregatesGroupBy();
        return q.build();
    }

    private static boolean isPlainColumn(SqlExpression e) {
        return e instanceof org.eclipse.daanse.rolap.element.RolapColumn;
    }

    /** Adds the table alias of {@code e} (when it is a plain column) to {@code aliases}. */
    private static void addAlias(java.util.Set<String> aliases, SqlExpression e) {
        if (e instanceof org.eclipse.daanse.rolap.element.RolapColumn c && c.getTable() != null) {
            aliases.add(c.getTable());
        }
    }

    /** Orders by {@code colExp}'s projection ref unless an equal column was already ordered (dedup
     *  is by rendered SQL). Ordering on the ref lets the renderer spell it as the SELECT alias
     *  when the dialect requiresOrderByAlias, and as the expression otherwise. */
    private static void orderOnce(java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> orders,
            java.util.Set<String> seen, ProjectionRef ref, SqlExpression colExp, SortSpec spec) {
        if (seen.add(orderSig(colExp))) {
            orders.add(java.util.Map.entry(ref, spec));
        }
    }

    /** Rendered identity of an order column — the ORDER BY dedup key: duplicates must collapse by
     *  rendered SQL, not object identity. */
    private static String orderSig(SqlExpression e) {
        if (e instanceof org.eclipse.daanse.rolap.element.RolapColumn c) {
            return c.getTable() + "." + c.getName();
        }
        // Dialect-free dedup key: the per-dialect SQL variants identify the expression.
        // This is only an ORDER BY dedup signature, never executed SQL.
        return String.valueOf(org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.sqlVariants(e));
    }

    private static boolean sameColumn(SqlExpression a, SqlExpression b) {
        return a instanceof org.eclipse.daanse.rolap.element.RolapColumn ca
                && b instanceof org.eclipse.daanse.rolap.element.RolapColumn cb
                && ca.getName().equals(cb.getName())
                && java.util.Objects.equals(ca.getTable(), cb.getTable());
    }

    private static SortSpec sortSpec(SortingDirection direction) {
        SortDirection dir = direction == SortingDirection.DESC ? SortDirection.DESC : SortDirection.ASC;
        return new SortSpec(dir, true, NullOrder.LAST, false);
    }

    /** As {@link #sortSpec} but nulls first — the parent-key ORDER BY collation. */
    private static SortSpec sortSpecNullsFirst(SortingDirection direction) {
        SortDirection dir = direction == SortingDirection.DESC ? SortDirection.DESC : SortDirection.ASC;
        return new SortSpec(dir, true, NullOrder.FIRST, false);
    }
}
