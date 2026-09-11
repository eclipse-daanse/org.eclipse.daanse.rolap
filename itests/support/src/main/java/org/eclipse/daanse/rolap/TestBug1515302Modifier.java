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
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
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
import org.eclipse.emf.ecore.util.EcoreUtil;
/**
 * EMF version of TestBug1515302Modifier from NonEmptyTest. Creates a cube
 * "Bug1515302" with Promotions and Customers dimensions.
 *
 * <Cube name="Bug1515302">
 * <Table name="sales_fact_1997"/>
 * <Dimension name="Promotions" foreignKey="promotion_id">
 * <Hierarchy hasAll="false" primaryKey="promotion_id">
 * <Table name="promotion"/>
 * <Level name="Promotion Name" column="promotion_name" uniqueMembers="true"/>
 * </Hierarchy> </Dimension>
 * <Dimension name="Customers" foreignKey="customer_id">
 * <Hierarchy hasAll="true" allMemberName="All Customers" primaryKey=
 * "customer_id">
 * <Table name="customer"/>
 * <Level name="Country" column="country" uniqueMembers="true"/>
 * <Level name="State Province" column="state_province" uniqueMembers="true"/>
 * <Level name="City" column="city" uniqueMembers="false"/>
 * <Level name="Name" column="customer_id" type="Numeric" uniqueMembers="true"/>
 * </Hierarchy> </Dimension>
 * <Measure name="Unit Sales" column="unit_sales" aggregator="sum"/> </Cube>
 */
