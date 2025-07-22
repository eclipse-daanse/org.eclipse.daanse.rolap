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

import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.rolap.aggregator.AbstractAggregator;

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

    protected AbstractFactCountBasedAggregator(final String name, final String factCountExpr) {
        super(name, false);
        this.factCountExpr = factCountExpr;
    }

    @Override
    public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> exp) {
        throw new UnsupportedOperationException();
    }

    public abstract boolean alwaysRequiresFactColumn();

    public abstract String getScalarExpression(String operand);
}
