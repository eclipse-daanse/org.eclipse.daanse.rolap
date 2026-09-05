/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.rolap;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.key.CellKey;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
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
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
/**
 * Test that the implementations of the CellKey interface are correct.
 *
 * @author Richard M. Emberson
 */
@RolapContextTest(FoodmartTestInstance.class)
class CellKeyTest  {

    @BeforeEach
    public void beforeEach() {
    }

    @AfterEach
    public void afterEach() {
    }

    /**
     * Der SalesTest-Katalog mit City/Gender/Address2-Dimensionen entsteht per
     * Komposition ({@code catalog = { FoodMart, TestCellLookupModifierEmf }})
     * statt {@code withSchemaEmf} im Testkörper; ExpandNonNative=false kommt
     * über {@code @RolapConfig} statt einer TestContextImpl-Mutation.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, TestCellLookupModifierEmf.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "false", type = Boolean.class)
    void testCellLookup(Context<?> context) {
        String query =
            "With Set [*NATIVE_CJ_SET] as NonEmptyCrossJoin([Gender].Children, [Address2].Children) "
            + "Select Generate([*NATIVE_CJ_SET], {([Gender].CurrentMember, [Address2].CurrentMember)}) on columns "
            + "From [SalesTest] where ([City].[Redwood City])";

        String result =
            "Axis #0:\n"
            + "{[City].[City].[Redwood City]}\n"
            + "Axis #1:\n"
            + "{[Gender].[Gender].[F], [Address2].[Address2].[#null]}\n"
            + "{[Gender].[Gender].[F], [Address2].[Address2].[#2]}\n"
            + "{[Gender].[Gender].[F], [Address2].[Address2].[Unit H103]}\n"
            + "{[Gender].[Gender].[M], [Address2].[Address2].[#null]}\n"
            + "{[Gender].[Gender].[M], [Address2].[Address2].[#208]}\n"
            + "Row #0: 71\n"
            + "Row #0: 10\n"
            + "Row #0: 3\n"
            + "Row #0: 52\n"
            + "Row #0: 8\n";

        assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid(result);
    }

    /** Named bridge onto the FoodMart CSVs (for the data=-Supplier form). */
    public static class FoodmartData implements org.eclipse.daanse.cwm.testkit.api.DataSupplier {
        @Override
        public java.util.Map<String, java.net.URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

    /**
     * Creates the SalesTest cube with City, Gender, and Address2 dimensions
     * over the FoodMart catalog.
     */
    public static class TestCellLookupModifierEmf implements CatalogMappingSupplier {

        private CatalogImpl catalog;

        public TestCellLookupModifierEmf(Catalog cat) {
            // Copy catalog using EcoreUtil
            catalog = EmfUtil.copy((CatalogImpl) cat);

            // Create measure "Unit Sales" using RolapMappingFactory
            SumMeasure measure =
                MeasureFactory.eINSTANCE.createSumMeasure();
            measure.setName("Unit Sales");
            measure.setColumn(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT);
            measure.setFormatString("Standard");

            // Create level "city" for City dimension
            Level cityLevel =
                LevelFactory.eINSTANCE.createLevel();
            cityLevel.setName("city");
            cityLevel.setColumn(CatalogSupplier.COLUMN_CITY_CUSTOMER);
            cityLevel.setUniqueMembers(true);

            TableSource customerQuery = SourceFactory.eINSTANCE.createTableSource();
            customerQuery.setTable(CatalogSupplier.TABLE_CUSTOMER);

            // Create hierarchy for City dimension
            ExplicitHierarchy cityHierarchy =
                HierarchyFactory.eINSTANCE.createExplicitHierarchy();
            cityHierarchy.setHasAll(true);
            cityHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);
            cityHierarchy.setSource(customerQuery);
            cityHierarchy.getLevels().add(cityLevel);

            // Create City dimension
            StandardDimension cityDimension =
                DimensionFactory.eINSTANCE.createStandardDimension();
            cityDimension.setName("City");
            cityDimension.getHierarchies().add(cityHierarchy);

