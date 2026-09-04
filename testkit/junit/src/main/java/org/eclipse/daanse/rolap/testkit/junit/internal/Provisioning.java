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

import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;

/** Facade for the extension class; keeps the worker classes package-private. */
public final class Provisioning {

    private Provisioning() {
    }

    /**
     * Provisions (or reuses) the database and opens a managed context over it.
     *
     * <p>A {@link DbScope#PER_TEST} database is released as soon as this one
     * {@link ManagedContext} closes — {@code isolationKey} is unique to this
     * call, so nothing else can still be using it. {@link DbScope#PER_CLASS}
     * is released separately, at class end (see {@code RolapContextExtension};
     * one context can close mid-class when {@code ContextScope.PER_TEST}
     * shares the class's database across several of them).
     */
    public static ManagedContext openManaged(RolapFixture fixture, String isolationKey, Map<String, Object> config) {
        ActiveDatabase database = DatabaseProvisioner.provision(fixture, isolationKey);
        Runnable releaseDatabase = fixture.dbScope() == DbScope.PER_TEST
                ? () -> DatabaseProvisioner.release(isolationKey)
                : null;
        return ManagedContext.open(fixture, database, config, releaseDatabase);
    }

    /** Releases {@code isolationKey}'s database now; see {@link DatabaseProvisioner#release}. */
    public static void release(String isolationKey) {
        DatabaseProvisioner.release(isolationKey);
    }

    /** Coerces one {@code @RolapConfig} value to its declared type. */
    public static Object coerce(String key, String value, Class<?> type) {
        return ConfigCoercion.coerce(key, value, type);
    }
}
