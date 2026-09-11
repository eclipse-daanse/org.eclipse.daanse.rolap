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

import java.util.List;

import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.VirtualCube;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
/**
 * EMF version of TestVirtualCubeMeasureInvalidCubeNameModifier from VirtualCubeTest.
 * Creates a virtual cube "Sales vs Warehouse" with measures from different cubes.
 * Uses objects from CatalogSupplier.
 *
 * NOTE: This test is @Disabled in the original test file with comment:
 * "cube name not a string. we use reference to cube. we not able to set 'Bad cube'.
 *  this test will delete in future"
 *
 * <VirtualCube name="Sales vs Warehouse">
 *   <VirtualCubeDimension name="Product"/>
 *   <VirtualCubeMeasure cubeName="Warehouse" name="[Measures].[Warehouse Sales]"/>
 *   <VirtualCubeMeasure cubeName="Bad cube" name="[Measures].[Unit Sales]"/>
 * </VirtualCube>
 */
public class TestVirtualCubeMeasureInvalidCubeNameModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    // Static dimension connector
    private static final DimensionConnector CONNECTOR_PRODUCT;

    // Static virtual cube
    private static final VirtualCube VIRTUAL_CUBE_SALES_VS_WAREHOUSE;

    static {
        // Create dimension connector for Product (no dimension reference, just override name)
        CONNECTOR_PRODUCT = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_PRODUCT.setOverrideDimensionName("Product");

        // Create virtual cube
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE = CubeFactory.eINSTANCE.createVirtualCube();
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.setName("Sales vs Warehouse");

        // Add dimension connector
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getDimensionConnectors().add(CONNECTOR_PRODUCT);

        // Add referenced measures
        // Note: In POJO version, there was a commented line: .cubeName("Bad cube")
        // Since we use references to actual measures, we cannot set an invalid cube name
        VIRTUAL_CUBE_SALES_VS_WAREHOUSE.getReferencedMeasures().addAll(
            List.of(
                CatalogSupplier.MEASURE_WAREHOUSE_SALES,
                CatalogSupplier.MEASURE_UNIT_SALES
            )
        );
    }

    public TestVirtualCubeMeasureInvalidCubeNameModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        this.catalog = EmfUtil.copy((CatalogImpl) baseCatalog);

        // Add the virtual cube to the catalog
        this.catalog.getImportedElement().add(VIRTUAL_CUBE_SALES_VS_WAREHOUSE);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
