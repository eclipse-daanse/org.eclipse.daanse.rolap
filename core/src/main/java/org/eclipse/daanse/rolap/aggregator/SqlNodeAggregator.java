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
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/**
 * Rolap-side capability of an {@link Aggregator} that renders itself as SQL: as a dialect-free
 * builder node and as an expression string. The OLAP {@link Aggregator} API carries no SQL — only
 * rolap aggregators do. Callers reach it through {@link #toNodeOrNull(Aggregator, SqlExpression)}
 * and {@link #expression(Aggregator, CharSequence)}.
 */
public interface SqlNodeAggregator extends Aggregator {

    /**
     * The dialect-free builder-node form of the aggregate, or {@code null} when this aggregator has
     * no node form (the caller then falls back to the rendered-string channel). {@code operand} is
     * the measure column node; some aggregators ignore it (e.g. PERCENTILE takes its ordered column
     * directly). The {@code DialectSqlRenderer} spells the node per dialect at render time, so no
     * aggregator needs to hold a {@code Dialect}.
     */
    default SqlExpression toNode(SqlExpression operand) {
        if (isDistinct()) {
            return null;
        }
        return Expressions.aggregate(getName(), nodeOperand(operand));
    }

    /**
     * The SQL expression string of the aggregate over {@code operand}, for example
     * {@code sum(operand)}; the string channel of custom aggregators and of {@code AggGen}.
     */
    default StringBuilder getExpression(CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        buf.append(getName());
        buf.append('(');
        if (isDistinct()) {
            buf.append("distinct ");
        }
        buf.append(operand);
        buf.append(')');
        return buf;
    }

    /** The operand node, {@code *} when there is none. */
    static SqlExpression nodeOperand(SqlExpression inner) {
        return inner == null ? Expressions.star() : inner;
    }

    /**
     * The SQL expression string of {@code aggregator} over {@code operand}.
     *
     * @throws IllegalArgumentException when the aggregator does not render SQL
     */
    static StringBuilder expression(Aggregator aggregator, CharSequence operand) {
        if (aggregator instanceof SqlNodeAggregator sql) {
            return sql.getExpression(operand);
        }
        throw new IllegalArgumentException("Aggregator " + aggregator.getName() + " does not render SQL");
    }

    /**
     * Returns {@code aggregator}'s node form when it is a {@link SqlNodeAggregator}, else
     * {@code null} — custom user aggregators keep their own dialect-opaque String templates, so the
     * caller falls back to the rendered-string channel.
     */
    static SqlExpression toNodeOrNull(Aggregator aggregator, SqlExpression operand) {
        return aggregator instanceof SqlNodeAggregator na ? na.toNode(operand) : null;
    }

    /** A printable form for logs and {@code toString}; never throws, also for an aggregator without SQL. */
    static String describe(Aggregator aggregator, CharSequence operand) {
        return aggregator instanceof SqlNodeAggregator sql
                ? sql.getExpression(operand).toString()
                : aggregator.getName() + "(" + operand + ")";
    }
}
