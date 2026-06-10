/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.rolap.element.RolapCubeMember;

public class WritebackRowBuilder {
	
    public static List<Map<String, Map.Entry<DataTypeJdbc, Object>>> allocateData(
            List<Map<List<Member>, Double>> l,
            String measureName,
            RolapWritebackTable writebackTable
        ) {
            List<Map<String, Map.Entry<DataTypeJdbc, Object>>> res = new ArrayList<>();
            for (Map<List<Member>, Double> d : l) {
                for (Map.Entry<List<Member>, Double> entry : d.entrySet()) {
                    List<Member> ms = entry.getKey();
                    Double value = entry.getValue();
                    List<RolapWritebackColumn> columns = writebackTable.getColumns();
                    Map<String, Map.Entry<DataTypeJdbc, Object>> mRes = new LinkedHashMap<>();
                    for (RolapWritebackColumn column : columns) {
                        if (column instanceof RolapWritebackMeasure rolapWritebackMeasure) {
                            if (rolapWritebackMeasure.getMeasure().getUniqueName().equals(measureName)) {
                                mRes.put(rolapWritebackMeasure.getColumn().getName(), Map.entry(rolapWritebackMeasure.getColumn().getType() != null ? rolapWritebackMeasure.getColumn().getType() : DataTypeJdbc.NUMERIC, round(value, rolapWritebackMeasure.getColumn().getType())));
                            } else {
                                mRes.put(rolapWritebackMeasure.getColumn().getName(),
                                        nullEntryForOtherMeasureOr0(rolapWritebackMeasure));
                            }
                        }
                        else if (column instanceof RolapWritebackAttribute rolapWritebackAttribute) {
                            Dimension dimensionWritebackAttribute = rolapWritebackAttribute.getDimension();
                            Optional<Member> oMember = ms.stream().filter(m -> dimensionWritebackAttribute.equals(m.getDimension())).findFirst();
                            if (oMember.isPresent()) {
                                Member m = oMember.get();
                                if (m instanceof RolapCubeMember rolapCubeMember) {
                                    Object key = rolapCubeMember.getKey();
                                    mRes.put(rolapWritebackAttribute.getColumn().getName(),
                                        Map.entry(rolapWritebackAttribute.getColumn().getType() != null ? rolapWritebackAttribute.getColumn().getType() : DataTypeJdbc.VARCHAR, key));
                                } else {
                                    throw new RuntimeException("Writeback  allocateData wrong member type");
                                }
                            } else {
                                throw new RuntimeException("Writeback member absent for " + rolapWritebackAttribute.getColumn().getName());
                            }
                        }
                    }
                    res.add(mRes);
                }
            }
            return res;
        }

    private static Object round(Double value, DataTypeJdbc type) {
        if (type != null && ( DataTypeJdbc.BIGINT.equals(type) || DataTypeJdbc.INTEGER.equals(type))) {
             return Math.round(value);
        }
        return value;
	}

	/**
     * Builds a {@code (datatype, null)} entry for a writeback measure column
     * that is NOT the one being written in this allocation. The datatype is
     * the measure's native bind type — VARCHAR for text-aggregation
     * (LISTAGG/TextAggMeasure) columns, NUMERIC otherwise.
     *
     * <p>Using a literal {@code 0} of NUMERIC type here (the previous
     * behaviour) collides with H2's UNION ALL type inference when the
     * read-side column is VARCHAR: H2 picks the NUMERIC branch as the
     * inferred type and then fails to convert the VARCHAR rows from the
     * writeback table. {@link AbstractMap.SimpleEntry} is used because
     * {@link Map#entry} rejects {@code null} values.
     */
    private static Map.Entry<DataTypeJdbc, Object> nullEntryForOtherMeasureOr0(RolapWritebackMeasure other) {
        DataTypeJdbc otherBind = other.getDatatype() == Datatype.VARCHAR
                ? DataTypeJdbc.VARCHAR
                : DataTypeJdbc.NUMERIC;
        Object value = other.getDatatype() == Datatype.VARCHAR ? null : 0d;
        return new AbstractMap.SimpleEntry<>(otherBind, value);
    }


}
