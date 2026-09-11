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
 *
 */
package org.eclipse.daanse.rolap.poc;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.junit.jupiter.api.Test;

@RolapContextTest(SchoolTestInstance.class)
public class FirstTest1 {


    @Test
    void capturesMemberChildrenSqlFromSchoolCatalog(Connection conn) throws Exception {
        String mdx =
                "SELECT {[Measures].[Anzahl Schulen]} ON COLUMNS FROM [Schulen in Jena (Institutionen)]";
        List<String> captured = new CopyOnWriteArrayList<>();
        RolapUtil.setHook(conn.getContext(), captured::add);
        try {
            assertNotNull(conn.execute(conn.parseQuery(mdx)));
        } finally {
            RolapUtil.setHook(conn.getContext(), null);
        }

        assertFalse(captured.isEmpty(),
                "expected at least one member SQL statement to be captured via RolapUtil hook");
        // The captured SQL is the golden baseline for the future builder-based member mapper.
        captured.forEach(sql -> System.out.println("[captured member SQL] " + sql));
    }

    @Test
    @RolapContextTest(SchoolTestInstance.class)
    void capturesMemberChildrenSqlFromSchoolCatalogWithAnnotation(Connection conn) throws Exception {
        String mdx =
                "SELECT {[Measures].[Anzahl Schulen]} ON COLUMNS FROM [Schulen in Jena (Institutionen)]";
        //conn.getCacheControl(null).flushSchemaCache();
        List<String> captured = new CopyOnWriteArrayList<>();
        RolapUtil.setHook(conn.getContext(), captured::add);
        try {
            assertNotNull(conn.execute(conn.parseQuery(mdx)));
        } finally {
            RolapUtil.setHook(conn.getContext(), null);
        }

        assertFalse(captured.isEmpty(),
                "expected at least one member SQL statement to be captured via RolapUtil hook");
        // The captured SQL is the golden baseline for the future builder-based member mapper.
        captured.forEach(sql -> System.out.println("[captured member SQL] " + sql));
    }

    @Test
    @RolapContextTest(SchoolTestInstance.class)
    void capturesMemberChildrenSqlFromSchoolCatalogWithCache(Connection conn) throws Exception {
        String mdx =
                "SELECT {[Measures].[Anzahl Schulen]} ON COLUMNS FROM [Schulen in Jena (Institutionen)]";
        //conn.getCacheControl(null).flushSchemaCache();
        List<String> captured = new CopyOnWriteArrayList<>();
        RolapUtil.setHook(conn.getContext(), captured::add);
        try {
            assertNotNull(conn.execute(conn.parseQuery(mdx)));
        } finally {
            RolapUtil.setHook(conn.getContext(), null);
        }

        assertFalse(captured.isEmpty(),
                "expected at least one member SQL statement to be captured via RolapUtil hook");
        // The captured SQL is the golden baseline for the future builder-based member mapper.
        captured.forEach(sql -> System.out.println("[captured member SQL] " + sql));

        captured = new CopyOnWriteArrayList<>();
        RolapUtil.setHook(conn.getContext(), captured::add);
        try {
            assertNotNull(conn.execute(conn.parseQuery(mdx)));
        } finally {
            RolapUtil.setHook(conn.getContext(), null);
        }

        assertTrue(captured.isEmpty(),
                "expected that data take from cache");
    }

    @Test
    @RolapContextTest(SchoolTestInstance.class)
    void capturesMemberChildrenSqlFromSchoolCatalogWithCache1(Connection conn) throws Exception {
        String mdx =
                "SELECT {[Measures].[Anzahl Schulen]} ON COLUMNS FROM [Schulen in Jena (Institutionen)]";
        //conn.getCacheControl(null).flushSchemaCache();
        List<String> captured = new CopyOnWriteArrayList<>();

        RolapUtil.setHook(conn.getContext(), captured::add);
        try {
            assertNotNull(executeQuery(conn, mdx));
        } finally {
            RolapUtil.setHook(conn.getContext(), null);
        }

        assertFalse(captured.isEmpty(),
                "expected at least one member SQL statement to be captured via RolapUtil hook");
        assertTrue(captured.stream().anyMatch(s -> s.equals("select \"schul_jahr\".\"id\" as \"c0\", \"schul_jahr\".\"order\" as \"c1\", \"schul_jahr\".\"schul_jahr\" as \"c2\" from \"schul_jahr\" as \"schul_jahr\" group by \"schul_jahr\".\"id\", \"schul_jahr\".\"order\", \"schul_jahr\".\"schul_jahr\" order by CASE WHEN \"schul_jahr\".\"order\" IS NULL THEN 1 ELSE 0 END, \"schul_jahr\".\"order\" ASC, CASE WHEN \"schul_jahr\".\"id\" IS NULL THEN 1 ELSE 0 END, \"schul_jahr\".\"id\" ASC")));
        assertTrue(captured.stream().anyMatch(s -> s.equals("select COUNT(distinct \"id\") as \"c0\" from \"schul_jahr\" as \"schul_jahr\"")));
    }

