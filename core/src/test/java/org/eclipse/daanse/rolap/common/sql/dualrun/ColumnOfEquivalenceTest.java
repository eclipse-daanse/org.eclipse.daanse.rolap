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
package org.eclipse.daanse.rolap.common.sql.dualrun;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.util.SqlExpressionResolver;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Proves the keystone identity for member/tuple SQL: {@code JoinPlanner.expressionFor(RolapColumn)}
 * rendered by {@link DialectSqlRenderer} equals the legacy
 * {@code SqlExpressionResolver.render(RolapColumn, sqlQuery)} — i.e. the dialect-free builder
 * column quotes a plain column exactly like {@code dialect.quoteIdentifier(table, name)}. This
 * underpins every member/tuple dual-run equivalence.
 */
class ColumnOfEquivalenceTest {

    private final Dialect ansi = new AnsiDialect();

    @Test
    void builderColumnEqualsLegacyGetExpression() {
        RolapColumn col = new RolapColumn("schul_jahr", "id");

        // legacy: SqlExpressionResolver.render(RolapColumn, dialect) → dialect.quoteIdentifier(table, name)
        String legacy = SqlExpressionResolver.render(col, ansi);

        // new: render JoinPlanner.expressionFor(col) as a bare select expression and strip the SELECT/alias.
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.table("schul_jahr", TableAlias.of("schul_jahr")));
        q.project(JoinPlanner.expressionFor(col), BestFitColumnType.INT);
        String rendered = new DialectSqlRenderer(ansi).render(q.build()).sql();
        // rendered: select "schul_jahr"."id" as "c0" from "schul_jahr" as "schul_jahr"
        String newExpr = rendered.substring("select ".length(), rendered.indexOf(" as "));

        SqlEquivalence.assertEquivalent(legacy, newExpr);
    }
}
