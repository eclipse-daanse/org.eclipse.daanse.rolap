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

import org.eclipse.daanse.mdx.model.api.expression.operation.FunctionOperationAtom;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.function.FunctionInterface;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.function.FunctionResolver;
import org.eclipse.daanse.olap.function.core.FunctionMetaDataR;
import org.eclipse.daanse.olap.function.core.FunctionParameterR;
import org.eclipse.daanse.olap.function.core.resolver.AbstractFunctionDefinitionMultiResolver;
import org.osgi.service.component.annotations.Component;

@Component(service = FunctionResolver.class)
public class IntersectResolver extends AbstractFunctionDefinitionMultiResolver {
    private static final FunctionOperationAtom atom = new FunctionOperationAtom("Intersect");
    private static final List<String> reservedWords = List.of("ALL");
    private static final String DESCRIPTION = "Returns the intersection of two input sets, optionally retaining duplicates.";

    private static final FunctionMetaData functionMetaData = FunctionMetaDataR.of(atom, DESCRIPTION, DataType.SET,
            FunctionParameterR.param(DataType.SET, "Set1"), //
            FunctionParameterR.param(DataType.SET, "Set2"), //
            FunctionParameterR.param(DataType.SYMBOL, "All_Flag").reserved("ALL").asOptional()
                    .describedAs("ALL retains duplicates while intersecting."))
            .interfaceName(FunctionInterface.FILTER);

    @Override
    public List<String> getReservedWords() {
        return reservedWords;
    }

    public IntersectResolver() {
        super(List.of(new IntersectFunDef(functionMetaData)));
    }
}
