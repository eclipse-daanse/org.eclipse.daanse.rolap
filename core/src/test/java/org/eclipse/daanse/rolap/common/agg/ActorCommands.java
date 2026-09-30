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

import java.util.function.Supplier;

import org.eclipse.daanse.olap.api.cache.CacheCommand;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;

/**
 * Test helper: anonymous {@link CacheCommand}s for probing or draining
 * the cache manager's actor - the tests repeated the same
 * null-execution-context stub six times.
 */
final class ActorCommands {

    private ActorCommands() {
    }

    /** Runs the probe on the actor and returns its result. */
    static <T> CacheCommand<T> onActor(Supplier<T> probe) {
        return new CacheCommand<>() {
            @Override
            public ExecutionContext getExecutionContext() {
                return null;
            }

            @Override
            public T call() {
                return probe.get();
            }
        };
    }

    /** A no-op command: executing it drains everything queued before. */
    static CacheCommand<Void> drain() {
        return onActor(() -> null);
    }
}
