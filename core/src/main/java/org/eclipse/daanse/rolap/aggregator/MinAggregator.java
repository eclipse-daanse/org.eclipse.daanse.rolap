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
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.fun.FunUtil;

public class MinAggregator extends AbstractAggregator {

    public static MinAggregator INSTANCE = new MinAggregator();

    public MinAggregator() {
        super("min", false);
    }

    @Override
    public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> exp) {
        return FunUtil.min(evaluator, members, exp);
    }

    @Override
    public boolean supportsFastAggregates(DataTypeJdbc dataType) {
        switch (dataType) {
        case INTEGER:
        case NUMERIC:
            return true;
        default:
            return false;
        }
    };

    @Override
    public Object aggregate(List<Object> rawData, DataTypeJdbc datatype) {
        assert rawData.size() > 0;
        switch (datatype) {
        case INTEGER:
            int minInt = Integer.MAX_VALUE;
            for (Object data : rawData) {
                if (data != null) {
                    minInt = Math.min(minInt, (Integer) data);
                }
            }
            return minInt == Integer.MAX_VALUE ? null : minInt;
        case NUMERIC:
            double minDouble = Double.MAX_VALUE;
            for (Object data : rawData) {
                if (data != null) {
                    minDouble = Math.min(minDouble, ((Number) data).doubleValue());
                }
            }
            return minDouble == Double.MAX_VALUE ? null : minDouble;
        default:
            throw new OlapRuntimeException(new StringBuilder("Aggregator ").append(this.name)
                    .append(" does not support datatype").append(datatype.getValue()).toString());
        }
    }
}