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
package org.eclipse.daanse.olap.function.def.generate;

import static org.junit.jupiter.api.Assertions.fail;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatSetExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.util.concurrent.CancellationException;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.exceptions.QueryTimeoutException;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class GenerateFunDefTest {

    @Test
    void testGenerateDepends(Context<?> context) {
        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Generate([Product].CurrentMember.Children, Crossjoin({[Product].CurrentMember}, Crossjoin([Store].[Store "
                + "State].Members, [Store Type].Members)), ALL)")
            .dependsOn( "[Product].[Product]" );
        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Generate([Product].[All Products].Children, Crossjoin({[Product].CurrentMember}, Crossjoin([Store].[Store "
                + "State].Members, [Store Type].Members)), ALL)")
            .dependsOn();
        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({[Store].[USA], [Store].[USA].[CA]}, {[Store].CurrentMember.Children})")
            .dependsOn();
        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({[Store].[USA], [Store].[USA].[CA]}, {[Gender].CurrentMember})")
            .dependsOn( "[Gender].[Gender]" );
        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({[Store].[USA], [Store].[USA].[CA]}, {[Gender].[M]})")
            .dependsOn();
    }

    @Test
    void testGenerate(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({[Store].[USA], [Store].[USA].[CA]}, {[Store].CurrentMember.Children})")
            .returns(
            "[Store].[Store].[USA].[CA]\n"
                + "[Store].[Store].[USA].[OR]\n"
                + "[Store].[Store].[USA].[WA]\n"
                + "[Store].[Store].[USA].[CA].[Alameda]\n"
                + "[Store].[Store].[USA].[CA].[Beverly Hills]\n"
                + "[Store].[Store].[USA].[CA].[Los Angeles]\n"
                + "[Store].[Store].[USA].[CA].[San Diego]\n"
                + "[Store].[Store].[USA].[CA].[San Francisco]" );
    }

    @Test
    void testGenerateNonSet(Context<?> context) {
        // SSAS implicitly converts arg #2 to a set
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({[Store].[USA], [Store].[USA].[CA]}, [Store].PrevMember, ALL)")
            .returns(
            "[Store].[Store].[Mexico]\n"
                + "[Store].[Store].[Mexico].[Zacatecas]" );

        // SSAS implicitly converts arg #1 to a set
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Generate([Store].[USA], [Store].PrevMember, ALL)")
            .returns(
            "[Store].[Store].[Mexico]" );
    }

    @Test
    void testGenerateAll(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({[Store].[USA].[CA], [Store].[USA].[OR].[Portland]},"
                + " Ascendants([Store].CurrentMember),"
                + " ALL)")
            .returns(
            "[Store].[Store].[USA].[CA]\n"
                + "[Store].[Store].[USA]\n"
                + "[Store].[Store].[All Stores]\n"
                + "[Store].[Store].[USA].[OR].[Portland]\n"
                + "[Store].[Store].[USA].[OR]\n"
                + "[Store].[Store].[USA]\n"
                + "[Store].[Store].[All Stores]" );
    }

    @Test
    void testGenerateUnique(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({[Store].[USA].[CA], [Store].[USA].[OR].[Portland]},"
                + " Ascendants([Store].CurrentMember))")
            .returns(
            "[Store].[Store].[USA].[CA]\n"
                + "[Store].[Store].[USA]\n"
                + "[Store].[Store].[All Stores]\n"
                + "[Store].[Store].[USA].[OR].[Portland]\n"
                + "[Store].[Store].[USA].[OR]" );
    }

    @Test
    void testGenerateUniqueTuple(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({([Store].[USA].[CA],[Product].[All Products]), "
                + "([Store].[USA].[CA],[Product].[All Products])},"
                + "{([Store].CurrentMember, [Product].CurrentMember)})")
            .returns(
            "{[Store].[Store].[USA].[CA], [Product].[Product].[All Products]}" );
    }

    @Test
    void testGenerateCrossJoin(Context<?> context) {
        // Note that the different regions have different Top 2.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({[Store].[USA].[CA], [Store].[USA].[CA].[San Francisco]},\n"
                + "  CrossJoin({[Store].CurrentMember},\n"
                + "    TopCount([Product].[Brand Name].members, \n"
                + "    2,\n"
                + "    [Measures].[Unit Sales])))")
            .returns(
            "{[Store].[Store].[USA].[CA], [Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Hermanos]}\n"
                + "{[Store].[Store].[USA].[CA], [Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Tell Tale]}\n"
                + "{[Store].[Store].[USA].[CA].[San Francisco], [Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[Ebony]}\n"
                + "{[Store].[Store].[USA].[CA].[San Francisco], [Product].[Product].[Food].[Produce].[Vegetables].[Fresh Vegetables].[High "
                + "Top]}" );
    }

    @Test
    void testGenerateString(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({Time.[1997], Time.[1998]},"
                + " Time.[Time].CurrentMember.Name)")
            .returns(
            "19971998" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Generate({Time.[1997], Time.[1998]},"
                + " Time.[Time].CurrentMember.Name, \" and \")")
            .returns(
            "1997 and 1998" );
    }

    //TODO: URGENT!!!!!
    //TODO: remove disable reset timeout time
    @Disabled
    @Test
    @RolapConfig(key = ConfigConstants.QUERY_TIMEOUT, value = "5", type = Integer.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "false", type = Boolean.class)
    void testGenerateWillTimeout(Context<?> context) {
        try {
            executeQuery(context.getConnectionWithDefaultRole(), "select {"
                + "Generate([Product].[Product Name].members,"
                    + "  Generate([Customers].[Name].members, "
                    + "    {([Store].CurrentMember, [Product].CurrentMember, [Customers].CurrentMember)}))"
                + "} on columns from Sales" );
        } catch ( QueryTimeoutException e ) {
            return;
        } catch ( CancellationException e ) {
            return;
        }
        fail( "should have timed out" );
    }

    // The test case for the issue: MONDRIAN-2402
    @Test
    void testGenerateForStringMemberProperty(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Store].[Store].[Lineage of Time] AS\n"
                + " Generate(Ascendants([Time].[Time].CurrentMember), [Time].[Time].CurrentMember.Properties(\"MEMBER_CAPTION\"), \",\")\n"
                + " SELECT\n"
                + "  {[Time].[Time].[1997]} ON Axis(0),\n"
                + "  Union(\n"
                + "   {([Store].[Store].[Lineage of Time])},\n"
                + "   {[Store].[Store].[All Stores]}) ON Axis(1)\n"
                + " FROM [Sales]\n")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1997]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[Lineage of Time]}\n"
                + "{[Store].[Store].[All Stores]}\n"
                + "Row #0: 1997\n"
                + "Row #1: 266,773\n" );
    }

}
