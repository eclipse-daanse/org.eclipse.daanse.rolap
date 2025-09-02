/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
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


package org.eclipse.daanse.rolap.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.Iterator;

import org.eclipse.daanse.olap.key.BitKey;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link BitKey}.
 *
 * @author Richard Emberson
 */
class BitKeyTest {
    /**
     * Test that negative size throws IllegalArgumentException.
     *
     */
	@Test
    void testBadSize() {
        int size = -1;
        boolean gotException = false;
        BitKey bitKey = null;
        try {
            bitKey = BitKey.Factory.makeBitKey(size);
//            discard(bitKey);
        } catch (IllegalArgumentException e) {
            gotException = true;
        }
        assertTrue((gotException), "BitKey negative size " + size);

        size = -10;
        gotException = false;
        try {
            bitKey = BitKey.Factory.makeBitKey(size);
        } catch (IllegalArgumentException e) {
            gotException = true;
        }
        assertTrue((gotException), "BitKey negative size " + size);
    }

    /**
     * Test that non-negative sizes do not throw IllegalArgumentException
     */
	@Test
    void testGoodSize() {
        int size = 0;
        boolean gotException = false;
        BitKey bitKey = null;
        try {
            bitKey = BitKey.Factory.makeBitKey(size);
//            discard(bitKey);
        } catch (IllegalArgumentException e) {
            gotException = true;
        }
        assertTrue(!gotException, "BitKey size " + size);

        size = 1;
        gotException = false;
        try {
            bitKey = BitKey.Factory.makeBitKey(size);
        } catch (IllegalArgumentException e) {
            gotException = true;
        }
        assertTrue(!gotException, "BitKey size " + size);

        size = 10;
        gotException = false;
        try {
            bitKey = BitKey.Factory.makeBitKey(size);
        } catch (IllegalArgumentException e) {
            gotException = true;
        }
        assertTrue(!gotException, "BitKey size " + size);
    }

    /**
     * Test that the implementation object returned is expected type.
     */
	@Test
    void testSizeTypes() {
        int size = 0;
        BitKey bitKey = BitKey.Factory.makeBitKey(size);
        assertTrue(
            (bitKey.getClass() == BitKey.Small.class),
            "BitKey size " + size + " not BitKey.Small");

        size = 63;
        bitKey = BitKey.Factory.makeBitKey(size);
        assertTrue(
            (bitKey.getClass() == BitKey.Small.class),
            "BitKey size " + size + " not BitKey.Small");

        size = 64;
        bitKey = BitKey.Factory.makeBitKey(size);
        assertTrue(
            (bitKey.getClass() == BitKey.Mid128.class),
            "BitKey size " + size + " not BitKey.Mid128");

        size = 65;
        bitKey = BitKey.Factory.makeBitKey(size);
        assertTrue(
            (bitKey.getClass() == BitKey.Mid128.class),
            "BitKey size " + size + " not BitKey.Mid128");

        size = 127;
        bitKey = BitKey.Factory.makeBitKey(size);
        assertTrue(
            (bitKey.getClass() == BitKey.Mid128.class),
            "BitKey size " + size + " not BitKey.Mid128");

        size = 128;
        bitKey = BitKey.Factory.makeBitKey(size);
        assertTrue(
            (bitKey.getClass() == BitKey.Big.class),
            "BitKey size " + size + " not BitKey.Big");

        size = 129;
        bitKey = BitKey.Factory.makeBitKey(size);
        assertTrue(
            (bitKey.getClass() == BitKey.Big.class),
            "BitKey size " + size + " not BitKey.Big");

        size = 1280;
        bitKey = BitKey.Factory.makeBitKey(size);
        assertTrue(
            (bitKey.getClass() == BitKey.Big.class),
            "BitKey size " + size + " not BitKey.Big");
    }

