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

import java.util.Objects;

import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.rolap.common.RolapColumn;
import org.eclipse.daanse.rolap.common.RolapSqlExpression;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;

public class ExpressionUtil {

    public static int hashCode(RolapSqlExpression expression) {
        if (expression instanceof org.eclipse.daanse.rolap.common.RolapColumn column) {
            return column.getName().hashCode() ^ (column.getTable()==null ? 0 : column.getTable().hashCode());
        }
        if (expression != null) {
            int h = 17;
            for (int i = 0; i < expression.getSqls().size(); i++) {
                h = 37 * h + SQLUtil.hashCode(expression.getSqls().get(i));
            }
            return h;
        }
        return expression.hashCode();
    }

    public static boolean equals(RolapSqlExpression expression, Object obj) {
        if (expression instanceof org.eclipse.daanse.rolap.common.RolapColumn col) {
            if (!(obj instanceof org.eclipse.daanse.rolap.common.RolapColumn that)) {
                return false;
            }
            return col.getName().equals(that.getName()) &&
                Objects.equals(col.getTable(), that.getTable());
        }
        if (expression != null) {
            if (!(obj instanceof RolapSqlExpression that)) {
                return false;
            }
            if (expression.getSqls().size() != that.getSqls().size()) {
                return false;
            }
            for (int i = 0; i < expression.getSqls().size(); i++) {
                if (! expression.getSqls().get(i).equals(that.getSqls().get(i))) {
                    return false;
                }
            }
            return true;
        }
        return expression.equals(obj);
    }

    public static String genericExpression(SqlExpression expression) {
            for (int i = 0; i < expression.getSqls().size(); i++) {
                if (expression.getSqls().get(i).getDialects().stream().anyMatch(d ->  "generic".equals(d))) {
                    return expression.getSqls().get(i).getSql();
                }
            }
            return expression.getSqls().get(0).getSql();
    }

    public static String toString(RolapSqlExpression expression) {
    	if (expression != null && expression.getSqls() != null && !expression.getSqls().isEmpty()) {
    		return expression.getSqls().get(0).getSql();
    	}
    	return null;
    }

    public static String getExpression(SqlExpression expression, SqlQuery query) {
        if (expression instanceof RolapColumn c) {
            return query.getDialect().quoteIdentifier(c.getTable(), c.getName());
        }
        return SQLUtil.toCodeSet(expression.getSqls()).chooseQuery(query.getDialect());
    }

    public static String getTableAlias(SqlExpression expression) {
        if (expression instanceof RolapColumn c) {
            return c.getTable();
        }
        return null;
    }
}
