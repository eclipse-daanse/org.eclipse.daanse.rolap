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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class BoundedCacheTest {

    @Test
    void statsCountHitsAndMisses() {
        BoundedCache<String, String> cache = BoundedCache.ofEntries(10);
        cache.put("a", "1");
        cache.get("a");
        cache.get("missing");
        assertThat(cache.stats().hitCount()).isEqualTo(1);
        assertThat(cache.stats().missCount()).isEqualTo(1);
    }

    @Test
    void mapSemantics() {
        BoundedCache<String, String> cache = BoundedCache.ofEntries(10);
        assertThat(cache.put("a", "1")).isNull();
        assertThat(cache.put("a", "2")).isEqualTo("1");
        assertThat(cache.get("a")).isEqualTo("2");
        assertThat(cache.putIfAbsent("a", "3")).isEqualTo("2");
        assertThat(cache.putIfAbsent("b", "1")).isNull();
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.remove("a")).isEqualTo("2");
        cache.clear();
        assertThat(cache.size()).isZero();
    }

    @Test
    void nullValueRejected() {
        BoundedCache<String, String> cache = BoundedCache.ofEntries(10);
        assertThatThrownBy(() -> cache.put("k", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> cache.putIfAbsent("k", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void mergeGrowsTheExistingEntryInPlace() {
        BoundedCache<String, List<String>> cache = BoundedCache.ofEntries(10);
        cache.merge("k", new ArrayList<>(List.of("a")), (existing, incoming) -> {
            existing.addAll(incoming);
            return existing;
        });
        cache.merge("k", new ArrayList<>(List.of("b")), (existing, incoming) -> {
            existing.addAll(incoming);
            return existing;
        });
        assertThat(cache.get("k")).containsExactly("a", "b");
    }

    /**
     * replace is the safe rewrite channel for Task iterations: it swaps
     * only while the entry still holds the expected value and never
     * inserts on a missing key - a lost race (concurrent writer, or
     * eviction) leaves the cache untouched instead of clobbering or
     * resurrecting.
     */
    @Test
    void replaceSwapsOnlyTheExpectedValue() {
        BoundedCache<String, List<String>> cache = BoundedCache.ofEntries(10);
        cache.put("k", List.of("a"));

        assertThat(cache.replace("k", List.of("a"), List.of("b"))).isTrue();
        assertThat(cache.replace("k", List.of("a"), List.of("c")))
                .as("a stale expected value must lose").isFalse();
        assertThat(cache.get("k")).containsExactly("b");

        assertThat(cache.replace("missing", List.of("x"), List.of("y")))
                .as("replace never inserts").isFalse();
        assertThat(cache.get("missing")).isNull();
    }

    @Test
    void weightCapEvicts() {
        // each value weighs its size; cap 10 cannot hold both size-6 lists
        BoundedCache<String, List<Integer>> cache =
                BoundedCache.weighted(10, (k, v) -> v.size());
        cache.put("a", List.of(1, 2, 3, 4, 5, 6));
        cache.put("b", List.of(1, 2, 3, 4, 5, 6));
        cache.cleanUp();
        assertThat(cache.size()).isLessThanOrEqualTo(1);
    }

    @Test
    void executeIteratorSupportsRemove() {
        BoundedCache<String, String> cache = BoundedCache.ofEntries(10);
        cache.put("keep", "1");
        cache.put("drop", "2");
        cache.execute(iterator -> {
            while (iterator.hasNext()) {
                if (iterator.next().getKey().equals("drop")) {
                    iterator.remove();
                }
            }
        });
        assertThat(cache.get("drop")).isNull();
        assertThat(cache.get("keep")).isEqualTo("1");
    }

    @Test
    void oversizedSingleValueIsNotCachedForever() {
        BoundedCache<String, List<Integer>> cache =
                BoundedCache.weighted(3, (k, v) -> v.size());
        cache.put("huge", List.of(1, 2, 3, 4, 5));
        cache.cleanUp();
        assertThat(cache.size()).isZero();
    }

    @Test
    void mergeReweighsGrownValues() {
        BoundedCache<String, List<Integer>> cache =
                BoundedCache.weighted(10, (k, v) -> v.size());
        cache.put("k", new java.util.ArrayList<>(List.of(0)));
        // in-place growth past the cap is re-weighed and evicted
        cache.merge("k", new java.util.ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
                (existing, incoming) -> {
                    existing.addAll(incoming);
                    return existing;
                });
        cache.cleanUp();
        assertThat(cache.get("k")).as("a value grown past the cap must be evicted").isNull();
    }
}
