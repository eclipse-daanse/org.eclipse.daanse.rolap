/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.agg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.junit.jupiter.api.Test;

/**
 * The worker is the store's fault isolation and the hard enforcement of
 * "the actor never waits on a store": a throwing store degrades to a
 * miss and a counted error, a detached worker degrades to a miss
 * without touching the store, and a call on the manager thread is
 * rejected outright. None of this had a direct test.
 */
class SegmentCacheWorkerTest {

    private final SegmentCache store = mock(SegmentCache.class);
    private final SegmentHeader header = SegmentTestHeaders.header("m1");

    @Test
    void aCallOnTheManagerThreadIsRejected() {
        SegmentCacheWorker worker =
            new SegmentCacheWorker(store, Thread.currentThread());

        assertThatThrownBy(() -> worker.get(header))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cache manager thread");
        verify(store, never()).get(any());
    }

    @Test
    void aThrowingStoreDegradesToAMissAndCountsTheError() {
        when(store.get(any())).thenThrow(new RuntimeException("store down"));
        SegmentCacheWorker worker = new SegmentCacheWorker(store, null);

        assertThat(worker.get(header))
            .as("a store failure is a miss, never an exception")
            .isNull();
        assertThat(worker.stats.errors())
            .as("the failure is counted for telemetry")
            .isEqualTo(1);
    }

    @Test
    void evenAnErrorFromTheStoreDegradesToAMiss() {
        when(store.get(any())).thenThrow(new AssertionError("foreign store bug"));
        SegmentCacheWorker worker = new SegmentCacheWorker(store, null);

        assertThat(worker.get(header)).isNull();
        assertThat(worker.stats.errors()).isEqualTo(1);
    }

    @Test
    void aDetachedWorkerNeverTouchesTheStoreAgain() {
        SegmentCacheWorker worker = new SegmentCacheWorker(store, null);
        worker.markClosing();

        assertThat(worker.get(header)).isNull();
        worker.put(header, mock(org.eclipse.daanse.olap.spi.SegmentBody.class));
        assertThat(worker.remove(header)).isFalse();
        verify(store, never()).get(any());
        verify(store, never()).put(any(), any());
        verify(store, never()).remove(any());
    }
}
