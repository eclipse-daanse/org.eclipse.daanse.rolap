/*
* Copyright (c) 2022 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   SmartCity Jena - initial
*/
package org.eclipse.daanse.rolap.common.agg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.daanse.rolap.common.agg.SegmentAxis;
import org.junit.jupiter.api.Test;

class SegmentAxisTest {

    @Test
    void testIsTested() {
        assertTrue(SegmentAxis.isSorted(new Integer[] { 1, 2, 3, 4 }));
        assertFalse(SegmentAxis.isSorted(new Integer[] { 1, 2, 5, 4 }));

        assertTrue(SegmentAxis.isSorted(new String[] { "a", "b", "c", "d" }));
        assertFalse(SegmentAxis.isSorted(new String[] { "a", "b", "d", "c" }));

    }
}
