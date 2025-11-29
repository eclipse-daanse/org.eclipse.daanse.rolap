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
package org.eclipse.daanse.rolap.common.aggregator;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.AggregationFactory;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.aggregator.CustomAggregatorFactory;
import org.eclipse.daanse.rolap.aggregator.AvgAggregator;
import org.eclipse.daanse.rolap.aggregator.CountAggregator;
import org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator;
import org.eclipse.daanse.rolap.aggregator.MaxAggregator;
import org.eclipse.daanse.rolap.aggregator.MinAggregator;
import org.eclipse.daanse.rolap.aggregator.SumAggregator;
import org.eclipse.daanse.rolap.aggregator.experimental.BitAggAggregator;
import org.eclipse.daanse.rolap.aggregator.experimental.ListAggAggregator;
import org.eclipse.daanse.rolap.aggregator.experimental.NoneAggregator;
import org.eclipse.daanse.rolap.aggregator.experimental.NthValueAggregator;
import org.eclipse.daanse.rolap.aggregator.experimental.PercentileAggregator;
import org.eclipse.daanse.rolap.common.RolapOrderedColumn;
import org.eclipse.daanse.rolap.element.RolapColumn;

public class AggregationFactoryImpl implements AggregationFactory{

    private Dialect dialect;
    private List<CustomAggregatorFactory> customAggregators;

    public AggregationFactoryImpl(Dialect dialect, List<CustomAggregatorFactory> customAggregators) {
        this.dialect = dialect;
        this.customAggregators = customAggregators;
    }

    @Override
    public Aggregator getAggregator(Object measure) {
        return switch (measure) {
            case org.eclipse.daanse.rolap.mapping.model.SumMeasure i -> SumAggregator.INSTANCE;
            case org.eclipse.daanse.rolap.mapping.model.MaxMeasure i -> MaxAggregator.INSTANCE;
            case org.eclipse.daanse.rolap.mapping.model.MinMeasure i -> MinAggregator.INSTANCE;
            case org.eclipse.daanse.rolap.mapping.model.AvgMeasure i  -> AvgAggregator.INSTANCE;
            case org.eclipse.daanse.rolap.mapping.model.CountMeasure i -> getCountAggregator(i);
            case org.eclipse.daanse.rolap.mapping.model.TextAggMeasure i -> getListAggAggregator(i);
            case org.eclipse.daanse.rolap.mapping.model.NoneMeasure i -> NoneAggregator.INSTANCE;
            case org.eclipse.daanse.rolap.mapping.model.BitAggMeasure i -> getBitAggAggregator(i);
            case org.eclipse.daanse.rolap.mapping.model.PercentileMeasure i -> getPercentileAggregator(i);
            case org.eclipse.daanse.rolap.mapping.model.CustomMeasure i -> findCustomAggregator(i);
            case org.eclipse.daanse.rolap.mapping.model.NthAggMeasure i -> getNthValueAggregator(i);
            default -> throw new RuntimeException("Incorect aggregation type");
        };
    }

    private Aggregator getCountAggregator(org.eclipse.daanse.rolap.mapping.model.CountMeasure i) {
        return (i.isDistinct() ? DistinctCountAggregator.INSTANCE : CountAggregator.INSTANCE);
    }

    private Aggregator getListAggAggregator(org.eclipse.daanse.rolap.mapping.model.TextAggMeasure i) {
        return new ListAggAggregator(i.isDistinct(), i.getSeparator(), getOrderedColumns(i.getOrderByColumns()), i.getCoalesce(), i.getOnOverflowTruncate(), dialect);
    }

    private Aggregator getNthValueAggregator(org.eclipse.daanse.rolap.mapping.model.NthAggMeasure i) {
        return new NthValueAggregator(i.isIgnoreNulls(), i.getN(),
                getOrderedColumns(i.getOrderByColumns()), dialect);
    }

    private Aggregator getPercentileAggregator(org.eclipse.daanse.rolap.mapping.model.PercentileMeasure measure) {
    	org.eclipse.daanse.rolap.mapping.model.OrderedColumn oc = measure.getColumn();
            return new PercentileAggregator(measure.getPercentType(), measure.getPercentile(),
                    new RolapOrderedColumn(new RolapColumn(oc.getColumn().getTable().getName(), oc.getColumn().getName()), oc.isAscend()), dialect);
    }

    private Aggregator getBitAggAggregator(org.eclipse.daanse.rolap.mapping.model.BitAggMeasure measure) {
        return new BitAggAggregator(measure.isNot(), measure.getAggType(), dialect);
    }

    private List<RolapOrderedColumn> getOrderedColumns(List<? extends org.eclipse.daanse.rolap.mapping.model.OrderedColumn> orderByColumns) {
        if (orderByColumns != null) {
            return orderByColumns.stream().map(oc -> new RolapOrderedColumn(new RolapColumn(oc.getColumn().getTable().getName(), oc.getColumn().getName()), oc.isAscend())).toList();
        }
        return List.of();
    }

    private Aggregator findCustomAggregator(org.eclipse.daanse.rolap.mapping.model.CustomMeasure i) {
        Optional<CustomAggregatorFactory> oAggregator = customAggregators.stream().filter(ca -> i.getName().equals(ca.getName())).findAny();
        if (oAggregator.isPresent()) {
            List<Object> l = (i.getColumns() == null) ? List.of() : i.getColumns().stream().map(Object.class::cast).toList();
            return oAggregator.get().getAggregator(i.getTemplate(), i.getProperties(), l);
            //TODO not use object
        }
        throw new RuntimeException("Custom aggregator with name " + i.getName() + "not found");
    }
}
