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


package org.eclipse.daanse.rolap.common.constraint;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.QueryTape;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;

/**
 * Restricts the SQL result set to the parent member of a
 * MemberChildren query.  If called with a calculated member an
 * exception will be thrown.
 */
public class DefaultMemberChildrenConstraint
    implements MemberChildrenConstraint
{
    private static final MemberChildrenConstraint instance =
        new DefaultMemberChildrenConstraint();

    protected DefaultMemberChildrenConstraint() {
    }

    /**
     * Records the single-parent member constraint ({@code WHERE parent = value}) on the fork.
     */
    @Override
    public QueryTape addMemberConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar,
        RolapMember parent)
    {
        MemberConstraintWriter.addMemberConstraint(
            dialect, fork, baseCube, aggStar, parent, true);
        return fork.ops();
    }

    /**
     * Records the member-set constraint ({@code WHERE exp IN (...)}) for {@code parents} on the
     * fork — see the single-parent form.
     */
    @Override
    public QueryTape addMemberConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar,
        List<RolapMember> parents)
    {
        boolean exclude = false;
        MemberConstraintWriter.addMemberConstraint(
            dialect, fork, baseCube, aggStar, parents, true, false, exclude);
        return fork.ops();
    }

    /** No per-level restriction here; {@code ChildByNameConstraint} overrides this with its
     *  name filter. */
    @Override
    public QueryTape addMemberLevelConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level)
    {
        return fork.ops();
    }

    @Override
    public Optional<ConstraintContribution> toContribution(
        RolapCube baseCube,
        AggStar aggStar,
        RolapMember parent)
    {
        // Only the parent-member key restriction, on the dimension's own tables — no fact join.
        return Optional.of(new ConstraintContribution(
            JoinPlanner.memberKeyConstraint(parent), List.of()));
    }

    @Override
	public String toString() {
        return "DefaultMemberChildrenConstraint";
    }

    @Override
	public Object getCacheKey() {
        // we have no state, so all instances are equal
        return this;
    }

    public static MemberChildrenConstraint instance() {
        return instance;
    }
}

// End DefaultMemberChildrenConstraint.java

