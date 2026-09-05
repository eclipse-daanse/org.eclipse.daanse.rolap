/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Validates the dimension attribute usagePrefix is correctly
 * applied when querying aggregate tables.
 * http://jira.pentaho.com/browse/MONDRIAN-595
 *
 * @author Matt Campbell
 */
@RolapContextTest(value = UsagePrefixTestInstance.class, dbScope = DbScope.PER_CLASS)
class UsagePrefixTest {

    @Disabled //TODO need investigate
    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testUsagePrefix(Connection connection) {
        String mdx =
            "select {[StoreX].[Store Value].members} on columns, "
                +   "{ measures.[Amount] } on rows from Cheques";

        assertThatQuery(connection, mdx).returnsGrid(
            "Axis #0:\n"
            +    "{}\n"
            +    "Axis #1:\n"
            +    "{[StoreX].[store1]}\n"
            +    "{[StoreX].[store2]}\n"
            +    "{[StoreX].[store3]}\n"
            +    "Axis #2:\n"
            +    "{[Measures].[Amount]}\n"
            +    "Row #0: 05.0\n"
            +    "Row #0: 02.5\n"
            +    "Row #0: 02.0\n");
    }

    @Disabled //TODO need investigate
    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testUsagePrefixTwoDims(Connection connection) {
        String mdx =
            "select Crossjoin([StoreX].[Store Value].members, "
            + " [StoreY].[Store Value].members) on columns, "
            +   "{ measures.[Amount] } on rows from Cheques";

        assertThatQuery(connection, mdx).returnsGrid(
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[StoreX].[store1], [StoreY].[OtherStore1]}\n"
                + "{[StoreX].[store1], [StoreY].[OtherStore2]}\n"
                + "{[StoreX].[store1], [StoreY].[OtherStore3]}\n"
                + "{[StoreX].[store2], [StoreY].[OtherStore1]}\n"
                + "{[StoreX].[store2], [StoreY].[OtherStore2]}\n"
                + "{[StoreX].[store2], [StoreY].[OtherStore3]}\n"
                + "{[StoreX].[store3], [StoreY].[OtherStore1]}\n"
                + "{[StoreX].[store3], [StoreY].[OtherStore2]}\n"
                + "{[StoreX].[store3], [StoreY].[OtherStore3]}\n"
                + "Axis #2:\n"
                + "{[Measures].[Amount]}\n"
                + "Row #0: 05.0\n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: 02.5\n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: 02.0\n");
    }

}
