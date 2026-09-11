/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2021 Hitachi Vantara
 * All Rights Reserved.
 *
 * jhyde, Feb 14, 2003
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

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Pattern;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.api.calc.profile.ProfileHandler;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.execution.QueryTiming;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.result.CellSet;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.impl.RectangularCellSetFormatter;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Tests related to explain plan and QueryTiming
 *
 * @author Benny
 *
 */
@RolapContextTest(FoodmartTestInstance.class)
class ExplainPlanTest {


  @BeforeEach
  public void beforeEach() {

  }

  @AfterEach
  public void afterEach() {
  }

  @Disabled //TODO need investigate
  @Test
  void testExplain(Context<?> context) throws SQLException {
//    Level originalLevel = RolapUtil.PROFILE_LOGGER.getLevel();
    //Util.setLevel( RolapUtil.PROFILE_LOGGER, Level.OFF ); // Must turn off in case test environment has enabled profiling
    Connection connection = context.getConnectionWithDefaultRole();
    final Statement statement = connection.createStatement();
    final ResultSet resultSet =
        statement.executeQuery( "explain plan for\n" + "select [Measures].[Unit Sales] on 0,\n"
            + "  Filter([Product].Children, [Measures].[Unit Sales] > 100) on 1\n" + "from [Sales]", Optional.empty(), null );
    assertTrue( resultSet.next() );
    assertEquals( 1, resultSet.getMetaData().getColumnCount() );
    assertEquals( "PLAN", resultSet.getMetaData().getColumnName( 1 ) );
    assertEquals( Types.VARCHAR, resultSet.getMetaData().getColumnType( 1 ) );
    String s = resultSet.getString( 1 );
    String expected = """
Axis (COLUMNS):
mondrian.olap.fun.SetFunDef$SetListCalc(type=SetType<MemberType<member=[Measures].[Unit Sales]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0)
    mondrian.olap.fun.SetFunDef$SetListCalc$4(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE, callCount=0, callMillis=0)
        org.eclipse.daanse.olap.calc.base.type.tuple.MemberCalcToTupleCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE, callCount=0, callMillis=0)
            org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)

Axis (ROWS):
mondrian.olap.fun.FilterFunDef$ImmutableIterCalc(type=SetType<MemberType<hierarchy=[Product]>>, resultStyle=ITERABLE, callCount=0, callMillis=0)
    mondrian.olap.fun.BuiltinFunTable$20$1(type=SetType<MemberType<hierarchy=[Product]>>, resultStyle=LIST, callCount=0, callMillis=0)
        mondrian.olap.fun.HierarchyCurrentMemberFunDef$CurrentMemberFixedCalc(type=MemberType<hierarchy=[Product]>, resultStyle=VALUE, callCount=0, callMillis=0)
    mondrian.olap.fun.BuiltinFunTable$59$1(type=BOOLEAN, resultStyle=VALUE, callCount=0, callMillis=0)
        mondrian.calc.impl.AbstractExpCompiler$UnknownToDoubleCalc(type=NUMERIC, resultStyle=VALUE, callCount=0, callMillis=0)
            mondrian.calc.impl.MemberValueCalc(type=SCALAR, resultStyle=VALUE, callCount=0, callMillis=0)
                org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
        org.eclipse.daanse.olap.calc.base.constant.ConstantDoubleCalc(type=NUMERIC, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)

		""";
	assertEquals(stubAnonymousClasses(expected), stubAnonymousClasses(s));
    //Util.setLevel( RolapUtil.PROFILE_LOGGER, originalLevel );
  }

