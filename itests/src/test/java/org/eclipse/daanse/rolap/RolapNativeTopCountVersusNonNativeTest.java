/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2015-2017 Hitachi Vantara and others
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


import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.CUSTOM_COUNT_MEASURE_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_HIDDEN_WHEN_NON_EMPTY_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_SHOWN_COUNTRIES_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_SHOWN_NOT_MORE_THAN_EXIST_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.EMPTY_CELLS_ARE_SHOWN_STATES_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.IMPLICIT_COUNT_MEASURE_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.NON_EMPTY_IS_NOT_IGNORED_WHEN_TWO_PARAMS_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.RESULTS_ARE_SHOWN_NOT_MORE_THAN_EXIST_2_PARAMS_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.ROLE_RESTRICTION_WORKS_DF_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.ROLE_RESTRICTION_WORKS_DF_ROLE_NAME;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.ROLE_RESTRICTION_WORKS_WA_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.ROLE_RESTRICTION_WORKS_WA_ROLE_NAME;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.SUM_MEASURE_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_CITIES_QUERY;
import static org.eclipse.daanse.rolap.RolapNativeTopCountTestCases.TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_STATES_QUERY;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.NativeVerify;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.CellKeyTest.FoodmartData;

/**
 * @author Andrey Khayrutdinov
 */
@RolapContextTest(FoodmartTestInstance.class)
class RolapNativeTopCountVersusNonNativeTest extends BatchTestCase {



    @BeforeEach
    public void beforeEach() {

    }

    @AfterEach
    public void afterEach() {
    }

    private void assertResultsAreEqual(
        Connection connection,
        String testCase,
        String query)
    {
        String message = String.format(
            "[%s]: native and non-native results of the query differ. The query:\n\t\t%s",
            testCase,
            query);
        NativeVerify.assertSameNativeAndNot(connection.getContext(), query, message);
    }

    @Test
    void testTopCount_ImplicitCountMeasure(Context<?> context) throws Exception {
        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Implicit Count Measure", IMPLICIT_COUNT_MEASURE_QUERY);
    }

    @Test
    void testTopCount_SumMeasure(Context<?> context) throws Exception {
        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Sum Measure", SUM_MEASURE_QUERY);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CustomCountMeasureCubeName.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testTopCount_CountMeasure(Context<?> context) throws Exception {

        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Custom Count Measure", CUSTOM_COUNT_MEASURE_QUERY);
    }

    @Test
    void testEmptyCellsAreShown_Countries(Context<?> context) throws Exception {
        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Empty Cells Are Shown - Countries",
            EMPTY_CELLS_ARE_SHOWN_COUNTRIES_QUERY);
    }

    @Test
    void testEmptyCellsAreShown_States(Context<?> context) throws Exception {
        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Empty Cells Are Shown - States",
            EMPTY_CELLS_ARE_SHOWN_STATES_QUERY);
    }

    @Test
    void testEmptyCellsAreShown_ButNoMoreThanReallyExist(Context<?> context) {
        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Empty Cells Are Shown - But no more than really exist",
            EMPTY_CELLS_ARE_SHOWN_NOT_MORE_THAN_EXIST_QUERY);
    }

    @Test
    void testEmptyCellsAreHidden_WhenNonEmptyIsDeclaredExplicitly(Context<?> context) {
        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Empty Cells Are Hidden - When NON EMPTY is declared explicitly",
            EMPTY_CELLS_ARE_HIDDEN_WHEN_NON_EMPTY_QUERY);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.RoleRestrictionWorksWaRoleDef.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRoleRestrictionWorks_ForRowWithData(@Roles(ROLE_RESTRICTION_WORKS_WA_ROLE_NAME) Connection connection) {
        assertResultsAreEqual(connection,
            "Role restriction works - For WA state",
            ROLE_RESTRICTION_WORKS_WA_QUERY);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.RoleRestrictionWorksDfRoleDef.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRoleRestrictionWorks_ForRowWithOutData(@Roles(ROLE_RESTRICTION_WORKS_DF_ROLE_NAME) Connection connection) {
        assertResultsAreEqual(connection,
            "Role restriction works - For DF state",
            ROLE_RESTRICTION_WORKS_DF_QUERY);
    }

    @Test
    void testMimicsHeadWhenTwoParams_States(Context<?> context) {
        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Two Parameters - States",
            TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_STATES_QUERY);
    }

    @Test
    void testMimicsHeadWhenTwoParams_Cities(Context<?> context) {
        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Two Parameters - Cities",
            TOPCOUNT_MIMICS_HEAD_WHEN_TWO_PARAMS_CITIES_QUERY);
    }

    @Test
    void testMimicsHeadWhenTwoParams_ShowsNotMoreThanExist(Context<?> context) {
        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Two Parameters - Shows not more than really exist",
            RESULTS_ARE_SHOWN_NOT_MORE_THAN_EXIST_2_PARAMS_QUERY);
    }

    @Test
    void testMimicsHeadWhenTwoParams_DoesNotIgnoreNonEmpty(Context<?> context) {
        assertResultsAreEqual(context.getConnectionWithDefaultRole(),
            "Two Parameters - Does not ignore NON EMPTY",
            NON_EMPTY_IS_NOT_IGNORED_WHEN_TWO_PARAMS_QUERY);
    }
}
