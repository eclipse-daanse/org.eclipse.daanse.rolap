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
 */
package org.eclipse.daanse.rolap.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.rolap.aggregator.extra.IppAggregator;
import org.eclipse.daanse.rolap.aggregator.extra.NoneAggregator;
import org.eclipse.daanse.rolap.aggregator.extra.RndAggregator;
import org.eclipse.daanse.rolap.common.SqlRender;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.junit.jupiter.api.Test;

/**
 * Verifies the generic {@link AbstractAggregator#toNode(SqlExpression)} default: for the simple
 * string-form aggregators ({@code None}, {@code Ipp}, {@code Rnd}) the dialect-free node render
 * equals the {@link AbstractAggregator#getExpression(CharSequence)} string default
 * {@code name(operand)}, so their rollup-measure segments render through the node channel.
 */
class AbstractAggregatorNodeFormTest {

    /** A verbatim operand so the assertion isolates the aggregator wrapper, not column spelling. */
    private static final String OPERAND = "emp.sal";

    private static String nodeForm(Aggregator agg) {
        SqlExpression node = SqlNodeAggregator.toNodeOrNull(agg, Expressions.raw(OPERAND));
        assertThat(node).as("%s must yield a node form", agg.getClass().getSimpleName()).isNotNull();
        return SqlRender.renderExpression(node, new AnsiDialect());
    }

    private static String stringForm(Aggregator agg) {
        return agg.getExpression(OPERAND).toString();
    }

    @Test
    void noneAggregatorNodeMatchesStringForm() {
        Aggregator agg = NoneAggregator.INSTANCE;
        assertThat(nodeForm(agg)).isEqualTo(stringForm(agg));
    }

    @Test
    void ippAggregatorNodeMatchesStringForm() {
        Aggregator agg = IppAggregator.INSTANCE;
        assertThat(nodeForm(agg)).isEqualTo(stringForm(agg));
    }

    @Test
    void rndAggregatorNodeMatchesStringForm() {
        Aggregator agg = RndAggregator.INSTANCE;
        assertThat(nodeForm(agg)).isEqualTo(stringForm(agg));
    }
}
