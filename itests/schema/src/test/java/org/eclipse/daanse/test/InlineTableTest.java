/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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

package org.eclipse.daanse.test;

import static org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct.getDatabaseProduct;
import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.DataSlot;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.InstanceFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Row;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.relational.InlineTable;
import org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.SumMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.Dimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.StandardDimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ExplicitHierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.HierarchyFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.test.FoodmartData;
/**
 * Unit test for the InlineTable element, defining tables whose values are held
 * in the Mondrian schema file, not in the database.
 *
 * @author jhyde
 */
@RolapContextTest(FoodmartTestInstance.class)
class InlineTableTest {

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, TestInlineTableModifierEmf.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testInlineTable(Context<?> context) {
        final String cubeName = "Sales_inline";
        /*
        class TestInlineTableModifier extends PojoMappingModifier {

            public TestInlineTableModifier(CatalogMapping catalog) {
                super(catalog);
            }

            protected List<CubeMapping> cubes(List<? extends CubeMapping> cubes) {
                PhysicalColumnMappingImpl promoId = PhysicalColumnMappingImpl.builder().withName("promo_id").withDataType(ColumnDataType.NUMERIC).build();
                PhysicalColumnMappingImpl promoName = PhysicalColumnMappingImpl.builder().withName("promo_name").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(20).build();
                InlineTableMappingImpl t = InlineTableMappingImpl.builder()
                .withName("alt_promotion")
                .withColumns(List.of(promoId, promoName))
                .withRows(List.of(
                       RowMappingImpl.builder().withRowValues(List.of(
                            RowValueMappingImpl.builder().withColumn(promoId).withValue("0").build(),
                            RowValueMappingImpl.builder().withColumn(promoName).withValue("Promo0").build())).build(),
                       RowMappingImpl.builder().withRowValues(List.of(
                               RowValueMappingImpl.builder().withColumn(promoId).withValue("1").build(),
                               RowValueMappingImpl.builder().withColumn(promoName).withValue("Promo1").build())).build()
                ))
                .build();

                List<CubeMapping> result = new ArrayList<>();
                result.addAll(super.cubes(cubes));
                result.add(PhysicalCubeMappingImpl.builder()
                    .withName(cubeName)
                    .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.SALES_FACT_1997_TABLE).build())
                    .withDimensionConnectors(List.of(
                    	DimensionConnectorMappingImpl.builder()
                    		.withOverrideDimensionName("Time")
                            .withForeignKey(FoodmartMappingSupplier.TIME_ID_COLUMN_IN_SALES_FACT_1997)
                            .withDimension(FoodmartMappingSupplier.DIMENSION_TIME)
                            .build(),
                        DimensionConnectorMappingImpl.builder()
                        	.withOverrideDimensionName("Alternative Promotion")
                        	.withForeignKey(FoodmartMappingSupplier.PROMOTION_ID_COLUMN_IN_SALES_FACT_1997)
                        	.withDimension(
                        		StandardDimensionMappingImpl.builder()
                        			.withName("Alternative Promotion")
                        			.withHierarchies(List.of(
                        				ExplicitHierarchyMappingImpl.builder()
                        					.withHasAll(true)
                        					.withPrimaryKey(promoId)
                        					.withQuery(InlineTableQueryMappingImpl.builder()
                        							.withAlias("alt_promotion")
                        							.withTable(t)
                        							.build()
                        					)
                        					.withLevels(List.of(
                        						LevelMappingImpl.builder()
                        							.withName("Alternative Promotion").withColumn(promoId)
                        							.withNameColumn(promoName)
                        							.withUniqueMembers(true)
                        							.build()
                        					))
                        					.build()
                            ))
                            .build()
                        ).build()
                    ))
                    .withMeasureGroups(List.of(
                    	MeasureGroupMappingImpl.builder()
                    	.withMeasures(List.of(
                            SumMeasureMappingImpl.builder()
                                .withName("Unit Sales")
                                .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_SALES_FACT_1997)
                                .withFormatString("Standard")
                                .withVisible(true)
                                .build(),
                            SumMeasureMappingImpl.builder()
                                .withName("Store Sales")
                                .withColumn(FoodmartMappingSupplier.STORE_SALES_COLUMN_IN_SALES_FACT_1997)
                                .withFormatString("#,###.00")
                                .build()
                    	))
                    	.build()
                    ))
                    .build());
                return result;
            }
        }
        */
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Alternative Promotion].[All Alternative Promotions].children} ON COLUMNS\n"
            + "from [" + cubeName + "] ").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Alternative Promotion].[Alternative Promotion].[Promo0]}\n"
            + "{[Alternative Promotion].[Alternative Promotion].[Promo1]}\n"
            + "Row #0: 195,448\n"
            + "Row #0: \n");
    }

    /**
     * EMF version of TestInlineTableModifier
     * Creates a test cube with inline table dimension
     */
    public static class TestInlineTableModifierEmf implements CatalogMappingSupplier {

        private CatalogImpl catalog;


        public TestInlineTableModifierEmf(Catalog cat) {
            // Copy catalog using EcoreUtil
            catalog = EmfUtil.copy((CatalogImpl) cat);

            // Create columns for inline table
            Column promoIdColumn = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
            promoIdColumn.setName("promo_id");
            promoIdColumn.setType(SQLSimpleTypes.Sql99.integerType());

            Column promoNameColumn = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
            promoNameColumn.setName("promo_name");
            promoNameColumn.setType(SQLSimpleTypes.varcharType(255));
            // promoNameColumn.setCharOctetLength(20);

            // Create inline table
            InlineTable inlineTable = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createInlineTable();
            inlineTable.setExtent(RelationalFactory.eINSTANCE.createRowSet());
            inlineTable.setName("alt_promotion");
            inlineTable.getFeature().add(promoIdColumn);
            inlineTable.getFeature().add(promoNameColumn);

            // Create first row: promo_id=0, promo_name=Promo0
            DataSlot rowValue1Col1 = InstanceFactory.eINSTANCE.createDataSlot();
            rowValue1Col1.setFeature(promoIdColumn);
            rowValue1Col1.setDataValue("0");

            DataSlot rowValue1Col2 = InstanceFactory.eINSTANCE.createDataSlot();
            rowValue1Col2.setFeature(promoNameColumn);
            rowValue1Col2.setDataValue("Promo0");

            Row row1 = RelationalFactory.eINSTANCE.createRow();
            row1.getSlot().add(rowValue1Col1);
            row1.getSlot().add(rowValue1Col2);

            // Create second row: promo_id=1, promo_name=Promo1
            DataSlot rowValue2Col1 = InstanceFactory.eINSTANCE.createDataSlot();
            rowValue2Col1.setFeature(promoIdColumn);
            rowValue2Col1.setDataValue("1");

            DataSlot rowValue2Col2 = InstanceFactory.eINSTANCE.createDataSlot();
            rowValue2Col2.setFeature(promoNameColumn);
            rowValue2Col2.setDataValue("Promo1");

            Row row2 = RelationalFactory.eINSTANCE.createRow();
            row2.getSlot().add(rowValue2Col1);
            row2.getSlot().add(rowValue2Col2);

            inlineTable.getExtent().getOwnedElement().add(row1);
            inlineTable.getExtent().getOwnedElement().add(row2);

            // Create inline table query
            InlineTableSource inlineTableQuery = SourceFactory.eINSTANCE.createInlineTableSource();
            inlineTableQuery.setAlias("alt_promotion");
            inlineTableQuery.setTable(inlineTable);

            // Create level for Alternative Promotion
            Level altPromoLevel = LevelFactory.eINSTANCE.createLevel();
            altPromoLevel.setName("Alternative Promotion");
            altPromoLevel.setColumn(promoIdColumn);
            altPromoLevel.setNameColumn(promoNameColumn);
            altPromoLevel.setUniqueMembers(true);

            // Create hierarchy
            ExplicitHierarchy altPromoHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
            altPromoHierarchy.setHasAll(true);
            altPromoHierarchy.setPrimaryKey(promoIdColumn);
            altPromoHierarchy.setSource(inlineTableQuery);
            altPromoHierarchy.getLevels().add(altPromoLevel);

            // Create dimension
            StandardDimension altPromoDimension = DimensionFactory.eINSTANCE.createStandardDimension();
            altPromoDimension.setName("Alternative Promotion");
            altPromoDimension.getHierarchies().add(altPromoHierarchy);

            // Create cube
            PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
            cube.setName("Sales_inline");

            // Set up query
            TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
            tableQuery.setTable(CatalogSupplier.TABLE_SALES_FACT);
            cube.setSource(tableQuery);

            // Create dimension connector for Time
            DimensionConnector timeDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
            timeDimConnector.setOverrideDimensionName("Time");
            timeDimConnector.setForeignKey(CatalogSupplier.COLUMN_TIME_ID_SALESFACT);
            timeDimConnector.setDimension(CatalogSupplier.DIMENSION_TIME);

            // Create dimension connector for Alternative Promotion
            DimensionConnector altPromoDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
            altPromoDimConnector.setOverrideDimensionName("Alternative Promotion");
            altPromoDimConnector.setForeignKey(CatalogSupplier.COLUMN_PROMOTION_ID_SALESFACT);
            altPromoDimConnector.setDimension(altPromoDimension);

            cube.getDimensionConnectors().add(timeDimConnector);
            cube.getDimensionConnectors().add(altPromoDimConnector);

            // Create measures
            SumMeasure unitSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
            unitSalesMeasure.setName("Unit Sales");
            unitSalesMeasure.setColumn(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT);
            unitSalesMeasure.setFormatString("Standard");
            unitSalesMeasure.setVisible(true);

            SumMeasure storeSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
            storeSalesMeasure.setName("Store Sales");
            storeSalesMeasure.setColumn(CatalogSupplier.COLUMN_STORE_SALES_SALESFACT);
            storeSalesMeasure.setFormatString("#,###.00");

            // Create measure group
            MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
            measureGroup.getMeasures().add(unitSalesMeasure);
            measureGroup.getMeasures().add(storeSalesMeasure);

            cube.getMeasureGroups().add(measureGroup);

            // Add the new cube to the catalog
            catalog.getImportedElement().add(cube);
        }

        @Override
        public Catalog get() {
            return catalog;
        }
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, TestInlineTableInSharedDimModifierEmf.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testInlineTableInSharedDim(Context<?> context) {
        final String cubeName = "Sales_inline_shared";
        /*
        class TestInlineTableInSharedDimModifier extends PojoMappingModifier {

            public TestInlineTableInSharedDimModifier(CatalogMapping catalog) {
                super(catalog);
            }

            private static final PhysicalColumnMappingImpl promoId = PhysicalColumnMappingImpl.builder().withName("promo_id").withDataType(ColumnDataType.INTEGER).build();
            private static final PhysicalColumnMappingImpl promoName = PhysicalColumnMappingImpl.builder().withName("promo_name").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(20).build();
            private static final InlineTableMappingImpl t = InlineTableMappingImpl.builder()
            .withName("alt_promotion")
            .withColumns(List.of(promoId, promoName))
            .withRows(List.of(
                   RowMappingImpl.builder().withRowValues(List.of(
                        RowValueMappingImpl.builder().withColumn(promoId).withValue("0").build(),
                        RowValueMappingImpl.builder().withColumn(promoName).withValue("First promo").build())).build(),
                   RowMappingImpl.builder().withRowValues(List.of(
                           RowValueMappingImpl.builder().withColumn(promoId).withValue("1").build(),
                           RowValueMappingImpl.builder().withColumn(promoName).withValue("Second promo").build())).build()
            ))
            .build();

            private static final StandardDimensionMappingImpl d = StandardDimensionMappingImpl.builder()
                    .withName("Shared Alternative Promotion")
                    .withHierarchies(List.of(
                        ExplicitHierarchyMappingImpl.builder()
                            .withHasAll(true)
                            .withPrimaryKey(promoId)
                            .withQuery(InlineTableQueryMappingImpl.builder()
                                .withAlias("alt_promotion")
                                .withTable(t)
                                .build())
                            .withLevels(List.of(
                                LevelMappingImpl.builder()
                                    .withName("Alternative Promotion")
                                    .withColumn(promoId)
                                    .withNameColumn(promoName)
                                    .withUniqueMembers(true)
                                    .build()
                            ))
                            .build())).build();

            @Override
            protected List<CubeMapping> cubes(List<? extends CubeMapping> cubes) {
                List<CubeMapping> result = new ArrayList<>();
                result.addAll(super.cubes(cubes));
                result.add(PhysicalCubeMappingImpl.builder()
                    .withName(cubeName)
                    .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.SALES_FACT_1997_TABLE).build())
                    .withDimensionConnectors(List.of(
                    	DimensionConnectorMappingImpl.builder()
                    		.withOverrideDimensionName("Time")
                    		.withDimension((DimensionMappingImpl) look(FoodmartMappingSupplier.DIMENSION_TIME))
                            .withForeignKey(FoodmartMappingSupplier.TIME_ID_COLUMN_IN_SALES_FACT_1997)
                            .build(),
                        DimensionConnectorMappingImpl.builder()
                            .withOverrideDimensionName("Shared Alternative Promotion")
                            .withDimension(d)
                            .withForeignKey(FoodmartMappingSupplier.PROMOTION_ID_COLUMN_IN_SALES_FACT_1997)
                            .build()
                    ))
                    .withMeasureGroups(List.of(
                    	MeasureGroupMappingImpl.builder()
                    	.withMeasures(List.of(
                            SumMeasureMappingImpl.builder()
                                .withName("Unit Sales")
                                .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_SALES_FACT_1997)
                                .withFormatString("Standard")
                                .withVisible(false)
                                .build(),
                            SumMeasureMappingImpl.builder()
                                .withName("Store Sales")
                                .withColumn(FoodmartMappingSupplier.STORE_SALES_COLUMN_IN_SALES_FACT_1997)
                                .withFormatString("#,###.00")
                                .build()
                    	))
                    	.build()
                        ))
                    .build());
                return result;
            }

            }
        */
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Shared Alternative Promotion].[All Shared Alternative Promotions].children} ON COLUMNS\n"
            + "from [" + cubeName + "] ").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Shared Alternative Promotion].[Shared Alternative Promotion].[First promo]}\n"
            + "{[Shared Alternative Promotion].[Shared Alternative Promotion].[Second promo]}\n"
            + "Row #0: 195,448\n"
            + "Row #0: \n");
    }

    /**
     * EMF version of TestInlineTableInSharedDimModifier
     * Creates a test cube with shared inline table dimension
     */
    public static class TestInlineTableInSharedDimModifierEmf implements CatalogMappingSupplier {

        private CatalogImpl catalog;

        public TestInlineTableInSharedDimModifierEmf(Catalog cat) {
            // Copy catalog using EcoreUtil
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) cat);
            this.catalog = (CatalogImpl) copier.get(cat);


            // Static shared dimension with inline table
            Column PROMO_ID_COLUMN;
            Column PROMO_NAME_COLUMN;
            InlineTable SHARED_INLINE_TABLE;
            StandardDimension SHARED_DIMENSION;

            // Create columns for inline table
            PROMO_ID_COLUMN = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
            PROMO_ID_COLUMN.setName("promo_id");
            PROMO_ID_COLUMN.setType(SQLSimpleTypes.Sql99.integerType());

            PROMO_NAME_COLUMN = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
            PROMO_NAME_COLUMN.setName("promo_name");
            PROMO_NAME_COLUMN.setType(SQLSimpleTypes.varcharType(255));
            // PROMO_NAME_COLUMN.setCharOctetLength(20);

            // Create inline table
            SHARED_INLINE_TABLE = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createInlineTable();
            SHARED_INLINE_TABLE.setExtent(RelationalFactory.eINSTANCE.createRowSet());
            SHARED_INLINE_TABLE.setName("alt_promotion");
            SHARED_INLINE_TABLE.getFeature().add(PROMO_ID_COLUMN);
            SHARED_INLINE_TABLE.getFeature().add(PROMO_NAME_COLUMN);

            // Create first row: promo_id=0, promo_name=First promo
            DataSlot rowValue1Col1 = InstanceFactory.eINSTANCE.createDataSlot();
            rowValue1Col1.setFeature(PROMO_ID_COLUMN);
            rowValue1Col1.setDataValue("0");

            DataSlot rowValue1Col2 = InstanceFactory.eINSTANCE.createDataSlot();
            rowValue1Col2.setFeature(PROMO_NAME_COLUMN);
            rowValue1Col2.setDataValue("First promo");

            Row row1 = RelationalFactory.eINSTANCE.createRow();
            row1.getSlot().add(rowValue1Col1);
            row1.getSlot().add(rowValue1Col2);

            // Create second row: promo_id=1, promo_name=Second promo
            DataSlot rowValue2Col1 = InstanceFactory.eINSTANCE.createDataSlot();
            rowValue2Col1.setFeature(PROMO_ID_COLUMN);
            rowValue2Col1.setDataValue("1");

            DataSlot rowValue2Col2 = InstanceFactory.eINSTANCE.createDataSlot();
            rowValue2Col2.setFeature(PROMO_NAME_COLUMN);
            rowValue2Col2.setDataValue("Second promo");

            Row row2 = RelationalFactory.eINSTANCE.createRow();
            row2.getSlot().add(rowValue2Col1);
            row2.getSlot().add(rowValue2Col2);

            SHARED_INLINE_TABLE.getExtent().getOwnedElement().add(row1);
            SHARED_INLINE_TABLE.getExtent().getOwnedElement().add(row2);

            // Create inline table query
            InlineTableSource inlineTableQuery = SourceFactory.eINSTANCE.createInlineTableSource();
            inlineTableQuery.setAlias("alt_promotion");
            inlineTableQuery.setTable(SHARED_INLINE_TABLE);

            // Create level for Alternative Promotion
            Level altPromoLevel = LevelFactory.eINSTANCE.createLevel();
            altPromoLevel.setName("Alternative Promotion");
            altPromoLevel.setColumn(PROMO_ID_COLUMN);
            altPromoLevel.setNameColumn(PROMO_NAME_COLUMN);
            altPromoLevel.setUniqueMembers(true);

            // Create hierarchy
            ExplicitHierarchy altPromoHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
            altPromoHierarchy.setHasAll(true);
            altPromoHierarchy.setPrimaryKey(PROMO_ID_COLUMN);
            altPromoHierarchy.setSource(inlineTableQuery);
            altPromoHierarchy.getLevels().add(altPromoLevel);

            // Create shared dimension
            SHARED_DIMENSION = DimensionFactory.eINSTANCE.createStandardDimension();
            SHARED_DIMENSION.setName("Shared Alternative Promotion");
            SHARED_DIMENSION.getHierarchies().add(altPromoHierarchy);

            // Create cube
            PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
            cube.setName("Sales_inline_shared");
            // Set up query
            TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
            tableQuery.setTable((Table) copier.get(CatalogSupplier.TABLE_SALES_FACT));
            cube.setSource(tableQuery);

            // Create dimension connector for Time
            DimensionConnector timeDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
            timeDimConnector.setOverrideDimensionName("Time");
            timeDimConnector.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_TIME_ID_SALESFACT));
            timeDimConnector.setDimension((Dimension) copier.get(CatalogSupplier.DIMENSION_TIME));

            // Create dimension connector for Shared Alternative Promotion
            DimensionConnector sharedPromoDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
            sharedPromoDimConnector.setOverrideDimensionName("Shared Alternative Promotion");
            sharedPromoDimConnector.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_PROMOTION_ID_SALESFACT));
            sharedPromoDimConnector.setDimension(SHARED_DIMENSION);

            cube.getDimensionConnectors().add(timeDimConnector);
            cube.getDimensionConnectors().add(sharedPromoDimConnector);

            // Create measures
            SumMeasure unitSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
            unitSalesMeasure.setName("Unit Sales");
            unitSalesMeasure.setColumn((Column) copier.get(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT));
            unitSalesMeasure.setFormatString("Standard");
            unitSalesMeasure.setVisible(false);

            SumMeasure storeSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
            storeSalesMeasure.setName("Store Sales");
            storeSalesMeasure.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_SALES_SALESFACT));
            storeSalesMeasure.setFormatString("#,###.00");

            // Create measure group
            MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
            measureGroup.getMeasures().add(unitSalesMeasure);
            measureGroup.getMeasures().add(storeSalesMeasure);

            cube.getMeasureGroups().add(measureGroup);

            // Add the new cube to the catalog
            catalog.getImportedElement().add(cube);
        }

        @Override
        public Catalog get() {
            return catalog;
        }
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, TestInlineTableSnowflakeModifierEmf.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testInlineTableSnowflake(Context<?> context) {
        if (getDatabaseProduct(getDialect(context.getConnectionWithDefaultRole()).name())
            == DatabaseProduct.INFOBRIGHT)
        {
            // Infobright has a bug joining an inline table. Gives error
            // "Illegal mix of collations (ascii_bin,IMPLICIT) and
            // (utf8_general_ci,COERCIBLE) for operation '='".
            return;
        }
        final String cubeName = "Sales_inline_snowflake";
        /*
        class TestInlineTableSnowflakeModifier extends PojoMappingModifier {

            public TestInlineTableSnowflakeModifier(CatalogMapping catalog) {
                super(catalog);
            }

            protected List<CubeMapping> cubes(List<? extends CubeMapping> cubes) {
                PhysicalColumnMappingImpl nationName = PhysicalColumnMappingImpl.builder().withName("nation_name").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(20).build();
                PhysicalColumnMappingImpl nationShortcode = PhysicalColumnMappingImpl.builder().withName("nation_shortcode").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(20).build();
                InlineTableMappingImpl t = InlineTableMappingImpl.builder()
                .withName("nation")
                .withColumns(List.of(nationName, nationShortcode))
                .withRows(List.of(
                       RowMappingImpl.builder().withRowValues(List.of(
                            RowValueMappingImpl.builder().withColumn(nationName).withValue("USA").build(),
                            RowValueMappingImpl.builder().withColumn(nationShortcode).withValue("US").build())).build(),
                       RowMappingImpl.builder().withRowValues(List.of(
                               RowValueMappingImpl.builder().withColumn(nationName).withValue("Mexico").build(),
                               RowValueMappingImpl.builder().withColumn(nationShortcode).withValue("MX").build())).build(),
                       RowMappingImpl.builder().withRowValues(List.of(
                               RowValueMappingImpl.builder().withColumn(nationName).withValue("Canada").build(),
                               RowValueMappingImpl.builder().withColumn(nationShortcode).withValue("CA").build())).build()
                ))
                .build();
            	List<CubeMapping> result = new ArrayList<>();
                result.addAll(super.cubes(cubes));
                JoinQueryMappingImpl j = JoinQueryMappingImpl.builder()
                		.withLeft(JoinedQueryElementMappingImpl.builder()
                				.withKey(FoodmartMappingSupplier.STORE_COUNTRY_COLUMN_IN_STORE)
                				.withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.STORE_TABLE).build())
                				.build())
                		.withRight(JoinedQueryElementMappingImpl.builder()
                				.withKey(nationName)
                				.withQuery(InlineTableQueryMappingImpl.builder()
                                .withAlias("nation")
                                .withTable(t)
                				.build()).build())
                		.build();

                result.add(PhysicalCubeMappingImpl.builder()
                    .withName(cubeName)
                    .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.SALES_FACT_1997_TABLE).build())
                    .withDimensionConnectors(List.of(
                    	DimensionConnectorMappingImpl.builder()
                    		.withOverrideDimensionName("Time")
                    		.withDimension((DimensionMappingImpl) look(FoodmartMappingSupplier.DIMENSION_TIME))
                            .withForeignKey(FoodmartMappingSupplier.TIME_ID_COLUMN_IN_SALES_FACT_1997)
                            .build(),
                      	DimensionConnectorMappingImpl.builder()
                    		.withOverrideDimensionName("Store")
                            .withForeignKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_SALES_FACT_1997)
                            .withDimension(
                            	StandardDimensionMappingImpl.builder()
                            		.withName("Store")
                            		.withHierarchies(List.of(
                            			ExplicitHierarchyMappingImpl.builder()
                            				.withHasAll(true)
                            				.withPrimaryKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_STORE)
                            				.withQuery(j)
                                    .withLevels(List.of(
                                        LevelMappingImpl.builder()
                                            .withName("Store Country")
                                            .withColumn(nationName)
                                            .withNameColumn(nationShortcode)
                                            .withUniqueMembers(true)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Store State")
                                            .withColumn(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_STORE)
                                            .withUniqueMembers(true)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Store City")
                                            .withColumn(FoodmartMappingSupplier.STORE_CITY_COLUMN_IN_STORE)
                                            .withUniqueMembers(false)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Store Name")
                                            .withColumn(FoodmartMappingSupplier.STORE_NAME_COLUMN_IN_STORE)
                                            .withUniqueMembers(true)
                                            .build()
                                        ))
                                    .build()
                            ))
                            .build()
                          ).build()
                    ))
                    .withMeasureGroups(List.of(
                    		MeasureGroupMappingImpl.builder()
                    		.withMeasures(List.of(
                                SumMeasureMappingImpl.builder()
                                    .withName("Unit Sales")
                                    .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_SALES_FACT_1997)
                                    .withFormatString("Standard")
                                    .withVisible(false)
                                    .build(),
                                SumMeasureMappingImpl.builder()
                                    .withName("Store Sales")
                                    .withColumn(FoodmartMappingSupplier.STORE_SALES_COLUMN_IN_SALES_FACT_1997)
                                    .withFormatString("#,###.00")
                                    .build()
                    				))
                    		.build()
                    		))
                    .build());
                return result;
            }

        }
        */
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Store].children} ON COLUMNS\n"
            + "from [" + cubeName + "] ").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store].[Store].[CA]}\n"
            + "{[Store].[Store].[MX]}\n"
            + "{[Store].[Store].[US]}\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 266,773\n");
    }

    /**
     * EMF version of TestInlineTableSnowflakeModifier
     * Creates a test cube with snowflake schema using inline table
     */
    public static class TestInlineTableSnowflakeModifierEmf implements CatalogMappingSupplier {

        private CatalogImpl catalog;

        public TestInlineTableSnowflakeModifierEmf(Catalog cat) {
            // Copy catalog using EcoreUtil
            catalog = EmfUtil.copy((CatalogImpl) cat);

            // Create columns for nation inline table
            Column nationNameColumn = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
            nationNameColumn.setName("nation_name");
            nationNameColumn.setType(SQLSimpleTypes.varcharType(255));
            // nationNameColumn.setCharOctetLength(20);

            Column nationShortcodeColumn = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
            nationShortcodeColumn.setName("nation_shortcode");
            nationShortcodeColumn.setType(SQLSimpleTypes.varcharType(255));
            // nationShortcodeColumn.setCharOctetLength(20);

            // Create nation inline table
            InlineTable nationInlineTable = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createInlineTable();
            nationInlineTable.setExtent(RelationalFactory.eINSTANCE.createRowSet());
            nationInlineTable.setName("nation");
            nationInlineTable.getFeature().add(nationNameColumn);
            nationInlineTable.getFeature().add(nationShortcodeColumn);

            // Create rows for nation table
            // Row 1: USA, US
            DataSlot row1Col1 = InstanceFactory.eINSTANCE.createDataSlot();
            row1Col1.setFeature(nationNameColumn);
            row1Col1.setDataValue("USA");
            DataSlot row1Col2 = InstanceFactory.eINSTANCE.createDataSlot();
            row1Col2.setFeature(nationShortcodeColumn);
            row1Col2.setDataValue("US");
            Row row1 = RelationalFactory.eINSTANCE.createRow();
            row1.getSlot().add(row1Col1);
            row1.getSlot().add(row1Col2);

            // Row 2: Mexico, MX
            DataSlot row2Col1 = InstanceFactory.eINSTANCE.createDataSlot();
            row2Col1.setFeature(nationNameColumn);
            row2Col1.setDataValue("Mexico");
            DataSlot row2Col2 = InstanceFactory.eINSTANCE.createDataSlot();
            row2Col2.setFeature(nationShortcodeColumn);
            row2Col2.setDataValue("MX");
            Row row2 = RelationalFactory.eINSTANCE.createRow();
            row2.getSlot().add(row2Col1);
            row2.getSlot().add(row2Col2);

            // Row 3: Canada, CA
            DataSlot row3Col1 = InstanceFactory.eINSTANCE.createDataSlot();
            row3Col1.setFeature(nationNameColumn);
            row3Col1.setDataValue("Canada");
            DataSlot row3Col2 = InstanceFactory.eINSTANCE.createDataSlot();
            row3Col2.setFeature(nationShortcodeColumn);
            row3Col2.setDataValue("CA");
            Row row3 = RelationalFactory.eINSTANCE.createRow();
            row3.getSlot().add(row3Col1);
            row3.getSlot().add(row3Col2);

            nationInlineTable.getExtent().getOwnedElement().add(row1);
            nationInlineTable.getExtent().getOwnedElement().add(row2);
            nationInlineTable.getExtent().getOwnedElement().add(row3);

            // Create inline table query for nation
            InlineTableSource nationInlineTableQuery = SourceFactory.eINSTANCE.createInlineTableSource();
            nationInlineTableQuery.setAlias("nation");
            nationInlineTableQuery.setTable(nationInlineTable);

            // Create store table query
            TableSource storeTableQuery = SourceFactory.eINSTANCE.createTableSource();
            storeTableQuery.setTable(CatalogSupplier.TABLE_STORE);

            // Create join: store LEFT JOIN nation
            JoinedQueryElement leftJoin = SourceFactory.eINSTANCE.createJoinedQueryElement();
            leftJoin.setKey(CatalogSupplier.COLUMN_STORE_COUNTRY_STORE);
            leftJoin.setSource(storeTableQuery);

            JoinedQueryElement rightJoin = SourceFactory.eINSTANCE.createJoinedQueryElement();
            rightJoin.setKey(nationNameColumn);
            rightJoin.setSource(nationInlineTableQuery);

            JoinSource joinQuery = SourceFactory.eINSTANCE.createJoinSource();
            joinQuery.setLeft(leftJoin);
            joinQuery.setRight(rightJoin);

            // Create levels for Store hierarchy
            Level storeCountryLevel = LevelFactory.eINSTANCE.createLevel();
            storeCountryLevel.setName("Store Country");
            storeCountryLevel.setColumn(nationNameColumn);
            storeCountryLevel.setNameColumn(nationShortcodeColumn);
            storeCountryLevel.setUniqueMembers(true);

            Level storeStateLevel = LevelFactory.eINSTANCE.createLevel();
            storeStateLevel.setName("Store State");
            storeStateLevel.setColumn(CatalogSupplier.COLUMN_STORE_STATE_STORE);
            storeStateLevel.setUniqueMembers(true);

            Level storeCityLevel = LevelFactory.eINSTANCE.createLevel();
            storeCityLevel.setName("Store City");
            storeCityLevel.setColumn(CatalogSupplier.COLUMN_STORE_CITY_STORE);
            storeCityLevel.setUniqueMembers(false);

            Level storeNameLevel = LevelFactory.eINSTANCE.createLevel();
            storeNameLevel.setName("Store Name");
            storeNameLevel.setColumn(CatalogSupplier.COLUMN_STORE_NAME_STORE);
            storeNameLevel.setUniqueMembers(true);

            // Create hierarchy for Store
            ExplicitHierarchy storeHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
            storeHierarchy.setHasAll(true);
            storeHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_STORE_ID_STORE);
            storeHierarchy.setSource(joinQuery);
            storeHierarchy.getLevels().add(storeCountryLevel);
            storeHierarchy.getLevels().add(storeStateLevel);
            storeHierarchy.getLevels().add(storeCityLevel);
            storeHierarchy.getLevels().add(storeNameLevel);

            // Create Store dimension
            StandardDimension storeDimension = DimensionFactory.eINSTANCE.createStandardDimension();
            storeDimension.setName("Store");
            storeDimension.getHierarchies().add(storeHierarchy);

            // Create cube
            PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
            cube.setName("Sales_inline_snowflake");

            // Set up query for cube (sales_fact_1997)
            TableSource cubeTableQuery = SourceFactory.eINSTANCE.createTableSource();
            cubeTableQuery.setTable(CatalogSupplier.TABLE_SALES_FACT);
            cube.setSource(cubeTableQuery);

            // Create dimension connector for Time
            DimensionConnector timeDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
            timeDimConnector.setOverrideDimensionName("Time");
            timeDimConnector.setForeignKey(CatalogSupplier.COLUMN_TIME_ID_SALESFACT);
            timeDimConnector.setDimension(CatalogSupplier.DIMENSION_TIME);

            // Create dimension connector for Store
            DimensionConnector storeDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
            storeDimConnector.setOverrideDimensionName("Store");
            storeDimConnector.setForeignKey(CatalogSupplier.COLUMN_STORE_ID_SALESFACT);
            storeDimConnector.setDimension(storeDimension);

            cube.getDimensionConnectors().add(timeDimConnector);
            cube.getDimensionConnectors().add(storeDimConnector);

            // Create measures
            SumMeasure unitSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
            unitSalesMeasure.setName("Unit Sales");
            unitSalesMeasure.setColumn(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT);
            unitSalesMeasure.setFormatString("Standard");
            unitSalesMeasure.setVisible(false);

            SumMeasure storeSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
            storeSalesMeasure.setName("Store Sales");
            storeSalesMeasure.setColumn(CatalogSupplier.COLUMN_STORE_SALES_SALESFACT);
            storeSalesMeasure.setFormatString("#,###.00");

            // Create measure group
            MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
            measureGroup.getMeasures().add(unitSalesMeasure);
            measureGroup.getMeasures().add(storeSalesMeasure);

            cube.getMeasureGroups().add(measureGroup);

            // Add the new cube to the catalog
            catalog.getImportedElement().add(cube);
        }

        @Override
        public Catalog get() {
            return catalog;
        }
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, TestInlineTableDateModifierEmf.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testInlineTableDate(Context<?> context) {
        final String cubeName = "Sales_Inline_Date";
        /*
        class TestInlineTableDateModifier extends PojoMappingModifier {

            public TestInlineTableDateModifier(CatalogMapping catalog) {
                super(catalog);
            }

            @Override
            protected List<CubeMapping> cubes(List<? extends CubeMapping> cubes) {
                PhysicalColumnMappingImpl nationName = PhysicalColumnMappingImpl.builder().withName("nation_name").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(20).build();
                PhysicalColumnMappingImpl nationShortcode = PhysicalColumnMappingImpl.builder().withName("nation_shortcode").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(20).build();
                InlineTableMappingImpl t = InlineTableMappingImpl.builder()
                .withColumns(List.of(nationName, nationShortcode))
                .withRows(List.of(
                       RowMappingImpl.builder().withRowValues(List.of(
                            RowValueMappingImpl.builder().withColumn(nationName).withValue("USA").build(),
                            RowValueMappingImpl.builder().withColumn(nationShortcode).withValue("US").build())).build(),
                       RowMappingImpl.builder().withRowValues(List.of(
                               RowValueMappingImpl.builder().withColumn(nationName).withValue("Mexico").build(),
                               RowValueMappingImpl.builder().withColumn(nationShortcode).withValue("MX").build())).build(),
                       RowMappingImpl.builder().withRowValues(List.of(
                               RowValueMappingImpl.builder().withColumn(nationName).withValue("Canada").build(),
                               RowValueMappingImpl.builder().withColumn(nationShortcode).withValue("CA").build())).build()
                ))
                .build();
                PhysicalColumnMappingImpl id = PhysicalColumnMappingImpl.builder().withName("id").withDataType(ColumnDataType.NUMERIC).build();
                PhysicalColumnMappingImpl date = PhysicalColumnMappingImpl.builder().withName("date").withDataType(ColumnDataType.DATE).build();
                InlineTableMappingImpl tt = InlineTableMappingImpl.builder()
                .withName("inline_promo")
                .withColumns(List.of(id, date))
                .withRows(List.of(
                       RowMappingImpl.builder().withRowValues(List.of(
                            RowValueMappingImpl.builder().withColumn(id).withValue("1").build(),
                            RowValueMappingImpl.builder().withColumn(date).withValue("2008-04-29").build())).build(),
                       RowMappingImpl.builder().withRowValues(List.of(
                               RowValueMappingImpl.builder().withColumn(id).withValue("2").build(),
                               RowValueMappingImpl.builder().withColumn(date).withValue("2007-01-20").build())).build()
                ))
                .build();
                List<CubeMapping> result = new ArrayList<>();
                result.addAll(super.cubes(cubes));
                JoinQueryMappingImpl j = JoinQueryMappingImpl.builder()
                		.withLeft(JoinedQueryElementMappingImpl.builder()
                				.withKey(FoodmartMappingSupplier.STORE_COUNTRY_COLUMN_IN_STORE)
                				.withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.STORE_TABLE).build())
                				.build())
                		.withRight(JoinedQueryElementMappingImpl.builder()
                				.withKey(nationName)
                				.withQuery(InlineTableQueryMappingImpl.builder()
                                        .withAlias("nation")
                                        .withTable(t)
                				.build()).build())
                		.build();

                result.add(PhysicalCubeMappingImpl.builder()
                    .withName(cubeName)
                    .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.SALES_FACT_1997_TABLE).build())
                    .withDimensionConnectors(List.of(
                    	DimensionConnectorMappingImpl.builder()
                    		.withOverrideDimensionName("Time")
                    		.withDimension((DimensionMappingImpl) look(FoodmartMappingSupplier.DIMENSION_TIME))
                            .withForeignKey(FoodmartMappingSupplier.TIME_ID_COLUMN_IN_SALES_FACT_1997)
                            .build(),
                        DimensionConnectorMappingImpl.builder()
                        	.withOverrideDimensionName("Alternative Promotion")
                        	.withForeignKey(FoodmartMappingSupplier.PROMOTION_ID_COLUMN_IN_SALES_FACT_1997)
                        	.withDimension(StandardDimensionMappingImpl.builder()
                            .withName("Alternative Promotion")
                            .withHierarchies(List.of(
                                ExplicitHierarchyMappingImpl.builder()
                                    .withHasAll(true)
                                    .withPrimaryKey(id)
                                    .withQuery(InlineTableQueryMappingImpl.builder()
                                        .withAlias("inline_promo")
                                        .withTable(tt)
                                        .build()
                                    )
                                    .withLevels(List.of(
                                        LevelMappingImpl.builder()
                                            .withName("Alternative Promotion")
                                            .withColumn(id)
                                            .withNameColumn(date)
                                            .withUniqueMembers(true)
                                            .build()
                                    ))
                                    .build()
                            ))
                            .build()
                        ).build()
                    ))
                   .withMeasureGroups(List.of(
                		   MeasureGroupMappingImpl.builder()
                		   .withMeasures(List.of(
                               SumMeasureMappingImpl.builder()
                                   .withName("Unit Sales")
                                   .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_SALES_FACT_1997)
                                   .withFormatString("Standard")
                                   .withVisible(false)
                                   .build(),
                               SumMeasureMappingImpl.builder()
                                   .withName("Store Sales")
                                   .withColumn(FoodmartMappingSupplier.STORE_SALES_COLUMN_IN_SALES_FACT_1997)
                                   .withFormatString("#,###.00")
                                   .build()
                			))
                		   .build()
                	))
                    .build());
                return result;

            }
        }
        */

        // With grouping sets, mondrian will join fact table to the inline
        // dimension table, them sum to compute the 'all' value. That semi-joins
        // away too many fact table rows, and the 'all' value comes out too low
        // (zero, in fact). It causes a test exception, but is valid mondrian
        // behavior. (Behavior is unspecified if schema does not have
        // referential integrity.)
        if (context.getConfigValue(ConfigConstants.ENABLE_GROUPING_SETS, ConfigConstants.ENABLE_GROUPING_SETS_DEFAULT_VALUE, Boolean.class)) {
            return;
        }
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Alternative Promotion].Members} ON COLUMNS\n"
            + "from [" + cubeName + "] ").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Alternative Promotion].[Alternative Promotion].[All Alternative Promotions]}\n"
            + "{[Alternative Promotion].[Alternative Promotion].[2008-04-29]}\n"
            + "{[Alternative Promotion].[Alternative Promotion].[2007-01-20]}\n"
            + "Row #0: 266,773\n"
            + "Row #0: \n"
            + "Row #0: \n");
    }

    /**
     * EMF version of TestInlineTableDateModifier
     * Creates a test cube with inline table dimension containing date columns
     */
    public static class TestInlineTableDateModifierEmf implements CatalogMappingSupplier {

        private CatalogImpl catalog;

        public TestInlineTableDateModifierEmf(Catalog cat) {
            // Copy catalog using EcoreUtil
            catalog = EmfUtil.copy((CatalogImpl) cat);

            // Create columns for inline promo table
            Column idColumn = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
            idColumn.setName("id");
            idColumn.setType(SQLSimpleTypes.Sql99.integerType());

            Column dateColumn = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
            dateColumn.setName("date");
            dateColumn.setType(SQLSimpleTypes.Sql99.dateType());

            // Create inline table
            InlineTable inlinePromoTable = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createInlineTable();
            inlinePromoTable.setExtent(RelationalFactory.eINSTANCE.createRowSet());
            inlinePromoTable.setName("inline_promo");
            inlinePromoTable.getFeature().add(idColumn);
            inlinePromoTable.getFeature().add(dateColumn);

            // Create first row: id=1, date=2008-04-29
            DataSlot row1Col1 = InstanceFactory.eINSTANCE.createDataSlot();
            row1Col1.setFeature(idColumn);
            row1Col1.setDataValue("1");

            DataSlot row1Col2 = InstanceFactory.eINSTANCE.createDataSlot();
            row1Col2.setFeature(dateColumn);
            row1Col2.setDataValue("2008-04-29");

            Row row1 = RelationalFactory.eINSTANCE.createRow();
            row1.getSlot().add(row1Col1);
            row1.getSlot().add(row1Col2);

            // Create second row: id=2, date=2007-01-20
            DataSlot row2Col1 = InstanceFactory.eINSTANCE.createDataSlot();
            row2Col1.setFeature(idColumn);
            row2Col1.setDataValue("2");

            DataSlot row2Col2 = InstanceFactory.eINSTANCE.createDataSlot();
            row2Col2.setFeature(dateColumn);
            row2Col2.setDataValue("2007-01-20");

            Row row2 = RelationalFactory.eINSTANCE.createRow();
            row2.getSlot().add(row2Col1);
            row2.getSlot().add(row2Col2);

            inlinePromoTable.getExtent().getOwnedElement().add(row1);
            inlinePromoTable.getExtent().getOwnedElement().add(row2);

            // Create inline table query
            InlineTableSource inlineTableQuery = SourceFactory.eINSTANCE.createInlineTableSource();
            inlineTableQuery.setAlias("inline_promo");
            inlineTableQuery.setTable(inlinePromoTable);

            // Create level for Alternative Promotion
            Level altPromoLevel = LevelFactory.eINSTANCE.createLevel();
            altPromoLevel.setName("Alternative Promotion");
            altPromoLevel.setColumn(idColumn);
            altPromoLevel.setNameColumn(dateColumn);
            altPromoLevel.setUniqueMembers(true);

            // Create hierarchy
            ExplicitHierarchy altPromoHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
            altPromoHierarchy.setHasAll(true);
            altPromoHierarchy.setPrimaryKey(idColumn);
            altPromoHierarchy.setSource(inlineTableQuery);
            altPromoHierarchy.getLevels().add(altPromoLevel);

            // Create dimension
            StandardDimension altPromoDimension = DimensionFactory.eINSTANCE.createStandardDimension();
            altPromoDimension.setName("Alternative Promotion");
            altPromoDimension.getHierarchies().add(altPromoHierarchy);

            // Create cube
            PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
            cube.setName("Sales_Inline_Date");

            // Set up query
            TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
            tableQuery.setTable(CatalogSupplier.TABLE_SALES_FACT);
            cube.setSource(tableQuery);

            // Create dimension connector for Time
            DimensionConnector timeDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
            timeDimConnector.setOverrideDimensionName("Time");
            timeDimConnector.setForeignKey(CatalogSupplier.COLUMN_TIME_ID_SALESFACT);
            timeDimConnector.setDimension(CatalogSupplier.DIMENSION_TIME);

            // Create dimension connector for Alternative Promotion
            DimensionConnector altPromoDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
            altPromoDimConnector.setOverrideDimensionName("Alternative Promotion");
            altPromoDimConnector.setForeignKey(CatalogSupplier.COLUMN_PROMOTION_ID_SALESFACT);
            altPromoDimConnector.setDimension(altPromoDimension);

            cube.getDimensionConnectors().add(timeDimConnector);
            cube.getDimensionConnectors().add(altPromoDimConnector);

            // Create measures
            SumMeasure unitSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
            unitSalesMeasure.setName("Unit Sales");
            unitSalesMeasure.setColumn(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT);
            unitSalesMeasure.setFormatString("Standard");
            unitSalesMeasure.setVisible(false);

            SumMeasure storeSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
            storeSalesMeasure.setName("Store Sales");
            storeSalesMeasure.setColumn(CatalogSupplier.COLUMN_STORE_SALES_SALESFACT);
            storeSalesMeasure.setFormatString("#,###.00");

            // Create measure group
            MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
            measureGroup.getMeasures().add(unitSalesMeasure);
            measureGroup.getMeasures().add(storeSalesMeasure);

            cube.getMeasureGroups().add(measureGroup);

            // Add the new cube to the catalog
            catalog.getImportedElement().add(cube);
        }

        @Override
        public Catalog get() {
            return catalog;
        }
    }
}
