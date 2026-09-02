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
package org.eclipse.daanse.olap.function.def.member.lastchild;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class LastChildFunDefTest {

    @Test
    void testLastChild(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].LastChild").returns("[Gender].[Gender].[M]");
    }

    @Test
    void testLastChildLastInLevel(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1997].[Q4].LastChild").returns("[Time].[Time].[1997].[Q4].[12]");
    }

    @Test
    void testLastChildAll(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[All Gender].LastChild").returns("[Gender].[Gender].[M]");
    }

    @Test
    void testLastChildOfChildless(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Gender].[M].LastChild").returns("");
    }

}
