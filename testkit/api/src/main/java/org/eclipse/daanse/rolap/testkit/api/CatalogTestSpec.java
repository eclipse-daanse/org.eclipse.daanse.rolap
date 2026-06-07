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

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * Hand-built escape hatch for bespoke tests that don't fit the published
 * {@link CatalogTestInstance} discovery pattern. Caller provides an
 * explicit CSV map — no auto-detection.
 */
public record CatalogTestSpec(
        String name,
        CatalogMappingSupplier mappingSupplier,
        OlapCheckSuiteSupplier checkSuiteSupplier,
        Map<String, URL> csvResources) {
}
