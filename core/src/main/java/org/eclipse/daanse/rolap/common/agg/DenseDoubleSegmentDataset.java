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


import java.util.BitSet;
import java.util.List;
import java.util.SortedSet;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.olap.common.Util;

import org.eclipse.daanse.olap.spi.SegmentBody;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.olap.key.CellKey;

/**
 * Implementation of {@link org.eclipse.daanse.rolap.common.agg.DenseSegmentDataset} that stores
 * values of type {@code double}.
 *
 * @author jhyde
 */
class DenseDoubleSegmentDataset extends DenseNativeSegmentDataset {
    final double[] values; // length == m[0] * ... * m[axes.length-1]

    /**
     * Creates a DenseDoubleSegmentDataset.
     *
     * @param axes Segment axes, containing actual column values
     * @param size Number of coordinates
     */
    DenseDoubleSegmentDataset(SegmentAxis[] axes, int size) {
        this(axes, new double[size], Util.bitSetBetween(0, size));
    }

    /**
     * Creates a populated DenseDoubleSegmentDataset.
     *
     * @param axes Segment axes, containing actual column values
     * @param values Cell values; not copied
     * @param nullIndicators Null indicators
     */
    DenseDoubleSegmentDataset(
        SegmentAxis[] axes, double[] values, BitSet nullIndicators)
    {
        super(axes, nullIndicators);
        this.values = values;
    }

    @Override
	public double getDouble(CellKey key) {
        int offset = key.getOffset(axisMultipliers);
        return values[offset];
    }

    @Override
	public Object getObject(CellKey pos) {
        if (values.length == 0) {
            // No values means they are all null.
            // We can't call isNull because we risk going into a SOE. Besides,
            // this is a tight loop and we can skip over one VFC.
            return null;
        }
        int offset = pos.getOffset(axisMultipliers);
        return getObject(offset);
    }

    @Override
	public Double getObject(int offset) {
        final double value = values[offset];
        if (value == 0 && isNull(offset)) {
            return null;
        }
        return value;
    }

    @Override
	public boolean exists(CellKey pos) {
        return true;
    }

    @Override
	public void populateFrom(int[] pos, SegmentDataset data, CellKey key) {
        final int offset = getOffset(pos);
        final double value = values[offset] = data.getDouble(key);
        if (value != 0d || !data.isNull(key)) {
            nullValues.clear(offset);
        }
    }

    @Override
	public void populateFrom(
        int[] pos, SegmentLoader.RowList rowList, int column)
    {
        final int offset = getOffset(pos);
        final double value = values[offset] = rowList.getDouble(column);
        if (value != 0d || !rowList.isNull(column)) {
            nullValues.clear(offset);
        }
    }

    @Override
	public BestFitColumnType getType() {
        return BestFitColumnType.DOUBLE;
    }

    void set(int k, double d) {
        values[k] = d;
    }

    @Override
	protected int getSize() {
        return values.length;
    }

    @Override
	public SegmentBody createSegmentBody(
        List<Pair<SortedSet<Comparable>, Boolean>> axes)
    {
        return new DenseDoubleSegmentBody(
            nullValues,
            values,
            axes);
    }
}
