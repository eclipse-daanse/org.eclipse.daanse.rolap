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
package org.eclipse.daanse.rolap;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.testkit.api.DatabaseSupplier;
import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
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
import org.eclipse.daanse.rolap.mapping.model.provider.util.Naming;

/**
 * Fixture for {@link OverrideDimensionNameOptionalTest}: a degenerate cube whose
 * {@link DimensionConnector}s leave the optional {@code overrideDimensionName}
 * unset, in the two shapes that leave {@code HierarchyUsage.fullName} null.
 *
 * <ul>
 * <li>{@code Store} — the hierarchy carries the dimension's own name.</li>
 * <li>{@code Product} — the hierarchy is unnamed and defaults to it.</li>
 * </ul>
 */
public class OverrideDimensionNameOptionalInstance implements CatalogTestInstance {

    public static final String CUBE_NAME = "Degenerate";
    public static final String MEASURE_NAME = "Amount";
    public static final String STORE = "Store";
    public static final String PRODUCT = "Product";

    @Override
    public String name() {
        return "rolap.overrideDimensionNameOptional";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        return this::catalog;
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> resources = new LinkedHashMap<>();
        resources.put("Fact", getClass().getResource("overridedimensionname/data/Fact.csv"));
        return resources;
    }

    @Override
    public DatabaseSupplier databaseSupplier() {
        return () -> schema(catalog());
    }

    private static Schema schema(Catalog catalog) {
        return org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages
                .available(catalog, Schema.class).get(0);
    }

    private Catalog catalog() {
        Column storeColumn = column("STORE", SQLSimpleTypes.Sql99.varcharType());
        Column productColumn = column("PRODUCT", SQLSimpleTypes.Sql99.varcharType());
        Column valueColumn = column("VALUE", SQLSimpleTypes.Sql99.integerType());

        Table table = RelationalFactory.eINSTANCE.createTable();
        table.setName("Fact");
        table.getFeature().addAll(List.of(storeColumn, productColumn, valueColumn));

        Schema databaseSchema = RelationalFactory.eINSTANCE.createSchema();
        databaseSchema.getOwnedElement().add(table);

        TableSource source = SourceFactory.eINSTANCE.createTableSource();
        source.setTable(table);

        // Hierarchy named exactly like its dimension.
        Level storeLevel = level(STORE, storeColumn);
        ExplicitHierarchy storeHierarchy = hierarchy(STORE, source, storeLevel);
        StandardDimension storeDimension = dimension(STORE, storeHierarchy);

        // Unnamed hierarchy: defaults to the name of the using dimension.
        Level productLevel = level(PRODUCT, productColumn);
        ExplicitHierarchy productHierarchy = hierarchy(null, source, productLevel);
        StandardDimension productDimension = dimension(PRODUCT, productHierarchy);

        SumMeasure measure = MeasureFactory.eINSTANCE.createSumMeasure();
        measure.setName(MEASURE_NAME);
        measure.setColumn(valueColumn);

        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(measure);

        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName(CUBE_NAME);
        cube.setSource(source);
        cube.getMeasureGroups().add(measureGroup);
        // The point of the fixture: no setOverrideDimensionName, no foreignKey
        // either - hierarchies and cube share the fact table.
        cube.getDimensionConnectors().addAll(List.of(connector(storeDimension), connector(productDimension)));

        Catalog catalog = CatalogFactory.eINSTANCE.createCatalog();
        catalog.setName("Override Dimension Name Optional");
        catalog.getImportedElement().add(databaseSchema);
        catalog.getOwnedElement().addAll(List.of(source, storeLevel, storeHierarchy, storeDimension,
                productLevel, productHierarchy, productDimension, cube));

        // Fills the connectors' CWM name from the dimension - and still leaves
        // overrideDimensionName unset, exactly as the shipped catalogs do.
        return Naming.complete(catalog);
    }

    private static Column column(String name, org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType type) {
        Column column = RelationalFactory.eINSTANCE.createColumn();
        column.setName(name);
        column.setType(type);
        return column;
    }

    private static Level level(String name, Column column) {
        Level level = LevelFactory.eINSTANCE.createLevel();
        level.setName(name);
        level.setColumn(column);
        return level;
    }

    private static ExplicitHierarchy hierarchy(String name, TableSource source, Level level) {
        ExplicitHierarchy hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        hierarchy.setName(name);
        hierarchy.setSource(source);
        hierarchy.getLevels().add(level);
        return hierarchy;
    }

    private static StandardDimension dimension(String name, ExplicitHierarchy hierarchy) {
        StandardDimension dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        dimension.setName(name);
        dimension.getHierarchies().add(hierarchy);
        return dimension;
    }

    private static DimensionConnector connector(StandardDimension dimension) {
        DimensionConnector connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        connector.setDimension(dimension);
        return connector;
    }
}
