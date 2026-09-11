/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * History:
 *  This files came from the mondrian project. Some of the Flies
 *  (mostly the Tests) did not have License Header.
 *  But the Project is EPL Header. 2002-2022 Hitachi Vantara.
 *
 * Contributors:
 *   Hitachi Vantara.
 *   SmartCity Jena - initial  Java 8, Junit5
 */

package org.eclipse.daanse.rolap;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link ConfigConstants#ENABLE_NON_EMPTY_ON_ALL_AXIS} property.
 */
@RolapContextTest(FoodmartTestInstance.class)
class NonEmptyPropertyForAllAxisTest {

    // No teardown needed: the setting lives on each test's own context, set by the
    // EnableNonEmptyOnAllAxis updater, and dies with it.

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "true", type = Boolean.class)
    void testNonEmptyForAllAxesWithPropertySet(Context<?> context) {

        final String MDX_QUERY =
            "select {[Country].[USA].[OR].Children} on 0,"
            + " {[Promotions].[Promotions].Members} on 1 "
            + "from [Sales] "
            + "where [Product].[All Products].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good].[Good Light Beer]";
        final String EXPECTED_RESULT =
            "Axis #0:\n"
            + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good].[Good Light Beer]}\n"
            + "Axis #1:\n"
            + "{[Customers].[Customers].[USA].[OR].[Albany]}\n"
            + "{[Customers].[Customers].[USA].[OR].[Corvallis]}\n"
            + "{[Customers].[Customers].[USA].[OR].[Lake Oswego]}\n"
            + "{[Customers].[Customers].[USA].[OR].[Lebanon]}\n"
            + "{[Customers].[Customers].[USA].[OR].[Portland]}\n"
            + "{[Customers].[Customers].[USA].[OR].[Woodburn]}\n"
            + "Axis #2:\n"
            + "{[Promotions].[Promotions].[All Promotions]}\n"
            + "{[Promotions].[Promotions].[Cash Register Lottery]}\n"
            + "{[Promotions].[Promotions].[No Promotion]}\n"
            + "{[Promotions].[Promotions].[Saving Days]}\n"
            + "Row #0: 4\n"
            + "Row #0: 6\n"
            + "Row #0: 5\n"
            + "Row #0: 10\n"
            + "Row #0: 6\n"
            + "Row #0: 3\n"
            + "Row #1: \n"
            + "Row #1: 2\n"
            + "Row #1: \n"
            + "Row #1: 2\n"
            + "Row #1: \n"
            + "Row #1: \n"
            + "Row #2: 4\n"
            + "Row #2: 4\n"
            + "Row #2: 3\n"
            + "Row #2: 8\n"
            + "Row #2: 6\n"
            + "Row #2: 3\n"
            + "Row #3: \n"
            + "Row #3: \n"
            + "Row #3: 2\n"
            + "Row #3: \n"
            + "Row #3: \n"
            + "Row #3: \n";
        assertThatQuery(context.getConnectionWithDefaultRole(),MDX_QUERY).returnsGrid(EXPECTED_RESULT);
    }

    @Test
    // Deliberately without the EnableNonEmptyOnAllAxis updater - this test is the
    // one that checks behaviour with the setting off.
    void testNonEmptyForAllAxesWithOutPropertySet(Context<?> context) {
        final String MDX_QUERY =
            "SELECT {customers.USA.CA.[Santa Cruz].[Brian Merlo]} on 0, "
            + "[product].[product category].members on 1 FROM [sales]";
        final String EXPECTED_RESULT =
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Customers].[Customers].[USA].[CA].[Santa Cruz].[Brian Merlo]}\n"
            + "Axis #2:\n"
            + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]}\n"
            + "{[Product].[Product].[Drink].[Beverages].[Carbonated Beverages]}\n"
            + "{[Product].[Product].[Drink].[Beverages].[Drinks]}\n"
            + "{[Product].[Product].[Drink].[Beverages].[Hot Beverages]}\n"
            + "{[Product].[Product].[Drink].[Beverages].[Pure Juice Beverages]}\n"
            + "{[Product].[Product].[Drink].[Dairy].[Dairy]}\n"
            + "{[Product].[Product].[Food].[Baked Goods].[Bread]}\n"
            + "{[Product].[Product].[Food].[Baking Goods].[Baking Goods]}\n"
            + "{[Product].[Product].[Food].[Baking Goods].[Jams and Jellies]}\n"
            + "{[Product].[Product].[Food].[Breakfast Foods].[Breakfast Foods]}\n"
            + "{[Product].[Product].[Food].[Canned Foods].[Canned Anchovies]}\n"
            + "{[Product].[Product].[Food].[Canned Foods].[Canned Clams]}\n"
            + "{[Product].[Product].[Food].[Canned Foods].[Canned Oysters]}\n"
            + "{[Product].[Product].[Food].[Canned Foods].[Canned Sardines]}\n"
            + "{[Product].[Product].[Food].[Canned Foods].[Canned Shrimp]}\n"
            + "{[Product].[Product].[Food].[Canned Foods].[Canned Soup]}\n"
            + "{[Product].[Product].[Food].[Canned Foods].[Canned Tuna]}\n"
            + "{[Product].[Product].[Food].[Canned Foods].[Vegetables]}\n"
            + "{[Product].[Product].[Food].[Canned Products].[Fruit]}\n"
            + "{[Product].[Product].[Food].[Dairy].[Dairy]}\n"
            + "{[Product].[Product].[Food].[Deli].[Meat]}\n"
            + "{[Product].[Product].[Food].[Deli].[Side Dishes]}\n"
            + "{[Product].[Product].[Food].[Eggs].[Eggs]}\n"
            + "{[Product].[Product].[Food].[Frozen Foods].[Breakfast Foods]}\n"
            + "{[Product].[Product].[Food].[Frozen Foods].[Frozen Desserts]}\n"
            + "{[Product].[Product].[Food].[Frozen Foods].[Frozen Entrees]}\n"
            + "{[Product].[Product].[Food].[Frozen Foods].[Meat]}\n"
            + "{[Product].[Product].[Food].[Frozen Foods].[Pizza]}\n"
            + "{[Product].[Product].[Food].[Frozen Foods].[Vegetables]}\n"
            + "{[Product].[Product].[Food].[Meat].[Meat]}\n"
            + "{[Product].[Product].[Food].[Produce].[Fruit]}\n"
            + "{[Product].[Product].[Food].[Produce].[Packaged Vegetables]}\n"
            + "{[Product].[Product].[Food].[Produce].[Specialty]}\n"
            + "{[Product].[Product].[Food].[Produce].[Vegetables]}\n"
            + "{[Product].[Product].[Food].[Seafood].[Seafood]}\n"
            + "{[Product].[Product].[Food].[Snack Foods].[Snack Foods]}\n"
            + "{[Product].[Product].[Food].[Snacks].[Candy]}\n"
            + "{[Product].[Product].[Food].[Starchy Foods].[Starchy Foods]}\n"
            + "{[Product].[Product].[Non-Consumable].[Carousel].[Specialty]}\n"
            + "{[Product].[Product].[Non-Consumable].[Checkout].[Hardware]}\n"
            + "{[Product].[Product].[Non-Consumable].[Checkout].[Miscellaneous]}\n"
            + "{[Product].[Product].[Non-Consumable].[Health and Hygiene].[Bathroom Products]}\n"
            + "{[Product].[Product].[Non-Consumable].[Health and Hygiene].[Cold Remedies]}\n"
            + "{[Product].[Product].[Non-Consumable].[Health and Hygiene].[Decongestants]}\n"
            + "{[Product].[Product].[Non-Consumable].[Health and Hygiene].[Hygiene]}\n"
            + "{[Product].[Product].[Non-Consumable].[Health and Hygiene].[Pain Relievers]}\n"
            + "{[Product].[Product].[Non-Consumable].[Household].[Bathroom Products]}\n"
            + "{[Product].[Product].[Non-Consumable].[Household].[Candles]}\n"
            + "{[Product].[Product].[Non-Consumable].[Household].[Cleaning Supplies]}\n"
            + "{[Product].[Product].[Non-Consumable].[Household].[Electrical]}\n"
            + "{[Product].[Product].[Non-Consumable].[Household].[Hardware]}\n"
            + "{[Product].[Product].[Non-Consumable].[Household].[Kitchen Products]}\n"
            + "{[Product].[Product].[Non-Consumable].[Household].[Paper Products]}\n"
            + "{[Product].[Product].[Non-Consumable].[Household].[Plastic Products]}\n"
            + "{[Product].[Product].[Non-Consumable].[Periodicals].[Magazines]}\n"
            + "Row #0: 2\n"
            + "Row #1: 2\n"
            + "Row #2: \n"
            + "Row #3: \n"
            + "Row #4: \n"
            + "Row #5: \n"
            + "Row #6: \n"
            + "Row #7: \n"
            + "Row #8: \n"
            + "Row #9: \n"
            + "Row #10: \n"
            + "Row #11: \n"
            + "Row #12: \n"
            + "Row #13: \n"
            + "Row #14: \n"
            + "Row #15: \n"
            + "Row #16: \n"
            + "Row #17: \n"
            + "Row #18: \n"
            + "Row #19: \n"
            + "Row #20: \n"
            + "Row #21: \n"
            + "Row #22: 1\n"
            + "Row #23: \n"
            + "Row #24: \n"
            + "Row #25: \n"
            + "Row #26: \n"
            + "Row #27: \n"
            + "Row #28: 1\n"
            + "Row #29: \n"
            + "Row #30: \n"
            + "Row #31: \n"
            + "Row #32: \n"
            + "Row #33: \n"
            + "Row #34: \n"
            + "Row #35: \n"
            + "Row #36: \n"
            + "Row #37: \n"
            + "Row #38: \n"
            + "Row #39: \n"
            + "Row #40: \n"
            + "Row #41: \n"
            + "Row #42: \n"
            + "Row #43: \n"
            + "Row #44: \n"
            + "Row #45: \n"
            + "Row #46: \n"
            + "Row #47: \n"
            + "Row #48: \n"
            + "Row #49: \n"
            + "Row #50: 1\n"
            + "Row #51: \n"
            + "Row #52: \n"
            + "Row #53: \n"
            + "Row #54: 2\n";
        assertThatQuery(context.getConnectionWithDefaultRole(), MDX_QUERY).returnsGrid(EXPECTED_RESULT);
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "true", type = Boolean.class)
    void testSlicerAxisDoesNotGetNonEmptyApplied(Context<?> context) {

        String mdxQuery = "select from [Sales]\n"
            + "where [Time].[Time].[1997]\n";
        Connection connection = context.getConnectionWithDefaultRole();
        Query query = connection.parseQuery(mdxQuery);
        assertEquals(mdxQuery, query.toString());
     }
}
