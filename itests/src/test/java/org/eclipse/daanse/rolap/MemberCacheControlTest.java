/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.agg.OlapAggregationManager;
import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.api.cache.CacheControl.MemberEditCommand;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.Property;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.query.component.AxisOrdinal;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.member.MemberCache;
import org.eclipse.daanse.rolap.common.member.MemberReader;
import org.eclipse.daanse.rolap.common.member.SmartMemberReader;
import org.eclipse.daanse.rolap.element.RolapBaseCubeMeasure;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.FlushSchemaCacheModifier;
import org.eclipse.daanse.rolap.testkit.assertions.MdxAssert;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import org.eclipse.daanse.test.DiffRepository;

/**
 * Unit tests for flushing member cache and editing cached member properties.
 *
 * <p>The purpose of the cache control API is to clear the cache so that
 * changes made to the DBMS can be seen. However, it is difficult to write
 * tests that modify the database. So these tests just check that the relevant
 * caches have been cleared. It is assumed that the updated values will be
 * loaded next time mondrian goes to the database.
 *
 * @author mberkowitz
 * @since Jan 2008
 */
@RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.MemberCacheControlTestModifier.class },
        database = FoodmartDatabaseSupplier.class, data = MemberCacheControlTest.FoodmartData.class)
@RolapConfig(key = ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE, value = "false", type = Boolean.class)
class MemberCacheControlTest {
    private ExecutionContext executionContext;

    // TODO: add multi-thread tests.
    // TODO: test set properties negative: refer to invalid property
    // TODO: test set properties negative: set prop to invalid value
    // TODO: edit a different member not known to be in cache -- will it be
    //       fetched?

    // The cube member cache is switched off for the whole class by the
    // @RolapConfig above; testMemberOpsFailIfCacheEnabled overrides it back on.

    /** Named bridge onto the FoodMart CSVs (for the data=-Supplier form). */
    public static class FoodmartData implements org.eclipse.daanse.cwm.testkit.api.DataSupplier {
        @Override
        public java.util.Map<String, java.net.URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

    @AfterEach
    public void afterEach() {
//        RolapCatalogCache.instance().clear();
        // Note: ExecutionContext.pop() removed.
        executionContext = null;
    }

    // ~ Utility methods ------------------------------------------------------
    DiffRepository getDiffRepos() {
        return DiffRepository.lookup(MemberCacheControlTest.class);
    }

    private void prepareTestContext(Context<?> context) {
        final Connection conn = (Connection) context.getConnectionWithDefaultRole();
        final Statement statement = conn.getInternalStatement();
        final ExecutionImpl execution = new ExecutionImpl(statement, Optional.empty());
        //executionContext = new Locus(execution, getName(), null);
        ExecutionMetadata metadata = ExecutionMetadata.of("MemberCacheControlTest", "MemberCacheControlTest", null, 0);
        executionContext = execution.asContext().createChild(metadata, Optional.empty());
        // Note: ExecutionContext.push() removed. Wrap operations in ExecutionContext.where() if needed.
        // The reduced-size "Retail" dimension (MemberCacheControlTestModifier)
        // is now applied via the class-level @RolapContextTest catalog instead
        // of withSchemaEmf.
    }

    /**
     * Creates a map.
     *
     * @param keys Keys
     * @param values Values
     * @return Map
     */
    private static <K, V> Map<K, V> createMap(List<K> keys, List<V> values) {
        assert keys.size() == values.size();
        final Map<K, V> map = new HashMap<>(keys.size());
        for (int i = 0; i < keys.size(); ++i) {
            map.put(keys.get(i), values.get(i));
        }
        return map;
    }

    /**
     * Finds a Member by its name and the name of its containing cube.
     *
     * @param connection connection
     * @param cubeName Cube name
     * @param names the full-qualified Member name
     * @return the Member
     * @throws OlapRuntimeException when not found.
     */
    protected static RolapMember findMember(
        Connection connection,
        String cubeName,
        String... names)
    {
        Cube cube = connection.getCatalog().lookupCube(cubeName).orElseThrow();
        CatalogReader scr = cube.getCatalogReader(null).withLocus();
        return (RolapMember)
            scr.getMemberByUniqueName(IdImpl.toList(names), true);
    }

    /**
     * Prints all properties of a Member.
     *
     * @param pw Print writer
     * @param member Member
     * @return the same print writer
     */
    private static PrintWriter printMemberProperties(
        PrintWriter pw,
        Member member)
    {
        pw.print(member.getUniqueName());
        pw.print(" {");
        int k = -1;
        for (Property p : member.getLevel().getProperties()) {
            if (++k > 0) {
                pw.print(",");
            }
            pw.println();
            String name = p.getName();
            Object value = member.getPropertyValue(name);

            // Fixup value for different database representations of boolean and
            // numeric values.
            if (value == null) {
                // no fixup needed
            } else if (name.equals("Has coffee bar")) {
                if (value instanceof Number) {
                    value = ((Number) value).intValue() != 0;
                }
            } else if (name.endsWith(" Sqft")) {
                Number number = (Number) value;
                value =
                    number.equals(number.intValue())
                        ? number.intValue()
                        : Math.round(number.floatValue());
            }
            pw.print("  [");
            pw.print(name);
            pw.print("]=[");
            pw.print(value);
            pw.print("]");
        }
        pw.println("}");
        return pw;
    }

