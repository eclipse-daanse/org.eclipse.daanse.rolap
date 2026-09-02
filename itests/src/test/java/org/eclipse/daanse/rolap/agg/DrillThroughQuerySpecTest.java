/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
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
package org.eclipse.daanse.rolap.agg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.ResultSet;
import java.util.Optional;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class DrillThroughQuerySpecTest {

  // test that returns correct number of columns
  @Test
  void testMdxQuery(Context<?> foodMartContext) throws Exception {
    String drillThroughMdx = "DRILLTHROUGH WITH "
        + "SET [*NATIVE_CJ_SET_WITH_SLICER] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Product_],[*BASE_MEMBERS__Store Type_])' "
        + "SET [*NATIVE_CJ_SET] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Product].[Product].CURRENTMEMBER)})' "
        + "SET [*BASE_MEMBERS__Store Type_] AS 'FILTER([Store Type].[Store Type].[Store Type].MEMBERS,[Store Type].[Store Type].CURRENTMEMBER "
        + "NOT IN {[Store Type].[Store Type].[All Store Types].[Small Grocery]})' "
        + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Product].[Product].CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Product].[Product]"
        + ".CURRENTMEMBER,[Product].[Product].[Product Family]).ORDERKEY,BASC)' "
        + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[Warehouse Cost]}' "
        + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET_WITH_SLICER], {([Store Type].[Store Type].CURRENTMEMBER)})' "
        + "SET [*BASE_MEMBERS__Product_] AS '[Product].[Product].[Product Department].MEMBERS' "
        + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Product].[Product].CURRENTMEMBER)})' "
        + "SELECT "
        + "FILTER([*BASE_MEMBERS__Measures_],([Measures].CurrentMember Is [Measures].[Warehouse Cost])) ON COLUMNS "
        + ",FILTER([*SORTED_ROW_AXIS],([Product].[Product].CurrentMember Is [Product].[Product].[Drink].[Alcoholic Beverages])) ON ROWS "
        + "FROM [Warehouse] " + "WHERE ([*CJ_SLICER_AXIS]) "
        + "RETURN [Product].[Product].[Product Department]";

    Connection connection = foodMartContext.getConnectionWithDefaultRole();
    ResultSet resultSet = connection.createStatement().executeQuery(drillThroughMdx, Optional.empty(), null);

    assertEquals(1, resultSet.getMetaData().getColumnCount());
    // The drill-through SQL aliases the column with the level's name:
    //   select "product_class"."product_department" as "Product Department" from ...
    // getColumnLabel is that alias by definition. getColumnName is not: JDBC leaves it to the
    // driver whether to look the base column up, so h2 answers "product_department" while
    // postgres and duckdb hand back the alias. Assert the part the generator promises.
    assertEquals
      ("Product Department", resultSet.getMetaData().getColumnLabel(1));
  }

}
