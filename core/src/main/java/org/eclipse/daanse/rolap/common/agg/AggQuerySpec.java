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

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
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
        this.segment0 = segments.get(0);
        this.rollup = rollup;
        this.groupingSetsList = groupingSetsList;
    }

    protected SqlQuery newSqlQuery() {
        return getStar().getSqlQuery();
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

    public Pair<String, List<BestFitColumnType>> generateSqlQuery() {
        SqlQuery sqlQuery = newSqlQuery();
        generateSql(sqlQuery);
        return sqlQuery.toSqlAndTypes();
    }

    private void addGroupingSets(SqlQuery sqlQuery) {
        List<RolapStar.Column[]> groupingSetsColumns =
            groupingSetsList.getGroupingSetsColumns();
        for (RolapStar.Column[] groupingSetColumns : groupingSetsColumns) {
            ArrayList<String> groupingColumnsExpr = new ArrayList<>();

            for (RolapStar.Column aColumnArr : groupingSetColumns) {
                groupingColumnsExpr.add(findColumnExpr(aColumnArr, sqlQuery));
            }
            sqlQuery.addGroupingSet(groupingColumnsExpr);
        }
    }

    private String findColumnExpr(RolapStar.Column columnj, SqlQuery sqlQuery) {
        AggStar.Table.Column column =
            aggStar.lookupColumn(columnj.getBitPosition());
        return column.generateExprString(sqlQuery);
    }

    protected void addMeasure(final int i, final SqlQuery query) {
        AggStar.FactTable.Measure column =
                (AggStar.FactTable.Measure) getMeasureAsColumn(i);

        column.getTable().addToFrom(query, false, true);
        String alias = getMeasureAlias(i);

        String expr;
        if (rollup) {
            expr = column.generateRollupString(query);
        } else {
            expr = column.generateExprString(query);
        }
        query.addSelect(expr, null, alias);
    }

    protected void generateSql(final SqlQuery sqlQuery) {
        // add constraining dimensions
        int columnCnt = getColumnCount();
        for (int i = 0; i < columnCnt; i++) {
            AggStar.Table.Column column = getColumn(i);
            AggStar.Table table = column.getTable();
            table.addToFrom(sqlQuery, false, true);

            String expr = column.generateExprString(sqlQuery);

            StarColumnPredicate predicate = getPredicate(i);
            final String where = RolapStar.Column.createInExpr(
                expr,
                predicate,
                column.getDatatype(),
                sqlQuery);
            if (!where.equals("true")) {
                sqlQuery.addWhere(where);
            }

            final String alias =
                sqlQuery.addSelect(expr, column.getInternalType());
            if (rollup) {
                sqlQuery.addGroupBy(expr, alias);
            }
        }

        // Add measures.
        // This can also add non-shared local dimension columns, which are
        // not measures.
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            addMeasure(i, sqlQuery);
        }
        addGroupingSets(sqlQuery);
        addGroupingFunction(sqlQuery);
    }

    private void addGroupingFunction(SqlQuery sqlQuery) {
        List<RolapStar.Column> list = groupingSetsList.getRollupColumns();
        for (RolapStar.Column column : list) {
            sqlQuery.addGroupingFunction(findColumnExpr(column, sqlQuery));
        }
    }
}
