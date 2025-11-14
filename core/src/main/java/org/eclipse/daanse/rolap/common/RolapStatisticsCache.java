/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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

package org.eclipse.daanse.rolap.common;

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.getExpression;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import  org.eclipse.daanse.olap.server.ExecutionImpl;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.sql.SqlStatisticsProviderNew;


/**
 * Provides and caches statistics.
 *
 * Wrapper around a chain of org.eclipse.daanse.olap.spi.StatisticsProvider s,
 * followed by a cache to store the results.
 */
public class RolapStatisticsCache {
    private final RolapStar star;
    private final Map<List, Long> columnMap = new HashMap<>();
    private final Map<List, Long> tableMap = new HashMap<>();
    private final Map<String, Long> queryMap =
        new HashMap<>();

    public RolapStatisticsCache(RolapStar star) {
        this.star = star;
    }

    public long getRelationCardinality(
        org.eclipse.daanse.rolap.mapping.model.RelationalQuery relation,
        String alias,
        long approxRowCount)
    {
        if (approxRowCount >= 0) {
            return approxRowCount;
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.TableQuery table) {
            return getTableCardinality(
                null, table.getTable());
        } else {
            final SqlQuery sqlQuery = star.getSqlQuery();
            sqlQuery.addSelect("*", null);
            sqlQuery.addFrom(relation, null, true);
            return getQueryCardinality(sqlQuery.toString());
        }
    }

    private long getTableCardinality(
        String catalog,
        org.eclipse.daanse.rolap.mapping.model.Table table)
    {
    	String schema = table.getSchema() != null ? table.getSchema().getName() : null;
        final List<String> key = Arrays.asList(catalog, schema, table.getName());
        long rowCount = -1;
        if (tableMap.containsKey(key)) {
            rowCount = tableMap.get(key);
        } else {
            final Dialect dialect = star.getSqlQueryDialect();
            //final List<StatisticsProvider> statisticsProviders =
            //    dialect.getStatisticsProviders();
            final List<SqlStatisticsProviderNew> statisticsProviders = List.of(new SqlStatisticsProviderNew());
            final ExecutionImpl execution =
                new ExecutionImpl(
                    star.getCatalog().getInternalConnection()
                        .getInternalStatement(),
                    ExecuteDurationUtil.executeDurationValue(star.getCatalog().getInternalConnection().getContext()));
            for (SqlStatisticsProviderNew statisticsProvider : statisticsProviders) {
                rowCount = statisticsProvider.getTableCardinality(
                    star.getContext(),
                    catalog,
                    schema,
                    table.getName(),
                    execution);
                if (rowCount >= 0) {
                    break;
                }
            }

            // Note: If all providers fail, we put -1 into the cache, to ensure
            // that we won't try again.
            tableMap.put(key, rowCount);
        }
        return rowCount;
    }

    private long getQueryCardinality(String sql) {
        long rowCount = -1;
        if (queryMap.containsKey(sql)) {
            rowCount = queryMap.get(sql);
        } else {
            final Dialect dialect = star.getSqlQueryDialect();
            //final List<StatisticsProvider> statisticsProviders =
            //    dialect.getStatisticsProviders();
            final List<SqlStatisticsProviderNew> statisticsProviders = List.of(new SqlStatisticsProviderNew());
            final ExecutionImpl execution =
                new ExecutionImpl(
                    star.getCatalog().getInternalConnection()
                        .getInternalStatement(),
                        ExecuteDurationUtil.executeDurationValue(star.getCatalog().getInternalConnection().getContext()));
            for (SqlStatisticsProviderNew statisticsProvider : statisticsProviders) {
                rowCount = statisticsProvider.getQueryCardinality( star.getContext(), sql, execution);
                if (rowCount >= 0) {
                    break;
                }
            }

            // Note: If all providers fail, we put -1 into the cache, to ensure
            // that we won't try again.
            queryMap.put(sql, rowCount);
        }
        return rowCount;
    }

    public long getColumnCardinality(
        org.eclipse.daanse.rolap.mapping.model.RelationalQuery relation,
        SqlExpression expression,
        long approxCardinality)
    {
        if (approxCardinality >= 0) {
            return approxCardinality;
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.TableQuery table
            && expression instanceof org.eclipse.daanse.rolap.element.RolapColumn column)
        {
            return getColumnCardinality(
                null,
                table.getTable(),
                column.getName());
        } else {
            final SqlQuery sqlQuery = star.getSqlQuery();
            sqlQuery.setDistinct(true);
            sqlQuery.addSelect(getExpression( expression, sqlQuery), null);
            sqlQuery.addFrom(relation, null, true);
            return getQueryCardinality(sqlQuery.toString());
        }
    }

    private long getColumnCardinality(
        String catalog,
        org.eclipse.daanse.rolap.mapping.model.Table table,
        String column)
    {
    	String schema = table.getSchema() != null ? table.getSchema().getName() : null;
        final List<String> key = Arrays.asList(catalog, schema, table.getName(), column);
        long rowCount = -1;
        if (columnMap.containsKey(key)) {
            rowCount = columnMap.get(key);
        } else {
            final Dialect dialect = star.getSqlQueryDialect();
            final List<SqlStatisticsProviderNew> statisticsProviders = List.of(new SqlStatisticsProviderNew());
            //final List<StatisticsProvider> statisticsProviders =
            //    dialect.getStatisticsProviders();
            final ExecutionImpl execution =
                new ExecutionImpl(
                    star.getCatalog().getInternalConnection()
                        .getInternalStatement(),
                        ExecuteDurationUtil.executeDurationValue(star.getCatalog().getInternalConnection().getContext()));
            for (SqlStatisticsProviderNew statisticsProvider : statisticsProviders) {
                rowCount = statisticsProvider.getColumnCardinality(
                    star.getContext(),
                    catalog,
                    schema,
                    table.getName(),
                    column,
                    execution);
                if (rowCount >= 0) {
                    break;
                }
            }

            // Note: If all providers fail, we put -1 into the cache, to ensure
            // that we won't try again.
            columnMap.put(key, rowCount);
        }
        return rowCount;
    }

    public int getColumnCardinality2(
        DataSource dataSource,
        Dialect dialect,
        String catalog,
        String schema,
        String table,
        String column)
    {
        return -1;
    }
}
