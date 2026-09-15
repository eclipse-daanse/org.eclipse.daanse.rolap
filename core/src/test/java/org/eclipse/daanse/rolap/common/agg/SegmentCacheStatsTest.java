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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.eclipse.daanse.olap.common.MapContextConfig;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.junit.jupiter.api.Test;

class SegmentCacheStatsTest {

    @Test
    void countersFollowWorkerTraffic() {
        SegmentCache cache = mock(SegmentCache.class);
        when(cache.get(any())).thenReturn(null, mock(SegmentBody.class));
        when(cache.put(any(), any())).thenReturn(true);
        when(cache.remove(any())).thenReturn(true, false);
        SegmentCacheWorker worker = new SegmentCacheWorker(cache, null);

        worker.get(null);
        worker.put(null, null);
        worker.get(null);
        worker.remove(null);
        worker.remove(null);

        SegmentCacheStats stats = worker.stats;
        assertThat(stats.gets()).isEqualTo(2);
        assertThat(stats.hits()).isEqualTo(1);
        assertThat(stats.puts()).isEqualTo(1);
        assertThat(stats.removes()).isEqualTo(1);
    }

    @Test
    void managerListsStatsPerAttachedCache() {
        AbstractRolapContext context = mock(AbstractRolapContext.class);
        when(context.getConfig()).thenReturn(new MapContextConfig(() -> Map.of()));
        SegmentCacheManager manager = new SegmentCacheManager(context);

        assertThat(manager.getCacheStats())
                .extracting(SegmentCacheStats::cacheName)
                .containsExactly("MemorySegmentCache");
        manager.shutdown();
    }
}
