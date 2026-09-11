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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
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
 * EMF version of TestNotInMultiLevelMemberConstraintMixedNullNonNullParentModifier from FilterTest.
 * Creates a cube "Warehouse2" with custom Warehouse2 dimension having fax, address1, and name levels.
 *
 * This test has a special structure - it creates THREE dimension connectors for Warehouse2:
 * 1. First connector with Warehouse2 dimension (fax, address1, name)
 * 2. Second connector with identical Warehouse2 dimension
 * 3. Third connector using Product dimension
 *
 * <Dimension name="Warehouse2">
 *   <Hierarchy hasAll="true" primaryKey="warehouse_id">
 *     <Table name="warehouse"/>
 *     <Level name="fax" column="warehouse_fax" uniqueMembers="true"/>
 *     <Level name="address1" column="wa_address1" uniqueMembers="false"/>
 *     <Level name="name" column="warehouse_name" uniqueMembers="false"/>
 *   </Hierarchy>
 * </Dimension>
 *
 * <Cube name="Warehouse2">
 *   <Table name="inventory_fact_1997"/>
 *   <DimensionUsage name="Product" source="Product" foreignKey="product_id"/>
 *   <DimensionUsage name="Warehouse2" source="Warehouse2" foreignKey="warehouse_id"/> (x2)
 *   <Measure name="Warehouse Cost" column="warehouse_cost" aggregator="sum"/>
 *   <Measure name="Warehouse Sales" column="warehouse_sales" aggregator="sum"/>
 * </Cube>
 */
