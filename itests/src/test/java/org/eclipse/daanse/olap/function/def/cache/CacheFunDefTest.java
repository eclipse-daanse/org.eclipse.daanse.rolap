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
package org.eclipse.daanse.olap.function.def.cache;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class CacheFunDefTest {

    @Test
    void testCache(Context<?> context) {
        // test various data types: integer, string, member, set, tuple
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Cache(1 + 2)").returns("3");
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Cache('foo' || 'bar')").returns("foobar" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "[Gender].Children").returns(
            "[Gender].[Gender].[F]\n"
                + "[Gender].[Gender].[M]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "([Gender].[M], [Marital Status].[S].PrevMember)").returns(
            "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M]}" );

        // inside another expression
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Order(Cache([Gender].Children), Cache(([Measures].[Unit Sales], [Time].[1997].[Q1])), BDESC)").returns(
            "[Gender].[Gender].[M]\n"
                + "[Gender].[Gender].[F]" );

        // doesn't work with multiple args
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Cache(1, 2)").throwsMessage(
            "No function matches signature 'Cache(<Numeric Expression>, <Numeric Expression>)'" );
    }


}
