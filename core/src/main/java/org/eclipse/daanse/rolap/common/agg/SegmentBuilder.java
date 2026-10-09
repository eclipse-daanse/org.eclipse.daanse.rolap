/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.common.agg;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.spi.body.DenseIntSegmentBody;
import org.eclipse.daanse.olap.spi.body.DenseDoubleSegmentBody;
import org.eclipse.daanse.olap.spi.body.DenseObjectSegmentBody;
import org.eclipse.daanse.olap.spi.body.SparseSegmentBody;

import static org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.genericSql;

import java.math.BigInteger;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.result.NullValue;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.key.CellKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentColumn;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.spi.SegmentPredicate;
import org.eclipse.daanse.olap.spi.SegmentRegion;
import org.eclipse.daanse.olap.util.ArraySortedSet;
import org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.EnumConvertor;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.agg.Segment.ExcludedRegion;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class that contains methods to convert between
 * {@link Segment} and {@link SegmentHeader}, and also
 * {@link SegmentWithData} and {@link SegmentBody}.
 *
 * @author LBoudreau
 */
public class SegmentBuilder {

    private static final String LOG_FORMAT_STRING = """
        column.columnExpression=%s
        column.valueCount=%s
        column.values=%s
        requestedValues=%s
        valueSet=%s
        values=%s
        hasNull=%b
        src=%d
        lostPredicate=%b
        """;
    private static final Logger LOGGER =
        LoggerFactory.getLogger(SegmentBuilder.class);
    /**
     * Converts a segment plus a {@link SegmentBody} into a
     * {@link SegmentWithData}.
     *
     * @param segment Segment
     * @param sb Segment body
     * @return SegmentWithData
     */
    public static SegmentWithData addData(Segment segment, SegmentBody sb) {
        // Load the axis keys for this segment
        SegmentAxis[] axes =
            new SegmentAxis[segment.predicates.length];
        for (int i = 0; i < segment.predicates.length; i++) {
            StarColumnPredicate predicate =
                segment.predicates[i];
            axes[i] =
                new SegmentAxis(
                    predicate,
                    sb.getAxisValueSets()[i],
                    sb.getNullAxisFlags()[i]);
        }
        final SegmentDataset dataSet = createDataset(sb, axes);
        return new SegmentWithData(segment, dataSet, axes);
    }

    /**
     * Creates a SegmentDataset that contains the cached
     * data and is initialized to be used with the supplied segment.
     *
     * @param body Segment with which the returned dataset will be associated
     * @param axes Segment axes, containing actual column values
     * @return A SegmentDataset object that contains cached data.
     */
    private static SegmentDataset createDataset(
        SegmentBody body,
        SegmentAxis[] axes)
    {
        final SegmentDataset dataSet;
        if (body instanceof DenseDoubleSegmentBody) {
            dataSet =
                new DenseDoubleSegmentDataset(
                    axes,
                    (double[]) body.getValueArray(),
                    body.getNullValueIndicators());
        } else if (body instanceof DenseIntSegmentBody) {
            dataSet =
                new DenseIntSegmentDataset(
                    axes,
                    (int[]) body.getValueArray(),
                    body.getNullValueIndicators());
        } else if (body instanceof DenseObjectSegmentBody) {
            dataSet =
                new DenseObjectSegmentDataset(
                    axes, (Object[]) body.getValueArray());
        } else if (body instanceof SparseSegmentBody) {
            dataSet = new SparseSegmentDataset(body.getValueMap());
        } else {
            throw Util.newInternal(
                new StringBuilder("Unknown segment body type: ").append(body.getClass()).append(": ").append(body).toString());
        }
        return dataSet;
    }