    /**
     * Test for equals and not equals
     */
	@Test
    void testEquals() {
        int[][] positionsArray0 = {
            new int[] { 0, 1, 2, 3, },
            new int[] { 3, 17, 33, 63 },
            new int[] { 1, 2, 3, 20, 21, 33, 61, 62, 63 },
        };
        doTestEquals(0, 0, positionsArray0);
        doTestEquals(0, 64, positionsArray0);
        doTestEquals(64, 0, positionsArray0);
        doTestEquals(0, 128, positionsArray0);
        doTestEquals(128, 0, positionsArray0);
        doTestEquals(64, 128, positionsArray0);
        doTestEquals(128, 64, positionsArray0);

        int[][] positionsArray1 = {
            new int[] { 0, 1, 2, 3, },
            new int[] { 3, 17, 33, 63 },
            new int[] { 1, 2, 3, 20, 21, 33, 61, 62, 63 },
            new int[] { 1, 2, 3, 20, 21, 33, 61, 62, 55, 56, 127 },
        };
        doTestEquals(65, 65, positionsArray1);
        doTestEquals(65, 128, positionsArray1);
        doTestEquals(128, 65, positionsArray1);
        doTestEquals(128, 128, positionsArray1);

        int[][] positionsArray2 = {
            new int[] { 0, 1, 2, 3, },
            new int[] { 1, 2, 3, 20, 21, 33, 61, 62, 55, 56, 127, 128 },
            new int[] { 1, 2, 499},
            new int[] { 1, 2, 200, 300, 499},
        };
        doTestEquals(500, 500, positionsArray2);
        doTestEquals(500, 700, positionsArray2);
        doTestEquals(700, 500, positionsArray2);
        doTestEquals(700, 700, positionsArray2);
    }

	@Test
    void testHashCode() {
        BitKey small = BitKey.Factory.makeBitKey(10);
        BitKey mid = BitKey.Factory.makeBitKey(70);
        BitKey big255 = BitKey.Factory.makeBitKey(255);
        BitKey big256 = BitKey.Factory.makeBitKey(256);
        BitKey big257 = BitKey.Factory.makeBitKey(257);
        BitKey[] bitKeys = {small, mid, big255, big256, big257};
        doHashCode(bitKeys);
        for (int i = 0; i < bitKeys.length; i++) {
            bitKeys[i].set(0, true);
        }
        doHashCode(bitKeys);
        bitKeys = new BitKey[] {mid, big255, big256, big257};
        for (int i = 0; i < bitKeys.length; i++) {
            bitKeys[i].set(50, true);
        }
        doHashCode(bitKeys);
        bitKeys = new BitKey[] {big255, big256, big257};
        for (int i = 0; i < bitKeys.length; i++) {
            bitKeys[i].set(128, true);
            bitKeys[i].set(50, false);
        }
        doHashCode(bitKeys);
    }

    /**
     * Applies hashCode, compareTo and equals to all combinations of bit keys,
     * including reflexive (comparing a key to itself) and symmetric
     * (comparing in both directions). All keys must be equal (albeit different
     * representations).
     */
    void doHashCode(BitKey[] bitKeys) {
        for (int i1 = 0; i1 < bitKeys.length; i1++) {
            BitKey bitKey1 = bitKeys[i1];
            for (int i2 = 0; i2 < bitKeys.length; i2++) {
                BitKey bitKey2 = bitKeys[i2];
                String s = "(" + i1 + ", " + i2 + ")";
                assertEquals(bitKey1, bitKey2, s);
                assertEquals(bitKey1.hashCode(), bitKey2.hashCode(), s);
                assertEquals(0, bitKey1.compareTo(bitKey2), s);
            }
        }
    }

    /**
     * Test for not equals and not equals
     */
	@Test
    void testNotEquals() {
        int[] positions0 = {
            0, 1, 2, 3, 4
        };
        int[] positions1 = {
            0, 1, 2, 3,
        };
        doTestNotEquals(0, positions0, 0, positions1);
        doTestNotEquals(0, positions1, 0, positions0);
        doTestNotEquals(0, positions0, 64, positions1);
        doTestNotEquals(0, positions1, 64, positions0);
        doTestNotEquals(64, positions0, 0, positions1);
        doTestNotEquals(64, positions1, 0, positions0);
        doTestNotEquals(0, positions0, 128, positions1);
        doTestNotEquals(128, positions1, 0, positions0);
        doTestNotEquals(64, positions0, 128, positions1);
        doTestNotEquals(128, positions1, 64, positions0);
        doTestNotEquals(128, positions0, 128, positions1);
        doTestNotEquals(128, positions1, 128, positions0);

        int[] positions2 = {
            0, 1,
        };
        int[] positions3 = {
            0, 1, 113
        };
        doTestNotEquals(0, positions2, 127, positions3);
        doTestNotEquals(127, positions3, 0, positions2);

        int[] positions4 = {
            0, 1, 100, 121
        };
        int[] positions5 = {
            0, 1, 100, 121, 200
        };
        doTestNotEquals(127, positions4, 300, positions5);
        doTestNotEquals(300, positions5, 127, positions4);

        int[] positions6 = {
            0, 1, 100, 121, 200,
        };
        int[] positions7 = {
            0, 1, 100, 121, 130, 200,
        };
        doTestNotEquals(200, positions6, 300, positions7);
        doTestNotEquals(300, positions7, 200, positions6);
    }

