/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2020 Hitachi Vantara
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

import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.NativeVerify.assertSameNativeAndNot;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.exceptions.NativeEvaluationUnsupportedException;
import  org.eclipse.daanse.olap.util.Bug;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.Mdx;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.BatchTestCase;
import org.eclipse.daanse.rolap.SchemaModifiersEmf;
import org.eclipse.daanse.test.FoodmartData;

/**
 * Test native evaluation of supported set operations.
 *
 * <p>
 */
@RolapContextTest(FoodmartTestInstance.class)
class NativeSetEvaluationTest extends BatchTestCase {




  @AfterEach
  public void afterEach() {
  }

  /**
   * we'll reuse this in a few variations
   */
  private static final class NativeTopCountWithAgg {
    public static  String getMysql(Connection connection) {
      return "select\n"
              + "    `product_class`.`product_family` as `c0`,\n"
              + "    `product_class`.`product_department` as `c1`,\n"
              + "    `product_class`.`product_category` as `c2`,\n"
              + "    `product_class`.`product_subcategory` as `c3`,\n"
              + "    `product`.`brand_name` as `c4`,\n"
              + "    `product`.`product_name` as `c5`,\n"
              + "    sum(`sales_fact_1997`.`store_sales`) as `c6`\n"
              + "from\n"
              + "    `product` as `product`\n"
              + "    join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id`\n"
              + "    join `sales_fact_1997` as `sales_fact_1997` on `sales_fact_1997`.`product_id` = `product`.`product_id`\n"
              + "    join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
              // aggregate set
              + "where\n"
              + "    `time_by_day`.`the_year` = 1997\n"
              + "and\n"
              + "    `time_by_day`.`week_of_year` in (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, "
              + "20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39)\n"
              + "group by\n"
              + "    `product_class`.`product_family`,\n"
              + "    `product_class`.`product_department`,\n"
              + "    `product_class`.`product_category`,\n"
              + "    `product_class`.`product_subcategory`,\n"
              + "    `product`.`brand_name`,\n"
              + "    `product`.`product_name`\n"
              + "order by\n"
              // top count Measures.[Store Sales]
              + (getDialect(connection).requiresOrderByAlias()
              ? "    `c6` DESC,\n"
              + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
              + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
              + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
              + "    ISNULL(`c3`) ASC, `c3` ASC,\n"
              + "    ISNULL(`c4`) ASC, `c4` ASC,\n"
              + "    ISNULL(`c5`) ASC, `c5` ASC"
              : "    sum(`sales_fact_1997`.`store_sales`) DESC,\n"
              + "    ISNULL(`product_class`.`product_family`) ASC, `product_class`.`product_family` ASC,\n"
              + "    ISNULL(`product_class`.`product_department`) ASC, `product_class`.`product_department` ASC,\n"
              + "    ISNULL(`product_class`.`product_category`) ASC, `product_class`.`product_category` ASC,\n"
              + "    ISNULL(`product_class`.`product_subcategory`) ASC, `product_class`.`product_subcategory` ASC,\n"
              + "    ISNULL(`product`.`brand_name`) ASC, `product`.`brand_name` ASC,\n"
              + "    ISNULL(`product`.`product_name`) ASC, `product`.`product_name` ASC");
    }

    private static String getMysqlAgg(Connection connection) {
      return "select\n"
              + "    `product_class`.`product_family` as `c0`,\n"
              + "    `product_class`.`product_department` as `c1`,\n"
              + "    `product_class`.`product_category` as `c2`,\n"
              + "    `product_class`.`product_subcategory` as `c3`,\n"
              + "    `product`.`brand_name` as `c4`,\n"
              + "    `product`.`product_name` as `c5`,\n"
              + "    sum(`agg_pl_01_sales_fact_1997`.`store_sales_sum`) as `c6`\n"
              + "from\n"
              + "    `product` as `product`\n"
              + "    join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id`\n"
              + "    join `agg_pl_01_sales_fact_1997` as `agg_pl_01_sales_fact_1997` on `agg_pl_01_sales_fact_1997`.`product_id` = `product`.`product_id`\n"
              + "    join `time_by_day` as `time_by_day` on `agg_pl_01_sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
              + "where\n"
              + "    `time_by_day`.`the_year` = 1997\n"
              + "and\n"
              + "    `time_by_day`.`week_of_year` in (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, "
              + "20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39)\n"
              + "group by\n"
              + "    `product_class`.`product_family`,\n"
              + "    `product_class`.`product_department`,\n"
              + "    `product_class`.`product_category`,\n"
              + "    `product_class`.`product_subcategory`,\n"
              + "    `product`.`brand_name`,\n"
              + "    `product`.`product_name`\n"
              + "order by\n"
              + (getDialect(connection).requiresOrderByAlias()
              ? "    `c6` DESC,\n"
              + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
              + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
              + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
              + "    ISNULL(`c3`) ASC, `c3` ASC,\n"
              + "    ISNULL(`c4`) ASC, `c4` ASC,\n"
              + "    ISNULL(`c5`) ASC, `c5` ASC"
              : "    sum(`agg_pl_01_sales_fact_1997`.`store_sales_sum`) DESC,\n"
              + "    ISNULL(`product_class`.`product_family`) ASC, `product_class`.`product_family` ASC,\n"
              + "    ISNULL(`product_class`.`product_department`) ASC, `product_class`.`product_department` ASC,\n"
              + "    ISNULL(`product_class`.`product_category`) ASC, `product_class`.`product_category` ASC,\n"
              + "    ISNULL(`product_class`.`product_subcategory`) ASC, `product_class`.`product_subcategory` ASC,\n"
              + "    ISNULL(`product`.`brand_name`) ASC, `product`.`brand_name` ASC,\n"
              + "    ISNULL(`product`.`product_name`) ASC, `product`.`product_name` ASC");
    }
    static final String result =
      "Axis #0:\n"
        + "{[Time].[Weekly].[x]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Sales]}\n"
        + "{[Measures].[x1]}\n"
        + "{[Measures].[x2]}\n"
        + "{[Measures].[x3]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Green Pepper]}\n"
        + "{[Product].[Product].[Non-Consumable].[Health and Hygiene].[Bathroom Products].[Mouthwash].[Hilltop].[Hilltop Mint "
        + "Mouthwash]}\n"
        + "Row #0: 737.26\n"
        + "Row #0: 281.78\n"
        + "Row #0: 165.98\n"
        + "Row #0: 262.48\n"
        + "Row #1: 680.56\n"
        + "Row #1: 264.26\n"
        + "Row #1: 173.76\n"
        + "Row #1: 188.24\n";
  }

  /**
   * Simple enumerated aggregate.
   */
  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeTopCountWithAggFlatSet(Context<?> context) {
	context.getCatalogCache().clear();
    // Note: changed mdx and expected as a part of the fix for MONDRIAN-2202
    // Formerly the aggregate set and measures used a conflicting hierarchy,
    // which is not a safe scenario for nativization.
    final boolean useAgg =
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class);

    final String mdx =
      "with\n"
        + "member [Time].[Weekly].x as Aggregate({[Time].[Weekly].[1997].[1] : [Time].[Weekly].[1997].[39]}, [Measures]"
        + ".[Store Sales])\n"
        + "member Measures.x1 as ([Time].[Time].[1997].[Q1], [Measures].[Store Sales])\n"
        + "member Measures.x2 as ([Time].[Time].[1997].[Q2], [Measures].[Store Sales])\n"
        + "member Measures.x3 as ([Time].[Time].[1997].[Q3], [Measures].[Store Sales])\n"
        + " set products as TopCount(Product.Product.[Product Name].Members, 2, Measures.[Store Sales])\n"
        + " SELECT NON EMPTY products ON 1,\n"
        + "NON EMPTY {[Measures].[Store Sales], Measures.x1, Measures.x2, Measures.x3} ON 0\n"
        + "FROM [Sales] where [Time].[Weekly].x";

