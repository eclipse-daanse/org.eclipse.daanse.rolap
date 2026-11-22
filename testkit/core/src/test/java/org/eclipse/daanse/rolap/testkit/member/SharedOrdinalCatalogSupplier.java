/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.member;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
import org.eclipse.daanse.rolap.mapping.model.database.relational.OrderedColumn;
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
 * Catalog with ONE shared dimension used by TWO cubes, where the dimension
 * level carries an ordinalColumn that orders members differently from their
 * key: DIM(KEY, ORD) with ORD reversing the alphabetical key order.
 *
 * <p>
 * Exercises the cube-local member ordinal: both cubes wrap the same shared
 * members, and each {@code RolapCubeMember} carries its own ordinal; the
 * shared member stays untouched.
 */
public class SharedOrdinalCatalogSupplier implements CatalogMappingSupplier {

    public static final String CATALOG_NAME = "SharedOrdinalCatalog";
    public static final String CUBE_A = "CubeA";
    public static final String CUBE_B = "CubeB";
    public static final String MEASURE_VAL = "Val";

    private final Schema databaseSchema;
    private final Catalog catalog;
    private final PhysicalCube cubeA;

    public SharedOrdinalCatalogSupplier() {
        this(null, null, false);
    }

    /**
     * @param cubeAMembersTag value for {@code daanse:cache.members} on CubeA, or null for no tag
     * @param cubeBMembersTag value for {@code daanse:cache.members} on CubeB, or null for no tag
     * @param soloDimOnCubeB  whether CubeB additionally connects its own SoloDim
     *                        (same DIM table, independent dimension object)
     */
    public SharedOrdinalCatalogSupplier(String cubeAMembersTag, String cubeBMembersTag, boolean soloDimOnCubeB) {
        this(cubeAMembersTag, cubeBMembersTag, soloDimOnCubeB, null);
    }

    /**
     * @param sharedHierarchyMembersTag value for {@code daanse:cache.members}
     *                                  on the shared KeyHierarchy itself, or
     *                                  null for no tag
     */
    public SharedOrdinalCatalogSupplier(String cubeAMembersTag, String cubeBMembersTag, boolean soloDimOnCubeB,
            String sharedHierarchyMembersTag) {
        this(cubeAMembersTag, cubeBMembersTag, soloDimOnCubeB, sharedHierarchyMembersTag, null);
    }

    /**
     * @param cubeACellsTag value for {@code daanse:cache.cells} on CubeA, or
     *                      null for no tag - "off" makes CubeA's segments
     *                      bypass the cell cache entirely
     */
    public SharedOrdinalCatalogSupplier(String cubeAMembersTag, String cubeBMembersTag, boolean soloDimOnCubeB,
            String sharedHierarchyMembersTag, String cubeACellsTag) {
        org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory rf =
                org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE;

        Column dimKeyColumn = rf.createColumn();
        dimKeyColumn.setName("KEY");
        dimKeyColumn.setType(SQLSimpleTypes.varcharType(20));

        Column dimOrdColumn = rf.createColumn();
        dimOrdColumn.setName("ORD");
        dimOrdColumn.setType(SQLSimpleTypes.Sql99.integerType());

        Table dimTable = rf.createTable();
        dimTable.setName("DIM");
        dimTable.getFeature().addAll(List.of(dimKeyColumn, dimOrdColumn));

        Column factAKeyColumn = rf.createColumn();
        factAKeyColumn.setName("KEY");
        factAKeyColumn.setType(SQLSimpleTypes.varcharType(20));

        Column factAValColumn = rf.createColumn();
        factAValColumn.setName("VAL");
        factAValColumn.setType(SQLSimpleTypes.Sql99.doublePrecisionType());

        Table factATable = rf.createTable();
        factATable.setName("FACT_A");
        factATable.getFeature().addAll(List.of(factAKeyColumn, factAValColumn));

        Column factBKeyColumn = rf.createColumn();
        factBKeyColumn.setName("KEY");
        factBKeyColumn.setType(SQLSimpleTypes.varcharType(20));

        Column factBValColumn = rf.createColumn();
        factBValColumn.setName("VAL");
        factBValColumn.setType(SQLSimpleTypes.Sql99.doublePrecisionType());

        Table factBTable = rf.createTable();
        factBTable.setName("FACT_B");
        factBTable.getFeature().addAll(List.of(factBKeyColumn, factBValColumn));

        databaseSchema = rf.createSchema();
        databaseSchema.getOwnedElement().addAll(List.of(dimTable, factATable, factBTable));

        TableSource dimSource = SourceFactory.eINSTANCE.createTableSource();
        dimSource.setTable(dimTable);

        TableSource factASource = SourceFactory.eINSTANCE.createTableSource();
        factASource.setTable(factATable);

        TableSource factBSource = SourceFactory.eINSTANCE.createTableSource();
        factBSource.setTable(factBTable);

        OrderedColumn ordinal =
                org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE
                        .createOrderedColumn();
        ordinal.setColumn(dimOrdColumn);

        Level level = LevelFactory.eINSTANCE.createLevel();
        level.setName("KeyLevel");
        level.setColumn(dimKeyColumn);
        level.getOrdinalColumns().add(ordinal);

        ExplicitHierarchy hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        hierarchy.setName("KeyHierarchy");
        hierarchy.setPrimaryKey(dimKeyColumn);
        hierarchy.setSource(dimSource);
        tagMembers(hierarchy, sharedHierarchyMembersTag);
        hierarchy.getLevels().add(level);

        StandardDimension dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        dimension.setName("SharedDim");
        dimension.getHierarchies().add(hierarchy);

        cubeA = cube(CUBE_A, factASource, factAValColumn, dimension, factAKeyColumn);
        PhysicalCube cubeB = cube(CUBE_B, factBSource, factBValColumn, dimension, factBKeyColumn);
        tagMembers(cubeA, cubeAMembersTag);
        tagMembers(cubeB, cubeBMembersTag);
        tag(cubeA, "daanse:cache.cells", cubeACellsTag);

        catalog = CatalogFactory.eINSTANCE.createCatalog();
        catalog.setName(CATALOG_NAME);
        catalog.getOwnedElement().add(databaseSchema);
        catalog.getOwnedElement().addAll(
                List.of(dimSource, factASource, factBSource, level, hierarchy, dimension, cubeA, cubeB));

        if (soloDimOnCubeB) {
            Level soloLevel = LevelFactory.eINSTANCE.createLevel();
            soloLevel.setName("SoloLevel");
            soloLevel.setColumn(dimKeyColumn);

            ExplicitHierarchy soloHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
            soloHierarchy.setName("SoloHierarchy");
            soloHierarchy.setPrimaryKey(dimKeyColumn);
            soloHierarchy.setSource(dimSource);
            soloHierarchy.getLevels().add(soloLevel);

            StandardDimension soloDimension = DimensionFactory.eINSTANCE.createStandardDimension();
            soloDimension.setName("SoloDim");
            soloDimension.getHierarchies().add(soloHierarchy);

            DimensionConnector soloConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
            soloConnector.setDimension(soloDimension);
            soloConnector.setForeignKey(factBKeyColumn);
            cubeB.getDimensionConnectors().add(soloConnector);

            catalog.getOwnedElement().addAll(List.of(soloLevel, soloHierarchy, soloDimension));
        }
    }

