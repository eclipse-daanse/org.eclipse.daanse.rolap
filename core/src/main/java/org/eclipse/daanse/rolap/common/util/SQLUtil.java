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

public class SQLUtil {
    /**
     * Converts an array of SQL to a
     * {@link org.eclipse.daanse.rolap.common.sql.SqlQuery.CodeSet} object.
     */
    public static SqlQuery.CodeSet toCodeSetSqlStatement(List<? extends org.eclipse.daanse.rolap.mapping.model.SqlStatement> sqls) {
        SqlQuery.CodeSet codeSet = new SqlQuery.CodeSet();
        for (org.eclipse.daanse.rolap.mapping.model.SqlStatement sql : sqls) {
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

    public boolean equals(org.eclipse.daanse.rolap.mapping.model.SqlStatement sql, Object obj) {
        if (!(obj instanceof org.eclipse.daanse.rolap.mapping.model.SqlStatement that)) {
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
