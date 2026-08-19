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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


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

    @BeforeEach void beforeEach() {
    }

    @AfterEach void afterEach() {
    }

    @Test
    void many() {
        CellKey key = CellKey.Generator.newManyCellKey(5);

        assertThat(key.size()).as("CellKey size").isEqualTo(5);

        CellKey copy = key.copy();
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        int[] ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);

       assertThatThrownBy(() -> key.setAxis(6, 1))
         .as("CellKey axis too big")
         .isInstanceOf(ArrayIndexOutOfBoundsException.class);

        assertThatThrownBy(() -> key.setOrdinals(new int[6]))
        .as("CellKey array too big")
        .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> key.setOrdinals(new int[4]))
        .as("CellKey array too small")
        .isInstanceOf(IllegalArgumentException.class);

        key.setAxis(0, 1);
        key.setAxis(1, 3);
        key.setAxis(2, 5);
        key.setAxis(3, 7);
        key.setAxis(4, 13);
        assertThat(copy).as("CellKey not equals").isNotEqualTo(key);

        copy = key.copy();
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);
    }

    @Test
    void zero() {
        CellKey key = CellKey.Generator.newCellKey(new int[0]);
        CellKey key2 = CellKey.Generator.newCellKey(new int[0]);
        assertThat(key2).isSameAs(key); // all 0-dimensional keys have same singleton
        assertThat(key.size()).isEqualTo(0);

        CellKey copy = key.copy();
        assertThat(key).isEqualTo(copy);

        assertThatThrownBy(() -> key.setAxis(0, 0))
        .as("CellKey axis too big")
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        
        int[] ordinals = key.getOrdinals();
        assertThat(ordinals.length).isEqualTo(0);
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);
    }

    @Test
    void one() {
        CellKey key = CellKey.Generator.newCellKey(1);
        assertThat(key.size()).as("CellKey size").isEqualTo(1);

        CellKey copy = key.copy();
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        int[] ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        assertThatThrownBy(() -> key.setAxis(3, 1))
        .as("CellKey axis too big")
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);

        assertThatThrownBy(() -> key.setOrdinals(new int[3]))
        .as("CellKey array too big")
        .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> key.setOrdinals(new int[0]))
        .as("CellKey array too small")
        .isInstanceOf(IllegalArgumentException.class);

        key.setAxis(0, 1);

        copy = key.copy();
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);
    }

    @Test
    void two() {
        CellKey key = CellKey.Generator.newCellKey(2);

        assertThat(key.size()).as("CellKey size").isEqualTo(2);

        CellKey copy = key.copy();
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        int[] ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        assertThatThrownBy(() -> key.setAxis(3, 1))
        .as("CellKey axis too big")
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);

        assertThatThrownBy(() -> key.setOrdinals(new int[3]))
        .as("CellKey array too big")
        .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> key.setOrdinals(new int[1]))
        .as("CellKey array too small")
        .isInstanceOf(IllegalArgumentException.class);

        key.setAxis(0, 1);
        key.setAxis(1, 3);

        copy = key.copy();
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);
    }

    @Test
    void three() {
        CellKey key = CellKey.Generator.newCellKey(3);

        assertThat(key.size()).as("CellKey size").isEqualTo(3);

        CellKey copy = key.copy();
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        int[] ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        assertThatThrownBy(() -> key.setAxis(3, 1))
        .as("CellKey axis too big")
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);

        assertThatThrownBy(() -> key.setOrdinals(new int[4]))
        .as("CellKey array too big")
        .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> key.setOrdinals(new int[1]))
        .as("CellKey array too small")
        .isInstanceOf(IllegalArgumentException.class);

        key.setAxis(0, 1);
        key.setAxis(1, 3);
        key.setAxis(2, 5);

        copy = key.copy();
        assertThat(copy).as("CellKey array too small").isEqualTo(key);

        ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);
    }

    @Test
    void four() {
        CellKey key = CellKey.Generator.newCellKey(4);

        assertThat(key.size()).as("CellKey size").isEqualTo(4);

        CellKey copy = key.copy();
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        int[] ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        assertThatThrownBy(() -> key.setAxis(4, 1))
        .as("CellKey axis too big")
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);

        assertThatThrownBy(() -> key.setOrdinals(new int[5]))
        .as("CellKey array too big")
        .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> key.setOrdinals(new int[1]))
        .as("CellKey array too small")
        .isInstanceOf(IllegalArgumentException.class);

        key.setAxis(0, 1);
        key.setAxis(1, 3);
        key.setAxis(2, 5);
        key.setAxis(3, 7);

        copy = key.copy();
        assertThat(copy).as("CellKey equals").isEqualTo(key);

        ordinals = key.getOrdinals();
        copy = CellKey.Generator.newCellKey(ordinals);
        assertThat(copy).as("CellKey equals").isEqualTo(key);
    }

}
