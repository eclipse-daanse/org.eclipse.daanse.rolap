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
package org.eclipse.daanse.olap.function.def.headtail;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class HeadTailFunDefTest {


    @Test
    void testHead(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Head([Store].Children, 2)")
            .returns(
            "[Store].[Store].[Canada]\n"
                + "[Store].[Store].[Mexico]" );
    }

    @Test
    void testHeadNegative(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Head([Store].Children, 2 - 3)")
            .returns(
            "" );
    }

    @Test
    void testHeadDefault(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Head([Store].Children)")
            .returns(
            "[Store].[Store].[Canada]" );
    }

    @Test
    void testHeadOvershoot(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Head([Store].Children, 2 + 2)")
            .returns(
            "[Store].[Store].[Canada]\n"
                + "[Store].[Store].[Mexico]\n"
                + "[Store].[Store].[USA]" );
    }

    @Test
    void testHeadEmpty(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Head([Gender].[F].Children, 2)")
            .returns(
            "" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Head([Gender].[F].Children)")
            .returns(
            "" );
    }

    /**
     * Test case for bug 2488492, "Union between calc mem and head function throws exception"
     */
    @Test
    void testHeadBug(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "SELECT\n"
                + "                        UNION(\n"
                + "                            {([Customers].CURRENTMEMBER)},\n"
                + "                            HEAD(\n"
                + "                                {([Customers].CURRENTMEMBER)},\n"
                + "                                IIF(\n"
                + "                                    COUNT(\n"
                + "                                        FILTER(\n"
                + "                                            DESCENDANTS(\n"
                + "                                                [Customers].CURRENTMEMBER,\n"
                + "                                                [Customers].[Country]),\n"
                + "                                            [Measures].[Unit Sales] >= 66),\n"
                + "                                        INCLUDEEMPTY)> 0,\n"
                + "                                    1,\n"
                + "                                    0)),\n"
                + "                            ALL)\n"
                + "    ON AXIS(0)\n"
                + "FROM\n"
                + "    [Sales]\n")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "Row #0: 266,773\n"
                + "Row #0: 266,773\n" );

        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH\n"
                + "    MEMBER\n"
                + "        [Customers].[COG_OQP_INT_t2]AS '1',\n"
                + "        SOLVE_ORDER = 65535\n"
                + "SELECT\n"
                + "                        UNION(\n"
                + "                            {([Customers].[COG_OQP_INT_t2])},\n"
                + "                            HEAD(\n"
                + "                                {([Customers].CURRENTMEMBER)},\n"
                + "                                IIF(\n"
                + "                                    COUNT(\n"
                + "                                        FILTER(\n"
                + "                                            DESCENDANTS(\n"
                + "                                                [Customers].CURRENTMEMBER,\n"
                + "                                                [Customers].[Country]),\n"
                + "                                            [Measures].[Unit Sales]>= 66),\n"
                + "                                        INCLUDEEMPTY)> 0,\n"
                + "                                    1,\n"
                + "                                    0)),\n"
                + "                            ALL)\n"
                + "    ON AXIS(0)\n"
                + "FROM\n"
                + "    [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[COG_OQP_INT_t2]}\n"
                + "{[Customers].[Customers].[All Customers]}\n"
                + "Row #0: 1\n"
                + "Row #0: 266,773\n" );

        // More minimal test case. Also demonstrates similar problem with Tail.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Union(\n"
                + "  Union(\n"
                + "    Tail([Customers].[USA].[CA].Children, 2),\n"
                + "    Head([Customers].[USA].[WA].Children, 2),\n"
                + "    ALL),\n"
                + "  Tail([Customers].[USA].[OR].Children, 2),"
                + "  ALL)")
            .returns(
            "[Customers].[Customers].[USA].[CA].[West Covina]\n"
                + "[Customers].[Customers].[USA].[CA].[Woodland Hills]\n"
                + "[Customers].[Customers].[USA].[WA].[Anacortes]\n"
                + "[Customers].[Customers].[USA].[WA].[Ballard]\n"
                + "[Customers].[Customers].[USA].[OR].[W. Linn]\n"
                + "[Customers].[Customers].[USA].[OR].[Woodburn]" );
    }


    @Test
    void testTail(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Tail([Store].Children, 2)")
            .returns(
            "[Store].[Store].[Mexico]\n"
                + "[Store].[Store].[USA]" );
    }

    @Test
    void testTailNegative(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Tail([Store].Children, 2 - 3)")
            .returns(
            "" );
    }

    @Test
    void testTailDefault(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Tail([Store].Children)")
            .returns(
            "[Store].[Store].[USA]" );
    }

    @Test
    void testTailOvershoot(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Tail([Store].Children, 2 + 2)")
            .returns(
            "[Store].[Store].[Canada]\n"
                + "[Store].[Store].[Mexico]\n"
                + "[Store].[Store].[USA]" );
    }

    @Test
    void testTailEmpty(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Tail([Gender].[F].Children, 2)")
            .returns(
            "" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Tail([Gender].[F].Children)")
            .returns(
            "" );
    }

}
