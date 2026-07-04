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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/**
 * Builds the segment / aggregate-measure SELECT for a {@link RolapStar} query with the generic
 * statement builder — the dialect-free counterpart of {@code AbstractQuerySpec.generateSql}
 * (the {@code QuerySpec} seam). The expressive aggregate builder behind that seam.
 * <p>
 * Segment SQL conventions: FROM rooted at the fact table with each reachable dimension table
 * ANSI-joined in ({@code JOIN…ON}, parent-first, deduped, fact-side first); per dimension
 * column, its join chain is emitted before its constraint conjunct (the {@code [join,
 * constraint]} order); dimension columns aliased {@code c{i}} and grouped; measures aliased
 * {@code m{i}} and rendered via {@link JoinPlanner#expressionFor(RolapStar.Measure, Dialect)}. Constraints arrive as builder
 * {@link Predicate}s (translate {@code StarPredicate}s with {@link StarPredicateTranslator}).
 * <p>
 * The {@code dialect} parameter is needed for one thing only: rendering each measure. A measure's
 * SQL is produced by its {@code Aggregator.getExpression(inner)} over the dialect-quoted column —
 * the function name is the aggregator's (e.g. a {@code "BITAGG"} aggregator renders
 * {@code BIT_AND_AGG(...)}), which is not available dialect-free. Everything else this mapper
 * produces (columns, joins, group/order) is dialect-free and rendered later by the renderer.
 */
public final class AggregateSqlMapper {

    private AggregateSqlMapper() {
    }

    /**
     * @param fact          the star's fact table
     * @param groupBy       dimension columns to select + group by (in order; aliased {@code c0..})
     * @param columnFilters per-column constraint predicates, <em>parallel to {@code groupBy}</em>
     *                      (a {@code null} entry means no constraint on that column)
     * @param measures      aggregated measures (in order; aliased {@code m0..})
     * @param dialect       used only to render the measures (see class doc)
     * @return the rendered-ready {@link SelectStatement}
     */
    public static SelectStatement aggregate(
        RolapStar.Table fact,
        List<RolapStar.Column> groupBy,
        List<Predicate> columnFilters,
        List<RolapStar.Measure> measures,
        org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect)
    {
        return aggregate(fact, groupBy, columnFilters, measures, dialect, java.util.List.of());
    }

    /** A compound (slicer) predicate not tied to a group-by column: the translated WHERE plus the star
     *  tables its constrained columns require joined to the fact (per predicate: join each
     *  constrained column's table, then the predicate WHERE). */
    public record ExtraConjunct(Predicate where, List<RolapStar.Table> joinTables) {
    }

