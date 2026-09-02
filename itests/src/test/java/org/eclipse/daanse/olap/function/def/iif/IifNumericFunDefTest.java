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
package org.eclipse.daanse.olap.function.def.iif;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class IifNumericFunDefTest {

    @Test
    void testIIfNumeric(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(([Measures].[Unit Sales],[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]) > 100, 45, 32)")
            .returns( "45" );

        // Compare two members. The system needs to figure out that they are
        // both numeric, and use the right overloaded version of ">", otherwise
        // we'll get a ClassCastException at runtime.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf([Measures].[Unit Sales] > [Measures].[Store Sales], 45, 32)")
            .returns( "32" );
    }

    @Test
    void testIIfWithNullAndNumber(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(([Measures].[Unit Sales],[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]) > 100, null,20)")
            .returns( "" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(([Measures].[Unit Sales],[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]) > 100, 20,null)")
            .returns( "20" );
    }

    @Test
    void testIifFWithBooleanBooleanAndNumericParameterForReturningTruePart(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "SELECT Filter(Store.allmembers, "
                + "iif(measures.profit < 400000,"
                + "[store].currentMember.NAME = \"USA\", 0)) on 0 FROM SALES")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA]}\n"
                + "Row #0: 266,773\n" );
    }

    @Test
    void testIifWithBooleanBooleanAndNumericParameterForReturningFalsePart(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "SELECT Filter([Store].[USA].[CA].[Beverly Hills].children, "
                + "iif(measures.profit > 400000,"
                + "[store].currentMember.NAME = \"USA\", 1)) on 0 FROM SALES")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
                + "Row #0: 21,333\n" );
    }

    @Test
    void testIIFWithBooleanBooleanAndNumericParameterForReturningZero(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "SELECT Filter(Store.allmembers, "
                + "iif(measures.profit > 400000,"
                + "[store].currentMember.NAME = \"USA\", 0)) on 0 FROM SALES")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n" );
    }

}
