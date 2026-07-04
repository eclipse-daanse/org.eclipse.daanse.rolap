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

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.common.agg.ListColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.ValueColumnPredicate;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Phase-4 constraint translation: {@link StarPredicateTranslator} turns ROLAP star predicates
 * into builder {@link Predicate}s that render exactly like the legacy {@code toSql} hook
 * (verified against the captured segment constraint {@code "schul_jahr"."id" = 4}).
 */
class StarPredicateTranslatorTest {

    private static RolapStar.Column column(String alias, String name, Datatype type) {
        RolapStar.Table table = mock(RolapStar.Table.class);
        when(table.getAlias()).thenReturn(alias);
        RolapStar.Column col = mock(RolapStar.Column.class);
        when(col.getTable()).thenReturn(table);
        when(col.getExpression()).thenReturn(new org.eclipse.daanse.rolap.element.RolapColumn(alias, name));
        when(col.getDatatype()).thenReturn(type);
        return col;
    }

    /** Render a predicate as the WHERE of a trivial query (so we exercise the real renderer). */
    private static String renderWhere(Predicate p) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.table("schul_jahr", TableAlias.of("schul_jahr")));
        q.where(p);
        String sql = new DialectSqlRenderer(new AnsiDialect()).render(q.build()).sql();
        return sql.substring(sql.indexOf(" where ") + " where ".length());
    }

    @Test
    void valueColumnPredicateBecomesEquals() {
        RolapStar.Column col = column("schul_jahr", "id", Datatype.INTEGER);
        ValueColumnPredicate value = mock(ValueColumnPredicate.class);
        when(value.getConstrainedColumn()).thenReturn(col);
        when(value.getValue()).thenReturn(4);

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(value)))
                .isEqualTo("\"schul_jahr\".\"id\" = 4");
    }

    @Test
    void listColumnPredicateBecomesIn() {
        RolapStar.Column col = column("schul_jahr", "id", Datatype.INTEGER);
        ValueColumnPredicate v1 = mock(ValueColumnPredicate.class);
        when(v1.getConstrainedColumn()).thenReturn(col);
        when(v1.getValue()).thenReturn(4);
        ValueColumnPredicate v2 = mock(ValueColumnPredicate.class);
        when(v2.getConstrainedColumn()).thenReturn(col);
        when(v2.getValue()).thenReturn(5);

        ListColumnPredicate list = mock(ListColumnPredicate.class);
        when(list.getConstrainedColumn()).thenReturn(col);
        when(list.getPredicates()).thenReturn(List.<StarColumnPredicate>of(v1, v2));

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(list)))
                .isEqualTo("\"schul_jahr\".\"id\" in (4, 5)");
    }
}
