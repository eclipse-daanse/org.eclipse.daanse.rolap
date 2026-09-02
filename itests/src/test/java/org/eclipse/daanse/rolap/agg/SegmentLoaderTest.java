/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 Julian Hyde
 * Copyright (C) 2005-2021 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.agg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.key.CellKey;
import org.eclipse.daanse.rolap.BatchTestCase;
import org.eclipse.daanse.rolap.common.SqlStatement;
import org.eclipse.daanse.rolap.common.agg.GroupingSet;
import org.eclipse.daanse.rolap.common.agg.GroupingSetsList;
import org.eclipse.daanse.rolap.common.agg.Segment;
import org.eclipse.daanse.rolap.common.agg.SegmentAxis;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager;
import org.eclipse.daanse.rolap.common.agg.SegmentLoader;
import org.eclipse.daanse.rolap.common.agg.SegmentWithData;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.CellRequestFixture;
import org.eclipse.daanse.rolap.testkit.assertions.ConfigOverride;
import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.testkit.assertions.Mdx;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.util.DelegatingInvocationHandler;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Test for <code>SegmentLoader</code></p>
 *
 * @author Thiyagu
 * @since 06-Jun-2007
 */
@RolapContextTest(FoodmartTestInstance.class)
class SegmentLoaderTest extends BatchTestCase {

    private ExecutionImpl execution;
    private ExecutionContext executionContext;
    private SegmentCacheManager cacheMgr;
    private Statement statement;

    private void prepareContext(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        cacheMgr =
            (SegmentCacheManager) ((AbstractBasicContext) connection
                .getContext()).getAggregationManager().getCacheMgr();
        statement = ((Connection) connection).getInternalStatement();
        execution = new ExecutionImpl(statement, Optional.of(Duration.ofMillis(1000)));
        executionContext = execution.asContext();
        cacheMgr = (SegmentCacheManager) ((AbstractBasicContext)execution.getDaanseStatement().getDaanseConnection()
            .getContext()).getAggregationManager().getCacheMgr();

        // Note: ExecutionContext.push() removed. Tests should wrap operations in ExecutionContext.where() if needed.
    }



    @BeforeEach
    public void beforeEach() {

    }

    @AfterEach
    protected void AfterEach() throws Exception {
        // Note: LocusImpl.pop() removed
        try {
            statement.cancel();
        } catch (Exception e) {
            // ignore.
        }
        try {
            execution.cancel();
        } catch (Exception e) {
            // ignore.
        }
        statement = null;
        execution = null;
        executionContext = null;
        cacheMgr = null;
    }

