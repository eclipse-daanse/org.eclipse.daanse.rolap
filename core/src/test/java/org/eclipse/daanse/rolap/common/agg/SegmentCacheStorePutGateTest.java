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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.api.monitor.event.CellCacheEvent;
import org.eclipse.daanse.olap.common.MapContextConfig;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.eclipse.daanse.rolap.common.catalog.RolapCatalogCache;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The store put of a finished load is decided ON the actor: only a
 * header the index still holds may reach the stores. The unconditional
 * caller-side put wrote the pre-flush body of a segment a flush had just
 * constrained away back into every store under the OLD id - the flush
 * was silently non-persistent, and the ghost was re-indexable everywhere.
 */
class SegmentCacheStorePutGateTest {

    private SegmentCacheManager manager;
    private SegmentCache external;
    private RolapStar star;

    @BeforeEach
    void setUp() {
        AbstractRolapContext context = mock(AbstractRolapContext.class);
        when(context.getConfig()).thenReturn(new MapContextConfig(() -> Map.of()));
        when(context.getCatalogCache()).thenReturn(new RolapCatalogCache(context));
        manager = new SegmentCacheManager(context);
        external = mock(SegmentCache.class);
        when(external.getSegmentHeaders()).thenReturn(List.of());
        when(external.put(Mockito.any(), Mockito.any())).thenReturn(true);
        manager.addExternalCache(external);

        star = mock(RolapStar.class, Mockito.RETURNS_DEEP_STUBS);
        when(star.getCatalog().isCellCachingEnabled(Mockito.any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    private static SegmentHeader header() {
        return SegmentTestHeaders.header("m");
    }

    private void cacheLoadedBound(SegmentHeader header, SegmentBody body) {
        Execution execution = mock(Execution.class, Mockito.RETURNS_DEEP_STUBS);
        ExecutionContext bound = ExecutionContext.root(Optional.empty(),
                ExecutionMetadata.of("t", "t", null, 0));
        bound.setExecution(execution);
        ExecutionContext.where(bound, () ->
            manager.cacheLoaded(star, header, body, CellCacheEvent.Source.SQL));
    }

    /**
     * Red before: the put ran unconditionally - a load whose header a
     * flush removed mid-flight ("data arrived late") still wrote the
     * pre-flush body into every store.
     */
    @Test
    void lateSegmentNeverReachesTheStores() throws Exception {
        SegmentHeader header = header();
        cacheLoadedBound(header, mock(SegmentBody.class));

        // drain the actor (the put decision runs there) ...
        manager.execute(ActorCommands.drain());
        // ... then flush the per-id store chain: a put queued by the bug
        // would sit in front of this barrier op, so waiting on it makes
        // the negative verify deterministic instead of sleep-based
        manager.sequencedCacheOp(header, () -> null)
            .get(5, TimeUnit.SECONDS);

        verify(external, never()).put(Mockito.any(), Mockito.any());
    }

    /**
     * A flush hitting a STILL-LOADING header defers its removal
     * (removeAfterLoad flag; the entry survives until the load lands) -
     * the late body must not reach the stores either. Red while the gate
     * used contains(), which stays true for flagged headers.
     */
    @Test
    void flushFlaggedLoadNeverReachesTheStores() throws Exception {
        SegmentHeader header = header();
        manager.execute(ActorCommands.onActor(() -> {
            var index = ((SegmentCacheManager.SegmentCacheIndexRegistry)
                manager.getIndexRegistry()).getIndex(star);
            index.add(header, true);   // load in flight
            index.remove(header);      // flush: deferred, flag only
            return null;
        }));

        cacheLoadedBound(header, mock(SegmentBody.class));

        manager.execute(ActorCommands.drain());
        manager.sequencedCacheOp(header, () -> null)
            .get(5, TimeUnit.SECONDS);

        verify(external, never()).put(Mockito.any(), Mockito.any());
    }

    /** A registered header's body reaches the stores as before. */
    @Test
    void registeredSegmentIsWrittenThrough() {
        SegmentHeader header = header();
        SegmentBody body = mock(SegmentBody.class);
        manager.execute(ActorCommands.onActor(() -> {
            ((SegmentCacheManager.SegmentCacheIndexRegistry) manager.getIndexRegistry())
                .getIndex(star).add(header, false);
            return null;
        }));

        cacheLoadedBound(header, body);

        verify(external, timeout(5000)).put(header, body);
    }
}
