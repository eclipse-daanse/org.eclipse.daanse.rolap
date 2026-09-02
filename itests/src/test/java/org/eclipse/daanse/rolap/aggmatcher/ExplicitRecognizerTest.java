/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2006-2017 Hitachi Vantara
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
import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;
import org.eclipse.daanse.rolap.testkit.assertions.FlushSchemaCacheModifier;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Each test method builds its own FoodMart-based "ExtraCol" cube with a
 * specific explicit aggregate-table configuration -- see the
 * {@link ExplicitRecognizerTestInstances} variants (one {@code CatalogTestInstance}
 * per scenario, mirroring what the pre-migration test built inline per
 * method via {@code setupMultiColDimCube}).
 *
 * <p>{@code SAME_THREAD}: every scenario constructs its own {@code CatalogSupplier}
 * (FoodMart mapping) instance -- like the pre-migration {@code CsvDBTestCase}
 * base class, this opts out of the module's default concurrent execution so
 * those constructions don't race across this class's own methods.
 */
@Execution(ExecutionMode.SAME_THREAD)
@RolapContextTest(FoodmartTestInstance.class)
class ExplicitRecognizerTest {

    @Test
    @RolapContextTest(ExplicitRecognizerTestInstances.ExplicitAggExtraColsRequiringJoin.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testExplicitAggExtraColsRequiringJoin(Connection connection) {
        String query =
            "select {[Measures].[Unit Sales]} on columns, "
            + "non empty CrossJoin({[TimeExtra].[TimeExtra].[Month].members},{[Gender].[Gender].[M]}) on rows "
            + "from [ExtraCol] ";
        FlushSchemaCacheModifier.flushSchemaCache(connection);
        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`the_year` as `c0`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`quarter` as `c1`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`month_of_year` as `c2`,\n"
                + "    `time_by_day`.`the_month` as `c3`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`gender` as `c4`\n"
                + "from\n"
                + "    `agg_g_ms_pcat_sales_fact_1997` as `agg_g_ms_pcat_sales_fact_1997`\n"
                + "join\n"
                + "    `time_by_day` as `time_by_day`\n"
                + "on\n"
                + "    `time_by_day`.`month_of_year` = `agg_g_ms_pcat_sales_fact_1997`.`month_of_year`\n"
                + "where\n"
                + "    (`agg_g_ms_pcat_sales_fact_1997`.`gender` = 'M')\n"
                + "group by\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`the_year`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`quarter`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`month_of_year`,\n"
                + "    `time_by_day`.`the_month`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`gender`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                    ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                    + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
                    + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
                    + "    ISNULL(`c4`) ASC, `c4` ASC"
                    : "    ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`the_year`) ASC, `agg_g_ms_pcat_sales_fact_1997`.`the_year` ASC,\n"
                    + "    ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`quarter`) ASC, `agg_g_ms_pcat_sales_fact_1997`.`quarter` ASC,\n"
                    + "    ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`month_of_year`) ASC, `agg_g_ms_pcat_sales_fact_1997`.`month_of_year` ASC,\n"
                    + "    ISNULL(`agg_g_ms_pcat_sales_fact_1997`.`gender`) ASC, `agg_g_ms_pcat_sales_fact_1997`.`gender` ASC"))).verify();
        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`the_year` as `c0`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`quarter` as `c1`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`month_of_year` as `c2`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`gender` as `c3`,\n"
                + "    sum(`agg_g_ms_pcat_sales_fact_1997`.`unit_sales`) as `m0`\n"
                + "from\n"
                + "    `agg_g_ms_pcat_sales_fact_1997` as `agg_g_ms_pcat_sales_fact_1997`\n"
                + "where\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`the_year` = 1997\n"
                + "and\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`gender` = 'M'\n"
                + "group by\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`the_year`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`quarter`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`month_of_year`,\n"
                + "    `agg_g_ms_pcat_sales_fact_1997`.`gender`")).verify();
    }

    @Test
    @RolapContextTest(ExplicitRecognizerTestInstances.ExplicitForeignKey.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testExplicitForeignKey(Connection connection) {
        String query =
            "select {[Measures].[Unit Sales]} on columns, "
            + "non empty CrossJoin({[TimeExtra].[TimeExtra].[Month].members},{[Store].[Store].[Store Name].members}) on rows "
            + "from [ExtraCol] ";
        // Run the query twice, verifying both the SqlTupleReader and
        // Segment load queries.
        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `agg_c_14_sales_fact_1997`.`the_year` as `c0`,\n"
                + "    `agg_c_14_sales_fact_1997`.`quarter` as `c1`,\n"
                + "    `agg_c_14_sales_fact_1997`.`month_of_year` as `c2`,\n"
                + "    `time_by_day`.`the_month` as `c3`,\n"
                + "    `store`.`store_country` as `c4`,\n"
                + "    `store`.`store_state` as `c5`,\n"
                + "    `store`.`store_city` as `c6`,\n"
                + "    `store`.`store_name` as `c7`,\n"
                + "    `store`.`store_street_address` as `c8`\n"
                + "from\n"
                + "    `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997`\n"
                + "join\n"
                + "    `time_by_day` as `time_by_day`\n"
                + "on\n"
                + "    `time_by_day`.`month_of_year` = `agg_c_14_sales_fact_1997`.`month_of_year`\n"
                + "join\n"
                + "    `store` as `store`\n"
                + "on\n"
                + "    `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id`\n"
                + "group by\n"
                + "    `agg_c_14_sales_fact_1997`.`the_year`,\n"
                + "    `agg_c_14_sales_fact_1997`.`quarter`,\n"
                + "    `agg_c_14_sales_fact_1997`.`month_of_year`,\n"
                + "    `time_by_day`.`the_month`,\n"
                + "    `store`.`store_country`,\n"
                + "    `store`.`store_state`,\n"
                + "    `store`.`store_city`,\n"
                + "    `store`.`store_name`,\n"
                + "    `store`.`store_street_address`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                    ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                    + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
                    + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
                    + "    ISNULL(`c4`) ASC, `c4` ASC,\n"
                    + "    ISNULL(`c5`) ASC, `c5` ASC,\n"
                    + "    ISNULL(`c6`) ASC, `c6` ASC,\n"
                    + "    ISNULL(`c7`) ASC, `c7` ASC"
                    : "    ISNULL(`agg_c_14_sales_fact_1997`.`the_year`) ASC, `agg_c_14_sales_fact_1997`.`the_year` ASC,\n"
                    + "    ISNULL(`agg_c_14_sales_fact_1997`.`quarter`) ASC, `agg_c_14_sales_fact_1997`.`quarter` ASC,\n"
                    + "    ISNULL(`agg_c_14_sales_fact_1997`.`month_of_year`) ASC, `agg_c_14_sales_fact_1997`.`month_of_year` ASC,\n"
                    + "    ISNULL(`store`.`store_country`) ASC, `store`.`store_country` ASC,\n"
                    + "    ISNULL(`store`.`store_state`) ASC, `store`.`store_state` ASC,\n"
                    + "    ISNULL(`store`.`store_city`) ASC, `store`.`store_city` ASC,\n"
                    + "    ISNULL(`store`.`store_name`) ASC, `store`.`store_name` ASC"))).verify();

        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `agg_c_14_sales_fact_1997`.`the_year` as `c0`,\n"
                + "    `agg_c_14_sales_fact_1997`.`quarter` as `c1`,\n"
                + "    `agg_c_14_sales_fact_1997`.`month_of_year` as `c2`,\n"
                + "    `store`.`store_name` as `c3`,\n"
                + "    sum(`agg_c_14_sales_fact_1997`.`unit_sales`) as `m0`\n"
                + "from\n"
                + "    `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997`\n"
                + "join\n"
                + "    `store` as `store`\n"
                + "on\n"
                + "    `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id`\n"
                + "where\n"
                + "    `agg_c_14_sales_fact_1997`.`the_year` = 1997\n"
                + "group by\n"
                + "    `agg_c_14_sales_fact_1997`.`the_year`,\n"
                + "    `agg_c_14_sales_fact_1997`.`quarter`,\n"
                + "    `agg_c_14_sales_fact_1997`.`month_of_year`,\n"
                + "    `store`.`store_name`")).verify();
    }

    @Test
    @RolapContextTest(ExplicitRecognizerTestInstances.ExplicitAggOrdinalOnAggTable.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testExplicitAggOrdinalOnAggTable(Connection connection) {
        String query =
            "select {[Measures].[Unit Sales]} on columns, "
            + "non empty CrossJoin({[TimeExtra].[TimeExtra].[Month].members},{[Gender].[Gender].[M]}) on rows "
            + "from [ExtraCol] ";

        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `exp_agg_test`.`testyear` as `c0`,\n"
                + "    `exp_agg_test`.`testqtr` as `c1`,\n"
                + "    `exp_agg_test`.`testmonthname` as `c2`,\n"
                + "    `exp_agg_test`.`testmonthord` as `c3`,\n"
                + "    `exp_agg_test`.`gender` as `c4`\n"
                + "from\n"
                + "    `exp_agg_test` as `exp_agg_test`\n"
                + "where\n"
                + "    (`exp_agg_test`.`gender` = 'M')\n"
                + "group by\n"
                + "    `exp_agg_test`.`testyear`,\n"
                + "    `exp_agg_test`.`testqtr`,\n"
                + "    `exp_agg_test`.`testmonthname`,\n"
                + "    `exp_agg_test`.`testmonthord`,\n"
                + "    `exp_agg_test`.`gender`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                    ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                    + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
                    + "    ISNULL(`c3`) ASC, `c3` ASC,\n"
                    + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
                    + "    ISNULL(`c4`) ASC, `c4` ASC"
                    : "    ISNULL(`exp_agg_test`.`testyear`) ASC, `exp_agg_test`.`testyear` ASC,\n"
                    + "    ISNULL(`exp_agg_test`.`testqtr`) ASC, `exp_agg_test`.`testqtr` ASC,\n"
                    + "    ISNULL(`exp_agg_test`.`testmonthord`) ASC, `exp_agg_test`.`testmonthord` ASC,\n"
                    + "    ISNULL(`exp_agg_test`.`testmonthname`) ASC, `exp_agg_test`.`testmonthname` ASC,\n"
                    + "    ISNULL(`exp_agg_test`.`gender`) ASC, `exp_agg_test`.`gender` ASC"))).verify();
    }

    @Test
    @RolapContextTest(ExplicitRecognizerTestInstances.ExplicitAggCaptionOnAggTable.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testExplicitAggCaptionOnAggTable(Connection connection) {
        String query =
            "select {[Measures].[Unit Sales]} on columns, "
            + "non empty CrossJoin({[TimeExtra].[TimeExtra].[Month].members},{[Gender].[Gender].[M]}) on rows "
            + "from [ExtraCol] ";

        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `exp_agg_test`.`testyear` as `c0`,\n"
                + "    `exp_agg_test`.`testqtr` as `c1`,\n"
                + "    `exp_agg_test`.`testmonthname` as `c2`,\n"
                + "    `exp_agg_test`.`testmonthcap` as `c3`,\n"
                + "    `exp_agg_test`.`gender` as `c4`\n"
                + "from\n"
                + "    `exp_agg_test` as `exp_agg_test`\n"
                + "where\n"
                + "    (`exp_agg_test`.`gender` = 'M')\n"
                + "group by\n"
                + "    `exp_agg_test`.`testyear`,\n"
                + "    `exp_agg_test`.`testqtr`,\n"
                + "    `exp_agg_test`.`testmonthname`,\n"
                + "    `exp_agg_test`.`testmonthcap`,\n"
                + "    `exp_agg_test`.`gender`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                    ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                    + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
                    + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
                    + "    ISNULL(`c4`) ASC, `c4` ASC"
                    : "    ISNULL(`exp_agg_test`.`testyear`) ASC, `exp_agg_test`.`testyear` ASC,\n"
                    + "    ISNULL(`exp_agg_test`.`testqtr`) ASC, `exp_agg_test`.`testqtr` ASC,\n"
                    + "    ISNULL(`exp_agg_test`.`testmonthname`) ASC, `exp_agg_test`.`testmonthname` ASC,\n"
                    + "    ISNULL(`exp_agg_test`.`gender`) ASC, `exp_agg_test`.`gender` ASC"))).verify();
    }

    @Disabled //TODO need investigate
    @Test
    @RolapContextTest(ExplicitRecognizerTestInstances.ExplicitAggNameColumnOnAggTable.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testExplicitAggNameColumnOnAggTable(Connection connection) {
        String query =
            "select {[Measures].[Unit Sales]} on columns, "
            + "non empty CrossJoin({[TimeExtra].[Month].members},{[Gender].[M]}) on rows "
            + "from [ExtraCol] ";

        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `exp_agg_test`.`testyear` as `c0`,\n"
                + "    `exp_agg_test`.`testqtr` as `c1`,\n"
                + "    `exp_agg_test`.`testmonthname` as `c2`,\n"
                + "    `exp_agg_test`.`testmonthcap` as `c3`,\n"
                + "    `exp_agg_test`.`testmonprop1` as `c4`,\n"
                + "    `exp_agg_test`.`gender` as `c5`\n"
                + "from\n"
                + "    `exp_agg_test` as `exp_agg_test`\n"
                + "where\n"
                + "    (`exp_agg_test`.`gender` = 'M')\n"
                + "group by\n"
                + "    `exp_agg_test`.`testyear`,\n"
                + "    `exp_agg_test`.`testqtr`,\n"
                + "    `exp_agg_test`.`testmonthname`,\n"
                + "    `exp_agg_test`.`testmonthcap`,\n"
                + "    `exp_agg_test`.`testmonprop1`,\n"
                + "    `exp_agg_test`.`gender`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                    ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                    + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
                    + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
                    + "    ISNULL(`c5`) ASC, `c5` ASC"
                    : "    ISNULL(`exp_agg_test`.`testyear`) ASC, `exp_agg_test`.`testyear` ASC,\n"
                    + "    ISNULL(`exp_agg_test`.`testqtr`) ASC, `exp_agg_test`.`testqtr` ASC,\n"
                    + "    ISNULL(`exp_agg_test`.`testmonthname`) ASC, `exp_agg_test`.`testmonthname` ASC,\n"
                    + "    ISNULL(`exp_agg_test`.`gender`) ASC, `exp_agg_test`.`gender` ASC"))).verify();
    }

    @Test
    @RolapContextTest(ExplicitRecognizerTestInstances.ExplicitAggPropertiesOnAggTable.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testExplicitAggPropertiesOnAggTable(Connection connection) {
        String query =
            "with member measures.propVal as 'Store.Store.CurrentMember.Properties(\"Street Address\")'"
            + "select { measures.[propVal], measures.[Customer Count], [Measures].[Unit Sales]} on columns, "
            + "non empty CrossJoin({[Gender].[Gender].Gender.members},{[Store].[Store].[USA].[WA].[Spokane].[Store 16]}) on rows "
            + "from [ExtraCol]";
        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `exp_agg_test_distinct_count`.`gender` as `c0`,\n"
                + "    `exp_agg_test_distinct_count`.`store_country` as `c1`,\n"
                + "    `exp_agg_test_distinct_count`.`store_st` as `c2`,\n"
                + "    `exp_agg_test_distinct_count`.`store_cty` as `c3`,\n"
                + "    `exp_agg_test_distinct_count`.`store_name` as `c4`,\n"
                + "    `exp_agg_test_distinct_count`.`store_add` as `c5`\n"
                + "from\n"
                + "    `exp_agg_test_distinct_count` as `exp_agg_test_distinct_count`\n"
                + "where\n"
                + "    (`exp_agg_test_distinct_count`.`store_name` = 'Store 16')\n"
                + "group by\n"
                + "    `exp_agg_test_distinct_count`.`gender`,\n"
                + "    `exp_agg_test_distinct_count`.`store_country`,\n"
                + "    `exp_agg_test_distinct_count`.`store_st`,\n"
                + "    `exp_agg_test_distinct_count`.`store_cty`,\n"
                + "    `exp_agg_test_distinct_count`.`store_name`,\n"
                + "    `exp_agg_test_distinct_count`.`store_add`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                    ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                    + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
                    + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
                    + "    ISNULL(`c3`) ASC, `c3` ASC,\n"
                    + "    ISNULL(`c4`) ASC, `c4` ASC"
                    : "    ISNULL(`exp_agg_test_distinct_count`.`gender`) ASC, `exp_agg_test_distinct_count`.`gender` ASC,\n"
                    + "    ISNULL(`exp_agg_test_distinct_count`.`store_country`) ASC, `exp_agg_test_distinct_count`.`store_country` ASC,\n"
                    + "    ISNULL(`exp_agg_test_distinct_count`.`store_st`) ASC, `exp_agg_test_distinct_count`.`store_st` ASC,\n"
                    + "    ISNULL(`exp_agg_test_distinct_count`.`store_cty`) ASC, `exp_agg_test_distinct_count`.`store_cty` ASC,\n"
                    + "    ISNULL(`exp_agg_test_distinct_count`.`store_name`) ASC, `exp_agg_test_distinct_count`.`store_name` ASC"))).verify();

        assertThatQuery(connection, query).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[propVal]}\n"
            + "{[Measures].[Customer Count]}\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[WA].[Spokane].[Store 16]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[WA].[Spokane].[Store 16]}\n"
            + "Row #0: 5922 La Salle Ct\n"
            + "Row #0: 45\n"
            + "Row #0: 12,068\n"
            + "Row #1: 5922 La Salle Ct\n"
            + "Row #1: 39\n"
            + "Row #1: 11,523\n");
        // Should use agg table for distinct count measure
        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `exp_agg_test_distinct_count`.`testyear` as `c0`,\n"
                + "    `exp_agg_test_distinct_count`.`gender` as `c1`,\n"
                + "    `exp_agg_test_distinct_count`.`store_name` as `c2`,\n"
                + "    `exp_agg_test_distinct_count`.`unit_s` as `m0`,\n"
                + "    `exp_agg_test_distinct_count`.`cust_cnt` as `m1`\n"
                + "from\n"
                + "    `exp_agg_test_distinct_count` as `exp_agg_test_distinct_count`\n"
                + "where\n"
                + "    `exp_agg_test_distinct_count`.`testyear` = 1997\n"
                + "and\n"
                + "    `exp_agg_test_distinct_count`.`store_name` = 'Store 16'")).verify();
    }

    @Test
    @RolapContextTest(ExplicitRecognizerTestInstances.CountDistinctAllowableRollup.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testCountDistinctAllowableRollup(Connection connection) {
        // Query brings in Year and Store Name, omitting Gender.
        // It's okay to roll up the agg table in this case
        // since Customer Count is dependent on Gender.
        String query =
            "select { measures.[Customer Count], [Measures].[Unit Sales]} on columns, "
            + "non empty CrossJoin({[TimeExtra].[TimeExtra].Year.members},{[Store].[Store].[USA].[WA].[Spokane].[Store 16]}) on rows "
            + "from [ExtraCol]";

        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `exp_agg_test_distinct_count`.`testyear` as `c0`,\n"
                + "    `exp_agg_test_distinct_count`.`store_country` as `c1`,\n"
                + "    `exp_agg_test_distinct_count`.`store_st` as `c2`,\n"
                + "    `exp_agg_test_distinct_count`.`store_cty` as `c3`,\n"
                + "    `exp_agg_test_distinct_count`.`store_name` as `c4`,\n"
                + "    `exp_agg_test_distinct_count`.`store_add` as `c5`\n"
                + "from\n"
                + "    `exp_agg_test_distinct_count` as `exp_agg_test_distinct_count`\n"
                + "where\n"
                + "    (`exp_agg_test_distinct_count`.`store_name` = 'Store 16')\n"
                + "group by\n"
                + "    `exp_agg_test_distinct_count`.`testyear`,\n"
                + "    `exp_agg_test_distinct_count`.`store_country`,\n"
                + "    `exp_agg_test_distinct_count`.`store_st`,\n"
                + "    `exp_agg_test_distinct_count`.`store_cty`,\n"
                + "    `exp_agg_test_distinct_count`.`store_name`,\n"
                + "    `exp_agg_test_distinct_count`.`store_add`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                    ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                    + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
                    + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
                    + "    ISNULL(`c3`) ASC, `c3` ASC,\n"
                    + "    ISNULL(`c4`) ASC, `c4` ASC"
                    : "    ISNULL(`exp_agg_test_distinct_count`.`testyear`) ASC, `exp_agg_test_distinct_count`.`testyear` ASC,\n"
                    + "    ISNULL(`exp_agg_test_distinct_count`.`store_country`) ASC, `exp_agg_test_distinct_count`.`store_country` ASC,\n"
                    + "    ISNULL(`exp_agg_test_distinct_count`.`store_st`) ASC, `exp_agg_test_distinct_count`.`store_st` ASC,\n"
                    + "    ISNULL(`exp_agg_test_distinct_count`.`store_cty`) ASC, `exp_agg_test_distinct_count`.`store_cty` ASC,\n"
                    + "    ISNULL(`exp_agg_test_distinct_count`.`store_name`) ASC, `exp_agg_test_distinct_count`.`store_name` ASC"))).verify();

        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `exp_agg_test_distinct_count`.`testyear` as `c0`,\n"
                + "    `exp_agg_test_distinct_count`.`store_name` as `c1`,\n"
                + "    sum(`exp_agg_test_distinct_count`.`unit_s`) as `m0`,\n"
                + "    sum(`exp_agg_test_distinct_count`.`cust_cnt`) as `m1`\n"
                + "from\n"
                + "    `exp_agg_test_distinct_count` as `exp_agg_test_distinct_count`\n"
                + "where\n"
                + "    `exp_agg_test_distinct_count`.`testyear` = 1997\n"
                + "and\n"
                + "    `exp_agg_test_distinct_count`.`store_name` = 'Store 16'\n"
                + "group by\n"
                + "    `exp_agg_test_distinct_count`.`testyear`,\n"
                + "    `exp_agg_test_distinct_count`.`store_name`")).verify();
    }

    @Test
    @RolapContextTest(ExplicitRecognizerTestInstances.CountDisallowedRollup.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testCountDisallowedRollup(Connection connection) {
        String query =
            "select { measures.[Customer Count]} on columns, "
            + "non empty CrossJoin({[TimeExtra].[TimeExtra].Year.members},{[Gender].[Gender].[F]}) on rows "
            + "from [ExtraCol]";

        // Seg load query should not use agg table, since the independent
        // attributes for store are on the aggStar bitkey and not part of the
        // request and rollup is not safe
        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `time_by_day`.`the_year` as `c0`,\n"
                + "    `customer`.`gender` as `c1`,\n"
                + "    count(distinct `sales_fact_1997`.`customer_id`) as `m0`\n"
                + "from\n"
                + "    `sales_fact_1997` as `sales_fact_1997`\n"
                + "join\n"
                + "    `time_by_day` as `time_by_day`\n"
                + "on\n"
                + "    `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
                + "join\n"
                + "    `customer` as `customer`\n"
                + "on\n"
                + "    `sales_fact_1997`.`customer_id` = `customer`.`customer_id`\n"
                + "where\n"
                + "    `time_by_day`.`the_year` = 1997\n"
                + "and\n"
                + "    `customer`.`gender` = 'F'\n"
                + "group by\n"
                + "    `time_by_day`.`the_year`,\n"
                + "    `customer`.`gender`")).verify();
    }

}
