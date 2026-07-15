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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Condition.JoinColumn;
import org.eclipse.daanse.rolap.common.util.SqlExpressionResolver;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/**
 * Engine-free helpers that translate {@link RolapStar} structure into the generic
 * {@code org.eclipse.daanse.sql.statement} model: column references, aggregate names, the
 * fact-side-first table ordering for a set of referenced tables, and the join predicate for a
 * {@link RolapStar.Condition}.
 * <p>
 * This lives next to {@code RolapStar} so the mappers and the in-engine query specs share one
 * translation. Join-extraction caveat: computed/composite-key columns fall back to a raw
 * fragment.
 */
public final class JoinPlanner {

    private JoinPlanner() {
    }

    /**
     * A {@link RolapStar.Column} as a builder column expression. Uses the column's underlying SQL
     * expression — the <em>physical</em> column ({@code "alias"."physical_name"}), not the column's
     * logical name — so the rendered SQL matches the database. Computed expressions fall back to a
     * raw fragment (see {@link #expressionFor}).
     */
    public static SqlExpression expressionFor(RolapStar.Column column) {
        return expressionFor(column.getExpression());
    }

    /**
     * A ROLAP-level expression (key/ordinal/caption/property) as a builder expression — the node
     * counterpart of {@link SqlExpressionResolver#render}: a plain
     * {@link org.eclipse.daanse.rolap.element.RolapColumn} becomes
     * {@code "tableAlias"."column"}; any other (computed/expression) column becomes a
     * {@code RawVariant} carrying the whole per-dialect SQL map, resolved to the dialect-specific
     * variant at render time rather than collapsing to the generic one.
     * For a {@link RolapStar.Column} (always plain) use {@link #expressionFor(RolapStar.Column)}.
     */
    public static SqlExpression expressionFor(org.eclipse.daanse.olap.api.sql.SqlExpression expression) {
        if (expression instanceof org.eclipse.daanse.rolap.element.RolapColumn c) {
            return Expressions.column(TableAlias.of(c.getTable()), c.getName());
        }
        return Expressions.rawVariant(SqlExpressionResolver.sqlVariants(expression));
    }

    /**
     * A measure as a builder expression, rendered the way its {@link Aggregator} dictates:
     * {@code aggregator.getExpression(inner)} over the measure's physical column (or {@code *} when
     * the measure has no expression, i.e. {@code count(*)}). Works for
     * every aggregator — including custom ones whose SQL function name differs from their display
     * name (e.g. a {@code "BITAGG"} aggregator that renders {@code BIT_AND_AGG(...)}). The result is
     * a {@link org.eclipse.daanse.sql.statement.api.expression.SqlExpression.Raw} because the
     * aggregator bakes the (dialect-quoted) column into its rendering.
     */
    public static SqlExpression expressionFor(RolapStar.Measure measure, Dialect dialect) {
        org.eclipse.daanse.olap.api.aggregator.Aggregator aggregator = measure.getAggregator();
        // Dialect-free node measure (mirrors AbstractQuerySpec.addMeasure): the inner operand is the measure
        // column node (plain Column / computed RawVariant) wrapped by the aggregator — the same aggregate
        // over the same column rendering as the raw string fallback below.
        SqlExpression innerNode = measure.getExpression() == null
                ? Expressions.star()
                : expressionFor(measure.getExpression());
        SqlExpression node = org.eclipse.daanse.rolap.aggregator.SqlNodeAggregator.toNodeOrNull(
                aggregator, innerNode);
        if (node != null) {
            return node;
        }
        // Bail: a custom aggregator without node support renders via its string form, kept raw.
        String inner = measure.getExpression() == null ? "*" : columnSql(dialect, measure.getExpression());
        return Expressions.raw(aggregator.getExpression(inner).toString());
    }

    /**
     * The dialect-quoted SQL for a column expression, matching how the renderer quotes a column.
     * THE single sanctioned producer-side quoting site: it feeds only the custom-aggregator bail
     * above (an aggregator without a node form renders its string once, kept raw); everything
     * else travels as dialect-free nodes. Do not add callers — retire it with the aggregator
     * node-form completion.
     */
    private static String columnSql(Dialect dialect, org.eclipse.daanse.olap.api.sql.SqlExpression expression) {
        if (expression instanceof org.eclipse.daanse.rolap.element.RolapColumn c) {
            return dialect.quoteIdentifier(c.getTable()) + "." + dialect.quoteIdentifier(c.getName());
        }
        // Resolve the dialect-specific SQL variant, NOT the dialect-blind "generic"
        // (ANSI, double-quoted) one — otherwise a computed measure expression renders with " instead of the
        // dialect's quoting (invalid on MySQL).
        return SqlExpressionResolver.render(expression, dialect);
    }

