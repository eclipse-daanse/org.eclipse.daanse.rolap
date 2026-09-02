/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
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

package org.eclipse.daanse.rolap;


import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.api.calc.ResultStyle;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.Quoting;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.IdImpl;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.cache.HardSmartCache;
import org.eclipse.daanse.rolap.common.catalog.RolapCatalogReader;
import org.eclipse.daanse.rolap.common.member.MemberCacheHelper;
import org.eclipse.daanse.rolap.common.member.SmartMemberReader;
import org.eclipse.daanse.rolap.common.nativize.RolapNative.Listener;
import org.eclipse.daanse.rolap.common.nativize.RolapNative.NativeEvent;
import org.eclipse.daanse.rolap.common.nativize.RolapNative.TupleEvent;
import org.eclipse.daanse.rolap.common.nativize.RolapNativeRegistry;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.testkit.assertions.CellRequestFixture;
import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.slf4j.LoggerFactory;

/**
 * To support all <code>Batch</code> related tests.
 *
 * @author Thiyagu
  * @since 06-Jun-2007
 */
public class BatchTestCase{


    protected final String tableTime = "time_by_day";
    protected final String tableProductClass = "product_class";
    protected final String tableCustomer = "customer";
    protected final String fieldYear = "the_year";
    protected final String fieldProductFamily = "product_family";
    protected final String fieldProductDepartment = "product_department";
    protected final String[] fieldValuesYear = {"1997"};
    protected final String[] fieldValuesProductFamily = {
        "Food", "Non-Consumable", "Drink"
    };
    protected final String[] fieldValueProductDepartment = {
        "Alcoholic Beverages", "Baked Goods", "Baking Goods",
         "Beverages", "Breakfast Foods", "Canned Foods",
        "Canned Products", "Carousel", "Checkout", "Dairy",
        "Deli", "Eggs", "Frozen Foods", "Health and Hygiene",
        "Household", "Meat", "Packaged Foods", "Periodicals",
        "Produce", "Seafood", "Snack Foods", "Snacks",
        "Starchy Foods"
    };
    protected final String[] fieldValuesGender = {"M", "F"};
    protected final String cubeNameSales = "Sales";
    protected final String measureUnitSales = "[Measures].[Unit Sales]";
    protected String fieldGender = "gender";

    /**
     * Checks that a given sequence of cell requests results in a
     * particular SQL statement being generated.
     *
     * <p>Always clears the cache before running the requests.
     *
     * <p>Runs the requests once for each SQL pattern in the current
     * dialect. If there are multiple patterns, runs the MDX query multiple
     * times, and expects to see each SQL statement appear. If there are no
     * patterns in this dialect, the test trivially succeeds.
     *
     * @param requests Sequence of cell requests
     * @param patterns Set of patterns
     */
    protected void assertRequestSql(Connection connection,
        CellRequest[] requests,
        SqlPattern[] patterns)
    {
        assertRequestSql(connection, requests, patterns, false);
    }

