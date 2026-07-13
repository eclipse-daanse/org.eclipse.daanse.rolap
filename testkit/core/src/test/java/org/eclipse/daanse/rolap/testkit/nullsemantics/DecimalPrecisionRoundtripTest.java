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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;

import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Result;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * DECIMAL(19,4) roundtrip: fact column DEC_VAL holds exact decimals whose sum
 * (112345678901234.5682) is not representable as a double. TODAY the engine
 * narrows the values to double, so the aggregate visibly loses the 4-decimal
 * precision.
 */
class DecimalPrecisionRoundtripTest {

    private static final String MDX = """
            SELECT {[Measures].[DecSum]} ON COLUMNS
            FROM [NullSemantics]
            """;

    /** Exact BigDecimal sum of the DEC_VAL column: 112345678901234.5682. */
    private static BigDecimal exactSum() {
        return NullSemanticsFixture.DEC_VALUES.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Target behavior with the decimal lane: Sum over a DECIMAL(19,4) column
     * equals the exact BigDecimal sum of the column values.
 */
    @Test
    @Disabled("Sum over DECIMAL(19,4) is narrowed to double and loses the .5682 fraction."
            + " An exact decimal lane was evaluated and deliberately not built;"
            + " this documents the accepted double narrowing.")
    void sumOverDecimalColumnIsExact() throws Exception {
        Result result = NullSemanticsFixture.execute(MDX);
        Cell cell = result.getCell(new int[] { 0 });
        BigDecimal actual = new BigDecimal(String.valueOf(cell.getValue()));
        assertEquals(0, exactSum().compareTo(actual),
                "expected exact decimal sum " + exactSum() + " but was " + actual);
    }

    /**
     * TODAY's behavior, kept under regression watch: the value arrives as a
     * Double and equals the double-narrowed exact sum — 1.1234567890123456E14
     * instead of 112345678901234.5682 (the .0082 tail is gone). Turn this
     * around (and delete it) in when
     * {@link #sumOverDecimalColumnIsExact()} is enabled.
 */
    @Test
    void sumOverDecimalColumnLosesPrecisionToday() throws Exception {
        Result result = NullSemanticsFixture.execute(MDX);
        Cell cell = result.getCell(new int[] { 0 });
        Object value = cell.getValue();
        assertInstanceOf(Double.class, value, "TODAY the DECIMAL sum surfaces as a Double");

        // Frozen observed value (== exactSum().doubleValue()):
        assertEquals(Double.valueOf(1.1234567890123456E14), value);
        assertEquals(exactSum().doubleValue(), ((Double) value).doubleValue());

        // ... and it is demonstrably NOT the exact decimal sum:
        assertNotEquals(0, exactSum().compareTo(new BigDecimal(((Double) value).doubleValue())),
                "double-narrowed sum must differ from the exact decimal sum — precision was lost");
    }

    /**
     * Same loss on a single cell: member A's DEC_VAL is 99999999999999.9999,
     * which TODAY comes back as the double 1.0E14 — the fraction is entirely
     * absorbed by the narrowing.
 */
    @Test
    void singleDecimalCellLosesFractionToday() throws Exception {
        Result result = NullSemanticsFixture.execute("""
                SELECT {[Measures].[DecSum]} ON COLUMNS,
                       {[KeyDim].[KeyHierarchy].[A]} ON ROWS
                FROM [NullSemantics]
                """);
        Cell cell = result.getCell(new int[] { 0, 0 });
        assertEquals(Double.valueOf(1.0E14), cell.getValue(),
                "TODAY 99999999999999.9999 is reported as 1.0E14");
        assertEquals("100,000,000,000,000", cell.getFormattedValue());
    }
}
