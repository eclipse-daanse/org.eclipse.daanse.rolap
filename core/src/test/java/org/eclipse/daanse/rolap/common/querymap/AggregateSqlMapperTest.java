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

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Condition.JoinColumn;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link AggregateSqlMapper} reproduces the real captured segment SQL (H2 School
 * catalog): a comma-product fact+dimension join, {@code [join, constraint]} WHERE order, dimension
 * column {@code c0} grouped, measure {@code m0} as lower-case {@code sum(...)} — plus the lifted
 * {@link AggregateSqlMapper.Shape} variants (countOnly / ordered / grouping sets), string-pinned
 * per capability branch since the H2/MySQL TCK logs carry zero occurrences of those shapes.
 */
class AggregateSqlMapperTest {

    /** The shared School-catalog star fixture: fact + one snowflake dimension, one constrained
     *  dimension column and one sum measure. */
    private record Fixture(RolapStar.Table fact, RolapStar.Column idCol, RolapStar.Measure measure,
            Predicate filter) {
    }

    private static final String BASE_FROM_WHERE =
            " from \"fact_personal\" as \"fact_personal\""
            + " join \"schul_jahr\" as \"schul_jahr\" on \"fact_personal\".\"schul_jahr_id\" = \"schul_jahr\".\"id\""
            + " where \"schul_jahr\".\"id\" = 4";

    private static RolapStar.Table table(String name, String alias, RolapStar.Table parent,
            RolapStar.Condition join) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getTableName()).thenReturn(name);
        when(t.getAlias()).thenReturn(alias);
        when(t.getParentTable()).thenReturn(parent);
        when(t.getJoinCondition()).thenReturn(join);
        return t;
    }

    private static Fixture fixture() {
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

        Predicate filter = Predicates.comparison(Expressions.column(TableAlias.of("schul_jahr"), "id"),
                ComparisonOperator.EQ, Expressions.literal(4, Datatype.INTEGER));
        return new Fixture(fact, idCol, measure, filter);
    }

    private static String render(Dialect dialect, Fixture f, AggregateSqlMapper.Shape shape) {
        return new DialectSqlRenderer(dialect)
                .render(AggregateSqlMapper.aggregate(f.fact(), List.of(f.idCol()), List.of(f.filter()),
                        List.of(f.measure()), dialect, List.of(), shape))
                .sql();
    }

    @Test
    void reproducesCapturedSegmentSql() {
        Fixture f = fixture();
        Dialect ansi = new AnsiDialect();

        String sql = new DialectSqlRenderer(ansi)
                .render(AggregateSqlMapper.aggregate(f.fact(), List.of(f.idCol()), List.of(f.filter()),
                        List.of(f.measure()), ansi))
                .sql();

        assertThat(sql).isEqualTo(
                "select \"schul_jahr\".\"id\" as \"c0\", sum(\"fact_personal\".\"anzahl_personen\") as \"m0\""
                + BASE_FROM_WHERE
                + " group by \"schul_jahr\".\"id\"");
    }

    /** countOnly: one leading {@code COUNT(*)} (auto-aliased {@code c0}) replaces the per-column
     *  SELECT/GROUP BY; the measures still follow; FROM/WHERE (joins + constraints) are unchanged.
     *  {@code COUNT(*)} is the established builder spelling (drill-through count precedent) — the
     *  recorder spelled it lower-case. */
    @Test
    void countOnlyProjectsGrandTotalWithoutGroupBy() {
        String sql = render(new AnsiDialect(), fixture(),
                new AggregateSqlMapper.Shape(true, false, List.of(), List.of()));

        assertThat(sql).isEqualTo(
                "select COUNT(*) as \"c0\", sum(\"fact_personal\".\"anzahl_personen\") as \"m0\""
                + BASE_FROM_WHERE);
    }

    /** ordered: deterministic results — ORDER BY each group-by projection ascending (non-nullable,
     *  so no null-collation SQL), expression-spelled for a dialect without requiresOrderByAlias. */
    @Test
    void orderedAppendsAscOrderByOverGroupedProjection() {
        Dialect dialect = new AnsiDialect() {
            @Override
            public boolean requiresOrderByAlias() {
                return false;
            }
        };
        String sql = render(dialect, fixture(),
                new AggregateSqlMapper.Shape(false, true, List.of(), List.of()));

        assertThat(sql).isEqualTo(
                "select \"schul_jahr\".\"id\" as \"c0\", sum(\"fact_personal\".\"anzahl_personen\") as \"m0\""
                + BASE_FROM_WHERE
                + " group by \"schul_jahr\".\"id\""
                + " order by \"schul_jahr\".\"id\" ASC");
    }

    /** ordered on a requiresOrderByAlias dialect (e.g. MySQL): the sort key is spelled via the
     *  projection alias — the ORDER BY rides the projection ref, so the renderer decides. */
    @Test
    void orderedUsesAliasWhenDialectRequiresOrderByAlias() {
        Dialect dialect = new AnsiDialect() {
            @Override
            public boolean requiresOrderByAlias() {
                return true;
            }
        };
        String sql = render(dialect, fixture(),
                new AggregateSqlMapper.Shape(false, true, List.of(), List.of()));

        assertThat(sql).endsWith(" order by \"c0\" ASC");
    }

    /** grouping sets, dialect supports them: {@code GROUP BY GROUPING SETS ((keys), ())} replaces the
     *  plain group-by spelling and each rollup column surfaces as a {@code GROUPING(x)} SELECT-tail
     *  column (renderer-aliased {@code g0}). */
    @Test
    void groupingSetsRenderGroupingSetsWhenSupported() {
        Fixture f = fixture();
        Dialect dialect = new AnsiDialect() {
            @Override
            public boolean supportsGroupingSets() {
                return true;
            }
        };
        String sql = render(dialect, f, new AggregateSqlMapper.Shape(false, false,
                List.of(List.of(f.idCol()), List.of()), List.of(f.idCol())));

        assertThat(sql).isEqualTo(
                "select \"schul_jahr\".\"id\" as \"c0\", sum(\"fact_personal\".\"anzahl_personen\") as \"m0\","
                + " GROUPING(\"schul_jahr\".\"id\") as \"g0\""
                + BASE_FROM_WHERE
                + " group by grouping sets ((\"schul_jahr\".\"id\"), ())");
    }

    /** grouping sets, dialect does NOT support them: the renderer falls back to the plain group-by
     *  keys (recorded alongside the sets — capability spelled at the renderer, not gated in the
     *  mapper); the {@code GROUPING(x)} column still projects. */
    @Test
    void groupingSetsFallBackToPlainGroupByWhenUnsupported() {
        Fixture f = fixture();
        Dialect dialect = new AnsiDialect() {
            @Override
            public boolean supportsGroupingSets() {
                return false;
            }
        };
        String sql = render(dialect, f, new AggregateSqlMapper.Shape(false, false,
                List.of(List.of(f.idCol()), List.of()), List.of(f.idCol())));

        assertThat(sql).isEqualTo(
                "select \"schul_jahr\".\"id\" as \"c0\", sum(\"fact_personal\".\"anzahl_personen\") as \"m0\","
                + " GROUPING(\"schul_jahr\".\"id\") as \"g0\""
                + BASE_FROM_WHERE
                + " group by \"schul_jahr\".\"id\"");
    }
}
