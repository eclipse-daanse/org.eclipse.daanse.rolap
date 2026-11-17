/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.rolap.common.catalog;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import org.eclipse.daanse.olap.api.cache.CatalogCache;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.ConnectionKey;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.eclipse.daanse.rolap.common.catalog.CatalogContentKey;

/**
 * Thread-safe pool of RolapCatalog instances on one Caffeine cache.
 *
 * <p>
 * Design: ONE tier, deliberately simple. The loader builds a catalog
 * atomically inside Caffeine's per-key compute (so an invalidate of the
 * same key blocks until the build published, and a build that starts
 * after a flush passed the key is by construction a post-flush catalog).
 * Every removal - explicit flush, size eviction, expiry - runs the
 * same-thread removal listener, which tears the catalog down via
 * {@link RolapCatalog#finalCleanUp()} (idempotent). An evicted or expired
 * catalog is therefore REBUILT on its next request; there is no second
 * "parked" tier. A retrospective audit removed that tier: keeping an
 * evicted catalog revivable required a multi-round lock-free validation
 * protocol whose entire payoff was saving one rebuild after a rare
 * memory event, while the live tier's {@code softValues()} already
 * yields to the GC under pressure.
 * </p>
 *
 * <p>
 * Accepted residual (the {@code flushSchemaCache} contract for pooled
 * catalogs): a flush that lands after a catalog was handed out destroys
 * it while the caller still holds it.
 * </p>
 *
 * <ul>
 * <li>Variable expiration from {@code daanse:cache.timeout} or the
 * connection's pin timeout; non-positive pins forever</li>
 * <li>Composite key: catalog content identity plus connection identity
 * (data source, session, aggregate-scan scope)</li>
 * <li>{@code recordStats} feeds {@code CacheStatsReport}</li>
 * </ul>
 *
 * @see CatalogCache
 * @see RolapCatalog
 */
public class RolapCatalogCache implements CatalogCache {

    static final Logger LOGGER = LoggerFactory.getLogger(RolapCatalogCache.class);

    /**
     * Upper bound on the number of pooled catalogs. Beyond this, Caffeine
     * evicts the least-valuable entry; the removal listener tears it down
     * and the next request rebuilds it. Actively used catalogs are
     * protected by Caffeine's frequency-based policy and by the sliding
     * expiry (every read resets the timeout). This is a memory-safety
     * bound, not a tuning target.
     */
    private static final int MAX_CACHED_CATALOGS = 100;

    /**
     * Cache entry combining a RolapCatalog with its individual timeout duration.
     *
     * @param catalog the cached catalog instance
     * @param timeout the duration after which this catalog should expire
     */
    private static record CatalogCacheValue(RolapCatalog catalog, Duration timeout) {
    }

