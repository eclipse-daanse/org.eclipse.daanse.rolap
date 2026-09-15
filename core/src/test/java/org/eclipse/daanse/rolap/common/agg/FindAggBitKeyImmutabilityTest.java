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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.junit.jupiter.api.Test;

/**
 * findAgg expands parent bits on a COPY of the caller's level bit key: the
 * argument is the batch's shared, published request key and must come back
 * bit-identical (the BitKey contract forbids mutating published keys).
 */
class FindAggBitKeyImmutabilityTest {

    @Test
    void findAggLeavesTheLevelBitKeyUntouched() {
        RolapStar star = mock(RolapStar.class);
        when(star.getAggStars()).thenReturn(List.of());
        RolapStar.Column column = mock(RolapStar.Column.class);
        when(star.getColumn(5)).thenReturn(column);
        when(column.getParentColumn()).thenReturn(null);

        BitKey levelBitKey = BitKey.Factory.makeBitKey(200);
        levelBitKey.set(5);
        levelBitKey.set(150);
        RolapStar.Column column150 = mock(RolapStar.Column.class);
        when(star.getColumn(150)).thenReturn(column150);
        when(column150.getParentColumn()).thenReturn(null);
        BitKey expected = levelBitKey.copy();

        BitKey measureBitKey = BitKey.Factory.makeBitKey(200);
        measureBitKey.set(180);

        AggregationManager.findAgg(star, levelBitKey, measureBitKey, new boolean[1]);

        assertThat((Object) levelBitKey).isEqualTo(expected);
        assertThat(measureBitKey.get(180)).isTrue();
    }
}
