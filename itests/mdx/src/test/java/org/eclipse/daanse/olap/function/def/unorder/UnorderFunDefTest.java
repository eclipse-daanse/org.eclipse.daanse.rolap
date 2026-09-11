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
package org.eclipse.daanse.olap.function.def.unorder;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;


@RolapContextTest(FoodmartTestInstance.class)
class UnorderFunDefTest {

    @Test
    void testUnorder(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Unorder([Gender].members)")
            .returns(
            "[Gender].[Gender].[All Gender]\n"
                + "[Gender].[Gender].[F]\n"
                + "[Gender].[Gender].[M]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Unorder(Order([Gender].members, -[Measures].[Unit Sales]))")
            .returns(
            "[Gender].[Gender].[All Gender]\n"
                + "[Gender].[Gender].[M]\n"
                + "[Gender].[Gender].[F]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Unorder(Crossjoin([Gender].members, [Marital Status].[Marital Status].Children))")
            .returns(
            "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[M]}\n"
                + "{[Gender].[Gender].[All Gender], [Marital Status].[Marital Status].[S]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S]}" );

        // implicitly convert member to set
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Unorder([Gender].[M])")
            .returns(
            "[Gender].[Gender].[M]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Unorder(1 + 3)")
            .throwsMessage( "No function matches signature 'Unorder(<Numeric Expression>)'" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Unorder([Gender].[M], 1 + 3)")
            .throwsMessage( "No function matches signature 'Unorder(<Member>, <Numeric Expression>)'" );
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Store Sales], [Measures].[Unit Sales]} on 0,\n"
                + "  Unorder([Gender].Members) on 1\n"
                + "from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Store Sales]}\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Gender].[Gender].[All Gender]}\n"
                + "{[Gender].[Gender].[F]}\n"
                + "{[Gender].[Gender].[M]}\n"
                + "Row #0: 565,238.13\n"
                + "Row #0: 266,773\n"
                + "Row #1: 280,226.21\n"
                + "Row #1: 131,558\n"
                + "Row #2: 285,011.92\n"
                + "Row #2: 135,215\n" );
    }

}
