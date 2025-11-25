/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2019 Hitachi Vantara..  All rights reserved.
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

import java.util.AbstractList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link PartiallyOrderedSet}.
 */
class PartiallyOrderedSetTest {
    private static final boolean debug = false;
    private final int SCALE = 250; // 100, 1000, 3000 are also reasonable values
    final long seed = new Random().nextLong();
    final Random random = new Random(seed);

    static final PartiallyOrderedSet.Ordering<String> stringSubsetOrdering =
        new PartiallyOrderedSet.Ordering<>() {
            @Override
			public boolean lessThan(String e1, String e2) {
                // e1 < e2 if every char in e1 is also in e2
                for (int i = 0; i < e1.length(); i++) {
                    if (e2.indexOf(e1.charAt(i)) < 0) {
                        return false;
                    }
                }
                return true;
            }
        };

    // Integers, ordered by division. Top is 1, its children are primes,
    // etc.
    static final PartiallyOrderedSet.Ordering<Integer> isDivisor =
        new PartiallyOrderedSet.Ordering<>() {
            @Override
			public boolean lessThan(Integer e1, Integer e2) {
                return e2 % e1 == 0;
            }
        };

    // Bottom is 1, parents are primes, etc.
    static final PartiallyOrderedSet.Ordering<Integer> isDivisorInverse =
        new PartiallyOrderedSet.Ordering<>() {
            @Override
			public boolean lessThan(Integer e1, Integer e2) {
                return e1 % e2 == 0;
            }
        };

    static final PartiallyOrderedSet.Ordering<Integer> isDivisorWithNulls =
      new PartiallyOrderedSet.Ordering<>() {
          @Override
		public boolean lessThan(Integer e1, Integer e2) {
              if (e1 == null || e2 == null) {
                  return true;
              }
              return e2 % e1 == 0;
          }
      };

    // Ordered by bit inclusion. E.g. the children of 14 (1110) are
    // 12 (1100), 10 (1010) and 6 (0110).
    static final PartiallyOrderedSet.Ordering<Integer> isBitSubset =
        new PartiallyOrderedSet.Ordering<>() {
            @Override
			public boolean lessThan(Integer e1, Integer e2) {
                return (e2 & e1) == e2;
            }
        };

    // Ordered by bit inclusion. E.g. the children of 14 (1110) are
    // 12 (1100), 10 (1010) and 6 (0110).
    static final PartiallyOrderedSet.Ordering<Integer> isBitSuperset =
        new PartiallyOrderedSet.Ordering<>() {
            @Override
			public boolean lessThan(Integer e1, Integer e2) {
                return (e2 & e1) == e1;
            }
        };

