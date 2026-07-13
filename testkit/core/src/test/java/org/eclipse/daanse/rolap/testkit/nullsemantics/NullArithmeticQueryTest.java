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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * characterization: TODAY's arithmetic with an MDX NULL calculated
 * member. Freezes the
 * MSAS-compatible rules {@code null + 1 = 1}, {@code null * x = null},
 * {@code x / null = +Infinity}.
 */
class NullArithmeticQueryTest {

    private static Result result;

    @BeforeAll
    static void executeQuery() throws Exception {
        result = NullSemanticsFixture.execute("""
                WITH MEMBER [Measures].[NullM] AS 'NULL'
                     MEMBER [Measures].[NullPlusOne] AS '[Measures].[NullM] + 1'
                     MEMBER [Measures].[NullTimesTwo] AS '[Measures].[NullM] * 2'
                     MEMBER [Measures].[OneOverNull] AS '1 / [Measures].[NullM]'
                     MEMBER [Measures].[IsEmptyNull] AS 'IsEmpty([Measures].[NullM])'
                SELECT {[Measures].[NullM], [Measures].[NullPlusOne], [Measures].[NullTimesTwo],
                        [Measures].[OneOverNull], [Measures].[IsEmptyNull]} ON COLUMNS
                FROM [NullSemantics]
                """);
    }

    @Test
    void nullLiteralMemberIsNullCell() {
        Cell cell = result.getCell(new int[] { 0 });
        assertTrue(cell.isNull());
        assertNull(cell.getValue(), "TODAY: getValue() of the NULL calculated member is Java null");
        assertEquals("", cell.getFormattedValue());
    }

    @Test
    void nullPlusOneIsOne() {
        Cell cell = result.getCell(new int[] { 1 });
        assertFalse(cell.isNull());
        assertEquals(Double.valueOf(1.0), cell.getValue(), "NULL + 1 = 1 (NULL treated as 0 in addition)");
    }

    @Test
    void nullTimesTwoIsNull() {
        Cell cell = result.getCell(new int[] { 2 });
        assertTrue(cell.isNull(), "NULL * 2 stays NULL (multiplication propagates NULL)");
        assertNull(cell.getValue());
    }

    /**
     * {@code 1 / NULL} yields +Infinity TODAY (MSAS default; the
     * nullDenominatorProducesNull property is off). The raw value is
     * {@code Double.POSITIVE_INFINITY} and it is rendered as "Infinity".
 */
    @Test
    void oneDividedByNullIsPositiveInfinity() {
        Cell cell = result.getCell(new int[] { 3 });
        assertFalse(cell.isNull());
        assertEquals(Double.valueOf(Double.POSITIVE_INFINITY), cell.getValue());
        assertEquals("Infinity", cell.getFormattedValue());
    }

    @Test
    void isEmptyOnNullMemberIsTrue() {
        Cell cell = result.getCell(new int[] { 4 });
        assertFalse(cell.isNull(), "the boolean result itself is a regular cell");
        assertEquals(Boolean.TRUE, cell.getValue());
    }
}
