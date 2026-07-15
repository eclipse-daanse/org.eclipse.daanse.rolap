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

import org.eclipse.daanse.sql.dialect.api.Dialect;
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

    /**
     * LEGACY RENDER SHIM: quotes a plain column; throws for anything computed (those must travel
     * as dialect-free RawVariant nodes). Kept only for the diagnostic printers and the shrinking
     * recorder string path — new producers use {@code JoinPlanner.expressionFor}.
     */
    public static String render(SqlExpression expression, Dialect dialect) {
        if (expression instanceof RolapColumn c) {
            return dialect.quoteIdentifier(c.getTable(), c.getName());
        }
        // A computed expression must be emitted as a dialect-free node (JoinPlanner.expressionFor),
        // resolved per dialect by the renderer, not a pre-rendered string; reaching here means a producer
        // passed a non-plain-column expression.
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
