/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2020 Hitachi Vantara and others
 * Copyright (C) 2022 Sergei Semenkov
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
package org.eclipse.daanse.olap.fun;

import static org.eclipse.daanse.olap.function.TestResources.hiersExcept;
import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.function.FunctionService;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.calc.base.profile.SimpleCalculationProfileWriter;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.Util;
import  org.eclipse.daanse.olap.util.Bug;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.FunDependencies;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.daanse.rolap.SchemaModifiersEmf;

//import mondrian.spi.DialectManager;

/**
 * <code>FunctionTest</code> tests the functions defined in
 * {@link BuiltinFunTable}.
 *
 * @author gjohnson
 */
@RolapContextTest(FoodmartTestInstance.class)
public class FunctionTest {

  private static final Logger LOGGER = LoggerFactory.getLogger( FunctionTest.class );
  private static final int NUM_EXPECTED_FUNCTIONS = 301;

  private static final String TimeWeekly = "[Time].[Weekly]";



  @BeforeEach
  public void beforeEach() {

  }

  @AfterEach
  public void afterEach() {
  }


  /**
   * Tests that Integeer.MIN_VALUE(-2147483648) in Lag is handled correctly.
   */
  @Test
  void testLagMinValue(Context<?> context) {
    // By running the query and getting a result without an exception, we should assert the return value which will
    // have empty rows, because the lag value is too large for the traversal it needs to make, so rows will be empty
    // data, but it will still return a result.
    String query = "with "
      + "member [measures].[foo] as "
      + "'([Measures].[unit sales], [Time].[1997].[Q1].Lag(-2147483648))' "
      + "select "
      + "[measures].[foo] on columns, "
      + "[time].[1997].children on rows "
      + "from [sales]";
    String expected = "Axis #0:\n"
      + "{}\n"
      + "Axis #1:\n"
      + "{[Measures].[foo]}\n"
      + "Axis #2:\n"
      + "{[Time].[Time].[1997].[Q1]}\n"
      + "{[Time].[Time].[1997].[Q2]}\n"
      + "{[Time].[Time].[1997].[Q3]}\n"
      + "{[Time].[Time].[1997].[Q4]}\n"
      + "Row #0: \n"
      + "Row #1: \n"
      + "Row #2: \n"
      + "Row #3: \n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expected );
  }


