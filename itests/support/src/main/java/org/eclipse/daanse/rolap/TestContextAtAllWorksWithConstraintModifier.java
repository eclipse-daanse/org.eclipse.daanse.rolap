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
 * EMF version of TestContextAtAllWorksWithConstraintModifier from NonEmptyTest.
 * Creates a cube "onlyGender" with a Gender dimension.
 *
 * <Cube name="onlyGender">
 *   <Table name="sales_fact_1997"/>
 *   <Dimension name="Gender" foreignKey="customer_id">
 *     <Hierarchy hasAll="true" allMemberName="All Gender" primaryKey="customer_id">
 *       <Table name="customer"/>
 *       <Level name="Gender" column="gender" uniqueMembers="true"/>
 *     </Hierarchy>
 *   </Dimension>
 *   <Measure name="Unit Sales" column="unit_sales" aggregator="sum"/>
 * </Cube>
 */
public class TestContextAtAllWorksWithConstraintModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    // Static level for Gender
    private static final Level LEVEL_GENDER;

    // Static hierarchy for Gender
    private static final ExplicitHierarchy HIERARCHY_GENDER;
    private static final TableSource TABLE_QUERY_CUSTOMER;

    // Static dimension Gender
    private static final StandardDimension DIMENSION_GENDER;

    // Static dimension connector for Gender
    private static final DimensionConnector CONNECTOR_GENDER;

    // Static table query for fact table
    private static final TableSource TABLE_QUERY_SALES_FACT;

    // Static measure
    private static final SumMeasure MEASURE_UNIT_SALES;

    // Static measure group
    private static final MeasureGroup MEASURE_GROUP;

    // Static cube
    private static final PhysicalCube CUBE_ONLY_GENDER;

    static {
        // Create level for Gender
        LEVEL_GENDER = LevelFactory.eINSTANCE.createLevel();
        LEVEL_GENDER.setName("Gender");
        LEVEL_GENDER.setColumn(CatalogSupplier.COLUMN_GENDER_CUSTOMER);
        LEVEL_GENDER.setUniqueMembers(true);

        // Create table query for customer
        TABLE_QUERY_CUSTOMER = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_CUSTOMER.setTable(CatalogSupplier.TABLE_CUSTOMER);

        // Create hierarchy for Gender
        HIERARCHY_GENDER = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_GENDER.setHasAll(true);
        HIERARCHY_GENDER.setAllMemberName("All Gender");
        HIERARCHY_GENDER.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);
        HIERARCHY_GENDER.setSource(TABLE_QUERY_CUSTOMER);
        HIERARCHY_GENDER.getLevels().add(LEVEL_GENDER);

        // Create dimension Gender
        DIMENSION_GENDER = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_GENDER.setName("Gender");
        DIMENSION_GENDER.getHierarchies().add(HIERARCHY_GENDER);

        // Create dimension connector for Gender
        CONNECTOR_GENDER = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_GENDER.setOverrideDimensionName("Gender");
        CONNECTOR_GENDER.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);
        CONNECTOR_GENDER.setDimension(DIMENSION_GENDER);

        // Create fact table query
        TABLE_QUERY_SALES_FACT = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_SALES_FACT.setTable(CatalogSupplier.TABLE_SALES_FACT);

        // Create measure
        MEASURE_UNIT_SALES = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_UNIT_SALES.setName("Unit Sales");
        MEASURE_UNIT_SALES.setColumn(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT);

        // Create measure group
        MEASURE_GROUP = CubeFactory.eINSTANCE.createMeasureGroup();
        MEASURE_GROUP.getMeasures().add(MEASURE_UNIT_SALES);

        // Create cube
        CUBE_ONLY_GENDER = CubeFactory.eINSTANCE.createPhysicalCube();
        CUBE_ONLY_GENDER.setName("onlyGender");
        CUBE_ONLY_GENDER.setSource(TABLE_QUERY_SALES_FACT);
        CUBE_ONLY_GENDER.getDimensionConnectors().add(CONNECTOR_GENDER);
        CUBE_ONLY_GENDER.getMeasureGroups().add(MEASURE_GROUP);
    }

    public TestContextAtAllWorksWithConstraintModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        this.catalog = EmfUtil.copy((CatalogImpl) baseCatalog);

        // Add the cube to the catalog
        this.catalog.getImportedElement().add(CUBE_ONLY_GENDER);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
