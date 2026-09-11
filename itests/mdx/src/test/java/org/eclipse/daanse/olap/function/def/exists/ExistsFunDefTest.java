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
package org.eclipse.daanse.olap.function.def.exists;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class ExistsFunDefTest {


    @Test
    void testExistsMembersLevel2(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists(\n"
                + "  {[Customers].[All Customers],\n"
                + "   [Customers].[Country].Members,\n"
                + "   [Customers].[State Province].[CA],\n"
                + "   [Customers].[Canada].[BC].[Richmond]},\n"
                + "  {[Customers].[Country].[USA]})\n"
                + "on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "{[Customers].[Customers].[USA].[CA]}\n"
                + "Row #0: 266,773\n"
                + "Row #0: 266,773\n"
                + "Row #0: 74,748\n" );
    }

    @Test
    void testExistsWithImplicitAllMember(Context<?> context) {
        // the tuple in the second arg in this case should implicitly
        // contain [Customers].[All Customers], so the whole tuple list
        // from the first arg should be returned.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select non empty exists(\n"
                + "  {[Customers].[All Customers],\n"
                + "   [Customers].[All Customers].Children,\n"
                + "   [Customers].[State Province].Members},\n"
                + "  {[Product].Members})\n"
                + "on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "{[Customers].[Customers].[USA].[CA]}\n"
                + "{[Customers].[Customers].[USA].[OR]}\n"
                + "{[Customers].[Customers].[USA].[WA]}\n"
                + "Row #0: 266,773\n"
                + "Row #0: 266,773\n"
                + "Row #0: 74,748\n"
                + "Row #0: 67,659\n"
                + "Row #0: 124,366\n" );

        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists( "
                + "[Customers].[Customers].[USA].[CA], (Store.[USA], Gender.[F])) "
                + "on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA]}\n"
                + "Row #0: 74,748\n" );
    }

    @Test
    void testExistsWithMultipleHierarchies(Context<?> context) {
        // tests queries w/ a multi-hierarchy dim in either or both args.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists( "
                + "crossjoin( time.[1997], {[Time].[Weekly].[1997].[16]}), "
                + " { Gender.F } ) on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1997], [Time].[Weekly].[1997].[16]}\n"
                + "Row #0: 3,839\n" );

        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists( "
                + "time.[1997].[Q1], {[Time].[Weekly].[1997].[4]}) "
                + " on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "Row #0: 66,291\n" );

        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists( "
                + "{ Gender.F }, "
                + "crossjoin( time.[1997], {[Time].[Weekly].[1997].[16]})  ) "
                + "on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Gender].[Gender].[F]}\n"
                + "Row #0: 131,558\n" );

        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists( "
                + "{ time.[1998] }, "
                + "crossjoin( time.[1997], {[Time].[Weekly].[1997].[16]})  ) "
                + "on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n" );
    }

    @Test
    void testExistsWithDefaultNonAllMember(Context<?> context) {
        // default mem for Time is 1997

        // non-all default on right side.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists( [Time].[1998].[Q1], Gender.[All Gender]) on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n" );

        // switching to an explicit member on the hierarchy chain should return
        // 1998.Q1
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists( [Time].[1998].[Q1], ([Time].[1998], Gender.[All Gender])) on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1998].[Q1]}\n"
                + "Row #0: \n" );


        // non-all default on left side
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists( "
                + "Gender.[All Gender], (Gender.[F], [Time].[1998].[Q1])) "
                + "on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n" );

        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists( "
                + "(Time.[1998].[Q1].[1], Gender.[All Gender]), (Gender.[F], [Time].[1998].[Q1])) "
                + "on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1998].[Q1].[1], [Gender].[Gender].[All Gender]}\n"
                + "Row #0: \n" );
    }

    @Test
    void testExistsMembers2Hierarchies(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists(\n"
                + "  {[Customers].[All Customers],\n"
                + "   [Customers].[All Customers].Children,\n"
                + "   [Customers].[State Province].Members,\n"
                + "   [Customers].[Country].[Canada],\n"
                + "   [Customers].[Country].[Mexico]},\n"
                + "  {[Customers].[Country].[USA],\n"
                + "   [Customers].[State Province].[Veracruz]})\n"
                + "on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "{[Customers].[Customers].[Mexico]}\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "{[Customers].[Customers].[Mexico].[Veracruz]}\n"
                + "{[Customers].[Customers].[USA].[CA]}\n"
                + "{[Customers].[Customers].[USA].[OR]}\n"
                + "{[Customers].[Customers].[USA].[WA]}\n"
                + "{[Customers].[Customers].[Mexico]}\n"
                + "Row #0: 266,773\n"
                + "Row #0: \n"
                + "Row #0: 266,773\n"
                + "Row #0: \n"
                + "Row #0: 74,748\n"
                + "Row #0: 67,659\n"
                + "Row #0: 124,366\n"
                + "Row #0: \n" );
    }

    @Test
    void testExistsTuplesAll(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists(\n"
                + "  crossjoin({[Product].[All Products]},{[Customers].[All Customers]}),\n"
                + "  {[Customers].[All Customers]})\n"
                + "on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Product].[Product].[All Products], [Customers].[Customers].[All Customers]}\n"
                + "Row #0: 266,773\n" );
    }

    @Test
    void testExistsTuplesLevel2(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists(\n"
                + "  crossjoin({[Product].[All Products]},{[Customers].[All Customers].Children}),\n"
                + "  {[Customers].[All Customers].[USA]})\n"
                + "on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Product].[Product].[All Products], [Customers].[Customers].[USA]}\n"
                + "Row #0: 266,773\n" );
    }

    @Test
    void testExistsTuplesLevel23(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists(\n"
                + "  crossjoin({[Customers].[State Province].Members}, {[Product].[All Products]}),\n"
                + "  {[Customers].[All Customers].[USA]})\n"
                + "on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA], [Product].[Product].[All Products]}\n"
                + "{[Customers].[Customers].[USA].[OR], [Product].[Product].[All Products]}\n"
                + "{[Customers].[Customers].[USA].[WA], [Product].[Product].[All Products]}\n"
                + "Row #0: 74,748\n"
                + "Row #0: 67,659\n"
                + "Row #0: 124,366\n" );
    }

    @Test
    void testExistsTuples2Dim(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists(\n"
                + "  crossjoin({[Customers].[State Province].Members}, {[Product].[Product Family].Members}),\n"
                + "  {([Product].[Product Department].[Dairy],[Customers].[All Customers].[USA])})\n"
                + "on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA], [Product].[Product].[Drink]}\n"
                + "{[Customers].[Customers].[USA].[OR], [Product].[Product].[Drink]}\n"
                + "{[Customers].[Customers].[USA].[WA], [Product].[Product].[Drink]}\n"
                + "Row #0: 7,102\n"
                + "Row #0: 6,106\n"
                + "Row #0: 11,389\n" );
    }

    @Test
    void testExistsTuplesDiffDim(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists(\n"
                + "  crossjoin(\n"
                + "    crossjoin({[Customers].[State Province].Members},\n"
                + "              {[Time].[Year].[1997]}), \n"
                + "    {[Product].[Product Family].Members}),\n"
                + "  {([Product].[Product Department].[Dairy],\n"
                + "    [Promotions].[All Promotions], \n"
                + "    [Customers].[All Customers].[USA])})\n"
                + "on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA], [Time].[Time].[1997], [Product].[Product].[Drink]}\n"
                + "{[Customers].[Customers].[USA].[OR], [Time].[Time].[1997], [Product].[Product].[Drink]}\n"
                + "{[Customers].[Customers].[USA].[WA], [Time].[Time].[1997], [Product].[Product].[Drink]}\n"
                + "Row #0: 7,102\n"
                + "Row #0: 6,106\n"
                + "Row #0: 11,389\n" );
    }

    @Test
    void testExistsMembersAll(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select exists(\n"
                + "  {[Customers].[All Customers],\n"
                + "   [Customers].[Country].Members,\n"
                + "   [Customers].[State Province].[CA],\n"
                + "   [Customers].[Canada].[BC].[Richmond]},\n"
                + "  {[Customers].[All Customers]})\n"
                + "on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "{[Customers].[Customers].[Canada]}\n"
                + "{[Customers].[Customers].[Mexico]}\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "{[Customers].[Customers].[USA].[CA]}\n"
                + "{[Customers].[Customers].[Canada].[BC].[Richmond]}\n"
                + "Row #0: 266,773\n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: 266,773\n"
                + "Row #0: 74,748\n"
                + "Row #0: \n" );
    }

}
