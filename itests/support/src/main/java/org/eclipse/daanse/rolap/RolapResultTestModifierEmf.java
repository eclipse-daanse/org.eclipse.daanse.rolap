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
package org.eclipse.daanse.rolap;


import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
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
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
public class RolapResultTestModifierEmf implements CatalogMappingSupplier {

    protected final Catalog catalog;

    // FT1 table columns
    protected Column d1IdFt1;
    protected Column d2IdFt1;
    protected Column valueFt1;
    protected Table ft1;

    // FT2 table columns
    protected Column d1IdFt2;
    protected Column d2IdFt2;
    protected Column valueFt2;
    protected Column vextraFt2;
    protected Table ft2;

    // D1 table columns
    protected Column d1IdD1;
    protected Column nameD1;
    protected Table d1;

    // D2 table columns
    protected Column d2IdD2;
    protected Column nameD2;
    protected Table d2;

    public RolapResultTestModifierEmf(Catalog catalogMapping) {
        this.catalog = EmfUtil.copy((CatalogImpl) catalogMapping);
        createTables();
        createCubes();
    }

    /*
                "<Cube name='FTAll'>\n"
            + "<Table name='FT1' />\n"
            + "<Dimension name='D1' foreignKey='d1_id' >\n"
            + " <Hierarchy hasAll='true' primaryKey='d1_id'>\n"
            + " <Table name='D1'/>\n"
            + " <Level name='Name' column='name' type='String' uniqueMembers='true'/>\n"
            + " </Hierarchy>\n"
            + "</Dimension>\n"
            + "<Dimension name='D2' foreignKey='d2_id' >\n"
            + " <Hierarchy hasAll='true' primaryKey='d2_id'>\n"
            + " <Table name='D2'/>\n"
            + " <Level name='Name' column='name' type='String' uniqueMembers='true'/>\n"
            + " </Hierarchy>\n"
            + "</Dimension>\n"

            + "<Measure name='Value' \n"
            + "    column='value' aggregator='sum'\n"
            + "   formatString='#,###'/>\n"
            + "</Cube> \n"

            + "<Cube name='FT1'>\n"
            + "<Table name='FT1' />\n"
            + "<Dimension name='D1' foreignKey='d1_id' >\n"
            + " <Hierarchy hasAll='false' defaultMember='[D1].[d]' primaryKey='d1_id'>\n"
            + " <Table name='D1'/>\n"
            + " <Level name='Name' column='name' type='String' uniqueMembers='true'/>\n"
            + " </Hierarchy>\n"
            + "</Dimension>\n"
            + "<Dimension name='D2' foreignKey='d2_id' >\n"
            + " <Hierarchy hasAll='false' defaultMember='[D2].[w]' primaryKey='d2_id'>\n"
            + " <Table name='D2'/>\n"
            + " <Level name='Name' column='name' type='String' uniqueMembers='true'/>\n"
            + " </Hierarchy>\n"
            + "</Dimension>\n"

            + "<Measure name='Value' \n"
            + "    column='value' aggregator='sum'\n"
            + "   formatString='#,###'/>\n"
            + "</Cube> \n"

            + "<Cube name='FT2'>\n"
            + "<Table name='FT2'/>\n"
            + "<Dimension name='D1' foreignKey='d1_id' >\n"
            + " <Hierarchy hasAll='false' defaultMember='[D1].[d]' primaryKey='d1_id'>\n"
            + " <Table name='D1'/>\n"
            + " <Level name='Name' column='name' type='String' uniqueMembers='true'/>\n"
            + " </Hierarchy>\n"
            + "</Dimension>\n"
            + "<Dimension name='D2' foreignKey='d2_id' >\n"
            + " <Hierarchy hasAll='false' defaultMember='[D2].[w]' primaryKey='d2_id'>\n"
            + " <Table name='D2'/>\n"
            + " <Level name='Name' column='name' type='String' uniqueMembers='true'/>\n"
            + " </Hierarchy>\n"
            + "</Dimension>\n"

            + "<Measure name='Value' \n"
            + "    column='value' aggregator='sum'\n"
            + "   formatString='#,###'/>\n"
            + "</Cube>\n"

            + "<Cube name='FT2Extra'>\n"
            + "<Table name='FT2'/>\n"
            + "<Dimension name='D1' foreignKey='d1_id' >\n"
            + " <Hierarchy hasAll='false' defaultMember='[D1].[d]' primaryKey='d1_id'>\n"
            + " <Table name='D1'/>\n"
            + " <Level name='Name' column='name' type='String' uniqueMembers='true'/>\n"
            + " </Hierarchy>\n"
            + "</Dimension>\n"
            + "<Dimension name='D2' foreignKey='d2_id' >\n"
            + " <Hierarchy hasAll='false' defaultMember='[D2].[w]' primaryKey='d2_id'>\n"
            + " <Table name='D2'/>\n"
            + " <Level name='Name' column='name' type='String' uniqueMembers='true'/>\n"
            + " </Hierarchy>\n"
            + "</Dimension>\n"
            + "<Measure name='VExtra' \n"
            + "    column='vextra' aggregator='sum'\n"
            + "   formatString='#,###'/>\n"
            + "<Measure name='Value' \n"
            + "    column='value' aggregator='sum'\n"
            + "   formatString='#,###'/>\n"
            + "</Cube>";

     */

