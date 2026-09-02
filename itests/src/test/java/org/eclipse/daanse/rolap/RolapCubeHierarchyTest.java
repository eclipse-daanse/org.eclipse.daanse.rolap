/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2001-2005 Julian Hyde
// Copyright (C) 2005-2017 Hitachi Vantara and others
// All Rights Reserved.
*/
package org.eclipse.daanse.rolap;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class RolapCubeHierarchyTest {

  @Test
  void testMONDRIAN2535(Context<?> context) {
      assertThatQuery(context.getConnectionWithDefaultRole(),
        "Select\n"
        + "  [Customers].children on rows,\n"
        + "  [Gender].children on columns\n "
        + "From [Warehouse and Sales]").returnsGrid(
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Gender].[Gender].[F]}\n"
        + "{[Gender].[Gender].[M]}\n"
        + "Axis #2:\n"
        + "{[Customers].[Customers].[Canada]}\n"
        + "{[Customers].[Customers].[Mexico]}\n"
        + "{[Customers].[Customers].[USA]}\n"
        + "Row #0: \n"
        + "Row #0: \n"
        + "Row #1: \n"
        + "Row #1: \n"
        + "Row #2: 280,226.21\n"
        + "Row #2: 285,011.92\n");
  }
}
