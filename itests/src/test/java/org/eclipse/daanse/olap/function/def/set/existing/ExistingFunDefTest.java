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
package org.eclipse.daanse.olap.function.def.set.existing;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class ExistingFunDefTest {
    @Test
    void testExisting(Context<?> context) {
        // basic test
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "  member measures.ExistingCount as\n"
                + "  count(Existing [Product].[Product Subcategory].Members)\n"
                + "  select {measures.ExistingCount} on 0,\n"
                + "  [Product].[Product Family].Members on 1\n"
                + "  from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[ExistingCount]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Food]}\n"
                + "{[Product].[Product].[Non-Consumable]}\n"
                + "Row #0: 8\n"
                + "Row #1: 62\n"
                + "Row #2: 32\n" );
        // same as exists+currentMember
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member measures.StaticCount as\n"
                + "  count([Product].[Product Subcategory].Members)\n"
                + "  member measures.WithExisting as\n"
                + "  count(Existing [Product].[Product Subcategory].Members)\n"
                + "  member measures.WithExists as\n"
                + "  count(Exists([Product].[Product Subcategory].Members, [Product].CurrentMember))\n"
                + "  select {measures.StaticCount, measures.WithExisting, measures.WithExists} on 0,\n"
                + "  [Product].[Product Family].Members on 1\n"
                + "  from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[StaticCount]}\n"
                + "{[Measures].[WithExisting]}\n"
                + "{[Measures].[WithExists]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Food]}\n"
                + "{[Product].[Product].[Non-Consumable]}\n"
                + "Row #0: 102\n"
                + "Row #0: 8\n"
                + "Row #0: 8\n"
                + "Row #1: 102\n"
                + "Row #1: 62\n"
                + "Row #1: 62\n"
                + "Row #2: 102\n"
                + "Row #2: 32\n"
                + "Row #2: 32\n" );
    }

    @Test
    void testExistingCalculatedMeasure(Context<?> context) {
        // sorry about the mess, this came from Analyzer
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH \n"
                + "SET [*NATIVE_CJ_SET] AS 'FILTER({[Time].[Weekly].[All Weeklys].[1997].[2],[Time].[Weekly].[All "
                + "Weeklys].[1997].[24]}, NOT ISEMPTY ([Measures].[Store Sales]) OR NOT ISEMPTY ([Measures]"
                + ".[CALCULATED_MEASURE_1]))' \n"
                + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Time].[Weekly].CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Time]"
                + ".[Weekly].CURRENTMEMBER,[Time].[Weekly].[Year]).ORDERKEY,BASC)'\n"
                + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0],[Measures].[CALCULATED_MEASURE_1]}'\n"
                + "SET [*BASE_MEMBERS__Time.Weekly_] AS '{[Time].[Weekly].[All Weeklys].[1997].[2],[Time].[Weekly].[All "
                + "Weeklys].[1997].[24]}'\n"
                + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Time].[Weekly].CURRENTMEMBER)})'\n"
                + "MEMBER [Measures].[CALCULATED_MEASURE_1] AS 'SetToStr( EXISTING [Time].[Weekly].[Week].Members )'\n"
                + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Store Sales]', FORMAT_STRING = '#,###.00', "
                + "SOLVE_ORDER=500\n"
                + "SELECT\n"
                + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
                + ", NON EMPTY\n"
                + "[*SORTED_ROW_AXIS] ON ROWS\n"
                + "FROM [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
                + "{[Measures].[CALCULATED_MEASURE_1]}\n"
                + "Axis #2:\n"
                + "{[Time].[Weekly].[1997].[2]}\n"
                + "{[Time].[Weekly].[1997].[24]}\n"
                + "Row #0: 19,756.43\n"
                + "Row #0: {[Time].[Weekly].[1997].[2]}\n"
                + "Row #1: 11,371.84\n"
                + "Row #1: {[Time].[Weekly].[1997].[24]}\n" );
    }

    @Test
    void testExistingCalculatedMeasureCompoundSlicer(Context<?> context) {
        // basic test
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "  member measures.subcategorystring as SetToStr( EXISTING [Product].[Product Subcategory].Members)\n"
                + "  select { measures.subcategorystring } on 0\n"
                + "  from [Sales]\n"
                + "  where {[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]} ")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine]}\n"
                + "Axis #1:\n"
                + "{[Measures].[subcategorystring]}\n"
                + "Row #0: {[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer], [Product].[Product].[Drink].[Alcoholic "
                + "Beverages].[Beer and Wine].[Wine]}\n" );

        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with MEMBER [Measures].[*CALCULATED_MEASURE_1] AS 'SetToStr( EXISTING [Product].[Product Category].Members )'\n"
                + " SELECT {[Measures].[*CALCULATED_MEASURE_1]} ON COLUMNS\n"
                + " FROM [Sales]\n"
                + " WHERE {[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer], [Product].[Drink].[Alcoholic "
                + "Beverages].[Beer and Wine].[Wine], [Product].[Food].[Eggs].[Eggs] } ")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer]}\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Wine]}\n"
                + "{[Product].[Product].[Food].[Eggs].[Eggs]}\n"
                + "Axis #1:\n"
                + "{[Measures].[*CALCULATED_MEASURE_1]}\n"
                + "Row #0: {[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine], [Product].[Product].[Food].[Eggs].[Eggs]}\n" );
    }

    @Test
    void testExistingAggSet(Context<?> context) {
        // aggregate simple set
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Measures].[Edible Sales] AS \n"
                + "Aggregate( Existing {[Product].[Drink], [Product].[Food]}, Measures.[Unit Sales] )\n"
                + "SELECT {Measures.[Unit Sales], Measures.[Edible Sales]} ON 0,\n"
                + "{ [Product].[Product Family].Members, [Product].[All Products] } ON 1\n"
                + "FROM [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Edible Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Food]}\n"
                + "{[Product].[Product].[Non-Consumable]}\n"
                + "{[Product].[Product].[All Products]}\n"
                + "Row #0: 24,597\n"
                + "Row #0: 24,597\n"
                + "Row #1: 191,940\n"
                + "Row #1: 191,940\n"
                + "Row #2: 50,236\n"
                + "Row #2: \n"
                + "Row #3: 266,773\n"
                + "Row #3: 216,537\n" );
    }

    @Test
    void testExistingGenerateAgg(Context<?> context) {
        // generate overrides existing context
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH SET BestOfFamilies AS\n"
                + "  Generate( [Product].[Product Family].Members,\n"
                + "            TopCount( Existing [Product].[Brand Name].Members, 10, Measures.[Unit Sales]) ) \n"
                + "MEMBER Measures.[Top 10 Brand Sales] AS Aggregate(Existing BestOfFamilies, Measures.[Unit Sales])"
                + "MEMBER Measures.[Rest Brand Sales] AS Aggregate( Except(Existing [Product].[Brand Name].Members, Existing "
                + "BestOfFamilies), Measures.[Unit Sales])"
                + "SELECT { Measures.[Unit Sales], Measures.[Top 10 Brand Sales], Measures.[Rest Brand Sales] } ON 0,\n"
                + "       {[Product].[Product Family].Members} ON 1\n"
                + "FROM [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Top 10 Brand Sales]}\n"
                + "{[Measures].[Rest Brand Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Food]}\n"
                + "{[Product].[Product].[Non-Consumable]}\n"
                + "Row #0: 24,597\n"
                + "Row #0: 9,448\n"
                + "Row #0: 15,149\n"
                + "Row #1: 191,940\n"
                + "Row #1: 32,506\n"
                + "Row #1: 159,434\n"
                + "Row #2: 50,236\n"
                + "Row #2: 8,936\n"
                + "Row #2: 41,300\n" );
    }

    @Test
    void testExistingGenerateOverrides(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER Measures.[StaticSumNC] AS\n"
                + " 'Sum(Generate([Product].[Non-Consumable],"
                + "    Existing [Product].[Product Department].Members), Measures.[Unit Sales])'\n"
                + "SELECT { Measures.[StaticSumNC], Measures.[Unit Sales] } ON 0,\n"
                + "    NON EMPTY {[Product].[Product Family].Members} ON 1\n"
                + "FROM [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[StaticSumNC]}\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Food]}\n"
                + "{[Product].[Product].[Non-Consumable]}\n"
                + "Row #0: 50,236\n"
                + "Row #0: 24,597\n"
                + "Row #1: 50,236\n"
                + "Row #1: 191,940\n"
                + "Row #2: 50,236\n"
                + "Row #2: 50,236\n" );
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER Measures.[StaticSumNC] AS\n"
                + " 'Sum(Generate([Product].[Product Family].Members,"
                + "    Existing [Product].[Product Department].Members), Measures.[Unit Sales])'\n"
                + "SELECT { Measures.[StaticSumNC], Measures.[Unit Sales] } ON 0,\n"
                + "    NON EMPTY {[Product].[Non-Consumable]} ON 1\n"
                + "FROM [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[StaticSumNC]}\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Non-Consumable]}\n"
                + "Row #0: 266,773\n"
                + "Row #0: 50,236\n" );
    }

    @Test
    void testExistingVirtualCube(Context<?> context) {
        // this should ideally return 14 for both,
        // but being coherent with exists is good enough
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Measures].[Count Exists] AS Count(exists( [Time].[Weekly].[Week].Members, [Time].[Weekly]"
                + ".CurrentMember ) )\n"
                + " MEMBER [Measures].[Count Existing] AS Count(existing [Time].[Weekly].[Week].Members)\n"
                + "SELECT\n"
                + "{[Measures].[Count Exists], [Measures].[Count Existing]}\n"
                + "ON 0\n"
                + "FROM [Warehouse and Sales]\n"
                + "WHERE [Time].[Time].[1997].[Q2]")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Time].[Time].[1997].[Q2]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Count Exists]}\n"
                + "{[Measures].[Count Existing]}\n"
                + "Row #0: 104\n"
                + "Row #0: 104\n" );
    }

}
