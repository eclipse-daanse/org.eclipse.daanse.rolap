/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 Julian Hyde
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.CellRequestFixture;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;

/**
 * Test support for generating SQL queries with the <code>GROUPING SETS</code>
 * construct, if the DBMS supports it.
 *
 * @author Thiyagu
 * @since 08-Jun-2007
 */
@RolapContextTest(FoodmartTestInstance.class)
class GroupingSetQueryTest extends BatchTestCase{
    private static final String cubeNameSales2 = "Sales 2";
    private static final String measureStoreSales = "[Measures].[Store Sales]";
    private static final String fieldNameMaritalStatus = "marital_status";
    private static final String measureCustomerCount =
        "[Measures].[Customer Count]";

    private static final Set<DatabaseProduct> ORACLE_TERADATA =
    		EnumSet.of(
            DatabaseProduct.ORACLE,
            DatabaseProduct.TERADATA);

    @AfterEach
    public void afterEach() {
    }

    @Test
    void testGroupingSetsWithAggregateOverDefaultMember(Context<?> context) {
        // testcase for MONDRIAN-705
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatQuery(connection,
            "with member [Gender].[Gender].[agg] as ' "
            + "  Aggregate({[Gender].[Gender].DefaultMember}, [Measures].[Store Cost])' "
            + "select "
            + "  {[Measures].[Store Cost]} ON COLUMNS, "
            + "  {[Gender].[Gender].[Gender].Members, [Gender].[Gender].[agg]} ON ROWS "
            + "from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Store Cost]}\n"
            + "Axis #2:\n"
            + "{[Gender].[Gender].[F]}\n"
            + "{[Gender].[Gender].[M]}\n"
            + "{[Gender].[Gender].[agg]}\n"
            + "Row #0: 111,777.48\n"
            + "Row #1: 113,849.75\n"
            + "Row #2: 225,627.23\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "true", type = Boolean.class)
    void testGroupingSetForSingleColumnConstraint(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").build();

        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").build();

        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).build();

        SqlPattern[] patternsWithGsets = {
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"customer\".\"gender\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\", "
                + "grouping(\"customer\".\"gender\") as \"g0\" "
                + "from \"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by grouping sets ((\"customer\".\"gender\"), ())",
                26)
        };

        // If aggregates are enabled, mondrian should use them. Results should
        // be the same with or without grouping sets enabled.
        SqlPattern[] patternsWithAggs = {
            new SqlPattern(
                ORACLE_TERADATA,
                "select sum(\"agg_c_10_sales_fact_1997\".\"unit_sales\") as \"m0\""
                + " from \"agg_c_10_sales_fact_1997\" \"agg_c_10_sales_fact_1997\"",
                null),
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"agg_g_ms_pcat_sales_fact_1997\".\"gender\" as \"c0\","
                + " sum(\"agg_g_ms_pcat_sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"agg_g_ms_pcat_sales_fact_1997\" \"agg_g_ms_pcat_sales_fact_1997\" "
                + "group by \"agg_g_ms_pcat_sales_fact_1997\".\"gender\"",
                null)
        };

        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            assertRequestSql(connection,
                new CellRequest[] {request3, request1, request2},
                patternsWithAggs);
        } else {
            assertRequestSql(connection,
                new CellRequest[] {request3, request1, request2},
                patternsWithGsets);
        }
    }

    @Test
    void testGroupingSetForSingleColumnConstraintGroupingSetsDisabled(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").build();

        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").build();

        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).build();

        SqlPattern[] patternsWithAggs = {
            new SqlPattern(
                ORACLE_TERADATA,
                "select sum(\"agg_c_10_sales_fact_1997\".\"unit_sales\") as \"m0\""
                + " from \"agg_c_10_sales_fact_1997\" \"agg_c_10_sales_fact_1997\"",
                null),
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"agg_g_ms_pcat_sales_fact_1997\".\"gender\" as \"c0\","
                + " sum(\"agg_g_ms_pcat_sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"agg_g_ms_pcat_sales_fact_1997\" \"agg_g_ms_pcat_sales_fact_1997\" "
                + "group by \"agg_g_ms_pcat_sales_fact_1997\".\"gender\"",
                null)
        };

        SqlPattern[] patternsWithoutGsets = {
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select \"customer\".\"gender\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"customer\" as \"customer\", \"sales_fact_1997\" as \"sales_fact_1997\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by \"customer\".\"gender\"",
                26),
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"customer\".\"gender\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by \"customer\".\"gender\"",
                26)
        };

        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            assertRequestSql(connection,
                new CellRequest[] {request3, request1, request2},
                patternsWithAggs);
        } else {
            assertRequestSql(connection,
                new CellRequest[] {request3, request1, request2},
                patternsWithoutGsets);
        }
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "true", type = Boolean.class)
    void testNotUsingGroupingSetWhenGroupUsesDifferentAggregateTable(Context<?> context) {
        if (!(context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
              && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)))
        {
            return;
        }

        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").build();

        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").build();

        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales).measure(measureUnitSales).build();

        SqlPattern[] patternsWithoutGsets = {
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select \"agg_g_ms_pcat_sales_fact_1997\".\"gender\" as \"c0\", "
                + "sum(\"agg_g_ms_pcat_sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"agg_g_ms_pcat_sales_fact_1997\" as \"agg_g_ms_pcat_sales_fact_1997\" "
                + "group by \"agg_g_ms_pcat_sales_fact_1997\".\"gender\"",
                26),
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"agg_g_ms_pcat_sales_fact_1997\".\"gender\" as \"c0\", "
                + "sum(\"agg_g_ms_pcat_sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"agg_g_ms_pcat_sales_fact_1997\" \"agg_g_ms_pcat_sales_fact_1997\" "
                + "group by \"agg_g_ms_pcat_sales_fact_1997\".\"gender\"",
                26)
        };
        assertRequestSql(context.getConnectionWithDefaultRole(),
            new CellRequest[] {request3, request1, request2},
            patternsWithoutGsets);
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "true", type = Boolean.class)
    void testNotUsingGroupingSet(Context<?> context) {
        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").build();

        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").build();

        SqlPattern[] patternsWithGsets = {
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"customer\".\"gender\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by \"customer\".\"gender\"", 72)
            };
        assertRequestSql(connection,
            new CellRequest[] {request1, request2},
            patternsWithGsets);
    }

    @Test
    void testNotUsingGroupingSetGroupingSetsDisabled(Context<?> context) {
        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").build();

        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").build();

        SqlPattern[] patternsWithoutGsets = {
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select \"customer\".\"gender\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"customer\" as \"customer\", \"sales_fact_1997\" as \"sales_fact_1997\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by \"customer\".\"gender\"", 72),
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"customer\".\"gender\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by \"customer\".\"gender\"", 72)
        };
        assertRequestSql(connection,
            new CellRequest[] {request1, request2},
            patternsWithoutGsets);
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "true", type = Boolean.class)
    void testGroupingSetForMultipleMeasureAndSingleConstraint(Context<?> context) {
        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").build();
        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").build();
        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).build();
        CellRequest request4 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureStoreSales).where(tableCustomer, fieldGender, "M").build();
        CellRequest request5 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureStoreSales).where(tableCustomer, fieldGender, "F").build();
        CellRequest request6 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureStoreSales).build();

        SqlPattern[] patternsWithGsets = {
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"customer\".\"gender\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\", "
                + "sum(\"sales_fact_1997\".\"store_sales\") as \"m1\", grouping(\"customer\".\"gender\") as \"g0\" "
                + "from \"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by grouping sets ((\"customer\".\"gender\"), ())",
                26)
        };
        assertRequestSql(connection,
            new CellRequest[] {
                request1, request2, request3, request4, request5, request6},
            patternsWithGsets);
    }

    @Test
    void testGroupingSetForMultipleMeasureAndSingleConstraintGroupingSetsDisabled(Context<?> context) {
        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").build();
        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").build();
        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).build();
        CellRequest request4 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureStoreSales).where(tableCustomer, fieldGender, "M").build();
        CellRequest request5 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureStoreSales).where(tableCustomer, fieldGender, "F").build();
        CellRequest request6 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureStoreSales).build();

        SqlPattern[] patternsWithoutGsets = {
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select \"customer\".\"gender\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\", "
                + "sum(\"sales_fact_1997\".\"store_sales\") as \"m1\" "
                + "from \"customer\" as \"customer\", \"sales_fact_1997\" as \"sales_fact_1997\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by \"customer\".\"gender\"", 26),
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"customer\".\"gender\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\", "
                + "sum(\"sales_fact_1997\".\"store_sales\") as \"m1\" "
                + "from \"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by \"customer\".\"gender\"", 26)
        };
        assertRequestSql(connection,
            new CellRequest[] {
                request1, request2, request3, request4, request5, request6},
            patternsWithoutGsets);
    }

    @Disabled //TODO need investigate
    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "true", type = Boolean.class)
    void testGroupingSetForASummaryCanBeGroupedWith2DetailBatch(Context<?> context) {
        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").build();
        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").build();
        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).build();
        CellRequest request4 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldNameMaritalStatus, "M").build();
        CellRequest request5 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldNameMaritalStatus, "S").build();
        CellRequest request6 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).build();

        SqlPattern[] patternWithGsets = {
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"customer\".\"gender\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\", "
                + "grouping(\"customer\".\"gender\") as \"g0\" "
                + "from \"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by grouping sets ((\"customer\".\"gender\"), ())",
                26),

            new SqlPattern(
                ORACLE_TERADATA,
                "select \"customer\".\"marital_status\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\" "
                + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by \"customer\".\"marital_status\"",
                26),
            };

        assertRequestSql(connection,
            new CellRequest[] {
                request1, request2, request3, request4, request5, request6},
            patternWithGsets);
    }

    @Disabled //TODO need investigate
    @Test
    void testGroupingSetForASummaryCanBeGroupedWith2DetailBatchGroupingSetsDisabled(Context<?> context) {
        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").build();
        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").build();
        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).build();
        CellRequest request4 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldNameMaritalStatus, "M").build();
        CellRequest request5 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldNameMaritalStatus, "S").build();
        CellRequest request6 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).build();

        SqlPattern[] patternWithoutGsets = {
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"sales_fact_1997\" as \"sales_fact_1997\"",
                40),
            new SqlPattern(
                ORACLE_TERADATA,
                "select sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"sales_fact_1997\" =as= \"sales_fact_1997\"",
                40)
        };

        assertRequestSql(connection,
            new CellRequest[] {
                request1, request2, request3, request4, request5, request6},
            patternWithoutGsets);
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "true", type = Boolean.class)
    void testGroupingSetForMultipleColumnConstraint(Context<?> context) {
        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").where(tableTime, fieldYear, "1997").build();

        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").where(tableTime, fieldYear, "1997").build();

        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableTime, fieldYear, "1997").build();

        SqlPattern[] patternsWithGsets = {
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"time_by_day\".\"the_year\" as \"c0\", \"customer\".\"gender\" as \"c1\", "
                + "sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\", grouping(\"customer\".\"gender\") as \"g0\" "
                + "from \"time_by_day\" =as= \"time_by_day\", \"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\" "
                + "where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and \"time_by_day\".\"the_year\" = 1997 "
                + "and \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by grouping sets ((\"time_by_day\".\"the_year\", \"customer\".\"gender\"), (\"time_by_day\".\"the_year\"))",
            150)
        };

        // Sometimes this query causes Oracle 10.2 XE to give
        //   ORA-12516, TNS:listener could not find available handler with
        //   matching protocol stack
        //
        // You need to configure Oracle:
        //  $ su - oracle
        //  $ sqlplus / as sysdba
        //  SQL> ALTER SYSTEM SET sessions=320 SCOPE=SPFILE;
        //  SQL> SHUTDOWN
        assertRequestSql(connection,
            new CellRequest[] {request3, request1, request2},
            patternsWithGsets);
    }

    @Test
    void testGroupingSetForMultipleColumnConstraintGroupingSetsDisabled(Context<?> context) {
        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            return;
        }
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "M").where(tableTime, fieldYear, "1997").build();

        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableCustomer, fieldGender, "F").where(tableTime, fieldYear, "1997").build();

        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureUnitSales).where(tableTime, fieldYear, "1997").build();

        SqlPattern[] patternsWithoutGsets = {
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select \"time_by_day\".\"the_year\" as \"c0\", \"customer\".\"gender\" as \"c1\", "
                + "sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"time_by_day\" as \"time_by_day\", \"sales_fact_1997\" as \"sales_fact_1997\", "
                + "\"customer\" as \"customer\" "
                + "where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and "
                + "\"time_by_day\".\"the_year\" = 1997 and "
                + "\"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by \"time_by_day\".\"the_year\", \"customer\".\"gender\"",
                50),
            new SqlPattern(
                ORACLE_TERADATA,
                "select \"time_by_day\".\"the_year\" as \"c0\", \"customer\".\"gender\" as \"c1\", "
                + "sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" "
                + "from \"time_by_day\" =as= \"time_by_day\", \"sales_fact_1997\" =as= \"sales_fact_1997\", "
                + "\"customer\" =as= \"customer\" "
                + "where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and "
                + "\"time_by_day\".\"the_year\" = 1997 "
                + "and \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
                + "group by \"time_by_day\".\"the_year\", \"customer\".\"gender\"",
                    50)
            };
        assertRequestSql(connection,
            new CellRequest[]{request3, request1, request2},
            patternsWithoutGsets);
    }

    @Disabled //TODO need investigate
    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_GROUPING_SETS, value = "true", type = Boolean.class)
    void testGroupingSetForMultipleColumnConstraintAndCompoundConstraint(Context<?> context)
    {
        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            return;
        }
        List<String[]> compoundMembers = new ArrayList<>();
        compoundMembers.add(new String[] {"USA", "OR"});
        compoundMembers.add(new String[] {"CANADA", "BC"});
        CellRequestFixture.Constraint constraint =
            CellRequestFixture.Constraint.countryState(compoundMembers.toArray(new String[0][]));
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureCustomerCount).where(tableCustomer, fieldGender, "M").where(tableTime, fieldYear, "1997").constrain(constraint).build();

        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureCustomerCount).where(tableCustomer, fieldGender, "F").where(tableTime, fieldYear, "1997").constrain(constraint).build();

        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureCustomerCount).where(tableTime, fieldYear, "1997").constrain(constraint).build();

        String sqlWithoutGS =
            "select \"time_by_day\".\"the_year\" as \"c0\", \"customer\".\"gender\" as \"c1\", "
            + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" from \"time_by_day\" =as= \"time_by_day\", "
            + "\"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\", \"store\" =as= \"store\" "
            + "where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and \"time_by_day\".\"the_year\" = 1997 "
            + "and \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" and "
            + "\"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\" and "
            + "((\"store\".\"store_country\" = 'USA' and \"store\".\"store_state\" = 'OR') or "
            + "(\"store\".\"store_country\" = 'CANADA' and \"store\".\"store_state\" = 'BC')) "
            + "group by \"time_by_day\".\"the_year\", \"customer\".\"gender\"";

        // as of change 12310 GS has been removed from distinct count queries,
        // since there is little or no performance benefit and there is a bug
        // related to it (2207515)
        SqlPattern[] patternsGSEnabled = {
            new SqlPattern(ORACLE_TERADATA, sqlWithoutGS, sqlWithoutGS)
        };

        assertRequestSql(connection,
            new CellRequest[] {request3, request1, request2},
            patternsGSEnabled);
    }

    @Disabled //TODO need investigate
    @Test
    void testGroupingSetForMultipleColumnConstraintAndCompoundConstraintGroupingSetsDisabled(Context<?> context)
    {
        if (context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)) {
            return;
        }
        List<String[]> compoundMembers = new ArrayList<>();
        compoundMembers.add(new String[] {"USA", "OR"});
        compoundMembers.add(new String[] {"CANADA", "BC"});
        CellRequestFixture.Constraint constraint =
            CellRequestFixture.Constraint.countryState(compoundMembers.toArray(new String[0][]));
        Connection connection = context.getConnectionWithDefaultRole();
        CellRequest request1 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureCustomerCount).where(tableCustomer, fieldGender, "M").where(tableTime, fieldYear, "1997").constrain(constraint).build();

        CellRequest request2 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureCustomerCount).where(tableCustomer, fieldGender, "F").where(tableTime, fieldYear, "1997").constrain(constraint).build();

        CellRequest request3 = CellRequestFixture.of(connection).request().cube(cubeNameSales2).measure(measureCustomerCount).where(tableTime, fieldYear, "1997").constrain(constraint).build();

        String sqlWithoutGS =
            "select \"time_by_day\".\"the_year\" as \"c0\", \"customer\".\"gender\" as \"c1\", "
            + "count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\" from \"time_by_day\" =as= \"time_by_day\", "
            + "\"sales_fact_1997\" =as= \"sales_fact_1997\", \"customer\" =as= \"customer\", \"store\" =as= \"store\" "
            + "where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and \"time_by_day\".\"the_year\" = 1997 "
            + "and \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" and "
            + "\"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\" and "
            + "((\"store\".\"store_country\" = 'USA' and \"store\".\"store_state\" = 'OR') or "
            + "(\"store\".\"store_country\" = 'CANADA' and \"store\".\"store_state\" = 'BC')) "
            + "group by \"time_by_day\".\"the_year\", \"customer\".\"gender\"";

        SqlPattern[] patternsGSDisabled = {
            new SqlPattern(ORACLE_TERADATA, sqlWithoutGS, sqlWithoutGS)
        };

        assertRequestSql(connection,
            new CellRequest[]{request3, request1, request2},
            patternsGSDisabled);
    }

    /**
     * Testcase for bug 2004202, "Except not working with grouping sets".
     */
    @Test
    void testBug2004202(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member store.allbutwallawalla as\n"
            + " 'aggregate(\n"
            + "    except(\n"
            + "        store.[store name].members,\n"
            + "        { [Store].[All Stores].[USA].[WA].[Walla Walla].[Store 22]}))'\n"
            + "select {\n"
            + "          store.[store name].members,\n"
            + "         store.allbutwallawalla,\n"
            + "         store.[all stores]} on 0,\n"
            + "  {measures.[customer count]} on 1\n"
            + "from sales").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store].[Store].[Canada].[BC].[Vancouver].[Store 19]}\n"
            + "{[Store].[Store].[Canada].[BC].[Victoria].[Store 20]}\n"
            + "{[Store].[Store].[Mexico].[DF].[Mexico City].[Store 9]}\n"
            + "{[Store].[Store].[Mexico].[DF].[San Andres].[Store 21]}\n"
            + "{[Store].[Store].[Mexico].[Guerrero].[Acapulco].[Store 1]}\n"
            + "{[Store].[Store].[Mexico].[Jalisco].[Guadalajara].[Store 5]}\n"
            + "{[Store].[Store].[Mexico].[Veracruz].[Orizaba].[Store 10]}\n"
            + "{[Store].[Store].[Mexico].[Yucatan].[Merida].[Store 8]}\n"
            + "{[Store].[Store].[Mexico].[Zacatecas].[Camacho].[Store 4]}\n"
            + "{[Store].[Store].[Mexico].[Zacatecas].[Hidalgo].[Store 12]}\n"
            + "{[Store].[Store].[Mexico].[Zacatecas].[Hidalgo].[Store 18]}\n"
            + "{[Store].[Store].[USA].[CA].[Alameda].[HQ]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles].[Store 7]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego].[Store 24]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco].[Store 14]}\n"
            + "{[Store].[Store].[USA].[OR].[Portland].[Store 11]}\n"
            + "{[Store].[Store].[USA].[OR].[Salem].[Store 13]}\n"
            + "{[Store].[Store].[USA].[WA].[Bellingham].[Store 2]}\n"
            + "{[Store].[Store].[USA].[WA].[Bremerton].[Store 3]}\n"
            + "{[Store].[Store].[USA].[WA].[Seattle].[Store 15]}\n"
            + "{[Store].[Store].[USA].[WA].[Spokane].[Store 16]}\n"
            + "{[Store].[Store].[USA].[WA].[Tacoma].[Store 17]}\n"
            + "{[Store].[Store].[USA].[WA].[Walla Walla].[Store 22]}\n"
            + "{[Store].[Store].[USA].[WA].[Yakima].[Store 23]}\n"
            + "{[Store].[Store].[allbutwallawalla]}\n"
            + "{[Store].[Store].[All Stores]}\n"
            + "Axis #2:\n"
            + "{[Measures].[Customer Count]}\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 1,059\n"
            + "Row #0: 1,147\n"
            + "Row #0: 962\n"
            + "Row #0: 296\n"
            + "Row #0: 563\n"
            + "Row #0: 474\n"
            + "Row #0: 190\n"
            + "Row #0: 179\n"
            + "Row #0: 906\n"
            + "Row #0: 84\n"
            + "Row #0: 278\n"
            + "Row #0: 96\n"
            + "Row #0: 95\n"
            + "Row #0: 5,485\n"
            + "Row #0: 5,581\n");
    }
}
