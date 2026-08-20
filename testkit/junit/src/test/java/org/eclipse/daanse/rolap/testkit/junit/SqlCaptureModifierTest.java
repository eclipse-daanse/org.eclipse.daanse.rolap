/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.cube.minimal.MinimalCubeTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.modifier.SqlCaptureModifier;
import org.junit.jupiter.api.Test;

/**
 * The declared interceptor replaces the hand-rolled
 * {@code RolapUtil.setHook(...) try/finally}: registered before the test,
 * injectable for evaluation, deregistered by the extension afterwards.
 */
@RolapContextTest(value = MinimalCubeTestInstance.class, modifiers = SqlCaptureModifier.class)
class SqlCaptureModifierTest {

    @Test
    void capturesTheEmittedSql(Connection connection, SqlCaptureModifier sql) {
        connection.execute(connection.parseQuery(
                "SELECT {[Measures].[Measure-Sum]} ON COLUMNS FROM [MinimalCube]"));

        assertThat(sql.captured())
                .as("The engine emitted at least one SQL query and the interceptor caught it.")
                .isNotEmpty()
                .allSatisfy(statement -> assertThat(statement).containsIgnoringCase("select"));
        assertThat(sql.lastMatching(s -> s.toLowerCase().contains("fact"))).isPresent();
    }

    @Test
    void hookIsScopedToTheTestContext(Context<?> context, SqlCaptureModifier sql) {
        //The extension's hook is registered for exactly this context —
        //and clear() only clears the record, not the registry.        sql.clear();
        assertThat(RolapUtil.getHook(context)).isNotNull();
        assertThat(sql.captured()).isEmpty();
    }
}
