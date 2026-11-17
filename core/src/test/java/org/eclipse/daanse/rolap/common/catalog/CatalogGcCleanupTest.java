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
package org.eclipse.daanse.rolap.common.catalog;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.ref.WeakReference;
import java.util.UUID;
import org.eclipse.daanse.rolap.common.catalog.CatalogContentKey;

import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.ConnectionKey;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager;
import org.eclipse.daanse.rolap.common.catalog.RolapCatalogCache.DropIndexAction;
import org.junit.jupiter.api.Test;

/**
 * The GC cleanup action drops the dead catalog's segment index from the
 * shared manager and holds neither the catalog nor the context strongly.
 */
class CatalogGcCleanupTest {

    private static final RolapCatalogKey KEY = new RolapCatalogKey(
            new CatalogContentKey("cat", "00".repeat(32)),
            new ConnectionKey(UUID.randomUUID(), null, null, null));

    @Test
    void dropsTheIndexOfTheDeadCatalog() {
        SegmentCacheManager.SegmentCacheIndexRegistry registry =
                mock(SegmentCacheManager.SegmentCacheIndexRegistry.class);
        SegmentCacheManager cacheManager = mock(SegmentCacheManager.class);
        when(cacheManager.getIndexRegistry()).thenReturn(registry);
        AggregationManager aggregationManager = mock(AggregationManager.class);
        when(aggregationManager.getSegmentCacheManager()).thenReturn(cacheManager);
        AbstractBasicContext<?> context = mock(AbstractBasicContext.class,
                withSettings().extraInterfaces(RolapContext.class));
        when(context.getAggregationManager()).thenReturn(aggregationManager);

        new DropIndexAction(KEY, new WeakReference<>((RolapContext) context),
                new WeakReference<>(null)).run();

        verify(registry).dropIndex(KEY);
    }

    /**
     * The index map is keyed by RolapCatalogKey, and several
     * catalog INSTANCES share one key (UseSchemaPool=false constructions,
     * a rebuild after a flush). The death of one instance dropped the
     * index the LIVE pooled sibling was still using - in-flight loads
     * published into a fresh index and their waiters stranded.
     */
    @Test
    void aLiveSiblingUnderTheSameKeyKeepsTheIndex() {
        SegmentCacheManager.SegmentCacheIndexRegistry registry =
                mock(SegmentCacheManager.SegmentCacheIndexRegistry.class);
        SegmentCacheManager cacheManager = mock(SegmentCacheManager.class);
        when(cacheManager.getIndexRegistry()).thenReturn(registry);
        AggregationManager aggregationManager = mock(AggregationManager.class);
        when(aggregationManager.getSegmentCacheManager()).thenReturn(cacheManager);
        AbstractBasicContext<?> context = mock(AbstractBasicContext.class,
                withSettings().extraInterfaces(RolapContext.class));
        when(context.getAggregationManager()).thenReturn(aggregationManager);

        RolapCatalogCache catalogCache = new RolapCatalogCache((RolapContext) context);
        // a pooled sibling still lives under KEY
        catalogCache.put(KEY, mock(org.eclipse.daanse.rolap.element.RolapCatalog.class));

        new DropIndexAction(KEY, new WeakReference<>((RolapContext) context),
                new WeakReference<>(catalogCache)).run();

        verify(registry, never()).dropIndex(KEY);
    }

    @Test
    void deadContextMeansNothingToClean() {
        SegmentCacheManager.SegmentCacheIndexRegistry registry =
                mock(SegmentCacheManager.SegmentCacheIndexRegistry.class);
        WeakReference<RolapContext> cleared = new WeakReference<>(null);

        new DropIndexAction(KEY, cleared, new WeakReference<>(null)).run();

        verify(registry, never()).dropIndex(KEY);
    }

    /**
     * The clear() invariant the same-thread executor exists for: when
     * clear() returns, every pooled catalog is torn down and the pool
     * answers empty - nothing survives a flushSchemaCache.
     */
    @org.junit.jupiter.api.Test
    void clearTearsEveryPooledCatalogDown() {
        AbstractBasicContext<?> context = mock(AbstractBasicContext.class,
                withSettings().extraInterfaces(RolapContext.class));
        RolapCatalogCache catalogCache = new RolapCatalogCache((RolapContext) context);
        org.eclipse.daanse.rolap.element.RolapCatalog pooled =
                mock(org.eclipse.daanse.rolap.element.RolapCatalog.class);
        catalogCache.put(KEY, pooled);
        org.assertj.core.api.Assertions.assertThat(catalogCache.holdsKey(KEY)).isTrue();

        catalogCache.clear();

        org.assertj.core.api.Assertions.assertThat(catalogCache.holdsKey(KEY))
            .as("nothing may survive a flush").isFalse();
        verify(pooled).finalCleanUp();
    }
}