    /**
     * Test that after clear the internal values are 0.
     */
	@Test
    void testClear() {
        BitKey bitKey_0 = BitKey.Factory.makeBitKey(0);
        BitKey bitKey_64 = BitKey.Factory.makeBitKey(64);
        BitKey bitKey_128 = BitKey.Factory.makeBitKey(128);

        int size0 = 20;
        int[] positions0 = {
            0, 1, 2, 3, 4
        };
        BitKey bitKey0 = makeAndSet(size0, positions0);
        bitKey0.clear();

        assertTrue(
            (bitKey0.equals(bitKey_0)), "BitKey 0 not equals after clear to 0");
        assertTrue(
            (bitKey0.equals(bitKey_64)),
            "BitKey 0 not equals after clear to 64");
        assertTrue(
            (bitKey0.equals(bitKey_128)),
            "BitKey 0 not equals after clear to 128");

        int size1 = 68;
        int[] positions1 = {
            0, 1, 2, 3, 4, 45, 67
        };
        BitKey bitKey1 = makeAndSet(size1, positions1);
        bitKey1.clear();

        assertTrue(
            (bitKey1.equals(bitKey_0)), "BitKey 1 not equals after clear to 0");
        assertTrue(
            (bitKey1.equals(bitKey_64)),
            "BitKey 1 not equals after clear to 64");
        assertTrue(
            (bitKey1.equals(bitKey_128)),
            "BitKey 1 not equals after clear to 128");

        int size2 = 400;
        int[] positions2 = {
            0, 1, 2, 3, 4, 45, 67, 213, 333
        };
        BitKey bitKey2 = makeAndSet(size2, positions2);
        bitKey2.clear();

        assertTrue(
            (bitKey2.equals(bitKey_0)), "BitKey 2 not equals after clear to 0");
        assertTrue(
            (bitKey2.equals(bitKey_64)),
            "BitKey 2 not equals after clear to 64");
        assertTrue(
            (bitKey2.equals(bitKey_128)),
            "BitKey 2 not equals after clear to 128");
    }

	@Test
    void testNewBitKeyIsTheSameAsAClearedBitKey() {
        BitKey bitKey = BitKey.Factory.makeBitKey(8);
        bitKey.set(1);
        assertNotEquals(BitKey.Factory.makeBitKey(8), bitKey);
        bitKey.clear();
        assertEquals(BitKey.Factory.makeBitKey(8), bitKey);
    }

	@Test
    void testEmptyCopyCreatesBitKeyOfTheSameSize() {
        BitKey bitKey = BitKey.Factory.makeBitKey(8);
        assertEquals(bitKey, bitKey.emptyCopy());
    }

