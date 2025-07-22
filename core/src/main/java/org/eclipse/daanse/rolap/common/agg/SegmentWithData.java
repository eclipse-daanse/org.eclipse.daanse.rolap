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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.key.CellKey;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.StarPredicate;

/**
 * Extension to {@link Segment} with a data set.
 *
 * @author jhyde
 */
public class SegmentWithData extends Segment {
    /**
     * An array of axes, one for each constraining column, containing the values
     * returned for that constraining column.
     */
    final SegmentAxis[] axes;

    /**
     * data holds a reference to the SegmentDataset
     * that contains the underlying cell values.
     *
     * Since the SegmentDataset is loaded and assigned after
     * Segment is constructed, threadsafe access to it is only
     * guaranteed if the access is guarded.
     *
     * Access which does not depend on data already having been
     * loaded should be guarded by obtaining either a read or write lock on
     * stateLock, as appropriate.
     *
     * Access that should not proceed until the data reference
     * has been loaded should be guarded using the dataGate latch.
     * This is typically accomplished by calling waitUntilLoaded(),
     * which will block until the latch is released and throw an error if
     * data failed to load.
     *
     * Once set, the value of data is presumed to be invariant
     * and should never be reset, nor should the contents be modified.  Thus,
     * for a given thread, any read access to data which comes after
     * dataGate.await() (or, by extension,
     * waitUntilLoaded will be threadsafe.
     */
    private final SegmentDataset data;

    /**
     * Creates a SegmentWithData from an existing Segment.
     *
     * @param segment Segment (without data)
     * @param data Data set
     */
    public SegmentWithData(
        Segment segment,
        SegmentDataset data,
        SegmentAxis[] axes)
    {
        this(
            segment.getStar(),
            segment.getConstrainedColumnsBitKey(),
            segment.getColumns(),
            segment.measure,
            segment.predicates,
            segment.getExcludedRegions(),
            segment.compoundPredicateList,
            data,
            axes);
        if (segment instanceof SegmentWithData) {
            throw new AssertionError();
        }
    }

    /**
     * Creates a SegmentWithData.
     *
     * @param star Star that this Segment belongs to
     * @param measure Measure whose values this Segment contains
     * @param predicates List of axes; each is a constraint plus a list of
     *     values.
     * @param excludedRegions List of regions which are not in this segment.
     */
    private SegmentWithData(
        RolapStar star,
        BitKey constrainedColumnsBitKey,
        RolapStar.Column[] columns,
        RolapStar.Measure measure,
        StarColumnPredicate[] predicates,
        List<ExcludedRegion> excludedRegions,
        final List<StarPredicate> compoundPredicateList,
        SegmentDataset data,
        SegmentAxis[] axes)
    {
        super(
            star,
            constrainedColumnsBitKey,
            columns,
            measure,
            predicates,
            excludedRegions,
            compoundPredicateList);
        this.axes = axes;
        this.data = data;
    }

    @Override
    protected void describeAxes(StringBuilder buf, int i, boolean values) {
        super.describeAxes(buf, i, values);
        if (!values) {
            return;
        }
        Object[] keys = axes[i].getKeys();
        buf.append(", values={");
        for (int j = 0; j < keys.length; j++) {
            if (j > 0) {
                buf.append(", ");
            }
            Object key = keys[j];
            buf.append(key);
        }
        buf.append("}");
    }

    /**
     * Retrieves the value at the location identified by
     * keys.
     *
     * Returns
     *
     * {@link org.eclipse.daanse.olap.common.Util#nullValue} if the cell value
     * is null (because no fact table rows met those criteria);
     *
     * null if the value is not supposed to be in this segment
     * (because one or more of the keys do not pass the axis criteria);
     *
     * the data value otherwise
     *
     *
     *
     */
    public Object getCellValue(Object[] keys) {
        assert keys.length == axes.length;
        int missed = 0;
        CellKey cellKey = CellKey.Generator.newCellKey(axes.length);
        for (int i = 0; i < keys.length; i++) {
            Comparable key = (Comparable) keys[i];
            int offset = axes[i].getOffset(key);
            if (offset < 0) {
                if (axes[i].wouldContain(key)) {
                    // see whether this segment should contain this value
                    missed++;
                    continue;
                } else {
                    // this value should not appear in this segment; we
                    // should be looking in a different segment
                    return null;
                }
            }
            cellKey.setAxis(i, offset);
        }
        if (isExcluded(keys)) {
            // this value should not appear in this segment; we
            // should be looking in a different segment
            return null;
        }
        if (missed > 0) {
            // the value should be in this segment, but isn't, because one
            // or more of its keys does have any values
            return Util.nullValue;
        } else {
            Object o = data.getObject(cellKey);
            if (o == null) {
                o = Util.nullValue;
            }
            return o;
        }
    }

    /**
     * Returns whether the given set of key values will be in this segment
     * when it finishes loading.
     */
    boolean wouldContain(Object[] keys) {
        Util.assertTrue(keys.length == axes.length);
        for (int i = 0; i < keys.length; i++) {
            Object key = keys[i];
            if (!axes[i].wouldContain(key)) {
                return false;
            }
        }
        return !isExcluded(keys);
    }

