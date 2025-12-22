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

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.olap.key.CellKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import  org.eclipse.daanse.olap.util.Pair;

/**
 * A SegmentDataset holds the values in a segment.
 *
 * @author jhyde
 * @since 21 March, 2002
 */
public interface SegmentDataset extends Iterable<Map.Entry<CellKey, Object>> {
    /**
     * Returns the value at a given coordinate, as an {@link Object}.
     *
     * @param pos Coordinate position
     * @return Value
     */
    Object getObject(CellKey pos);

    /**
     * Returns the value at a given coordinate, as an {@code int}.
     *
     * @param pos Coordinate position
     * @return Value
     */
    int getInt(CellKey pos);

    /**
     * Returns the value at a given coordinate, as a {@code double}.
     *
     * @param pos Coordinate position
     * @return Value
     */
    double getDouble(CellKey pos);

    /**
     * Returns whether the cell at a given coordinate is null.
     *
     * @param pos Coordinate position
     * @return Whether cell value is null
     */
    boolean isNull(CellKey pos);

    /**
     * Returns whether there is a value at a given coordinate.
     *
     * @param pos Coordinate position
     * @return Whether there is a value
     */
    boolean exists(CellKey pos);

    /**
     * Returns the number of bytes occupied by this dataset.
     *
     * @return number of bytes
     */
    double getBytes();

    void populateFrom(int[] pos, SegmentDataset data, CellKey key);

    /**
     * Sets the value a given ordinal.
     *
     * @param pos Ordinal
     * @param rowList Row list
     * @param column Column of row list
     */
    void populateFrom(
        int[] pos, SegmentLoader.RowList rowList, int column);

    /**
     * Returns the SQL type of the data contained in this dataset.
     * @return A value of BestFitColumnType
     */
    BestFitColumnType getType();

    /**
     * Return an immutable, final and serializable implementation
     * of a SegmentBody in order to cache this dataset.
     *
     * @param axes An array with, for each axis, the set of axis values, sorted
     *     in natural order, and a flag saying whether the null value is also
     *     present.
     *     This is supplied by the {@link SegmentLoader}.
     *
     * @return A {@link SegmentBody}.
     */
    SegmentBody createSegmentBody(
        List<Pair<SortedSet<Comparable>, Boolean>> axes);
}
