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
package org.eclipse.daanse.olap.function.def.drilldownleveltopbottom;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class DrilldownLevelTopBottomFunDefTest {

    @Test
    void testDrilldownLevelTop(Context<?> context) {
        // <set>, <n>, <level>
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop({[Store].[USA]}, 2, [Store].[Store Country])")
            .returns(
            "[Store].[Store].[USA]\n"
                + "[Store].[Store].[USA].[WA]\n"
                + "[Store].[Store].[USA].[CA]" );

        // similarly DrilldownLevelBottom
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelBottom({[Store].[USA]}, 2, [Store].[Store Country])")
            .returns(
            "[Store].[Store].[USA]\n"
                + "[Store].[Store].[USA].[OR]\n"
                + "[Store].[Store].[USA].[CA]" );

        // <set>, <n>
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop({[Store].[USA]}, 2)")
            .returns(
            "[Store].[Store].[USA]\n"
                + "[Store].[Store].[USA].[WA]\n"
                + "[Store].[Store].[USA].[CA]" );

        // <n> greater than number of children
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop({[Store].[USA], [Store].[Canada]}, 4)")
            .returns(
            "[Store].[Store].[USA]\n"
                + "[Store].[Store].[USA].[WA]\n"
                + "[Store].[Store].[USA].[CA]\n"
                + "[Store].[Store].[USA].[OR]\n"
                + "[Store].[Store].[Canada]\n"
                + "[Store].[Store].[Canada].[BC]" );

        // <n> negative
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop({[Store].[USA]}, 2 - 3)")
            .returns( "[Store].[Store].[USA]" );

        // <n> zero
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop({[Store].[USA]}, 2 - 2)")
            .returns( "[Store].[Store].[USA]" );

        // <n> null
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop({[Store].[USA]}, null)")
            .returns( "[Store].[Store].[USA]" );

        // mixed bag, no level, all expanded
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop({[Store].[USA], "
                + "[Store].[USA].[CA].[San Francisco], "
                + "[Store].[All Stores], "
                + "[Store].[Canada].[BC]}, "
                + "2)")
            .returns(
            "[Store].[Store].[USA]\n"
                + "[Store].[Store].[USA].[WA]\n"
                + "[Store].[Store].[USA].[CA]\n"
                + "[Store].[Store].[USA].[CA].[San Francisco]\n"
                + "[Store].[Store].[USA].[CA].[San Francisco].[Store 14]\n"
                + "[Store].[Store].[All Stores]\n"
                + "[Store].[Store].[USA]\n"
                + "[Store].[Store].[Canada]\n"
                + "[Store].[Store].[Canada].[BC]\n"
                + "[Store].[Store].[Canada].[BC].[Vancouver]\n"
                + "[Store].[Store].[Canada].[BC].[Victoria]" );

        // mixed bag, only specified level expanded
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop({[Store].[USA], "
                + "[Store].[USA].[CA].[San Francisco], "
                + "[Store].[All Stores], "
                + "[Store].[Canada].[BC]}, 2, [Store].[Store City])")
            .returns(
            "[Store].[Store].[USA]\n"
                + "[Store].[Store].[USA].[CA].[San Francisco]\n"
                + "[Store].[Store].[USA].[CA].[San Francisco].[Store 14]\n"
                + "[Store].[Store].[All Stores]\n"
                + "[Store].[Store].[Canada].[BC]" );

        // bad level
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop({[Store].[USA]}, 2, [Customers].[Country])")
            .throwsMessage(
            "Level '[Customers].[Customers].[Country]' not compatible with "
                + "member '[Store].[Store].[USA]'" );
    }

    @Test
    void testDrilldownMemberEmptyExpr(Context<?> context) {
        // no level, with expression
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop({[Store].[USA]}, 2, , [Measures].[Unit Sales])")
            .returns(
            "[Store].[Store].[USA]\n"
                + "[Store].[Store].[USA].[WA]\n"
                + "[Store].[Store].[USA].[CA]" );

        // reverse expression
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "DrilldownLevelTop("
                + "{[Store].[USA]}, 2, , - [Measures].[Unit Sales])")
            .returns(
            "[Store].[Store].[USA]\n"
                + "[Store].[Store].[USA].[OR]\n"
                + "[Store].[Store].[USA].[CA]" );
    }

}