    @Test
    void poset() {
        String empty = "''";
        String abcd = "'abcd'";
        PartiallyOrderedSet<String> poset =
            new PartiallyOrderedSet<>(stringSubsetOrdering);
        assertThat(poset.size()).isEqualTo(0);

        final StringBuilder buf = new StringBuilder();
        poset.out(buf);
        assertThat(buf.toString()).isEqualTo("PartiallyOrderedSet size: 0 elements: {\n"
            + "}");

        poset.add("a");
        printValidate(poset);
        poset.add("b");
        printValidate(poset);

        poset.clear();
        assertThat(poset.size()).isEqualTo(0);
        poset.add(empty);
        printValidate(poset);
        poset.add(abcd);
        printValidate(poset);
        assertThat(poset.size()).isEqualTo(2);
        assertThat(poset.getNonChildren().toString()).isEqualTo("['abcd']");
        assertThat(poset.getNonParents().toString()).isEqualTo("['']");

        final String ab = "'ab'";
        poset.add(ab);
        printValidate(poset);
        assertThat(poset.size()).isEqualTo(3);
        assertThat(poset.getChildren(empty).toString()).isEqualTo("[]");
        assertThat(poset.getParents(empty).toString()).isEqualTo("['ab']");
        assertThat(poset.getChildren(abcd).toString()).isEqualTo("['ab']");
        assertThat(poset.getParents(abcd).toString()).isEqualTo("[]");
        assertThat(poset.getChildren(ab).toString()).isEqualTo("['']");
        assertThat(poset.getParents(ab).toString()).isEqualTo("['abcd']");

        // "bcd" is child of "abcd" and parent of ""
        final String bcd = "'bcd'";
        poset.add(bcd);
        printValidate(poset);
        assertThat(poset.isValid(false)).isTrue();
        assertThat(poset.getChildren(bcd).toString()).isEqualTo("['']");
        assertThat(poset.getParents(bcd).toString()).isEqualTo("['abcd']");
        assertThat(poset.getChildren(abcd).toString()).isEqualTo("['ab', 'bcd']");

        buf.setLength(0);
        poset.out(buf);
        assertThat(buf.toString()).isEqualTo("PartiallyOrderedSet size: 4 elements: {\n"
            + "  'abcd' parents: [] children: ['ab', 'bcd']\n"
            + "  'ab' parents: ['abcd'] children: ['']\n"
            + "  'bcd' parents: ['abcd'] children: ['']\n"
            + "  '' parents: ['ab', 'bcd'] children: []\n"
            + "}");

        final String b = "'b'";

        // ancestors of an element not in the set
        assertEqualsList("['ab', 'abcd', 'bcd']", poset.getAncestors(b));

        poset.add(b);
        printValidate(poset);
        assertThat(poset.getNonChildren().toString()).isEqualTo("['abcd']");
        assertThat(poset.getNonParents().toString()).isEqualTo("['']");
        assertThat(poset.getChildren(b).toString()).isEqualTo("['']");
        assertEqualsList("['ab', 'bcd']", poset.getParents(b));
        assertThat(poset.getChildren(b).toString()).isEqualTo("['']");
        assertThat(poset.getChildren(abcd).toString()).isEqualTo("['ab', 'bcd']");
        assertThat(poset.getChildren(bcd).toString()).isEqualTo("['b']");
        assertThat(poset.getChildren(ab).toString()).isEqualTo("['b']");
        assertEqualsList("['ab', 'abcd', 'bcd']", poset.getAncestors(b));

        // descendants and ancestors of an element with no descendants
        assertThat(poset.getDescendants(empty).toString()).isEqualTo("[]");
        assertEqualsList(
            "['ab', 'abcd', 'b', 'bcd']",
            poset.getAncestors(empty));

        // some more ancestors of missing elements
        assertEqualsList("['abcd']", poset.getAncestors("'ac'"));
        assertEqualsList("[]", poset.getAncestors("'z'"));
        assertEqualsList("['ab', 'abcd']", poset.getAncestors("'a'"));
    }
    @Test
    void posetTricky() {
        PartiallyOrderedSet<String> poset =
            new PartiallyOrderedSet<>(stringSubsetOrdering);

        // A tricky little poset with 4 elements:
        // {a <= ab and ac, b < ab, ab, ac}
        poset.clear();
        poset.add("'a'");
        printValidate(poset);
        poset.add("'b'");
        printValidate(poset);
        poset.add("'ac'");
        printValidate(poset);
        poset.add("'ab'");
        printValidate(poset);
    }
    @Test
    void posetBits() {
        final PartiallyOrderedSet<Integer> poset =
            new PartiallyOrderedSet<>(isBitSuperset);
        poset.add(2112); // {6, 11} i.e. 64 + 2048
        poset.add(2240); // {6, 7, 11} i.e. 64 + 128 + 2048
        poset.add(2496); // {6, 7, 8, 11} i.e. 64 + 128 + 256 + 2048
        printValidate(poset);
        poset.remove(2240);
        printValidate(poset);
        poset.add(2240); // {6, 7, 11} i.e. 64 + 128 + 2048
        printValidate(poset);
    }

    @Test
    void posetBitsRemoveParent() {
        final PartiallyOrderedSet<Integer> poset =
            new PartiallyOrderedSet<>(isBitSuperset);
        poset.add(66); // {bit 2, bit 6}
        poset.add(68); // {bit 3, bit 6}
        poset.add(72); // {bit 4, bit 6}
        poset.add(64); // {bit 6}
        printValidate(poset);
        poset.remove(64); // {bit 6}
        printValidate(poset);
    }

    @Test
    void daanse2628() {
        PartiallyOrderedSet<Integer> integers =
            new PartiallyOrderedSet<>(isDivisorWithNulls,
              range(1, 1000));
        // Null elements can't be added to the poset.
        assertThat(integers.add(null)).isFalse();
        // Ancestors list cannot have null elements.
        for (int i = 1; i < 1000; i++) {
            assertThat(integers.getAncestors(i).contains(null)).as("Ancestor list of " + i + " has null elements.").isFalse();
        }
    }

