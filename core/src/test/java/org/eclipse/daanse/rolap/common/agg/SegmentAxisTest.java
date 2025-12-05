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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SegmentAxisTest {

    @Test
    void isTested() {
        assertThat(SegmentAxis.isSorted(new Integer[]{1, 2, 3, 4})).isTrue();
        assertThat(SegmentAxis.isSorted(new Integer[]{1, 2, 5, 4})).isFalse();

        assertThat(SegmentAxis.isSorted(new String[]{"a", "b", "c", "d"})).isTrue();
        assertThat(SegmentAxis.isSorted(new String[]{"a", "b", "d", "c"})).isFalse();

    }
}
