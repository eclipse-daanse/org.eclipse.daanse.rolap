/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.olap.function.def.aggregatex.avg;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class AvgFunDefTest {

    // MONDRIAN-2408 - Consumer wants (immutable) LIST in CrossJoinFunDef.compileCall(ResolvedFunCall, ExpCompiler)
    @Test
    void testIIfSetType_InCrossJoinAndAvg(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Avg(CROSSJOIN([Store Type].[Deluxe Supermarket],IIf(1 = 1, {[Store].[USA].[OR], [Store].[USA].[WA]}, {[Store]"
                + ".[Mexico], [Store].[USA].[CA]})), [Measures].[Store Sales])").returns(
            "81,031.12" );
    }

}
