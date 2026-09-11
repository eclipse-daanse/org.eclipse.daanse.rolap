/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara.  All rights reserved.
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

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * Tests for UnionFunDef
 *
 * @author Yury Bakhmutski
 */
@RolapContextTest(FoodmartTestInstance.class)
class UnionFunDefTest {

  @Test
  void testArity4TupleUnion(Context<?> context) {
    String tupleSet =
        "CrossJoin( [Customers].[USA].Children,"
        + " CrossJoin( Time.[1997].children, { (Gender.F, [Marital Status].M ) }) ) ";
    String expected =
        "{[Customers].[Customers].[USA].[CA], [Time].[Time].[1997].[Q1], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[CA], [Time].[Time].[1997].[Q2], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[CA], [Time].[Time].[1997].[Q3], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[CA], [Time].[Time].[1997].[Q4], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[OR], [Time].[Time].[1997].[Q1], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[OR], [Time].[Time].[1997].[Q2], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[OR], [Time].[Time].[1997].[Q3], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[OR], [Time].[Time].[1997].[Q4], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[WA], [Time].[Time].[1997].[Q1], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[WA], [Time].[Time].[1997].[Q2], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[WA], [Time].[Time].[1997].[Q3], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[USA].[WA], [Time].[Time].[1997].[Q4], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}";

    assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Union( " + tupleSet + ", " + tupleSet + ")").returns(expected);
  }

  @Test
  void testArity5TupleUnion(Context<?> context) {
    String tupleSet = "CrossJoin( [Customers].[Canada].Children, "
        + "CrossJoin( [Time].[1997].lastChild, "
        + "CrossJoin ([Education Level].children,{ (Gender.F, [Marital Status].M ) })) )";
    String expected =
        "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q4], [Education Level].[Education Level].[Bachelors Degree], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q4], [Education Level].[Education Level].[Graduate Degree], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q4], [Education Level].[Education Level].[High School Degree], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q4], [Education Level].[Education Level].[Partial College], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q4], [Education Level].[Education Level].[Partial High School], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}";
    Connection connection = context.getConnectionWithDefaultRole();
    assertThatAxis(connection, "Sales", tupleSet).returns(expected);

    assertThatAxis(connection, "Sales", "Union( " + tupleSet + ", " + tupleSet + ")").returns(expected);
  }

  void testArity5TupleUnionAll(Context<?> context) {
    String tupleSet = "CrossJoin( [Customers].[Canada].Children, "
        + "CrossJoin( [Time].[1998].firstChild, "
        + "CrossJoin ([Education Level].members,{ (Gender.F, [Marital Status].M ) })) )";
    String expected =
        "{[Customers].[Canada].[BC], [Time].[1998].[Q1], [Education Level].[All Education Levels], [Gender].[F], [Marital Status].[M]}\n"
        + "{[Customers].[Canada].[BC], [Time].[1998].[Q1], [Education Level].[Bachelors Degree], [Gender].[F], [Marital Status].[M]}\n"
        + "{[Customers].[Canada].[BC], [Time].[1998].[Q1], [Education Level].[Graduate Degree], [Gender].[F], [Marital Status].[M]}\n"
        + "{[Customers].[Canada].[BC], [Time].[1998].[Q1], [Education Level].[High School Degree], [Gender].[F], [Marital Status].[M]}\n"
        + "{[Customers].[Canada].[BC], [Time].[1998].[Q1], [Education Level].[Partial College], [Gender].[F], [Marital Status].[M]}\n"
        + "{[Customers].[Canada].[BC], [Time].[1998].[Q1], [Education Level].[Partial High School], [Gender].[F], [Marital Status].[M]}";

    Connection connection = context.getConnectionWithDefaultRole();
    assertThatAxis(connection, "Sales", tupleSet).returns(expected);

    assertThatAxis(connection, "Sales",
        "Union( " + tupleSet + ", " + tupleSet + ", " + "ALL" + ")").returns(
        expected + "\n" + expected);
  }

  @Test
  void testArity6TupleUnion(Context<?> context) {
    String tupleSet1 = "CrossJoin( [Customers].[Canada].Children, "
        + "CrossJoin( [Time].[1997].firstChild, "
        + "CrossJoin ([Education Level].lastChild,"
        + "CrossJoin ([Yearly Income].lastChild,"
        + "{ (Gender.F, [Marital Status].M ) })) ) )";
    String tupleSet2 = "CrossJoin( [Customers].[Canada].Children, "
        + "CrossJoin( [Time].[1997].firstChild, "
        + "CrossJoin ([Education Level].lastChild,"
        + "CrossJoin ([Yearly Income].children,"
        + "{ (Gender.F, [Marital Status].M ) })) ) )";

    String tupleSet1Expected =
        "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$90K - $110K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}";
    Connection connection = context.getConnectionWithDefaultRole();
    assertThatAxis(connection, "Sales", tupleSet1).returns(tupleSet1Expected);

    String tupleSet2Expected =
        "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$10K - $30K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$110K - $130K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$130K - $150K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$150K +], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$30K - $50K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$50K - $70K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$70K - $90K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + tupleSet1Expected;

    assertThatAxis(connection, "Sales", tupleSet2).returns(tupleSet2Expected);

    assertThatAxis(connection, "Sales",
        "Union( " + tupleSet2 + ", " + tupleSet1 + ")").returns(
        tupleSet2Expected);
  }

  @Test
  void testArity6TupleUnionAll(Context<?> context) {
    String tupleSet1 = "CrossJoin( [Customers].[Canada].Children, "
        + "CrossJoin( [Time].[1997].firstChild, "
        + "CrossJoin ([Education Level].lastChild,"
        + "CrossJoin ([Yearly Income].lastChild,"
        + "{ (Gender.F, [Marital Status].M ) })) ) )";
    String tupleSet2 = "CrossJoin( [Customers].[Canada].Children, "
        + "CrossJoin( [Time].[1997].firstChild, "
        + "CrossJoin ([Education Level].lastChild,"
        + "CrossJoin ([Yearly Income].children,"
        + "{ (Gender.F, [Marital Status].M ) })) ) )";

    String tupleSet1Expected =
        "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$90K - $110K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}";
    Connection connection = context.getConnectionWithDefaultRole();
    assertThatAxis(connection, "Sales", tupleSet1).returns(tupleSet1Expected);

    String tupleSet2Expected =
        "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$10K - $30K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$110K - $130K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$130K - $150K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$150K +], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$30K - $50K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$50K - $70K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + "{[Customers].[Customers].[Canada].[BC], [Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School], [Yearly Income].[Yearly Income].[$70K - $90K], [Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
        + tupleSet1Expected;
    assertThatAxis(connection, "Sales", tupleSet2).returns(tupleSet2Expected);

    assertThatAxis(connection, "Sales",
            "Union( " + tupleSet1 + ", " + tupleSet2 + ", " + "ALL" + ")").returns(
        tupleSet1Expected + "\n" + tupleSet2Expected);
  }

}
