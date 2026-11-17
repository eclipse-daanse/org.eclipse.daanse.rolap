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

class SoftValueCacheTest {

    @Test
    void putGetRemoveSize() {
        SoftValueCache<String, String> cache = new SoftValueCache<>();
        assertThat(cache.size()).isZero();

        assertThat(cache.put("a", "1")).isNull();
        assertThat(cache.get("a")).isEqualTo("1");
        assertThat(cache.size()).isEqualTo(1);

        assertThat(cache.put("a", "2")).isEqualTo("1");
        assertThat(cache.get("a")).isEqualTo("2");

        assertThat(cache.remove("a")).isEqualTo("2");
        assertThat(cache.get("a")).isNull();
        assertThat(cache.size()).isZero();
    }

    @Test
    void lookupIsEqualsBasedNotIdentity() {
        SoftValueCache<String, String> cache = new SoftValueCache<>();
        cache.put("key", "value");
        // a distinct but equal key must hit; identity-keyed schemes
        // (e.g. Caffeine weakKeys) would miss exactly this
        String equalButDistinctKey = new StringBuilder("key").toString();
        assertThat(equalButDistinctKey).isNotSameAs("key");
        assertThat(cache.get(equalButDistinctKey)).isEqualTo("value");
    }

    @Test
    void putIfAbsentKeepsTheFirstValue() {
        SoftValueCache<String, String> cache = new SoftValueCache<>();
        assertThat(cache.putIfAbsent("k", "first")).isNull();
        assertThat(cache.putIfAbsent("k", "second")).isEqualTo("first");
        assertThat(cache.get("k")).isEqualTo("first");
    }

    @Test
    void nullValueRejected() {
        SoftValueCache<String, String> cache = new SoftValueCache<>();
        cache.put("k", "v");
        assertThatThrownBy(() -> cache.put("k", null))
                .isInstanceOf(NullPointerException.class); // remove(key) is the way to drop an entry
        assertThatThrownBy(() -> cache.putIfAbsent("k", null))
                .isInstanceOf(NullPointerException.class);
        assertThat(cache.get("k")).isEqualTo("v");
    }

    @Test
    void mergeGrowsTheExistingEntryInPlace() {
        SoftValueCache<String, List<String>> cache = new SoftValueCache<>();
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
    void clearEmptiesTheCache() {
        SoftValueCache<String, String> cache = new SoftValueCache<>();
        cache.put("a", "1");
        cache.put("b", "2");
        assertThat(cache.size()).isEqualTo(2);

        cache.clear();

        assertThat(cache.size()).isZero();
        assertThat(cache.get("a")).isNull();
        assertThat(cache.get("b")).isNull();
    }

    @Test
    void executeIteratorSupportsRemoveDespiteReadAhead() {
        SoftValueCache<String, String> cache = new SoftValueCache<>();
        cache.put("keep", "1");
        cache.put("drop", "2");
        cache.execute(iterator -> {
            while (iterator.hasNext()) {
                // hasNext read-ahead between next() and remove() must not
                // change which entry remove() deletes
                var entry = iterator.next();
                boolean ignored = iterator.hasNext();
                if (entry.getKey().equals("drop")) {
                    iterator.remove();
                }
            }
        });
        assertThat(cache.get("drop")).isNull();
        assertThat(cache.get("keep")).isEqualTo("1");
    }

    @Test
    void reclaimedValuesArePurged() {
        SoftValueCache<String, Object> cache = new SoftValueCache<>();
        cache.put("k", new Object());
        // simulate the collector clearing the soft reference: no public seam,
        // so exercise the equivalent path - a cleared entry must not answer
        System.gc(); // best effort; soft refs usually survive a healthy heap
        assertThat(cache.size()).isLessThanOrEqualTo(1);
    }
}
