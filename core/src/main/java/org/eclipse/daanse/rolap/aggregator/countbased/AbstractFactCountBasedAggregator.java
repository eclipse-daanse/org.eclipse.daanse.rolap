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
package org.eclipse.daanse.rolap.aggregator.countbased;

import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.rolap.aggregator.AbstractAggregator;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/**
 * This is the base class for implementing aggregators over sum and average
 * columns in an aggregate table. These differ from the above aggregators in
 * that these require not only the operand to create the aggregation String
 * expression, but also, the aggregate table's fact count column expression.
 * These aggregators are NOT singletons like the above aggregators; rather, each
 * is different because of the fact count column expression.
 */
public abstract class AbstractFactCountBasedAggregator extends AbstractAggregator {

    protected final String factCountExpr;

    /** Dialect-free SQL node for the agg table's fact-count column. */
    protected final SqlExpression factCountNode;

    protected AbstractFactCountBasedAggregator(final String name, final String factCountExpr,
            final SqlExpression factCountNode) {
        super(name, false);
        this.factCountExpr = factCountExpr;
        this.factCountNode = factCountNode;
    }

    @Override
    public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> exp) {
        throw new UnsupportedOperationException();
    }

    public abstract boolean alwaysRequiresFactColumn();

    public abstract String getScalarExpression(String operand);

    /**
     * Returns the scalar aggregate as a dialect-free SQL node: composes {@code operand} with
     * {@link #factCountNode} (each side individually parenthesized via the empty-name Function paren
     * wrapper, joined by a non-parenthesized infix). Only defined where
     * {@link #alwaysRequiresFactColumn()} is {@code true}.
     */
    public abstract SqlExpression getScalarNode(SqlExpression operand);
}
