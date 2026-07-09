/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * Unit-test for FilteredIterable
 *
 * @author jlopez, lcanals, Stefan Bischof
 * @since May, 2008
 */
class FilteredIterableTest{
    public FilteredIterableTest() {
    }

    @Test
    void emptyList() throws Exception {
        final List<Integer> base = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            base.add(i);
        }

        final List<Integer> empty =
            new FilteredIterableList<>(
                base,
                new Predicate<Integer>() {
                    @Override
					public boolean test(final Integer i) {
                        return false;
                    }
                });
        for (final Integer x : empty) {
            fail("All elements should have been filtered");
        }
    }

    @Test
    void getter() throws Exception {
        final List<Integer> base = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            base.add(i);
        }

        final List<Integer> empty =
            new FilteredIterableList<>(
                base,
                new Predicate<Integer>() {
                    @Override
					public boolean test(final Integer i) {
                        return i < 2;
                    }
                });
        for (int i = 0; i < 2; i++) {
            assertThat(empty.get(i)).isEqualTo(Integer.valueOf(i));
        }
    }

    @Test
    void test2Elements() throws Exception {
        final List<Integer> base = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            base.add(i);
        }

        final List<Integer> identical =
            new FilteredIterableList<>(
                base,
                new Predicate<Integer>() {
                    @Override
					public boolean test	(final Integer i) {
                        return true;
                    }
                });
        assertThat(identical.isEmpty()).isFalse();
        assertThat(identical.getFirst()).isNotNull();
        int k = 0;
        for (final Integer i : identical) {
            assertThat(identical.get(k)).isEqualTo(i);
            k++;
        }
    }

    /**
     * Regression test for the positional cache key semantics.
     *
     * The cache was previously built with {@code weakKeys()}, which switches
     * Caffeine to identity ({@code ==}) key comparison. Autoboxed {@link Integer}
     * keys are only identical within the -128..127 Integer cache, so a lookup
     * for an index &gt;= 128 never matched a previously stored entry and the
     * list fell back to a full linear scan (re-invoking the filter). This test
     * primes the cache at a high index via the iterator and asserts that a
     * subsequent random access to that index is served from the cache without
     * re-scanning.
     */
    @Test
    void cacheServesHighIndexWithoutRescan() throws Exception {
        final AtomicInteger filterCalls = new AtomicInteger();
        final List<Integer> base = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            base.add(i);
        }
        final FilteredIterableList<Integer> list =
            new FilteredIterableList<>(base, i -> {
                filterCalls.incrementAndGet();
                return true;
            });

        // Prime the positional cache up to index 200 (> 127) via the iterator;
        // the last next() stores index 200 as the most-recent cache entry.
        final ListIterator<Integer> it = list.listIterator(0);
        Integer last = null;
        for (int i = 0; i <= 200; i++) {
            last = it.next();
        }
        assertThat(last).isEqualTo(Integer.valueOf(200));

        final int callsAfterPriming = filterCalls.get();

        // Random access to the just-cached high index must be a cache hit and
        // must NOT trigger another linear scan (no further filter invocations).
        assertThat(list.get(200)).isEqualTo(Integer.valueOf(200));
        assertThat(filterCalls.get())
            .as("get(200) must hit the cache, not re-scan and re-invoke the filter")
            .isEqualTo(callsAfterPriming);
    }
}
