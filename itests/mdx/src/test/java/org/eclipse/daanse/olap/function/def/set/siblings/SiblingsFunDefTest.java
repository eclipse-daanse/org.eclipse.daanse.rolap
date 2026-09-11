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
package org.eclipse.daanse.olap.function.def.set.siblings;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class SiblingsFunDefTest {

    @Test
    void testSiblingsA(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Time].[1997].Siblings}")
            .returns(
            "[Time].[Time].[1997]\n"
                + "[Time].[Time].[1998]" );
    }

    @Test
    void testSiblingsB(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Store].Siblings}")
            .returns( "[Store].[Store].[All Stores]" );
    }

    @Test
    void testSiblingsC(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Store].[USA].[CA].Siblings}")
            .returns(
            "[Store].[Store].[USA].[CA]\n"
                + "[Store].[Store].[USA].[OR]\n"
                + "[Store].[Store].[USA].[WA]" );
    }

    @Test
    void testSiblingsD(Context<?> context) {
        // The null member has no siblings -- not even itself
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "{[Gender].Parent.Siblings}").returns( "" );

        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "count ([Gender].parent.siblings, includeempty)")
            .returns( "0" );
    }

}