    /**
     * As {@link #aggregate(RolapStar.Table, List, List, List, Dialect)} but also emits {@code extras} —
     * compound (slicer) predicates not tied to a group-by column — after the group-by columns and before
     * the measures, matching {@code nonDistinctGenerateSql}'s order (columns, extraPredicates,
     * measures).
     */
    public static SelectStatement aggregate(
        RolapStar.Table fact,
        List<RolapStar.Column> groupBy,
        List<Predicate> columnFilters,
        List<RolapStar.Measure> measures,
        org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect,
        List<ExtraConjunct> extras)
    {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        if (!measures.isEmpty()) {
            q.header("segment cube " + measures.get(0).getCubeName());
        }
        q.footerComment("segment request");

        // FROM: fact + the dimension tables reachable from the referenced columns, fact-side first.
        List<RolapStar.Column> referenced = new ArrayList<>(groupBy);
        referenced.addAll(measures);
        Set<RolapStar.Table> referencedTables = JoinPlanner.referencedTables(referenced);
        Set<RolapStar.Table> joined = JoinPlanner.joinOrder(fact, referencedTables);
        // FROM: fact as the root; each referenced dimension table is joined in via emitJoinChain below
        // (ANSI JOIN…ON, parent-first, deduped). Base-FROM provenance names the fact (+ cube when known).
        q.from(From.commentBase(tableFromClause(fact, dialect),
                !measures.isEmpty() ? "fact table (cube " + measures.get(0).getCubeName() + ")"
                        : "fact table " + fact.getTableName()));

        // WHERE: nonDistinctGenerateSql's interleaving — per group-by column in order, the column's
        // table join chain (emitted once, parent-first) then that column's constraint; the measures'
        // join chains follow. The conjunct order is join,constraint per dimension — NOT
        // all-joins-then-all-constraints.
        Set<RolapStar.Table> emittedJoins = new java.util.LinkedHashSet<>();
        // Fact table's own sqlWhereExpression first — the fact's filter precedes any dimension
        // join/constraint.
        Predicate factWhere = tableWhere(fact);
        if (factWhere != null) {
            q.where(factWhere);
        }
        for (int i = 0; i < groupBy.size(); i++) {
            RolapStar.Column gbCol = groupBy.get(i);
            emitJoinChain(gbCol.getTable(), fact, emittedJoins, q, dialect,
                    "snowflake key " + columnLabel(gbCol));
            Predicate filter = (columnFilters != null && i < columnFilters.size())
                ? columnFilters.get(i) : null;
            if (filter != null) {
                q.where(filter, "context " + columnLabel(gbCol));
            }
        }
        // Compound (slicer) predicates not tied to a group-by column, emitted here (after columns,
        // before measures) — per predicate, its constrained columns' join chains first, then the
        // predicate WHERE.
        for (ExtraConjunct ex : extras) {
            for (RolapStar.Table t : ex.joinTables()) {
                emitJoinChain(t, fact, emittedJoins, q, dialect, "snowflake slicer");
            }
            q.where(ex.where(), "slicer (compound)");
        }
        for (RolapStar.Measure m : measures) {
            emitJoinChain(m.getTable(), fact, emittedJoins, q, dialect, "snowflake measure");
        }

        // SELECT: group-by columns (grouped), then aggregated measures (aliased m{i}).
        for (RolapStar.Column col : groupBy) {
            ProjectionRef ref = q.project(JoinPlanner.expressionFor(col), col.getInternalType(), null,
                    "key " + columnLabel(col));
            q.groupOn(ref);
        }
        for (int i = 0; i < measures.size(); i++) {
            RolapStar.Measure m = measures.get(i);
            q.project(JoinPlanner.expressionFor(m, dialect), m.getInternalType(), ColumnAlias.of("m" + i),
                    measureComment(m));
        }
        SelectStatement statement = q.build();
        // Reached only on the builder fast path (AbstractQuerySpec.tryAggregateBuilder via SqlBuildGuard.build),
        // so the path is always builder-authoritative here.
        if (org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.isDebugEnabled()) {
            int filterCount = columnFilters == null ? 0
                    : (int) columnFilters.stream().filter(java.util.Objects::nonNull).count();
            new StatementBuildSummary("aggregate", "fact=" + fact.getAlias(), groupBy.size(), measures.size(),
                    filterCount, joined.size(), 0, "builder-authoritative", null).log();
        }
        return statement;
    }

    /** A constrained drill-through column: its star column, optional WHERE predicate, whether it is
     *  projected, the (pre-deduped) SELECT alias, and whether it participates in {@code ORDER BY}
     *  (the request's cut columns do; columns surfaced only by a compound slicer predicate do not). */
    public record DrillColumn(RolapStar.Column column, Predicate filter, boolean selected, String alias,
            boolean ordered) {
    }

    /** A pre-rendered SELECT item (a drill-through measure's raw expr, or a {@code NULL} placeholder)
     *  with its result-reading column type (measure: {@code null}; NULL placeholder: STRING) and an
     *  optional provenance comment (rendered only when comments are on). */
    public record RawProjection(SqlExpression expr,
            org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType type, String alias, String comment) {
        public RawProjection(SqlExpression expr,
                org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType type, String alias) {
            this(expr, type, alias, null);
        }
    }

