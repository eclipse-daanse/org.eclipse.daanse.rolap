/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2015-2017 Hitachi Vantara and others
// All Rights Reserved.
*/
package org.eclipse.daanse.rolap;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.CUSTOM_COUNT_MEASURE_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.CUSTOM_COUNT_MEASURE_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_HIDDEN_WHEN_NON_EMPTY_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_HIDDEN_WHEN_NON_EMPTY_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_SHOWN_COUNTRIES_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_SHOWN_COUNTRIES_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_SHOWN_NOT_MORE_THAN_EXIST_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_SHOWN_NOT_MORE_THAN_EXIST_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_SHOWN_STATES_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_SHOWN_STATES_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.IMPLICIT_COUNT_MEASURE_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.IMPLICIT_COUNT_MEASURE_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.NON_EMPTY_IS_NOT_IGNORED_WHEN_TWO_PARAMS_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.NON_EMPTY_IS_NOT_IGNORED_WHEN_TWO_PARAMS_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.RESULTS_ARE_SHOWN_NOT_MORE_THAN_EXIST_2_PARAMS_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.RESULTS_ARE_SHOWN_NOT_MORE_THAN_EXIST_2_PARAMS_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.ROLE_RESTRICTION_WORKS_DF_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.ROLE_RESTRICTION_WORKS_DF_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.ROLE_RESTRICTION_WORKS_DF_ROLE_NAME;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.ROLE_RESTRICTION_WORKS_WA_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.ROLE_RESTRICTION_WORKS_WA_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.ROLE_RESTRICTION_WORKS_WA_ROLE_NAME;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.SUM_MEASURE_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.SUM_MEASURE_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_CITIES_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_CITIES_RESULT;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_STATES_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_STATES_RESULT;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.junit.jupiter.api.Test;

/**
 * @author Andrey Khayrutdinov
 * @see RolapNativeTopCountTestCases
 */
@RolapContextTest(FoodmartTestInstance.class)
class RolapNativeTopCountTest extends BatchTestCase {

    @Test
    void testTopCount_ImplicitCountMeasure(Connection connection) throws Exception {
        assertThatQuery(connection,
            IMPLICIT_COUNT_MEASURE_QUERY).returnsGrid(IMPLICIT_COUNT_MEASURE_RESULT);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CustomCountMeasureCubeName.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testTopCount_CountMeasure(Connection connection) throws Exception {
        assertThatQuery(connection,
            CUSTOM_COUNT_MEASURE_QUERY).returnsGrid(CUSTOM_COUNT_MEASURE_RESULT);
    }

    @Test
    void testTopCount_SumMeasure(Connection connection) throws Exception {
        assertThatQuery(connection, SUM_MEASURE_QUERY).returnsGrid(SUM_MEASURE_RESULT);
    }

    @Test
    void testEmptyCellsAreShown_Countries(Connection connection) {
        assertThatQuery(connection,
            EMPTY_CELLS_ARE_SHOWN_COUNTRIES_QUERY).returnsGrid(
            EMPTY_CELLS_ARE_SHOWN_COUNTRIES_RESULT);
    }

    @Test
    void testEmptyCellsAreShown_States(Connection connection) {
        assertThatQuery(connection,
            EMPTY_CELLS_ARE_SHOWN_STATES_QUERY).returnsGrid(
            EMPTY_CELLS_ARE_SHOWN_STATES_RESULT);
    }

    @Test
    void testEmptyCellsAreShown_ButNoMoreThanReallyExist(Connection connection) {
        assertThatQuery(connection,
            EMPTY_CELLS_ARE_SHOWN_NOT_MORE_THAN_EXIST_QUERY).returnsGrid(
            EMPTY_CELLS_ARE_SHOWN_NOT_MORE_THAN_EXIST_RESULT);
    }

    @Test
    void testEmptyCellsAreHidden_WhenNonEmptyIsDeclaredExplicitly(Connection connection) {
        assertThatQuery(connection,
            EMPTY_CELLS_ARE_HIDDEN_WHEN_NON_EMPTY_QUERY).returnsGrid(
            EMPTY_CELLS_ARE_HIDDEN_WHEN_NON_EMPTY_RESULT);
    }


    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.RoleRestrictionWorksWaRoleDef.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRoleRestrictionWorks_ForRowWithData(
        @Roles(ROLE_RESTRICTION_WORKS_WA_ROLE_NAME) Connection connection) throws Exception
    {
        assertThatQuery(connection,
            ROLE_RESTRICTION_WORKS_WA_QUERY).returnsGrid(
            ROLE_RESTRICTION_WORKS_WA_RESULT);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.RoleRestrictionWorksDfRoleDef.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRoleRestrictionWorks_ForRowWithOutData(
        @Roles(ROLE_RESTRICTION_WORKS_DF_ROLE_NAME) Connection connection) throws Exception
    {
        assertThatQuery(connection,
            ROLE_RESTRICTION_WORKS_DF_QUERY).returnsGrid(
            ROLE_RESTRICTION_WORKS_DF_RESULT);
    }

    @Test
    void testMimicsHeadWhenTwoParams_States(Connection connection) {
        assertThatQuery(connection,
            TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_STATES_QUERY).returnsGrid(
            TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_STATES_RESULT);
    }

    @Test
    void testMimicsHeadWhenTwoParams_Cities(Connection connection) {
        assertThatQuery(connection,
            TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_CITIES_QUERY).returnsGrid(
            TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_CITIES_RESULT);
    }

    @Test
    void testMimicsHeadWhenTwoParams_ShowsNotMoreThanExist(Connection connection) {
        assertThatQuery(connection,
            RESULTS_ARE_SHOWN_NOT_MORE_THAN_EXIST_2_PARAMS_QUERY).returnsGrid(
            RESULTS_ARE_SHOWN_NOT_MORE_THAN_EXIST_2_PARAMS_RESULT);
    }

    @Test
    void testMimicsHeadWhenTwoParams_DoesNotIgnoreNonEmpty(Connection connection) {
        assertThatQuery(connection,
            NON_EMPTY_IS_NOT_IGNORED_WHEN_TWO_PARAMS_QUERY).returnsGrid(
            NON_EMPTY_IS_NOT_IGNORED_WHEN_TWO_PARAMS_RESULT);
    }

    /** Named bridge onto the FoodMart CSVs (for the {@code data =} supplier form). */
    public static class FoodmartData implements org.eclipse.daanse.cwm.testkit.api.DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }
}
