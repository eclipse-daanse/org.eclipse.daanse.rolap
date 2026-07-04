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

import java.util.function.Supplier;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.SqlRender;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.compare.SqlTextNormalizer;
import org.eclipse.daanse.sql.statement.compare.StatementEquivalence;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;

/**
 * Chooses the SQL to execute when a query can be produced two ways — by the generic statement
 * builder and by another path that supplies a reference {@link GuardedSql}.
 * <p>
 * {@link #orReference} builds the candidate {@link SelectStatement}, renders it, and returns it only
 * when its SQL is whitespace-identical to the reference's; otherwise it returns the reference
 * unchanged. The candidate is supplied lazily and built inside the guard, so a builder that cannot
 * produce a given query shape (an error while building <em>or</em> rendering) — as well as any SQL
 * difference — yields the reference. Thus the builder is used for the shapes it reproduces
 * identically and the reference for the rest, and a mapper that does not yet cover a shape degrades
 * to the reference rather than failing the query. The returned column types are the reference's
 * (identical when the SQL is identical).
 */
public final class SqlBuildGuard {

    private SqlBuildGuard() {
    }

    /**
     * Renders {@code candidate} and returns its own {@link RenderedSql} (SQL, column types and any
     * bound parameters) as the authoritative result — for paths where the builder is trusted to
     * produce the query directly, gated by the mapper's {@code supports} check and verified by
     * result-level tests rather than a byte-for-byte comparison with a reference. A build/render
     * error propagates to the caller, which falls back to its reference SQL.
     */
    public static GuardedSql build(Dialect dialect, Supplier<SelectStatement> candidate) {
        return build(dialect, false, candidate);
    }

    /**
     * As {@link #build(Dialect, Supplier)} but honoring the {@code generateFormattedSql} config flag:
     * renders multi-line/indented when {@code formatted}, else compact. The builder fast paths must
     * honor the flag, or a query asserted in formatted form (e.g. tests with
     * {@code setGenerateFormattedSql(true)}) sees single-line SQL.
     */
    public static GuardedSql build(Dialect dialect, boolean formatted, Supplier<SelectStatement> candidate) {
        org.eclipse.daanse.sql.statement.api.render.RenderOptions opts = formatted
                ? org.eclipse.daanse.sql.statement.api.render.RenderOptions.multiLine()
                : org.eclipse.daanse.sql.statement.api.render.RenderOptions.compact();
        SelectStatement candidateStatement = candidate.get();
        RenderedSql rendered = SqlRender.render(candidateStatement, dialect, opts);
        // Authoritative path: no reference SQL exists here; the builder render is executed as-is.
        // (sql() is already computed, so the parameterized call is cheap and needs no level guard.)
        RolapUtil.SQL_GEN_LOGGER.trace("SqlBuildGuard: builder authoritative NEW=[{}]", rendered.sql());
        return new GuardedSql(candidateStatement, rendered);
    }

    /** {@code -Ddaanse.sql.gen.structural=true} — classify each byte-divergence as structurally
     *  EQUIVALENT (promotable) or DIFFERENT (a real blocker) via {@link StatementEquivalence}. */
    private static final boolean STRUCTURAL_VERIFY = Boolean.getBoolean("daanse.sql.gen.structural");

    /** {@code -Ddaanse.sql.gen.dualexec=true} — additionally EXECUTE both sides of an EQUIVALENT
     *  divergence against the live database and compare the result multisets (a result-level
     *  proof of equivalence). Verify-mode only; requires structural mode. */
    private static final boolean DUAL_EXEC = Boolean.getBoolean("daanse.sql.gen.dualexec");

    /** Row cap per side — beyond it the comparison logs TRUNCATED (not a proof). */
    private static final int DUAL_EXEC_ROW_CAP = 1000;

    /**
     * The promotion allowlist: a byte-diverging builder candidate is USED (instead of falling back
     * to the reference) when it is structurally equivalent AND every equivalence class the
     * divergence needed is in this set. A class is admitted only with a result-level proof that
     * the divergence is result-neutral; rollback = remove the class name.
     * <p>
     * {@code join-order} — inner-join FROM order (same tables, same ON conditions) is
     * result-neutral; proven by dual-executing both orderings and comparing result multisets.
     */
    private static final java.util.Set<String> ACCEPTED_EQUIVALENCES = java.util.Set.of("join-order");

