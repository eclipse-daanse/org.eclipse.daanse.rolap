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

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.net.URL;
import java.util.Map;

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
 * Testcase for
 *
 *
 * @author <a>Richard M. Emberson</a>
 * @since Feb 21 2007
 */
@RolapContextTest(value = RolapResultTestInstance.class, dbScope = DbScope.PER_CLASS)
class RolapResultTest extends BatchTestCase {

    private static final String RESULTS_ALL =
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[D1].[D1].[a]}\n"
        + "{[D1].[D1].[b]}\n"
        + "{[D1].[D1].[c]}\n"
        + "Axis #2:\n"
        + "{[D2].[D2].[x]}\n"
        + "{[D2].[D2].[y]}\n"
        + "{[D2].[D2].[z]}\n"
        + "Row #0: 5\n"
        + "Row #0: \n"
        + "Row #0: \n"
        + "Row #1: \n"
        + "Row #1: 10\n"
        + "Row #1: \n"
        + "Row #2: \n"
        + "Row #2: \n"
        + "Row #2: 15\n";

    private static final String RESULTS =
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[D1].[D1].[a]}\n"
        + "{[D1].[D1].[b]}\n"
        + "{[D1].[D1].[c]}\n"
        + "Axis #2:\n"
        + "{[D2].[D2].[x]}\n"
        + "{[D2].[D2].[y]}\n"
        + "{[D2].[D2].[z]}\n"
        + "Row #0: 5\n"
        + "Row #0: \n"
        + "Row #0: \n"
        + "Row #1: \n"
        + "Row #1: 10\n"
        + "Row #1: \n"
        + "Row #2: \n"
        + "Row #2: \n"
        + "Row #2: 15\n";



    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testAll(Connection connection) throws Exception {
        String mdx =
            "select "
            + " filter({[D1].[a],[D1].[b],[D1].[c]}, "
            + "    [Measures].[Value] > 0) "
            + " ON COLUMNS, "
            + " {[D2].[x],[D2].[y],[D2].[z]} "
            + " ON ROWS "
            + "from FTAll";

        assertThatQuery(connection, mdx).returnsGrid(RESULTS_ALL);
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testD1(Connection connection) throws Exception {
        String mdx =
            "select "
            + " filter({[D1].[a],[D1].[b],[D1].[c]}, "
            + "    [Measures].[Value] > 0) "
            + " ON COLUMNS, "
            + " {[D2].[x],[D2].[y],[D2].[z]} "
            + " ON ROWS "
            + "from FT1";

        assertThatQuery(connection, mdx).returnsGrid(RESULTS);
        
        //Result result = executeQuery(mdx, connection);
        //String resultString = TestUtil.toString(result);
        //assertEquals(resultString, RESULTS);
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testD2(Connection connection) throws Exception {
        String mdx =
            "select "
            + " NON EMPTY filter({[D1].[a],[D1].[b],[D1].[c]}, "
            + "    [Measures].[Value] > 0) "
            + " ON COLUMNS, "
            + " {[D2].[x],[D2].[y],[D2].[z]} "
            + " ON ROWS "
            + "from FT2";

        assertThatQuery(connection, mdx).returnsGrid(RESULTS_ALL);
    }

    /**
     * This ought to give the same result as the above testD2() method.
     * In this case, the FT2Extra cube has a default measure with no
     * data (null) for all members. This default measure is used
     * in the evaluation even though there is an implicit use of the
     * measure [Measures].[Value].
     *
     * @throws Exception
     */
    @Disabled //disabled for CI build
    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void _testNullDefaultMeasure(Connection connection) throws Exception {
        String mdx =
            "select "
            + " NON EMPTY filter({[D1].[a],[D1].[b],[D1].[c]}, "
            + "    [Measures].[Value] > 0) "
            + " ON COLUMNS, "
            + " {[D2].[x],[D2].[y],[D2].[z]} "
            + " ON ROWS "
            + "from FT2Extra";

        //Result result = executeQuery(mdx, connection);
        //String resultString = TestUtil.toString(result);
        //assertTrue(resultString.equals(RESULTS));
        assertThatQuery(connection, mdx).returnsGrid(RESULTS_ALL);
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.RolapResultTestModifier.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNonAllPromotionMembers(Connection connection) {
        assertThatQuery(connection,
            "select {[Promotion2 Name].[Price Winners], [Promotion2 Name].[Sale Winners]} * {Tail([Time].[Year].Members,3)} ON COLUMNS, "
            + "NON EMPTY Crossjoin({[Store].CurrentMember.Children},  {[Store Type].[All Store Types].Children}) ON ROWS "
            + "from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Promotions2].[Promotions2].[Price Winners], [Time].[Time].[1997]}\n"
            + "{[Promotions2].[Promotions2].[Price Winners], [Time].[Time].[1998]}\n"
            + "{[Promotions2].[Promotions2].[Sale Winners], [Time].[Time].[1997]}\n"
            + "{[Promotions2].[Promotions2].[Sale Winners], [Time].[Time].[1998]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA], [Store Type].[Store Type].[Mid-Size Grocery]}\n"
            + "{[Store].[Store].[USA], [Store Type].[Store Type].[Small Grocery]}\n"
            + "{[Store].[Store].[USA], [Store Type].[Store Type].[Supermarket]}\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 444\n"
            + "Row #0: \n"
            + "Row #1: 23\n"
            + "Row #1: \n"
            + "Row #1: \n"
            + "Row #1: \n"
            + "Row #2: 1,271\n"
            + "Row #2: \n"
            + "Row #2: \n"
            + "Row #2: \n");
    }

    /** Named bridge onto the FoodMart CSVs (for the {@code data =} supplier form). */
    public static class FoodmartData implements org.eclipse.daanse.cwm.testkit.api.DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

}
