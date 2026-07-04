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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner;
import org.eclipse.daanse.rolap.common.sqlbuild.RelationFromMapper;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
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
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
        String alias,
        long approxRowCount)
    {
        if (approxRowCount >= 0) {
            return approxRowCount;
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table) {
            return getTableCardinality(
                null, table.getTable());
        } else {
            final Dialect dialect = star.getDialect();
            SelectStatementBuilder q = SelectStatementBuilder.create();
            org.eclipse.daanse.sql.statement.api.model.FromClause from = RelationFromMapper.from(relation);
            // Diagnostic provenance (rendered only when comments are on; never part of the executed
            // SQL): this whole-relation read is the statistics cache's row-count probe input — the
            // provider wraps it as `select count(*) from (<this>)`.
            q.header("table cardinality " + relationName(alias, from));
            q.footerComment("cardinality probe (count rows)");
            q.from(from);
            q.project(Expressions.star(), null);
            return getQueryCardinality(SqlRender.render(q.build(), dialect).sql());
        }
    }

    private long getTableCardinality(
        String catalog,
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet table)
    {
    	String schema = table.getNamespace() != null ? table.getNamespace().getName() : null;
        final List<String> key = Arrays.asList(catalog, schema, table.getName());
        long rowCount = -1;
        if (tableMap.containsKey(key)) {
            rowCount = tableMap.get(key);
        } else {
            final Dialect dialect = star.getDialect();
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
            final Dialect dialect = star.getDialect();
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
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
        SqlExpression expression,
        long approxCardinality)
    {
        if (approxCardinality >= 0) {
            return approxCardinality;
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table
            && expression instanceof org.eclipse.daanse.rolap.element.RolapColumn column)
        {
            return getColumnCardinality(
                null,
                table.getTable(),
                column.getName());
        } else {
            final Dialect dialect = star.getDialect();
            SelectStatementBuilder q = SelectStatementBuilder.create();
            org.eclipse.daanse.sql.statement.api.model.FromClause from = RelationFromMapper.from(relation);
            // Diagnostic provenance: the distinct-values read the statistics cache wraps as
            // `select count(*) from (<this>)` — a count-distinct probe for one column/expression.
            q.header("column cardinality " + columnName(expression, from));
            q.footerComment("cardinality probe (count distinct values)");
            q.distinct(true);
            q.from(from);
            q.project(JoinPlanner.expressionFor(expression), null);
            return getQueryCardinality(SqlRender.render(q.build(), dialect).sql());
        }
    }

    private long getColumnCardinality(
        String catalog,
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet table,
        String column)
    {
    	String schema = table.getNamespace() != null ? table.getNamespace().getName() : null;
        final List<String> key = Arrays.asList(catalog, schema, table.getName(), column);
        long rowCount = -1;
        if (columnMap.containsKey(key)) {
            rowCount = columnMap.get(key);
        } else {
            final Dialect dialect = star.getDialect();
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

    /** The provenance name for the row-count probe: the caller's alias, else the FROM base alias. */
    private static String relationName(String alias,
            org.eclipse.daanse.sql.statement.api.model.FromClause from) {
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        org.eclipse.daanse.sql.statement.api.model.TableAlias base =
                org.eclipse.daanse.sql.statement.api.From.baseAlias(from);
        return base != null ? base.name() : "relation";
    }

    /** The provenance name for the count-distinct probe: table.column for a plain column, else the base alias. */
    private static String columnName(SqlExpression expression,
            org.eclipse.daanse.sql.statement.api.model.FromClause from) {
        if (expression instanceof org.eclipse.daanse.rolap.element.RolapColumn column) {
            return column.getTable() != null
                    ? column.getTable() + "." + column.getName() : column.getName();
        }
        return "expression on " + relationName(null, from);
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
