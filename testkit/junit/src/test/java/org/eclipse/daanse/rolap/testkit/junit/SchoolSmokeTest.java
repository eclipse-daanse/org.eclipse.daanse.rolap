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

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * End-to-end smoke over the complex School catalog — the same scenario the
 * hand-wired {@code MdxQuerySmokeTest} covers, in three lines. Runs against
 * whatever {@code DAANSE_TEST_DB} selects (h2 default; duckdb in CI).
 */
@RolapContextTest(SchoolTestInstance.class)
class SchoolSmokeTest {

    @Test
    void schoolCubeAcceptsSimpleMdx(Connection connection) {
        Result result = connection.execute(connection.parseQuery(
                "SELECT [Measures].Members ON COLUMNS FROM [Schulen in Jena (Institutionen)]"));

        assertThat(result.getAxes()).as("at least one axis with measures").hasSizeGreaterThanOrEqualTo(1);
        assertThat(result.getAxes()[0].getPositions()).isNotEmpty();
    }
}
