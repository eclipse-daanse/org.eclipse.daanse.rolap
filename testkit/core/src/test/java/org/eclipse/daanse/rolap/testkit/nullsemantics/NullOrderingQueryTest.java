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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.daanse.olap.api.result.Result;
import org.junit.jupiter.api.Test;

/**
 * characterization: TODAY's placement of NULL cells in Order and
 * TopCount. The
 * engine's sort order is -Infinity &lt; NULL &lt; values &lt; NaN &lt;
 * +Infinity, so NULLs come first ascending and last descending.
 *
 * <p>
 * ValSum by member: A=10.5, B=NULL, C=20.25, D=NULL, E=5.0.
 */
class NullOrderingQueryTest {

    @Test
    void orderAscendingPutsNullsBeforeValues() throws Exception {
        Result result = NullSemanticsFixture.execute("""
                SELECT {[Measures].[ValSum]} ON COLUMNS,
                       Order([KeyDim].[KeyHierarchy].[KeyLevel].Members, [Measures].[ValSum], BASC) ON ROWS
                FROM [NullSemantics]
                """);

        // NULL members first (stable in hierarchical order), then ascending values.
        assertEquals(List.of("B", "D", "E", "A", "C"), NullSemanticsFixture.rowMemberNames(result));
        assertTrue(result.getCell(new int[] { 0, 0 }).isNull());
        assertTrue(result.getCell(new int[] { 0, 1 }).isNull());
        assertEquals(Double.valueOf(5.0), result.getCell(new int[] { 0, 2 }).getValue());
        assertEquals(Double.valueOf(10.5), result.getCell(new int[] { 0, 3 }).getValue());
        assertEquals(Double.valueOf(20.25), result.getCell(new int[] { 0, 4 }).getValue());
    }

    @Test
    void orderDescendingPutsNullsAfterValues() throws Exception {
        Result result = NullSemanticsFixture.execute("""
                SELECT {[Measures].[ValSum]} ON COLUMNS,
                       Order([KeyDim].[KeyHierarchy].[KeyLevel].Members, [Measures].[ValSum], BDESC) ON ROWS
                FROM [NullSemantics]
                """);

        assertEquals(List.of("C", "A", "E", "B", "D"), NullSemanticsFixture.rowMemberNames(result));
        assertEquals(Double.valueOf(20.25), result.getCell(new int[] { 0, 0 }).getValue());
        assertTrue(result.getCell(new int[] { 0, 3 }).isNull());
        assertTrue(result.getCell(new int[] { 0, 4 }).isNull());
    }

    /**
     * TopCount ranks NULL cells below every value, so the top 3 are the three
     * members with non-NULL VAL.
 */
    @Test
    void topCountPrefersValuesOverNulls() throws Exception {
        Result result = NullSemanticsFixture.execute("""
                SELECT {[Measures].[ValSum]} ON COLUMNS,
                       TopCount([KeyDim].[KeyHierarchy].[KeyLevel].Members, 3, [Measures].[ValSum]) ON ROWS
                FROM [NullSemantics]
                """);

        assertEquals(List.of("C", "A", "E"), NullSemanticsFixture.rowMemberNames(result));
        assertEquals(Double.valueOf(20.25), result.getCell(new int[] { 0, 0 }).getValue());
        assertEquals(Double.valueOf(10.5), result.getCell(new int[] { 0, 1 }).getValue());
        assertEquals(Double.valueOf(5.0), result.getCell(new int[] { 0, 2 }).getValue());
    }
}
