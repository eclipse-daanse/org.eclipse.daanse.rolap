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
import static org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct.getDatabaseProduct;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.eclipse.daanse.rolap.testkit.assertions.FlushSchemaCacheModifier.flushSchemaCache;

import java.util.Collection;
import java.util.List;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.query.Quoting;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.exceptions.NativeEvaluationUnsupportedException;
import org.eclipse.daanse.olap.query.component.IdImpl;
import  org.eclipse.daanse.olap.util.Bug;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.catalog.RolapCatalogReader;
import org.eclipse.daanse.rolap.common.connection.AbstractRolapConnection.NonEmptyResult;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.member.MemberCacheHelper;
import org.eclipse.daanse.rolap.common.member.SmartMemberReader;
import org.eclipse.daanse.rolap.common.nativize.RolapNative.Listener;
import org.eclipse.daanse.rolap.common.nativize.RolapNative.NativeEvent;
import org.eclipse.daanse.rolap.common.nativize.RolapNative.TupleEvent;
import org.eclipse.daanse.rolap.common.nativize.RolapNativeRegistry;
import org.eclipse.daanse.rolap.common.result.RolapResult;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.SqlConstraintFactory;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.HideMemberIf;
import org.eclipse.daanse.rolap.testkit.assertions.NativeVerify;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;

/**
 * Tests for NON EMPTY Optimization, includes SqlConstraint type hierarchy and RolapNative classes.
 *
 * @author av
 * @since Nov 21, 2005
 */
@RolapContextTest(FoodmartTestInstance.class)
class NonEmptyTest extends BatchTestCase {


	public static String hierarchyName(String dimension, String hierarchy) {
		return "[" + dimension + "].[" + hierarchy + "]";
	}

	public static String levelName(String dimension, String hierarchy, String level) {
		return hierarchyName(dimension, hierarchy) + ".[" + level + "]";
	}

  SqlConstraintFactory scf = SqlConstraintFactory.instance();

  private static final String STORE_TYPE_LEVEL =
    levelName( "Store Type", "Store Type", "Store Type" );

  private static final String EDUCATION_LEVEL_LEVEL =
    levelName(
      "Education Level", "Education Level", "Education Level" );