    /**
     * The byte-equal-or-fall-back guard. In structural-verify mode
     * ({@code -Ddaanse.sql.gen.structural}), when the byte-compare diverges it classifies the
     * divergence with {@link StatementEquivalence} against {@code reference.statement()} (the
     * recorder's assembled statement) — logging whether the path is structurally promotable
     * (cosmetic diff) or a real blocker. Additionally, in dual-execute mode
     * ({@code -Ddaanse.sql.gen.dualexec}, requires structural mode), EXECUTES both sides of an
     * EQUIVALENT-classified divergence against {@code dataSource} (nullable) and logs whether the
     * result multisets are EQUAL — a result-level equivalence proof. The executed engine SQL is
     * unaffected by the verify modes (still the byte-equal builder or the reference).
     */
    public static GuardedSql orReference(
            String what, Dialect dialect, Supplier<SelectStatement> candidate,
            GuardedSql reference, javax.sql.DataSource dataSource) {
        try {
            SelectStatement candidateStatement = candidate.get();
            RenderedSql candidateRendered = SqlRender.render(candidateStatement, dialect);
            String sql = candidateRendered.sql();
            boolean match = SqlTextNormalizer.normalizeWhitespace(sql)
                    .equals(SqlTextNormalizer.normalizeWhitespace(reference.render().sql()));
            // OLD/NEW side-by-side at TRACE (sql()/reference.render().sql() already computed → cheap, no guard).
            RolapUtil.SQL_GEN_LOGGER.trace("SqlBuildGuard[{}] match={} OLD=[{}] NEW=[{}]",
                    what, match, reference.render().sql(), sql);
            SelectStatement refStmt = !match ? reference.statement() : null;
            if (!match && STRUCTURAL_VERIFY && refStmt != null) {
                try {
                    // ALL clause diffs, not the first — a first-diff report misattributes multi-clause
                    // divergences (e.g. a duplicated FROM table surfacing as a spurious ORDER-BY diff).
                    java.util.List<StatementEquivalence.Diff> structural =
                            StatementEquivalence.describeAll(candidateStatement, refStmt, dialect);
                    RolapUtil.SQL_GEN_LOGGER.debug("SqlBuildGuard[{}]: STRUCTURAL {}{}", what,
                            structural.isEmpty() ? "EQUIVALENT (promotable)" : "DIFFERENT (blocker) — ",
                            structural.isEmpty() ? "" : structural.stream().map(Object::toString)
                                    .collect(java.util.stream.Collectors.joining(" | ")));
                    if (structural.isEmpty() && DUAL_EXEC) {
                        if (dataSource != null) {
                            dualExecute(what, dataSource, reference.render().sql(), sql,
                                    !candidateStatement.orderKeys().isEmpty());
                        } else {
                            RolapUtil.SQL_GEN_LOGGER.debug("DUALEXEC[{}] skipped: no DataSource", what);
                        }
                    }
                } catch (RuntimeException e) {
                    RolapUtil.SQL_GEN_LOGGER.debug("SqlBuildGuard[{}]: structural-verify failed ({})", what, e.toString());
                }
            }
            // Promotion (production-active): a byte-diverging candidate is USED when the
            // divergence is structurally equivalent and every equivalence class it needed is in
            // ACCEPTED_EQUIVALENCES. An EMPTY class set (equivalent but no named relaxation
            // explains the byte diff — e.g. a raw-fragment spelling difference) stays conservative:
            // fall back to the reference.
            if (!match && refStmt != null) {
                java.util.Optional<java.util.Set<String>> classes =
                        StatementEquivalence.equivalenceClasses(candidateStatement, refStmt, dialect);
                if (classes.isPresent() && !classes.get().isEmpty()
                        && ACCEPTED_EQUIVALENCES.containsAll(classes.get())) {
                    RolapUtil.SQL_GEN_LOGGER.debug("SqlBuildGuard[{}]: PROMOTED class={} -> using builder SQL",
                            what, classes.get());
                    // Match the reference's formatting (generateFormattedSql is not threaded here;
                    // a multi-line reference means the flag was on) so formatted SQL asserts see
                    // the same layout, differing only by the accepted clause diff.
                    if (reference.render().sql().indexOf('\n') >= 0) {
                        return new GuardedSql(candidateStatement,
                                SqlRender.render(candidateStatement, dialect,
                                        org.eclipse.daanse.sql.statement.api.render.RenderOptions.multiLine()));
                    }
                    return new GuardedSql(candidateStatement, candidateRendered);
                }
            }
            if (match) {
                // Whitespace-identical means identical tokens — semantically the same query. Execute
                // the reference unchanged (preserving its exact text, which some TCK tests assert
                // byte-for-byte) while having verified the builder reproduces it. Promotion to
                // build() is what makes the builder's own SQL authoritative.
                RolapUtil.SQL_GEN_LOGGER.debug("SqlBuildGuard[{}]: builder matches reference -> using reference (verified)", what);
                return new GuardedSql(candidateStatement, reference.render());
            }
            // The builder does not reproduce every shape; a fallback here is a normal outcome,
            // not an error — hence DEBUG, not WARN.
            RolapUtil.SQL_GEN_LOGGER.debug("SqlBuildGuard[{}]: builder SQL differs from reference -> using legacy reference", what);
        } catch (RuntimeException e) {
            RolapUtil.SQL_GEN_LOGGER.debug("SqlBuildGuard[{}]: builder unavailable ({}) -> using legacy reference", what, e.toString());
        }
        return reference;
    }

