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
package org.eclipse.daanse.olap.function.def.iif;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class IifFunDefTest {

    @Test
    void testIIfMember(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(1 > 2,[Store].[USA],[Store].[Canada].[BC])")
            .returns( "[Store].[Store].[Canada].[BC]" );
    }

    @Test
    void testIIfLevel(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(1 > 2, [Store].[Store Country],[Store].[Store City]).Name")
            .returns( "Store City" );
    }


    @Test
    void testIIfHierarchy(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(1 > 2, [Time], [Store]).Name")
            .returns( "Store" );

        // Call Iif(<Logical>, <Dimension>, <Hierarchy>). Argument #3, the
        // hierarchy [Time.Weekly] is implicitly converted to
        // the dimension [Time] to match argument #2 which is a dimension.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(1 > 2, [Time], [Time].[Weekly]).Name")
            .returns( "Time" );
    }


    @Test
    void testIIfDimension(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(1 > 2, [Store], [Time]).Name")
            .returns( "Time" );
    }


    @Test
    void testIIfSet(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "IIf(1 > 2, {[Store].[USA], [Store].[USA].[CA]}, {[Store].[Mexico], [Store].[USA].[OR]})")
            .returns(
            "[Store].[Store].[Mexico]\n"
                + "[Store].[Store].[USA].[OR]" );
    }

    // MONDRIAN-2408 - Consumer wants ITERABLE or ANY in CrossJoinFunDef.compileCall(ResolvedFunCall, ExpCompiler)
    @Test
    void testIIfSetType_InCrossJoin(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "CROSSJOIN([Store Type].[Deluxe Supermarket],IIf(1 = 1, {[Store].[USA], [Store].[USA].[CA]}, {[Store].[Mexico],"
                + " [Store].[USA].[OR]}))")
            .returns(
            "{[Store Type].[Store Type].[Deluxe Supermarket], [Store].[Store].[USA]}\n"
                + "{[Store Type].[Store Type].[Deluxe Supermarket], [Store].[Store].[USA].[CA]}" );
    }
}
