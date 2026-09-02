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
package org.eclipse.daanse.olap.function.def.set.setitem;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class SetItemFunDefTest {

    /**
     * Tests the function <code>&lt;Set&gt;.Item(&lt;Integer&gt;)</code>.
     */
    @Test
    void testSetItemInt(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Customers].[All Customers].[USA].[OR].[Lebanon].[Mary Frances Christian]}.Item(0)")
            .returns(
            "[Customers].[Customers].[USA].[OR].[Lebanon].[Mary Frances Christian]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Customers].[All Customers].[USA],"
                + "[Customers].[All Customers].[USA].[WA],"
                + "[Customers].[All Customers].[USA].[CA],"
                + "[Customers].[All Customers].[USA].[OR].[Lebanon].[Mary Frances Christian]}.Item(2)")
            .returns(
            "[Customers].[Customers].[USA].[CA]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Customers].[All Customers].[USA],"
                + "[Customers].[All Customers].[USA].[WA],"
                + "[Customers].[All Customers].[USA].[CA],"
                + "[Customers].[All Customers].[USA].[OR].[Lebanon].[Mary Frances Christian]}.Item(100 / 50 - 1)")
            .returns(
            "[Customers].[Customers].[USA].[WA]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{([Time].[1997].[Q1].[1], [Customers].[All Customers].[USA]),"
                + "([Time].[1997].[Q1].[2], [Customers].[All Customers].[USA].[WA]),"
                + "([Time].[1997].[Q1].[3], [Customers].[All Customers].[USA].[CA]),"
                + "([Time].[1997].[Q2].[4], [Customers].[All Customers].[USA].[OR].[Lebanon].[Mary Frances Christian])}"
                + ".Item(100 / 50 - 1)")
            .returns(
            "{[Time].[Time].[1997].[Q1].[2], [Customers].[Customers].[USA].[WA]}" );

        // given index out of bounds, item returns null
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Customers].[All Customers].[USA],"
                + "[Customers].[All Customers].[USA].[WA],"
                + "[Customers].[All Customers].[USA].[CA],"
                + "[Customers].[All Customers].[USA].[OR].[Lebanon].[Mary Frances Christian]}.Item(-1)")
            .returns(
            "" );

        // given index out of bounds, item returns null
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Customers].[All Customers].[USA],"
                + "[Customers].[All Customers].[USA].[WA],"
                + "[Customers].[All Customers].[USA].[CA],"
                + "[Customers].[All Customers].[USA].[OR].[Lebanon].[Mary Frances Christian]}.Item(4)")
            .returns(
            "" );
    }

    /**
     * Tests the function <code>&lt;Set&gt;.Item(&lt;String&gt; [,...])</code>.
     */
    @Test
    void testSetItemString(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Gender].[M], [Gender].[F]}.Item(\"M\")")
            .returns(
            "[Gender].[Gender].[M]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{CrossJoin([Gender].Members, [Marital Status].Members)}.Item(\"M\", \"S\")")
            .returns(
            "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S]}" );

        // MSAS fails with "duplicate dimensions across (independent) axes".
        // (That's a bug in MSAS.)
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{CrossJoin([Gender].Members, [Marital Status].Members)}.Item(\"M\", \"M\")")
            .returns(
            "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M]}" );

        // None found.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{[Gender].[M], [Gender].[F]}.Item(\"X\")")
            .returns( "" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{CrossJoin([Gender].[Gender].Members, [Marital Status].[Marital Status].Members)}.Item(\"M\", \"F\")")
            .returns(
            "" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "CrossJoin([Gender].[Gender].Members, [Marital Status].[Marital Status].Members).Item(\"S\", \"M\")")
            .returns(
            "" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "CrossJoin([Gender].Members, [Marital Status].Members).Item(\"M\")")
            .throwsMessage( "Argument count does not match set's cardinality 2" );
    }

}
