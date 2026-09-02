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
package org.eclipse.daanse.rolap.sql;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ExpressionColumn;
import org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.SqlStatement;
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
 * Self-contained fixture for {@link SqlQueryTest#testDoubleInList}: adds a
 * "Sales 3" cube with a "StoreEmpSalary" level whose caption is a LucidDB-only
 * SQL expression, on top of the FoodMart catalog. This scenario only ever
 * exercises SQL generation under the LucidDB dialect, so under any other
 * dialect (this module always runs under H2) the test returns before using
 * it -- kept for parity with the pre-migration test.
 */
public class SqlQueryTestDoubleInListModifierEmf implements CatalogMappingSupplier {

    private CatalogImpl catalog;

    public SqlQueryTestDoubleInListModifierEmf(Catalog cat) {
        // Copy catalog using EcoreUtil
        catalog = EmfUtil.copy((CatalogImpl) cat);

        // Create cube
        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("Sales 3");

        // Set up query
        TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
        tableQuery.setTable(CatalogSupplier.TABLE_SALES_FACT);
        cube.setSource(tableQuery);

        // Create SQL expression for caption column
        ExpressionColumn captionExpression = RelationalFactory.eINSTANCE.createExpressionColumn();
        captionExpression.setType(SQLSimpleTypes.decimalType(18, 4));

        // Create SQL statement for LucidDB
        SqlStatement sqlStatement = SourceFactory.eINSTANCE.createSqlStatement();
        sqlStatement.getDialects().add("luciddb");
        sqlStatement.setBody("cast(cast(\"salary\" as double)*cast(1000.0 as double)/cast(3.1234567890123456 as double) as double)");
        captionExpression.getSqls().add(sqlStatement);

        // Create Salary level
        Level salaryLevel = LevelFactory.eINSTANCE.createLevel();
        salaryLevel.setName("Salary");
        salaryLevel.setColumn(CatalogSupplier.COLUMN_SALARY_EMPLOYEE);
        salaryLevel.setColumnType(ColumnInternalDataType.NUMERIC);
        salaryLevel.setUniqueMembers(true);
        salaryLevel.setApproxRowCount("10000000");
        salaryLevel.setCaptionColumn(captionExpression);

        // Create hierarchy
        ExplicitHierarchy hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        hierarchy.setHasAll(true);
        hierarchy.setAllMemberName("All Salary");
        hierarchy.setPrimaryKey(CatalogSupplier.COLUMN_STORE_ID_EMPLOYEE);

        TableSource employeeTableQuery = SourceFactory.eINSTANCE.createTableSource();
        employeeTableQuery.setTable(CatalogSupplier.TABLE_EMPLOYEE);
        hierarchy.setSource(employeeTableQuery);

        hierarchy.getLevels().add(salaryLevel);

        // Create dimension
        StandardDimension dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        dimension.setName("StoreEmpSalary");
        dimension.getHierarchies().add(hierarchy);

        // Create dimension connector
        DimensionConnector dimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        dimConnector.setOverrideDimensionName("StoreEmpSalary");
        dimConnector.setForeignKey(CatalogSupplier.COLUMN_STORE_ID_SALESFACT);
        dimConnector.setDimension(dimension);

        cube.getDimensionConnectors().add(dimConnector);

        // Create measure
        SumMeasure storeCostMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        storeCostMeasure.setName("Store Cost");
        storeCostMeasure.setColumn(CatalogSupplier.COLUMN_STORE_COST_SALESFACT);

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(storeCostMeasure);

        cube.getMeasureGroups().add(measureGroup);

        // Add cube to catalog
        catalog.getImportedElement().add(cube);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
