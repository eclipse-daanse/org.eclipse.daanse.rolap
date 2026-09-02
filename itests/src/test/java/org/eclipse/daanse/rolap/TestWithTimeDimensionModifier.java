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

/**
 * EMF version of TestWithTimeDimensionModifier from VirtualCubeTest.
 * Creates a virtual cube "Sales vs Warehouse" with Time and Product dimensions.
 * Uses objects from CatalogSupplier.
 *
 * <VirtualCube name="Sales vs Warehouse">
 *   <VirtualCubeDimension name="Time"/>
 *   <VirtualCubeDimension name="Product"/>
 *   <VirtualCubeMeasure cubeName="Warehouse" name="[Measures].[Warehouse Sales]"/>
 *   <VirtualCubeMeasure cubeName="Sales" name="[Measures].[Unit Sales]"/>
 *   <CalculatedMember ... name="Profit"/>
 * </VirtualCube>
 */
public class TestWithTimeDimensionModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    public TestWithTimeDimensionModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);


        // Static dimension connectors
        //DimensionConnector CONNECTOR_TIME;
        //DimensionConnector CONNECTOR_PRODUCT;

        // Static virtual cube
        VirtualCube VIRTUAL_CUBE_SALES_VS_WAREHOUSE;

        // Create dimension connector for Time
        //CONNECTOR_TIME = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_TIME.setOverrideDimensionName("Time");
        //CONNECTOR_TIME.setDimension(CatalogSupplier.DIMENSION_TIME);

        // Create dimension connector for Product
        //CONNECTOR_PRODUCT = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_PRODUCT.setOverrideDimensionName("Product");
        //CONNECTOR_PRODUCT.setDimension(CatalogSupplier.DIMENSION_PRODUCT);

        // Create virtual cube
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE = CubeFactory.eINSTANCE.createVirtualCube();
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.setName("Sales vs Warehouse");

        // Add dimension connectors
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_TIME));
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_PRODUCT));

        // Add referenced measures
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_WAREHOUSE_SALES));
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_UNIT_SALES));

        // Add referenced calculated members
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getReferencedCalculatedMembers().add(
                (CalculatedMember) copier.get(CatalogSupplier.CALCULATED_MEMBER_PROFIT)
        );

        // Add the virtual cube to the catalog
        this.catalog.getImportedElement().add(VIRTUAL_CUBE_SALES_VS_WAREHOUSE);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