    protected void createTables() {
        // Create FT1 table
        // ColumnNames: d1_id,d2_id,value
        // ColumnTypes: INTEGER,INTEGER,DECIMAL(10,2)
        d1IdFt1 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        d1IdFt1.setName("d1_id");
        d1IdFt1.setType(SQLSimpleTypes.Sql99.integerType());

        d2IdFt1 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        d2IdFt1.setName("d2_id");
        d2IdFt1.setType(SQLSimpleTypes.Sql99.integerType());

        valueFt1 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        valueFt1.setName("value");
        valueFt1.setType(SQLSimpleTypes.decimalType(18, 4));
        // valueFt1.setColumnSize(10);
        // valueFt1.setDecimalDigits(2);

        ft1 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        ft1.setName("FT1");
        ft1.getFeature().add(d1IdFt1);
        ft1.getFeature().add(d2IdFt1);
        ft1.getFeature().add(valueFt1);

        // Create FT2 table
        // ColumnNames: d1_id,d2_id,value,vextra
        // ColumnTypes: INTEGER,INTEGER,DECIMAL(10,2),DECIMAL(10,2):null
        d1IdFt2 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        d1IdFt2.setName("d1_id");
        d1IdFt2.setType(SQLSimpleTypes.Sql99.integerType());

        d2IdFt2 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        d2IdFt2.setName("d2_id");
        d2IdFt2.setType(SQLSimpleTypes.Sql99.integerType());

        valueFt2 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        valueFt2.setName("value");
        valueFt2.setType(SQLSimpleTypes.decimalType(18, 4));
        // valueFt2.setColumnSize(10);
        // valueFt2.setDecimalDigits(2);

        vextraFt2 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        vextraFt2.setName("vextra");
        vextraFt2.setType(SQLSimpleTypes.decimalType(18, 4));
        // vextraFt2.setColumnSize(10);
        // vextraFt2.setDecimalDigits(2);

        ft2 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        ft2.setName("FT2");
        ft2.getFeature().add(d1IdFt2);
        ft2.getFeature().add(d2IdFt2);
        ft2.getFeature().add(valueFt2);
        ft2.getFeature().add(vextraFt2);

        // Create D1 table
        // ColumnNames: d1_id,name
        // ColumnTypes: INTEGER,VARCHAR(20)
        d1IdD1 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        d1IdD1.setName("d1_id");
        d1IdD1.setType(SQLSimpleTypes.Sql99.integerType());

        nameD1 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        nameD1.setName("name");
        nameD1.setType(SQLSimpleTypes.varcharType(255));
        // nameD1.setCharOctetLength(20);

        d1 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        d1.setName("D1");
        d1.getFeature().add(d1IdD1);
        d1.getFeature().add(nameD1);

        // Create D2 table
        // ColumnNames: d2_id,name
        // ColumnTypes: INTEGER,VARCHAR(20)
        d2IdD2 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        d2IdD2.setName("d2_id");
        d2IdD2.setType(SQLSimpleTypes.Sql99.integerType());

        nameD2 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        nameD2.setName("name");
        nameD2.setType(SQLSimpleTypes.varcharType(255));
        // nameD2.setCharOctetLength(20);

        d2 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        d2.setName("D2");
        d2.getFeature().add(d2IdD2);
        d2.getFeature().add(nameD2);

        // Add tables to database schema
        if (Packages.available(catalog, Schema.class).size() > 0) {
            Schema dbSchema = Packages.available(catalog, Schema.class).get(0);
            dbSchema.getOwnedElement().add(ft1);
            dbSchema.getOwnedElement().add(ft2);
            dbSchema.getOwnedElement().add(d1);
            dbSchema.getOwnedElement().add(d2);
        }
    }

