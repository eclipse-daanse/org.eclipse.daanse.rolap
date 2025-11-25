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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
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
    void testEmptyList() throws Exception {
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
    void testGetter() throws Exception {
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
            assertEquals(Integer.valueOf(i), empty.get(i));
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
        assertFalse(identical.isEmpty());
        assertNotNull(identical.get(0));
        int k = 0;
        for (final Integer i : identical) {
            assertEquals(i, identical.get(k));
            k++;
        }
    }
}
