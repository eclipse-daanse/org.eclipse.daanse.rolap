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
 * Self-contained fixture for {@link NonCollapsedAggTest}: the "foo"/"foo2"
 * cubes with non-collapsed levels in agg tables, built from scratch by
 * {@link NonCollapsedAggTestModifierEmf} -- no dependency on the FoodMart
 * catalog or its data.
 */
public class NonCollapsedAggTestInstance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.NonCollapsedAggTest";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        Catalog base = CatalogFactory.eINSTANCE.createCatalog();
        base.setName("NonCollapsedAggTest");
        // AggTableManager resolves the fact table and any candidate aggregate
        // tables through the CWM Schema reachable from the catalog mapping;
        // NonCollapsedAggTestModifierEmf attaches its synthetic tables to this
        // one.
        Schema databaseSchema = RelationalFactory.eINSTANCE.createSchema();
        base.getImportedElement().add(databaseSchema);
        return new NonCollapsedAggTestModifierEmf(base);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> m = new LinkedHashMap<>();
        m.put("line", getClass().getResource("noncollapsedaggtest/data/line.csv"));
        m.put("tenant", getClass().getResource("noncollapsedaggtest/data/tenant.csv"));
        m.put("line_tenant", getClass().getResource("noncollapsedaggtest/data/line_tenant.csv"));
        m.put("line_class", getClass().getResource("noncollapsedaggtest/data/line_class.csv"));
        m.put("line_line_class", getClass().getResource("noncollapsedaggtest/data/line_line_class.csv"));
        m.put("distributor", getClass().getResource("noncollapsedaggtest/data/distributor.csv"));
        m.put("line_class_distributor", getClass().getResource("noncollapsedaggtest/data/line_class_distributor.csv"));
        m.put("network", getClass().getResource("noncollapsedaggtest/data/network.csv"));
        m.put("line_class_network", getClass().getResource("noncollapsedaggtest/data/line_class_network.csv"));
        m.put("foo_fact", getClass().getResource("noncollapsedaggtest/data/foo_fact.csv"));
        m.put("agg_tenant", getClass().getResource("noncollapsedaggtest/data/agg_tenant.csv"));
        m.put("agg_line_class", getClass().getResource("noncollapsedaggtest/data/agg_line_class.csv"));
        m.put("agg_10_foo_fact", getClass().getResource("noncollapsedaggtest/data/agg_10_foo_fact.csv"));
        return m;
    }
}
