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
package org.eclipse.daanse.olap.function.def.operators.and;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class AndOperatorDefTest {

    @Test
    void testAnd(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " 1=1 AND 2=2 ").isTrue();
    }

    @Test
    void testAnd2(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " 1=1 AND 2=0 ").isFalse();
    }

    @Test
    void testOr(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " 1=0 OR 2=0 ").isFalse();
    }

    @Test
    void testBool1(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1=1 AND 1=0").returns( "false" );
    }
    @Test
    void testBool2(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1=1 AND 1=1").returns( "true" );
    }

    @Test
    void testBool3(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1=1 AND null").returns( "false" );
    }

}
