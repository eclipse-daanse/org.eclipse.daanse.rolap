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

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.cube.minimal.MinimalCubeTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.junit.jupiter.api.Test;

/** Type-based injection of every supported parameter type, no annotations on parameters. */
@RolapContextTest(MinimalCubeTestInstance.class)
class QuickstartInjectionTest {

    @Test
    void injectsConnectionAndRunsMdx(Connection connection) {
        Result result = connection.execute(connection.parseQuery(
                "SELECT {[Measures].[Measure-Sum]} ON COLUMNS FROM [MinimalCube]"));

        assertThat(result.getAxes()).as("Column axis with the Measure").hasSize(1);
        assertThat(result.getAxes()[0].getPositions()).isNotEmpty();
    }

    @Test
    void injectsAllSupportedTypes(Connection connection, Context<?> context, Dialect dialect,
            DataSource dataSource, ActiveDatabase database) {
        assertThat(connection).isNotNull();
        assertThat(context).isNotNull();
        assertThat(dialect).isSameAs(database.dialect());
        assertThat(dataSource).isSameAs(database.dataSource());
    }

    @Test
    void contextOnlySignatureWorks(Context<?> context) {
        assertThat(context.getConnectionWithDefaultRole()).isNotNull();
    }
}
