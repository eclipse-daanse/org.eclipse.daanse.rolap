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
package org.eclipse.daanse.olap.function.def.union;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;


@RolapContextTest(FoodmartTestInstance.class)
class UnionFunDefTest {


    @Test
    void testUnionAll(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Union({[Gender].[M]}, {[Gender].[F]}, ALL)")
            .returns(
            "[Gender].[Gender].[M]\n"
                + "[Gender].[Gender].[F]" ); // order is preserved
    }

    @Test
    void testUnionAllTuple(Context<?> context) {
        // With the bug, the last 8 rows are repeated.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "set [Set1] as 'Crossjoin({[Time].[1997].[Q1]:[Time].[1997].[Q4]},{[Store].[USA].[CA]:[Store].[USA].[OR]})'\n"
                + "set [Set2] as 'Crossjoin({[Time].[1997].[Q2]:[Time].[1997].[Q3]},{[Store].[Mexico].[DF]:[Store].[Mexico]"
                + ".[Veracruz]})'\n"
                + "select \n"
                + "{[Measures].[Unit Sales]} ON COLUMNS,\n"
                + "Union([Set1], [Set2], ALL) ON ROWS\n"
                + "from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1997].[Q1], [Store].[Store].[USA].[CA]}\n"
                + "{[Time].[Time].[1997].[Q1], [Store].[Store].[USA].[OR]}\n"
                + "{[Time].[Time].[1997].[Q2], [Store].[Store].[USA].[CA]}\n"
                + "{[Time].[Time].[1997].[Q2], [Store].[Store].[USA].[OR]}\n"
                + "{[Time].[Time].[1997].[Q3], [Store].[Store].[USA].[CA]}\n"
                + "{[Time].[Time].[1997].[Q3], [Store].[Store].[USA].[OR]}\n"
                + "{[Time].[Time].[1997].[Q4], [Store].[Store].[USA].[CA]}\n"
                + "{[Time].[Time].[1997].[Q4], [Store].[Store].[USA].[OR]}\n"
                + "{[Time].[Time].[1997].[Q2], [Store].[Store].[Mexico].[DF]}\n"
                + "{[Time].[Time].[1997].[Q2], [Store].[Store].[Mexico].[Guerrero]}\n"
                + "{[Time].[Time].[1997].[Q2], [Store].[Store].[Mexico].[Jalisco]}\n"
                + "{[Time].[Time].[1997].[Q2], [Store].[Store].[Mexico].[Veracruz]}\n"
                + "{[Time].[Time].[1997].[Q3], [Store].[Store].[Mexico].[DF]}\n"
                + "{[Time].[Time].[1997].[Q3], [Store].[Store].[Mexico].[Guerrero]}\n"
                + "{[Time].[Time].[1997].[Q3], [Store].[Store].[Mexico].[Jalisco]}\n"
                + "{[Time].[Time].[1997].[Q3], [Store].[Store].[Mexico].[Veracruz]}\n"
                + "Row #0: 16,890\n"
                + "Row #1: 19,287\n"
                + "Row #2: 18,052\n"
                + "Row #3: 15,079\n"
                + "Row #4: 18,370\n"
                + "Row #5: 16,940\n"
                + "Row #6: 21,436\n"
                + "Row #7: 16,353\n"
                + "Row #8: \n"
                + "Row #9: \n"
                + "Row #10: \n"
                + "Row #11: \n"
                + "Row #12: \n"
                + "Row #13: \n"
                + "Row #14: \n"
                + "Row #15: \n" );
    }

    @Test
    void testUnion(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Union({[Store].[USA], [Store].[USA], [Store].[USA].[OR]}, "
                + "{[Store].[USA].[CA], [Store].[USA]})")
            .returns(
            "[Store].[Store].[USA]\n"
                + "[Store].[Store].[USA].[OR]\n"
                + "[Store].[Store].[USA].[CA]" );
    }

    @Test
    void testUnionEmptyBoth(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Union({}, {})")
            .returns(
            "" );
    }

    @Test
    void testUnionEmptyRight(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Union({[Gender].[M]}, {})")
            .returns(
            "[Gender].[Gender].[M]" );
    }

    @Test
    void testUnionTuple(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Union({"
                + " ([Gender].[M], [Marital Status].[S]),"
                + " ([Gender].[F], [Marital Status].[S])"
                + "}, {"
                + " ([Gender].[M], [Marital Status].[M]),"
                + " ([Gender].[M], [Marital Status].[S])"
                + "})")
            .returns(
            "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M]}" );
    }

    @Test
    void testUnionTupleDistinct(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Union({"
                + " ([Gender].[M], [Marital Status].[S]),"
                + " ([Gender].[F], [Marital Status].[S])"
                + "}, {"
                + " ([Gender].[M], [Marital Status].[M]),"
                + " ([Gender].[M], [Marital Status].[S])"
                + "}, Distinct)")
            .returns(
            "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M]}" );
    }

    @Test
    void testUnionQuery(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales], "
                + "[Measures].[Store Cost], "
                + "[Measures].[Store Sales]} on columns,\n"
                + " Hierarchize(\n"
                + "   Union(\n"
                + "     Crossjoin(\n"
                + "       Crossjoin([Gender].[All Gender].children,\n"
                + "                 [Marital Status].[All Marital Status].children),\n"
                + "       Crossjoin([Customers].[All Customers].children,\n"
                + "                 [Product].[All Products].children) ),\n"
                + "     Crossjoin({([Gender].[All Gender].[M], [Marital Status].[All Marital Status].[M])},\n"
                + "       Crossjoin(\n"
                + "         [Customers].[All Customers].[USA].children,\n"
                + "         [Product].[All Products].children) ) )) on rows\n"
                + "from Sales where ([Time].[1997])" );
        final Axis rowsAxis = result.getAxes()[ 1 ];
        assertEquals( 45, rowsAxis.getPositions().size() );
    }


}
