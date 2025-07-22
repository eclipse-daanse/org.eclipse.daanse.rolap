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

import java.security.SecureRandom;

import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.rolap.aggregator.AbstractAggregator;

public class RndAggregator extends AbstractAggregator {

    public static RndAggregator INSTANCE = new RndAggregator();

    private SecureRandom secureRandom = new SecureRandom();

    public RndAggregator() {
        super("Rnd", false);
    }

    public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> calc) {

        return secureRandom.nextLong();
    }

    public String getExpression(String operand) {
        return secureRandom.nextLong() + "";
    }

    public boolean supportsFastAggregates(DataTypeJdbc dataType) {
        return true;
    }
}
