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
 * Self-contained fixture for {@link Checkin_7634Test}: the "Checkin_7634"
 * cube, built from scratch by {@link Checkin_7634Modifier} -- no dependency
 * on the FoodMart catalog or its data.
 */
public class Checkin_7634TestInstance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.Checkin_7634Test";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        Catalog base = CatalogFactory.eINSTANCE.createCatalog();
        base.setName("Checkin_7634Test");
        return new Checkin_7634Modifier(base);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> m = new LinkedHashMap<>();
        m.put("geography7631", getClass().getResource("checkin7634test/data/geography7631.csv"));
        m.put("prod7631", getClass().getResource("checkin7634test/data/prod7631.csv"));
        m.put("table7634", getClass().getResource("checkin7634test/data/table7634.csv"));
        return m;
    }
}
