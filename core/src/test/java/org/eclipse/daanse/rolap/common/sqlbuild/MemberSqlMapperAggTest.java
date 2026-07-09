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
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.AggPlan;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the SQL {@link MemberSqlMapper#aggChildMemberSql} produces for three member-children
 * shapes routed to aggregate tables (mock-built, no XMI):
 * <ul>
 *   <li>C1 — unconstrained fact-join children ({@code [Store].[Store Country]}): dimension FROM,
 *       agg fact joined on the agg dim chain's condition, NO WHERE (an empty predicate set is a
 *       valid unconstrained plan, not a bail);</li>
 *   <li>C2 — collapsed child level with dimension extras + parent walk
 *       ({@code [Product].[Product Category]}): agg fact FROM base, snowflake dimension tables
 *       breadth-attached, the parent-walk duplicate edge folded to WHERE after the context and the
 *       parenthesised member-key group;</li>
 *   <li>C3 — computed key through the {@code RawVariant} projection channel
 *       ({@code ERROR_TEST_FUNCTION_NAME}): same fact-join frame as C1, key spelled from the
 *       expression's per-dialect SQL map.</li>
 * </ul>
 */
class MemberSqlMapperAggTest {

    private static final String AGG = "agg_c_14_sales_fact_1997";

    // ---- fixtures (AggJoinPlannerTest style) ----