    /**
     * This test is one BitKey is a subset of another.
     */
	@Test
    void testIsSuperSetOf() {
        int size0 = 20;
        int[] positions0 = {
            0, 2, 3, 4, 23, 30
        };
        BitKey bitKey0 = makeAndSet(size0, positions0);

        int size1 = 20;
        int[] positions1 = {
            0, 2, 23
        };
        BitKey bitKey1 = makeAndSet(size1, positions1);

        assertTrue(
            (bitKey0.isSuperSetOf(bitKey1)), "BitKey 1 not subset of 0");

        assertTrue(
            (!bitKey1.isSuperSetOf(bitKey0)), "BitKey 0 is subset of 1");

        int size2 = 65;
        int[] positions2 = {
            0, 1, 2, 3, 4, 23, 30, 113
        };
        BitKey bitKey2 = makeAndSet(size2, positions2);

        assertTrue(
            (bitKey2.isSuperSetOf(bitKey0)), "BitKey 0 not subset of 2");
        assertTrue(
            (bitKey2.isSuperSetOf(bitKey1)), "BitKey 1 not subset of 2");

        assertTrue(
            (!bitKey0.isSuperSetOf(bitKey2)), "BitKey 2 is subset of 0");
        assertTrue(
            (!bitKey1.isSuperSetOf(bitKey2)), "BitKey 2 is subset of 1");


        int size3 = 213;
        int[] positions3 = {
            0, 1, 2, 3, 4, 23, 30, 113, 145, 233, 234
        };
        BitKey bitKey3 = makeAndSet(size3, positions3);

        assertTrue(
            (bitKey3.isSuperSetOf(bitKey0)), "BitKey 0 not subset of 3");
        assertTrue(
            (bitKey3.isSuperSetOf(bitKey1)), "BitKey 1 not subset of 3");
        assertTrue(
            (bitKey3.isSuperSetOf(bitKey2)), "BitKey 2 not subset of 3");

        assertTrue(
            (!bitKey0.isSuperSetOf(bitKey3)), "BitKey 3 is subset of 0");
        assertTrue(
            (!bitKey1.isSuperSetOf(bitKey3)), "BitKey 3 is subset of 1");
        assertTrue(
            (!bitKey2.isSuperSetOf(bitKey3)), "BitKey 3 is subset of 2");
    }

    /**
     * Tests the 'or' operation on BitKeys
     */
	@Test
    void testOr() {
        doTestOp(
            new Checker() {
                @Override
				public void check(
                    int size0, int[] positions0, int size1, int[] positions1)
                {
                    BitKey bitKey0 = makeAndSet(size0, positions0);
                    BitKey bitKey1 = makeAndSet(size1, positions1);
                    BitKey bitKey = bitKey0.or(bitKey1);
                    int max = 0;
                    for (int i = 0; i < positions0.length; i++) {
                        max = Math.max(max, positions0[i]);
                    }
                    for (int i = 0; i < positions1.length; i++) {
                        max = Math.max(max, positions1[i]);
                    }
                    for (int pos = 0; pos <= max; pos++) {
                        boolean expected = contains(positions0, pos)
                            || contains(positions1, pos);
                        assertEquals(expected, bitKey.get(pos));
                    }
                }
            });
    }

    /**
     * Tests the 'nor' operation on BitKeys
     */
	@Test
    void testOrNot() {
        doTestOp(
            new Checker() {
                @Override
				public void check(
                    int size0, int[] positions0, int size1, int[] positions1)
                {
                    BitKey bitKey0 = makeAndSet(size0, positions0);
                    BitKey bitKey1 = makeAndSet(size1, positions1);
                    BitKey bitKey = bitKey0.orNot(bitKey1);
                    int max = 0;
                    for (int i = 0; i < positions0.length; i++) {
                        max = Math.max(max, positions0[i]);
                    }
                    for (int i = 0; i < positions1.length; i++) {
                        max = Math.max(max, positions1[i]);
                    }
                    for (int pos = 0; pos <= max; pos++) {
                        boolean expected = contains(positions0, pos)
                            ^ contains(positions1, pos);
                        assertEquals(expected, bitKey.get(pos));
                    }
                }
            });
    }

    /**
     * Tests the 'and' operation on BitKeys
     */
	@Test
    void testAnd() {
        doTestOp(
            new Checker() {
                @Override
				public void check(
                    int size0, int[] positions0, int size1, int[] positions1)
                {
                    BitKey bitKey0 = makeAndSet(size0, positions0);
                    BitKey bitKey1 = makeAndSet(size1, positions1);
                    BitKey bitKey = bitKey0.and(bitKey1);
                    int max = 0;
                    for (int i = 0; i < positions0.length; i++) {
                        max = Math.max(max, positions0[i]);
                    }
                    for (int i = 0; i < positions1.length; i++) {
                        max = Math.max(max, positions1[i]);
                    }
                    for (int pos = 0; pos <= max; pos++) {
                        boolean expected =
                            contains(positions0, pos)
                            && contains(positions1, pos);
                        assertEquals(expected, bitKey.get(pos));
                    }
                }
            });
    }

