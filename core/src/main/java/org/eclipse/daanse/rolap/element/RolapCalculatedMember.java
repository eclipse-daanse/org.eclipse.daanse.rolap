/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * Copyright (C) 2021 Sergei Semenkov
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

package org.eclipse.daanse.rolap.element;

import org.eclipse.daanse.olap.api.element.CalculatedMember;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.element.OlapMetaData;
import org.eclipse.daanse.olap.query.component.FormulaImpl;

/**
 * A RolapCalculatedMember is a member based upon a
 * {@link FormulaImpl}.
 *
 * It is created before the formula has been resolved; the formula is
 * responsible for setting the "format_string" property.
 *
 * @author jhyde
 * @since 26 August, 2001
 */
public class RolapCalculatedMember extends RolapMemberBase implements CalculatedMember{
    private final Formula formula;
    private MetaData metaData;
    // source cube for a virtual member
    private RolapCube baseCube;

    /**
     * Creates a RolapCalculatedMember.
     *
     * @param parentMember Parent member
     * @param level Level
     * @param name Name
     * @param formula Formula
     */
    public RolapCalculatedMember(
        RolapMember parentMember,
        RolapLevel level,
        String name,
        Formula formula)
    {
        // A calculated measure has MemberType.FORMULA because FORMULA
        // overrides MEASURE.
        super(parentMember, level, name, null, MemberType.FORMULA);
        this.formula = formula;
        this.metaData = OlapMetaData.empty();
    }

    // override RolapMember
    @Override
	public int getSolveOrder() {
        final Number solveOrder = formula.getSolveOrder();
        return solveOrder == null ? 0 : solveOrder.intValue();
    }

    @Override
	public Object getPropertyValue(String propertyName, boolean matchCase) {
        if (Util.equalWithMatchCaseOption(propertyName, StandardProperty.FORMULA.getName(), matchCase)) {
            return formula;
        } else if (Util.equalWithMatchCaseOption(
                propertyName, StandardProperty.CHILDREN_CARDINALITY.getName(), matchCase))
        {
            // Looking up children is unnecessary for calculated member.
            // If do that, SQLException will be thrown.
            return 0;
        } else {
            return super.getPropertyValue(propertyName, matchCase);
        }
    }

    @Override
	protected boolean computeCalculated(final MemberType memberType) {
        return true;
    }

    @Override
	public boolean isCalculatedInQuery() {
        final String memberScope =
            (String) getPropertyValue(StandardProperty.MEMBER_SCOPE.getName());
        return memberScope == null
            || memberScope.equals("QUERY");
    }

    @Override
	public Expression getExpression() {
        return formula.getExpression();
    }

    public Formula getFormula() {
        return formula;
    }

    @Override
    public MetaData getMetaData() {
        return metaData;
    }

    public void setMetadata(MetaData metaData) {
        assert metaData != null;
        this.metaData = metaData;
    }

    public RolapCube getBaseCube() {
        return baseCube;
    }

    public void setBaseCube(RolapCube baseCube) {
        this.baseCube = baseCube;
    }
}
