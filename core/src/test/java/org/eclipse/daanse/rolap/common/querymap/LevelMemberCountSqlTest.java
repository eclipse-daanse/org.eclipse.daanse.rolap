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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.LevelType;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.element.OlapMetaDataBase;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.rolap.mapping.model.database.relational.DialectSqlView;
import org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.SqlStatement;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.eclipse.emf.common.util.BasicEList;
import org.junit.jupiter.api.Test;

/**
 * Verifies the level-cardinality count query {@link TupleSqlMapper#levelMemberCountSql} builds — a
 * {@code select count(*) as "c0" from (select distinct …) as "init"} that walks up to the first
 * unique ancestor level.
 *
 * <p>Also verifies the routing gate {@link TupleSqlMapper#countSupports}: true for any
 * supported-relation level — plain-column levels, a parent-child level (its key projects like any
 * other), a computed (non-column) key, and a single view/inline relation that resolves. It stays
 * false for a degenerate single relation with an empty view body and for a parent-child level on a
 * single view/inline relation.
 *
 * <p>The levels are REAL {@link RolapLevel} instances (only the hierarchy is a mock): the mapper
 * reads {@code isAll()}/{@code isUnique()}, which are final and therefore not stubbable.
 */
class LevelMemberCountSqlTest {

    /**
     * The [Customer] fixture hierarchy on the single table {@code customer}:
     * (all) &gt; Country &gt; State Province (unique) &gt; City. Depths match list indexes, as
     * {@code hierarchy.getLevels()} guarantees for the walk in {@code levelMemberCountSql}.
     */
    private static List<RolapLevel> customerLevels() {
        TableSource customerSource = mock(TableSource.class);
        when(customerSource.getAlias()).thenReturn("customer");
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet.class);
        when(ncs.getName()).thenReturn("customer");
        when(customerSource.getTable()).thenReturn(ncs);

        RolapHierarchy hierarchy = mock(RolapHierarchy.class);
        when(hierarchy.getRelation()).thenReturn(customerSource);
        when(hierarchy.getUniqueName()).thenReturn("[Customer]");
        when(hierarchy.getDimension()).thenReturn(mock(Dimension.class));
        when(hierarchy.tableExists("customer")).thenReturn(true);
        // Live list: RolapLevel's constructor walks getParentLevel() -> getLevels().get(depth - 1),
        // so each level must see its ancestors while being built.
        List<RolapLevel> levels = new ArrayList<>();
        doReturn(levels).when(hierarchy).getLevels();

