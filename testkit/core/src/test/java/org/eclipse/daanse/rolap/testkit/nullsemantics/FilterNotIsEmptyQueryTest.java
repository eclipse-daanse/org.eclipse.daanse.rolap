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

import java.util.List;

import org.eclipse.daanse.olap.api.result.Result;
import org.junit.jupiter.api.Test;

/**
 * The interpreter's {@code Filter(..., Not IsEmpty(measure))} must exclude
 * NULL cells - including cells answered from the segment cache, where the
 * object-storage NULL placeholder ({@code NullValue.INSTANCE}) must map to
 * {@code NullValue}, not leak as a value
 * ({@code RolapAggregationManager.toCellValue}).
 */
class FilterNotIsEmptyQueryTest {

    @Test
    void filterNotIsEmptyExcludesNullCells() throws Exception {
        // Rows B and D have NULL ValSum; A, C, E have values.
        Result result = NullSemanticsFixture.execute("""
                SELECT {[Measures].[ValSum]} ON COLUMNS,
                       Filter([KeyDim].[KeyHierarchy].[KeyLevel].Members,
                              Not IsEmpty([Measures].[ValSum])) ON ROWS
                FROM [NullSemantics]
                """);
        List<String> rows = NullSemanticsFixture.rowMemberNames(result);
        assertEquals(List.of("A", "C", "E"), rows,
                "Filter(Not IsEmpty) must drop the NULL cells B and D");
    }

    /** Same shape, but with warmed cache (second execution). */
    @Test
    void filterNotIsEmptyExcludesNullCellsWarmCache() throws Exception {
        NullSemanticsFixture.execute("""
                SELECT {[Measures].[ValSum]} ON COLUMNS,
                       [KeyDim].[KeyHierarchy].[KeyLevel].Members ON ROWS
                FROM [NullSemantics]
                """);
        Result result = NullSemanticsFixture.execute("""
                SELECT {[Measures].[ValSum]} ON COLUMNS,
                       Filter([KeyDim].[KeyHierarchy].[KeyLevel].Members,
                              Not IsEmpty([Measures].[ValSum])) ON ROWS
                FROM [NullSemantics]
                """);
        List<String> rows = NullSemanticsFixture.rowMemberNames(result);
        assertEquals(List.of("A", "C", "E"), rows,
                "Filter(Not IsEmpty) must drop the NULL cells B and D (warm cache)");
    }
}
