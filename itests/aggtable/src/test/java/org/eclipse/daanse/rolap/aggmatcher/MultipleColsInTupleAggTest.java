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
package org.eclipse.daanse.rolap.aggmatcher;

import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.common.result.RolapAxis;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;

/**
 * Testcase for levels that contain multiple columns and are
 * collapsed in the agg table.
 *
 * @author Will Gorman
 */
@RolapContextTest(value = MultipleColsInTupleAggTestInstance.class, dbScope = DbScope.PER_CLASS)
class MultipleColsInTupleAggTest {

    /** The "true" total/sliced-total, computed once from the {@code fact} CSV fixture data. */
    private static final double EXPECTED_TOTAL = 66;
    private static final double EXPECTED_SLICED_TOTAL = 9;

    /**
     * The original test compared the same queries computed twice within one
     * method, toggling {@code USE_AGGREGATES} between the calls -- the new
     * testkit has no supported way to mutate a context's config after it is
     * built, so it is now two independent tests ({@code READ_AGGREGATES} is
     * {@code false} in both, so aggregate tables are never actually read;
     * both must recompute the same totals directly from the fact data).
     */
    @Test
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testTotalWithoutAggregates(Connection connection) {
        assertTotals(connection);
    }

    @Test
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testTotalWithAggregatesButNotRead(Connection connection) {
        assertTotals(connection);
    }

    private void assertTotals(Connection connection) {
        String mdx = "select {[Measures].[Total]} on columns from [Fact]";
        Result result = executeQuery(connection, mdx);
        Object v = result.getCell(new int[] {0}).getValue();
        assertEquals(EXPECTED_TOTAL, ((Number) v).doubleValue());

        String mdx2 =
            "select {[Measures].[Total]} on columns from [Fact] where "
            + "{[Product].[Cat One].[Prod Cat One].[One]}";
        Result aresult = executeQuery(connection, mdx2);
        Object av = aresult.getCell(new int[] {0}).getValue();
        assertEquals(EXPECTED_SLICED_TOTAL, ((Number) av).doubleValue());
    }

