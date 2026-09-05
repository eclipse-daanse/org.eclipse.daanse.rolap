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

package org.eclipse.daanse.rolap.aggmatcher;

import static org.eclipse.daanse.rolap.testkit.assertions.SqlAssert.mysqlPattern;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Tests {@code AggName}/{@code AggPattern} recognition of {@code AggMeasureFactCount}
 * columns, against a from-scratch "Sales" cube built by
 * {@link AggMeasureFactCountTestModifierEmf} over the {@code fact_csv_2016}
 * fixture, with a minimal self-contained Store dimension (the queries here
 * never reference Store members).
 *
 * <p>Each test method composes its own aggregate-table schema via a
 * method-level {@code @RolapContextTest(value = ...)} -- see the
 * {@link AggMeasureFactCountTestInstances} variants (one {@code CatalogTestInstance}
 * per distinct {@code AggName} configuration, built from the
 * {@link AggMeasureFactCountTestModifiers} subclasses), mirroring what the
 * pre-migration test built inline per method via an anonymous
 * {@code getAggTables()}/{@code getAggExcludes()} override.
 *
 * <p>Tests that used to compare the same query computed twice within one
 * method, toggling {@code USE_AGGREGATES} between the calls, are split into
 * independent {@code WithAggregates}/{@code WithoutAggregates} tests (the
 * testkit has no supported way to mutate a context's config mid-test) --
 * both assert the same expected result, computed once from the CSV fixture
 * (every quarter averages to Store Sales 1.00 / Store Cost 2.00 / Unit Sales
 * 3 by construction of the fixture data).
 */
class AggMeasureFactCountTest {

    private static final String QUERY = ""
            + "select [Time].[Time].[Quarter].Members on columns, \n"
            + "{[Measures].[Store Sales], [Measures].[Store Cost], [Measures].[Unit Sales]} on rows "
            + "from [Sales]";

    /** Every quarter averages to the same Store Sales/Store Cost/Unit Sales values by construction of the fixture. */
    private static final String CLEAN_RESULT = ""
            + "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Time].[Time].[1997].[Q1]}\n"
            + "{[Time].[Time].[1997].[Q2]}\n"
            + "{[Time].[Time].[1997].[Q3]}\n"
            + "{[Time].[Time].[1997].[Q4]}\n"
            + "Axis #2:\n"
            + "{[Measures].[Store Sales]}\n"
            + "{[Measures].[Store Cost]}\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Row #0: 1.00\n"
            + "Row #0: 1.00\n"
            + "Row #0: 1.00\n"
            + "Row #0: 1.00\n"
            + "Row #1: 2.00\n"
            + "Row #1: 2.00\n"
            + "Row #1: 2.00\n"
            + "Row #1: 2.00\n"
            + "Row #2: 3\n"
            + "Row #2: 3\n"
            + "Row #2: 3\n"
            + "Row #2: 3\n";

    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.Default.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testDefaultRecognitionWithAggregates(Connection connection) {
        String sqlMysql = ""
                + "select\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` as `c0`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter` as `c1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`unit_sales`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`unit_sales_fact_count`) as `m0`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_cost`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`store_cost_fact_count`) as `m1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_sales`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`store_sales_fact_count`) as `m2`\n"
                + "from\n"
                + "    `agg_c_6_fact_csv_2016` as `agg_c_6_fact_csv_2016`\n"
                + "where\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` = 1997\n"
                + "group by\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter`";

        SqlAssert.forQuery(connection, QUERY).expectSql(mysqlPattern(sqlMysql)).verify();
        assertThatQuery(connection, QUERY).returnsGrid(CLEAN_RESULT);
    }

    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.Default.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testDefaultRecognitionWithoutAggregates(Connection connection) {
        assertThatQuery(connection, QUERY).returnsGrid(CLEAN_RESULT);
    }

    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.AggName.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testAggNameWithAggregates(Connection connection) {
        String aggSql = ""
                + "select\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` as `c0`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter` as `c1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`unit_sales`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`unit_sales_fact_count`) as `m0`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_cost`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`store_cost_fact_count`) as `m1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_sales`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`store_sales_fact_count`) as `m2`\n"
                + "from\n"
                + "    `agg_c_6_fact_csv_2016` as `agg_c_6_fact_csv_2016`\n"
                + "where\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` = 1997\n"
                + "group by\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter`";

        SqlAssert.forQuery(connection, QUERY).expectSql(mysqlPattern(aggSql)).verify();
        assertThatQuery(connection, QUERY).returnsGrid(CLEAN_RESULT);
    }

    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.AggName.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testAggNameWithoutAggregates(Connection connection) {
        assertThatQuery(connection, QUERY).returnsGrid(CLEAN_RESULT);
    }

    @Disabled // TODO need investigate
    @Test
    @DisabledIfSystemProperty(named = "tempIgnoreStrageTests", matches = "true")
    @RolapContextTest(AggMeasureFactCountTestInstances.FactColumnNotExists.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testFactColumnNotExists(Context<?> context) {
        try {
            assertThatQuery(context.getConnectionWithDefaultRole(), QUERY).returnsGrid("");
            fail("Should throw mondrian exception");
        } catch (OlapRuntimeException e) {
            assertTrue(
                    e.getMessage().startsWith(
                            "Mondrian Error:Internal"
                                    + " error: while parsing catalog"));
        }
    }

    // aggregation tables are used, but with general fact count column
    // test uses aggregation column because right now we use reference to column.
    // previously we used the column as a string, and mondrian used "fact_count"
    // because "unit_SALES" != unit_sales, "StOrE_cosT" != "store_cost",
    // "STORE_SALES" != "store_sales"; right now this is impossible because we
    // use a reference to the column, so the schema is the same as testAggName.
    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.AggName.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testMeasureFactColumnUpperCase(Connection connection) {
        String aggSql = ""
                + "select\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` as `c0`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter` as `c1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`unit_sales` * `agg_c_6_fact_csv_2016`.`unit_sales_fact_count`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`unit_sales_fact_count`) as `m0`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_cost` * `agg_c_6_fact_csv_2016`.`store_cost_fact_count`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`store_cost_fact_count`) as `m1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_sales` * `agg_c_6_fact_csv_2016`.`store_sales_fact_count`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`store_sales_fact_count`) as `m2`\n"
                + "from\n"
                + "    `agg_c_6_fact_csv_2016` as `agg_c_6_fact_csv_2016`\n"
                + "where\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` = 1997\n"
                + "group by\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter`";

        SqlAssert.forQuery(connection, QUERY).expectSql(mysqlPattern(aggSql)).verify();
    }

    // aggregation table is used, but falls back to the general fact_count
    // column since every AggMeasureFactCount.factColumn points at a column
    // that does not exist in the fact table.
    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.MeasureFactColumnNotExist.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testMeasureFactColumnNotExist(Connection connection) {
        String aggSql = ""
                + "select\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` as `c0`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter` as `c1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`unit_sales` * `agg_c_6_fact_csv_2016`.`fact_count`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`fact_count`) as `m0`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_cost` * `agg_c_6_fact_csv_2016`.`fact_count`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`fact_count`) as `m1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_sales` * `agg_c_6_fact_csv_2016`.`fact_count`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`fact_count`) as `m2`\n"
                + "from\n"
                + "    `agg_c_6_fact_csv_2016` as `agg_c_6_fact_csv_2016`\n"
                + "where\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` = 1997\n"
                + "group by\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter`";

        SqlAssert.forQuery(connection, QUERY).expectSql(mysqlPattern(aggSql)).verify();
    }

    // no AggMeasureFactCount elements at all -- falls back to the general
    // fact_count column.
    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.WithoutMeasureFactColumnElement.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testWithoutMeasureFactColumnElement(Connection connection) {
        String aggSql = ""
                + "select\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` as `c0`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter` as `c1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`unit_sales` * `agg_c_6_fact_csv_2016`.`fact_count`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`fact_count`) as `m0`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_cost` * `agg_c_6_fact_csv_2016`.`fact_count`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`fact_count`) as `m1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_sales` * `agg_c_6_fact_csv_2016`.`fact_count`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`fact_count`) as `m2`\n"
                + "from\n"
                + "    `agg_c_6_fact_csv_2016` as `agg_c_6_fact_csv_2016`\n"
                + "where\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` = 1997\n"
                + "group by\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter`";

        SqlAssert.forQuery(connection, QUERY).expectSql(mysqlPattern(aggSql)).verify();
    }

    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.MeasureFactColumnAndAggFactCountNotExist.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testMeasureFactColumnAndAggFactCountNotExist(Context<?> context) {
        try {
            assertThatQuery(context.getConnectionWithDefaultRole(), QUERY).returnsGrid("");
            fail("Should have thrown mondrian exception");
        } catch (OlapRuntimeException e) {
            assertEquals(
                    "Too many errors, '1',"
                            + " while loading/reloading aggregates.",
                    e.getMessage());
        }
    }

    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.AggNameDifferentColumnNames.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testAggNameDifferentColumnNamesWithAggregates(Connection connection) {
        String aggSql = ""
                + "select\n"
                + "    `agg_csv_different_column_names`.`the_year` as `c0`,\n"
                + "    `agg_csv_different_column_names`.`quarter` as `c1`,\n"
                + "    sum(`agg_csv_different_column_names`.`unit_sales` * `agg_csv_different_column_names`.`us_fc`) * 1e0 / sum(`agg_csv_different_column_names`.`us_fc`) as `m0`,\n"
                + "    sum(`agg_csv_different_column_names`.`store_cost` * `agg_csv_different_column_names`.`sc_fc`) * 1e0 / sum(`agg_csv_different_column_names`.`sc_fc`) as `m1`,\n"
                + "    sum(`agg_csv_different_column_names`.`store_sales` * `agg_csv_different_column_names`.`ss_fc`) * 1e0 / sum(`agg_csv_different_column_names`.`ss_fc`) as `m2`\n"
                + "from\n"
                + "    `agg_csv_different_column_names` as `agg_csv_different_column_names`\n"
                + "where\n"
                + "    `agg_csv_different_column_names`.`the_year` = 1997\n"
                + "group by\n"
                + "    `agg_csv_different_column_names`.`the_year`,\n"
                + "    `agg_csv_different_column_names`.`quarter`";

        SqlAssert.forQuery(connection, QUERY).expectSql(mysqlPattern(aggSql)).verify();
        assertThatQuery(connection, QUERY).returnsGrid(CLEAN_RESULT);
    }

    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.AggNameDifferentColumnNames.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testAggNameDifferentColumnNamesWithoutAggregates(Connection connection) {
        assertThatQuery(connection, QUERY).returnsGrid(CLEAN_RESULT);
    }

    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.AggDivideByZero.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testAggDivideByZero(Connection connection) {
        String result = ""
                + "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "{[Time].[Time].[1997].[Q2]}\n"
                + "{[Time].[Time].[1997].[Q3]}\n"
                + "{[Time].[Time].[1997].[Q4]}\n"
                + "Axis #2:\n"
                + "{[Measures].[Store Sales]}\n"
                + "{[Measures].[Store Cost]}\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Row #0: \n"
                + "Row #0: 1.00\n"
                + "Row #0: 1.00\n"
                + "Row #0: 1.00\n"
                + "Row #1: 2.00\n"
                + "Row #1: 2.00\n"
                + "Row #1: 2.00\n"
                + "Row #1: 2.00\n"
                + "Row #2: 3\n"
                + "Row #2: 3\n"
                + "Row #2: 3\n"
                + "Row #2: 3\n";

        assertThatQuery(connection, QUERY).returnsGrid(result);
    }

    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.AggPattern.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testAggPatternWithAggregates(Connection connection) {
        String aggSql = ""
                + "select\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` as `c0`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter` as `c1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`unit_sales`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`unit_sales_fact_count`) as `m0`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_cost`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`store_cost_fact_count`) as `m1`,\n"
                + "    sum(`agg_c_6_fact_csv_2016`.`store_sales`) * 1e0 / sum(`agg_c_6_fact_csv_2016`.`store_sales_fact_count`) as `m2`\n"
                + "from\n"
                + "    `agg_c_6_fact_csv_2016` as `agg_c_6_fact_csv_2016`\n"
                + "where\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year` = 1997\n"
                + "group by\n"
                + "    `agg_c_6_fact_csv_2016`.`the_year`,\n"
                + "    `agg_c_6_fact_csv_2016`.`quarter`";

        SqlAssert.forQuery(connection, QUERY).expectSql(mysqlPattern(aggSql)).verify();
        assertThatQuery(connection, QUERY).returnsGrid(CLEAN_RESULT);
    }

    @Test
    @RolapContextTest(AggMeasureFactCountTestInstances.AggPattern.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testAggPatternWithoutAggregates(Connection connection) {
        assertThatQuery(connection, QUERY).returnsGrid(CLEAN_RESULT);
    }
}
