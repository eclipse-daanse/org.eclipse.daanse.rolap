/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
* All Rights Reserved.
* 
* ---- All changes after Fork in 2023 ------------------------
*
* Project: Eclipse daanse
*
* Copyright (c) 2023 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors after Fork in 2023:
*   SmartCity Jena - initial
*/


package org.eclipse.daanse.olap;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.io.IOException;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * <code>NullMemberRepresentationTest</code> tests the null member
 * custom representation feature supported via
 * {@link SystemWideProperties#NullMemberRepresentation} property.
 * @author ajogleka
 */
@RolapContextTest(FoodmartTestInstance.class)
class NullMemberRepresentationTest {

    @Test
    void testClosingPeriodMemberLeafWithCustomNullRepresentation(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member [Measures].[Foo] as ' ClosingPeriod().uniquename '\n"
            + "select {[Measures].[Foo]} on columns,\n"
            + "  {[Time].[1997],\n"
            + "   [Time].[1997].[Q2],\n"
            + "   [Time].[1997].[Q2].[4]} on rows\n"
            + "from Sales")
            .returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Foo]}\n"
            + "Axis #2:\n"
            + "{[Time].[Time].[1997]}\n"
            + "{[Time].[Time].[1997].[Q2]}\n"
            + "{[Time].[Time].[1997].[Q2].[4]}\n"
            + "Row #0: [Time].[Time].[1997].[Q4]\n"
            + "Row #1: [Time].[Time].[1997].[Q2].[6]\n"
            + "Row #2: [Time].[Time].["
            + getNullMemberRepresentation()
            + "]\n"
            + "");
    }

    @Test
    void testItemMemberWithCustomNullMemberRepresentation(Context<?> context)
        throws IOException
    {
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatExpr(connection, "Sales",
            "[Time].[1997].Children.Item(6).UniqueName")
            .returns( "[Time].[Time].[" + getNullMemberRepresentation() + "]" );
        assertThatExpr(connection, "Sales",
            "[Time].[1997].Children.Item(-1).UniqueName")
            .returns( "[Time].[Time].[" + getNullMemberRepresentation() + "]" );
    }

    void testNullMemberWithCustomRepresentation(Context<?> context) throws IOException {
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatExpr(connection, "Sales",
            "[Gender].[All Gender].Parent.UniqueName")
            .returns( "[Gender].[" + getNullMemberRepresentation() + "]" );

        assertThatExpr(connection, "Sales",
            "[Gender].[All Gender].Parent.Name").returns( getNullMemberRepresentation() );
    }

    private String getNullMemberRepresentation() {
        return RolapUtil.mdxNullLiteral();
    }

}
