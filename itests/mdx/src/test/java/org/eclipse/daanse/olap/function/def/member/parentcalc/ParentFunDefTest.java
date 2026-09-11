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
package org.eclipse.daanse.olap.function.def.member.parentcalc;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatMemberExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class ParentFunDefTest {


    @Test
    void testParent(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatMemberExpr(connection, "Sales",
            "[Gender].Parent")
            .dependsOn( "[Gender].[Gender]" );
        assertThatMemberExpr(connection, "Sales", "[Gender].[M].Parent").dependsOn();
        assertThatAxis(connection, "Sales",
            "{[Store].[USA].[CA].Parent}").returns( "[Store].[Store].[USA]" );
        // root member has null parent
        assertThatAxis(connection, "Sales", "{[Store].[All Stores].Parent}").returns( "" );
        // parent of null member is null
        assertThatAxis(connection, "Sales", "{[Store].[All Stores].Parent.Parent}").returns( "" );
    }

    @Test
    void testParentPC(Context<?> context) {
        //final Context<?> testContext<?> = getContext().withCube( "HR" );
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatAxis(connection, "HR",
            "[Employees].Parent")
            .returns( "" );
        assertThatAxis(connection, "HR",
            "[Employees].[Sheri Nowmer].Parent")
            .returns( "[Employees].[Employees].[All Employees]" );
        assertThatAxis(connection, "HR",
            "[Employees].[Sheri Nowmer].[Derrick Whelply].Parent")
            .returns( "[Employees].[Employees].[Sheri Nowmer]" );
        assertThatAxis(connection, "HR",
            "[Employees].Members.Item(3)")
            .returns( "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Beverly Baker]" );
        assertThatAxis(connection, "HR",
            "[Employees].Members.Item(3).Parent")
            .returns( "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply]" );
        assertThatAxis(connection, "HR",
            "[Employees].AllMembers.Item(3).Parent")
            .returns( "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply]" );

        // Ascendants(<Member>) applied to parent-child hierarchy accessed via
        // <Level>.Members
        assertThatAxis(connection, "HR",
            "Ascendants([Employees].Members.Item(73))")
            .returns(
            "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Beverly Baker].[Jacqueline Wyllie].[Ralph Mccoy].[Bertha "
                + "Jameson].[James Bailey]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Beverly Baker].[Jacqueline Wyllie].[Ralph Mccoy].[Bertha "
                + "Jameson]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Beverly Baker].[Jacqueline Wyllie].[Ralph Mccoy]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Beverly Baker].[Jacqueline Wyllie]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Beverly Baker]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply]\n"
                + "[Employees].[Employees].[Sheri Nowmer]\n"
                + "[Employees].[Employees].[All Employees]" );
    }

    @Test
    void testBasic5(Context<?> context) {
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "select{ [Product].[All Products].[Drink].Parent} on columns "
                    + "from Sales" );
        assertEquals(
            "All Products",
            result.getAxes()[ 0 ].getPositions().get( 0 ).get( 0 ).getName() );
    }

    @Test
    void testFirstInLevel5(Context<?> context) {
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "select {[Time].[1997].[Q2].[4].Parent} on columns,"
                    + "{[Gender].[M]} on rows from Sales" );
        assertEquals(
            "Q2",
            result.getAxes()[ 0 ].getPositions().get( 0 ).get( 0 ).getName() );
    }

    @Test
    void testAll5(Context<?> context) {
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "select {[Time].[1997].[Q2].Parent} on columns,"
                    + "{[Gender].[M]} on rows from Sales" );
        // previous to [Gender].[All] is null, so no members are returned
        assertEquals(
            "1997",
            result.getAxes()[ 0 ].getPositions().get( 0 ).get( 0 ).getName() );
    }

}
