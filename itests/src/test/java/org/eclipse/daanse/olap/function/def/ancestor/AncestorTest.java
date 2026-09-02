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
package org.eclipse.daanse.olap.function.def.ancestor;

import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
public class AncestorTest {

	@Test
	void testAncestor(Context<?> context) {
		Connection con = context.getConnectionWithDefaultRole();
		assertThatAxis(con, "Sales",
				"Ancestor([Store].[USA].[CA].[Los Angeles],[Store Country])").returns("[Store].[Store].[USA]");

		assertThatAxis(con, "Sales", "Ancestor([Store].[USA].[CA].[Los Angeles],[Promotions].[Promotion Name])")
				.throwsMessage("Error while executing query");
	}

	@Test
	//
	void testAncestorNumeric(Context<?> context) {
		Connection con = context.getConnectionWithDefaultRole();

		assertThatAxis(con, "Sales", "Ancestor([Store].[USA].[CA].[Los Angeles],1)").returns("[Store].[Store].[USA].[CA]");

		assertThatAxis(con, "Sales", "Ancestor([Store].[USA].[CA].[Los Angeles], 0)").returns("[Store].[Store].[USA].[CA].[Los Angeles]");

		assertThatAxis(con, "Sales Ragged", "Ancestor([Store].[All Stores].[Vatican], 1)").returns("[Store].[Store].[All Stores]");

		assertThatAxis(con, "Sales Ragged", "Ancestor([Store].[USA].[Washington], 1)").returns("[Store].[Store].[USA]");

		// complicated way to say "1".
		assertThatAxis(con, "Sales Ragged", "Ancestor([Store].[USA].[Washington], 7 * 6 - 41)").returns("[Store].[Store].[USA]");

		// Ancestor at 2 must be null
		assertThatAxis(con, "Sales Ragged", "Ancestor([Store].[All Stores].[Vatican], 2)").returns("");

		// Ancestor at -5 must be null
		assertThatAxis(con, "Sales Ragged", "Ancestor([Store].[All Stores].[Vatican], -5)").returns("");
	}

	@Test
	void testAncestorHigher(Context<?> context) {
		// MSOLAP returns null
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Ancestor([Store].[USA],[Store].[Store City])").returns("");
	}

	@Test
	void testAncestorSameLevel(Context<?> context) {
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
				"Ancestor([Store].[Canada],[Store].[Store Country])").returns("[Store].[Store].[Canada]");
	}

	@Test
	void testAncestorWrongHierarchy(Context<?> context) {
		// MSOLAP gives error "Formula error - dimensions are not
		// valid (they do not match) - in the Ancestor function"
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Ancestor([Gender].[M],[Store].[Store Country])")
				.throwsMessage("Error while executing query");
	}

	@Test
	void testAncestorAllLevel(Context<?> context) {
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Ancestor([Store].[USA].[CA],[Store].Levels(0))").returns("[Store].[Store].[All Stores]");
	}

	@Test
	void testAncestorWithHiddenParent(Context<?> context) {
		// final Context<?> testContext<?> =
		// getContext().withCube( "Sales Ragged" );
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales Ragged",
				"Ancestor([Store].[All Stores].[Israel].[Haifa], [Store].[Store Country])").returns("[Store].[Store].[Israel]");
	}

	@Test
	void testAncestorDepends(Context<?> context) {
		Connection con = context.getConnectionWithDefaultRole();
		assertThatExpr(con, "Sales", "Ancestor([Store].CurrentMember, [Store].[Store Country]).Name").dependsOn("[Store].[Store]");

		assertThatExpr(con, "Sales", "Ancestor([Store].[All Stores].[USA], [Store].CurrentMember.Level).Name").dependsOn("[Store].[Store]");

		assertThatExpr(con, "Sales", "Ancestor([Store].[All Stores].[USA], [Store].[Store Country]).Name").dependsOn();

		assertThatExpr(con, "Sales", "Ancestor([Store].CurrentMember, 2+1).Name").dependsOn("[Store].[Store]");
	}

}
