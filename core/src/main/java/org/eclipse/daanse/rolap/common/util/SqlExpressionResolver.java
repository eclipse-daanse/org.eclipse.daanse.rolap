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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.SqlStatement;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.element.RolapColumn;

public class SqlExpressionResolver {

    public static String genericSql(SqlExpression expression) {
            for (SqlStatement element : expression.getSqls()) {
                if (element.getDialects().stream().anyMatch(d ->  "generic".equals(d))) {
                    return element.getSql();
                }
            }
            return expression.getSqls().getFirst().getSql();
    }

    /** Renders {@code expression} with only a {@link Dialect}. */
    public static String render(SqlExpression expression, Dialect dialect) {
        if (expression instanceof RolapColumn c) {
            return dialect.quoteIdentifier(c.getTable(), c.getName());
        }
        // A computed expression is rendered as a dialect-free RawVariant node (JoinPlanner.expressionFor),
        // resolved per dialect by the renderer's chooseVariant. Reaching this removed legacy dialect-string path
        // means a SQL producer still feeds a computed column as a string — convert that producer to a node.
        throw new IllegalStateException(
            "computed SQL expression must be a RawVariant node, not the removed legacy dialect-string path: "
                + expression);
    }

    /**
     * Dialect-free counterpart of {@link #render(SqlExpression, Dialect)} for a computed (non-column)
     * expression: the whole {@code dialect-name -> SQL} map, for a renderer-resolved {@code RawVariant} node.
     * (Plain {@link RolapColumn}s have no map — callers build a dialect-free {@code Column} node instead.)
     */
    public static java.util.Map<String, String> sqlVariants(SqlExpression expression) {
        return ViewCodeSet.fromOlapSqlStatement(expression.getSqls()).asMap();
    }

    public static String getTableAlias(SqlExpression expression) {
        if (expression instanceof RolapColumn c) {
            return c.getTable();
        }
        return null;
    }
}
