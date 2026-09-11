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
package org.eclipse.daanse.olap.function.def.set.range;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class RangeFunDefTest {

    @Test
    void testRange(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Time].[1997].[Q1].[2] : [Time].[1997].[Q2].[5]")
            .returns(
            "[Time].[Time].[1997].[Q1].[2]\n"
                + "[Time].[Time].[1997].[Q1].[3]\n"
                + "[Time].[Time].[1997].[Q2].[4]\n"
                + "[Time].[Time].[1997].[Q2].[5]" ); // not parents

        // testcase for bug XXXXX: braces required
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with set [Set1] as '[Product].[Drink]:[Product].[Food]' \n"
                + "\n"
                + "select [Set1] on columns, {[Measures].defaultMember} on rows \n"
                + "\n"
                + "from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Food]}\n"
                + "Axis #2:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Row #0: 24,597\n"
                + "Row #0: 191,940\n" );
    }

    /**
     * tests that a null passed in returns an empty set in range function
     */
    @Test
    void testNullRange(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Time].[1997].[Q1].[2] : NULL")
            .returns( //[Time].[1997].[Q2].[5]
            "" ); // Empty Set
    }

    /**
     * tests that an exception is thrown if both parameters in a range function are null.
     */
    @Test
    void testTwoNullRange(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "NULL : NULL")
            .throwsMessage( "Cannot deduce type of call to function ':'" );
    }

    /**
     * Large dimensions use a different member reader, therefore need to be tested separately.
     */
    @Test
    void testRangeLarge(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Customers].[USA].[CA].[San Francisco] : [Customers].[USA].[WA].[Bellingham]")
            .returns(
            "[Customers].[Customers].[USA].[CA].[San Francisco]\n"
                + "[Customers].[Customers].[USA].[CA].[San Gabriel]\n"
                + "[Customers].[Customers].[USA].[CA].[San Jose]\n"
                + "[Customers].[Customers].[USA].[CA].[Santa Cruz]\n"
                + "[Customers].[Customers].[USA].[CA].[Santa Monica]\n"
                + "[Customers].[Customers].[USA].[CA].[Spring Valley]\n"
                + "[Customers].[Customers].[USA].[CA].[Torrance]\n"
                + "[Customers].[Customers].[USA].[CA].[West Covina]\n"
                + "[Customers].[Customers].[USA].[CA].[Woodland Hills]\n"
                + "[Customers].[Customers].[USA].[OR].[Albany]\n"
                + "[Customers].[Customers].[USA].[OR].[Beaverton]\n"
                + "[Customers].[Customers].[USA].[OR].[Corvallis]\n"
                + "[Customers].[Customers].[USA].[OR].[Lake Oswego]\n"
                + "[Customers].[Customers].[USA].[OR].[Lebanon]\n"
                + "[Customers].[Customers].[USA].[OR].[Milwaukie]\n"
                + "[Customers].[Customers].[USA].[OR].[Oregon City]\n"
                + "[Customers].[Customers].[USA].[OR].[Portland]\n"
                + "[Customers].[Customers].[USA].[OR].[Salem]\n"
                + "[Customers].[Customers].[USA].[OR].[W. Linn]\n"
                + "[Customers].[Customers].[USA].[OR].[Woodburn]\n"
                + "[Customers].[Customers].[USA].[WA].[Anacortes]\n"
                + "[Customers].[Customers].[USA].[WA].[Ballard]\n"
                + "[Customers].[Customers].[USA].[WA].[Bellingham]" );
    }

    @Test
    void testRangeStartEqualsEnd(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Time].[1997].[Q3].[7] : [Time].[1997].[Q3].[7]")
            .returns(
            "[Time].[Time].[1997].[Q3].[7]" );
    }

    @Test
    void testRangeStartEqualsEndLarge(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Customers].[USA].[CA] : [Customers].[USA].[CA]")
            .returns(
            "[Customers].[Customers].[USA].[CA]" );
    }

    @Test
    void testRangeEndBeforeStart(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Time].[1997].[Q3].[7] : [Time].[1997].[Q2].[5]")
            .returns(
            "[Time].[Time].[1997].[Q2].[5]\n"
                + "[Time].[Time].[1997].[Q2].[6]\n"
                + "[Time].[Time].[1997].[Q3].[7]" ); // same as if reversed
    }

    @Test
    void testRangeEndBeforeStartLarge(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Customers].[USA].[WA] : [Customers].[USA].[CA]")
            .returns(
            "[Customers].[Customers].[USA].[CA]\n"
                + "[Customers].[Customers].[USA].[OR]\n"
                + "[Customers].[Customers].[USA].[WA]" );
    }

    @Test
    void testRangeBetweenDifferentLevelsIsError(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Time].[1997].[Q2] : [Time].[1997].[Q2].[5]")
            .throwsMessage( "Members must belong to the same level" );
    }

    @Test
    void testRangeBoundedByAll(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Gender] : [Gender]")
            .returns(
            "[Gender].[Gender].[All Gender]" );
    }

    @Test
    void testRangeBoundedByAllLarge(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Customers].DefaultMember : [Customers]")
            .returns(
            "[Customers].[Customers].[All Customers]" );
    }

    @Test
    void testRangeBoundedByNull(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Gender].[F] : [Gender].[M].NextMember")
            .returns(
            "" );
    }

    @Test
    void testRangeBoundedByNullLarge(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Customers].PrevMember : [Customers].[USA].[OR]")
            .returns(
            "" );
    }

    @Test
    void testComplexSlicerWith_Calc(Context<?> context) {
        String query =
            "with "
                + "member [Time].[Time].[H1 1997] as 'Aggregate([Time].[Time].[1997].[Q1] : [Time].[Time].[1997].[Q2])', $member_scope = \"CUBE\","
                + " MEMBER_ORDINAL = 6 "
                + "SELECT "
                + "{[Measures].[Customer Count]} ON 0, "
                + "{[Education Level].Members} ON 1 "
                + "FROM [Sales] "
                + "WHERE {[Time].[Time].[H1 1997]}";
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
        assertThatQuery(context.getConnectionWithDefaultRole(), query)
            .returnsGrid( expectedResult );
    }

    @Test
    void testComplexSlicerWith_CalcBase(Context<?> context) {
        String query =
            "with "
                + "member [Time].[Time].[H1 1997] as 'Aggregate([Time].[Time].[1997].[Q1] : [Time].[Time].[1997].[Q2])', $member_scope = \"CUBE\","
                + " MEMBER_ORDINAL = 6 "
                + "SELECT "
                + "{[Measures].[Customer Count]} ON 0, "
                + "{[Education Level].[Education Level].Members} ON 1 "
                + "FROM [Sales] "
                + "WHERE {[Time].[Time].[H1 1997],[Time].[Time].[1998].[Q1]}";
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
        assertThatQuery(context.getConnectionWithDefaultRole(), query)
            .returnsGrid( expectedResult );
    }

    @Test
    void testComplexSlicerWith_Calc_Calc(Context<?> context) {
        String query =
            "with "
                + "member [Time].[Time].[H1 1997] as 'Aggregate([Time].[Time].[1997].[Q1] : [Time].[Time].[1997].[Q2])', $member_scope = \"CUBE\","
                + " MEMBER_ORDINAL = 6 "
                + "member [Education Level].[Partial] as 'Aggregate([Education Level].[Education Level].[Partial College]:[Education Level].[Education Level]"
                + ".[Partial High School])', $member_scope = \"CUBE\", MEMBER_ORDINAL = 7 "
                + "SELECT "
                + "{[Measures].[Customer Count]} ON 0 "
                + "FROM [Sales] "
                + "WHERE ([Time].[Time].[H1 1997],[Education Level].[Education Level].[Partial])";
        String expectedResult =
            "Axis #0:\n"
                + "{[Time].[Time].[H1 1997], [Education Level].[Education Level].[Partial]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Customer Count]}\n"
                + "Row #0: 1,671\n";
        assertThatQuery(context.getConnectionWithDefaultRole(), query)
            .returnsGrid( expectedResult );
    }

}
