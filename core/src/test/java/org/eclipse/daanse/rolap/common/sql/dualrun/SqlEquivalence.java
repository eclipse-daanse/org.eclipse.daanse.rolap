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
package org.eclipse.daanse.rolap.common.sql.dualrun;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dual-run comparator for the {@code QueryRecorder} → generic-statement-builder migration.
 * <p>
 * During the migration each query shape is built two ways — by the legacy
 * {@link org.eclipse.daanse.rolap.common.sql.QueryRecorder} and by the new
 * {@code org.eclipse.daanse.sql.statement} builder rendered with the same {@code Dialect} — and
 * the two SQL strings must be equivalent. The legacy builder can emit multi-line/indented SQL
 * (its {@code generateFormattedSql} flag) while the new renderer is single-line, so comparison
 * is <em>whitespace-normalized</em>: every run of whitespace collapses to a single space and the
 * result is trimmed (the same normalization the legacy SQL test matchers use — see
 * {@code NEXT_TODO_SQL_WHITESPACE.md}).
 */
public final class SqlEquivalence {

    private SqlEquivalence() {
    }

    /** Collapse all whitespace runs to a single space and trim. */
    public static String normalize(String sql) {
        return org.eclipse.daanse.sql.statement.compare.SqlTextNormalizer.normalizeWhitespace(sql);
    }

    /** Assert that the legacy and new SQL are equivalent after whitespace normalization. */
    public static void assertEquivalent(String legacySql, String newSql) {
        assertEquals(normalize(legacySql), normalize(newSql),
                () -> "dual-run SQL mismatch:\n  legacy: " + legacySql + "\n  new:    " + newSql);
    }
}
