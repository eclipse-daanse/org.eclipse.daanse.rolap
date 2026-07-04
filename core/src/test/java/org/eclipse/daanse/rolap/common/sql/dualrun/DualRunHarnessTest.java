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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.model.NullOrder;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SortDirection;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Phase 1 of the {@code QueryRecorder} replacement: proves the dual-run harness on the non-join
 * shapes (single fact table), confirming that the legacy {@link QueryRecorder} and the new
 * {@code org.eclipse.daanse.sql.statement} builder produce whitespace-equivalent SQL under the
 * same {@link AnsiDialect}. Join-bearing shapes are added in Phase 3 (after {@code JoinPlanner}).
 * <p>
 * The legacy builder receives <em>pre-quoted</em> column expressions (as ROLAP's
 * {@code generateExprString} produces them); the new builder receives structured
 * {@code Column}/{@code Literal} nodes and the renderer does the quoting — so identical output
 * is the equivalence we assert.
 */
class DualRunHarnessTest {

    private final Dialect ansi = new AnsiDialect();

    private DialectSqlRenderer renderer() {
        return new DialectSqlRenderer(ansi);
    }

    /** SELECT key + aggregate, FROM single table, WHERE comparison, GROUP BY key. */
    @Test
    void aggregateOverSingleTable() {
        // --- legacy ---
        QueryRecorder legacy = new QueryRecorder(false);
        legacy.addFromTable(null, "sales", "f", null, null, false);
        legacy.addSelectGroupBy("\"f\".\"region\"", BestFitColumnType.STRING);
        legacy.addSelect("SUM(\"f\".\"amount\")", BestFitColumnType.DECIMAL);
        legacy.addWhere("\"f\".\"year\" = 2024");

        // --- new ---
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias f = TableAlias.of("f");
        q.from(From.table("sales", f));
        ProjectionRef region = q.project(Expressions.column(f, "region"), BestFitColumnType.STRING);
        q.groupOn(region);
        q.project(Expressions.aggregate("SUM", Expressions.column(f, "amount")), BestFitColumnType.DECIMAL);
        q.where(Predicates.comparison(Expressions.column(f, "year"), ComparisonOperator.EQ,
                Expressions.literal(2024, Datatype.INTEGER)));

        SqlEquivalence.assertEquivalent(legacy.toSqlAndTypes(ansi).sql(), renderer().render(q.build()).sql());
    }

    /** SELECT DISTINCT over a single table. */
    @Test
    void distinctSelect() {
        QueryRecorder legacy = new QueryRecorder(false);
        legacy.setDistinct(true);
        legacy.addFromTable(null, "customer", "c", null, null, false);
        legacy.addSelect("\"c\".\"name\"", BestFitColumnType.STRING);

        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias c = TableAlias.of("c");
        q.distinct(true);
        q.from(From.table("customer", c));
        q.project(Expressions.column(c, "name"), BestFitColumnType.STRING);

        SqlEquivalence.assertEquivalent(legacy.toSqlAndTypes(ansi).sql(), renderer().render(q.build()).sql());
    }

    /**
     * Two-table join, ANSI {@code JOIN…ON}. The JOIN…ON-standard conversion flipped the
     * {@code allowsJoinOn} capability default to {@code true} (AnsiDialect now allows it), so the
     * renderer emits a structured {@code FROM a JOIN b ON …} for an {@code innerJoin} edge — the
     * canonical form the migrated member/tuple path produces (via {@code addJoinCondition} →
     * {@code FromJoin}). The obsolete comma-join + pushed-down WHERE form (the pre-conversion
     * {@code allowsJoinOn=false} fallback) is no longer the spelling, so this asserts the builder's
     * JOIN…ON output directly.
     */
    @Test
    void ansiJoinParity() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias f = TableAlias.of("f");
        TableAlias p = TableAlias.of("p");
        q.from(From.table("sales", f));
        q.innerJoin(From.table("product", p), Predicates.comparison(Expressions.column(f, "product_id"),
                ComparisonOperator.EQ, Expressions.column(p, "id")));
        q.project(Expressions.column(p, "name"), BestFitColumnType.STRING);

        String expected = "select \"p\".\"name\" as \"c0\" from \"sales\" as \"f\" "
                + "join \"product\" as \"p\" on \"f\".\"product_id\" = \"p\".\"id\"";
        SqlEquivalence.assertEquivalent(expected, renderer().render(q.build()).sql());
    }

    /** SELECT + ORDER BY: exercises the shared {@code orderByGenerator}. */
    @Test
    void orderByAscending() {
        QueryRecorder legacy = new QueryRecorder(false);
        legacy.addFromTable(null, "product", "p", null, null, false);
        legacy.addSelect("\"p\".\"name\"", BestFitColumnType.STRING);
        legacy.addOrderBy("\"p\".\"name\"", SortingDirection.ASC, false, false);

        SelectStatementBuilder q = SelectStatementBuilder.create();
        TableAlias p = TableAlias.of("p");
        q.from(From.table("product", p));
        q.project(Expressions.column(p, "name"), BestFitColumnType.STRING);
        // legacy: nullable=false, collateNullsLast=true  ->  SortSpec(ASC, nullable=false, nulls LAST)
        q.orderOn(Expressions.column(p, "name"),
                new SortSpec(SortDirection.ASC, false, NullOrder.LAST, false));

        SqlEquivalence.assertEquivalent(legacy.toSqlAndTypes(ansi).sql(), renderer().render(q.build()).sql());
    }
}
