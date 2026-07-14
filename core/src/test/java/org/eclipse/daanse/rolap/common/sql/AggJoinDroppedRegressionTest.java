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
package org.eclipse.daanse.rolap.common.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.SqlStatement;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Regression pin for the {@code agg-join-dropped} fix: a join condition whose side is a computed
 * {@code <SQL>} expression (no resolvable table alias) is EMITTED as a WHERE predicate — the
 * legacy SqlQuery behavior — instead of being silently dropped (which left a cross join with
 * wrong aggregates). The parent-walk guard stays: a resolvable counterpart whose table is NOT in
 * the FROM keeps the silent no-op.
 */
class AggJoinDroppedRegressionTest {

    private static SqlExpression computed(String sql) {
        SqlExpression exp = mock(SqlExpression.class);
        SqlStatement stmt = mock(SqlStatement.class);
        when(stmt.getDialects()).thenReturn(List.of("generic"));
        when(stmt.getSql()).thenReturn(sql);
        when(exp.getSqls()).thenReturn(List.of(stmt));
        return exp;
    }

    private static QueryRecorder recorderWithEmployeeFrom() {
        QueryRecorder q = new QueryRecorder(false);
        q.addSelect("*", BestFitColumnType.STRING);
        q.addFromTable(null, "employee", "employee", null, null, false);
        return q;
    }

    private static String render(QueryRecorder q) {
        return new DialectSqlRenderer(new AnsiDialect()).render(q.buildStatement()).sql();
    }

    @Test
    void computedSideJoinConditionIsEmittedAsWhere() {
        QueryRecorder q = recorderWithEmployeeFrom();
        q.addJoinCondition(
            computed("RTRIM(supervisor_id)"),
            new RolapColumn("employee", "employee_id"));
        assertThat(render(q))
            .contains("where")
            .contains("RTRIM(supervisor_id) = \"employee\".\"employee_id\"");
    }

    @Test
    void parentWalkGuardStaysSilentWhenCounterpartTableNotInFrom() {
        QueryRecorder q = recorderWithEmployeeFrom();
        q.addJoinCondition(
            computed("RTRIM(supervisor_id)"),
            new RolapColumn("some_other_table", "employee_id"));
        assertThat(render(q)).doesNotContain("where");
    }

    @Test
    void bothSidesComputedIsEmittedAsWhere() {
        QueryRecorder q = recorderWithEmployeeFrom();
        q.addJoinCondition(
            computed("RTRIM(supervisor_id)"),
            computed("LTRIM(employee_id)"));
        assertThat(render(q))
            .contains("RTRIM(supervisor_id) = LTRIM(employee_id)");
    }
}
