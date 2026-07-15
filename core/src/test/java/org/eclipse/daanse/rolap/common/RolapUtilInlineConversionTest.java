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
package org.eclipse.daanse.rolap.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.generator.SqlGenerator;
import org.eclipse.daanse.rolap.mapping.model.database.relational.InlineTable;
import org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.junit.jupiter.api.Test;

/**
 * The InlineTableSource branch of {@code RolapCube.modifyFact} (line 2536–2542)
 * funnels through {@link RolapCube#convertInlineTableToRelation}. These tests
 * verify that identifier quoting for that branch is the dialect's
 * responsibility — i.e. rolap delegates SQL generation to
 * {@code dialect.sqlGenerator().generateInline(...)} and embeds the result
 * verbatim. The dialect's own quoting correctness is asserted by tests in the
 * dialect module (see {@code JdbcInlineDataGenerator}, which calls
 * {@code dialect.quoteIdentifier(...)} for the alias and every column name).
 */
class RolapUtilInlineConversionTest {

    @Test
    void convertInlineTableToRelation_delegatesSqlBuildingToDialectGenerator() {
        // Build a minimal real InlineTableSource. The branch under test does not need
        // populated columns/rows to exercise the dialect delegation.
        InlineTable inlineTable = RelationalFactory.eINSTANCE.createInlineTable();
        inlineTable.setName("MEMFACT");

        InlineTableSource source = SourceFactory.eINSTANCE.createInlineTableSource();
        source.setAlias("memfact_alias");
        source.setTable(inlineTable);

        // Stub the dialect's SqlGenerator — what it returns is what RolapUtil must
        // embed verbatim. We deliberately bake the dialect's quoting into the stub
        // so the assertion proves rolap does not re-quote or otherwise mangle.
        Dialect dialect = mock(Dialect.class);
        SqlGenerator generator = mock(SqlGenerator.class);
        when(dialect.sqlGenerator()).thenReturn(generator);
        when(generator.generateInline(anyList(), anyList(), anyList())).thenReturn(new StringBuilder(
                "(select \"PRODUCT\", \"AMOUNT\" from (values('widget', 42)) as t(\"PRODUCT\", \"AMOUNT\"))"));

        RelationalSource result = RolapCube.convertInlineTableToRelation(source, dialect);

        // Delegation happened — generator was called once with the column metadata
        // (empty for this minimal fixture) and the value rows (also empty).
        verify(generator, times(1)).generateInline(anyList(), anyList(), anyList());

        // The resulting view embeds the dialect-produced SQL verbatim.
        assertThat(result).isInstanceOf(SqlSelectSource.class);
        SqlSelectSource view = (SqlSelectSource) result;
        assertThat(view.getAlias()).isEqualTo("memfact_alias");

        String generatedSql = view.getSql().getDialectStatements().get(0).getSql();
        assertThat(generatedSql).contains("\"PRODUCT\"").contains("\"AMOUNT\"");

        // Rolap did not re-quote the dialect output — bare names introduced by the
        // dialect must not appear with extra quoting added on the rolap side.
        assertThat(generatedSql).doesNotContain("\"\"PRODUCT\"\"").doesNotContain("\"\"AMOUNT\"\"");
    }

    @Test
    void convertInlineTableToRelation_orderedOverload_alsoDelegatesToDialectGenerator() {
        InlineTable inlineTable = RelationalFactory.eINSTANCE.createInlineTable();
        inlineTable.setName("MEMFACT");

        InlineTableSource source = SourceFactory.eINSTANCE.createInlineTableSource();
        source.setAlias("memfact_alias");
        source.setTable(inlineTable);

        Dialect dialect = mock(Dialect.class);
        SqlGenerator generator = mock(SqlGenerator.class);
        when(dialect.sqlGenerator()).thenReturn(generator);
        when(generator.generateInline(anyList(), anyList(), anyList()))
                .thenReturn(new StringBuilder("(select \"AMOUNT\" from (values(42)) as t(\"AMOUNT\"))"));

        // Caller (RolapCube.modifyFact, line 2537) hands the writeback column order in.
        List<String> orderColumns = List.of("PRODUCT", "AMOUNT");

        RelationalSource result = RolapCube.convertInlineTableToRelation(source, dialect, orderColumns);

        verify(generator, times(1)).generateInline(anyList(), anyList(), anyList());

        assertThat(result).isInstanceOf(SqlSelectSource.class);
        SqlSelectSource view = (SqlSelectSource) result;
        String generatedSql = view.getSql().getDialectStatements().get(0).getSql();
        assertThat(generatedSql).contains("\"AMOUNT\"");
    }
}
