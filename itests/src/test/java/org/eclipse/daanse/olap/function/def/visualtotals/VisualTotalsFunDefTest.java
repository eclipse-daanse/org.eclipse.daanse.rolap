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
package org.eclipse.daanse.olap.function.def.visualtotals;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.util.List;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;


@RolapContextTest(FoodmartTestInstance.class)
class VisualTotalsFunDefTest {


    @Test
    void testVisualTotalsBasic(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns, "
                + "{VisualTotals("
                + "    {[Product].[All Products].[Food].[Baked Goods].[Bread],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Muffins]},"
                + "     \"**Subtotal - *\")} on rows "
                + "from [Sales]")
            .returnsGrid(

            // note that Subtotal - Bread only includes 2 displayed children
            // in member with visual totals name is the same but caption is changed
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread].[Bagels]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread].[Muffins]}\n"
                + "Row #0: 4,312\n"
                + "Row #1: 815\n"
                + "Row #2: 3,497\n" );
    }

    @Disabled //disabled for CI build
    @Test
    void testVisualTotalsConsecutively(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns, "
                + "{VisualTotals("
                + "    {[Product].[All Products].[Food].[Baked Goods].[Bread],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels].[Colony],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Muffins]},"
                + "     \"**Subtotal - *\")} on rows "
                + "from [Sales]")
            .returnsGrid(

            // Note that [Bagels] occurs 3 times, but only once does it
            // become a subtotal. Note that the subtotal does not include
            // the following [Bagels] member.
            // in member with visual totals name is the same but caption is changed
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Food].[Baked Goods].[Bread]}\n"
                + "{[Product].[Food].[Baked Goods].[Bread].[Bagels]}\n"
                + "{[Product].[Food].[Baked Goods].[Bread].[Bagels]}\n"
                + "{[Product].[Food].[Baked Goods].[Bread].[Bagels].[Colony]}\n"
                + "{[Product].[Food].[Baked Goods].[Bread].[Bagels]}\n"
                + "{[Product].[Food].[Baked Goods].[Bread].[Muffins]}\n"
                + "Row #0: 3,660\n"
                + "Row #1: 163\n"
                + "Row #2: 163\n"
                + "Row #3: 163\n"
                + "Row #4: 163\n"
                + "Row #5: 3,497\n" );
        // test is working incorrect. should be 163, 163, 163, 163, 3497  = 3497 + 163.  This is Analysis Services behavior.
        // We should use only [Product].[Food].[Baked Goods].[Bread].[Bagels].[Colony] for  [Food].[Baked Goods].[Bread].[Bagels]
    }

    @Test
    void testVisualTotalsNoPattern(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "VisualTotals("
                + "    {[Product].[All Products].[Food].[Baked Goods].[Bread],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Muffins]})")
            .returns(

            // Note that the [Bread] visual member is just called [Bread].
            "[Product].[Product].[Food].[Baked Goods].[Bread]\n"
                + "[Product].[Product].[Food].[Baked Goods].[Bread].[Bagels]\n"
                + "[Product].[Product].[Food].[Baked Goods].[Bread].[Muffins]" );
    }

    @Test
    void testVisualTotalsWithFilter(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns, "
                + "{Filter("
                + "    VisualTotals("
                + "        {[Product].[All Products].[Food].[Baked Goods].[Bread],"
                + "         [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],"
                + "         [Product].[All Products].[Food].[Baked Goods].[Bread].[Muffins]},"
                + "        \"**Subtotal - *\"),"
                + "[Measures].[Unit Sales] > 3400)} on rows "
                + "from [Sales]")
            .returnsGrid(

            // Note that [*Subtotal - Bread] still contains the
            // contribution of [Bagels] 815, which was filtered out.
            // in member with visual totals name is the same but caption is changed
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread].[Muffins]}\n"
                + "Row #0: 4,312\n"
                + "Row #1: 3,497\n" );
    }

    @Disabled //disabled for CI build
    @Test
    void testVisualTotalsNested(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns, "
                + "{VisualTotals("
                + "    Filter("
                + "        VisualTotals("
                + "            {[Product].[All Products].[Food].[Baked Goods].[Bread],"
                + "             [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],"
                + "             [Product].[All Products].[Food].[Baked Goods].[Bread].[Muffins]},"
                + "            \"**Subtotal - *\"),"
                + "    [Measures].[Unit Sales] > 3400),"
                + "    \"Second total - *\")} on rows "
                + "from [Sales]")
            .returnsGrid(

            // Yields the same -- no extra total.
            // in member with visual totals name is the same but caption is changed
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Food].[Baked Goods].[Bread]}\n"
                + "{[Product].[Food].[Baked Goods].[Bread].[Muffins]}\n"
                + "Row #0: 4,312\n"
                + "Row #1: 3,497\n" );
    }

    @Test
    void testVisualTotalsFilterInside(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns, "
                + "{VisualTotals("
                + "    Filter("
                + "        {[Product].[All Products].[Food].[Baked Goods].[Bread],"
                + "         [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],"
                + "         [Product].[All Products].[Food].[Baked Goods].[Bread].[Muffins]},"
                + "        [Measures].[Unit Sales] > 3400),"
                + "    \"**Subtotal - *\")} on rows "
                + "from [Sales]")
            .returnsGrid(

            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread].[Muffins]}\n"
                + "Row #0: 3,497\n"
                + "Row #1: 3,497\n" );
    }

    @Test
    void testVisualTotalsOutOfOrder(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns, "
                + "{VisualTotals("
                + "    {[Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Muffins]},"
                + "    \"**Subtotal - *\")} on rows "
                + "from [Sales]")
            .returnsGrid(

            // Note that [*Subtotal - Bread] 3497 does not include 815 for
            // bagels.
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread].[Bagels]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread].[Muffins]}\n"
                + "Row #0: 815\n"
                + "Row #1: 3,497\n"
                + "Row #2: 3,497\n" );
    }

    @Test
    void testVisualTotalsGrandparentsAndOutOfOrder(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns, "
                + "{VisualTotals("
                + "    {[Product].[All Products].[Food],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],"
                + "     [Product].[All Products].[Food].[Frozen Foods].[Breakfast Foods],"
                + "     [Product].[All Products].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Golden],"
                + "     [Product].[All Products].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Big Time],"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Muffins]},"
                + "    \"**Subtotal - *\")} on rows "
                + "from [Sales]")
            .returnsGrid(

            // Note:
            // [*Subtotal - Food]  = 4513 = 815 + 311 + 3497
            // [*Subtotal - Bread] = 815, does not include muffins
            // [*Subtotal - Breakfast Foods] = 311 = 110 + 201, includes
            //     grandchildren
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Food]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread].[Bagels]}\n"
                + "{[Product].[Product].[Food].[Frozen Foods].[Breakfast Foods]}\n"
                + "{[Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Golden]}\n"
                + "{[Product].[Product].[Food].[Frozen Foods].[Breakfast Foods].[Pancake Mix].[Big Time]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread].[Muffins]}\n"
                + "Row #0: 4,623\n"
                + "Row #1: 815\n"
                + "Row #2: 815\n"
                + "Row #3: 311\n"
                + "Row #4: 110\n"
                + "Row #5: 201\n"
                + "Row #6: 3,497\n" );
    }

    @Test
    void testVisualTotalsCrossjoin(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "VisualTotals(Crossjoin([Gender].Members, [Store].children))")
            .throwsMessage( "Argument to 'VisualTotals' function must be a set of members; got set of tuples." );
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-615">MONDRIAN-615</a>,
     * "VisualTotals doesn't work for the all member".
     */
    @Test
    void testVisualTotalsAll(Context<?> context) {
        final String query =
            "SELECT \n"
                + "  {[Measures].[Unit Sales]} ON 0, \n"
                + "  VisualTotals(\n"
                + "    {[Customers].[All Customers],\n"
                + "     [Customers].[USA],\n"
                + "     [Customers].[USA].[CA],\n"
                + "     [Customers].[USA].[OR]}) ON 1\n"
                + "FROM [Sales]";
        assertThatQuery(context.getConnectionWithDefaultRole(),
            query)
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "{[Customers].[Customers].[USA].[CA]}\n"
                + "{[Customers].[Customers].[USA].[OR]}\n"
                + "Row #0: 142,407\n"
                + "Row #1: 142,407\n"
                + "Row #2: 74,748\n"
                + "Row #3: 67,659\n" );

        // Check captions
        final Result result = executeQuery(context.getConnectionWithDefaultRole(), query);
        final List<Position> positionList = result.getAxes()[ 1 ].getPositions();
        assertEquals( "All Customers", positionList.get( 0 ).get( 0 ).getCaption() );
        assertEquals( "USA", positionList.get( 1 ).get( 0 ).getCaption() );
        assertEquals( "CA", positionList.get( 2 ).get( 0 ).getCaption() );
    }

    /**
     * Test case involving a named set and query pivoted. Suggested in
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-615">MONDRIAN-615</a>,
     * "VisualTotals doesn't work for the all member".
     */
    @Test
    void testVisualTotalsWithNamedSetAndPivot(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH SET [CA_OR] AS\n"
                + "    VisualTotals(\n"
                + "        {[Customers].[All Customers],\n"
                + "         [Customers].[USA],\n"
                + "         [Customers].[USA].[CA],\n"
                + "         [Customers].[USA].[OR]})\n"
                + "SELECT \n"
                + "    Drilldownlevel({[Time].[1997]}) ON 0, \n"
                + "    [CA_OR] ON 1 \n"
                + "FROM [Sales] ")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1997]}\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "{[Time].[Time].[1997].[Q2]}\n"
                + "{[Time].[Time].[1997].[Q3]}\n"
                + "{[Time].[Time].[1997].[Q4]}\n"
                + "Axis #2:\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "{[Customers].[Customers].[USA].[CA]}\n"
                + "{[Customers].[Customers].[USA].[OR]}\n"
                + "Row #0: 142,407\n"
                + "Row #0: 36,177\n"
                + "Row #0: 33,131\n"
                + "Row #0: 35,310\n"
                + "Row #0: 37,789\n"
                + "Row #1: 142,407\n"
                + "Row #1: 36,177\n"
                + "Row #1: 33,131\n"
                + "Row #1: 35,310\n"
                + "Row #1: 37,789\n"
                + "Row #2: 74,748\n"
                + "Row #2: 16,890\n"
                + "Row #2: 18,052\n"
                + "Row #2: 18,370\n"
                + "Row #2: 21,436\n"
                + "Row #3: 67,659\n"
                + "Row #3: 19,287\n"
                + "Row #3: 15,079\n"
                + "Row #3: 16,940\n"
                + "Row #3: 16,353\n" );

        // same query, swap axes
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH SET [CA_OR] AS\n"
                + "    VisualTotals(\n"
                + "        {[Customers].[All Customers],\n"
                + "         [Customers].[USA],\n"
                + "         [Customers].[USA].[CA],\n"
                + "         [Customers].[USA].[OR]})\n"
                + "SELECT \n"
                + "    [CA_OR] ON 0,\n"
                + "    Drilldownlevel({[Time].[1997]}) ON 1\n"
                + "FROM [Sales] ")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "{[Customers].[Customers].[USA].[CA]}\n"
                + "{[Customers].[Customers].[USA].[OR]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1997]}\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "{[Time].[Time].[1997].[Q2]}\n"
                + "{[Time].[Time].[1997].[Q3]}\n"
                + "{[Time].[Time].[1997].[Q4]}\n"
                + "Row #0: 142,407\n"
                + "Row #0: 142,407\n"
                + "Row #0: 74,748\n"
                + "Row #0: 67,659\n"
                + "Row #1: 36,177\n"
                + "Row #1: 36,177\n"
                + "Row #1: 16,890\n"
                + "Row #1: 19,287\n"
                + "Row #2: 33,131\n"
                + "Row #2: 33,131\n"
                + "Row #2: 18,052\n"
                + "Row #2: 15,079\n"
                + "Row #3: 35,310\n"
                + "Row #3: 35,310\n"
                + "Row #3: 18,370\n"
                + "Row #3: 16,940\n"
                + "Row #4: 37,789\n"
                + "Row #4: 37,789\n"
                + "Row #4: 21,436\n"
                + "Row #4: 16,353\n" );
    }

    /**
     * Tests that members generated by VisualTotals have correct identity.
     *
     * <p>Testcase for <a href="http://jira.pentaho.com/browse/MONDRIAN-295">
     * bug MONDRIAN-295, "Query generated by Excel 2007 gives incorrect results"</a>.
     */
    @Test
    void testVisualTotalsIntersect(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH\n"
                + "SET [XL_Row_Dim_0] AS 'VisualTotals(Distinct(Hierarchize({Ascendants([Customers].[All Customers].[USA]), "
                + "Descendants([Customers].[All Customers].[USA])})))' \n"
                + "SELECT \n"
                + "NON EMPTY Hierarchize({[Time].[Year].members}) ON COLUMNS , \n"
                + "NON EMPTY Hierarchize(Intersect({DrilldownLevel({[Customers].[All Customers]})}, [XL_Row_Dim_0])) ON "
                + "ROWS \n"
                + "FROM [Sales] \n"
                + "WHERE ([Measures].[Store Sales])")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1997]}\n"
                + "Axis #2:\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "Row #0: 565,238.13\n"
                + "Row #1: 565,238.13\n" );
    }

    /**
     * <p>Testcase for <a href="http://jira.pentaho.com/browse/MONDRIAN-668">
     * bug MONDRIAN-668, "Intersect should return any VisualTotals members in right-hand set"</a>.
     */
    @Test
    void testVisualTotalsWithNamedSetAndPivotSameAxis(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH SET [XL_Row_Dim_0] AS\n"
                + " VisualTotals(\n"
                + "   Distinct(\n"
                + "     Hierarchize(\n"
                + "       {Ascendants([Store].[USA].[CA]),\n"
                + "        Descendants([Store].[USA].[CA])})))\n"
                + "select NON EMPTY \n"
                + "  Hierarchize(\n"
                + "    Intersect(\n"
                + "      {DrilldownLevel({[Store].[USA]})},\n"
                + "      [XL_Row_Dim_0])) ON COLUMNS\n"
                + "from [Sales] "
                + "where [Measures].[Sales count]\n")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Sales Count]}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA]}\n"
                + "{[Store].[Store].[USA].[CA]}\n"
                + "Row #0: 24,442\n"
                + "Row #0: 24,442\n" );

        // now with tuples
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH SET [XL_Row_Dim_0] AS\n"
                + " VisualTotals(\n"
                + "   Distinct(\n"
                + "     Hierarchize(\n"
                + "       {Ascendants([Store].[USA].[CA]),\n"
                + "        Descendants([Store].[USA].[CA])})))\n"
                + "select NON EMPTY \n"
                + "  Hierarchize(\n"
                + "    Intersect(\n"
                + "     [Marital Status].[M]\n"
                + "     * {DrilldownLevel({[Store].[USA]})}\n"
                + "     * [Gender].[F],\n"
                + "     [Marital Status].[M]\n"
                + "     * [XL_Row_Dim_0]\n"
                + "     * [Gender].[F])) ON COLUMNS\n"
                + "from [Sales] "
                + "where [Measures].[Sales count]\n")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Sales Count]}\n"
                + "Axis #1:\n"
                + "{[Marital Status].[Marital Status].[M], [Store].[Store].[USA], [Gender].[Gender].[F]}\n"
                + "{[Marital Status].[Marital Status].[M], [Store].[Store].[USA].[CA], [Gender].[Gender].[F]}\n"
                + "Row #0: 6,054\n"
                + "Row #0: 6,054\n" );
    }

    /**
     * <p>Testcase for <a href="http://jira.pentaho.com/browse/MONDRIAN-682">
     * bug MONDRIAN-682, "VisualTotals + Distinct-count measure gives wrong results"</a>.
     */
    @Test
    void testVisualTotalsDistinctCountMeasure(Context<?> context) {
        // distinct measure
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH SET [XL_Row_Dim_0] AS\n"
                + " VisualTotals(\n"
                + "   Distinct(\n"
                + "     Hierarchize(\n"
                + "       {Ascendants([Store].[USA].[CA]),\n"
                + "        Descendants([Store].[USA].[CA])})))\n"
                + "select NON EMPTY \n"
                + "  Hierarchize(\n"
                + "    Intersect(\n"
                + "      {DrilldownLevel({[Store].[All Stores]})},\n"
                + "      [XL_Row_Dim_0])) ON COLUMNS\n"
                + "from [HR] "
                + "where [Measures].[Number of Employees]\n")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Number of Employees]}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[All Stores]}\n"
                + "{[Store].[Store].[USA]}\n"
                + "Row #0: 193\n"
                + "Row #0: 193\n" );

        // distinct measure
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH SET [XL_Row_Dim_0] AS\n"
                + " VisualTotals(\n"
                + "   Distinct(\n"
                + "     Hierarchize(\n"
                + "       {Ascendants([Store].[USA].[CA].[Beverly Hills]),\n"
                + "        Descendants([Store].[USA].[CA].[Beverly Hills]),\n"
                + "        Ascendants([Store].[USA].[CA].[Los Angeles]),\n"
                + "        Descendants([Store].[USA].[CA].[Los Angeles])})))"
                + "select NON EMPTY \n"
                + "  Hierarchize(\n"
                + "    Intersect(\n"
                + "      {DrilldownLevel({[Store].[All Stores]})},\n"
                + "      [XL_Row_Dim_0])) ON COLUMNS\n"
                + "from [HR] "
                + "where [Measures].[Number of Employees]\n")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Number of Employees]}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[All Stores]}\n"
                + "{[Store].[Store].[USA]}\n"
                + "Row #0: 110\n"
                + "Row #0: 110\n" );

        // distinct measure on columns
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH SET [XL_Row_Dim_0] AS\n"
                + " VisualTotals(\n"
                + "   Distinct(\n"
                + "     Hierarchize(\n"
                + "       {Ascendants([Store].[USA].[CA]),\n"
                + "        Descendants([Store].[USA].[CA])})))\n"
                + "select {[Measures].[Count], [Measures].[Number of Employees]} on COLUMNS,"
                + " NON EMPTY \n"
                + "  Hierarchize(\n"
                + "    Intersect(\n"
                + "      {DrilldownLevel({[Store].[All Stores]})},\n"
                + "      [XL_Row_Dim_0])) ON ROWS\n"
                + "from [HR] ")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Count]}\n"
                + "{[Measures].[Number of Employees]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[All Stores]}\n"
                + "{[Store].[Store].[USA]}\n"
                + "Row #0: 2,316\n"
                + "Row #0: 193\n"
                + "Row #1: 2,316\n"
                + "Row #1: 193\n" );

        // distinct measure with tuples
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH SET [XL_Row_Dim_0] AS\n"
                + " VisualTotals(\n"
                + "   Distinct(\n"
                + "     Hierarchize(\n"
                + "       {Ascendants([Store].[USA].[CA]),\n"
                + "        Descendants([Store].[USA].[CA])})))\n"
                + "select NON EMPTY \n"
                + "  Hierarchize(\n"
                + "    Intersect(\n"
                + "     [Marital Status].[M]\n"
                + "     * {DrilldownLevel({[Store].[USA]})}\n"
                + "     * [Gender].[F],\n"
                + "     [Marital Status].[M]\n"
                + "     * [XL_Row_Dim_0]\n"
                + "     * [Gender].[F])) ON COLUMNS\n"
                + "from [Sales] "
                + "where [Measures].[Customer count]\n")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Customer Count]}\n"
                + "Axis #1:\n"
                + "{[Marital Status].[Marital Status].[M], [Store].[Store].[USA], [Gender].[Gender].[F]}\n"
                + "{[Marital Status].[Marital Status].[M], [Store].[Store].[USA].[CA], [Gender].[Gender].[F]}\n"
                + "Row #0: 654\n"
                + "Row #0: 654\n" );
    }

    /**
     * <p>Testcase for <a href="http://jira.pentaho.com/browse/MONDRIAN-761">
     * bug MONDRIAN-761, "VisualTotalMember cannot be cast to RolapCubeMember"</a>.
     */
    @Test
    void testVisualTotalsClassCast(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH  SET [XL_Row_Dim_0] AS\n"
                + " VisualTotals(\n"
                + "   Distinct(\n"
                + "     Hierarchize(\n"
                + "       {Ascendants([Store].[USA].[WA].[Yakima]), \n"
                + "        Descendants([Store].[USA].[WA].[Yakima]), \n"
                + "        Ascendants([Store].[USA].[WA].[Walla Walla]), \n"
                + "        Descendants([Store].[USA].[WA].[Walla Walla]), \n"
                + "        Ascendants([Store].[USA].[WA].[Tacoma]), \n"
                + "        Descendants([Store].[USA].[WA].[Tacoma]), \n"
                + "        Ascendants([Store].[USA].[WA].[Spokane]), \n"
                + "        Descendants([Store].[USA].[WA].[Spokane]), \n"
                + "        Ascendants([Store].[USA].[WA].[Seattle]), \n"
                + "        Descendants([Store].[USA].[WA].[Seattle]), \n"
                + "        Ascendants([Store].[USA].[WA].[Bremerton]), \n"
                + "        Descendants([Store].[USA].[WA].[Bremerton]), \n"
                + "        Ascendants([Store].[USA].[OR]), \n"
                + "        Descendants([Store].[USA].[OR])}))) \n"
                + " SELECT NON EMPTY \n"
                + " Hierarchize(\n"
                + "   Intersect(\n"
                + "     DrilldownMember(\n"
                + "       {{DrilldownMember(\n"
                + "         {{DrilldownMember(\n"
                + "           {{DrilldownLevel(\n"
                + "             {[Store].[All Stores]})}},\n"
                + "           {[Store].[USA]})}},\n"
                + "         {[Store].[USA].[WA]})}},\n"
                + "       {[Store].[USA].[WA].[Bremerton]}),\n"
                + "       [XL_Row_Dim_0]))\n"
                + "DIMENSION PROPERTIES \n"
                + "  PARENT_UNIQUE_NAME, \n"
                + "  [Store].[Store Name].[Store Type],\n"
                + "  [Store].[Store Name].[Store Manager],\n"
                + "  [Store].[Store Name].[Store Sqft],\n"
                + "  [Store].[Store Name].[Grocery Sqft],\n"
                + "  [Store].[Store Name].[Frozen Sqft],\n"
                + "  [Store].[Store Name].[Meat Sqft],\n"
                + "  [Store].[Store Name].[Has coffee bar],\n"
                + "  [Store].[Store Name].[Street address] ON COLUMNS \n"
                + "FROM [HR]\n"
                + "WHERE \n"
                + "  ([Measures].[Number of Employees])\n"
                + "CELL PROPERTIES\n"
                + "  VALUE,\n"
                + "  FORMAT_STRING,\n"
                + "  LANGUAGE,\n"
                + "  BACK_COLOR,\n"
                + "  FORE_COLOR,\n"
                + "  FONT_FLAGS")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Number of Employees]}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[All Stores]}\n"
                + "{[Store].[Store].[USA]}\n"
                + "{[Store].[Store].[USA].[OR]}\n"
                + "{[Store].[Store].[USA].[WA]}\n"
                + "{[Store].[Store].[USA].[WA].[Bremerton]}\n"
                + "{[Store].[Store].[USA].[WA].[Bremerton].[Store 3]}\n"
                + "{[Store].[Store].[USA].[WA].[Seattle]}\n"
                + "{[Store].[Store].[USA].[WA].[Spokane]}\n"
                + "{[Store].[Store].[USA].[WA].[Tacoma]}\n"
                + "{[Store].[Store].[USA].[WA].[Walla Walla]}\n"
                + "{[Store].[Store].[USA].[WA].[Yakima]}\n"
                + "Row #0: 419\n"
                + "Row #0: 419\n"
                + "Row #0: 136\n"
                + "Row #0: 283\n"
                + "Row #0: 62\n"
                + "Row #0: 62\n"
                + "Row #0: 62\n"
                + "Row #0: 62\n"
                + "Row #0: 74\n"
                + "Row #0: 4\n"
                + "Row #0: 19\n" );
    }

    /**
     * <p>Testcase for <a href="http://jira.pentaho.com/browse/MONDRIAN-678">
     * bug MONDRIAN-678, "VisualTotals gives UnsupportedOperationException calling getOrdinal"</a>. Key difference from
     * previous test is that there are multiple hierarchies in Named set.
     */
    @Test
    void testVisualTotalsWithNamedSetOfTuples(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH SET [XL_Row_Dim_0] AS\n"
                + " VisualTotals(\n"
                + "   Distinct(\n"
                + "     Hierarchize(\n"
                + "       {Ascendants([Customers].[All Customers].[USA].[CA].[Beverly Hills].[Ari Tweten]),\n"
                + "        Descendants([Customers].[All Customers].[USA].[CA].[Beverly Hills].[Ari Tweten]),\n"
                + "        Ascendants([Customers].[All Customers].[Mexico]),\n"
                + "        Descendants([Customers].[All Customers].[Mexico])})))\n"
                + "select NON EMPTY \n"
                + "  Hierarchize(\n"
                + "    Intersect(\n"
                + "      (DrilldownMember(\n"
                + "        {{DrilldownMember(\n"
                + "          {{DrilldownLevel(\n"
                + "            {[Customers].[All Customers]})}},\n"
                + "          {[Customers].[All Customers].[USA]})}},\n"
                + "        {[Customers].[All Customers].[USA].[CA]})),\n"
                + "        [XL_Row_Dim_0])) ON COLUMNS\n"
                + "from [Sales]\n"
                + "where [Measures].[Sales count]\n")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Sales Count]}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "{[Customers].[Customers].[USA].[CA]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Beverly Hills]}\n"
                + "Row #0: 4\n"
                + "Row #0: 4\n"
                + "Row #0: 4\n"
                + "Row #0: 4\n" );
    }

    @Test
    void testVisualTotalsLevel(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns,\n"
                + "{[Product].[All Products],\n"
                + " [Product].[All Products].[Food].[Baked Goods].[Bread],\n"
                + " VisualTotals(\n"
                + "    {[Product].[All Products].[Food].[Baked Goods].[Bread],\n"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],\n"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Muffins]},\n"
                + "     \"**Subtotal - *\")} on rows\n"
                + "from [Sales]" );
        final List<Position> rowPos = result.getAxes()[ 1 ].getPositions();
        final Member member0 = rowPos.get( 0 ).get( 0 );
        assertEquals( "All Products", member0.getName() );
        assertEquals( "(All)", member0.getLevel().getName() );
        final Member member1 = rowPos.get( 1 ).get( 0 );
        assertEquals( "Bread", member1.getName() );
        assertEquals( "Product Category", member1.getLevel().getName() );
        final Member member2 = rowPos.get( 2 ).get( 0 );
        assertEquals( "Bread", member2.getName() );
        assertEquals( "*Subtotal - Bread", member2.getCaption() );
        assertEquals( "Product Category", member2.getLevel().getName() );
        final Member member3 = rowPos.get( 3 ).get( 0 );
        assertEquals( "Bagels", member3.getName() );
        assertEquals( "Product Subcategory", member3.getLevel().getName() );
        final Member member4 = rowPos.get( 4 ).get( 0 );
        assertEquals( "Muffins", member4.getName() );
        assertEquals( "Product Subcategory", member4.getLevel().getName() );
    }

    /**
     * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-749"> MONDRIAN-749, "Cannot use visual totals
     * members in calculations"</a>.
     *
     * <p>The bug is not currently fixed, so it is a negative test case. Row #2
     * cell #1 contains an exception, but should be "**Subtotal - Bread : Product Subcategory".
     */
    @Test
    void testVisualTotalsMemberInCalculation(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member [Measures].[Foo] as\n"
                + " [Product].CurrentMember.Name || ' : ' || [Product].Level.Name\n"
                + "select {[Measures].[Unit Sales], [Measures].[Foo]} on columns,\n"
                + "{[Product].[All Products],\n"
                + " [Product].[All Products].[Food].[Baked Goods].[Bread],\n"
                + " VisualTotals(\n"
                + "    {[Product].[All Products].[Food].[Baked Goods].[Bread],\n"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Bagels],\n"
                + "     [Product].[All Products].[Food].[Baked Goods].[Bread].[Muffins]},\n"
                + "     \"**Subtotal - *\")} on rows\n"
                + "from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Foo]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[All Products]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread].[Bagels]}\n"
                + "{[Product].[Product].[Food].[Baked Goods].[Bread].[Muffins]}\n"
                + "Row #0: 266,773\n"
                + "Row #0: All Products : (All)\n"
                + "Row #1: 7,870\n"
                + "Row #1: Bread : Product Category\n"
                + "Row #2: 4,312\n"
                + "Row #2: #ERR: org.eclipse.daanse.olap.fun.DaanseEvaluationException: Could not find an aggregator in the current "
                + "evaluation context\n"
                + "Row #3: 815\n"
                + "Row #3: Bagels : Product Subcategory\n"
                + "Row #4: 3,497\n"
                + "Row #4: Muffins : Product Subcategory\n" );
    }


}
