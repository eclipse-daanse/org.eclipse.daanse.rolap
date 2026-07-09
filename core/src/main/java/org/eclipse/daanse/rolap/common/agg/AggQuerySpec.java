/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2002-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * jhyde, 21 March, 2002
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

package org.eclipse.daanse.rolap.common.agg;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.sqlbuild.AggregateSqlMapper;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An AggStar's version of the {@link QuerySpec}.
 *
 * When/if the {@link AggStar} code is merged into {@link RolapStar}
 * (or RolapStar is merged into AggStar}, then this, indeed, can implement the
 * {@link QuerySpec} interface.
 *
 * @author Richard M. Emberson
 */
class AggQuerySpec {
    private static final Logger LOGGER = LoggerFactory.getLogger(AggQuerySpec.class);

    private final AggStar aggStar;
    private final List<Segment> segments;
    private final Segment segment0;
    private final boolean rollup;
    private final GroupingSetsList groupingSetsList;

    AggQuerySpec(
        final AggStar aggStar,
        final boolean rollup,
        GroupingSetsList groupingSetsList)
    {
        this.aggStar = aggStar;
        this.segments = groupingSetsList.getDefaultSegments();
        this.segment0 = segments.getFirst();
        this.rollup = rollup;
        this.groupingSetsList = groupingSetsList;
    }

    protected QueryRecorder newQuery() {
        return getStar().newQueryRecorder();
    }

    public RolapStar getStar() {
        return aggStar.getStar();
    }

    public int getMeasureCount() {
        return segments.size();
    }

    public AggStar.FactTable.Column getMeasureAsColumn(final int i) {
        int bitPos = segments.get(i).measure.getBitPosition();
        return aggStar.lookupColumn(bitPos);
    }

    public String getMeasureAlias(final int i) {
        return "m" + Integer.toString(i);
    }

    public int getColumnCount() {
        return segment0.getColumns().length;
    }

    public AggStar.Table.Column getColumn(final int i) {
        RolapStar.Column[] columns = segment0.getColumns();
        int bitPos = columns[i].getBitPosition();
        AggStar.Table.Column column = aggStar.lookupColumn(bitPos);

        // this should never happen
        if (column == null) {
            LOGGER.error("column null for bitPos={}", bitPos);
        }
        return column;
    }

    public String getColumnAlias(final int i) {
        return "c" + Integer.toString(i);
    }

    /**
     * Returns the predicate on the ith column.
     *
     * If the column is unconstrained, returns
     * {@link LiteralStarPredicate}(true).
     *
     * @param i Column ordinal
     * @return Constraint on column
     */
    public StarColumnPredicate getPredicate(int i) {
        return segment0.predicates[i];
    }