    /**
     * Returns the number of cells in this Segment, deducting cells in
     * excluded regions.
     *
     * This method may return a value which is slightly too low, or
     * occasionally even negative. This occurs when a Segment has more than one
     * excluded region, and those regions overlap. Cells which are in both
     * regions will be counted twice.
     *
     * @return Number of cells in this Segment
     */
    public int getCellCount() {
        int cellCount = 1;
        for (SegmentAxis axis : axes) {
            cellCount *= axis.getKeys().length;
        }
        for (ExcludedRegion excludedRegion : excludedRegions) {
            cellCount -= excludedRegion.getCellCount();
        }
        return cellCount;
    }

    /**
     * Creates a Segment which has the same dimensionality as this Segment and a
     * subset of the values.
     *
     * If bestColumn is not -1, the bestColumnth
     * column's predicate should be replaced by bestPredicate.
     *
     * @param axisKeepBitSets For each axis, a bitmap of the axis values to
     *   keep; each axis must have at least one bit set
     * @param bestColumn The column that retains most of its values
     * @param bestPredicate
     * @param excludedRegions List of regions to exclude from segment
     * @return Segment containing a subset of the values
     */
    SegmentWithData createSubSegment(
        BitSet[] axisKeepBitSets,
        int bestColumn,
        StarColumnPredicate bestPredicate,
        List<ExcludedRegion> excludedRegions)
    {
        assert axisKeepBitSets.length == axes.length;

        // Create a new segment with a subset of the values. If only one
        // of the axes is restricted, restrict just that axis. If more than
        // one of the axis is restricted, add a negation to the segment.
        final SegmentAxis[] newAxes = axes.clone();
        final StarColumnPredicate[] newPredicates = predicates.clone();

        // For each axis, map from old position to new position.
        final Map<Integer, Integer>[] axisPosMaps = new Map[axes.length];

        int valueCount = 1;
        for (int j = 0; j < axes.length; j++) {
            SegmentAxis axis = axes[j];
            StarColumnPredicate newPredicate = axis.getPredicate();
            if (j == bestColumn) {
                newPredicate = bestPredicate;
            }
            final Comparable[] axisKeys = axis.getKeys();
            BitSet keepBitSet = axisKeepBitSets[j];
            int firstClearBit = keepBitSet.nextClearBit(0);
            Comparable[] newAxisKeys;
            if (firstClearBit >= axisKeys.length) {
                // Keep everything
                newAxisKeys = axisKeys;
                axisPosMaps[j] = null; // identity map
            } else {
                List<Object> newAxisKeyList = new ArrayList<>();
                Map<Integer, Integer> map =
                    axisPosMaps[j] =
                    new HashMap<>();
                for (int bit = keepBitSet.nextSetBit(0);
                    bit >= 0;
                    bit = keepBitSet.nextSetBit(bit + 1))
                {
                    map.put(bit, newAxisKeyList.size());
                    newAxisKeyList.add(axisKeys[bit]);
                }
                newAxisKeys =
                    newAxisKeyList.toArray(
                        new Comparable[newAxisKeyList.size()]);
                assert newAxisKeys.length > 0;
            }
            final SegmentAxis newAxis =
                new SegmentAxis(newPredicate, newAxisKeys);
            newAxes[j] = newAxis;
            newPredicates[j] = newPredicate;
            valueCount *= newAxisKeys.length;
        }

        // Create a dataset containing a subset of the current dataset.
        // Keep the same representation as the current dataset.
        // (We could be smarter - sometimes a subset of a sparse dataset will
        // be dense and VERY occasionally a subset of a relatively dense dataset
        // will be sparse.)
        SegmentDataset newData =
            createDataset(
                axes,
                data instanceof SparseSegmentDataset,
                data.getType(),
                valueCount);

        // If the source is sparse, it is more efficient to iterate over the
        // values we need. If it's dense, it doesn't matter too much.
        int[] pos = new int[axes.length];
        data:
        for (Map.Entry<CellKey, Object> entry : data) {
            CellKey key = entry.getKey();

            // Map each of the source coordinates to the target coordinate.
            // If any of the coordinates maps to null, it means that the
            // cell falls outside the subset.
            for (int i = 0; i < pos.length; i++) {
                int ordinal = key.getAxis(i);

                Map<Integer, Integer> axisPosMap = axisPosMaps[i];
                if (axisPosMap == null) {
                    pos[i] = ordinal;
                } else {
                    Integer integer = axisPosMap.get(ordinal);
                    if (integer == null) {
                        continue data;
                    }
                    pos[i] = integer;
                }
            }
            newData.populateFrom(pos, data, key);
        }

        // Create a segment with the new data set.
        return new SegmentWithData(
            star, constrainedColumnsBitKey, columns, measure,
            newPredicates, excludedRegions, compoundPredicateList,
            newData, newAxes);
    }

    /**
     * Returns the data set.
     *
     * WARNING: the returned SegmentDataset reference should not be modified;
     * it is assumed to be invariant.
     *
     * @return The data reference
     */
    public final SegmentDataset getData() {
        return data;
    }
}
