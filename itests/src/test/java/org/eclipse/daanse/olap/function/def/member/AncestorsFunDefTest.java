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
package org.eclipse.daanse.olap.function.def.member;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.text.NumberFormat;
import java.util.Locale;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class AncestorsFunDefTest {

    @Test
    void testAncestors(Context<?> context) {
        // Test that we can execute Ancestors by passing a level as
        // the depth argument (PC hierarchy)
    	NumberFormat format = NumberFormat.getCurrencyInstance(Locale.getDefault());
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatQuery(connection,
            "with\n"
                + "set [*ancestors] as\n"
                + "  'Ancestors([Employees].[All Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Eric Long]"
                + ".[Adam Reynolds].[Joshua Huff].[Teanna Cobb], [Employees].[All Employees].Level)'\n"
                + "select\n"
                + "  [*ancestors] on columns\n"
                + "from [HR]\n").returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Eric Long].[Adam Reynolds].[Joshua Huff]}\n"
                + "{[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Eric Long].[Adam Reynolds]}\n"
                + "{[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Eric Long]}\n"
                + "{[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges]}\n"
                + "{[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply]}\n"
                + "{[Employees].[Employees].[Sheri Nowmer]}\n"
                + "{[Employees].[Employees].[All Employees]}\n"
                + "Row #0: " + format.format(984.45) + "\n"
                + "Row #0: " + format.format(3426.54) + "\n"
                
                + "Row #0: " + format.format(3610.14) + "\n"
                + "Row #0: " + format.format(17099.20) + "\n"
                + "Row #0: " + format.format(36494.07) + "\n"
                + "Row #0: " + format.format(39431.67) + "\n"
                + "Row #0: " + format.format(39431.67) + "\n");
        // Test that we can execute Ancestors by passing a level as
        // the depth argument (non PC hierarchy)
        assertThatQuery(connection,
            "with\n"
                + "set [*ancestors] as\n"
                + "  'Ancestors([Store].[USA].[CA].[Los Angeles], [Store].[Store Country])'\n"
                + "select\n"
                + "  [*ancestors] on columns\n"
                + "from [Sales]\n").returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA].[CA]}\n"
                + "{[Store].[Store].[USA]}\n"
                + "Row #0: 74,748\n"
                + "Row #0: 266,773\n" );
        // Test that we can execute Ancestors by passing an integer as
        // the depth argument (PC hierarchy)
        assertThatQuery(connection,
            "with\n"
                + "set [*ancestors] as\n"
                + "  'Ancestors([Employees].[All Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Eric Long]"
                + ".[Adam Reynolds].[Joshua Huff].[Teanna Cobb], 3)'\n"
                + "select\n"
                + "  [*ancestors] on columns\n"
                + "from [HR]\n").returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Eric Long].[Adam Reynolds].[Joshua Huff]}\n"
                + "{[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Eric Long].[Adam Reynolds]}\n"
                + "{[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Eric Long]}\n"
                + "Row #0: " + format.format(984.45) + "\n"
                + "Row #0: " + format.format(3426.54) + "\n"
                + "Row #0: " + format.format(3610.14) + "\n");
        // Test that we can execute Ancestors by passing an integer as
        // the depth argument (non PC hierarchy)
        assertThatQuery(connection,
            "with\n"
                + "set [*ancestors] as\n"
                + "  'Ancestors([Store].[USA].[CA].[Los Angeles], 2)'\n"
                + "select\n"
                + "  [*ancestors] on columns\n"
                + "from [Sales]\n").returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA].[CA]}\n"
                + "{[Store].[Store].[USA]}\n"
                + "Row #0: 74,748\n"
                + "Row #0: 266,773\n" );
        // Test that we can count the number of ancestors.
        assertThatQuery(connection,
            "with\n"
                + "set [*ancestors] as\n"
                + "  'Ancestors([Employees].[All Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Eric Long]"
                + ".[Adam Reynolds].[Joshua Huff].[Teanna Cobb], [Employees].[All Employees].Level)'\n"
                + "member [Measures].[Depth] as\n"
                + "  'Count([*ancestors])'\n"
                + "select\n"
                + "  [Measures].[Depth] on columns\n"
                + "from [HR]\n").returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Depth]}\n"
                + "Row #0: 7\n" );
        // test depth argument not a level
        assertThatAxis(connection, "Sales",
            "Ancestors([Store].[USA].[CA].[Los Angeles],[Store])")
            .throwsMessage("Error while executing query");
    }

}