    Connection connection = context.getConnectionWithDefaultRole();
    SqlPattern mysqlPattern = useAgg
      ? new SqlPattern(
      DatabaseProduct.MYSQL,
      NativeTopCountWithAgg.getMysqlAgg(connection),
      NativeTopCountWithAgg.getMysqlAgg(connection))
      : new SqlPattern(
      DatabaseProduct.MYSQL,
      NativeTopCountWithAgg.getMysql(connection),
      NativeTopCountWithAgg.getMysql(connection));
    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class) ) {
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
    }
    assertThatQuery(context.getConnectionWithDefaultRole(), mdx).returnsGrid( NativeTopCountWithAgg.result );
  }

  /**
   * Same as above, but using a named set
   */
  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeTopCountWithAggMemberNamedSet(Context<?> context) {
	context.getCatalogCache().clear();
    final boolean useAgg =
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class);
    final String mdx =
      "with set TO_AGGREGATE as '{[Time].[Weekly].[1997].[1] : [Time].[Weekly].[1997].[39]}'\n"
        + "member [Time].[Weekly].x as Aggregate(TO_AGGREGATE, [Measures].[Store Sales])\n"
        + "member Measures.x1 as ([Time].[Time].[1997].[Q1], [Measures].[Store Sales])\n"
        + "member Measures.x2 as ([Time].[Time].[1997].[Q2], [Measures].[Store Sales])\n"
        + "member Measures.x3 as ([Time].[Time].[1997].[Q3], [Measures].[Store Sales])\n"
        + " set products as TopCount(Product.Product.[Product Name].Members, 2, Measures.[Store Sales])\n"
        + " SELECT NON EMPTY products ON 1,\n"
        + "NON EMPTY {[Measures].[Store Sales], Measures.x1, Measures.x2, Measures.x3} ON 0\n"
        + "FROM [Sales] where [Time].[Weekly].x";
    Connection connection = context.getConnectionWithDefaultRole();
    SqlPattern mysqlPattern = useAgg ? new SqlPattern(
      DatabaseProduct.MYSQL,
      NativeTopCountWithAgg.getMysqlAgg(connection),
      NativeTopCountWithAgg.getMysqlAgg(connection) )
      : new SqlPattern(
      DatabaseProduct.MYSQL,
      NativeTopCountWithAgg.getMysql(connection),
      NativeTopCountWithAgg.getMysql(connection));
    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class)
      && context.getConfigValue(ConfigConstants.ENABLE_NATIVE_NON_EMPTY, ConfigConstants.ENABLE_NATIVE_NON_EMPTY_DEFAULT_VALUE, Boolean.class) ) {
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
    }
    assertThatQuery(context.getConnectionWithDefaultRole(), mdx).returnsGrid( NativeTopCountWithAgg.result );
  }

  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeFilterWithAggDescendants(Context<?> context) {
	  context.getCatalogCache().clear();
	  context.getConnectionWithDefaultRole().getCacheControl(null).flushSchemaCache();
    final boolean useAgg =
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class);
    final String mdx =
      "with\n"
        + "  set QUARTERS as Descendants([Time].[Time].[1997], [Time].[Time].[Quarter])\n"
        + "  member Time.Time.x as Aggregate(QUARTERS, [Measures].[Store Sales])\n"
        + "  set products as Filter([Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].Children, "
        + "[Measures].[Store Sales] > 700)\n"
        + "  SELECT NON EMPTY products ON 1,\n"
        + "  NON EMPTY {[Measures].[Store Sales]} ON 0\n"
        + "  FROM [Sales] where Time.Time.x";

    final String mysqlQuery =
      "select\n"
        + "    `product_class`.`product_family` as `c0`,\n"
        + "    `product_class`.`product_department` as `c1`,\n"
        + "    `product_class`.`product_category` as `c2`,\n"
        + "    `product_class`.`product_subcategory` as `c3`,\n"
        + "    `product`.`brand_name` as `c4`,\n"
        + "    `product`.`product_name` as `c5`\n"
        + "from\n"
        + "    `product` as `product`\n"
        + "    join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + ( useAgg ? "    join `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997` on `agg_c_14_sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "where\n"
        + "    `agg_c_14_sales_fact_1997`.`the_year` = 1997\n"
        + "and\n"
        + "    `agg_c_14_sales_fact_1997`.`quarter` in ('Q1', 'Q2', 'Q3', 'Q4')\n"
        : "    join `sales_fact_1997` as `sales_fact_1997` on `sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "    join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
        + "where\n"
        + "    `time_by_day`.`the_year` = 1997\n"
        + "and\n"
        + "    `time_by_day`.`quarter` in ('Q1', 'Q2', 'Q3', 'Q4')\n" )
        + "and\n"
        + "    (`product`.`brand_name` = 'Hermanos' and `product_class`.`product_subcategory` = 'Fresh Vegetables' "
        + "and `product_class`.`product_category` = 'Vegetables' and `product_class`.`product_department` = 'Produce'"
        + " and `product_class`.`product_family` = 'Food')\n"
        + "group by\n"
        + "    `product_class`.`product_family`,\n"
        + "    `product_class`.`product_department`,\n"
        + "    `product_class`.`product_category`,\n"
        + "    `product_class`.`product_subcategory`,\n"
        + "    `product`.`brand_name`,\n"
        + "    `product`.`product_name`\n"
        + "having\n"
        + ( useAgg ? "    (sum(`agg_c_14_sales_fact_1997`.`store_sales`) > 700)\n"
        : "    (sum(`sales_fact_1997`.`store_sales`) > 700)\n" )
        + "order by\n"
        + (getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
        + "    ISNULL(`c3`) ASC, `c3` ASC,\n"
        + "    ISNULL(`c4`) ASC, `c4` ASC,\n"
        + "    ISNULL(`c5`) ASC, `c5` ASC"
        : "    ISNULL(`product_class`.`product_family`) ASC, `product_class`.`product_family` ASC,\n"
        + "    ISNULL(`product_class`.`product_department`) ASC, `product_class`.`product_department` ASC,\n"
        + "    ISNULL(`product_class`.`product_category`) ASC, `product_class`.`product_category` ASC,\n"
        + "    ISNULL(`product_class`.`product_subcategory`) ASC, `product_class`.`product_subcategory` ASC,\n"
        + "    ISNULL(`product`.`brand_name`) ASC, `product`.`brand_name` ASC,\n"
        + "    ISNULL(`product`.`product_name`) ASC, `product`.`product_name` ASC" );

    SqlPattern mysqlPattern =
      new SqlPattern(
        DatabaseProduct.MYSQL,
        mysqlQuery,
        mysqlQuery );
    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_FILTER, ConfigConstants.ENABLE_NATIVE_FILTER_DEFAULT_VALUE, Boolean.class)
      && context.getConfigValue(ConfigConstants.ENABLE_NATIVE_NON_EMPTY, ConfigConstants.ENABLE_NATIVE_NON_EMPTY_DEFAULT_VALUE, Boolean.class) ) {
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
    }
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[x]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Broccoli]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Green Pepper]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos New Potatos]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Prepared Salad]}\n"
        + "Row #0: 742.73\n"
        + "Row #1: 922.54\n"
        + "Row #2: 703.80\n"
        + "Row #3: 718.08\n" );
  }


  /**
   * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-1426"> Mondrian-1426:</a> Native top count support
   * for Member expressions in Calculated member slicer
   */
  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeTopCountWithMemberOnlySlicer(Context<?> context) {
	context.getCatalogCache().clear();
    final boolean useAggregates =
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class);
    final String mdx =
      "WITH\n"
        + "  SET TC AS 'TopCount([Product].[Product].[Drink].[Alcoholic Beverages].Children, 3, [Measures].[Unit Sales] )'\n"
        + "  MEMBER [Time].[Time].[Slicer] as [Time].[Time].[1997]\n"
        + "  MEMBER [Store Type].[Store Type].[Slicer] as [Store Type].[Store Type].[Store Type].[Deluxe Supermarket]\n"
        + "\n"
        + "  SELECT NON EMPTY [Measures].[Unit Sales] on 0,\n"
        + "    TC ON 1 \n"
        + "  FROM [Sales] WHERE {([Time].[Time].[Slicer], [Store Type].[Store Type].[Slicer])}\n";

    String mysqlQuery =
      "select\n"
        + "    `product_class`.`product_family` as `c0`,\n"
        + "    `product_class`.`product_department` as `c1`,\n"
        + "    `product_class`.`product_category` as `c2`,\n"
        + ( useAggregates
        ? ( "    sum(`agg_c_14_sales_fact_1997`.`unit_sales`) as `c3`\n"
        + "from\n"
        + "    `product` as `product`\n"
        + "join\n"
        + "    `product_class` as `product_class`\n"
        + "on\n"
        + "    `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "join\n"
        + "    `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997`\n"
        + "on\n"
        + "    `agg_c_14_sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "join\n"
        + "    `store` as `store`\n"
        + "on\n"
        + "    `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id`\n"
        + "where\n"
        + "    `store`.`store_type` = 'Deluxe Supermarket'\n"
        + "and\n"
        + "    `agg_c_14_sales_fact_1997`.`the_year` = 1997\n" )
        : ( "    sum(`sales_fact_1997`.`unit_sales`) as `c3`\n"
        + "from\n"
        + "    `product` as `product`\n"
        + "join\n"
        + "    `product_class` as `product_class`\n"
        + "on\n"
        + "    `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "join\n"
        + "    `sales_fact_1997` as `sales_fact_1997`\n"
        + "on\n"
        + "    `sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "join\n"
        + "    `store` as `store`\n"
        + "on\n"
        + "    `sales_fact_1997`.`store_id` = `store`.`store_id`\n"
        + "join\n"
        + "    `time_by_day` as `time_by_day`\n"
        + "on\n"
        + "    `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
        + "where\n"
        + "    `store`.`store_type` = `Deluxe Supermarket`\n"
        + "and\n"
        + "    `time_by_day`.`the_year` = 1997\n" ) )
        + "and\n"
        + "    (`product_class`.`product_department` = 'Alcoholic Beverages' and `product_class`.`product_family` = "
        + "'Drink')\n"
        + "group by\n"
        + "    `product_class`.`product_family`,\n"
        + "    `product_class`.`product_department`,\n"
        + "    `product_class`.`product_category`\n"
        + "order by\n"
        + (getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    `c3` DESC,\n"
        + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c2`) ASC, `c2` ASC"
        : "    sum(`"
        + ( useAggregates
        ? "agg_c_14_sales_fact_1997"
        : "sales_fact_1997" )
        + "`.`unit_sales`) DESC,\n"
        + "    ISNULL(`product_class`.`product_family`) ASC, `product_class`.`product_family` ASC,\n"
        + "    ISNULL(`product_class`.`product_department`) ASC, `product_class`.`product_department` ASC,\n"
        + "    ISNULL(`product_class`.`product_category`) ASC, `product_class`.`product_category` ASC" );

    SqlPattern mysqlPattern =
      new SqlPattern(
        DatabaseProduct.MYSQL,
        mysqlQuery,
        mysqlQuery.indexOf( "(" ) );
    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class) ) {
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
    }
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[Slicer], [Store Type].[Store Type].[Slicer]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]}\n"
        + "Row #0: 1,910\n" );
  }

  /**
   * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-1430"> Mondrian-1430:</a> Native top count support
   * for + and tuple (Parentheses) expressions in Calculated member slicer
   */
  @Disabled("disabled for CI build") //disabled for CI build
  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeTopCountWithParenthesesMemberSlicer(Context<?> context) {

    final boolean useAggregates =
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class);
    final String mdx =
      "WITH\n"
        + "  SET TC AS 'TopCount([Product].[Drink].[Alcoholic Beverages].Children, 3, [Measures].[Unit Sales] )'\n"
        + "  MEMBER [Time].[Slicer] as [Time].[1997]\n"
        + "  MEMBER [Store Type].[Slicer] as ([Store Type].[Store Type].[Deluxe Supermarket])\n"
        + "\n"
        + "  SELECT NON EMPTY [Measures].[Unit Sales] on 0,\n"
        + "    TC ON 1 \n"
        + "  FROM [Sales] WHERE {([Time].[Slicer], [Store Type].[Slicer])}\n";

    String mysqlQuery =
      "select\n"
        + "    `product_class`.`product_family` as `c0`,\n"
        + "    `product_class`.`product_department` as `c1`,\n"
        + "    `product_class`.`product_category` as `c2`,\n"
        + ( useAggregates
        ? ( "    sum(`agg_c_14_sales_fact_1997`.`unit_sales`) as `c3`\n"
        + "from\n"
        + "    `product` as `product`,\n"
        + "    `product_class` as `product_class`,\n"
        + "    `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997`,\n"
        + "    `store` as `store`\n"
        + "where\n"
        + "    `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "and\n"
        + "    `agg_c_14_sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "and\n"
        + "    `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id`\n"
        + "and\n"
        + "    `store`.`store_type` = 'Deluxe Supermarket'\n"
        + "and\n"
        + "    `agg_c_14_sales_fact_1997`.`the_year` = 1997\n" )
        : ( "    sum(`sales_fact_1997`.`unit_sales`) as `c3`\n"
        + "from\n"
        + "    `product` as `product`,\n"
        + "    `product_class` as `product_class`,\n"
        + "    `sales_fact_1997` as `sales_fact_1997`,\n"
        + "    `store` as `store`,\n"
        + "    `time_by_day` as `time_by_day`\n"
        + "where\n"
        + "    `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "and\n"
        + "    `sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "and\n"
        + "    `sales_fact_1997`.`store_id` = `store`.`store_id`\n"
        + "and\n"
        + "    `store`.`store_type` = `Deluxe Supermarket`\n"
        + "and\n"
        + "    `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
        + "and\n"
        + "    `time_by_day`.`the_year` = 1997\n" ) )
        + "and\n"
        + "    (`product_class`.`product_department` = 'Alcoholic Beverages' and `product_class`.`product_family` = "
        + "'Drink')\n"
        + "group by\n"
        + "    `product_class`.`product_family`,\n"
        + "    `product_class`.`product_department`,\n"
        + "    `product_class`.`product_category`\n"
        + "order by\n"
        + (getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    `c3` DESC,\n"
        + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c2`) ASC, `c2` ASC"
        : "    sum(`"
        + ( useAggregates
        ? "agg_c_14_sales_fact_1997"
        : "sales_fact_1997" )
        + "`.`unit_sales`) DESC,\n"
        + "    ISNULL(`product_class`.`product_family`) ASC, `product_class`.`product_family` ASC,\n"
        + "    ISNULL(`product_class`.`product_department`) ASC, `product_class`.`product_department` ASC,\n"
        + "    ISNULL(`product_class`.`product_category`) ASC, `product_class`.`product_category` ASC" );

    SqlPattern mysqlPattern =
      new SqlPattern(
        DatabaseProduct.MYSQL,
        mysqlQuery,
        mysqlQuery.indexOf( "(" ) );
    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class) ) {
      context.getConnectionWithDefaultRole().getCacheControl(null).flushSchemaCache();
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
    }
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Slicer], [Store Type].[Slicer]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]}\n"
        + "Row #0: 1,910\n" );
  }

  /**
   * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-1430"> Mondrian-1430:</a> Native top count support
   * for + and tuple (Parentheses) expressions in Calculated member slicer
   */
  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeTopCountWithMemberSumSlicer(Context<?> context) {
	context.getCatalogCache().clear();
    final boolean useAggregates =
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class);
    final String mdx =
      "WITH\n"
        + "  SET TC AS 'TopCount([Product].[Product].[Drink].[Alcoholic Beverages].Children, 3, [Measures].[Unit Sales] )'\n"
        + "  MEMBER [Time].[Time].[Slicer] as [Time].[Time].[1997]\n"
        + "  MEMBER [Store Type].[Store Type].[Slicer] as [Store Type].[Store Type].[Deluxe Supermarket] + [Store Type].[Store "
        + "Type].[Gourmet Supermarket]\n"
        + "\n"
        + "  SELECT NON EMPTY [Measures].[Unit Sales] on 0,\n"
        + "    TC ON 1 \n"
        + "  FROM [Sales] WHERE {([Time].[Time].[Slicer], [Store Type].[Store Type].[Slicer])}\n";

    String mysqlQuery =
      "select\n"
        + "    `product_class`.`product_family` as `c0`,\n"
        + "    `product_class`.`product_department` as `c1`,\n"
        + "    `product_class`.`product_category` as `c2`,\n"
        + ( useAggregates
        ? ( "    sum(`agg_c_14_sales_fact_1997`.`unit_sales`) as `c3`\n"
        + "from\n"
        + "    `product` as `product`,\n"
        + "    `product_class` as `product_class`,\n"
        + "    `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997`,\n"
        + "    `store` as `store`\n"
        + "where\n"
        + "    `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "and\n"
        + "    `agg_c_14_sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "and\n"
        + "    `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id`\n"
        + "and\n"
        + "    `store`.`store_type` in ('Deluxe Supermarket', 'Gourmet Supermarket')\n"
        + "and\n"
        + "    `agg_c_14_sales_fact_1997`.`the_year` = 1997\n" )
        : ( "    sum(`sales_fact_1997`.`unit_sales`) as `c3`\n"
        + "from\n"
        + "    `product` as `product`\n"
        + "join\n"
        + "    `product_class` as `product_class`\n"
        + "on\n"
        + "    `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "join\n"
        + "    `sales_fact_1997` as `sales_fact_1997`\n"
        + "on\n"
        + "    `sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "join\n"
        + "    `store` as `store`\n"
        + "on\n"
        + "    `sales_fact_1997`.`store_id` = `store`.`store_id`\n"
        + "join\n"
        + "    `time_by_day` as `time_by_day`\n"
        + "on\n"
        + "    `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
        + "where\n"
        + "    `store`.`store_type` in ('Deluxe Supermarket', 'Gourmet Supermarket')\n"
        + "and\n"
        + "    `time_by_day`.`the_year` = 1997\n" ) )
        + "and\n"
        + "    (`product_class`.`product_department` = 'Alcoholic Beverages' and `product_class`.`product_family` = "
        + "'Drink')\n"
        + "group by\n"
        + "    `product_class`.`product_family`,\n"
        + "    `product_class`.`product_department`,\n"
        + "    `product_class`.`product_category`\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    `c3` DESC,\n"
        + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c2`) ASC, `c2` ASC"
        : "    sum(`"
        + ( useAggregates
        ? "agg_c_14_sales_fact_1997"
        : "sales_fact_1997" )
        + "`.`unit_sales`) DESC,\n"
        + "    ISNULL(`product_class`.`product_family`) ASC, `product_class`.`product_family` ASC,\n"
        + "    ISNULL(`product_class`.`product_department`) ASC, `product_class`.`product_department` ASC,\n"
        + "    ISNULL(`product_class`.`product_category`) ASC, `product_class`.`product_category` ASC" );

    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class) ) {
      SqlPattern mysqlPattern =
        new SqlPattern(
          DatabaseProduct.MYSQL,
          mysqlQuery,
          mysqlQuery.indexOf( "(" ) );
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
    }
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[Slicer], [Store Type].[Store Type].[Slicer]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]}\n"
        + "Row #0: 2,435\n" );
  }

  /**
   * Aggregate with default measure and TopCount without measure argument.
   */
  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testAggTCNoExplicitMeasure(Context<?> context) {
    final String mdx =
      "WITH\n"
        + "  SET TC AS 'TopCount([Product].[Drink].[Alcoholic Beverages].Children, 3)'\n"
        + "  MEMBER [Store Type].[Store Type].[Store Type].[Slicer] as Aggregate([Store Type].[Store Type].[Store Type].Members)\n"
        + "\n"
        + "  SELECT NON EMPTY [Measures].[Unit Sales] on 0,\n"
        + "    TC ON 1 \n"
        + "  FROM [Sales] WHERE [Store Type].[Slicer]\n";
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Store Type].[Store Type].[Slicer]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]}\n"
        + "Row #0: 6,838\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR")
  void testAggTCTwoArg(Context<?> context) {
    // will throw an error if native eval is not used
      // native should be used and Canada/Mexico should be returned
    // even though Canada and Mexico have no associated data.
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select TopCount(Customers.Country.members, 2) "
        + "on 0 from Sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Customers].[Customers].[Canada]}\n"
        + "{[Customers].[Customers].[Mexico]}\n"
        + "Row #0: \n"
        + "Row #0: \n" );
    // TopCount should return in natural order, not order of measure val
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select TopCount(Product.Drink.Children, 2) "
        + "on 0 from Sales").returnsGrid(
      "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Product].[Product].[Drink].[Alcoholic Beverages]}\n"
        + "{[Product].[Product].[Drink].[Beverages]}\n"
        + "Row #0: 6,838\n"
        + "Row #0: 13,573\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR")
  void testAggTCTwoArgWithCrossjoinedSet(Context<?> context) {
	context.getCatalogCache().clear();
    if ( !context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class) ) {
      return;
    }
      Connection connection = context.getConnectionWithDefaultRole();
    try {
      executeQuery(
        "select TopCount( CrossJoin(Gender.Gender.members, Product.Drink.Children), 2) "
          + "on 0 from Sales", connection);
      fail( "Expected expression to fail native eval" );
    } catch ( NativeEvaluationUnsupportedException neue ) {
      assertTrue(
        neue.getMessage().contains( "Native evaluation not supported" ) );
    }
  }

  @Test
  @RolapConfig(key = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, value = "ERROR")
  void testAggTCTwoArgWithCalcMemPresent(Context<?> context) {
	context.getCatalogCache().clear();
    if ( !context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class) ) {
      return;
    }
      Connection connection = context.getConnectionWithDefaultRole();
    try {
      executeQuery(
        "with member Gender.foo as '1'"
          + "select TopCount( {Gender.foo, Gender.Gender.members}, 2) "
          + "on 0 from Sales", connection);
      fail( "Expected expression to fail native eval" );
    } catch ( NativeEvaluationUnsupportedException neue ) {
      assertTrue(
        neue.getMessage().contains( "Native evaluation not supported" ) );
    }
  }

  /**
   * Crossjoin that uses same dimension as slicer but is independent from it, evaluated via a named set. No loop should
   * happen here.
   */
  @Test
  void testCJSameDimAsSlicerNamedSet(Context<?> context) {
    String mdx =
      "WITH\n"
        + "SET ST AS 'TopCount([Store Type].[Store Type].CurrentMember, 5)'\n"
        + "SET TOP_BEV AS 'TopCount([Product].[Drink].Children, 3, [Measures].[Unit Sales])'\n"
        + "SET TC AS TopCount(NonEmptyCrossJoin([Time].[Year].Members, TOP_BEV), 2, [Measures].[Unit Sales])\n"
        + "MEMBER [Product].[Top Drinks] as Aggregate(TC, [Measures].[Unit Sales]) \n"
        + "SET TOP_COUNTRY AS 'TopCount([Customers].[Country].Members, 1, [Measures].[Unit Sales])'\n"
        + "SELECT NON EMPTY [Measures].[Unit Sales] on 0,\n"
        + "  NON EMPTY TOP_COUNTRY ON 1 \n"
        + "FROM [Sales] WHERE [Product].[Top Drinks]";
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Product].[Product].[Top Drinks]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Customers].[Customers].[USA]}\n"
        + "Row #0: 20,411\n" );
  }

  /**
   * Test evaluation loop detection still works after changes to make it more permissable.
   */
  @Test
  @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
  void testLoopDetection(Context<?> context) {
    // Note that this test will fail if the query below is executed
    // non-natively, or if the level.members expressions are replaced
    // with enumerated sets.
    // See http://jira.pentaho.com/browse/MONDRIAN-2337
    if ( !context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class) ) {
      return;
    }
    final String mdx =
      "WITH\n"
        + "  SET CJ AS NonEmptyCrossJoin([Store Type].[Store Type].[Store Type].Members, {[Measures].[Unit Sales]})\n"
        + "  SET TC AS 'TopCount([Store Type].[Store Type].[Store Type].Members, 10, [Measures].[Unit Sales])'\n"
        + "  SET TIME_DEP AS 'Generate(CJ, {[Time].[Time].CurrentMember})' \n"
        + "  MEMBER [Time].[Time].[Slicer] as Aggregate(TIME_DEP)\n"
        + "\n"
        + "  SELECT NON EMPTY [Measures].[Unit Sales] on 0,\n"
        + "    TC ON 1 \n"
        + "  FROM [Sales] where [Time].[Time].[Slicer]\n";
    assertThatQuery(context.getConnectionWithDefaultRole(), mdx).throwsMessage( "evaluating itself" );
  }

  /**
   * Check if getSlicerMembers in native evaluation context doesn't break the results as in MONDRIAN-1187
   */
  @Test
  void testSlicerTuplesPartialCrossJoin(Context<?> context) {
    final String mdx =
      "with\n"
        + "set TSET as {NonEmptyCrossJoin({[Time].[1997].[Q1], [Time].[1997].[Q2]}, {[Store Type].[Supermarket]}),\n"
        + " NonEmptyCrossJoin({[Time].[1997].[Q1]}, {[Store Type].[Deluxe Supermarket], [Store Type].[Gourmet "
        + "Supermarket]}) }\n"
        + " set products as TopCount(Product.[Product Name].Members, 2, Measures.[Store Sales])\n"
        + " SELECT NON EMPTY products ON 1,\n"
        + "NON EMPTY {[Measures].[Store Sales]} ON 0\n"
        + " FROM [Sales]\n"
        + "where TSET";

    final String result =
      "Axis #0:\n"
        + "{[Time].[Time].[1997].[Q1], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Time].[Time].[1997].[Q2], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Time].[Time].[1997].[Q1], [Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Time].[Time].[1997].[Q1], [Store Type].[Store Type].[Gourmet Supermarket]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Food].[Snack Foods].[Snack Foods].[Dried Fruit].[Fort West].[Fort West Raspberry Fruit Roll]}\n"
        + "{[Product].[Product].[Food].[Canned Foods].[Canned Soup].[Soup].[Bravo].[Bravo Noodle Soup]}\n"
        + "Row #0: 372.36\n"
        + "Row #1: 365.20\n";

    assertThatQuery(context.getConnectionWithDefaultRole(), mdx).returnsGrid( result );
  }

  /**
   * Same as before but without combinations missing in the crossjoin
   */
  @Test
  void testSlicerTuplesFullCrossJoin(Context<?> context) {
    if ( !context.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class)
      && !Bug.Bug2452Fixed ) {
      // The NonEmptyCrossJoin in the TSET named set below returns
      // extra tuples due to MONDRIAN-2452.
      return;
    }
    final String mdx =
      "with\n"
        + "set TSET as NonEmptyCrossJoin({[Time].[1997].[Q1], [Time].[1997].[Q2]}, {[Store Type].[Supermarket], "
        + "[Store Type].[Deluxe Supermarket], [Store Type].[Gourmet Supermarket]})\n"
        + " set products as TopCount(Product.[Product Name].Members, 2, Measures.[Store Sales])\n"
        + " SELECT NON EMPTY products ON 1,\n"
        + "NON EMPTY {[Measures].[Store Sales]} ON 0\n"
        + " FROM [Sales]\n"
        + "where TSET";

    String result =
      "Axis #0:\n"
        + "{[Time].[Time].[1997].[Q1], [Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Time].[Time].[1997].[Q1], [Store Type].[Store Type].[Gourmet Supermarket]}\n"
        + "{[Time].[Time].[1997].[Q1], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Time].[Time].[1997].[Q2], [Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Time].[Time].[1997].[Q2], [Store Type].[Store Type].[Gourmet Supermarket]}\n"
        + "{[Time].[Time].[1997].[Q2], [Store Type].[Store Type].[Supermarket]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Food].[Eggs].[Eggs].[Eggs].[Urban].[Urban Small Eggs]}\n"
        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos].[Hermanos Green Pepper]}\n"
        + "Row #0: 460.02\n"
        + "Row #1: 420.74\n";

    assertThatQuery(context.getConnectionWithDefaultRole(), mdx).returnsGrid( result );
  }

  /**
   * Now that some native evaluation is supporting aggregated members, we need to push that logic down to the AggStar
   * selection
   */
  @Test
  @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testTopCountWithAggregatedMemberAggStar(Context<?> context) {
	context.getCatalogCache().clear();

    final String mdx =
      "with member [Time].[Weekly].x as Aggregate([Time].[Weekly].[1997].Children) "
        + "set products as "
        + "'TopCount([Product].[Product].[Product Department].Members, 2, "
        + "[Measures].[Store Sales])' "
        + "select NON EMPTY {[Measures].[Store Sales]} ON COLUMNS, "
        + "NON EMPTY [products] ON ROWS "
        + " from [Sales] where [Time].[Weekly].[x]";

    final String mysql =
      "select\n"
        + "    `product_class`.`product_family` as `c0`,\n"
        + "    `product_class`.`product_department` as `c1`,\n"
        + "    sum(`agg_pl_01_sales_fact_1997`.`store_sales_sum`) as `c2`\n"
        + "from\n"
        + "    `product` as `product`\n"
        + "join\n"
        + "    `product_class` as `product_class`\n"
        + "on\n"
        + "    `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "join\n"
        + "    `agg_pl_01_sales_fact_1997` as `agg_pl_01_sales_fact_1997`\n"
        + "on\n"
        + "    `agg_pl_01_sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "join\n"
        + "    `time_by_day` as `time_by_day`\n"
        + "on\n"
        + "    `agg_pl_01_sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
        + "where\n"
        + "    `time_by_day`.`the_year` = 1997\n"
        + "and\n"
        + "    `time_by_day`.`week_of_year` in (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, "
        + "20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, "
        + "46, 47, 48, 49, 50, 51, 52)\n"
        + "group by\n"
        + "    `product_class`.`product_family`,\n"
        + "    `product_class`.`product_department`\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    `c2` DESC,\n"
        + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC"
        : "    sum(`agg_pl_01_sales_fact_1997`.`store_sales_sum`) DESC,\n"
        + "    ISNULL(`product_class`.`product_family`) ASC, `product_class`.`product_family` ASC,\n"
        + "    ISNULL(`product_class`.`product_department`) ASC, `product_class`.`product_department` ASC" );

    SqlPattern mysqlPattern =
      new SqlPattern(
        DatabaseProduct.MYSQL,
        mysql,
        mysql );

    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class) ) {
      context.getConnectionWithDefaultRole().getCacheControl(null).flushSchemaCache();
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
    }

    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Weekly].[x]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Sales]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Food].[Produce]}\n"
        + "{[Product].[Product].[Food].[Snack Foods]}\n"
        + "Row #0: 82,248.42\n"
        + "Row #1: 67,609.82\n" );
  }

  /**
   * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-1291"> Mondrian-1291:</a> NPE on native set with at
   * least two elements and two all members for same dimension in slicer
   */
  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, TestMultipleAllWithInExprModifier.class },
  database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testMultipleAllWithInExpr(Context<?> context) {
    // set up three hierarchies on same dimension
    final String multiHierarchyCube =
      " <Cube name=\"3StoreHCube\">\n"
        + " <Table name=\"sales_fact_1997\"/>\n"
        + " <Dimension name=\"AltStore\" foreignKey=\"store_id\">\n"
        + " <Hierarchy hasAll=\"true\" primaryKey=\"store_id\" allMemberName=\"All\">\n"
        + " <Table name=\"store\"/>\n"
        + " <Level name=\"Store Name\" column=\"store_name\" uniqueMembers=\"false\"/>\n"
        + " <Level name=\"Store City\" column=\"store_city\" uniqueMembers=\"false\"/>\n"
        + " </Hierarchy>\n"
        + " <Hierarchy name=\"City\" hasAll=\"true\" primaryKey=\"store_id\" allMemberName=\"All\">\n"
        + " <Table name=\"store\"/>\n"
        + " <Level name=\"Store City\" column=\"store_city\" uniqueMembers=\"false\"/>\n"
        + " <Level name=\"Store Name\" column=\"store_name\" uniqueMembers=\"false\"/>\n"
        + " </Hierarchy>\n"
        + " <Hierarchy name=\"State\" hasAll=\"true\" primaryKey=\"store_id\" allMemberName=\"All\">\n"
        + " <Table name=\"store\"/>\n"
        + " <Level name=\"Store State\" column=\"store_state\" uniqueMembers=\"false\"/>\n"
        + " <Level name=\"Store City\" column=\"store_city\" uniqueMembers=\"false\"/>\n"
        + " <Level name=\"Store Name\" column=\"store_name\" uniqueMembers=\"false\"/>\n"
        + " </Hierarchy>\n"
        + " </Dimension>\n"
        + " <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/> \n"
        + " <DimensionUsage name=\"Product\" source=\"Product\" foreignKey=\"product_id\"/>\n"
        + " <Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\" formatString=\"#,###.00\"/>\n"
        + " </Cube>";
    // slicer with multiple elements and two All members
    final String mdx =
      "with member [AltStore].[AltStore].[AllSlicer] as 'Aggregate({[AltStore].[AltStore].[All]})'\n"
        + " member [AltStore].[City].[SetSlicer] as 'Aggregate({[AltStore].[City].[San Francisco], [AltStore].[City].[San "
        + "Diego]})'\n"
        + " member [AltStore].[State].[AllSlicer] as 'Aggregate({[AltStore].[State].[All]})'\n"
        + "select {[Time].[Time].[1997].[Q1]} ON COLUMNS,\n"
        + " NON EMPTY TopCount(Product.Product.[Product Name].Members, 2, Measures.[Store Sales]) ON ROWS\n"
        + "from [3StoreHCube]\n"
        + "where ([AltStore].[AltStore].[AllSlicer], [AltStore].[City].[SetSlicer], [AltStore].[State].[AllSlicer])\n";
    String result =
      "Axis #0:\n"
        + "{[AltStore].[AltStore].[AllSlicer], [AltStore].[City].[SetSlicer], [AltStore].[State].[AllSlicer]}\n"
        + "Axis #1:\n"
        + "{[Time].[Time].[1997].[Q1]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Food].[Deli].[Meat].[Bologna].[Red Spade].[Red Spade Low Fat Bologna]}\n"
        + "{[Product].[Product].[Non-Consumable].[Health and Hygiene].[Bathroom Products].[Mouthwash].[Hilltop].[Hilltop Mint "
        + "Mouthwash]}\n"
        + "Row #0: 51.60\n"
        + "Row #1: 28.96\n";
      /*
      class TestMultipleAllWithInExprModifier extends PojoMappingModifier {

          public TestMultipleAllWithInExprModifier(CatalogMapping catalogMapping) {
              super(catalogMapping);
          }

          @Override
          protected List<CubeMapping> cubes(List<? extends CubeMapping> cubes) {
              List<CubeMapping> result = new ArrayList<>();
              result.addAll(super.cubes(cubes));
              result.add(PhysicalCubeMappingImpl.builder()
                  .withName("3StoreHCube")
                  .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.SALES_FACT_1997_TABLE).build())
                  .withDimensionConnectors(List.of(
                      DimensionConnectorMappingImpl.builder()
                      	  .withOverrideDimensionName("AltStore")
                          .withForeignKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_SALES_FACT_1997)
                          .withDimension(StandardDimensionMappingImpl.builder()
                        	  .withName("AltStore")
                        	  .withHierarchies(List.of(
                              ExplicitHierarchyMappingImpl.builder()
                                  .withHasAll(true)
                                  .withPrimaryKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_STORE)
                                  .withAllMemberName("All")
                                  .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.STORE_TABLE).build())
                                  .withLevels(List.of(
                                      LevelMappingImpl.builder()
                                          .withName("Store Name")
                                          .withColumn(FoodmartMappingSupplier.STORE_NAME_COLUMN_IN_STORE)
                                          .withUniqueMembers(false)
                                          .build(),
                                      LevelMappingImpl.builder()
                                          .withName("Store City")
                                          .withColumn(FoodmartMappingSupplier.STORE_CITY_COLUMN_IN_STORE)
                                          .withUniqueMembers(false)
                                          .build()
                                  ))
                                  .build(),
                              ExplicitHierarchyMappingImpl.builder()
                                  .withName("City")
                                  .withHasAll(true)
                                  .withPrimaryKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_STORE)
                                  .withAllMemberName("All")
                                  .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.STORE_TABLE).build())
                                  .withLevels(List.of(
                                      LevelMappingImpl.builder()
                                          .withName("Store City")
                                          .withColumn(FoodmartMappingSupplier.STORE_CITY_COLUMN_IN_STORE)
                                          .withUniqueMembers(false)
                                          .build(),
                                      LevelMappingImpl.builder()
                                          .withName("Store Name")
                                          .withColumn(FoodmartMappingSupplier.STORE_NAME_COLUMN_IN_STORE)
                                          .withUniqueMembers(false)
                                          .build()
                                  ))
                                  .build(),
                              ExplicitHierarchyMappingImpl.builder()
                                  .withName("State")
                                  .withHasAll(true)
                                  .withPrimaryKey(FoodmartMappingSupplier.STORE_ID_COLUMN_IN_STORE)
                                  .withAllMemberName("All")
                                  .withQuery(TableQueryMappingImpl.builder().withTable(FoodmartMappingSupplier.STORE_TABLE).build())
                                  .withLevels(List.of(
                                      LevelMappingImpl.builder()
                                          .withName("Store State")
                                          .withColumn(FoodmartMappingSupplier.STORE_SQFT_COLUMN_IN_STORE)
                                          .withUniqueMembers(false)
                                          .build(),
                                      LevelMappingImpl.builder()
                                          .withName("Store City")
                                          .withColumn(FoodmartMappingSupplier.STORE_CITY_COLUMN_IN_STORE)
                                          .withUniqueMembers(false)
                                          .build(),
                                      LevelMappingImpl.builder()
                                          .withName("Store Name")
                                          .withColumn(FoodmartMappingSupplier.STORE_NAME_COLUMN_IN_STORE)
                                          .withUniqueMembers(false)
                                          .build()
                                  ))
                                  .build()
                          )).build())
                          .build(),
                      DimensionConnectorMappingImpl.builder()
                      	  .withOverrideDimensionName("Time")
                      	  .withDimension(FoodmartMappingSupplier.DIMENSION_TIME)
                          .withForeignKey(FoodmartMappingSupplier.TIME_ID_COLUMN_IN_SALES_FACT_1997)
                          .build(),
                      DimensionConnectorMappingImpl.builder()
                      	  .withOverrideDimensionName("Product")
                          .withDimension(FoodmartMappingSupplier.DIMENSION_PRODUCT)
                          .withForeignKey(FoodmartMappingSupplier.PRODUCT_ID_COLUMN_IN_SALES_FACT_1997)
                          .build()
                  ))
                  .withMeasureGroups(List.of(MeasureGroupMappingImpl.builder()
                		  .withMeasures(List.of(
                              SumMeasureMappingImpl.builder()
                                  .withName("Store Sales")
                                  .withColumn(FoodmartMappingSupplier.STORE_SALES_COLUMN_IN_SALES_FACT_1997)
                                  .withFormatString("#,###.00")
                                  .build()
                		  ))
                		  .build()))
                  .build());
              return result;
          }
      }
      */
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      result );
  }

  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
  void testCompoundSlicerNativeEval(Context<?> context) {
	context.getCatalogCache().clear();
    // MONDRIAN-1404
    final String mdx =
      "select NON EMPTY [Customers].[USA].[CA].[San Francisco].Children ON COLUMNS \n"
        + "from [Sales] \n"
        + "where ([Time].[Time].[1997].[Q1] : [Time].[Time].[1997].[Q3]) \n";

    final String mysql =
      "select\n"
        + "    `customer`.`customer_id` as `c0`,\n"
        + "    CONCAT(`customer`.`fname`, ' ', `customer`.`lname`) as `c1`,\n"
        + "    CONCAT(`customer`.`fname`, ' ', `customer`.`lname`) as `c2`,\n"
        + "    `customer`.`gender` as `c3`,\n"
        + "    `customer`.`marital_status` as `c4`,\n"
        + "    `customer`.`education` as `c5`,\n"
        + "    `customer`.`yearly_income` as `c6`\n"
        + "from\n"
        + "    `sales_fact_1997` as `sales_fact_1997`\n"
        + "    join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
        + "    join `customer` as `customer` on `sales_fact_1997`.`customer_id` = `customer`.`customer_id`\n"
        + "where\n"
        + "    `time_by_day`.`the_year` = 1997\n"
        + "and\n"
        + "    `time_by_day`.`quarter` in ('Q1', 'Q2', 'Q3')\n"
        + "and\n"
        + "    `customer`.`state_province` = 'CA'\n"
        + "and\n"
        + "    `customer`.`city` = 'San Francisco'\n"
        + "and\n"
        + "    (`customer`.`city` = 'San Francisco' and `customer`.`state_province` = 'CA')\n"
        + "group by\n"
        + "    `customer`.`customer_id`,\n"
        + "    CONCAT(`customer`.`fname`, ' ', `customer`.`lname`),\n"
        + "    `customer`.`gender`,\n"
        + "    `customer`.`marital_status`,\n"
        + "    `customer`.`education`,\n"
        + "    `customer`.`yearly_income`\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    ISNULL(`c1`) ASC, `c1` ASC"
        :
        "    ISNULL(CONCAT(`customer`.`fname`, ' ', `customer`.`lname`)) ASC, CONCAT(`customer`.`fname`, ' ', "
          + "`customer`.`lname`) ASC" );
    SqlPattern mysqlPattern =
      new SqlPattern(
        DatabaseProduct.MYSQL,
        mysql,
        mysql );

    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_NON_EMPTY, ConfigConstants.ENABLE_NATIVE_NON_EMPTY_DEFAULT_VALUE, Boolean.class) ) {
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
    }

    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[1997].[Q1]}\n"
        + "{[Time].[Time].[1997].[Q2]}\n"
        + "{[Time].[Time].[1997].[Q3]}\n"
        + "Axis #1:\n"
        + "{[Customers].[Customers].[USA].[CA].[San Francisco].[Dennis Messer]}\n"
        + "{[Customers].[Customers].[USA].[CA].[San Francisco].[Esther Logsdon]}\n"
        + "{[Customers].[Customers].[USA].[CA].[San Francisco].[Karen Moreland]}\n"
        + "{[Customers].[Customers].[USA].[CA].[San Francisco].[Kent Brant]}\n"
        + "{[Customers].[Customers].[USA].[CA].[San Francisco].[Louise Wakefield]}\n"
        + "{[Customers].[Customers].[USA].[CA].[San Francisco].[Reta Mikalas]}\n"
        + "{[Customers].[Customers].[USA].[CA].[San Francisco].[Tammy Mihalek]}\n"
        + "Row #0: 8\n"
        + "Row #0: 3\n"
        + "Row #0: 13\n"
        + "Row #0: 5\n"
        + "Row #0: 13\n"
        + "Row #0: 10\n"
        + "Row #0: 1\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
  void testSnowflakeDimInSlicerBug1407(Context<?> context) {
	context.getCatalogCache().clear();
    // MONDRIAN-1407
    final String mdx =
      "select TopCount([Customers].[Name].members, 5, measures.[unit sales]) ON COLUMNS \n"
        + "  from sales where \n"
        + " { [Time].[1997]} * {[Product].[All Products].[Drink], [Product].[All Products].[Food] }";

    final String mysql =
      "select\n"
        + "    `customer`.`country` as `c0`,\n"
        + "    `customer`.`state_province` as `c1`,\n"
        + "    `customer`.`city` as `c2`,\n"
        + "    `customer`.`customer_id` as `c3`,\n"
        + "    CONCAT(`customer`.`fname`, ' ', `customer`.`lname`) as `c4`,\n"
        + "    CONCAT(`customer`.`fname`, ' ', `customer`.`lname`) as `c5`,\n"
        + "    `customer`.`gender` as `c6`,\n"
        + "    `customer`.`marital_status` as `c7`,\n"
        + "    `customer`.`education` as `c8`,\n"
        + "    `customer`.`yearly_income` as `c9`,\n"
        + "    sum(`sales_fact_1997`.`unit_sales`) as `c10`\n"
        + "from\n"
        + "    `customer` as `customer`\n"
        + "    join `sales_fact_1997` as `sales_fact_1997` on `sales_fact_1997`.`customer_id` = `customer`.`customer_id`\n"
        + "    join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
        + "    join `product` as `product` on `sales_fact_1997`.`product_id` = `product`.`product_id`\n"
        + "    join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id`\n"
        + "where\n"
        + "    `time_by_day`.`the_year` = 1997\n"
        + "and\n"
        + "    `product_class`.`product_family` in ('Drink', 'Food')\n"
        + "group by\n"
        + "    `customer`.`country`,\n"
        + "    `customer`.`state_province`,\n"
        + "    `customer`.`city`,\n"
        + "    `customer`.`customer_id`,\n"
        + "    CONCAT(`customer`.`fname`, ' ', `customer`.`lname`),\n"
        + "    `customer`.`gender`,\n"
        + "    `customer`.`marital_status`,\n"
        + "    `customer`.`education`,\n"
        + "    `customer`.`yearly_income`\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    `c10` DESC,\n"
        + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
        + "    ISNULL(`c4`) ASC, `c4` ASC,\n"
        + "    ISNULL(`c3`) ASC, `c3` ASC"
        : "    sum(`sales_fact_1997`.`unit_sales`) DESC,\n"
        + "    ISNULL(`customer`.`country`) ASC, `customer`.`country` ASC,\n"
        + "    ISNULL(`customer`.`state_province`) ASC, `customer`.`state_province` ASC,\n"
        + "    ISNULL(`customer`.`city`) ASC, `customer`.`city` ASC,\n"
        + "    ISNULL(CONCAT(`customer`.`fname`, ' ', `customer`.`lname`)) ASC, CONCAT(`customer`.`fname`, ' ', "
        + "`customer`.`lname`) ASC,\n"
        + "    ISNULL(`customer`.`customer_id`) ASC, `customer`.`customer_id` ASC");
    SqlPattern mysqlPattern =
      new SqlPattern(
        DatabaseProduct.MYSQL,
        mysql,
        mysql );

    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class) ) {
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
    }

    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[1997], [Product].[Product].[Drink]}\n"
        + "{[Time].[Time].[1997], [Product].[Product].[Food]}\n"
        + "Axis #1:\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Mary Francis Benigar]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[James Horvat]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Wildon Cameron]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Ida Rodriguez]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Joann Mramor]}\n"
        + "Row #0: 427\n"
        + "Row #0: 384\n"
        + "Row #0: 366\n"
        + "Row #0: 357\n"
        + "Row #0: 324\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
  void testCompoundSlicerNonUniqueMemberNames1413(Context<?> context) {
	context.getCatalogCache().clear();
    // MONDRIAN-1413
    final String mdx =
      "select TopCount([Customers].[Customers].[Name].members, 5, "
        + "measures.[unit sales]) ON COLUMNS \n"
        + "  from sales where \n"
        + "  {[Time].[Weekly].[1997].[48].[17] : [Time].[Weekly].[1997].[48].[20]} ";

    final String mysql =
      "select\n"
        + "    `customer`.`country` as `c0`,\n"
        + "    `customer`.`state_province` as `c1`,\n"
        + "    `customer`.`city` as `c2`,\n"
        + "    `customer`.`customer_id` as `c3`,\n"
        + "    CONCAT(`customer`.`fname`, ' ', `customer`.`lname`) as `c4`,\n"
        + "    CONCAT(`customer`.`fname`, ' ', `customer`.`lname`) as `c5`,\n"
        + "    `customer`.`gender` as `c6`,\n"
        + "    `customer`.`marital_status` as `c7`,\n"
        + "    `customer`.`education` as `c8`,\n"
        + "    `customer`.`yearly_income` as `c9`,\n"
        + "    sum(`sales_fact_1997`.`unit_sales`) as `c10`\n"
        + "from\n"
        + "    `customer` as `customer`\n"
        + "    join `sales_fact_1997` as `sales_fact_1997` on `sales_fact_1997`.`customer_id` = `customer`.`customer_id`\n"
        + "    join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
        + "where\n"
        + "    `time_by_day`.`the_year` = 1997\n"
        + "and\n"
        + "    `time_by_day`.`week_of_year` = 48\n"
        + "and\n"
        + "    `time_by_day`.`day_of_month` in (17, 18, 19, 20)\n"
        + "group by\n"
        + "    `customer`.`country`,\n"
        + "    `customer`.`state_province`,\n"
        + "    `customer`.`city`,\n"
        + "    `customer`.`customer_id`,\n"
        + "    CONCAT(`customer`.`fname`, ' ', `customer`.`lname`),\n"
        + "    `customer`.`gender`,\n"
        + "    `customer`.`marital_status`,\n"
        + "    `customer`.`education`,\n"
        + "    `customer`.`yearly_income`\n"
        + "order by\n"
        + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
        ? "    `c10` DESC,\n"
        + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c2`) ASC, `c2` ASC,\n"
        + "    ISNULL(`c4`) ASC, `c4` ASC,\n"
        + "    ISNULL(`c3`) ASC, `c3` ASC"
        : "    sum(`sales_fact_1997`.`unit_sales`) DESC,\n"
        + "    ISNULL(`customer`.`country`) ASC, `customer`.`country` ASC,\n"
        + "    ISNULL(`customer`.`state_province`) ASC, `customer`.`state_province` ASC,\n"
        + "    ISNULL(`customer`.`city`) ASC, `customer`.`city` ASC,\n"
        + "    ISNULL(CONCAT(`customer`.`fname`, ' ', `customer`.`lname`)) ASC, CONCAT(`customer`.`fname`, ' ', "
        + "`customer`.`lname`) ASC,\n"
        + "    ISNULL(`customer`.`customer_id`) ASC, `customer`.`customer_id` ASC");
    SqlPattern mysqlPattern =
      new SqlPattern(
        DatabaseProduct.MYSQL,
        mysql,
        mysql );

    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class) ) {
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
    }

    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Weekly].[1997].[48].[17]}\n"
        + "{[Time].[Weekly].[1997].[48].[18]}\n"
        + "{[Time].[Weekly].[1997].[48].[19]}\n"
        + "{[Time].[Weekly].[1997].[48].[20]}\n"
        + "Axis #1:\n"
        + "{[Customers].[Customers].[USA].[WA].[Yakima].[Joanne Skuderna]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Yakima].[Paula Stevens]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Everett].[Sarah Miller]}\n"
        + "{[Customers].[Customers].[USA].[OR].[Albany].[Kathryn Chamberlin]}\n"
        + "{[Customers].[Customers].[USA].[OR].[Salem].[Scott Pavicich]}\n"
        + "Row #0: 37\n"
        + "Row #0: 32\n"
        + "Row #0: 29\n"
        + "Row #0: 28\n"
        + "Row #0: 28\n" );
  }

  @Test
  void testConstraintCacheIncludesMultiPositionSlicer(Context<?> context) {
    // MONDRIAN-2081
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select non empty [Customers].[USA].[WA].[Spokane].children  on 0, "
        + "Time.[1997].[Q1].[1] * [Store].[USA].[WA].[Spokane] * Gender.F * [Marital Status].M on 1 from sales where\n"
        + "{[Product].[Food].[Snacks].[Candy].[Gum].[Atomic].[Atomic Bubble Gum],\n"
        + "[Product].[Food].[Snacks].[Candy].[Gum].[Choice].[Choice Bubble Gum]}").returnsGrid(
      "Axis #0:\n"
        + "{[Product].[Product].[Food].[Snacks].[Candy].[Gum].[Atomic].[Atomic Bubble Gum]}\n"
        + "{[Product].[Product].[Food].[Snacks].[Candy].[Gum].[Choice].[Choice Bubble Gum]}\n"
        + "Axis #1:\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[David Cocadiz]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Peter Von Breymann]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1997].[Q1].[1], [Store].[Store].[USA].[WA].[Spokane], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "Row #0: 4\n"
        + "Row #0: 3\n" );
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select non empty [Customers].[USA].[WA].[Spokane].children on 0, "
        + "Time.[1997].[Q1].[1] * [Store].[USA].[WA].[Spokane] * Gender.F *"
        + "[Marital Status].M on 1 from sales where "
        + "   { [Product].[Food], [Product].[Drink] }").returnsGrid(
      "Axis #0:\n"
        + "{[Product].[Product].[Food]}\n"
        + "{[Product].[Product].[Drink]}\n"
        + "Axis #1:\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Abbie Carlbon]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Bob Alexander]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Dauna Barton]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[David Cocadiz]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[David Hassard]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Dawn Laner]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Donna Weisinger]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Fran McEvilly]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[James Horvat]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[John Lenorovitz]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Linda Combs]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Luther Moran]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Martha Griego]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Peter Von Breymann]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Richard Callahan]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Robert Vaughn]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Shirley Gottbehuet]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Stanley Marks]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Suzanne Davis]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Takiko Collins]}\n"
        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Virginia Bell]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1997].[Q1].[1], [Store].[Store].[USA].[WA].[Spokane], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "Row #0: 25\n"
        + "Row #0: 17\n"
        + "Row #0: 17\n"
        + "Row #0: 30\n"
        + "Row #0: 16\n"
        + "Row #0: 9\n"
        + "Row #0: 6\n"
        + "Row #0: 12\n"
        + "Row #0: 61\n"
        + "Row #0: 15\n"
        + "Row #0: 20\n"
        + "Row #0: 27\n"
        + "Row #0: 36\n"
        + "Row #0: 22\n"
        + "Row #0: 32\n"
        + "Row #0: 2\n"
        + "Row #0: 30\n"
        + "Row #0: 19\n"
        + "Row #0: 27\n"
        + "Row #0: 3\n"
        + "Row #0: 7\n" );
  }

  /**
   * This is a test for
   * <a href="http://jira.pentaho.com/browse/MONDRIAN-1630">MONDRIAN-1630</a>
   *
   * <p>The baseCube was taken out of the evaluator instead of being passed
   * by the caller, which caused the star column not to be found for the level to evaluate natively as part of the set.
   */
  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, TestNativeVirtualRestrictedSetModifier.class },
  database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testNativeVirtualRestrictedSet(Context<?> context) throws Exception {
      /*
      class TestNativeVirtualRestrictedSetModifier extends PojoMappingModifier {

          public TestNativeVirtualRestrictedSetModifier(CatalogMapping catalogMapping) {
              super(catalogMapping);
          }

          @Override
          protected List<? extends AccessRoleMapping> catalogAccessRoles(CatalogMapping schema) {
              List<AccessRoleMapping> result = new ArrayList<>();
              result.addAll(super.catalogAccessRoles(schema));
              result.add(AccessRoleMappingImpl.builder()
                  .withName("F-MIS-BE-CLIENT")
                  .withAccessCatalogGrants(List.of(
                		AccessCatalogGrantMappingImpl.builder()
                          .withAccess(AccessCatalog.NONE)
                          .withCubeGrant(List.of(
                        	  AccessCubeGrantMappingImpl.builder()
                                  .withCube((CubeMappingImpl) look(FoodmartMappingSupplier.CUBE_VIRTIAL_WAREHOUSE_AND_SALES))
                                  .withAccess(AccessCube.ALL)
                                  .withHierarchyGrants(List.of(
                                		AccessHierarchyGrantMappingImpl.builder()
                                          .withHierarchy((HierarchyMappingImpl) look(FoodmartMappingSupplier.storeHierarchy))
                                          .withRollupPolicyType(RollupPolicyType.PARTIAL)
                                          .withAccess(AccessHierarchy.CUSTOM)
                                          .withMemberGrants(List.of(
                                        	  AccessMemberGrantMappingImpl.builder()
                                                  .withMember("[Store].[All Stores]")
                                                  .withAccess(AccessMember.NONE)
                                                  .build(),
                                              AccessMemberGrantMappingImpl.builder()
                                                  .withMember("[Store].[USA]")
                                                  .withAccess(AccessMember.ALL)
                                                  .build()
                                          ))
                                          .build()
                                  ))
                                  .build()
                          ))
                          .build()
                  ))
                  .build());
              return result;
          }
      }
      */
    Result result = executeQuery(
      "With\n"
        + "Set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Store],[*BASE_MEMBERS_Warehouse])'\n"
        + "Set [*SORTED_ROW_AXIS] as 'Order([*CJ_ROW_AXIS],[Store].CurrentMember.OrderKey,BASC,[Warehouse]"
        + ".CurrentMember.OrderKey,BASC)'\n"
        + "Set [*BASE_MEMBERS_Warehouse] as '[Warehouse].[Country].Members'\n"
        + "Set [*BASE_MEMBERS_Store] as '[Store].[Store Country].Members'\n"
        + "Set [*BASE_MEMBERS_Measures] as '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
        + "Set [*CJ_ROW_AXIS] as 'Generate([*NATIVE_CJ_SET], {([Store].currentMember,[Warehouse].currentMember)})'\n"
        + "Set [*CJ_COL_AXIS] as '[*NATIVE_CJ_SET]'\n"
        + "Member [Measures].[*FORMATTED_MEASURE_0] as '[Measures].[Store Invoice]', FORMAT_STRING = '#,###.00', "
        + "SOLVE_ORDER=400\n"
        + "Select\n"
        + "[*BASE_MEMBERS_Measures] on columns,\n"
        + "Non Empty [*SORTED_ROW_AXIS] on rows\n"
        + "From [Warehouse and Sales]\n", context.getConnection(new ConnectionProps(List.of("F-MIS-BE-CLIENT"))));
    assertNotNull(result);
  }

  @Disabled("disabled for CI build") //disabled for CI build
  @Test
  @RolapConfig(key = ConfigConstants.MAX_CONSTRAINTS, value = "4", type = Integer.class)
  @RolapContextTest(catalog = { CatalogSupplier.class, TestNativeHonorsRoleRestrictionsModifier.class },
  database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testNativeHonorsRoleRestrictions(Context<?> context) {
    // NativeSetEvaluation pushes role restrictions to the where clause
    // (see ContextConstraintWriter.addRoleAccessConstraints) by
    // generating an IN expression based on accessible members.
    // If the number of accessible members in a hierarchy w/ CUSTOM
    // access exceeds MaxConstraints, it is not possible to
    // include the full role restriction in the IN clause.
    // This test verifies only permitted members are returned in this
    // case.

	// test failed because in native mode system returns limit quantity 6 and then filter by role
	// select topcount([Product].[Product Name].members, 6, Measures.[Unit Sales]) on 0 from sales


    String roleDef =
      "  <Role name=\"Test\">\n"
        + "    <SchemaGrant access=\"none\">\n"
        + "      <CubeGrant cube=\"Sales\" access=\"all\">\n"
        + "        <HierarchyGrant hierarchy=\"[Product]\" rollupPolicy=\"partial\" access=\"custom\">\n"
        + "          <MemberGrant member=\"[Product].[Non-Consumable].[Household].[Electrical].[Batteries]"
        + ".[Cormorant].[Cormorant AA-Size Batteries]\" access=\"all\" />\n"
        + "          <MemberGrant member=\"[Product].[Non-Consumable].[Household].[Electrical].[Batteries]"
        + ".[Cormorant].[Cormorant AA-Size Batteries]\" access=\"all\"/>\n"
        + "          <MemberGrant member=\"[Product].[Non-Consumable].[Household].[Electrical].[Batteries]"
        + ".[Cormorant].[Cormorant AAA-Size Batteries]\" access=\"all\"/>\n"
        + "          <MemberGrant member=\"[Product].[Non-Consumable].[Household].[Electrical].[Batteries]"
        + ".[Cormorant].[Cormorant C-Size Batteries]\" access=\"all\"/>\n"
        + "          <MemberGrant member=\"[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Denny]"
        + ".[Denny AA-Size Batteries]\" access=\"all\"/>\n"
        + "          <MemberGrant member=\"[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Denny]"
        + ".[Denny AAA-Size Batteries]\" access=\"all\"/>\n"
        + "        </HierarchyGrant>\n"
        + "      </CubeGrant>\n"
        + "    </SchemaGrant>\n"
        + "  </Role>";
    // The following queries should not include [Denny C-Size Batteries] or
    // [Denny D-Size Batteries]
      /*
      class TestNativeHonorsRoleRestrictionsModifier extends PojoMappingModifier {

          public TestNativeHonorsRoleRestrictionsModifier(CatalogMapping catalogMapping) {
              super(catalogMapping);
          }

          protected List<? extends AccessRoleMapping> catalogAccessRoles(CatalogMapping schema) {
              List<AccessRoleMapping> result = new ArrayList<>();
              result.addAll(super.catalogAccessRoles(schema));
              result.add(AccessRoleMappingImpl.builder()
                  .withName("Test")
                  .withAccessCatalogGrants(List.of(
                	 AccessCatalogGrantMappingImpl.builder()
                          .withAccess(AccessCatalog.NONE)
                          .withCubeGrant(List.of(
                        	 AccessCubeGrantMappingImpl.builder()
                                  .withCube((CubeMappingImpl) look(FoodmartMappingSupplier.CUBE_SALES))
                                  .withAccess(AccessCube.ALL)
                                  .withHierarchyGrants(List.of(
                                	 AccessHierarchyGrantMappingImpl.builder()
                                          .withHierarchy((HierarchyMappingImpl) look(FoodmartMappingSupplier.HIERARCHY_PRODUCT))
                                          .withRollupPolicyType(RollupPolicyType.PARTIAL)
                                          .withAccess(AccessHierarchy.CUSTOM)
                                          .withMemberGrants(List.of(
                                        	  AccessMemberGrantMappingImpl.builder()
                                                  .withMember("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant AA-Size Batteries]")
                                                  .withAccess(AccessMember.ALL)
                                                  .build(),
                                              AccessMemberGrantMappingImpl.builder()
                                                  .withMember("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant AA-Size Batteries]")
                                                  .withAccess(AccessMember.ALL)
                                                  .build(),
                                              AccessMemberGrantMappingImpl.builder()
                                                  .withMember("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant AAA-Size Batteries]")
                                                  .withAccess(AccessMember.ALL)
                                                  .build(),
                                              AccessMemberGrantMappingImpl.builder()
                                                  .withMember("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant C-Size Batteries]")
                                                  .withAccess(AccessMember.ALL)
                                                  .build(),
                                              AccessMemberGrantMappingImpl.builder()
                                                  .withMember("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Denny].[Denny AA-Size Batteries]")
                                                  .withAccess(AccessMember.ALL)
                                                  .build(),
                                              AccessMemberGrantMappingImpl.builder()
                                                  .withMember("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Denny].[Denny AAA-Size Batteries]")
                                                  .withAccess(AccessMember.ALL)
                                                  .build()
                                          ))
                                          .build()
                                  ))
                                  .build()
                          ))
                          .build()
                  ))
                  .build());
              return result;
          }
      }
      */

      Connection connection = context.getConnection(new ConnectionProps(List.of("Test")));
    assertSameNativeAndNot(connection,
      "select non empty crossjoin([Store].[USA],[Product].[Product Name].members) on 0 from sales",
      "Native crossjoin mismatch");
    assertSameNativeAndNot(connection,
      "select topcount([Product].[Product Name].members, 6, Measures.[Unit Sales]) on 0 from sales",
      "Native topcount mismatch");
    assertSameNativeAndNot(connection,
      "select filter([Product].[Product Name].members, Measures.[Unit Sales] > 0) on 0 from sales",
      "Native native filter mismatch");

  }

  private static boolean isUseAgg(Context<?> context) {
    return
      context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
        && context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class);
  }

  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeFilterWithCompoundSlicer(Context<?> context) {
	context.getCatalogCache().clear();
    String mdx =
      "WITH MEMBER [Measures].[TotalVal] AS 'Aggregate(Filter({[Store].[Store City].members},[Measures].[Unit Sales] "
        + "> 1000))'\n"
        + "SELECT [Measures].[TotalVal] ON 0, [Product].[All Products].Children on 1 \n"
        + "FROM [Sales] WHERE {[Time].[1997].[Q1],[Time].[1997].[Q2]}";
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[1997].[Q1]}\n"
        + "{[Time].[Time].[1997].[Q2]}\n"
        + "Axis #1:\n"
        + "{[Measures].[TotalVal]}\n"
        + "Axis #2:\n"
        + "{[Product].[Product].[Drink]}\n"
        + "{[Product].[Product].[Food]}\n"
        + "{[Product].[Product].[Non-Consumable]}\n"
        + "Row #0: 10,152\n"
        + "Row #1: 90,413\n"
        + "Row #2: 23,813\n" );
    context.getConnectionWithDefaultRole().getCacheControl(null).flushSchemaCache();
    if ( !context.getConfigValue(ConfigConstants.ENABLE_NATIVE_FILTER, ConfigConstants.ENABLE_NATIVE_FILTER_DEFAULT_VALUE, Boolean.class) ) {
      return;
    }
    final String mysql = !isUseAgg(context)
      ? "select\n"
      + "    `store`.`store_country` as `c0`,\n"
      + "    `store`.`store_state` as `c1`,\n"
      + "    `store`.`store_city` as `c2`\n"
      + "from\n"
      + "    `store` as `store` join `sales_fact_1997` as `sales_fact_1997` on `sales_fact_1997`.`store_id` = `store`.`store_id` join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id` join `product` as `product` on `sales_fact_1997`.`product_id` = `product`.`product_id` join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id`\n"
      + "where\n"
      + "    `time_by_day`.`the_year` = 1997\n"
      + "and\n"
      + "    `time_by_day`.`quarter` in ('Q1', 'Q2')\n"
      + "and\n"
      + "    `product_class`.`product_family` = 'Drink'\n"
      + "group by\n"
      + "    `store`.`store_country`,\n"
      + "    `store`.`store_state`,\n"
      + "    `store`.`store_city`\n"
      + "having\n"
      + "    (sum(`sales_fact_1997`.`unit_sales`) > 1000)\n"
      + "order by\n"
      + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
      ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
      + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
      + "    ISNULL(`c2`) ASC, `c2` ASC"
      : "    ISNULL(`store`.`store_country`) ASC, `store`.`store_country` ASC,\n"
      + "    ISNULL(`store`.`store_state`) ASC, `store`.`store_state` ASC,\n"
      + "    ISNULL(`store`.`store_city`) ASC, `store`.`store_city` ASC" )
      : "select\n"
      + "    `store`.`store_country` as `c0`,\n"
      + "    `store`.`store_state` as `c1`,\n"
      + "    `store`.`store_city` as `c2`\n"
      + "from\n"
      + "    `store` as `store` join `agg_c_14_sales_fact_1997` as `agg_c_14_sales_fact_1997` on `agg_c_14_sales_fact_1997`.`store_id` = `store`.`store_id` join `product` as `product` on `agg_c_14_sales_fact_1997`.`product_id` = `product`.`product_id` join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id`\n"
      + "where\n"
      + "    `agg_c_14_sales_fact_1997`.`the_year` = 1997\n"
      + "and\n"
      + "    `agg_c_14_sales_fact_1997`.`quarter` in ('Q1', 'Q2')\n"
      + "and\n"
      + "    `product_class`.`product_family` = 'Drink'\n"
      + "group by\n"
      + "    `store`.`store_country`,\n"
      + "    `store`.`store_state`,\n"
      + "    `store`.`store_city`\n"
      + "having\n"
      + "    (sum(`agg_c_14_sales_fact_1997`.`unit_sales`) > 1000)\n"
      + "order by\n"
      + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
      ? "    ISNULL(`c0`) ASC, `c0` ASC,\n"
      + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
      + "    ISNULL(`c2`) ASC, `c2` ASC"
      : "    ISNULL(`store`.`store_country`) ASC, `store`.`store_country` ASC,\n"
      + "    ISNULL(`store`.`store_state`) ASC, `store`.`store_state` ASC,\n"
      + "    ISNULL(`store`.`store_city`) ASC, `store`.`store_city` ASC" );
    SqlPattern mysqlPattern =
      new SqlPattern( DatabaseProduct.MYSQL, mysql, null );
    SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdx).bypassSchemaCache().expectSql(new SqlPattern[] { mysqlPattern } ).verify();
  }

  /**
   * This test demonstrates complex interaction between member calcs and a compound slicer
   */
  @Test
  void testOverridingCompoundFilter(Context<?> context) {
    String mdx =
      "WITH MEMBER [Gender].[All Gender].[NoSlicer] AS '([Product].[All Products], [Time].[1997])', solve_order=1000\n "
        + "MEMBER [Measures].[TotalVal] AS 'Aggregate(Filter({[Store].[Store City].members},[Measures].[Unit Sales] <"
        + " 2300)), solve_order=900'\n"
        + "SELECT {[Measures].[TotalVal], [Measures].[Unit Sales]} on 0, {[Gender].[All Gender], [Gender].[All "
        + "Gender].[NoSlicer]} on 1 from [Sales]\n"
        + "WHERE {([Product].[Non-Consumable], [Time].[1997].[Q1]),([Product].[Drink], [Time].[1997].[Q2])}";

    //TestContext<?> context = getTestContext().withFreshConnection();
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Product].[Product].[Non-Consumable], [Time].[Time].[1997].[Q1]}\n"
        + "{[Product].[Product].[Drink], [Time].[Time].[1997].[Q2]}\n"
        + "Axis #1:\n"
        + "{[Measures].[TotalVal]}\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Gender].[Gender].[All Gender]}\n"
        + "{[Gender].[Gender].[All Gender].[NoSlicer]}\n"
        + "Row #0: 12,730\n"
        + "Row #0: 18,401\n"
        + "Row #1: 6,557\n"
        + "Row #1: 266,773\n" );

    mdx =
      "WITH MEMBER [Gender].[All Gender].[SomeSlicer] AS '([Product].[All Products])', solve_order=1000\n "
        + "MEMBER [Measures].[TotalVal] AS 'Aggregate(Filter({[Store].[Store City].members},[Measures].[Unit Sales] <"
        + " 2700)), solve_order=900'\n"
        + "SELECT {[Measures].[TotalVal], [Measures].[Unit Sales]} on 0, {[Gender].[All Gender], [Gender].[All "
        + "Gender].[SomeSlicer]} on 1 from [Sales]\n"
        + "WHERE {([Product].[Non-Consumable], [Time].[1997].[Q1]),([Product].[Drink], [Time].[1997].[Q2])}";

    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Product].[Product].[Non-Consumable], [Time].[Time].[1997].[Q1]}\n"
        + "{[Product].[Product].[Drink], [Time].[Time].[1997].[Q2]}\n"
        + "Axis #1:\n"
        + "{[Measures].[TotalVal]}\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Gender].[Gender].[All Gender]}\n"
        + "{[Gender].[Gender].[All Gender].[SomeSlicer]}\n"
        + "Row #0: 15,056\n"
        + "Row #0: 18,401\n"
        + "Row #1: 3,045\n"
        + "Row #1: 128,901\n" );
  }

  @Test
  void testNativeFilterWithCompoundSlicerCJ(Context<?> context) {
    String mdx =
      "WITH MEMBER [Measures].[TotalVal] AS 'Aggregate(Filter( {[Store].[Store City].members},[Measures].[Unit Sales]"
        + " > 1000))'\n"
        + "SELECT [Measures].[TotalVal] ON 0, [Gender].[All Gender].Children on 1 \n"
        + "FROM [Sales]\n"
        + "WHERE CrossJoin({ [Product].[Non-Consumable], [Product].[Drink] }, {[Time].[1997].[Q1],[Time].[1997].[Q2]})";
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Product].[Product].[Non-Consumable], [Time].[Time].[1997].[Q1]}\n"
        + "{[Product].[Product].[Non-Consumable], [Time].[Time].[1997].[Q2]}\n"
        + "{[Product].[Product].[Drink], [Time].[Time].[1997].[Q1]}\n"
        + "{[Product].[Product].[Drink], [Time].[Time].[1997].[Q2]}\n"
        + "Axis #1:\n"
        + "{[Measures].[TotalVal]}\n"
        + "Axis #2:\n"
        + "{[Gender].[Gender].[F]}\n"
        + "{[Gender].[Gender].[M]}\n"
        + "Row #0: 16,729\n"
        + "Row #1: 17,044\n" );
  }

  @Test
  void testFilterWithDiffLevelCompoundSlicer(Context<?> context) {
    // not supported in native, but detected
    // and skipped to regular evaluation
    String mdx =
      "SELECT [Measures].[Unit Sales] ON 0,\n"
        + " Filter({[Store].[Store City].members},[Measures].[Unit Sales] > 10000) on 1 \n"
        + "FROM [Sales] WHERE {[Time].[1997].[Q1], [Time].[1997].[Q2].[4]}";
    assertThatQuery(context.getConnectionWithDefaultRole(),
      mdx).returnsGrid(
      "Axis #0:\n"
        + "{[Time].[Time].[1997].[Q1]}\n"
        + "{[Time].[Time].[1997].[Q2].[4]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Store].[Store].[USA].[OR].[Salem]}\n"
        + "{[Store].[Store].[USA].[WA].[Tacoma]}\n"
        + "Row #0: 14,683\n"
        + "Row #1: 10,950\n" );
  }

  @Test
  void testNativeFilterWithCompoundSlicer2049(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "with member measures.avgQtrs as 'avg( filter( time.quarter.members, measures.[unit sales] < 200))' "
        + "select measures.avgQtrs * gender.members on 0 from sales where head( product.[product name].members, 3)").returnsGrid(
      "Axis #0:\n"
        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good].[Good Imported Beer]}\n"
        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good].[Good Light Beer]}\n"
        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Pearl].[Pearl Imported Beer]}\n"
        + "Axis #1:\n"
        + "{[Measures].[avgQtrs], [Gender].[Gender].[All Gender]}\n"
        + "{[Measures].[avgQtrs], [Gender].[Gender].[F]}\n"
        + "{[Measures].[avgQtrs], [Gender].[Gender].[M]}\n"
        + "Row #0: 111\n"
        + "Row #0: 58\n"
        + "Row #0: 53\n" );
  }

  @Test
  void testNativeFilterTupleCompoundSlicer1861(Context<?> context) {
    // Using a slicer list instead of tuples causes slicers with
    // tuples where not all combinations of their members are present to
    // fail when nativized.
    // MondrianProperties.instance().EnableNativeFilter.set(true);
    assertThatQuery(context.getConnectionWithDefaultRole(),
      "select [Measures].[Unit Sales] on columns, Filter([Time].[1997].Children, [Measures].[Unit Sales] < 12335) on "
        + "rows from [Sales] where {([Product].[Drink],[Store].[USA].[CA]),([Product].[Food],[Store].[USA].[OR])}").returnsGrid(
      "Axis #0:\n"
        + "{[Product].[Product].[Drink], [Store].[Store].[USA].[CA]}\n"
        + "{[Product].[Product].[Food], [Store].[Store].[USA].[OR]}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Time].[Time].[1997].[Q2]}\n"
        + "Row #0: 12,334\n" );
  }

  /**
   * tests if cache associated with Native Sets is flushed.
   *
   * @see <a href="http://jira.pentaho.com/browse/MONDRIAN-2366">Jira issue</a>
   */
  @Test
  @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
  void testNativeSetsCacheClearing(Context<?> context) {
    if ( context.getConfigValue(ConfigConstants.READ_AGGREGATES, ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE ,Boolean.class)
      && context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class) ) {
      return;
    }
    final String mdx =
      "select filter( gender.gender.gender.members, measures.[Unit Sales] > 0) on 0 from sales ";

    final String query = "select\n"
      + "    `customer`.`gender` as `c0`\n"
      + "from\n"
      + "    `customer` as `customer`\n"
      + "    join `sales_fact_1997` as `sales_fact_1997` on `sales_fact_1997`.`customer_id` = `customer`.`customer_id`\n"
      + "    join `time_by_day` as `time_by_day` on `sales_fact_1997`.`time_id` = `time_by_day`.`time_id`\n"
      + "where\n"
      + "    `time_by_day`.`the_year` = 1997\n"
      + "group by\n"
      + "    `customer`.`gender`\n"
      + "having\n"
      + "    (sum(`sales_fact_1997`.`unit_sales`) > 0)\n"
      + "order by\n"
      + ( getDialect(context.getConnectionWithDefaultRole()).requiresOrderByAlias()
      ? "    ISNULL(`c0`) ASC, `c0` ASC"
      : "    ISNULL(`customer`.`gender`) ASC, `customer`.`gender` ASC" );

    SqlPattern mysqlPattern =
      new SqlPattern(
        DatabaseProduct.MYSQL,
        query,
        null );
    Result rest = executeQuery( mdx, context.getConnectionWithDefaultRole());
    RolapCube cube = (RolapCube) rest.getQuery().getCube();
    Connection con = (Connection) rest.getQuery().getConnection();
    CacheControl cacheControl = con.getCacheControl( null );

    for ( Hierarchy hier : cube.getHierarchies() ) {
      if ( hier.hasAll() ) {
        cacheControl.flush(
          cacheControl.createMemberSet( hier.getAllMember(), true ) );
      }
    }
    SqlPattern[] patterns = new SqlPattern[] { mysqlPattern };
    if ( context.getConfigValue(ConfigConstants.ENABLE_NATIVE_FILTER, ConfigConstants.ENABLE_NATIVE_FILTER_DEFAULT_VALUE, Boolean.class) ) {
      SqlAssert.forQuery(context.getConnectionWithDefaultRole(),
        mdx).keepCache().expectSql(patterns).verify();
    }
  }

  /** Verifies native and non-native evaluation agree (native is on by default;
   * see {@link #testNativeFilterWithLargeAggSetInSlicerNonNative}). */
  @Test
  void testNativeFilterWithLargeAggSetInSlicer(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      NATIVE_FILTER_WITH_LARGE_AGG_SET_IN_SLICER_MDX).returnsGrid(
      "Axis #0:\n"
      + "{[Customers].[Customers].[agg]}\n"
      + "Axis #1:\n"
      + "{[Gender].[Gender].[All Gender]}\n"
      + "{[Gender].[Gender].[M]}\n"
      + "Row #0: 266,704\n"
      + "Row #0: 135,215\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "false", type = Boolean.class)
  void testNativeFilterWithLargeAggSetInSlicerNonNative(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      NATIVE_FILTER_WITH_LARGE_AGG_SET_IN_SLICER_MDX).returnsGrid(
      "Axis #0:\n"
      + "{[Customers].[Customers].[agg]}\n"
      + "Axis #1:\n"
      + "{[Gender].[Gender].[All Gender]}\n"
      + "{[Gender].[Gender].[M]}\n"
      + "Row #0: 266,704\n"
      + "Row #0: 135,215\n" );
  }

  private static final String NATIVE_FILTER_WITH_LARGE_AGG_SET_IN_SLICER_MDX =
      "with member customers.agg as "
      + "'Aggregate(Except(Customers.[Name].members,    "
      + "{[Customers].[USA].[OR].[Corvallis].[Judy Doolittle]}    ))' "
      + " select filter(gender.gender.members, measures.[unit sales] >131500)"
      + " on 0 from sales "
      + " where customers.agg";

  /** Verifies native and non-native evaluation agree (native is on by default;
   * see {@link #testNativeFilterWithLargeAggSetInSlicerTwoAggsNonNative}). */
  @Test
  void testNativeFilterWithLargeAggSetInSlicerTwoAggs(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      NATIVE_FILTER_WITH_LARGE_AGG_SET_IN_SLICER_TWO_AGGS_MDX).returnsGrid(
      "Axis #0:\n"
      + "{[Customers].[Customers].[agg], [Store].[Store].[agg]}\n"
      + "Axis #1:\n"
      + "{[Gender].[Gender].[All Gender]}\n"
      + "{[Gender].[Gender].[M]}\n"
      + "Row #0: 266,773\n"
      + "Row #0: 135,215\n" );
  }

  @Test
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "false", type = Boolean.class)
  @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "false", type = Boolean.class)
  void testNativeFilterWithLargeAggSetInSlicerTwoAggsNonNative(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      NATIVE_FILTER_WITH_LARGE_AGG_SET_IN_SLICER_TWO_AGGS_MDX).returnsGrid(
      "Axis #0:\n"
      + "{[Customers].[Customers].[agg], [Store].[Store].[agg]}\n"
      + "Axis #1:\n"
      + "{[Gender].[Gender].[All Gender]}\n"
      + "{[Gender].[Gender].[M]}\n"
      + "Row #0: 266,773\n"
      + "Row #0: 135,215\n" );
  }

  private static final String NATIVE_FILTER_WITH_LARGE_AGG_SET_IN_SLICER_TWO_AGGS_MDX =
      "with \n"
      + "member \n"
      + "[Customers].[agg] as 'Aggregate({[Customers].[Country].Members})'\n"
      + "member \n"
      + "[Store].[agg] as 'Aggregate({[Store].[Store State].Members})'\n"
      + "select Filter([Gender].[Gender].Members, ([Measures].[Unit Sales] > 135000)) ON COLUMNS\n"
      + "from [Sales]\n"
      + "where ([Customers].[agg],[Store].[agg])";

  /**
   * Disabled: this used to compare native vs. non-native evaluation of the
   * same query by toggling the native flags on the shared TestContextImpl at
   * runtime (see the old {@code verifySameNativeAndNot}). Under
   * {@code @RolapContextTest} config is fixed per test via {@code @RolapConfig},
   * so there is no supported way to flip the native flags mid-test, and the
   * result set here (every customer with unit sales > 100 under a compound
   * store/gender aggregate) is far too large to hardcode as a literal expected
   * string the way the other {@code LargeAggSetInSlicer} tests were split.
   */
  @Disabled("needs a runtime native/non-native toggle not available under @RolapContextTest")
  @Test
  @RolapConfig(key = ConfigConstants.MAX_CONSTRAINTS, value = "24", type = Integer.class)
  void testNativeFilterWithLargeAggSetInSlicerCompoundAggregate(Context<?> context) {
    assertThatQuery(context.getConnectionWithDefaultRole(),
      NATIVE_FILTER_WITH_LARGE_AGG_SET_IN_SLICER_COMPOUND_AGGREGATE_MDX).returnsGrid( "CAPTURE_ME");
  }

  private static final String NATIVE_FILTER_WITH_LARGE_AGG_SET_IN_SLICER_COMPOUND_AGGREGATE_MDX =
      "WITH member store.agg as "
      + "'Aggregate(CrossJoin(Store.[Store Name].members, Gender.Members))' "
      + "SELECT filter(customers.[name].members, measures.[unit sales] > 100) on 0 "
      + "FROM sales where store.agg";

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.NativeSetEvaluationTestModifier.class },
  database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testDimensionUsageWithDifferentNameExecutedNatively(Context<?> context) {
    /*
    ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
        "Sales",
        "<DimensionUsage name=\"PurchaseDate\" source=\"Time\" foreignKey=\"time_id\"/>" ));
     */
      String mdx = ""
      + "with member Measures.q1Sales as '([PurchaseDate].[PurchaseDate].[1997].[Q1], Measures.[Unit Sales])'\n"
      + "select NonEmptyCrossjoin([PurchaseDate].[PurchaseDate].[1997].[Q1], Gender.Gender.Gender.members) on 0 \n"
      + "from Sales where Measures.q1Sales";
    Result result = executeQuery(mdx, context.getConnectionWithDefaultRole());

    checkNative(context, mdx, result);
    context.getCatalogCache().clear();
  }

  @Test
  void testDimensionUsageExecutedNatively(Context<?> context) {
    String mdx = ""
      + "with member Measures.q1Sales as '([Time].[Time].[1997].[Q1], Measures.[Unit Sales])'\n"
      + "select NonEmptyCrossjoin( [Time].[Time].[1997].[Q1], Gender.Gender.Gender.members) on 0 \n"
      + "from Sales where Measures.q1Sales";
    Connection connection = context.getConnectionWithDefaultRole();
    Result result = executeQuery(mdx, connection);

    checkNative(context, mdx, result );
  }

  @Test
  void testMondrian2575(Context<?> context) {
    assertQueriesReturnSimilarResults(context.getConnectionWithDefaultRole(),
      String.format(
        "WITH member [Customers].[AggregatePageMembers] AS \n'Aggregate({[Customers].[USA].[CA].[Altadena].[Amy "
          + "Petranoff], [Customers].[USA].[CA].[Altadena].[Arvid Duran]})'\nmember [Measures].[test set] AS "
          + "\n'SetToStr(Filter([Product].[Product Name].Members,[Measures].[Store Sales] > 0))'\nSELECT {[Measures]"
          + ".[test set]} ON COLUMNS,\n{[Product].[All Products], [Product].[All Products].Children} ON ROWS\nFROM "
          + "[Sales]\nWHERE [Customers].[AggregatePageMembers]" ),
      String.format(
        "WITH member [Customers].[AggregatePageMembers] AS \n'Aggregate({[Customers].[USA].[CA].[Altadena].[Arvid "
          + "Duran], [Customers].[USA].[CA].[Altadena].[Amy Petranoff]})'\nmember [Measures].[test set] AS "
          + "\n'SetToStr(Filter([Product].[Product Name].Members,[Measures].[Store Sales] > 0))'\nSELECT {[Measures]"
          + ".[test set]} ON COLUMNS,\n{[Product].[All Products], [Product].[All Products].Children} ON ROWS\nFROM "
          + "[Sales]\nWHERE [Customers].[AggregatePageMembers]" ));
  }

  /**
   * Disabled: the original test deliberately opened the connection (forcing
   * catalog/member load under the default result limit) before lowering
   * resultLimit to 400, specifically so the limit would trip only while
   * evaluating the crossjoin below, not while the catalog is built. Under
   * {@code @RolapContextTest}, {@code @RolapConfig} values are fixed from
   * context construction, so resultLimit=400 is already in effect while the
   * catalog itself is loaded, which now throws
   * ResourceLimitExceededException before the test body even runs.
   */
  @Disabled("resultLimit is fixed before catalog load under @RolapContextTest; can't isolate it to query evaluation")
  @Test
  @RolapConfig(key = ConfigConstants.RESULT_LIMIT, value = "400", type = Integer.class)
  void testResultLimitInNativeCJ(Context<?> context) {
      Connection connection = context.getConnectionWithDefaultRole();
    assertThatAxis(connection, "Sales", "NonEmptyCrossjoin({[Product].[All Products].Children}, "
        + "{ [Customers].[Name].members})").throwsMessage(
      "exceeded limit (400)");
  }

  /**
   * Executes query1 and query2 and Compares the obtained measure values.
   */
  private static void assertQueriesReturnSimilarResults(
      Connection connection, String query1, String query2)
  {
      String resultString1 = toString(Mdx.executeQuery(connection, query1));
      String resultString2 = toString(Mdx.executeQuery(connection, query2));
      assertEquals(measureValues(resultString1), measureValues(resultString2));
  }

  /**
   * Truncates the query result to return only measure values.
   */
  private static String measureValues(String resultString) {
      int index = resultString.indexOf("}");
      return index != -1 ? resultString.substring(index) : resultString;
  }

    /**
     * Converts a {@link Result} to text in traditional format.
     *
     * @param result Query result
     * @return Result as text
     */
    private static String toString(Result result) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        result.print(pw);
        pw.flush();
        return sw.toString();
    }
}
