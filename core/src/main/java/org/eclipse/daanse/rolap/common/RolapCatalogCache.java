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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.daanse.olap.api.CatalogCache;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.util.ByteString;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.mapping.api.model.CatalogMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

public class RolapCatalogCache implements CatalogCache {

    static final Logger LOGGER = LoggerFactory.getLogger(RolapCatalogCache.class);

    private static record CatalogEntry(RolapCatalog catalog, Duration timeout) {
        // This record is used to store catalog entries in the cache together with there timeout duration
    }

    private final Cache<CacheKey, CatalogEntry> innerCache = Caffeine.newBuilder()
            .expireAfter(new Expiry<CacheKey, CatalogEntry>() {
                @Override
                public long expireAfterCreate(CacheKey key, CatalogEntry entry, long currentTime) {
                    return entry.timeout.toNanos();
                }

                @Override
                public long expireAfterUpdate(CacheKey key, CatalogEntry entry, long currentTime,
                        long currentDuration) {
                    return entry.timeout.toNanos();
                }

                @Override
                public long expireAfterRead(CacheKey key, CatalogEntry entry, long currentTime, long currentDuration) {
                    return entry.timeout.toNanos();
                }
            }).build();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private RolapContext context;

    public RolapCatalogCache(RolapContext context) {
        this.context = context;
    }

    public RolapCatalog getOrCreateCatalog(CatalogMapping catalogMapping, final ConnectionProps connectionProps,
            final Optional<String> oSessionId) {

        final boolean useSchemaPool = connectionProps.useSchemaPool();
        final CatalogContentKey catalogContentKey = CatalogContentKey.create(catalogMapping);
        final ConnectionKey connectionKey = ConnectionKey.of(context.getDataSource(), oSessionId.orElse(null));
        final CacheKey key = new CacheKey(catalogContentKey, connectionKey);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("getOrCreateSchema" + key.toString());
        }

        // Use the schema pool unless "UseSchemaPool" is explicitly false.
        if (useSchemaPool) {
            return getFromCacheByKey(context, connectionProps, key);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("create (no pool): " + key);
        }
        RolapCatalog schema = createRolapCatalog(context, connectionProps, key);
        return schema;

    }

    // is extracted and made package-local for testing purposes
    RolapCatalog createRolapCatalog(RolapContext context, ConnectionProps connectionProps, CacheKey key) {
        return new RolapCatalog(key, connectionProps, context);
    }

    private RolapCatalog getFromCacheByKey(RolapContext context, ConnectionProps connectionProps, CacheKey key) {
        Duration timeOut = connectionProps.pinSchemaTimeout();
        RolapCatalog catalog = lookUp(key, timeOut);
        if (catalog != null) {
            return catalog;
        }

        lock.writeLock().lock();
        try {
            // We need to check once again, now under
            // write lock's protection, because it is possible,
            // that another thread has already replaced old ref
            // with a new one, having the same key.
            // If the condition were not checked, then this thread
            // would remove the newborn schema
            CatalogEntry entry = innerCache.getIfPresent(key);
            if (entry != null) {
                catalog = entry.catalog;
                // Reset timeout by putting the catalog back with the same timeout
                innerCache.put(key, new CatalogEntry(catalog, timeOut));
                return catalog;
            }

            catalog = createRolapCatalog(context, connectionProps, key);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("create: " + catalog);
            }
            putSchemaIntoPool(catalog, null, timeOut);
            return catalog;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private RolapCatalog lookUp(CacheKey key, Duration timeOut) {
        lock.readLock().lock();
        try {
            CatalogEntry entry = innerCache.getIfPresent(key);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("get(key={}) returned {}", key, entry != null ? entry.catalog : null);
            }

            if (entry != null) {
                // Reset timeout by putting the catalog back with the same timeout
                innerCache.put(key, new CatalogEntry(entry.catalog, timeOut));
                return entry.catalog;
            }
        } finally {
            lock.readLock().unlock();
        }

        return null;
    }

    /**
     * Adds schema to the pool. Attention! This method is not doing any synchronization internally and
     * relies on the assumption that it is invoked inside a critical section
     *
     * @param rolapCatalog catalog to be stored
     * @param md5Bytes     md5 hash, can be null
     * @param pinTimeout   timeout mark
     */
    private void putSchemaIntoPool(final RolapCatalog rolapCatalog, final ByteString md5Bytes, Duration timeOut) {
        final CatalogEntry entry = new CatalogEntry(rolapCatalog, timeOut);

        innerCache.put(rolapCatalog.getKey(), entry);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("put: schema={}, key={}, checksum={}, map-size={}", rolapCatalog, rolapCatalog.getKey(),
                    md5Bytes, innerCache.estimatedSize());
        }
    }

    public void remove(RolapCatalog schema) {
        if (schema != null) {
            if (RolapCatalog.LOGGER.isDebugEnabled()) {
                RolapCatalog.LOGGER.debug(new StringBuilder("Pool.remove: schema \"").append(schema.getName())
                        .append("\" and datasource object").toString());
            }
            remove(schema.getKey());
        }
    }

    private void remove(CacheKey key) {
        lock.writeLock().lock();
        RolapCatalog schema = null;
        try {
            CatalogEntry entry = innerCache.getIfPresent(key);
            if (entry != null) {
                schema = entry.catalog;
            }
            innerCache.invalidate(key);
        } finally {
            lock.writeLock().unlock();
        }

        if (schema != null) {
            schema.finalCleanUp();
        }
    }

    public void clear() {
        if (RolapCatalog.LOGGER.isDebugEnabled()) {
            RolapCatalog.LOGGER.debug("Pool.clear: clearing all RolapCatalogs");
        }
        List<RolapCatalog> schemas = getRolapCatalogs();
        innerCache.invalidateAll();

        schemas.forEach(s -> s.finalCleanUp());
    }

    public List<RolapCatalog> getRolapCatalogs() {
        lock.readLock().lock();
        try {
            return innerCache.asMap().values().parallelStream().filter(Objects::nonNull).map(entry -> entry.catalog)
                    .filter(Objects::nonNull).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    boolean contains(RolapCatalog rolapSchema) {
        lock.readLock().lock();
        try {
            return innerCache.getIfPresent(rolapSchema.getKey()) != null;
        } finally {
            lock.readLock().unlock();
        }
    }

}
