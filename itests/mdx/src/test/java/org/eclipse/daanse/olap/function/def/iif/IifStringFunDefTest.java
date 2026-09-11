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

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class IifStringFunDefTest {

    @Test
    void testIIf(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(([Measures].[Unit Sales],[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]) > 100, \"Yes\",\"No\")").returns(
            "Yes" );
    }

    @Test
    void testIIfWithStringAndNull(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(([Measures].[Unit Sales],[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]) > 100, null,\"foo\")").returns(
            "" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(([Measures].[Unit Sales],[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]) > 100, \"foo\",null)").returns(
            "foo" );
    }

    @Test
    void testIsEmptyWithNull(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "iif (isempty(null), \"is empty\", \"not is empty\")").returns(
            "is empty" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "iif (isempty(null), 1, 2)").returns("1");
    }

}
