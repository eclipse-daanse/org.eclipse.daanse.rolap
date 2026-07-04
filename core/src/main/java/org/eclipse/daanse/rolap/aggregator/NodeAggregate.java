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
package org.eclipse.daanse.rolap.aggregator;

import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/**
 * Implemented by aggregators that can emit a <em>dialect-free</em> builder node for their SQL instead of a
 * dialect-rendered string. {@code DialectSqlRenderer} turns the node into dialect SQL at render time, so the
 * aggregator no longer needs to hold a {@code Dialect}. Used by the dialect-generator aggregators
 * (PERCENTILE / LISTAGG / bitwise / NTH_VALUE), which carry their parameters into a
 * {@code SqlExpression.ExtraAggregate} node.
 */
public interface NodeAggregate {

    /**
     * The dialect-free builder node for this aggregate over {@code operand} — which may be {@code null} for
     * aggregators that take their ordered column directly (e.g. PERCENTILE).
     */
    SqlExpression toNode(SqlExpression operand);
}
