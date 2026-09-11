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
/*
"<Cube name='Checkin_7634'>\n"
+ "<Table name='table7634'/>\n"
+ "<Dimension name='Geography' foreignKey='cust_loc_id'>\n"
+ "    <Hierarchy hasAll='true' allMemberName='All Regions' primaryKey='cust_loc_id'>\n"
+ "    <Table name='geography7631'/>\n"
+ "    <Level column='state_cd' name='State' type='String' uniqueMembers='true'/>\n"
+ "    <Level column='city_nm' name='City' type='String' uniqueMembers='true'/>\n"
+ "    <Level column='zip_cd' name='Zip Code' type='String' uniqueMembers='true'/>\n"
+ "    </Hierarchy>\n"
+ "</Dimension>\n"
+ "<Dimension name='Product' foreignKey='prod_id'>\n"
+ "    <Hierarchy hasAll='true' allMemberName='All Products' primaryKey='prod_id'>\n"
+ "    <Table name='prod7631'/>\n"
+ "    <Level column='class' name='Class' type='String' uniqueMembers='true'/>\n"
+ "    <Level column='brand' name='Brand' type='String' uniqueMembers='true'/>\n"
+ "    <Level column='item' name='Item' type='String' uniqueMembers='true'/>\n"
+ "    </Hierarchy>\n"
+ "</Dimension>\n"
+ "<Measure name='First Measure' \n"
+ "    column='first' aggregator='sum'\n"
+ "   formatString='#,###'/>\n"
+ "<Measure name='Requested Value' \n"
+ "    column='request_value' aggregator='sum'\n"
+ "   formatString='#,###'/>\n"
+ "<Measure name='Shipped Value' \n"
+ "    column='shipped_value' aggregator='sum'\n"
+ "   formatString='#,###'/>\n"
+ "</Cube>";

*/
/*
public class Checkin_7634Modifier extends PojoMappingModifier {

    public Checkin_7634Modifier(CatalogMapping c) {
        super(c);
    }


    @Override
    protected List<? extends CubeMapping> catalogCubes(CatalogMapping schemaMappingOriginal) {
        List<CubeMapping> result = new ArrayList<>();
        PhysicalColumnMappingImpl cust_loc_id_geography7631 = PhysicalColumnMappingImpl.builder().withName("cust_loc_id").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl state_cd = PhysicalColumnMappingImpl.builder().withName("state_cd").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(20).build();
        PhysicalColumnMappingImpl city_nm = PhysicalColumnMappingImpl.builder().withName("city_nm").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(20).build();
        PhysicalColumnMappingImpl zip_cd = PhysicalColumnMappingImpl.builder().withName("zip_cd").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(20).build();
        PhysicalTableMappingImpl geography7631 = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("geography7631")
                .withColumns(List.of(
                        cust_loc_id_geography7631, state_cd, city_nm, zip_cd
                        ))).build();
        PhysicalColumnMappingImpl cust_loc_id_table7634 = PhysicalColumnMappingImpl.builder().withName("cust_loc_id").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl prod_id_table7634 = PhysicalColumnMappingImpl.builder().withName("prod_id").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl first_table7634 = PhysicalColumnMappingImpl.builder().withName("first").withDataType(ColumnDataType.DECIMAL).withColumnSize(10).withDecimalDigits(2).build();
        PhysicalColumnMappingImpl request_value_table7634 = PhysicalColumnMappingImpl.builder().withName("request_value").withDataType(ColumnDataType.DECIMAL).withColumnSize(10).withDecimalDigits(2).build();
        PhysicalColumnMappingImpl shipped_value_table7634 = PhysicalColumnMappingImpl.builder().withName("shipped_value").withDataType(ColumnDataType.DECIMAL).withColumnSize(10).withDecimalDigits(2).build();
        PhysicalTableMappingImpl table7634 = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("table7634")
                .withColumns(List.of(
                        cust_loc_id_table7634, prod_id_table7634, first_table7634, request_value_table7634, shipped_value_table7634
                        ))).build();
        PhysicalColumnMappingImpl prod_id_prod7631 = PhysicalColumnMappingImpl.builder().withName("prod_id").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl class_prod7631 = PhysicalColumnMappingImpl.builder().withName("class").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl brand_prod7631 = PhysicalColumnMappingImpl.builder().withName("brand").withDataType(ColumnDataType.INTEGER).build();
        PhysicalColumnMappingImpl item_prod7631 = PhysicalColumnMappingImpl.builder().withName("item").withDataType(ColumnDataType.INTEGER).build();
        PhysicalTableMappingImpl prod7631 = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("prod7631")
                .withColumns(List.of(
                		prod_id_prod7631, class_prod7631, brand_prod7631, item_prod7631
                        ))).build();

        result.addAll(super.catalogCubes(schemaMappingOriginal));
        result.add(PhysicalCubeMappingImpl.builder()
            .withName("Checkin_7634")
            .withQuery(TableQueryMappingImpl.builder().withTable(table7634).build())
            .withDimensionConnectors(List.of(
            	DimensionConnectorMappingImpl.builder()
            		.withOverrideDimensionName("Geography")
                    .withForeignKey(cust_loc_id_table7634)
                    .withDimension(StandardDimensionMappingImpl.builder()
                        .withName("Geography")
                        .withHierarchies(List.of(
                        ExplicitHierarchyMappingImpl.builder()
                            .withHasAll(true)
                            .withAllMemberName("All Regions")
                            .withPrimaryKey(cust_loc_id_geography7631)
                            .withQuery(TableQueryMappingImpl.builder().withTable(geography7631).build())
                            .withLevels(List.of(
                                LevelMappingImpl.builder()
                                    .withColumn(state_cd)
                                    .withName("State")
                                    .withType(InternalDataType.STRING)
                                    .withUniqueMembers(true)
                                    .build(),
                                LevelMappingImpl.builder()
                                    .withColumn(city_nm)
                                    .withName("City")
                                    .withType(InternalDataType.STRING)
                                    .withUniqueMembers(true)
                                    .build(),
                                LevelMappingImpl.builder()
                                    .withColumn(zip_cd)
                                    .withName("Zip Code")
                                    .withType(InternalDataType.STRING)
                                    .withUniqueMembers(true)
                                    .build()
                            ))
                            .build()

                    )).build())
                    .build(),
                DimensionConnectorMappingImpl.builder()
                	.withOverrideDimensionName("Product")
                    .withForeignKey(prod_id_table7634)
                    .withDimension(StandardDimensionMappingImpl.builder()
                        .withName("Product")
                        .withHierarchies(List.of(
                        ExplicitHierarchyMappingImpl.builder()
                            .withHasAll(true)
                            .withAllMemberName("All Products")
                            .withPrimaryKey(prod_id_prod7631)
                            .withQuery(TableQueryMappingImpl.builder().withTable(prod7631).build())
                            .withLevels(List.of(
                                LevelMappingImpl.builder()
                                    .withColumn(class_prod7631)
                                    .withName("Class")
                                    .withType(InternalDataType.STRING)
                                    .withUniqueMembers(true)
                                    .build(),
                                LevelMappingImpl.builder()
                                    .withColumn(brand_prod7631)
                                    .withName("Brand")
                                    .withType(InternalDataType.STRING)
                                    .withUniqueMembers(true)
                                    .build(),
                                LevelMappingImpl.builder()
                                    .withColumn(item_prod7631)
                                    .withName("Item")
                                    .withType(InternalDataType.STRING)
                                    .withUniqueMembers(true)
                                    .build()
                            ))
                            .build()

                    )).build())
                    .build()
            ))
            .withMeasureGroups(List.of(MeasureGroupMappingImpl.builder().withMeasures(List.of(
                SumMeasureMappingImpl.builder()
                    .withName("First Measure")
                    .withColumn(first_table7634)
                    .withFormatString("#,###")
                    .build(),
                SumMeasureMappingImpl.builder()
                    .withName("Requested Value")
                    .withColumn(request_value_table7634)
                    .withFormatString("#,###")
                    .build(),
                SumMeasureMappingImpl.builder()
                    .withName("Shipped Value")
                    .withColumn(shipped_value_table7634)
                    .withFormatString("#,###")
                    .build()
            )).build()))
            .build());


        return result;
    }
}
*/
public class Checkin_7634Modifier implements CatalogMappingSupplier {

