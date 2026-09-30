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
package org.eclipse.daanse.rolap.common.star;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentColumn;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.spi.SegmentRegion;
import org.eclipse.daanse.olap.util.ByteString;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** The audit renderings: header form, region boxes, coordinate maps. */
class BitKeyExplainTest {

    /**
     * A wire-decoded key can carry bits this star does not know; the
     * diagnostic rendering degrades to ?#pos instead of aborting the
     * caller (RolapStar.getColumn throws beyond the column list).
     */
    @Test
    void starExplainDegradesForUnknownBits() {
        RolapStar star = Mockito.mock(RolapStar.class);
        Mockito.when(star.getColumnCount()).thenReturn(2);
        RolapStar.Column c0 = Mockito.mock(RolapStar.Column.class);
        Mockito.when(c0.genericSql()).thenReturn("DIM.KEY");
        Mockito.when(star.getColumn(0)).thenReturn(c0);

        BitKey bitKey = BitKey.Factory.makeBitKey(8);
        bitKey.set(0);
        bitKey.set(5);

        assertThat(BitKeyExplain.explain(star, bitKey))
                .isEqualTo("{DIM.KEY#0, ?#5}");
    }

    @Test
    void headerExplainsColumnsFormsAndRegions() {
        BitKey bitKey = BitKey.Factory.makeBitKey(3);
        bitKey.set(0);
        bitKey.set(1);
        bitKey.set(2);
        SegmentHeader header = new SegmentHeader("Schema",
            new ByteString("c".getBytes()), "Cube", "[Measures].[Sales]",
            List.of(
                new SegmentColumn("store.country", 10,
                    new TreeSet<>(List.of("USA"))),
                new SegmentColumn("time.the_year", 3,
                    new TreeSet<>(List.of(1997, 1998))),
                new SegmentColumn("store.state", 50, null)),
            List.of(), "sales_fact", bitKey,
            List.of(new SegmentRegion(List.of(
                new SegmentColumn("store.country", 10,
                    new TreeSet<>(List.of("Mexico")))))));

        assertThat(BitKeyExplain.explain(header)).isEqualTo(
            "[Measures].[Sales]@sales_fact "
            + "{store.country=USA, time.the_year=in(2 values), store.state=*}"
            + " -box{store.country=Mexico}");
    }

    @Test
    void coordinatesExplainThemselves() {
        Map<String, Comparable> coordinates = new LinkedHashMap<>();
        coordinates.put("store.country", "USA");
        coordinates.put("time.the_year", 1997);
        assertThat(BitKeyExplain.explain(coordinates))
            .isEqualTo("{store.country=USA, time.the_year=1997}");
    }
}
