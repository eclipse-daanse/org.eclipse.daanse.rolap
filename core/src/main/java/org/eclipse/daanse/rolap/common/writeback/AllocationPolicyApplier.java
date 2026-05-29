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

public class AllocationPolicyApplier {

    public static List<Map<String, Map.Entry<DataTypeJdbc, Object>>> allocateData(
            Map<List<Member>, Object> data,
            String measureName,
            Double value,
            AllocationPolicy allocation,
            RolapWritebackTable writebackTable
        ) {
            List<Map<List<Member>, Double>> res = new ArrayList<Map<List<Member>, Double>>();
            Map<List<Member>, Double> d = new HashMap<>();
            Map<List<Member>, Double> dMinus = new HashMap<>();
            int size = data.size();
            switch (allocation) {
                case EQUAL_ALLOCATION:
                    double val = value / size;
                    for (Map.Entry<List<Member>, Object> entry : data.entrySet()) {
                        dMinus.put(entry.getKey(), (-1) * (Double) entry.getValue());
                        d.put(entry.getKey(), val);
                    }
                    break;
                case WEIGHTED_ALLOCATION:
                    Double sum = data.entrySet().stream().mapToDouble(en -> ((Double) en.getValue())).sum();
                    for (Map.Entry<List<Member>, Object> entry : data.entrySet()) {
                        dMinus.put(entry.getKey(), (-1) * (Double) entry.getValue());
                        d.put(entry.getKey(), value / sum * (Double) entry.getValue());
                    }
                    break;
                case EQUAL_INCREMENT:
                    sum = data.entrySet().stream().mapToDouble(en -> ((Double) en.getValue())).sum();
                    double offset = value - sum;
                    for (Map.Entry<List<Member>, Object> entry : data.entrySet()) {
                        dMinus.put(entry.getKey(), (-1) * (Double) entry.getValue());
                        d.put(entry.getKey(), (Double) entry.getValue() + offset / size);
                    }
                    break;
                case WEIGHTED_INCREMENT:
                    sum = data.entrySet().stream().mapToDouble(en -> ((Double) en.getValue())).sum();
                    offset = value - sum;
                    for (Map.Entry<List<Member>, Object> entry : data.entrySet()) {
                        dMinus.put(entry.getKey(), (-1) * (Double) entry.getValue());
                        d.put(entry.getKey(), (Double) entry.getValue() + offset / sum * (Double) entry.getValue());
                    }
                    break;
                default:
                    size = data.size();
                    val = value / size;
                    for (Map.Entry<List<Member>, Object> entry : data.entrySet()) {
                        dMinus.put(entry.getKey(), (-1) * (Double) entry.getValue());
                        d.put(entry.getKey(), val);
                    }
            }
            res.add(dMinus);
            res.add(d);
            return WritebackRowBuilder.allocateData(res, measureName, writebackTable);
        }
}
