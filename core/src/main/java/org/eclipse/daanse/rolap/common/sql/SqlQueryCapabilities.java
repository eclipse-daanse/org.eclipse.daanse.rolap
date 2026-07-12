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
package org.eclipse.daanse.rolap.common.sql;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

/**
 * The narrow, producer-facing view of the dialect: exactly the capability probes the SQL
 * producers legitimately gate PLANNER decisions on (which query shape to build) — never how SQL
 * is spelled, which is the renderer's job behind {@code SqlRender}. Producers receive this
 * interface instead of the full {@link Dialect}; the confinement is pinned by
 * {@code DialectConfinementInvariantTest}.
 */
public interface SqlQueryCapabilities {

    /** {@code count(distinct x)} executes inline (no derived-table degradation needed). */
    boolean countDistinct();

    /** Several {@code count(distinct x)} projections may appear in one SELECT. */
    boolean multipleCountDistinct();

    /** {@code count(distinct x)} may be combined with other aggregates in one SELECT. */
    boolean countDistinctWithOtherAggs();

    /** Several distinct-count MEASURES may share one segment-load SELECT. */
    boolean multipleDistinctSqlMeasures();

    /** {@code count(distinct x, y)} over a compound (multi-column) key. */
    boolean compoundCountDistinct();

    /** {@code select distinct} is allowed inside a derived table. */
    boolean innerDistinct();

    /** A SELECT may appear in the FROM clause (derived table). */
    boolean fromQuery();

    /** Select-list items may carry {@code AS alias}. */
    boolean fieldAlias();

    /** {@code group by grouping sets (...)} executes natively. */
    boolean groupingSets();

    /** No practical limit on IN-list length (native set evaluation feasibility). */
    boolean unlimitedValueList();

    /** Regular-expression predicates are available in WHERE (native Filter feasibility). */
    boolean regexInWhere();

    /** A drill-through row limit must be inlined as a LIMIT clause (not setMaxRows). */
    boolean drillthroughMaxRowsInLimit();

    /** Maximum column-name/alias length; 0 = unlimited. */
    int maxColumnNameLength();

    static SqlQueryCapabilities of(Dialect dialect) {
        return new DialectSqlQueryCapabilities(dialect);
    }
}