    /**
     * Tests the {@link BitKey#andNot(BitKey)} operation.
     */
	@Test
    void testAndNot() {
        doTestOp(
            new Checker() {
                @Override
				public void check(
                    int size0, int[] positions0, int size1, int[] positions1)
                {
                    BitKey bitKey0 = makeAndSet(size0, positions0);
                    BitKey bitKey1 = makeAndSet(size1, positions1);
                    BitKey bitKey = bitKey0.andNot(bitKey1);
                    int max = 0;
                    for (int i = 0; i < positions0.length; i++) {
                        max = Math.max(max, positions0[i]);
                    }
                    for (int i = 0; i < positions1.length; i++) {
                        max = Math.max(max, positions1[i]);
                    }
                    for (int pos = 0; pos <= max; pos++) {
                        boolean expected =
                            contains(positions0, pos)
                            && !contains(positions1, pos);
                        assertEquals(expected, bitKey.get(pos));
                    }
                }
            });
    }

    /**
     * Tests the 'intersects' operation on BitKeys
     */
	@Test
    void testIntersects() {
        doTestOp(
            new Checker() {
                @Override
				public void check(
                    int size0, int[] positions0, int size1, int[] positions1)
                {
                    BitKey bitKey0 = makeAndSet(size0, positions0);
                    BitKey bitKey1 = makeAndSet(size1, positions1);
                    boolean result = bitKey0.intersects(bitKey1);
                    boolean expected = false;
                    for (int i = 0; i < positions0.length; i++) {
                        for (int j = 0; j < positions1.length; j++) {
                            if (positions0[i] == positions1[j]) {
                                expected = true;
                            }
                        }
                    }
                    assertEquals(expected, result);
                }
            });
    }

    /**
     * Tests the {@link BitKey#toBitSet()} method.
     */
	@Test
    void testToBitSet() {
        doTestOp(
            new Checker() {
                @Override
				public void check(
                    int size0, int[] positions0, int size1, int[] positions1)
                {
                    BitKey bitKey0 = makeAndSet(size0, positions0);
                    final BitSet bitSet = bitKey0.toBitSet();
                    int j = 0;
                    for (int i = bitSet.nextSetBit(0);
                        i >= 0;
                        i = bitSet.nextSetBit(i + 1))
                    {
                        assertEquals(i,  positions0[j++]);
                    }
                    assertEquals(j, positions0.length);
                }
            });
    }

    /**
     * Tests the 'compareTo' operation on BitKeys
     */
	@Test
    void testCompareTo() {
        doTestOp(
            new Checker() {
                @Override
				public void check(
                    int size0, int[] positions0, int size1, int[] positions1)
                {
                    BitKey bitKey0 = makeAndSet(size0, positions0);
                    BitKey bitKey1 = makeAndSet(size1, positions1);
                    int c = bitKey0.compareTo(bitKey1);
                    final String s0 = bitKey0.toString();
                    final String s1 = bitKey1.toString();
                    String ps0 = s0.substring("0x".length());
                    String ps1 = s1.substring("0x".length());
                    while (ps0.length() < ps1.length()) {
                        ps0 = "0" + ps0;
                    }
                    while (ps1.length() < ps0.length()) {
                        ps1 = "0" + ps1;
                    }
                    assertEquals(c, sign(ps0.compareTo(ps1)));
                    assertEquals(-c, bitKey1.compareTo(bitKey0));
                    assertEquals(0, bitKey0.compareTo(bitKey0));
                    assertEquals(0, bitKey1.compareTo(bitKey1));
                }
            });
    }

    private static int sign(int c) {
        return c < 0 ? -1
            : c > 0 ? 1
            : 0;
    }

