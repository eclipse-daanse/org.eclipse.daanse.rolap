/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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
 *   Stefan Bischof (bipolis.org) - initial
 */


package org.eclipse.daanse.rolap.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Test cases for {@link ConcatenableList}.
 *
 * <p>Currently oriented at testing fixes for a couple of known bugs;
 * these should not be considered to be otherwise comprehensive.</p>
 */
class ConcatenableListTest{

    // Just some placeholder constants for expected values in backing lists
    private final String NON_EMPTY_MARKER = "Not empty",
        VALUE_1 = "A",
        VALUE_2 = "B",
        VALUE_3 = "C",
        VALUE_4 = "D",
        VALUE_5 = "E",
        VALUE_6 = "F";

    /**
     * Tests that basic iteration over multiple backing lists works properly,
     * whether or not there are intervening empty lists.
     */
    @Test
    void basicIteration() {
        List<String> testList = new ConcatenableList<>();
        testList.addAll(Arrays.asList(VALUE_1));
        testList.addAll(new ArrayList<String>());
        testList.addAll(new ArrayList<String>());
        testList.addAll(Arrays.asList(VALUE_2, VALUE_3));
        testList.addAll(Arrays.asList(VALUE_4));
        testList.addAll(new ArrayList<String>());
        testList.addAll(new ArrayList<String>());
        testList.addAll(new ArrayList<String>());
        testList.addAll(Arrays.asList(VALUE_5, VALUE_6));

        Iterator<String> iterator = testList.iterator();
        assertThat(iterator.hasNext()).as("iterator.hasNext() should be true").isTrue();
        assertThat(iterator.next()).as("first value should be A").isEqualTo(VALUE_1);
        assertThat(iterator.hasNext()).as("iterator.hasNext() should be true").isTrue();
        assertThat(iterator.next()).as("first value should be B").isEqualTo(VALUE_2);
        assertThat(iterator.hasNext()).as("iterator.hasNext() should be true").isTrue();
        assertThat(iterator.next()).as("first value should be C").isEqualTo(VALUE_3);
        assertThat(iterator.hasNext()).as("iterator.hasNext() should be true").isTrue();
        assertThat(iterator.next()).as("first value should be D").isEqualTo(VALUE_4);
        assertThat(iterator.hasNext()).as("iterator.hasNext() should be true").isTrue();
        assertThat(iterator.next()).as("first value should be E").isEqualTo(VALUE_5);
        assertThat(iterator.hasNext()).as("iterator.hasNext() should be true").isTrue();
        assertThat(iterator.next()).as("first value should be F").isEqualTo(VALUE_6);
        assertThat(iterator.hasNext()).as("iterator.hasNext() should be false, since there are no more values").isFalse();
    }

    /**
     * Tests that it is possible to iterate through a series of next() calls
     * without first calling hasNext(). (Necessary because an earlier
     * implementation of ConcatenableList would throw a null pointer exception
     * if hasNext() wasn't called first.)
     */
    @Test
    void iteratorNextWithoutHasNext() {
        List<String> testList = new ConcatenableList<>();
        testList.addAll(Arrays.asList(VALUE_1));
        testList.addAll(new ArrayList<String>());
        testList.addAll(new ArrayList<String>());
        testList.addAll(Arrays.asList(VALUE_2, VALUE_3));
        testList.addAll(Arrays.asList(VALUE_4));
        testList.addAll(new ArrayList<String>());
        testList.addAll(new ArrayList<String>());
        testList.addAll(new ArrayList<String>());
        testList.addAll(Arrays.asList(VALUE_5, VALUE_6));

        Iterator<String> iterator = testList.iterator();
        assertThat(iterator.next()).as("first value should be A").isEqualTo(VALUE_1);
        assertThat(iterator.next()).as("first value should be B").isEqualTo(VALUE_2);
        assertThat(iterator.next()).as("first value should be C").isEqualTo(VALUE_3);
        assertThat(iterator.next()).as("first value should be D").isEqualTo(VALUE_4);
        assertThat(iterator.next()).as("first value should be E").isEqualTo(VALUE_5);
        assertThat(iterator.next()).as("first value should be F").isEqualTo(VALUE_6);
        assertThat(iterator.hasNext()).as("iterator.hasNext() should be false, since there are no more values").isFalse();
    }

    /**
     * Tests that if multiple empty lists are added, followed by a non-empty
     * list, iteration behaves correctly and get(0) does not fail. (An earlier
     * implementation of ConcatenableList would incorrectly throw an
     * IndexOutOfBoundsException when get(0) was called on an instance where the
     * backing lists included two consecutive empty lists.)
     */
    @Test
    void getZeroWithMultipleEmptyLists() {
        List<String> testList = new ConcatenableList<>();

        testList.addAll(new ArrayList<String>());
        testList.addAll(new ArrayList<String>());
        testList.addAll(Arrays.asList(NON_EMPTY_MARKER));

        assertThat(testList.isEmpty()).as("ConcatenableList testList should not be empty").isFalse();

        assertThat(testList.get(0)).as("testList.get(0) should return NON_EMPTY_MARKER").isEqualTo(NON_EMPTY_MARKER);
    }
}
