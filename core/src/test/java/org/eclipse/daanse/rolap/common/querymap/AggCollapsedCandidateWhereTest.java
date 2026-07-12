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

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies how {@link TupleSqlMapper#collapsedSingleColumnSql} renders the candidate WHERE for a
 * collapsed agg-table level-members read: the top-level {@code And} is split into per-conjunct
 * WHERE clauses, while a nested cross-join member-key group keeps its parentheses. Context
 * conjuncts precede the member-key groups and render unparenthesised.
 */
class AggCollapsedCandidateWhereTest {

    private static final String AGG = "agg_c_10_sales_fact_1997";

    private RolapCubeLevel level(String uniqueName, AggStar aggStar, int bitPos, String column) {
        RolapStar.Column starColumn = mock(RolapStar.Column.class);
        when(starColumn.getBitPosition()).thenReturn(bitPos);
        when(starColumn.getInternalType()).thenReturn(BestFitColumnType.INT);
        RolapCubeLevel level = mock(RolapCubeLevel.class);
        when(level.getStarKeyColumn()).thenReturn(starColumn);
        when(level.getUniqueName()).thenReturn(uniqueName);
        AggStar.Table.Column aggColumn = mock(AggStar.Table.Column.class);
        when(aggColumn.toSqlExpression())
                .thenReturn(Expressions.column(TableAlias.of(AGG), column));
        when(aggColumn.getExpression()).thenReturn(new RolapColumn(AGG, column));
        AggStar.Table table = mock(AggStar.Table.class);
        when(table.getName()).thenReturn(AGG);
        when(aggColumn.getTable()).thenReturn(table);
        when(aggStar.lookupColumn(bitPos)).thenReturn(aggColumn);
        return level;
    }

    @Test
    void candidateWhereRendersTheDescendantsKeyGroupParenthesised() {
        AggStar aggStar = mock(AggStar.class);
        RolapCubeLevel year = level("[Time].[Year]", aggStar, 1, "the_year");
        RolapCubeLevel quarter = level("[Time].[Quarter]", aggStar, 2, "quarter");

        // No context conjunct (all-member context); one member-key group nested under the
        // top-level And.
        Predicate keyGroup = Predicates.and(List.of(Predicates.comparison(
                Expressions.column(TableAlias.of(AGG), "the_year"),
                ComparisonOperator.EQ, Expressions.literal(1997, Datatype.INTEGER))));
        Optional<Predicate> candidateWhere = Optional.of(Predicates.and(List.of(keyGroup)));

        String sql = new DialectSqlRenderer(new AnsiDialect())
                .render(AggTupleQueries.collapsedSingleColumnSql(List.of(year, quarter), aggStar,
                        candidateWhere, Optional.empty(), Optional.empty()))
                .sql();

        assertThat(sql)
                .startsWith("select \"" + AGG + "\".\"the_year\" as \"c0\","
                        + " \"" + AGG + "\".\"quarter\" as \"c1\""
                        + " from \"" + AGG + "\" as \"" + AGG + "\""
                        + " where (\"" + AGG + "\".\"the_year\" = 1997)"
                        + " group by \"" + AGG + "\".\"the_year\", \"" + AGG + "\".\"quarter\"")
                .contains(" order by ");
    }

    /** Context conjuncts precede the member-key group and render unparenthesised. */
    @Test
    void contextConjunctsPrecedeTheArgGroupUnparenthesised() {
        AggStar aggStar = mock(AggStar.class);
        RolapCubeLevel quarter = level("[Time].[Quarter]", aggStar, 2, "quarter");

        Predicate context = Predicates.comparison(
                Expressions.column(TableAlias.of(AGG), "store_id"),
                ComparisonOperator.EQ, Expressions.literal(7, Datatype.INTEGER));
        Predicate keyGroup = Predicates.and(List.of(Predicates.comparison(
                Expressions.column(TableAlias.of(AGG), "the_year"),
                ComparisonOperator.EQ, Expressions.literal(1997, Datatype.INTEGER))));
        Optional<Predicate> candidateWhere = Optional.of(Predicates.and(List.of(context, keyGroup)));

        String sql = new DialectSqlRenderer(new AnsiDialect())
                .render(AggTupleQueries.collapsedSingleColumnSql(List.of(quarter), aggStar,
                        candidateWhere, Optional.empty(), Optional.empty()))
                .sql();

        assertThat(sql).contains(
                " where \"" + AGG + "\".\"store_id\" = 7"
                + " and (\"" + AGG + "\".\"the_year\" = 1997)"
                + " group by ");
    }
}
