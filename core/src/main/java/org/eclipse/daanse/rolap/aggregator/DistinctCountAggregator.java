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
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;

public class DistinctCountAggregator extends AbstractAggregator {

    public static DistinctCountAggregator INSTANCE = new DistinctCountAggregator();

    public DistinctCountAggregator() {
        super("distinct-count", true);
    }

    @Override
    public Aggregator getRollup() {
      // Distinct counts cannot always be rolled up, when they can,
      // it's using Sum.
      return SumAggregator.INSTANCE;
    }

    @Override
    public AbstractAggregator getNonDistinctAggregator() {
      return CountAggregator.INSTANCE;
    }

    @Override
    public Object aggregate( Evaluator evaluator, TupleList members, Calc<?> exp ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public StringBuilder getExpression( CharSequence operand ) {
      return new StringBuilder("count(distinct ").append(operand).append(")");
    }

    @Override
    public boolean supportsFastAggregates( DataTypeJdbc dataType ) {
      // We can't rollup using the raw data, because this is
      // a distinct-count operation.
      return false;
    };

}
