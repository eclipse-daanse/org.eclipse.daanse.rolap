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
 * EMF version of TestSchemaPropInvalidDefaultExpFailsModifier from ParameterTest.
 * Adds a schema parameter "Product Current Member" with an invalid default expression.
 *
 * The default value "[Product].DefaultMember.Children(2)" is invalid because
 * Children() doesn't accept numeric arguments.
 *
 * Original XML (type should be Member):
 * <Parameter name="Product Current Member" type="Member"
 *            defaultValue="[Product].DefaultMember.Children(2) " />
 *
 * POJO/EMF version uses NUMERIC type (though this may be a bug in the original modifier).
 */
public class TestSchemaPropInvalidDefaultExpFailsModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    // Static parameter
    private static final Parameter PARAMETER_PRODUCT_CURRENT_MEMBER;

    static {
        // Create parameter with invalid default expression
        PARAMETER_PRODUCT_CURRENT_MEMBER = RolapMappingFactory.eINSTANCE.createParameter();
        PARAMETER_PRODUCT_CURRENT_MEMBER.setName("Product Current Member");
        PARAMETER_PRODUCT_CURRENT_MEMBER.setDataType(ColumnInternalDataType.NUMERIC);
        PARAMETER_PRODUCT_CURRENT_MEMBER.setDefaultValue("[Product].DefaultMember.Children(2)");
    }

    public TestSchemaPropInvalidDefaultExpFailsModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        this.catalog = EmfUtil.copy((CatalogImpl) baseCatalog);

        // Add the parameter to the catalog
        this.catalog.getParameters().add(PARAMETER_PRODUCT_CURRENT_MEMBER);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
