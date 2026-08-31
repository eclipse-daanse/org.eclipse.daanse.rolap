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
package org.eclipse.daanse.rolap.testkit.assertions;

import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.api.connection.Connection;

/**
 * Flushes a connection's schema cache mid-test, so a query that runs right after picks up a
 * schema/config change instead of a cached plan.
 *
 * <p>
 * Replaces the legacy {@code TestUtil.flushSchemaCache(Connection)} static helper - same
 * behavior, new home. A no-op if {@code connection} is null or has no {@link CacheControl}.
 */
public final class FlushSchemaCacheModifier {

    private FlushSchemaCacheModifier() {
    }

    /** Flushes {@code connection}'s schema cache; does nothing if there isn't one to flush. */
    public static synchronized void flushSchemaCache(Connection connection) {
        if (connection == null) {
            return;
        }
        CacheControl cc = connection.getCacheControl(null);
        if (cc == null) {
            return;
        }
        cc.flushSchemaCache();
    }
}
