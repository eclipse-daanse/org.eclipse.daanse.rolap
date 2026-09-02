/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
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

import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct.getDatabaseProduct;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.eclipse.daanse.rolap.testkit.assertions.FlushSchemaCacheModifier.flushSchemaCache;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.agg.ValueColumnPredicate;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.result.FastBatchingCellReader;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.testkit.assertions.CellRequestFixture;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;

/**
 * Unit test for {@link AggregationManager}.
 *
 * @author jhyde
 * @since 21 March, 2002
 */
@Execution(ExecutionMode.SAME_THREAD)
@RolapContextTest(FoodmartTestInstance.class)
class TestAggregationManager extends BatchTestCase {

    public static class FoodmartData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

    private static final Set<DatabaseProduct> ACCESS_MYSQL =
    		EnumSet.of(
            DatabaseProduct.ACCESS,
            DatabaseProduct.MYSQL);

    private ExecutionContext executionContext;
    private ExecutionImpl execution;
    private AggregationManager aggMgr;



    @BeforeEach
    void beforeEach() {

    }

    @AfterEach
    void afterEach() {
        // Note: ExecutionContext.pop() removed.

        // allow gc
        executionContext = null;
        execution = null;
        aggMgr = null;
    }

    private void prepareContext(Context<?> context) {
        final Statement statement =
            ((Connection) context.getConnectionWithDefaultRole())
                .getInternalStatement();
        execution = new ExecutionImpl(statement, Optional.empty());
        aggMgr =
            (AggregationManager)((AbstractBasicContext)execution.getDaanseStatement()
                .getDaanseConnection()
                .getContext()).getAggregationManager();
        ExecutionMetadata metadata = ExecutionMetadata.of("TestAggregationManager", "TestAggregationManager", null, 0);
        executionContext = execution.asContext().createChild(metadata, Optional.empty());
        // Note: ExecutionContext.push() removed. Wrap operations in ExecutionContext.where() if needed.
    }