    @Test
    void divisorPoset() {
        PartiallyOrderedSet<Integer> integers =
            new PartiallyOrderedSet<>(isDivisor, range(1, 1000));
        assertThat(new TreeSet<>(integers.getDescendants(120)).toString()).isEqualTo("[1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 24, 30, 40, 60]");
        assertThat(new TreeSet<>(integers.getAncestors(120)).toString()).isEqualTo("[240, 360, 480, 600, 720, 840, 960]");
        assertThat(integers.getDescendants(1).isEmpty()).isTrue();
        assertThat(integers.getAncestors(1).size()).isEqualTo(998);
        assertThat(integers.isValid(true)).isTrue();
    }

    @Test
    void divisorSeries() {
        checkPoset(isDivisor, debug, range(1, SCALE * 3), false);
    }

    @Test
    void divisorRandom() {
        boolean ok = false;
        try {
            checkPoset(
                isDivisor, debug, random(random, SCALE, SCALE * 3), false);
            ok = true;
        } finally {
            if (!ok) {
                System.out.println("Random seed: " + seed);
            }
        }
    }

    @Test
    void divisorRandomWithRemoval() {
        boolean ok = false;
        try {
            checkPoset(
                isDivisor, debug, random(random, SCALE, SCALE * 3), true);
            ok = true;
        } finally {
            if (!ok) {
                System.out.println("Random seed: " + seed);
            }
        }
    }

    @Test
    void divisorInverseSeries() {
        checkPoset(isDivisorInverse, debug, range(1, SCALE * 3), false);
    }

    @Test
    void divisorInverseRandom() {
        boolean ok = false;
        try {
            checkPoset(
                isDivisorInverse, debug, random(random, SCALE, SCALE * 3),
                false);
            ok = true;
        } finally {
            if (!ok) {
                System.out.println("Random seed: " + seed);
            }
        }
    }

    @Test
    void divisorInverseRandomWithRemoval() {
        boolean ok = false;
        try {
            checkPoset(
                isDivisorInverse, debug, random(random, SCALE, SCALE * 3),
                true);
            ok = true;
        } finally {
            if (!ok) {
                System.out.println("Random seed: " + seed);
            }
        }
    }

    @Test
    void subsetSeries() {
        checkPoset(isBitSubset, debug, range(1, SCALE / 2), false);
    }

    @Test
    void subsetRandom() {
        boolean ok = false;
        try {
            checkPoset(
                isBitSubset, debug, random(random, SCALE / 4, SCALE), false);
            ok = true;
        } finally {
            if (!ok) {
                System.out.println("Random seed: " + seed);
            }
        }
    }

    private <E> void printValidate(PartiallyOrderedSet<E> poset) {
        if (debug) {
            dump(poset);
        }
        assertThat(poset.isValid(debug)).isTrue();
    }

    public void checkPoset(
        PartiallyOrderedSet.Ordering<Integer> ordering,
        boolean debug,
        Iterable<Integer> generator,
        boolean remove)
    {
        final PartiallyOrderedSet<Integer> poset =
            new PartiallyOrderedSet<>(ordering);
        int n = 0;
        int z = 0;
        if (debug) {
            dump(poset);
        }
        for (int i : generator) {
            if (remove && z++ % 2 == 0) {
                if (debug) {
                    System.out.println("remove " + i);
                }
                poset.remove(i);
                if (debug) {
                    dump(poset);
                }
                continue;
            }
            if (debug) {
                System.out.println("add " + i);
            }
            poset.add(i);
            if (debug) {
                dump(poset);
            }
            assertThat(poset.size()).isEqualTo(++n);
            if (i < 100) {
                if (!poset.isValid(false)) {
                    dump(poset);
                }
                assertThat(poset.isValid(true)).isTrue();
            }
        }
        assertThat(poset.isValid(true)).isTrue();

        final StringBuilder buf = new StringBuilder();
        poset.out(buf);
        assertThat(buf.length() > 0).isTrue();
    }

    private <E> void dump(PartiallyOrderedSet<E> poset) {
        final StringBuilder buf = new StringBuilder();
        poset.out(buf);
        System.out.println(buf);
    }

    private static Collection<Integer> range(
        final int start, final int end)
    {
        return new AbstractList<>() {
            @Override
            public Integer get(int index) {
                return start + index;
            }

            @Override
            public int size() {
                return end - start;
            }
        };
    }

    private static Iterable<Integer> random(
        Random random, final int size, final int max)
    {
        final Set<Integer> set = new LinkedHashSet<>();
        while (set.size() < size) {
            set.add(random.nextInt(max) + 1);
        }
        return set;
    }

    private static void assertEqualsList(String expected, List<String> ss) {
        assertThat(new TreeSet<>(ss).toString()).isEqualTo(expected);
    }
}
