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
import org.eclipse.daanse.rolap.common.star.RolapStar;
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
 * Builds the level/tuple-members SELECT for a single target {@link RolapLevel} with the generic,
 * dialect-free statement builder, for the non-virtual-cube, single-target case.
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
     * True if {@link #levelMembersSql(RolapLevel, org.eclipse.daanse.jdbc.db.dialect.api.Dialect)} can
     * build {@code targetLevel}: the relation is any composition of table / join / view / inline that
     * the dialect-aware FROM can render (incl. a view nested inside a join), with plain columns. The
     * dialect overload builds the snowflake <em>subset</em> (not the whole relation), so this is safe
     * to use authoritatively — it joins only the tables the selected columns need, without over-joining.
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
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        for (int i = 0; i <= targetLevel.getDepth(); i++) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            if (level.isParentChild()) {
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
     * The level's projected expressions in emission order — key, caption (when present),
     * ordinals, property columns. The single home of that enumeration: the plain-column checks
     * and the FROM alias collection iterate the same list, so a new expression kind is added
     * once, not per loop copy.
     */
    private static List<SqlExpression> levelExpressions(RolapLevel level) {
        List<SqlExpression> out = new java.util.ArrayList<>();
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
        return buildLevelSelect(targetLevel, fromForLevels(targetLevel, null), null,
                java.util.List.of(), java.util.Optional.empty());
    }

    /**
     * As {@link #levelMembersSql(RolapLevel)} but renders dialect-aware: a view/inline-table relation
     * (also when nested in a join) becomes a {@code From.raw} (or converted) FROM, and computed
     * (expression) columns render with their dialect-specific SQL — see {@link #supportsViaDialectFrom}
     * and {@link #supportsAllowingExpressions}.
     */
    public static SelectStatement levelMembersSql(RolapLevel targetLevel,
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect) {
        return buildLevelSelect(targetLevel, fromForLevels(targetLevel, dialect), dialect,
                java.util.List.of(), java.util.Optional.empty());
    }

    /**
     * The context-constrained level-members SELECT (tuple path): the level relation joined to
     * the fact (and any context dimension tables) so a NON-EMPTY / context restriction applies — the
     * builder counterpart of {@code addLevelMemberSql} + {@code SqlContextConstraint.addConstraint}.
     * FROM = the level relation subset, then the fact, then the context tables (the reference path's
     * add order, so the rendered SQL matches);
     * WHERE = the star join predicates then {@code where} (the translated context predicate, each its
     * own conjunct). The fact is derived from {@code joinTables} (each star table knows its star).
     */
    public static SelectStatement levelMembersSql(RolapLevel targetLevel,
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<RolapStar.Table> joinTables) {
        return levelMembersSql(targetLevel, dialect, where, joinTables, java.util.List.of(),
                java.util.Optional.empty(), java.util.Optional.empty(), false);
    }

    /**
     * As above, but with the context constraint's per-column {@code (table, predicate)} pairs so the
     * fact-join WHERE is interleaved {@code [target-dim join, then per context table: its join (once)
     * then its where(s)]} exactly like the reference path ({@code addLevelMemberSql + addConstraint}). Empty
     * {@code orderedPredicates} keeps the grouped {@code [all joins][all wheres]} form (guarded use).
     */
    public static SelectStatement levelMembersSql(RolapLevel targetLevel,
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<RolapStar.Table> joinTables,
            java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
                    orderedPredicates,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            boolean factJoinRequired) {
        org.eclipse.daanse.rolap.element.RolapCubeLevel cubeLevel0 =
                (targetLevel instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel cl) ? cl : null;
        // The fact comes from the first join table's star; or — when there are no join tables but the target
        // level still needs its OWN non-empty existence join (factJoinRequired: a NonEmptyCrossJoin with an
        // all-member co-arg — the addLevelConstraint→joinLevelTableToFactTable case) — from the target
        // level's own star-key table's star.
        RolapStar.Table fact = !joinTables.isEmpty()
                ? joinTables.get(0).getStar().getFactTable()
                : (factJoinRequired && cubeLevel0 != null && cubeLevel0.getStarKeyColumn() != null)
                        ? cubeLevel0.getStarKeyColumn().getTable().getStar().getFactTable()
                        : null;
        if (fact == null) {
            // A dimension-only restriction (member-key / child-by-name), or factJoinRequired but the target
            // has no resolvable star key: the plain level FROM with the WHERE predicate, no fact join.
            return buildLevelSelect(targetLevel, fromForLevels(targetLevel, dialect), dialect,
                    java.util.List.of(), where, nativeOrder, nativeHaving);
        }
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        java.util.Set<String> dimAliases = RelationFromMapper.tableAliases(hierarchy.getRelation());
        java.util.List<org.eclipse.daanse.sql.statement.api.model.FromClause> items =
                new java.util.ArrayList<>();
        items.add(fromForLevels(targetLevel, dialect));
        // Add the fact table UNLESS it is already the dimension relation (a degenerate dimension whose own
        // table carries the measure, e.g. a store-grain sum(store.store_sqft) TopCount): fromForLevels above
        // already put it in items.get(0), so adding it again would emit the same alias twice in the comma-join
        // FROM (`store as store, store as store`). Mirrors the
        // context-table dedup below (the !dimAliases.contains check at the joinTables loop).
        if (!dimAliases.contains(fact.getAlias())) {
            items.add(org.eclipse.daanse.sql.statement.api.From.table(fact.getTableName(),
                    org.eclipse.daanse.sql.statement.api.model.TableAlias.of(fact.getAlias())));
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
                items.add(org.eclipse.daanse.sql.statement.api.From.table(t.getTableName(),
                        org.eclipse.daanse.sql.statement.api.model.TableAlias.of(t.getAlias())));
            }
        }
        org.eclipse.daanse.sql.statement.api.model.FromClause from =
                new org.eclipse.daanse.sql.statement.api.model.FromClause.FromProduct(items);
        if (!orderedPredicates.isEmpty() || factJoinRequired) {
            // The constrained level-members query is dimension-rooted: the target relation is the FROM
            // ROOT (its own snowflake JOIN…ON tree intact, NOT flattened to comma+WHERE), the fact joins
            // INTO that root's fact-adjacent table, then each context table joins to the fact — all ANSI
            // JOIN…ON — with the per-column context restrictions in WHERE (column order). Shape:
            //   from <dim relation> join <fact> on <fact.fk = dim.key> join <ctx> on … where <constraints>
            // Rooting at the fact instead would re-join the dimension under a duplicate alias and diverge
            // from the reference SQL, so the orReference guard would fall back on every constrained query.
            org.eclipse.daanse.rolap.element.RolapCubeLevel cubeLevel =
                    (targetLevel instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel cl) ? cl : null;
            RolapStar.Table factAdjacent = (cubeLevel != null && cubeLevel.getStarKeyColumn() != null)
                    ? factAdjacentTable(cubeLevel.getStarKeyColumn().getTable(), fact) : null;
            if (factAdjacent != null) {
                java.util.Set<RolapStar.Table> emitted = new java.util.LinkedHashSet<>();
                java.util.List<JoinPlanner.JoinStep> joinSteps = new java.util.ArrayList<>();
                java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> wheres =
                        new java.util.ArrayList<>();
                // The fact is joined INTO the dimension root ONLY when genuinely needed: a non-empty
                // existence restriction (factJoinRequired) or a context/cross-join table on a DIFFERENT
                // dimension (which can only reach the target through the fact). An ancestor-only slicer —
                // every constrained table is part of the target's OWN dimension relation (in dimAliases) —
                // is a plain WHERE on that relation; the reference query adds no fact join there, so
                // emitting one would over-join and the orReference guard would fall back. Skip it then.
                boolean crossDimension = false;
                for (org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate cp
                        : orderedPredicates) {
                    if (!dimAliases.contains(cp.table().getAlias())) {
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
                // Each context table joins to the fact (parent-first, deduped). A context table that is part
                // of the dimension relation (already present in the root, e.g. a snowflake ancestor)
                // contributes only its WHERE predicate, not a duplicate join.
                for (org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate cp
                        : orderedPredicates) {
                    if (!dimAliases.contains(cp.table().getAlias())) {
                        joinSteps.addAll(JoinPlanner.joinStepsFor(cp.table(), fact, emitted));
                    }
                    wheres.add(cp.predicate());
                }
                // Join-only context tables: a joinTable with no per-column WHERE — e.g. an all-member
                // cross-join arg contributing only its non-empty existence join to the fact. joinStepsFor
                // skips tables already emitted above, so orderedPredicates' tables are not re-joined.
                for (RolapStar.Table t : referenced) {
                    if (!dimAliases.contains(t.getAlias())) {
                        joinSteps.addAll(JoinPlanner.joinStepsFor(t, fact, emitted));
                    }
                }
                // Wrap the per-column WHERE predicates in a flat top-level AND: buildLevelSelect splits a
                // top-level AND back into one q.where(...) per operand (a nested AND, e.g. a tuple key,
                // stays grouped) — one WHERE conjunct per column predicate, in column order.
                java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where2 =
                        wheres.isEmpty() ? java.util.Optional.empty()
                                : java.util.Optional.of(org.eclipse.daanse.sql.statement.api.Predicates.and(wheres));
                // items.get(0) is the target relation (the snowflake FromJoin tree) — the FROM root.
                return buildLevelSelect(targetLevel, items.get(0), dialect, joinSteps, where2, nativeOrder,
                        nativeHaving);
            }
            // No resolvable fact-adjacent table (e.g. a degenerate dimension keyed on the fact) — fall back
            // to the grouped comma-join assembly. Guarded: a divergence falls back to the reference query.
            return buildLevelSelect(targetLevel, from, dialect,
                    JoinPlanner.joinSteps(fact, referenced), where, nativeOrder, nativeHaving);
        }
        return buildLevelSelect(targetLevel, from, dialect,
                JoinPlanner.joinSteps(fact, referenced), where, nativeOrder, nativeHaving);
    }

    /**
     * The table in {@code table}'s parent chain that joins directly to {@code fact} (its parent is the
     * fact) — the dimension's fact-adjacent table, the one the fact's foreign key references. The rest of
     * the dimension's snowflake hangs off it inside the relation. Returns {@code null} when the chain does
     * not reach the fact (e.g. a degenerate dimension keyed on the fact table itself).
     */
    private static RolapStar.Table factAdjacentTable(RolapStar.Table table, RolapStar.Table fact) {
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
     * FROM subset, so on a multi-table relation the subset may be wrong — must be byte-checked.
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
     * True if {@link #levelMembersSql(RolapLevel, org.eclipse.daanse.jdbc.db.dialect.api.Dialect)} can
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
     * The FROM for a level query: the smallest relation subset reaching each
     * selected level's columns (childless-snowflake semantics). A top-level view/inline relation
     * (which has no joinable subset) is rendered via {@code from} when {@code dialect} is
     * non-null. Falls back to the whole relation if none resolve.
     */
    // Package-visible: reused by MemberSqlMapper.childMemberSql so member-children FROM uses the same
    // proven per-level snowflake subset (honoring FilterChildlessSnowflakeMembers + the computed-column
    // whole-relation fallback) instead of the whole relation.
    static org.eclipse.daanse.sql.statement.api.model.FromClause fromForLevels(
            RolapLevel targetLevel, org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        if (dialect != null && !RelationFromMapper.supports(hierarchy.getRelation())) {
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
            return dialect != null && !RelationFromMapper.supports(hierarchy.getRelation())
                    ? RelationFromMapper.from(hierarchy.getRelation())
                    : RelationFromMapper.from(hierarchy.getRelation());
        }
        java.util.Set<String> fromTables = RelationFromMapper.memberFromTables(hierarchy.getRelation(), levelTables);
        if (dialect != null && !RelationFromMapper.supports(hierarchy.getRelation())) {
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
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect,
            java.util.List<JoinPlanner.JoinStep> joinSteps,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where) {
        return buildLevelSelect(targetLevel, from, dialect, joinSteps, where, java.util.Optional.empty(),
                java.util.Optional.empty());
    }

    private static SelectStatement buildLevelSelect(RolapLevel targetLevel,
            org.eclipse.daanse.sql.statement.api.model.FromClause from,
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect,
            java.util.List<JoinPlanner.JoinStep> joinSteps,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        int levelDepth = targetLevel.getDepth();
        boolean needsGroupBy = RolapUtil.isGroupByNeeded(hierarchy, levels, levelDepth);

        SelectStatementBuilder q = SelectStatementBuilder.create();
        // Base-FROM provenance: name the dimension + the deepest projected level whose relation table
        // anchors the FROM (rendered only when comments are on; never part of the executed SQL).
        q.from(org.eclipse.daanse.sql.statement.api.From.commentBase(from,
                baseTableComment(targetLevel,
                        org.eclipse.daanse.sql.statement.api.From.baseAlias(from))));
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        boolean constrained = where.isPresent() || !joinSteps.isEmpty()
                || nativeOrder.isPresent() || nativeHaving.isPresent();
        q.header("members " + targetLevel.getUniqueName());
        q.footerComment(constrained ? "level members (constrained)" : "level members (unconstrained)");
        // Context constraint: the star join steps as ANSI JOIN…ON (the join predicate goes
        // into the FROM tree, not WHERE), then each context restriction as its own WHERE conjunct
        // (a nested AND — e.g. a tuple key — stays one grouped conjunct).
        for (JoinPlanner.JoinStep s : joinSteps) {
            q.join(org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER,
                    JoinPlanner.tableFromClause(s.table(), dialect), s.on(), "fact join (context)");
        }
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
        // (and the key tiebreaker that follows a non-key ordinal) is not emitted twice.
        java.util.Set<String> orderedColumns = new java.util.LinkedHashSet<>();
        // Defer the level ORDER BY entries so a native TopCount/Order measure order can be PREPENDED below
        // (the measure ORDER BY must precede the level ordering).
        java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders = new java.util.ArrayList<>();
        for (int i = 0; i <= levelDepth; i++) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            // Parent-child level: the parent key column is emitted ahead of the level key —
            // SELECT + GROUP BY + ORDER BY (nulls first for a null nullParentValue).
            SqlExpression parentExp = level.getParentExp();
            if (parentExp != null) {
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
                // group the property unless the dialect allows select-not-in-group-by AND the
                // property is functionally dependent on the level value.
                if ((needsGroupBy && !dialect.allowsSelectNotInGroupBy()) || !property.dependsOnLevelValue()) {
                    q.groupOn(ref);
                }
            }
        }
        // Native TopCount/Order: project the measure (after the level columns, so it gets the trailing
        // alias) and order by it FIRST, then apply the deferred level ORDER BY — the emission order of
        // TopCountConstraint.addConstraint (project the measure, order by it, before the level order).
        nativeOrder.ifPresent(no -> {
            ProjectionRef measureRef = q.project(no.measureExpr(), null, null, "measure (native order)");
            SortDirection dir = no.direction() == SortingDirection.DESC ? SortDirection.DESC : SortDirection.ASC;
            // The measure ORDER BY collates nulls last: DESC renders plain `c DESC`
            // (MySQL DESC is naturally nulls-last), ASC adds an ISNULL term.
            q.orderOn(measureRef, new SortSpec(dir, no.nullable(), NullOrder.LAST, false));
        });
        levelOrders.forEach(e -> q.orderOn(e.getKey(), e.getValue()));
        return q.build();
    }

    /**
     * True if {@link #levelMemberCountSql} can build the count query for {@code targetLevel}: a
     * supported relation and plain-column keys on every counted level. The {@code SELECT … FROM
     * (SELECT DISTINCT …)} subquery shape additionally requires {@code dialect.allowsFromQuery()} —
     * check that at the call site.
     */
    public static boolean countSupports(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        if (hierarchy.getRelation() == null || !RelationFromMapper.supports(hierarchy.getRelation())) {
            return false;
        }
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        for (int i = targetLevel.getDepth(); i >= 0; i--) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            if (level.isParentChild() || !isPlainColumn(level.getKeyExp())) {
                return false;
            }
            if (level.isUnique()) {
                break;
            }
        }
        return true;
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
        // comments only in the formatted diagnostic mode; the executed SQL is byte-identical).
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

    // ---- helpers ---------------------------------------------------------------

    /**
     * The whole-hierarchy member enumeration of {@code SqlMemberSource.makeKeysSql}: for each non-all
     * level, the key (SELECT + GROUP BY), then each ordinal (SELECT + GROUP BY + ORDER BY) or — when the
     * level has no ordinal — the key as ORDER BY, then each property (SELECT, and GROUP BY unless the
     * dialect allowsSelectNotInGroupBy and the property is functionally dependent on the level value).
     * No caption and no parent-child key (makeKeysSql omits both); every column is projected with a null
     * type.
     */
    public static SelectStatement keysSql(RolapHierarchy hierarchy,
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect) {
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
                if (!dialect.allowsSelectNotInGroupBy() || !property.dependsOnLevelValue()) {
                    q.groupOn(ref);
                }
            }
        }
        return q.build();
    }

    /**
     * The children of a parent-child hierarchy member ({@code SqlMemberSource.makeChildMemberSqlPC}):
     * {@code SELECT key[, ordinals][, properties] FROM <rel> WHERE (parentExp = member.key)
     * GROUP BY … ORDER BY …}. Single (unique) PC level: key projected with internalType (SELECT+GROUP
     * BY), ordinals deduped by rendered SQL against the key (matching ordinal → ORDER BY key; otherwise
     * SELECT+GROUP BY+ORDER BY the ordinal; no extra key tiebreaker), or ORDER BY key when there is no
     * ordinal, then properties (SELECT, GROUP BY unless allowsSelectNotInGroupBy and the property is
     * functionally dependent on the level value). The parent predicate is a single-element AND so it
     * renders parenthesised: {@code WHERE (parent = key)}.
     */
    public static SelectStatement parentChildChildrenSql(
            org.eclipse.daanse.rolap.api.element.RolapMember member,
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect) {
        RolapLevel level = (RolapLevel) member.getLevel();
        return pcLevelSelect(level, level.getParentExp(),
                parentEqualsKey(level.getParentExp(), member.getKey(), level, dialect), dialect);
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
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect) {
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
            // string's exact numeric formatting byte-for-byte; see 07-fallback-policy.
            where = org.eclipse.daanse.sql.statement.api.Predicates.comparison(parentNode,
                    org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                    org.eclipse.daanse.sql.statement.api.Expressions.raw(valueSql));
        }
        return pcLevelSelect(level, parentExp, where, dialect);
    }

    /**
     * Children of a PC member queried via an intermediate parent-child level
     * ({@code SqlMemberSource.makeChildMemberSqlPCForLevel}): {@code WHERE parentChildLevel.parentExp =
     * member.key}, projecting the member's own level.
     */
    public static SelectStatement parentChildChildrenForLevelSql(
            org.eclipse.daanse.rolap.api.element.RolapMember member, RolapLevel parentChildLevel,
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect) {
        RolapLevel level = (RolapLevel) member.getLevel();
        return pcLevelSelect(level, parentChildLevel.getParentExp(),
                parentEqualsKey(parentChildLevel.getParentExp(), member.getKey(), level, dialect), dialect);
    }

    /** {@code (parentExp = key)} as a single-element AND so it renders parenthesised. */
    private static org.eclipse.daanse.sql.statement.api.expression.Predicate parentEqualsKey(
            SqlExpression parentExp, Object key, RolapLevel keyTypeLevel,
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect) {
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
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect) {
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
            if (!dialect.allowsSelectNotInGroupBy() || !property.dependsOnLevelValue()) {
                q.groupOn(ref);
            }
        }
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
