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
package org.eclipse.daanse.olap.function.def.intersect;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class IntersectFunDefTest {


    @Test
    void testIntersectAll(Context<?> context) {
        // Note: duplicates retained from left, not from right; and order is
        // preserved.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Intersect({[Time].[1997].[Q2], [Time].[1997], [Time].[1997].[Q1], [Time].[1997].[Q2]}, "
                + "{[Time].[1998], [Time].[1997], [Time].[1997].[Q2], [Time].[1997]}, "
                + "ALL)")
            .returns(
            "[Time].[Time].[1997].[Q2]\n"
                + "[Time].[Time].[1997]\n"
                + "[Time].[Time].[1997].[Q2]" );
    }

    @Test
    void testIntersect(Context<?> context) {
        // Duplicates not preserved. Output in order that first duplicate
        // occurred.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Intersect(\n"
                + "  {[Time].[1997].[Q2], [Time].[1997], [Time].[1997].[Q1], [Time].[1997].[Q2]}, "
                + "{[Time].[1998], [Time].[1997], [Time].[1997].[Q2], [Time].[1997]})")
            .returns(
            "[Time].[Time].[1997].[Q2]\n"
                + "[Time].[Time].[1997]" );
    }

    @Test
    void testIntersectTuples(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Intersect(\n"
                + "  {([Time].[1997].[Q2], [Gender].[M]),\n"
                + "   ([Time].[1997], [Gender].[F]),\n"
                + "   ([Time].[1997].[Q1], [Gender].[M]),\n"
                + "   ([Time].[1997].[Q2], [Gender].[M])},\n"
                + "  {([Time].[1998], [Gender].[F]),\n"
                + "   ([Time].[1997], [Gender].[F]),\n"
                + "   ([Time].[1997].[Q2], [Gender].[M]),\n"
                + "   ([Time].[1997], [Gender])})")
            .returns(
            "{[Time].[Time].[1997].[Q2], [Gender].[Gender].[M]}\n"
                + "{[Time].[Time].[1997], [Gender].[Gender].[F]}" );
    }

    @Test
    void testIntersectRightEmpty(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Intersect({[Time].[1997]}, {})")
            .returns(
            "" );
    }

    @Test
    void testIntersectLeftEmpty(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Intersect({}, {[Store].[USA].[CA]})")
            .returns(
            "" );
    }

}