    /**
     * Prints properties of all Members on an Axis.
     *
     * @param pw Print writer
     * @param axis Axis
     * @return the same print writer
     */
    private static PrintWriter printMemberProperties(
        PrintWriter pw,
        Axis axis)
    {
        for (Position pos : axis.getPositions()) {
            for (Member m : pos) {
                printMemberProperties(pw, m).println();
            }
        }
        return pw;
    }

    /**
     * Prints properties of the Row Axis from a Result.
     *
     * @param pw Print writer
     * @param result Result
     * @return the same print writer
     */
    private static PrintWriter printRowMemberProperties(
        PrintWriter pw,
        Result result)
    {
        return printMemberProperties(
            pw,
            result.getAxes()[
                AxisOrdinal.StandardAxisOrdinal.ROWS.logicalOrdinal()]);
    }

    private static String getRowMemberPropertiesAsString(Result r) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        printRowMemberProperties(pw, r);
        pw.flush();
        return sw.toString();
    }

    private CacheControl.MemberSet createInterestingMemberSet(
        Connection connection, CacheControl cc)
    {
        return cc.createUnionSet(
            // all stores in OR
            cc.createMemberSet(findMember(connection, "Sales", "Retail", "OR"), true),
            // all stores in Hidalgo, Zacatecas
            cc.createMemberSet(
                findMember(connection, "Sales", "Retail", "Zacatecas", "Hidalgo"),
                true),
            // a single store
            cc.createMemberSet(
                findMember(connection, "Sales", "Retail", "CA", "Alameda", "HQ"),
                false),
            // a range of stores
            cc.createMemberSet(
                true, findMember(connection, "Sales", "Retail", "WA", "Bremerton"),
                true, findMember(connection, "Sales", "Retail", "Yucatan", "Merida"),
                false),
            // all stores in a range of states
            cc.createMemberSet(
                true, findMember(connection, "Sales", "Retail", "DF"),
                true, findMember(connection, "Sales", "Retail", "Jalisco"),
                true));
    }

    // ~ Tests ----------------------------------------------------------------

    /**
     * Tests operations on member sets, in particular the
     * {@link CacheControl#filter} method.
     */
    @Test
    void testFilter(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareTestContext(context);
        final Connection conn = context.getConnectionWithDefaultRole();
        final DiffRepository dr = getDiffRepos();
        final CacheControl cc = conn.getCacheControl(null);

        CacheControl.MemberSet memberSet = createInterestingMemberSet(conn, cc);
        dr.assertEquals("before", "${before}", memberSet.toString());
        final Member orMember = findMember(conn, "Sales", "Retail", "OR");
        final CacheControl.MemberSet filteredMemberSet =
            cc.filter(orMember.getLevel(), memberSet);
        dr.assertEquals("after", "${after}", filteredMemberSet.toString());
    }

