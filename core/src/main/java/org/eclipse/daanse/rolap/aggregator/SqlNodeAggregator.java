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

import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/**
 * Rolap-side capability of an {@link Aggregator} that can render itself as a dialect-free SQL
 * builder node. Keeping this on the rolap side lets the OLAP {@link Aggregator} API stay free of the
 * SQL statement model — only rolap aggregators carry the node form. Callers reach it through
 * {@link #toNodeOrNull(Aggregator, SqlExpression)}.
 */
public interface SqlNodeAggregator {

    /**
     * The dialect-free builder-node form of the aggregate, or {@code null} when this aggregator has
     * no node form (the caller then falls back to the rendered-string channel). {@code operand} is
     * the measure column node; some aggregators ignore it (e.g. PERCENTILE takes its ordered column
     * directly). The {@code DialectSqlRenderer} spells the node per dialect at render time, so no
     * aggregator needs to hold a {@code Dialect}.
     */
    SqlExpression toNode(SqlExpression operand);

    /**
     * Returns {@code aggregator}'s node form when it is a {@link SqlNodeAggregator}, else
     * {@code null} — custom user aggregators keep their own dialect-opaque String templates, so the
     * caller falls back to the rendered-string channel.
     */
    static SqlExpression toNodeOrNull(Aggregator aggregator, SqlExpression operand) {
        return aggregator instanceof SqlNodeAggregator na ? na.toNode(operand) : null;
    }
}
