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

package org.eclipse.daanse.test;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.monitor.EventBus;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;



/**
 * Cunning tests to discover whether the cache manager is working to spec
 * and is thread-safe.
 *
 * @author Julian Hyde
 */
@RolapContextTest(FoodmartTestInstance.class)
class CacheTest {
    /**
     * Tests that if N queries are executed at the same time, only one segment
     * request will be sent. The query that arrives second should see that there
     * is a pending segment in the aggregation manager, and should wait for
     * that.
     *
     * <p>If the test fails, look at segmentCreateViaSqlCount. If it has
     * increased by more than one between before and after, the clients have not
     * managed to share work. If it has not increased, the cache was probably
     * not flushed correctly.</p>
     */
    @Test
    void testNQueriesWaitingForSameSegmentRepeat(Context<?> foodMartContext)
        throws ExecutionException, InterruptedException
    {
		Connection connection = foodMartContext.getConnectionWithDefaultRole();
        final int parallel = 10;
        final ThreadPoolExecutor executor =
            new ThreadPoolExecutor(
                2, parallel, 1, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(parallel * 2));
        final int repeatCount = 20;
        for (int i = 0; i < repeatCount; i++) {
            checkNQueriesWaitingForSameSegment(
        		connection, executor, parallel, "iteration #" + i + " of " + repeatCount);
        }
        executor.shutdown();
    }

    private void checkNQueriesWaitingForSameSegment(
		Connection connection,
        ThreadPoolExecutor executor,
        int parallel,
        String iteration)
        throws InterruptedException, ExecutionException
    {
        final Context<?> context =
           connection.getContext();
        final CacheControl cacheControl =
        		connection.getCacheControl(null);
        cacheControl.flush(
            cacheControl.createMeasuresRegion(
                getCubeWithName(
                    "Sales",
                    connection.getCatalog().getCubes())));
        Thread.sleep(2000); // wait for flush to propagate
        final EventBus monitor = context.getMonitor();
//        final ServerInfo serverBefore = monitor.getServer();

        final List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < parallel; i++) {
            Callable<Boolean> runnable = new Callable<>() {
                @Override
				public Boolean call() {
                    assertThatQuery(
                		connection,
                        "select [Gender].Children * [Product].Children on 0\n"
                        + "from [Sales]").returnsGrid(
                        "Axis #0:\n"
                        + "{}\n"
                        + "Axis #1:\n"
                        + "{[Gender].[Gender].[F], [Product].[Product].[Drink]}\n"
                        + "{[Gender].[Gender].[F], [Product].[Product].[Food]}\n"
                        + "{[Gender].[Gender].[F], [Product].[Product].[Non-Consumable]}\n"
                        + "{[Gender].[Gender].[M], [Product].[Product].[Drink]}\n"
                        + "{[Gender].[Gender].[M], [Product].[Product].[Food]}\n"
                        + "{[Gender].[Gender].[M], [Product].[Product].[Non-Consumable]}\n"
                        + "Row #0: 12,202\n"
                        + "Row #0: 94,814\n"
                        + "Row #0: 24,542\n"
                        + "Row #0: 12,395\n"
                        + "Row #0: 97,126\n"
                        + "Row #0: 25,694\n");
                    return true;
                }
            };
            futures.add(executor.submit(runnable));
        }
        for (Future<Boolean> future : futures) {
            assertTrue(future.get() == Boolean.TRUE);
        }
//        final ServerInfo serverAfter = monitor.getServer();
//        final String beforeAfter =
//            "before: " + serverBefore + "\n"
//            + "after: " + serverAfter + "\n"
//            + iteration;
//        assertTrue(
//            serverAfter.segmentCreateCount
//            == serverBefore.segmentCreateCount + 1
//            && serverAfter.segmentCreateViaSqlCount
//               == serverBefore.segmentCreateViaSqlCount + 1,
//           beforeAfter);
    }

    private Cube getCubeWithName(String cubeName, List<Cube> cubes) {
        for (Cube cube : cubes) {
            if (cubeName.equals(cube.getName())) {
                return cube;
            }
        }
        return null;
    }
}
