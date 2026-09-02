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


package org.eclipse.daanse.olap.fun;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Optional;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.exceptions.ResourceLimitExceededException;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.rolap.BatchTestCase;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
/**
 * Unit test for the {@code NativizeSet} function.
 *
 * @author jrand
 * @since Oct 14, 2009
 */
@RolapContextTest(FoodmartTestInstance.class)
@RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "true", type = Boolean.class)
class NativizeSetFunDefTest extends BatchTestCase {

    @BeforeEach
    public void beforeEach() {


        // ENABLE_NON_EMPTY_ON_ALL_AXIS is set via the class-level @RolapConfig
        // below; @BeforeEach cannot do it, the Context only arrives as a
        // parameter of the test method.
        // SSAS-compatible naming causes <dimension>.<level>.members to be
        // interpreted as <dimension>.<hierarchy>.members, and that happens a
        // lot in this test. There is little to be gained by having this test
        // run for both values. When SSAS-compatible naming is the standard, we
        // should upgrade all the MDX.
    }

    @AfterEach
    public void afterEach() {
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testIsNoOpWithAggregatesTablesOn(Context<?> context) {
        checkNotNative(context,
            "with  member [gender].[agg] as"
            + "  'aggregate({[gender].[gender].members},[measures].[unit sales])'"
            + "select NativizeSet(CrossJoin( "
            + "{gender.gender.members, gender.agg}, "
            + "{[marital status].[marital status].members}"
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "17", type = Integer.class)
    void testLevelHierarchyHighCardinality(Context<?> context) {
        // The cardinality for the hierarchy looks like this:
        //    Year: 2 (level * gender cardinality:2)
        //    Quarter: 16 (level * gender cardinality:2)
        //    Month: 48 (level * gender cardinality:2)
        String mdx =
            "select NativizeSet("
            + "CrossJoin( "
            + "gender.gender.members, "
            + "CrossJoin("
            + "{ measures.[unit sales] }, "
            + "[Time].[Month].members"
            + "))) on 0"
            + " from sales";
        checkNative(context, mdx);
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "50", type = Integer.class)
    void testLevelHierarchyLowCardinality(Context<?> context) {
        // The cardinality for the hierarchy looks like this:
        //    Year: 2 (level * gender cardinality:2)
        //    Quarter: 16 (level * gender cardinality:2)
        //    Month: 48 (level * gender cardinality:2)
        String mdx =
            "select NativizeSet("
            + "CrossJoin( "
            + "gender.gender.gender.members, "
            + "CrossJoin("
            + "{ measures.[unit sales] }, "
            + "[Time].[Time].[Month].members"
            + "))) on 0"
            + " from sales";
        checkNotNative(context,mdx);
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "2147483647", type = Integer.class)
    void testNamedSetLowCardinality(Context<?> context) {
        checkNotNative(context,
            "with "
            + "set [levelMembers] as 'crossjoin( gender.gender.members, "
            + "[marital status].[marital status].members) '"
            + "select  nativizeSet([levelMembers]) on 0 "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "2147483647", type = Integer.class)
    void testCrossjoinWithNamedSetLowCardinality(Context<?> context) {
        checkNotNative(context,
            "with "
            + "set [genderMembers] as 'gender.gender.members'"
            + "set [maritalMembers] as '[marital status].[marital status].members'"
            + "set [levelMembers] as 'crossjoin( [genderMembers],[maritalMembers]) '"
            + "select  nativizeSet([levelMembers]) on 0 "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMeasureInCrossJoinWithTwoDimensions(Context<?> context) {
        checkNative(context,
            "select NativizeSet("
            + "CrossJoin( "
            + "gender.gender.gender.members, "
            + "CrossJoin("
            + "{ measures.[unit sales] }, "
            + "[marital status].[marital status].[marital status].members"
            + "))) on 0 "
            + "from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MAX_RESULTS, value = "0", type = Integer.class)
    void testNativeResultLimitAtZero(Context<?> context) {
        // This query will return exactly 6 rows:
        // {Female,Male,Agg}x{Married,Single}
        String mdx =
            "with  member [gender].[gender].[agg] as"
            + "  'aggregate({[gender].[gender].[gender].members},[measures].[unit sales])'"
            + "select NativizeSet(CrossJoin( "
            + "{gender.gender.gender.members, gender.agg}, "
            + "{[marital status].[marital status].[marital status].members}"
            + ")) on 0 from sales";

        // Set limit to zero (effectively, no limit)
        checkNative(context, mdx);
    }

    // Split from the legacy testNativeResultLimitBeforeMerge: it mutated
    // NativizeMaxResults mid-test (6, run; then 3, run again expecting
    // failure). @RolapConfig bakes config in at context construction, so
    // the two phases become two tests, each with its own fixed limit.
    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MAX_RESULTS, value = "6", type = Integer.class)
    void testNativeResultLimitBeforeMerge_withinLimit(Context<?> context) {
        // This query will return exactly 6 rows:
        // {Female,Male,Agg}x{Married,Single}
        String mdx =
            "with  member [gender].[agg] as"
            + "  'aggregate({[gender].[gender].[gender].members},[measures].[unit sales])'"
            + "select NativizeSet(CrossJoin( "
            + "{gender.gender.gender.members, gender.agg}, "
            + "{[marital status].[marital status].[marital status].members}"
            + ")) on 0 from sales";

        // Set limit to exact size of result
        checkNative(context, mdx);
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MAX_RESULTS, value = "3", type = Integer.class)
    void testNativeResultLimitBeforeMerge_exceedsLimit(Context<?> context) {
        String mdx =
            "with  member [gender].[agg] as"
            + "  'aggregate({[gender].[gender].[gender].members},[measures].[unit sales])'"
            + "select NativizeSet(CrossJoin( "
            + "{gender.gender.gender.members, gender.agg}, "
            + "{[marital status].[marital status].[marital status].members}"
            + ")) on 0 from sales";

        try {
            // The native list doesn't contain the calculated members,
            // so it will have 4 rows.  Setting the limit to 3 means
            // that the exception will be thrown before calculated
            // members are merged into the result.
            checkNative(context,mdx);
            fail("Should have thrown ResourceLimitExceededException.");
        } catch (ResourceLimitExceededException expected) {
            // ok
        }
    }

    // Split from the legacy testNativeResultLimitDuringMerge: same reason as
    // testNativeResultLimitBeforeMerge_* above.
    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MAX_RESULTS, value = "6", type = Integer.class)
    void testNativeResultLimitDuringMerge_withinLimit(Context<?> context) {
        // This query will return exactly 6 rows:
        // {Female,Male,Agg}x{Married,Single}
        String mdx =
            "with  member [gender].[gender].[agg] as"
            + "  'aggregate({[gender].[gender].[gender].members},[measures].[unit sales])'"
            + "select NativizeSet(CrossJoin( "
            + "{gender.gender.gender.members, gender.agg}, "
            + "{[marital status].[marital status].[marital status].members}"
            + ")) on 0 from sales";

        // Set limit to exact size of result
        checkNative(context, mdx);
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MAX_RESULTS, value = "5", type = Integer.class)
    void testNativeResultLimitDuringMerge_exceedsLimit(Context<?> context) {
        String mdx =
            "with  member [gender].[gender].[agg] as"
            + "  'aggregate({[gender].[gender].[gender].members},[measures].[unit sales])'"
            + "select NativizeSet(CrossJoin( "
            + "{gender.gender.gender.members, gender.agg}, "
            + "{[marital status].[marital status].[marital status].members}"
            + ")) on 0 from sales";

        try {
            // The native list doesn't contain the calculated members,
            // so setting the limit to 5 means the exception won't be
            // thrown until calculated members are merged into the result.
            checkNative(context, mdx);
            fail("Should have thrown ResourceLimitExceededException.");
        } catch (ResourceLimitExceededException expected) {
        }
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMeasureAndDimensionInCrossJoin(Context<?> context) {
        checkNotNative(context,
            // There's no crossjoin left after the measure is set aside,
            // so it's not even a candidate for native evaluation.
            // This test is here to ensure that "NativizeSet" still returns
            // the correct result.
            "select NativizeSet("
            + "CrossJoin("
            + "{ measures.[unit sales] }, "
            + "[marital status].[marital status].members"
            + ")) on 0"
            + " from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testDimensionAndMeasureInCrossJoin(Context<?> context) {
        checkNotNative(context,
            // There's no crossjoin left after the measure is set aside,
            // so it's not even a candidate for native evaluation.
            // This test is here to ensure that "NativizeSet" still returns
            // the correct result.
            "select NativizeSet("
            + "CrossJoin("
            + "[marital status].[marital status].members, "
            + "{ measures.[unit sales] }"
            + ")) on 0"
            + " from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testAllByAll(Context<?> context) {
        checkNotNative(context,
            // There's no crossjoin left after all members are set aside,
            // so it's not even a candidate for native evaluation.
            // This test is here to ensure that "NativizeSet" still returns
            // the correct result.
            "select NativizeSet("
            + "CrossJoin("
            + "{ [gender].[all gender] }, "
            + "{ [marital status].[all marital status] } "
            + ")) on 0"
            + " from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testAllByAllByAll(Context<?> context) {
        checkNotNative(context,
            // There's no crossjoin left after all members are set aside,
            // so it's not even a candidate for native evaluation.
            // This test is here to ensure that "NativizeSet" still returns
            // the correct result.
            "select NativizeSet("
            + "CrossJoin("
            + "{ [product].[all products] }, "
            + "CrossJoin("
            + "{ [gender].[all gender] }, "
            + "{ [marital status].[all marital status] } "
            + "))) on 0"
            + " from sales");
    }

    // Split from the legacy testNativizeTwoAxes: it mutated
    // NativizeMinThreshold mid-test (0, native; then 200000, not native).
    // Same reason as testNativeResultLimitBeforeMerge_* above.
    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNativizeTwoAxes_lowThresholdIsNative(Context<?> context) {
        String mdx =
            "select "
            + "NativizeSet("
            + "CrossJoin("
            + "{ [gender].[gender].[gender].members }, "
            + "{ [marital status].[marital status].[marital status].members } "
            + ")) on 0,"
            + "NativizeSet("
            + "CrossJoin("
            + "{ [measures].[unit sales] }, "
            + "{ [Education Level].[Education Level].[Education Level].members } "
            + ")) on 1"
            + " from [warehouse and sales]";

        // Our setUp sets threshold at zero, so should always be native
        // if possible.
        checkNative(context,mdx);
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "200000", type = Integer.class)
    void testNativizeTwoAxes_highThresholdIsNotNative(Context<?> context) {
        String mdx =
            "select "
            + "NativizeSet("
            + "CrossJoin("
            + "{ [gender].[gender].[gender].members }, "
            + "{ [marital status].[marital status].[marital status].members } "
            + ")) on 0,"
            + "NativizeSet("
            + "CrossJoin("
            + "{ [measures].[unit sales] }, "
            + "{ [Education Level].[Education Level].[Education Level].members } "
            + ")) on 1"
            + " from [warehouse and sales]";

        // Set the threshold high; same mdx should no longer be natively
        // evaluated.
        checkNotNative(context,mdx);
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testCurrentMemberAsFunArg(Context<?> context) {
        checkNative(context,
            "with "
            ////////////////////////////////////////////////////////////
            // Having a member of the measures dimension as a function
            // argument will normally disable native evaluation but
            // there is a special case in FunUtil.checkNativeCompatible
            // which allows currentmember
            ////////////////////////////////////////////////////////////
            + "member [gender].[gender].[x] "
            + "   as 'iif (measures.currentmember is measures.[unit sales], "
            + "       Aggregate(gender.gender.gender.members), 101010)' "
            + "select "
            + "NativizeSet("
            + "crossjoin("
            + "{time.time.year.members}, "
            + "crossjoin("
            + "{gender.gender.x},"
            + "[marital status].[marital status].[marital status].members"
            + "))) "
            + "on axis(0) "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testOnlyMeasureIsLiteral(Context<?> context) {
        checkNotNative(context,
            //////////////////////////////////////////////////////////////////
            // There's no base cube, so this should NOT be natively evaluated.
            //////////////////////////////////////////////////////////////////
            "with "
            + "member [measures].[cog_oqp_int_t1] as '1', solve_order = 65535 "
            + "select NativizeSet(CrossJoin("
            + "   [marital status].[marital status].members, "
            + "   [gender].[gender].members "
            + ")) on 1, "
            + "{ [measures].[cog_oqp_int_t1] } "
            + "on 0 "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testTwoLiteralMeasuresAndUnitAndStoreSales(Context<?> context) {
        checkNative(context,
            // Should be natively evaluated because the unit sales
            // measure will bring in a base cube.
            "with "
            + "member [measures].[cog_oqp_int_t1] as '1', solve_order = 65535 "
            + "member [measures].[cog_oqp_int_t2] as '2', solve_order = 65535 "
            + "select "
            + "   NativizeSet(CrossJoin("
            + "      [marital status].[marital status].[marital status].members, "
            + "      [gender].[gender].[gender].members "
            + "    ))"
            + "on 1, "
            + "{ "
            + "   { [measures].[cog_oqp_int_t1] }, "
            + "   { [measures].[unit sales] }, "
            + "   { [measures].[cog_oqp_int_t2] }, "
            + "   { [measures].[store sales] } "
            + "} "
            + " on 0 "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testLiteralMeasuresWithinParentheses(Context<?> context) {
        checkNative(context,
            // Should be natively evaluated because the unit sales
            // measure will bring in a base cube.  The extra parens
            // around the reference to the calculated member should no
            // longer cause native evaluation to be abandoned.
            "with "
            + "member [measures].[cog_oqp_int_t1] as '1', solve_order = 65535 "
            + "member [measures].[cog_oqp_int_t2] as '2', solve_order = 65535 "
            + "select "
            + "   NativizeSet(CrossJoin("
            + "      [marital status].[marital status].[marital status].members, "
            + "      [gender].[gender].[gender].members "
            + "    ))"
            + "on 1, "
            + "{ "
            + "   { ((( [measures].[cog_oqp_int_t1] ))) }, "
            + "   { [measures].[unit sales] }, "
            + "   { ( [measures].[cog_oqp_int_t2] ) }, "
            + "   { [measures].[store sales] } "
            + "} "
            + " on 0 "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testIsEmptyOnMeasures(Context<?> context) {
        checkNative(context,
            "with "
            ////////////////////////////////////////////////////////
            // isEmpty doesn't pose a problem for native evaluation.
            ////////////////////////////////////////////////////////
            + "member [measures].[cog_oqp_int_t1] "
            + "   as 'iif( isEmpty( measures.[unit sales]), 1010,2020)', solve_order = 65535 "
            + "select "
            + "   NativizeSet(CrossJoin("
            + "      [marital status].[marital status].[marital status].members, "
            + "      [gender].[gender].[gender].members "
            + "    ))"
            + "on 1, "
            + "{ "
            + "   { [measures].[cog_oqp_int_t1] }, "
            + "   { [measures].[unit sales] } "
            + "} "
            + " on 0 "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testLagOnMeasures(Context<?> context) {
        checkNotNative(context,
            "with "
            /////////////////////////////////////////////
            // Lag function is NOT compatible with native.
            /////////////////////////////////////////////
            + "member [measures].[cog_oqp_int_t1] "
            + "   as 'measures.[store sales].lag(1)', solve_order = 65535 "
            + "select "
            + "   NativizeSet(CrossJoin("
            + "      [marital status].[marital status].members, "
            + "      [gender].[gender].members "
            + "    ))"
            + "on 1, "
            + "{ "
            + "   { [measures].[cog_oqp_int_t1] }, "
            + "   { [measures].[unit sales] }, "
            + "   { [measures].[store sales] } "
            + "} "
            + " on 0 "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testLagOnMeasuresWithinParentheses(Context<?> context) {
        checkNotNative(context,
            "with "
            /////////////////////////////////////////////
            // Lag function is NOT compatible with native.
            // Here we're making sure that the lag function
            // disables native eval even when buried in layers
            // of parentheses.
            /////////////////////////////////////////////
            + "member [measures].[cog_oqp_int_t1] "
            + "   as 'measures.[store sales].lag(1)', solve_order = 65535 "
            + "select "
            + "   NativizeSet(CrossJoin("
            + "      [marital status].[marital status].members, "
            + "      [gender].[gender].members "
            + "    ))"
            + "on 1, "
            + "{ "
            + "   { ((( [measures].[cog_oqp_int_t1] ))) }, "
            + "   { [measures].[unit sales] }, "
            + "   { [measures].[store sales] } "
            + "} "
            + " on 0 "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testRangeOfMeasures(Context<?> context) {
        checkNotNative(context,
            "select "
            + "   NativizeSet(CrossJoin("
            + "      [marital status].[marital status].members, "
            + "      [gender].[gender].members "
            + "    ))"
            + "on 1, "
            + "{ "
            ///////////////////////////////////////////////////
            // Range of measures is NOT compatible with native.
            ///////////////////////////////////////////////////
            + "    measures.[unit sales] : measures.[store sales]  "
            + "} "
            + " on 0 "
            + "from [warehouse and sales]");
    }


    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testOrderOnMeasures(Context<?> context) {
        checkNative(context,
            "with "
            ///////////////////////////////////////////////////
            // Order function should be compatible with native.
            ///////////////////////////////////////////////////
            + "member [measures].[cog_oqp_int_t1] "
            + " as 'aggregate(order({measures.[store sales]}, measures.[store sales]), "
            + "measures.[store sales])', solve_order = 65535 "
            + "select "
            + "   NativizeSet(CrossJoin("
            + "      [marital status].[marital status].[marital status].members, "
            + "      [gender].[gender].[gender].members "
            + "   ))"
            + "on 1, "
            + "{ "
            + "   measures.[cog_oqp_int_t1],"
            + "   measures.[unit sales]"
            + "} "
            + " on 0 "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testLiteralMeasureAndUnitSalesUsingSet(Context<?> context) {
        checkNative(context,
            // Should be natively evaluated because the unit sales
            "with "   // measure will bring in a base cube.
            + "member [measures].[cog_oqp_int_t1] as '1', solve_order = 65535 "
            + "member [measures].[cog_oqp_int_t2] as '2', solve_order = 65535 "
            + "set [cog_oqp_int_s1] as "
            + "   'CrossJoin("
            + "      [marital status].[marital status].[marital status].members, "
            + "      [gender].[gender].[gender].members "
            + "    )'"
            + "select "
            + "   NativizeSet([cog_oqp_int_s1])"
            + "on 1, "
            + "{ "
            + "   [measures].[cog_oqp_int_t1], "
            + "   [measures].[unit sales], "
            + "   [measures].[cog_oqp_int_t1], "
            + "   [measures].[store sales] "
            + "} "
            + " on 0 "
            + "from [warehouse and sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNoSubstitutionsArityOne(Context<?> context) {
        checkNotNative(context,
            // no crossjoin, so not native
            "SELECT NativizeSet({Gender.F, Gender.M}) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNoSubstitutionsArityTwo(Context<?> context) {
        checkNotNative(context,
            "SELECT NativizeSet(CrossJoin("
            + "{Gender.F, Gender.M}, "
            + "{ [Marital Status].M } "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testExplicitCurrentMonth(Context<?> context) {
        checkNative(context,
            "SELECT NativizeSet(CrossJoin( "
            + "   { [Time].[Time].[Month].currentmember }, "
            + "   Gender.Gender.Gender.members )) " + "on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void disabled_testCalculatedCurrentMonth(Context<?> context) {
        checkNative(context,
            "WITH "
            + "SET [Current Month] AS 'tail([Time].[Time].[month].members, 1)'"
            + "SELECT NativizeSet(CrossJoin( "
            + "   { [Current Month] }, "
            + "   Gender.Gender.Gender.members )) "
            + "on 0 from sales");
    }

    @Disabled //has not been fixed during creating Daanse project
    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void disabled_testCalculatedRelativeMonth(Context<?> context) {
        checkNative(context,
            "with "
            + "member [gender].[cog_oqp_int_t2] as '1', solve_order = 65535 "
            + "select NativizeSet("
            + "   { { [gender].[cog_oqp_int_t2] }, "
            + "       crossjoin( {tail([Time].[month].members, 1)}, [gender].[gender].members ) },"
            + "   { { [gender].[cog_oqp_int_t2] }, "
            + "       crossjoin( {tail([Time].[month].members, 1).lag(1)}, [gender].[gender].members ) },"
            + ") on 0 "
            + "from [sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testAcceptsAllDimensionMembersSetAsInput(Context<?> context) {
        checkNotNative(context,
            // no crossjoin, so not native
            "SELECT NativizeSet({[Marital Status].[Marital Status].members})"
            + " on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testAcceptsCrossJoinAsInput(Context<?> context) {
        checkNative(context,
            "SELECT NativizeSet( CrossJoin({ Gender.Gender.F, Gender.Gender.M }, "
            + "{[Marital Status].[Marital Status].[Marital Status].members})) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testRedundantEnumMembersFirst(Context<?> context) {
        checkNative(context,
            // In the enumerated marital status values { M, S, S }
            // the second S is clearly redundant, but should be
            // included in the result nonetheless. The extra
            // level of parens aren't logically necessary, but
            // are included here because they require special handling.
            "SELECT NativizeSet( CrossJoin("
            + "{ { [Marital Status].M, [Marital Status].S }, "
            + "  { [Marital Status].S } "
            + "},"
            + "CrossJoin( "
            + "{ gender.gender.members }, "
            + "{ time.quarter.members } "
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testRedundantEnumMembersMiddle(Context<?> context) {
        checkNative(context,
            // In the enumerated gender values { F, M, M, M }
            // the last two M values are redunant, but should be
            // included in the result nonetheless. The extra
            // level of parens aren't logically necessary, but
            // are included here because they require special handling.
            "SELECT NativizeSet( CrossJoin("
            + "{  [Marital Status].[Marital Status].[Marital Status].members },"
            + "CrossJoin( "
            + "{ { gender.gender.F, gender.gender.M , gender.gender.M}, "
            + "  { gender.gender.M } "
            + "}, "
            + "{ time.time.quarter.members } "
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testRedundantEnumMembersLast(Context<?> context) {
        checkNative(context,
            // In the enumerated time quarter values { Q1, Q2, Q2 }
            // the last two Q2 values are redunant, but should be
            // included in the result nonetheless. The extra
            // level of parens aren't logically necessary, but
            // are included here because they require special handling.
            "SELECT NativizeSet( CrossJoin("
            + "{  [Marital Status].[Marital Status].[Marital Status].members },"
            + "CrossJoin( "
            + "{ gender.gender.gender.members }, "
            + "{ { time.time.[1997].Q1, time.[1997].Q2 }, "
            + "  { time.time.[1997].Q2 } "
            + "} "
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testRedundantLevelMembersFirst(Context<?> context) {
        checkNative(context,
            // The second marital status members function is clearly
            // redundant, but should be included in the result
            // nonetheless. The extra level of parens aren't logically
            // necessary, but are included here because they require
            // special handling.
            "SELECT NativizeSet( CrossJoin("
            + "{  [Marital Status].[Marital Status].members, "
            + "   { [Marital Status].[Marital Status].members } "
            + "},"
            + "CrossJoin( "
            + "{ gender.gender.members }, "
            + "{ time.quarter.members } "
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testRedundantLevelMembersMiddle(Context<?> context) {
        checkNative(context,
            // The second gender members function is clearly
            // redundant, but should be included in the result
            // nonetheless. The extra level of parens aren't logically
            // necessary, but are included here because they require
            // special handling.
            "SELECT NativizeSet( CrossJoin("
            + "{  [Marital Status].[Marital Status].[Marital Status].members },"
            + "CrossJoin( "
            + "{ gender.gender.gender.members, "
            + "  { gender.gender.gender.members } "
            + "}, "
            + "{ time.time.quarter.members } "
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testRedundantLevelMembersLast(Context<?> context) {
        checkNative(context,
            // The second time.quarter members function is clearly
            // redundant, but should be included in the result
            // nonetheless. The extra level of parens aren't logically
            // necessary, but are included here because they require
            // special handling.
            "SELECT NativizeSet( CrossJoin("
            + "{  [Marital Status].[Marital Status].[Marital Status].members },"
            + "CrossJoin( "
            + "{ gender.gender.gender.members }, "
            + "{ time.time.quarter.members, "
            + "  { time.time.quarter.members } "
            + "} "
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNonEmptyNestedCrossJoins(Context<?> context) {
        checkNative(context,
            "SELECT "
            + "NativizeSet(CrossJoin("
            + "{ Gender.Gender.F, Gender.Gender.M }, "
            + "CrossJoin("
            + "{ [Marital Status].[Marital Status].[Marital Status].members }, "
            + "CrossJoin("
            + "{ [Store].[Store].[All Stores].[USA].[CA], [Store].[All Stores].[USA].[OR] }, "
            + "{ [Education Level].[Education Level].[Education Level].members } "
            + ")))"
            + ") on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testLevelMembersAndAll(Context<?> context) {
        checkNative(context,
            "select NativizeSet ("
            + "crossjoin( "
            + "  { gender.gender.gender.members, gender.gender.[all gender] }, "
            + "  [marital status].[marital status].[marital status].members "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testCrossJoinArgInNestedBraces(Context<?> context) {
        checkNative(context,
            "select NativizeSet ("
            + "crossjoin( "
            + "  { { gender.gender.gender.members } }, "
            + "  [marital status].[marital status].[marital status].members "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testLevelMembersAndAllWhereOrderMatters(Context<?> context) {
        checkNative(context,
            "select NativizeSet ("
            + "crossjoin( "
            + "  { gender.gender.gender.members, gender.gender.[all gender] }, "
            + "  { [marital status].[marital status].S, [marital status].[marital status].M } "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testEnumMembersAndAll(Context<?> context) {
        checkNative(context,
            "select NativizeSet ("
            + "crossjoin( "
            + "  { gender.gender.F, gender.gender.M, gender.gender.[all gender] }, "
            + "  [marital status].[marital status].[marital status].members "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNativizeWithASetAtTopLevel(Context<?> context) {
        checkNative(context,
            "WITH"
            + "  MEMBER [Gender].[Gender].[umg1] AS "
            + "  '([Gender].[Gender].[gender agg], [Measures].[Unit Sales])', SOLVE_ORDER = 8 "
            + "  MEMBER [Gender].[Gender].[gender agg] AS"
            + "  'AGGREGATE({[Gender].[Gender].[Gender].MEMBERS},[Measures].[Unit Sales])', SOLVE_ORDER = 8 "
            + " MEMBER [Marital Status].[Marital Status].[umg2] AS "
            + " '([Marital Status].[Marital Status].[marital agg], [Measures].[Unit Sales])', SOLVE_ORDER = 4 "
            + " MEMBER [Marital Status].[Marital Status].[marital agg] AS "
            + "  'AGGREGATE({[Marital Status].[Marital Status].[Marital Status].MEMBERS},[Measures].[Unit Sales])', SOLVE_ORDER = 4 "
            + " SET [s2] AS "
            + "  'CROSSJOIN({[Marital Status].[Marital Status].MEMBERS}, {{[Gender].[Gender].[Gender].MEMBERS}, {[Gender].[Gender].[umg1]}})' "
            + " SET [s1] AS "
            + "  'CROSSJOIN({[Marital Status].[Marital Status].[umg2]}, {[Gender].[Gender].DEFAULTMEMBER})' "
            + " SELECT "
            + "  NativizeSet({[Measures].[Unit Sales]}) DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON AXIS(0), "
            + "  NativizeSet({[s2],[s1]}) "
            + " DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON AXIS(1)"
            + " FROM [Sales]  CELL PROPERTIES VALUE, FORMAT_STRING");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNativizeWithASetAtTopLevel3Levels(Context<?> context) {
        checkNative(context,
            "WITH\n"
            + "MEMBER [Gender].[COG_OQP_INT_umg2] AS 'IIF([Measures].CURRENTMEMBER IS [Measures].[Unit Sales], "
            + "([Gender].[COG_OQP_INT_m5], [Measures].[Unit Sales]), "
            + "AGGREGATE({[Gender].[Gender].[Gender].MEMBERS}))', SOLVE_ORDER = 8\n"
            + "MEMBER [Gender].[COG_OQP_INT_m5] AS "
            + "'AGGREGATE({[Gender].[Gender].[Gender].MEMBERS}, [Measures].[Unit Sales])', SOLVE_ORDER = 8\n"
            + "MEMBER [Store Type].[Store Type].[COG_OQP_INT_umg1] AS "
            + "'IIF([Measures].CURRENTMEMBER IS [Measures].[Unit Sales], "
            + "([Store Type].[Store Type].[COG_OQP_INT_m4], [Measures].[Unit Sales]), "
            + "AGGREGATE({[Store Type].[Store Type].[Store Type].MEMBERS}))', SOLVE_ORDER = 12\n"
            + "MEMBER [Store Type].[Store Type].[COG_OQP_INT_m4] AS "
            + "'AGGREGATE({[Store Type].[Store Type].[Store Type].MEMBERS}, [Measures].[Unit Sales])', SOLVE_ORDER = 12\n"
            + "MEMBER [Marital Status].[Marital Status].[COG_OQP_INT_umg3] AS "
            + "'IIF([Measures].CURRENTMEMBER IS [Measures].[Unit Sales], "
            + "([Marital Status].[Marital Status].[COG_OQP_INT_m6], [Measures].[Unit Sales]), "
            + "AGGREGATE({[Marital Status].[Marital Status].[Marital Status].MEMBERS}))', SOLVE_ORDER = 4\n"
            + "MEMBER [Marital Status].[Marital Status].[COG_OQP_INT_m6] AS "
            + "'AGGREGATE({[Marital Status].[Marital Status].[Marital Status].MEMBERS}, [Measures].[Unit Sales])', SOLVE_ORDER = 4\n"
            + "SET [COG_OQP_INT_s5] AS 'CROSSJOIN({[Marital Status].[Marital Status].[Marital Status].MEMBERS}, {[COG_OQP_INT_s4], [COG_OQP_INT_s3]})'\n"
            + "SET [COG_OQP_INT_s4] AS 'CROSSJOIN({[Gender].[Gender].[Gender].MEMBERS}, {{[Store Type].[Store Type].[Store Type].MEMBERS}, "
            + "{[Store Type].[Store Type].[COG_OQP_INT_umg1]}})'\n"
            + "SET [COG_OQP_INT_s3] AS 'CROSSJOIN({[Gender].[Gender].[COG_OQP_INT_umg2]}, {[Store Type].DEFAULTMEMBER})'\n"
            + "SET [COG_OQP_INT_s2] AS 'CROSSJOIN({[Marital Status].[Marital Status].[COG_OQP_INT_umg3]}, [COG_OQP_INT_s1])'\n"
            + "SET [COG_OQP_INT_s1] AS 'CROSSJOIN({[Gender].[Gender].DEFAULTMEMBER}, {[Store Type].[Store Type].DEFAULTMEMBER})' \n"
            + "SELECT {[Measures].[Unit Sales]} "
            + "DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON AXIS(0), \n"
            + "NativizeSet({[COG_OQP_INT_s5], [COG_OQP_INT_s2]}) "
            + "DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON AXIS(1)\n"
            + "FROM [Sales]  CELL PROPERTIES VALUE, FORMAT_STRING\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNativizeWithASetAtTopLevel2(Context<?> context) {
        checkNative(context,
            "WITH"
            + "  MEMBER [Gender].[Gender].[umg1] AS "
            + "  '([Gender].[Gender].[gender agg], [Measures].[Unit Sales])', SOLVE_ORDER = 8 "
            + "  MEMBER [Gender].[Gender].[gender agg] AS"
            + "  'AGGREGATE({[Gender].[Gender].[Gender].MEMBERS},[Measures].[Unit Sales])', SOLVE_ORDER = 8 "
            + " MEMBER [Marital Status].[Marital Status].[umg2] AS "
            + " '([Marital Status].[Marital Status].[marital agg], [Measures].[Unit Sales])', SOLVE_ORDER = 4 "
            + " MEMBER [Marital Status].[Marital Status].[marital agg] AS "
            + "  'AGGREGATE({[Marital Status].[Marital Status].[Marital Status].MEMBERS},[Measures].[Unit Sales])', SOLVE_ORDER = 4 "
            + " SET [s2] AS "
            + "  'CROSSJOIN({{[Marital Status].[Marital Status].[Marital Status].MEMBERS},{[Marital Status].[Marital Status].[umg2]}}, "
            + "{{[Gender].[Gender].[Gender].MEMBERS}, {[Gender].[Gender].[umg1]}})' "
            + " SET [s1] AS "
            + "  'CROSSJOIN({[Marital Status].[Marital Status].[umg2]}, {[Gender].[Gender].DEFAULTMEMBER})' "
            + " SELECT "
            + "  NativizeSet({[Measures].[Unit Sales]}) "
            + "DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON AXIS(0), "
            + "  NativizeSet({[s2]}) "
            + " DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON AXIS(1)"
            + " FROM [Sales]  CELL PROPERTIES VALUE, FORMAT_STRING");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testGenderMembersAndAggByMaritalStatus(Context<?> context) {
        checkNative(context,
            "with member gender.gender.agg as 'Aggregate( gender.gender.gender.members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { gender.gender.gender.members, gender.gender.[agg] }, "
            + "  [marital status].[marital status].[marital status].members "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testGenderAggAndMembersByMaritalStatus(Context<?> context) {
        checkNative(context,
            "with member gender.gender.agg as 'Aggregate( gender.gender.gender.members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { gender.gender.[agg], gender.gender.gender.members }, "
            + "  [marital status].[marital status].[marital status].members "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testGenderAggAndMembersAndAllByMaritalStatus(Context<?> context) {
        checkNative(context,
            "with member gender.gender.agg as 'Aggregate( gender.gender.gender.members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { gender.gender.[agg], gender.gender.gender.members, gender.gender.[all gender] }, "
            + "  [marital status].[marital status].[marital status].members "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMaritalStatusByGenderMembersAndAgg(Context<?> context) {
        checkNative(context,
            "with member gender.gender.agg as 'Aggregate( gender.gender.gender.members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  [marital status].[marital status].[marital status].members, "
            + "  { gender.gender.gender.members, gender.gender.[agg] } "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMaritalStatusByGenderAggAndMembers(Context<?> context) {
        checkNative(context,
            "with member gender.gender.agg as 'Aggregate( gender.gender.gender.members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  [marital status].[marital status].[marital status].members, "
            + "  { gender.gender.[agg], gender.gender.gender.members } "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testAggWithEnumMembers(Context<?> context) {
        checkNative(context,
            "with member gender.gender.agg as 'Aggregate( gender.gender.gender.members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { gender.gender.gender.members, gender.gender.[agg] }, "
            + "  { [marital status].[marital status].[M], [marital status].[marital status].[S] } "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testCrossjoinArgWithMultipleElementTypes(Context<?> context) {
        checkNative(context,
            // Test for correct handling of a crossjoin arg that contains
            // a combination of element types: a members function, an
            // explicit enumerated value, an aggregate, and the all level.
            "with member [gender].[gender].agg as 'Aggregate( gender.gender.gender.members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "{ time.time.quarter.members }, "
            + "CrossJoin( "
            + "{ gender.gender.gender.members, gender.gender.F, gender.gender.[agg], gender.gender.[all gender] }, "
            + "{ [marital status].[marital status].[marital status].members }"
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testProductFamilyMembers(Context<?> context) {
        checkNative(context,
            "select non empty NativizeSet("
            + "crossjoin( "
            + "  [product].[product family].members, "
            + "  { [gender].F } "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNestedCrossJoinWhereAllColsHaveNative(Context<?> context) {
        checkNative(context,
            "with "
            + "member gender.gender.agg as 'Aggregate( gender.gender.gender.members )' "
            + "member [marital status].agg as 'Aggregate( [marital status].[marital status].[marital status].members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { gender.gender.[all gender], gender.gender.gender.members, gender.gender.[agg] }, "
            + "  crossjoin("
            + "  { [marital status].[marital status].[marital status].members, [marital status].[marital status].[agg] },"
            + "  [Education Level].[Education Level].[Education Level].members "
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNestedCrossJoinWhereFirstColumnNonNative(Context<?> context) {
        checkNative(context,
            "with "
            + "member gender.gender.agg as 'Aggregate( gender.gender.gender.members )' "
            + "member [marital status].[marital status].agg as 'Aggregate( [marital status].[marital status].[marital status].members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { gender.gender.[all gender], gender.gender.[agg] }, "
            + "  crossjoin("
            + "  { [marital status].[marital status].[marital status].members, [marital status].[marital status].[agg] },"
            + "  [Education Level].[Education Level].[Education Level].members "
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNestedCrossJoinWhereMiddleColumnNonNative(Context<?> context) {
        checkNative(context,
            "with "
            + "member gender.gender.agg as 'Aggregate( gender.gender.gender.members )' "
            + "member [marital status].[marital status].agg as 'Aggregate( [marital status].[marital status].[marital status].members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { [marital status].[marital status].[marital status].members, [marital status].[marital status].[agg] },"
            + "  crossjoin("
            + "  { gender.gender.[all gender], gender.gender.[agg] }, "
            + "  [Education Level].[Education Level].[Education Level].members "
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testNestedCrossJoinWhereLastColumnNonNative(Context<?> context) {
        checkNative(context,
            "with "
            + "member gender.gender.agg as 'Aggregate( gender.gender.gender.members )' "
            + "member [marital status].[marital status].agg as 'Aggregate( [marital status].[marital status].[marital status].members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { [marital status].[marital status].[marital status].members, [marital status].[marital status].[agg] },"
            + "  crossjoin("
            + "  [Education Level].[Education Level].[Education Level].members, "
            + "  { gender.gender.[all gender], gender.gender.[agg] } "
            + "))) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testGenderAggByMaritalStatus(Context<?> context) {
        checkNotNative(context,
            // NativizeSet removes the crossjoin, so not native
            "with member gender.agg as 'Aggregate( gender.gender.members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { gender.[agg] }, "
            + "  [marital status].[marital status].members "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testGenderAggTwiceByMaritalStatus(Context<?> context) {
        checkNotNative(context,
            // NativizeSet removes the crossjoin, so not native
            "with "
            + "member gender.agg1 as 'Aggregate( { gender.M } )' "
            + "member gender.agg2 as 'Aggregate( { gender.F } )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { gender.[agg1], gender.[agg2] }, "
            + "  [marital status].[marital status].members "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testSameGenderAggTwiceByMaritalStatus(Context<?> context) {
        checkNotNative(context,
            // NativizeSet removes the crossjoin, so not native
            "with "
            + "member gender.agg as 'Aggregate( gender.gender.members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  { gender.[agg], gender.[agg] }, "
            + "  [marital status].[marital status].members "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMaritalStatusByGenderAgg(Context<?> context) {
        checkNotNative(context,
            // NativizeSet removes the crossjoin, so not native
            "with member gender.agg as 'Aggregate( gender.gender.members )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  [marital status].[marital status].members, "
            + "  { gender.[agg] } "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMaritalStatusByTwoGenderAggs(Context<?> context) {
        checkNotNative(context,
            // NativizeSet removes the crossjoin, so not native
            "with "
            + "member gender.agg1 as 'Aggregate( { gender.M } )' "
            + "member gender.agg2 as 'Aggregate( { gender.F } )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  [marital status].[marital status].members, "
            + "  { gender.[agg1], gender.[agg2] } "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMaritalStatusBySameGenderAggTwice(Context<?> context) {
        checkNotNative(context,
            // NativizeSet removes the crossjoin, so not native
            "with "
            + "member gender.agg as 'Aggregate( { gender.M } )' "
            + "select NativizeSet("
            + "crossjoin( "
            + "  [marital status].[marital status].members, "
            + "  { gender.[agg], gender.[agg] } "
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMultipleLevelsOfSameDimInConcatenatedJoins(Context<?> context) {
        checkNotNative(context,
            // See notes for testMultipleLevelsOfSameDimInSingleArg
            // because the NativizeSetFunDef transforms this mdx into the
            // mdx in that test.
            "select NativizeSet( {"
            + "CrossJoin("
            + "  { [Time].[Year].members },"
            + "  { gender.F, gender. M } ),"
            + "CrossJoin("
            + "  { [Time].[Quarter].members },"
            + "  { gender.F, gender. M } )"
            + "} ) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMultipleLevelsOfSameDimInSingleArg(Context<?> context) {
        checkNotNative(context,
            // Although it's legal MDX, the RolapNativeSet.checkCrossJoinArg
            // can't deal with an arg that contains multiple .members functions.
            // If they were at the same level, the NativizeSetFunDef would
            // deal with them, but since they are at differen levels, we're
            // stuck.
            "select NativizeSet( {"
            + "CrossJoin("
            + "  { [Time].[Year].members,"
            + "    [Time].[Quarter].members },"
            + "  { gender.F, gender. M } )"
            + "} ) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testDoesNoHarmToPlainEnumeratedMembers(Context<?> context) {


        assertQueryIsReWritten(context.getConnectionWithDefaultRole(),
            "SELECT NativizeSet({Gender.Gender.M,Gender.Gender.F}) on 0 from sales",
            "select "
            + "NativizeSet({[Gender].[Gender].[M], [Gender].[Gender].[F]}) "
            + "ON COLUMNS\n"
            + "from [sales]\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testDoesNoHarmToPlainDotMembers(Context<?> context) {


        assertQueryIsReWritten(context.getConnectionWithDefaultRole(),
            "select NativizeSet({[Marital Status].[Marital Status].members}) "
            + "on 0 from sales",
            "select NativizeSet({[Marital Status].[Marital Status].Members}) "
            + "ON COLUMNS\n"
            + "from [sales]\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testTransformsCallToRemoveDotMembersInCrossJoin(Context<?> context) {


        assertQueryIsReWritten(context.getConnectionWithDefaultRole(),
            "select NativizeSet(CrossJoin({Gender.Gender.M,Gender.Gender.F},{[Marital Status].[Marital Status].[Marital Status].members})) "
            + "on 0 from sales",
            "with member [Marital Status].[Marital Status].[_Nativized_Member_Marital Status_Marital Status_Marital Status_] as '[Marital Status].[Marital Status].DefaultMember'\n"
            + "  set [_Nativized_Set_Marital Status_Marital Status_Marital Status_] as "
            + "'{[Marital Status].[Marital Status].[_Nativized_Member_Marital Status_Marital Status_Marital Status_]}'\n"
            + "  member [Gender].[Gender].[_Nativized_Sentinel_Gender_Gender_(All)_] as '101010'\n"
            + "  member [Marital Status].[Marital Status].[_Nativized_Sentinel_Marital Status_Marital Status_(All)_] as '101010'\n"
            + "select NativizeSet(Crossjoin({[Gender].[Gender].[M], [Gender].[Gender].[F]}, "
            + "{[_Nativized_Set_Marital Status_Marital Status_Marital Status_]})) ON COLUMNS\n"
            + "from [sales]\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void DISABLED_testTransformsWithSeveralDimensionsNestedOnRows(Context<?> context) {


        assertQueryIsReWritten(context.getConnectionWithDefaultRole(),
            "WITH SET [COG_OQP_INT_s4] AS 'CROSSJOIN({[Education Level].[Education Level].[Graduate Degree]},"
            + " [COG_OQP_INT_s3])'"
            + " SET [COG_OQP_INT_s3] AS 'CROSSJOIN({[Marital Status].[Marital Status].[S]}, [COG_OQP_INT_s2])'"
            + " SET [COG_OQP_INT_s2] AS 'CROSSJOIN({[Gender].[Gender].[F]}, [COG_OQP_INT_s1])'"
            + " SET [COG_OQP_INT_s1] AS 'CROSSJOIN({[Product].[Product].[Product Name].MEMBERS}, {[Customers].[Customers].[Name].MEMBERS})' "
            + "SELECT {[Measures].[Unit Sales]} DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON AXIS(0),"
            + " NativizeSet([COG_OQP_INT_s4]) DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON AXIS(1) "
            + "FROM [Sales] CELL PROPERTIES VALUE, FORMAT_STRING",
            "with set [COG_OQP_INT_s4] as 'Crossjoin({[Education Level].[Education Level].[Graduate Degree]}, [COG_OQP_INT_s3])'\n"
            + "  set [COG_OQP_INT_s3] as 'Crossjoin({[Marital Status].[Marital Status].[S]}, [COG_OQP_INT_s2])'\n"
            + "  set [COG_OQP_INT_s2] as 'Crossjoin({[Gender].[Gender].[F]}, [COG_OQP_INT_s1])'\n"
            + "  set [COG_OQP_INT_s1] as 'Crossjoin({[_Nativized_Set_Product_Product_Product Name_]}, {[_Nativized_Set_Customers_Customers_Name_]})'\n"
            + "  member [Product].[Product].[_Nativized_Member_Product_Product_Product Name_] as '[Product].[Product].DefaultMember'\n"
            + "  set [_Nativized_Set_Product_Product_Product Name_] as '{[Product].[Product].[_Nativized_Member_Product_Product_Product Name_]}'\n"
            + "  member [Customers].[Customers].[_Nativized_Member_Customers_Customers_Name_] as '[Customers].[Customers].DefaultMember'\n"
            + "  set [_Nativized_Set_Customers_Customers_Name_] as '{[Customers].[Customers].[_Nativized_Member_Customers_Customers_Name_]}'\n"
            + "  member [Education Level].[Education Level].[_Nativized_Sentinel_Education Level_Education Level_(All)_] as '101010'\n"
            + "  member [Marital Status].[Marital Status].[_Nativized_Sentinel_Marital Status_Marital Status_(All)_] as '101010'\n"
            + "  member [Gender].[Gender].[_Nativized_Sentinel_Gender_Gender_(All)_] as '101010'\n"
            + "  member [Product].[Product].[_Nativized_Sentinel_Product_Product_(All)_] as '101010'\n"
            + "  member [Customers].[Customers].[_Nativized_Sentinel_Customers_Customers_(All)_] as '101010'\n"
            + "select {[Measures].[Unit Sales]} DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON COLUMNS,\n"
            + "  NativizeSet([COG_OQP_INT_s4]) DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON ROWS\n"
            + "from [Sales]\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testTransformsComplexQueryWithGenerateAndAggregate(Context<?> context) {


        assertQueryIsReWritten(context.getConnectionWithDefaultRole(),
            "WITH MEMBER [Product].[Product].[COG_OQP_INT_umg1] AS "
            + "'IIF([Measures].CURRENTMEMBER IS [Measures].[Unit Sales], ([Product].[Product].[COG_OQP_INT_m2], [Measures].[Unit Sales]),"
            + " AGGREGATE({[Product].[Product].[Product Name].MEMBERS}))', SOLVE_ORDER = 4 "
            + "MEMBER [Product].[Product].[COG_OQP_INT_m2] AS 'AGGREGATE({[Product].[Product].[Product Name].MEMBERS},"
            + " [Measures].[Unit Sales])', SOLVE_ORDER = 4 "
            + "SET [COG_OQP_INT_s5] AS 'CROSSJOIN({[Marital Status].[Marital Status].[S]}, [COG_OQP_INT_s4])'"
            + " SET [COG_OQP_INT_s4] AS 'CROSSJOIN({[Gender].[Gender].[F]}, [COG_OQP_INT_s2])'"
            + " SET [COG_OQP_INT_s3] AS 'CROSSJOIN({[Gender].[Gender].[F]}, {[COG_OQP_INT_s2], [COG_OQP_INT_s1]})' "
            + "SET [COG_OQP_INT_s2] AS 'CROSSJOIN({[Product].[Product].[Product Name].MEMBERS}, {[Customers].[Customers].[Name].MEMBERS})' "
            + "SET [COG_OQP_INT_s1] AS 'CROSSJOIN({[Product].[Product].[COG_OQP_INT_umg1]}, {[Customers].[Customers].DEFAULTMEMBER})' "
            + "SELECT {[Measures].[Unit Sales]} DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON AXIS(0),"
            + " NativizeSet(GENERATE({[Education Level].[Education Level].[Graduate Degree]}, \n"
            + "CROSSJOIN(HEAD({([Education Level].[Education Level].CURRENTMEMBER)}, IIF(COUNT([COG_OQP_INT_s5], INCLUDEEMPTY) > 0, 1, 0)), "
            + "GENERATE({[Marital Status].[Marital Status].[S]}, CROSSJOIN(HEAD({([Marital Status].[Marital Status].CURRENTMEMBER)}, "
            + "IIF(COUNT([COG_OQP_INT_s4], INCLUDEEMPTY) > 0, 1, 0)), [COG_OQP_INT_s3]), ALL)), ALL))"
            + " DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON AXIS(1)"
            + " FROM [Sales]  CELL PROPERTIES VALUE, FORMAT_STRING",
            "with member [Product].[Product].[COG_OQP_INT_umg1] as "
            + "'IIf(([Measures].CurrentMember IS [Measures].[Unit Sales]), ([Product].[Product].[COG_OQP_INT_m2], [Measures].[Unit Sales]), "
            + "Aggregate({[Product].[Product].[Product Name].Members}))', SOLVE_ORDER = 4\n"
            + "  member [Product].[Product].[COG_OQP_INT_m2] as "
            + "'Aggregate({[Product].[Product].[Product Name].Members}, [Measures].[Unit Sales])', SOLVE_ORDER = 4\n"
            + "  set [COG_OQP_INT_s5] as 'Crossjoin({[Marital Status].[Marital Status].[S]}, [COG_OQP_INT_s4])'\n"
            + "  set [COG_OQP_INT_s4] as 'Crossjoin({[Gender].[Gender].[F]}, [COG_OQP_INT_s2])'\n"
            + "  set [COG_OQP_INT_s3] as 'Crossjoin({[Gender].[Gender].[F]}, {[COG_OQP_INT_s2], [COG_OQP_INT_s1]})'\n"
            + "  set [COG_OQP_INT_s2] as 'Crossjoin({[Product].[Product].[Product Name].Members}, {[Customers].[Customers].[Name].Members})'\n"
            + "  set [COG_OQP_INT_s1] as 'Crossjoin({[Product].[Product].[COG_OQP_INT_umg1]}, {[Customers].[Customers].DefaultMember})'\n"
            + "select {[Measures].[Unit Sales]} DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON COLUMNS,\n"
            + "  NativizeSet(Generate({[Education Level].[Education Level].[Graduate Degree]}, "
            + "Crossjoin(Head({([Education Level].[Education Level].CurrentMember)}, IIf((Count([COG_OQP_INT_s5], INCLUDEEMPTY) > 0), 1, 0)), "
            + "Generate({[Marital Status].[Marital Status].[S]}, "
            + "Crossjoin(Head({([Marital Status].[Marital Status].CurrentMember)}, "
            + "IIf((Count([COG_OQP_INT_s4], INCLUDEEMPTY) > 0), 1, 0)), [COG_OQP_INT_s3]), ALL)), ALL)) "
            + "DIMENSION PROPERTIES PARENT_LEVEL, CHILDREN_CARDINALITY, PARENT_UNIQUE_NAME ON ROWS\n"
            + "from [Sales]\n");
    }

    @Disabled //has not been fixed during creating Daanse project
    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void DISABLED_testParallelCrossjoins(Context<?> context) {
        checkNative(context,
            // DE2185
            "select NativizeSet( {"
            + "  CrossJoin( { [Marital Status].[Marital Status].members }, { gender.F, gender. M } ),"
            + "  CrossJoin( { [Marital Status].[Marital Status].members }, { gender.F, gender. M } )"
            + "} ) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMultipleHierarchySsasTrue(Context<?> context) {


        // Ssas compatible: time.[weekly].[week]
        // Use fresh connection -- unique names are baked in when schema is
        // loaded, depending the Ssas setting at that time.
        //Context<?> context = getTestContext().withFreshConnection();
        Connection connection = context.getConnectionWithDefaultRole();
        try {
            assertQueryIsReWritten(
                connection,
                "select nativizeSet(crossjoin(time.[week].members, { gender.gender.m })) on 0 "
                + "from sales",
                "with member [Time].[Weekly].[_Nativized_Member_Time_Weekly_Week_] as '[Time].[Weekly].DefaultMember'\n"
                + "  set [_Nativized_Set_Time_Weekly_Week_] as '{[Time].[Weekly].[_Nativized_Member_Time_Weekly_Week_]}'\n"
                + "  member [Time].[Time].[_Nativized_Sentinel_Time_Time_Year_] as '101010'\n"
                + "  member [Gender].[Gender].[_Nativized_Sentinel_Gender_Gender_(All)_] as '101010'\n"
                + "select NativizeSet(Crossjoin([_Nativized_Set_Time_Weekly_Week_], {[Gender].[Gender].[M]})) ON COLUMNS\n"
                + "from [sales]\n");
        } finally {
            connection.close();
        }
    }

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS, value = "false", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testMultipleHierarchySsasFalse(Context<?> context) {


        // Ssas compatible: [time.weekly].week
        assertQueryIsReWritten(context.getConnectionWithDefaultRole(),
            "select nativizeSet(crossjoin( [time].[weekly].week.members, { gender.gender.m })) on 0 "
            + "from sales",
            "with member [Time].[Weekly].[_Nativized_Member_Time_Weekly_Week_] as '[Time].[Weekly].DefaultMember'\n"
            + "  set [_Nativized_Set_Time_Weekly_Week_] as '{[Time].[Weekly].[_Nativized_Member_Time_Weekly_Week_]}'\n"
            + "  member [Time].[Time].[_Nativized_Sentinel_Time_Time_Year_] as '101010'\n"
            + "  member [Gender].[Gender].[_Nativized_Sentinel_Gender_Gender_(All)_] as '101010'\n"
            + "select NativizeSet(Crossjoin([_Nativized_Set_Time_Weekly_Week_], {[Gender].[Gender].[M]})) ON COLUMNS\n"
            + "from [sales]\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testComplexCrossjoinAggInMiddle(Context<?> context) {
        checkNative(context,
            "WITH\n"
            + "\tMEMBER [Time].[Time].[COG_OQP_USR_Aggregate(Time Values)] AS "
            + "'IIF([Measures].CURRENTMEMBER IS [Measures].[Unit Sales], ([Time].[Time].[1997], [Measures].[Unit Sales]), ([Time].[Time].[1997]))',\n"
            + "\tSOLVE_ORDER = 4 MEMBER [Store Type].[Store Type].[COG_OQP_INT_umg1] AS "
            + "'IIF([Measures].CURRENTMEMBER IS [Measures].[Unit Sales], ([Store Type].[Store Type].[COG_OQP_INT_m2], [Measures].[Unit Sales]), "
            + "AGGREGATE({[Store Type].[Store Type].[Store Type].MEMBERS}))',\n"
            + "\tSOLVE_ORDER = 8 MEMBER [Store Type].[Store Type].[COG_OQP_INT_m2] AS "
            + "'AGGREGATE({[Store Type].[Store Type].[Store Type].MEMBERS}, [Measures].[Unit Sales])',\n"
            + "\tSOLVE_ORDER = 8 \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s9] AS 'CROSSJOIN({[Marital Status].[Marital Status].[Marital Status].MEMBERS}, {[COG_OQP_INT_s8], [COG_OQP_INT_s6]})' \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s8] AS 'CROSSJOIN({[Store Type].[Store Type].[Store Type].MEMBERS}, [COG_OQP_INT_s7])' \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s7] AS 'CROSSJOIN({[Promotions].[Promotions].MEMBERS}, "
            + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Pearl].[Pearl Imported Beer]})' \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s6] AS 'CROSSJOIN({[Store Type].[Store Type].[COG_OQP_INT_umg1]}, [COG_OQP_INT_s1])' \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s5] AS 'CROSSJOIN({[Time].[Time].[COG_OQP_USR_Aggregate(Time Values)]}, [COG_OQP_INT_s4])' \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s4] AS 'CROSSJOIN({[Gender].[Gender].DEFAULTMEMBER}, [COG_OQP_INT_s3])' \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s3] AS 'CROSSJOIN({[Marital Status].[Marital Status].DEFAULTMEMBER}, [COG_OQP_INT_s2])' \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s2] AS 'CROSSJOIN({[Store Type].[Store Type].DEFAULTMEMBER}, [COG_OQP_INT_s1])' \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s11] AS 'CROSSJOIN({[Gender].[Gender].[Gender].MEMBERS}, [COG_OQP_INT_s10])' \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s10] AS 'CROSSJOIN({[Marital Status].[Marital Status].[Marital Status].MEMBERS}, [COG_OQP_INT_s8])' \n"
            + "SET\n"
            + "\t[COG_OQP_INT_s1] AS 'CROSSJOIN({[Promotion Name].DEFAULTMEMBER}, "
            + "{[Product].[Product].[Drink].[Alcoholic Beverages].[Beer and Wine].[Beer].[Pearl].[Pearl Imported Beer]})' \n"
            + "SELECT\n"
            + "\t{[Measures].[Unit Sales]} DIMENSION PROPERTIES PARENT_LEVEL,\n"
            + "\tCHILDREN_CARDINALITY,\n"
            + "\tPARENT_UNIQUE_NAME ON AXIS(0),\n"
            + "NativizeSet(\n"
            + "\t{\n"
            + "CROSSJOIN({[Time].[Time].[1997]}, CROSSJOIN({[Gender].[Gender].[Gender].MEMBERS}, [COG_OQP_INT_s9])),\n"
            + "\t[COG_OQP_INT_s5]}\n"
            + ")\n"
            + "ON AXIS(1) \n"
            + "FROM\n"
            + "\t[Sales] ");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testTopCountDoesNotGetTransformed(Context<?> context) {
        assertQueryIsReWritten(context.getConnectionWithDefaultRole(),
            "select "
            + "   NativizeSet(Crossjoin([Gender].[Gender].[Gender].members,"
            + "TopCount({[Marital Status].[Marital Status].[Marital Status].members},1,[Measures].[Unit Sales]))"
            + " ) on 0,"
            + "{[Measures].[Unit Sales]} on 1 FROM [Sales]",
            "with member [Gender].[Gender].[_Nativized_Member_Gender_Gender_Gender_] as '[Gender].[Gender].DefaultMember'\n"
            + "  set [_Nativized_Set_Gender_Gender_Gender_] as '{[Gender].[Gender].[_Nativized_Member_Gender_Gender_Gender_]}'\n"
            + "  member [Gender].[Gender].[_Nativized_Sentinel_Gender_Gender_(All)_] as '101010'\n"
            + "select NON EMPTY NativizeSet(Crossjoin([_Nativized_Set_Gender_Gender_Gender_], "
            + "TopCount({[Marital Status].[Marital Status].[Marital Status].Members}, 1, [Measures].[Unit Sales]))) ON COLUMNS,\n"
            + "  NON EMPTY {[Measures].[Unit Sales]} ON ROWS\n"
            + "from [Sales]\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testCrossjoinWithFilter(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select\n"
            + "NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS,   \n"
            + "NON EMPTY NativizeSet(Crossjoin({[Time].[Time].[1997]}, "
            + "Filter({[Gender].[Gender].[Gender].Members}, ([Measures].[Unit Sales] < 131559)))) ON ROWS \n"
            + "from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Time].[Time].[1997], [Gender].[Gender].[F]}\n"
            + "Row #0: 131,558\n");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "10000", type = Integer.class)
    void testEvaluationIsNonNativeWhenBelowHighcardThreshoold(Context<?> context) {
        SqlPattern[] patterns = {
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select `customer`.`gender` as `c0` "
                + "from `customer` as `customer`, `sales_fact_1997` as `sales_fact_1997` "
                + "where `sales_fact_1997`.`customer_id` = `customer`.`customer_id` "
                + "and `customer`.`marital_status` = 'S' "
                + "group by `customer`.`gender` order by 1 ASC", 251)
        };
        String mdxQuery =
            "select non empty NativizeSet("
            + "Crossjoin([Gender].[Gender].members,{[Time].[1997]})) on 0 "
            + "from [Warehouse and Sales] "
            + "where [Marital Status].[Marital Status].[S]";
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(), mdxQuery).expectNoSql(patterns).verify();
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testCalculatedLevelsDoNotCauseException(Context<?> context) {
        String mdx =
            "SELECT "
            + "  Nativizeset"
            + "  ("
            + "    {"
            + "      [Store].Levels(0).MEMBERS"
            + "    }"
            + "  ) ON COLUMNS"
            + " FROM [Sales]";
        checkNotNative(context,mdx);
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testAxisWithArityOneIsNotNativelyEvaluated(Context<?> context) {
        SqlPattern[] patterns = {
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select `promotion`.`media_type` as `c0` "
                + "from `promotion` as `promotion`, `sales_fact_1997` as `sales_fact_1997` "
                + "where `sales_fact_1997`.`promotion_id` = `promotion`.`promotion_id` "
                + "group by `promotion`.`media_type` "
                + "order by Iif(`promotion`.`media_type` IS NULL, 1, 0), "
                + "`promotion`.`media_type` ASC", 296)
        };
        String query =
            "select "
            + "  NON EMPTY "
            + "  NativizeSet("
            + "    Except("
            + "      {[Promotion Media].[Promotion Media].Members},\n"
            + "      {[Promotion Media].[Bulk Mail],[Promotion Media].[All Media].[Daily Paper]}"
            + "    )"
            + "  ) ON COLUMNS,"
            + "  NON EMPTY "
            + "  {[Measures].[Unit Sales]} ON ROWS "
            + "from [Sales] \n"
            + "where [Time].[1997]";
        SqlAssert.forQuery(context.getConnectionWithDefaultRole(), query).expectNoSql(patterns).verify();
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testAxisWithNamedSetArityOneIsNotNativelyEvaluated(Context<?> context) {
        checkNotNative(context,
            "with "
            + "set [COG_OQP_INT_s1] as "
            + "'Intersect({[Gender].[Gender].Members}, {[Gender].[Gender].[M]})' "
            + "select NON EMPTY "
            + "NativizeSet([COG_OQP_INT_s1]) ON COLUMNS "
            + "from [Sales]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "19", type = Integer.class)
    void testOneAxisHighAndOneLowGetsNativeEvaluation(Context<?> context) {
        checkNative(context,
            "select NativizeSet("
            + "Crossjoin([Gender].[Gender].members,"
            + "[Marital Status].[Marital Status].members)) on 0,"
            + "NativizeSet("
            + "Crossjoin([Store].[Store State].members,[Time].[Year].members)) on 1 "
            + "from [Warehouse and Sales]");
    }

    @Disabled //has not been fixed during creating Daanse project
    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void disabled_testAggregatesInSparseResultsGetSortedCorrectly(Context<?> context) {
        checkNative(context,
            "select non empty NativizeSet("
            + "Crossjoin({[Store Type].[Store Type].members,[Store Type].[all store types]},"
            + "{ [Promotion Media].[Media Type].members }"
            + ")) on 0 from sales");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testLeafMembersOfParentChildDimensionAreNativelyEvaluated(Context<?> context) {
        checkNative(context,
            "SELECT"
            + " NON EMPTY "
            + "NativizeSet(Crossjoin("
            + "{"
            + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Gabriel Walton],"
            + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Bishop Meastas],"
            + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Paula Duran],"
            + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Margaret Earley],"
            + "[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Pat Chin].[Elizabeth Horne]"
            + "},"
            + "[Store].[Store Name].members"
            + ")) on 0 from hr");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testAggregatedCrossjoinWithZeroMembersInNativeList(Context<?> context) {
        checkNative(context,
            "with"
            + " member [gender].[gender].[agg] as"
            + "  'aggregate({[gender].[gender].[gender].members},[measures].[unit sales])'"
            + " member [Marital Status].[Marital Status].[agg] as"
            + "  'aggregate({[Marital Status].[Marital Status].[Marital Status].members},[measures].[unit sales])'"
            + "select"
            + " non empty "
            + " NativizeSet("
            + "Crossjoin("
            + "{[Marital Status].[Marital Status].[Marital Status].members,[Marital Status].[Marital Status].[agg]},"
            + "{[Gender].[Gender].[Gender].members,[gender].[gender].[agg]}"
            + ")) on 0 "
            + " from sales "
            + " where [Store].[Store].[Canada].[BC].[Vancouver].[Store 19]");
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testCardinalityQueriesOnlyExecuteOnce(Context<?> context) {
        SqlPattern[] patterns = {
            new SqlPattern(
                DatabaseProduct.ORACLE,
                "select count(*) as \"c0\" "
                + "from (select "
                + "distinct \"customer\".\"gender\" as \"c0\" "
                + "from \"customer\" \"customer\") \"init\"",
                108),
            new SqlPattern(
                DatabaseProduct.ACCESS,
                "select count(*) as `c0` "
                + "from (select "
                + "distinct `customer`.`gender` as `c0` "
                + "from `customer` as `customer`) as `init`",
                108)
        };
        String mdxQuery =
            "select"
            + " non empty"
            + " NativizeSet(Crossjoin("
            + "[Gender].[Gender].members,[Marital Status].[Marital Status].members"
            + ")) on 0 from Sales";
        Connection connection = context.getConnectionWithDefaultRole();
        connection.execute(connection.parseQuery(mdxQuery));
        SqlAssert.forQuery(connection, mdxQuery).keepCache().expectNoSql(patterns).verify();
    }

    @Test
    @RolapConfig(key = ConfigConstants.NATIVIZE_MIN_THRESHOLD, value = "0", type = Integer.class)
    void testSingleLevelDotMembersIsNativelyEvaluated(Context<?> context) {
        String mdx1 =
            "with member [Customers].[agg] as '"
            + "AGGREGATE({[Customers].[name].MEMBERS}, [Measures].[Unit Sales])'"
            + "select non empty NativizeSet({{[Customers].[name].members}, {[Customers].[agg]}}) on 0,"
            + "non empty NativizeSet("
            + "Crossjoin({[Gender].[Gender].[M]},"
            + "[Measures].[Unit Sales])) on 1 "
            + "from Sales";
        String mdx2 =
            "select non empty NativizeSet({[Customers].[name].members}) on 0,"
            + "non empty NativizeSet("
            + "Crossjoin({[Gender].[Gender].[M]},"
            + "[Measures].[Unit Sales])) on 1 "
            + "from Sales";

        String sql = "select \"customer\".\"country\" as \"c0\", "
            + "\"customer\".\"state_province\" as \"c1\", "
            + "\"customer\".\"city\" as \"c2\", "
            + "\"customer\".\"customer_id\" as \"c3\", \"fname\" || ' ' || \"lname\" as \"c4\", "
            + "\"fname\" || ' ' || \"lname\" as \"c5\", \"customer\".\"gender\" as \"c6\", "
            + "\"customer\".\"marital_status\" as \"c7\", "
            + "\"customer\".\"education\" as \"c8\", \"customer\".\"yearly_income\" as \"c9\" "
            + "from \"customer\" \"customer\", \"sales_fact_1997\" \"sales_fact_1997\" "
            + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" "
            + "and (\"customer\".\"gender\" = 'M') "
            + "group by \"customer\".\"country\", \"customer\".\"state_province\", "
            + "\"customer\".\"city\", \"customer\".\"customer_id\", \"fname\" || ' ' || \"lname\", "
            + "\"customer\".\"gender\", \"customer\".\"marital_status\", \"customer\".\"education\", "
            + "\"customer\".\"yearly_income\" "
            + "order by \"customer\".\"country\" ASC NULLS LAST, "
            + "\"customer\".\"state_province\" ASC NULLS LAST, \"customer\".\"city\" ASC NULLS LAST, "
            + "\"fname\" || ' ' || \"lname\" ASC NULLS LAST";
        SqlPattern oraclePattern =
            new SqlPattern(DatabaseProduct.ORACLE, sql, sql.length());
        Connection connection = context.getConnectionWithDefaultRole();
        SqlAssert.forQuery(connection, mdx1).expectSql(new SqlPattern[]{oraclePattern}).verify();
        SqlAssert.forQuery(connection, mdx2).expectSql(new SqlPattern[]{oraclePattern}).verify();
    }

    // ~ ====== Helper methods =================================================

    private void checkNotNative(Context<?> context, String mdx) {
        final String mdx2 = removeNativize(mdx);
        final Result result = executeQuery(mdx2, context.getConnectionWithDefaultRole());
        checkNotNative(context, mdx, result);
    }

    private void checkNative(Context<?> context, String mdx) {
        final String mdx2 = removeNativize(mdx);
        final Result result = executeQuery(mdx2, context.getConnectionWithDefaultRole());
        checkNative(context, mdx, result);
    }

    private static String removeNativize(String mdx) {
        String mdxWithoutNativize = mdx.replaceAll("(?i)NativizeSet", "");
        assertFalse(
            mdx.equals(mdxWithoutNativize), "Query does use NativizeSet");
        return mdxWithoutNativize;
    }



    private void assertQueryIsReWritten(
        Connection connection,
        final String query,
        final String expectedQuery)
    {
        ExecutionImpl execution = new ExecutionImpl(
            ((org.eclipse.daanse.olap.api.connection.Connection) connection).getInternalStatement(),
            Optional.of(Duration.ofMinutes(5)));
        ExecutionMetadata metadata = ExecutionMetadata.of(NativizeSetFunDefTest.class.getName(), NativizeSetFunDefTest.class.getName(), null, 0);
        String actualOutput =
            ExecutionContext.where(
                execution.asContext().createChild(metadata, Optional.empty()),
                () -> {
                    return connection.parseQuery(query).toString();
                }
            );
        if (!Util.NL.equals("\n")) {
            actualOutput = actualOutput.replace(Util.NL, "\n");
        }
        assertEquals(expectedQuery, actualOutput);
    }
}
