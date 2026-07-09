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
package org.eclipse.daanse.rolap.element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.util.resource.relational.SqlSimpleTypes;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.generator.SqlGenerator;
import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.rolap.common.writeback.RolapWritebackAttribute;
import org.eclipse.daanse.rolap.common.writeback.RolapWritebackColumn;
import org.eclipse.daanse.rolap.common.writeback.RolapWritebackMeasure;
import org.eclipse.daanse.rolap.common.writeback.RolapWritebackTable;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the writeback union-all wrapper produced by
 * {@link RolapCube#getWriteBackSql} routes every identifier through the
 * dialect's {@code quoteIdentifier(...)}. Without this, the inner UNION ALL
 * remains unquoted while the surrounding aggregation query is quoted, and
 * dialects with strict identifier rules (Oracle, MSSQL with QUOTED_IDENTIFIER,
 * any mixed-case schema) fail.
 *
 * <p>
 * The dialect is stubbed to return {@code "&lt;name&gt;"}-wrapped strings so
 * the test can pattern-match the wrapper rather than depending on a particular
 * dialect's quote character.
 */
class RolapCubeWriteBackSqlTest {

    private static Column column(String name) {
        Column c = RelationalFactory.eINSTANCE.createColumn();
        c.setName(name);
        c.setType(SqlSimpleTypes.Sql99.varcharType());
        return c;
    }

    private static Dialect dialectQuotingWith(String openQuote, String closeQuote) {
        Dialect dialect = mock(Dialect.class);
        when(dialect.quoteIdentifier(any(CharSequence.class)))
                .thenAnswer(inv -> openQuote + inv.getArgument(0).toString() + closeQuote);
        // sqlGenerator() is consulted only for sessionValues; default to a stub
        // returning empty so plain-table tests don't trip on it.
        when(dialect.sqlGenerator()).thenReturn(mock(SqlGenerator.class));
        return dialect;
    }

    @Test
    void quotesEveryColumnAndTheTableName() {
        Dialect dialect = dialectQuotingWith("\"", "\"");

        Column productColumn = column("PRODUCT");
        Column cityColumn = column("CITY");
        Column amountColumn = column("AMOUNT");
        Column textColumn = column("TXT_INPUT");

        Dimension productDim = mock(Dimension.class);
        Dimension cityDim = mock(Dimension.class);
        Member amountMeasure = mock(Member.class);
        Member textMeasure = mock(Member.class);

        List<RolapWritebackColumn> columns = List.of(new RolapWritebackAttribute(productDim, productColumn),
                new RolapWritebackAttribute(cityDim, cityColumn),
                new RolapWritebackMeasure(amountMeasure, amountColumn, Datatype.NUMERIC),
                new RolapWritebackMeasure(textMeasure, textColumn, Datatype.VARCHAR));

        RolapWritebackTable writebackTable = new RolapWritebackTable("FACTWB", null, columns);

        CharSequence sql = RolapCube.getWriteBackSql(dialect, writebackTable, null);

        // Each identifier must appear quoted.
        assertThat(sql.toString()).contains("\"PRODUCT\"").contains("\"CITY\"").contains("\"AMOUNT\"")
                .contains("\"TXT_INPUT\"").contains("\"FACTWB\"");

        // The unquoted bare names must NOT appear as standalone identifiers.
        // (We pattern-match a comma- or space-delimited bare form to avoid
        // false negatives from the dialect-quoted occurrences above.)
        assertThat(sql.toString()).doesNotContain(" PRODUCT,").doesNotContain(" CITY,").doesNotContain(" AMOUNT,")
                .doesNotContain("from FACTWB").doesNotContain("from FACTWB ");

        // The leading " union all select " glue stays as-is.
        assertThat(sql.toString()).startsWith(" union all select ");
        // Structure: union all select <q>c1</q>, <q>c2</q>, ... from <q>TABLE</q>
        assertThat(sql.toString())
                .matches("\\s*union all select \"PRODUCT\", \"CITY\", \"AMOUNT\", \"TXT_INPUT\" from \"FACTWB\"\\s*");

        // And the dialect was consulted once per column + once for the table.
        verify(dialect, times(5)).quoteIdentifier(any(CharSequence.class));
    }

    @Test
    void singleColumnSchemaStillQuotes() {
        Dialect dialect = dialectQuotingWith("<<", ">>");

        Column amountColumn = column("AMOUNT");
        Member amountMeasure = mock(Member.class);

        RolapWritebackTable writebackTable = new RolapWritebackTable("FACTWB", "WB",
                List.of(new RolapWritebackMeasure(amountMeasure, amountColumn, Datatype.NUMERIC)));

        CharSequence sql = RolapCube.getWriteBackSql(dialect, writebackTable, null);

        assertThat(sql.toString()).isEqualTo(" union all select <<AMOUNT>> from <<FACTWB>>");
    }

    @Test
    void emptyColumnsProducesEmptySelectListButStillQuotesTable() {
        Dialect dialect = dialectQuotingWith("`", "`");

        RolapWritebackTable writebackTable = new RolapWritebackTable("FACTWB", null, List.of());

        CharSequence sql = RolapCube.getWriteBackSql(dialect, writebackTable, null);

        assertThat(sql.toString()).isEqualTo(" union all select  from `FACTWB`");
        verify(dialect, times(1)).quoteIdentifier(any(CharSequence.class));
    }

    @Test
    void nullSessionValuesDoesNotCallSqlGenerator() {
        Dialect dialect = dialectQuotingWith("\"", "\"");

        RolapWritebackTable writebackTable = new RolapWritebackTable("FACTWB", null,
                List.of(new RolapWritebackMeasure(mock(Member.class), column("AMOUNT"), Datatype.NUMERIC)));

        RolapCube.getWriteBackSql(dialect, writebackTable, null);

        // No sessionValues → no generateUnionAllSql call.
        verify(dialect.sqlGenerator(), never()).generateUnionAllSql(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void nonEmptySessionValuesAppendsDialectGenerated() {
        Dialect dialect = dialectQuotingWith("\"", "\"");

        SqlGenerator generator = dialect.sqlGenerator();
        when(generator.generateUnionAllSql(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new StringBuilder(" union all select 1, 2, 3, 'foo' from dual"));

        RolapWritebackTable writebackTable = new RolapWritebackTable("FACTWB", null,
                List.of(new RolapWritebackMeasure(mock(Member.class), column("AMOUNT"), Datatype.NUMERIC)));

        @SuppressWarnings("unchecked")
        List<Map<String, Map.Entry<Datatype, Object>>> sessionValues = (List<Map<String, Map.Entry<Datatype, Object>>>) (List<?>) List
                .of(Map.of());

        CharSequence sql = RolapCube.getWriteBackSql(dialect, writebackTable, sessionValues);

        // Dialect's own SQL for session values is appended after the writeback table
        // union.
        assertThat(sql.toString()).startsWith(" union all select \"AMOUNT\" from \"FACTWB\"")
                .endsWith(" union all select 1, 2, 3, 'foo' from dual");

        verify(generator, times(1)).generateUnionAllSql(sessionValues);
    }
}
