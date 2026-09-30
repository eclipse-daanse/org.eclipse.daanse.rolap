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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The SPI's default rename succeeds only when the body actually MOVED:
 * reporting a bodiless removal (the body was evicted between get and
 * remove) as success made the manager publish the birth of a header no
 * store holds - a phantom in every same-checksum index.
 */
class SegmentCacheDefaultRenameTest {

    private static SegmentHeader header(String measure) {
        return SegmentTestHeaders.header(measure);
    }

    /** In-memory store exercising the interface's DEFAULT rename. */
    private static class MapCache implements SegmentCache {
        final Map<SegmentHeader, SegmentBody> map = new HashMap<>();

        @Override
        public SegmentBody get(SegmentHeader h) {
            return map.get(h);
        }

        @Override
        public boolean put(SegmentHeader h, SegmentBody b) {
            map.put(h, b);
            return true;
        }

        @Override
        public boolean remove(SegmentHeader h) {
            return map.remove(h) != null;
        }

        @Override
        public List<SegmentHeader> getSegmentHeaders() {
            return List.copyOf(map.keySet());
        }

        boolean contains(SegmentHeader h) {
            return map.containsKey(h);
        }

        @Override
        public void addListener(SegmentCacheListener l) {
        }

        @Override
        public void removeListener(SegmentCacheListener l) {
        }

        @Override
        public void tearDown() {
        }
    }

    @Test
    void renameMovesTheBody() {
        MapCache cache = new MapCache();
        SegmentHeader oldHeader = header("m-old");
        SegmentHeader newHeader = header("m-new");
        cache.put(oldHeader, Mockito.mock(SegmentBody.class));

        assertThat(cache.rename(oldHeader, newHeader)).isTrue();
        assertThat(cache.contains(newHeader)).isTrue();
        assertThat(cache.contains(oldHeader)).isFalse();
    }

    /**
     * Red before: a header whose body vanished (soft-reference store)
     * reported the rename as success although nothing was written under
     * the new key.
     */
    @Test
    void bodilessRemovalIsNotASuccessfulRename() {
        MapCache cache = new MapCache() {
            @Override
            public SegmentBody get(SegmentHeader h) {
                return null; // body evicted between put and rename
            }
        };
        SegmentHeader oldHeader = header("m-old");
        cache.map.put(oldHeader, Mockito.mock(SegmentBody.class));

        assertThat(cache.rename(oldHeader, header("m-new"))).isFalse();
        assertThat(cache.map).doesNotContainKey(oldHeader);
    }
}
