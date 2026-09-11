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
package org.eclipse.daanse.olap.function.def.aggregatex;

import static org.eclipse.daanse.olap.function.TestResources.hiersExcept;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;


import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class AggregateFunDefTest {

    @Test
    void testAggregateDepends(Context<?> context) {
        // Depends on everything except Measures, Gender
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "([Measures].[Unit Sales], [Gender].[F])")
            .dependsOn( hiersExcept("[Measures]", "[Gender].[Gender]" ) );
        // Depends on everything except Customers, Measures, Gender
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Aggregate([Customers].Members, ([Measures].[Unit Sales], [Gender].[F]))")
            .dependsOn( hiersExcept( "[Customers].[Customers]", "[Gender].[Gender]" ) );
        // Depends on everything except Customers
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Aggregate([Customers].Members)")
            .dependsOn( hiersExcept( "[Customers].[Customers]" ) );
        // Depends on the current member of the Product dimension, even though
        // [Product].[All Products] is referenced from the expression.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Aggregate(Filter([Customers].[City].Members, (([Measures].[Unit Sales] / ([Measures].[Unit Sales], [Product]"
                + ".[All Products])) > 0.1)))")
            .dependsOn( hiersExcept( "[Customers].[Customers]" ) );
    }

    @Test
    void testAggregate(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Store].[CA plus OR] AS 'AGGREGATE({[Store].[USA].[CA], [Store].[USA].[OR]})'\n"
                + "SELECT {[Measures].[Unit Sales], [Measures].[Store Sales]} ON COLUMNS,\n"
                + "      {[Store].[USA].[CA], [Store].[USA].[OR], [Store].[CA plus OR]} ON ROWS\n"
                + "FROM Sales\n"
                + "WHERE ([1997].[Q1])").returnsGrid(
            "Axis #0:\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[USA].[CA]}\n"
                + "{[Store].[Store].[USA].[OR]}\n"
                + "{[Store].[Store].[CA plus OR]}\n"
                + "Row #0: 16,890\n"
                + "Row #0: 36,175.20\n"
                + "Row #1: 19,287\n"
                + "Row #1: 40,170.29\n"
                + "Row #2: 36,177\n"
                + "Row #2: 76,345.49\n" );
    }

    @Test
    void testAggregate2(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH\n"
                + "  Member [Time].[Time].[1st Half Sales] AS 'Aggregate({Time.[1997].[Q1], Time.[1997].[Q2]})'\n"
                + "  Member [Time].[Time].[2nd Half Sales] AS 'Aggregate({Time.[1997].[Q3], Time.[1997].[Q4]})'\n"
                + "  Member [Time].[Time].[Difference] AS 'Time.[2nd Half Sales] - Time.[1st Half Sales]'\n"
                + "SELECT\n"
                + "   { [Store].[Store State].Members} ON COLUMNS,\n"
                + "   { Time.[1st Half Sales], Time.[2nd Half Sales], Time.[Difference]} ON ROWS\n"
                + "FROM Sales\n"
                + "WHERE [Measures].[Store Sales]").returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[Canada].[BC]}\n"
                + "{[Store].[Store].[Mexico].[DF]}\n"
                + "{[Store].[Store].[Mexico].[Guerrero]}\n"
                + "{[Store].[Store].[Mexico].[Jalisco]}\n"
                + "{[Store].[Store].[Mexico].[Veracruz]}\n"
                + "{[Store].[Store].[Mexico].[Yucatan]}\n"
                + "{[Store].[Store].[Mexico].[Zacatecas]}\n"
                + "{[Store].[Store].[USA].[CA]}\n"
                + "{[Store].[Store].[USA].[OR]}\n"
                + "{[Store].[Store].[USA].[WA]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1st Half Sales]}\n"
                + "{[Time].[Time].[2nd Half Sales]}\n"
                + "{[Time].[Time].[Difference]}\n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: 74,571.95\n"
                + "Row #0: 71,943.17\n"
                + "Row #0: 125,779.50\n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: 84,595.89\n"
                + "Row #1: 70,333.90\n"
                + "Row #1: 138,013.72\n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: 10,023.94\n"
                + "Row #2: -1,609.27\n"
                + "Row #2: 12,234.22\n" );
    }

    @Test
    void testAggregateWithIIF(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member store.foo as 'iif(3>1,"
                + "aggregate({[Store].[All Stores].[USA].[OR]}),"
                + "aggregate({[Store].[All Stores].[USA].[CA]}))' "
                + "select {store.foo} on 0 from sales").returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[foo]}\n"
                + "Row #0: 67,659\n" );
    }


    @Test
    void testAggregate2AllMembers(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH\n"
                + "  Member [Time].[Time].[1st Half Sales] AS 'Aggregate({Time.[1997].[Q1], Time.[1997].[Q2]})'\n"
                + "  Member [Time].[Time].[2nd Half Sales] AS 'Aggregate({Time.[1997].[Q3], Time.[1997].[Q4]})'\n"
                + "  Member [Time].[Time].[Difference] AS 'Time.[2nd Half Sales] - Time.[1st Half Sales]'\n"
                + "SELECT\n"
                + "   { [Store].[Store State].AllMembers} ON COLUMNS,\n"
                + "   { Time.[1st Half Sales], Time.[2nd Half Sales], Time.[Difference]} ON ROWS\n"
                + "FROM Sales\n"
                + "WHERE [Measures].[Store Sales]").returnsGrid(
            "Axis #0:\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[Canada].[BC]}\n"
                + "{[Store].[Store].[Mexico].[DF]}\n"
                + "{[Store].[Store].[Mexico].[Guerrero]}\n"
                + "{[Store].[Store].[Mexico].[Jalisco]}\n"
                + "{[Store].[Store].[Mexico].[Veracruz]}\n"
                + "{[Store].[Store].[Mexico].[Yucatan]}\n"
                + "{[Store].[Store].[Mexico].[Zacatecas]}\n"
                + "{[Store].[Store].[USA].[CA]}\n"
                + "{[Store].[Store].[USA].[OR]}\n"
                + "{[Store].[Store].[USA].[WA]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1st Half Sales]}\n"
                + "{[Time].[Time].[2nd Half Sales]}\n"
                + "{[Time].[Time].[Difference]}\n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: \n"
                + "Row #0: 74,571.95\n"
                + "Row #0: 71,943.17\n"
                + "Row #0: 125,779.50\n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: \n"
                + "Row #1: 84,595.89\n"
                + "Row #1: 70,333.90\n"
                + "Row #1: 138,013.72\n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: \n"
                + "Row #2: 10,023.94\n"
                + "Row #2: -1,609.27\n"
                + "Row #2: 12,234.22\n" );
    }


    @Test
    void testAggregateToSimulateCompoundSlicer(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Time].[Time].[1997 H1] as 'Aggregate({[Time].[1997].[Q1], [Time].[1997].[Q2]})'\n"
                + "  MEMBER [Education Level].[College or higher] as 'Aggregate({[Education Level].[Bachelors Degree], "
                + "[Education Level].[Graduate Degree]})'\n"
                + "SELECT {[Measures].[Unit Sales], [Measures].[Store Sales]} on columns,\n"
                + "  {[Product].children} on rows\n"
                + "FROM [Sales]\n"
                + "WHERE ([Time].[1997 H1], [Education Level].[College or higher], [Gender].[F])").returnsGrid(
            "Axis #0:\n"
                + "{[Time].[Time].[1997 H1], [Education Level].[Education Level].[College or higher], [Gender].[Gender].[F]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Food]}\n"
                + "{[Product].[Product].[Non-Consumable]}\n"
                + "Row #0: 1,797\n"
                + "Row #0: 3,620.49\n"
                + "Row #1: 15,002\n"
                + "Row #1: 31,931.88\n"
                + "Row #2: 3,845\n"
                + "Row #2: 8,173.22\n" );
    }

}
