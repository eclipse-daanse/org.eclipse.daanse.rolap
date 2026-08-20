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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.type.NullType;
import org.eclipse.daanse.olap.fun.DaanseEvaluationException;
import org.eclipse.daanse.olap.query.component.NumericLiteralImpl;
import org.eclipse.daanse.olap.query.component.StringLiteralImpl;
import org.eclipse.daanse.olap.util.type.TypeWrapperExp;
import org.eclipse.daanse.rolap.common.nativize.NativeSqlContext;
import org.eclipse.daanse.rolap.common.nativize.RolapNativeSql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author Andrey Khayrutdinov
 */
class NumberSqlCompilerTest {

    private static RolapNativeSql.NumberSqlCompiler compiler;

    @BeforeAll static void beforeAll() throws Exception {
        // The node channel emits builder literal nodes; the dialect feeds only the
        // regex-capability read at construction.
        Dialect dialect = mock(Dialect.class);
        when(dialect.name())
            .thenReturn("mysql");

        RolapNativeSql sql = new RolapNativeSql(
            NativeSqlContext.scratch(
                org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities.of(dialect)),
            null, null, null);
        compiler = sql.new NumberSqlCompiler();
    }

    @AfterAll static void afterAll() throws Exception {
        compiler = null;
    }

    @ParameterizedTest(name = "accepts a numeric literal \"{0}\"")
    @ValueSource(strings = { "1", "-1", "+1.01", "-.00001" })
    void acceptsNumericString(String value) {
        assertThat(compiler.compileNodeExpr(StringLiteralImpl.create(value)))
                .as(value).isNotNull();
    }

    @ParameterizedTest(name = "rejects non-literal \"{0}\"")
    @ValueSource(strings = { "(select 100)", "NaN", "Infinity", "1.0.", ".", "--1.0" })
    void rejectsNonNumericString(String value) {
        assertThatThrownBy(() ->
                compiler.compileNodeExpr(StringLiteralImpl.create(value)))
                .as("Expected to get DaanseEvaluationException for " + value)
                .isInstanceOf(DaanseEvaluationException.class);
    }
    
    @Test
    void rejectsNonLiteral() {
        Expression exp = new TypeWrapperExp(NullType.INSTANCE);
        assertThat(compiler.compileNodeExpr(exp)).isNull();
    }

    @Test
    void acceptsNumeric() {
        Expression exp = NumericLiteralImpl.create(BigDecimal.ONE);
        assertThat(compiler.compileNodeExpr(exp)).isNotNull();
    }
}
