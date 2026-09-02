/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
 * All Rights Reserved.
 *
 * remberson, Jan 31, 2006
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

package org.eclipse.daanse.olap;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URL;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.query.component.AxisOrdinal;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.FlushSchemaCacheModifier;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
@RolapContextTest(FoodmartTestInstance.class)
class HierarchyBugTest {

    public static class FoodmartData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

	@BeforeEach
	public void beforeEach() {

	}

	@AfterEach
	public void afterEach() {
	}

    /**
     * This is code that demonstrates a bug that appears when using
     * JPivot with the current version of Mondrian. With the previous
     * version of Mondrian (and JPivot), pre compilation Mondrian,
     * this was not a bug (or at least Mondrian did not have a null
     * hierarchy).
     * Here the Time dimension is not returned in axis == 0, rather
     * null is returned. This causes a NullPointer exception in JPivot
     * when it tries to access the (null) hierarchy's name.
     * If the Time hierarchy is miss named in the query string, then
     * the parse ought to pick it up.
     **/
	@Test
    void testNoHierarchy(Context<?> foodMartContext) {
        String queryString =
            "select NON EMPTY "
            + "Crossjoin(Hierarchize(Union({[Time].[Time].LastSibling}, "
            + "[Time].[Time].LastSibling.Children)), "
            + "{[Measures].[Unit Sales],      "
            + "[Measures].[Store Cost]}) ON columns, "
            + "NON EMPTY Hierarchize(Union({[Store].[All Stores]}, "
            + "[Store].[All Stores].Children)) ON rows "
            + "from [Sales]";

        Connection conn = foodMartContext.getConnectionWithDefaultRole();
        Query query = conn.parseQuery(queryString);

        String failStr = null;
        int len = query.getAxes().length;
        for (int i = 0; i < len; i++) {
            Hierarchy[] hs =
                query.getMdxHierarchiesOnAxis(
                    AxisOrdinal.StandardAxisOrdinal.forLogicalOrdinal(i));
            if (hs == null) {
            } else {
                for (Hierarchy h : hs) {
                    // This should NEVER be null, but it is.
                    if (h == null) {
                        failStr =
                            "Got a null Hierarchy, "
                            + "Should be Time Hierarchy";
                    }
                }
            }
        }
        if (failStr != null) {
            fail(failStr);
        }
    }

