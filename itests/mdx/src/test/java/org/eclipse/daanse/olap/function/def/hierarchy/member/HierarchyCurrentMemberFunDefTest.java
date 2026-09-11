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
package org.eclipse.daanse.olap.function.def.hierarchy.member;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.eclipse.daanse.olap.function.TestResources.hiersExcept;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatMemberExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class HierarchyCurrentMemberFunDefTest {

    @Test
    void testCurrentMember(Context<?> context) {
        // <Dimension>.CurrentMember
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatAxis(connection, "Sales", "[Gender].CurrentMember").returns( "[Gender].[Gender].[All Gender]" );
        // <Hierarchy>.CurrentMember
        assertThatAxis(connection, "Sales",
            "[Gender].Hierarchy.CurrentMember").returns( "[Gender].[Gender].[All Gender]" );

        // <Level>.CurrentMember
        // MSAS doesn't allow this, but Mondrian does: it implicitly casts
        // level to hierarchy.
        assertThatAxis(connection, "Sales", "[Store Name].CurrentMember").returns( "[Store].[Store].[All Stores]" );
    }

    @Disabled //disabled for CI build
    @Test
    void testCurrentMemberDepends(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatMemberExpr(connection, "Sales",
            "[Gender].CurrentMember")
            .dependsOn( "[Gender]" );

        assertThatExpr(connection, "Sales",
            "[Gender].[M].Dimension.Name").dependsOn();
        // implicit call to .CurrentMember when dimension is used as a member
        // expression
        assertThatMemberExpr(connection, "Sales",
            "[Gender].[M].Dimension")
            .dependsOn( "[Gender]" );

        assertThatMemberExpr(connection, "Sales",
            "[Gender].[M].Dimension.CurrentMember").dependsOn( "[Gender]" );
        assertThatMemberExpr(connection, "Sales",
            "[Gender].[M].Dimension.CurrentMember.Parent").dependsOn( "[Gender]" );

        // [Customers] is short for [Customers].CurrentMember, so
        // depends upon everything
        assertThatExpr(connection, "Sales",
            "[Customers]").dependsOn( hiersExcept() );
    }

    @Test
    void testCurrentMemberFromSlicer(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "with member [Measures].[Foo] as '[Gender].CurrentMember.Name'\n"
                + "select {[Measures].[Foo]} on columns\n"
                + "from Sales where ([Gender].[F])" );
        assertEquals( "F", result.getCell( new int[] { 0 } ).getValue() );
    }

    @Test
    void testCurrentMemberFromDefaultMember(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "with member [Measures].[Foo] as"
                + " '[Time].[Time].CurrentMember.Name'\n"
                + "select {[Measures].[Foo]} on columns\n"
                + "from Sales" );
        assertEquals( "1997", result.getCell( new int[] { 0 } ).getValue() );
    }

    @Test
    void testCurrentMemberMultiHierarchy(Context<?> context) {
        final String hierarchyName = "Weekly";
        final String queryString =
            "with member [Measures].[Foo] as\n"
                + " 'IIf(([Time].[Time].CurrentMember.Hierarchy.Name = \""
                + hierarchyName
                + "\"), \n"
                + "[Measures].[Unit Sales], \n"
                + "- [Measures].[Unit Sales])'\n"
                + "select {[Measures].[Unit Sales], [Measures].[Foo]} ON COLUMNS,\n"
                + "  {[Product].[Food].[Dairy]} ON ROWS\n"
                + "from [Sales]";
        Connection connection = context.getConnectionWithDefaultRole();
        Result result =
            executeQuery(connection,
                queryString + " where [Time].[1997]" );
        final int[] coords = { 1, 0 };
        assertEquals(
            "-12,885",
            result.getCell( coords ).getFormattedValue() );

        // As above, but context provided on rows axis as opposed to slicer.
        final String queryString1 =
            "with member [Measures].[Foo] as\n"
                + " 'IIf(([Time].[Time].CurrentMember.Hierarchy.Name = \""
                + hierarchyName
                + "\"), \n"
                + "[Measures].[Unit Sales], \n"
                + "- [Measures].[Unit Sales])'\n"
                + "select {[Measures].[Unit Sales], [Measures].[Foo]} ON COLUMNS,";

        final String queryString2 =
            "from [Sales]\n"
                + "  where [Product].[Food].[Dairy] ";

        result =
            executeQuery(connection,
                queryString1 + " {[Time].[1997]} ON ROWS " + queryString2 );
        assertEquals(
            "-12,885",
            result.getCell( coords ).getFormattedValue() );

        result =
            executeQuery(connection,
                queryString + " where [Time].[Weekly].[1997]" );
        assertEquals(
            "-12,885",
            result.getCell( coords ).getFormattedValue() );

        result =
            executeQuery(connection,
                queryString1 + " {[Time].[Weekly].[1997]} ON ROWS "
                    + queryString2 );
        assertEquals(
            "-12,885",
            result.getCell( coords ).getFormattedValue() );
    }


    @Test
    void testCurrentMemberFromAxis(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "with member [Measures].[Foo] as"
                + " '[Gender].CurrentMember.Name"
                + " || [Marital Status].CurrentMember.Name'\n"
                + "select {[Measures].[Foo]} on columns,\n"
                + " CrossJoin({[Gender].children},"
                + "           {[Marital Status].children}) on rows\n"
                + "from Sales" );
        assertEquals( "FM", result.getCell( new int[] { 0, 0 } ).getValue() );
    }

    /**
     * When evaluating a calculated member, MSOLAP regards that calculated member as the current member of that dimension,
     * so it cycles in this case. But I disagree; it is the previous current member, before the calculated member was
     * expanded.
     */
    @Test
    void testCurrentMemberInCalcMember(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "with member [Measures].[Foo] as '[Measures].CurrentMember.Name'\n"
                + "select {[Measures].[Foo]} on columns\n"
                + "from Sales" );
        assertEquals(
            "Unit Sales", result.getCell( new int[] { 0 } ).getValue() );
    }

}