    /**
     * The underlying Caffeine cache with variable expiration and teardown
     * on every removal.
     */
    private final Cache<RolapCatalogKey, CatalogCacheValue> cache = Caffeine.newBuilder()
            // soft values let the GC reclaim the wrapper under memory
            // pressure; a collected entry vanishes silently (the listener
            // skips null values) and the next request rebuilds the catalog
            // - the GC Cleaner below reaps its lingering segment index
            .softValues()
            // maintenance and removal notifications on the CALLING thread:
            // clear() then tears catalogs down strictly inside
            // invalidateAll(), so a flush is deterministic - no async
            // notification can act after the flush returned
            .executor(Runnable::run)
            // bound the pool so many catalog/session keys cannot grow it
            // without limit
            .maximumSize(MAX_CACHED_CATALOGS)
            // hit/miss/load stats for diagnosability (see getCacheStats())
            .recordStats()
            .expireAfter(new Expiry<RolapCatalogKey, CatalogCacheValue>() {
                @Override
                public long expireAfterCreate(RolapCatalogKey key, CatalogCacheValue value, long currentTime) {
                    return nanosFor(value.timeout);
                }

                @Override
                public long expireAfterUpdate(RolapCatalogKey key, CatalogCacheValue value, long currentTime,
                        long currentDuration) {
                    return nanosFor(value.timeout);
                }

                @Override
                public long expireAfterRead(RolapCatalogKey key, CatalogCacheValue value, long currentTime,
                        long currentDuration) {
                    return nanosFor(value.timeout);
                }
            }).removalListener((RemovalListener<RolapCatalogKey, CatalogCacheValue>) (key, value, cause) -> {
                if (value != null && value.catalog != null) {
                    LOGGER.debug("Removing catalog '{}' due to removal cause: {}", key, cause);
                    // every removal tears down: an evicted or expired
                    // catalog is rebuilt on the next request instead of
                    // being kept revivable (finalCleanUp is idempotent).
                    // EVICTION causes run the teardown OFF the calling
                    // thread: with the same-thread executor an unrelated
                    // reader's maintenance would otherwise stall inside
                    // finalCleanUp (one deadline-bounded segment flush per
                    // cube plus a connection close). EXPLICIT removals
                    // stay inline - clear()'s determinism (everything torn
                    // down when it returns) depends on it.
                    if (cause.wasEvicted()) {
                        // the deferred task re-checks the key: an eviction
                        // followed by a same-key rebuild races the daemon,
                        // and the OLD catalog's flushSegments is KEY-scoped
                        // - run late it would wipe the successor's freshly
                        // primed index and the persistent warm-start data.
                        // With a successor present only the instance side
                        // is torn down; the segments stay valid for it
                        // (same key = same content fingerprint). The check
                        // NARROWS, it does not eliminate: holdsKey cannot
                        // see a rebuild still inside the per-key compute
                        // (the CHM reservation node reads as absent, same
                        // blindness DropIndexAction documents), so a
                        // successor built during the full teardown can
                        // still lose its warm start once - accepted:
                        // conservative deletion, self-healing on the next
                        // miss, no correctness delta.
                        EVICTION_CLEANUP.execute(() -> {
                            if (holdsKey(key)) {
                                value.catalog.finalCleanUpKeepingSegments();
                            } else {
                                value.catalog.finalCleanUp();
                            }
                        });
                    } else {
                        value.catalog.finalCleanUp();
                    }
                }
            }).build();

