/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.common.nativize;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.evaluator.NativeEvaluator;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.api.query.component.NumericLiteral;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;

/**
 * Computes a TopCount in SQL.
 *
 * @author av
 * @since Nov 21, 2005
 */
public class RolapNativeTopCount extends RolapNativeSet {

    public RolapNativeTopCount(boolean enableNativeTopCount) {
        super.setEnabled(
            enableNativeTopCount);
    }

    public static class TopCountConstraint extends SetConstraint {
        Expression orderByExpr;
        SortingDirection sortingDirection;
        Integer topCount;

        public TopCountConstraint(
            int count,
            CrossJoinArg[] args, RolapEvaluator evaluator,
            Expression orderByExpr, SortingDirection sortingDirection)
        {
            super(args, evaluator, true);
            this.orderByExpr = orderByExpr;
            this.sortingDirection = sortingDirection;
            this.topCount = count;
        }

        /**
         * If the orderByExpr is not present than we're dealing with
         * the 2 arg form of TC.  The 2 arg form cannot be evaluated
         * with a join to the fact.  Because of this, it's only valid
         * to apply a native constraint for the 2 arg form if a single CJ
         * arg is in place.  Otherwise we'd need to join the dims together
         * via the fact table, which could eliminate tuples that should
         * be returned.
         */
        protected boolean isValid() {
            if (orderByExpr == null) {
                return args.length == 1
                    && canApplyCrossJoinArgConstraint(args[0]);
            }
            return true;
        }

        /**
         * {@inheritDoc}
         *
         * TopCount needs to join the fact table if a top count expression
         * is present (i.e. orderByExpr).  If not present than the results of
         * TC should be the natural ordering of the set in the first argument,
         * which cannot use a join to the fact table without potentially
         * eliminating empty tuples.
         */
        @Override
		public boolean isJoinRequired() {
            return orderByExpr != null;
        }

        @Override
        public boolean supportsAggTables() {
            // We can only safely use agg tables if we can limit
            // results to those with data (i.e. if we would be
            // joining to a fact table).
            return isJoinRequired();
        }

        /**
         * The TopCount composition runs the expanded calc gate ({@link CalcLift#LIFTED}): a
         * supported calculated member in context or slicer is expanded in place rather than
         * forcing non-native evaluation. The {@code topcount-base-context-empty} /
         * {@code topcount-candidate-base-context-empty} bails still apply to non-calc base shapes.
         */
        @Override
        public CalcLift executedCalcLift() {
            return CalcLift.LIFTED;
        }

        /**
         * Builds the TopCount's constraint contribution (context + NativeOrder), reached from the
         * base 2-arg {@code toContribution} dispatch with {@link #executedCalcLift()}. For the
         * join-required (orderByExpr present) form: the inherited {@link SetConstraint}
         * context/cross-join contribution plus a
         * {@link org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder} carrying
         * the measure projection + sort (the measure node built as {@code AbstractQuerySpec.addMeasure}
         * does — column node wrapped by the aggregator); a non-stored-measure order expression is
         * compiled through the shared {@code generateTopCountOrderByNode} channel. For the
         * natural-order (2-arg) form: {@link #naturalOrderContribution} — ONLY args[0]'s member
         * restriction, no context, no fact join. Returns {@link java.util.Optional#empty()} for a
         * composite/non-node aggregator, an unmodelled natural arg shape, or when the inherited
         * context cannot be expressed.
         */
        @Override
        protected org.eclipse.daanse.rolap.common.sql.ContributionResult toContribution(
            RolapCube baseCube, AggStar aggStar, CalcLift lift) {
            return contribution(baseCube, aggStar, true, lift);
        }

