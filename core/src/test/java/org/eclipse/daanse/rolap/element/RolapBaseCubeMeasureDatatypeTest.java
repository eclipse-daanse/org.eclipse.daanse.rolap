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

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the DATATYPE property conversion in
 * {@link RolapBaseCubeMeasure#toDialectDatatype(Object)}.
 *
 * <p>The constructor stores literals from the local {@code DataType} enum
 * ({@code "Integer"}, {@code "Numeric"}, {@code "String"}) — these are the same
 * literals the EMF mapping {@code ColumnInternalDataType} produces. The jdbc
 * dialect {@link Datatype} enum uses different value names ({@code "Integer"},
 * {@code "Numeric"}, {@code "Varchar"}) and {@code Datatype.fromValue("String")}
 * silently falls back to {@link Datatype#NUMERIC}. Without the explicit
 * {@code "String"} → {@code VARCHAR} mapping, text-aggregation (LISTAGG)
 * measures were typed as numeric and {@code SegmentLoader} attempted to
 * {@code Double.parseDouble} their concatenated string results.
 */
class RolapBaseCubeMeasureDatatypeTest {

    @Test
    void stringLiteralMapsToVarchar() {
        // Without the explicit map this returned NUMERIC.
        assertThat(RolapBaseCubeMeasure.toDialectDatatype("String"))
            .isEqualTo(Datatype.VARCHAR);
    }

    @Test
    void numericLiteralMapsToNumeric() {
        assertThat(RolapBaseCubeMeasure.toDialectDatatype("Numeric"))
            .isEqualTo(Datatype.NUMERIC);
    }

    @Test
    void integerLiteralMapsToInteger() {
        assertThat(RolapBaseCubeMeasure.toDialectDatatype("Integer"))
            .isEqualTo(Datatype.INTEGER);
    }

    @Test
    void varcharLiteralMapsToVarchar() {
        // The dialect-side spelling also flows through.
        assertThat(RolapBaseCubeMeasure.toDialectDatatype("Varchar"))
            .isEqualTo(Datatype.VARCHAR);
    }

    @Test
    void nullDefaultsToVarcharByCatchAll() {
        // Datatype.fromValue(null) returns NUMERIC by its contract, so the catch
        // path is not exercised here; null specifically routes through fromValue.
        assertThat(RolapBaseCubeMeasure.toDialectDatatype(null))
            .isEqualTo(Datatype.NUMERIC);
    }

    @Test
    void unrecognizedStringFallsBackToNumeric() {
        // Existing behaviour for unknown values: fromValue() falls back to NUMERIC.
        assertThat(RolapBaseCubeMeasure.toDialectDatatype("SomethingUnknown"))
            .isEqualTo(Datatype.NUMERIC);
    }

    @Test
    void nonStringObjectFallsBackToVarcharViaCatch() {
        // ClassCastException path → VARCHAR.
        assertThat(RolapBaseCubeMeasure.toDialectDatatype(Integer.valueOf(42)))
            .isEqualTo(Datatype.VARCHAR);
    }
}
