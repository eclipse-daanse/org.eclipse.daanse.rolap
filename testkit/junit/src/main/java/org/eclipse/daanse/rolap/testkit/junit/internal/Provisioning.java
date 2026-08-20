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

/** Facade for the extension class; keeps the worker classes package-private. */
public final class Provisioning {

    private Provisioning() {
    }

    /** Provisions (or reuses) the database and opens a managed context over it. */
    public static ManagedContext openManaged(RolapFixture fixture, String isolationKey, Map<String, Object> config) {
        return ManagedContext.open(fixture, DatabaseProvisioner.provision(fixture, isolationKey), config);
    }

    /** Coerces one {@code @RolapConfig} value to its declared type. */
    public static Object coerce(String key, String value, Class<?> type) {
        return ConfigCoercion.coerce(key, value, type);
    }
}
