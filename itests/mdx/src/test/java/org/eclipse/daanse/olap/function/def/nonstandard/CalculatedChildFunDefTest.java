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
package org.eclipse.daanse.olap.function.def.nonstandard;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class CalculatedChildFunDefTest {

    @Test
    void testCalculatedChild(Context<?> context) {
        // Construct calculated children with the same name for both [Drink] and
        // [Non-Consumable].  Then, create a metric to select the calculated
        // child based on current product member.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with\n"
                + " member [Product].[All Products].[Drink].[Calculated Child] as '[Product].[All Products].[Drink]"
                + ".[Alcoholic Beverages]'\n"
                + " member [Product].[All Products].[Non-Consumable].[Calculated Child] as '[Product].[All Products]"
                + ".[Non-Consumable].[Carousel]'\n"
                + " member [Measures].[Unit Sales CC] as '([Measures].[Unit Sales],[Product].currentmember.CalculatedChild"
                + "(\"Calculated Child\"))'\n"
                + " select non empty {[Measures].[Unit Sales CC]} on columns,\n"
                + " non empty {[Product].[Drink], [Product].[Non-Consumable]} on rows\n"
                + " from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales CC]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Non-Consumable]}\n"
                + "Row #0: 6,838\n" // Calculated child for [Drink]
                + "Row #1: 841\n" ); // Calculated child for [Non-Consumable]
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Product].[All Products].CalculatedChild(\"foobar\")").returns("");
    }

    @Disabled //disabled for CI build
    @Test
    void testCalculatedChildUsingItem(Context<?> context) {
        // Construct calculated children with the same name for both [Drink] and
        // [Non-Consumable].  Then, create a metric to select the first
        // calculated child.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with\n"
                + " member [Product].[All Products].[Drink].[Calculated Child] as '[Product].[All Products].[Drink]"
                + ".[Alcoholic Beverages]'\n"
                + " member [Product].[All Products].[Non-Consumable].[Calculated Child] as '[Product].[All Products]"
                + ".[Non-Consumable].[Carousel]'\n"
                + " member [Measures].[Unit Sales CC] as '([Measures].[Unit Sales],AddCalculatedMembers([Product]"
                + ".currentmember.children).Item(\"Calculated Child\"))'\n"
                + " select non empty {[Measures].[Unit Sales CC]} on columns,\n"
                + " non empty {[Product].[Drink], [Product].[Non-Consumable]} on rows\n"
                + " from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales CC]}\n"
                + "Axis #2:\n"
                + "{[Product].[Drink]}\n"
                + "{[Product].[Non-Consumable]}\n"
                + "Row #0: 6,838\n"
                // Note: For [Non-Consumable], the calculated child for [Drink] was
                // selected!
                + "Row #1: 6,838\n" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Product].[All Products].CalculatedChild(\"foobar\")").returns("");
    }

    @Test
    void testCalculatedChildOnMemberWithNoChildren(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
                "[Measures].[Store Sales].CalculatedChild(\"foobar\")").returns("");
    }

    @Test
    void testCalculatedChildOnNullMember(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
                "[Measures].[Store Sales].parent.CalculatedChild(\"foobar\")").returns("");
    }


}
