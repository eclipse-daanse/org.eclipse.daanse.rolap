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

import static org.eclipse.daanse.rolap.mapping.model.provider.util.Expressions.mdx;

import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.VirtualCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.BaseMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.CalculatedMember;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreUtil;
/**
 * EMF version of TestMemberVisibilityModifier from VirtualCubeTest.
 * Creates a virtual cube "Warehouse and Sales Member Visibility" with measures and calculated members
 * that have different visibility settings.
 * Uses objects from CatalogSupplier.
 *
 * <VirtualCube name="Warehouse and Sales Member Visibility">
 *   <VirtualCubeDimension cubeName="Sales" name="Customers"/>
 *   <VirtualCubeDimension name="Time"/>
 *   <VirtualCubeMeasure cubeName="Sales" name="[Measures].[Sales Count]" visible="true" />
 *   <VirtualCubeMeasure cubeName="Sales" name="[Measures].[Store Cost]" visible="false" />
 *   <VirtualCubeMeasure cubeName="Sales" name="[Measures].[Store Sales]"/>
 *   <VirtualCubeMeasure cubeName="Sales" name="[Measures].[Profit last Period]" visible="true" />
 *   <VirtualCubeMeasure cubeName="Warehouse" name="[Measures].[Units Shipped]" visible="false" />
 *   <VirtualCubeMeasure cubeName="Warehouse" name="[Measures].[Average Warehouse Sale]" visible="false" />
 *   <CalculatedMember name="Profit" dimension="Measures" visible="false" >
 *     <Formula>[Measures].[Store Sales] - [Measures].[Store Cost]</Formula>
 *   </CalculatedMember>
 * </VirtualCube>
 */
public class TestMemberVisibilityModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    public TestMemberVisibilityModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);

        // Static dimension connectors
        //DimensionConnector CONNECTOR_CUSTOMERS;
        //DimensionConnector CONNECTOR_TIME;

        // Static calculated member
        CalculatedMember CALCULATED_MEMBER_PROFIT;

        // Static virtual cube
        VirtualCube VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY;

        // Create dimension connector for Customers (references Sales cube)
        //CONNECTOR_CUSTOMERS = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_CUSTOMERS.setOverrideDimensionName("Customers");
        //CONNECTOR_CUSTOMERS.setPhysicalCube(CatalogSupplier.CUBE_SALES);

        // Create dimension connector for Time (references dimension directly)
        //CONNECTOR_TIME = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_TIME.setOverrideDimensionName("Time");
        //CONNECTOR_TIME.setDimension(CatalogSupplier.DIMENSION_TIME);

        // Create calculated member Profit with visible=false
        CALCULATED_MEMBER_PROFIT = LevelFactory.eINSTANCE.createCalculatedMember();
        CALCULATED_MEMBER_PROFIT.setName("Profit");
        CALCULATED_MEMBER_PROFIT.setVisible(false);
        CALCULATED_MEMBER_PROFIT.setFormula(mdx("[Measures].[Store Sales] - [Measures].[Store Cost]"));

        // Create virtual cube
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY = CubeFactory.eINSTANCE.createVirtualCube();
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY.setName("Warehouse and Sales Member Visibility");

        // Add dimension connectors
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_CUSTOMER));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_TIME));

        // Add referenced measures
        // Note: In original XML, visibility was controlled via visible="true/false" attribute
        // In EMF, we reference the measures directly from their source cubes
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_SALES_COUNT)); // visible="true" in original
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_STORE_COST));    // visible="false" in original
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_STORE_SALES));   // no visibility attribute (default true)
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_UNITS_SHIPPED));  // visible="false" in original

        // Add referenced calculated members
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY.getReferencedCalculatedMembers().add((CalculatedMember) copier.get(CatalogSupplier.CALCULATED_MEMBER_PROFIT_LAST_PERIOD));// visible="true" in original
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY.getReferencedCalculatedMembers().add((CalculatedMember) copier.get(CatalogSupplier.CALCULATED_MEMBER_AVERAGE_WAREHOUSE_SALE));// visible="false" in original

        // Add local calculated member
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY.getCalculatedMembers().add(
            CALCULATED_MEMBER_PROFIT  // visible="false"
        );

        // Add the virtual cube to the catalog
        this.catalog.getImportedElement().add(VIRTUAL_CUBE_WAREHOUSE_AND_SALES_MEMBER_VISIBILITY);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