    /**
     * Checks that a given sequence of cell requests results in a
     * particular SQL statement being generated.
     *
     * <p>Always clears the cache before running the requests.
     *
     * <p>Runs the requests once for each SQL pattern in the current
     * dialect. If there are multiple patterns, runs the MDX query multiple
     * times, and expects to see each SQL statement appear. If there are no
     * patterns in this dialect, the test trivially succeeds.
     *
     * @param requests Sequence of cell requests
     * @param patterns Set of patterns
     * @param negative Set to false in order to 'expect' a query or
     * true to 'forbid' a query.
     */
    protected void assertRequestSql(
        Connection connection,
        CellRequest[] requests,
        SqlPattern[] patterns,
        boolean negative)
    {
        final String cubeName = requests[0].getMeasure().getCubeName();
        final RolapCube cube = getCube(connection, cubeName);
        final Dialect sqlDialect = requests[0].getMeasure().getStar().getDialect();
        DatabaseProduct d = DatabaseProduct.getDatabaseProduct(sqlDialect.name());
        if (d == DatabaseProduct.UNKNOWN) {
            // If the dialect is not one in the pattern set, do not run the
            // test. We do not print any warning message.
            return;
        }

        boolean patternFound = false;
        for (SqlPattern pattern : patterns) {
            if (!pattern.hasDatabaseProduct(d)) {
                continue;
            }

            patternFound = true;

            clearCache(connection, cube);

            String sql = pattern.getSql();
            String trigger = pattern.getTriggerSql();
            switch (d) {
            case ORACLE:
                sql = sql.replaceAll(" =as= ", " ");
                trigger = trigger.replaceAll(" =as= ", " ");
                break;
            case TERADATA:
                sql = sql.replaceAll(" =as= ", " as ");
                trigger = trigger.replaceAll(" =as= ", " as ");
                break;
            }

            // The actual "run the requests, trap the SQL, compare" mechanic - and the
            // rendered-request failure output - lives in CellRequestFixture now; this loop only
            // owns picking the right pattern for the current dialect.
            CellRequestFixture.RequestSqlAssert assertion =
                CellRequestFixture.of(connection).forRequests(requests);
            if (negative) {
                assertion.forbidSql(trigger);
            } else {
                assertion.expectSql(sql, trigger);
            }
            assertion.verify();
        }

        // Print warning message that no pattern was specified for the current
        // dialect.
        if (!patternFound) {
            String warnDialect =
                connection.getContext().getConfigValue(ConfigConstants.WARN_IF_NO_PATTERN_FOR_DIALECT, ConfigConstants.WARN_IF_NO_PATTERN_FOR_DIALECT_DEFAULT_VALUE, String.class);

            if (warnDialect.equals(d.toString())) {
                System.out.println(
                    "[No expected SQL statements found for dialect \""
                    + sqlDialect.toString()
                    + "\" and test not run]");
            }
        }
    }


    /**
     * Checks that a given MDX query results in a particular SQL statement
     * being generated.
     *
     * @param connection Connection
     * @param mdxQuery MDX query
     * @param patterns Set of patterns for expected SQL statements
     */
    protected void assertQuerySql(
        Connection connection,
        String mdxQuery,
        SqlPattern[] patterns)
    {
        assertQuerySqlOrNot(
            connection, mdxQuery, patterns, false, false, true);
    }

    /**
     * Checks that a given MDX query does not result in a particular SQL
     * statement being generated.
     *
     * @param mdxQuery MDX query
     * @param patterns Set of patterns for expected SQL statements
     */
    protected void assertNoQuerySql(Connection connection,
        String mdxQuery,
        SqlPattern[] patterns)
    {
        assertQuerySqlOrNot(
                connection, mdxQuery, patterns, true, false, true);
    }

    /**
     * Checks that a given MDX query results in a particular SQL statement
     * being generated.
     *
     * @param mdxQuery MDX query
     * @param patterns Set of patterns, one for each dialect.
     * @param clearCache whether to clear cache before running the query
     */
    protected void assertQuerySql(
        Connection connection,
        String mdxQuery,
        SqlPattern[] patterns,
        boolean clearCache)
    {
        assertQuerySqlOrNot(
            connection, mdxQuery, patterns, false, false, clearCache);
    }

