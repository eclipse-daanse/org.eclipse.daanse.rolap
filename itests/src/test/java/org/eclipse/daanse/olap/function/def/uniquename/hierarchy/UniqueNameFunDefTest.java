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
package org.eclipse.daanse.olap.function.def.uniquename.hierarchy;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;


@RolapContextTest(FoodmartTestInstance.class)
class UniqueNameFunDefTest {

    @Test
    void testHierarchyUniqueName(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Gender].DefaultMember.Hierarchy.UniqueName")
            .returns( "[Gender].[Gender]" );
    }

    @Test
    void testTime(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Time].[1997].[Q1].[1].Hierarchy.UniqueName").returns( "[Time].[Time]" );
    }

    @Test
    void testBasic9(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Gender].[All Gender].[F].Hierarchy.UniqueName").returns( "[Gender].[Gender]" );
    }

    @Test
    void testFirstInLevel9(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Education Level].[All Education Levels].[Bachelors Degree].Hierarchy.UniqueName")
            .returns( "[Education Level].[Education Level]" );
    }

    @Test
    void testHierarchyAll(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Gender].[All Gender].Hierarchy.UniqueName").returns( "[Gender].[Gender]" );
    }

}