    /** Distinct set of tables referenced by the given columns, in encounter order. */
    public static Set<RolapStar.Table> referencedTables(Collection<? extends RolapStar.Column> columns) {
        Set<RolapStar.Table> tables = new LinkedHashSet<>();
        columns.forEach(c -> tables.add(c.getTable()));
        return tables;
    }

    /**
     * The dimension tables to join, in the order the reference query adds them: for each referenced
     * table (in encounter order) the table itself then its ancestors up to — but excluding — the
     * fact table, walking {@link RolapStar.Table#getParentTable()}. Deduplicated, first occurrence
     * wins.
     */
    public static Set<RolapStar.Table> joinOrder(RolapStar.Table fact, Set<RolapStar.Table> referenced) {
        Set<RolapStar.Table> order = new LinkedHashSet<>();
        for (RolapStar.Table t : referenced) {
            for (RolapStar.Table cur = t; cur != null && cur != fact; cur = cur.getParentTable()) {
                order.add(cur);
            }
        }
        return order;
    }

    /**
     * The join predicates for the given referenced tables, in the order the reference query adds
     * them to {@code WHERE}: for each referenced table (encounter order) the chain to the fact
     * table is emitted <em>parent-first</em> (a table's parent join condition precedes its own),
     * deduplicated. (Note this differs from {@link #joinOrder}, which lists the tables themselves
     * self-first for the {@code FROM} clause.)
     */
    public static java.util.List<Predicate> joinPredicates(RolapStar.Table fact,
            Set<RolapStar.Table> referenced) {
        Set<RolapStar.Table> emitted = new LinkedHashSet<>();
        java.util.List<Predicate> predicates = new java.util.ArrayList<>();
        for (RolapStar.Table t : referenced) {
            emitJoinChain(t, fact, emitted, predicates);
        }
        return predicates;
    }

    /**
     * The join predicate(s) connecting {@code table} (and any snowflake ancestors not yet present) to
     * {@code fact}, using a CALLER-OWNED {@code emitted} set so repeated calls dedup across tables —
     * lets a consumer interleave each table's join with its own WHERE conjuncts
     * while still emitting every join exactly once. Returns
     * empty when {@code table} (and its ancestors) were already emitted.
     */
    public static java.util.List<Predicate> joinChainFor(RolapStar.Table table, RolapStar.Table fact,
            Set<RolapStar.Table> emitted) {
        java.util.List<Predicate> predicates = new java.util.ArrayList<>();
        emitJoinChain(table, fact, emitted, predicates);
        return predicates;
    }

    private static void emitJoinChain(RolapStar.Table table, RolapStar.Table fact,
            Set<RolapStar.Table> emitted, java.util.List<Predicate> predicates) {
        if (table == null || table == fact || !emitted.add(table)) {
            return;
        }
        emitJoinChain(table.getParentTable(), fact, emitted, predicates); // parent's condition first
        predicates.add(joinPredicate(table.getJoinCondition()));
    }

    /**
     * A single ANSI {@code JOIN … ON} step: the dimension {@code table} to join and the {@code on}
     * join predicate connecting it to its parent. The {@link #joinSteps}/{@link #joinStepsFor}
     * counterparts of {@link #joinPredicates}/{@link #joinChainFor} return these instead of bare
     * predicates so a consumer can route the join into the FROM tree ({@code innerJoin}) rather than
     * {@code WHERE}, while keeping the same parent-first, deduped order.
     */
    public record JoinStep(RolapStar.Table table, Predicate on) {
    }

    /** Registers {@code table} and its snowflake ancestors (self-first, up to but excluding the
     *  fact) in {@code pending}, preserving first-appearance order. A {@code null} table (a shared /
     *  non-cube level constrained via its key expression) registers nothing. */
    public static void addChainSelfFirst(Set<RolapStar.Table> pending, RolapStar.Table table,
            RolapStar.Table fact) {
        for (RolapStar.Table t = table; t != null && t != fact; t = t.getParentTable()) {
            pending.add(t);
        }
    }

