/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2006-2017 Hitachi Vantara and others
 * All Rights Reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.Parameter;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.element.MatchType;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.query.NameSegment;
import org.eclipse.daanse.olap.api.query.component.AxisOrdinal;
import org.eclipse.daanse.olap.api.query.component.CellProperty;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.api.query.component.Id;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.query.component.QueryAxis;
import org.eclipse.daanse.olap.api.query.component.QueryComponent;
import org.eclipse.daanse.olap.api.query.component.Subcube;
import org.eclipse.daanse.olap.api.result.SubtotalVisibility;
import org.eclipse.daanse.olap.common.IdBatchResolver;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.base.QueryProviderImpl;
import org.eclipse.daanse.olap.query.component.QueryAxisImpl;
import org.eclipse.daanse.olap.query.component.QueryImpl;
import org.eclipse.daanse.rolap.common.connection.InternalRolapConnection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.FlushSchemaCacheModifier;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@RolapContextTest(FoodmartTestInstance.class)
class IdBatchResolverTest  {

	@Mock
     Query query;

    @Captor
     ArgumentCaptor<List<NameSegment>> childNames;

    @Captor
     ArgumentCaptor<Member> parentMember;

    @Captor
     ArgumentCaptor<MatchType> matchType;



    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

    }
    @AfterEach
    public void  afterEach(){
    }
	@Test
    void testSimpleEnum(Context<?> context) {
        assertContains(
            "Resolved map omitted one or more members",
            batchResolve(context,
                "SELECT "
                + "{[Product].[Product].[Food].[Dairy],"
                + "[Product].[Product].[Food].[Deli],"
                + "[Product].[Product].[Food].[Eggs],"
                + "[Product].[Product].[Food].[Produce],"
                + "[Product].[Product].[Food].[Starchy Foods]}"
                + "on 0 FROM SALES"),
            list(
                "[Product].[Product].[Food].[Dairy]",
                "[Product].[Product].[Food].[Deli]",
                "[Product].[Product].[Food].[Eggs]",
                "[Product].[Product].[Food].[Produce]",
                "[Product].[Product].[Food].[Starchy Foods]"));

        // verify lookupMemberChildrenByNames is called as expected with
        // batched children's names.
        verify(
            query.getCatalogReader(true), times(2))
            .lookupMemberChildrenByNames(
                parentMember.capture(),
                childNames.capture(),
                matchType.capture());

        assertEquals(
            "[Product].[Product].[All Products]",
            parentMember.getAllValues().get(0).getUniqueName());
        assertTrue(childNames.getAllValues().get(0).size() == 1);
        assertEquals(
            "Food",
            childNames.getAllValues().get(0).get(0).getName());

        assertEquals(
            "[Product].[Product].[Food]",
            parentMember.getAllValues().get(1).getUniqueName());
        assertTrue(childNames.getAllValues().get(1).size() == 5);

        assertEquals(
            "[[Dairy], [Deli], [Eggs], [Produce], [Starchy Foods]]",
            sortedNames(childNames.getAllValues().get(1)));
    }
	@Test
    void testCalcMemsNotResolved(Context<?> context) {
        assertFalse(
            batchResolve(context,
                "with member time.time.foo as '1' member time.time.bar as '2' "
                + " select "
                + " {[Time].[Time].[foo], [Time].[Time].[bar], "
                + "  [Time].[Time].[1997],"
                + "  [Time].[Time].[1997].[Q1], [Time].[Time].[1997].[Q2]} "
                + " on 0 from sales ")
                .removeAll(list("[Time].[Time].[foo]", "[Time].[Time].[bar]")),
                "Resolved map should not contain calc members");
        // .removeAll will only return true if the set has changed, i.e. if
        // one ore more of the members were present.
    }
	@Test
    void testLevelReferenceHandled(Context<?> context) {
        // make sure ["Week", 1997] don't get batched as children of
        // [Time.Weekly].[All]
        batchResolve(context,
            "with member Gender.levelRef as "
            + "'Sum(Descendants([Time].[Weekly].CurrentMember, [Time].[Weekly].Week))' "
            + "select Gender.levelRef on 0 from sales where [Time].[Weekly].[1997]");
        verify(
            query.getCatalogReader(true), times(1))
            .lookupMemberChildrenByNames(
                parentMember.capture(),
                childNames.capture(),
                matchType.capture());
        assertEquals(
            "[Time].[Weekly].[All Weeklys]",
            parentMember.getAllValues().get(0).getUniqueName());
        assertEquals(
            "[[1997]]",
            sortedNames(childNames.getAllValues().get(0)));
    }

	@Test
    void testPhysMemsResolvedWhenCalcsMixedIn(Context<?> context) {
        assertContains(
            "Resolved map omitted one or more members",
            batchResolve(context,
                "with member time.time.foo as '1' member time.time.bar as '2' "
                + " select "
                + " {[Time].[Time].[foo], [Time].[Time].[bar], "
                + "  [Time].[Time].[1997],"
                + "  [Time].[Time].[1997].[Q1], [Time].[Time].[1997].[Q2]} "
                + " on 0 from sales "),
            list(
                "[Time].[Time].[1997]",
                "[Time].[Time].[1997].[Q1]",
                "[Time].[Time].[1997].[Q2]"));
        verify(
            query.getCatalogReader(true), times(1))
            .lookupMemberChildrenByNames(
                parentMember.capture(),
                childNames.capture(),
                matchType.capture());
        assertEquals(
            "[Time].[Time].[1997]",
            parentMember.getAllValues().get(0).getUniqueName());
        assertTrue(childNames.getAllValues().get(0).size() == 2);
        assertEquals(
            "[[Q1], [Q2]]",
            sortedNames(childNames.getAllValues().get(0)));
    }

	@Test
    void testAnalyzerFilterMdx(Context<?> context) {
        assertContains(
            "Resolved map omitted one or more members",
            batchResolve(context,
                "WITH\n"
                + "SET [*NATIVE_CJ_SET] AS 'NONEMPTYCROSSJOIN([*BASE_MEMBERS__Promotions_],[*BASE_MEMBERS__Store_])'\n"
                + "SET [*BASE_MEMBERS__Store_] AS '{[Store].[Store].[USA].[WA].[Bellingham],[Store].[Store].[USA].[CA].[Beverly Hills],[Store].[Store].[USA].[WA].[Bremerton],[Store].[Store].[USA].[CA].[Los Angeles]}'\n"
                + "SET [*SORTED_COL_AXIS] AS 'ORDER([*CJ_COL_AXIS],[Promotions].[Promotions].CURRENTMEMBER.ORDERKEY,BASC)'\n"
                + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
                + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Store].[Store].CURRENTMEMBER)})'\n"
                + "SET [*BASE_MEMBERS__Promotions_] AS '{[Promotions].[Promotions].[Bag Stuffers],[Promotions].[Promotions].[Best Savings],[Promotions].[Promotions].[Big Promo],[Promotions].[Promotions].[Big Time Discounts],[Promotions].[Promotions].[Big Time Savings],[Promotions].[Promotions].[Bye Bye Baby]}'\n"
                + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Store].[Store].CURRENTMEMBER.ORDERKEY,BASC,ANCESTOR([Store].[Store].CURRENTMEMBER,[Store].[Store].[Store State]).ORDERKEY,BASC)'\n"
                + "SET [*CJ_COL_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Promotions].[Promotions].CURRENTMEMBER)})'\n"
                + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', SOLVE_ORDER=500\n"
                + "SELECT\n"
                + "CROSSJOIN([*SORTED_COL_AXIS],[*BASE_MEMBERS__Measures_]) ON COLUMNS\n"
                + ",NON EMPTY\n"
                + "[*SORTED_ROW_AXIS] ON ROWS\n"
                + "FROM [Sales]"),
            list(
                "[Store].[Store].[USA].[WA].[Bellingham]",
                "[Store].[Store].[USA].[CA].[Beverly Hills]",
                "[Store].[Store].[USA].[WA].[Bremerton]",
                "[Store].[Store].[USA].[CA].[Los Angeles]",
                "[Promotions].[Promotions].[Bag Stuffers]", "[Promotions].[Promotions].[Best Savings]",
                "[Promotions].[Promotions].[Big Promo]", "[Promotions].[Promotions].[Big Time Discounts]",
                "[Promotions].[Promotions].[Big Time Savings]",
                "[Promotions].[Promotions].[Bye Bye Baby]"));

        verify(
            query.getCatalogReader(true), times(5))
            .lookupMemberChildrenByNames(
                parentMember.capture(),
                childNames.capture(),
                matchType.capture());

        assertEquals(
            "[Promotions].[Promotions].[All Promotions]",
            parentMember.getAllValues().get(0).getUniqueName());
        assertTrue(childNames.getAllValues().get(0).size() == 6);
        assertEquals(
            "[[Bag Stuffers], [Best Savings], [Big Promo], "
            + "[Big Time Discounts], [Big Time Savings], [Bye Bye Baby]]",
            sortedNames(childNames.getAllValues().get(0)));

        assertEquals(
            "[Store].[Store].[USA].[CA]",
            parentMember.getAllValues().get(3).getUniqueName());
        assertTrue(childNames.getAllValues().get(3).size() == 2);
        assertEquals(
            "[[Beverly Hills], [Los Angeles]]",
            sortedNames(childNames.getAllValues().get(3)));
    }
	@Test
    void testSetWithNullMember(Context<?> context) {
        assertContains(
            "Resolved map omitted one or more members",
            batchResolve(context,
                "WITH\n"
                + "SET [*NATIVE_CJ_SET] AS 'FILTER([*BASE_MEMBERS__Store Size in SQFT_], NOT ISEMPTY ([Measures].[Unit Sales]))'\n"
                + "SET [*BASE_MEMBERS__Store Size in SQFT_] AS '{[Store Size in SQFT].[Store Size in SQFT].[#null],[Store Size in SQFT].[Store Size in SQFT].[20319],[Store Size in SQFT].[Store Size in SQFT].[21215],[Store Size in SQFT].[Store Size in SQFT].[22478],[Store Size in SQFT].[Store Size in SQFT].[23598]}'\n"
                + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
                + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Store Size in SQFT].CURRENTMEMBER)})'\n"
                + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', SOLVE_ORDER=500\n"
                + "SELECT\n"
                + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
                + "FROM [Sales]\n"
                + "WHERE ([*CJ_SLICER_AXIS])"),
            list(
                "[Store Size in SQFT].[Store Size in SQFT].[#null]",
                "[Store Size in SQFT].[Store Size in SQFT].[20319]",
                "[Store Size in SQFT].[Store Size in SQFT].[21215]",
                "[Store Size in SQFT].[Store Size in SQFT].[22478]",
                "[Store Size in SQFT].[Store Size in SQFT].[23598]"));

        verify(
            query.getCatalogReader(true), times(1))
            .lookupMemberChildrenByNames(
                parentMember.capture(),
                childNames.capture(),
                matchType.capture());

        assertEquals(
            "[Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs]",
            parentMember.getAllValues().get(0).getUniqueName());
        assertTrue(childNames.getAllValues().get(0).size() == 5);
        assertEquals(
            "[[#null], [20319], [21215], [22478], [23598]]",
            sortedNames(childNames.getAllValues().get(0)));
    }
	@Test
    void testMultiHierarchySSAS(Context<?> context) {
        assertContains(
            "Resolved map omitted one or more members",
            batchResolve(context,
                "WITH\n"
                + "SET [*NATIVE_CJ_SET] AS 'FILTER([*BASE_MEMBERS__Time.Weekly_], NOT ISEMPTY ([Measures].[Unit Sales]))'\n"
                + "SET [*BASE_MEMBERS__Time.Weekly_] AS '{[Time].[Weekly].[1997].[4],[Time].[Weekly].[1997].[5],[Time].[Weekly].[1997].[6]}'\n"
                + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
                + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Time].[Weekly].CURRENTMEMBER)})'\n"
                + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', SOLVE_ORDER=500\n"
                + "SELECT\n"
                + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
                + "FROM [Sales]\n"
                + "WHERE ([*CJ_SLICER_AXIS])"),
            list(
                "[Time].[Weekly].[1997].[4]",
                "[Time].[Weekly].[1997].[5]",
                "[Time].[Weekly].[1997].[6]"));

        verify(
            query.getCatalogReader(true), times(2))
            .lookupMemberChildrenByNames(
                parentMember.capture(),
                childNames.capture(),
                matchType.capture());
        assertEquals(
            "[Time].[Weekly].[All Weeklys]",
            parentMember.getAllValues().get(0).getUniqueName());
        assertTrue(
            childNames.getAllValues().get(0).size() == 1);
        assertEquals(
            "1997",
            childNames.getAllValues().get(0).get(0).getName());
        assertEquals(
            "[[4], [5], [6]]",
            sortedNames(childNames.getAllValues().get(1)));
    }
	@Test
    void testParentChild(Context<?> context) {
        // P-C resolution will not result in consolidated SQL, but it should
        // still correctly identify children and attempt to resolve them
        // together.
        assertContains(
            "Resolved map omitted one or more members",
            batchResolve(context,
                "WITH\n"
                + "SET [*NATIVE_CJ_SET] AS 'FILTER([*BASE_MEMBERS__Employees_], NOT ISEMPTY ([Measures].[Number of Employees]))'\n"
                + "SET [*BASE_MEMBERS__Employees_] AS '{[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply],[Employees].[Employees].[Sheri Nowmer].[Michael Spence]}'\n"
                + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
                + "SET [*CJ_SLICER_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Employees].CURRENTMEMBER)})'\n"
                + "MEMBER [Measures].[*FORMATTED_MEASURE_0] AS '[Measures].[Number of Employees]', FORMAT_STRING = '#,#', SOLVE_ORDER=500\n"
                + "SELECT\n"
                + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
                + "FROM [HR]\n"
                + "WHERE ([*CJ_SLICER_AXIS])"),
                list(
                    "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply]",
                    "[Employees].[Employees].[Sheri Nowmer].[Michael Spence]"));

        verify(
            query.getCatalogReader(true), times(2))
            .lookupMemberChildrenByNames(
                parentMember.capture(),
                childNames.capture(),
                matchType.capture());
        assertEquals(
            "[Employees].[Employees].[Sheri Nowmer]",
            parentMember.getAllValues().get(1).getUniqueName());
        assertTrue(childNames.getAllValues().get(1).size() == 2);
    }


    private void assertContains(
        String msg, Collection<String> strings, Collection<String> list)
    {
        if (!strings.containsAll(list)) {
            List<String> copy = new ArrayList<>(list);
            copy.removeAll(strings);
            fail(
                String.format(
                    "%s\nMissing: %s", msg,
                Arrays.toString(copy.toArray())));
        }
    }

    public Set<String> batchResolve(Context<?> context,String mdx) {
        IdBatchResolver batchResolver = makeTestBatchResolver(context,mdx);
        Map<QueryComponent, QueryComponent> resolvedIdents = batchResolver.resolve();
        Set<String> resolvedNames = getResolvedNames(resolvedIdents);
        return resolvedNames;
    }

    private String sortedNames(List<NameSegment> items) {
        return Arrays.toString(items.stream()
            .sorted((o1, o2)->o1.getName().compareTo(o2.getName())).toArray());
    }

    private Collection<String> list(String... items) {
        return Arrays.asList(items);
    }

    private Set<String> getResolvedNames(
        Map<QueryComponent, QueryComponent> resolvedIdents)
    {
		return resolvedIdents.keySet().stream().map(Object::toString).collect(Collectors.toSet());

    }

    public IdBatchResolver makeTestBatchResolver(Context<?> context,String mdx) {
    	FlushSchemaCacheModifier.flushSchemaCache(context.getConnectionWithDefaultRole());

        InternalRolapConnection conn = (InternalRolapConnection) spy(
        		context.getConnectionWithDefaultRole());
        when(conn.getQueryProvider()).thenReturn(new QueryProviderWrapper());

        query = conn.parseQuery(mdx);
        // Note: ExecutionContext setup removed. If tests fail, wrap test execution in:
        // ExecutionImpl execution = new ExecutionImpl(query.getStatement(), Optional.of(Duration.ofMillis(Integer.MAX_VALUE)));
        // ExecutionContext.where(execution.asContext().createChild("batchResolveTest", "batchResolveTest", null, 0), () -> { ... });

        return new IdBatchResolver(query);
    }

    private class QueryTestWrapper extends QueryImpl {
        private CatalogReader spyReader;

        public QueryTestWrapper(
            Statement statement,
            Formula[] formulas,
            QueryAxis[] axes,
            String cube,
            QueryAxisImpl slicerAxis,
            CellProperty[] cellProps,
            boolean strictValidation)
        {
            super(
                statement,
                Util.lookupCube(statement.getCatalogReader(), cube, true),
                formulas,
                axes,
                slicerAxis,
                cellProps,
                new Parameter[0],
                strictValidation);
        }

        @Override
        public void resolve() {
            // for testing purposes we want to defer resolution till after
            //  Query init (resolve is called w/in constructor).
            // We do still need formulas to be created, though.
            if (getFormulas() != null) {
                for (Formula formula : getFormulas()) {
                    formula.createElement(this);
                }
            }
        }

        @Override
        public synchronized CatalogReader getCatalogReader(
            boolean accessControlled)
        {
            if (spyReader == null) {
            	spyReader=spy( new SpyCatalogReader(super.getCatalogReader(accessControlled)));
            }
            return spyReader;
        }
    }

    class QueryProviderWrapper extends QueryProviderImpl {
        @Override
        public Query createQuery(Statement statement,
                                 Formula[] formula,
                                 QueryAxis[] axes,
                                 Subcube subcube,
                                 QueryAxis slicerAxis,
                                 CellProperty[] cellProps,
                                 boolean strictValidation) {

            final QueryAxisImpl slicerAxisW =
                slicerAxis == null || slicerAxis.getSet() == null
                    ? null
                    : new QueryAxisImpl(
                    false, slicerAxis.getSet(), AxisOrdinal.StandardAxisOrdinal.SLICER,
                    SubtotalVisibility.Undefined, new Id[0]);
            return new QueryTestWrapper(
                statement, formula, axes, subcube.getCubeName(), slicerAxisW, cellProps,
                strictValidation);
        }
    }
}
