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
 * jhyde, Feb 14, 2003
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

package org.eclipse.daanse.test.clearview;

import java.io.PrintWriter;
import java.io.StringWriter;
import static org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct.getDatabaseProduct;

import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;
import org.eclipse.daanse.rolap.BatchTestCase;
import org.eclipse.daanse.test.DiffRepository;


/**
 * <code>ClearViewBase</code> is the base class to build test cases which test
 * queries against the FoodMart database. A concrete sub class and
 * a ref.xml file will be needed for each test suites to be added. MDX queries
 * and their expected results are maintained separately in *.ref.xml files.
 * If you would prefer to see them as inlined Java string literals, run
 * ant target "generateDiffRepositoryJUnit" and then use
 * files *JUnit.java which will be generated in this directory.
 *
 * @author John Sichi
 * @author Richard Emberson
 * @author Khanh Vu
 *
 * @since Jan 25, 2007
 */
 @RolapContextTest(FoodmartTestInstance.class)
 public abstract class ClearViewBase extends BatchTestCase {

    public abstract DiffRepository getDiffRepos();

    private String name;

    // implement TestCase
    @BeforeEach
    protected void beforeEach() throws Exception {
    }


    // implement TestCase
    @AfterEach
    protected void afterEach() {
        DiffRepository diffRepos = getDiffRepos();
        diffRepos.setCurrentTestCaseName(null);
    }

    /**
     * Runs every test case in {@link #getDiffRepos()}, in diff-repository
     * order. Subclasses whose {@code @RolapContextTest}/{@code @RolapConfig}
     * match this default and whose per-case behavior needs no customization
     * inherit this directly instead of overriding it. A subclass that needs
     * different context config, or to skip/special-case individual test
     * names, overrides this method and calls {@link #runOneTestCase(Context)}
     * per case (mirroring the loop below) rather than looping over
     * {@code super.runTest(context)} - that would take the whole suite once
     * per case, since {@code runTest} is a suite-level driver, not a
     * single-case executor.
     */
    @Test
    protected void runTest(Context<?> context) {
        DiffRepository diffRepos = getDiffRepos();
        for (String name : diffRepos.getTestCaseNames()) {
            setName(name);
            diffRepos.setCurrentTestCaseName(name);
            runOneTestCase(context);
        }
    }

    /** Runs the single test case current set via {@link #setName}/{@code setCurrentTestCaseName}. */
    protected void runOneTestCase(Context<?> context) {
            DiffRepository diffRepos = getDiffRepos();
            // add calculated member to a cube if specified in the xml file
            /*
            String cubeName = diffRepos.expand(null, "${modifiedCubeName}").trim();
            if (!(cubeName.equals("")
                    || cubeName.equals("${modifiedCubeName}"))) {
                String customDimensions = diffRepos.expand(
                        null, "${customDimensions}");
                customDimensions =
                        (!(customDimensions.equals("")
                                || customDimensions.equals("${customDimensions}")))
                                ? customDimensions : null;
                String measures = diffRepos.expand(
                        null, "${measures}");
                measures =
                        (!(measures.equals("")
                                || measures.equals("${measures}")))
                                ? measures : null;
                String calculatedMembers = diffRepos.expand(
                        null, "${calculatedMembers}");
                calculatedMembers =
                        (!(calculatedMembers.equals("")
                                || calculatedMembers.equals("${calculatedMembers}")))
                                ? calculatedMembers : null;
                String namedSets = diffRepos.expand(
                        null, "${namedSets}");
                namedSets =
                        (!(namedSets.equals("")
                                || namedSets.equals("${namedSets}")))
                                ? namedSets : null;
                ((BaseTestContext) context).update(SchemaUpdater.createSubstitutingCube(
                        cubeName, customDimensions, measures, calculatedMembers,
                        namedSets, false));

            }
             */

            // ExpandNonNative=true is supplied per subclass via
            // @RolapConfig, to match the way we configure it for ClearView.

            String mdx = diffRepos.expand(null, "${mdx}");
            String result = Util.NL + toString(
                    executeQuery(mdx, context.getConnectionWithDefaultRole()));
            diffRepos.assertEquals("result", "${result}", result);
    }

    protected void assertQuerySql(Connection connection, boolean flushCache)
    {
        DiffRepository diffRepos = getDiffRepos();

        if (buildSqlPatternArray(connection) == null) {
            return;
        }

        SqlAssert.forQuery(connection, diffRepos.expand(null, "${mdx}"))
            .clearCacheFirst(flushCache)
            .expectSql(buildSqlPatternArray(connection))
            .verify();
    }

    protected void assertNoQuerySql(Connection connection, boolean flushCache)
        throws Exception
    {
        DiffRepository diffRepos = getDiffRepos();

        if (buildSqlPatternArray(connection) == null) {
            return;
        }

        SqlAssert.forQuery(connection, diffRepos.expand(null, "${mdx}"))
            .clearCacheFirst(flushCache)
            .expectNoSql(buildSqlPatternArray(connection))
            .verify();
    }

    private SqlPattern[] buildSqlPatternArray(Connection connection) {
        DiffRepository diffRepos = getDiffRepos();
        Dialect d = getDialect(connection);
        DatabaseProduct dialect = getDatabaseProduct(d.name());
        String testCaseName = getName();
        String sql = diffRepos.get(
            testCaseName, "expectedSql", dialect.name());
        if (sql != null) {
            sql = sql.replaceAll("[ \t\n\f\r]+", " ").trim();
            return new SqlPattern[]{
                new SqlPattern(dialect, sql, null)
            };
        }
        return null;
    }

    public void setName(String name) {
        this.name = name;
    }

    protected String getName() {
        return this.name;
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
