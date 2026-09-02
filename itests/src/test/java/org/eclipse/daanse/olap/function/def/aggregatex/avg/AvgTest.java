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
public class AvgTest {

    @Test
	void testAvg(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
                "AVG({[Store].[All Stores].[USA].children})").returns("88,924");
	}

    @Test
	void testAvgNumeric(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
                "AVG({[Store].[All Stores].[USA].children},[Measures].[Store Sales])").returns("188,412.71");
	}

	// todo: testAvgWithNulls

}
