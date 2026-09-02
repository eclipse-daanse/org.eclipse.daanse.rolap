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
package org.eclipse.daanse.olap.function.def.lastperiods;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class LastPeriodsFunDefTest {

    @Test
    void testLastPeriods(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(0, [Time].[1998])")
            .returns( "" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(1, [Time].[1998])")
            .returns( "[Time].[Time].[1998]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(-1, [Time].[1998])")
            .returns( "[Time].[Time].[1998]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(2, [Time].[1998])")
            .returns(
            "[Time].[Time].[1997]\n" + "[Time].[Time].[1998]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(-2, [Time].[1997])")
            .returns(
            "[Time].[Time].[1997]\n" + "[Time].[Time].[1998]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(5000, [Time].[1998])")
            .returns(
            "[Time].[Time].[1997]\n" + "[Time].[Time].[1998]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(-5000, [Time].[1997])")
            .returns(
            "[Time].[Time].[1997]\n" + "[Time].[Time].[1998]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(2, [Time].[1998].[Q2])")
            .returns(
            "[Time].[Time].[1998].[Q1]\n" + "[Time].[Time].[1998].[Q2]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(4, [Time].[1998].[Q2])")
            .returns(
            "[Time].[Time].[1997].[Q3]\n"
                + "[Time].[Time].[1997].[Q4]\n"
                + "[Time].[Time].[1998].[Q1]\n"
                + "[Time].[Time].[1998].[Q2]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(-2, [Time].[1997].[Q2])")
            .returns(
            "[Time].[Time].[1997].[Q2]\n" + "[Time].[Time].[1997].[Q3]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(-4, [Time].[1997].[Q2])")
            .returns(
            "[Time].[Time].[1997].[Q2]\n"
                + "[Time].[Time].[1997].[Q3]\n"
                + "[Time].[Time].[1997].[Q4]\n"
                + "[Time].[Time].[1998].[Q1]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(5000, [Time].[1998].[Q2])")
            .returns(
            "[Time].[Time].[1997].[Q1]\n"
                + "[Time].[Time].[1997].[Q2]\n"
                + "[Time].[Time].[1997].[Q3]\n"
                + "[Time].[Time].[1997].[Q4]\n"
                + "[Time].[Time].[1998].[Q1]\n"
                + "[Time].[Time].[1998].[Q2]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(-5000, [Time].[1998].[Q2])")
            .returns(
            "[Time].[Time].[1998].[Q2]\n"
                + "[Time].[Time].[1998].[Q3]\n"
                + "[Time].[Time].[1998].[Q4]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(2, [Time].[1998].[Q2].[5])")
            .returns(
            "[Time].[Time].[1998].[Q2].[4]\n" + "[Time].[Time].[1998].[Q2].[5]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(12, [Time].[1998].[Q2].[5])")
            .returns(
            "[Time].[Time].[1997].[Q2].[6]\n"
                + "[Time].[Time].[1997].[Q3].[7]\n"
                + "[Time].[Time].[1997].[Q3].[8]\n"
                + "[Time].[Time].[1997].[Q3].[9]\n"
                + "[Time].[Time].[1997].[Q4].[10]\n"
                + "[Time].[Time].[1997].[Q4].[11]\n"
                + "[Time].[Time].[1997].[Q4].[12]\n"
                + "[Time].[Time].[1998].[Q1].[1]\n"
                + "[Time].[Time].[1998].[Q1].[2]\n"
                + "[Time].[Time].[1998].[Q1].[3]\n"
                + "[Time].[Time].[1998].[Q2].[4]\n"
                + "[Time].[Time].[1998].[Q2].[5]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(-2, [Time].[1998].[Q2].[4])")
            .returns(
            "[Time].[Time].[1998].[Q2].[4]\n" + "[Time].[Time].[1998].[Q2].[5]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(-12, [Time].[1997].[Q2].[6])")
            .returns(
            "[Time].[Time].[1997].[Q2].[6]\n"
                + "[Time].[Time].[1997].[Q3].[7]\n"
                + "[Time].[Time].[1997].[Q3].[8]\n"
                + "[Time].[Time].[1997].[Q3].[9]\n"
                + "[Time].[Time].[1997].[Q4].[10]\n"
                + "[Time].[Time].[1997].[Q4].[11]\n"
                + "[Time].[Time].[1997].[Q4].[12]\n"
                + "[Time].[Time].[1998].[Q1].[1]\n"
                + "[Time].[Time].[1998].[Q1].[2]\n"
                + "[Time].[Time].[1998].[Q1].[3]\n"
                + "[Time].[Time].[1998].[Q2].[4]\n"
                + "[Time].[Time].[1998].[Q2].[5]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(2, [Gender].[M])")
            .returns(
            "[Gender].[Gender].[F]\n" + "[Gender].[Gender].[M]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(-2, [Gender].[F])")
            .returns(
            "[Gender].[Gender].[F]\n" + "[Gender].[Gender].[M]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(2, [Gender])")
            .returns( "[Gender].[Gender].[All Gender]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "LastPeriods(2, [Gender].Parent)")
            .returns( "" );
    }

}
