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
package org.eclipse.daanse.rolap.common.nativize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint;
import org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint.CalcLift;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution;
import org.junit.jupiter.api.Test;

/**
 * Verifies the per-family calc-gate mode ({@code SqlContextConstraint.executedCalcLift}) and its
 * threading through the mode-threaded {@code toContribution} chain: every constraint family returns
 * {@link CalcLift#LIFTED}, and the 2-arg {@code toContribution} threads the class's own
 * {@code executedCalcLift()} mode, so a family's mode changes with a single per-class override.
 *
 * <p>Needs the Mockito inline mock maker ({@code src/test/resources/mockito-extensions}): the
 * constraint classes are mocked without their (evaluator-heavy) constructors and the real dispatch
 * methods are invoked selectively.
 */
class CalcLiftLabelTest {

    /**
     * Every constraint family — plain {@code SqlContextConstraint}, {@code FilterConstraint},
     * {@code TopCountConstraint}, and the {@code NonEmptyCrossJoinConstraint}/NECJ family —
     * returns {@link CalcLift#LIFTED}.
     */
    @Test
    void executedCalcLiftSplitsPerConstraintFamily() {
        SqlContextConstraint plain = mock(SqlContextConstraint.class);
        when(plain.executedCalcLift()).thenCallRealMethod();
        assertThat(plain.executedCalcLift()).isEqualTo(CalcLift.LIFTED);

        RolapNativeCrossJoin.NonEmptyCrossJoinConstraint necj =
                mock(RolapNativeCrossJoin.NonEmptyCrossJoinConstraint.class);
        when(necj.executedCalcLift()).thenCallRealMethod();
        assertThat(necj.executedCalcLift()).isEqualTo(CalcLift.LIFTED);

        RolapNativeFilter.FilterConstraint filter =
                mock(RolapNativeFilter.FilterConstraint.class);
        when(filter.executedCalcLift()).thenCallRealMethod();
        assertThat(filter.executedCalcLift()).isEqualTo(CalcLift.LIFTED);

        RolapNativeTopCount.TopCountConstraint topCount =
                mock(RolapNativeTopCount.TopCountConstraint.class);
        when(topCount.executedCalcLift()).thenCallRealMethod();
        assertThat(topCount.executedCalcLift()).isEqualTo(CalcLift.LIFTED);
    }

    /**
     * The 2-arg dispatch threads the class's own {@code executedCalcLift()} mode through the virtual
     * chain, so a family's mode changes with one override. Checked with the real mode
     * ({@code LIFTED}) and with a stubbed {@code COMPOSED} class — the dispatch threads whatever the
     * override returns.
     */
    @Test
    void executedDispatchThreadsTheClassExecutedMode() {
        RolapNativeTopCount.TopCountConstraint tc =
                mock(RolapNativeTopCount.TopCountConstraint.class);
        when(tc.toContribution(null, null)).thenCallRealMethod();
        when(tc.executedCalcLift()).thenCallRealMethod(); // LIFTED
        Optional<ConstraintContribution> sentinel = Optional.of(ConstraintContribution.EMPTY);
        when(tc.toContribution(null, null, CalcLift.LIFTED)).thenReturn(sentinel);

        assertThat(tc.toContribution(null, null)).isSameAs(sentinel);

        // A class overriding to COMPOSED is threaded as COMPOSED.
        RolapNativeCrossJoin.NonEmptyCrossJoinConstraint necj =
                mock(RolapNativeCrossJoin.NonEmptyCrossJoinConstraint.class);
        when(necj.toContribution(null, null)).thenCallRealMethod();
        when(necj.executedCalcLift()).thenReturn(CalcLift.COMPOSED); // stubbed COMPOSED mode
        when(necj.toContribution(null, null, CalcLift.COMPOSED)).thenReturn(sentinel);

        assertThat(necj.toContribution(null, null)).isSameAs(sentinel);
    }
}
