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
package org.eclipse.daanse.olap.function.def.linreg;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class PointFunDefTest {


    @Test
    void testLinRegPointQuarter(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Measures].[Test] as \n"
                + "  'LinRegPoint(\n"
                + "    Rank(Time.[Time].CurrentMember, Time.[Time].CurrentMember.Level.Members),\n"
                + "    Descendants([Time].[1997], [Time].[Quarter]), \n"
                + "[Measures].[Store Sales], \n"
                + "    Rank(Time.[Time].CurrentMember, Time.[Time].CurrentMember.Level.Members))' \n"
                + "SELECT \n"
                + "{[Measures].[Test],[Measures].[Store Sales]} ON ROWS, \n"
                + "{[Time].[1997].Children} ON COLUMNS \n"
                + "FROM Sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "{[Time].[Time].[1997].[Q2]}\n"
                + "{[Time].[Time].[1997].[Q3]}\n"
                + "{[Time].[Time].[1997].[Q4]}\n"
                + "Axis #2:\n"
                + "{[Measures].[Test]}\n"
                + "{[Measures].[Store Sales]}\n"
                + "Row #0: 134,299.22\n"
                + "Row #0: 138,972.76\n"
                + "Row #0: 143,646.30\n"
                + "Row #0: 148,319.85\n"
                + "Row #1: 139,628.35\n"
                + "Row #1: 132,666.27\n"
                + "Row #1: 140,271.89\n"
                + "Row #1: 152,671.62\n" );
    }
}
