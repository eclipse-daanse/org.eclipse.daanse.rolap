/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.rolap.common.constraint;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.QueryTape;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;

/**
 * TupleConstaint which restricts the result of a tuple sqlQuery to a
 * set of parents.  All parents must belong to the same level.
 *
 * @author av
 * @since Nov 10, 2005
 */
public class DescendantsConstraint implements TupleConstraint {
    List<RolapMember> parentMembers;
    MemberChildrenConstraint mcc;

    /**
     * Creates a DescendantsConstraint.
     *
     * @param parentMembers list of parents all from the same level
     * @param mcc the constraint that would return the children for each single
     * parent
     */
    public DescendantsConstraint(
        List<RolapMember> parentMembers,
        MemberChildrenConstraint mcc)
    {
        this.parentMembers = parentMembers;
        this.mcc = mcc;
    }

    /**
     * Delegates to the wrapped member-children constraint's ops form on the SAME fork (a
     * descendants read is the parents' member-children read).
     */
    @Override
    public QueryTape addConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar)
    {
        return mcc.addMemberConstraintOps(dialect, fork, baseCube, aggStar, parentMembers);
    }

    /**
     * Delegates the level constraint to the wrapped member-children constraint — see
     * {@link #addConstraintOps}.
     */
    @Override
    public QueryTape addLevelConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level)
    {
        return mcc.addMemberLevelConstraintOps(dialect, fork, baseCube, aggStar, level);
    }

    @Override
	public MemberChildrenConstraint getMemberChildrenConstraint(
        RolapMember parent)
    {
        return mcc;
    }

    /**
     * {@inheritDoc}
     *
     * This implementation returns null, because descendants is not cached.
     */
    @Override
	public Object getCacheKey() {
        return null;
    }

    @Override
	public Evaluator getEvaluator() {
        return null;
    }

    @Override
    public boolean supportsAggTables() {
        return true;
    }

    /**
     * Delegates to the wrapped {@link MemberChildrenConstraint} for a single parent (the common
     * descend-one-member case): {@code addConstraint} is {@code mcc.addMemberConstraint(parents)}, so a
     * one-element parent list is exactly that constraint's children contribution. Multiple parents (an
     * {@code IN}/{@code OR} over parent keys) are not yet expressible — returns empty.
     */
    @Override
    public java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> toContribution(
        RolapCube baseCube, AggStar aggStar)
    {
        if (parentMembers.size() == 1) {
            return mcc.toContribution(baseCube, aggStar, parentMembers.get(0));
        }
        // Multiple parents, all from the same level (e.g. a role-restricted descendants set {[OR],[WA]} →
        // store_state IN ('OR','WA')): build the multi-member IN via memberConstraintContribution (a
        // single-level multi-member set → one IN). Guarded at the consumer, so a shape it can't express (calc
        // member, computed / snowflake multi-table key) falls back to the reference query.
        if (!parentMembers.isEmpty()) {
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> cp =
                MemberConstraintWriter.memberConstraintContribution(baseCube, parentMembers, false, false);
            if (cp.isEmpty()) {
                return java.util.Optional.empty();
            }
            // Compose the wrapped constraint's CONTEXT: the list-parents member constraint is
            // addContextConstraint + addMemberConstraint, so a context-bearing mcc (SqlContextConstraint)
            // must contribute its slicer columns + fact join FIRST, then the parent IN — otherwise the
            // slicer WHERE (e.g. promotion_name = '…') and its joins are silently dropped.
            // Mirrors RolapNativeSet's composition.
            if (mcc instanceof SqlContextConstraint contextConstraint) {
                java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> base =
                    contextConstraint.toContribution(baseCube, aggStar);
                if (base.isEmpty()) {
                    return java.util.Optional.empty();
                }
                org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = base.get();
                java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> wheres =
                    new java.util.ArrayList<>();
                c.where().ifPresent(wheres::add);
                wheres.add(cp.get().predicate());
                java.util.List<org.eclipse.daanse.rolap.common.star.RolapStar.Table> joinTables =
                    new java.util.ArrayList<>(c.joinTables());
                joinTables.add(cp.get().table());
                java.util.List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> ordered =
                    new java.util.ArrayList<>(c.orderedPredicates());
                ordered.add(cp.get());
                return java.util.Optional.of(new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
                    java.util.Optional.of(wheres.size() == 1 ? wheres.get(0)
                        : org.eclipse.daanse.sql.statement.api.Predicates.and(wheres)),
                    joinTables, ordered, c.memberKeyGroup()).withFactJoinRequired(c.factJoinRequired()));
            }
            return java.util.Optional.of(new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
                java.util.Optional.of(cp.get().predicate()),
                java.util.List.of(cp.get().table()),
                java.util.List.of(cp.get())));
        }
        return java.util.Optional.empty();
    }
}
