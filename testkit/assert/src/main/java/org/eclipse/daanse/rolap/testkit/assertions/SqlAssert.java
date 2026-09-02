/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.testkit.assertions;

import static org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct.getDatabaseProduct;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.query.Quoting;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.member.MemberCacheHelper;
import org.eclipse.daanse.rolap.common.member.SmartMemberReader;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.opentest4j.AssertionFailedError;

/**
 * Fluent SQL-generation assertions:
 * {@code SqlAssert.forQuery(connection, mdx).expectSql(SqlPattern.mysql("...")).verify()}.
 *
 * <p>
 * Replaces the legacy {@code TestUtil.assertQuerySql} / {@code assertNoQuerySql} /
 * {@code assertQuerySqlOrNot} family: parses and executes {@code mdx} once per pattern whose
 * dialect matches the connection's, and - via a query-execution hook, same trick as the legacy
 * code - interrupts execution the instant a SQL statement's prefix matches the pattern's
 * trigger, then checks whether that (or the absence of that) is what {@link QuerySqlAssert#expectSql} /
 * {@link QuerySqlAssert#expectNoSql} called for.
 *
 * <p>
 * {@link QuerySqlAssert#verify()} is the terminal call - {@code expectSql}/{@code expectNoSql} only
 * record what's expected, exactly as in the sample call shapes this class was modeled on.
 */
public final class SqlAssert {

    private SqlAssert() {
    }

    /** Starts a fluent SQL-generation assertion for {@code mdxQuery} run over {@code connection}. */
    public static QuerySqlAssert forQuery(Connection connection, String mdxQuery) {
        return new QuerySqlAssert(connection, mdxQuery);
    }

    /**
     * Checks that {@code actualSql} - typically a drill-through SQL string, not one captured via
     * {@link #forQuery} - matches {@code expectedSql} once both are dialectized and stripped of
     * quotes, then runs {@code actualSql} against {@code connection}'s datasource and checks it
     * returns {@code expectedRows} rows. Replaces the legacy {@code TestUtil.assertSqlEquals}.
     */
    public static void assertSqlEquals(Connection connection,
                                       String expectedSql,
                                       String actualSql,
                                       int expectedRows) {
        assertSqlEquals(connection, expectedSql, actualSql, expectedRows, false);
    }

    /**
     * Like {@link #assertSqlEquals(Connection, String, String, int)}, but compares the SQL
     * whitespace-insensitively (every run of whitespace - including newlines - collapsed to a
     * single space, then trimmed). Use this for queries produced by the generic statement
     * builder, whose {@code DialectSqlRenderer} emits compact single-line SQL that is
     * token-for-token equal to the legacy {@code SqlSelectQuery} output but not format-equal. The
     * datasource row-count check still runs against the actual SQL.
     */
    public static void assertSqlEqualsIgnoreFormatting(Connection connection,
                                                       String expectedSql,
                                                       String actualSql,
                                                       int expectedRows) {
        assertSqlEquals(connection, expectedSql, actualSql, expectedRows, true);
    }

    /** Wraps {@code sql} as a single-dialect {@link SqlPattern} for the MySQL/MariaDB dialect. */
    public static SqlPattern[] mysqlPattern(String sql) {
        return sqlPattern(DatabaseProduct.MYSQL, sql);
    }

    private static SqlPattern[] sqlPattern(DatabaseProduct db, String sql) {
        return new SqlPattern[]{new SqlPattern(db, sql, sql.length())};
    }

    private static void assertSqlEquals(Connection connection,
                                        String expectedSql,
                                        String actualSql,
                                        int expectedRows,
                                        boolean ignoreFormatting) {
        // if the actual SQL isn't in the current dialect we have some
        // problems... probably with the dialectize method
        assertEquals(dialectize(connection, actualSql), actualSql);

        String transformedExpectedSql = removeQuotes(dialectize(connection, expectedSql));
        String transformedActualSql = removeQuotes(actualSql);
        if (ignoreFormatting) {
            transformedExpectedSql = normalizeWhitespace(transformedExpectedSql);
            transformedActualSql = normalizeWhitespace(transformedActualSql);
        } else {
            transformedExpectedSql = transformedExpectedSql.replaceAll("\r\n", "\n");
            transformedActualSql = transformedActualSql.replaceAll("\r\n", "\n");
        }
        assertEquals(transformedExpectedSql, transformedActualSql);

        checkSqlAgainstDatasource(connection, actualSql, expectedRows);
    }