    private org.eclipse.daanse.rolap.mapping.model.database.source.TableSource tableSource(
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

    private AggStar.Table.JoinCondition joinCondition(RolapColumn left, RolapColumn right) {
        AggStar.Table.JoinCondition jc = mock(AggStar.Table.JoinCondition.class);
        when(jc.getLeft()).thenReturn(left);
        when(jc.getRight()).thenReturn(right);
        return jc;
    }

    private void stubAggTable(AggStar.Table table, String name, AggStar.Table parent,
            AggStar.Table.JoinCondition joinCondition) {
        org.eclipse.daanse.rolap.mapping.model.database.source.TableSource relation =
                tableSource(name, name);
        when(table.getName()).thenReturn(name);
        when(table.getRelation()).thenReturn(relation);
        when(table.hasParent()).thenReturn(parent != null);
        when(table.getParent()).thenReturn(parent);
        when(table.hasJoinCondition()).thenReturn(joinCondition != null);
        when(table.getJoinCondition()).thenReturn(joinCondition);
    }

    /** A non-collapsed agg DIM table (isLevelCollapsed twin sees a plain Table, not a FactTable). */
    private AggStar.Table aggDimTable(String name, AggStar.Table parent,
            AggStar.Table.JoinCondition joinCondition) {
        AggStar.Table table = mock(AggStar.Table.class);
        stubAggTable(table, name, parent, joinCondition);
        return table;
    }

    /** A collapsed agg FACT table (the isLevelCollapsed twin's {@code instanceof FactTable}). */
    private AggStar.Table aggFactTable(String name) {
        AggStar.Table table = mock(AggStar.FactTable.class);
        stubAggTable(table, name, null, null);
        return table;
    }

    private AggStar.Table.Column aggColumn(RolapColumn expression, AggStar.Table table) {
        AggStar.Table.Column column = mock(AggStar.Table.Column.class);
        when(column.getExpression()).thenReturn(expression);
        when(column.getTable()).thenReturn(table);
        return column;
    }

    private RolapCubeLevel level(org.eclipse.daanse.rolap.element.RolapCubeHierarchy hierarchy, String uniqueName,
            org.eclipse.daanse.olap.api.sql.SqlExpression key, RolapColumn caption,
            List<RolapColumn> ordinals, int bitPos) {
        RolapStar.Column starColumn = mock(RolapStar.Column.class);
        when(starColumn.getBitPosition()).thenReturn(bitPos);
        RolapCubeLevel level = mock(RolapCubeLevel.class);
        when(level.getHierarchy()).thenReturn(hierarchy);
        when(level.getUniqueName()).thenReturn(uniqueName);
        when(level.getKeyExp()).thenReturn(key);
        when(level.hasCaptionColumn()).thenReturn(caption != null);
        when(level.getCaptionExp()).thenReturn(caption);
        when(level.getOrdinalExps()).thenAnswer(inv -> ordinals);
        when(level.getProperties()).thenReturn(new RolapProperty[0]);
        when(level.getStarKeyColumn()).thenReturn(starColumn);
        return level;
    }

    private org.eclipse.daanse.rolap.element.RolapCubeHierarchy hierarchy(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        org.eclipse.daanse.rolap.element.RolapCubeHierarchy h =
                mock(org.eclipse.daanse.rolap.element.RolapCubeHierarchy.class);
        when(h.getRelation()).thenReturn(relation);
        return h;
    }

    private org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join(
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

    private String render(org.eclipse.daanse.sql.statement.api.model.SelectStatement stmt) {
        return new DialectSqlRenderer(new AnsiDialect()).render(stmt).sql();
    }

    // ---- C1: unconstrained fact-join children ----

    /**
     * C1: {@code [Store].[Store Country]} children under an all-member context routed to
     * {@code agg_c_14_sales_fact_1997} — the level is NOT collapsed, the dimension is the FROM base,
     * the agg fact joins in on the agg dim chain's own condition
     * ({@code agg.store_id = store.store_id}) and there is NO WHERE at all: the empty
     * {@code orderedAggPredicates} + empty {@code where} is the valid unconstrained plan.
     */
    @Test
    void c1UnconstrainedFactJoinChildrenEmitNoWhere() {
        org.eclipse.daanse.rolap.element.RolapCubeHierarchy h = hierarchy(tableSource("store", "store"));
        RolapCubeLevel level = level(h, "[Store].[Store].[Store Country]",
                new RolapColumn("store", "store_country"), null, List.of(), 1);

        AggStar.Table aggFact = aggDimTable(AGG, null, null);
        AggStar.Table aggDim = aggDimTable("store", aggFact,
                joinCondition(new RolapColumn(AGG, "store_id"), new RolapColumn("store", "store_id")));
        // Helper mocks BEFORE the when() chain (Mockito unfinished-stubbing trap, see AggJoinPlannerTest).
        AggStar.Table.Column storeCountry = aggColumn(new RolapColumn("store", "store_country"), aggDim);
        AggStar aggStar = mock(AggStar.class);
        when(aggStar.lookupColumn(1)).thenReturn(storeCountry);

        String sql = render(MemberSqlMapper.aggChildMemberSql(level, aggStar,
                true, Optional.empty(), List.of(), Optional.empty()));

        assertThat(sql).isEqualTo(
                "select \"store\".\"store_country\" as \"c0\""
                + " from \"store\" as \"store\""
                + " join \"" + AGG + "\" as \"" + AGG + "\""
                + " on \"" + AGG + "\".\"store_id\" = \"store\".\"store_id\""
                + " group by \"store\".\"store_country\""
                + " order by CASE WHEN \"store\".\"store_country\" IS NULL THEN 1 ELSE 0 END,"
                + " \"store\".\"store_country\" ASC");
    }

    // ---- C2: collapsed child with dim extras + parent walk ----

    /**
     * C2: {@code [Product].[Product Category]} children of {@code [Cat One]} on a collapsed agg
     * fact ({@code test_lp_xxx_fact.product_category}) with caption/ordinal on the dimension — the
     * agg fact is the FROM base (the context registered it first), the snowflake tables
     * breadth-attach (key table on the {@code dimKey = aggColumn} edge, then its relation joins),
     * and the PARENT-WALK edge ({@code cat.cat = fact.category}) arrives when both tables are
     * already placed, so it folds to the LAST WHERE conjunct — after the context conjunct and the
     * parenthesised member-key group.
     */
    @Test
    void c2CollapsedParentWalkFoldsDuplicateEdgeToWhere() {
        var productCsv = tableSource("product_csv", "product_csv");
        var productCat = tableSource("product_cat", "product_cat");
        var cat = tableSource("cat", "cat");
        var relation = join(productCsv, "prod_cat", join(productCat, "cat", cat, "cat"), "prod_cat");
        org.eclipse.daanse.rolap.element.RolapCubeHierarchy h = hierarchy(relation);

        AggStar.Table aggFact = aggFactTable("test_lp_xxx_fact");
        // Helper mocks BEFORE the when() chains (Mockito unfinished-stubbing trap, see AggJoinPlannerTest).
        AggStar.Table.Column productCategoryCol =
                aggColumn(new RolapColumn("test_lp_xxx_fact", "product_category"), aggFact);
        AggStar.Table.Column categoryCol =
                aggColumn(new RolapColumn("test_lp_xxx_fact", "category"), aggFact);
        AggStar aggStar = mock(AggStar.class);
        when(aggStar.lookupColumn(5)).thenReturn(productCategoryCol);
        when(aggStar.lookupColumn(4)).thenReturn(categoryCol);

        RolapCubeLevel level = level(h, "[Product].[Product].[Product Category]",
                new RolapColumn("product_cat", "name2"), new RolapColumn("product_cat", "cap"),
                List.of(new RolapColumn("product_cat", "ord")), 5);
        RolapCubeLevel parent = level(h, "[Product].[Product].[Category]",
                new RolapColumn("cat", "cat"), null, List.of(), 4);
        RolapCubeLevel all = level(h, "[Product].[Product].[All]",
                new RolapColumn("product_csv", "prod_cat"), null, List.of(), 0);
        when(all.isAll()).thenReturn(true);
        when(level.getParentLevel()).thenReturn(parent);
        when(parent.getParentLevel()).thenReturn(all);
        when(level.isUnique()).thenReturn(false);

        Predicate context = Predicates.comparison(
                Expressions.column(TableAlias.of("test_lp_xxx_fact"), "category"),
                ComparisonOperator.EQ, Expressions.literal(1, Datatype.INTEGER));
        Predicate memberKeyGroup = Predicates.and(List.of(Predicates.comparison(
                Expressions.column(TableAlias.of("test_lp_xxx_fact"), "category"),
                ComparisonOperator.EQ, Expressions.literal(1, Datatype.INTEGER))));

        String sql = render(MemberSqlMapper.aggChildMemberSql(level, aggStar,
                true, Optional.empty(),
                List.of(new AggPlan.AggColumnPredicate(aggFact, context)),
                Optional.of(memberKeyGroup)));

        assertThat(sql).isEqualTo(
                "select \"product_cat\".\"name2\" as \"c0\","
                + " \"product_cat\".\"cap\" as \"c1\","
                + " \"product_cat\".\"ord\" as \"c2\""
                + " from \"test_lp_xxx_fact\" as \"test_lp_xxx_fact\""
                + " join \"product_cat\" as \"product_cat\""
                + " on \"product_cat\".\"name2\" = \"test_lp_xxx_fact\".\"product_category\""
                + " join \"cat\" as \"cat\" on \"product_cat\".\"cat\" = \"cat\".\"cat\""
                + " join \"product_csv\" as \"product_csv\""
                + " on \"product_csv\".\"prod_cat\" = \"product_cat\".\"prod_cat\""
                + " where \"test_lp_xxx_fact\".\"category\" = 1"
                + " and (\"test_lp_xxx_fact\".\"category\" = 1)"
                + " and \"cat\".\"cat\" = \"test_lp_xxx_fact\".\"category\""
                + " group by \"product_cat\".\"name2\", \"product_cat\".\"cap\", \"product_cat\".\"ord\""
                + " order by CASE WHEN \"product_cat\".\"ord\" IS NULL THEN 1 ELSE 0 END,"
                + " \"product_cat\".\"ord\" ASC");
    }

    // ---- C3: computed key via RawVariant ----

    /**
     * C3: {@code [Promotions].[Promotion Name]} children with a COMPUTED key expression
     * ({@code ERROR_TEST_FUNCTION_NAME("promotion_name")}) — the key projects through the
     * {@code RawVariant} channel (per-dialect SQL map, spelled verbatim in SELECT and GROUP BY),
     * the FROM falls back to the whole relation (a computed key has no table alias to subset by)
     * and the agg fact joins exactly like C1. The route gate for this shape is
     * {@code supportsComputed}, not the strict {@code supports}.
     */
    @Test
    void c3ComputedKeyProjectsThroughTheRawVariantChannel() {
        org.eclipse.daanse.rolap.element.RolapCubeHierarchy h = hierarchy(tableSource("promotion", "promotion"));

        org.eclipse.daanse.olap.api.SqlStatement computedSql =
                mock(org.eclipse.daanse.olap.api.SqlStatement.class);
        when(computedSql.getDialects()).thenAnswer(inv -> List.of("generic"));
        when(computedSql.getSql()).thenReturn("ERROR_TEST_FUNCTION_NAME(\"promotion_name\")");
        org.eclipse.daanse.olap.api.sql.SqlExpression computedKey =
                mock(org.eclipse.daanse.olap.api.sql.SqlExpression.class);
        when(computedKey.getSqls()).thenAnswer(inv -> List.of(computedSql));
        when(computedKey.getSortingDirection()).thenReturn(SortingDirection.ASC);

        RolapCubeLevel level = level(h, "[Promotions].[Promotions].[Promotion Name]",
                computedKey, null, List.of(), 2);

        AggStar.Table aggFact = aggDimTable(AGG, null, null);
        AggStar.Table aggDim = aggDimTable("promotion", aggFact, joinCondition(
                new RolapColumn(AGG, "promotion_id"), new RolapColumn("promotion", "promotion_id")));
        // Helper mock BEFORE the when() chain (Mockito unfinished-stubbing trap, see AggJoinPlannerTest).
        AggStar.Table.Column promotionId = aggColumn(new RolapColumn("promotion", "promotion_id"), aggDim);
        AggStar aggStar = mock(AggStar.class);
        when(aggStar.lookupColumn(2)).thenReturn(promotionId);

        String sql = render(MemberSqlMapper.aggChildMemberSql(level, aggStar,
                true, Optional.empty(), List.of(), Optional.empty()));

        assertThat(sql).isEqualTo(
                "select ERROR_TEST_FUNCTION_NAME(\"promotion_name\") as \"c0\""
                + " from \"promotion\" as \"promotion\""
                + " join \"" + AGG + "\" as \"" + AGG + "\""
                + " on \"" + AGG + "\".\"promotion_id\" = \"promotion\".\"promotion_id\""
                + " group by ERROR_TEST_FUNCTION_NAME(\"promotion_name\")"
                + " order by CASE WHEN ERROR_TEST_FUNCTION_NAME(\"promotion_name\") IS NULL THEN 1 ELSE 0 END,"
                + " ERROR_TEST_FUNCTION_NAME(\"promotion_name\") ASC");
    }
}
