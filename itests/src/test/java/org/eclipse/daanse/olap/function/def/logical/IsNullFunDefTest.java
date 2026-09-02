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
package org.eclipse.daanse.olap.function.def.logical;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;


import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class IsNullFunDefTest {

    @Test
    void testIsNull(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " Measures.[Profit] IS NULL " ).isFalse();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " Store.[All Stores] IS NULL " ).isFalse();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " Store.[All Stores].parent IS NULL " ).isTrue();
    }

    @Test
    void testIsNullWithCalcMem(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member Store.foo as '1010' "
                + "member measures.bar as 'Store.currentmember IS NULL' "
                + "SELECT measures.bar on 0, {Store.foo} on 1 from sales").returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[bar]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[foo]}\n"
                + "Row #0: false\n" );
    }

}
