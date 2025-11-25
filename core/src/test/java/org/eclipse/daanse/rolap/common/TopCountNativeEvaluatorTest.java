/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2015-2017 Hitachi Vantara and others
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.eclipse.daanse.mdx.model.api.expression.operation.FunctionOperationAtom;
import org.eclipse.daanse.mdx.model.api.expression.operation.OperationAtom;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.type.EmptyType;
import org.eclipse.daanse.olap.query.component.NumericLiteralImpl;
import org.eclipse.daanse.olap.util.type.TypeWrapperExp;
import org.junit.jupiter.api.Test;

/**
 * This class contains tests for some cases related to creating
 * native evaluator for {@code TOPCOUNT} function.
 *
 * @author Andrey Khayrutdinov
 * @see RolapNativeTopCount#createEvaluator(RolapEvaluator, FunctionDefinition, Expression[])
 */
class TopCountNativeEvaluatorTest {

    @Test
    void nonNativeWhenExplicitlyDisabled() throws Exception {
        RolapNativeTopCount nativeTopCount = new RolapNativeTopCount(false);

        assertThat(nativeTopCount.createEvaluator(null, null, null, true)).as("Native evaluator should not be created when "
            + "'daanse.native.topcount.enable' is 'false'").isNull();
    }

    @Test
    void nonNativeWhenContextIsInvalid() throws Exception {
        RolapNativeTopCount nativeTopCount = createTopCountSpy();
        doReturn(false).when(nativeTopCount)
            .isValidContext(any(RolapEvaluator.class));

        assertThat(nativeTopCount.createEvaluator(null, null, null, true)).as("Native evaluator should not be created when "
            + "evaluation context is invalid").isNull();
    }

    /**
     * For now, prohibit native evaluation of the function if has two
     * parameters. According to the specification, this means
     * the function should behave similarly to {@code HEAD} function.
     * However, native evaluation joins data with the fact table and if there
     * is no data there, then some records are ignored, what is not correct.
     *
     * @see <a href="http://jira.pentaho.com/browse/MONDRIAN-2394">MONDRIAN-2394</a>
     */
    @Test
    void nonNativeWhenTwoParametersArePassed() throws Exception {
        RolapNativeTopCount nativeTopCount = createTopCountSpy();
        doReturn(true).when(nativeTopCount)
            .isValidContext(any(RolapEvaluator.class));

        Expression[] arguments = new Expression[] {
            new TypeWrapperExp(EmptyType.INSTANCE),
            NumericLiteralImpl.create(BigDecimal.ONE)
        };

        assertThat(nativeTopCount.createEvaluator(
            null, mockFunctionDef(), arguments, true)).as("Native evaluator should not be created when "
            + "two parameters are passed").isNull();
    }

    private RolapNativeTopCount createTopCountSpy() {
        RolapNativeTopCount nativeTopCount = new RolapNativeTopCount(true);
        nativeTopCount = spy(nativeTopCount);
        return nativeTopCount;
    }

    private FunctionDefinition mockFunctionDef() {
        FunctionDefinition topCountFunMock = mock(FunctionDefinition.class);
        FunctionMetaData functionInformation = mock(FunctionMetaData.class);
        OperationAtom functionAtom = new FunctionOperationAtom("TOPCOUNT");

        when(topCountFunMock.getFunctionMetaData()).thenReturn(functionInformation);
        when(functionInformation.operationAtom()).thenReturn(functionAtom);

        return topCountFunMock;
    }
}
