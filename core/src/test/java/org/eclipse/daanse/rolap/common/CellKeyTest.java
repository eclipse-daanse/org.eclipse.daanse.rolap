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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.key.CellKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test that the implementations of the CellKey interface are correct.
 *
 * @author Richard M. Emberson
 */
class CellKeyTest  {

    @BeforeEach
    public void beforeEach() {
    }

    @AfterEach
    public void afterEach() {
        SystemWideProperties.instance().populateInitial();
    }

    @Test
    void testMany() {
        CellKey key = CellKey.Generator.newManyCellKey(5);

        assertTrue(key.size() == 5, "CellKey size");

        CellKey copy = key.copy();
        assertTrue(key.equals(copy), "CellKey equals");

        int[] ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");

        boolean gotException = false;
        try {
            key.setAxis(6, 1);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey axis too big");

        gotException = false;
        try {
            key.setOrdinals(new int[6]);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey array too big");

        gotException = false;
        try {
            key.setOrdinals(new int[4]);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey array too small");

        key.setAxis(0, 1);
        key.setAxis(1, 3);
        key.setAxis(2, 5);
        key.setAxis(3, 7);
        key.setAxis(4, 13);
        assertTrue(!key.equals(copy), "CellKey not equals");

        copy = key.copy();
        assertTrue(key.equals(copy), "CellKey equals");

        ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");
    }

    @Test
    void testZero() {
        CellKey key = CellKey.Generator.newCellKey(new int[0]);
        CellKey key2 = CellKey.Generator.newCellKey(new int[0]);
        assertTrue(key == key2); // all 0-dimensional keys have same singleton
        assertEquals(0, key.size());

        CellKey copy = key.copy();
        assertEquals(copy, key);

        boolean gotException = false;
        try {
            key.setAxis(0, 0);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey axis too big");

        int[] ordinals = key.getOrdinals();
        assertEquals(ordinals.length, 0);
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");
    }

    @Test
    void testOne() {
        CellKey key = CellKey.Generator.newCellKey(1);
        assertTrue(key.size() == 1, "CellKey size");

        CellKey copy = key.copy();
        assertTrue(key.equals(copy), "CellKey equals");

        int[] ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");

        boolean gotException = false;
        try {
            key.setAxis(3, 1);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey axis too big");

        gotException = false;
        try {
            key.setOrdinals(new int[3]);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey array too big");

        gotException = false;
        try {
            key.setOrdinals(new int[0]);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey array too small");

        key.setAxis(0, 1);

        copy = key.copy();
        assertTrue(key.equals(copy), "CellKey equals");

        ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");
    }

    @Test
    void testTwo() {
        CellKey key = CellKey.Generator.newCellKey(2);

        assertTrue(key.size() == 2, "CellKey size");

        CellKey copy = key.copy();
        assertTrue(key.equals(copy), "CellKey equals");

        int[] ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");

        boolean gotException = false;
        try {
            key.setAxis(3, 1);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey axis too big");

        gotException = false;
        try {
            key.setOrdinals(new int[3]);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey array too big");

        gotException = false;
        try {
            key.setOrdinals(new int[1]);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey array too small");

        key.setAxis(0, 1);
        key.setAxis(1, 3);

        copy = key.copy();
        assertTrue(key.equals(copy), "CellKey equals");

        ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");
    }

    @Test
    void testThree() {
        CellKey key = CellKey.Generator.newCellKey(3);

        assertTrue(key.size() == 3, "CellKey size");

        CellKey copy = key.copy();
        assertTrue(key.equals(copy), "CellKey equals");

        int[] ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");

        boolean gotException = false;
        try {
            key.setAxis(3, 1);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey axis too big");

        gotException = false;
        try {
            key.setOrdinals(new int[4]);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey array too big");

        gotException = false;
        try {
            key.setOrdinals(new int[1]);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey array too small");

        key.setAxis(0, 1);
        key.setAxis(1, 3);
        key.setAxis(2, 5);

        copy = key.copy();
        assertTrue(key.equals(copy), "CellKey array too small");

        ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");
    }

    @Test
    void testFour() {
        CellKey key = CellKey.Generator.newCellKey(4);

        assertTrue(key.size() == 4, "CellKey size");

        CellKey copy = key.copy();
        assertTrue(key.equals(copy), "CellKey equals");

        int[] ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");

        boolean gotException = false;
        try {
            key.setAxis(4, 1);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey axis too big");

        gotException = false;
        try {
            key.setOrdinals(new int[5]);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey array too big");

        gotException = false;
        try {
            key.setOrdinals(new int[1]);
        } catch (Exception ex) {
            gotException = true;
        }
        assertTrue(gotException, "CellKey array too small");

        key.setAxis(0, 1);
        key.setAxis(1, 3);
        key.setAxis(2, 5);
        key.setAxis(3, 7);

        copy = key.copy();
        assertTrue(key.equals(copy), "CellKey equals");

        ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertTrue(key.equals(copy), "CellKey equals");
    }

}