        private org.eclipse.daanse.rolap.common.sql.ContributionResult contribution(
            RolapCube baseCube, AggStar aggStar, boolean liftNonStoredOrder, CalcLift lift) {
            if (aggStar != null && orderByExpr != null) {
                // Agg routing: compile the order node through the agg-aware
                // generateTopCountOrderByNode channel — the stored-measure direct build below
                // projects the BASE star measure column, which is wrong on an agg read (the agg
                // fact carries the substituted measure column).
                return nonStoredOrderContribution(baseCube, aggStar, lift);
            }
            if (!(orderByExpr instanceof MemberExpression me)
                || !(me.getMember() instanceof RolapStoredMeasure storedMeasure)
                || !(storedMeasure.getStarMeasure()
                        instanceof org.eclipse.daanse.rolap.common.star.RolapStar.Measure starMeasure)) {
                if (orderByExpr != null) {
                    if (!liftNonStoredOrder) {
                        // A measure order the builder cannot project directly (calc-measure order,
                        // non-star measure): no contribution without a NativeOrder is valid here.
                        // The lifted route (liftNonStoredOrder=true) instead compiles it via
                        // nonStoredOrderContribution.
                        throw dead("topcount-order-not-stored-measure");
                    }
                    return nonStoredOrderContribution(baseCube, aggStar, lift);
                }
                return naturalOrderContribution(baseCube);
            }
            org.eclipse.daanse.rolap.common.sql.ContributionResult base =
                super.toContribution(baseCube, aggStar, lift);
            if (!base.isSupported()) {
                return bail("topcount-base-context-empty");
            }
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression innerNode =
                starMeasure.getExpression() == null
                    ? org.eclipse.daanse.sql.statement.api.Expressions.star()
                    : (starMeasure.getExpression() instanceof org.eclipse.daanse.rolap.element.RolapColumn
                        ? org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(
                            starMeasure.getExpression())
                        : org.eclipse.daanse.sql.statement.api.Expressions.rawVariant(
                            org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.sqlVariants(
                                starMeasure.getExpression())));
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression measureNode =
                org.eclipse.daanse.rolap.aggregator.SqlNodeAggregator.toNodeOrNull(
                    starMeasure.getAggregator(), innerNode);
            if (measureNode == null) {
                throw dead("topcount-non-node-aggregator");
            }
            // withNativeOrder carries everything else — incl. factJoinRequired (without it the
            // mapper's same-dimension gate skips the fact join the measure ORDER BY references) and
            // the agg-join channel.
            return org.eclipse.daanse.rolap.common.sql.ContributionResult.of(
                base.contribution().withNativeOrder(
                    new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder(
                        measureNode, sortingDirection, deduceNullability(orderByExpr))));
        }

        /**
         * The builder for a NON-stored-measure order expression: the inherited
         * {@link SetConstraint} context plus a
         * {@link org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder} whose node
         * is the order expression compiled through {@link RolapNativeSql#generateTopCountOrderByNode}.
         * Compiled on a {@link NativeSqlContext#scratch} seam (the node is self-contained; FROM side
         * effects are owned by the contribution's joinTables / factJoinRequired), mirroring the
         * {@code RolapNativeFilter} contribution. Returns {@link java.util.Optional#empty()} when the
         * inherited context is not expressible or the order compiler fails / yields no node.
         */
        private org.eclipse.daanse.rolap.common.sql.ContributionResult
            nonStoredOrderContribution(RolapCube baseCube, AggStar aggStar, CalcLift lift) {
            org.eclipse.daanse.rolap.common.sql.ContributionResult base =
                super.toContribution(baseCube, aggStar, lift);
            if (!base.isSupported()) {
                return bail("topcount-candidate-base-context-empty");
            }
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression orderByNode;
            try {
                RolapNativeSql sql = new RolapNativeSql(
                    NativeSqlContext.scratch(
                        org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities.of(
                            getEvaluator().getCatalogReader().getContext().getDialect())),
                    aggStar, getEvaluator(), null);
                orderByNode = sql.generateTopCountOrderByNode(orderByExpr);
            } catch (RuntimeException e) {
                return bail("topcount-candidate-order-compile-error");
            }
            if (orderByNode == null) {
                throw dead("topcount-candidate-order-null");
            }
            // withNativeOrder carries everything else — incl. factJoinRequired (without it the
            // mapper's same-dimension gate skips the fact join the order-by node references) and the
            // agg-join channel of an agg-routed base contribution (the order node above was compiled
            // agg-substituted).
            return org.eclipse.daanse.rolap.common.sql.ContributionResult.of(
                base.contribution().withNativeOrder(
                    new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder(
                        orderByNode, sortingDirection, deduceNullability(orderByExpr))));
        }

