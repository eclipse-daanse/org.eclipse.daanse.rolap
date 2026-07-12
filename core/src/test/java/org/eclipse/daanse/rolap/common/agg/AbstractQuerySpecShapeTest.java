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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Condition.JoinColumn;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code AbstractQuerySpec.generateSql} per shape: countOnly, ordered and grouping-sets specs
 * are rendered by the builder (no {@code QueryRecorder} constructed), while the distinct-subquery
 * rewrite is produced by the recorder. Each shape asserts the exact SQL.
 */
class AbstractQuerySpecShapeTest {

    /** Minimal concrete spec over a mocked star: one constrained (always-true) dimension column,
     *  one sum measure. Shape knobs are constructor-driven. */
    private static final class TestSpec extends AbstractQuerySpec {
        private final RolapStar.Column column;
        private final RolapStar.Measure measure;
        private final boolean ordered;
        private final List<RolapStar.Column[]> groupingSets;
        private final List<RolapStar.Column> rollupColumns;

        TestSpec(RolapStar star, RolapStar.Column column, RolapStar.Measure measure,
                boolean countOnly, boolean ordered,
                List<RolapStar.Column[]> groupingSets, List<RolapStar.Column> rollupColumns) {
            super(star, countOnly);
            this.column = column;
            this.measure = measure;
            this.ordered = ordered;
            this.groupingSets = groupingSets;
            this.rollupColumns = rollupColumns;
        }

        @Override
        public int getMeasureCount() {
            return 1;
        }

        @Override
        public RolapStar.Measure getMeasure(int i) {
            return measure;
        }

        @Override
        public String getMeasureAlias(int i) {
            return "m" + i;
        }

        @Override
        public RolapStar.Column[] getColumns() {
            return new RolapStar.Column[] { column };
        }

        @Override
        public String getColumnAlias(int i) {
            return "c" + i;
        }

        @Override
        public StarColumnPredicate getColumnPredicate(int i) {
            return new LiteralStarPredicate(column, true);
        }

        @Override
        protected boolean isAggregate() {
            return true;
        }

        @Override
        protected boolean isOrdered() {
            return ordered;
        }

        @Override
        protected List<RolapStar.Column[]> getGroupingSetsColumns() {
            return groupingSets;
        }

        @Override
        protected List<RolapStar.Column> getRollupColumns() {
            return rollupColumns;
        }

        @Override
        protected void addGroupingFunction(org.eclipse.daanse.rolap.common.sql.QueryRecorder query) {
            // These shapes carry no grouping functions.
        }

        @Override
        protected void addGroupingSets(org.eclipse.daanse.rolap.common.sql.QueryRecorder query,
                java.util.Map<RolapStar.Column, String> groupingSetsAliases) {
            // These shapes carry no grouping sets.
        }
    }

    private record Fixture(RolapStar star, RolapStar.Column idCol, RolapStar.Measure measure) {
    }

    private static final String BASE_FROM =
            " from \"fact_personal\" as \"fact_personal\""
            + " join \"schul_jahr\" as \"schul_jahr\" on \"fact_personal\".\"schul_jahr_id\" = \"schul_jahr\".\"id\"";

