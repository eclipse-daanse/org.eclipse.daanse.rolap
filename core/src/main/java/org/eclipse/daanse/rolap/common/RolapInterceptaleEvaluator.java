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

import java.util.List;

import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.calc.base.compiler.DelegatingExpressionCompiler;

/**
 * Evaluator that collects profiling information as it evaluates expressions.
 *
 *
 * TODO: Cleanup tasks as part of explain/profiling project:
 *
 *
 * 1. Obsolete AbstractCalc.calcs member, AbstractCalc.getCalcs(), and Calc[]
 * constructor parameter to many Calc subclasses. Store the tree structure
 * (children of a calc, parent of a calc) in RolapEvaluatorRoot.compiledExps.
 *
 *
 * Rationale: Children calcs are used in about 50 places, but mostly for
 * dependency-checking (e.g.
 * org.eclipse.daanse.olap.calc.base.AbstractProfilingNestedCalc#anyDepends. A
 * few places uses the calcs array but should use more strongly typed members.
 * e.g. FilterFunDef.MutableMemberIterCalc should have data members
 * 'MemberListCalc listCalc' and 'BooleanCalc conditionCalc'.
 *
 *
 * 2. Split Query into parse tree, plan, statement. Fits better into the
 * createStatement - prepare - execute JDBC lifecycle. Currently Query has
 * aspects of all of these, and some other state is held in RolapResult
 * (computed in the constructor, unfortunately) and RolapEvaluatorRoot. This
 * cleanup may not be essential for the explain/profiling task but should happen
 * soon afterwards.
 *
 * @author jhyde
 * @since October, 2010
 */
public class RolapInterceptaleEvaluator extends RolapEvaluator {

    /**
     * Creates a profiling evaluator.
     *
     * @param root Shared context between this evaluator and its children
     */
    RolapInterceptaleEvaluator(RolapEvaluatorRoot root) {
        super(root);
    }

    /**
     * Creates a child evaluator.
     *
     * @param root      Root evaluation context
     * @param evaluator Parent evaluator
     */
    private RolapInterceptaleEvaluator(RolapEvaluatorRoot root, RolapInterceptaleEvaluator evaluator,
            List<List<Member>> aggregationList) {
        super(root, evaluator, aggregationList);
    }

    @Override
    protected RolapEvaluator pushClone(List<List<Member>> aggregationList) {
        return new RolapInterceptaleEvaluator(root, this, aggregationList);
    }

    public static class InterceptableEvaluatorCompiler extends DelegatingExpressionCompiler {
        public InterceptableEvaluatorCompiler(ExpressionCompiler compiler) {
            super(compiler);
        }

        @Override
        protected Calc<?> afterCompile(Expression exp, Calc calc, boolean mutable) {
            calc = super.afterCompile(exp, calc, mutable);
            // Theoretical option to wrap a calc for special reasons, to do extras
            return calc;
        }
    }

}
