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

import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Result;
import org.junit.jupiter.api.Test;

/**
 * Characterization of the load protocol (cache-miss, batch load,
 * re-evaluation): a query touching many cells must return identical
 * values on the first execution (cache-miss, dirty evaluation discarded and
 * re-run after the batch load) and on the second execution (cache-hit). This
 * freezes the batch/re-evaluation protocol externally, including NULL cells.
 */
class BatchLoadTest {

    private static final String MDX = """
            SELECT {[Measures].[ValSum], [Measures].[ValMin], [Measures].[ValMax], [Measures].[ValAvg],
                    [Measures].[DecSum]} ON COLUMNS,
                   [KeyDim].[KeyHierarchy].[KeyLevel].Members ON ROWS
            FROM [NullSemantics]
            """;

    @Test
    void cacheMissAndCacheHitReturnIdenticalCells() throws Exception {
        // First execution: cache miss, cells resolved via the batch loader.
        Result first = NullSemanticsFixture.execute(MDX);
        // Second execution (fresh parse): served from the segment cache.
        Result second = NullSemanticsFixture.execute(MDX);

        int columns = first.getAxes()[0].getPositions().size();
        int rows = first.getAxes()[1].getPositions().size();
        assertEquals(5, columns);
        assertEquals(5, rows);
        assertEquals(columns, second.getAxes()[0].getPositions().size());
        assertEquals(rows, second.getAxes()[1].getPositions().size());

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int[] pos = { col, row };
                Cell a = first.getCell(pos);
                Cell b = second.getCell(pos);
                String at = "cell [" + col + "," + row + "]";
                assertEquals(a.isNull(), b.isNull(), at + " isNull");
                assertEquals(a.getValue(), b.getValue(), at + " value");
                assertEquals(a.getFormattedValue(), b.getFormattedValue(), at + " formatted value");
            }
        }
    }
}
