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
package org.eclipse.daanse.olap.function.def.topbottompercentsum;

import java.io.PrintWriter;
import java.io.StringWriter;
import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class TopBottomPercentSumFunDefTest {


    @Test
    void testBottomPercent(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "BottomPercent(Filter({[Store].[All Stores].[USA].[CA].Children, [Store].[All Stores].[USA].[OR].Children, "
                + "[Store].[All Stores].[USA].[WA].Children}, ([Measures].[Unit Sales] > 0.0)), 100.0, [Measures].[Store "
                + "Sales])")
            .returns(
            "[Store].[Store].[USA].[CA].[San Francisco]\n"
                + "[Store].[Store].[USA].[WA].[Walla Walla]\n"
                + "[Store].[Store].[USA].[WA].[Bellingham]\n"
                + "[Store].[Store].[USA].[WA].[Yakima]\n"
                + "[Store].[Store].[USA].[CA].[Beverly Hills]\n"
                + "[Store].[Store].[USA].[WA].[Spokane]\n"
                + "[Store].[Store].[USA].[WA].[Seattle]\n"
                + "[Store].[Store].[USA].[WA].[Bremerton]\n"
                + "[Store].[Store].[USA].[CA].[San Diego]\n"
                + "[Store].[Store].[USA].[CA].[Los Angeles]\n"
                + "[Store].[Store].[USA].[OR].[Portland]\n"
                + "[Store].[Store].[USA].[WA].[Tacoma]\n"
                + "[Store].[Store].[USA].[OR].[Salem]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "BottomPercent({[Promotion Media].[Media Type].members}, 1, [Measures].[Unit Sales])")
            .returns(
            "[Promotion Media].[Promotion Media].[Radio]\n"
                + "[Promotion Media].[Promotion Media].[Sunday Paper, Radio, TV]" );
    }

    // todo: test precision

    @Test
    void testBottomSum(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "BottomSum({[Promotion Media].[Media Type].members}, 5000, [Measures].[Unit Sales])")
            .returns(
            "[Promotion Media].[Promotion Media].[Radio]\n"
                + "[Promotion Media].[Promotion Media].[Sunday Paper, Radio, TV]" );
    }

    /**
     * Tests that TopPercent() operates succesfully on a axis of crossjoined tuples.  previously, this would fail with a
     * ClassCastException in FunUtil.java.  bug 1440306
     */
    @Test
    void testTopPercentCrossjoin(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{TopPercent(Crossjoin([Product].[Product Department].members,\n"
                + "[Time].[1997].children),10,[Measures].[Store Sales])}")
            .returns(
            "{[Product].[Product].[Food].[Produce], [Time].[Time].[1997].[Q4]}\n"
                + "{[Product].[Product].[Food].[Produce], [Time].[Time].[1997].[Q1]}\n"
                + "{[Product].[Product].[Food].[Produce], [Time].[Time].[1997].[Q3]}" );
    }


    @Test
    void testTopPercent(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "TopPercent({[Promotion Media].[Media Type].members}, 70, [Measures].[Unit Sales])")
            .returns(
            "[Promotion Media].[Promotion Media].[No Media]" );
    }

    // todo: test precision

    @Test
    void testTopSum(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "TopSum({[Promotion Media].[Media Type].members}, 200000, [Measures].[Unit Sales])")
            .returns(
            "[Promotion Media].[Promotion Media].[No Media]\n"
                + "[Promotion Media].[Promotion Media].[Daily Paper, Radio, TV]" );
    }

    @Test
    void testTopSumEmpty(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "TopSum(Filter({[Promotion Media].[Media Type].members}, 1=0), "
                + "200000, [Measures].[Unit Sales])")
            .returns(
            "" );
    }


    /**
     * This is a test for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-2157">MONDRIAN-2157</a>
     * <p/>
     * <p>The results should be equivalent either we use aliases or not</p>
     */
    @Test
    void testTopPercentWithAlias(Context<?> context) {
        final String queryWithoutAlias =
            "select\n"
                + " {[Measures].[Store Cost]}on rows,\n"
                + " TopPercent([Product].[Brand Name].Members*[Time].[1997].children,"
                + " 50, [Measures].[Unit Sales]) on columns\n"
                + "from Sales";
        String queryWithAlias =
            "with\n"
                + " set [*aaa] as '[Product].[Brand Name].Members*[Time].[1997].children'\n"
                + "select\n"
                + " {[Measures].[Store Cost]}on rows,\n"
                + " TopPercent([*aaa], 50, [Measures].[Unit Sales]) on columns\n"
                + "from Sales";

        final Result result = executeQuery(context.getConnectionWithDefaultRole(), queryWithoutAlias );
        assertThatQuery(context.getConnectionWithDefaultRole(),
            queryWithAlias)
            .returnsGrid(
            toString( result ) );
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
