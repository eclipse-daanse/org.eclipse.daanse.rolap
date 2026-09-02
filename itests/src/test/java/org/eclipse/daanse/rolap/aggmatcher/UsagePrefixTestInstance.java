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
 * Self-contained fixture for {@link UsagePrefixTest}: the "Cheques" cube with
 * an explicit aggregate table and two usages of an equally-shaped "store"
 * table (StoreX/StoreY) disambiguated via {@code usagePrefix}, built from
 * scratch by {@link UsagePrefixTestModifierEmf} -- no dependency on the
 * FoodMart catalog or its data. See MONDRIAN-595.
 */
public class UsagePrefixTestInstance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.UsagePrefixTest";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        Catalog base = CatalogFactory.eINSTANCE.createCatalog();
        base.setName("usagePrefixTest");
        // AggTableManager resolves the fact table and the explicit aggregate
        // table through the CWM Schema reachable from the catalog mapping;
        // UsagePrefixTestModifierEmf attaches its synthetic tables to this
        // one.
        Schema databaseSchema = RelationalFactory.eINSTANCE.createSchema();
        base.getImportedElement().add(databaseSchema);
        return new UsagePrefixTestModifierEmf(base);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> m = new LinkedHashMap<>();
        m.put("cheques", getClass().getResource("usageprefixtest/data/cheques.csv"));
        m.put("store_x", getClass().getResource("usageprefixtest/data/store_x.csv"));
        m.put("store_y", getClass().getResource("usageprefixtest/data/store_y.csv"));
        m.put("agg_lp_595_cheques", getClass().getResource("usageprefixtest/data/agg_lp_595_cheques.csv"));
        return m;
    }
}
