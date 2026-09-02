/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (c) 2002-2017 Hitachi Vantara.  All rights reserved.
*/
package org.eclipse.daanse.rolap.sql;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * Tests for CrossJoinArgFactory
 *
 * @author Yury Bakhmutski
 */
@RolapContextTest(FoodmartTestInstance.class)
class CrossJoinArgFactoryTest {

     /**
     * test for MONDRIAN-2287 issue. Tests if correct result is returned
     * instead of CCE throwing.
     */
     @Test
     void testCrossJoinExample(Connection connection) {
        String query =
                "with "
                + " member [Measures].[aa] as '([Measures].[Store Cost],[Gender].[Gender].[M])'"
                + " member [Measures].[bb] as '([Measures].[Store Cost],[Gender].[Gender].[M].PrevMember)'"
                + " select"
                + " non empty"
                + " crossjoin("
                + " {[Store].[Store].[All Stores].[USA].[CA]},"
                + " {[Measures].[aa], [Measures].[bb]}"
                + " ) on columns,"
                + " non empty"
                + " [Marital Status].[Marital Status].[Marital Status].members on rows"
                + " from sales";
        String expected = "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA].[CA], [Measures].[aa]}\n"
                + "{[Store].[Store].[USA].[CA], [Measures].[bb]}\n"
                + "Axis #2:\n"
                + "{[Marital Status].[Marital Status].[M]}\n"
                + "{[Marital Status].[Marital Status].[S]}\n"
                + "Row #0: 15,339.94\n"
                + "Row #0: 15,941.98\n"
                + "Row #1: 16,598.87\n"
                + "Row #1: 15,649.64\n";
        assertThatQuery(connection, query).returnsGrid(expected);
    }
}
