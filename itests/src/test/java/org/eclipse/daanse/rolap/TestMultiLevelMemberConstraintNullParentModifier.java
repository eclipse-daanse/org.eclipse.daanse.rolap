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
 * EMF version of TestMultiLevelMemberConstraintNullParentModifier from NonEmptyTest.
 * Creates a cube "Warehouse2" with custom Warehouse dimension having address levels.
 *
 * <Dimension name="Warehouse2">
 *   <Hierarchy hasAll="true" primaryKey="warehouse_id">
 *     <Table name="warehouse"/>
 *     <Level name="address3" column="wa_address3" uniqueMembers="true"/>
 *     <Level name="address2" column="wa_address2" uniqueMembers="true"/>
 *     <Level name="address1" column="wa_address1" uniqueMembers="false"/>
 *     <Level name="name" column="warehouse_name" uniqueMembers="false"/>
 *   </Hierarchy>
 * </Dimension>
 *
 * <Cube name="Warehouse2">
 *   <Table name="inventory_fact_1997"/>
 *   <DimensionUsage name="Product" source="Product" foreignKey="product_id"/>
 *   <DimensionUsage name="Warehouse2" source="Warehouse2" foreignKey="warehouse_id"/>
 *   <Measure name="Warehouse Cost" column="warehouse_cost" aggregator="sum"/>
 *   <Measure name="Warehouse Sales" column="warehouse_sales" aggregator="sum"/>
 * </Cube>
 */
public class TestMultiLevelMemberConstraintNullParentModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    // Static columns for wa_address (not in CatalogSupplier, need to create)
    private static final Column COLUMN_WA_ADDRESS1;
    private static final Column COLUMN_WA_ADDRESS2;
    private static final Column COLUMN_WA_ADDRESS3;

    // Static levels for Warehouse2 dimension
    private static final Level LEVEL_ADDRESS3;
    private static final Level LEVEL_ADDRESS2;
    private static final Level LEVEL_ADDRESS1;
    private static final Level LEVEL_NAME;

    // Static hierarchy for Warehouse2
    private static final ExplicitHierarchy HIERARCHY_WAREHOUSE2;
    private static final TableSource TABLE_QUERY_WAREHOUSE;

    // Static dimension Warehouse2
    private static final StandardDimension DIMENSION_WAREHOUSE2;

    // Static dimension connectors
    private static final DimensionConnector CONNECTOR_PRODUCT;
    private static final DimensionConnector CONNECTOR_WAREHOUSE2;

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
        // Create wa_address columns (not available in CatalogSupplier)
        COLUMN_WA_ADDRESS1 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        COLUMN_WA_ADDRESS1.setName("wa_address1");
        COLUMN_WA_ADDRESS1.setType(SQLSimpleTypes.varcharType(255));

        COLUMN_WA_ADDRESS2 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        COLUMN_WA_ADDRESS2.setName("wa_address2");
        COLUMN_WA_ADDRESS2.setType(SQLSimpleTypes.varcharType(255));

        COLUMN_WA_ADDRESS3 = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        COLUMN_WA_ADDRESS3.setName("wa_address3");
        COLUMN_WA_ADDRESS3.setType(SQLSimpleTypes.varcharType(255));

        // Create levels for Warehouse2 dimension
        LEVEL_ADDRESS3 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_ADDRESS3.setName("address3");
        LEVEL_ADDRESS3.setColumn(COLUMN_WA_ADDRESS3);
        LEVEL_ADDRESS3.setUniqueMembers(true);

        LEVEL_ADDRESS2 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_ADDRESS2.setName("address2");
        LEVEL_ADDRESS2.setColumn(COLUMN_WA_ADDRESS2);
        LEVEL_ADDRESS2.setUniqueMembers(true);

        LEVEL_ADDRESS1 = LevelFactory.eINSTANCE.createLevel();
        LEVEL_ADDRESS1.setName("address1");
        LEVEL_ADDRESS1.setColumn(COLUMN_WA_ADDRESS1);
        LEVEL_ADDRESS1.setUniqueMembers(false);

        LEVEL_NAME = LevelFactory.eINSTANCE.createLevel();
        LEVEL_NAME.setName("name");
        LEVEL_NAME.setColumn(CatalogSupplier.COLUMN_WAREHOUSE_NAME_WAREHOUSE);
        LEVEL_NAME.setUniqueMembers(false);

        // Create table query for warehouse
        TABLE_QUERY_WAREHOUSE = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_WAREHOUSE.setTable(CatalogSupplier.TABLE_WAREHOUSE);

        // Create hierarchy for Warehouse2
        HIERARCHY_WAREHOUSE2 = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_WAREHOUSE2.setHasAll(true);
        HIERARCHY_WAREHOUSE2.setPrimaryKey(CatalogSupplier.COLUMN_WAREHOUSE_ID_WAREHOUSE);
        HIERARCHY_WAREHOUSE2.setSource(TABLE_QUERY_WAREHOUSE);
        HIERARCHY_WAREHOUSE2.getLevels().addAll(List.of(
            LEVEL_ADDRESS3,
            LEVEL_ADDRESS2,
            LEVEL_ADDRESS1,
            LEVEL_NAME
        ));

        // Create dimension Warehouse2
        DIMENSION_WAREHOUSE2 = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_WAREHOUSE2.setName("Warehouse2");
        DIMENSION_WAREHOUSE2.getHierarchies().add(HIERARCHY_WAREHOUSE2);

        // Create dimension connector for Product (reuse from CatalogSupplier)
        CONNECTOR_PRODUCT = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_PRODUCT.setOverrideDimensionName("Product");
        CONNECTOR_PRODUCT.setDimension(CatalogSupplier.DIMENSION_PRODUCT);
        CONNECTOR_PRODUCT.setForeignKey(CatalogSupplier.COLUMN_PRODUCT_ID_INVENTORY_FACT);

        // Create dimension connector for Warehouse2
        CONNECTOR_WAREHOUSE2 = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_WAREHOUSE2.setOverrideDimensionName("Warehouse2");
        CONNECTOR_WAREHOUSE2.setDimension(DIMENSION_WAREHOUSE2);
        CONNECTOR_WAREHOUSE2.setForeignKey(CatalogSupplier.COLUMN_WAREHOUSE_ID_INVENTORY_FACT);

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

        // Create cube
        CUBE_WAREHOUSE2 = CubeFactory.eINSTANCE.createPhysicalCube();
        CUBE_WAREHOUSE2.setName("Warehouse2");
        CUBE_WAREHOUSE2.setSource(TABLE_QUERY_INVENTORY_FACT);
        CUBE_WAREHOUSE2.getDimensionConnectors().addAll(List.of(
            CONNECTOR_PRODUCT,
            CONNECTOR_WAREHOUSE2
        ));
        CUBE_WAREHOUSE2.getMeasureGroups().add(MEASURE_GROUP);
    }

    public TestMultiLevelMemberConstraintNullParentModifier(Catalog baseCatalog) {
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
