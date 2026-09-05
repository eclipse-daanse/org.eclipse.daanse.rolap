/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.test;


import static org.eclipse.daanse.rolap.mapping.model.provider.util.Expressions.mdx;
import java.util.Collection;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationExclude;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationFactory;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ExpressionColumn;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.SqlStatement;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.CountMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.SumMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.StandardDimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.TimeDimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ExplicitHierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.HierarchyFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ParentChildHierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ParentChildLink;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.CalculatedMember;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.CalculatedMemberProperty;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelDefinition;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.MemberProperty;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
/**
 * EMF-based version of MyFoodmartModifier.
 * This class demonstrates the conversion from POJO builder patterns to EMF factory methods.
 *
 * Key conversion patterns:
 * - Use RolapMappingFactory.eINSTANCE.createXxx() instead of XxxImpl.builder()
 * - Use setXxx() methods instead of withXxx()
 * - Use getXxx().add() or getXxx().addAll() for lists
 * - Import from org.eclipse.daanse.rolap.mapping.emf.rolapmapping instead of pojo
 * - Implement CatalogMappingSupplier instead of extending PojoMappingModifier
 * - Use org.opencube.junit5.EmfUtil.copy((CatalogImpl) catalogMapping) to copy the catalog
 */
public class MyFoodmartModifierEmf implements CatalogMappingSupplier {

    private final org.eclipse.daanse.rolap.mapping.model.catalog.Catalog catalog;

    public MyFoodmartModifierEmf(org.eclipse.daanse.rolap.mapping.model.catalog.Catalog catalogMapping) {
        // Copy the original catalog
        this.catalog = CatalogFactory.eINSTANCE.createCatalog();
        this.catalog.setName("FoodMart");
        this.catalog.getImportedElement().addAll(Packages.available(catalogMapping, Schema.class));
        // References to hierarchies and levels for access roles
        ExplicitHierarchy storeHierarchy;
        ExplicitHierarchy customersHierarchy;
        ExplicitHierarchy genderHierarchy;
        Level storeCountryLevel;
        Level customersStateProvince;
        Level customersCity;

        // Store Dimension (shared)
        StandardDimension storeDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        storeDimension.setName("Store");

        storeHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        ((ExplicitHierarchy) storeHierarchy).setHasAll(true);
        ((ExplicitHierarchy) storeHierarchy).setPrimaryKey(CatalogSupplier.COLUMN_STORE_ID_STORE);

        TableSource storeTableQuery = SourceFactory.eINSTANCE.createTableSource();
        storeTableQuery.setTable(CatalogSupplier.TABLE_STORE);
        ((ExplicitHierarchy) storeHierarchy).setSource(storeTableQuery);

        // Store Hierarchy Levels
        storeCountryLevel = LevelFactory.eINSTANCE.createLevel();
        storeCountryLevel.setName("Store Country");
        storeCountryLevel.setColumn(CatalogSupplier.COLUMN_STORE_COUNTRY_STORE);
        storeCountryLevel.setUniqueMembers(true);

        Level storeStateLevel = LevelFactory.eINSTANCE.createLevel();
        storeStateLevel.setName("Store State");
        storeStateLevel.setColumn(CatalogSupplier.COLUMN_STORE_STATE_STORE);
        storeStateLevel.setUniqueMembers(true);

        Level storeCityLevel = LevelFactory.eINSTANCE.createLevel();
        storeCityLevel.setName("Store City");
        storeCityLevel.setColumn(CatalogSupplier.COLUMN_STORE_CITY_STORE);
        storeCityLevel.setUniqueMembers(false);

        Level storeNameLevel = LevelFactory.eINSTANCE.createLevel();
        storeNameLevel.setName("Store Name");
        storeNameLevel.setColumn(CatalogSupplier.COLUMN_STORE_NAME_STORE);
        storeNameLevel.setUniqueMembers(true);

        // Store Name Level - Member Properties
        MemberProperty storeTypeProp = LevelFactory.eINSTANCE.createMemberProperty();
        storeTypeProp.setName("Store Type");
        storeTypeProp.setColumn(CatalogSupplier.COLUMN_STORE_TYPE_STORE);

        MemberProperty storeManagerProp = LevelFactory.eINSTANCE.createMemberProperty();
        storeManagerProp.setName("Store Manager");
        storeManagerProp.setColumn(CatalogSupplier.COLUMN_STORE_MANAGER_STORE);

        MemberProperty storeSqftProp = LevelFactory.eINSTANCE.createMemberProperty();
        storeSqftProp.setName("Store Sqft");
        storeSqftProp.setColumn(CatalogSupplier.COLUMN_STORE_SQFT_STORE);
        storeSqftProp.setPropertyType(ColumnInternalDataType.NUMERIC);

        MemberProperty grocerySqftProp = LevelFactory.eINSTANCE.createMemberProperty();
        grocerySqftProp.setName("Grocery Sqft");
        grocerySqftProp.setColumn(CatalogSupplier.COLUMN_GROCERY_SQFT_STORE);
        grocerySqftProp.setPropertyType(ColumnInternalDataType.NUMERIC);

        MemberProperty frozenSqftProp = LevelFactory.eINSTANCE.createMemberProperty();
        frozenSqftProp.setName("Frozen Sqft");
        frozenSqftProp.setColumn(CatalogSupplier.COLUMN_FROZEN_SQFT_STORE);
        frozenSqftProp.setPropertyType(ColumnInternalDataType.NUMERIC);

        MemberProperty meatSqftProp = LevelFactory.eINSTANCE.createMemberProperty();
        meatSqftProp.setName("Meat Sqft");
        meatSqftProp.setColumn(CatalogSupplier.COLUMN_MEAT_SQFT_STORE);
        meatSqftProp.setPropertyType(ColumnInternalDataType.NUMERIC);

        MemberProperty coffeeBarProp = LevelFactory.eINSTANCE.createMemberProperty();
        coffeeBarProp.setName("Has coffee bar");
        coffeeBarProp.setColumn(CatalogSupplier.COLUMN_COFFEE_BAR_STORE);
        coffeeBarProp.setPropertyType(ColumnInternalDataType.BOOLEAN);

        MemberProperty streetAddressProp = LevelFactory.eINSTANCE.createMemberProperty();
        streetAddressProp.setName("Street address");
        streetAddressProp.setColumn(CatalogSupplier.COLUMN_STREET_ADDRESS_STORE);
        streetAddressProp.setPropertyType(ColumnInternalDataType.STRING);

        storeNameLevel.getMemberProperties().addAll(List.of(
            storeTypeProp, storeManagerProp, storeSqftProp, grocerySqftProp,
            frozenSqftProp, meatSqftProp, coffeeBarProp, streetAddressProp
        ));

        ((ExplicitHierarchy) storeHierarchy).getLevels().addAll(List.of(
            storeCountryLevel, storeStateLevel, storeCityLevel, storeNameLevel
        ));

        storeDimension.getHierarchies().add(storeHierarchy);

        // Store Size in SQFT Dimension (shared)
        StandardDimension storeSizeSQFTDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        storeSizeSQFTDimension.setName("Store Size in SQFT");

        ExplicitHierarchy storeSizeHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        storeSizeHierarchy.setHasAll(true);
        storeSizeHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_STORE_ID_STORE);

