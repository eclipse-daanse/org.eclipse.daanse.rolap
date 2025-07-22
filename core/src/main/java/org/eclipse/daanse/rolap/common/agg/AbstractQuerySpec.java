/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * Copyright (C) 2021 Sergei Semenkov
 * All Rights Reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.common.Util;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.StarPredicate;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;

/**
 * Base class for {@link QuerySpec} implementations.
 *
 * @author jhyde
 * @author Richard M. Emberson
 */
public abstract class AbstractQuerySpec implements QuerySpec {
    private final RolapStar star;
    protected final boolean countOnly;

    /**
     * Creates an AbstractQuerySpec.
     *
     * @param star Star which defines columns of interest and their
     * relationships
     *
     * @param countOnly If true, generate no GROUP BY clause, so the query
     * returns a single row containing a grand total
     */
    protected AbstractQuerySpec(final RolapStar star, boolean countOnly) {
        this.star = star;
        this.countOnly = countOnly;
    }

    /**
     * Creates a query object.
     *
     * @return a new query object
     */
    protected SqlQuery newSqlQuery() {
        return getStar().getSqlQuery();
    }

    @Override
	public RolapStar getStar() {
        return star;
    }

    /**
     * Adds a measure to a query.
     *
     * @param i Ordinal of measure
     * @param sqlQuery Query object
     */
    protected void addMeasure(final int i, final SqlQuery sqlQuery) {
        RolapStar.Measure measure = getMeasure(i);
        if (!isPartOfSelect(measure)) {
            return;
        }
        Util.assertTrue(measure.getTable() == getStar().getFactTable());
        measure.getTable().addToFrom(sqlQuery, false, true);

        String exprInner =
            measure.getExpression() == null
                ? "*"
                : measure.generateExprString(sqlQuery);
        StringBuilder exprOuter = measure.getAggregator().getExpression(exprInner);
        sqlQuery.addSelect(
            exprOuter,
            measure.getInternalType(),
            getMeasureAlias(i));
    }

    protected abstract boolean isAggregate();

    protected Map<String, String> nonDistinctGenerateSql(SqlQuery sqlQuery)
    {
        //First add fact table to From.
        getStar().getFactTable().addToFrom(sqlQuery, false, false);
        // add constraining dimensions
        RolapStar.Column[] columns = getColumns();
        int arity = columns.length;
        if (countOnly) {
            sqlQuery.addSelect("count(*)", BestFitColumnType.INT);
        }
        for (int i = 0; i < arity; i++) {
            RolapStar.Column column = columns[i];
            RolapStar.Table table = column.getTable();
            if (table.isFunky()) {
                // this is a funky dimension -- ignore for now
                continue;
            }
            table.addToFrom(sqlQuery, false, true);

            String expr = column.generateExprString(sqlQuery);

            StarColumnPredicate predicate = getColumnPredicate(i);
            final String where = RolapStar.Column.createInExpr(
                expr,
                predicate,
                column.getDatatype(),
                sqlQuery);
            if (!where.equals("true")) {
                sqlQuery.addWhere(where);
            }

            if (countOnly) {
                continue;
            }

            if (!isPartOfSelect(column)) {
                continue;
            }

            // some DB2 (AS400) versions throw an error, if a column alias is
            // there and *not* used in a subsequent order by/group by
            final Dialect dialect = sqlQuery.getDialect();
            final String alias =
                    sqlQuery.addSelect(expr, column.getInternalType(), dialect.allowsFieldAs() ? getColumnAlias(i) : null);

            if (isAggregate()) {
                sqlQuery.addGroupBy(expr, alias);
            }

            // Add ORDER BY clause to make the results deterministic.
            // Derby has a bug with ORDER BY, so ignore it.
            if (isOrdered()) {
                sqlQuery.addOrderBy(
                    expr,
                    alias,
                    true, false, false, true);
            }
        }

        // Add compound member predicates
        extraPredicates(sqlQuery);

        // add measures
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            addMeasure(i, sqlQuery);
        }