    public RenderedSql generateSql() {
        // AUTHORITATIVE builder for the plain rollup agg-table segment (the AbstractQuerySpec
        // fastpath pattern, agg-table twin). Declined shapes fall to the recorder below with a
        // debug-logged reason.
        RenderedSql built = tryBuilderAuthoritative();
        if (built != null) {
            return built;
        }
        // AUTHORITATIVE builder for the exact-granularity (non-rollup) agg-table segment — the twin
        // of the rollup path above. nonRollupSegmentStatement() returns null for the non-tractable
        // non-rollup shapes (grouping sets, distinct-rollup, FilterChildlessSnowflakeMembers=off,
        // missing/computed column, fake-column predicate); a translator/mapper RuntimeException
        // declines the same way. Declines fall to the recorder below with a debug-logged reason.
        try {
            org.eclipse.daanse.sql.statement.api.model.SelectStatement nonRollup =
                nonRollupSegmentStatement();
            if (nonRollup != null) {
                return org.eclipse.daanse.rolap.common.sqlbuild.SqlBuildGuard.build(
                    getStar().getDialect(),
                    getStar().getContext().getConfigValue(
                        org.eclipse.daanse.olap.common.ConfigConstants.GENERATE_FORMATTED_SQL,
                        org.eclipse.daanse.olap.common.ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE,
                        Boolean.class),
                    () -> nonRollup)
                    .render();
            }
        } catch (RuntimeException e) {
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                "agg segment: path=recorder reason={}", e.toString());
        }
        QueryRecorder query = newQuery();
        generateSql(query);
        return org.eclipse.daanse.rolap.common.SqlRender.render(query.buildStatement(), getStar().getDialect(), query.renderOptions());
    }

    /**
     * The AUTHORITATIVE builder for the exact-granularity (non-rollup) agg-table segment (built and
     * rendered by {@link #generateSql()}): the same constraining columns {@code c{i}} (agg-column
     * nodes + translated predicates) and FROM fold as {@link #tryBuilderAuthoritative()}, but the
     * measures {@code m{i}} carry their PLAIN
     * ({@link AggStar.FactTable.Measure#generateExpression()}) node and the columns are NOT grouped
     * (via {@link AggregateSqlMapper#aggSegment(java.util.List, java.util.List,
     * AggregateSqlMapper.AggFromTable, String, boolean)} with {@code rollup=false}) — mirroring the
     * recorder's {@code rollup==false} path. Returns {@code null} (recorder stays authoritative) for
     * the rollup shape (built by {@link #tryBuilderAuthoritative()}), grouping sets (a rollup
     * batching construct — unlike the rollup path it stays a decline here),
     * {@code FilterChildlessSnowflakeMembers=off}, a missing/computed agg column, a fake-column
     * predicate, a distinct or string-form measure.
     */
    private org.eclipse.daanse.sql.statement.api.model.SelectStatement nonRollupSegmentStatement() {
        if (rollup) {
            return null;
        }
        if (groupingSetsList.useGroupingSets()) {
            return null;
        }
        if (!org.eclipse.daanse.olap.common.SystemWideProperties.instance()
                .FilterChildlessSnowflakeMembers) {
            return null;
        }
        java.util.Map<AggStar.Table, AggregateSqlMapper.AggFromTable> tables =
            new java.util.IdentityHashMap<>();

        List<AggregateSqlMapper.AggSegmentColumn> columns = new ArrayList<>();
        int columnCnt = getColumnCount();
        for (int i = 0; i < columnCnt; i++) {
            AggStar.Table.Column column = getColumn(i);
            if (column == null) {
                return null;
            }
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
                column.toSqlExpression();
            StarColumnPredicate predicate = getPredicate(i);
            if (node == null || predicate.getConstrainedColumn() == null) {
                return null;
            }
            org.eclipse.daanse.sql.statement.api.expression.Predicate filter =
                org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue(predicate)
                    ? null
                    : org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator
                        .toColumnPredicate(predicate, node);
            columns.add(new AggregateSqlMapper.AggSegmentColumn(
                aggFromTable(column.getTable(), tables), node, column.getInternalType(), filter,
                column.getName() != null ? column.getName() : "column"));
        }

        List<AggregateSqlMapper.AggSegmentMeasure> measures = new ArrayList<>();
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            AggStar.FactTable.Measure measure =
                (AggStar.FactTable.Measure) getMeasureAsColumn(i);
            if (measure.isDistinct()) {
                return null;
            }
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
                measure.generateExpression();
            if (node == null) {
                return null;
            }
            RolapStar.Measure starMeasure = segments.get(i).measure;
            measures.add(new AggregateSqlMapper.AggSegmentMeasure(node,
                "measure " + starMeasure.getName()
                    + " (" + starMeasure.getAggregator().getName() + ")"));
        }

        AggregateSqlMapper.AggFromTable fact = aggFromTable(aggStar.getFactTable(), tables);
        String cubeName = getMeasureCount() > 0 ? segments.get(0).measure.getCubeName() : null;
        return AggregateSqlMapper.aggSegment(columns, measures, fact, cubeName, false);
    }

    /**
     * AUTHORITATIVE builder for the plain rollup aggregate-table segment load via
     * {@link AggregateSqlMapper#aggSegment}: group-by columns {@code c{i}} (agg-column nodes +
     * translated predicates), rolled-up measures {@code m{i}}
     * ({@link AggStar.FactTable.Measure#generateRollupExpression()}), FROM replayed structurally
     * (base = first referenced table, chains folded like the recorder's assembler). Returns
     * {@code null} — the recorder stays authoritative — for every shape outside the gate:
     * <ul>
     * <li>{@code rollup == false} (the exact-granularity read is built by
     *     {@link #nonRollupSegmentStatement()} instead),</li>
     * <li>{@code FilterChildlessSnowflakeMembers=false} (the recorder adds extra same-dimension
     *     joins on that setting),</li>
     * <li>a column/measure without a dialect-free node (a NULL column expression — the recorder
     *     cannot render it either), a non-table relation, or any translator/mapper
     *     {@link RuntimeException}.</li>
     * </ul>
     * Grouping sets are handled here: the batched multi-rollup shape is carried to the mapper as an
     * {@link AggregateSqlMapper.AggShape}, the renderer picking the per-dialect spelling exactly
     * like the base-star path. A fake-column predicate (no constrained column) is handled too: its
     * WHERE substitutes the value-constraint onto the agg column node via the fallback datatype,
     * carried as a conjunct.
     */
    private RenderedSql tryBuilderAuthoritative() {
        if (!rollup) {
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                "agg segment: path=recorder reason=non-rollup");
            return null;
        }
        if (!org.eclipse.daanse.olap.common.SystemWideProperties.instance()
                .FilterChildlessSnowflakeMembers) {
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                "agg segment: path=recorder reason=filter-childless-snowflake-members-off");
            return null;
        }
        try {
            org.eclipse.daanse.sql.statement.api.model.SelectStatement statement =
                rollupSegmentStatement();
            if (statement == null) {
                // The decline was already logged with its reason inside rollupSegmentStatement.
                return null;
            }
            return org.eclipse.daanse.rolap.common.sqlbuild.SqlBuildGuard.build(
                getStar().getDialect(),
                getStar().getContext().getConfigValue(
                    org.eclipse.daanse.olap.common.ConfigConstants.GENERATE_FORMATTED_SQL,
                    org.eclipse.daanse.olap.common.ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE,
                    Boolean.class),
                () -> statement)
                .render();
        } catch (RuntimeException e) {
            // A translator/mapper shape outside builder scope: the recorder builds this segment.
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                "agg segment: path=recorder reason={}", e.toString());
            return null;
        }
    }

    /**
     * The rolled-up segment assembly of the AUTHORITATIVE path
     * ({@link #tryBuilderAuthoritative()}): constraining columns {@code c{i}}, rolled-up measures
     * {@code m{i}}, FROM records — one {@link AggregateSqlMapper#aggSegment} statement. Returns
     * {@code null} (reason debug-logged) for the shapes outside builder scope; a translator/mapper
     * {@link RuntimeException} propagates to the caller.
     * <p>
     * A DISTINCT measure's rollup node comes from the same
     * {@link AggStar.FactTable.Measure#generateRollupExpression()} as every other measure — its
     * {@code getRollupAggregator()} already swaps an FK-based distinct measure to the NON-distinct
     * aggregator (count instead of sum) and a non-FK distinct measure to its rollup aggregator
     * ({@code sum} of the pre-aggregated counts) — dialect-free. This holds equally WITH grouping
     * sets: the distinct-rollup node flows into the {@link AggregateSqlMapper.AggShape}
     * grouping-sets form unchanged. Residue decline: a distinct measure whose swapped aggregator
     * has no node form ({@code measure-string-form}) — only the custom/user-template case, all
     * built-in aggregators having a node via the generic {@code AbstractAggregator.toNode} default.
     */
    private org.eclipse.daanse.sql.statement.api.model.SelectStatement rollupSegmentStatement()
    {
        // One shared AggFromTable per AggStar.Table (identity-keyed): the mapper's FROM fold
        // follows parent references through these records.
        java.util.Map<AggStar.Table, AggregateSqlMapper.AggFromTable> tables =
            new java.util.IdentityHashMap<>();

        List<AggregateSqlMapper.AggSegmentColumn> columns = new ArrayList<>();
        int columnCnt = getColumnCount();
        for (int i = 0; i < columnCnt; i++) {
            AggStar.Table.Column column = getColumn(i);
            if (column == null) {
                // lookupColumn miss (already error-logged): out of scope.
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                    "agg segment: path=recorder reason=missing-agg-column");
                return null;
            }
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
                column.toSqlExpression();
            StarColumnPredicate predicate = getPredicate(i);
            if (node == null) {
                // A COMPUTED agg column is handled by the normal path above — toSqlExpression models
                // it as an Expressions.rawVariant node (the per-dialect SQL map). node == null
                // therefore means a NULL column expression — a shape the recorder cannot render
                // either (SqlExpressionResolver.render throws); it stays declined.
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                    "agg segment: path=recorder reason=computed-column");
                return null;
            }
            // The agg column node IS the WHERE column too: for a fake-column predicate (no
            // constrained column) the node substitutes it via the fallback datatype; a predicate
            // carrying its own star column uses that column's type. Skipped when always-true, like
            // the recorder.
            org.eclipse.daanse.sql.statement.api.expression.Predicate filter =
                org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue(predicate)
                    ? null
                    : org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator
                        .toColumnPredicate(predicate, node, column.getDatatype());
            columns.add(new AggregateSqlMapper.AggSegmentColumn(
                aggFromTable(column.getTable(), tables), node, column.getInternalType(), filter,
                column.getName() != null ? column.getName() : "column"));
        }

        List<AggregateSqlMapper.AggSegmentMeasure> measures = new ArrayList<>();
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            AggStar.FactTable.Measure measure =
                (AggStar.FactTable.Measure) getMeasureAsColumn(i);
            // A DISTINCT measure co-occurring with grouping sets is handled: its rollup node comes
            // from the same generateRollupExpression() as every other measure (getRollupAggregator()
            // already swaps an FK distinct measure to count and a non-FK distinct measure to
            // sum-of-counts, dialect-free) and simply flows into the AggShape grouping-sets form
            // below. The grouping-sets machinery is measure-node-agnostic, so the batched
            // combination is the distinct-rollup node fed into the grouping-sets shape.
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
                measure.generateRollupExpression();
            if (node == null) {
                // Composite / non-node rollup aggregator: recorder's generateRollupString path.
                org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                    "agg segment: path=recorder reason=measure-string-form");
                return null;
            }
            RolapStar.Measure starMeasure = segments.get(i).measure;
            measures.add(new AggregateSqlMapper.AggSegmentMeasure(node,
                "measure " + starMeasure.getName()
                    + " (" + starMeasure.getAggregator().getName() + ")"));
        }

        AggregateSqlMapper.AggFromTable fact = aggFromTable(aggStar.getFactTable(), tables);
        String cubeName = getMeasureCount() > 0 ? segments.get(0).measure.getCubeName() : null;
        // The batched grouping-sets shape: each grouping-set entry / grouping(x) rollup column
        // resolved to its AGG column node — the node twin of the recorder's findColumnExpr
        // (generateExprString). An unresolvable column declines.
        AggregateSqlMapper.AggShape shape = AggregateSqlMapper.AggShape.PLAIN;
        if (groupingSetsList.useGroupingSets()) {
            List<List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression>> sets =
                new ArrayList<>();
            for (RolapStar.Column[] set : groupingSetsList.getGroupingSetsColumns()) {
                List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> keys =
                    new ArrayList<>();
                for (RolapStar.Column starColumn : set) {
                    org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
                        aggColumnNode(starColumn);
                    if (node == null) {
                        return null;
                    }
                    keys.add(node);
                }
                sets.add(keys);
            }
            List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> groupingFunctions =
                new ArrayList<>();
            for (RolapStar.Column starColumn : groupingSetsList.getRollupColumns()) {
                org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
                    aggColumnNode(starColumn);
                if (node == null) {
                    return null;
                }
                groupingFunctions.add(node);
            }
            shape = new AggregateSqlMapper.AggShape(sets, groupingFunctions);
        }
        return AggregateSqlMapper.aggSegment(columns, measures, fact, cubeName, true, shape);
    }

    /** The AGG column node for a base star column (grouping-sets keys / grouping(x) functions) —
     *  the node twin of {@link #findColumnExpr}; {@code null} (reason debug-logged) when the
     *  column has no agg node. */
    private org.eclipse.daanse.sql.statement.api.expression.SqlExpression aggColumnNode(
        RolapStar.Column starColumn)
    {
        AggStar.Table.Column column = aggStar.lookupColumn(starColumn.getBitPosition());
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
            column == null ? null : column.toSqlExpression();
        if (node == null) {
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                "agg segment: path=recorder reason=grouping-sets-column-unresolved");
        }
        return node;
    }

    /**
     * The {@link AggregateSqlMapper.AggFromTable} shadow of an {@link AggStar.Table} (memoized per
     * table so parent references share instances). Requires a named {@code TableSource} relation
     * and a plain-column join condition whose two sides live on exactly this table and its parent —
     * anything else throws {@link IllegalStateException}, which the fast path turns into a
     * recorder decline.
     */
    private AggregateSqlMapper.AggFromTable aggFromTable(
        AggStar.Table table,
        java.util.Map<AggStar.Table, AggregateSqlMapper.AggFromTable> tables)
    {
        AggregateSqlMapper.AggFromTable known = tables.get(table);
        if (known != null) {
            return known;
        }
        AggregateSqlMapper.AggFromTable parent =
            table.getParent() != null ? aggFromTable(table.getParent(), tables) : null;

        org.eclipse.daanse.sql.statement.api.expression.Predicate joinToParent = null;
        if (table.hasJoinCondition()) {
            if (parent == null) {
                throw new IllegalStateException("agg segment: join condition without parent on "
                    + table.getName());
            }
            AggStar.Table.JoinCondition jc = table.getJoinCondition();
            // Recorder parity: the fold keys join edges by the two sides' TABLE ALIASES; require
            // them to be plain columns on exactly {this table, parent} so the structural edge below
            // matches the recorder's alias-keyed edge.
            if (!(jc.getLeft() instanceof org.eclipse.daanse.rolap.element.RolapColumn left)
                || !(jc.getRight() instanceof org.eclipse.daanse.rolap.element.RolapColumn right)) {
                throw new IllegalStateException("agg segment: non-column join condition on "
                    + table.getName());
            }
            java.util.Set<String> sides = java.util.Set.of(left.getTable(), right.getTable());
            if (!sides.equals(java.util.Set.of(table.getName(), table.getParent().getName()))) {
                throw new IllegalStateException("agg segment: join condition aliases " + sides
                    + " do not match tables (" + table.getName() + ", "
                    + table.getParent().getName() + ")");
            }
            // Stored orientation (left = right), like the recorder's JoinEdge.
            joinToParent = org.eclipse.daanse.sql.statement.api.Predicates.comparison(
                org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(jc.getLeft()),
                org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(jc.getRight()));
        }

        AggregateSqlMapper.AggFromTable made = new AggregateSqlMapper.AggFromTable(
            table.getName(), aggTableFromClause(table), parent, joinToParent,
            aggTableFilter(table),
            table == aggStar.getFactTable() ? "agg table " + table.getName() : null);
        tables.put(table, made);
        return made;
    }

    /**
     * The FROM clause for an agg-star table: {@code [schema.]table AS <aggstar-table-name>} for a
     * named {@code TableSource} (the recorder's {@code addFromTable} spelling — the alias is the
     * AggStar table's name). View / inline relations are out of the fast path's scope and throw,
     * so the recorder keeps them.
     */
    private org.eclipse.daanse.sql.statement.api.model.FromClause aggTableFromClause(AggStar.Table table) {
        if (table.getRelation()
                instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource ts
            && ts.getTable() != null
            && ts.getTable().getName() != null && !ts.getTable().getName().isBlank()) {
            String schema = ts.getTable().getNamespace()
                instanceof org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema s
                    ? s.getName() : null;
            org.eclipse.daanse.sql.statement.api.model.TableAlias alias =
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of(table.getName());
            return schema == null
                ? org.eclipse.daanse.sql.statement.api.From.table(ts.getTable().getName(), alias)
                : org.eclipse.daanse.sql.statement.api.From.table(schema, ts.getTable().getName(), alias);
        }
        throw new IllegalStateException("agg segment: relation of " + table.getName()
            + " is not a named table");
    }

    /** The table's own {@code sqlWhereExpression} as a parenthesised WHERE predicate (or
     *  {@code null}) — the recorder adds it at {@code addFromTable} time, so the mapper must emit
     *  it at table-registration position. */
    private static org.eclipse.daanse.sql.statement.api.expression.Predicate aggTableFilter(
        AggStar.Table table)
    {
        if (table.getRelation()
                instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource ts
            && ts.getSqlWhereExpression() != null) {
            String sql = ts.getSqlWhereExpression().getSql();
            if (sql != null && !sql.isBlank()) {
                return org.eclipse.daanse.sql.statement.api.Predicates.raw("(" + sql + ")");
            }
        }
        return null;
    }

    private void addGroupingSets(QueryRecorder query) {
        List<RolapStar.Column[]> groupingSetsColumns =
            groupingSetsList.getGroupingSetsColumns();
        for (RolapStar.Column[] groupingSetColumns : groupingSetsColumns) {
            ArrayList<String> groupingColumnsExpr = new ArrayList<>();

            for (RolapStar.Column aColumnArr : groupingSetColumns) {
                groupingColumnsExpr.add(findColumnExpr(aColumnArr, query));
            }
            query.addGroupingSet(groupingColumnsExpr);
        }
    }

    private String findColumnExpr(RolapStar.Column columnj, QueryRecorder query) {
        AggStar.Table.Column column =
            aggStar.lookupColumn(columnj.getBitPosition());
        return column.generateExprString(getStar().getDialect());
    }

    protected void addMeasure(final int i, final QueryRecorder query) {
        AggStar.FactTable.Measure column =
                (AggStar.FactTable.Measure) getMeasureAsColumn(i);

        column.getTable().addToFrom(query, false, true);
        {
            // The measure lives on an aggregate fact table (an agg-table rewrite of the base star).
            query.commentFrom(column.getTable().getName(), "agg table " + column.getTable().getName());
        }
        String alias = getMeasureAlias(i);

        // Dialect-free when the (rollup or plain) measure column yields a simple node; else string.
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
            rollup ? column.generateRollupExpression() : column.generateExpression();
        if (node != null) {
            // Keep the explicit measure alias (m{i}); comment is added only when on (diagnostic copy only).
            String measureComment = null;
            {
                RolapStar.Measure measure = segments.get(i).measure;
                measureComment = "measure " + measure.getName()
                        + " (" + measure.getAggregator().getName() + ")";
            }
            query.addSelectNodeCommented(node, null, alias, measureComment);
            return;
        }
        String expr;
        if (rollup) {
            expr = column.generateRollupString(getStar().getDialect());
        } else {
            expr = column.generateExprString(getStar().getDialect());
        }
        query.addSelect(expr, null, alias);
    }

    protected void generateSql(final QueryRecorder query) {
        if (getMeasureCount() > 0) {
            query.setHeaderComment(
                "segment cube " + segments.get(0).measure.getCubeName() + " (agg table)");
            query.setFooterComment("segment request (agg table)");
        }
        // add constraining dimensions
        int columnCnt = getColumnCount();
        for (int i = 0; i < columnCnt; i++) {
            AggStar.Table.Column column = getColumn(i);
            AggStar.Table table = column.getTable();
            table.addToFrom(query, false, true);

            StarColumnPredicate predicate = getPredicate(i);
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression node = column.toSqlExpression();
            if (node != null) {
                // Dialect-free: use the agg column node for WHERE / SELECT / GROUP BY. A fake-column
                // predicate (no constrained column) substitutes this node via the fallback datatype,
                // skipped when always-true.
                if (!org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue(predicate)) {
                    query.addWhere(
                        org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator
                            .toColumnPredicate(predicate, node, column.getDatatype()));
                }
                final String alias = query.addSelectNode(node, column.getInternalType());
                if (rollup) {
                    query.addGroupByNode(node, alias);
                }
            } else {
                // node == null → getExpression() is null; generateExprString / SqlExpressionResolver
                // .render throws below (the recorder cannot render such a column either). Unreachable,
                // retained for structural parity — no predicate can render against a null expression.
                String expr = column.generateExprString(getStar().getDialect());
                final String alias =
                    query.addSelect(expr, column.getInternalType());
                if (rollup) {
                    query.addGroupBy(expr, alias);
                }
            }
        }

        // Add measures.
        // This can also add non-shared local dimension columns, which are
        // not measures.
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            addMeasure(i, query);
        }
        addGroupingSets(query);
        addGroupingFunction(query);
    }

    private void addGroupingFunction(QueryRecorder query) {
        List<RolapStar.Column> list = groupingSetsList.getRollupColumns();
        for (RolapStar.Column column : list) {
            query.addGroupingFunction(findColumnExpr(column, query));
        }
    }
}
