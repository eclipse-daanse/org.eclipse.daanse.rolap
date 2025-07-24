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


package org.eclipse.daanse.rolap.common.sql;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.rolap.common.SqlConstraintUtils;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;

/**
 * Represents one of:
 *
 * Level.Members:  member == null and level != null
 * Member.Children: member != null and level =
 *     member.getLevel().getChildLevel()
 * Member.Descendants: member != null and level == some level below
 *     member.getLevel()
 *
 */
public class DescendantsCrossJoinArg implements CrossJoinArg {
    RolapMember member;
    RolapLevel level;

    public DescendantsCrossJoinArg(RolapLevel level, RolapMember member) {
        this.level = level;
        this.member = member;
    }

    @Override
	public RolapLevel getLevel() {
        return level;
    }

    @Override
	public List<RolapMember> getMembers() {
        if (member == null) {
            return null;
        }
        final List<RolapMember> list = new ArrayList<>();
        list.add(member);
        return list;
    }

    @Override
	public void addConstraint(
        SqlQuery sqlQuery,
        RolapCube baseCube,
        AggStar aggStar)
    {
        if (member != null) {
            SqlConstraintUtils.addMemberConstraint(
                sqlQuery, baseCube, aggStar, member, true);
        }
    }

    @Override
	public boolean isPreferInterpreter(boolean joinArg) {
        return false;
    }

    private boolean equals(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    @Override
	public boolean equals(Object obj) {
        if (!(obj instanceof DescendantsCrossJoinArg that)) {
            return false;
        }
        if (!equals(this.level, that.level)) {
            return false;
        }
        return equals(this.member, that.member);
    }

    @Override
	public int hashCode() {
        int c = 1;
        if (level != null) {
            c = level.hashCode();
        }
        if (member != null) {
            c = 31 * c + member.hashCode();
        }
        return c;
    }
}
