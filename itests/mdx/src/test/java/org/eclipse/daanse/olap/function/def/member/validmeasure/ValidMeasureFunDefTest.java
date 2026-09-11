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
package org.eclipse.daanse.olap.function.def.member.validmeasure;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.FunDependencies;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.olap.function.TestResources;

@RolapContextTest(FoodmartTestInstance.class)
class ValidMeasureFunDefTest {

    /**
     * Tests the <code>ValidMeasure</code> function.
     */
    @Test
    void testValidMeasure(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with\n"
                + "member measures.[with VM] as 'validmeasure([measures].[unit sales])'\n"
                + "select { measures.[with VM]} on 0,\n"
                + "[Warehouse].[Country].members on 1 from [warehouse and sales]\n")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[with VM]}\n"
                + "Axis #2:\n"
                + "{[Warehouse].[Warehouse].[Canada]}\n"
                + "{[Warehouse].[Warehouse].[Mexico]}\n"
                + "{[Warehouse].[Warehouse].[USA]}\n"
                + "Row #0: 266,773\n"
                + "Row #1: 266,773\n"
                + "Row #2: 266,773\n" );
    }

    @Test
    void _testValidMeasureNonEmpty(Context<?> context) {
        // Note that [with VM2] is NULL where it needs to be - and therefore
        // does not prevent NON EMPTY from eliminating empty rows.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with set [Foo] as ' Crossjoin({[Time].[Time].Children}, {[Measures].[Warehouse Sales]}) '\n"
                + " member [Measures].[with VM] as 'ValidMeasure([Measures].[Unit Sales])'\n"
                + " member [Measures].[with VM2] as 'Iif(Count(Filter([Foo], not isempty([Measures].CurrentMember))) > 0, "
                + "ValidMeasure([Measures].[Unit Sales]), NULL)'\n"
                + "select NON EMPTY Crossjoin({[Time].[Time].Children}, {[Measures].[with VM2], [Measures].[Warehouse Sales]}) ON "
                + "COLUMNS,\n"
                + "  NON EMPTY {[Warehouse].[Warehouse].[All Warehouses].[USA].[WA].Children} ON ROWS\n"
                + "from [Warehouse and Sales]\n"
                + "where [Product].[Product].[All Products].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good].[Good Light "
                + "Beer]")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Good].[Good Light Beer]}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1997].[Q1], [Measures].[with VM2]}\n"
                + "{[Time].[Time].[1997].[Q1], [Measures].[Warehouse Sales]}\n"
                + "{[Time].[Time].[1997].[Q2], [Measures].[with VM2]}\n"
                + "{[Time].[Time].[1997].[Q2], [Measures].[Warehouse Sales]}\n"
                + "{[Time].[Time].[1997].[Q3], [Measures].[with VM2]}\n"
                + "{[Time].[Time].[1997].[Q4], [Measures].[with VM2]}\n"
                + "Axis #2:\n"
                + "{[Warehouse].[Warehouse].[USA].[WA].[Seattle]}\n"
                + "{[Warehouse].[Warehouse].[USA].[WA].[Tacoma]}\n"
                + "{[Warehouse].[Warehouse].[USA].[WA].[Yakima]}\n"
                + "Row #0: 26\n"
                + "Row #0: 34.793\n"
                + "Row #0: 25\n"
                + "Row #0: \n"
                + "Row #0: 36\n"
                + "Row #0: 28\n"
                + "Row #1: 26\n"
                + "Row #1: \n"
                + "Row #1: 25\n"
                + "Row #1: 64.615\n"
                + "Row #1: 36\n"
                + "Row #1: 28\n"
                + "Row #2: 26\n"
                + "Row #2: 79.657\n"
                + "Row #2: 25\n"
                + "Row #2: \n"
                + "Row #2: 36\n"
                + "Row #2: 28\n" );
    }

    @Test
    void testValidMeasureTupleHasAnotherMember(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with\n"
                + "member measures.[with VM] as 'validmeasure(([measures].[unit sales],[customers].[all customers]))'\n"
                + "select { measures.[with VM]} on 0,\n"
                + "[Warehouse].[Country].members on 1 from [warehouse and sales]\n")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[with VM]}\n"
                + "Axis #2:\n"
                + "{[Warehouse].[Warehouse].[Canada]}\n"
                + "{[Warehouse].[Warehouse].[Mexico]}\n"
                + "{[Warehouse].[Warehouse].[USA]}\n"
                + "Row #0: 266,773\n"
                + "Row #1: 266,773\n"
                + "Row #2: 266,773\n" );
    }

    @Test
    void testValidMeasureDepends(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        FunDependencies.assertThatExpr(connection, "Sales",
            "ValidMeasure([Measures].[Unit Sales])")
            .dependsOn( TestResources.hiersExcept( "[Measures]" ) );

        FunDependencies.assertThatExpr(connection, "Sales",
            "ValidMeasure(([Measures].[Unit Sales], [Time].[1997].[Q1]))")
            .dependsOn( TestResources.hiersExcept( "[Measures]", "[Time].[Time]" ) );

        FunDependencies.assertThatExpr(connection, "Sales",
            "ValidMeasure(([Measures].[Unit Sales], "
                + "[Time].[Time].CurrentMember.Parent))")
            .dependsOn( TestResources.hiersExcept( "[Measures]" ) );
    }

    @Test
    void testValidMeasureNonVirtualCube(Context<?> context) {
        // verify ValidMeasure used outside of a virtual cube
        // is effectively a no-op.
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatQuery(connection,
            "with member measures.vm as 'ValidMeasure(measures.[Store Sales])'"
                + " select measures.[vm] on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[vm]}\n"
                + "Row #0: 565,238.13\n" );
        assertThatQuery(connection,
            "with member measures.vm as 'ValidMeasure((gender.f, measures.[Store Sales]))'"
                + " select measures.[vm] on 0 from Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[vm]}\n"
                + "Row #0: 280,226.21\n" );
    }

    /**
     * This is a test for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-2109">MONDRIAN-2109</a>
     *
     * <p>We can't allow calculated members in ValidMeasure so a proper message
     * must be returned.
     */
    @Test
    void testValidMeasureCalculatedMemberMeasure(Context<?> context) {
        // Check for failure.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member measures.calc as 'measures.[Warehouse sales]' \n"
                + "member measures.vm as 'ValidMeasure(measures.calc)' \n"
                + "select from [warehouse and sales]\n"
                + "where (measures.vm ,gender.f) \n")
            .throwsMessage( "The function ValidMeasure cannot be used with the measure '[Measures].[calc]' because it is a calculated "
                + "member." );
        // Check the working version
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "member measures.vm as 'ValidMeasure(measures.[warehouse sales])' \n"
                + "select from [warehouse and sales] where (measures.vm, gender.f) \n")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[vm], [Gender].[Gender].[F]}\n"
                + "196,770.888" );
    }

}
