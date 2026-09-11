/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.test;

import java.util.List;

import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.model.Parameter;
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * EMF version of TestSchemaPropDupFailsModifier from ParameterTest.
 * Adds three schema parameters, two of which have duplicate names to test error handling.
 *
 * <Parameter name="foo" type="Numeric" defaultValue="1" />
 * <Parameter name="bar" type="Numeric" defaultValue="2" />
 * <Parameter name="foo" type="Numeric" defaultValue="3" />
 */
public class TestSchemaPropDupFailsModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    // Static parameters
    private static final Parameter PARAMETER_FOO_1;
    private static final Parameter PARAMETER_BAR;
    private static final Parameter PARAMETER_FOO_2;

    static {
        // Create first parameter "foo"
        PARAMETER_FOO_1 = RolapMappingFactory.eINSTANCE.createParameter();
        PARAMETER_FOO_1.setName("foo");
        PARAMETER_FOO_1.setDataType(ColumnInternalDataType.NUMERIC);
        PARAMETER_FOO_1.setDefaultValue("1");

        // Create parameter "bar"
        PARAMETER_BAR = RolapMappingFactory.eINSTANCE.createParameter();
        PARAMETER_BAR.setName("bar");
        PARAMETER_BAR.setDataType(ColumnInternalDataType.NUMERIC);
        PARAMETER_BAR.setDefaultValue("2");

        // Create second parameter "foo" (duplicate)
        PARAMETER_FOO_2 = RolapMappingFactory.eINSTANCE.createParameter();
        PARAMETER_FOO_2.setName("foo");
        PARAMETER_FOO_2.setDataType(ColumnInternalDataType.NUMERIC);
        PARAMETER_FOO_2.setDefaultValue("3");
    }

    public TestSchemaPropDupFailsModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        this.catalog = EmfUtil.copy((CatalogImpl) baseCatalog);

        // Add all parameters to the catalog (including the duplicate)
        this.catalog.getParameters().addAll(List.of(
            PARAMETER_FOO_1,
            PARAMETER_BAR,
            PARAMETER_FOO_2
        ));
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
