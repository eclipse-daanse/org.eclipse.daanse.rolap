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
package org.eclipse.daanse.olap.function.def.member.nextmember;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class NextMemberFunDefTest {

    @Test
    void testBasic2(Context<?> context) {
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "select {[Gender].[F].NextMember} ON COLUMNS from Sales" );
        assertEquals(
            "M",
            result.getAxes()[ 0 ].getPositions().get( 0 ).get( 0 ).getName() );
    }

    @Test
    void testFirstInLevel2(Context<?> context) {
        Result result =
            executeQuery(context.getConnectionWithDefaultRole(),
                "select {[Gender].[M].NextMember} ON COLUMNS from Sales" );
        assertEquals( 0, result.getAxes()[ 0 ].getPositions().size() );
    }

}