  @Test
  void testNumericLiteral(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "2").returns( "2" );
    if ( false ) {
      // The test is currently broken because the value 2.5 is formatted
      // as "2". TODO: better default format string
      assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "2.5").returns( "2.5" );
    }
     assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-10.0").returns( "-10" );
    FunDependencies.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1.5").dependsOn();
  }

  @Test
  void testStringLiteral(Context<?> context) {
    // single-quoted string
    if ( false ) {
      // TODO: enhance parser so that you can include a quoted string
      //   inside a WITH MEMBER clause
      assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "'foobar'").returns( "foobar" );
    }
    // double-quoted string
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "\"foobar\"").returns( "foobar" );
    // literals don't depend on any dimensions
    FunDependencies.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "\"foobar\"").dependsOn();
  }




  @Test
  void testNullMember(Context<?> context) {
    // MSAS fails here, but Mondrian doesn't.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "[Gender].[All Gender].Parent.Level.UniqueName").returns(
      "[Gender].[Gender].[(All)]" );

    // MSAS fails here, but Mondrian doesn't.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "[Gender].[All Gender].Parent.Hierarchy.UniqueName").returns( "[Gender].[Gender]" );

    // MSAS fails here, but Mondrian doesn't.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "[Gender].[All Gender].Parent.Dimension.UniqueName").returns( "[Gender]" );

    // MSAS succeeds too
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "[Gender].[All Gender].Parent.Children.Count").returns( "0" );

    // MSAS returns "" here.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "[Gender].[All Gender].Parent.UniqueName").returns( "[Gender].[Gender].[#null]" );

    // MSAS returns "" here.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "[Gender].[All Gender].Parent.Name").returns( "#null" );
  }

  /**
   * Tests use of NULL literal to generate a null cell value. Testcase is from bug 1440344.
   */
  @Test
  void testNullValue(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member [Measures].[X] as 'IIF([Measures].[Store Sales]>10000,[Measures].[Store Sales],Null)'\n"
        + "select\n"
        + "{[Measures].[X]} on columns,\n"
        + "{[Product].[Product Department].members} on rows\n"
        + "from Sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[X]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Drink].[Alcoholic Beverages]}\n"
        + "{[Product].[Product].[Drink].[Beverages]}\n"
        + "{[Product].[Product].[Drink].[Dairy]}\n"
        + "{[Product].[Product].[Food].[Baked Goods]}\n"
        + "{[Product].[Product].[Food].[Baking Goods]}\n"
        + "{[Product].[Product].[Food].[Breakfast Foods]}\n"
        + "{[Product].[Product].[Food].[Canned Foods]}\n"
        + "{[Product].[Product].[Food].[Canned Products]}\n"
        + "{[Product].[Product].[Food].[Dairy]}\n"
        + "{[Product].[Product].[Food].[Deli]}\n"
        + "{[Product].[Product].[Food].[Eggs]}\n"
        + "{[Product].[Product].[Food].[Frozen Foods]}\n"
        + "{[Product].[Product].[Food].[Meat]}\n"
        + "{[Product].[Product].[Food].[Produce]}\n"
        + "{[Product].[Product].[Food].[Seafood]}\n"
        + "{[Product].[Product].[Food].[Snack Foods]}\n"
        + "{[Product].[Product].[Food].[Snacks]}\n"
        + "{[Product].[Product].[Food].[Starchy Foods]}\n"
        + "{[Product].[Product].[Non-Consumable].[Carousel]}\n"
        + "{[Product].[Product].[Non-Consumable].[Checkout]}\n"
        + "{[Product].[Product].[Non-Consumable].[Health and Hygiene]}\n"
        + "{[Product].[Product].[Non-Consumable].[Household]}\n"
        + "{[Product].[Product].[Non-Consumable].[Periodicals]}\n"
        + "Row #0: 14,029.08\n"
        + "Row #1: 27,748.53\n"
        + "Row #2: \n"
        + "Row #3: 16,455.43\n"
        + "Row #4: 38,670.41\n"
        + "Row #5: \n"
        + "Row #6: 39,774.34\n"
        + "Row #7: \n"
        + "Row #8: 30,508.85\n"
        + "Row #9: 25,318.93\n"
        + "Row #10: \n"
        + "Row #11: 55,207.50\n"
        + "Row #12: \n"
        + "Row #13: 82,248.42\n"
        + "Row #14: \n"
        + "Row #15: 67,609.82\n"
        + "Row #16: 14,550.05\n"
        + "Row #17: 11,756.07\n"
        + "Row #18: \n"
        + "Row #19: \n"
        + "Row #20: 32,571.86\n"
        + "Row #21: 60,469.89\n"
        + "Row #22: \n" );
  }

  @Test
  void testNullInMultiplication(Context<?> context) {
    Connection connection = context.getConnectionWithDefaultRole();
    assertThatExpr(connection, "Sales", "NULL*1").returns( "" );
    assertThatExpr(connection, "Sales", "1*NULL").returns( "" );
    assertThatExpr(connection, "Sales", "NULL*NULL").returns( "" );
  }

  @Test
  void testNullInAddition(Context<?> context) {
    Connection connection = context.getConnectionWithDefaultRole();
    assertThatExpr(connection, "Sales", "1+NULL").returns( "1" );
    assertThatExpr(connection, "Sales", "NULL+1").returns( "1" );
  }

  @Test
  void testNullInSubtraction(Context<?> context) {
    Connection connection = context.getConnectionWithDefaultRole();
    assertThatExpr(connection, "Sales", "1-NULL").returns( "1" );
    assertThatExpr(connection, "Sales", "NULL-1").returns( "-1" );
  }

  @Disabled //TODO need investigate
  @Test
  void testIsEmptyQuery(Context<?> context) {
    String desiredResult =
      "Axis #0:\n"
        + "{[Time].[1997].[Q4].[12], [Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Portsmouth]"
        + ".[Portsmouth Imported Beer], [Measures].[Foo]}\n"
        + "Axis #1:\n"
        + "{[Store].[USA].[WA].[Bellingham]}\n"
        + "{[Store].[USA].[WA].[Bremerton]}\n"
        + "{[Store].[USA].[WA].[Seattle]}\n"
        + "{[Store].[USA].[WA].[Spokane]}\n"
        + "{[Store].[USA].[WA].[Tacoma]}\n"
        + "{[Store].[USA].[WA].[Walla Walla]}\n"
        + "{[Store].[USA].[WA].[Yakima]}\n"
        + "Row #0: 5\n"
        + "Row #0: 5\n"
        + "Row #0: 2\n"
        + "Row #0: 5\n"
        + "Row #0: 11\n"
        + "Row #0: 5\n"
        + "Row #0: 4\n";

    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH MEMBER [Measures].[Foo] AS 'Iif(IsEmpty([Measures].[Unit Sales]), 5, [Measures].[Unit Sales])'\n"
        + "SELECT {[Store].[USA].[WA].children} on columns\n"
        + "FROM Sales\n"
        + "WHERE ([Time].[1997].[Q4].[12],\n"
        + " [Product].[All Products].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Portsmouth].[Portsmouth "
        + "Imported Beer],\n"
        + " [Measures].[Foo])").returnsGrid(
      desiredResult );

    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH MEMBER [Measures].[Foo] AS 'Iif([Measures].[Unit Sales] IS EMPTY, 5, [Measures].[Unit Sales])'\n"
        + "SELECT {[Store].[USA].[WA].children} on columns\n"
        + "FROM Sales\n"
        + "WHERE ([Time].[1997].[Q4].[12],\n"
        + " [Product].[All Products].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Portsmouth].[Portsmouth "
        + "Imported Beer],\n"
        + " [Measures].[Foo])").returnsGrid(
      desiredResult );

    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH MEMBER [Measures].[Foo] AS 'Iif([Measures].[Bar] IS EMPTY, 1, [Measures].[Bar])'\n"
        + "MEMBER [Measures].[Bar] AS 'CAST(\"42\" AS INTEGER)'\n"
        + "SELECT {[Measures].[Unit Sales], [Measures].[Foo]} on columns\n"
        + "FROM Sales\n"
        + "WHERE ([Time].[1998].[Q4].[12])").returnsGrid(
      "Axis #0:\n"
        + "{[Time].[1998].[Q4].[12]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "{[Measures].[Foo]}\n"
        + "Row #0: \n"
        + "Row #0: 42\n" );
  }

  @Test
  void testQueryWithoutValidMeasure(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with\n"
        + "member measures.[without VM] as ' [measures].[unit sales] '\n"
        + "select {measures.[without VM] } on 0,\n"
        + "[Warehouse].[Country].members on 1 from [warehouse and sales]\n").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[without VM]}\n"
        + "Axis #2:\n"
        + "{[Warehouse].[Warehouse].[Canada]}\n"
        + "{[Warehouse].[Warehouse].[Mexico]}\n"
        + "{[Warehouse].[Warehouse].[USA]}\n"
        + "Row #0: \n"
        + "Row #1: \n"
        + "Row #2: \n" );
  }

  /**
   * Tests behavior where CurrentMember occurs in calculated members and that member is a set.
   *
   * <p>Mosha discusses this behavior in the article
   * <a href="http://www.mosha.com/msolap/articles/mdxmultiselectcalcs.htm">
   * Multiselect friendly MDX calculations</a>.
   *
   * <p>Mondrian's behavior is consistent with MSAS 2K: it returns zeroes.
   * SSAS 2005 returns an error, which can be fixed by reformulating the calculated members.
   *
   */
  //* @see mondrian.rolap.FastBatchingCellReaderTest#testAggregateDistinctCount()
  @Test
  void testMultiselectCalculations(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH\n"
        + "MEMBER [Measures].[Declining Stores Count] AS\n"
        + " ' Count(Filter(Descendants(Store.CurrentMember, Store.[Store Name]), [Store Sales] < ([Store Sales],Time"
        + ".Time.PrevMember))) '\n"
        + " MEMBER \n"
        + "  [Store].[XL_QZX] AS 'Aggregate ({ [Store].[All Stores].[USA].[WA] , [Store].[All Stores].[USA].[CA] })' \n"
        + "SELECT \n"
        + "  NON EMPTY HIERARCHIZE(AddCalculatedMembers({DrillDownLevel({[Product].[All Products]})})) \n"
        + "    DIMENSION PROPERTIES PARENT_UNIQUE_NAME ON COLUMNS \n"
        + "FROM [Sales] \n"
        + "WHERE ([Measures].[Declining Stores Count], [Time].[1998].[Q3], [Store].[XL_QZX])").returnsGrid(
      "Axis #0:\n"
        + "{[Measures].[Declining Stores Count], [Time].[Time].[1998].[Q3], [Store].[Store].[XL_QZX]}\n"
        + "Axis #1:\n"
        + "{[Product].[Product].[All Products]}\n"
        + "{[Product].[Product].[Drink]}\n"
        + "{[Product].[Product].[Food]}\n"
        + "{[Product].[Product].[Non-Consumable]}\n"
        + "Row #0: .00\n"
        + "Row #0: .00\n"
        + "Row #0: .00\n"
        + "Row #0: .00\n" );
  }

  @Test
  void testBug715177(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH MEMBER [Product].[Non-Consumable].[Other] AS\n"
        + " 'Sum(Except( [Product].[Product Department].Members,\n"
        + "       TopCount([Product].[Product Department].Members, 3)),\n"
        + "       Measures.[Unit Sales])'\n"
        + "SELECT\n"
        + "  { [Measures].[Unit Sales] } ON COLUMNS,\n"
        + "  { TopCount([Product].[Product Department].Members,3),\n"
        + "              [Product].[Non-Consumable].[Other] } ON ROWS\n"
        + "FROM [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Drink].[Alcoholic Beverages]}\n"
        + "{[Product].[Product].[Drink].[Beverages]}\n"
        + "{[Product].[Product].[Drink].[Dairy]}\n"
        + "{[Product].[Product].[Non-Consumable].[Other]}\n"
        + "Row #0: 6,838\n"
        + "Row #1: 13,573\n"
        + "Row #2: 4,186\n"
        + "Row #3: 242,176\n" );
  }

  @Test
  void testBug714707(Context<?> context) {
    // Same issue as bug 715177 -- "children" returns immutable
    // list, which set operator must make mutable.
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "{[Store].[USA].[CA].children, [Store].[USA]}").returns(
      "[Store].[Store].[USA].[CA].[Alameda]\n"
        + "[Store].[Store].[USA].[CA].[Beverly Hills]\n"
        + "[Store].[Store].[USA].[CA].[Los Angeles]\n"
        + "[Store].[Store].[USA].[CA].[San Diego]\n"
        + "[Store].[Store].[USA].[CA].[San Francisco]\n"
        + "[Store].[Store].[USA]" );
  }

  @Test
	void testTuple(Context<?> context) {
		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
				"([Gender].[M], " + "[Time].[Time].Children.Item(2), " + "[Measures].[Unit Sales])").returns( "33,249");
		// Calc calls MemberValue with 3 args -- more efficient than
		// constructing a tuple.
		String expr = "([Gender].[M], [Time].[Time].Children.Item(2), [Measures].[Unit Sales])";
		String expectedCalc = """
org.eclipse.daanse.olap.calc.base.type.tuplebase.MemberArrayValueCalc(type=SCALAR, resultStyle=VALUE, callCount=0, callMillis=0)
    org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Gender].[Gender].[M]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
    org.eclipse.daanse.olap.function.def.set.setitem.SetItemFunDef$3(type=MemberType<hierarchy=[Time].[Time]>, resultStyle=VALUE, callCount=0, callMillis=0)
        org.eclipse.daanse.olap.function.def.set.children.ChildrenCalc(type=SetType<MemberType<hierarchy=[Time].[Time]>>, resultStyle=LIST, callCount=0, callMillis=0)
            org.eclipse.daanse.olap.function.def.hierarchy.member.HierarchyCurrentMemberFixedCalc(type=MemberType<hierarchy=[Time].[Time]>, resultStyle=VALUE, callCount=0, callMillis=0)
        org.eclipse.daanse.olap.calc.base.constant.ConstantIntegerCalc(type=DecimalType(0), resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
    org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
							""";
		assertExprCompilesTo(context.getConnectionWithDefaultRole(), expr, expectedCalc);
  }

  /**
   * Tests whether the tuple operator can be applied to arguments of various types. See bug 1491699 "ClassCastException
   * in mondrian.calc.impl.GenericCalc.evaluat".
   */
  @Test
  void testTupleArgTypes(Context<?> context) {
    // can coerce dimensions (if they have a unique hierarchy) and
    // hierarchies to members
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "([Gender], [Time].[Time])").returns(
      "266,773" );

    // can coerce hierarchy to member
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "([Gender].[M], " + TimeWeekly + ")").returns( "135,215" );

    // coerce args (hierarchy, member, member, dimension)
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "{([Time].[Weekly], [Measures].[Store Sales], [Marital Status].[M], [Promotion Media])}").returns(
      "{[Time].[Weekly].[All Weeklys], [Measures].[Store Sales], [Marital Status].[Marital Status].[M], [Promotion Media].[Promotion Media].[All "
        + "Media]}" );

    // usage of different hierarchies in the [Time] dimension
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "{([Time].[Weekly], [Measures].[Store Sales], [Marital Status].[M], [Time].[Time])}").returns(
      "{[Time].[Weekly].[All Weeklys], [Measures].[Store Sales], [Marital Status].[Marital Status].[M], [Time].[Time].[1997]}" );

    // two usages of the [Time].[Weekly] hierarchy

    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "{([Time].[Weekly], [Measures].[Store Sales], [Marital Status].[M], [Time].[Weekly])}").throwsMessage(
      "Tuple contains more than one member of hierarchy '[Time].[Weekly]'." );

    // cannot coerce integer to member
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "{([Gender].[M], 123)}").throwsMessage(
      "No function matches signature '(<Member>, <Numeric Expression>)'" );
  }

  @Test
  void testTupleItem(Context<?> context) {
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "([Time].[1997].[Q1].[1], [Customers].[All Customers].[USA].[OR], [Gender].[All Gender].[M]).item(2)").returns(
      "[Gender].[Gender].[M]" );

    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "([Time].[1997].[Q1].[1], [Customers].[All Customers].[USA].[OR], [Gender].[All Gender].[M]).item(1)").returns(
      "[Customers].[Customers].[USA].[OR]" );

    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "{[Time].[1997].[Q1].[1]}.item(0)").returns(
      "[Time].[Time].[1997].[Q1].[1]" );

    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "{[Time].[1997].[Q1].[1]}.Item(0).Item(0)").returns(
      "[Time].[Time].[1997].[Q1].[1]" );

    // given out of bounds index, item returns null
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "([Time].[1997].[Q1].[1], [Customers].[All Customers].[USA].[OR], [Gender].[All Gender].[M]).item(-1)").returns(
      "" );

    // given out of bounds index, item returns null
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "([Time].[1997].[Q1].[1], [Customers].[All Customers].[USA].[OR], [Gender].[All Gender].[M]).item(500)").returns(
      "" );

    // empty set
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "Filter([Gender].members, 1 = 0).Item(0)").returns(
      "" );

    // empty set of unknown type
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "{}.Item(3)").returns(
      "" );

    // past end of set
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "{[Gender].members}.Item(4)").returns(
      "" );

    // negative index
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "{[Gender].members}.Item(-50)").returns(
      "" );
  }

  @Test
  void testTupleAppliedToUnknownHierarchy(Context<?> context) {
    // manifestation of bug 1735821
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with \n"
        + "member [Product].[Test] as '([Product].[Food],Dimensions(0).defaultMember)' \n"
        + "select \n"
        + "{[Product].[Test], [Product].[Food]} on columns, \n"
        + "{[Measures].[Store Sales]} on rows \n"
        + "from Sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Product].[Product].[Test]}\n"
        + "{[Product].[Product].[Food]}\n"
        + "Axis #2:\n"
        + "{[Measures].[Store Sales]}\n"
        + "Row #0: 191,940.00\n"
        + "Row #0: 409,035.59\n" );
  }

  @Test
  void testTupleDepends(Context<?> context) {
    FunDependencies.assertThatMemberExpr(context.getConnectionWithDefaultRole(), "Sales",
      "([Store].[USA], [Gender].[F])").dependsOn();

    FunDependencies.assertThatMemberExpr(context.getConnectionWithDefaultRole(), "Sales",
      "([Store].[USA], [Gender])").dependsOn("[Gender].[Gender]");

    // in a scalar context, the expression depends on everything except
    // the explicitly stated dimensions
    FunDependencies.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "([Store].[USA], [Gender])").dependsOn( hiersExcept( "[Store].[Store]" ) );

    // The result should be all dims except [Gender], but there's a small
    // bug in MemberValueCalc.dependsOn where we escalate 'might depend' to
    // 'depends' and we return that it depends on all dimensions.
    FunDependencies.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "(Dimensions('Store').CurrentMember, [Gender].[F])").dependsOn( hiersExcept() );
  }

  @Test
  void testItemNull(Context<?> context) {
    // In the following queries, MSAS returns 'Formula error - object type
    // is not valid - in an <object> base class. An error occurred during
    // attempt to get cell value'. This is because in MSAS, Item is a COM
    // function, and COM doesn't like null pointers.
    //
    // Mondrian represents null members as actual objects, so its behavior
    // is different.

    // MSAS returns error here.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "Filter([Gender].members, 1 = 0).Item(0).Dimension.Name").returns(
      "Gender" );

    // MSAS returns error here.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "Filter([Gender].members, 1 = 0).Item(0).Parent").returns(
      "" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "(Filter([Store].members, 0 = 0).Item(0).Item(0),"
        + "Filter([Store].members, 0 = 0).Item(0).Item(0))").returns(
      "266,773" );

    // MSAS returns error here.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "Filter([Gender].members, 1 = 0).Item(0).Name").returns(
      "#null" );
  }

  @Test
  void testTupleNull(Context<?> context) {
    // if a tuple contains any null members, it evaluates to null
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select {[Measures].[Unit Sales]} on columns,\n"
        + " { ([Gender].[M], [Store]),\n"
        + "   ([Gender].[F], [Store].parent),\n"
        + "   ([Gender].parent, [Store])} on rows\n"
        + "from [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Gender].[Gender].[M], [Store].[Store].[All Stores]}\n"
        + "Row #0: 135,215\n" );

    // the set function eliminates tuples which are wholly or partially
    // null
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "([Gender].parent, [Marital Status]),\n" // part null
        + " ([Gender].[M], [Marital Status].parent),\n" // part null
        + " ([Gender].parent, [Marital Status].parent),\n" // wholly null
        + " ([Gender].[M], [Marital Status])").returns( // not null
      "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[All Marital Status]}" );

    // The tuple constructor returns a null tuple if one of its
    // arguments is null -- and the Item function returns null if the
    // tuple is null.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "([Gender].parent, [Marital Status]).Item(0).Name").returns(
      "#null" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "([Gender].parent, [Marital Status]).Item(1).Name").returns(
      "#null" );
  }

  @Test
  void testLevelMemberExpressions(Context<?> context) {
	context.getCatalogCache().clear();
    // Should return Beverly Hills in California.
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "[Store].[Store City].[Beverly Hills]").returns(
      "[Store].[Store].[USA].[CA].[Beverly Hills]" );

    // There are two months named "1" in the time dimension: one
    // for 1997 and one for 1998.  <Level>.<Member> should return
    // the first one.
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Time].[Month].[1]").returns( "[Time].[Time].[1997].[Q1].[1]" );

    // Shouldn't be able to find a member named "Q1" on the month level.
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "[Time].[Month].[Q1]").throwsMessage(
      "MDX object '[Time].[Month].[Q1]' not found in cube");
  }

  @Test
  void testCaseTestMatch(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "CASE WHEN 1=0 THEN \"first\" WHEN 1=1 THEN \"second\" WHEN 1=2 THEN \"third\" ELSE \"fourth\" END").returns(
      "second" );
  }

  @Test
  void testCaseTestMatchElse(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "CASE WHEN 1=0 THEN \"first\" ELSE \"fourth\" END").returns(
      "fourth" );
  }

  @Test
  void testCaseTestMatchNoElse(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "CASE WHEN 1=0 THEN \"first\" END").returns(
      "" );
  }

  /**
   * Testcase for bug 1799391, "Case Test function throws class cast exception"
   */
  @Test
  void testCaseTestReturnsMemberBug1799391(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH\n"
        + " MEMBER [Product].[CaseTest] AS\n"
        + " 'CASE\n"
        + " WHEN [Gender].CurrentMember IS [Gender].[M] THEN [Gender].[F]\n"
        + " ELSE [Gender].[F]\n"
        + " END'\n"
        + "                \n"
        + "SELECT {[Product].[CaseTest]} ON 0, {[Gender].[M]} ON 1 FROM Sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Product].[Product].[CaseTest]}\n"
        + "Axis #2:\n"
        + "{[Gender].[Gender].[M]}\n"
        + "Row #0: 131,558\n" );

    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "CASE WHEN 1+1 = 2 THEN [Gender].[F] ELSE [Gender].[F].Parent END").returns(
      "[Gender].[Gender].[F]" );

    // try case match for good measure
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
      "CASE 1 WHEN 2 THEN [Gender].[F] ELSE [Gender].[F].Parent END").returns(
      "[Gender].[Gender].[All Gender]" );
  }

  @Test
  void testCaseMatch(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "CASE 2 WHEN 1 THEN \"first\" WHEN 2 THEN \"second\" WHEN 3 THEN \"third\" ELSE \"fourth\" END").returns(
      "second" );
  }

  @Test
  void testCaseMatchElse(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "CASE 7 WHEN 1 THEN \"first\" ELSE \"fourth\" END").returns(
      "fourth" );
  }

  @Test
  void testCaseMatchNoElse(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "CASE 8 WHEN 0 THEN \"first\" END").returns(
      "" );
  }

  @Test
  void testCaseTypeMismatch(Context<?> context) {
    // type mismatch between case and else
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales" ,
      "CASE 1 WHEN 1 THEN 2 ELSE \"foo\" END").throwsMessage(
      "No function matches signature");
    // type mismatch between case and case
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales" ,
      "CASE 1 WHEN 1 THEN 2 WHEN 2 THEN \"foo\" ELSE 3 END").throwsMessage(
      "No function matches signature");
    // type mismatch between value and case
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales" ,
      "CASE 1 WHEN \"foo\" THEN 2 ELSE 3 END").throwsMessage(
      "No function matches signature");
    // non-boolean condition
    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales" ,
      "CASE WHEN 1 = 2 THEN 3 WHEN 4 THEN 5 ELSE 6 END").throwsMessage(
      "No function matches signature");
  }

  /**
   * Testcase for
   * <a href="http://jira.pentaho.com/browse/MONDRIAN-853">
   * bug MONDRIAN-853, "When using CASE WHEN in a CalculatedMember values are not returned the way expected"</a>.
   */
  @Test
  void testCaseTuple(Context<?> context) {
    // The case in the bug, simplified. With the bug, returns a member array
    // "[Lmondrian.olap.Member;@151b0a5". Type deduction should realize
    // that the result is a scalar, therefore a tuple (represented by a
    // member array) needs to be evaluated to a scalar. I think that if we
    // get the type deduction right, the MDX exp compiler will handle the
    // rest.
    if ( false ) {
      assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
        "case 1 when 0 then 1.5\n"
          + " else ([Gender].[M], [Measures].[Unit Sales]) end").returns(
        "135,215" );
    }

    // "case when" variant always worked
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "case when 1=0 then 1.5\n"
        + " else ([Gender].[M], [Measures].[Unit Sales]) end").returns(
      "135,215" );

    // case 2: cannot deduce type (tuple x) vs. (tuple y). Should be able
    // to deduce that the result type is tuple-type<member-type<Gender>,
    // member-type<Measures>>.
    if ( false ) {
      assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
        "case when 1=0 then ([Gender].[M], [Measures].[Store Sales])\n"
          + " else ([Gender].[M], [Measures].[Unit Sales]) end").returns(
        "xxx" );
    }

    // case 3: mixture of member & tuple. Should be able to deduce that
    // result type is an expression.
    if ( false ) {
      assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
        "case when 1=0 then ([Measures].[Store Sales])\n"
          + " else ([Gender].[M], [Measures].[Unit Sales]) end").returns(
        "xxx" );
    }
  }

  @Test
  void testMod(Context<?> context) {
    // the following tests are consistent with excel xp

    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "mod(11, 3)").returns( "2" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "mod(-12, 3)").returns( "0" );

    // can handle non-ints, using the formula MOD(n, d) = n - d * INT(n / d)
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "mod(7.2, 3)").returns( 1.2, 0.0001 );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "mod(7.2, 3.2)").returns( .8, 0.0001 );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "mod(7.2, -3.2)").returns( -2.4, 0.0001 );

    // per Excel doc "sign of result is same as divisor"
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "mod(3, 2)").returns( "1" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "mod(-3, 2)").returns( "1" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "mod(3, -2)").returns( "-1" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "mod(-3, -2)").returns( "-1" );

    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "mod(4, 0)").throwsMessage(
      "java.lang.ArithmeticException: / by zero" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "mod(0, 0)").throwsMessage(
      "java.lang.ArithmeticException: / by zero" );
  }


  @Test
  void testString(Context<?> context) {
    // The String(Integer,Char) function requires us to implicitly cast a
    // string to a char.
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member measures.x as 'String(3, \"yahoo\")'\n"
        + "select measures.x on 0 from [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[x]}\n"
        + "Row #0: yyy\n" );
    // String is converted to char by taking first character
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "String(3, \"yahoo\")").returns( "yyy" ); // SSAS agrees
    // Integer is converted to char by converting to string and taking first
    // character
    if ( Bug.Ssas2005Compatible ) {
      // SSAS2005 can implicitly convert an integer (32) to a string, and
      // then to a char by taking the first character. Mondrian requires
      // an explicit cast.
      assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "String(3, 32)").returns( "333" );
      assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "String(8, -5)").returns( "--------" );
    } else {
      assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "String(3, Cast(32 as string))").returns( "333" );
      assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "String(8, Cast(-5 as string))").returns( "--------" );
    }
    // Error if length<0
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "String(0, 'x')").returns( "" ); // SSAS agrees
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "String(-1, 'x')").throwsMessage( "NegativeArraySizeException" ); // SSAS agrees
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "String(-200, 'x')").throwsMessage( "NegativeArraySizeException" ); // SSAS agrees
  }

  /**
   * Compiles a scalar expression, and asserts that the program looks as expected.
   */
  void assertExprCompilesTo(Connection connection,
    String expr,
    String expectedCalc ) {
    Query query = connection.parseQuery(
        "with member [Measures].[Foo] as " + Util.singleQuoteString(expr)
            + " select {[Measures].[Foo]} on columns from Sales");
    Calc calc = query.compileExpression(query.getFormulas()[0].getExpression(), true, null);
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    new SimpleCalculationProfileWriter(pw).write(calc.getCalculationProfile());
    pw.flush();
    final String actualCalc = sw.toString();
    final int expDeps =
      connection.getContext().getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class);
    if ( expDeps > 0 ) {
      // Don't bother checking the compiled output if we are also
      // testing dependencies. The compiled code will have extra
      // 'DependencyTestingCalc' instances embedded in it.
      return;
    }
    assertEquals(stubAnonymousClasses(expectedCalc), stubAnonymousClasses(actualCalc));
  }

  @Test
  void testCast(Context<?> context) {
    // NOTE: Some of these tests fail with 'cannot convert ...', and they
    // probably shouldn't. Feel free to fix the conversion.
    // -- jhyde, 2006/9/3

    // From double to integer.  MONDRIAN-1631
    Cell cell = executeQuery(context.getConnectionWithDefaultRole(),
        "with member [Measures].[Foo] as " + Util.singleQuoteString("Cast(1.4 As Integer)")
        + " select {[Measures].[Foo]} on columns from Sales")
        .getCell(new int[] { 0 });
    assertEquals(Integer.class, cell.getValue().getClass(),
            "Cast to Integer resulted in wrong datatype\n"
                    + cell.getValue().getClass().toString());
    assertEquals(1, cell.getValue() );

    // From integer
    // To integer (trivial)
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "0 + Cast(1 + 2 AS Integer)").returns( "3" );
    // To String
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "'' || Cast(1 + 2 AS String)").returns( "3.0" );
    // To Boolean
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1=1 AND Cast(1 + 2 AS Boolean)").returns( "true" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1=1 AND Cast(1 - 1 AS Boolean)").returns( "false" );


    // From boolean
    // To String
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "'' || Cast((1 = 1 AND 1 = 2) AS String)").returns( "false" );

    // This case demonstrates the relative precedence of 'AS' in 'CAST'
    // and 'AS' for creating inline named sets. See also bug MONDRIAN-648.
