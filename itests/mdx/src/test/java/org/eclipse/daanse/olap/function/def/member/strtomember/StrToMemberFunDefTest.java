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
package org.eclipse.daanse.olap.function.def.member.strtomember;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class StrToMemberFunDefTest {
    @Test
    void testStrToMember(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"[Time].[1997].[Q2].[4]\").Name")
            .returns( "4" );
    }

    @Test
    void testStrToMemberUniqueName(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"[Store].[USA].[CA]\").Name")
            .returns( "CA" );
    }

    @Test
    void testStrToMemberFullyQualifiedName(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"[Store].[All Stores].[USA].[CA]\").Name")
            .returns( "CA" );
    }

    @Test
    void testStrToMemberNull(Context<?> context) {
        // SSAS 2005 gives "#Error An MDX expression was expected. An empty
        // expression was specified."
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(null).Name")
            .throwsMessage( "An MDX expression was expected. An empty expression was specified" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToSet(null, [Gender]).Count")
            .throwsMessage( "An MDX expression was expected. An empty expression was specified" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToTuple(null, [Gender]).Name")
            .throwsMessage( "An MDX expression was expected. An empty expression was specified" );
    }

    /**
     * Testcase for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-560">
     * bug MONDRIAN-560, "StrToMember function doesn't use IgnoreInvalidMembers option"</a>.
     */
    @Test
    @RolapConfig(key = ConfigConstants.IGNORE_INVALID_MEMBERS_DURING_QUERY, value = "true", type = Boolean.class)
    void testStrToMemberIgnoreInvalidMembers(Context<?> context) {
        // [Product].[Drugs] is invalid, becomes null member, and is dropped
        // from list
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  {[Product].[Food],\n"
                + "    StrToMember(\"[Product].[Drugs]\")} on columns,\n"
                + "  {[Measures].[Unit Sales]} on rows\n"
                + "from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Product].[Product].[Food]}\n"
                + "Axis #2:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Row #0: 191,940\n" );

        // Hierarchy is inferred from leading edge
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"[Marital Status].[Separated]\").Hierarchy.Name")
            .returns( "Marital Status" );

        // Null member is returned
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"[Marital Status].[Separated]\").Name")
            .returns( "#null" );

        // Use longest valid prefix, so get [Time].[Weekly] rather than just
        // [Time].
        final String timeWeekly = "[Time].[Weekly]";
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"" + timeWeekly
                + ".[1996].[Q1]\").Hierarchy.UniqueName")
            .returns( timeWeekly );

        // If hierarchy is invalid, throw an error even though
        // IgnoreInvalidMembersDuringQuery is set.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"[Unknown Hierarchy].[Invalid].[Member]\").Name")
            .throwsMessage( "MDX object '[Unknown Hierarchy].[Invalid].[Member]' not found in cube 'Sales'" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"[Unknown Hierarchy].[Invalid]\").Name")
            .throwsMessage( "MDX object '[Unknown Hierarchy].[Invalid]' not found in cube 'Sales'" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"[Unknown Hierarchy]\").Name")
            .throwsMessage( "MDX object '[Unknown Hierarchy]' not found in cube 'Sales'" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"\")")
            .throwsMessage( "MDX object '' not found in cube 'Sales'" );
    }

    /**
     * Testcase for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-560">
     * bug MONDRIAN-560, "StrToMember function doesn't use IgnoreInvalidMembers option"</a>.
     *
     * <p>Companion to {@link #testStrToMemberIgnoreInvalidMembers}: with
     * IgnoreInvalidMembersDuringQuery left at its default (false), an invalid
     * member reference throws instead of being silently dropped.</p>
     */
    @Test
    void testStrToMemberDoNotIgnoreInvalidMembers(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  {[Product].[Food],\n"
                + "    StrToMember(\"[Product].[Drugs]\")} on columns,\n"
                + "  {[Measures].[Unit Sales]} on rows\n"
                + "from [Sales]")
            .throwsMessage( "Member '[Product].[Drugs]' not found" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToMember(\"[Marital Status].[Separated]\").Hierarchy.Name")
            .throwsMessage( "Member '[Marital Status].[Separated]' not found" );
    }

}
