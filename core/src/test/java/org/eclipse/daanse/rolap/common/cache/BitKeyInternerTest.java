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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.Set;

import org.eclipse.daanse.olap.key.BitKey;
import org.junit.jupiter.api.Test;

class BitKeyInternerTest {

    @Test
    void valueEqualKeysShareOneInstance() {
        BitKeyInterner interner = new BitKeyInterner();
        BitKey first = BitKey.Factory.makeBitKey(200);
        first.set(3);
        first.set(150);
        BitKey second = BitKey.Factory.makeBitKey(200);
        second.set(3);
        second.set(150);

        assertThat((Object) interner.intern(first)).isSameAs(first);
        assertThat((Object) interner.intern(second)).isSameAs(first);
        assertThat(interner.size()).isEqualTo(1);
    }

    @Test
    void variantsCanonicalizeByValue() {
        BitKeyInterner interner = new BitKeyInterner();
        BitKey big = BitKey.Factory.makeBitKey(200);
        big.set(5);
        BitKey small = BitKey.Factory.makeBitKey(30);
        small.set(5);

        BitKey canonical = interner.intern(big);
        // a value-equal key of another variant maps to the same instance
        assertThat((Object) interner.intern(small)).isSameAs(canonical);
    }

    @Test
    void pastTheCapKeysPassThrough() {
        BitKeyInterner interner = new BitKeyInterner();
        for (int i = 0; i < 1024; i++) {
            BitKey key = BitKey.Factory.makeBitKey(2048);
            key.set(i);
            interner.intern(key);
        }
        BitKey overflow = BitKey.Factory.makeBitKey(2048);
        overflow.set(1500);
        assertThat((Object) interner.intern(overflow)).isSameAs(overflow);
        assertThat(interner.size()).isEqualTo(1024);
    }

    @Test
    void concurrentInterningHasOneWinner() throws Exception {
        BitKeyInterner interner = new BitKeyInterner();
        CountDownLatch start = new CountDownLatch(1);
        Set<BitKey> results = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                BitKey key = BitKey.Factory.makeBitKey(200);
                key.set(7);
                key.set(120);
                results.add(interner.intern(key));
            }));
        }
        start.countDown();
        for (CompletableFuture<Void> future : futures) {
            future.get();
        }
        assertThat(results).hasSize(1);
    }
}
