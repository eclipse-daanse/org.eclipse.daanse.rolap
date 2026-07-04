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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.common.SqlRender;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code -Ddaanse.sql.commentlog.dir} diagnostic file log: rendering a statement through
 * either seam ({@link SqlRender} or the {@link QueryRecorder} internal render) appends a
 * comments-on copy to {@code <dir>/commented-sql-<tcid>.sql} while the returned (executed) SQL
 * stays comment-free and byte-identical to a render with the property off.
 */
class CommentedSqlLogTest {

    private final Dialect ansi = new AnsiDialect();

    @TempDir
    Path dir;

    @BeforeEach
    @AfterEach
    void reset() {
        System.clearProperty(CommentedSqlLog.DIR_PROPERTY);
        CommentedSqlLog.resetForTests();
    }

    private QueryRecorder recorder() {
        QueryRecorder q = new QueryRecorder(false);
        q.setHeaderComment("members [Store].[Store City] (cube Sales)");
        q.addFromTable(null, "store", "store", null, null, false);
        q.addSelectExprCommented(new RolapColumn("store", "store_city"), BestFitColumnType.STRING,
                "level key [Store].[Store City]");
        return q;
    }

    @Test
    void appendsCommentedCopyAndKeepsExecutedSqlIdentical() throws Exception {
        QueryRecorder q = recorder();
        SelectStatement statement = q.buildStatement(ansi);

        // Baseline renders with the log OFF.
        String offSqlRender = SqlRender.render(statement, ansi).sql();
        String offRecorder = q.toSqlAndTypes(ansi).sql();
        Path file = dir.resolve("commented-sql-" + System.getProperty("daanse.tc.id", "0") + ".sql");
        assertThat(file).doesNotExist();

        System.setProperty(CommentedSqlLog.DIR_PROPERTY, dir.toString());
        // Both seams: the SqlRender entry point and QueryRecorder's internal (bypassing) render.
        String onSqlRender = SqlRender.render(statement, ansi).sql();
        String onRecorder = recorder().toSqlAndTypes(ansi).sql();

        // Executed SQL is byte-identical and comment-free — the log render is a side effect only.
        assertThat(onSqlRender).isEqualTo(offSqlRender).doesNotContain("--");
        assertThat(onRecorder).isEqualTo(offRecorder).doesNotContain("--");

        CommentedSqlLog.flushForTests();
        String log = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(log)
                .contains("-- ==== statement 1 ====")
                .contains("-- ==== statement 2 ====")
                .contains("-- members [Store].[Store City] (cube Sales)")
                .contains("-- level key [Store].[Store City]");
    }

    @Test
    void dedupesImmediateReRenderOfSameStatement() throws Exception {
        System.setProperty(CommentedSqlLog.DIR_PROPERTY, dir.toString());
        SelectStatement statement = recorder().buildStatement(ansi);
        // The SqlBuildGuard promote path renders the SAME statement instance twice in a row.
        SqlRender.render(statement, ansi);
        SqlRender.render(statement, ansi,
                org.eclipse.daanse.sql.statement.api.render.RenderOptions.multiLine());
        CommentedSqlLog.flushForTests();
        String log = Files.readString(
                dir.resolve("commented-sql-" + System.getProperty("daanse.tc.id", "0") + ".sql"),
                StandardCharsets.UTF_8);
        assertThat(log).contains("-- ==== statement 1 ====").doesNotContain("==== statement 2 ====");
    }

    @Test
    void noopWithoutProperty() {
        // Property cleared in reset(): rendering must neither fail nor create a file.
        SqlRender.render(recorder().buildStatement(ansi), ansi);
        assertThat(dir).isEmptyDirectory();
    }
}
