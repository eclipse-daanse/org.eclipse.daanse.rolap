/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.element;

import org.eclipse.daanse.olap.api.query.component.Expression;

/**
 * Member of a parent-child dimension which has no closure table.
 *
 * This member is calculated. When you ask for its value, it returns
 * an expression which aggregates the values of its child members.
 * This calculation is very inefficient, and we can only support
 * aggregatable measures ("count distinct" is non-aggregatable).
 * Unfortunately it's the best we can do without a closure table.
 */
public class RolapParentChildMemberNoClosure
    extends RolapParentChildMember
{

    public RolapParentChildMemberNoClosure(
        RolapMember parentMember,
        RolapLevel childLevel, Object value, RolapMember dataMember)
    {
        super(parentMember, childLevel, value, dataMember);
    }

    @Override
	protected boolean computeCalculated(final MemberType memberType) {
        return true;
    }

    @Override
    public boolean isCalculated() {
        return false;
    }

    @Override
	public Expression getExpression() {
        return super.getHierarchy().getAggregateChildrenExpression();
    }

    @Override
    public boolean isParentChildPhysicalMember() {
        return true;
    }
}
