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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType;
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
 * Fixture for {@link UntypedKeyColumnTest}: a two-level product hierarchy
 * whose upper level sits on a column the mapping declares without a SQL type.
 *
 * <p>The database column is a {@code VARCHAR} holding keys like {@code G100};
 * only the mapping leaves the type out. That is what an XMI of the pre-July
 * CWM model ends up with when a column is missing from its type's
 * {@code structuralFeature} list: the loader drops the column's {@code type}.
 */
public class UntypedKeyColumnInstance implements CatalogTestInstance {

    public static final String CUBE_NAME = "Sales";
    public static final String MEASURE_NAME = "Amount";
    public static final String DIMENSION = "Product";
    public static final String HIERARCHY = "Product";
    public static final String GROUP_LEVEL = "Group";
    public static final String PRODUCT_LEVEL = "Product";

    @Override
    public String name() {
        return "rolap.untypedKeyColumn";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        return () -> catalog(false);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> resources = new LinkedHashMap<>();
        resources.put("Fact", getClass().getResource("untypedkeycolumn/data/Fact.csv"));
        resources.put("Product", getClass().getResource("untypedkeycolumn/data/Product.csv"));
        return resources;
    }

    @Override
    public DatabaseSupplier databaseSupplier() {
        // The database itself knows the column as VARCHAR.
        return () -> schema(catalog(true));
    }

    private static Schema schema(Catalog catalog) {
        return org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages
                .available(catalog, Schema.class).get(0);
    }

    private static Catalog catalog(boolean typedGroupColumn) {
        Column factProduct = column("PRODUCT", SQLSimpleTypes.Sql99.varcharType());
        Column factValue = column("VALUE", SQLSimpleTypes.Sql99.integerType());
        Table fact = table("Fact", factProduct, factValue);

        Column product = column("PRODUCT_CODE", SQLSimpleTypes.Sql99.varcharType());
        Column group = column("PRODUCT_GROUP", typedGroupColumn ? SQLSimpleTypes.Sql99.varcharType() : null);
        Table productTable = table("Product", product, group);

        Schema databaseSchema = RelationalFactory.eINSTANCE.createSchema();
        databaseSchema.getOwnedElement().addAll(List.of(fact, productTable));

        TableSource factSource = SourceFactory.eINSTANCE.createTableSource();
        factSource.setTable(fact);
        TableSource productSource = SourceFactory.eINSTANCE.createTableSource();
        productSource.setTable(productTable);

        Level groupLevel = level(GROUP_LEVEL, group);
        Level productLevel = level(PRODUCT_LEVEL, product);

        ExplicitHierarchy hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        hierarchy.setName(HIERARCHY);
        hierarchy.setSource(productSource);
        hierarchy.setPrimaryKey(product);
        hierarchy.getLevels().addAll(List.of(groupLevel, productLevel));

        StandardDimension dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        dimension.setName(DIMENSION);
        dimension.getHierarchies().add(hierarchy);

        DimensionConnector connector = DimensionFactory.eINSTANCE.createDimensionConnector();
        connector.setDimension(dimension);
        connector.setOverrideDimensionName(DIMENSION);
        connector.setForeignKey(factProduct);

        SumMeasure measure = MeasureFactory.eINSTANCE.createSumMeasure();
        measure.setName(MEASURE_NAME);
        measure.setColumn(factValue);

        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(measure);

        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName(CUBE_NAME);
        cube.setSource(factSource);
        cube.getMeasureGroups().add(measureGroup);
        cube.getDimensionConnectors().add(connector);

        Catalog catalog = CatalogFactory.eINSTANCE.createCatalog();
        catalog.setName("Untyped Key Column");
        catalog.getImportedElement().add(databaseSchema);
        catalog.getOwnedElement().addAll(List.of(factSource, productSource, groupLevel, productLevel,
                hierarchy, dimension, cube));
        return Naming.complete(catalog);
    }

    private static Table table(String name, Column... columns) {
        Table table = RelationalFactory.eINSTANCE.createTable();
        table.setName(name);
        table.getFeature().addAll(List.of(columns));
        return table;
    }

    private static Column column(String name, SQLDataType type) {
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
}
