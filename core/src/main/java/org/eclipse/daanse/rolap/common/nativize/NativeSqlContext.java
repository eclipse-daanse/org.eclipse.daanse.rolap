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
package org.eclipse.daanse.rolap.common.nativize;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.element.RolapHierarchy;

/**
 * The minimal seam {@link RolapNativeSql} needs from a query while it compiles a native
 * filter / top-count expression: the {@link Dialect}, a way to ensure a level's table is joined
 * into the FROM, and the SELECT alias of a (possibly) projected expression.
 * <p>
 * This lets {@code RolapNativeSql} run against a throwaway scratch context — {@link #scratch(Dialect)}
 * (or {@link #scratchCollecting(Dialect, java.util.function.BiConsumer)}) — used by the native
 * {@code createEvaluator} convertibility checks and by the {@code toContribution} builder channel
 * (Filter HAVING node, feasibility checks).
 */
public interface NativeSqlContext {

    Dialect getDialect();

    /** Ensure the table that {@code expression}'s column belongs to is joined into the FROM. */
    void addToFrom(RolapHierarchy hierarchy, SqlExpression expression);

    /** The SELECT alias previously assigned to {@code expression} (resolved dialect-free by builder node), or
     *  {@code null} if none. */
    String getAlias(SqlExpression expression);

    /**
     * Scratch context for the native {@code createEvaluator} feasibility step. The compiled node it
     * helps build is DISCARDED, so the FROM need not be tracked ({@code addToFrom} is a no-op) and
     * nothing is projected ({@code getAlias} returns {@code null}). The node still renders correctly
     * because each column carries its own intrinsic table alias via
     * {@code SqlExpressionResolver.getExpression}.
     */
    static NativeSqlContext scratch(Dialect dialect) {
        return new NativeSqlContext() {
            @Override
            public Dialect getDialect() {
                return dialect;
            }

            @Override
            public void addToFrom(RolapHierarchy hierarchy, SqlExpression expression) {
                // The scratch query is never executed; its FROM is discarded.
            }

            @Override
            public String getAlias(SqlExpression expression) {
                // The scratch query projects nothing, so getAlias returns null here too.
                return null;
            }
        };
    }

    /**
     * Scratch context that COLLECTS the {@code addToFrom} side effects instead of dropping them —
     * used by the builder channel of a native-filter HAVING compile
     * ({@code FilterConstraint.levelMembersAggHavingWithJoins}). The compiled predicate node is
     * identical to {@link #scratch(Dialect)}'s (same null-alias compile); each
     * {@code addToFrom(hierarchy, expression)} call — the FROM join of a filter-referenced
     * dimension table ({@code RolapNativeSql}'s MATCHES compiler under an aggStar) — is
     * additionally reported to {@code joinSink}, so an agg consumer can replay it into its FROM
     * ({@code TupleSqlMapper.aggTupleLevelMembersSql} havingJoins). Without the replay the built
     * FROM misses the referenced table's snowflake subset.
     */
    static NativeSqlContext scratchCollecting(Dialect dialect,
            java.util.function.BiConsumer<RolapHierarchy, SqlExpression> joinSink) {
        return new NativeSqlContext() {
            @Override
            public Dialect getDialect() {
                return dialect;
            }

            @Override
            public void addToFrom(RolapHierarchy hierarchy, SqlExpression expression) {
                joinSink.accept(hierarchy, expression);
            }

            @Override
            public String getAlias(SqlExpression expression) {
                // Like scratch: nothing is projected; columns carry their intrinsic aliases.
                return null;
            }
        };
    }
}
