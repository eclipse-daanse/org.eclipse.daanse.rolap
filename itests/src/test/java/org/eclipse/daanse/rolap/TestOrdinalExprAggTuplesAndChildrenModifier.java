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
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
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
/**
 * EMF version of TestOrdinalExprAggTuplesAndChildrenModifier from TestAggregationManager.
 */
public class TestOrdinalExprAggTuplesAndChildrenModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    public TestOrdinalExprAggTuplesAndChildrenModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        this.catalog = EmfUtil.copy((CatalogImpl) baseCatalog);

        // Create objects using RolapMappingFactory
        RolapMappingFactory factory = RolapMappingFactory.eINSTANCE;

        // Create TableSource for sales_fact_1997
        TableSource salesFactQuery = SourceFactory.eINSTANCE.createTableSource();
        salesFactQuery.setTable(CatalogSupplier.TABLE_SALES_FACT);

        // Create Product dimension with join between product and product_class tables
        TableSource productTableQuery = SourceFactory.eINSTANCE.createTableSource();
        productTableQuery.setTable(CatalogSupplier.TABLE_PRODUCT);

        TableSource productClassTableQuery = SourceFactory.eINSTANCE.createTableSource();
        productClassTableQuery.setTable(CatalogSupplier.TABLE_PRODUCT_CLASS);

        JoinedQueryElement productJoinLeft = SourceFactory.eINSTANCE.createJoinedQueryElement();
        productJoinLeft.setKey(CatalogSupplier.COLUMN_PRODUCT_CLASS_ID_PRODUCT);
        productJoinLeft.setSource(productTableQuery);

        JoinedQueryElement productClassJoinRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        productClassJoinRight.setKey(CatalogSupplier.COLUMN_PRODUCT_CLASS_ID_PRODUCT_CLASS);
        productClassJoinRight.setSource(productClassTableQuery);

        JoinSource productJoinQuery = SourceFactory.eINSTANCE.createJoinSource();
        productJoinQuery.setLeft(productJoinLeft);
        productJoinQuery.setRight(productClassJoinRight);

        // Create levels for Product hierarchy
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
        productCategoryLevel.setCaptionColumn(CatalogSupplier.COLUMN_PRODUCT_FAMILY_PRODUCT_CLASS);
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
        productNameLevel.setUniqueMembers(true);

        // Create Product hierarchy
        ExplicitHierarchy productHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        productHierarchy.setHasAll(true);
        productHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_PRODUCT_ID_PRODUCT);
        productHierarchy.setSource(productJoinQuery);
        productHierarchy.getLevels().add(productFamilyLevel);
        productHierarchy.getLevels().add(productDepartmentLevel);
        productHierarchy.getLevels().add(productCategoryLevel);
        productHierarchy.getLevels().add(productSubcategoryLevel);
        productHierarchy.getLevels().add(brandNameLevel);
        productHierarchy.getLevels().add(productNameLevel);

        // Create Product dimension
        StandardDimension productDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        productDimension.setName("Product");
        productDimension.getHierarchies().add(productHierarchy);

        // Create Product dimension connector
        DimensionConnector productDimensionConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        productDimensionConnector.setOverrideDimensionName("Product");
        productDimensionConnector.setForeignKey(CatalogSupplier.COLUMN_PRODUCT_ID_SALESFACT);
        productDimensionConnector.setDimension(productDimension);

        // Create Gender dimension
        TableSource customerTableQuery = SourceFactory.eINSTANCE.createTableSource();
        customerTableQuery.setTable(CatalogSupplier.TABLE_CUSTOMER);

        Level genderLevel = LevelFactory.eINSTANCE.createLevel();
        genderLevel.setName("Gender");
        genderLevel.setColumn(CatalogSupplier.COLUMN_GENDER_CUSTOMER);
        genderLevel.setUniqueMembers(true);

        ExplicitHierarchy genderHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        genderHierarchy.setHasAll(false);
        genderHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);
        genderHierarchy.setSource(customerTableQuery);
        genderHierarchy.getLevels().add(genderLevel);

        StandardDimension genderDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        genderDimension.setName("Gender");
        genderDimension.getHierarchies().add(genderHierarchy);

        // Create Gender dimension connector
        DimensionConnector genderDimensionConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        genderDimensionConnector.setOverrideDimensionName("Gender");
        genderDimensionConnector.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);
        genderDimensionConnector.setDimension(genderDimension);

        // Create measures
        SumMeasure unitSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        unitSalesMeasure.setName("Unit Sales");
        unitSalesMeasure.setColumn(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT);
        unitSalesMeasure.setFormatString("Standard");
        unitSalesMeasure.setVisible(false);

        SumMeasure storeCostMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        storeCostMeasure.setName("Store Cost");
        storeCostMeasure.setColumn(CatalogSupplier.COLUMN_STORE_COST_SALESFACT);
        storeCostMeasure.setFormatString("#,###.00");

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(unitSalesMeasure);
        measureGroup.getMeasures().add(storeCostMeasure);

        // Create Sales_Prod_Ord cube
        PhysicalCube salesProdOrdCube = CubeFactory.eINSTANCE.createPhysicalCube();
        salesProdOrdCube.setName("Sales_Prod_Ord");
        salesProdOrdCube.setSource(salesFactQuery);
        salesProdOrdCube.getDimensionConnectors().add(productDimensionConnector);
        salesProdOrdCube.getDimensionConnectors().add(genderDimensionConnector);
        salesProdOrdCube.getMeasureGroups().add(measureGroup);

        // Add cube to catalog
        this.catalog.getImportedElement().add(salesProdOrdCube);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
