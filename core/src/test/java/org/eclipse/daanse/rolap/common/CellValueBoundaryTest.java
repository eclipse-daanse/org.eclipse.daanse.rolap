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
package org.eclipse.daanse.rolap.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.eclipse.daanse.olap.api.result.ErrorValue;
import org.eclipse.daanse.olap.api.result.NotLoaded;
import org.eclipse.daanse.olap.api.result.NullValue;
import org.eclipse.daanse.olap.api.result.ObjectValue;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link RolapAggregationManager#toCellValue}, the bridge from the
 * object convention of the cache probe API ({@link NullValue#INSTANCE} for a
 * stored NULL / Java {@code null} for "not in cache" / raw value otherwise)
 * into the sealed {@code CellValue} protocol of the {@code CellReader}
 * chain.
 *
 * <p>
 * A NULL cell must surface as {@link NullValue} (empty) and never leak as an
 * {@link ObjectValue} carrying a placeholder — the interpreter's
 * {@code IsEmpty} would then report NULL cells as non-empty and diverge from
 * the native SQL path.
 */
class CellValueBoundaryTest {

    @Test
    void absentCellYieldsTheRequestedAbsentState() {
        assertSame(NullValue.INSTANCE,
                RolapAggregationManager.toCellValue(null, NullValue.INSTANCE));
        assertSame(NotLoaded.INSTANCE,
                RolapAggregationManager.toCellValue(null, NotLoaded.INSTANCE));
    }

    @Test
    void rawValueWrapsAsObjectValue() {
        ObjectValue ov = assertInstanceOf(ObjectValue.class,
                RolapAggregationManager.toCellValue(74748.0, NullValue.INSTANCE));
        assertEquals(74748.0, ov.value());
    }

    @Test
    void throwableWrapsAsErrorValue() {
        RuntimeException failure = new RuntimeException("boom");
        ErrorValue ev = assertInstanceOf(ErrorValue.class,
                RolapAggregationManager.toCellValue(failure, NullValue.INSTANCE));
        assertSame(failure, ev.cause());
    }

    @Test
    void cellValueStatesPassThroughUnchanged() {
        assertSame(NotLoaded.INSTANCE,
                RolapAggregationManager.toCellValue(NotLoaded.INSTANCE, NullValue.INSTANCE));
        assertSame(NullValue.INSTANCE,
                RolapAggregationManager.toCellValue(NullValue.INSTANCE, NotLoaded.INSTANCE));
    }
}
