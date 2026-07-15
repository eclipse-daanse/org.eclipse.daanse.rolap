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
package org.eclipse.daanse.rolap.common.constraint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.common.SqlRender;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.AggPlan;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code SqlContextConstraint.aggContextColumnPredicate} — the per-context-column agg
 * substitution ({@code aggStar.lookupColumn(bitPos).toSqlExpression()}). Pins the substitution, the
 * {@link AggPlan.AggColumnPredicate} provenance (the agg table, for the fact-join interleaving), the
 * {@code #null}-sentinel {@code IS NULL} translation, and the null bail signal for an
 * agg-unresolvable column ("agg-missing-column").
 */
class SqlContextConstraintAggModeTest {

    private static final String AGG = "agg_c_10_sales_fact_1997";

    private final AggStar aggStar = mock(AggStar.class);
    private final AggStar.Table aggTable = mock(AggStar.Table.class);
    private final RolapStar.Column yearCol = column(7, Datatype.INTEGER);

    SqlContextConstraintAggModeTest() {
        when(aggTable.getName()).thenReturn(AGG);
        AggStar.Table.Column aggColumn = mock(AggStar.Table.Column.class);
        when(aggColumn.toSqlExpression())
                .thenReturn(Expressions.column(TableAlias.of(AGG), "the_year"));
        when(aggColumn.getTable()).thenReturn(aggTable);
        when(aggStar.lookupColumn(7)).thenReturn(aggColumn);
    }

    private static RolapStar.Column column(int bitPos, Datatype datatype) {
        RolapStar.Column col = mock(RolapStar.Column.class);
        when(col.getBitPosition()).thenReturn(bitPos);
        when(col.getDatatype()).thenReturn(datatype);
        return col;
    }

    private static String render(Predicate p) {
        return SqlRender.renderPredicate(p, new AnsiDialect());
    }

    @Test
    void substitutesTheAggColumnAndCarriesItsTable() {
        AggPlan.AggColumnPredicate acp =
                SqlContextConstraint.aggContextColumnPredicate(aggStar, yearCol, 1997);

        assertThat(acp).isNotNull();
        assertThat(acp.table()).isSameAs(aggTable);
        assertThat(render(acp.predicate())).isEqualTo("\"" + AGG + "\".\"the_year\" = 1997");
    }

    /** The null sentinel Util.sqlNullValue (toString "#null") must become IS NULL, never a literal. */
    @Test
    void nullSentinelBecomesIsNull() {
        AggPlan.AggColumnPredicate sentinel =
                SqlContextConstraint.aggContextColumnPredicate(aggStar, yearCol, Util.sqlNullValue);
        AggPlan.AggColumnPredicate javaNull =
                SqlContextConstraint.aggContextColumnPredicate(aggStar, yearCol, null);

        assertThat(render(sentinel.predicate())).isEqualTo("\"" + AGG + "\".\"the_year\" is null");
        assertThat(render(javaNull.predicate())).isEqualTo("\"" + AGG + "\".\"the_year\" is null");
    }

    /** No agg column for the bit position -> the null bail signal ("agg-missing-column"). */
    @Test
    void missingAggColumnSignalsBail() {
        assertThat(SqlContextConstraint.aggContextColumnPredicate(
                mock(AggStar.class), yearCol, 1997)).isNull();
    }

    /** An agg column without a dialect-free expression is the same bail class. */
    @Test
    void aggColumnWithoutExpressionSignalsBail() {
        AggStar broken = mock(AggStar.class);
        when(broken.lookupColumn(7)).thenReturn(mock(AggStar.Table.Column.class));

        assertThat(SqlContextConstraint.aggContextColumnPredicate(broken, yearCol, 1997)).isNull();
    }
}
