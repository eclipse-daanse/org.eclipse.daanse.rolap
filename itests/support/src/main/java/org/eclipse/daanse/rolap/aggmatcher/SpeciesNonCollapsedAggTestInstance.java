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

import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * Self-contained fixture for {@link SpeciesNonCollapsedAggTest}: the
 * "Testmart" catalog with a joined "Animal" dimension (Family/Genus/Species)
 * and a non-collapsed ("Genus" level) explicit aggregate table, built from
 * scratch by {@link SpeciesNonCollapsedAggTestModifier} -- no dependency on
 * the FoodMart catalog or its data. See MONDRIAN-1105.
 */
public class SpeciesNonCollapsedAggTestInstance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.SpeciesNonCollapsedAggTest";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        // SpeciesNonCollapsedAggTestModifier builds its "Testmart" catalog
        // entirely from scratch; the base catalog handed in is unused.
        Catalog base = CatalogFactory.eINSTANCE.createCatalog();
        return new SpeciesNonCollapsedAggTestModifier(base);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> m = new LinkedHashMap<>();
        m.put("DIM_FAMILY", getClass().getResource("speciesnoncollapsedaggtest/data/DIM_FAMILY.csv"));
        m.put("DIM_GENUS", getClass().getResource("speciesnoncollapsedaggtest/data/DIM_GENUS.csv"));
        m.put("DIM_SPECIES", getClass().getResource("speciesnoncollapsedaggtest/data/DIM_SPECIES.csv"));
        m.put("species_mart", getClass().getResource("speciesnoncollapsedaggtest/data/species_mart.csv"));
        m.put("AGG_SPECIES_MART", getClass().getResource("speciesnoncollapsedaggtest/data/AGG_SPECIES_MART.csv"));
        return m;
    }
}