    /**
     * Test cases for <a href="http://jira.pentaho.com/browse/MONDRIAN-1126">
     * MONDRIAN-1126:
     * member getHierarchy vs. level.getHierarchy differences in Time Dimension
     * </a>
     */
	@Test
	void testNamesIdentitySsasCompatibleTimeHierarchy(Context<?> foodMartContext) {

        String mdxTime = "SELECT\n"
            + "   [Measures].[Unit Sales] ON COLUMNS,\n"
            + "   [Time].[Time].[Year].Members ON ROWS\n"
            + "FROM [Sales]";
       Connection conn= foodMartContext.getConnectionWithDefaultRole();


        Result resultTime = executeQuery(conn, mdxTime);
        verifyMemberLevelNamesIdentityMeasureAxis(
            resultTime.getAxes()[0], "[Measures]");
        verifyMemberLevelNamesIdentityDimAxis(
            resultTime.getAxes()[1], "[Time].[Time]");

FlushSchemaCacheModifier.flushSchemaCache(conn);
    }
	@Test
    void testNamesIdentitySsasCompatibleWeeklyHierarchy(Context<?> foodMartContext) {
        String mdxWeekly = "SELECT\n"
            + "   [Measures].[Unit Sales] ON COLUMNS,\n"
            + "   [Time].[Weekly].[Year].Members ON ROWS\n"
            + "FROM [Sales]";

       // Fresh sets this before get new Conn
       //  RolapConnectionProperties.UseSchemaPool.name(), false);
       //foodMartContext.setProperty(RolapConnectionProperties.UseSchemaPool.name(), Boolean.toString(false));

        Connection conn = foodMartContext.getConnection(new ConnectionProps(
            List.of("Administrator"), false, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty()
        ));

        Result resultWeekly =executeQuery(conn, mdxWeekly);
        verifyMemberLevelNamesIdentityMeasureAxis(
            resultWeekly.getAxes()[0], "[Measures]");
        verifyMemberLevelNamesIdentityDimAxis(
            resultWeekly.getAxes()[1], "[Time].[Weekly]");
        FlushSchemaCacheModifier.flushSchemaCache(conn);
    }
	@Test
    void testNamesIdentitySsasInCompatibleTimeHierarchy(Context<?> foodMartContext) {
        // SsasCompatibleNaming defaults to false
        String mdxTime = "SELECT\n"
            + "   [Measures].[Unit Sales] ON COLUMNS,\n"
            + "   [Time].[Year].Members ON ROWS\n"
            + "FROM [Sales]";

        Connection conn=foodMartContext.getConnectionWithDefaultRole();
        Result resultTime =executeQuery(conn, mdxTime);
        verifyMemberLevelNamesIdentityMeasureAxis(
            resultTime.getAxes()[0], "[Measures]");
        verifyMemberLevelNamesIdentityDimAxis(
            resultTime.getAxes()[1], "[Time].[Time]");
        FlushSchemaCacheModifier.flushSchemaCache(conn);
    }
	@Test
    void testNamesIdentitySsasInCompatibleWeeklyHierarchy(Context<?> foodMartContext) {
        String mdxWeekly = "SELECT\n"
            + "   [Measures].[Unit Sales] ON COLUMNS,\n"
            + "   [Time].[Weekly].[Year].Members ON ROWS\n"
            + "FROM [Sales]";

        Connection conn=foodMartContext.getConnectionWithDefaultRole();
        Result resultWeekly =executeQuery(conn, mdxWeekly);

        verifyMemberLevelNamesIdentityMeasureAxis(
            resultWeekly.getAxes()[0], "[Measures]");
        verifyMemberLevelNamesIdentityDimAxis(
            resultWeekly.getAxes()[1], "[Time].[Weekly]");
        FlushSchemaCacheModifier.flushSchemaCache(conn);
    }

    private String verifyMemberLevelNamesIdentityMeasureAxis(
        Axis axis, String expected)
    {
        OlapElement unitSales =
            axis.getPositions().get(0).get(0);
        String unitSalesHierarchyName =
            unitSales.getHierarchy().getUniqueName();
        assertEquals(expected, unitSalesHierarchyName);
        return unitSalesHierarchyName;
    }

    private void verifyMemberLevelNamesIdentityDimAxis(
        Axis axis, String expected)
    {
        Member year1997 = axis.getPositions().get(0).get(0);
        String year1997HierarchyName = year1997.getHierarchy().getUniqueName();
        assertEquals(expected, year1997HierarchyName);
        Level year = year1997.getLevel();
        String yearHierarchyName = year.getHierarchy().getUniqueName();
        assertEquals(year1997HierarchyName, yearHierarchyName);
    }
	@Test
    void testNamesIdentitySsasCompatibleOlap4j(Context<?> foodMartContext) throws SQLException {
        verifyLevelMemberNamesIdentityOlap4jTimeHierarchy(foodMartContext, "[Time].[Time]");
    }