        /**
         * The order expression compiled against the AGG columns as a
         * {@link org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder}, so an
         * aggStar collapsed level-members read carries the native measure ORDER. Uses the same
         * {@link RolapNativeSql#generateTopCountOrderByNode} channel + {@code aggStar} as
         * {@link #nonStoredOrderContribution}, for the collapsed level-members consumers that call
         * it directly. The 2-arg (no orderByExpr) form carries no measure order
         * ({@link java.util.Optional#empty()}).
         */
        @Override
        public java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder>
            levelMembersAggOrder( AggStar aggStar ) {
          if ( orderByExpr == null ) {
            return java.util.Optional.empty();
          }
          try {
            RolapNativeSql sql = new RolapNativeSql(
                NativeSqlContext.scratch( org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities.of(
                    getEvaluator().getCatalogReader().getContext().getDialect() ) ),
                aggStar, getEvaluator(), null );
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
                sql.generateTopCountOrderByNode( orderByExpr );
            if ( node == null ) {
              return java.util.Optional.empty();
            }
            return java.util.Optional.of(
                new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder(
                    node, sortingDirection, deduceNullability( orderByExpr ) ) );
          } catch ( RuntimeException e ) {
            return java.util.Optional.empty();
          }
        }

        /**
         * The natural-order (2-arg, no orderByExpr) contribution, which applies ONLY args[0]'s
         * member constraint: no inherited context, no fact join (a fact join would eliminate empty
         * tuples the natural ordering must keep). The result is a plain dimension {@code WHERE}
         * (e.g. {@code store_country = 'USA'}) on the target level's own FROM, so no join tables and
         * no {@code factJoinRequired} are reported. Arg shapes without a single-ColumnPredicate
         * member restriction return {@link java.util.Optional#empty()}.
         */
        private org.eclipse.daanse.rolap.common.sql.ContributionResult
            naturalOrderContribution(RolapCube baseCube) {
            if (args.length != 1) {
                // The non-join branch applies no constraint at all for multi-arg sets (isValid()
                // rejects them before evaluator creation) — mirror "adds nothing".
                return org.eclipse.daanse.rolap.common.sql.ContributionResult.of(
                    org.eclipse.daanse.rolap.common.sql.ConstraintContribution.EMPTY);
            }
            CrossJoinArg arg = args[0];
            // The member set + flags applied per arg type (same modelling as the SetConstraint
            // contribution loop).
            List<org.eclipse.daanse.rolap.api.element.RolapMember> argMembers;
            boolean argRestrict;
            boolean argExclude;
            if (arg instanceof org.eclipse.daanse.rolap.common.sql.MemberListCrossJoinArg mlArg) {
                argMembers = mlArg.getMembers();
                argRestrict = mlArg.isRestrictMemberTypes();
                argExclude = mlArg.isExclude();
            } else if (arg instanceof org.eclipse.daanse.rolap.common.sql.DescendantsCrossJoinArg) {
                argMembers = arg.getMembers(); // [member] (or null when the arg has no member)
                argRestrict = true;
                argExclude = false;
            } else {
                throw dead("topcount-natural-arg-shape");
            }
            if (argMembers == null) {
                // The arg constrains nothing -> the plain level-members query.
                return org.eclipse.daanse.rolap.common.sql.ContributionResult.of(
                    org.eclipse.daanse.rolap.common.sql.ConstraintContribution.EMPTY);
            }
            if (argMembers.isEmpty()
                || argMembers.stream().anyMatch(org.eclipse.daanse.olap.api.element.Member::isNull)) {
                // Empty set / NULL member: the member constraint degenerates to an always-false
                // "(1 = 0)" conjunct, which the builder has no table-less form for.
                throw dead("topcount-natural-arg-degenerate");
            }
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> argCp =
                org.eclipse.daanse.rolap.common.constraint.MemberConstraintWriter.memberConstraintContribution(
                    baseCube, argMembers, argRestrict, argExclude);
            if (argCp.isEmpty()) {
                if (argMembers.stream().allMatch(org.eclipse.daanse.olap.api.element.Member::isAll)) {
                    // An all-member set restricts nothing on this no-join path.
                    return org.eclipse.daanse.rolap.common.sql.ContributionResult.of(
                        org.eclipse.daanse.rolap.common.sql.ConstraintContribution.EMPTY);
                }
                throw dead("topcount-natural-arg-inexpressible");
            }
            // WHERE only: the predicate's columns live on the target level's own hierarchy FROM, so
            // no join tables are reported (reporting the arg's star table would force the fact join).
            // Single-operand And wrap: the member-set conjunct is one parenthesised group and must
            // sit one level below the mapper's top-level And split.
            return org.eclipse.daanse.rolap.common.sql.ContributionResult.of(new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
                java.util.Optional.of(org.eclipse.daanse.sql.statement.api.Predicates.and(
                    java.util.List.of(argCp.get().predicate()))), List.of()));
        }

