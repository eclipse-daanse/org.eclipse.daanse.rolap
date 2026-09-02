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
package org.eclipse.daanse.olap.function.def.openingclosingperiod;

import static org.eclipse.daanse.olap.function.TestResources.hiersExcept;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatMemberExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class OpeningClosingPeriodFunDefTest {

    @Test
    void testClosingPeriodNoArgs(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatMemberExpr(connection, "Sales",
            "ClosingPeriod()").dependsOn( "[Time].[Time]" );
        // MSOLAP returns [1997].[Q4], because [Time].CurrentMember =
        // [1997].
        assertThatAxis(connection, "Sales", "ClosingPeriod()").returns( "[Time].[Time].[1997].[Q4]" );
    }

    @Test
    void testClosingPeriodLevel(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatMemberExpr(connection, "Sales",
            "ClosingPeriod([Time].[Year])").dependsOn( "[Time].[Time]" );
        assertThatMemberExpr(connection, "Sales",
            "([Measures].[Unit Sales], ClosingPeriod([Time].[Month]))")
            .dependsOn( "[Time].[Time]" );

        assertThatAxis(connection, "Sales", "ClosingPeriod([Year])").returns( "[Time].[Time].[1997]" );

        assertThatAxis(connection, "Sales", "ClosingPeriod([Quarter])").returns( "[Time].[Time].[1997].[Q4]" );

        assertThatAxis(connection, "Sales", "ClosingPeriod([Month])").returns( "[Time].[Time].[1997].[Q4].[12]" );

        assertThatQuery(connection,
            "with member [Measures].[Closing Unit Sales] as "
                + "'([Measures].[Unit Sales], ClosingPeriod([Time].[Month]))'\n"
                + "select non empty {[Measures].[Closing Unit Sales]} on columns,\n"
                + " {Descendants([Time].[1997])} on rows\n"
                + "from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Closing Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1997]}\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "{[Time].[Time].[1997].[Q1].[1]}\n"
                + "{[Time].[Time].[1997].[Q1].[2]}\n"
                + "{[Time].[Time].[1997].[Q1].[3]}\n"
                + "{[Time].[Time].[1997].[Q2]}\n"
                + "{[Time].[Time].[1997].[Q2].[4]}\n"
                + "{[Time].[Time].[1997].[Q2].[5]}\n"
                + "{[Time].[Time].[1997].[Q2].[6]}\n"
                + "{[Time].[Time].[1997].[Q3]}\n"
                + "{[Time].[Time].[1997].[Q3].[7]}\n"
                + "{[Time].[Time].[1997].[Q3].[8]}\n"
                + "{[Time].[Time].[1997].[Q3].[9]}\n"
                + "{[Time].[Time].[1997].[Q4]}\n"
                + "{[Time].[Time].[1997].[Q4].[10]}\n"
                + "{[Time].[Time].[1997].[Q4].[11]}\n"
                + "{[Time].[Time].[1997].[Q4].[12]}\n"
                + "Row #0: 26,796\n"
                + "Row #1: 23,706\n"
                + "Row #2: 21,628\n"
                + "Row #3: 20,957\n"
                + "Row #4: 23,706\n"
                + "Row #5: 21,350\n"
                + "Row #6: 20,179\n"
                + "Row #7: 21,081\n"
                + "Row #8: 21,350\n"
                + "Row #9: 20,388\n"
                + "Row #10: 23,763\n"
                + "Row #11: 21,697\n"
                + "Row #12: 20,388\n"
                + "Row #13: 26,796\n"
                + "Row #14: 19,958\n"
                + "Row #15: 25,270\n"
                + "Row #16: 26,796\n" );

        assertThatQuery(connection,
            "with member [Measures].[Closing Unit Sales] as '([Measures].[Unit Sales], ClosingPeriod([Time].[Month]))'\n"
                + "select {[Measures].[Unit Sales], [Measures].[Closing Unit Sales]} on columns,\n"
                + " {[Time].[1997], [Time].[1997].[Q1], [Time].[1997].[Q1].[1], [Time].[1997].[Q1].[3], [Time].[1997].[Q4]"
                + ".[12]} on rows\n"
                + "from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Closing Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1997]}\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "{[Time].[Time].[1997].[Q1].[1]}\n"
                + "{[Time].[Time].[1997].[Q1].[3]}\n"
                + "{[Time].[Time].[1997].[Q4].[12]}\n"
                + "Row #0: 266,773\n"
                + "Row #0: 26,796\n"
                + "Row #1: 66,291\n"
                + "Row #1: 23,706\n"
                + "Row #2: 21,628\n"
                + "Row #2: 21,628\n"
                + "Row #3: 23,706\n"
                + "Row #3: 23,706\n"
                + "Row #4: 26,796\n"
                + "Row #4: 26,796\n" );
    }

    @Test
    void testClosingPeriodLevelNotInTimeFails(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "ClosingPeriod([Store].[Store City])")
            .throwsMessage( "The <level> and <member> arguments to ClosingPeriod must be from "
                + "the same hierarchy. The level was from '[Store]' but the member "
                + "was from '[Time]'" );
    }

    @Test
    void testClosingPeriodMember(Context<?> context) {
        if ( false ) {
            // This test is mistaken. Valid forms are ClosingPeriod(<level>)
            // and ClosingPeriod(<level>, <member>), but not
            // ClosingPeriod(<member>)
            assertThatAxis( context.getConnectionWithDefaultRole(), "Sales", "ClosingPeriod([USA])" ).returns( "[Store].[Store].[USA].[WA]" );
        }
    }

    @Test
    void testClosingPeriodMemberLeaf(Context<?> context) {
        if ( false ) {
            // This test is mistaken. Valid forms are ClosingPeriod(<level>)
            // and ClosingPeriod(<level>, <member>), but not
            // ClosingPeriod(<member>)
            assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
                "ClosingPeriod([Time].[1997].[Q3].[8])").returns( "" );
        }
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member [Measures].[Foo] as ClosingPeriod().uniquename\n"
                + "select {[Measures].[Foo]} on columns,\n"
                + "  {[Time].[1997],\n"
                + "   [Time].[1997].[Q2],\n"
                + "   [Time].[1997].[Q2].[4]} on rows\n"
                + "from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Foo]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1997]}\n"
                + "{[Time].[Time].[1997].[Q2]}\n"
                + "{[Time].[Time].[1997].[Q2].[4]}\n"
                + "Row #0: [Time].[Time].[1997].[Q4]\n"
                + "Row #1: [Time].[Time].[1997].[Q2].[6]\n"
                + "Row #2: [Time].[Time].[#null]\n"
                // MSAS returns "" here.
                + "" );
    }

    @Test
    void testClosingPeriod(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatMemberExpr(connection, "Sales",
            "ClosingPeriod([Time].[Month], [Time].[Time].CurrentMember)")
            .dependsOn( "[Time].[Time]" );

        assertThatExpr(connection, "Sales",
            "(([Measures].[Store Sales],"
                + " ClosingPeriod([Time].[Month], [Time].[Time].CurrentMember)) - "
                + "([Measures].[Store Cost],"
                + " ClosingPeriod([Time].[Time].[Month], [Time].[Time].CurrentMember)))")
            .dependsOn( hiersExcept( "[Measures]" ) );

        assertThatMemberExpr(connection, "Sales",
            "ClosingPeriod([Time].[Month], [Time].[1997].[Q3])").dependsOn();

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Time].[Year], [Time].[1997].[Q3])").returns( "" );

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Time].[Quarter], [Time].[1997].[Q3])")
            .returns( "[Time].[Time].[1997].[Q3]" );

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Time].[Month], [Time].[1997].[Q3])")
            .returns( "[Time].[Time].[1997].[Q3].[9]" );

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Time].[Quarter], [Time].[1997])")
            .returns( "[Time].[Time].[1997].[Q4]" );

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Time].[Year], [Time].[1997])").returns( "[Time].[Time].[1997]" );

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Time].[Month], [Time].[1997])")
            .returns( "[Time].[Time].[1997].[Q4].[12]" );

        // leaf member

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Time].[Year], [Time].[1997].[Q3].[8])").returns( "" );

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Time].[Quarter], [Time].[1997].[Q3].[8])").returns( "" );

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Time].[Month], [Time].[1997].[Q3].[8])")
            .returns( "[Time].[Time].[1997].[Q3].[8]" );

        // non-Time dimension

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Product].[Product Name], [Product].[All Products].[Drink])")
            .returns( "[Product].[Product].[Drink].[Dairy].[Dairy].[Milk].[Gorilla].[Gorilla Whole Milk]" );

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Product].[Product Family], [Product].[All Products].[Drink])")
            .returns( "[Product].[Product].[Drink]" );

        // 'all' level

        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Product].[(All)], [Product].[All Products].[Drink])")
            .returns( "" );

        // ragged
        //getContext().withCube( "[Sales Ragged]" ).
        assertThatAxis(connection, "Sales Ragged",
            "ClosingPeriod([Store].[Store City], [Store].[All Stores].[Israel])")
            .returns( "[Store].[Store].[Israel].[Israel].[Tel Aviv]" );

        // Default member is [Time].[1997].
        assertThatAxis(connection, "Sales",
            "ClosingPeriod([Time].[Month])").returns( "[Time].[Time].[1997].[Q4].[12]" );

        assertThatAxis(connection, "Sales", "ClosingPeriod()").returns( "[Time].[Time].[1997].[Q4]" );

        //Context<?> testContext<?> = getContext().withCube( "[Sales Ragged]" );
        assertThatAxis(connection, "Sales Ragged",
            "ClosingPeriod([Store].[Store State], [Store].[All Stores].[Israel])")
            .returns( "" );

        assertThatAxis(connection, "Sales Ragged",
            "ClosingPeriod([Time].[Year], [Store].[All Stores].[Israel])")
            .throwsMessage( "The <level> and <member> arguments to ClosingPeriod must be "
                + "from the same hierarchy. The level was from '[Time]' but "
                + "the member was from '[Store]'." );
    }

    @Test
    void testClosingPeriodBelow(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "ClosingPeriod([Quarter],[1997].[Q3].[8])").returns( "" );
    }


    @Test
    void testOpeningPeriod(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "OpeningPeriod([Time].[Month], [Time].[1997].[Q3])")
            .returns( "[Time].[Time].[1997].[Q3].[7]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "OpeningPeriod([Time].[Quarter], [Time].[1997])")
            .returns( "[Time].[Time].[1997].[Q1]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "OpeningPeriod([Time].[Year], [Time].[1997])").returns( "[Time].[Time].[1997]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "OpeningPeriod([Time].[Month], [Time].[1997])")
            .returns( "[Time].[Time].[1997].[Q1].[1]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "OpeningPeriod([Product].[Product Name], [Product].[All Products].[Drink])")
            .returns( "[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good].[Good Imported Beer]" );

        //getTestContext().withCube( "[Sales Ragged]" ).
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales Ragged",
            "OpeningPeriod([Store].[Store City], [Store].[All Stores].[Israel])")
            .returns( "[Store].[Store].[Israel].[Israel].[Haifa]" );

        //getTestContext().withCube( "[Sales Ragged]" ).
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales Ragged",
            "OpeningPeriod([Store].[Store State], [Store].[All Stores].[Israel])")
            .returns( "" );

        // Default member is [Time].[1997].
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "OpeningPeriod([Time].[Month])").returns( "[Time].[Time].[1997].[Q1].[1]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "OpeningPeriod()").returns( "[Time].[Time].[1997].[Q1]" );

        //TestContext<?> testContext<?> = getTestContext().withCube( "[Sales Ragged]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales Ragged",
            "OpeningPeriod([Time].[Year], [Store].[All Stores].[Israel])")
            .throwsMessage( "The <level> and <member> arguments to OpeningPeriod must be "
                + "from the same hierarchy. The level was from '[Time]' but "
                + "the member was from '[Store]'." );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales Ragged",
            "OpeningPeriod([Store].[Store City])")
            .throwsMessage( "The <level> and <member> arguments to OpeningPeriod must be "
                + "from the same hierarchy. The level was from '[Store]' but "
                + "the member was from '[Time]'." );
    }

    /**
     * This tests new NULL functionality exception throwing
     */
    @Test
    void testOpeningPeriodNull(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "OpeningPeriod([Time].[Month], NULL)")
            .throwsMessage( "Function does not support NULL member parameter" );
    }

}
