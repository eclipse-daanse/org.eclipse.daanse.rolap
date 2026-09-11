/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.aggmatcher;


import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationColumnName;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationFactory;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationLevel;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationMeasure;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.ExplicitAggregationTable;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.database.relational.OrderedColumn;
import org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory;
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
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.MemberProperty;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
public class MultipleColsInTupleAggTestModifierEmf implements CatalogMappingSupplier {

    private final CatalogImpl originalCatalog;

    public MultipleColsInTupleAggTestModifierEmf(Catalog catalog) {
        this.originalCatalog = EmfUtil.copy((CatalogImpl) catalog);
        // Create fact table columns
        Column prodIdFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        prodIdFact.setName("prod_id");
        prodIdFact.setType(SQLSimpleTypes.Sql99.integerType());

        Column storeIdFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        storeIdFact.setName("store_id");
        storeIdFact.setType(SQLSimpleTypes.Sql99.integerType());

        Column amountFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        amountFact.setName("amount");
        amountFact.setType(SQLSimpleTypes.Sql99.integerType());

        // Create fact table
        Table fact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        fact.setName("fact");
        fact.getFeature().add(prodIdFact);
        fact.getFeature().add(storeIdFact);
        fact.getFeature().add(amountFact);

        // Create store_csv table columns
        Column storeIdStoreCsv = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        storeIdStoreCsv.setName("store_id");
        storeIdStoreCsv.setType(SQLSimpleTypes.Sql99.integerType());

        Column valueStoreCsv = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        valueStoreCsv.setName("value");
        valueStoreCsv.setType(SQLSimpleTypes.Sql99.integerType());

        // Create store_csv table
        Table storeCsv = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        storeCsv.setName("store_csv");
        storeCsv.getFeature().add(storeIdStoreCsv);
        storeCsv.getFeature().add(valueStoreCsv);

        // Create product_csv table columns
        Column prodIdProductCsv = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        prodIdProductCsv.setName("prod_id");
        prodIdProductCsv.setType(SQLSimpleTypes.Sql99.integerType());

        Column prodCatProductCsv = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        prodCatProductCsv.setName("prod_cat");
        prodCatProductCsv.setType(SQLSimpleTypes.Sql99.integerType());

        Column name1ProductCsv = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        name1ProductCsv.setName("name1");
        name1ProductCsv.setType(SQLSimpleTypes.varcharType(255));
        // name1ProductCsv.setCharOctetLength(30);

        Column colorProductCsv = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        colorProductCsv.setName("color");
        colorProductCsv.setType(SQLSimpleTypes.varcharType(255));
        // colorProductCsv.setCharOctetLength(30);

        // Create product_csv table
        Table productCsv = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        productCsv.setName("product_csv");
        productCsv.getFeature().add(prodIdProductCsv);
        productCsv.getFeature().add(prodCatProductCsv);
        productCsv.getFeature().add(name1ProductCsv);
        productCsv.getFeature().add(colorProductCsv);

        // Create cat table columns
        Column catCat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        catCat.setName("cat");
        catCat.setType(SQLSimpleTypes.Sql99.integerType());

        Column name3Cat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        name3Cat.setName("name3");
        name3Cat.setType(SQLSimpleTypes.varcharType(255));
        // name3Cat.setCharOctetLength(30);

        Column ordCat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        ordCat.setName("ord");
        ordCat.setType(SQLSimpleTypes.Sql99.integerType());

        Column capCat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        capCat.setName("cap");
        capCat.setType(SQLSimpleTypes.varcharType(255));
        // capCat.setCharOctetLength(30);

        // Create cat table
        Table cat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        cat.setName("cat");
        cat.getFeature().add(catCat);
        cat.getFeature().add(name3Cat);
        cat.getFeature().add(ordCat);
        cat.getFeature().add(capCat);

        // Create product_cat table columns
        Column prodCatProductCat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        prodCatProductCat.setName("prod_cat");
        prodCatProductCat.setType(SQLSimpleTypes.Sql99.integerType());

        Column catProductCat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        catProductCat.setName("cat");
        catProductCat.setType(SQLSimpleTypes.Sql99.integerType());

        Column name2ProductCat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        name2ProductCat.setName("name2");
        name2ProductCat.setType(SQLSimpleTypes.varcharType(255));
        // name2ProductCat.setCharOctetLength(30);

        Column ordProductCat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        ordProductCat.setName("ord");
        ordProductCat.setType(SQLSimpleTypes.Sql99.integerType());

        Column capProductCat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        capProductCat.setName("cap");
        capProductCat.setType(SQLSimpleTypes.varcharType(255));

        // Create product_cat table
        Table productCat = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        productCat.setName("product_cat");
        productCat.getFeature().add(prodCatProductCat);
        productCat.getFeature().add(catProductCat);
        productCat.getFeature().add(name2ProductCat);
        productCat.getFeature().add(ordProductCat);
        productCat.getFeature().add(capProductCat);

        // Create test_lp_xxx_fact aggregation table columns
        Column categoryTestLpXxxFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        categoryTestLpXxxFact.setName("category");
        categoryTestLpXxxFact.setType(SQLSimpleTypes.Sql99.integerType());

        Column productCategoryTestLpXxxFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        productCategoryTestLpXxxFact.setName("product_category");
        productCategoryTestLpXxxFact.setType(SQLSimpleTypes.varcharType(255));
        // productCategoryTestLpXxxFact.setCharOctetLength(30);

        Column amountTestLpXxxFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        amountTestLpXxxFact.setName("amount");
        amountTestLpXxxFact.setType(SQLSimpleTypes.Sql99.integerType());

        Column factCountTestLpXxxFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        factCountTestLpXxxFact.setName("fact_count");
        factCountTestLpXxxFact.setType(SQLSimpleTypes.Sql99.integerType());

        // Create test_lp_xxx_fact aggregation table
        Table testLpXxxFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        testLpXxxFact.setName("test_lp_xxx_fact");
        testLpXxxFact.getFeature().add(categoryTestLpXxxFact);
        testLpXxxFact.getFeature().add(productCategoryTestLpXxxFact);
        testLpXxxFact.getFeature().add(amountTestLpXxxFact);
        testLpXxxFact.getFeature().add(factCountTestLpXxxFact);

        // Create test_lp_xx2_fact aggregation table columns
        Column prodnameTestLpXx2Fact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        prodnameTestLpXx2Fact.setName("prodname");
        prodnameTestLpXx2Fact.setType(SQLSimpleTypes.varcharType(255));
        // prodnameTestLpXx2Fact.setCharOctetLength(30);

        Column amountTestLpXx2Fact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        amountTestLpXx2Fact.setName("amount");
        amountTestLpXx2Fact.setType(SQLSimpleTypes.Sql99.integerType());

        Column factCountTestLpXx2Fact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        factCountTestLpXx2Fact.setName("fact_count");
        factCountTestLpXx2Fact.setType(SQLSimpleTypes.Sql99.integerType());

        // Create test_lp_xx2_fact aggregation table
        Table testLpXx2Fact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        testLpXx2Fact.setName("test_lp_xx2_fact");
        testLpXx2Fact.getFeature().add(prodnameTestLpXx2Fact);
        testLpXx2Fact.getFeature().add(amountTestLpXx2Fact);
        testLpXx2Fact.getFeature().add(factCountTestLpXx2Fact);

        // Create first aggregation name (test_lp_xxx_fact)
        AggregationColumnName aggFactCount1 = AggregationFactory.eINSTANCE.createAggregationColumnName();
        aggFactCount1.setColumn(factCountTestLpXxxFact);

        AggregationMeasure aggMeasure1 = AggregationFactory.eINSTANCE.createAggregationMeasure();
        aggMeasure1.setColumn(amountTestLpXxxFact);
        aggMeasure1.setName("[Measures].[Total]");

        AggregationLevel aggLevel1 = AggregationFactory.eINSTANCE.createAggregationLevel();
        aggLevel1.setColumn(categoryTestLpXxxFact);
        aggLevel1.setName("[Product].[Product].[Category]");

        AggregationLevel aggLevel2 = AggregationFactory.eINSTANCE.createAggregationLevel();
        aggLevel2.setColumn(productCategoryTestLpXxxFact);
        aggLevel2.setName("[Product].[Product].[Product Category]");

        ExplicitAggregationTable aggregationName1 = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        aggregationName1.setTable(testLpXxxFact);
        aggregationName1.setAggregationFactCount(aggFactCount1);
        aggregationName1.getAggregationMeasures().add(aggMeasure1);
        aggregationName1.getAggregationLevels().add(aggLevel1);
        aggregationName1.getAggregationLevels().add(aggLevel2);

        // Create second aggregation name (test_lp_xx2_fact)
        AggregationColumnName aggFactCount2 = AggregationFactory.eINSTANCE.createAggregationColumnName();
        aggFactCount2.setColumn(factCountTestLpXx2Fact);

        AggregationMeasure aggMeasure2 = AggregationFactory.eINSTANCE.createAggregationMeasure();
        aggMeasure2.setColumn(amountTestLpXx2Fact);
        aggMeasure2.setName("[Measures].[Total]");

        AggregationLevel aggLevel3 = AggregationFactory.eINSTANCE.createAggregationLevel();
        aggLevel3.setColumn(prodnameTestLpXx2Fact);
        aggLevel3.setName("[Product].[Product].[Product Name]");
        aggLevel3.setCollapsed(false);

        ExplicitAggregationTable aggregationName2 = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        aggregationName2.setTable(testLpXx2Fact);
        aggregationName2.setAggregationFactCount(aggFactCount2);
        aggregationName2.getAggregationMeasures().add(aggMeasure2);
        aggregationName2.getAggregationLevels().add(aggLevel3);

        // Create table query with aggregations
        TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
        tableQuery.setTable(fact);
        tableQuery.getAggregationTables().add(aggregationName1);
        tableQuery.getAggregationTables().add(aggregationName2);

        // Create Store dimension hierarchy
        Level storeValueLevel = LevelFactory.eINSTANCE.createLevel();
        storeValueLevel.setName("Store Value");
        storeValueLevel.setColumn(valueStoreCsv);
        storeValueLevel.setUniqueMembers(true);

        TableSource storeQuery = SourceFactory.eINSTANCE.createTableSource();
        storeQuery.setTable(storeCsv);

        ExplicitHierarchy storeHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        storeHierarchy.setHasAll(true);
        storeHierarchy.setPrimaryKey(storeIdStoreCsv);
        storeHierarchy.setSource(storeQuery);
        storeHierarchy.getLevels().add(storeValueLevel);

        StandardDimension storeDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        storeDimension.setName("Store");
        storeDimension.getHierarchies().add(storeHierarchy);

        DimensionConnector storeConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        storeConnector.setOverrideDimensionName("Store");
        storeConnector.setForeignKey(storeIdFact);
        storeConnector.setDimension(storeDimension);

        // Create Product dimension with complex joins
        // Create Product Name level with property
        MemberProperty productColorProperty = LevelFactory.eINSTANCE.createMemberProperty();
        productColorProperty.setName("Product Color");
        productColorProperty.setColumn(colorProductCsv);

        Level productNameLevel = LevelFactory.eINSTANCE.createLevel();
        productNameLevel.setName("Product Name");
        productNameLevel.setColumn(name1ProductCsv);
        productNameLevel.setUniqueMembers(true);
        productNameLevel.getMemberProperties().add(productColorProperty);

        OrderedColumn oc1 = RelationalFactory.eINSTANCE.createOrderedColumn();
        oc1.setColumn(ordProductCat);

        Level productCategoryLevel = LevelFactory.eINSTANCE.createLevel();
        productCategoryLevel.setName("Product Category");
        productCategoryLevel.setColumn(name2ProductCat);
        productCategoryLevel.getOrdinalColumns().add(oc1);
        productCategoryLevel.setCaptionColumn(capProductCat);
        productCategoryLevel.setUniqueMembers(false);

        OrderedColumn oc2 = RelationalFactory.eINSTANCE.createOrderedColumn();
        oc2.setColumn(ordCat);

        Level categoryLevel = LevelFactory.eINSTANCE.createLevel();
        categoryLevel.setName("Category");
        categoryLevel.setColumn(catCat);
        categoryLevel.getOrdinalColumns().add(oc2);
        categoryLevel.setCaptionColumn(capCat);
        categoryLevel.setNameColumn(name3Cat);
        categoryLevel.setUniqueMembers(false);
        categoryLevel.setColumnType(ColumnInternalDataType.NUMERIC);

        // Create join: product_cat JOIN cat
        TableSource catQuery = SourceFactory.eINSTANCE.createTableSource();
        catQuery.setTable(cat);

        JoinedQueryElement catJoinedElement = SourceFactory.eINSTANCE.createJoinedQueryElement();
        catJoinedElement.setKey(catCat);
        catJoinedElement.setSource(catQuery);

        TableSource productCatQuery = SourceFactory.eINSTANCE.createTableSource();
        productCatQuery.setTable(productCat);

        JoinedQueryElement productCatJoinedElement = SourceFactory.eINSTANCE.createJoinedQueryElement();
        productCatJoinedElement.setKey(catProductCat);
        productCatJoinedElement.setSource(productCatQuery);

        JoinSource innerJoin = SourceFactory.eINSTANCE.createJoinSource();
        innerJoin.setLeft(productCatJoinedElement);
        innerJoin.setRight(catJoinedElement);

        // Create join: product_csv JOIN (product_cat JOIN cat)
        TableSource productCsvQuery = SourceFactory.eINSTANCE.createTableSource();
        productCsvQuery.setTable(productCsv);

        JoinedQueryElement productCsvJoinedElement = SourceFactory.eINSTANCE.createJoinedQueryElement();
        productCsvJoinedElement.setKey(prodCatProductCsv);
        productCsvJoinedElement.setSource(productCsvQuery);

        JoinedQueryElement innerJoinElement = SourceFactory.eINSTANCE.createJoinedQueryElement();
        innerJoinElement.setAlias("product_cat");
        innerJoinElement.setKey(prodCatProductCat);
        innerJoinElement.setSource(innerJoin);

        JoinSource outerJoin = SourceFactory.eINSTANCE.createJoinSource();
        outerJoin.setLeft(productCsvJoinedElement);
        outerJoin.setRight(innerJoinElement);

        ExplicitHierarchy productHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        productHierarchy.setHasAll(true);
        productHierarchy.setPrimaryKey(prodIdProductCsv);
        productHierarchy.setSource(outerJoin);
        productHierarchy.getLevels().add(categoryLevel);
        productHierarchy.getLevels().add(productCategoryLevel);
        productHierarchy.getLevels().add(productNameLevel);

        StandardDimension productDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        productDimension.setName("Product");
        productDimension.getHierarchies().add(productHierarchy);

        DimensionConnector productConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        productConnector.setOverrideDimensionName("Product");
        productConnector.setForeignKey(prodIdFact);
        productConnector.setDimension(productDimension);

        // Create measures
        SumMeasure totalMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        totalMeasure.setName("Total");
        totalMeasure.setColumn(amountFact);
        totalMeasure.setFormatString("#,###");

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(totalMeasure);

        // Create cube
        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("Fact");
        cube.setSource(tableQuery);
        cube.getDimensionConnectors().add(storeConnector);
        cube.getDimensionConnectors().add(productConnector);
        cube.getMeasureGroups().add(measureGroup);

        // Add tables to database schema
        if (Packages.available(originalCatalog, Schema.class).size() > 0) {
            Packages.available(originalCatalog, Schema.class).get(0).getOwnedElement().add(fact);
            Packages.available(originalCatalog, Schema.class).get(0).getOwnedElement().add(storeCsv);
            Packages.available(originalCatalog, Schema.class).get(0).getOwnedElement().add(productCsv);
            Packages.available(originalCatalog, Schema.class).get(0).getOwnedElement().add(cat);
            Packages.available(originalCatalog, Schema.class).get(0).getOwnedElement().add(productCat);
            Packages.available(originalCatalog, Schema.class).get(0).getOwnedElement().add(testLpXxxFact);
            Packages.available(originalCatalog, Schema.class).get(0).getOwnedElement().add(testLpXx2Fact);
        }

        // Add the cube to the catalog copy
        originalCatalog.getImportedElement().add(cube);

    }

