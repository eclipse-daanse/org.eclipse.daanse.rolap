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
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
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
import org.eclipse.daanse.rolap.common.sql.QueryTape;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
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

    static class TopCountConstraint extends SetConstraint {
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

        /** Records the top-count constraint on the fork: the measure order (SELECT + prepended
         *  ORDER BY) when present, then the inherited context (join-required) or only args[0]'s
         *  member constraint (natural order). */
        @Override
        public QueryTape addConstraintOps(
            Dialect dialect,
            QueryRecorder.Fork fork,
            RolapCube baseCube,
            AggStar aggStar)
        {
            assert isValid();
            if (orderByExpr != null) {
                RolapNativeSql sql =
                    new RolapNativeSql(
                        NativeSqlContext.ofRecorder(
                            fork, getEvaluator().getCatalogReader().getContext().getDialect()),
                        aggStar, getEvaluator(), null);
                final org.eclipse.daanse.sql.statement.api.expression.SqlExpression orderByNode =
                    sql.generateTopCountOrderByNode(orderByExpr);
                boolean nullable =
                    deduceNullability(orderByExpr);
                final String orderByAlias =
                    fork.addSelectNode(orderByNode, null);
                fork.addOrderByNode(
                    orderByNode,
                    orderByAlias,
                    sortingDirection,
                    true,
                    nullable,
                    true);
            }
            if (isJoinRequired()) {
                super.addConstraintOps(dialect, fork, baseCube, aggStar);
            } else if (args.length == 1) {
                args[0].addConstraint(dialect, fork, baseCube, null);
            }
            return fork.ops();
        }

        /**
         * The builder counterpart of {@link #addConstraint} for the join-required (orderByExpr present)
         * form: the inherited {@link SetConstraint} context/cross-join contribution plus a
         * {@link org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder} carrying the
         * measure projection + sort. The measure node is built exactly as {@code AbstractQuerySpec.addMeasure}
         * does (column node wrapped by the aggregator). Returns
         * {@link java.util.Optional#empty()} (recorder path) for the 2-arg natural-order form, a non-stored-measure
         * orderByExpr, a composite/non-node aggregator, or when the inherited context cannot be expressed.
         */
        @Override
        public java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> toContribution(
            RolapCube baseCube, AggStar aggStar) {
            if (!(orderByExpr instanceof MemberExpression me)
                || !(me.getMember() instanceof RolapStoredMeasure storedMeasure)
                || !(storedMeasure.getStarMeasure()
                        instanceof org.eclipse.daanse.rolap.common.star.RolapStar.Measure starMeasure)) {
                // No expressible measure order (the 2-arg natural form, a calc-measure order, a non-node
                // aggregator): carry the inherited context/cross-join so an ancestor slicer / args[0]
                // restriction is not dropped — the native top-N is applied by the evaluator. BUT the
                // natural-order recorded path (isJoinRequired()==false here) applies ONLY args[0], never the
                // slicer context, and never joins the fact (it would eliminate empty tuples). So if the
                // inherited context forces a fact join (a CROSS-dimension slicer, e.g. [Time].[1997] over a
                // [Customer] target), the builder would over-join (join sales_fact + a slicer WHERE) where
                // the reference adds none — bail to the reference. A residual measure-order divergence also
                // falls back via the guard.
                java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> natural =
                    super.toContribution(baseCube, aggStar);
                if (natural.isPresent() && natural.get().requiresFactJoin()) {
                    // The context would force the fact join — but the natural-order recorded path applies
                    // ONLY args[0], never the context, never the fact join. So
                    // model that exactly: args[0]'s member constraint as a plain dimension WHERE
                    // (e.g. `store_country = 'USA'`, no join). Unmodelled arg
                    // shapes stay bailed (guarded fallback).
                    return bail("topcount-natural-context-fact-join");
                }
                return natural;
            }
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> base =
                super.toContribution(baseCube, aggStar);
            if (base.isEmpty()) {
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
                (starMeasure.getAggregator()
                        instanceof org.eclipse.daanse.rolap.aggregator.AbstractAggregator agg)
                    ? agg.getExpression(innerNode) : null;
            if (measureNode == null) {
                return bail("topcount-non-node-aggregator");
            }
            org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = base.get();
            // Carry the base's factJoinRequired — same re-wrap drop as RolapNativeFilter: without it the
            // mapper's same-dimension gate skips the fact join the measure ORDER BY references.
            return java.util.Optional.of(new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
                c.where(), c.joinTables(), c.orderedPredicates(), c.memberKeyGroup(),
                java.util.Optional.of(new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder(
                    measureNode, sortingDirection, deduceNullability(orderByExpr))))
                .withFactJoinRequired(c.factJoinRequired()));
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
        // convertibility; the node is discarded (the real ORDER BY / SELECT is regenerated against
        // the executing query in TopCountConstraint.addConstraintOps). No recorder is needed.
        RolapNativeSql sql =
            new RolapNativeSql(
                NativeSqlContext.scratch(context.getDialect()), null, evaluator, null);
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
