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
package org.eclipse.daanse.olap.function.def.operators.plus;

import static org.eclipse.daanse.olap.function.TestResources.NullNumericExpr;
import static org.eclipse.daanse.olap.function.TestResources.hiersExcept;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.FunDependencies;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class PlusOperatorDefTest {

    @Test
    void testPlus(Context<?> context) {
        FunDependencies.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1 + 2").dependsOn();
        FunDependencies.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "([Measures].[Unit Sales], [Gender].[F]) + 2")
            .dependsOn( hiersExcept( "[Measures]", "[Gender].[Gender]" ) );

        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1+2").returns( "3" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "5 + " + NullNumericExpr).returns( "5" ); // 5 + null --> 5
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", NullNumericExpr + " + " + NullNumericExpr).returns( "" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", NullNumericExpr + " + 0").returns( "0" );
    }


    @Test
    void testPlus_NULL_plus_1(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",  "null + 1").returns( "1" );
    }

    @Test
    void testPlus_NULL_plus_0(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",  "null + 0").returns( "0" );
    }
    @Test
    void testPlus_NULL_plus_NULL(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",  "null + null").returns( "" );
    }

    @Test
    void testMinus(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "1-3").returns( "-2" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "5 - " + NullNumericExpr).returns( "5" ); // 5 - null --> 5
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", NullNumericExpr + " - - 2").returns( "2" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", NullNumericExpr + " - " + NullNumericExpr).returns( "" );
    }

}
