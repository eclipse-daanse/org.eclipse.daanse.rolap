/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2015-2017 Hitachi Vantara.
 * All Rights Reserved.
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

package org.eclipse.daanse.rolap.common;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.type.NullType;
import org.eclipse.daanse.olap.fun.MondrianEvaluationException;
import org.eclipse.daanse.olap.query.component.NumericLiteralImpl;
import org.eclipse.daanse.olap.query.component.StringLiteralImpl;
import org.eclipse.daanse.olap.util.type.TypeWrapperExp;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author Andrey Khayrutdinov
 */
class NumberSqlCompilerTest {

    private static RolapNativeSql.NumberSqlCompiler compiler;

    @BeforeAll
    public static void beforeAll() throws Exception {
        Dialect dialect = mock(Dialect.class);
        when(dialect.getDialectName())
            .thenReturn("mysql");

        when(dialect.quoteDecimalLiteral("1"))
            .thenReturn(new StringBuilder("1"));

        when(dialect.quoteDecimalLiteral("+1.01"))
        .thenReturn(new StringBuilder("+1.01"));

        when(dialect.quoteDecimalLiteral("-.00001"))
        .thenReturn(new StringBuilder("-.00001"));

        when(dialect.quoteDecimalLiteral("-1"))
        .thenReturn(new StringBuilder("-1"));

        SqlQuery query = mock(SqlQuery.class);
        when(query.getDialect()).thenReturn(dialect);

        RolapNativeSql sql = new RolapNativeSql(query, null, null, null);
        compiler = sql.new NumberSqlCompiler();
    }

    @AfterAll
    public static void afterAll() throws Exception {
        compiler = null;
    }

    @Test
    void testRejectsNonLiteral() {
        Expression exp = new TypeWrapperExp(NullType.INSTANCE);
        assertNull(compiler.compile(exp));
    }

    @Test
    void testAcceptsNumeric() {
        Expression exp = NumericLiteralImpl.create(BigDecimal.ONE);
        assertNotNull(compiler.compile(exp));
    }

    @Test
    void testAcceptsString_Int() {
        checkAcceptsString("1");
    }

    @Test
    void testAcceptsString_Negative() {
        checkAcceptsString("-1");
    }

    @Test
    void testAcceptsString_ExplicitlyPositive() {
        checkAcceptsString("+1.01");
    }

    @Test
    void testAcceptsString_NoIntegerPart() {
        checkAcceptsString("-.00001");
    }

    private void checkAcceptsString(String value) {
        Expression exp = StringLiteralImpl.create(value);
        assertNotNull(value, compiler.compile(exp).toString());
    }


    @Test
    void testRejectsString_SelectStatement() {
        checkRejectsString("(select 100)");
    }

    @Test
    void testRejectsString_NaN() {
        checkRejectsString("NaN");
    }

    @Test
    void testRejectsString_Infinity() {
        checkRejectsString("Infinity");
    }

    @Test
    void testRejectsString_TwoDots() {
        checkRejectsString("1.0.");
    }

    @Test
    void testRejectsString_OnlyDot() {
        checkRejectsString(".");
    }

    @Test
    void testRejectsString_DoubleNegation() {
        checkRejectsString("--1.0");
    }

    private void checkRejectsString(String value) {
        Expression exp = StringLiteralImpl.create(value);
        try {
            compiler.compile(exp);
        } catch (MondrianEvaluationException e) {
            return;
        }
        fail("Expected to get MondrianEvaluationException for " + value);
    }
}
