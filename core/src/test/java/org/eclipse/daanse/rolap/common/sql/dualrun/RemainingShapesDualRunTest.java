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
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SetOperation;
import org.eclipse.daanse.sql.statement.api.model.Statement;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Builder-capability dual-runs for the remaining query shapes, confirming the generic builder
 * can produce each legacy SQL form: Shape 3 (tuple/crossjoin — multi-dimension comma product),
 * Shape 6 (drill-through — NULL placeholder + dialect row-limit), Shape 7 (native filter —
 * {@code HAVING}), Shape 10 (union of member sources).
 */
class RemainingShapesDualRunTest {

    private final Dialect ansi = new AnsiDialect();

    private String render(Statement s) {
        return new DialectSqlRenderer(ansi).render(s).sql();
    }

    /** Shape 3: two dimensions joined to the fact, comma-product FROM, join predicates in WHERE. */
    @Test
    void tupleCrossjoin() {
        TableAlias sales = TableAlias.of("sales");
        TableAlias product = TableAlias.of("product");
        TableAlias customer = TableAlias.of("customer");
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(new FromClause.FromProduct(List.of(
                From.table("sales", sales), From.table("product", product), From.table("customer", customer))));
        ProjectionRef p = q.project(Expressions.column(product, "name"), BestFitColumnType.STRING);
        ProjectionRef c = q.project(Expressions.column(customer, "name"), BestFitColumnType.STRING);
        q.groupOn(p);
        q.groupOn(c);
        q.where(Predicates.comparison(Expressions.column(sales, "product_id"), ComparisonOperator.EQ,
                Expressions.column(product, "id")));
        q.where(Predicates.comparison(Expressions.column(sales, "customer_id"), ComparisonOperator.EQ,
                Expressions.column(customer, "id")));

        SqlEquivalence.assertEquivalent(
                "select \"product\".\"name\" as \"c0\", \"customer\".\"name\" as \"c1\""
                + " from \"sales\" as \"sales\", \"product\" as \"product\", \"customer\" as \"customer\""
                + " where \"sales\".\"product_id\" = \"product\".\"id\""
                + " and \"sales\".\"customer_id\" = \"customer\".\"id\""
                + " group by \"product\".\"name\", \"customer\".\"name\"",
                render(q.build()));
    }

    /**
     * E3.3 target: a dimension level's members enumerated under a context/slicer constraint — the
     * legacy ContextConstraintWriter path joins the fact table and pushes the constraint into WHERE.
     * This proves the builder expresses that shape (comma-product fact+dim, join predicate then the
     * constraint predicate, member select/group), which the constrained-SELECT cluster rewrite
     * (constraints/native/AggStar off the shared QueryRecorder) must generate from the star + evaluator.
     */
    @Test
    void constrainedMemberChildren() {
        TableAlias fact = TableAlias.of("fact");
        TableAlias dim = TableAlias.of("dim");
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(new FromClause.FromProduct(List.of(From.table("fact", fact), From.table("dim", dim))));
        ProjectionRef n = q.project(Expressions.column(dim, "name"), BestFitColumnType.STRING);
        q.groupOn(n);
        q.where(Predicates.comparison(Expressions.column(fact, "dim_id"), ComparisonOperator.EQ,
                Expressions.column(dim, "id")));
        q.where(Predicates.comparison(Expressions.column(fact, "store_id"), ComparisonOperator.EQ,
                Expressions.raw("1")));

        SqlEquivalence.assertEquivalent(
                "select \"dim\".\"name\" as \"c0\" from \"fact\" as \"fact\", \"dim\" as \"dim\""
                + " where \"fact\".\"dim_id\" = \"dim\".\"id\" and \"fact\".\"store_id\" = 1"
                + " group by \"dim\".\"name\"",
                render(q.build()));
    }

    /** Shape 6: drill-through — real columns + NULL placeholders + dialect row limit. */
    @Test
    void drillThroughWithNullPlaceholderAndLimit() {
        TableAlias f = TableAlias.of("f");
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.table("fact", f));
        q.project(Expressions.column(f, "a"), BestFitColumnType.STRING);
        q.project(Expressions.raw("NULL"), BestFitColumnType.STRING); // unavailable column placeholder
        q.rowLimit(100);

        SqlEquivalence.assertEquivalent(
                "select \"f\".\"a\" as \"c0\", NULL as \"c1\" from \"fact\" as \"f\" FETCH NEXT 100 ROWS ONLY",
                render(q.build()));
    }

    /** Shape 7: native filter — HAVING with a (raw) aggregate condition. */
    @Test
    void nativeFilterHaving() {
        TableAlias f = TableAlias.of("f");
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.table("fact", f));
        ProjectionRef region = q.project(Expressions.column(f, "region"), BestFitColumnType.STRING);
        q.groupOn(region);
        q.having(Predicates.raw("sum(\"f\".\"x\") > 100"));

        SqlEquivalence.assertEquivalent(
                "select \"f\".\"region\" as \"c0\" from \"fact\" as \"f\""
                + " group by \"f\".\"region\" having sum(\"f\".\"x\") > 100",
                render(q.build()));
    }

    /** Shape 10: union of two member sources. */
    @Test
    void unionOfMemberSources() {
        TableAlias a = TableAlias.of("a");
        TableAlias b = TableAlias.of("b");
        SelectStatementBuilder s1 = SelectStatementBuilder.create();
        s1.from(From.table("product_a", a));
        s1.project(Expressions.column(a, "name"), BestFitColumnType.STRING);
        SelectStatementBuilder s2 = SelectStatementBuilder.create();
        s2.from(From.table("product_b", b));
        s2.project(Expressions.column(b, "name"), BestFitColumnType.STRING);

        Statement union = SetOperation.unionAll(List.of(s1.build(), s2.build()));
        SqlEquivalence.assertEquivalent(
                "select \"a\".\"name\" as \"c0\" from \"product_a\" as \"a\""
                + " union all select \"b\".\"name\" as \"c0\" from \"product_b\" as \"b\"",
                render(union));
    }
}
