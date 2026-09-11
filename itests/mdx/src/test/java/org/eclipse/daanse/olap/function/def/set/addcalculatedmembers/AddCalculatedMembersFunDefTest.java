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
package org.eclipse.daanse.olap.function.def.set.addcalculatedmembers;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class AddCalculatedMembersFunDefTest {
    @Test
    void testAddCalculatedMembers(Context<?> context) {
        //----------------------------------------------------
        // AddCalculatedMembers: Calc member in dimension based on level
        // included
        //----------------------------------------------------
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatQuery(connection,
            "WITH MEMBER [Store].[USA].[CA plus OR] AS 'AGGREGATE({[Store].[USA].[CA], [Store].[USA].[OR]})' "
                + "SELECT {[Measures].[Unit Sales], [Measures].[Store Sales]} ON COLUMNS,"
                + "AddCalculatedMembers([Store].[USA].Children) ON ROWS "
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
                + "{[Store].[Store].[USA].[CA plus OR]}\n"
                + "Row #0: 16,890\n"
                + "Row #0: 36,175.20\n"
                + "Row #1: 19,287\n"
                + "Row #1: 40,170.29\n"
                + "Row #2: 30,114\n"
                + "Row #2: 63,282.86\n"
                + "Row #3: 36,177\n"
                + "Row #3: 76,345.49\n" );
        //----------------------------------------------------
        // Calc member in dimension based on level included
        // Calc members in measures in schema included
        //----------------------------------------------------
        assertThatQuery(connection,
            "WITH MEMBER [Store].[USA].[CA plus OR] AS 'AGGREGATE({[Store].[USA].[CA], [Store].[USA].[OR]})' "
                + "SELECT AddCalculatedMembers({[Measures].[Unit Sales], [Measures].[Store Sales]}) ON COLUMNS,"
                + "AddCalculatedMembers([Store].[USA].Children) ON ROWS "
                + "FROM Sales "
                + "WHERE ([1997].[Q1])")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Store Sales]}\n"
                + "{[Measures].[Profit]}\n"
                + "{[Measures].[Profit last Period]}\n"
                + "{[Measures].[Profit Growth]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[USA].[CA]}\n"
                + "{[Store].[Store].[USA].[OR]}\n"
                + "{[Store].[Store].[USA].[WA]}\n"
                + "{[Store].[Store].[USA].[CA plus OR]}\n"
                + "Row #0: 16,890\n"
                + "Row #0: 36,175.20\n"
                + "Row #0: $21,744.11\n"
                + "Row #0: $21,744.11\n"
                + "Row #0: 0.0%\n"
                + "Row #1: 19,287\n"
                + "Row #1: 40,170.29\n"
                + "Row #1: $24,089.22\n"
                + "Row #1: $24,089.22\n"
                + "Row #1: 0.0%\n"
                + "Row #2: 30,114\n"
                + "Row #2: 63,282.86\n"
                + "Row #2: $38,042.78\n"
                + "Row #2: $38,042.78\n"
                + "Row #2: 0.0%\n"
                + "Row #3: 36,177\n"
                + "Row #3: 76,345.49\n"
                + "Row #3: $45,833.33\n"
                + "Row #3: $45,833.33\n"
                + "Row #3: 0.0%\n" );
        //----------------------------------------------------
        // Two dimensions
        //----------------------------------------------------
        assertThatQuery(connection,
            "SELECT AddCalculatedMembers({[Measures].[Unit Sales], [Measures].[Store Sales]}) ON COLUMNS,"
                + "{([Store].[USA].[CA], [Gender].[F])} ON ROWS "
                + "FROM Sales "
                + "WHERE ([1997].[Q1])")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Store Sales]}\n"
                + "{[Measures].[Profit]}\n"
                + "{[Measures].[Profit last Period]}\n"
                + "{[Measures].[Profit Growth]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[USA].[CA], [Gender].[Gender].[F]}\n"
                + "Row #0: 8,218\n"
                + "Row #0: 17,928.37\n"
                + "Row #0: $10,771.98\n"
                + "Row #0: $10,771.98\n"
                + "Row #0: 0.0%\n" );
        //----------------------------------------------------
        // Should throw more than one dimension error
        //----------------------------------------------------

        assertThatAxis(connection, "Sales",
            "AddCalculatedMembers({([Store].[Store].[USA].[CA], [Gender].[Gender].[F])})")
            .throwsMessage( "Only single dimension members allowed in Set for AddCalculatedMembers");
    }

}
