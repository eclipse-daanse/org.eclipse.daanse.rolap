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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.nullsemantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Result;
import org.junit.jupiter.api.Test;

/**
 * MDX-level behavior of NULL cells.
 *
 * <p>
 * Fact rows B and D have {@code VAL = SQL NULL} (see
 * {@link NullSemanticsFixture}).
 */
class NullCellQueryTest {

    /**
     * A cell backed by a SQL NULL: {@code isNull()} is true,
     * {@code getValue()} returns Java {@code null} and the formatted value
     * is the empty string.
 */
    @Test
    void nullCellIsNullAndGetValueReturnsJavaNull() throws Exception {
        Result result = NullSemanticsFixture.execute("""
                SELECT {[Measures].[ValSum]} ON COLUMNS,
                       [KeyDim].[KeyHierarchy].[KeyLevel].Members ON ROWS
                FROM [NullSemantics]
                """);

        int rowB = NullSemanticsFixture.rowIndexOf(result, "B");
        Cell nullCell = result.getCell(new int[] { 0, rowB });
        assertTrue(nullCell.isNull(), "SQL-NULL backed cell must report isNull()");
        assertNull(nullCell.getValue(), "getValue() of a NULL cell is Java null");
        assertEquals("", nullCell.getFormattedValue(), "NULL cell formats as empty string");

        int rowA = NullSemanticsFixture.rowIndexOf(result, "A");
        Cell valueCell = result.getCell(new int[] { 0, rowA });
        assertFalse(valueCell.isNull());
        assertEquals(Double.valueOf(10.5), valueCell.getValue());
    }

    /** {@code IsEmpty} on a stored measure: true for NULL cells, false otherwise. */
    @Test
    void isEmptyOnStoredMeasure() throws Exception {
        Result result = NullSemanticsFixture.execute("""
                WITH MEMBER [Measures].[ValIsEmpty] AS 'IsEmpty([Measures].[ValSum])'
                SELECT {[Measures].[ValIsEmpty]} ON COLUMNS,
                       [KeyDim].[KeyHierarchy].[KeyLevel].Members ON ROWS
                FROM [NullSemantics]
                """);

        assertEquals(Boolean.FALSE,
                result.getCell(new int[] { 0, NullSemanticsFixture.rowIndexOf(result, "A") }).getValue());
        assertEquals(Boolean.TRUE,
                result.getCell(new int[] { 0, NullSemanticsFixture.rowIndexOf(result, "B") }).getValue());
        assertEquals(Boolean.TRUE,
                result.getCell(new int[] { 0, NullSemanticsFixture.rowIndexOf(result, "D") }).getValue());
    }

    /** {@code CoalesceEmpty(measure, 0)} replaces NULL cells with 0.0. */
    @Test
    void coalesceEmptyReplacesNullWithDefault() throws Exception {
        Result result = NullSemanticsFixture.execute("""
                WITH MEMBER [Measures].[ValOrZero] AS 'CoalesceEmpty([Measures].[ValSum], 0)'
                SELECT {[Measures].[ValOrZero]} ON COLUMNS,
                       [KeyDim].[KeyHierarchy].[KeyLevel].Members ON ROWS
                FROM [NullSemantics]
                """);

        Cell coalescedNull = result.getCell(new int[] { 0, NullSemanticsFixture.rowIndexOf(result, "B") });
        assertFalse(coalescedNull.isNull());
        assertEquals(Double.valueOf(0.0), coalescedNull.getValue());
        assertEquals(Double.valueOf(20.25),
                result.getCell(new int[] { 0, NullSemanticsFixture.rowIndexOf(result, "C") }).getValue());
    }

    /**
     * Aggregates over the partially-NULL VAL column (10.5, NULL, 20.25, NULL,
     * 5.0) ignore NULL rows: Sum/Min/Max as if the NULL rows did not exist,
     * Avg divides by the count of non-NULL rows (3), not the row count (5).
 */
    @Test
    void aggregatesIgnoreNullRows() throws Exception {
        Result result = NullSemanticsFixture.execute("""
                SELECT {[Measures].[ValSum], [Measures].[ValMin], [Measures].[ValMax],
                        [Measures].[ValAvg]} ON COLUMNS
                FROM [NullSemantics]
                """);

        assertEquals(Double.valueOf(35.75), result.getCell(new int[] { 0 }).getValue(), "Sum");
        assertEquals(Double.valueOf(5.0), result.getCell(new int[] { 1 }).getValue(), "Min");
        assertEquals(Double.valueOf(20.25), result.getCell(new int[] { 2 }).getValue(), "Max");
        assertEquals(Double.valueOf(35.75 / 3.0), result.getCell(new int[] { 3 }).getValue(),
                "Avg over 3 non-NULL rows");
    }
}
