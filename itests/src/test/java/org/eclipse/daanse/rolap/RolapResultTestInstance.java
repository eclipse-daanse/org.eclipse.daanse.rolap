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
package org.eclipse.daanse.rolap;

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
 * Self-contained fixture for {@link RolapResultTest}: four synthetic tables
 * (D1, D2, FT1, FT2) and four cubes (FTAll, FT1, FT2, FT2Extra) built from
 * scratch by {@link RolapResultTestModifierEmf} — no dependency on the
 * FoodMart catalog or its data.
 */
public class RolapResultTestInstance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.RolapResultTest";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        Catalog base = CatalogFactory.eINSTANCE.createCatalog();
        base.setName("RolapResultTest");
        // AggTableManager.loadRolapStarAggregates requires at least one CWM Schema
        // reachable from the catalog mapping (Packages.available(..., Schema.class));
        // RolapResultTestModifierEmf attaches its synthetic tables to this one.
        Schema databaseSchema = RelationalFactory.eINSTANCE.createSchema();
        base.getImportedElement().add(databaseSchema);
        return new RolapResultTestModifierEmf(base);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> m = new LinkedHashMap<>();
        m.put("D1", getClass().getResource("rolapresulttest/data/D1.csv"));
        m.put("D2", getClass().getResource("rolapresulttest/data/D2.csv"));
        m.put("FT1", getClass().getResource("rolapresulttest/data/FT1.csv"));
        m.put("FT2", getClass().getResource("rolapresulttest/data/FT2.csv"));
        return m;
    }
}