    @Test
    void testFemaleUnitSales(Context<?> context) {
        prepareContext(context);
        Connection connection = context.getConnectionWithDefaultRole();
        final FastBatchingCellReader fbcr =
            new FastBatchingCellReader(execution, getCube(connection,"Sales"), aggMgr);
        CellRequest request = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Unit Sales]").where("customer", "gender", "F").build();
        Object value = aggMgr.getCellFromCache(request);
        assertNull(value); // before load, the cell is not found
        fbcr.recordCellRequest(request);
        ExecutionContext.where(executionContext, () -> {
            fbcr.loadAggregations();
        });
        value = aggMgr.getCellFromCache(request); // after load, cell found
        assertTrue(value instanceof Number);
        assertEquals(131558, ((Number) value).intValue());
    }

    @Test
    void testFemaleCustomerCount(Context<?> context) {
        prepareContext(context);
        final FastBatchingCellReader fbcr =
            new FastBatchingCellReader(execution, getCube(context.getConnectionWithDefaultRole(), "Sales"), aggMgr);
        CellRequest request =
            CellRequestFixture.of(context.getConnectionWithDefaultRole()).request()
                .cube("Sales").measure("[Measures].[Customer Count]")
                .where("customer", "gender", "F").build();
        Object value = aggMgr.getCellFromCache(request);
        assertNull(value); // before load, the cell is not found
        fbcr.recordCellRequest(request);
        ExecutionContext.where(executionContext, () -> {
            fbcr.loadAggregations();
        });
        value = aggMgr.getCellFromCache(request); // after load, cell found
        assertTrue(value instanceof Number);
        assertEquals(2755, ((Number) value).intValue());
    }

    @Test
    void testFemaleCustomerCountWithConstraints(Context<?> context) {
        prepareContext(context);
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 =
            CellRequestFixture.of(connection).request()
                .cube("Sales").measure("[Measures].[Customer Count]")
                .where("customer", "gender", "F")
                .constrain(CellRequestFixture.Constraint.yearQuarterMonth(
                    new String[] {"1997", "Q1", "1"})).build();

        CellRequest request2 =
            CellRequestFixture.of(connection).request()
                .cube("Sales").measure("[Measures].[Customer Count]")
                .where("customer", "gender", "F")
                .constrain(CellRequestFixture.Constraint.yearQuarterMonth(
                    new String[] {"1997", "Q2", "5"})).build();

        CellRequest request3 =
            CellRequestFixture.of(connection).request()
                .cube("Sales").measure("[Measures].[Customer Count]")
                .where("customer", "gender", "F")
                .constrain(CellRequestFixture.Constraint.yearQuarterMonth(
                    new String[] {"1997", "Q1", "1"}, new String[] {"1997", "Q2", "5"})).build();

        FastBatchingCellReader fbcr =
            new FastBatchingCellReader(execution, getCube(connection, "Sales"), aggMgr);

        Object value = aggMgr.getCellFromCache(request1);
        assertNull(value); // before load, the cell is not found

        ExecutionContext.where(executionContext, () -> {
            fbcr.recordCellRequest(request1);
            fbcr.recordCellRequest(request2);
            fbcr.recordCellRequest(request3);
            fbcr.loadAggregations();
        });

        value = aggMgr.getCellFromCache(request1); // after load, found
        assertTrue(value instanceof Number);
        assertEquals(694, ((Number) value).intValue());

        value = aggMgr.getCellFromCache(request2); // after load, found
        assertTrue(value instanceof Number);
        assertEquals(672, ((Number) value).intValue());

        value = aggMgr.getCellFromCache(request3); // after load, found
        assertTrue(value instanceof Number);
        assertEquals(1122, ((Number) value).intValue());
        // Note: 1122 != (694 + 672)
    }

    /**
     * Tests that a request for ([Measures].[Unit Sales], [Gender].[F])
     * generates the correct SQL.
     */
    @Test
    void testFemaleUnitSalesSql(Context<?> context) {
        prepareContext(context);
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
              && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Unit Sales]").where("customer", "gender", "F").build();

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select `agg_g_ms_pcat_sales_fact_1997`.`gender` as `c0`,"
                + " sum(`agg_g_ms_pcat_sales_fact_1997`.`unit_sales`) as `m0` "
                + "from `agg_g_ms_pcat_sales_fact_1997` as `agg_g_ms_pcat_sales_fact_1997` "
                + "where `agg_g_ms_pcat_sales_fact_1997`.`gender` = 'F' "
                + "group by `agg_g_ms_pcat_sales_fact_1997`.`gender`",
                26)
        };

        assertRequestSql(connection, new CellRequest[]{request}, patterns);
    }

    /**
     * As {@link #testFemaleUnitSalesSql()}, but with aggregate tables switched
     * on.
     *
     * TODO: Enable this test.
     */
    private void _testFemaleUnitSalesSql_withAggs(Context<?> context) {
        prepareContext(context);
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Unit Sales]").where("customer", "gender", "F").build();

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select `customer`.`gender` as `c0`,"
                + " sum(`agg_l_03_sales_fact_1997`.`unit_sales`) as `m0` "
                + "from `customer` as `customer`,"
                + " `agg_l_03_sales_fact_1997` as `agg_l_03_sales_fact_1997` "
                + "where `agg_l_03_sales_fact_1997`.`customer_id` = `customer`.`customer_id` "
                + "and `customer`.`gender` = 'F' "
                + "group by `customer`.`gender`",
                26)
        };

        assertRequestSql(connection, new CellRequest[]{request}, patterns);
    }

    /**
     * Test a batch containing multiple measures:
     *   (store_state=CA, gender=F, measure=[Unit Sales])
     *   (store_state=CA, gender=M, measure=[Store Sales])
     *   (store_state=OR, gender=M, measure=[Unit Sales])
     */
    @Test
    void testMultipleMeasures(Context<?> context) {
        prepareContext(context);
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
              && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest[] requests = new CellRequest[] {
            CellRequestFixture.of(connection).request()
                .cube("Sales").measure("[Measures].[Unit Sales]")
                .where("customer", "gender", "F").where("store", "store_state", "CA").build(),
            CellRequestFixture.of(connection).request()
                .cube("Sales").measure("[Measures].[Store Sales]")
                .where("customer", "gender", "M").where("store", "store_state", "CA").build(),
            CellRequestFixture.of(connection).request()
                .cube("Sales").measure("[Measures].[Unit Sales]")
                .where("customer", "gender", "F").where("store", "store_state", "OR").build()};

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select `store`.`store_state` as `c0`,"
                + " `customer`.`gender` as `c1`,"
                + " sum(`agg_l_05_sales_fact_1997`.`unit_sales`) as `m0`,"
                + " sum(`agg_l_05_sales_fact_1997`.`store_sales`) as `m1` "
                + "from `store` as `store`,"
                + " `agg_l_05_sales_fact_1997` as `agg_l_05_sales_fact_1997`,"
                + " `customer` as `customer` "
                + "where `agg_l_05_sales_fact_1997`.`store_id` = `store`.`store_id` "
                + "and `store`.`store_state` in ('CA', 'OR') "
                + "and `agg_l_05_sales_fact_1997`.`customer_id` = `customer`.`customer_id` "
                + "group by `store`.`store_state`, "
                + "`customer`.`gender`",
                29)
        };

        assertRequestSql(connection, requests, patterns);
    }

    /**
     * As {@link #testMultipleMeasures()}, but with aggregate tables switched
     * on.
     *
     * TODO: Enable this test.
     */
    @SuppressWarnings("java:S5810")
    private void _testMultipleMeasures_withAgg(Context<?> context) {
        prepareContext(context);
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest[] requests = new CellRequest[] {
            CellRequestFixture.of(connection).request()
                .cube("Sales").measure("[Measures].[Unit Sales]")
                .where("customer", "gender", "F").where("store", "store_state", "CA").build(),
            CellRequestFixture.of(connection).request()
                .cube("Sales").measure("[Measures].[Store Sales]")
                .where("customer", "gender", "M").where("store", "store_state", "CA").build(),
            CellRequestFixture.of(connection).request()
                .cube("Sales").measure("[Measures].[Unit Sales]")
                .where("customer", "gender", "F").where("store", "store_state", "OR").build()};

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select `customer`.`gender` as `c0`,"
                + " `store`.`store_state` as `c1`,"
                + " sum(`agg_l_05_sales_fact_1997`.`unit_sales`) as `m0`,"
                + " sum(`agg_l_05_sales_fact_1997`.`store_sales`) as `m1` "
                + "from `customer` as `customer`,"
                + " `agg_l_05_sales_fact_1997` as `agg_l_05_sales_fact_1997`,"
                + " `store` as `store` "
                + "where `agg_l_05_sales_fact_1997`.`customer_id` = `customer`.`customer_id`"
                + " and `agg_l_05_sales_fact_1997`.`store_id` = `store`.`store_id`"
                + " and `store`.`store_state` in ('CA', 'OR') "
                + "group by `customer`.`gender`, `store`.`store_state`",
                26)
        };

        assertRequestSql(connection, requests, patterns);
    }

    /**
     */
    @Test
    void createMultipleMeasureCellRequest(Context<?> context) {
        prepareContext(context);
        String cube = "Sales";
        String measure = "[Measures].[Unit Sales]";
        String table = "store";
        String column = "store_state";
        String value = "CA";
        Connection connection = context.getConnectionWithDefaultRole();
        final boolean fail = true;
        Cube salesCube = connection.getCatalog().lookupCube(cube).orElseThrow();
        Member storeSqftMeasure =
            salesCube.getCatalogReader(null).getMemberByUniqueName(
                Util.parseIdentifier(measure), fail);
        RolapStar.Measure starMeasure =
            RolapStar.getStarMeasure(storeSqftMeasure);
        CellRequest request = new CellRequest(starMeasure, false, false);
        final RolapStar star = starMeasure.getStar();
        final RolapStar.Column storeTypeColumn =
            star.lookupColumn(table, column);
        request.addConstrainedColumn(
            storeTypeColumn,
            new ValueColumnPredicate(storeTypeColumn, value));
        assertTrue(true);
    }

    // todo: test unrestricted column, (Unit Sales, Gender=*)

    // todo: test one unrestricted, one restricted, (UNit Sales, Gender=*,
    //  State={CA, OR})

    // todo: test with 2 dimension columns on the same table, e.g.
    //  (Unit Sales, Gender={F}, MaritalStatus={S}) and make sure that the
    // table only appears once in the from clause.

    /**
     * Tests that if a level is marked 'unique members', then its parent
     * is not constrained.
     */
    @Test
    void testUniqueMembers(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareContext(context);
        // [Store].[Store State] is unique, so we don't expect to see any
        // references to country.
        final String mdxQuery =
            "select {[Measures].[Unit Sales]} on columns,"
            + " {[Store].[USA].[CA], [Store].[USA].[OR]} on rows "
            + "from [Sales]";
        SqlPattern[] patterns;
        String accessMysqlSql, derbySql;

        // Note: the following aggregate loading sqls contain no
        // references to the parent level column "store_country".
        if (context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
            && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class))
        {
            accessMysqlSql =
                "select `store`.`store_state` as `c0`,"
                + " `agg_c_14_sales_fact_1997`.`the_year` as `c1`,"
                + " sum(`agg_c_14_sales_fact_1997`.`unit_sales`) as `m0` "
                + "from `store` as `store`,"
                + " `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997` "
                + "where `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id`"
                + " and `store`.`store_state` in ('CA', 'OR')"
                + " and `agg_c_14_sales_fact_1997`.`the_year` = 1997 "
                + "group by `store`.`store_state`,"
                + " `agg_c_14_sales_fact_1997`.`the_year`";

            derbySql =
                "select "
                + "\"store\".\"store_state\" as \"c0\", \"agg_c_14_sales_fact_1997\".\"the_year\" as \"c1\", "
                + "sum(\"agg_c_14_sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from "
                + "\"store\" as \"store\", \"agg_c_14_sales_fact_1997\" as \"agg_c_14_sales_fact_1997\" "
                + "where "
                + "\"agg_c_14_sales_fact_1997\".\"store_id\" = \"store\".\"store_id\" and "
                + "\"store\".\"store_state\" in ('CA', 'OR') and "
                + "\"agg_c_14_sales_fact_1997\".\"the_year\" = 1997 "
                + "group by "
                + "\"store\".\"store_state\", \"agg_c_14_sales_fact_1997\".\"the_year\"";

            patterns = new SqlPattern[] {
                new SqlPattern(
                    ACCESS_MYSQL,
                    accessMysqlSql, 50),
                new SqlPattern(
                    DatabaseProduct.DERBY, derbySql, derbySql)
            };
        } else {
            accessMysqlSql =
                "select `store`.`store_state` as `c0`, `time_by_day`.`the_year` as `c1`, sum(`sales_fact_1997`.`unit_sales`) as `m0` from `sales_fact_1997` as `sales_fact_1997` join `store` as `store` on `sales_fact_1997`.`store_id` = `store`.`store_id` join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` where `store`.`store_state` in ('CA', 'OR') and `time_by_day`.`the_year` = 1997 group by `store`.`store_state`, `time_by_day`.`the_year`";

            derbySql =
                "select \"store\".\"store_state\" as \"c0\", \"time_by_day\".\"the_year\" as \"c1\", "
                + "sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from "
                + "\"sales_fact_1997\" as \"sales_fact_1997\" "
                + "join \"store\" as \"store\" "
                + "on \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\" "
                + "join \"time_by_day\" as \"time_by_day\" "
                + "on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" "
                + "where "
                + "\"store\".\"store_state\" in ('CA', 'OR') and "
                + "\"time_by_day\".\"the_year\" = 1997 "
                + "group by "
                + "\"store\".\"store_state\", \"time_by_day\".\"the_year\"";

            patterns = new SqlPattern[] {
                new SqlPattern(
                    ACCESS_MYSQL,
                    accessMysqlSql, 50),
                new SqlPattern(
                    DatabaseProduct.DERBY, derbySql, derbySql)
            };
        }
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdxQuery).expectSql(patterns).verify();
    }

    /**
     * Tests that a NonEmptyCrossJoin uses the measure referenced by the query
     * (Store Sales) instead of the default measure (Unit Sales) in the case
     * where the query only has one result axis.  The setup here is necessarily
     * elaborate because the original bug was quite arbitrary.
     */
    @Test
    void testNonEmptyCrossJoinLoneAxis(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareContext(context);
        // Not sure what this test is checking.
        // For now, only run it for derby.
        Connection connection = context.getConnectionWithDefaultRole();
        final Dialect dialect = getDialect(connection);
        if (getDatabaseProduct(dialect.name()) != DatabaseProduct.DERBY) {
            return;
        }
        String mdxQuery =
            "With "
            + "Set [*NATIVE_CJ_SET] as "
            + "'NonEmptyCrossJoin([*BASE_MEMBERS_Store],[*BASE_MEMBERS_Product])' "
            + "Set [*BASE_MEMBERS_Store] as '{[Store].[All Stores].[USA]}' "
            + "Set [*GENERATED_MEMBERS_Store] as "
            + "'Generate([*NATIVE_CJ_SET], {[Store].CurrentMember})' "
            + "Set [*BASE_MEMBERS_Product] as "
            + "'{[Product].[All Products].[Food],[Product].[All Products].[Drink]}' "
            + "Set [*GENERATED_MEMBERS_Product] as "
            + "'Generate([*NATIVE_CJ_SET], {[Product].CurrentMember})' "
            + "Member [Store].[*FILTER_MEMBER] as 'Aggregate ([*GENERATED_MEMBERS_Store])' "
            + "Member [Product].[*FILTER_MEMBER] as 'Aggregate ([*GENERATED_MEMBERS_Product])' "
            + "Select {[Measures].[Store Sales]} on columns "
            + "From [Sales] "
            + "Where ([Store].[*FILTER_MEMBER], [Product].[*FILTER_MEMBER])";

        String derbySql =
            "select "
            + "\"store\".\"store_country\" as \"c0\", "
            + "\"time_by_day\".\"the_year\" as \"c1\", "
            + "\"product_class\".\"product_family\" as \"c2\", "
            + "sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
            + "from "
            + "\"store\" as \"store\", "
            + "\"sales_fact_1997\" as \"sales_fact_1997\", "
            + "\"time_by_day\" as \"time_by_day\", "
            + "\"product_class\" as \"product_class\", "
            + "\"product\" as \"product\" "
            + "where "
            + "\"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\" and "
            + "\"store\".\"store_country\" = 'USA' and "
            + "\"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and "
            + "\"time_by_day\".\"the_year\" = 1997 and "
            + "\"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" and "
            + "\"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" "
            + "group by "
            + "\"store\".\"store_country\", \"time_by_day\".\"the_year\", "
            + "\"product_class\".\"product_family\"";

        SqlPattern[] patterns = {
            new SqlPattern(DatabaseProduct.DERBY, derbySql, derbySql)};

        // For derby, the TestAggregationManager.testNonEmptyCrossJoinLoneAxis
        // test fails if the non-empty crossjoin optimizer is used.
        // With it on one gets a recursive call coming through the
        //  RolapEvaluator.getCachedResult.
        SqlAssert.forQuery(connection, mdxQuery).expectNoSql(patterns).verify();
    }

    /**
     * If a hierarchy lives in the fact table, we should not generate a join.
     */
    @Test
    void testHierarchyInFactTable(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareContext(context);
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request = CellRequestFixture.of(connection).request()
            .cube("Store").measure("[Measures].[Store Sqft]")
            .where("store", "store_type", "Supermarket").build();

        String accessMysqlSql =
            "select `store`.`store_type` as `c0`,"
            + " sum(`store`.`store_sqft`) as `m0` "
            + "from `store` as `store` "
            + "where `store`.`store_type` = 'Supermarket' "
            + "group by `store`.`store_type`";

        String derbySql =
            "select "
            + "\"store\".\"store_type\" as \"c0\", "
            + "sum(\"store\".\"store_sqft\") as \"m0\" "
            + "from "
            + "\"store\" as \"store\" "
            + "where "
            + "\"store\".\"store_type\" = 'Supermarket' "
            + "group by \"store\".\"store_type\"";

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL, accessMysqlSql, 26),
            new SqlPattern(DatabaseProduct.DERBY, derbySql, derbySql)
        };

        assertRequestSql(connection, new CellRequest[]{request}, patterns);
    }

    @Test
    void testCountDistinctAggMiss(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareContext(context);
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Customer Count]")
            .where("time_by_day", "the_year", "1997").where("time_by_day", "quarter", "Q1").build();

        String accessSql =
            "select"
            + " `d0` as `c0`,"
            + " `d1` as `c1`,"
            + " count(`m0`) as `c2` "
            + "from ("
            + "select distinct `time_by_day`.`the_year` as `d0`, "
            + "`time_by_day`.`quarter` as `d1`, "
            + "`sales_fact_1997`.`customer_id` as `m0` "
            + "from "
            + "`sales_fact_1997` as `sales_fact_1997`, "
            + "`time_by_day` as `time_by_day` "
            + "where "
            + "`sales_fact_1997`.`time_id` = `time_by_day`.`time_id` and "
            + "`time_by_day`.`the_year` = 1997 and "
            + "`time_by_day`.`quarter` = 'Q1'"
            + ") as `dummyname` "
            + "group by `d0`, `d1`";

        String mysqlSql =
            "select `time_by_day`.`the_year` as `c0`, `time_by_day`.`quarter` as `c1`, count(distinct `sales_fact_1997`.`customer_id`) as `m0` from `sales_fact_1997` as `sales_fact_1997` join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` where `time_by_day`.`the_year` = 1997 and `time_by_day`.`quarter` = 'Q1' group by `time_by_day`.`the_year`, `time_by_day`.`quarter`";

        String derbySql =
            "select "
            + "\"time_by_day\".\"the_year\" as \"c0\", "
            + "\"time_by_day\".\"quarter\" as \"c1\", "
            + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" "
            + "from "
            + "\"sales_fact_1997\" as \"sales_fact_1997\" "
            + "join \"time_by_day\" as \"time_by_day\" "
            + "on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" "
            + "where "
            + "\"time_by_day\".\"the_year\" = 1997 and "
            + "\"time_by_day\".\"quarter\" = 'Q1' "
            + "group by \"time_by_day\".\"the_year\", \"time_by_day\".\"quarter\"";

        SqlPattern[] patterns = {
            new SqlPattern(DatabaseProduct.ACCESS, accessSql, 26),
            new SqlPattern(DatabaseProduct.MYSQL, mysqlSql, 26),
            new SqlPattern(DatabaseProduct.DERBY, derbySql, derbySql)
        };

        assertRequestSql(connection, new CellRequest[]{request}, patterns);
    }

    @Test
    void testCountDistinctAggMatch(Context<?> context) {
        prepareContext(context);
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
              && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Customer Count]")
            .where("time_by_day", "the_year", "1997")
            .where("time_by_day", "quarter", "Q1")
            .where("time_by_day", "month_of_year", "1").build();

        String accessSql =
            "select "
            + "`agg_c_10_sales_fact_1997`.`the_year` as `c0`, "
            + "`agg_c_10_sales_fact_1997`.`quarter` as `c1`, "
            + "`agg_c_10_sales_fact_1997`.`month_of_year` as `c2`, "
            + "`agg_c_10_sales_fact_1997`.`customer_count` as `m0` "
            + "from "
            + "`agg_c_10_sales_fact_1997` as `agg_c_10_sales_fact_1997` "
            + "where "
            + "`agg_c_10_sales_fact_1997`.`the_year` = 1997 and "
            + "`agg_c_10_sales_fact_1997`.`quarter` = 'Q1' and "
            + "`agg_c_10_sales_fact_1997`.`month_of_year` = 1";

        SqlPattern[] patterns = {
            new SqlPattern(DatabaseProduct.ACCESS, accessSql, 26)};

        assertRequestSql(connection, new CellRequest[]{request}, patterns);
    }

    @Test
    void testCountDistinctCannotRollup(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareContext(context);
        // Summary "agg_g_ms_pcat_sales_fact_1997" doesn't match,
        // because we'd need to roll-up the distinct-count measure over
        // "month_of_year".
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Customer Count]")
            .where("time_by_day", "the_year", "1997")
            .where("time_by_day", "quarter", "Q1")
            .where("product_class", "product_family", "Food").build();

        SqlPattern[] patterns = {
            new SqlPattern(
                DatabaseProduct.MYSQL,
                "select `time_by_day`.`the_year` as `c0`, `time_by_day`.`quarter` as `c1`, `product_class`.`product_family` as `c2`, count(distinct `sales_fact_1997`.`customer_id`) as `m0` from `sales_fact_1997` as `sales_fact_1997` join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` join `product` as `product` on `sales_fact_1997`.`product_id` = `product`.`product_id` join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id` where `time_by_day`.`the_year` = 1997 and `time_by_day`.`quarter` = 'Q1' and `product_class`.`product_family` = 'Food' group by `time_by_day`.`the_year`, `time_by_day`.`quarter`, `product_class`.`product_family`",
                23),
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select"
                + " `d0` as `c0`,"
                + " `d1` as `c1`,"
                + " `d2` as `c2`,"
                + " count(`m0`) as `c3` "
                + "from ("
                + "select distinct `time_by_day`.`the_year` as `d0`,"
                + " `time_by_day`.`quarter` as `d1`,"
                + " `product_class`.`product_family` as `d2`,"
                + " `sales_fact_1997`.`customer_id` as `m0` "
                + "from `sales_fact_1997` as `sales_fact_1997`,"
                + " `time_by_day` as `time_by_day`,"
                + " `product_class` as `product_class`,"
                + " `product` as `product` "
                + "where `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`"
                + " and `time_by_day`.`the_year` = 1997"
                + " and `time_by_day`.`quarter` = 'Q1'"
                + " and `sales_fact_1997`.`product_id` = `product`.`product_id`"
                + " and `product`.`product_class_id` = `product_class`.`product_class_id`"
                + " and `product_class`.`product_family` = 'Food') as `dummyname` "
                + "group by `d0`, `d1`, `d2`",
                23),
             new SqlPattern(
                 DatabaseProduct.DERBY,
                 "select "
                 + "\"time_by_day\".\"the_year\" as \"c0\", \"time_by_day\".\"quarter\" as \"c1\", "
                 + "\"product_class\".\"product_family\" as \"c2\", "
                 + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" "
                 + "from "
                 + "\"sales_fact_1997\" as \"sales_fact_1997\" "
                 + "join \"time_by_day\" as \"time_by_day\" "
                 + "on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" "
                 + "join \"product\" as \"product\" "
                 + "on \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" "
                 + "join \"product_class\" as \"product_class\" "
                 + "on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" "
                 + "where "
                 + "\"time_by_day\".\"the_year\" = 1997 and "
                 + "\"time_by_day\".\"quarter\" = 'Q1' and "
                 + "\"product_class\".\"product_family\" = 'Food' "
                 + "group by \"time_by_day\".\"the_year\", \"time_by_day\".\"quarter\", "
                 + "\"product_class\".\"product_family\"",
                 23)
        };

        assertRequestSql(connection, new CellRequest[]{request}, patterns);
    }

    /**
     * Now, here's a funny thing. Usually you can't roll up a distinct-count
     * aggregate. But if you're rolling up along the dimension which the
     * count is counting, it's OK. In this case, you know that every member
     * can only belong to one group.
     */
    @Test
    void testCountDistinctRollupAlongDim(Context<?> context) {
        prepareContext(context);
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
              && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }
        // Request has granularity
        //  [Time].[Month]
        //  [Product].[Category]
        //
        // whereas agg table "agg_g_ms_pcat_sales_fact_1997" has
        // granularity
        //
        //  [Time].[Month]
        //  [Product].[Category]
        //  [Gender].[Gender]
        //  [Marital Status].[Marital Status]
        //
        // Because [Gender] and [Marital Status] come from the [Customer]
        // table (the same as the distinct-count measure), we can roll up.
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Customer Count]")
            .where("time_by_day", "the_year", "1997")
            .where("time_by_day", "quarter", "Q1")
            .where("time_by_day", "month_of_year", "1")
            .where("product_class", "product_family", "Food")
            .where("product_class", "product_department", "Deli")
            .where("product_class", "product_category", "Meat").build();

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select `agg_g_ms_pcat_sales_fact_1997`.`the_year` as `c0`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`quarter` as `c1`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`month_of_year` as `c2`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_family` as `c3`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_department` as `c4`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_category` as `c5`,"
                + " sum(`agg_g_ms_pcat_sales_fact_1997`.`customer_count`) as `m0` "
                + "from `agg_g_ms_pcat_sales_fact_1997` as `agg_g_ms_pcat_sales_fact_1997` "
                + "where `agg_g_ms_pcat_sales_fact_1997`.`the_year` = 1997"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`quarter` = 'Q1'"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`month_of_year` = 1"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`product_family` = 'Food'"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`product_department` = 'Deli'"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`product_category` = 'Meat' "
                + "group by `agg_g_ms_pcat_sales_fact_1997`.`the_year`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`quarter`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`month_of_year`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_family`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_department`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_category`",
                58)
        };

        assertRequestSql(connection, new CellRequest[]{request}, patterns);
    }

    /**
     * As above, but we rollup [Marital Status] but not [Gender].
     */
    @Test
    void testCountDistinctRollup2(Context<?> context) {
        prepareContext(context);
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
              && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Customer Count]")
            .where("time_by_day", "the_year", "1997")
            .where("time_by_day", "quarter", "Q1")
            .where("time_by_day", "month_of_year", "1")
            .where("product_class", "product_family", "Food")
            .where("product_class", "product_department", "Deli")
            .where("product_class", "product_category", "Meat")
            .where("customer", "gender", "F").build();

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select `agg_g_ms_pcat_sales_fact_1997`.`the_year` as `c0`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`quarter` as `c1`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`month_of_year` as `c2`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_family` as `c3`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_department` as `c4`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_category` as `c5`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`gender` as `c6`,"
                + " sum(`agg_g_ms_pcat_sales_fact_1997`.`customer_count`) as `m0` "
                + "from `agg_g_ms_pcat_sales_fact_1997` as `agg_g_ms_pcat_sales_fact_1997` "
                + "where `agg_g_ms_pcat_sales_fact_1997`.`the_year` = 1997"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`quarter` = 'Q1'"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`month_of_year` = 1"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`product_family` = 'Food'"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`product_department` = 'Deli'"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`product_category` = 'Meat'"
                + " and `agg_g_ms_pcat_sales_fact_1997`.`gender` = 'F' "
                + "group by `agg_g_ms_pcat_sales_fact_1997`.`the_year`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`quarter`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`month_of_year`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_family`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_department`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`product_category`,"
                + " `agg_g_ms_pcat_sales_fact_1997`.`gender`",
                58)
        };

        assertRequestSql(connection, new CellRequest[]{request}, patterns);
    }

    /**
     * Test that cells with the same compound member constraints are
     * loaded in one Sql statement.
     *
     * Cells [Food] and [Drink] have the same constraint:
     *
     *  {[1997].[Q1].[1], [1997].[Q3].[7]}
     */
    @Test
    void testCountDistinctBatchLoading(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareContext(context);
        CellRequestFixture.Constraint aggConstraint =
            CellRequestFixture.Constraint.yearQuarterMonth(
                new String[] {"1997", "Q1", "1"}, new String[] {"1997", "Q3", "7"});
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Customer Count]")
            .where("product_class", "product_family", "Food")
            .constrain(aggConstraint).build();

        CellRequest request2 = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Customer Count]")
            .where("product_class", "product_family", "Drink")
            .constrain(aggConstraint).build();

        String mysqlSql =
            "select `product_class`.`product_family` as `c0`, count(distinct `sales_fact_1997`.`customer_id`) as `m0` from `sales_fact_1997` as `sales_fact_1997` join `product` as `product` on `sales_fact_1997`.`product_id` = `product`.`product_id` join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id` join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` where (((`time_by_day`.`the_year`, `time_by_day`.`quarter`, `time_by_day`.`month_of_year`) in ((1997, 'Q1', 1), (1997, 'Q3', 7)))) group by `product_class`.`product_family`";

        String derbySql =
            "select \"product_class\".\"product_family\" as \"c0\", "
            + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" "
            + "from \"sales_fact_1997\" as \"sales_fact_1997\" "
            + "join \"product\" as \"product\" "
            + "on \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" "
            + "join \"product_class\" as \"product_class\" "
            + "on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" "
            + "join \"time_by_day\" as \"time_by_day\" "
            + "on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" "
            + "where ((((\"time_by_day\".\"the_year\" = 1997 and \"time_by_day\".\"quarter\" = 'Q1' and \"time_by_day\".\"month_of_year\" = 1) or "
            + "(\"time_by_day\".\"the_year\" = 1997 and \"time_by_day\".\"quarter\" = 'Q3' and \"time_by_day\".\"month_of_year\" = 7)))) "
            + "group by \"product_class\".\"product_family\"";

        SqlPattern[] patterns = {
            new SqlPattern(DatabaseProduct.MYSQL, mysqlSql, mysqlSql),
            new SqlPattern(DatabaseProduct.DERBY, derbySql, derbySql)
        };

        assertRequestSql(connection, new CellRequest[]{request1, request2}, patterns);
    }

    /**
     * Tests that an aggregate table is used to speed up a
     * <code>&lt;Member&gt;.Children</code> expression.
     */
    @Test
    void testAggMembers(Context<?> context) {
        prepareContext(context);
        if (context.getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) > 0) {
            return;
        }
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
                && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }
        if (!(context.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class))) {
            return;
        }
        SqlPattern[] patterns = {
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select `store`.`store_country` as `c0` "
                + "from `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997`,"
                + " `store` as `store` "
                + "where `agg_c_14_sales_fact_1997`.`the_year` = 1998 "
                + "and `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id` "
                + "group by `store`.`store_country` "
                + "order by Iif(`store`.`store_country` IS NULL, 1, 0),"
                + " `store`.`store_country` ASC",
                26),
            new SqlPattern(
                DatabaseProduct.MYSQL,
                "select `store`.`store_country` as `c0` "
                + "from `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997`,"
                + " `store` as `store` "
                + "where `agg_c_14_sales_fact_1997`.`the_year` = 1998 "
                + "and `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id` "
                + "group by `store`.`store_country` "
                + "order by ISNULL(`store`.`store_country`) ASC, `store`.`store_country` ASC",
                26)};
        Connection connection = context.getConnectionWithDefaultRole();
        SqlAssert.forQuery(connection,
            "select NON EMPTY {[Customers].[USA]} ON COLUMNS,\n"
            + "       NON EMPTY Crossjoin(Hierarchize(Union({[Store].[All Stores]},\n"
            + "           [Store].[All Stores].Children)), {[Product].[All Products]}) \n"
            + "           ON ROWS\n"
            + "    from [Sales]\n"
            + "    where ([Measures].[Unit Sales], [Time].[1998])").expectSql(patterns).verify();
    }

    /**
     * As {@link #testAggMembers()}, but asks for children of a leaf level.
     * Rewrite using an aggregate table is not possible, so just check that it
     * gets the right result.
     */
    @Test
    void testAggChildMembersOfLeaf(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareContext(context);
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatQuery(connection,
            "select NON EMPTY {[Time].[1997]} ON COLUMNS,\n"
            + "       NON EMPTY Crossjoin(Hierarchize(Union({[Store].[All Stores]},\n"
            + "           [Store].[USA].[CA].[San Francisco].[Store 14].Children)), {[Product].[All Products]}) \n"
            + "           ON ROWS\n"
            + "    from [Sales]\n"
            + "    where [Measures].[Unit Sales]").returnsGrid(
            "Axis #0:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #1:\n"
            + "{[Time].[Time].[1997]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[All Stores], [Product].[Product].[All Products]}\n"
            + "Row #0: 266,773\n");
    }

    /**
     * This test case tests for a null pointer that was being thrown
     * inside of CellRequest.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier1.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNoNullPtrInCellRequest(Context<?> context) {
        prepareContext(context);
        /*
        ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
            "Sales",
            "<Dimension name=\"Store2\" foreignKey=\"store_id\">\n"
            + "  <Hierarchy hasAll=\"true\" primaryKey=\"store_id\" allMemberName=\"All Stores\">"
            + "    <Table name=\"store\"/>\n"
            + "    <Level name=\"Store Country\" column=\"store_country\" uniqueMembers=\"true\"/>\n"
            + "    <Level name=\"Store State\"   column=\"store_state\"   uniqueMembers=\"true\"/>\n"
            + "    <Level name=\"Store City\"    column=\"store_city\"    uniqueMembers=\"false\"/>\n"
            + "    <Level name=\"Store Type\"    column=\"store_type\"    uniqueMembers=\"false\"/>\n"
            + "    <Level name=\"Store Name\"    column=\"store_name\"    uniqueMembers=\"true\"/>\n"
            + "  </Hierarchy>\n"
            + "</Dimension>"));
         */
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns, "
            + "Filter ({ "
            + "[Store2].[All Stores].[USA].[CA].[Beverly Hills], "
            + "[Store2].[All Stores].[USA].[CA].[Beverly Hills].[Gourmet Supermarket] "
            + "},[Measures].[Unit Sales] > 0) on rows "
            + "from [Sales] "
            + "where [Store Type].[Store Type].[Small Grocery]").returnsGrid(
            "Axis #0:\n"
            + "{[Store Type].[Store Type].[Small Grocery]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n");
    }

    /**
     *  Test that once fetched, column cardinality can be shared between
     *  different queries using the same connection.
     *
     *  <p>Test also that expressions with only table alias difference do not
     *  share cardinality result.
     */
    @Test
    void testColumnCadinalityCache(Context<?> context) {
        prepareContext(context);
        String query1 =
            "select "
            + "NonEmptyCrossJoin("
            + "[Product].[Product Family].Members, "
            + "[Gender].[Gender].Members) on columns "
            + "from [Sales]";

        String query2 =
            "select "
            + "NonEmptyCrossJoin("
            + "[Store].[Store Country].Members, "
            + "[Product].[Product Family].Members) on columns "
            + "from [Warehouse]";

        String cardinalitySqlDerby =
            "select "
            + "count(distinct \"product_class\".\"product_family\") "
            + "from \"product_class\" as \"product_class\"";

        String cardinalitySqlMySql =
            "select "
            + "count(distinct `product_class`.`product_family`) as `c0` "
            + "from `product_class` as `product_class`";

        SqlPattern[] patterns =
            new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.DERBY,
                    cardinalitySqlDerby,
                    cardinalitySqlDerby),
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    cardinalitySqlMySql,
                    cardinalitySqlMySql)
            };

        //final TestContext<?> context = getTestContext().withFreshConnection();
        Connection connection = context.getConnectionWithDefaultRole();
        try {
            // This MDX gets the [Product].[Product Family] cardinality
            // from the DB.
            executeQuery(query1, connection);

            // This MDX should be able to reuse the cardinality for
            // [Product].[Product Family]; and should not issue a SQL to fetch
            // that from DB again.
            SqlAssert.forQuery(connection, query2).keepCache().expectNoSql(patterns).verify();
        } finally {
            connection.close();
        }
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, TestKeyExpressionCardinalityCacheModifier.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testKeyExpressionCardinalityCache(Context<?> context) {
        prepareContext(context);
        /*
        String storeDim1 =
            "<Dimension name=\"Store1\">\n"
            + "  <Hierarchy hasAll=\"true\" primaryKey=\"store_id\">\n"
            + "  <Table name=\"store\"/>\n"
            + "    <Level name=\"Store Country\" uniqueMembers=\"true\">\n"
            + "      <KeyExpression>\n"
            + "        <SQL dialect=\"oracle\">\n"
            + "\"store_country\"\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"hsqldb\">\n"
            + "\"store_country\"\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"derby\">\n"
            + "\"store_country\"\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"luciddb\">\n"
            + "\"store_country\"\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"mysql\">\n"
            + "`store_country`\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"netezza\">\n"
            + "\"store_country\"\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"neoview\">\n"
            + "\"store_country\"\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"generic\">\n"
            + "store_country\n"
            + "        </SQL>\n"
            + "      </KeyExpression>\n"
            + "    </Level>\n"
            + "  </Hierarchy>\n"
            + "</Dimension>\n";

        String storeDim2 =
            "<Dimension name=\"Store2\">\n"
            + "  <Hierarchy hasAll=\"true\" primaryKey=\"store_id\">\n"
            + "  <Table name=\"store_ragged\"/>\n"
            + "    <Level name=\"Store Country\" uniqueMembers=\"true\">\n"
            + "      <KeyExpression>\n"
            + "        <SQL dialect=\"oracle\">\n"
            + "\"store_country\"\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"derby\">\n"
            + "\"store_country\"\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"luciddb\">\n"
            + "\"store_country\"\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"mysql\">\n"
            + "`store_country`\n"
            + "        </SQL>\n"
            + "        <SQL dialect=\"generic\">\n"
            + "store_country\n"
            + "        </SQL>\n"
            + "      </KeyExpression>\n"
            + "    </Level>\n"
            + "  </Hierarchy>\n"
            + "</Dimension>\n";

        String salesCube1 =
            "<Cube name=\"Sales1\" defaultMeasure=\"Unit Sales\">\n"
            + "  <Table name=\"sales_fact_1997\" />\n"
            + "  <DimensionUsage name=\"Store1\" source=\"Store1\" foreignKey=\"store_id\"/>\n"
            + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\" formatString=\"Standard\"/>\n"
            + "  <Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\" formatString=\"Standard\"/>\n"
            + "</Cube>\n";

        String salesCube2 =
            "<Cube name=\"Sales2\" defaultMeasure=\"Unit Sales\">\n"
            + "  <Table name=\"sales_fact_1997\" />\n"
            + "  <DimensionUsage name=\"Store2\" source=\"Store2\" foreignKey=\"store_id\"/>\n"
            + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\" formatString=\"Standard\"/>\n"
            + "</Cube>\n";
        */
        String query =
            "select {[Measures].[Unit Sales]} ON COLUMNS, {[Store1].members} ON ROWS FROM [Sales1]";

        String query1 =
            "select {[Measures].[Store Sales]} ON COLUMNS, {[Store1].members} ON ROWS FROM [Sales1]";

        String query2 =
            "select {[Measures].[Unit Sales]} ON COLUMNS, {[Store2].members} ON ROWS FROM [Sales2]";

        String cardinalitySqlDerby1 =
            "select count(*) from (select distinct \"store_country\" as \"c0\" from \"store\" as \"store\") as \"init\"";

        String cardinalitySqlMySql1 =
            "select COUNT(distinct `store_country`) as `c0` from `store` as `store`";

        String cardinalitySqlDerby2 =
            "select count(*) from (select distinct \"store_country\" as \"c0\" from \"store_ragged\" as \"store_ragged\") as \"init\"";

        String cardinalitySqlMySql2 =
            "select COUNT(*) as `c0` from (select distinct `store_country` as `c0` from `store_ragged` as `store_ragged`) as `init`";

        SqlPattern[] patterns1 =
            new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.DERBY,
                    cardinalitySqlDerby1,
                    cardinalitySqlDerby1),
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    cardinalitySqlMySql1,
                    cardinalitySqlMySql1)
            };

        SqlPattern[] patterns2 =
            new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.DERBY,
                    cardinalitySqlDerby2,
                    cardinalitySqlDerby2),
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    cardinalitySqlMySql2,
                    cardinalitySqlMySql2)
            };
        /*
        class TestKeyExpressionCardinalityCacheModifier extends PojoMappingModifier {

            public TestKeyExpressionCardinalityCacheModifier(CatalogMapping catalog) {
                super(catalog);
            }

            protected List<CubeMapping> cubes(List<? extends CubeMapping> cubes) {
            	StandardDimensionMappingImpl store1Dimension = StandardDimensionMappingImpl.builder()
                .withName("Store1")
                .withHierarchies(List.of(
                    ExplicitHierarchyMappingImpl.builder()
                        .withHasAll(true)
                        .withPrimaryKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_STORE)
                        .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.STORE_TABLE).build())
                        .withLevels(List.of(
                            LevelMappingImpl.builder()
                                .withName("Store Country")
                                .withUniqueMembers(true)
                                .withColumn(SQLExpressionMappingColumnImpl.builder()
                                		.withSqls(List.of(
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("oracle"))
                                            .withSql("\"store_country\"")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("hsqldb"))
                                            .withSql("\"store_country\"")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("derby"))
                                            .withSql("\"store_country\"")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("luciddb"))
                                            .withSql("\"store_country\"")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("mysql"))
                                            .withSql("`store_country`")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("netezza"))
                                            .withSql("\"store_country\"")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("neoview"))
                                            .withSql("\"store_country\"")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("generic"))
                                            .withSql("store_country")
                                            .build()
                                        )).withDataType(ColumnDataType.VARCHAR).build())
                                .build()
                        ))
                        .build()
                ))
                .build();
            	StandardDimensionMappingImpl store2Dimension = StandardDimensionMappingImpl.builder()
                .withName("Store2")
                .withHierarchies(List.of(
                    ExplicitHierarchyMappingImpl.builder()
                        .withHasAll(true)
                        .withPrimaryKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_STORE_RAGGED)
                        .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.STORE_RAGGED_TABLE).build())
                        .withLevels(List.of(
                            LevelMappingImpl.builder()
                                .withName("Store Country")
                                .withUniqueMembers(true)
                                .withColumn(SQLExpressionMappingColumnImpl.builder()
                                    .withSqls(List.of(
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("oracle"))
                                            .withSql("\"store_country\"")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("derby"))
                                            .withSql("\"store_country\"")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("luciddb"))
                                            .withSql("\"store_country\"")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("mysql"))
                                            .withSql("`store_country`")
                                            .build(),
                                        SqlStatementMappingImpl.builder()
                                            .withDialects(List.of("generic"))
                                            .withSql("store_country")
                                            .build()
                                    )).withDataType(ColumnDataType.VARCHAR).build())
                                    .build()))
                                .build()
                        ))
                        .build();

            	MeasureMappingImpl sales1UnitSales = SumMeasureMappingImpl.builder()
                .withName("Unit Sales")
                .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_SALES_FACT_1997)
                .withFormatString("Standard")
                .build();

            	MeasureMappingImpl sales2UnitSales = SumMeasureMappingImpl.builder()
            		.withName("Unit Sales")
                    .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_SALES_FACT_1997)
                    .withFormatString("Standard")
                    .build();


                List<CubeMapping> result = new ArrayList<>();
                result.addAll(super.cubes(cubes));

                result.add(PhysicalCubeMappingImpl.builder()
                    .withName("Sales1")
                    .withDefaultMeasure(sales1UnitSales)
                    .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.SALES_FACT_1997_TABLE).build())
                    .withDimensionConnectors(List.of(
                    	DimensionConnectorMappingImpl.builder()
                    		.withOverrideDimensionName("Store1")
                    		.withDimension(store1Dimension)
                            .withForeignKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_SALES_FACT_1997)
                            .build()
                    ))
                    .withMeasureGroups(List.of(MeasureGroupMappingImpl.builder().withMeasures(List.of(
                    	sales1UnitSales,
                        SumMeasureMappingImpl.builder()
                        .withName("Store Sales")
                        .withColumn(FoodmartMappingSupplier.STORE_SALES_COLUMN_IN_SALES_FACT_1997)
                        .withFormatString("Standard")
                        .build()
                    )).build()))
                    .build());

                result.add(PhysicalCubeMappingImpl.builder()
                    .withName("Sales2")
                    .withDefaultMeasure(sales2UnitSales)
                    .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.SALES_FACT_1997_TABLE).build())
                    .withDimensionConnectors(List.of(
                        DimensionConnectorMappingImpl.builder()
                        	.withOverrideDimensionName("Store2")
                        	.withDimension(store2Dimension)
                            .withForeignKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_SALES_FACT_1997)
                            .build()
                    ))
                    .withMeasureGroups(List.of(MeasureGroupMappingImpl.builder().withMeasures(List.of(
                    		sales2UnitSales)).build()
                    ))
                    .build());
                return result;

            }
        }
        */
        // This query causes "store"."store_country" cardinality to be
        // retrieved.
        Connection connection = context.getConnectionWithDefaultRole();
        executeQuery(query, connection);

        // Query1 will find the "store"."store_country" cardinality in cache.
        SqlAssert.forQuery(connection, query1).keepCache().expectNoSql(patterns1).verify();

        // Query2 again will not find the "store_ragged"."store_country"
        // cardinality in cache.
        SqlAssert.forQuery(connection, query2).keepCache().expectSql(patterns2).verify();
        context.getCatalogCache().clear();
    }

    /**
     * Test that using compound member constrant disables using AggregateTable
     */
    @Test
    void testCountDistinctWithConstraintAggMiss(Context<?> context) {
        prepareContext(context);
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
              && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }

        // Request has granularity
        //  [Product].[Category]
        // and the compound constraint on
        //  [Time].[Quarter]
        //
        // whereas agg table "agg_g_ms_pcat_sales_fact_1997" has
        // granularity
        //
        //  [Time].[Quarter]
        //  [Product].[Category]
        //  [Gender].[Gender]
        //  [Marital Status].[Marital Status]
        //
        // The presence of compound constraint causes agg table not used.
        //
        // Note ideally we should also test that non distinct measures could be
        // loaded from Aggregate table; however, the testing framework here uses
        // CellRequest directly which causes any compound constraint to be kept
        // separately. This will cause Aggregate tables not to be used.
        //
        // CellRequest generated by the code form MDX will in this case not
        // separate out the compound constraint from the "regular" constraints
        // and Aggregate tables can still be used.

        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request = CellRequestFixture.of(connection).request()
            .cube("Sales").measure("[Measures].[Customer Count]")
            .where("product_class", "product_family", "Food")
            .where("product_class", "product_department", "Deli")
            .where("product_class", "product_category", "Meat")
            .constrain(CellRequestFixture.Constraint.yearQuarterMonth(
                new String[] {"1997", "Q1", "1"})).build();

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select "
                + "`product_class`.`product_family` as `c0`, "
                + "`product_class`.`product_department` as `c1`, "
                + "`product_class`.`product_category` as `c2`, "
                + "count(distinct `sales_fact_1997`.`customer_id`) as `m0` "
                + "from "
                + "`product_class` as `product_class`, `product` as `product`, "
                + "`sales_fact_1997` as `sales_fact_1997`, `time_by_day` as `time_by_day` "
                + "where "
                + "`sales_fact_1997`.`product_id` = `product`.`product_id` and "
                + "`product`.`product_class_id` = `product_class`.`product_class_id` and "
                + "`product_class`.`product_family` = 'Food' and "
                + "`product_class`.`product_department` = 'Deli' and "
                + "`product_class`.`product_category` = 'Meat' and "
                + "`sales_fact_1997`.`time_id` = `time_by_day`.`time_id` and "
                + "(`time_by_day`.`the_year` = 1997 and `time_by_day`.`quarter` = 'Q1' and "
                + "`time_by_day`.`month_of_year` = 1) "
                + "group by "
                + "`product_class`.`product_family`, `product_class`.`product_department`, "
                + "`product_class`.`product_category`",
                58)
        };

        assertRequestSql(connection, new CellRequest[]{request}, patterns);
    }

    /**
     * Test case for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-663">bug MONDRIAN-663,
     * "Improve metadata query (TupleReader) support for aggregation tables to
     * include dimensions defining more than one column"</a>.
     */
    @Test
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, TestOrdinalExprAggTuplesAndChildrenModifier.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testOrdinalExprAggTuplesAndChildren(Context<?> context) {
        prepareContext(context);
        // this verifies that we can load properties, ordinals, etc out of
        // agg tables in member lookups (tuples and children)
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
                && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }
        if (!(context.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class))) {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        flushSchemaCache(connection);
        /*
        String cube = "<Cube name=\"Sales_Prod_Ord\">\n"
        + "  <Table name=\"sales_fact_1997\"/>\n"
        + "  <Dimension name=\"Product\" foreignKey=\"product_id\">\n"
        + "    <Hierarchy hasAll=\"true\" primaryKey=\"product_id\" primaryKeyTable=\"product\">\n"
        + "      <Join leftKey=\"product_class_id\" rightKey=\"product_class_id\">\n"
        + "        <Table name=\"product\"/>\n"
        + "        <Table name=\"product_class\"/>\n"
        + "      </Join>\n"
        + "      <Level name=\"Product Family\" table=\"product_class\" column=\"product_family\"\n"
        + "          uniqueMembers=\"true\"/>\n"
        + "      <Level name=\"Product Department\" table=\"product_class\" column=\"product_department\"\n"
        + "          uniqueMembers=\"false\"/>\n"
        + "      <Level name=\"Product Category\" table=\"product_class\" captionColumn=\"product_family\" column=\"product_category\"\n"
        + "          uniqueMembers=\"false\"/>\n"
        + "      <Level name=\"Product Subcategory\" table=\"product_class\" column=\"product_subcategory\"\n"
        + "          uniqueMembers=\"false\"/>\n"
        + "      <Level name=\"Brand Name\" table=\"product\" column=\"brand_name\" uniqueMembers=\"false\"/>\n"
        + "      <Level name=\"Product Name\" table=\"product\" column=\"product_name\"\n"
        + "          uniqueMembers=\"true\"/>\n"
        + "    </Hierarchy>\n"
        + "  </Dimension>\n"
        + "  <Dimension name=\"Gender\" foreignKey=\"customer_id\">\n"
        + "    <Hierarchy hasAll=\"false\" primaryKey=\"customer_id\">\n"
        + "    <Table name=\"customer\"/>\n"
        + "      <Level name=\"Gender\" column=\"gender\" uniqueMembers=\"true\"/>\n"
        + "    </Hierarchy>\n"
        + "  </Dimension>"
        + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
        + "      formatString=\"Standard\" visible=\"false\"/>\n"
        + "  <Measure name=\"Store Cost\" column=\"store_cost\" aggregator=\"sum\"\n"
        + "      formatString=\"#,###.00\"/>\n"
        + "</Cube>";
        */
        /*
        class TestOrdinalExprAggTuplesAndChildrenModifier extends PojoMappingModifier {

            public TestOrdinalExprAggTuplesAndChildrenModifier(CatalogMapping catalog) {
                super(catalog);
            }

            @Override
            protected List<CubeMapping> cubes(List<? extends CubeMapping> cubes) {
                List<CubeMapping> result = new ArrayList<>();
                result.addAll(super.cubes(cubes));
                result.add(PhysicalCubeMappingImpl.builder()
                    .withName("Sales_Prod_Ord")
                    .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.SALES_FACT_1997_TABLE).build())
                    .withDimensionConnectors(List.of(
                        DimensionConnectorMappingImpl.builder()
                        	.withOverrideDimensionName("Product")
                            .withForeignKey(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_SALES_FACT_1997)
                            .withDimension(StandardDimensionMappingImpl.builder()
                            	.withName("Product")
                            	.withHierarchies(List.of(
                                ExplicitHierarchyMappingImpl.builder()
                                    .withHasAll(true)
                                    .withPrimaryKey(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_PRODUCT)
                                    .withQuery(JoinQueryMappingImpl.builder()
                                    		.withLeft(JoinedQueryElementMappingImpl.builder()
                                    				.withKey(FoodmartMappingSupplier.PRODUCT_CLASS_ID_COLUMN_IN_PRODUCT)
                                    				.withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.PRODUCT_TABLE).build())
                                    				.build())
                                    		.withRight(JoinedQueryElementMappingImpl.builder()
                                    				.withKey(FoodmartMappingSupplier.PRODUCT_CLASS_ID_COLUMN_IN_PRODUCT_CLASS)
                                    				.withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.PRODUCT_CLASS_TABLE).build())
                                    				.build())
                                    		.build())
                                    .withLevels(List.of(
                                        LevelMappingImpl.builder()
                                            .withName("Product Family")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_FAMILY_COLUMN_IN_PRODUCT_CLASS)
                                            .withUniqueMembers(true)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Product Department")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_DEPARTMENT_COLUMN_IN_PRODUCT_CLASS)
                                            .withUniqueMembers(false)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Product Category")
                                            .withCaptionColumn(FoodmartMappingSupplier.PRODUCT_FAMILY_COLUMN_IN_PRODUCT_CLASS)
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_CATEGORY_COLUMN_IN_PRODUCT_CLASS)
                                            .withUniqueMembers(false)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Product Subcategory")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_SUBCATEGORY_COLUMN_IN_PRODUCT_CLASS)
                                            .withUniqueMembers(false)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Brand Name")
                                            .withColumn(FoodmartMappingSupplier.BRAND_NAME_COLUMN_IN_PRODUCT)
                                            .withUniqueMembers(false)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Product Name")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_NAME_COLUMN_IN_PRODUCT)
                                            .withUniqueMembers(true)
                                            .build()
                                        ))
                                    .build()
                            )).build())
                            .build(),
                        DimensionConnectorMappingImpl.builder()
                        	.withOverrideDimensionName("Gender")
                            .withForeignKey(FoodmartMappingSupplier.CUSTOMER_ID_COLUMN_IN_SALES_FACT_1997)
                            .withDimension(StandardDimensionMappingImpl.builder()
                            	.withName("Gender")
                            	.withHierarchies(List.of(
                                ExplicitHierarchyMappingImpl.builder()
                                    .withHasAll(false)
                                    .withPrimaryKey(FoodmartMappingSupplier.CUSTOMER_ID_COLUMN_IN_CUSTOMER)
                                    .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.CUSTOMER_TABLE).build())
                                    .withLevels(List.of(
                                        LevelMappingImpl.builder()
                                            .withName("Gender")
                                            .withColumn(FoodmartMappingSupplier.GENDER_COLUMN_IN_CUSTOMER)
                                            .withUniqueMembers(true)
                                            .build()
                                    ))
                                    .build()
                            )).build())
                            .build()
                    ))
                    .withMeasureGroups(List.of(MeasureGroupMappingImpl.builder()
                    		.withMeasures(List.of(
                                SumMeasureMappingImpl.builder()
                                    .withName("Unit Sales")
                                    .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_SALES_FACT_1997)
                                    .withFormatString("Standard")
                                    .withVisible(false)
                                    .build(),
                                SumMeasureMappingImpl.builder()
                                    .withName("Store Cost")
                                    .withColumn(FoodmartMappingSupplier.STORE_COST_COLUMN_IN_SALES_FACT_1997)
                                    .withFormatString("#,###.00")
                                    .build()
                    		))
                    		.build()))
                    .build());
                return result;
            }
        }
        */
        String query =
            "select {[Measures].[Unit Sales]} on columns, "
            + "non empty CrossJoin({[Product].[Food].[Deli].[Meat]},{[Gender].[M]}) on rows "
            + "from [Sales_Prod_Ord] ";

        // first check that the sql is generated correctly

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`product_family` as `c0`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`product_department` as `c1`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`product_category` as `c2`,\n"
                + "    `product_class`.`product_family` as `c3`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`gender` as `c4`\n"
                + "from\n"
                + "    `agg_g_ms_pcat_sales_fact_1997` as `agg_g_ms_pcat_sales_fact_1997`,\n"
                + "    `product_class` as `product_class`\n"
                + "where\n"
                + "    `product_class`.`product_category` = `agg_g_ms_pcat_sales_fact_1997`.`product_category`\n"
                + "and\n"
                + "    (`agg_g_ms_pcat_sales_fact_1997`.`product_category` = 'Meat' and `agg_g_ms_pcat_sales_fact_1997`.`product_department` = 'Deli' and `agg_g_ms_pcat_sales_fact_1997`.`product_family` = 'Food')\n"
                + "and\n"
                + "    (`agg_g_ms_pcat_sales_fact_1997`.`gender` = 'M')\n"
                + "group by\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`product_family`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`product_department`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`product_category`,\n"
                + "    `product_class`.`product_family`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`gender`\n"
                + "order by\n"
                + "    ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`product_family`) ASC, `agg_g_ms_pcat_sales_fact_1997`.`product_family` ASC,\n"
                + "    ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`product_department`) ASC, `agg_g_ms_pcat_sales_fact_1997`.`product_department` ASC,\n"
                + "    ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`product_category`) ASC, `agg_g_ms_pcat_sales_fact_1997`.`product_category` ASC,\n"
                + "    ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`gender`) ASC, `agg_g_ms_pcat_sales_fact_1997`.`gender` ASC",
                null)
        };

        SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).keepCache().expectSql(patterns).verify();

        assertThatQuery(context.getConnectionWithDefaultRole(),
            query).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Product].[Food].[Deli].[Meat], [Gender].[M]}\n"
            + "Row #0: 4,705\n");

        Result result = executeQuery(query, context.getConnectionWithDefaultRole());
        // this verifies that the caption for meat is Food
        assertEquals(
            "Meat",
            result.getAxes()[1].getPositions().get(0).get(0).getName());
        assertEquals(
            "Food",
            result.getAxes()[1].getPositions().get(0).get(0).getCaption());

        // Test children
        query =
            "select {[Measures].[Unit Sales]} on columns, "
            + "non empty [Product].[Food].[Deli].Children on rows "
            + "from [Sales_Prod_Ord] ";

        assertThatQuery(context.getConnectionWithDefaultRole(),
            query).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Product].[Food].[Deli].[Meat]}\n"
            + "{[Product].[Food].[Deli].[Side Dishes]}\n"
            + "Row #0: 4,728\n"
            + "Row #1: 1,262\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "1", type = Integer.class)
    void testAggregatingTuples(Context<?> context) {
        prepareContext(context);
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
                && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }
        if (!(context.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class))) {
            return;
        }
        // flush cache, to be sure sql is executed
        Connection connection = context.getConnectionWithDefaultRole();
        flushSchemaCache(connection);

        // This first query verifies that simple collapsed levels in aggregate
        // tables load as tuples correctly.  The collapsed levels appear
        // in the aggregate table SQL below.

        // also note that at the time of this writing, this exercising the high
        // cardinality tuple reader

        String query =
            "select {[Measures].[Unit Sales]} on columns, "
            + "non empty CrossJoin({[Gender].[M]},{[Marital Status].[M]}) on rows "
            + "from [Sales] ";

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select "
                + "`agg_g_ms_pcat_sales_fact_1997`.`gender` as `c0`, "
                + "`agg_g_ms_pcat_sales_fact_1997`.`marital_status` as `c1` "
                + "from "
                + "`agg_g_ms_pcat_sales_fact_1997` as `agg_g_ms_pcat_sales_fact_1997` "
                + "where "
                + "(`agg_g_ms_pcat_sales_fact_1997`.`gender` = 'M') "
                + "and (`agg_g_ms_pcat_sales_fact_1997`.`marital_status` = 'M') "
                + "group by "
                + "`agg_g_ms_pcat_sales_fact_1997`.`gender`, "
                + "`agg_g_ms_pcat_sales_fact_1997`.`marital_status` "
                + "order by "
                + "ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`gender`) ASC, "
                + "`agg_g_ms_pcat_sales_fact_1997`.`gender` ASC, "
                + "ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`marital_status`) ASC, "
                + "`agg_g_ms_pcat_sales_fact_1997`.`marital_status` ASC",
                null)
        };

        SqlAssert.forQuery(connection, query).keepCache().expectSql(patterns).verify();

        assertThatQuery(connection,
            query).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Gender].[M], [Marital Status].[M]}\n"
            + "Row #0: 66,460\n");

        // This second query verifies that joined levels on aggregate tables
        // load correctly.

        String query2 =
            "select {[Measures].[Unit Sales]} ON COLUMNS, "
            + "NON EMPTY {[Store].[Store State].Members} ON ROWS "
            + "from [Sales] where [Time].[1997].[Q1]";

        SqlPattern[] patterns2 = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select "
                + "`store`.`store_country` as `c0`, "
                + "`store`.`store_state` as `c1` "
                + "from "
                + "`store` as `store`, "
                + "`agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997` "
                + "where "
                + "`agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id` and "
                + "`agg_c_14_sales_fact_1997`.`the_year` = 1997 and "
                + "`agg_c_14_sales_fact_1997`.`quarter` = 'Q1' "
                + "group by "
                + "`store`.`store_country`, `store`.`store_state` "
                + "order by "
                + "ISNULL(`store`.`store_country`) ASC, "
                + "`store`.`store_country` ASC, "
                + "ISNULL(`store`.`store_state`) ASC, "
                + "`store`.`store_state` ASC",
                null)
        };

        SqlAssert.forQuery(connection, query2).keepCache().expectSql(patterns2).verify();

        assertThatQuery(connection,
            query2).returnsGrid(
            "Axis #0:\n"
            + "{[Time].[1997].[Q1]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[USA].[CA]}\n"
            + "{[Store].[USA].[OR]}\n"
            + "{[Store].[USA].[WA]}\n"
            + "Row #0: 16,890\n"
            + "Row #1: 19,287\n"
            + "Row #2: 30,114\n");
    }

    /**
     * this test verifies the collapsed children code in SqlMemberSource
     */
    @Test
    void testCollapsedChildren(Context<?> context) {
        prepareContext(context);
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
                && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }
        if (!(context.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class))) {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        // flush cache to be sure sql is executed
        flushSchemaCache(connection);

        SqlPattern[] patterns = {
            new SqlPattern(
                ACCESS_MYSQL,
                "select "
                + "`agg_g_ms_pcat_sales_fact_1997`.`gender` as `c0` "
                + "from `agg_g_ms_pcat_sales_fact_1997` "
                + "as `agg_g_ms_pcat_sales_fact_1997` "
                + "group by "
                + "`agg_g_ms_pcat_sales_fact_1997`.`gender`"
                + " order by ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`gender`) ASC, `agg_g_ms_pcat_sales_fact_1997`.`gender` ASC",
                null)
        };

        String query =
            "select non empty [Gender].Children on columns\n"
            + "from [Sales]";

        SqlAssert.forQuery(connection, query).keepCache().expectSql(patterns).verify();

        assertThatQuery(connection,
            query).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Gender].[F]}\n"
            + "{[Gender].[M]}\n"
            + "Row #0: 131,558\n"
            + "Row #0: 135,215\n");
    }

    /**
     * Test case for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-812">bug MONDRIAN-812,
     * "Issues with aggregate table recognition when using
     * &lt;KeyExpression&gt;&lt;SQL&gt; ... &lt;/SQL&gt;&lt;/KeyExpression&gt;
     * to define a level"</a>. Using a key expression for a level
     * element would make aggregate tables fail to be used.
     */
    @Disabled // schema depends on a runtime-computed dialect-quoted column name and needs two sequential schema states within one test — incompatible with eager, static catalog composition
    /**
     * The error half of what used to be a single test that swapped the
     * catalog mid-test (see {@link #testLevelKeyAsSqlExpWithAgg}) - split
     * because a {@code @RolapContextTest}-based test's catalog is fixed for
     * the whole method.
     */
    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier2.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testLevelKeyAsSqlExpWithAggError(Context<?> context) {
        prepareContext(context);
        // Provoke an error in the key resolution to prove it uses it.
        assertThatQuery(context.getConnectionWithDefaultRole(), "select non empty{[Promotions].[All Promotions].Children} ON rows, "
            + "non empty {[Store].[All Stores]} ON columns "
            + "from [Sales] "
            + "where {[Measures].[Unit Sales]}")
            .throwsMessage("ERROR_TEST_FUNCTION_NAME");
    }

    /**
     * Test case for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1120">MONDRIAN-1120</a>
     * run for real this time (see {@link #testLevelKeyAsSqlExpWithAggError}).
     */
    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier10.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testLevelKeyAsSqlExpWithAgg(Context<?> context) {
        prepareContext(context);
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select non empty{[Promotions].[All Promotions].Children} ON rows, "
            + "non empty {[Store].[All Stores]} ON columns "
            + "from [Sales] "
            + "where {[Measures].[Unit Sales]}").returnsGrid(
            "Axis #0:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #1:\n"
            + "{[Store].[Store].[All Stores]}\n"
            + "Axis #2:\n"
            + "{[Promotions].[Promotions].[Bag Stuffers]}\n"
            + "{[Promotions].[Promotions].[Best Savings]}\n"
            + "{[Promotions].[Promotions].[Big Promo]}\n"
            + "{[Promotions].[Promotions].[Big Time Discounts]}\n"
            + "{[Promotions].[Promotions].[Big Time Savings]}\n"
            + "{[Promotions].[Promotions].[Bye Bye Baby]}\n"
            + "{[Promotions].[Promotions].[Cash Register Lottery]}\n"
            + "{[Promotions].[Promotions].[Dimes Off]}\n"
            + "{[Promotions].[Promotions].[Dollar Cutters]}\n"
            + "{[Promotions].[Promotions].[Dollar Days]}\n"
            + "{[Promotions].[Promotions].[Double Down Sale]}\n"
            + "{[Promotions].[Promotions].[Double Your Savings]}\n"
            + "{[Promotions].[Promotions].[Free For All]}\n"
            + "{[Promotions].[Promotions].[Go For It]}\n"
            + "{[Promotions].[Promotions].[Green Light Days]}\n"
            + "{[Promotions].[Promotions].[Green Light Special]}\n"
            + "{[Promotions].[Promotions].[High Roller Savings]}\n"
            + "{[Promotions].[Promotions].[I Cant Believe It Sale]}\n"
            + "{[Promotions].[Promotions].[Money Savers]}\n"
            + "{[Promotions].[Promotions].[Mystery Sale]}\n"
            + "{[Promotions].[Promotions].[No Promotion]}\n"
            + "{[Promotions].[Promotions].[One Day Sale]}\n"
            + "{[Promotions].[Promotions].[Pick Your Savings]}\n"
            + "{[Promotions].[Promotions].[Price Cutters]}\n"
            + "{[Promotions].[Promotions].[Price Destroyers]}\n"
            + "{[Promotions].[Promotions].[Price Savers]}\n"
            + "{[Promotions].[Promotions].[Price Slashers]}\n"
            + "{[Promotions].[Promotions].[Price Smashers]}\n"
            + "{[Promotions].[Promotions].[Price Winners]}\n"
            + "{[Promotions].[Promotions].[Sale Winners]}\n"
            + "{[Promotions].[Promotions].[Sales Days]}\n"
            + "{[Promotions].[Promotions].[Sales Galore]}\n"
            + "{[Promotions].[Promotions].[Save-It Sale]}\n"
            + "{[Promotions].[Promotions].[Saving Days]}\n"
            + "{[Promotions].[Promotions].[Savings Galore]}\n"
            + "{[Promotions].[Promotions].[Shelf Clearing Days]}\n"
            + "{[Promotions].[Promotions].[Shelf Emptiers]}\n"
            + "{[Promotions].[Promotions].[Super Duper Savers]}\n"
            + "{[Promotions].[Promotions].[Super Savers]}\n"
            + "{[Promotions].[Promotions].[Super Wallet Savers]}\n"
            + "{[Promotions].[Promotions].[Three for One]}\n"
            + "{[Promotions].[Promotions].[Tip Top Savings]}\n"
            + "{[Promotions].[Promotions].[Two Day Sale]}\n"
            + "{[Promotions].[Promotions].[Two for One]}\n"
            + "{[Promotions].[Promotions].[Unbeatable Price Savers]}\n"
            + "{[Promotions].[Promotions].[Wallet Savers]}\n"
            + "{[Promotions].[Promotions].[Weekend Markdown]}\n"
            + "{[Promotions].[Promotions].[You Save Days]}\n"
            + "Row #0: 901\n"
            + "Row #1: 2,081\n"
            + "Row #2: 1,789\n"
            + "Row #3: 932\n"
            + "Row #4: 700\n"
            + "Row #5: 921\n"
            + "Row #6: 4,792\n"
            + "Row #7: 1,219\n"
            + "Row #8: 781\n"
            + "Row #9: 1,652\n"
            + "Row #10: 1,959\n"
            + "Row #11: 843\n"
            + "Row #12: 1,638\n"
            + "Row #13: 689\n"
            + "Row #14: 1,607\n"
            + "Row #15: 436\n"
            + "Row #16: 2,654\n"
            + "Row #17: 253\n"
            + "Row #18: 899\n"
            + "Row #19: 1,021\n"
            + "Row #20: 195,448\n"
            + "Row #21: 1,973\n"
            + "Row #22: 323\n"
            + "Row #23: 1,624\n"
            + "Row #24: 2,173\n"
            + "Row #25: 4,094\n"
            + "Row #26: 1,148\n"
            + "Row #27: 504\n"
            + "Row #28: 1,294\n"
            + "Row #29: 444\n"
            + "Row #30: 2,055\n"
            + "Row #31: 2,572\n"
            + "Row #32: 2,203\n"
            + "Row #33: 1,446\n"
            + "Row #34: 1,382\n"
            + "Row #35: 754\n"
            + "Row #36: 2,118\n"
            + "Row #37: 2,628\n"
            + "Row #38: 2,497\n"
            + "Row #39: 1,183\n"
            + "Row #40: 1,155\n"
            + "Row #41: 525\n"
            + "Row #42: 2,053\n"
            + "Row #43: 335\n"
            + "Row #44: 2,100\n"
            + "Row #45: 916\n"
            + "Row #46: 914\n"
            + "Row #47: 3,145\n");
    }

    /**
     * This is a test for MONDRIAN-918 and MONDRIAN-903. We have added
     * an attribute to AggName called approxRowCount so that the
     * aggregation manager can optimize the aggregation tables without
     * having to issue a select count() query.
     */
    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier5.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testAggNameApproxRowCount(Context<?> context) {
        prepareContext(context);
        /*
        withSchema(context,
                "<Schema name=\"FooSchema\"><Cube name=\"Sales_Foo\" defaultMeasure=\"Unit Sales\">\n"
                + "  <Table name=\"sales_fact_1997\">\n"
                + " <AggName name=\"agg_pl_01_sales_fact_1997\" approxRowCount=\"86000\">\n"
                + "     <AggFactCount column=\"FACT_COUNT\"/>\n"
                + "     <AggForeignKey factColumn=\"product_id\" aggColumn=\"PRODUCT_ID\" />\n"
                + "     <AggForeignKey factColumn=\"customer_id\" aggColumn=\"CUSTOMER_ID\" />\n"
                + "     <AggForeignKey factColumn=\"time_id\" aggColumn=\"TIME_ID\" />\n"
                + "     <AggMeasure name=\"[Measures].[Unit Sales]\" column=\"UNIT_SALES_SUM\" />\n"
                + "     <AggMeasure name=\"[Measures].[Store Cost]\" column=\"STORE_COST_SUM\" />\n"
                + "     <AggMeasure name=\"[Measures].[Store Sales]\" column=\"STORE_SALES_SUM\" />\n"
                + " </AggName>\n"
                + "    <AggExclude name=\"agg_c_special_sales_fact_1997\" />\n"
                + "    <AggExclude name=\"agg_lc_100_sales_fact_1997\" />\n"
                + "    <AggExclude name=\"agg_lc_10_sales_fact_1997\" />\n"
                + "    <AggExclude name=\"agg_pc_10_sales_fact_1997\" />\n"
                + "  </Table>\n"
                + "<Dimension name=\"Time\" type=\"TimeDimension\" foreignKey=\"time_id\">\n"
                + "    <Hierarchy hasAll=\"true\" name=\"Weekly\" primaryKey=\"time_id\">\n"
                + "      <Table name=\"time_by_day\"/>\n"
                + "      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"\n"
                + "          levelType=\"TimeYears\"/>\n"
                + "      <Level name=\"Week\" column=\"week_of_year\" type=\"Numeric\" uniqueMembers=\"false\"\n"
                + "          levelType=\"TimeWeeks\"/>\n"
                + "      <Level name=\"Day\" column=\"day_of_month\" uniqueMembers=\"false\" type=\"Numeric\"\n"
                + "          levelType=\"TimeDays\"/>\n"
                + "    </Hierarchy>\n"
                + "</Dimension>\n"
                + "<Dimension name=\"Product\" foreignKey=\"product_id\">\n"
                + "    <Hierarchy hasAll=\"true\" primaryKey=\"product_id\" primaryKeyTable=\"product\">\n"
                + "      <Join leftKey=\"product_class_id\" rightKey=\"product_class_id\">\n"
                + "        <Table name=\"product\"/>\n"
                + "        <Table name=\"product_class\"/>\n"
                + "      </Join>\n"
                + "      <Level name=\"Product Family\" table=\"product_class\" column=\"product_family\"\n"
                + "          uniqueMembers=\"true\"/>\n"
                + "      <Level name=\"Product Department\" table=\"product_class\" column=\"product_department\"\n"
                + "          uniqueMembers=\"false\"/>\n"
                + "      <Level name=\"Product Category\" table=\"product_class\" column=\"product_category\"\n"
                + "          uniqueMembers=\"false\"/>\n"
                + "      <Level name=\"Product Subcategory\" table=\"product_class\" column=\"product_subcategory\"\n"
                + "          uniqueMembers=\"false\"/>\n"
                + "      <Level name=\"Brand Name\" table=\"product\" column=\"brand_name\" uniqueMembers=\"false\"/>\n"
                + "      <Level name=\"Product Name\" table=\"product\" column=\"product_name\"\n"
                + "          uniqueMembers=\"true\"/>\n"
                + "    </Hierarchy>\n"
                + "</Dimension>\n"
                + "  <Dimension name=\"Customers\" foreignKey=\"customer_id\">\n"
                + "    <Hierarchy hasAll=\"true\" allMemberName=\"All Customers\" primaryKey=\"customer_id\">\n"
                + "      <Table name=\"customer\"/>\n"
                + "      <Level name=\"Country\" column=\"country\" uniqueMembers=\"true\"/>\n"
                + "      <Level name=\"State Province\" column=\"state_province\" uniqueMembers=\"true\"/>\n"
                + "      <Level name=\"City\" column=\"city\" uniqueMembers=\"false\"/>\n"
                + "      <Level name=\"Name\" column=\"customer_id\" type=\"Numeric\" uniqueMembers=\"true\">\n"
                + "        <NameExpression>\n"
                + "          <SQL dialect=\"oracle\">\n"
                + "\"fname\" || ' ' || \"lname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"hive\">\n"
                + "`customer`.`fullname`\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"hsqldb\">\n"
                + "\"fname\" || ' ' || \"lname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"access\">\n"
                + "fname + ' ' + lname\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"postgres\">\n"
                + "\"fname\" || ' ' || \"lname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"mysql\">\n"
                + "CONCAT(`customer`.`fname`, ' ', `customer`.`lname`)\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"mssql\">\n"
                + "fname + ' ' + lname\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"derby\">\n"
                + "\"customer\".\"fullname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"db2\">\n"
                + "CONCAT(CONCAT(\"customer\".\"fname\", ' '), \"customer\".\"lname\")\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"luciddb\">\n"
                + "\"fname\" || ' ' || \"lname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"neoview\">\n"
                + "\"customer\".\"fullname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"teradata\">\n"
                + "\"fname\" || ' ' || \"lname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"generic\">\n"
                + "fullname\n"
                + "          </SQL>\n"
                + "        </NameExpression>\n"
                + "        <OrdinalExpression>\n"
                + "          <SQL dialect=\"oracle\">\n"
                + "\"fname\" || ' ' || \"lname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"hsqldb\">\n"
                + "\"fname\" || ' ' || \"lname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"access\">\n"
                + "fname + ' ' + lname\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"postgres\">\n"
                + "\"fname\" || ' ' || \"lname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"mysql\">\n"
                + "CONCAT(`customer`.`fname`, ' ', `customer`.`lname`)\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"mssql\">\n"
                + "fname + ' ' + lname\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"neoview\">\n"
                + "\"customer\".\"fullname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"derby\">\n"
                + "\"customer\".\"fullname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"db2\">\n"
                + "CONCAT(CONCAT(\"customer\".\"fname\", ' '), \"customer\".\"lname\")\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"luciddb\">\n"
                + "\"fname\" || ' ' || \"lname\"\n"
                + "          </SQL>\n"
                + "          <SQL dialect=\"generic\">\n"
                + "fullname\n"
                + "          </SQL>\n"
                + "        </OrdinalExpression>\n"
                + "        <Property name=\"Gender\" column=\"gender\"/>\n"
                + "        <Property name=\"Marital Status\" column=\"marital_status\"/>\n"
                + "        <Property name=\"Education\" column=\"education\"/>\n"
                + "        <Property name=\"Yearly Income\" column=\"yearly_income\"/>\n"
                + "      </Level>\n"
                + "    </Hierarchy>\n"
                + "  </Dimension>\n"
                + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
                + "      formatString=\"Standard\"/>\n"
                + "  <Measure name=\"Store Cost\" column=\"store_cost\" aggregator=\"sum\"\n"
                + "      formatString=\"#,###.00\"/>\n"
                + "  <Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\"\n"
                + "      formatString=\"#,###.00\"/>\n"
                + "  <Measure name=\"Sales Count\" column=\"product_id\" aggregator=\"count\"\n"
                + "      formatString=\"#,###\"/>\n"
                + "  <Measure name=\"Customer Count\" column=\"customer_id\"\n"
                + "      aggregator=\"distinct-count\" formatString=\"#,###\"/>\n"
                + "</Cube></Schema>\n");
         */
        final String mdxQuery =
            "select {[Measures].[Unit Sales]} on columns, "
            + "non empty CrossJoin({[Time].[Weekly].[1997].[1].[15]},CrossJoin({[Customers].[USA].[CA].[Lincoln Acres].[William Smith]}, {[Product].[Drink].[Beverages].[Carbonated Beverages].[Soda].[Washington].[Washington Diet Cola]})) on rows "
            + "from [Sales_Foo] ";
        final String sqlOracle =
            "select count(*) as \"c0\" from \"agg_pl_01_sales_fact_1997\" \"agg_pl_01_sales_fact_1997\"";
        final String sqlMysql =
            "select count(*) as `c0` from `agg_pl_01_sales_fact_1997` as `agg_pl_01_sales_fact_1997`";
        // If the approxRowcount is used, there should not be
        // a query like : select count(*) from agg_pl_01_sales_fact_1997
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            mdxQuery).keepCache().expectNoSql(new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.ORACLE,
                    sqlOracle,
                    sqlOracle.length()),
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysql,
                    sqlMysql.length())
            }).verify();
        context.getCatalogCache().clear();
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, TestNonCollapsedAggregateModifier.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNonCollapsedAggregate(Context<?> context) {
        prepareContext(context);
        /*
        final String cube =
            "<Cube name=\"Foo\" defaultMeasure=\"Unit Sales\">\n"
            + "  <Table name=\"sales_fact_1997\">\n"
            + "    <AggExclude name=\"agg_g_ms_pcat_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_c_14_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_pl_01_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_ll_01_sales_fact_1997\"/>"
            + "    <AggName name=\"agg_l_05_sales_fact_1997\">"
            + "        <AggFactCount column=\"fact_count\"/>\n"
            + "        <AggIgnoreColumn column=\"customer_id\"/>\n"
            + "        <AggIgnoreColumn column=\"store_id\"/>\n"
            + "        <AggIgnoreColumn column=\"promotion_id\"/>\n"
            + "        <AggIgnoreColumn column=\"store_sales\"/>\n"
            + "        <AggIgnoreColumn column=\"store_cost\"/>\n"
            + "        <AggMeasure name=\"[Measures].[Unit Sales]\" column=\"unit_sales\" />\n"
            + "        <AggLevel name=\"[Product].[Product Id]\" column=\"product_id\" collapsed=\"false\"/>\n"
            + "    </AggName>\n"
            + "</Table>\n"
            + "<Dimension foreignKey=\"product_id\" name=\"Product\">\n"
            + "<Hierarchy hasAll=\"true\" primaryKey=\"product_id\" primaryKeyTable=\"product\">\n"
            + "  <Join leftKey=\"product_class_id\" rightKey=\"product_class_id\">\n"
            + " <Table name=\"product\"/>\n"
            + " <Table name=\"product_class\"/>\n"
            + "  </Join>\n"
            + "  <Level name=\"Product Family\" table=\"product_class\" column=\"product_family\"\n"
            + "   uniqueMembers=\"true\"/>\n"
            + "  <Level name=\"Product Department\" table=\"product_class\" column=\"product_department\"\n"
            + "   uniqueMembers=\"false\"/>\n"
            + "  <Level name=\"Product Category\" table=\"product_class\" column=\"product_category\"\n"
            + "   uniqueMembers=\"false\"/>\n"
            + "  <Level name=\"Product Subcategory\" table=\"product_class\" column=\"product_subcategory\"\n"
            + "   uniqueMembers=\"false\"/>\n"
            + "  <Level name=\"Brand Name\" table=\"product\" column=\"brand_name\" uniqueMembers=\"false\"/>\n"
            + "  <Level name=\"Product Name\" table=\"product\" column=\"product_name\"\n"
            + "   uniqueMembers=\"true\"/>\n"
            + "  <Level name=\"Product Id\" table=\"product\" column=\"product_id\"\n"
            + "   uniqueMembers=\"true\"/>\n"
            + "</Hierarchy>\n"
            + "</Dimension>\n"
            + "<Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "</Cube>\n";
         */
        /*
        class TestNonCollapsedAggregateModifier extends PojoMappingModifier {

        	private static final SumMeasureMappingImpl m = SumMeasureMappingImpl.builder()
            .withName("Unit Sales")
            .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_SALES_FACT_1997)
            .withFormatString("Standard")
            .withVisible(false)
            .build();


            public TestNonCollapsedAggregateModifier(CatalogMapping catalog) {
                super(catalog);
            }

            @Override
            protected List<CubeMapping> cubes(List<? extends CubeMapping> cubes) {
                List<CubeMapping> result = new ArrayList<>();
                result.add(PhysicalCubeMappingImpl.builder()
                    .withName("Foo")
                    .withDefaultMeasure(m)
                    .withQuery(TableQueryMappingImpl.builder()
                    		.withTable(FoodmartMappingSupplier.SALES_FACT_1997_TABLE)
                    		.withAggregationExcludes(List.of(
                    			AggregationExcludeMappingImpl.builder()
                                    .withName("agg_g_ms_pcat_sales_fact_1997")
                                    .build(),
                                AggregationExcludeMappingImpl.builder()
                                    .withName("agg_c_14_sales_fact_1997")
                                    .build(),
                                AggregationExcludeMappingImpl.builder()
                                    .withName("agg_pl_01_sales_fact_1997")
                                    .build(),
                                AggregationExcludeMappingImpl.builder()
                                    .withName("agg_ll_01_sales_fact_1997")
                                    .build()
                    		))
                    		.withAggregationTables(List.of(
                    				AggregationNameMappingImpl.builder()
                                    .withName(FoodmartMappingSupplier.AGG_L_05_SALES_FACT_1997)
                                    .withAggregationFactCount(AggregationColumnNameMappingImpl.builder().withColumn(FoodmartMappingSupplier.FACT_COUNT_COLUMN_IN_AGG_L_05_SALES_FACT_1997).build())
                                    .withAggregationIgnoreColumns(List.of(
                                    	AggregationColumnNameMappingImpl.builder()
                                           	.withColumn(FoodmartMappingSupplier.CUSTOMER_ID_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                            .build(),
                                        AggregationColumnNameMappingImpl.builder()
                                            .withColumn(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                            .build(),
                                        AggregationColumnNameMappingImpl.builder()
                                            .withColumn(FoodmartMappingSupplier.PROMOTION_ID_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                            .build(),
                                        AggregationColumnNameMappingImpl.builder()
                                    		.withColumn(FoodmartMappingSupplier.STORE_SALES_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                    		.build(),
                                    	AggregationColumnNameMappingImpl.builder()
                                            .withColumn(FoodmartMappingSupplier.STORE_COST_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                            .build()
                                    ))
                                    .withAggregationMeasures(List.of(
                                    		AggregationMeasureMappingImpl.builder()
                                            .withName("[Measures].[Unit Sales]")
                                            .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                            .build()
                                    ))
                                    .withAggregationLevels(List.of(
                                    	AggregationLevelMappingImpl.builder()
                                            .withName("[Product].[Product].[Product Id]")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                            .withCollapsed(false)
                                            .build()
                                    ))
                                    .build()
                    				))
                    		.build())
                    .withDimensionConnectors(List.of(
                    		DimensionConnectorMappingImpl
                    		.builder()
                    		.withOverrideDimensionName("Product")
                    		.withForeignKey(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_SALES_FACT_1997)
                    		.withDimension(
                    				StandardDimensionMappingImpl.builder()
                                    .withName("Product")
                                    .withHierarchies(List.of(
                                        ExplicitHierarchyMappingImpl.builder()
                                            .withHasAll(true)
                                            .withPrimaryKey(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_PRODUCT)
                                            .withQuery(
                                                    JoinQueryMappingImpl.builder()
                                                    .withLeft(
                                                    	JoinedQueryElementMappingImpl.builder()
                                                    		.withKey(FoodmartMappingSupplier.PRODUCT_CLASS_ID_COLUMN_IN_PRODUCT)
                                                    		.withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.PRODUCT_TABLE).build())
                                                    		.build())
                                                    .withRight(
                                                    	JoinedQueryElementMappingImpl.builder()
                                                    		.withKey(FoodmartMappingSupplier.PRODUCT_CLASS_ID_COLUMN_IN_PRODUCT_CLASS)
                                                    		.withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.PRODUCT_CLASS_TABLE).build())
                                                    		.build())
                                                    .build()
                                                )
                                            .withLevels(List.of(
                                                LevelMappingImpl.builder()
                                                    .withName("Product Family")
                                                    .withColumn(FoodmartMappingSupplier.PRODUCT_FAMILY_COLUMN_IN_PRODUCT_CLASS)
                                                    .withUniqueMembers(true)
                                                    .build(),
                                                LevelMappingImpl.builder()
                                                    .withName("Product Department")
                                                    .withColumn(FoodmartMappingSupplier.PRODUCT_DEPARTMENT_COLUMN_IN_PRODUCT_CLASS)
                                                    .withUniqueMembers(false)
                                                    .build(),
                                                LevelMappingImpl.builder()
                                                    .withName("Product Category")
                                                    .withColumn(FoodmartMappingSupplier.PRODUCT_CATEGORY_COLUMN_IN_PRODUCT_CLASS)
                                                    .withUniqueMembers(false)
                                                    .build(),
                                                LevelMappingImpl.builder()
                                                    .withName("Product Subcategory")
                                                    .withColumn(FoodmartMappingSupplier.PRODUCT_SUBCATEGORY_COLUMN_IN_PRODUCT_CLASS)
                                                    .withUniqueMembers(false)
                                                    .build(),
                                                LevelMappingImpl.builder()
                                                    .withName("Brand Name")
                                                    .withColumn(FoodmartMappingSupplier.BRAND_NAME_COLUMN_IN_PRODUCT)
                                                    .withUniqueMembers(false)
                                                    .build(),
                                                LevelMappingImpl.builder()
                                                    .withName("Product Name")
                                                    .withColumn(FoodmartMappingSupplier.PRODUCT_NAME_COLUMN_IN_PRODUCT)
                                                    .withUniqueMembers(true)
                                                    .build(),
                                                LevelMappingImpl.builder()
                                                    .withName("Product Id")
                                                    .withColumn(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_PRODUCT)
                                                    .withUniqueMembers(true)
                                                    .build()
                                            ))
                                            .build()
                                    ))
                    				.build())
                    		.build()))
                    .withMeasureGroups(List.of(MeasureGroupMappingImpl.builder()
                    		.withMeasures(List.of(m))
                    		.build()))
                    .build());
                result.addAll(super.cubes(cubes));
                return result;
            }
        }
        */
        final String mdx =
            "select {[Product].[Product].[Product Family].Members} on rows, {[Measures].[Unit Sales]} on columns from [Foo]";
        final String sqlOracle =
            "select \"product_class\".\"product_family\" as \"c0\", sum(\"agg_l_05_sales_fact_1997\".\"unit_sales\") as \"m0\" from \"product_class\" \"product_class\", \"product\" \"product\", \"agg_l_05_sales_fact_1997\" \"agg_l_05_sales_fact_1997\" where \"agg_l_05_sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" group by \"product_class\".\"product_family\"";
        final String sqlMysql =
            "select `product_class`.`product_family` as `c0`, sum(`agg_l_05_sales_fact_1997`.`unit_sales`) as `m0` from `product_class` as `product_class` join `product` as `product` on `product`.`product_class_id` = `product_class`.`product_class_id` join `agg_l_05_sales_fact_1997` as `agg_l_05_sales_fact_1997` on `agg_l_05_sales_fact_1997`.`product_id` = `product`.`product_id` group by `product_class`.`product_family`";
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            mdx).expectSql(new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.ORACLE,
                    sqlOracle,
                    sqlOracle.length()),
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysql,
                    sqlMysql.length())
            }).verify();
        context.getCatalogCache().clear();
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNonCollapsedAggregateAllLevelsPresentInQuerySnowflake(Context<?> context)
        throws Exception
    {
        prepareContext(context);
        // MONDRIAN-1072.
        /*
        final String cube =
            "<Schema name=\"AMC\"><Cube name=\"Foo\" defaultMeasure=\"Unit Sales\">\n"
            + "  <Table name=\"sales_fact_1997\">\n"
            + "    <AggExclude name=\"agg_g_ms_pcat_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_c_14_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_pl_01_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_ll_01_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_l_03_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_lc_06_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_l_04_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_c_10_sales_fact_1997\"/>"
            + "    <AggName name=\"agg_l_05_sales_fact_1997\">"
            + "        <AggFactCount column=\"fact_count\"/>\n"
            + "        <AggIgnoreColumn column=\"customer_id\"/>\n"
            + "        <AggIgnoreColumn column=\"store_id\"/>\n"
            + "        <AggIgnoreColumn column=\"promotion_id\"/>\n"
            + " <AggForeignKey factColumn=\"product_id\" aggColumn=\"product_id\"/>"
            + "        <AggMeasure name=\"[Measures].[Store Cost]\" column=\"store_cost\" />\n"
            + "        <AggMeasure name=\"[Measures].[Store Sales]\" column=\"store_sales\" />\n"
            + "        <AggMeasure name=\"[Measures].[Unit Sales]\" column=\"unit_sales\" />\n"
            + "    </AggName>\n"
            + "</Table>\n"
            + "  <Dimension name=\"Product\" foreignKey=\"product_id\">\n"
            + "<Hierarchy hasAll=\"true\" primaryKey=\"product_id\" primaryKeyTable=\"product\">\n"
            + "      <Join leftKey=\"product_class_id\" rightKey=\"product_class_id\">\n"
            + "        <Table name=\"product\"/>\n"
            + "        <Table name=\"product_class\"/>\n"
            + "     </Join>\n"
            + "     <Level name=\"Product Family\" table=\"product_class\" column=\"product_family\"\n"
            + "        uniqueMembers=\"true\"/>"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "<Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "<Measure name=\"Customer Count\" column=\"customer_id\" aggregator=\"distinct-count\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "<Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "<Measure name=\"Store Cost\" column=\"store_cost\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "</Cube></Schema>\n";
        withSchema(context, cube);
         */

        final String mdx =
            "select \n"
            + "{ "
            + "[Product].[Product Family].members } on rows, "
            + "{[Measures].[Unit Sales]} on columns from [Foo]";

        assertThatQuery(context.getConnectionWithDefaultRole(),
            mdx).returnsGrid(
            "Axis #0:\n"
            +    "{}\n"
            +    "Axis #1:\n"
            +    "{[Measures].[Unit Sales]}\n"
            +    "Axis #2:\n"
            +    "{[Product].[Product].[Drink]}\n"
            +    "{[Product].[Product].[Food]}\n"
            +    "{[Product].[Product].[Non-Consumable]}\n"
            +    "Row #0: 24,597\n"
            +    "Row #1: 191,940\n"
            +    "Row #2: 50,236\n");
        final String sqlMysql =
            "select `product_class`.`product_family` as `c0`, sum(`agg_l_05_sales_fact_1997`.`unit_sales`) as `m0` from `product_class` as `product_class` join `product` as `product` on `product`.`product_class_id` = `product_class`.`product_class_id` join `agg_l_05_sales_fact_1997` as `agg_l_05_sales_fact_1997` on `agg_l_05_sales_fact_1997`.`product_id` = `product`.`product_id` group by `product_class`.`product_family`";
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            mdx).expectSql(new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysql,
                    sqlMysql.length())
            }).verify();
    }


    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier8.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNonCollapsedAggregateAllLevelsPresentInQuery(Context<?> context)
        throws Exception
    {
        prepareContext(context);
        // MONDRIAN-1072
        /*
        final String cube =
            "<Schema name=\"AMC\"><Cube name=\"Foo\" defaultMeasure=\"Unit Sales\">\n"
            + "  <Table name=\"sales_fact_1997\">\n"
            + "    <AggExclude name=\"agg_g_ms_pcat_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_c_14_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_pl_01_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_ll_01_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_l_03_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_lc_06_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_l_04_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_c_10_sales_fact_1997\"/>"
            + "    <AggName name=\"agg_l_05_sales_fact_1997\">"
            + "        <AggFactCount column=\"fact_count\"/>\n"
            + "        <AggIgnoreColumn column=\"customer_id\"/>\n"
            + "        <AggIgnoreColumn column=\"store_id\"/>\n"
            + "        <AggIgnoreColumn column=\"promotion_id\"/>\n"
            + " <AggForeignKey factColumn=\"promotion_id\" aggColumn=\"promotion_id\"/>"
            + "        <AggMeasure name=\"[Measures].[Store Cost]\" column=\"store_cost\" />\n"
            + "        <AggMeasure name=\"[Measures].[Store Sales]\" column=\"store_sales\" />\n"
            + "        <AggMeasure name=\"[Measures].[Unit Sales]\" column=\"unit_sales\" />\n"
            + "    </AggName>\n"
            + "</Table>\n"
            + "  <Dimension name=\"Promotions\" foreignKey=\"promotion_id\">\n"
            + "    <Hierarchy hasAll=\"true\" allMemberName=\"All Promotions\" primaryKey=\"promotion_id\" defaultMember=\"[All Promotions]\">\n"
            + "      <Table name=\"promotion\"/>\n"
            + "      <Level name=\"Media Type\" column=\"media_type\" uniqueMembers=\"true\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>"
            + "<Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "<Measure name=\"Customer Count\" column=\"customer_id\" aggregator=\"distinct-count\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "<Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "<Measure name=\"Store Cost\" column=\"store_cost\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "</Cube></Schema>\n";
        withSchema(context, cube);
         */
        final String mdx =
            "select \n"
            + "{ "
            + "[Promotions].[Media Type].members } on rows, {[Measures].[Unit Sales]} on columns from [Foo]";

        assertThatQuery(context.getConnectionWithDefaultRole(),
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Promotions].[Promotions].[Bulk Mail]}\n"
            + "{[Promotions].[Promotions].[Cash Register Handout]}\n"
            + "{[Promotions].[Promotions].[Daily Paper]}\n"
            + "{[Promotions].[Promotions].[Daily Paper, Radio]}\n"
            + "{[Promotions].[Promotions].[Daily Paper, Radio, TV]}\n"
            + "{[Promotions].[Promotions].[In-Store Coupon]}\n"
            + "{[Promotions].[Promotions].[No Media]}\n"
            + "{[Promotions].[Promotions].[Product Attachment]}\n"
            + "{[Promotions].[Promotions].[Radio]}\n"
            + "{[Promotions].[Promotions].[Street Handout]}\n"
            + "{[Promotions].[Promotions].[Sunday Paper]}\n"
            + "{[Promotions].[Promotions].[Sunday Paper, Radio]}\n"
            + "{[Promotions].[Promotions].[Sunday Paper, Radio, TV]}\n"
            + "{[Promotions].[Promotions].[TV]}\n"
            + "Row #0: 4,320\n"
            + "Row #1: 6,697\n"
            + "Row #2: 7,738\n"
            + "Row #3: 6,891\n"
            + "Row #4: 9,513\n"
            + "Row #5: 3,798\n"
            + "Row #6: 195,448\n"
            + "Row #7: 7,544\n"
            + "Row #8: 2,454\n"
            + "Row #9: 5,753\n"
            + "Row #10: 4,339\n"
            + "Row #11: 5,945\n"
            + "Row #12: 2,726\n"
            + "Row #13: 3,607\n");
        final String sqlMysql =
            "select `promotion`.`media_type` as `c0`, sum(`agg_c_special_sales_fact_1997`.`unit_sales_sum`) as `m0` from `promotion` as `promotion` join `agg_c_special_sales_fact_1997` as `agg_c_special_sales_fact_1997` on `agg_c_special_sales_fact_1997`.`promotion_id` = `promotion`.`promotion_id` group by `promotion`.`media_type`";
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            mdx).expectSql(new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysql,
                    sqlMysql.length())
            }).verify();
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, TestTwoNonCollapsedAggregateModifier.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testTwoNonCollapsedAggregate(Context<?> context) throws Exception {
        prepareContext(context);
        /*
        final String cube =
            "<Cube name=\"Foo\" defaultMeasure=\"Unit Sales\">\n"
            + "  <Table name=\"sales_fact_1997\">\n"
            + "    <AggExclude name=\"agg_g_ms_pcat_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_c_14_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_pl_01_sales_fact_1997\"/>"
            + "    <AggExclude name=\"agg_ll_01_sales_fact_1997\"/>"
            + "    <AggName name=\"agg_l_05_sales_fact_1997\">"
            + "        <AggFactCount column=\"fact_count\"/>\n"
            + "        <AggIgnoreColumn column=\"customer_id\"/>\n"
            + "        <AggIgnoreColumn column=\"promotion_id\"/>\n"
            + "        <AggIgnoreColumn column=\"store_sales\"/>\n"
            + "        <AggIgnoreColumn column=\"store_cost\"/>\n"
            + "        <AggMeasure name=\"[Measures].[Unit Sales]\" column=\"unit_sales\" />\n"
            + "        <AggLevel name=\"[Product].[Product Id]\" column=\"product_id\" collapsed=\"false\"/>\n"
            + "        <AggLevel name=\"[Store].[Store Id]\" column=\"store_id\" collapsed=\"false\"/>\n"
            + "    </AggName>\n"
            + "</Table>\n"
            + "<Dimension foreignKey=\"product_id\" name=\"Product\">\n"
            + "<Hierarchy hasAll=\"true\" primaryKey=\"product_id\" primaryKeyTable=\"product\">\n"
            + "  <Join leftKey=\"product_class_id\" rightKey=\"product_class_id\">\n"
            + " <Table name=\"product\"/>\n"
            + " <Table name=\"product_class\"/>\n"
            + "  </Join>\n"
            + "  <Level name=\"Product Family\" table=\"product_class\" column=\"product_family\"\n"
            + "   uniqueMembers=\"true\"/>\n"
            + "  <Level name=\"Product Department\" table=\"product_class\" column=\"product_department\"\n"
            + "   uniqueMembers=\"false\"/>\n"
            + "  <Level name=\"Product Category\" table=\"product_class\" column=\"product_category\"\n"
            + "   uniqueMembers=\"false\"/>\n"
            + "  <Level name=\"Product Subcategory\" table=\"product_class\" column=\"product_subcategory\"\n"
            + "   uniqueMembers=\"false\"/>\n"
            + "  <Level name=\"Brand Name\" table=\"product\" column=\"brand_name\" uniqueMembers=\"false\"/>\n"
            + "  <Level name=\"Product Name\" table=\"product\" column=\"product_name\"\n"
            + "   uniqueMembers=\"true\"/>\n"
            + "  <Level name=\"Product Id\" table=\"product\" column=\"product_id\"\n"
            + "   uniqueMembers=\"true\"/>\n"
            + "</Hierarchy>\n"
            + "</Dimension>\n"
            + "  <Dimension name=\"Store\" foreignKey=\"store_id\" >\n"
            + "    <Hierarchy hasAll=\"true\" primaryKey=\"store_id\"\n"
            + "        primaryKeyTable=\"store\">\n"
            + "      <Join leftKey=\"region_id\" rightKey=\"region_id\">\n"
            + "        <Table name=\"store\"/>\n"
            + "        <Table name=\"region\"/>\n"
            + "      </Join>\n"
            + "      <Level name=\"Store Region\" table=\"region\" column=\"sales_city\"\n"
            + "          uniqueMembers=\"false\"/>\n"
            + "      <Level name=\"Store Id\" table=\"store\" column=\"store_id\"\n"
            + "          uniqueMembers=\"true\">\n"
            + "      </Level>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "<Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "</Cube>\n";
         */
        /*
        class TestTwoNonCollapsedAggregateModifier extends PojoMappingModifier {

            public TestTwoNonCollapsedAggregateModifier(CatalogMapping catalog) {
                super(catalog);
            }
            protected List<CubeMapping> cubes(List<? extends CubeMapping> cubes) {
            	SumMeasureMappingImpl m = SumMeasureMappingImpl.builder()
                        .withName("Unit Sales")
                        .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_SALES_FACT_1997)
                        .withFormatString("Standard")
                        .build();
                PhysicalColumnMappingImpl salesRegion = PhysicalColumnMappingImpl.builder().withName("sales_region").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(30).build();
                PhysicalColumnMappingImpl salesCity = PhysicalColumnMappingImpl.builder().withName("sales_city").withDataType(ColumnDataType.VARCHAR).withCharOctetLength(30).build();
                PhysicalColumnMappingImpl salesDistrictId = PhysicalColumnMappingImpl.builder().withName("sales_district_id").withDataType(ColumnDataType.INTEGER).build();
                PhysicalColumnMappingImpl regionId = PhysicalColumnMappingImpl.builder().withName("region_id").withDataType(ColumnDataType.INTEGER).build();
                PhysicalTableMappingImpl region = ((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("region")
                        .withColumns(List.of(
                                salesRegion, salesCity, salesDistrictId, regionId
                                ))).build();
                List<CubeMapping> result = new ArrayList<>();
                result.add(PhysicalCubeMappingImpl.builder()
                    .withName("Foo")
                    .withDefaultMeasure(m)
                    .withQuery(TableQueryMappingImpl.builder()
                    	.withTable(FoodmartMappingSupplier.SALES_FACT_1997_TABLE)
                    	.withAggregationExcludes(List.of(
                    		AggregationExcludeMappingImpl.builder()
                            .withName("agg_g_ms_pcat_sales_fact_1997")
                            .build(),
                            AggregationExcludeMappingImpl.builder()
                            .withName("agg_c_14_sales_fact_1997")
                            .build(),
                            AggregationExcludeMappingImpl.builder()
                            .withName("agg_pl_01_sales_fact_1997")
                            .build(),
                            AggregationExcludeMappingImpl.builder()
                            .withName("agg_ll_01_sales_fact_1997")
                            .build()
                    	))
                    	.withAggregationTables(List.of(
                    		AggregationNameMappingImpl.builder()
                            .withName(FoodmartMappingSupplier.AGG_L_05_SALES_FACT_1997)
                            .withAggregationFactCount(AggregationColumnNameMappingImpl.builder().withColumn(FoodmartMappingSupplier.FACT_COUNT_COLUMN_IN_AGG_L_05_SALES_FACT_1997).build())
                            .withAggregationIgnoreColumns(List.of(
                            	AggregationColumnNameMappingImpl.builder()
                                    .withColumn(FoodmartMappingSupplier.CUSTOMER_ID_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                    .build(),
                                AggregationColumnNameMappingImpl.builder()
                                    .withColumn(FoodmartMappingSupplier.PROMOTION_ID_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                    .build(),
                                AggregationColumnNameMappingImpl.builder()
                                    .withColumn(FoodmartMappingSupplier.STORE_SALES_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                    .build(),
                                AggregationColumnNameMappingImpl.builder()
                                    .withColumn(FoodmartMappingSupplier.STORE_COST_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                    .build()
                            ))
                            .withAggregationMeasures(List.of(
                            	AggregationMeasureMappingImpl.builder()
                                    .withName("[Measures].[Unit Sales]")
                                    .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                    .build()
                            ))
                            .withAggregationLevels(List.of(
                            	AggregationLevelMappingImpl.builder()
                                    .withName("[Product].[Product].[Product Id]")
                                    .withColumn(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                    .withCollapsed(false)
                                    .build(),
                                AggregationLevelMappingImpl.builder()
                                    .withName("[Store].[Store].[Store Id]")
                                    .withColumn(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_AGG_L_05_SALES_FACT_1997)
                                    .withCollapsed(false)
                                    .build()
                            ))
                            .build()
                    	)).build())
                    .withDimensionConnectors(List.of(
                    	DimensionConnectorMappingImpl.builder()
                    		.withOverrideDimensionName("Product")
                            .withForeignKey(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_SALES_FACT_1997)
                            .withDimension(StandardDimensionMappingImpl.builder()
                            	.withHierarchies(List.of(
                                ExplicitHierarchyMappingImpl.builder()
                                    .withHasAll(true)
                                    .withPrimaryKey(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_PRODUCT)
                                    .withQuery(
                                            JoinQueryMappingImpl.builder()
                                            .withLeft(
                                            	JoinedQueryElementMappingImpl.builder()
                                            		.withKey(FoodmartMappingSupplier.PRODUCT_CLASS_ID_COLUMN_IN_PRODUCT)
                                            		.withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.PRODUCT_TABLE).build())
                                            		.build())
                                            .withRight(
                                            	JoinedQueryElementMappingImpl.builder()
                                            		.withKey(FoodmartMappingSupplier.PRODUCT_CLASS_ID_COLUMN_IN_PRODUCT_CLASS)
                                            		.withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.PRODUCT_CLASS_TABLE).build())
                                            		.build())
                                            .build()
                                    )
                                    .withLevels(List.of(
                                        LevelMappingImpl.builder()
                                            .withName("Product Family")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_FAMILY_COLUMN_IN_PRODUCT_CLASS)
                                            .withUniqueMembers(true)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Product Department")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_DEPARTMENT_COLUMN_IN_PRODUCT_CLASS)
                                            .withUniqueMembers(false)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Product Category")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_CATEGORY_COLUMN_IN_PRODUCT_CLASS)
                                            .withUniqueMembers(false)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Product Subcategory")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_SUBCATEGORY_COLUMN_IN_PRODUCT_CLASS)
                                            .withUniqueMembers(false)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Brand Name")
                                            .withColumn(FoodmartMappingSupplier.BRAND_NAME_COLUMN_IN_PRODUCT)
                                            .withUniqueMembers(false)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Product Name")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_NAME_COLUMN_IN_PRODUCT)
                                            .withUniqueMembers(true)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Product Id")
                                            .withColumn(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_PRODUCT)
                                            .withUniqueMembers(true)
                                            .build()
                                    ))
                                    .build()
                            )).build())
                            .build(),
                        DimensionConnectorMappingImpl.builder()
                        	.withOverrideDimensionName("Store")
                            .withForeignKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_SALES_FACT_1997)
                            .withDimension(StandardDimensionMappingImpl.builder()
                            		.withName("Store")
                                	.withHierarchies(List.of(
                                    ExplicitHierarchyMappingImpl.builder()
                                    .withHasAll(true)
                                    .withPrimaryKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_STORE)
                                    .withQuery(
                                            JoinQueryMappingImpl.builder()
                                            .withLeft(
                                            	JoinedQueryElementMappingImpl.builder()
                                            		.withKey(FoodmartMappingSupplier.REGION_ID_COLUMN_IN_STORE)
                                            		.withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.STORE_TABLE).build())
                                            		.build())
                                            .withRight(
                                            	JoinedQueryElementMappingImpl.builder()
                                            		.withKey(regionId)
                                            		.withQuery(TableQueryMappingImpl.builder().withTable(region).build())
                                            		.build())
                                            .build()
                                    )
                                    .withLevels(List.of(
                                        LevelMappingImpl.builder()
                                            .withName("Store Region")
                                            .withColumn(salesCity)
                                            .withUniqueMembers(false)
                                            .build(),
                                        LevelMappingImpl.builder()
                                            .withName("Store Id")
                                            .withColumn(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_STORE)
                                            .withUniqueMembers(true)
                                            .build()
                                    ))
                                    .build()
                            )).build())
                            .build()

                    ))
                   .withMeasureGroups(List.of(MeasureGroupMappingImpl.builder()
                	    .withMeasures(List.of(
                	    	SumMeasureMappingImpl.builder()
                            	.withName("Unit Sales")
                                .withColumn(FoodmartMappingSupplier.UNIT_SALES_COLUMN_IN_SALES_FACT_1997)
                                .withFormatString("Standard")
                                .build()

                        )).build()))
                    .build());
                result.addAll(super.cubes(cubes));
                return result;
            }
        }
        */

        final String mdx =
            "select {Crossjoin([Product].[Product].[Product Family].Members, [Store].[Store].[Store Id].Members)} on rows, {[Measures].[Unit Sales]} on columns from [Foo]";
        final String sqlOracle =
            "select\n"
            + "    \"product_class\".\"product_family\" as \"c0\",\n"
            + "    \"agg_l_05_sales_fact_1997\".\"store_id\" as \"c1\",\n"
            + "    sum(\"agg_l_05_sales_fact_1997\".\"unit_sales\") as \"m0\"\n"
            + "from\n"
            + "    \"product_class\" \"product_class\",\n"
            + "    \"product\" \"product\",\n"
            + "    \"agg_l_05_sales_fact_1997\" \"agg_l_05_sales_fact_1997\"\n"
            + "where\n"
            + "    \"agg_l_05_sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"\n"
            + "and\n"
            + "    \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\"\n"
            + "group by\n"
            + "    \"product_class\".\"product_family\",\n"
            + "    \"agg_l_05_sales_fact_1997\".\"store_id\"";
        final String sqlMysql =
            "select `product_class`.`product_family` as `c0`, `agg_l_05_sales_fact_1997`.`store_id` as `c1`, sum(`agg_l_05_sales_fact_1997`.`unit_sales`) as `m0`";
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            mdx).expectSql(new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.ORACLE,
                    sqlOracle,
                    sqlOracle.length()),
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysql,
                    sqlMysql.length())
            }).verify();
    }

    /**
     * This is a test for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1221">MONDRIAN-1221</a>
     *
     * When performing a non-empty crossjoin over a virtual cube with agg
     * tables, there was no match with any agg tables.
     */
    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier9.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testVirtualCubeAggBugMondrian1221(Context<?> context) {
        prepareContext(context);
        /*
        final String schema =
            "<?xml version=\"1.0\"?>\n"
            + "<Schema name=\"custom\">\n"
            + "  <Dimension name=\"Store\">\n"
            + "    <Hierarchy hasAll=\"true\" primaryKey=\"store_id\">\n"
            + "      <Table name=\"store\"/>\n"
            + "      <Level name=\"Store Country\" column=\"store_country\" uniqueMembers=\"true\"/>\n"
            + "      <Level name=\"Store State\" column=\"store_state\" uniqueMembers=\"true\"/>\n"
            + "      <Level name=\"Store City\" column=\"store_city\" uniqueMembers=\"false\"/>\n"
            + "      <Level name=\"Store Name\" column=\"store_name\" uniqueMembers=\"true\">\n"
            + "      </Level>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "  <Dimension name=\"Time\" type=\"TimeDimension\">\n"
            + "    <Hierarchy hasAll=\"false\" primaryKey=\"time_id\">\n"
            + "      <Table name=\"time_by_day\"/>\n"
            + "      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"\n"
            + "          levelType=\"TimeYears\"/>\n"
            + "      <Level name=\"Quarter\" column=\"quarter\" uniqueMembers=\"false\"\n"
            + "          levelType=\"TimeQuarters\"/>\n"
            + "      <Level name=\"Month\" column=\"month_of_year\" uniqueMembers=\"false\" type=\"Numeric\"\n"
            + "          levelType=\"TimeMonths\"/>\n"
            + "    </Hierarchy>\n"
            + "    <Hierarchy hasAll=\"true\" name=\"Weekly\" primaryKey=\"time_id\">\n"
            + "      <Table name=\"time_by_day\"/>\n"
            + "      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"\n"
            + "          levelType=\"TimeYears\"/>\n"
            + "      <Level name=\"Week\" column=\"week_of_year\" type=\"Numeric\" uniqueMembers=\"false\"\n"
            + "          levelType=\"TimeWeeks\"/>\n"
            + "      <Level name=\"Day\" column=\"day_of_month\" uniqueMembers=\"false\" type=\"Numeric\"\n"
            + "          levelType=\"TimeDays\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "  <Cube name=\"Sales1\" defaultMeasure=\"Unit Sales\">\n"
            + "    <Table name=\"sales_fact_1997\">\n"
            + "      <AggName name=\"agg_c_special_sales_fact_1997\">\n"
            + "        <AggFactCount column=\"FACT_COUNT\"/>\n"
            + "        <AggIgnoreColumn column=\"foo\"/>\n"
            + "        <AggIgnoreColumn column=\"bar\"/>\n"
            + "        <AggIgnoreColumn column=\"PRODUCT_ID\" />\n"
            + "        <AggIgnoreColumn column=\"CUSTOMER_ID\" />\n"
            + "        <AggIgnoreColumn column=\"PROMOTION_ID\" />\n"
            + "        <AggForeignKey factColumn=\"store_id\" aggColumn=\"STORE_ID\" />\n"
            + "        <AggMeasure name=\"[Measures].[Unit Sales]\" column=\"UNIT_SALES_SUM\" />\n"
            + "        <AggMeasure name=\"[Measures].[Store Cost]\" column=\"STORE_COST_SUM\" />\n"
            + "        <AggMeasure name=\"[Measures].[Store Sales]\" column=\"STORE_SALES_SUM\" />\n"
            + "        <AggLevel name=\"[Time].[Year]\" column=\"TIME_YEAR\" />\n"
            + "        <AggLevel name=\"[Time].[Quarter]\" column=\"TIME_QUARTER\" />\n"
            + "        <AggLevel name=\"[Time].[Month]\" column=\"TIME_MONTH\" />\n"
            + "      </AggName>\n"
            + "    </Table>\n"
            + "    <DimensionUsage name=\"Store\" source=\"Store\" foreignKey=\"store_id\"/>\n"
            + "    <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>\n"
            + "    <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "    <Measure name=\"Store Cost\" column=\"store_cost\" aggregator=\"sum\"\n"
            + "      formatString=\"#,###.00\"/>\n"
            + "    <Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"#,###.00\"/>\n"
            + "  </Cube>\n"
            + "  <Cube name=\"Sales2\" defaultMeasure=\"Unit Sales\">\n"
            + "    <Table name=\"sales_fact_1997\">\n"
            + "      <AggName name=\"agg_c_special_sales_fact_1997\">\n"
            + "        <AggFactCount column=\"FACT_COUNT\"/>\n"
            + "        <AggIgnoreColumn column=\"foo\"/>\n"
            + "        <AggIgnoreColumn column=\"bar\"/>\n"
            + "        <AggIgnoreColumn column=\"PRODUCT_ID\" />\n"
            + "        <AggIgnoreColumn column=\"CUSTOMER_ID\" />\n"
            + "        <AggIgnoreColumn column=\"PROMOTION_ID\" />\n"
            + "        <AggForeignKey factColumn=\"store_id\" aggColumn=\"STORE_ID\" />\n"
            + "        <AggMeasure name=\"[Measures].[Unit Sales]\" column=\"UNIT_SALES_SUM\" />\n"
            + "        <AggMeasure name=\"[Measures].[Store Cost]\" column=\"STORE_COST_SUM\" />\n"
            + "        <AggMeasure name=\"[Measures].[Store Sales]\" column=\"STORE_SALES_SUM\" />\n"
            + "        <AggLevel name=\"[Time].[Year]\" column=\"TIME_YEAR\" />\n"
            + "        <AggLevel name=\"[Time].[Quarter]\" column=\"TIME_QUARTER\" />\n"
            + "        <AggLevel name=\"[Time].[Month]\" column=\"TIME_MONTH\" />\n"
            + "      </AggName>\n"
            + "    </Table>\n"
            + "    <DimensionUsage name=\"Store\" source=\"Store\" foreignKey=\"store_id\"/>\n"
            + "    <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>\n"
            + "    <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "    <Measure name=\"Store Cost\" column=\"store_cost\" aggregator=\"sum\"\n"
            + "      formatString=\"#,###.00\"/>\n"
            + "    <Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"#,###.00\"/>\n"
            + "  </Cube>\n"
            + "  <VirtualCube name=\"SuperSales\" defaultMeasure=\"Unit Sales\">\n"
            + "    <VirtualCubeDimension cubeName=\"Sales1\" name=\"Store\"/>\n"
            + " <VirtualCubeDimension cubeName=\"Sales1\" name=\"Time\"/>\n"
            + "    <VirtualCubeMeasure cubeName=\"Sales2\" name=\"[Measures].[Unit Sales]\"/>\n"
            + " <VirtualCubeMeasure cubeName=\"Sales2\" name=\"[Measures].[Store Cost]\"/>\n"
            + " <VirtualCubeMeasure cubeName=\"Sales2\" name=\"[Measures].[Store Sales]\"/>\n"
            + "  </VirtualCube>\n"
            + "</Schema>\n";
         */
        final String mdx =
            "select {NonEmptyCrossJoin([Time].[Month].Members, [Store].[Store Country].Members)} on rows,"
            + "{[Measures].[Unit Sales]} on columns "
            + "from [SuperSales]";


        if (context.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class)) {
            final String sqlMysql =
                "select `agg_c_14_sales_fact_1997`.`the_year` as `c0`, `agg_c_14_sales_fact_1997`.`quarter` as `c1`, `agg_c_14_sales_fact_1997`.`month_of_year` as `c2`, `store`.`store_country` as `c3` "
                + "from `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997` "
                + "join `store` as `store` on `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id` "
                + "group by `agg_c_14_sales_fact_1997`.`the_year`, `agg_c_14_sales_fact_1997`.`quarter`, `agg_c_14_sales_fact_1997`.`month_of_year`, `store`.`store_country` "
                + "order by ISNULL(`c0`) ASC, `c0` ASC, ISNULL(`c1`) ASC, `c1` ASC, ISNULL(`c2`) ASC, `c2` ASC, ISNULL(`c3`) ASC, `c3` ASC";

            SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
                mdx).expectSql(new SqlPattern[] {
                    new SqlPattern(
                        DatabaseProduct.MYSQL,
                        sqlMysql,
                        sqlMysql.length())
                }).verify();
        }
        assertThatQuery(context.getConnectionWithDefaultRole(),
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Time].[Time].[1997].[Q1].[1], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q1].[2], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q1].[3], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q2].[4], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q2].[5], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q2].[6], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q3].[7], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q3].[8], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q3].[9], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q4].[10], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q4].[11], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1997].[Q4].[12], [Store].[Store].[USA]}\n"
            + "Row #0: 21,628\n"
            + "Row #1: 20,957\n"
            + "Row #2: 23,706\n"
            + "Row #3: 20,179\n"
            + "Row #4: 21,081\n"
            + "Row #5: 21,350\n"
            + "Row #6: 23,763\n"
            + "Row #7: 21,697\n"
            + "Row #8: 20,388\n"
            + "Row #9: 19,958\n"
            + "Row #10: 25,270\n"
            + "Row #11: 26,796\n");
    }

    /**
     * This is a test for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1271">MONDRIAN-1271</a>
     *
     * When a non-collapsed AggLevel was used, Mondrian would join on the
     * key column of the lowest level instead of the one it should have.
     */
    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier6.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMondrian1271(Context<?> context) {
        prepareContext(context);
        if (!context.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class)) {
            return;
        }
        /*
        final String schema =
            "<?xml version=\"1.0\"?>\n"
            + "<Schema name=\"custom\">\n"
            + "  <Dimension name=\"Store\">\n"
            + "    <Hierarchy hasAll=\"true\" primaryKey=\"store_id\">\n"
            + "      <Table name=\"store\"/>\n"
            + "      <Level name=\"Store Country\" column=\"store_country\" uniqueMembers=\"true\"/>\n"
            + "      <Level name=\"Store State\" column=\"store_state\" uniqueMembers=\"true\"/>\n"
            + "      <Level name=\"Store City\" column=\"store_city\" uniqueMembers=\"false\"/>\n"
            + "      <Level name=\"Store Name\" column=\"store_name\" uniqueMembers=\"true\">\n"
            + "      </Level>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "  <Dimension name=\"Time\" type=\"TimeDimension\">\n"
            + "    <Hierarchy hasAll=\"false\" primaryKey=\"time_id\">\n"
            + "      <Table name=\"time_by_day\"/>\n"
            + "      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"\n"
            + "          levelType=\"TimeYears\"/>\n"
            + "      <Level name=\"Quarter\" column=\"quarter\" uniqueMembers=\"false\"\n"
            + "          levelType=\"TimeQuarters\"/>\n"
            + "      <Level name=\"Month\" column=\"month_of_year\" uniqueMembers=\"true\" type=\"Numeric\"\n"
            + "          levelType=\"TimeMonths\"/>\n"
            + "      <Level name=\"Day\" column=\"day_of_month\" uniqueMembers=\"false\" type=\"Numeric\"\n"
            + "          levelType=\"TimeDays\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "  <Cube name=\"Sales1\" defaultMeasure=\"Unit Sales\">\n"
            + "    <Table name=\"sales_fact_1997\">\n"
            + "      <AggExclude name=\"agg_c_special_sales_fact_1997\"/>"
            + "      <AggExclude name=\"agg_c_10_sales_fact_1997\"/>"
            + "      <AggExclude name=\"agg_l_04_sales_fact_1997\"/>"
            + "      <AggExclude name=\"agg_g_ms_pcat_sales_fact_1997\"/>"
            + "      <AggExclude name=\"agg_lc_06_sales_fact_1997\"/>"
            + "      <AggExclude name=\"agg_l_03_sales_fact_1997\"/>"
            + "      <AggExclude name=\"agg_lc_100_sales_fact_1997\"/>"
            + "      <AggExclude name=\"agg_pl_01_sales_fact_1997\"/>"
            + "      <AggExclude name=\"agg_ll_01_sales_fact_1997\"/>"
            + "      <AggExclude name=\"agg_l_05_sales_fact_1997\"/>"
            + "      <AggName name=\"agg_c_14_sales_fact_1997\">\n"
            + "        <AggFactCount column=\"fact_count\"/>\n"
            + "        <AggIgnoreColumn column=\"product_id\" />\n"
            + "        <AggIgnoreColumn column=\"customer_id\" />\n"
            + "        <AggIgnoreColumn column=\"promotion_id\" />\n"
            + "        <AggIgnoreColumn column=\"the_year\" />\n"
            + "        <AggIgnoreColumn column=\"quarter\" />\n"
            + "        <AggForeignKey factColumn=\"store_id\" aggColumn=\"store_id\" />\n"
            + "        <AggMeasure name=\"[Measures].[Unit Sales]\" column=\"unit_sales\" />\n"
            + "        <AggMeasure name=\"[Measures].[Store Cost]\" column=\"store_cost\" />\n"
            + "        <AggMeasure name=\"[Measures].[Store Sales]\" column=\"store_sales\" />\n"
            + "        <AggLevel name=\"[Time].[Month]\" column=\"month_of_year\" collapsed=\"false\" />\n"
            + "      </AggName>\n"
            + "    </Table>\n"
            + "    <DimensionUsage name=\"Store\" source=\"Store\" foreignKey=\"store_id\"/>\n"
            + "    <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>\n"
            + "    <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\"/>\n"
            + "    <Measure name=\"Store Cost\" column=\"store_cost\" aggregator=\"sum\"\n"
            + "      formatString=\"#,###.00\"/>\n"
            + "    <Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"#,###.00\"/>\n"
            + "  </Cube>\n"
            + "</Schema>\n";
         */

        final String mdx =
            "select {NonEmptyCrossJoin([Time].[Time].[Year].Members, [Store].[Store].[Store Country].Members)} on rows,"
            + "{[Measures].[Unit Sales]} on columns "
            + "from [Sales1]";
        final String mdxTooLowForAgg =
            "select {NonEmptyCrossJoin([Time].[Time].[Day].Members, [Store].[Store].[Store Country].Members)} on rows,"
            + "{[Measures].[Unit Sales]} on columns "
            + "from [Sales1]";

        final String sqlMysqlTupleQuery =
            "select\n"
            + "    `time_by_day`.`the_year` as `c0`,\n"
            + "    `store`.`store_country` as `c1`\n"
            + "from\n"
            + "    `time_by_day` as `time_by_day`\n"
            + "join\n"
            + "    `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997`\n"
            + "on\n"
            + "    `agg_c_14_sales_fact_1997`.`month_of_year` = `time_by_day`.`month_of_year`\n"
            + "join\n"
            + "    `store` as `store`\n"
            + "on\n"
            + "    `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id`\n"
            + "group by\n"
            + "    `time_by_day`.`the_year`,\n"
            + "    `store`.`store_country`\n"
            + "order by\n"
            + (getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
                ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                + "    ISNULL(`c1`) ASC, `c1` ASC"
                : "    ISNULL(`time_by_day`.`the_year`) ASC, `time_by_day`.`the_year` ASC,\n"
                + "    ISNULL(`store`.`store_country`) ASC, `store`.`store_country` ASC");

        final String sqlMysqlSegmentQuery =
            "select\n"
            + "    `store`.`store_country` as `c0`,\n"
            + "    `time_by_day`.`the_year` as `c1`,\n"
            + "    sum(`agg_c_14_sales_fact_1997`.`unit_sales`) as `m0`\n"
            + "from\n"
            + "    `store` as `store`\n"
            + "join\n"
            + "    `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997`\n"
            + "on\n"
            + "    `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id`\n"
            + "join\n"
            + "    `time_by_day` as `time_by_day`\n"
            + "on\n"
            + "    `agg_c_14_sales_fact_1997`.`month_of_year` = `time_by_day`.`month_of_year`\n"
            + "where\n"
            + "    `store`.`store_country` = 'USA'\n"
            + "group by\n"
            + "    `store`.`store_country`,\n"
            + "    `time_by_day`.`the_year`";

        final String sqlMysqlTooLowTupleQuery =
            "select `time_by_day`.`the_year` as `c0`, `time_by_day`.`quarter` as `c1`, `time_by_day`.`month_of_year` as `c2`, `time_by_day`.`day_of_month` as `c3`, `store`.`store_country` as `c4` from `time_by_day` as `time_by_day` join `sales_fact_1997` as `sales_fact_1997` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` join `store` as `store` on `sales_fact_1997`.`store_id` = `store`.`store_id` group by `time_by_day`.`the_year`, `time_by_day`.`quarter`, `time_by_day`.`month_of_year`, `time_by_day`.`day_of_month`, `store`.`store_country` order by ISNULL(`c0`) ASC, `c0` ASC, ISNULL(`c1`) ASC, `c1` ASC, ISNULL(`c2`) ASC, `c2` ASC, ISNULL(`c3`) ASC, `c3` ASC, ISNULL(`c4`) ASC, `c4` ASC";

        final String sqlMysqlTooLowSegmentQuery =
            "select `store`.`store_country` as `c0`, `time_by_day`.`month_of_year` as `c1`, `time_by_day`.`day_of_month` as `c2`, sum(`sales_fact_1997`.`unit_sales`) as `m0` from `sales_fact_1997` as `sales_fact_1997` join `store` as `store` on `sales_fact_1997`.`store_id` = `store`.`store_id` join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` where `store`.`store_country` = 'USA' group by `store`.`store_country`, `time_by_day`.`month_of_year`, `time_by_day`.`day_of_month`";


        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            mdx).expectSql(new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysqlTupleQuery,
                    sqlMysqlTupleQuery.length())
            }).verify();

        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            mdx).expectSql(new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysqlSegmentQuery,
                    sqlMysqlSegmentQuery.length())
            }).verify();

        // Because we have caused a many-to-many relation between the agg table
        // and the dim table, we expect retarded numbers here.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Time].[Time].[1997], [Store].[Store].[USA]}\n"
            + "{[Time].[Time].[1998], [Store].[Store].[USA]}\n"
            + "Row #0: 8,119,905\n"
            + "Row #1: 8,119,905\n");

        // Make sure that queries on lower levels don't trigger a
        // false positive with the agg matcher.
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            mdxTooLowForAgg).expectSql(new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysqlTooLowTupleQuery,
                    sqlMysqlTooLowTupleQuery.length())
            }).verify();

        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            mdxTooLowForAgg).expectSql(new SqlPattern[] {
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysqlTooLowSegmentQuery,
                    sqlMysqlTooLowSegmentQuery.length())
            }).verify();
        context.getCatalogCache().clear();
    }

    @Test
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier7.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testAggStarWithIgnoredColumnsRequiresRollup(Context<?> context) {
        prepareContext(context);
        boolean chooseAggregateByVolume = context.getConfigValue(ConfigConstants.CHOOSE_AGGREGATE_BY_VOLUME, ConfigConstants.CHOOSE_AGGREGATE_BY_VOLUME_DEFAULT_VALUE ,Boolean.class);
        /*
        withSchema(context,
                "<Schema name=\"FoodMart\">"
                + "  <Dimension name=\"Time\" type=\"TimeDimension\">\n"
                + "    <Hierarchy hasAll=\"false\" primaryKey=\"time_id\">\n"
                + "      <Table name=\"time_by_day\"/>\n"
                + "      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"\n"
                + "          levelType=\"TimeYears\"/>\n"
                + "      <Level name=\"Quarter\" column=\"quarter\" uniqueMembers=\"false\"\n"
                + "          levelType=\"TimeQuarters\"/>\n"
                + "    </Hierarchy>\n"
                + "  </Dimension>\n"
                + "<Cube name=\"Sales\" defaultMeasure=\"Unit Sales\">\n"
                + "  <Table name=\"sales_fact_1997\">\n"
                + "    <AggExclude name=\"agg_c_special_sales_fact_1997\" />\n"
                + "    <AggExclude name=\"agg_lc_100_sales_fact_1997\" />\n"
                + "    <AggExclude name=\"agg_lc_10_sales_fact_1997\" />\n"
                + "    <AggExclude name=\"agg_pc_10_sales_fact_1997\" />\n"
                + "    <AggName name=\"agg_c_10_sales_fact_1997\">\n"
                + "        <AggFactCount column=\"FACT_COUNT\"/>\n"
                + "        <AggIgnoreColumn column=\"Quarter\"/>\n"
                + "        <AggIgnoreColumn column=\"MONTH_OF_YEAR\"/>\n"
                + "        <AggMeasure name=\"[Measures].[Unit Sales]\" column=\"unit_sales\" />\n"
                + "        <AggLevel name=\"[Time].[Year]\" column=\"the_year\" />\n"
                + "    </AggName>\n"
                + "  </Table>\n"
                + "  <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>\n"
                + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
                + "      formatString=\"Standard\"/>\n"
                + "</Cube>\n"
                + "</Schema>");
         */
        Connection connection = context.getConnectionWithDefaultRole();

		RolapCatalog rolapCatalog = (RolapCatalog) connection.getCatalogReader().getCatalog();
        RolapStar star = rolapCatalog.getRolapStarRegistry().getStar("sales_fact_1997");
        AggStar aggStar1 = getAggStar(star, "agg_c_10_sales_fact_1997");
        AggStar aggStarSpy = spy(
            getAggStar(star, "agg_c_10_sales_fact_1997"));
        // make sure the test AggStar will be prioritized first
        when(aggStarSpy.getSize(chooseAggregateByVolume)).thenReturn(0l);

		RolapCatalog rolapCatalog2 = (RolapCatalog) connection.getCatalogReader().getCatalog();
		rolapCatalog2.getRolapStarRegistry().getStar("sales_fact_1997").addAggStar(aggStarSpy);
        boolean[] rollup = { false };
        AggStar returnedStar = AggregationManager
            .findAgg(
                star, aggStarSpy.getLevelBitKey(),
                aggStarSpy.getMeasureBitKey(), rollup);
        assertTrue(rollup[0],
                "Rollup should be true since AggStar has ignored columns ");
        assertEquals(aggStar1.toString(), returnedStar.toString());
        assertTrue(aggStarSpy.hasIgnoredColumns(),
                "Columns marked with AggIgnoreColumn, so AggStar "
                        + ".hasIgnoredColumns() should be true");
        String sqlMysql =
            "select\n"
            + "    `agg_c_10_sales_fact_1997`.`the_year` as `c0`,\n"
            + "    sum(`agg_c_10_sales_fact_1997`.`unit_sales`) as `m0`\n"
            + "from\n"
            + "    `agg_c_10_sales_fact_1997` as `agg_c_10_sales_fact_1997`\n"
            + "where\n"
            + "    `agg_c_10_sales_fact_1997`.`the_year` = 1997\n"
            + "group by\n"
            + "    `agg_c_10_sales_fact_1997`.`the_year`";
        String sqlOra =
            "select\n"
            + "    \"agg_c_10_sales_fact_1997\".\"the_year\" as \"c0\",\n"
            + "    sum(\"agg_c_10_sales_fact_1997\".\"unit_sales\") as \"m0\"\n"
            + "from\n"
            + "    \"agg_c_10_sales_fact_1997\" \"agg_c_10_sales_fact_1997\"\n"
            + "where\n"
            + "    \"agg_c_10_sales_fact_1997\".\"the_year\" = 1997\n"
            + "group by\n"
            + "    \"agg_c_10_sales_fact_1997\".\"the_year\"";
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            "select Time.Time.[1997] on 0 from sales").expectSql(new SqlPattern[]{
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysql,
                    sqlMysql.length()),
                new SqlPattern(
                    DatabaseProduct.ORACLE,
                    sqlOra,
                    sqlOra.length())}).verify();
        context.getCatalogCache().clear();
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier3.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testAggStarWithUnusedColumnsRequiresRollup(Context<?> context) {
        prepareContext(context);
        /*
        withSchema(context,
                "<Schema name=\"FoodMart\">"
                + "<Cube name=\"Sales\" defaultMeasure=\"Unit Sales\">\n"
                + "  <Table name=\"sales_fact_1997\" />\n"
                + "  <Dimension name=\"Gender\" foreignKey=\"customer_id\">\n"
                + "    <Hierarchy hasAll=\"true\" allMemberName=\"All Gender\" primaryKey=\"customer_id\">\n"
                + "      <Table name=\"customer\"/>\n"
                + "      <Level name=\"Gender\" column=\"gender\" uniqueMembers=\"true\"/>\n"
                + "    </Hierarchy>\n"
                + "  </Dimension>\n"
                + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
                + "      formatString=\"Standard\"/>\n"
                + "</Cube>\n"
                + "</Schema>");
        */

        // getConnectionWithDefaultRole() builds a NEW connection on every call, so hold one:
        // asking twice can hand back two catalogs and two stars, and the spy would then sit in
        // one star while findAgg searches the other. The spy lands in the SHARED, cached star,
        // so flush the schema cache afterwards -- otherwise the getSize()=0 spy survives into the
        // next test (testLevelKeyAsSqlExpWithAgg reads the same star and picks the wrong aggregate).
        Connection connection = context.getConnectionWithDefaultRole();
        try {
            RolapCatalog rolapCatalog = (RolapCatalog) connection.getCatalogReader().getCatalog();
            RolapStar star = rolapCatalog.getRolapStarRegistry().getStar("sales_fact_1997");
            AggStar aggStarSpy = spy(
                getAggStar(star, "agg_c_special_sales_fact_1997"));
            // make sure the test AggStar will be prioritized first
            when(aggStarSpy.getSize(context.getConfigValue(ConfigConstants.CHOOSE_AGGREGATE_BY_VOLUME, ConfigConstants.CHOOSE_AGGREGATE_BY_VOLUME_DEFAULT_VALUE ,Boolean.class))).thenReturn(0l);

            star.addAggStar(aggStarSpy);

            boolean[] rollup = { false };
            AggStar returnedStar = AggregationManager
                .findAgg(
                    star, aggStarSpy.getLevelBitKey(),
                    aggStarSpy.getMeasureBitKey(), rollup);
            assertTrue(rollup[0],
                    "Rollup should be true since AggStar has ignored columns ");
            assertEquals(aggStarSpy, returnedStar);
            assertTrue(aggStarSpy.hasIgnoredColumns(),
                    "Unused columns are present, should be marked as "
                            + "having ignored columns.");

            String sqlOra =
                "select\n"
                + "    \"customer\".\"gender\" as \"c0\",\n"
                + "    sum(\"agg_c_special_sales_fact_1997\".\"unit_sales_sum\") as \"m0\"\n"
                + "from\n"
                + "    \"customer\" \"customer\",\n"
                + "    \"agg_c_special_sales_fact_1997\" \"agg_c_special_sales_fact_1997\"\n"
                + "where\n"
                + "    \"agg_c_special_sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n"
                + "group by\n"
                + "    \"customer\".\"gender\"";
            String sqlMysql =
                "select `customer`.`gender` as `c0`, sum(`agg_c_special_sales_fact_1997`.`unit_sales_sum`) as `m0`";
            SqlAssert.forQuery(connection,
                "select gender.gender.members on 0 from sales").expectSql(new SqlPattern[]{
                    new SqlPattern(
                        DatabaseProduct.MYSQL,
                        sqlMysql,
                        sqlMysql.length()),
                    new SqlPattern(
                        DatabaseProduct.ORACLE,
                        sqlOra,
                        sqlOra.length())}).verify();
        } finally {
            flushSchemaCache(connection);
        }
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.TestAggregationManagerModifier4.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testAggStarWithIgnoredColumnsAndCountDistinct(Context<?> context) {
        prepareContext(context);
        /*
        withSchema(context,
                "<Schema name=\"FoodMart\">"
                + "  <Dimension name=\"Time\" type=\"TimeDimension\">\n"
                + "    <Hierarchy hasAll=\"false\" primaryKey=\"time_id\">\n"
                + "      <Table name=\"time_by_day\"/>\n"
                + "      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"\n"
                + "          levelType=\"TimeYears\"/>\n"
                + "      <Level name=\"Quarter\" column=\"quarter\" uniqueMembers=\"false\"\n"
                + "          levelType=\"TimeQuarters\"/>\n"
                + "    </Hierarchy>\n"
                + "  </Dimension>\n"
                + "<Cube name=\"Sales\" defaultMeasure=\"Unit Sales\">\n"
                + "  <Table name=\"sales_fact_1997\">\n"
                + "    <AggExclude name=\"agg_c_special_sales_fact_1997\" />\n"
                + "    <AggExclude name=\"agg_lc_100_sales_fact_1997\" />\n"
                + "    <AggExclude name=\"agg_lc_10_sales_fact_1997\" />\n"
                + "    <AggExclude name=\"agg_pc_10_sales_fact_1997\" />\n"
                + "    <AggName name=\"agg_g_ms_pcat_sales_fact_1997\">\n"
                + "        <AggFactCount column=\"FACT_COUNT\"/>\n"
                + "        <AggIgnoreColumn column=\"Quarter\"/>\n"
                + "        <AggIgnoreColumn column=\"MONTH_OF_YEAR\"/>\n"
                + "        <AggMeasure name=\"[Measures].[Customer Count]\" column=\"customer_count\" />\n"
                + "        <AggLevel name=\"[Time].[Year]\" column=\"the_year\" />\n"
                + "    </AggName>\n"
                + "  </Table>\n"
                + "  <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>\n"
                + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
                + "      formatString=\"Standard\"/>\n"
                + "  <Measure name=\"Customer Count\" column=\"customer_id\" aggregator=\"distinct-count\"\n"
                + "      formatString=\"Standard\"/>\n"
                + "</Cube>\n"
                + "</Schema>");
         */

        RolapCatalog rolapCatalog = (RolapCatalog) context.getConnectionWithDefaultRole().getCatalogReader()
                .getCatalog();
        RolapStar star = rolapCatalog.getRolapStarRegistry().getStar("sales_fact_1997");
        AggStar aggStarSpy = spy(
            getAggStar(star, "agg_g_ms_pcat_sales_fact_1997"));
        // make sure the test AggStar will be prioritized first
        when(aggStarSpy.getSize(context.getConfigValue(ConfigConstants.CHOOSE_AGGREGATE_BY_VOLUME, ConfigConstants.CHOOSE_AGGREGATE_BY_VOLUME_DEFAULT_VALUE ,Boolean.class))).thenReturn(0l);

        RolapCatalog rolapCatalog2 = (RolapCatalog) context.getConnectionWithDefaultRole().getCatalogReader()
                .getCatalog();

        rolapCatalog2.getRolapStarRegistry().getStar("sales_fact_1997").addAggStar(aggStarSpy);
        boolean[] rollup = { false };
        AggStar returnedStar = AggregationManager
            .findAgg(
                star, aggStarSpy.getLevelBitKey(),
                aggStarSpy.getMeasureBitKey(), rollup);
        assertNull(returnedStar,
                "Should not find an agg star given that ignored or unused "
                        + "columns are present, and loading distinct count measure");
        String sqlOra =
            "select\n"
            + "    \"time_by_day\".\"the_year\" as \"c0\",\n"
            + "    count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\"\n"
            + "from\n"
            + "    \"sales_fact_1997\" \"sales_fact_1997\",\n"
            + "    \"time_by_day\" \"time_by_day\"\n"
            + "where\n"
            + "    \"time_by_day\".\"the_year\" = 1997\n"
            + "and\n"
            + "    \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n"
            + "group by\n"
            + "    \"time_by_day\".\"the_year\"";
        String sqlMysql =
            "select `time_by_day`.`the_year` as `c0`, count(distinct `sales_fact_1997`.`customer_id`) as `m0` from `sales_fact_1997` as `sales_fact_1997` join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` where `time_by_day`.`the_year` = 1997 group by `time_by_day`.`the_year`";
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            "select Time.[1997] on 0 from sales where "
            + "measures.[Customer Count]").expectSql(new SqlPattern[]{
                new SqlPattern(
                    DatabaseProduct.MYSQL,
                    sqlMysql,
                    sqlMysql.length()),
                new SqlPattern(
                    DatabaseProduct.ORACLE,
                    sqlOra,
                    sqlOra.length())}).verify();
        context.getCatalogCache().clear();
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    void testDisabledReadAggregatesIgnoresDefaultRules(Context<?> context)
        throws Exception
    {
    	context.getCatalogCache().clear();
        prepareContext(context);
        String sql =
            "select count(*) as `c0` from `agg_c_10_sales_fact_1997` as `agg_c_10_sales_fact_1997`";
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            "select from sales").bypassSchemaCache().clearCacheFirst().expectNoSql(new SqlPattern[]{
                new SqlPattern(
                    DatabaseProduct.MYSQL, sql, sql.length()) }).verify();
    }

    private AggStar getAggStar(RolapStar star, String aggStarName) {
        for (AggStar aggStar : star.getAggStars()) {
            if (aggStar.getFactTable().getName().equals(aggStarName)) {
                return aggStar;
            }
        }
        return null;
    }

}
