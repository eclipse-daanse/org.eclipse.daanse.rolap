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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.junit.jupiter.api.Test;

/**
 * Pins the rendered writeback {@code INSERT} form: the SQL is produced from a dialect-free
 * {@link org.eclipse.daanse.sql.statement.api.model.InsertStatement} of positional parameter markers,
 * rendered by the renderer (identifier quoting is the renderer's, not
 * {@code dialect.quoteIdentifier}). Row binding still happens in the PreparedStatement batch loop.
 */
class BatchInsertEmitterSqlTest {

    private final AnsiDialect ansi = new AnsiDialect();

    @Test
    void insertWithoutUser() {
        String sql = BatchInsertEmitter.buildInsertSql(ansi, "WB_TABLE", List.of("amount", "qty"), false);
        assertThat(sql).isEqualTo(
            "insert into \"WB_TABLE\" (\"amount\", \"qty\", \"ID\") values (?, ?, ?)");
    }

    @Test
    void insertWithUser() {
        String sql = BatchInsertEmitter.buildInsertSql(ansi, "WB_TABLE", List.of("amount"), true);
        assertThat(sql).isEqualTo(
            "insert into \"WB_TABLE\" (\"amount\", \"ID\", \"USER\") values (?, ?, ?)");
    }
}
