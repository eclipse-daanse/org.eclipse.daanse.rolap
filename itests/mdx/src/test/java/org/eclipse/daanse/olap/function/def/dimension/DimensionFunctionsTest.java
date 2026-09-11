/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * For more information please visit the Project: Hitachi Vantara - Mondrian
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
package org.eclipse.daanse.olap.function.def.dimension;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
public class DimensionFunctionsTest {

	@Test
	void testDimensionHierarchy(Context<?> context) {
		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Time].Dimension.Name").returns("Time");
	}

	@Test
	void testLevelDimension(Context<?> context) {
		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Time].[Year].Dimension.UniqueName").returns("[Time]");
	}

	@Test
	void testMemberDimension(Context<?> context) {
		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1997].[Q2].Dimension.UniqueName").returns("[Time]");
	}


}
