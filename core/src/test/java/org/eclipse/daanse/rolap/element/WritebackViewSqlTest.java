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
package org.eclipse.daanse.rolap.element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.element.db.DatabaseColumn;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.common.writeback.RolapWritebackColumn;
import org.eclipse.daanse.rolap.common.writeback.RolapWritebackTable;
import org.junit.jupiter.api.Test;

/**
 * Pins the writeback fact-view SQL built on the statement model: the writeback-table arm plus
 * one FROM-less literal SELECT per session row, appended as {@code union all} — the legacy
 * hand-concatenated shape, now spelled by the renderer (quoting + literals).
 */
class WritebackViewSqlTest {

    private static RolapWritebackTable writebackTable() {
        RolapWritebackTable table = mock(RolapWritebackTable.class);
        when(table.getName()).thenReturn("wb_fact");
        RolapWritebackColumn units = mock(RolapWritebackColumn.class);
        DatabaseColumn unitsCol = mock(DatabaseColumn.class);
        when(unitsCol.getName()).thenReturn("units");
        when(units.getColumn()).thenReturn(unitsCol);
        RolapWritebackColumn store = mock(RolapWritebackColumn.class);
        DatabaseColumn storeCol = mock(DatabaseColumn.class);
        when(storeCol.getName()).thenReturn("store_id");
        when(store.getColumn()).thenReturn(storeCol);
        when(table.getColumns()).thenReturn(List.of(units, store));
        return table;
    }

    @Test
    void unionTailCarriesWritebackTableAndSessionLiterals() {
        Map<String, Map.Entry<Datatype, Object>> row = new LinkedHashMap<>();
        row.put("units", Map.entry(Datatype.NUMERIC, (Object) 42));
        row.put("store_id", Map.entry(Datatype.VARCHAR, (Object) "s1"));
        String sql = RolapCube.renderWritebackUnionArms(new AnsiDialect(), writebackTable(), List.of(row));
        assertThat(sql)
            .startsWith(" union all ")
            .contains("select \"units\" as \"c0\", \"store_id\" as \"c1\" from \"wb_fact\" as \"wb_fact\"")
            .contains("union all select 42 as \"units\", 's1' as \"store_id\"");
    }

    @Test
    void emptySessionValuesKeepOnlyTheWritebackArm() {
        String sql = RolapCube.renderWritebackUnionArms(new AnsiDialect(), writebackTable(), List.of());
        assertThat(sql)
            .startsWith(" union all select")
            .contains("from \"wb_fact\"")
            .doesNotContain("union all select 42");
    }
}
