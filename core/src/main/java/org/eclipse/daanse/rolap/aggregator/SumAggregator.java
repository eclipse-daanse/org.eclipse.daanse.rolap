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
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.calc.base.CompensatedSum;
import org.eclipse.daanse.olap.fun.FunUtil;

public class SumAggregator extends AbstractAggregator {

    public static SumAggregator INSTANCE = new SumAggregator();

    public SumAggregator() {
        super("sum", false);
    }

    @Override
    public org.eclipse.daanse.sql.statement.api.expression.SqlExpression toNode(
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression inner) {
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression operand = nodeOperand(inner);
        return org.eclipse.daanse.sql.statement.api.Expressions.aggregate(getName(), operand);
    }

    @Override
    public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> exp) {
        return FunUtil.sum(evaluator, members, exp);
    }

    @Override
    public boolean supportsFastAggregates(DataTypeJdbc dataType) {
        return switch (dataType) {
        case INTEGER, NUMERIC -> true;
        default -> false;
        };
    };

    @Override
    public Object aggregate(List<Object> rawData, DataTypeJdbc datatype) {
        assert !rawData.isEmpty();
        switch (datatype) {
        case INTEGER:
            Integer sumInt = null;
            for (Object data : rawData) {
                if (data != null) {
                    if (data instanceof Double) {
                        data = ((Double) data).intValue();
                    }
                    sumInt = sumInt == null ? (Integer) data : sumInt + (Integer) data;
                }
            }
            return sumInt;
        case NUMERIC:
            CompensatedSum sumDouble = null;
            for (Object data : rawData) {
                if (data != null) {
                    if (sumDouble == null) {
                        sumDouble = new CompensatedSum();
                    }
                    sumDouble.add(((Number) data).doubleValue());
                }
            }
            return sumDouble == null ? null : sumDouble.value();
        default:
            throw new OlapRuntimeException(new StringBuilder("Aggregator ").append(name)
                    .append(" does not support datatype").append(datatype.getValue()).toString());
        }
    }
}