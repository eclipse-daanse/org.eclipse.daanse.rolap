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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.SqlStatement;

/**
 * Implementation of org.eclipse.daanse.olap.spi.StatisticsProvider that generates
 * SQL queries to count rows and distinct values.
 */
//TODO remove this class when new SqlStatisticsProvider will ready
public class SqlStatisticsProviderNew  {
    public long getTableCardinality(
        Context context,
        String catalog,
        String schema,
        String table,
        ExecutionImpl execution)
    {
        StringBuilder buf = new StringBuilder("select count(*) from ");
        context.getDialect().quoteIdentifier(buf, catalog, schema, table);
        final String sql = buf.toString();
        ExecutionMetadata metadata = ExecutionMetadata.of(
            "SqlStatisticsProviderNew.getTableCardinality",
            "Reading row count from table " + table,
            org.eclipse.daanse.olap.api.monitor.event.SqlStatementEvent.Purpose.OTHER,
            0
        );
        ExecutionContext execContext = execution.asContext().createChild(metadata, Optional.empty());
        SqlStatement stmt =
            RolapUtil.executeQuery(
                context,
                sql,
                execContext);
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

    public long getQueryCardinality(
        Context context,
        String sql,
        ExecutionImpl execution)
    {
        Dialect dialect=context.getDialect();
        final StringBuilder buf = new StringBuilder();
        buf.append(
            "select count(*) from (").append(sql).append(")");
        if (dialect.requiresAliasForFromQuery()) {
            if (dialect.allowsAs()) {
                buf.append(" as ");
            } else {
                buf.append(" ");
            }
            dialect.quoteIdentifier(buf, "init");
        }
        final String countSql = buf.toString();
        ExecutionMetadata metadata = ExecutionMetadata.of(
            "SqlStatisticsProviderNew.getQueryCardinality",
            "Reading row count from query",
            org.eclipse.daanse.olap.api.monitor.event.SqlStatementEvent.Purpose.OTHER,
            0
        );
        ExecutionContext execContext = execution.asContext().createChild(metadata, Optional.empty());
        SqlStatement stmt =
            RolapUtil.executeQuery(
                context,
                countSql,
                execContext);
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

    public long getColumnCardinality(
       Context context,
        String catalog,
        String schema,
        String table,
        String column,
        ExecutionImpl execution)
    {
        final String sql =
            generateColumnCardinalitySql(
                    context.getDialect(), schema, table, column);
        if (sql == null) {
            return -1;
        }
        ExecutionMetadata metadata = ExecutionMetadata.of(
            "SqlStatisticsProviderNew.getColumnCardinality",
            "Reading cardinality for column " + table + "." + column,
            org.eclipse.daanse.olap.api.monitor.event.SqlStatementEvent.Purpose.OTHER,
            0
        );
        ExecutionContext execContext = execution.asContext().createChild(metadata, Optional.empty());
        SqlStatement stmt =
            RolapUtil.executeQuery(
                context,
                sql,
                execContext);
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

    private static String generateColumnCardinalitySql(
        Dialect dialect,
        String schema,
        String table,
        String column)
    {
        final StringBuilder buf = new StringBuilder();
        StringBuilder exprStringBuilder = dialect.quoteIdentifier(column);
        if (dialect.allowsCountDistinct()) {
            // e.g. "select count(distinct product_id) from product"
            buf.append("select count(distinct ")
                .append(exprStringBuilder)
                .append(") from ");
            dialect.quoteIdentifier(buf, schema, table);
            return buf.toString();
        } else if (dialect.allowsFromQuery()) {
            // Some databases (e.g. Access) don't like 'count(distinct)',
            // so use, e.g., "select count(*) from (select distinct
            // product_id from product)"
            buf.append("select count(*) from (select distinct ")
                .append(exprStringBuilder)
                .append(" from ");
            dialect.quoteIdentifier(buf, schema, table);
            buf.append(")");
            if (dialect.requiresAliasForFromQuery()) {
                if (dialect.allowsAs()) {
                    buf.append(" as ");
                } else {
                    buf.append(' ');
                }
                dialect.quoteIdentifier(buf, "init");
            }
            return buf.toString();
        } else {
            // Cannot compute cardinality: this database neither supports COUNT
            // DISTINCT nor SELECT in the FROM clause.
            return null;
        }
    }

}
