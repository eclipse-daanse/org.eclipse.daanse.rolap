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
import org.eclipse.daanse.jdbc.db.dialect.api.OrderedColumn;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.rolap.common.RolapColumn;
import org.eclipse.daanse.rolap.common.RolapOrderedColumn;

public class ListAggAggregator implements Aggregator {

    private boolean distinct;
    private String separator;
    private List<RolapOrderedColumn> columns;
    private String coalesce;
    private String onOverflowTruncate;
    private Dialect dialect;
    
    //
    public ListAggAggregator(boolean distinct, String separator, List<RolapOrderedColumn> columns, String coalesce, String onOverflowTruncate, Dialect dialect) {
        this.distinct = distinct;
        this.separator = separator;
        this.columns = columns;
        this.coalesce = coalesce;
        this.onOverflowTruncate = onOverflowTruncate;
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
        List<OrderedColumn> columnsList = List.of();
        if (this.columns != null) {
            columnsList = this.columns.stream().map(c -> new OrderedColumn(c.getColumn().getName(), c.getColumn().getTable(), c.isAscend())).toList();
        }
        return this.dialect.generateListAgg(operand, distinct, separator, coalesce, onOverflowTruncate, columnsList);
    }

    @Override
    public boolean supportsFastAggregates(DataTypeJdbc dataType) {
        // Usually no, because we need the actual "first" item, which
        // cannot be computed by typical pre-aggregation statistics.
        return false;
    }

    @Override
    public String getName() {
        return "LISTAGG";
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
        return this.distinct;
    }

    @Override
    public Aggregator getNonDistinctAggregator() {
        return null;
    }
}