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
package org.eclipse.daanse.olap.function.def.except;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class ExceptFunDefTest {


    @Test
    void testExceptEmpty(Context<?> context) {
        // If left is empty, result is empty.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Except(Filter([Gender].Members, 1=0), {[Gender].[Gender].[M]})").returns( "" );

        // If right is empty, result is left.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Except({[Gender].[M]}, Filter([Gender].Members, 1=0))")
            .returns( "[Gender].[Gender].[M]" );
    }

    /**
     * Tests that Except() successfully removes crossjoined tuples from the axis results.  Previously, this would fail by
     * returning all tuples in the first argument to Except.  bug 1439627
     */
    @Test
    void testExceptCrossjoin(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Except(CROSSJOIN({[Promotion Media].[All Media]},\n"
                + "                  [Product].[All Products].Children),\n"
                + "       CROSSJOIN({[Promotion Media].[All Media]},\n"
                + "                  {[Product].[All Products].[Drink]}))")
            .returns(
            "{[Promotion Media].[Promotion Media].[All Media], [Product].[Product].[Food]}\n"
                + "{[Promotion Media].[Promotion Media].[All Media], [Product].[Product].[Non-Consumable]}" );
    }

    @Test
    void testExtract(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract(\n"
                + "Crossjoin({[Gender].[F], [Gender].[M]},\n"
                + "          {[Marital Status].Members}),\n"
                + "[Gender])")
            .returns( "[Gender].[Gender].[F]\n" + "[Gender].[Gender].[M]" );

        // Extract(<set>) with no dimensions is not valid
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract(Crossjoin({[Gender].[F], [Gender].[M]}, {[Marital Status].Members}))")
            .throwsMessage( "No function matches signature 'Extract(<Set>)'" );

        // Extract applied to non-constant dimension should fail
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract(Crossjoin([Gender].Members, [Store].Children), [Store].Hierarchy.Dimension)")
            .throwsMessage( "not a constant hierarchy: [Store].Hierarchy.Dimension" );

        // Extract applied to non-constant hierarchy should fail
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract(Crossjoin([Gender].Members, [Store].Children), [Store].Hierarchy)")
            .throwsMessage( "not a constant hierarchy: [Store].Hierarchy" );

        // Extract applied to set of members is OK (if silly). Duplicates are
        // removed, as always.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract({[Gender].[M], [Gender].Members}, [Gender])")
            .returns(
            "[Gender].[Gender].[M]\n"
                + "[Gender].[Gender].[All Gender]\n"
                + "[Gender].[Gender].[F]" );

        // Extract of hierarchy not in set fails
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract(Crossjoin([Gender].Members, [Store].Children), [Marital Status])")
            .throwsMessage( "hierarchy [Marital Status].[Marital Status] is not a hierarchy of the expression Crossjoin([Gender].Members, [Store].Children)" );

        // Extract applied to empty set returns empty set
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract(Crossjoin({[Gender].Parent}, [Store].Children), [Store])")
            .returns( "" );

        // Extract applied to asymmetric set
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract(\n"
                + "{([Gender].[M], [Marital Status].[M]),\n"
                + " ([Gender].[F], [Marital Status].[M]),\n"
                + " ([Gender].[M], [Marital Status].[S])},\n"
                + "[Gender])")
            .returns( "[Gender].[Gender].[M]\n" + "[Gender].[Gender].[F]" );

        // Extract applied to asymmetric set (other side)
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract(\n"
                + "{([Gender].[M], [Marital Status].[M]),\n"
                + " ([Gender].[F], [Marital Status].[M]),\n"
                + " ([Gender].[M], [Marital Status].[S])},\n"
                + "[Marital Status])")
            .returns(
            "[Marital Status].[Marital Status].[M]\n"
                + "[Marital Status].[Marital Status].[S]" );

        // Extract more than one hierarchy
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract(\n"
                + "[Gender].Children * [Marital Status].Children * [Time].[1997].Children * [Store].[USA].Children,\n"
                + "[Time], [Marital Status])")
            .returns(
            "{[Time].[Time].[1997].[Q1], [Marital Status].[Marital Status].[M]}\n"
                + "{[Time].[Time].[1997].[Q2], [Marital Status].[Marital Status].[M]}\n"
                + "{[Time].[Time].[1997].[Q3], [Marital Status].[Marital Status].[M]}\n"
                + "{[Time].[Time].[1997].[Q4], [Marital Status].[Marital Status].[M]}\n"
                + "{[Time].[Time].[1997].[Q1], [Marital Status].[Marital Status].[S]}\n"
                + "{[Time].[Time].[1997].[Q2], [Marital Status].[Marital Status].[S]}\n"
                + "{[Time].[Time].[1997].[Q3], [Marital Status].[Marital Status].[S]}\n"
                + "{[Time].[Time].[1997].[Q4], [Marital Status].[Marital Status].[S]}" );

        // Extract duplicate hierarchies fails
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Extract(\n"
                + "{([Gender].[M], [Marital Status].[M]),\n"
                + " ([Gender].[F], [Marital Status].[M]),\n"
                + " ([Gender].[M], [Marital Status].[S])},\n"
                + "[Gender], [Gender])")
            .throwsMessage( "hierarchy [Gender].[Gender] is extracted more than once" );
    }

    /**
     * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-1043"> MONDRIAN-1043, "Hierarchize with Except
     * sort set members differently than in Mondrian 3.2.1"</a>.
     *
     * <p>This test makes sure that
     * Hierarchize and Except can be used within each other and that the sort order is maintained.</p>
     */
    @Test
    void testHierarchizeExcept(Context<?> context) throws Exception {
        final String[] mdxA =
            new String[] {
                "SELECT {[Measures].[Unit Sales], [Measures].[Store Sales]} ON COLUMNS, Hierarchize(Except({[Customers].[USA]"
                    + ".Children, [Customers].[USA].[CA].Children}, [Customers].[USA].[CA])) ON ROWS FROM [Sales]",
                "SELECT {[Measures].[Unit Sales], [Measures].[Store Sales]} ON COLUMNS, Except(Hierarchize({[Customers].[USA]"
                    + ".Children, [Customers].[USA].[CA].Children}), [Customers].[USA].[CA]) ON ROWS FROM [Sales] "
            };
        for ( String mdx : mdxA ) {
            assertThatQuery(context.getConnectionWithDefaultRole(), mdx)
                .returnsGrid(
                "Axis #0:\n"
                    + "{}\n"
                    + "Axis #1:\n"
                    + "{[Measures].[Unit Sales]}\n"
                    + "{[Measures].[Store Sales]}\n"
                    + "Axis #2:\n"
                    + "{[Customers].[Customers].[USA].[CA].[Altadena]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Arcadia]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Bellflower]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Berkeley]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Beverly Hills]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Burbank]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Burlingame]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Chula Vista]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Colma]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Concord]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Coronado]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Daly City]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Downey]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[El Cajon]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Fremont]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Glendale]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Grossmont]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Imperial Beach]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[La Jolla]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[La Mesa]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Lakewood]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Lemon Grove]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Lincoln Acres]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Long Beach]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Los Angeles]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Mill Valley]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[National City]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Newport Beach]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Novato]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Oakland]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Palo Alto]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Pomona]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Redwood City]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Richmond]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[San Carlos]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[San Diego]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[San Francisco]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[San Gabriel]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[San Jose]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Santa Cruz]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Santa Monica]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Spring Valley]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Torrance]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[West Covina]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Woodland Hills]}\n"
                    + "{[Customers].[Customers].[USA].[OR]}\n"
                    + "{[Customers].[Customers].[USA].[WA]}\n"
                    + "Row #0: 2,574\n"
                    + "Row #0: 5,585.59\n"
                    + "Row #1: 2,440\n"
                    + "Row #1: 5,136.59\n"
                    + "Row #2: 3,106\n"
                    + "Row #2: 6,633.97\n"
                    + "Row #3: 136\n"
                    + "Row #3: 320.17\n"
                    + "Row #4: 2,907\n"
                    + "Row #4: 6,194.37\n"
                    + "Row #5: 3,086\n"
                    + "Row #5: 6,577.33\n"
                    + "Row #6: 198\n"
                    + "Row #6: 407.38\n"
                    + "Row #7: 2,999\n"
                    + "Row #7: 6,284.30\n"
                    + "Row #8: 129\n"
                    + "Row #8: 287.78\n"
                    + "Row #9: 105\n"
                    + "Row #9: 219.77\n"
                    + "Row #10: 2,391\n"
                    + "Row #10: 5,051.15\n"
                    + "Row #11: 129\n"
                    + "Row #11: 271.60\n"
                    + "Row #12: 3,440\n"
                    + "Row #12: 7,367.06\n"
                    + "Row #13: 2,543\n"
                    + "Row #13: 5,460.42\n"
                    + "Row #14: 163\n"
                    + "Row #14: 350.22\n"
                    + "Row #15: 3,284\n"
                    + "Row #15: 7,082.91\n"
                    + "Row #16: 2,131\n"
                    + "Row #16: 4,458.60\n"
                    + "Row #17: 1,616\n"
                    + "Row #17: 3,409.34\n"
                    + "Row #18: 1,938\n"
                    + "Row #18: 4,081.37\n"
                    + "Row #19: 1,834\n"
                    + "Row #19: 3,908.26\n"
                    + "Row #20: 2,487\n"
                    + "Row #20: 5,174.12\n"
                    + "Row #21: 2,651\n"
                    + "Row #21: 5,636.82\n"
                    + "Row #22: 2,176\n"
                    + "Row #22: 4,691.94\n"
                    + "Row #23: 2,973\n"
                    + "Row #23: 6,422.37\n"
                    + "Row #24: 2,009\n"
                    + "Row #24: 4,312.99\n"
                    + "Row #25: 58\n"
                    + "Row #25: 109.36\n"
                    + "Row #26: 2,031\n"
                    + "Row #26: 4,237.46\n"
                    + "Row #27: 3,098\n"
                    + "Row #27: 6,696.06\n"
                    + "Row #28: 163\n"
                    + "Row #28: 335.98\n"
                    + "Row #29: 70\n"
                    + "Row #29: 145.90\n"
                    + "Row #30: 133\n"
                    + "Row #30: 272.08\n"
                    + "Row #31: 2,712\n"
                    + "Row #31: 5,595.62\n"
                    + "Row #32: 144\n"
                    + "Row #32: 312.43\n"
                    + "Row #33: 110\n"
                    + "Row #33: 212.45\n"
                    + "Row #34: 145\n"
                    + "Row #34: 289.80\n"
                    + "Row #35: 1,535\n"
                    + "Row #35: 3,348.69\n"
                    + "Row #36: 88\n"
                    + "Row #36: 195.28\n"
                    + "Row #37: 2,631\n"
                    + "Row #37: 5,663.60\n"
                    + "Row #38: 161\n"
                    + "Row #38: 343.20\n"
                    + "Row #39: 185\n"
                    + "Row #39: 367.78\n"
                    + "Row #40: 2,660\n"
                    + "Row #40: 5,739.63\n"
                    + "Row #41: 1,790\n"
                    + "Row #41: 3,862.79\n"
                    + "Row #42: 2,570\n"
                    + "Row #42: 5,405.02\n"
                    + "Row #43: 2,503\n"
                    + "Row #43: 5,302.08\n"
                    + "Row #44: 2,516\n"
                    + "Row #44: 5,406.21\n"
                    + "Row #45: 67,659\n"
                    + "Row #45: 142,277.07\n"
                    + "Row #46: 124,366\n"
                    + "Row #46: 263,793.22\n" );
        }
    }

}
