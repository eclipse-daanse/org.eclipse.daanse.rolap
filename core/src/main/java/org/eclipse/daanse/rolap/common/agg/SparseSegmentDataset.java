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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.olap.key.CellKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import  org.eclipse.daanse.olap.util.Pair;

/**
 * A SparseSegmentDataset is a means of storing segment values
 * which is suitable when few of the combinations of keys have a value present.
 *
 * The storage requirements are as follows. Key is 1 word for each
 * dimension. Hashtable entry is 3 words. Value is 1 word. Total space is (4 +
 * d) * v. (May also need hash table to ensure that values are only stored
 * once.)
 *
 * NOTE: This class is not synchronized.
 *
 * @author jhyde
 * @since 21 March, 2002
 */
class SparseSegmentDataset implements SegmentDataset {
    private final Map<CellKey, Object> values;

    /**
     * Creates an empty SparseSegmentDataset.
     */
    SparseSegmentDataset() {
        this(new HashMap<>());
    }

    /**
     * Creates a SparseSegmentDataset with a given value map. The map is not
     * copied; a reference to the map is retained inside the dataset, and
     * therefore the contents of the dataset will change if the map is modified.
     *
     * @param values Value map
     */
    SparseSegmentDataset(Map<CellKey, Object> values) {
        this.values = values;
    }

    @Override
	public Object getObject(CellKey pos) {
        return values.get(pos);
    }

    @Override
	public boolean isNull(CellKey pos) {
        // cf exists -- calls values.containsKey
        return values.get(pos) == null;
    }

    @Override
	public int getInt(CellKey pos) {
        throw new UnsupportedOperationException();
    }

    @Override
	public double getDouble(CellKey pos) {
        throw new UnsupportedOperationException();
    }

    @Override
	public boolean exists(CellKey pos) {
        return values.containsKey(pos);
    }

    public void put(CellKey key, Object value) {
        values.put(key, value);
    }

    @Override
	public Iterator<Map.Entry<CellKey, Object>> iterator() {
        return values.entrySet().iterator();
    }

    @Override
	public double getBytes() {
        // assume a slot, key, and value are each 4 bytes
        return values.size() * 12d;
    }

    @Override
	public void populateFrom(int[] pos, SegmentDataset data, CellKey key) {
        values.put(CellKey.Generator.newCellKey(pos), data.getObject(key));
    }

    @Override
	public void populateFrom(
        int[] pos, SegmentLoader.RowList rowList, int column)
    {
        final Object o = rowList.getObject(column);
        put(CellKey.Generator.newCellKey(pos), o);
    }

    @Override
	public BestFitColumnType getType() {
        return BestFitColumnType.OBJECT;
    }

    @Override
	public SegmentBody createSegmentBody(
        List<Pair<SortedSet<Comparable>, Boolean>> axes)
    {
        return new SparseSegmentBody(
            values,
            axes);
    }
}
