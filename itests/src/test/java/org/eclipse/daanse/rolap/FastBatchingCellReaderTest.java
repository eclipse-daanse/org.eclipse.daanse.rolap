/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2021 Hitachi Vantara..  All rights reserved.
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

import static org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct.getDatabaseProduct;
import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.eclipse.daanse.olap.api.cache.CacheCommand;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.util.Bug;
import org.eclipse.daanse.rolap.aggregator.MaxAggregator;
import org.eclipse.daanse.rolap.aggregator.MinAggregator;
import org.eclipse.daanse.rolap.aggregator.SumAggregator;
import org.eclipse.daanse.rolap.common.agg.AggregationKey;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.agg.Segment;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager;
import org.eclipse.daanse.rolap.common.agg.SegmentWithData;
import org.eclipse.daanse.rolap.common.result.BatchLoader;
import org.eclipse.daanse.rolap.common.result.FastBatchingCellReader;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ExpressionColumn;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.SqlStatement;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.CountMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.SumMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.Dimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.assertions.CellRequestFixture;
import org.eclipse.daanse.rolap.testkit.assertions.Mdx;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.util.DelegatingInvocationHandler;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;
/**
 * Test for <code>FastBatchingCellReader</code>.
 *
 * @author Thiyagu
 * @since 24-May-2007
 */
@RolapContextTest(FoodmartTestInstance.class)
class FastBatchingCellReaderTest extends BatchTestCase {

    private ExecutionContext executionContext;
    private ExecutionImpl e;
    private AggregationManager aggMgr;
    private RolapCube salesCube;
    private Connection connection;

    @BeforeEach
    public void beforeEach() {

    }

    @AfterEach
    public void afterEach() {
        // Note: ExecutionContext.pop() removed
        // cleanup
        connection.close();
        connection = null;
        e = null;
        aggMgr = null;
        executionContext = null;
        salesCube = null;
    }

    private void prepareContext(Context<?> context) {
        connection = context.getConnectionWithDefaultRole();
        connection.getCacheControl(null).flushSchemaCache();
        final Statement statement = ((Connection) connection).getInternalStatement();
        e = new ExecutionImpl(statement, Optional.empty());
        AbstractBasicContext<?> abc = (AbstractBasicContext) e.getDaanseStatement().getDaanseConnection().getContext();
        aggMgr = (AggregationManager) abc.getAggregationManager();
        executionContext = e.asContext();
        // Note: ExecutionContext.push() removed. Tests should wrap operations in
        // ExecutionContext.where() if needed.
        salesCube = (RolapCube) connection.getCatalogReader().withLocus().getCubes().get(0);
    }

    private BatchLoader createFbcr(Boolean useGroupingSets, RolapCube cube) {
        Dialect dialect = cube.getStar().getDialect();
        if (useGroupingSets != null) {
            dialect = dialectWithGroupingSets(dialect, useGroupingSets);
        }
        return new BatchLoader(ExecutionContext.current(), aggMgr.getCacheMgr(),
            org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities.of(dialect), cube);
    }

    private Dialect dialectWithGroupingSets(final Dialect dialect, final boolean supportsGroupingSets) {
        return (Dialect) Proxy.newProxyInstance(Dialect.class.getClassLoader(), new Class[] { Dialect.class },
                new MyDelegatingInvocationHandler(dialect, supportsGroupingSets));
    }

    @Test
    void testMissingSubtotalBugMetricFilter(Context<?> context) {
        prepareContext(context);
        assertThatQuery(context.getConnectionWithDefaultRole(), "With " + "Set [*NATIVE_CJ_SET] as "
                + "'NonEmptyCrossJoin({[Time].[Year].[1997]},"
                + "                   NonEmptyCrossJoin({[Product].[All Products].[Drink]},{[Education Level].[All Education Levels].[Bachelors Degree]}))' "
                + "Set [*METRIC_CJ_SET] as 'Filter([*NATIVE_CJ_SET],[Measures].[*Unit Sales_SEL~SUM] > 1000.0)' "
                + "Set [*METRIC_MEMBERS_Education Level] as 'Generate([*METRIC_CJ_SET], {[Education Level].CurrentMember})' "
                + "Member [Measures].[*Unit Sales_SEL~SUM] as '([Measures].[Unit Sales],[Time].[Time].CurrentMember,[Product].CurrentMember,[Education Level].CurrentMember)', SOLVE_ORDER=200 "
                + "Member [Education Level].[*CTX_MEMBER_SEL~SUM] as 'Sum(Filter([*METRIC_MEMBERS_Education Level],[Measures].[*Unit Sales_SEL~SUM] > 1000.0))', SOLVE_ORDER=-102 "
                + "Select " + "{[Measures].[Unit Sales]} on columns, "
                + "Non Empty Union(CrossJoin(Generate([*METRIC_CJ_SET], {([Time].[Time].CurrentMember,[Product].CurrentMember)}),{[Education Level].[*CTX_MEMBER_SEL~SUM]}),"
                + "                Generate([*METRIC_CJ_SET], {([Time].[Time].CurrentMember,[Product].CurrentMember,[Education Level].CurrentMember)})) on rows "
                + "From [Sales]").returnsGrid(
                "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Measures].[Unit Sales]}\n" + "Axis #2:\n"
                        + "{[Time].[Time].[1997], [Product].[Product].[Drink], [Education Level].[Education Level].[*CTX_MEMBER_SEL~SUM]}\n"
                        + "{[Time].[Time].[1997], [Product].[Product].[Drink], [Education Level].[Education Level].[Bachelors Degree]}\n"
                        + "Row #0: 6,423\n" + "Row #1: 6,423\n");
    }

