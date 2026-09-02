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
package org.eclipse.daanse.olap.function.def.periodstodate.xtd;

import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatSetExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
public class XtdFunDefTest {

	private static final String TimeWeekly = "[Time].[Weekly]";

	@Test
	void testYtd(Context<?> context) {

		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Ytd()")
            .returns( "[Time].[Time].[1997]");

		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Ytd([Time].[1997].[Q3])")
            .returns( """
				[Time].[Time].[1997].[Q1]
				[Time].[Time].[1997].[Q2]
				[Time].[Time].[1997].[Q3]""");

		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Ytd([Time].[1997].[Q2].[4])")
            .returns( """
				[Time].[Time].[1997].[Q1].[1]
				[Time].[Time].[1997].[Q1].[2]
				[Time].[Time].[1997].[Q1].[3]
				[Time].[Time].[1997].[Q2].[4]""");

		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Ytd([Store])")
				.throwsMessage("Argument to function 'Ytd' must belong to Time hierarchy");

		assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales", "Ytd()").dependsOn("[Time].[Time]", TimeWeekly);

		assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales", "Ytd([Time].[1997].[Q2])").dependsOn();
	}

	/**
	 * Testcase for <a href="http://jira.pentaho.com/browse/MONDRIAN-458"> bug
	 * MONDRIAN-458, "error deducing type of Ytd/Qtd/Mtd functions within
	 * Generate"</a>.
	 */
	@Test
	void testGeneratePlusXtd(Context<?> context) {

		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", """
				generate(
				  {[Time].[1997].[Q1].[2], [Time].[1997].[Q3].[7]},
				 {Ytd( [Time].[Time].currentMember)})""")
            .returns( """
				[Time].[Time].[1997].[Q1].[1]
				[Time].[Time].[1997].[Q1].[2]
				[Time].[Time].[1997].[Q1].[3]
				[Time].[Time].[1997].[Q2].[4]
				[Time].[Time].[1997].[Q2].[5]
				[Time].[Time].[1997].[Q2].[6]
				[Time].[Time].[1997].[Q3].[7]""");

		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", """
				generate(
				  {[Time].[1997].[Q1].[2], [Time].[1997].[Q3].[7]},
				 {Ytd( [Time].[Time].currentMember)}, ALL)""")
            .returns( """
				[Time].[Time].[1997].[Q1].[1]
				[Time].[Time].[1997].[Q1].[2]
				[Time].[Time].[1997].[Q1].[1]
				[Time].[Time].[1997].[Q1].[2]
				[Time].[Time].[1997].[Q1].[3]
				[Time].[Time].[1997].[Q2].[4]
				[Time].[Time].[1997].[Q2].[5]
				[Time].[Time].[1997].[Q2].[6]
				[Time].[Time].[1997].[Q3].[7]""");

		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
				"count(generate({[Time].[1997].[Q4].[11]}, {Qtd( [Time].[Time].currentMember)}))").returns( 2, 0);

		assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
				"count(generate({[Time].[1997].[Q4].[11]}, {Mtd( [Time].[Time].currentMember)}))").returns( 1, 0);
	}

	@Test
	void testQtd(Context<?> context) {
		// zero args
		assertThatQuery(context.getConnectionWithDefaultRole(), """
				with member [Measures].[Foo] as ' SetToStr(Qtd()) '
				select {[Measures].[Foo]} on columns
				from [Sales]
				where [Time].[1997].[Q2].[5]""")
            .returnsGrid( """
				Axis #0:
				{[Time].[Time].[1997].[Q2].[5]}
				Axis #1:
				{[Measures].[Foo]}
				Row #0: {[Time].[Time].[1997].[Q2].[4], [Time].[Time].[1997].[Q2].[5]}
				""");

		// one arg, a month
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Qtd([Time].[1997].[Q2].[5])")
            .returns(
				"[Time].[Time].[1997].[Q2].[4]\n" + "[Time].[Time].[1997].[Q2].[5]");

		// one arg, a quarter
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Qtd([Time].[1997].[Q2])")
            .returns( "[Time].[Time].[1997].[Q2]");

		// one arg, a year
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Qtd([Time].[1997])")
            .returns( "");

		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Qtd([Store])")
				.throwsMessage("Argument to function 'Qtd' must belong to Time hierarchy");
	}

	@Test
	void testMtd(Context<?> context) {
		// zero args
		assertThatQuery(context.getConnectionWithDefaultRole(), """
				with member [Measures].[Foo] as ' SetToStr(Mtd()) '
				select {[Measures].[Foo]} on columns
				from [Sales]
				where [Time].[1997].[Q2].[5]""")
            .returnsGrid( """
				Axis #0:
				{[Time].[Time].[1997].[Q2].[5]}
				Axis #1:
				{[Measures].[Foo]}
				Row #0: {[Time].[Time].[1997].[Q2].[5]}
				""");

		// one arg, a month
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Mtd([Time].[1997].[Q2].[5])")
            .returns( "[Time].[Time].[1997].[Q2].[5]");

		// one arg, a quarter
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Mtd([Time].[1997].[Q2])")
            .returns( "");

		// one arg, a year
		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Mtd([Time].[1997])")
            .returns( "");

		assertThatAxis(context.getConnectionWithDefaultRole(), "Sales", "Mtd([Store])")
				.throwsMessage("Argument to function 'Mtd' must belong to Time hierarchy");
	}

}
