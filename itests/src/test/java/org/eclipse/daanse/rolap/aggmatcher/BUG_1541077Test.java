/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package org.eclipse.daanse.rolap.aggmatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.BatchTestCase;

/**
 * Testcase for
 * <a href="http://jira.pentaho.com/browse/MONDRIAN-214">MONDRIAN-214</a>
 * (formerly SourceForge bug 1541077)
 * and a couple of other aggregate table ExplicitRecognizer conditions.
 *
 * <p>Each original test compared the same query computed twice within one
 * method, toggling {@code USE_AGGREGATES} between the calls -- the new
 * testkit has no supported way to mutate a context's config after it is
 * built, so each is now two independent tests (aggregates off / on) that
 * both assert the same value, computed once from the CSV fixture data and
 * confirmed by running the query against both configurations.
 *
 * @author Richard M. Emberson
 */
@RolapContextTest(value = BUG_1541077TestInstance.class, dbScope = DbScope.PER_CLASS)
class BUG_1541077Test extends BatchTestCase {

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
    void testStoreCountWithoutAggregates(Connection connection) throws Exception {
        assertStoreCount(connection);
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    void testStoreCountWithAggregates(Connection connection) throws Exception {
        assertStoreCount(connection);
    }

    private void assertStoreCount(Connection connection) throws Exception {
        String mdx = "select {[Measures].[Store Count]} on columns from Cheques";
        Result result = executeQuery(mdx, connection);
        Object v = result.getCell(new int[] {0}).getValue();
        assertEquals(3.0, ((Number) v).doubleValue());
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
    void testSalesCountWithoutAggregates(Connection connection) throws Exception {
        assertSalesCount(connection);
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    void testSalesCountWithAggregates(Connection connection) throws Exception {
        assertSalesCount(connection);
    }

    private void assertSalesCount(Connection connection) throws Exception {
        String mdx = "select {[Measures].[Sales Count]} on columns from Cheques";
        Result result = executeQuery(mdx, connection);
        Object v = result.getCell(new int[] {0}).getValue();
        assertEquals(6.0, ((Number) v).doubleValue());
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
    void testTotalAmountWithoutAggregates(Connection connection) throws Exception {
        assertTotalAmount(connection);
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    void testTotalAmountWithAggregates(Connection connection) throws Exception {
        assertTotalAmount(connection);
    }

    private void assertTotalAmount(Connection connection) throws Exception {
        String mdx = "select {[Measures].[Total Amount]} on columns from Cheques";
        Result result = executeQuery(mdx, connection);
        Object v = result.getCell(new int[] {0}).getValue();
        assertEquals(19.0, ((Number) v).doubleValue());
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "false", type = Boolean.class)
    void testBug1541077WithoutAggregates(Connection connection) throws Exception {
        assertAvgAmount(connection);
    }

    @Test
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    void testBug1541077WithAggregates(Connection connection) throws Exception {
        assertAvgAmount(connection);
    }

    private void assertAvgAmount(Connection connection) throws Exception {
        // Formatted, not raw: the raw double differs by a ULP or two between
        // sum/count and the FACT_COUNT-weighted average reconstructed from
        // agg_lp_xxx_cheques, but both round to the same "00.0" string.
        String mdx = "select {[Measures].[Avg Amount]} on columns from Cheques";
        Result result = executeQuery(mdx, connection);
        Object v = result.getCell(new int[] {0}).getFormattedValue();
        assertEquals("03.2", v);
    }

}