    /**
     * During MDX query parse and execution, checks that the query results
     * (or does not result) in a particular SQL statement being generated.
     *
     * <p>Parses and executes the MDX query once for each SQL
     * pattern in the current dialect. If there are multiple patterns, runs the
     * MDX query multiple times, and expects to see each SQL statement appear.
     * If there are no patterns in this dialect, the test trivially succeeds.
     *
     * @param connection Connection
     * @param mdxQuery MDX query
     * @param patterns Set of patterns
     * @param negative false to assert if SQL is generated;
     *                 true to assert if SQL is NOT generated
     * @param bypassSchemaCache whether to grab a new connection and bypass the
     *        schema cache before parsing the MDX query
     * @param clearCache whether to clear cache before executing the MDX query
     */
    protected void assertQuerySqlOrNot(
        Connection connection,
        String mdxQuery,
        SqlPattern[] patterns,
        boolean negative,
        boolean bypassSchemaCache,
        boolean clearCache)
    {

        // Run the test once for each pattern in this dialect.
        // (We could optimize and run it once, collecting multiple queries, and
        // comparing all queries at the end.)
        Dialect dialect = getDialect(connection);
        DatabaseProduct d = DatabaseProduct.getDatabaseProduct(dialect.name());
        boolean patternFound = false;
        for (SqlPattern sqlPattern : patterns) {
            if (!sqlPattern.hasDatabaseProduct(d)) {
                // If the dialect is not one in the pattern set, skip the
                // test. If in the end no pattern is located, print a warning
                // message if required.
                continue;
            }

            patternFound = true;

            String sql = sqlPattern.getSql();
            String trigger = sqlPattern.getTriggerSql();

            sql = dialectize(d, sql);
            trigger = dialectize(d, trigger);

            // Create a dummy DataSource which will throw a 'bomb' if it is
            // asked to execute a particular SQL statement, but will otherwise
            // behave exactly the same as the current DataSource.
            final TriggerHook hook = new TriggerHook(trigger);
            RolapUtil.setHook(connection.getContext(), hook);
            Bomb bomb = null;
            try {
                if (bypassSchemaCache) {
                    //connection =
                    //    testContext.withSchemaPool(false).getConnection();
                }
                final Query query = connection.parseQuery(mdxQuery);
                if (clearCache) {
                    clearCache(connection, (RolapCube)query.getCube());
                }
                final Result result = connection.execute(query);
//                discard(result);
                bomb = null;
            } catch (Bomb e) {
                bomb = e;
            } catch (RuntimeException e) {
                // Walk up the exception tree and see if the root cause
                // was a SQL bomb.
                bomb = Util.getMatchingCause(e, Bomb.class);
                if (bomb == null) {
                    throw e;
                }
            } finally {
                RolapUtil.setHook(connection.getContext(), null);
            }
            if (negative) {
                if (bomb != null || hook.foundMatch()) {
                    fail("forbidden query [" + sql + "] detected");
                }
            } else {
                if (bomb == null && !hook.foundMatch()) {
                    fail("expected query [" + sql + "] did not occur");
                }
                if (bomb != null) {
                    // The TriggerHook already matched whitespace-insensitively (it collapses
                    // whitespace runs before startsWith). The statement builder/DialectSqlRenderer
                    // emits compact single-line SQL, whereas the expected pattern may be
                    // pretty-printed (setGenerateFormattedSql); they are token-for-token equal but
                    // not format-equal. Compare the same whitespace-insensitive way so this
                    // secondary check verifies token equality rather than failing on formatting.
                    assertEquals(
                        replaceQuotes(
                            sql.replaceAll("\\s+", " ").trim()),
                        replaceQuotes(
                            bomb.sql.replaceAll("\\s+", " ").trim()));
                }
            }
        }

        // Print warning message that no pattern was specified for the current
        // dialect.
        if (!patternFound) {
            String warnDialect =
                connection.getContext().getConfigValue(ConfigConstants.WARN_IF_NO_PATTERN_FOR_DIALECT, ConfigConstants.WARN_IF_NO_PATTERN_FOR_DIALECT_DEFAULT_VALUE, String.class);

            if (warnDialect.equals(d.toString())) {
                System.out.println(
                    "[No expected SQL statements found for dialect \""
                    + dialect.toString()
                    + "\" and test not run]");
            }
        }
    }

    protected SqlPattern[] sqlPattern(DatabaseProduct db, String sql) {
        return new SqlPattern[]{new SqlPattern(db, sql, sql.length())};
    }

    protected SqlPattern[] mysqlPattern(String sql) {
        return sqlPattern(DatabaseProduct.MYSQL, sql);
    }

