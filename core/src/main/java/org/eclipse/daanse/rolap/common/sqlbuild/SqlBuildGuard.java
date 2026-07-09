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
import org.eclipse.daanse.rolap.common.sql.BuiltSql;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;

/**
 * Renders a builder-produced {@link SelectStatement} into the executable {@link BuiltSql} — the
 * ONE seam every builder-authoritative path goes through. The statement builder is the single
 * authoritative SQL producer on these paths; a build or render error propagates to the caller
 * (there is no fallback — the routing conditions at the call sites send shapes the builder cannot
 * express to the recorder BEFORE building).
 *
 * <p>Kept as a class (not inlined into its call sites) because it centralizes exactly the three
 * things every authoritative render must agree on: the {@code RenderOptions} selection from the
 * {@code generateFormattedSql} flag, the {@code SqlBuildGuard: builder authoritative} trace line
 * (greppable telemetry — the gate tooling counts authoritative renders by this exact prefix; do
 * not reword it), and the {@link BuiltSql} packaging.
 */
public final class SqlBuildGuard {

    private SqlBuildGuard() {
    }

    /**
     * Renders {@code candidate} compact and returns its {@link RenderedSql} (SQL, column types
     * and any bound parameters) as the authoritative result.
     */
    public static BuiltSql build(Dialect dialect, Supplier<SelectStatement> candidate) {
        return build(dialect, false, candidate);
    }

    /**
     * As {@link #build(Dialect, Supplier)} but honoring the {@code generateFormattedSql} config flag:
     * renders multi-line/indented when {@code formatted}, else compact.
     */
    public static BuiltSql build(Dialect dialect, boolean formatted, Supplier<SelectStatement> candidate) {
        org.eclipse.daanse.sql.statement.api.render.RenderOptions opts = formatted
                ? org.eclipse.daanse.sql.statement.api.render.RenderOptions.multiLine()
                : org.eclipse.daanse.sql.statement.api.render.RenderOptions.compact();
        SelectStatement candidateStatement = candidate.get();
        RenderedSql rendered = SqlRender.render(candidateStatement, dialect, opts);
        // (sql() is already computed, so the parameterized call is cheap and needs no level guard.)
        RolapUtil.SQL_GEN_LOGGER.trace("SqlBuildGuard: builder authoritative NEW=[{}]", rendered.sql());
        return new BuiltSql(candidateStatement, rendered);
    }
}
