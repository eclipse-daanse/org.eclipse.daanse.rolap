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

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.Datatype;
import org.eclipse.daanse.olap.api.DataTypeJdbc;

public class EnumConvertor {

    public static List<Map<String, Entry<Datatype, Object>>> convertSessionValues(
            List<Map<String, Entry<DataTypeJdbc, Object>>> sessionValues) {
        if (sessionValues != null) {
            sessionValues.stream().map(e -> convertMap(e)).toList();
        }
        return List.of();
    }

    private static Map<String, Entry<Datatype, Object>> convertMap(Map<String, Entry<DataTypeJdbc, Object>> map) {
        return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> toEntry(e.getValue())));

    }

    private static Entry<Datatype, Object> toEntry(Entry<DataTypeJdbc, Object> e) {
        return Map.entry(Datatype.fromValue(e.getKey().name()), e.getValue());
    }

    public static BestFitColumnType toBestFitColumnType(String type) {
        return type != null ? BestFitColumnType.valueOf(type) : null;
    }
    
    public static DataTypeJdbc toDataTypeJdbc(Datatype type) {
        return type != null ? DataTypeJdbc.fromValue(type.getValue()) : null;
    }
}
