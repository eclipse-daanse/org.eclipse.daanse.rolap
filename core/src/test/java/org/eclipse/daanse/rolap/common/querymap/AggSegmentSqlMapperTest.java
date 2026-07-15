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
package org.eclipse.daanse.rolap.common.querymap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator;
import org.eclipse.daanse.rolap.aggregator.SqlNodeAggregator;
import org.eclipse.daanse.rolap.aggregator.SumAggregator;
import org.eclipse.daanse.rolap.common.sqlbuild.AggregateSqlMapper;
import org.eclipse.daanse.rolap.common.sqlbuild.AggregateSqlMapper.AggFromTable;
import org.eclipse.daanse.rolap.common.sqlbuild.AggregateSqlMapper.AggSegmentColumn;
import org.eclipse.daanse.rolap.common.sqlbuild.AggregateSqlMapper.AggSegmentMeasure;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the SQL {@link AggregateSqlMapper#aggSegment} produces for aggregate-table rollup
 * segment loads over FoodMart-shaped agg tables:
 * <ul>
 * <li>fact-table-only rollup (columns and measure on the agg fact),</li>
 * <li>a collapsed-dimension read whose first constraining column lives on a dimension table — the
 *     FROM is rooted at that dimension with the agg fact joined to it,</li>
 * <li>a snowflake chain registered self-first ({@code product_class} before {@code product}) that
 *     the fold joins parent-first off the agg fact,</li>
 * <li>grouping-sets and distinct-count rollup shapes, the raw fake-column predicate, and the
 *     computed-column projection.</li>
 * </ul>
 */
class AggSegmentSqlMapperTest {

    private final AnsiDialect ansi = new AnsiDialect();

    private static SqlExpression col(String table, String column) {
        return Expressions.column(TableAlias.of(table), column);
    }

    private static AggFromTable table(String name, AggFromTable parent, Predicate joinToParent) {
        return new AggFromTable(name, From.table(name, TableAlias.of(name)), parent, joinToParent,
                null, parent == null ? "agg table " + name : null);
    }

    private String render(List<AggSegmentColumn> columns, List<AggSegmentMeasure> measures,
            AggFromTable fact) {
        return new DialectSqlRenderer(ansi)
                .render(AggregateSqlMapper.aggSegment(columns, measures, fact, "Sales"))
                .sql();
    }

    private String renderNonRollup(List<AggSegmentColumn> columns, List<AggSegmentMeasure> measures,
            AggFromTable fact) {
        return new DialectSqlRenderer(ansi)
                .render(AggregateSqlMapper.aggSegment(columns, measures, fact, "Sales", false))
                .sql();
    }

    /** Rollup over agg_c_10 with both group-by columns on the agg fact table. */
    @Test
    void reproducesFactOnlyRollup() {
        AggFromTable fact = table("agg_c_10_sales_fact_1997", null, null);
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(fact, col("agg_c_10_sales_fact_1997", "the_year"),
                        BestFitColumnType.INT,
                        Predicates.comparison(col("agg_c_10_sales_fact_1997", "the_year"),
                                ComparisonOperator.EQ, Expressions.literal(1997, Datatype.INTEGER)),
                        "the_year"),
                new AggSegmentColumn(fact, col("agg_c_10_sales_fact_1997", "quarter"), null, null,
                        "quarter"));
        List<AggSegmentMeasure> measures = List.of(new AggSegmentMeasure(
                SumAggregator.INSTANCE.toNode(col("agg_c_10_sales_fact_1997", "unit_sales")),
                "measure Unit Sales (sum)"));

        assertThat(render(columns, measures, fact)).isEqualTo(
                "select \"agg_c_10_sales_fact_1997\".\"the_year\" as \"c0\","
                + " \"agg_c_10_sales_fact_1997\".\"quarter\" as \"c1\","
                + " sum(\"agg_c_10_sales_fact_1997\".\"unit_sales\") as \"m0\""
                + " from \"agg_c_10_sales_fact_1997\" as \"agg_c_10_sales_fact_1997\""
                + " where \"agg_c_10_sales_fact_1997\".\"the_year\" = 1997"
                + " group by \"agg_c_10_sales_fact_1997\".\"the_year\","
                + " \"agg_c_10_sales_fact_1997\".\"quarter\"");
    }

    /**
     * The {@code rollup=false} exact-granularity read: identical SELECT / FROM / WHERE to the
     * fact-only rollup above but WITHOUT the trailing {@code GROUP BY} — the constraining columns
     * are projected, not grouped, and the measure carries its plain (un-rolled) node.
     */
    @Test
    void omitsGroupByForNonRollupExactGranularity() {
        AggFromTable fact = table("agg_c_10_sales_fact_1997", null, null);
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(fact, col("agg_c_10_sales_fact_1997", "the_year"),
                        BestFitColumnType.INT,
                        Predicates.comparison(col("agg_c_10_sales_fact_1997", "the_year"),
                                ComparisonOperator.EQ, Expressions.literal(1997, Datatype.INTEGER)),
                        "the_year"),
                new AggSegmentColumn(fact, col("agg_c_10_sales_fact_1997", "quarter"), null, null,
                        "quarter"));
        List<AggSegmentMeasure> measures = List.of(new AggSegmentMeasure(
                SumAggregator.INSTANCE.toNode(col("agg_c_10_sales_fact_1997", "unit_sales")),
                "measure Unit Sales (sum)"));

        assertThat(renderNonRollup(columns, measures, fact)).isEqualTo(
                "select \"agg_c_10_sales_fact_1997\".\"the_year\" as \"c0\","
                + " \"agg_c_10_sales_fact_1997\".\"quarter\" as \"c1\","
                + " sum(\"agg_c_10_sales_fact_1997\".\"unit_sales\") as \"m0\""
                + " from \"agg_c_10_sales_fact_1997\" as \"agg_c_10_sales_fact_1997\""
                + " where \"agg_c_10_sales_fact_1997\".\"the_year\" = 1997");
    }

    /**
     * A non-FK distinct-count measure rolls up with its rollup aggregator — {@code sum} of the
     * pre-aggregated counts ({@code AggStar.FactTable.Measure.generateRollupExpression}'s swap; an
     * FK-based one swaps to the non-distinct {@code count}), never {@code count(distinct …)}.
     */
    @Test
    void distinctRollupCandidateRollsUpWithTheSwappedAggregator() {
        AggFromTable fact = table("exp_agg_test_distinct_count", null, null);
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(fact, col("exp_agg_test_distinct_count", "testyear"),
                        BestFitColumnType.INT,
                        Predicates.comparison(col("exp_agg_test_distinct_count", "testyear"),
                                ComparisonOperator.EQ, Expressions.literal(1997, Datatype.INTEGER)),
                        "testyear"),
                new AggSegmentColumn(fact, col("exp_agg_test_distinct_count", "store_name"), null,
                        Predicates.comparison(col("exp_agg_test_distinct_count", "store_name"),
                                ComparisonOperator.EQ,
                                Expressions.literal("Store 16", Datatype.VARCHAR)),
                        "store_name"));
        List<AggSegmentMeasure> measures = List.of(
                new AggSegmentMeasure(
                        SumAggregator.INSTANCE.toNode(col("exp_agg_test_distinct_count", "unit_s")),
                        "measure Unit Sales (sum)"),
                // the distinct-count measure AFTER the swap: DistinctCountAggregator.getRollup()
                // == SumAggregator (non-FK), so the node is sum(cust_cnt) — never count(distinct …)
                new AggSegmentMeasure(
                        SqlNodeAggregator.toNodeOrNull(DistinctCountAggregator.INSTANCE.getRollup(), col("exp_agg_test_distinct_count", "cust_cnt")),
                        "measure Customer Count (distinct-count)"));

        assertThat(render(columns, measures, fact)).isEqualTo(
                "select \"exp_agg_test_distinct_count\".\"testyear\" as \"c0\","
                + " \"exp_agg_test_distinct_count\".\"store_name\" as \"c1\","
                + " sum(\"exp_agg_test_distinct_count\".\"unit_s\") as \"m0\","
                + " sum(\"exp_agg_test_distinct_count\".\"cust_cnt\") as \"m1\""
                + " from \"exp_agg_test_distinct_count\" as \"exp_agg_test_distinct_count\""
                + " where \"exp_agg_test_distinct_count\".\"testyear\" = 1997"
                + " and \"exp_agg_test_distinct_count\".\"store_name\" = 'Store 16'"
                + " group by \"exp_agg_test_distinct_count\".\"testyear\","
                + " \"exp_agg_test_distinct_count\".\"store_name\"");
    }

    /**
     * On a dialect that supports grouping sets, {@code GROUP BY GROUPING SETS ((keys), ())}
     * replaces the plain group-by spelling and each rollup column surfaces as a {@code GROUPING(x)}
     * SELECT-tail column (renderer-aliased {@code g0}) after the measures.
     */
    @Test
    void groupingSetsRenderGroupingSetsWhenSupported() {
        AggFromTable fact = table("agg_c_10_sales_fact_1997", null, null);
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(fact, col("agg_c_10_sales_fact_1997", "the_year"),
                        BestFitColumnType.INT, null, "the_year"));
        List<AggSegmentMeasure> measures = List.of(new AggSegmentMeasure(
                SumAggregator.INSTANCE.toNode(col("agg_c_10_sales_fact_1997", "unit_sales")),
                "measure Unit Sales (sum)"));
        AggregateSqlMapper.AggShape shape = new AggregateSqlMapper.AggShape(
                List.of(List.of(col("agg_c_10_sales_fact_1997", "the_year")), List.of()),
                List.of(col("agg_c_10_sales_fact_1997", "the_year")));

        String sql = new DialectSqlRenderer(new AnsiDialect() {
                    @Override
                    public boolean supportsGroupingSets() {
                        return true;
                    }
                })
                .render(AggregateSqlMapper.aggSegment(columns, measures, fact, "Sales", true, shape))
                .sql();

        assertThat(sql).isEqualTo(
                "select \"agg_c_10_sales_fact_1997\".\"the_year\" as \"c0\","
                + " sum(\"agg_c_10_sales_fact_1997\".\"unit_sales\") as \"m0\","
                + " GROUPING(\"agg_c_10_sales_fact_1997\".\"the_year\") as \"g0\""
                + " from \"agg_c_10_sales_fact_1997\" as \"agg_c_10_sales_fact_1997\""
                + " group by grouping sets ((\"agg_c_10_sales_fact_1997\".\"the_year\"), ())");
    }

    /** Grouping sets on a dialect WITHOUT support: the renderer falls back to the plain group-by
     *  keys (capability spelled at the renderer, never gated in the mapper); the {@code GROUPING(x)}
     *  column still projects. */
    @Test
    void groupingSetsFallBackToPlainGroupByWhenUnsupported() {
        AggFromTable fact = table("agg_c_10_sales_fact_1997", null, null);
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(fact, col("agg_c_10_sales_fact_1997", "the_year"),
                        BestFitColumnType.INT, null, "the_year"));
        List<AggSegmentMeasure> measures = List.of(new AggSegmentMeasure(
                SumAggregator.INSTANCE.toNode(col("agg_c_10_sales_fact_1997", "unit_sales")),
                "measure Unit Sales (sum)"));
        AggregateSqlMapper.AggShape shape = new AggregateSqlMapper.AggShape(
                List.of(List.of(col("agg_c_10_sales_fact_1997", "the_year")), List.of()),
                List.of(col("agg_c_10_sales_fact_1997", "the_year")));

        String sql = new DialectSqlRenderer(new AnsiDialect() {
                    @Override
                    public boolean supportsGroupingSets() {
                        return false;
                    }
                })
                .render(AggregateSqlMapper.aggSegment(columns, measures, fact, "Sales", true, shape))
                .sql();

        assertThat(sql).isEqualTo(
                "select \"agg_c_10_sales_fact_1997\".\"the_year\" as \"c0\","
                + " sum(\"agg_c_10_sales_fact_1997\".\"unit_sales\") as \"m0\","
                + " GROUPING(\"agg_c_10_sales_fact_1997\".\"the_year\") as \"g0\""
                + " from \"agg_c_10_sales_fact_1997\" as \"agg_c_10_sales_fact_1997\""
                + " group by \"agg_c_10_sales_fact_1997\".\"the_year\"");
    }

    /**
     * The distinct-rollup × grouping-sets combination: the swapped distinct-count node
     * ({@code sum(cust_cnt)} — {@code DistinctCountAggregator.getRollup()} == {@code sum}, non-FK)
     * flows unchanged into the {@link AggregateSqlMapper.AggShape} grouping-sets form. On a dialect
     * that supports grouping sets, {@code GROUP BY GROUPING SETS ((keys), ())} plus the trailing
     * {@code GROUPING(x)} column.
     */
    @Test
    void distinctRollupWithGroupingSetsRendersGroupingSetsWhenSupported() {
        AggFromTable fact = table("exp_agg_test_distinct_count", null, null);
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(fact, col("exp_agg_test_distinct_count", "testyear"),
                        BestFitColumnType.INT, null, "testyear"));
        List<AggSegmentMeasure> measures = List.of(new AggSegmentMeasure(
                SqlNodeAggregator.toNodeOrNull(DistinctCountAggregator.INSTANCE.getRollup(), col("exp_agg_test_distinct_count", "cust_cnt")),
                "measure Customer Count (distinct-count)"));
        AggregateSqlMapper.AggShape shape = new AggregateSqlMapper.AggShape(
                List.of(List.of(col("exp_agg_test_distinct_count", "testyear")), List.of()),
                List.of(col("exp_agg_test_distinct_count", "testyear")));

        String sql = new DialectSqlRenderer(new AnsiDialect() {
                    @Override
                    public boolean supportsGroupingSets() {
                        return true;
                    }
                })
                .render(AggregateSqlMapper.aggSegment(columns, measures, fact, "Sales", true, shape))
                .sql();

        assertThat(sql).isEqualTo(
                "select \"exp_agg_test_distinct_count\".\"testyear\" as \"c0\","
                + " sum(\"exp_agg_test_distinct_count\".\"cust_cnt\") as \"m0\","
                + " GROUPING(\"exp_agg_test_distinct_count\".\"testyear\") as \"g0\""
                + " from \"exp_agg_test_distinct_count\" as \"exp_agg_test_distinct_count\""
                + " group by grouping sets ((\"exp_agg_test_distinct_count\".\"testyear\"), ())");
    }

    /**
     * The same distinct-rollup × grouping-sets shape on a dialect WITHOUT grouping-sets support:
     * the renderer falls back to the plain group-by keys, the {@code GROUPING(x)} column still
     * projecting. Capability is spelled at the renderer, never gated in the mapper.
     */
    @Test
    void distinctRollupWithGroupingSetsFallsBackToPlainGroupByWhenUnsupported() {
        AggFromTable fact = table("exp_agg_test_distinct_count", null, null);
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(fact, col("exp_agg_test_distinct_count", "testyear"),
                        BestFitColumnType.INT, null, "testyear"));
        List<AggSegmentMeasure> measures = List.of(new AggSegmentMeasure(
                SqlNodeAggregator.toNodeOrNull(DistinctCountAggregator.INSTANCE.getRollup(), col("exp_agg_test_distinct_count", "cust_cnt")),
                "measure Customer Count (distinct-count)"));
        AggregateSqlMapper.AggShape shape = new AggregateSqlMapper.AggShape(
                List.of(List.of(col("exp_agg_test_distinct_count", "testyear")), List.of()),
                List.of(col("exp_agg_test_distinct_count", "testyear")));

        String sql = new DialectSqlRenderer(new AnsiDialect() {
                    @Override
                    public boolean supportsGroupingSets() {
                        return false;
                    }
                })
                .render(AggregateSqlMapper.aggSegment(columns, measures, fact, "Sales", true, shape))
                .sql();

        assertThat(sql).isEqualTo(
                "select \"exp_agg_test_distinct_count\".\"testyear\" as \"c0\","
                + " sum(\"exp_agg_test_distinct_count\".\"cust_cnt\") as \"m0\","
                + " GROUPING(\"exp_agg_test_distinct_count\".\"testyear\") as \"g0\""
                + " from \"exp_agg_test_distinct_count\" as \"exp_agg_test_distinct_count\""
                + " group by \"exp_agg_test_distinct_count\".\"testyear\"");
    }

    /**
     * The first constraining column lives on the {@code store} dimension, so the FROM is rooted at
     * {@code store} and the agg fact joins to it via the dim table's join condition in stored
     * orientation (fact-side {@code left} = dim-side {@code right}).
     */
    @Test
    void rootsFromAtTheFirstReferencedDimensionTable() {
        AggFromTable fact = table("agg_c_14_sales_fact_1997", null, null);
        AggFromTable store = table("store", fact,
                Predicates.comparison(col("agg_c_14_sales_fact_1997", "store_id"),
                        ComparisonOperator.EQ, col("store", "store_id")));
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(store, col("store", "store_country"), null,
                        Predicates.comparison(col("store", "store_country"), ComparisonOperator.EQ,
                                Expressions.literal("USA", Datatype.VARCHAR)),
                        "store_country"),
                new AggSegmentColumn(fact, col("agg_c_14_sales_fact_1997", "the_year"),
                        BestFitColumnType.INT, null, "the_year"));
        List<AggSegmentMeasure> measures = List.of(new AggSegmentMeasure(
                SumAggregator.INSTANCE.toNode(col("agg_c_14_sales_fact_1997", "unit_sales")),
                "measure Unit Sales (sum)"));

        assertThat(render(columns, measures, fact)).isEqualTo(
                "select \"store\".\"store_country\" as \"c0\","
                + " \"agg_c_14_sales_fact_1997\".\"the_year\" as \"c1\","
                + " sum(\"agg_c_14_sales_fact_1997\".\"unit_sales\") as \"m0\""
                + " from \"store\" as \"store\""
                + " join \"agg_c_14_sales_fact_1997\" as \"agg_c_14_sales_fact_1997\""
                + " on \"agg_c_14_sales_fact_1997\".\"store_id\" = \"store\".\"store_id\""
                + " where \"store\".\"store_country\" = 'USA'"
                + " group by \"store\".\"store_country\", \"agg_c_14_sales_fact_1997\".\"the_year\"");
    }

    /**
     * A predicate created WITHOUT a constrained column is carried as a {@code Predicates.raw}
     * conjunct verbatim next to the translated ones.
     */
    @Test
    void fakeColumnPredicateCarriesTheRawCreateInExprString() {
        AggFromTable fact = table("agg_c_10_sales_fact_1997", null, null);
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(fact, col("agg_c_10_sales_fact_1997", "the_year"),
                        BestFitColumnType.INT,
                        Predicates.raw("\"agg_c_10_sales_fact_1997\".\"the_year\" in (1997, 1998)"),
                        "the_year"));
        List<AggSegmentMeasure> measures = List.of(new AggSegmentMeasure(
                SumAggregator.INSTANCE.toNode(col("agg_c_10_sales_fact_1997", "unit_sales")),
                "measure Unit Sales (sum)"));

        assertThat(render(columns, measures, fact)).isEqualTo(
                "select \"agg_c_10_sales_fact_1997\".\"the_year\" as \"c0\","
                + " sum(\"agg_c_10_sales_fact_1997\".\"unit_sales\") as \"m0\""
                + " from \"agg_c_10_sales_fact_1997\" as \"agg_c_10_sales_fact_1997\""
                + " where \"agg_c_10_sales_fact_1997\".\"the_year\" in (1997, 1998)"
                + " group by \"agg_c_10_sales_fact_1997\".\"the_year\"");
    }

    /**
     * A computed agg column carries an {@code Expressions.rawVariant} node (a per-dialect SQL map)
     * that the renderer resolves at render time; SELECT and GROUP BY spell the resolved variant.
     */
    @Test
    void computedColumnProjectsItsRawVariantNode() {
        AggFromTable fact = table("agg_c_10_sales_fact_1997", null, null);
        SqlExpression computed = Expressions.rawVariant(java.util.Map.of(
                "generic", "\"agg_c_10_sales_fact_1997\".\"the_year\" + 1"));
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(fact, computed, BestFitColumnType.INT, null, "the_year_next"));
        List<AggSegmentMeasure> measures = List.of(new AggSegmentMeasure(
                SumAggregator.INSTANCE.toNode(col("agg_c_10_sales_fact_1997", "unit_sales")),
                "measure Unit Sales (sum)"));

        assertThat(render(columns, measures, fact)).isEqualTo(
                "select \"agg_c_10_sales_fact_1997\".\"the_year\" + 1 as \"c0\","
                + " sum(\"agg_c_10_sales_fact_1997\".\"unit_sales\") as \"m0\""
                + " from \"agg_c_10_sales_fact_1997\" as \"agg_c_10_sales_fact_1997\""
                + " group by \"agg_c_10_sales_fact_1997\".\"the_year\" + 1");
    }

    /**
     * A snowflake column ({@code product_class.product_family}) registers its chain self-first
     * (product_class, then product), but the fold joins parent-first off the placed agg fact —
     * {@code JOIN product ON fact→product}, then {@code JOIN product_class ON product→product_class}.
     */
    @Test
    void foldsSelfFirstSnowflakeChainParentFirst() {
        AggFromTable fact = table("agg_c_14_sales_fact_1997", null, null);
        AggFromTable product = table("product", fact,
                Predicates.comparison(col("agg_c_14_sales_fact_1997", "product_id"),
                        ComparisonOperator.EQ, col("product", "product_id")));
        AggFromTable productClass = table("product_class", product,
                Predicates.comparison(col("product", "product_class_id"), ComparisonOperator.EQ,
                        col("product_class", "product_class_id")));
        List<AggSegmentColumn> columns = List.of(
                new AggSegmentColumn(fact, col("agg_c_14_sales_fact_1997", "the_year"),
                        BestFitColumnType.INT,
                        Predicates.comparison(col("agg_c_14_sales_fact_1997", "the_year"),
                                ComparisonOperator.EQ, Expressions.literal(1997, Datatype.INTEGER)),
                        "the_year"),
                new AggSegmentColumn(fact, col("agg_c_14_sales_fact_1997", "quarter"), null,
                        Predicates.comparison(col("agg_c_14_sales_fact_1997", "quarter"),
                                ComparisonOperator.EQ, Expressions.literal("Q1", Datatype.VARCHAR)),
                        "quarter"),
                new AggSegmentColumn(productClass, col("product_class", "product_family"), null,
                        null, "product_family"));
        List<AggSegmentMeasure> measures = List.of(new AggSegmentMeasure(
                SumAggregator.INSTANCE.toNode(col("agg_c_14_sales_fact_1997", "fact_count")),
                "measure Fact Count (sum)"));

        assertThat(render(columns, measures, fact)).isEqualTo(
                "select \"agg_c_14_sales_fact_1997\".\"the_year\" as \"c0\","
                + " \"agg_c_14_sales_fact_1997\".\"quarter\" as \"c1\","
                + " \"product_class\".\"product_family\" as \"c2\","
                + " sum(\"agg_c_14_sales_fact_1997\".\"fact_count\") as \"m0\""
                + " from \"agg_c_14_sales_fact_1997\" as \"agg_c_14_sales_fact_1997\""
                + " join \"product\" as \"product\""
                + " on \"agg_c_14_sales_fact_1997\".\"product_id\" = \"product\".\"product_id\""
                + " join \"product_class\" as \"product_class\""
                + " on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\""
                + " where \"agg_c_14_sales_fact_1997\".\"the_year\" = 1997"
                + " and \"agg_c_14_sales_fact_1997\".\"quarter\" = 'Q1'"
                + " group by \"agg_c_14_sales_fact_1997\".\"the_year\","
                + " \"agg_c_14_sales_fact_1997\".\"quarter\", \"product_class\".\"product_family\"");
    }
}
