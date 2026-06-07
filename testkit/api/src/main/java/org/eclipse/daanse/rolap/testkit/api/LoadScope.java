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
package org.eclipse.daanse.rolap.testkit.api;

/** CSV-reload cadence for the harness. Default {@link #PER_CLASS}. */
public enum LoadScope {

    /** Reload CSVs for every emitted {@code DynamicTest}. */
    PER_TEST,

    /** Reload CSVs once per test class. */
    PER_CLASS,

    /** Reload CSVs at most once per JVM. Read-only suites only. */
    PER_JVM,

    /**
     * Reload CSVs at most once per named scope; lifetime is caller-controlled
     * via {@code releaseScope(String)} or {@code TestKitLifecycleExtension}.
     */
    NAMED
}
