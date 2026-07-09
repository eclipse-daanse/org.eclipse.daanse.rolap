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
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.common.cache;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.daanse.rolap.util.FauxMemoryMonitor;
import org.eclipse.daanse.rolap.util.MemoryMonitor;
import org.eclipse.daanse.rolap.util.NotificationMemoryMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process-wide coupling of {@link SmartCache}s to the JVM memory monitor.
 *
 * <p>
 * Registered caches are cleared when free memory drops below a threshold. This
 * gives the adaptive "shed under memory pressure" behaviour that the former
 * soft-reference {@link SoftSmartCache} relied on, but with strong,
 * equals-based keys and explicit, testable control instead of opaque GC
 * behaviour. Caffeine cannot express the soft keys the original
 * {@code ReferenceMap(SOFT, SOFT)} used, and its {@code weakKeys()} compares by
 * identity; a strong bounded cache plus this registry avoids both problems.
 * </p>
 *
 * <p>
 * Caches are held through {@link WeakReference}, so a discarded cache needs no
 * explicit deregistration — the single shared listener prunes dead references
 * as it runs. The listener clears whole caches (coarse granularity); a miss
 * simply recomputes the entry.
 * </p>
 *
 * <p>
 * The clearing runs on a JMX/GC thread. {@link SmartCache#clear()} takes its
 * own write lock, so clearing registered caches from that thread is safe. This
 * is only valid because {@code SmartCache} implementations are internally
 * synchronised — it must not be used for thread-confined caches.
 * </p>
 */
final class MemoryPressureCacheRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryPressureCacheRegistry.class);

    /**
     * Percentage of used memory at which registered caches are cleared. A high
     * value keeps the caches effective in normal operation and only sheds when
     * the heap is genuinely tight, mirroring soft-reference timing.
     */
    private static final int THRESHOLD_PERCENT = 90;

    private static final CopyOnWriteArrayList<WeakReference<SmartCache<?, ?>>> CACHES = new CopyOnWriteArrayList<>();

    private static volatile boolean listenerInstalled;

    private MemoryPressureCacheRegistry() {
    }

    /**
     * Registers a cache to be cleared under memory pressure. Safe to call from
     * a cache constructor; only a weak reference is retained.
     *
     * @param cache the cache to shed under pressure
     */
    static void register(SmartCache<?, ?> cache) {
        CACHES.add(new WeakReference<>(cache));
        ensureListener();
    }

    private static synchronized void ensureListener() {
        if (listenerInstalled) {
            return;
        }
        listenerInstalled = true;
        MemoryMonitor monitor;
        try {
            monitor = new NotificationMemoryMonitor();
        } catch (RuntimeException t) {
            // JMX memory pools unavailable: fall back to no-op. Each cache's
            // backstop maximumSize still bounds memory without adaptive shedding.
            LOGGER.info("Memory-pressure cache clearing unavailable; relying on backstop size bound", t);
            monitor = new FauxMemoryMonitor();
        }
        monitor.addListener(MemoryPressureCacheRegistry::clearAll, THRESHOLD_PERCENT);
    }

    private static void clearAll(long used, long max) {
        int cleared = 0;
        for (Iterator<WeakReference<SmartCache<?, ?>>> it = CACHES.iterator(); it.hasNext();) {
            SmartCache<?, ?> cache = it.next().get();
            if (cache != null) {
                cache.clear();
                cleared++;
            }
        }
        CACHES.removeIf(ref -> ref.get() == null);
        LOGGER.debug("Cleared {} smart cache(s) at {}% memory usage", cleared,
                max == 0 ? 0 : (100 * used / max));
    }
}