    protected String dialectize(DatabaseProduct d, String sql) {
        sql = sql.replaceAll("\r\n", "\n");
        switch (d) {
        case ORACLE:
            return sql.replaceAll(" =as= ", " ");
        case GREENPLUM:
        case POSTGRES:
        case TERADATA:
            return sql.replaceAll(" =as= ", " as ");
        case DERBY:
            return sql.replaceAll("`", "\"");
        case ACCESS:
            return sql.replaceAll(
                "ISNULL\\(([^)]*)\\)",
                "Iif($1 IS NULL, 1, 0)");
        default:
            return sql;
        }
    }

    private void clearCache(Connection connection,  RolapCube cube) {
        // Clear the cache for the Sales cube, so the query runs as if
        // for the first time. (TODO: Cleaner way to do this.)
        final Cube salesCube =
                connection.getCatalog().lookupCube("Sales").orElse(null);
        if (salesCube != null) {
            RolapHierarchy hierarchy =
                    (RolapHierarchy) salesCube.lookupHierarchy(
                            new IdImpl.NameSegmentImpl("Store", Quoting.UNQUOTED),
                            false);
            if (hierarchy != null) {
                SmartMemberReader memberReader =
                    (SmartMemberReader) hierarchy.getMemberReader();
                MemberCacheHelper cacheHelper = memberReader.cacheHelper;
                cacheHelper.mapLevelToMembers.cache.clear();
                cacheHelper.mapMemberToChildren.cache.clear();
            }
        }
        // Flush the cache, to ensure that the query gets executed.
        cube.clearCachedAggregations(true);

        CacheControl cacheControl = connection.getCacheControl(null);
        final CacheControl.CellRegion measuresRegion =
            cacheControl.createMeasuresRegion(cube);
        cacheControl.flush(measuresRegion);
        waitForFlush(cacheControl, measuresRegion, cube.getName());
    }

    private void waitForFlush(
        final CacheControl cacheControl,
        final CacheControl.CellRegion measuresRegion,
        final String cubeName)
    {
        int i = 100;
        while (true) {
            try {
                Thread.sleep(i);
            } catch (InterruptedException e) {
                fail(e.getMessage());
            }
            String cacheState = getCacheState(cacheControl, measuresRegion);
            if (regionIsEmpty(cacheState, cubeName)) {
                break;
            }
            i *= 2;
            if (i > 6400) {
                fail(
                    "Cache didn't flush in sufficient time\nCache Was: \n"
                    + cacheState);
                break;
            }
        }
    }

    private String getCacheState(
        final CacheControl cacheControl,
        final CacheControl.CellRegion measuresRegion)
    {
        StringWriter out = new StringWriter();
        cacheControl.printCacheState(new PrintWriter(out), measuresRegion);
        return out.toString();
    }

    private boolean regionIsEmpty(
        final String cacheState, final String cubeName)
    {
        return !cacheState.contains("Cube:[" + cubeName + "]");
    }

    private static String replaceQuotes(String s) {
        s = s.replace('`', '\"');
        s = s.replace('\'', '\"');
        return s;
    }

    void clearAndHardenCache(MemberCacheHelper helper) {
        helper.mapLevelToMembers.setCache(
            new HardSmartCache<Pair<RolapLevel, Object>, List<RolapMember>>());
        helper.mapMemberToChildren.setCache(
            new HardSmartCache<Pair<RolapMember, Object>, List<RolapMember>>());
        helper.mapKeyToMember.clear();
        helper.mapParentToNamedChildren.setCache(
            new HardSmartCache<RolapMember, Collection<RolapMember>>());
    }

    protected RolapStar.Measure getMeasure(Connection connection, String cube, String measureName) {
        //final Connection connection = getFoodMartConnection(context);
        final boolean fail = true;
        Cube salesCube = connection.getCatalog().lookupCube(cube).orElseThrow();
        Member measure = salesCube.getCatalogReader(null).getMemberByUniqueName(
            Util.parseIdentifier(measureName), fail);
        return RolapStar.getStarMeasure(measure);
    }

    protected RolapCube getCube(Connection connection, final String cube) {
        return (RolapCube) connection.getCatalog().lookupCube(cube).orElseThrow();
    }

