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
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
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
/**
 * EMF version of TestMultipleAllWithInExprModifier from NativeSetEvaluationTest.
 * Creates a cube "3StoreHCube" with AltStore dimension having three hierarchies.
 *
 * <Dimension name="AltStore">
 *   <Hierarchy name="(default)" hasAll="true" allMemberName="All" primaryKey="store_id">
 *     <Table name="store"/>
 *     <Level name="Store Name" column="store_name" uniqueMembers="false"/>
 *     <Level name="Store City" column="store_city" uniqueMembers="false"/>
 *   </Hierarchy>
 *   <Hierarchy name="City" hasAll="true" allMemberName="All" primaryKey="store_id">
 *     <Table name="store"/>
 *     <Level name="Store City" column="store_city" uniqueMembers="false"/>
 *     <Level name="Store Name" column="store_name" uniqueMembers="false"/>
 *   </Hierarchy>
 *   <Hierarchy name="State" hasAll="true" allMemberName="All" primaryKey="store_id">
 *     <Table name="store"/>
 *     <Level name="Store State" column="store_sqft" uniqueMembers="false"/>
 *     <Level name="Store City" column="store_city" uniqueMembers="false"/>
 *     <Level name="Store Name" column="store_name" uniqueMembers="false"/>
 *   </Hierarchy>
 * </Dimension>
 *
 * <Cube name="3StoreHCube">
 *   <Table name="sales_fact_1997"/>
 *   <Dimension name="AltStore" foreignKey="store_id"/> (with 3 hierarchies)
 *   <Dimension name="Time" source="Time" foreignKey="time_id"/>
 *   <Dimension name="Product" source="Product" foreignKey="product_id"/>
 *   <Measure name="Store Sales" column="store_sales" aggregator="sum" formatString="#,###.00"/>
 * </Cube>
 */
public class TestMultipleAllWithInExprModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    // Static levels for default hierarchy
    private static final Level LEVEL_STORE_NAME_1;
    private static final Level LEVEL_STORE_CITY_1;

    // Static levels for City hierarchy
    private static final Level LEVEL_STORE_CITY_2;
    private static final Level LEVEL_STORE_NAME_2;

    // Static levels for State hierarchy
    private static final Level LEVEL_STORE_STATE;
    private static final Level LEVEL_STORE_CITY_3;
    private static final Level LEVEL_STORE_NAME_3;

    // Static table queries
    private static final TableSource TABLE_QUERY_STORE_1;
    private static final TableSource TABLE_QUERY_STORE_2;
    private static final TableSource TABLE_QUERY_STORE_3;

    // Static hierarchies
    private static final ExplicitHierarchy HIERARCHY_DEFAULT;
    private static final ExplicitHierarchy HIERARCHY_CITY;
    private static final ExplicitHierarchy HIERARCHY_STATE;

    // Static AltStore dimension
    private static final StandardDimension DIMENSION_ALT_STORE;

    // Static dimension connectors
    private static final DimensionConnector CONNECTOR_ALT_STORE;
    private static final DimensionConnector CONNECTOR_TIME;
    private static final DimensionConnector CONNECTOR_PRODUCT;

    // Static fact table query
    private static final TableSource TABLE_QUERY_SALES_FACT;

    // Static measure
    private static final SumMeasure MEASURE_STORE_SALES;

    // Static measure group
    private static final MeasureGroup MEASURE_GROUP;

    // Static cube
    private static final PhysicalCube CUBE_3STORE_H_CUBE;

    static {
        // Create default hierarchy levels
        LEVEL_STORE_NAME_1 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_STORE_NAME_1.setName("Store Name");
        LEVEL_STORE_NAME_1.setColumn(CatalogSupplier.COLUMN_STORE_NAME_STORE);
        LEVEL_STORE_NAME_1.setUniqueMembers(false);

        LEVEL_STORE_CITY_1 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_STORE_CITY_1.setName("Store City");
        LEVEL_STORE_CITY_1.setColumn(CatalogSupplier.COLUMN_STORE_CITY_STORE);
        LEVEL_STORE_CITY_1.setUniqueMembers(false);

        TABLE_QUERY_STORE_1 = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_STORE_1.setTable(CatalogSupplier.TABLE_STORE);

        // Create default hierarchy
        HIERARCHY_DEFAULT = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_DEFAULT.setHasAll(true);
        HIERARCHY_DEFAULT.setAllMemberName("All");
        HIERARCHY_DEFAULT.setPrimaryKey(CatalogSupplier.COLUMN_STORE_ID_STORE);
        HIERARCHY_DEFAULT.setSource(TABLE_QUERY_STORE_1);
        HIERARCHY_DEFAULT.getLevels().addAll(List.of(
            LEVEL_STORE_NAME_1,
            LEVEL_STORE_CITY_1
        ));

        // Create City hierarchy levels
        LEVEL_STORE_CITY_2 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_STORE_CITY_2.setName("Store City");
        LEVEL_STORE_CITY_2.setColumn(CatalogSupplier.COLUMN_STORE_CITY_STORE);
        LEVEL_STORE_CITY_2.setUniqueMembers(false);

        LEVEL_STORE_NAME_2 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_STORE_NAME_2.setName("Store Name");
        LEVEL_STORE_NAME_2.setColumn(CatalogSupplier.COLUMN_STORE_NAME_STORE);
        LEVEL_STORE_NAME_2.setUniqueMembers(false);

        TABLE_QUERY_STORE_2 = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_STORE_2.setTable(CatalogSupplier.TABLE_STORE);

        // Create City hierarchy
        HIERARCHY_CITY = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_CITY.setName("City");
        HIERARCHY_CITY.setHasAll(true);
        HIERARCHY_CITY.setAllMemberName("All");
        HIERARCHY_CITY.setPrimaryKey(CatalogSupplier.COLUMN_STORE_ID_STORE);
        HIERARCHY_CITY.setSource(TABLE_QUERY_STORE_2);
        HIERARCHY_CITY.getLevels().addAll(List.of(
            LEVEL_STORE_CITY_2,
            LEVEL_STORE_NAME_2
        ));

        // Create State hierarchy levels
        LEVEL_STORE_STATE = LevelFactory.eINSTANCE.createLevel();
        LEVEL_STORE_STATE.setName("Store State");
        LEVEL_STORE_STATE.setColumn(CatalogSupplier.COLUMN_STORE_SQFT_STORE);
        LEVEL_STORE_STATE.setUniqueMembers(false);

        LEVEL_STORE_CITY_3 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_STORE_CITY_3.setName("Store City");
        LEVEL_STORE_CITY_3.setColumn(CatalogSupplier.COLUMN_STORE_CITY_STORE);
        LEVEL_STORE_CITY_3.setUniqueMembers(false);

        LEVEL_STORE_NAME_3 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_STORE_NAME_3.setName("Store Name");
        LEVEL_STORE_NAME_3.setColumn(CatalogSupplier.COLUMN_STORE_NAME_STORE);
        LEVEL_STORE_NAME_3.setUniqueMembers(false);

        TABLE_QUERY_STORE_3 = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_STORE_3.setTable(CatalogSupplier.TABLE_STORE);

        // Create State hierarchy
        HIERARCHY_STATE = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_STATE.setName("State");
        HIERARCHY_STATE.setHasAll(true);
        HIERARCHY_STATE.setAllMemberName("All");
        HIERARCHY_STATE.setPrimaryKey(CatalogSupplier.COLUMN_STORE_ID_STORE);
        HIERARCHY_STATE.setSource(TABLE_QUERY_STORE_3);
        HIERARCHY_STATE.getLevels().addAll(List.of(
            LEVEL_STORE_STATE,
            LEVEL_STORE_CITY_3,
            LEVEL_STORE_NAME_3
        ));

        // Create AltStore dimension with three hierarchies
        DIMENSION_ALT_STORE = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_ALT_STORE.setName("AltStore");
        DIMENSION_ALT_STORE.getHierarchies().addAll(List.of(
            HIERARCHY_DEFAULT,
            HIERARCHY_CITY,
            HIERARCHY_STATE
        ));

        // Create dimension connector for AltStore
        CONNECTOR_ALT_STORE = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_ALT_STORE.setOverrideDimensionName("AltStore");
        CONNECTOR_ALT_STORE.setDimension(DIMENSION_ALT_STORE);
        CONNECTOR_ALT_STORE.setForeignKey(CatalogSupplier.COLUMN_STORE_ID_SALESFACT);

        // Create dimension connector for Time
        CONNECTOR_TIME = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_TIME.setOverrideDimensionName("Time");
        CONNECTOR_TIME.setDimension(CatalogSupplier.DIMENSION_TIME);
        CONNECTOR_TIME.setForeignKey(CatalogSupplier.COLUMN_TIME_ID_SALESFACT);

        // Create dimension connector for Product
        CONNECTOR_PRODUCT = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_PRODUCT.setOverrideDimensionName("Product");
        CONNECTOR_PRODUCT.setDimension(CatalogSupplier.DIMENSION_PRODUCT);
        CONNECTOR_PRODUCT.setForeignKey(CatalogSupplier.COLUMN_PRODUCT_ID_SALESFACT);

        // Create fact table query
        TABLE_QUERY_SALES_FACT = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_SALES_FACT.setTable(CatalogSupplier.TABLE_SALES_FACT);

        // Create measure
        MEASURE_STORE_SALES = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_STORE_SALES.setName("Store Sales");
        MEASURE_STORE_SALES.setColumn(CatalogSupplier.COLUMN_STORE_SALES_SALESFACT);
        MEASURE_STORE_SALES.setFormatString("#,###.00");

        // Create measure group
        MEASURE_GROUP = CubeFactory.eINSTANCE.createMeasureGroup();
        MEASURE_GROUP.getMeasures().add(MEASURE_STORE_SALES);

        // Create cube
        CUBE_3STORE_H_CUBE = CubeFactory.eINSTANCE.createPhysicalCube();
        CUBE_3STORE_H_CUBE.setName("3StoreHCube");
        CUBE_3STORE_H_CUBE.setSource(TABLE_QUERY_SALES_FACT);
        CUBE_3STORE_H_CUBE.getDimensionConnectors().addAll(List.of(
            CONNECTOR_ALT_STORE,
            CONNECTOR_TIME,
            CONNECTOR_PRODUCT
        ));
        CUBE_3STORE_H_CUBE.getMeasureGroups().add(MEASURE_GROUP);
    }

    public TestMultipleAllWithInExprModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        this.catalog = EmfUtil.copy((CatalogImpl) baseCatalog);

        // Add the cube to the catalog
        this.catalog.getImportedElement().add(CUBE_3STORE_H_CUBE);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
