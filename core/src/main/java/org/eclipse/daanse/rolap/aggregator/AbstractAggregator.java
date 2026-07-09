/*
* Copyright (c) 2025 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   SmartCity Jena - initial
*/
package org.eclipse.daanse.rolap.aggregator;

import java.util.List;

import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;

public abstract class AbstractAggregator implements Aggregator, SqlNodeAggregator {

    protected String name;
    private int ordinal;
    private String description;

    private final boolean distinct;

    protected AbstractAggregator(String name, boolean distinct) {
        this.name = name;
        description = null;
        this.distinct = distinct;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public String getName() {
        return name;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns the expression to apply this aggregator to an operand. For example,
     * getExpression("emp.sal") returns "sum(emp.sal)".
     */
    @Override
    public StringBuilder getExpression(CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        buf.append(name);
        buf.append('(');
        if (distinct) {
            buf.append("distinct ");
        }
        buf.append(operand);
        buf.append(')');
        return buf;
    }

    /**
     * The {@link #toNode} operand for simple {@code name(operand)} aggregators: a {@code null}
     * operand means "no measure column" and renders as {@code *} (e.g. {@code count(*)}).
     */
    protected static org.eclipse.daanse.sql.statement.api.expression.SqlExpression nodeOperand(
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression inner) {
        return inner == null ? org.eclipse.daanse.sql.statement.api.Expressions.star() : inner;
    }

    /**
     * Returns the aggregate expression as a dialect-free SQL node: a plain {@code name(operand)}
     * aggregate call over {@link #getName()}. This covers the simple non-distinct aggregators
     * ({@code None}, {@code Ipp}, {@code Rnd}). DISTINCT aggregators return {@code null} here (no
     * node form; the String form is used) because a distinct node spelling is a per-aggregator
     * choice ({@code distinct-count} renders {@code count(distinct …)}, not
     * {@code distinct-count(distinct …)}), so a distinct aggregator that needs a node MUST override
     * this. Custom user aggregators keep their own dialect-opaque String templates (a render-time
     * callback node is neither cache- nor equality-safe).
     */
    @Override
    public org.eclipse.daanse.sql.statement.api.expression.SqlExpression toNode(
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression operand) {
        if (distinct) {
            return null;
        }
        return org.eclipse.daanse.sql.statement.api.Expressions.aggregate(getName(), nodeOperand(operand));
    }

    /**
     * If this is a distinct aggregator, returns the corresponding non-distinct
     * aggregator, otherwise throws an error.
     */
    public AbstractAggregator getNonDistinctAggregator() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the aggregator used to roll up. By default, aggregators roll up
     * themselves.
     */
    @Override
    public Aggregator getRollup() {
        return this;
    }

    /**
     * By default, fast rollup is not supported for all classes.
     */
    @Override
    public boolean supportsFastAggregates(DataTypeJdbc dataType) {
        return false;
    }

    @Override
    public Object aggregate(List<Object> rawData, DataTypeJdbc datatype) {
        throw new UnsupportedOperationException();
    }
}
