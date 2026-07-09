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
package org.eclipse.daanse.rolap.common.agg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Condition.JoinColumn;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.junit.jupiter.api.Test;

/**
 * Verifies count(distinct) rendering: the builder emits a flat {@code count(distinct col)} and the
 * renderer degrades it to a distinct subquery when the dialect forbids count-distinct. Checked on a
 * mocked star over an {@link AnsiDialect} whose {@code allowsCountDistinct} (and, for the
 * multi-measure case, {@code allowsInnerDistinct}) are overridden. For each shape both the
 * {@link AbstractQuerySpec#distinctGenerateSql} recorder rendering and the live
 * {@link AbstractQuerySpec#generateSql} builder rendering are asserted to match.
 */
class DistinctCountRewriteOracleTest {

    private record Fixture(RolapStar star, RolapStar.Column idCol, RolapStar.Column landCol,
            RolapStar.Measure m1, RolapStar.Measure m2) {
    }

    private static RolapStar.Column column(RolapStar.Table table, String tableAlias, String name,
            BestFitColumnType type) {
        RolapStar.Column c = mock(RolapStar.Column.class);
        when(c.getTable()).thenReturn(table);
        when(c.getExpression()).thenReturn(new RolapColumn(tableAlias, name));
        when(c.getInternalType()).thenReturn(type);
        when(c.getName()).thenReturn(name);
        return c;
    }

    private static RolapStar.Measure distinctCountMeasure(RolapStar.Table fact, String tableAlias, String col,
            String name) {
        RolapStar.Measure m = mock(RolapStar.Measure.class);
        when(m.getTable()).thenReturn(fact);
        when(m.getExpression()).thenReturn(new RolapColumn(tableAlias, col));
        when(m.getInternalType()).thenReturn(BestFitColumnType.DECIMAL);
        when(m.getAggregator()).thenReturn(DistinctCountAggregator.INSTANCE);
        when(m.getName()).thenReturn(name);
        when(m.getCubeName()).thenReturn("TestCube");
        return m;
    }