    protected void createCubes() {
        // Create FTAll cube
        catalog.getImportedElement().add(createFTAllCube());

        // Create FT1 cube
        catalog.getImportedElement().add(createFT1Cube());

        // Create FT2 cube
        catalog.getImportedElement().add(createFT2Cube());

        // Create FT2Extra cube
        catalog.getImportedElement().add(createFT2ExtraCube());
    }

    protected PhysicalCube createFTAllCube() {
        // Create table query
        TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
        tableQuery.setTable(ft1);

        // Create D1 dimension with hasAll=true
        StandardDimension d1Dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        d1Dimension.setName("D1");

        TableSource d1Query = SourceFactory.eINSTANCE.createTableSource();
        d1Query.setTable(d1);

        Level d1NameLevel = LevelFactory.eINSTANCE.createLevel();
        d1NameLevel.setName("Name");
        d1NameLevel.setColumn(nameD1);
        d1NameLevel.setColumnType(ColumnInternalDataType.STRING);
        d1NameLevel.setUniqueMembers(true);

        ExplicitHierarchy d1Hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        d1Hierarchy.setHasAll(true);
        d1Hierarchy.setPrimaryKey(d1IdD1);
        d1Hierarchy.setSource(d1Query);
        d1Hierarchy.getLevels().add(d1NameLevel);

        d1Dimension.getHierarchies().add(d1Hierarchy);

        // Create D1 connector
        DimensionConnector d1Connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        d1Connector.setOverrideDimensionName("D1");
        d1Connector.setForeignKey(d1IdFt1);
        d1Connector.setDimension(d1Dimension);

        // Create D2 dimension with hasAll=true
        StandardDimension d2Dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        d2Dimension.setName("D2");

        TableSource d2Query = SourceFactory.eINSTANCE.createTableSource();
        d2Query.setTable(d2);

        Level d2NameLevel = LevelFactory.eINSTANCE.createLevel();
        d2NameLevel.setName("Name");
        d2NameLevel.setColumn(nameD2);
        d2NameLevel.setColumnType(ColumnInternalDataType.STRING);
        d2NameLevel.setUniqueMembers(true);

        ExplicitHierarchy d2Hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        d2Hierarchy.setHasAll(true);
        d2Hierarchy.setPrimaryKey(d2IdD2);
        d2Hierarchy.setSource(d2Query);
        d2Hierarchy.getLevels().add(d2NameLevel);

        d2Dimension.getHierarchies().add(d2Hierarchy);

        // Create D2 connector
        DimensionConnector d2Connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        d2Connector.setOverrideDimensionName("D2");
        d2Connector.setForeignKey(d2IdFt1);
        d2Connector.setDimension(d2Dimension);

        // Create measure
        SumMeasure valueMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        valueMeasure.setName("Value");
        valueMeasure.setColumn(valueFt1);
        valueMeasure.setFormatString("#,###");

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(valueMeasure);

        // Create cube
        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("FTAll");
        cube.setSource(tableQuery);
        cube.getDimensionConnectors().add(d1Connector);
        cube.getDimensionConnectors().add(d2Connector);
        cube.getMeasureGroups().add(measureGroup);

        return cube;
    }

