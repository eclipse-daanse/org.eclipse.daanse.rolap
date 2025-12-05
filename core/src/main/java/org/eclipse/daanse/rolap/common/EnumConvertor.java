/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.rolap.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.Datatype;
import org.eclipse.daanse.olap.api.DataTypeJdbc;

public class EnumConvertor {

    public static List<Map<String, Entry<Datatype, Object>>> convertSessionValues(
            List<Map<String, Entry<DataTypeJdbc, Object>>> sessionValues) {
        if (sessionValues != null) {
            return sessionValues.stream().map(e -> convertMap(e)).toList();
        }
        return List.of();
    }

    private static Map<String, Entry<Datatype, Object>> convertMap(Map<String, Entry<DataTypeJdbc, Object>> map) {
    	Map<String, Entry<Datatype, Object>> result = new LinkedHashMap<String, Entry<Datatype, Object>>();
    	for (Map.Entry<String, Entry<DataTypeJdbc, Object>> e :  map.entrySet()) {
    		result.put(e.getKey(), toEntry(e.getValue()));
    	}
        return result;
    }

    private static Entry<Datatype, Object> toEntry(Entry<DataTypeJdbc, Object> e) {
        return Map.entry(Datatype.fromValue(e.getKey().getValue()), e.getValue());
    }

    public static BestFitColumnType toBestFitColumnType(String type) {
        return type != null ? BestFitColumnType.valueOf(type) : null;
    }
    
    public static DataTypeJdbc toDataTypeJdbc(Datatype type) {
        return type != null ? DataTypeJdbc.fromValue(type.getValue()) : null;
    }
}
