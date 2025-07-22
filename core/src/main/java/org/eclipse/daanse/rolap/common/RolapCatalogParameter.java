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

import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.Parameter;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.compiler.CompilableParameter;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.type.Type;
import org.eclipse.daanse.olap.calc.base.nested.AbstractProfilingNestedUnknownCalc;
import org.eclipse.daanse.olap.exceptions.ParameterIsNotModifiableException;

/**
 * Parameter that is defined in a schema.
 *
 * @author jhyde
 * @since Jul 20, 2006
 */
public class RolapCatalogParameter implements Parameter, CompilableParameter {
    private final RolapCatalog catalog;
    private final String name;
    private String description;
    private String defaultExpString;
    private Type type;
    private final boolean modifiable;
    private Object value;
    private boolean assigned;
    private Object cachedDefaultValue;

    RolapCatalogParameter(
        RolapCatalog catalog,
        String name,
        String defaultExpString,
        String description,
        Type type,
        boolean modifiable)
    {
        assert defaultExpString != null;
        assert name != null;
        assert catalog != null;
        assert type != null;
        this.catalog = catalog;
        this.name = name;
        this.defaultExpString = defaultExpString;
        this.description = description;
        this.type = type;
        this.modifiable = modifiable;
        catalog.parameterList.add(this);
    }

    RolapCatalog getCatalog() {
        return catalog;
    }

    @Override
	public boolean isModifiable() {
        return modifiable;
    }

    @Override
	public Scope getScope() {
        return Scope.Schema;
    }

    @Override
	public Type getType() {
        return type;
    }

    @Override
	public Expression getDefaultExp() {
        throw new UnsupportedOperationException();
    }

    @Override
	public String getName() {
        return name;
    }

    @Override
	public String getDescription() {
        return description;
    }

    @Override
	public Object getValue() {
        return value;
    }

    @Override
	public void setValue(Object value) {
        if (!modifiable) {
            throw new ParameterIsNotModifiableException(
                getName(), getScope().name());
        }
        this.assigned = true;
        this.value = value;
    }

    @Override
	public boolean isSet() {
        return assigned;
    }

    @Override
	public void unsetValue() {
        if (!modifiable) {
            throw new ParameterIsNotModifiableException(
                getName(), getScope().name());
        }
        assigned = false;
        value = null;
    }

    @Override
	public Calc compile(ExpressionCompiler compiler) {
        // Parse and compile the expression for the default value.
        Expression defaultExp = compiler.getValidator()
            .getQuery()
            .getConnection()
            .parseExpression(defaultExpString);
        defaultExp = compiler.getValidator().validate(defaultExp, true);
        final Calc defaultCalc = defaultExp.accept(compiler);

        // Generate a program which looks at the assigned value first,
        // and if it is not set, returns the default expression.
        return new AbstractProfilingNestedUnknownCalc(defaultExp.getType()) {
            @Override
			public Calc[] getChildCalcs() {
                return new Calc[] {defaultCalc};
            }

            @Override
			public Object evaluate(Evaluator evaluator) {
                if (value != null) {
                    return value;
                }
                if (cachedDefaultValue == null) {
                    cachedDefaultValue = defaultCalc.evaluate(evaluator);
                }
                return cachedDefaultValue;
            }
        };
    }
}
