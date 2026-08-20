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
package org.eclipse.daanse.rolap.testkit.junit.api;

/**
 * Lifetime of the loaded database instance. Orthogonal to
 * {@link ContextScope}: the database holds the data, the context holds
 * engine state (schema cache, config).
 *
 * <p>Isolation keys carry a {@code rolap-junit:} prefix so they never collide
 * with keys other jdbc-testkit consumers use.
 *
 * <p>Not the same as {@code org.eclipse.daanse.rolap.testkit.api.LoadScope},
 * which only governs how often the CSV load re-runs against an
 * already-activated database — DbScope governs which database instance a
 * test class talks to in the first place.
 */
public enum DbScope {

    /** Fresh database per test method. Expensive; for data-mutating tests. */
    PER_TEST,

    /** One database per test class (isolation key = class name). */
    PER_CLASS,

    /**
     * Default: one database per identical fixture for the whole JVM run —
     * classes using the same catalog/data share one loaded database, like the
     * legacy JVM-cached FoodMart. Read-only tests want this.
     */
    PER_RUNTIME,

    /**
     * Shared across classes under an explicit {@code scopeName}. No teardown:
     * the provider has no per-database release API; the load-once guard
     * prevents reloading.
     */
    NAMED
}
