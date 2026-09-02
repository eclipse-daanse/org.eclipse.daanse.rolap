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

import static org.eclipse.daanse.rolap.mapping.model.provider.util.Expressions.mdx;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessCatalogGrant;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessRole;
import org.eclipse.daanse.rolap.mapping.model.access.common.CatalogAccess;
import org.eclipse.daanse.rolap.mapping.model.access.common.CommonFactory;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessCubeGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessHierarchyGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessMemberGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.CubeAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.HierarchyAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.MemberAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.OlapFactory;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationColumnName;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationFactory;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationLevel;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationMeasure;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.ExplicitAggregationTable;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
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
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.RollupPolicy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
/*
public class SpeciesNonCollapsedAggTestModifier extends PojoMappingModifier {

	//## TableName: DIM_SPECIES
	//## ColumnNames: FAMILY_ID,GENUS_ID,SPECIES_ID,SPECIES_NAME
	//## ColumnTypes: INTEGER,INTEGER,INTEGER,VARCHAR(30)
	PhysicalColumnMappingImpl familyIdDimSpecies = PhysicalColumnMappingImpl.builder().withName("FAMILY_ID").withDataType(ColumnDataType.INTEGER).build();
	PhysicalColumnMappingImpl genisIdDimSpecies = PhysicalColumnMappingImpl.builder().withName("GENUS_ID").withDataType(ColumnDataType.INTEGER).build();
	PhysicalColumnMappingImpl speciesIdDimSpecies = PhysicalColumnMappingImpl.builder().withName("SPECIES_ID").withDataType(ColumnDataType.INTEGER).build();
	PhysicalColumnMappingImpl speciesNameDimSpecies = PhysicalColumnMappingImpl.builder().withName("SPECIES_NAME").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(30).build();
    PhysicalTableMappingImpl dimSpecies = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("DIM_SPECIES")
            .withColumns(List.of(familyIdDimSpecies, genisIdDimSpecies, speciesIdDimSpecies, speciesNameDimSpecies))).build();
    //## TableName: DIM_FAMILY
    //## ColumnNames: FAMILY_ID,FAMILY_NAME
    //## ColumnTypes: INTEGER,VARCHAR(30)
    PhysicalColumnMappingImpl familyIdDimFamily = PhysicalColumnMappingImpl.builder().withName("FAMILY_ID").withDataType(ColumnDataType.INTEGER).build();
    PhysicalColumnMappingImpl familyNameDimFamily = PhysicalColumnMappingImpl.builder().withName("FAMILY_NAME").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(30).build();
    PhysicalTableMappingImpl dimFamily = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("DIM_FAMILY")
            .withColumns(List.of(familyIdDimFamily, familyNameDimFamily))).build();
    //## TableName: DIM_GENUS
    //## ColumnNames: FAMILY_ID,GENUS_ID,GENUS_NAME
    //## ColumnTypes: INTEGER,INTEGER,VARCHAR(30)
    PhysicalColumnMappingImpl familyIdDimGenus = PhysicalColumnMappingImpl.builder().withName("FAMILY_ID").withDataType(ColumnDataType.INTEGER).build();
    PhysicalColumnMappingImpl genusIdDimGenus = PhysicalColumnMappingImpl.builder().withName("GENUS_ID").withDataType(ColumnDataType.INTEGER).build();
    PhysicalColumnMappingImpl genusNameDimGenus = PhysicalColumnMappingImpl.builder().withName("GENUS_NAME").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(30).build();
    PhysicalTableMappingImpl dimGenus = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("DIM_GENUS")
            .withColumns(List.of(familyIdDimGenus, genusIdDimGenus, genusNameDimGenus))).build();
    //## TableName: species_mart
    //## ColumnNames: SPECIES_ID,POPULATION
    //## ColumnTypes: INTEGER,INTEGER
    PhysicalColumnMappingImpl speciesIdSpeciesMart = PhysicalColumnMappingImpl.builder().withName("SPECIES_ID").withDataType(ColumnDataType.INTEGER).build();
    PhysicalColumnMappingImpl populationSpeciesMart = PhysicalColumnMappingImpl.builder().withName("POPULATION").withDataType(ColumnDataType.INTEGER).build();
    PhysicalTableMappingImpl speciesMart = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("species_mart")
            .withColumns(List.of(speciesIdSpeciesMart, populationSpeciesMart))).build();

    //## TableName: AGG_SPECIES_MART
    //## ColumnNames: GEN_ID,POPULATION,FACT_COUNT
    //## ColumnTypes: INTEGER,INTEGER,INTEGER
    PhysicalColumnMappingImpl genIdAggSpeciesMart = PhysicalColumnMappingImpl.builder().withName("GEN_ID").withDataType(ColumnDataType.INTEGER).build();
    PhysicalColumnMappingImpl populationAggSpeciesMart = PhysicalColumnMappingImpl.builder().withName("POPULATION").withDataType(ColumnDataType.INTEGER).build();
    PhysicalColumnMappingImpl factCountAggSpeciesMart = PhysicalColumnMappingImpl.builder().withName("FACT_COUNT").withDataType(ColumnDataType.INTEGER).build();
    PhysicalTableMappingImpl aggSpeciesMart = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("AGG_SPECIES_MART")
            .withColumns(List.of(genIdAggSpeciesMart, populationAggSpeciesMart, factCountAggSpeciesMart))).build();

    public SpeciesNonCollapsedAggTestModifier(CatalogMapping catalog) {
        super(catalog);
    }
*/
    /*
            "<?xml version='1.0'?>\n"
        + "<Schema name='Testmart'>\n"
        + "  <Dimension name='Animal'>\n"
        + "    <Hierarchy name='Animals' hasAll='true' allMemberName='All Animals' primaryKey='SPECIES_ID' primaryKeyTable='DIM_SPECIES'>\n"
        + "      <Join leftKey='GENUS_ID' rightAlias='DIM_GENUS' rightKey='GENUS_ID'>\n"
        + "        <Table name='DIM_SPECIES' />\n"
        + "        <Join leftKey='FAMILY_ID' rightKey='FAMILY_ID'>\n"
        + "          <Table name='DIM_GENUS' />\n"
        + "          <Table name='DIM_FAMILY' />\n"
        + "        </Join>\n"
        + "      </Join>\n"
        + "      <Level name='Family' table='DIM_FAMILY' column='FAMILY_ID' nameColumn='FAMILY_NAME' uniqueMembers='true' type='Numeric' approxRowCount='2' />\n"
        + "      <Level name='Genus' table='DIM_GENUS' column='GENUS_ID' nameColumn='GENUS_NAME' uniqueMembers='true' type='Numeric' approxRowCount='4' />\n"
        + "      <Level name='Species' table='DIM_SPECIES' column='SPECIES_ID' nameColumn='SPECIES_NAME' uniqueMembers='true' type='Numeric' approxRowCount='8' />\n"
        + "    </Hierarchy>\n"
        + "  </Dimension>\n"
        + "  <Cube name='Test' defaultMeasure='Population'>\n"
        + "    <Table name='species_mart'>\n" // See MONDRIAN-2237 - Table name needs to be lower case for embedded Windows MySQL integration testing
        + "      <AggName name='AGG_SPECIES_MART'>\n"
        + "        <AggFactCount column='FACT_COUNT' />\n"
        + "        <AggMeasure name='Measures.[Population]' column='POPULATION' />\n"
        + "        <AggLevel name='[Animal.Animals].[Genus]' column='GEN_ID' collapsed='false' />\n"
        + "      </AggName>\n"
        + "    </Table>\n"
        + "    <DimensionUsage name='Animal' source='Animal' foreignKey='SPECIES_ID'/>\n"
        + "    <Measure name='Population' column='POPULATION' aggregator='sum'/>\n"
        + "  </Cube>\n"
        + "  <Role name='Test role'>\n"
        + "    <SchemaGrant access='none'>\n"
        + "      <CubeGrant cube='Test' access='all'>\n"
        + "        <HierarchyGrant hierarchy='[Animal.Animals]' access='custom' rollupPolicy='partial'>\n"
        + "          <MemberGrant member='[Animal.Animals].[Family].[Loricariidae]' access='all'/>\n"
        + "          <MemberGrant member='[Animal.Animals].[Family].[Cichlidae]' access='all'/>\n"
        + "          <MemberGrant member='[Animal.Animals].[Family].[Cyprinidae]' access='none'/>\n"
        + "        </HierarchyGrant>\n"
        + "      </CubeGrant>\n"
        + "    </SchemaGrant>\n"
        + "  </Role>\n"
        + "</Schema>";
     */
