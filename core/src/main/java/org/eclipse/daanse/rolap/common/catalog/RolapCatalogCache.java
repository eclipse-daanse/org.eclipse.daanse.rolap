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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

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
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

/**
 * Thread-safe cache implementation for RolapCatalog instances using Caffeine cache.
 * 
 * <p>
 * This cache provides efficient storage and retrieval of ROLAP catalogs with automatic cleanup and
 * configurable expiration. Key features include:
 * </p>
 * 
 * <ul>
 * <li>Thread-safe operations using Caffeine's built-in concurrency control</li>
 * <li>Automatic expiration based on individual catalog timeout settings</li>
 * <li>Automatic cleanup of resources via RemovalListener</li>
 * <li>Optional catalog pooling to reuse catalogs across connections</li>
 * </ul>
 * 
 * <p>
 * The cache uses a composite key consisting of catalog content and connection information to ensure
 * proper isolation between different catalogs and data sources.
 * </p>
 * 
 * 
 * <p>
 * Expired cache elements are stored in a soft cache to allow for garbage collection, if needed. The
 * Cache lookup will first check the soft cache before creating a new catalog.
 * </p>
 * 
 * @see CatalogCache
 * @see RolapCatalog
 */
public class RolapCatalogCache implements CatalogCache {

    static final Logger LOGGER = LoggerFactory.getLogger(RolapCatalogCache.class);

    /**
     * Upper bound on the number of live (tier-1) catalogs. Beyond this,
     * Caffeine evicts the least-valuable entry; its RemovalListener parks it
     * into the soft second tier (so it can still be resurrected while memory
     * allows) and flushes its segments. Without a bound the tier-1 cache could
     * grow unboundedly across many distinct catalog/session keys. Actively used
     * catalogs are protected from eviction by Caffeine's frequency-based policy
     * and by the sliding expiry (every read resets the timeout). This is a
     * memory-safety bound, not a tuning target.
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

    private final Cache<RolapCatalogKey, RolapCatalog> softCache = Caffeine.newBuilder().softValues()
            .removalListener((RemovalListener<RolapCatalogKey, RolapCatalog>) (key, value, cause) -> {
                LOGGER.debug("Cleaning up catalog from softCache '{}' due to removal cause: {}", key, cause);
            }).build();

    /**
     * The underlying Caffeine cache with custom expiration and cleanup logic.
     * 
     * <ul>
     * <li>Variable expiration based on individual catalog timeout settings</li>
     * <li>Automatic resource cleanup via RemovalListener</li>
     * </ul>
     */
    private final Cache<RolapCatalogKey, CatalogCacheValue> cache = Caffeine.newBuilder()
            // Deliver maintenance and removal notifications on the CALLING thread. The removal
            // listener below parks EXPIRED entries into softCache; with the default asynchronous
            // executor that parking races clear(): cache.invalidateAll() schedules the EXPIRED
            // notification, softCache.invalidateAll() runs first, and the notification then
            // re-inserts the just-flushed catalog into softCache — the next connection resurrects
            // the OLD catalog (with all its per-catalog caches, e.g. the native-set tuple cache)
            // instead of rebuilding, silently defeating CacheControl.flushSchemaCache(). With a
            // same-thread executor the parking happens inside cache.invalidateAll(), strictly
            // before softCache.invalidateAll(), so a flush deterministically drops the catalog.
            .executor(Runnable::run)
            // Bound the live tier so many catalog/session keys cannot grow it
            // without limit; size-evicted entries are parked into softCache by
            // the removal listener below (which makes the RemovalCause.SIZE
            // branch reachable).
            .maximumSize(MAX_CACHED_CATALOGS)
            // Collect hit/miss/load stats for diagnosability (see getCacheStats()).
            .recordStats()
            .expireAfter(new Expiry<RolapCatalogKey, CatalogCacheValue>() {
                @Override
                public long expireAfterCreate(RolapCatalogKey key, CatalogCacheValue value, long currentTime) {
                    return value.timeout.toNanos();
                }

                @Override
                public long expireAfterUpdate(RolapCatalogKey key, CatalogCacheValue value, long currentTime,
                        long currentDuration) {
                    return value.timeout.toNanos();
                }

                @Override
                public long expireAfterRead(RolapCatalogKey key, CatalogCacheValue value, long currentTime,
                        long currentDuration) {
                    return value.timeout.toNanos();
                }
            }).removalListener((RemovalListener<RolapCatalogKey, CatalogCacheValue>) (key, value, cause) -> {
                if (value != null && value.catalog != null) {

                    if (cause == RemovalCause.EXPIRED || cause == RemovalCause.SIZE) {
                        softCache.put(key, value.catalog);
                    }
                    LOGGER.debug("Cleaning up catalog '{}' due to removal cause: {}", key, cause);
                    value.catalog.finalCleanUp();
                }
            }).build();