    private final Catalog originalCatalog;

    public Checkin_7634Modifier(Catalog catalog) {
        this.originalCatalog = catalog;
    }

    /*
                "<Cube name='Checkin_7634'>\n"
            + "<Table name='table7634'/>\n"
            + "<Dimension name='Geography' foreignKey='cust_loc_id'>\n"
            + "    <Hierarchy hasAll='true' allMemberName='All Regions' primaryKey='cust_loc_id'>\n"
            + "    <Table name='geography7631'/>\n"
            + "    <Level column='state_cd' name='State' type='String' uniqueMembers='true'/>\n"
            + "    <Level column='city_nm' name='City' type='String' uniqueMembers='true'/>\n"
            + "    <Level column='zip_cd' name='Zip Code' type='String' uniqueMembers='true'/>\n"
            + "    </Hierarchy>\n"
            + "</Dimension>\n"
            + "<Dimension name='Product' foreignKey='prod_id'>\n"
            + "    <Hierarchy hasAll='true' allMemberName='All Products' primaryKey='prod_id'>\n"
            + "    <Table name='prod7631'/>\n"
            + "    <Level column='class' name='Class' type='String' uniqueMembers='true'/>\n"
            + "    <Level column='brand' name='Brand' type='String' uniqueMembers='true'/>\n"
            + "    <Level column='item' name='Item' type='String' uniqueMembers='true'/>\n"
            + "    </Hierarchy>\n"
            + "</Dimension>\n"
            + "<Measure name='First Measure' \n"
            + "    column='first' aggregator='sum'\n"
            + "   formatString='#,###'/>\n"
            + "<Measure name='Requested Value' \n"
            + "    column='request_value' aggregator='sum'\n"
            + "   formatString='#,###'/>\n"
            + "<Measure name='Shipped Value' \n"
            + "    column='shipped_value' aggregator='sum'\n"
            + "   formatString='#,###'/>\n"
            + "</Cube>";

     */