    protected PhysicalCube createFT1Cube() {
        // Create table query
        TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
        tableQuery.setTable(ft1);

        // Create D1 dimension with hasAll=false and defaultMember
        StandardDimension d1Dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        d1Dimension.setName("D1");

        TableSource d1Query = SourceFactory.eINSTANCE.createTableSource();
        d1Query.setTable(d1);

        Level d1NameLevel = LevelFactory.eINSTANCE.createLevel();
        d1NameLevel.setName("Name");
        d1NameLevel.setColumn(nameD1);
        d1NameLevel.setColumnType(ColumnInternalDataType.STRING);
        d1NameLevel.setUniqueMembers(true);

        ExplicitHierarchy d1Hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        d1Hierarchy.setHasAll(false);
        d1Hierarchy.setDefaultMember("[D1].[d]");
        d1Hierarchy.setPrimaryKey(d1IdD1);
        d1Hierarchy.setSource(d1Query);
        d1Hierarchy.getLevels().add(d1NameLevel);

        d1Dimension.getHierarchies().add(d1Hierarchy);

        // Create D1 connector
        DimensionConnector d1Connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        d1Connector.setOverrideDimensionName("D1");
        d1Connector.setForeignKey(d1IdFt1);
        d1Connector.setDimension(d1Dimension);

        // Create D2 dimension with hasAll=false and defaultMember
        StandardDimension d2Dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        d2Dimension.setName("D2");

        TableSource d2Query = SourceFactory.eINSTANCE.createTableSource();
        d2Query.setTable(d2);

        Level d2NameLevel = LevelFactory.eINSTANCE.createLevel();
        d2NameLevel.setName("Name");
        d2NameLevel.setColumn(nameD2);
        d2NameLevel.setColumnType(ColumnInternalDataType.STRING);
        d2NameLevel.setUniqueMembers(true);

        ExplicitHierarchy d2Hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        d2Hierarchy.setHasAll(false);
        d2Hierarchy.setDefaultMember("[D2].[w]");
        d2Hierarchy.setPrimaryKey(d2IdD2);
        d2Hierarchy.setSource(d2Query);
        d2Hierarchy.getLevels().add(d2NameLevel);

        d2Dimension.getHierarchies().add(d2Hierarchy);

        // Create D2 connector
        DimensionConnector d2Connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        d2Connector.setOverrideDimensionName("D2");
        d2Connector.setForeignKey(d2IdFt1);
        d2Connector.setDimension(d2Dimension);

        // Create measure
        SumMeasure valueMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        valueMeasure.setName("Value");
        valueMeasure.setColumn(valueFt1);
        valueMeasure.setFormatString("#,###");

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(valueMeasure);

        // Create cube
        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("FT1");
        cube.setSource(tableQuery);
        cube.getDimensionConnectors().add(d1Connector);
        cube.getDimensionConnectors().add(d2Connector);
        cube.getMeasureGroups().add(measureGroup);

        return cube;
    }

    protected PhysicalCube createFT2Cube() {
        // Create table query
        TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
        tableQuery.setTable(ft2);

        // Create D1 dimension with hasAll=true (note: different from original XML comment which shows false)
        StandardDimension d1Dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        d1Dimension.setName("D1");

        TableSource d1Query = SourceFactory.eINSTANCE.createTableSource();
        d1Query.setTable(d1);

        Level d1NameLevel = LevelFactory.eINSTANCE.createLevel();
        d1NameLevel.setName("Name");
        d1NameLevel.setColumn(nameD1);
        d1NameLevel.setColumnType(ColumnInternalDataType.STRING);
        d1NameLevel.setUniqueMembers(true);

        ExplicitHierarchy d1Hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        d1Hierarchy.setHasAll(true);
        d1Hierarchy.setDefaultMember("[D1].[d]");
        d1Hierarchy.setPrimaryKey(d1IdD1);
        d1Hierarchy.setSource(d1Query);
        d1Hierarchy.getLevels().add(d1NameLevel);

        d1Dimension.getHierarchies().add(d1Hierarchy);

        // Create D1 connector
        DimensionConnector d1Connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        d1Connector.setOverrideDimensionName("D1");
        d1Connector.setForeignKey(d1IdFt2);
        d1Connector.setDimension(d1Dimension);

        // Create D2 dimension with hasAll=true (note: different from original XML comment which shows false)
        StandardDimension d2Dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        d2Dimension.setName("D2");

        TableSource d2Query = SourceFactory.eINSTANCE.createTableSource();
        d2Query.setTable(d2);

        Level d2NameLevel = LevelFactory.eINSTANCE.createLevel();
        d2NameLevel.setName("Name");
        d2NameLevel.setColumn(nameD2);
        d2NameLevel.setColumnType(ColumnInternalDataType.STRING);
        d2NameLevel.setUniqueMembers(true);

        ExplicitHierarchy d2Hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        d2Hierarchy.setHasAll(true);
        d2Hierarchy.setDefaultMember("[D2].[w]");
        d2Hierarchy.setPrimaryKey(d2IdD2);
        d2Hierarchy.setSource(d2Query);
        d2Hierarchy.getLevels().add(d2NameLevel);

        d2Dimension.getHierarchies().add(d2Hierarchy);

        // Create D2 connector
        DimensionConnector d2Connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        d2Connector.setOverrideDimensionName("D2");
        d2Connector.setForeignKey(d2IdFt2);
        d2Connector.setDimension(d2Dimension);

        // Create measure
        SumMeasure valueMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        valueMeasure.setName("Value");
        valueMeasure.setColumn(valueFt2);
        valueMeasure.setFormatString("#,###");

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(valueMeasure);

        // Create cube
        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("FT2");
        cube.setSource(tableQuery);
        cube.getDimensionConnectors().add(d1Connector);
        cube.getDimensionConnectors().add(d2Connector);
        cube.getMeasureGroups().add(measureGroup);

        return cube;
    }

