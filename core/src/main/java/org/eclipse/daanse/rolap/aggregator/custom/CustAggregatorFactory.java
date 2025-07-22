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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.aggregator.custom;

import java.util.List;

import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.aggregator.CustomAggregatorFactory;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.rolap.common.RolapColumn;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

@Component(service = CustomAggregatorFactory.class, scope = ServiceScope.SINGLETON)
public class CustAggregatorFactory implements CustomAggregatorFactory{

    @Override
    public String getName() {
        return "Custom";
    }
    
    @Override
    public Aggregator getAggregator(String template,
            List<String> properties, List<Object> columns) {
        return new Aggregator () {

            @Override
            public String getName() {
                return "Custom";
            }

            @Override
            public Aggregator getRollup() {
                return this;
            }

            @Override
            public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> calc) {
                return null;
            }

            @Override
            public boolean supportsFastAggregates(DataTypeJdbc datatype) {
                return true;
            }

            @Override
            public Object aggregate(List<Object> rawData, DataTypeJdbc datatype) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StringBuilder getExpression(CharSequence inner) {
                return new StringBuilder("42");
            }

            @Override
            public boolean isDistinct() {
                return false;
            }

            @Override
            public Aggregator getNonDistinctAggregator() {
                return null;
            }
            
        };
    }


}
