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
package org.eclipse.daanse.rolap.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link SqlTupleReader#classifyAggShape} as a pure decision table: label derivation from the
 * per-read flags, independent of any live star/aggStar fixture. Flag order: {@code (singleTarget,
 * allCollapsedSingle, anyDimJoin, anyCollapsed, hasPlanPredicates, dimTablePredicate,
 * factJoinRequired)}.
 */
class AggProbeClassifierTest {

    /** Every projected level collapsed-single-column, predicates (if any) on the agg fact — the
     *  all-collapsed projection, single- and multi-target alike. */
    @Test
    void allCollapsedWithoutDimPredicateIsCollapsedShape() {
        assertThat(SqlTupleReader.classifyAggShape(false, true, false, true, true, false, true))
                .isEqualTo("agg-mt-collapsed");
        assertThat(SqlTupleReader.classifyAggShape(true, true, false, true, false, false, false))
                .isEqualTo("agg-mt-collapsed");
    }

    /** An agg-DIM-table predicate disqualifies the single-agg-table collapsed candidate: the
     *  read falls through to the fact-join family (predicates present). */
    @Test
    void allCollapsedWithDimTablePredicateFallsToFactJoin() {
        assertThat(SqlTupleReader.classifyAggShape(true, true, false, true, true, true, true))
                .isEqualTo("agg-st-factjoin");
        assertThat(SqlTupleReader.classifyAggShape(false, true, false, true, true, true, true))
                .isEqualTo("agg-mt-factjoin");
    }

    /** A collapsed multi-column level needing the dimension for its extras wins over the fact-join
     *  classification, split by target count. */
    @Test
    void dimJoinFamilyWinsOverFactJoin() {
        assertThat(SqlTupleReader.classifyAggShape(true, false, true, true, true, false, true))
                .isEqualTo("agg-st-dimjoin");
        assertThat(SqlTupleReader.classifyAggShape(false, false, true, true, false, false, false))
                .isEqualTo("agg-mt-dimjoin");
    }

    /** Agg-substituted predicates or a forced existence join, no dim-join level. */
    @Test
    void factJoinFamilySplitsByTargetCount() {
        assertThat(SqlTupleReader.classifyAggShape(true, false, false, false, true, false, false))
                .isEqualTo("agg-st-factjoin");
        assertThat(SqlTupleReader.classifyAggShape(false, false, false, false, false, false, true))
                .isEqualTo("agg-mt-factjoin");
    }

    /** Single target, nothing agg-shaped beyond the projection substitution. */
    @Test
    void unconstrainedNonCollapsedSingleTargetIsNeutral() {
        assertThat(SqlTupleReader.classifyAggShape(true, false, false, false, false, false, false))
                .isEqualTo("agg-st-neutral");
    }

    /** The mixed multi-target read: collapsed level(s) projected on the agg fact plus non-collapsed
     *  target(s) joined through it, with no agg-substituted predicate (e.g. a NonEmptyCrossJoin of
     *  [Time].[Month] x [Store Country]). Such a read carries factJoinRequired=TRUE (a
     *  NonEmptyCrossJoin's SetConstraint.isJoinRequired is args.length > 1), so the mixed row must
     *  win over the fact-join row for the no-predicate case. */
    @Test
    void mixedCollapsedMultiTargetIsAggMixed() {
        // Multi-target, mixed collapsed, no predicates, existence join forced.
        assertThat(SqlTupleReader.classifyAggShape(false, false, false, true, false, false, true))
                .isEqualTo("agg-mixed");
        // The variant without the forced join classifies identically.
        assertThat(SqlTupleReader.classifyAggShape(false, false, false, true, false, false, false))
                .isEqualTo("agg-mixed");
    }

    /** Everything else is unclassifiable: a multi-target neutral read (nothing collapsed), and a
     *  SINGLE-target mixed-collapsed read with no predicate/join signal. */
    @Test
    void residueIsUnclassified() {
        assertThat(SqlTupleReader.classifyAggShape(false, false, false, false, false, false, false))
                .isNull();
        assertThat(SqlTupleReader.classifyAggShape(true, false, false, true, false, false, false))
                .isNull();
    }
}