    private void doTestOp(final Checker checker) {
        int size0 = 40;
        int size1 = 100;
        int size2 = 400;

        int[] positions0 = { 0 };
        int[] positions1 = { 1 };
        checker.check(size0, positions0, size0, positions1);

        int[] positions2 = { 0, 1, 10, 20 };
        int[] positions3 = { 1, 2, 10, 11 };
        checker.check(size0, positions2, size0, positions3);

        int[] positions4 = { 0, 1, 10, 20 };
        int[] positions5 = { 1, 2, 10, 65, 66 };
        checker.check(size0, positions4, size1, positions5);
        checker.check(size1, positions5, size0, positions4);

        int[] positions6 = { 0, 1, 10, 20, 64, 65, 66 };
        int[] positions7 = { 1, 2, 10, 65, 66 };
        checker.check(size1, positions6, size1, positions7);

        int[] positions8 = { 0, 1, 10, 20 };
        int[] positions9 = { 1, 2, 10, 165, 366 };
        checker.check(size0, positions8, size2, positions9);
        checker.check(size2, positions9, size0, positions8);

        int[] positions10 = { 0, 1, 10, 20, 100 };
        int[] positions11 = { 1, 2, 10, 165, 366 };
        checker.check(size1, positions10, size2, positions11);
        checker.check(size2, positions11, size1, positions10);

        int[] positions12 = { 0, 1, 10, 20, 100, 165, 367 };
        int[] positions13 = { 1, 2, 10, 165, 366 };
        checker.check(size2, positions12, size2, positions13);
        checker.check(size2, positions13, size2, positions12);

        int[] positions14 = {63};
        int[] positions15 = {63, 127, 191};
        checker.check(size1, positions14, size1, positions14);
        checker.check(size2, positions15, size2, positions15);
    }

    private interface Checker {
        void check(
            int size0, int[] positions0, int size1, int[] positions1);
    }