        private boolean deduceNullability(Expression expr) {
            if (!(expr instanceof MemberExpression memberExpr)) {
                return true;
            }
            if (!(memberExpr.getMember() instanceof RolapStoredMeasure)) {
                return true;
            }
            final RolapStoredMeasure measure =
                (RolapStoredMeasure) memberExpr.getMember();
            return measure.getAggregator() != DistinctCountAggregator.INSTANCE;
        }

        @Override
		public Object getCacheKey() {
            List<Object> key = new ArrayList<>();
            key.add(super.getCacheKey());
            // Note: need to use string in order for caching to work
            if (orderByExpr != null) {
                key.add(orderByExpr.toString());
            }
            key.add(sortingDirection);
            key.add(topCount);
            key.add(this.getEvaluator().isNonEmpty());

            if (this.getEvaluator() instanceof RolapEvaluator) {
                key.add(
                    ((RolapEvaluator)this.getEvaluator())
                        .getSlicerMembers());
            }
            return key;
        }
    }

    @Override
	protected boolean restrictMemberTypes() {
        return true;
    }

    @Override
    public
	NativeEvaluator createEvaluator(
        RolapEvaluator evaluator,
        FunctionDefinition fun,
        Expression[] args, boolean enableNativeFilter )
    {
        if (!isEnabled() || !isValidContext(evaluator)) {
            return null;
        }

        // is this "TopCount(<set>, <count>, [<numeric expr>])"
        SortingDirection sortingDirection;
        String funName = fun.getFunctionMetaData().operationAtom().name();
        if ("TopCount".equalsIgnoreCase(funName)) {
            sortingDirection = SortingDirection.DESC;
        } else if ("BottomCount".equalsIgnoreCase(funName)) {
            sortingDirection = SortingDirection.ASC;
        } else {
            return null;
        }
        if (args.length < 2 || args.length > 3) {
            return null;
        }

        // extract the set expression
        List<CrossJoinArg[]> allArgs =
            crossJoinArgFactory().checkCrossJoinArg(evaluator, args[0], enableNativeFilter);

        // checkCrossJoinArg returns a list of CrossJoinArg arrays.  The first
        // array is the CrossJoin dimensions.  The second array, if any,
        // contains additional constraints on the dimensions. If either the list
        // or the first array is null, then native cross join is not feasible.
        if (allArgs == null || allArgs.isEmpty() || allArgs.getFirst() == null) {
            alertNonNativeTopCount(
                "Set in 1st argument does not support native eval.",
                evaluator.getCatalogReader().getContext().getConfigValue(ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED_DEFAULT_VALUE, String.class));
            return null;
        }

        CrossJoinArg[] cjArgs = allArgs.getFirst();
        if (isPreferInterpreter(cjArgs, false)) {
            alertNonNativeTopCount(
                "One or more args prefer non-native.",
                evaluator.getCatalogReader().getContext().getConfigValue(ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED_DEFAULT_VALUE, String.class));
            return null;
        }

		int count = 0;
		// extract count
		if ((args[1] instanceof NumericLiteral numericLiteral)) {
			count = numericLiteral.getIntValue();
		} else {
			alertNonNativeTopCount("TopCount value cannot be determined.",
                evaluator.getCatalogReader().getContext().getConfigValue(ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED_DEFAULT_VALUE, String.class));
			return null;
		}

        // extract "order by" expression
        CatalogReader schemaReader = evaluator.getCatalogReader();

        Context context=schemaReader.getContext();
        // generate the ORDER BY Clause
        // Need to generate top count order by to determine whether
        // or not it can be created. The top count
        // could change to use an aggregate table later in evaulation
        // Scratch context: this RolapNativeSql only generates the order-by node to test
        // convertibility; the node is discarded (the real ORDER BY / SELECT is regenerated in
        // TopCountConstraint.toContribution).
        RolapNativeSql sql =
            new RolapNativeSql(
                NativeSqlContext.scratch(org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities.of(context.getDialect())),
                null, evaluator, null);
        Expression orderByExpr = null;
        if (args.length == 3) {
            orderByExpr = args[2];
            if (sql.generateTopCountOrderByNode(args[2]) == null) {
                alertNonNativeTopCount(
                    "Cannot convert order by expression to SQL.",
                    evaluator.getCatalogReader().getContext().getConfigValue(ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED_DEFAULT_VALUE, String.class));
                return null;
            }
        }

        final int savepoint = evaluator.savepoint();
        try {
            overrideContext(evaluator, cjArgs, sql.getStoredMeasure());

            CrossJoinArg[] predicateArgs = null;
            if (allArgs.size() == 2) {
                predicateArgs = allArgs.get(1);
            }

            CrossJoinArg[] combinedArgs;
            if (predicateArgs != null) {
                // Combined the CJ and the additional predicate args
                // to form the TupleConstraint.
                combinedArgs =
                    Util.appendArrays(cjArgs, predicateArgs);
            } else {
                combinedArgs = cjArgs;
            }
            TopCountConstraint constraint =
                new TopCountConstraint(
                    count, combinedArgs, evaluator, orderByExpr, sortingDirection);
            if (!constraint.isValid()) {
                alertNonNativeTopCount(
                    "Constraint constructed cannot be used for native eval.",
                    evaluator.getCatalogReader().getContext().getConfigValue(ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED, ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED_DEFAULT_VALUE, String.class));
                return null;
            }
            LOGGER.debug("using native topcount");
            SetEvaluator sev =
                new SetEvaluator(cjArgs, schemaReader, constraint);
            sev.setMaxRows(count);
            sev.setCompleteWithNullValues(!evaluator.isNonEmpty());
            return sev;
        } finally {
            evaluator.restore(savepoint);
        }
    }

    private void alertNonNativeTopCount(String msg, String alertNativeEvaluationUnsupported) {
        RolapUtil.alertNonNative("TopCount", msg, alertNativeEvaluationUnsupported);
    }

    // package-local visibility for testing purposes
    public boolean isValidContext(RolapEvaluator evaluator) {
        return TopCountConstraint.isValidContext(
            evaluator, restrictMemberTypes());
    }
}
