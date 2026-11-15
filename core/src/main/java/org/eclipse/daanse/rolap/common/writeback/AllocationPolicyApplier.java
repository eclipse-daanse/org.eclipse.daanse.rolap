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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.writeback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.result.AllocationPolicy;
import org.eclipse.daanse.olap.writeback.AllocationShares;

/**
 * Turns "set this aggregate to {@code value}" into per-cell delta rows:
 * for every target cell one row cancelling the old value and one row
 * carrying the new share.
 *
 * Cell values arrive as whatever the evaluator produced - Double,
 * Integer, BigDecimal or null (empty cell) - and are normalized through
 * {@link #num(Object)}; a hard {@code (Double)} cast used to NPE on the
 * first empty target cell. A zero base sum degrades the WEIGHTED
 * policies to their EQUAL siblings instead of dividing by zero (a fresh
 * plan starts at 0 everywhere - NaN rows reached the writeback table).
 * Integer-typed measure columns get largest-remainder rounding over the
 * shares, so the rounded rows still sum to the rounded target.
 */
public final class AllocationPolicyApplier {

    private AllocationPolicyApplier() {
    }

    public static List<Map<String, Map.Entry<DataTypeJdbc, Object>>> allocateData(
            Map<List<Member>, Object> data,
            String measureName,
            Double value,
            AllocationPolicy allocation,
            RolapWritebackTable writebackTable
        ) {
            AllocationShares.Shares shares = AllocationShares.of(data, num(value), allocation,
                    isIntegerColumn(measureName, writebackTable));
            List<Map<List<Member>, Double>> res = new ArrayList<>();
            res.add(shares.removals());
            res.add(shares.additions());
            return WritebackRowBuilder.allocateData(res, measureName, writebackTable);
        }

    /** Evaluator output is Double, Integer, BigDecimal or null (empty cell). */
    static double num(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    private static boolean isIntegerColumn(String measureName, RolapWritebackTable writebackTable) {
        for (RolapWritebackColumn column : writebackTable.getColumns()) {
            if (column instanceof RolapWritebackMeasure measure
                    && measure.getMeasure().getUniqueName().equals(measureName)) {
                DataTypeJdbc type = measure.getColumn().getType();
                return DataTypeJdbc.INTEGER.equals(type) || DataTypeJdbc.BIGINT.equals(type);
            }
        }
        return false;
    }

}
