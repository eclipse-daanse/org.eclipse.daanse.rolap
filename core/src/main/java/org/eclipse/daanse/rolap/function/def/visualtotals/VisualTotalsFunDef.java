/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.function.def.visualtotals;

import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.Validator;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.StringCalc;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.calc.todo.TupleListCalc;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.ResolvedFunCall;
import org.eclipse.daanse.olap.api.type.MemberType;
import org.eclipse.daanse.olap.api.type.SetType;
import org.eclipse.daanse.olap.api.type.Type;
import org.eclipse.daanse.olap.function.def.AbstractFunctionDefinition;

public class VisualTotalsFunDef extends AbstractFunctionDefinition {
        private final static String visualTotalsAppliedToTuples =
            "Argument to 'VisualTotals' function must be a set of members; got set of tuples.";

        public VisualTotalsFunDef(FunctionMetaData functionMetaData) {
            super(functionMetaData);
        }

        @Override
        protected Expression validateArgument(
            Validator validator, Expression[] args, int i, DataType category)
        {
            final Expression validatedArg =
                super.validateArgument(validator, args, i, category);
            if (i == 0) {
                // The function signature guarantees that we have a set of members
                // or a set of tuples.
                final SetType setType = (SetType) validatedArg.getType();
                final Type elementType = setType.getElementType();
                if (!(elementType instanceof MemberType)) {
                    throw new OlapRuntimeException(visualTotalsAppliedToTuples);
                }
            }
            return validatedArg;
        }

        @Override
        public Calc<?> compileCall( ResolvedFunCall call, ExpressionCompiler compiler) {
            final TupleListCalc tupleListCalc = compiler.compileList(call.getArg(0));
            final StringCalc stringCalc =
                call.getArgCount() > 1
                ? compiler.compileString(call.getArg(1))
                : null;
            return new VisualTotalsCalc(call, tupleListCalc, stringCalc);
        }

}
