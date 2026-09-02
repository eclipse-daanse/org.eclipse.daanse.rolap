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
package org.eclipse.daanse.olap.function.def.member.lastsibling;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class LastSiblingFunDefTest {

    @Test
    void testLastSibling(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[F].LastSibling").returns("[Gender].[Gender].[M]");
    }

    @Test
    void testLastSiblingFirstInLevel(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1997].[Q1].LastSibling").returns("[Time].[Time].[1997].[Q4]");
    }

    @Test
    void testLastSiblingAll(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[All Gender].LastSibling").returns("[Gender].[Gender].[All Gender]");
    }

    @Test
    void testLastSiblingRoot(Context<?> context) {
        // The [Time] hierarchy does not have an 'all' member, so
        // [1997], [1998] do not have parents.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1998].LastSibling").returns("[Time].[Time].[1998]");
    }

    @Test
    void testLastSiblingNull(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[F].FirstChild.LastSibling").returns("");
    }

}
