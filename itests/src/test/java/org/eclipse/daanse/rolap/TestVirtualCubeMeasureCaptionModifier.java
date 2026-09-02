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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.VirtualCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.SumMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.StandardDimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ExplicitHierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.HierarchyFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
/**
 * EMF version of TestVirtualCubeMeasureCaptionModifier from VirtualCubeTest.
 * Creates a physical cube "TestStore" and a virtual cube "VirtualTestStore"
 * to test bug MONDRIAN-352 (Caption is not set on RolapVirtualCubeMesure).
 * Uses objects from CatalogSupplier.
 *
 * <Cube name="TestStore">
 *   <Table name="store"/>
 *   <Dimension name="HCB" caption="Has coffee bar caption">
 *     <Hierarchy hasAll="true">
 *       <Level name="Has coffee bar" column="coffee_bar" uniqueMembers="true" type="Boolean"/>
 *     </Hierarchy>
 *   </Dimension>
 *   <Measure name="Store Sqft" caption="Store Sqft Caption" column="store_sqft" aggregator="sum" formatString="#,###"/>
 * </Cube>
 * <VirtualCube name="VirtualTestStore">
 *   <VirtualCubeDimension cubeName="TestStore" name="HCB"/>
 *   <VirtualCubeMeasure cubeName="TestStore" name="[Measures].[Store Sqft]"/>
 * </VirtualCube>
 */
public class TestVirtualCubeMeasureCaptionModifier implements CatalogMappingSupplier {

    private final Catalog catalog;


    public TestVirtualCubeMeasureCaptionModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);


        // Static table query
        TableSource TABLE_QUERY_STORE;

        // Static level
        Level LEVEL_HAS_COFFEE_BAR;

        // Static hierarchy
        ExplicitHierarchy HIERARCHY_HCB;

        // Static dimension
        StandardDimension DIMENSION_HCB;

        // Static measure
        SumMeasure MEASURE_STORE_SQFT_LOCAL;

        // Static measure group
        MeasureGroup MEASURE_GROUP_TEST_STORE;

        // Static dimension connector for physical cube
        DimensionConnector CONNECTOR_HCB;

        // Static physical cube
        PhysicalCube CUBE_TEST_STORE;

        // Static dimension connector for virtual cube
        //DimensionConnector VC_CONNECTOR_HCB;

        // Static virtual cube
        VirtualCube VIRTUAL_CUBE_TEST_STORE;

        // Create table query
        TABLE_QUERY_STORE = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_STORE.setTable((Table) copier.get(CatalogSupplier.TABLE_STORE));

        // Create level "Has coffee bar"
        LEVEL_HAS_COFFEE_BAR = LevelFactory.eINSTANCE.createLevel();
        LEVEL_HAS_COFFEE_BAR.setName("Has coffee bar");
        LEVEL_HAS_COFFEE_BAR.setColumn((Column) copier.get(CatalogSupplier.COLUMN_COFFEE_BAR_STORE));
        LEVEL_HAS_COFFEE_BAR.setUniqueMembers(true);
        LEVEL_HAS_COFFEE_BAR.setColumnType(ColumnInternalDataType.BOOLEAN);

        // Create hierarchy
        HIERARCHY_HCB = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_HCB.setHasAll(true);
        HIERARCHY_HCB.setPrimaryKey((Column) copier.get(CatalogSupplier.COLUMN_STORE_ID_STORE));
        HIERARCHY_HCB.setSource(TABLE_QUERY_STORE);
        HIERARCHY_HCB.getLevels().add(LEVEL_HAS_COFFEE_BAR);

        // Create dimension HCB
        DIMENSION_HCB = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_HCB.setName("HCB");
        DIMENSION_HCB.getHierarchies().add(HIERARCHY_HCB);

        // Create measure "Store Sqft"
        MEASURE_STORE_SQFT_LOCAL = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_STORE_SQFT_LOCAL.setName("Store Sqft");
        MEASURE_STORE_SQFT_LOCAL.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_SQFT_STORE));
        MEASURE_STORE_SQFT_LOCAL.setFormatString("#,###");

        // Create measure group
        MEASURE_GROUP_TEST_STORE = CubeFactory.eINSTANCE.createMeasureGroup();
        MEASURE_GROUP_TEST_STORE.getMeasures().add(MEASURE_STORE_SQFT_LOCAL);

        // Create dimension connector for physical cube
        CONNECTOR_HCB = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_HCB.setOverrideDimensionName("HCB");
        CONNECTOR_HCB.setDimension(DIMENSION_HCB);

        // Create physical cube "TestStore"
        CUBE_TEST_STORE = CubeFactory.eINSTANCE.createPhysicalCube();
        CUBE_TEST_STORE.setName("TestStore");
        CUBE_TEST_STORE.setSource(TABLE_QUERY_STORE);
        CUBE_TEST_STORE.getDimensionConnectors().add(CONNECTOR_HCB);
        CUBE_TEST_STORE.getMeasureGroups().add(MEASURE_GROUP_TEST_STORE);

        // Create dimension connector for virtual cube
        //VC_CONNECTOR_HCB = DimensionFactory.eINSTANCE.createDimensionConnector();
        //VC_CONNECTOR_HCB.setOverrideDimensionName("HCB");
        //VC_CONNECTOR_HCB.setPhysicalCube(CUBE_TEST_STORE);

        // Create virtual cube "VirtualTestStore"
        VIRTUAL_CUBE_TEST_STORE = CubeFactory.eINSTANCE.createVirtualCube();
        VIRTUAL_CUBE_TEST_STORE.setName("VirtualTestStore");
        VIRTUAL_CUBE_TEST_STORE.getDimensionConnectors().add(CONNECTOR_HCB);
        VIRTUAL_CUBE_TEST_STORE.getReferencedMeasures().add(MEASURE_STORE_SQFT_LOCAL);

        // Add the physical cube and virtual cube to the catalog
        this.catalog.getImportedElement().add(CUBE_TEST_STORE);
        this.catalog.getImportedElement().add(VIRTUAL_CUBE_TEST_STORE);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