        levels.add(level(hierarchy, "(All)", 0, null, null,
                RolapLevel.FLAG_ALL | RolapLevel.FLAG_UNIQUE));
        levels.add(level(hierarchy, "Country", 1, new RolapColumn("customer", "country"), null,
                RolapLevel.FLAG_UNIQUE));
        levels.add(level(hierarchy, "State Province", 2,
                new RolapColumn("customer", "state_province"), null, RolapLevel.FLAG_UNIQUE));
        levels.add(level(hierarchy, "City", 3, new RolapColumn("customer", "city"), null, 0));
        return levels;
    }

    private static RolapLevel level(RolapHierarchy hierarchy, String name, int depth,
            SqlExpression keyExp, SqlExpression parentExp, int flags) {
        return new RolapLevel(hierarchy, name, null, true, null, depth, keyExp, null, null,
                null, parentExp, null, null, RolapProperty.emptyArray, flags, null, null,
                RolapLevel.HideMemberCondition.Never, LevelType.REGULAR, "",
                OlapMetaDataBase.empty());
    }

    private static String render(org.eclipse.daanse.sql.statement.api.model.SelectStatement stmt) {
        return new DialectSqlRenderer(new AnsiDialect()).render(stmt).sql();
    }

    /** Single-key count: [Customer].[Country] — its unique key alone in the DISTINCT subquery. */
    @Test
    void singleKeyLevelCountMatchesCorpusSql() {
        List<RolapLevel> levels = customerLevels();
        assertThat(CountQueries.countSupports(levels.get(1))).isTrue();
        assertThat(render(CountQueries.levelMemberCountSql(levels.get(1)))).isEqualTo(
                "select count(*) as \"c0\" from"
                + " (select distinct \"customer\".\"country\" as \"c0\""
                + " from \"customer\" as \"customer\") as \"init\"");
    }

    /** Multi-level count: [Customer].[City] walks up to the first unique level (State Province)
     *  and stops — two DISTINCT keys, target first. */
    @Test
    void multiLevelCountStopsAtUniqueLevelAndMatchesCorpusSql() {
        List<RolapLevel> levels = customerLevels();
        assertThat(CountQueries.countSupports(levels.get(3))).isTrue();
        assertThat(render(CountQueries.levelMemberCountSql(levels.get(3)))).isEqualTo(
                "select count(*) as \"c0\" from"
                + " (select distinct \"customer\".\"city\" as \"c0\","
                + " \"customer\".\"state_province\" as \"c1\""
                + " from \"customer\" as \"customer\") as \"init\"");
    }

    /** A parent-child level is in the count scope: the gate accepts it and the count projects its
     *  key like any other. (The gate walks {@code hierarchy.getLevels()}, so the level is swapped
     *  into the live list at its depth.) */
    @Test
    void parentChildLevelSupportedAndRendersItsKey() {
        List<RolapLevel> levels = customerLevels();
        RolapHierarchy hierarchy = levels.get(0).getHierarchy();
        RolapLevel parentChild = level(hierarchy, "Employee", 1,
                new RolapColumn("customer", "customer_id"),
                new RolapColumn("customer", "parent_id"), RolapLevel.FLAG_UNIQUE);
        levels.set(1, parentChild);
        assertThat(CountQueries.countSupports(parentChild)).isTrue();
        assertThat(render(CountQueries.levelMemberCountSql(parentChild))).isEqualTo(
                "select count(*) as \"c0\" from"
                + " (select distinct \"customer\".\"customer_id\" as \"c0\""
                + " from \"customer\" as \"customer\") as \"init\"");
    }

    /** A computed (non-column) key is accepted too — the RawVariant carries it. */
    @Test
    void computedKeySupported() {
        List<RolapLevel> levels = customerLevels();
        RolapHierarchy hierarchy = levels.get(0).getHierarchy();
        RolapLevel computed = level(hierarchy, "FullName", 1, mock(SqlExpression.class), null,
                RolapLevel.FLAG_UNIQUE);
        levels.set(1, computed);
        assertThat(CountQueries.countSupports(computed)).isTrue();
    }

    /**
     * A level on a SINGLE view relation that resolves is built by the count mapper — the view is
     * mapped to a {@code FromVariant} and the count projects the level key against it.
     */
    @Test
    void viewBackedSingleRelationSupportedAndRendersCorpusSql() {
        List<RolapLevel> levels = viewLevels("select 0 as promo_id");
        assertThat(CountQueries.countSupports(levels.get(1))).isTrue();
        assertThat(render(CountQueries.levelMemberCountSql(levels.get(1)))).isEqualTo(
                "select count(*) as \"c0\" from"
                + " (select distinct \"v\".\"promo_id\" as \"c0\""
                + " from (select 0 as promo_id) as \"v\") as \"init\"");
    }

    /**
     * A single view with an EMPTY body is the degenerate relation that renders {@code from () as
     * foo}, so the count gate declines it ({@code resolvesSingleViewOrInline} is false for a blank
     * view body).
     */
    @Test
    void emptyViewRelationDeclined() {
        List<RolapLevel> levels = viewLevels("");
        assertThat(CountQueries.countSupports(levels.get(1))).isFalse();
    }

    /** A parent-child level on a single view/inline relation stays declined (the exotic gate
     *  excludes parent-child). */
    @Test
    void parentChildViewRelationDeclined() {
        List<RolapLevel> levels = viewLevels("select 0 as promo_id");
        RolapHierarchy hierarchy = levels.get(0).getHierarchy();
        RolapLevel pc = level(hierarchy, "Promo", 1, new RolapColumn("v", "promo_id"),
                new RolapColumn("v", "parent_id"), RolapLevel.FLAG_UNIQUE);
        levels.set(1, pc);
        assertThat(CountQueries.countSupports(pc)).isFalse();
    }

    /**
     * A single-VIEW hierarchy {@code (all) > Promo (unique)} backed by a lone {@link SqlSelectSource}
     * whose {@code generic} body is {@code bodySql}. An empty {@code bodySql} gives a degenerate view.
     */
    private static List<RolapLevel> viewLevels(String bodySql) {
        SqlStatement stmt = mock(SqlStatement.class);
        when(stmt.getDialects()).thenReturn(new BasicEList<>(List.of("generic")));
        when(stmt.getSql()).thenReturn(bodySql);
        DialectSqlView dsv = mock(DialectSqlView.class);
        when(dsv.getDialectStatements()).thenReturn(new BasicEList<>(List.of(stmt)));
        SqlSelectSource view = mock(SqlSelectSource.class);
        when(view.getAlias()).thenReturn("v");
        when(view.getSql()).thenReturn(dsv);

        RolapHierarchy hierarchy = mock(RolapHierarchy.class);
        when(hierarchy.getRelation()).thenReturn(view);
        when(hierarchy.getUniqueName()).thenReturn("[Promo]");
        when(hierarchy.getDimension()).thenReturn(mock(Dimension.class));
        when(hierarchy.tableExists("v")).thenReturn(true);
        List<RolapLevel> levels = new ArrayList<>();
        doReturn(levels).when(hierarchy).getLevels();
        levels.add(level(hierarchy, "(All)", 0, null, null,
                RolapLevel.FLAG_ALL | RolapLevel.FLAG_UNIQUE));
        levels.add(level(hierarchy, "Promo", 1, new RolapColumn("v", "promo_id"), null,
                RolapLevel.FLAG_UNIQUE));
        return levels;
    }
}
