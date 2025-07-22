/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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

import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.function.def.aggregate.AggregateFunDef;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;

/**
 * Implementation of {@link org.eclipse.daanse.rolap.common.RolapCalculation}
 * that wraps a {@link RolapMember calculated member}.
 *
 * @author jhyde
 * @since May 15, 2009
 */
class RolapMemberCalculation implements RolapCalculation {
    private final RolapMember member;
    private final int solveOrder;
    private Boolean containsAggregateFunction;

    /**
     * Creates a RolapMemberCalculation.
     *
     * @param member Calculated member
     */
    public RolapMemberCalculation(RolapMember member) {
        this.member = member;
        // compute and solve order: it is used frequently
        solveOrder = this.member.getSolveOrder();
        assert member.isEvaluated();
    }

    @Override
	public int hashCode() {
        return member.hashCode();
    }

    @Override
	public boolean equals(Object obj) {
        return obj instanceof RolapMemberCalculation
            && member == ((RolapMemberCalculation) obj).member;
    }

    @Override
	public void setContextIn(RolapEvaluator evaluator) {
        final RolapMember defaultMember =
            evaluator.root.defaultMembers[getHierarchyOrdinal()];

        // This method does not need to call RolapEvaluator.removeCalcMember.
        // That happens implicitly in setContext.
        evaluator.setContext(defaultMember);
        evaluator.setExpanding(member);
    }

    @Override
	public int getSolveOrder() {
        return solveOrder;
    }

    @Override
	public int getHierarchyOrdinal() {
        return member.getHierarchy().getOrdinalInCube();
    }

    @Override
	public Calc getCompiledExpression(RolapEvaluatorRoot root) {
        final Expression exp = member.getExpression();
        return root.getCompiled(exp, true, null);
    }

    @Override
	public boolean isCalculatedInQuery() {
        return member.isCalculatedInQuery();
    }

    @Override
	public boolean containsAggregateFunction() {
        // searching for agg functions is expensive, so cache result
        if (containsAggregateFunction == null) {
            containsAggregateFunction =
                foundAggregateFunction(member.getExpression());
        }
        return containsAggregateFunction;
    }

    /**
     * Returns whether an expression contains a call to an aggregate
     * function such as "Aggregate" or "Sum".
     *
     * @param exp Expression
     * @return Whether expression contains a call to an aggregate function.
     */
    private static boolean foundAggregateFunction(Expression exp) {
        if (exp instanceof ResolvedFunCallImpl resolvedFunCall) {
            if (resolvedFunCall.getFunDef() instanceof AggregateFunDef) {
                return true;
            } else {
                for (Expression argExp : resolvedFunCall.getArgs()) {
                    if (foundAggregateFunction(argExp)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