//    discard( Bug.Bug648Fixed );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "'xxx' || Cast(1 = 1 AND 1 = 2 AS String)").returns(
      "xxxfalse" );

    // To boolean (trivial)
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "1=1 AND Cast((1 = 1 AND 1 = 2) AS Boolean)").returns(
      "false" );

    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "1=1 OR Cast(1 = 1 AND 1 = 2 AS Boolean)").returns(
      "true" );

    // From null : should not throw exceptions since RolapResult.executeBody
    // can receive NULL values when the cell value is not loaded yet, so
    // should return null instead.
    // To Integer : Expect to return NULL

    // Expect to return NULL
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "0 * Cast(NULL AS Integer)").returns( "" );

    // To Numeric : Expect to return NULL
    // Expect to return NULL
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "0 * Cast(NULL AS Numeric)").returns( "" );

    // To String : Expect to return "null"
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "'' || Cast(NULL AS String)").returns( "null" );

    // To Boolean : Expect to return NULL, but since FunUtil.BooleanNull
    // does not implement three-valued boolean logic yet, this will return
    // false
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1=1 AND Cast(NULL AS Boolean)").returns( "false" );

    // Double is not allowed as a type
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "Cast(1 AS Double)").throwsMessage(
      "Unknown type 'Double'; values are NUMERIC, STRING, BOOLEAN" );

    // An integer constant is not allowed as a type
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "Cast(1 AS 5)").throwsMessage(
      "Encountered an error at (or somewhere around) input:1:11" );

    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Cast('tr' || 'ue' AS boolean)").returns( "true" );
  }

  @Test
  void testCastAndNull(Context<?> context) {
	    // To Boolean : Expect to return NULL, but since FunUtil.BooleanNull
	    // does not implement three-valued boolean logic yet, this will return
	    // false
	    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1=1 AND Cast(NULL AS Boolean)").returns( "false" );
  }

  @Test
  void testCastNull(Context<?> context) {
	    // To Boolean : Expect to return NULL, but since FunUtil.BooleanNull
	    // does not implement three-valued boolean logic yet, this will return
	    // false
	    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Cast(NULL AS Boolean)").returns( "false" );
  }
  /**
   * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-524"> MONDRIAN-524, "VB functions: expected
   * primitive type, got java.lang.Object"</a>.
   */
  @Test
  void testCastBug524(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "Cast(Int([Measures].[Store Sales] / 3600) as String)").returns(
      "157" );
  }

  /**
   * Tests {@link org.eclipse.daanse.olap.api.function.FunctionTable#getFunctionInfos()}, but more importantly, generates an HTML table of all
   * implemented functions into a file called "functions.html". You can manually include that table in the <a
   * href="{@docRoot}/../mdx.html">MDX specification</a>.
   */
  @Test
  void testDumpFunctions(Context<?> context) throws IOException {
    FunctionService functionService = context.getFunctionService();
    assertEquals( NUM_EXPECTED_FUNCTIONS, functionService.getResolvers().size() );

  }

  @Test
  void testLeftFunctionWithValidArguments(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "Left([Store].CURRENTMEMBER.Name, 4)=\"Bell\") on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testLeftFunctionWithLengthValueZero(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "Left([Store].CURRENTMEMBER.Name, 0)=\"\" And "
        + "[Store].CURRENTMEMBER.Name = \"Bellingham\") on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testLeftFunctionWithLengthValueEqualToStringLength(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "Left([Store].CURRENTMEMBER.Name, 10)=\"Bellingham\") "
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testLeftFunctionWithLengthMoreThanStringLength(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "Left([Store].CURRENTMEMBER.Name, 20)=\"Bellingham\") "
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testLeftFunctionWithZeroLengthString(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,Left(\"\", 20)=\"\" "
        + "And [Store].CURRENTMEMBER.Name = \"Bellingham\") "
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testLeftFunctionWithNegativeLength(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "Left([Store].CURRENTMEMBER.Name, -20)=\"Bellingham\") "
        + "on 0 from sales").throwsMessage(
      "StringIndexOutOfBoundsException: Range [0, -20) out of bounds for length 10" );
  }

  @Test
  void testMidFunctionWithValidArguments(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "[Store].CURRENTMEMBER.Name = \"Bellingham\""
        + "And Mid(\"Bellingham\", 4, 6) = \"lingha\")"
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testMidFunctionWithZeroLengthStringArgument(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "[Store].CURRENTMEMBER.Name = \"Bellingham\""
        + "And Mid(\"\", 4, 6) = \"\")"
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testMidFunctionWithLengthArgumentLargerThanStringLength(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "[Store].CURRENTMEMBER.Name = \"Bellingham\""
        + "And Mid(\"Bellingham\", 4, 20) = \"lingham\")"
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testMidFunctionWithStartIndexGreaterThanStringLength(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "[Store].CURRENTMEMBER.Name = \"Bellingham\""
        + "And Mid(\"Bellingham\", 20, 2) = \"\")"
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testMidFunctionWithStartIndexZeroFails(Context<?> context) {
    // Note: SSAS 2005 treats start<=0 as 1, therefore gives different
    // result for this query. We favor the VBA spec over SSAS 2005.
    if ( Bug.Ssas2005Compatible ) {
      assertThatQuery(context.getConnectionWithDefaultRole(),
        "select filter([Store].MEMBERS,"
          + "[Store].CURRENTMEMBER.Name = \"Bellingham\""
          + "And Mid(\"Bellingham\", 0, 2) = \"Be\")"
          + "on 0 from sales").returnsGrid(
        "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n"
          + "{[Store].[USA].[WA].[Bellingham]}\n"
          + "Row #0: 2,237\n" );
    } else {
      assertThatQuery(context.getConnectionWithDefaultRole(),
        "select filter([Store].MEMBERS,"
          + "[Store].CURRENTMEMBER.Name = \"Bellingham\""
          + "And Mid(\"Bellingham\", 0, 2) = \"Be\")"
          + "on 0 from sales").throwsMessage(
        "Invalid parameter. Start parameter of Mid function must be "
          + "positive" );
    }
  }

  @Test
  void testMidFunctionWithStartIndexOne(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "[Store].CURRENTMEMBER.Name = \"Bellingham\""
        + "And Mid(\"Bellingham\", 1, 2) = \"Be\")"
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testMidFunctionWithNegativeStartIndex(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "[Store].CURRENTMEMBER.Name = \"Bellingham\""
        + "And Mid(\"Bellingham\", -20, 2) = \"\")"
        + "on 0 from sales").throwsMessage(
      "Invalid parameter. "
        + "Start parameter of Mid function must be positive" );
  }

  @Test
  void testMidFunctionWithNegativeLength(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "[Store].CURRENTMEMBER.Name = \"Bellingham\""
        + "And Mid(\"Bellingham\", 2, -2) = \"\")"
        + "on 0 from sales").throwsMessage(
      "Invalid parameter. "
        + "Length parameter of Mid function must be non-negative" );
  }

  @Test
  void testMidFunctionWithoutLength(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,"
        + "[Store].CURRENTMEMBER.Name = \"Bellingham\""
        + "And Mid(\"Bellingham\", 2) = \"ellingham\")"
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testLenFunctionWithNonEmptyString(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS, "
        + "Len([Store].CURRENTMEMBER.Name) = 3) on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA]}\n"
        + "Row #0: 266,773\n" );
  }

  @Test
  void testLenFunctionWithAnEmptyString(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,Len(\"\")=0 "
        + "And [Store].CURRENTMEMBER.Name = \"Bellingham\") "
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testInStrFunctionWithValidArguments(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,InStr(\"Bellingham\", \"ingha\")=5 "
        + "And [Store].CURRENTMEMBER.Name = \"Bellingham\") "
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }


  @Test
  void testInStrFunctionWithEmptyString1(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,InStr(\"\", \"ingha\")=0 "
        + "And [Store].CURRENTMEMBER.Name = \"Bellingham\") "
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  @Test
  void testInStrFunctionWithEmptyString2(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select filter([Store].MEMBERS,InStr(\"Bellingham\", \"\")=1 "
        + "And [Store].CURRENTMEMBER.Name = \"Bellingham\") "
        + "on 0 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
        + "Row #0: 2,237\n" );
  }

  private static void printHtml( PrintWriter pw, String s ) {
    final String escaped = StringEscapeUtils.escapeHtml4(s);
    pw.print( escaped );
  }

  // The following methods test VBA functions. They don't test all of them,
  // because the raw methods are tested in VbaTest, but they test the core
  // functionalities like error handling and operator overloading.

  @Test
  void testVbaBasic(Context<?> context) {
    // Exp is a simple function: one arg.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "exp(0)").returns( "1" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "exp(1)").returns( Math.E, 0.00000001 );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "exp(-2)").returns( 1d / ( Math.E * Math.E ), 0.00000001 );

    }
  @Test
  void testVbaBasic1(Context<?> context) {
	  // If any arg is null, result is null.
	    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "exp(null)").returns( "" );

  }
  @Test
  void testVbaBasic2(Context<?> context) {
	  // If any arg is null, result is null.
	    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "exp(cast(null as numeric))").returns( "" );

  }

  // Test a VBA function with variable number of args.
  @Test
  void testVbaOverloading(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "replace('xyzxyz', 'xy', 'a')").returns( "azaz" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "replace('xyzxyz', 'xy', 'a', 2)").returns( "xyzaz" );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "replace('xyzxyz', 'xy', 'a', 1, 1)").returns( "azxyz" );
  }

  // Test VBA exception handling
  @Test
  void testVbaExceptions(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "right(\"abc\", -4)").throwsMessage(
      "StringIndexOutOfBoundsException: Range [7, 3) out of bounds for length 3");
  }

  @Test
  void testVbaDateTime(Context<?> context) {
    // function which returns date
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
      "Format(DateSerial(2006, 4, 29), \"Long Date\")").returns(
      "Saturday, April 29, 2006" );
    // function with date parameter
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Year(DateSerial(2006, 4, 29))").returns( "2,006" );
  }

  @Test
  void testExcelPi(Context<?> context) {
    // The PI function is defined in the Excel class.
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Pi()").returns( "3" );
  }

  @Test
  void testExcelPower(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Power(8, 0.333333)").returns( 2.0, 0.01 );
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Power(-2, 0.5)").returns( Double.NaN, 0.001 );
  }

  // Comment from the bug: the reason for this is that in AbstractExpCompiler
  // in the compileInteger method we are casting an IntegerCalc into a
  // DoubleCalc and there is no check for IntegerCalc in the NumericType
  // conditional path.
  @Test
  void testBug1881739(Context<?> context) {
    assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "LEFT(\"TEST\", LEN(\"TEST\"))").returns( "TEST" );
  }

  /**
   * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-296"> MONDRIAN-296, "Cube getTimeDimension use
   * when Cube has no Time dimension"</a>.
   */
  @Test
  void testCubeTimeDimensionFails(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select LastPeriods(1) on columns from [Store]").throwsMessage(
      "'LastPeriods', no time dimension" );
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select OpeningPeriod() on columns from [Store]").throwsMessage(
      "'OpeningPeriod', no time dimension" );
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select OpeningPeriod([Store Type]) on columns from [Store]").throwsMessage(
      "'OpeningPeriod', no time dimension" );
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select ClosingPeriod() on columns from [Store]").throwsMessage(
      "'ClosingPeriod', no time dimension" );
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select ClosingPeriod([Store Type]) on columns from [Store]").throwsMessage(
      "'ClosingPeriod', no time dimension" );
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select ParallelPeriod() on columns from [Store]").throwsMessage(
      "'ParallelPeriod', no time dimension" );
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select PeriodsToDate() on columns from [Store]").throwsMessage(
      "'PeriodsToDate', no time dimension" );
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select Mtd() on columns from [Store]").throwsMessage(
      "'Mtd', no time dimension" );
  }

  /**
   * Executes a query that has a complex parse tree. Goal is to find algorithmic complexity bugs in the validator which
   * would make the query run extremely slowly.
   */
  @Test
  void testComplexQuery(Context<?> context) {
    final String expected =
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Gender].[Gender].[All Gender]}\n"
        + "{[Gender].[Gender].[F]}\n"
        + "{[Gender].[Gender].[M]}\n"
        + "Row #0: 266,773\n"
        + "Row #1: 131,558\n"
        + "Row #2: 135,215\n";

    // hand written case
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select\n"
        + "   [Measures].[Unit Sales] on 0,\n"
        + "   Distinct({\n"
        + "     [Gender],\n"
        + "     Tail(\n"
        + "       Head({\n"
        + "         [Gender],\n"
        + "         [Gender].[F],\n"
        + "         [Gender].[M]},\n"
        + "         2),\n"
        + "       1),\n"
        + "     Tail(\n"
        + "       Head({\n"
        + "         [Gender],\n"
        + "         [Gender].[F],\n"
        + "         [Gender].[M]},\n"
        + "         2),\n"
        + "       1),\n"
        + "     [Gender].[M]}) on 1\n"
        + "from [Sales]").returnsGrid( expected );

    // generated equivalent
    StringBuilder buf = new StringBuilder();
    buf.append(
      "select\n"
        + "   [Measures].[Unit Sales] on 0,\n" );
    generateComplex( buf, "   ", 0, 7, 3 );
    buf.append(
      " on 1\n"
        + "from [Sales]" );
    if ( false ) {
      System.out.println( buf.toString().length() + ": " + buf.toString() );
    }
    assertThatQuery(context.getConnectionWithDefaultRole(), buf.toString()).returnsGrid( expected );
  }

  /**
   * Recursive routine to generate a complex MDX expression.
   *
   * @param buf        String builder
   * @param indent     Indent
   * @param depth      Current depth
   * @param depthLimit Max recursion depth
   * @param breadth    Number of iterations at each depth
   */
  void generateComplex(
    StringBuilder buf,
    String indent,
    int depth,
    int depthLimit,
    int breadth ) {
    buf.append( indent + "Distinct({\n" );
    buf.append( indent + "  [Gender],\n" );
    for ( int i = 0; i < breadth; i++ ) {
      if ( depth < depthLimit ) {
        buf.append( indent + "  Tail(\n" );
        buf.append( indent + "    Head({\n" );
        generateComplex(
          buf,
          indent + "      ",
          depth + 1,
          depthLimit,
          breadth );
        buf.append( "},\n" );
        buf.append( indent + "      2),\n" );
        buf.append( indent + "    1),\n" );
      } else {
        buf.append( indent + "  [Gender].[F],\n" );
      }
    }
    buf.append( indent + "  [Gender].[M]})" );
  }

  /**
   * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-1050"> MONDRIAN-1050, "MDX Order function fails
   * when using DateTime expression for ordering"</a>.
   */
  @Test
  void testDateParameter(Context<?> context) throws Exception {
    String query = "SELECT"
      + " {[Measures].[Unit Sales]} ON COLUMNS,"
      + " Order([Gender].Members,"
      + " Now(), ASC) ON ROWS"
      + " FROM [Sales]";
    String expected = "Axis #0:\n"
      + "{}\n"
      + "Axis #1:\n"
      + "{[Measures].[Unit Sales]}\n"
      + "Axis #2:\n"
      + "{[Gender].[Gender].[All Gender]}\n"
      + "{[Gender].[Gender].[F]}\n"
      + "{[Gender].[Gender].[M]}\n"
      + "Row #0: 266,773\n"
      + "Row #1: 131,558\n"
      + "Row #2: 135,215\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expected );
  }

  @Test
  void testComplexSlicer_BaseBase(Context<?> context) {
    String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0, "
        + "{[Education Level].Members} ON 1 "
        + "FROM [Sales] "
        + "WHERE {[Time].[1997].[Q2],[Time].[1998].[Q1]}";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[1997].[Q2]}\n"
        + "{[Time].[Time].[1998].[Q1]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Axis #2:\n"
        + "{[Education Level].[Education Level].[All Education Levels]}\n"
        + "{[Education Level].[Education Level].[Bachelors Degree]}\n"
        + "{[Education Level].[Education Level].[Graduate Degree]}\n"
        + "{[Education Level].[Education Level].[High School Degree]}\n"
        + "{[Education Level].[Education Level].[Partial College]}\n"
        + "{[Education Level].[Education Level].[Partial High School]}\n"
        + "Row #0: 2,973\n"
        + "Row #1: 760\n"
        + "Row #2: 178\n"
        + "Row #3: 853\n"
        + "Row #4: 273\n"
        + "Row #5: 909\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_Calc(Context<?> context) {
      /*
      ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />" ));
       */
    String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0, "
        + "{[Education Level].Members} ON 1 "
        + "FROM [Sales] "
        + "WHERE {[Time].[H1 1997]}";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[H1 1997]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Axis #2:\n"
        + "{[Education Level].[Education Level].[All Education Levels]}\n"
        + "{[Education Level].[Education Level].[Bachelors Degree]}\n"
        + "{[Education Level].[Education Level].[Graduate Degree]}\n"
        + "{[Education Level].[Education Level].[High School Degree]}\n"
        + "{[Education Level].[Education Level].[Partial College]}\n"
        + "{[Education Level].[Education Level].[Partial High School]}\n"
        + "Row #0: 4,257\n"
        + "Row #1: 1,109\n"
        + "Row #2: 240\n"
        + "Row #3: 1,237\n"
        + "Row #4: 394\n"
        + "Row #5: 1,277\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_CalcBase(Context<?> context) {
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />" ));
     */

      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0, "
        + "{[Education Level].Members} ON 1 "
        + "FROM [Sales] "
        + "WHERE {[Time].[H1 1997],[Time].[1998].[Q1]}";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[H1 1997]}\n"
        + "{[Time].[Time].[1998].[Q1]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Axis #2:\n"
        + "{[Education Level].[Education Level].[All Education Levels]}\n"
        + "{[Education Level].[Education Level].[Bachelors Degree]}\n"
        + "{[Education Level].[Education Level].[Graduate Degree]}\n"
        + "{[Education Level].[Education Level].[High School Degree]}\n"
        + "{[Education Level].[Education Level].[Partial College]}\n"
        + "{[Education Level].[Education Level].[Partial High School]}\n"
        + "Row #0: 4,257\n"
        + "Row #1: 1,109\n"
        + "Row #2: 240\n"
        + "Row #3: 1,237\n"
        + "Row #4: 394\n"
        + "Row #5: 1,277\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_BaseCalc(Context<?> context) {
     /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />" ));
    */

      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0, "
        + "{[Education Level].Members} ON 1 "
        + "FROM [Sales] "
        + "WHERE {[Time].[1998].[Q1], [Time].[H1 1997]}";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[1998].[Q1]}\n"
        + "{[Time].[Time].[H1 1997]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Axis #2:\n"
        + "{[Education Level].[Education Level].[All Education Levels]}\n"
        + "{[Education Level].[Education Level].[Bachelors Degree]}\n"
        + "{[Education Level].[Education Level].[Graduate Degree]}\n"
        + "{[Education Level].[Education Level].[High School Degree]}\n"
        + "{[Education Level].[Education Level].[Partial College]}\n"
        + "{[Education Level].[Education Level].[Partial High School]}\n"
        + "Row #0: 4,257\n"
        + "Row #1: 1,109\n"
        + "Row #2: 240\n"
        + "Row #3: 1,237\n"
        + "Row #4: 394\n"
        + "Row #5: 1,277\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_Calc_Base(Context<?> context) {
     /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />" ));
      */
      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0 "
        + "FROM [Sales] "
        + "WHERE ([Time].[H1 1997],[Education Level].[Partial College])";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[H1 1997], [Education Level].[Education Level].[Partial College]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Row #0: 394\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier2.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_Calc_Calc(Context<?> context) {
      /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />"
        + "<CalculatedMember "
        + "name='Partial' "
        + "formula='Aggregate([Education Level].[Partial College]:[Education Level].[Partial High School])' "
        + "dimension='Education Level' />"));
       */

      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0 "
        + "FROM [Sales] "
        + "WHERE ([Time].[H1 1997],[Education Level].[Partial])";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[H1 1997], [Education Level].[Education Level].[Partial]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Row #0: 1,671\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }


  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_X_Base_Base(Context<?> context) {
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />" ));
      */

      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0 "
        + "FROM [Sales] "
        + "WHERE CROSSJOIN ([Time].[1997].[Q1] , [Education Level].[Partial College])";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial College]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Row #0: 278\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_X_Calc_Base(Context<?> context) {
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />" ));
    */

      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0 "
        + "FROM [Sales] "
        + "WHERE CROSSJOIN ([Time].[H1 1997] , [Education Level].[Partial College])";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[H1 1997], [Education Level].[Education Level].[Partial College]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Row #0: 394\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier2.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_X_Calc_Calc(Context<?> context) {
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />"
        + "<CalculatedMember "
        + "name='Partial' "
        + "formula='Aggregate([Education Level].[Partial College]:[Education Level].[Partial High School])' "
        + "dimension='Education Level' />" ));
      */
      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0 "
        + "FROM [Sales] "
        + "WHERE CROSSJOIN ([Time].[H1 1997] , [Education Level].[Partial])";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[H1 1997], [Education Level].[Education Level].[Partial]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Row #0: 1,671\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_X_BaseBase_Base(Context<?> context) {
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />" ));
    */

      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0 "
        + "FROM [Sales] "
        + "WHERE CROSSJOIN ({[Time].[1997].[Q1], [Time].[1997].[Q2]} , [Education Level].[Partial College])";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial College]}\n"
        + "{[Time].[Time].[1997].[Q2], [Education Level].[Education Level].[Partial College]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Row #0: 394\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  void testComplexSlicer_X_BaseBaseBase_BaseBase(Context<?> context) {
    String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0 "
        + "FROM [Sales] "
        + "WHERE CROSSJOIN ({[Time].[1997].[Q1],[Time].[1997].[Q2],[Time].[1998].[Q1]} , {[Education Level].[Partial "
        + "College],[Education Level].[Partial High School]})";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial College]}\n"
        + "{[Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School]}\n"
        + "{[Time].[Time].[1997].[Q2], [Education Level].[Education Level].[Partial College]}\n"
        + "{[Time].[Time].[1997].[Q2], [Education Level].[Education Level].[Partial High School]}\n"
        + "{[Time].[Time].[1998].[Q1], [Education Level].[Education Level].[Partial College]}\n"
        + "{[Time].[Time].[1998].[Q1], [Education Level].[Education Level].[Partial High School]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Row #0: 1,671\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_X_CalcBase_Base(Context<?> context) {
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />" ));
     */

      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0 "
        + "FROM [Sales] "
        + "WHERE CROSSJOIN ({[Time].[H1 1997],[Time].[1998].[Q1]} , [Education Level].[Partial College])";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[H1 1997], [Education Level].[Education Level].[Partial College]}\n"
        + "{[Time].[Time].[1998].[Q1], [Education Level].[Education Level].[Partial College]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Row #0: 394\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_X_CalcBase_BaseBase(Context<?> context) {
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />" ));
     */
      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0 "
        + "FROM [Sales] "
        + "WHERE CROSSJOIN ({[Time].[H1 1997],[Time].[1998].[Q1]} , {[Education Level].[Partial College],[Education "
        + "Level].[Partial High School]})";
    String expectedResult =
      "Axis #0:\n"
        + "{[Time].[Time].[H1 1997], [Education Level].[Education Level].[Partial College]}\n"
        + "{[Time].[Time].[H1 1997], [Education Level].[Education Level].[Partial High School]}\n"
        + "{[Time].[Time].[1998].[Q1], [Education Level].[Education Level].[Partial College]}\n"
        + "{[Time].[Time].[1998].[Q1], [Education Level].[Education Level].[Partial High School]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Row #0: 1,671\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier2.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testComplexSlicer_Calc_ComplexAxis(Context<?> context) {
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='Aggregate([Time].[1997].[Q1]:[Time].[1997].[Q2])' "
        + "dimension='Time' />"
        + "<CalculatedMember "
        + "name='Partial' "
        + "formula='Aggregate([Education Level].[Partial College]:[Education Level].[Partial High School])' "
        + "dimension='Education Level' />" ));
      */

      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0, "
        + "{[Time].[H1 1997], [Time].[1997].[Q1]} ON 1 "
        + "FROM [Sales] "
        + "WHERE "
        + "{[Education Level].[Partial]} ";
    String expectedResult =
      "Axis #0:\n"
        + "{[Education Level].[Education Level].[Partial]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Customer Count]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[H1 1997]}\n"
        + "{[Time].[Time].[1997].[Q1]}\n"
        + "Row #0: 1,671\n"
        + "Row #1: 1,173\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid( expectedResult );
  }

  @Disabled //TODO need investigate
  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier.class },
          database = FoodmartDatabaseSupplier.class)
  void testComplexSlicer_Unsupported(Context<?> context) {
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
      "Sales",
      null,
      "<CalculatedMember "
        + "name='H1 1997' "
        + "formula='([Time].[1997].[Q1] - [Time].[1997].[Q2])' "
        + "dimension='Time' />" ));
     */
      String query =
      "SELECT "
        + "{[Measures].[Customer Count]} ON 0, "
        + "{[Education Level].Members} ON 1 "
        + "FROM [Sales] "
        + "WHERE {[Time].[H1 1997],[Time].[1998].[Q1]}";
    final String errorMessagePattern =
      "Calculated member 'H1 1997' is not supported within a compound predicate";
    assertThatQuery(context.getConnectionWithDefaultRole(), query).throwsMessage( errorMessagePattern );
  }

  /**
   * Replaces anonymous class names (/\$\d+/) with a stub "$-anonymous-class-" in constructions
   * "class&nbsp;mondrian.rest.package.name.ClassName$InnerClassNames". <br/> e.g. <br/>
   * <code>stubAnonymousClasses("class mondrian.fun.Fun$21$1")</code>
   * results
   * <code>
   * "class mondrian.fun.Fun$-anonymous-class-$-anonymous-class-"
   * </code>.
   * <br/> Within a Strings comparison <br/> applying this to both compared <code>String</code>s makes the comparison
   * independent on anonymous class names.
   * </br>
   */
  private static String stubAnonymousClasses( String str ) {
    if ( !str.contains( "$" ) ) {
      return str;
    }
    final String regex =
        "(class mondrian(?:\\.\\w+)*(?:\\$(?:\\w+|-anonymous-class-))*?)(?:\\$\\d+)\\b";
    final String replacement = "$1\\$-anonymous-class-";
    Pattern p = Pattern.compile( regex );
    String str1 = p.matcher( str ).replaceAll( replacement );
    while ( !str.equals( str1 ) ) {
      str = str1;
      str1 = p.matcher( str ).replaceAll( replacement );
    }
    return str1;
  }

  /** Named bridge onto the FoodMart CSVs (for the {@code data =} supplier form). */
  public static class FoodmartData implements org.eclipse.daanse.cwm.testkit.api.DataSupplier {
      @Override
      public Map<String, URL> csvResources() {
          return new FoodmartTestInstance().dataSupplier().csvResources();
      }
  }
}