    /*
        return "<Cube name='Fact'>\n"
       + "<Table name='fact'>\n"
       + " <AggName name='test_lp_xxx_fact'>\n"
       + "  <AggFactCount column='fact_count'/>\n"
       + "  <AggMeasure column='amount' name='[Measures].[Total]'/>\n"
       + "  <AggLevel column='category' name='[Product].[Category]'/>\n"
       + "  <AggLevel column='product_category' "
       + "            name='[Product].[Product Category]'/>\n"
       + " </AggName>\n"
        + " <AggName name='test_lp_xx2_fact'>\n"
        + "  <AggFactCount column='fact_count'/>\n"
        + "  <AggMeasure column='amount' name='[Measures].[Total]'/>\n"
        + "  <AggLevel column='prodname' name='[Product].[Product Name]' collapsed='false'/>\n"
        + " </AggName>\n"
       + "</Table>"
       + "<Dimension name='Store' foreignKey='store_id'>\n"
       + " <Hierarchy hasAll='true' primaryKey='store_id'>\n"
       + "  <Table name='store_csv'/>\n"
       + "  <Level name='Store Value' column='value' "
       + "         uniqueMembers='true'/>\n"
       + " </Hierarchy>\n"
       + "</Dimension>\n"
       + "<Dimension name='Product' foreignKey='prod_id'>\n"
       + " <Hierarchy hasAll='true' primaryKey='prod_id' "
       + "primaryKeyTable='product_csv'>\n"
       + " <Join leftKey='prod_cat' rightAlias='product_cat' "
       + "rightKey='prod_cat'>\n"
       + "  <Table name='product_csv'/>\n"
       + "  <Join leftKey='cat' rightKey='cat'>\n"
       + "   <Table name='product_cat'/>\n"
       + "   <Table name='cat'/>\n"
       + "  </Join>"
       + " </Join>\n"
       + " <Level name='Category' table='cat' column='cat' "
       + "ordinalColumn='ord' captionColumn='cap' nameColumn='name3' "
       + "uniqueMembers='false' type='Numeric'/>\n"
       + " <Level name='Product Category' table='product_cat' "
       + "column='name2' ordinalColumn='ord' captionColumn='cap' "
       + "uniqueMembers='false'/>\n"
       + " <Level name='Product Name' table='product_csv' column='name1' "
       + "uniqueMembers='true'>\n"
        + "<Property name='Product Color' table='product_csv' column='color' />"
        + "</Level>"
       + " </Hierarchy>\n"
       + "</Dimension>\n"
       + "<Measure name='Total' \n"
       + "    column='amount' aggregator='sum'\n"
       + "   formatString='#,###'/>\n"
       + "</Cube>";

     */

    @Override
    public Catalog get() {

        return originalCatalog;
    }
}
