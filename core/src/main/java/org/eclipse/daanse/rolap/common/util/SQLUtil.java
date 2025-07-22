/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.rolap.common.util;

import java.util.List;

import org.eclipse.daanse.olap.api.SqlStatement;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.mapping.api.model.SqlStatementMapping;

public class SQLUtil {
    /**
     * Converts an array of SQL to a
     * {@link org.eclipse.daanse.rolap.common.sql.SqlQuery.CodeSet} object.
     */
    public static SqlQuery.CodeSet toCodeSetSqlStatement(List<? extends SqlStatementMapping> sqls) {
        SqlQuery.CodeSet codeSet = new SqlQuery.CodeSet();
        for (SqlStatementMapping sql : sqls) {
            for (String dialect : sql.getDialects()) {
                codeSet.put(dialect, sql.getSql());
            }
        }
        return codeSet;
    }

    public static SqlQuery.CodeSet toCodeSet(List<SqlStatement> sqls) {
        SqlQuery.CodeSet codeSet = new SqlQuery.CodeSet();
        for (SqlStatement sql : sqls) {
            for (String dialect : sql.getDialects()) {
                codeSet.put(dialect, sql.getSql());
            }
        }
        return codeSet;
    }

    public static int hashCode(SqlStatement sql) {
        return sql.getDialects().hashCode();
    }

    public boolean equals(SqlStatementMapping sql, Object obj) {
        if (!(obj instanceof SqlStatementMapping that)) {
            return false;
        }
        if (sql.getDialects().size() != that.getDialects().size()) {
            return false;
        }
        if (!sql.getSql().equals(that.getSql())) {
            return false;
        }

        for (int i = 0; i < sql.getDialects().size(); i++) {
            if (sql.getDialects().get(i).equals(that.getDialects().get(i))) {
                return false;
            }
        }
        return true;
    }
}
