/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2015-2017 Hitachi Vantara.  All rights reserved.
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

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;
import org.eclipse.daanse.rolap.testkit.assertions.FlushSchemaCacheModifier;
import org.eclipse.daanse.rolap.testkit.assertions.NativeVerify;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;

/**
 * @author Andrey Khayrutdinov
 */
@RolapContextTest(FoodmartTestInstance.class)
class NativeFilterAgainstAggTableTest extends BatchTestCase {


    @BeforeAll
    public static void beforeAll() {
    }

    @AfterEach
    public void afterEach() {
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    void testFilteringOnAggregated_ByCount(Context<?> context) {
        // http://jira.pentaho.com/browse/MONDRIAN-2155
        // Aggregation table can have fact's count value exceeding 1,
        // so that to compute the overall amount of facts it is necessary
        // to sum the values instead of counting them

        // See this query:
        //      select
        //          count(t.fact_count) as cnt,
        //          sum(t.fact_count) as s
        //      from
        //          agg_c_14_sales_fact_1997 as t
        //      join
        //          product as p on
        //              t.product_id = p.product_id and
        //              t.the_year=1997 and t.quarter='Q1'
        //      join
        //          product_class as pp on
        //              pp.product_class_id = p.product_class_id and
        //              pp.product_family = 'Food'
        // It returns:
        //      +-------+-------+
        //      | cnt   | s     |
        //      +-------+-------+
        //      | 15533 | 15539 |
        //      +-------+-------+

        String query = ""
            + "SELECT "
            + "   {FILTER("
            + "      {[Product].[All Products].Children},"
            + "      [Measures].[Sales Count] < 15535"
            + "   )} ON COLUMNS,"
            + "   {[Measures].[Sales Count]} on ROWS "
            + "FROM [Sales] "
            + "WHERE [Time].[1997].[Q1]";

        String expectedResult = ""
            + "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1]}\n"
            + "Axis #1:\n"
            + "{[Product].[Product].[Drink]}\n"
            + "{[Product].[Product].[Non-Consumable]}\n"
            + "Axis #2:\n"
            + "{[Measures].[Sales Count]}\n"
            + "Row #0: 1,959\n"
            + "Row #0: 4,090\n";

        doTestFilteringOnAggregatedBy(context, "COUNT", query, expectedResult);
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testFilteringOnAggregated_BySum(Context<?> context) {
        String query = ""
            + "SELECT "
            + "   {FILTER("
            + "      {[Product].[All Products].Children},"
            + "      [Measures].[Store Sales] > 11586"
            + "   )} ON COLUMNS,"
            + "   {[Measures].[Store Sales]} on ROWS "
            + "FROM [Sales] "
            + "WHERE [Time].[1997].[Q1]";

        String expectedResult = ""
            + "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1]}\n"
            + "Axis #1:\n"
            + "{[Product].[Product].[Food]}\n"
            + "{[Product].[Product].[Non-Consumable]}\n"
            + "Axis #2:\n"
            + "{[Measures].[Store Sales]}\n"
            + "Row #0: 101,261.32\n"
            + "Row #0: 26,781.23\n";

        doTestFilteringOnAggregatedBy(context, "SUM", query, expectedResult);
    }

    private void doTestFilteringOnAggregatedBy(
            Context<?> context,
            String aggregator,
            String query,
            String expectedResult)
    {
        assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid(expectedResult);
        verifySameNativeAndNot(context, query, "Aggregated with " + aggregator);
    }

    private void verifySameNativeAndNot(Context<?> context, String query, String testCase) {
        String message = String.format(
            "[%s]: Native and non-native executions of FILTER() differ. "
            + "The query:\n\t\t%s",
            query, testCase);
        NativeVerify.assertSameNativeAndNot(context, query, message);
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    void testAggTableWithNotAllMeasures(Context<?> context) {
        // http://jira.pentaho.com/browse/MONDRIAN-1703
        // If a filter condition contains one or more measures that are
        // not present in the aggregate table, the SQL should omit the
        // having clause altogether.

        String sqlMysqlNoHaving =
            "select\n"
            + "    `agg_c_10_sales_fact_1997`.`the_year` as `c0`,\n"
            + "    `agg_c_10_sales_fact_1997`.`quarter` as `c1`\n"
            + "from\n"
            + "    `agg_c_10_sales_fact_1997` as `agg_c_10_sales_fact_1997`\n"
            + "where\n"
            + "    (`agg_c_10_sales_fact_1997`.`the_year` = 1997)\n"
            + "group by\n"
            + "    `agg_c_10_sales_fact_1997`.`the_year`,\n"
            + "    `agg_c_10_sales_fact_1997`.`quarter`\n"
            + "order by\n"
            + (getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
                ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                + "    ISNULL(`c1`) ASC, `c1` ASC"
                : "    ISNULL(`agg_c_10_sales_fact_1997`.`the_year`) ASC, `agg_c_10_sales_fact_1997`.`the_year` ASC,\n"
                + "    ISNULL(`agg_c_10_sales_fact_1997`.`quarter`) ASC, `agg_c_10_sales_fact_1997`.`quarter` ASC");

        SqlPattern[] patterns = mysqlPattern(sqlMysqlNoHaving);

        // This query should hit the agg_c_10_sales_fact_1997 agg table,
        // which has [unit sales] but not [store count], so should
        // not include the filter condition in the having.
        Connection connection = context.getConnectionWithDefaultRole();
        FlushSchemaCacheModifier.flushSchemaCache(connection);
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            "select filter(Time.[1997].children,  "
            + "measures.[Sales Count] +  measures.[unit sales] > 0) on 0 "
            + "from [sales]").bypassSchemaCache().clearCacheFirst().expectSql(patterns).verify();

        String mySqlWithHaving =
            "select\n"
            + "    `agg_c_10_sales_fact_1997`.`the_year` as `c0`,\n"
            + "    `agg_c_10_sales_fact_1997`.`quarter` as `c1`\n"
            + "from\n"
            + "    `agg_c_10_sales_fact_1997` as `agg_c_10_sales_fact_1997`\n"
            + "where\n"
            + "    (`agg_c_10_sales_fact_1997`.`the_year` = 1997)\n"
            + "group by\n"
            + "    `agg_c_10_sales_fact_1997`.`the_year`,\n"
            + "    `agg_c_10_sales_fact_1997`.`quarter`\n"
            + "having\n"
            + "    ((sum(`agg_c_10_sales_fact_1997`.`store_sales`) + sum(`agg_c_10_sales_fact_1997`.`unit_sales`)) > 0)\n"
            + "order by\n"
            + (getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
                ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                + "    ISNULL(`c1`) ASC, `c1` ASC"
                : "    ISNULL(`agg_c_10_sales_fact_1997`.`the_year`) ASC, `agg_c_10_sales_fact_1997`.`the_year` ASC,\n"
                + "    ISNULL(`agg_c_10_sales_fact_1997`.`quarter`) ASC, `agg_c_10_sales_fact_1997`.`quarter` ASC");

        patterns = mysqlPattern(mySqlWithHaving);

        // both measures are present on the agg table, so this one *should*
        // include having.
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
            "select filter(Time.[1997].children,  "
            + "measures.[Store Sales] +  measures.[unit sales] > 0) on 0 "
            + "from [sales]").bypassSchemaCache().clearCacheFirst().expectSql(patterns).verify();
    }
}