    /**
     * The assembler's FROM/JOIN fold over the registered tables: starting from the fact,
     * repeatedly scan {@code pending} in registration order and join every table whose parent is
     * already in the tree ({@code JOIN t ON t.joinCondition}), until all are placed. Single-chain
     * registrations reduce to the plain parent-first order; a mix of dimensions places each
     * fact-adjacent table on its first eligible pass and deeper snowflake tables as soon as their
     * parent lands. A table whose chain never reaches the tree (defensive; chains are registered
     * whole) is appended parent-first so the SQL stays valid.
     */
    public static java.util.List<JoinStep> foldJoinSteps(java.util.LinkedHashSet<RolapStar.Table> pending,
            RolapStar.Table fact) {
        return foldJoinSteps(pending, fact, Set.of());
    }

    /**
     * As {@link #foldJoinSteps(java.util.LinkedHashSet, RolapStar.Table)} with tables that are
     * ALREADY part of the caller's FROM (e.g. a chain table whose alias lives inside the FROM-root
     * relation): they are seeded as placed — never re-joined (the same alias twice is invalid SQL) —
     * but their children still attach to them.
     */
    public static java.util.List<JoinStep> foldJoinSteps(java.util.LinkedHashSet<RolapStar.Table> pending,
            RolapStar.Table fact, Set<RolapStar.Table> prePlaced) {
        java.util.List<JoinStep> steps = new java.util.ArrayList<>();
        Set<RolapStar.Table> placed = new java.util.HashSet<>(prePlaced);
        placed.add(fact);
        boolean progress = true;
        while (!pending.isEmpty() && progress) {
            progress = false;
            for (java.util.Iterator<RolapStar.Table> it = pending.iterator(); it.hasNext();) {
                RolapStar.Table t = it.next();
                if (placed.contains(t.getParentTable())) {
                    steps.add(new JoinStep(t, joinPredicate(t.getJoinCondition())));
                    placed.add(t);
                    it.remove();
                    progress = true;
                }
            }
        }
        for (RolapStar.Table t : pending) {
            steps.addAll(joinStepsFor(t, fact, placed));
        }
        return steps;
    }

    /**
     * The join steps for the given referenced tables, in the order the reference query adds them: for
     * each referenced table (encounter order) the chain to the fact table is emitted
     * <em>parent-first</em> (a table's parent join precedes its own), deduplicated. The ANSI
     * {@code JOIN … ON} counterpart of {@link #joinPredicates}.
     */
    public static java.util.List<JoinStep> joinSteps(RolapStar.Table fact,
            Set<RolapStar.Table> referenced) {
        Set<RolapStar.Table> emitted = new LinkedHashSet<>();
        java.util.List<JoinStep> steps = new java.util.ArrayList<>();
        for (RolapStar.Table t : referenced) {
            emitJoinSteps(t, fact, emitted, steps);
        }
        return steps;
    }

    /**
     * The join step(s) connecting {@code table} (and any snowflake ancestors not yet present) to
     * {@code fact}, using a CALLER-OWNED {@code emitted} set so repeated calls dedup across tables.
     * The ANSI {@code JOIN … ON} counterpart of {@link #joinChainFor}.
     */
    public static java.util.List<JoinStep> joinStepsFor(RolapStar.Table table, RolapStar.Table fact,
            Set<RolapStar.Table> emitted) {
        java.util.List<JoinStep> steps = new java.util.ArrayList<>();
        emitJoinSteps(table, fact, emitted, steps);
        return steps;
    }

    private static void emitJoinSteps(RolapStar.Table table, RolapStar.Table fact,
            Set<RolapStar.Table> emitted, java.util.List<JoinStep> steps) {
        if (table == null || table == fact || !emitted.add(table)) {
            return;
        }
        emitJoinSteps(table.getParentTable(), fact, emitted, steps); // parent joined first (left-deep)
        steps.add(new JoinStep(table, joinPredicate(table.getJoinCondition())));
    }

