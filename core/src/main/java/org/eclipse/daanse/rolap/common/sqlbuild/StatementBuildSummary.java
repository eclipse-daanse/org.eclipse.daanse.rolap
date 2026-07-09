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

import java.time.Duration;

import org.eclipse.daanse.rolap.common.RolapUtil;

/**
 * One uniform per-statement summary line for the {@code daanse.sql.gen} build trace — the build-time
 * analogue of {@code SqlStatement.formatTimingStatus} (which reports execute+fetch time for the executed
 * statement). Emitted once at a producer's build return so the trace ends each statement with its shape.
 * <p>
 * The fields are gathered ONLY when {@link RolapUtil#SQL_GEN_LOGGER} is at DEBUG — callers must build this
 * record inside a {@code SQL_GEN_LOGGER.isDebugEnabled()} guard (counting columns/joins and measuring build
 * time must not run on the hot path when the channel is off).
 *
 * @param kind     {@code aggregate | drill-through | member-children | level-members | member-count}
 * @param context  inline context text, e.g. {@code cube=Sales level=[Time].[Month]} (NOT MDC)
 * @param groupBy  number of group-by columns
 * @param measures number of aggregated measures
 * @param filters  number of WHERE predicates (constraints + table filters)
 * @param joins    number of joined dimension tables
 * @param distinct number of distinct-count measures (0 = none)
 * @param path     verdict: {@code builder-authoritative} (recorder routes emit their decline
 *                 reason on the {@code daanse.sql.gen}/{@code daanse.sql.gen.bail} channels instead
 *                 of a summary line)
 * @param buildTime wall time around the build call (may be {@code null} if not measured)
 */
public record StatementBuildSummary(String kind, String context, int groupBy, int measures, int filters,
        int joins, int distinct, String path, Duration buildTime) {

    /** The single summary line, e.g.
     *  {@code built aggregate SELECT [cube=Sales]: groupBy=1 measures=2 filters=1 joins=2 distinct=0 path=builder-authoritative in 0.4ms}. */
    public String format() {
        StringBuilder b = new StringBuilder("built ").append(kind).append(" SELECT");
        if (context != null && !context.isBlank()) {
            b.append(" [").append(context).append(']');
        }
        b.append(": groupBy=").append(groupBy)
            .append(" measures=").append(measures)
            .append(" filters=").append(filters)
            .append(" joins=").append(joins)
            .append(" distinct=").append(distinct)
            .append(" path=").append(path);
        if (buildTime != null) {
            b.append(" in ").append(buildTime.toNanos() / 1_000_000.0).append("ms");
        }
        return b.toString();
    }

    /** Format and emit this summary to the {@code daanse.sql.gen} channel at DEBUG. Build the record (and
     *  thus do the counting/timing) only inside a {@link RolapUtil#SQL_GEN_LOGGER} {@code isDebugEnabled()}
     *  guard at the call site. */
    public void log() {
        RolapUtil.SQL_GEN_LOGGER.debug("{}", format());
    }
}
