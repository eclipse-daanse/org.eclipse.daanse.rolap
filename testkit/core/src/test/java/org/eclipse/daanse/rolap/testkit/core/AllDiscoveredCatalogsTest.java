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
package org.eclipse.daanse.rolap.testkit.core;

import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Smoke test: discovers all {@code CatalogTestInstance} services on the test
 * classpath and runs them against the database selected by
 * {@code DAANSE_TEST_DB} (default: H2).
 */
class AllDiscoveredCatalogsTest {

    @TestFactory
    Stream<DynamicTest> discovered() {
        return CatalogTestHarness.discoveredTests();
    }
}
