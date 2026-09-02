/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2015-2017 Hitachi Vantara and others
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

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * @author Andrey Khayrutdinov
 */
@RolapContextTest(value = AggregationOverAggTableTestInstance.class, dbScope = DbScope.PER_CLASS)
class AggregationOverAggTableTest {

    @Test
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testAvgMeasureLowestGranularity(Connection connection) {
        String query =
            "select {[Measures].[Avg Unit Sales]} on columns, "
            + "non empty CrossJoin({[TimeExtra].[1997].[Q1].Children},{[Gender].[M]}) on rows "
            + "from [ExtraCol]";

        assertThatQuery(connection,
            query).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Avg Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[TimeExtra].[TimeExtra].[1997].[Q1].[1], [Gender].[Gender].[M]}\n"
            + "{[TimeExtra].[TimeExtra].[1997].[Q1].[2], [Gender].[Gender].[M]}\n"
            + "{[TimeExtra].[TimeExtra].[1997].[Q1].[3], [Gender].[Gender].[M]}\n"
            + "Row #0: 3\n"
            + "Row #1: 3\n"
            + "Row #2: 3\n");

        SqlAssert.forQuery(connection,
            query).expectSql(mysqlPattern(
                "select\n"
                + "    `agg_c_avg_sales_fact_1997`.`the_year` as `c0`,\n"
                + "    `agg_c_avg_sales_fact_1997`.`quarter` as `c1`,\n"
                + "    `agg_c_avg_sales_fact_1997`.`month_of_year` as `c2`,\n"
                + "    `agg_c_avg_sales_fact_1997`.`gender` as `c3`,\n"
                + "    (`agg_c_avg_sales_fact_1997`.`unit_sales`) / (`agg_c_avg_sales_fact_1997`.`fact_count`) as `m0`\n"
                + "from\n"
                + "    `agg_c_avg_sales_fact_1997` as `agg_c_avg_sales_fact_1997`\n"
                + "where\n"
                + "    `agg_c_avg_sales_fact_1997`.`the_year` = 1997\n"
                + "and\n"
                + "    `agg_c_avg_sales_fact_1997`.`quarter` = 'Q1'\n"
                + "and\n"
                + "    `agg_c_avg_sales_fact_1997`.`month_of_year` in (1, 2, 3)\n"
                + "and\n"
                + "    `agg_c_avg_sales_fact_1997`.`gender` = 'M'")).verify();
    }

}
