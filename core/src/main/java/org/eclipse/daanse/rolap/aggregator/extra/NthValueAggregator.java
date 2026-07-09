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
package org.eclipse.daanse.rolap.aggregator.extra;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.sql.OrderedColumn;
import org.eclipse.daanse.jdbc.db.api.sql.SortDirection;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;

public class NthValueAggregator implements Aggregator, org.eclipse.daanse.rolap.aggregator.SqlNodeAggregator {

    private boolean ignoreNulls;
    private Integer n;
    private List<RolapColumn> rolapOrderedColumnList;


    public NthValueAggregator(boolean ignoreNulls, Integer n,
                              List<RolapColumn> rolapOrderedColumnList) {
        this.ignoreNulls = ignoreNulls;
        this.n = n;
        this.rolapOrderedColumnList = rolapOrderedColumnList;
    }

    @Override
    public SqlExpression toNode(SqlExpression operand) {
        List<OrderedColumn> columnsList = List.of();
        if (rolapOrderedColumnList != null) {
            columnsList = rolapOrderedColumnList.stream()
                .map(c -> new OrderedColumn(c.getName(), c.getTable(),
                        org.eclipse.daanse.olap.api.sql.SortingDirection.NONE.equals(c.getSortingDirection()) ? Optional.empty() : Optional.of(SortDirection.valueOf(c.getSortingDirection().name())),
                                Optional.empty()))
                .toList();
        }
        return new SqlExpression.ExtraAggregate(Optional.ofNullable(operand),
                new SqlExpression.ExtraAggregate.Spec.NthValue(ignoreNulls, n, columnsList));
    }

    @Override
    public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> exp) {
        //TODO
        return null;
    }

    @Override
    public StringBuilder getExpression(CharSequence operand) {
        return new StringBuilder(getName()).append('(').append(operand).append(')');
    }

    @Override
    public boolean supportsFastAggregates(DataTypeJdbc dataType) {
        // Usually no, because we need the actual "first" item, which
        // cannot be computed by typical pre-aggregation statistics.
        return false;
    }

    @Override
    public String getName() {
        return "NthValue";
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
