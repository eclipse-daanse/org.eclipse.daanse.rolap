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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.junit.jupiter.api.Test;

/**
 * Guards the corpus of {@link AllDiscoveredCatalogsTest}: every discovered
 * {@code CatalogTestInstance} must load and supply mapping + check suite, and
 * the count must not drop below {@link #EXPECTED_MINIMUM}. New instance in
 * rolap.mapping? Add its artifact to testkit/core/pom.xml and raise the
 * minimum. 7 instances are parked there with documented defects (search
 * "parked").
 */
class DiscoveredCoverageTest {

    /** 90 registered in rolap.mapping minus the 7 parked. */
    private static final int EXPECTED_MINIMUM = 83;

    @Test
    void allInstancesLoadAndAreFullyWired() {
        List<CatalogTestInstance> instances = new ArrayList<>();
        ServiceLoader.load(CatalogTestInstance.class).forEach(instances::add);

        assertTrue(instances.size() >= EXPECTED_MINIMUM,
                () -> "Discovered only " + instances.size() + " CatalogTestInstances, expected at least "
                        + EXPECTED_MINIMUM + ". Did a testkit/core/pom.xml dependency go missing?\nDiscovered: "
                        + instances.stream().map(i -> i.getClass().getName()).sorted().toList());

        for (CatalogTestInstance instance : instances) {
            assertNotNull(instance.name(), () -> instance.getClass().getName() + " has no name");
            assertNotNull(instance.mappingSupplier(),
                    () -> instance.name() + " has no mappingSupplier — catalog is never built");
            assertNotNull(instance.checkSuiteSupplier(),
                    () -> instance.name() + " has no checkSuiteSupplier — its checks are silently skipped");
        }
    }
}
