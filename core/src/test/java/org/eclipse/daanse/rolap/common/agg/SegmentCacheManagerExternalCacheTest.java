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

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentColumn;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.util.ByteString;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.common.MapContextConfig;
import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.eclipse.daanse.rolap.common.catalog.RolapCatalogCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SegmentCacheManagerExternalCacheTest {

    private SegmentCacheManager manager;
    private SegmentCache external;

    @BeforeEach
    void setUp() {
        AbstractRolapContext context = mock(AbstractRolapContext.class);
        when(context.getConfig()).thenReturn(new MapContextConfig(() -> Map.of()));
        when(context.getCatalogCache()).thenReturn(new RolapCatalogCache(context));
        manager = new SegmentCacheManager(context);
        external = mock(SegmentCache.class);
        when(external.getSegmentHeaders()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    /**
     * Stars build lazily: a content-identical catalog cached FIRST may
     * not have built the star yet. getStar must keep scanning instead of
     * returning that catalog's null - the abort NPEd the converter lookup
     * on the query thread (or bound the converter to the wrong star).
     * Red before the fall-through.
     */
    @Test
    void getStarFallsThroughCatalogsWithoutTheStar() {
        AbstractRolapContext context = mock(AbstractRolapContext.class);
        when(context.getConfig()).thenReturn(new MapContextConfig(() -> Map.of()));
        RolapCatalogCache catalogCache = mock(RolapCatalogCache.class);
        when(context.getCatalogCache()).thenReturn(catalogCache);

        ByteString checksum =
                new ByteString("c".getBytes());
        RolapCatalog starless =
                mock(RolapCatalog.class, RETURNS_DEEP_STUBS);
        RolapCatalog starred =
                mock(RolapCatalog.class, RETURNS_DEEP_STUBS);
        RolapStar star =
                mock(RolapStar.class);
        when(starless.getChecksum()).thenReturn(checksum);
        when(starred.getChecksum()).thenReturn(checksum);
        when(starless.getRolapStarRegistry().getStar("FACT")).thenReturn(null);
        when(starred.getRolapStarRegistry().getStar("FACT")).thenReturn(star);
        when(catalogCache.getCachedCatalogs()).thenReturn(List.of(starless, starred));

        SegmentCacheManager man = new SegmentCacheManager(context);
        try {
            SegmentHeader header =
                    new SegmentHeader("Schema", checksum, "Cube", "Measure",
                            List.of(new SegmentColumn("c", 3, null)),
                            List.of(), "FACT",
                            BitKey.Factory.makeBitKey(3), List.of());
            assertThat(man.getStar(header)).isSameAs(star);
            // and a null star never reaches the converter memo
            assertThat(man.getConverter(null, header)).isNull();
        } finally {
            man.shutdown();
        }
    }

    /**
     * Content-identical catalogs (same checksum, different connections)
     * each hold their own index over the same segment ids: an external
     * CREATE must reach every one of them, symmetric to the deleted
     * event. Red while the event resolved only the FIRST matching star.
     */
    @Test
    void externalCreateReachesEveryContentIdenticalCatalog() {
        AbstractRolapContext context = mock(AbstractRolapContext.class);
        when(context.getConfig()).thenReturn(new MapContextConfig(() -> Map.of()));
        RolapCatalogCache catalogCache = mock(RolapCatalogCache.class);
        when(context.getCatalogCache()).thenReturn(catalogCache);

        ByteString checksum =
                new ByteString("c".getBytes());
        RolapCatalog catA =
                mock(RolapCatalog.class, RETURNS_DEEP_STUBS);
        RolapCatalog catB =
                mock(RolapCatalog.class, RETURNS_DEEP_STUBS);
        RolapStar starA =
                mock(RolapStar.class, RETURNS_DEEP_STUBS);
        RolapStar starB =
                mock(RolapStar.class, RETURNS_DEEP_STUBS);
        when(catA.getChecksum()).thenReturn(checksum);
        when(catB.getChecksum()).thenReturn(checksum);
        when(catA.getRolapStarRegistry().getStar("FACT")).thenReturn(starA);
        when(catB.getRolapStarRegistry().getStar("FACT")).thenReturn(starB);
        when(catalogCache.getCachedCatalogs()).thenReturn(List.of(catA, catB));

        SegmentCacheManager man = new SegmentCacheManager(context);
        try {
            SegmentHeader header =
                    new SegmentHeader("Schema", checksum, "Cube", "Measure",
                            List.of(new SegmentColumn("c", 3, null)),
                            List.of(), "FACT",
                            BitKey.Factory.makeBitKey(3), List.of());
            man.externalSegmentCreated(header, context);
            var registry = (SegmentCacheManager.SegmentCacheIndexRegistry) man.getIndexRegistry();
            // execute() goes through the same actor queue and thus runs
            // after the enqueued create event
            Boolean both = man.execute(ActorCommands.onActor(() ->
                registry.getIndex(starA).contains(header)
                    && registry.getIndex(starB).contains(header)));
            assertThat(both).as("both catalogs' indexes hold the created header").isTrue();
        } finally {
            man.shutdown();
        }
    }

    @Test
    void attachAndDetachAreSymmetricAndNeverTearDown() {
        int workersBefore = manager.segmentCacheWorkers.size();

        manager.addExternalCache(external);
        assertThat(manager.segmentCacheWorkers).hasSize(workersBefore + 1);
        ArgumentCaptor<SegmentCache.SegmentCacheListener> listener =
                ArgumentCaptor.forClass(SegmentCache.SegmentCacheListener.class);
        verify(external).addListener(listener.capture());

        manager.removeExternalCache(external);
        assertThat(manager.segmentCacheWorkers).hasSize(workersBefore);
        verify(external).removeListener(listener.getValue());
        verify(external, never()).tearDown();
    }

    @Test
    void detachedWorkerDegradesInFlightCallsToAMiss() {
        when(external.get(any())).thenThrow(new IllegalStateException("provider closed"));
        manager.addExternalCache(external);
        SegmentCacheWorker worker = manager.segmentCacheWorkers.get(manager.segmentCacheWorkers.size() - 1);

        manager.removeExternalCache(external);

        assertThat(worker.get(null)).isNull();
        assertThat(worker.remove(null)).isFalse();
        assertThat(worker.getSegmentHeaders()).isEmpty();
    }

    @Test
    void attachingTheSameCacheTwiceRegistersOnce() {
        manager.addExternalCache(external);
        int workers = manager.segmentCacheWorkers.size();

        manager.addExternalCache(external);

        assertThat(manager.segmentCacheWorkers).hasSize(workers);
        verify(external).addListener(any());
    }

    @Test
    void shutdownDetachesExternalCachesWithoutTearingThemDown() {
        manager.addExternalCache(external);

        manager.shutdown();

        // the shared external store stays intact for other instances
        verify(external, never()).tearDown();
        verify(external).removeListener(any());
        assertThat(manager.segmentCacheWorkers).isEmpty();
    }

    @Test
    void overlaysIgnoreExternalCaches() {
        SegmentCacheManager overlay = SegmentCacheManager.sessionOverlay(manager);
        int workersBefore = overlay.segmentCacheWorkers.size();
        overlay.addExternalCache(external);
        assertThat(overlay.segmentCacheWorkers).hasSize(workersBefore);
        verify(external, never()).addListener(any());
        overlay.shutdown();
    }
}
