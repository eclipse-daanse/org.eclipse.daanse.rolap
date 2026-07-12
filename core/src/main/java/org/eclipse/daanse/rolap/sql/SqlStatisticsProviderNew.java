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


package org.eclipse.daanse.rolap.sql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.execution.Execution.Purpose;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.SqlRender;
import org.eclipse.daanse.rolap.common.SqlStatement;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/**
 * Generates and executes the statistics probes (row counts and distinct-value counts) on the
 * dialect-free statement model: the probe is built as a {@link SelectStatement} and spelled once
 * by the renderer — identifier quoting, FROM-subquery aliasing and the count-distinct
 * degradation (a dialect that cannot execute {@code count(distinct x)} inline gets the nested
 * derived-table form) all live behind {@code SqlRender}, not here.
 */
public class SqlStatisticsProviderNew  {

    /** The derived-table alias of the query-cardinality probe (historic spelling kept). */
    private static final TableAlias PROBE_ALIAS = TableAlias.of("init");

    public long getTableCardinality(
        Context context,
        String schema,
        String table,
        ExecutionImpl execution)
    {
        return executeCountQuery(context, tableCardinalityStatement(schema, table), execution,
            "SqlStatisticsProviderNew.getTableCardinality",
            "Reading row count from table " + table);
    }

    public long getQueryCardinality(
        Context context,
        String sql,
        ExecutionImpl execution)
    {
        return executeCountQuery(context, queryCardinalityStatement(sql), execution,
            "SqlStatisticsProviderNew.getQueryCardinality",
            "Reading row count from query");
    }

    public long getColumnCardinality(
        Context context,
        String schema,
        String table,
        String column,
        ExecutionImpl execution)
    {
        // A dialect that supports neither count(distinct) nor a SELECT in the FROM clause cannot
        // express the probe at all (the renderer's degradation needs the derived-table form) —
        // the historic -1 contract: no query is issued.
        if (!context.getDialect().allowsCountDistinct() && !context.getDialect().allowsFromQuery()) {
            return -1;
        }
        return executeCountQuery(context, columnCardinalityStatement(schema, table, column), execution,
            "SqlStatisticsProviderNew.getColumnCardinality",
            "Reading cardinality for column " + table + "." + column);
    }

    static SelectStatement tableCardinalityStatement(String schema, String table) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.project(Expressions.countStar(), BestFitColumnType.LONG);
        q.from(From.table(schema, table, TableAlias.of(table)));
        return q.build();
    }

    static SelectStatement queryCardinalityStatement(String sql) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.project(Expressions.countStar(), BestFitColumnType.LONG);
        q.from(From.raw(sql, PROBE_ALIAS));
        return q.build();
    }

    static SelectStatement columnCardinalityStatement(String schema, String table, String column) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        // Canonical flat count(distinct col); the renderer degrades it to the nested
        // derived-table form for a dialect that cannot execute it inline.
        q.project(Expressions.countDistinct(Expressions.column(column)), BestFitColumnType.LONG);
        q.from(From.table(schema, table, TableAlias.of(table)));
        return q.build();
    }

    private static long executeCountQuery(
        Context context,
        SelectStatement statement,
        ExecutionImpl execution,
        String component,
        String message)
    {
        final String sql = SqlRender.render(statement, context.getDialect()).sql();
        ExecutionMetadata metadata = ExecutionMetadata.of(component, message, Purpose.OTHER, 0);
        ExecutionContext execContext = execution.asContext().createChild(metadata, Optional.empty());
        SqlStatement stmt = RolapUtil.executeQuery(context, sql, execContext);
        try {
            ResultSet resultSet = stmt.getResultSet();
            if (resultSet.next()) {
                ++stmt.rowCount;
                return resultSet.getInt(1);
            }
            return -1; // huh?
        } catch (SQLException e) {
            throw stmt.handle(e);
        } finally {
            stmt.close();
        }
    }

}