    /**
     * Creates a segment from a SegmentHeader. The star,
     * constrainedColsBitKey, constrainedColumns and measure arguments are a
     * helping hand, because we know what we were looking for.
     *
     * @param header The header to convert.
     * @param star Star
     * @param constrainedColumnsBitKey Constrained columns
     * @param constrainedColumns Constrained columns
     * @param measure Measure
     * @return Segment
     */
    public static Segment toSegment(
        SegmentHeader header,
        RolapStar star,
        BitKey constrainedColumnsBitKey,
        RolapStar.Column[] constrainedColumns,
        RolapStar.Measure measure,
        List<StarPredicate> compoundPredicates)
    {
        final List<StarColumnPredicate> predicateList =
            new ArrayList<>();
        for (int i = 0; i < constrainedColumns.length; i++) {
            RolapStar.Column constrainedColumn = constrainedColumns[i];
            final SortedSet<Comparable> values =
                header.getConstrainedColumns().get(i).values;
            StarColumnPredicate predicate;
            if (values == null) {
                predicate =
                    new LiteralStarPredicate(
                        constrainedColumn,
                        true);
            } else if (values.size() == 1) {
                predicate =
                    new ValueColumnPredicate(
                        constrainedColumn,
                        values.first());
            } else {
                final List<StarColumnPredicate> valuePredicateList =
                    new ArrayList<>();
                for (Object value : values) {
                    valuePredicateList.add(
                        new ValueColumnPredicate(
                            constrainedColumn,
                            value));
                }
                predicate =
                    new ListColumnPredicate(
                        constrainedColumn,
                        valuePredicateList);
            }
            predicateList.add(predicate);
        }

        return new Segment(
            star,
            constrainedColumnsBitKey,
            constrainedColumns,
            measure,
            predicateList.toArray(
                new StarColumnPredicate[predicateList.size()]),
            new ExcludedRegionList(header),
            compoundPredicates,
            header);
    }

