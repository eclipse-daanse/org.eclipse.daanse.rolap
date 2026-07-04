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
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.element.RolapHierarchy;

/**
 * The minimal seam {@link RolapNativeSql} needs from a query while it generates a native
 * filter / top-count condition string: the {@link Dialect}, a way to ensure a level's table is joined
 * into the FROM, and the SELECT alias of a (possibly) projected expression.
 * <p>
 * This lets {@code RolapNativeSql} run against either:
 * <ul>
 *   <li>the real {@code SqlTupleReader} query — {@link #ofRecorder(QueryRecorder, Dialect)} adapts the
 *       {@link QueryRecorder} the {@code addConstraintOps} callbacks record on (behaviour and emitted SQL
 *       identical to the former the retired query facade adaptation, whose facade entry (removed in P4-B4,
 *       Dialect)} now delegates here via the facade's recorder); or</li>
 *   <li>a throwaway scratch context — {@link #scratch(Dialect)} — used only by the native
 *       {@code createEvaluator} feasibility / string-generation step, whose SQL is DISCARDED (the real
 *       HAVING / ORDER BY is regenerated later against the executing query). No the retired query facade is
 *       constructed for that path.</li>
 * </ul>
 */
public interface NativeSqlContext {

    Dialect getDialect();

    /** Ensure the table that {@code expression}'s column belongs to is joined into the FROM. */
    void addToFrom(RolapHierarchy hierarchy, SqlExpression expression);

    /** The SELECT alias previously assigned to {@code expression} (resolved dialect-free by builder node), or
     *  {@code null} if none. */
    String getAlias(SqlExpression expression);

    /**
     * Adapter over the real executing {@link QueryRecorder} (typically the {@code addConstraintOps} fork) —
     * forwards every call, so behaviour and emitted SQL are byte-identical to the legacy path. The
     * {@link Dialect} is supplied by the caller (the query no longer carries one; it is resolved from the
     * same {@code Context}/{@code RolapStar} the query was built against) rather than read back off the
     * recorder.
     */
    static NativeSqlContext ofRecorder(QueryRecorder recorder, Dialect dialect) {
        return new NativeSqlContext() {
            @Override
            public Dialect getDialect() {
                return dialect;
            }

            @Override
            public void addToFrom(RolapHierarchy hierarchy, SqlExpression expression) {
                hierarchy.addToFrom(recorder, expression);
            }

            @Override
            public String getAlias(SqlExpression expression) {
                return recorder.getAlias(expression);
            }
        };
    }


    /**
     * Scratch context for the native {@code createEvaluator} feasibility step. The condition string it
     * helps build is DISCARDED, so the FROM need not be tracked ({@code addToFrom} is a no-op) and
     * nothing is projected ({@code getAlias} returns {@code null}) — exactly what the legacy scratch
     * the retired query facade did, since it never had any column added to its FROM/SELECT either. The string
     * still renders correctly because each column carries its own intrinsic table alias via
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
}
