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

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
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
 * Verifies the SQL the context-constrained {@link MemberSqlMapper#childMemberSql(RolapLevel,
 * boolean, Optional, List)} star-join overload produces: FROM = the dimension relation as root,
 * the fact joined into it on the dimension's own fact edge, then the context table joined to the
 * fact (all ANSI JOIN…ON); WHERE = each context predicate as its own conjunct, with the multi-part
 * parent key kept as one parenthesised AND group. Also verifies the by-name lookup splits the
 * top-level AND so the parent-key group and the {@code UPPER(name)} filter render as separate
 * conjuncts.
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
                        List.of(customer, time)))
                .sql();

        assertThat(sql).isEqualTo(
                "select \"customer\".\"gender\" as \"c0\""
                + " from \"customer\" as \"customer\""
                + " join \"sales_fact_1997\" as \"sales_fact_1997\""
                + " on \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\""
                + " join \"time_by_day\" as \"time_by_day\""
                + " on \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\""
                + " where \"time_by_day\".\"the_year\" = 1997"
                + " and (\"customer\".\"country\" = 'USA')"
                + " order by CASE WHEN \"customer\".\"gender\" IS NULL THEN 1 ELSE 0 END,"
                + " \"customer\".\"gender\" ASC");
    }

    /**
     * The dimension-only child SELECT's two WHERE forms: the plain parent-key enumeration keeps its
     * multi-column key as ONE grouped conjunct ({@code splitConjuncts=false}), while the by-name
     * lookup ({@code splitConjuncts=true}) splits the top-level AND so the parent-key group and the
     * {@code UPPER(name) = UPPER('x')} filter render as two separate conjuncts —
     * {@code (group) and UPPER(name) = UPPER('x')}, not {@code ((group) and UPPER(name) = UPPER('x'))}.
     */
    @Test
    void byNameSplitsParentKeyAndNameFilterIntoSeparateConjuncts() {
        TableSource customerSource = mock(TableSource.class);
        when(customerSource.getAlias()).thenReturn("customer");
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet.class);
        when(ncs.getName()).thenReturn("customer");
        when(customerSource.getTable()).thenReturn(ncs);

        RolapLevel level = mock(RolapLevel.class);
        RolapHierarchy hierarchy = mock(RolapHierarchy.class);
        when(hierarchy.getRelation()).thenReturn(customerSource);
        when(hierarchy.getLevels()).thenReturn((List) List.of(level));
        when(level.getHierarchy()).thenReturn(hierarchy);
        when(level.getDepth()).thenReturn(0);
        when(level.isAll()).thenReturn(false);
        when(level.getUniqueName()).thenReturn("[Customers].[Name]");
        when(level.getKeyExp()).thenReturn(new RolapColumn("customer", "customer_id"));
        when(level.getInternalType()).thenReturn(BestFitColumnType.STRING);
        when(level.hasCaptionColumn()).thenReturn(false);
        when(level.getOrdinalExps()).thenReturn(List.of());
        when(level.getProperties()).thenReturn(new org.eclipse.daanse.rolap.element.RolapProperty[0]);

        // where = parent-key group (a nested AND, one grouped conjunct) AND the by-name filter.
        Predicate parentGroup = Predicates.and(List.of(Predicates.comparison(
                Expressions.column(TableAlias.of("customer"), "city"),
                ComparisonOperator.EQ, Expressions.literal("San Francisco", Datatype.VARCHAR))));
        Predicate nameFilter = Predicates.comparison(
                new org.eclipse.daanse.sql.statement.api.expression.SqlExpression.CaseFold(
                        Expressions.column(TableAlias.of("customer"), "fullname")),
                ComparisonOperator.EQ,
                new org.eclipse.daanse.sql.statement.api.expression.SqlExpression.CaseFold(
                        Expressions.literal("Gladys Evans", Datatype.VARCHAR)));
        Predicate where = Predicates.and(List.of(parentGroup, nameFilter));

        Dialect ansi = new AnsiDialect();
        String grouped = new DialectSqlRenderer(ansi)
                .render(MemberSqlMapper.childMemberSql(level, false, Optional.of(where), false)).sql();
        String split = new DialectSqlRenderer(ansi)
                .render(MemberSqlMapper.childMemberSql(level, false, Optional.of(where), true)).sql();

        // Non-split: the whole AND is one conjunct → an extra outer paren.
        assertThat(grouped).contains(
                " where ((\"customer\".\"city\" = 'San Francisco')"
                + " and UPPER(\"customer\".\"fullname\") = UPPER('Gladys Evans'))");
        // Split: parent-key group and name filter are two separate conjuncts.
        assertThat(split).contains(
                " where (\"customer\".\"city\" = 'San Francisco')"
                + " and UPPER(\"customer\".\"fullname\") = UPPER('Gladys Evans')");
    }
}