public class TestBug1515302Modifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    public TestBug1515302Modifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);
        // Static levels for Promotions dimension
        Level LEVEL_PROMOTION_NAME;

        // Static hierarchy for Promotions
        ExplicitHierarchy HIERARCHY_PROMOTIONS;
        TableSource TABLE_QUERY_PROMOTION;

        // Static dimension Promotions
        StandardDimension DIMENSION_PROMOTIONS;

        // Static dimension connector for Promotions
        DimensionConnector CONNECTOR_PROMOTIONS;

        // Static levels for Customers dimension
        Level LEVEL_COUNTRY;
        Level LEVEL_STATE_PROVINCE;
        Level LEVEL_CITY;
        Level LEVEL_NAME;

        // Static hierarchy for Customers
        ExplicitHierarchy HIERARCHY_CUSTOMERS;
        TableSource TABLE_QUERY_CUSTOMER;

        // Static dimension Customers
        StandardDimension DIMENSION_CUSTOMERS;

        // Static dimension connector for Customers
        DimensionConnector CONNECTOR_CUSTOMERS;

        // Static table query for fact table
        TableSource TABLE_QUERY_SALES_FACT;

        // Static measure
        SumMeasure MEASURE_UNIT_SALES;

        // Static measure group
        MeasureGroup MEASURE_GROUP;

        // Static cube
        PhysicalCube CUBE_BUG1515302;

        // Create Promotions dimension
        LEVEL_PROMOTION_NAME = LevelFactory.eINSTANCE.createLevel();
        LEVEL_PROMOTION_NAME.setName("Promotion Name");
        LEVEL_PROMOTION_NAME.setColumn(CatalogSupplier.COLUMN_PROMOTION_NAME_PROMOTION);
        LEVEL_PROMOTION_NAME.setUniqueMembers(true);

        TABLE_QUERY_PROMOTION = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_PROMOTION.setTable(CatalogSupplier.TABLE_PROMOTION);

        HIERARCHY_PROMOTIONS = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_PROMOTIONS.setHasAll(false);
        HIERARCHY_PROMOTIONS.setPrimaryKey(CatalogSupplier.COLUMN_PROMOTION_ID_PROMOTION);
        HIERARCHY_PROMOTIONS.setSource(TABLE_QUERY_PROMOTION);
        HIERARCHY_PROMOTIONS.getLevels().add(LEVEL_PROMOTION_NAME);

        DIMENSION_PROMOTIONS = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_PROMOTIONS.setName("Promotions");
        DIMENSION_PROMOTIONS.getHierarchies().add(HIERARCHY_PROMOTIONS);

        CONNECTOR_PROMOTIONS = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_PROMOTIONS.setOverrideDimensionName("Promotions");
        CONNECTOR_PROMOTIONS.setForeignKey(CatalogSupplier.COLUMN_PROMOTION_ID_SALESFACT);
        CONNECTOR_PROMOTIONS.setDimension(DIMENSION_PROMOTIONS);

        // Create Customers dimension
        LEVEL_COUNTRY = LevelFactory.eINSTANCE.createLevel();
        LEVEL_COUNTRY.setName("Country");
        LEVEL_COUNTRY.setColumn(CatalogSupplier.COLUMN_COUNTRY_CUSTOMER);
        LEVEL_COUNTRY.setUniqueMembers(true);

        LEVEL_STATE_PROVINCE = LevelFactory.eINSTANCE.createLevel();
        LEVEL_STATE_PROVINCE.setName("State Province");
        LEVEL_STATE_PROVINCE.setColumn(CatalogSupplier.COLUMN_STATE_PROVINCE_CUSTOMER);
        LEVEL_STATE_PROVINCE.setUniqueMembers(true);

        LEVEL_CITY = LevelFactory.eINSTANCE.createLevel();
        LEVEL_CITY.setName("City");
        LEVEL_CITY.setColumn(CatalogSupplier.COLUMN_CITY_CUSTOMER);
        LEVEL_CITY.setUniqueMembers(false);

        LEVEL_NAME = LevelFactory.eINSTANCE.createLevel();
        LEVEL_NAME.setName("Name");
        LEVEL_NAME.setColumn(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);
        LEVEL_NAME.setColumnType(ColumnInternalDataType.NUMERIC);
        LEVEL_NAME.setUniqueMembers(true);

        TABLE_QUERY_CUSTOMER = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_CUSTOMER.setTable(CatalogSupplier.TABLE_CUSTOMER);

        HIERARCHY_CUSTOMERS = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_CUSTOMERS.setHasAll(true);
        HIERARCHY_CUSTOMERS.setAllMemberName("All Customers");
        HIERARCHY_CUSTOMERS.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);
        HIERARCHY_CUSTOMERS.setSource(TABLE_QUERY_CUSTOMER);
        HIERARCHY_CUSTOMERS.getLevels().addAll(List.of(LEVEL_COUNTRY, LEVEL_STATE_PROVINCE, LEVEL_CITY, LEVEL_NAME));

        DIMENSION_CUSTOMERS = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_CUSTOMERS.setName("Customers");
        DIMENSION_CUSTOMERS.getHierarchies().add(HIERARCHY_CUSTOMERS);

        CONNECTOR_CUSTOMERS = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_CUSTOMERS.setOverrideDimensionName("Customers");
        CONNECTOR_CUSTOMERS.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);
        CONNECTOR_CUSTOMERS.setDimension(DIMENSION_CUSTOMERS);

        // Create fact table query
        TABLE_QUERY_SALES_FACT = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_SALES_FACT.setTable(CatalogSupplier.TABLE_SALES_FACT);

        // Create measure
        MEASURE_UNIT_SALES = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_UNIT_SALES.setName("Unit Sales");
        MEASURE_UNIT_SALES.setColumn(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT);

        // Create measure group
        MEASURE_GROUP = CubeFactory.eINSTANCE.createMeasureGroup();
        MEASURE_GROUP.getMeasures().add(MEASURE_UNIT_SALES);

        // Create cube
        CUBE_BUG1515302 = CubeFactory.eINSTANCE.createPhysicalCube();
        CUBE_BUG1515302.setName("Bug1515302");
        CUBE_BUG1515302.setSource(TABLE_QUERY_SALES_FACT);
        CUBE_BUG1515302.getDimensionConnectors().addAll(List.of(CONNECTOR_PROMOTIONS, CONNECTOR_CUSTOMERS));
        CUBE_BUG1515302.getMeasureGroups().add(MEASURE_GROUP);

        // Add the cube to the catalog
        this.catalog.getImportedElement().add(CUBE_BUG1515302);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
