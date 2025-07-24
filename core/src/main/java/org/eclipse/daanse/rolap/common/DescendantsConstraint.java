/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara and others
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

import java.util.List;

import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;

/**
 * TupleConstaint which restricts the result of a tuple sqlQuery to a
 * set of parents.  All parents must belong to the same level.
 *
 * @author av
 * @since Nov 10, 2005
 */
class DescendantsConstraint implements TupleConstraint {
    List<RolapMember> parentMembers;
    MemberChildrenConstraint mcc;

    /**
     * Creates a DescendantsConstraint.
     *
     * @param parentMembers list of parents all from the same level
     * @param mcc the constraint that would return the children for each single
     * parent
     */
    public DescendantsConstraint(
        List<RolapMember> parentMembers,
        MemberChildrenConstraint mcc)
    {
        this.parentMembers = parentMembers;
        this.mcc = mcc;
    }

    @Override
	public void addConstraint(
        SqlQuery sqlQuery,
        RolapCube baseCube,
        AggStar aggStar)
    {
        mcc.addMemberConstraint(sqlQuery, baseCube, aggStar, parentMembers);
    }

    @Override
	public void addLevelConstraint(
        SqlQuery sqlQuery,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level)
    {
        mcc.addLevelConstraint(sqlQuery, baseCube, aggStar, level);
    }

    @Override
	public MemberChildrenConstraint getMemberChildrenConstraint(
        RolapMember parent)
    {
        return mcc;
    }

    /**
     * {@inheritDoc}
     *
     * This implementation returns null, because descendants is not cached.
     */
    @Override
	public Object getCacheKey() {
        return null;
    }

    @Override
	public Evaluator getEvaluator() {
        return null;
    }

    @Override
    public boolean supportsAggTables() {
        return true;
    }
}
