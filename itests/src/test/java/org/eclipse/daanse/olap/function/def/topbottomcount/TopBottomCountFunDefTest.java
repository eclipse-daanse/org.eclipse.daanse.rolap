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
package org.eclipse.daanse.olap.function.def.topbottomcount;

import java.io.PrintWriter;
import java.io.StringWriter;
import static org.eclipse.daanse.olap.function.TestResources.hiersExcept;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatSetExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RolapContextTest(FoodmartTestInstance.class)
class TopBottomCountFunDefTest {

    private static final Logger LOGGER = LoggerFactory.getLogger( TopBottomCountFunDefTest.class );

    @Test
    void testBottomCount(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "BottomCount({[Promotion Media].[Media Type].members}, 2, [Measures].[Unit Sales])")
            .returns(
            "[Promotion Media].[Promotion Media].[Radio]\n"
                + "[Promotion Media].[Promotion Media].[Sunday Paper, Radio, TV]" );
    }

    @Test
    void testBottomCountUnordered(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "BottomCount({[Promotion Media].[Media Type].members}, 2)")
            .returns(
            "[Promotion Media].[Promotion Media].[Sunday Paper, Radio, TV]\n"
                + "[Promotion Media].[Promotion Media].[TV]" );
    }



    @Test
    void testTopCount(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "TopCount({[Promotion Media].[Media Type].members}, 2, [Measures].[Unit Sales])")
            .returns(
            "[Promotion Media].[Promotion Media].[No Media]\n"
                + "[Promotion Media].[Promotion Media].[Daily Paper, Radio, TV]" );
    }

    @Test
    void testTopCountUnordered(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "TopCount({[Promotion Media].[Media Type].members}, 2)")
            .returns(
            "[Promotion Media].[Promotion Media].[Bulk Mail]\n"
                + "[Promotion Media].[Promotion Media].[Cash Register Handout]" );
    }

    @Test
    void testTopCountTuple(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "TopCount([Customers].[Name].members,2,(Time.[1997].[Q1],[Measures].[Store Sales]))")
            .returns(
            "[Customers].[Customers].[USA].[WA].[Spokane].[Grace McLaughlin]\n"
                + "[Customers].[Customers].[USA].[WA].[Spokane].[Matt Bellah]" );
    }

    @Test
    void testTopCountEmpty(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "TopCount(Filter({[Promotion Media].[Media Type].members}, 1=0), 2, [Measures].[Unit Sales])")
            .returns(
            "" );
    }

    @Test
    void testTopCountDepends(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        checkTopBottomCountPercentDepends(connection, "TopCount" );
        checkTopBottomCountPercentDepends(connection, "TopPercent" );
        checkTopBottomCountPercentDepends(connection, "TopSum" );
        checkTopBottomCountPercentDepends(connection, "BottomCount" );
        checkTopBottomCountPercentDepends(connection, "BottomPercent" );
        checkTopBottomCountPercentDepends(connection, "BottomSum" );
    }

    private void checkTopBottomCountPercentDepends(Connection connection, String fun) {
        assertThatSetExpr(connection, "Sales",
            fun
                + "({[Promotion Media].[Promotion Media].[Media Type].members}, "
                + "2, [Measures].[Unit Sales])")
            .dependsOn( hiersExcept( "[Measures]", "[Promotion Media].[Promotion Media]" ) );

        if ( fun.endsWith( "Count" ) ) {
            assertThatSetExpr(connection, "Sales",
                fun + "({[Promotion Media].[Promotion Media].[Media Type].members}, 2)")
                .dependsOn();
        }
    }

    /**
     * Tests TopCount applied to a large result set.
     *
     * <p>Before optimizing (see FunUtil.partialSort), on a 2-core 32-bit 2.4GHz
     * machine, the 1st query took 14.5 secs, the 2nd query took 5.0 secs. After optimizing, who knows?
     */
    @Test
    void testTopCountHuge(Context<?> context) {
        // TODO convert printfs to trace
        final String query =
            "SELECT [Measures].[Store Sales] ON 0,\n"
                + "TopCount([Time].[Month].members * "
                + "[Customers].[Name].members, 3, [Measures].[Store Sales]) ON 1\n"
                + "FROM [Sales]";
        final String desiredResult =
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1997].[Q1].[3], [Customers].[Customers].[USA].[WA].[Spokane].[George Todero]}\n"
                + "{[Time].[Time].[1997].[Q3].[7], [Customers].[Customers].[USA].[WA].[Spokane].[James Horvat]}\n"
                + "{[Time].[Time].[1997].[Q4].[11], [Customers].[Customers].[USA].[WA].[Olympia].[Charles Stanley]}\n"
                + "Row #0: 234.83\n"
                + "Row #1: 199.46\n"
                + "Row #2: 191.90\n";
        long now = System.currentTimeMillis();
        assertThatQuery(context.getConnectionWithDefaultRole(), query)
            .returnsGrid( desiredResult );
        LOGGER.info( "first query took " + ( System.currentTimeMillis() - now ) );
        now = System.currentTimeMillis();
        assertThatQuery(context.getConnectionWithDefaultRole(), query)
            .returnsGrid( desiredResult );
        LOGGER.info( "second query took " + ( System.currentTimeMillis() - now ) );
    }


    /**
     * This is a test for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1187">MONDRIAN-1187</a>
     * <p/>
     * <p>The results should be equivalent</p>
     */
    @Test
    void testMondrian_1187(Context<?> context) {
        final String queryWithoutAlias =
            "WITH\n" + "SET [Top Count] AS\n"
                + "{\n" + "TOPCOUNT(\n" + "DISTINCT([Customers].[Name].Members),\n"
                + "5,\n" + "[Measures].[Unit Sales]\n" + ")\n" + "}\n" + "SELECT\n"
                + "[Top Count] * [Measures].[Unit Sales] on 0\n" + "FROM [Sales]\n"
                + "WHERE [Time].[1997].[Q1].[1] : [Time].[1997].[Q3].[8]";
        String queryWithAlias =
            "SELECT\n"
                + "TOPCOUNT( DISTINCT( [Customers].[Name].Members), 5, [Measures].[Unit Sales]) * [Measures].[Unit Sales] on "
                + "0\n"
                + "FROM [Sales]\n"
                + "WHERE [Time].[1997].[Q1].[1]:[Time].[1997].[Q3].[8]";
        final Result result = executeQuery(context.getConnectionWithDefaultRole(), queryWithoutAlias );
        assertThatQuery(context.getConnectionWithDefaultRole(),
            queryWithAlias)
            .returnsGrid(
            toString(result));
    }



    /**
     * Converts a {@link Result} to text in traditional format.
     *
     * @param result Query result
     * @return Result as text
     */
    private static String toString(Result result) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        result.print(pw);
        pw.flush();
        return sw.toString();
    }
}
