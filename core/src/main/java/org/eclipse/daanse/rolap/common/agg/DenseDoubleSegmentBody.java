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

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.SortedSet;

import  org.eclipse.daanse.olap.util.Pair;

/**
 * Implementation of a segment body which stores the data inside
 * a dense primitive array of double precision numbers.
 *
 * @author LBoudreau
 */
public class DenseDoubleSegmentBody extends AbstractSegmentBody {
    private static final long serialVersionUID = 5775717165497921144L;

    private final double[] values;
    private final BitSet nullValues;

    /**
     * Creates a DenseDoubleSegmentBody.
     *
     * Stores the given array of cell values and null indicators; caller must
     * not modify them afterwards.
     *
     * @param nullValues A bit-set indicating whether values are null. Each
     *                   position in the bit-set corresponds to an offset in the
     *                   value array. If position is null, the corresponding
     *                   entry in the value array will also be 0.
     * @param values Cell values
     * @param axes Axes
     */
    public DenseDoubleSegmentBody(
        BitSet nullValues,
        double[] values,
        List<Pair<SortedSet<Comparable>, Boolean>> axes)
    {
        super(axes);
        this.values = values;
        this.nullValues = nullValues;
    }

    @Override
    public Object getValueArray() {
        return values;
    }

    @Override
    public BitSet getNullValueIndicators() {
        return nullValues;
    }

    @Override
    public int getSize() {
        return values.length;
    }

    @Override
    public int getEffectiveSize() {
        return values.length - nullValues.cardinality();
    }

    @Override
    public Object getObject(int i) {
        double value = values[i];
        if (value == 0d && nullValues.get(i)) {
            return null;
        }
        return value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DenseDoubleSegmentBody(size=");
        sb.append(values.length);
        sb.append(", data=");
        sb.append(Arrays.toString(values));
        sb.append(", notNullZeroValues=").append(nullValues);
        sb.append(", axisValueSets=");
        sb.append(Arrays.toString(getAxisValueSets()));
        sb.append(", nullAxisFlags=");
        sb.append(Arrays.toString(getNullAxisFlags()));
        if (getAxisValueSets().length > 0 && getAxisValueSets()[0].iterator().hasNext()) {
            sb.append(", aVS[0]=");
            sb.append(getAxisValueSets()[0].getClass());
            sb.append(", aVS[0][0]=");
            sb.append(getAxisValueSets()[0].iterator().next().getClass());
        }
        sb.append(")");
        return sb.toString();
    }
}