    /**
     * Executes both SQLs and compares the results: as SEQUENCES when the statement has an ORDER BY
     * (order is row-significant; ties can still legitimately differ — a DIFFERENT verdict on a tie
     * column set needs a manual look), else as MULTISETS (rows sorted before compare). Rows beyond
     * {@link #DUAL_EXEC_ROW_CAP} log TRUNCATED — an inconclusive verdict, NOT a proof.
     */
    private static void dualExecute(String what, javax.sql.DataSource dataSource,
            String oldSql, String newSql, boolean orderSensitive) {
        try (java.sql.Connection con = dataSource.getConnection()) {
            java.util.List<java.util.List<String>> oldRows = fetch(con, oldSql);
            java.util.List<java.util.List<String>> newRows = fetch(con, newSql);
            if (oldRows == null || newRows == null) {
                RolapUtil.SQL_GEN_LOGGER.debug("DUALEXEC[{}] RESULT=TRUNCATED (>{} rows; not a proof)",
                        what, DUAL_EXEC_ROW_CAP);
                return;
            }
            java.util.List<java.util.List<String>> l = oldRows;
            java.util.List<java.util.List<String>> r = newRows;
            if (!orderSensitive) {
                java.util.Comparator<java.util.List<String>> cmp =
                        java.util.Comparator.comparing(Object::toString);
                l = new java.util.ArrayList<>(oldRows);
                r = new java.util.ArrayList<>(newRows);
                l.sort(cmp);
                r.sort(cmp);
            }
            if (l.equals(r)) {
                RolapUtil.SQL_GEN_LOGGER.debug("DUALEXEC[{}] RESULT=EQUAL rows={} ordered={}",
                        what, oldRows.size(), orderSensitive);
            } else {
                int i = 0;
                int max = Math.min(l.size(), r.size());
                while (i < max && l.get(i).equals(r.get(i))) {
                    i++;
                }
                RolapUtil.SQL_GEN_LOGGER.debug(
                        "DUALEXEC[{}] RESULT=DIFFERENT rowsOld={} rowsNew={} firstDiff#{} old={} new={}",
                        what, l.size(), r.size(), i,
                        i < l.size() ? l.get(i) : "<none>", i < r.size() ? r.get(i) : "<none>");
            }
        } catch (java.sql.SQLException | RuntimeException e) {
            RolapUtil.SQL_GEN_LOGGER.debug("DUALEXEC[{}] failed ({})", what, e.toString());
        }
    }

    /** All rows as stringified cell lists, or {@code null} when the cap is exceeded (truncated). */
    private static java.util.List<java.util.List<String>> fetch(java.sql.Connection con, String sql)
            throws java.sql.SQLException {
        try (java.sql.Statement st = con.createStatement();
                java.sql.ResultSet rs = st.executeQuery(sql)) {
            int n = rs.getMetaData().getColumnCount();
            java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
            while (rs.next()) {
                if (rows.size() >= DUAL_EXEC_ROW_CAP) {
                    return null;
                }
                java.util.List<String> row = new java.util.ArrayList<>(n);
                for (int i = 1; i <= n; i++) {
                    row.add(String.valueOf(rs.getObject(i)));
                }
                rows.add(row);
            }
            return rows;
        }
    }
}
