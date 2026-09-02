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
package org.eclipse.daanse.olap.function.def.caption.member;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;


import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class CaptionFunDefTest {

    @Test
    void testMemberCaption(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1997].Caption").returns("1997");
    }

    @Test
    void testGetCaptionUsingMemberDotCaption(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "SELECT Filter(Store.allmembers, "
                + "[store].currentMember.caption = \"USA\") on 0 FROM SALES").returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA]}\n"
                + "Row #0: 266,773\n" );
    }

}
