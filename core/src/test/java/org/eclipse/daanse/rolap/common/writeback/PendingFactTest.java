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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.writeback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * What keeps one session's uncommitted writeback out of another session's
 * query.
 */
class PendingFactTest {

    private final PendingFact fact = new PendingFact();
    private final List<String> order = new CopyOnWriteArrayList<>();

    @Test
    void theFactIsRewrittenBeforeTheWorkAndPutBackAfter() {
        String answer = fact.run(() -> order.add("modify"), () -> {
            order.add("work");
            return "done";
        }, () -> order.add("restore"));

        assertThat(answer).isEqualTo("done");
        assertThat(order).containsExactly("modify", "work", "restore");
    }

    @Test
    void theFactIsPutBackEvenWhenTheWorkThrows() {
        assertThatThrownBy(() -> fact.run(() -> order.add("modify"), () -> {
            throw new IllegalStateException("the query failed");
        }, () -> order.add("restore"))).isInstanceOf(IllegalStateException.class);

        assertThat(order).containsExactly("modify", "restore");
        assertThat(fact.isHeld()).isFalse();
    }

    /**
     * The reason the class exists: while one caller has the fact rewritten, no
     * other caller may be between its own modify and restore.
     */
    @Test
    void twoCallersNeverOverlap() throws InterruptedException {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger everOverlapped = new AtomicInteger();
        int callers = 8;
        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(callers);

        for (int i = 0; i < callers; i++) {
            Thread.ofPlatform().start(() -> {
                try {
                    startTogether.await();
                    fact.run(() -> {
                        if (inside.incrementAndGet() != 1) {
                            everOverlapped.incrementAndGet();
                        }
                    }, () -> {
                        if (inside.get() != 1) {
                            everOverlapped.incrementAndGet();
                        }
                        Thread.yield();
                        return null;
                    }, inside::decrementAndGet);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startTogether.countDown();
        assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(everOverlapped).hasValue(0);
        assertThat(inside).hasValue(0);
    }

    /**
     * {@code UPDATE CUBE} resolves its tuple and its value with further MDX on the
     * same cube and the same thread while its pending values are already in place.
     * A non-reentrant lock would deadlock the writeback it exists to protect.
     */
    @Test
    void theSameThreadMayEnterAgain() {
        String answer = fact.run(() -> order.add("outer-modify"),
                () -> fact.run(() -> order.add("inner-modify"), () -> "nested", () -> order.add("inner-restore")),
                () -> order.add("outer-restore"));

        assertThat(answer).isEqualTo("nested");
        assertThat(order).containsExactly("outer-modify", "inner-modify", "inner-restore", "outer-restore");
        assertThat(fact.isHeld()).isFalse();
    }
}