    /**
     * The FROM clause for a star table (fact or a joined dimension): a plain {@link From#table} for a
     * named table, or — when the table has no name (an inline {@code VALUES} table, or a view) — the
     * dialect-aware rendering of its relation via {@link RelationFromMapper#from}.
     */
    public static FromClause tableFromClause(RolapStar.Table table) {
        String name = table.getTableName();
        if (name == null || name.isBlank()) {
            return RelationFromMapper.from(table.getRelation());
        }
        // A star table declared with a schema SQL filter carries it in the FromTable.filter
        // slot — the renderer pushes it into WHERE, matching the recorder's addFromTable conjunct.
        return From.table(From.tableRef(null, name), TableAlias.of(table.getAlias()),
                RelationFromMapper.tableFilter(table.getRelation()), java.util.Map.of());
    }

    /**
     * The parent-member constraint for a member-children query — the dialect-free counterpart of
     * {@code MemberConstraintWriter.addMemberConstraint} for a single parent: an equality
     * {@code keyColumn = key} for the parent and each non-all ancestor (member level first, then up),
     * AND-combined and wrapped in parentheses (the {@code Predicates.and} of one-or-more equalities
     * renders {@code (a)} / {@code (a and b)}).
     * <p>
     * Empty when the parent is the all member (drilling the all member's children has no filter).
     * Returns an always-false predicate for a null member. Only plain single-member key constraints
     * are produced here; richer constraints (context/slicer, multi-value {@code IN}) are not.
     */
    public static java.util.Optional<Predicate> memberKeyConstraint(
            org.eclipse.daanse.rolap.api.element.RolapMember parent) {
        java.util.List<Predicate> equalities = new java.util.ArrayList<>();
        for (org.eclipse.daanse.rolap.api.element.RolapMember m = parent;
                m != null && !m.isAll(); m = m.getParentMember()) {
            if (m.isNull()) {
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.trace(
                        "member-key {}: always-false (null member)", m);
                return java.util.Optional.of(Predicates.or(java.util.List.of())); // always false → 1 = 0
            }
            org.eclipse.daanse.rolap.element.RolapLevel level =
                    (org.eclipse.daanse.rolap.element.RolapLevel) m.getLevel();
            // A regular member can have a NULL key (the Util.sqlNullValue sentinel, displayed as "#null") —
            // m.isNull() (the FLAG_NULL member type) is NOT that. Constrain it with IS NULL, else the
            // equality `key = <null literal>` matches no rows and the null-key parent appears childless.
            if (m.getKey() == org.eclipse.daanse.olap.common.Util.sqlNullValue) {
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.trace(
                        "member-key {}: IS NULL (null-key sentinel)", m);
                equalities.add(Predicates.isNull(expressionFor(level.getKeyExp())));
            } else {
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.trace("member-key {}: key=literal", m);
                equalities.add(Predicates.comparison(expressionFor(level.getKeyExp()), ComparisonOperator.EQ,
                        Expressions.literal(m.getKey(), level.getDatatype())));
            }
            // Ancestors above the first unique level are redundant — a unique key already identifies the member.
            if (level.isUnique()) {
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.trace(
                        "member-key: stop ascending at unique level {}", level);
                break;
            }
        }
        return equalities.isEmpty() ? java.util.Optional.empty()
                : java.util.Optional.of(Predicates.and(equalities));
    }

    /** The {@code left = right} equality for a join condition. */
    public static Predicate joinPredicate(RolapStar.Condition condition) {
        return Predicates.comparison(joinConditionSide(condition.leftColumn(), condition.getLeft()), ComparisonOperator.EQ,
                joinConditionSide(condition.rightColumn(), condition.getRight()));
    }

    /**
     * A join-condition side as a builder expression. Core resolves plain columns to a
     * {@link JoinColumn} ({@code alias}+{@code name}); non-plain expressions (computed/composite
     * keys) fall back to a dialect-keyed {@code RawVariant} the renderer resolves at the render
     * boundary (same as {@link #expressionFor}), rather than pre-rendering one generic variant.
     */
    private static SqlExpression joinConditionSide(Optional<JoinColumn> column,
            org.eclipse.daanse.olap.api.sql.SqlExpression raw) {
        return column.<SqlExpression>map(c -> Expressions.column(TableAlias.of(c.tableAlias()), c.columnName()))
                .orElseGet(() -> Expressions.rawVariant(SqlExpressionResolver.sqlVariants(raw)));
    }
}
