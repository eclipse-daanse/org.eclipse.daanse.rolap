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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Condition.JoinColumn;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Golden-SQL coverage for the context-constrained {@link MemberSqlMapper#childMemberSql(RolapLevel,
 * boolean, Optional, List)} star-join overload (Step B). The {@code SqlContextConstraint} paths are
 * native-evaluated / member-cached in the TCK, so the byte-for-byte match cannot be measured there;
 * this exercises the mapper directly with a constructed {@code ConstraintContribution} instead.
 * <p>
 * Verifies: FROM = dimension relation, then fact, then the context table (legacy add order); WHERE =
 * the star join predicates (parent-first) then each context predicate as its own conjunct, with the
 * multi-part parent key kept as one parenthesised AND group.
 */
class MemberSqlMapperContextTest {

    private static RolapStar.Table table(String name, String alias, RolapStar.Table parent,
            RolapStar.Condition join, RolapStar star) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getTableName()).thenReturn(name);
        when(t.getAlias()).thenReturn(alias);
        when(t.getParentTable()).thenReturn(parent);
        when(t.getJoinCondition()).thenReturn(join);
        when(t.getStar()).thenReturn(star);
        return t;
    }

    private static RolapStar.Condition join(String leftTable, String leftCol, String rightTable,
            String rightCol) {
        RolapStar.Condition c = mock(RolapStar.Condition.class);
        when(c.leftColumn()).thenReturn(Optional.of(new JoinColumn(leftTable, leftCol)));
        when(c.rightColumn()).thenReturn(Optional.of(new JoinColumn(rightTable, rightCol)));
        return c;
    }

    @Test
    void reproducesContextConstrainedMemberChildrenSql() {
        RolapStar star = mock(RolapStar.class);
        RolapStar.Table fact = table("sales_fact_1997", "sales_fact_1997", null, null, star);
        when(star.getFactTable()).thenReturn(fact);
        RolapStar.Table customer = table("customer", "customer", fact,
                join("sales_fact_1997", "customer_id", "customer", "customer_id"), star);
        RolapStar.Table time = table("time_by_day", "time_by_day", fact,
                join("sales_fact_1997", "time_id", "time_by_day", "time_id"), star);

        // The dimension being drilled: [Customer].[Gender], a single-table relation.
        TableSource customerSource = mock(TableSource.class);
        when(customerSource.getAlias()).thenReturn("customer");
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet.class);
        when(ncs.getName()).thenReturn("customer");
        when(customerSource.getTable()).thenReturn(ncs);

        RolapHierarchy hierarchy = mock(RolapHierarchy.class);
        when(hierarchy.getRelation()).thenReturn(customerSource);
        RolapLevel level = mock(RolapLevel.class);
        when(level.getHierarchy()).thenReturn(hierarchy);
        when(level.getKeyExp()).thenReturn(new RolapColumn("customer", "gender"));
        when(level.getInternalType()).thenReturn(BestFitColumnType.STRING);
        when(level.hasCaptionColumn()).thenReturn(false);
        when(level.getOrdinalExps()).thenReturn(List.of());
        when(level.getProperties()).thenReturn(new org.eclipse.daanse.rolap.element.RolapProperty[0]);

        // Contribution: a context restriction (time.the_year = 1997) AND the parent key
        // (customer.country = 'USA'), the parent key a nested AND so it stays a grouped conjunct.
        Predicate context = Predicates.comparison(
                Expressions.column(TableAlias.of("time_by_day"), "the_year"),
                ComparisonOperator.EQ, Expressions.literal(1997, Datatype.INTEGER));
        Predicate parentKey = Predicates.and(List.of(Predicates.comparison(
                Expressions.column(TableAlias.of("customer"), "country"),
                ComparisonOperator.EQ, Expressions.literal("USA", Datatype.VARCHAR))));
        Predicate where = Predicates.and(List.of(context, parentKey));

        Dialect ansi = new AnsiDialect();
        String sql = new DialectSqlRenderer(ansi)
                .render(MemberSqlMapper.childMemberSql(level, false, Optional.of(where),
                        List.of(customer, time), ansi.allowsSelectNotInGroupBy()))
                .sql();

        assertThat(sql).isEqualTo(
                "select \"customer\".\"gender\" as \"c0\""
                + " from \"customer\" as \"customer\", \"sales_fact_1997\" as \"sales_fact_1997\","
                + " \"time_by_day\" as \"time_by_day\""
                + " where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\""
                + " and \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\""
                + " and \"time_by_day\".\"the_year\" = 1997"
                + " and (\"customer\".\"country\" = 'USA')"
                + " order by CASE WHEN \"customer\".\"gender\" IS NULL THEN 1 ELSE 0 END,"
                + " \"customer\".\"gender\" ASC");
    }
}
