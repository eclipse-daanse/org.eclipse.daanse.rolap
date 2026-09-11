/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *
 */
package org.eclipse.daanse.olap.function.def.set.stripcalculatedmembers;

import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatSetExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class StripCalculatedMembersFunDefTest {

    @Test
    void testStripCalculatedMembers(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatAxis(connection, "Sales",
            "StripCalculatedMembers({[Measures].AllMembers})")
            .returns(
            "[Measures].[Unit Sales]\n"
                + "[Measures].[Store Cost]\n"
                + "[Measures].[Store Sales]\n"
                + "[Measures].[Sales Count]\n"
                + "[Measures].[Customer Count]\n"
                + "[Measures].[Promotion Sales]" );

        // applied to empty set
        assertThatAxis(connection, "Sales", "StripCalculatedMembers({[Gender].Parent})")
            .returns( "" );

        assertThatSetExpr(connection, "Sales",
            "StripCalculatedMembers([Customers].CurrentMember.Children)")
            .dependsOn( "[Customers].[Customers]" );

        // ----------------------------------------------------
        // Calc members in dimension based on level stripped
        // Actual members in measures left alone
        // ----------------------------------------------------
        assertThatQuery(connection,
            "WITH MEMBER [Store].[USA].[CA plus OR] AS "
                + "'AGGREGATE({[Store].[USA].[CA], [Store].[USA].[OR]})' "
                + "SELECT StripCalculatedMembers({[Measures].[Unit Sales], "
                + "[Measures].[Store Sales]}) ON COLUMNS,"
                + "StripCalculatedMembers("
                + "AddCalculatedMembers([Store].[USA].Children)) ON ROWS "
                + "FROM Sales "
                + "WHERE ([1997].[Q1])")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[USA].[CA]}\n"
                + "{[Store].[Store].[USA].[OR]}\n"
                + "{[Store].[Store].[USA].[WA]}\n"
                + "Row #0: 16,890\n"
                + "Row #0: 36,175.20\n"
                + "Row #1: 19,287\n"
                + "Row #1: 40,170.29\n"
                + "Row #2: 30,114\n"
                + "Row #2: 63,282.86\n" );
    }

}
