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
package org.eclipse.daanse.olap.function.def.member.firstsibling;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class FirstSiblingFunDefTest {


    @Test
    void testFirstSiblingFirstInLevel(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[F].FirstSibling").returns("[Gender].[Gender].[F]");
    }

    @Test
    void testFirstSiblingLastInLevel(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1997].[Q4].FirstSibling").returns("[Time].[Time].[1997].[Q1]");
    }

    @Test
    void testFirstSiblingAll(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[All Gender].FirstSibling").returns("[Gender].[Gender].[All Gender]");
    }

    @Test
    void testFirstSiblingRoot(Context<?> context) {
        // The [Measures] hierarchy does not have an 'all' member, so
        // [Unit Sales] does not have a parent.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Measures].[Store Sales].FirstSibling").returns("[Measures].[Unit Sales]");
    }

    @Test
    void testFirstSiblingNull(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[F].FirstChild.FirstSibling").returns("");
    }


}
