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

import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/** The OLAP distinct count, rendered as SQL {@code count(distinct operand)}. */
public class DistinctCountAggregator extends org.eclipse.daanse.olap.aggregator.DistinctCountAggregator implements SqlNodeAggregator {

    public static final DistinctCountAggregator INSTANCE = new DistinctCountAggregator();

    @Override
    public Aggregator getRollup() {
        return SumAggregator.INSTANCE;
    }

    @Override
    public Aggregator getNonDistinctAggregator() {
        return CountAggregator.INSTANCE;
    }

    @Override
    public StringBuilder getExpression(CharSequence operand) {
        return new StringBuilder("count(distinct ").append(operand).append(")");
    }

    @Override
    public SqlExpression toNode(SqlExpression inner) {
        // renders "count(distinct <operand>)" (NOT "distinct-count(...)") — use the literal "count".
        return Expressions.aggregateDistinct("count", SqlNodeAggregator.nodeOperand(inner));
    }
}