  @Disabled //TODO need investigate
  @Test
  @DisabledIfSystemProperty(named = "tempIgnoreStrageTests",matches = "true")
	void testExplainComplex(Context<?> context) throws SQLException {
//    Level originalLevel = RolapUtil.PROFILE_LOGGER.getLevel();
		// Util.setLevel( RolapUtil.PROFILE_LOGGER, Level.OFF );; // Must turn off in
		// case test environment has enabled profiling
		Connection connection = context.getConnectionWithDefaultRole();
		final Statement statement = connection.createStatement();
		final String mdx = """
				with member [Time].[Time].[1997].[H1] as
				    Aggregate({[Time].[1997].[Q1], [Time].[1997].[Q2]})
				  member [Measures].[Store Margin] as
				    [Measures].[Store Sales] - [Measures].[Store Cost],
				      format_string =
				        iif(
				          [Measures].[Unit Sales] > 50000,
				          "\\<b\\>#.00\\<\\/b\\>",
				           "\\<i\\>#.00\\<\\/i\\>")
				  set [Hi Val Products] as
				    Filter(
				      Descendants([Product].[Drink], , LEAVES),
				     [Measures].[Unit Sales] > 100)
				select
				  {[Measures].[Unit Sales], [Measures].[Store Margin]} on 0,
				  [Hi Val Products] * [Marital Status].Members on 1
				from [Sales]
				where [Gender].[F]""";

		// Plan before execution.
		final ResultSet resultSet = statement.executeQuery("explain plan for\n" + mdx, Optional.empty(), null);
		assertTrue(resultSet.next());
		String s = resultSet.getString(1);
		String expected = """
				Axis (FILTER):
				mondrian.olap.fun.SetFunDef$SetListCalc(type=SetType<TupleType<MemberType<member=[Gender].[F]>>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0)
				    mondrian.olap.fun.SetFunDef$SetListCalc$4(type=TupleType<MemberType<member=[Gender].[F]>>, resultStyle=VALUE, callCount=0, callMillis=0)
				        mondrian.olap.fun.TupleFunDef$CurrentMemberCalc(type=TupleType<MemberType<member=[Gender].[F]>>, resultStyle=VALUE, callCount=0, callMillis=0)
				            org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Gender].[F]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)

				Axis (COLUMNS):
				mondrian.olap.fun.SetFunDef$SetListCalc(type=SetType<MemberType<member=[Measures].[Unit Sales]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0)
				    mondrian.olap.fun.SetFunDef$SetListCalc$4(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE, callCount=0, callMillis=0)
				        org.eclipse.daanse.olap.calc.base.type.tuple.MemberCalcToTupleCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE, callCount=0, callMillis=0)
				            org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
				    mondrian.olap.fun.SetFunDef$SetListCalc$4(type=MemberType<member=[Measures].[Store Margin]>, resultStyle=VALUE, callCount=0, callMillis=0)
				        org.eclipse.daanse.olap.calc.base.type.tuple.MemberCalcToTupleCalc(type=MemberType<member=[Measures].[Store Margin]>, resultStyle=VALUE, callCount=0, callMillis=0)
				            org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Store Margin]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)

				Axis (ROWS):
				mondrian.olap.fun.CrossJoinFunDef$CrossJoinIterCalc(type=SetType<TupleType<MemberType<member=[Product].[Drink]>, MemberType<hierarchy=[Marital Status]>>>, resultStyle=ITERABLE, callCount=0, callMillis=0)
				    mondrian.mdx.NamedSetExpressionImpl$1(type=SetType<MemberType<member=[Product].[Drink]>>, resultStyle=ITERABLE, callCount=0, callMillis=0)
				    mondrian.olap.fun.BuiltinFunTable$21$1(type=SetType<MemberType<hierarchy=[Marital Status]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0)
				        org.eclipse.daanse.olap.calc.base.constant.ConstantHierarchyCalc(type=HierarchyType<hierarchy=[Marital Status]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)

				""";
		assertEquals(stubAnonymousClasses(expected), stubAnonymousClasses(s));

		// Plan after execution, including profiling.
		final ArrayList<String> strings = new ArrayList<>();
		((Statement) statement).enableProfiling(new ProfileHandler() {
			@Override
			public void explain(String plan, QueryTiming timing) {
				strings.add(plan);
				strings.add(String.valueOf(timing));
			}
		});

		final CellSet cellSet = statement.executeQuery(mdx);
		new RectangularCellSetFormatter(true).format(cellSet, new PrintWriter(new StringWriter()));
		cellSet.close();
		assertEquals(8, strings.size());
		String actual = strings.get(0).replaceAll("callMillis=[0-9]+", "callMillis=nnn").replaceAll("[0-9]+ms",
				"nnnms");
		expected = """
NamedSet (Hi Val Products):
mondrian.olap.fun.FilterFunDef$MutableIterCalc(type=SetType<MemberType<member=[Product].[Drink]>>, resultStyle=ITERABLE, callCount=0, callMillis=nnn)
    mondrian.olap.fun.DescendantsFunDef$1(type=SetType<MemberType<member=[Product].[Drink]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=nnn)
        org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Product].[Drink]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=nnn)
    mondrian.olap.fun.BuiltinFunTable$59$1(type=BOOLEAN, resultStyle=VALUE, callCount=0, callMillis=nnn)
        mondrian.calc.impl.AbstractExpCompiler$UnknownToDoubleCalc(type=NUMERIC, resultStyle=VALUE, callCount=0, callMillis=nnn)
            mondrian.calc.impl.MemberValueCalc(type=SCALAR, resultStyle=VALUE, callCount=0, callMillis=nnn)
                org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=nnn)
        org.eclipse.daanse.olap.calc.base.constant.ConstantDoubleCalc(type=NUMERIC, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=nnn)
        """;
		assertEquals(stubAnonymousClasses(expected), stubAnonymousClasses(actual));

		assertTrue(strings.get(1).contains("FilterFunDef invoked 6 times for total of"), strings.get(1));

		actual = strings.get(2).replaceAll("callMillis=[0-9]+", "callMillis=nnn").replaceAll("[0-9]+ms", "nnnms");
		String expected2 = """
Axis (COLUMNS):
mondrian.olap.fun.SetFunDef$SetListCalc(type=SetType<MemberType<member=[Measures].[Unit Sales]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=nnn)
    mondrian.olap.fun.SetFunDef$SetListCalc$4(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE, callCount=0, callMillis=nnn)
        org.eclipse.daanse.olap.calc.base.type.tuple.MemberCalcToTupleCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE, callCount=0, callMillis=nnn)
            org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=nnn)
    mondrian.olap.fun.SetFunDef$SetListCalc$4(type=MemberType<member=[Measures].[Store Margin]>, resultStyle=VALUE, callCount=0, callMillis=nnn)
        org.eclipse.daanse.olap.calc.base.type.tuple.MemberCalcToTupleCalc(type=MemberType<member=[Measures].[Store Margin]>, resultStyle=VALUE, callCount=0, callMillis=nnn)
            org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Store Margin]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=nnn)
				""";
		assertEquals(stubAnonymousClasses(expected2), stubAnonymousClasses(actual));

		actual = strings.get(4).replaceAll("callMillis=[0-9]+", "callMillis=nnn").replaceAll("[0-9]+ms", "nnnms");
		String expected3 = """
Axis (ROWS):
mondrian.olap.fun.CrossJoinFunDef$CrossJoinIterCalc(type=SetType<TupleType<MemberType<member=[Product].[Drink]>, MemberType<hierarchy=[Marital Status]>>>, resultStyle=ITERABLE, callCount=0, callMillis=nnn)
    mondrian.mdx.NamedSetExpr$1(type=SetType<MemberType<member=[Product].[Drink]>>, resultStyle=ITERABLE, callCount=0, callMillis=nnn)
    mondrian.olap.fun.BuiltinFunTable$22$1(type=SetType<MemberType<hierarchy=[Marital Status]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=nnn)
        org.eclipse.daanse.olap.calc.base.constant.ConstantHierarchyCalc(type=HierarchyType<hierarchy=[Marital Status]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=nnn)
				""";
		assertEquals(stubAnonymousClasses(expected3), stubAnonymousClasses(actual));

		actual = strings.get(6).replaceAll("callMillis=[0-9]+", "callMillis=nnn").replaceAll("[0-9]+ms", "nnnms");
		assertEquals(stubAnonymousClasses("QueryBody:\n"), stubAnonymousClasses(actual));

		assertTrue(strings.get(3).contains("SqlStatement-SqlTupleReader.readTuples [[Product].[Product "
				+ "Category]] invoked 1 times for total of "), strings.get(3));
		// Util.setLevel( RolapUtil.PROFILE_LOGGER, originalLevel );
	}