/*
    @Override
    protected List<? extends TableMapping> databaseSchemaTables(DatabaseSchemaMapping databaseSchema) {
        List<TableMapping> result = new ArrayList<TableMapping>();
        result.addAll(super.databaseSchemaTables(databaseSchema));
        result.addAll(List.of(dimSpecies, dimFamily, dimGenus, speciesMart, aggSpeciesMart));
        return result;
    }

    @Override
    protected CatalogMapping modifyCatalog(CatalogMapping schemaMappingOriginal) {
    	HierarchyMappingImpl animalsHierarchy;
        StandardDimensionMappingImpl animal = StandardDimensionMappingImpl.builder()
        .withName("Animal")
        .withHierarchies(List.of(
        	animalsHierarchy = ExplicitHierarchyMappingImpl.builder()
                .withName("Animals")
                .withHasAll(true)
                .withAllMemberName("All Animals")
                .withPrimaryKey(speciesIdDimSpecies)
                .withQuery(JoinQueryMappingImpl.builder()
                		.withLeft(JoinedQueryElementMappingImpl.builder().withKey(genisIdDimSpecies)
                				.withQuery(TableQueryMappingImpl.builder().withTable(dimSpecies).build())
                				.build())
                		.withRight(JoinedQueryElementMappingImpl.builder().withAlias("DIM_GENUS").withKey(genusIdDimGenus)
                                .withQuery(JoinQueryMappingImpl.builder()
                                		.withLeft(JoinedQueryElementMappingImpl.builder().withKey(familyIdDimGenus)
                                				.withQuery(TableQueryMappingImpl.builder().withTable(dimGenus).build())
                                				.build())
                                		.withRight(JoinedQueryElementMappingImpl.builder().withKey(familyIdDimFamily)
                                				.withQuery(TableQueryMappingImpl.builder().withTable(dimFamily).build())
                                				.build())
                                		.build())

                				.build())
                		.build())

                .withLevels(List.of(
                    LevelMappingImpl.builder()
                        .withName("Family")
                        .withColumn(familyIdDimFamily)
                        .withNameColumn(familyNameDimFamily)
                        .withUniqueMembers(true)
                        .withType(InternalDataType.NUMERIC)
                        .withApproxRowCount("2")
                        .build(),
                    LevelMappingImpl.builder()
                        .withName("Genus")
                        .withColumn(genusIdDimGenus)
                        .withNameColumn(genusNameDimGenus)
                        .withUniqueMembers(true)
                        .withType(InternalDataType.NUMERIC)
                        .withApproxRowCount("4")
                        .build(),
                    LevelMappingImpl.builder()
                        .withName("Species")
                        .withColumn(speciesIdDimSpecies)
                        .withNameColumn(speciesNameDimSpecies)
                        .withUniqueMembers(true)
                        .withType(InternalDataType.NUMERIC)
                        .withApproxRowCount("8")
                        .build()
                ))
                .build()
        ))
        .build();

        SumMeasureMappingImpl populationMeasure = SumMeasureMappingImpl.builder()
        .withName("Population")
        .withColumn(populationSpeciesMart)
        .build();
        PhysicalCubeMappingImpl testCube;

        return CatalogMappingImpl.builder()
        .withName("Testmart")
        .withDbSchemas((List<DatabaseSchemaMappingImpl>) catalogDatabaseSchemas(schemaMappingOriginal))
        .withCubes(List.of(
        	testCube = PhysicalCubeMappingImpl.builder()
                .withName("Test")
                .withDefaultMeasure(populationMeasure)
                .withQuery(TableQueryMappingImpl.builder().withTable(speciesMart).withAggregationTables(
                    List.of(
                        AggregationNameMappingImpl.builder()
                            .withName(aggSpeciesMart)
                            .withAggregationFactCount(AggregationColumnNameMappingImpl.builder()
                                .withColumn(factCountAggSpeciesMart)
                                .build())
                            .withAggregationMeasures(List.of(
                            	AggregationMeasureMappingImpl.builder()
                                    .withName("Measures.[Population]")
                                    .withColumn(populationAggSpeciesMart)
                                    .build()
                            ))
                            .withAggregationLevels(List.of(
                                AggregationLevelMappingImpl.builder()
                                    .withName("[Animal].[Animals].[Genus]")
                                    .withColumn(genIdAggSpeciesMart)
                                    .withCollapsed(false)
                                    .build()
                            ))
                            .build()
                    )).build())
                .withDimensionConnectors(List.of(
                    DimensionConnectorMappingImpl.builder()
                        .withOverrideDimensionName("Animal")
                        .withDimension(animal)
                        .withForeignKey(speciesIdSpeciesMart)
                        .build()
                ))
                .withMeasureGroups(List.of(MeasureGroupMappingImpl.builder().withMeasures(List.of(populationMeasure)).build()))
                .build()
        ))
        .withAccessRoles(List.of(
            AccessRoleMappingImpl.builder()
                .withName("Test role")
                .withAccessCatalogGrants(List.of(
                	AccessCatalogGrantMappingImpl.builder()
                        .withAccess(AccessCatalog.NONE)
                        .withCubeGrant(List.of(
                        	AccessCubeGrantMappingImpl.builder()
                        		.withCube(testCube)
                                .withAccess(AccessCube.ALL)
                                .withHierarchyGrants(List.of(
                                	AccessHierarchyGrantMappingImpl.builder()
                                        .withHierarchy(animalsHierarchy)
                                        .withAccess(AccessHierarchy.CUSTOM)
                                        .withRollupPolicyType(RollupPolicyType.PARTIAL)
                                        .withMemberGrants(List.of(
                                        	AccessMemberGrantMappingImpl.builder()
                                                .withMember("[Animal].[Animals].[Family].[Loricariidae]")
                                                .withAccess(AccessMember.ALL)
                                                .build(),
                                            AccessMemberGrantMappingImpl.builder()
                                                .withMember("[Animal].[Animals].[Family].[Cichlidae]")
                                                .withAccess(AccessMember.ALL)
                                                .build(),
                                            AccessMemberGrantMappingImpl.builder()
                                                .withMember("[Animal].[Animals].[Family].[Cyprinidae]")
                                                .withAccess(AccessMember.NONE)
                                                .build()
                                        ))
                                        .build()
                                ))
                                .build()
                        ))
                        .build()
                ))
                .build()
        ))
        .build();
}
}
*/
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * EMF version of SpeciesNonCollapsedAggTestModifier
 * Creates Testmart catalog with Animal dimension, Test cube, aggregation tables and access roles
 */