    @Override
    public Catalog get() {
        Catalog catalogCopy = EmfUtil.copy((CatalogImpl) originalCatalog);

        // Create columns for geography7631 table using RolapMappingFactory
        Column cust_loc_id_geography7631 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        cust_loc_id_geography7631.setName("cust_loc_id");
        cust_loc_id_geography7631.setType(SQLSimpleTypes.Sql99.integerType());

        Column state_cd = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        state_cd.setName("state_cd");
        state_cd.setType(SQLSimpleTypes.varcharType(255));
        // state_cd.setCharOctetLength(20);

        Column city_nm = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        city_nm.setName("city_nm");
        city_nm.setType(SQLSimpleTypes.varcharType(255));
        // city_nm.setCharOctetLength(20);

        Column zip_cd = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        zip_cd.setName("zip_cd");
        zip_cd.setType(SQLSimpleTypes.varcharType(255));
        // zip_cd.setCharOctetLength(20);

        // Create geography7631 table using RolapMappingFactory
        Table geography7631 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        geography7631.setName("geography7631");
        geography7631.getFeature().add(cust_loc_id_geography7631);
        geography7631.getFeature().add(state_cd);
        geography7631.getFeature().add(city_nm);
        geography7631.getFeature().add(zip_cd);

        // Create columns for table7634 using RolapMappingFactory
        Column cust_loc_id_table7634 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        cust_loc_id_table7634.setName("cust_loc_id");
        cust_loc_id_table7634.setType(SQLSimpleTypes.Sql99.integerType());

        Column prod_id_table7634 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        prod_id_table7634.setName("prod_id");
        prod_id_table7634.setType(SQLSimpleTypes.Sql99.integerType());

        Column first_table7634 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        first_table7634.setName("first");
        first_table7634.setType(SQLSimpleTypes.decimalType(18, 4));
        // first_table7634.setColumnSize(10);
        // first_table7634.setDecimalDigits(2);

        Column request_value_table7634 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        request_value_table7634.setName("request_value");
        request_value_table7634.setType(SQLSimpleTypes.decimalType(18, 4));
        // request_value_table7634.setColumnSize(10);
        // request_value_table7634.setDecimalDigits(2);

        Column shipped_value_table7634 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        shipped_value_table7634.setName("shipped_value");
        shipped_value_table7634.setType(SQLSimpleTypes.decimalType(18, 4));
        // shipped_value_table7634.setColumnSize(10);
        // shipped_value_table7634.setDecimalDigits(2);

        // Create table7634 using RolapMappingFactory
        Table table7634 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        table7634.setName("table7634");
        table7634.getFeature().add(cust_loc_id_table7634);
        table7634.getFeature().add(prod_id_table7634);
        table7634.getFeature().add(first_table7634);
        table7634.getFeature().add(request_value_table7634);
        table7634.getFeature().add(shipped_value_table7634);

        // Create columns for prod7631 table using RolapMappingFactory
        Column prod_id_prod7631 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        prod_id_prod7631.setName("prod_id");
        prod_id_prod7631.setType(SQLSimpleTypes.Sql99.integerType());

        Column class_prod7631 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        class_prod7631.setName("class");
        // Checkin_7634.csv upstream declares these VARCHAR(30) -- the values are Class0/Brand01/
        // Item011. Declared INTEGER here, mondrian quoted the member key by the column type
        // and emitted where "prod7631"."class" = Class1, unquoted, on every dialect.
        class_prod7631.setType(SQLSimpleTypes.varcharType(30));

        Column brand_prod7631 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        brand_prod7631.setName("brand");
        brand_prod7631.setType(SQLSimpleTypes.varcharType(30));

        Column item_prod7631 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        item_prod7631.setName("item");
        item_prod7631.setType(SQLSimpleTypes.varcharType(30));

        // Create prod7631 table using RolapMappingFactory
        Table prod7631 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        prod7631.setName("prod7631");
        prod7631.getFeature().add(prod_id_prod7631);
        prod7631.getFeature().add(class_prod7631);
        prod7631.getFeature().add(brand_prod7631);
        prod7631.getFeature().add(item_prod7631);

        // Create Geography hierarchy using RolapMappingFactory
        Level stateLevel = LevelFactory.eINSTANCE.createLevel();
        stateLevel.setColumn(state_cd);
        stateLevel.setName("State");
        stateLevel.setColumnType(ColumnInternalDataType.STRING);
        stateLevel.setUniqueMembers(true);

        Level cityLevel = LevelFactory.eINSTANCE.createLevel();
        cityLevel.setColumn(city_nm);
        cityLevel.setName("City");
        cityLevel.setColumnType(ColumnInternalDataType.STRING);
        cityLevel.setUniqueMembers(true);

        Level zipCodeLevel = LevelFactory.eINSTANCE.createLevel();
        zipCodeLevel.setColumn(zip_cd);
        zipCodeLevel.setName("Zip Code");
        zipCodeLevel.setColumnType(ColumnInternalDataType.STRING);
        zipCodeLevel.setUniqueMembers(true);

        TableSource geographyQuery = SourceFactory.eINSTANCE.createTableSource();
        geographyQuery.setTable(geography7631);

        ExplicitHierarchy geographyHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        geographyHierarchy.setHasAll(true);
        geographyHierarchy.setAllMemberName("All Regions");
        geographyHierarchy.setPrimaryKey(cust_loc_id_geography7631);
        geographyHierarchy.setSource(geographyQuery);
        geographyHierarchy.getLevels().add(stateLevel);
        geographyHierarchy.getLevels().add(cityLevel);
        geographyHierarchy.getLevels().add(zipCodeLevel);

        StandardDimension geographyDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        geographyDimension.setName("Geography");
        geographyDimension.getHierarchies().add(geographyHierarchy);

        DimensionConnector geographyConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        geographyConnector.setOverrideDimensionName("Geography");
        geographyConnector.setForeignKey(cust_loc_id_table7634);
        geographyConnector.setDimension(geographyDimension);

        // Create Product hierarchy using RolapMappingFactory
        Level classLevel = LevelFactory.eINSTANCE.createLevel();
        classLevel.setColumn(class_prod7631);
        classLevel.setName("Class");
        classLevel.setColumnType(ColumnInternalDataType.STRING);
        classLevel.setUniqueMembers(true);

        Level brandLevel = LevelFactory.eINSTANCE.createLevel();
        brandLevel.setColumn(brand_prod7631);
        brandLevel.setName("Brand");
        brandLevel.setColumnType(ColumnInternalDataType.STRING);
        brandLevel.setUniqueMembers(true);

        Level itemLevel = LevelFactory.eINSTANCE.createLevel();
        itemLevel.setColumn(item_prod7631);
        itemLevel.setName("Item");
        itemLevel.setColumnType(ColumnInternalDataType.STRING);
        itemLevel.setUniqueMembers(true);

        TableSource productQuery = SourceFactory.eINSTANCE.createTableSource();
        productQuery.setTable(prod7631);

        ExplicitHierarchy productHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        productHierarchy.setHasAll(true);
        productHierarchy.setAllMemberName("All Products");
        productHierarchy.setPrimaryKey(prod_id_prod7631);
        productHierarchy.setSource(productQuery);
        productHierarchy.getLevels().add(classLevel);
        productHierarchy.getLevels().add(brandLevel);
        productHierarchy.getLevels().add(itemLevel);

        StandardDimension productDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        productDimension.setName("Product");
        productDimension.getHierarchies().add(productHierarchy);

        DimensionConnector productConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        productConnector.setOverrideDimensionName("Product");
        productConnector.setForeignKey(prod_id_table7634);
        productConnector.setDimension(productDimension);

        // Create measures using RolapMappingFactory
        SumMeasure firstMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        firstMeasure.setName("First Measure");
        firstMeasure.setColumn(first_table7634);
        firstMeasure.setFormatString("#,###");

        SumMeasure requestedValueMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        requestedValueMeasure.setName("Requested Value");
        requestedValueMeasure.setColumn(request_value_table7634);
        requestedValueMeasure.setFormatString("#,###");

        SumMeasure shippedValueMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        shippedValueMeasure.setName("Shipped Value");
        shippedValueMeasure.setColumn(shipped_value_table7634);
        shippedValueMeasure.setFormatString("#,###");

        // Create measure group using RolapMappingFactory
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(firstMeasure);
        measureGroup.getMeasures().add(requestedValueMeasure);
        measureGroup.getMeasures().add(shippedValueMeasure);

        // Create cube query using RolapMappingFactory
        TableSource cubeQuery = SourceFactory.eINSTANCE.createTableSource();
        cubeQuery.setTable(table7634);

        // Create cube using RolapMappingFactory
        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("Checkin_7634");
        cube.setSource(cubeQuery);
        cube.getDimensionConnectors().add(geographyConnector);
        cube.getDimensionConnectors().add(productConnector);
        cube.getMeasureGroups().add(measureGroup);

        // Add the cube to the catalog copy
        catalogCopy.getImportedElement().add(cube);

        return catalogCopy;
    }
}

