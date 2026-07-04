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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapAggregationManager;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.QueryTape;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.MultiCardinalityDefaultMember;
import org.eclipse.daanse.rolap.element.RolapCalculatedMember;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy.LimitedRollupMember;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.eclipse.daanse.rolap.element.RolapVirtualCube;

/**
 * limits the result of a Member SQL query to the current evaluation context.
 * All Members of the current context are joined against the fact table and only
 * those rows are returned, that have an entry in the fact table.
 *
 * For example, if you have two dimensions, "invoice" and "time", and the
 * current context (e.g. the slicer) contains a day from the "time" dimension,
 * then only the invoices of that day are found. Used to optimize NON EMPTY.
 *
 *  The {@link TupleConstraint} methods may silently ignore calculated
 * members (depends on the strict c'tor argument), so these may
 * return more members than the current context restricts to. The
 * MemberChildren methods will never accept calculated members as parents,
 * these will cause an exception.
 *
 * @author av
 * @since Nov 2, 2005
 */
public class SqlContextConstraint
    implements MemberChildrenConstraint, TupleConstraint
{
    private final List<Object> cacheKey;
    private RolapEvaluator evaluator;
    private boolean strict;

    /** Diagnostic: why a context contribution bailed to the recorder (reference) path. */
    private static final org.slf4j.Logger BAIL_LOG =
        org.slf4j.LoggerFactory.getLogger("daanse.sql.gen.bail");

    private java.util.Optional<ConstraintContribution> bail(String reason) {
        if (BAIL_LOG.isDebugEnabled()) {
            try {
                BAIL_LOG.debug("contextContribution bail reason={} cube={} members={} slicer={}",
                    reason, evaluator.getCube().getName(),
                    java.util.Arrays.toString(evaluator.getMembers()),
                    evaluator.getSlicerMembers());
            } catch (RuntimeException ignore) {
                BAIL_LOG.debug("contextContribution bail reason={}", reason);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * @param context evaluation context
     * @param strict false if more rows than requested may be returned
     * (i.e. the constraint is incomplete)
     *
     * @return false if this contstraint will not work for the current context
     */
    public static boolean isValidContext(Evaluator context, boolean strict) {
        return isValidContext(context, true, null, strict);
    }

    /**
     * @param context evaluation context
     * @param disallowVirtualCube if true, check for virtual cubes
     * @param levels levels being referenced in the current context
     * @param strict false if more rows than requested may be returned
     * (i.e. the constraint is incomplete)
     *
     * @return false if constraint will not work for current context
     */
    public static boolean isValidContext(
        Evaluator context,
        boolean disallowVirtualCube,
        Level [] levels,
        boolean strict)
    {
        if (context == null) {
            return false;
        }
        RolapCube cube = (RolapCube) context.getCube();
        if (disallowVirtualCube && cube instanceof RolapVirtualCube) {
            return false;
        }
        if (cube instanceof RolapVirtualCube) {
            Query query = context.getQuery();
            Set<Cube> baseCubes = new HashSet<>();
            List<Cube> baseCubeList = new ArrayList<>();
            if (!findVirtualCubeBaseCubes(query, baseCubes, baseCubeList)) {
                return false;
            }
            if (levels == null) {
                throw new IllegalArgumentException("levels should not be null");
            }
            query.setBaseCubes(baseCubeList);
        }

        if (MeasureConflictDetector.measuresConflictWithMembers(
                context.getQuery().getMeasuresMembers(), context.getMembers()))
        {
            // one or more dimension members referenced within measure calcs
            // conflict with the context members.  Not safe to apply
            // SqlContextConstraint.
            return false;
        }

        // may return more rows than requested?
        if (!strict) {
            return true;
        }

        // we can not handle all calc members in slicer. Calc measure and some
        // like aggregates are exceptions
        Member[] members = context.getMembers();
        for (int i = 1; i < members.length; i++) {
            if (members[i].isCalculated()
                && !CalculatedMemberExpander.isSupportedCalculatedMember(members[i]))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Locates base cubes related to the measures referenced in the query.
     *
     * @param query query referencing the virtual cube
     * @param baseCubes set of base cubes
     *
     * @return true if valid measures exist
     */
    private static boolean findVirtualCubeBaseCubes(
        Query query,
        Set<Cube> baseCubes,
        List<Cube> baseCubeList)
    {
        // Gather the unique set of level-to-column maps corresponding
        // to the underlying star/cube where the measure column
        // originates from.
        Set<Member> measureMembers = query.getMeasuresMembers();
        // if no measures are explicitly referenced, just use the default
        // measure
        if (measureMembers.isEmpty()) {
            Cube cube = query.getCube();
            Dimension dimension = cube.getDimensions().getFirst();
            query.addMeasuresMembers(
                dimension.getHierarchy().getDefaultMember());
        }
        for (Member member : query.getMeasuresMembers()) {
            if (member instanceof RolapStoredMeasure rolapStoredMeasure) {
                addMeasure(
                    rolapStoredMeasure, baseCubes, baseCubeList);
            } else if (member instanceof RolapCalculatedMember) {
                findMeasures(member.getExpression(), baseCubes, baseCubeList);
            }
        }

        return !baseCubes.isEmpty();
    }

    /**
     * Adds information regarding a stored measure to maps
     *
     * @param measure the stored measure
     * @param baseCubes set of base cubes
     */
    private static void addMeasure(
        RolapStoredMeasure measure,
        Set<Cube> baseCubes,
        List<Cube> baseCubeList)
    {
        RolapCube baseCube = measure.getCube();
        if (baseCubes.add(baseCube)) {
            baseCubeList.add(baseCube);
        }
    }

    /**
     * Extracts the stored measures referenced in an expression
     *
     * @param exp expression
     * @param baseCubes set of base cubes
     */
    private static void findMeasures(
        Expression exp,
        Set<Cube> baseCubes,
        List<Cube> baseCubeList)
    {
        if (exp instanceof MemberExpression memberExpr) {
            Member member = memberExpr.getMember();
            if (member instanceof RolapStoredMeasure rolapStoredMeasure) {
                addMeasure(
                    rolapStoredMeasure, baseCubes, baseCubeList);
            } else if (member instanceof RolapCalculatedMember) {
                findMeasures(member.getExpression(), baseCubes, baseCubeList);
            }
        } else if (exp instanceof ResolvedFunCallImpl funCall) {
            Expression [] args = funCall.getArgs();
            for (Expression arg : args) {
                findMeasures(arg, baseCubes, baseCubeList);
            }
        }
    }

    /**
    * Creates a SqlContextConstraint.
    *
    * @param evaluator Evaluator
    * @param strict defines the behaviour if the evaluator context
    * contains calculated members. If true, an exception is thrown,
    * otherwise calculated members are silently ignored. The
    * {@link org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint} member-constraint
    * methods will never accept a calculated member as parent.
    */
    public SqlContextConstraint(RolapEvaluator evaluator, boolean strict) {
        this.evaluator = evaluator.push();
        this.strict = strict;
        cacheKey = new ArrayList<>();
        cacheKey.add(getClass());
        cacheKey.add(strict);

        List<Member> members = new ArrayList<>();
        List<Member> expandedMembers = new ArrayList<>();

        members.addAll(
            Arrays.asList(
                CalculatedMemberExpander.expandMultiPositionSlicerMembers(
                    evaluator.getMembers(), evaluator)));

        // Now we'll need to expand the aggregated members
        expandedMembers.addAll(
            CalculatedMemberExpander.expandSupportedCalculatedMembers(
                members,
                evaluator).getMembers());
        cacheKey.add(expandedMembers);
        cacheKey.add(evaluator.getSlicerTuples());

        // Add restrictions imposed by Role based access filtering
        Map<Level, List<RolapMember>> roleMembers =
            ContextConstraintWriter.getRoleConstraintMembers(
                this.getEvaluator().getCatalogReader(),
                this.getEvaluator().getMembers());
        for (List<RolapMember> list : roleMembers.values()) {
            cacheKey.addAll(list);
        }

        // MONDRIAN-2597
        //For virtual cube we add all base cubes
        //associated with this virtual cube to the key
        if (evaluator.getCube() instanceof RolapVirtualCube) {
            cacheKey.addAll(evaluator.getCube().getBaseCubes());
        }
    }


    /**
     * Restricts the parent's children to the current context: the context constraint is applied
     * with {@code parent} set as context, then the parent member constraint — both recorded on
     * the fork.
     */
    @Override
    public QueryTape addMemberConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar,
        RolapMember parent)
    {
        if (parent.isCalculated()) {
            throw Util.newInternal("cannot restrict SQL to calculated member");
        }
        final int savepoint = evaluator.savepoint();
        try {
            evaluator.setContext(parent);
            ContextConstraintWriter.addContextConstraint(
                dialect, fork, aggStar, evaluator, baseCube, strict);
        } finally {
            evaluator.restore(savepoint);
        }

         MemberConstraintWriter.addMemberConstraint(
                dialect, fork, baseCube, aggStar, parent, true);
        return fork.ops();
    }


    /**
     * Restricts the children of {@code parents} to the current context: the context constraint,
     * then the member-set constraint — both recorded on the fork.
     */
    @Override
    public QueryTape addMemberConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar,
        List<RolapMember> parents)
    {
        ContextConstraintWriter.addContextConstraint(
            dialect, fork, aggStar, evaluator, baseCube, strict);
        boolean exclude = false;
        MemberConstraintWriter.addMemberConstraint(
            dialect, fork, baseCube, aggStar, parents, true, false, exclude);
        return fork.ops();
    }


    /**
     * Applies the current context constraint to the fork. Subclasses that add their own
     * restrictions ({@code RolapNativeSet.SetConstraint}) override {@code addConstraintOps}
     * rather than relying on this base form.
     */
    @Override
    public QueryTape addConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar)
    {
        ContextConstraintWriter.addContextConstraint(
            dialect, fork, aggStar, evaluator, baseCube, strict);
        return fork.ops();
    }

    /**
     * The generic-builder counterpart of {@link #addMemberConstraintOps(Dialect,
     * QueryRecorder.Fork, RolapCube, AggStar, RolapMember)}: the parent's children restricted to
     * the current context, as a
     * {@link ConstraintContribution} (builder {@code WHERE} predicate + the star tables to join).
     * <p>
     * Mirrors the recorded sequence — {@code addContextConstraint} (one {@code col = value} per
     * single-valued context column) then the parent-key restriction — and lists the columns' tables
     * so the mapper joins them to the fact. Returns {@link java.util.Optional#empty()} (→ the
     * recorder path) for everything outside that simple scope: an aggregate table, a virtual
     * cube, calculated parent, tuple / disjoint / multi-level slicers, role-based access, or any
     * multi-valued (IN-list) slicer column. The byte-equal guard at the call site keeps a divergent
     * translation honest.
     */
    @Override
    public java.util.Optional<ConstraintContribution> toContribution(
        RolapCube baseCube,
        AggStar aggStar,
        RolapMember parent)
    {
        if (parent.isCalculated()) {
            return java.util.Optional.empty();
        }
        return contextContribution(baseCube, aggStar, java.util.Optional.of(parent));
    }

    /**
     * The generic-builder counterpart of {@link #addConstraint} (level members): the current context
     * as a {@link ConstraintContribution}, no parent-key restriction.
     */
    @Override
    public java.util.Optional<ConstraintContribution> toContribution(
        RolapCube baseCube,
        AggStar aggStar)
    {
        return contextContribution(baseCube, aggStar, java.util.Optional.empty());
    }

    /**
     * Shared context translation for both SPI variants: one {@code col = value} per single-valued
     * context column (+ the parent key when {@code parent} is present), with the columns' tables to
     * join. {@link java.util.Optional#empty()} for anything outside the simple scope.
     */
    private java.util.Optional<ConstraintContribution> contextContribution(
        RolapCube baseCube,
        AggStar aggStar,
        java.util.Optional<RolapMember> parent)
    {
        if (aggStar != null) {
            return bail("aggStar");
        }
        RolapCube cube = (baseCube != null) ? baseCube : (RolapCube) evaluator.getCube();
        if (cube instanceof RolapVirtualCube) {
            return bail("virtual-cube");
        }
        // Tuple / disjoint slicers and role-based access are out of the simple scope.
        org.eclipse.daanse.olap.api.calc.tuple.TupleList slicerTuples =
            evaluator.getOptimizedSlicerTuples(cube);
        // A slicer tuple list — single OR multi-level — is reproduced below as the
        // addContextConstraintTuples Or(And(...)) predicate, which StarPredicateTranslator collapses to a
        // single-column IN or a multi-column tuple-IN (Predicates.inTuple). A shape the dialect can't
        // tuple-IN, or a genuinely divergent optimization (constant-level extraction), falls back via
        // the guard.
        // Role-based access is reproduced as a contribution after the context column loop below
        // (context columns first, then addRoleAccessConstraints — addContextConstraint's order).
        // Calculated context OR slicer members (e.g. *FILTER_MEMBER calc members from
        // optimizer-generated MDX) — the calc-member expansion applies constraints this translation
        // does not capture, producing a wrong (over/under-constrained) query. Out of scope: bail to
        // the recorder path.
        // (members[0] is the measure; a calc measure is fine. Slicer calc members live in
        // getSlicerMembers(), NOT getMembers().)
        Member[] ctxMembers = evaluator.getMembers();
        for (int i = 1; i < ctxMembers.length; i++) {
            if (ctxMembers[i].isCalculated()) {
                return bail("calc-context-member:" + ctxMembers[i].getUniqueName());
            }
        }
        for (Member sm : evaluator.getSlicerMembers()) {
            if (sm.isCalculated()) {
                return bail("calc-slicer-member:" + sm.getUniqueName());
            }
        }

        try {
            // Throwaway push() copy: never mutate this constraint's (possibly cached, reused) evaluator —
            // a mutation would corrupt member/level enumeration for subsequent queries. Copy discarded;
            // no savepoint/restore.
            RolapEvaluator ev = evaluator.push();
            parent.ifPresent(ev::setContext);
            TupleConstraintStruct expanded =
                ContextConstraintWriter.makeContextConstraintSet(ev, strict, false);
            if (!expanded.getDisjoinedTupleLists().isEmpty()) {
                return bail("disjoined-tuples");
            }
            Member[] members = expanded.getMembersArray();
            if (members.length > 0 && !(members[0] instanceof RolapStoredMeasure)) {
                RolapStoredMeasure measure = (RolapStoredMeasure) cube.getMeasures().getFirst();
                List<Member> memberList = new ArrayList<>(Arrays.asList(members));
                memberList.add(0, measure);
                members = memberList.toArray(new Member[0]);
            }
            CellRequest request = RolapAggregationManager.makeRequest(members);
            if (request == null) {
                return bail("request-null");
            }
            RolapStar.Column[] columns = request.getConstrainedColumns();
            Object[] values = request.getSingleValues();
            Map<org.eclipse.daanse.olap.api.sql.SqlExpression, Set<RolapMember>> slicerMap =
                SlicerAnalyzer.getSlicerMemberMap(ev);
            // Slicer routing — mirror ContextConstraintWriter.addContextConstraint: use the
            // tuple-IN Or(And(...)) predicate ONLY when the tuple list is disjoint OR multi-level. A plain
            // non-disjoint single-level slicer ({1997}×{Q1,Q2}) is FACTORED per column below
            // (the_year = 1997 AND quarter IN ('Q1','Q2')) — two separate conjuncts, not one tuple-IN.
            boolean useSlicerTuplePred = slicerTuples != null && !slicerTuples.isEmpty()
                && (SlicerAnalyzer.isDisjointTuple(slicerTuples) || evaluator.isMultiLevelSlicerTuple());
            org.eclipse.daanse.rolap.common.star.StarPredicate slicerTuplePred =
                useSlicerTuplePred ? ContextConstraintWriter.getSlicerTuplesPredicatePure(slicerTuples, cube) : null;
            org.eclipse.daanse.olap.key.BitKey slicerBitKey =
                (slicerTuplePred != null) ? slicerTuplePred.getConstrainedColumnBitKey() : null;

            List<org.eclipse.daanse.sql.statement.api.expression.Predicate> predicates = new ArrayList<>();
            List<RolapStar.Table> joinTables = new ArrayList<>();
            List<ConstraintContribution.ColumnPredicate> ordered = new ArrayList<>();
            for (int i = 0; i < columns.length; i++) {
                RolapStar.Column column = columns[i];
                if (slicerBitKey != null && slicerBitKey.get(column.getBitPosition())) {
                    // constrained by the slicer tuple predicate (added after this loop) — skip here.
                    continue;
                }
                if (slicerMap.containsKey(column.getExpression())) {
                    java.util.List<RolapMember> colSlicerMembers =
                        ContextConstraintWriter.getNonAllMembers(slicerMap.get(column.getExpression()));
                    if (colSlicerMembers.size() > 1) {
                        // Multi-valued slicer column => the per-column factored IN, via the pure twin
                        // (quarter IN ('Q1','Q2')). Only reached when the tuple-IN path is NOT used (gated
                        // above); each part is added as its own WHERE conjunct. A shape the pure twin
                        // can't express (computed/null key) bails to the recorder path.
                        java.util.Optional<java.util.List<
                            org.eclipse.daanse.sql.statement.api.expression.Predicate>> inParts =
                            MemberConstraintWriter.generateSingleValueInPredicatePure(
                                cube, colSlicerMembers, (RolapLevel) colSlicerMembers.get(0).getLevel(),
                                false, false, false);
                        if (inParts.isEmpty()) {
                            return bail("multi-value-slicer-pure:" + column.getExpression());
                        }
                        for (org.eclipse.daanse.sql.statement.api.expression.Predicate p : inParts.get()) {
                            predicates.add(p);
                            joinTables.add(column.getTable());
                            ordered.add(new ConstraintContribution.ColumnPredicate(column.getTable(), p));
                        }
                        continue;
                    }
                }
                org.eclipse.daanse.sql.statement.api.expression.SqlExpression col =
                    JoinPlanner.expressionFor(column);
                Object value = values[i];
                org.eclipse.daanse.sql.statement.api.expression.Predicate p =
                    // The null sentinel Util.sqlNullValue is NOT Java null (its toString() is "#null", which
                    // must render as IS NULL, never leak as a literal) — matching StarPredicateTranslator.
                    (value == null || value == Util.sqlNullValue)
                        ? org.eclipse.daanse.sql.statement.api.Predicates.isNull(col)
                        : org.eclipse.daanse.sql.statement.api.Predicates.comparison(
                            col,
                            org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                            org.eclipse.daanse.sql.statement.api.Expressions.literal(value, column.getDatatype()));
                predicates.add(p);
                joinTables.add(column.getTable());
                ordered.add(new ConstraintContribution.ColumnPredicate(column.getTable(), p));
            }
            // Slicer tuple list (after the non-slicer context columns, matching addContextConstraintTuples):
            // one dialect-free Or(And(...)) predicate via StarPredicateTranslator, with the slicer columns'
            // tables joined. The whole predicate attaches to its first column's table for the mapper's
            // [join, where] interleaving (single-dimension slicers — the common case — are one table).
            if (slicerTuplePred != null) {
                org.eclipse.daanse.sql.statement.api.expression.Predicate slicerWhere =
                    org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toPredicate(slicerTuplePred);
                java.util.List<RolapStar.Column> slicerCols = slicerTuplePred.getConstrainedColumnList();
                if (slicerCols.isEmpty()) {
                    return bail("slicer-tuple-no-columns");
                }
                predicates.add(slicerWhere);
                java.util.LinkedHashSet<RolapStar.Table> slicerTables = new java.util.LinkedHashSet<>();
                for (RolapStar.Column sc : slicerCols) {
                    slicerTables.add(sc.getTable());
                }
                joinTables.addAll(slicerTables);
                ordered.add(new ConstraintContribution.ColumnPredicate(slicerCols.get(0).getTable(), slicerWhere));
            }
            // Role-based access (addRoleAccessConstraints, applied after the context columns):
            // per (level, accessible members) an IN predicate (per level, parent levels included) plus the
            // level's table joined to the fact. Reproduce each part as a WHERE conjunct + an ordered
            // (table, predicate) pair so the mapper joins the level table once and emits the parts in order.
            java.util.Map<Level, List<RolapMember>> roleMembers =
                ContextConstraintWriter.getRoleConstraintMembers(
                    getEvaluator().getCatalogReader(), getEvaluator().getMembers());
            for (java.util.Map.Entry<Level, List<RolapMember>> entry : roleMembers.entrySet()) {
                if (!(entry.getKey() instanceof RolapCubeLevel roleLevel)) {
                    return java.util.Optional.empty();
                }
                java.util.Optional<List<org.eclipse.daanse.sql.statement.api.expression.Predicate>> roleParts =
                    MemberConstraintWriter.generateSingleValueInPredicatePure(
                        cube, entry.getValue(), roleLevel, strict, false, true);
                RolapStar.Column roleKey = roleLevel.getBaseStarKeyColumn(cube);
                if (roleParts.isEmpty() || roleKey == null) {
                    return java.util.Optional.empty();
                }
                RolapStar.Table roleTable = roleKey.getTable();
                for (org.eclipse.daanse.sql.statement.api.expression.Predicate part : roleParts.get()) {
                    predicates.add(part);
                    joinTables.add(roleTable);
                    ordered.add(new ConstraintContribution.ColumnPredicate(roleTable, part));
                }
            }

            // children-of-parent restriction (a nested AND so it renders as one parenthesised group).
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> parentPredicate =
                parent.flatMap(p -> JoinPlanner.memberKeyConstraint(p));
            parentPredicate.ifPresent(predicates::add);

            if (predicates.isEmpty()) {
                if (columns.length == 0 && parent.isEmpty()) {
                    // Genuinely unconstrained context (all-[All] members → no constrained columns) — return
                    // an EMPTY (present, unrestricted) contribution rather than bailing, so a wrapping
                    // SetConstraint/native constraint still contributes its own args. Otherwise its
                    // base.isEmpty() check makes the whole set fall back, dropping the set's WHERE (e.g. a
                    // {[Time].[Q1],[Time].[Q2]} × level crossjoin). A pure level read with this shape is
                    // reproduced unrestricted by the mapper anyway. Carry isJoinRequired() (a non-empty set,
                    // e.g. a crossjoin, still needs the target level's existence join to the fact) — the
                    // shared EMPTY constant is false, so build a fresh contribution for it.
                    return java.util.Optional.of(
                        ConstraintContribution.EMPTY.withFactJoinRequired(isJoinRequired()));
                }
                // A non-empty constrained-column set that yielded no predicate = "could not express it", NOT
                // "no restriction"; bail so the real context is not dropped.
                return bail("empty-predicates");
            }
            org.eclipse.daanse.sql.statement.api.expression.Predicate where =
                (predicates.size() == 1)
                    ? predicates.get(0)
                    : org.eclipse.daanse.sql.statement.api.Predicates.and(predicates);
            // Per-column ordering lets the mapper interleave [join, that table's where(s)] exactly like
            // the recorded path. The parent-key restriction (member-children) is redundant in the
            // fact-join case: it is added both via addContextConstraint (after setContext(parent), so
            // the parent's level key is already a constrained context column) and via
            // addMemberConstraint, and the WHERE dedup collapses the second add. So the context columns
            // in `ordered` already carry the parent key; the member mapper appends only the child level
            // -> fact join (addLevelConstraint) after them. Keep `ordered` even when a parent is present.
            List<ConstraintContribution.ColumnPredicate> orderedToUse = ordered;
            // The parent-key group (addMemberConstraint) is rendered parenthesized, so it is NOT a
            // duplicate of the unparenthesised context columns and survives as a trailing WHERE conjunct.
            // The member mapper appends it after the interleaved context (tuple path: empty parent).
            return java.util.Optional.of(
                new ConstraintContribution(java.util.Optional.of(where), joinTables, orderedToUse,
                    parentPredicate).withFactJoinRequired(isJoinRequired()));
        } catch (RuntimeException e) {
            return bail("exception:" + e.getClass().getSimpleName());
        }
    }

    /**
     * Returns whether a join with the fact table is required. A join is
     * required if the context contains members from dimensions other than
     * level. If we are interested in the members of a level or a members
     * children then it does not make sense to join only one dimension (the one
     * that contains the requested members) with the fact table for NON EMPTY
     * optimization.
     */
    public boolean isJoinRequired() {
        Member[] members = evaluator.getMembers();
        // members[0] is the Measure, so loop starts at 1
        for (int i = 1; i < members.length; i++) {
            if (!members[i].isAll()
                || members[i] instanceof LimitedRollupMember
                || members[i] instanceof MultiCardinalityDefaultMember)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Joins the level's table to the fact table when the context requires a fact join
     * ({@link org.eclipse.daanse.rolap.common.sql.TupleConstraint} form); a no-op otherwise
     * (see {@link #isJoinRequired}).
     */
    @Override
    public QueryTape addLevelConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level)
    {
        if (!isJoinRequired()) {
            return fork.ops();
        }
        ContextConstraintWriter.joinLevelTableToFactTable(
            fork, baseCube, aggStar, (RolapCubeLevel)level);
        return fork.ops();
    }

    /**
     * As {@link #addLevelConstraintOps} for the
     * {@link org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint} SPI.
     */
    @Override
    public QueryTape addMemberLevelConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level)
    {
        if (!isJoinRequired()) {
            return fork.ops();
        }
        ContextConstraintWriter.joinLevelTableToFactTable(
            fork, baseCube, aggStar, (RolapCubeLevel)level);
        return fork.ops();
    }

    @Override
	public MemberChildrenConstraint getMemberChildrenConstraint(
        RolapMember parent)
    {
        return this;
    }

    @Override
	public Object getCacheKey() {
        return cacheKey;
    }

    @Override
	public RolapEvaluator getEvaluator() {
        return evaluator;
    }

    @Override
    public boolean supportsAggTables() {
        return true;
    }
}

// End SqlContextConstraint.java

