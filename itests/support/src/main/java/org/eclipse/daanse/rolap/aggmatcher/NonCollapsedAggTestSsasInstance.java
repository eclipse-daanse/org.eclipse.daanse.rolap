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
package org.eclipse.daanse.rolap.aggmatcher;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * Self-contained fixture for {@link NonCollapsedAggTest#testSsasCompatNamingInAgg}:
 * the "testSsas" cube built from scratch by
 * {@link TestSsasCompatNamingInAggModifier} over the same {@code foo_fact}
 * / {@code line} / {@code tenant} / ... CSV data as {@link NonCollapsedAggTestInstance}
 * -- no dependency on the FoodMart catalog or its data.
 */
public class NonCollapsedAggTestSsasInstance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.NonCollapsedAggTest.testSsasCompatNamingInAgg";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        Catalog base = CatalogFactory.eINSTANCE.createCatalog();
        base.setName("NonCollapsedAggTest.testSsasCompatNamingInAgg");
        Schema databaseSchema = RelationalFactory.eINSTANCE.createSchema();
        base.getImportedElement().add(databaseSchema);
        return new TestSsasCompatNamingInAggModifier(base);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        return new NonCollapsedAggTestInstance().csvResources();
    }
}
