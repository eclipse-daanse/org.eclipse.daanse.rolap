/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2015-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.common.member;

import static java.util.Arrays.asList;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.constraint.DefaultMemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.constraint.ContextConstraintWriter;
import org.eclipse.daanse.rolap.common.constraint.MemberConstraintWriter;
import org.eclipse.daanse.rolap.common.nativize.RolapNativeSet;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.QueryTape;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;

/**
 * Constraint which excludes the members in the list received in constructor.
 *
 * @author Pedro Vale
 */
public class MemberExcludeConstraint implements TupleConstraint {
    private final List<RolapMember> excludes;
    private final Object cacheKey;
    private final RolapLevel level;
    private final RolapNativeSet.SetConstraint csc;
    private final Map<RolapLevel, List<RolapMember>> roles;

    /**
     * Creates a MemberExcludeConstraint.
     *
     */
    public MemberExcludeConstraint(
        List<RolapMember> excludes,
        RolapLevel level,
        RolapNativeSet.SetConstraint csc)
    {
        this.excludes = excludes;
        this.cacheKey = asList(MemberExcludeConstraint.class, excludes, csc);
        this.level = level;
        this.csc = csc;

        roles = (csc == null)
            ? Collections.<RolapLevel, List<RolapMember>>emptyMap()
            : ContextConstraintWriter.getRolesConstraints(csc.getEvaluator());
    }


    @Override
    public int hashCode() {
        return getCacheKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MemberExcludeConstraint
            && getCacheKey()
                .equals(((MemberExcludeConstraint) obj).getCacheKey());
    }

    /**
     * B3 ops-SPI form of {@code addLevelConstraint}: the same body against the fork directly (no
     * the retired query facade view) — byte-identical to the bridge.
     */
    @Override
    public QueryTape addLevelConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level)
    {
        if (level.equalsOlapElement(this.level)) {
            MemberConstraintWriter.addMemberConstraint(
                dialect, fork, baseCube, aggStar, excludes, true, false, true);
        }
        if (csc != null) {
            for (CrossJoinArg cja : csc.args) {
                if (cja.getLevel().equalsOlapElement(level)) {
                    cja.addConstraint(dialect, fork, baseCube, aggStar);
                }
            }
        }

        if (roles.containsKey(level)) {
            List<RolapMember> members = roles.get(level);
            MemberConstraintWriter.addMemberConstraint(
                dialect, fork, baseCube, aggStar, members, true, false, false);
        }
        return fork.ops();
    }

    @Override
	public String toString() {
        return new StringBuilder("MemberExcludeConstraint(").append(excludes).append(")").toString();
    }

    @Override
    public Object getCacheKey() {
        return cacheKey;
    }


    @Override
	public MemberChildrenConstraint getMemberChildrenConstraint(
        RolapMember parent)
    {
        return DefaultMemberChildrenConstraint.instance();
    }

    /**
     * B2 ops-SPI form: the exclude filter is applied per level ({@code addLevelConstraint}), so
     * this contributes nothing — the fork's empty tape, byte-identical to the bridge running the
     * empty legacy {@code addConstraint} against a view of the fork.
     */
    @Override
    public QueryTape addConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar)
    {
        return fork.ops();
    }

    /**
     * The generic-builder counterpart of the legacy {@code addLevelConstraint} for the common
     * pure-exclude shape ({@code csc == null}: no native cross-join, so no {@code CrossJoinArg} or role
     * sub-constraints, base star only). It reproduces the single
     * {@code MemberConstraintWriter.addMemberConstraint(excludes, restrictMemberTypes=true, crossJoin=false,
     * exclude=true)} that the legacy path emits for {@code this.level}: with {@code crossJoin=false} that
     * takes the single-value path and OR-combines the per-level exclude predicates, which the renderer
     * wraps in parens exactly like the legacy {@code "(" + … + ")"}. The excluded members live on the level
     * being read, so the restriction is dimension-only (no extra fact join → empty {@code joinTables}).
     *
     * <p>Returns {@link java.util.Optional#empty()} (legacy the retired query facade path) for an aggregate
     * star, the {@code csc != null} cross-join/role shapes, an empty exclude set (legacy emits {@code (1=1)},
     * not modelled here), or any column the pure predicate builder cannot express. The
     * {@code SqlBuildGuard.orReference} guard at the consumer ({@code SqlTupleReader.preferTupleBuilder}) makes
     * a divergent attempt safe — it byte-compares and falls back.
     */
    @Override
    public java.util.Optional<ConstraintContribution> toContribution(RolapCube baseCube, AggStar aggStar) {
        if (aggStar != null || csc != null || excludes.isEmpty()) {
            return java.util.Optional.empty();
        }
        // Mirror addMemberConstraint's first-unique-parent scan to derive fromLevel.
        RolapMember firstUniqueParent = excludes.get(0);
        for (; firstUniqueParent != null && !firstUniqueParent.getLevel().isUnique();
             firstUniqueParent = firstUniqueParent.getParentMember()) {
            // advance to the first unique parent level
        }
        RolapLevel firstUniqueParentLevel =
            (firstUniqueParent != null) ? firstUniqueParent.getLevel() : null;
        java.util.Optional<java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate>> parts =
            MemberConstraintWriter.generateSingleValueInPredicatePure(
                baseCube, excludes, firstUniqueParentLevel, true, true, true);
        if (parts.isEmpty()) {
            return java.util.Optional.empty();
        }
        // exclude => OR across the per-level parts; the Or's parens reproduce the legacy outer "(" + … + ")".
        org.eclipse.daanse.sql.statement.api.expression.Predicate where =
            org.eclipse.daanse.sql.statement.api.Predicates.or(parts.get());
        return java.util.Optional.of(
            new ConstraintContribution(java.util.Optional.of(where), java.util.List.of()));
    }

    @Override
    public Evaluator getEvaluator() {
        if (csc != null) {
            return csc.getEvaluator();
        } else {
            return null;
        }
    }

    @Override
    public boolean supportsAggTables() {
        return true;
    }
}
