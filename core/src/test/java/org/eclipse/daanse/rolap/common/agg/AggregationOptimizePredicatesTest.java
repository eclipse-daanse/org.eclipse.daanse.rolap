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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.agg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The predicate-bloat heuristic decides which WHERE-IN constraints are
 * THROWN AWAY before the segment SQL runs - it widens the loaded cell
 * rectangle, it must never widen the answer (the segment still
 * constrains at read time), but a wrong drop loads unbounded data and a
 * wrong keep overflows the dialect's IN-list limit. It had no direct
 * test at all.
 */
class AggregationOptimizePredicatesTest {

    private static final int MAX_CONSTRAINTS = 10;

    private final RolapStar star =
        mock(RolapStar.class, Mockito.RETURNS_DEEP_STUBS);
    private final BitKey bitKey = BitKey.Factory.makeBitKey(8);
    private final Aggregation aggregation =
        new Aggregation(star, bitKey, List.of(), MAX_CONSTRAINTS);

    private RolapStar.Column column(int cardinality) {
        RolapStar.Column column = mock(RolapStar.Column.class);
        when(column.getCardinality()).thenReturn((long) cardinality);
        return column;
    }

    private StarColumnPredicate values(RolapStar.Column column, int count) {
        List<StarColumnPredicate> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new ValueColumnPredicate(column, "v" + i));
        }
        return new ListColumnPredicate(column, list);
    }

    /** Beyond maxConstraints the IN-list is dropped even with the flag off. */
    @Test
    void overlongInListsAreAlwaysDropped() {
        RolapStar.Column column = column(1000);
        StarColumnPredicate[] in = { values(column, MAX_CONSTRAINTS + 1) };

        StarColumnPredicate[] out = aggregation.optimizePredicates(
            new RolapStar.Column[] { column }, in, false);

        assertThat(out[0])
            .as("an IN-list the dialect cannot execute must be dropped")
            .isInstanceOf(LiteralStarPredicate.class);
    }

    /** With optimizePredicates=false a selective constraint stays. */
    @Test
    void theFlagOffKeepsSelectiveConstraints() {
        RolapStar.Column column = column(100);
        StarColumnPredicate[] in = { values(column, 8) }; // bloat 0.08

        StarColumnPredicate[] out = aggregation.optimizePredicates(
            new RolapStar.Column[] { column }, in, false);

        assertThat(out[0]).isInstanceOf(ListColumnPredicate.class);
    }

    /**
     * The relative rule: constraints are dropped by descending bloat
     * until the constrained cell count is half the unconstrained one -
     * the coarse constraint (60% of the column) goes, the selective one
     * (10%) stays.
     */
    @Test
    void coarseConstraintsAreDroppedSelectiveOnesStay() {
        RolapStar.Column coarse = column(10);
        RolapStar.Column selective = column(100);
        StarColumnPredicate[] in = {
            values(coarse, 6),      // bloat 0.6
            values(selective, 10),  // bloat 0.1
        };

        StarColumnPredicate[] out = aggregation.optimizePredicates(
            new RolapStar.Column[] { coarse, selective }, in, true);

        assertThat(out[0])
            .as("the coarse constraint is optimized away")
            .isInstanceOf(LiteralStarPredicate.class);
        assertThat(out[1])
            .as("the selective constraint survives")
            .isInstanceOf(ListColumnPredicate.class);
    }

    /**
     * A single-value list is never DROPPED; the structural collapse to a
     * value predicate is deliberately disabled (see StarPredicates.optimize
     * - the golden-SQL pins assert the IN-list form), so it stays a list.
     */
    @Test
    void singleValueListsSurviveUncollapsed() {
        RolapStar.Column column = column(100);
        StarColumnPredicate[] in = { values(column, 1) };

        StarColumnPredicate[] out = aggregation.optimizePredicates(
            new RolapStar.Column[] { column }, in, true);

        assertThat(out[0]).isInstanceOf(ListColumnPredicate.class);
    }

    /** The input array is never mutated - callers reuse it. */
    @Test
    void theInputArrayStaysUntouched() {
        RolapStar.Column column = column(10);
        StarColumnPredicate original = values(column, 6);
        StarColumnPredicate[] in = { original };

        aggregation.optimizePredicates(
            new RolapStar.Column[] { column }, in, true);

        assertThat(in[0]).isSameAs(original);
    }
}
