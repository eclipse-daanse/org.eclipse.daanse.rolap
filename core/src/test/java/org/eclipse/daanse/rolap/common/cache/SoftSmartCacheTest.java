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
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SoftSmartCacheTest {

    @Test
    void putGetRemoveSize() {
        SoftSmartCache<String, String> cache = new SoftSmartCache<>();
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
        SoftSmartCache<String, String> cache = new SoftSmartCache<>();
        cache.put("key", "value");
        // A distinct but equal key must hit; this is exactly what Caffeine
        // weakKeys() (identity) would have missed.
        String equalButDistinctKey = new StringBuilder("key").toString();
        assertThat(equalButDistinctKey).isNotSameAs("key");
        assertThat(cache.get(equalButDistinctKey)).isEqualTo("value");
    }

    @Test
    void nullValueRemoves() {
        SoftSmartCache<String, String> cache = new SoftSmartCache<>();
        cache.put("k", "v");
        assertThat(cache.put("k", null)).isEqualTo("v"); // null value behaves as remove
        assertThat(cache.get("k")).isNull();
        assertThat(cache.size()).isZero();
    }

    @Test
    void clearEmptiesTheCache() {
        SoftSmartCache<String, String> cache = new SoftSmartCache<>();
        cache.put("a", "1");
        cache.put("b", "2");
        assertThat(cache.size()).isEqualTo(2);

        cache.clear(); // the same operation the memory-pressure registry invokes

        assertThat(cache.size()).isZero();
        assertThat(cache.get("a")).isNull();
        assertThat(cache.get("b")).isNull();
    }
}
