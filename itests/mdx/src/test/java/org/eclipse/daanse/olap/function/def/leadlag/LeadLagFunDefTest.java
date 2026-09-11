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
package org.eclipse.daanse.olap.function.def.leadlag;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class LeadLagFunDefTest {

    @Test
    void testLag(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1997].[Q4].[12].Lag(4)").returns("[Time].[Time].[1997].[Q3].[8]");
    }

    @Test
    void testLagFirstInLevel(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[F].Lag(1)").returns("");
    }

    @Test
    void testLagAll(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].DefaultMember.Lag(2)").returns("");
    }

    @Test
    void testLagRoot(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1998].Lag(1)").returns("[Time].[Time].[1997]");
    }

    @Test
    void testLagRootTooFar(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1998].Lag(2)").returns("");
    }

    @Test
    void testLead(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1997].[Q2].[4].Lead(4)").returns("[Time].[Time].[1997].[Q3].[8]");
    }

    @Test
    void testLeadNegative(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[M].Lead(-1)").returns("[Gender].[Gender].[F]");
    }

    @Test
    void testLeadLastInLevel(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[M].Lead(3)").returns("");
    }

    @Test
    void testLeadNull(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].Parent.Lead(1)").returns("");
    }

    @Test
    void testLeadZero(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[F].Lead(0)").returns("[Gender].[Gender].[F]");
    }

}
