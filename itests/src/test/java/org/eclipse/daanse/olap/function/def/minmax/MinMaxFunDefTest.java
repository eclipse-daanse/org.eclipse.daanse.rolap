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
package org.eclipse.daanse.olap.function.def.minmax;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class MinMaxFunDefTest {

    @Test
    void testMax(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "MAX({[Store].[All Stores].[USA].children},[Measures].[Store Sales])")
            .returns( "263,793.22" );
    }

    @Test
    void testMaxNegative(Context<?> context) {
        // Bug 1771928, "Max() works incorrectly with negative values"
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "  member [Customers].[Neg] as '-1'\n"
                + "  member [Customers].[Min] as 'Min({[Customers].[Neg]})'\n"
                + "  member [Customers].[Max] as 'Max({[Customers].[Neg]})'\n"
                + "select {[Customers].[Neg],[Customers].[Min],[Customers].[Max]} on 0\n"
                + "from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[Neg]}\n"
                + "{[Customers].[Customers].[Min]}\n"
                + "{[Customers].[Customers].[Max]}\n"
                + "Row #0: -1\n"
                + "Row #0: -1\n"
                + "Row #0: -1\n" );
    }

    @Test
    void testMin(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "MIN({[Store].[All Stores].[USA].children},[Measures].[Store Sales])")
            .returns( "142,277.07" );
    }

    @Test
    void testMinTuple(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Min([Customers].[All Customers].[USA].Children, ([Measures].[Unit Sales], [Gender].[All Gender].[F]))")
            .returns( "33,036" );
    }

}
