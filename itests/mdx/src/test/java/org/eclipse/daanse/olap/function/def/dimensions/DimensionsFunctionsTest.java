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
package org.eclipse.daanse.olap.function.def.dimensions;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.FunDependencies;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.olap.function.TestResources;

@RolapContextTest(FoodmartTestInstance.class)
public class DimensionsFunctionsTest {

	@Test
	void testDimensionsNumeric(Context<?> context) {
		FunDependencies.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(2).Name").dependsOn();
		FunDependencies.assertThatMemberExpr(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(3).CurrentMember")
				.dependsOn(TestResources.hiersExcept());
		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(2).Name").returns("Store Size in SQFT");
		// bug 1426134 -- Dimensions(0) throws 'Index '0' out of bounds'
		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(0).Name").returns("Measures");
		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(-1).Name").throwsMessage("Index '-1' out of bounds");
		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(100).Name").throwsMessage("Index '100' out of bounds");
		// Since Dimensions returns a Hierarchy, can apply CurrentMember.
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(3).CurrentMember").returns("[Store Type].[Store Type].[All Store Types]");
	}

	@Test
	void testDimensionsString(Context<?> context) {
		FunDependencies.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(\"foo\").UniqueName").dependsOn();
		FunDependencies.assertThatMemberExpr(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(\"foo\").CurrentMember")
				.dependsOn(TestResources.hiersExcept());
		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(\"Store\").UniqueName").returns("[Store].[Store]");
		// Since Dimensions returns a Hierarchy, can apply Children.
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Dimensions(\"Store\").Children").returns("""
				[Store].[Store].[Canada]
				[Store].[Store].[Mexico]
				[Store].[Store].[USA]""");
	}

	@Test
	void testDimensionsDepends(Context<?> context) {
		final String expression = """
				Crossjoin(
				{Dimensions("Measures").CurrentMember.Hierarchy.CurrentMember},
				{Dimensions("Product")})""";
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", expression).returns("{[Measures].[Unit Sales], [Product].[Product].[All Products]}");
		FunDependencies.assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales", expression)
				.dependsOn(TestResources.hiersExcept());
	}

}
