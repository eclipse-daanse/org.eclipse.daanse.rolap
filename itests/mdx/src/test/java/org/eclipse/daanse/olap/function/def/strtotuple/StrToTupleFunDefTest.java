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
package org.eclipse.daanse.olap.function.def.strtotuple;

import static org.eclipse.daanse.olap.function.TestResources.hiersExcept;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatMemberExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class StrToTupleFunDefTest {


    @Test
    void testStrToTuple(Context<?> context) {
        // single dimension yields member
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{StrToTuple(\"[Time].[1997].[Q2]\", [Time])}")
            .returns( "[Time].[Time].[1997].[Q2]" );

        // multiple dimensions yield tuple
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{StrToTuple(\"([Gender].[F], [Time].[1997].[Q2])\", [Gender], [Time])}")
            .returns( "{[Gender].[Gender].[F], [Time].[Time].[1997].[Q2]}" );

        // todo: test for garbage at end of string
    }

    @Test
    @RolapConfig(key = ConfigConstants.IGNORE_INVALID_MEMBERS_DURING_QUERY, value = "true", type = Boolean.class)
    void testStrToTupleIgnoreInvalidMembers(Context<?> context) {
        // If any member is invalid, the whole tuple is null.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "StrToTuple(\"([Gender].[M], [Marital Status].[Separated])\","
                + " [Gender], [Marital Status])")
            .returns( "" );
    }

    @Test
    void testStrToTupleDuHierarchiesFails(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{StrToTuple(\"([Gender].[F], [Time].[1997].[Q2], [Gender].[M])\", [Gender], [Time], [Gender])}")
            .throwsMessage( "Tuple contains more than one member of hierarchy '[Gender].[Gender]'." );
    }

    @Test
    void testStrToTupleDupHierInSameDimensions(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "{StrToTuple("
                + "\"([Gender].[F], "
                + "[Time].[1997].[Q2], "
                + "[Time].[Weekly].[1997].[10])\","
                + " [Gender], "
                + "[Time].[Weekly]"
                + ", [Gender])}")
            .throwsMessage( "Tuple contains more than one member of hierarchy '[Gender].[Gender]'." );
    }

    @Test
    void testStrToTupleDepends(Context<?> context) {
        assertThatMemberExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToTuple(\"[Time].[1997].[Q2]\", [Time])")
            .dependsOn();

        // converted to scalar, depends set is larger
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToTuple(\"[Time].[1997].[Q2]\", [Time])")
            .dependsOn( hiersExcept( "[Time].[Time]" ) );

        assertThatMemberExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToTuple(\"[Time].[1997].[Q2], [Gender].[F]\", [Time], [Gender])")
            .dependsOn();

        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "StrToTuple(\"[Time].[1997].[Q2], [Gender].[F]\", [Time], [Gender])")
            .dependsOn( hiersExcept( "[Time].[Time]", "[Gender].[Gender]" ) );
    }

}
