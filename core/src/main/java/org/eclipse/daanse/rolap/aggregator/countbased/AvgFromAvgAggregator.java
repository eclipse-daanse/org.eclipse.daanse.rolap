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

    public AvgFromAvgAggregator(String factCountExpr) {
        super("AvgFromAvg", factCountExpr);
    }

    @Override
    public StringBuilder getExpression(CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        buf.append("sum(");
        buf.append(operand);
        buf.append(" * ");
        buf.append(factCountExpr);
        buf.append(") / sum(");
        buf.append(factCountExpr);
        buf.append(')');
        return buf;
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
}
