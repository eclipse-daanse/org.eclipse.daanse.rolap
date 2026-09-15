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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.api.cache.CacheControl.CellRegion;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.common.MapContextConfig;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.eclipse.daanse.rolap.common.CacheControlImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The administrative flush walks EVERY session overlay. One overlay's
 * failure must neither skip the remaining overlays nor displace the
 * recorded shared failure - before the per-overlay try, an
 * AssertionError out of the first overlay aborted the loop and was
 * thrown INSTEAD of the shared store failure the administrator needed
 * to see.
 */
class OverlayFlushAggregationTest {

    private AbstractRolapContext context;
    private AggregationManager aggMgr;
    // strong refs: sessionOverlays is weak-keyed
    private Connection failingConnection;
    private Connection survivingConnection;

    @BeforeEach
    void setUp() {
        context = mock(AbstractRolapContext.class);
        when(context.getConfig()).thenReturn(new MapContextConfig(() -> Map.of()));
        aggMgr = new AggregationManager(context);
    }

    @AfterEach
    void tearDown() {
        aggMgr.shutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    void oneFailingOverlayNeitherSkipsTheRestNorMasksTheSharedFailure()
            throws Exception {
        SegmentCacheManager failing = mock(SegmentCacheManager.class);
        when(failing.execute(any(SegmentCacheManager.FlushCommand.class)))
            .thenThrow(new AssertionError("overlay-1 actor died"));
        SegmentCacheManager surviving = mock(SegmentCacheManager.class);
        doReturn(new SegmentCacheManager.FlushResult(List.of()))
            .when(surviving).execute(any(SegmentCacheManager.FlushCommand.class));

        failingConnection = mock(Connection.class);
        survivingConnection = mock(Connection.class);
        Field field = AggregationManager.class.getDeclaredField("sessionOverlays");
        field.setAccessible(true);
        Map<Object, Object> overlays = (Map<Object, Object>) field.get(aggMgr);
        synchronized (overlays) {
            overlays.put(failingConnection, failing);
            overlays.put(survivingConnection, surviving);
        }

        Connection flushingConnection = mock(Connection.class);
        doReturn(context).when(flushingConnection).getContext();
        CacheControl cacheControl = aggMgr.getCacheControl(flushingConnection, null);
        Method flushNonUnion = findFlushNonUnion(cacheControl.getClass());
        flushNonUnion.setAccessible(true);

        // a mock region makes the SHARED flush fail deterministically -
        // exactly the "shared stores kept the region" case
        CellRegion region = mock(CellRegion.class);
        Throwable thrown = catchThrowable(() ->
            ExecutionContext.where(ExecutionImpl.NONE.asContext(), () -> {
                try {
                    flushNonUnion.invoke(cacheControl, region,
                        CacheControlImpl.FlushDeadline.standard());
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause() instanceof RuntimeException r ? r
                        : new IllegalStateException(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
                return null;
            }));

        // the loop reached the second overlay despite the first one's Error
        verify(surviving).execute(any(SegmentCacheManager.FlushCommand.class));
        // the shared failure stays primary; the overlay Error rides along
        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown.getSuppressed())
            .as("the overlay AssertionError must ride along as suppressed")
            .anySatisfy(s -> assertThat(s).isInstanceOf(AssertionError.class));
    }

    private static Method findFlushNonUnion(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals("flushNonUnion")
                        && method.getParameterCount() == 2) {
                    return method;
                }
            }
        }
        throw new IllegalStateException("flushNonUnion not found");
    }
}
