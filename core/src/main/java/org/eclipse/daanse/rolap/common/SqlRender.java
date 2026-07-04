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
package org.eclipse.daanse.rolap.common;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;

/**
 * The single entry point for rendering a dialect-free statement/predicate to dialect SQL. Producers build
 * a {@link SelectStatement} / {@link Predicate} without a dialect and call these helpers with the live
 * {@link Dialect}; {@link DialectSqlRenderer} (the only dialect-aware render component) is constructed here
 * rather than at each call site — a step toward render-once-at-the-executor.
 */
public final class SqlRender {

    private SqlRender() {
    }

    /** Renders a statement with default (compact) options. */
    public static RenderedSql render(SelectStatement statement, Dialect dialect) {
        org.eclipse.daanse.rolap.common.sqlbuild.CommentedSqlLog.append(statement, dialect);
        return new DialectSqlRenderer(dialect).render(statement);
    }

    /** Renders a statement with explicit render options. */
    public static RenderedSql render(SelectStatement statement, Dialect dialect, RenderOptions options) {
        org.eclipse.daanse.rolap.common.sqlbuild.CommentedSqlLog.append(statement, dialect);
        return new DialectSqlRenderer(dialect).render(statement, options);
    }

    /** Renders a single predicate to its dialect SQL fragment (no surrounding SELECT). */
    public static String renderPredicate(Predicate predicate, Dialect dialect) {
        return new DialectSqlRenderer(dialect).renderPredicate(predicate);
    }

    /** Renders a single scalar expression to its dialect SQL fragment (e.g. a native-SQL column). */
    public static String renderExpression(
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression expression, Dialect dialect) {
        return new DialectSqlRenderer(dialect).renderExpression(expression);
    }
}
