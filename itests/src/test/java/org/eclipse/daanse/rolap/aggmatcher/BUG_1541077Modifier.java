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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationColumnName;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationFactory;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationForeignKey;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationMeasure;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.ExplicitAggregationTable;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.AvgMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.CountMeasure;
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
/*
return "<Cube name='Cheques'>\n"
       + "<Table name='cheques'>\n"
       + "<AggName name='agg_lp_xxx_cheques'>\n"
       + "<AggFactCount column='FACT_COUNT'/>\n"
       + "<AggForeignKey factColumn='store_id' aggColumn='store_id' />\n"
       + "<AggMeasure name='[Measures].[Avg Amount]'\n"
       + "   column='amount_AVG' />\n"
       + "</AggName>\n"
           + "</Table>\n"
           + "<Dimension name='StoreX' foreignKey='store_id'>\n"
           + " <Hierarchy hasAll='true' primaryKey='store_id'>\n"
           + " <Table name='store_x'/>\n"
           + " <Level name='Store Value' column='value' uniqueMembers='true'/>\n"
           + " </Hierarchy>\n"
           + "</Dimension>\n"
           + "<Dimension name='ProductX' foreignKey='prod_id'>\n"
           + " <Hierarchy hasAll='true' primaryKey='prod_id'>\n"
           + " <Table name='product_x'/>\n"
           + " <Level name='Store Name' column='name' uniqueMembers='true'/>\n"
           + " </Hierarchy>\n"
           + "</Dimension>\n"

           + "<Measure name='Sales Count' \n"
           + "    column='prod_id' aggregator='count'\n"
           + "   formatString='#,###'/>\n"
           + "<Measure name='Store Count' \n"
           + "    column='store_id' aggregator='distinct-count'\n"
           + "   formatString='#,###'/>\n"
           + "<Measure name='Total Amount' \n"
           + "    column='amount' aggregator='sum'\n"
           + "   formatString='#,###'/>\n"
           + "<Measure name='Avg Amount' \n"
           + "    column='amount' aggregator='avg'\n"
           + "   formatString='00.0'/>\n"
           + "</Cube>";

*/
/*
public class BUG_1541077Modifier extends PojoMappingModifier {

    public BUG_1541077Modifier(CatalogMapping c) {
        super(c);
    }


    protected List<? extends CubeMapping> catalogCubes(CatalogMapping schema) {
    	//## ColumnNames: prod_id,store_id,amount
    	//## ColumnTypes: INTEGER,INTEGER,DECIMAL(10,2)
        PhysicalColumnMappingImpl store_id_cheques = PhysicalColumnMappingImpl.builder().withName("store_id").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl prod_id_cheques = PhysicalColumnMappingImpl.builder().withName("prod_id").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl amount_cheques = PhysicalColumnMappingImpl.builder().withName("amount").withDataType(ColumnDataType.DECIMAL).withColumnSize(10).withDecimalDigits(2).build();
        PhysicalTableMappingImpl cheques = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("cheques")
                .withColumns(List.of(
                        store_id_cheques, prod_id_cheques, amount_cheques
                        ))).build();
        //## ColumnNames: store_id,value
        //## ColumnTypes: INTEGER,DECIMAL(10,2)
        PhysicalColumnMappingImpl store_id_store_x = PhysicalColumnMappingImpl.builder().withName("store_id").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl value_store_x = PhysicalColumnMappingImpl.builder().withName("store_id").withDataType(ColumnDataType.DECIMAL).withColumnSize(10).withDecimalDigits(2).build();
        PhysicalTableMappingImpl store_x = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("store_x")
                .withColumns(List.of(
                        store_id_store_x, value_store_x
                        ))).build();
        //## ColumnNames: prod_id,name
        //## ColumnTypes: INTEGER,VARCHAR(30)
        PhysicalColumnMappingImpl prod_id_product_x = PhysicalColumnMappingImpl.builder().withName("prod_id").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl name_product_x = PhysicalColumnMappingImpl.builder().withName("name").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(30).build();
        PhysicalTableMappingImpl product_x = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("product_x")
                .withColumns(List.of(
                        prod_id_product_x, name_product_x
                        ))).build();
        //## TableName: agg_lp_xxx_cheques
        //## ColumnNames: store_id,amount_AVG,FACT_COUNT
        //## ColumnTypes: INTEGER,DECIMAL(10,2),INTEGER
        PhysicalColumnMappingImpl storeId = PhysicalColumnMappingImpl.builder().withName("store_id").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl amountAvg = PhysicalColumnMappingImpl.builder().withName("amount_AVG").withDataType(ColumnDataType.DECIMAL).withCharOctetLength(10).withDecimalDigits(2).build();
        PhysicalColumnMappingImpl factCount = PhysicalColumnMappingImpl.builder().withName("FACT_COUNT").withDataType(ColumnDataType.INTEGER).build();
        PhysicalTableMappingImpl aggLpXxxCheques = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("agg_lp_xxx_cheques")
                .withColumns(List.of(
                		storeId, amountAvg, factCount
                        ))).build();

        List<CubeMapping> result = new ArrayList<>();
        result.addAll(super.catalogCubes(schema));
        result.add(PhysicalCubeMappingImpl.builder()
            .withName("Cheques")
            .withQuery(TableQueryMappingImpl.builder().withTable(cheques)
            		.withAggregationTables(List.of(
                            AggregationNameMappingImpl.builder()
                            .withName(aggLpXxxCheques)
                            .withAggregationFactCount(AggregationColumnNameMappingImpl.builder()
                                .withColumn(factCount)
                                .build())
                            .withAggregationForeignKeys(List.of(
                            	AggregationForeignKeyMappingImpl.builder()
                                    .withFactColumn(store_id_cheques)
                                    .withAggregationColumn(storeId)
                                    .build()
                            ))
                            .withAggregationMeasures(List.of(
                                AggregationMeasureMappingImpl.builder()
                                    .withName("[Measures].[Avg Amount]")
                                    .withColumn(amountAvg)
                                    .build()
                            ))
                            .build()
            		))
            		.build())
            .withDimensionConnectors(List.of(
            	DimensionConnectorMappingImpl.builder()
            		.withOverrideDimensionName("StoreX")
                    .withForeignKey(store_id_cheques)
                    .withDimension(StandardDimensionMappingImpl.builder()
                    	.withName("StoreX")
                    	.withHierarchies(List.of(
                        ExplicitHierarchyMappingImpl.builder()
                            .withHasAll(true)
                            .withPrimaryKey(store_id_store_x)
                            .withQuery(TableQueryMappingImpl.builder().withTable(store_x).build())
                            .withLevels(List.of(
                                LevelMappingImpl.builder()
                                    .withName("Store Value")
                                    .withColumn(value_store_x)
                                    .withUniqueMembers(true)
                                    .build()
                            ))
                            .build()
                    )).build())
                    .build(),
                DimensionConnectorMappingImpl.builder()
                    .withOverrideDimensionName("ProductX")
                    .withForeignKey(prod_id_cheques)
                    .withDimension(StandardDimensionMappingImpl.builder()
                        .withName("ProductX")
                        .withHierarchies(List.of(
                        ExplicitHierarchyMappingImpl.builder()
                            .withHasAll(true)
                            .withPrimaryKey(prod_id_product_x)
                            .withQuery(TableQueryMappingImpl.builder().withTable(product_x).build())
                            .withLevels(List.of(
                                LevelMappingImpl.builder()
                                    .withName("Store Name")
                                    .withColumn(name_product_x)
                                    .withUniqueMembers(true)
                                    .build()
                            ))
                            .build()
                    )).build())
                    .build()
            ))
            .withMeasureGroups(List.of(MeasureGroupMappingImpl.builder().withMeasures(List.of(
                CountMeasureMappingImpl.builder()
                    .withName("Sales Count")
                    .withColumn(prod_id_cheques)
                    .withFormatString("#,###")
                    .build(),
                CountMeasureMappingImpl.builder()
                    .withName("Store Count")
                    .withColumn(store_id_cheques)
                    .withDistinct(true)
                    .withFormatString("#,###")
                    .build(),
                SumMeasureMappingImpl.builder()
                    .withName("Total Amount")
                    .withColumn(amount_cheques)
                    .withFormatString("#,###")
                    .build(),
                AvgMeasureMappingImpl.builder()
                    .withName("Avg Amount")
                    .withColumn(amount_cheques)
                    .withFormatString("00.0")
                    .build()
            )).build()))
            .build());
        return result;

    }
}
*/
public class BUG_1541077Modifier implements CatalogMappingSupplier {

