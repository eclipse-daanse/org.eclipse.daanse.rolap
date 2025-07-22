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

import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;

public class NoneAggregator extends AbstractAggregator {

    public NoneAggregator() {
        super("None", false);
    }

    public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> calc) {

        final Evaluator eval = evaluator.pushAggregation(members);
        eval.setNonEmpty(false);
        return eval.evaluateCurrent(); // never calc and cache always value from db.
    }

    public String getExpression(String operand) {
        return operand;
    }

    public boolean supportsFastAggregates(DataTypeJdbc dataType) {
        return false;
    }
}