    /**
     * Make sure the mdx runs correctly and not in native mode.
     *
     * @param rowCount number of rows returned
     * @param mdx      query
     */
    protected void checkNotNative(Context<?> context, int rowCount, String mdx) {
        checkNotNative(context, rowCount, mdx, null);
    }

    /**
     * Makes sure the MDX runs correctly and not in native mode.
     *
     * @param rowCount       Number of rows returned
     * @param mdx            Query
     * @param expectedResult Expected result string
     */
    protected void checkNotNative(Context<?> context,
        int rowCount,
        String mdx,
        String expectedResult)
    {
        context.getConnectionWithDefaultRole().getCacheControl(null).flushSchemaCache();
        //Connection con =
        //    getTestContext().withSchemaPool(false).getConnection();
        Connection con = context.getConnectionWithDefaultRole();
        RolapNativeRegistry reg = getRegistry(con);
        reg.setListener(
            new Listener() {
                @Override
				public void foundEvaluator(NativeEvent e) {
                    fail("should not be executed native");
                }

                @Override
				public void foundInCache(TupleEvent e) {
                }

                @Override
				public void executingSql(TupleEvent e) {
                }
            });

        TestCase c = new TestCase(con, 0, rowCount, mdx);
        Result result = c.run();

        if (expectedResult != null) {
            String nonNativeResult = toString(result);
            if (!nonNativeResult.equals(expectedResult)) {
                assertEquals(
                    expectedResult,
                    nonNativeResult,
                    "Non Native implementation returned different result than "
                    + "expected; MDX=" + mdx);
            }
        }
    }

    RolapNativeRegistry getRegistry(Connection connection) {
        RolapCube cube =
            (RolapCube) connection.getCatalog().lookupCube("Sales").orElseThrow();
        RolapCatalogReader schemaReader =
            (RolapCatalogReader) cube.getCatalogReader();
        return schemaReader.getCatalog().getNativeRegistry();
    }

    /**
     * Runs a query twice, with native crossjoin optimization enabled and
     * disabled. If both results are equal, its considered correct.
     *
     * @param resultLimit Maximum result size of all the MDX operations in this
     *                    query. This might be hard to estimate as it is usually
     *                    larger than the rowCount of the final result. Setting
     *                    it to 0 will cause this limit to be ignored.
     * @param rowCount    Number of rows returned
     * @param mdx         Query
     */
    protected void checkNative(Context<?> context,
        int resultLimit, int rowCount, String mdx)
    {
        checkNative(context, resultLimit, rowCount, mdx, null, false);
    }

    /**
     * Runs a query twice, with native crossjoin optimization enabled and
     * disabled. If both results are equal,and both aggree with the expected
     * result, it is considered correct.
     *
     * <p>Optionally the query can be run with
     * fresh connection. This is useful if the test case sets its certain
     * mondrian properties, e.g. native properties like:
     * mondrian.native.filter.enable
     *
     * @param resultLimit     Maximum result size of all the MDX operations in
     *                        this query. This might be hard to estimate as it
     *                        is usually larger than the rowCount of the final
     *                        result. Setting it to 0 will cause this limit to
     *                        be ignored.
     * @param rowCount        Number of rows returned. (That is, the number
     *                        of positions on the last axis of the query.)
     * @param mdx             Query
     * @param expectedResult  Expected result string
     * @param freshConnection Whether fresh connection is required
     */
    protected void checkNative(
        Context<?> context,
        int resultLimit,
        int rowCount,
        String mdx,
        String expectedResult,
        boolean freshConnection)
    {
        // Don't run the test if we're testing expression dependencies.
        // Expression dependencies cause spurious interval calls to
        // 'level.getMembers()' which create false negatives in this test.
        if (context.getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) > 0) {
            return;
        }

