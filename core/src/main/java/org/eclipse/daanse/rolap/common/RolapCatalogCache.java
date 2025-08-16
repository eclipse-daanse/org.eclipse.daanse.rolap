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
package org.eclipse.daanse.rolap.common;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.olap.api.CatalogCache;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.mapping.api.model.CatalogMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;

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
     * @param oSessionId      optional session identifier for connection isolation
     * @return the cached or newly created catalog
     */
    public RolapCatalog getOrCreateCatalog(CatalogMapping catalogMapping, final ConnectionProps connectionProps,
            final Optional<String> oSessionId) {

        final boolean useCatalogCache = connectionProps.useCatalogCache();
        final RolapCatalogContentKey catalogContentKey = RolapCatalogContentKey.create(catalogMapping);
        final ConnectionKey connectionKey = ConnectionKey.of(context.getDataSource(), oSessionId.orElse(null));
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
        Duration timeOut = connectionProps.pinSchemaTimeout();

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
            cache.invalidate(catalog.getKey());
            softCache.invalidate(catalog.getKey());

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
        cache.invalidateAll();
        softCache.invalidateAll();
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

}
