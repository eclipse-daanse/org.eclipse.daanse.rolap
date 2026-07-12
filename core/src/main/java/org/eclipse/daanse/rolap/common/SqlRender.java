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

    /** Diagnostic channel for the commented-SQL double-render (see {@link #logCommented}); enabled purely by
     *  logback configuration — no system property needed. Distinct from the file log in {@code CommentedSqlLog}
     *  (which is gated on {@code -Ddaanse.sql.commentlog.dir}). */
    private static final org.slf4j.Logger COMMENTED_SQL_LOGGER =
            org.slf4j.LoggerFactory.getLogger("org.eclipse.daanse.rolap.sql.CommentedSql");

    /** Multi-line, comments-on options for the diagnostic commented copy (readable regardless of the executed
     *  query's formatting). */
    private static final RenderOptions COMMENTED =
            RenderOptions.multiLine().withComments(true, RenderOptions.CommentStyle.LINE);

    private SqlRender() {
    }

    /** Renders a statement with default (compact) options. */
    /**
     * As {@link #render(SelectStatement, Dialect)} for any {@code Statement} — the writeback view
     * renders a {@code SetOperation} (fact UNION ALL writeback UNION ALL session rows) directly.
     */
    public static RenderedSql render(org.eclipse.daanse.sql.statement.api.model.Statement statement,
            Dialect dialect) {
        return new org.eclipse.daanse.sql.statement.render.DialectSqlRenderer(dialect)
            .render(statement, org.eclipse.daanse.sql.statement.api.render.RenderOptions.compact());
    }

    public static RenderedSql render(SelectStatement statement, Dialect dialect) {
        org.eclipse.daanse.rolap.common.sqlbuild.CommentedSqlLog.append(statement, dialect);
        DialectSqlRenderer renderer = new DialectSqlRenderer(dialect);
        logCommented(renderer, statement);
        return renderer.render(statement);
    }

    /** Renders a statement with explicit render options. */
    public static RenderedSql render(SelectStatement statement, Dialect dialect, RenderOptions options) {
        org.eclipse.daanse.rolap.common.sqlbuild.CommentedSqlLog.append(statement, dialect);
        DialectSqlRenderer renderer = new DialectSqlRenderer(dialect);
        logCommented(renderer, statement);
        return renderer.render(statement, options);
    }

    /**
     * Diagnostic double-hit: whenever the commented-SQL logger is enabled (a pure logback decision), render the
     * SAME statement a second time WITH comments and log it. This is a pure side effect — the renderer is
     * stateless and the returned/executed SQL is rendered separately with the caller's options, so the executed
     * SQL is unaffected; the commented copy goes only to the log. Never throws (render problems are
     * logged as a single WARN and swallowed).
     */
    private static void logCommented(DialectSqlRenderer renderer, SelectStatement statement) {
        if (COMMENTED_SQL_LOGGER.isInfoEnabled()) {
            try {
                COMMENTED_SQL_LOGGER.info("commented sql:\n{}", renderer.render(statement, COMMENTED).sql());
            } catch (RuntimeException e) {
                COMMENTED_SQL_LOGGER.warn("commented-sql render failed (ignored): {}", e.toString());
            }
        }
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
