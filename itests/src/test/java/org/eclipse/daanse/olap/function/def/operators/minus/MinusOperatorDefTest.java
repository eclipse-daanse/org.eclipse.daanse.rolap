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
package org.eclipse.daanse.olap.function.def.operators.minus;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class MinusOperatorDefTest {

    @Test
    void testMinus_bug1234759(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Customers].[USAMinusMexico]\n"
                + "AS '([Customers].[All Customers].[USA] - [Customers].[All Customers].[Mexico])'\n"
                + "SELECT {[Measures].[Unit Sales]} ON COLUMNS,\n"
                + "{[Customers].[All Customers].[USA], [Customers].[All Customers].[Mexico],\n"
                + "[Customers].[USAMinusMexico]} ON ROWS\n"
                + "FROM [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "{[Customers].[Customers].[Mexico]}\n"
                + "{[Customers].[Customers].[USAMinusMexico]}\n"
                + "Row #0: 266,773\n"
                + "Row #1: \n"
                + "Row #2: 266,773\n"
                // with bug 1234759, this was null
                + "" );
    }

    @Test
    void testMinusAssociativity(Context<?> context) {
        // right-associative would give 11-(7-5) = 9, which is wrong
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "11-7-5").returns( "-1" );
    }

}
