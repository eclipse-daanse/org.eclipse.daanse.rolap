/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde and others
 * Copyright (C) 2005-2019 Hitachi Vantara and others
 * All Rights Reserved.
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
package org.eclipse.daanse.rolap.common;

import java.lang.ref.Reference;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.daanse.olap.api.CatalogCache;
import org.eclipse.daanse.olap.api.ConnectionProps;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.mapping.api.model.CatalogMapping;
import org.eclipse.daanse.rolap.util.reference.expiring.ExpiringReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.daanse.olap.util.ByteString;

public class RolapCatalogCache implements CatalogCache {

	static final Logger LOGGER = LoggerFactory.getLogger(RolapCatalogCache.class);

	private final Map<CacheKey, ExpiringReference<RolapCatalog>> innerCache = new HashMap<>();

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
			ExpiringReference<RolapCatalog> expiringRefToRolapCatalog = innerCache.get(key);
			if (expiringRefToRolapCatalog != null) {
				catalog = expiringRefToRolapCatalog.getCatalogAndResetTimeout(timeOut);
				if (catalog == null) {
					innerCache.remove(key);
				} else {
					return catalog;
				}
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

	private <T> RolapCatalog lookUp(T key, Duration timeOut) {
		lock.readLock().lock();
		try {
			ExpiringReference<RolapCatalog> expiringReference = innerCache.get(key);
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("get(key={}) returned {}", key, toString(expiringReference));
			}

			if (expiringReference != null) {
				RolapCatalog schema = expiringReference.getCatalogAndResetTimeout(timeOut);
				if (schema != null) {
					return schema;
				}
			}
		} finally {
			lock.readLock().unlock();
		}

		return null;
	}

	/**
	 * Adds schema to the pool. Attention! This method is not doing
	 * any synchronization internally and relies on the assumption that it is
	 * invoked inside a critical section
	 *
	 * @param schema     schema to be stored
	 * @param md5Bytes   md5 hash, can be null
	 * @param pinTimeout timeout mark
	 */
	private void putSchemaIntoPool(final RolapCatalog schema, final ByteString md5Bytes, Duration timeOut) {
		final ExpiringReference<RolapCatalog> reference = new ExpiringReference<>(schema, timeOut);

		innerCache.put(schema.getKey(), reference);

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("put: schema={}, key={}, checksum={}, map-size={}", schema, schema.getKey(), md5Bytes,
					innerCache.size());
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
			Reference<RolapCatalog> ref = innerCache.get(key);
			if (ref != null) {
				schema = ref.get();
			}
			innerCache.remove(key);
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
		innerCache.clear();

		schemas.forEach(s -> s.finalCleanUp());
	}

	public List<RolapCatalog> getRolapCatalogs() {
		lock.readLock().lock();
		try {

			List<RolapCatalog> list = innerCache.values().parallelStream().filter(Objects::nonNull)
					.map(ExpiringReference::get).filter(Objects::nonNull).toList();

			return list;
		} finally {
			lock.readLock().unlock();
		}
	}

	boolean contains(RolapCatalog rolapSchema) {
		lock.readLock().lock();
		try {
			return innerCache.containsKey(rolapSchema.getKey());
		} finally {
			lock.readLock().unlock();
		}
	}

	private static <T> String toString(Reference<T> ref) {
		if (ref == null) {
			return "null";
		} else {
			T t = ref.get();
			if (t == null) {
				return "ref(null)";
			} else {
				return new StringBuilder("ref(").append(t).append(", id=")
						.append(Integer.toHexString(System.identityHashCode(t))).append(")").toString();
			}
		}
	}
}
