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

import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;

/**
 * A constraint producer's aggregate-table translation of its contribution: the {@link AggStar} the
 * read was routed to plus the context/value predicates already substituted onto agg columns, each
 * paired with the {@link AggStar.Table} it lives on so a fact-join consumer can interleave
 * {@code [agg table join, then that table's where(s)]} in add order (the assembler folds left-deep,
 * cycle edges go to {@code WHERE}).
 * <p>
 * This record deliberately carries PRODUCER knowledge only — which agg columns constrain the read
 * and where they live. Everything a MAPPER can derive from the target level + aggStar is NOT here:
 * the level-expression-to-agg-expression map ({@code targetExpMap}), the join-to-dimension
 * decision, the agg table chain and the {@code dimKey = aggColumn} edge are level-derived and
 * computed deterministically by {@code org.eclipse.daanse.rolap.common.sqlbuild.AggJoinPlanner} —
 * duplicating them here would let the two drift.
 * <p>
 * An EMPTY {@code orderedAggPredicates} list is a VALID plan, not a bail: it states "this read is
 * on {@code aggStar} and the constraint contributes no agg-column restriction" (an unconstrained
 * context). "Unconstrained" is distinct from "absent": absence of a plan (an empty
 * {@code Optional<AggPlan>} on {@link ConstraintContribution}) means the producer could NOT
 * translate the read for the agg star, which keeps the recorder; an empty plan means it could, and
 * the result is unconstrained.
 *
 * @param aggStar              the aggregate star the read is routed to (never null — a plan only
 *                             exists for a non-null aggStar routing)
 * @param orderedAggPredicates per-column agg-substituted {@code (table, predicate)} pairs in add
 *                             order (may be empty — a valid unconstrained plan)
 */
public record AggPlan(AggStar aggStar, List<AggColumnPredicate> orderedAggPredicates) {

    public AggPlan {
        Objects.requireNonNull(aggStar, "aggStar");
        orderedAggPredicates = List.copyOf(orderedAggPredicates);
    }

    /**
     * One agg-substituted value-restriction paired with the {@link AggStar.Table} it lives on, in
     * {@code addConstraint} order — the aggregate twin of
     * {@link ConstraintContribution.ColumnPredicate}.
     *
     * @param table     the agg table carrying the predicate's column; {@code null} for a
     *                  key-expression predicate (the column already lives in the plain level FROM,
     *                  so there is nothing to join — consumers must skip a null table)
     * @param predicate the agg-substituted restriction
     */
    public record AggColumnPredicate(AggStar.Table table, Predicate predicate) {
        public AggColumnPredicate {
            Objects.requireNonNull(predicate, "predicate");
        }
    }
}