            // Create dimension connector for City
            DimensionConnector cityConnector =
                DimensionFactory.eINSTANCE.createDimensionConnector();
            cityConnector.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);
            cityConnector.setOverrideDimensionName("City");
            cityConnector.setDimension(cityDimension);

            // Create level "gender" for Gender dimension
            Level genderLevel =
                LevelFactory.eINSTANCE.createLevel();
            genderLevel.setName("gender");
            genderLevel.setColumn(CatalogSupplier.COLUMN_GENDER_CUSTOMER);
            genderLevel.setUniqueMembers(true);

            TableSource customerQuery1 = SourceFactory.eINSTANCE.createTableSource();
            customerQuery1.setTable(CatalogSupplier.TABLE_CUSTOMER);

            // Create hierarchy for Gender dimension
            ExplicitHierarchy genderHierarchy =
                HierarchyFactory.eINSTANCE.createExplicitHierarchy();
            genderHierarchy.setHasAll(true);
            genderHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);
            genderHierarchy.setSource(customerQuery1);
            genderHierarchy.getLevels().add(genderLevel);

            // Create Gender dimension
            StandardDimension genderDimension =
                DimensionFactory.eINSTANCE.createStandardDimension();
            genderDimension.setName("Gender");
            genderDimension.getHierarchies().add(genderHierarchy);

            // Create dimension connector for Gender
            DimensionConnector genderConnector =
                DimensionFactory.eINSTANCE.createDimensionConnector();
            genderConnector.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);
            genderConnector.setOverrideDimensionName("Gender");
            genderConnector.setDimension(genderDimension);

            // Create level "addr" for Address2 dimension
            Level addrLevel =
                LevelFactory.eINSTANCE.createLevel();
            addrLevel.setName("addr");
            addrLevel.setColumn(CatalogSupplier.COLUMN_ADDRESS2_CUSTOMER);
            addrLevel.setUniqueMembers(true);

            TableSource customerQuery2 = SourceFactory.eINSTANCE.createTableSource();
            customerQuery2.setTable(CatalogSupplier.TABLE_CUSTOMER);

            // Create hierarchy for Address2 dimension
            ExplicitHierarchy addrHierarchy =
                HierarchyFactory.eINSTANCE.createExplicitHierarchy();
            addrHierarchy.setHasAll(true);
            addrHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);
            addrHierarchy.setSource(customerQuery2);
            addrHierarchy.getLevels().add(addrLevel);

            // Create Address2 dimension
            StandardDimension addrDimension =
                DimensionFactory.eINSTANCE.createStandardDimension();
            addrDimension.setName("Address2");
            addrDimension.getHierarchies().add(addrHierarchy);

            // Create dimension connector for Address2
            DimensionConnector addrConnector =
                DimensionFactory.eINSTANCE.createDimensionConnector();
            addrConnector.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);
            addrConnector.setOverrideDimensionName("Address2");
            addrConnector.setDimension(addrDimension);

            // Create measure group
            MeasureGroup measureGroup =
                CubeFactory.eINSTANCE.createMeasureGroup();
            measureGroup.getMeasures().add(measure);

            TableSource cubeQuery = SourceFactory.eINSTANCE.createTableSource();
            cubeQuery.setTable(CatalogSupplier.TABLE_SALES_FACT);

            // Create SalesTest cube
            PhysicalCube salesTestCube =
                CubeFactory.eINSTANCE.createPhysicalCube();
            salesTestCube.setName("SalesTest");
            salesTestCube.setDefaultMeasure(measure);
            salesTestCube.setSource(cubeQuery);
            salesTestCube.getDimensionConnectors().add(cityConnector);
            salesTestCube.getDimensionConnectors().add(genderConnector);
            salesTestCube.getDimensionConnectors().add(addrConnector);
            salesTestCube.getMeasureGroups().add(measureGroup);

            catalog.getImportedElement().add(salesTestCube);
        }

        @Override
        public Catalog get() {
            return catalog;
        }
    }

    void testSize() {
        for (int i = 1; i < 20; i++) {
            assertEquals(i, CellKey.Generator.newCellKey(new int[i]).size());
            assertEquals(i, CellKey.Generator.newCellKey(i).size());
        }
    }
}
