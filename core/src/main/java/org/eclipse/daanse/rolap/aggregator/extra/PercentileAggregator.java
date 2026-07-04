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

import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.rolap.aggregator.NodeAggregate;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.PercentType;
import org.eclipse.daanse.rolap.mapping.model.database.relational.SortingDirection;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;


public class PercentileAggregator implements Aggregator, NodeAggregate {

    private Double percentile;
    private PercentType percentileType;
    private RolapColumn rolapOrderedColumn;


    public PercentileAggregator(PercentType percentileType, Double percentile,
            RolapColumn rolapOrderedColumn) {
        this.percentile = percentile;
        this.percentileType = percentileType;
        this.rolapOrderedColumn = rolapOrderedColumn;
    }

    @Override
    public SqlExpression toNode(SqlExpression operand) {
        return new SqlExpression.ExtraAggregate(Optional.empty(),
                new SqlExpression.ExtraAggregate.Spec.Percentile(percentile,
                        percentileType == PercentType.CONT,
                        SortingDirection.DESC.equals(rolapOrderedColumn.getSortingDirection()),
                        rolapOrderedColumn.getTable(), rolapOrderedColumn.getName()));
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
        return "PERCENTILE";
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
