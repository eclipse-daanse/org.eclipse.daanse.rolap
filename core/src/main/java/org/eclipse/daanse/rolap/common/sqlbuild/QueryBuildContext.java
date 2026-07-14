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
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.SqlRender;
import org.eclipse.daanse.rolap.common.sql.BuiltSql;
import org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;

/**
 * The per-read bundle of everything a builder-authoritative SQL build needs from the
 * execution context: the {@link Dialect} handed to the render seam and the
 * {@code generateFormattedSql} flag. Created ONCE at a routing entry point
 * ({@code SqlMemberSource.makeChildMemberSql}, {@code SqlTupleReader.generateSelectForLevels},
 * {@code AbstractQuerySpec.generateSql}) and threaded to every {@link #build(Supplier)} call of
 * that read, replacing the repeated {@code context.getDialect()} + config-read plumbing at each
 * call site.
 *
 * <p>{@link #build(Supplier)} is the ONE seam every builder-authoritative path renders through
 * (formerly the {@code SqlBuildGuard} class, dissolved once its reference-compare semantics were
 * gone). A build or render error propagates to the caller — there is no fallback; the routing
 * conditions at the call sites send shapes the builder cannot express to the recorder BEFORE
 * building.
 */
public record QueryBuildContext(Dialect dialect, boolean formattedSql) {

    public static QueryBuildContext of(Context<?> context) {
        return new QueryBuildContext(context.getDialect(),
            Boolean.TRUE.equals(context.getConfigValue(ConfigConstants.GENERATE_FORMATTED_SQL,
                ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class)));
    }

    /**
     * As {@link #of(Context)} but with an explicitly supplied {@link Dialect} — for the aggregate
     * query specs, whose authoritative dialect source is {@code star.getDialect()} (identical to
     * the context dialect in production, but independently stubbable in tests).
     */
    public static QueryBuildContext of(Dialect dialect, Context<?> context) {
        return new QueryBuildContext(dialect,
            Boolean.TRUE.equals(context.getConfigValue(ConfigConstants.GENERATE_FORMATTED_SQL,
                ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class)));
    }

    /** As {@link #of(Context)} with the formatted flag forced off (compact render). */
    public static QueryBuildContext compact(Dialect dialect) {
        return new QueryBuildContext(dialect, false);
    }

    /** The narrow capability view producers gate their PLANNER decisions on. */
    public SqlQueryCapabilities capabilities() {
        return SqlQueryCapabilities.of(dialect);
    }

    /**
     * Renders {@code candidate} (multi-line when {@link #formattedSql()}, else compact) and
     * packages the authoritative {@link BuiltSql}. The trace line's exact
     * {@code SqlBuildGuard: builder authoritative} prefix is greppable telemetry — the gate
     * tooling counts authoritative renders by it; its spelling is frozen for baseline
     * comparability even though the SqlBuildGuard class itself is gone.
     */
    public BuiltSql build(Supplier<SelectStatement> candidate) {
        RenderOptions opts = formattedSql ? RenderOptions.multiLine() : RenderOptions.compact();
        SelectStatement candidateStatement = candidate.get();
        RenderedSql rendered = SqlRender.render(candidateStatement, dialect, opts);
        // (sql() is already computed, so the parameterized call is cheap and needs no level guard.)
        RolapUtil.SQL_GEN_LOGGER.trace("SqlBuildGuard: builder authoritative NEW=[{}]", rendered.sql());
        return new BuiltSql(candidateStatement, rendered);
    }
}
