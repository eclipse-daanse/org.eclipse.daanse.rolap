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

import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
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
 * column {@code c0} grouped, measure {@code m0} as lower-case {@code sum(...)}.
 */
class AggregateSqlMapperTest {

    private static RolapStar.Table table(String name, String alias, RolapStar.Table parent,
            RolapStar.Condition join) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getTableName()).thenReturn(name);
        when(t.getAlias()).thenReturn(alias);
        when(t.getParentTable()).thenReturn(parent);
        when(t.getJoinCondition()).thenReturn(join);
        return t;
    }

    @Test
    void reproducesCapturedSegmentSql() {
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

        Dialect ansi = new AnsiDialect();
        Predicate filter = Predicates.comparison(Expressions.column(TableAlias.of("schul_jahr"), "id"),
                ComparisonOperator.EQ, Expressions.literal(4, Datatype.INTEGER));

        String sql = new DialectSqlRenderer(ansi)
                .render(AggregateSqlMapper.aggregate(fact, List.of(idCol), List.of(filter), List.of(measure), ansi))
                .sql();

        assertThat(sql).isEqualTo(
                "select \"schul_jahr\".\"id\" as \"c0\", sum(\"fact_personal\".\"anzahl_personen\") as \"m0\""
                + " from \"fact_personal\" as \"fact_personal\""
                + " join \"schul_jahr\" as \"schul_jahr\" on \"fact_personal\".\"schul_jahr_id\" = \"schul_jahr\".\"id\""
                + " where \"schul_jahr\".\"id\" = 4"
                + " group by \"schul_jahr\".\"id\"");
    }
}
