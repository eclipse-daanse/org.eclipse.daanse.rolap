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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

/** Drop-in parity with {@link BoundedCacheTest}'s guarantees, plus the weight account. */
class OwnBoundedCacheTest {

    @Test
    void mapSemantics() {
        OwnBoundedCache<String, String> cache = OwnBoundedCache.ofEntries(10);
        assertThat(cache.put("a", "1")).isNull();
        assertThat(cache.put("a", "2")).isEqualTo("1");
        assertThat(cache.get("a")).isEqualTo("2");
        assertThat(cache.putIfAbsent("a", "3")).isEqualTo("2");
        assertThat(cache.putIfAbsent("b", "1")).isNull();
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.remove("a")).isEqualTo("2");
        cache.clear();
        assertThat(cache.size()).isZero();
        assertThat(cache.weightUsed()).isZero();
    }

    @Test
    void nullValueRejected() {
        OwnBoundedCache<String, String> cache = OwnBoundedCache.ofEntries(10);
        assertThatThrownBy(() -> cache.put("k", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> cache.putIfAbsent("k", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void mergeGrowsTheExistingEntryInPlace() {
        OwnBoundedCache<String, List<String>> cache = OwnBoundedCache.ofEntries(10);
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

    @Test
    void weightCapEvicts() {
        OwnBoundedCache<String, List<Integer>> cache =
                OwnBoundedCache.weighted(10, (k, v) -> v.size());
        cache.put("a", List.of(1, 2, 3, 4, 5, 6));
        cache.put("b", List.of(1, 2, 3, 4, 5, 6));
        assertThat(cache.size()).isLessThanOrEqualTo(1);
        assertThat(cache.weightUsed()).isLessThanOrEqualTo(10);
    }

    @Test
    void executeIteratorSupportsRemoveDespiteReadAhead() {
        OwnBoundedCache<String, String> cache = OwnBoundedCache.ofEntries(10);
        cache.put("keep", "1");
        cache.put("drop", "2");
        cache.execute(iterator -> {
            while (iterator.hasNext()) {
                var entry = iterator.next();
                boolean ignored = iterator.hasNext();
                if (entry.getKey().equals("drop")) {
                    iterator.remove();
                }
            }
        });
        assertThat(cache.get("drop")).isNull();
        assertThat(cache.get("keep")).isEqualTo("1");
        assertThat(cache.weightUsed()).isEqualTo(1);
    }

    @Test
    void oversizedSingleValueIsNotCached() {
        OwnBoundedCache<String, List<Integer>> cache =
                OwnBoundedCache.weighted(3, (k, v) -> v.size());
        cache.put("huge", List.of(1, 2, 3, 4, 5));
        assertThat(cache.size()).isZero();
        assertThat(cache.weightUsed()).isZero();
    }

    @Test
    void recencySurvivesEviction() {
        OwnBoundedCache<Integer, String> cache = OwnBoundedCache.ofEntries(8);
        for (int i = 0; i < 8; i++) {
            cache.put(i, "v" + i);
        }
        // keep key 0 hot, then overflow: 0 must survive sampled-LRU eviction
        for (int round = 0; round < 4; round++) {
            cache.get(0);
            cache.put(100 + round, "n" + round);
        }
        assertThat(cache.get(0)).isEqualTo("v0");
        assertThat(cache.size()).isLessThanOrEqualTo(8);
    }

    @Test
    void weightAccountStaysConsistentUnderConcurrency() throws Exception {
        OwnBoundedCache<Integer, int[]> cache = OwnBoundedCache.weighted(5_000, (k, v) -> v.length);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            final int seed = t;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                java.util.Random random = new java.util.Random(seed);
                for (int i = 0; i < 50_000; i++) {
                    int key = random.nextInt(2_000);
                    switch (i % 4) {
                    case 0 -> cache.put(key, new int[1 + (key % 16)]);
                    case 1 -> cache.putIfAbsent(key, new int[1 + (key % 16)]);
                    case 2 -> cache.get(key);
                    default -> cache.remove(key);
                    }
                }
            }));
        }
        start.countDown();
        for (CompletableFuture<Void> future : futures) {
            future.get();
        }
        // account equals the sum over the live entries and respects the cap
        long expected = 0;
        var iterator = new long[] {0};
        cache.execute(it -> {
            while (it.hasNext()) {
                iterator[0] += it.next().getValue().length;
            }
        });
        expected = iterator[0];
        assertThat(cache.weightUsed()).isEqualTo(expected);
        assertThat(cache.weightUsed()).isLessThanOrEqualTo(5_000 + 8L * 16);
    }
}