        return Collections.emptyMap();
    }

    /**
     * Allows subclasses to specify if a given column must
     * be returned as part of the result set, in the select clause.
     */
    @SuppressWarnings("java:S1172")
    protected boolean isPartOfSelect(RolapStar.Column col) {
        return true;
    }

    /**
     * Allows subclasses to specify if a given column must
     * be returned as part of the result set, in the select clause.
     */
    @SuppressWarnings("java:S1172")
    protected boolean isPartOfSelect(RolapStar.Measure measure) {
        return true;
    }

    /**
     * Whether to add an ORDER BY clause to make results deterministic.
     * Necessary if query returns more than one row and results are for
     * human consumption.
     *
     * @return whether to sort query
     */
    protected boolean isOrdered() {
        return false;
    }

    @Override
	public Pair<String, List<BestFitColumnType>> generateSqlQuery() {
        SqlQuery sqlQuery = newSqlQuery();

        int k = getDistinctMeasureCount();
        final Dialect dialect = sqlQuery.getDialect();
        final Map<String, String> groupingSetsAliases;
        if (!dialect.allowsCountDistinct() && k > 0
            || !dialect.allowsMultipleCountDistinct() && k > 1)
        {
            groupingSetsAliases =
                distinctGenerateSql(sqlQuery, countOnly);
        } else {
            groupingSetsAliases =
                nonDistinctGenerateSql(sqlQuery);
        }
        if (!countOnly) {
            addGroupingFunction(sqlQuery);
            addGroupingSets(sqlQuery, groupingSetsAliases);
        }
        return sqlQuery.toSqlAndTypes();
    }

    protected void addGroupingFunction(SqlQuery sqlQuery) {
        throw new UnsupportedOperationException();
    }

    protected void addGroupingSets(
        SqlQuery sqlQuery,
        Map<String, String> groupingSetsAliases)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the number of measures whose aggregation function is
     * distinct-count.
     *
     * @return Number of distinct-count measures
     */
    protected int getDistinctMeasureCount() {
        int k = 0;
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            RolapStar.Measure measure = getMeasure(i);
            if (measure.getAggregator().isDistinct()) {
                ++k;
            }
        }
        return k;
    }

    /**
     * Generates a SQL query to retrieve the values in this segment using
     * an algorithm which converts distinct-aggregates to non-distinct
     * aggregates over subqueries.
     *
     * @param outerSqlQuery Query to modify
     * @param countOnly If true, only generate a single row: no need to
     *   generate a GROUP BY clause or put any constraining columns in the
     *   SELECT clause
     * @return A map of aliases used in the inner query if grouping sets
     * were enabled.
     */
    protected Map<String, String> distinctGenerateSql(
        final SqlQuery outerSqlQuery,
        boolean countOnly)
    {
        final Dialect dialect = outerSqlQuery.getDialect();
        final Map<String, String> groupingSetsAliases =
            new HashMap<>();
        // Generate something like
        //
        //  select d0, d1, count(m0)
        //  from (
        //    select distinct dim1.x as d0, dim2.y as d1, f.z as m0
        //    from f, dim1, dim2
        //    where dim1.k = f.k1
        //    and dim2.k = f.k2) as dummyname
        //  group by d0, d1
        //
        // or, if countOnly=true
        //
        //  select count(m0)
        //  from (
        //    select distinct f.z as m0
        //    from f, dim1, dim2
        //    where dim1.k = f.k1
        //    and dim2.k = f.k2) as dummyname

        // GREENPLUM not support InnerDistinct
        final SqlQuery innerSqlQuery = newSqlQuery();
        innerSqlQuery.setDistinct(dialect.allowsInnerDistinct());

        // add constraining dimensions
        RolapStar.Column[] columns = getColumns();
        int arity = columns.length;
        for (int i = 0; i < arity; i++) {
            RolapStar.Column column = columns[i];
            RolapStar.Table table = column.getTable();
            if (table.isFunky()) {
                // this is a funky dimension -- ignore for now
                continue;
            }
            table.addToFrom(innerSqlQuery, false, true);
            String expr = column.generateExprString(innerSqlQuery);
            StarColumnPredicate predicate = getColumnPredicate(i);
            final String where = RolapStar.Column.createInExpr(
                expr,
                predicate,
                column.getDatatype(),
                innerSqlQuery);
            if (!where.equals("true")) {
                innerSqlQuery.addWhere(where);
            }
            if (countOnly) {
                continue;
            }
            String alias = "d" + i;
            alias = innerSqlQuery.addSelect(expr, null, alias);
            if (!dialect.allowsInnerDistinct()) {
                innerSqlQuery.addGroupBy(expr, alias);
            }
            final StringBuilder quotedAlias = dialect.quoteIdentifier(alias);
            outerSqlQuery.addSelectGroupBy(quotedAlias.toString(), null);
            // Add this alias to the map of grouping sets aliases
            groupingSetsAliases.put(
                expr,
                dialect.quoteIdentifier(
                    new StringBuilder("dummyname.").append(alias)).toString());
        }

        // add predicates not associated with columns
        extraPredicates(innerSqlQuery);

        // add measures
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            RolapStar.Measure measure = getMeasure(i);

            Util.assertTrue(measure.getTable() == getStar().getFactTable());
            measure.getTable().addToFrom(innerSqlQuery, false, true);

            String alias = getMeasureAlias(i);
            String expr = measure.generateExprString(outerSqlQuery);
            innerSqlQuery.addSelect(
                expr,
                measure.getInternalType(),
                alias);
            if (!dialect.allowsInnerDistinct()) {
                innerSqlQuery.addGroupBy(expr, alias);
            }
            outerSqlQuery.addSelect(
                measure.getAggregator().getNonDistinctAggregator()
                    .getExpression(dialect.quoteIdentifier(alias)),
                measure.getInternalType());
        }
        outerSqlQuery.addFrom(innerSqlQuery, "dummyname", true);
        return groupingSetsAliases;
    }

    /**
     * Adds predicates not associated with columns.
     *
     * @param sqlQuery Query
     */
    protected void extraPredicates(SqlQuery sqlQuery) {
        List<StarPredicate> predicateList = getPredicateList();
        for (StarPredicate predicate : predicateList) {
            for (RolapStar.Column column
                : predicate.getConstrainedColumnList())
            {
                final RolapStar.Table table = column.getTable();
                table.addToFrom(sqlQuery, false, true);
            }
            StringBuilder buf = new StringBuilder();
            predicate.toSql(sqlQuery, buf);
            final String where = buf.toString();
            if (!where.equals("true")) {
                sqlQuery.addWhere(where);
            }
        }
    }

    /**
     * Returns a list of predicates not associated with a particular column.
     *
     * @return list of non-column predicates
     */
    protected List<StarPredicate> getPredicateList() {
        return Collections.emptyList();
    }
}


// End AbstractQuerySpec.java
