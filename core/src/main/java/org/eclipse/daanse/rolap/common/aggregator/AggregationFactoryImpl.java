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
import org.eclipse.daanse.rolap.aggregator.experimental.PercentileAggregator;
import org.eclipse.daanse.rolap.common.RolapColumn;
import org.eclipse.daanse.rolap.common.RolapOrderedColumn;
import org.eclipse.daanse.rolap.aggregator.experimental.ListAggAggregator;
import org.eclipse.daanse.rolap.aggregator.experimental.NoneAggregator;
import org.eclipse.daanse.rolap.aggregator.experimental.NthValueAggregator;
import org.eclipse.daanse.rolap.mapping.api.model.AvgMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.BitAggMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.CountMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.CustomMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.MaxMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.MinMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.NoneMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.NthAggMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.OrderedColumnMapping;
import org.eclipse.daanse.rolap.mapping.api.model.PercentileMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.SumMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.TextAggMeasureMapping;

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
            case SumMeasureMapping i -> SumAggregator.INSTANCE;
            case MaxMeasureMapping i -> MaxAggregator.INSTANCE;
            case MinMeasureMapping i -> MinAggregator.INSTANCE;
            case AvgMeasureMapping i  -> AvgAggregator.INSTANCE;
            case CountMeasureMapping i -> getCountAggregator(i);
            case TextAggMeasureMapping i -> getListAggAggregator(i);
            case NoneMeasureMapping i -> NoneAggregator.INSTANCE;
            case BitAggMeasureMapping i -> getBitAggAggregator(i);
            case PercentileMeasureMapping i -> getPercentileAggregator(i);
            case CustomMeasureMapping i -> findCustomAggregator(i);
            case NthAggMeasureMapping i -> getNthValueAggregator(i);
            default -> throw new RuntimeException("Incorect aggregation type");
        };
    }

    private Aggregator getCountAggregator(CountMeasureMapping i) {
        return (i.isDistinct() ? DistinctCountAggregator.INSTANCE : CountAggregator.INSTANCE);
    }

    private Aggregator getListAggAggregator(TextAggMeasureMapping i) {
        return new ListAggAggregator(i.isDistinct(), i.getSeparator(), getOrderedColumns(i.getOrderByColumns()), i.getCoalesce(), i.getOnOverflowTruncate(), dialect);
    }

    private Aggregator getNthValueAggregator(NthAggMeasureMapping i) {
        return new NthValueAggregator(i.isIgnoreNulls(), i.getN(),
                getOrderedColumns(i.getOrderByColumns()), dialect);
    }

    private Aggregator getPercentileAggregator(PercentileMeasureMapping measure) {
            OrderedColumnMapping oc = measure.getColumn();
            return new PercentileAggregator(measure.getPercentileType(), measure.getPercentile(),
                    new RolapOrderedColumn(new RolapColumn(oc.getColumn().getTable().getName(), oc.getColumn().getName()), oc.isAscend()), dialect);
    }

    private Aggregator getBitAggAggregator(BitAggMeasureMapping measure) {
        return new BitAggAggregator(measure.isNot(), measure.getBitAggType(), dialect);
    }

    private List<RolapOrderedColumn> getOrderedColumns(List<? extends OrderedColumnMapping> orderByColumns) {
        if (orderByColumns != null) {
            return orderByColumns.stream().map(oc -> new RolapOrderedColumn(new RolapColumn(oc.getColumn().getTable().getName(), oc.getColumn().getName()), oc.isAscend())).toList();
        }
        return List.of();
    }

    private Aggregator findCustomAggregator(CustomMeasureMapping i) {
        Optional<CustomAggregatorFactory> oAggregator = customAggregators.stream().filter(ca -> i.getName().equals(ca.getName())).findAny();
        if (oAggregator.isPresent()) {
            List<Object> l = (i.getColumns() == null) ? List.of() : i.getColumns().stream().map(Object.class::cast).toList();
            return oAggregator.get().getAggregator(i.getTemplate(), i.getProperties(), l);
            //TODO not use object
        }
        throw new RuntimeException("Custom aggregator with name " + i.getName() + "not found");
    }
}
