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
package org.eclipse.daanse.rolap.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.junit.jupiter.api.Test;

/**
 * Session-value writeback rows legitimately carry {@code null} as the value
 * for "other" measure columns (typed-NULL bind for measures the current
 * writeback isn't targeting). {@link EnumConvertor#convertSessionValues}
 * must let these flow through — using {@link Map#entry} here would
 * {@code NullPointerException} via {@code Objects.requireNonNull} and break
 * any numeric writeback into a schema that also has text measures.
 */
class EnumConvertorNullValueTest {

    @Test
    void nullValuePassesThroughConversion() {
        Map<String, Map.Entry<DataTypeJdbc, Object>> row = new LinkedHashMap<>();
        row.put("CATEGORY",  new AbstractMap.SimpleEntry<>(DataTypeJdbc.VARCHAR, "A"));
        row.put("AMOUNT",    new AbstractMap.SimpleEntry<>(DataTypeJdbc.NUMERIC, -30.0));
        row.put("TXT_INPUT", new AbstractMap.SimpleEntry<>(DataTypeJdbc.VARCHAR, null));

        List<Map<String, Map.Entry<Datatype, Object>>> converted =
            EnumConvertor.convertSessionValues(List.of(row));

        assertThat(converted).hasSize(1);
        Map<String, Map.Entry<Datatype, Object>> out = converted.get(0);

        assertThat(out.get("CATEGORY").getKey()).isEqualTo(Datatype.VARCHAR);
        assertThat(out.get("CATEGORY").getValue()).isEqualTo("A");

        assertThat(out.get("AMOUNT").getKey()).isEqualTo(Datatype.NUMERIC);
        assertThat(out.get("AMOUNT").getValue()).isEqualTo(-30.0);

        // Critical: the VARCHAR-typed null must survive conversion intact.
        assertThat(out.get("TXT_INPUT").getKey()).isEqualTo(Datatype.VARCHAR);
        assertThat(out.get("TXT_INPUT").getValue()).isNull();
    }

    @Test
    void nullSessionValuesYieldsEmptyList() {
        assertThat(EnumConvertor.convertSessionValues(null)).isEmpty();
    }
}
