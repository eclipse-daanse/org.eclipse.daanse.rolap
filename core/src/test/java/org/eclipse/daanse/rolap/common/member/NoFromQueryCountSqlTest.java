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
package org.eclipse.daanse.rolap.common.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.LevelType;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.element.OlapMetaDataBase;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Pins the level-cardinality count SQL of the {@code allowsFromQuery()==false} branch
 * (unreachable on H2/MySQL/Postgres/DuckDB, so the TCK never exercises it): the count expressions
 * are emitted as dialect-free nodes — a flat {@code count(distinct c1, c2)} when the dialect
 * allows the compound form, the ordered manual-count projection otherwise. The historic
 * {@code functionGenerator().generateCountExpression} string channel is gone.
 */
class NoFromQueryCountSqlTest {

    private static final class NoFromQueryDialect extends AnsiDialect {
        private final boolean compound;

        NoFromQueryDialect(boolean compound) {
            this.compound = compound;
        }

        @Override
        public boolean allowsFromQuery() {
            return false;
        }

        @Override
        public boolean allowsCompoundCountDistinct() {
            return compound;
        }
    }

    /** (all) &gt; Country &gt; State Province (unique) &gt; City on the single table customer. */
    private static List<RolapLevel> customerLevels() {
        // Deep stubs satisfy the SqlMemberSource constructor's catalog-reader chain.
        RolapHierarchy hierarchy = mock(RolapHierarchy.class, Mockito.RETURNS_DEEP_STUBS);
        when(hierarchy.getUniqueName()).thenReturn("[Customer]");
        when(hierarchy.getDimension()).thenReturn(mock(Dimension.class));
        when(hierarchy.tableExists("customer")).thenReturn(true);
        List<RolapLevel> levels = new ArrayList<>();
        doReturn(levels).when(hierarchy).getLevels();
        // addToFrom is the hierarchy's FROM contribution; replay it as the plain customer table.
        doAnswer(inv -> {
            QueryRecorder q = inv.getArgument(0);
            q.addFromTable(null, "customer", "customer", null, null, false);
            return null;
        }).when(hierarchy).addToFrom(Mockito.any(QueryRecorder.class), Mockito.any(SqlExpression.class));

        levels.add(level(hierarchy, "(All)", 0, null,
                RolapLevel.FLAG_ALL | RolapLevel.FLAG_UNIQUE));
        levels.add(level(hierarchy, "Country", 1, new RolapColumn("customer", "country"),
                RolapLevel.FLAG_UNIQUE));
        levels.add(level(hierarchy, "State Province", 2,
                new RolapColumn("customer", "state_province"), RolapLevel.FLAG_UNIQUE));
        levels.add(level(hierarchy, "City", 3, new RolapColumn("customer", "city"), 0));
        return levels;
    }

    private static RolapLevel level(RolapHierarchy hierarchy, String name, int depth,
            SqlExpression keyExp, int flags) {
        return new RolapLevel(hierarchy, name, null, true, null, depth, keyExp, null, null,
                null, null, null, null, RolapProperty.emptyArray, flags, null, null,
                RolapLevel.HideMemberCondition.Never, LevelType.REGULAR, "",
                OlapMetaDataBase.empty());
    }

    private static String countSql(boolean compound, boolean[] mustCount) {
        List<RolapLevel> levels = customerLevels();
        RolapHierarchy hierarchy = levels.get(0).getHierarchy();
        Context<?> context = mock(Context.class);
        doReturn(new NoFromQueryDialect(compound)).when(context).getDialect();
        doReturn(Boolean.FALSE).when(context).getConfigValue(
                Mockito.anyString(), Mockito.any(), Mockito.eq(Boolean.class));

        SqlMemberSource source = Mockito.mock(SqlMemberSource.class,
                Mockito.withSettings().useConstructor(hierarchy).defaultAnswer(Mockito.CALLS_REAL_METHODS));
        // City (depth 3, non-unique) walks up to State Province (unique).
        return source.makeLevelMemberCountSql(levels.get(3), context, mustCount);
    }

    @Test
    void compoundCapableDialectGetsFlatCountDistinctNodes() {
        boolean[] mustCount = {false};
        assertThat(countSql(true, mustCount)).isEqualTo(
                "select COUNT(distinct \"customer\".\"city\", \"customer\".\"state_province\")"
                        + " as \"c0\" from \"customer\" as \"customer\"");
        assertThat(mustCount[0]).isFalse();
    }

    @Test
    void nonCompoundDialectGetsOrderedManualCountProjection() {
        boolean[] mustCount = {false};
        assertThat(countSql(false, mustCount)).isEqualTo(
                "select \"customer\".\"city\" as \"c0\", \"customer\".\"state_province\" as \"c1\""
                        + " from \"customer\" as \"customer\""
                        + " order by CASE WHEN \"customer\".\"city\" IS NULL THEN 1 ELSE 0 END,"
                        + " \"customer\".\"city\" ASC,"
                        + " CASE WHEN \"customer\".\"state_province\" IS NULL THEN 1 ELSE 0 END,"
                        + " \"customer\".\"state_province\" ASC");
        assertThat(mustCount[0]).isTrue();
    }
}
