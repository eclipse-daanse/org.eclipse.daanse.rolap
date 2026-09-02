/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2020 Hitachi Vantara..  All rights reserved.
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

package org.eclipse.daanse.olap.fun;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


/**
 * <code>SortTest</code> tests the collation order of positive and negative
 * infinity, and {@link Double#NaN}.
 *
 * @author jhyde
 * @since Sep 21, 2006
 */
@RolapContextTest(FoodmartTestInstance.class)
class SortTest {

  /**
   * Access properties via this object and their values will be reset.
   */

  @AfterEach
  public void afterEach() {
  }

  @Test
  void testOrderDesc(Context<?> context) {
    // In MSAS, NULLs collate last (or almost last, along with +inf and
    // NaN) whereas in Mondrian NULLs collate least (that is, before -inf).
      assertThatQuery(context.getConnectionWithDefaultRole(),
      "with"
        + "   member [Measures].[Foo] as '\n"
        + "      Iif([Promotion Media].CurrentMember IS [Promotion Media].[TV], 1.0 / 0.0,\n"
        + "         Iif([Promotion Media].CurrentMember IS [Promotion Media].[Radio], -1.0 / 0.0,\n"
        + "            Iif([Promotion Media].CurrentMember IS [Promotion Media].[Bulk Mail], 0.0 / 0.0,\n"
        + "               Iif([Promotion Media].CurrentMember IS [Promotion Media].[Daily Paper], NULL,\n"
        + "       [Measures].[Unit Sales])))) '\n"
        + "select \n"
        + "    {[Measures].[Foo]} on columns, \n"
        + "    order(except([Promotion Media].[Media Type].members,{[Promotion Media].[Media Type].[No Media]}),"
        + "[Measures].[Foo],DESC) on rows\n"
        + "from Sales")
      .returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Foo]}\n"
        + "Axis #2:\n"
        + "{[Promotion Media].[Promotion Media].[TV]}\n"
        + "{[Promotion Media].[Promotion Media].[Bulk Mail]}\n"
        + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio, TV]}\n"
        + "{[Promotion Media].[Promotion Media].[Product Attachment]}\n"
        + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio]}\n"
        + "{[Promotion Media].[Promotion Media].[Cash Register Handout]}\n"
        + "{[Promotion Media].[Promotion Media].[Sunday Paper, Radio]}\n"
        + "{[Promotion Media].[Promotion Media].[Street Handout]}\n"
        + "{[Promotion Media].[Promotion Media].[Sunday Paper]}\n"
        + "{[Promotion Media].[Promotion Media].[In-Store Coupon]}\n"
        + "{[Promotion Media].[Promotion Media].[Sunday Paper, Radio, TV]}\n"
        + "{[Promotion Media].[Promotion Media].[Radio]}\n"
        + "{[Promotion Media].[Promotion Media].[Daily Paper]}\n"
        + "Row #0: Infinity\n"
        + "Row #1: NaN\n"
        + "Row #2: 9,513\n"
        + "Row #3: 7,544\n"
        + "Row #4: 6,891\n"
        + "Row #5: 6,697\n"
        + "Row #6: 5,945\n"
        + "Row #7: 5,753\n"
        + "Row #8: 4,339\n"
        + "Row #9: 3,798\n"
        + "Row #10: 2,726\n"
        + "Row #11: -Infinity\n"
        + "Row #12: \n" );
  }

  @Test
  void testOrderAndRank(Context<?> context) {
      assertThatQuery(context.getConnectionWithDefaultRole(),
      "with "
        + "   member [Measures].[Foo] as '\n"
        + "      Iif([Promotion Media].CurrentMember IS [Promotion Media].[TV], 1.0 / 0.0,\n"
        + "         Iif([Promotion Media].CurrentMember IS [Promotion Media].[Radio], -1.0 / 0.0,\n"
        + "            Iif([Promotion Media].CurrentMember IS [Promotion Media].[Bulk Mail], 0.0 / 0.0,\n"
        + "               Iif([Promotion Media].CurrentMember IS [Promotion Media].[Daily Paper], NULL,\n"
        + "                  [Measures].[Unit Sales])))) '\n"
        + "   member [Measures].[R] as '\n"
        + "      Rank([Promotion Media].CurrentMember, [Promotion Media].Members, [Measures].[Foo]) '\n"
        + "select\n"
        + "    {[Measures].[Foo], [Measures].[R]} on columns, \n"
        + "    order([Promotion Media].[Media Type].members,[Measures].[Foo]) on rows\n"
        + "from Sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Foo]}\n"
        + "{[Measures].[R]}\n"
        + "Axis #2:\n"
        + "{[Promotion Media].[Promotion Media].[Daily Paper]}\n"
        + "{[Promotion Media].[Promotion Media].[Radio]}\n"
        + "{[Promotion Media].[Promotion Media].[Sunday Paper, Radio, TV]}\n"
        + "{[Promotion Media].[Promotion Media].[In-Store Coupon]}\n"
        + "{[Promotion Media].[Promotion Media].[Sunday Paper]}\n"
        + "{[Promotion Media].[Promotion Media].[Street Handout]}\n"
        + "{[Promotion Media].[Promotion Media].[Sunday Paper, Radio]}\n"
        + "{[Promotion Media].[Promotion Media].[Cash Register Handout]}\n"
        + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio]}\n"
        + "{[Promotion Media].[Promotion Media].[Product Attachment]}\n"
        + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio, TV]}\n"
        + "{[Promotion Media].[Promotion Media].[No Media]}\n"
        + "{[Promotion Media].[Promotion Media].[Bulk Mail]}\n"
        + "{[Promotion Media].[Promotion Media].[TV]}\n"
        + "Row #0: \n"
        + "Row #0: 15\n"
        + "Row #1: -Infinity\n"
        + "Row #1: 14\n"
        + "Row #2: 2,726\n"
        + "Row #2: 13\n"
        + "Row #3: 3,798\n"
        + "Row #3: 12\n"
        + "Row #4: 4,339\n"
        + "Row #4: 11\n"
        + "Row #5: 5,753\n"
        + "Row #5: 10\n"
        + "Row #6: 5,945\n"
        + "Row #6: 9\n"
        + "Row #7: 6,697\n"
        + "Row #7: 8\n"
        + "Row #8: 6,891\n"
        + "Row #8: 7\n"
        + "Row #9: 7,544\n"
        + "Row #9: 6\n"
        + "Row #10: 9,513\n"
        + "Row #10: 5\n"
        + "Row #11: 195,448\n"
        + "Row #11: 4\n"
        + "Row #12: NaN\n"
        + "Row #12: 2\n"
        + "Row #13: Infinity\n"
        + "Row #13: 1\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.CELL_BATCH_SIZE, value = "2", type = Integer.class)
  void testListTuplesExceedsCellEvalLimit(Context<?> context) {
    // cell eval performed within the sort, so cycles to retrieve all cells.
      assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "ORDER(GENERATE(CROSSJOIN({[Customers].[USA].[WA].Children},{[Product].[Food]}),\n"
        + "{([Customers].CURRENTMEMBER,[Product].CURRENTMEMBER)}), [Measures].[Store Sales], BASC, [Customers]"
        + ".CURRENTMEMBER.ORDERKEY,BASC)").returns(
      "{[Customers].[Customers].[USA].[WA].[Sedro Woolley], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Anacortes], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Bellingham], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Seattle], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Issaquah], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Redmond], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Marysville], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Edmonds], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Renton], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Kirkland], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Walla Walla], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Lynnwood], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Ballard], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Everett], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Burien], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Tacoma], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Yakima], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Puyallup], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Bremerton], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Olympia], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Port Orchard], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane], [Product].[Product].[Food]}" );

  }

  @Test
  void testNonBreakingAscendingComparator(Context<?> context) {
    // more than one non-breaking sortkey, where first is ascending
      assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "ORDER(GENERATE(CROSSJOIN({[Customers].[USA].[WA].Children},{[Product].[Food]}),\n"
        + "{([Customers].CURRENTMEMBER,[Product].CURRENTMEMBER)}), [Measures].[Unit Sales], DESC, [Measures].[Store "
        + "Sales], ASC)").returns(
      "{[Customers].[Customers].[USA].[WA].[Spokane], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Olympia], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Port Orchard], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Bremerton], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Puyallup], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Yakima], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Tacoma], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Burien], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Everett], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Ballard], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Kirkland], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Marysville], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Renton], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Walla Walla], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Lynnwood], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Redmond], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Issaquah], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Edmonds], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Seattle], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Anacortes], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Bellingham], [Product].[Product].[Food]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Sedro Woolley], [Product].[Product].[Food]}" );
  }

  @Test
  void testMultiLevelBrkSort(Context<?> context) {
    // first 2 sort keys depend on Customers hierarchy only.
    // 3rd requires both Customer and Product
      assertThatQuery(context.getConnectionWithDefaultRole(), "WITH\n"
      + "SET [*NATIVE_CJ_SET] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Customers_],[*BASE_MEMBERS__Product_])'\n"
      + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Customers].CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR"
      + "([Customers].CURRENTMEMBER,[Customers].[City]).ORDERKEY,BASC,[Measures].[*SORTED_MEASURE],BASC)'\n"
      + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0],[Measures]"
      + ".[*FORMATTED_MEASURE_1]}'\n"
      + "SET [*BASE_MEMBERS__Customers_] AS '[Customers].[Name].MEMBERS'\n"
      + "SET [*BASE_MEMBERS__Product_] AS '[Product].[Product Name].MEMBERS'\n"
      + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Customers].CURRENTMEMBER,[Product].CURRENTMEMBER)})"
      + "'\n"
      + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
      + "SOLVE_ORDER=500\n"
      + "MEMBER [Measures].[*FORMATTED_MEASURE_1] AS '[Measures].[Store Sales]', FORMAT_STRING = '#,###', "
      + "SOLVE_ORDER=500\n"
      + "MEMBER [Measures].[*SORTED_MEASURE] AS '([Measures].[*FORMATTED_MEASURE_1])', SOLVE_ORDER=400\n"
      + "SELECT\n"
      + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
      + ", NON EMPTY\n"
      + "HEAD([*SORTED_ROW_AXIS],5) ON ROWS\n"
      + "FROM [Sales]").returnsGrid("Axis #0:\n"
      + "{}\n"
      + "Axis #1:\n"
      + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
      + "{[Measures].[*FORMATTED_MEASURE_1]}\n"
      + "Axis #2:\n"
      + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Jeanne Derry], [Product].[Product].[Food].[Starchy Foods].[Starchy Foods].[Pasta]"
      + ".[Monarch].[Monarch Spaghetti]}\n"
      + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Jeanne Derry], [Product].[Product].[Food].[Deli].[Side Dishes].[Deli Salads]"
      + ".[Lake].[Lake Low Fat Cole Slaw]}\n"
      + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Jeanne Derry], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods]"
      + ".[Waffles].[Big Time].[Big Time Low Fat Waffles]}\n"
      + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Jeanne Derry], [Product].[Product].[Food].[Baking Goods].[Baking Goods].[Sugar]"
      + ".[Super].[Super Brown Sugar]}\n"
      + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Jeanne Derry], [Product].[Product].[Non-Consumable].[Health and Hygiene].[Bathroom"
      + " Products].[Mouthwash].[Faux Products].[Faux Products Laundry Detergent]}\n"
      + "Row #0: 2\n"
      + "Row #0: 3\n"
      + "Row #1: 3\n"
      + "Row #1: 3\n"
      + "Row #2: 3\n"
      + "Row #2: 3\n"
      + "Row #3: 3\n"
      + "Row #3: 4\n"
      + "Row #4: 2\n"
      + "Row #4: 4\n" );
  }

  @Test
  void testAttributesWithShowsRowsColumnsWithMeasureData(Context<?> context) {
    // Sort on Attributes with Shows rows/columns with measure data.   Most common use case.
      assertThatQuery(context.getConnectionWithDefaultRole(), "WITH\n"
      + "SET [*NATIVE_CJ_SET_WITH_SLICER] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Store_],NONEMPTYCROSSJOIN"
      + "([*BASE_MEMBERS__Education Level_],NONEMPTYCROSSJOIN([*BASE_MEMBERS__Product_],NONEMPTYCROSSJOIN"
      + "([*BASE_MEMBERS__Yearly Income_],[*BASE_MEMBERS__Store Type_]))))'\n"
      + "SET [*NATIVE_CJ_SET] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Store].[Store].CURRENTMEMBER,[Education Level].[Education Level]"
      + ".CURRENTMEMBER,[Product].[Product].CURRENTMEMBER,[Yearly Income].[Yearly Income].CURRENTMEMBER)})'\n"
      + "SET [*BASE_MEMBERS__Store Type_] AS '{[Store Type].[Store Type].[All Store Types].[HeadQuarters],[Store Type].[Store Type].[All Store "
      + "Types].[Mid-Size Grocery],[Store Type].[Store Type].[All Store Types].[Small Grocery]}'\n"
      + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Education Level].[Education Level].CURRENTMEMBER.ORDERKEY,BASC,[Product].[Product]"
      + ".CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].[Product].CURRENTMEMBER,[Product].[Product].[Product Family]).ORDERKEY,BASC,"
      + "[Yearly Income].CURRENTMEMBER.ORDERKEY,BASC)'\n"
      + "SET [*SORTED_COL_AXIS] AS 'ORDER([*CJ_COL_AXIS],[Store].[Store].CURRENTMEMBER.ORDERKEY,BASC,[Measures].CURRENTMEMBER"
      + ".ORDERKEY,BASC)'\n"
      + "SET [*BASE_MEMBERS__Education Level_] AS '[Education Level].[Education Level].[Education Level].MEMBERS'\n"
      + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
      + "SET [*BASE_MEMBERS__Store_] AS '[Store].[Store].[Store Country].MEMBERS'\n"
      + "SET [*BASE_MEMBERS__Yearly Income_] AS '[Yearly Income].[Yearly Income].[Yearly Income].MEMBERS'\n"
      + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Store Type].[Store Type].CURRENTMEMBER)})'\n"
      + "SET [*CJ_COL_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Store].[Store].CURRENTMEMBER)})'\n"
      + "SET [*BASE_MEMBERS__Product_] AS '[Product].[Product].[Product Department].MEMBERS'\n"
      + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Education Level].[Education Level].CURRENTMEMBER,[Product].[Product].CURRENTMEMBER,"
      + "[Yearly Income].[Yearly Income].CURRENTMEMBER)})'\n"
      + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
      + "SOLVE_ORDER=500\n"
      + "SELECT\n"
      + "CROSSJOIN([*SORTED_COL_AXIS],[*BASE_MEMBERS__Measures_]) ON COLUMNS\n"
      + ", NON EMPTY\n"
      + "{HEAD([*SORTED_ROW_AXIS],5), TAIL([*SORTED_ROW_AXIS],5)} ON ROWS\n"
      + "FROM [Sales]\n"
      + "WHERE ([*CJ_SLICER_AXIS])\n").returnsGrid( "Axis #0:\n"
      + "{[Store Type].[Store Type].[Mid-Size Grocery]}\n"
      + "{[Store Type].[Store Type].[Small Grocery]}\n"
      + "Axis #1:\n"
      + "{[Store].[Store].[USA], [Measures].[*FORMATTED_MEASURE_0]}\n"
      + "Axis #2:\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$110K - "
      + "$130K]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$130K - "
      + "$150K]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$150K +]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$50K - "
      + "$70K]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$70K - "
      + "$90K]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$130K - $150K]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$150K +]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$30K - $50K]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$50K - $70K]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$90K - $110K]}\n"
      + "Row #0: 15\n"
      + "Row #1: 8\n"
      + "Row #2: 13\n"
      + "Row #3: 75\n"
      + "Row #4: 43\n"
      + "Row #5: 5\n"
      + "Row #6: 4\n"
      + "Row #7: 9\n"
      + "Row #8: 2\n"
      + "Row #9: 3\n" );
  }

  @Test
  void testSortOnMeasureWithShowRowsColumnsWithMeasureData(Context<?> context) {
      assertThatQuery(context.getConnectionWithDefaultRole(), "WITH\n"
      + "SET [*NATIVE_CJ_SET_WITH_SLICER] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Education Level_],NONEMPTYCROSSJOIN"
      + "([*BASE_MEMBERS__Product_],NONEMPTYCROSSJOIN([*BASE_MEMBERS__Yearly Income_],NONEMPTYCROSSJOIN"
      + "([*BASE_MEMBERS__Store_],[*BASE_MEMBERS__Store Type_]))))'\n"
      + "SET [*NATIVE_CJ_SET] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Education Level].[Education Level].CURRENTMEMBER,[Product].[Product]"
      + ".CURRENTMEMBER,[Yearly Income].[Yearly Income].CURRENTMEMBER,[Store].[Store].CURRENTMEMBER)})'\n"
      + "SET [*BASE_MEMBERS__Store Type_] AS '{[Store Type].[Store Type].[All Store Types].[HeadQuarters],[Store Type].[Store Type].[All Store "
      + "Types].[Mid-Size Grocery],[Store Type].[Store Type].[All Store Types].[Small Grocery]}'\n"
      + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Education Level].[Education Level].CURRENTMEMBER.ORDERKEY,BASC,[Product].[Product]"
      + ".CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].[Product].CURRENTMEMBER,[Product].[Product].[Product Family]).ORDERKEY,BASC,"
      + "[Yearly Income].[Yearly Income].CURRENTMEMBER.ORDERKEY,BASC,[Measures].[*SORTED_MEASURE],BASC)'\n"
      + "SET [*BASE_MEMBERS__Education Level_] AS '[Education Level].[Education Level].[Education Level].MEMBERS'\n"
      + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
      + "SET [*BASE_MEMBERS__Store_] AS '[Store].[Store].[Store Country].MEMBERS'\n"
      + "SET [*BASE_MEMBERS__Yearly Income_] AS '[Yearly Income].[Yearly Income].[Yearly Income].MEMBERS'\n"
      + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Store Type].[Store Type].CURRENTMEMBER)})'\n"
      + "SET [*BASE_MEMBERS__Product_] AS '[Product].[Product].[Product Department].MEMBERS'\n"
      + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Education Level].[Education Level].CURRENTMEMBER,[Product].[Product].CURRENTMEMBER,"
      + "[Yearly Income].[Yearly Income].CURRENTMEMBER,[Store].[Store].CURRENTMEMBER)})'\n"
      + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
      + "SOLVE_ORDER=500\n"
      + "MEMBER [Measures].[*SORTED_MEASURE] AS '([Measures].[*FORMATTED_MEASURE_0])', SOLVE_ORDER=400\n"
      + "SELECT\n"
      + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
      + ", NON EMPTY\n"
      + "{HEAD([*SORTED_ROW_AXIS],5), TAIL([*SORTED_ROW_AXIS],5)} ON ROWS\n"
      + "FROM [Sales]\n"
      + "WHERE ([*CJ_SLICER_AXIS])\n").returnsGrid("Axis #0:\n"
      + "{[Store Type].[Store Type].[Mid-Size Grocery]}\n"
      + "{[Store Type].[Store Type].[Small Grocery]}\n"
      + "Axis #1:\n"
      + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
      + "Axis #2:\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$110K - "
      + "$130K], [Store].[Store].[USA]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$130K - "
      + "$150K], [Store].[Store].[USA]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$150K +], "
      + "[Store].[Store].[USA]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$50K - "
      + "$70K], [Store].[Store].[USA]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$70K - "
      + "$90K], [Store].[Store].[USA]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$130K - $150K],"
      + " [Store].[Store].[USA]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$150K +], "
      + "[Store].[Store].[USA]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$30K - $50K], "
      + "[Store].[Store].[USA]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$50K - $70K], "
      + "[Store].[Store].[USA]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$90K - $110K], "
      + "[Store].[Store].[USA]}\n"
      + "Row #0: 15\n"
      + "Row #1: 8\n"
      + "Row #2: 13\n"
      + "Row #3: 75\n"
      + "Row #4: 43\n"
      + "Row #5: 5\n"
      + "Row #6: 4\n"
      + "Row #7: 9\n"
      + "Row #8: 2\n"
      + "Row #9: 3\n" );
  }

  @Test
  void testSortOnAttributesWithShowsRowsColumnsWithMeasureAndCalculatedMeasureData(Context<?> context) {
      assertThatQuery(context.getConnectionWithDefaultRole(), "WITH\n"
      + "SET [*NATIVE_CJ_SET_WITH_SLICER] AS '[*BASE_MEMBERS__Store Type_]'\n"
      + "SET [*NATIVE_CJ_SET] AS 'CROSSJOIN([*BASE_MEMBERS__Education Level_],CROSSJOIN([*BASE_MEMBERS__Product_],"
      + "[*BASE_MEMBERS__Yearly Income_]))'\n"
      + "SET [*BASE_MEMBERS__Store Type_] AS '{[Store Type].[Store Type].[All Store Types].[HeadQuarters],[Store Type].[Store Type].[All Store "
      + "Types].[Mid-Size Grocery],[Store Type].[Store Type].[All Store Types].[Small Grocery]}'\n"
      + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Education Level].[Education Level].CURRENTMEMBER.ORDERKEY,BASC,[Product].[Product]"
      + ".CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].[Product].CURRENTMEMBER,[Product].[Product].[Product Family]).ORDERKEY,BASC,"
      + "[Yearly Income].CURRENTMEMBER.ORDERKEY,BASC)'\n"
      + "SET [*BASE_MEMBERS__Education Level_] AS '[Education Level].[Education Level].[Education Level].MEMBERS'\n"
      + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
      + "SET [*BASE_MEMBERS__Yearly Income_] AS '[Yearly Income].[Yearly Income].[Yearly Income].MEMBERS'\n"
      + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Store Type].[Store Type].CURRENTMEMBER)})'\n"
      + "SET [*BASE_MEMBERS__Product_] AS '[Product].[Product].[Product Department].MEMBERS'\n"
      + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Education Level].[Education Level].CURRENTMEMBER,[Product].[Product].CURRENTMEMBER,"
      + "[Yearly Income].CURRENTMEMBER)})'\n"
      + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
      + "SOLVE_ORDER=500\n"
      + "SELECT\n"
      + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
      + ", NON EMPTY\n"
      + "{HEAD([*SORTED_ROW_AXIS],5), TAIL([*SORTED_ROW_AXIS],5)} ON ROWS\n"
      + "FROM [Sales]\n"
      + "WHERE ([*CJ_SLICER_AXIS])\n").returnsGrid("Axis #0:\n"
      + "{[Store Type].[Store Type].[HeadQuarters]}\n"
      + "{[Store Type].[Store Type].[Mid-Size Grocery]}\n"
      + "{[Store Type].[Store Type].[Small Grocery]}\n"
      + "Axis #1:\n"
      + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
      + "Axis #2:\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$110K - "
      + "$130K]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$130K - "
      + "$150K]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$150K +]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$150K +]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$30K - $50K]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$50K - $70K]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$90K - $110K]}\n"
      + "Row #0: 15\n"
      + "Row #1: 8\n"
      + "Row #2: 13\n"
      + "Row #3: 4\n"
      + "Row #4: 9\n"
      + "Row #5: 2\n"
      + "Row #6: 3\n" );
  }

  @Test
  void testSortOnMeasureWithShowsRowsColumnsWithShowAllEvenBlank(Context<?> context) {
      assertThatQuery(context.getConnectionWithDefaultRole(), "WITH\n"
      + "SET [*NATIVE_CJ_SET_WITH_SLICER] AS '[*BASE_MEMBERS__Store Type_]'\n"
      + "SET [*NATIVE_CJ_SET] AS 'CROSSJOIN([*BASE_MEMBERS__Education Level_],CROSSJOIN([*BASE_MEMBERS__Product_],"
      + "[*BASE_MEMBERS__Yearly Income_]))'\n"
      + "SET [*BASE_MEMBERS__Store Type_] AS '{[Store Type].[Store Type].[All Store Types].[HeadQuarters],[Store Type].[Store Type].[All Store "
      + "Types].[Mid-Size Grocery],[Store Type].[Store Type].[All Store Types].[Small Grocery]}'\n"
      + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Education Level].[Education Level].CURRENTMEMBER.ORDERKEY,BASC,[Product].[Product]"
      + ".CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].[Product].CURRENTMEMBER,[Product].[Product].[Product Family]).ORDERKEY,BASC,"
      + "[Measures].[*SORTED_MEASURE],BASC)'\n"
      + "SET [*BASE_MEMBERS__Education Level_] AS '[Education Level].[Education Level].[Education Level].MEMBERS'\n"
      + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
      + "SET [*BASE_MEMBERS__Yearly Income_] AS '[Yearly Income].[Yearly Income].[Yearly Income].MEMBERS'\n"
      + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Store Type].[Store Type].CURRENTMEMBER)})'\n"
      + "SET [*BASE_MEMBERS__Product_] AS '[Product].[Product].[Product Department].MEMBERS'\n"
      + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Education Level].[Education Level].CURRENTMEMBER,[Product].[Product].CURRENTMEMBER,"
      + "[Yearly Income].CURRENTMEMBER)})'\n"
      + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
      + "SOLVE_ORDER=500\n"
      + "MEMBER [Measures].[*SORTED_MEASURE] AS '([Measures].[*FORMATTED_MEASURE_0])', SOLVE_ORDER=400\n"
      + "SELECT\n"
      + "[*BASE_MEMBERS__Measures_] ON COLUMNS,\n"
      + "{HEAD([*SORTED_ROW_AXIS],5), TAIL([*SORTED_ROW_AXIS],5)} ON ROWS\n"
      + "FROM [Sales]\n"
      + "WHERE ([*CJ_SLICER_AXIS])\n").returnsGrid("Axis #0:\n"
      + "{[Store Type].[Store Type].[HeadQuarters]}\n"
      + "{[Store Type].[Store Type].[Mid-Size Grocery]}\n"
      + "{[Store Type].[Store Type].[Small Grocery]}\n"
      + "Axis #1:\n"
      + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
      + "Axis #2:\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$10K - "
      + "$30K]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$30K - "
      + "$50K]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$90K - "
      + "$110K]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$130K - "
      + "$150K]}\n"
      + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Alcoholic Beverages], [Yearly Income].[Yearly Income].[$150K +]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$150K +]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$130K - $150K]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$30K - $50K]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$110K - $130K]}\n"
      + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Starchy Foods], [Yearly Income].[Yearly Income].[$10K - $30K]}\n"
      + "Row #0: \n"
      + "Row #1: \n"
      + "Row #2: \n"
      + "Row #3: 8\n"
      + "Row #4: 13\n"
      + "Row #5: 4\n"
      + "Row #6: 5\n"
      + "Row #7: 9\n"
      + "Row #8: 10\n"
      + "Row #9: 68\n" );
  }

}