    /**
     * Given a collection of segments, all of the same dimensionality, rolls up
     * to create a segment with reduced dimensionality.
     *
     * @param map Source segment headers and bodies
     * @param keepColumns A list of column names to keep as part of
     * the rolled up segment.
     * @param targetBitkey The column bit key to match with the
     * resulting segment.
     * @param rollupAggregator The aggregator to use to rollup.
     * @param datatype The data type to use.
     * @param sparseSegmentCountThreshold Sparse-decision cell count threshold
     * @param sparseSegmentDensityThreshold Sparse-decision density threshold
     * @return Segment header and body of requested dimensionality
     */
    public static Pair<SegmentHeader, SegmentBody> rollup(
        Map<SegmentHeader, SegmentBody> map,
        Set<String> keepColumns,
        BitKey targetBitkey,
        Aggregator rollupAggregator,
        Datatype datatype,
        int sparseSegmentCountThreshold,
        double sparseSegmentDensityThreshold)
    {
        long startTime = System.currentTimeMillis();
        class AxisInfo {
            SegmentColumn column;
            SortedSet<Comparable> requestedValues;
            SortedSet<Comparable> valueSet;
            Comparable[] values;
            boolean hasNull;
            int src;
            boolean lostPredicate;
        }

        assert allHeadersHaveSameDimensionality(map.keySet());

        // store the map values in a list to assure the first header
        // loaded here is consistent w/ the first segment processed below.
        List<Map.Entry<SegmentHeader, SegmentBody>>  segments =
            List.copyOf(map.entrySet());
        final SegmentHeader firstHeader = segments.getFirst().getKey();
        final List<AxisInfo> axes = new ArrayList<>(keepColumns.size());
        int z = 0, j = 0;
        List<SegmentColumn> firstHeaderConstrainedColumns =
            firstHeader.getConstrainedColumns();
        for (SegmentColumn column : firstHeaderConstrainedColumns) {
            if (keepColumns.contains(column.columnExpression)) {
                final AxisInfo axisInfo = new AxisInfo();
                axes.add(axisInfo);
                axisInfo.src = j;
                axisInfo.column = column;
                axisInfo.requestedValues = column.values;
            }
            j++;
        }

        // Compute the sets of values in each axis of the target segment. These
        // are the intersection of the input axes.
        for (Map.Entry<SegmentHeader, SegmentBody> entry : segments) {
            final SegmentHeader header = entry.getKey();
            for (AxisInfo axis : axes) {
                final SortedSet<Comparable> values =
                    entry.getValue().getAxisValueSets()[axis.src];
                final SegmentColumn headerColumn =
                    header.getConstrainedColumn(axis.column.columnExpression);
                final boolean hasNull =
                    entry.getValue().getNullAxisFlags()[axis.src];
                final SortedSet<Comparable> requestedValues =
                    headerColumn.getValues();
                if (axis.valueSet == null) {
                    axis.valueSet = new TreeSet<>(values);
                    axis.hasNull = hasNull;
                    axis.requestedValues = requestedValues;
                } else {
                    final SortedSet<Comparable> filteredValues;
                    final boolean filteredHasNull;
                    if (axis.requestedValues == null
                        && requestedValues == null)
                    {
                        // there are 2+ segments that are unconstrained for
                        // this axis.  While unconstrained, individually
                        // they may not have all values present.
                        // Make sure we don't lose any values.
                        filteredValues = axis.valueSet;
                        filteredValues.addAll(values);
                        filteredHasNull = hasNull || axis.hasNull;
                    } else if (axis.requestedValues == null) {
                        filteredValues = values;
                        filteredHasNull = hasNull;
                        axis.column = headerColumn;
                    } else if (requestedValues == null) {
                        // this axis is wildcarded
                        filteredValues = axis.requestedValues;
                        filteredHasNull = axis.hasNull;
                    } else {
                        filteredValues = Util.intersect(
                            requestedValues,
                            axis.requestedValues);

                        // SegmentColumn predicates cannot ask for the null
                        // value (at present).
                        filteredHasNull = false;
                    }
                    axis.valueSet = filteredValues;
                    axis.hasNull = axis.hasNull || filteredHasNull;
                    if (!Objects.equals(axis.requestedValues, requestedValues)) {
                        if (axis.requestedValues == null) {
                            // Downgrade from wildcard to a specific list.
                            axis.requestedValues = requestedValues;
                        } else if (requestedValues != null) {
                            // Segment requests have incompatible predicates.
                            // Best we can say is "we must have asked for the
                            // values that came back".
                            axis.lostPredicate = true;
                        }
                    }
                }
            }
        }

        for (AxisInfo axis : axes) {
            axis.values =
                axis.valueSet.toArray(Comparable[]::new);
        }

        // Populate cells.
        //
        // (This is a rough implementation, very inefficient. It makes all
        // segment types pretend to be sparse, for purposes of reading. It
        // maps all axis ordinals to a value, then back to an axis ordinal,
        // even if this translation were not necessary, say if the source and
        // target axes had the same set of values. And it always creates a
        // sparse segment.
        //
        // We should do really efficient rollup if the source is an array: we
        // should box values (e.g double to Double and back), and we should read
        // a stripe of values from the and add them up into a single cell.
        final Map<CellKey, List<Object>> cellValues =
            new HashMap<>();

        // De-duping across overlapping segments identifies a SOURCE cell by
        // its ordinal tuple over ALL axes: kept axes use the target
        // ordinals, projected-away axes a union value space built once —
        // O(1) per cell instead of a value-list tree.
        final boolean dedupe = map.size() > 1;
        final Comparable[][] unionValues =
            new Comparable[firstHeaderConstrainedColumns.size()][];
        final boolean[] unionHasNull =
            new boolean[firstHeaderConstrainedColumns.size()];
        if (dedupe) {
            for (int i = 0; i < firstHeaderConstrainedColumns.size(); i++) {
                if (keepColumns.contains(
                        firstHeaderConstrainedColumns.get(i).columnExpression)) {
                    continue;
                }
                final SortedSet<Comparable> union = new TreeSet<>();
                for (Map.Entry<SegmentHeader, SegmentBody> entry : segments) {
                    union.addAll(entry.getValue().getAxisValueSets()[i]);
                    unionHasNull[i] |= entry.getValue().getNullAxisFlags()[i];
                }
                unionValues[i] = union.toArray(Comparable[]::new);
            }
        }
        final Set<CellKey> seenSourceCells = dedupe ? new HashSet<>() : null;

        for (Map.Entry<SegmentHeader, SegmentBody> entry : segments) {
            final int[] pos = new int[axes.size()];
            final Comparable[][] valueArrays =
                new Comparable[firstHeaderConstrainedColumns.size()][];
            final SegmentBody body = entry.getValue();

            // Copy source value sets into arrays. For axes that are being
            // projected away, store null.
            z = 0;
            for (SortedSet<Comparable> set : body.getAxisValueSets()) {
                    valueArrays[z] = keepColumns.contains(
                        firstHeaderConstrainedColumns.get(z).columnExpression)
                        ? set.toArray(Comparable[]::new)
                        : null;
                ++z;
            }
            // The source-ordinal -> target-ordinal mapping is fixed per
            // (segment, axis): binary-search each source value ONCE instead
            // of per cell. The extra slot at the end resolves the source's
            // null cell (ordinal == valueArray.length); -1 drops the cell.
            final int[][] ordinalMaps = new int[valueArrays.length][];
            z = 0;
            for (int i = 0; i < valueArrays.length; i++) {
                final Comparable[] valueArray = valueArrays[i];
                if (valueArray == null) {
                    continue;
                }
                final AxisInfo axis = axes.get(z);
                final int nullOrdinal = axis.hasNull ? axis.valueSet.size() : -1;
                final int[] ordinalMap = new int[valueArray.length + 1];
                for (int s = 0; s < valueArray.length; s++) {
                    final Comparable value = valueArray[s];
                    ordinalMap[s] = value == null ? nullOrdinal
                            : Util.binarySearch(axis.values, 0, axis.values.length, value);
                }
                ordinalMap[valueArray.length] = nullOrdinal;
                ordinalMaps[i] = ordinalMap;
                z++;
            }
            // ordinal maps of the projected-away axes into their union
            // value spaces, for the source-cell identity when de-duping
            final int[][] dedupeMaps = dedupe
                ? new int[valueArrays.length][] : null;
            if (dedupe) {
                for (int i = 0; i < valueArrays.length; i++) {
                    if (valueArrays[i] != null) {
                        continue;
                    }
                    final Comparable[] union = unionValues[i];
                    final Comparable[] source =
                        body.getAxisValueSets()[i].toArray(Comparable[]::new);
                    final int nullOrdinal =
                        unionHasNull[i] ? union.length : -1;
                    final int[] dedupeMap = new int[source.length + 1];
                    for (int s = 0; s < source.length; s++) {
                        dedupeMap[s] = source[s] == null ? nullOrdinal
                            : Util.binarySearch(union, 0, union.length, source[s]);
                    }
                    dedupeMap[source.length] = nullOrdinal;
                    dedupeMaps[i] = dedupeMap;
                }
            }

            Map<CellKey, Object> v = body.getValueMap();
            entryLoop:
            for (Map.Entry<CellKey, Object> vEntry : v.entrySet()) {
                z = 0;
                for (int i = 0; i < vEntry.getKey().size(); i++) {
                    final int[] ordinalMap = ordinalMaps[i];
                    if (ordinalMap == null) {
                        continue;
                    }
                    final int targetOrdinal = ordinalMap[vEntry.getKey().getAxis(i)];
                    if (targetOrdinal >= 0) {
                        pos[z++] = targetOrdinal;
                    } else {
                        // a rollup candidate that does not contain the
                        // requested cell
                        continue entryLoop;
                    }
                }
                final CellKey ck = CellKey.Generator.newCellKey(pos);
                final List<Object> cellList =
                    cellValues.computeIfAbsent(ck, k -> new ArrayList<>());
                if (!dedupe) {
                    // no de-duping needed when rolling up only 1 segment
                    cellList.add(rollupValue(vEntry.getValue()));
                } else {
                    // the same origin cell may live in several overlapping
                    // segments and must be summed once: identify it by its
                    // full ordinal tuple (kept axes in target space,
                    // projected-away axes in the union space)
                    final int[] origin = new int[vEntry.getKey().size()];
                    for (int i = 0; i < origin.length; i++) {
                        final int sourceOrdinal = vEntry.getKey().getAxis(i);
                        origin[i] = ordinalMaps[i] != null
                            ? ordinalMaps[i][sourceOrdinal]
                            : dedupeMaps[i][sourceOrdinal];
                    }
                    if (seenSourceCells.add(
                            CellKey.Generator.newCellKey(origin))) {
                        cellList.add(rollupValue(vEntry.getValue()));
                    }
                }
            }
        }

        // Build the axis list.
        final List<Pair<SortedSet<Comparable>, Boolean>> axisList =
            new ArrayList<>();
        BigInteger bigValueCount = BigInteger.ONE;
        for (AxisInfo axis : axes) {
            // the body ships the compact sorted-array view, not a TreeSet
            axisList.add(Pair.of(
                (SortedSet<Comparable>) new ArraySortedSet(axis.values),
                axis.hasNull));
            int size = axis.values.length;
            bigValueCount = bigValueCount.multiply(
                BigInteger.valueOf(axis.hasNull ? size + 1 : size));
        }

        // The logic used here for the sparse check follows
        // SegmentLoader.setAxisDataAndDecideSparseUse.
        // The two methods use different data structures (AxisInfo/SegmentAxis)
        // so combining logic is probably more trouble than it's worth.
        final boolean sparse =
            bigValueCount.compareTo
                (BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                || SegmentLoader.useSparse(
                    bigValueCount.doubleValue(),
                    cellValues.size(),
                    sparseSegmentCountThreshold,
                    sparseSegmentDensityThreshold);
        final int[] axisMultipliers =
            computeAxisMultipliers(axisList);
        final DataTypeJdbc jdbcType =
            EnumConvertor.toDataTypeJdbc(datatype);

        // grand-total rollup (no axes kept): every source cell collapses
        // into the single zero-arity key, and the dense path below stores
        // exactly one value (bigValueCount == 1, offset 0)
        assert !axisList.isEmpty() || cellValues.size() <= 1
            : "grand-total rollup must collapse to a single cell, got " + cellValues.size();

        final SegmentBody body;
        // Peak at the values and determine the best way to store them
        // (whether to use a dense native dataset or a sparse one.
        if (cellValues.isEmpty()) {
            // Just store the data into an empty dense object dataset.
            body =
                new DenseObjectSegmentBody(
                    new Object[0],
                    axisList);
        } else if (sparse) {
            // The rule says we must use a sparse dataset.
            // First, aggregate the values of each key.
            final Map<CellKey, Object> data =
                new HashMap<>();
            for (Entry<CellKey, List<Object>> entry
                : cellValues.entrySet())
            {
                // keys come out of newCellKey and are never mutated — share
                data.put(
                    entry.getKey(),
                    rollupAggregator.aggregate(
                        entry.getValue(),
                        jdbcType));
            }
            body =
                new SparseSegmentBody(
                    data,
                    axisList);
        } else {
            final BitSet nullValues;
            final int valueCount = bigValueCount.intValue();
            switch (datatype) {
            case INTEGER:
                final int[] ints = new int[valueCount];
                nullValues = Util.bitSetBetween(0, valueCount);
                for (Entry<CellKey, List<Object>> entry
                    : cellValues.entrySet())
                {
                    final int offset =
                        entry.getKey().getOffset(axisMultipliers);
                    final Object value =
                        rollupAggregator.aggregate(
                            entry.getValue(),
                            jdbcType);
                    if (value != null) {
                        ints[offset] = (Integer) value;
                        nullValues.clear(offset);
                    }
                }
                body =
                    new DenseIntSegmentBody(
                        nullValues,
                        ints,
                        axisList);
                  break;
            case NUMERIC:
                final double[] doubles = new double[valueCount];
                nullValues = Util.bitSetBetween(0, valueCount);
                for (Entry<CellKey, List<Object>> entry
                    : cellValues.entrySet())
                {
                    final int offset =
                        entry.getKey().getOffset(axisMultipliers);
                    final Object value =
                        rollupAggregator.aggregate(
                            entry.getValue(),
                            jdbcType);
                    if (value != null) {
                        doubles[offset] = (Double) value;
                        nullValues.clear(offset);
                    }
                }
                body =
                    new DenseDoubleSegmentBody(
                        nullValues,
                        doubles,
                        axisList);
                break;
            default:
                final Object[] objects = new Object[valueCount];
                for (Entry<CellKey, List<Object>> entry
                    : cellValues.entrySet())
                {
                    final int offset =
                        entry.getKey().getOffset(axisMultipliers);
                    objects[offset] =
                        rollupAggregator.aggregate(
                            entry.getValue(),
                            jdbcType);
                }
                body =
                    new DenseObjectSegmentBody(
                        objects,
                        axisList);
            }
        }

        // Create header.
        final List<SegmentColumn> constrainedColumns =
            new ArrayList<>();
        for (int i = 0; i < axes.size(); i++) {
            AxisInfo axisInfo = axes.get(i);

            constrainedColumns.add(
                new SegmentColumn(
                    axisInfo.column.getColumnExpression(),
                    axisInfo.column.getValueCount(),
                    axisInfo.lostPredicate
                        ? axisList.get(i).left
                        : axisInfo.column.values));
        }
        // Excluded regions survive the rollup, widened to the kept columns:
        // the flushed cells stay physically in the source bodies and are
        // summed into exactly the target cells the widened box marks, so
        // the target header keeps refusing them. A region that loses every
        // column spans the whole target and becomes a wildcard-column box.
        final Set<SegmentRegion> excludedRegions = new LinkedHashSet<>();
        for (Map.Entry<SegmentHeader, SegmentBody> sourceEntry : segments) {
            final SegmentHeader sourceHeader = sourceEntry.getKey();
            for (SegmentRegion region : sourceHeader.getExcludedRegions()) {
                final List<SegmentColumn> keptBox = new ArrayList<>();
                for (SegmentColumn column : region.columns()) {
                    if (keepColumns.contains(column.columnExpression)) {
                        keptBox.add(column);
                    }
                }
                if (keptBox.isEmpty() && !constrainedColumns.isEmpty()) {
                    keptBox.add(new SegmentColumn(
                        constrainedColumns.get(0).columnExpression,
                        constrainedColumns.get(0).valueCount,
                        null));
                }
                if (!keptBox.isEmpty()) {
                    excludedRegions.add(new SegmentRegion(keptBox));
                } else {
                    // grand-total rollup of a constrained source: the region
                    // is inexpressible on a zero-column target and the
                    // flushed cells were still summed in. Reaching this
                    // would publish resurrected data - today it is
                    // unreachable ONLY because excludedRegionHitsRequest
                    // (SegmentCacheIndexImpl) rejects constrained headers
                    // as grand-total rollup candidates. A data-corruption
                    // guard must hold in production too (-da), so this
                    // throws instead of asserting: aborting the rollup is
                    // strictly better than publishing resurrected data.
                    throw new IllegalStateException(
                        "rollup dropped an inexpressible excluded region");
                }
            }
        }
        final SegmentHeader header =
            new SegmentHeader(
                firstHeader.schemaName,
                firstHeader.schemaChecksum,
                firstHeader.cubeName,
                firstHeader.measureName,
                constrainedColumns,
                firstHeader.compoundPredicates,
                firstHeader.rolapStarFactTableName,
                targetBitkey,
                List.copyOf(excludedRegions));
        if (LOGGER.isDebugEnabled()) {
            StringBuilder builder = new StringBuilder();
            builder.append("SegmentBuilder.rollup: done rolling up segments with parameters: \n");
            builder.append("keepColumns=").append(keepColumns).append("\n");
            builder.append("aggregator=").append(rollupAggregator).append("\n");
            builder.append("datatype=").append(datatype).append("\n");
            for (Map.Entry<SegmentHeader, SegmentBody > segment : segments) {
                builder.append(segment.getKey()).append("\n");
            }
            if (LOGGER.isTraceEnabled()) {
              builder.append("AxisInfos constructed:");
              for (AxisInfo axis : axes) {
                  SortedSet<Comparable> colVals = axis.column.getValues();
                  builder.append(
                      LOG_FORMAT_STRING.formatted(
                          axis.column.columnExpression,
                          axis.column.getValueCount(),
                          Arrays.toString(
                              colVals == null ? null
                              : colVals.toArray()),
                          axis.requestedValues,
                          axis.valueSet,
                          Arrays.asList(axis.values),
                          axis.hasNull,
                          axis.src,
                          axis.lostPredicate));
              }
            }
            builder.append("Resulted in Segment:  \n");
            builder.append(header);
            if (LOGGER.isTraceEnabled()) {
              builder.append(body.toString());
            }
            builder.append(", ").append(System.currentTimeMillis() - startTime).append(" ms \n");
            LOGGER.debug(builder.toString());
        }
        return Pair.of(header, body);
    }

    /**
     * Value of a source cell as the rollup aggregators take it: a NULL stored
     * as the {@link NullValue#INSTANCE} placeholder (object datasets) is Java
     * {@code null}, which the aggregators skip.
     */
    private static Object rollupValue(Object value) {
        return value == NullValue.INSTANCE ? null : value;
    }

    private static boolean allHeadersHaveSameDimensionality(
        Set<SegmentHeader> headers)
    {
        final Iterator<SegmentHeader> headerIter = headers.iterator();
        final SegmentHeader firstHeader = headerIter.next();
        BitKey bitKey = firstHeader.getConstrainedColumnsBitKey();
        while (headerIter.hasNext()) {
            final SegmentHeader nextHeader = headerIter.next();
            if (!bitKey.equals(nextHeader.getConstrainedColumnsBitKey())) {
                return false;
            }
        }
        return true;
    }

    private static int[] computeAxisMultipliers(
        List<Pair<SortedSet<Comparable>, Boolean>> axes)
    {
        final int[] axisMultipliers = new int[axes.size()];
        int multiplier = 1;
        for (int i = axes.size() - 1; i >= 0; --i) {
            axisMultipliers[i] = multiplier;
            // if the nullAxisFlag is set we need to offset by 1.
            int nullAxisAdjustment = axes.get(i).right ? 1 : 0;
            multiplier *= (axes.get(i).left.size() + nullAxisAdjustment);
        }
        return axisMultipliers;
    }

    private static class ExcludedRegionList
        extends AbstractList<Segment.ExcludedRegion>
        implements Segment.ExcludedRegion
    {
        private final int cellCount;
        private final SegmentHeader header;
        public ExcludedRegionList(SegmentHeader header) {
            this.header = header;
            int cellCount = 0;
            for (SegmentRegion region : header.getExcludedRegions()) {
                // TODO find a way to approximate the cardinality
                // of wildcard columns.
                int regionCells = 1;
                for (SegmentColumn cc : region.columns()) {
                    if (cc.values != null) {
                        regionCells *= cc.values.size();
                    }
                }
                cellCount += regionCells;
            }
            // 0 when the header carries no excluded regions: getCellCount
            // consumers subtract this from the live cell count
            this.cellCount = cellCount;
        }

        @Override
		public void describe(StringBuilder buf) {
            for (SegmentRegion region : header.getExcludedRegions()) {
                buf.append('(');
                for (SegmentColumn cc : region.columns()) {
                    buf.append(cc.columnExpression).append('=')
                            .append(cc.values == null ? "*" : cc.values).append(';');
                }
                buf.append(')');
            }
        }

        @Override
		public int getArity() {
            return header.getConstrainedColumns().size();
        }

        @Override
		public int getCellCount() {
            return cellCount;
        }

        @Override
		public boolean wouldContain(Object[] keys) {
            assert keys.length == header.getConstrainedColumns().size();
            // a cell is excluded only when it lies inside one region box:
            // every column of the box must match
            for (SegmentRegion region : header.getExcludedRegions()) {
                boolean inside = true;
                for (SegmentColumn excl : region.columns()) {
                    int i = indexOfColumn(excl.columnExpression);
                    if (i < 0 || (excl.values != null && !excl.values.contains(keys[i]))) {
                        inside = false;
                        break;
                    }
                }
                if (inside) {
                    return true;
                }
            }
            return false;
        }

        private int indexOfColumn(String columnExpression) {
            final List<SegmentColumn> columns = header.getConstrainedColumns();
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).columnExpression.equals(columnExpression)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
		public ExcludedRegion get(int index) {
            return this;
        }

        @Override
		public int size() {
            // no excluded regions: an empty list, so nothing is subtracted
            // from the cell count and describe prints no excluded block
            return header.getExcludedRegions().isEmpty() ? 0 : 1;
        }
    }


    public static List<SegmentColumn> toConstrainedColumns(
        StarColumnPredicate[] predicates)
    {
        return toConstrainedColumns(
            Arrays.asList(predicates));
    }

    public static List<SegmentColumn> toConstrainedColumns(
        Collection<StarColumnPredicate> predicates)
    {
        List<SegmentColumn> ccs =
            new ArrayList<>(predicates.size());
        for (StarColumnPredicate predicate : predicates) {
            if (predicate instanceof LiteralStarPredicate) {
                if (((LiteralStarPredicate) predicate).getValue()) {
                    // no constraint for this column
                    ccs.add(segmentColumn(predicate, null));
                    continue;
                }
            }
            List<Comparable> values = new ArrayList<>();
            predicate.values(Util.cast(values));
            Comparable[] valuesArray =
                values.toArray(Comparable[]::new);
            Arrays.sort(valuesArray, RolapUtil.SqlNullSafeComparator.instance);
            ccs.add(
                segmentColumn(predicate, new ArraySortedSet(valuesArray)));
        }
        return ccs;
    }

    private static SegmentColumn segmentColumn(
        StarColumnPredicate predicate, SortedSet<Comparable> set)
    {
        return new SegmentColumn(
            predicate.getConstrainedColumn().genericSql(),
            predicate.getConstrainedColumn().getCardinality(),
            set);
    }

    /**
     * Creates a SegmentHeader object describing the supplied
     * Segment object.
     *
     * @param segment A segment object for which we want to generate
     * a SegmentHeader.
     * @return A SegmentHeader describing the supplied Segment object.
     */
    public static SegmentHeader toHeader(Segment segment) {
        final List<SegmentColumn> cc =
            SegmentBuilder.toConstrainedColumns(segment.predicates);
        final List<SegmentPredicate> cp =
            SegmentPredicates.toWire(segment.compoundPredicateList);
        final RolapCatalog schema = segment.star.getCatalog();
        return new SegmentHeader(
            schema.getName(),
            schema.getChecksum(),
            segment.measure.getCubeName(),
            segment.measure.getName(),
            cc,
            cp,
            segment.star.getFactTable().getAlias(),
            segment.constrainedColumnsBitKey,
            Collections.<SegmentRegion>emptyList());
    }

    private static RolapStar.Column[] getConstrainedColumns(
        RolapStar star,
        BitKey bitKey)
    {
        final List<RolapStar.Column> list =
            new ArrayList<>();
        for (int bit = bitKey.nextSetBit(0); bit >= 0;
                bit = bitKey.nextSetBit(bit + 1)) {
            if (bit >= star.getColumnCount()) {
                // a wire-decoded header whose key carries bits this star
                // does not know cannot be converted - name the mismatch
                // instead of dying in ArrayList.get
                throw new IllegalArgumentException(
                    "segment key bit " + bit + " beyond star '"
                    + star.getFactTable().getAlias() + "' with "
                    + star.getColumnCount() + " columns");
            }
            list.add(star.getColumn(bit));
        }
        return list.toArray(new RolapStar.Column[list.size()]);
    }

    /**
     * Functor to convert a segment header and body into a
     * {@link SegmentWithData}.
     */
    public static interface SegmentConverter {
        SegmentWithData convert(
            SegmentHeader header,
            SegmentBody body);
    }

    /**
     * Implementation of {@link SegmentConverter} that uses a star measure
     * and a list of {@link StarPredicate}.
     */
    public static class StarSegmentConverter implements SegmentConverter {
        private final RolapStar.Measure measure;
        private final List<StarPredicate> compoundPredicateList;

        public StarSegmentConverter(
            RolapStar.Measure measure,
            List<StarPredicate> compoundPredicateList)
        {
            // a plain hard reference: converters live in the per-catalog
            // index, which dies with its catalog - the measure never
            // outlives the index that references it
            this.measure = measure;
            this.compoundPredicateList = compoundPredicateList;
        }

        @Override
		public SegmentWithData convert(
            SegmentHeader header,
            SegmentBody body)
        {
            final Segment segment =
                toSegment(
                    header,
                    measure.getStar(),
                    header.getConstrainedColumnsBitKey(),
                    getConstrainedColumns(
                        measure.getStar(),
                        header.getConstrainedColumnsBitKey()),
                    measure,
                    compoundPredicateList);
            return addData(segment, body);
        }
    }



}