    @Test
    void testMissingSubtotalBugMultiLevelMetricFilter(Context<?> context) {
        prepareContext(context);
        assertThatQuery(context.getConnectionWithDefaultRole(), "With "
                + "Set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Product],[*BASE_MEMBERS_Education Level])' "
                + "Set [*METRIC_CJ_SET] as 'Filter([*NATIVE_CJ_SET],[Measures].[*Store Cost_SEL~SUM] > 1000.0)' "
                + "Set [*BASE_MEMBERS_Product] as '{[Product].[All Products].[Drink].[Beverages],[Product].[All Products].[Food].[Baked Goods]}' "
                + "Set [*METRIC_MEMBERS_Product] as 'Generate([*METRIC_CJ_SET], {[Product].CurrentMember})' "
                + "Set [*BASE_MEMBERS_Education Level] as '{[Education Level].[All Education Levels].[High School Degree],[Education Level].[All Education Levels].[Partial High School]}' "
                + "Set [*METRIC_MEMBERS_Education Level] as 'Generate([*METRIC_CJ_SET], {[Education Level].CurrentMember})' "
                + "Member [Measures].[*Store Cost_SEL~SUM] as '([Measures].[Store Cost],[Product].CurrentMember,[Education Level].CurrentMember)', SOLVE_ORDER=200 "
                + "Member [Product].[Drink].[*CTX_MEMBER_SEL~SUM] as 'Sum(Filter([*METRIC_MEMBERS_Product],[Product].CurrentMember.Parent = [Product].[All Products].[Drink]))', SOLVE_ORDER=-100 "
                + "Member [Product].[Food].[*CTX_MEMBER_SEL~SUM] as 'Sum(Filter([*METRIC_MEMBERS_Product],[Product].CurrentMember.Parent = [Product].[All Products].[Food]))', SOLVE_ORDER=-100 "
                + "Member [Education Level].[*CTX_MEMBER_SEL~SUM] as 'Sum(Filter([*METRIC_MEMBERS_Education Level],[Measures].[*Store Cost_SEL~SUM] > 1000.0))', SOLVE_ORDER=-101 "
                + "Select " + "{[Measures].[Store Cost]} on columns, "
                + "NonEmptyCrossJoin({[Product].[Drink].[*CTX_MEMBER_SEL~SUM],[Product].[Food].[*CTX_MEMBER_SEL~SUM]},{[Education Level].[*CTX_MEMBER_SEL~SUM]}) "
                + "on rows From [Sales]").returnsGrid(
                "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Measures].[Store Cost]}\n" + "Axis #2:\n"
                        + "{[Product].[Product].[Drink].[*CTX_MEMBER_SEL~SUM], [Education Level].[Education Level].[*CTX_MEMBER_SEL~SUM]}\n"
                        + "{[Product].[Product].[Food].[*CTX_MEMBER_SEL~SUM], [Education Level].[Education Level].[*CTX_MEMBER_SEL~SUM]}\n"
                        + "Row #0: 6,535.30\n" + "Row #1: 3,860.89\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "true", type = Boolean.class)
    void testShouldUseGroupingFunctionOnPropertyTrueAndOnSupportedDB(Context<?> context) {
        context.getCatalogCache().clear();
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            BatchLoader fbcr = createFbcr(true, salesCube);
            assertTrue(fbcr.shouldUseGroupingFunction());
        });
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "true", type = Boolean.class)
    void testShouldUseGroupingFunctionOnPropertyTrueAndOnNonSupportedDB(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            BatchLoader fbcr = createFbcr(false, salesCube);
            assertFalse(fbcr.shouldUseGroupingFunction());
        });
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "false", type = Boolean.class)
    void testShouldUseGroupingFunctionOnPropertyFalseOnSupportedDB(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            BatchLoader fbcr = createFbcr(true, salesCube);
            assertFalse(fbcr.shouldUseGroupingFunction());
        });
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "false", type = Boolean.class)
    void testShouldUseGroupingFunctionOnPropertyFalseOnNonSupportedDB(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            BatchLoader fbcr = createFbcr(false, salesCube);
            assertFalse(fbcr.shouldUseGroupingFunction());
        });
    }

    @Test
    void testDoesDBSupportGroupingSets(Context<?> context) {
        prepareContext(context);
        final Dialect dialect = getDialect(context.getConnectionWithDefaultRole());
        FastBatchingCellReader fbcr = new FastBatchingCellReader(e, salesCube, aggMgr) {
            @Override
            public Dialect getDialect() {
                return dialect;
            }
        };
        // The list below is a product enumeration; DatabaseProduct has no DUCKDB constant, so
        // DuckDB lands in the default branch and is asserted not to support grouping sets --
        // while DuckDbDialect is the one dialect that reports true. Ask the dialect by name
        // instead of maintaining a second, silently diverging list.
        boolean expected = switch (getDatabaseProduct(dialect.name())) {
        case ORACLE, TERADATA, DB2, DB2_AS400, DB2_OLD_AS400, GREENPLUM -> true;
        default -> "DUCKDB".equalsIgnoreCase(dialect.name());
        };
        assertEquals(expected, fbcr.getDialect().supportsGroupingSets(),
                "grouping-sets capability of " + dialect.name());
    }

    @Test
    void testGroupBatchesForNonGroupableBatchesWithSorting(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch genderBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "gender", "F").build());
            BatchLoader.Batch maritalStatusBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "marital_status", "M").build());
            ArrayList<BatchLoader.Batch> batchList = new ArrayList<>();
            batchList.add(genderBatch);
            batchList.add(maritalStatusBatch);
            List<BatchLoader.CompositeBatch> groupedBatches = BatchLoader.groupBatches(batchList);
            assertEquals(batchList.size(), groupedBatches.size());
            assertEquals(genderBatch, groupedBatches.get(0).detailedBatch);
            assertEquals(maritalStatusBatch, groupedBatches.get(1).detailedBatch);
        });
    }

    @Test
    void testGroupBatchesForNonGroupableBatchesWithConstraints(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            List<String[]> compoundMembers = new ArrayList<>();
            compoundMembers.add(new String[] { "USA", "CA" });
            compoundMembers.add(new String[] { "Canada", "BC" });
            CellRequestFixture.Constraint constraint =
                CellRequestFixture.Constraint.countryState(compoundMembers.toArray(new String[0][]));
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch genderBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales)
                            .where("customer", "gender", "F").constrain(constraint).build());
            BatchLoader.Batch maritalStatusBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales)
                            .where("customer", "marital_status", "M").constrain(constraint).build());
            ArrayList<BatchLoader.Batch> batchList = new ArrayList<>();
            batchList.add(genderBatch);
            batchList.add(maritalStatusBatch);
            List<BatchLoader.CompositeBatch> groupedBatches = BatchLoader.groupBatches(batchList);
            assertEquals(batchList.size(), groupedBatches.size());
            assertEquals(genderBatch, groupedBatches.get(0).detailedBatch);
            assertEquals(maritalStatusBatch, groupedBatches.get(1).detailedBatch);
        });
    }

    @Test
    void testGroupBatchesForGroupableBatches(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch genderBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "gender", "F").build()) {
                @Override
                public boolean canBatch(BatchLoader.Batch other) {
                    return false;
                }
            };
            BatchLoader.Batch superBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales)
                            .build()) {
                @Override
                public boolean canBatch(BatchLoader.Batch batch) {
                    return true;
                }
            };
            ArrayList<BatchLoader.Batch> batchList = new ArrayList<>();
            batchList.add(genderBatch);
            batchList.add(superBatch);
            List<BatchLoader.CompositeBatch> groupedBatches = BatchLoader.groupBatches(batchList);
            assertEquals(1, groupedBatches.size());
            assertEquals(superBatch, groupedBatches.get(0).detailedBatch);
            assertTrue(groupedBatches.get(0).summaryBatches.contains(genderBatch));
        });
    }

    @Test
    void testGroupBatchesForGroupableBatchesAndNonGroupableBatches(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            final BatchLoader.Batch group1Agg2 = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "gender", "F").build()) {
                @Override
                public boolean canBatch(BatchLoader.Batch batch) {
                    return false;
                }
            };
            final BatchLoader.Batch group1Agg1 = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "country", "F").build()) {
                @Override
                public boolean canBatch(BatchLoader.Batch batch) {
                    return batch.equals(group1Agg2);
                }
            };
            BatchLoader.Batch group1Detailed = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales)
                            .build()) {
                @Override
                public boolean canBatch(BatchLoader.Batch batch) {
                    return batch.equals(group1Agg1);
                }
            };

            final BatchLoader.Batch group2Agg1 = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "education", "F").build()) {
                @Override
                public boolean canBatch(BatchLoader.Batch batch) {
                    return false;
                }
            };
            BatchLoader.Batch group2Detailed = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "yearly_income", "").build()) {
                @Override
                public boolean canBatch(BatchLoader.Batch batch) {
                    return batch.equals(group2Agg1);
                }
            };
            ArrayList<BatchLoader.Batch> batchList = new ArrayList<>();
            batchList.add(group1Agg1);
            batchList.add(group1Agg2);
            batchList.add(group1Detailed);
            batchList.add(group2Agg1);
            batchList.add(group2Detailed);
            List<BatchLoader.CompositeBatch> groupedBatches = BatchLoader.groupBatches(batchList);
            assertEquals(2, groupedBatches.size());
            assertEquals(group1Detailed, groupedBatches.get(0).detailedBatch);
            assertTrue(groupedBatches.get(0).summaryBatches.contains(group1Agg1));
            assertTrue(groupedBatches.get(0).summaryBatches.contains(group1Agg2));
            assertEquals(group2Detailed, groupedBatches.get(1).detailedBatch);
            assertTrue(groupedBatches.get(1).summaryBatches.contains(group2Agg1));
        });
    }

    @Test
    void testGroupBatchesForTwoSetOfGroupableBatches(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            String[] fieldValuesStoreType = { "Deluxe Supermarket", "Gourmet Supermarket", "HeadQuarters",
                    "Mid-Size Grocery", "Small Grocery", "Supermarket" };
            String fieldStoreType = "store_type";
            String tableStore = "store";

            String[] fieldValuesWarehouseCountry = { "Canada", "Mexico", "USA" };
            String fieldWarehouseCountry = "warehouse_country";
            String tableWarehouse = "warehouse";

            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch batch1RollupOnGender = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableStore, fieldStoreType, fieldValuesStoreType)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .build();

            BatchLoader.Batch batch1RollupOnGenderAndProductDepartment = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .build();

            BatchLoader.Batch batch1RollupOnStoreTypeAndProductDepartment = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableCustomer, fieldGender, fieldValuesGender)
                    .build();

            BatchLoader.Batch batch1Detailed = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableStore, fieldStoreType, fieldValuesStoreType)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableCustomer, fieldGender, fieldValuesGender)
                    .build();

            String warehouseCube = "Warehouse";
            String measure2 = "[Measures].[Warehouse Sales]";
            BatchLoader.Batch batch2RollupOnStoreType = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(warehouseCube).measure(measure2)
                    .where(tableWarehouse, fieldWarehouseCountry, fieldValuesWarehouseCountry)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .build();

            BatchLoader.Batch batch2RollupOnStoreTypeAndWareHouseCountry = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(warehouseCube).measure(measure2)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .build();

            BatchLoader.Batch batch2RollupOnProductFamilyAndWareHouseCountry = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(warehouseCube).measure(measure2)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableStore, fieldStoreType, fieldValuesStoreType)
                    .build();

            BatchLoader.Batch batch2Detailed = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(warehouseCube).measure(measure2)
                    .where(tableWarehouse, fieldWarehouseCountry, fieldValuesWarehouseCountry)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableStore, fieldStoreType, fieldValuesStoreType)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .build();

            List<BatchLoader.Batch> batchList = new ArrayList<>();

            batchList.add(batch1RollupOnGender);
            batchList.add(batch2RollupOnStoreType);
            batchList.add(batch2RollupOnStoreTypeAndWareHouseCountry);
            batchList.add(batch2RollupOnProductFamilyAndWareHouseCountry);
            batchList.add(batch1RollupOnGenderAndProductDepartment);
            batchList.add(batch1RollupOnStoreTypeAndProductDepartment);
            batchList.add(batch2Detailed);
            batchList.add(batch1Detailed);
            List<BatchLoader.CompositeBatch> groupedBatches = fbcr.groupBatches(batchList);
            final int groupedBatchCount = groupedBatches.size();

            // Until MONDRIAN-1001 is fixed, behavior is flaky due to interaction
            // with previous tests.
            if (Bug.Bug1001Fixed) {
                if (context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE,
                        Boolean.class)
                        && context.getConfigValue(ConfigConstants.READ_AGGREGATES,
                                ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE, Boolean.class)) {
                    assertEquals(4, groupedBatchCount);
                } else {
                    assertEquals(2, groupedBatchCount);
                }
            } else {
                assertTrue(groupedBatchCount == 2 || groupedBatchCount == 4);
            }
        });
    }

    @Test
    void testAddToCompositeBatchForBothBatchesNotPartOfCompositeBatch(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch batch1 = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "country", "F").build());
            BatchLoader.Batch batch2 = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "gender", "F").build());
            Map<AggregationKey, BatchLoader.CompositeBatch> batchGroups = new HashMap<>();
            fbcr.addToCompositeBatch(batchGroups, batch1, batch2);
            assertEquals(1, batchGroups.size());
            BatchLoader.CompositeBatch compositeBatch = batchGroups.get(batch1.batchKey);
            assertEquals(batch1, compositeBatch.detailedBatch);
            assertEquals(1, compositeBatch.summaryBatches.size());
            assertTrue(compositeBatch.summaryBatches.contains(batch2));
        });
    }

    @Test
    void testAddToCompositeBatchForDetailedBatchAlreadyPartOfACompositeBatch(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            prepareContext(context);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch detailedBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "country", "F").build());
            BatchLoader.Batch aggBatch1 = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "gender", "F").build());
            BatchLoader.Batch aggBatchAlreadyInComposite = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "gender", "F").build());
            Map<AggregationKey, BatchLoader.CompositeBatch> batchGroups = new HashMap<>();
            BatchLoader.CompositeBatch existingCompositeBatch = new BatchLoader.CompositeBatch(detailedBatch);
            existingCompositeBatch.add(aggBatchAlreadyInComposite);
            batchGroups.put(detailedBatch.batchKey, existingCompositeBatch);

            BatchLoader.addToCompositeBatch(batchGroups, detailedBatch, aggBatch1);

            assertEquals(1, batchGroups.size());
            BatchLoader.CompositeBatch compositeBatch = batchGroups.get(detailedBatch.batchKey);
            assertEquals(detailedBatch, compositeBatch.detailedBatch);
            assertEquals(2, compositeBatch.summaryBatches.size());
            assertTrue(compositeBatch.summaryBatches.contains(aggBatch1));
            assertTrue(compositeBatch.summaryBatches.contains(aggBatchAlreadyInComposite));
        });
    }

    @Test
    void testAddToCompositeBatchForAggregationBatchAlreadyPartOfACompositeBatch(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch detailedBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "country", "F").build());
            BatchLoader.Batch aggBatchToAddToDetailedBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "gender", "F").build());
            BatchLoader.Batch aggBatchAlreadyInComposite = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "city", "F").build());
            Map<AggregationKey, BatchLoader.CompositeBatch> batchGroups = new HashMap<>();
            BatchLoader.CompositeBatch existingCompositeBatch = new BatchLoader.CompositeBatch(
                    aggBatchToAddToDetailedBatch);
            existingCompositeBatch.add(aggBatchAlreadyInComposite);
            batchGroups.put(aggBatchToAddToDetailedBatch.batchKey, existingCompositeBatch);

            fbcr.addToCompositeBatch(batchGroups, detailedBatch, aggBatchToAddToDetailedBatch);

            assertEquals(1, batchGroups.size());
            BatchLoader.CompositeBatch compositeBatch = batchGroups.get(detailedBatch.batchKey);
            assertEquals(detailedBatch, compositeBatch.detailedBatch);
            assertEquals(2, compositeBatch.summaryBatches.size());
            assertTrue(compositeBatch.summaryBatches.contains(aggBatchToAddToDetailedBatch));
            assertTrue(compositeBatch.summaryBatches.contains(aggBatchAlreadyInComposite));
        });
    }

    @Test
    void testAddToCompositeBatchForBothBatchAlreadyPartOfACompositeBatch(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch detailedBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "country", "F").build());
            BatchLoader.Batch aggBatchToAddToDetailedBatch = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "gender", "F").build());
            BatchLoader.Batch aggBatchAlreadyInCompositeOfAgg = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "city", "F").build());
            BatchLoader.Batch aggBatchAlreadyInCompositeOfDetail = fbcr.new Batch(
                    CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where("customer", "state_province", "F").build());

            Map<AggregationKey, BatchLoader.CompositeBatch> batchGroups = new HashMap<>();
            BatchLoader.CompositeBatch existingAggCompositeBatch = new BatchLoader.CompositeBatch(
                    aggBatchToAddToDetailedBatch);
            existingAggCompositeBatch.add(aggBatchAlreadyInCompositeOfAgg);
            batchGroups.put(aggBatchToAddToDetailedBatch.batchKey, existingAggCompositeBatch);

            BatchLoader.CompositeBatch existingCompositeBatch = new BatchLoader.CompositeBatch(detailedBatch);
            existingCompositeBatch.add(aggBatchAlreadyInCompositeOfDetail);
            batchGroups.put(detailedBatch.batchKey, existingCompositeBatch);

            BatchLoader.addToCompositeBatch(batchGroups, detailedBatch, aggBatchToAddToDetailedBatch);

            assertEquals(1, batchGroups.size());
            BatchLoader.CompositeBatch compositeBatch = batchGroups.get(detailedBatch.batchKey);
            assertEquals(detailedBatch, compositeBatch.detailedBatch);
            assertEquals(3, compositeBatch.summaryBatches.size());
            assertTrue(compositeBatch.summaryBatches.contains(aggBatchToAddToDetailedBatch));
            assertTrue(compositeBatch.summaryBatches.contains(aggBatchAlreadyInCompositeOfAgg));
            assertTrue(compositeBatch.summaryBatches.contains(aggBatchAlreadyInCompositeOfDetail));
        });
    }

    /**
     * Tests that can batch for batch with super set of contraint column bit key and
     * all values for additional condition.
     */
    @Test
    void testCanBatchForSuperSet(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch aggregationBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .build();

            BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .where(tableCustomer, fieldGender, fieldValuesGender)
                    .build();

            assertTrue(detailedBatch.canBatch(aggregationBatch));
            assertFalse(aggregationBatch.canBatch(detailedBatch));
        });
    }

    @Test
    void testCanBatchForBatchWithConstraint(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            List<String[]> compoundMembers = new ArrayList<>();
            compoundMembers.add(new String[] { "USA", "CA" });
            compoundMembers.add(new String[] { "Canada", "BC" });
            CellRequestFixture.Constraint constraint =
                CellRequestFixture.Constraint.countryState(compoundMembers.toArray(new String[0][]));
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch aggregationBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .constrain(constraint)
                    .build();

            BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .where(tableCustomer, fieldGender, fieldValuesGender)
                    .constrain(constraint)
                    .build();

            assertTrue(detailedBatch.canBatch(aggregationBatch));
            assertFalse(aggregationBatch.canBatch(detailedBatch));
        });
    }

    @Test
    void testCanBatchForBatchWithConstraint2(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            List<String[]> compoundMembers1 = new ArrayList<>();
            compoundMembers1.add(new String[] { "USA", "CA" });
            compoundMembers1.add(new String[] { "Canada", "BC" });
            CellRequestFixture.Constraint constraint1 =
                CellRequestFixture.Constraint.countryState(compoundMembers1.toArray(new String[0][]));

            // Different constraint will cause the Batch not to match.
            List<String[]> compoundMembers2 = new ArrayList<>();
            compoundMembers2.add(new String[] { "USA", "CA" });
            compoundMembers2.add(new String[] { "USA", "OR" });
            CellRequestFixture.Constraint constraint2 =
                CellRequestFixture.Constraint.countryState(compoundMembers2.toArray(new String[0][]));

            BatchLoader.Batch aggregationBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .constrain(constraint1)
                    .build();

            BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .where(tableCustomer, fieldGender, fieldValuesGender)
                    .constrain(constraint2)
                    .build();

            assertTrue(detailedBatch.canBatch(aggregationBatch));
            assertFalse(aggregationBatch.canBatch(detailedBatch));
        });
    }

    @Test
    void testCanBatchForBatchWithDistinctCountInDetailedBatch(Context<?> context) {
        prepareContext(context);
        if (!context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE,
                Boolean.class)
                || !context.getConfigValue(ConfigConstants.READ_AGGREGATES,
                        ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE, Boolean.class)) {
            return;
        }
        final BatchLoader fbcr = createFbcr(null, salesCube);
        Connection connection = context.getConnectionWithDefaultRole();
        BatchLoader.Batch aggregationBatch = CellRequestFixture.of(connection).batch(fbcr)
                .cube(cubeNameSales).measure(measureUnitSales)
                .where(tableTime, fieldYear, fieldValuesYear)
                .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                .build();

        BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                .cube(cubeNameSales).measure("[Measures].[Customer Count]")
                .where(tableTime, fieldYear, fieldValuesYear)
                .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                .build();

        assertFalse(detailedBatch.canBatch(aggregationBatch));
        assertFalse(aggregationBatch.canBatch(detailedBatch));
    }

    @Test
    void testCanBatchForBatchWithDistinctCountInAggregateBatch(Context<?> context) {
        prepareContext(context);
        if (!context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE,
                Boolean.class)
                || !context.getConfigValue(ConfigConstants.READ_AGGREGATES,
                        ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE, Boolean.class)) {
            return;
        }
        final BatchLoader fbcr = createFbcr(null, salesCube);
        Connection connection = context.getConnectionWithDefaultRole();
        BatchLoader.Batch aggregationBatch = CellRequestFixture.of(connection).batch(fbcr)
                .cube(cubeNameSales).measure("[Measures].[Customer Count]")
                .where(tableTime, fieldYear, fieldValuesYear)
                .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                .build();

        BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                .cube(cubeNameSales).measure(measureUnitSales)
                .where(tableTime, fieldYear, fieldValuesYear)
                .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                .build();

        assertFalse(detailedBatch.canBatch(aggregationBatch));
        assertFalse(aggregationBatch.canBatch(detailedBatch));
    }

    @Test
    void testCanBatchSummaryBatchWithDetailedBatchWithDistinctCount(Context<?> context) {
        prepareContext(context);
        if (context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE,
                Boolean.class)
                || context.getConfigValue(ConfigConstants.READ_AGGREGATES,
                        ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE, Boolean.class)) {
            return;
        }
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch aggregationBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure("[Measures].[Customer Count]")
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .build();

            BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .build();

            assertFalse(detailedBatch.canBatch(aggregationBatch));
            assertFalse(aggregationBatch.canBatch(detailedBatch));
        });
    }

    /**
     * Test that can batch for batch with non superset of constraint column bit key
     * and all values for additional condition.
     */
    @Test
    void testNonSuperSet(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch aggregationBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .build();

            BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .where(tableCustomer, fieldGender, fieldValuesGender)
                    .build();

            assertFalse(detailedBatch.canBatch(aggregationBatch));
            assertFalse(aggregationBatch.canBatch(detailedBatch));
        });
    }

    /**
     * Tests that can batch for batch with super set of constraint column bit key
     * and NOT all values for additional condition.
     */
    @Test
    void testSuperSetAndNotAllValues(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch aggregationBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .build();

            BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .where(tableCustomer, fieldGender, new String[] { "M" })
                    .build();

            assertFalse(detailedBatch.canBatch(aggregationBatch));
            assertFalse(aggregationBatch.canBatch(detailedBatch));
        });
    }

    @Test
    void testCanBatchForBatchesFromSameAggregationButDifferentRollupOption(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch batch1 = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .build();

            BatchLoader.Batch batch2 = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableTime, "quarter", new String[] { "Q1", "Q2", "Q3", "Q4" })
                    .where(tableTime, "month_of_year",
                            new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12" })
                    .build();

            // Until MONDRIAN-1001 is fixed, behavior is flaky due to interaction
            // with previous tests.
            final boolean batch2CanBatch1 = batch2.canBatch(batch1);
            final boolean batch1CanBatch2 = batch1.canBatch(batch2);
            if (Bug.Bug1001Fixed) {
                if (context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE,
                        Boolean.class)
                        && context.getConfigValue(ConfigConstants.READ_AGGREGATES,
                                ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE, Boolean.class)) {
                    assertFalse(batch2CanBatch1);
                    assertFalse(batch1CanBatch2);
                } else {
                    assertTrue(batch2CanBatch1);
                }
            }
        });
    }

    /**
     * Tests that Can Batch For Batch With Super Set Of Constraint Column Bit Key
     * And Different Values For Overlapping Columns.
     */
    @Test
    void testSuperSetDifferentValues(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch aggregationBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, new String[] { "1997" })
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .build();

            BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, new String[] { "1998" })
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .where(tableCustomer, fieldGender, fieldValuesGender)
                    .build();

            assertFalse(detailedBatch.canBatch(aggregationBatch));
            assertFalse(aggregationBatch.canBatch(detailedBatch));
        });
    }

    @Test
    void testCanBatchForBatchWithDifferentAggregationTable(Context<?> context) {
        prepareContext(context);
        Connection connection = context.getConnectionWithDefaultRole();
        final Dialect dialect = getDialect(connection);
        final DatabaseProduct product = getDatabaseProduct(dialect.name());
        switch (product) {
        case TERADATA:
        case INFOBRIGHT:
        case NEOVIEW:
            // On Teradata, Infobright and Neoview we don't create aggregate
            // tables, so this test will fail.
            return;
        }

        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            BatchLoader.Batch summaryBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .build();

            BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableCustomer, fieldGender, fieldValuesGender)
                    .build();

            if (context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE,
                    Boolean.class)
                    && context.getConfigValue(ConfigConstants.READ_AGGREGATES,
                            ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE, Boolean.class)) {
                assertFalse(detailedBatch.canBatch(summaryBatch));
                assertFalse(summaryBatch.canBatch(detailedBatch));
            } else {
                assertTrue(detailedBatch.canBatch(summaryBatch));
                assertFalse(summaryBatch.canBatch(detailedBatch));
            }
        });
    }

    @Test
    void testCannotBatchTwoBatchesAtTheSameLevel(Context<?> context) {
        prepareContext(context);
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);
            Connection connection = context.getConnectionWithDefaultRole();
            BatchLoader.Batch firstBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure("[Measures].[Customer Count]")
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, new String[] { "Food" })
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .build();

            BatchLoader.Batch secondBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure("[Measures].[Customer Count]")
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, new String[] { "Drink" })
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .build();

            assertFalse(firstBatch.canBatch(secondBatch));
            assertFalse(secondBatch.canBatch(firstBatch));
        });
    }

    @Test
    void testCompositeBatchLoadAggregation(Context<?> context) throws Exception {
        prepareContext(context);
        Connection connection = context.getConnectionWithDefaultRole();
        if (!getDialect(connection).supportsGroupingSets()) {
            return;
        }
        // Every other dialect returns above: DuckDB is the only one whose supportsGroupingSets()
        // is true, so this body had never actually run, and unlike its neighbours it was never
        // wrapped -- createFbcr calls ExecutionContext.current().
        ExecutionContext.where(executionContext, () -> {
            final BatchLoader fbcr = createFbcr(null, salesCube);

            BatchLoader.Batch summaryBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .build();

            BatchLoader.Batch detailedBatch = CellRequestFixture.of(connection).batch(fbcr)
                    .cube(cubeNameSales).measure(measureUnitSales)
                    .where(tableTime, fieldYear, fieldValuesYear)
                    .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
                    .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
                    .where(tableCustomer, fieldGender, fieldValuesGender)
                    .build();

            final BatchLoader.CompositeBatch compositeBatch = new BatchLoader.CompositeBatch(detailedBatch);

            compositeBatch.add(summaryBatch);

            final List<Future<Map<Segment, SegmentWithData>>> segmentFutures = new ArrayList<>();

            AbstractBasicContext<?> abc = (AbstractBasicContext) context;
            ((SegmentCacheManager) (abc.getAggregationManager().getCacheMgr())).execute(new CacheCommand<Void>() {
                private final ExecutionContext executionContext = ExecutionContext.current();

                @Override
                public Void call() throws Exception {
                    compositeBatch.load(segmentFutures);
                    return null;
                }

                @Override
                public ExecutionContext getExecutionContext() {
                    return executionContext;
                }
            });

            assertEquals(1, segmentFutures.size());
            assertEquals(2, segmentFutures.get(0).get().size());
            // The order of the segments is not deterministic, so we need to
            // iterate over the segments and find a match for the batch.
            // If none are found, we fail.
            boolean found = false;
            for (Segment seg : segmentFutures.get(0).get().keySet()) {
                if (detailedBatch.getConstrainedColumnsBitKey().equals(seg.getConstrainedColumnsBitKey())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                fail("No bitkey match found.");
            }
            found = false;
            for (Segment seg : segmentFutures.get(0).get().keySet()) {
                if (summaryBatch.getConstrainedColumnsBitKey().equals(seg.getConstrainedColumnsBitKey())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                fail("No bitkey match found.");
            }
            return null;
        });
    }

    /**
     * Checks that in dialects that request it (e.g. LucidDB), distinct aggregates
     * based on SQL expressions, e.g.
     * <code>count(distinct "col1" + "col2"), count(distinct query)</code>, are
     * loaded individually, and separately from the other aggregates.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, TestLoadDistinctSqlMeasureModifierEmf.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testLoadDistinctSqlMeasure(Context<?> context) {
        prepareContext(context);
        // Some databases cannot handle scalar subqueries inside
        // count(distinct).
        Connection connection = context.getConnectionWithDefaultRole();
        final Dialect dialect = getDialect(connection);
        switch (getDatabaseProduct(dialect.name())) {
        case ORACLE:
            // Oracle gives 'feature not supported' in Express 10.2
        case ACCESS:
        case TERADATA:
            // Teradata gives "Syntax error: expected something between '(' and
            // the 'select' keyword." in 12.0.
        case NEOVIEW:
            // Neoview gives "ERROR[4008] A subquery is not allowed inside an
            // aggregate function."
        case NETEZZA:
            // Netezza gives an "ERROR: Correlated Subplan expressions not
            // supported"
        case GREENPLUM:
            // Greenplum says 'Does not support yet that query'
        case VERTICA:
            // Vertica says "Aggregate function calls cannot contain subqueries"
            return;
        }

        String query = "select " + "   [Store Type].Children on rows, "
                + "   {[Measures].[Count Distinct of Warehouses (Large Owned)],"
                + "    [Measures].[Count Distinct of Warehouses (Large Independent)],"
                + "    [Measures].[Count All of Warehouses (Large Independent)],"
                + "    [Measures].[Count Distinct Store+Warehouse]," + "    [Measures].[Count All Store+Warehouse],"
                + "    [Measures].[Store Count]} on columns " + "from [Warehouse2]";

        String desiredResult = "Axis #0:\n" + "{}\n" + "Axis #1:\n"
                + "{[Measures].[Count Distinct of Warehouses (Large Owned)]}\n"
                + "{[Measures].[Count Distinct of Warehouses (Large Independent)]}\n"
                + "{[Measures].[Count All of Warehouses (Large Independent)]}\n"
                + "{[Measures].[Count Distinct Store+Warehouse]}\n" + "{[Measures].[Count All Store+Warehouse]}\n"
                + "{[Measures].[Store Count]}\n" + "Axis #2:\n" + "{[Store Type].[Store Type].[Deluxe Supermarket]}\n"
                + "{[Store Type].[Store Type].[Gourmet Supermarket]}\n" + "{[Store Type].[Store Type].[HeadQuarters]}\n"
                + "{[Store Type].[Store Type].[Mid-Size Grocery]}\n" + "{[Store Type].[Store Type].[Small Grocery]}\n"
                + "{[Store Type].[Store Type].[Supermarket]}\n" + "Row #0: 1\n" + "Row #0: 0\n" + "Row #0: 0\n"
                + "Row #0: 6\n" + "Row #0: 6\n" + "Row #0: 6\n" + "Row #1: 1\n" + "Row #1: 0\n" + "Row #1: 0\n"
                + "Row #1: 2\n" + "Row #1: 2\n" + "Row #1: 2\n" + "Row #2: \n" + "Row #2: \n" + "Row #2: \n"
                + "Row #2: \n" + "Row #2: \n" + "Row #2: \n" + "Row #3: 0\n" + "Row #3: 1\n" + "Row #3: 1\n"
                + "Row #3: 4\n" + "Row #3: 4\n" + "Row #3: 4\n" + "Row #4: 0\n" + "Row #4: 1\n" + "Row #4: 1\n"
                + "Row #4: 4\n" + "Row #4: 4\n" + "Row #4: 4\n" + "Row #5: 0\n" + "Row #5: 1\n" + "Row #5: 3\n"
                + "Row #5: 8\n" + "Row #5: 8\n" + "Row #5: 8\n";

        assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( desiredResult);

        String loadCountDistinct_luciddb1 = "select " + "\"store\".\"store_type\" as \"c0\", " + "count(distinct "
                + "(select \"warehouse_class\".\"warehouse_class_id\" AS \"warehouse_class_id\" "
                + "from \"warehouse_class\" AS \"warehouse_class\" "
                + "where \"warehouse_class\".\"warehouse_class_id\" = \"warehouse\".\"warehouse_class_id\" and \"warehouse_class\".\"description\" = 'Large Owned')) as \"m0\" "
                + "from \"warehouse\" as \"warehouse\" join \"store\" as \"store\" "
                + "on \"warehouse\".\"stores_id\" = \"store\".\"store_id\" " + "group by \"store\".\"store_type\"";

        String loadCountDistinct_luciddb2 = "select " + "\"store\".\"store_type\" as \"c0\", " + "count(distinct "
                + "(select \"warehouse_class\".\"warehouse_class_id\" AS \"warehouse_class_id\" "
                + "from \"warehouse_class\" AS \"warehouse_class\" "
                + "where \"warehouse_class\".\"warehouse_class_id\" = \"warehouse\".\"warehouse_class_id\" and \"warehouse_class\".\"description\" = 'Large Independent')) as \"m0\" "
                + "from \"warehouse\" as \"warehouse\" join \"store\" as \"store\" "
                + "on \"warehouse\".\"stores_id\" = \"store\".\"store_id\" " + "group by \"store\".\"store_type\"";

        String loadOtherAggs_luciddb = "select " + "\"store\".\"store_type\" as \"c0\", " + "count("
                + "(select \"warehouse_class\".\"warehouse_class_id\" AS \"warehouse_class_id\" "
                + "from \"warehouse_class\" AS \"warehouse_class\" "
                + "where \"warehouse_class\".\"warehouse_class_id\" = \"warehouse\".\"warehouse_class_id\" and \"warehouse_class\".\"description\" = 'Large Independent')) as \"m0\", "
                + "count(distinct \"store_id\"+\"warehouse_id\") as \"m1\", "
                + "count(\"store_id\"+\"warehouse_id\") as \"m2\", " + "count(\"warehouse\".\"stores_id\") as \"m3\" "
                + "from \"warehouse\" as \"warehouse\" join \"store\" as \"store\" "
                + "on \"warehouse\".\"stores_id\" = \"store\".\"store_id\" " + "group by \"store\".\"store_type\"";

        // Derby splits into multiple statements.
        String loadCountDistinct_derby1 = "select \"store\".\"store_type\" as \"c0\", count(distinct (select \"warehouse_class\".\"warehouse_class_id\" AS \"warehouse_class_id\" from \"warehouse_class\" AS \"warehouse_class\" where \"warehouse_class\".\"warehouse_class_id\" = \"warehouse\".\"warehouse_class_id\" and \"warehouse_class\".\"description\" = 'Large Owned')) as \"m0\" from \"warehouse\" as \"warehouse\" join \"store\" as \"store\" on \"warehouse\".\"stores_id\" = \"store\".\"store_id\" group by \"store\".\"store_type\"";
        String loadCountDistinct_derby2 = "select \"store\".\"store_type\" as \"c0\", count(distinct (select \"warehouse_class\".\"warehouse_class_id\" AS \"warehouse_class_id\" from \"warehouse_class\" AS \"warehouse_class\" where \"warehouse_class\".\"warehouse_class_id\" = \"warehouse\".\"warehouse_class_id\" and \"warehouse_class\".\"description\" = 'Large Independent')) as \"m0\" from \"warehouse\" as \"warehouse\" join \"store\" as \"store\" on \"warehouse\".\"stores_id\" = \"store\".\"store_id\" group by \"store\".\"store_type\"";
        String loadCountDistinct_derby3 = "select \"store\".\"store_type\" as \"c0\", count(distinct \"store_id\"+\"warehouse_id\") as \"m0\" from \"warehouse\" as \"warehouse\" join \"store\" as \"store\" on \"warehouse\".\"stores_id\" = \"store\".\"store_id\" group by \"store\".\"store_type\"";
        String loadOtherAggs_derby = "select \"store\".\"store_type\" as \"c0\", count((select \"warehouse_class\".\"warehouse_class_id\" AS \"warehouse_class_id\" from \"warehouse_class\" AS \"warehouse_class\" where \"warehouse_class\".\"warehouse_class_id\" = \"warehouse\".\"warehouse_class_id\" and \"warehouse_class\".\"description\" = 'Large Independent')) as \"m0\", count(\"store_id\"+\"warehouse_id\") as \"m1\", count(\"warehouse\".\"stores_id\") as \"m2\" from \"warehouse\" as \"warehouse\" join \"store\" as \"store\" on \"warehouse\".\"stores_id\" = \"store\".\"store_id\" group by \"store\".\"store_type\"";

        // MySQL does it in one statement.
        String load_mysql = "select" + " `store`.`store_type` as `c0`,"
                + " count(distinct (select `warehouse_class`.`warehouse_class_id` AS `warehouse_class_id` from `warehouse_class` AS `warehouse_class` where `warehouse_class`.`warehouse_class_id` = `warehouse`.`warehouse_class_id` and `warehouse_class`.`description` = 'Large Owned')) as `m0`,"
                + " count(distinct (select `warehouse_class`.`warehouse_class_id` AS `warehouse_class_id` from `warehouse_class` AS `warehouse_class` where `warehouse_class`.`warehouse_class_id` = `warehouse`.`warehouse_class_id` and `warehouse_class`.`description` = 'Large Independent')) as `m1`,"
                + " count((select `warehouse_class`.`warehouse_class_id` AS `warehouse_class_id` from `warehouse_class` AS `warehouse_class` where `warehouse_class`.`warehouse_class_id` = `warehouse`.`warehouse_class_id` and `warehouse_class`.`description` = 'Large Independent')) as `m2`,"
                + " count(distinct `store_id`+`warehouse_id`) as `m3`," + " count(`store_id`+`warehouse_id`) as `m4`,"
                + " count(`warehouse`.`stores_id`) as `m5` " + "from `warehouse` as `warehouse`"
                + " join `store` as `store` " + "on `warehouse`.`stores_id` = `store`.`store_id` "
                + "group by `store`.`store_type`";

        SqlPattern[] patterns = {
                new SqlPattern(DatabaseProduct.LUCIDDB, loadCountDistinct_luciddb1, loadCountDistinct_luciddb1),
                new SqlPattern(DatabaseProduct.LUCIDDB, loadCountDistinct_luciddb2, loadCountDistinct_luciddb2),
                new SqlPattern(DatabaseProduct.LUCIDDB, loadOtherAggs_luciddb, loadOtherAggs_luciddb),

                new SqlPattern(DatabaseProduct.DERBY, loadCountDistinct_derby1, loadCountDistinct_derby1),
                new SqlPattern(DatabaseProduct.DERBY, loadCountDistinct_derby2, loadCountDistinct_derby2),
                new SqlPattern(DatabaseProduct.DERBY, loadCountDistinct_derby3, loadCountDistinct_derby3),
                new SqlPattern(DatabaseProduct.DERBY, loadOtherAggs_derby, loadOtherAggs_derby),

                new SqlPattern(DatabaseProduct.MYSQL, load_mysql, load_mysql), };

        SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns).verify();
    }

    /**
     * Creates the Warehouse2 cube with distinct-count measures based on SQL
     * expressions, layered onto the FoodMart catalog via composition
     * ({@code catalog = { CatalogSupplier, TestLoadDistinctSqlMeasureModifierEmf }})
     * instead of the legacy {@code withSchemaEmf} in-test mutation.
     */
    public static class TestLoadDistinctSqlMeasureModifierEmf implements CatalogMappingSupplier {

            /** The dialects that quote identifiers with a backtick. */
            private static final List<String> BACKTICK_DIALECTS = List.of("mysql", "mariadb", "infobright");

            /** Every other dialect the TCK runs; they all quote with ANSI double quotes. */
            private static final List<String> ANSI_DIALECTS = List.of("postgres", "h2", "duckdb", "derby", "sqlite",
                    "mssql", "clickhouse", "oracle");

            /**
             * Raw SQL is copied into the generated statement untouched, so the fragment has to
             * quote its identifiers the way the target dialect does. Derby and Oracle fold an
             * unquoted identifier to upper case and would not find the lower-case tables the
             * loader creates; MySQL reads a double-quoted identifier as a string literal, which
             * makes the predicate silently false rather than raising an error. Name every dialect
             * explicitly so a wrong guess cannot hide, and keep a "generic" entry because
             * SqlExpressionResolver.genericSql() looks for exactly that one.
             */
            private void addQuotedVariants(ExpressionColumn column, String ansiSql, String mysqlSql) {
                column.getSqls().add(sqlStatement(ansiSql, "generic"));
                ANSI_DIALECTS.forEach(dialect -> column.getSqls().add(sqlStatement(ansiSql, dialect)));
                BACKTICK_DIALECTS.forEach(dialect -> column.getSqls().add(sqlStatement(mysqlSql, dialect)));
            }

            private SqlStatement sqlStatement(String sql, String dialect) {
                SqlStatement statement = SourceFactory.eINSTANCE.createSqlStatement();
                statement.getDialects().add(dialect);
                statement.setBody(sql);
                return statement;
            }

            private String warehouseClassSubselect(String description, char q) {
                return "(select " + q + "warehouse_class" + q + "." + q + "warehouse_class_id" + q + " AS " + q
                        + "warehouse_class_id" + q + " from " + q + "warehouse_class" + q + " AS " + q
                        + "warehouse_class" + q + " where " + q + "warehouse_class" + q + "." + q + "warehouse_class_id"
                        + q + " = " + q + "warehouse" + q + "." + q + "warehouse_class_id" + q + " and " + q
                        + "warehouse_class" + q + "." + q + "description" + q + " = '" + description + "')";
            }

            private String storeIdPlusWarehouseId(char q) {
                return q + "store_id" + q + "+" + q + "warehouse_id" + q;
            }

            private CatalogImpl catalog;

            public TestLoadDistinctSqlMeasureModifierEmf(Catalog cat) {
                // Copy catalog using EcoreUtil
                EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) cat);
                catalog = (CatalogImpl) copier.get(cat);
                // Create the Warehouse2 cube
                PhysicalCube warehouse2Cube = CubeFactory.eINSTANCE.createPhysicalCube();
                warehouse2Cube.setName("Warehouse2");

                // Set up query
                TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
                tableQuery.setTable((Table) copier.get(CatalogSupplier.TABLE_WAREHOUSE));
                warehouse2Cube.setSource(tableQuery);

                // Create dimension connector for Store Type
                DimensionConnector storeTypeDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
                storeTypeDimConnector.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_STORES_ID_WAREHOUSE));
                storeTypeDimConnector.setOverrideDimensionName("Store Type");
                storeTypeDimConnector.setDimension((Dimension) copier.get(CatalogSupplier.DIMENSION_STORE_TYPE));

                warehouse2Cube.getDimensionConnectors().add(storeTypeDimConnector);

                // Create measure group
                MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();

                // 1. Count Distinct of Warehouses (Large Owned)
                CountMeasure measure1 = MeasureFactory.eINSTANCE.createCountMeasure();
                measure1.setName("Count Distinct of Warehouses (Large Owned)");
                measure1.setDistinct(true);
                measure1.setFormatString("#,##0");

                ExpressionColumn sqlCol1 = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createExpressionColumn();
                addQuotedVariants(sqlCol1, warehouseClassSubselect("Large Owned", '"'),
                        warehouseClassSubselect("Large Owned", '`'));
                measure1.setColumn(sqlCol1);

                // 2. Count Distinct of Warehouses (Large Independent)
                CountMeasure measure2 = MeasureFactory.eINSTANCE.createCountMeasure();
                measure2.setName("Count Distinct of Warehouses (Large Independent)");
                measure2.setDistinct(true);
                measure2.setFormatString("#,##0");

                ExpressionColumn sqlCol2 = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createExpressionColumn();
                addQuotedVariants(sqlCol2, warehouseClassSubselect("Large Independent", '"'),
                        warehouseClassSubselect("Large Independent", '`'));
                measure2.setColumn(sqlCol2);

                // 3. Count All of Warehouses (Large Independent)
                CountMeasure measure3 = MeasureFactory.eINSTANCE.createCountMeasure();
                measure3.setName("Count All of Warehouses (Large Independent)");
                measure3.setDistinct(false);
                measure3.setFormatString("#,##0");

                ExpressionColumn sqlCol3 = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createExpressionColumn();
                addQuotedVariants(sqlCol3, warehouseClassSubselect("Large Independent", '"'),
                        warehouseClassSubselect("Large Independent", '`'));
                measure3.setColumn(sqlCol3);

                // 4. Count Distinct Store+Warehouse
                CountMeasure measure4 = MeasureFactory.eINSTANCE.createCountMeasure();
                measure4.setName("Count Distinct Store+Warehouse");
                measure4.setDistinct(true);
                measure4.setFormatString("#,##0");

                ExpressionColumn sqlCol4 = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createExpressionColumn();
                addQuotedVariants(sqlCol4, storeIdPlusWarehouseId('"'), storeIdPlusWarehouseId('`'));
                measure4.setColumn(sqlCol4);

                // 5. Count All Store+Warehouse
                CountMeasure measure5 = MeasureFactory.eINSTANCE.createCountMeasure();
                measure5.setName("Count All Store+Warehouse");
                measure5.setDistinct(false);
                measure5.setFormatString("#,##0");

                ExpressionColumn sqlCol5 = org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory.eINSTANCE.createExpressionColumn();
                addQuotedVariants(sqlCol5, storeIdPlusWarehouseId('"'), storeIdPlusWarehouseId('`'));
                measure5.setColumn(sqlCol5);

                // 6. Store Count
                CountMeasure measure6 = MeasureFactory.eINSTANCE.createCountMeasure();
                measure6.setName("Store Count");
                measure6.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORES_ID_WAREHOUSE));
                measure6.setFormatString("#,###");

                // Add all measures to measure group
                measureGroup.getMeasures().add(measure1);
                measureGroup.getMeasures().add(measure2);
                measureGroup.getMeasures().add(measure3);
                measureGroup.getMeasures().add(measure4);
                measureGroup.getMeasures().add(measure5);
                measureGroup.getMeasures().add(measure6);

                warehouse2Cube.getMeasureGroups().add(measureGroup);

                // Add the new cube to the catalog
                catalog.getImportedElement().add(warehouse2Cube);
            }

            @Override
            public Catalog get() {
                return catalog;
            }
        }

    @Test
    void testAggregateDistinctCount(Context<?> context) {
        prepareContext(context);
        // solve_order=1 says to aggregate [CA] and [OR] before computing their
        // sums
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "WITH MEMBER [Time].[Time].[1997 Q1 plus Q2] AS 'AGGREGATE({[Time].[1997].[Q1], [Time].[1997].[Q2]})', solve_order=1\n"
                        + "SELECT {[Measures].[Customer Count]} ON COLUMNS,\n"
                        + "      {[Time].[1997].[Q1], [Time].[1997].[Q2], [Time].[1997 Q1 plus Q2]} ON ROWS\n"
                        + "FROM Sales\n" + "WHERE ([Store].[USA].[CA])").returnsGrid(
                "Axis #0:\n" + "{[Store].[Store].[USA].[CA]}\n" + "Axis #1:\n" + "{[Measures].[Customer Count]}\n"
                        + "Axis #2:\n" + "{[Time].[Time].[1997].[Q1]}\n" + "{[Time].[Time].[1997].[Q2]}\n"
                        + "{[Time].[Time].[1997 Q1 plus Q2]}\n" + "Row #0: 1,110\n" + "Row #1: 1,173\n"
                        + "Row #2: 1,854\n");
    }

    /**
     * As {@link #testAggregateDistinctCount()}, but (a) calc member includes
     * members from different levels and (b) also display [unit sales].
     */
    @Test
    void testAggregateDistinctCount2(Context<?> context) {
        prepareContext(context);
        assertThatQuery(context.getConnectionWithDefaultRole(), "WITH MEMBER [Time].[Time].[1997 Q1 plus July] AS\n"
                + " 'AGGREGATE({[Time].[1997].[Q1], [Time].[1997].[Q3].[7]})', solve_order=1\n"
                + "SELECT {[Measures].[Unit Sales], [Measures].[Customer Count]} ON COLUMNS,\n"
                + "      {[Time].[1997].[Q1],\n" + "       [Time].[1997].[Q2],\n" + "       [Time].[1997].[Q3].[7],\n"
                + "       [Time].[1997 Q1 plus July]} ON ROWS\n" + "FROM Sales\n" + "WHERE ([Store].[USA].[CA])").returnsGrid(
                "Axis #0:\n" + "{[Store].[Store].[USA].[CA]}\n" + "Axis #1:\n" + "{[Measures].[Unit Sales]}\n"
                        + "{[Measures].[Customer Count]}\n" + "Axis #2:\n" + "{[Time].[Time].[1997].[Q1]}\n"
                        + "{[Time].[Time].[1997].[Q2]}\n" + "{[Time].[Time].[1997].[Q3].[7]}\n"
                        + "{[Time].[Time].[1997 Q1 plus July]}\n" + "Row #0: 16,890\n" + "Row #0: 1,110\n"
                        + "Row #1: 18,052\n" + "Row #1: 1,173\n" + "Row #2: 5,403\n" + "Row #2: 412\n"
                        // !!!
                        + "Row #3: 22,293\n"
                        // = 16,890 + 5,403
                        + "Row #3: 1,386\n"); // between 1,110 and 1,110 + 412
    }

    /**
     * As {@link #testAggregateDistinctCount2()}, but with two calc members
     * simultaneously.
     */
    @Test
    void testAggregateDistinctCount3(Context<?> context) {
        prepareContext(context);
        assertThatQuery(context.getConnectionWithDefaultRole(), "WITH\n"
                + "  MEMBER [Promotion Media].[TV plus Radio] AS 'AGGREGATE({[Promotion Media].[TV], [Promotion Media].[Radio]})', solve_order=1\n"
                + "  MEMBER [Time].[Time].[1997 Q1 plus July] AS 'AGGREGATE({[Time].[1997].[Q1], [Time].[1997].[Q3].[7]})', solve_order=1\n"
                + "SELECT {[Promotion Media].[TV plus Radio],\n" + "        [Promotion Media].[TV],\n"
                + "        [Promotion Media].[Radio]} ON COLUMNS,\n" + "       {[Time].[1997],\n"
                + "        [Time].[1997].[Q1],\n" + "        [Time].[1997 Q1 plus July]} ON ROWS\n" + "FROM Sales\n"
                + "WHERE [Measures].[Customer Count]").returnsGrid(
                "Axis #0:\n" + "{[Measures].[Customer Count]}\n" + "Axis #1:\n"
                        + "{[Promotion Media].[Promotion Media].[TV plus Radio]}\n"
                        + "{[Promotion Media].[Promotion Media].[TV]}\n"
                        + "{[Promotion Media].[Promotion Media].[Radio]}\n" + "Axis #2:\n" + "{[Time].[Time].[1997]}\n"
                        + "{[Time].[Time].[1997].[Q1]}\n" + "{[Time].[Time].[1997 Q1 plus July]}\n" + "Row #0: 455\n"
                        + "Row #0: 274\n" + "Row #0: 186\n" + "Row #1: 139\n" + "Row #1: 99\n" + "Row #1: 40\n"
                        + "Row #2: 139\n" + "Row #2: 99\n" + "Row #2: 40\n");

        // There are 9 cells in the result. 6 sql statements have to be issued
        // to fetch all of them, with each loading these cells:
        // (1) ([1997], [TV Plus radio])
        //
        // (2) ([1997], [TV])
        // ([1997], [radio])
        //
        // (3) ([1997].[Q1], [TV Plus radio])
        //
        // (4) ([1997].[Q1], [TV])
        // ([1997].[Q1], [radio])
        //
        // (5) ([1997 Q1 plus July], [TV Plus radio])
        //
        // (6) ([1997 Q1 Plus July], [TV])
        // ([1997 Q1 Plus July], [radio])
        final String oracleSql = "select "
                + "\"time_by_day\".\"the_year\" as \"c0\", \"time_by_day\".\"quarter\" as \"c1\", "
                + "\"promotion\".\"media_type\" as \"c2\", "
                + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" " + "from "
                + "\"sales_fact_1997\" \"sales_fact_1997\" "
                + "join \"time_by_day\" \"time_by_day\" on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" "
                + "join \"promotion\" \"promotion\" on \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\" "
                + "where "
                + "\"time_by_day\".\"the_year\" = 1997 and " + "\"time_by_day\".\"quarter\" = 'Q1' and "
                + "\"promotion\".\"media_type\" in ('Radio', 'TV') " + "group by "
                + "\"time_by_day\".\"the_year\", \"time_by_day\".\"quarter\", " + "\"promotion\".\"media_type\"";

        final String mysqlSql = "select " + "`time_by_day`.`the_year` as `c0`, `time_by_day`.`quarter` as `c1`, "
                + "`promotion`.`media_type` as `c2`, count(distinct `sales_fact_1997`.`customer_id`) as `m0` " + "from "
                + "`sales_fact_1997` as `sales_fact_1997` "
                + "join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` "
                + "join `promotion` as `promotion` on `sales_fact_1997`.`promotion_id` = `promotion`.`promotion_id` "
                + "where "
                + "`time_by_day`.`the_year` = 1997 and `time_by_day`.`quarter` = 'Q1' and "
                + "`promotion`.`media_type` in ('Radio', 'TV') " + "group by "
                + "`time_by_day`.`the_year`, `time_by_day`.`quarter`, `promotion`.`media_type`";

        final String derbySql = "select "
                + "\"time_by_day\".\"the_year\" as \"c0\", \"time_by_day\".\"quarter\" as \"c1\", "
                + "\"promotion\".\"media_type\" as \"c2\", "
                + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" " + "from "
                + "\"sales_fact_1997\" as \"sales_fact_1997\" "
                + "join \"time_by_day\" as \"time_by_day\" on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" "
                + "join \"promotion\" as \"promotion\" on \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\" "
                + "where "
                + "\"time_by_day\".\"the_year\" = 1997 and \"time_by_day\".\"quarter\" = 'Q1' and "
                + "\"promotion\".\"media_type\" in ('Radio', 'TV') " + "group by "
                + "\"time_by_day\".\"the_year\", \"time_by_day\".\"quarter\", " + "\"promotion\".\"media_type\"";

        SqlAssert.forQuery(context.getConnectionWithDefaultRole(), "WITH\n"
                + "  MEMBER [Promotion Media].[TV plus Radio] AS 'AGGREGATE({[Promotion Media].[TV], [Promotion Media].[Radio]})', solve_order=1\n"
                + "  MEMBER [Time].[Time].[1997 Q1 plus July] AS 'AGGREGATE({[Time].[1997].[Q1], [Time].[1997].[Q3].[7]})', solve_order=1\n"
                + "SELECT {[Promotion Media].[TV plus Radio],\n" + "        [Promotion Media].[TV],\n"
                + "        [Promotion Media].[Radio]} ON COLUMNS,\n" + "       {[Time].[1997],\n"
                + "        [Time].[1997].[Q1],\n" + "        [Time].[1997 Q1 plus July]} ON ROWS\n" + "FROM Sales\n"
                + "WHERE [Measures].[Customer Count]").expectSql(new SqlPattern[] { new SqlPattern(DatabaseProduct.ORACLE, oracleSql, oracleSql),
                        new SqlPattern(DatabaseProduct.MYSQL, mysqlSql, mysqlSql),
                        new SqlPattern(DatabaseProduct.DERBY, derbySql, derbySql) }).verify();
    }

    /**
     * Distinct count over aggregate member which contains overlapping members. Need
     * to count them twice for rollable measures such as [Unit Sales], but not for
     * distinct-count measures such as [Customer Count].
     */
    @Test
    void testAggregateDistinctCount4(Context<?> context) {
        prepareContext(context);
        // CA and USA are overlapping members
        final String mdxQuery = "WITH\n"
                + "  MEMBER [Store].[CA plus USA] AS 'AGGREGATE({[Store].[USA].[CA], [Store].[USA]})', solve_order=1\n"
                + "  MEMBER [Time].[Time].[Q1 plus July] AS 'AGGREGATE({[Time].[1997].[Q1], [Time].[1997].[Q3].[7]})', solve_order=1\n"
                + "SELECT {[Measures].[Customer Count], [Measures].[Unit Sales]} ON COLUMNS,\n"
                + "      Union({[Store].[CA plus USA]} * {[Time].[Q1 plus July]}, "
                + "      Union({[Store].[USA].[CA]} * {[Time].[Q1 plus July]},"
                + "      Union({[Store].[USA]} * {[Time].[Q1 plus July]},"
                + "      Union({[Store].[CA plus USA]} * {[Time].[1997].[Q1]},"
                + "            {[Store].[CA plus USA]} * {[Time].[1997].[Q3].[7]})))) ON ROWS\n" + "FROM Sales";

        String result = "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Measures].[Customer Count]}\n"
                + "{[Measures].[Unit Sales]}\n" + "Axis #2:\n"
                + "{[Store].[Store].[CA plus USA], [Time].[Time].[Q1 plus July]}\n"
                + "{[Store].[Store].[USA].[CA], [Time].[Time].[Q1 plus July]}\n"
                + "{[Store].[Store].[USA], [Time].[Time].[Q1 plus July]}\n"
                + "{[Store].[Store].[CA plus USA], [Time].[Time].[1997].[Q1]}\n"
                + "{[Store].[Store].[CA plus USA], [Time].[Time].[1997].[Q3].[7]}\n" + "Row #0: 3,505\n"
                + "Row #0: 112,347\n" + "Row #1: 1,386\n" + "Row #1: 22,293\n" + "Row #2: 3,505\n" + "Row #2: 90,054\n"
                + "Row #3: 2,981\n" + "Row #3: 83,181\n" + "Row #4: 1,462\n" + "Row #4: 29,166\n";

        assertThatQuery(context.getConnectionWithDefaultRole(), mdxQuery).returnsGrid( result);
    }

    /**
     * Fix a problem when genergating predicates for distinct count aggregate
     * loading and using the aggregate function in the slicer.
     */
    @Test
    @RolapConfig(key = ConfigConstants.MAX_CONSTRAINTS, value = "2", type = Integer.class)
    void testAggregateDistinctCount5(Context<?> context) {
        prepareContext(context);
        // make sure tuple optimization will be used

        String query = "With " + "Set [Products] as " + " '{[Product].[Drink], " + "   [Product].[Food], "
                + "   [Product].[Non-Consumable]}' " + "Member [Product].[Selected Products] as "
                + " 'Aggregate([Products])', SOLVE_ORDER=2 " + "Select " + " {[Store].[Store State].Members} on rows, "
                + " {[Measures].[Customer Count]} on columns " + "From [Sales] "
                + "Where ([Product].[Selected Products])";

        String derbySql = "select \"store\".\"store_state\" as \"c0\", " + "\"time_by_day\".\"the_year\" as \"c1\", "
                + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" "
                + "from \"sales_fact_1997\" as \"sales_fact_1997\" "
                + "join \"store\" as \"store\" on \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\" "
                + "join \"time_by_day\" as \"time_by_day\" on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" "
                + "where \"time_by_day\".\"the_year\" = 1997 "
                + "group by \"store\".\"store_state\", \"time_by_day\".\"the_year\"";

        String mysqlSql = "select `store`.`store_state` as `c0`, `time_by_day`.`the_year` as `c1`, "
                + "count(distinct `sales_fact_1997`.`customer_id`) as `m0` "
                + "from `sales_fact_1997` as `sales_fact_1997` "
                + "join `store` as `store` on `sales_fact_1997`.`store_id` = `store`.`store_id` "
                + "join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` "
                + "where `time_by_day`.`the_year` = 1997 "
                + "group by `store`.`store_state`, `time_by_day`.`the_year`";

        SqlPattern[] patterns = { new SqlPattern(DatabaseProduct.DERBY, derbySql, derbySql),
                new SqlPattern(DatabaseProduct.MYSQL, mysqlSql, mysqlSql) };

        SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns).verify();
    }

    // Test for multiple members on different levels within the same hierarchy.
    @Test
    void testAggregateDistinctCount6(Context<?> context) {
        prepareContext(context);
        // CA and USA are overlapping members
        final String mdxQuery = "WITH " + " MEMBER [Store].[Select Region] AS "
                + " 'AGGREGATE({[Store].[USA].[CA], [Store].[Mexico], [Store].[Canada], [Store].[USA].[OR]})', solve_order=1\n"
                + " MEMBER [Time].[Time].[Select Time Period] AS "
                + " 'AGGREGATE({[Time].[1997].[Q1], [Time].[1997].[Q3].[7], [Time].[1997].[Q4], [Time].[1997]})', solve_order=1\n"
                + "SELECT {[Measures].[Customer Count], [Measures].[Unit Sales]} ON COLUMNS,\n"
                + "      Union({[Store].[Select Region]} * {[Time].[Select Time Period]},"
                + "      Union({[Store].[Select Region]} * {[Time].[1997].[Q1]},"
                + "      Union({[Store].[Select Region]} * {[Time].[1997].[Q3].[7]},"
                + "      Union({[Store].[Select Region]} * {[Time].[1997].[Q4]},"
                + "            {[Store].[Select Region]} * {[Time].[1997]})))) " + "ON ROWS\n" + "FROM Sales";

        String result = "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Measures].[Customer Count]}\n"
                + "{[Measures].[Unit Sales]}\n" + "Axis #2:\n"
                + "{[Store].[Store].[Select Region], [Time].[Time].[Select Time Period]}\n"
                + "{[Store].[Store].[Select Region], [Time].[Time].[1997].[Q1]}\n"
                + "{[Store].[Store].[Select Region], [Time].[Time].[1997].[Q3].[7]}\n"
                + "{[Store].[Store].[Select Region], [Time].[Time].[1997].[Q4]}\n"
                + "{[Store].[Store].[Select Region], [Time].[Time].[1997]}\n" + "Row #0: 3,753\n" + "Row #0: 229,496\n"
                + "Row #1: 1,877\n" + "Row #1: 36,177\n" + "Row #2: 845\n" + "Row #2: 13,123\n" + "Row #3: 2,073\n"
                + "Row #3: 37,789\n" + "Row #4: 3,753\n" + "Row #4: 142,407\n";

        assertThatQuery(context.getConnectionWithDefaultRole(), mdxQuery).returnsGrid( result);
    }

    /**
     * Test case for bug 1785406 to fix "query already contains alias" exception.
     *
     * <p>
     * Note: 1785406 is a regression from checkin 9710. Code changes made in 9710 is
     * no longer in use (and removed). So this bug will not occur; however, keeping
     * the test case here to get some coverage for a query with a slicer.
     */
    @Test
    void testDistinctCountBug1785406(Context<?> context) {
        prepareContext(context);
        String query = "With \n" + "Set [*BASE_MEMBERS_Product] as {[Product].[All Products].[Food].[Deli]}\n"
                + "Set [*BASE_MEMBERS_Store] as {[Store].[All Stores].[USA].[WA]}\n"
                + "Member [Product].[*CTX_MEMBER_SEL~SUM] As Aggregate([*BASE_MEMBERS_Product])\n" + "Select\n"
                + "{[Measures].[Customer Count]} on columns,\n"
                + "NonEmptyCrossJoin([*BASE_MEMBERS_Store],{([Product].[*CTX_MEMBER_SEL~SUM])})\n" + "on rows\n"
                + "From [Sales]\n" + "where ([Time].[1997])";

        assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid(
                "Axis #0:\n" + "{[Time].[Time].[1997]}\n" + "Axis #1:\n" + "{[Measures].[Customer Count]}\n"
                        + "Axis #2:\n" + "{[Store].[Store].[USA].[WA], [Product].[Product].[*CTX_MEMBER_SEL~SUM]}\n"
                        + "Row #0: 889\n");

        String mysqlSql = "select " + "`store`.`store_state` as `c0`, `time_by_day`.`the_year` as `c1`, "
                + "count(distinct `sales_fact_1997`.`customer_id`) as `m0` " + "from "
                + "`sales_fact_1997` as `sales_fact_1997` "
                + "join `store` as `store` on `sales_fact_1997`.`store_id` = `store`.`store_id` "
                + "join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` "
                + "join `product` as `product` on `sales_fact_1997`.`product_id` = `product`.`product_id` "
                + "join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id` "
                + "where " + "`store`.`store_state` = 'WA' "
                + "and `time_by_day`.`the_year` = 1997 "
                + "and (`product_class`.`product_department` = 'Deli' "
                + "and `product_class`.`product_family` = 'Food') "
                + "group by `store`.`store_state`, `time_by_day`.`the_year`";

        String accessSql = "select `d0` as `c0`," + " `d1` as `c1`," + " count(`m0`) as `c2` "
                + "from (select distinct `store`.`store_state` as `d0`," + " `time_by_day`.`the_year` as `d1`,"
                + " `sales_fact_1997`.`customer_id` as `m0` " + "from `sales_fact_1997` as `sales_fact_1997`"
                + " join `store` as `store` on `sales_fact_1997`.`store_id` = `store`.`store_id`"
                + " join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`"
                + " join `product` as `product` on `sales_fact_1997`.`product_id` = `product`.`product_id`"
                + " join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id` "
                + "where `store`.`store_state` = 'WA' "
                + "and `time_by_day`.`the_year` = 1997 "
                + "and (`product_class`.`product_department` = 'Deli' "
                + "and `product_class`.`product_family` = 'Food')) as `dummyname` " + "group by `d0`, `d1`";

        String derbySql = "select " + "\"store\".\"store_state\" as \"c0\", "
                + "\"time_by_day\".\"the_year\" as \"c1\", "
                + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" " + "from "
                + "\"sales_fact_1997\" as \"sales_fact_1997\" "
                + "join \"store\" as \"store\" on \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\" "
                + "join \"time_by_day\" as \"time_by_day\" on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" "
                + "join \"product\" as \"product\" on \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" "
                + "join \"product_class\" as \"product_class\" on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" "
                + "where "
                + "\"store\".\"store_state\" = 'WA' "
                + "and \"time_by_day\".\"the_year\" = 1997 "
                + "and (\"product_class\".\"product_department\" = 'Deli' "
                + "and \"product_class\".\"product_family\" = 'Food') "
                + "group by \"store\".\"store_state\", \"time_by_day\".\"the_year\"";

        SqlPattern[] patterns = { new SqlPattern(DatabaseProduct.ACCESS, accessSql, accessSql),
                new SqlPattern(DatabaseProduct.DERBY, derbySql, derbySql),
                new SqlPattern(DatabaseProduct.MYSQL, mysqlSql, mysqlSql) };

        SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns).verify();
    }

    @Test
    void testDistinctCountBug1785406_2(Context<?> context) {
        prepareContext(context);
        String query = "With " + "Member [Product].[x] as 'Aggregate({Gender.CurrentMember})'\n"
                + "member [Measures].[foo] as '([Product].[x],[Measures].[Customer Count])'\n"
                + "select Filter([Gender].members,(Not IsEmpty([Measures].[foo]))) on 0 " + "from Sales";

        assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid(
                "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n" + "{[Gender].[Gender].[M]}\n" + "Row #0: 266,773\n"
                        + "Row #0: 131,558\n" + "Row #0: 135,215\n");

        String mysqlSql = "select " + "`time_by_day`.`the_year` as `c0`, "
                + "count(distinct `sales_fact_1997`.`customer_id`) as `m0` " + "from "
                + "`sales_fact_1997` as `sales_fact_1997` "
                + "join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` "
                + "where `time_by_day`.`the_year` = 1997 " + "group by `time_by_day`.`the_year`";

        String accessSql = "select `d0` as `c0`," + " count(`m0`) as `c1` "
                + "from (select distinct `time_by_day`.`the_year` as `d0`,"
                + " `sales_fact_1997`.`customer_id` as `m0` " + "from `sales_fact_1997` as `sales_fact_1997` "
                + "join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` "
                + "where `time_by_day`.`the_year` = 1997) as `dummyname` group by `d0`";

        String derbySql = "select " + "\"time_by_day\".\"the_year\" as \"c0\", "
                + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" " + "from "
                + "\"sales_fact_1997\" as \"sales_fact_1997\" "
                + "join \"time_by_day\" as \"time_by_day\" on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" "
                + "where "
                + "\"time_by_day\".\"the_year\" = 1997 " + "group by \"time_by_day\".\"the_year\"";

        SqlPattern[] patterns = { new SqlPattern(DatabaseProduct.ACCESS, accessSql, accessSql),
                new SqlPattern(DatabaseProduct.DERBY, derbySql, derbySql),
                new SqlPattern(DatabaseProduct.MYSQL, mysqlSql, mysqlSql) };

        SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns).verify();
    }

    @Test
    void testAggregateDistinctCount2ndParameter(Context<?> context) {
        prepareContext(context);
        // simple case of count distinct measure as second argument to
        // Aggregate(). Should apply distinct-count aggregator (MONDRIAN-2016)
        assertThatQuery(connection,
                "with\n" + "  set periods as [Time].[Time].[1997].[Q1].[1] : [Time].[Time].[1997].[Q4].[10]\n"
                        + "  member [Time].[Time].[agg] as Aggregate(periods, [Measures].[Customer Count])\n"
                        + "select\n" + "  [Time].[agg]  ON COLUMNS,\n" + "  [Gender].[Gender].[M] on ROWS\n"
                        + "FROM [Sales]").returnsGrid(
                "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Time].[Time].[agg]}\n" + "Axis #2:\n"
                        + "{[Gender].[Gender].[M]}\n" + "Row #0: 2,651\n");
        assertThatQuery(connection,
                "WITH MEMBER [Measures].[My Distinct Count] AS \n"
                        + "'AGGREGATE([1997].Children, [Measures].[Customer Count])' \n"
                        + "SELECT {[Measures].[My Distinct Count], [Measures].[Customer Count]} ON COLUMNS,\n"
                        + "{[1997].Children} ON ROWS\n" + "FROM Sales").returnsGrid(
                "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Measures].[My Distinct Count]}\n"
                        + "{[Measures].[Customer Count]}\n" + "Axis #2:\n" + "{[Time].[Time].[1997].[Q1]}\n"
                        + "{[Time].[Time].[1997].[Q2]}\n" + "{[Time].[Time].[1997].[Q3]}\n"
                        + "{[Time].[Time].[1997].[Q4]}\n" + "Row #0: 5,581\n" + "Row #0: 2,981\n" + "Row #1: 5,581\n"
                        + "Row #1: 2,973\n" + "Row #2: 5,581\n" + "Row #2: 3,026\n" + "Row #3: 5,581\n"
                        + "Row #3: 3,261\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, TestCountDistinctAggWithOtherCountDistinctInContextModifierEmf.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testCountDistinctAggWithOtherCountDistinctInContext(Context<?> context) {
        prepareContext(context);
        // tests that Aggregate( <set>, <count-distinct measure>) aggregates
        // the correct measure when a *different* count-distinct measure is
        // in context (MONDRIAN-2128)
        // We should get the same answer whether the default [Store Count]
        // measure is in context or [Unit Sales]. The measure specified in the
        // second param of Aggregate() should be used.
        final String queryStoreCountInContext = "with member Store.agg as "
                + "'aggregate({[Store].[USA].[CA],[Store].[USA].[OR]}, " + "           measures.[Customer Count])'"
                + " select Store.agg on 0 from [2CountDistincts] ";
        final String queryUnitSalesInContext = "with member Store.agg as "
                + "'aggregate({[Store].[USA].[CA],[Store].[USA].[OR]}, " + "           measures.[Customer Count])'"
                + " select Store.agg on 0 from [2CountDistincts] where " + "measures.[Unit Sales] ";
        assertQueriesReturnSimilarResults(context.getConnectionWithDefaultRole(), queryStoreCountInContext,
                queryUnitSalesInContext);

        final String queryCAORRollup = "with member measures.agg as "
                + "'aggregate({[Store].[USA].[CA],[Store].[USA].[OR]}, " + "           measures.[Customer Count])'"
                + " select {measures.agg, measures.[Customer Count]} on 0,  "
                + " [Product].[All Products].children on 1 " + "from [2CountDistincts] ";
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatQuery(connection, queryCAORRollup).returnsGrid(
                "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Measures].[agg]}\n" + "{[Measures].[Customer Count]}\n"
                        + "Axis #2:\n" + "{[Product].[Product].[Drink]}\n" + "{[Product].[Product].[Food]}\n"
                        + "{[Product].[Product].[Non-Consumable]}\n" + "Row #0: 2,243\n" + "Row #0: 3,485\n"
                        + "Row #1: 3,711\n" + "Row #1: 5,525\n" + "Row #2: 2,957\n" + "Row #2: 4,468\n");

        // [Customer Count] should override context
        assertThatQuery(connection,
                "with member Store.agg as " + "'aggregate({[Store].[USA].[CA],[Store].[USA].[OR]}, "
                        + "           measures.[Customer Count])'"
                        + " select {measures.[Store Count], measures.[Customer Count]} on 0,  " + " [Store].agg on 1 "
                        + "from [2CountDistincts] ").returnsGrid(
                "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Measures].[Store Count]}\n"
                        + "{[Measures].[Customer Count]}\n" + "Axis #2:\n" + "{[Store].[Store].[agg]}\n"
                        + "Row #0: 3,753\n" + "Row #0: 3,753\n");
        // aggregate should pick up measure in context
        assertThatQuery(connection,
                "with member Store.agg as " + "'aggregate({[Store].[USA].[CA],[Store].[USA].[OR]})'"
                        + " select {measures.[Store Count], measures.[Customer Count]} on 0,  " + " [Store].agg on 1 "
                        + "from [2CountDistincts] ").returnsGrid(
                "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Measures].[Store Count]}\n"
                        + "{[Measures].[Customer Count]}\n" + "Axis #2:\n" + "{[Store].[Store].[agg]}\n" + "Row #0: 6\n"
                        + "Row #0: 3,753\n");
    }

    /**
     * Creates the 2CountDistincts cube with multiple distinct-count measures,
     * layered onto the FoodMart catalog via composition ({@code catalog = {
     * CatalogSupplier, TestCountDistinctAggWithOtherCountDistinctInContextModifierEmf }})
     * instead of the legacy {@code withSchemaEmf} in-test mutation.
     */
    public static class TestCountDistinctAggWithOtherCountDistinctInContextModifierEmf implements CatalogMappingSupplier {

            private CatalogImpl catalog;

            public TestCountDistinctAggWithOtherCountDistinctInContextModifierEmf(Catalog cat) {
                // Copy catalog using EcoreUtil
                EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) cat);
                catalog = (CatalogImpl) copier.get(cat);

                // Create Store Count measure (distinct count)
                CountMeasure storeCountMeasure = MeasureFactory.eINSTANCE.createCountMeasure();
                storeCountMeasure.setName("Store Count");
                storeCountMeasure.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_ID_SALESFACT));
                storeCountMeasure.setDistinct(true);

                // Create Customer Count measure (distinct count)
                CountMeasure customerCountMeasure = MeasureFactory.eINSTANCE.createCountMeasure();
                customerCountMeasure.setName("Customer Count");
                customerCountMeasure.setColumn((Column) copier.get(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT));
                customerCountMeasure.setDistinct(true);

                // Create Unit Sales measure (sum)
                SumMeasure unitSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
                unitSalesMeasure.setName("Unit Sales");
                unitSalesMeasure.setColumn((Column) copier.get(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT));

                // Create 2CountDistincts cube
                PhysicalCube twoCountDistinctsCube = CubeFactory.eINSTANCE.createPhysicalCube();
                twoCountDistinctsCube.setName("2CountDistincts");
                twoCountDistinctsCube.setDefaultMeasure(storeCountMeasure);

                // Set up query
                TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
                tableQuery.setTable((Table) copier.get(CatalogSupplier.TABLE_SALES_FACT));
                twoCountDistinctsCube.setSource(tableQuery);

                // Create dimension connector for Time
                DimensionConnector timeDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
                timeDimConnector.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_TIME_ID_SALESFACT));
                timeDimConnector.setOverrideDimensionName("Time");
                timeDimConnector.setDimension((Dimension) copier.get(CatalogSupplier.DIMENSION_TIME));

                // Create dimension connector for Store
                DimensionConnector storeDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
                storeDimConnector.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_STORE_ID_SALESFACT));
                storeDimConnector.setOverrideDimensionName("Store");
                storeDimConnector.setDimension((Dimension) copier.get(CatalogSupplier.DIMENSION_STORE));

                // Create dimension connector for Product
                DimensionConnector productDimConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
                productDimConnector.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_PRODUCT_ID_SALESFACT));
                productDimConnector.setOverrideDimensionName("Product");
                productDimConnector.setDimension((Dimension) copier.get(CatalogSupplier.DIMENSION_PRODUCT));

                twoCountDistinctsCube.getDimensionConnectors().add(timeDimConnector);
                twoCountDistinctsCube.getDimensionConnectors().add(storeDimConnector);
                twoCountDistinctsCube.getDimensionConnectors().add(productDimConnector);

                // Create measure group with all measures
                MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
                measureGroup.getMeasures().add(storeCountMeasure);
                measureGroup.getMeasures().add(customerCountMeasure);
                measureGroup.getMeasures().add(unitSalesMeasure);

                twoCountDistinctsCube.getMeasureGroups().add(measureGroup);

                // Add the new cube to the catalog
                catalog.getImportedElement().add(twoCountDistinctsCube);
            }

            @Override
            public Catalog get() {
                return catalog;
            }
        }

    @Test
    void testContextSetCorrectlyWith2ParamAggregate(Context<?> context) {
        prepareContext(context);
        // Aggregate with a second parameter may change context. Verify
        // the evaluator is restored. The query below would return
        // the [Unit Sales] value instead of [Store Sales] if context was
        // not restored.
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with \n" + "member Store.cond as 'iif( \n"
                        + "aggregate({[Store].[All Stores].[USA]}, measures.[unit sales])\n"
                        + " > 70000, (Store.[All Stores], measures.currentMember), 0)'\n"
                        + "select Store.cond on 0 from sales\n" + "where measures.[store sales]\n").returnsGrid(
                "Axis #0:\n" + "{[Measures].[Store Sales]}\n" + "Axis #1:\n" + "{[Store].[Store].[cond]}\n"
                        + "Row #0: 565,238.13\n");
    }

    @Test
    void testAggregateDistinctCountInDimensionFilter(Context<?> context) {
        prepareContext(context);
        String query = "With "
                + "Set [Products] as '{[Product].[All Products].[Drink], [Product].[All Products].[Food]}' "
                + "Set [States] as '{[Store].[All Stores].[USA].[CA], [Store].[All Stores].[USA].[OR]}' "
                + "Member [Product].[Selected Products] as 'Aggregate([Products])', SOLVE_ORDER=2 " + "Select "
                + "Filter([States], not IsEmpty([Measures].[Customer Count])) on rows, "
                + "{[Measures].[Customer Count]} on columns " + "From [Sales] "
                + "Where ([Product].[Selected Products])";

        assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid(
                "Axis #0:\n" + "{[Product].[Product].[Selected Products]}\n" + "Axis #1:\n"
                        + "{[Measures].[Customer Count]}\n" + "Axis #2:\n" + "{[Store].[Store].[USA].[CA]}\n"
                        + "{[Store].[Store].[USA].[OR]}\n" + "Row #0: 2,692\n" + "Row #1: 1,036\n");

        String mysqlSql = "select " + "`store`.`store_state` as `c0`, `time_by_day`.`the_year` as `c1`, "
                + "count(distinct `sales_fact_1997`.`customer_id`) as `m0` " + "from "
                + "`sales_fact_1997` as `sales_fact_1997` "
                + "join `store` as `store` on `sales_fact_1997`.`store_id` = `store`.`store_id` "
                + "join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` "
                + "join `product` as `product` on `sales_fact_1997`.`product_id` = `product`.`product_id` "
                + "join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id` "
                + "where " + "`store`.`store_state` in ('CA', 'OR') and "
                + "`time_by_day`.`the_year` = 1997 and "
                + "`product_class`.`product_family` in ('Drink', 'Food') " + "group by "
                + "`store`.`store_state`, `time_by_day`.`the_year`";

        String derbySql = "select " + "\"store\".\"store_state\" as \"c0\", \"time_by_day\".\"the_year\" as \"c1\", "
                + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" " + "from "
                + "\"sales_fact_1997\" as \"sales_fact_1997\" "
                + "join \"store\" as \"store\" on \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\" "
                + "join \"time_by_day\" as \"time_by_day\" on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" "
                + "join \"product\" as \"product\" on \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" "
                + "join \"product_class\" as \"product_class\" on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" "
                + "where "
                + "\"store\".\"store_state\" in ('CA', 'OR') and "
                + "\"time_by_day\".\"the_year\" = 1997 and "
                + "\"product_class\".\"product_family\" in ('Drink', 'Food') " + "group by "
                + "\"store\".\"store_state\", \"time_by_day\".\"the_year\"";

        SqlPattern[] patterns = { new SqlPattern(DatabaseProduct.DERBY, derbySql, derbySql),
                new SqlPattern(DatabaseProduct.MYSQL, mysqlSql, mysqlSql) };

        SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns).verify();
    }

    public static class MyDelegatingInvocationHandler extends DelegatingInvocationHandler {
        private final Dialect dialect;
        private final boolean supportsGroupingSets;

        private MyDelegatingInvocationHandler(Dialect dialect, boolean supportsGroupingSets) {
            this.dialect = dialect;
            this.supportsGroupingSets = supportsGroupingSets;
        }

        @Override
        protected Object getTarget() {
            return dialect;
        }

        /**
         * Handler for {@link Dialect#supportsGroupingSets()}.
         *
         * @return whether dialect supports GROUPING SETS syntax
         */
        public boolean supportsGroupingSets() {
            return supportsGroupingSets;
        }
    }

    @Test
    void testInMemoryAggSum(Context<?> context) throws Exception {
        prepareContext(context);
        // Double arrays
        final Object[] dblSet1 = new Double[] { null, 0.0, 1.1, 2.4 };
        final Object[] dblSet2 = new Double[] { null, null, null };
        final Object[] dblSet3 = new Double[] {};
        final Object[] dblSet4 = new Double[] { 2.7, 1.9 };

        // Arrays of ints
        final Object[] intSet1 = new Integer[] { null, 0, 1, 4 };
        final Object[] intSet2 = new Integer[] { null, null, null };
        final Object[] intSet3 = new Integer[] {};
        final Object[] intSet4 = new Integer[] { 3, 7 };

        // Test with double
        assertEquals(3.5, SumAggregator.INSTANCE.aggregate(Arrays.asList(dblSet1), DataTypeJdbc.NUMERIC));
        assertEquals(null, SumAggregator.INSTANCE.aggregate(Arrays.asList(dblSet2), DataTypeJdbc.NUMERIC));
        List list = Arrays.asList(dblSet3);
        try {
            SumAggregator.INSTANCE.aggregate(list, DataTypeJdbc.NUMERIC);
            fail("Expected an AssertionError!");
        } catch (AssertionError e) {
            assertNotNull(e);
            assertInstanceOf(AssertionError.class, e);
        }
        assertEquals(4.6, SumAggregator.INSTANCE.aggregate(Arrays.asList(dblSet4), DataTypeJdbc.NUMERIC));

        // test with int
        assertEquals(5, SumAggregator.INSTANCE.aggregate(Arrays.asList(intSet1), DataTypeJdbc.INTEGER));
        assertEquals(null, SumAggregator.INSTANCE.aggregate(Arrays.asList(intSet2), DataTypeJdbc.INTEGER));
        List list1 = Arrays.asList(intSet3);
        try {
            SumAggregator.INSTANCE.aggregate(list1, DataTypeJdbc.INTEGER);
            fail();
        } catch (AssertionError e) {
            assertNotNull(e);
            assertInstanceOf(AssertionError.class, e);
        }
        assertEquals(10, SumAggregator.INSTANCE.aggregate(Arrays.asList(intSet4), DataTypeJdbc.INTEGER));
    }

    @Test
    void testInMemoryAggMin(Context<?> context) throws Exception {
        prepareContext(context);
        // Double arrays
        final Object[] dblSet1 = new Double[] { null, 0.0, 1.1, 2.4 };
        final Object[] dblSet2 = new Double[] { null, null, null };
        final Object[] dblSet3 = new Double[] {};
        final Object[] dblSet4 = new Double[] { 2.7, 1.9 };

        // Arrays of ints
        final Object[] intSet1 = new Integer[] { null, 0, 1, 4 };
        final Object[] intSet2 = new Integer[] { null, null, null };
        final Object[] intSet3 = new Integer[] {};
        final Object[] intSet4 = new Integer[] { 3, 7 };

        // Test with double
        assertEquals(0.0, MinAggregator.INSTANCE.aggregate(Arrays.asList(dblSet1), DataTypeJdbc.NUMERIC));
        assertEquals(null, MinAggregator.INSTANCE.aggregate(Arrays.asList(dblSet2), DataTypeJdbc.NUMERIC));
        List list = Arrays.asList(dblSet3);
        try {
            MinAggregator.INSTANCE.aggregate(list, DataTypeJdbc.NUMERIC);
            fail();
        } catch (AssertionError e) {
            assertNotNull(e);
            assertInstanceOf(AssertionError.class, e);
        }
        assertEquals(1.9, MinAggregator.INSTANCE.aggregate(Arrays.asList(dblSet4), DataTypeJdbc.NUMERIC));

        // test with int
        assertEquals(0, MinAggregator.INSTANCE.aggregate(Arrays.asList(intSet1), DataTypeJdbc.INTEGER));
        assertEquals(null, MinAggregator.INSTANCE.aggregate(Arrays.asList(intSet2), DataTypeJdbc.INTEGER));
        List list1 = Arrays.asList(intSet3);
        try {
            MinAggregator.INSTANCE.aggregate(list1, DataTypeJdbc.INTEGER);
            fail();
        } catch (AssertionError e) {
            assertNotNull(e);
            assertInstanceOf(AssertionError.class, e);
        }
        assertEquals(3, MinAggregator.INSTANCE.aggregate(Arrays.asList(intSet4), DataTypeJdbc.INTEGER));
    }

    @Test
    void testInMemoryAggMax(Context<?> context) throws Exception {
        prepareContext(context);
        // Double arrays
        final Object[] dblSet1 = new Double[] { null, 0.0, 1.1, 2.4 };
        final Object[] dblSet2 = new Double[] { null, null, null };
        final Object[] dblSet3 = new Double[] {};
        final Object[] dblSet4 = new Double[] { 2.7, 1.9 };
        final Object[] dblSet5 = new Double[] { -1.2, -3.4 };

        // Arrays of ints
        final Object[] intSet1 = new Integer[] { null, 0, 1, 4 };
        final Object[] intSet2 = new Integer[] { null, null, null };
        final Object[] intSet3 = new Integer[] {};
        final Object[] intSet4 = new Integer[] { 3, 7 };

        // Test with double
        assertEquals(2.4, MaxAggregator.INSTANCE.aggregate(Arrays.asList(dblSet1), DataTypeJdbc.NUMERIC));
        assertEquals(null, MaxAggregator.INSTANCE.aggregate(Arrays.asList(dblSet2), DataTypeJdbc.NUMERIC));
        assertEquals(-1.2, MaxAggregator.INSTANCE.aggregate(Arrays.asList(dblSet5), DataTypeJdbc.NUMERIC));
        List list = Arrays.asList(dblSet3);
        try {
            MaxAggregator.INSTANCE.aggregate(list, DataTypeJdbc.NUMERIC);
            fail();
        } catch (AssertionError e) {
            assertNotNull(e);
            assertInstanceOf(AssertionError.class, e);
        }
        assertEquals(2.7, MaxAggregator.INSTANCE.aggregate(Arrays.asList(dblSet4), DataTypeJdbc.NUMERIC));

        // test with int
        assertEquals(4, MaxAggregator.INSTANCE.aggregate(Arrays.asList(intSet1), DataTypeJdbc.INTEGER));
        assertEquals(null, MaxAggregator.INSTANCE.aggregate(Arrays.asList(intSet2), DataTypeJdbc.INTEGER));
        List list1 = Arrays.asList(intSet3);
        try {
            MaxAggregator.INSTANCE.aggregate(list1, DataTypeJdbc.INTEGER);
            fail();
        } catch (AssertionError e) {
            assertNotNull(e);
            assertInstanceOf(AssertionError.class, e);
        }
        assertEquals(7, MaxAggregator.INSTANCE.aggregate(Arrays.asList(intSet4), DataTypeJdbc.INTEGER));
    }

    /**
     * Tests if UdfResolver processes CellRequestQuantumExceededException. It should
     * be catch in {@mondrian.rolap.RolapResult}. No exceptions should be throw
     * outside
     *
     * @see <a href="http://jira.pentaho.com/browse/MONDRIAN-2251">Jira issue</a>
     */
    @Test
    @RolapConfig(key = ConfigConstants.CELL_BATCH_SIZE, value = "1", type = Integer.class)
    void testCellBatchSizeWithUdf(Context<?> context) {
        prepareContext(context);
        assertThatQuery(connection,
                "select lastnonempty([education level].members, measures.[unit sales]) on 0 from sales").returnsGrid(
                "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Education Level].[Education Level].[Partial High School]}\n"
                        + "Row #0: 79,155\n");
    }

    /**
     * Executes query1 and query2 and Compares the obtained measure values.
     */
    private static void assertQueriesReturnSimilarResults(
        Connection connection, String query1, String query2)
    {
        String resultString1 = toString(Mdx.executeQuery(connection, query1));
        String resultString2 = toString(Mdx.executeQuery(connection, query2));
        assertEquals(measureValues(resultString1), measureValues(resultString2));
    }

    /**
     * Truncates the query result to return only measure values.
     */
    private static String measureValues(String resultString) {
        int index = resultString.indexOf("}");
        return index != -1 ? resultString.substring(index) : resultString;
    }

    /** Named bridge onto the FoodMart CSVs (for the data=-Supplier form). */
    public static class FoodmartData implements org.eclipse.daanse.cwm.testkit.api.DataSupplier {
        @Override
        public java.util.Map<String, java.net.URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

    /**
     * Converts a {@link Result} to text in traditional format.
     *
     * @param result Query result
     * @return Result as text
     */
    private static String toString(Result result) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        result.print(pw);
        pw.flush();
        return sw.toString();
    }
}
