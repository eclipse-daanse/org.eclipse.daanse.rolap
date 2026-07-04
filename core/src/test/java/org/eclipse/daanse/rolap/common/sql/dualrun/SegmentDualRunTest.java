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
package org.eclipse.daanse.rolap.common.sql.dualrun;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Shape 4/5 (segment / aggregate measure) builder-capability dual-run: proves the generic
 * builder reproduces the <em>real</em> segment SQL the engine emits (captured by
 * {@code MemberSqlCaptureTest} against the School catalog), including the segment-specific
 * conventions — measure alias {@code m0} (vs {@code c0} for dimension columns), lower-case
 * {@code sum(...)}, a comma-join product with the join predicate and the slicer constraint in
 * the legacy {@code [join, constraint]} order (enabled by {@code FromClause.FromProduct}).
 */
class SegmentDualRunTest {

    private final Dialect ansi = new AnsiDialect();

    /** Captured from the live engine (H2 School catalog). */
    private static final String GOLDEN =
            "select \"schul_jahr\".\"id\" as \"c0\", sum(\"fact_personal\".\"anzahl_personen\") as \"m0\""
            + " from \"fact_personal\" as \"fact_personal\", \"schul_jahr\" as \"schul_jahr\""
            + " where \"fact_personal\".\"schul_jahr_id\" = \"schul_jahr\".\"id\" and \"schul_jahr\".\"id\" = 4"
            + " group by \"schul_jahr\".\"id\"";

    @Test
    void reproducesCapturedSegmentSql() {
        TableAlias fact = TableAlias.of("fact_personal");
        TableAlias sj = TableAlias.of("schul_jahr");

        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(new FromClause.FromProduct(List.of(
                From.table("fact_personal", fact),
                From.table("schul_jahr", sj))));

        ProjectionRef c0 = q.project(Expressions.column(sj, "id"), BestFitColumnType.INT);
        q.groupOn(c0);
        q.project(Expressions.aggregate("sum", Expressions.column(fact, "anzahl_personen")),
                BestFitColumnType.DECIMAL, ColumnAlias.of("m0"));

        // [join, constraint] — exact legacy WHERE order, controlled by the mapper via FromProduct.
        q.where(Predicates.comparison(Expressions.column(fact, "schul_jahr_id"), ComparisonOperator.EQ,
                Expressions.column(sj, "id")));
        q.where(Predicates.comparison(Expressions.column(sj, "id"), ComparisonOperator.EQ,
                Expressions.literal(4, Datatype.INTEGER)));

        SqlEquivalence.assertEquivalent(GOLDEN, new DialectSqlRenderer(ansi).render(q.build()).sql());
    }
}
