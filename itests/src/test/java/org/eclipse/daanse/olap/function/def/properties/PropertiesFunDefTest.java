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
package org.eclipse.daanse.olap.function.def.properties;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class PropertiesFunDefTest {

    @Test
    void testPropertiesExpr(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Store].[USA].[CA].[Beverly Hills].[Store 6].Properties(\"Store Type\")")
            .returns( "Gourmet Supermarket" );
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1227">MONDRIAN-1227,
     * "Properties function does not implicitly convert dimension to member; has documentation typos"</a>.
     */
    @Test
    void testPropertiesOnDimension(Context<?> context) {
        // [Store] is a dimension. When called with a property like FirstChild,
        // it is implicitly converted to a member.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "[Store].FirstChild").returns( "[Store].[Store].[Canada]" );

        // The same should happen with the <Member>.Properties(<String>)
        // function; now the bug is fixed, it does. Dimension is implicitly
        // converted to member.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Store].Properties('MEMBER_UNIQUE_NAME')")
            .returns( "[Store].[Store].[All Stores]" );

        // Hierarchy is implicitly converted to member.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Store].[USA].Hierarchy.Properties('MEMBER_UNIQUE_NAME')")
            .returns( "[Store].[Store].[All Stores]" );
    }

    /**
     * Tests that non-existent property throws an error. *
     */
    @Test
    void testPropertiesNonExistent(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Store].[USA].[CA].[Beverly Hills].[Store 6].Properties(\"Foo\")")
            .throwsMessage( "Property 'Foo' is not valid for" );
    }

    @Test
    void testPropertiesFilter(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "SELECT { [Store Sales] } ON COLUMNS,\n"
                + " TOPCOUNT(Filter( [Store].[Store Name].Members,\n"
                + "                   [Store].CurrentMember.Properties(\"Store Type\") = \"Supermarket\"),\n"
                + "           10, [Store Sales]) ON ROWS\n"
                + "FROM [Sales]" );
        assertEquals( 8, result.getAxes()[ 1 ].getPositions().size() );
    }

    @Test
    void testPropertyInCalculatedMember(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Measures].[Store Sales per Sqft]\n"
                + "AS '[Measures].[Store Sales] / "
                + "  [Store].CurrentMember.Properties(\"Store Sqft\")'\n"
                + "SELECT \n"
                + "  {[Measures].[Unit Sales], [Measures].[Store Sales per Sqft]} ON COLUMNS,\n"
                + "  {[Store].[Store Name].members} ON ROWS\n"
                + "FROM Sales" );
        Member member;
        Cell cell;
        member = result.getAxes()[ 1 ].getPositions().get( 18 ).get( 0 );
        assertEquals(
            "[Store].[Store].[USA].[WA].[Bellingham].[Store 2]",
            member.getUniqueName() );
        cell = result.getCell( new int[] { 0, 18 } );
        assertEquals( "2,237", cell.getFormattedValue() );
        cell = result.getCell( new int[] { 1, 18 } );
        assertEquals( ".17", cell.getFormattedValue() );
        member = result.getAxes()[ 1 ].getPositions().get( 3 ).get( 0 );
        assertEquals(
            "[Store].[Store].[Mexico].[DF].[San Andres].[Store 21]",
            member.getUniqueName() );
        cell = result.getCell( new int[] { 0, 3 } );
        assertEquals( "", cell.getFormattedValue() );
        cell = result.getCell( new int[] { 1, 3 } );
        assertEquals( "", cell.getFormattedValue() );
    }

}
