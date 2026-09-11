/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 SAS Institute, Inc.
 * Copyright (C) 2006-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.test;

import static org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct.getDatabaseProduct;
import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.SchemaModifiersEmf;


/**
 * Test for MDX syntax compatibility with Microsoft and SAS servers.
 *
 * <p>There is no MDX spec document per se, so compatibility with de facto
 * standards from the major vendors is important. Uses the FoodMart
 * database.</p>
 *
 * @see Ssas2005CompatibilityTest
 * @author sasebb
 * @since March 30, 2005
 */
@RolapContextTest(FoodmartTestInstance.class)
class CompatibilityTest {


    @BeforeAll
    public static void beforeAll() {
    }

	@BeforeEach
	public void beforeEach() {

	}

	@AfterEach
	public void afterEach() {
	}

    /**
     * Cube names are case insensitive.
     */
    @Test
    void testCubeCase(Connection connection) {
        String queryFrom = "select {[Measures].[Unit Sales]} on columns from ";
        String result =
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Row #0: 266,773\n";

        assertThatQuery(connection, queryFrom + "[Sales]").returnsGrid(result);
        assertThatQuery(connection, queryFrom + "[SALES]").returnsGrid(result);
        assertThatQuery(connection, queryFrom + "[sAlEs]").returnsGrid(result);
        assertThatQuery(connection, queryFrom + "[sales]").returnsGrid(result);
    }

    /**
     * Brackets around cube names are optional.
     */
    @Test
    void testCubeBrackets(Connection connection) {
        String queryFrom = "select {[Measures].[Unit Sales]} on columns from ";
        String result =
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Row #0: 266,773\n";

        assertThatQuery(connection, queryFrom + "Sales").returnsGrid(result);
        assertThatQuery(connection, queryFrom + "SALES").returnsGrid(result);
        assertThatQuery(connection, queryFrom + "sAlEs").returnsGrid(result);
        assertThatQuery(connection, queryFrom + "sales").returnsGrid(result);
    }