        TableSource storeSizeTable = SourceFactory.eINSTANCE.createTableSource();
        storeSizeTable.setTable(CatalogSupplier.TABLE_STORE);
        storeSizeHierarchy.setSource(storeSizeTable);

        Level storeSqftLevel = LevelFactory.eINSTANCE.createLevel();
        storeSqftLevel.setName("Store Sqft");
        storeSqftLevel.setColumn(CatalogSupplier.COLUMN_STORE_SQFT_STORE);
        storeSqftLevel.setUniqueMembers(true);

        storeSizeHierarchy.getLevels().add(storeSqftLevel);
        storeSizeSQFTDimension.getHierarchies().add(storeSizeHierarchy);

        // Store Type Dimension (shared)
        StandardDimension storeTypeDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        storeTypeDimension.setName("Store Type");

        ExplicitHierarchy storeTypeHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        storeTypeHierarchy.setHasAll(true);
        storeTypeHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_STORE_ID_STORE);

        TableSource storeTypeTable = SourceFactory.eINSTANCE.createTableSource();
        storeTypeTable.setTable(CatalogSupplier.TABLE_STORE);
        storeTypeHierarchy.setSource(storeTypeTable);

        Level storeTypeLevel = LevelFactory.eINSTANCE.createLevel();
        storeTypeLevel.setName("Store Type");
        storeTypeLevel.setColumn(CatalogSupplier.COLUMN_STORE_TYPE_STORE);
        storeTypeLevel.setUniqueMembers(true);

        storeTypeHierarchy.getLevels().add(storeTypeLevel);
        storeTypeDimension.getHierarchies().add(storeTypeHierarchy);

        // Time Dimension (shared)
        TimeDimension timeDimension = DimensionFactory.eINSTANCE.createTimeDimension();
        timeDimension.setName("Time");

        ExplicitHierarchy timeHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        timeHierarchy.setHasAll(false);
        timeHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_TIME_ID_TIME_BY_DAY);

        TableSource timeTable = SourceFactory.eINSTANCE.createTableSource();
        timeTable.setTable(CatalogSupplier.TABLE_TIME_BY_DAY);
        timeHierarchy.setSource(timeTable);

        Level yearLevel = LevelFactory.eINSTANCE.createLevel();
        yearLevel.setName("Year");
        yearLevel.setColumn(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY);
        yearLevel.setColumnType(ColumnInternalDataType.NUMERIC);
        yearLevel.setUniqueMembers(true);
        yearLevel.setType(LevelDefinition.TIME_YEARS);

        // Year Caption Expression with SQL dialects
        ExpressionColumn yearCaptionExpr = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createExpressionColumn();
        yearCaptionExpr.setType(SQLSimpleTypes.varcharType(255));

        SqlStatement accessSql = SourceFactory.eINSTANCE.createSqlStatement();
        accessSql.getDialects().add("access");
        accessSql.setBody("cstr(the_year) + '-12-31'");

        SqlStatement mysqlSql = SourceFactory.eINSTANCE.createSqlStatement();
        mysqlSql.getDialects().add("mysql");
        // CaptionTest runs this for MARIADB too, but no mariadb statement was ever registered:
        // it fell through to the generic "the_year" || '-12-31', and in MariaDB's default mode
        // || is logical OR, so the caption came out as 1 instead of 1997-12-31.
        mysqlSql.getDialects().add("mariadb");
        mysqlSql.setBody("concat(cast(`the_year` as char(4)), '-12-31')");

        SqlStatement derbySql = SourceFactory.eINSTANCE.createSqlStatement();
        derbySql.getDialects().add("derby");
        derbySql.setBody("'foobar'");

        SqlStatement genericSql = SourceFactory.eINSTANCE.createSqlStatement();
        genericSql.getDialects().add("generic");
        genericSql.setBody("\"the_year\" || '-12-31'");

        yearCaptionExpr.getSqls().addAll(List.of(accessSql, mysqlSql, derbySql, genericSql));
        yearLevel.setCaptionColumn(yearCaptionExpr);

        Level quarterLevel = LevelFactory.eINSTANCE.createLevel();
        quarterLevel.setName("Quarter");
        quarterLevel.setColumn(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY);
        quarterLevel.setUniqueMembers(false);
        quarterLevel.setType(LevelDefinition.TIME_QUARTERS);

        Level monthLevel = LevelFactory.eINSTANCE.createLevel();
        monthLevel.setName("Month");
        monthLevel.setColumn(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY);
        monthLevel.setColumnType(ColumnInternalDataType.NUMERIC);
        monthLevel.setUniqueMembers(false);
        monthLevel.setType(LevelDefinition.TIME_MONTHS);

        timeHierarchy.getLevels().addAll(List.of(yearLevel, quarterLevel, monthLevel));
        timeDimension.getHierarchies().add(timeHierarchy);

        // Product Dimension (shared)
        StandardDimension productDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        productDimension.setName("Product");

        ExplicitHierarchy productHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        productHierarchy.setHasAll(true);
        productHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_PRODUCT_ID_PRODUCT);

        // Product Join Query
        JoinSource productJoin = SourceFactory.eINSTANCE.createJoinSource();

        JoinedQueryElement productLeft = SourceFactory.eINSTANCE.createJoinedQueryElement();
        productLeft.setKey(CatalogSupplier.COLUMN_PRODUCT_CLASS_ID_PRODUCT);
        TableSource productTableLeft = SourceFactory.eINSTANCE.createTableSource();
        productTableLeft.setTable(CatalogSupplier.TABLE_PRODUCT);
        productLeft.setSource(productTableLeft);

        JoinedQueryElement productRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        productRight.setKey(CatalogSupplier.COLUMN_PRODUCT_CLASS_ID_PRODUCT_CLASS);
        TableSource productClassTable = SourceFactory.eINSTANCE.createTableSource();
        productClassTable.setTable(CatalogSupplier.TABLE_PRODUCT_CLASS);
        productRight.setSource(productClassTable);

        productJoin.setLeft(productLeft);
        productJoin.setRight(productRight);
        productHierarchy.setSource(productJoin);

        Level productFamilyLevel = LevelFactory.eINSTANCE.createLevel();
        productFamilyLevel.setName("Product Family");
        productFamilyLevel.setColumn(CatalogSupplier.COLUMN_PRODUCT_FAMILY_PRODUCT_CLASS);
        productFamilyLevel.setUniqueMembers(true);

        Level productDepartmentLevel = LevelFactory.eINSTANCE.createLevel();
        productDepartmentLevel.setName("Product Department");
        productDepartmentLevel.setColumn(CatalogSupplier.COLUMN_PRODUCT_DEPARTMENT_PRODUCT_CLASS);
        productDepartmentLevel.setUniqueMembers(false);

        Level productCategoryLevel = LevelFactory.eINSTANCE.createLevel();
        productCategoryLevel.setName("Product Category");
        productCategoryLevel.setColumn(CatalogSupplier.COLUMN_PRODUCT_CATEGORY_PRODUCT_CLASS);
        productCategoryLevel.setUniqueMembers(false);

        Level productSubcategoryLevel = LevelFactory.eINSTANCE.createLevel();
        productSubcategoryLevel.setName("Product Subcategory");
        productSubcategoryLevel.setColumn(CatalogSupplier.COLUMN_PRODUCT_SUBCATEGORY_PRODUCT_CLASS);
        productSubcategoryLevel.setUniqueMembers(false);

        Level brandNameLevel = LevelFactory.eINSTANCE.createLevel();
        brandNameLevel.setName("Brand Name");
        brandNameLevel.setColumn(CatalogSupplier.COLUMN_BRAND_NAME_PRODUCT);
        brandNameLevel.setUniqueMembers(false);

        Level productNameLevel = LevelFactory.eINSTANCE.createLevel();
        productNameLevel.setName("Product Name");
        productNameLevel.setColumn(CatalogSupplier.COLUMN_PRODUCT_NAME_PRODUCT);
        productNameLevel.setUniqueMembers(false);

        productHierarchy.getLevels().addAll(List.of(
            productFamilyLevel, productDepartmentLevel, productCategoryLevel,
            productSubcategoryLevel, brandNameLevel, productNameLevel
        ));
        productDimension.getHierarchies().add(productHierarchy);

        // Warehouse Dimension (shared)
        StandardDimension warehouseDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        warehouseDimension.setName("Warehouse");

        ExplicitHierarchy warehouseHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        warehouseHierarchy.setHasAll(true);
        warehouseHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_WAREHOUSE_ID_WAREHOUSE);

        TableSource warehouseTable = SourceFactory.eINSTANCE.createTableSource();
        warehouseTable.setTable(CatalogSupplier.TABLE_WAREHOUSE);
        warehouseHierarchy.setSource(warehouseTable);

        Level warehouseCountryLevel = LevelFactory.eINSTANCE.createLevel();
        warehouseCountryLevel.setName("Country");
        warehouseCountryLevel.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_COUNTRY_WAREHOUSE);
        warehouseCountryLevel.setUniqueMembers(true);

        Level warehouseStateLevel = LevelFactory.eINSTANCE.createLevel();
        warehouseStateLevel.setName("State Province");
        warehouseStateLevel.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_STATE_PROVINCE_WAREHOUSE);
        warehouseStateLevel.setUniqueMembers(true);

        Level warehouseCityLevel = LevelFactory.eINSTANCE.createLevel();
        warehouseCityLevel.setName("City");
        warehouseCityLevel.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_CITY_WAREHOUSE);
        warehouseCityLevel.setUniqueMembers(false);

        Level warehouseNameLevel = LevelFactory.eINSTANCE.createLevel();
        warehouseNameLevel.setName("Warehouse Name");
        warehouseNameLevel.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_NAME_WAREHOUSE);
        warehouseNameLevel.setUniqueMembers(true);

        warehouseHierarchy.getLevels().addAll(List.of(
            warehouseCountryLevel, warehouseStateLevel, warehouseCityLevel, warehouseNameLevel
        ));
        warehouseDimension.getHierarchies().add(warehouseHierarchy);

        // Sales Cube
        PhysicalCube sales = CubeFactory.eINSTANCE.createPhysicalCube();
        sales.setName("Sales");

        // Sales Cube Query (sales_fact_1997 table with aggregation exclude)
        TableSource salesTableQuery = SourceFactory.eINSTANCE.createTableSource();
        salesTableQuery.setTable(CatalogSupplier.TABLE_SALES_FACT);

        AggregationExclude aggExclude = AggregationFactory.eINSTANCE.createAggregationExclude();
        aggExclude.setPattern(".*");
        salesTableQuery.getAggregationExcludes().add(aggExclude);
        sales.setSource(salesTableQuery);

        // Store dimension connector
        DimensionConnector storeDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        storeDimConn.setOverrideDimensionName("Store");
        storeDimConn.setDimension(storeDimension);
        storeDimConn.setForeignKey(CatalogSupplier.COLUMN_STORE_ID_SALESFACT);

        // Store Size in SQFT dimension connector
        DimensionConnector storeSizeDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        storeSizeDimConn.setOverrideDimensionName("Store Size in SQFT");
        storeSizeDimConn.setDimension(storeSizeSQFTDimension);
        storeSizeDimConn.setForeignKey(CatalogSupplier.COLUMN_STORE_ID_SALESFACT);

        // Store Type dimension connector
        DimensionConnector storeTypeDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        storeTypeDimConn.setOverrideDimensionName("Store Type");
        storeTypeDimConn.setDimension(storeTypeDimension);
        storeTypeDimConn.setForeignKey(CatalogSupplier.COLUMN_STORE_ID_SALESFACT);

        // Time dimension connector
        DimensionConnector timeDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        timeDimConn.setOverrideDimensionName("Time");
        timeDimConn.setDimension(timeDimension);
        timeDimConn.setForeignKey(CatalogSupplier.COLUMN_TIME_ID_SALESFACT);

        // Product dimension connector
        DimensionConnector productDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        productDimConn.setOverrideDimensionName("Product");
        productDimConn.setDimension(productDimension);
        productDimConn.setForeignKey(CatalogSupplier.COLUMN_PRODUCT_ID_SALESFACT);

        // Promotion Media Dimension (private)
        StandardDimension promotionMediaDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        promotionMediaDimension.setName("Promotion Media");

        ExplicitHierarchy promotionMediaHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        promotionMediaHierarchy.setHasAll(true);
        promotionMediaHierarchy.setAllMemberName("All Media");
        promotionMediaHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_PROMOTION_ID_PROMOTION);

        TableSource promotionTable = SourceFactory.eINSTANCE.createTableSource();
        promotionTable.setTable(CatalogSupplier.TABLE_PROMOTION);
        promotionMediaHierarchy.setSource(promotionTable);

        Level mediaTypeLevel = LevelFactory.eINSTANCE.createLevel();
        mediaTypeLevel.setName("Media Type");
        mediaTypeLevel.setColumn(CatalogSupplier.COLUMN_MEDIA_TYPE_PROMOTION);
        mediaTypeLevel.setUniqueMembers(true);

        promotionMediaHierarchy.getLevels().add(mediaTypeLevel);
        promotionMediaDimension.getHierarchies().add(promotionMediaHierarchy);

        DimensionConnector promotionMediaDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        promotionMediaDimConn.setOverrideDimensionName("Promotion Media");
        promotionMediaDimConn.setDimension(promotionMediaDimension);
        promotionMediaDimConn.setForeignKey(CatalogSupplier.COLUMN_PROMOTION_ID_SALESFACT);

        // Promotions Dimension (private)
        StandardDimension promotionsDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        promotionsDimension.setName("Promotions");

        ExplicitHierarchy promotionsHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        promotionsHierarchy.setHasAll(true);
        promotionsHierarchy.setAllMemberName("All Promotions");
        promotionsHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_PROMOTION_ID_PROMOTION);

        TableSource promotionsTable = SourceFactory.eINSTANCE.createTableSource();
        promotionsTable.setTable(CatalogSupplier.TABLE_PROMOTION);
        promotionsHierarchy.setSource(promotionsTable);

        Level promotionNameLevel = LevelFactory.eINSTANCE.createLevel();
        promotionNameLevel.setName("Promotion Name");
        promotionNameLevel.setColumn(CatalogSupplier.COLUMN_PROMOTION_NAME_PROMOTION);
        promotionNameLevel.setUniqueMembers(true);

        promotionsHierarchy.getLevels().add(promotionNameLevel);
        promotionsDimension.getHierarchies().add(promotionsHierarchy);

        DimensionConnector promotionsDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        promotionsDimConn.setOverrideDimensionName("Promotions");
        promotionsDimConn.setDimension(promotionsDimension);
        promotionsDimConn.setForeignKey(CatalogSupplier.COLUMN_PRODUCT_ID_SALESFACT);

        // Customers Dimension (private)
        StandardDimension customersDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        customersDimension.setName("Customers");

        customersHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        customersHierarchy.setHasAll(true);
        customersHierarchy.setAllMemberName("All Customers");
        customersHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);

        TableSource customerTable = SourceFactory.eINSTANCE.createTableSource();
        customerTable.setTable(CatalogSupplier.TABLE_CUSTOMER);
        customersHierarchy.setSource(customerTable);

        Level customerCountryLevel = LevelFactory.eINSTANCE.createLevel();
        customerCountryLevel.setName("Country");
        customerCountryLevel.setColumn(CatalogSupplier.COLUMN_COUNTRY_CUSTOMER);
        customerCountryLevel.setUniqueMembers(true);

        customersStateProvince = LevelFactory.eINSTANCE.createLevel();
        customersStateProvince.setName("State Province");
        customersStateProvince.setColumn(CatalogSupplier.COLUMN_STATE_PROVINCE_CUSTOMER);
        customersStateProvince.setUniqueMembers(true);

        customersCity = LevelFactory.eINSTANCE.createLevel();
        customersCity.setName("City");
        customersCity.setColumn(CatalogSupplier.COLUMN_CITY_CUSTOMER);
        customersCity.setUniqueMembers(false);

        Level customerNameLevel = LevelFactory.eINSTANCE.createLevel();
        customerNameLevel.setName("Name");
        customerNameLevel.setUniqueMembers(true);

        // Customer Name Key Expression (multi-dialect SQL)
        ExpressionColumn customerNameKeyExpr = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createExpressionColumn();
        customerNameKeyExpr.setType(SQLSimpleTypes.varcharType(255));

        SqlStatement oracleSql = SourceFactory.eINSTANCE.createSqlStatement();
        oracleSql.getDialects().add("oracle");
        oracleSql.setBody("\"fname\" || ' ' || \"lname\"\n");

        SqlStatement accessSql2 = SourceFactory.eINSTANCE.createSqlStatement();
        accessSql2.getDialects().add("access");
        accessSql2.setBody("fname, ' ', lname\n");

        SqlStatement postgresSql = SourceFactory.eINSTANCE.createSqlStatement();
        postgresSql.getDialects().add("postgres");
        postgresSql.setBody("\"fname\" || ' ' || \"lname\"\n");

        SqlStatement mysqlSql2 = SourceFactory.eINSTANCE.createSqlStatement();
        mysqlSql2.getDialects().add("mysql");
        mysqlSql2.setBody("CONCAT(`customer`.`fname`, ' ', `customer`.`lname`)\n");

        SqlStatement mssqlSql = SourceFactory.eINSTANCE.createSqlStatement();
        mssqlSql.getDialects().add("mssql");
        mssqlSql.setBody("fname, ' ', lname\n");

        customerNameKeyExpr.getSqls().addAll(List.of(oracleSql, accessSql2, postgresSql, mysqlSql2, mssqlSql));
        customerNameLevel.setColumn(customerNameKeyExpr);

        // Customer Name Member Properties
        MemberProperty genderProp = LevelFactory.eINSTANCE.createMemberProperty();
        genderProp.setName("Gender");
        genderProp.setColumn(CatalogSupplier.COLUMN_GENDER_CUSTOMER);

        MemberProperty maritalStatusProp = LevelFactory.eINSTANCE.createMemberProperty();
        maritalStatusProp.setName("Marital Status");
        maritalStatusProp.setColumn(CatalogSupplier.COLUMN_MARITAL_STATUS_CUSTOMER);

        MemberProperty educationProp = LevelFactory.eINSTANCE.createMemberProperty();
        educationProp.setName("Education");
        educationProp.setColumn(CatalogSupplier.COLUMN_EDUCATION_CUSTOMER);

        MemberProperty yearlyIncomeProp = LevelFactory.eINSTANCE.createMemberProperty();
        yearlyIncomeProp.setName("Yearly Income");
        yearlyIncomeProp.setColumn(CatalogSupplier.COLUMN_YEARLY_INCOME_CUSTOMER);

        customerNameLevel.getMemberProperties().addAll(List.of(
            genderProp, maritalStatusProp, educationProp, yearlyIncomeProp
        ));

        customersHierarchy.getLevels().addAll(List.of(
            customerCountryLevel, customersStateProvince, customersCity, customerNameLevel
        ));
        customersDimension.getHierarchies().add(customersHierarchy);

        DimensionConnector customersDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        customersDimConn.setOverrideDimensionName("Customers");
        customersDimConn.setDimension(customersDimension);
        customersDimConn.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);

        // Education Level Dimension (private)
        StandardDimension educationLevelDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        educationLevelDimension.setName("Education Level");

        ExplicitHierarchy educationHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        educationHierarchy.setHasAll(true);
        educationHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);

        TableSource educationTable = SourceFactory.eINSTANCE.createTableSource();
        educationTable.setTable(CatalogSupplier.TABLE_CUSTOMER);
        educationHierarchy.setSource(educationTable);

        Level educationLevelLevel = LevelFactory.eINSTANCE.createLevel();
        educationLevelLevel.setName("Education Level");
        educationLevelLevel.setColumn(CatalogSupplier.COLUMN_EDUCATION_CUSTOMER);
        educationLevelLevel.setUniqueMembers(true);

        educationHierarchy.getLevels().add(educationLevelLevel);
        educationLevelDimension.getHierarchies().add(educationHierarchy);

        DimensionConnector educationLevelDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        educationLevelDimConn.setOverrideDimensionName("Education Level");
        educationLevelDimConn.setDimension(educationLevelDimension);
        educationLevelDimConn.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);

        // Gender Dimension (private)
        StandardDimension genderDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        genderDimension.setName("Gender");

        genderHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        genderHierarchy.setHasAll(true);
        genderHierarchy.setAllMemberName("All Gender");
        genderHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);

        TableSource genderTable = SourceFactory.eINSTANCE.createTableSource();
        genderTable.setTable(CatalogSupplier.TABLE_CUSTOMER);
        genderHierarchy.setSource(genderTable);

        Level genderLevel = LevelFactory.eINSTANCE.createLevel();
        genderLevel.setName("Gender");
        genderLevel.setColumn(CatalogSupplier.COLUMN_GENDER_CUSTOMER);
        genderLevel.setUniqueMembers(true);

        genderHierarchy.getLevels().add(genderLevel);
        genderDimension.getHierarchies().add(genderHierarchy);

        DimensionConnector genderDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        genderDimConn.setOverrideDimensionName("Gender");
        genderDimConn.setDimension(genderDimension);
        genderDimConn.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);

        // Marital Status Dimension (private)
        StandardDimension maritalStatusDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        maritalStatusDimension.setName("Marital Status");

        ExplicitHierarchy maritalStatusHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        maritalStatusHierarchy.setHasAll(true);
        maritalStatusHierarchy.setAllMemberName("All Marital Status");
        maritalStatusHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);

        TableSource maritalStatusTable = SourceFactory.eINSTANCE.createTableSource();
        maritalStatusTable.setTable(CatalogSupplier.TABLE_CUSTOMER);
        maritalStatusHierarchy.setSource(maritalStatusTable);

        Level maritalStatusLevel = LevelFactory.eINSTANCE.createLevel();
        maritalStatusLevel.setName("Marital Status");
        maritalStatusLevel.setColumn(CatalogSupplier.COLUMN_MARITAL_STATUS_CUSTOMER);
        maritalStatusLevel.setUniqueMembers(true);

        maritalStatusHierarchy.getLevels().add(maritalStatusLevel);
        maritalStatusDimension.getHierarchies().add(maritalStatusHierarchy);

        DimensionConnector maritalStatusDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        maritalStatusDimConn.setOverrideDimensionName("Marital Status");
        maritalStatusDimConn.setDimension(maritalStatusDimension);
        maritalStatusDimConn.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);

        // Yearly Income Dimension (private)
        StandardDimension yearlyIncomeDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        yearlyIncomeDimension.setName("Yearly Income");

        ExplicitHierarchy yearlyIncomeHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        yearlyIncomeHierarchy.setHasAll(true);
        yearlyIncomeHierarchy.setAllMemberName("All Marital Status");
        yearlyIncomeHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);

        TableSource yearlyIncomeTable = SourceFactory.eINSTANCE.createTableSource();
        yearlyIncomeTable.setTable(CatalogSupplier.TABLE_CUSTOMER);
        yearlyIncomeHierarchy.setSource(yearlyIncomeTable);

        Level yearlyIncomeLevel = LevelFactory.eINSTANCE.createLevel();
        yearlyIncomeLevel.setName("Yearly Income");
        yearlyIncomeLevel.setColumn(CatalogSupplier.COLUMN_YEARLY_INCOME_CUSTOMER);
        yearlyIncomeLevel.setUniqueMembers(true);

        yearlyIncomeHierarchy.getLevels().add(yearlyIncomeLevel);
        yearlyIncomeDimension.getHierarchies().add(yearlyIncomeHierarchy);

        DimensionConnector yearlyIncomeDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        yearlyIncomeDimConn.setOverrideDimensionName("Yearly Income");
        yearlyIncomeDimConn.setDimension(yearlyIncomeDimension);
        yearlyIncomeDimConn.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);

        // Add all dimension connectors to Sales cube
        sales.getDimensionConnectors().addAll(List.of(
            storeDimConn, storeSizeDimConn, storeTypeDimConn, timeDimConn, productDimConn,
            promotionMediaDimConn, promotionsDimConn, customersDimConn, educationLevelDimConn,
            genderDimConn, maritalStatusDimConn, yearlyIncomeDimConn
        ));

        // Sales Cube Measures
        SumMeasure measuresUnitSales = MeasureFactory.eINSTANCE.createSumMeasure();
        measuresUnitSales.setName("Unit Sales");
        measuresUnitSales.setColumn(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT);
        measuresUnitSales.setFormatString("Standard");

        SumMeasure measuresStoreCost = MeasureFactory.eINSTANCE.createSumMeasure();
        measuresStoreCost.setName("Store Cost");
        measuresStoreCost.setColumn(CatalogSupplier.COLUMN_STORE_COST_SALESFACT);
        measuresStoreCost.setFormatString("#,###.00");

        SumMeasure measuresStoreSales = MeasureFactory.eINSTANCE.createSumMeasure();
        measuresStoreSales.setName("Store Sales");
        measuresStoreSales.setColumn(CatalogSupplier.COLUMN_STORE_SALES_SALESFACT);
        measuresStoreSales.setFormatString("#,###.00");

        SumMeasure measuresSalesCount = MeasureFactory.eINSTANCE.createSumMeasure();
        measuresSalesCount.setName("Sales Count");
        measuresSalesCount.setColumn(CatalogSupplier.COLUMN_PRODUCT_ID_SALESFACT);
        measuresSalesCount.setFormatString("#,###");

        SumMeasure measuresCustomerCount = MeasureFactory.eINSTANCE.createSumMeasure();
        measuresCustomerCount.setName("Customer Count");
        measuresCustomerCount.setColumn(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);
        measuresCustomerCount.setFormatString("#,###");

        MeasureGroup salesMeasureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        salesMeasureGroup.getMeasures().addAll(List.of(
            measuresUnitSales, measuresStoreCost, measuresStoreSales,
            measuresSalesCount, measuresCustomerCount
        ));
        sales.getMeasureGroups().add(salesMeasureGroup);

        // Sales Cube Calculated Members
        CalculatedMember profitCalcMember = LevelFactory.eINSTANCE.createCalculatedMember();
        profitCalcMember.setName("Profit");
        profitCalcMember.setFormula(mdx("[Measures].[Store Sales] - [Measures].[Store Cost]"));

        CalculatedMemberProperty profitFormatProp = LevelFactory.eINSTANCE.createCalculatedMemberProperty();
        profitFormatProp.setName("FORMAT_STRING");
        profitFormatProp.setValue("$#,##0.00");
        profitCalcMember.getCalculatedMemberProperties().add(profitFormatProp);

        CalculatedMember profitLastPeriodCalcMember = LevelFactory.eINSTANCE.createCalculatedMember();
        profitLastPeriodCalcMember.setName("Profit last Period");
        profitLastPeriodCalcMember.setFormula(mdx("COALESCEEMPTY((Measures.[Profit], [Time].PREVMEMBER),    Measures.[Profit])"));
        profitLastPeriodCalcMember.setVisible(false);

        CalculatedMember profitGrowthCalcMember = LevelFactory.eINSTANCE.createCalculatedMember();
        profitGrowthCalcMember.setName("Profit Growth");
        profitGrowthCalcMember.setFormula(mdx("([Measures].[Profit] - [Measures].[Profit last Period]) / [Measures].[Profit last Period]"));
        profitGrowthCalcMember.setVisible(true);

        CalculatedMemberProperty profitGrowthFormatProp = LevelFactory.eINSTANCE.createCalculatedMemberProperty();
        profitGrowthFormatProp.setName("FORMAT_STRING");
        profitGrowthFormatProp.setValue("0.0%");
        profitGrowthCalcMember.getCalculatedMemberProperties().add(profitGrowthFormatProp);

        sales.getCalculatedMembers().addAll(List.of(
            profitCalcMember, profitLastPeriodCalcMember, profitGrowthCalcMember
        ));

        // Warehouse Cube
        PhysicalCube warehouse = CubeFactory.eINSTANCE.createPhysicalCube();
        warehouse.setName("Warehouse");

        TableSource warehouseTableQuery = SourceFactory.eINSTANCE.createTableSource();
        warehouseTableQuery.setTable(CatalogSupplier.TABLE_INVENTORY_FACT);
        warehouse.setSource(warehouseTableQuery);

        // Warehouse Cube Dimension Connectors
        DimensionConnector whStoreDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        whStoreDimConn.setOverrideDimensionName("Store");
        whStoreDimConn.setDimension(storeDimension);
        whStoreDimConn.setForeignKey(CatalogSupplier.COLUMN_STORE_ID_INVENTORY_FACT);

        DimensionConnector whStoreSizeDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        whStoreSizeDimConn.setOverrideDimensionName("Store Size in SQFT");
        whStoreSizeDimConn.setDimension(storeSizeSQFTDimension);
        whStoreSizeDimConn.setForeignKey(CatalogSupplier.COLUMN_STORE_ID_INVENTORY_FACT);

        DimensionConnector whStoreTypeDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        whStoreTypeDimConn.setOverrideDimensionName("Store Type");
        whStoreTypeDimConn.setDimension(storeTypeDimension);
        whStoreTypeDimConn.setForeignKey(CatalogSupplier.COLUMN_STORE_ID_INVENTORY_FACT);

        DimensionConnector whTimeDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        whTimeDimConn.setOverrideDimensionName("Time");
        whTimeDimConn.setDimension(timeDimension);
        whTimeDimConn.setForeignKey(CatalogSupplier.COLUMN_TIME_ID_INVENTORY_FACT);

        DimensionConnector whProductDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        whProductDimConn.setOverrideDimensionName("Product");
        whProductDimConn.setDimension(productDimension);
        whProductDimConn.setForeignKey(CatalogSupplier.COLUMN_PRODUCT_ID_INVENTORY_FACT);

        DimensionConnector whWarehouseDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        whWarehouseDimConn.setOverrideDimensionName("Warehouse");
        whWarehouseDimConn.setDimension(warehouseDimension);
        whWarehouseDimConn.setForeignKey(CatalogSupplier.COLUMN_WAREHOUSE_ID_INVENTORY_FACT);

        warehouse.getDimensionConnectors().addAll(List.of(
            whStoreDimConn, whStoreSizeDimConn, whStoreTypeDimConn,
            whTimeDimConn, whProductDimConn, whWarehouseDimConn
        ));

        // Warehouse Cube Measures
        SumMeasure warehouseMeasuresStoreInvoice = MeasureFactory.eINSTANCE.createSumMeasure();
        warehouseMeasuresStoreInvoice.setName("Store Invoice");
        warehouseMeasuresStoreInvoice.setColumn(CatalogSupplier.COLUMN_STORE_INVOICE_INVENTORY_FACT);

        SumMeasure warehouseMeasuresSupplyTime = MeasureFactory.eINSTANCE.createSumMeasure();
        warehouseMeasuresSupplyTime.setName("Supply Time");
        warehouseMeasuresSupplyTime.setColumn(CatalogSupplier.COLUMN_SUPPLY_TIME_INVENTORY_FACT);

        SumMeasure warehouseMeasuresWarehouseCost = MeasureFactory.eINSTANCE.createSumMeasure();
        warehouseMeasuresWarehouseCost.setName("Warehouse Cost");
        warehouseMeasuresWarehouseCost.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_COST_INVENTORY_FACT);

        SumMeasure warehouseMeasuresWarehouseSales = MeasureFactory.eINSTANCE.createSumMeasure();
        warehouseMeasuresWarehouseSales.setName("Warehouse Sales");
        warehouseMeasuresWarehouseSales.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_SALES_INVENTORY_FACT);

        SumMeasure warehouseMeasuresUnitsShipped = MeasureFactory.eINSTANCE.createSumMeasure();
        warehouseMeasuresUnitsShipped.setName("Units Shipped");
        warehouseMeasuresUnitsShipped.setColumn(CatalogSupplier.COLUMN_UNITS_SHIPPED_INVENTORY_FACT);
        warehouseMeasuresUnitsShipped.setFormatString("#.0");

        SumMeasure warehouseMeasuresUnitsOrdered = MeasureFactory.eINSTANCE.createSumMeasure();
        warehouseMeasuresUnitsOrdered.setName("Units Ordered");
        warehouseMeasuresUnitsOrdered.setColumn(CatalogSupplier.COLUMN_UNITS_ORDERED_INVENTORY_FACT);
        warehouseMeasuresUnitsOrdered.setFormatString("#.0");

        SumMeasure warehouseMeasuresWarehouseProfit = MeasureFactory.eINSTANCE.createSumMeasure();
        warehouseMeasuresWarehouseProfit.setName("Warehouse Profit");
        warehouseMeasuresWarehouseProfit.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_COST_INVENTORY_FACT);

        MeasureGroup warehouseMeasureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        warehouseMeasureGroup.getMeasures().addAll(List.of(
            warehouseMeasuresStoreInvoice, warehouseMeasuresSupplyTime,
            warehouseMeasuresWarehouseCost, warehouseMeasuresWarehouseSales,
            warehouseMeasuresUnitsShipped, warehouseMeasuresUnitsOrdered,
            warehouseMeasuresWarehouseProfit
        ));
        warehouse.getMeasureGroups().add(warehouseMeasureGroup);

        // Store Cube
        PhysicalCube storeCube = CubeFactory.eINSTANCE.createPhysicalCube();
        storeCube.setName("Store");

        TableSource storeTableQuery2 = SourceFactory.eINSTANCE.createTableSource();
        storeTableQuery2.setTable(CatalogSupplier.TABLE_STORE);
        storeCube.setSource(storeTableQuery2);

        // Store Type Dimension (private for Store cube)
        StandardDimension storeTypePrivateDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        storeTypePrivateDimension.setName("Store Type");

        ExplicitHierarchy storeTypePrivateHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        storeTypePrivateHierarchy.setHasAll(true);

        Level storeTypePrivateLevel = LevelFactory.eINSTANCE.createLevel();
        storeTypePrivateLevel.setName("Store Type");
        storeTypePrivateLevel.setColumn(CatalogSupplier.COLUMN_STORE_TYPE_STORE);
        storeTypePrivateLevel.setUniqueMembers(true);

        storeTypePrivateHierarchy.getLevels().add(storeTypePrivateLevel);
        storeTypePrivateDimension.getHierarchies().add(storeTypePrivateHierarchy);

        DimensionConnector storeTypePrivateDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        storeTypePrivateDimConn.setOverrideDimensionName("Store Type");
        storeTypePrivateDimConn.setDimension(storeTypePrivateDimension);

        // Store Dimension connector
        DimensionConnector storeStoreDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        storeStoreDimConn.setOverrideDimensionName("Store");
        storeStoreDimConn.setDimension(storeDimension);

        // Has coffee bar Dimension (private)
        StandardDimension hasCoffeeBarDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        hasCoffeeBarDimension.setName("Has coffee bar");

        ExplicitHierarchy hasCoffeeBarHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        hasCoffeeBarHierarchy.setHasAll(true);

        Level hasCoffeeBarLevel = LevelFactory.eINSTANCE.createLevel();
        hasCoffeeBarLevel.setName("Has coffee bar");
        hasCoffeeBarLevel.setColumn(CatalogSupplier.COLUMN_COFFEE_BAR_STORE);
        hasCoffeeBarLevel.setUniqueMembers(true);

        hasCoffeeBarHierarchy.getLevels().add(hasCoffeeBarLevel);
        hasCoffeeBarDimension.getHierarchies().add(hasCoffeeBarHierarchy);

        DimensionConnector hasCoffeeBarDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        hasCoffeeBarDimConn.setOverrideDimensionName("Has coffee bar");
        hasCoffeeBarDimConn.setDimension(hasCoffeeBarDimension);

        storeCube.getDimensionConnectors().addAll(List.of(
            storeTypePrivateDimConn, storeStoreDimConn, hasCoffeeBarDimConn
        ));

        // Store Cube Measures
        SumMeasure storeSqftMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        storeSqftMeasure.setName("Store Sqft");
        storeSqftMeasure.setColumn(CatalogSupplier.COLUMN_STORE_SQFT_STORE);
        storeSqftMeasure.setFormatString("#,###");

        SumMeasure grocerySqftMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        grocerySqftMeasure.setName("Grocery Sqft");
        grocerySqftMeasure.setColumn(CatalogSupplier.COLUMN_GROCERY_SQFT_STORE);
        grocerySqftMeasure.setFormatString("#,###");

        MeasureGroup storeMeasureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        storeMeasureGroup.getMeasures().addAll(List.of(storeSqftMeasure, grocerySqftMeasure));
        storeCube.getMeasureGroups().add(storeMeasureGroup);

        // HR Cube (simplified version - showing the EMF pattern for key components)
        // Full implementation would include all joins and dimensions as in MyFoodmartModifier
        PhysicalCube hrCube = CubeFactory.eINSTANCE.createPhysicalCube();
        hrCube.setName("HR");

        TableSource hrTableQuery = SourceFactory.eINSTANCE.createTableSource();
        hrTableQuery.setTable(CatalogSupplier.TABLE_SALARY);
        hrCube.setSource(hrTableQuery);

        // Time Dimension (private for HR cube)
        TimeDimension hrTimeDimension = DimensionFactory.eINSTANCE.createTimeDimension();
        hrTimeDimension.setName("Time");

        ExplicitHierarchy hrTimeHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        hrTimeHierarchy.setHasAll(false);
        hrTimeHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_THE_DATE_TIME);

        TableSource hrTimeTable = SourceFactory.eINSTANCE.createTableSource();
        hrTimeTable.setTable(CatalogSupplier.TABLE_TIME_BY_DAY);
        hrTimeHierarchy.setSource(hrTimeTable);

        Level hrYearLevel = LevelFactory.eINSTANCE.createLevel();
        hrYearLevel.setName("Year");
        hrYearLevel.setColumn(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY);
        hrYearLevel.setColumnType(ColumnInternalDataType.NUMERIC);
        hrYearLevel.setUniqueMembers(true);
        hrYearLevel.setType(LevelDefinition.TIME_YEARS);

        Level hrQuarterLevel = LevelFactory.eINSTANCE.createLevel();
        hrQuarterLevel.setName("Quarter");
        hrQuarterLevel.setColumn(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY);
        hrQuarterLevel.setUniqueMembers(false);
        hrQuarterLevel.setType(LevelDefinition.TIME_QUARTERS);

        Level hrMonthLevel = LevelFactory.eINSTANCE.createLevel();
        hrMonthLevel.setName("Month");
        hrMonthLevel.setColumn(CatalogSupplier.COLUMN_THE_MONTH_TIME_BY_DAY);
        hrMonthLevel.setColumnType(ColumnInternalDataType.NUMERIC);
        hrMonthLevel.setUniqueMembers(false);
        hrMonthLevel.setType(LevelDefinition.TIME_MONTHS);

        hrTimeHierarchy.getLevels().addAll(List.of(hrYearLevel, hrQuarterLevel, hrMonthLevel));
        hrTimeDimension.getHierarchies().add(hrTimeHierarchy);

        DimensionConnector hrTimeDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        hrTimeDimConn.setOverrideDimensionName("Time");
        hrTimeDimConn.setDimension(hrTimeDimension);
        hrTimeDimConn.setForeignKey(CatalogSupplier.COLUMN_PAY_DATE_SALARY);

        // NOTE: Full HR cube would include Store, Pay Type, Store Type, Position, Department dimensions with joins
        // For brevity, showing the pattern with Employees parent-child hierarchy

        // Employees Dimension (parent-child with closure table)
        StandardDimension employeesDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        employeesDimension.setName("Employees");

        ParentChildHierarchy employeesHierarchy = HierarchyFactory.eINSTANCE.createParentChildHierarchy();
        employeesHierarchy.setHasAll(true);
        employeesHierarchy.setAllMemberName("All Employees");
        employeesHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_EMPLOYEE_ID_EMPLOYEE);

        TableSource employeeTable = SourceFactory.eINSTANCE.createTableSource();
        employeeTable.setTable(CatalogSupplier.TABLE_EMPLOYEE);
        employeesHierarchy.setSource(employeeTable);

        employeesHierarchy.setParentColumn(CatalogSupplier.COLUMN_SUPERVISOR_ID_EMPLOYEE);
        employeesHierarchy.setNullParentValue("0");

        // Parent-Child Link (closure table)
        ParentChildLink pcLink = HierarchyFactory.eINSTANCE.createParentChildLink();
        pcLink.setParentColumn(CatalogSupplier.COLUMN_SUPERVISOR_ID_EMPLOYEE_CLOSURE);
        pcLink.setChildColumn(CatalogSupplier.COLUMN_EMPLOYEE_ID_EMPLOYEE_CLOSURE);

        TableSource closureTable = SourceFactory.eINSTANCE.createTableSource();
        closureTable.setTable(CatalogSupplier.TABLE_EMPLOYEE_CLOSURE);
        pcLink.setTable(closureTable);

        employeesHierarchy.setParentChildLink(pcLink);

        // Employee Level
        Level employeeLevel = LevelFactory.eINSTANCE.createLevel();
        employeeLevel.setName("Employee Id");
        employeeLevel.setColumnType(ColumnInternalDataType.NUMERIC);
        employeeLevel.setColumn(CatalogSupplier.COLUMN_EMPLOYEE_ID_EMPLOYEE);
        employeeLevel.setNameColumn(CatalogSupplier.COLUMN_FULL_NAME_EMPLOYEE);
        employeeLevel.setUniqueMembers(true);

        // Employee properties
        MemberProperty maritalStatusEmpProp = LevelFactory.eINSTANCE.createMemberProperty();
        maritalStatusEmpProp.setName("Marital Status");
        maritalStatusEmpProp.setColumn(CatalogSupplier.COLUMN_MARITAL_STATUS_EMPLOYEE);

        MemberProperty positionTitleEmpProp = LevelFactory.eINSTANCE.createMemberProperty();
        positionTitleEmpProp.setName("Position Title");
        positionTitleEmpProp.setColumn(CatalogSupplier.COLUMN_POSITION_TITLE_EMPLOYEE);

        MemberProperty genderEmpProp = LevelFactory.eINSTANCE.createMemberProperty();
        genderEmpProp.setName("Gender");
        genderEmpProp.setColumn(CatalogSupplier.COLUMN_GENDER_EMPLOYEE);

        MemberProperty salaryEmpProp = LevelFactory.eINSTANCE.createMemberProperty();
        salaryEmpProp.setName("Salary");
        salaryEmpProp.setColumn(CatalogSupplier.COLUMN_SALARY_EMPLOYEE);

        MemberProperty educationLevelEmpProp = LevelFactory.eINSTANCE.createMemberProperty();
        educationLevelEmpProp.setName("Education Level");
        educationLevelEmpProp.setColumn(CatalogSupplier.COLUMN_EDUCATION_LEVEL_EMPLOYEE);

        MemberProperty managementRoleEmpProp = LevelFactory.eINSTANCE.createMemberProperty();
        managementRoleEmpProp.setName("Management Role");
        managementRoleEmpProp.setColumn(CatalogSupplier.COLUMN_MANAGEMENT_ROLE_EMPLOYEE);

        employeeLevel.getMemberProperties().addAll(List.of(
            maritalStatusEmpProp, positionTitleEmpProp, genderEmpProp,
            salaryEmpProp, educationLevelEmpProp, managementRoleEmpProp
        ));

        employeesHierarchy.setLevel(employeeLevel);
        employeesDimension.getHierarchies().add(employeesHierarchy);

        DimensionConnector employeesDimConn = DimensionFactory.eINSTANCE.createDimensionConnector();
        employeesDimConn.setOverrideDimensionName("Employees");
        employeesDimConn.setDimension(employeesDimension);
        employeesDimConn.setForeignKey(CatalogSupplier.COLUMN_EMPLOYEE_ID_SALARY);

        hrCube.getDimensionConnectors().addAll(List.of(hrTimeDimConn, employeesDimConn));

        // HR Cube Measures
        SumMeasure orgSalaryMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        orgSalaryMeasure.setName("Org Salary");
        orgSalaryMeasure.setColumn(CatalogSupplier.COLUMN_SALARY_PAID_SALARY);
        orgSalaryMeasure.setFormatString("Currency");

        CountMeasure countMeasure = MeasureFactory.eINSTANCE.createCountMeasure();
        countMeasure.setName("Count");
        countMeasure.setColumn(CatalogSupplier.COLUMN_EMPLOYEE_ID_SALARY);
        countMeasure.setFormatString("#,#");

        CountMeasure numEmployeesMeasure = MeasureFactory.eINSTANCE.createCountMeasure();
        numEmployeesMeasure.setName("Number of Employees");
        numEmployeesMeasure.setColumn(CatalogSupplier.COLUMN_EMPLOYEE_ID_SALARY);
        numEmployeesMeasure.setDistinct(true);
        numEmployeesMeasure.setFormatString("#,#");

        MeasureGroup hrMeasureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        hrMeasureGroup.getMeasures().addAll(List.of(orgSalaryMeasure, countMeasure, numEmployeesMeasure));
        hrCube.getMeasureGroups().add(hrMeasureGroup);

        // HR Cube Calculated Members
        CalculatedMember employeeSalaryCalcMember = LevelFactory.eINSTANCE.createCalculatedMember();
        employeeSalaryCalcMember.setName("Employee Salary");
        employeeSalaryCalcMember.setFormatString("Currency");
        employeeSalaryCalcMember.setFormula(mdx("([Employees].currentmember.datamember, [Measures].[Org Salary])"));

        CalculatedMember avgSalaryCalcMember = LevelFactory.eINSTANCE.createCalculatedMember();
        avgSalaryCalcMember.setName("Avg Salary");
        avgSalaryCalcMember.setFormatString("Currency");
        avgSalaryCalcMember.setFormula(mdx("[Measures].[Org Salary]/[Measures].[Number of Employees]"));

        hrCube.getCalculatedMembers().addAll(List.of(employeeSalaryCalcMember, avgSalaryCalcMember));

        // Add all cubes to catalog
        catalog.getImportedElement().addAll(List.of(sales, warehouse, storeCube, hrCube));

        // NOTE: Sales Ragged cube, Virtual cubes, and Access Roles implementation would follow similar patterns
        // Pattern: create with factory, set properties, add to collections.
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
