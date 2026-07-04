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
import java.util.Map;
import java.util.function.Function;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.FromClause;

/**
 * The immutable mutation tape of the retired query facade: one {@link QueryOp} per {@code track()}
 * step, in call order, plus the query-level flags a replay needs ({@code supported},
 * {@code distinct}, {@code formatted}).
 *
 * <p>Each op captures EXACTLY the information the corresponding {@code track()} lambda consumed —
 * dialect-free builder nodes ({@link SqlExpression} / {@link Predicate} / {@link FromClause} are
 * immutable values and are carried directly), raw strings, and flags. The only non-value payload is
 * the {@link FromDeferred} resolver closure (a view / inline-table / sub-query FROM that needs the
 * {@link Dialect} at render time); it is effectively immutable and is carried as-is.
 *
 * <p>{@code org.eclipse.daanse.rolap.common.sqlbuild.ContributionAssembler} replays a tape against
 * a fresh {@code SelectStatementBuilder} — running the byte-identical copied logic of each recorded
 * lambda plus the copied {@code buildStatement}/{@code buildFromClause} assembly — to produce the
 * same SQL as the live the retired query facade it was recorded from.
 */
public record QueryTape(List<QueryOp> ops, boolean supported, boolean distinct, boolean formatted) {

    public QueryTape {
        ops = List.copyOf(ops);
    }

    /** One recorded {@code track()} mutation step of the retired query facade. */
    public sealed interface QueryOp {
    }

    /** {@code setDistinct(boolean)} — {@code builder.distinct(value)}. */
    public record Distinct(boolean value) implements QueryOp {
    }

    /**
     * {@code addFromRendered(...)} — a FROM item carried as an already-built immutable
     * {@link FromClause} node: {@code FromRaw} (rendered subquery string via {@code addFromQuery}),
     * {@code FromVariant} (view), or {@code FromInline} (inline table). Added only if {@code alias}
     * is new to the builder-FROM alias set.
     */
    public record FromRendered(String alias, FromClause node) implements QueryOp {
    }

    /**
     * {@code addDeferredFrom(...)} — a view / inline-table / sub-query FROM whose {@link FromClause}
     * needs the live {@link Dialect}: a placeholder is added at the current FROM index and the
     * resolver replaces it at assembly time. The resolver closure is effectively immutable (the
     * sub-query variant closes over another the retired query facade — acceptable for 4.1a).
     */
    public record FromDeferred(String alias, Function<Dialect, FromClause> resolver) implements QueryOp {
    }

    /**
     * {@code addFromTable(...)} — a plain {@code [schema.]table AS alias} FROM item. {@code hints}
     * is the EFFECTIVE hint map (already gated on {@code allowHints} at record time; empty when
     * hints are disabled or absent). The per-table filter is not part of this op — it was routed
     * through {@code addWhere(String)} and is recorded as its own {@link WhereRaw}.
     */
    public record FromTable(String schema, String name, String alias, Map<String, String> hints)
            implements QueryOp {
    }

    /**
     * {@code addJoin(...)} / {@code addJoinCondition(...)} — a dialect-free ANSI {@code JOIN … ON}
     * edge between two FROM aliases; the assembly folds these into a left-deep {@code FromJoin}
     * tree (unused cyclic edges go to WHERE).
     */
    public record JoinEdge(String leftAlias, String rightAlias, Predicate on) implements QueryOp {
    }

    /**
     * Any of the SELECT-projection mutators ({@code addSelect}, {@code addSelectExpr},
     * {@code addSelectNode} and their {@code *Commented} variants) — a single projection:
     * <ul>
     *   <li>{@code node} — the dialect-free expression node (a {@code Raw} for legacy string
     *       callers);</li>
     *   <li>{@code explicitAlias} — the explicit {@code ColumnAlias} fed to the builder, or
     *       {@code null} for the auto-{@code c<ordinal>} path (renderer synthesizes it);</li>
     *   <li>{@code mapAlias} — the alias under which the {@code ProjectionRef} is registered for
     *       GROUP BY / ORDER BY resolution (the auto alias on the auto path, the explicit alias
     *       otherwise), or {@code null} when the caller passed a null alias (no registration);</li>
     *   <li>{@code comment} — the diagnostic-render comment, or {@code null} (also selects the
     *       exact builder {@code project(...)} overload the original lambda called).</li>
     * </ul>
     */
    public record SelectItem(SqlExpression node, BestFitColumnType type, String explicitAlias,
            String mapAlias, String comment) implements QueryOp {
    }

