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
package org.eclipse.daanse.olap.function.def.median;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class MedianFunDefTest {

    @Test
    void testMedian(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "MEDIAN({[Store].[All Stores].[USA].children},"
                + "[Measures].[Store Sales])").returns(
            "159,167.84" );
        // single value
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "MEDIAN({[Store].[All Stores].[USA]}, [Measures].[Store Sales])").returns(
            "565,238.13" );
    }

    @Test
    void testMedian2(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH\n"
                + "   Member [Time].[Time].[1st Half Sales] AS 'Sum({[Time].[1997].[Q1], [Time].[1997].[Q2]})'\n"
                + "   Member [Time].[Time].[2nd Half Sales] AS 'Sum({[Time].[1997].[Q3], [Time].[1997].[Q4]})'\n"
                + "   Member [Time].[Time].[Median] AS 'Median(Time.[Time].Members)'\n"
                + "SELECT\n"
                + "   NON EMPTY { [Store].[Store Name].Members} ON COLUMNS,\n"
                + "   { [Time].[1st Half Sales], [Time].[2nd Half Sales], [Time].[Median]} ON ROWS\n"
                + "FROM Sales\n"
                + "WHERE [Measures].[Store Sales]").returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
                + "{[Store].[Store].[USA].[CA].[Los Angeles].[Store 7]}\n"
                + "{[Store].[Store].[USA].[CA].[San Diego].[Store 24]}\n"
                + "{[Store].[Store].[USA].[CA].[San Francisco].[Store 14]}\n"
                + "{[Store].[Store].[USA].[OR].[Portland].[Store 11]}\n"
                + "{[Store].[Store].[USA].[OR].[Salem].[Store 13]}\n"
                + "{[Store].[Store].[USA].[WA].[Bellingham].[Store 2]}\n"
                + "{[Store].[Store].[USA].[WA].[Bremerton].[Store 3]}\n"
                + "{[Store].[Store].[USA].[WA].[Seattle].[Store 15]}\n"
                + "{[Store].[Store].[USA].[WA].[Spokane].[Store 16]}\n"
                + "{[Store].[Store].[USA].[WA].[Tacoma].[Store 17]}\n"
                + "{[Store].[Store].[USA].[WA].[Walla Walla].[Store 22]}\n"
                + "{[Store].[Store].[USA].[WA].[Yakima].[Store 23]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1st Half Sales]}\n"
                + "{[Time].[Time].[2nd Half Sales]}\n"
                + "{[Time].[Time].[Median]}\n"
                + "Row #0: 20,801.04\n"
                + "Row #0: 25,421.41\n"
                + "Row #0: 26,275.11\n"
                + "Row #0: 2,074.39\n"
                + "Row #0: 28,519.18\n"
                + "Row #0: 43,423.99\n"
                + "Row #0: 2,140.99\n"
                + "Row #0: 25,502.08\n"
                + "Row #0: 25,293.50\n"
                + "Row #0: 23,265.53\n"
                + "Row #0: 34,926.91\n"
                + "Row #0: 2,159.60\n"
                + "Row #0: 12,490.89\n"
                + "Row #1: 24,949.20\n"
                + "Row #1: 29,123.87\n"
                + "Row #1: 28,156.03\n"
                + "Row #1: 2,366.79\n"
                + "Row #1: 26,539.61\n"
                + "Row #1: 43,794.29\n"
                + "Row #1: 2,598.24\n"
                + "Row #1: 27,394.22\n"
                + "Row #1: 27,350.57\n"
                + "Row #1: 26,368.93\n"
                + "Row #1: 39,917.05\n"
                + "Row #1: 2,546.37\n"
                + "Row #1: 11,838.34\n"
                + "Row #2: 4,577.35\n"
                + "Row #2: 5,211.38\n"
                + "Row #2: 4,722.87\n"
                + "Row #2: 398.24\n"
                + "Row #2: 5,039.50\n"
                + "Row #2: 7,374.59\n"
                + "Row #2: 410.22\n"
                + "Row #2: 4,924.04\n"
                + "Row #2: 4,569.13\n"
                + "Row #2: 4,511.68\n"
                + "Row #2: 6,630.91\n"
                + "Row #2: 419.51\n"
                + "Row #2: 2,169.48\n" );
    }

}
