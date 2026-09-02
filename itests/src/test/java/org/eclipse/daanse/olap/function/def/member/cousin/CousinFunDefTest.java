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
package org.eclipse.daanse.olap.function.def.member.cousin;

import static org.eclipse.daanse.olap.exceptions.CousinHierarchyMismatchException.cousinHierarchyMismatch;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import java.text.MessageFormat;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class CousinFunDefTest {

    @Test
    void testCousin1(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Cousin([1997].[Q4],[1998])").returns("[Time].[Time].[1998].[Q4]");
    }

    @Test
    void testCousin2(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Cousin([1997].[Q4].[12],[1998].[Q1])").returns("[Time].[Time].[1998].[Q1].[3]");
    }

    @Test
    void testCousinOverrun(Context<?> context) {
        // CA has more cities than OR
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Cousin([Customers].[USA].[CA].[San Jose],"
                + " [Customers].[USA].[OR])").returns("");
    }

    @Test
    void testCousinThreeDown(Context<?> context) {
        // Barbara Combs is the 6th child
        // of the 4th child (Berkeley)
        // of the 1st child (CA)
        // of USA
        // Annmarie Hill is the 6th child
        // of the 4th child (Tixapan)
        // of the 1st child (DF)
        // of Mexico
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
                "Cousin([Customers].[USA].[CA].[Berkeley].[Barbara Combs],"
                    + " [Customers].[Mexico])").returns(
            "[Customers].[Customers].[Mexico].[DF].[Tixapan].[Annmarie Hill]");
    }

    @Test
    void testCousinSameLevel(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Cousin([Gender].[M], [Gender].[F])").returns("[Gender].[Gender].[F]");
    }

    @Test
    void testCousinHigherLevel(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Cousin([Time].[1997], [Time].[1998].[Q1])").returns("");
    }

    @Test
    void testCousinWrongHierarchy(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Cousin([Time].[1997], [Gender].[M])")
            .throwsMessage(MessageFormat.format(cousinHierarchyMismatch,
                "[Time].[Time].[1997]",
                "[Gender].[Gender].[M]" ));
    }

}
