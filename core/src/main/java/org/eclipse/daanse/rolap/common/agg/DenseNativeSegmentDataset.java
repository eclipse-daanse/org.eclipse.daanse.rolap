/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2002-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * jhyde, 21 March, 2002
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

import org.eclipse.daanse.olap.key.CellKey;

/**
 * Implementation of {@link DenseSegmentDataset} that stores
 * values of type {@code double}.
 *
 * @author jhyde
 */
abstract class DenseNativeSegmentDataset extends DenseSegmentDataset {
    protected final BitSet nullValues;

    /**
     * Creates a DenseNativeSegmentDataset.
     *
     * @param axes Segment axes, containing actual column values
     * @param nullValues A bit-set indicating whether values are null. Each
     *                   position in the bit-set corresponds to an offset in the
     *                   value array. If position is null, the corresponding
     *                   entry in the value array will also be 0.
     */
    DenseNativeSegmentDataset(
        SegmentAxis[] axes,
        BitSet nullValues)
    {
        super(axes);
        this.nullValues = nullValues;
    }

    @Override
	public boolean isNull(CellKey key) {
        int offset = key.getOffset(axisMultipliers);
        return isNull(offset);
    }

    /**
     * Returns whether the value at the given offset is null.
     *
     * The native value at this offset will also be 0. You only need to
     * call this method if the {@link #getInt getXxx} method has returned 0.
     *
     * @param offset Cell offset
     * @return Whether the cell at this offset is null
     */
    protected final boolean isNull(int offset) {
        return nullValues.get(offset);
    }
}
