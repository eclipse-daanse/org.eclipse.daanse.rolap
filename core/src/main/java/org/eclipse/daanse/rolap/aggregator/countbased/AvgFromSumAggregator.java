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

    public AvgFromSumAggregator(String factCountExpr) {
        super("AvgFromSum", factCountExpr);
    }

    @Override
    public StringBuilder getExpression(CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        buf.append("sum(");
        buf.append(operand);
        buf.append(") / sum(");
        buf.append(factCountExpr);
        buf.append(')');
        return buf;
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
}