    /** Runs eviction-caused catalog teardown off the reader threads. */
    private static final java.util.concurrent.ExecutorService EVICTION_CLEANUP =
        java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "daanse.rolap.catalogEvictionCleanup");
            thread.setDaemon(true);
            return thread;
        });

    /** Zero or negative timeout pins the catalog permanently. */
    private static long nanosFor(Duration timeout) {
        return timeout.isNegative() || timeout.isZero() ? Long.MAX_VALUE : timeout.toNanos();
    }

    /** The ROLAP context used for catalog creation. */
    private final RolapContext context;

    /**
     * Creates a new catalog cache with the specified ROLAP context.
     *
     * @param context the ROLAP context used for creating new catalogs
     */
    public RolapCatalogCache(RolapContext context) {
        this.context = context;
        LOGGER.info("Initialized RolapCatalogCache with context: {}", context.getClass().getSimpleName());
    }

    /**
     * Retrieves an existing catalog from cache or creates a new one if not found.
     *
     * <p>
     * This method respects the {@code useSchemaPool} setting from connection properties. When schema
     * pooling is disabled, a new catalog is created for each request without caching.
     * </p>
     *
     * @param catalogMapping  the catalog mapping definition
     * @param connectionProps connection properties containing cache and timeout settings
     * @return the cached or newly created catalog
     */
    public RolapCatalog getOrCreateCatalog(org.eclipse.daanse.rolap.mapping.model.catalog.Catalog catalogMapping, final ConnectionProps connectionProps) {

        final boolean useCatalogCache = connectionProps.useCatalogCache();
        final CatalogContentKey catalogContentKey =
                CatalogContentKey.of(catalogMapping.getName(), context.getContentIdentitySha256());
        final ConnectionKey connectionKey = ConnectionKey.of(context.getDataSource(),
                connectionProps.sessionId().orElse(null),
                connectionProps.aggregateScanSchema().orElse(null),
                connectionProps.aggregateScanCatalog().orElse(null));
        final RolapCatalogKey key = new RolapCatalogKey(catalogContentKey, connectionKey);

        LOGGER.debug("Requesting catalog for key: {}, pooling: {}", key, useCatalogCache);

        // Use the schema pool unless "UseSchemaPool" is explicitly false.
        if (useCatalogCache) {
            return getCatalogFromCache(context, connectionProps, key, timeout(catalogMapping, connectionProps));
        }

        LOGGER.debug("Creating catalog without pooling for key: {}", key);
        RolapCatalog catalog = createCatalog(context, connectionProps, key);
        return catalog;

    }

    /**
     * Creates a new RolapCatalog instance.
     *
     * @param context         the ROLAP context
     * @param connectionProps connection properties
     * @param key             the cache key for the catalog
     * @return a new RolapCatalog instance
     */
    private RolapCatalog createCatalog(RolapContext context, ConnectionProps connectionProps, RolapCatalogKey key) {
        LOGGER.debug("Creating new RolapCatalog for key: {}", key);
        long start = System.nanoTime();
        RolapCatalog catalog = new RolapCatalog(key, connectionProps, context);
        loadCount.increment();
        loadNanos.add(System.nanoTime() - start);
        registerGcCleanup(key, catalog);
        return catalog;
    }

    private final LongAdder loadCount =
        new LongAdder();
    private final LongAdder loadNanos =
        new LongAdder();

    /** Number of full catalog loads this cache performed. */
    public long catalogLoadCount() {
        return loadCount.sum();
    }

    /** Total nanoseconds spent in full catalog loads. */
    public long catalogLoadNanos() {
        return loadNanos.sum();
    }

    private static final Cleaner GC_CLEANER = Cleaner.create();

    /**
     * When the garbage collector reclaims a softly-referenced catalog, its
     * segment index in the shared cache manager would linger forever. This
     * registers a cleanup that drops that index once the catalog object is
     * gone. Memory segment bodies are softly referenced and fall on their
     * own; external stores keep their entries by design (they serve warm
     * starts).
     */
    Cleaner.Cleanable registerGcCleanup(RolapCatalogKey key, RolapCatalog catalog) {
        return GC_CLEANER.register(catalog,
            new DropIndexAction(key, new WeakReference<>(context), new WeakReference<>(this)));
    }

    /** Holds no catalog reference: only the key and weak context/cache. */
    static final class DropIndexAction implements Runnable {
        private final RolapCatalogKey key;
        private final WeakReference<RolapContext> contextRef;
        private final WeakReference<RolapCatalogCache> cacheRef;

        DropIndexAction(RolapCatalogKey key, WeakReference<RolapContext> contextRef,
                WeakReference<RolapCatalogCache> cacheRef) {
            this.key = key;
            this.contextRef = contextRef;
            this.cacheRef = cacheRef;
        }

        @Override
        public void run() {
            // the index map is keyed by RolapCatalogKey, and several catalog
            // INSTANCES can share one key (UseSchemaPool=false constructions,
            // a rebuild after a flush): the death of one instance must not
            // drop the index a LIVE sibling is still using. The probe is
            // blind to a rebuild still inside the per-key compute (the CHM
            // reservation node reads as absent) - that window is
            // self-healing: dropIndex is a plain remove, getIndex lazily
            // recreates, and a catalog mid-build has no loads in flight.
            RolapCatalogCache catalogCache = cacheRef.get();
            if (catalogCache != null && catalogCache.holdsKey(key)) {
                return;
            }
            RolapContext context = contextRef.get();
            if (context instanceof org.eclipse.daanse.olap.core.AbstractBasicContext<?> basic
                    && basic.getAggregationManager()
                        instanceof org.eclipse.daanse.rolap.common.agg.AggregationManager manager
                    && manager.getSegmentCacheManager()
                        instanceof org.eclipse.daanse.rolap.common.agg.SegmentCacheManager cacheManager
                    && cacheManager.getIndexRegistry()
                        instanceof org.eclipse.daanse.rolap.common.agg.SegmentCacheManager.SegmentCacheIndexRegistry registry) {
                registry.dropIndex(key);
            }
        }
    }

    /** daanse:cache.timeout at the catalog (ISO-8601) beats the connection default. */
    static Duration timeout(org.eclipse.daanse.rolap.mapping.model.catalog.Catalog catalogMapping,
            ConnectionProps connectionProps) {
        for (var taggedValue : catalogMapping.getTaggedValue()) {
            if ("daanse:cache.timeout".equals(taggedValue.getTag())) {
                try {
                    return Duration.parse(taggedValue.getValue());
                } catch (RuntimeException e) {
                    LOGGER.warn("tag daanse:cache.timeout carries '{}' (expected ISO-8601); using the connection default",
                            taggedValue.getValue());
                }
            }
        }
        return connectionProps.pinCatalogTimeout();
    }

    /**
     * Retrieves a catalog from cache or creates it if not present, using
     * Caffeine's atomic get-or-create. The timeout is fixed by the first
     * caller that loads the catalog; later callers with a different
     * timeout share the entry as is. A flush of the same key blocks on the
     * per-key compute until the build published, and a build that starts
     * after the flush passed the key is a post-flush catalog by
     * construction - no revive validation is needed because nothing is
     * ever revived.
     *
     * @param context         the ROLAP context
     * @param connectionProps connection properties containing timeout settings
     * @param key             the cache key for the catalog
     * @return the cached or newly created catalog
     */
    private RolapCatalog getCatalogFromCache(RolapContext context, ConnectionProps connectionProps,
            RolapCatalogKey key, Duration timeOut) {

        LOGGER.debug("Attempting to retrieve catalog from cache for key: {}, timeout: {}", key, timeOut);

        CatalogCacheValue entry = cache.get(key, k -> {
            LOGGER.debug("Cache miss - creating new catalog for key: {}", k);
            return new CatalogCacheValue(createCatalog(context, connectionProps, k), timeOut);
        });
        // residual, accepted: a flush that lands after this return destroys
        // the catalog while the caller holds it - that is flushSchemaCache's
        // inherent contract for pooled catalogs handed out before the flush
        return entry.catalog;
    }

    /**
     * Removes a specific catalog from the cache.
     *
     * <p>
     * The catalog's cleanup is handled by the RemovalListener.
     * </p>
     *
     * @param catalog the catalog to remove, null values are ignored
     */
    public void remove(RolapCatalog catalog) {
        if (catalog == null) {
            LOGGER.debug("Attempted to remove null catalog - ignoring");
            return;
        }
        // identity-guarded: several catalog instances can share one key
        // (UseSchemaPool=false constructions) - removing a foreign, pooled,
        // in-use instance because it happens to share the key would tear a
        // live catalog down
        RolapCatalogKey key = catalog.getKey();
        CatalogCacheValue live = cache.policy().getIfPresentQuietly(key);
        if (live != null && live.catalog == catalog) {
            LOGGER.debug("Removing catalog '{}' from cache", catalog.getName());
            cache.invalidate(key);
        } else {
            LOGGER.debug("Catalog '{}' is not the pooled instance under its key - ignoring",
                    catalog.getName());
        }
    }

    /**
     * Removes all catalogs from the cache.
     *
     * <p>
     * All catalog cleanup is handled by the RemovalListener, on this thread
     * (same-thread executor): when this method returns, every pooled
     * catalog is torn down.
     * </p>
     */
    public void clear() {
        long size = cache.estimatedSize();
        LOGGER.info("Clearing cache containing approximately {} catalogs", size);
        cache.invalidateAll();
        LOGGER.debug("Cache cleared successfully");
    }

    /**
     * Returns a list of all currently cached catalogs.
     *
     * <p>
     * This method creates a snapshot of the current cache state. The returned list may not reflect
     * concurrent modifications to the cache.
     * </p>
     *
     * @return an immutable list of all cached catalogs
     */
    @Override
    public List<RolapCatalog> getCachedCatalogs() {
        List<RolapCatalog> catalogs = cache.asMap().values().stream().map(CatalogCacheValue::catalog).toList();
        LOGGER.debug("Retrieved {} catalogs from cache", catalogs.size());
        return Collections.unmodifiableList(catalogs);
    }

    /** Installs a pooled entry directly, pinned - seam for tests. */
    void put(RolapCatalogKey key, RolapCatalog catalog) {
        cache.put(key, new CatalogCacheValue(catalog, Duration.ofSeconds(-1)));
    }

    /** Whether the pool still carries a live catalog under this key. */
    boolean holdsKey(RolapCatalogKey key) {
        // QUIET probe: a plain read (asMap().get included) renews the
        // variable expiry and pollutes the published stats - the GC
        // cleaner must disturb neither
        return cache.policy().getIfPresentQuietly(key) != null;
    }

    /**
     * Returns hit/miss/load statistics for the catalog pool, useful for
     * diagnosing why catalogs are being (re)built at runtime.
     *
     * @return a snapshot of the cache statistics
     */
    public CacheStats getCacheStats() {
        return cache.stats();
    }

}
