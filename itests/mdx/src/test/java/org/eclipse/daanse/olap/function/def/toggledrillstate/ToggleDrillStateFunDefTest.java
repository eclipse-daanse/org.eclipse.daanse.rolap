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
package org.eclipse.daanse.olap.function.def.toggledrillstate;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class ToggleDrillStateFunDefTest {


    @Test
    void testToggleDrillState(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "ToggleDrillState({[Customers].[USA],[Customers].[Canada]},"
                + "{[Customers].[USA],[Customers].[USA].[CA]})")
            .returns(
            "[Customers].[Customers].[USA]\n"
                + "[Customers].[Customers].[USA].[CA]\n"
                + "[Customers].[Customers].[USA].[OR]\n"
                + "[Customers].[Customers].[USA].[WA]\n"
                + "[Customers].[Customers].[Canada]" );
    }

    @Test
    void testToggleDrillState2(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "ToggleDrillState([Product].[Product Department].members, "
                + "{[Product].[All Products].[Food].[Snack Foods]})")
            .returns(
            "[Product].[Product].[Drink].[Alcoholic Beverages]\n"
                + "[Product].[Product].[Drink].[Beverages]\n"
                + "[Product].[Product].[Drink].[Dairy]\n"
                + "[Product].[Product].[Food].[Baked Goods]\n"
                + "[Product].[Product].[Food].[Baking Goods]\n"
                + "[Product].[Product].[Food].[Breakfast Foods]\n"
                + "[Product].[Product].[Food].[Canned Foods]\n"
                + "[Product].[Product].[Food].[Canned Products]\n"
                + "[Product].[Product].[Food].[Dairy]\n"
                + "[Product].[Product].[Food].[Deli]\n"
                + "[Product].[Product].[Food].[Eggs]\n"
                + "[Product].[Product].[Food].[Frozen Foods]\n"
                + "[Product].[Product].[Food].[Meat]\n"
                + "[Product].[Product].[Food].[Produce]\n"
                + "[Product].[Product].[Food].[Seafood]\n"
                + "[Product].[Product].[Food].[Snack Foods]\n"
                + "[Product].[Product].[Food].[Snack Foods].[Snack Foods]\n"
                + "[Product].[Product].[Food].[Snacks]\n"
                + "[Product].[Product].[Food].[Starchy Foods]\n"
                + "[Product].[Product].[Non-Consumable].[Carousel]\n"
                + "[Product].[Product].[Non-Consumable].[Checkout]\n"
                + "[Product].[Product].[Non-Consumable].[Health and Hygiene]\n"
                + "[Product].[Product].[Non-Consumable].[Household]\n"
                + "[Product].[Product].[Non-Consumable].[Periodicals]" );
    }

    @Test
    void testToggleDrillState3(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "ToggleDrillState("
                + "{[Time].[1997].[Q1],"
                + " [Time].[1997].[Q2],"
                + " [Time].[1997].[Q2].[4],"
                + " [Time].[1997].[Q2].[6],"
                + " [Time].[1997].[Q3]},"
                + "{[Time].[1997].[Q2]})")
            .returns(
            "[Time].[Time].[1997].[Q1]\n"
                + "[Time].[Time].[1997].[Q2]\n"
                + "[Time].[Time].[1997].[Q3]" );
    }

    // bug 634860
    @Test
    void testToggleDrillStateTuple(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "ToggleDrillState(\n"
                + "{([Store].[USA].[CA],"
                + "  [Product].[All Products].[Drink].[Alcoholic Beverages]),\n"
                + " ([Store].[USA],"
                + "  [Product].[All Products].[Drink])},\n"
                + "{[Store].[All stores].[USA].[CA]})")
            .returns(
            "{[Store].[Store].[USA].[CA], [Product].[Product].[Drink].[Alcoholic Beverages]}\n"
                + "{[Store].[Store].[USA].[CA].[Alameda], [Product].[Product].[Drink].[Alcoholic Beverages]}\n"
                + "{[Store].[Store].[USA].[CA].[Beverly Hills], [Product].[Product].[Drink].[Alcoholic Beverages]}\n"
                + "{[Store].[Store].[USA].[CA].[Los Angeles], [Product].[Product].[Drink].[Alcoholic Beverages]}\n"
                + "{[Store].[Store].[USA].[CA].[San Diego], [Product].[Product].[Drink].[Alcoholic Beverages]}\n"
                + "{[Store].[Store].[USA].[CA].[San Francisco], [Product].[Product].[Drink].[Alcoholic Beverages]}\n"
                + "{[Store].[Store].[USA], [Product].[Product].[Drink]}" );
    }

    @Test
    void testToggleDrillStateRecursive(Context<?> context) {
        // We expect this to fail.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "Select \n"
                + "    ToggleDrillState(\n"
                + "        {[Store].[USA]}, \n"
                + "        {[Store].[USA]}, recursive) on Axis(0) \n"
                + "from [Sales]\n")
            .throwsMessage( "'RECURSIVE' is not supported in ToggleDrillState." );
    }

}
