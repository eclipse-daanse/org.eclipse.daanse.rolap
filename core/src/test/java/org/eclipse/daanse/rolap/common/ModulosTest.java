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
 */


package org.eclipse.daanse.rolap.common;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.UnaryTupleList;
import org.junit.jupiter.api.Test;

/**
 * Test that the implementations of the Modulos interface are correct.
 *
 * @author <a>Richard M. Emberson</a>
 */
class ModulosTest {

	@Test
    void testMany() {
        Axis[] axes = new Axis[3];
        TupleList positions = newPositionList(4);
        axes[0] = new RolapAxis(positions);
        positions = newPositionList(3);
        axes[1] = new RolapAxis(positions);
        positions = newPositionList(3);
        axes[2] = new RolapAxis(positions);

        Modulos modulos = Modulos.Generator.createMany(axes);
        int ordinal = 23;

        int[] pos = modulos.getCellPos(ordinal);
        assertTrue(pos.length == 3, "Pos length equals 3");
        assertTrue(pos[0] == 3, "Pos[0] length equals 3");
        assertTrue(pos[1] == 2, "Pos[1] length equals 2");
        assertTrue(pos[2] == 1, "Pos[2] length equals 1");
    }

	@Test
    void testOne() {
        Axis[] axes = new Axis[1];
        TupleList positions = newPositionList(53);
        axes[0] = new RolapAxis(positions);

        Modulos modulosMany = Modulos.Generator.createMany(axes);
        Modulos modulos = Modulos.Generator.create(axes);
        int ordinal = 43;

        int[] posMany = modulosMany.getCellPos(ordinal);
        int[] pos = modulos.getCellPos(ordinal);
        assertTrue(Arrays.equals(posMany, pos), "Pos are not equal");

        ordinal = 23;
        posMany = modulosMany.getCellPos(ordinal);
        pos = modulos.getCellPos(ordinal);
        assertTrue(Arrays.equals(posMany, pos), "Pos are not equal");

        ordinal = 7;
        posMany = modulosMany.getCellPos(ordinal);
        pos = modulos.getCellPos(ordinal);
        assertTrue(Arrays.equals(posMany, pos), "Pos are not equal");

        pos[0] = 23;

        int oMany = modulosMany.getCellOrdinal(pos);
        int o = modulos.getCellOrdinal(pos);
        assertTrue(oMany == o, "Ordinals are not equal");

        pos[0] = 11;
        oMany = modulosMany.getCellOrdinal(pos);
        o = modulos.getCellOrdinal(pos);
        assertTrue(oMany == o, "Ordinals are not equal");

        pos[0] = 7;
        oMany = modulosMany.getCellOrdinal(pos);
        o = modulos.getCellOrdinal(pos);
        assertTrue(oMany == o, "Ordinals are not equal");
    }

	@Test
    void testTwo() {
        Axis[] axes = new Axis[2];
        TupleList positions = newPositionList(23);
        axes[0] = new RolapAxis(positions);
        positions = newPositionList(13);
        axes[1] = new RolapAxis(positions);

        Modulos modulosMany = Modulos.Generator.createMany(axes);
        Modulos modulos = Modulos.Generator.create(axes);
        int ordinal = 23;

        int[] posMany = modulosMany.getCellPos(ordinal);
        int[] pos = modulos.getCellPos(ordinal);
        assertTrue(Arrays.equals(posMany, pos), "Pos are not equal");

        ordinal = 11;
        posMany = modulosMany.getCellPos(ordinal);
        pos = modulos.getCellPos(ordinal);
        assertTrue(Arrays.equals(posMany, pos), "Pos are not equal");

        ordinal = 7;
        posMany = modulosMany.getCellPos(ordinal);
        pos = modulos.getCellPos(ordinal);
        assertTrue(Arrays.equals(posMany, pos), "Pos are not equal");

        pos[0] = 3;
        pos[1] = 2;

        int oMany = modulosMany.getCellOrdinal(pos);
        int o = modulos.getCellOrdinal(pos);
        assertTrue(oMany == o, "Ordinals are not equal");

        pos[0] = 2;
        oMany = modulosMany.getCellOrdinal(pos);
        o = modulos.getCellOrdinal(pos);
        assertTrue(oMany == o, "Ordinals are not equal");

        pos[0] = 1;
        oMany = modulosMany.getCellOrdinal(pos);
        o = modulos.getCellOrdinal(pos);
        assertTrue(oMany == o, "Ordinals are not equal");
    }

	@Test
    void testThree() {
        Axis[] axes = new Axis[3];
        TupleList positions = newPositionList(4);
        axes[0] = new RolapAxis(positions);
        positions = newPositionList(3);
        axes[1] = new RolapAxis(positions);
        positions = newPositionList(2);
        axes[2] = new RolapAxis(positions);

        Modulos modulosMany = Modulos.Generator.createMany(axes);
        Modulos modulos = Modulos.Generator.create(axes);
        int ordinal = 23;

        int[] posMany = modulosMany.getCellPos(ordinal);
        int[] pos = modulos.getCellPos(ordinal);
        assertTrue(Arrays.equals(posMany, pos), "Pos are not equal");

        ordinal = 11;
        posMany = modulosMany.getCellPos(ordinal);
        pos = modulos.getCellPos(ordinal);
        assertTrue(Arrays.equals(posMany, pos), "Pos are not equal");

        ordinal = 7;
        posMany = modulosMany.getCellPos(ordinal);
        pos = modulos.getCellPos(ordinal);
        assertTrue(Arrays.equals(posMany, pos), "Pos are not equal");

        pos[0] = 3;
        pos[1] = 2;
        pos[2] = 1;

        int oMany = modulosMany.getCellOrdinal(pos);
        int o = modulos.getCellOrdinal(pos);
        assertTrue(oMany == o, "Ordinals are not equal");

        pos[0] = 2;
        oMany = modulosMany.getCellOrdinal(pos);
        o = modulos.getCellOrdinal(pos);
        assertTrue(oMany == o, "Ordinals are not equal");

        pos[0] = 1;
        oMany = modulosMany.getCellOrdinal(pos);
        o = modulos.getCellOrdinal(pos);
        assertTrue(oMany == o, "Ordinals are not equal");
    }

    TupleList newPositionList(int size) {
        return new UnaryTupleList(
            Collections.<Member>nCopies(size, null));
    }
}
