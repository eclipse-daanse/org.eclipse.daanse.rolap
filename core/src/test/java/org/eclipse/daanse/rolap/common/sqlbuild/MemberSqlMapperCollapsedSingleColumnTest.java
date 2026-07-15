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
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
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
 * Verifies the collapsed single-column aggregate-table member-children read rendered through
 * {@link MemberSqlMapper#collapsedSingleColumnSql} (the member-children counterpart of
 * {@link AggCollapsedCandidateWhereTest}, which exercises the level-members shape). The SINGLE
 * child-level column ({@code quarter}) is projected/grouped/ordered, and the agg-substituted
 * parent-member restriction ({@code the_year = 1997}) renders as the parenthesised single-conjunct
 * WHERE.
 */
class MemberSqlMapperCollapsedSingleColumnTest {

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

    private String render(org.eclipse.daanse.sql.statement.api.model.SelectStatement stmt) {
        return new DialectSqlRenderer(new AnsiDialect()).render(stmt).sql();
    }

    /**
     * SELECT the child column only ({@code quarter}), WHERE the parenthesised parent-key group
     * ({@code the_year = 1997}): given an outer {@code And} whose single operand is a nested
     * member-key {@code And}, the mapper splits the outer {@code And} and the nested group keeps
     * its parentheses.
     */
    @Test
    void collapsedSingleColumnMemberChildrenPinsTheParentKeyWhere() {
        AggStar aggStar = mock(AggStar.class);
        RolapCubeLevel quarter = level("[Time].[Quarter]", aggStar, 2, "quarter");

        Predicate parentKeyGroup = Predicates.and(List.of(Predicates.comparison(
                Expressions.column(TableAlias.of(AGG), "the_year"),
                ComparisonOperator.EQ, Expressions.literal(1997, Datatype.INTEGER))));
        Optional<Predicate> aggWhere = Optional.of(Predicates.and(List.of(parentKeyGroup)));

        String sql = render(MemberSqlMapper.collapsedSingleColumnSql(quarter, aggStar, true, aggWhere));

        assertThat(sql).isEqualTo(
                "select \"" + AGG + "\".\"quarter\" as \"c0\""
                + " from \"" + AGG + "\" as \"" + AGG + "\""
                + " where (\"" + AGG + "\".\"the_year\" = 1997)"
                + " group by \"" + AGG + "\".\"quarter\""
                + " order by CASE WHEN \"" + AGG + "\".\"quarter\" IS NULL THEN 1 ELSE 0 END,"
                + " \"" + AGG + "\".\"quarter\" ASC");
    }
}
