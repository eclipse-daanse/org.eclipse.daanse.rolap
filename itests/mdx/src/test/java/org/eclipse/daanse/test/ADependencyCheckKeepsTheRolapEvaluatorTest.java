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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * Under the dependency check ({@code testExpDependencies}) a ROLAP query still runs the ROLAP evaluator:
 * the check is a mode of the root, not an evaluator of its own. It used to be one that
 * ResultImpl put in place of RolapEvaluator, so every cast to RolapEvaluator on the SQL side -- the slicer
 * analysis, the native filter, the constraint factory -- failed as soon as the switch was on, and the nine
 * RolapEvaluator hooks (compound slicer, slicer predicates, the cube of the measure) fell away.
 */
@RolapContextTest(FoodmartTestInstance.class)
@RolapConfig(key = ConfigConstants.TEST_EXP_DEPENDENCIES, value = "1", type = Integer.class)
class ADependencyCheckKeepsTheRolapEvaluatorTest {

    /** Drink 24,597 + Food 191,940: the compound slicer is answered by the ROLAP slicer predicate. */
    @Test
    void aCompoundSlicerIsAnsweredUnderTheCheck(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        Result result = connection.execute(connection.parseQuery(
                "select {[Measures].[Unit Sales]} on 0 from [Sales] where {[Product].[Drink], [Product].[Food]}"));
        assertThat(result.getCell(new int[] { 0 }).getValue()).isEqualTo(216537.0);
    }

    /** A native filter under a compound slicer reads the slicer members off the ROLAP evaluator. */
    @Test
    void aNativeFilterUnderACompoundSlicerRunsUnderTheCheck(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        Result result = connection.execute(connection.parseQuery(
                "select NON EMPTY Filter([Store].[Store Name].Members, [Measures].[Unit Sales] > 20000) on 0"
                        + " from [Sales] where {[Product].[Drink], [Product].[Food]}"));
        assertThat(result.getAxes()[0].getPositions()).isNotEmpty();
        for (int i = 0; i < result.getAxes()[0].getPositions().size(); i++) {
            assertThat(((Number) result.getCell(new int[] { i }).getValue()).doubleValue()).isGreaterThan(20000);
        }
    }
}