    /**
     * See how we are at diagnosing reserved words.
     */
    @Test
    void testReservedWord(Connection connection) {
    	assertThatAxis(connection, "Sales",
            "with member [Measures].ordinal as '1'\n"
            + " select {[Measures].ordinal} on columns from Sales")
            .throwsMessage("Encountered an error at (or somewhere around) input:1:9");
    	assertThatQuery(
    		connection,
            "with member [Measures].[ordinal] as '1'\n"
            + " select {[Measures].[ordinal]} on columns from Sales").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[ordinal]}\n"
            + "Row #0: 1\n");
    }

    /**
     * Dimension names are case insensitive.
     */
    @Test
    void testDimensionCase(Connection connection) {
        checkAxis(connection, "[Measures].[Unit Sales]", "[Measures].[Unit Sales]");
        checkAxis(connection, "[Measures].[Unit Sales]", "[MEASURES].[Unit Sales]");
        checkAxis(connection, "[Measures].[Unit Sales]", "[mEaSuReS].[Unit Sales]");
        checkAxis(connection, "[Measures].[Unit Sales]", "[measures].[Unit Sales]");

        checkAxis(connection, "[Customers].[Customers].[All Customers]", "[Customers].[All Customers]");
        checkAxis(connection, "[Customers].[Customers].[All Customers]", "[CUSTOMERS].[All Customers]");
        checkAxis(connection, "[Customers].[Customers].[All Customers]", "[cUsToMeRs].[All Customers]");
        checkAxis(connection, "[Customers].[Customers].[All Customers]", "[customers].[All Customers]");
    }

    /**
     * Brackets around dimension names are optional.
     */
    @Test
    void testDimensionBrackets(Connection connection) {
        checkAxis(connection, "[Measures].[Unit Sales]", "Measures.[Unit Sales]");
        checkAxis(connection, "[Measures].[Unit Sales]", "MEASURES.[Unit Sales]");
        checkAxis(connection, "[Measures].[Unit Sales]", "mEaSuReS.[Unit Sales]");
        checkAxis(connection, "[Measures].[Unit Sales]", "measures.[Unit Sales]");

        checkAxis(connection, "[Customers].[Customers].[All Customers]", "Customers.[All Customers]");
        checkAxis(connection, "[Customers].[Customers].[All Customers]", "CUSTOMERS.[All Customers]");
        checkAxis(connection, "[Customers].[Customers].[All Customers]", "cUsToMeRs.[All Customers]");
        checkAxis(connection, "[Customers].[Customers].[All Customers]", "customers.[All Customers]");
    }

    /**
     * Member names are case insensitive.
     */
    @Test
    void testMemberCase(Connection connection) {
        checkAxis(connection, "[Measures].[Unit Sales]", "[Measures].[UNIT SALES]");
        checkAxis(connection, "[Measures].[Unit Sales]", "[Measures].[uNiT sAlEs]");
        checkAxis(connection, "[Measures].[Unit Sales]", "[Measures].[unit sales]");

        checkAxis(connection, "[Measures].[Profit]", "[Measures].[Profit]");
        checkAxis(connection, "[Measures].[Profit]", "[Measures].[pRoFiT]");
        checkAxis(connection, "[Measures].[Profit]", "[Measures].[PROFIT]");
        checkAxis(connection, "[Measures].[Profit]", "[Measures].[profit]");

        checkAxis(connection, "[Customers].[Customers].[All Customers]", "[Customers].[All Customers]");
        checkAxis(connection, "[Customers].[Customers].[All Customers]", "[Customers].[ALL CUSTOMERS]");
        checkAxis(connection, "[Customers].[Customers].[All Customers]", "[Customers].[aLl CuStOmErS]");
        checkAxis(connection, "[Customers].[Customers].[All Customers]", "[Customers].[all customers]");

        checkAxis(connection, "[Customers].[Customers].[Mexico]", "[Customers].[Mexico]");
        checkAxis(connection, "[Customers].[Customers].[Mexico]", "[Customers].[MEXICO]");
        checkAxis(connection, "[Customers].[Customers].[Mexico]", "[Customers].[mExIcO]");
        checkAxis(connection, "[Customers].[Customers].[Mexico]", "[Customers].[mexico]");
    }

    /**
     * Calculated member names are case insensitive.
     */
    @Test
    @RolapConfig(key = ConfigConstants.CASE_SENSITIVE, value = "false", type = Boolean.class)
    void testCalculatedMemberCase(Connection connection) {
        assertThatQuery(
    		connection,
            "with member [Measures].[CaLc] as '1'\n"
            + " select {[Measures].[CaLc]} on columns from Sales").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[CaLc]}\n"
            + "Row #0: 1\n");
        assertThatQuery(
    		connection,
            "with member [Measures].[CaLc] as '1'\n"
            + " select {[Measures].[cAlC]} on columns from Sales").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[CaLc]}\n"
            + "Row #0: 1\n");
        assertThatQuery(
    		connection,
            "with member [mEaSuReS].[CaLc] as '1'\n"
            + " select {[MeAsUrEs].[cAlC]} on columns from Sales").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[CaLc]}\n"
            + "Row #0: 1\n");
    }

    /**
     * Solve order is case insensitive.
     */
    @Test
    void testSolveOrderCase(Connection connection) {
        checkSolveOrder(connection, "SOLVE_ORDER");
        checkSolveOrder(connection, "SoLvE_OrDeR");
        checkSolveOrder(connection, "solve_order");
    }

    private void checkSolveOrder(Connection connection, String keyword) {
        assertThatQuery(
    		connection,
            "WITH\n"
            + "   MEMBER [Store].[StoreCalc] as '0', " + keyword + "=0\n"
            + "   MEMBER [Product].[ProdCalc] as '1', " + keyword + "=1\n"
            + "SELECT\n"
            + "   { [Product].[ProdCalc] } ON columns,\n"
            + "   { [Store].[StoreCalc] } ON rows\n"
            + "FROM Sales").returnsGrid(

            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Product].[Product].[ProdCalc]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[StoreCalc]}\n"
            + "Row #0: 1\n");
    }

    /**
     * Brackets around member names are optional.
     */
    @Test
    void testMemberBrackets(Connection connection) {
        checkAxis(connection, "[Measures].[Profit]", "[Measures].Profit");
        checkAxis(connection, "[Measures].[Profit]", "[Measures].pRoFiT");
        checkAxis(connection, "[Measures].[Profit]", "[Measures].PROFIT");
        checkAxis(connection, "[Measures].[Profit]", "[Measures].profit");

        checkAxis(
    		connection,
            "[Customers].[Customers].[Mexico]",
            "[Customers].Mexico");
        checkAxis(
    		connection,
            "[Customers].[Customers].[Mexico]",
            "[Customers].MEXICO");
        checkAxis(
    		connection,
            "[Customers].[Customers].[Mexico]",
            "[Customers].mExIcO");
        checkAxis(
    		connection,
            "[Customers].[Customers].[Mexico]",
            "[Customers].mexico");
    }

    /**
     * Hierarchy names of the form [Dim].[Hier], [Dim.Hier], and
     * Dim.Hier are accepted.
     */
    @Test
    void testHierarchyNames(Connection connection) {
        checkAxis(connection, "[Customers].[Customers].[All Customers]", "[Customers].[All Customers]");
        checkAxis(
    		connection,
            "[Customers].[Customers].[All Customers]",
            "[Customers].[Customers].[All Customers]");
        checkAxis(
    		connection,
            "[Customers].[Customers].[All Customers]",
            "Customers.[Customers].[All Customers]");
        checkAxis(
    		connection,
            "[Customers].[Customers].[All Customers]",
            "[Customers].Customers.[All Customers]");
        if (false) {
            // don't know if this makes sense
            checkAxis(
        		connection,
                "[Customers].[Customers].[All Customers]",
                "[Customers.Customers].[All Customers]");
        }
    }

    private void checkAxis(Connection connection, String result, String expression) {
        assertThatAxis(connection, "Sales", expression).returns(result);
    }

    /**
     * Tests that a #null member on a Hiearchy Level of type String can
     * still be looked up when case sensitive is off.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.Ssas2005CompatibilityTestModifier4.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testCaseInsensitiveNullMember(Context<?> context, Dialect dialect, Connection connection) {
        if (getDatabaseProduct(dialect.name()) == DatabaseProduct.LUCIDDB) {
            // TODO jvs 29-Nov-2006:  LucidDB is strict about
            // null literals (type can't be inferred in this context);
            // maybe enhance the inline table to use the columndef
            // types to apply a CAST.
            return;
        }
        // This test should work irrespective of the case-sensitivity setting.
        //props.CaseSensitive;
//        discard();

        assertThatQuery(
    		connection,
            "select {[Measures].[Unit Sales]} ON COLUMNS,\n"
            + "  {[Alternative Promotion].[#null]} ON ROWS \n"
            + "  from [Sales_inline]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Alternative Promotion].[Alternative Promotion].[#null]}\n"
            + "Row #0: \n");
    }

    /**
     * Tests that data in Hierarchy.Level attribute "nameColumn" can be null.
     * This will map to the #null memeber.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CompatibilityTestModifier.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNullNameColumn(Context<?> context, Dialect dialect, Connection connection) {
        switch (getDatabaseProduct(dialect.name())) {
        case LUCIDDB:
            // TODO jvs 29-Nov-2006:  See corresponding comment in
            // testCaseInsensitiveNullMember
            return;
        case HSQLDB:
            // This test exposes a bug in hsqldb. The following query should
            // return 1 row, but returns none.
            //
            // select "alt_promotion"."promo_id" as "c0",
            //   "alt_promotion"."promo_name" as "c1"
            // from (
            //    select 0 as "promo_id", null as "promo_name"
            //    from "days" where "day" = 1
            //    union all
            //    select 1 as "promo_id", 'Promo1' as "promo_name"
            //    from "days" where "day" = 1) as "alt_promotion"
            // where UPPER("alt_promotion"."promo_name") = UPPER('Promo1')
            // group by "alt_promotion"."promo_id",
            //    "alt_promotion"."promo_name"
            // order by
            //   CASE WHEN "alt_promotion"."promo_id" IS NULL THEN 1 ELSE 0 END,
            //   "alt_promotion"."promo_id" ASC
            return;
        }
        final String cubeName = "Sales_inline";
        /*
        String schema = SchemaUtil.getSchema(
    		baseSchema,
            null,
            "<Cube name=\"" + cubeName + "\">\n"
            + "  <Table name=\"sales_fact_1997\"/>\n"
            + "  <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>\n"
            + "  <Dimension name=\"Alternative Promotion\" foreignKey=\"promotion_id\">\n"
            + "    <Hierarchy hasAll=\"true\" primaryKey=\"promo_id\">\n"
            + "      <InlineTable alias=\"alt_promotion\">\n"
            + "        <ColumnDefs>\n"
            + "          <ColumnDef name=\"promo_id\" type=\"Numeric\"/>\n"
            + "          <ColumnDef name=\"promo_name\" type=\"String\"/>\n"
            + "        </ColumnDefs>\n"
            + "        <Rows>\n"
            + "          <Row>\n"
            + "            <Value column=\"promo_id\">0</Value>\n"
            + "          </Row>\n"
            + "          <Row>\n"
            + "            <Value column=\"promo_id\">1</Value>\n"
            + "            <Value column=\"promo_name\">Promo1</Value>\n"
            + "          </Row>\n"
            + "        </Rows>\n"
            + "      </InlineTable>\n"
            + "      <Level name=\"Alternative Promotion\" column=\"promo_id\" nameColumn=\"promo_name\" uniqueMembers=\"true\"/> \n"
            + "    </Hierarchy>\n"
            + "  </Dimension>\n"
            + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"Standard\" visible=\"false\"/>\n"
            + "  <Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\"\n"
            + "      formatString=\"#,###.00\"/>\n"
            + "</Cube>", null, null, null, null);
        */
        assertThatQuery(
    		connection,
            "select {"
            + "[Alternative Promotion].[#null], "
            + "[Alternative Promotion].[Promo1]} ON COLUMNS\n"
            + "from [" + cubeName + "] ").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Alternative Promotion].[Alternative Promotion].[#null]}\n"
            + "{[Alternative Promotion].[Alternative Promotion].[Promo1]}\n"
            + "Row #0: 195,448\n"
            + "Row #0: \n");
    }

    /**
     * Tests that NULL values sort last on all platforms. On some platforms,
     * such as MySQL, NULLs naturally come before other values, so we have to
     * generate a modified ORDER BY clause.
      */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CompatibilityTestModifier2.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNullCollation(Dialect dialect, Connection connection) {
        if (dialect.supportsGroupByExpressions()) {
            // Derby does not support expressions in the GROUP BY clause,
            // therefore this testing strategy of using an expression for the
            // store key won't work. Give the test a bye.
            return;
        }
        final String cubeName = "Store_NullsCollation";
        assertThatQuery(
    		connection,
            "select { [Measures].[Store Sqft] } on columns,\n"
            + " NON EMPTY topcount(\n"
            + "    {[Store].[Store Name].members},\n"
            + "    5,\n"
            + "    [measures].[store sqft]) on rows\n"
            + "from [" + cubeName + "] ").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Store Sqft]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store 3]}\n"
            + "{[Store].[Store 18]}\n"
            + "{[Store].[Store 9]}\n"
            + "{[Store].[Store 10]}\n"
            + "{[Store].[Store 20]}\n"
            + "Row #0: 39,696\n"
            + "Row #1: 38,382\n"
            + "Row #2: 36,509\n"
            + "Row #3: 34,791\n"
            + "Row #4: 34,452\n");
    }

    /**
     * Tests that property names are case sensitive iff the
     * "mondrian.olap.case.sensitive" property is set.
     *
     * <p>The test does not alter this property: for testing coverage, we assume
     * that you run the test once with mondrian.olap.case.sensitive=true,
     * and once with mondrian.olap.case.sensitive=false.
     */
    @Test
    void testPropertyCaseSensitivity(Context<?> context, Connection connection) {
        boolean caseSensitive = context.getConfigValue(
                ConfigConstants.CASE_SENSITIVE, ConfigConstants.CASE_SENSITIVE_DEFAULT_VALUE, Boolean.class);

        // A user-defined property of a member.
        assertThatExpr(connection, "Sales",
            "[Store].[USA].[CA].[Beverly Hills].[Store 6].Properties(\"Store Type\")").returns(
            "Gourmet Supermarket");

        if (caseSensitive) {
        	assertThatExpr(connection, "Sales",
                "[Store].[USA].[CA].[Beverly Hills].[Store 6].Properties(\"store tYpe\")").throwsMessage(
                "Property 'store tYpe' is not valid for member '[Store].[USA].[CA].[Beverly Hills].[Store 6]'");
        } else {
        	assertThatExpr(connection, "Sales",
                "[Store].[USA].[CA].[Beverly Hills].[Store 6].Properties(\"store tYpe\")").returns(
                "Gourmet Supermarket");
        }

        // A builtin property of a member.
        assertThatExpr(connection, "Sales",
            "[Store].[USA].[CA].[Beverly Hills].[Store 6].Properties(\"LEVEL_NUMBER\")").returns(
            "4");
        if (caseSensitive) {
        	assertThatExpr(connection, "Sales",
                "[Store].[USA].[CA].[Beverly Hills].[Store 6].Properties(\"Level_Number\")").throwsMessage(
                "Property 'store tYpe' is not valid for member '[Store].[USA].[CA].[Beverly Hills].[Store 6]'");
        } else {
        	assertThatExpr(connection, "Sales",
                "[Store].[USA].[CA].[Beverly Hills].[Store 6].Properties(\"Level_Number\")").returns(
                "4");
        }

        // Cell properties.
        Result result = executeQuery(
    		connection,
            "select {[Measures].[Unit Sales],[Measures].[Store Sales]} on columns,\n"
            + " {[Gender].[M]} on rows\n"
            + "from Sales");
        Cell cell = result.getCell(new int[]{0, 0});
        assertEquals("135,215", cell.getPropertyValue("FORMATTED_VALUE"));
        if (caseSensitive) {
            assertNull(cell.getPropertyValue("Formatted_Value"));
        } else {
            assertEquals("135,215", cell.getPropertyValue("Formatted_Value"));
        }
    }
}
