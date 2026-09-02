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
package org.eclipse.daanse.olap.function.def.level.numeric;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class LevelsNumericPropertyDefTest {

    @Test
    void testLevelsNumeric(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatExpr(connection, "Sales", "[Time].[Time].Levels(2).Name").returns( "Month" );
        assertThatExpr(connection, "Sales", "[Time].[Time].Levels(0).Name").returns( "Year" );
        assertThatExpr(connection, "Sales", "[Product].Levels(0).Name").returns( "(All)" );
    }

    @Test
    void testLevelsTooSmall(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Time].[Time].Levels(-1).Name").throwsMessage( "Index '-1' out of bounds" );
    }

    @Test
    void testLevelsTooLarge(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Time].[Time].Levels(8).Name").throwsMessage( "Index '8' out of bounds" );
    }

}
