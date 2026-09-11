/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.poc;

import org.eclipse.daanse.olap.api.result.Result;

/**
 * Small read helper for {@link Result}: pulls out the unique name of the
 * first member of the first position on a given axis, for tests that only
 * need a quick sanity check on what an axis resolved to.
 */
public final class ResultProvider {

    private ResultProvider() {
    }

    public static String getAxis(Result result, int axisIndex, int row, int coll) {
        return result.getAxes()[axisIndex].getPositions().get(row).get(coll).getUniqueName();
    }

    /**
     * Formatted value of the cell at coordinates {@code (i, j, k, ...)} - one
     * position per axis, in {@link Result#getAxes()} order (axis 0 = columns,
     * axis 1 = rows, ...). Only as many leading coordinates as the result has
     * axes are used, so a 2-axis result tolerates trailing, unused
     * coordinates instead of throwing.
     */
    public static String getCell(Result result, int... coordinates) {
        int axisCount = result.getAxes().length;
        if (coordinates.length < axisCount) {
            throw new IllegalArgumentException(
                    "need at least " + axisCount + " coordinates (one per axis), got " + coordinates.length);
        }
        int[] pos = new int[axisCount];
        System.arraycopy(coordinates, 0, pos, 0, axisCount);
        return result.getCell(pos).getFormattedValue();
    }
}
