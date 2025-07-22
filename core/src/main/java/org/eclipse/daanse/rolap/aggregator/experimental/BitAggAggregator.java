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
package org.eclipse.daanse.rolap.aggregator.experimental;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.rolap.mapping.api.model.enums.BitAggregationType;

public class BitAggAggregator implements Aggregator {

    private boolean not;
    private BitAggregationType bitAggType;
    private Dialect dialect;

    //
    public BitAggAggregator(boolean not, BitAggregationType bitAggType, Dialect dialect) {
        this.not = not;
        this.bitAggType = bitAggType;
        this.dialect = dialect;
    }

    @Override
    public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> exp) {
        // We iterate in the natural order in which the members appear in the TupleList.
        for (int i = 0; i < members.size(); i++) {
            evaluator.setContext(members.get(i));
            Object value = exp.evaluate(evaluator);
            // Return the first encountered non-null value
            if (value != null) {
                return value;
            }
        }
        // If everything is null or the collection is empty, return null
        return null;
    }

    @Override
    public StringBuilder getExpression(CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        switch (bitAggType) {
            case BitAggregationType.AND:
                return and(operand);
            case BitAggregationType.OR:
                return or(operand);
            case BitAggregationType.XOR:
                return xor(operand);
        }
        return buf;

    }

    private StringBuilder and(CharSequence operand) {
        if (not) {
            return dialect.generateNAndBitAggregation(operand);
        } else {
            return dialect.generateAndBitAggregation(operand);
        }
    }

    private StringBuilder or(CharSequence operand) {
        if (not) {
            return dialect.generateNOrBitAggregation(operand);
        } else {
            return dialect.generateOrBitAggregation(operand);
        }
    }

    private StringBuilder xor(CharSequence operand) {
        if (not) {
            return dialect.generateNXorBitAggregation(operand);
        } else {
            return dialect.generateXorBitAggregation(operand);
        }
    }

    @Override
    public boolean supportsFastAggregates(DataTypeJdbc dataType) {
        // Usually no, because we need the actual "first" item, which
        // cannot be computed by typical pre-aggregation statistics.
        return false;
    }

    @Override
    public String getName() {
        return "BITAGG";
    }

    @Override
    public Aggregator getRollup() {
        return this;
    }

    @Override
    public Object aggregate(List<Object> rawData, DataTypeJdbc datatype) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDistinct() {
        return false;
    }

    @Override
    public Aggregator getNonDistinctAggregator() {
        return null;
    }
}