    private void verifyLevelMemberNamesIdentityOlap4jTimeHierarchy(Context<?> foodMartContext, String expected)
        throws SQLException
    {
        // essential here, in time hierarchy, is hasAll="false"
        // so that we expect "[Time]"
        String mdx = "SELECT\n"
            + "   [Measures].[Unit Sales] ON COLUMNS,\n"
            + "   [Time].[Time].[Year].Members ON ROWS\n"
            + "FROM [Sales]";
        verifyLevelMemberNamesIdentityOlap4j(mdx, foodMartContext, expected);
    }
	@Test
    void testNamesIdentitySsasCompatibleOlap4jWeekly(Context<?> foodMartContext)
        throws SQLException
    {
        String mdx = "SELECT\n"
            + "   [Measures].[Unit Sales] ON COLUMNS,\n"
            + "   [Time].[Weekly].[Year].Members ON ROWS\n"
            + "FROM [Sales]";
        verifyLevelMemberNamesIdentityOlap4j(
            mdx, foodMartContext, "[Time].[Weekly]");
    }
	@Test
    void testNamesIdentitySsasInCompatibleOlap4jWeekly(Context<?> foodMartContext)
        throws SQLException
    {
        // SsasCompatibleNaming defaults to false
        String mdx = "SELECT\n"
            + "   [Measures].[Unit Sales] ON COLUMNS,\n"
            + "   [Time].[Weekly].[Year].Members ON ROWS\n"
            + "FROM [Sales]";
        verifyLevelMemberNamesIdentityOlap4j(
            mdx, foodMartContext, "[Time].[Weekly]");
    }
	@Test
	@RolapContextTest(catalog = { CatalogSupplier.class, VerifyMemberLevelNamesIdentityOlap4jDateDimModifier.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNamesIdentitySsasCompatibleOlap4jDateDim(Context<?> foodMartContext)
        throws SQLException
    {
        verifyMemberLevelNamesIdentityOlap4jDateDim(foodMartContext, "[Date].[Date]");
    }

    private void verifyMemberLevelNamesIdentityOlap4jDateDim(Context<?> context, String expected)
        throws SQLException
    {
        // essential here, in time hierarchy, is hasAll="false"
        // so that we expect "[Time]"
        String mdx =
            "SELECT\n"
            + "   [Measures].[Unit Sales] ON COLUMNS,\n"
            + "   [Date].[Date].[Year].Members ON ROWS\n"
            + "FROM [Sales]";

        verifyLevelMemberNamesIdentityOlap4j(mdx, context, expected);
    }
	@Test
	@RolapContextTest(catalog = { CatalogSupplier.class, VerifyMemberLevelNamesIdentityOlap4jWeeklyModifier.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNamesIdentitySsasCompatibleOlap4jDateWeekly(Context<?> context)
        throws SQLException
    {
        String mdx = "SELECT\n"
            + "   [Measures].[Unit Sales] ON COLUMNS,\n"
            + "   [Date].[Weekly].[Year].Members ON ROWS\n"
            + "FROM [Sales]";
        verifyMemberLevelNamesIdentityOlap4jWeekly(context,mdx,"[Date].[Weekly]");
    }
	@Test
	@RolapContextTest(catalog = { CatalogSupplier.class, VerifyMemberLevelNamesIdentityOlap4jWeeklyModifier.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNamesIdentitySsasInCompatibleOlap4jDateDim(Context<?> context)
        throws SQLException
    {
        // SsasCompatibleNaming defaults to false
        String mdx = "SELECT\n"
            + "   [Measures].[Unit Sales] ON COLUMNS,\n"
            + "   [Date].[Weekly].[Year].Members ON ROWS\n"
            + "FROM [Sales]";
        verifyMemberLevelNamesIdentityOlap4jWeekly(context,mdx, "[Date].[Weekly]");
    }

    private void verifyMemberLevelNamesIdentityOlap4jWeekly(Context<?> context,
        String mdx, String expected) throws SQLException
    {
        verifyLevelMemberNamesIdentityOlap4j(mdx, context, expected);
    }

    private void verifyLevelMemberNamesIdentityOlap4j(
        String mdx, Context<?> context, String expected)
    {
    Connection connection =	context.getConnectionWithDefaultRole();
        org.eclipse.daanse.olap.api.result.CellSet result = connection.createStatement().executeQuery(mdx);

        List<Position> positions =
            result.getAxes().get(1).getPositions();
        Member year1997 =
            positions.get(0).getMembers().get(0);
        String year1997HierarchyName = year1997.getHierarchy().getUniqueName();
        assertEquals(expected, year1997HierarchyName);

        Level year = year1997.getLevel();
        String yearHierarchyName = year.getHierarchy().getUniqueName();
        assertEquals(year1997HierarchyName, yearHierarchyName);

        FlushSchemaCacheModifier.flushSchemaCache(context.getConnectionWithDefaultRole());
    }


}
