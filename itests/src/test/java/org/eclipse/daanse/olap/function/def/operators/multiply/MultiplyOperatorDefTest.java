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
package org.eclipse.daanse.olap.function.def.operators.multiply;

import static org.eclipse.daanse.olap.function.TestResources.NullNumericExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class MultiplyOperatorDefTest {

    @Test
    void testMultiply(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "4*7").returns( "28" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "5 * " + NullNumericExpr).returns( "" ); // 5 * null --> null
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", NullNumericExpr + " * - 2").returns( "" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", NullNumericExpr + " - " + NullNumericExpr).returns( "" );
    }

    @Test
    void testMultiplyPrecedence(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "3 + 4 * 5 + 6").returns( "29" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "5 * 24 / 4 * 2").returns( "60" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "48 / 4 / 2").returns( "6" );
    }

    /**
     * Bug 774807 caused expressions to be mistaken for the crossjoin operator.
     */
    @Test
    void testMultiplyBug774807(Context<?> context) {
        final String desiredResult =
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[All Stores]}\n"
                + "Axis #2:\n"
                + "{[Measures].[Store Sales]}\n"
                + "{[Measures].[A]}\n"
                + "Row #0: 565,238.13\n"
                + "Row #1: 319,494,143,605.90\n";
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Measures].[A] AS\n"
                + " '([Measures].[Store Sales] * [Measures].[Store Sales])'\n"
                + "SELECT {[Store]} ON COLUMNS,\n"
                + " {[Measures].[Store Sales], [Measures].[A]} ON ROWS\n"
                + "FROM Sales")
            .returnsGrid( desiredResult );
        // as above, no parentheses
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Measures].[A] AS\n"
                + " '[Measures].[Store Sales] * [Measures].[Store Sales]'\n"
                + "SELECT {[Store]} ON COLUMNS,\n"
                + " {[Measures].[Store Sales], [Measures].[A]} ON ROWS\n"
                + "FROM Sales")
            .returnsGrid( desiredResult );
        // as above, plus 0
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Measures].[A] AS\n"
                + " '[Measures].[Store Sales] * [Measures].[Store Sales] + 0'\n"
                + "SELECT {[Store]} ON COLUMNS,\n"
                + " {[Measures].[Store Sales], [Measures].[A]} ON ROWS\n"
                + "FROM Sales")
            .returnsGrid( desiredResult );
    }

}
