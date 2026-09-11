/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2015-2017 Hitachi Vantara..  All rights reserved.
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
package org.eclipse.daanse.rolap.agg;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.junit.jupiter.api.Test;

/**
 * @author Andrey Khayrutdinov
 */
@RolapContextTest(value = AggregationOnInvalidRoleTestInstance.class, dbScope = DbScope.PER_CLASS)
class AggregationOnInvalidRoleTest {

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.IGNORE_INVALID_MEMBERS, value = "true", type = Boolean.class)
    void test_ExecutesCorrectly_WhenIgnoringInvalidMembers(@Roles("Test") Connection connection) {
        executeAnalyzerQuery(connection);
    }

    /** Shared with {@link AggregationOnInvalidRoleWhenNotIgnoringTest}. */
    static void executeAnalyzerQuery(Connection connection) {
        // select measures on columns
        // and sorted lexicography products on rows
        String queryFromAnalyzer = ""
            + "with "
            + "  set [*NATIVE_CJ_SET_WITH_SLICER] as 'Filter([*BASE_MEMBERS__Product Code_], (NOT IsEmpty([Measures].[Measure])))'"
            + "  set [*NATIVE_CJ_SET] as '[*NATIVE_CJ_SET_WITH_SLICER]'"
            + "  set [*BASE_MEMBERS__Product Code_] as '[Product Code].[Product Code].[Code].Members'"
            + "  set [*BASE_MEMBERS__Measures_] as '{[Measures].[Measure]}'"
            + "  set [*CJ_ROW_AXIS] as 'Generate([*NATIVE_CJ_SET], {[Product Code].[Product Code].CurrentMember})'"
            + "  set [*SORTED_ROW_AXIS] as 'Order([*CJ_ROW_AXIS], [Product Code].[Product Code].CurrentMember.OrderKey, BASC)' "
            + "select "
            + "  [*BASE_MEMBERS__Measures_] on columns,"
            + "  [*SORTED_ROW_AXIS] on rows "
            + "from [mondrian2225]";

        String expected = ""
            + "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Measure]}\n"
            + "Axis #2:\n"
            + "{[Product Code].[Product Code].[eight]}\n"
            + "{[Product Code].[Product Code].[five]}\n"
            + "{[Product Code].[Product Code].[four]}\n"
            + "{[Product Code].[Product Code].[mdg]}\n"
            + "{[Product Code].[Product Code].[three]}\n"
            + "{[Product Code].[Product Code].[tst]}\n"
            + "{[Product Code].[Product Code].[two]}\n"
            + "Row #0: 175\n"
            + "Row #1: 5\n"
            + "Row #2: 4\n"
            + "Row #3: 2\n"
            + "Row #4: 3\n"
            + "Row #5: 1,000\n"
            + "Row #6: 2\n";

        assertThatQuery(connection, queryFromAnalyzer).returnsGrid( expected );
    }

}
