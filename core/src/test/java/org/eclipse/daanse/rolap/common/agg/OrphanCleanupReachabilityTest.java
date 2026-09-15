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

import java.lang.ref.WeakReference;
import java.util.Map;

import org.eclipse.daanse.olap.common.MapContextConfig;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.junit.jupiter.api.Test;

/**
 * The orphan-cleanup runnable is held by a Cleaner registered ON the
 * context. Any strong path from the runnable back to the manager (and
 * through it to the context) keeps the context reachable forever - the
 * safety net could then never fire, and every dropped context became a
 * permanent leak. The predecessor captured the whole manager; this
 * proves the runnable pins neither manager nor context.
 */
class OrphanCleanupReachabilityTest {

    @Test
    void theOrphanRunnablePinsNeitherManagerNorContext() throws Exception {
        AbstractRolapContext context = mock(AbstractRolapContext.class);
        when(context.getConfig()).thenReturn(new MapContextConfig(() -> Map.of()));
        AggregationManager manager = new AggregationManager(context);

        Runnable cleanup = manager.orphanCleanup();
        WeakReference<AggregationManager> managerRef = new WeakReference<>(manager);
        WeakReference<AbstractRolapContext> contextRef = new WeakReference<>(context);

        manager.shutdown();
        manager = null;
        context = null;
        // flush Mockito's thread-local retention of the last-touched mock
        mock(Runnable.class).run();

        for (int i = 0; i < 100
                && (managerRef.get() != null || contextRef.get() != null); i++) {
            System.gc();
            Thread.sleep(10);
        }
        assertThat(managerRef.get())
            .as("the cleanup runnable must not pin the manager").isNull();
        assertThat(contextRef.get())
            .as("the cleanup runnable must not pin the context").isNull();

        // and it still runs against the captured leaf resources
        cleanup.run();
    }
}
