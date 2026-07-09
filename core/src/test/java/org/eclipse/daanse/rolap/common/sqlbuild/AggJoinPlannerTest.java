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

import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.JoinKind;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link AggJoinPlanner}'s level-derived aggregate-table translations against
 * FoodMart-shaped fixtures ({@code agg_c_10_sales_fact_1997}-style names, mock-built, no XMI):
 * {@code levelTargetExpMap}/{@code requiresJoinToDim} (which columns substitute to the agg table
 * and whether a dimension join is still needed), the agg-table join chain, the
 * {@code dimKey = aggColumn} edge, and the inverse FROM ({@link RelationFromMapper#fromInverse}).
 */
class AggJoinPlannerTest {

    private static final String AGG = "agg_c_10_sales_fact_1997";

    // ---- fixtures ----

    private RolapCubeLevel level(RolapColumn key, List<RolapColumn> ordinals, RolapColumn caption,
            RolapProperty[] properties, int bitPos) {
        RolapStar.Column starColumn = mock(RolapStar.Column.class);
        when(starColumn.getBitPosition()).thenReturn(bitPos);
        RolapCubeLevel level = mock(RolapCubeLevel.class);
        when(level.getKeyExp()).thenReturn(key);
        when(level.getOrdinalExps()).thenAnswer(inv -> ordinals);
        when(level.getCaptionExp()).thenReturn(caption);
        when(level.getProperties()).thenReturn(properties);
        when(level.getStarKeyColumn()).thenReturn(starColumn);
        return level;
    }

    private RolapProperty property(String name, RolapColumn exp) {
        RolapProperty prop = mock(RolapProperty.class);
        when(prop.getName()).thenReturn(name);
        when(prop.getExp()).thenReturn(exp);
        return prop;
    }

    private AggStar.Table.Column aggColumn(RolapColumn expression) {
        AggStar.Table.Column column = mock(AggStar.Table.Column.class);
        when(column.getExpression()).thenReturn(expression);
        return column;
    }

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

    private AggStar.Table aggTable(String name, AggStar.Table parent,
            AggStar.Table.JoinCondition joinCondition) {
        // NOTE: helper mocks are created BEFORE the when() chains — creating a stubbed mock inside
        // thenReturn(...) trips Mockito's unfinished-stubbing detection.
        org.eclipse.daanse.rolap.mapping.model.database.source.TableSource relation = tableSource(name, name);
        AggStar.Table table = mock(AggStar.Table.class);
        when(table.getName()).thenReturn(name);
        when(table.getRelation()).thenReturn(relation);
        when(table.hasParent()).thenReturn(parent != null);
        when(table.getParent()).thenReturn(parent);
        when(table.hasJoinCondition()).thenReturn(joinCondition != null);
        when(table.getJoinCondition()).thenReturn(joinCondition);
        return table;
    }

    private AggStar.Table.JoinCondition joinCondition(RolapColumn left, RolapColumn right) {
        AggStar.Table.JoinCondition jc = mock(AggStar.Table.JoinCondition.class);
        when(jc.getLeft()).thenReturn(left);
        when(jc.getRight()).thenReturn(right);
        return jc;
    }

    // ---- levelTargetExpMap / requiresJoinToDim ----

    @Test
    void noAggStarYieldsTheIdentityMap() {
        RolapColumn key = new RolapColumn("time_by_day", "quarter");
        RolapColumn ord = new RolapColumn("time_by_day", "quarter_ord");
        RolapColumn cap = new RolapColumn("time_by_day", "quarter_cap");
        RolapCubeLevel level = level(key, List.of(ord), cap, new RolapProperty[0], 2);

        Map<SqlExpression, SqlExpression> map = AggJoinPlanner.levelTargetExpMap(level, null);

        assertThat(map).containsEntry(key, key).containsEntry(ord, ord).containsEntry(cap, cap);
        assertThat(AggJoinPlanner.requiresJoinToDim(map)).isTrue();
    }

    /** The aggLevel==null branch: the key re-maps to the raw agg column, caption/ordinals keep
     *  their identity entries (NOT substituted, NOT dropped from the map) — "no extra columns". */
    @Test
    void rawAggColumnBranchRemapsOnlyTheKey() {
        RolapColumn key = new RolapColumn("time_by_day", "quarter");
        RolapColumn ord = new RolapColumn("time_by_day", "quarter_ord");
        RolapColumn cap = new RolapColumn("time_by_day", "quarter_cap");
        RolapCubeLevel level = level(key, List.of(ord), cap, new RolapProperty[0], 2);
        RolapColumn aggKey = new RolapColumn(AGG, "quarter");
        AggStar.Table.Column rawAggColumn = aggColumn(aggKey);
        AggStar aggStar = mock(AggStar.class);
        when(aggStar.lookupLevel(2)).thenReturn(null);
        when(aggStar.lookupColumn(2)).thenReturn(rawAggColumn);

        Map<SqlExpression, SqlExpression> map = AggJoinPlanner.levelTargetExpMap(level, aggStar);

        assertThat(map).containsEntry(key, aggKey) // substituted
                .containsEntry(ord, ord)           // identity: not on the agg table
                .containsEntry(cap, cap);
        assertThat(AggJoinPlanner.requiresJoinToDim(map)).isTrue();
    }

    @Test
    void aggLevelBranchMapsKeyOrdinalsCaptionAndPropertiesByName() {
        RolapColumn key = new RolapColumn("time_by_day", "quarter");
        RolapColumn ord1 = new RolapColumn("time_by_day", "ord1");
        RolapColumn ord2 = new RolapColumn("time_by_day", "ord2");
        RolapColumn cap = new RolapColumn("time_by_day", "cap");
        RolapColumn propExp = new RolapColumn("time_by_day", "fiscal_period");
        RolapColumn otherPropExp = new RolapColumn("time_by_day", "day_name");
        RolapCubeLevel level = level(key, List.of(ord1, ord2), cap,
                new RolapProperty[] { property("Fiscal Period", propExp), property("Day Name", otherPropExp) }, 2);

        RolapColumn aggKey = new RolapColumn(AGG, "quarter");
        RolapColumn aggOrd1 = new RolapColumn(AGG, "ord1");
        RolapColumn aggCap = new RolapColumn(AGG, "cap");
        RolapColumn aggProp = new RolapColumn(AGG, "fiscal_period");
        AggStar.Table.Level aggLevel = mock(AggStar.Table.Level.class);
        when(aggLevel.getExpression()).thenReturn(aggKey);
        when(aggLevel.getOrdinalExps()).thenAnswer(inv -> List.of(aggOrd1)); // SHORTER than the level's
        when(aggLevel.getCaptionExp()).thenReturn(aggCap);
        when(aggLevel.getProperties()).thenReturn(Map.of("Fiscal Period", aggProp));
        AggStar aggStar = mock(AggStar.class);
        when(aggStar.lookupLevel(2)).thenReturn(aggLevel);

        Map<SqlExpression, SqlExpression> map = AggJoinPlanner.levelTargetExpMap(level, aggStar);

        assertThat(map).containsEntry(key, aggKey)
                .containsEntry(ord1, aggOrd1)              // index-paired up to min size
                .containsEntry(ord2, ord2)                 // beyond the agg list: identity
                .containsEntry(cap, aggCap)
                .containsEntry(propExp, aggProp)           // matched by property NAME
                .containsEntry(otherPropExp, otherPropExp); // not on the agg level: identity
        assertThat(AggJoinPlanner.requiresJoinToDim(map)).isTrue(); // ord2 + Day Name are identity
    }

    /** A fully collapsed level (everything substituted; the no-caption null entry does not count)
     *  needs no dimension join. */
    @Test
    void fullySubstitutedLevelNeedsNoDimJoin() {
        RolapColumn key = new RolapColumn("time_by_day", "quarter");
        RolapColumn ord = new RolapColumn("time_by_day", "ord");
        RolapCubeLevel level = level(key, List.of(ord), null, new RolapProperty[0], 2);
        RolapColumn aggKey = new RolapColumn(AGG, "quarter");
        RolapColumn aggOrd = new RolapColumn(AGG, "ord");
        AggStar.Table.Level aggLevel = mock(AggStar.Table.Level.class);
        when(aggLevel.getExpression()).thenReturn(aggKey);
        when(aggLevel.getOrdinalExps()).thenAnswer(inv -> List.of(aggOrd));
        when(aggLevel.getProperties()).thenReturn(Map.of());
        AggStar aggStar = mock(AggStar.class);
        when(aggStar.lookupLevel(2)).thenReturn(aggLevel);

        Map<SqlExpression, SqlExpression> map = AggJoinPlanner.levelTargetExpMap(level, aggStar);

        assertThat(map).containsEntry(key, aggKey).containsEntry(ord, aggOrd);
        assertThat(map).containsEntry(null, null); // the no-caption identity entry
        assertThat(AggJoinPlanner.requiresJoinToDim(map)).isFalse();
    }

    // ---- aggTableChain / aggTableFrom / joinPredicate ----

    @Test
    void factOnlyChainIsOneRootEdgeWithoutOn() {
        AggStar.Table fact = aggTable(AGG, null, null);

        List<AggJoinPlanner.AggJoinEdge> chain = AggJoinPlanner.aggTableChain(fact);

        assertThat(chain).hasSize(1);
        assertThat(chain.get(0).fromAlias()).isEqualTo(AGG);
        assertThat(chain.get(0).on()).isNull();
        assertThat(chain.get(0).from())
                .isEqualTo(From.table(From.tableRef(null, AGG), TableAlias.of(AGG), null, Map.of()));
    }

    /** SELF first, then the parents — the addToFrom registration order; each edge carries the
     *  table's own join condition as left = right (dialect-free via JoinPlanner.expressionFor). */
    @Test
    void dimChainIsSelfFirstThenParentsWithItsJoinCondition() {
        AggStar.Table fact = aggTable(AGG, null, null);
        AggStar.Table dim = aggTable("time_by_day",
                fact, joinCondition(new RolapColumn(AGG, "time_id"), new RolapColumn("time_by_day", "time_id")));

        List<AggJoinPlanner.AggJoinEdge> chain = AggJoinPlanner.aggTableChain(dim);

        assertThat(chain).hasSize(2);
        assertThat(chain.get(0).fromAlias()).isEqualTo("time_by_day");
        assertThat(chain.get(0).on()).isEqualTo(Predicates.comparison(
                Expressions.column(TableAlias.of(AGG), "time_id"),
                ComparisonOperator.EQ,
                Expressions.column(TableAlias.of("time_by_day"), "time_id")));
        assertThat(chain.get(1).fromAlias()).isEqualTo(AGG);
        assertThat(chain.get(1).on()).isNull();
    }

    /** A DimTable's AggStar name is its star ALIAS; the physical name comes from the relation —
     *  aggTableFrom must alias the physical table like addFrom(relation, name) does. */
    @Test
    void aggTableFromAliasesThePhysicalTableWithTheAggTableName() {
        org.eclipse.daanse.rolap.mapping.model.database.source.TableSource relation =
                tableSource("time_by_day", "time_alias");
        AggStar.Table dim = mock(AggStar.Table.class);
        when(dim.getName()).thenReturn("time_alias");
        when(dim.getRelation()).thenReturn(relation);

        assertThat(AggJoinPlanner.aggTableFrom(dim)).isEqualTo(
                From.table(From.tableRef(null, "time_by_day"), TableAlias.of("time_alias"), null, Map.of()));
    }

    // ---- dimToAggEdge ----

    @Test
    void dimToAggEdgeEquatesTheLevelKeyWithTheAggColumn() {
        RolapCubeLevel level = level(new RolapColumn("time_by_day", "month_of_year"),
                List.of(), null, new RolapProperty[0], 3);
        AggStar.Table.Column monthAggColumn = aggColumn(new RolapColumn(AGG, "month_of_year"));
        AggStar aggStar = mock(AggStar.class);
        when(aggStar.lookupColumn(3)).thenReturn(monthAggColumn);

        assertThat(AggJoinPlanner.dimToAggEdge(level, aggStar)).isEqualTo(Predicates.comparison(
                Expressions.column(TableAlias.of("time_by_day"), "month_of_year"),
                ComparisonOperator.EQ,
                Expressions.column(TableAlias.of(AGG), "month_of_year")));
    }

    // ---- RelationFromMapper.fromInverse (the addToFromInverse twin) ----

    private org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement element(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource source, String keyName) {
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column key =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column.class);
        when(key.getName()).thenReturn(keyName);
        org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement e =
                mock(org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement.class);
        when(e.getSource()).thenReturn(source);
        when(e.getKey()).thenReturn(key);
        return e;
    }

    private org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource left, String leftKey,
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource right, String rightKey) {
        org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement leftElement =
                element(left, leftKey);
        org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement rightElement =
                element(right, rightKey);
        org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource j =
                mock(org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource.class);
        when(j.getLeft()).thenReturn(leftElement);
        when(j.getRight()).thenReturn(rightElement);
        return j;
    }

    private FromClause fromTable(String name) {
        return From.table(From.tableRef(null, name), TableAlias.of(name), null, Map.of());
    }

    /** product ⋈ product_class with the level key on the LEFT (leaf) side: the whole join rides
     *  along — the level-key table first with its leaf-ward subtree. */
    @Test
    void aliasInTheLeftBranchKeepsTheWholeJoin() {
        var product = tableSource("product", "product");
        var productClass = tableSource("product_class", "product_class");
        var relation = join(product, "product_class_id", productClass, "product_class_id");

        FromClause from = RelationFromMapper.fromInverse(relation, "product");

        assertThat(from).isEqualTo(new FromClause.FromJoin(
                fromTable("product"), JoinKind.INNER, fromTable("product_class"),
                Predicates.comparison(
                        Expressions.column(TableAlias.of("product"), "product_class_id"),
                        ComparisonOperator.EQ,
                        Expressions.column(TableAlias.of("product_class"), "product_class_id"))));
    }

    /** The level key on the RIGHT (root-ward) side: only that subtree remains — the root-ward
     *  ancestors are dropped, unlike relationSubset's childless-filter behavior. */
    @Test
    void aliasInTheRightBranchDropsTheLeftSide() {
        var product = tableSource("product", "product");
        var productClass = tableSource("product_class", "product_class");
        var relation = join(product, "product_class_id", productClass, "product_class_id");

        assertThat(RelationFromMapper.fromInverse(relation, "product_class"))
                .isEqualTo(fromTable("product_class"));
    }

    @Test
    void nestedJoinResolvesTheInnerLeafWardSubtree() {
        var a = tableSource("employee", "employee");
        var b = tableSource("store", "store");
        var c = tableSource("region", "region");
        var inner = join(b, "region_id", c, "region_id");
        var top = join(a, "store_id", inner, "store_id");

        FromClause from = RelationFromMapper.fromInverse(top, "store");

        assertThat(from).isEqualTo(new FromClause.FromJoin(
                fromTable("store"), JoinKind.INNER, fromTable("region"),
                Predicates.comparison(
                        Expressions.column(TableAlias.of("store"), "region_id"),
                        ComparisonOperator.EQ,
                        Expressions.column(TableAlias.of("region"), "region_id"))));
    }

    @Test
    void nonJoinRelationAndNullAliasMapWhole() {
        var product = tableSource("product", "product");
        assertThat(RelationFromMapper.fromInverse(product, "product")).isEqualTo(fromTable("product"));

        var productClass = tableSource("product_class", "product_class");
        var relation = join(product, "product_class_id", productClass, "product_class_id");
        assertThat(RelationFromMapper.fromInverse(relation, null))
                .isInstanceOf(FromClause.FromJoin.class);
    }

    @Test
    void relationSubsetInverseReturnsNullWhenNoTableMatches() {
        var product = tableSource("product", "product");
        var productClass = tableSource("product_class", "product_class");
        var relation = join(product, "product_class_id", productClass, "product_class_id");

        assertThat(RelationFromMapper.relationSubsetInverse(relation, "no_such_table")).isNull();
    }

    // ---- schema propagation ----

    @Test
    void aggTableFromCarriesTheRelationSchema() {
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema schema =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema.class);
        when(schema.getName()).thenReturn("foodmart");
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet.class);
        when(ncs.getName()).thenReturn(AGG);
        when(ncs.getNamespace()).thenReturn(schema);
        org.eclipse.daanse.rolap.mapping.model.database.source.TableSource ts =
                mock(org.eclipse.daanse.rolap.mapping.model.database.source.TableSource.class);
        when(ts.getTable()).thenReturn(ncs);
        AggStar.Table fact = mock(AggStar.Table.class);
        when(fact.getName()).thenReturn(AGG);
        when(fact.getRelation()).thenReturn(ts);

        FromClause from = AggJoinPlanner.aggTableFrom(fact);

        assertThat(from).isInstanceOf(FromClause.FromTable.class);
        FromClause.FromTable table = (FromClause.FromTable) from;
        assertThat(table.table().schema().map(s -> s.name())).contains("foodmart");
        assertThat(table.alias()).isEqualTo(TableAlias.of(AGG));
        assertThat(table.filter()).isEqualTo(Optional.empty());
    }
}