public class SpeciesNonCollapsedAggTestModifier implements CatalogMappingSupplier {

    private Catalog catalog;

    public SpeciesNonCollapsedAggTestModifier(Catalog cat) {
        // Create new catalog from scratch (not copying the existing one)
        catalog = CatalogFactory.eINSTANCE.createCatalog();
        catalog.setName("Testmart");


        // Create database schema
        Schema dbSchema =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createSchema();

        // Create tables and columns - DIM_SPECIES
        Column familyIdDimSpecies =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        familyIdDimSpecies.setName("FAMILY_ID");
        familyIdDimSpecies.setType(SQLSimpleTypes.Sql99.integerType());

        Column genusIdDimSpecies =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        genusIdDimSpecies.setName("GENUS_ID");
        genusIdDimSpecies.setType(SQLSimpleTypes.Sql99.integerType());

        Column speciesIdDimSpecies =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        speciesIdDimSpecies.setName("SPECIES_ID");
        speciesIdDimSpecies.setType(SQLSimpleTypes.Sql99.integerType());

        Column speciesNameDimSpecies =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        speciesNameDimSpecies.setName("SPECIES_NAME");
        speciesNameDimSpecies.setType(SQLSimpleTypes.varcharType(255));
        // speciesNameDimSpecies.setCharOctetLength(30);

        Table dimSpecies =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        dimSpecies.setName("DIM_SPECIES");
        dimSpecies.getFeature().add(familyIdDimSpecies);
        dimSpecies.getFeature().add(genusIdDimSpecies);
        dimSpecies.getFeature().add(speciesIdDimSpecies);
        dimSpecies.getFeature().add(speciesNameDimSpecies);

        // DIM_FAMILY table
        Column familyIdDimFamily =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        familyIdDimFamily.setName("FAMILY_ID");
        familyIdDimFamily.setType(SQLSimpleTypes.Sql99.integerType());

        Column familyNameDimFamily =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        familyNameDimFamily.setName("FAMILY_NAME");
        familyNameDimFamily.setType(SQLSimpleTypes.varcharType(255));
        // familyNameDimFamily.setCharOctetLength(30);

        Table dimFamily =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        dimFamily.setName("DIM_FAMILY");
        dimFamily.getFeature().add(familyIdDimFamily);
        dimFamily.getFeature().add(familyNameDimFamily);

        // DIM_GENUS table
        Column familyIdDimGenus =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        familyIdDimGenus.setName("FAMILY_ID");
        familyIdDimGenus.setType(SQLSimpleTypes.Sql99.integerType());

        Column genusIdDimGenus =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        genusIdDimGenus.setName("GENUS_ID");
        genusIdDimGenus.setType(SQLSimpleTypes.Sql99.integerType());

        Column genusNameDimGenus =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        genusNameDimGenus.setName("GENUS_NAME");
        genusNameDimGenus.setType(SQLSimpleTypes.varcharType(255));
        // genusNameDimGenus.setCharOctetLength(30);

        Table dimGenus =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        dimGenus.setName("DIM_GENUS");
        dimGenus.getFeature().add(familyIdDimGenus);
        dimGenus.getFeature().add(genusIdDimGenus);
        dimGenus.getFeature().add(genusNameDimGenus);

        // species_mart fact table
        Column speciesIdSpeciesMart =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        speciesIdSpeciesMart.setName("SPECIES_ID");
        speciesIdSpeciesMart.setType(SQLSimpleTypes.Sql99.integerType());

        Column populationSpeciesMart =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        populationSpeciesMart.setName("POPULATION");
        populationSpeciesMart.setType(SQLSimpleTypes.Sql99.integerType());

        Table speciesMart =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        speciesMart.setName("species_mart");
        speciesMart.getFeature().add(speciesIdSpeciesMart);
        speciesMart.getFeature().add(populationSpeciesMart);

        // AGG_SPECIES_MART aggregation table
        Column genIdAggSpeciesMart =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        genIdAggSpeciesMart.setName("GEN_ID");
        genIdAggSpeciesMart.setType(SQLSimpleTypes.Sql99.integerType());

        Column populationAggSpeciesMart =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        populationAggSpeciesMart.setName("POPULATION");
        populationAggSpeciesMart.setType(SQLSimpleTypes.Sql99.integerType());

        Column factCountAggSpeciesMart =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        factCountAggSpeciesMart.setName("FACT_COUNT");
        factCountAggSpeciesMart.setType(SQLSimpleTypes.Sql99.integerType());

        Table aggSpeciesMart =
            org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        aggSpeciesMart.setName("AGG_SPECIES_MART");
        aggSpeciesMart.getFeature().add(genIdAggSpeciesMart);
        aggSpeciesMart.getFeature().add(populationAggSpeciesMart);
        aggSpeciesMart.getFeature().add(factCountAggSpeciesMart);

        // Add tables to database schema
        dbSchema.getOwnedElement().add(dimSpecies);
        dbSchema.getOwnedElement().add(dimFamily);
        dbSchema.getOwnedElement().add(dimGenus);
        dbSchema.getOwnedElement().add(speciesMart);
        dbSchema.getOwnedElement().add(aggSpeciesMart);

        // Create levels for Animal hierarchy
        Level familyLevel =
            LevelFactory.eINSTANCE.createLevel();
        familyLevel.setName("Family");
        familyLevel.setColumn(familyIdDimFamily);
        familyLevel.setNameColumn(familyNameDimFamily);
        familyLevel.setUniqueMembers(true);
        familyLevel.setColumnType(ColumnInternalDataType.NUMERIC);
        familyLevel.setApproxRowCount("2");

        Level genusLevel =
            LevelFactory.eINSTANCE.createLevel();
        genusLevel.setName("Genus");
        genusLevel.setColumn(genusIdDimGenus);
        genusLevel.setNameColumn(genusNameDimGenus);
        genusLevel.setUniqueMembers(true);
        genusLevel.setColumnType(ColumnInternalDataType.NUMERIC);
        genusLevel.setApproxRowCount("4");

        Level speciesLevel =
            LevelFactory.eINSTANCE.createLevel();
        speciesLevel.setName("Species");
        speciesLevel.setColumn(speciesIdDimSpecies);
        speciesLevel.setNameColumn(speciesNameDimSpecies);
        speciesLevel.setUniqueMembers(true);
        speciesLevel.setColumnType(ColumnInternalDataType.NUMERIC);
        speciesLevel.setApproxRowCount("8");

        // Create join query for hierarchy (DIM_SPECIES -> DIM_GENUS -> DIM_FAMILY)
        TableSource dimGenusQuery = SourceFactory.eINSTANCE.createTableSource();
        dimGenusQuery.setTable(dimGenus);

        JoinedQueryElement left = SourceFactory.eINSTANCE.createJoinedQueryElement();
        left.setKey(familyIdDimGenus);
        left.setSource(dimGenusQuery);

        TableSource dimFamilyQuery =  SourceFactory.eINSTANCE.createTableSource();
        dimFamilyQuery.setTable(dimFamily);

        JoinedQueryElement right = SourceFactory.eINSTANCE.createJoinedQueryElement();
        right.setKey(familyIdDimFamily);
        right.setSource(dimFamilyQuery);

        JoinSource innerJoin =
            SourceFactory.eINSTANCE.createJoinSource();
        innerJoin.setLeft(left);
        innerJoin.setRight(right);

        TableSource dimSpeciesQuery = SourceFactory.eINSTANCE.createTableSource();
        dimSpeciesQuery.setTable(dimSpecies);

        JoinedQueryElement left1 = SourceFactory.eINSTANCE.createJoinedQueryElement();
        left1.setKey(genusIdDimSpecies);
        left1.setSource(dimSpeciesQuery);

        JoinedQueryElement right1 = SourceFactory.eINSTANCE.createJoinedQueryElement();
        right1.setKey(genusIdDimGenus);
        right1.setAlias("DIM_GENUS");
        right1.setSource(innerJoin);

        JoinSource outerJoin =
            SourceFactory.eINSTANCE.createJoinSource();
        outerJoin.setLeft(left1);
        outerJoin.setRight(right1);

        // Create Animals hierarchy
        ExplicitHierarchy animalsHierarchy =
            HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        animalsHierarchy.setName("Animals");
        animalsHierarchy.setHasAll(true);
        animalsHierarchy.setAllMemberName("All Animals");
        animalsHierarchy.setPrimaryKey(speciesIdDimSpecies);
        animalsHierarchy.setSource(outerJoin);
        animalsHierarchy.getLevels().add(familyLevel);
        animalsHierarchy.getLevels().add(genusLevel);
        animalsHierarchy.getLevels().add(speciesLevel);

        // Create Animal dimension
        StandardDimension animalDimension =
            DimensionFactory.eINSTANCE.createStandardDimension();
        animalDimension.setName("Animal");
        animalDimension.getHierarchies().add(animalsHierarchy);

        // Create Population measure
        SumMeasure populationMeasure =
            MeasureFactory.eINSTANCE.createSumMeasure();
        populationMeasure.setName("Population");
        populationMeasure.setColumn(populationSpeciesMart);


        // Create aggregation
        AggregationColumnName aggFactCount =
            AggregationFactory.eINSTANCE.createAggregationColumnName();
        aggFactCount.setColumn(factCountAggSpeciesMart);

        AggregationMeasure aggMeasure =
            AggregationFactory.eINSTANCE.createAggregationMeasure();
        aggMeasure.setName("Measures.[Population]");
        aggMeasure.setColumn(populationAggSpeciesMart);

        AggregationLevel aggLevel =
            AggregationFactory.eINSTANCE.createAggregationLevel();
        aggLevel.setName("[Animal].[Animals].[Genus]");
        aggLevel.setColumn(genIdAggSpeciesMart);
        aggLevel.setCollapsed(false);

        ExplicitAggregationTable aggregation =
            AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        aggregation.setTable(aggSpeciesMart);
        aggregation.setAggregationFactCount(aggFactCount);
        aggregation.getAggregationMeasures().add(aggMeasure);
        aggregation.getAggregationLevels().add(aggLevel);

        // Create table query with aggregation for cube
        TableSource cubeQuery =
            SourceFactory.eINSTANCE.createTableSource();
        cubeQuery.setTable(speciesMart);
        cubeQuery.getAggregationTables().add(aggregation);

        // Create dimension connector
        DimensionConnector dimConnector =
            DimensionFactory.eINSTANCE.createDimensionConnector();
        dimConnector.setOverrideDimensionName("Animal");
        dimConnector.setDimension(animalDimension);
        dimConnector.setForeignKey(speciesIdSpeciesMart);

        // Create measure group
        MeasureGroup measureGroup =
            CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(populationMeasure);

        // Create Test cube
        PhysicalCube testCube =
            CubeFactory.eINSTANCE.createPhysicalCube();
        testCube.setName("Test");
        testCube.setDefaultMeasure(populationMeasure);
        testCube.setSource(cubeQuery);
        testCube.getDimensionConnectors().add(dimConnector);
        testCube.getMeasureGroups().add(measureGroup);

        // Create access member grants
        AccessMemberGrant memberGrant1 =
            OlapFactory.eINSTANCE.createAccessMemberGrant();
        memberGrant1.setMember(mdx("[Animal].[Animals].[Family].[Loricariidae]"));
        memberGrant1.setMemberAccess(MemberAccess.ALL);

        AccessMemberGrant memberGrant2 =
            OlapFactory.eINSTANCE.createAccessMemberGrant();
        memberGrant2.setMember(mdx("[Animal].[Animals].[Family].[Cichlidae]"));
        memberGrant2.setMemberAccess(MemberAccess.ALL);

        AccessMemberGrant memberGrant3 =
            OlapFactory.eINSTANCE.createAccessMemberGrant();
        memberGrant3.setMember(mdx("[Animal].[Animals].[Family].[Cyprinidae]"));
        memberGrant3.setMemberAccess(MemberAccess.NONE);

        // Create hierarchy grant
        AccessHierarchyGrant hierarchyGrant =
            OlapFactory.eINSTANCE.createAccessHierarchyGrant();
        hierarchyGrant.setHierarchy(animalsHierarchy);
        hierarchyGrant.setHierarchyAccess(HierarchyAccess.CUSTOM);
        hierarchyGrant.setRollupPolicy(RollupPolicy.PARTIAL);
        hierarchyGrant.getMemberGrants().add(memberGrant1);
        hierarchyGrant.getMemberGrants().add(memberGrant2);
        hierarchyGrant.getMemberGrants().add(memberGrant3);

        // Create cube grant
        AccessCubeGrant cubeGrant =
            OlapFactory.eINSTANCE.createAccessCubeGrant();
        cubeGrant.setCube(testCube);
        cubeGrant.setCubeAccess(CubeAccess.ALL);
        cubeGrant.getHierarchyGrants().add(hierarchyGrant);

        // Create catalog grant
        AccessCatalogGrant catalogGrant =
            CommonFactory.eINSTANCE.createAccessCatalogGrant();
        catalogGrant.setCatalogAccess(CatalogAccess.NONE);
        catalogGrant.getCubeGrants().add(cubeGrant);

        // Create access role
        AccessRole accessRole =
            CommonFactory.eINSTANCE.createAccessRole();
        accessRole.setName("Test role");
        accessRole.getAccessCatalogGrants().add(catalogGrant);

        // Add everything to schema and catalog

        catalog.getImportedElement().add(dbSchema);
        catalog.getImportedElement().add(testCube);
        catalog.getImportedElement().add(accessRole);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