  @Test
  void testExplainInvalid(Context<?> context) throws SQLException {
    Connection connection = context.getConnectionWithDefaultRole();
    assertThatQuery(connection,
        "select\n" + "  {[Measures].[Unit Sales], [Measures].[Store Margin]} on 0,\n"
            + "  [Hi Val Products] * [Marital Status].Members on 1\n" + "from [Sales]\n" + "where [Gender].[F]")
        .throwsMessage("MDX object '[Measures].[Store Margin]' not found in cube 'Sales'");
  }

  /**
   * Verifies all QueryTiming elements
   *
   * @throws SQLException
   */
  @Test
  void testQueryTimingAnalyzer(Context<?> context) throws SQLException {

    final String mdx =
        "WITH\r\n"
            + " SET [*NATIVE_CJ_SET_WITH_SLICER] AS 'FILTER(NONEMPTYCROSSJOIN([*BASE_MEMBERS__Gender_],[*BASE_MEMBERS__Education Level_]), NOT ISEMPTY ([Measures].[Unit Sales]))'\r\n"
            + " SET [*NATIVE_CJ_SET] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Gender].[Gender].CURRENTMEMBER)})'\r\n"
            + " SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Gender].CURRENTMEMBER.ORDERKEY,BASC)'\r\n"
            + " SET [*BASE_MEMBERS__Education Level_] AS '{[Education Level].[Education Level].[All Education Levels].[Bachelors Degree],[Education Level].[Education Level].[All Education Levels].[Graduate Degree]}'\r\n"
            + " SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0],[Measures].[*SUMMARY_MEASURE_1]}'\r\n"
            + " SET [*BASE_MEMBERS__Gender_] AS '[Gender].[Gender].[Gender].MEMBERS'\r\n"
            + " SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Education Level].[Education Level].CURRENTMEMBER)})'\r\n"
            + " SET [*NATIVE_MEMBERS__Gender_] AS 'GENERATE([*NATIVE_CJ_SET], {[Gender].[Gender].CURRENTMEMBER})'\r\n"
            + " SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Gender].[Gender].CURRENTMEMBER)})'\r\n"
            + " MEMBER [Gender].[*TOTAL_MEMBER_SEL~AGG] AS 'AGGREGATE({[Gender].[Gender].[All Gender]})', SOLVE_ORDER=-100\r\n"
            + " MEMBER [Gender].[*TOTAL_MEMBER_SEL~AVG] AS 'AVG([*CJ_ROW_AXIS])', SOLVE_ORDER=300\r\n"
            + " MEMBER [Gender].[*TOTAL_MEMBER_SEL~MAX] AS 'MAX([*CJ_ROW_AXIS])', SOLVE_ORDER=300\r\n"
            + " MEMBER [Gender].[*TOTAL_MEMBER_SEL~SUM] AS 'SUM([*CJ_ROW_AXIS])', SOLVE_ORDER=100\r\n"
            + " MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', SOLVE_ORDER=500\r\n"
            + " MEMBER [Measures].[*SUMMARY_MEASURE_1] AS 'Rank(([Gender].CURRENTMEMBER),[*CJ_ROW_AXIS],[Measures].[Unit Sales])', FORMAT_STRING = '#,##0', SOLVE_ORDER=200\r\n"
            + " SELECT\r\n" + " [*BASE_MEMBERS__Measures_] ON COLUMNS\r\n" + " , NON EMPTY\r\n"
            + " UNION({[Gender].[*TOTAL_MEMBER_SEL~MAX]},UNION({[Gender].[*TOTAL_MEMBER_SEL~AVG]},UNION({[Gender].[*TOTAL_MEMBER_SEL~AGG]},UNION({[Gender].[*TOTAL_MEMBER_SEL~SUM]},[*SORTED_ROW_AXIS])))) ON ROWS\r\n"
            + " FROM [Sales]\r\n" + " WHERE ([*CJ_SLICER_AXIS])";

    ArrayList<String> strings = executeQuery(context, mdx );
    assertEquals( 20, strings.size() );
    assertTrue(strings.get( 19 ).contains( "RankFunDef invoked 16 times" ), strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "EvalForSlicer invoked 6 times" ),  strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "SumFunDef invoked 4 times" ),  strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "Sort invoked 2 times" ),  strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "AggregateFunDef invoked 4" ),  strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "AvgFunDef invoked 4 times" ),  strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "FilterFunDef invoked 2 times" ),  strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "EvalForSort invoked 2 times " ),  strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "MinMaxFunDef invoked 4 times" ),  strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "OrderFunDef invoked 2 times" ),  strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains(
        "SqlStatement-SqlTupleReader.readTuples [[Gender].[Gender].[Gender], [Education Level].[Education Level].[Education Level]] invoked 1 times" ) );
  }

  @Test
  void testMutiKeySort(Context<?> context) throws SQLException {
    final String mdx =
        "WITH\r\n"
            + " SET [*NATIVE_CJ_SET] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Gender_],NONEMPTYCROSSJOIN([*BASE_MEMBERS__Education Level_],[*BASE_MEMBERS__Product_]))'\r\n"
            + " SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Education Level].CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].CURRENTMEMBER, [Product].[Product Family]).ORDERKEY,BASC,ANCESTOR([Product].CURRENTMEMBER, [Product].[Product Department]).ORDERKEY,BASC,[Measures].[*SORTED_MEASURE],BDESC)'\r\n"
            + " SET [*SORTED_COL_AXIS] AS 'ORDER([*CJ_COL_AXIS],[Gender].CURRENTMEMBER.ORDERKEY,BASC,[Measures].CURRENTMEMBER.ORDERKEY,BASC)'\r\n"
            + " SET [*BASE_MEMBERS__Education Level_] AS '{[Education Level].[All Education Levels].[Graduate Degree]}'\r\n"
            + " SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\r\n"
            + " SET [*BASE_MEMBERS__Gender_] AS '[Gender].[Gender].MEMBERS'\r\n"
            + " SET [*CJ_COL_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Gender].CURRENTMEMBER)})'\r\n"
            + " SET [*BASE_MEMBERS__Product_] AS 'FILTER([Product].[Product Category].MEMBERS,ANCESTOR([Product].CURRENTMEMBER, [Product].[Product Family]) IN {[Product].[All Products].[Drink]})'\r\n"
            + " SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Education Level].CURRENTMEMBER,[Product].CURRENTMEMBER)})'\r\n"
            + " MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', SOLVE_ORDER=500\r\n"
            + " MEMBER [Measures].[*SORTED_MEASURE] AS '([Measures].[*FORMATTED_MEASURE_0],[Gender].[M])', SOLVE_ORDER=400\r\n"
            + " SELECT\r\n" + " CROSSJOIN([*SORTED_COL_AXIS],[*BASE_MEMBERS__Measures_]) ON COLUMNS\r\n"
            + " , NON EMPTY\r\n" + " [*SORTED_ROW_AXIS] ON ROWS\r\n" + " FROM [Sales]";

    ArrayList<String> strings = executeQuery(context, mdx );
    assertTrue(strings.get( strings.size() - 1 ).contains(
        "OrderFunDef invoked 5 times" ),  strings.get( strings.size() - 1 ));
  }

  /**
   * Verifies that we don't double count the time spent in instrumented functions such as a SUM within a SUM MDX
   * expression.
   *
   * @throws SQLException
   */
  @Test
  void testNestedSumFunDef(Context<?> context) throws SQLException {
    final String mdx =
        "WITH\r\n"
            + " SET [*NATIVE_CJ_SET] AS 'FILTER([Time].[Time].[Month].MEMBERS, NOT ISEMPTY ([Measures].[Unit Sales]))'\r\n"
            + " SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Time].[Time].CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Time].[Time].CURRENTMEMBER,[Time].[Time].[Quarter]).ORDERKEY,BASC)'\r\n"
            + " SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0],[Measures].[*CALCULATED_MEASURE_1]}'\r\n"
            + " SET [*BASE_MEMBERS__Time_] AS '[Time].[Time].[Month].MEMBERS'\r\n"
            + " SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Time].[Time].CURRENTMEMBER)})'\r\n"
            + " MEMBER [Measures].[*CALCULATED_MEASURE_1] AS 'SUM(YTD(), [Measures].[Unit Sales])', SOLVE_ORDER=0\r\n"
            + " MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', SOLVE_ORDER=500\r\n"
            + " MEMBER [Time].[Time].[*TOTAL_MEMBER_SEL~SUM] AS 'SUM([*CJ_ROW_AXIS])', SOLVE_ORDER=100\r\n" + " SELECT\r\n"
            + " [*BASE_MEMBERS__Measures_] ON COLUMNS\r\n" + " , NON EMPTY\r\n"
            + " UNION({[Time].[Time].[*TOTAL_MEMBER_SEL~SUM]},[*SORTED_ROW_AXIS]) ON ROWS\r\n" + " FROM [Sales]";

    ArrayList<String> strings = executeQuery(context, mdx);
    assertEquals( 14, strings.size() );
    assertTrue(strings.get( 13 ).contains( "SumFunDef invoked 52 times for total of " ), strings.get( 13 ));
    assertTrue(strings.get( 13 ).contains( "XtdWithoutMemberCalc invoked 24 times for total of " ), strings.get( 13 ));
    assertTrue(strings.get( 13 ).contains( "FilterFunDef invoked 2 times for total of " ), strings.get( 13 ));
    assertTrue(strings.get( 13 ).contains( "OrderFunDef invoked 2 times for total of " ), strings.get( 13 ));
  }

  /**
   * Verifies the QueryTimings for when the Aggregate total CM solve order is ABOVE the compound slicer member solve
   * order
   *
   * @throws SQLException
   */
  @Test
  void testAggAboveSlicerSolveOrder(Context<?> context) throws SQLException {

    final String mdx =
        "WITH\r\n"
            + " SET [*NATIVE_CJ_SET_WITH_SLICER] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Education Level_],NONEMPTYCROSSJOIN([*BASE_MEMBERS__Customers_],[*BASE_MEMBERS__Product_]))'\r\n"
            + " SET [*NATIVE_CJ_SET] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Education Level].CURRENTMEMBER,[Customers].CURRENTMEMBER)})'\r\n"
            + " SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Education Level].CURRENTMEMBER.ORDERKEY,BASC,[Customers].CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Customers].CURRENTMEMBER,[Customers].[City]).ORDERKEY,BASC)'\r\n"
            + " SET [*BASE_MEMBERS__Education Level_] AS '{[Education Level].[All Education Levels].[Bachelors Degree],[Education Level].[All Education Levels].[Graduate Degree],[Education Level].[All Education Levels].[High School Degree]}'\r\n"
            + " SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\r\n"
            + " SET [*BASE_MEMBERS__Customers_] AS '{[Customers].[All Customers].[USA].[CA].[Colma].[Catherine Beaudoin],[Customers].[All Customers].[USA].[CA].[San Jose].[Richard Smith]}'\r\n"
            + " SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Product].CURRENTMEMBER)})'\r\n"
            + " SET [*BASE_MEMBERS__Product_] AS '{[Product].[All Products].[Food],[Product].[All Products].[Non-Consumable]}'\r\n"
            + " SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Education Level].CURRENTMEMBER,[Customers].CURRENTMEMBER)})'\r\n"
            + " MEMBER [Customers].[*DEFAULT_MEMBER] AS '[Customers].DEFAULTMEMBER', SOLVE_ORDER=-400\r\n"
            + " MEMBER [Education Level].[*TOTAL_MEMBER_SEL~AGG] AS 'AGGREGATE([*CJ_ROW_AXIS])', SOLVE_ORDER=-100\r\n"
            + " MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', SOLVE_ORDER=500\r\n"
            + " SELECT\r\n" + " [*BASE_MEMBERS__Measures_] ON COLUMNS\r\n" + " , NON EMPTY\r\n"
            + " UNION(CROSSJOIN({[Education Level].[*TOTAL_MEMBER_SEL~AGG]},{([Customers].[*DEFAULT_MEMBER])}),[*SORTED_ROW_AXIS]) ON ROWS\r\n"
            + " FROM [Sales]\r\n" + " WHERE ([*CJ_SLICER_AXIS])";

    ArrayList<String> strings = executeQuery(context, mdx);

    assertTrue(strings.get( 19 ).contains( "EvalForSlicer invoked 4 times" ), strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "AggregateFunDef invoked 2 times" ), strings.get( 19 ));

  }

  /**
   * Verifies the QueryTimings for when the Aggregate total CM solve order is BELOW the compound slicer member solve
   * order
   *
   * @throws SQLException
   */
  @Test
  @RolapConfig(key = ConfigConstants.DISABLE_CACHING, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.COMPOUND_SLICER_MEMBER_SOLVE_ORDER, value = "0", type = Integer.class)
  void testAggBelowSlicerSolveOrder(Context<?> context) throws SQLException {
    final String mdx =
        "WITH\r\n"
            + " SET [*NATIVE_CJ_SET_WITH_SLICER] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Education Level_],NONEMPTYCROSSJOIN([*BASE_MEMBERS__Customers_],[*BASE_MEMBERS__Product_]))'\r\n"
            + " SET [*NATIVE_CJ_SET] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Education Level].CURRENTMEMBER,[Customers].CURRENTMEMBER)})'\r\n"
            + " SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Education Level].CURRENTMEMBER.ORDERKEY,BASC,[Customers].CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Customers].CURRENTMEMBER,[Customers].[City]).ORDERKEY,BASC)'\r\n"
            + " SET [*BASE_MEMBERS__Education Level_] AS '{[Education Level].[All Education Levels].[Bachelors Degree],[Education Level].[All Education Levels].[Graduate Degree],[Education Level].[All Education Levels].[High School Degree]}'\r\n"
            + " SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\r\n"
            + " SET [*BASE_MEMBERS__Customers_] AS '{[Customers].[All Customers].[USA].[CA].[Colma].[Catherine Beaudoin],[Customers].[All Customers].[USA].[CA].[San Jose].[Richard Smith]}'\r\n"
            + " SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Product].CURRENTMEMBER)})'\r\n"
            + " SET [*BASE_MEMBERS__Product_] AS '{[Product].[All Products].[Food],[Product].[All Products].[Non-Consumable]}'\r\n"
            + " SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Education Level].CURRENTMEMBER,[Customers].CURRENTMEMBER)})'\r\n"
            + " MEMBER [Customers].[*DEFAULT_MEMBER] AS '[Customers].DEFAULTMEMBER', SOLVE_ORDER=-400\r\n"
            + " MEMBER [Education Level].[*TOTAL_MEMBER_SEL~AGG] AS 'AGGREGATE([*CJ_ROW_AXIS])', SOLVE_ORDER=-100\r\n"
            + " MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', SOLVE_ORDER=500\r\n"
            + " SELECT\r\n" + " [*BASE_MEMBERS__Measures_] ON COLUMNS\r\n" + " , NON EMPTY\r\n"
            + " UNION(CROSSJOIN({[Education Level].[*TOTAL_MEMBER_SEL~AGG]},{([Customers].[*DEFAULT_MEMBER])}),[*SORTED_ROW_AXIS]) ON ROWS\r\n"
            + " FROM [Sales]\r\n" + " WHERE ([*CJ_SLICER_AXIS])";

    ArrayList<String> strings = executeQuery(context, mdx);
    assertTrue(strings.get( 19 ).contains( "EvalForSlicer invoked 6 times" ), strings.get( 19 ));
    assertTrue(strings.get( 19 ).contains( "AggregateFunDef invoked 4 times" ), strings.get( 19 ));
  }

  private ArrayList<String> executeQuery(Context<?> context, String mdx ) throws SQLException {

    Connection connection = context.getConnectionWithDefaultRole();
    final CacheControl cacheControl = connection.getCacheControl( null );

    // Flush the entire cache.
    final Cube salesCube = connection.getCatalog().lookupCube( "Sales" ).orElseThrow();
    final CacheControl.CellRegion measuresRegion = cacheControl.createMeasuresRegion( salesCube );
    cacheControl.flush( measuresRegion );

    final Statement statement = connection.createStatement();

    final ArrayList<String> strings = new ArrayList<>();
    ( statement ).enableProfiling( new ProfileHandler() {
      @Override
	public void explain( String plan, QueryTiming timing ) {
        strings.add( plan );
        strings.add( String.valueOf( timing ) );
      }
    } );

    CellSet cellSet = statement.executeQuery( mdx );
    cellSet.close();
    return strings;
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
}
