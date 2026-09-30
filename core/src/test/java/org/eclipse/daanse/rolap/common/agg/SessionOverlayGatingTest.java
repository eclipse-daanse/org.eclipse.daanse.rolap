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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.MapContextConfig;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.eclipse.daanse.rolap.common.writeback.ScenarioImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Writeback isolation is unconditional: a session with pending writeback
 * changes gets a private cache overlay REGARDLESS of the
 * enableSessionCaching flag - with the flag at its stock default (false),
 * uncommitted session values used to be published into the SHARED stores
 * under ordinary header ids and survived a rollback. The flag itself
 * keeps its documented meaning: when true, every connection is isolated.
 *
 * The second half covers the overlay LIFECYCLE: the getter is
 * side-effect free, an overlay survives until the transaction boundary
 * reaps it, and closed sessions leave neither threads nor overlays.
 */
class SessionOverlayGatingTest {

    private AbstractRolapContext context;
    private AggregationManager aggMgr;

    @BeforeEach
    void setUp() {
        // STOCK configuration: enableSessionCaching stays at its default
        // (false) - writeback isolation must not depend on the opt-in
        context = mock(AbstractRolapContext.class);
        when(context.getConfig()).thenReturn(new MapContextConfig(() -> Map.of()));
        aggMgr = new AggregationManager(context);
    }

    @AfterEach
    void tearDown() {
        aggMgr.shutdown();
    }

    private Connection connectionWith(ScenarioImpl scenario) {
        Connection connection = mock(Connection.class);
        doReturn(context).when(connection).getContext();
        when(connection.getScenario()).thenReturn(scenario);
        return connection;
    }

    private static ScenarioImpl scenarioWithPendingCell() {
        ScenarioImpl scenario = new ScenarioImpl();
        scenario.getWritebackCells().add(null);
        return scenario;
    }

    @Test
    void noScenarioReadsShared() {
        assertThat(aggMgr.getSegmentCacheManager(connectionWith(null))).isSameAs(aggMgr.getSegmentCacheManager());
    }

    @Test
    void emptyScenarioReadsShared() {
        assertThat(aggMgr.getSegmentCacheManager(connectionWith(new ScenarioImpl()))).isSameAs(aggMgr.getSegmentCacheManager());
    }

    @Test
    void pendingChangesGetOneStableOverlay() {
        Connection connection = connectionWith(scenarioWithPendingCell());

        var overlay = aggMgr.getSegmentCacheManager(connection);

        assertThat(overlay).isNotSameAs(aggMgr.getSegmentCacheManager());
        assertThat(aggMgr.getSegmentCacheManager(connection)).isSameAs(overlay);
    }

    /**
     * BEHAVIOR CHANGE (review round 4): the getter is side-effect free.
     * Clearing the pending changes routes reads back to the shared
     * manager, but the overlay survives until the TRANSACTION BOUNDARY
     * (commit/rollback/close) reaps it - the old reap-on-read tore down a
     * concurrently running statement's overlay mid-query.
     */
    @Test
    void overlaySurvivesUntilTheTransactionBoundaryReapsIt() throws Exception {
        ScenarioImpl scenario = scenarioWithPendingCell();
        Connection connection = connectionWith(scenario);
        var overlay = aggMgr.getSegmentCacheManager(connection);

        scenario.clear();

        // reads route to the shared manager, the overlay lingers
        assertThat(aggMgr.getSegmentCacheManager(connection)).isSameAs(aggMgr.getSegmentCacheManager());
        assertThat(sessionOverlays()).hasSize(1);

        // the transaction boundary reaps
        aggMgr.removeSegmentCacheManager(connection);
        assertThat(sessionOverlays()).isEmpty();

        // a new round after the boundary starts with a fresh overlay
        scenario.getWritebackCells().add(null);
        assertThat(aggMgr.getSegmentCacheManager(connection)).isNotSameAs(overlay);
    }

    /**
     * Red before: with the flag at its default, pending writeback read the
     * SHARED manager and published uncommitted values into shared stores.
     */
    @Test
    void pendingChangesIsolateEvenWithoutTheCachingFlag() {
        Connection connection = connectionWith(scenarioWithPendingCell());
        assertThat(aggMgr.getSegmentCacheManager(connection))
            .as("pending writeback must never share the cell caches")
            .isNotSameAs(aggMgr.getSegmentCacheManager());
    }

    /** The flag's documented meaning: every connection gets its own manager. */
    @Test
    void cachingFlagIsolatesEveryConnection() {
        AbstractRolapContext flagged = mock(AbstractRolapContext.class);
        when(flagged.getConfig()).thenReturn(new MapContextConfig(
                () -> Map.of(ConfigConstants.ENABLE_SESSION_CACHING, true)));
        AggregationManager flaggedMgr = new AggregationManager(flagged);
        try {
            Connection connection = mock(Connection.class);
            doReturn(flagged).when(connection).getContext();
            when(connection.getScenario()).thenReturn(null);
            assertThat(flaggedMgr.getSegmentCacheManager(connection))
                .isNotSameAs(flaggedMgr.getSegmentCacheManager());
        } finally {
            flaggedMgr.shutdown();
        }
    }

    @Test
    void hundredSessionsLeaveNoThreadsAndNoOverlays() throws Exception {
        int threadsBefore = Thread.activeCount();

        for (int i = 0; i < 100; i++) {
            Connection connection = connectionWith(scenarioWithPendingCell());
            assertThat(aggMgr.getSegmentCacheManager(connection)).isNotSameAs(aggMgr.getSegmentCacheManager());
            aggMgr.removeSegmentCacheManager(connection);
        }

        assertThat(Thread.activeCount()).isLessThanOrEqualTo(threadsBefore);
        assertThat(sessionOverlays()).isEmpty();
        SegmentCacheManager shared = (SegmentCacheManager) aggMgr.getSegmentCacheManager();
        assertThat(shared.thread.isAlive()).isTrue();
        assertThat(shared.cacheExecutor.isShutdown()).isFalse();
        assertThat(shared.sqlExecutor.isShutdown()).isFalse();
    }

    /**
     * A statement can still be running on the session when the
     * transaction boundary reaps the overlay (two statements per XMLA
     * connection are normal). Its composite-cache reference must degrade
     * to clean misses on closed workers - the old teardown CLEARED the
     * worker list the live composite holds by reference, so in-flight
     * puts vanished without a trace and reads saw an emptied structure.
     */
    @Test
    void overlayTeardownDegradesInFlightReadsToMiss() {
        Connection connection = connectionWith(scenarioWithPendingCell());
        SegmentCacheManager overlay =
            (SegmentCacheManager) aggMgr.getSegmentCacheManager(connection);
        var composite = overlay.compositeCache;
        assertThat(overlay.segmentCacheWorkers).isNotEmpty();

        aggMgr.removeSegmentCacheManager(connection);

        assertThat(overlay.segmentCacheWorkers)
            .as("the worker list the composite references stays intact")
            .isNotEmpty();
        assertThat(composite.get(SegmentTestHeaders.header("m1")))
            .as("reads degrade to a miss on the closed worker")
            .isNull();
    }

    private Map<?, ?> sessionOverlays() throws Exception {
        Field field = AggregationManager.class.getDeclaredField("sessionOverlays");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(aggMgr);
    }
}