    /** A table mock whose {@code addToFrom} mirrors the production registration (table, parent, join). */
    private static RolapStar.Table table(String name, RolapStar.Table parent, RolapStar.Condition join) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getTableName()).thenReturn(name);
        when(t.getAlias()).thenReturn(name);
        when(t.getParentTable()).thenReturn(parent);
        when(t.getJoinCondition()).thenReturn(join);
        doAnswer(inv -> {
            QueryRecorder q = inv.getArgument(0);
            q.addFromTable(null, name, name, null, Map.of(), false);
            if (parent != null) {
                parent.addToFrom(q, inv.getArgument(1), inv.getArgument(2));
            }
            if (Boolean.TRUE.equals(inv.getArgument(2)) && join != null) {
                q.addWhere(join);
            }
            return null;
        }).when(t).addToFrom(any(), anyBoolean(), anyBoolean());
        return t;
    }

    private static Fixture fixture(Dialect dialect) {
        RolapStar.Table fact = table("fact_personal", null, null);
        RolapStar.Condition join = mock(RolapStar.Condition.class);
        when(join.leftColumn()).thenReturn(Optional.of(new JoinColumn("fact_personal", "schul_jahr_id")));
        when(join.rightColumn()).thenReturn(Optional.of(new JoinColumn("schul_jahr", "id")));
        // getLeft()/getRight() feed the recorder's addWhere(Condition) join edge (structured, dialect-free).
        when(join.getLeft()).thenReturn(new RolapColumn("fact_personal", "schul_jahr_id"));
        when(join.getRight()).thenReturn(new RolapColumn("schul_jahr", "id"));
        RolapStar.Table schulJahr = table("schul_jahr", fact, join);

        RolapStar.Column idCol = column(schulJahr, "schul_jahr", "id", BestFitColumnType.INT);
        RolapStar.Column landCol = column(schulJahr, "schul_jahr", "land", BestFitColumnType.STRING);
        RolapStar.Measure m1 = distinctCountMeasure(fact, "fact_personal", "anzahl_personen", "AnzahlDistinct");
        RolapStar.Measure m2 = distinctCountMeasure(fact, "fact_personal", "anzahl_klassen", "KlassenDistinct");

        Context<?> context = mock(Context.class);
        doReturn(Boolean.FALSE).when(context).getConfigValue(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(Boolean.class));

        RolapStar star = mock(RolapStar.class);
        when(star.getFactTable()).thenReturn(fact);
        when(star.getDialect()).thenReturn(dialect);
        doReturn(context).when(star).getContext();
        when(star.newQueryRecorder()).thenAnswer(inv -> new QueryRecorder(false));
        return new Fixture(star, idCol, landCol, m1, m2);
    }

    /** A minimal concrete aggregate spec over the fixture's columns + measures (never countOnly). */
    private static final class Spec extends AbstractQuerySpec {
        private final RolapStar.Column[] columns;
        private final StarColumnPredicate[] predicates;
        private final RolapStar.Measure[] measures;

        Spec(RolapStar star, RolapStar.Column[] columns, StarColumnPredicate[] predicates,
                RolapStar.Measure[] measures) {
            super(star, false);
            this.columns = columns;
            this.predicates = predicates;
            this.measures = measures;
        }

        @Override
        public int getMeasureCount() {
            return measures.length;
        }

        @Override
        public RolapStar.Measure getMeasure(int i) {
            return measures[i];
        }

        @Override
        public String getMeasureAlias(int i) {
            return "m" + i;
        }

        @Override
        public RolapStar.Column[] getColumns() {
            return columns;
        }

        @Override
        public String getColumnAlias(int i) {
            return "c" + i;
        }

        @Override
        public StarColumnPredicate getColumnPredicate(int i) {
            return predicates[i];
        }

        @Override
        protected boolean isAggregate() {
            return true;
        }

        @Override
        protected void addGroupingFunction(QueryRecorder query) {
            // no grouping functions in these shapes
        }

        @Override
        protected void addGroupingSets(QueryRecorder query, Map<RolapStar.Column, String> aliases) {
            // no grouping sets in these shapes
        }
    }

    /** Renders the recorder's distinctGenerateSql via the same assembler+renderer the segment loader uses. */
    private static String old(Spec spec, RolapStar star, Dialect dialect) {
        QueryRecorder q = star.newQueryRecorder();
        Map<RolapStar.Column, String> aliases = spec.distinctGenerateSql(q, false);
        spec.addGroupingFunction(q);
        spec.addGroupingSets(q, aliases);
        return org.eclipse.daanse.rolap.common.SqlRender.render(q.buildStatement(), dialect, q.renderOptions()).sql();
    }

    private static Dialect noCountDistinct() {
        return new AnsiDialect() {
            @Override
            public boolean allowsCountDistinct() {
                return false;
            }
        };
    }

    private static Dialect noCountDistinctNoInnerDistinct() {
        return new AnsiDialect() {
            @Override
            public boolean allowsCountDistinct() {
                return false;
            }

            @Override
            public boolean allowsInnerDistinct() {
                return false;
            }
        };
    }

    /** k=1, allowsInnerDistinct: inner uses SELECT DISTINCT. */
    @Test
    void singleDistinctMeasure_innerDistinct_newEqualsOld() {
        Dialect dialect = noCountDistinct();
        Fixture f = fixture(dialect);
        Spec spec = new Spec(f.star(), new RolapStar.Column[] { f.idCol() },
                new StarColumnPredicate[] { new LiteralStarPredicate(f.idCol(), true) },
                new RolapStar.Measure[] { f.m1() });

        RenderedSql neu = spec.generateSql();
        assertThat(neu.sql()).contains("dummyname").contains("distinct")
                .isEqualTo(old(spec, f.star(), dialect));
    }

    /** k=1, !allowsInnerDistinct: inner uses GROUP BY over every projection (Greenplum shape). */
    @Test
    void singleDistinctMeasure_innerGroupBy_newEqualsOld() {
        Dialect dialect = noCountDistinctNoInnerDistinct();
        Fixture f = fixture(dialect);
        Spec spec = new Spec(f.star(), new RolapStar.Column[] { f.idCol() },
                new StarColumnPredicate[] { new LiteralStarPredicate(f.idCol(), true) },
                new RolapStar.Measure[] { f.m1() });

        RenderedSql neu = spec.generateSql();
        assertThat(neu.sql()).contains("dummyname").doesNotContain("distinct")
                .isEqualTo(old(spec, f.star(), dialect));
    }

    /** k=2 distinct measures over two dimension columns, allowsInnerDistinct. */
    @Test
    void twoDistinctMeasures_twoDims_innerDistinct_newEqualsOld() {
        Dialect dialect = noCountDistinct();
        Fixture f = fixture(dialect);
        Spec spec = new Spec(f.star(), new RolapStar.Column[] { f.idCol(), f.landCol() },
                new StarColumnPredicate[] { new LiteralStarPredicate(f.idCol(), true),
                        new LiteralStarPredicate(f.landCol(), true) },
                new RolapStar.Measure[] { f.m1(), f.m2() });

        RenderedSql neu = spec.generateSql();
        assertThat(neu.sql()).contains("dummyname").isEqualTo(old(spec, f.star(), dialect));
    }

    /** k=2, !allowsInnerDistinct: inner GROUP BY over both dims and both measure columns. */
    @Test
    void twoDistinctMeasures_twoDims_innerGroupBy_newEqualsOld() {
        Dialect dialect = noCountDistinctNoInnerDistinct();
        Fixture f = fixture(dialect);
        Spec spec = new Spec(f.star(), new RolapStar.Column[] { f.idCol(), f.landCol() },
                new StarColumnPredicate[] { new LiteralStarPredicate(f.idCol(), true),
                        new LiteralStarPredicate(f.landCol(), true) },
                new RolapStar.Measure[] { f.m1(), f.m2() });

        RenderedSql neu = spec.generateSql();
        assertThat(neu.sql()).contains("dummyname").isEqualTo(old(spec, f.star(), dialect));
    }
}