    @Disabled //TODO need investigate
    @Test
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "false", type = Boolean.class)
    void testRollup(Context<?> context) {
        prepareContext(context);
        for (boolean rollup : new Boolean[] {true, false}) {
            PrintWriter pw = new PrintWriter(System.out);
            context.getConnectionWithDefaultRole().getCacheControl(pw).flushSchemaCache();
            pw.flush();
            ConfigOverride.of(context).set(ConfigConstants.ENABLE_IN_MEMORY_ROLLUP, rollup);
            final String queryOracle =
                "select \"time_by_day\".\"the_year\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" from \"sales_fact_1997\" \"sales_fact_1997\", \"time_by_day\" \"time_by_day\" where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" group by \"time_by_day\".\"the_year\"";
            final String queryMySQL =
                "select `time_by_day`.`the_year` as `c0`, sum(`sales_fact_1997`.`unit_sales`) as `m0` from `sales_fact_1997` as `sales_fact_1997`, `time_by_day` as `time_by_day` where `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` group by `time_by_day`.`the_year`";
            Mdx.executeQuery(context.getConnectionWithDefaultRole(),
                "select {[Store].[Store Country].Members} on rows, {[Time].[Time].[Year].Members} on columns from [Sales]");
            SqlPattern[] patterns = new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.ORACLE,
                    queryOracle,
                    queryOracle.length()),
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    queryMySQL,
                    queryMySQL.length())
            };
            SqlAssert.QuerySqlAssert sqlAssert = SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
                "select {[Time].[Time].[Year].Members} on columns from [Sales]")
                .keepCache();
            if (rollup) {
                sqlAssert.expectNoSql(patterns);
            } else {
                sqlAssert.expectSql(patterns);
            }
            sqlAssert.verify();
        }
    }

    @Disabled //has not been fixed during creating Daanse project
    @Test
    void testLoadWithMockResultsForLoadingSummaryAndDetailedSegments(Context<?> context) throws ExecutionException, InterruptedException {
        prepareContext(context);
        GroupingSet groupableSetsInfo = getGroupingSetRollupOnGender(context.getConnectionWithDefaultRole());

        GroupingSet groupingSetsInfo = getDefaultGroupingSet(context.getConnectionWithDefaultRole());
        ArrayList<GroupingSet> groupingSets =
            new ArrayList<>();
        groupingSets.add(groupingSetsInfo);
        groupingSets.add(groupableSetsInfo);
        SegmentLoader loader = new SegmentLoader(cacheMgr) {
            @Override
            public
			SqlStatement createExecuteSql(
                int cellRequestCount,
                final GroupingSetsList groupingSetsList,
                List<StarPredicate> compoundPredicateList, boolean useAggregates)
            {
                return new MockSqlStatement(
                    cellRequestCount,
                    groupingSetsList,
                    getData(true));
            }
        };
        final List<Future<Map<Segment, SegmentWithData>>> segmentFutures =
            new ArrayList<>();
        loader.load(0, groupingSets, null, segmentFutures);
        for (Future<?> future : segmentFutures) {
            Util.safeGet(future, "");
        }
        SegmentAxis[] axes = groupingSetsInfo.getAxes();
        verifyYearAxis(axes[0]);
        verifyProductFamilyAxis(axes[1]);
        verifyProductDepartmentAxis(axes[2]);
        verifyGenderAxis(axes[3]);
        verifyUnitSalesDetailed(
            getFor(
                segmentFutures,
                groupingSets.get(0).getSegments().get(0)));

        axes = groupingSets.get(0).getAxes();
        verifyYearAxis(axes[0]);
        verifyProductFamilyAxis(axes[1]);
        verifyProductDepartmentAxis(axes[2]);
        verifyUnitSalesAggregate(
            getFor(
                segmentFutures,
                groupingSets.get(1).getSegments().get(0)));
    }

    private ResultSet toResultSet(final List<Object[]> list) {
        final MyDelegatingInvocationHandler handler =
            new MyDelegatingInvocationHandler(list);
        Object o =
            Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                new Class[] {ResultSet.class, ResultSetMetaData.class},
                handler);
        handler.resultSetMetaData = (ResultSetMetaData) o;
        return (ResultSet) o;
    }

    private SegmentLoader.RowList toRowList2(List<Object[]> list) {
        final SegmentLoader.RowList rowList =
            new SegmentLoader.RowList(
                Collections.nCopies(
                    list.get(0).length,
                    BestFitColumnType.OBJECT));
        for (Object[] objects : list) {
            rowList.createRow();
            for (int i = 0; i < objects.length; i++) {
                Object object = objects[i];
                rowList.setObject(i, object);
            }
        }
        return rowList;
    }

    /**
     * Tests load with mock results for loading summary and detailed
     * segments with null in rollup column.
     */
    @Disabled //has not been fixed during creating Daanse project
    @Test
    void testLoadWithWithNullInRollupColumn(Context<?> context) throws ExecutionException, InterruptedException {
        prepareContext(context);
        GroupingSet groupableSetsInfo = getGroupingSetRollupOnGender(context.getConnectionWithDefaultRole());

        GroupingSet groupingSetsInfo = getDefaultGroupingSet(context.getConnectionWithDefaultRole());
        ArrayList<GroupingSet> groupingSets =
            new ArrayList<>();
        groupingSets.add(groupingSetsInfo);
        groupingSets.add(groupableSetsInfo);
        SegmentLoader loader = new SegmentLoader(cacheMgr) {
            @Override
            public
			SqlStatement createExecuteSql(
                int cellRequestCount,
                GroupingSetsList groupingSetsList,
                List<StarPredicate> compoundPredicateList, boolean useAggregates)
            {
                return new MockSqlStatement(
                    cellRequestCount,
                    groupingSetsList,
                    getDataWithNullInRollupColumn(true));
            }
        };
        final List<Future<Map<Segment, SegmentWithData>>> segmentFutures =
            new ArrayList<>();
        loader.load(0, groupingSets, null, segmentFutures);
        SegmentWithData detailedSegment =
            getFor(
                segmentFutures,
                groupingSets.get(0).getSegments().get(0));
        assertEquals(3, detailedSegment.getCellCount());
    }

    @Disabled //has not been fixed during creating Daanse project
    @Test
    public void
        testLoadWithMockResultsForLoadingSummaryAndDetailedSegmentsUsingSparse(Context<?> context) throws ExecutionException, InterruptedException {
        prepareContext(context);
        GroupingSet groupableSetsInfo = getGroupingSetRollupOnGender(context.getConnectionWithDefaultRole());

        GroupingSet groupingSetsInfo = getDefaultGroupingSet(context.getConnectionWithDefaultRole());
        ArrayList<GroupingSet> groupingSets =
            new ArrayList<>();
        groupingSets.add(groupingSetsInfo);
        groupingSets.add(groupableSetsInfo);
        SegmentLoader loader = new SegmentLoader(cacheMgr) {
            @Override
            public
			SqlStatement createExecuteSql(
                int cellRequestCount,
                GroupingSetsList groupingSetsList,
                List<StarPredicate> compoundPredicateList, boolean useAggregates)
            {
                return new MockSqlStatement(
                    cellRequestCount,
                    groupingSetsList,
                    getData(true));
            }

            @Override
			public boolean useSparse(boolean sparse, int n, RowList rows,
                              int sparseSegmentCountThreshold,
                              double sparseSegmentDensityThreshold) {
                return true;
            }
        };
        final List<Future<Map<Segment, SegmentWithData>>> segmentFutures =
            new ArrayList<>();
        loader.load(0, groupingSets, null, segmentFutures);
        for (Future<?> future : segmentFutures) {
            Util.safeGet(future, "");
        }
        SegmentAxis[] axes = groupingSetsInfo.getAxes();
        verifyYearAxis(axes[0]);
        verifyProductFamilyAxis(axes[1]);
        verifyProductDepartmentAxis(axes[2]);
        verifyGenderAxis(axes[3]);
        verifyUnitSalesDetailedForSparse(
            getFor(
                segmentFutures,
                groupingSets.get(0).getSegments().get(0)));

        axes = groupingSets.get(0).getAxes();
        verifyYearAxis(axes[0]);
        verifyProductFamilyAxis(axes[1]);
        verifyProductDepartmentAxis(axes[2]);
        verifyUnitSalesAggregateForSparse(
            getFor(
                segmentFutures,
                groupingSets.get(1).getSegments().get(0)));
    }

    private SegmentWithData getFor(
        List<Future<Map<Segment, SegmentWithData>>> mapFutures,
        Segment segment)
        throws ExecutionException, InterruptedException
    {
        for (Future<Map<Segment, SegmentWithData>> mapFuture : mapFutures) {
            final Map<Segment, SegmentWithData> map = mapFuture.get();
            if (map.containsKey(segment)) {
                return map.get(segment);
            }
        }
        return null;
    }

    private List<Object[]> trim(final int length, final List<Object[]> data) {
        return new AbstractList<>() {
            @Override
            public Object[] get(int index) {
                return Arrays.copyOf(data.get(index), length);
            }

            @Override
            public int size() {
                return data.size();
            }
        };
    }

    @Disabled //has not been fixed during creating Daanse project
    @Test
    void testLoadWithMockResultsForLoadingOnlyDetailedSegments(Context<?> context) throws ExecutionException,
            InterruptedException {
        prepareContext(context);
        GroupingSet groupingSetsInfo = getDefaultGroupingSet(context.getConnectionWithDefaultRole());
        ArrayList<GroupingSet> groupingSets =
            new ArrayList<>();
        groupingSets.add(groupingSetsInfo);
        SegmentLoader loader = new SegmentLoader(cacheMgr) {
            @Override
            public
			SqlStatement createExecuteSql(
                int cellRequestCount,
                GroupingSetsList groupingSetsList,
                List<StarPredicate> compoundPredicateList, boolean useAggregates)
            {
                return new MockSqlStatement(
                    cellRequestCount,
                    groupingSetsList,
                    trim(5, getData(false)));
            }
        };
        final List<Future<Map<Segment, SegmentWithData>>> segmentFutures =
            new ArrayList<>();
        loader.load(0, groupingSets, null, segmentFutures);
        for (Future<?> future : segmentFutures) {
            Util.safeGet(future, "");
        }
        SegmentAxis[] axes = groupingSetsInfo.getAxes();
        verifyYearAxis(axes[0]);
        verifyProductFamilyAxis(axes[1]);
        verifyProductDepartmentAxis(axes[2]);
        verifyGenderAxis(axes[3]);
        verifyUnitSalesDetailed(
            getFor(
                segmentFutures,
                groupingSetsInfo.getSegments().get(0)));
    }

    @Test
    public void
        testProcessDataForGettingGroupingSetsBitKeysAndLoadingAxisValueSet(Context<?> context) throws SQLException {
        prepareContext(context);
        GroupingSet groupableSetsInfo = getGroupingSetRollupOnGender(context.getConnectionWithDefaultRole());

        GroupingSet groupingSetsInfo = getDefaultGroupingSet(context.getConnectionWithDefaultRole());

        List<GroupingSet> groupingSets = new ArrayList<>();
        groupingSets.add(groupingSetsInfo);
        groupingSets.add(groupableSetsInfo);

        final SqlStatement stmt =
            new MockSqlStatement(
                0,
                new GroupingSetsList(groupingSets),
                getData(true));
        SegmentLoader loader = new SegmentLoader(cacheMgr) {
            @Override
            public
            SqlStatement createExecuteSql(
                int cellRequestCount,
                GroupingSetsList groupingSetsList,
                List<StarPredicate> compoundPredicateList, boolean useAggregates)
            {
                return stmt;
            }
        };
        int axisCount = 4;
        SortedSet<Comparable>[] axisValueSet =
            loader.getDistinctValueWorkspace(axisCount);
        boolean[] axisContainsNull = new boolean[axisCount];
        ExecutionContext.where(executionContext, () -> {
		    try {
			    SegmentLoader.RowList list = loader.processData(
                    stmt,
                    axisContainsNull,
                    axisValueSet,
                    new GroupingSetsList(groupingSets));
	            int totalNoOfRows = 12;
	            int lengthOfRowWithBitKey = 6;
	            assertEquals(totalNoOfRows, list.size());
	            assertEquals(lengthOfRowWithBitKey, list.getTypes().size());
	            list.first();
	            list.next();
	            assertEquals(BitKey.Factory.makeBitKey(0), list.getObject(5));

	            BitKey bitKeyForSummaryRow = BitKey.Factory.makeBitKey(0);
	            bitKeyForSummaryRow.set(0);
	            list.next();
	            list.next();
	            assertEquals(bitKeyForSummaryRow, list.getObject(5));
		    } catch (SQLException e) {
			    new RuntimeException(e.getMessage(), e);
		    }
        });
        SortedSet<Comparable> yearAxis = axisValueSet[0];
        assertEquals(1, yearAxis.size());
        SortedSet<Comparable> productFamilyAxis = axisValueSet[1];
        assertEquals(3, productFamilyAxis.size());
        SortedSet<Comparable> productDepartmentAxis = axisValueSet[2];
        assertEquals(4, productDepartmentAxis.size());
        SortedSet<Comparable> genderAxis = axisValueSet[3];
        assertEquals(2, genderAxis.size());

        assertFalse(axisContainsNull[0]);
        assertFalse(axisContainsNull[1]);
        assertFalse(axisContainsNull[2]);
        assertFalse(axisContainsNull[3]);
    }

    private GroupingSet getGroupingSetRollupOnGender(Connection connection) {
        return CellRequestFixture.of(connection).groupingSet()
            .cube(cubeNameSales).measure(measureUnitSales)
            .where(tableTime, fieldYear, fieldValuesYear)
            .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
            .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
            .build();
    }

    @Test
    void testProcessDataForSettingNullAxis(Context<?> context)
        throws SQLException
    {
        prepareContext(context);
        GroupingSet groupingSetsInfo = getDefaultGroupingSet(context.getConnectionWithDefaultRole());

        final SqlStatement stmt =
            new MockSqlStatement(
                0,
                new GroupingSetsList(
                    Collections.singletonList(groupingSetsInfo)),
                trim(5, getDataWithNullInAxisColumn(false)));
        SegmentLoader loader = new SegmentLoader(cacheMgr) {
            @Override
            public
            SqlStatement createExecuteSql(
                int cellRequestCount,
                GroupingSetsList groupingSetsList,
                List<StarPredicate> compoundPredicateList,
                boolean useAggregates)
            {
                return stmt;
            }
        };
        int axisCount = 4;
        SortedSet<Comparable>[] axisValueSet =
            loader.getDistinctValueWorkspace(axisCount);
        boolean[] axisContainsNull = new boolean[axisCount];
        List<GroupingSet> groupingSets = new ArrayList<>();
        groupingSets.add(groupingSetsInfo);
        ExecutionContext.where(executionContext, () -> {
            try {
			    loader.processData(
			        stmt,
			        axisContainsNull,
			        axisValueSet,
			        new GroupingSetsList(groupingSets));
		    } catch (SQLException e) {
			    new RuntimeException(e.getMessage(), e);
		    }
        });

        assertFalse(axisContainsNull[0]);
        assertFalse(axisContainsNull[1]);
        assertTrue(axisContainsNull[2]);
        assertFalse(axisContainsNull[3]);
    }

    // PDI-16150
    @Test
    void testProcessBinaryData(Context<?> context)
            throws SQLException
    {
        prepareContext(context);
        GroupingSet groupingSetsInfo = getDefaultGroupingSet(context.getConnectionWithDefaultRole());

        String binaryData0 = "11011";
        String binaryData1 = "01011";

        final List<Object[]> data = new ArrayList<>();
        data.add(new Object[]{binaryData0.getBytes()});
        data.add(new Object[]{binaryData1.getBytes()});


        final SqlStatement stmt =
                new MockSqlStatement(
                        0,
                        new GroupingSetsList(
                                Collections.singletonList(groupingSetsInfo)),
                        trim(5, data));
        SegmentLoader loader = new SegmentLoader(cacheMgr) {
            @Override
            public
            SqlStatement createExecuteSql(
                    int cellRequestCount,
                    GroupingSetsList groupingSetsList,
                    List<StarPredicate> compoundPredicateList,
                    boolean useAggregates)
            {
                return stmt;
            }
        };
        int axisCount = 2;
        SortedSet<Comparable>[] axisValueSet =
                loader.getDistinctValueWorkspace(axisCount);
        boolean[] axisContainsNull = new boolean[axisCount];
        List<GroupingSet> groupingSets = new ArrayList<>();
        groupingSets.add(groupingSetsInfo);
        ExecutionContext.where(executionContext, () -> {
        	try {
        		loader.processData(
        				stmt,
        				axisContainsNull,
        				axisValueSet,
        				new GroupingSetsList(groupingSets));
        	} catch (SQLException e) {
        		new RuntimeException(e.getMessage(), e);
        	}
        });
        Object[] values = axisValueSet[0].toArray();
        assertEquals( binaryData0, values[1] );
        assertEquals( binaryData1, values[0] );

    }

    @Test
    void testProcessDataForNonGroupingSetsScenario(Context<?> context)
        throws SQLException
    {
        prepareContext(context);
        GroupingSet groupingSetsInfo = getDefaultGroupingSet(context.getConnectionWithDefaultRole());
        final List<Object[]> data = new ArrayList<>();
        data.add(new Object[]{"1997", "Food", "Deli", "F", "5990"});
        data.add(new Object[]{"1997", "Food", "Deli", "M", "6047"});
        data.add(new Object[]{"1997", "Food", "Canned_Products", "F", "867"});
        final SqlStatement stmt =
            new MockSqlStatement(
                0,
                new GroupingSetsList(
                    Collections.singletonList(groupingSetsInfo)),
                data);
        SegmentLoader loader = new SegmentLoader(cacheMgr) {
            @Override
            public
            SqlStatement createExecuteSql(
                int cellRequestCount,
                GroupingSetsList groupingSetsList,
                List<StarPredicate> compoundPredicateList,
                boolean useAggregates)
            {
                return stmt;
            }
        };
        List<GroupingSet> groupingSets = new ArrayList<>();
        groupingSets.add(groupingSetsInfo);

        SortedSet<Comparable>[] axisValueSet =
            loader.getDistinctValueWorkspace(4);
        ExecutionContext.where(executionContext, () -> {
        	SegmentLoader.RowList list;
        	try {
        		list = loader.processData(
        				stmt,
        				new boolean[4],
        				axisValueSet,
        				new GroupingSetsList(groupingSets));
        		int totalNoOfRows = 3;
        		assertEquals(totalNoOfRows, list.size());
        		int lengthOfRowWithoutBitKey = 5;
        		assertEquals(lengthOfRowWithoutBitKey, list.getTypes().size());
        	} catch (SQLException e) {
        		new RuntimeException(e.getMessage(), e);
        	}
        });
        SortedSet<Comparable> yearAxis = axisValueSet[0];
        assertEquals(1, yearAxis.size());
        SortedSet<Comparable> productFamilyAxis = axisValueSet[1];
        assertEquals(1, productFamilyAxis.size());
        SortedSet<Comparable> productDepartmentAxis = axisValueSet[2];
        assertEquals(2, productDepartmentAxis.size());
        SortedSet<Comparable> genderAxis = axisValueSet[3];
        assertEquals(2, genderAxis.size());
    }

    private void verifyUnitSalesDetailed(SegmentWithData segment) {
        Double[] unitSalesValues = {
            null, null, null, null, 1987.0, 2199.0,
            null, null, 867.0, 945.0, null, null, null, null, 5990.0,
            6047.0, null, null, 368.0, 473.0, null, null, null, null
        };
        int index = 0;
        for (Map.Entry<CellKey, Object> x : segment.getData()) {
            assertEquals(unitSalesValues[index++], x.getValue());
        }
    }

    private void verifyUnitSalesDetailedForSparse(SegmentWithData segment) {
        Map<CellKey, Double> cells = new HashMap<>();
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 2, 1, 0}),
            368.0);
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 0, 2, 0}),
            1987.0);
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 1, 0, 0}),
            867.0);
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 2, 1, 1}),
            473.0);
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 1, 0, 1}),
            945.0);
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 1, 3, 0}),
            5990.0);
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 0, 2, 1}),
            2199.0);
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 1, 3, 1}),
            6047.0);

        for (Map.Entry<CellKey, Object> x : segment.getData()) {
            assertTrue(cells.containsKey(x.getKey()));
            assertEquals(cells.get(x.getKey()), x.getValue());
        }
    }

    private void verifyUnitSalesAggregateForSparse(SegmentWithData segment) {
        Map<CellKey, Double> cells = new HashMap<>();
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 2, 1}),
            841.0);
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 1, 0}),
            1812.0);
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 1, 3}),
            12037.0);
        cells.put(
            CellKey.Generator.newCellKey(new int[]{0, 0, 2}),
            4186.0);

        for (Map.Entry<CellKey, Object> x : segment.getData()) {
            assertTrue(cells.containsKey(x.getKey()));
            assertEquals(cells.get(x.getKey()), x.getValue());
        }
    }

    private void verifyUnitSalesAggregate(SegmentWithData segment) {
        Double[] unitSalesValues = {
            null, null, 4186.0, null, 1812.0, null,
            null, 12037.0, null, 841.0, null, null
        };
        int index = 0;
        for (Map.Entry<CellKey, Object> x : segment.getData()) {
            assertEquals(unitSalesValues[index++], x.getValue());
        }
    }

    @Disabled //has not been fixed during creating Daanse project
    @Test
    void testGetGroupingBitKey(Context<?> context) throws SQLException {
        prepareContext(context);
        Object[] data = {
            "1997", "Food", "Deli", "M", "6047", 0, 0, 0, 0
        };
        ResultSet rowList =
            toResultSet(Collections.singletonList(data));
        assertTrue(rowList.next());
        assertEquals(
            BitKey.Factory.makeBitKey(4),
            new SegmentLoader(cacheMgr).getRollupBitKey(4, rowList, 5));

        data = new Object[]{
            "1997", "Food", "Deli", null, "12037", 0, 0, 0, 1
        };
        rowList = toResultSet(Collections.singletonList(data));
        BitKey key = BitKey.Factory.makeBitKey(4);
        key.set(3);
        assertEquals(
            key,
            new SegmentLoader(cacheMgr).getRollupBitKey(4, rowList, 5));

        data = new Object[] {
            "1997", null, "Deli", null, "12037", 0, 1, 0, 1
        };
        rowList = toResultSet(Collections.singletonList(data));
        key = BitKey.Factory.makeBitKey(4);
        key.set(1);
        key.set(3);
        assertEquals(
            key,
            new SegmentLoader(cacheMgr).getRollupBitKey(4, rowList, 5));
    }

    @Test
    void testGroupingSetsUtilForMissingGroupingBitKeys(Context<?> context) {
        prepareContext(context);
        List<GroupingSet> groupingSets = new ArrayList<>();
        groupingSets.add(getDefaultGroupingSet(context.getConnectionWithDefaultRole()));
        groupingSets.add(getGroupingSetRollupOnGender(context.getConnectionWithDefaultRole()));
        GroupingSetsList detail = new GroupingSetsList(groupingSets);

        List<BitKey> bitKeysList = detail.getRollupColumnsBitKeyList();
        int columnsCount = 4;
        assertEquals(
            BitKey.Factory.makeBitKey(columnsCount),
            bitKeysList.get(0));
        BitKey key = BitKey.Factory.makeBitKey(columnsCount);
        key.set(0);
        assertEquals(key, bitKeysList.get(1));

        groupingSets = new ArrayList<>();
        groupingSets.add(getDefaultGroupingSet(context.getConnectionWithDefaultRole()));
        groupingSets.add(getGroupingSetRollupOnGenderAndProductFamily(context.getConnectionWithDefaultRole()));
        bitKeysList = new GroupingSetsList(groupingSets)
            .getRollupColumnsBitKeyList();
        assertEquals(
            BitKey.Factory.makeBitKey(columnsCount),
            bitKeysList.get(0));
        key = BitKey.Factory.makeBitKey(columnsCount);
        key.set(0);
        key.set(1);
        assertEquals(key, bitKeysList.get(1));

        List<BitKey> list = new GroupingSetsList(
                new ArrayList<GroupingSet>())
                .getRollupColumnsBitKeyList();
        assertTrue(list.size() == 1 && list.get(0).equals(BitKey.EMPTY));
    }

    private GroupingSet getGroupingSetRollupOnGenderAndProductFamily(Connection connection) {
        return CellRequestFixture.of(connection).groupingSet()
            .cube(cubeNameSales).measure(measureUnitSales)
            .where(tableTime, fieldYear, fieldValuesYear)
            .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
            .build();
    }

    @Test
    void testGroupingSetsUtilSetsDetailForRollupColumns(Context<?> context) {
        prepareContext(context);
        RolapStar.Measure measure = getMeasure(context.getConnectionWithDefaultRole(), cubeNameSales, measureUnitSales);
        RolapStar star = measure.getStar();
        RolapStar.Column year = star.lookupColumn(tableTime, fieldYear);
        RolapStar.Column productFamily =
            star.lookupColumn(tableProductClass, fieldProductFamily);
        RolapStar.Column productDepartment =
            star.lookupColumn(tableProductClass, fieldProductDepartment);
        RolapStar.Column gender = star.lookupColumn(tableCustomer, fieldGender);

        List<GroupingSet> groupingSets = new ArrayList<>();
        groupingSets.add(getDefaultGroupingSet(context.getConnectionWithDefaultRole()));
        groupingSets.add(getGroupingSetRollupOnProductDepartment(context.getConnectionWithDefaultRole()));
        groupingSets.add(getGroupingSetRollupOnGenderAndProductDepartment(context.getConnectionWithDefaultRole()));
        GroupingSetsList detail = new GroupingSetsList(groupingSets);

        List<RolapStar.Column> rollupColumnsList = detail.getRollupColumns();
        assertEquals(2, rollupColumnsList.size());
        assertEquals(gender, rollupColumnsList.get(0));
        assertEquals(productDepartment, rollupColumnsList.get(1));

        groupingSets
            .add(getGroupingSetRollupOnGenderAndProductDepartmentAndYear(context.getConnectionWithDefaultRole()));
        detail = new GroupingSetsList(groupingSets);
        rollupColumnsList = detail.getRollupColumns();
        assertEquals(3, rollupColumnsList.size());
        assertEquals(gender, rollupColumnsList.get(0));
        assertEquals(productDepartment, rollupColumnsList.get(1));
        assertEquals(year, rollupColumnsList.get(2));

        groupingSets
            .add(getGroupingSetRollupOnProductFamilyAndProductDepartment(context.getConnectionWithDefaultRole()));
        detail = new GroupingSetsList(groupingSets);
        rollupColumnsList = detail.getRollupColumns();
        assertEquals(4, rollupColumnsList.size());
        assertEquals(gender, rollupColumnsList.get(0));
        assertEquals(productDepartment, rollupColumnsList.get(1));
        assertEquals(productFamily, rollupColumnsList.get(2));
        assertEquals(year, rollupColumnsList.get(3));

        assertTrue(
            new GroupingSetsList(new ArrayList<GroupingSet>())
            .getRollupColumns().isEmpty());
    }

    private GroupingSet getGroupingSetRollupOnGenderAndProductDepartment(Connection connection) {
        return CellRequestFixture.of(connection).groupingSet()
            .cube(cubeNameSales).measure(measureUnitSales)
            .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
            .where(tableTime, fieldYear, fieldValuesYear)
            .build();
    }

    private GroupingSet
        getGroupingSetRollupOnProductFamilyAndProductDepartment(Connection connection)
    {
        return CellRequestFixture.of(connection).groupingSet()
            .cube(cubeNameSales).measure(measureUnitSales)
            .where(tableCustomer, fieldGender, fieldValuesGender)
            .where(tableTime, fieldYear, fieldValuesYear)
            .build();
    }

    private GroupingSet
        getGroupingSetRollupOnGenderAndProductDepartmentAndYear(Connection connection)
    {
        return CellRequestFixture.of(connection).groupingSet()
            .cube(cubeNameSales).measure(measureUnitSales)
            .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
            .build();
    }

    private GroupingSet getGroupingSetRollupOnProductDepartment(Connection connection) {
        return CellRequestFixture.of(connection).groupingSet()
            .cube(cubeNameSales).measure(measureUnitSales)
            .where(tableCustomer, fieldGender, fieldValuesGender)
            .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
            .where(tableTime, fieldYear, fieldValuesYear)
            .build();
    }

    @Test
    void testGroupingSetsUtilSetsForDetailForRollupColumns(Context<?> context) {
        prepareContext(context);
        RolapStar.Measure measure = getMeasure(context.getConnectionWithDefaultRole(), cubeNameSales, measureUnitSales);
        RolapStar star = measure.getStar();
        RolapStar.Column year = star.lookupColumn(tableTime, fieldYear);
        RolapStar.Column productFamily =
            star.lookupColumn(tableProductClass, fieldProductFamily);
        RolapStar.Column productDepartment =
            star.lookupColumn(tableProductClass, fieldProductDepartment);
        RolapStar.Column gender = star.lookupColumn(tableCustomer, fieldGender);

        List<GroupingSet> groupingSets = new ArrayList<>();
        groupingSets.add(getDefaultGroupingSet(context.getConnectionWithDefaultRole()));
        groupingSets.add(getGroupingSetRollupOnProductDepartment(context.getConnectionWithDefaultRole()));
        groupingSets.add(getGroupingSetRollupOnGenderAndProductDepartment(context.getConnectionWithDefaultRole()));
        GroupingSetsList detail = new GroupingSetsList(groupingSets);

        List<RolapStar.Column> rollupColumnsList = detail.getRollupColumns();
        assertEquals(2, rollupColumnsList.size());
        assertEquals(gender, rollupColumnsList.get(0));
        assertEquals(productDepartment, rollupColumnsList.get(1));

        groupingSets
            .add(getGroupingSetRollupOnGenderAndProductDepartmentAndYear(context.getConnectionWithDefaultRole()));
        detail = new GroupingSetsList(groupingSets);
        rollupColumnsList = detail.getRollupColumns();
        assertEquals(3, rollupColumnsList.size());
        assertEquals(gender, rollupColumnsList.get(0));
        assertEquals(productDepartment, rollupColumnsList.get(1));
        assertEquals(year, rollupColumnsList.get(2));

        groupingSets
            .add(getGroupingSetRollupOnProductFamilyAndProductDepartment(context.getConnectionWithDefaultRole()));
        detail = new GroupingSetsList(groupingSets);
        rollupColumnsList = detail.getRollupColumns();
        assertEquals(4, rollupColumnsList.size());
        assertEquals(gender, rollupColumnsList.get(0));
        assertEquals(productDepartment, rollupColumnsList.get(1));
        assertEquals(productFamily, rollupColumnsList.get(2));
        assertEquals(year, rollupColumnsList.get(3));

        assertTrue(
            new GroupingSetsList(new ArrayList<GroupingSet>())
            .getRollupColumns().isEmpty());
    }

    @Test
    void testGroupingSetsUtilSetsForGroupingFunctionIndex(Context<?> context) {
        prepareContext(context);
        List<GroupingSet> groupingSets = new ArrayList<>();
        groupingSets.add(getDefaultGroupingSet(context.getConnectionWithDefaultRole()));
        groupingSets.add(getGroupingSetRollupOnProductDepartment(context.getConnectionWithDefaultRole()));
        groupingSets.add(getGroupingSetRollupOnGenderAndProductDepartment(context.getConnectionWithDefaultRole()));
        GroupingSetsList detail = new GroupingSetsList(groupingSets);
        assertEquals(0, detail.findGroupingFunctionIndex(3));
        assertEquals(1, detail.findGroupingFunctionIndex(2));

        groupingSets
            .add(getGroupingSetRollupOnGenderAndProductDepartmentAndYear(context.getConnectionWithDefaultRole()));
        detail = new GroupingSetsList(groupingSets);
        assertEquals(0, detail.findGroupingFunctionIndex(3));
        assertEquals(1, detail.findGroupingFunctionIndex(2));
        assertEquals(2, detail.findGroupingFunctionIndex(0));

        groupingSets
            .add(getGroupingSetRollupOnProductFamilyAndProductDepartment(context.getConnectionWithDefaultRole()));
        detail = new GroupingSetsList(groupingSets);
        assertEquals(0, detail.findGroupingFunctionIndex(3));
        assertEquals(1, detail.findGroupingFunctionIndex(2));
        assertEquals(2, detail.findGroupingFunctionIndex(1));
        assertEquals(3, detail.findGroupingFunctionIndex(0));
    }

    @Test
    void testGetGroupingColumnsList(Context<?> context) {
        prepareContext(context);
        GroupingSet groupingSetsInfo = getDefaultGroupingSet(context.getConnectionWithDefaultRole());

        GroupingSet groupableSetsInfo = getGroupingSetRollupOnGender(context.getConnectionWithDefaultRole());


        RolapStar.Column[] detailedColumns =
            groupingSetsInfo.getSegments().get(0).getColumns();
        RolapStar.Column[] summaryColumns =
            groupableSetsInfo.getSegments().get(0).getColumns();
        List<GroupingSet> groupingSets = new ArrayList<>();
        groupingSets.add(groupingSetsInfo);
        groupingSets.add(groupableSetsInfo);

        List<RolapStar.Column[]> groupingColumns =
            new GroupingSetsList(groupingSets).getGroupingSetsColumns();
        assertEquals(2, groupingColumns.size());
        assertEquals(detailedColumns, groupingColumns.get(0));
        assertEquals(summaryColumns, groupingColumns.get(1));

        groupingColumns = new GroupingSetsList(
            new ArrayList<GroupingSet>()).getGroupingSetsColumns();
        assertEquals(0, groupingColumns.size());
    }

    private GroupingSet getDefaultGroupingSet(Connection connection) {
        return CellRequestFixture.of(connection).groupingSet()
            .cube(cubeNameSales).measure(measureUnitSales)
            .where(tableCustomer, fieldGender, fieldValuesGender)
            .where(tableProductClass, fieldProductDepartment, fieldValueProductDepartment)
            .where(tableProductClass, fieldProductFamily, fieldValuesProductFamily)
            .where(tableTime, fieldYear, fieldValuesYear)
            .build();
    }

    private void verifyYearAxis(SegmentAxis axis) {
        Comparable[] keys = axis.getKeys();
        assertEquals(1, keys.length);
        assertEquals("1997", keys[0].toString());
    }

    private void verifyProductFamilyAxis(SegmentAxis axis) {
        Comparable[] keys = axis.getKeys();
        assertEquals(3, keys.length);
        assertEquals("Drink", keys[0].toString());
        assertEquals("Food", keys[1].toString());
        assertEquals("Non-Consumable", keys[2].toString());
    }

    private void verifyProductDepartmentAxis(SegmentAxis axis) {
        Comparable[] keys = axis.getKeys();
        assertEquals(4, keys.length);
        assertEquals("Canned_Products", keys[0].toString());
    }

    private void verifyGenderAxis(SegmentAxis axis) {
        Comparable[] keys = axis.getKeys();
        assertEquals(2, keys.length);
        assertEquals("F", keys[0].toString());
        assertEquals("M", keys[1].toString());
    }

    private List<Object[]> getData(boolean incSummaryData) {
        List<Object[]> data = new ArrayList<>();
        data.add(new Object[]{"1997", "Food", "Deli", "F", "5990", 0});
        data.add(new Object[]{"1997", "Food", "Deli", "M", "6047", 0});
        if (incSummaryData) {
            data.add(new Object[]{"1997", "Food", "Deli", null, "12037", 1});
        }
        data.add(
            new Object[]{"1997", "Food", "Canned_Products", "F", "867", 0});
        data.add(
            new Object[]{"1997", "Food", "Canned_Products", "M", "945", 0});
        if (incSummaryData) {
            data.add(
                new Object[]{
                    "1997", "Food", "Canned_Products", null, "1812", 1});
        }
        data.add(new Object[]{"1997", "Drink", "Dairy", "F", "1987", 0});
        data.add(new Object[]{"1997", "Drink", "Dairy", "M", "2199", 0});
        if (incSummaryData) {
            data.add(new Object[]{"1997", "Drink", "Dairy", null, "4186", 1});
        }
        data.add(
            new Object[]{
                "1997", "Non-Consumable", "Carousel", "F", "368", 0});
        data.add(
            new Object[]{
                "1997", "Non-Consumable", "Carousel", "M", "473", 0});
        if (incSummaryData) {
            data.add(
                new Object[]{
                    "1997", "Non-Consumable", "Carousel", null, "841", 1});
        }
        return data;
    }

    private List<Object[]> getDataWithNullInRollupColumn(
        boolean incSummaryData)
    {
        List<Object[]> data = new ArrayList<>();
        data.add(new Object[]{"1997", "Food", "Deli", "F", "5990", 0});
        data.add(new Object[]{"1997", "Food", "Deli", "M", "6047", 0});
        data.add(new Object[]{"1997", "Food", "Deli", null, "867", 0});
        if (incSummaryData) {
            data.add(new Object[]{"1997", "Food", "Deli", null, "12037", 1});
        }
        return data;
    }

    private List<Object[]> getDataWithNullInAxisColumn(
        boolean incSummaryData)
    {
        List<Object[]> data = new ArrayList<>();
        data.add(new Object[]{"1997", "Food", "Deli", "F", "5990", 0});
        data.add(new Object[]{"1997", "Food", "Deli", "M", "6047", 0});
        if (incSummaryData) {
            data.add(new Object[]{"1997", "Food", "Deli", null, "12037", 1});
        }
        data.add(
            new Object[]{"1997", "Food", null, "F", "867", 0});
        return data;
    }

    public static class MyDelegatingInvocationHandler
        extends DelegatingInvocationHandler
    {
        int row;
        public boolean wasNull;
        ResultSetMetaData resultSetMetaData;
        private final List<Object[]> list;

        public MyDelegatingInvocationHandler(List<Object[]> list) {
            this.list = list;
            row = -1;
        }

        @Override
		protected Object getTarget() {
            return null;
        }

        public ResultSetMetaData getMetaData() {
            return resultSetMetaData;
        }

        // implement ResultSetMetaData
        public int getColumnCount() {
            return list.get(0).length;
        }

        // implement ResultSetMetaData
        public int getColumnType(int column) {
            return Types.VARCHAR;
        }

        public boolean next() {
            if (row < list.size() - 1) {
                ++row;
                return true;
            }
            return false;
        }

        public Object getObject(int column) {
            return list.get(row)[column - 1];
        }

        public int getInt(int column) {
            final Object o = list.get(row)[column - 1];
            if (o == null) {
                wasNull = true;
                return 0;
            } else {
                wasNull = false;
                return ((Number) o).intValue();
            }
        }

        public double getDouble(int column) {
            final Object o = list.get(row)[column - 1];
            if (o == null) {
                wasNull = true;
                return 0D;
            } else {
                wasNull = false;
                return ((Number) o).doubleValue();
            }
        }

        public boolean wasNull() {
            return wasNull;
        }
    }

    private class MockSqlStatement extends SqlStatement {
        private final List<Object[]> data;

        public MockSqlStatement(
            int cellRequestCount,
            GroupingSetsList groupingSetsList,
            List<Object[]> data)
        {
            super(
                //groupingSetsList.getStar().getDataSource(),
                //TODO Commented by reason context implementation
                null,
                "",
                null,
                cellRequestCount,
                0,
                SegmentLoaderTest.this.executionContext,
                0,
                0,
                null);
            this.data = data;
        }

        @Override
        public List<BestFitColumnType> guessTypes() throws SQLException {
            return Collections.nCopies(
                getResultSet().getMetaData().getColumnCount(),
                BestFitColumnType.OBJECT);
        }

        @Override
        public ResultSet getResultSet() {
            return toResultSet(data);
        }

        @Override
        public void close() {

        }
    }
}
