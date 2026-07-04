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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.sqlbuild.AggregateSqlMapper;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Condition.JoinColumn;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.render.RenderOptions;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * PROVENANCE-COMMENT DEMO: builds four representative ROLAP statements through the same comment
 * attachment points the producers use ({@code setHeaderComment} / {@code addSelectExprCommented} /
 * {@code commentFrom} / {@code addWhere(p, comment)} / {@code addHaving(p, comment)} /
 * {@code setFooterComment}, and {@link AggregateSqlMapper#drillThrough} for the direct-builder
 * path) and renders each WITH comments (formatted, LINE style) into
 * {@code target/commented-sql-demo.txt}. The executed SQL always renders with comments OFF, so this
 * is diagnostics-only; the substring asserts pin the stable comment vocabulary.
 */
class CommentedSqlDemoTest {

    private final Dialect ansi = new AnsiDialect();

    private static final RenderOptions COMMENTED =
            RenderOptions.multiLine().withComments(true, RenderOptions.CommentStyle.LINE);

    private String render(SelectStatement statement) {
        return new DialectSqlRenderer(ansi).render(statement, COMMENTED).sql();
    }

    /** 1: unconstrained level members (the SqlTupleReader / SqlMemberSource recorder shape). */
    private String levelMembers() {
        QueryRecorder q = new QueryRecorder(true);
        q.setHeaderComment("members [Store].[Store City] (cube Sales)");
        q.addFromTable(null, "store", "store", null, null, false);
        q.commentFrom("store", "dimension [Store] level table [Store].[Store City]");
        RolapColumn city = new RolapColumn("store", "store_city");
        String cityAlias = q.addSelectExprCommented(city, BestFitColumnType.STRING,
                "level key [Store].[Store City]");
        q.addSelectExprCommented(new RolapColumn("store", "store_sqft"), BestFitColumnType.INT,
                "member property Store Sqft");
        q.addGroupByExpr(city, cityAlias);
        q.addGroupByExpr(new RolapColumn("store", "store_sqft"), "c1");
        q.addOrderByExpr(city, cityAlias, SortingDirection.ASC, false, true, true);
        q.setFooterComment("members via DefaultTupleConstraint");
        return render(q.buildStatement(ansi));
    }

    /** 2: constrained members — slicer + context WHERE plus the (nonempty) fact join. */
    private String constrainedMembers() {
        QueryRecorder q = new QueryRecorder(true);
        q.setHeaderComment("members [Gender].[Gender] (cube Sales)");
        q.addFromTable(null, "customer", "customer", null, null, false);
        q.addFromTable(null, "sales_fact", "sales_fact", null, null, false);
        q.addFromTable(null, "time_by_day", "time_by_day", null, null, false);
        q.addJoinCondition(new RolapColumn("sales_fact", "customer_id"),
                new RolapColumn("customer", "customer_id"));
        q.addJoinCondition(new RolapColumn("sales_fact", "time_id"),
                new RolapColumn("time_by_day", "time_id"));
        q.commentFrom("customer", "dimension [Gender] level table [Gender].[Gender]");
        q.commentFrom("sales_fact", "fact join (nonempty)");
        q.commentFrom("time_by_day", "snowflake [Time].[Quarter]");
        RolapColumn gender = new RolapColumn("customer", "gender");
        String genderAlias = q.addSelectExprCommented(gender, BestFitColumnType.STRING,
                "level key [Gender].[Gender]");
        q.addWhere(Predicates.comparison(
                Expressions.column(org.eclipse.daanse.sql.statement.api.model.TableAlias.of("time_by_day"),
                        "quarter"),
                ComparisonOperator.EQ, Expressions.literal("Q1", Datatype.VARCHAR)),
                "slicer [Time].[1997].[Q1]");
        q.addWhere(Predicates.comparison(
                Expressions.column(org.eclipse.daanse.sql.statement.api.model.TableAlias.of("customer"),
                        "marital_status"),
                ComparisonOperator.EQ, Expressions.literal("M", Datatype.VARCHAR)),
                "context marital_status");
        q.addGroupByExpr(gender, genderAlias);
        q.addOrderByExpr(gender, genderAlias, SortingDirection.ASC, false, true, true);
        q.setFooterComment("members via SqlContextConstraint");
        return render(q.buildStatement(ansi));
    }

    /** 3: drill-through — via the real producer {@link AggregateSqlMapper#drillThrough}. */
    private String drillThrough() {
        RolapStar.Table fact = table("sales_fact", "sales_fact", null, null);
        RolapStar.Condition join = mock(RolapStar.Condition.class);
        when(join.leftColumn()).thenReturn(Optional.of(new JoinColumn("sales_fact", "time_id")));
        when(join.rightColumn()).thenReturn(Optional.of(new JoinColumn("time_by_day", "time_id")));
        RolapStar.Table time = table("time_by_day", "time_by_day", fact, join);

        RolapStar.Column quarter = mock(RolapStar.Column.class);
        when(quarter.getName()).thenReturn("Quarter");
        when(quarter.getTable()).thenReturn(time);
        when(quarter.getExpression()).thenReturn(new RolapColumn("time_by_day", "quarter"));
        when(quarter.getInternalType()).thenReturn(BestFitColumnType.STRING);

        AggregateSqlMapper.DrillColumn cut = new AggregateSqlMapper.DrillColumn(quarter,
                Predicates.comparison(
                        Expressions.column(org.eclipse.daanse.sql.statement.api.model.TableAlias.of("time_by_day"),
                                "quarter"),
                        ComparisonOperator.EQ, Expressions.literal("Q1", Datatype.VARCHAR)),
                true, "Quarter", true);
        List<AggregateSqlMapper.RawProjection> raw = List.of(
                new AggregateSqlMapper.RawProjection(
                        Expressions.column(org.eclipse.daanse.sql.statement.api.model.TableAlias.of("sales_fact"),
                                "unit_sales"),
                        null, "Unit Sales", "measure Unit Sales"),
                new AggregateSqlMapper.RawProjection(
                        Expressions.raw("NULL"), BestFitColumnType.STRING, "Promotion Name",
                        "inapplicable member"));
        SelectStatement statement = AggregateSqlMapper.drillThrough(
                fact, List.of(cut), List.of(), raw, false, 1000, ansi);
        return render(statement);
    }

    /** 4: native filter — the HAVING attachment (RolapNativeFilter's fork.addHaving). */
    private String nativeFilterHaving() {
        QueryRecorder q = new QueryRecorder(true);
        q.setHeaderComment("members [Store].[Store City] (cube Sales)");
        q.addFromTable(null, "store", "store", null, null, false);
        q.addFromTable(null, "sales_fact", "sales_fact", null, null, false);
        q.addJoinCondition(new RolapColumn("sales_fact", "store_id"),
                new RolapColumn("store", "store_id"));
        q.commentFrom("store", "dimension [Store] level table [Store].[Store City]");
        q.commentFrom("sales_fact", "fact join (nonempty)");
        RolapColumn city = new RolapColumn("store", "store_city");
        String cityAlias = q.addSelectExprCommented(city, BestFitColumnType.STRING,
                "level key [Store].[Store City]");
        q.addGroupByExpr(city, cityAlias);
        q.addHaving(Predicates.raw("(sum(\"sales_fact\".\"unit_sales\") > 100)"),
                "native filter [Measures].[Unit Sales] > 100");
        q.addOrderByExpr(city, cityAlias, SortingDirection.ASC, false, true, true);
        q.setFooterComment("tuples via FilterConstraint");
        return render(q.buildStatement(ansi));
    }

    /**
     * 5: virtual-cube UNION — the SqlTupleReader multi-base-cube composition
     * ({@code select * from (<arm> union <arm>) as unionQuery order by 1}). Statement-level comment
     * slots: only {@code SelectStatement} has a header; SetOperation / WithStatement / Insert /
     * Update / Delete carry a FOOTER slot only. In the diagnostic combination (formatted +
     * comments) the renderer now renders derived tables — including the set's inputs — FORMATTED
     * with their comments and indent stacking, so each arm's own header / projection comments show
     * up inside the parentheses; the wrapper select still carries the union-level provenance
     * (header + footer), exactly as SqlTupleReader sets it. Executed SQL (comments off) renders the
     * arms compact and comment-free, byte-identically to before.
     */
    private String virtualCubeUnion() {
        org.eclipse.daanse.sql.statement.api.SelectStatementBuilder arm1 =
                org.eclipse.daanse.sql.statement.api.SelectStatementBuilder.create();
        arm1.header("members [Time].[Quarter] (cube Sales)"); // shown formatted in diagnostic mode
        arm1.from(org.eclipse.daanse.sql.statement.api.From.table("time_by_day",
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of("time_by_day")));
        arm1.project(Expressions.column(
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of("time_by_day"), "quarter"),
                BestFitColumnType.STRING, null, "level key [Time].[Quarter]");
        org.eclipse.daanse.sql.statement.api.SelectStatementBuilder arm2 =
                org.eclipse.daanse.sql.statement.api.SelectStatementBuilder.create();
        arm2.header("members [Time].[Quarter] (cube Warehouse)"); // shown as well
        arm2.from(org.eclipse.daanse.sql.statement.api.From.table("time_by_day_w",
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of("time_by_day_w")));
        arm2.project(Expressions.column(
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of("time_by_day_w"), "quarter"),
                BestFitColumnType.STRING, null, "level key [Time].[Quarter]");

        org.eclipse.daanse.sql.statement.api.SelectStatementBuilder wrapper =
                org.eclipse.daanse.sql.statement.api.SelectStatementBuilder.create();
        wrapper.header("virtual cube union (2 base cubes)");
        wrapper.footerComment("tuples via SqlContextConstraint (union of 2 base cubes)");
        wrapper.project(Expressions.star(), null);
        wrapper.from(org.eclipse.daanse.sql.statement.api.From.set(
                new org.eclipse.daanse.sql.statement.api.model.SetOperation(
                        org.eclipse.daanse.sql.statement.api.model.SetOperation.SetOp.UNION,
                        List.of(arm1.build(), arm2.build()), List.of(), Optional.empty()),
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of("unionQuery")));
        wrapper.orderOn(Expressions.ordinal(1),
                new org.eclipse.daanse.sql.statement.api.model.SortSpec(
                        org.eclipse.daanse.sql.statement.api.model.SortDirection.ASC, false,
                        org.eclipse.daanse.sql.statement.api.model.NullOrder.LAST, false));
        return render(wrapper.build());
    }

    /**
     * 6: cardinality probe — the {@code TupleSqlMapper.levelMemberCountSql} shape
     * ({@code select count(*) from (select distinct <keys>) as init}, consumed by
     * {@code SqlMemberSource.getLevelCardinalityFromSql}), built through the same attachment points
     * the producer uses: outer header ({@code level cardinality <level>}) + footer
     * ({@code cardinality probe (count distinct keys)}), inner header ({@code distinct level
     * keys}), inner per-level {@code level key} projection comments and the inner base-table
     * provenance ({@code TupleSqlMapper.baseTableComment} wording). In diagnostic mode the derived
     * table renders FORMATTED with LINE comments, indented one level deeper (indent stacking).
     */
    private String cardinalityProbe() {
        org.eclipse.daanse.sql.statement.api.model.TableAlias store =
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of("store");
        org.eclipse.daanse.sql.statement.api.SelectStatementBuilder inner =
                org.eclipse.daanse.sql.statement.api.SelectStatementBuilder.create();
        inner.header("distinct level keys");
        inner.distinct(true);
        inner.from(org.eclipse.daanse.sql.statement.api.From.commentBase(
                org.eclipse.daanse.sql.statement.api.From.table("store", store),
                "dimension [Store] level table [Store].[Store State]"));
        inner.project(Expressions.column(store, "store_state"), BestFitColumnType.STRING, null,
                "level key [Store].[Store State]");

        org.eclipse.daanse.sql.statement.api.SelectStatementBuilder outer =
                org.eclipse.daanse.sql.statement.api.SelectStatementBuilder.create();
        outer.header("level cardinality [Store].[Store].[Store State]");
        outer.footerComment("cardinality probe (count distinct keys)");
        outer.project(Expressions.aggregate("count", Expressions.star()), null);
        outer.from(org.eclipse.daanse.sql.statement.api.From.subquery(inner.build(),
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of("init")));
        return render(outer.build());
    }

    private static RolapStar.Table table(String name, String alias, RolapStar.Table parent,
            RolapStar.Condition join) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getTableName()).thenReturn(name);
        when(t.getAlias()).thenReturn(alias);
        when(t.getParentTable()).thenReturn(parent);
        when(t.getJoinCondition()).thenReturn(join);
        return t;
    }

    @Test
    void writesCommentedSqlDemo() throws Exception {
        String demo = new StringBuilder()
                .append("=== 1. level members (unconstrained) ===\n")
                .append(levelMembers()).append("\n\n")
                .append("=== 2. level members (slicer + context + fact join) ===\n")
                .append(constrainedMembers()).append("\n\n")
                .append("=== 3. drill-through (AggregateSqlMapper.drillThrough) ===\n")
                .append(drillThrough()).append("\n\n")
                .append("=== 4. native filter (HAVING) ===\n")
                .append(nativeFilterHaving()).append("\n\n")
                .append("=== 5. virtual-cube union (wrapper header/footer; arms formatted in diagnostic mode) ===\n")
                .append(virtualCubeUnion()).append("\n\n")
                .append("=== 6. cardinality probe (TupleSqlMapper.levelMemberCountSql shape) ===\n")
                .append(cardinalityProbe()).append("\n")
                .toString();

        Path out = Path.of("target", "commented-sql-demo.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, demo, StandardCharsets.UTF_8);

        // Pin the stable comment vocabulary (LINE style: `-- comment` on its own line; footer block).
        // Base-FROM provenance: every statement's BASE table carries a comment on the line above it
        // (dimension roots: `dimension [X] level table [level]`; fact roots: `fact table …`).
        String ls = System.lineSeparator();
        assertThat(demo)
                .contains("-- dimension [Store] level table [Store].[Store City]" + ls
                        + "    \"store\" as \"store\"")
                .contains("-- dimension [Gender] level table [Gender].[Gender]" + ls
                        + "    \"customer\" as \"customer\"")
                .contains("-- fact table sales_fact" + ls
                        + "    \"sales_fact\" as \"sales_fact\"")
                .contains("-- members [Store].[Store City] (cube Sales)")
                .contains("-- level key [Store].[Store City]")
                .contains("-- member property Store Sqft")
                .contains("/* members via DefaultTupleConstraint */")
                .contains("-- slicer [Time].[1997].[Q1]")
                .contains("-- context marital_status")
                .contains("-- fact join (nonempty)")
                .contains("-- snowflake [Time].[Quarter]")
                .contains("/* members via SqlContextConstraint */")
                .contains("-- drill-through fact sales_fact")
                .contains("-- measure Unit Sales")
                .contains("-- inapplicable member")
                .contains("/* drill-through request */")
                .contains("-- native filter [Measures].[Unit Sales] > 100")
                .contains("/* tuples via FilterConstraint */")
                .contains("-- virtual cube union (2 base cubes)")
                .contains("/* tuples via SqlContextConstraint (union of 2 base cubes) */")
                // Diagnostic mode renders the union arms FORMATTED with their comments (indent-stacked).
                .contains("-- members [Time].[Quarter] (cube Sales)")
                .contains("-- members [Time].[Quarter] (cube Warehouse)")
                .contains("-- level key [Time].[Quarter]")
                // 6: the cardinality probe — outer LINE header/footer + the nested-formatted inner select.
                .contains("-- level cardinality [Store].[Store].[Store State]")
                .contains("        -- distinct level keys" + ls + "        select distinct")
                .contains("            -- level key [Store].[Store State]")
                .contains("            -- dimension [Store] level table [Store].[Store State]" + ls
                        + "            \"store\" as \"store\"")
                .contains("    ) as \"init\"")
                .contains("/* cardinality probe (count distinct keys) */");

        // The executed render (comments off) of the same statements carries NO comment text.
        QueryRecorder q = new QueryRecorder(false);
        q.setHeaderComment("members [Store].[Store City] (cube Sales)");
        q.addFromTable(null, "store", "store", null, null, false);
        q.addSelectExprCommented(new RolapColumn("store", "store_city"), BestFitColumnType.STRING,
                "level key [Store].[Store City]");
        q.setFooterComment("members via DefaultTupleConstraint");
        String executed = q.toSqlAndTypes(ansi).sql();
        assertThat(executed).doesNotContain("--").doesNotContain("/*");
    }
}
