/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara
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

import java.util.Arrays;
import java.util.List;

import org.eclipse.daanse.olap.api.query.NameSegment;
import org.eclipse.daanse.rolap.element.RolapLevel;

/**
 * Constraint which optimizes the search for a child by name. This is used
 * whenever the string representation of a member is parsed, e.g.
 * [Customers].[USA].[CA]. Restricts the result to
 * the member we are searching for.
 *
 * @author avix
 */
public class ChildByNameConstraint extends DefaultMemberChildrenConstraint {
    private final String[] childNames;
    private final Object cacheKey;

    /**
     * Creates a ChildByNameConstraint.
     *
     * @param childName Name of child
     */
    public ChildByNameConstraint(NameSegment childName) {
        this.childNames = new String[]{childName.getName()};
        this.cacheKey = List.of(ChildByNameConstraint.class, childName);
    }

    public ChildByNameConstraint(List<NameSegment> childNames) {
        this.childNames = new String[childNames.size()];
        int i = 0;
        for (NameSegment name : childNames) {
            this.childNames[i++] = name.getName();
        }
        this.cacheKey = List.of(
            ChildByNameConstraint.class, this.childNames);
    }

    @Override
    public int hashCode() {
        return getCacheKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ChildByNameConstraint childByNameConstraint
            && getCacheKey().equals(childByNameConstraint.getCacheKey());
    }

    @Override
	public String toString() {
        return new StringBuilder("ChildByNameConstraint(").append(Arrays.toString(childNames)).append(")").toString();
    }

    @Override
	public Object getCacheKey() {
        return cacheKey;
    }

    public List<String> getChildNames() {
        return List.of(childNames);
    }

    /**
     * The dimension-only contribution: the inherited parent-key restriction AND the by-name filter on the
     * child level being read — the same {@code memberKeyConstraint(parent)} +
     * {@link LevelConstraintGenerator#constrainLevelPredicate} predicates the recorded
     * {@code addMemberConstraint(parent)} + level-constraint path applies, so the mapper reproduces the
     * member-children query. No fact join (empty {@code joinTables}), so the caller builds it authoritatively
     * (result-verified, like {@link DefaultMemberChildrenConstraint}); only the WHERE parenthesization may
     * differ from the recorded form (a semantic no-op).
     * <p>
     * Returns {@link java.util.Optional#empty()} (→ the {@link org.eclipse.daanse.rolap.common.sql.QueryRecorder}
     * fallback) when the child level / name column cannot be expressed as a node (computed column), or the
     * inherited contribution carries a fact join.
     */
    @Override
    public java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> toContribution(
        org.eclipse.daanse.rolap.element.RolapCube baseCube,
        org.eclipse.daanse.rolap.common.aggmatcher.AggStar aggStar,
        org.eclipse.daanse.rolap.api.element.RolapMember parent)
    {
        java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> base =
            super.toContribution(baseCube, aggStar, parent);
        if (base.isEmpty() || !base.get().joinTables().isEmpty()) {
            return java.util.Optional.empty();
        }
        // The level whose members are being read (the level the name filter applies to).
        if (parent == null || !(parent.getLevel().getChildLevel() instanceof RolapLevel childLevel)) {
            return java.util.Optional.empty();
        }
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> namePred =
            LevelConstraintGenerator.constrainLevelPredicate(childLevel, baseCube, aggStar, childNames, true);
        if (namePred.isEmpty()) {
            return java.util.Optional.empty();
        }
        // Parent key (a parenthesized group, when present) AND the name filter, as the WHERE.
        org.eclipse.daanse.sql.statement.api.expression.Predicate where = base.get().where().isPresent()
            ? org.eclipse.daanse.sql.statement.api.Predicates.and(
                java.util.List.of(base.get().where().get(), namePred.get()))
            : namePred.get();
        return java.util.Optional.of(new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
            java.util.Optional.of(where), java.util.List.of()));
    }

}