    private static boolean contains(int[] positions, int pos) {
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] == pos) {
                return true;
            }
        }
        return false;
    }

	@Test
    void testCreateFromBitSet() {
        final BitSet bitSet = new BitSet(72);
        bitSet.set(2);
        bitSet.set(3);
        bitSet.set(5);
        bitSet.set(11);
        BitKey bitKey = BitKey.Factory.makeBitKey(bitSet);
        assertEquals(
            "0x0000000000000000000000000000000000000000000000000000100000101100",
            bitKey.toString());

        final BitSet emptyBitSet = new BitSet(77);
        bitKey = BitKey.Factory.makeBitKey(emptyBitSet);
        assertTrue(bitKey.isEmpty());
    }

	@Test
    void testIsEmpty() {
        BitKey small = BitKey.Factory.makeBitKey(3);
        assertTrue(small.isEmpty());
        small.set(2);
        assertFalse(small.isEmpty());

        BitKey medium = BitKey.Factory.makeBitKey(66);
        assertTrue(medium.isEmpty());
        medium.set(2);
        assertFalse(medium.isEmpty());
        medium.set(2, false);
        assertTrue(medium.isEmpty());
        medium.set(65);
        assertFalse(medium.isEmpty());

        BitKey large = BitKey.Factory.makeBitKey(131);
        assertTrue(large.isEmpty());
        large.set(2);
        assertFalse(large.isEmpty());
        large.set(129);
        assertFalse(large.isEmpty());
        large.set(129, false);
        large.set(2, false);
        assertTrue(large.isEmpty());
    }

	@Test
    void testIterator() {
/*
        printBitPositions(0);
        printBitPositions(1);
        printBitPositions(2);
        printBitPositions(3);
        printBitPositions(4);
        printBitPositions(5);
        printBitPositions(6);
        printBitPositions(7);
        printBitPositions(8);
        printBitPositions(9);
*/
        // BitKey.Small
        int[] bitPositions = new int[] {
            //0, 1, 2
            1
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            2, 3, 4, 7, 14
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            3, 6, 9, 12, 15, 24, 35, 48
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            60, 62
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            1, 3, 60, 63
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            63
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            0, 1, 62, 63
        };
        doTestIterator(bitPositions);

        // BitKey.Mid128
        bitPositions = new int[] {
            65
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            1, 65
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            1, 63, 64, 65, 66, 127
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            127
        };
        doTestIterator(bitPositions);

        // BitKey.Big
        bitPositions = new int[] {
            128
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            192
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            1, 128
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            0, 1, 127, 193
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            0, 1, 127, 128, 191, 192, 193
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            0, 1, 62, 63, 64, 127, 128, 191, 192, 193
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
            567
        };
        doTestIterator(bitPositions);
        bitPositions = new int[] {
        };
        doTestIterator(bitPositions);
    }

    private void printBitPositions(int i) {
        int b = (i & -i);
        int p = BitKey.bitPositionTable[b];
        System.out.println("  i=" + i + ",b=" + b + ",p=" + p);
    }

    private void doTestIterator(int[] bitPositions) {
        int maxPosition = 0;
        for (int pos : bitPositions) {
            if (pos > maxPosition) {
                maxPosition = pos;
            }
        }
        BitKey bitKey = BitKey.Factory.makeBitKey(maxPosition);
        for (int pos : bitPositions) {
            bitKey.set(pos);
        }
        int index = 0;
        for (Integer i : bitKey) {
            assertEquals(i, Integer.valueOf(bitPositions[index++]));
        }

        // Check cardinality
        assertEquals(bitKey.cardinality(), bitPositions.length);

        // Check nextSetBit
        index = -1;
        final Iterator<Integer> iter = bitKey.iterator();
        while (iter.hasNext()) {
            index = bitKey.nextSetBit(index + 1);
            assertEquals(index, (int) iter.next());
        }
        assertEquals(-1, bitKey.nextSetBit(index + 1));
    }

    private void doTestEquals(int size0, int size1, int[][] positionsArray) {
        for (int i = 0; i < positionsArray.length; i++) {
            int[] positions = positionsArray[i];

            BitKey bitKey0 = makeAndSet(size0, positions);
            BitKey bitKey1 = makeAndSet(size1, positions);

            assertTrue(
                (bitKey0.equals(bitKey1)),
                "BitKey not equals size0=" + size0 + ", size1=" + size1 + ", i="
                		+ i);
        }
    }

    private void doTestNotEquals(
        int size0,
        int[] positions0,
        int size1,
        int[] positions1)
    {
        BitKey bitKey0 = makeAndSet(size0, positions0);
        BitKey bitKey1 = makeAndSet(size1, positions1);

        assertTrue(
            (!bitKey0.equals(bitKey1)),
            "BitKey not equals size0=" + size0 + ", size1=" + size1);
    }

    private static BitKey makeAndSet(int size, int[] positions) {
        BitKey bitKey = BitKey.Factory.makeBitKey(size);

        for (int i = 0; i < positions.length; i++) {
            bitKey.set(positions[i]);
        }
        return bitKey;
    }

	@Test
    void testCompareUnsigned() {
        assertEquals(0, BitKey.AbstractBitKey.compareUnsigned(0, 0));
        assertEquals(0, BitKey.AbstractBitKey.compareUnsigned(10, 10));
        assertEquals(0, BitKey.AbstractBitKey.compareUnsigned(-3, -3));
        assertEquals(-1, BitKey.AbstractBitKey.compareUnsigned(0, 1));
        assertEquals(1, BitKey.AbstractBitKey.compareUnsigned(1, 0));
        // negative numbers are interpreted as large unsigned
        assertEquals(1, BitKey.AbstractBitKey.compareUnsigned(-1, 1));
        assertEquals(1, BitKey.AbstractBitKey.compareUnsigned(-1, 0));
        // -1 is a larger unsigned number than -2
        assertEquals(1, BitKey.AbstractBitKey.compareUnsigned(-1, -2));
        assertEquals(-1, BitKey.AbstractBitKey.compareUnsigned(-2, -1));
    }

	@Test
    void testCompareUnsignedLongArrays() {
        // empty arrays are equal
        assertEquals(
            0,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {},
                new long[] {}));
        // empty array does not equal other
        assertEquals(
            -1,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {},
                new long[] {1}));
        // empty array with left-padding
        assertEquals(
            0,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {},
                new long[] {0, 0}));
        assertEquals(
            0,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {0},
                new long[] {}));
        assertEquals(
            0,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {0, 0},
                new long[] {0, 0}));
        // 0x00000050000001 > 00000040000002
        assertEquals(
            1,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {1, 5},
                new long[] {2, 4}));
        // 0x00000050000001 < 00000050000002
        assertEquals(
            -1,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {1, 5},
                new long[] {2, 5}));
        // as above, with zero padding
        assertEquals(
            -1,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {1, 5},
                new long[] {2, 5, 0, 0}));
        assertEquals(
            -1,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {1, 5, 0, 0, 0},
                new long[] {2, 5, 0, 0}));
        assertEquals(
            -1,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {1, 5, 0, 0, 0},
                new long[] {2, 5}));
        // negative numbers are interpreted as large unsigned
        assertEquals(
            1,
            BitKey.AbstractBitKey.compareUnsignedArrays(
                new long[] {1, 5},
                new long[] {-2, 4}));
    }
}
