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
package org.eclipse.daanse.olap.function.def.string;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class LenFunDefTest {

    @Test
    void testLenFunctionWithNullString(Context<?> context) {
        // SSAS2005 returns 0
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member [Measures].[Foo] as ' NULL '\n"
                + " member [Measures].[Bar] as ' len([Measures].[Foo]) '\n"
                + "select [Measures].[Bar] on 0\n"
                + "from [Warehouse and Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Bar]}\n"
                + "Row #0: 0\n" );
        // same, but inline
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "len(null)").returns( 0, 0 );
    }

}
