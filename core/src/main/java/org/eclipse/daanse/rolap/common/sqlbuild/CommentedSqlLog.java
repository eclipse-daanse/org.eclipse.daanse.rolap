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
package org.eclipse.daanse.rolap.common.sqlbuild;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic commented-SQL statement log, enabled by the system property
 * {@code -Ddaanse.sql.commentlog.dir=<dir>}: every statement rendered for execution is
 * ADDITIONALLY rendered with comments on (formatted, {@link RenderOptions.CommentStyle#LINE})
 * and appended to {@code <dir>/commented-sql-<daanse.tc.id or 0>.sql}, each entry preceded by a
 * {@code -- ==== statement N ====} separator. The extra render is a pure side effect (the
 * renderer is stateless), so the EXECUTED SQL stays byte-identical — comments live only in this
 * file.
 *
 * <p>Hooked from the two render seams that cover all executed statements:
 * {@code SqlRender.render(SelectStatement, ...)} (the rolap-wide render entry point) and
 * {@code QueryRecorder}'s internal render behind {@code toSqlAndTypes}/{@code toGuarded} (which
 * constructs {@code DialectSqlRenderer} directly, bypassing {@code SqlRender}).
 *
 * <p>Dedupe: a back-to-back re-render of the SAME statement instance (e.g. {@code SqlBuildGuard}
 * re-rendering a promoted candidate in the reference's formatting) is logged once, by identity of
 * the last logged statement. NOT deduped — and accepted, since the file is for human inspection:
 * {@code SqlBuildGuard.orReference} logs both the builder candidate and the reference even
 * though only one of them is executed, and statistics/cardinality probe queries are logged like
 * any other statement.
 *
 * <p>Never throws: render or IO problems are logged as a single WARN and the diagnostic is
 * silently skipped from then on (for IO, per failing directory).
 */
public final class CommentedSqlLog {

    /** System property naming the target directory; the log is off when unset/blank. */
    public static final String DIR_PROPERTY = "daanse.sql.commentlog.dir";

    /** Harness worktree id — suffixes the file name so parallel JVMs never share a file. */
    private static final String TC_ID_PROPERTY = "daanse.tc.id";

    private static final Logger LOGGER = LoggerFactory.getLogger(CommentedSqlLog.class);

    private static final RenderOptions COMMENTED =
            RenderOptions.multiLine().withComments(true, RenderOptions.CommentStyle.LINE);

    private static final Object LOCK = new Object();
    private static PrintWriter writer;
    private static String openedDir;
    private static String failedDir;
    private static int counter;
    private static boolean warned;
    private static boolean hookInstalled;
    private static SelectStatement lastLogged;

    private CommentedSqlLog() {
    }

    /**
     * Appends the commented render of {@code statement} to the log file when the
     * {@value #DIR_PROPERTY} property is set; a cheap no-op otherwise. Never throws.
     */
    public static void append(SelectStatement statement, Dialect dialect) {
        final String dir = System.getProperty(DIR_PROPERTY);
        if (dir == null || dir.isBlank() || statement == null || dialect == null) {
            return;
        }
        // Render OUTSIDE the lock: parallel test threads must not serialize on the (expensive)
        // diagnostic render — only the file append itself is synchronized. The lastLogged dedupe
        // is checked twice (cheap pre-check, authoritative re-check inside the lock).
        if (statement == lastLogged) {
            return;
        }
        final String sql;
        try {
            sql = new DialectSqlRenderer(dialect).render(statement, COMMENTED).sql();
        } catch (RuntimeException e) {
            warnOnce("commented-sql render failed (statement skipped): " + e);
            return;
        }
        synchronized (LOCK) {
            if (statement == lastLogged) {
                return;
            }
            final PrintWriter w = writer(dir);
            if (w == null) {
                return;
            }
            counter++;
            w.println("-- ==== statement " + counter + " ====");
            w.println();
            w.println(sql);
            w.println();
            // Buffered: flush every 50 entries (plus the shutdown hook installed by writer()) —
            // a per-entry flush serialized the parallel tests into a crawl.
            if (counter % 50 == 0) {
                w.flush();
            }
            lastLogged = statement;
        }
    }

    /** The lazily opened per-dir+tcid appender, or {@code null} when opening failed (warned once). */
    private static PrintWriter writer(String dir) {
        if (writer != null && dir.equals(openedDir)) {
            return writer;
        }
        if (dir.equals(failedDir)) {
            return null;
        }
        if (writer != null) {
            writer.close();
            writer = null;
            openedDir = null;
        }
        try {
            final String tcId = System.getProperty(TC_ID_PROPERTY, "0");
            final Path file = Path.of(dir).resolve("commented-sql-" + tcId + ".sql");
            Files.createDirectories(file.getParent());
            writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND));
            openedDir = dir;
            if (!hookInstalled) {
                hookInstalled = true;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    synchronized (LOCK) {
                        if (writer != null) {
                            writer.flush();
                        }
                    }
                }, "commented-sql-log-flush"));
            }
            return writer;
        } catch (IOException | RuntimeException e) {
            failedDir = dir;
            warnOnce("cannot open commented-sql log in '" + dir + "': " + e);
            return null;
        }
    }

    private static void warnOnce(String message) {
        if (!warned) {
            warned = true;
            LOGGER.warn("{} (further commented-sql log warnings suppressed)", message);
        }
    }

    /** Test hook: flushes the buffered writer so assertions can read the file. */
    static void flushForTests() {
        synchronized (LOCK) {
            if (writer != null) {
                writer.flush();
            }
        }
    }

    /** Test hook: closes the writer and resets all state (counter, dedupe, warn latch). */
    static void resetForTests() {
        synchronized (LOCK) {
            if (writer != null) {
                writer.close();
            }
            writer = null;
            openedDir = null;
            failedDir = null;
            counter = 0;
            warned = false;
            lastLogged = null;
        }
    }
}
