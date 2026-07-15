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

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QueryRecorder}.
 */
class SqlQueryTest {

    private Dialect dialect;
    private QueryRecorder sqlQuery;

    @BeforeEach
    void setUp() {
        dialect = mock(Dialect.class);
        when(dialect.allowsFromAlias()).thenReturn(true);
        when(dialect.allowsFieldAlias()).thenReturn(true);
        when(dialect.quoteIdentifier(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> "\"" + invocation.getArgument(0) + "\"");
        sqlQuery = new QueryRecorder(false);
    }

    @Test
    void testAddSelectWithAlias() {
        String alias = sqlQuery.addSelect("column1", BestFitColumnType.STRING, "c0");
        assertThat(alias).isEqualTo("c0");
    }

    @Test
    void testAddSelectWithoutAlias() {
        String alias = sqlQuery.addSelect("column1", BestFitColumnType.STRING);
        assertThat(alias).isEqualTo("c0");
    }

    @Test
    void testAutoAliasAdvancesPerSelect() {
        sqlQuery.addSelect("col1", BestFitColumnType.STRING);
        String alias = sqlQuery.addSelect("col2", BestFitColumnType.STRING);
        assertThat(alias).isEqualTo("c1");
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

    // ---- addJoinCondition no-op semantics ----

    private java.util.List<QueryTape.JoinEdge> joinEdges() {
        return sqlQuery.ops().ops().stream()
            .filter(QueryTape.JoinEdge.class::isInstance)
            .map(QueryTape.JoinEdge.class::cast)
            .toList();
    }

    @Test
    void testAddJoinConditionEmitsEdgeWhenBothTablesRegistered() {
        sqlQuery.addFromTable(null, "sales_fact", "sales_fact", null, null, false);
        sqlQuery.addFromTable(null, "agg_time", "agg_time", null, null, false);
        sqlQuery.addJoinCondition(
            new org.eclipse.daanse.rolap.element.RolapColumn("sales_fact", "time_id"),
            new org.eclipse.daanse.rolap.element.RolapColumn("agg_time", "time_id"));
        assertThat(joinEdges()).hasSize(1);
        assertThat(joinEdges().get(0).leftAlias()).isEqualTo("sales_fact");
        assertThat(joinEdges().get(0).rightAlias()).isEqualTo("agg_time");
    }

    @Test
    void testAddJoinConditionParentWalkGuardWhenTableNotRegistered() {
        // Case (1): alias resolvable but table never registered in FROM — the legitimate
        // parent-walk guard; silent no-op, no edge.
        sqlQuery.addFromTable(null, "sales_fact", "sales_fact", null, null, false);
        sqlQuery.addJoinCondition(
            new org.eclipse.daanse.rolap.element.RolapColumn("sales_fact", "time_id"),
            new org.eclipse.daanse.rolap.element.RolapColumn("not_in_from", "time_id"));
        assertThat(joinEdges()).isEmpty();
    }

    @Test
    void testAddJoinConditionDropsComputedSideWithoutThrowing() {
        // Case (2): a computed <SQL> join-expression side has no resolvable table alias — the
        // condition is dropped (WARN "agg-join-dropped" logged; behavior-preserving no-op even
        // though both tables ARE in the FROM). Documents the correctness hazard: legacy SqlQuery
        // emitted this condition unconditionally as a WHERE string.
        sqlQuery.addFromTable(null, "sales_fact", "sales_fact", null, null, false);
        sqlQuery.addFromTable(null, "agg_time", "agg_time", null, null, false);
        org.eclipse.daanse.olap.api.sql.SqlExpression computed =
            mock(org.eclipse.daanse.olap.api.sql.SqlExpression.class);
        sqlQuery.addJoinCondition(computed,
            new org.eclipse.daanse.rolap.element.RolapColumn("agg_time", "time_id"));
        assertThat(joinEdges()).isEmpty();
    }
}
