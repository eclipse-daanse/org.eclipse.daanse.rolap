/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.util;

import org.eclipse.daanse.olap.util.ByteString;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.daanse.olap.key.CellKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Small structures of the engine: cell keys, byte strings, slot futures.
 */
class SmallStructuresTest {

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 7 })
    void aCellKeyOfEveryArityKeepsItsCoordinatesAndItsCopyIsEqual(int arity) {
        int[] pos = new int[arity];
        for (int i = 0; i < arity; i++) {
            pos[i] = i + 3;
        }
        CellKey key = CellKey.Generator.newCellKey(pos);
        assertThat(key.size()).isEqualTo(arity);
        assertThat(key.getOrdinals()).containsExactly(pos);
        assertThat(key.copy()).isEqualTo(key).hasSameHashCodeAs(key);
        assertThat(CellKey.Generator.newCellKey(key.getOrdinals())).isEqualTo(key);
        CellKey empty = CellKey.Generator.newCellKey(arity);
        if (arity > 0) {
            empty.setOrdinals(pos);
            assertThat(empty).isEqualTo(key);
        }
    }

    @Test
    void theOffsetOfACellKeyFollowsTheAxisMultipliers() {
        int[] multipliers = { 50, 10, 1 };
        CellKey key = CellKey.Generator.newCellKey(new int[] { 2, 3, 4 });
        assertThat(key.getOffset(multipliers)).isEqualTo(134);
        assertThat(CellKey.Generator.getOffset(new int[] { 2, 3, 4 }, multipliers)).isEqualTo(134);
        assertThat(CellKey.Generator.newManyCellKey(3).getOffset(multipliers)).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 5 })
    void aCellKeyReadBackIsEqualToTheOriginal(int arity) throws Exception {
        int[] pos = new int[arity];
        for (int i = 0; i < arity; i++) {
            pos[i] = 7 * i;
        }
        CellKey key = CellKey.Generator.newCellKey(pos);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(key);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(in.readObject()).isEqualTo(key);
        }
    }

    @Test
    void aByteStringCopiesInAndOutAndComparesByContent() {
        byte[] source = { 1, 2, 3 };
        ByteString string = new ByteString(source);
        source[0] = 9;
        assertThat(string.byteAt(0)).isEqualTo((byte) 1);
        byte[] out = string.toByteArray();
        out[1] = 9;
        assertThat(string.byteAt(1)).isEqualTo((byte) 2);
        assertThat(string).isEqualTo(new ByteString(new byte[] { 1, 2, 3 })).hasSameHashCodeAs(new ByteString(new byte[] { 1, 2, 3 }));
        assertThat(string.length()).isEqualTo(3);
    }

    @Test
    void aSlotFutureHandsItsValueOrItsFailureToEveryWaiterAndRefusesASecondEnd() throws Exception {
        SlotFuture<String> future = new SlotFuture<>();
        Thread waiter = Thread.ofVirtual().start(() -> {
            try {
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("v");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        future.put("v");
        waiter.join(5000);
        assertThat(future.isDone()).isTrue();
        assertThat(future.get()).isEqualTo("v");
        assertThatThrownBy(() -> future.put("w")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already done");

        SlotFuture<String> failing = new SlotFuture<>();
        IllegalStateException cause = new IllegalStateException("broken");
        failing.fail(cause);
        assertThatThrownBy(failing::get).isInstanceOf(ExecutionException.class).hasCause(cause);
        assertThatThrownBy(() -> failing.fail(cause)).isInstanceOf(IllegalArgumentException.class);
    }
}
