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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
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
        Dialect dialect)
    {
        return aggregate(fact, groupBy, columnFilters, measures, dialect, java.util.List.of());
    }

    /** A compound (slicer) predicate not tied to a group-by column: the translated WHERE plus the star
     *  tables its constrained columns require joined to the fact (per predicate: join each
     *  constrained column's table, then the predicate WHERE). */
    public record ExtraConjunct(Predicate where, List<RolapStar.Table> joinTables) {
    }

    /**
     * The aggregate variants beyond the plain shape — countOnly, ordered, grouping-sets and rollup:
     * <ul>
     * <li>{@code countOnly} — the grand-total row: a single leading {@code count(*)} (auto-aliased
     *     {@code c0}) replaces the per-column SELECT / GROUP BY / ORDER BY; measures still follow
     *     (an inline {@code count(distinct col)} measure included — the distinct-SUBQUERY rewrite for
     *     dialects without {@code allowsCountDistinct} stays with the recorder, gated at the caller).
     *     Takes precedence over {@code ordered} and {@code groupingSets} (matching
     *     {@code AbstractQuerySpec.generateSql} emission order: grouping sets/functions are added
     *     only when not countOnly).</li>
     * <li>{@code ordered} — deterministic results: ORDER BY each group-by projection ascending
     *     (non-nullable, nulls-last flag set), deduped on the column node.</li>
     * <li>{@code groupingSets} — the batched multi-rollup segment load: each inner list is one
     *     {@code GROUPING SETS} entry. The plain group-by keys stay recorded alongside; the RENDERER
     *     spells {@code GROUP BY GROUPING SETS} when the dialect supports it and falls back to the
     *     plain keys otherwise — the capability is spelled at the renderer, never gated here (both
     *     the grouping-set entries and the plain keys are recorded, so either capability branch is
     *     covered).</li>
     * <li>{@code rollupColumns} — the {@code grouping(x)} super-aggregate SELECT-tail columns
     *     (auto-aliased {@code g{i}} by the renderer) that let the reader tell rolled-up rows
     *     apart.</li>
     * </ul>
     */
    public record Shape(boolean countOnly, boolean ordered,
            List<List<RolapStar.Column>> groupingSets, List<RolapStar.Column> rollupColumns,
            boolean distinctCountOnly) {

        /** Compatibility form: no distinct-countOnly. */
        public Shape(boolean countOnly, boolean ordered,
                List<List<RolapStar.Column>> groupingSets, List<RolapStar.Column> rollupColumns) {
            this(countOnly, ordered, groupingSets, rollupColumns, false);
        }

        /** The plain aggregate: no countOnly, no ORDER BY, no grouping sets. */
        public static final Shape PLAIN = new Shape(false, false, List.of(), List.of());
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
        Dialect dialect,
        List<ExtraConjunct> extras)
    {
        return aggregate(fact, groupBy, columnFilters, measures, dialect, extras, Shape.PLAIN);
    }

    /**
     * As {@link #aggregate(RolapStar.Table, List, List, List, Dialect, List)} but for an explicit
     * {@link Shape}: countOnly, ordered and/or grouping-sets aggregates. FROM and WHERE are identical to
     * the plain shape (countOnly still joins and constrains every group-by column — only the SELECT /
     * GROUP BY / ORDER BY parts differ).
     */
    public static SelectStatement aggregate(
        RolapStar.Table fact,
        List<RolapStar.Column> groupBy,
        List<Predicate> columnFilters,
        List<RolapStar.Measure> measures,
        Dialect dialect,
        List<ExtraConjunct> extras,
        Shape shape)
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

        // SELECT: group-by columns (grouped; ordered ascending when the shape asks), then aggregated
        // measures (aliased m{i}). countOnly replaces the per-column SELECT / GROUP BY / ORDER BY with
        // one leading count(*) (auto-aliased c0) while the measures still follow.
        if (shape.countOnly()) {
            // The DISTINCT-countOnly shape emits NO count(*): the legacy distinctGenerateSql
            // countOnly output is the nonDistinct re-aggregation of the distinct measures only
            // (the renderer's subquery rewrite reproduces it from the flat distinct measures).
            if (!shape.distinctCountOnly()) {
                q.project(Expressions.countStar(),
                        org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType.INT);
            }
        } else {
            // ORDER BY dedup on the (value-equal) column node: two group-by columns rendering the same
            // expression are ONE sort key (some dialects reject the duplicate).
            Set<SqlExpression> orderedNodes = new java.util.LinkedHashSet<>();
            for (RolapStar.Column col : groupBy) {
                SqlExpression node = JoinPlanner.expressionFor(col);
                ProjectionRef ref = q.project(node, col.getInternalType(), null,
                        "key " + columnLabel(col));
                q.groupOn(ref);
                if (shape.ordered() && orderedNodes.add(node)) {
                    // A non-nullable ascending key (no null-collation SQL is emitted for a
                    // non-nullable key; the nulls-last flag is carried on the sort spec).
                    q.orderOn(ref, new org.eclipse.daanse.sql.statement.api.model.SortSpec(
                            org.eclipse.daanse.sql.statement.api.model.SortDirection.ASC, false,
                            org.eclipse.daanse.sql.statement.api.model.NullOrder.LAST, false));
                }
            }
        }
        for (int i = 0; i < measures.size(); i++) {
            RolapStar.Measure m = measures.get(i);
            q.project(JoinPlanner.expressionFor(m, dialect), m.getInternalType(), ColumnAlias.of("m" + i),
                    measureComment(m));
        }
        // GROUPING SETS entries + grouping(x) SELECT-tail functions — skipped for countOnly (matching
        // AbstractQuerySpec.generateSql: added only when not countOnly). The plain group keys recorded
        // above stay: the renderer spells GROUP BY GROUPING SETS when the dialect supports it and falls
        // back to the plain keys otherwise (capability spelled at the RENDERER — see Shape).
        if (!shape.countOnly()) {
            for (List<RolapStar.Column> set : shape.groupingSets()) {
                List<SqlExpression> keys = new ArrayList<>();
                for (RolapStar.Column c : set) {
                    keys.add(JoinPlanner.expressionFor(c));
                }
                q.addGroupingSet(keys);
            }
            for (RolapStar.Column c : shape.rollupColumns()) {
                q.addGroupingFunction(JoinPlanner.expressionFor(c));
            }
        }
        SelectStatement statement = q.build();
        // Reached only on the builder fast path (AbstractQuerySpec.tryAggregateBuilder via QueryBuildContext.build),
        // so the path is always builder-authoritative here.
        if (org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.isDebugEnabled()) {
            int filterCount = columnFilters == null ? 0
                    : (int) columnFilters.stream().filter(java.util.Objects::nonNull).count();
            new StatementBuildSummary("aggregate", "fact=" + fact.getAlias() + shapeSuffix(shape),
                    groupBy.size(), measures.size(),
                    filterCount, joined.size(), 0, "builder-authoritative", null).log();
        }
        return statement;
    }

    /** Build-trace context suffix naming the non-plain shape parts (empty for the plain shape). */
    private static String shapeSuffix(Shape shape) {
        StringBuilder b = new StringBuilder();
        if (shape.countOnly()) {
            b.append(" shape=count-only");
        }
        if (shape.ordered()) {
            b.append(" shape=ordered");
        }
        if (!shape.groupingSets().isEmpty()) {
            b.append(" shape=grouping-sets(").append(shape.groupingSets().size()).append(')');
        }
        return b.toString();
    }

    /**
     * One FROM participant of an aggregate-table segment load — the engine-free counterpart of an
     * {@code AggStar.Table}: the {@code alias}, its rendered {@code from} clause,
     * the {@code parent} toward the aggregate fact table ({@code null} for the fact itself), the
     * {@code joinToParent} ON predicate in the join condition's <em>stored</em> orientation
     * (fact-side {@code left = right} dim-side; {@code null} for the fact), an optional
     * {@code tableFilter} (the table's own {@code sqlWhereExpression}, pre-parenthesised) and an
     * optional provenance {@code comment} (diagnostic render only).
     * <p>
     * Instances must be shared: one record per agg-star table, with {@code parent} pointing at the
     * caller's instance for that table — the FROM fold tracks identity through these records.
     */
    public record AggFromTable(String alias, FromClause from, AggFromTable parent,
            Predicate joinToParent, Predicate tableFilter, String comment) {
    }

    /** A constraining (group-by) column of an aggregate-table segment: the {@link AggFromTable} it
     *  lives on, its dialect-free column {@code node}, the result-reading type, an optional
     *  translated constraint (a {@code null} filter means unconstrained) and a provenance label. */
    public record AggSegmentColumn(AggFromTable table, SqlExpression node,
            org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType type, Predicate filter,
            String label) {
    }

    /** A rolled-up measure of an aggregate-table segment: the pre-built aggregator node (the
     *  {@code AggStar.FactTable.Measure.generateRollupExpression()} result) and a provenance
     *  comment. Aliased {@code m{i}}; no result type is declared for agg measures (the type is
     *  guessed in {@code RenderedSql.types()}). */
    public record AggSegmentMeasure(SqlExpression node, String comment) {
    }

    /**
     * The agg-table twin of {@link Shape} for the batched multi-rollup segment load: each inner
     * {@code groupingSets} list is one {@code GROUPING SETS} entry (agg-column nodes),
     * {@code groupingFunctions} are the {@code grouping(x)} super-aggregate SELECT-tail columns
     * (renderer-aliased {@code g{i}}). Exactly like the base-star path, the plain group-by keys stay
     * recorded alongside and the RENDERER picks the spelling ({@code GROUP BY GROUPING SETS} when the
     * dialect supports it, the plain keys otherwise) — the capability is never gated in the mapper.
     */
    public record AggShape(List<List<SqlExpression>> groupingSets,
            List<SqlExpression> groupingFunctions) {

        /** The plain rolled-up segment: no grouping sets, no grouping functions. */
        public static final AggShape PLAIN = new AggShape(List.of(), List.of());
    }

    /**
     * The rolled-up aggregate-table segment SELECT — the counterpart of
     * {@code AggQuerySpec.generateSql} for the plain rollup shape (no grouping sets, no
     * distinct-rollup): {@code SELECT c0..cn, m0..mk FROM <first referenced table> JOIN … WHERE
     * <filters> GROUP BY c0..cn}.
     * <p>
     * FROM parity with the recorder is structural, not fact-rooted: the recorder's
     * {@code AggStar.Table.addToFrom} registers each constraining column's table chain
     * <em>self-first</em> (table, then its parents up to the aggregate fact) and records each
     * dim table's join edge parent-first; the assembler then folds the registered tables in
     * registration order, joining each pending table by the first recorded edge whose other
     * endpoint is already placed. This method replays exactly that: the base of the FROM tree is
     * the FIRST registered table (a dimension table when the first constraining column lives on
     * one — e.g. {@code FROM "store" JOIN "agg_c_14_…" ON …}), and a snowflake chain registered
     * self-first still joins parent-first ({@code JOIN "product" … JOIN "product_class" …}).
     * WHERE order is tape order: each newly registered table's own filter, then that column's
     * constraint; the fold must consume every join edge and every table (a disconnected or
     * diamond-shaped graph throws {@link IllegalStateException} — the caller declines to the
     * recorder).
     *
     * @param columns   constraining columns in segment order (selected {@code c{i}}, grouped)
     * @param measures  rolled-up measures in segment order (selected {@code m{i}})
     * @param factTable the aggregate fact table (the measures' home; joined even when no
     *                  constraining column references it)
     * @param cubeName  provenance only (header comment), may be {@code null}
     */
    public static SelectStatement aggSegment(
        List<AggSegmentColumn> columns,
        List<AggSegmentMeasure> measures,
        AggFromTable factTable,
        String cubeName)
    {
        return aggSegment(columns, measures, factTable, cubeName, true);
    }

    /**
     * As {@link #aggSegment(List, List, AggFromTable, String)} but with an explicit {@code rollup}
     * flag. {@code rollup == true} is the authoritative rolled-up shape (every constraining column
     * grouped, measures carry rolled-up aggregator nodes). {@code rollup == false} is the
     * exact-granularity read — the constraining columns are projected but NOT grouped and the
     * measures carry their plain (un-rolled) node — the exact-granularity segment shape.
     */
    public static SelectStatement aggSegment(
        List<AggSegmentColumn> columns,
        List<AggSegmentMeasure> measures,
        AggFromTable factTable,
        String cubeName,
        boolean rollup)
    {
        return aggSegment(columns, measures, factTable, cubeName, rollup, AggShape.PLAIN);
    }

    /**
     * As {@link #aggSegment(List, List, AggFromTable, String, boolean)} but for an explicit
     * {@link AggShape}: the batched grouping-sets segment load. The grouping-set entries and the
     * {@code grouping(x)} SELECT-tail functions are emitted after the measures — matching
     * {@code AggQuerySpec.generateSql}'s emission order (columns, measures, {@code addGroupingSets},
     * {@code addGroupingFunction}).
     */
    public static SelectStatement aggSegment(
        List<AggSegmentColumn> columns,
        List<AggSegmentMeasure> measures,
        AggFromTable factTable,
        String cubeName,
        boolean rollup,
        AggShape shape)
    {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        if (!measures.isEmpty() && cubeName != null) {
            q.header("segment cube " + cubeName + " (agg table)");
        }
        q.footerComment("segment request (agg table)");

        // Tape emulation: table registration order (self-first chains), join edges (parent-first,
        // deduped) and the interleaved WHERE items ([table filter(s), column constraint] per column).
        java.util.LinkedHashSet<AggFromTable> tableOrder = new java.util.LinkedHashSet<>();
        java.util.Map<String, AggFromTable> byAlias = new java.util.HashMap<>();
        java.util.Map<String, List<AggFromTable>> edgesByAlias = new java.util.HashMap<>();
        java.util.LinkedHashSet<AggFromTable> edgeOwners = new java.util.LinkedHashSet<>();
        record WhereItem(Predicate predicate, String comment) {
        }
        List<WhereItem> wheres = new ArrayList<>();
        for (AggSegmentColumn c : columns) {
            registerAggChain(c.table(), tableOrder, byAlias, edgesByAlias, edgeOwners,
                    (p) -> wheres.add(new WhereItem(p, null)));
            if (c.filter() != null) {
                wheres.add(new WhereItem(c.filter(), "context " + c.label()));
            }
        }
        if (!measures.isEmpty()) {
            registerAggChain(factTable, tableOrder, byAlias, edgesByAlias, edgeOwners,
                    (p) -> wheres.add(new WhereItem(p, null)));
        }
        if (tableOrder.isEmpty()) {
            throw new IllegalStateException("agg segment: no tables referenced");
        }

        // FROM fold: base = first registered table; each pending table joins by the
        // first recorded edge whose other endpoint is placed (edge orientation kept as stored).
        java.util.Iterator<AggFromTable> order = tableOrder.iterator();
        AggFromTable base = order.next();
        q.from(base.comment() != null ? From.commentBase(base.from(), base.comment()) : base.from());
        Set<String> placed = new java.util.HashSet<>();
        placed.add(base.alias());
        List<AggFromTable> pending = new ArrayList<>();
        order.forEachRemaining(pending::add);
        java.util.Set<AggFromTable> usedEdges = new java.util.HashSet<>();
        boolean progress = true;
        while (!pending.isEmpty() && progress) {
            progress = false;
            for (java.util.Iterator<AggFromTable> it = pending.iterator(); it.hasNext();) {
                AggFromTable t = it.next();
                AggFromTable edgeOwner = null;
                for (AggFromTable owner : edgesByAlias.getOrDefault(t.alias(), List.of())) {
                    String other = owner.alias().equals(t.alias())
                            ? owner.parent().alias() : owner.alias();
                    if (placed.contains(other)) {
                        edgeOwner = owner;
                        break;
                    }
                }
                if (edgeOwner != null) {
                    q.join(org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER, t.from(),
                            edgeOwner.joinToParent(), t.comment());
                    placed.add(t.alias());
                    usedEdges.add(edgeOwner);
                    it.remove();
                    progress = true;
                }
            }
        }
        if (!pending.isEmpty()) {
            // The recorder would render this as a comma-product; out of builder scope — decline.
            throw new IllegalStateException("agg segment: join graph not connected from "
                    + base.alias());
        }
        if (usedEdges.size() != edgeOwners.size()) {
            // A diamond edge the tree could not carry would leak to WHERE in the assembler — decline.
            throw new IllegalStateException("agg segment: join edge not folded into the tree");
        }

        // WHERE in tape order (table filters interleaved with column constraints).
        for (WhereItem w : wheres) {
            if (w.comment() != null) {
                q.where(w.predicate(), w.comment());
            } else {
                q.where(w.predicate());
            }
        }

        // SELECT: constraining columns (auto-aliased c{i}, grouped only for the rollup shape), then
        // the measures (aliased m{i}, no declared type).
        for (AggSegmentColumn c : columns) {
            ProjectionRef ref = q.project(c.node(), c.type(), null, "key " + c.label());
            if (rollup) {
                q.groupOn(ref);
            }
        }
        for (int i = 0; i < measures.size(); i++) {
            AggSegmentMeasure m = measures.get(i);
            q.project(m.node(), null, ColumnAlias.of("m" + i), m.comment());
        }
        // GROUPING SETS entries + grouping(x) SELECT-tail functions, after the measures (matching
        // AggQuerySpec.generateSql order). The plain group keys recorded above stay: the renderer
        // spells GROUP BY GROUPING SETS when the dialect supports it and falls back to the plain keys
        // otherwise (capability spelled at the RENDERER — see AggShape; same contract as the base-star
        // Shape).
        for (List<SqlExpression> set : shape.groupingSets()) {
            q.addGroupingSet(set);
        }
        for (SqlExpression g : shape.groupingFunctions()) {
            q.addGroupingFunction(g);
        }
        SelectStatement statement = q.build();
        // rollup==true is the builder fast path (AggQuerySpec.tryBuilderAuthoritative via
        // QueryBuildContext.build) — builder-authoritative; rollup==false is the
        // exact-granularity segment shape.
        if (org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.isDebugEnabled()) {
            int filterCount = (int) columns.stream().filter(c -> c.filter() != null).count();
            new StatementBuildSummary("agg-segment",
                    "fact=" + factTable.alias()
                        + (rollup ? "" : " shape=exact-granularity")
                        + (shape.groupingSets().isEmpty() ? ""
                            : " shape=grouping-sets(" + shape.groupingSets().size() + ")"),
                    columns.size(), measures.size(), filterCount, tableOrder.size(), 0,
                    "builder-authoritative", null)
                    .log();
        }
        return statement;
    }

    /**
     * Replays {@code AggStar.Table.addToFrom(query, false, true)} for one table: register the table
     * (self-first; its own filter becomes a WHERE item at first registration), recurse to the
     * parent, then record the table's join edge under both endpoint aliases (parent-first edge
     * order, once per table). Two distinct tables sharing an alias would silently dedup in the
     * recorder — here it throws so the caller declines.
     */
    private static void registerAggChain(AggFromTable t,
            java.util.LinkedHashSet<AggFromTable> tableOrder,
            java.util.Map<String, AggFromTable> byAlias,
            java.util.Map<String, List<AggFromTable>> edgesByAlias,
            java.util.LinkedHashSet<AggFromTable> edgeOwners,
            java.util.function.Consumer<Predicate> tableFilterSink) {
        AggFromTable known = byAlias.putIfAbsent(t.alias(), t);
        if (known != null && !known.equals(t)) {
            throw new IllegalStateException("agg segment: alias collision on " + t.alias());
        }
        if (tableOrder.add(t) && t.tableFilter() != null) {
            tableFilterSink.accept(t.tableFilter());
        }
        if (t.parent() == null) {
            return;
        }
        registerAggChain(t.parent(), tableOrder, byAlias, edgesByAlias, edgeOwners, tableFilterSink);
        if (t.joinToParent() != null && edgeOwners.add(t)) {
            edgesByAlias.computeIfAbsent(t.alias(), k -> new ArrayList<>()).add(t);
            edgesByAlias.computeIfAbsent(t.parent().alias(), k -> new ArrayList<>()).add(t);
        }
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
            org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType type, String alias, String comment) {
        public RawProjection(SqlExpression expr,
                org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType type, String alias) {
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
        Dialect dialect)
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
            q.project(Expressions.countStar(), org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType.INT);
            return q.build();
        }

        // SELECT: selected columns (named alias); ORDER BY each selected column, deduped by the
        // COLUMN NODE's value equality (the same expression must not be ordered twice — a duplicate
        // is illegal on e.g. MSSQL and redundant everywhere); then the raw projections. The node key
        // is dialect-free: it subsumes the former requiresOrderByAlias/rendered-string signatures
        // (an alias-keyed dedup only approximated expression identity, and collapsing two aliases
        // of the same expression to one sort key is a semantic no-op).
        Set<SqlExpression> orderedColumns = new java.util.LinkedHashSet<>();
        for (DrillColumn c : columns) {
            if (!c.selected()) {
                continue;
            }
            // Dialect-aware so a computed column renders its dialect-specific SQL;
            // plain columns render identically to expressionFor(RolapStar.Column).
            SqlExpression colExpr = JoinPlanner.expressionFor(c.column().getExpression());
            ProjectionRef ref = q.project(colExpr, c.column().getInternalType(),
                    c.alias() == null ? null : ColumnAlias.of(c.alias()));
            if (c.ordered() && orderedColumns.add(colExpr)) {
                q.orderOn(ref, org.eclipse.daanse.sql.statement.api.model.SortSpec.asc());
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
            Dialect dialect, String comment) {
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
            Dialect dialect) {
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
