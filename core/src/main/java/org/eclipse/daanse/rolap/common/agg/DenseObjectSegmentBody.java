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

import  org.eclipse.daanse.olap.util.Pair;

/**
 * Implementation of a segment body which stores the data inside
 * a dense array of Java objects.
 *
 * @author LBoudreau
 */
public class DenseObjectSegmentBody extends AbstractSegmentBody {
    private static final long serialVersionUID = -3558427982849392173L;

    private final Object[] values;

    /**
     * Creates a DenseObjectSegmentBody.
     *
     * Stores the given array of cell values; caller must not modify it
     * afterwards.
     *
     * @param values Cell values
     * @param axes Axes
     */
    public DenseObjectSegmentBody(
        Object[] values,
        List<Pair<SortedSet<Comparable>, Boolean>> axes)
    {
        super(axes);
        this.values = values;
    }

    @Override
    public Object getValueArray() {
        return values;
    }

    @Override
    public Object getObject(int i) {
        return values[i];
    }

    @Override
    public int getSize() {
        return values.length;
    }
}
