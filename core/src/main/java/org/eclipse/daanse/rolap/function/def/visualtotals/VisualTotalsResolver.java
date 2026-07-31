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

import java.util.List;

import org.eclipse.daanse.mdx.model.api.expression.operation.FunctionOperationAtom;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.function.FunctionResolver;
import org.eclipse.daanse.olap.function.core.FunctionMetaDataR;
import org.eclipse.daanse.olap.function.core.FunctionParameterR;
import org.eclipse.daanse.olap.function.core.resolver.AbstractFunctionDefinitionMultiResolver;
import org.osgi.service.component.annotations.Component;

@Component(service = FunctionResolver.class)
public class VisualTotalsResolver extends AbstractFunctionDefinitionMultiResolver {
    private static final FunctionOperationAtom atom = new FunctionOperationAtom("VisualTotals");
    private static final String DESCRIPTION = "Dynamically totals child members specified in a set using a pattern for the total label in the result set.";

    private static final FunctionMetaData functionMetaData = FunctionMetaDataR.of(atom, DESCRIPTION, DataType.SET,
            FunctionParameterR.param(DataType.SET), //
            FunctionParameterR.param(DataType.STRING, "Pattern").asOptional()
                    .describedAs("Label pattern for the visual total member; * is replaced by the parent name."));

    public VisualTotalsResolver() {
        super(List.of(new VisualTotalsFunDef(functionMetaData)));
    }
}
