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
package org.eclipse.daanse.rolap.common.writeback;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchInsertEmitter {

	private static final Logger LOGGER = LoggerFactory.getLogger(WritebackUtil.class);
	
    public static void execute(RolapCube cube, DataSource dataSource, Dialect dialect, RolapWritebackTable writebackTable, List<Map<String, Map.Entry<Datatype, Object>>> sessionValues, String userId) {
        StringBuilder sql = new StringBuilder("INSERT INTO ")
             .append(dialect.quoteIdentifier(writebackTable.getName())).append(" (");
        sql.append(writebackTable.getColumns().stream().map(c -> dialect.quoteIdentifier(c.getColumn().getName()))
             .collect(Collectors.joining(", ")));
        sql.append(", ").append(dialect.quoteIdentifier("ID"));
        if (userId != null) {
             sql.append(", ").append(dialect.quoteIdentifier("USER"));
        }
        sql.append(")  values (");
        sql.append(writebackTable.getColumns().stream().map(c -> "?")
                .collect(Collectors.joining(", ")));
        sql.append(", ?");
        if (userId != null) {
            sql.append(", ?");
        }
        sql.append(")");
        try (final java.sql.Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
        	boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(false);
            int n = 0;
            try {
                for (Map<String, Map.Entry<Datatype, Object>> wbc : sessionValues) {
                    int i = 1;
                    for (Map.Entry<String, Map.Entry<Datatype, Object>> en : wbc.entrySet()) {
                        setData(ps, i, en.getValue().getValue(), en.getValue().getKey());
                        i++;
                    }
                    ps.setString(i, UUID.randomUUID().toString());
                    i++;
                    if (userId != null) {
                        ps.setString(i, userId);
                    }
                    ps.addBatch();
                    if (++n % 500 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
        	    conn.rollback(); 
                LOGGER.error("Error committing writeback values to table: {}", writebackTable.getName(), e);
                throw new RuntimeException("Writeback commit failed", e); 
            } 
            finally { conn.setAutoCommit(prev); }
        } catch (SQLException e) {
            LOGGER.error("Error committing writeback values to table: {}", writebackTable.getName(), e);
            throw new RuntimeException("Writeback commit failed", e);
        }
    }

    private static void setData(final PreparedStatement ps, int i, Object value, Datatype type) throws SQLException {
        if (value == null) {
            ps.setObject(i, value);
            return;
        }
        switch(type) {
        case Datatype.VARCHAR:
        	ps.setString(i, value.toString());
        	return;
        case Datatype.NUMERIC:
        case Datatype.DECIMAL:
        case Datatype.FLOAT:
        case Datatype.REAL:
        case Datatype.DOUBLE:
            ps.setDouble(i, ((Number) value).doubleValue());
            return;
        case Datatype.INTEGER:
        case Datatype.SMALLINT:
            ps.setInt(i, ((Number) value).intValue());
            return;
        case Datatype.BIGINT:
            ps.setLong(i, ((Number) value).longValue());
            return;
        default:
            ps.setString(i, value.toString());
        }
    }

}