    /**
     * The detail-row drill-through SELECT — the dialect-free counterpart of {@code
     * DrillThroughQuerySpec.generateSql} (= {@code nonDistinctGenerateSql} + inapplicable NULLs +
     * row limit). Not aggregated (no GROUP BY). FROM = fact + the tables reachable from <em>all</em>
     * {@code columns} (fact-side first, reusing {@link JoinPlanner}); WHERE = join predicates, then each
     * column's {@code filter}, then {@code extraFilters}; SELECT = each <em>selected</em> column
     * ({@link JoinPlanner#expressionFor(RolapStar.Column)} aliased) then the {@code rawProjections} (measures + NULLs) in
     * order; ORDER BY each selected column ascending (plain, nulls-last) when that column is marked
     * {@link DrillColumn#ordered()}; a {@code rowLimit} when positive.
     */
    public static SelectStatement drillThrough(
        RolapStar.Table fact,
        List<DrillColumn> columns,
        List<Predicate> extraFilters,
        List<RawProjection> rawProjections,
        boolean countOnly,
        long rowLimit,
        org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect)
    {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        q.header("drill-through fact " + fact.getAlias());
        q.footerComment(countOnly ? "drill-through request (count)" : "drill-through request");

        // FROM: fact + the dimension tables reachable from all columns, fact-side first (table order).
        List<RolapStar.Column> all = new ArrayList<>();
        for (DrillColumn c : columns) {
            all.add(c.column());
        }
        Set<RolapStar.Table> referencedTables = JoinPlanner.referencedTables(all);
        Set<RolapStar.Table> joined = JoinPlanner.joinOrder(fact, referencedTables);
        // FROM: fact as the root; each referenced dimension table is joined in via emitJoinChain below
        // (ANSI JOIN…ON, parent-first, deduped). Base-FROM provenance names the fact (drill-through has
        // no cube handle here — the star serves several cubes — so the table name stands in).
        q.from(From.commentBase(tableFromClause(fact, dialect), "fact table " + fact.getTableName()));

        // WHERE: interleaved per column in order — the column's table join chain (emitted once,
        // parent-first) then the column's filter; then the trailing extra (slicer) predicates.
        Set<RolapStar.Table> emittedJoins = new java.util.LinkedHashSet<>();
        Predicate factWhere = tableWhere(fact);
        if (factWhere != null) {
            q.where(factWhere);
        }
        for (DrillColumn c : columns) {
            emitJoinChain(c.column().getTable(), fact, emittedJoins, q, dialect,
                    "snowflake " + columnLabel(c.column()));
            if (c.filter() != null) {
                q.where(c.filter(), "context " + columnLabel(c.column()));
            }
        }
        for (Predicate filter : extraFilters) {
            q.where(filter, "slicer (compound)");
        }

        // Count-only drill-through: just SELECT count(*) over the same FROM+WHERE; no detail
        // columns, order, raw projections or limit.
        if (countOnly) {
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                    "drill-through: countOnly=true -> SELECT count(*)");
            q.project(Expressions.countStar(), org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType.INT);
            return q.build();
        }

