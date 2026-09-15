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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One contract, both implementations: the member-cache family switches
 * between SoftValueCache and BoundedCache per map, and the shared
 * semantics (merge-to-null removes, replace loses races cleanly,
 * remove answers the old value) were never pinned across both.
 */
class SimpleCacheContractTest {

    static Stream<SimpleCache<String, String>> caches() {
        return Stream.of(
            new SoftValueCache<>(),
            BoundedCache.ofEntries(100));
    }

    @ParameterizedTest
    @MethodSource("caches")
    void putGetRemoveRoundTrip(SimpleCache<String, String> cache) {
        assertThat(cache.put("k", "v1")).isNull();
        assertThat(cache.get("k")).isEqualTo("v1");
        assertThat(cache.put("k", "v2")).isEqualTo("v1");
        assertThat(cache.remove("k")).isEqualTo("v2");
        assertThat(cache.get("k")).isNull();
    }

    @ParameterizedTest
    @MethodSource("caches")
    void putIfAbsentKeepsTheFirstValue(SimpleCache<String, String> cache) {
        assertThat(cache.putIfAbsent("k", "first")).isNull();
        assertThat(cache.putIfAbsent("k", "second")).isEqualTo("first");
        assertThat(cache.get("k")).isEqualTo("first");
    }

    @ParameterizedTest
    @MethodSource("caches")
    void mergeToNullRemovesTheEntry(SimpleCache<String, String> cache) {
        cache.put("k", "old");
        cache.merge("k", "new", (a, b) -> null);
        assertThat(cache.get("k")).isNull();
    }

    @ParameterizedTest
    @MethodSource("caches")
    void replaceLosesRacesCleanly(SimpleCache<String, String> cache) {
        cache.put("k", "current");
        assertThat(cache.replace("k", "stale", "won't land")).isFalse();
        assertThat(cache.get("k")).isEqualTo("current");
        assertThat(cache.replace("k", "current", "landed")).isTrue();
        assertThat(cache.get("k")).isEqualTo("landed");
    }

    @ParameterizedTest
    @MethodSource("caches")
    void executeVisitsEveryLiveEntry(SimpleCache<String, String> cache) {
        cache.put("a", "1");
        cache.put("b", "2");
        List<String> seen = new ArrayList<>();
        cache.execute(iterator -> iterator.forEachRemaining(
            entry -> seen.add(entry.getKey())));
        assertThat(seen).containsExactlyInAnyOrder("a", "b");
    }
}
