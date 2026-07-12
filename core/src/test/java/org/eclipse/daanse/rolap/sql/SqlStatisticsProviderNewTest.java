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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.common.SqlRender;
import org.junit.jupiter.api.Test;

/**
 * Pins the rendered SQL of the statistics probes: the statements are dialect-free; the renderer
 * owns quoting, FROM-subquery aliasing and the count-distinct degradation.
 */
class SqlStatisticsProviderNewTest {

    private static String render(org.eclipse.daanse.sql.statement.api.model.SelectStatement s, Dialect d) {
        return SqlRender.render(s, d).sql();
    }

    @Test
    void tableCardinalityIsPlainCountStar() {
        assertEquals(
            "select COUNT(*) as \"c0\" from \"the_schema\".\"the_table\" as \"the_table\"",
            render(SqlStatisticsProviderNew.tableCardinalityStatement("the_schema", "the_table"),
                new AnsiDialect()));
    }

    @Test
    void tableCardinalityWithoutSchema() {
        assertEquals(
            "select COUNT(*) as \"c0\" from \"the_table\" as \"the_table\"",
            render(SqlStatisticsProviderNew.tableCardinalityStatement(null, "the_table"),
                new AnsiDialect()));
    }

    @Test
    void queryCardinalityWrapsTheProbeQuery() {
        assertEquals(
            "select COUNT(*) as \"c0\" from (select * from foo) as \"init\"",
            render(SqlStatisticsProviderNew.queryCardinalityStatement("select * from foo"),
                new AnsiDialect()));
    }

    @Test
    void columnCardinalityIsFlatCountDistinct() {
        assertEquals(
            "select COUNT(distinct \"the_column\") as \"c0\" from \"the_schema\".\"the_table\" as \"the_table\"",
            render(SqlStatisticsProviderNew.columnCardinalityStatement("the_schema", "the_table", "the_column"),
                new AnsiDialect()));
    }

    @Test
    void columnCardinalityDegradesWhenCountDistinctUnsupported() {
        Dialect noCountDistinct = new AnsiDialect() {
            @Override
            public boolean allowsCountDistinct() {
                return false;
            }
        };
        assertEquals(
            "select COUNT(\"m0\") as \"c0\" from (select distinct \"the_column\" as \"m0\" from"
                + " \"the_schema\".\"the_table\" as \"the_table\") as \"dummyname\"",
            render(SqlStatisticsProviderNew.columnCardinalityStatement("the_schema", "the_table", "the_column"),
                noCountDistinct));
    }
}