    /** The ROLAP context used for catalog creation. */
    private RolapContext context;

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
        final RolapCatalogContentKey catalogContentKey = RolapCatalogContentKey.create(catalogMapping);
        final ConnectionKey connectionKey = ConnectionKey.of(context.getDataSource(), connectionProps.sessionId().orElse(null));
        final RolapCatalogKey key = new RolapCatalogKey(catalogContentKey, connectionKey);

        LOGGER.debug("Requesting catalog for key: {}, pooling: {}", key, useCatalogCache);

        // Use the schema pool unless "UseSchemaPool" is explicitly false.
        if (useCatalogCache) {
            return getCatalogFromCache(context, connectionProps, key);
        }

        LOGGER.debug("Creating catalog without pooling for key: {}", key);
        RolapCatalog catalog = createCatalog(context, connectionProps, key);
        return catalog;

    }

    /**
     * Creates a new RolapCatalog instance.
     * 
     * 
     * @param context         the ROLAP context
     * @param connectionProps connection properties
     * @param key             the cache key for the catalog
     * @return a new RolapCatalog instance
     */
    private RolapCatalog createCatalog(RolapContext context, ConnectionProps connectionProps, RolapCatalogKey key) {
        LOGGER.debug("Creating new RolapCatalog for key: {}", key);
        return new RolapCatalog(key, connectionProps, context);
    }

    /**
     * Retrieves a catalog from cache or creates it if not present.
     * 
     * <p>
     * This method uses Caffeine's atomic get-or-create functionality to ensure thread-safe catalog
     * creation. It also updates the timeout on each access.
     * </p>
     * 
     * @param context         the ROLAP context
     * @param connectionProps connection properties containing timeout settings
     * @param key             the cache key for the catalog
     * @return the cached or newly created catalog
     */
    private RolapCatalog getCatalogFromCache(RolapContext context, ConnectionProps connectionProps,
            RolapCatalogKey key) {
        Duration timeOut = connectionProps.pinCatalogTimeout();

        LOGGER.debug("Attempting to retrieve catalog from cache for key: {}, timeout: {}", key, timeOut);

        CatalogCacheValue entry = cache.get(key, k -> {

            RolapCatalog catalog = softCache.getIfPresent(key);

            if (catalog != null) {
                LOGGER.debug("Cache hit - found existing catalog for key: {}", k);

                return new CatalogCacheValue(catalog, timeOut);
            }

            LOGGER.debug("Cache miss - creating new catalog for key: {}", k);
            catalog = createCatalog(context, connectionProps, k);
            return new CatalogCacheValue(catalog, timeOut);
        });

        return entry.catalog;
    }

    /**
     * Removes a specific catalog from the cache.
     * 
     * <p>
     * The catalog's cleanup will be handled automatically by the RemovalListener.
     * </p>
     * 
     * @param catalog the catalog to remove, null values are ignored
     */
    public void remove(RolapCatalog catalog) {
        if (catalog != null) {
            LOGGER.debug("Removing catalog '{}' from cache", catalog.getName());
            invalidateBothTiers(catalog.getKey());
        } else {
            LOGGER.debug("Attempted to remove null catalog - ignoring");
        }
    }

    /**
     * Removes all catalogs from the cache.
     * 
     * <p>
     * All catalog cleanup will be handled automatically by the RemovalListener.
     * </p>
     */
    public void clear() {
        long size = cache.estimatedSize();
        LOGGER.info("Clearing cache containing approximately {} catalogs", size);
        invalidateAllTiers();
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
    public List<RolapCatalog> getCachedCatalogs() {
        List<RolapCatalog> catalogs = cache.asMap().values().stream().map(CatalogCacheValue::catalog).toList();
        LOGGER.debug("Retrieved {} catalogs from cache", catalogs.size());

        List<RolapCatalog> catalogsSoft = softCache.asMap().values().stream().filter(catalog -> catalog != null)
                .toList();
        return Collections.unmodifiableList(Stream.concat(catalogs.stream(), catalogsSoft.stream()).toList());
    }

    /**
     * The single entry point for invalidating one catalog. Both tiers must
     * always be invalidated together, tier-1 first: its same-thread removal
     * listener parks EXPIRED/SIZE entries into softCache, so invalidating
     * softCache strictly afterwards guarantees a flushed catalog cannot be
     * resurrected by the next lookup.
     *
     * @param key the catalog key to invalidate in both tiers
     */
    private void invalidateBothTiers(RolapCatalogKey key) {
        cache.invalidate(key);
        softCache.invalidate(key);
    }

    /** Invalidates every catalog in both tiers (see {@link #invalidateBothTiers}). */
    private void invalidateAllTiers() {
        cache.invalidateAll();
        softCache.invalidateAll();
    }

    /**
     * Returns hit/miss/load statistics for the live (tier-1) catalog cache,
     * useful for diagnosing why catalogs are being (re)built at runtime.
     *
     * @return a snapshot of the tier-1 cache statistics
     */
    public CacheStats getCacheStats() {
        return cache.stats();
    }

}
