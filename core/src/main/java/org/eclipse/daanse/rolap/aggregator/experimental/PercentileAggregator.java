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
import org.eclipse.daanse.rolap.common.RolapOrderedColumn;
import org.eclipse.daanse.rolap.mapping.api.model.enums.PercentileType;

public class PercentileAggregator implements Aggregator {

    private Double percentile;
    private PercentileType percentileType;
    private RolapOrderedColumn rolapOrderedColumn;
    private Dialect dialect;


    public PercentileAggregator(PercentileType percentileType, Double percentile,
            RolapOrderedColumn rolapOrderedColumn, Dialect dialect) {
        this.percentile = percentile;
        this.percentileType = percentileType;
        this.rolapOrderedColumn = rolapOrderedColumn;
        this.dialect = dialect;
    }

    @Override
    public Object aggregate(Evaluator evaluator, TupleList members, Calc<?> exp) {
        //TODO
        return null;
    }

    @Override
    public StringBuilder getExpression(CharSequence operand) {
        StringBuilder buf = new StringBuilder(64);
        switch (percentileType) {
            case PercentileType.DISC:
                return dialect.generatePercentileDisc(this.percentile, !this.rolapOrderedColumn.isAscend(), 
                        this.rolapOrderedColumn.getColumn().getTable(), this.rolapOrderedColumn.getColumn().getName());
            case PercentileType.CONT:
                return dialect.generatePercentileCont(this.percentile, !this.rolapOrderedColumn.isAscend(), 
                        this.rolapOrderedColumn.getColumn().getTable(), this.rolapOrderedColumn.getColumn().getName());
        }
        return buf;

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