    private static String normalizeWhitespace(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    /**
     * Converts a SQL string into the current dialect.
     *
     * <p>
     * This is not intended to be a general purpose method: it looks for specific patterns known to
     * occur in tests, in particular "=as=" and "fname + ' ' + lname".
     *
     * @param sql SQL string in generic dialect
     * @return SQL string converted into current dialect
     */
    private static String dialectize(Connection connection, String sql) {
        final String search = "fname \\+ ' ' \\+ lname";
        final Dialect dialect = connection.getContext().getDialect();
        final DatabaseProduct databaseProduct = getDatabaseProduct(dialect.name());
        switch (databaseProduct) {
            case MYSQL:
            case MARIADB:
                // Mysql would generate "CONCAT(...)"
                sql = sql.replaceAll(
                    search,
                    "CONCAT(`customer`.`fname`, ' ', `customer`.`lname`)");
                break;
            case POSTGRES:
            case ORACLE:
            case LUCIDDB:
            case TERADATA:
                sql = sql.replaceAll(
                    search,
                    "`fname` || ' ' || `lname`");
                break;
            case DERBY:
                sql = sql.replaceAll(
                    search,
                    "`customer`.`fullname`");
                break;
            case INGRES:
                sql = sql.replaceAll(
                    search,
                    "fullname");
                break;
            case DB2:
            case DB2_AS400:
            case DB2_OLD_AS400:
                sql = sql.replaceAll(
                    search,
                    "CONCAT(CONCAT(`customer`.`fname`, ' '), `customer`.`lname`)");
                break;
            default:
                break;
        }

        if (databaseProduct == DatabaseProduct.ORACLE) {
            // " + tableQualifier + "
            sql = sql.replaceAll(" =as= ", " ");
        } else {
            sql = sql.replaceAll(" =as= ", " as ");
        }
        return sql;
    }

    private static String removeQuotes(String actualSql) {
        String transformedActualSql = actualSql.replaceAll("`", "");
        transformedActualSql = transformedActualSql.replaceAll("\"", "");
        return transformedActualSql;
    }

    private static void checkSqlAgainstDatasource(Connection connection,
                                                  String actualSql,
                                                  int expectedRows) {

        java.sql.Connection jdbcConn = null;
        java.sql.Statement stmt = null;
        java.sql.ResultSet rs = null;

        try {
            jdbcConn = connection.getDataSource().getConnection();
            stmt = jdbcConn.createStatement();

            if (RolapUtil.SQL_LOGGER.isDebugEnabled()) {
                StringBuffer sqllog = new StringBuffer();
                sqllog.append("mondrian.test.TestContext: executing sql [");
                if (actualSql.indexOf('\n') >= 0) {
                    // SQL appears to be formatted as multiple lines. Make it
                    // start on its own line.
                    sqllog.append("\n");
                }
                sqllog.append(actualSql);
                sqllog.append(']');
                RolapUtil.SQL_LOGGER.debug(sqllog.toString());
            }

            long startTime = System.currentTimeMillis();
            rs = stmt.executeQuery(actualSql);
            long time = System.currentTimeMillis();
            final long execMs = time - startTime;

            RolapUtil.SQL_LOGGER.debug(", exec " + execMs + " ms");

            int rows = 0;
            while (rs.next()) {
                rows++;
            }

            assertEquals(expectedRows, rows, "row count");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(
                "ERROR in SQL - invalid for database: "
                    + ""
                    + "\n" + actualSql,
                e);
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (Exception e1) {
                // ignore
            }
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (Exception e1) {
                // ignore
            }
            try {
                if (jdbcConn != null) {
                    jdbcConn.close();
                }
            } catch (Exception e1) {
                // ignore
            }
        }
    }

    public static final class QuerySqlAssert {

        private final Connection connection;
        private final String mdxQuery;
        private boolean bypassSchemaCache;
        // Defaults to true: the legacy assertQuerySql/assertNoQuerySql convenience wrappers this
        // class replaces always clear the cache before executing - it's not a flag callers set,
        // it's baked into the wrapper. Matching that default here avoids a warm cache silently
        // turning "did the query run this SQL" into "was this SQL ever run, maybe minutes ago".
        private boolean clearCacheFirst = true;
        private SqlPattern[] patterns;
        private boolean negative;

        private QuerySqlAssert(Connection connection, String mdxQuery) {
            this.connection = Objects.requireNonNull(connection, "connection");
            this.mdxQuery = Objects.requireNonNull(mdxQuery, "mdxQuery");
        }

        /**
         * Matches the legacy 6-arg form's {@code bypassSchemaCache} flag - which is itself
         * currently a no-op there too (the "grab a fresh, schema-pool-bypassing connection" step
         * is commented out in {@code TestUtil.assertQuerySqlOrNot}). Kept for call-site parity;
         * wire it up for real once that gap is closed upstream.
         */
        public QuerySqlAssert bypassSchemaCache() {
            this.bypassSchemaCache = true;
            return this;
        }

        /**
         * Clears the query's cube's aggregation/member cache before executing, so its SQL runs as
         * if for the first time. On by default (see field comment); calling this is only ever
         * needed to undo a prior {@link #keepCache()} on the same builder.
         */
        public QuerySqlAssert clearCacheFirst() {
            this.clearCacheFirst = true;
            return this;
        }

        /**
         * Opts out of the default cache-clearing - matches passing {@code clearCache=false} to the
         * legacy 6-arg {@code assertQuerySqlOrNot}. Use when the test deliberately wants to
         * observe cache-hit behavior (e.g. asserting that a warm cache produces no SQL at all).
         */
        public QuerySqlAssert keepCache() {
            this.clearCacheFirst = false;
            return this;
        }

        /**
         * Sets the cache-clearing flag from a caller-supplied value, for call sites that decide
         * at runtime rather than picking {@link #clearCacheFirst()} or {@link #keepCache()}
         * literally.
         */
        public QuerySqlAssert clearCacheFirst(boolean clearCacheFirst) {
            this.clearCacheFirst = clearCacheFirst;
            return this;
        }

        /** Records that {@link #verify()} must see each matching-dialect pattern's SQL get executed. */
        public QuerySqlAssert expectSql(SqlPattern... patterns) {
            this.patterns = Objects.requireNonNull(patterns, "patterns");
            this.negative = false;
            return this;
        }

        /** Records that {@link #verify()} must NOT see any matching-dialect pattern's SQL get executed. */
        public QuerySqlAssert expectNoSql(SqlPattern... patterns) {
            this.patterns = Objects.requireNonNull(patterns, "patterns");
            this.negative = true;
            return this;
        }

        /**
         * Runs the query once per pattern whose dialect matches the connection's, and checks each
         * against {@link #expectSql}/{@link #expectNoSql}'s expectation. A dialect with no matching
         * pattern at all is skipped - the assertion is trivially satisfied for it, same as legacy.
         */
        public void verify() {
            if (patterns == null) {
                throw new IllegalStateException("call expectSql(...) or expectNoSql(...) before verify()");
            }

            Dialect dialect = connection.getContext().getDialect();
            DatabaseProduct databaseProduct = getDatabaseProduct(dialect.name());
            boolean patternFound = false;

            for (SqlPattern sqlPattern : patterns) {
                if (!sqlPattern.hasDatabaseProduct(databaseProduct)) {
                    continue;
                }
                patternFound = true;
                verifyOne(sqlPattern, databaseProduct);
            }

            if (!patternFound) {
                warnNoPatternForDialect(dialect, databaseProduct);
            }
        }

        private void verifyOne(SqlPattern sqlPattern, DatabaseProduct databaseProduct) {
            String sql = dialectize(databaseProduct, sqlPattern.getSql());
            String trigger = dialectize(databaseProduct, sqlPattern.getTriggerSql());

            TriggerHook hook = new TriggerHook(trigger);
            RolapUtil.setHook(connection.getContext(), hook);
            Bomb bomb = null;
            try {
                Query query = connection.parseQuery(mdxQuery);
                if (clearCacheFirst) {
                    clearCache(connection, (RolapCube) query.getCube());
                }
                connection.execute(query);
            } catch (Bomb caught) {
                bomb = caught;
            } catch (RuntimeException e) {
                bomb = Util.getMatchingCause(e, Bomb.class);
                if (bomb == null) {
                    throw e;
                }
            } finally {
                RolapUtil.setHook(connection.getContext(), null);
            }

            if (negative) {
                if (bomb != null || hook.foundMatch()) {
                    throw new AssertionFailedError("forbidden query [" + sql + "] detected"
                        + System.lineSeparator() + "MDX:" + System.lineSeparator() + mdxQuery);
                }
                return;
            }

            if (bomb == null && !hook.foundMatch()) {
                StringBuilder seen = new StringBuilder();
                for (String s : hook.seen()) {
                    seen.append(System.lineSeparator()).append("--- actual ---").append(System.lineSeparator())
                        .append(s);
                }
                throw new AssertionFailedError(
                    "expected query [" + sql + "] did not occur; statements seen:" + seen
                        + System.lineSeparator() + "MDX:" + System.lineSeparator() + mdxQuery);
            }
            if (bomb != null) {
                String expected = replaceQuotes(sql.replaceAll("\r\n", "\n"));
                String actual = replaceQuotes(bomb.sql.replaceAll("\r\n", "\n"));
                if (!expected.equals(actual)) {
                    throw new AssertionFailedError(
                        "SQL did not match pattern" + System.lineSeparator() + "MDX:" + System.lineSeparator()
                            + mdxQuery,
                        expected, actual);
                }
            }
        }

        private void warnNoPatternForDialect(Dialect dialect, DatabaseProduct databaseProduct) {
            String warnDialect = connection.getContext().getConfigValue(
                ConfigConstants.WARN_IF_NO_PATTERN_FOR_DIALECT,
                ConfigConstants.WARN_IF_NO_PATTERN_FOR_DIALECT_DEFAULT_VALUE, String.class);
            if (warnDialect.equals(databaseProduct.toString())) {
                System.out.println(
                    "[No expected SQL statements found for dialect \"" + dialect + "\" and test not run]");
            }
        }
    }

    private static String replaceQuotes(String s) {
        return s.replace('`', '"').replace('\'', '"');
    }

    /** Fake exception used to interrupt execution the instant the trigger SQL is seen. */
    private static final class Bomb extends Error {
        final String sql;

        Bomb(String sql) {
            this.sql = sql;
        }
    }

    private static final class TriggerHook implements RolapUtil.ExecuteQueryHook {

        private final String trigger;
        private boolean foundMatch;
        private final List<String> seen = new java.util.ArrayList<>();

        TriggerHook(String trigger) {
            this.trigger = trigger.replaceAll("\r\n", "").replaceAll("\r", "").replaceAll("\n", "");
        }

        private boolean matchTrigger(String sql) {
            String normalizedSql = sql.replaceAll("\r\n", "").replaceAll("\r", "").replaceAll("\n", "");
            String s = replaceQuotes(normalizedSql);
            String t = replaceQuotes(trigger);
            if (s.startsWith(t) && !foundMatch) {
                foundMatch = true;
            }
            return s.startsWith(t);
        }

        @Override
        public void onExecuteQuery(String sql) {
            seen.add(sql);
            if (matchTrigger(sql)) {
                throw new Bomb(sql);
            }
        }

        boolean foundMatch() {
            return foundMatch;
        }

        List<String> seen() {
            return seen;
        }
    }

    private static String dialectize(DatabaseProduct d, String sql) {
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

    public static void clearCache(Connection connection, RolapCube cube) {
        // Clear the cache for the Sales cube, so the query runs as if
        // for the first time. (TODO: Cleaner way to do this.)
        final Cube salesCube =
            connection.getCatalog().lookupCube(cube.getName()).orElseThrow();
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
        // Flush the cache, to ensure that the query gets executed.
        cube.clearCachedAggregations(true);

        CacheControl cacheControl = connection.getCacheControl(null);
        final CacheControl.CellRegion measuresRegion =
            cacheControl.createMeasuresRegion(cube);
        cacheControl.flush(measuresRegion);
        waitForFlush(cacheControl, measuresRegion, cube.getName());
    }

    private static void waitForFlush(
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

    private static String getCacheState(
        final CacheControl cacheControl,
        final CacheControl.CellRegion measuresRegion)
    {
        StringWriter out = new StringWriter();
        cacheControl.printCacheState(new PrintWriter(out), measuresRegion);
        return out.toString();
    }

    private static boolean regionIsEmpty(
        final String cacheState, final String cubeName)
    {
        return !cacheState.contains("Cube:[" + cubeName + "]");
    }
}