    /** Tags CubeA, such as {@code daanse:profile.mode=on}; before the first {@link #get()}. */
    public SharedOrdinalCatalogSupplier tagCubeA(String name, String value) {
        tag(cubeA, name, value);
        return this;
    }

    private static void tagMembers(
            org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement element, String value) {
        tag(element, "daanse:cache.members", value);
    }

    private static void tag(
            org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement element, String name, String value) {
        if (value == null) {
            return;
        }
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue tag =
                org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory.eINSTANCE.createTaggedValue();
        tag.setTag(name);
        tag.setValue(value);
        element.getTaggedValue().add(tag);
    }

    private static PhysicalCube cube(String name, TableSource source, Column valColumn,
            StandardDimension dimension, Column foreignKey) {
        SumMeasure val = MeasureFactory.eINSTANCE.createSumMeasure();
        val.setName(MEASURE_VAL);
        val.setColumn(valColumn);

        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(val);

        DimensionConnector connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        connector.setDimension(dimension);
        connector.setForeignKey(foreignKey);

        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName(name);
        cube.setSource(source);
        cube.getMeasureGroups().add(measureGroup);
        cube.getDimensionConnectors().add(connector);
        return cube;
    }

    /** The CWM database schema, for {@code DatabaseLayer.apply}. */
    public Schema schema() {
        return databaseSchema;
    }

    /**
     * The standard rows for this fixture: DIM A/B/C with descending ORD,
     * FACT_A and FACT_B with values 1..3. Owned here so every test on this
     * catalog loads identical data.
     */
    public static void insertRows(DataSource dataSource) throws SQLException {
        try (Connection jdbc = dataSource.getConnection()) {
            try (PreparedStatement ps = jdbc
                    .prepareStatement("insert into \"DIM\" (\"KEY\", \"ORD\") values (?, ?)")) {
                for (int i = 0; i < 3; i++) {
                    ps.setString(1, List.of("A", "B", "C").get(i));
                    ps.setInt(2, 3 - i);
                    ps.executeUpdate();
                }
            }
            for (String fact : List.of("FACT_A", "FACT_B")) {
                try (PreparedStatement ps = jdbc.prepareStatement(
                        "insert into \"" + fact + "\" (\"KEY\", \"VAL\") values (?, ?)")) {
                    for (int i = 0; i < 3; i++) {
                        ps.setString(1, List.of("A", "B", "C").get(i));
                        ps.setDouble(2, i + 1);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
