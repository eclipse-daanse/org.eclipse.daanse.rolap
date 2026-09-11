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

package org.eclipse.daanse.test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.spi.StatisticsProvider;

/**
 * Implementation of {@link mondrian.spi.StatisticsProvider} that uses JDBC
 * metadata calls to count rows and distinct values.
 */
public class JdbcStatisticsProvider implements StatisticsProvider {
    private static final Logger LOG =
        LoggerFactory.getLogger(JdbcStatisticsProvider.class);
    @Override
	public long getTableCardinality(
        Context context,
        String catalog,
        String schema,
        String table,
        ExecutionImpl execution)
    {
        Connection connection = null;
        ResultSet resultSet = null;
        try {
            connection = context.getDataSource().getConnection();
            resultSet =
                connection
                    .getMetaData()
                    .getIndexInfo(catalog, schema, table, false, true);
            int maxNonUnique = -1;
            while (resultSet.next()) {
                final int type = resultSet.getInt(7); // "TYPE" column
                final int cardinality = resultSet.getInt(11);
                final boolean unique =
                    !resultSet.getBoolean(4); // "NON_UNIQUE" column
                switch (type) {
                case DatabaseMetaData.tableIndexStatistic:
                    return cardinality; // "CARDINALITY" column
                }
                if (!unique) {
                    maxNonUnique = Math.max(maxNonUnique, cardinality);
                }
            }
            // The cardinality of each non-unique index will be the number of
            // non-NULL values in that index. Unless we're unlucky, one of those
            // columns will cover most of the table.
            return maxNonUnique;
        } catch (SQLException e) {
            // We will have to try a count() operation or some other
            // statistics provider in the chain.
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                    "JdbcStatisticsProvider failed to get the cardinality of the table "
                        + table,
                    e);
            }
            return -1;
        } finally {
            Util.close(resultSet, null, connection);
        }
    }

    @Override
	public long getQueryCardinality(
        Context context,
        String sql,
        ExecutionImpl execution)
    {
        // JDBC cannot help with this. Defer to another statistics provider.
        return -1;
    }

    @Override
	public long getColumnCardinality(
        Context context,
        String catalog,
        String schema,
        String table,
        String column,
        ExecutionImpl execution)
    {
        Connection connection = null;
        ResultSet resultSet = null;
        try {
            connection = context.getDataSource().getConnection();
            resultSet =
                connection
                    .getMetaData()
                    .getIndexInfo(catalog, schema, table, false, true);
            while (resultSet.next()) {
                int type = resultSet.getInt(7); // "TYPE" column
                switch (type) {
                case DatabaseMetaData.tableIndexStatistic:
                    continue;
                default:
                    String columnName = resultSet.getString(9); //COLUMN_NAME
                    if (columnName != null && columnName.equals(column)) {
                        return resultSet.getInt(11); // "CARDINALITY" column
                    }
                }
            }
            return -1; // information not available, apparently
        } catch (SQLException e) {
            // We will have to try a count() operation or some other
            // statistics provider in the chain.
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                    "JdbcStatisticsProvider failed to get the cardinality of the table "
                        + table,
                    e);
            }
            return -1;
        } finally {
            Util.close(resultSet, null, connection);
        }
    }
}
