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

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class MinusPrefixOperatorDefTest {

    @Test
    void testUnaryMinus(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-3").returns( "-3" );
    }

    @Test
    void testUnaryMinusMember(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "- ([Measures].[Unit Sales],[Gender].[F])")
            .returns( "-131,558" );
    }

    @Test
    void testUnaryMinusPrecedence(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1 - -10.5 * 2 -3").returns( "19" );
    }

    @Test
    void testNegativeZero(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-0.0").returns( "0" );
    }

    @Test
    void testNegativeZero1(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-(0.0)").returns( "0" );
    }

    @Test
    void testNegativeZeroSubtract(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-0.0 - 0.0").returns( "0" );
    }

    @Test
    void testNegativeZeroMultiply(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-1 * 0").returns( "0" );
    }

    @Test
    void testNegativeZeroDivide(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-0.0 / 2").returns( "0" );
    }

}
