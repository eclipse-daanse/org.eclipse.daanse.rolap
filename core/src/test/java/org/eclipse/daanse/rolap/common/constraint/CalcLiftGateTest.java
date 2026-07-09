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

import java.util.List;

import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint.CalcLift;
import org.junit.jupiter.api.Test;

/**
 * Verifies the calculated-dimension-member gate {@link SqlContextConstraint#calcGateBailReason} and
 * the per-family mode {@link SqlContextConstraint#executedCalcLift}:
 * <ul>
 * <li>a supported calc member (its expression reduces to plain members) bails only in the
 *     set-composition mode ({@code COMPOSED}); the expanded mode ({@code LIFTED}) lets it fall
 *     through into the expansion;</li>
 * <li>an unsupported calc member bails in {@code COMPOSED}; {@code LIFTED} drops it on a non-strict
 *     constraint (the writer removes it) and keeps bailing on a strict one (the writer throws);</li>
 * <li>a calculated measure never bails (measures contribute no context column);</li>
 * <li>bail reasons use the {@code calc-<kind>-member-supported/-unsupported} prefixes.</li>
 * </ul>
 */
class CalcLiftGateTest {

    /** Expression reducible to a plain member — the SUPPORTED class. */
    private static Member supportedCalc() {
        Member m = mock(Member.class);
        when(m.isCalculated()).thenReturn(true);
        when(m.isMeasure()).thenReturn(false);
        when(m.getExpression()).thenReturn(mock(MemberExpression.class));
        when(m.getUniqueName()).thenReturn("[Time].[H1 1997]");
        return m;
    }

    /** No reducible expression — the UNSUPPORTED class (e.g. a [Scenario].[1] writeback member). */
    private static Member unsupportedCalc() {
        Member m = mock(Member.class);
        when(m.isCalculated()).thenReturn(true);
        when(m.isMeasure()).thenReturn(false);
        when(m.getExpression()).thenReturn(null);
        when(m.getUniqueName()).thenReturn("[Scenario].[1]");
        return m;
    }

    @Test
    void supportedCalcBailsOnlyInComposedMode() {
        List<Member> members = List.of(supportedCalc());

        assertThat(SqlContextConstraint.calcGateBailReason(members, "context",
                CalcLift.COMPOSED, false))
            .contains("calc-context-member-supported:[Time].[H1 1997]");
        assertThat(SqlContextConstraint.calcGateBailReason(members, "context",
                CalcLift.LIFTED, false)).isEmpty();
        // strict does not change the supported class in any mode
        assertThat(SqlContextConstraint.calcGateBailReason(members, "context",
                CalcLift.LIFTED, true)).isEmpty();
    }

    @Test
    void unsupportedCalcAlwaysBailsInComposedMode() {
        List<Member> members = List.of(unsupportedCalc());

        for (boolean strict : new boolean[] {false, true}) {
            assertThat(SqlContextConstraint.calcGateBailReason(members, "slicer",
                    CalcLift.COMPOSED, strict))
                .as("strict=%s", strict)
                .contains("calc-slicer-member-unsupported:[Scenario].[1]");
        }
    }

    /** The lifted mode drops the unsupported member on a non-strict constraint (the writer removes
     *  it); strict keeps bailing (the writer throws there). */
    @Test
    void liftedModeDropsUnsupportedOnlyWhenNonStrict() {
        List<Member> members = List.of(unsupportedCalc());

        assertThat(SqlContextConstraint.calcGateBailReason(members, "context",
                CalcLift.LIFTED, false)).isEmpty();
        assertThat(SqlContextConstraint.calcGateBailReason(members, "context",
                CalcLift.LIFTED, true))
            .contains("calc-context-member-unsupported:[Scenario].[1]");
    }

    /** Measures never contribute a context COLUMN, so a calculated measure is harmless in every
     *  mode (the same skip as SlicerAnalyzer). */
    @Test
    void calculatedMeasureNeverBails() {
        Member calcMeasure = mock(Member.class);
        when(calcMeasure.isCalculated()).thenReturn(true);
        when(calcMeasure.isMeasure()).thenReturn(true);

        for (CalcLift mode : CalcLift.values()) {
            assertThat(SqlContextConstraint.calcGateBailReason(List.of(calcMeasure), "context",
                    mode, true)).as("mode=%s", mode).isEmpty();
        }
    }

    /** A non-calculated member passes every mode; the first bailing member wins (iteration
     *  order). */
    @Test
    void plainMembersPassAndFirstBailWins() {
        Member plain = mock(Member.class);
        when(plain.isCalculated()).thenReturn(false);

        assertThat(SqlContextConstraint.calcGateBailReason(List.of(plain), "context",
                CalcLift.COMPOSED, true)).isEmpty();
        assertThat(SqlContextConstraint.calcGateBailReason(
                List.of(plain, unsupportedCalc(), supportedCalc()), "context",
                CalcLift.COMPOSED, false))
            .contains("calc-context-member-unsupported:[Scenario].[1]");
    }
}
