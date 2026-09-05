/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2020 Hitachi Vantara..  All rights reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.rolap.aggmatcher;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * Test case for non-collapsed levels in agg tables, based on the "Species"
 * schema.
 */
@RolapContextTest(value = SpeciesNonCollapsedAggTestInstance.class, dbScope = DbScope.PER_CLASS)
class SpeciesNonCollapsedAggTest {

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1105">MONDRIAN-1105,
     * "AggLevel column attribute not used properly in all cases"</a>.
     */
    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testBugMondrian1105(Connection connection) {
        // If agg table is not used, cell values will be very different.
        assertThatQuery(connection,
            "SELECT \n"
            + " { [Measures].[Population] } ON COLUMNS,\n"
            + " { [Animal].[Animals].[Family].Members } ON ROWS\n"
            + "FROM [Test]\n").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Population]}\n"
            + "Axis #2:\n"
            + "{[Animal].[Animals].[Loricariidae]}\n"
            + "{[Animal].[Animals].[Cichlidae]}\n"
            + "{[Animal].[Animals].[Cyprinidae]}\n"
            + "Row #0: 666\n"
            + "Row #1: 579\n"
            + "Row #2: 479\n");
    }
}
