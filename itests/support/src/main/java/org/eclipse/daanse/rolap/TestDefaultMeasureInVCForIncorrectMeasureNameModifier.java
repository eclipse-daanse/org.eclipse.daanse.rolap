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
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.CalculatedMember;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreUtil;
/**
 * EMF version of TestDefaultMeasureInVCForIncorrectMeasureNameModifier from
 * VirtualCubeTest. Creates a virtual cube "Sales vs Warehouse" without setting
 * a default measure. Tests the behavior when no explicit default measure is
 * set. Uses objects from CatalogSupplier.
 *
 * <VirtualCube name="Sales vs Warehouse" defaultMeasure="Profit Error">
 * <VirtualCubeDimension name="Product"/>
 * <VirtualCubeMeasure cubeName="Warehouse" name="[Measures].[Warehouse
 * Sales]"/>
 * <VirtualCubeMeasure cubeName="Sales" name="[Measures].[Unit Sales]"/>
 * <VirtualCubeMeasure cubeName="Sales" name="[Measures].[Profit]"/>
 * </VirtualCube>
 */
public class TestDefaultMeasureInVCForIncorrectMeasureNameModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    public TestDefaultMeasureInVCForIncorrectMeasureNameModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);
        // Static virtual cube
        VirtualCube VIRTUAL_CUBE_SALES_VS_WAREHOUSE;

        // Create virtual cube
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE = CubeFactory.eINSTANCE.createVirtualCube();
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.setName("Sales vs Warehouse");

        // Note: No default measure set (commented out in original:
        // .withDefaultMeasure("Profit Error"))

        // Add dimension connector
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_PRODUCT));

        // Add referenced measures
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_WAREHOUSE_SALES));
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_UNIT_SALES));

        // Add referenced calculated members
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getReferencedCalculatedMembers().add((CalculatedMember) copier.get(CatalogSupplier.CALCULATED_MEMBER_PROFIT));

        // Add the virtual cube to the catalog
        this.catalog.getImportedElement().add(VIRTUAL_CUBE_SALES_VS_WAREHOUSE);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
