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
package org.eclipse.daanse.rolap.agg;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * Self-contained fixture for {@link AggregationOnInvalidRoleTest}: the
 * "mondrian2225" cube with an explicit aggregate table and a "Test" access
 * role whose only member grant names a customer that does not exist in the
 * data, built from scratch by {@link AggregationOnInvalidRoleTestModifierEmf}
 * -- no dependency on the FoodMart catalog or its data.
 */
public class AggregationOnInvalidRoleTestInstance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.AggregationOnInvalidRoleTest";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        Catalog base = CatalogFactory.eINSTANCE.createCatalog();
        base.setName("AggregationOnInvalidRoleTest");
        // AggTableManager resolves the fact table and the explicit aggregate
        // table through the CWM Schema reachable from the catalog mapping;
        // AggregationOnInvalidRoleTestModifierEmf attaches its synthetic
        // tables to this one.
        Schema databaseSchema = RelationalFactory.eINSTANCE.createSchema();
        base.getImportedElement().add(databaseSchema);
        return new AggregationOnInvalidRoleTestModifierEmf(base);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> m = new LinkedHashMap<>();
        m.put("mondrian2225_customer", getClass().getResource("aggregationoninvalidroletest/data/mondrian2225_customer.csv"));
        m.put("mondrian2225_dim", getClass().getResource("aggregationoninvalidroletest/data/mondrian2225_dim.csv"));
        m.put("mondrian2225_fact", getClass().getResource("aggregationoninvalidroletest/data/mondrian2225_fact.csv"));
        m.put("mondrian2225_agg", getClass().getResource("aggregationoninvalidroletest/data/mondrian2225_agg.csv"));
        return m;
    }
}
