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

import java.util.List;
import java.util.SortedSet;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.olap.key.CellKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import  org.eclipse.daanse.olap.util.Pair;

/**
 * Implementation of {@link org.eclipse.daanse.rolap.common.agg.DenseSegmentDataset} that stores
 * values of type {@link Object}.
 *
 * The storage requirements are as follows. Table requires 1 word per
 * cell.
 *
 * @author jhyde
 * @since 21 March, 2002
 */
class DenseObjectSegmentDataset extends DenseSegmentDataset {
    final Object[] values; // length == m[0] * ... * m[axes.length-1]

    /**
     * Creates a DenseSegmentDataset.
     *
     * @param axes Segment axes, containing actual column values
     * @param size Number of coordinates
     */
    DenseObjectSegmentDataset(SegmentAxis[] axes, int size) {
        this(axes, new Object[size]);
    }

    /**
     * Creates and populates a DenseSegmentDataset. The data set is not copied.
     *
     * @param axes Axes
     * @param values Data set
     */
    DenseObjectSegmentDataset(SegmentAxis[] axes, Object[] values) {
        super(axes);
        this.values = values;
    }

    @Override
	public Object getObject(CellKey key) {
        if (values.length == 0) {
            // No values means they are all null.
            // We can't call isNull because we risk going into a SOE. Besides,
            // this is a tight loop and we can skip over one VFC.
            return null;
        }
        int offset = key.getOffset(axisMultipliers);
        return values[offset];
    }

    @Override
	public boolean isNull(CellKey pos) {
        if (values.length == 0) {
            // No values means they are all null.
            return true;
        }
        return getObject(pos) != null;
    }

    @Override
	public boolean exists(CellKey pos) {
        return getObject(pos) != null;
    }

    @Override
	public void populateFrom(int[] pos, SegmentDataset data, CellKey key) {
        values[getOffset(pos)] = data.getObject(key);
    }

    @Override
	public void populateFrom(
        int[] pos, SegmentLoader.RowList rowList, int column)
    {
        int offset = getOffset(pos);
        values[offset] = rowList.getObject(column);
    }

    @Override
	public BestFitColumnType getType() {
        return BestFitColumnType.OBJECT;
    }

    public void put(CellKey key, Object value) {
        int offset = key.getOffset(axisMultipliers);
        values[offset] = value;
    }

    @Override
	protected Object getObject(int i) {
        return values[i];
    }

    @Override
	protected int getSize() {
        return values.length;
    }

    @Override
	public SegmentBody createSegmentBody(
        List<Pair<SortedSet<Comparable>, Boolean>> axes)
    {
        return new DenseObjectSegmentBody(
            values,
            axes);
    }
}
