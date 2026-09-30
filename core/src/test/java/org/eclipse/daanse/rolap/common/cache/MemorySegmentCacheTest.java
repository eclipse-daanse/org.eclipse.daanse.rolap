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
package org.eclipse.daanse.rolap.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.util.ByteString;
import org.junit.jupiter.api.Test;

class MemorySegmentCacheTest {

    private static SegmentHeader header(String measure) {
        return new SegmentHeader("Schema", new ByteString("cs".getBytes()), "Cube", measure, List.of(), List.of(),
                "FACT", BitKey.Factory.makeBitKey(2), List.of());
    }

    @Test
    void reclaimedBodiesLeaveNoZombieHeaders() throws Exception {
        MemorySegmentCache cache = new MemorySegmentCache();
        SegmentHeader header = header("m1");
        cache.put(header, mock(SegmentBody.class));
        assertThat(cache.getSegmentHeaders()).containsExactly(header);

        // force what the garbage collector does under memory pressure
        Field mapField = MemorySegmentCache.class.getDeclaredField("map");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<SegmentHeader, SoftReference<SegmentBody>> map =
                (Map<SegmentHeader, SoftReference<SegmentBody>>) mapField.get(cache);
        SoftReference<SegmentBody> ref = map.get(header);
        ref.clear();
        ref.enqueue();

        assertThat(cache.getSegmentHeaders()).isEmpty();
        assertThat(cache.contains(header)).isFalse();
        assertThat(cache.get(header)).isNull();
    }

    @Test
    void replacedEntrySurvivesTheDrainOfItsPredecessor() throws Exception {
        MemorySegmentCache cache = new MemorySegmentCache();
        SegmentHeader header = header("m1");
        cache.put(header, mock(SegmentBody.class));

        Field mapField = MemorySegmentCache.class.getDeclaredField("map");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<SegmentHeader, SoftReference<SegmentBody>> map =
                (Map<SegmentHeader, SoftReference<SegmentBody>>) mapField.get(cache);
        SoftReference<SegmentBody> first = map.get(header);

        SegmentBody replacement = mock(SegmentBody.class);
        cache.put(header, replacement);
        first.clear();
        first.enqueue();

        assertThat(cache.get(header)).isSameAs(replacement);
    }

    @Test
    void renameMovesTheBodyToTheNewHeader() {
        MemorySegmentCache cache = new MemorySegmentCache();
        SegmentHeader oldHeader = header("m1");
        SegmentHeader newHeader = header("m2");
        SegmentBody body = mock(SegmentBody.class);
        cache.put(oldHeader, body);

        assertThat(cache.rename(oldHeader, newHeader)).isTrue();
        assertThat(cache.get(newHeader)).isSameAs(body);
        assertThat(cache.get(oldHeader)).isNull();
    }

    @Test
    void renameOfAnUnknownHeaderChangesNothing() {
        MemorySegmentCache cache = new MemorySegmentCache();
        SegmentHeader present = header("m1");
        SegmentBody body = mock(SegmentBody.class);
        cache.put(present, body);

        assertThat(cache.rename(header("missing"), header("m2"))).isFalse();
        assertThat(cache.get(present)).isSameAs(body);
        assertThat(cache.getSegmentHeaders()).containsExactly(present);
    }

    @Test
    void renameWithAReclaimedBodyKeepsTheOldMapping() throws Exception {
        MemorySegmentCache cache = new MemorySegmentCache();
        SegmentHeader oldHeader = header("m1");
        cache.put(oldHeader, mock(SegmentBody.class));

        Field mapField = MemorySegmentCache.class.getDeclaredField("map");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<SegmentHeader, SoftReference<SegmentBody>> map =
                (Map<SegmentHeader, SoftReference<SegmentBody>>) mapField.get(cache);
        map.get(oldHeader).clear();

        assertThat(cache.rename(oldHeader, header("m2"))).isFalse();
        // the cleared entry is still registered; the drain removes it, not rename
        assertThat(map).containsKey(oldHeader);
    }
}
