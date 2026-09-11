/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.olap.function.def.crossjoinx;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.text.NumberFormat;
import java.util.Locale;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class CrossJoinFunDefTest {

    @Test
    void testCrossjoinNested(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "  CrossJoin(\n"
                + "    CrossJoin(\n"
                + "      [Gender].members,\n"
                + "      [Marital Status].members),\n"
                + "   {[Store], [Store].children})")
            .returns(
            "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[All Stores]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[Canada]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[Mexico]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[USA]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[M], [Store].[Store].[All Stores]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[M], [Store].[Store].[Canada]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[M], [Store].[Store].[Mexico]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[M], [Store].[Store].[USA]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[S], [Store].[Store].[All Stores]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[S], [Store].[Store].[Canada]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[S], [Store].[Store].[Mexico]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[S], [Store].[Store].[USA]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[All Stores]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[Canada]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[Mexico]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[USA]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M], [Store].[Store].[All Stores]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M], [Store].[Store].[Canada]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M], [Store].[Store].[Mexico]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M], [Store].[Store].[USA]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S], [Store].[Store].[All Stores]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S], [Store].[Store].[Canada]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S], [Store].[Store].[Mexico]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S], [Store].[Store].[USA]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[All Stores]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[Canada]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[Mexico]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[All Marital Status], [Store].[Store].[USA]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M], [Store].[Store].[All Stores]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M], [Store].[Store].[Canada]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M], [Store].[Store].[Mexico]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M], [Store].[Store].[USA]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Store].[Store].[All Stores]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Store].[Store].[Canada]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Store].[Store].[Mexico]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Store].[Store].[USA]}" );
    }

    @Test
    void testCrossjoinSingletonTuples(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "CrossJoin({([Gender].[M])}, {([Marital Status].[S])})")
            .returns("{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S]}" );
    }

    @Test
    void testCrossjoinSingletonTuplesNested(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "CrossJoin({([Gender].[M])}, CrossJoin({([Marital Status].[S])}, [Store].[Store].children))")
            .returns(
            "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Store].[Store].[Canada]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Store].[Store].[Mexico]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Store].[Store].[USA]}" );
    }

    @Test
    void testCrossjoinAsterisk(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Gender].[Gender].[M]} * {[Marital Status].[Marital Status].[S]}")
            .returns("{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S]}" );
    }

    @Test
    void testCrossjoinAsteriskTuple(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} ON COLUMNS, "
                + "NON EMPTY [Store].[All Stores] "
                + " * ([Product].[All Products], [Gender]) "
                + " * [Customers].[All Customers] ON ROWS "
                + "from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[All Stores], [Product].[Product].[All Products], [Gender].[Gender].[All Gender], [Customers].[Customers].[All Customers]}\n"
                + "Row #0: 266,773\n" );
    }

    @Test
    void testCrossjoinAsteriskAssoc(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Order({[Gender].Children} * {[Marital Status].Children} * {[Time].[1997].[Q2].Children},"
                + "[Measures].[Unit Sales])")
            .returns(
            "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M], [Time].[Time].[1997].[Q2].[4]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M], [Time].[Time].[1997].[Q2].[6]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M], [Time].[Time].[1997].[Q2].[5]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S], [Time].[Time].[1997].[Q2].[4]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S], [Time].[Time].[1997].[Q2].[5]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S], [Time].[Time].[1997].[Q2].[6]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M], [Time].[Time].[1997].[Q2].[4]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M], [Time].[Time].[1997].[Q2].[5]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M], [Time].[Time].[1997].[Q2].[6]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Time].[Time].[1997].[Q2].[6]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Time].[Time].[1997].[Q2].[4]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Time].[Time].[1997].[Q2].[5]}" );
    }

    @Test
    void testCrossjoinAsteriskInsideBraces(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Gender].[M] * [Marital Status].[S] * [Time].[1997].[Q2].Children}")
            .returns(
            "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Time].[Time].[1997].[Q2].[4]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Time].[Time].[1997].[Q2].[5]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S], [Time].[Time].[1997].[Q2].[6]}" );
    }

    @Test
    void testAncestors(Context<?> context) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.getDefault());
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "SELECT {[Measures].members * [1997].children} ON COLUMNS,\n"
                + " {[Store].[USA].children * [Position].[All Position].children} DIMENSION PROPERTIES [Store].[Store SQFT] "
                + "ON ROWS\n"
                + "FROM [HR]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Org Salary], [Time].[Time].[1997].[Q1]}\n"
                + "{[Measures].[Org Salary], [Time].[Time].[1997].[Q2]}\n"
                + "{[Measures].[Org Salary], [Time].[Time].[1997].[Q3]}\n"
                + "{[Measures].[Org Salary], [Time].[Time].[1997].[Q4]}\n"
                + "{[Measures].[Count], [Time].[Time].[1997].[Q1]}\n"
                + "{[Measures].[Count], [Time].[Time].[1997].[Q2]}\n"
                + "{[Measures].[Count], [Time].[Time].[1997].[Q3]}\n"
                + "{[Measures].[Count], [Time].[Time].[1997].[Q4]}\n"
                + "{[Measures].[Number of Employees], [Time].[Time].[1997].[Q1]}\n"
                + "{[Measures].[Number of Employees], [Time].[Time].[1997].[Q2]}\n"
                + "{[Measures].[Number of Employees], [Time].[Time].[1997].[Q3]}\n"
                + "{[Measures].[Number of Employees], [Time].[Time].[1997].[Q4]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[USA].[CA], [Position].[Position].[Middle Management]}\n"
                + "{[Store].[Store].[USA].[CA], [Position].[Position].[Senior Management]}\n"
                + "{[Store].[Store].[USA].[CA], [Position].[Position].[Store Full Time Staf]}\n"
                + "{[Store].[Store].[USA].[CA], [Position].[Position].[Store Management]}\n"
                + "{[Store].[Store].[USA].[CA], [Position].[Position].[Store Temp Staff]}\n"
                + "{[Store].[Store].[USA].[OR], [Position].[Position].[Middle Management]}\n"
                + "{[Store].[Store].[USA].[OR], [Position].[Position].[Senior Management]}\n"
                + "{[Store].[Store].[USA].[OR], [Position].[Position].[Store Full Time Staf]}\n"
                + "{[Store].[Store].[USA].[OR], [Position].[Position].[Store Management]}\n"
                + "{[Store].[Store].[USA].[OR], [Position].[Position].[Store Temp Staff]}\n"
                + "{[Store].[Store].[USA].[WA], [Position].[Position].[Middle Management]}\n"
                + "{[Store].[Store].[USA].[WA], [Position].[Position].[Senior Management]}\n"
                + "{[Store].[Store].[USA].[WA], [Position].[Position].[Store Full Time Staf]}\n"
                + "{[Store].[Store].[USA].[WA], [Position].[Position].[Store Management]}\n"
                + "{[Store].[Store].[USA].[WA], [Position].[Position].[Store Temp Staff]}\n"
                + "Row #0: " + format.format(275.40) + "\n"
                + "Row #0: " + format.format(275.40) + "\n"
                + "Row #0: " + format.format(275.40) + "\n"
                + "Row #0: " + format.format(275.40) + "\n"
                + "Row #0: 27\n"
                + "Row #0: 27\n"
                + "Row #0: 27\n"
                + "Row #0: 27\n"
                + "Row #0: 9\n"
                + "Row #0: 9\n"
                + "Row #0: 9\n"
                + "Row #0: 9\n"
                + "Row #1: " + format.format(837.00) + "\n"
                + "Row #1: " + format.format(837.00) + "\n"
                + "Row #1: " + format.format(837.00) + "\n"
                + "Row #1: " + format.format(837.00) + "\n"
                + "Row #1: 24\n"
                + "Row #1: 24\n"
                + "Row #1: 24\n"
                + "Row #1: 24\n"
                + "Row #1: 8\n"
                + "Row #1: 8\n"
                + "Row #1: 8\n"
                + "Row #1: 8\n"
                + "Row #2: " + format.format(1728.45) + "\n"
                + "Row #2: " + format.format(1727.02) + "\n"
                + "Row #2: " + format.format(1727.72) + "\n"
                + "Row #2: " + format.format(1726.55) + "\n"
                + "Row #2: 357\n"
                + "Row #2: 357\n"
                + "Row #2: 357\n"
                + "Row #2: 357\n"
                + "Row #2: 119\n"
                + "Row #2: 119\n"
                + "Row #2: 119\n"
                + "Row #2: 119\n"
                + "Row #3: " + format.format(473.04) + "\n"
                + "Row #3: " + format.format(473.04) + "\n"
                + "Row #3: " + format.format(473.04) + "\n"
                + "Row #3: " + format.format(473.04) + "\n"
                + "Row #3: 51\n"
                + "Row #3: 51\n"
                + "Row #3: 51\n"
                + "Row #3: 51\n"
                + "Row #3: 17\n"
                + "Row #3: 17\n"
                + "Row #3: 17\n"
                + "Row #3: 17\n"
                + "Row #4: " + format.format(401.35) + "\n"
                + "Row #4: " + format.format(405.73) + "\n"
                + "Row #4: " + format.format(400.61) + "\n"
                + "Row #4: " + format.format(402.31) + "\n"
                + "Row #4: 120\n"
                + "Row #4: 120\n"
                + "Row #4: 120\n"
                + "Row #4: 120\n"
                + "Row #4: 40\n"
                + "Row #4: 40\n"
                + "Row #4: 40\n"
                + "Row #4: 40\n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #5: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #6: \n"
                + "Row #7: " + format.format(1343.62) + "\n"
                + "Row #7: " + format.format(1342.61) + "\n"
                + "Row #7: " + format.format(1342.57) + "\n"
                + "Row #7: " + format.format(1343.65) + "\n"
                + "Row #7: 279\n"
                + "Row #7: 279\n"
                + "Row #7: 279\n"
                + "Row #7: 279\n"
                + "Row #7: 93\n"
                + "Row #7: 93\n"
                + "Row #7: 93\n"
                + "Row #7: 93\n"
                + "Row #8: " + format.format(286.74) + "\n"
                + "Row #8: " + format.format(286.74) + "\n"
                + "Row #8: " + format.format(286.74) + "\n"
                + "Row #8: " + format.format(286.74) + "\n"
                + "Row #8: 30\n"
                + "Row #8: 30\n"
                + "Row #8: 30\n"
                + "Row #8: 30\n"
                + "Row #8: 10\n"
                + "Row #8: 10\n"
                + "Row #8: 10\n"
                + "Row #8: 10\n"
                + "Row #9: " + format.format(333.20) + "\n"
                + "Row #9: " + format.format(332.65) + "\n"
                + "Row #9: " + format.format(331.28) + "\n"
                + "Row #9: " + format.format(332.43) + "\n"
                + "Row #9: 99\n"
                + "Row #9: 99\n"
                + "Row #9: 99\n"
                + "Row #9: 99\n"
                + "Row #9: 33\n"
                + "Row #9: 33\n"
                + "Row #9: 33\n"
                + "Row #9: 33\n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #10: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #11: \n"
                + "Row #12: " + format.format(2768.60) + "\n"
                + "Row #12: " + format.format(2769.18) + "\n"
                + "Row #12: " + format.format(2766.78) + "\n"
                + "Row #12: " + format.format(2769.50) + "\n"
                + "Row #12: 579\n"
                + "Row #12: 579\n"
                + "Row #12: 579\n"
                + "Row #12: 579\n"
                + "Row #12: 193\n"
                + "Row #12: 193\n"
                + "Row #12: 193\n"
                + "Row #12: 193\n"
                + "Row #13: " + format.format(736.29) + "\n"
                + "Row #13: " + format.format(736.29) + "\n"
                + "Row #13: " + format.format(736.29) + "\n"
                + "Row #13: " + format.format(736.29) + "\n"
                + "Row #13: 81\n"
                + "Row #13: 81\n"
                + "Row #13: 81\n"
                + "Row #13: 81\n"
                + "Row #13: 27\n"
                + "Row #13: 27\n"
                + "Row #13: 27\n"
                + "Row #13: 27\n"
                + "Row #14: " + format.format(674.70) + "\n"
                + "Row #14: " + format.format(674.54) + "\n"
                + "Row #14: " + format.format(676.26) + "\n"
                + "Row #14: " + format.format(676.48) + "\n"
                + "Row #14: 201\n"
                + "Row #14: 201\n"
                + "Row #14: 201\n"
                + "Row #14: 201\n"
                + "Row #14: 67\n"
                + "Row #14: 67\n"
                + "Row #14: 67\n"
                + "Row #14: 67\n" );
    }

    /**
     * Testcase for bug 1889745, "StackOverflowError while resolving crossjoin". The problem occurs when a calculated
     * member that references itself is referenced in a crossjoin.
     */
    @Test
    void testCrossjoinResolve(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with\n"
                + "member [Measures].[Filtered Unit Sales] as\n"
                + " 'IIf((([Measures].[Unit Sales] > 50000.0)\n"
                + "      OR ([Product].[Product].CurrentMember.Level.UniqueName <>\n"
                + "          \"[Product].[Product].[Product Family]\")),\n"
                + "      IIf(((Count([Product].[Product].CurrentMember.Children) = 0.0)),\n"
                + "          [Measures].[Unit Sales],\n"
                + "          Sum([Product].[Product].CurrentMember.Children,\n"
                + "              [Measures].[Filtered Unit Sales])),\n"
                + "      NULL)'\n"
                + "select NON EMPTY {crossjoin({[Measures].[Filtered Unit Sales]},\n"
                + "{[Gender].[Gender].[M], [Gender].[Gender].[F]})} ON COLUMNS,\n"
                + "NON EMPTY {[Product].[Product].[All Products]} ON ROWS\n"
                + "from [Sales]\n"
                + "where [Time].[Time].[1997]")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Time].[Time].[1997]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Filtered Unit Sales], [Gender].[Gender].[M]}\n"
                + "{[Measures].[Filtered Unit Sales], [Gender].[Gender].[F]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[All Products]}\n"
                + "Row #0: 97,126\n"
                + "Row #0: 94,814\n" );
    }

    /**
     * Test case for bug 1911832, "Exception converting immutable list to array in JDK 1.5".
     */
    @Test
    void testCrossjoinOrder(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH\n"
                + "\n"
                + "SET [S1] AS 'CROSSJOIN({[Time].[Time].[1997]}, {[Gender].[Gender].[Gender].MEMBERS})'\n"
                + "\n"
                + "SELECT CROSSJOIN(ORDER([S1], [Measures].[Unit Sales], BDESC),\n"
                + "{[Measures].[Unit Sales]}) ON AXIS(0)\n"
                + "FROM [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1997], [Gender].[Gender].[M], [Measures].[Unit Sales]}\n"
                + "{[Time].[Time].[1997], [Gender].[Gender].[F], [Measures].[Unit Sales]}\n"
                + "Row #0: 135,215\n"
                + "Row #0: 131,558\n" );
    }

    @Test
    void testCrossjoinDupHierarchyFails(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select [Measures].[Unit Sales] ON COLUMNS,\n"
                + " CrossJoin({[Time].[Quarter].[Q1]}, {[Time].[Month].[5]}) ON ROWS\n"
                + "from [Sales]")
            .throwsMessage("Tuple contains more than one member of hierarchy '[Time].[Time]'." );

        // now with Item, for kicks
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select [Measures].[Unit Sales] ON COLUMNS,\n"
                + " CrossJoin({[Time].[Quarter].[Q1]}, {[Time].[Month].[5]}).Item(0) ON ROWS\n"
                + "from [Sales]")
            .throwsMessage("Tuple contains more than one member of hierarchy '[Time].[Time]'." );

        // same query using explicit tuple
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select [Measures].[Unit Sales] ON COLUMNS,\n"
                + " ([Time].[Quarter].[Q1], [Time].[Month].[5]) ON ROWS\n"
                + "from [Sales]")
            .throwsMessage("Tuple contains more than one member of hierarchy '[Time].[Time]'." );
    }

    /**
     *
     * Not an error.
     */
    //* Tests cases of different hierarchies in the same dimension. (Compare to {@link #testCrossjoinDupHierarchyFails()}).
    @Test
    void testCrossjoinDupDimensionOk(Context<?> context) {
        final String expectedResult =
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1997].[Q1], [Time].[Weekly].[1997].[10]}\n"
                + "Row #0: 4,395\n";
        final String timeWeekly = "[Time].[Weekly]";
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select [Measures].[Unit Sales] ON COLUMNS,\n"
                + " CrossJoin({[Time].[Quarter].[Q1]}, {"
                + timeWeekly + ".[1997].[10]}) ON ROWS\n"
                + "from [Sales]")
            .returnsGrid(expectedResult );

        // now with Item, for kicks
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select [Measures].[Unit Sales] ON COLUMNS,\n"
                + " CrossJoin({[Time].[Quarter].[Q1]}, {"
                + timeWeekly + ".[1997].[10]}).Item(0) ON ROWS\n"
                + "from [Sales]")
            .returnsGrid(expectedResult );

        // same query using explicit tuple
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select [Measures].[Unit Sales] ON COLUMNS,\n"
                + " ([Time].[Quarter].[Q1], "
                + timeWeekly + ".[1997].[10]) ON ROWS\n"
                + "from [Sales]")
            .returnsGrid(expectedResult );
    }


    @Test
    void testItemTuple(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(),
            "Sales",
            "CrossJoin([Gender].[All Gender].children, "
                + "[Time].[1997].[Q2].children).Item(0).Item(1).UniqueName")
            .returns("[Time].[Time].[1997].[Q2].[4]" );
    }
}
