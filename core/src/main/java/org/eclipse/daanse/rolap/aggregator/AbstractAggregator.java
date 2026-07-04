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

public abstract class AbstractAggregator implements Aggregator {

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
     * Dialect-free node form of {@link #getExpression(CharSequence)}, for callers that already hold a builder
     * {@link org.eclipse.daanse.sql.statement.api.expression.SqlExpression} for the operand. Returns
     * {@code null} when this aggregator cannot be represented as a simple {@code name(operand)} builder node
     * (composite count-based or dialect-generator aggregators) — the caller then falls back to the
     * {@link CharSequence} string form. The simple {@code name(operand)} aggregators override this.
     */
    public org.eclipse.daanse.sql.statement.api.expression.SqlExpression getExpression(
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression inner) {
        return null;
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
