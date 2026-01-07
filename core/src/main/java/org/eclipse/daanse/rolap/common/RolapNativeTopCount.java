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

package org.eclipse.daanse.rolap.common;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.NativeEvaluator;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.api.query.component.NumericLiteral;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
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
        boolean ascending;
        Integer topCount;

        public TopCountConstraint(
            int count,
            CrossJoinArg[] args, RolapEvaluator evaluator,
            Expression orderByExpr, boolean ascending)
        {
            super(args, evaluator, true);
            this.orderByExpr = orderByExpr;
            this.ascending = ascending;
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
		protected boolean isJoinRequired() {
            return orderByExpr != null;
        }

        @Override
        public boolean supportsAggTables() {
            // We can only safely use agg tables if we can limit
            // results to those with data (i.e. if we would be
            // joining to a fact table).
            return isJoinRequired();
        }

        @Override
		public void addConstraint(
            SqlQuery sqlQuery,
            RolapCube baseCube,
            AggStar aggStar)
        {
            assert isValid();
            if (orderByExpr != null) {
                RolapNativeSql sql =
                    new RolapNativeSql(
                        sqlQuery, aggStar, getEvaluator(), null);
                final StringBuilder orderBySql =
                    sql.generateTopCountOrderBy(orderByExpr);
                boolean nullable =
                    deduceNullability(orderByExpr);
                final String orderByAlias =
                    sqlQuery.addSelect(orderBySql, null);
                sqlQuery.addOrderBy(
                    orderBySql,
                    orderByAlias,
                    ascending,
                    true,
                    nullable,
                    true);
            }
            if (isJoinRequired()) {
                super.addConstraint(sqlQuery, baseCube, aggStar);
            } else if (args.length == 1) {
                args[0].addConstraint(sqlQuery, baseCube, null);
            }
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
            key.add(ascending);
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
        boolean ascending;
        String funName = fun.getFunctionMetaData().operationAtom().name();
        if ("TopCount".equalsIgnoreCase(funName)) {
            ascending = false;
        } else if ("BottomCount".equalsIgnoreCase(funName)) {
            ascending = true;
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
        SqlQuery sqlQuery = SqlQuery.newQuery(context, "NativeTopCount");
        RolapNativeSql sql =
            new RolapNativeSql(
                sqlQuery, null, evaluator, null);
        Expression orderByExpr = null;
        if (args.length == 3) {
            orderByExpr = args[2];
            StringBuilder orderBySQL = sql.generateTopCountOrderBy(args[2]);
            if (orderBySQL == null) {
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
                    count, combinedArgs, evaluator, orderByExpr, ascending);
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