    @Test
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testTupleSelection(Connection connection) {
        String mdx =
            "select "
            + "{[Measures].[Total]} on columns, "
            + "non empty CrossJoin({[Product].[Cat One].[Prod Cat One]},"
            + "{[Store].[All Stores]}) on rows "
            + "from [Fact]";

        assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Total]}\n"
            + "Axis #2:\n"
            + "{[Product].[Product].[Cat One].[Prod Cat One],"
            + " [Store].[Store].[All Stores]}\n"
            + "Row #0: 15\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testNativeFilterWithoutMeasures(Connection connection) {
        // Native filter without any measures hit an edge case that
        // could fail to include the Agg star in the WHERE clause,
        // and could also mishandle the field referred to in the native
        // HAVING clause.  ANALYZER-2655
        assertThatQuery(connection,
            "select "
            + "Filter([Product].[Category].members, "
            + "Product.CurrentMember.Caption MATCHES (\"(?i).*Two.*\") )"
            + " on columns "
            + "from [Fact]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Product].[Product].[Cat Two]}\n"
            + "Row #0: 33\n");
        //  CurrentMember.Name should map to
        // `test_lp_xxx_fact`.`product_category`, with 2 member matches
        assertThatQuery(connection,
            "select "
            + "Filter([Product].[Product Category].members, "
            + "Product.CurrentMember.Name MATCHES (\"(?i).*Two.*\") )"
            + " on columns "
            + "from [Fact]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Product].[Product].[Cat Two].[Prod Cat Two]}\n"
            + "{[Product].[Product].[Cat One].[Prod Cat Two]}\n"
            + "Row #0: 16\n"
            + "Row #0: 18\n");
        // .Caption is defined as `product_cat`.`cap`.
        // [Cat One].[Prod Cat Two] has just one caption matching -- "PCTwo"
        assertThatQuery(connection,
            "select "
            + "Filter([Product].[Product Category].Members, "
            + "Product.CurrentMember.Caption MATCHES (\"(?i).*Two.*\") )"
            + " on columns "
            + "from [Fact]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Product].[Product].[Cat One].[Prod Cat Two]}\n"
            + "Row #0: 18\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testNativeFilterWithoutMeasuresAndLevelWithProps(Connection connection) {
        // similar to the previous test, but verifies a case where
        // a level property is the extra column that requires joining
        // agg star back to the dim table.  This test also uses the bottom
        // level of the dim
        final String query = "select "
            + "Filter([Product].[Product].[Product Name].members, "
            + "Product.Product.CurrentMember.Caption MATCHES (\"(?i).*Two.*\") )"
            + " on columns "
            + "from [Fact] ";
        assertThatQuery(connection,
            query).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Product].[Product].[Cat One].[Prod Cat One].[Two]}\n"
            + "Row #0: 6\n");

        // check generated sql only for native evaluation
        if (connection.getContext().getConfigValue(ConfigConstants.ENABLE_NATIVE_FILTER,
                ConfigConstants.ENABLE_NATIVE_FILTER_DEFAULT_VALUE, Boolean.class)) {
          SqlAssert.forQuery(connection,
              query).expectSql(new SqlPattern[] {
                  new SqlPattern(
                      DatabaseProduct.MYSQL,
                  "select\n"
                  + "    `cat`.`cat` as `c0`,\n"
                  + "    `cat`.`cap` as `c1`,\n"
                  + "    `cat`.`ord` as `c2`,\n"
                  + "    `cat`.`name3` as `c3`,\n"
                  + "    `product_cat`.`name2` as `c4`,\n"
                  + "    `product_cat`.`cap` as `c5`,\n"
                  + "    `product_cat`.`ord` as `c6`,\n"
                  + "    `test_lp_xx2_fact`.`prodname` as `c7`,\n"
                  + "    `product_csv`.`color` as `c8`\n"
                  + "from\n"
                  + "    `product_csv` as `product_csv`,\n"
                  + "    `product_cat` as `product_cat`,\n"
                  + "    `cat` as `cat`,\n"
                  + "    `test_lp_xx2_fact` as `test_lp_xx2_fact`\n"
                  + "where\n"
                  + "    `product_cat`.`cat` = `cat`.`cat`\n"
                  + "and\n"
                  + "    `product_csv`.`prod_cat` = `product_cat`.`prod_cat`\n"
                  + "and\n"
                  + "    `product_csv`.`name1` = `test_lp_xx2_fact`.`prodname`\n"
                  + "group by\n"
                  + "    `cat`.`cat`,\n"
                  + "    `cat`.`cap`,\n"
                  + "    `cat`.`ord`,\n"
                  + "    `cat`.`name3`,\n"
                  + "    `product_cat`.`name2`,\n"
                  + "    `product_cat`.`cap`,\n"
                  + "    `product_cat`.`ord`,\n"
                  + "    `test_lp_xx2_fact`.`prodname`,\n"
                  + "    `product_csv`.`color`\n"
                  + "having\n"
                  + "    c7 IS NOT NULL AND UPPER(c7) REGEXP '.*TWO.*'\n"
                  + "order by\n"
                  + (getDialect(connection).requiresOrderByAlias()
                      ? "    ISNULL(`c2`) ASC, `c2` ASC,\n"
                      + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
                      + "    ISNULL(`c6`) ASC, `c6` ASC,\n"
                      + "    ISNULL(`c4`) ASC, `c4` ASC,\n"
                      + "    ISNULL(`c7`) ASC, `c7` ASC"
                      : "    ISNULL(`cat`.`ord`) ASC, `cat`.`ord` ASC,\n"
                      + "    ISNULL(`product_cat`.`ord`) ASC, `product_cat`.`ord` ASC,\n"
                      + "    ISNULL(`test_lp_xx2_fact`.`prodname`) ASC, "
                      + "`test_lp_xx2_fact`.`prodname` ASC"), null)}).verify();
        }
        Axis axis = executeQuery(connection, "select {"
            + "Filter([Product].[Product].[Product Name].members, "
            + "Product.Product.CurrentMember.Caption MATCHES (\"(?i).*Two.*\") )"
            + "} on columns from Fact").getAxes()[0];
        assertEquals(
            "Black",
            ((RolapAxis) axis).getTupleList().get(0).get(0)
                .getPropertyValue("Product Color"), "Member property value was not loaded correctly.");
    }

    @Test
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
    void testChildSelection(Connection connection) {
        String mdx = "select {[Measures].[Total]} on columns, "
            + "non empty [Product].[Cat One].Children on rows from [Fact]";
        assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Total]}\n"
            + "Axis #2:\n"
            + "{[Product].[Product].[Cat One].[Prod Cat Two]}\n"
            + "{[Product].[Product].[Cat One].[Prod Cat One]}\n"
            + "Row #0: 18\n"
            + "Row #1: 15\n");
    }

}
