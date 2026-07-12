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
     * The generic-builder counterpart of the recorder's {@code addLevelConstraint} for the common
     * pure-exclude shape ({@code csc == null}: no native cross-join, so no {@code CrossJoinArg} or role
     * sub-constraints, base star only). It reproduces the single
     * {@code MemberConstraintWriter.addMemberConstraint(excludes, restrictMemberTypes=true, crossJoin=false,
     * exclude=true)} that the recorder path emits for {@code this.level}: with {@code crossJoin=false} that
     * takes the single-value path and OR-combines the per-level exclude predicates, which the renderer
     * wraps in parens exactly like the recorder's {@code "(" + … + ")"}. The excluded members live on the level
     * being read, so the restriction is dimension-only (no extra fact join → empty {@code joinTables}).
     *
     * <p>The {@code csc != null} cross-join/role composition routes through
     * {@link #toContributionCscComposition} (the cross-join/role exclude composition, e.g. TopCount
     * completeWithNullValues re-reads); anything it cannot compose keeps the grep-stable
     * {@code exclude-csc-*} bails. Returns {@link java.util.Optional#empty()} for an aggregate
     * star, an empty exclude set (the recorder path emits {@code (1=1)}, not
     * modelled here), or any column the pure predicate builder cannot express — the consumer then
     * routes the read to the recorder, which carries the full restriction.
     */
    /** Diagnostic twin of {@code RolapNativeSet.SetConstraint}'s bail log: why THIS constraint's
     * contribution fell back. */
    private static final org.slf4j.Logger BAIL_LOG =
        org.slf4j.LoggerFactory.getLogger("daanse.sql.gen.bail");

    private org.eclipse.daanse.rolap.common.sql.ContributionResult bail(String reason) {
        BAIL_LOG.debug("MemberExcludeConstraint toContribution bail reason={}", reason);
        return org.eclipse.daanse.rolap.common.sql.ContributionResult.unsupported(reason);
    }

    @Override
    public org.eclipse.daanse.rolap.common.sql.ContributionResult toContribution(RolapCube baseCube, AggStar aggStar) {
        if (aggStar != null) {
            return bail("exclude-agg-star");
        }
        if (excludes.isEmpty()) {
            return bail("exclude-empty-set"); // the recorder path emits "(1 = 1)", not modelled here
        }
        if (csc != null) {
            // The csc composition is the live path where composable —
            // non-composable shapes keep the logged exclude-csc-* bails (→ recorder).
            return toContributionCscComposition(baseCube);
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
            return bail("exclude-not-expressible-as-predicates");
        }
        // exclude => OR across the per-level parts; the Or's parens reproduce the recorder's outer "(" + … + ")".
        org.eclipse.daanse.sql.statement.api.expression.Predicate where =
            org.eclipse.daanse.sql.statement.api.Predicates.or(parts.get());
        return org.eclipse.daanse.rolap.common.sql.ContributionResult.of(
            new ConstraintContribution(java.util.Optional.of(where), java.util.List.of()));
    }

    /**
     * The {@code csc != null} live path of {@link #toContribution}:
     * the composition as a single dimension-only contribution, mirroring the
     * recorder's per-level emission EXACTLY. The recorder read walks the target hierarchy's levels
     * root→{@code this.level} ({@code SqlTupleReader.addLevelMemberSql}, All levels skipped) and calls
     * {@link #addLevelConstraintOps} per level, whose within-level order is (1) the exclude constraint
     * (on {@code this.level}), (2) each {@code csc} cross-join arg on that level, (3) the role-access
     * members on that level (AccessControlTest#testBugMondrian_1201_*): ancestor-level role INs land BEFORE the exclude,
     * target-level args/roles after it. Conjunct twins:
     * <ul>
     * <li>exclude — {@link MemberConstraintWriter#memberConstraintContributionFactoredExclude} (the
     *     factored per-level NOT/null-reinclude OR);</li>
     * <li>csc args — the SetConstraint arg channel's proven
     *     {@link MemberConstraintWriter#memberConstraintContribution} per arg type (member list with
     *     its own restrict/exclude flags, descendants parent as a single-member constraint), incl.
     *     the empty-set {@code (1 = 0)}/{@code (1 = 1)} raw conjunct and the adds-nothing skip;</li>
     * <li>roles — {@link MemberConstraintWriter#memberConstraintContributionFactored} (the
     *     non-crossjoin {@code addMemberConstraint(members, true, false, false)} form).</li>
     * </ul>
     * All constrained columns are levels of the target hierarchy, so the contribution is
     * dimension-only: no join tables, no fact join — exactly the recorder's FROM (the exclude read
     * never joins the fact; {@link #addConstraintOps} is empty). Anything non-composable bails with a
     * grep-stable {@code exclude-csc-*} reason.
     */
    private org.eclipse.daanse.rolap.common.sql.ContributionResult toContributionCscComposition(RolapCube baseCube) {
        if (excludes.isEmpty()) {
            return bail("exclude-csc-empty-excludes"); // defensive — toContribution bails earlier
        }
        org.eclipse.daanse.rolap.element.RolapHierarchy hierarchy = level.getHierarchy();
        if (!level.isAll() && hierarchy instanceof org.eclipse.daanse.rolap.element.RolapCubeHierarchy ch
                && baseCube != null && !ch.getCube().equalsOlapElement(baseCube)) {
            hierarchy = baseCube.findBaseCubeHierarchy(hierarchy);
            if (hierarchy == null) {
                return bail("exclude-csc-no-base-hierarchy");
            }
        }
        @SuppressWarnings("unchecked")
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        int levelDepth = level.getDepth();
        List<org.eclipse.daanse.sql.statement.api.expression.Predicate> conjuncts =
            new java.util.ArrayList<>();
        for (int i = 0; i <= levelDepth && i < levels.size(); i++) {
            RolapLevel currLevel = levels.get(i);
            if (currLevel.isAll()) {
                continue;
            }
            // (1) the exclude itself, on its level (addLevelConstraintOps order).
            if (currLevel.equalsOlapElement(this.level)) {
                java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> cp =
                    MemberConstraintWriter.memberConstraintContributionFactoredExclude(
                        baseCube, excludes, true);
                if (cp.isEmpty()) {
                    return bail("exclude-csc-exclude-inexpressible");
                }
                conjuncts.add(cp.get().predicate());
            }
            // (2) the csc's cross-join args constrained on this level, in arg order.
            for (CrossJoinArg cja : csc.args) {
                if (cja.getLevel() == null || !cja.getLevel().equalsOlapElement(currLevel)) {
                    continue;
                }
                ArgConjunct arg = cscArgConjunct(baseCube, cja);
                if (arg == null) {
                    return bail("exclude-csc-arg-inexpressible");
                }
                if (arg.predicate() != null) {
                    conjuncts.add(arg.predicate());
                }
            }
            // (3) the role-access member restriction on this level.
            if (roles.containsKey(currLevel)) {
                List<RolapMember> roleMembers = roles.get(currLevel);
                java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> cp =
                    MemberConstraintWriter.memberConstraintContributionFactored(
                        baseCube, roleMembers, true, false);
                if (cp.isPresent()) {
                    conjuncts.add(cp.get().predicate());
                } else {
                    boolean addsNothing = roleMembers.stream().allMatch(RolapMember::isAll)
                        || roleMembers.stream().allMatch(m -> m.isCalculated() && !m.isParentChildLeaf());
                    if (!addsNothing) {
                        return bail("exclude-csc-role-inexpressible");
                    }
                }
            }
        }
        if (conjuncts.isEmpty()) {
            return bail("exclude-csc-no-conjuncts");
        }
        // Dimension-only: the mapper splits the top-level And into the recorder's WHERE conjuncts.
        return org.eclipse.daanse.rolap.common.sql.ContributionResult.of(new ConstraintContribution(
            java.util.Optional.of(org.eclipse.daanse.sql.statement.api.Predicates.and(conjuncts)),
            java.util.List.of()));
    }

    /** One csc arg's conjunct: {@code null} = bail; a null {@link #predicate()} = adds nothing (skip). */
    private record ArgConjunct(org.eclipse.daanse.sql.statement.api.expression.Predicate predicate) {
        static final ArgConjunct ADDS_NOTHING = new ArgConjunct(null);
    }

    /**
     * The pure twin of one {@code cja.addConstraint} call in {@link #addLevelConstraintOps} — the
     * SetConstraint arg channel's member-set extraction and {@code memberConstraintContribution}
     * translation (same flags per arg type, same empty-set raw conjunct, same null-member bail and
     * adds-nothing skip). Unlike {@code SetConstraint.addConstraintOps} there is NO calc-member
     * ({@code canApplyCrossJoinArgConstraint}) or base-cube gate here: {@code addLevelConstraintOps}
     * applies every level-matching arg unconditionally, so the twin does too.
     */
    private ArgConjunct cscArgConjunct(RolapCube baseCube, CrossJoinArg cja) {
        List<RolapMember> argMembers;
        boolean argRestrict;
        boolean argExclude;
        if (cja instanceof org.eclipse.daanse.rolap.common.sql.MemberListCrossJoinArg mlArg) {
            argMembers = mlArg.getMembers();
            argRestrict = mlArg.isRestrictMemberTypes();
            argExclude = mlArg.isExclude();
        } else if (cja instanceof org.eclipse.daanse.rolap.common.sql.DescendantsCrossJoinArg) {
            argMembers = cja.getMembers(); // [member], or null when the arg has no member
            argRestrict = true;
            argExclude = false;
        } else {
            return null; // arg type not modelled -> bail
        }
        if (argMembers == null) {
            return ArgConjunct.ADDS_NOTHING; // the recorder's addConstraint also adds nothing
        }
        if (argMembers.isEmpty()) {
            // addMemberConstraint's empty-set conjunct: always-false (always-true for exclude).
            return new ArgConjunct(org.eclipse.daanse.sql.statement.api.Predicates.raw(
                argExclude ? "(1 = 1)" : "(1 = 0)"));
        }
        if (argMembers.stream().anyMatch(RolapMember::isNull)) {
            return null; // which addMemberConstraint branch runs is not modelled (see SetConstraint)
        }
        java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> cp =
            MemberConstraintWriter.memberConstraintContribution(
                baseCube, argMembers, argRestrict, argExclude);
        if (cp.isEmpty()) {
            boolean addsNothing = argMembers.stream().allMatch(RolapMember::isAll)
                || argMembers.stream().allMatch(m -> m.isCalculated() && !m.isParentChildLeaf());
            return addsNothing ? ArgConjunct.ADDS_NOTHING : null;
        }
        return new ArgConjunct(cp.get().predicate());
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
