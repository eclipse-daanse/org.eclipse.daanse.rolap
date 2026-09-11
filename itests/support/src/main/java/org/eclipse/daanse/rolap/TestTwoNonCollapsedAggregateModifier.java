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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationColumnName;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationExclude;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationFactory;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationLevel;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationMeasure;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.ExplicitAggregationTable;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement;
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
import org.eclipse.emf.ecore.util.EcoreUtil;
/**
 * EMF version of TestTwoNonCollapsedAggregateModifier from TestAggregationManager.
 * Creates a cube "Foo" with two non-collapsed aggregate levels for testing aggregation.
 * Uses objects from CatalogSupplier and creates custom region table.
 */
public class TestTwoNonCollapsedAggregateModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    public TestTwoNonCollapsedAggregateModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);

        // Static custom columns for region table
        Column COLUMN_SALES_REGION;
        Column COLUMN_SALES_CITY;
        Column COLUMN_SALES_DISTRICT_ID;
        Column COLUMN_REGION_ID_REGION;

        // Static custom region table
        Table TABLE_REGION;

        // Static aggregation configuration
        AggregationExclude AGG_EXCLUDE_1;
        AggregationExclude AGG_EXCLUDE_2;
        AggregationExclude AGG_EXCLUDE_3;
        AggregationExclude AGG_EXCLUDE_4;

        AggregationColumnName AGG_FACT_COUNT;
        AggregationColumnName AGG_IGNORE_CUSTOMER;
        AggregationColumnName AGG_IGNORE_PROMOTION;
        AggregationColumnName AGG_IGNORE_STORE_SALES;
        AggregationColumnName AGG_IGNORE_STORE_COST;

        AggregationMeasure AGG_MEASURE_UNIT_SALES;

        AggregationLevel AGG_LEVEL_PRODUCT;
        AggregationLevel AGG_LEVEL_STORE;

        ExplicitAggregationTable AGGREGATION_NAME;

        // Static table query with aggregations
        TableSource TABLE_QUERY_SALES_FACT;

        // Static Product dimension levels
        Level LEVEL_PRODUCT_FAMILY;
        Level LEVEL_PRODUCT_DEPARTMENT;
        Level LEVEL_PRODUCT_CATEGORY;
        Level LEVEL_PRODUCT_SUBCATEGORY;
        Level LEVEL_BRAND_NAME;
        Level LEVEL_PRODUCT_NAME;
        Level LEVEL_PRODUCT_ID;

        // Static Product hierarchy
        ExplicitHierarchy HIERARCHY_PRODUCT;

        // Static Product dimension
        StandardDimension DIMENSION_PRODUCT_LOCAL;

        // Static Store dimension levels
        Level LEVEL_STORE_REGION;
        Level LEVEL_STORE_ID;

        // Static Store hierarchy
        ExplicitHierarchy HIERARCHY_STORE;

        // Static Store dimension
        StandardDimension DIMENSION_STORE_LOCAL;

        // Static dimension connectors
        DimensionConnector CONNECTOR_PRODUCT;
        DimensionConnector CONNECTOR_STORE;

        // Static measure
        SumMeasure MEASURE_UNIT_SALES;

        // Static measure group
        MeasureGroup MEASURE_GROUP_FOO;

        // Static physical cube
        PhysicalCube CUBE_FOO;

        // Create custom region table columns
        COLUMN_SALES_REGION = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        COLUMN_SALES_REGION.setName("sales_region");
        COLUMN_SALES_REGION.setType(SQLSimpleTypes.varcharType(255));
        // COLUMN_SALES_REGION.setCharOctetLength(30);

        COLUMN_SALES_CITY = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        COLUMN_SALES_CITY.setName("sales_city");
        COLUMN_SALES_CITY.setType(SQLSimpleTypes.varcharType(255));
        // COLUMN_SALES_CITY.setCharOctetLength(30);

        COLUMN_SALES_DISTRICT_ID = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        COLUMN_SALES_DISTRICT_ID.setName("sales_district_id");
        COLUMN_SALES_DISTRICT_ID.setType(SQLSimpleTypes.Sql99.integerType());

        COLUMN_REGION_ID_REGION = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        COLUMN_REGION_ID_REGION.setName("region_id");
        COLUMN_REGION_ID_REGION.setType(SQLSimpleTypes.Sql99.integerType());

        // Create custom region table
        TABLE_REGION = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        TABLE_REGION.setName("region");
        TABLE_REGION.getFeature().addAll(List.of(
            COLUMN_SALES_REGION,
            COLUMN_SALES_CITY,
            COLUMN_SALES_DISTRICT_ID,
            COLUMN_REGION_ID_REGION
        ));

        // Create aggregation excludes
        AGG_EXCLUDE_1 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_1.setName("agg_g_ms_pcat_sales_fact_1997");

        AGG_EXCLUDE_2 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_2.setName("agg_c_14_sales_fact_1997");

        AGG_EXCLUDE_3 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_3.setName("agg_pl_01_sales_fact_1997");

        AGG_EXCLUDE_4 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_4.setName("agg_ll_01_sales_fact_1997");

        // Create aggregation fact count
        AGG_FACT_COUNT = AggregationFactory.eINSTANCE.createAggregationColumnName();
        AGG_FACT_COUNT.setColumn((Column) copier.get(CatalogSupplier.COLUMN_FACT_COUNT_AGG_L_05_SALES_FACT_1997));

        // Create aggregation ignore columns
        AGG_IGNORE_CUSTOMER = AggregationFactory.eINSTANCE.createAggregationColumnName();
        AGG_IGNORE_CUSTOMER.setColumn((Column) copier.get(CatalogSupplier.COLUMN_CUSTOMER_ID_AGG_L_05_SALES_FACT_1997));

        AGG_IGNORE_PROMOTION = AggregationFactory.eINSTANCE.createAggregationColumnName();
        AGG_IGNORE_PROMOTION.setColumn((Column) copier.get(CatalogSupplier.COLUMN_PROMOTION_ID_AGG_L_05_SALES_FACT_1997));

        AGG_IGNORE_STORE_SALES = AggregationFactory.eINSTANCE.createAggregationColumnName();
        AGG_IGNORE_STORE_SALES.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_SALES_AGG_L_05_SALES_FACT_1997));

        AGG_IGNORE_STORE_COST = AggregationFactory.eINSTANCE.createAggregationColumnName();
        AGG_IGNORE_STORE_COST.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_COST_AGG_L_05_SALES_FACT_1997));

        // Create aggregation measure
        AGG_MEASURE_UNIT_SALES = AggregationFactory.eINSTANCE.createAggregationMeasure();
        AGG_MEASURE_UNIT_SALES.setName("[Measures].[Unit Sales]");
        AGG_MEASURE_UNIT_SALES.setColumn((Column) copier.get(CatalogSupplier.COLUMN_UNIT_SALES_AGG_L_05_SALES_FACT_1997));

        // Create aggregation levels (collapsed=false)
        AGG_LEVEL_PRODUCT = AggregationFactory.eINSTANCE.createAggregationLevel();
        AGG_LEVEL_PRODUCT.setName("[Product].[Product].[Product Id]");
        AGG_LEVEL_PRODUCT.setColumn((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_ID_AGG_L_05_SALES_FACT_1997));
        AGG_LEVEL_PRODUCT.setCollapsed(false);

        AGG_LEVEL_STORE = AggregationFactory.eINSTANCE.createAggregationLevel();
        AGG_LEVEL_STORE.setName("[Store].[Store].[Store Id]");
        AGG_LEVEL_STORE.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_ID_AGG_L_05_SALES_FACT_1997));
        AGG_LEVEL_STORE.setCollapsed(false);

        // Create aggregation name
        AGGREGATION_NAME = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        AGGREGATION_NAME.setTable((Table) copier.get(CatalogSupplier.TABLE_AGG_L_05_SALES_FACT));
        AGGREGATION_NAME.setAggregationFactCount(AGG_FACT_COUNT);
        AGGREGATION_NAME.getAggregationIgnoreColumns().addAll(List.of(
            AGG_IGNORE_CUSTOMER,
            AGG_IGNORE_PROMOTION,
            AGG_IGNORE_STORE_SALES,
            AGG_IGNORE_STORE_COST
        ));
        AGGREGATION_NAME.getAggregationMeasures().add(AGG_MEASURE_UNIT_SALES);
        AGGREGATION_NAME.getAggregationLevels().addAll(List.of(
            AGG_LEVEL_PRODUCT,
            AGG_LEVEL_STORE
        ));

        // Create table query with aggregations
        TABLE_QUERY_SALES_FACT = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_SALES_FACT.setTable((Table) copier.get(CatalogSupplier.TABLE_SALES_FACT));
        TABLE_QUERY_SALES_FACT.getAggregationExcludes().addAll(List.of(
            AGG_EXCLUDE_1,
            AGG_EXCLUDE_2,
            AGG_EXCLUDE_3,
            AGG_EXCLUDE_4
        ));
        TABLE_QUERY_SALES_FACT.getAggregationTables().add(AGGREGATION_NAME);

        // Create Product dimension levels
        LEVEL_PRODUCT_FAMILY = LevelFactory.eINSTANCE.createLevel();
        LEVEL_PRODUCT_FAMILY.setName("Product Family");
        LEVEL_PRODUCT_FAMILY.setColumn((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_FAMILY_PRODUCT_CLASS));
        LEVEL_PRODUCT_FAMILY.setUniqueMembers(true);

        LEVEL_PRODUCT_DEPARTMENT = LevelFactory.eINSTANCE.createLevel();
        LEVEL_PRODUCT_DEPARTMENT.setName("Product Department");
        LEVEL_PRODUCT_DEPARTMENT.setColumn((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_DEPARTMENT_PRODUCT_CLASS));
        LEVEL_PRODUCT_DEPARTMENT.setUniqueMembers(false);

        LEVEL_PRODUCT_CATEGORY = LevelFactory.eINSTANCE.createLevel();
        LEVEL_PRODUCT_CATEGORY.setName("Product Category");
        LEVEL_PRODUCT_CATEGORY.setColumn((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_CATEGORY_PRODUCT_CLASS));
        LEVEL_PRODUCT_CATEGORY.setUniqueMembers(false);

        LEVEL_PRODUCT_SUBCATEGORY = LevelFactory.eINSTANCE.createLevel();
        LEVEL_PRODUCT_SUBCATEGORY.setName("Product Subcategory");
        LEVEL_PRODUCT_SUBCATEGORY.setColumn((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_SUBCATEGORY_PRODUCT_CLASS));
        LEVEL_PRODUCT_SUBCATEGORY.setUniqueMembers(false);

        LEVEL_BRAND_NAME = LevelFactory.eINSTANCE.createLevel();
        LEVEL_BRAND_NAME.setName("Brand Name");
        LEVEL_BRAND_NAME.setColumn((Column) copier.get(CatalogSupplier.COLUMN_BRAND_NAME_PRODUCT));
        LEVEL_BRAND_NAME.setUniqueMembers(false);

        LEVEL_PRODUCT_NAME = LevelFactory.eINSTANCE.createLevel();
        LEVEL_PRODUCT_NAME.setName("Product Name");
        LEVEL_PRODUCT_NAME.setColumn((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_NAME_PRODUCT));
        LEVEL_PRODUCT_NAME.setUniqueMembers(true);

        LEVEL_PRODUCT_ID = LevelFactory.eINSTANCE.createLevel();
        LEVEL_PRODUCT_ID.setName("Product Id");
        LEVEL_PRODUCT_ID.setColumn((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_ID_PRODUCT));
        LEVEL_PRODUCT_ID.setUniqueMembers(true);

        // Create Product join query
        JoinedQueryElement productLeft = SourceFactory.eINSTANCE.createJoinedQueryElement();
        productLeft.setKey((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_CLASS_ID_PRODUCT));
        TableSource productTableQuery = SourceFactory.eINSTANCE.createTableSource();
        productTableQuery.setTable((Table) copier.get(CatalogSupplier.TABLE_PRODUCT));
        productLeft.setSource(productTableQuery);

        JoinedQueryElement productRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        productRight.setKey((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_CLASS_ID_PRODUCT_CLASS));
        TableSource productClassTableQuery = SourceFactory.eINSTANCE.createTableSource();
        productClassTableQuery.setTable((Table) copier.get(CatalogSupplier.TABLE_PRODUCT_CLASS));
        productRight.setSource(productClassTableQuery);

        JoinSource productJoin = SourceFactory.eINSTANCE.createJoinSource();
        productJoin.setLeft(productLeft);
        productJoin.setRight(productRight);

        // Create Product hierarchy
        HIERARCHY_PRODUCT = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_PRODUCT.setHasAll(true);
        HIERARCHY_PRODUCT.setPrimaryKey((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_ID_PRODUCT));
        HIERARCHY_PRODUCT.setSource(productJoin);
        HIERARCHY_PRODUCT.getLevels().addAll(List.of(
            LEVEL_PRODUCT_FAMILY,
            LEVEL_PRODUCT_DEPARTMENT,
            LEVEL_PRODUCT_CATEGORY,
            LEVEL_PRODUCT_SUBCATEGORY,
            LEVEL_BRAND_NAME,
            LEVEL_PRODUCT_NAME,
            LEVEL_PRODUCT_ID
        ));

        // Create Product dimension
        DIMENSION_PRODUCT_LOCAL = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_PRODUCT_LOCAL.getHierarchies().add(HIERARCHY_PRODUCT);

        // Create Store dimension levels
        LEVEL_STORE_REGION = LevelFactory.eINSTANCE.createLevel();
        LEVEL_STORE_REGION.setName("Store Region");
        LEVEL_STORE_REGION.setColumn(COLUMN_SALES_CITY);
        LEVEL_STORE_REGION.setUniqueMembers(false);

        LEVEL_STORE_ID = LevelFactory.eINSTANCE.createLevel();
        LEVEL_STORE_ID.setName("Store Id");
        LEVEL_STORE_ID.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_ID_STORE));
        LEVEL_STORE_ID.setUniqueMembers(true);

        // Create Store join query
        JoinedQueryElement storeLeft = SourceFactory.eINSTANCE.createJoinedQueryElement();
        storeLeft.setKey((Column) copier.get(CatalogSupplier.COLUMN_REGION_ID_STORE));
        TableSource storeTableQuery = SourceFactory.eINSTANCE.createTableSource();
        storeTableQuery.setTable((Table) copier.get(CatalogSupplier.TABLE_STORE));
        storeLeft.setSource(storeTableQuery);

        JoinedQueryElement storeRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        storeRight.setKey(COLUMN_REGION_ID_REGION);
        TableSource regionTableQuery = SourceFactory.eINSTANCE.createTableSource();
        regionTableQuery.setTable(TABLE_REGION);
        storeRight.setSource(regionTableQuery);

        JoinSource storeJoin = SourceFactory.eINSTANCE.createJoinSource();
        storeJoin.setLeft(storeLeft);
        storeJoin.setRight(storeRight);

        // Create Store hierarchy
        HIERARCHY_STORE = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_STORE.setHasAll(true);
        HIERARCHY_STORE.setPrimaryKey((Column) copier.get(CatalogSupplier.COLUMN_STORE_ID_STORE));
        HIERARCHY_STORE.setSource(storeJoin);
        HIERARCHY_STORE.getLevels().addAll(List.of(
            LEVEL_STORE_REGION,
            LEVEL_STORE_ID
        ));

        // Create Store dimension
        DIMENSION_STORE_LOCAL = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_STORE_LOCAL.setName("Store");
        DIMENSION_STORE_LOCAL.getHierarchies().add(HIERARCHY_STORE);

        // Create dimension connectors
        CONNECTOR_PRODUCT = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_PRODUCT.setOverrideDimensionName("Product");
        CONNECTOR_PRODUCT.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_ID_SALESFACT));
        CONNECTOR_PRODUCT.setDimension(DIMENSION_PRODUCT_LOCAL);

        CONNECTOR_STORE = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_STORE.setOverrideDimensionName("Store");
        CONNECTOR_STORE.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_STORE_ID_SALESFACT));
        CONNECTOR_STORE.setDimension(DIMENSION_STORE_LOCAL);

        // Create measure
        MEASURE_UNIT_SALES = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_UNIT_SALES.setName("Unit Sales");
        MEASURE_UNIT_SALES.setColumn((Column) copier.get(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT));
        MEASURE_UNIT_SALES.setFormatString("Standard");

        // Create measure group
        MEASURE_GROUP_FOO = CubeFactory.eINSTANCE.createMeasureGroup();
        MEASURE_GROUP_FOO.getMeasures().add(MEASURE_UNIT_SALES);

        // Create physical cube "Foo"
        CUBE_FOO = CubeFactory.eINSTANCE.createPhysicalCube();
        CUBE_FOO.setName("Foo");
        CUBE_FOO.setDefaultMeasure(MEASURE_UNIT_SALES);
        CUBE_FOO.setSource(TABLE_QUERY_SALES_FACT);
        CUBE_FOO.getDimensionConnectors().addAll(List.of(
            CONNECTOR_PRODUCT,
            CONNECTOR_STORE
        ));
        CUBE_FOO.getMeasureGroups().add(MEASURE_GROUP_FOO);

        // Add the physical cube to the catalog
        this.catalog.getImportedElement().add(CUBE_FOO);


    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