    /**
     * Tests that member operations fail if cache is enabled.
     */
    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE, value = "true", type = Boolean.class)
    void testMemberOpsFailIfCacheEnabled(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareTestContext(context);
        final Connection conn = context.getConnectionWithDefaultRole();
        final CacheControl cc = conn.getCacheControl(null);
        final MemberEditCommand command =
            cc.createDeleteCommand(findMember(conn, "Sales", "Retail", "OR"));
        try {
            cc.execute(command);
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals(
                "Member cache control operations are not allowed unless "
                + "property daanse.rolap.EnableRolapCubeMemberCache is "
                + "false",
                e.getMessage());
        }
    }

    /**
     * Test that edits the properties of a single leaf Member.
     */
    @Test
    void testSetPropertyCommandOnLeafMember(Context<?> context) {
    	context.getCatalogCache().clear();
    	prepareTestContext(context);
        final Connection conn = context.getConnectionWithDefaultRole();
        final DiffRepository dr = getDiffRepos();
        final CacheControl cc = conn.getCacheControl(null);

        // A query that refers to a single leaf Member fetches the Member.
        // Changing Member properties does not affect Cell boundaries, so we
        // check that the MDX results are invariant.
        String mdx =
            "SELECT {[Measures].[Unit Sales]} ON COLUMNS,"
            + " {[Store].[USA].[CA].[San Francisco].[Store 14]}"
            + " ON ROWS FROM [Sales]";
        Query q = conn.parseQuery(mdx);
        Result r = conn.execute(q);
        dr.assertEquals(
            "props before",
            "${props before}",
            getRowMemberPropertiesAsString(r));
        final String resultString = toString(r);
        dr.assertEquals(
            "result before",
            "${result before}",
            resultString);

        // Change properties
        Member m =
            findMember(
                conn, "Sales", "Store", "USA", "CA", "San Francisco", "Store 14");
        cc.execute(cc.createSetPropertyCommand(m, "Store Manager", "Higgins"));
        cc.execute(
            cc.createCompoundCommand(
                Arrays.asList(
                    cc.createSetPropertyCommand(
                        m, "Street address", "770 Mission St"),
                    cc.createSetPropertyCommand(m, "Store Sqft", 6000),
                    cc.createSetPropertyCommand(
                        m, "Has coffee bar", "false"))));

        // Repeat same query; verify properties are changed.
        // Changing properties does not affect measures, so results unchanged.
        r = conn.execute(q);
        dr.assertEquals(
            "props after",
            "${props after}",
            getRowMemberPropertiesAsString(r));
        assertEquals(
            resultString,
            toString(r));
    }

    /**
     * Test that edits properties of Members at various Levels (use Retail
     * Dimension), but leaves grouping unchanged, so results not changed.
     */
    @Test
    void testSetPropertyCommandOnNonLeafMember(Context<?> context) {
    	context.getCatalogCache().clear();
    	prepareTestContext(context);
        final Connection conn = context.getConnectionWithDefaultRole();
        final DiffRepository dr = getDiffRepos();
        final CacheControl cc = conn.getCacheControl(null);

        String mdx = "SELECT {[Measures].[Unit Sales]} ON COLUMNS,"
            + " {[Retail].Members} ON ROWS "
            + "FROM [Sales]";
        Query q = conn.parseQuery(mdx);
        Result r = conn.execute(q);
        dr.assertEquals(
            "props before",
            "${props before}",
            getRowMemberPropertiesAsString(r));
        final String resultString = toString(r);
        dr.assertEquals(
            "result before",
            "${result before}",
            resultString);

        // Change some properties (TODO: change dimension table first)
        // set 2 properties (TODO: set both with one command)

        // try all ways to construct MemberSets
        CacheControl.MemberSet memberSet = createInterestingMemberSet(conn, cc);

        final Map<String, Object> propertyValues =
            createMap(
                Arrays.asList("Has coffee bar", "Store Sqft"),
                Arrays.asList((Object) "true", 123));
        MemberEditCommand command;

        // first, the member set contains members of various levels
        try {
            command = cc.createSetPropertyCommand(memberSet, propertyValues);
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals(
                "all members in set must belong to same level",
                e.getMessage());
        }

        // after we filter set to just members of store level we're ok
        final Member hqMember =
            findMember(conn, "Sales", "Retail", "CA", "Alameda", "HQ");
        final CacheControl.MemberSet filteredMemberSet =
            cc.filter(hqMember.getLevel(), memberSet);
        command =
            cc.createSetPropertyCommand(filteredMemberSet, propertyValues);
        final MemberEditCommand c = command;
        ExecutionContext.where(executionContext, () -> {
        	cc.execute(c);
        });


        // Repeat same query; verify properties were changed.
        // Changing properties does not affect measures, so results unchanged.
        r = conn.execute(q);
        dr.assertEquals(
            "props after",
            "${props after}",
            getRowMemberPropertiesAsString(r));
        assertEquals(
            resultString,
            toString(r));
    }

    @Test
    void testAddCommand(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareTestContext(context);
        final Connection conn = context.getConnectionWithDefaultRole();
        final CacheControl cc = conn.getCacheControl(null);
        final RolapCubeMember caCubeMember =
            (RolapCubeMember) findMember(conn, "Sales", "Retail", "CA");
        final RolapMember caMember = caCubeMember.member;
        final RolapMember rootMember = caMember.getParentMember();
        final RolapHierarchy hierarchy = caMember.getHierarchy();
        final RolapMember berkeleyMember =
            (RolapMember) hierarchy.createMember(
                caMember,
                caMember.getLevel().getChildLevel(),
                "Berkeley",
                null);
        final RolapBaseCubeMeasure unitSalesCubeMember =
            (RolapBaseCubeMeasure) findMember(
                conn, "Sales", "Measures", "Unit Sales");
        final RolapCubeMember yearCubeMember =
            (RolapCubeMember) findMember(
                conn, "Sales", "Time", "Year", "1997");
        final Member[] cacheRegionMembers =
            new Member[] {
                unitSalesCubeMember,
                caCubeMember,
                yearCubeMember
            };

        assertThatQuery(conn,
            "select {[Retail].[City].Members} on columns from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Retail].[Retail].[BC].[Vancouver]}\n"
            + "{[Retail].[Retail].[BC].[Victoria]}\n"
            + "{[Retail].[Retail].[CA].[Alameda]}\n"
            + "{[Retail].[Retail].[CA].[Beverly Hills]}\n"
            + "{[Retail].[Retail].[CA].[Los Angeles]}\n"
            + "{[Retail].[Retail].[CA].[San Diego]}\n"
            + "{[Retail].[Retail].[CA].[San Francisco]}\n"
            + "{[Retail].[Retail].[DF].[Mexico City]}\n"
            + "{[Retail].[Retail].[DF].[San Andres]}\n"
            + "{[Retail].[Retail].[Guerrero].[Acapulco]}\n"
            + "{[Retail].[Retail].[Jalisco].[Guadalajara]}\n"
            + "{[Retail].[Retail].[OR].[Portland]}\n"
            + "{[Retail].[Retail].[OR].[Salem]}\n"
            + "{[Retail].[Retail].[Veracruz].[Orizaba]}\n"
            + "{[Retail].[Retail].[WA].[Bellingham]}\n"
            + "{[Retail].[Retail].[WA].[Bremerton]}\n"
            + "{[Retail].[Retail].[WA].[Seattle]}\n"
            + "{[Retail].[Retail].[WA].[Spokane]}\n"
            + "{[Retail].[Retail].[WA].[Tacoma]}\n"
            + "{[Retail].[Retail].[WA].[Walla Walla]}\n"
            + "{[Retail].[Retail].[WA].[Yakima]}\n"
            + "{[Retail].[Retail].[Yucatan].[Merida]}\n"
            + "{[Retail].[Retail].[Zacatecas].[Camacho]}\n"
            + "{[Retail].[Retail].[Zacatecas].[Hidalgo]}\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 21,333\n"
            + "Row #0: 25,663\n"
            + "Row #0: 25,635\n"
            + "Row #0: 2,117\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 26,079\n"
            + "Row #0: 41,580\n"
            + "Row #0: \n"
            + "Row #0: 2,237\n"
            + "Row #0: 24,576\n"
            + "Row #0: 25,011\n"
            + "Row #0: 23,591\n"
            + "Row #0: 35,257\n"
            + "Row #0: 2,203\n"
            + "Row #0: 11,491\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n");
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[Retail].[CA].Children").returns(
"[Retail].[Retail].[CA].[Alameda]\n"
            + "[Retail].[Retail].[CA].[Beverly Hills]\n"
            + "[Retail].[Retail].[CA].[Los Angeles]\n"
            + "[Retail].[Retail].[CA].[San Diego]\n"
            + "[Retail].[Retail].[CA].[San Francisco]");
        final MemberReader memberReader = hierarchy.getMemberReader();
        final MemberCache memberCache =
            ((SmartMemberReader) memberReader).getMemberCache();
        List<RolapMember> caChildren =
            memberCache.getChildrenFromCache(caMember, null);
        assertEquals(5, caChildren.size());

        // Load cell data and check it is in cache
        executeQuery(conn,
            "select {[Measures].[Unit Sales]} on columns, {[Retail].[CA]} on rows from [Sales]");

        AbstractBasicContext<?> abc = (AbstractBasicContext) conn.getContext();
        final OlapAggregationManager aggMgr =
          abc.getAggregationManager();
        ExecutionContext.where(executionContext, () -> {
        assertEquals(
            Double.valueOf("74748"),
            ((AggregationManager)aggMgr).getCellFromAllCaches(
                AggregationManager.makeRequest(cacheRegionMembers), conn));
        });
        // Now tell the cache that [CA].[Berkeley] is new
        final MemberEditCommand command =
            cc.createAddCommand(berkeleyMember);
        cc.execute(command);

        // test that cells have been removed
        ExecutionContext.where(executionContext, () -> {
            assertNull(
                ((AggregationManager)aggMgr).getCellFromAllCaches(
                AggregationManager.makeRequest(cacheRegionMembers), conn));
    	});
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[Retail].[CA].Children").returns(
"[Retail].[Retail].[CA].[Alameda]\n"
            + "[Retail].[Retail].[CA].[Beverly Hills]\n"
            + "[Retail].[Retail].[CA].[Los Angeles]\n"
            + "[Retail].[Retail].[CA].[San Diego]\n"
            + "[Retail].[Retail].[CA].[San Francisco]\n"
            + "[Retail].[Retail].[CA].[Berkeley]");

        assertThatQuery(conn,
            "select {[Retail].[Retail].[City].Members} on columns from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Retail].[Retail].[BC].[Vancouver]}\n"
            + "{[Retail].[Retail].[BC].[Victoria]}\n"
            + "{[Retail].[Retail].[CA].[Alameda]}\n"
            + "{[Retail].[Retail].[CA].[Berkeley]}\n"
            + "{[Retail].[Retail].[CA].[Beverly Hills]}\n"
            + "{[Retail].[Retail].[CA].[Los Angeles]}\n"
            + "{[Retail].[Retail].[CA].[San Diego]}\n"
            + "{[Retail].[Retail].[CA].[San Francisco]}\n"
            + "{[Retail].[Retail].[DF].[Mexico City]}\n"
            + "{[Retail].[Retail].[DF].[San Andres]}\n"
            + "{[Retail].[Retail].[Guerrero].[Acapulco]}\n"
            + "{[Retail].[Retail].[Jalisco].[Guadalajara]}\n"
            + "{[Retail].[Retail].[OR].[Portland]}\n"
            + "{[Retail].[Retail].[OR].[Salem]}\n"
            + "{[Retail].[Retail].[Veracruz].[Orizaba]}\n"
            + "{[Retail].[Retail].[WA].[Bellingham]}\n"
            + "{[Retail].[Retail].[WA].[Bremerton]}\n"
            + "{[Retail].[Retail].[WA].[Seattle]}\n"
            + "{[Retail].[Retail].[WA].[Spokane]}\n"
            + "{[Retail].[Retail].[WA].[Tacoma]}\n"
            + "{[Retail].[Retail].[WA].[Walla Walla]}\n"
            + "{[Retail].[Retail].[WA].[Yakima]}\n"
            + "{[Retail].[Retail].[Yucatan].[Merida]}\n"
            + "{[Retail].[Retail].[Zacatecas].[Camacho]}\n"
            + "{[Retail].[Retail].[Zacatecas].[Hidalgo]}\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 21,333\n"
            + "Row #0: 25,663\n"
            + "Row #0: 25,635\n"
            + "Row #0: 2,117\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 26,079\n"
            + "Row #0: 41,580\n"
            + "Row #0: \n"
            + "Row #0: 2,237\n"
            + "Row #0: 24,576\n"
            + "Row #0: 25,011\n"
            + "Row #0: 23,591\n"
            + "Row #0: 35,257\n"
            + "Row #0: 2,203\n"
            + "Row #0: 11,491\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n");

        assertThatQuery(conn,
            "select [Retail].[Retail].Children on 0 from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Retail].[Retail].[BC]}\n"
            + "{[Retail].[Retail].[CA]}\n"
            + "{[Retail].[Retail].[DF]}\n"
            + "{[Retail].[Retail].[Guerrero]}\n"
            + "{[Retail].[Retail].[Jalisco]}\n"
            + "{[Retail].[Retail].[OR]}\n"
            + "{[Retail].[Retail].[Veracruz]}\n"
            + "{[Retail].[Retail].[WA]}\n"
            + "{[Retail].[Retail].[Yucatan]}\n"
            + "{[Retail].[Retail].[Zacatecas]}\n"
            + "Row #0: \n"
            + "Row #0: 74,748\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 67,659\n"
            + "Row #0: \n"
            + "Row #0: 124,366\n"
            + "Row #0: \n"
            + "Row #0: \n");

        List<RolapMember> rootChildren =
            memberCache.getChildrenFromCache(rootMember, null);
        if (rootChildren != null) { // might be null due to gc
            assertEquals(
                10, rootChildren.size());
        }
    }

    @Test
    void testDeleteCommand(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareTestContext(context);
        final Connection conn = context.getConnectionWithDefaultRole();
        final CacheControl cc = conn.getCacheControl(null);
        final RolapCubeMember sfCubeMember =
            (RolapCubeMember) findMember(
                conn, "Sales", "Retail", "CA", "San Francisco");
        final RolapMember caMember = sfCubeMember.member.getParentMember();
        final RolapHierarchy hierarchy = caMember.getHierarchy();
        final RolapBaseCubeMeasure unitSalesCubeMember =
            (RolapBaseCubeMeasure) findMember(
                conn, "Sales", "Measures", "Unit Sales");
        final RolapCubeMember yearCubeMember =
            (RolapCubeMember) findMember(
                conn, "Sales", "Time", "Year", "1997");
        final Member[] cacheRegionMembers =
            new Member[] {
                unitSalesCubeMember,
                sfCubeMember,
                yearCubeMember
            };

        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[CA].Children").returns(
"[Retail].[Retail].[CA].[Alameda]\n"
            + "[Retail].[Retail].[CA].[Beverly Hills]\n"
            + "[Retail].[Retail].[CA].[Los Angeles]\n"
            + "[Retail].[Retail].[CA].[San Diego]\n"
            + "[Retail].[Retail].[CA].[San Francisco]");

        final MemberReader memberReader = hierarchy.getMemberReader();
        final MemberCache memberCache =
            ((SmartMemberReader) memberReader).getMemberCache();
        List<RolapMember> caChildren =
            memberCache.getChildrenFromCache(caMember, null);
        assertEquals(5, caChildren.size());

        // Load cell data and check it is in cache
        executeQuery(conn,
            "select {[Measures].[Unit Sales]} on columns, {[Retail].[CA].[Alameda]} on rows from [Sales]");
        AbstractBasicContext<?> abc = (AbstractBasicContext) conn.getContext();
        final OlapAggregationManager aggMgr =
            abc.getAggregationManager();
        ExecutionContext.where(executionContext, () -> {
            assertEquals(
                    Double.valueOf("2117"),
                    ((AggregationManager)aggMgr).getCellFromAllCaches(
                    AggregationManager.makeRequest(cacheRegionMembers), conn));
        });

        // Now tell the cache that [CA].[San Francisco] has been removed.
        final MemberEditCommand command =
            cc.createDeleteCommand(sfCubeMember);
        cc.execute(command);

        // Children of CA should be 4
        assertEquals(
            4,
            memberCache.getChildrenFromCache(caMember, null).size());

        // test that cells have been removed
        ExecutionContext.where(executionContext, () -> {
        assertNull(
            ((AggregationManager)aggMgr).getCellFromAllCaches(
                AggregationManager.makeRequest(cacheRegionMembers), conn));
        });
        // The list of children should be updated.
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[Retail].[CA].Children").returns(
"[Retail].[Retail].[CA].[Alameda]\n"
            + "[Retail].[Retail].[CA].[Beverly Hills]\n"
            + "[Retail].[Retail].[CA].[Los Angeles]\n"
            + "[Retail].[Retail].[CA].[San Diego]");
    }

    @Disabled //TODO need investigate
    @Test
    void testMoveCommand(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareTestContext(context);
        final Connection conn = context.getConnectionWithDefaultRole();
        final CacheControl cc = conn.getCacheControl(null);
        final RolapCubeMember caCubeMember =
            (RolapCubeMember) findMember(conn, "Sales", "Retail", "CA");
        final RolapMember caMember = caCubeMember.member;
        final RolapHierarchy hierarchy = caMember.getHierarchy();
        final MemberReader memberReader = hierarchy.getMemberReader();
        final MemberCache memberCache =
            ((SmartMemberReader) memberReader).getMemberCache();
        final RolapMember alamedaMember =
            (RolapMember) hierarchy.createMember(
                caMember,
                caMember.getLevel().getChildLevel(),
                "Alameda",
                null);
        final RolapMember sfMember =
            (RolapMember) hierarchy.createMember(
                caMember,
                caMember.getLevel().getChildLevel(),
                "San Francisco",
                null);
        final RolapMember storeMember =
            (RolapMember) hierarchy.createMember(
                sfMember,
                sfMember.getLevel().getChildLevel(),
                "Store 14",
                null);

        // test axis contents
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[CA].Children").returns(
"[Retail].[CA].[Alameda]\n"
            + "[Retail].[CA].[Beverly Hills]\n"
            + "[Retail].[CA].[Los Angeles]\n"
            + "[Retail].[CA].[San Diego]\n"
            + "[Retail].[CA].[San Francisco]");
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[CA].[Alameda].Children").returns(
"[Retail].[CA].[Alameda].[HQ]");
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[CA].[San Francisco].Children").returns(
"[Retail].[CA].[San Francisco].[Store 14]");

        List<RolapMember> sfChildren =
            memberCache.getChildrenFromCache(sfMember, null);
        assertEquals(1, sfChildren.size());
        List<RolapMember> alamedaChildren =
            memberCache.getChildrenFromCache(alamedaMember, null);
        assertEquals(1, alamedaChildren.size());
        assertTrue(
            storeMember.getParentMember().equals(sfMember));

        // Now tell the cache that Store 14 moved to Alameda
        final MemberEditCommand command =
            cc.createMoveCommand(storeMember, alamedaMember);
        cc.execute(command);

        // The list of SF children should contain 0 elements
        assertEquals(
            0,
            memberCache.getChildrenFromCache(sfMember, null).size());

        // Check Alameda's children. It should be null as the parent's list
        // should be cleared.
        alamedaChildren =
            memberCache.getChildrenFromCache(alamedaMember, null);
        assertEquals(2, alamedaChildren.size());

        // test axis contents
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[CA].[San Francisco].Children").returns(
"");
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[CA].[Alameda].Children").returns(
"[Retail].[CA].[Alameda].[HQ]\n"
            + "[Retail].[CA].[Alameda].[Store 14]");

        // Test parent object
        assertTrue(
            storeMember.getParentMember().equals(alamedaMember));
    }

    @Disabled //TODO need investigate
    @Test
    void testMoveFailBadLevel(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareTestContext(context);
        final Connection conn = context.getConnectionWithDefaultRole();
        final CacheControl cc = conn.getCacheControl(null);
        final RolapCubeMember caCubeMember =
            (RolapCubeMember) findMember(conn, "Sales", "Retail", "CA");
        final RolapMember caMember = caCubeMember.member;
        final RolapHierarchy hierarchy = caMember.getHierarchy();
        final MemberReader memberReader = hierarchy.getMemberReader();
        final MemberCache memberCache =
            ((SmartMemberReader) memberReader).getMemberCache();
        final RolapMember sfMember =
            (RolapMember) hierarchy.createMember(
                caMember,
                caMember.getLevel().getChildLevel(),
                "San Francisco",
                null);
        final RolapMember storeMember =
            (RolapMember) hierarchy.createMember(
                sfMember,
                sfMember.getLevel().getChildLevel(),
                "Store 14",
                null);

        // test axis contents
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[CA].Children").returns(
"[Retail].[CA].[Alameda]\n"
            + "[Retail].[CA].[Beverly Hills]\n"
            + "[Retail].[CA].[Los Angeles]\n"
            + "[Retail].[CA].[San Diego]\n"
            + "[Retail].[CA].[San Francisco]");
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[CA].[San Francisco].Children").returns(
"[Retail].[CA].[San Francisco].[Store 14]");

        List<RolapMember> sfChildren =
            memberCache.getChildrenFromCache(sfMember, null);
        assertEquals(1, sfChildren.size());
        assertTrue(
            storeMember.getParentMember().equals(sfMember));

        // Now tell the cache that Store 14 moved to CA
        final MemberEditCommand command =
            cc.createMoveCommand(storeMember, caMember);
        try {
            cc.execute(command);
            fail("Should have failed due to improper level");
        } catch (OlapRuntimeException e) {
            assertEquals(
                "new parent belongs to different level than old",
                e.getCause().getMessage());
        }

        // The list of SF children should still contain 1 element
        assertEquals(
            1,
            memberCache.getChildrenFromCache(sfMember, null).size());

        // test axis contents. should not have been modified
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[CA].[San Francisco].Children").returns(
"[Retail].[CA].[San Francisco].[Store 14]");
        MdxAssert.assertThatAxis(conn, "Sales",
"[Retail].[CA].Children").returns(
"[Retail].[CA].[Alameda]\n"
            + "[Retail].[CA].[Beverly Hills]\n"
            + "[Retail].[CA].[Los Angeles]\n"
            + "[Retail].[CA].[San Diego]\n"
            + "[Retail].[CA].[San Francisco]");

        // Test parent object. should be the same
        assertTrue(
            storeMember.getParentMember().equals(sfMember));
    }

    /**
     * Tests a variety of negative cases including add/delete/move null members
     * add/delete/move members in parent-child hierarchies.
     */
    @Test
    void testAddCommandNegative(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareTestContext(context);
        final Connection conn = context.getConnectionWithDefaultRole();
        final CacheControl cc = conn.getCacheControl(null);

        MemberEditCommand command;
        try {
            command = cc.createAddCommand(null);
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals("cannot add null member", e.getMessage());
        }

        final RolapCubeMember alamedaCubeMember =
            (RolapCubeMember) findMember(
                conn, "Sales", "Retail", "CA", "Alameda");
        final RolapMember alamedaMember = alamedaCubeMember.member;
        final RolapMember caMember = alamedaMember.getParentMember();

        final RolapCubeMember empCubeMember =
            (RolapCubeMember) findMember(
                conn, "HR", "Employees", "Sheri Nowmer", "Michael Spence");
        final RolapMember empMember = empCubeMember.member;

        try {
            command = cc.createMoveCommand(null, alamedaMember);
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals("cannot move null member", e.getMessage());
        }

        try {
            command = cc.createMoveCommand(alamedaMember, null);
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals("cannot move member to null location", e.getMessage());
        }

        try {
            command = cc.createDeleteCommand((Member) null);
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals("cannot delete null member", e.getMessage());
        }

        try {
            command = cc.createSetPropertyCommand(null, "foo", 1);
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals(
                "cannot set properties on null member",
                e.getMessage());
        }

        try {
            command = cc.createAddCommand(empMember);
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals(
                "add member not supported for parent-child hierarchy",
                e.getMessage());
        }

        try {
            command = cc.createMoveCommand(empMember, null);
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals(
                "move member not supported for parent-child hierarchy",
                e.getMessage());
        }

        try {
            command = cc.createDeleteCommand(empMember);
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals(
                "delete member not supported for parent-child hierarchy",
                e.getMessage());
        }

        try {
            command = cc.createSetPropertyCommand(empMember, "foo", "bar");
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals(
                "set properties not supported for parent-child hierarchy",
                e.getMessage());
        }

        try {
            command = cc.createSetPropertyCommand(
                cc.createUnionSet(
                    cc.createMemberSet(alamedaMember, false),
                    cc.createMemberSet(caMember, false)),
                Collections.<String, Object>emptyMap());
            fail("expected exception, got " + command);
        } catch (IllegalArgumentException e) {
            assertEquals(
                "all members in set must belong to same level",
                e.getMessage());
        }
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1076">MONDRIAN-1076,
     * "Add CacheControl API to flush members from dimension cache"</a>.
     */
    @Disabled //disabled for CI build
    @Test
    void testFlushHierarchy(Context<?> context) {
    	context.getCatalogCache().clear();
        prepareTestContext(context);
        FlushSchemaCacheModifier.flushSchemaCache(context.getConnectionWithDefaultRole());
        final CacheControl cacheControl =
            context.getConnectionWithDefaultRole().getCacheControl(null);
        final Cube salesCube =
                context.getConnectionWithDefaultRole()
                .getCatalog().lookupCube("Sales").orElseThrow();

        final Logger logger = RolapUtil.SQL_LOGGER;
        final StringWriter sw = new StringWriter();
        //final Appender appender =
        //    Util.makeAppender("testMdcContext", sw, null);
        //Util.addAppender(appender, logger, org.apache.logging.log4j.Level.DEBUG);

        try {
            final Hierarchy storeHierarchy =
                salesCube.getDimensions().get(1).getHierarchies().getFirst();
            assertEquals("Store", storeHierarchy.getName());
            final CacheControl.MemberSet storeMemberSet =
                cacheControl.createMemberSet(
                    storeHierarchy.getAllMember(), true);
            final Runnable storeFlusher =
                new Runnable() {
                    @Override
					public void run() {
                        cacheControl.flush(storeMemberSet);
                    }
                };

            final Result result =
                executeQuery(context.getConnectionWithDefaultRole(),
                    "select [Store].[Mexico].[Yucatan] on 0 from [Sales]");
            final Member storeYucatanMember =
                result.getAxes()[0].getPositions().get(0).getFirst();
            final CacheControl.MemberSet storeYucatanMemberSet =
                cacheControl.createMemberSet(
                    storeYucatanMember, true);
            final Runnable storeYucatanFlusher =
                new Runnable() {
                    @Override
					public void run() {
                        cacheControl.flush(storeYucatanMemberSet);
                    }
                };

            checkFlushHierarchy(
                sw, true, storeFlusher,
                new Runnable() {
                    @Override
					public void run() {
                        // Check that <Member>.Children uses cache when applied
                        // to an 'all' member.
                        MdxAssert.assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
"[Store].Children").returns(
"[Store].[Canada]\n"
                            + "[Store].[Mexico]\n"
                            + "[Store].[USA]");
                    }
                });
            checkFlushHierarchy(
                sw, true, storeFlusher,
                new Runnable() {
                    @Override
					public void run() {
                        // Check that <Member>.Children uses cache when applied
                        // to regular member.
                        MdxAssert.assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
"[Store].[USA].[CA].Children").returns(
"[Store].[USA].[CA].[Alameda]\n"
                            + "[Store].[USA].[CA].[Beverly Hills]\n"
                            + "[Store].[USA].[CA].[Los Angeles]\n"
                            + "[Store].[USA].[CA].[San Diego]\n"
                            + "[Store].[USA].[CA].[San Francisco]");
                    }
                });

            // In contrast to preceding, flushing Yucatan should not affect
            // California.
            checkFlushHierarchy(
                sw, false, storeYucatanFlusher,
                new Runnable() {
                    @Override
					public void run() {
                        // Check that <Member>.Children uses cache when applied
                        // to regular member.
                        MdxAssert.assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
"[Store].[USA].[CA].Children").returns(
"[Store].[USA].[CA].[Alameda]\n"
                            + "[Store].[USA].[CA].[Beverly Hills]\n"
                            + "[Store].[USA].[CA].[Los Angeles]\n"
                            + "[Store].[USA].[CA].[San Diego]\n"
                            + "[Store].[USA].[CA].[San Francisco]");
                    }
                });

            checkFlushHierarchy(
                sw, true, storeFlusher, new Runnable() {
                    @Override
					public void run() {
                        // Check that <Hierarchy>.Members uses cache.
                        MdxAssert.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
"Count([Store].Members)").returns(
"63");
                    }
                });
            checkFlushHierarchy(
                sw, true, storeFlusher, new Runnable() {
                    @Override
					public void run() {
                        // Check that <Level>.Members uses cache.
                        MdxAssert.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
"Count([Store].[Store Name].Members)").returns(
"25");
                    }
                });


            // Time hierarchy is interesting because it has public 'all' member.
            // But you can still use the private all member for purposes like
            // flushing.
            final Hierarchy timeHierarchy =
                salesCube.getDimensions().get(4).getHierarchies().get(0);
            assertEquals("Time", timeHierarchy.getName());
            final CacheControl.MemberSet timeMemberSet =
                cacheControl.createMemberSet(
                    timeHierarchy.getAllMember(), true);
            final Runnable timeFlusher =
                new Runnable() {
                    @Override
					public void run() {
                        cacheControl.flush(timeMemberSet);
                    }
                };

            checkFlushHierarchy(
                sw, true, timeFlusher,
                new Runnable() {
                    @Override
					public void run() {
                        // Check that <Level>.Members uses cache.
                        MdxAssert.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
"Count([Time].[Month].Members)").returns(
"24");
                    }
                });
            checkFlushHierarchy(
                sw, true, timeFlusher,
                new Runnable() {
                    @Override
					public void run() {
                        // Check that <Level>.Members uses cache.
                        MdxAssert.assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
"[Time].[1997].[Q2].Children").returns(
"[Time].[1997].[Q2].[4]\n"
                            + "[Time].[1997].[Q2].[5]\n"
                            + "[Time].[1997].[Q2].[6]");
                    }
                });
        } finally {
            //Util.removeAppender(appender, logger);
        }
    }

    /**
     * Runs the same command ({@code foo(testContext, k)}) three times. Between
     * the 2nd and the 3rd, flushes the cache, and makes sure that the 3rd time
     * causes SQL to be executed.
     *
     * @param writer Writer, written into each time a SQL statement is executed
     * @param affected Whether the cache flush affects the command
     * @param flusher Functor that performs cache flushing action to be tested
     * @param command Command to execute that requires cache contents
     */
    private void checkFlushHierarchy(
        StringWriter writer,
        boolean affected,
        Runnable flusher,
        Runnable command)
    {
        // Run command for first time.
        command.run();

        // Now cache is primed, running the command for second time should not
        // require any additional SQL. (There is a small chance that GC will
        // kick in and we'll lose the cache, but we've never seen that happen
        // in the wild.)
        int length1 = writer.getBuffer().length();
        command.run();
        final String since1 = writer.getBuffer().substring(length1);
        assertEquals("", since1);
        flusher.run();

        // Now cache has been flushed, it should be impossible to execute the
        // command without running additional SQL.
        int length2 = writer.getBuffer().length();
        command.run();
        final String since2 = writer.getBuffer().substring(length2);
        if (affected) {
            assertNotSame("", since2);
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
