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
package org.eclipse.daanse.rolap.common.sqlbuild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.AggPlan;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the SQL the agg tuple mapper produces — {@link TupleSqlMapper#aggTupleLevelMembersSql}
 * and the multi-target {@link TupleSqlMapper#collapsedTupleLevelMembersSql} — over FoodMart-shaped
 * aggregate tables:
 * <ul>
 * <li>agg-only multi-target with the {@code AggStar.Table.Level.getOrdinalExps()} channel
 *     ({@code testmonthord} ordered BEFORE {@code testmonthname});</li>
 * <li>key-on-agg + caption-on-dim, inverse dimension FROM joined via the {@code dimKey = aggColumn}
 *     edge;</li>
 * <li>non-collapsed dimension root with an agg-fact existence join, agg-column WHERE and native
 *     HAVING carried;</li>
 * <li>the ON-vs-WHERE fold rule — an edge whose both aliases are already joined becomes a WHERE
 *     conjunct after the context conjuncts.</li>
 * </ul>
 * Mock-built fixtures (no XMI). Needs the Mockito inline mock maker.
 */
class AggTupleSqlMapperTest {

    private final Dialect ansi = new AnsiDialect();

    private String render(org.eclipse.daanse.sql.statement.api.model.SelectStatement statement) {
        return new DialectSqlRenderer(ansi).render(statement).sql();
    }

    // ---- fixtures ----------------------------------------------------------------------------

    private static org.eclipse.daanse.rolap.mapping.model.database.source.TableSource tableSource(
            String physicalName, String alias) {
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet.class);
        when(ncs.getName()).thenReturn(physicalName);
        org.eclipse.daanse.rolap.mapping.model.database.source.TableSource ts =
                mock(org.eclipse.daanse.rolap.mapping.model.database.source.TableSource.class);
        when(ts.getTable()).thenReturn(ncs);
        when(ts.getAlias()).thenReturn(alias);
        return ts;
    }

    /** An aggregate FACT table (its columns make a level COLLAPSED — isLevelCollapsed's rule). */
    private static AggStar.FactTable aggFactTable(String name) {
        org.eclipse.daanse.rolap.mapping.model.database.source.TableSource relation =
                tableSource(name, name);
        AggStar.FactTable fact = mock(AggStar.FactTable.class);
        when(fact.getName()).thenReturn(name);
        when(fact.getRelation()).thenReturn(relation);
        return fact;
    }

    /** An aggregate DIM table (plain Table — NOT FactTable, so its levels are NOT collapsed). */
    private static AggStar.Table aggDimTable(String name, AggStar.Table parent,
            RolapColumn joinLeft, RolapColumn joinRight) {
        org.eclipse.daanse.rolap.mapping.model.database.source.TableSource relation =
                tableSource(name, name);
        AggStar.Table.JoinCondition jc = mock(AggStar.Table.JoinCondition.class);
        when(jc.getLeft()).thenReturn(joinLeft);
        when(jc.getRight()).thenReturn(joinRight);
        AggStar.Table table = mock(AggStar.Table.class);
        when(table.getName()).thenReturn(name);
        when(table.getRelation()).thenReturn(relation);
        when(table.hasParent()).thenReturn(true);
        when(table.getParent()).thenReturn(parent);
        when(table.hasJoinCondition()).thenReturn(true);
        when(table.getJoinCondition()).thenReturn(jc);
        return table;
    }

    private static AggStar.Table.Column aggColumn(AggStar.Table table, RolapColumn expression) {
        // Compute the node BEFORE the when() chains (Mockito unfinished-stubbing rule).
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
                JoinPlanner.expressionFor(expression);
        AggStar.Table.Column column = mock(AggStar.Table.Column.class);
        when(column.getExpression()).thenReturn(expression);
        when(column.getTable()).thenReturn(table);
        when(column.toSqlExpression()).thenReturn(node);
        return column;
    }

    private static RolapCubeLevel level(int depth, RolapColumn key, List<RolapColumn> ordinals,
            RolapColumn caption, int bitPos, String uniqueName) {
        RolapStar.Column starColumn = mock(RolapStar.Column.class);
        when(starColumn.getBitPosition()).thenReturn(bitPos);
        when(starColumn.getInternalType()).thenReturn(BestFitColumnType.STRING);
        RolapCubeLevel l = mock(RolapCubeLevel.class);
        when(l.getDepth()).thenReturn(depth);
        when(l.getKeyExp()).thenReturn(key);
        when(l.getOrdinalExps()).thenAnswer(inv -> ordinals);
        when(l.getCaptionExp()).thenReturn(caption);
        when(l.getProperties()).thenReturn(new RolapProperty[0]);
        when(l.getInternalType()).thenReturn(BestFitColumnType.STRING);
        when(l.getUniqueName()).thenReturn(uniqueName);
        when(l.getStarKeyColumn()).thenReturn(starColumn);
        when(l.getBaseStarKeyColumn(null)).thenReturn(starColumn);
        return l;
    }

    private static RolapCubeHierarchy hierarchy(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            RolapCubeLevel... levels) {
        RolapCubeHierarchy h = mock(RolapCubeHierarchy.class);
        when(h.getRelation()).thenReturn(relation);
        org.mockito.Mockito.doReturn(List.of(levels)).when(h).getLevels();
        for (RolapCubeLevel level : levels) {
            when(level.getHierarchy()).thenReturn(h);
        }
        return h;
    }

    private static AggStar.Table.Level aggLevel(RolapColumn expression, List<RolapColumn> ordinals) {
        AggStar.Table.Level al = mock(AggStar.Table.Level.class);
        when(al.getExpression()).thenReturn(expression);
        when(al.getOrdinalExps()).thenAnswer(inv -> ordinals);
        when(al.getProperties()).thenReturn(Map.of());
        return al;
    }

    // ---- agg-only multi-target with the agg-level ordinal channel -------------------------

    /**
     * {@code tuples [TimeExtra].[Month] x [Gender].[Gender]} on {@code exp_agg_test}:
     * Year/Quarter/Gender are collapsed single-column, Month is collapsed WITH an agg-side ordinal
     * ({@code testmonthord}) — projected after the key, ORDERed BEFORE it (ordinal ≠ key), key as
     * tiebreaker. WHERE is the parenthesised member-set conjunct.
     */
    @Test
    void m1MultiTargetAggOnlyProjectionWithAggOrdinalChannel() {
        String agg = "exp_agg_test";
        AggStar.FactTable fact = aggFactTable(agg);
        AggStar aggStar = mock(AggStar.class);
        AggStar.Table.Column col1_fact = aggColumn(fact, new RolapColumn(agg, "testyear"));
        when(aggStar.lookupColumn(1)).thenReturn(col1_fact);
        AggStar.Table.Column col2_fact = aggColumn(fact, new RolapColumn(agg, "testqtr"));
        when(aggStar.lookupColumn(2)).thenReturn(col2_fact);
        AggStar.Table.Column col3_fact = aggColumn(fact, new RolapColumn(agg, "testmonthname"));
        when(aggStar.lookupColumn(3)).thenReturn(col3_fact);
        AggStar.Table.Column col4_fact = aggColumn(fact, new RolapColumn(agg, "gender"));
        when(aggStar.lookupColumn(4)).thenReturn(col4_fact);
        // Month carries an AggStar Level with an ordinal on the agg table — the ordinal channel.
        AggStar.Table.Level aggLevel3 = aggLevel(new RolapColumn(agg, "testmonthname"),
                List.of(new RolapColumn(agg, "testmonthord")));
        when(aggStar.lookupLevel(3)).thenReturn(aggLevel3);

        RolapCubeLevel year = level(0, new RolapColumn("time_by_day", "the_year"),
                List.of(), null, 1, "[TimeExtra].[Year]");
        RolapCubeLevel quarter = level(1, new RolapColumn("time_by_day", "quarter"),
                List.of(), null, 2, "[TimeExtra].[Quarter]");
        RolapCubeLevel month = level(2, new RolapColumn("time_by_day", "the_month"),
                List.of(new RolapColumn("time_by_day", "month_of_year")), null, 3,
                "[TimeExtra].[Month]");
        hierarchy(tableSource("time_by_day", "time_by_day"), year, quarter, month);
        RolapCubeLevel gender = level(0, new RolapColumn("customer", "gender"),
                List.of(), null, 4, "[Gender].[Gender]");
        hierarchy(tableSource("customer", "customer"), gender);

        String sql = render(AggTupleQueries.collapsedTupleLevelMembersSql(
                List.of(List.of(year, quarter, month), List.of(gender)), aggStar,
                Optional.of(Predicates.and(List.of(Predicates.and(List.of(
                        Predicates.raw("\"exp_agg_test\".\"gender\" = 'M'")))))),
                Optional.empty(), Optional.empty()));

        assertThat(sql).isEqualTo(
                "select \"exp_agg_test\".\"testyear\" as \"c0\","
                + " \"exp_agg_test\".\"testqtr\" as \"c1\","
                + " \"exp_agg_test\".\"testmonthname\" as \"c2\","
                + " \"exp_agg_test\".\"testmonthord\" as \"c3\","
                + " \"exp_agg_test\".\"gender\" as \"c4\""
                + " from \"exp_agg_test\" as \"exp_agg_test\""
                + " where (\"exp_agg_test\".\"gender\" = 'M')"
                + " group by \"exp_agg_test\".\"testyear\", \"exp_agg_test\".\"testqtr\","
                + " \"exp_agg_test\".\"testmonthname\", \"exp_agg_test\".\"testmonthord\","
                + " \"exp_agg_test\".\"gender\""
                + " order by CASE WHEN \"exp_agg_test\".\"testyear\" IS NULL THEN 1 ELSE 0 END,"
                + " \"exp_agg_test\".\"testyear\" ASC,"
                + " CASE WHEN \"exp_agg_test\".\"testqtr\" IS NULL THEN 1 ELSE 0 END,"
                + " \"exp_agg_test\".\"testqtr\" ASC,"
                // the ordinal ORDERs BEFORE the key (c3 before c2), key tiebreaker after.
                + " CASE WHEN \"exp_agg_test\".\"testmonthord\" IS NULL THEN 1 ELSE 0 END,"
                + " \"exp_agg_test\".\"testmonthord\" ASC,"
                + " CASE WHEN \"exp_agg_test\".\"testmonthname\" IS NULL THEN 1 ELSE 0 END,"
                + " \"exp_agg_test\".\"testmonthname\" ASC,"
                + " CASE WHEN \"exp_agg_test\".\"gender\" IS NULL THEN 1 ELSE 0 END,"
                + " \"exp_agg_test\".\"gender\" ASC");
    }

    // ---- dim-join: key on agg, caption on dim, inverse FROM ----------------------

    /**
     * {@code tuples [TimeExtra].[Month] x [Gender].[Gender]} on
     * {@code agg_g_ms_pcat_sales_fact_1997}: Month's KEY is on the agg table
     * ({@code month_of_year}), its CAPTION stays on {@code time_by_day} (identity entry ⇒
     * requiresJoinToDim), so the inverse dimension FROM joins into the agg root via the
     * {@code dimKey = aggColumn} edge (the tree ON). Caption is projected and grouped but never
     * ordered.
     */
    @Test
    void l2DimJoinKeyOnAggCaptionOnDimUsesInverseFromAndDimToAggEdge() {
        String agg = "agg_g_ms_pcat_sales_fact_1997";
        AggStar.FactTable fact = aggFactTable(agg);
        AggStar aggStar = mock(AggStar.class);
        AggStar.Table.Column col1_fact = aggColumn(fact, new RolapColumn(agg, "the_year"));
        when(aggStar.lookupColumn(1)).thenReturn(col1_fact);
        AggStar.Table.Column col2_fact = aggColumn(fact, new RolapColumn(agg, "quarter"));
        when(aggStar.lookupColumn(2)).thenReturn(col2_fact);
        AggStar.Table.Column col3_fact = aggColumn(fact, new RolapColumn(agg, "month_of_year"));
        when(aggStar.lookupColumn(3)).thenReturn(col3_fact);
        AggStar.Table.Column col4_fact = aggColumn(fact, new RolapColumn(agg, "gender"));
        when(aggStar.lookupColumn(4)).thenReturn(col4_fact);
        // Month's agg level substitutes the KEY only — the caption keeps its identity entry.
        AggStar.Table.Level aggLevel3 = aggLevel(new RolapColumn(agg, "month_of_year"), List.of());
        when(aggStar.lookupLevel(3)).thenReturn(aggLevel3);

        RolapCubeLevel year = level(0, new RolapColumn("time_by_day", "the_year"),
                List.of(), null, 1, "[TimeExtra].[Year]");
        RolapCubeLevel quarter = level(1, new RolapColumn("time_by_day", "quarter"),
                List.of(), null, 2, "[TimeExtra].[Quarter]");
        RolapCubeLevel month = level(2, new RolapColumn("time_by_day", "month_of_year"),
                List.of(), new RolapColumn("time_by_day", "the_month"), 3, "[TimeExtra].[Month]");
        hierarchy(tableSource("time_by_day", "time_by_day"), year, quarter, month);
        RolapCubeLevel gender = level(0, new RolapColumn("customer", "gender"),
                List.of(), null, 4, "[Gender].[Gender]");
        hierarchy(tableSource("customer", "customer"), gender);

        AggPlan.AggColumnPredicate memberSet = new AggPlan.AggColumnPredicate(fact,
                Predicates.and(List.of(Predicates.raw(
                        "\"agg_g_ms_pcat_sales_fact_1997\".\"gender\" = 'M'"))));

        String sql = render(AggTupleQueries.aggTupleLevelMembersSql(
                List.of(month, gender), aggStar, true,
                Optional.empty(), List.of(memberSet), List.of(), Optional.empty(), Optional.empty(),
                true, null, true));

        assertThat(sql).isEqualTo(
                "select \"agg_g_ms_pcat_sales_fact_1997\".\"the_year\" as \"c0\","
                + " \"agg_g_ms_pcat_sales_fact_1997\".\"quarter\" as \"c1\","
                + " \"agg_g_ms_pcat_sales_fact_1997\".\"month_of_year\" as \"c2\","
                + " \"time_by_day\".\"the_month\" as \"c3\","
                + " \"agg_g_ms_pcat_sales_fact_1997\".\"gender\" as \"c4\""
                + " from \"agg_g_ms_pcat_sales_fact_1997\" as \"agg_g_ms_pcat_sales_fact_1997\""
                + " join \"time_by_day\" as \"time_by_day\""
                + " on \"time_by_day\".\"month_of_year\" = \"agg_g_ms_pcat_sales_fact_1997\".\"month_of_year\""
                + " where (\"agg_g_ms_pcat_sales_fact_1997\".\"gender\" = 'M')"
                + " group by \"agg_g_ms_pcat_sales_fact_1997\".\"the_year\","
                + " \"agg_g_ms_pcat_sales_fact_1997\".\"quarter\","
                + " \"agg_g_ms_pcat_sales_fact_1997\".\"month_of_year\","
                + " \"time_by_day\".\"the_month\","
                + " \"agg_g_ms_pcat_sales_fact_1997\".\"gender\""
                + " order by CASE WHEN \"agg_g_ms_pcat_sales_fact_1997\".\"the_year\" IS NULL THEN 1 ELSE 0 END,"
                + " \"agg_g_ms_pcat_sales_fact_1997\".\"the_year\" ASC,"
                + " CASE WHEN \"agg_g_ms_pcat_sales_fact_1997\".\"quarter\" IS NULL THEN 1 ELSE 0 END,"
                + " \"agg_g_ms_pcat_sales_fact_1997\".\"quarter\" ASC,"
                + " CASE WHEN \"agg_g_ms_pcat_sales_fact_1997\".\"month_of_year\" IS NULL THEN 1 ELSE 0 END,"
                + " \"agg_g_ms_pcat_sales_fact_1997\".\"month_of_year\" ASC,"
                + " CASE WHEN \"agg_g_ms_pcat_sales_fact_1997\".\"gender\" IS NULL THEN 1 ELSE 0 END,"
                + " \"agg_g_ms_pcat_sales_fact_1997\".\"gender\" ASC");
    }

    // ---- leaf-ward subtree: the having-join replays the WHOLE snowflake subset --------------

    /** A snowflake JoinSource (the mapping-model join mock). */
    private static org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource left, String leftKey,
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource right, String rightKey) {
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column lk =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column.class);
        when(lk.getName()).thenReturn(leftKey);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column rk =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column.class);
        when(rk.getName()).thenReturn(rightKey);
        org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement le =
                mock(org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement.class);
        when(le.getSource()).thenReturn(left);
        when(le.getKey()).thenReturn(lk);
        org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement re =
                mock(org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement.class);
        when(re.getSource()).thenReturn(right);
        when(re.getKey()).thenReturn(rk);
        org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource j =
                mock(org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource.class);
        when(j.getLeft()).thenReturn(le);
        when(j.getRight()).thenReturn(re);
        return j;
    }

    /**
     * {@code members [Product].[Category]} on {@code test_lp_xxx_fact} with a Filter on the caption:
     * the collapsed+dim-join Category level roots the FROM at its inverse subset ({@code cat} alone
     * — {@code relationSubsetInverse} finds the alias right-most) plus the agg fact on the
     * {@code dimKey = aggColumn} edge; the native-HAVING compile then joins the filter-referenced
     * caption table's WHOLE snowflake subset (cat→product_cat→product_csv) — the
     * {@link TupleSqlMapper.HavingJoin} replay. Without it the FROM stops at {@code cat join fact}.
     */
    @Test
    void dimJoinHavingJoinReplaysWholeLeafwardSnowflakeSubtree() {
        String agg = "test_lp_xxx_fact";
        AggStar.FactTable fact = aggFactTable(agg);
        AggStar aggStar = mock(AggStar.class);
        AggStar.Table.Column col4_fact = aggColumn(fact, new RolapColumn(agg, "category"));
        when(aggStar.lookupColumn(4)).thenReturn(col4_fact);
        // Category's agg level substitutes the KEY only — caption/ordinal keep identity entries
        // (=> multipleCols + requiresJoinToDim).
        AggStar.Table.Level aggLevel4 = aggLevel(new RolapColumn(agg, "category"), List.of());
        when(aggStar.lookupLevel(4)).thenReturn(aggLevel4);

        var productCsv = tableSource("product_csv", "product_csv");
        var productCat = tableSource("product_cat", "product_cat");
        var cat = tableSource("cat", "cat");
        var relation = join(productCsv, "prod_cat", join(productCat, "cat", cat, "cat"), "prod_cat");
        RolapCubeLevel category = level(0, new RolapColumn("cat", "cat"),
                List.of(new RolapColumn("cat", "ord")), new RolapColumn("cat", "cap"), 4,
                "[Product].[Product].[Category]");
        RolapCubeHierarchy h = hierarchy(relation, category);

        String sql = render(AggTupleQueries.aggTupleLevelMembersSql(
                List.of((RolapLevel) category), aggStar, true,
                Optional.empty(), List.of(),
                List.of(new AggTupleQueries.HavingJoin(h, new RolapColumn("cat", "cap"))),
                Optional.empty(),
                Optional.of(Predicates.raw("c1 IS NOT NULL AND UPPER(c1) REGEXP '.*TWO.*'")),
                false, null, true));

        // The FROM: cat (inverse subset) join fact (dim→agg edge) join product_cat join
        // product_csv (the having-join's whole-subset replay).
        assertThat(sql).contains(
                "from \"cat\" as \"cat\""
                + " join \"test_lp_xxx_fact\" as \"test_lp_xxx_fact\""
                + " on \"cat\".\"cat\" = \"test_lp_xxx_fact\".\"category\""
                + " join \"product_cat\" as \"product_cat\" on \"product_cat\".\"cat\" = \"cat\".\"cat\""
                + " join \"product_csv\" as \"product_csv\""
                + " on \"product_csv\".\"prod_cat\" = \"product_cat\".\"prod_cat\"");
        assertThat(sql).startsWith(
                "select \"test_lp_xxx_fact\".\"category\" as \"c0\","
                + " \"cat\".\"cap\" as \"c1\","
                + " \"cat\".\"ord\" as \"c2\"");
        assertThat(sql).contains(" having c1 IS NOT NULL AND UPPER(c1) REGEXP '.*TWO.*'");
        assertThat(sql).doesNotContain(" where ");
    }

    /** WITHOUT the having-join replay the FROM stops at the inverse subset + agg fact — pinned so
     *  the replay's effect is visible in isolation. */
    @Test
    void dimJoinWithoutHavingJoinStopsAtInverseSubset() {
        String agg = "test_lp_xxx_fact";
        AggStar.FactTable fact = aggFactTable(agg);
        AggStar aggStar = mock(AggStar.class);
        AggStar.Table.Column col4_fact = aggColumn(fact, new RolapColumn(agg, "category"));
        when(aggStar.lookupColumn(4)).thenReturn(col4_fact);
        AggStar.Table.Level aggLevel4 = aggLevel(new RolapColumn(agg, "category"), List.of());
        when(aggStar.lookupLevel(4)).thenReturn(aggLevel4);

        var productCsv = tableSource("product_csv", "product_csv");
        var productCat = tableSource("product_cat", "product_cat");
        var cat = tableSource("cat", "cat");
        var relation = join(productCsv, "prod_cat", join(productCat, "cat", cat, "cat"), "prod_cat");
        RolapCubeLevel category = level(0, new RolapColumn("cat", "cat"),
                List.of(new RolapColumn("cat", "ord")), new RolapColumn("cat", "cap"), 4,
                "[Product].[Product].[Category]");
        hierarchy(relation, category);

        String sql = render(AggTupleQueries.aggTupleLevelMembersSql(
                List.of((RolapLevel) category), aggStar, true,
                Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                false, null, true));

        assertThat(sql).contains(
                "from \"cat\" as \"cat\""
                + " join \"test_lp_xxx_fact\" as \"test_lp_xxx_fact\""
                + " on \"cat\".\"cat\" = \"test_lp_xxx_fact\".\"category\" group by");
        assertThat(sql).doesNotContain("product_cat");
    }

    // ---- non-collapsed dimension root with the agg-fact existence join -------------

    /**
     * {@code members [Store].[Store State]} on {@code agg_c_14_sales_fact_1997}: the levels are NOT
     * collapsed (their agg columns live on the agg DIM table {@code store}), so the dimension is the
     * FROM root and the per-level existence join brings the agg fact in via the DimTable's join
     * condition ({@code agg.store_id = store.store_id}). The role-access restriction on the agg fact
     * is the WHERE; the native Filter HAVING is carried through.
     */
    @Test
    void m2FactJoinNonCollapsedDimensionRootJoinsAggFact() {
        String agg = "agg_c_14_sales_fact_1997";
        AggStar.FactTable fact = aggFactTable(agg);
        AggStar.Table store = aggDimTable("store", fact,
                new RolapColumn(agg, "store_id"), new RolapColumn("store", "store_id"));
        AggStar aggStar = mock(AggStar.class);
        AggStar.Table.Column col10_store = aggColumn(store, new RolapColumn("store", "store_country"));
        when(aggStar.lookupColumn(10)).thenReturn(col10_store);
        AggStar.Table.Column col11_store = aggColumn(store, new RolapColumn("store", "store_state"));
        when(aggStar.lookupColumn(11)).thenReturn(col11_store);

        RolapCubeLevel country = level(0, new RolapColumn("store", "store_country"),
                List.of(), null, 10, "[Store].[Store Country]");
        RolapCubeLevel state = level(1, new RolapColumn("store", "store_state"),
                List.of(), null, 11, "[Store].[Store State]");
        hierarchy(tableSource("store", "store"), country, state);

        AggPlan.AggColumnPredicate roleAccess = new AggPlan.AggColumnPredicate(fact,
                Predicates.raw("\"agg_c_14_sales_fact_1997\".\"the_year\" = 1997"));

        String sql = render(AggTupleQueries.aggTupleLevelMembersSql(
                List.of((RolapLevel) state), aggStar, true,
                Optional.empty(), List.of(roleAccess), List.of(), Optional.empty(),
                Optional.of(Predicates.raw("c1 IS NOT NULL AND UPPER(c1) REGEXP '.*CA.*'")),
                true, null, true));

        assertThat(sql).isEqualTo(
                "select \"store\".\"store_country\" as \"c0\","
                + " \"store\".\"store_state\" as \"c1\""
                + " from \"store\" as \"store\""
                + " join \"agg_c_14_sales_fact_1997\" as \"agg_c_14_sales_fact_1997\""
                + " on \"agg_c_14_sales_fact_1997\".\"store_id\" = \"store\".\"store_id\""
                + " where \"agg_c_14_sales_fact_1997\".\"the_year\" = 1997"
                + " group by \"store\".\"store_country\", \"store\".\"store_state\""
                + " having c1 IS NOT NULL AND UPPER(c1) REGEXP '.*CA.*'"
                + " order by CASE WHEN \"store\".\"store_country\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_country\" ASC,"
                + " CASE WHEN \"store\".\"store_state\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_state\" ASC");
    }

    // ---- cycle fold: the second dim↔agg edge is demoted to WHERE ------------------------------

    /**
     * The ON-vs-WHERE fold rule: a non-collapsed Year roots the FROM at {@code time_by_day} and its
     * existence join brings the agg fact in via the DimTable condition
     * ({@code agg.time_id = time_by_day.time_id} — the tree ON). The collapsed Month then adds its
     * {@code dimKey = aggColumn} edge between two ALREADY-JOINED aliases — the assembler cannot carry
     * it in the tree, so it lands as a WHERE conjunct AFTER the context conjuncts, deduped by
     * predicate value.
     */
    @Test
    void cycleEdgeBetweenJoinedAliasesIsDemotedToWhere() {
        String agg = "agg_time_mix";
        AggStar.FactTable fact = aggFactTable(agg);
        AggStar.Table timeDim = aggDimTable("time_by_day", fact,
                new RolapColumn(agg, "time_id"), new RolapColumn("time_by_day", "time_id"));
        AggStar aggStar = mock(AggStar.class);
        AggStar.Table.Column col1_timeDim = aggColumn(timeDim, new RolapColumn("time_by_day", "the_year"));
        when(aggStar.lookupColumn(1)).thenReturn(col1_timeDim);
        AggStar.Table.Column col3_fact = aggColumn(fact, new RolapColumn(agg, "month_of_year"));
        when(aggStar.lookupColumn(3)).thenReturn(col3_fact);
        AggStar.Table.Level aggLevel3 = aggLevel(new RolapColumn(agg, "month_of_year"), List.of());
        when(aggStar.lookupLevel(3)).thenReturn(aggLevel3);

        RolapCubeLevel year = level(0, new RolapColumn("time_by_day", "the_year"),
                List.of(), null, 1, "[Time].[Year]");
        RolapCubeLevel month = level(1, new RolapColumn("time_by_day", "month_of_year"),
                List.of(), new RolapColumn("time_by_day", "the_month"), 3, "[Time].[Month]");
        hierarchy(tableSource("time_by_day", "time_by_day"), year, month);

        String sql = render(AggTupleQueries.aggTupleLevelMembersSql(
                List.of((RolapLevel) month), aggStar, true,
                Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                true, null, true));

        assertThat(sql).isEqualTo(
                "select \"time_by_day\".\"the_year\" as \"c0\","
                + " \"agg_time_mix\".\"month_of_year\" as \"c1\","
                + " \"time_by_day\".\"the_month\" as \"c2\""
                + " from \"time_by_day\" as \"time_by_day\""
                + " join \"agg_time_mix\" as \"agg_time_mix\""
                + " on \"agg_time_mix\".\"time_id\" = \"time_by_day\".\"time_id\""
                // the cycle edge — both aliases already joined — folds into WHERE, not ON.
                + " where \"time_by_day\".\"month_of_year\" = \"agg_time_mix\".\"month_of_year\""
                + " group by \"time_by_day\".\"the_year\", \"agg_time_mix\".\"month_of_year\","
                + " \"time_by_day\".\"the_month\""
                + " order by CASE WHEN \"time_by_day\".\"the_year\" IS NULL THEN 1 ELSE 0 END,"
                + " \"time_by_day\".\"the_year\" ASC,"
                + " CASE WHEN \"agg_time_mix\".\"month_of_year\" IS NULL THEN 1 ELSE 0 END,"
                + " \"agg_time_mix\".\"month_of_year\" ASC");
    }

    // ---- delegation: the single-target route ----------------------

    /**
     * The single-target {@link TupleSqlMapper#collapsedSingleColumnSql} delegates to the
     * multi-target core; this pins its header/FROM/WHERE-split/ORDER form.
     */
    @Test
    void singleTargetCollapsedDelegationStaysByteIdentical() {
        String agg = "agg_c_10_sales_fact_1997";
        AggStar.FactTable fact = aggFactTable(agg);
        AggStar aggStar = mock(AggStar.class);
        AggStar.Table.Column col1_fact = aggColumn(fact, new RolapColumn(agg, "the_year"));
        when(aggStar.lookupColumn(1)).thenReturn(col1_fact);
        AggStar.Table.Column col2_fact = aggColumn(fact, new RolapColumn(agg, "quarter"));
        when(aggStar.lookupColumn(2)).thenReturn(col2_fact);

        RolapCubeLevel year = level(0, new RolapColumn("time_by_day", "the_year"),
                List.of(), null, 1, "[Time].[Year]");
        RolapCubeLevel quarter = level(1, new RolapColumn("time_by_day", "quarter"),
                List.of(), null, 2, "[Time].[Quarter]");
        hierarchy(tableSource("time_by_day", "time_by_day"), year, quarter);

        String sql = render(AggTupleQueries.collapsedSingleColumnSql(
                List.of(year, quarter), aggStar,
                Optional.of(Predicates.and(List.of(Predicates.raw(
                        "\"agg_c_10_sales_fact_1997\".\"the_year\" = 1997")))),
                Optional.empty(), Optional.empty()));

        assertThat(sql).isEqualTo(
                "select \"agg_c_10_sales_fact_1997\".\"the_year\" as \"c0\","
                + " \"agg_c_10_sales_fact_1997\".\"quarter\" as \"c1\""
                + " from \"agg_c_10_sales_fact_1997\" as \"agg_c_10_sales_fact_1997\""
                + " where \"agg_c_10_sales_fact_1997\".\"the_year\" = 1997"
                + " group by \"agg_c_10_sales_fact_1997\".\"the_year\","
                + " \"agg_c_10_sales_fact_1997\".\"quarter\""
                + " order by CASE WHEN \"agg_c_10_sales_fact_1997\".\"the_year\" IS NULL THEN 1 ELSE 0 END,"
                + " \"agg_c_10_sales_fact_1997\".\"the_year\" ASC,"
                + " CASE WHEN \"agg_c_10_sales_fact_1997\".\"quarter\" IS NULL THEN 1 ELSE 0 END,"
                + " \"agg_c_10_sales_fact_1997\".\"quarter\" ASC");
    }
}
