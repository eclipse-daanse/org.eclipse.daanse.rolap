/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.common.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SqlQuery}.
 */
class SqlQueryTest {

    private Dialect dialect;
    private SqlQuery sqlQuery;

    @BeforeEach
    void setUp() {
        dialect = mock(Dialect.class);
        when(dialect.allowsAs()).thenReturn(true);
        when(dialect.allowsFieldAs()).thenReturn(true);
        when(dialect.quoteIdentifier(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> "\"" + invocation.getArgument(0) + "\"");
        sqlQuery = new SqlQuery(dialect, false);
    }

    @Test
    void testConstructorCreatesEmptyQuery() {
        assertThat(sqlQuery.toString()).isEmpty();
        assertThat(sqlQuery.getDialect()).isEqualTo(dialect);
    }

    @Test
    void testCloneEmptyCreatesNewInstance() {
        SqlQuery cloned = sqlQuery.cloneEmpty();
        assertThat(cloned).isNotSameAs(sqlQuery);
        assertThat(cloned.getDialect()).isEqualTo(dialect);
    }

    @Test
    void testAddSelectWithAlias() {
        String alias = sqlQuery.addSelect("column1", BestFitColumnType.STRING, "c0");
        assertThat(alias).isEqualTo("c0");
        assertThat(sqlQuery.getAlias("column1")).isEqualTo("c0");
    }

    @Test
    void testAddSelectWithoutAlias() {
        String alias = sqlQuery.addSelect("column1", BestFitColumnType.STRING);
        assertThat(alias).isEqualTo("c0");
    }

    @Test
    void testNextColumnAlias() {
        assertThat(sqlQuery.nextColumnAlias()).isEqualTo("c0");
        sqlQuery.addSelect("col1", BestFitColumnType.STRING);
        assertThat(sqlQuery.nextColumnAlias()).isEqualTo("c1");
    }

    @Test
    void testGetCurrentSelectListSize() {
        assertThat(sqlQuery.getCurrentSelectListSize()).isZero();
        sqlQuery.addSelect("col1", BestFitColumnType.STRING);
        assertThat(sqlQuery.getCurrentSelectListSize()).isEqualTo(1);
        sqlQuery.addSelect("col2", BestFitColumnType.STRING);
        assertThat(sqlQuery.getCurrentSelectListSize()).isEqualTo(2);
    }

    @Test
    void testAddWhereWithNullThrowsException() {
        String nullExpression = null;
        assertThatThrownBy(() -> sqlQuery.addWhere(nullExpression))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expression must not be null or blank");
    }

    @Test
    void testAddWhereWithBlankThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addWhere("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expression must not be null or blank");
    }

    @Test
    void testAddWhereWithEmptyThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addWhere(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expression must not be null or blank");
    }

    @Test
    void testAddWhereWithValidExpression() {
        sqlQuery.addWhere("column1 = 'value'");
        // No exception should be thrown
    }

    @Test
    void testAddGroupByWithNullThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addGroupBy(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expression must not be null or blank");
    }

    @Test
    void testAddGroupByWithBlankThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addGroupBy("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expression must not be null or blank");
    }

    @Test
    void testAddHavingWithNullThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addHaving(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expression must not be null or blank");
    }

    @Test
    void testAddHavingWithBlankThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addHaving("\t"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("expression must not be null or blank");
    }

    @Test
    void testSetDistinct() {
        sqlQuery.setDistinct(true);
        // Verify via generated SQL would require more setup
    }

    @Test
    void testSetAllowHints() {
        sqlQuery.setAllowHints(true);
        // Verify via generated SQL would require more setup
    }

    @Test
    void testIsSupportedDefaultTrue() {
        assertThat(sqlQuery.isSupported()).isTrue();
    }

    @Test
    void testSetSupported() {
        sqlQuery.setSupported(false);
        assertThat(sqlQuery.isSupported()).isFalse();
    }

    @Test
    void testAddFromQueryWithNullAliasThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addFromQuery("SELECT 1", null, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("alias must not be null or blank");
    }

    @Test
    void testAddFromQueryWithEmptyAliasThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addFromQuery("SELECT 1", "", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("alias must not be null or blank");
    }

    @Test
    void testAddFromQueryWithBlankAliasThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addFromQuery("SELECT 1", "   ", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("alias must not be null or blank");
    }

    @Test
    void testAddFromTableWithEmptyAliasThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addFromTable("schema", "table", "", null, null, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("alias must not be null or blank");
    }

    @Test
    void testAddFromTableWithBlankAliasThrowsException() {
        assertThatThrownBy(() -> sqlQuery.addFromTable("schema", "table", "  ", null, null, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("alias must not be null or blank");
    }

    @Test
    void testAddFromTableWithNullAliasDoesNotThrow() {
        // null alias is allowed for addFromTable
        sqlQuery.addFromTable("schema", "table", null, null, null, false);
        // No exception should be thrown
    }
}
