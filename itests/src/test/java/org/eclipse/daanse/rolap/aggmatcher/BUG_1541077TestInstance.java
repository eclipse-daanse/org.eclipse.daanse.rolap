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
 * Self-contained fixture for {@link BUG_1541077Test}: a "Cheques" cube with
 * an explicit aggregate table (agg_lp_xxx_cheques), built from scratch by
 * {@link BUG_1541077Modifier} -- no dependency on the FoodMart catalog or its
 * data.
 */
public class BUG_1541077TestInstance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.BUG_1541077Test";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        Catalog base = CatalogFactory.eINSTANCE.createCatalog();
        base.setName("BUG_1541077Test");
        // AggTableManager resolves the fact table and any candidate aggregate
        // tables through the CWM Schema reachable from the catalog mapping;
        // BUG_1541077Modifier attaches its synthetic tables to this one.
        Schema databaseSchema = RelationalFactory.eINSTANCE.createSchema();
        base.getImportedElement().add(databaseSchema);
        return new BUG_1541077Modifier(base);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> m = new LinkedHashMap<>();
        m.put("cheques", getClass().getResource("bug1541077test/data/cheques.csv"));
        m.put("store_x", getClass().getResource("bug1541077test/data/store_x.csv"));
        m.put("product_x", getClass().getResource("bug1541077test/data/product_x.csv"));
        m.put("agg_lp_xxx_cheques", getClass().getResource("bug1541077test/data/agg_lp_xxx_cheques.csv"));
        return m;
    }
}
