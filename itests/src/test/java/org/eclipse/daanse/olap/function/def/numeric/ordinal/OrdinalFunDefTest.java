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
package org.eclipse.daanse.olap.function.def.numeric.ordinal;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class OrdinalFunDefTest {

    @Test
    void testOrdinal(Context<?> context) {
        //final Context<?> testContext<?> =
        //  getContext().withCube( "Sales Ragged" );
        Connection connection = context.getConnectionWithDefaultRole();
        Cell cell =
            executeQuery(connection, "with member [Measures].[Foo] as "
                + Util.singleQuoteString("[Store].[All Stores].[Vatican].ordinal")
                + " select {[Measures].[Foo]} on columns from [Sales Ragged]")
                .getCell(new int[] { 0 });
        assertEquals(
            1,
            ( (Number) cell.getValue() ).intValue(), "Vatican is at level 1.");

        cell = executeQuery(connection, "with member [Measures].[Foo] as "
                + Util.singleQuoteString("[Store].[All Stores].[USA].[Washington].ordinal")
                + " select {[Measures].[Foo]} on columns from [Sales Ragged]")
                .getCell(new int[] { 0 });
        assertEquals(
            3,
            ( (Number) cell.getValue() ).intValue(), "Washington is at level 3.");
    }

}
