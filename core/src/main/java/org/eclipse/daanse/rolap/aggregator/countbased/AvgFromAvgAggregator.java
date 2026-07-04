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

/**
 * Aggregator used for aggregate tables implementing the average aggregator.
 *
 *
 * It uses the aggregate table fact_count column and an average measure to
 * create the query used to generate an average:
 *    avg == sum(column_sum * factcount) / sum(factcount).
 *
 *
 *
 * If the fact table has both a sum and average over the same column and the
 * aggregate table only has a average and fact count column, then the average
 * aggregator can be generated using this aggregator.
 */
public class AvgFromAvgAggregator extends AbstractFactCountBasedAggregator {

    public AvgFromAvgAggregator(String factCountExpr,
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression factCountNode) {
        super("AvgFromAvg", factCountExpr, factCountNode);
    }

    @Override
    public StringBuilder getExpression(CharSequence operand) {
        // sum(x * fc) * 1e0 / sum(fc): the 1e0 factor forces approximate-numeric division
        // on every dialect. A bare decimal division truncates on Derby/ClickHouse (result-
        // scale truncation) and SQLite (integer division), while MySQL rounds at scale+4 —
        // the double division makes the rollup value dialect-independent.
        StringBuilder buf = new StringBuilder(64);
        buf.append("sum(");
        buf.append(operand);
        buf.append(" * ");
        buf.append(factCountExpr);
        buf.append(") * 1e0 / sum(");
        buf.append(factCountExpr);
        buf.append(')');
        return buf;
    }

    @Override
    public org.eclipse.daanse.sql.statement.api.expression.SqlExpression getExpression(
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression inner) {
        // sum(<inner> * <factCount>) * 1e0 / sum(<factCount>) — non-parenthesized, matching the
        // string form; see getExpression(CharSequence) for the 1e0 rationale.
        var fc = factCountNode;
        return org.eclipse.daanse.sql.statement.api.Expressions.infix(
            org.eclipse.daanse.sql.statement.api.Expressions.infix(
                org.eclipse.daanse.sql.statement.api.Expressions.aggregate("sum",
                    org.eclipse.daanse.sql.statement.api.Expressions.infix(
                        inner, org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator.MULTIPLY, fc)),
                org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator.MULTIPLY,
                org.eclipse.daanse.sql.statement.api.Expressions.raw("1e0")),
            org.eclipse.daanse.sql.statement.api.expression.ArithmeticOperator.DIVIDE,
            org.eclipse.daanse.sql.statement.api.Expressions.aggregate("sum", fc));
    }

    @Override
    public boolean alwaysRequiresFactColumn() {
        return false;
    }

    @Override
    public String getScalarExpression(String operand) {
        throw new UnsupportedOperationException(
                "This method should not be invoked if alwaysRequiresFactColumn() is false");
    }

    @Override
    public org.eclipse.daanse.sql.statement.api.expression.SqlExpression getScalarNode(
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression operand) {
        throw new UnsupportedOperationException(
                "This method should not be invoked if alwaysRequiresFactColumn() is false");
    }
}
