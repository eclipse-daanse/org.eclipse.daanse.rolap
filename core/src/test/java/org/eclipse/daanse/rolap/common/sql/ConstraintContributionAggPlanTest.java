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
package org.eclipse.daanse.rolap.common.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link AggPlan} record composition and its threading through
 * {@link ConstraintContribution}: every pre-existing overload delegates with an empty plan,
 * {@code withAggPlan} composes without disturbing the other components, and an EMPTY predicate list
 * is a VALID unconstrained plan (present, but forcing no fact join) as opposed to the absent-plan
 * bail.
 */
class ConstraintContributionAggPlanTest {

    private static final Predicate PRED = Predicates.comparison(
            Expressions.column(TableAlias.of("agg"), "the_year"),
            ComparisonOperator.EQ, Expressions.literal(1997, Datatype.INTEGER));

    // ---- AggPlan record composition ----

    @Test
    void aggPlanRequiresAnAggStar() {
        assertThatThrownBy(() -> new AggPlan(null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyPredicateListIsAValidPlan() {
        AggStar aggStar = mock(AggStar.class);
        AggPlan plan = new AggPlan(aggStar, List.of());
        assertThat(plan.aggStar()).isSameAs(aggStar);
        assertThat(plan.orderedAggPredicates()).isEmpty();
    }

    @Test
    void predicateListIsDefensivelyCopied() {
        AggStar aggStar = mock(AggStar.class);
        java.util.List<AggPlan.AggColumnPredicate> mutable = new java.util.ArrayList<>();
        mutable.add(new AggPlan.AggColumnPredicate(mock(AggStar.Table.class), PRED));
        AggPlan plan = new AggPlan(aggStar, mutable);
        mutable.clear();
        assertThat(plan.orderedAggPredicates()).hasSize(1);
        assertThatThrownBy(() -> plan.orderedAggPredicates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullTableMeansKeyExpressionNoJoin() {
        // A key-expression predicate carries no agg table — consumers skip the join.
        AggPlan.AggColumnPredicate cp = new AggPlan.AggColumnPredicate(null, PRED);
        assertThat(cp.table()).isNull();
        assertThat(cp.predicate()).isSameAs(PRED);
        assertThatThrownBy(() -> new AggPlan.AggColumnPredicate(null, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- ConstraintContribution threading ----

    @Test
    void everyExistingOverloadDelegatesWithAnEmptyPlan() {
        assertThat(ConstraintContribution.EMPTY.aggPlan()).isEmpty();
        assertThat(new ConstraintContribution(Optional.empty(), List.of()).aggPlan()).isEmpty();
        assertThat(new ConstraintContribution(Optional.empty(), List.of(), List.of()).aggPlan()).isEmpty();
        assertThat(new ConstraintContribution(Optional.empty(), List.of(), List.of(),
                Optional.empty()).aggPlan()).isEmpty();
        assertThat(new ConstraintContribution(Optional.empty(), List.of(), List.of(),
                Optional.empty(), Optional.empty()).aggPlan()).isEmpty();
        assertThat(new ConstraintContribution(Optional.empty(), List.of(), List.of(),
                Optional.empty(), Optional.empty(), Optional.empty()).aggPlan()).isEmpty();
        assertThat(new ConstraintContribution(Optional.empty(), List.of(), List.of(),
                Optional.empty(), Optional.empty(), Optional.empty(), true).aggPlan()).isEmpty();
    }

    @Test
    void withAggPlanComposesAndPreservesTheOtherComponents() {
        ConstraintContribution base = new ConstraintContribution(Optional.of(PRED), List.of(),
                List.of(), Optional.empty(), Optional.empty(), Optional.empty(), true);
        AggPlan plan = new AggPlan(mock(AggStar.class), List.of());
        ConstraintContribution withPlan = base.withAggPlan(plan);
        assertThat(withPlan.aggPlan()).contains(plan);
        assertThat(withPlan.where()).isEqualTo(base.where());
        assertThat(withPlan.joinTables()).isEqualTo(base.joinTables());
        assertThat(withPlan.orderedPredicates()).isEqualTo(base.orderedPredicates());
        assertThat(withPlan.memberKeyGroup()).isEqualTo(base.memberKeyGroup());
        assertThat(withPlan.nativeOrder()).isEqualTo(base.nativeOrder());
        assertThat(withPlan.nativeHaving()).isEqualTo(base.nativeHaving());
        assertThat(withPlan.factJoinRequired()).isTrue();
    }

    @Test
    void withFactJoinRequiredPreservesTheAggPlan() {
        AggPlan plan = new AggPlan(mock(AggStar.class), List.of());
        ConstraintContribution c = ConstraintContribution.EMPTY.withAggPlan(plan);
        assertThat(c.withFactJoinRequired(true).aggPlan()).contains(plan);
        // the identity short-circuit keeps the plan too
        assertThat(c.withFactJoinRequired(false).aggPlan()).contains(plan);
    }

    @Test
    void aggPredicatesForceTheFactJoin() {
        AggPlan constrained = new AggPlan(mock(AggStar.class),
                List.of(new AggPlan.AggColumnPredicate(mock(AggStar.Table.class), PRED)));
        ConstraintContribution c = ConstraintContribution.EMPTY.withAggPlan(constrained);
        assertThat(c.requiresFactJoin()).isTrue();
        assertThat(c.producesPlainLevelMembers()).isFalse();
    }

    @Test
    void anUnconstrainedPlanForcesNoFactJoin() {
        // EMPTY predicates = VALID unconstrained plan: present, but adds nothing.
        AggPlan unconstrained = new AggPlan(mock(AggStar.class), List.of());
        ConstraintContribution c = ConstraintContribution.EMPTY.withAggPlan(unconstrained);
        assertThat(c.aggPlan()).isPresent();
        assertThat(c.requiresFactJoin()).isFalse();
        assertThat(c.producesPlainLevelMembers()).isTrue();
    }

    @Test
    void anAbsentPlanChangesNothing() {
        assertThat(ConstraintContribution.EMPTY.requiresFactJoin()).isFalse();
        assertThat(ConstraintContribution.EMPTY.producesPlainLevelMembers()).isTrue();
    }
}
