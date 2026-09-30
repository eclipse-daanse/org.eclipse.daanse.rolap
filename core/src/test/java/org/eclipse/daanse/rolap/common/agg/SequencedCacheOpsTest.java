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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.daanse.olap.common.MapContextConfig;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cache operations on the same segment id run in submission order. */
class SequencedCacheOpsTest {

    private SegmentCacheManager manager;

    @BeforeEach
    void setUp() {
        AbstractRolapContext context = mock(AbstractRolapContext.class);
        when(context.getConfig()).thenReturn(new MapContextConfig(() -> Map.of()));
        manager = new SegmentCacheManager(context);
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    private static SegmentHeader header(String measure) {
        return SegmentTestHeaders.header(measure);
    }

    @Test
    void sameSegmentIdRunsInSubmissionOrder() throws Exception {
        SegmentHeader header = header("m1");
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch releaseFirst = new CountDownLatch(1);

        manager.sequencedCacheOp(header, () -> {
            try {
                releaseFirst.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            order.add("put");
            return null;
        });
        manager.sequencedCacheOp(header, () -> {
            order.add("remove");
            return null;
        });
        var last = manager.sequencedCacheOp(header, () -> {
            order.add("put2");
            return null;
        });

        // a different segment id is not held up by the blocked chain
        SegmentHeader other = header("m2");
        manager.sequencedCacheOp(other, () -> {
            order.add("other");
            return null;
        }).get(10, TimeUnit.SECONDS);
        assertThat(order).containsExactly("other");

        releaseFirst.countDown();
        last.get(10, TimeUnit.SECONDS);
        assertThat(order).containsExactly("other", "put", "remove", "put2");
    }

    @Test
    void resultAndFailurePropagateWithoutBreakingTheChain() throws Exception {
        SegmentHeader header = header("m1");
        var failing = manager.sequencedCacheOp(header, () -> {
            throw new IllegalStateException("op fails");
        });
        var after = manager.sequencedCacheOp(header, () -> "ok");

        assertThat(after.get(10, TimeUnit.SECONDS)).isEqualTo("ok");
        assertThat(failing.isCompletedExceptionally()).isTrue();
    }

    /**
     * A rename reads under the OLD id and writes under the NEW: an op
     * enqueued afterwards under the new id must run after the rename.
     * Red before dual-id sequencing (the rename held only the old chain).
     */
    @Test
    void renameOrdersFollowUpsOnTheTargetId() throws Exception {
        SegmentHeader oldHeader = header("m-old");
        SegmentHeader newHeader = header("m-new");
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch renameRunning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        var rename = manager.sequencedCacheOp(oldHeader, newHeader, () -> {
            renameRunning.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            order.add("rename");
            return Boolean.TRUE;
        });
        assertThat(renameRunning.await(5, TimeUnit.SECONDS)).isTrue();
        var followUp = manager.sequencedCacheOp(newHeader, () -> {
            order.add("newIdOp");
            return Boolean.TRUE;
        });
        release.countDown();
        rename.get(10, TimeUnit.SECONDS);
        followUp.get(10, TimeUnit.SECONDS);

        assertThat(order).containsExactly("rename", "newIdOp");
    }

    /**
     * An executor reject (shutdown) must surface as an exceptional future,
     * not a synchronous throw: the throw path left the claimed slot in
     * storeOps forever, blocking every later op on the same id.
     */
    @Test
    void rejectAfterShutdownYieldsExceptionalFutureAndFreesTheSlot() throws Exception {
        SegmentHeader header = header("m1");
        manager.shutdown();

        var first = manager.sequencedCacheOp(header, () -> "never");
        var second = manager.sequencedCacheOp(header, () -> "never either");
        var rename = manager.sequencedCacheOp(header, header("m2"), () -> Boolean.TRUE);

        for (var f : List.of(first, second, rename)) {
            assertThat(
                assertThrows(
                    ExecutionException.class,
                    () -> f.get(10, TimeUnit.SECONDS)))
                .isNotNull();
        }
    }

    /**
     * Crossing rename chains (H->H1 while H1->H2) must not deadlock:
     * slots are claimed in canonical id order.
     */
    @Test
    void crossingRenamesDoNotDeadlock() throws Exception {
        SegmentHeader[] hs = { header("s0"), header("s1"), header("s2"), header("s3") };
        List<CompletableFuture<Boolean>> all = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 200; i++) {
                final int a = i % 4;
                final int b = (i + 1) % 4;
                pool.submit(() -> all.add(
                    manager.sequencedCacheOp(hs[a], hs[b], () -> Boolean.TRUE)));
                pool.submit(() -> all.add(
                    manager.sequencedCacheOp(hs[b], () -> Boolean.TRUE)));
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            for (var f : all) {
                f.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