  // No setup or teardown for EnableNativeNonEmpty: true is already the default in
  // ConfigConstants, and the tests that want it off set it on their own context.
  @AfterEach
  public void afterEach() {
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testBugMondrian584EnumOrder(Context<?> context) {
    // The interpreter results include males before females, which is
    // correct because it is consistent with the explicit order present
    // in the query. Native evaluation returns the females before males,
    // which is probably a reflection of the database ordering.
    //
    if ( Bug.Bug584Fixed ) {
      checkNative(context,
        4,
        4,
        "SELECT non empty { CrossJoin( "
          + "  {Gender.M, Gender.F}, "
          + "  { [Marital Status].[Marital Status].members } "
          + ") } on 0 from sales" );
    }
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testBugCantRestrictSlicerToCalcMember(Context<?> context) throws Exception {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH Member [Time].[Time].[Aggr] AS 'Aggregate({[Time].[1998].[Q1], [Time].[1998].[Q2]})' "
        + "SELECT {[Measures].[Store Sales]} ON COLUMNS, "
        + "NON EMPTY Order(TopCount([Customers].[Name].Members,3,[Measures].[Store Sales]),[Measures].[Store Sales],"
        + "BASC) ON ROWS "
        + "FROM [Sales] "
        + "WHERE ([Time].[Aggr])").returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[Aggr]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Sales]}\n"
        + "Axis #2:\n" );
  }

  /**
   * Test case for an issue where mondrian failed to use native evaluation for evaluating crossjoin. With the issue,
   * performance is poor because mondrian is doing crossjoins in memory; and the test case throws because the result
   * limit is exceeded.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.RESULT_LIMIT, value = "5000000", type = Integer.class)
  void testAnalyzerPerformanceIssue(Context<?> context) {

    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Education Level], NonEmptyCrossJoin"
        + "([*BASE_MEMBERS_Product], NonEmptyCrossJoin([*BASE_MEMBERS_Customers], [*BASE_MEMBERS_Time])))' "
        + "set [*METRIC_CJ_SET] as 'Filter([*NATIVE_CJ_SET], ([Measures].[*TOP_Unit Sales_SEL~SUM] <= 2.0))' "
        + "set [*SORTED_ROW_AXIS] as 'Order([*CJ_ROW_AXIS], [Product].[Product].CurrentMember.OrderKey, BASC, Ancestor"
        + "([Product].[Product].CurrentMember, [Product].[Product].[Brand Name]).OrderKey, BASC, [Customers].[Customers].CurrentMember.OrderKey, "
        + "BASC, Ancestor([Customers].[Customers].CurrentMember, [Customers].[Customers].[City]).OrderKey, BASC)' "
        + "set [*SORTED_COL_AXIS] as 'Order([*CJ_COL_AXIS], [Education Level].[Education Level].CurrentMember.OrderKey, BASC)' "
        + "set [*BASE_MEMBERS_Time] as '{[Time].[1997].[Q1]}' "
        + "set [*NATIVE_MEMBERS_Customers] as 'Generate([*NATIVE_CJ_SET], {[Customers].[Customers].CurrentMember})' "
        + "set [*TOP_SET] as 'Order(Generate([*NATIVE_CJ_SET], {[Product].[Product].CurrentMember}), ([Measures].[Unit Sales], "
        + "[Customers].[*CTX_MEMBER_SEL~SUM], [Education Level].[*CTX_MEMBER_SEL~SUM], [Time].[Time].[*CTX_MEMBER_SEL~AGG]),"
        + " BDESC)' "
        + "set [*BASE_MEMBERS_Education Level] as '[Education Level].[Education Level].[Education Level].Members' "
        + "set [*NATIVE_MEMBERS_Education Level] as 'Generate([*NATIVE_CJ_SET], {[Education Level].[Education Level].CurrentMember})' "
        + "set [*METRIC_MEMBERS_Time] as 'Generate([*METRIC_CJ_SET], {[Time].[Time].CurrentMember})' "
        + "set [*NATIVE_MEMBERS_Time] as 'Generate([*NATIVE_CJ_SET], {[Time].[Time].CurrentMember})' "
        + "set [*BASE_MEMBERS_Customers] as '[Customers].[Customers].[Name].Members' "
        + "set [*BASE_MEMBERS_Product] as '[Product].[Product].[Product Name].Members' "
        + "set [*BASE_MEMBERS_Measures] as '{[Measures].[*FORMATTED_MEASURE_0]}' "
        + "set [*CJ_COL_AXIS] as 'Generate([*METRIC_CJ_SET], {[Education Level].[Education Level].CurrentMember})' "
        + "set [*CJ_ROW_AXIS] as 'Generate([*METRIC_CJ_SET], {([Product].[Product].CurrentMember, [Customers].[Customers].CurrentMember)})' "
        + "member [Customers].[*DEFAULT_MEMBER] as '[Customers].[Customers].DefaultMember', SOLVE_ORDER = (- 500.0) "
        + "member [Product].[*TOTAL_MEMBER_SEL~SUM] as 'Sum(Generate([*METRIC_CJ_SET], {([Product].[Product].CurrentMember, "
        + "[Customers].[Customers].CurrentMember)}))', SOLVE_ORDER = (- 100.0) "
        + "member [Customers].[Customers].[*TOTAL_MEMBER_SEL~SUM] as 'Sum(Generate(Exists([*METRIC_CJ_SET], {[Product].[Product]"
        + ".CurrentMember}), {([Product].[Product].CurrentMember, [Customers].[Customers].CurrentMember)}))', SOLVE_ORDER = (- 101.0) "
        + "member [Measures].[*TOP_Unit Sales_SEL~SUM] as 'Rank([Product].[Product].CurrentMember, [*TOP_SET])', SOLVE_ORDER = "
        + "300.0 "
        + "member [Measures].[*FORMATTED_MEASURE_0] as '[Measures].[Unit Sales]', FORMAT_STRING = \"Standard\", "
        + "SOLVE_ORDER = 400.0 "
        + "member [Customers].[Customers].[*CTX_MEMBER_SEL~SUM] as 'Sum({[Customers].[Customers].[All Customers]})', SOLVE_ORDER = (- 101.0) "
        + "member [Education Level].[Education Level].[*TOTAL_MEMBER_SEL~SUM] as 'Sum(Generate([*METRIC_CJ_SET], {[Education Level].[Education Level]"
        + ".CurrentMember}))', SOLVE_ORDER = (- 102.0) "
        + "member [Education Level].[*CTX_MEMBER_SEL~SUM] as 'Sum({[Education Level].[All Education Levels]})', "
        + "SOLVE_ORDER = (- 102.0) "
        + "member [Time].[Time].[*CTX_MEMBER_SEL~AGG] as 'Aggregate([*NATIVE_MEMBERS_Time])', SOLVE_ORDER = (- 402.0) "
        + "member [Time].[Time].[*SLICER_MEMBER] as 'Aggregate([*METRIC_MEMBERS_Time])', SOLVE_ORDER = (- 400.0) "
        + "select Union(Crossjoin({[Education Level].[*TOTAL_MEMBER_SEL~SUM]}, [*BASE_MEMBERS_Measures]), Crossjoin"
        + "([*SORTED_COL_AXIS], [*BASE_MEMBERS_Measures])) ON COLUMNS, "
        + "NON EMPTY Union(Crossjoin({[Product].[*TOTAL_MEMBER_SEL~SUM]}, {[Customers].[Customers].[*DEFAULT_MEMBER]}), Union"
        + "(Crossjoin(Generate([*METRIC_CJ_SET], {[Product].CurrentMember}), {[Customers].[Customers].[*TOTAL_MEMBER_SEL~SUM]}), "
        + "[*SORTED_ROW_AXIS])) ON ROWS "
        + "from [Sales] "
        + "where [Time].[Time].[*SLICER_MEMBER] ").returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[*SLICER_MEMBER]}\n"
        + "Axis #1:\n"
        + "{[Education Level].[Education Level].[*TOTAL_MEMBER_SEL~SUM], [Measures].[*FORMATTED_MEASURE_0]}\n"
        + "{[Education Level].[Education Level].[Bachelors Degree], [Measures].[*FORMATTED_MEASURE_0]}\n"
        + "{[Education Level].[Education Level].[Graduate Degree], [Measures].[*FORMATTED_MEASURE_0]}\n"
        + "{[Education Level].[Education Level].[High School Degree], [Measures].[*FORMATTED_MEASURE_0]}\n"
        + "{[Education Level].[Education Level].[Partial College], [Measures].[*FORMATTED_MEASURE_0]}\n"
        + "{[Education Level].[Education Level].[Partial High School], [Measures].[*FORMATTED_MEASURE_0]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[*TOTAL_MEMBER_SEL~SUM], [Customers].[Customers].[*DEFAULT_MEMBER]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers]"
        + ".[*TOTAL_MEMBER_SEL~SUM]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[*TOTAL_MEMBER_SEL~SUM]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Puyallup].[Cheryl Herring]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[OR].[Salem].[Robert Ahlering]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Port Orchard].[Judy Zugelder]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Marysville].[Brian Johnston]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[OR].[Corvallis].[Judy Doolittle]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Spokane].[Greg Morgan]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[CA].[West Covina].[Sandra Young]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[CA].[Long Beach].[Dana Chappell]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[CA].[La Mesa].[Georgia Thompson]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Tacoma].[Jessica Dugan]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[OR].[Milwaukie].[Adrian Torrez]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Spokane].[Grace McLaughlin]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Bremerton].[Julia Stewart]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Port Orchard].[Maureen Overholser]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Yakima].[Mary Craig]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[CA].[Spring Valley].[Deborah Adams]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[CA].[Woodland Hills].[Warren Kaufman]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[OR].[Woodburn].[David Moss]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[CA].[Newport Beach].[Michael Sample]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[OR].[Portland].[Ofelia Trembath]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Bremerton].[Alexander Case]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Bremerton].[Gloria Duncan]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[WA].[Olympia].[Jeanette Foster]}\n"
        + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods].[Spices].[BBB Best].[BBB Best Pepper], [Customers].[Customers].[USA]"
        + ".[CA].[Lakewood].[Shyla Bettis]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[OR].[Portland].[Tomas Manzanares]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Bremerton].[Kerry Westgaard]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Yakima].[Beatrice Barney]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Seattle].[James La Monica]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Spokane].[Martha Griego]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Bremerton].[Michelle Neri]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Spokane].[Herman Webb]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Spokane].[Bob Alexander]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Issaquah].[Gery Scott]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Spokane].[Grace McLaughlin]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Kirkland].[Brandon Rohlke]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Port Orchard].[Elwood Carter]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[CA].[Beverly Hills].[Samuel Arden]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[OR].[Woodburn].[Ida Cezar]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Olympia].[Barbara Smith]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Spokane].[Matt Bellah]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Sedro Woolley].[William Akin]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[OR].[Albany].[Karie Taylor]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[OR].[Milwaukie].[Bertie Wherrett]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[CA].[Lincoln Acres].[L. Troy Barnes]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Tacoma].[Patricia Martin]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Bremerton].[Martha Clifton]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Garlic], [Customers].[Customers]"
        + ".[USA].[WA].[Bremerton].[Marla Bell]}\n"
        + "Row #0: 170\n"
        + "Row #0: 45\n"
        + "Row #0: 7\n"
        + "Row #0: 47\n"
        + "Row #0: 16\n"
        + "Row #0: 55\n"
        + "Row #1: 87\n"
        + "Row #1: 25\n"
        + "Row #1: 5\n"
        + "Row #1: 21\n"
        + "Row #1: 8\n"
        + "Row #1: 28\n"
        + "Row #2: 83\n"
        + "Row #2: 20\n"
        + "Row #2: 2\n"
        + "Row #2: 26\n"
        + "Row #2: 8\n"
        + "Row #2: 27\n"
        + "Row #3: 4\n"
        + "Row #3: \n"
        + "Row #3: \n"
        + "Row #3: \n"
        + "Row #3: 4\n"
        + "Row #3: \n"
        + "Row #4: 4\n"
        + "Row #4: \n"
        + "Row #4: \n"
        + "Row #4: \n"
        + "Row #4: 4\n"
        + "Row #4: \n"
        + "Row #5: 3\n"
        + "Row #5: 3\n"
        + "Row #5: \n"
        + "Row #5: \n"
        + "Row #5: \n"
        + "Row #5: \n"
        + "Row #6: 4\n"
        + "Row #6: 4\n"
        + "Row #6: \n"
        + "Row #6: \n"
        + "Row #6: \n"
        + "Row #6: \n"
        + "Row #7: 4\n"
        + "Row #7: \n"
        + "Row #7: \n"
        + "Row #7: \n"
        + "Row #7: \n"
        + "Row #7: 4\n"
        + "Row #8: 4\n"
        + "Row #8: 4\n"
        + "Row #8: \n"
        + "Row #8: \n"
        + "Row #8: \n"
        + "Row #8: \n"
        + "Row #9: 3\n"
        + "Row #9: \n"
        + "Row #9: \n"
        + "Row #9: \n"
        + "Row #9: \n"
        + "Row #9: 3\n"
        + "Row #10: 2\n"
        + "Row #10: 2\n"
        + "Row #10: \n"
        + "Row #10: \n"
        + "Row #10: \n"
        + "Row #10: \n"
        + "Row #11: 3\n"
        + "Row #11: \n"
        + "Row #11: \n"
        + "Row #11: \n"
        + "Row #11: \n"
        + "Row #11: 3\n"
        + "Row #12: 3\n"
        + "Row #12: \n"
        + "Row #12: \n"
        + "Row #12: 3\n"
        + "Row #12: \n"
        + "Row #12: \n"
        + "Row #13: 4\n"
        + "Row #13: 4\n"
        + "Row #13: \n"
        + "Row #13: \n"
        + "Row #13: \n"
        + "Row #13: \n"
        + "Row #14: 4\n"
        + "Row #14: \n"
        + "Row #14: \n"
        + "Row #14: 4\n"
        + "Row #14: \n"
        + "Row #14: \n"
        + "Row #15: 3\n"
        + "Row #15: \n"
        + "Row #15: \n"
        + "Row #15: \n"
        + "Row #15: \n"
        + "Row #15: 3\n"
        + "Row #16: 4\n"
        + "Row #16: \n"
        + "Row #16: \n"
        + "Row #16: 4\n"
        + "Row #16: \n"
        + "Row #16: \n"
        + "Row #17: 5\n"
        + "Row #17: \n"
        + "Row #17: 5\n"
        + "Row #17: \n"
        + "Row #17: \n"
        + "Row #17: \n"
        + "Row #18: 4\n"
        + "Row #18: \n"
        + "Row #18: \n"
        + "Row #18: \n"
        + "Row #18: \n"
        + "Row #18: 4\n"
        + "Row #19: 3\n"
        + "Row #19: \n"
        + "Row #19: \n"
        + "Row #19: 3\n"
        + "Row #19: \n"
        + "Row #19: \n"
        + "Row #20: 3\n"
        + "Row #20: \n"
        + "Row #20: \n"
        + "Row #20: 3\n"
        + "Row #20: \n"
        + "Row #20: \n"
        + "Row #21: 4\n"
        + "Row #21: \n"
        + "Row #21: \n"
        + "Row #21: 4\n"
        + "Row #21: \n"
        + "Row #21: \n"
        + "Row #22: 4\n"
        + "Row #22: 4\n"
        + "Row #22: \n"
        + "Row #22: \n"
        + "Row #22: \n"
        + "Row #22: \n"
        + "Row #23: 4\n"
        + "Row #23: \n"
        + "Row #23: \n"
        + "Row #23: \n"
        + "Row #23: \n"
        + "Row #23: 4\n"
        + "Row #24: 4\n"
        + "Row #24: \n"
        + "Row #24: \n"
        + "Row #24: \n"
        + "Row #24: \n"
        + "Row #24: 4\n"
        + "Row #25: 3\n"
        + "Row #25: \n"
        + "Row #25: \n"
        + "Row #25: \n"
        + "Row #25: \n"
        + "Row #25: 3\n"
        + "Row #26: 4\n"
        + "Row #26: 4\n"
        + "Row #26: \n"
        + "Row #26: \n"
        + "Row #26: \n"
        + "Row #26: \n"
        + "Row #27: 4\n"
        + "Row #27: 4\n"
        + "Row #27: \n"
        + "Row #27: \n"
        + "Row #27: \n"
        + "Row #27: \n"
        + "Row #28: 4\n"
        + "Row #28: 4\n"
        + "Row #28: \n"
        + "Row #28: \n"
        + "Row #28: \n"
        + "Row #28: \n"
        + "Row #29: 3\n"
        + "Row #29: \n"
        + "Row #29: \n"
        + "Row #29: 3\n"
        + "Row #29: \n"
        + "Row #29: \n"
        + "Row #30: 2\n"
        + "Row #30: \n"
        + "Row #30: \n"
        + "Row #30: \n"
        + "Row #30: \n"
        + "Row #30: 2\n"
        + "Row #31: 4\n"
        + "Row #31: \n"
        + "Row #31: \n"
        + "Row #31: \n"
        + "Row #31: 4\n"
        + "Row #31: \n"
        + "Row #32: 5\n"
        + "Row #32: \n"
        + "Row #32: \n"
        + "Row #32: 5\n"
        + "Row #32: \n"
        + "Row #32: \n"
        + "Row #33: 3\n"
        + "Row #33: \n"
        + "Row #33: \n"
        + "Row #33: \n"
        + "Row #33: \n"
        + "Row #33: 3\n"
        + "Row #34: 4\n"
        + "Row #34: 4\n"
        + "Row #34: \n"
        + "Row #34: \n"
        + "Row #34: \n"
        + "Row #34: \n"
        + "Row #35: 3\n"
        + "Row #35: 3\n"
        + "Row #35: \n"
        + "Row #35: \n"
        + "Row #35: \n"
        + "Row #35: \n"
        + "Row #36: 4\n"
        + "Row #36: \n"
        + "Row #36: \n"
        + "Row #36: 4\n"
        + "Row #36: \n"
        + "Row #36: \n"
        + "Row #37: 4\n"
        + "Row #37: \n"
        + "Row #37: \n"
        + "Row #37: 4\n"
        + "Row #37: \n"
        + "Row #37: \n"
        + "Row #38: 3\n"
        + "Row #38: \n"
        + "Row #38: \n"
        + "Row #38: 3\n"
        + "Row #38: \n"
        + "Row #38: \n"
        + "Row #39: 3\n"
        + "Row #39: 3\n"
        + "Row #39: \n"
        + "Row #39: \n"
        + "Row #39: \n"
        + "Row #39: \n"
        + "Row #40: 2\n"
        + "Row #40: \n"
        + "Row #40: 2\n"
        + "Row #40: \n"
        + "Row #40: \n"
        + "Row #40: \n"
        + "Row #41: 4\n"
        + "Row #41: \n"
        + "Row #41: \n"
        + "Row #41: \n"
        + "Row #41: 4\n"
        + "Row #41: \n"
        + "Row #42: 4\n"
        + "Row #42: \n"
        + "Row #42: \n"
        + "Row #42: \n"
        + "Row #42: \n"
        + "Row #42: 4\n"
        + "Row #43: 2\n"
        + "Row #43: 2\n"
        + "Row #43: \n"
        + "Row #43: \n"
        + "Row #43: \n"
        + "Row #43: \n"
        + "Row #44: 3\n"
        + "Row #44: \n"
        + "Row #44: \n"
        + "Row #44: 3\n"
        + "Row #44: \n"
        + "Row #44: \n"
        + "Row #45: 4\n"
        + "Row #45: \n"
        + "Row #45: \n"
        + "Row #45: 4\n"
        + "Row #45: \n"
        + "Row #45: \n"
        + "Row #46: 4\n"
        + "Row #46: \n"
        + "Row #46: \n"
        + "Row #46: \n"
        + "Row #46: \n"
        + "Row #46: 4\n"
        + "Row #47: 3\n"
        + "Row #47: \n"
        + "Row #47: \n"
        + "Row #47: \n"
        + "Row #47: \n"
        + "Row #47: 3\n"
        + "Row #48: 4\n"
        + "Row #48: \n"
        + "Row #48: \n"
        + "Row #48: \n"
        + "Row #48: \n"
        + "Row #48: 4\n"
        + "Row #49: 7\n"
        + "Row #49: \n"
        + "Row #49: \n"
        + "Row #49: \n"
        + "Row #49: \n"
        + "Row #49: 7\n" );
  }

@Test
  void testBug1961163(Context<?> context) throws Exception {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member [Measures].[AvgRevenue] as 'Avg([Store].[Store Name].Members, [Measures].[Store Sales])' "
        + "select NON EMPTY {[Measures].[Store Sales], [Measures].[AvgRevenue]} ON COLUMNS, "
        + "NON EMPTY Filter([Store].[Store Name].Members, ([Measures].[AvgRevenue] < [Measures].[Store Sales])) ON "
        + "ROWS "
        + "from [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Sales]}\n"
        + "{[Measures].[AvgRevenue]}\n"
        + "Axis #2:\n"
        + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
        + "{[Store].[Store].[USA].[CA].[Los Angeles].[Store 7]}\n"
        + "{[Store].[Store].[USA].[CA].[San Diego].[Store 24]}\n"
        + "{[Store].[Store].[USA].[OR].[Portland].[Store 11]}\n"
        + "{[Store].[Store].[USA].[OR].[Salem].[Store 13]}\n"
        + "{[Store].[Store].[USA].[WA].[Bremerton].[Store 3]}\n"
        + "{[Store].[Store].[USA].[WA].[Seattle].[Store 15]}\n"
        + "{[Store].[Store].[USA].[WA].[Spokane].[Store 16]}\n"
        + "{[Store].[Store].[USA].[WA].[Tacoma].[Store 17]}\n"
        + "Row #0: 45,750.24\n"
        + "Row #0: 43,479.86\n"
        + "Row #1: 54,545.28\n"
        + "Row #1: 43,479.86\n"
        + "Row #2: 54,431.14\n"
        + "Row #2: 43,479.86\n"
        + "Row #3: 55,058.79\n"
        + "Row #3: 43,479.86\n"
        + "Row #4: 87,218.28\n"
        + "Row #4: 43,479.86\n"
        + "Row #5: 52,896.30\n"
        + "Row #5: 43,479.86\n"
        + "Row #6: 52,644.07\n"
        + "Row #6: 43,479.86\n"
        + "Row #7: 49,634.46\n"
        + "Row #7: 43,479.86\n"
        + "Row #8: 74,843.96\n"
        + "Row #8: 43,479.86\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testTopCountWithCalcMemberInSlicer(Context<?> context) {
    // Internal error: can not restrict SQL to calculated Members
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member [Time].[Time].[First Term] as 'Aggregate({[Time].[1997].[Q1], [Time].[1997].[Q2]})' "
        + "select {[Measures].[Unit Sales]} ON COLUMNS, "
        + "TopCount([Product].[Product Subcategory].Members, 3, [Measures].[Unit Sales]) ON ROWS "
        + "from [Sales] "
        + "where ([Time].[First Term]) ").returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[First Term]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables]}\n"
        + "{[Product].[Product].[Food].[Produce].[Fruit].[Fresh Fruit]}\n"
        + "{[Product].[Product].[Food].[Canned Foods].[Canned Soup].[Soup]}\n"
        + "Row #0: 10,215\n"
        + "Row #1: 5,711\n"
        + "Row #2: 3,926\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testTopCountCacheKeyMustIncludeCount(Context<?> context) {
    /**
     * When caching topcount results, the number of elements must
     * be part of the cache key
     */
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select {[Measures].[Unit Sales]} ON COLUMNS, "
        + "TopCount([Product].[Product Subcategory].Members, 2, [Measures].[Unit Sales]) ON ROWS "
        + "from [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables]}\n"
        + "{[Product].[Product].[Food].[Produce].[Fruit].[Fresh Fruit]}\n"
        + "Row #0: 20,739\n"
        + "Row #1: 11,767\n" );
    // run again with different count
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select {[Measures].[Unit Sales]} ON COLUMNS, "
        + "TopCount([Product].[Product Subcategory].Members, 3, [Measures].[Unit Sales]) ON ROWS "
        + "from [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables]}\n"
        + "{[Product].[Product].[Food].[Produce].[Fruit].[Fresh Fruit]}\n"
        + "{[Product].[Product].[Food].[Canned Foods].[Canned Soup].[Soup]}\n"
        + "Row #0: 20,739\n"
        + "Row #1: 11,767\n"
        + "Row #2: 8,006\n" );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, TestStrMeasureModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testStrMeasure(Context<?> context) {
      assertThatQuery(context.getConnectionWithDefaultRole(),
      "select {[Measures].[Media]} on columns " + "from [StrMeasure]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Media]}\n"
        + "Row #0: TV\n" );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, TestBug1515302Modifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testBug1515302(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select {[Measures].[Unit Sales]} on columns, "
        + "non empty crossjoin({[Promotions].[Big Promo]}, "
        + "Descendants([Customers].[USA], [City], "
        + "SELF_AND_BEFORE)) on rows "
        + "from [Bug1515302]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Anacortes]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Ballard]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Bellingham]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Burien]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Everett]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Issaquah]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Kirkland]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Lynnwood]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Marysville]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Olympia]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Puyallup]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Redmond]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Renton]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Seattle]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Sedro Woolley]}\n"
        + "{[Promotions].[Promotions].[Big Promo], [Customers].[Customers].[USA].[WA].[Tacoma]}\n"
        + "Row #0: 1,789\n"
        + "Row #1: 1,789\n"
        + "Row #2: 20\n"
        + "Row #3: 35\n"
        + "Row #4: 15\n"
        + "Row #5: 18\n"
        + "Row #6: 60\n"
        + "Row #7: 42\n"
        + "Row #8: 36\n"
        + "Row #9: 79\n"
        + "Row #10: 58\n"
        + "Row #11: 520\n"
        + "Row #12: 438\n"
        + "Row #13: 14\n"
        + "Row #14: 20\n"
        + "Row #15: 65\n"
        + "Row #16: 3\n"
        + "Row #17: 366\n" );
  }

  /**
   * Must not use native sql optimization because it chooses the wrong RolapStar in
   * SqlContextConstraint/ContextConstraintWriter.  Test ensures that no exception is thrown.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testVirtualCube(Context<?> context) {
    if ( context.getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) > 0 ) {
      return;
    }
    TestCase c = new TestCase(context.getConnectionWithDefaultRole(),
      99,
      3,
      "select NON EMPTY {[Measures].[Unit Sales], [Measures].[Warehouse Sales]} ON COLUMNS, "
        + "NON EMPTY [Product].[All Products].Children ON ROWS "
        + "from [Warehouse and Sales]" );
    c.run();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testVirtualCubeMembers(Context<?> context) throws Exception {
    if ( context.getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) > 0 ) {
      return;
    }
    // ok to use native sql optimization for members on a virtual cube
    TestCase c = new TestCase(context.getConnectionWithDefaultRole(),
      6,
      3,
      "select NON EMPTY {[Measures].[Unit Sales], [Measures].[Warehouse Sales]} ON COLUMNS, "
        + "NON EMPTY {[Product].[Product Family].Members} ON ROWS "
        + "from [Warehouse and Sales]" );
    c.run();
  }

  /**
   * verifies that redundant set braces do not prevent native evaluation for example, {[Store].[Store Name].members }
   * and {{[Store Type].[Store Type].members}}
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  void testNativeCJWithRedundantSetBraces(Context<?> context) {

    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    boolean requestFreshConnection = true;
    checkNative(context,
      0,
      20,
      "select non empty {CrossJoin({[Store].[Store Name].members}, "
        + "                        {{" + STORE_TYPE_LEVEL + ".members}})}"
        + "                         on rows, "
        + "{[Measures].[Store Sqft]} on columns "
        + "from [Store]",
      null,
      requestFreshConnection );
  }

  /**
   * Verifies that CrossJoins with two non native inputs can be natively evaluated.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  void testExpandAllNonNativeInputs(Context<?> context) {
    // This query will not run natively unless the <Dimension>.Children
    // expression is expanded to a member list.
    //
    // Note: Both dimensions only have one hierarchy, which has the All
    // member. <Dimension>.Children is interpreted as the children of
    // the All member.

    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    boolean requestFreshConnection = true;
    checkNative(context,
      0,
      2,
      "select "
        + "NonEmptyCrossJoin([Gender].Children, [Store].Children) on columns "
        + "from [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Gender].[Gender].[F], [Store].[Store].[USA]}\n"
        + "{[Gender].[Gender].[M], [Store].[Store].[USA]}\n"
        + "Row #0: 131,558\n"
        + "Row #0: 135,215\n",
      requestFreshConnection );
  }

  /**
   * Verifies that CrossJoins with one non native inputs can be natively evaluated.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  void testExpandOneNonNativeInput(Context<?> context) {
    // This query will not be evaluated natively unless the Filter
    // expression is expanded to a member list.

    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    boolean requestFreshConnection = true;
    checkNative(context,
      0, 1,
      "With "
        + "Set [*Filtered_Set] as Filter([Product].[Product Name].Members, [Product].CurrentMember IS [Product]"
        + ".[Product Name].[Fast Raisins]) "
        + "Set [*NECJ_Set] as NonEmptyCrossJoin([Store].[Store Country].Members, [*Filtered_Set]) "
        + "select [*NECJ_Set] on columns "
        + "From [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA], [Product].[Product].[Food].[Snack Foods].[Snack Foods].[Dried Fruit].[Fast].[Fast Raisins]}\n"
        + "Row #0: 152\n",
      requestFreshConnection );
  }

  /**
   * Check that the ExpandNonNative does not create Joins with input lists containing large number of members.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.RESULT_LIMIT, value = "2", type = Integer.class)
  void testExpandNonNativeResourceLimitFailure(Context<?> context) {
	context.getCatalogCache().clear();


    try {
      Connection connection = context.getConnectionWithDefaultRole();
      // After the connection: building the catalog reads members too, and a
      // limit this low would already trip there.
      executeQuery(
        "select "
          + "NonEmptyCrossJoin({[Gender].Children, [Gender].[F]}, {[Store].Children, [Store].[Mexico]}) on columns "
          + "from [Sales]", connection );
      fail( "Expected error did not occur" );
    } catch ( Throwable e ) {
      String expectedErrorMsg =
        "Size of CrossJoin result (3) exceeded limit (2)";
      assertEquals( expectedErrorMsg, e.getMessage() );
    }
  }

  /**
   * Verify that the presence of All member in all the inputs disables native evaluation, even when ExpandNonNative is
   * true.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  void testExpandAllMembersInAllInputs(Context<?> context) {
    // This query will not be evaluated natively, even if the Hierarchize
    // expression is expanded to a member list. The reason is that the
    // expanded list contains ALL members.
    checkNotNative(context,
      1, "select NON EMPTY {[Time].[1997]} ON COLUMNS,\n"
        + "       NON EMPTY Crossjoin(Hierarchize(Union({[Store].[All Stores]},\n"
        + "           [Store].[USA].[CA].[San Francisco].[Store 14].Children)), {[Product].[All Products]}) \n"
        + "           ON ROWS\n"
        + "    from [Sales]\n"
        + "    where [Measures].[Unit Sales]",
      "Axis #0:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #1:\n"
        + "{[Time].[Time].[1997]}\n"
        + "Axis #2:\n"
        + "{[Store].[Store].[All Stores], [Product].[Product].[All Products]}\n"
        + "Row #0: 266,773\n" );
  }

  /**
   * Verifies that the presence of calculated member in all the inputs disables native evaluation, even when
   * ExpandNonNative is true.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  void testExpandCalcMembersInAllInputs(Context<?> context) {
    // This query will not be evaluated natively, even if the Hierarchize
    // expression is expanded to a member list. The reason is that the
    // expanded list contains ALL members.
    checkNotNative(context,
      1,
      "With "
        + "Member [Product].[*CTX_MEMBER_SEL~SUM] as 'Sum({[Product].[Product Family].Members})' "
        + "Member [Gender].[*CTX_MEMBER_SEL~SUM] as 'Sum({[Gender].[All Gender]})' "
        + "Select "
        + "NonEmptyCrossJoin({[Gender].[*CTX_MEMBER_SEL~SUM]},{[Product].[*CTX_MEMBER_SEL~SUM]}) "
        + "on columns "
        + "From [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Gender].[Gender].[*CTX_MEMBER_SEL~SUM], [Product].[Product].[*CTX_MEMBER_SEL~SUM]}\n"
        + "Row #0: 266,773\n" );
  }

  /**
   * Check that if both inputs to NECJ are either AllMember(currentMember, defaultMember are also AllMember) or
   * Calcculated member native CJ is not used.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testExpandCalcMemberInputNECJ(Context<?> context) {
    checkNotNative(context,
      1,
      "With \n"
        + "Member [Product].[All Products].[Food].[CalcSum] as \n"
        + "'Sum({[Product].[All Products].[Food]})', SOLVE_ORDER=-100\n"
        + "Select\n"
        + "{[Measures].[Store Cost]} on columns,\n"
        + "NonEmptyCrossJoin({[Product].[All Products].[Food].[CalcSum]},\n"
        + "                  {[Education Level].DefaultMember}) on rows\n"
        + "From [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Cost]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Food].[CalcSum], [Education Level].[Education Level].[All Education Levels]}\n"
        + "Row #0: 163,270.72\n" );
  }

  /**
   * Native evaluation is no longer possible after the fix to {@link #testCjEnumCalcMembersBug()} test.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testExpandCalcMembers(Context<?> context) {
    checkNotNative(context,
      9,
      "with "
        + "member [Store Type].[All Store Types].[S] as sum({[Store Type].[All Store Types]}) "
        + "set [Enum Store Types] as {"
        + "    [Store Type].[All Store Types].[Small Grocery], "
        + "    [Store Type].[All Store Types].[Supermarket], "
        + "    [Store Type].[All Store Types].[HeadQuarters], "
        + "    [Store Type].[All Store Types].[S]} "
        + "set [Filtered Enum Store Types] as Filter([Enum Store Types], [Measures].[Unit Sales] > 0)"
        + "select NonEmptyCrossJoin([Product].[All Products].Children, [Filtered Enum Store Types])  on columns from "
        + "[Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Product].[Product].[Drink], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Product].[Product].[Drink], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Product].[Product].[Drink], [Store Type].[Store Type].[All Store Types].[S]}\n"
        + "{[Product].[Product].[Food], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Product].[Product].[Food], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Product].[Product].[Food], [Store Type].[Store Type].[All Store Types].[S]}\n"
        + "{[Product].[Product].[Non-Consumable], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Product].[Product].[Non-Consumable], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Product].[Product].[Non-Consumable], [Store Type].[Store Type].[All Store Types].[S]}\n"
        + "Row #0: 574\n"
        + "Row #0: 14,092\n"
        + "Row #0: 24,597\n"
        + "Row #0: 4,764\n"
        + "Row #0: 108,188\n"
        + "Row #0: 191,940\n"
        + "Row #0: 1,219\n"
        + "Row #0: 28,275\n"
        + "Row #0: 50,236\n" );
  }

  /**
   * Verify that evaluation is native for expressions with nested non native inputs that preduce MemberList results.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  void testExpandNestedNonNativeInputs(Context<?> context) {

    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    boolean requestFreshConnection = true;
    checkNative(context,
      0,
      6,
      "select "
        + "NonEmptyCrossJoin("
        + "  NonEmptyCrossJoin([Gender].Children, [Store].Children), "
        + "  [Product].Children) on columns "
        + "from [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Gender].[Gender].[F], [Store].[Store].[USA], [Product].[Product].[Drink]}\n"
        + "{[Gender].[Gender].[F], [Store].[Store].[USA], [Product].[Product].[Food]}\n"
        + "{[Gender].[Gender].[F], [Store].[Store].[USA], [Product].[Product].[Non-Consumable]}\n"
        + "{[Gender].[Gender].[M], [Store].[Store].[USA], [Product].[Product].[Drink]}\n"
        + "{[Gender].[Gender].[M], [Store].[Store].[USA], [Product].[Product].[Food]}\n"
        + "{[Gender].[Gender].[M], [Store].[Store].[USA], [Product].[Product].[Non-Consumable]}\n"
        + "Row #0: 12,202\n"
        + "Row #0: 94,814\n"
        + "Row #0: 24,542\n"
        + "Row #0: 12,395\n"
        + "Row #0: 97,126\n"
        + "Row #0: 25,694\n",
      requestFreshConnection );
  }

  /**
   * Verify that a low value for maxConstraints disables native evaluation, even when ExpandNonNative is true.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.MAX_CONSTRAINTS, value = "2", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testExpandLowMaxConstraints(Context<?> context) {
    checkNotNative(context,
      12,
      "select NonEmptyCrossJoin("
        + "    Filter([Store Type].Children, [Measures].[Unit Sales] > 10000), "
        + "    [Product].Children) on columns "
        + "from [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store Type].[Store Type].[Deluxe Supermarket], [Product].[Product].[Drink]}\n"
        + "{[Store Type].[Store Type].[Deluxe Supermarket], [Product].[Product].[Food]}\n"
        + "{[Store Type].[Store Type].[Deluxe Supermarket], [Product].[Product].[Non-Consumable]}\n"
        + "{[Store Type].[Store Type].[Gourmet Supermarket], [Product].[Product].[Drink]}\n"
        + "{[Store Type].[Store Type].[Gourmet Supermarket], [Product].[Product].[Food]}\n"
        + "{[Store Type].[Store Type].[Gourmet Supermarket], [Product].[Product].[Non-Consumable]}\n"
        + "{[Store Type].[Store Type].[Mid-Size Grocery], [Product].[Product].[Drink]}\n"
        + "{[Store Type].[Store Type].[Mid-Size Grocery], [Product].[Product].[Food]}\n"
        + "{[Store Type].[Store Type].[Mid-Size Grocery], [Product].[Product].[Non-Consumable]}\n"
        + "{[Store Type].[Store Type].[Supermarket], [Product].[Product].[Drink]}\n"
        + "{[Store Type].[Store Type].[Supermarket], [Product].[Product].[Food]}\n"
        + "{[Store Type].[Store Type].[Supermarket], [Product].[Product].[Non-Consumable]}\n"
        + "Row #0: 6,827\n"
        + "Row #0: 55,358\n"
        + "Row #0: 14,652\n"
        + "Row #0: 1,945\n"
        + "Row #0: 15,438\n"
        + "Row #0: 3,950\n"
        + "Row #0: 1,159\n"
        + "Row #0: 8,192\n"
        + "Row #0: 2,140\n"
        + "Row #0: 14,092\n"
        + "Row #0: 108,188\n"
        + "Row #0: 28,275\n" );
  }

  /**
   * Verify that native evaluation is not enabled if expanded member list will contain members from different levels,
   * even if ExpandNonNative is set.
   */
  @Test
  @RolapContextTest(FoodmartTestInstance.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testExpandDifferentLevels(Context<?> context) {
    checkNotNative(context,
      278,
      "select NonEmptyCrossJoin("
        + "    Descendants([Customers].[All Customers].[USA].[WA].[Yakima]), "
        + "    [Product].Children) on columns "
        + "from [Sales]",
      null );
  }

  /**
   * Verify that native evaluation is turned off for tuple inputs, even if ExpandNonNative is set.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testExpandTupleInputs1(Context<?> context) {
    checkNotNative(context,
      1,
      "with "
        + "set [Tuple Set] as {([Store Type].[All Store Types].[HeadQuarters], [Product].[All Products].[Drink]), "
        + "([Store Type].[All Store Types].[Supermarket], [Product].[All Products].[Food])} "
        + "set [Filtered Tuple Set] as Filter([Tuple Set], 1=1) "
        + "set [NECJ] as NonEmptyCrossJoin([Store].Children, [Filtered Tuple Set]) "
        + "select [NECJ] on columns from [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA], [Store Type].[Store Type].[Supermarket], [Product].[Product].[Food]}\n"
        + "Row #0: 108,188\n" );
  }

  /**
   * Verify that native evaluation is turned off for tuple inputs, even if ExpandNonNative is set.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testExpandTupleInputs2(Context<?> context) {
    checkNotNative(context,
      1,
      "with "
        + "set [Tuple Set] as {([Store Type].[All Store Types].[HeadQuarters], [Product].[All Products].[Drink]), "
        + "([Store Type].[All Store Types].[Supermarket], [Product].[All Products].[Food])} "
        + "set [Filtered Tuple Set] as Filter([Tuple Set], 1=1) "
        + "set [NECJ] as NonEmptyCrossJoin([Filtered Tuple Set], [Store].Children) "
        + "select [NECJ] on columns from [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store Type].[Store Type].[Supermarket], [Product].[Product].[Food], [Store].[Store].[USA]}\n"
        + "Row #0: 108,188\n" );
  }

  /**
   * Verify that native evaluation is on when ExpendNonNative is set, even if the input list is empty.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testExpandWithOneEmptyInput(Context<?> context) {
    boolean requestFreshConnection = true;
    // Query should return empty result.
    checkNative(context,
      0,
      0,
      "With "
        + "Set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Gender],[*BASE_MEMBERS_Product])' "
        + "Set [*BASE_MEMBERS_Measures] as '{[Measures].[*FORMATTED_MEASURE_0]}' "
        + "Set [*BASE_MEMBERS_Gender] as 'Filter([Gender].[Gender].Members,[Gender].CurrentMember.Name Matches "
        + "(\"abc\"))' "
        + "Set [*NATIVE_MEMBERS_Gender] as 'Generate([*NATIVE_CJ_SET], {[Gender].CurrentMember})' "
        + "Set [*BASE_MEMBERS_Product] as '[Product].[Product Name].Members' "
        + "Set [*NATIVE_MEMBERS_Product] as 'Generate([*NATIVE_CJ_SET], {[Product].CurrentMember})' "
        + "Member [Measures].[*FORMATTED_MEASURE_0] as '[Measures].[Unit Sales]', FORMAT_STRING = '#,##0', "
        + "SOLVE_ORDER=400 "
        + "Select "
        + "[*BASE_MEMBERS_Measures] on columns, "
        + "Non Empty Generate([*NATIVE_CJ_SET], {([Gender].CurrentMember,[Product].CurrentMember)}) on rows "
        + "From [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
        + "Axis #2:\n",
      requestFreshConnection );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testExpandWithTwoEmptyInputs(Context<?> context) {
    context.getConnectionWithDefaultRole().getCacheControl( null ).flushSchemaCache();
    // Query should return empty result.
    checkNotNative(context,
      0,
      "With "
        + "Set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Gender],[*BASE_MEMBERS_Product])' "
        + "Set [*BASE_MEMBERS_Measures] as '{[Measures].[*FORMATTED_MEASURE_0]}' "
        + "Set [*BASE_MEMBERS_Gender] as '{}' "
        + "Set [*NATIVE_MEMBERS_Gender] as 'Generate([*NATIVE_CJ_SET], {[Gender].CurrentMember})' "
        + "Set [*BASE_MEMBERS_Product] as '{}' "
        + "Set [*NATIVE_MEMBERS_Product] as 'Generate([*NATIVE_CJ_SET], {[Product].CurrentMember})' "
        + "Member [Measures].[*FORMATTED_MEASURE_0] as '[Measures].[Unit Sales]', FORMAT_STRING = '#,##0', "
        + "SOLVE_ORDER=400 "
        + "Select "
        + "[*BASE_MEMBERS_Measures] on columns, "
        + "Non Empty Generate([*NATIVE_CJ_SET], {([Gender].CurrentMember,[Product].CurrentMember)}) on rows "
        + "From [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
        + "Axis #2:\n" );
  }

  /**
   * Verify that native MemberLists inputs are subject to SQL constriant limitation. If mondrian.rolap.maxConstraints is
   * set too low, native evaluations will be turned off.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.MAX_CONSTRAINTS, value = "2", type = Integer.class)
  void testEnumLowMaxConstraints(Context<?> context) {
    checkNotNative(context,
      12,
      "with "
        + "set [All Store Types] as {"
        + "[Store Type].[Deluxe Supermarket], "
        + "[Store Type].[Gourmet Supermarket], "
        + "[Store Type].[Mid-Size Grocery], "
        + "[Store Type].[Small Grocery], "
        + "[Store Type].[Supermarket]} "
        + "set [All Products] as {"
        + "[Product].[Drink], "
        + "[Product].[Food], "
        + "[Product].[Non-Consumable]} "
        + "select "
        + "NonEmptyCrossJoin("
        + "Filter([All Store Types], ([Measures].[Unit Sales] > 10000)), "
        + "[All Products]) on columns "
        + "from [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store Type].[Store Type].[Deluxe Supermarket], [Product].[Product].[Drink]}\n"
        + "{[Store Type].[Store Type].[Deluxe Supermarket], [Product].[Product].[Food]}\n"
        + "{[Store Type].[Store Type].[Deluxe Supermarket], [Product].[Product].[Non-Consumable]}\n"
        + "{[Store Type].[Store Type].[Gourmet Supermarket], [Product].[Product].[Drink]}\n"
        + "{[Store Type].[Store Type].[Gourmet Supermarket], [Product].[Product].[Food]}\n"
        + "{[Store Type].[Store Type].[Gourmet Supermarket], [Product].[Product].[Non-Consumable]}\n"
        + "{[Store Type].[Store Type].[Mid-Size Grocery], [Product].[Product].[Drink]}\n"
        + "{[Store Type].[Store Type].[Mid-Size Grocery], [Product].[Product].[Food]}\n"
        + "{[Store Type].[Store Type].[Mid-Size Grocery], [Product].[Product].[Non-Consumable]}\n"
        + "{[Store Type].[Store Type].[Supermarket], [Product].[Product].[Drink]}\n"
        + "{[Store Type].[Store Type].[Supermarket], [Product].[Product].[Food]}\n"
        + "{[Store Type].[Store Type].[Supermarket], [Product].[Product].[Non-Consumable]}\n"
        + "Row #0: 6,827\n"
        + "Row #0: 55,358\n"
        + "Row #0: 14,652\n"
        + "Row #0: 1,945\n"
        + "Row #0: 15,438\n"
        + "Row #0: 3,950\n"
        + "Row #0: 1,159\n"
        + "Row #0: 8,192\n"
        + "Row #0: 2,140\n"
        + "Row #0: 14,092\n"
        + "Row #0: 108,188\n"
        + "Row #0: 28,275\n" );
  }

  /**
   * Verify that the presence of All member in all the inputs disables native evaluation.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  void testAllMembersNECJ1(Context<?> context) {
    // This query cannot be evaluated natively because of the "All" member.
    checkNotNative(context,
      1,
      "select "
        + "NonEmptyCrossJoin({[Store].[All Stores]}, {[Product].[All Products]}) on columns "
        + "from [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[All Stores], [Product].[Product].[All Products]}\n"
        + "Row #0: 266,773\n" );
  }

  /**
   * Verify that the native evaluation is possible if one input does not contain the All member.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  void testAllMembersNECJ2(Context<?> context) {
    // This query can be evaluated natively because there is at least one
    // non "All" member.
    //
    // It can also be rewritten to use
    // Filter([Product].Children, Is
    // NotEmpty([Measures].[Unit Sales]))
    // which can be natively evaluated

    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    boolean requestFreshConnection = true;
    checkNative(context,
      0,
      3,
      "select "
        + "NonEmptyCrossJoin([Product].[All Products].Children, {[Store].[All Stores]}) on columns "
        + "from [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Product].[Product].[Drink], [Store].[Store].[All Stores]}\n"
        + "{[Product].[Product].[Food], [Store].[Store].[All Stores]}\n"
        + "{[Product].[Product].[Non-Consumable], [Store].[Store].[All Stores]}\n"
        + "Row #0: 24,597\n"
        + "Row #0: 191,940\n"
        + "Row #0: 50,236\n",
      requestFreshConnection );
  }

  /**
   * getMembersInLevel where Level = (All)
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testAllLevelMembers(Context<?> context) {
    checkNative(context,
      14,
      14,
      "select {[Measures].[Store Sales]} ON COLUMNS, "
        + "NON EMPTY Crossjoin([Product].[(All)].Members, [Promotion Media].[All Media].Children) ON ROWS "
        + "from [Sales]" );
  }

  /**
   * enum sets {} containing ALL
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjDescendantsEnumAllOnly(Context<?> context) {
    checkNative(context,
      9,
      9,
      "select {[Measures].[Unit Sales]} ON COLUMNS, "
        + "NON EMPTY Crossjoin("
        + "  Descendants([Customers].[All Customers].[USA], [Customers].[City]), "
        + "  {[Product].[All Products]}) ON ROWS " + "from [Sales] "
        + "where ([Promotions].[All Promotions].[Bag Stuffers])" );
  }

  /**
   * checks that crossjoin returns a modifiable copy from cache because its modified during sort
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testResultIsModifyableCopy(Context<?> context) {
    checkNative(context,
      3,
      3,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Order("
        + "        CrossJoin([Customers].[All Customers].[USA].children, [Promotions].[Promotion Name].Members), "
        + "        [Measures].[Store Sales]) ON ROWS"
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  /**
   * Checks that TopCount is executed natively unless disabled.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
  void testNativeTopCount(Context<?> context) {
    switch ( getDatabaseProduct(getDialect(context.getConnectionWithDefaultRole()).name()) ) {
      case INFOBRIGHT:
        // Hits same Infobright bug as NamedSetTest.testNamedSetOnMember.
        return;
    }

    String query =
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY TopCount("
        + "        CrossJoin([Customers].[All Customers].[USA].children, [Promotions].[Promotion Name].Members), "
        + "        3, (3 * [Measures].[Store Sales]) - 100) ON ROWS"
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])";


    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    boolean requestFreshConnection = true;
    checkNative(context, 3, 3, query, null, requestFreshConnection );
  }

  /**
   * Checks that TopCount is executed natively with calculated member.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
  void testCmNativeTopCount(Context<?> context) {
    switch ( getDatabaseProduct(getDialect(context.getConnectionWithDefaultRole()).name()) ) {
      case INFOBRIGHT:
        // Hits same Infobright bug as NamedSetTest.testNamedSetOnMember.
        return;
    }
    String query =
      "with member [Measures].[Store Profit Rate] as '([Measures].[Store Sales]-[Measures].[Store Cost])/[Measures]"
        + ".[Store Cost]', format = '#.00%' "
        + "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY TopCount("
        + "        [Customers].[All Customers].[USA].children, "
        + "        3, [Measures].[Store Profit Rate] / 2) ON ROWS"
        + " from [Sales]";


    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    boolean requestFreshConnection = true;
    checkNative(context, 3, 3, query, null, requestFreshConnection );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMeasureAndAggregateInSlicer(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member [Store Type].[All Store Types].[All Types] as 'Aggregate({[Store Type].[All Store Types].[Deluxe "
        + "Supermarket],  "
        + "[Store Type].[All Store Types].[Gourmet Supermarket],  "
        + "[Store Type].[All Store Types].[HeadQuarters],  "
        + "[Store Type].[All Store Types].[Mid-Size Grocery],  "
        + "[Store Type].[All Store Types].[Small Grocery],  "
        + "[Store Type].[All Store Types].[Supermarket]})'  "
        + "select NON EMPTY {[Time].[1997]} ON COLUMNS,   "
        + "NON EMPTY [Store].[All Stores].[USA].[CA].Children ON ROWS   "
        + "from [Sales] "
        + "where ([Store Type].[All Store Types].[All Types], [Measures].[Unit Sales], [Customers].[All Customers]"
        + ".[USA], [Product].[All Products].[Drink])  ").returnsGrid(
      "Axis #0:\n"
        + "{[Store Type].[Store Type].[All Store Types].[All Types], [Measures].[Unit Sales], [Customers].[Customers].[USA], [Product].[Product]"
        + ".[Drink]}\n"
        + "Axis #1:\n"
        + "{[Time].[Time].[1997]}\n"
        + "Axis #2:\n"
        + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
        + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
        + "{[Store].[Store].[USA].[CA].[San Diego]}\n"
        + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
        + "Row #0: 1,945\n"
        + "Row #1: 2,422\n"
        + "Row #2: 2,560\n"
        + "Row #3: 175\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMeasureInSlicer(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select NON EMPTY {[Time].[1997]} ON COLUMNS,   "
        + "NON EMPTY [Store].[All Stores].[USA].[CA].Children ON ROWS  "
        + "from [Sales]  "
        + "where ([Measures].[Unit Sales], [Customers].[All Customers].[USA], [Product].[All Products].[Drink])").returnsGrid(
      "Axis #0:\n"
        + "{[Measures].[Unit Sales], [Customers].[Customers].[USA], [Product].[Product].[Drink]}\n"
        + "Axis #1:\n"
        + "{[Time].[Time].[1997]}\n"
        + "Axis #2:\n"
        + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
        + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
        + "{[Store].[Store].[USA].[CA].[San Diego]}\n"
        + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
        + "Row #0: 1,945\n"
        + "Row #1: 2,422\n"
        + "Row #2: 2,560\n"
        + "Row #3: 175\n" );
  }

  /**
   * Calc Member in TopCount: this topcount can not be calculated native because its set contains calculated members.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCmInTopCount(Context<?> context) {
    checkNotNative(context,
      1,
      "with member [Time].[Time].[Jan] as  "
        + "'Aggregate({[Time].[1998].[Q1].[1], [Time].[1997].[Q1].[1]})'  "
        + "select NON EMPTY {[Measures].[Unit Sales]} ON columns,  "
        + "NON EMPTY TopCount({[Time].[Jan]}, 2) ON rows from [Sales] " );
  }

  /**
   * Calc member in slicer cannot be executed natively.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCmInSlicer(Context<?> context) {
    checkNotNative(context,
      3,
      "with member [Time].[Time].[Jan] as  "
        + "'Aggregate({[Time].[1998].[Q1].[1], [Time].[1997].[Q1].[1]})'  "
        + "select NON EMPTY {[Measures].[Unit Sales]} ON columns,  "
        + "NON EMPTY [Product].Children ON rows from [Sales] "
        + "where ([Time].[Jan]) " );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCmInSlicerResults(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member [Time].[Time].[Jan] as  "
        + "'Aggregate({[Time].[1998].[Q1].[1], [Time].[1997].[Q1].[1]})'  "
        + "select NON EMPTY {[Measures].[Unit Sales]} ON columns,  "
        + "NON EMPTY [Product].Children ON rows from [Sales] "
        + "where ([Time].[Jan]) ").returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[Jan]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Drink]}\n"
        + "{[Product].[Product].[Food]}\n"
        + "{[Product].[Product].[Non-Consumable]}\n"
        + "Row #0: 1,910\n"
        + "Row #1: 15,604\n"
        + "Row #2: 4,114\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testSetInSlicerResults(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select NON EMPTY {[Measures].[Unit Sales]} ON columns,  "
        + "NON EMPTY [Product].Children ON rows from [Sales] "
        + "where {[Time].[1998].[Q1].[1], [Time].[1997].[Q1].[1]} ").returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[1998].[Q1].[1]}\n"
        + "{[Time].[Time].[1997].[Q1].[1]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Drink]}\n"
        + "{[Product].[Product].[Food]}\n"
        + "{[Product].[Product].[Non-Consumable]}\n"
        + "Row #0: 1,910\n"
        + "Row #1: 15,604\n"
        + "Row #2: 4,114\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjMembersMembersMembers(Context<?> context) {
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin("
        + "    Crossjoin("
        + "        [Customers].[Name].Members,"
        + "        [Product].[Product Name].Members), "
        + "    [Promotions].[Promotion Name].Members) ON rows "
        + " from [Sales] where ("
        + "  [Store].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.NonEmptyTestModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjMembersWithHideIfBlankLeafAndNoAll(Context<?> context) {
    // No 'all' level, and ragged because [Product Name] is hidden if
    // blank.  Native evaluation should be able to handle this query.
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin("
        + "    Crossjoin("
        + "        [Customers].[Name].Members,"
        + "        [Product Ragged].[Product Name].Members), "
        + "    [Promotions].[Promotion Name].Members) ON rows "
        + " from [Sales] where ("
        + "  [Store].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, NonEmptyTestModifier2HideIfBlankName.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjMembersWithHideIfBlankLeaf(Context<?> context) {
      // [Product Name] can be hidden if it is blank, but native evaluation
    // should be able to handle the query.
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin("
        + "    Crossjoin("
        + "        [Customers].[Name].Members,"
        + "        [Product Ragged].[Product Name].Members), "
        + "    [Promotions].[Promotion Name].Members) ON rows "
        + " from [Sales] where ("
        + "  [Store].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, NonEmptyTestModifier2HideIfParentsName.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjMembersWithHideIfParentsNameLeaf(Context<?> context) {
      // [Product Name] can be hidden if it it matches its parent name, so
    // native evaluation can not handle this query.
    checkNotNative(context,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin("
        + "    Crossjoin("
        + "        [Customers].[Name].Members,"
        + "        [Product Ragged].[Product Name].Members), "
        + "    [Promotions].[Promotion Name].Members) ON rows "
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.NonEmptyTestModifier3.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjMembersWithHideIfBlankNameAncestor(Context<?> context) {
    // Since the parent of [Product Name] can be hidden, native evaluation
    // can't handle the query.
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin("
        + "    Crossjoin("
        + "        [Customers].[Name].Members,"
        + "        [Product Ragged].[Product Name].Members), "
        + "    [Promotions].[Promotion Name].Members) ON rows "
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.NonEmptyTestModifier3.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjMembersWithHideIfParentsNameAncestor(Context<?> context) {
      // Since the parent of [Product Name] can be hidden, native evaluation
    // can't handle the query.
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin("
        + "    Crossjoin("
        + "        [Customers].[Name].Members,"
        + "        [Product Ragged].[Product Name].Members), "
        + "    [Promotions].[Promotion Name].Members) ON rows "
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, NonEmptyTestModifier2HideIfBlankName.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjEnumWithHideIfBlankLeaf(Context<?> context) {
      // [Product Name] can be hidden if it is blank, but native evaluation
    // should be able to handle the query.
    // Note there's an existing bug with result ordering in native
    // non-empty evaluation of enumerations. This test intentionally
    // avoids this bug by explicitly lilsting [High Top Cauliflower]
    // before [Sphinx Bagels].
    checkNative(context,
      999,
      7,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin("
        + "    Crossjoin("
        + "        [Customers].[Name].Members,"
        + "        { [Product Ragged].[Kiwi].[Kiwi Scallops],"
        + "          [Product Ragged].[Fast].[Fast Avocado Dip],"
        + "          [Product Ragged].[High Top].[High Top Lemons],"
        + "          [Product Ragged].[Moms].[Moms Sliced Turkey],"
        + "          [Product Ragged].[High Top].[High Top Cauliflower],"
        + "          [Product Ragged].[Sphinx].[Sphinx Bagels]"
        + "        }), "
        + "    [Promotions].[Promotion Name].Members) ON rows "
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  /**
   * use SQL even when all members are known
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjEnumEnum(Context<?> context) {
    checkNative(context,
      4,
      4,
      "select {[Measures].[Unit Sales]} ON COLUMNS, "
        +
        "NonEmptyCrossjoin({[Product].[All Products].[Drink].[Beverages], [Product].[All Products].[Drink].[Dairy]}, "
        + "{[Customers].[All Customers].[USA].[OR].[Portland], [Customers].[All Customers].[USA].[OR].[Salem]}) ON "
        + "ROWS "
        + "from [Sales] " );
  }

  /**
   * Set containing only null member should not prevent usage of native.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.IGNORE_INVALID_MEMBERS_DURING_QUERY, value = "true", type = Boolean.class)
  void testCjNullInEnum(Context<?> context) {
    checkNative(context,
      20,
      0,
      "select {[Measures].[Unit Sales]} ON COLUMNS, "
        + "NON EMPTY Crossjoin({[Gender].[All Gender].[emale]}, [Customers].[All Customers].[USA].children) ON "
        + "ROWS "
        + "from [Sales] " );
  }

  /**
   * enum sets {} containing members from different levels can not be computed natively currently.
   */
  @Test
  void testCjDescendantsEnumAll(Context<?> context) {
    checkNotNative(context,
      13,
      "select {[Measures].[Unit Sales]} ON COLUMNS, "
        + "NON EMPTY Crossjoin("
        + "  Descendants([Customers].[All Customers].[USA], [Customers].[City]), "
        + "  {[Product].[All Products], [Product].[All Products].[Drink].[Dairy]}) ON ROWS "
        + "from [Sales] "
        + "where ([Promotions].[All Promotions].[Bag Stuffers])" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjDescendantsEnum(Context<?> context) {
    checkNative(context,
      11,
      11,
      "select {[Measures].[Unit Sales]} ON COLUMNS, "
        + "NON EMPTY Crossjoin("
        + "  Descendants([Customers].[All Customers].[USA], [Customers].[City]), "
        + "  {[Product].[All Products].[Drink].[Beverages], [Product].[All Products].[Drink].[Dairy]}) ON ROWS "
        + "from [Sales] "
        + "where ([Promotions].[All Promotions].[Bag Stuffers])" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjEnumChildren(Context<?> context) {
    // Make sure maxConstraint settting is high enough
    checkNative(context,
      3,
      3,
      "select {[Measures].[Unit Sales]} ON COLUMNS, "
        + "NON EMPTY Crossjoin("
        + "  {[Product].[All Products].[Drink].[Beverages], [Product].[All Products].[Drink].[Dairy]}, "
        + "  [Customers].[All Customers].[USA].[WA].Children) ON ROWS "
        + "from [Sales] "
        + "where ([Promotions].[All Promotions].[Bag Stuffers])" );
  }

  /**
   * {} contains members from different levels, this can not be handled by the current native crossjoin.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjEnumDifferentLevelsChildren(Context<?> context) {
    // Don't run the test if we're testing expression dependencies.
    // Expression dependencies cause spurious interval calls to
    // 'level.getMembers()' which create false negatives in this test.
    if ( context.getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) > 0 ) {
      return;
    }

    TestCase c = new TestCase(context.getConnectionWithDefaultRole(),
      8,
      5,
      "select {[Measures].[Unit Sales]} ON COLUMNS, "
        + "NON EMPTY Crossjoin("
        + "  {[Product].[All Products].[Food], [Product].[All Products].[Drink].[Dairy]}, "
        + "  [Customers].[All Customers].[USA].[WA].Children) ON ROWS "
        + "from [Sales] "
        + "where ([Promotions].[All Promotions].[Bag Stuffers])" );
    c.run();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjDescendantsMembers(Context<?> context)  {
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + " NON EMPTY Crossjoin("
        + "   Descendants([Customers].[All Customers].[USA].[CA], [Customers].[Name]),"
        + "     [Product].[Product Name].Members) ON rows "
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  void testCjMembersDescendants(Context<?> context)  {
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + " NON EMPTY Crossjoin("
        + "  [Product].[Product Name].Members,"
        + "  Descendants([Customers].[All Customers].[USA].[CA], [Customers].[Name])) ON rows "
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  // testcase for bug MONDRIAN-506
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjMembersDescendantsWithNumericArgument(Context<?> context)  {
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + " NON EMPTY Crossjoin("
        + "  {[Product].[Product Name].Members},"
        + "  {Descendants([Customers].[All Customers].[USA].[CA], 2)}) ON rows "
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  void testCjChildrenMembers(Context<?> context)  {
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin([Customers].[All Customers].[USA].[CA].children,"
        + "    [Product].[Product Name].Members) ON rows "
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjMembersChildren(Context<?> context)  {
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin([Product].[Product Name].Members,"
        + "    [Customers].[All Customers].[USA].[CA].children) ON rows "
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjMembersMembers(Context<?> context)  {
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin([Customers].[Name].Members,"
        + "    [Product].[Product Name].Members) ON rows "
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjChildrenChildren(Context<?> context)  {
    checkNative(context,
      3,
      3,
      "select {[Measures].[Store Sales]} on columns, "
        + "  NON EMPTY Crossjoin("
        + "    [Product].[All Products].[Drink].[Alcoholic Beverages].[Beer and Wine].[Wine].children, "
        + "    [Customers].[All Customers].[USA].[CA].CHILDREN) ON rows"
        + " from [Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  /**
   * Checks that multi-level member list generates compact form of SQL where clause: (1) Use IN list if possible (2)
   * Group members sharing the same parent (3) Only need to compare up to the first unique parent level.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMultiLevelMemberConstraintNonNullParent(Context<?> context)  {
    String query =
      "with "
        + "set [Filtered Store City Set] as "
        + "{[Store].[USA].[OR].[Portland], "
        + " [Store].[USA].[OR].[Salem], "
        + " [Store].[USA].[CA].[San Francisco], "
        + " [Store].[USA].[WA].[Tacoma]} "
        + "set [NECJ] as NonEmptyCrossJoin([Filtered Store City Set], {[Product].[Product Family].Food}) "
        + "select [NECJ] on columns from [Sales]";

    String necjSqlDerby =
      "select "
        + "\"store\".\"store_country\", \"store\".\"store_state\", \"store\".\"store_city\", "
        + "\"product_class\".\"product_family\" "
        + "from "
        + "\"store\" as \"store\" "
        + "join \"sales_fact_1997\" as \"sales_fact_1997\" on \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\" "
        + "join \"product\" as \"product\" on \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" "
        + "join \"product_class\" as \"product_class\" on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" "
        + "where "
        + "((\"store\".\"store_state\" = 'OR' and \"store\".\"store_city\" in ('Portland', 'Salem'))"
        + " or (\"store\".\"store_state\" = 'CA' and \"store\".\"store_city\" = 'San Francisco')"
        + " or (\"store\".\"store_state\" = 'WA' and \"store\".\"store_city\" = 'Tacoma')) "
        + "and (\"product_class\".\"product_family\" = 'Food') "
        + "group by \"store\".\"store_country\", \"store\".\"store_state\", \"store\".\"store_city\", "
        + "\"product_class\".\"product_family\" "
        + "order by CASE WHEN \"store\".\"store_country\" IS NULL THEN 1 ELSE 0 END, \"store\".\"store_country\" ASC,"
        + " CASE WHEN \"store\".\"store_state\" IS NULL THEN 1 ELSE 0 END, \"store\".\"store_state\" ASC, CASE WHEN "
        + "\"store\".\"store_city\" IS NULL THEN 1 ELSE 0 END, \"store\".\"store_city\" ASC, CASE WHEN "
        + "\"product_class\".\"product_family\" IS NULL THEN 1 ELSE 0 END, \"product_class\".\"product_family\" ASC";

    String necjSqlMySql =
      "select "
        + "`store`.`store_country` as `c0`, `store`.`store_state` as `c1`, "
        + "`store`.`store_city` as `c2`, `product_class`.`product_family` as `c3` "
        + "from "
        + "`store` as `store` "
        + "join `sales_fact_1997` as `sales_fact_1997` on `sales_fact_1997`.`store_id` = `store`.`store_id` "
        + "join `product` as `product` on `sales_fact_1997`.`product_id` = `product`.`product_id` "
        + "join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id` "
        + "where "
        + "((`store`.`store_city`, `store`.`store_state`) in (('Portland', 'OR'), ('Salem', 'OR'), ('San "
        + "Francisco', 'CA'), ('Tacoma', 'WA'))) "
        + "and (`product_class`.`product_family` = 'Food') "
        + "group by `store`.`store_country`, `store`.`store_state`, `store`.`store_city`, `product_class`"
        + ".`product_family` order by "
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "ISNULL(`c0`) ASC, `c0` ASC, ISNULL(`c1`) ASC, `c1` ASC, "
        + "ISNULL(`c2`) ASC, `c2` ASC, ISNULL(`c3`) ASC, `c3` ASC"
        :
        "ISNULL(`store`.`store_country`) ASC, `store`.`store_country` ASC, ISNULL(`store`.`store_state`) ASC, `store`"
          + ".`store_state` ASC, "
          + "ISNULL(`store`.`store_city`) ASC, `store`.`store_city` ASC, ISNULL(`product_class`.`product_family`) "
          + "ASC, `product_class`.`product_family` ASC" );

    if ( context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
      && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) ) {
      // slightly different sql expected, uses agg table now for join
      necjSqlMySql = necjSqlMySql.replaceAll(
        "sales_fact_1997", "agg_c_14_sales_fact_1997" );
      necjSqlDerby = necjSqlDerby.replaceAll(
        "sales_fact_1997", "agg_c_14_sales_fact_1997" );
    }

    if ( !context.getConfigValue(ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS, ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS_DEFAULT_VALUE, Boolean.class) ) {
      necjSqlMySql = necjSqlMySql.replaceAll(
        "`product` as `product`, `product_class` as `product_class`",
        "`product_class` as `product_class`, `product` as `product`" );
      necjSqlMySql = necjSqlMySql.replaceAll(
        "`product`.`product_class_id` = `product_class`.`product_class_id` and "
          + "`sales_fact_1997`.`product_id` = `product`.`product_id` and ",
        "`sales_fact_1997`.`product_id` = `product`.`product_id` and "
          + "`product`.`product_class_id` = `product_class`.`product_class_id` and " );
      necjSqlDerby = necjSqlDerby.replaceAll(
        "\"product\" as \"product\", \"product_class\" as \"product_class\"",
        "\"product_class\" as \"product_class\", \"product\" as \"product\"" );
      necjSqlDerby = necjSqlDerby.replaceAll(
        "\"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" and "
          + "\"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" and ",
        "\"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" and "
          + "\"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" and " );
    }

    SqlPattern[] patterns = {
      new SqlPattern(
        DatabaseProduct.DERBY, necjSqlDerby, necjSqlDerby ),
      new SqlPattern(
        DatabaseProduct.MYSQL, necjSqlMySql, necjSqlMySql )
    };

    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns ).verify();
  }

  /**
   * Checks that multi-level member list generates compact form of SQL where clause: (1) Use IN list if possible(not
   * possible if there are null values because NULLs in IN lists do not match) (2) Group members sharing the same
   * parent, including parents with NULLs. (3) If parent levels include NULLs, comparision includes any unique level.
   */
  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, TestMultiLevelMemberConstraintNullParentModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testMultiLevelMemberConstraintNullParent(Context<?> context)  {
    if ( !context.getConfigValue(ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS, ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS_DEFAULT_VALUE, Boolean.class) ){
      return;
    }
    String query =
      "with\n"
        + "set [Filtered Warehouse Set] as "
        + "{[Warehouse2].[#null].[#null].[5617 Saclan Terrace].[Arnold and Sons],"
        + " [Warehouse2].[#null].[#null].[3377 Coachman Place].[Jones International]} "
        + "set [NECJ] as NonEmptyCrossJoin([Filtered Warehouse Set], {[Product].[Product Family].Food}) "
        + "select [NECJ] on columns from [Warehouse2]";

    String necjSqlMySql =
      "select\n"
        + "    `warehouse`.`wa_address3` as `c0`,\n"
        + "    `warehouse`.`wa_address2` as `c1`,\n"
        + "    `warehouse`.`wa_address1` as `c2`,\n"
        + "    `warehouse`.`warehouse_name` as `c3`,\n"
        + "    `product_class`.`product_family` as `c4`\n"
        + "from\n"
        + "    `warehouse` as `warehouse` join `inventory_fact_1997` as `inventory_fact_1997` on `inventory_fact_1997`.`warehouse_id` = `warehouse`.`warehouse_id` join `product` as `product` on `inventory_fact_1997`.`product_id` = `product`.`product_id` join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "where\n"
        + "    ((`warehouse`.`wa_address2` is null and (`warehouse`.`warehouse_name`, `warehouse`.`wa_address1`) "
        + "in (('Arnold and Sons', '5617 Saclan Terrace'), ('Jones International', '3377 Coachman Place'))))\n"
        + "and\n"
        + "    (`product_class`.`product_family` = 'Food')\n"
        + "group by\n"
        + "    `warehouse`.`wa_address3`,\n"
        + "    `warehouse`.`wa_address2`,\n"
        + "    `warehouse`.`wa_address1`,\n"
        + "    `warehouse`.`warehouse_name`,\n"
        + "    `product_class`.`product_family`\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
        + "    ISNULL(`c3`) ASC, `c3` ASC,\n"
        + "    ISNULL(`c4`) ASC, `c4` ASC"
        : "    ISNULL(`warehouse`.`wa_address3`) ASC, `warehouse`.`wa_address3` ASC,\n"
        + "    ISNULL(`warehouse`.`wa_address2`) ASC, `warehouse`.`wa_address2` ASC,\n"
        + "    ISNULL(`warehouse`.`wa_address1`) ASC, `warehouse`.`wa_address1` ASC,\n"
        + "    ISNULL(`warehouse`.`warehouse_name`) ASC, `warehouse`.`warehouse_name` ASC,\n"
        + "    ISNULL(`product_class`.`product_family`) ASC, `product_class`.`product_family` ASC" );
    SqlPattern[] patterns = {
      new SqlPattern(
        DatabaseProduct.MYSQL, necjSqlMySql, necjSqlMySql )
    };

    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns ).verify();
  }

  /**
   * Check that multi-level member list generates compact form of SQL where clause: (1) Use IN list if possible(not
   * possible if there are null values because NULLs in IN lists do not match) (2) Group members sharing the same
   * parent, including parents with NULLs. (3) If parent levels include NULLs, comparision includes any unique level.
   * (4) Can handle predicates correctly if the member list contains both NULL and non NULL parent levels.
   */
  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, TestMultiLevelMemberConstraintMixedNullNonNullParentModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testMultiLevelMemberConstraintMixedNullNonNullParent(Context<?> context)  {
    if ( !context.getConfigValue(ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS, ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS_DEFAULT_VALUE, Boolean.class) ) {
      return;
    }
    String query =
      "with\n"
        + "set [Filtered Warehouse Set] as "
        + "{[Warehouse2].[#null].[234 West Covina Pkwy].[Freeman And Co],"
        + " [Warehouse2].[971-555-6213].[3377 Coachman Place].[Jones International]} "
        + "set [NECJ] as NonEmptyCrossJoin([Filtered Warehouse Set], {[Product].[Product Family].Food}) "
        + "select [NECJ] on columns from [Warehouse2]";

    String necjSqlMySql =
      "select\n"
        + "    `warehouse`.`warehouse_fax` as `c0`,\n"
        + "    `warehouse`.`wa_address1` as `c1`,\n"
        + "    `warehouse`.`warehouse_name` as `c2`,\n"
        + "    `product_class`.`product_family` as `c3`\n"
        + "from\n"
        + "    `warehouse` as `warehouse` join `inventory_fact_1997` as `inventory_fact_1997` on `inventory_fact_1997`.`warehouse_id` = `warehouse`.`warehouse_id` join `product` as `product` on `inventory_fact_1997`.`product_id` = `product`.`product_id` join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "where\n"
        + "    ((`warehouse`.`warehouse_name`, `warehouse`.`wa_address1`, `warehouse`.`warehouse_fax`) in (('Jones "
        + "International', '3377 Coachman Place', '971-555-6213')) or (`warehouse`.`warehouse_fax` is null and "
        + "(`warehouse`.`warehouse_name`, `warehouse`.`wa_address1`) in (('Freeman And Co', '234 West Covina Pkwy')))"
        + ")\n"
        + "and\n"
        + "    (`product_class`.`product_family` = 'Food')\n"
        + "group by\n"
        + "    `warehouse`.`warehouse_fax`,\n"
        + "    `warehouse`.`wa_address1`,\n"
        + "    `warehouse`.`warehouse_name`,\n"
        + "    `product_class`.`product_family`\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
        + "    ISNULL(`c3`) ASC, `c3` ASC"
        : "    ISNULL(`warehouse`.`warehouse_fax`) ASC, `warehouse`.`warehouse_fax` ASC,\n"
        + "    ISNULL(`warehouse`.`wa_address1`) ASC, `warehouse`.`wa_address1` ASC,\n"
        + "    ISNULL(`warehouse`.`warehouse_name`) ASC, `warehouse`.`warehouse_name` ASC,\n"
        + "    ISNULL(`product_class`.`product_family`) ASC, `product_class`.`product_family` ASC" );
      SqlPattern[] patterns = {
      new SqlPattern(
        DatabaseProduct.MYSQL, necjSqlMySql, necjSqlMySql )
    };

    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns ).verify();
  }

  /**
   * Check that multi-level member list generates compact form of SQL where clause: (1) Use IN list if possible(not
   * possible if there are null values because NULLs in IN lists do not match) (2) Group members sharing the same parent
   * (3) Only need to compare up to the first unique parent level. (4) Can handle predicates correctly if the member
   * list contains both NULL and non NULL child levels.
   */
  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, TestMultiLevelMemberConstraintWithMixedNullNonNullChildModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMultiLevelMemberConstraintWithMixedNullNonNullChild(Context<?> context)  {
    if ( !context.getConfigValue(ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS, ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS_DEFAULT_VALUE, Boolean.class) ) {
      return;
    }
    String query =
      "with\n"
        + "set [Filtered Warehouse Set] as "
        + "{[Warehouse2].[#null].[#null].[#null],"
        + " [Warehouse2].[#null].[#null].[971-555-6213]} "
        + "set [NECJ] as NonEmptyCrossJoin([Filtered Warehouse Set], {[Product].[Product Family].Food}) "
        + "select [NECJ] on columns from [Warehouse2]";

    String necjSqlDerby =
      "select \"warehouse\".\"wa_address3\", \"warehouse\".\"wa_address2\", \"warehouse\".\"warehouse_fax\", "
        + "\"product_class\".\"product_family\" "
        + "from \"warehouse\" as \"warehouse\" "
        + "join \"inventory_fact_1997\" as \"inventory_fact_1997\" on \"inventory_fact_1997\".\"warehouse_id\" = \"warehouse\".\"warehouse_id\" "
        + "join \"product\" as \"product\" on \"inventory_fact_1997\".\"product_id\" = \"product\".\"product_id\" "
        + "join \"product_class\" as \"product_class\" on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" "
        + "where "
        + "((\"warehouse\".\"warehouse_fax\" = '971-555-6213' or \"warehouse\".\"warehouse_fax\" is null) and "
        + "\"warehouse\".\"wa_address2\" is null and \"warehouse\".\"wa_address3\" is null) and "
        + "(\"product_class\".\"product_family\" = 'Food') "
        + "group by \"warehouse\".\"wa_address3\", \"warehouse\".\"wa_address2\", \"warehouse\".\"warehouse_fax\", "
        + "\"product_class\".\"product_family\" "
        + "order by CASE WHEN \"warehouse\".\"wa_address3\" IS NULL THEN 1 ELSE 0 END, \"warehouse\".\"wa_address3\" "
        + "ASC, CASE WHEN \"warehouse\".\"wa_address2\" IS NULL THEN 1 ELSE 0 END, \"warehouse\".\"wa_address2\" ASC,"
        + " CASE WHEN \"warehouse\".\"warehouse_fax\" IS NULL THEN 1 ELSE 0 END, \"warehouse\".\"warehouse_fax\" ASC,"
        + " CASE WHEN \"product_class\".\"product_family\" IS NULL THEN 1 ELSE 0 END, \"product_class\""
        + ".\"product_family\" ASC";

    String necjSqlMySql =
      "select `warehouse`.`wa_address3` as `c0`, `warehouse`.`wa_address2` as `c1`, `warehouse`.`warehouse_fax` as "
        + "`c2`, "
        + "`product_class`.`product_family` as `c3` from `warehouse` as `warehouse` "
        + "join `inventory_fact_1997` as `inventory_fact_1997` on `inventory_fact_1997`.`warehouse_id` = `warehouse`.`warehouse_id` "
        + "join `product` as `product` on `inventory_fact_1997`.`product_id` = `product`.`product_id` "
        + "join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id` "
        + "where "
        + "((`warehouse`.`warehouse_fax` = '971-555-6213' or `warehouse`.`warehouse_fax` is null) and "
        + "`warehouse`.`wa_address2` is null and `warehouse`.`wa_address3` is null) and "
        + "(`product_class`.`product_family` = 'Food') "
        + "group by `warehouse`.`wa_address3`, `warehouse`.`wa_address2`, `warehouse`.`warehouse_fax`, "
        + "`product_class`.`product_family` "
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "order by ISNULL(`c0`) ASC, `c0` ASC, ISNULL(`c1`) ASC, "
        + "`c1` ASC, ISNULL(`c2`) ASC, `c2` ASC, "
        + "ISNULL(`c3`) ASC, `c3` ASC"
        :
        "order by ISNULL(`warehouse`.`wa_address3`) ASC, `warehouse`.`wa_address3` ASC, ISNULL(`warehouse`"
          + ".`wa_address2`) ASC, "
          + "`warehouse`.`wa_address2` ASC, ISNULL(`warehouse`.`warehouse_fax`) ASC, `warehouse`.`warehouse_fax` ASC, "
          + "ISNULL(`product_class`.`product_family`) ASC, `product_class`.`product_family` ASC" );
    SqlPattern[] patterns = {
      new SqlPattern(
        DatabaseProduct.DERBY, necjSqlDerby, necjSqlDerby ),
      new SqlPattern(
        DatabaseProduct.MYSQL, necjSqlMySql, necjSqlMySql )
    };

    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns ).verify();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonEmptyUnionQuery(Context<?> context)  {
    Result result = executeQuery(
      "select {[Measures].[Unit Sales], [Measures].[Store Cost], [Measures].[Store Sales]} on columns,\n"
        + " NON EMPTY Hierarchize(\n"
        + "   Union(\n"
        + "     Crossjoin(\n"
        + "       Crossjoin([Gender].[All Gender].children,\n"
        + "                 [Marital Status].[All Marital Status].children),\n"
        + "       Crossjoin([Customers].[All Customers].children,\n"
        + "                 [Product].[All Products].children) ),\n"
        + "     Crossjoin({([Gender].[All Gender].[M], [Marital Status].[All Marital Status].[M])},\n"
        + "       Crossjoin(\n"
        + "         [Customers].[All Customers].[USA].children,\n"
        + "         [Product].[All Products].children) ) )) on rows\n"
        + "from Sales where ([Time].[1997])", context.getConnectionWithDefaultRole());
    final Axis rowsAxis = result.getAxes()[ 1 ];
    assertEquals( 21, rowsAxis.getPositions().size() );
  }

  /**
   * when Mondrian parses a string like "[Store].[All Stores].[USA].[CA].[San Francisco]" it shall not lookup additional
   * members.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testLookupMemberCache(Context<?> context)  {
    if ( context.getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) > 0 ) {
      // Dependency testing causes extra SQL reads, and screws up this
      // test.
      return;
    }

    // there currently isn't a cube member to children cache, only
    // a shared cache so use the shared smart member reader
    SmartMemberReader smr = getSmartMemberReader(context.getConnectionWithDefaultRole(), "Store" );
    MemberCacheHelper smrch = smr.cacheHelper;
    MemberCacheHelper rcsmrch =
      ( (RolapCubeHierarchy.RolapCubeHierarchyMemberReader) smr )
        .getRolapCubeMemberCacheHelper();
    SmartMemberReader ssmr = getSharedSmartMemberReader(context.getConnectionWithDefaultRole(), "Store" );
    MemberCacheHelper ssmrch = ssmr.cacheHelper;
    clearAndHardenCache( smrch );
    clearAndHardenCache( rcsmrch );
    clearAndHardenCache( ssmrch );

    RolapResult result =
      (RolapResult) executeQuery(
        "select {[Store].[All Stores].[USA].[CA].[San Francisco]} on columns from [Sales]", context.getConnectionWithDefaultRole() );
    assertTrue(
      ssmrch.mapKeyToMember.size() <= 5, "no additional members should be read:"
                    + ssmrch.mapKeyToMember.size());
    RolapMember sf =
      (RolapMember) result.getAxes()[ 0 ].getPositions().get( 0 ).get( 0 );
    RolapMember ca = sf.getParentMember();

    // convert back to shared members
    ca = ( (RolapCubeMember) ca ).getRolapMember();
    sf = ( (RolapCubeMember) sf ).getRolapMember();

    List<RolapMember> list = ssmrch.mapMemberToChildren.get(
      ca, scf.getMemberChildrenConstraint( null ) );
    assertNull(list, "children of [CA] are not in cache");

    Collection caChildren = ssmrch.mapParentToNamedChildren.get( ca );

    assertNotNull(caChildren, "child [San Francisco] of [CA] is in cache");
    assertTrue(caChildren.contains( sf ), "[San Francisco] expected");
  }

  /**
   * When looking for [Month] Mondrian generates SQL that tries to find 'Month' as a member of the time dimension. This
   * resulted in an SQLException because the year level is numeric and the constant 'Month' in the WHERE condition is
   * not.  Its probably a bug that Mondrian does not take into account [Time].[1997] when looking up [Month].
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testLookupMember(Context<?> context)  {
    // ok if no exception occurs
    executeQuery(
      "SELECT DESCENDANTS([Time].[1997], [Month]) ON COLUMNS FROM [Sales]", context.getConnectionWithDefaultRole() );
  }


  /**
   * Non Empty CrossJoin (A,B) gets turned into CrossJoin (Non Empty(A), Non Empty(B)).  Verify that there is no crash
   * when the length of B could be non-zero length before the non empty and 0 after the non empty.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "false", type = Boolean.class)
  void testNonEmptyCrossJoinList(Context<?> context)  {

    executeQuery(
      "select non empty CrossJoin([Customers].[Name].Members, "
        + "{[Promotions].[All Promotions].[Fantastic Discounts]}) "
        + "ON COLUMNS FROM [Sales]", context.getConnectionWithDefaultRole());
  }

  /**
   * SQL Optimization must be turned off in ragged hierarchies.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testLookupMember2(Context<?> context)  {
    // ok if no exception occurs
    executeQuery(
      "select {[Store].[USA].[Washington]} on columns from [Sales Ragged]", context.getConnectionWithDefaultRole());
  }

  /**
   * Make sure that the Crossjoin in [Measures].[CustomerCount] is not evaluated in NON EMPTY context.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCalcMemberWithNonEmptyCrossJoin(Context<?> context)  {
    //etCacheControl( null );
    flushSchemaCache(context.getConnectionWithDefaultRole());
    Result result = executeQuery(
      "with member [Measures].[CustomerCount] as \n"
        + "'Count(CrossJoin({[Product].[All Products]}, [Customers].[Name].Members))'\n"
        + "select \n"
        + "NON EMPTY{[Measures].[CustomerCount]} ON columns,\n"
        + "NON EMPTY{[Product].[All Products]} ON rows\n"
        + "from [Sales]\n"
        + "where ([Store].[All Stores].[USA].[CA].[San Francisco].[Store 14], [Time].[1997].[Q1].[1])",
            context.getConnectionWithDefaultRole());
    Cell c = result.getCell( new int[] { 0, 0 } );
    // we expect 10281 customers, although there are only 20 non-empty ones
    // @see #testLevelMembers
    assertEquals( "10,281", c.getFormattedValue() );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testLevelMembers(Context<?> context)  {
    if ( context.getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) > 0 ) {
      // Dependency testing causes extra SQL reads, and screws up this
      // test.
      return;
    }
    SmartMemberReader smr = getSmartMemberReader(context.getConnectionWithDefaultRole(), "Customers" );
    // use the RolapCubeHierarchy's member cache for levels
    MemberCacheHelper smrch =
      ( (RolapCubeHierarchy.CacheRolapCubeHierarchyMemberReader) smr )
        .rolapCubeCacheHelper;
    clearAndHardenCache( smrch );
    MemberCacheHelper smrich = smr.cacheHelper;
    clearAndHardenCache( smrich );

    // use the shared member cache for mapMemberToChildren
    SmartMemberReader ssmr = getSharedSmartMemberReader(context.getConnectionWithDefaultRole(), "Customers" );
    MemberCacheHelper ssmrch = ssmr.cacheHelper;
    clearAndHardenCache( ssmrch );

    TestCase c = new TestCase(context.getConnectionWithDefaultRole(),
      50,
      21,
      "select \n"
        + "{[Measures].[Unit Sales]} ON columns,\n"
        + "NON EMPTY {[Customers].[All Customers], [Customers].[Name].Members} ON rows\n"
        + "from [Sales]\n"
        + "where ([Store].[All Stores].[USA].[CA].[San Francisco].[Store 14], [Time].[1997].[Q1].[1])" );
    Result r = c.run();
    List<? extends Level> levels = smr.getHierarchy().getLevels();
    Level nameLevel = levels.getLast();

    // evaluator for [All Customers], [Store 14], [1/1/1997]
    Evaluator evaluator = getEvaluator( r, new int[] { 0, 0 } );

    // make sure that [Customers].[Name].Members is NOT in cache
    TupleConstraint lmc = scf.getLevelMembersConstraint( null );
    assertNull( smrch.mapLevelToMembers.get( (RolapLevel) nameLevel, lmc ) );
    // make sure that NON EMPTY [Customers].[Name].Members IS in cache
    evaluator.setNonEmpty( true );
    lmc = scf.getLevelMembersConstraint( evaluator );
    List<RolapMember> list =
      smrch.mapLevelToMembers.get( (RolapLevel) nameLevel, lmc );
    if ( context.getConfigValue(ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE, ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE_DEFAULT_VALUE, Boolean.class) ) {
      assertNotNull( list );
      assertEquals( 20, list.size() );
    }
    // make sure that the parent/child for the context are cached

    // [Customers].[USA].[CA].[Burlingame].[Peggy Justice]
    Member member = r.getAxes()[ 1 ].getPositions().get( 1 ).get( 0 );
    Member parent = member.getParentMember();
    parent = ( (RolapCubeMember) parent ).getRolapMember();
    member = ( (RolapCubeMember) member ).getRolapMember();

    // lookup all children of [Burlingame] -> not in cache
    MemberChildrenConstraint mcc = scf.getMemberChildrenConstraint( null );
    assertNull( ssmrch.mapMemberToChildren.get( (RolapMember) parent, mcc ) );

    // lookup NON EMPTY children of [Burlingame] -> yes these are in cache
    mcc = scf.getMemberChildrenConstraint( evaluator );
    list = smrich.mapMemberToChildren.get( (RolapMember) parent, mcc );
    assertNotNull( list );
    assertTrue( list.contains( member ) );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testLevelMembersWithoutNonEmpty(Context<?> context)  {
    // getConnectionWithDefaultRole() builds a NEW connection every time, and this test asked for
    // four of them -- the member reader whose cache is asserted on could belong to a different
    // catalog than the query that is supposed to fill it. Hold one connection.
    //
    // setLevelPreCacheThreshold(0) is kept on purpose: at threshold 0
    // getLevelMembersConstraint(evaluator) returns the constrained constraint, so the NON EMPTY
    // members go under a different cache key -- which is what the assertNull below checks. The
    // flushSchemaCache() the port had added is dropped; it emptied the very cache the test fills.
    Connection connection = context.getConnectionWithDefaultRole();
    SmartMemberReader smr = getSmartMemberReader(connection, "Customers" );

    MemberCacheHelper smrch =
      ( (RolapCubeHierarchy.CacheRolapCubeHierarchyMemberReader) smr )
        .rolapCubeCacheHelper;
    clearAndHardenCache( smrch );

    MemberCacheHelper smrich = smr.cacheHelper;
    clearAndHardenCache( smrich );

    SmartMemberReader ssmr = getSharedSmartMemberReader(connection, "Customers" );
    MemberCacheHelper ssmrch = ssmr.cacheHelper;
    clearAndHardenCache( ssmrch );

    Result r = executeQuery(
      "select \n"
        + "{[Measures].[Unit Sales]} ON columns,\n"
        + "{[Customers].[All Customers], [Customers].[Name].Members} ON rows\n"
        + "from [Sales]\n"
        + "where ([Store].[All Stores].[USA].[CA].[San Francisco].[Store 14], [Time].[1997].[Q1].[1])", connection );
    List<? extends Level> levels = smr.getHierarchy().getLevels();
    Level nameLevel = levels.getLast();

    // evaluator for [All Customers], [Store 14], [1/1/1997]
    Evaluator evaluator = getEvaluator( r, new int[] { 0, 0 } );

    // make sure that [Customers].[Name].Members IS in cache
    TupleConstraint lmc = scf.getLevelMembersConstraint( null );
    List<RolapMember> list =
      smrch.mapLevelToMembers.get( (RolapLevel) nameLevel, lmc );
    if ( context.getConfigValue(ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE, ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE_DEFAULT_VALUE, Boolean.class) ) {
      assertNotNull( list );
      assertEquals( 10281, list.size() );
    }
    // make sure that NON EMPTY [Customers].[Name].Members is NOT in cache
    evaluator.setNonEmpty( true );
    lmc = scf.getLevelMembersConstraint( evaluator );
    assertNull( smrch.mapLevelToMembers.get( (RolapLevel) nameLevel, lmc ) );

    // make sure that the parent/child for the context are cached

    // [Customers].[Canada].[BC].[Burnaby]
    Member member = r.getAxes()[ 1 ].getPositions().get( 1 ).get( 0 );
    Member parent = member.getParentMember();

    parent = ( (RolapCubeMember) parent ).getRolapMember();
    member = ( (RolapCubeMember) member ).getRolapMember();

    // lookup all children of [Burnaby] -> yes, found in cache
    MemberChildrenConstraint mcc = scf.getMemberChildrenConstraint( null );
    list = ssmrch.mapMemberToChildren.get( (RolapMember) parent, mcc );
    assertNotNull( list );
    assertTrue( list.contains( member ) );

    // lookup NON EMPTY children of [Burlingame] -> not in cache
    mcc = scf.getMemberChildrenConstraint( evaluator );
    list = ssmrch.mapMemberToChildren.get( (RolapMember) parent, mcc );
    assertNull( list );
  }

  /**
   * Tests that <Dimension>.Members exploits the same optimization as
   * <Level>.Members.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testDimensionMembers(Context<?> context)  {
    // No query should return more than 20 rows. (1 row at 'all' level,
    // 1 row at nation level, 1 at state level, 20 at city level, and 11
    // at customers level = 34.)
    TestCase c = new TestCase(context.getConnectionWithDefaultRole(),
      34,
      34,
      "select \n"
        + "{[Measures].[Unit Sales]} ON columns,\n"
        + "NON EMPTY [Customers].Members ON rows\n"
        + "from [Sales]\n"
        + "where ([Store].[All Stores].[USA].[CA].[San Francisco].[Store 14], [Time].[1997].[Q1].[1])" );
    c.run();
  }

  /**
   * Tests non empty children of rolap member
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMemberChildrenOfRolapMember(Context<?> context)  {
    TestCase c = new TestCase(context.getConnectionWithDefaultRole(),
      50,
      4,
      "select \n"
        + "{[Measures].[Unit Sales]} ON columns,\n"
        + "NON EMPTY [Customers].[All Customers].[USA].[CA].[Palo Alto].Children ON rows\n"
        + "from [Sales]\n"
        + "where ([Store].[All Stores].[USA].[CA].[San Francisco].[Store 14], [Time].[1997].[Q1].[1])" );
    c.run();
  }

  /**
   * Tests non empty children of All member
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMemberChildrenOfAllMember(Context<?> context)  {
    TestCase c = new TestCase(context.getConnectionWithDefaultRole(),
      50,
      14,
      "select {[Measures].[Unit Sales]} ON columns,\n"
        + "NON EMPTY [Promotions].[All Promotions].Children ON rows from [Sales]\n"
        + "where ([Time].[1997].[Q1].[1])" );
    c.run();
  }

  /**
   * Tests non empty children of All member w/o WHERE clause
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMemberChildrenNoWhere(Context<?> context)  {
    // The time dimension is joined because there is no (All) level in the
    // Time hierarchy:
    //
    //      select
    //        `promotion`.`promotion_name` as `c0`
    //      from
    //        `time_by_day` as `time_by_day`,
    //        `sales_fact_1997` as `sales_fact_1997`,
    //        `promotion` as `promotion`
    //      where `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`
    //        and `time_by_day`.`the_year` = 1997
    //        and `sales_fact_1997`.`promotion_id`
    //                = `promotion`.`promotion_id`
    //      group by
    //        `promotion`.`promotion_name`
    //      order by
    //        `promotion`.`promotion_name`

    TestCase c =
      new TestCase(context.getConnectionWithDefaultRole(),
        50,
        48,
        "select {[Measures].[Unit Sales]} ON columns,\n"
          + "NON EMPTY [Promotions].[All Promotions].Children ON rows "
          + "from [Sales]\n" );
    c.run();
  }

  /**
   * Testcase for bug 1379068, which causes no children of [Time].[1997].[Q2] to be found, because it incorrectly
   * constrains on the level's key column rather than name column.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMemberChildrenNameCol(Context<?> context)  {
    // Expression dependency testing casues false negatives.
    if ( context.getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) > 0 ) {
      return;
    }
    TestCase c = new TestCase(context.getConnectionWithDefaultRole(),
      3,
      1,
      "select "
        + " {[Measures].[Count]} ON columns,"
        + " {[Time].[1997].[Q2].[April]} on rows "
        + "from [HR]" );
    c.run();
  }

  /**
   * When a member is expanded in JPivot with mulitple hierarchies visible it generates a
   * <code>CrossJoin({[member from left hierarchy]}, [member to
   * expand].Children)</code>
   *
   * <p>This should behave the same as if <code>[member from left
   * hierarchy]</code> was put into the slicer.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossjoin(Context<?> context)  {
    if ( context.getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) > 0 ) {
      // Dependency testing causes extra SQL reads, and makes this
      // test fail.
      return;
    }

    TestCase c =
      new TestCase(context.getConnectionWithDefaultRole(),
        45,
        4,
        "select \n"
          + "{[Measures].[Unit Sales]} ON columns,\n"
          + "NON EMPTY Crossjoin("
          + "{[Store].[USA].[CA].[San Francisco].[Store 14]},"
          + " [Customers].[USA].[CA].[Palo Alto].Children) ON rows\n"
          + "from [Sales] where ([Time].[1997].[Q1].[1])" );
    c.run();
  }

  /**
   * Ensures that NON EMPTY Descendants is optimized. Ensures that Descendants as a side effect collects MemberChildren
   * that may be looked up in the cache.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonEmptyDescendants(Context<?> context)  {
    // Don't run the test if we're testing expression dependencies.
    // Expression dependencies cause spurious interval calls to
    // 'level.getMembers()' which create false negatives in this test.
    if ( context.getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) > 0 ) {
      return;
    }

    Connection con = context.getConnectionWithDefaultRole();
    SmartMemberReader smr = getSmartMemberReader( con, "Customers" );
    MemberCacheHelper smrch = smr.cacheHelper;
    clearAndHardenCache( smrch );

    SmartMemberReader ssmr = getSmartMemberReader( con, "Customers" );
    MemberCacheHelper ssmrch = ssmr.cacheHelper;
    clearAndHardenCache( ssmrch );

    TestCase c =
      new TestCase(
        con,
        45,
        21,
        "select \n"
          + "{[Measures].[Unit Sales]} ON columns, "
          + "NON EMPTY {[Customers].[All Customers], Descendants([Customers].[All Customers].[USA].[CA], [Customers]"
          + ".[Name])} on rows "
          + "from [Sales] "
          + "where ([Store].[All Stores].[USA].[CA].[San Francisco].[Store 14], [Time].[1997].[Q1].[1])" );
    Result result = c.run();
    // [Customers].[All Customers].[USA].[CA].[Burlingame].[Peggy Justice]
    RolapMember peggy =
      (RolapMember) result.getAxes()[ 1 ].getPositions().get( 1 ).get( 0 );
    RolapMember burlingame = peggy.getParentMember();

    peggy = ( (RolapCubeMember) peggy ).getRolapMember();
    burlingame = ( (RolapCubeMember) burlingame ).getRolapMember();

    // all children of burlingame are not in cache
    MemberChildrenConstraint mcc = scf.getMemberChildrenConstraint( null );
    assertNull( ssmrch.mapMemberToChildren.get( burlingame, mcc ) );
    // but non empty children is
    Evaluator evaluator = getEvaluator( result, new int[] { 0, 0 } );
    evaluator.setNonEmpty( true );
    mcc = scf.getMemberChildrenConstraint( evaluator );
    List<RolapMember> list =
      ssmrch.mapMemberToChildren.get( burlingame, mcc );
    assertNotNull( list );
    assertTrue( list.contains( peggy ) );

    // now we run the same query again, this time everything must come out
    // of the cache
    RolapNativeRegistry reg = getRegistry( con );
    reg.setListener(
      new Listener()  {
        @Override
		public void foundEvaluator( NativeEvent e ) {
        }

        @Override
		public void foundInCache( TupleEvent e ) {
        }

        @Override
		public void executingSql( TupleEvent e ) {
          fail( "expected caching" );
        }
      } );
    try {
      c.run();
    } finally {
      reg.setListener( null );
    }
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testBug1412384(Context<?> context)  {
    // Bug 1412384 causes a NPE in ContextConstraintWriter.
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select NON EMPTY {[Time].[1997]} ON COLUMNS,\n"
        + "NON EMPTY Hierarchize(Union({[Customers].[All Customers]},\n"
        + "[Customers].[All Customers].Children)) ON ROWS\n"
        + "from [Sales]\n"
        + "where [Measures].[Profit]").returnsGrid(
      "Axis #0:\n"
        + "{[Measures].[Profit]}\n"
        + "Axis #1:\n"
        + "{[Time].[Time].[1997]}\n"
        + "Axis #2:\n"
        + "{[Customers].[Customers].[All Customers]}\n"
        + "{[Customers].[Customers].[USA]}\n"
        + "Row #0: $339,610.90\n"
        + "Row #1: $339,610.90\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testVirtualCubeCrossJoin(Context<?> context)  {
    checkNative(context,
      18,
      3,
      "select "
        + "{[Measures].[Units Ordered], [Measures].[Store Sales]} on columns, "
        + "non empty crossjoin([Product].[All Products].children, "
        + "[Store].[All Stores].children) on rows "
        + "from [Warehouse and Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testVirtualCubeNonEmptyCrossJoin(Context<?> context)  {
    checkNative(context,
      18,
      3,
      "select "
        + "{[Measures].[Units Ordered], [Measures].[Store Sales]} on columns, "
        + "NonEmptyCrossJoin([Product].[All Products].children, "
        + "[Store].[All Stores].children) on rows "
        + "from [Warehouse and Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testVirtualCubeNonEmptyCrossJoin3Args(Context<?> context)  {
    checkNative(context,
      3,
      3,
      "select "
        + "{[Measures].[Store Sales]} on columns, "
        + "nonEmptyCrossJoin([Product].[All Products].children, "
        + "nonEmptyCrossJoin([Customers].[All Customers].children,"
        + "[Store].[All Stores].children)) on rows "
        + "from [Warehouse and Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR", type = String.class)
  void testNotNativeVirtualCubeCrossJoin1(Context<?> context)  {
    switch ( getDatabaseProduct(getDialect(context.getConnectionWithDefaultRole()).name()) ) {
      case INFOBRIGHT:
        // Hits same Infobright bug as NamedSetTest.testNamedSetOnMember.
        return;
    }
    // for this test, verify that no alert is raised even though
    // native evaluation isn't supported, because query
    // doesn't use explicit NonEmptyCrossJoin
    // native cross join cannot be used due to AllMembers
    checkNotNative(context,
      3,
      "select "
        + "{[Measures].AllMembers} on columns, "
        + "non empty crossjoin([Product].[All Products].children, "
        + "[Store].[All Stores].children) on rows "
        + "from [Warehouse and Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNotNativeVirtualCubeCrossJoin2(Context<?> context)  {
    // native cross join cannot be used due to the range operator
    checkNotNative(context,
      3,
      "select "
        + "{[Measures].[Sales Count] : [Measures].[Unit Sales]} on columns, "
        + "non empty crossjoin([Product].[All Products].children, "
        + "[Store].[All Stores].children) on rows "
        + "from [Warehouse and Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR", type = String.class)
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "OFF", type = String.class)
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "OFF", type = String.class)
  void testNotNativeVirtualCubeCrossJoinUnsupported(Context<?> context)  {
    switch ( getDatabaseProduct(getDialect(context.getConnectionWithDefaultRole()).name()) ) {
      case INFOBRIGHT:
        // Hits same Infobright bug as NamedSetTest.testNamedSetOnMember.
        return;
    }
    final Boolean enableProperty =
      context.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class);
    final String alertProperty =
      context.getConfigValue(ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED_DEFAULT_VALUE, String.class);
    if ( !enableProperty ) {
      // When native cross joins are explicitly disabled, no alerts
      // are supposed to be raised.
      return;
    }

    String mdx =
      "select "
        + "{[Measures].AllMembers} on columns, "
        + "NonEmptyCrossJoin([Product].[All Products].children, "
        + "[Store].[All Stores].children) on rows "
        + "from [Warehouse and Sales]";


    // set up log4j listener to detect alerts
    //TODO test loging
    /*
    TestAppender alertListener = new TestAppender();
    final Logger rolapUtilLogger = LoggerFactory.getLogger( RolapUtil.class );
    propSaver.setAtLeast( rolapUtilLogger, org.apache.logging.log4j.Level.WARN );
    Util.addAppender( alertListener, rolapUtilLogger, null );
    String expectedMessage =
      "Unable to use native SQL evaluation for 'NonEmptyCrossJoin'";
    */
    // verify that exception is thrown if alerting is set to ERROR
    try {
      checkNotNative(context, 3, mdx );
      fail( "Expected NativeEvaluationUnsupportedException" );
    } catch ( Exception ex ) {
      Throwable t = ex;
      //while ( t.getCause() != null && t != t.getCause() ) {
      //  t = t.getCause();
      //}
      if ( !( t instanceof NativeEvaluationUnsupportedException ) ) {
        fail();
      }
      // Expected
    } finally {
      context.getCatalogCache().clear();
      //propSaver.setAtLeast( rolapUtilLogger, org.apache.logging.log4j.Level.WARN );
    }

    // should have gotten one ERROR
    //TODO test loging
    /*
    int nEvents = countFilteredEvents(
      alertListener.getLogEvents(), org.apache.logging.log4j.Level.ERROR, expectedMessage );
    assertEquals(1, nEvents, "logged error count check");
    alertListener.clear();

    // verify that exactly one warning is posted but execution succeeds
    // if alerting is set to WARN
    propSaver.set(
      alertProperty, org.apache.logging.log4j.Level.WARN.toString() );
    try {
      checkNotNative(context, 3, mdx );
    } finally {
      propSaver.reset();
      propSaver.setAtLeast( rolapUtilLogger, org.apache.logging.log4j.Level.WARN );
    }

    // should have gotten one WARN
    nEvents = countFilteredEvents(
      alertListener.getLogEvents(), org.apache.logging.log4j.Level.WARN, expectedMessage );
    assertEquals(1, nEvents,  "logged warning count check");
    alertListener.clear();
    */
    // verify that no warning is posted if native evaluation is
    // explicitly disabled
    try {
      checkNotNative(context, 3, mdx );
    } finally {
    	context.getCatalogCache().clear();
        //propSaver.setAtLeast( rolapUtilLogger, org.apache.logging.log4j.Level.WARN );
    }
    //TODO test loging
    /*
    // should have gotten no WARN
    nEvents = countFilteredEvents(
      alertListener.getLogEvents(), org.apache.logging.log4j.Level.WARN, expectedMessage );
    assertEquals(0, nEvents,  "logged warning count check");
    alertListener.clear();

    // no biggie if we don't get here for some reason; just being
    // half-heartedly clean
    Util.removeAppender( alertListener, rolapUtilLogger );
     */
  }
