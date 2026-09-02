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
package org.eclipse.daanse.rolap;

import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.VirtualCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.BaseMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreUtil;
/**
 * EMF version of TestOrdinalColumnModifier from VirtualCubeTest. Creates a
 * virtual cube "Sales vs HR" that tests ordinalColumn property in dimensions.
 * Uses objects from CatalogSupplier.
 *
 * <VirtualCube name="Sales vs HR"> <VirtualCubeDimension name="Store"/>
 * <VirtualCubeDimension cubeName="HR" name="Position"/>
 * <VirtualCubeMeasure cubeName="HR" name="[Measures].[Org Salary]"/>
 * </VirtualCube>
 */
public class TestOrdinalColumnModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    public TestOrdinalColumnModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);

        // Static virtual cube
        VirtualCube VIRTUAL_CUBE_SALES_VS_HR;


        // Create virtual cube
        VIRTUAL_CUBE_SALES_VS_HR = CubeFactory.eINSTANCE.createVirtualCube();
        VIRTUAL_CUBE_SALES_VS_HR.setName("Sales vs HR");

        // Add dimension connectors
        VIRTUAL_CUBE_SALES_VS_HR.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_HR_STORE));
        VIRTUAL_CUBE_SALES_VS_HR.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_HR_POSITION));

        // Add referenced measures
        VIRTUAL_CUBE_SALES_VS_HR.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_ORG_SALARY));

        // Add the virtual cube to the catalog
        this.catalog.getImportedElement().add(VIRTUAL_CUBE_SALES_VS_HR);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