    @Test
    @RolapContextTest(FoodmartTestInstance.class)
    void capturesMemberChildrenSqlFromFoodmart1(@Roles("Administrator") Connection conn) throws Exception {
        String mdx = """
            select {CrossJoin( [Customers].[Canada].Children, CrossJoin( [Time].[1997].firstChild, CrossJoin ([Education Level].lastChild, CrossJoin ([Yearly Income].lastChild, { (Gender.F, [Marital Status].M ) })) ) )} on columns from Sales
        """;
        Result result = executeQuery(conn, mdx);
        assertNotNull(result);
        assertEquals("[Customers].[Customers].[Canada].[BC]", ResultProvider.getAxis(result, 0, 0, 0 ));
        assertEquals("[Time].[Time].[1997].[Q1]", ResultProvider.getAxis(result, 0, 0, 1));
        assertEquals("[Education Level].[Education Level].[Partial High School]", ResultProvider.getAxis(result, 0, 0, 2));
        assertEquals("[Yearly Income].[Yearly Income].[$90K - $110K]", ResultProvider.getAxis(result, 0, 0, 3));
        assertEquals("[Gender].[Gender].[F]", ResultProvider.getAxis(result, 0, 0, 4));
        assertEquals("[Marital Status].[Marital Status].[M]", ResultProvider.getAxis(result, 0, 0, 5));

        mdx = """
            select
                {[Product].[Food]} on columns,
                {[Measures].[Unit Sales]} on rows
                from [Sales]
        """;
        result = executeQuery(conn, mdx);
        assertNotNull(result);
        assertEquals("[Product].[Product].[Food]", ResultProvider.getAxis(result, 0, 0, 0 ));
        assertEquals("[Measures].[Unit Sales]", ResultProvider.getAxis(result, 1, 0, 0));
        assertEquals("191,940", ResultProvider.getCell(result, 0, 0));


        mdx = """
            SELECT [Measures].[Fact Count] ON COLUMNS,
                TOPCOUNT([Store Type].[All Store Types].Children, 3, [Measures].[Fact Count]) ON ROWS
                FROM [Store]
        """;
        result = executeQuery(conn, mdx);
        assertNotNull(result);
        assertEquals("[Measures].[Fact Count]", ResultProvider.getAxis(result, 0, 0, 0 ));

        assertEquals("[Store Type].[Store Type].[Supermarket]", ResultProvider.getAxis(result, 1, 0, 0));
        assertEquals("[Store Type].[Store Type].[Deluxe Supermarket]", ResultProvider.getAxis(result, 1, 1, 0));
        assertEquals("[Store Type].[Store Type].[Mid-Size Grocery]", ResultProvider.getAxis(result, 1, 2, 0));

        assertEquals("8", ResultProvider.getCell(result, 0, 0));
        assertEquals("6", ResultProvider.getCell(result, 0, 1));
        assertEquals("4", ResultProvider.getCell(result, 0, 2));

    }


    @Test
    @RolapContextTest(FoodmartTestInstance.class)
    void capturesMemberChildrenSqlFromFoodmart(@Roles("Administrator") Connection conn) throws Exception {
        String mdx =
                "SELECT {[Measures].[Unit Sales]} ON COLUMNS FROM [Sales]";
        //conn.getCacheControl(null).flushSchemaCache();
        List<String> captured = new CopyOnWriteArrayList<>();
        RolapUtil.setHook(conn.getContext(), captured::add);
        try {
            assertNotNull(conn.execute(conn.parseQuery(mdx)));
        } finally {
            RolapUtil.setHook(conn.getContext(), null);
        }

        assertFalse(captured.isEmpty(),
                "expected at least one member SQL statement to be captured via RolapUtil hook");
        // The captured SQL is the golden baseline for the future builder-based member mapper.
        captured.forEach(sql -> System.out.println("[captured member SQL] " + sql));
    }

}