        // SELECT: selected columns (named alias); ORDER BY each selected column, deduped by rendered
        // SQL (the same column must not be ordered twice); then the raw projections.
        Set<String> orderedColumns = new java.util.LinkedHashSet<>();
        for (DrillColumn c : columns) {
            if (!c.selected()) {
                continue;
            }
            // Dialect-aware so a computed column renders its dialect-specific SQL;
            // plain columns render identically to expressionFor(RolapStar.Column).
            SqlExpression colExpr = JoinPlanner.expressionFor(c.column().getExpression());
            ProjectionRef ref = q.project(colExpr, c.column().getInternalType(),
                    c.alias() == null ? null : ColumnAlias.of(c.alias()));
            if (c.ordered()) {
                // ORDER BY dedups by the rendered item: the alias when the dialect requires
                // ORDER-BY aliases (e.g. MySQL), else the column expression. Order on the projection
                // ref so the renderer spells it the same way. The signature is a DEDUP KEY only (never
                // emitted): a plain column uses its dialect-quoted spelling; a COMPUTED column must
                // not be dialect-string-rendered — its node (a value-equal RawVariant record) is the
                // key instead. MySQL (requiresOrderByAlias) always takes the alias branch.
                String sig;
                if (dialect.requiresOrderByAlias() && c.alias() != null) {
                    sig = c.alias();
                } else if (c.column().getExpression() instanceof org.eclipse.daanse.rolap.element.RolapColumn) {
                    sig = c.column().generateExprString(dialect);
                } else {
                    sig = colExpr.toString();
                }
                if (orderedColumns.add(sig)) {
                    q.orderOn(ref, org.eclipse.daanse.sql.statement.api.model.SortSpec.asc());
                }
            }
        }
        for (RawProjection p : rawProjections) {
            q.project(p.expr(), p.type(), p.alias() == null ? null : ColumnAlias.of(p.alias()), p.comment());
        }
        // Row limit inlined only for dialects that require MaxRows in the LIMIT clause; others
        // enforce it outside the SQL.
        if (rowLimit > 0 && dialect.requiresDrillthroughMaxRowsInLimit()) {
            q.rowLimit(rowLimit);
        }
        SelectStatement statement = q.build();
        if (org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.isDebugEnabled()) {
            int selected = (int) columns.stream().filter(DrillColumn::selected).count();
            new StatementBuildSummary("drill-through", "fact=" + fact.getAlias(), 0, rawProjections.size(),
                    extraFilters.size(), 0, 0, "builder-authoritative", null).log();
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.trace(
                    "drill-through: selectedColumns={} rawProjections={} rowLimit={}",
                    selected, rawProjections.size(), rowLimit);
        }
        return statement;
    }

    /** Emits {@code table}'s join chain up to (excluding) {@code fact}, parent
     *  condition first, each table once.
     *  {@code comment} names the join's cube-side reason (diagnostic render only; the first reason
     *  wins for a table shared by several chains — the join is emitted once). */
    private static void emitJoinChain(RolapStar.Table table, RolapStar.Table fact,
            Set<RolapStar.Table> emitted, SelectStatementBuilder q,
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect, String comment) {
        if (table == null || table == fact || !emitted.add(table)) {
            return;
        }
        // parent joined first (left-deep)
        emitJoinChain(table.getParentTable(), fact, emitted, q, dialect, comment);
        // ANSI JOIN…ON: the join predicate goes into the FROM tree, not WHERE.
        q.join(org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER,
                tableFromClause(table, dialect), JoinPlanner.joinPredicate(table.getJoinCondition()), comment);
        Predicate tw = tableWhere(table);
        if (tw != null) {
            q.where(tw);
        }
    }

    /** The star column's name for a provenance comment ({@code "column"} when unnamed). */
    private static String columnLabel(RolapStar.Column col) {
        return col.getName() != null ? col.getName() : "column";
    }

    /** {@code measure <name> (<agg>)} — null-tolerant (mock-built stars may carry no aggregator). */
    private static String measureComment(RolapStar.Measure m) {
        String agg = m.getAggregator() != null ? m.getAggregator().getName() : null;
        return "measure " + m.getName() + (agg != null ? " (" + agg + ")" : "");
    }

    /**
     * The FROM clause for a star table (fact or a joined dimension): a plain {@link From#table} for a
     * named table, or — when the table has no name (an inline {@code VALUES} table, or a view) — the
     * dialect-aware rendering of its relation via {@link RelationFromMapper#from}. Without
     * the latter branch a joined inline/view dimension renders as an empty FROM item ({@code , as
     * `alias`}) and the query is malformed.
     */
    private static FromClause tableFromClause(RolapStar.Table table,
            org.eclipse.daanse.jdbc.db.dialect.api.Dialect dialect) {
        String name = table.getTableName();
        if (name == null || name.isBlank()) {
            return RelationFromMapper.from(table.getRelation());
        }
        return From.table(name, TableAlias.of(table.getAlias()));
    }

    /** The table's own {@code sqlWhereExpression} as a parenthesised WHERE predicate (or
     *  {@code null}). The mapper fast path must emit it, else a fact/dimension table-level
     *  filter is silently dropped. */
    private static Predicate tableWhere(RolapStar.Table table) {
        if (table.getRelation() instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource ts
                && ts.getSqlWhereExpression() != null) {
            String sql = ts.getSqlWhereExpression().getSql();
            if (sql != null && !sql.isBlank()) {
                return Predicates.raw("(" + sql + ")");
            }
        }
        return null;
    }
}