    /** {@code setHeaderComment(String)} — statement-level diagnostic comment. */
    public record Header(String comment) implements QueryOp {
    }

    /** {@code setFooterComment(String)} — trailing statement-level diagnostic comment (request type +
     *  constraint class), appended at the very end of the diagnostic render only. */
    public record Footer(String comment) implements QueryOp {
    }

    /**
     * {@code commentFrom(String, String)} — a diagnostic "why" comment for the FROM item with the
     * given alias, merged onto its {@code FromJoin} edge at assembly (emitted only by the
     * comment render; never affects the executed SQL).
     */
    public record FromComment(String alias, String comment) implements QueryOp {
    }

    /**
     * {@code addGroupBy(String, String)} — GROUP BY a raw expression deduped by the expression
     * string, rendered via the projection ref of {@code alias} when projected.
     */
    public record GroupByAliased(String expression, String alias) implements QueryOp {
    }

    /** {@code addGroupByExpr(...)} — GROUP BY a dialect-free node, deduped by {@code "e:" + node}. */
    public record GroupByExprNode(SqlExpression node, String alias) implements QueryOp {
    }

    /** {@code addGroupByNode(...)} — GROUP BY a builder node, deduped by {@code "node:" + alias}. */
    public record GroupByNode(SqlExpression node, String alias) implements QueryOp {
    }

    /** {@code addWhere(String)} — a raw WHERE conjunct, deduped by the expression string. */
    public record WhereRaw(String expression) implements QueryOp {
    }

    /** {@code addWhere(Predicate)} — a dialect-free WHERE predicate (no dedup). */
    public record WherePredicate(Predicate predicate) implements QueryOp {
    }

    /** {@code addWhere(Predicate, String)} — a dialect-free WHERE predicate with a comment slot. */
    public record WherePredicateCommented(Predicate predicate, String comment) implements QueryOp {
    }

    /**
     * {@code addHaving(Predicate, String)} (P5-N4c) — a dialect-free HAVING conjunct
     * predicate, deduped by node value (node equality is render-equality, so it mirrors the
     * string dedup the retired raw HAVING op used); {@code comment} selects the commented
     * builder overload when non-null.
     */
    public record HavingNode(Predicate predicate, String comment) implements QueryOp {
    }

    /** {@code addOrderBy(CharSequence, CharSequence, ...)} — an ORDER BY item on a raw expression. */
    public record OrderBy(String expr, String alias, SortingDirection direction, boolean prepend,
            boolean nullable, boolean collateNullsLast) implements QueryOp {
    }

    /** {@code addOrderByExpr(expression, alias, ...)} — an ORDER BY item on a dialect-free node. */
    public record OrderByNode(SqlExpression node, String alias, SortingDirection direction,
            boolean prepend, boolean nullable, boolean collateNullsLast) implements QueryOp {
    }

    /** The parent-value {@code addOrderByExpr(expression, alias, ..., nullParentValue, ...)} —
     *  nulls sort as if they held {@code nullParentValue}. */
    public record OrderByValueNode(SqlExpression node, String alias, SortingDirection direction,
            boolean prepend, String nullParentValue, Datatype type, boolean collateNullsLast)
            implements QueryOp {
    }

    /** {@code addGroupingSet(List<String>)} — one GROUPING SETS entry (raw key expressions). */
    public record GroupingSet(List<String> columns) implements QueryOp {
        public GroupingSet {
            columns = List.copyOf(columns);
        }
    }

    /** {@code addGroupingFunction(String)} — one {@code grouping(expr)} SELECT-tail function. */
    public record GroupingFunction(String columnExpr) implements QueryOp {
    }

    /** {@code setSupported(false)} — the query was marked unsupported (no builder effect). */
    public record Unsupported() implements QueryOp {
    }
}
