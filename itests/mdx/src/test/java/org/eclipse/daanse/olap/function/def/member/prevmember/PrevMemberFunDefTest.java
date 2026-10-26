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
package org.eclipse.daanse.olap.function.def.member.prevmember;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class PrevMemberFunDefTest {

    @Test
    void testAll2(Context<?> context) {
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(), "select {[Gender].PrevMember} ON COLUMNS from Sales" );
        // previous to [Gender].[All] is null, so no members are returned
        assertEquals( 0, result.getAxes()[ 0 ].getPositions().size() );
    }

    @Test
    void testBasic(Context<?> context) {
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "select {[Gender].[M].PrevMember} ON COLUMNS from Sales" );
        assertEquals(
            "F",
            result.getAxes()[ 0 ].getPositions().get( 0 ).get( 0 ).getName() );
    }

    @Test
    void testFirstInLevel(Context<?> context) {
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "select {[Gender].[F].PrevMember} ON COLUMNS from Sales" );
        assertEquals( 0, result.getAxes()[ 0 ].getPositions().size() );
    }

    @Test
    void testAll(Context<?> context) {
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(), "select {[Gender].PrevMember} ON COLUMNS from Sales" );
        // previous to [Gender].[All] is null, so no members are returned
        assertEquals( 0, result.getAxes()[ 0 ].getPositions().size() );
    }

    @Test
    void testCalculatedMember(Context<?> context) {
        // a calculated member is not among the members of its level, so it has no siblings
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "with member [Time].[Time].[X] as Aggregate({[Time].[1997].[Q1], [Time].[1997].[Q2]})\n"
                    + "select {[Time].[Time].[X].PrevMember, [Time].[Time].[X].NextMember,\n"
                    + " [Time].[Time].[X].Lag(2), [Time].[Time].[X].Lag(0)} ON COLUMNS from Sales" );
        assertEquals( 1, result.getAxes()[ 0 ].getPositions().size() );
        assertEquals( "X", result.getAxes()[ 0 ].getPositions().get( 0 ).get( 0 ).getName() );
    }

    @Test
    void testCalculatedMemberInTheSlicer(Context<?> context) {
        // Profit last Period of no previous period is the Profit of the months aggregated
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "with member [Time].[Time].[X] as Aggregate({[Time].[1997].[Q1].[2], [Time].[1997].[Q1].[3]}),"
                    + " SOLVE_ORDER = -100\n"
                    + "member [Measures].[P] as ([Measures].[Profit], [Time].[1997].[Q1].[2])"
                    + " + ([Measures].[Profit], [Time].[1997].[Q1].[3])\n"
                    + "select {[Measures].[Profit last Period], [Measures].[P]} ON COLUMNS from Sales\n"
                    + "where [Time].[Time].[X]" );
        assertEquals(
            result.getCell( new int[] { 1 } ).getValue(),
            result.getCell( new int[] { 0 } ).getValue() );
    }

}
