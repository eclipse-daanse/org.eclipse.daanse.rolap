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

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class IsFunDefTest {

    @Test
    void testIsMember(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " Store.[USA].parent IS Store.[All Stores]").isTrue();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " [Store].[USA].[CA].parent IS [Store].[Mexico]").isFalse();
    }

    @Test
    void testIsString(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " [Store].[USA].Name IS \"USA\" ")
            .throwsMessage( "No function matches signature '<String> IS <String>'" );
    }

    @Test
    void testIsNumeric(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " [Store].[USA].Level.Ordinal IS 25 ")
            .throwsMessage( "No function matches signature '<Numeric Expression> IS <Numeric Expression>'" );
    }

    @Test
    void testIsTuple(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " (Store.[USA], Gender.[M]) IS (Store.[USA], Gender.[M])").isTrue();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " (Store.[USA], Gender.[M]) IS (Gender.[M], Store.[USA])").isTrue();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " (Store.[USA], Gender.[M]) IS (Gender.[M], Store.[USA]) "
                + "OR [Gender] IS NULL")
            .isTrue();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " (Store.[USA], Gender.[M]) IS (Gender.[M], Store.[USA]) "
                + "AND [Gender] IS NULL")
            .isFalse();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " (Store.[USA], Gender.[M]) IS (Store.[USA], Gender.[F])")
            .isFalse();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " (Store.[USA], Gender.[M]) IS (Store.[USA])")
            .isFalse();
        assertThatExpr(context.getConnectionWithDefaultRole(),
                "Sales", " (Store.[USA], Gender.[M]) IS Store.[USA]")
            .isFalse();
    }

    @Test
    void testIsLevel(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " Store.[USA].level IS Store.[Store Country] ").isTrue();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " Store.[USA].[CA].level IS Store.[Store Country] ").isFalse();
    }

    @Test
    void testIsHierarchy(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " Store.[USA].hierarchy IS Store.[Mexico].hierarchy ").isTrue();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            " Store.[USA].hierarchy IS Gender.[M].hierarchy ").isFalse();
    }

    @Test
    void testIsDimension(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " Store.[USA].dimension IS Store ").isTrue();
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " Gender.[M].dimension IS Store ").isFalse();
    }

}
