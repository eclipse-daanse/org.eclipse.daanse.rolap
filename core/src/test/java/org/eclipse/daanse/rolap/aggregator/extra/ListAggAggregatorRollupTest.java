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
package org.eclipse.daanse.rolap.aggregator.extra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.eclipse.daanse.olap.api.DataTypeJdbc;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ListAggAggregator} supports in-memory rollup of
 * segment results so parent-level cells (e.g. "All Categories" totals) get
 * the concatenated child-level values — parallel to how
 * {@code SumAggregator} rolls up numeric segments.
 *
 * <p>Prior to this, {@link ListAggAggregator#supportsFastAggregates}
 * returned {@code false} and {@link ListAggAggregator#aggregate(List,
 * DataTypeJdbc)} threw {@link UnsupportedOperationException}, so totals
 * across a text-aggregation measure came out empty in Excel/MDX.
 */
class ListAggAggregatorRollupTest {

    private static ListAggAggregator nonDistinct(String sep) {
        return new ListAggAggregator(false, sep, null, null, null, null);
    }

    private static ListAggAggregator distinct(String sep) {
        return new ListAggAggregator(true, sep, null, null, null, null);
    }

    @Test
    void supportsVarcharFastAggregation() {
        ListAggAggregator agg = nonDistinct(" | ");
        assertThat(agg.supportsFastAggregates(DataTypeJdbc.VARCHAR)).isTrue();
        assertThat(agg.supportsFastAggregates(DataTypeJdbc.NUMERIC)).isFalse();
    }

    @Test
    void nonDistinctConcatenatesAllStringsWithSeparator() {
        ListAggAggregator agg = nonDistinct(" | ");

        Object out = agg.aggregate(Arrays.asList("alpha", "beta", "gamma"), DataTypeJdbc.VARCHAR);

        assertThat(out).isEqualTo("alpha | beta | gamma");
    }

    @Test
    void nonDistinctRollupConcatenatesAlreadyAggregatedStrings() {
        // Each input string is itself the LISTAGG result of a child segment.
        ListAggAggregator agg = nonDistinct(" | ");

        Object out = agg.aggregate(Arrays.asList("a | b", "c | d"), DataTypeJdbc.VARCHAR);

        assertThat(out).isEqualTo("a | b | c | d");
    }

    @Test
    void distinctRollupDedupesIndividualElements() {
        // DISTINCT must dedupe individual elements across already-aggregated
        // child strings, not just dedupe whole strings.
        ListAggAggregator agg = distinct(" | ");

        Object out = agg.aggregate(Arrays.asList("a | b", "a | c", "d"), DataTypeJdbc.VARCHAR);

        assertThat(out).isEqualTo("a | b | c | d");
    }

    @Test
    void nullsAreSkipped() {
        ListAggAggregator agg = nonDistinct(",");

        Object out = agg.aggregate(Arrays.asList("a", null, "b", null, "c"), DataTypeJdbc.VARCHAR);

        assertThat(out).isEqualTo("a,b,c");
    }

    @Test
    void allNullsYieldsNull() {
        ListAggAggregator agg = nonDistinct(",");

        Object out = agg.aggregate(Arrays.asList(null, null), DataTypeJdbc.VARCHAR);

        assertThat(out).isNull();
    }

    @Test
    void emptyInputYieldsNull() {
        ListAggAggregator agg = nonDistinct(",");

        assertThat(agg.aggregate(List.of(), DataTypeJdbc.VARCHAR)).isNull();
    }

    @Test
    void coalesceReplacesNullCells() {
        ListAggAggregator agg = new ListAggAggregator(false, ",", null, "<empty>", null, null);

        Object out = agg.aggregate(Arrays.asList("a", null, "b"), DataTypeJdbc.VARCHAR);

        assertThat(out).isEqualTo("a,<empty>,b");
    }

    @Test
    void separatorDefaultsToCommaWhenNull() {
        // If the configured separator is null, fall back to "," rather than
        // throwing NullPointerException via String.join.
        ListAggAggregator agg = new ListAggAggregator(false, null, null, null, null, null);

        Object out = agg.aggregate(Arrays.asList("x", "y"), DataTypeJdbc.VARCHAR);

        assertThat(out).isEqualTo("x,y");
    }
}
