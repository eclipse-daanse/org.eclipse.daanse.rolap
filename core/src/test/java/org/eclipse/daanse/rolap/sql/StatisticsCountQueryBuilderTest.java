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
package org.eclipse.daanse.rolap.sql;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Pins the renderer's count-query text form: {@link DialectSqlRenderer} spells {@code COUNT(*)}
 * upper-case and synthesizes a {@code c0} projection alias — both diverge from the form the
 * SQL-pattern-asserting TCK expects, which is why statistics count queries keep their producer
 * string form rather than routing through the builder.
 */
class StatisticsCountQueryBuilderTest {

    private final AnsiDialect ansi = new AnsiDialect();

    @Test
    void countOverRawSqlSubquery() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.project(Expressions.countStar(), null);
        q.from(From.raw("select \"a\" from \"foo\"", TableAlias.of("init")));
        String sql = new DialectSqlRenderer(ansi).render(q.build()).sql();
        assertThat(sql).isEqualTo(
            "select COUNT(*) as \"c0\" from (select \"a\" from \"foo\") as \"init\"");
    }

    @Test
    void countOverDistinctInner() {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.project(Expressions.countStar(), null);
        q.from(From.raw("select distinct \"c\" as \"c0\" from \"t\"", TableAlias.of("init")));
        String sql = new DialectSqlRenderer(ansi).render(q.build()).sql();
        assertThat(sql).isEqualTo(
            "select COUNT(*) as \"c0\" from (select distinct \"c\" as \"c0\" from \"t\") as \"init\"");
    }
}
