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

package org.eclipse.daanse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.LocaleUtils;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * Test suite for internalization and localization.
 *
 * @see mondrian.util.FormatTest
 *
 * @author jhyde
 * @since September 22, 2005
 */
@RolapContextTest(FoodmartTestInstance.class)
class I18nTest {
    public static final char Euro = '\u20AC';
    public static final char Nbsp = ' ';
    public static final char EA = '\u00e9'; // e acute
    public static final char UC = '\u00FB'; // u circumflex

    @Test
    void testAutoFrench(Context<?> context) {
        // Create a connection in French.
        String localeName = "fr_FR";
        String resultString = "12" + Nbsp + "345,67";
        assertFormatNumber(context, localeName, resultString);
    }

    @Test
    void testAutoSpanish(Context<?> context) {
        // Format a number in (Peninsular) spanish.
        assertFormatNumber(context, "es", "12.345,67");
    }

    @Test
    void testAutoMexican(Context<?> context) {
        // Format a number in Mexican spanish.
        assertFormatNumber(context, "es_MX", "12,345.67");
    }

    private void assertFormatNumber(Context<?> context, String localeName, String resultString) {
        //final Util.PropertyList properties =
        //    TestUtil.getConnectionProperties().clone();
        //properties.put(RolapConnectionProperties.Locale.name(), localeName);
        //context.setProperty(RolapConnectionProperties.Locale.name(), localeName);
        Connection connection = context.getConnection(new ConnectionProps(
		List.of("Administrator"), true, LocaleUtils.toLocale(localeName),
		Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty()));

        Query query = connection.parseQuery(
            "WITH MEMBER [Measures].[Foo] AS ' 12345.67 ',\n"
            + " FORMAT_STRING='#,###.00'\n"
            + "SELECT {[Measures].[Foo]} ON COLUMNS\n"
            + "FROM [Sales]");
        Result result = connection.execute(query);
        String actual = toString(result);
        assertEquals(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Foo]}\n"
            + "Row #0: " + resultString + "\n",
            actual);
    }

    /**
     * Converts a {@link Result} to text in traditional format.
     *
     * @param result Query result
     * @return Result as text
     */
    private static String toString(Result result) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        result.print(pw);
        pw.flush();
        return sw.toString();
    }
}

// End I18nTest.java

