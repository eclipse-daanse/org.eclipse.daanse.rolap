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

import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.model.Parameter;
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * EMF version of TestSchemaPropIllegalTypeFailsModifier from ParameterTest.
 * Adds a schema parameter "foo" with type Numeric and default value "1".
 *
 * NOTE: The original XML test tried to use type="Bad type", but in the EMF/POJO version,
 * the type is an enum (InternalDataType), so it's not possible to set an illegal type value.
 * This test was marked as @Disabled with a comment that it will be deleted in the future,
 * since the type validation happens at compile time with enums.
 *
 * Original XML (which would fail):
 * <Parameter name="foo" type="Bad type" defaultValue="1" />
 *
 * EMF/POJO version (valid, since type is an enum):
 * <Parameter name="foo" type="Numeric" defaultValue="1" />
 */
public class TestSchemaPropIllegalTypeFailsModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    // Static parameter
    private static final Parameter PARAMETER_FOO;

    static {
        // Create parameter
        // Note: In EMF, type is InternalDataType enum, so illegal types are not possible
        PARAMETER_FOO = RolapMappingFactory.eINSTANCE.createParameter();
        PARAMETER_FOO.setName("foo");
        PARAMETER_FOO.setDataType(ColumnInternalDataType.NUMERIC);
        PARAMETER_FOO.setDefaultValue("1");
    }

    public TestSchemaPropIllegalTypeFailsModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        this.catalog = EmfUtil.copy((CatalogImpl) baseCatalog);

        // Add the parameter to the catalog
        this.catalog.getParameters().add(PARAMETER_FOO);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
