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

import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

/**
 * Aggregator used for aggregate tables implementing the average aggregator.
 *
 *
 * It uses the aggregate table fact_count column and a sum measure to create the
 * query used to generate an average:
 *    avg == sum(column_sum) / sum(factcount).
 *
 *
 *
 * If the fact table has both a sum and average over the same column and the
 * aggregate table only has a sum and fact count column, then the average
 * aggregator can be generated using this aggregator.
 */
public class AvgFromSumAggregator extends AbstractFactCountBasedAggregator {

    public AvgFromSumAggregator(String factCountExpr, SqlExpression factCountNode) {
        super("AvgFromSum", factCountExpr, factCountNode);
    }

    @Override
    public StringBuilder getExpression(CharSequence operand) {
        // sum(x) * 1e0 / sum(fc): the 1e0 factor forces approximate-numeric division on
        // every dialect. A bare decimal/integer division truncates on Derby/ClickHouse
        // (result-scale truncation) and SQLite (integer division), while MySQL rounds at
        // scale+4 — the double division makes the rollup value dialect-independent.
        StringBuilder buf = new StringBuilder(64);
        buf.append("sum(");
        buf.append(operand);
        buf.append(") * 1e0 / sum(");
        buf.append(factCountExpr);
        buf.append(')');
        return buf;
    }

    @Override
    public SqlExpression toNode(SqlExpression inner) {
        SqlExpression operand = nodeOperand(inner);
        // sum(<operand>) * 1e0 / sum(<factCount>) — non-parenthesized, matching the string form;
        // see getExpression(CharSequence) for the 1e0 rationale.
        return Expressions.infix(
            Expressions.infix(
                Expressions.aggregate("sum", operand),
                ArithmeticOperator.MULTIPLY,
                Expressions.raw("1e0")),
            ArithmeticOperator.DIVIDE,
            Expressions.aggregate("sum", factCountNode));
    }

    @Override
    public boolean alwaysRequiresFactColumn() {
        return true;
    }

    @Override
    public String getScalarExpression(String operand) {
        return new StringBuilder(64).append('(').append(operand).append(") / (").append(factCountExpr).append(')')
                .toString();
    }

    @Override
    public SqlExpression getScalarNode(SqlExpression operand) {
        // (<operand>) / (<factCount>) — each side individually parenthesized (empty-name Function renders
        // exactly "(x)"), joined by a NON-parenthesized infix "/".
        return Expressions.infix(
            Expressions.function("", operand),
            ArithmeticOperator.DIVIDE,
            Expressions.function("", factCountNode));
    }
}