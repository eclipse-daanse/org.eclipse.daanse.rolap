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
package org.eclipse.daanse.rolap.common.sqlbuild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the aggregate-table row-count query {@link TupleSqlMapper#aggTableCountSql} builds — a
 * {@code select count(*) from "agg_x" as "agg_x"}, with and without a schema prefix. A view
 * ({@link SqlSelectSource}) aggregate fact is out of scope and returns {@code null}.
 */
class AggTableCountSqlTest {

    private static String render(SelectStatement stmt) {
        return new DialectSqlRenderer(new AnsiDialect()).render(stmt).sql();
    }

    private static TableSource tableSource(String name, String schema) {
        TableSource ts = mock(TableSource.class);
        NamedColumnSet ncs = mock(NamedColumnSet.class);
        when(ncs.getName()).thenReturn(name);
        if (schema != null) {
            Schema s = mock(Schema.class);
            when(s.getName()).thenReturn(schema);
            when(ncs.getNamespace()).thenReturn(s);
        }
        when(ts.getTable()).thenReturn(ncs);
        return ts;
    }

    /** Agg-fact cardinality count: alias == physical table name, no schema. The count(*)
     *  projection carries no explicit alias, so the renderer synthesizes {@code c0} on an
     *  allowsFieldAlias dialect. */
    @Test
    void aggTableCountMatchesCorpusSql() {
        SelectStatement stmt = TupleSqlMapper.aggTableCountSql(
                tableSource("agg_c_10_sales_fact_1997", null), "agg_c_10_sales_fact_1997");
        assertThat(stmt).isNotNull();
        assertThat(render(stmt)).isEqualTo(
                "select count(*) as \"c0\" from"
                + " \"agg_c_10_sales_fact_1997\" as \"agg_c_10_sales_fact_1997\"");
    }

    /** A schema-qualified aggregate fact renders the schema prefix, alias unchanged. */
    @Test
    void aggTableCountWithSchema() {
        SelectStatement stmt = TupleSqlMapper.aggTableCountSql(
                tableSource("agg_c_14_sales_fact_1997", "foodmart"), "agg_c_14_sales_fact_1997");
        assertThat(render(stmt)).isEqualTo(
                "select count(*) as \"c0\" from \"foodmart\".\"agg_c_14_sales_fact_1997\""
                + " as \"agg_c_14_sales_fact_1997\"");
    }

    /** A non-table (view) aggregate fact is outside the trivial builder's scope: null → recorder. */
    @Test
    void viewAggFactDeclines() {
        RelationalSource view = mock(SqlSelectSource.class);
        assertThat(TupleSqlMapper.aggTableCountSql(view, "agg_view")).isNull();
    }
}
