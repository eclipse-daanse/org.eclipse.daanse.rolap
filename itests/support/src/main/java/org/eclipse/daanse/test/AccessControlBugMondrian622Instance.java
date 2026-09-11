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
package org.eclipse.daanse.test;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.cwm.testkit.api.DatabaseSupplier;
import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/** Form-A fixture for {@link AccessControlTest#testBugMondrian622}, backed by {@link AccessControlBugMondrian622Modifier}. */
public class AccessControlBugMondrian622Instance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.AccessControlTest.testBugMondrian622";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        Catalog catalog = new CatalogSupplier().get();
        return new AccessControlBugMondrian622Modifier(catalog);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        return new FoodmartTestInstance().dataSupplier().csvResources();
    }

    @Override
    public DatabaseSupplier databaseSupplier() {
        return new FoodmartDatabaseSupplier();
    }
}
