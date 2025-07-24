/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2015-2017 Hitachi Vantara and others
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

import static java.util.Arrays.asList;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;

/**
 * Constraint which excludes the members in the list received in constructor.
 *
 * @author Pedro Vale
 */
class MemberExcludeConstraint implements TupleConstraint {
    private final List<RolapMember> excludes;
    private final Object cacheKey;
    private final RolapLevel level;
    private final RolapNativeSet.SetConstraint csc;
    private final Map<RolapLevel, List<RolapMember>> roles;

    /**
     * Creates a MemberExcludeConstraint.
     *
     */
    public MemberExcludeConstraint(
        List<RolapMember> excludes,
        RolapLevel level,
        RolapNativeSet.SetConstraint csc)
    {
        this.excludes = excludes;
        this.cacheKey = asList(MemberExcludeConstraint.class, excludes, csc);
        this.level = level;
        this.csc = csc;

        roles = (csc == null)
            ? Collections.<RolapLevel, List<RolapMember>>emptyMap()
            : SqlConstraintUtils.getRolesConstraints(csc.getEvaluator());
    }


    @Override
    public int hashCode() {
        return getCacheKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MemberExcludeConstraint
            && getCacheKey()
                .equals(((MemberExcludeConstraint) obj).getCacheKey());
    }

    @Override
    public void addLevelConstraint(
        SqlQuery query,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level)
    {
        if (level.equalsOlapElement(this.level)) {
            SqlConstraintUtils.addMemberConstraint(
                query, baseCube, aggStar, excludes, true, false, true);
        }
        if (csc != null) {
            for (CrossJoinArg cja : csc.args) {
                if (cja.getLevel().equalsOlapElement(level)) {
                    cja.addConstraint(query, baseCube, aggStar);
                }
            }
        }

        if (roles.containsKey(level)) {
            List<RolapMember> members = roles.get(level);
            SqlConstraintUtils.addMemberConstraint(
                query, baseCube, aggStar, members, true, false, false);
        }
    }

    @Override
	public String toString() {
        return new StringBuilder("MemberExcludeConstraint(").append(excludes).append(")").toString();
    }

    @Override
    public Object getCacheKey() {
        return cacheKey;
    }


    @Override
	public MemberChildrenConstraint getMemberChildrenConstraint(
        RolapMember parent)
    {
        return DefaultMemberChildrenConstraint.instance();
    }

    @Override
    public void addConstraint(
        SqlQuery sqlQuery,
        RolapCube baseCube,
        AggStar aggStar)
    {
    }

    @Override
    public Evaluator getEvaluator() {
        if (csc != null) {
            return csc.getEvaluator();
        } else {
            return null;
        }
    }

    @Override
    public boolean supportsAggTables() {
        return true;
    }
}