    private final Catalog originalCatalog;

    public BUG_1541077Modifier(Catalog catalog) {
        this.originalCatalog = catalog;
    }

    /*
        return "<Cube name='Cheques'>\n"
               + "<Table name='cheques'>\n"
               + "<AggName name='agg_lp_xxx_cheques'>\n"
               + "<AggFactCount column='FACT_COUNT'/>\n"
               + "<AggForeignKey factColumn='store_id' aggColumn='store_id' />\n"
               + "<AggMeasure name='[Measures].[Avg Amount]'\n"
               + "   column='amount_AVG' />\n"
               + "</AggName>\n"
                   + "</Table>\n"
                   + "<Dimension name='StoreX' foreignKey='store_id'>\n"
                   + " <Hierarchy hasAll='true' primaryKey='store_id'>\n"
                   + " <Table name='store_x'/>\n"
                   + " <Level name='Store Value' column='value' uniqueMembers='true'/>\n"
                   + " </Hierarchy>\n"
                   + "</Dimension>\n"
                   + "<Dimension name='ProductX' foreignKey='prod_id'>\n"
                   + " <Hierarchy hasAll='true' primaryKey='prod_id'>\n"
                   + " <Table name='product_x'/>\n"
                   + " <Level name='Store Name' column='name' uniqueMembers='true'/>\n"
                   + " </Hierarchy>\n"
                   + "</Dimension>\n"

                   + "<Measure name='Sales Count' \n"
                   + "    column='prod_id' aggregator='count'\n"
                   + "   formatString='#,###'/>\n"
                   + "<Measure name='Store Count' \n"
                   + "    column='store_id' aggregator='distinct-count'\n"
                   + "   formatString='#,###'/>\n"
                   + "<Measure name='Total Amount' \n"
                   + "    column='amount' aggregator='sum'\n"
                   + "   formatString='#,###'/>\n"
                   + "<Measure name='Avg Amount' \n"
                   + "    column='amount' aggregator='avg'\n"
                   + "   formatString='00.0'/>\n"
                   + "</Cube>";

     */

