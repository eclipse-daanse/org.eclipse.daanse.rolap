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

import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
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

/**
 * Self-contained fixture for {@link SqlQueryTest#testApproxRowCountOverridesCount}:
 * adds an "ApproxTest" cube with a "Gender" level carrying an
 * {@code approxRowCount}, on top of the FoodMart catalog. See MONDRIAN-711.
 */
public class SqlQueryTestApproxRowCountModifierEmf implements CatalogMappingSupplier {

    private CatalogImpl catalog;

    public SqlQueryTestApproxRowCountModifierEmf(Catalog cat) {
        // Copy catalog using EcoreUtil
        catalog = EmfUtil.copy((CatalogImpl) cat);

        // Create cube
        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("ApproxTest");

        // Set up query
        TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
        tableQuery.setTable(CatalogSupplier.TABLE_SALES_FACT);
        cube.setSource(tableQuery);

        // Create Gender level
        Level genderLevel = LevelFactory.eINSTANCE.createLevel();
        genderLevel.setName("Gender");
        genderLevel.setColumn(CatalogSupplier.COLUMN_GENDER_CUSTOMER);
        genderLevel.setColumnType(ColumnInternalDataType.NUMERIC);
        genderLevel.setUniqueMembers(true);
        genderLevel.setApproxRowCount("2");

        // Create hierarchy
        ExplicitHierarchy hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        hierarchy.setHasAll(true);
        hierarchy.setAllMemberName("All Gender");
        hierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);

        TableSource customerTableQuery = SourceFactory.eINSTANCE.createTableSource();
        customerTableQuery.setTable(CatalogSupplier.TABLE_CUSTOMER);
        hierarchy.setSource(customerTableQuery);

        hierarchy.getLevels().add(genderLevel);

        // Create dimension
        StandardDimension dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        dimension.setName("Gender");
        dimension.getHierarchies().add(hierarchy);

        // Create dimension connector
        DimensionConnector dimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        dimConnector.setOverrideDimensionName("Gender");
        dimConnector.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);
        dimConnector.setDimension(dimension);

        cube.getDimensionConnectors().add(dimConnector);

        // Create measure
        SumMeasure unitSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        unitSalesMeasure.setName("Unit Sales");
        unitSalesMeasure.setColumn(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT);

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(unitSalesMeasure);

        cube.getMeasureGroups().add(measureGroup);

        // Add cube to catalog
        catalog.getImportedElement().add(cube);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
