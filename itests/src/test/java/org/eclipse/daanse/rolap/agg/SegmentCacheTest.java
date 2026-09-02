/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.rolap.agg;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.api.cache.OlapSegmentCacheManager;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheWorker;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;


/**
 * Test suite that runs the {@link BasicQueryTest} but with the
 * {@link MockSegmentCache} active.
 *
 * @author LBoudreau
 */
@RolapContextTest(FoodmartTestInstance.class)
class SegmentCacheTest {

    @Test
    void testCompoundPredicatesCollision(Context<?> context) {
        String query =
            "SELECT [Gender].[All Gender] ON 0, [MEASURES].[CUSTOMER COUNT] ON 1 FROM SALES";
        String query2 =
            "WITH MEMBER GENDER.X AS 'AGGREGATE({[GENDER].[GENDER].members} * "
            + "{[STORE].[ALL STORES].[USA].[CA]})', solve_order=100 "
            + "SELECT GENDER.X ON 0, [MEASURES].[CUSTOMER COUNT] ON 1 FROM SALES";
        String result =
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Gender].[Gender].[All Gender]}\n"
            + "Axis #2:\n"
            + "{[Measures].[Customer Count]}\n"
            + "Row #0: 5,581\n";
        String result2 =
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Gender].[Gender].[X]}\n"
            + "Axis #2:\n"
            + "{[Measures].[Customer Count]}\n"
            + "Row #0: 2,716\n";
        Connection connection = context.getConnectionWithDefaultRole();
        connection.getCacheControl(null).flushSchemaCache();
        assertThatQuery(connection, query).returnsGrid(result);
        assertThatQuery(connection, query2).returnsGrid(result2);
    }

    @Test
    void testSegmentCacheEvents(Context<?> context) throws Exception {
        SegmentCache mockCache = new MockSegmentCache();
        SegmentCacheWorker testWorker =
            new SegmentCacheWorker(mockCache, null);

        // Flush the cache before we start. Wait a second for the cache
        // flush to propagate.
        Connection connection = context.getConnectionWithDefaultRole();
        connection.getCacheControl(null).flushSchemaCache();
        final CacheControl cc =
                connection.getCacheControl(null);
        Cube salesCube = getCube(connection, "Sales");
        cc.flush(cc.createMeasuresRegion(salesCube));
        Thread.sleep(1000);

        OlapSegmentCacheManager  segmentCacheManager = ((AbstractBasicContext)connection.getContext())
            .getAggregationManager().getCacheMgr();
        ((SegmentCacheManager)segmentCacheManager).segmentCacheWorkers
            .add(testWorker);

        final List<SegmentHeader> createdHeaders =
            new ArrayList<>();
        final List<SegmentHeader> deletedHeaders =
            new ArrayList<>();
        final SegmentCache.SegmentCacheListener listener =
            new SegmentCache.SegmentCacheListener() {
                @Override
				public void handle(SegmentCacheEvent e) {
                    switch (e.getEventType()) {
                    case ENTRY_CREATED:
                        createdHeaders.add(e.getSource());
                        break;
                    case ENTRY_DELETED:
                        deletedHeaders.add(e.getSource());
                        break;
                    default:
                        throw new UnsupportedOperationException();
                    }
                }
            };

        try {
            // Register our custom listener.
            segmentCacheManager = ((AbstractBasicContext)connection.getContext())
                    .getAggregationManager().getCacheMgr();
                ((SegmentCacheManager)segmentCacheManager).compositeCache
                .addListener(listener);

            // Now execute a query and check the events
            executeQuery(connection,
                "select {[Measures].[Unit Sales]} on columns from [Sales]");
            // Wait for propagation.
            Thread.sleep(2000);
            assertEquals(2, createdHeaders.size());
            assertEquals(0, deletedHeaders.size());
            assertEquals("Sales", createdHeaders.get(0).cubeName);
            assertEquals("FoodMart", createdHeaders.get(0).schemaName);
            assertEquals("Unit Sales", createdHeaders.get(0).measureName);
            createdHeaders.clear();
            deletedHeaders.clear();

            // Now flush the segment and check the events.
            cc.flush(cc.createMeasuresRegion(salesCube));

            // Wait for propagation.
            Thread.sleep(2000);
            assertEquals(0, createdHeaders.size());
            assertEquals(2, deletedHeaders.size());
            assertEquals("Sales", deletedHeaders.get(0).cubeName);
            assertEquals("FoodMart", deletedHeaders.get(0).schemaName);
            assertEquals("Unit Sales", deletedHeaders.get(0).measureName);
        } finally {
            segmentCacheManager = ((AbstractBasicContext)connection.getContext())
                    .getAggregationManager().getCacheMgr();
            ((SegmentCacheManager)segmentCacheManager).compositeCache
                .removeListener(listener);
            ((SegmentCacheManager)segmentCacheManager).segmentCacheWorkers
                .remove(testWorker);
        }
    }

    private Cube getCube(Connection connection, String cubeName) {
        for (Cube cube
            : connection.getCatalogReader().withLocus().getCubes())
        {
            if (cube.getName().equals(cubeName)) {
                return cube;
            }
        }
        return null;
    }
}