        context.getConnectionWithDefaultRole().getCacheControl(null).flushSchemaCache();
        try {
            LoggerFactory.getLogger(getClass()).debug("*** Native: " + mdx);
            boolean reuseConnection = !freshConnection;
            //Connection con =
            //    getTestContext()
            //        .withSchemaPool(reuseConnection)
            //        .getConnection();
            Connection con = context.getConnectionWithDefaultRole();
            RolapNativeRegistry reg = getRegistry(con);
            reg.useHardCache(true);
            TestListener listener = new TestListener();
            reg.setListener(listener);
            reg.setEnabled(true);
            TestCase c = new TestCase(con, resultLimit, rowCount, mdx);
            Result result = c.run();
            String nativeResult = toString(result);
            if (!listener.isFoundEvaluator()) {
                fail("expected native execution of " + mdx);
            }
            if (!listener.isExecuteSql()) {
                fail("cache is empty: expected SQL query to be executed");
            }
            if (context.getConfigValue(ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE,
                    ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE_DEFAULT_VALUE, Boolean.class))
            {
                // run once more to make sure that the result comes from cache
                // now
                listener.setExecuteSql(false);
                c.run();
                if (listener.isExecuteSql()) {
                    fail("expected result from cache when query runs twice");
                }
            }
            con.close();

            LoggerFactory.getLogger(getClass()).debug("*** Interpreter: " + mdx);

            context.getConnectionWithDefaultRole().getCacheControl(null).flushSchemaCache();
            //con = getTestContext().withSchemaPool(false).getConnection();
            con = context.getConnectionWithDefaultRole();
            reg = getRegistry(con);
            listener.setFoundEvaluator(false);
            reg.setListener(listener);
            // disable RolapNativeSet
            reg.setEnabled(false);
            result = executeQuery(mdx, con);
            String interpretedResult = toString(result);
            if (listener.isFoundEvaluator()) {
                fail("did not expect native executions of " + mdx);
            }

            if (expectedResult != null) {
                assertEquals(
                    expectedResult,
                    nativeResult,
                    "Native implementation returned different result than "
                    + "expected; MDX=" + mdx);
                assertEquals(
                    expectedResult,
                    interpretedResult,
                    "Interpreter implementation returned different result than "
                    + "expected; MDX=" + mdx);
            }

            if (!nativeResult.equals(interpretedResult)) {
                assertEquals(
                    interpretedResult,
                    nativeResult,
                    "Native implementation returned different result than "
                    + "interpreter; MDX=" + mdx);
            }
        } finally {
            Connection con = context.getConnectionWithDefaultRole();
            RolapNativeRegistry reg = getRegistry(con);
            reg.setEnabled(true);
            reg.useHardCache(false);
        }
    }

    private static int getRowCount(Result result) {
        return result.getAxes()[result.getAxes().length - 1]
            .getPositions().size();
    }

    public Result executeQuery(String mdx, Connection connection) {
    	Query query = connection.parseQuery(mdx);
        query.setResultStyle(ResultStyle.LIST);
        return connection.execute(query);
    }

    /**
     * Convenience method for debugging; please do not delete.
     */
    public void assertNotNative(Context<?> context, String mdx) {
        new BatchTestCase().checkNotNative(context, mdx, null);
    }

    public static void checkNotNative(Context<?> context, String mdx, Result expectedResult) {
        BatchTestCase test = new BatchTestCase();
        test.checkNotNative(context,
                getRowCount(expectedResult),
                mdx,
                toString(expectedResult));
    }

    public static void checkNative(Context<?> context, String mdx, Result expectedResult) {
        BatchTestCase test = new BatchTestCase();
        test.checkNative(context,
                0,
                getRowCount(expectedResult),
                mdx,
                toString(expectedResult),
                true);
    }
    /**
     * Convenience method for debugging; please do not delete.
     */
    public void assertNative(Context<?> context, String mdx) {
        new BatchTestCase().checkNative(context,0, 0, mdx, null, true);
    }

    /**
     * Runs an MDX query with a predefined resultLimit and checks the number of
     * positions of the row axis. The reduced resultLimit ensures that the
     * optimization is present.
     */
    protected class TestCase {
        /**
         * Maximum number of rows to be read from SQL. If more than this number
         * of rows are read, the test will fail.
         */
        final int resultLimit;

        /**
         * MDX query to execute.
         */
        final String query;

        /**
         * Number of positions we expect on rows axis of result.
         */
        final int rowCount;

        /**
         * Mondrian connection.
         */
        final Connection con;

        public TestCase(
            Connection con, int resultLimit, int rowCount, String query)
        {
            this.con = con;
            this.resultLimit = resultLimit;
            this.rowCount = rowCount;
            this.query = query;
        }

        protected Result run() {
            con.getCacheControl(null).flushSchemaCache();
            // Contexts provisioned by @RolapContextTest are immutable per test (no
            // runtime config mutation), so resultLimit can no longer be pushed onto
            // the connection's context here - callers are expected to already have
            // the right RESULT_LIMIT via @RolapConfig.
            return runAndCheckRowCount();
        }

        private Result runAndCheckRowCount() {
            Result result = executeQuery(query, con);

            // Check the number of positions on the last axis, which is
            // the ROWS axis in a 2 axis query.
            int numAxes = result.getAxes().length;
            Axis a = result.getAxes()[numAxes - 1];
            final int positionCount = a.getPositions().size();
            assertEquals(rowCount, positionCount);
            return result;
        }
    }

    /**
     * Fake exception to interrupt the test when we see the desired query.
     * It is an {@link Error} because we need it to be unchecked
     * ({@link Exception} is checked), and we don't want handlers to handle
     * it.
     */
    static class Bomb extends Error {
        final String sql;

        Bomb(final String sql) {
            this.sql = sql;
        }
    }

    private static class TriggerHook implements RolapUtil.ExecuteQueryHook {
        private final String trigger;
        private boolean foundMatch = false;

        public TriggerHook(String trigger) {
            // Normalise whitespace once; matchTrigger normalises the captured SQL
            // the same way so a multi-line indented expectation matches a
            // single-line captured query.
            this.trigger = trigger.replaceAll("\\s+", " ").trim();
        }

        private boolean matchTrigger(String sql) {
            if (trigger == null) {
                return true;
            }
            // SQL is whitespace-insensitive; collapse runs so that an unformatted
            // captured query matches a multi-line indented expectation. Quotes are
            // also normalised because mysql drivers vary on backtick vs. doublequote.
            String s = replaceQuotes(sql).replaceAll("\\s+", " ").trim();
            String t = replaceQuotes(trigger);
            if (s.startsWith(t) && !foundMatch) {
                foundMatch = true;
            }
            return s.startsWith(t);
        }

        @Override
		public void onExecuteQuery(String sql) {
            if (matchTrigger(sql)) {
                throw new Bomb(sql);
            }
        }

        public boolean foundMatch() {
            return foundMatch;
        }
    }

    /**
     * Gets notified on various test events:
     * <ul>
     * <li>when a matching native evaluator was found
     * <li>when SQL is executed
     * <li>when result is found in the cache
     * </ul>
     */
    static class TestListener implements Listener {
        boolean foundEvaluator;
        boolean foundInCache;
        boolean executeSql;

        boolean isExecuteSql() {
            return executeSql;
        }

        void setExecuteSql(boolean excecuteSql) {
            this.executeSql = excecuteSql;
        }

        boolean isFoundEvaluator() {
            return foundEvaluator;
        }

        void setFoundEvaluator(boolean foundEvaluator) {
            this.foundEvaluator = foundEvaluator;
        }

        boolean isFoundInCache() {
            return foundInCache;
        }

        void setFoundInCache(boolean foundInCache) {
            this.foundInCache = foundInCache;
        }

        @Override
		public void foundEvaluator(NativeEvent e) {
            this.foundEvaluator = true;
        }

        @Override
		public void foundInCache(TupleEvent e) {
            this.foundInCache = true;
        }

        @Override
		public void executingSql(TupleEvent e) {
            this.executeSql = true;
        }
    }


    /**
     * Converts a {@link Result} to text in traditional format.
     *
     * @param result Query result
     * @return Result as text
     */
    private static String toString(Result result) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        result.print(pw);
        pw.flush();
        return sw.toString();
    }
}

// End BatchTestCase.java

