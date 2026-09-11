/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara.
 * All Rights Reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.test;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import  org.eclipse.daanse.olap.util.Bug;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.NativeVerify;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.SchemaModifiersEmf;
import org.eclipse.daanse.test.FoodmartData;

/**
 * Tests the expressions used for calculated members. Please keep in sync
 * with the actual code used by the wizard.
 *
 * @author jhyde
 * @since 15 May, 2009
 */
@RolapContextTest(FoodmartTestInstance.class)
class CompoundSlicerTest {

    @BeforeEach
    public void beforeEach() {

    }

    @AfterEach
    public void afterEach() {
    }


    /**
     * Query that simulates a compound slicer by creating a calculated member
     * that aggregates over a set and places it in the WHERE clause.
     */
    @Test
    void testSimulatedCompoundSlicer(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatQuery(connection,
                "with\n"
                        + "  member [Measures].[Price per Unit] as\n"
                        + "    [Measures].[Store Sales] / [Measures].[Unit Sales]\n"
                        + "  set [Top Products] as\n"
                        + "    TopCount(\n"
                        + "      [Product].[Brand Name].Members,\n"
                        + "      3,\n"
                        + "      ([Measures].[Unit Sales], [Time].[1997].[Q3]))\n"
                        + "  member [Product].[Top] as\n"
                        + "    Aggregate([Top Products])\n"
                        + "select {\n"
                        + "  [Measures].[Unit Sales],\n"
                        + "  [Measures].[Price per Unit]} on 0,\n"
                        + " [Gender].Children * [Marital Status].Children on 1\n"
                        + "from [Sales]\n"
                        + "where ([Product].[Top], [Time].[1997].[Q3])").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Top], [Time].[Time].[1997].[Q3]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "{[Measures].[Price per Unit]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
                        + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S]}\n"
                        + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M]}\n"
                        + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S]}\n"
                        + "Row #0: 779\n"
                        + "Row #0: 2.40\n"
                        + "Row #1: 811\n"
                        + "Row #1: 2.24\n"
                        + "Row #2: 829\n"
                        + "Row #2: 2.23\n"
                        + "Row #3: 886\n"
                        + "Row #3: 2.25\n");

        // Now the equivalent query, using a set in the slicer.
        assertThatQuery(connection,
                "with\n"
                        + "  member [Measures].[Price per Unit] as\n"
                        + "    [Measures].[Store Sales] / [Measures].[Unit Sales]\n"
                        + "  set [Top Products] as\n"
                        + "    TopCount(\n"
                        + "      [Product].[Brand Name].Members,\n"
                        + "      3,\n"
                        + "      ([Measures].[Unit Sales], [Time].[1997].[Q3]))\n"
                        + "select {\n"
                        + "  [Measures].[Unit Sales],\n"
                        + "  [Measures].[Price per Unit]} on 0,\n"
                        + " [Gender].Children * [Marital Status].Children on 1\n"
                        + "from [Sales]\n"
                        + "where [Top Products] * [Time].[1997].[Q3]").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos], [Time].[Time].[1997].[Q3]}\n"
                        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Tell Tale], [Time].[Time].[1997].[Q3]}\n"
                        + "{[Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Ebony], [Time].[Time].[1997].[Q3]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "{[Measures].[Price per Unit]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
                        + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S]}\n"
                        + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M]}\n"
                        + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S]}\n"
                        + "Row #0: 779\n"
                        + "Row #0: 2.40\n"
                        + "Row #1: 811\n"
                        + "Row #1: 2.24\n"
                        + "Row #2: 829\n"
                        + "Row #2: 2.23\n"
                        + "Row #3: 886\n"
                        + "Row #3: 2.25\n");
    }

    /**
     * Tests compound slicer with EXCEPT.
     *
     * <p>Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-637">
     * Bug MONDRIAN-637, "Using Except in the slicer makes no sense"</a>.
     */
    @Test
    void testCompoundSlicerExcept(Context<?> context) {
        Connection  connection = context.getConnectionWithDefaultRole();
        final String expected =
                "Axis #0:\n"
                        + "{[Promotion Media].[Promotion Media].[Bulk Mail]}\n"
                        + "{[Promotion Media].[Promotion Media].[Cash Register Handout]}\n"
                        + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio]}\n"
                        + "{[Promotion Media].[Promotion Media].[Daily Paper, Radio, TV]}\n"
                        + "{[Promotion Media].[Promotion Media].[In-Store Coupon]}\n"
                        + "{[Promotion Media].[Promotion Media].[No Media]}\n"
                        + "{[Promotion Media].[Promotion Media].[Product Attachment]}\n"
                        + "{[Promotion Media].[Promotion Media].[Radio]}\n"
                        + "{[Promotion Media].[Promotion Media].[Street Handout]}\n"
                        + "{[Promotion Media].[Promotion Media].[Sunday Paper]}\n"
                        + "{[Promotion Media].[Promotion Media].[Sunday Paper, Radio]}\n"
                        + "{[Promotion Media].[Promotion Media].[Sunday Paper, Radio, TV]}\n"
                        + "{[Promotion Media].[Promotion Media].[TV]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 259,035\n"
                        + "Row #1: 127,871\n"
                        + "Row #2: 131,164\n";

        // slicer expression that inherits [Promotion Media] member from context
        assertThatQuery(connection,
                "select [Measures].[Unit Sales] on 0,\n"
                        + " [Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where Except(\n"
                        + "  [Promotion Media].Children,\n"
                        + "  {[Promotion Media].[Daily Paper]})").returnsGrid( expected);

        // similar query, but don't assume that [Promotion Media].CurrentMember
        // = [Promotion Media].[All Media]
        assertThatQuery(connection,
                "select [Measures].[Unit Sales] on 0,\n"
                        + " [Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where Except(\n"
                        + "  [Promotion Media].[All Media].Children,\n"
                        + "  {[Promotion Media].[Daily Paper]})").returnsGrid( expected);

        // reference query, computing the same numbers a different way
        assertThatQuery(connection,
                "with member [Promotion Media].[Except Daily Paper] as\n"
                        + "  Aggregate(\n"
                        + "    Except(\n"
                        + "      [Promotion Media].Children,\n"
                        + "      {[Promotion Media].[Daily Paper]}))\n"
                        + "select [Measures].[Unit Sales]\n"
                        + " * {[Promotion Media],\n"
                        + "    [Promotion Media].[Daily Paper],\n"
                        + "    [Promotion Media].[Except Daily Paper]} on 0,\n"
                        + " [Gender].Members on 1\n"
                        + "from [Sales]").returnsGrid(
                "Axis #0:\n"
                        + "{}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales], [Promotion Media].[Promotion Media].[All Media]}\n"
                        + "{[Measures].[Unit Sales], [Promotion Media].[Promotion Media].[Daily Paper]}\n"
                        + "{[Measures].[Unit Sales], [Promotion Media].[Promotion Media].[Except Daily Paper]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 266,773\n"
                        + "Row #0: 7,738\n"
                        + "Row #0: 259,035\n"
                        + "Row #1: 131,558\n"
                        + "Row #1: 3,687\n"
                        + "Row #1: 127,871\n"
                        + "Row #2: 135,215\n"
                        + "Row #2: 4,051\n"
                        + "Row #2: 131,164\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CompoundSlicerTestModifier1.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testCompoundSlicerWithCellFormatter(Context<?> context) {
        /*
        String xmlMeasure =
                "<Measure name='Unit Sales Foo Bar' column='unit_sales'\n"
                        + "    aggregator='sum' formatString='Standard' formatter='"
                        + UdfTest.FooBarCellFormatter.class.getName()
                        + "'/>";
        ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
                "Sales", null, xmlMeasure, null, null));
         */
        // the cell formatter for the measure should still be used
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select from sales where "
                        + " measures.[Unit Sales Foo Bar] * Gender.Gender.Gender.members ").returnsGrid(
                "Axis #0:\n"
                        + "{[Measures].[Unit Sales Foo Bar], [Gender].[Gender].[F]}\n"
                        + "{[Measures].[Unit Sales Foo Bar], [Gender].[Gender].[M]}\n"
                        + "foo266773.0bar");

        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select from sales where "
                        + " Gender.Gender.Gender.members * measures.[Unit Sales Foo Bar]").returnsGrid(
                "Axis #0:\n"
                        + "{[Gender].[Gender].[F], [Measures].[Unit Sales Foo Bar]}\n"
                        + "{[Gender].[Gender].[M], [Measures].[Unit Sales Foo Bar]}\n"
                        + "foo266773.0bar");
    }


    @Test
    void testMondrian1226(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with set a as '([Time].[Time].[1997].[Q1] : [Time].[Time].[1997].[Q2])'\n"
                        +    "member Time.Time.x as Aggregate(a,[Measures].[Store Sales])\n"
                        +    "member Measures.x1 as ([Time].[Time].[1997].[Q1],"
                        + "[Measures].[Store Sales])\n"
                        +    "member Measures.x2 as ([Time].[Time].[1997].[Q2],"
                        + " [Measures].[Store Sales])\n"
                        +    "set products as TopCount("
                        + "Product.Product.[Product Name].Members,1,Measures.[Store Sales])\n"
                        +    "SELECT\n"
                        +    "NON EMPTY products ON 1,\n"
                        +    "NON EMPTY {[Measures].[Store Sales], "
                        + "Measures.x1, Measures.x2} ON 0\n"
                        +    "FROM [Sales]\n"
                        +    "where ([Time].[Time].[1997].[Q1] : [Time].[Time].[1997].[Q2])").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[1997].[Q1]}\n"
                        + "{[Time].[Time].[1997].[Q2]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Store Sales]}\n"
                        + "{[Measures].[x1]}\n"
                        + "{[Measures].[x2]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Food].[Eggs].[Eggs].[Eggs]"
                        + ".[Urban].[Urban Small Eggs]}\n"
                        + "Row #0: 497.42\n"
                        + "Row #0: 235.62\n"
                        + "Row #0: 261.80\n");
    }

    public void _testMondrian1226Variation(Context<?> context) {
        // currently broke.  Below are two queries with two dimensions
        // in the compound slicer.

        //  The first has a measure which overrides the Time context,
        // and gives expected results (since the Time dimension is
        // the "placeholder" dimension.
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with member measures.HalfTime as '[Time].[1997].[Q1]/2'"
                        + " select measures.HalfTime on 0 from sales where "
                        + "({[Time].[1997].[Q1] : [Time].[1997].[Q2]} * gender.[All Gender]) ").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[1997].[Q1], [Gender].[All Gender]}\n"
                        + "{[Time].[1997].[Q2], [Gender].[All Gender]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[HalfTime]}\n"
                        + "Row #0: 33,146\n");

        // The second query has a measure overriding gender, which
        // fails since context is not set appropriately for gender.
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with member measures.HalfMan as 'Gender.m/2'"
                        +    " select measures.HalfMan on 0 from sales where "
                        +    "({[Time].[1997].[Q1] : [Time].[1997].[Q2]} "
                        + "* gender.[All Gender]) ").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[1997].[Q1], [Gender].[M]}\n"
                        + "{[Time].[1997].[Q2], [Gender].[M]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[HalfMan]}\n"
                        + "Row #0: 32,500\n");
    }


    /**
     * Tests a query with a compond slicer over tuples. (Multiple rows, each
     * of which has multiple members.)
     */
    @Test
    void testCompoundSlicerOverTuples(Context<?> context) {
        // reference query
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "    TopCount(\n"
                        + "      [Product].[Product Category].Members\n"
                        + "      * [Customers].[City].Members,\n"
                        + "      10) on 1\n"
                        + "from [Sales]\n"
                        + "where [Time].[1997].[Q3]").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[1997].[Q3]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Customers].[Customers].[Canada].[BC].[Burnaby]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Customers].[Customers].[Canada].[BC].[Cliffside]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Customers].[Customers].[Canada].[BC].[Haney]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Customers].[Customers].[Canada].[BC].[Ladner]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Customers].[Customers].[Canada].[BC].[Langford]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Customers].[Customers].[Canada].[BC].[Langley]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Customers].[Customers].[Canada].[BC].[Metchosin]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Customers].[Customers].[Canada].[BC].[N. Vancouver]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Customers].[Customers].[Canada].[BC].[Newton]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Customers].[Customers].[Canada].[BC].[Oak Bay]}\n"
                        + "Row #0: \n"
                        + "Row #1: \n"
                        + "Row #2: \n"
                        + "Row #3: \n"
                        + "Row #4: \n"
                        + "Row #5: \n"
                        + "Row #6: \n"
                        + "Row #7: \n"
                        + "Row #8: \n"
                        + "Row #9: \n");

        // The actual query. Note that the set in the slicer has two dimensions.
        // This could not be expressed using calculated members and the
        // Aggregate function.
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with\n"
                        + "  member [Measures].[Price per Unit] as\n"
                        + "    [Measures].[Store Sales] / [Measures].[Unit Sales]\n"
                        + "  set [Top Product Cities] as\n"
                        + "    TopCount(\n"
                        + "      [Product].[Product Category].Members\n"
                        + "      * [Customers].[City].Members,\n"
                        + "      3,\n"
                        + "      ([Measures].[Unit Sales], [Time].[1997].[Q3]))\n"
                        + "select {\n"
                        + "  [Measures].[Unit Sales],\n"
                        + "  [Measures].[Price per Unit]} on 0,\n"
                        + " [Gender].Children * [Marital Status].Children on 1\n"
                        + "from [Sales]\n"
                        + "where [Top Product Cities] * [Time].[1997].[Q3]").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Food].[Snack Foods].[Snack Foods], [Customers].[Customers].[USA].[WA].[Spokane], [Time].[Time].[1997].[Q3]}\n"
                        + "{[Product].[Product].[Food].[Produce].[Vegetables], [Customers].[Customers].[USA].[WA].[Spokane], [Time].[Time].[1997].[Q3]}\n"
                        + "{[Product].[Product].[Food].[Snack Foods].[Snack Foods], [Customers].[Customers].[USA].[WA].[Puyallup], [Time].[Time].[1997].[Q3]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "{[Measures].[Price per Unit]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
                        + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S]}\n"
                        + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M]}\n"
                        + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S]}\n"
                        + "Row #0: 483\n"
                        + "Row #0: 2.21\n"
                        + "Row #1: 419\n"
                        + "Row #1: 2.21\n"
                        + "Row #2: 422\n"
                        + "Row #2: 2.22\n"
                        + "Row #3: 332\n"
                        + "Row #3: 2.20\n");
    }

    /**
     * Tests that if the slicer contains zero members, all cells are null.
     */
    @Test
    void testEmptySetSlicerReturnsNull(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Product].Children on 1\n"
                        + "from [Sales]\n"
                        + "where {}").returnsGrid(
                "Axis #0:\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "{[Product].[Product].[Food]}\n"
                        + "{[Product].[Product].[Non-Consumable]}\n"
                        + "Row #0: \n"
                        + "Row #1: \n"
                        + "Row #2: \n");
    }

    /**
     * Tests that if the slicer is calculated using an expression and contains
     * zero members, all cells are null.
     */
    @Test
    void testEmptySetSlicerViaExpressionReturnsNull(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Product].Children on 1\n"
                        + "from [Sales]\n"
                        + "where filter([Gender].members * [Marital Status].members, 1 = 0)").returnsGrid(
                "Axis #0:\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "{[Product].[Product].[Food]}\n"
                        + "{[Product].[Product].[Non-Consumable]}\n"
                        + "Row #0: \n"
                        + "Row #1: \n"
                        + "Row #2: \n");
    }

    /**
     * Test case for a basic query with more than one member of the same
     * hierarchy in the WHERE clause.
     */
    @Test
    void testCompoundSlicer(Context<?> context) {
        // Reference query.
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where {[Product].[Drink]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 24,597\n"
                        + "Row #1: 12,202\n"
                        + "Row #2: 12,395\n");
        // Reference query.
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where {[Product].[Food]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Food]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 191,940\n"
                        + "Row #1: 94,814\n"
                        + "Row #2: 97,126\n");

        // Sum members at same level.
        // Note that 216,537 = 24,597 (drink) + 191,940 (food).
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where {[Product].[Drink], [Product].[Food]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "{[Product].[Product].[Food]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 216,537\n"
                        + "Row #1: 107,016\n"
                        + "Row #2: 109,521\n");

        // sum list that contains duplicates
        // duplicates are ignored (checked SSAS 2005)
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where {[Product].[Drink], [Product].[Food], [Product].[Drink]}").returnsGrid(
                Bug.Bug555Fixed
                        ? "Axis #0:\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "{[Product].[Product].[Food]}\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 241,134, 241,134, 241,134\n"
                        + "Row #1: 119,218, 119,218, 119,218\n"
                        + "Row #2: 121,916, 121,916, 121,916\n"
                        : "Axis #0:\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "{[Product].[Product].[Food]}\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 241,134\n"
                        + "Row #1: 119,218\n"
                        + "Row #2: 121,916\n");

        // sum list that contains a null member -
        // null member is ignored;
        // confirmed behavior with ssas 2005
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where {[Product].[All Products].Parent, [Product].[Food], [Product].[Drink]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Food]}\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 216,537\n"
                        + "Row #1: 107,016\n"
                        + "Row #2: 109,521\n");

        // Reference query.
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where {\n"
                        + "  [Product].[Drink],\n"
                        + "  [Product].[Food].[Dairy]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "{[Product].[Product].[Food].[Dairy]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 37,482\n"
                        + "Row #1: 18,715\n"
                        + "Row #2: 18,767\n");

        // Sum list that contains a member and one of its children;
        // SSAS 2005 doesn't simply sum them: it behaves behavior as if
        // predicates are pushed down to the fact table. Mondrian double-counts,
        // and that is a bug.
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where {\n"
                        + "  [Product].[Drink],\n"
                        + "  [Product].[Food].[Dairy],\n"
                        + "  [Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]}").returnsGrid(
                Bug.Bug555Fixed
                        ? "Axis #0:\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "{[Product].[Product].[Food].[Dairy]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 37,482\n"
                        + "Row #1: 18,715\n"
                        + "Row #2: 18.767\n"
                        : "Axis #0:\n"
                        + "{[Product].[Product].[Drink]}\n"
                        + "{[Product].[Product].[Food].[Dairy]}\n"
                        + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 39,165\n"
                        + "Row #1: 19,532\n"
                        + "Row #2: 19,633\n");

        // The correct behavior of the aggregate function is to double-count.
        // SSAS 2005 and Mondrian give the same behavior.
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with member [Product].[Foo] as\n"
                        + "  Aggregate({\n"
                        + "    [Product].[Drink],\n"
                        + "    [Product].[Food].[Dairy],\n"
                        + "    [Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]})\n"
                        + "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where [Product].[Foo]\n").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Foo]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 39,165\n"
                        + "Row #1: 19,532\n"
                        + "Row #2: 19,633\n");
    }

    /**
     * Slicer that is a member expression that evaluates to null.
     * SSAS 2005 allows this, and returns null cells.
     */
    @Test
    void testSlicerContainsNullMember(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where [Product].Parent").returnsGrid(
                "Axis #0:\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: \n"
                        + "Row #1: \n"
                        + "Row #2: \n");
    }

    /**
     * Slicer that is literal null.
     * SSAS 2005 allows this, and returns null cells; Mondrian currently gives
     * an error.
     */
    @Disabled //has not been fixed during creating Daanse project
    @Test
    void testSlicerContainsLiteralNull(Context<?> context) {
        final String mdx =
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where null";
        if (Bug.Ssas2005Compatible) {
            // SSAS returns a cell set containing null cells.
            assertThatQuery(context.getConnectionWithDefaultRole(),
                    mdx).returnsGrid(
                    "xxx");
        } else {
            // Mondrian gives an error. This is not unreasonable. It is very
            // low priority to make Mondrian consistent with SSAS 2005 in this
            // behavior.
            assertThatQuery(context.getConnectionWithDefaultRole(), mdx)
                .throwsMessage("Function does not support NULL member parameter");
        }
    }

    /**
     * Slicer that is a tuple and one of the members evaluates to null;
     * that makes it a null tuple, and it is eliminated from the list.
     * SSAS 2005 allows this, and returns null cells.
     */
    @Test
    void testSlicerContainsPartiallyNullMember(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select [Measures].[Unit Sales] on 0,\n"
                        + "[Gender].Members on 1\n"
                        + "from [Sales]\n"
                        + "where ([Product].Parent, [Store].[USA].[CA])").returnsGrid(
                "Axis #0:\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: \n"
                        + "Row #1: \n"
                        + "Row #2: \n");
    }

    /**
     * Compound slicer with distinct-count measure.
     */
    @Test
    void testCompoundSlicerWithDistinctCount(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        // Reference query.
        assertThatQuery(connection,
                "select [Measures].[Customer Count] on 0,\n"
                        + "  {[Store].[USA].[CA], [Store].[USA].[OR].[Portland]}\n"
                        + "  * {([Product].[Food], [Time].[1997].[Q1]),\n"
                        + "    ([Product].[Drink], [Time].[1997].[Q2].[4])} on 1\n"
                        + "from [Sales]\n").returnsGrid(
                "Axis #0:\n"
                        + "{}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Customer Count]}\n"
                        + "Axis #2:\n"
                        + "{[Store].[Store].[USA].[CA], [Product].[Product].[Food], [Time].[Time].[1997].[Q1]}\n"
                        + "{[Store].[Store].[USA].[CA], [Product].[Product].[Drink], [Time].[Time].[1997].[Q2].[4]}\n"
                        + "{[Store].[Store].[USA].[OR].[Portland], [Product].[Product].[Food], [Time].[Time].[1997].[Q1]}\n"
                        + "{[Store].[Store].[USA].[OR].[Portland], [Product].[Product].[Drink], [Time].[Time].[1997].[Q2].[4]}\n"
                        + "Row #0: 1,069\n"
                        + "Row #1: 155\n"
                        + "Row #2: 332\n"
                        + "Row #3: 48\n");
        // The figures look reasonable, because:
        //  332 + 48 = 380 > 352
        //  1069 + 155 = 1224 > 1175
        assertThatQuery(connection,
                "select [Measures].[Customer Count] on 0,\n"
                        + "{[Store].[USA].[CA], [Store].[USA].[OR].[Portland]} on 1\n"
                        + "from [Sales]\n"
                        + "where {\n"
                        + "  ([Product].[Food], [Time].[1997].[Q1]),\n"
                        + "  ([Product].[Drink], [Time].[1997].[Q2].[4])}").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Food], [Time].[Time].[1997].[Q1]}\n"
                        + "{[Product].[Product].[Drink], [Time].[Time].[1997].[Q2].[4]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Customer Count]}\n"
                        + "Axis #2:\n"
                        + "{[Store].[Store].[USA].[CA]}\n"
                        + "{[Store].[Store].[USA].[OR].[Portland]}\n"
                        + "Row #0: 1,175\n"
                        + "Row #1: 352\n");
    }

    /**
     * Tests compound slicer, and other rollups, with AVG function.
     *
     * <p>Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-675">
     * Bug MONDRIAN-675,
     * "Allow rollup of measures based on AVG aggregate function"</a>.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CompoundSlicerTestModifier2.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRollupAvg(Context<?> context) {
        /*
        ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
                "Sales",
                null,
                "<Measure name='Avg Unit Sales' aggregator='avg' column='unit_sales'/>\n"
                        + "<Measure name='Count Unit Sales' aggregator='count' column='unit_sales'/>\n"
                        + "<Measure name='Sum Unit Sales' aggregator='sum' column='unit_sales'/>\n",
                null,
                null));
         */
        // basic query with avg
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select from [Sales]\n"
                        + "where [Measures].[Avg Unit Sales]").returnsGrid(
                "Axis #0:\n"
                        + "{[Measures].[Avg Unit Sales]}\n"
                        + "3.072");

        // roll up using compound slicer
        // (should give a real value, not an error)
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatQuery(connection,
                "select from [Sales]\n"
                        + "where [Measures].[Avg Unit Sales]\n"
                        + "   * {[Customers].[USA].[OR], [Customers].[USA].[CA]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Measures].[Avg Unit Sales], [Customers].[Customers].[USA].[OR]}\n"
                        + "{[Measures].[Avg Unit Sales], [Customers].[Customers].[USA].[CA]}\n"
                        + "3.092");

        // roll up using a named set
        assertThatQuery(connection,
                "with member [Customers].[OR and CA] as Aggregate(\n"
                        + " {[Customers].[USA].[OR], [Customers].[USA].[CA]})\n"
                        + "select from [Sales]\n"
                        + "where ([Measures].[Avg Unit Sales], [Customers].[OR and CA])").returnsGrid(
                "Axis #0:\n"
                        + "{[Measures].[Avg Unit Sales], [Customers].[Customers].[OR and CA]}\n"
                        + "3.092");
    }

    /**
     * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-899">
     * Bug MONDRIAN-899,
     * "Order() function does not work properly together with WHERE clause"</a>.
     */
    @Test
    void testBugMondrian899(Context<?> context) {
        final String expected =
                "Axis #0:\n"
                        + "{[Time].[Time].[1997].[Q1].[2]}\n"
                        + "{[Time].[Time].[1997].[Q1].[3]}\n"
                        + "{[Time].[Time].[1997].[Q2].[4]}\n"
                        + "{[Time].[Time].[1997].[Q2].[5]}\n"
                        + "{[Time].[Time].[1997].[Q2].[6]}\n"
                        + "{[Time].[Time].[1997].[Q3].[7]}\n"
                        + "{[Time].[Time].[1997].[Q3].[8]}\n"
                        + "{[Time].[Time].[1997].[Q3].[9]}\n"
                        + "{[Time].[Time].[1997].[Q4].[10]}\n"
                        + "{[Time].[Time].[1997].[Q4].[11]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Wildon Cameron]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Emily Barela]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Dauna Barton]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Mona Vigil]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Linda Combs]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Eric Winters]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Jack Zucconi]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Luann Crawford]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Suzanne Davis]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Lucy Flowers]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Donna Weisinger]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Stanley Marks]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[James Short]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Curtis Pollard]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Dawn Laner]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Patricia Towns]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Puyallup].[William Wade]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Lorriene Weathers]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Grace McLaughlin]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Edna Woodson]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Harry Torphy]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Anne Allard]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Bonnie Staley]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Olympia].[Patricia Gervasi]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Shirley Gottbehuet]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Puyallup].[Jeremy Styers]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Beth Ohnheiser]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Bremerton].[Harold Powers]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Daniel Thompson]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Fran McEvilly]}\n"
                        + "Row #0: 327\n"
                        + "Row #1: 323\n"
                        + "Row #2: 319\n"
                        + "Row #3: 308\n"
                        + "Row #4: 305\n"
                        + "Row #5: 296\n"
                        + "Row #6: 296\n"
                        + "Row #7: 295\n"
                        + "Row #8: 291\n"
                        + "Row #9: 289\n"
                        + "Row #10: 285\n"
                        + "Row #11: 284\n"
                        + "Row #12: 281\n"
                        + "Row #13: 279\n"
                        + "Row #14: 279\n"
                        + "Row #15: 278\n"
                        + "Row #16: 277\n"
                        + "Row #17: 271\n"
                        + "Row #18: 268\n"
                        + "Row #19: 266\n"
                        + "Row #20: 265\n"
                        + "Row #21: 264\n"
                        + "Row #22: 260\n"
                        + "Row #23: 251\n"
                        + "Row #24: 250\n"
                        + "Row #25: 249\n"
                        + "Row #26: 249\n"
                        + "Row #27: 248\n"
                        + "Row #28: 247\n"
                        + "Row #29: 247\n";
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
                        + "  Subset(Order([Customers].[Name].Members, [Measures].[Unit Sales], BDESC), 10.0, 30.0) ON ROWS \n"
                        + "from [Sales] \n"
                        + "where ([Time].[1997].[Q1].[2] : [Time].[1997].[Q4].[11])").returnsGrid(
                expected);

        // Equivalent query.
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
                        + "  Tail(\n"
                        + "    TopCount([Customers].[Name].Members, 40, [Measures].[Unit Sales]),\n"
                        + "    30) ON ROWS \n"
                        + "from [Sales] \n"
                        + "where ([Time].[1997].[Q1].[2] : [Time].[1997].[Q4].[11])").returnsGrid(
                expected);
    }

    // similar to MONDRIAN-899 testcase
    @Test
    void testTopCount(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
                        + "  TopCount([Customers].[USA].[WA].[Spokane].Children, 10, [Measures].[Unit Sales]) ON ROWS \n"
                        + "from [Sales] \n"
                        + "where ([Time].[1997].[Q1].[2] : [Time].[1997].[Q1].[3])").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[1997].[Q1].[2]}\n"
                        + "{[Time].[Time].[1997].[Q1].[3]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Grace McLaughlin]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[George Todero]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Matt Bellah]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Mary Francis Benigar]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Lucy Flowers]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[David Hassard]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Dauna Barton]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Dora Sims]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Joann Mramor]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Mike Madrid]}\n"
                        + "Row #0: 131\n"
                        + "Row #1: 129\n"
                        + "Row #2: 113\n"
                        + "Row #3: 103\n"
                        + "Row #4: 95\n"
                        + "Row #5: 94\n"
                        + "Row #6: 92\n"
                        + "Row #7: 85\n"
                        + "Row #8: 79\n"
                        + "Row #9: 79\n");
    }


    @Test
    void testTopCountAllSlicers(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
                        + "  TopCount([Customers].[USA].[WA].[Spokane].Children, 10, [Measures].[Unit Sales]) ON ROWS \n"
                        + "from [Sales] \n"
                        + "where {[Time].[1997].[Q1].[2] : [Time].[1997].[Q1].[3]}*{[Product].[All Products]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[1997].[Q1].[2], [Product].[Product].[All Products]}\n"
                        + "{[Time].[Time].[1997].[Q1].[3], [Product].[Product].[All Products]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Grace McLaughlin]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[George Todero]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Matt Bellah]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Mary Francis Benigar]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Lucy Flowers]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[David Hassard]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Dauna Barton]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Dora Sims]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Joann Mramor]}\n"
                        + "{[Customers].[Customers].[USA].[WA].[Spokane].[Mike Madrid]}\n"
                        + "Row #0: 131\n"
                        + "Row #1: 129\n"
                        + "Row #2: 113\n"
                        + "Row #3: 103\n"
                        + "Row #4: 95\n"
                        + "Row #5: 94\n"
                        + "Row #6: 92\n"
                        + "Row #7: 85\n"
                        + "Row #8: 79\n"
                        + "Row #9: 79\n");
    }

    /**
     * Test case for the support of native top count with aggregated measures.
     * This version puts the range in a calculated member.
     */

    @Test
    void testTopCountWithAggregatedMemberCMRange(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with set TO_AGGREGATE as '([Time].[Time].[1997].[Q1] : [Time].[Time].[1997].[Q2])'\n"
                        + "member Time.Time.x as Aggregate(TO_AGGREGATE, [Measures].[Store Sales])\n"
                        + "member Measures.x1 as ([Time].[Time].[1997].[Q1], [Measures].[Store Sales])\n"
                        + "member Measures.x2 as ([Time].[Time].[1997].[Q2], [Measures].[Store Sales])\n"
                        + " set products as TopCount(Product.Product.[Product Name].Members, 2, Measures.[Store Sales])\n"
                        + " SELECT NON EMPTY products ON 1,\n"
                        + "NON EMPTY {[Measures].[Store Sales], Measures.x1, Measures.x2} ON 0\n"
                        + " FROM [Sales] where Time.Time.x").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[x]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Store Sales]}\n"
                        + "{[Measures].[x1]}\n"
                        + "{[Measures].[x2]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Food].[Eggs].[Eggs].[Eggs].[Urban].[Urban Small Eggs]}\n"
                        + "{[Product].[Product].[Food].[Snack Foods].[Snack Foods].[Dried Fruit].[Fort West].[Fort West Raspberry Fruit Roll]}\n"
                        + "Row #0: 497.42\n"
                        + "Row #0: 235.62\n"
                        + "Row #0: 261.80\n"
                        + "Row #1: 462.84\n"
                        + "Row #1: 226.20\n"
                        + "Row #1: 236.64\n");
    }

    /**
     * Test case for the support of native top count with aggregated measures
     * feeding the range directly to aggregate.
     */
    @Test
    void testTopCountWithAggregatedMember2(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with\n"
                        + "member Time.Time.x as Aggregate([Time].[Time].[1997].[Q1] : [Time].[Time].[1997].[Q2], [Measures].[Store Sales])\n"
                        + "member Measures.x1 as ([Time].[Time].[1997].[Q1], [Measures].[Store Sales])\n"
                        + "member Measures.x2 as ([Time].[Time].[1997].[Q2], [Measures].[Store Sales])\n"
                        + " set products as TopCount(Product.Product.[Product Name].Members, 2, Measures.[Store Sales])\n"
                        + " SELECT NON EMPTY products ON 1,\n"
                        + "NON EMPTY {[Measures].[Store Sales], Measures.x1, Measures.x2} ON 0\n"
                        + "FROM [Sales] where Time.Time.x").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[x]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Store Sales]}\n"
                        + "{[Measures].[x1]}\n"
                        + "{[Measures].[x2]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Food].[Eggs].[Eggs].[Eggs].[Urban].[Urban Small Eggs]}\n"
                        + "{[Product].[Product].[Food].[Snack Foods].[Snack Foods].[Dried Fruit].[Fort West].[Fort West Raspberry Fruit Roll]}\n"
                        + "Row #0: 497.42\n"
                        + "Row #0: 235.62\n"
                        + "Row #0: 261.80\n"
                        + "Row #1: 462.84\n"
                        + "Row #1: 226.20\n"
                        + "Row #1: 236.64\n");
    }

    /**
     * Test case for the support of native top count with aggregated measures
     * using enumerated members in a calculated member.
     */
    @Test
    void testTopCountWithAggregatedMemberEnumCMSet(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with set TO_AGGREGATE as '{[Time].[Time].[1997].[Q1] , [Time].[Time].[1997].[Q2]}'\n"
                        + "member Time.Time.x as Aggregate(TO_AGGREGATE, [Measures].[Store Sales])\n"
                        + "member Measures.x1 as ([Time].[Time].[1997].[Q1], [Measures].[Store Sales])\n"
                        + "member Measures.x2 as ([Time].[Time].[1997].[Q2], [Measures].[Store Sales])\n"
                        + " set products as TopCount(Product.[Product Name].Members, 2, Measures.[Store Sales])\n"
                        + " SELECT NON EMPTY products ON 1,\n"
                        + "NON EMPTY {[Measures].[Store Sales], Measures.x1, Measures.x2} ON 0\n"
                        + " FROM [Sales] where Time.Time.x").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[x]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Store Sales]}\n"
                        + "{[Measures].[x1]}\n"
                        + "{[Measures].[x2]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Food].[Eggs].[Eggs].[Eggs].[Urban].[Urban Small Eggs]}\n"
                        + "{[Product].[Product].[Food].[Snack Foods].[Snack Foods].[Dried Fruit].[Fort West].[Fort West Raspberry Fruit Roll]}\n"
                        + "Row #0: 497.42\n"
                        + "Row #0: 235.62\n"
                        + "Row #0: 261.80\n"
                        + "Row #1: 462.84\n"
                        + "Row #1: 226.20\n"
                        + "Row #1: 236.64\n");
    }

    /**
     * Test case for the support of native top count with aggregated measures
     * using enumerated members.
     */
    @Test
    void testTopCountWithAggregatedMemberEnumSet(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with\n"
                        + "member Time.Time.x as Aggregate({[Time].[Time].[1997].[Q1] , [Time].[Time].[1997].[Q2]}, [Measures].[Store Sales])\n"
                        + "member Measures.x1 as ([Time].[Time].[1997].[Q1], [Measures].[Store Sales])\n"
                        + "member Measures.x2 as ([Time].[Time].[1997].[Q2], [Measures].[Store Sales])\n"
                        + " set products as TopCount(Product.Product.[Product Name].Members, 2, Measures.[Store Sales])\n"
                        + " SELECT NON EMPTY products ON 1,\n"
                        + "NON EMPTY {[Measures].[Store Sales], Measures.x1, Measures.x2} ON 0\n"
                        + "FROM [Sales] where Time.Time.x").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[x]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Store Sales]}\n"
                        + "{[Measures].[x1]}\n"
                        + "{[Measures].[x2]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Food].[Eggs].[Eggs].[Eggs].[Urban].[Urban Small Eggs]}\n"
                        + "{[Product].[Product].[Food].[Snack Foods].[Snack Foods].[Dried Fruit].[Fort West].[Fort West Raspberry Fruit Roll]}\n"
                        + "Row #0: 497.42\n"
                        + "Row #0: 235.62\n"
                        + "Row #0: 261.80\n"
                        + "Row #1: 462.84\n"
                        + "Row #1: 226.20\n"
                        + "Row #1: 236.64\n");
    }

    /**
     * Test case for the support of native top count with aggregated measures
     * using yet another different format, slightly different results
     */
    @Test
    void testTopCountWithAggregatedMember5(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with\n"
                        + "member Time.Time.x as Aggregate([Time].[Time].[1997].[Q1] : [Time].[Time].[1997].[Q2], [Measures].[Store Sales])\n"
                        + "member Measures.x1 as ([Time].[Time].[1997].[Q1], [Measures].[Store Sales])\n"
                        + "member Measures.x2 as ([Time].[Time].[1997].[Q2], [Measures].[Store Sales])\n"
                        + " set products as TopCount(Product.Product.[Product Name].Members,2,(Measures.[Store Sales],Time.Time.x))\n"
                        + " SELECT NON EMPTY products ON 1,\n"
                        + "NON EMPTY {[Measures].[Store Sales], Measures.x1, Measures.x2} ON 0\n"
                        + "FROM [Sales]").returnsGrid(
                "Axis #0:\n"
                        + "{}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Store Sales]}\n"
                        + "{[Measures].[x1]}\n"
                        + "{[Measures].[x2]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Food].[Eggs].[Eggs].[Eggs].[Urban].[Urban Small Eggs]}\n"
                        + "{[Product].[Product].[Food].[Snack Foods].[Snack Foods].[Dried Fruit].[Fort West].[Fort West Raspberry Fruit Roll]}\n"
                        + "Row #0: 845.24\n"
                        + "Row #0: 235.62\n"
                        + "Row #0: 261.80\n"
                        + "Row #1: 730.80\n"
                        + "Row #1: 226.20\n"
                        + "Row #1: 236.64\n");
    }

    /**
     * Test case for the support of native top count with aggregated measures
     * using the most complex format I can think of, slightly different results.
     * We'll execute 2 queries to make sure Time.x is not member of the cache
     * key.
     */
    @Test
    void testTopCountWithAggregatedMemberCacheKey(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with\n"
                        + "member Time.Time.x as Aggregate({[Time].[Time].[1997].[Q1] , [Time].[Time].[1997].[Q2]}, [Measures].[Store Sales])\n"
                        + "member Measures.x1 as ([Time].[Time].[1997].[Q1], [Measures].[Store Sales])\n"
                        + "member Measures.x2 as ([Time].[Time].[1997].[Q2], [Measures].[Store Sales])\n"
                        + " set products as TopCount(Product.Product.[Product Name].Members, 2, Measures.[Store Sales])\n"
                        + " SELECT NON EMPTY products ON 1,\n"
                        + "NON EMPTY {[Measures].[Store Sales], Measures.x1, Measures.x2} ON 0\n"
                        + "FROM [Sales] where Time.Time.x").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[x]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Store Sales]}\n"
                        + "{[Measures].[x1]}\n"
                        + "{[Measures].[x2]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Food].[Eggs].[Eggs].[Eggs].[Urban].[Urban Small Eggs]}\n"
                        + "{[Product].[Product].[Food].[Snack Foods].[Snack Foods].[Dried Fruit].[Fort West].[Fort West Raspberry Fruit Roll]}\n"
                        + "Row #0: 497.42\n"
                        + "Row #0: 235.62\n"
                        + "Row #0: 261.80\n"
                        + "Row #1: 462.84\n"
                        + "Row #1: 226.20\n"
                        + "Row #1: 236.64\n");

        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with\n"
                        + "member Time.Time.x as Aggregate(Union({[Time].[Time].[1997].[Q4]},[Time].[Time].[1997].[Q1] : [Time].[Time].[1997].[Q2]),[Measures].[Store Sales]) \n"
                        + "member Measures.x1 as ([Time].[Time].[1997].[Q1],[Measures].[Store Sales]) \n"
                        + "member Measures.x2 as ([Time].[Time].[1997].[Q2],[Measures].[Store Sales]) \n"
                        + " set products as TopCount(Product.Product.[Product Name].Members,2,(Measures.[Store Sales]))\n"
                        + " SELECT NON EMPTY products ON 1, \n"
                        + "NON EMPTY {[Measures].[Store Sales], Measures.x1, Measures.x2} ON 0 \n"
                        + "FROM [Sales]\n"
                        + "where  Time.Time.x").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[x]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Store Sales]}\n"
                        + "{[Measures].[x1]}\n"
                        + "{[Measures].[x2]}\n"
                        + "Axis #2:\n"
                        + "{[Product].[Product].[Drink].[Beverages].[Drinks].[Flavored Drinks].[Washington].[Washington Apple Drink]}\n"
                        + "{[Product].[Product].[Food].[Eggs].[Eggs].[Eggs].[Urban].[Urban Small Eggs]}\n"
                        + "Row #0: 737.10\n"
                        + "Row #0: 189.54\n"
                        + "Row #0: 203.58\n"
                        + "Row #1: 729.30\n"
                        + "Row #1: 235.62\n"
                        + "Row #1: 261.80\n");
    }

    /**
     * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-900">
     * Bug MONDRIAN-900,
     * "Filter() function works incorrectly together with WHERE clause"</a>.
     */
    @Disabled //TODO need investigate
    @Test
    void testBugMondrian900(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS,\n"
                        + "  Tail(Filter([Customers].[Name].Members, ([Measures].[Unit Sales] IS EMPTY)), 3) ON ROWS \n"
                        + "from [Sales]\n"
                        + "where ([Time].[1997].[Q1].[2] : [Time].[1997].[Q4].[10])").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[1997].[Q1].[2]}\n"
                        + "{[Time].[1997].[Q1].[3]}\n"
                        + "{[Time].[1997].[Q2].[4]}\n"
                        + "{[Time].[1997].[Q2].[5]}\n"
                        + "{[Time].[1997].[Q2].[6]}\n"
                        + "{[Time].[1997].[Q3].[7]}\n"
                        + "{[Time].[1997].[Q3].[8]}\n"
                        + "{[Time].[1997].[Q3].[9]}\n"
                        + "{[Time].[1997].[Q4].[10]}\n"
                        + "Axis #1:\n"
                        + "Axis #2:\n"
                        + "{[Customers].[USA].[WA].[Walla Walla].[Melanie Snow]}\n"
                        + "{[Customers].[USA].[WA].[Walla Walla].[Ramon Williams]}\n"
                        + "{[Customers].[USA].[WA].[Yakima].[Louis Gomez]}\n");
    }



    @Test
    void testSlicerWithCalcMembers(Context<?> context) throws Exception {
        //2 calc mems
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "WITH "
                        + "MEMBER [Store].[Store].[aggCA] AS "
                        + "'Aggregate({[Store].[Store].[USA].[CA].[Los Angeles], "
                        + "[Store].[Store].[USA].[CA].[San Francisco]})'"
                        + " MEMBER [Store].[Store].[aggOR] AS "
                        + "'Aggregate({[Store].[Store].[USA].[OR].[Portland]})' "
                        + " SELECT FROM SALES WHERE { [Store].[Store].[aggCA], [Store].[Store].[aggOR] } ").returnsGrid(
                "Axis #0:\n"
                        + "{[Store].[Store].[aggCA]}\n"
                        + "{[Store].[Store].[aggOR]}\n"
                        + "53,859");

        // mix calc and non-calc
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "WITH "
                        + "MEMBER [Store].[Store].[aggCA] AS "
                        + "'Aggregate({[Store].[Store].[USA].[CA].[Los Angeles], "
                        + "[Store].[Store].[USA].[CA].[San Francisco]})'"
                        + " SELECT FROM SALES WHERE { [Store].[Store].[aggCA], [Store].[Store].[All Stores].[USA].[OR].[Portland] } ").returnsGrid(
                "Axis #0:\n"
                        + "{[Store].[Store].[aggCA]}\n"
                        + "{[Store].[Store].[USA].[OR].[Portland]}\n"
                        + "53,859");

        // multi-position slicer with mix of calc and non-calc
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "WITH "
                        + "MEMBER [Store].[Store].[aggCA] AS "
                        + "'Aggregate({[Store].[Store].[USA].[CA].[Los Angeles], "
                        + "[Store].[Store].[USA].[CA].[San Francisco]})'"
                        + " SELECT FROM SALES WHERE "
                        +  "Gender.Gender.Gender.members * "
                        + "{ [Store].[Store].[aggCA], [Store].[Store].[All Stores].[USA].[OR].[Portland] } ").returnsGrid(
                "Axis #0:\n"
                        + "{[Gender].[Gender].[F], [Store].[Store].[aggCA]}\n"
                        + "{[Gender].[Gender].[F], [Store].[Store].[USA].[OR].[Portland]}\n"
                        + "{[Gender].[Gender].[M], [Store].[Store].[aggCA]}\n"
                        + "{[Gender].[Gender].[M], [Store].[Store].[USA].[OR].[Portland]}\n"
                        + "53,859");

        // named set with calc mem and non-calc
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with member Time.Time.aggTime as "
                        + "'aggregate({ [Time].[Time].[1997].[Q1], [Time].[Time].[1997].[Q2] })'"
                        + "set [timeMembers] as "
                        + "'{Time.Time.aggTime, [Time].[Time].[1997].[Q3] }'"
                        + "select from sales where [timeMembers]").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[aggTime]}\n"
                        + "{[Time].[Time].[1997].[Q3]}\n"
                        + "194,749");

        // calculated measure in slicer
        assertThatQuery(context.getConnectionWithDefaultRole(),
                " SELECT FROM SALES WHERE "
                        + "[Measures].[Profit] * { [Store].[Store].[USA].[CA], [Store].[Store].[USA].[OR]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Measures].[Profit], [Store].[Store].[USA].[CA]}\n"
                        + "{[Measures].[Profit], [Store].[Store].[USA].[OR]}\n"
                        + "$181,141.98");
    }

    @Test
    void testCompoundSlicerAndNamedSet(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "WITH SET [aSet] as 'Filter( Except([Store].[Store Country].Members, [Store].[Store Country].[Canada]), Measures.[Store Sales] > 0)'\n"
                        + "SELECT\n"
                        + "  { Measures.[Unit Sales] } ON COLUMNS,\n"
                        + "  [aSet] ON ROWS\n"
                        + "FROM [Sales]\n"
                        + "WHERE CrossJoin( {[Product].[Drink]}, { [Time].[1997].[Q2], [Time].[1998].[Q1]} )").returnsGrid(
                "Axis #0:\n"
                        + "{[Product].[Product].[Drink], [Time].[Time].[1997].[Q2]}\n"
                        + "{[Product].[Product].[Drink], [Time].[Time].[1998].[Q1]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Unit Sales]}\n"
                        + "Axis #2:\n"
                        + "{[Store].[Store].[USA]}\n"
                        + "Row #0: 5,895\n");
    }

    @Test
    void testDistinctCountMeasureInSlicer(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select gender.members on 0 "
                        + "from sales where "
                        + "NonEmptyCrossJoin(Measures.[Customer Count], "
                        + "{Time.[1997].Q1, Time.[1997].Q2})").returnsGrid(
                "Axis #0:\n"
                        + "{[Measures].[Customer Count], [Time].[Time].[1997].[Q1]}\n"
                        + "{[Measures].[Customer Count], [Time].[Time].[1997].[Q2]}\n"
                        + "Axis #1:\n"
                        + "{[Gender].[Gender].[All Gender]}\n"
                        + "{[Gender].[Gender].[F]}\n"
                        + "{[Gender].[Gender].[M]}\n"
                        + "Row #0: 4,257\n"
                        + "Row #0: 2,095\n"
                        + "Row #0: 2,162\n");
    }

    @Test
    void testDistinctCountWithAggregateMembersAndCompSlicer(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with member time.time.agg as 'Aggregate({Time.Time.[1997].Q1, Time.Time.[1997].Q2})' "
                        + "member Store.Store.agg as 'Aggregate(Head(Store.Store.[USA].children,2))' "
                        + "select NON EMPTY CrossJoin( time.time.agg, CrossJoin( store.store.agg, measures.[customer count]))"
                        + " on 0 from sales "
                        + "WHERE CrossJoin(Gender.F, "
                        + "{[Education Level].[Education Level].[Bachelors Degree], [Education Level].[Education Level].[Graduate Degree]})").returnsGrid(
                "Axis #0:\n"
                        + "{[Gender].[Gender].[F], [Education Level].[Education Level].[Bachelors Degree]}\n"
                        + "{[Gender].[Gender].[F], [Education Level].[Education Level].[Graduate Degree]}\n"
                        + "Axis #1:\n"
                        + "{[Time].[Time].[agg], [Store].[Store].[agg], [Measures].[Customer Count]}\n"
                        + "Row #0: 450\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CompoundSlicerTestModifier3.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testVirtualCubeWithCountDistinctUnsatisfiable(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select {measures.[Customer Count], "
                        + "measures.[Unit Sales by Customer]} on 0 from [warehouse and sales] "
                        + "WHERE {[Time].[1997].Q1, [Time].[1997].Q2} "
                        + "*{[Warehouse].[USA].[CA], Warehouse.[USA].[WA]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[1997].[Q1], [Warehouse].[Warehouse].[USA].[CA]}\n"
                        + "{[Time].[Time].[1997].[Q1], [Warehouse].[Warehouse].[USA].[WA]}\n"
                        + "{[Time].[Time].[1997].[Q2], [Warehouse].[Warehouse].[USA].[CA]}\n"
                        + "{[Time].[Time].[1997].[Q2], [Warehouse].[Warehouse].[USA].[WA]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Customer Count]}\n"
                        + "{[Measures].[Unit Sales by Customer]}\n"
                        + "Row #0: \n"
                        + "Row #0: \n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CompoundSlicerTestModifier3.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testVirtualCubeWithCountDistinctSatisfiable(Context<?> context) {
        //virtualCubeWithDC(context);
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select {measures.[Customer Count], "
                        + "measures.[Unit Sales by Customer]} on 0 from [warehouse and sales] "
                        + "WHERE {[Time].[1997].Q1, [Time].[1997].Q2} "
                        + "*{[Store].[USA].[CA], Store.[USA].[WA]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[1997].[Q1], [Store].[Store].[USA].[CA]}\n"
                        + "{[Time].[Time].[1997].[Q1], [Store].[Store].[USA].[WA]}\n"
                        + "{[Time].[Time].[1997].[Q2], [Store].[Store].[USA].[CA]}\n"
                        + "{[Time].[Time].[1997].[Q2], [Store].[Store].[USA].[WA]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Customer Count]}\n"
                        + "{[Measures].[Unit Sales by Customer]}\n"
                        + "Row #0: 3,311\n"
                        + "Row #0: 29\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CompoundSlicerTestModifier3.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testVirtualCubeWithCountDistinctPartiallySatisfiable(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "select {measures.[Warehouse Sales], "
                        + "measures.[Unit Sales by Customer]} on 0 from [warehouse and sales] "
                        + "WHERE {[Time].[Time].[1997].Q1, [Time].[Time].[1997].Q2} "
                        + "*{[Education Level].[Education Level].[Education Level].members}").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Bachelors Degree]}\n"
                        + "{[Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Graduate Degree]}\n"
                        + "{[Time].[Time].[1997].[Q1], [Education Level].[Education Level].[High School Degree]}\n"
                        + "{[Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial College]}\n"
                        + "{[Time].[Time].[1997].[Q1], [Education Level].[Education Level].[Partial High School]}\n"
                        + "{[Time].[Time].[1997].[Q2], [Education Level].[Education Level].[Bachelors Degree]}\n"
                        + "{[Time].[Time].[1997].[Q2], [Education Level].[Education Level].[Graduate Degree]}\n"
                        + "{[Time].[Time].[1997].[Q2], [Education Level].[Education Level].[High School Degree]}\n"
                        + "{[Time].[Time].[1997].[Q2], [Education Level].[Education Level].[Partial College]}\n"
                        + "{[Time].[Time].[1997].[Q2], [Education Level].[Education Level].[Partial High School]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Warehouse Sales]}\n"
                        + "{[Measures].[Unit Sales by Customer]}\n"
                        + "Row #0: \n"
                        + "Row #0: 30\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CompoundSlicerTestModifier3.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testCompoundSlicerWithComplexAggregation(Context<?> context) {
        //virtualCubeWithDC(context);
        assertThatQuery(context.getConnectionWithDefaultRole(),
                "with\n"
                        + "member time.time.agg as 'Aggregate( { ( Gender.Gender.F, Time.Time.[1997].Q1), (Gender.Gender.M, Time.Time.[1997].Q2) })'\n"
                        + "select measures.[customer count] on 0\n"
                        + "from sales\n"
                        + "where {time.time.agg, Time.Time.[1998]}").returnsGrid(
                "Axis #0:\n"
                        + "{[Time].[Time].[agg]}\n"
                        + "{[Time].[Time].[1998]}\n"
                        + "Axis #1:\n"
                        + "{[Measures].[Customer Count]}\n"
                        + "Row #0: 2,990\n"); // 5,881
    }

    @Test
    void testCompoundAggCalcMemberInSlicer1(Context<?> context) {
        String query = "WITH member store.agg as "
                + "'Aggregate(CrossJoin(Store.[Store Name].members, Gender.F))' "
                + "SELECT filter(customers.[name].members, measures.[unit sales] > 100) on 0 "
                + "FROM sales where store.agg";

        NativeVerify.assertSameNativeAndNot(context,
                query,
                "Compound aggregated member should return same results with native filter on/off");
    }

    @Test
    void testCompoundAggCalcMemberInSlicer2(Context<?> context) {
        String query = "WITH member store.agg as "
                + "'Aggregate({ ([Product].[Product Family].[Drink], Time.[1997].[Q1]), ([Product].[Product Family].[Food], Time.[1997].[Q2]) }))' "
                + "SELECT filter(customers.[name].members, measures.[unit sales] > 100) on 0 "
                + "FROM sales where store.agg";

        NativeVerify.assertSameNativeAndNot(context,
                query,
                "Compound aggregated member should return same results with native filter on/off");
    }

    @Test
    void testNativeFilterWithNullMember(Context<?> context) {
        // The [Store Sqft] attribute include a null member.  This member should not be excluded
        // by the filter function in this query.
        NativeVerify.assertSameNativeAndNot(context, "WITH\n"
                + "SET [*NATIVE_CJ_SET] AS 'FILTER(FILTER([Store Size in SQFT].[Store Sqft].MEMBERS,[Store Size in SQFT]"
                + ".CURRENTMEMBER.CAPTION NOT MATCHES (\"(?i).*20319.*\")), NOT ISEMPTY ([Measures].[Unit Sales]))'\n"
                + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Store Size in SQFT].CURRENTMEMBER.ORDERKEY,BASC)'\n"
                + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
                + "SET [*BASE_MEMBERS__Store Size in SQFT_] AS 'FILTER([Store Size in SQFT].[Store Sqft].MEMBERS,[Store "
                + "Size in SQFT].CURRENTMEMBER.CAPTION NOT MATCHES (\"(?i).*20319.*\"))'\n"
                + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Store Size in SQFT].CURRENTMEMBER)})'\n"
                + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', "
                + "SOLVE_ORDER=500\n"
                + "SELECT\n"
                + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
                + ",[*SORTED_ROW_AXIS] ON ROWS\n"
                + "FROM [Sales]", "");
    }
}
