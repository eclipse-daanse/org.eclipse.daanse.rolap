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
package org.eclipse.daanse.rolap.common.writeback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.rolap.common.writeback.PendingFact;
import org.junit.jupiter.api.Test;

/** What the bracket around the pending writeback rows of one cube hands on, and what it leaves undone. */
class PendingRowsBracketTest {

    private static final List<Map<String, Entry<DataTypeJdbc, Object>>> TWO_ROWS = List.of(
            Map.of("Amount", Map.entry(DataTypeJdbc.DOUBLE, (Object) 10.0)),
            Map.of("Amount", Map.entry(DataTypeJdbc.DOUBLE, (Object) 20.0)));

    private final List<String> order = new ArrayList<>();
    private final PendingFact fact = new PendingFact();

    @Test
    void theRowsReachTheRewriteExactlyAsTheyWereHandedOver() {
        List<List<Map<String, Entry<DataTypeJdbc, Object>>>> rewrittenWith = new ArrayList<>();
        Cube cube = mock(Cube.class);
        when(cube.withPendingRows(any(), any())).thenCallRealMethod();
        doAnswer(call -> rewrittenWith.add(call.getArgument(0))).when(cube).modifyFact(any());

        cube.withPendingRows(TWO_ROWS, () -> "done");

        assertThat(rewrittenWith).containsExactly(TWO_ROWS);
    }

    @Test
    void aCubeThatCannotBeWrittenToStillRunsTheWorkOnAnEmptyList() {
        Cube cube = mock(Cube.class);
        when(cube.withPendingRows(any(), any())).thenCallRealMethod();

        String answer = cube.withPendingRows(List.of(), () -> "the query ran");

        assertThat(answer).isEqualTo("the query ran");
    }

    @Test
    void aRewriteThatFailsRunsNeitherTheWorkNorTheRestoreAndLeavesTheFactFree() {
        IllegalStateException failure = new IllegalStateException("the fact could not be rewritten");

        assertThatThrownBy(() -> fact.run(() -> {
            throw failure;
        }, () -> {
            order.add("work");
            return "done";
        }, () -> order.add("restore"))).isSameAs(failure);

        assertThat(order).isEmpty();
        assertThat(fact.isHeld()).isFalse();
    }
}