    private static RolapStar.Table table(String name, String alias, RolapStar.Table parent,
            RolapStar.Condition join) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getTableName()).thenReturn(name);
        when(t.getAlias()).thenReturn(alias);
        when(t.getParentTable()).thenReturn(parent);
        when(t.getJoinCondition()).thenReturn(join);
        return t;
    }

    private static Fixture fixture(Dialect dialect) {
        RolapStar.Table fact = table("fact_personal", "fact_personal", null, null);
        RolapStar.Condition join = mock(RolapStar.Condition.class);
        when(join.leftColumn()).thenReturn(Optional.of(new JoinColumn("fact_personal", "schul_jahr_id")));
        when(join.rightColumn()).thenReturn(Optional.of(new JoinColumn("schul_jahr", "id")));
        RolapStar.Table schulJahr = table("schul_jahr", "schul_jahr", fact, join);

        RolapStar.Column idCol = mock(RolapStar.Column.class);
        when(idCol.getTable()).thenReturn(schulJahr);
        when(idCol.getExpression()).thenReturn(new RolapColumn("schul_jahr", "id"));
        when(idCol.getInternalType()).thenReturn(BestFitColumnType.INT);

        Aggregator sum = mock(Aggregator.class);
        when(sum.getExpression(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> new StringBuilder("sum(" + inv.getArgument(0) + ")"));
        RolapStar.Measure measure = mock(RolapStar.Measure.class);
        when(measure.getTable()).thenReturn(fact);
        when(measure.getExpression()).thenReturn(new RolapColumn("fact_personal", "anzahl_personen"));
        when(measure.getInternalType()).thenReturn(BestFitColumnType.DECIMAL);
        when(measure.getAggregator()).thenReturn(sum);

        Context<?> context = mock(Context.class);
        // generateFormattedSql=false -> compact render (the pins below are single-line).
        doReturn(Boolean.FALSE).when(context).getConfigValue(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(Boolean.class));

        RolapStar star = mock(RolapStar.class);
        when(star.getFactTable()).thenReturn(fact);
        when(star.getDialect()).thenReturn(dialect);
        doReturn(context).when(star).getContext();
        // Only the distinct-subquery rewrite constructs a QueryRecorder; the builder-rendered
        // shapes never call this.
        when(star.newQueryRecorder()).thenAnswer(
                inv -> new org.eclipse.daanse.rolap.common.sql.QueryRecorder(false));
        return new Fixture(star, idCol, measure);
    }

    @Test
    void orderedSpecIsBuilderAuthoritative() {
        Dialect dialect = new AnsiDialect() {
            @Override
            public boolean requiresOrderByAlias() {
                return false;
            }
        };
        Fixture f = fixture(dialect);
        TestSpec spec = new TestSpec(f.star(), f.idCol(), f.measure(), false, true,
                List.of(), List.of());

        assertThat(spec.generateSql().sql()).isEqualTo(
                "select \"schul_jahr\".\"id\" as \"c0\", sum(\"fact_personal\".\"anzahl_personen\") as \"m0\""
                + BASE_FROM
                + " group by \"schul_jahr\".\"id\""
                + " order by \"schul_jahr\".\"id\" ASC");
    }

    @Test
    void countOnlySpecIsBuilderAuthoritative() {
        Fixture f = fixture(new AnsiDialect());
        TestSpec spec = new TestSpec(f.star(), f.idCol(), f.measure(), true, false,
                List.of(), List.of());

        assertThat(spec.generateSql().sql()).isEqualTo(
                "select COUNT(*) as \"c0\", sum(\"fact_personal\".\"anzahl_personen\") as \"m0\""
                + BASE_FROM);
    }

    @Test
    void groupingSetsSpecIsBuilderAuthoritative() {
        Dialect dialect = new AnsiDialect() {
            @Override
            public boolean supportsGroupingSets() {
                return true;
            }
        };
        Fixture f = fixture(dialect);
        TestSpec spec = new TestSpec(f.star(), f.idCol(), f.measure(), false, false,
                List.of(new RolapStar.Column[] { f.idCol() }, new RolapStar.Column[0]),
                List.of(f.idCol()));

        assertThat(spec.generateSql().sql()).isEqualTo(
                "select \"schul_jahr\".\"id\" as \"c0\", sum(\"fact_personal\".\"anzahl_personen\") as \"m0\","
                + " GROUPING(\"schul_jahr\".\"id\") as \"g0\""
                + BASE_FROM
                + " group by grouping sets ((\"schul_jahr\".\"id\"), ())");
    }

    /** A dialect without count-distinct support + a distinct measure: the distinct-subquery rewrite
     *  is produced by the recorder — the statement reads from the {@code dummyname} distinct
     *  subquery. */
    @Test
    void distinctSubqueryRewriteStillDeclinesToRecorder() {
        Dialect dialect = new AnsiDialect() {
            @Override
            public boolean allowsCountDistinct() {
                return false;
            }
        };
        Fixture f = fixture(dialect);
        when(f.measure().getAggregator().isDistinct()).thenReturn(true);
        Aggregator count = mock(Aggregator.class);
        when(count.getExpression(org.mockito.ArgumentMatchers.any(CharSequence.class)))
                .thenAnswer(inv -> new StringBuilder("count(" + inv.getArgument(0) + ")"));
        when(f.measure().getAggregator().getNonDistinctAggregator()).thenReturn(count);
        TestSpec spec = new TestSpec(f.star(), f.idCol(), f.measure(), false, false,
                List.of(), List.of());

        assertThat(spec.generateSql().sql()).contains("dummyname").contains("distinct");
    }

    /** A REAL distinct-count measure on a no-count-distinct dialect: builder-eligible — the flat
     *  {@code count(distinct x)} is emitted by the mapper and the RENDERER degrades it into the
     *  nested {@code dummyname} form (no QueryRecorder constructed). */
    @Test
    void distinctSimpleMeasureIsBuilderEligibleAndRendererRewritten() {
        Dialect dialect = new AnsiDialect() {
            @Override
            public boolean allowsCountDistinct() {
                return false;
            }
        };
        Fixture f = fixture(dialect);
        when(f.measure().getAggregator())
                .thenReturn(org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator.INSTANCE);
        TestSpec spec = new TestSpec(f.star(), f.idCol(), f.measure(), false, false,
                List.of(), List.of());

        assertThat(spec.generateSql().sql()).isEqualTo(
                "select \"d0\" as \"c0\", count(\"m0\") as \"c1\" from ("
                + "select distinct \"schul_jahr\".\"id\" as \"d0\","
                + " \"fact_personal\".\"anzahl_personen\" as \"m0\""
                + BASE_FROM + ") as \"dummyname\" group by \"d0\"");
    }

    /** The distinct-countOnly shape: NO count(*) column (legacy distinctGenerateSql semantics) —
     *  the flat distinct measure alone, renderer-rewritten into the nested count form. */
    @Test
    void distinctCountOnlyEmitsMeasuresOnly() {
        Dialect dialect = new AnsiDialect() {
            @Override
            public boolean allowsCountDistinct() {
                return false;
            }
        };
        Fixture f = fixture(dialect);
        when(f.measure().getAggregator())
                .thenReturn(org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator.INSTANCE);
        TestSpec spec = new TestSpec(f.star(), f.idCol(), f.measure(), true, false,
                List.of(), List.of());

        assertThat(spec.generateSql().sql()).isEqualTo(
                "select count(\"m0\") as \"c0\" from ("
                + "select distinct \"fact_personal\".\"anzahl_personen\" as \"m0\""
                + BASE_FROM + ") as \"dummyname\"");
    }
}
