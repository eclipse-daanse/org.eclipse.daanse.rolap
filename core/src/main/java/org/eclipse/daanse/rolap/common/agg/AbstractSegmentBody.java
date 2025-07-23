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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;

import org.eclipse.daanse.olap.key.CellKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import  org.eclipse.daanse.olap.util.Pair;

/**
 * Abstract implementation of a SegmentBody.
 *
 * @author LBoudreau
 */
public abstract class AbstractSegmentBody implements SegmentBody {
    private static final long serialVersionUID = -7094121704771005640L;

    protected final SortedSet<Comparable>[] axisValueSets;
    private final boolean[] nullAxisFlags;

    protected AbstractSegmentBody(
        List<Pair<SortedSet<Comparable>, Boolean>> axes)
    {
        super();
        //noinspection unchecked
        this.axisValueSets = new SortedSet[axes.size()];
        this.nullAxisFlags = new boolean[axes.size()];
        for (int i = 0; i < axes.size(); i++) {
            Pair<SortedSet<Comparable>, Boolean> pair = axes.get(i);
            axisValueSets[i] = pair.left;
            nullAxisFlags[i] = pair.right;
        }
    }

    @Override
	public SortedSet<Comparable>[] getAxisValueSets() {
        return axisValueSets;
    }

    @Override
	public boolean[] getNullAxisFlags() {
        return nullAxisFlags;
    }

    @Override
	public Map<CellKey, Object> getValueMap() {
        return new AbstractMap<>() {
            @Override
			public Set<Entry<CellKey, Object>> entrySet() {
                return new AbstractSet<>() {
                    @Override
					public Iterator<Entry<CellKey, Object>> iterator() {
                        return new SegmentBodyIterator();
                    }

                    @Override
					public int size() {
                        return getEffectiveSize();
                    }
                };
            }
        };
    }

    @Override
	public Object getValueArray() {
        throw new UnsupportedOperationException(
            "This method is only supported for dense segments");
    }

    @Override
	public BitSet getNullValueIndicators() {
        throw new UnsupportedOperationException(
            "This method is only supported for dense segments of native values");
    }

    /**
     * Returns the overall amount of stored elements, including those,
     * that are considered to be null.
     * @return      the size of stored data
     */
    public abstract int getSize();

    /**
     * Returns the amount of non-null elements. This amount is equal to
     * number of elements that
     * getValueMap().entrySet().iterator() is returned.
     * By default the method executes getSize().
     * @return      the effective size of stored data
     */
    public int getEffectiveSize() {
      return getSize();
    }

    public abstract Object getObject(int i);

    /**
     * Iterator over all (cellkey, value) pairs in this data set.
     */
    private class SegmentBodyIterator
        implements Iterator<Map.Entry<CellKey, Object>>
    {
        private int i = -1;
        private final int[] ordinals;
        private final int size = getSize();
        private boolean hasNext = true;
        private Object next;

        SegmentBodyIterator() {
            ordinals = new int[axisValueSets.length];
            ordinals[ordinals.length - 1] = -1;
            moveToNext();
        }

        @Override
		public boolean hasNext() {
            return hasNext;
        }

        @Override
		public Map.Entry<CellKey, Object> next() {
            if(!hasNext()){
                throw new NoSuchElementException();
            }
            Pair<CellKey, Object> o =
                Pair.of(CellKey.Generator.newCellKey(ordinals), next);
            moveToNext();
            return o;
        }

        private void moveToNext() {
            for (;;) {
                ++i;
                if (i >= size) {
                    hasNext = false;
                    return;
                }
                int k = ordinals.length - 1;
                while (k >= 0) {
                    int j = 1;
                    if (nullAxisFlags[k]) {
                        j = 0;
                    }
                    if (ordinals[k] < axisValueSets[k].size() - j) {
                        ++ordinals[k];
                        break;
                    } else {
                        ordinals[k] = 0;
                        --k;
                    }
                }
                next = getObject(i);
                if (next != null) {
                    return;
                }
            }
        }

        @Override
		public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
