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

import java.lang.ref.WeakReference;
import java.util.Map;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Scenario;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.MapContextConfig;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.eclipse.daanse.rolap.common.catalog.RolapCatalogCache;
import org.junit.jupiter.api.Test;

/**
 * The session overlay map holds its connections weakly: an abandoned
 * writeback connection (never queried again, never closed) does not stay
 * pinned by the aggregation manager.
 */
class AggregationManagerSessionOverlayGcTest {

    @Test
    void abandonedWritebackConnectionIsCollectable() throws Exception {
        AbstractRolapContext context = mock(AbstractRolapContext.class);
        doReturn(new MapContextConfig(() -> Map.of(ConfigConstants.ENABLE_SESSION_CACHING, true)))
                .when(context).getConfig();
        doReturn(new RolapCatalogCache(context)).when(context).getCatalogCache();
        AggregationManager manager = new AggregationManager(context);
        try {
            Connection connection = mock(Connection.class);
            doReturn(context).when(connection).getContext();
            Scenario scenario = mock(Scenario.class);
            doReturn(true).when(scenario).hasPendingChanges();
            doReturn(scenario).when(connection).getScenario();

            assertThat(manager.getSegmentCacheManager(connection))
                    .isNotSameAs(manager.getSegmentCacheManager());

            WeakReference<Connection> ref = new WeakReference<>(connection);
            connection = null;
            for (int i = 0; i < 100 && ref.get() != null; i++) {
                System.gc();
                Thread.sleep(10);
            }
            assertThat(ref.get()).as("overlay map must not pin the abandoned connection").isNull();
        } finally {
            manager.shutdown();
        }
    }
}
