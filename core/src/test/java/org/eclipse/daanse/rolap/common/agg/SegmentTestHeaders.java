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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.agg;

import java.util.List;
import java.util.TreeSet;

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentColumn;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.util.ByteString;

/**
 * Test fixture: the minimal one-column segment header the agg tests
 * shared as three byte-identical private builders.
 */
final class SegmentTestHeaders {

    private SegmentTestHeaders() {
    }

    static SegmentHeader header(String measure) {
        return new SegmentHeader("Schema", new ByteString("c".getBytes()), "Cube", measure,
                List.of(new SegmentColumn("[f].[c]", 10, new TreeSet<>(List.of("a")))),
                List.of(), "FACT", BitKey.Factory.makeBitKey(3), List.of());
    }
}
