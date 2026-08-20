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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.junit.modifier;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.monitor.event.ExecutionEndEvent;
import org.eclipse.daanse.rolap.testkit.core.TestContext;
import org.eclipse.daanse.rolap.testkit.junit.api.ContextModifier;

/**
 * Counts segment cache loads and hits for the test's context, via the
 * {@link ExecutionEndEvent} the engine fires once per query execution.
 *
 * <p>Declare it and inject it:
 *
 * <pre>{@code
 * @RolapContextTest(value = SchoolTestInstance.class, modifiers = CacheProbe.class)
 * class SegmentCacheBehaviorTest {
 *     @Test
 *     void secondExecutionIsServedFromTheSegmentCache(Connection conn, CacheProbe cache) {
 *         conn.execute(conn.parseQuery(MDX));
 *         assertThat(cache.segmentLoads()).isPositive();
 *
 *         cache.reset();
 *         conn.execute(conn.parseQuery(MDX));
 *         assertThat(cache.segmentLoads()).isZero();
 *         assertThat(cache.hits()).isPositive();
 *     }
 * }
 * }</pre>
 *
 * <p>Uses {@link TestContext#addEventListener} — an additive tap on the
 * context's own {@code EventBus}, scoped to this context instance and
 * deregistered when the extension calls {@link #close()}.
 */
public final class CacheProbe implements ContextModifier {

    private final AtomicInteger loads = new AtomicInteger();
    private final AtomicInteger hits = new AtomicInteger();
    private AutoCloseable subscription;

    @Override
    public void apply(Context<?> context) {
        if (!(context instanceof TestContext testContext)) {
            throw new IllegalStateException(
                    "CacheProbe requires a org.eclipse.daanse.rolap.testkit.core.TestContext, got "
                            + context.getClass().getName());
        }
        subscription = testContext.addEventListener(event -> {
            if (event instanceof ExecutionEndEvent end) {
                loads.addAndGet(end.cellCacheMissCount());
                hits.addAndGet(end.cellCacheHitCount());
            }
        });
    }

    @Override
    public void close() throws Exception {
        if (subscription != null) {
            subscription.close();
        }
    }

    /** Segments loaded from the database (cache misses) since the context was built or {@link #reset()}. */
    public int segmentLoads() {
        return loads.get();
    }

    /** Cell requests served from an already-cached segment since the context was built or {@link #reset()}. */
    public int hits() {
        return hits.get();
    }

    /** Zeroes both counters; the tap stays registered. */
    public void reset() {
        loads.set(0);
        hits.set(0);
    }
}
