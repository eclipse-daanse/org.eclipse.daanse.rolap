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
 * Self-contained fixture for {@link MultipleColsInTupleAggTest}: the "Fact"
 * cube with levels that contain multiple columns and are collapsed in the
 * agg table, built from scratch by {@link MultipleColsInTupleAggTestModifierEmf}
 * -- no dependency on the FoodMart catalog or its data.
 */
public class MultipleColsInTupleAggTestInstance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.MultipleColsInTupleAggTest";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        Catalog base = CatalogFactory.eINSTANCE.createCatalog();
        base.setName("MultipleColsInTupleAggTest");
        // AggTableManager resolves the fact table and any candidate aggregate
        // tables through the CWM Schema reachable from the catalog mapping;
        // MultipleColsInTupleAggTestModifierEmf attaches its synthetic tables
        // to this one.
        Schema databaseSchema = RelationalFactory.eINSTANCE.createSchema();
        base.getImportedElement().add(databaseSchema);
        return new MultipleColsInTupleAggTestModifierEmf(base);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> m = new LinkedHashMap<>();
        m.put("store_csv", getClass().getResource("multiplecolsintupleaggtest/data/store_csv.csv"));
        m.put("cat", getClass().getResource("multiplecolsintupleaggtest/data/cat.csv"));
        m.put("product_cat", getClass().getResource("multiplecolsintupleaggtest/data/product_cat.csv"));
        m.put("product_csv", getClass().getResource("multiplecolsintupleaggtest/data/product_csv.csv"));
        m.put("fact", getClass().getResource("multiplecolsintupleaggtest/data/fact.csv"));
        m.put("test_lp_xxx_fact", getClass().getResource("multiplecolsintupleaggtest/data/test_lp_xxx_fact.csv"));
        m.put("test_lp_xx2_fact", getClass().getResource("multiplecolsintupleaggtest/data/test_lp_xx2_fact.csv"));
        return m;
    }
}