/*
//TODO
  private int countFilteredEvents(
    List<LogEvent> events,
    org.apache.logging.log4j.Level level,
    String pattern ) {
    int filteredEventCount = 0;
    for ( LogEvent event : events ) {
      if ( !event.getLevel().equals( level ) ) {
        continue;
      }
      if ( event.getMessage().toString().indexOf( pattern ) == -1 ) {
        continue;
      }
      filteredEventCount++;
    }
    return filteredEventCount;
  }

 */

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testVirtualCubeCrossJoinCalculatedMember1(Context<?> context)  {
    // calculated member appears in query
    checkNative(context,
      18,
      3,
      "WITH MEMBER [Measures].[Total Cost] as "
        + "'[Measures].[Store Cost] + [Measures].[Warehouse Cost]' "
        + "select "
        + "{[Measures].[Total Cost]} on columns, "
        + "non empty crossjoin([Product].[All Products].children, "
        + "[Store].[All Stores].children) on rows "
        + "from [Warehouse and Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testVirtualCubeCrossJoinCalculatedMember2(Context<?> context)  {
    // calculated member defined in schema
    checkNative(context,
      18,
      3,
      "select "
        + "{[Measures].[Profit Per Unit Shipped]} on columns, "
        + "non empty crossjoin([Product].[All Products].children, "
        + "[Store].[All Stores].children) on rows "
        + "from [Warehouse and Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNotNativeVirtualCubeCrossJoinCalculatedMember(Context<?> context)  {
    // native cross join cannot be used due to CurrentMember in the
    // calculated member
    checkNotNative(context,
      3,
      "WITH MEMBER [Measures].[CurrMember] as "
        + "'[Measures].CurrentMember' "
        + "select "
        + "{[Measures].[CurrMember]} on columns, "
        + "non empty crossjoin([Product].[All Products].children, "
        + "[Store].[All Stores].children) on rows "
        + "from [Warehouse and Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjEnumCalcMembers(Context<?> context)  {
    // 3 cross joins -- 2 of the 4 arguments to the cross joins are
    // enumerated sets with calculated members
    // should be non-native due to the fix to testCjEnumCalcMembersBug()
    checkNotNative(context,
      30,
      "with "
        + "member [Product].[All Products].[Drink].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "    'sum({[Product].[All Products].[Drink]})' "
        + "member [Product].[All Products].[Non-Consumable].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "    'sum({[Product].[All Products].[Non-Consumable]})' "
        + "member [Customers].[All Customers].[USA].[CA].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "    'sum({[Customers].[All Customers].[USA].[CA]})' "
        + "member [Customers].[All Customers].[USA].[OR].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "    'sum({[Customers].[All Customers].[USA].[OR]})' "
        + "member [Customers].[All Customers].[USA].[WA].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "    'sum({[Customers].[All Customers].[USA].[WA]})' "
        + "select "
        + "{[Measures].[Unit Sales]} on columns, "
        + "non empty "
        + "    crossjoin("
        + "        crossjoin("
        + "            crossjoin("
        + "                {[Product].[All Products].[Drink].[*SUBTOTAL_MEMBER_SEL~SUM], "
        + "                    [Product].[All Products].[Non-Consumable].[*SUBTOTAL_MEMBER_SEL~SUM]}, "
        + "                " + EDUCATION_LEVEL_LEVEL + ".Members), "
        + "            {[Customers].[All Customers].[USA].[CA].[*SUBTOTAL_MEMBER_SEL~SUM], "
        + "                [Customers].[All Customers].[USA].[OR].[*SUBTOTAL_MEMBER_SEL~SUM], "
        + "                [Customers].[All Customers].[USA].[WA].[*SUBTOTAL_MEMBER_SEL~SUM]}), "
        + "        [Time].[Year].members)"
        + "    on rows "
        + "from [Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testCjEnumCalcMembersBug(Context<?> context)  {
    // make sure NECJ is forced to be non-native
    // before the fix, the query is natively evaluated and result
    // has empty rows for [Store Type].[All Store Types].[HeadQuarters]
    checkNotNative(context,
      9,
      "with "
        + "member [Store Type].[All Store Types].[S] as sum({[Store Type].[All Store Types]}) "
        + "set [Enum Store Types] as {"
        + "    [Store Type].[All Store Types].[HeadQuarters], "
        + "    [Store Type].[All Store Types].[Small Grocery], "
        + "    [Store Type].[All Store Types].[Supermarket], "
        + "    [Store Type].[All Store Types].[S]}"
        + "select [Measures] on columns,\n"
        + "    NonEmptyCrossJoin([Product].[All Products].Children, [Enum Store Types]) on rows\n"
        + "from [Sales]",
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Drink], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Product].[Product].[Drink], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Product].[Product].[Drink], [Store Type].[Store Type].[All Store Types].[S]}\n"
        + "{[Product].[Product].[Food], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Product].[Product].[Food], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Product].[Product].[Food], [Store Type].[Store Type].[All Store Types].[S]}\n"
        + "{[Product].[Product].[Non-Consumable], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Product].[Product].[Non-Consumable], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Product].[Product].[Non-Consumable], [Store Type].[Store Type].[All Store Types].[S]}\n"
        + "Row #0: 574\n"
        + "Row #1: 14,092\n"
        + "Row #2: 24,597\n"
        + "Row #3: 4,764\n"
        + "Row #4: 108,188\n"
        + "Row #5: 191,940\n"
        + "Row #6: 1,219\n"
        + "Row #7: 28,275\n"
        + "Row #8: 50,236\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjEnumEmptyCalcMembers(Context<?> context)  {

    // enumerated list of calculated members results in some empty cells
    checkNotNative(context,
      5,
      "with "
        + "member [Customers].[All Customers].[USA].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "    'sum({[Customers].[All Customers].[USA]})' "
        + "member [Customers].[All Customers].[Mexico].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "    'sum({[Customers].[All Customers].[Mexico]})' "
        + "member [Customers].[All Customers].[Canada].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "    'sum({[Customers].[All Customers].[Canada]})' "
        + "select "
        + "{[Measures].[Unit Sales]} on columns, "
        + "non empty "
        + "    crossjoin("
        + "        {[Customers].[All Customers].[Mexico].[*SUBTOTAL_MEMBER_SEL~SUM], "
        + "            [Customers].[All Customers].[Canada].[*SUBTOTAL_MEMBER_SEL~SUM], "
        + "            [Customers].[All Customers].[USA].[*SUBTOTAL_MEMBER_SEL~SUM]}, "
        + "        " + EDUCATION_LEVEL_LEVEL + ".Members) "
        + "    on rows "
        + "from [Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCjUnionEnumCalcMembers(Context<?> context)  {
    // non-native due to the fix to testCjEnumCalcMembersBug()
    checkNotNative(context,
      46,
      "with "
        + "member [Education Level].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "    'sum({[Education Level].[All Education Levels]})' "
        + "member [Education Level].[*SUBTOTAL_MEMBER_SEL~AVG] as "
        + "   'avg([Education Level].[Education Level].Members)' select "
        + "{[Measures].[Unit Sales]} on columns, "
        + "non empty union (Crossjoin("
        + "    [Product].[Product Department].Members, "
        + "    {[Education Level].[*SUBTOTAL_MEMBER_SEL~AVG]}), "
        + "crossjoin("
        + "    [Product].[Product Department].Members, "
        + "    {[Education Level].[*SUBTOTAL_MEMBER_SEL~SUM]})) on rows "
        + "from [Sales]" );
  }

  /**
   * Tests the behavior if you have NON EMPTY on both axes, and the default member of a hierarchy is not 'all' or the
   * first child.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonEmptyWithWeirdDefaultMember(Context<?> context)  {
    // Bug.Bug229Fixed is a compile-time-constant false, so this test always
    // returned here; the rest of the (schema-mutating) body was unreachable.
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossJoinNamedSets1(Context<?> context)  {
    checkNative(context,
      3,
      3,
      "with "
        + "SET [ProductChildren] as '[Product].[All Products].children' "
        + "SET [StoreMembers] as '[Store].[Store Country].members' "
        + "select {[Measures].[Store Sales]} on columns, "
        + "non empty crossjoin([ProductChildren], [StoreMembers]) "
        + "on rows from [Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossJoinNamedSets2(Context<?> context)  {

    checkNative(context,
      3, 3,
      "with "
        + "SET [ProductChildren] as '{[Product].[All Products].[Drink], "
        + "[Product].[All Products].[Food], "
        + "[Product].[All Products].[Non-Consumable]}' "
        + "SET [StoreChildren] as '[Store].[All Stores].children' "
        + "select {[Measures].[Store Sales]} on columns, "
        + "non empty crossjoin([ProductChildren], [StoreChildren]) on rows from "
        + "[Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossJoinSetWithDifferentParents(Context<?> context)  {
    // Verify that only the members explicitly referenced in the set
    // are returned.  Note that different members are referenced in
    // each level in the time dimension.
    checkNative(context,
      5,
      5,
      "select "
        + "{[Measures].[Unit Sales]} on columns, "
        + "NonEmptyCrossJoin(" + EDUCATION_LEVEL_LEVEL + ".Members, "
        + "{[Time].[1997].[Q1], [Time].[1998].[Q2]}) on rows from Sales" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossJoinSetWithCrossProdMembers(Context<?> context)  {

    // members in set are a cross product of (1997, 1998) and (Q1, Q2, Q3)
    checkNative(context,
      50, 15,
      "select "
        + "{[Measures].[Unit Sales]} on columns, "
        + "NonEmptyCrossJoin(" + EDUCATION_LEVEL_LEVEL + ".Members, "
        + "{[Time].[1997].[Q1], [Time].[1997].[Q2], [Time].[1997].[Q3], "
        + "[Time].[1998].[Q1], [Time].[1998].[Q2], [Time].[1998].[Q3]})"
        + "on rows from Sales" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossJoinSetWithSameParent(Context<?> context)  {

    // members in set have the same parent
    checkNative(context,
      10, 10,
      "select "
        + "{[Measures].[Unit Sales]} on columns, "
        + "NonEmptyCrossJoin(" + EDUCATION_LEVEL_LEVEL + ".Members, "
        + "{[Store].[All Stores].[USA].[CA].[Beverly Hills], "
        + "[Store].[All Stores].[USA].[CA].[San Francisco]}) "
        + "on rows from Sales" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossJoinSetWithUniqueLevel(Context<?> context)  {

    // members in set have different parents but there is a unique level
    checkNative(context,
      10, 10,
      "select "
        + "{[Measures].[Unit Sales]} on columns, "
        + "NonEmptyCrossJoin(" + EDUCATION_LEVEL_LEVEL + ".Members, "
        + "{[Store].[All Stores].[USA].[CA].[Beverly Hills].[Store 6], "
        + "[Store].[All Stores].[USA].[WA].[Bellingham].[Store 2]}) "
        + "on rows from Sales" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossJoinMultiInExprAllMember(Context<?> context)  {
    checkNative(context,
      10,
      10,
      "select "
        + "{[Measures].[Unit Sales]} on columns, "
        + "NonEmptyCrossJoin(" + EDUCATION_LEVEL_LEVEL + ".Members, "
        + "{[Product].[All Products].[Drink].[Alcoholic Beverages], "
        + "[Product].[All Products].[Food].[Breakfast Foods]}) "
        + "on rows from Sales" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossJoinEvaluatorContext1(Context<?> context)  {
    // This test ensures that the proper measure members context is
    // set when evaluating a non-empty cross join.  The context should
    // not include the calculated measure [*TOP_BOTTOM_SET].  If it
    // does, the query will result in an infinite loop because the cross
    // join will try evaluating the calculated member (when it shouldn't)
    // and the calculated member references the cross join, resulting
    // in the loop
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "With "
        + "Set [*NATIVE_CJ_SET] as "
        + "'NonEmptyCrossJoin([*BASE_MEMBERS_Store], [*BASE_MEMBERS_Products])' "
        + "Set [*TOP_BOTTOM_SET] as "
        + "'Order([*GENERATED_MEMBERS_Store], ([Measures].[Unit Sales], "
        + "[Product].[All Products].[*TOP_BOTTOM_MEMBER]), BDESC)' "
        + "Set [*BASE_MEMBERS_Store] as '[Store].members' "
        + "Set [*GENERATED_MEMBERS_Store] as 'Generate([*NATIVE_CJ_SET], {[Store].CurrentMember})' "
        + "Set [*BASE_MEMBERS_Products] as "
        + "'{[Product].[All Products].[Food], [Product].[All Products].[Drink], "
        + "[Product].[All Products].[Non-Consumable]}' "
        + "Set [*GENERATED_MEMBERS_Products] as "
        + "'Generate([*NATIVE_CJ_SET], {[Product].CurrentMember})' "
        + "Member [Product].[All Products].[*TOP_BOTTOM_MEMBER] as "
        + "'Aggregate([*GENERATED_MEMBERS_Products])'"
        + "Member [Measures].[*TOP_BOTTOM_MEMBER] as 'Rank([Store].CurrentMember,[*TOP_BOTTOM_SET])' "
        + "Member [Store].[All Stores].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "'sum(Filter([*GENERATED_MEMBERS_Store], [Measures].[*TOP_BOTTOM_MEMBER] <= 10))'"
        + "Select {[Measures].[Store Cost]} on columns, "
        + "Non Empty Filter(Generate([*NATIVE_CJ_SET], {([Store].CurrentMember)}), "
        + "[Measures].[*TOP_BOTTOM_MEMBER] <= 10) on rows From [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Cost]}\n"
        + "Axis #2:\n"
        + "{[Store].[Store].[All Stores]}\n"
        + "{[Store].[Store].[USA]}\n"
        + "{[Store].[Store].[USA].[CA]}\n"
        + "{[Store].[Store].[USA].[OR]}\n"
        + "{[Store].[Store].[USA].[OR].[Portland]}\n"
        + "{[Store].[Store].[USA].[OR].[Salem]}\n"
        + "{[Store].[Store].[USA].[OR].[Salem].[Store 13]}\n"
        + "{[Store].[Store].[USA].[WA]}\n"
        + "{[Store].[Store].[USA].[WA].[Tacoma]}\n"
        + "{[Store].[Store].[USA].[WA].[Tacoma].[Store 17]}\n"
        + "Row #0: 225,627.23\n"
        + "Row #1: 225,627.23\n"
        + "Row #2: 63,530.43\n"
        + "Row #3: 56,772.50\n"
        + "Row #4: 21,948.94\n"
        + "Row #5: 34,823.56\n"
        + "Row #6: 34,823.56\n"
        + "Row #7: 105,324.31\n"
        + "Row #8: 29,959.28\n"
        + "Row #9: 29,959.28\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossJoinEvaluatorContext2(Context<?> context)  {

    // calculated measure contains a calculated member
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "With Set [*NATIVE_CJ_SET] as "
        + "'NonEmptyCrossJoin([*BASE_MEMBERS_Dates], [*BASE_MEMBERS_Stores])' "
        + "Set [*BASE_MEMBERS_Dates] as '{[Time].[1997].[Q1], [Time].[1997].[Q2]}' "
        + "Set [*GENERATED_MEMBERS_Dates] as "
        + "'Generate([*NATIVE_CJ_SET], {[Time].[Time].CurrentMember})' "
        + "Set [*GENERATED_MEMBERS_Measures] as '{[Measures].[*SUMMARY_METRIC_0]}' "
        + "Set [*BASE_MEMBERS_Stores] as '{[Store].[USA].[CA], [Store].[USA].[WA]}' "
        + "Set [*GENERATED_MEMBERS_Stores] as "
        + "'Generate([*NATIVE_CJ_SET], {[Store].CurrentMember})' "
        + "Member [Time].[Time].[*SM_CTX_SEL] as 'Aggregate([*GENERATED_MEMBERS_Dates])' "
        + "Member [Measures].[*SUMMARY_METRIC_0] as "
        + "'[Measures].[Unit Sales]/([Measures].[Unit Sales],[Time].[*SM_CTX_SEL])', "
        + "FORMAT_STRING = '0.00%' "
        + "Member [Time].[Time].[*SUBTOTAL_MEMBER_SEL~SUM] as 'sum([*GENERATED_MEMBERS_Dates])' "
        + "Member [Store].[*SUBTOTAL_MEMBER_SEL~SUM] as "
        + "'sum(Filter([*GENERATED_MEMBERS_Stores], "
        + "([Measures].[Unit Sales], [Time].[*SUBTOTAL_MEMBER_SEL~SUM]) > 0.0))' "
        + "Select Union "
        + "(CrossJoin "
        + "(Filter "
        + "(Generate([*NATIVE_CJ_SET], {([Time].[Time].CurrentMember)}), "
        + "Not IsEmpty ([Measures].[Unit Sales])), "
        + "[*GENERATED_MEMBERS_Measures]), "
        + "CrossJoin "
        + "(Filter "
        + "({[Time].[*SUBTOTAL_MEMBER_SEL~SUM]}, "
        + "Not IsEmpty ([Measures].[Unit Sales])), "
        + "[*GENERATED_MEMBERS_Measures])) on columns, "
        + "Non Empty Union "
        + "(Filter "
        + "(Filter "
        + "(Generate([*NATIVE_CJ_SET], "
        + "{([Store].CurrentMember)}), "
        + "([Measures].[Unit Sales], "
        + "[Time].[*SUBTOTAL_MEMBER_SEL~SUM]) > 0.0), "
        + "Not IsEmpty ([Measures].[Unit Sales])), "
        + "Filter("
        + "{[Store].[*SUBTOTAL_MEMBER_SEL~SUM]}, "
        + "Not IsEmpty ([Measures].[Unit Sales]))) on rows "
        + "From [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Time].[Time].[1997].[Q1], [Measures].[*SUMMARY_METRIC_0]}\n"
        + "{[Time].[Time].[1997].[Q2], [Measures].[*SUMMARY_METRIC_0]}\n"
        + "{[Time].[Time].[*SUBTOTAL_MEMBER_SEL~SUM], [Measures].[*SUMMARY_METRIC_0]}\n"
        + "Axis #2:\n"
        + "{[Store].[Store].[USA].[CA]}\n"
        + "{[Store].[Store].[USA].[WA]}\n"
        + "{[Store].[Store].[*SUBTOTAL_MEMBER_SEL~SUM]}\n"
        + "Row #0: 48.34%\n"
        + "Row #0: 51.66%\n"
        + "Row #0: 100.00%\n"
        + "Row #1: 50.53%\n"
        + "Row #1: 49.47%\n"
        + "Row #1: 100.00%\n"
        + "Row #2: 49.72%\n"
        + "Row #2: 50.28%\n"
        + "Row #2: 100.00%\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testVCNativeCJWithIsEmptyOnMeasure(Context<?> context)  {
    // Don't use checkNative method here because in the case where
    // native cross join isn't used, the query causes a stack overflow.
    //
    // A measures member is referenced in the IsEmpty() function.  This
    // shouldn't prevent native cross join from being used.
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with "
        + "set BM_PRODUCT as {[Product].[Product].[All Products].[Drink]} "
        + "set BM_EDU as [Education Level].[Education Level].[Education Level].Members "
        + "set BM_GENDER as {[Gender].[Gender].[M]} "
        + "set CJ as NonEmptyCrossJoin(BM_GENDER,NonEmptyCrossJoin(BM_EDU,BM_PRODUCT)) "
        + "set GM_PRODUCT as Generate(CJ, {[Product].[Product].CurrentMember}) "
        + "set GM_EDU as Generate(CJ, {[Education Level].[Education Level].CurrentMember}) "
        + "set GM_GENDER as Generate(CJ, {[Gender].[Gender].CurrentMember}) "
        + "set GM_MEASURE as {[Measures].[Unit Sales]} "
        + "member [Education Level].[Education Level].FILTER1 as Aggregate(GM_EDU) "
        + "member [Gender].[Gender].FILTER2 as Aggregate(GM_GENDER) "
        + "select "
        + "Filter(GM_PRODUCT, Not IsEmpty([Measures].[Unit Sales])) on rows, "
        + "GM_MEASURE on columns "
        + "from [Warehouse and Sales] "
        + "where ([Education Level].[Education Level].FILTER1, [Gender].[Gender].FILTER2)").returnsGrid(
      "Axis #0:\n"
        + "{[Education Level].[Education Level].[FILTER1], [Gender].[Gender].[FILTER2]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Drink]}\n"
        + "Row #0: 12,395\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testVCNativeCJWithTopPercent(Context<?> context)  {
    // The reference to [Store Sales] inside the topPercent function
    // should not prevent native cross joins from being used
    checkNative(context,
      92,
      1,
      "select {topPercent(nonemptycrossjoin([Product].[Product Department].members, "
        + "[Time].[1997].children),10,[Measures].[Store Sales])} on columns, "
        + "{[Measures].[Store Sales]} on rows from "
        + "[Warehouse and Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testVCOrdinalExpression(Context<?> context)  {
    // [Customers].[Name] is an ordinal expression.  Make sure ordering
    // is done on the column corresponding to that expression.
    checkNative(context,
      0,
      67,
      "select {[Measures].[Store Sales]} on columns,"
        + "  NON EMPTY Crossjoin([Customers].[Name].Members,"
        + "    [Product].[Product Name].Members) ON rows "
        + " from [Warehouse and Sales] where ("
        + "  [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14],"
        + "  [Time].[1997].[Q1].[1])" );
  }

  /**
   * Test for bug #1696772 Modified which calculations are tested for non native, non empty joins
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonEmptyWithCalcMeasure(Context<?> context)  {
    checkNative(context,
      15,
      6,
      "With "
        + "Set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Store],NonEmptyCrossJoin"
        + "([*BASE_MEMBERS_Education Level],[*BASE_MEMBERS_Product]))' "
        + "Set [*METRIC_CJ_SET] as 'Filter([*NATIVE_CJ_SET],[Measures].[*Store Sales_SEL~SUM] > 50000.0 And "
        + "[Measures].[*Unit Sales_SEL~MAX] > 50000.0)' "
        + "Set [*BASE_MEMBERS_Store] as '[Store].[Store Country].Members' "
        + "Set [*NATIVE_MEMBERS_Store] as 'Generate([*NATIVE_CJ_SET], {[Store].CurrentMember})' "
        + "Set [*METRIC_MEMBERS_Store] as 'Generate([*METRIC_CJ_SET], {[Store].CurrentMember})' "
        + "Set [*BASE_MEMBERS_Measures] as '{[Measures].[Store Sales],[Measures].[Unit Sales]}' "
        + "Set [*BASE_MEMBERS_Education Level] as '" + EDUCATION_LEVEL_LEVEL
        + ".Members' "
        + "Set [*NATIVE_MEMBERS_Education Level] as 'Generate([*NATIVE_CJ_SET], {[Education Level].CurrentMember})' "
        + "Set [*METRIC_MEMBERS_Education Level] as 'Generate([*METRIC_CJ_SET], {[Education Level].CurrentMember})' "
        + "Set [*BASE_MEMBERS_Product] as '[Product].[Product Family].Members' "
        + "Set [*NATIVE_MEMBERS_Product] as 'Generate([*NATIVE_CJ_SET], {[Product].CurrentMember})' "
        + "Set [*METRIC_MEMBERS_Product] as 'Generate([*METRIC_CJ_SET], {[Product].CurrentMember})' "
        + "Member [Product].[*CTX_METRIC_MEMBER_SEL~SUM] as 'Sum({[Product].[All Products]})' "
        + "Member [Store].[*CTX_METRIC_MEMBER_SEL~SUM] as 'Sum({[Store].[All Stores]})' "
        + "Member [Measures].[*Store Sales_SEL~SUM] as '([Measures].[Store Sales],[Education Level].CurrentMember,"
        + "[Product].[*CTX_METRIC_MEMBER_SEL~SUM],[Store].[*CTX_METRIC_MEMBER_SEL~SUM])' "
        + "Member [Product].[*CTX_METRIC_MEMBER_SEL~MAX] as 'Max([*NATIVE_MEMBERS_Product])' "
        + "Member [Store].[*CTX_METRIC_MEMBER_SEL~MAX] as 'Max([*NATIVE_MEMBERS_Store])' "
        + "Member [Measures].[*Unit Sales_SEL~MAX] as '([Measures].[Unit Sales],[Education Level].CurrentMember,"
        + "[Product].[*CTX_METRIC_MEMBER_SEL~MAX],[Store].[*CTX_METRIC_MEMBER_SEL~MAX])' "
        + "Select "
        + "Non Empty CrossJoin(Generate([*METRIC_CJ_SET], {([Store].CurrentMember)}),[*BASE_MEMBERS_Measures]) on "
        + "columns, "
        + "Non Empty Generate([*METRIC_CJ_SET], {([Education Level].CurrentMember,[Product].CurrentMember)}) on rows "
        + "From [Sales]" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCalculatedSlicerMember(Context<?> context)  {
    // This test verifies that members(the FILTER members in the query
    // below) on the slicer are ignored in CrossJoin emptiness check.
    // Otherwise, if they are not ignored, stack over flow will occur
    // because emptiness check depends on a calculated slicer member
    // which references the non-empty set being computed.
    //
    // Bcause native evaluation already ignores calculated members on
    // the slicer, both native and non-native evaluation should return
    // the same result.
    checkNative(context,
      0,
      1,
      "With "
        + "Set BM_PRODUCT as '{[Product].[All Products].[Drink]}' "
        + "Set BM_EDU as '" + EDUCATION_LEVEL_LEVEL + ".Members' "
        + "Set BM_GENDER as '{[Gender].[Gender].[M]}' "
        + "Set NECJ_SET as 'NonEmptyCrossJoin(BM_GENDER, NonEmptyCrossJoin(BM_EDU,BM_PRODUCT))' "
        + "Set GM_PRODUCT as 'Generate(NECJ_SET, {[Product].CurrentMember})' "
        + "Set GM_EDU as 'Generate(NECJ_SET, {[Education Level].CurrentMember})' "
        + "Set GM_GENDER as 'Generate(NECJ_SET, {[Gender].CurrentMember})' "
        + "Set GM_MEASURE as '{[Measures].[Unit Sales]}' "
        + "Member [Education Level].FILTER1 as 'Aggregate(GM_EDU)' "
        + "Member [Gender].FILTER2 as 'Aggregate(GM_GENDER)' "
        + "Select "
        + "GM_PRODUCT on rows, GM_MEASURE on columns "
        + "From [Sales] Where ([Education Level].FILTER1, [Gender].FILTER2)" );
  }

  // next two verify that when NECJ references dimension from slicer,
  // slicer is correctly ignored for purposes of evaluating NECJ emptiness,
  // regardless of whether evaluation is native or non-native

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "false", type = Boolean.class)
  void testIndependentSlicerMemberNonNative(Context<?> context)  {
    checkIndependentSlicerMemberNative(context);
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  void testIndependentSlicerMemberNative(Context<?> context)  {
    checkIndependentSlicerMemberNative(context);
  }

  private void checkIndependentSlicerMemberNative(Context<?> context) {
    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    //final TestContext<?> context = getTestContext().withFreshConnection();
    Connection connection = context.getConnectionWithDefaultRole();
    try {
      assertThatQuery(connection,
        "with set [p] as '[Product].[Product Family].members' "
          + "set [s] as '[Store].[Store Country].members' "
          + "set [ne] as 'nonemptycrossjoin([p],[s])' "
          + "set [nep] as 'Generate([ne],{[Product].CurrentMember})' "
          + "select [nep] on columns from sales "
          + "where ([Store].[Store Country].[Mexico])").returnsGrid(
        "Axis #0:\n"
          + "{[Store].[Store].[Mexico]}\n"
          + "Axis #1:\n"
          + "{[Product].[Product].[Drink]}\n"
          + "{[Product].[Product].[Food]}\n"
          + "{[Product].[Product].[Non-Consumable]}\n"
          + "Row #0: \n"
          + "Row #0: \n"
          + "Row #0: \n" );
    } finally {
      connection.close();
    }
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "false", type = Boolean.class)
  void testDependentSlicerMemberNonNative(Context<?> context)  {

    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    //final TestContext<?> context = getTestContext().withFreshConnection();
    Connection connection = context.getConnectionWithDefaultRole();
    try {
      assertThatQuery(connection,
        "with set [p] as '[Product].[Product Family].members' "
          + "set [s] as '[Store].[Store Country].members' "
          + "set [ne] as 'nonemptycrossjoin([p],[s])' "
          + "set [nep] as 'Generate([ne],{[Product].CurrentMember})' "
          + "select [nep] on columns from sales "
          + "where ([Time].[1998])").returnsGrid(
        "Axis #0:\n"
          + "{[Time].[Time].[1998]}\n"
          + "Axis #1:\n" );
    } finally {
      connection.close();
    }
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  void testDependentSlicerMemberNative(Context<?> context)  {

    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    //final TestContext<?> context = getTestContext().withFreshConnection();
    Connection connection = context.getConnectionWithDefaultRole();
    try {
      assertThatQuery(connection,
        "with set [p] as '[Product].[Product Family].members' "
          + "set [s] as '[Store].[Store Country].members' "
          + "set [ne] as 'nonemptycrossjoin([p],[s])' "
          + "set [nep] as 'Generate([ne],{[Product].CurrentMember})' "
          + "select [nep] on columns from sales "
          + "where ([Time].[1998])").returnsGrid(
        "Axis #0:\n"
          + "{[Time].[Time].[1998]}\n"
          + "Axis #1:\n" );
    } finally {
      connection.close();
    }
  }

  /**
   * Tests bug 1791609, "CrossJoin non empty optimizer eliminates calculated member".
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testBug1791609NonEmptyCrossJoinEliminatesCalcMember(Context<?> context)  {
    if ( !Bug.Bug328Fixed ) {
      return;
    }
    // From the bug:
    //   With NON EMPTY (mondrian.rolap.nonempty) behavior set to true
    //   the following mdx return no result. The same mdx returns valid
    // result when NON EMPTY is turned off.
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH \n"
        + "MEMBER Measures.Calc AS '[Measures].[Profit] * 2', SOLVE_ORDER=1000\n"
        + "MEMBER Product.Conditional as 'Iif (Measures.CurrentMember IS Measures.[Calc], "
        + "Measures.CurrentMember, null)', SOLVE_ORDER=2000\n"
        + "SET [S2] AS '{[Store].MEMBERS}' \n"
        + "SET [S1] AS 'CROSSJOIN({[Customers].[All Customers]},{Product.Conditional})' \n"
        + "SELECT \n"
        + "NON EMPTY GENERATE({Measures.[Calc]}, \n"
        + "          CROSSJOIN(HEAD( {([Measures].CURRENTMEMBER)}, \n"
        + "                           1\n"
        + "                        ), \n"
        + "                     {[S1]}\n"
        + "                  ), \n"
        + "                   ALL\n"
        + "                 ) \n"
        + "                                   ON AXIS(0), \n"
        + "NON EMPTY [S2] ON AXIS(1) \n"
        + "FROM [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Calc], [Customers].[All Customers], [Product].[Conditional]}\n"
        + "Axis #2:\n"
        + "{[Store].[All Stores]}\n"
        + "{[Store].[USA]}\n"
        + "{[Store].[USA].[CA]}\n"
        + "{[Store].[USA].[CA].[Beverly Hills]}\n"
        + "{[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
        + "{[Store].[USA].[CA].[Los Angeles]}\n"
        + "{[Store].[USA].[CA].[Los Angeles].[Store 7]}\n"
        + "{[Store].[USA].[CA].[San Diego]}\n"
        + "{[Store].[USA].[CA].[San Diego].[Store 24]}\n"
        + "{[Store].[USA].[CA].[San Francisco]}\n"
        + "{[Store].[USA].[CA].[San Francisco].[Store 14]}\n"
        + "{[Store].[USA].[OR]}\n"
        + "{[Store].[USA].[OR].[Portland]}\n"
        + "{[Store].[USA].[OR].[Portland].[Store 11]}\n"
        + "{[Store].[USA].[OR].[Salem]}\n"
        + "{[Store].[USA].[OR].[Salem].[Store 13]}\n"
        + "{[Store].[USA].[WA]}\n"
        + "{[Store].[USA].[WA].[Bellingham]}\n"
        + "{[Store].[USA].[WA].[Bellingham].[Store 2]}\n"
        + "{[Store].[USA].[WA].[Bremerton]}\n"
        + "{[Store].[USA].[WA].[Bremerton].[Store 3]}\n"
        + "{[Store].[USA].[WA].[Seattle]}\n"
        + "{[Store].[USA].[WA].[Seattle].[Store 15]}\n"
        + "{[Store].[USA].[WA].[Spokane]}\n"
        + "{[Store].[USA].[WA].[Spokane].[Store 16]}\n"
        + "{[Store].[USA].[WA].[Tacoma]}\n"
        + "{[Store].[USA].[WA].[Tacoma].[Store 17]}\n"
        + "{[Store].[USA].[WA].[Walla Walla]}\n"
        + "{[Store].[USA].[WA].[Walla Walla].[Store 22]}\n"
        + "{[Store].[USA].[WA].[Yakima]}\n"
        + "{[Store].[USA].[WA].[Yakima].[Store 23]}\n"
        + "Row #0: $679,221.79\n"
        + "Row #1: $679,221.79\n"
        + "Row #2: $191,274.83\n"
        + "Row #3: $54,967.60\n"
        + "Row #4: $54,967.60\n"
        + "Row #5: $65,547.49\n"
        + "Row #6: $65,547.49\n"
        + "Row #7: $65,435.21\n"
        + "Row #8: $65,435.21\n"
        + "Row #9: $5,324.53\n"
        + "Row #10: $5,324.53\n"
        + "Row #11: $171,009.14\n"
        + "Row #12: $66,219.69\n"
        + "Row #13: $66,219.69\n"
        + "Row #14: $104,789.45\n"
        + "Row #15: $104,789.45\n"
        + "Row #16: $316,937.82\n"
        + "Row #17: $5,685.23\n"
        + "Row #18: $5,685.23\n"
        + "Row #19: $63,548.67\n"
        + "Row #20: $63,548.67\n"
        + "Row #21: $63,374.53\n"
        + "Row #22: $63,374.53\n"
        + "Row #23: $59,677.94\n"
        + "Row #24: $59,677.94\n"
        + "Row #25: $89,769.36\n"
        + "Row #26: $89,769.36\n"
        + "Row #27: $5,651.26\n"
        + "Row #28: $5,651.26\n"
        + "Row #29: $29,230.83\n"
        + "Row #30: $29,230.83\n" );
  }

  /**
   * Test that executes &lt;Level&gt;.Members and applies a non-empty constraint. Must work regardless of whether
   * EnableNativeNonEmpty  is enabled. Testcase for bug 1722959, "NON EMPTY Level.MEMBERS
   * fails if nonempty.enable=false"
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "true", type = Boolean.class)
  void testNonEmptyLevelMembers(Context<?> context)  {
      assertThatQuery(context.getConnectionWithDefaultRole(),
        "WITH MEMBER [Measures].[One] AS '1' "
          + "SELECT "
          + "NON EMPTY {[Measures].[One], [Measures].[Store Sales]} ON rows, "
          + "NON EMPTY [Store].[Store State].MEMBERS on columns "
          + "FROM sales").returnsGrid(
        "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n"
          + "{[Store].[Store].[Canada].[BC]}\n"
          + "{[Store].[Store].[Mexico].[DF]}\n"
          + "{[Store].[Store].[Mexico].[Guerrero]}\n"
          + "{[Store].[Store].[Mexico].[Jalisco]}\n"
          + "{[Store].[Store].[Mexico].[Veracruz]}\n"
          + "{[Store].[Store].[Mexico].[Yucatan]}\n"
          + "{[Store].[Store].[Mexico].[Zacatecas]}\n"
          + "{[Store].[Store].[USA].[CA]}\n"
          + "{[Store].[Store].[USA].[OR]}\n"
          + "{[Store].[Store].[USA].[WA]}\n"
          + "Axis #2:\n"
          + "{[Measures].[One]}\n"
          + "{[Measures].[Store Sales]}\n"
          + "Row #0: 1\n"
          + "Row #0: 1\n"
          + "Row #0: 1\n"
          + "Row #0: 1\n"
          + "Row #0: 1\n"
          + "Row #0: 1\n"
          + "Row #0: 1\n"
          + "Row #0: 1\n"
          + "Row #0: 1\n"
          + "Row #0: 1\n"
          + "Row #1: \n"
          + "Row #1: \n"
          + "Row #1: \n"
          + "Row #1: \n"
          + "Row #1: \n"
          + "Row #1: \n"
          + "Row #1: \n"
          + "Row #1: 159,167.84\n"
          + "Row #1: 142,277.07\n"
          + "Row #1: 263,793.22\n" );
    // Bug.Bug446Fixed is a compile-time-constant false, so the second half of
    // this test (re-running with EnableNativeNonEmpty=true) was unreachable.
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonEmptyResults(Context<?> context)  {
    // This unit test was failing with a NullPointerException in JPivot
    // after the highcardinality feature was added, I've included it
    // here to make sure it continues to work.
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select NON EMPTY {[Measures].[Unit Sales], [Measures].[Store Cost]} ON columns, "
        + "NON EMPTY Filter([Product].[Brand Name].Members, ([Measures].[Unit Sales] > 100000.0)) ON rows "
        + "from [Sales] where [Time].[1997]").returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[1997]}\n"
        + "Axis #1:\n"
        + "Axis #2:\n" );
  }

  /**
   * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-412"> MONDRIAN-412, "NON EMPTY and Filter() breaking
   * aggregate calculations"</a>.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testBugMondrian412(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member [Measures].[AvgRevenue] as 'Avg([Store].[Store Name].Members, [Measures].[Store Sales])' "
        + "select NON EMPTY {[Measures].[Store Sales], [Measures].[AvgRevenue]} ON COLUMNS, "
        + "NON EMPTY Filter([Store].[Store Name].Members, ([Measures].[AvgRevenue] < [Measures].[Store Sales])) ON "
        + "ROWS "
        + "from [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Sales]}\n"
        + "{[Measures].[AvgRevenue]}\n"
        + "Axis #2:\n"
        + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
        + "{[Store].[Store].[USA].[CA].[Los Angeles].[Store 7]}\n"
        + "{[Store].[Store].[USA].[CA].[San Diego].[Store 24]}\n"
        + "{[Store].[Store].[USA].[OR].[Portland].[Store 11]}\n"
        + "{[Store].[Store].[USA].[OR].[Salem].[Store 13]}\n"
        + "{[Store].[Store].[USA].[WA].[Bremerton].[Store 3]}\n"
        + "{[Store].[Store].[USA].[WA].[Seattle].[Store 15]}\n"
        + "{[Store].[Store].[USA].[WA].[Spokane].[Store 16]}\n"
        + "{[Store].[Store].[USA].[WA].[Tacoma].[Store 17]}\n"
        + "Row #0: 45,750.24\n"
        + "Row #0: 43,479.86\n"
        + "Row #1: 54,545.28\n"
        + "Row #1: 43,479.86\n"
        + "Row #2: 54,431.14\n"
        + "Row #2: 43,479.86\n"
        + "Row #3: 55,058.79\n"
        + "Row #3: 43,479.86\n"
        + "Row #4: 87,218.28\n"
        + "Row #4: 43,479.86\n"
        + "Row #5: 52,896.30\n"
        + "Row #5: 43,479.86\n"
        + "Row #6: 52,644.07\n"
        + "Row #6: 43,479.86\n"
        + "Row #7: 49,634.46\n"
        + "Row #7: 43,479.86\n"
        + "Row #8: 74,843.96\n"
        + "Row #8: 43,479.86\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonEmpyOnVirtualCubeWithNonJoiningDimension(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select non empty {[Warehouse].[Warehouse name].members} on 0,"
        + "{[Measures].[Units Shipped],[Measures].[Unit Sales]} on 1"
        + " from [Warehouse and Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[Beverly Hills].[Big  Quality Warehouse]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[Los Angeles].[Artesia Warehousing, Inc.]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[San Diego].[Jorgensen Service Storage]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[San Francisco].[Food Service Storage, Inc.]}\n"
        + "{[Warehouse].[Warehouse].[USA].[OR].[Portland].[Quality Distribution, Inc.]}\n"
        + "{[Warehouse].[Warehouse].[USA].[OR].[Salem].[Treehouse Distribution]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Bellingham].[Foster Products]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Bremerton].[Destination, Inc.]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Seattle].[Quality Warehousing and Trucking]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Spokane].[Jones International]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Tacoma].[Jorge Garcia, Inc.]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Walla Walla].[Valdez Warehousing]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Yakima].[Maddock Stored Foods]}\n"
        + "Axis #2:\n"
        + "{[Measures].[Units Shipped]}\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Row #0: 10759.0\n"
        + "Row #0: 24587.0\n"
        + "Row #0: 23835.0\n"
        + "Row #0: 1696.0\n"
        + "Row #0: 8515.0\n"
        + "Row #0: 32393.0\n"
        + "Row #0: 2348.0\n"
        + "Row #0: 22734.0\n"
        + "Row #0: 24110.0\n"
        + "Row #0: 11889.0\n"
        + "Row #0: 32411.0\n"
        + "Row #0: 1860.0\n"
        + "Row #0: 10589.0\n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: \n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonEmptyOnNonJoiningValidMeasure(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member [Measures].[vm] as 'ValidMeasure([Measures].[Unit Sales])'"
        + "select non empty {[Warehouse].[Warehouse name].members} on 0,"
        + "{[Measures].[Units Shipped],[Measures].[vm]} on 1"
        + " from [Warehouse and Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[Beverly Hills].[Big  Quality Warehouse]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[Los Angeles].[Artesia Warehousing, Inc.]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[San Diego].[Jorgensen Service Storage]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[San Francisco].[Food Service Storage, Inc.]}\n"
        + "{[Warehouse].[Warehouse].[USA].[OR].[Portland].[Quality Distribution, Inc.]}\n"
        + "{[Warehouse].[Warehouse].[USA].[OR].[Salem].[Treehouse Distribution]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Bellingham].[Foster Products]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Bremerton].[Destination, Inc.]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Seattle].[Quality Warehousing and Trucking]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Spokane].[Jones International]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Tacoma].[Jorge Garcia, Inc.]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Walla Walla].[Valdez Warehousing]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Yakima].[Maddock Stored Foods]}\n"
        + "Axis #2:\n"
        + "{[Measures].[Units Shipped]}\n"
        + "{[Measures].[vm]}\n"
        + "Row #0: 10759.0\n"
        + "Row #0: 24587.0\n"
        + "Row #0: 23835.0\n"
        + "Row #0: 1696.0\n"
        + "Row #0: 8515.0\n"
        + "Row #0: 32393.0\n"
        + "Row #0: 2348.0\n"
        + "Row #0: 22734.0\n"
        + "Row #0: 24110.0\n"
        + "Row #0: 11889.0\n"
        + "Row #0: 32411.0\n"
        + "Row #0: 1860.0\n"
        + "Row #0: 10589.0\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n"
        + "Row #1: 266,773\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossjoinWithTwoDimensionsJoiningToOppositeBaseCubes(Context<?> context)  {
    // This test formerly expected an empty result set,
    // which is actually inconsistent with SSAS.  Since ValidMeasure forces
    // Warehouse to the [All] level when evaluating the [vm] measure,
    // the results should include each [warehouse name] member intersected
    // with the non-empty Gender members.
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member [Measures].[vm] as 'ValidMeasure([Measures].[Unit Sales])'\n"
        + "select non empty Crossjoin([Warehouse].[Warehouse].[Warehouse Name].members, [Gender].[Gender].[Gender].members) on 0,\n"
        + "{[Measures].[Units Shipped],[Measures].[vm]} on 1\n"
        + "from [Warehouse and Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[Beverly Hills].[Big  Quality Warehouse], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[Beverly Hills].[Big  Quality Warehouse], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[Los Angeles].[Artesia Warehousing, Inc.], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[Los Angeles].[Artesia Warehousing, Inc.], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[San Diego].[Jorgensen Service Storage], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[San Diego].[Jorgensen Service Storage], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[San Francisco].[Food Service Storage, Inc.], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[CA].[San Francisco].[Food Service Storage, Inc.], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[OR].[Portland].[Quality Distribution, Inc.], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[OR].[Portland].[Quality Distribution, Inc.], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[OR].[Salem].[Treehouse Distribution], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[OR].[Salem].[Treehouse Distribution], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Bellingham].[Foster Products], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Bellingham].[Foster Products], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Bremerton].[Destination, Inc.], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Bremerton].[Destination, Inc.], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Seattle].[Quality Warehousing and Trucking], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Seattle].[Quality Warehousing and Trucking], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Spokane].[Jones International], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Spokane].[Jones International], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Tacoma].[Jorge Garcia, Inc.], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Tacoma].[Jorge Garcia, Inc.], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Walla Walla].[Valdez Warehousing], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Walla Walla].[Valdez Warehousing], [Gender].[Gender].[M]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Yakima].[Maddock Stored Foods], [Gender].[Gender].[F]}\n"
        + "{[Warehouse].[Warehouse].[USA].[WA].[Yakima].[Maddock Stored Foods], [Gender].[Gender].[M]}\n"
        + "Axis #2:\n"
        + "{[Measures].[Units Shipped]}\n"
        + "{[Measures].[vm]}\n"
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
        + "Row #0: \n"
        + "Row #0: \n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n"
        + "Row #1: 131,558\n"
        + "Row #1: 135,215\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossjoinWithOneDimensionThatDoesNotJoinToBothBaseCubes(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member [Measures].[vm] as 'ValidMeasure([Measures].[Units Shipped])'"
        + "select non empty Crossjoin([Store].[Store].[Store Name].members, [Gender].[Gender].[Gender].members) on 0,"
        + "{[Measures].[Unit Sales],[Measures].[vm]} on 1"
        + " from [Warehouse and Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[CA].[Los Angeles].[Store 7], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[CA].[Los Angeles].[Store 7], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[CA].[San Diego].[Store 24], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[CA].[San Diego].[Store 24], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[CA].[San Francisco].[Store 14], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[CA].[San Francisco].[Store 14], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[OR].[Portland].[Store 11], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[OR].[Portland].[Store 11], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[OR].[Salem].[Store 13], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[OR].[Salem].[Store 13], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham].[Store 2], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[WA].[Bellingham].[Store 2], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[WA].[Bremerton].[Store 3], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[WA].[Bremerton].[Store 3], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[WA].[Seattle].[Store 15], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[WA].[Seattle].[Store 15], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[WA].[Spokane].[Store 16], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[WA].[Spokane].[Store 16], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[WA].[Tacoma].[Store 17], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[WA].[Tacoma].[Store 17], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[WA].[Walla Walla].[Store 22], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[WA].[Walla Walla].[Store 22], [Gender].[Gender].[M]}\n"
        + "{[Store].[Store].[USA].[WA].[Yakima].[Store 23], [Gender].[Gender].[F]}\n"
        + "{[Store].[Store].[USA].[WA].[Yakima].[Store 23], [Gender].[Gender].[M]}\n"
        + "Axis #2:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "{[Measures].[vm]}\n"
        + "Row #0: 10,771\n"
        + "Row #0: 10,562\n"
        + "Row #0: 12,089\n"
        + "Row #0: 13,574\n"
        + "Row #0: 12,835\n"
        + "Row #0: 12,800\n"
        + "Row #0: 1,064\n"
        + "Row #0: 1,053\n"
        + "Row #0: 12,488\n"
        + "Row #0: 13,591\n"
        + "Row #0: 20,548\n"
        + "Row #0: 21,032\n"
        + "Row #0: 1,096\n"
        + "Row #0: 1,141\n"
        + "Row #0: 11,640\n"
        + "Row #0: 12,936\n"
        + "Row #0: 13,513\n"
        + "Row #0: 11,498\n"
        + "Row #0: 12,068\n"
        + "Row #0: 11,523\n"
        + "Row #0: 17,420\n"
        + "Row #0: 17,837\n"
        + "Row #0: 1,019\n"
        + "Row #0: 1,184\n"
        + "Row #0: 5,007\n"
        + "Row #0: 6,484\n"
        + "Row #1: 10759.0\n"
        + "Row #1: 10759.0\n"
        + "Row #1: 24587.0\n"
        + "Row #1: 24587.0\n"
        + "Row #1: 23835.0\n"
        + "Row #1: 23835.0\n"
        + "Row #1: 1696.0\n"
        + "Row #1: 1696.0\n"
        + "Row #1: 8515.0\n"
        + "Row #1: 8515.0\n"
        + "Row #1: 32393.0\n"
        + "Row #1: 32393.0\n"
        + "Row #1: 2348.0\n"
        + "Row #1: 2348.0\n"
        + "Row #1: 22734.0\n"
        + "Row #1: 22734.0\n"
        + "Row #1: 24110.0\n"
        + "Row #1: 24110.0\n"
        + "Row #1: 11889.0\n"
        + "Row #1: 11889.0\n"
        + "Row #1: 32411.0\n"
        + "Row #1: 32411.0\n"
        + "Row #1: 1860.0\n"
        + "Row #1: 1860.0\n"
        + "Row #1: 10589.0\n"
        + "Row #1: 10589.0\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testLeafMembersOfParentChildDimensionAreNativelyEvaluated(Context<?> context)  {
    final String query = "SELECT"
      + " NON EMPTY "
      + "Crossjoin("
      + "{"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Gabriel "
      + "Walton],"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Bishop "
      + "Meastas],"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Paula "
      + "Duran],"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Margaret "
      + "Earley],"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Elizabeth"
      + " Horne]"
      + "},"
      + "[Store].[Store Name].members"
      + ") on 0 from hr";
    checkNative(context, 50, 5, query );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonLeafMembersOfPCDimensionAreNotNativelyEvaluated(Context<?> context)  {
    final String query = "SELECT"
      + " NON EMPTY "
      + "Crossjoin("
      + "{"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Beverly Baker],"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Gabriel "
      + "Walton],"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin],"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays],"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley],"
      + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Elizabeth"
      + " Horne]"
      + "},"
      + "[Store].[Store Name].members"
      + ") on 0 from hr";
    checkNotNative(context, 9, query );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.NULL_MEMBER_REPRESENTATION, value = "~Missing ", type = String.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "true", type = Boolean.class)
  void testNativeWithOverriddenNullMemberRepAndNullConstraint(Context<?> context)  {
    String preMdx = "SELECT FROM [Sales]";

    String mdx =
      "SELECT \n"
        + "  [Gender].[Gender].MEMBERS ON ROWS\n"
        + " ,{[Measures].[Unit Sales]} ON COLUMNS\n"
        + "FROM [Sales]\n"
        + "WHERE \n"
        + "  [Store Size in SQFT].[All Store Size in SQFTs].[~Missing ]";

    // run an mdx query with the default NullMemberRepresentation
    executeQuery(preMdx, context.getConnectionWithDefaultRole());



    executeQuery(mdx, context.getConnectionWithDefaultRole());
  }

  /**
   * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-321"> MONDRIAN-321, "CrossJoin has no nulls when
   * EnableNativeNonEmpty=true"</a>.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testBugMondrian321(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH SET [#DataSet#] AS 'Crossjoin({Descendants([Customers].[All Customers], 2)}, {[Product].[All Products]})'"
        + " \n"
        + "SELECT {[Measures].[Unit Sales], [Measures].[Store Sales]} on columns, \n"
        + "NON EMPTY Hierarchize({[#DataSet#]}) on rows FROM [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "{[Measures].[Store Sales]}\n"
        + "Axis #2:\n"
        + "{[Customers].[Customers].[USA].[CA], [Product].[Product].[All Products]}\n"
        + "{[Customers].[Customers].[USA].[OR], [Product].[Product].[All Products]}\n"
        + "{[Customers].[Customers].[USA].[WA], [Product].[Product].[All Products]}\n"
        + "Row #0: 74,748\n"
        + "Row #0: 159,167.84\n"
        + "Row #1: 67,659\n"
        + "Row #1: 142,277.07\n"
        + "Row #2: 124,366\n"
        + "Row #2: 263,793.22\n" );

    NativeVerify.assertSameNativeAndNot(context,
      "WITH SET [#DataSet#] AS 'Crossjoin({Descendants([Customers].[All Customers], 2)}, {[Product].[All Products]})'"
        + " \n"
        + "SELECT {[Measures].[Unit Sales], [Measures].[Store Sales]} on columns, \n"
        + "NON EMPTY Hierarchize({[#DataSet#]}) on rows FROM [Sales]",
      "testBugMondrian321 failed"
    );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeCrossjoinWillConstrainUsingArgsFromAllAxes(Context<?> context)  {
    String mdx = "select "
      + "non empty Crossjoin({[Gender].[Gender].[F]},{[Measures].[Unit Sales]}) on 0,"
      + "non empty Crossjoin({[Time].[1997]},{[Promotions].[All Promotions].[Bag Stuffers],[Promotions].[All "
      + "Promotions].[Best Savings]}) on 1"
      + " from [Warehouse and Sales]";
    SqlPattern oraclePattern = new SqlPattern(
      DatabaseProduct.ORACLE,
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        ? "select\n"
        + "    \"agg_c_14_sales_fact_1997\".\"the_year\" as \"c0\",\n"
        + "    \"promotion\".\"promotion_name\" as \"c1\"\n"
        + "from\n"
        + "    \"agg_c_14_sales_fact_1997\" \"agg_c_14_sales_fact_1997\",\n"
        + "    \"promotion\" \"promotion\",\n"
        + "    \"customer\" \"customer\"\n"
        + "where\n"
        + "    \"agg_c_14_sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\"\n"
        + "and\n"
        + "    \"agg_c_14_sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n"
        + "and\n"
        + "    (\"customer\".\"gender\" = 'F')\n"
        + "and\n"
        + "    (\"agg_c_14_sales_fact_1997\".\"the_year\" = 1997)\n"
        + "and\n"
        + "    (\"promotion\".\"promotion_name\" in ('Bag Stuffers', 'Best Savings'))\n"
        + "group by\n"
        + "    \"agg_c_14_sales_fact_1997\".\"the_year\",\n"
        + "    \"promotion\".\"promotion_name\"\n"
        + "order by\n"
        + "    \"agg_c_14_sales_fact_1997\".\"the_year\" ASC NULLS LAST,\n"
        + "    \"promotion\".\"promotion_name\" ASC NULLS LAST"
        : "select\n"
        + "    \"time_by_day\".\"the_year\" as \"c0\",\n"
        + "    \"promotion\".\"promotion_name\" as \"c1\"\n"
        + "from\n"
        + "    \"time_by_day\" \"time_by_day\",\n"
        + "    \"sales_fact_1997\" \"sales_fact_1997\",\n"
        + "    \"promotion\" \"promotion\",\n"
        + "    \"customer\" \"customer\"\n"
        + "where\n"
        + "    \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n"
        + "and\n"
        + "    \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\"\n"
        + "and\n"
        + "    \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n"
        + "and\n"
        + "    (\"customer\".\"gender\" = 'F')\n"
        + "and\n"
        + "    (\"time_by_day\".\"the_year\" = 1997)\n"
        + "and\n"
        + "    (\"promotion\".\"promotion_name\" in ('Bag Stuffers', 'Best Savings'))\n"
        + "group by\n"
        + "    \"time_by_day\".\"the_year\",\n"
        + "    \"promotion\".\"promotion_name\"\n"
        + "order by\n"
        + "    \"time_by_day\".\"the_year\" ASC NULLS LAST,\n"
        + "    \"promotion\".\"promotion_name\" ASC NULLS LAST",
      611 );
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).expectSql(new SqlPattern[] { oraclePattern } ).verify();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testLevelMembersWillConstrainUsingArgsFromAllAxes(Context<?> context)  {
    String mdx = "select "
      + "non empty Crossjoin({[Gender].[Gender].[F]},{[Measures].[Unit Sales]}) on 0,"
      + "non empty [Promotions].[Promotions].members on 1"
      + " from [Warehouse and Sales]";
    SqlPattern oraclePattern = new SqlPattern(
      DatabaseProduct.ORACLE,
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        ? "select\n"
        + "    \"promotion\".\"promotion_name\" as \"c0\"\n"
        + "from\n"
        + "    \"promotion\" \"promotion\",\n"
        + "    \"agg_c_14_sales_fact_1997\" \"agg_c_14_sales_fact_1997\",\n"
        + "    \"customer\" \"customer\"\n"
        + "where\n"
        + "    \"agg_c_14_sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\"\n"
        + "and\n"
        + "    \"agg_c_14_sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n"
        + "and\n"
        + "    (\"customer\".\"gender\" = 'F')\n"
        + "group by\n"
        + "    \"promotion\".\"promotion_name\"\n"
        + "order by\n"
        + "    \"promotion\".\"promotion_name\" ASC NULLS LAST"
        : "select\n"
        + "    \"promotion\".\"promotion_name\" as \"c0\"\n"
        + "from\n"
        + "    \"promotion\" \"promotion\",\n"
        + "    \"sales_fact_1997\" \"sales_fact_1997\",\n"
        + "    \"customer\" \"customer\"\n"
        + "where\n"
        + "    \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\"\n"
        + "and\n"
        + "    \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n"
        + "and\n"
        + "    (\"customer\".\"gender\" = 'F')\n"
        + "group by\n"
        + "    \"promotion\".\"promotion_name\"\n"
        + "order by\n"
        + "    \"promotion\".\"promotion_name\" ASC NULLS LAST",
      347 );
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).expectSql(new SqlPattern[] { oraclePattern } ).verify();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeCrossjoinWillExpandFirstLastChild(Context<?> context)  {
    String mdx = "select "
      + "non empty Crossjoin({[Gender].firstChild,[Gender].lastChild},{[Measures].[Unit Sales]}) on 0,"
      + "non empty Crossjoin({[Time].[1997]},{[Promotions].[All Promotions].[Bag Stuffers],[Promotions].[All "
      + "Promotions].[Best Savings]}) on 1"
      + " from [Warehouse and Sales]";
    final SqlPattern pattern = new SqlPattern(
      DatabaseProduct.ORACLE,
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        ? "select\n"
        + "    \"agg_c_14_sales_fact_1997\".\"the_year\" as \"c0\",\n"
        + "    \"promotion\".\"promotion_name\" as \"c1\"\n"
        + "from\n"
        + "    \"agg_c_14_sales_fact_1997\" \"agg_c_14_sales_fact_1997\",\n"
        + "    \"promotion\" \"promotion\",\n"
        + "    \"customer\" \"customer\"\n"
        + "where\n"
        + "    \"agg_c_14_sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\"\n"
        + "and\n"
        + "    \"agg_c_14_sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n"
        + "and\n"
        + "    (\"customer\".\"gender\" in ('F', 'M'))\n"
        + "and\n"
        + "    (\"agg_c_14_sales_fact_1997\".\"the_year\" = 1997)\n"
        + "and\n"
        + "    (\"promotion\".\"promotion_name\" in ('Bag Stuffers', 'Best Savings'))\n"
        + "group by\n"
        + "    \"agg_c_14_sales_fact_1997\".\"the_year\",\n"
        + "    \"promotion\".\"promotion_name\"\n"
        + "order by\n"
        + "    \"agg_c_14_sales_fact_1997\".\"the_year\" ASC NULLS LAST,\n"
        + "    \"promotion\".\"promotion_name\" ASC NULLS LAST"
        : "select\n"
        + "    \"time_by_day\".\"the_year\" as \"c0\",\n"
        + "    \"promotion\".\"promotion_name\" as \"c1\"\n"
        + "from\n"
        + "    \"time_by_day\" \"time_by_day\",\n"
        + "    \"sales_fact_1997\" \"sales_fact_1997\",\n"
        + "    \"promotion\" \"promotion\",\n"
        + "    \"customer\" \"customer\"\n"
        + "where\n"
        + "    \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n"
        + "and\n"
        + "    \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\"\n"
        + "and\n"
        + "    \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n"
        + "and\n"
        + "    (\"customer\".\"gender\" in ('F', 'M'))\n"
        + "and\n"
        + "    (\"time_by_day\".\"the_year\" = 1997)\n"
        + "and\n"
        + "    (\"promotion\".\"promotion_name\" in ('Bag Stuffers', 'Best Savings'))\n"
        + "group by\n"
        + "    \"time_by_day\".\"the_year\",\n"
        + "    \"promotion\".\"promotion_name\"\n"
        + "order by\n"
        + "    \"time_by_day\".\"the_year\" ASC NULLS LAST,\n"
        + "    \"promotion\".\"promotion_name\" ASC NULLS LAST",
      611 );
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).expectSql(new SqlPattern[] { pattern } ).verify();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeCrossjoinWillExpandLagInNamedSet(Context<?> context)  {
    String mdx =
      "with set [blah] as '{[Gender].lastChild.lag(1),[Gender].[M]}' "
        + "select "
        + "non empty Crossjoin([blah],{[Measures].[Unit Sales]}) on 0,"
        + "non empty Crossjoin({[Time].[1997]},{[Promotions].[All Promotions].[Bag Stuffers],[Promotions].[All "
        + "Promotions].[Best Savings]}) on 1"
        + " from [Warehouse and Sales]";
    final SqlPattern pattern = new SqlPattern(
      DatabaseProduct.ORACLE,
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        ? "select\n"
        + "    \"agg_c_14_sales_fact_1997\".\"the_year\" as \"c0\",\n"
        + "    \"promotion\".\"promotion_name\" as \"c1\"\n"
        + "from\n"
        + "    \"agg_c_14_sales_fact_1997\" \"agg_c_14_sales_fact_1997\",\n"
        + "    \"promotion\" \"promotion\",\n"
        + "    \"customer\" \"customer\"\n"
        + "where\n"
        + "    \"agg_c_14_sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\"\n"
        + "and\n"
        + "    \"agg_c_14_sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n"
        + "and\n"
        + "    (\"customer\".\"gender\" in ('F', 'M'))\n"
        + "and\n"
        + "    (\"agg_c_14_sales_fact_1997\".\"the_year\" = 1997)\n"
        + "and\n"
        + "    (\"promotion\".\"promotion_name\" in ('Bag Stuffers', 'Best Savings'))\n"
        + "group by\n"
        + "    \"agg_c_14_sales_fact_1997\".\"the_year\",\n"
        + "    \"promotion\".\"promotion_name\"\n"
        + "order by\n"
        + "    \"agg_c_14_sales_fact_1997\".\"the_year\" ASC NULLS LAST,\n"
        + "    \"promotion\".\"promotion_name\" ASC NULLS LAST"
        : "select\n"
        + "    \"time_by_day\".\"the_year\" as \"c0\",\n"
        + "    \"promotion\".\"promotion_name\" as \"c1\"\n"
        + "from\n"
        + "    \"time_by_day\" \"time_by_day\",\n"
        + "    \"sales_fact_1997\" \"sales_fact_1997\",\n"
        + "    \"promotion\" \"promotion\",\n"
        + "    \"customer\" \"customer\"\n"
        + "where\n"
        + "    \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n"
        + "and\n"
        + "    \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\"\n"
        + "and\n"
        + "    \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n"
        + "and\n"
        + "    (\"customer\".\"gender\" in ('F', 'M'))\n"
        + "and\n"
        + "    (\"time_by_day\".\"the_year\" = 1997)\n"
        + "and\n"
        + "    (\"promotion\".\"promotion_name\" in ('Bag Stuffers', 'Best Savings'))\n"
        + "group by\n"
        + "    \"time_by_day\".\"the_year\",\n"
        + "    \"promotion\".\"promotion_name\"\n"
        + "order by\n"
        + "    \"time_by_day\".\"the_year\" ASC NULLS LAST,\n"
        + "    \"promotion\".\"promotion_name\" ASC NULLS LAST",
      611 );
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).expectSql(new SqlPattern[] { pattern } ).verify();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testConstrainedMeasureGetsOptimized(Context<?> context)  {
    String mdx =
      "with member [Measures].[unit sales Male] as '([Measures].[Unit Sales],[Gender].[Gender].[M])' "
        + "member [Measures].[unit sales Female] as '([Measures].[Unit Sales],[Gender].[Gender].[F])' "
        + "member [Measures].[store sales Female] as '([Measures].[Store Sales],[Gender].[Gender].[F])' "
        + "member [Measures].[literal one] as '1' "
        + "select "
        + "non empty {{[Measures].[unit sales Male]}, {([Measures].[literal one])}, "
        + "[Measures].[unit sales Female], [Measures].[store sales Female]} on 0, "
        + "non empty [Customers].[name].members on 1 "
        + "from Sales";
    final String sqlOracle =
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        ? "select \"customer\".\"country\" as \"c0\","
        + " \"customer\".\"state_province\" as \"c1\", \"customer\".\"city\" as \"c2\", \"customer\".\"customer_id\" "
        + "as \"c3\", \"fname\" || ' ' || \"lname\" as \"c4\", \"fname\" || ' ' || \"lname\" as \"c5\", \"customer\""
        + ".\"gender\" as \"c6\", \"customer\".\"marital_status\" as \"c7\", \"customer\".\"education\" as \"c8\", "
        + "\"customer\".\"yearly_income\" as \"c9\" from \"customer\" \"customer\", \"agg_l_03_sales_fact_1997\" "
        + "\"agg_l_03_sales_fact_1997\" where \"agg_l_03_sales_fact_1997\".\"customer_id\" = \"customer\""
        + ".\"customer_id\" and (\"customer\".\"gender\" in ('M', 'F')) group by \"customer\".\"country\", "
        + "\"customer\".\"state_province\", \"customer\".\"city\", \"customer\".\"customer_id\", \"fname\" || ' ' || "
        + "\"lname\", \"customer\".\"gender\", \"customer\".\"marital_status\", \"customer\".\"education\", "
        + "\"customer\".\"yearly_income\" order by \"customer\".\"country\" ASC NULLS LAST, \"customer\""
        + ".\"state_province\" ASC NULLS LAST, \"customer\".\"city\" ASC NULLS LAST, \"fname\" || ' ' || \"lname\" "
        + "ASC NULLS LAST"
        : "select \"customer\".\"country\" as \"c0\","
        + " \"customer\".\"state_province\" as \"c1\", \"customer\".\"city\" as \"c2\", \"customer\".\"customer_id\" "
        + "as \"c3\", \"fname\" || ' ' || \"lname\" as \"c4\", \"fname\" || ' ' || \"lname\" as \"c5\", \"customer\""
        + ".\"gender\" as \"c6\", \"customer\".\"marital_status\" as \"c7\", \"customer\".\"education\" as \"c8\", "
        + "\"customer\".\"yearly_income\" as \"c9\" from \"customer\" \"customer\", \"sales_fact_1997\" "
        + "\"sales_fact_1997\" where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" and "
        + "(\"customer\".\"gender\" in ('M', 'F')) group by \"customer\".\"country\", \"customer\""
        + ".\"state_province\", \"customer\".\"city\", \"customer\".\"customer_id\", \"fname\" || ' ' || \"lname\", "
        + "\"customer\".\"gender\", \"customer\".\"marital_status\", \"customer\".\"education\", \"customer\""
        + ".\"yearly_income\" order by \"customer\".\"country\" ASC NULLS LAST, \"customer\".\"state_province\" ASC "
        + "NULLS LAST, \"customer\".\"city\" ASC NULLS LAST, \"fname\" || ' ' || \"lname\" ASC NULLS LAST";
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
      mdx).expectSql(new SqlPattern[] {
        new SqlPattern(
          DatabaseProduct.ORACLE,
          sqlOracle,
          sqlOracle.length() ) } ).verify();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNestedMeasureConstraintsGetOptimized(Context<?> context)  {
    String mdx =
      "with member [Measures].[unit sales Male] as '([Measures].[Unit Sales],[Gender].[Gender].[M])' "
        + "member [Measures].[unit sales Male Married] as '([Measures].[unit sales Male],[Marital Status].[Marital "
        + "Status].[M])' "
        + "select "
        + "non empty {[Measures].[unit sales Male Married]} on 0, "
        + "non empty [Customers].[name].members on 1 "
        + "from Sales";
    final String sqlOracle =
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        ? "select \"customer\".\"country\" as \"c0\","
        + " \"customer\".\"state_province\" as \"c1\", \"customer\".\"city\" as \"c2\", \"customer\".\"customer_id\" "
        + "as \"c3\", \"fname\" || ' ' || \"lname\" as \"c4\", \"fname\" || ' ' || \"lname\" as \"c5\", \"customer\""
        + ".\"gender\" as \"c6\", \"customer\".\"marital_status\" as \"c7\", \"customer\".\"education\" as \"c8\", "
        + "\"customer\".\"yearly_income\" as \"c9\" from \"customer\" \"customer\", \"agg_l_03_sales_fact_1997\" "
        + "\"agg_l_03_sales_fact_1997\" where \"agg_l_03_sales_fact_1997\".\"customer_id\" = \"customer\""
        + ".\"customer_id\" and (\"customer\".\"gender\" = 'M') and (\"customer\".\"marital_status\" = 'M') group by "
        + "\"customer\".\"country\", \"customer\".\"state_province\", \"customer\".\"city\", \"customer\""
        + ".\"customer_id\", \"fname\" || ' ' || \"lname\", \"customer\".\"gender\", \"customer\".\"marital_status\","
        + " \"customer\".\"education\", \"customer\".\"yearly_income\" order by \"customer\".\"country\" ASC NULLS "
        + "LAST, \"customer\".\"state_province\" ASC NULLS LAST, \"customer\".\"city\" ASC NULLS LAST, \"fname\" || '"
        + " ' || \"lname\" ASC NULLS LAST"
        : "select \"customer\".\"country\" as \"c0\", "
        + "\"customer\".\"state_province\" as \"c1\", "
        + "\"customer\".\"city\" as \"c2\", "
        + "\"customer\".\"customer_id\" as \"c3\", "
        + "\"fname\" || \" \" || \"lname\" as \"c4\", "
        + "\"fname\" || \" \" || \"lname\" as \"c5\", "
        + "\"customer\".\"gender\" as \"c6\", "
        + "\"customer\".\"marital_status\" as \"c7\", "
        + "\"customer\".\"education\" as \"c8\", "
        + "\"customer\".\"yearly_income\" as \"c9\" "
        + "from \"customer\" \"customer\", "
        + "\"sales_fact_1997\" \"sales_fact_1997\" "
        + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
        + "and (\"customer\".\"gender\" = \"M\") "
        + "and (\"customer\".\"marital_status\" = \"M\") "
        + "group by \"customer\".\"country\", "
        + "\"customer\".\"state_province\", "
        + "\"customer\".\"city\", "
        + "\"customer\".\"customer_id\", "
        + "\"fname\" || \" \" || \"lname\", "
        + "\"customer\".\"gender\", "
        + "\"customer\".\"marital_status\", "
        + "\"customer\".\"education\", "
        + "\"customer\".\"yearly_income\" "
        + "order by \"customer\".\"country\" ASC NULLS LAST, "
        + "\"customer\".\"state_province\" ASC NULLS LAST, "
        + "\"customer\".\"city\" ASC NULLS LAST, "
        + "\"fname\" || \" \" || \"lname\" ASC NULLS LAST";
    SqlPattern pattern = new SqlPattern(
      DatabaseProduct.ORACLE,
      sqlOracle,
      sqlOracle.length() );
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).expectSql(new SqlPattern[] { pattern } ).verify();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonUniformNestedMeasureConstraintsGetOptimized(Context<?> context)  {
    if ( context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class) ) {
      // This test can't work with aggregates becaused
      // the aggregate table doesn't include member properties.
      return;
    }
    String mdx =
      "with member [Measures].[unit sales Male] as '([Measures].[Unit Sales],[Gender].[Gender].[M])' "
        + "member [Measures].[unit sales Female] as '([Measures].[Unit Sales],[Gender].[Gender].[F])' "
        + "member [Measures].[unit sales Male Married] as '([Measures].[unit sales Male],[Marital Status].[Marital "
        + "Status].[M])' "
        + "select "
        + "non empty {[Measures].[unit sales Male Married],[Measures].[unit sales Female]} on 0, "
        + "non empty [Customers].[name].members on 1 "
        + "from Sales";
    final SqlPattern pattern = new SqlPattern(
      DatabaseProduct.ORACLE,
      "select \"customer\".\"country\" as \"c0\", "
        + "\"customer\".\"state_province\" as \"c1\", "
        + "\"customer\".\"city\" as \"c2\", "
        + "\"customer\".\"customer_id\" as \"c3\", "
        + "\"fname\" || ' ' || \"lname\" as \"c4\", "
        + "\"fname\" || ' ' || \"lname\" as \"c5\", "
        + "\"customer\".\"gender\" as \"c6\", "
        + "\"customer\".\"marital_status\" as \"c7\", "
        + "\"customer\".\"education\" as \"c8\", "
        + "\"customer\".\"yearly_income\" as \"c9\" "
        + "from \"customer\" \"customer\", \"sales_fact_1997\" \"sales_fact_1997\" "
        + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
        + "and (\"customer\".\"gender\" in ('M', 'F')) "
        + "group by \"customer\".\"country\", "
        + "\"customer\".\"state_province\", "
        + "\"customer\".\"city\", "
        + "\"customer\".\"customer_id\", "
        + "\"fname\" || ' ' || \"lname\", "
        + "\"customer\".\"gender\", "
        + "\"customer\".\"marital_status\", "
        + "\"customer\".\"education\", "
        + "\"customer\".\"yearly_income\" "
        + "order by \"customer\".\"country\" ASC NULLS LAST,"
        + " \"customer\".\"state_province\" ASC NULLS LAST,"
        + " \"customer\".\"city\" ASC NULLS LAST, "
        + "\"fname\" || ' ' || \"lname\" ASC NULLS LAST",
      852 );
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).expectSql(new SqlPattern[] { pattern } ).verify();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonUniformConstraintsAreNotUsedForOptimization(Context<?> context)  {
    String mdx =
      "with member [Measures].[unit sales Male] as '([Measures].[Unit Sales],[Gender].[Gender].[M])' "
        + "member [Measures].[unit sales Married] as '([Measures].[Unit Sales],[Marital Status].[Marital Status].[M])' "
        + "select "
        + "non empty {[Measures].[unit sales Male], [Measures].[unit sales Married]} on 0, "
        + "non empty [Customers].[name].members on 1 "
        + "from Sales";
    final String sqlOracle =
      "select \"customer\".\"country\" as \"c0\", \"customer\".\"state_province\" as \"c1\", \"customer\".\"city\" as"
        + " \"c2\", \"customer\".\"customer_id\" as \"c3\", \"fname\" || ' ' || \"lname\" as \"c4\", \"fname\" || ' ' "
        + "|| \"lname\" as \"c5\", \"customer\".\"gender\" as \"c6\", \"customer\".\"marital_status\" as \"c7\", "
        + "\"customer\".\"education\" as \"c8\", \"customer\".\"yearly_income\" as \"c9\" from \"customer\" "
        + "\"customer\", \"sales_fact_1997\" \"sales_fact_1997\" where \"sales_fact_1997\".\"customer_id\" = "
        + "\"customer\".\"customer_id\" and (\"customer\".\"gender\" in ('M', 'F')) group by \"customer\".\"country\", "
        + "\"customer\".\"state_province\", \"customer\".\"city\", \"customer\".\"customer_id\", \"fname\" || ' ' || "
        + "\"lname\", \"customer\".\"gender\", \"customer\".\"marital_status\", \"customer\".\"education\", "
        + "\"customer\".\"yearly_income\" order by \"customer\".\"country\" ASC NULLS LAST, \"customer\""
        + ".\"state_province\" ASC NULLS LAST, \"customer\".\"city\" ASC NULLS LAST, \"fname\" || ' ' || \"lname\" ASC "
        + "NULLS LAST";
    final SqlPattern pattern = new SqlPattern(
      DatabaseProduct.ORACLE,
      sqlOracle,
      sqlOracle.length() );
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).expectNoSql(new SqlPattern[] { pattern }).verify();
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
  void testMeasureConstraintsInACrossjoinHaveCorrectResults(Context<?> context)  {
    //http://jira.pentaho.com/browse/MONDRIAN-715
    String mdx =
      "with "
        + "  member [Measures].[aa] as '([Measures].[Store Cost],[Gender].[M])'"
        + "  member [Measures].[bb] as '([Measures].[Store Cost],[Gender].[F])'"
        + " select"
        + "  non empty "
        + "  crossjoin({[Store].[Store].[All Stores].[USA].[CA]},"
        + "      {[Measures].[aa], [Measures].[bb]}) on columns,"
        + "  non empty "
        + "  [Marital Status].[Marital Status].[Marital Status].members on rows"
        + " from sales";
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store].[Store].[USA].[CA], [Measures].[aa]}\n"
        + "{[Store].[Store].[USA].[CA], [Measures].[bb]}\n"
        + "Axis #2:\n"
        + "{[Marital Status].[Marital Status].[M]}\n"
        + "{[Marital Status].[Marital Status].[S]}\n"
        + "Row #0: 15,339.94\n"
        + "Row #0: 15,941.98\n"
        + "Row #1: 16,598.87\n"
        + "Row #1: 15,649.64\n" );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, TestContextAtAllWorksWithConstraintModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testContextAtAllWorksWithConstraint(Context<?> context)  {
      String mdx =
      " select "
        + " NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, "
        + " NON EMPTY {[Gender].[Gender].[Gender].Members} ON ROWS "
        + " from [onlyGender] ";
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Gender].[Gender].[F]}\n"
        + "{[Gender].[Gender].[M]}\n"
        + "Row #0: 131,558\n"
        + "Row #1: 135,215\n" );
  }

  /***
   * Before the fix this test would throw an IndexOutOfBounds exception
   * in CalculatedMemberExpander.removeDefaultMembers.  The method assumed that the
   * first member in the list would exist and be a measure.  But, when the
   * default measure is calculated, it would have already been removed from
   * the list by removeCalculatedMembers, and thus the assumption was wrong.
   */
  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, TestCalculatedDefaultMeasureOnVirtualCubeNoThrowExceptionModifier.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
  void testCalculatedDefaultMeasureOnVirtualCubeNoThrowException(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select "
        + " [Measures].[Unit Sales] on COLUMNS, "
        + " NON EMPTY {[Store].[Store State].Members} ON ROWS "
        + " from [virtual] ").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Store].[Store].[USA].[CA]}\n"
        + "{[Store].[Store].[USA].[OR]}\n"
        + "{[Store].[Store].[USA].[WA]}\n"
        + "Row #0: 74,748\n"
        + "Row #1: 67,659\n"
        + "Row #2: 124,366\n" );
  }

  /**
   * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-734"> MONDRIAN-734, "Exception thrown when creating
   * a "New Analysis View" with JPivot"</a>.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testExpandNonNativeWithEnableNativeCrossJoin(Context<?> context)  {

    String mdx =
      "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS,"
        + " NON EMPTY Crossjoin(Hierarchize(Crossjoin({[Store].[All Stores]}, Crossjoin({[Store Size in SQFT].[All "
        + "Store Size in SQFTs]}, Crossjoin({[Store Type].[All Store Types]}, Union(Crossjoin({[Time].[1997]}, "
        + "{[Product].[All Products]}), Crossjoin({[Time].[1997]}, [Product].[All Products].Children)))))), {"
        + "([Promotion Media].[All Media], [Promotions].[All Promotions], [Customers].[All Customers], [Education "
        + "Level].[All Education Levels], [Gender].[All Gender], [Marital Status].[All Marital Status], [Yearly "
        + "Income].[All Yearly Incomes])}) ON ROWS"
        + " from [Sales]";
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Store].[Store].[All Stores], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Store Type].[Store Type].[All Store Types], "
        + "[Time].[Time].[1997], [Product].[Product].[All Products], [Promotion Media].[Promotion Media].[All Media], [Promotions].[Promotions].[All Promotions], "
        + "[Customers].[Customers].[All Customers], [Education Level].[Education Level].[All Education Levels], [Gender].[Gender].[All Gender], [Marital "
        + "Status].[Marital Status].[All Marital Status], [Yearly Income].[Yearly Income].[All Yearly Incomes]}\n"
        + "{[Store].[Store].[All Stores], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Store Type].[Store Type].[All Store Types], "
        + "[Time].[Time].[1997], [Product].[Product].[Drink], [Promotion Media].[Promotion Media].[All Media], [Promotions].[Promotions].[All Promotions], "
        + "[Customers].[Customers].[All Customers], [Education Level].[Education Level].[All Education Levels], [Gender].[Gender].[All Gender], [Marital "
        + "Status].[Marital Status].[All Marital Status], [Yearly Income].[Yearly Income].[All Yearly Incomes]}\n"
        + "{[Store].[Store].[All Stores], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Store Type].[Store Type].[All Store Types], "
        + "[Time].[Time].[1997], [Product].[Product].[Food], [Promotion Media].[Promotion Media].[All Media], [Promotions].[Promotions].[All Promotions], [Customers].[Customers]"
        + ".[All Customers], [Education Level].[Education Level].[All Education Levels], [Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[All "
        + "Marital Status], [Yearly Income].[Yearly Income].[All Yearly Incomes]}\n"
        +
        "{[Store].[Store].[All Stores], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Store Type].[Store Type].[All Store Types], "
        + "[Time].[Time].[1997], [Product].[Product].[Non-Consumable], [Promotion Media].[Promotion Media].[All Media], [Promotions].[Promotions].[All Promotions], "
        + "[Customers].[Customers].[All Customers], [Education Level].[Education Level].[All Education Levels], [Gender].[Gender].[All Gender], [Marital "
        + "Status].[Marital Status].[All Marital Status], [Yearly Income].[Yearly Income].[All Yearly Incomes]}\n"
        + "Row #0: 266,773\n"
        + "Row #1: 24,597\n"
        + "Row #2: 191,940\n"
        + "Row #3: 50,236\n" );
  }

  /**
   * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-695"> MONDRIAN-695, "Unexpected data set may
   * returned when MDX slicer contains multiple dimensions"</a>.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testNonEmptyCJWithMultiPositionSlicer(Context<?> context)  {
    final String mdx =
      "select NON EMPTY NonEmptyCrossJoin([Measures].[Sales Count], [Store].[USA].Children) ON COLUMNS, "
        + "       NON EMPTY CrossJoin({[Customers].[All Customers]}, {([Promotions].[Bag Stuffers] : [Promotions]"
        + ".[Bye Bye Baby])}) ON ROWS "
        + "from [Sales Ragged] "
        + "where ({[Product].[Drink]} * {[Time].[1997].[Q1], [Time].[1997].[Q2]})";
    final String expected =
      "Axis #0:\n"
        + "{[Product].[Product].[Drink], [Time].[Time].[1997].[Q1]}\n"
        + "{[Product].[Product].[Drink], [Time].[Time].[1997].[Q2]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Sales Count], [Store].[Store].[USA].[CA]}\n"
        + "{[Measures].[Sales Count], [Store].[Store].[USA].[USA].[Washington]}\n"
        + "{[Measures].[Sales Count], [Store].[Store].[USA].[WA]}\n"
        + "Axis #2:\n"
        + "{[Customers].[Customers].[All Customers], [Promotions].[Promotions].[Bag Stuffers]}\n"
        + "{[Customers].[Customers].[All Customers], [Promotions].[Promotions].[Best Savings]}\n"
        + "{[Customers].[Customers].[All Customers], [Promotions].[Promotions].[Big Promo]}\n"
        + "{[Customers].[Customers].[All Customers], [Promotions].[Promotions].[Big Time Savings]}\n"
        + "{[Customers].[Customers].[All Customers], [Promotions].[Promotions].[Bye Bye Baby]}\n"
        + "Row #0: \n"
        + "Row #0: \n"
        + "Row #0: 2\n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #1: 13\n"
        + "Row #2: \n"
        + "Row #2: \n"
        + "Row #2: 9\n"
        + "Row #3: \n"
        + "Row #3: 12\n"
        + "Row #3: \n"
        + "Row #4: 1\n"
        + "Row #4: 21\n"
        + "Row #4: \n";
    // Get a fresh connection; Otherwise the mondrian property setting
    // is not refreshed for this parameter.
    checkNative(context,
      0,
      5,
      mdx,
      expected,
      true );
  }

  SmartMemberReader getSmartMemberReader( Connection con, String hierName ) {
    RolapCube cube = (RolapCube) con.getCatalog().lookupCube( "Sales" ).orElseThrow();
    RolapCatalogReader schemaReader =
      (RolapCatalogReader) cube.getCatalogReader();
    RolapHierarchy hierarchy =
      (RolapHierarchy) cube.lookupHierarchy(
        new IdImpl.NameSegmentImpl( hierName, Quoting.UNQUOTED ),
        false );
    assertNotNull( hierarchy );
    return (SmartMemberReader)
      hierarchy.createMemberReader( schemaReader.getRole() );
  }

  private SmartMemberReader getSharedSmartMemberReader(
    Connection con, String hierName ) {
    RolapCube cube = (RolapCube) con.getCatalog().lookupCube( "Sales").orElseThrow();
    RolapCatalogReader schemaReader =
      (RolapCatalogReader) cube.getCatalogReader();
    RolapCubeHierarchy hierarchy =
      (RolapCubeHierarchy) cube.lookupHierarchy(
        new IdImpl.NameSegmentImpl( hierName, Quoting.UNQUOTED ), false );
    assertNotNull( hierarchy );
    return (SmartMemberReader) hierarchy.getRolapHierarchy()
      .createMemberReader( schemaReader.getRole() );
  }


  RolapEvaluator getEvaluator( Result res, int[] pos ) {
    while ( res instanceof NonEmptyResult ) {
      res = ( (NonEmptyResult) res ).underlying;
    }
    return (RolapEvaluator) ( (RolapResult) res ).getEvaluator( pos );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testFilterChildlessSnowflakeMembers2(Context<?> context)  {
    if ( context.getConfigValue(ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS, ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS_DEFAULT_VALUE, Boolean.class) ) {
      // If FilterChildlessSnowflakeMembers is true, then
      // [Product].[Drink].[Baking Goods].[Coffee] does not even exist!
      return;
    }
    // children across a snowflake boundary
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select [Product].[Drink].[Baking Goods].[Dry Goods].[Coffee].Children on 0\n"
        + "from [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS, value = "false", type = Boolean.class)
  void testFilterChildlessSnowflakeMembers(Context<?> context)  {
    SqlPattern[] patterns = {
      new SqlPattern(
        DatabaseProduct.MYSQL,
        "select `product_class`.`product_family` as `c0` "
          + "from `product_class` as `product_class` "
          + "group by `product_class`.`product_family` "
          + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
          ? "order by ISNULL(`c0`) ASC,"
          + " `c0` ASC"
          : "order by ISNULL(`product_class`.`product_family`) ASC,"
          + " `product_class`.`product_family` ASC" ),
        null )
    };
    Connection connection = context.getConnectionWithDefaultRole();
    try {
      SqlAssert.forQuery(connection,
        "select [Product].[Product Family].Members on 0\n"
          + "from [Sales]").expectSql(patterns ).verify();

      // note that returns an extra member,
      // [Product].[Drink].[Baking Goods]
      assertThatQuery(connection,
        "select [Product].[Drink].Children on 0\n"
          + "from [Sales]").returnsGrid(
        "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n"
          + "{[Product].[Product].[Drink].[Alcoholic Beverages]}\n"
          + "{[Product].[Product].[Drink].[Baking Goods]}\n"
          + "{[Product].[Product].[Drink].[Beverages]}\n"
          + "{[Product].[Product].[Drink].[Dairy]}\n"
          + "Row #0: 6,838\n"
          + "Row #0: \n"
          + "Row #0: 13,573\n"
          + "Row #0: 4,186\n" );

      // [Product].[Drink].[Baking Goods] has one child, but no fact data
      assertThatQuery(connection,
        "select [Product].[Drink].[Baking Goods].Children on 0\n"
          + "from [Sales]").returnsGrid(
        "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n"
          + "{[Product].[Product].[Drink].[Baking Goods].[Dry Goods]}\n"
          + "Row #0: \n" );

      // NON EMPTY filters out that child
      assertThatQuery(connection,
        "select non empty [Product].[Drink].[Baking Goods].Children on 0\n"
          + "from [Sales]").returnsGrid(
        "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n" );

      // [Product].[Drink].[Baking Goods].[Dry Goods] has one child, but
      // no fact data
      assertThatQuery(connection,
        "select [Product].[Drink].[Baking Goods].[Dry Goods].Children on 0\n"
          + "from [Sales]").returnsGrid(
        "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n"
          + "{[Product].[Product].[Drink].[Baking Goods].[Dry Goods].[Coffee]}\n"
          + "Row #0: \n" );

      // NON EMPTY filters out that child
      assertThatQuery(connection,
        "select non empty [Product].[Drink].[Baking Goods].[Dry Goods].Children on 0\n"
          + "from [Sales]").returnsGrid(
        "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n" );

      // [Coffee] has no children
      assertThatQuery(connection,
        "select [Product].[Drink].[Baking Goods].[Dry Goods].[Coffee].Children on 0\n"
          + "from [Sales]").returnsGrid(
        "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n" );

      assertThatQuery(connection,
        "select [Measures].[Unit Sales] on 0,\n"
          + " [Product].[Product Family].Members on 1\n"
          + "from [Sales]").returnsGrid(
        "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n"
          + "{[Measures].[Unit Sales]}\n"
          + "Axis #2:\n"
          + "{[Product].[Product].[Drink]}\n"
          + "{[Product].[Product].[Food]}\n"
          + "{[Product].[Product].[Non-Consumable]}\n"
          + "Row #0: 24,597\n"
          + "Row #1: 191,940\n"
          + "Row #2: 50,236\n" );
    } finally {
      connection.close();
    }
  }

  /**
   * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-897"> MONDRIAN-897, "ClassCastException in
   * CrossJoinArgFactory.allArgsCheapToExpand when defining a NamedSet as another NamedSet"</a>.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testBugMondrian897DoubleNamedSetDefinitions(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH SET [CustomerSet] as {[Customers].[Canada].[BC].[Burnaby].[Alexandra Wellington], [Customers].[USA].[WA]"
        + ".[Tacoma].[Eric Coleman]} "
        + "SET [InterestingCustomers] as [CustomerSet] "
        + "SET [TimeRange] as {[Time].[1998].[Q1], [Time].[1998].[Q2]} "
        + "SELECT {[Measures].[Store Sales]} ON COLUMNS, "
        + "CrossJoin([InterestingCustomers], [TimeRange]) ON ROWS "
        + "FROM [Sales]").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Sales]}\n"
        + "Axis #2:\n"
        + "{[Customers].[Customers].[Canada].[BC].[Burnaby].[Alexandra Wellington], [Time].[Time].[1998].[Q1]}\n"
        + "{[Customers].[Customers].[Canada].[BC].[Burnaby].[Alexandra Wellington], [Time].[Time].[1998].[Q2]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Tacoma].[Eric Coleman], [Time].[Time].[1998].[Q1]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Tacoma].[Eric Coleman], [Time].[Time].[1998].[Q2]}\n"
        + "Row #0: \n"
        + "Row #1: \n"
        + "Row #2: \n"
        + "Row #3: \n" );
  }

  /**
   * Test case for
   * <a href="http://jira.pentaho.com/browse/MONDRIAN-1133">MONDRIAN-1133</a>
   *
   * <p>RolapNativeFilter would force the join to the fact table.
   * Some queries don't need to be joined to it and gain in performance.
   */
  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.NonEmptyTestModifier6.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testMondrian1133(Context<?> context, @Roles("Role1") Connection role1Connection)  {
    final String query =
      "With\n"
        + "Set [*BASE_MEMBERS_Product] as 'Filter([Store].[Store State].Members,[Store].CurrentMember.Caption Matches"
        + " (\"(?i).*CA.*\"))'\n"
        + "Select\n"
        + "[*BASE_MEMBERS_Product] on columns\n"
        + "From [Sales1] \n";

    final String nonEmptyQuery =
      "Select\n"
        + "NON EMPTY Filter([Store].[Store State].Members,[Store].CurrentMember.Caption Matches (\"(?i).*CA.*\")) on "
        + "columns\n"
        + "From [Sales1] \n";

    final String mysql =
      "select\n"
        + "    `store`.`store_country` as `c0`,\n"
        + "    `store`.`store_state` as `c1`\n"
        + "from\n"
        + "    `store` as `store`\n"
        + "group by\n"
        + "    `store`.`store_country`,\n"
        + "    `store`.`store_state`\n"
        + "having\n"
        + "    c1 IS NOT NULL AND UPPER(c1) REGEXP '.*CA.*'\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC"
        : "    ISNULL(`store`.`store_country`) ASC, `store`.`store_country` ASC,\n"
        + "    ISNULL(`store`.`store_state`) ASC, `store`.`store_state` ASC" );

    final String mysqlWithFactJoin =
      "select\n"
        + "    `store`.`store_country` as `c0`,\n"
        + "    `store`.`store_state` as `c1`\n"
        + "from\n"
        + "    `store` as `store` join `sales_fact_1997` as `sales_fact_1997` on `sales_fact_1997`.`store_id` = `store`.`store_id` join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
        + "where\n"
        + "    `time_by_day`.`the_year` = 1997\n"
        + "group by\n"
        + "    `store`.`store_country`,\n"
        + "    `store`.`store_state`\n"
        + "having\n"
        + "    c1 IS NOT NULL AND UPPER(c1) REGEXP '.*CA.*'\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC"
        : "    ISNULL(`store`.`store_country`) ASC, `store`.`store_country` ASC,\n"
        + "    ISNULL(`store`.`store_state`) ASC, `store`.`store_state` ASC" );

    final String oracle =
      "select\n"
        + "    \"store\".\"store_country\" as \"c0\",\n"
        + "    \"store\".\"store_state\" as \"c1\"\n"
        + "from\n"
        + "    \"store\" \"store\"\n"
        + "group by\n"
        + "    \"store\".\"store_country\",\n"
        + "    \"store\".\"store_state\"\n"
        + "having\n"
        + "    \"store\".\"store_state\" IS NOT NULL AND REGEXP_LIKE(\"store\".\"store_state\", '.*CA.*', 'i')\n"
        + "order by\n"
        + "    \"store\".\"store_country\" ASC NULLS LAST,\n"
        + "    \"store\".\"store_state\" ASC NULLS LAST";

    final String oracleWithFactJoin =
      "select\n"
        + "    \"store\".\"store_country\" as \"c0\",\n"
        + "    \"store\".\"store_state\" as \"c1\"\n"
        + "from\n"
        + "    \"store\" \"store\" join \"sales_fact_1997\" \"sales_fact_1997\" on \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\" join \"time_by_day\" \"time_by_day\" on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n"
        + "where\n"
        + "    \"time_by_day\".\"the_year\" = 1997\n"
        + "group by\n"
        + "    \"store\".\"store_country\",\n"
        + "    \"store\".\"store_state\"\n"
        + "having\n"
        + "    \"store\".\"store_state\" IS NOT NULL AND REGEXP_LIKE(\"store\".\"store_state\", '.*CA.*', 'i')\n"
        + "order by\n"
        + "    \"store\".\"store_country\" ASC NULLS LAST,\n"
        + "    \"store\".\"store_state\" ASC NULLS LAST";

    final SqlPattern[] patterns = {
      new SqlPattern(
        DatabaseProduct.MYSQL, mysql, mysql ),
      new SqlPattern(
        DatabaseProduct.ORACLE, oracle, oracle )
    };

    final SqlPattern[] patternsWithFactJoin = {
      new SqlPattern(
        DatabaseProduct.MYSQL,
        mysqlWithFactJoin, mysqlWithFactJoin ),
      new SqlPattern(
        DatabaseProduct.ORACLE,
        oracleWithFactJoin, oracleWithFactJoin )
    };

    // The filter condition does not require a join to the fact table.
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns ).verify();
    SqlAssert.forQuery(role1Connection, query).expectSql(patterns ).verify();

    // in a non-empty context where a role is in effect, the query
    // will pessimistically join the fact table and apply the
    // constraint, since the filter condition could be influenced by
    // role limitations.
    SqlAssert.forQuery(role1Connection, nonEmptyQuery).expectSql(patternsWithFactJoin ).verify();
  }

  /**
   * Test case for
   * <a href="http://jira.pentaho.com/browse/MONDRIAN-1133">MONDRIAN-1133</a>
   *
   * <p>RolapNativeFilter would force the join to the fact table.
   * Some queries don't need to be joined to it and gain in performance.
   *
   * <p>This one is for agg tables turned on.
   */
  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.NonEmptyTestModifier6.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testMondrian1133WithAggs(Context<?> context, @Roles("Role1") Connection role1Connection)  {
    final String query =
      "With\n"
        + "Set [*BASE_MEMBERS_Product] as 'Filter([Store].[Store State].Members,[Store].CurrentMember.Caption Matches"
        + " (\"(?i).*CA.*\"))'\n"
        + "Select\n"
        + "[*BASE_MEMBERS_Product] on columns\n"
        + "From [Sales1] \n";

    final String nonEmptyQuery =
      "Select\n"
        + "NON EMPTY Filter([Store].[Store State].Members,[Store].CurrentMember.Caption Matches (\"(?i).*CA.*\")) on "
        + "columns\n"
        + "From [Sales1] \n";


    final String mysql =
      "select\n"
        + "    `store`.`store_country` as `c0`,\n"
        + "    `store`.`store_state` as `c1`\n"
        + "from\n"
        + "    `store` as `store`\n"
        + "group by\n"
        + "    `store`.`store_country`,\n"
        + "    `store`.`store_state`\n"
        + "having\n"
        + "    c1 IS NOT NULL AND UPPER(c1) REGEXP '.*CA.*'\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC"
        : "    ISNULL(`store`.`store_country`) ASC, `store`.`store_country` ASC,\n"
        + "    ISNULL(`store`.`store_state`) ASC, `store`.`store_state` ASC" );

    final String mysqlWithFactJoin =
      "select\n"
        + "    `store`.`store_country` as `c0`,\n"
        + "    `store`.`store_state` as `c1`\n"
        + "from\n"
        + "    `store` as `store` join `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997` on `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id`\n"
        + "where\n"
        + "    `agg_c_14_sales_fact_1997`.`the_year` = 1997\n"
        + "group by\n"
        + "    `store`.`store_country`,\n"
        + "    `store`.`store_state`\n"
        + "having\n"
        + "    c1 IS NOT NULL AND UPPER(c1) REGEXP '.*CA.*'\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC"
        : "    ISNULL(`store`.`store_country`) ASC, `store`.`store_country` ASC,\n"
        + "    ISNULL(`store`.`store_state`) ASC, `store`.`store_state` ASC" );

    final String oracle =
      "select\n"
        + "    \"store\".\"store_country\" as \"c0\",\n"
        + "    \"store\".\"store_state\" as \"c1\"\n"
        + "from\n"
        + "    \"store\" \"store\"\n"
        + "group by\n"
        + "    \"store\".\"store_country\",\n"
        + "    \"store\".\"store_state\"\n"
        + "having\n"
        + "    \"store\".\"store_state\" IS NOT NULL AND REGEXP_LIKE(\"store\".\"store_state\", '.*CA.*', 'i')\n"
        + "order by\n"
        + "    \"store\".\"store_country\" ASC NULLS LAST,\n"
        + "    \"store\".\"store_state\" ASC NULLS LAST";

    final String oracleWithFactJoin =
      "select\n"
        + "    \"store\".\"store_country\" as \"c0\",\n"
        + "    \"store\".\"store_state\" as \"c1\"\n"
        + "from\n"
        + "    \"store\" \"store\" join \"agg_c_14_sales_fact_1997\" \"agg_c_14_sales_fact_1997\" on \"agg_c_14_sales_fact_1997\".\"store_id\" = \"store\".\"store_id\"\n"
        + "where\n"
        + "    \"agg_c_14_sales_fact_1997\".\"the_year\" = 1997\n"
        + "group by\n"
        + "    \"store\".\"store_country\",\n"
        + "    \"store\".\"store_state\"\n"
        + "having\n"
        + "    \"store\".\"store_state\" IS NOT NULL AND REGEXP_LIKE(\"store\".\"store_state\", '.*CA.*', 'i')\n"
        + "order by\n"
        + "    \"store\".\"store_country\" ASC NULLS LAST,\n"
        + "    \"store\".\"store_state\" ASC NULLS LAST";

    final SqlPattern[] patterns = {
      new SqlPattern(
        DatabaseProduct.MYSQL, mysql, mysql ),
      new SqlPattern(
        DatabaseProduct.ORACLE, oracle, oracle )
    };

    final SqlPattern[] patternsWithFactJoin = {
      new SqlPattern(
        DatabaseProduct.MYSQL,
        mysqlWithFactJoin, mysqlWithFactJoin ),
      new SqlPattern(
        DatabaseProduct.ORACLE,
        oracleWithFactJoin, oracleWithFactJoin )
    };

    // The filter condition does not require a join to the fact table.
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectSql(patterns ).verify();
    SqlAssert.forQuery(role1Connection, query).expectSql(patterns ).verify();

    // in a non-empty context where a role is in effect, the query
    // will pessimistically join the fact table and apply the
    // constraint, since the filter condition could be influenced by
    // role limitations.
    SqlAssert.forQuery(role1Connection, nonEmptyQuery).expectSql(patternsWithFactJoin ).verify();
  }


  /**
   * Native CrossJoin with a ranged slicer.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNonEmptyAggregateSlicerIsNative(Context<?> context)  {
	context.getCatalogCache().clear();
    final String mdx =
      "select NON EMPTY\n"
        + " Crossjoin([Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Portsmouth]\n"
        + " , [Customers].[USA].[WA].[Puyallup].Children) ON COLUMNS\n"
        + "from [Sales]\n"
        + "where ([Time].[1997].[Q1].[2] : [Time].[1997].[Q2].[5])";

    String mysqlNativeCrossJoinQuery =
      "select\n"
        + "    `time_by_day`.`the_year` as `c0`,\n"
        + "    `time_by_day`.`quarter` as `c1`,\n"
        + "    `time_by_day`.`month_of_year` as `c2`,\n"
        + "    `product_class`.`product_family` as `c3`,\n"
        + "    `product_class`.`product_department` as `c4`,\n"
        + "    `product_class`.`product_category` as `c5`,\n"
        + "    `product_class`.`product_subcategory` as `c6`,\n"
        + "    `product`.`brand_name` as `c7`,\n"
        + "    `customer`.`customer_id` as `c8`,\n"
        + "    sum(`sales_fact_1997`.`unit_sales`) as `m0`\n"
        + "from\n"
        + "    `sales_fact_1997` as `sales_fact_1997`\n"
        + "join\n"
        + "    `time_by_day` as `time_by_day`\n"
        + "on\n"
        + "    `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
        + "join\n"
        + "    `product` as `product`\n"
        + "on\n"
        + "    `sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "join\n"
        + "    `product_class` as `product_class`\n"
        + "on\n"
        + "    `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "join\n"
        + "    `customer` as `customer`\n"
        + "on\n"
        + "    `sales_fact_1997`.`customer_id` = `customer`.`customer_id`\n"
        + "where\n"
        + "    `time_by_day`.`the_year` = 1997\n"
        + "and\n"
        + "    `time_by_day`.`quarter` in ('Q1', 'Q2')\n"
        + "and\n"
        + "    `time_by_day`.`month_of_year` in (2, 3, 4, 5)\n"
        + "and\n"
        + "    `product_class`.`product_family` = 'Drink'\n"
        + "and\n"
        + "    `product_class`.`product_department` = 'Alcoholic Beverages'\n"
        + "and\n"
        + "    `product_class`.`product_category` = 'Beer and Wine'\n"
        + "and\n"
        + "    `product_class`.`product_subcategory` = 'Beer'\n"
        + "and\n"
        + "    `product`.`brand_name` = 'Portsmouth'\n"
        + "and\n"
        + "    `customer`.`customer_id` = 5219\n"
        + "group by\n"
        + "    `time_by_day`.`the_year`,\n"
        + "    `time_by_day`.`quarter`,\n"
        + "    `time_by_day`.`month_of_year`,\n"
        + "    `product_class`.`product_family`,\n"
        + "    `product_class`.`product_department`,\n"
        + "    `product_class`.`product_category`,\n"
        + "    `product_class`.`product_subcategory`,\n"
        + "    `product`.`brand_name`,\n"
        + "    `customer`.`customer_id`";
    String triggerSql =
      "select\n"
        + "    `time_by_day`.`the_year` as `c0`,\n"
        + "    `time_by_day`.`quarter` as `c1`,\n"
        + "    `time_by_day`.`month_of_year` as `c2`,\n"
        + "    `product_class`.`product_family` as `c3`,\n";

    if ( context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
      && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class) ) {
      mysqlNativeCrossJoinQuery =
        "select\n"
          + "    `agg_c_14_sales_fact_1997`.`the_year` as `c0`,\n"
          + "    `agg_c_14_sales_fact_1997`.`quarter` as `c1`,\n"
          + "    `agg_c_14_sales_fact_1997`.`month_of_year` as `c2`,\n"
          + "    `product_class`.`product_family` as `c3`,\n"
          + "    `product_class`.`product_department` as `c4`,\n"
          + "    `product_class`.`product_category` as `c5`,\n"
          + "    `product_class`.`product_subcategory` as `c6`,\n"
          + "    `product`.`brand_name` as `c7`,\n"
          + "    `customer`.`customer_id` as `c8`,\n"
          + "    sum(`agg_c_14_sales_fact_1997`.`unit_sales`) as `m0`\n"
          + "from\n"
          + "    `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997`,\n"
          + "    `product_class` as `product_class`,\n"
          + "    `product` as `product`,\n"
          + "    `customer` as `customer`\n"
          + "where\n"
          + "    `agg_c_14_sales_fact_1997`.`the_year` = 1997\n"
          + "and\n"
          + "    `agg_c_14_sales_fact_1997`.`quarter` in ('Q1', 'Q2')\n"
          + "and\n"
          + "    `agg_c_14_sales_fact_1997`.`month_of_year` in (2, 3, 4, 5)\n"
          + "and\n"
          + "    `agg_c_14_sales_fact_1997`.`product_id` = `product`.`product_id`\n"
          + "and\n"
          + "    `product`.`product_class_id` = `product_class`.`product_class_id`\n"
          + "and\n"
          + "    `product_class`.`product_family` = 'Drink'\n"
          + "and\n"
          + "    `product_class`.`product_department` = 'Alcoholic Beverages'\n"
          + "and\n"
          + "    `product_class`.`product_category` = 'Beer and Wine'\n"
          + "and\n"
          + "    `product_class`.`product_subcategory` = 'Beer'\n"
          + "and\n"
          + "    `product`.`brand_name` = 'Portsmouth'\n"
          + "and\n"
          + "    `agg_c_14_sales_fact_1997`.`customer_id` = `customer`.`customer_id`\n"
          + "and\n"
          + "    `customer`.`customer_id` = 5219\n"
          + "group by\n"
          + "    `agg_c_14_sales_fact_1997`.`the_year`,\n"
          + "    `agg_c_14_sales_fact_1997`.`quarter`,\n"
          + "    `agg_c_14_sales_fact_1997`.`month_of_year`,\n"
          + "    `product_class`.`product_family`,\n"
          + "    `product_class`.`product_department`,\n"
          + "    `product_class`.`product_category`,\n"
          + "    `product_class`.`product_subcategory`,\n"
          + "    `product`.`brand_name`,\n"
          + "    `customer`.`customer_id`";
      triggerSql =
        "select\n"
          + "    `agg_c_14_sales_fact_1997`.`the_year` as `c0`,\n"
          + "    `agg_c_14_sales_fact_1997`.`quarter` as `c1`,\n"
          + "    `agg_c_14_sales_fact_1997`.`month_of_year` as `c2`,\n"
          + "    `product_class`.`product_family` as `c3`,\n";
    }
    SqlPattern mysqlPattern =
      new SqlPattern(
        DatabaseProduct.MYSQL,
        mysqlNativeCrossJoinQuery,
        triggerSql );

    SqlAssert.forQuery(context.getConnectionWithDefaultRole(),  mdx).expectSql(new SqlPattern[] { mysqlPattern } ).verify();

    checkNative(context,
      20,
      1,
      "select NON EMPTY\n"
        + " Crossjoin([Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Portsmouth]\n"
        + " , [Customers].[USA].[WA].[Puyallup].Children) ON COLUMNS\n"
        + "from [Sales]\n"
        + "where ([Time].[1997].[Q1].[2] : [Time].[1997].[Q2].[5])",
      "Axis #0:\n"
        + "{[Time].[Time].[1997].[Q1].[2]}\n"
        + "{[Time].[Time].[1997].[Q1].[3]}\n"
        + "{[Time].[Time].[1997].[Q2].[4]}\n"
        + "{[Time].[Time].[1997].[Q2].[5]}\n"
        + "Axis #1:\n"
        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Portsmouth], [Customers].[Customers].[USA].[WA]"
        + ".[Puyallup].[Diane Biondo]}\n"
        + "Row #0: 2\n",
      true );
  }

  /**
   * Test case for
   * <a href="http://jira.pentaho.com/browse/MONDRIAN-1658">MONDRIAN-1658</a>
   *
   * <p>Error: Tuple length does not match arity
   *
   * <p>An empty set argument to crossjoin caused native evaluation to return
   * an incorrect type which in turn caused the types for each argument to union to be different
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
  void testMondrian1658(Context<?> context)  {
    String mdx =
      "Select\n"
        + "  [Measures].[Unit Sales] on columns,\n"
        + "  Non Empty \n"
        + "  Union(\n"
        + "    {([Gender].[M],[Time].[1997].[Q1])},\n"
        + "      Union(\n"
        + "        CrossJoin({[Gender].[F]},{}),\n"
        + "          {([Gender].[F],[Time].[1997].[Q2])}))\n"
        + "  on rows\n"
        + "From [Sales]\n";
    String expected =
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Gender].[Gender].[M], [Time].[Time].[1997].[Q1]}\n"
        + "{[Gender].[Gender].[F], [Time].[Time].[1997].[Q2]}\n"
        + "Row #0: 33,381\n"
        + "Row #1: 30,992\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), mdx).returnsGrid(expected );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMondrian2202WithConflictingMemberInSlicer(Context<?> context)  {
    // Validates correct behavior of the crossjoin optimizer and
    // native non empty when a calculated member should override the
    // slicer context.
    // In this case the YTD measure should have a value for each
    // of the 5 [Booker] products, even though not all of them have
    // data in the context of the slicer.

    String[] referencesToTimeMember = new String[] {
      "[Time].[1997].[Q3].[9]",
      "[Time].[Time].CurrentMember",
      "[Time].[1997].[Q4].[10].PrevMember"
    };

    for ( String timeMember : referencesToTimeMember ) {
      assertThatQuery(context.getConnectionWithDefaultRole(),
        "with member [Measures].[YTD Unit Sales] as "
          + "'Sum(Ytd(" + timeMember + "), [Measures].[Unit Sales])'\n"
          + "select\n"
          + "{[Measures].[YTD Unit Sales]}\n"
          + "ON COLUMNS,\n"
          + "NON EMPTY Crossjoin(\n"
          + "{[Customers].[All Customers]}\n"
          + ", [Product].[Drink].[Dairy].[Dairy].[Milk].[Booker].Children) ON ROWS\n"
          + "from [Sales]\n"
          + "where\n"
          + "{ [Time].[1997].[Q3].[9]}").returnsGrid(
        "Axis #0:\n"
          + "{[Time].[Time].[1997].[Q3].[9]}\n"
          + "Axis #1:\n"
          + "{[Measures].[YTD Unit Sales]}\n"
          + "Axis #2:\n"
          + "{[Customers].[Customers].[All Customers], [Product].[Product].[Drink].[Dairy].[Dairy].[Milk].[Booker].[Booker 1% Milk]}\n"
          + "{[Customers].[Customers].[All Customers], [Product].[Product].[Drink].[Dairy].[Dairy].[Milk].[Booker].[Booker 2% Milk]}\n"
          + "{[Customers].[Customers].[All Customers], [Product].[Product].[Drink].[Dairy].[Dairy].[Milk].[Booker].[Booker Buttermilk]}\n"
          + "{[Customers].[Customers].[All Customers], [Product].[Product].[Drink].[Dairy].[Dairy].[Milk].[Booker].[Booker Chocolate Milk]}\n"
          + "{[Customers].[Customers].[All Customers], [Product].[Product].[Drink].[Dairy].[Dairy].[Milk].[Booker].[Booker Whole Milk]}\n"
          + "Row #0: 147\n"
          + "Row #1: 136\n"
          + "Row #2: 84\n"
          + "Row #3: 94\n"
          + "Row #4: 101\n" );
    }
  }


  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMondrian2202WithCrossjoin(Context<?> context)  {
    // the [overrideContext] measure should have a value for the tuple
    // on rows, given it overrides the time member on the axis.
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH  member measures.[overrideContext] as '( measures.[unit sales], Time.[1997].Q1 )'\n"
        + "SELECT measures.[overrideContext] on 0, \n"
        + "NON EMPTY crossjoin( Time.[1998].Q1, [Marital Status].[M]) on 1\n"
        + "FROM sales\n").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[overrideContext]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1998].[Q1], [Marital Status].[Marital Status].[M]}\n"
        + "Row #0: 33,101\n" );
    // same thing w/ nonemptycrossjoin().
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH  member measures.[overrideContext] as '( measures.[unit sales], Time.[1997].Q1 )'\n"
        + "SELECT measures.[overrideContext] on 0, \n"
        + "NonEmptyCrossjoin( Time.[1998].Q1, [Marital Status].[M]) on 1\n"
        + "FROM sales\n").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[overrideContext]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1998].[Q1], [Marital Status].[Marital Status].[M]}\n"
        + "Row #0: 33,101\n" );
  }


  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMondrian2202WithLevelMembers(Context<?> context)  {
    // verifies SqlConstraintFactory.getLevelMembersConstraint() doesn't
    // generate a conflicting constraint.  Since CJAF attempts to collect
    // constraints from all axes, it's possible for it to construct
    // a constraint which includes the same hierarchy more than once.
    // In this case it would result in
    //    (year = 1997 AND year = 1998)
    // if potential conflicts aren't removed.
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH  member measures.[overrideContext] as '( measures.[unit sales], Time.Time.[1997].Q1 )'\n"
        + "SELECT measures.[overrideContext] on 0, \n"
        + "NON EMPTY [Marital Status].[Marital Status].[Marital Status].members on 1,\n"
        + "NON EMPTY Time.Time.[1998].Q1 on 2\n"
        + "FROM sales\n").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[overrideContext]}\n"
        + "Axis #2:\n"
        + "{[Marital Status].[Marital Status].[M]}\n"
        + "{[Marital Status].[Marital Status].[S]}\n"
        + "Axis #3:\n"
        + "{[Time].[Time].[1998].[Q1]}\n"
        + "Row #0: 33,101\n"
        + "Row #1: 33,190\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMondrian2202WithAggTopCountSet(Context<?> context)  {
    // in slicer
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member measures.top5Prod as "
        + "'aggregate(topcount("
        + "crossjoin( {time.time.[1997]}, product.product.[product name].members), 5, measures.[unit sales]), measures.[unit "
        + "sales])'"
        + " select measures.top5Prod on 0, non empty crossjoin({[Marital Status].[Marital Status].[M]}, gender.gender.gender.members) on 1"
        + " from sales where time.[1998].[Q1]").returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[1998].[Q1]}\n"
        + "Axis #1:\n"
        + "{[Measures].[top5Prod]}\n"
        + "Axis #2:\n"
        + "{[Marital Status].[Marital Status].[M], [Gender].[Gender].[F]}\n"
        + "{[Marital Status].[Marital Status].[M], [Gender].[Gender].[M]}\n"
        + "Row #0: 398\n"
        + "Row #1: 385\n" );
    // in CJ
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member measures.top5Prod as "
        + "'aggregate(topcount("
        + "crossjoin( {time.time.[1997]}, product.product.[product name].members), 5, measures.[unit sales]), measures.[unit "
        + "sales])'"
        + " select measures.top5Prod on 0, non empty crossjoin({[Time].[Time].[1998].[Q1]}, gender.gender.gender.members) on 1"
        + " from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[top5Prod]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1998].[Q1], [Gender].[Gender].[F]}\n"
        + "{[Time].[Time].[1998].[Q1], [Gender].[Gender].[M]}\n"
        + "Row #0: 699\n"
        + "Row #1: 699\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMondrian2202WithParameter(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH "
        + "member measures.[overrideContext] as "
        + "'( measures.[unit sales], "
        + "Parameter(\"timeParam\",[Time].[Time],[Time].[Time].[1997].[Q1],\"?\") )'\n"
        + "SELECT measures.[overrideContext] on 0, \n"
        + "NON EMPTY crossjoin( Time.Time.[1998].Q1, [Marital Status].[Marital Status].[M]) on 1\n"
        + "FROM sales\n").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[overrideContext]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1998].[Q1], [Marital Status].[Marital Status].[M]}\n"
        + "Row #0: 33,101\n" );
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH member Time.Time.param as 'Parameter(\"timeParam\",[Time].[Time],[Time].[Time].[1997].[Q1],\"?\")' "
        + "member measures.[overrideContext] as '( measures.[unit sales], Time.param )'\n"
        + "SELECT measures.[overrideContext] on 0, \n"
        + "NON EMPTY crossjoin( Time.Time.[1998].Q1, [Marital Status].[Marital Status].[M]) on 1\n"
        + "FROM sales\n").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[overrideContext]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1998].[Q1], [Marital Status].[Marital Status].[M]}\n"
        + "Row #0: 33,101\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMondrian2202WithFilter(Context<?> context)  {
    // Validates correct results when a filtered set contains a member
    // overriden by the filter condition.
    // (This worked before the fix for MONDRIAN-2202, since
    // RolapNativeSql cannot nativize tuple calculations.)
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH  member measures.[overrideContext] as "
        + " '( measures.[unit sales], Time.Time.[1997].Q1 )'\n"
        + "SELECT measures.[overrideContext] on 0, \n"
        + "filter ( Crossjoin(Time.Time.[1998].Q1, [Marital Status].[Marital Status].[marital status].members), "
        + "measures.[overrideContext] >= 0) on 1\n"
        + "FROM sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[overrideContext]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1998].[Q1], [Marital Status].[Marital Status].[M]}\n"
        + "{[Time].[Time].[1998].[Q1], [Marital Status].[Marital Status].[S]}\n"
        + "Row #0: 33,101\n"
        + "Row #1: 33,190\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMondrian2202WithTopCount(Context<?> context)  {
    // Validates correct results when a topcount set contains a member
    // overriden by the filter condition.
    // (This worked before the fix for MONDRIAN-2202, since
    // RolapNativeSql cannot nativize tuple calculations.)
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH  member measures.[overrideContext] as "
        + " '( measures.[unit sales], Time.Time.[1997].Q1 )'\n"
        + "SELECT measures.[overrideContext] on 0, \n"
        + "TopCount ( Crossjoin(Time.Time.[1998].Q1.children, "
        + "[Marital Status].[Marital Status].[marital status].members), "
        + "2, measures.[overrideContext] ) on 1\n"
        + "FROM sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[overrideContext]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1998].[Q1].[1], [Marital Status].[Marital Status].[S]}\n"
        + "{[Time].[Time].[1998].[Q1].[2], [Marital Status].[Marital Status].[S]}\n"
        + "Row #0: 33,190\n"
        + "Row #1: 33,190\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMondrian2202WithMeasureContainingCJ(Context<?> context)  {
    // NECJ nested within a measure expression
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with  "
        + " member gender.gender.agg as 'aggregate(NonEmptyCrossJoin({Gender.Gender.F}, {[Marital Status].[Marital Status].[Marital Status]"
        + ".members}))' "
        + "member measures.lastYear as '(parallelperiod([Time].[Time].[Year], 1, [Time].[Time].CurrentMember), Measures.[unit "
        + "sales])' "
        + "member measures.ratioCurrentOverAgg as 'Measures.[Unit Sales] / (gender.gender.agg, Measures.[Unit Sales])' "
        + " select gender.gender.agg on 0, {measures.lastYear, measures.ratioCurrentOverAgg} on 1 from sales where [Time]"
        + ".[1998].[Q2]").returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[1998].[Q2]}\n"
        + "Axis #1:\n"
        + "{[Gender].[Gender].[agg]}\n"
        + "Axis #2:\n"
        + "{[Measures].[lastYear]}\n"
        + "{[Measures].[ratioCurrentOverAgg]}\n"
        + "Row #0: 30,992\n"
        + "Row #1: \n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testMon2202RunningSum(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "WITH\n"
        + "SET [*NATIVE_CJ_SET] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Time_],NONEMPTYCROSSJOIN"
        + "([*BASE_MEMBERS__Education Level_],[*BASE_MEMBERS__Customers_])))'\n"
        + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_1],[Measures].[*SUMMARY_MEASURE_0]}'\n"
        + "SET [*BASE_MEMBERS__Time_] AS 'FILTER([Time].[Time].[Month].MEMBERS,ANCESTOR([Time].[Time].CURRENTMEMBER, [Time].[Time].[Year])"
        + " IN {[Time].[Time].[1997]})'\n"
        + "SET [*BASE_MEMBERS__Customers_] AS '{[Customers].[Customers].[USA].[WA].[Ballard]}'\n"
        + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Education Level].[Education Level].CURRENTMEMBER,[Customers].[Customers]"
        + ".CURRENTMEMBER)})'\n"
        + "SET [*BASE_MEMBERS__Education Level_] AS '{[Education Level].[Education Level].[Partial College]}'\n"
        + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Time].[Time].CURRENTMEMBER)})'\n"
        + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Time].[Time].CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Time].[Time]"
        + ".CURRENTMEMBER,[Time].[Time].[Quarter]).ORDERKEY,BASC)'\n"
        + "MEMBER [Measures].[*FORMATTED_MEASURE_1] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
        + "SOLVE_ORDER=500\n"
        + "MEMBER [Measures].[*SUMMARY_MEASURE_0] AS 'SUM(HEAD([*SORTED_ROW_AXIS],RANK(([Time].[Time].CURRENTMEMBER),"
        + "[*SORTED_ROW_AXIS])),[Measures].[Unit Sales])', SOLVE_ORDER=200\n"
        + "SELECT\n"
        + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
        + ",NON EMPTY [*SORTED_ROW_AXIS] ON ROWS\n"
        + "FROM [Sales]\n"
        + "WHERE ([*CJ_SLICER_AXIS])").returnsGrid(
      "Axis #0:\n"
        + "{[Education Level].[Education Level].[Partial College], [Customers].[Customers].[USA].[WA].[Ballard]}\n"
        + "Axis #1:\n"
        + "{[Measures].[*FORMATTED_MEASURE_1]}\n"
        + "{[Measures].[*SUMMARY_MEASURE_0]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1997].[Q1].[2]}\n"
        + "{[Time].[Time].[1997].[Q1].[3]}\n"
        + "{[Time].[Time].[1997].[Q2].[4]}\n"
        + "{[Time].[Time].[1997].[Q2].[5]}\n"
        + "{[Time].[Time].[1997].[Q2].[6]}\n"
        + "{[Time].[Time].[1997].[Q3].[7]}\n"
        + "{[Time].[Time].[1997].[Q3].[8]}\n"
        + "{[Time].[Time].[1997].[Q3].[9]}\n"
        + "{[Time].[Time].[1997].[Q4].[10]}\n"
        + "{[Time].[Time].[1997].[Q4].[11]}\n"
        + "{[Time].[Time].[1997].[Q4].[12]}\n"
        + "Row #0: 24\n"
        + "Row #0: 24\n"
        + "Row #1: 11\n"
        + "Row #1: 35\n"
        + "Row #2: \n"
        + "Row #2: 35\n"
        + "Row #3: \n"
        + "Row #3: 35\n"
        + "Row #4: \n"
        + "Row #4: 35\n"
        + "Row #5: 112\n"
        + "Row #5: 147\n"
        + "Row #6: \n"
        + "Row #6: 147\n"
        + "Row #7: 14\n"
        + "Row #7: 161\n"
        + "Row #8: 42\n"
        + "Row #8: 203\n"
        + "Row #9: 56\n"
        + "Row #9: 259\n"
        + "Row #10: \n"
        + "Row #10: 259\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR", type = String.class)
  void testMon2202AnalyzerTopCount(Context<?> context)  {
    // will throw an exception if native cj is not used.
    executeQuery(
      "WITH\n"
        + "SET [*NATIVE_CJ_SET] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Marital Status_],NONEMPTYCROSSJOIN"
        + "([*BASE_MEMBERS__Product_],[*BASE_MEMBERS__Time_]))'\n"
        + "SET [*METRIC_CJ_SET] AS 'FILTER(FILTER([*NATIVE_CJ_SET],[Measures].[*TOP_Unit Sales_SEL~SUM] <= 3), NOT "
        + "ISEMPTY ([Measures].[Unit Sales]))'\n"
        + "SET [*BASE_MEMBERS__Marital Status_] AS '[Marital Status].[Marital Status].[Marital Status].MEMBERS'\n"
        + "SET [*SORTED_COL_AXIS] AS 'ORDER([*CJ_COL_AXIS],[Marital Status].[Marital Status].CURRENTMEMBER.ORDERKEY,BASC)'\n"
        + "SET [*TOP_SET] AS 'ORDER(GENERATE([*NATIVE_CJ_SET],{[Product].[Product].CURRENTMEMBER}),([Measures].[Unit Sales],"
        + "[Marital Status].[Marital Status].[*CTX_MEMBER_SEL~SUM],[Time].[Time].[*CTX_MEMBER_SEL~AGG]),BDESC)'\n"
        + "SET [*NATIVE_MEMBERS__Time_] AS 'GENERATE([*NATIVE_CJ_SET], {[Time].[Time].CURRENTMEMBER})'\n"
        + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
        + "SET [*NATIVE_MEMBERS__Marital Status_] AS 'GENERATE([*NATIVE_CJ_SET], {[Marital Status].[Marital Status].CURRENTMEMBER})'\n"
        + "SET [*BASE_MEMBERS__Time_] AS '{[Time].[Time].[1997].[Q2].[4],[Time].[Time].[1997].[Q1].[2],[Time].[Time].[1997].[Q1].[1],"
        + "[Time].[Time].[1997].[Q1].[3]}'\n"
        + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*METRIC_CJ_SET], {([Time].[Time].CURRENTMEMBER)})'\n"
        + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*METRIC_CJ_SET], {([Product].[Product].CURRENTMEMBER)})'\n"
        + "SET [*BASE_MEMBERS__Product_] AS '[Product].[Product].[Brand Name].MEMBERS'\n"
        + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Measures].[*SORTED_MEASURE],BDESC)'\n"
        + "SET [*CJ_COL_AXIS] AS 'GENERATE([*METRIC_CJ_SET], {([Marital Status].[Marital Status].CURRENTMEMBER)})'\n"
        + "MEMBER [Marital Status].[Marital Status].[*CTX_MEMBER_SEL~SUM] AS 'SUM([*NATIVE_MEMBERS__Marital Status_])', SOLVE_ORDER=99\n"
        + "MEMBER [Measures].[Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
        + "SOLVE_ORDER=500\n"
        + "MEMBER [Measures].[*SORTED_MEASURE] AS '([Measures].[*FORMATTED_MEASURE_0],[Marital Status].[Marital Status]"
        + ".[*CTX_MEMBER_SEL~SUM],[Time].[Time].[*CTX_MEMBER_SEL~AGG])', SOLVE_ORDER=400\n"
        + "MEMBER [Measures].[*TOP_Unit Sales_SEL~SUM] AS 'RANK([Product].[Product].CURRENTMEMBER,[*TOP_SET])', SOLVE_ORDER=400\n"
        + "MEMBER [Time].[Time].[*CTX_MEMBER_SEL~AGG] AS 'AGGREGATE([*NATIVE_MEMBERS__Time_])', SOLVE_ORDER=-302\n"
        + "SELECT\n"
        + "CROSSJOIN([*SORTED_COL_AXIS],[*BASE_MEMBERS__Measures_]) ON COLUMNS\n"
        + ",[*SORTED_ROW_AXIS] ON ROWS\n"
        + "FROM [Sales]\n"
        + "WHERE ([*CJ_SLICER_AXIS])", context.getConnectionWithDefaultRole());
  }


  /**
   * DuckDB's column_lifetime pass mis-binds a column that appears both in a WHERE of OR-ed
   * AND-tuples and in a GROUP BY over those same columns:
   *   INTERNAL Error: Failed to bind column reference "product_category" [3.3]
   * (1.5.4 hides it behind "unsuccessful or closed pending query result"). Mondrian emits that
   * shape for the compound member predicates these tests produce -- 238 of the 316469 statements
   * in a full duckdb run, 0.075%.
   *
   * <p>Disabling the pass for the whole run makes duckdb three times slower (364s to 1198s) and
   * even changed one result, so it is disabled only around the handful of tests that need it.
   * disabled_optimizers is a GLOBAL setting: a SET on any connection reaches the keeper and every
   * duplicate, present and future, which is exactly the reach required here and the reason it
   * must be put back afterwards.
   */
  private static void withDuckDbColumnLifetimeDisabled(Context<?> context, Runnable body) {
    if (!"DUCKDB".equalsIgnoreCase(context.getDialect().name())) {
      body.run();
      return;
    }
    setDuckDbDisabledOptimizers(context, "column_lifetime");
    try {
      body.run();
    } finally {
      setDuckDbDisabledOptimizers(context, "");
    }
  }

  private static void setDuckDbDisabledOptimizers(Context<?> context, String value) {
    try (java.sql.Connection connection = context.getDataSource().getConnection();
        java.sql.Statement statement = connection.createStatement()) {
      statement.execute("SET GLOBAL disabled_optimizers='" + value + "'");
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException("could not set duckdb's disabled_optimizers", e);
    }
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR", type = String.class)
  void testMon2202AnalyzerFilter(Context<?> context)  {
    withDuckDbColumnLifetimeDisabled(context, () -> {
        assertThatQuery(context.getConnectionWithDefaultRole(),
        "WITH\n"
          + "SET [*NATIVE_CJ_SET] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Education Level_],NONEMPTYCROSSJOIN"
          + "([*BASE_MEMBERS__Product_],[*BASE_MEMBERS__Time_]))'\n"
          + "SET [*METRIC_CJ_SET] AS 'FILTER([*NATIVE_CJ_SET],[Measures].[*Unit Sales_SEL~SUM] > 5.0)'\n"
          + "SET [*NATIVE_MEMBERS__Time_] AS 'GENERATE([*NATIVE_CJ_SET], {[Time].[Time].CURRENTMEMBER})'\n"
          + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
          + "SET [*BASE_MEMBERS__Time_] AS '{[Time].[Time].[1997].[Q2].[4],[Time].[Time].[1997].[Q1].[2],[Time].[Time].[1997].[Q1].[1],"
          + "[Time].[Time].[1997].[Q1].[3]}'\n"
          + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*METRIC_CJ_SET], {([Time].[Time].CURRENTMEMBER)})'\n"
          + "SET [*BASE_MEMBERS__Education Level_] AS '[Education Level].[Education Level].[Education Level].MEMBERS'\n"
          + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*METRIC_CJ_SET], {([Education Level].[Education Level].CURRENTMEMBER,[Product].[Product]"
          + ".CURRENTMEMBER)})'\n"
          + "SET [*BASE_MEMBERS__Product_] AS '{[Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American],[Product].[Product]"
          + ".[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB Best],[Product].[Product].[Food].[Frozen Foods].[Breakfast "
          + "Foods].[Pancake Mix].[Big Time]}'\n"
          + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Education Level].[Education Level].CURRENTMEMBER.ORDERKEY,BASC,[Product].[Product]"
          + ".CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].[Product].CURRENTMEMBER,[Product].[Product].[Product Subcategory]).ORDERKEY,"
          + "BASC)'\n"
          + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
          + "SOLVE_ORDER=500\n"
          + "MEMBER [Measures].[*Unit Sales_SEL~SUM] AS '([Measures].[Unit Sales],[Education Level].[Education Level].CURRENTMEMBER,"
          + "[Product].[Product].CURRENTMEMBER,[Time].[*CTX_MEMBER_SEL~AGG])', SOLVE_ORDER=400\n"
          + "MEMBER [Time].[Time].[*CTX_MEMBER_SEL~AGG] AS 'AGGREGATE([*NATIVE_MEMBERS__Time_])', SOLVE_ORDER=-301\n"
          + "SELECT\n"
          + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
          + ",NON EMPTY\n"
          + "[*SORTED_ROW_AXIS] ON ROWS\n"
          + "FROM [Sales]\n"
          + "WHERE ([*CJ_SLICER_AXIS])").returnsGrid(
        "Axis #0:\n"
          + "{[Time].[Time].[1997].[Q1].[1]}\n"
          + "{[Time].[Time].[1997].[Q1].[3]}\n"
          + "{[Time].[Time].[1997].[Q2].[4]}\n"
          + "{[Time].[Time].[1997].[Q1].[2]}\n"
          + "Axis #1:\n"
          + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
          + "Axis #2:\n"
          + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix]"
          + ".[Big Time]}\n"
          + "{[Education Level].[Education Level].[Graduate Degree], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[High School Degree], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[High School Degree], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[High School Degree], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix]"
          + ".[Big Time]}\n"
          + "{[Education Level].[Education Level].[Partial College], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix]"
          + ".[Big Time]}\n"
          + "Row #0: 38\n"
          + "Row #1: 13\n"
          + "Row #2: 28\n"
          + "Row #3: 13\n"
          + "Row #4: 62\n"
          + "Row #5: 13\n"
          + "Row #6: 22\n"
          + "Row #7: 30\n"
          + "Row #8: 68\n"
          + "Row #9: 12\n"
          + "Row #10: 27\n" );
    });
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR", type = String.class)
  void testMon2202AnalyzerPercOfMeasure(Context<?> context)  {
    withDuckDbColumnLifetimeDisabled(context, () -> {
        assertThatQuery(context.getConnectionWithDefaultRole(),
        "WITH\n"
          + "SET [*NATIVE_CJ_SET] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Education Level_],NONEMPTYCROSSJOIN"
          + "([*BASE_MEMBERS__Product_],[*BASE_MEMBERS__Time_]))'\n"
          + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*SUMMARY_MEASURE_0]}'\n"
          + "SET [*BASE_MEMBERS__Time_] AS '{[Time].[Time].[1997].[Q2].[4],[Time].[Time].[1997].[Q1].[2],[Time].[Time].[1997].[Q1].[1],"
          + "[Time].[Time].[1997].[Q1].[3]}'\n"
          + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Time].[Time].CURRENTMEMBER)})'\n"
          + "SET [*BASE_MEMBERS__Education Level_] AS '[Education Level].[Education Level].[Education Level].MEMBERS'\n"
          + "SET [*NATIVE_MEMBERS__Education Level_] AS 'GENERATE([*NATIVE_CJ_SET], {[Education Level].[Education Level].CURRENTMEMBER})'\n"
          + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Education Level].[Education Level].CURRENTMEMBER,[Product].[Product]"
          + ".CURRENTMEMBER)})'\n"
          + "SET [*BASE_MEMBERS__Product_] AS '{[Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American],[Product].[Product]"
          + ".[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB Best],[Product].[Product].[Food].[Frozen Foods].[Breakfast "
          + "Foods].[Pancake Mix].[Big Time]}'\n"
          + "SET [*NATIVE_MEMBERS__Product_] AS 'GENERATE([*NATIVE_CJ_SET], {[Product].[Product].CURRENTMEMBER})'\n"
          + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Education Level].[Education Level].CURRENTMEMBER.ORDERKEY,BASC,[Product]"
          + ".CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].[Product].CURRENTMEMBER,[Product].[Product].[Product Subcategory]).ORDERKEY,"
          + "BASC)'\n"
          + "MEMBER [Education Level].[Education Level].[*TOTAL_MEMBER_SEL~SUM] AS 'SUM([*NATIVE_MEMBERS__Education Level_])', "
          + "SOLVE_ORDER=100\n"
          + "MEMBER [Measures].[*SUMMARY_MEASURE_0] AS '[Measures].[Unit Sales]/([Measures].[Unit Sales],[Education "
          + "Level].[Education Level].[*TOTAL_MEMBER_SEL~SUM],[Product].[*TOTAL_MEMBER_SEL~SUM])', FORMAT_STRING = '###0.00%', "
          + "SOLVE_ORDER=200\n"
          + "MEMBER [Product].[*TOTAL_MEMBER_SEL~SUM] AS 'SUM([*NATIVE_MEMBERS__Product_])', SOLVE_ORDER=99\n"
          + "SELECT\n"
          + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
          + ",NON EMPTY\n"
          + "[*SORTED_ROW_AXIS] ON ROWS\n"
          + "FROM [Sales]\n"
          + "WHERE ([*CJ_SLICER_AXIS])").returnsGrid(
        "Axis #0:\n"
          + "{[Time].[Time].[1997].[Q1].[1]}\n"
          + "{[Time].[Time].[1997].[Q1].[3]}\n"
          + "{[Time].[Time].[1997].[Q2].[4]}\n"
          + "{[Time].[Time].[1997].[Q1].[2]}\n"
          + "Axis #1:\n"
          + "{[Measures].[*SUMMARY_MEASURE_0]}\n"
          + "Axis #2:\n"
          + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix]"
          + ".[Big Time]}\n"
          + "{[Education Level].[Education Level].[Graduate Degree], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Graduate Degree], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Big"
          + " Time]}\n"
          + "{[Education Level].[Education Level].[High School Degree], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[High School Degree], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[High School Degree], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix]"
          + ".[Big Time]}\n"
          + "{[Education Level].[Education Level].[Partial College], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Partial College], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[Partial College], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Big"
          + " Time]}\n"
          + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix]"
          + ".[Big Time]}\n"
          + "Row #0: 11.34%\n"
          + "Row #1: 3.88%\n"
          + "Row #2: 8.36%\n"
          + "Row #3: 3.88%\n"
          + "Row #4: 0.90%\n"
          + "Row #5: 18.51%\n"
          + "Row #6: 3.88%\n"
          + "Row #7: 6.57%\n"
          + "Row #8: 8.96%\n"
          + "Row #9: 0.90%\n"
          + "Row #10: 0.90%\n"
          + "Row #11: 20.30%\n"
          + "Row #12: 3.58%\n"
          + "Row #13: 8.06%\n" );
    });
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR", type = String.class)
  void testMon2202AnalyzerRunningSum(Context<?> context)  {
    withDuckDbColumnLifetimeDisabled(context, () -> {
        assertThatQuery(context.getConnectionWithDefaultRole(),
        "WITH\n"
          + "SET [*NATIVE_CJ_SET] AS 'FILTER(NONEMPTYCROSSJOIN([*BASE_MEMBERS__Education Level_],NONEMPTYCROSSJOIN"
          + "([*BASE_MEMBERS__Product_],[*BASE_MEMBERS__Time_])), NOT ISEMPTY ([Measures].[Unit Sales]))'\n"
          + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_1],[Measures].[*SUMMARY_MEASURE_0]}'\n"
          + "SET [*BASE_MEMBERS__Time_] AS '{[Time].[Time].[1997].[Q2].[4],[Time].[Time].[1997].[Q1].[2],[Time].[Time].[1997].[Q1].[1],"
          + "[Time].[Time].[1997].[Q1].[3]}'\n"
          + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Time].[Time].CURRENTMEMBER)})'\n"
          + "SET [*BASE_MEMBERS__Education Level_] AS '[Education Level].[Education Level].[Education Level].MEMBERS'\n"
          + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Education Level].[Education Level].CURRENTMEMBER,[Product].[Product]"
          + ".CURRENTMEMBER)})'\n"
          + "SET [*BASE_MEMBERS__Product_] AS '{[Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American],[Product].[Product]"
          + ".[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB Best],[Product].[Product].[Food].[Frozen Foods].[Breakfast "
          + "Foods].[Pancake Mix].[Big Time]}'\n"
          + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Education Level].[Education Level].CURRENTMEMBER.ORDERKEY,BASC,[Product].[Product]"
          + ".CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].[Product].CURRENTMEMBER,[Product].[Product].[Product Subcategory]).ORDERKEY,"
          + "BASC)'\n"
          + "MEMBER [Measures].[*FORMATTED_MEASURE_1] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
          + "SOLVE_ORDER=500\n"
          + "MEMBER [Measures].[*SUMMARY_MEASURE_0] AS 'SUM(HEAD([*SORTED_ROW_AXIS],RANK(([Education Level].[Education Level]"
          + ".CURRENTMEMBER,[Product].CURRENTMEMBER),[*SORTED_ROW_AXIS])),[Measures].[Unit Sales])', SOLVE_ORDER=200\n"
          + "SELECT\n"
          + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
          + ",[*SORTED_ROW_AXIS] ON ROWS\n"
          + "FROM [Sales]\n"
          + "WHERE ([*CJ_SLICER_AXIS])").returnsGrid(
        "Axis #0:\n"
          + "{[Time].[Time].[1997].[Q1].[1]}\n"
          + "{[Time].[Time].[1997].[Q1].[3]}\n"
          + "{[Time].[Time].[1997].[Q2].[4]}\n"
          + "{[Time].[Time].[1997].[Q1].[2]}\n"
          + "Axis #1:\n"
          + "{[Measures].[*FORMATTED_MEASURE_1]}\n"
          + "{[Measures].[*SUMMARY_MEASURE_0]}\n"
          + "Axis #2:\n"
          + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[Bachelors Degree], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix]"
          + ".[Big Time]}\n"
          + "{[Education Level].[Education Level].[Graduate Degree], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Graduate Degree], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Big"
          + " Time]}\n"
          + "{[Education Level].[Education Level].[High School Degree], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[High School Degree], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[High School Degree], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix]"
          + ".[Big Time]}\n"
          + "{[Education Level].[Education Level].[Partial College], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Partial College], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[Partial College], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Big"
          + " Time]}\n"
          + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American]}\n"
          + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best]}\n"
          + "{[Education Level].[Education Level].[Partial High School], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix]"
          + ".[Big Time]}\n"
          + "Row #0: 38\n"
          + "Row #0: 38\n"
          + "Row #1: 13\n"
          + "Row #1: 51\n"
          + "Row #2: 28\n"
          + "Row #2: 79\n"
          + "Row #3: 13\n"
          + "Row #3: 92\n"
          + "Row #4: 3\n"
          + "Row #4: 95\n"
          + "Row #5: 62\n"
          + "Row #5: 157\n"
          + "Row #6: 13\n"
          + "Row #6: 170\n"
          + "Row #7: 22\n"
          + "Row #7: 192\n"
          + "Row #8: 30\n"
          + "Row #8: 222\n"
          + "Row #9: 3\n"
          + "Row #9: 225\n"
          + "Row #10: 3\n"
          + "Row #10: 228\n"
          + "Row #11: 68\n"
          + "Row #11: 296\n"
          + "Row #12: 12\n"
          + "Row #12: 308\n"
          + "Row #13: 27\n"
          + "Row #13: 335\n" );
    });
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR", type = String.class)
  void testMon2202SeveralFilteredHierarchiesPlusMeasureFilter(Context<?> context)  {
    withDuckDbColumnLifetimeDisabled(context, () -> {
        assertThatQuery(context.getConnectionWithDefaultRole(),
        "WITH\n"
          + "SET [*NATIVE_CJ_SET] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Promotion Media_],NONEMPTYCROSSJOIN"
          + "([*BASE_MEMBERS__Store_],NONEMPTYCROSSJOIN([*BASE_MEMBERS__Education Level_],NONEMPTYCROSSJOIN"
          + "([*BASE_MEMBERS__Product_],NONEMPTYCROSSJOIN([*BASE_MEMBERS__Gender_],[*BASE_MEMBERS__Time_])))))'\n"
          + "SET [*METRIC_CJ_SET] AS 'FILTER([*NATIVE_CJ_SET],[Measures].[*Store Cost_SEL~SUM] > 0.0)'\n"
          + "SET [*BASE_MEMBERS__Store_] AS '{[Store].[Store].[USA].[OR]}'\n"
          + "SET [*NATIVE_MEMBERS__Time_] AS 'GENERATE([*NATIVE_CJ_SET], {[Time].[Time].CURRENTMEMBER})'\n"
          + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
          + "SET [*BASE_MEMBERS__Time_] AS '{[Time].[Time].[1997].[Q2].[4],[Time].[Time].[1997].[Q1].[2],[Time].[Time].[1997].[Q1].[1],"
          + "[Time].[Time].[1997].[Q1].[3]}'\n"
          + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*METRIC_CJ_SET], {([Time].[Time].CURRENTMEMBER)})'\n"
          + "SET [*BASE_MEMBERS__Education Level_] AS '[Education Level].[Education Level].[Education Level].MEMBERS'\n"
          + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*METRIC_CJ_SET], {([Promotion Media].[Promotion Media].CURRENTMEMBER,[Store].[Store].CURRENTMEMBER,"
          + "[Education Level].[Education Level].CURRENTMEMBER,[Product].[Product].CURRENTMEMBER,[Gender].[Gender].CURRENTMEMBER)})'\n"
          + "SET [*BASE_MEMBERS__Promotion Media_] AS '{[Promotion Media].[Promotion Media].[Daily Paper, Radio],[Promotion Media].[Promotion Media].[Daily"
          + " Paper, Radio, TV],[Promotion Media].[Promotion Media].[In-Store Coupon],[Promotion Media].[Promotion Media].[No Media]}'\n"
          + "SET [*BASE_MEMBERS__Product_] AS '{[Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American],[Product].[Product]"
          + ".[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB Best],[Product].[Product].[Food].[Frozen Foods].[Breakfast "
          + "Foods].[Pancake Mix].[Big Time]}'\n"
          + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Promotion Media].CURRENTMEMBER.ORDERKEY,BASC,[Store].[Store]"
          + ".CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Store].[Store].CURRENTMEMBER,[Store].[Store Country]).ORDERKEY,BASC,"
          + "[Education Level].[Education Level].CURRENTMEMBER.ORDERKEY,BASC,[Product].[Product].CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].[Product]"
          + ".CURRENTMEMBER,[Product].[Product].[Product Subcategory]).ORDERKEY,BASC,[Gender].[Gender].CURRENTMEMBER.ORDERKEY,BASC)'\n"
          + "SET [*BASE_MEMBERS__Gender_] AS '{[Gender].[Gender].[F]}'\n"
          + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
          + "SOLVE_ORDER=500\n"
          + "MEMBER [Measures].[*Store Cost_SEL~SUM] AS '([Measures].[Store Cost],[Promotion Media].CURRENTMEMBER,"
          + "[Store].CURRENTMEMBER,[Education Level].CURRENTMEMBER,[Product].CURRENTMEMBER,[Gender].[Gender].CURRENTMEMBER,"
          + "[Time].[Time].[*CTX_MEMBER_SEL~AGG])', SOLVE_ORDER=400\n"
          + "MEMBER [Time].[Time].[*CTX_MEMBER_SEL~AGG] AS 'AGGREGATE([*NATIVE_MEMBERS__Time_])', SOLVE_ORDER=-301\n"
          + "SELECT\n"
          + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
          + ",NON EMPTY\n"
          + "[*SORTED_ROW_AXIS] ON ROWS\n"
          + "FROM [Sales]\n"
          + "WHERE ([*CJ_SLICER_AXIS])").returnsGrid(
        "Axis #0:\n"
          + "{[Time].[Time].[1997].[Q1].[3]}\n"
          + "{[Time].[Time].[1997].[Q2].[4]}\n"
          + "{[Time].[Time].[1997].[Q1].[2]}\n"
          + "Axis #1:\n"
          + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
          + "Axis #2:\n"
          + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio], [Store].[Store].[USA].[OR], [Education Level].[Education Level].[Bachelors Degree], "
          + "[Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio], [Store].[Store].[USA].[OR], [Education Level].[Education Level].[High School Degree], "
          + "[Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio], [Store].[Store].[USA].[OR], [Education Level].[Education Level].[Partial High School], "
          + "[Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Big Time], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio, TV], [Store].[Store].[USA].[OR], [Education Level].[Education Level].[Partial High School], "
          + "[Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[No Media], [Store].[Store].[USA].[OR], [Education Level].[Education Level].[High School Degree], [Product].[Product]"
          + ".[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[No Media], [Store].[Store].[USA].[OR], [Education Level].[Education Level].[Partial College], [Product].[Product].[Food]"
          + ".[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[No Media], [Store].[Store].[USA].[OR], [Education Level].[Education Level].[Partial High School], [Product].[Product]"
          + ".[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[No Media], [Store].[Store].[USA].[OR], [Education Level].[Education Level].[Partial High School], [Product].[Product]"
          + ".[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB Best], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[No Media], [Store].[Store].[USA].[OR], [Education Level].[Education Level].[Partial High School], [Product].[Product]"
          + ".[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Big Time], [Gender].[Gender].[F]}\n"
          + "Row #0: 2\n"
          + "Row #1: 4\n"
          + "Row #2: 5\n"
          + "Row #3: 4\n"
          + "Row #4: 2\n"
          + "Row #5: 5\n"
          + "Row #6: 3\n"
          + "Row #7: 4\n"
          + "Row #8: 3\n" );
    });
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR", type = String.class)
  void testMon2202AnalyzerCompoundMeasureFilterPlusTopCount(Context<?> context)  {
    withDuckDbColumnLifetimeDisabled(context, () -> {
        assertThatQuery(context.getConnectionWithDefaultRole(),
        "WITH\n"
          + "SET [*NATIVE_CJ_SET] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Promotion Media_],NONEMPTYCROSSJOIN"
          + "([*BASE_MEMBERS__Product_],NONEMPTYCROSSJOIN([*BASE_MEMBERS__Gender_],[*BASE_MEMBERS__Time_])))'\n"
          + "SET [*METRIC_CJ_SET] AS 'FILTER(FILTER([*NATIVE_CJ_SET],[Measures].[*Store Cost_SEL~SUM] > 0.0 AND "
          + "[Measures].[*Unit Sales_SEL~SUM] > 0.0),[Measures].[*TOP_Customer Count_SEL~SUM] <= 2)'\n"
          + "SET [*NATIVE_MEMBERS__Time_] AS 'GENERATE([*NATIVE_CJ_SET], {[Time].[Time].CURRENTMEMBER})'\n"
          + "SET [*NATIVE_MEMBERS__Gender_] AS 'GENERATE([*NATIVE_CJ_SET], {[Gender].[Gender].CURRENTMEMBER})'\n"
          + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
          + "SET [*BASE_MEMBERS__Time_] AS '{[Time].[Time].[1997].[Q2].[4],[Time].[Time].[1997].[Q1].[2],[Time].[1997].[Q1].[1],"
          + "[Time].[Time].[1997].[Q1].[3]}'\n"
          + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*METRIC_CJ_SET], {([Time].[Time].CURRENTMEMBER)})'\n"
          + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*METRIC_CJ_SET], {([Promotion Media].[Promotion Media].CURRENTMEMBER,[Product].[Product]"
          + ".CURRENTMEMBER,[Gender].CURRENTMEMBER)})'\n"
          + "SET [*BASE_MEMBERS__Promotion Media_] AS '[Promotion Media].[Promotion Media].[Media Type].MEMBERS'\n"
          + "SET [*BASE_MEMBERS__Product_] AS '{[Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American],[Product].[Product]"
          + ".[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB Best],[Product].[Product].[Food].[Frozen Foods].[Breakfast "
          + "Foods].[Pancake Mix].[Big Time]}'\n"
          + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Promotion Media].[Promotion Media].CURRENTMEMBER.ORDERKEY,BASC,[Product].[Product]"
          + ".CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].[Product].CURRENTMEMBER,[Product].[Product].[Product Subcategory]).ORDERKEY,"
          + "BASC,[Gender].[Gender].CURRENTMEMBER.ORDERKEY,BASC)'\n"
          + "SET [*BASE_MEMBERS__Gender_] AS '{[Gender].[Gender].[F]}'\n"
          + "MEMBER [Gender].[*CTX_MEMBER_SEL~SUM] AS 'SUM([*NATIVE_MEMBERS__Gender_])', SOLVE_ORDER=98\n"
          + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
          + "SOLVE_ORDER=500\n"
          + "MEMBER [Measures].[*Store Cost_SEL~SUM] AS '([Measures].[Store Cost],[Promotion Media].[Promotion Media].CURRENTMEMBER,"
          + "[Product].[Product].CURRENTMEMBER,[Gender].[Gender].[*CTX_MEMBER_SEL~SUM],[Time].[Time].[*CTX_MEMBER_SEL~AGG])', SOLVE_ORDER=400\n"
          + "MEMBER [Measures].[*TOP_Customer Count_SEL~SUM] AS 'RANK([Product].[Product].CURRENTMEMBER,ORDER(FILTER(GENERATE"
          + "(EXISTS([*NATIVE_CJ_SET], {([Promotion Media].[Promotion Media].CURRENTMEMBER)}),{[Product].[Product].CURRENTMEMBER}),[Measures]"
          + ".[*Store Cost_SEL~SUM] > 0.0 AND [Measures].[*Unit Sales_SEL~SUM] > 0.0),([Measures].[Customer Count],"
          + "[Promotion Media].[Promotion Media].CURRENTMEMBER,[Gender].[*CTX_MEMBER_SEL~SUM],[Time].[Time].[*CTX_MEMBER_SEL~AGG]),BDESC))', "
          + "SOLVE_ORDER=400\n"
          + "MEMBER [Measures].[*Unit Sales_SEL~SUM] AS '([Measures].[Unit Sales],[Promotion Media].CURRENTMEMBER,"
          + "[Product].[Product].CURRENTMEMBER,[Gender].[*CTX_MEMBER_SEL~SUM],[Time].[Time].[*CTX_MEMBER_SEL~AGG])', SOLVE_ORDER=400\n"
          + "MEMBER [Time].[Time].[*CTX_MEMBER_SEL~AGG] AS 'AGGREGATE([*NATIVE_MEMBERS__Time_])', SOLVE_ORDER=-301\n"
          + "SELECT\n"
          + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
          + ",NON EMPTY\n"
          + "[*SORTED_ROW_AXIS] ON ROWS\n"
          + "FROM [Sales]\n"
          + "WHERE ([*CJ_SLICER_AXIS])").returnsGrid(
        "Axis #0:\n"
          + "{[Time].[Time].[1997].[Q1].[2]}\n"
          + "{[Time].[Time].[1997].[Q1].[3]}\n"
          + "{[Time].[Time].[1997].[Q2].[4]}\n"
          + "{[Time].[Time].[1997].[Q1].[1]}\n"
          + "Axis #1:\n"
          + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
          + "Axis #2:\n"
          + "{[Promotion Media].[Promotion Media].[Daily Paper], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Daily Paper], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB Best], "
          + "[Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender]"
          + ".[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix]"
          + ".[Big Time], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio, TV], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American], "
          + "[Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[In-Store Coupon], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender]"
          + ".[F]}\n"
          + "{[Promotion Media].[Promotion Media].[In-Store Coupon], [Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Big"
          + " Time], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[No Media], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[No Media], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB Best], "
          + "[Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Product Attachment], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender]"
          + ".[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Street Handout], [Product].[Product].[Food].[Deli].[Meat].[Deli Meats].[American], [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Street Handout], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB Best],"
          + " [Gender].[Gender].[F]}\n"
          + "{[Promotion Media].[Promotion Media].[Sunday Paper, Radio], [Product].[Product].[Drink].[Beverages].[Hot Beverages].[Chocolate].[BBB "
          + "Best], [Gender].[Gender].[F]}\n"
          + "Row #0: 2\n"
          + "Row #1: 3\n"
          + "Row #2: 6\n"
          + "Row #3: 5\n"
          + "Row #4: 4\n"
          + "Row #5: 3\n"
          + "Row #6: 3\n"
          + "Row #7: 69\n"
          + "Row #8: 17\n"
          + "Row #9: 4\n"
          + "Row #10: 5\n"
          + "Row #11: 3\n"
          + "Row #12: 3\n" );
    });
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testNonEmptyCrossJoinCalcMember(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(), new StringBuilder()
        .append( "WITH \n" )
        .append( "MEMBER Measures.Calc AS '[Measures].[Profit] * 2', SOLVE_ORDER=1000\n" )
        .append( "MEMBER Product.Conditional as 'Iif(Measures.CurrentMember IS Measures.[Calc], + Measures.CurrentMember, " )
        .append( "null)', SOLVE_ORDER=2000\n" ).append( "SET [S2] AS '{[Store].[Store].MEMBERS}' \n" )
        .append( "SET [S1] AS 'CROSSJOIN({[Customers].[Customers].[All Customers]},{Product.Product.Conditional})' \n" ).append( "SELECT \n" )
        .append( "NON EMPTY GENERATE({Measures.[Calc]}, CROSSJOIN(HEAD({([Measures].CURRENTMEMBER)}, 1),{[S1]}), ALL) ON AXIS" )
        .append( "(0), NON EMPTY [S2] ON AXIS(1) \n" )
        .append( "FROM [Sales]" ).toString()).returnsGrid(
      String.format(
        "Axis #0:\n{}\nAxis #1:\n{[Measures].[Calc], [Customers].[Customers].[All Customers], [Product].[Product].[Conditional]}\nAxis "
          + "#2:\n{[Store].[Store].[All Stores]}\n{[Store].[Store].[USA]}\n{[Store].[Store].[USA].[CA]}\n{[Store].[Store].[USA].[CA].[Beverly "
          + "Hills]}\n{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n{[Store].[Store].[USA].[CA].[Los Angeles]}\n{[Store].[Store]"
          + ".[USA].[CA].[Los Angeles].[Store 7]}\n{[Store].[Store].[USA].[CA].[San Diego]}\n{[Store].[Store].[USA].[CA].[San Diego]"
          + ".[Store 24]}\n{[Store].[Store].[USA].[CA].[San Francisco]}\n{[Store].[Store].[USA].[CA].[San Francisco].[Store "
          + "14]}\n{[Store].[Store].[USA].[OR]}\n{[Store].[Store].[USA].[OR].[Portland]}\n{[Store].[Store].[USA].[OR].[Portland].[Store "
          + "11]}\n{[Store].[Store].[USA].[OR].[Salem]}\n{[Store].[Store].[USA].[OR].[Salem].[Store 13]}\n{[Store].[Store].[USA]"
          + ".[WA]}\n{[Store].[Store].[USA].[WA].[Bellingham]}\n{[Store].[Store].[USA].[WA].[Bellingham].[Store 2]}\n{[Store].[Store].[USA]"
          + ".[WA].[Bremerton]}\n{[Store].[Store].[USA].[WA].[Bremerton].[Store 3]}\n{[Store].[Store].[USA].[WA].[Seattle]}\n{[Store].[Store]"
          + ".[USA].[WA].[Seattle].[Store 15]}\n{[Store].[Store].[USA].[WA].[Spokane]}\n{[Store].[Store].[USA].[WA].[Spokane].[Store "
          + "16]}\n{[Store].[Store].[USA].[WA].[Tacoma]}\n{[Store].[Store].[USA].[WA].[Tacoma].[Store 17]}\n{[Store].[Store].[USA].[WA].[Walla "
          + "Walla]}\n{[Store].[Store].[USA].[WA].[Walla Walla].[Store 22]}\n{[Store].[Store].[USA].[WA].[Yakima]}\n{[Store].[Store].[USA].[WA]"
          + ".[Yakima].[Store 23]}\nRow #0: $679,221.79\nRow #1: $679,221.79\nRow #2: $191,274.83\nRow #3: $54,967"
          + ".60\nRow #4: $54,967.60\nRow #5: $65,547.49\nRow #6: $65,547.49\nRow #7: $65,435.21\nRow #8: $65,435"
          + ".21\nRow #9: $5,324.53\nRow #10: $5,324.53\nRow #11: $171,009.14\nRow #12: $66,219.69\nRow #13: $66,219"
          + ".69\nRow #14: $104,789.45\nRow #15: $104,789.45\nRow #16: $316,937.82\nRow #17: $5,685.23\nRow #18: $5,685"
          + ".23\nRow #19: $63,548.67\nRow #20: $63,548.67\nRow #21: $63,374.53\nRow #22: $63,374.53\nRow #23: $59,677"
          + ".94\nRow #24: $59,677.94\nRow #25: $89,769.36\nRow #26: $89,769.36\nRow #27: $5,651.26\nRow #28: $5,651"
          + ".26\nRow #29: $29,230.83\nRow #30: $29,230.83\n" ) );
  }

  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testCrossJoinCalcMember(Context<?> context)  {
    assertThatQuery(context.getConnectionWithDefaultRole(), String.format(
      "WITH \nMEMBER Measures.Calc AS '[Measures].[Profit] * 2', SOLVE_ORDER=1000\nMEMBER Product.Conditional as 'Iif"
        + "(Measures.CurrentMember IS Measures.[Calc], + Measures.CurrentMember, null)', SOLVE_ORDER=2000\nSET [S2] AS "
        + "'{[Store].MEMBERS}' \nSET [S1] AS 'CROSSJOIN({[Customers].[All Customers]},{Product.Conditional})' \nSELECT "
        + "\nGENERATE({Measures.[Calc]}, CROSSJOIN(HEAD({([Measures].CURRENTMEMBER)}, 1),{[S1]}), ALL) ON AXIS(0), NON "
        + "EMPTY [S2] ON AXIS(1) \nFROM [Sales]" )).returnsGrid(
      String.format(
        "Axis #0:\n{}\nAxis #1:\n{[Measures].[Calc], [Customers].[Customers].[All Customers], [Product].[Product].[Conditional]}\nAxis "
          + "#2:\n{[Store].[Store].[All Stores]}\n{[Store].[Store].[USA]}\n{[Store].[Store].[USA].[CA]}\n{[Store].[Store].[USA].[CA].[Beverly "
          + "Hills]}\n{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n{[Store].[Store].[USA].[CA].[Los Angeles]}\n{[Store].[Store]"
          + ".[USA].[CA].[Los Angeles].[Store 7]}\n{[Store].[Store].[USA].[CA].[San Diego]}\n{[Store].[Store].[USA].[CA].[San Diego]"
          + ".[Store 24]}\n{[Store].[Store].[USA].[CA].[San Francisco]}\n{[Store].[Store].[USA].[CA].[San Francisco].[Store "
          + "14]}\n{[Store].[Store].[USA].[OR]}\n{[Store].[Store].[USA].[OR].[Portland]}\n{[Store].[Store].[USA].[OR].[Portland].[Store "
          + "11]}\n{[Store].[Store].[USA].[OR].[Salem]}\n{[Store].[Store].[USA].[OR].[Salem].[Store 13]}\n{[Store].[Store].[USA]"
          + ".[WA]}\n{[Store].[Store].[USA].[WA].[Bellingham]}\n{[Store].[Store].[USA].[WA].[Bellingham].[Store 2]}\n{[Store].[Store].[USA]"
          + ".[WA].[Bremerton]}\n{[Store].[Store].[USA].[WA].[Bremerton].[Store 3]}\n{[Store].[Store].[USA].[WA].[Seattle]}\n{[Store].[Store]"
          + ".[USA].[WA].[Seattle].[Store 15]}\n{[Store].[Store].[USA].[WA].[Spokane]}\n{[Store].[Store].[USA].[WA].[Spokane].[Store "
          + "16]}\n{[Store].[Store].[USA].[WA].[Tacoma]}\n{[Store].[Store].[USA].[WA].[Tacoma].[Store 17]}\n{[Store].[Store].[USA].[WA].[Walla "
          + "Walla]}\n{[Store].[Store].[USA].[WA].[Walla Walla].[Store 22]}\n{[Store].[Store].[USA].[WA].[Yakima]}\n{[Store].[Store].[USA].[WA]"
          + ".[Yakima].[Store 23]}\nRow #0: $679,221.79\nRow #1: $679,221.79\nRow #2: $191,274.83\nRow #3: $54,967"
          + ".60\nRow #4: $54,967.60\nRow #5: $65,547.49\nRow #6: $65,547.49\nRow #7: $65,435.21\nRow #8: $65,435"
          + ".21\nRow #9: $5,324.53\nRow #10: $5,324.53\nRow #11: $171,009.14\nRow #12: $66,219.69\nRow #13: $66,219"
          + ".69\nRow #14: $104,789.45\nRow #15: $104,789.45\nRow #16: $316,937.82\nRow #17: $5,685.23\nRow #18: $5,685"
          + ".23\nRow #19: $63,548.67\nRow #20: $63,548.67\nRow #21: $63,374.53\nRow #22: $63,374.53\nRow #23: $59,677"
          + ".94\nRow #24: $59,677.94\nRow #25: $89,769.36\nRow #26: $89,769.36\nRow #27: $5,651.26\nRow #28: $5,651"
          + ".26\nRow #29: $29,230.83\nRow #30: $29,230.83\n" ) );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.NonEmptyTestModifier5.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testDefaultMemberNonEmptyContext(Context<?> context)  {
      assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member measures.one as '1' select non empty store2.usa.[OR].children on 0, measures.one on 1 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store2].[Store2].[USA].[OR].[Portland]}\n"
        + "{[Store2].[Store2].[USA].[OR].[Salem]}\n"
        + "Axis #2:\n"
        + "{[Measures].[one]}\n"
        + "Row #0: 1\n"
        + "Row #0: 1\n" );
    assertThatQuery(context.getConnectionWithDefaultRole(), "with member measures.one as '1' "
        + "select store2.usa.[OR].children on 0, measures.one on 1 from sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Store2].[Store2].[USA].[OR].[Portland]}\n"
        + "{[Store2].[Store2].[USA].[OR].[Salem]}\n"
        + "Axis #2:\n"
        + "{[Measures].[one]}\n"
        + "Row #0: 1\n"
        + "Row #0: 1\n" );
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.NonEmptyTestModifier7.class },
          database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
  void testCalcMeasureInVirtualCubeWithoutBaseComponents(Context<?> context)  {
    // http://jira.pentaho.com/browse/ANALYZER-3630
      NativeVerify.assertSameNativeAndNot(context,
      "select "
        + " [Measures].[dummyMeasure2] on COLUMNS, "
        + " NON EMPTY CrossJoin([Store].[Store State].Members, Time.[Year].members) ON ROWS "
        + " from [virtual] ",
      "");
  }

  /** {@link SchemaModifiersEmf.NonEmptyTestModifier2} pinned to {@code HideMemberIf.IF_BLANK_NAME}, so it fits the
   * single-{@code Catalog}-arg constructor shape {@code @RolapContextTest(catalog = ...)} composition requires. */
  public static class NonEmptyTestModifier2HideIfBlankName extends SchemaModifiersEmf.NonEmptyTestModifier2 {
      public NonEmptyTestModifier2HideIfBlankName(Catalog cat) {
          super(cat, HideMemberIf.IF_BLANK_NAME);
      }
  }

  /** {@link SchemaModifiersEmf.NonEmptyTestModifier2} pinned to {@code HideMemberIf.IF_PARENTS_NAME}. */
  public static class NonEmptyTestModifier2HideIfParentsName extends SchemaModifiersEmf.NonEmptyTestModifier2 {
      public NonEmptyTestModifier2HideIfParentsName(Catalog cat) {
          super(cat, HideMemberIf.IF_PARENTS_NAME);
      }
  }

  /** Named bridge onto the FoodMart CSVs (for the data=-Supplier form). */
  public static class FoodmartData implements org.eclipse.daanse.cwm.testkit.api.DataSupplier {
      @Override
      public java.util.Map<String, java.net.URL> csvResources() {
          return new FoodmartTestInstance().dataSupplier().csvResources();
      }
  }
}