    @Override
    public Catalog get() {
        Catalog catalogCopy = EmfUtil.copy( (CatalogImpl) originalCatalog);

        // Create columns for cheques table using RolapMappingFactory
        // ColumnNames: prod_id,store_id,amount
        // ColumnTypes: INTEGER,INTEGER,DECIMAL(10,2)
        Column store_id_cheques = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        store_id_cheques.setName("store_id");
        store_id_cheques.setType(SQLSimpleTypes.Sql99.integerType());

        Column prod_id_cheques = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        prod_id_cheques.setName("prod_id");
        prod_id_cheques.setType(SQLSimpleTypes.Sql99.integerType());

        Column amount_cheques = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        amount_cheques.setName("amount");
        amount_cheques.setType(SQLSimpleTypes.decimalType(18, 4));
        // amount_cheques.setColumnSize(10);
        // amount_cheques.setDecimalDigits(2);

        // Create cheques table using RolapMappingFactory
        Table cheques = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        cheques.setName("cheques");
        cheques.getFeature().add(store_id_cheques);
        cheques.getFeature().add(prod_id_cheques);
        cheques.getFeature().add(amount_cheques);

        // Create columns for store_x table using RolapMappingFactory
        // ColumnNames: store_id,value
        // ColumnTypes: INTEGER,DECIMAL(10,2)
        Column store_id_store_x = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        store_id_store_x.setName("store_id");
        store_id_store_x.setType(SQLSimpleTypes.Sql99.integerType());

        Column value_store_x = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        value_store_x.setName("value");
        value_store_x.setType(SQLSimpleTypes.decimalType(18, 4));
        // value_store_x.setColumnSize(10);
        // value_store_x.setDecimalDigits(2);

        // Create store_x table using RolapMappingFactory
        Table store_x = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        store_x.setName("store_x");
        store_x.getFeature().add(store_id_store_x);
        store_x.getFeature().add(value_store_x);

        // Create columns for product_x table using RolapMappingFactory
        // ColumnNames: prod_id,name
        // ColumnTypes: INTEGER,VARCHAR(30)
        Column prod_id_product_x = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        prod_id_product_x.setName("prod_id");
        prod_id_product_x.setType(SQLSimpleTypes.Sql99.integerType());

        Column name_product_x = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        name_product_x.setName("name");
        name_product_x.setType(SQLSimpleTypes.varcharType(255));
        // name_product_x.setCharOctetLength(30);

        // Create product_x table using RolapMappingFactory
        Table product_x = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        product_x.setName("product_x");
        product_x.getFeature().add(prod_id_product_x);
        product_x.getFeature().add(name_product_x);

        // Create columns for agg_lp_xxx_cheques table using RolapMappingFactory
        // TableName: agg_lp_xxx_cheques
        // ColumnNames: store_id,amount_AVG,FACT_COUNT
        // ColumnTypes: INTEGER,DECIMAL(10,2),INTEGER
        Column storeId = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        storeId.setName("store_id");
        storeId.setType(SQLSimpleTypes.Sql99.integerType());

        Column amountAvg = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        amountAvg.setName("amount_AVG");
        amountAvg.setType(SQLSimpleTypes.decimalType(18, 4));
        // amountAvg.setColumnSize(10);
        // amountAvg.setDecimalDigits(2);

        Column factCount = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        factCount.setName("FACT_COUNT");
        factCount.setType(SQLSimpleTypes.Sql99.integerType());

        // Create agg_lp_xxx_cheques table using RolapMappingFactory
        Table aggLpXxxCheques = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        aggLpXxxCheques.setName("agg_lp_xxx_cheques");
        aggLpXxxCheques.getFeature().add(storeId);
        aggLpXxxCheques.getFeature().add(amountAvg);
        aggLpXxxCheques.getFeature().add(factCount);

        // Create aggregation column name for fact count using RolapMappingFactory
        AggregationColumnName aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
        aggFactCount.setColumn(factCount);

        // Create aggregation foreign key using RolapMappingFactory
        AggregationForeignKey aggForeignKey = AggregationFactory.eINSTANCE.createAggregationForeignKey();
        aggForeignKey.setFactColumn(store_id_cheques);
        aggForeignKey.setAggregationColumn(storeId);

        // Create aggregation measure using RolapMappingFactory
        AggregationMeasure aggMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
        aggMeasure.setName("[Measures].[Avg Amount]");
        aggMeasure.setColumn(amountAvg);

        // AggTableManager resolves both the fact table and candidate aggregate
        // tables by looking them up (by name) in the CWM Schema reachable from
        // the catalog mapping -- so all four physical tables must be owned
        // elements of that Schema, not just referenced from the cube/TableSource.
        if (!Packages.available(catalogCopy, Schema.class).isEmpty()) {
            Schema dbSchema = Packages.available(catalogCopy, Schema.class).get(0);
            dbSchema.getOwnedElement().add(cheques);
            dbSchema.getOwnedElement().add(store_x);
            dbSchema.getOwnedElement().add(product_x);
            dbSchema.getOwnedElement().add(aggLpXxxCheques);
        }

        // Create aggregation name using RolapMappingFactory
        ExplicitAggregationTable aggregationName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        aggregationName.setTable(aggLpXxxCheques);
        aggregationName.setAggregationFactCount(aggFactCount);
        aggregationName.getAggregationForeignKeys().add(aggForeignKey);
        aggregationName.getAggregationMeasures().add(aggMeasure);

        // Create table query with aggregation using RolapMappingFactory
        TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
        tableQuery.setTable(cheques);
        tableQuery.getAggregationTables().add(aggregationName);

        // Create StoreX hierarchy using RolapMappingFactory
        Level storeValueLevel = LevelFactory.eINSTANCE.createLevel();
        storeValueLevel.setName("Store Value");
        storeValueLevel.setColumn(value_store_x);
        storeValueLevel.setUniqueMembers(true);

        TableSource storeQuery = SourceFactory.eINSTANCE.createTableSource();
        storeQuery.setTable(store_x);

        ExplicitHierarchy storeHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        storeHierarchy.setHasAll(true);
        storeHierarchy.setPrimaryKey(store_id_store_x);
        storeHierarchy.setSource(storeQuery);
        storeHierarchy.getLevels().add(storeValueLevel);

        StandardDimension storeDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        storeDimension.setName("StoreX");
        storeDimension.getHierarchies().add(storeHierarchy);

        DimensionConnector storeConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        storeConnector.setOverrideDimensionName("StoreX");
        storeConnector.setForeignKey(store_id_cheques);
        storeConnector.setDimension(storeDimension);

        // Create ProductX hierarchy using RolapMappingFactory
        Level storeNameLevel = LevelFactory.eINSTANCE.createLevel();
        storeNameLevel.setName("Store Name");
        storeNameLevel.setColumn(name_product_x);
        storeNameLevel.setUniqueMembers(true);

        TableSource productQuery = SourceFactory.eINSTANCE.createTableSource();
        productQuery.setTable(product_x);

        ExplicitHierarchy productHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        productHierarchy.setHasAll(true);
        productHierarchy.setPrimaryKey(prod_id_product_x);
        productHierarchy.setSource(productQuery);
        productHierarchy.getLevels().add(storeNameLevel);

        StandardDimension productDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        productDimension.setName("ProductX");
        productDimension.getHierarchies().add(productHierarchy);

        DimensionConnector productConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        productConnector.setOverrideDimensionName("ProductX");
        productConnector.setForeignKey(prod_id_cheques);
        productConnector.setDimension(productDimension);

        // Create measures using RolapMappingFactory
        CountMeasure salesCountMeasure = MeasureFactory.eINSTANCE.createCountMeasure();
        salesCountMeasure.setName("Sales Count");
        salesCountMeasure.setColumn(prod_id_cheques);
        salesCountMeasure.setFormatString("#,###");

        CountMeasure storeCountMeasure = MeasureFactory.eINSTANCE.createCountMeasure();
        storeCountMeasure.setName("Store Count");
        storeCountMeasure.setColumn(store_id_cheques);
        storeCountMeasure.setDistinct(true);
        storeCountMeasure.setFormatString("#,###");

        SumMeasure totalAmountMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        totalAmountMeasure.setName("Total Amount");
        totalAmountMeasure.setColumn(amount_cheques);
        totalAmountMeasure.setFormatString("#,###");

        AvgMeasure avgAmountMeasure = MeasureFactory.eINSTANCE.createAvgMeasure();
        avgAmountMeasure.setName("Avg Amount");
        avgAmountMeasure.setColumn(amount_cheques);
        avgAmountMeasure.setFormatString("00.0");

        // Create measure group using RolapMappingFactory
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(salesCountMeasure);
        measureGroup.getMeasures().add(storeCountMeasure);
        measureGroup.getMeasures().add(totalAmountMeasure);
        measureGroup.getMeasures().add(avgAmountMeasure);

        // Create cube using RolapMappingFactory
        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("Cheques");
        cube.setSource(tableQuery);
        cube.getDimensionConnectors().add(storeConnector);
        cube.getDimensionConnectors().add(productConnector);
        cube.getMeasureGroups().add(measureGroup);

        // Add the cube to the catalog copy
        catalogCopy.getImportedElement().add(cube);

        return catalogCopy;
    }
}