public class TestNotInMultiLevelMemberConstraintMixedNullNonNullParentModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    // Static columns not in CatalogSupplier
    private static final Column COLUMN_WAREHOUSE_FAX;
    private static final Column COLUMN_WA_ADDRESS1;

    // Static levels for Warehouse2 dimension (first instance)
    private static final Level LEVEL_FAX_1;
    private static final Level LEVEL_ADDRESS1_1;
    private static final Level LEVEL_NAME_1;

    // Static hierarchy for Warehouse2 (first instance)
    private static final ExplicitHierarchy HIERARCHY_WAREHOUSE2_1;
    private static final TableSource TABLE_QUERY_WAREHOUSE_1;

    // Static dimension Warehouse2 (first instance)
    private static final StandardDimension DIMENSION_WAREHOUSE2_1;

    // Static levels for Warehouse2 dimension (second instance - duplicate)
    private static final Level LEVEL_FAX_2;
    private static final Level LEVEL_ADDRESS1_2;
    private static final Level LEVEL_NAME_2;

    // Static hierarchy for Warehouse2 (second instance - duplicate)
    private static final ExplicitHierarchy HIERARCHY_WAREHOUSE2_2;
    private static final TableSource TABLE_QUERY_WAREHOUSE_2;

    // Static dimension Warehouse2 (second instance - duplicate)
    private static final StandardDimension DIMENSION_WAREHOUSE2_2;

    // Static dimension connectors
    private static final DimensionConnector CONNECTOR_PRODUCT;
    private static final DimensionConnector CONNECTOR_WAREHOUSE2_1;
    private static final DimensionConnector CONNECTOR_WAREHOUSE2_2;

    // Static table query for fact table
    private static final TableSource TABLE_QUERY_INVENTORY_FACT;

    // Static measures
    private static final SumMeasure MEASURE_WAREHOUSE_COST;
    private static final SumMeasure MEASURE_WAREHOUSE_SALES;

    // Static measure group
    private static final MeasureGroup MEASURE_GROUP;

    // Static cube
    private static final PhysicalCube CUBE_WAREHOUSE2;

    static {
        // Create columns not available in CatalogSupplier
        COLUMN_WAREHOUSE_FAX = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        COLUMN_WAREHOUSE_FAX.setName("warehouse_fax");
        COLUMN_WAREHOUSE_FAX.setType(SQLSimpleTypes.varcharType(255));

        COLUMN_WA_ADDRESS1 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        COLUMN_WA_ADDRESS1.setName("wa_address1");
        COLUMN_WA_ADDRESS1.setType(SQLSimpleTypes.varcharType(255));

        // Create first Warehouse2 dimension
        LEVEL_FAX_1 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_FAX_1.setName("fax");
        LEVEL_FAX_1.setColumn(COLUMN_WAREHOUSE_FAX);
        LEVEL_FAX_1.setUniqueMembers(true);

        LEVEL_ADDRESS1_1 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_ADDRESS1_1.setName("address1");
        LEVEL_ADDRESS1_1.setColumn(COLUMN_WA_ADDRESS1);
        LEVEL_ADDRESS1_1.setUniqueMembers(false);

        LEVEL_NAME_1 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_NAME_1.setName("name");
        LEVEL_NAME_1.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_NAME_WAREHOUSE);
        LEVEL_NAME_1.setUniqueMembers(false);

        TABLE_QUERY_WAREHOUSE_1 = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_WAREHOUSE_1.setTable(CatalogSupplier.TABLE_WAREHOUSE);

        HIERARCHY_WAREHOUSE2_1 = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_WAREHOUSE2_1.setHasAll(true);
        HIERARCHY_WAREHOUSE2_1.setPrimaryKey(CatalogSupplier.COLUMN_WAREHOUSE_ID_WAREHOUSE);
        HIERARCHY_WAREHOUSE2_1.setSource(TABLE_QUERY_WAREHOUSE_1);
        HIERARCHY_WAREHOUSE2_1.getLevels().addAll(List.of(
            LEVEL_FAX_1,
            LEVEL_ADDRESS1_1,
            LEVEL_NAME_1
        ));

        DIMENSION_WAREHOUSE2_1 = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_WAREHOUSE2_1.setName("Warehouse2");
        DIMENSION_WAREHOUSE2_1.getHierarchies().add(HIERARCHY_WAREHOUSE2_1);

        // Create second Warehouse2 dimension (duplicate of first)
        LEVEL_FAX_2 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_FAX_2.setName("fax");
        LEVEL_FAX_2.setColumn(COLUMN_WAREHOUSE_FAX);
        LEVEL_FAX_2.setUniqueMembers(true);

        LEVEL_ADDRESS1_2 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_ADDRESS1_2.setName("address1");
        LEVEL_ADDRESS1_2.setColumn(COLUMN_WA_ADDRESS1);
        LEVEL_ADDRESS1_2.setUniqueMembers(false);

        LEVEL_NAME_2 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_NAME_2.setName("name");
        LEVEL_NAME_2.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_NAME_WAREHOUSE);
        LEVEL_NAME_2.setUniqueMembers(false);

        TABLE_QUERY_WAREHOUSE_2 = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_WAREHOUSE_2.setTable(CatalogSupplier.TABLE_WAREHOUSE);

        HIERARCHY_WAREHOUSE2_2 = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_WAREHOUSE2_2.setHasAll(true);
        HIERARCHY_WAREHOUSE2_2.setPrimaryKey(CatalogSupplier.COLUMN_WAREHOUSE_ID_WAREHOUSE);
        HIERARCHY_WAREHOUSE2_2.setSource(TABLE_QUERY_WAREHOUSE_2);
        HIERARCHY_WAREHOUSE2_2.getLevels().addAll(List.of(
            LEVEL_FAX_2,
            LEVEL_ADDRESS1_2,
            LEVEL_NAME_2
        ));

        DIMENSION_WAREHOUSE2_2 = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_WAREHOUSE2_2.setName("Warehouse2");
        DIMENSION_WAREHOUSE2_2.getHierarchies().add(HIERARCHY_WAREHOUSE2_2);

        // Create dimension connector for Product
        CONNECTOR_PRODUCT = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_PRODUCT.setOverrideDimensionName("Product");
        CONNECTOR_PRODUCT.setDimension(CatalogSupplier.DIMENSION_PRODUCT);
        CONNECTOR_PRODUCT.setForeignKey(CatalogSupplier.COLUMN_PRODUCT_ID_INVENTORY_FACT);

        // Create first dimension connector for Warehouse2
        CONNECTOR_WAREHOUSE2_1 = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_WAREHOUSE2_1.setOverrideDimensionName("Warehouse2");
        CONNECTOR_WAREHOUSE2_1.setDimension(DIMENSION_WAREHOUSE2_1);
        CONNECTOR_WAREHOUSE2_1.setForeignKey(CatalogSupplier.COLUMN_WAREHOUSE_ID_INVENTORY_FACT);

        // Create second dimension connector for Warehouse2 (duplicate)
        CONNECTOR_WAREHOUSE2_2 = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_WAREHOUSE2_2.setOverrideDimensionName("Warehouse2");
        CONNECTOR_WAREHOUSE2_2.setDimension(DIMENSION_WAREHOUSE2_2);
        CONNECTOR_WAREHOUSE2_2.setForeignKey(CatalogSupplier.COLUMN_WAREHOUSE_ID_INVENTORY_FACT);

        // Create fact table query
        TABLE_QUERY_INVENTORY_FACT = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_INVENTORY_FACT.setTable(CatalogSupplier.TABLE_INVENTORY_FACT);

        // Create measures
        MEASURE_WAREHOUSE_COST = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_WAREHOUSE_COST.setName("Warehouse Cost");
        MEASURE_WAREHOUSE_COST.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_COST_INVENTORY_FACT);

        MEASURE_WAREHOUSE_SALES = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_WAREHOUSE_SALES.setName("Warehouse Sales");
        MEASURE_WAREHOUSE_SALES.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_SALES_INVENTORY_FACT);

        // Create measure group
        MEASURE_GROUP = CubeFactory.eINSTANCE.createMeasureGroup();
        MEASURE_GROUP.getMeasures().addAll(List.of(
            MEASURE_WAREHOUSE_COST,
            MEASURE_WAREHOUSE_SALES
        ));

        // Create cube with THREE dimension connectors (unusual structure for testing)
        CUBE_WAREHOUSE2 = CubeFactory.eINSTANCE.createPhysicalCube();
        CUBE_WAREHOUSE2.setName("Warehouse2");
        CUBE_WAREHOUSE2.setSource(TABLE_QUERY_INVENTORY_FACT);
        CUBE_WAREHOUSE2.getDimensionConnectors().addAll(List.of(
            CONNECTOR_PRODUCT,
            CONNECTOR_WAREHOUSE2_1,
            CONNECTOR_WAREHOUSE2_2  // Note: duplicate connector for testing purposes
        ));
        CUBE_WAREHOUSE2.getMeasureGroups().add(MEASURE_GROUP);
    }

    public TestNotInMultiLevelMemberConstraintMixedNullNonNullParentModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        this.catalog = EmfUtil.copy((CatalogImpl) baseCatalog);

        // Add the cube to the catalog
        this.catalog.getImportedElement().add(CUBE_WAREHOUSE2);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
