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
package org.eclipse.daanse.rolap.function.def.intersect;

import java.util.List;

import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.calc.todo.TupleListCalc;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.query.component.ResolvedFunCall;
import org.eclipse.daanse.olap.fun.FunUtil;
import org.eclipse.daanse.olap.function.def.AbstractFunctionDefinition;

public class IntersectFunDef extends AbstractFunctionDefinition
{
    private static final List<String> ReservedWords = List.of("ALL");

    public IntersectFunDef(FunctionMetaData functionMetaData)
    {
        super(functionMetaData);
    }

    @Override
    public Calc<?> compileCall( ResolvedFunCall call, ExpressionCompiler compiler) {
        final String literalArg = FunUtil.getLiteralArg(call, 2, "", IntersectFunDef.ReservedWords);
        final boolean all = literalArg.equalsIgnoreCase("ALL");
        final int arity = call.getType().getArity();

        final TupleListCalc listCalc1 = compiler.compileList(call.getArg(0));
        final TupleListCalc listCalc2 = compiler.compileList(call.getArg(1));
        return new IntersectCalc(
                call.getType(), listCalc1, listCalc2, all, arity);
    }
}
