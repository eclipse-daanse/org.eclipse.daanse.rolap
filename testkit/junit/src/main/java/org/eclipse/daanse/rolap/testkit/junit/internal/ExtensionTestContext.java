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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.junit.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.daanse.jdbc.datasource.pools.api.ConnectionPool;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.core.TestContext;
import org.eclipse.daanse.sql.dialect.api.Dialect;

/**
 * {@link TestContext} with a mutable configuration map — the seam for
 * {@code @RolapConfig}. Swaps in a map that {@code getConfigValue} reads
 * lazily, so per-test config values take effect at query time.
 *
 * <p>Also the seam for the CacheProbe EventBus tap: {@code eventBus} on
 * {@code AbstractBasicContext} is protected, reachable only from a subclass.
 */
class ExtensionTestContext extends TestContext {

    private final Map<String, Object> config = new ConcurrentHashMap<>();

    ExtensionTestContext(ConnectionPool connectionPool, Dialect dialect,
            CatalogMappingSupplier catalogMappingSupplier) {
        super(connectionPool, dialect, catalogMappingSupplier);
        updateConfiguration(config);
    }

    void putConfig(String key, Object value) {
        config.put(key, value);
    }
}
