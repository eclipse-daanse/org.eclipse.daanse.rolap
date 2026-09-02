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
package org.eclipse.daanse.olap.function.def.operators.divide;

import static org.eclipse.daanse.olap.function.TestResources.NullNumericExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class DivideOperatorDefTest {

    @Test
    void testDivide(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "10 / 5").returns( "2" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", NullNumericExpr + " / - 2").returns( "" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", NullNumericExpr + " / " + NullNumericExpr).returns( "" );

        // default behavior (NullDenominatorProducesNull = false)
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-2 / " + NullNumericExpr).returns( "Infinity" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "0 / 0").returns( "NaN" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-3 / (2 - 2)").returns( "-Infinity" );

        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "NULL/1").returns( "" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "NULL/NULL").returns( "" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1/NULL").returns( "Infinity" );
    }

    @Test
    @RolapConfig(key = ConfigConstants.NULL_DENOMINATOR_PRODUCES_NULL, value = "true", type = Boolean.class)
    void testDivideNullDenominatorProducesNull(Context<?> context) {
        // when NullOrZeroDenominatorProducesNull is set to true
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-2 / " + NullNumericExpr).returns( "" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "0 / 0").returns( "NaN" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "-3 / (2 - 2)").returns( "-Infinity" );

        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "NULL/1").returns( "" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "NULL/NULL").returns( "" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1/NULL").returns( "" );
    }

    @Test
    void testDividePrecedence(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "24 / 4 / 2 * 10 - -1").returns( "31" );
    }

}
