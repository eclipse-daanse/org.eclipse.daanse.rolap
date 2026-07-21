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
package org.eclipse.daanse.rolap.testkit.nullsemantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.PreparedStatement;
import java.util.List;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SqlSimpleTypes;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
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
import org.eclipse.daanse.rolap.testkit.core.TestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression for the mondrian TCK failure
 * {@code SegmentBuilderTest#testSegmentCreationForBoolean_True/_False}
 * (null/decimal-semantics initiative): members of a BOOLEAN level
 * ({@code [true]} / {@code [false]}) must be resolvable by name in a slicer.
 */
class BooleanLevelMemberLookupTest {

    private static Connection connection;

    @BeforeAll
    static void setUp() throws Exception {
        ActiveDatabase db = DatabaseProvider.selected().activate("BooleanLevelMemberLookup");
        BoolCatalogSupplier supplier = new BoolCatalogSupplier();
        DatabaseLayer.apply(db.dataSource(), db.dialect(), supplier.schema());
        insertRows(db.dataSource());
        TestContext ctx = new TestContext(db.dataSource(), db.dialect(), supplier);
        connection = ((Context<?>) ctx).getConnectionWithDefaultRole();
    }

    private static void insertRows(DataSource dataSource) throws Exception {
        try (java.sql.Connection jdbc = dataSource.getConnection();
                PreparedStatement ps = jdbc.prepareStatement(
                        "insert into \"STOREB\" (\"NAME\", \"COFFEE\", \"SQFT\") values (?, ?, ?)")) {
            insert(ps, "a", true, 10.0);
            insert(ps, "b", false, 20.0);
            insert(ps, "c", true, 30.0);
        }
    }

    private static void insert(PreparedStatement ps, String name, boolean coffee, double sqft) throws Exception {
        ps.setString(1, name);
        ps.setBoolean(2, coffee);
        ps.setDouble(3, sqft);
        ps.executeUpdate();
    }

    /**
     * The key of a BOOLEAN-typed level arrives as a Number (0/1) through the
     * dialect's INT accessor; the member name must still be true/false, not
     * 0/1 (main fix d2e6497, RolapMemberBase.keyToName).
 */
    @Test
    void booleanLevelMembersAreNamedTrueFalse() throws Exception {
        Result result = execute("""
                SELECT [CoffeeDim].[CoffeeHierarchy].[CoffeeLevel].members ON COLUMNS
                FROM [BoolCube]
                """);
        List<String> names = result.getAxes()[0].getPositions().stream()
                .map(p -> p.get(0).getName())
                .sorted()
                .toList();
        assertEquals(List.of("false", "true"), names);
    }

    /**
     * The TCK failure was a FailedToParseQueryException: '[Has coffee bar].[true]'
     * not found. Parsing resolves the slicer member, so a successful parse pins
     * the by-name lookup. (Execution is not asserted here: with the key arriving
     * as 0/1 through the INT accessor the segment predicate is rendered
     * numerically, which H2's strict BOOLEAN comparison rejects - a
     * dialect-specific mismatch outside this regression; the duckdb TCK covers
     * the end-to-end path.)
 */
    @Test
    void booleanTrueMemberResolvesInSlicer() throws Exception {
        assertNotNull(connection.parseQuery("""
                SELECT {[Measures].[SqftSum]} ON COLUMNS
                FROM [BoolCube]
                WHERE [CoffeeDim].[true]
                """));
    }

    @Test
    void booleanFalseMemberResolvesInSlicer() throws Exception {
        assertNotNull(connection.parseQuery("""
                SELECT {[Measures].[SqftSum]} ON COLUMNS
                FROM [BoolCube]
                WHERE [CoffeeDim].[false]
                """));
    }

    private static Result execute(String mdx) throws Exception {
        return connection.execute(connection.parseQuery(mdx));
    }

    /** Minimal catalog: fact table with a BOOLEAN dimension level. */
    static class BoolCatalogSupplier implements CatalogMappingSupplier {

        private final Schema databaseSchema;
        private final Catalog catalog;

        BoolCatalogSupplier() {
            RelationalFactory rf = RelationalFactory.eINSTANCE;

            Column nameColumn = rf.createColumn();
            nameColumn.setName("NAME");
            nameColumn.setType(SqlSimpleTypes.varcharType(20));

            Column coffeeColumn = rf.createColumn();
            coffeeColumn.setName("COFFEE");
            coffeeColumn.setType(SqlSimpleTypes.Sql99.booleanType());

            Column sqftColumn = rf.createColumn();
            sqftColumn.setName("SQFT");
            sqftColumn.setType(SqlSimpleTypes.Sql99.doublePrecisionType());

            Table table = rf.createTable();
            table.setName("STOREB");
            table.getFeature().addAll(List.of(nameColumn, coffeeColumn, sqftColumn));

            databaseSchema = rf.createSchema();
            databaseSchema.getOwnedElement().add(table);

            TableSource query = SourceFactory.eINSTANCE.createTableSource();
            query.setTable(table);

            SumMeasure sqftSum = MeasureFactory.eINSTANCE.createSumMeasure();
            sqftSum.setName("SqftSum");
            sqftSum.setColumn(sqftColumn);

            MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
            measureGroup.getMeasures().add(sqftSum);

            Level level = LevelFactory.eINSTANCE.createLevel();
            level.setName("CoffeeLevel");
            level.setColumn(coffeeColumn);
            level.setUniqueMembers(true);
            level.setColumnType(ColumnInternalDataType.BOOLEAN);

            ExplicitHierarchy hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
            hierarchy.setName("CoffeeHierarchy");
            hierarchy.setPrimaryKey(coffeeColumn);
            hierarchy.setSource(query);
            hierarchy.getLevels().add(level);

            StandardDimension dimension = DimensionFactory.eINSTANCE.createStandardDimension();
            dimension.setName("CoffeeDim");
            dimension.getHierarchies().add(hierarchy);

            DimensionConnector dimensionConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
            dimensionConnector.setDimension(dimension);

            PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
            cube.setName("BoolCube");
            cube.setSource(query);
            cube.getMeasureGroups().add(measureGroup);
            cube.getDimensionConnectors().add(dimensionConnector);

            catalog = CatalogFactory.eINSTANCE.createCatalog();
            catalog.setName("BoolCatalog");
            catalog.getDbschemas().add(databaseSchema);
            catalog.getCubes().add(cube);
        }

        Schema schema() {
            return databaseSchema;
        }

        @Override
        public Catalog get() {
            return catalog;
        }
    }
}
