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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.eclipse.daanse.olap.common.MapContextConfig;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionOverlayTest {

    private SegmentCacheManager shared;

    @BeforeEach
    void setUp() {
        AbstractRolapContext context = mock(AbstractRolapContext.class);
        when(context.getConfig()).thenReturn(new MapContextConfig(() -> Map.of()));
        shared = new SegmentCacheManager(context);
    }

    @AfterEach
    void tearDown() {
        shared.shutdown();
    }

    @Test
    void overlayStartsNoThreadsAndSharesTheActor() {
        int threadsBefore = Thread.activeCount();

        SegmentCacheManager overlay = SegmentCacheManager.sessionOverlay(shared);

        assertThat(overlay.thread).isSameAs(shared.thread);
        assertThat(Thread.activeCount()).isLessThanOrEqualTo(threadsBefore);

        overlay.shutdown();
        // overlay shutdown leaves the shared manager fully alive
        assertThat(shared.thread.isAlive()).isTrue();
        assertThat(shared.cacheExecutor.isShutdown()).isFalse();
        assertThat(shared.sqlExecutor.isShutdown()).isFalse();
    }

    @Test
    void overlayOwnsItsIndexRegistry() {
        SegmentCacheManager overlay = SegmentCacheManager.sessionOverlay(shared);
        assertThat(overlay.getIndexRegistry()).isNotSameAs(shared.getIndexRegistry());
        overlay.shutdown();
    }
}