    protected PhysicalCube createFT2ExtraCube() {
        // Create table query
        TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
        tableQuery.setTable(ft2);

        // Create D1 dimension with hasAll=true and defaultMember
        StandardDimension d1Dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        d1Dimension.setName("D1");

        TableSource d1Query = SourceFactory.eINSTANCE.createTableSource();
        d1Query.setTable(d1);

        Level d1NameLevel = LevelFactory.eINSTANCE.createLevel();
        d1NameLevel.setName("Name");
        d1NameLevel.setColumn(nameD1);
        d1NameLevel.setColumnType(ColumnInternalDataType.STRING);
        d1NameLevel.setUniqueMembers(true);

        ExplicitHierarchy d1Hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        d1Hierarchy.setHasAll(true);
        d1Hierarchy.setDefaultMember("[D1].[d]");
        d1Hierarchy.setPrimaryKey(d1IdD1);
        d1Hierarchy.setSource(d1Query);
        d1Hierarchy.getLevels().add(d1NameLevel);

        d1Dimension.getHierarchies().add(d1Hierarchy);

        // Create D1 connector
        DimensionConnector d1Connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        d1Connector.setOverrideDimensionName("D1");
        d1Connector.setForeignKey(d1IdFt2);
        d1Connector.setDimension(d1Dimension);

        // Create D2 dimension with hasAll=false and defaultMember
        StandardDimension d2Dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        d2Dimension.setName("D2");

        TableSource d2Query = SourceFactory.eINSTANCE.createTableSource();
        d2Query.setTable(d2);

        Level d2NameLevel = LevelFactory.eINSTANCE.createLevel();
        d2NameLevel.setName("Name");
        d2NameLevel.setColumn(nameD2);
        d2NameLevel.setColumnType(ColumnInternalDataType.STRING);
        d2NameLevel.setUniqueMembers(true);

        ExplicitHierarchy d2Hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        d2Hierarchy.setHasAll(false);
        d2Hierarchy.setDefaultMember("[D2].[w]");
        d2Hierarchy.setPrimaryKey(d2IdD2);
        d2Hierarchy.setSource(d2Query);
        d2Hierarchy.getLevels().add(d2NameLevel);

        d2Dimension.getHierarchies().add(d2Hierarchy);

        // Create D2 connector
        DimensionConnector d2Connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        d2Connector.setOverrideDimensionName("D2");
        d2Connector.setForeignKey(d2IdFt2);
        d2Connector.setDimension(d2Dimension);

        // Create measures
        SumMeasure vextraMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        vextraMeasure.setName("VExtra");
        vextraMeasure.setColumn(vextraFt2);
        vextraMeasure.setFormatString("#,###");

        SumMeasure valueMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        valueMeasure.setName("Value");
        valueMeasure.setColumn(valueFt2);
        valueMeasure.setFormatString("#,###");

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(vextraMeasure);
        measureGroup.getMeasures().add(valueMeasure);

        // Create cube
        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("FT2Extra");
        cube.setSource(tableQuery);
        cube.getDimensionConnectors().add(d1Connector);
        cube.getDimensionConnectors().add(d2Connector);
        cube.getMeasureGroups().add(measureGroup);

        return cube;
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
