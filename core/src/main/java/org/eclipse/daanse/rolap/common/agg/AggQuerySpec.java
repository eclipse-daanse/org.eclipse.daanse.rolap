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

import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
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
        QueryRecorder query = newQuery();
        generateSql(query);
        return query.toSqlAndTypes(getStar().getDialect());
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
            if (node != null && predicate.getConstrainedColumn() != null) {
                // Dialect-free: use the agg column node for WHERE / SELECT / GROUP BY.
                if (!org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue(predicate)) {
                    query.addWhere(
                        org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toColumnPredicate(predicate, node));
                }
                final String alias = query.addSelectNode(node, column.getInternalType());
                if (rollup) {
                    query.addGroupByNode(node, alias);
                }
            } else {
                // Computed agg column, or a predicate without a constrained column: raw-string path.
                String expr = column.generateExprString(getStar().getDialect());
                final String where = RolapStar.Column.createInExpr(
                    expr,
                    predicate,
                    column.getDatatype(),
                    getStar().getDialect());
                if (!where.equals("true")) {
                    query.addWhere(where);
                }
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
