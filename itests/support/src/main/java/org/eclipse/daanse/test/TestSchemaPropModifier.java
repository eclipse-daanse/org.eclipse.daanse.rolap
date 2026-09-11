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
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * EMF version of TestSchemaPropModifier from ParameterTest.
 * Adds a schema parameter "prop" with type String and default value "foo bar".
 *
 * <Parameter name="prop" type="String" defaultValue=" 'foo bar' " />
 */
public class TestSchemaPropModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    // Static parameter
    private static final Parameter PARAMETER_PROP;

    static {
        // Create parameter
        PARAMETER_PROP = RolapMappingFactory.eINSTANCE.createParameter();
        PARAMETER_PROP.setName("prop");
        PARAMETER_PROP.setDataType(ColumnInternalDataType.STRING);
        PARAMETER_PROP.setDefaultValue("'foo bar'");
    }

    public TestSchemaPropModifier(Catalog cat) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) cat);
        catalog = (CatalogImpl) copier.get(cat);


        // Add the parameter to the catalog
        this.catalog.getParameters().add(PARAMETER_PROP);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
