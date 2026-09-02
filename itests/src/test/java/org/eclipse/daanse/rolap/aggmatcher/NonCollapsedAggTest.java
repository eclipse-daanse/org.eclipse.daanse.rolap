/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/
package org.eclipse.daanse.rolap.aggmatcher;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Testcase for non-collapsed levels in agg tables.
 *
 * @author Luc Boudreau
 */
@RolapContextTest(value = NonCollapsedAggTestInstance.class, dbScope = DbScope.PER_CLASS)
class NonCollapsedAggTest {

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testSingleJoin(Connection connection) {
        final String mdx =
            "select {[Measures].[Unit Sales]} on columns, {[dimension].[tenant].[tenant].Members} on rows from [foo]";

        // We expect the correct cell value + 1 if the agg table is used.
        assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[dimension].[tenant].[tenant one]}\n"
            + "{[dimension].[tenant].[tenant two]}\n"
            + "Row #0: 31\n"
            + "Row #1: 121\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testComplexJoin(Connection connection) {
        final String mdx =
            "select {[Measures].[Unit Sales]} on columns, {[dimension].[distributor].[line class].Members} on rows from [foo]";

        // We expect the correct cell value + 1 if the agg table is used.
        assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[dimension].[distributor].[distributor one].[line class one]}\n"
            + "{[dimension].[distributor].[distributor two].[line class two]}\n"
            + "Row #0: 31\n"
            + "Row #1: 121\n");

        final String mdx2 =
            "select {[Measures].[Unit Sales]} on columns, {[dimension].[network].[line class].Members} on rows from [foo]";
        // We expect the correct cell value + 1 if the agg table is used.
        assertThatQuery(connection,
            mdx2).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[dimension].[network].[network one].[line class one]}\n"
            + "{[dimension].[network].[network two].[line class two]}\n"
            + "Row #0: 31\n"
            + "Row #1: 121\n");
    }

    @Disabled //TODO need investigate
    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testComplexJoinDefaultRecognizer(Connection connection) {
        // We expect the correct cell value + 2 if the agg table is used.
        final String mdx =
            "select {[Measures].[Unit Sales]} on columns, {[dimension.distributor].[line class].Members} on rows from [foo2]";
        assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[dimension.distributor].[distributor one].[line class one]}\n"
            + "{[dimension.distributor].[distributor two].[line class two]}\n"
            + "Row #0: 32\n"
            + "Row #1: 122\n");

        final String mdx2 =
            "select {[Measures].[Unit Sales]} on columns, {[dimension.network].[line class].Members} on rows from [foo2]";
        // We expect the correct cell value + 2 if the agg table is used.
        assertThatQuery(connection,
            mdx2).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[dimension.network].[network one].[line class one]}\n"
            + "{[dimension.network].[network two].[line class two]}\n"
            + "Row #0: 32\n"
            + "Row #1: 122\n");
    }

    @Test
    @RolapContextTest(NonCollapsedAggTestSsasInstance.class)
    void testSsasCompatNamingInAgg(Connection connection) {
        // MONDRIAN-1085
        final String mdx =
                "select {[Measures].[Unit Sales]} on columns, {[dimension].[tenant].[tenant].Members} on rows from [testSsas]";
        assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[dimension].[tenant].[tenant one]}\n"
            + "{[dimension].[tenant].[tenant two]}\n"
            + "Row #0: 30\n"
            + "Row #1: 120\n");
    }

    /**
     * Test case for cast exception on min/max of an integer measure
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, TestMondrian1325Modifier.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMondrian1325(Connection connection) {
        final String query =
            "SELECT\n"
            + "{ Measures.[Bogus Number]} on 0,\n"
            + "non empty Descendants([Time].[Time].[Year].Members, Time.Time.Month, SELF_AND_BEFORE) on 1\n"
            + "FROM [Sales]";

        executeQuery(connection, query);
    }

    /** Named bridge onto the FoodMart CSVs (for the {@code data =} supplier form). */
    public static class FoodmartData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }
}
