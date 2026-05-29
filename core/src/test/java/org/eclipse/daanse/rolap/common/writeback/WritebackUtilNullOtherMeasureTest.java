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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.util.resource.relational.SqlSimpleTypes;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.element.Member;
import org.junit.jupiter.api.Test;

/**
 * Pins the behaviour of {@code WritebackUtil.nullEntryForOtherMeasure}: when
 * the numeric allocation path encounters a measure column other than the one
 * being written, the produced session-value cell must be NULL bound at the
 * measure's native JDBC datatype — never a literal {@code 0} of NUMERIC.
 *
 * <p>The previous {@code Map.entry(DataTypeJdbc.NUMERIC, 0)} behaviour
 * produced {@code 0 as "COMMENT"} in the writeback UNION ALL even though
 * {@code COMMENT} is a VARCHAR text-aggregation column. H2's UNION ALL then
 * inferred NUMERIC for the column and failed converting the VARCHAR rows
 * from the writeback table ({@code Data conversion error converting
 * "initial entry for A"}).
 */
class WritebackUtilNullOtherMeasureTest {

    @Test
    void textMeasureGetsTypedNullVarcharEntry() throws Exception {
        Column commentColumn = RelationalFactory.eINSTANCE.createColumn();
        commentColumn.setName("COMMENT");
        commentColumn.setType(SqlSimpleTypes.Sql99.varcharType());

        RolapWritebackMeasure textMeasure = new RolapWritebackMeasure(
                mock(Member.class), commentColumn, Datatype.VARCHAR);

        var entry = invokeNullEntry(textMeasure);

        assertThat(entry.getKey()).isEqualTo(DataTypeJdbc.VARCHAR);
        assertThat(entry.getValue()).isNull();
    }

    @Test
    void numericMeasureGetsTypedNullNumericEntry() throws Exception {
        Column priceColumn = RelationalFactory.eINSTANCE.createColumn();
        priceColumn.setName("PRICE");
        priceColumn.setType(SqlSimpleTypes.Sql99.integerType());

        RolapWritebackMeasure numericMeasure = new RolapWritebackMeasure(
                mock(Member.class), priceColumn, Datatype.NUMERIC);

        var entry = invokeNullEntry(numericMeasure);

        assertThat(entry.getKey()).isEqualTo(DataTypeJdbc.NUMERIC);
        assertThat(entry.getValue()).isNull();
    }

    @Test
    void integerMeasureFallsBackToNumericNull() throws Exception {
        // INTEGER, DECIMAL, etc. all bind via the NUMERIC JDBC path.
        Column countColumn = RelationalFactory.eINSTANCE.createColumn();
        countColumn.setName("CNT");
        countColumn.setType(SqlSimpleTypes.Sql99.integerType());

        Member m = mock(Member.class);
        // Bypass the convenience ctor — set datatype to anything non-VARCHAR.
        RolapWritebackMeasure intMeasure = new RolapWritebackMeasure(
                m, countColumn, Datatype.INTEGER);

        var entry = invokeNullEntry(intMeasure);

        assertThat(entry.getKey()).isEqualTo(DataTypeJdbc.NUMERIC);
        assertThat(entry.getValue()).isNull();
    }

    /** Helper to call the package-private static method via reflection. */
    @SuppressWarnings("unchecked")
    private static java.util.Map.Entry<DataTypeJdbc, Object> invokeNullEntry(RolapWritebackMeasure other) throws Exception {
        Method m = WritebackRowBuilder.class.getDeclaredMethod("nullEntryForOtherMeasure", RolapWritebackMeasure.class);
        m.setAccessible(true);
        return (java.util.Map.Entry<DataTypeJdbc, Object>) m.invoke(null, other);
    }
}
