/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors: SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.common.agg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.daanse.olap.common.ExecutionConfig;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.util.ByteString;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.catalog.RolapCatalogCache;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Priming pulls the external inventory for a freshly built star. The
 * store listing is network I/O: it must run on the cache executor, not
 * on the caller - callers hold catalog-build locks the actor also
 * takes, and the flush path reaches priming ON the actor.
 */
class SegmentCacheManagerPrimingTest {

    private static final ByteString CHECKSUM = new ByteString(new byte[] { 1, 2 });

    @Mock private RolapContext context;
    private AutoCloseable mocks;
    private SegmentCacheManager man;
    private RolapStar star;

    @BeforeEach
    void beforeEach() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.getConfig()).thenReturn(ExecutionConfig.DEFAULTS);
        man = new SegmentCacheManager(context);
        star = mock(RolapStar.class, RETURNS_DEEP_STUBS);
        when(star.getCatalog().getChecksum()).thenReturn(CHECKSUM);
        when(star.getFactTable().getAlias()).thenReturn("FACT");
    }

    @AfterEach
    void afterEach() throws Exception {
        man.shutdown();
        mocks.close();
    }

    private void attach(SegmentCache cache) {
        when(cache.knownStars())
                .thenReturn(Set.of(new SegmentCache.StarKey(CHECKSUM.toString(), "FACT")));
        RolapCatalogCache catalogCache = mock(RolapCatalogCache.class);
        when(context.getCatalogCache()).thenReturn(catalogCache);
        when(catalogCache.getCachedCatalogs()).thenReturn(List.of());
        man.addExternalCache(cache);
    }

    /**
     * The caller returns promptly even while the store listing hangs;
     * the listing runs on the cache executor and the primed header
     * reaches the index through the actor. Red before the async change:
     * schedulePrimingIfPending listed synchronously on the caller.
     */
    @Test
    void primingListsOnTheCacheExecutorNotTheCaller() throws Exception {
        SegmentCache cache = mock(SegmentCache.class);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> listingThread = new AtomicReference<>();
        SegmentHeader header = header();
        when(cache.getSegmentHeaders(CHECKSUM, "FACT")).thenAnswer(invocation -> {
            listingThread.set(Thread.currentThread());
            release.await(10, TimeUnit.SECONDS);
            return List.of(header);
        });
        attach(cache);

        // the attach lists the inventory asynchronously too, so the marker
        // becomes visible eventually - poll for it, then time the actual
        // scheduling call (the hanging listing mock is only reached by it)
        long markerDeadline = System.currentTimeMillis() + 10_000;
        long before = System.nanoTime();
        boolean pending = man.schedulePrimingIfPending(star);
        while (!pending && System.currentTimeMillis() < markerDeadline) {
            Thread.sleep(10);
            before = System.nanoTime();
            pending = man.schedulePrimingIfPending(star);
        }
        long elapsedMillis = (System.nanoTime() - before) / 1_000_000;

        assertThat(pending).isTrue();
        assertThat(elapsedMillis)
                .as("the caller must not wait for the store listing")
                .isLessThan(2_000);
        release.countDown();

        SegmentCacheManager.SegmentCacheIndexRegistry registry =
                (SegmentCacheManager.SegmentCacheIndexRegistry) man.getIndexRegistry();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline
                && !onActor(() -> registry.getIndex(star).contains(header))) {
            Thread.sleep(50);
        }
        assertThat(onActor(() -> registry.getIndex(star).contains(header))).isTrue();
        assertThat(listingThread.get()).isNotNull();
        assertThat(listingThread.get()).isNotSameAs(man.thread);
        assertThat(listingThread.get().getName()).contains("cacheExecutor");
    }

    /**
     * A store failure during the listing degrades to an empty result in
     * the worker (by design - a failing store is a miss, never an error)
     * and CONSUMES the marker; only a rejected submission (shutdown)
     * restores it for a later attach.
     */
    @Test
    void rejectedPrimingRestoresTheMarker() throws Exception {
        SegmentCache cache = mock(SegmentCache.class);
        when(cache.getSegmentHeaders(CHECKSUM, "FACT")).thenReturn(List.of());
        attach(cache);
        man.shutdown();

        assertThat(man.schedulePrimingIfPending(star))
                .as("the marker is consumed even though the submit is rejected")
                .isTrue();
        assertThat(man.schedulePrimingIfPending(star))
                .as("the rejected submit put the marker back")
                .isTrue();
    }

    /**
     * The pending marker is keyed by (checksum, alias): with an alias-only
     * key, catalog A consumed catalog B's pending prime (same fact alias,
     * different checksum) and B stayed blind until a catalog rebuild.
     */
    @Test
    void primingMarkersAreScopedPerCatalogChecksum() {
        ByteString checksumB = new ByteString(new byte[] { 9, 9 });
        SegmentCache cache = mock(SegmentCache.class);
        when(cache.knownStars()).thenReturn(Set.of(
                new SegmentCache.StarKey(CHECKSUM.toString(), "FACT"),
                new SegmentCache.StarKey(checksumB.toString(), "FACT")));
        RolapCatalogCache catalogCache = mock(RolapCatalogCache.class);
        when(context.getCatalogCache()).thenReturn(catalogCache);
        when(catalogCache.getCachedCatalogs()).thenReturn(List.of());
        when(cache.getSegmentHeaders(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        man.addExternalCache(cache);

        RolapStar starB = mock(RolapStar.class, RETURNS_DEEP_STUBS);
        when(starB.getCatalog().getChecksum()).thenReturn(checksumB);
        when(starB.getFactTable().getAlias()).thenReturn("FACT");

        assertThat(man.schedulePrimingIfPending(star)).as("catalog A primes").isTrue();
        assertThat(man.schedulePrimingIfPending(starB)).as("catalog B still primes").isTrue();
    }

    private boolean onActor(java.util.function.Supplier<Boolean> probe) {
        return man.execute(ActorCommands.onActor(probe));
    }

    private SegmentHeader header() {
        return new SegmentHeader("Schema", CHECKSUM, "Cube", "Measure",
                List.of(new org.eclipse.daanse.olap.spi.SegmentColumn("c", 3, null)),
                List.of(), "FACT", BitKey.Factory.makeBitKey(3), List.of());
    }
}
