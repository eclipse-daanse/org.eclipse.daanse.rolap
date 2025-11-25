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

package org.eclipse.daanse.rolap.function.def.visualtotals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * <code>VisualTotalsTest</code> tests the internal functions defined in
 * {@link VisualTotalsFunDef}. Right now, only tests substitute().
 *
 * @author efine
 */
class VisualTotalsTest {
	@Test
    void testSubstituteEmpty() {
        final String actual = VisualTotalsCalc.substitute("", "anything");
        final String expected = "";
        assertEquals(expected, actual);
    }

	@Test
    void testSubstituteOneStarOnly() {
        final String actual = VisualTotalsCalc.substitute("*", "anything");
        final String expected = "anything";
        assertEquals(expected, actual);
    }

	@Test
    void testSubstituteOneStarBegin() {
        final String actual =
        VisualTotalsCalc.substitute("* is the word.", "Grease");
        final String expected = "Grease is the word.";
        assertEquals(expected, actual);
    }

	@Test
    void testSubstituteOneStarEnd() {
        final String actual =
            VisualTotalsCalc.substitute(
                "Lies, damned lies, and *!", "statistics");
        final String expected = "Lies, damned lies, and statistics!";
        assertEquals(expected, actual);
    }

	@Test
    void testSubstituteTwoStars() {
        final String actual = VisualTotalsCalc.substitute("**", "anything");
        final String expected = "*";
        assertEquals(expected, actual);
    }

	@Test
    void testSubstituteCombined() {
        final String actual =
            VisualTotalsCalc.substitute(
                "*: see small print**** for *", "disclaimer");
        final String expected = "disclaimer: see small print** for disclaimer";
        assertEquals(expected, actual);
    }

}
