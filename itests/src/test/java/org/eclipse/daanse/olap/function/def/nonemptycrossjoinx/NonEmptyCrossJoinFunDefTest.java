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
package org.eclipse.daanse.olap.function.def.nonemptycrossjoinx;

import static org.eclipse.daanse.olap.function.TestResources.hiersExcept;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatSetExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class NonEmptyCrossJoinFunDefTest {

    @Test
    void testNonEmptyCrossJoin(Context<?> context) {
        // NonEmptyCrossJoin needs to evaluate measures to find out whether
        // cells are empty, so it implicitly depends upon all dimensions.
        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "NonEmptyCrossJoin([Store].[USA].Children, [Gender].Children)")
            .dependsOn( hiersExcept( "[Store].[Store]" ) );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "NonEmptyCrossJoin("
                + "[Customers].[All Customers].[USA].[CA].Children, "
                + "[Product].[All Products].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good].Children)")
            .returns(
            "{[Customers].[Customers].[USA].[CA].[Bellflower], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good]"
                + ".[Good Light Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Downey], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good]"
                + ".[Good Imported Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Glendale], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good]"
                + ".[Good Imported Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Glendale], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good]"
                + ".[Good Light Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Grossmont], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good]"
                + ".[Good Light Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Imperial Beach], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]"
                + ".[Good].[Good Light Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[La Jolla], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good]"
                + ".[Good Imported Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Lincoln Acres], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]"
                + ".[Good].[Good Imported Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Lincoln Acres], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]"
                + ".[Good].[Good Light Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Long Beach], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]"
                + ".[Good].[Good Light Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Los Angeles], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]"
                + ".[Good].[Good Imported Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Newport Beach], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]"
                + ".[Good].[Good Imported Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Pomona], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good]"
                + ".[Good Imported Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Pomona], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good]"
                + ".[Good Light Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[San Gabriel], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]"
                + ".[Good].[Good Light Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[West Covina], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]"
                + ".[Good].[Good Imported Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[West Covina], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]"
                + ".[Good].[Good Light Beer]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Woodland Hills], [Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]"
                + ".[Good].[Good Imported Beer]}" );

        // empty set
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "NonEmptyCrossJoin({Gender.Parent}, {Store.Parent})").returns( "" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "NonEmptyCrossJoin({Store.Parent}, Gender.Children)").returns( "" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "NonEmptyCrossJoin(Store.Members, {})").returns( "" );

        // same dimension twice
        // todo: should throw
        if ( false ) {
            assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
                "NonEmptyCrossJoin({Store.[USA]}, {Store.[USA].[CA]})")
                .throwsMessage( "xxx" );
        }
    }

}
