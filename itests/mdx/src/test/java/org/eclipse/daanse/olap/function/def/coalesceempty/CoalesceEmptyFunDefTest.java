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
package org.eclipse.daanse.olap.function.def.coalesceempty;

import static org.eclipse.daanse.olap.function.TestResources.checkDataResults;
import static org.eclipse.daanse.olap.function.TestResources.hiersExcept;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatExpr;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class CoalesceEmptyFunDefTest {

    @Test
    void testCoalesceEmptyDepends(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "coalesceempty([Time].[1997], [Gender].[M])")
            .dependsOn( hiersExcept() );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "coalesceempty(([Measures].[Unit Sales], [Time].[1997]),"
                + " ([Measures].[Store Sales], [Time].[1997].[Q2]))")
            .dependsOn( hiersExcept( "[Measures]", "[Time].[Time]" ) );
    }

    @Test
    void testCoalesceEmpty(Context<?> context) {
        // [DF] is all null and [WA] has numbers for 1997 but not for 1998.
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "with\n"
                + "    member Measures.[Coal1] as 'coalesceempty(([Time].[1997], Measures.[Store Sales]), ([Time].[1998], "
                + "Measures.[Store Sales]))'\n"
                + "    member Measures.[Coal2] as 'coalesceempty(([Time].[1997], Measures.[Unit Sales]), ([Time].[1998], "
                + "Measures.[Unit Sales]))'\n"
                + "select \n"
                + "    {Measures.[Coal1], Measures.[Coal2]} on columns,\n"
                + "    {[Store].[All Stores].[Mexico].[DF], [Store].[All Stores].[USA].[WA]} on rows\n"
                + "from \n"
                + "    [Sales]" );

        checkDataResults(
            new Double[][] {
                new Double[] { null, null },
                new Double[] { new Double( 263793.22 ), new Double( 124366 ) }
            },
            result,
            0.001 );

        result = executeQuery(context.getConnectionWithDefaultRole(),
            "with\n"
                + "    member Measures.[Sales Per Customer] as 'Measures.[Sales Count] / Measures.[Customer Count]'\n"
                + "    member Measures.[Coal] as 'coalesceempty(([Measures].[Sales Per Customer], [Store].[All Stores]"
                + ".[Mexico].[DF]),\n"
                + "        Measures.[Sales Per Customer])'\n"
                + "select \n"
                + "    {Measures.[Sales Per Customer], Measures.[Coal]} on columns,\n"
                + "    {[Store].[All Stores].[Mexico].[DF], [Store].[All Stores].[USA].[WA]} on rows\n"
                + "from \n"
                + "    [Sales]\n"
                + "where\n"
                + "    ([Time].[1997].[Q2])" );

        checkDataResults(
            new Double[][] {
                new Double[] { null, null },
                new Double[] { new Double( 8.963 ), new Double( 8.963 ) }
            },
            result,
            0.001 );

        result = executeQuery(context.getConnectionWithDefaultRole(),
            "with\n"
                + "    member Measures.[Sales Per Customer] as 'Measures.[Sales Count] / Measures.[Customer Count]'\n"
                + "    member Measures.[Coal] as 'coalesceempty(([Measures].[Sales Per Customer], [Store].[All Stores]"
                + ".[Mexico].[DF]),\n"
                + "        ([Measures].[Sales Per Customer], [Store].[All Stores].[Mexico].[DF]),\n"
                + "        ([Measures].[Sales Per Customer], [Store].[All Stores].[Mexico].[DF]),\n"
                + "        ([Measures].[Sales Per Customer], [Store].[All Stores].[Mexico].[DF]),\n"
                + "        ([Measures].[Sales Per Customer], [Store].[All Stores].[Mexico].[DF]),\n"
                + "        ([Measures].[Sales Per Customer], [Store].[All Stores].[Mexico].[DF]),\n"
                + "        ([Measures].[Sales Per Customer], [Store].[All Stores].[Mexico].[DF]),\n"
                + "        ([Measures].[Sales Per Customer], [Store].[All Stores].[Mexico].[DF]),\n"
                + "        Measures.[Sales Per Customer])'\n"
                + "select \n"
                + "    {Measures.[Sales Per Customer], Measures.[Coal]} on columns,\n"
                + "    {[Store].[All Stores].[Mexico].[DF], [Store].[All Stores].[USA].[WA]} on rows\n"
                + "from \n"
                + "    [Sales]\n"
                + "where\n"
                + "    ([Time].[1997].[Q2])" );

        checkDataResults(
            new Double[][] {
                new Double[] { null, null },
                new Double[] { new Double( 8.963 ), new Double( 8.963 ) }
            },
            result,
            0.001 );
    }

    @Test
    void testCoalesceEmpty2(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "with\n"
                + "    member Measures.[Sales Per Customer] as 'Measures.[Sales Count] / Measures.[Customer Count]'\n"
                + "    member Measures.[Coal] as 'coalesceempty(([Measures].[Sales Per Customer], [Store].[All Stores]"
                + ".[Mexico].[DF]),\n"
                + "        Measures.[Sales Per Customer])'\n"
                + "select \n"
                + "    {Measures.[Sales Per Customer], Measures.[Coal]} on columns,\n"
                + "    {[Store].[All Stores].[Mexico].[DF], [Store].[All Stores].[USA].[WA]} on rows\n"
                + "from \n"
                + "    [Sales]\n"
                + "where\n"
                + "    ([Time].[1997].[Q2])" );

        checkDataResults(
            new Double[][] {
                new Double[] { null, null },
                new Double[] { 8.963D, 8.963D }
            },
            result,
            0.001 );
    }

    @Test
    void testBrokenContextBug(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "with\n"
                + "    member Measures.[Sales Per Customer] as 'Measures.[Sales Count] / Measures.[Customer Count]'\n"
                + "    member Measures.[Coal] as 'coalesceempty(([Measures].[Sales Per Customer], [Store].[All Stores]"
                + ".[Mexico].[DF]),\n"
                + "        Measures.[Sales Per Customer])'\n"
                + "select \n"
                + "    {Measures.[Coal]} on columns,\n"
                + "    {[Store].[All Stores].[USA].[WA]} on rows\n"
                + "from \n"
                + "    [Sales]\n"
                + "where\n"
                + "    ([Time].[1997].[Q2])" );

        checkDataResults( new Double[][] { { new Double( 8.963 ) } }, result, 0.001 );
    }

}
