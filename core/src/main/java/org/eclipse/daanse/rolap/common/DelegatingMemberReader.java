/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
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
import java.util.Map;

import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.rolap.common.TupleReader.MemberBuilder;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;

/**
 * A DelegatingMemberReader is a {@link MemberReader} which
 * redirects all method calls to an underlying {@link MemberReader}.
 *
 * @author jhyde
 * @since Feb 26, 2003
 */
class DelegatingMemberReader implements MemberReader {
    protected final MemberReader memberReader;

    DelegatingMemberReader(MemberReader memberReader) {
        this.memberReader = memberReader;
    }

    @Override
	public RolapMember substitute(RolapMember member) {
        return memberReader.substitute(member);
    }

    @Override
	public RolapMember desubstitute(RolapMember member) {
        return memberReader.desubstitute(member);
    }

    @Override
	public RolapMember getMemberByKey(
        RolapLevel level, List<Comparable> keyValues)
    {
        return memberReader.getMemberByKey(level, keyValues);
    }

    @Override
	public RolapMember getLeadMember(RolapMember member, int n) {
        return memberReader.getLeadMember(member, n);
    }

    @Override
	public List<RolapMember> getMembersInLevel(
        RolapLevel level)
    {
        return memberReader.getMembersInLevel(level);
    }

    @Override
	public void getMemberRange(
        RolapLevel level,
        RolapMember startMember,
        RolapMember endMember,
        List<RolapMember> list)
    {
        memberReader.getMemberRange(level, startMember, endMember, list);
    }

    @Override
	public int compare(
        RolapMember m1,
        RolapMember m2,
        boolean siblingsAreEqual)
    {
        return memberReader.compare(m1, m2, siblingsAreEqual);
    }

    @Override
	public RolapHierarchy getHierarchy() {
        return memberReader.getHierarchy();
    }

    @Override
	public boolean setCache(MemberCache cache) {
        return memberReader.setCache(cache);
    }

    @Override
	public List<RolapMember> getMembers() {
        return memberReader.getMembers();
    }

    @Override
	public List<RolapMember> getRootMembers() {
        return memberReader.getRootMembers();
    }

    @Override
	public void getMemberChildren(
        RolapMember parentMember,
        List<RolapMember> children)
    {
        getMemberChildren(parentMember, children, null);
    }

    @Override
	public Map<? extends Member, AccessMember> getMemberChildren(
        RolapMember parentMember,
        List<RolapMember> children,
        MemberChildrenConstraint constraint)
    {
        return memberReader.getMemberChildren(
            parentMember, children, constraint);
    }

    @Override
	public void getMemberChildren(
        List<RolapMember> parentMembers,
        List<RolapMember> children)
    {
        memberReader.getMemberChildren(
            parentMembers, children);
    }

    @Override
	public Map<? extends Member, AccessMember> getMemberChildren(
        List<RolapMember> parentMembers,
        List<RolapMember> children,
        MemberChildrenConstraint constraint)
    {
        return memberReader.getMemberChildren(
            parentMembers, children, constraint);
    }

    @Override
	public int getMemberCount() {
        return memberReader.getMemberCount();
    }

    @Override
	public RolapMember lookupMember(
        List<Segment> uniqueNameParts,
        boolean failIfNotFound)
    {
        return memberReader.lookupMember(uniqueNameParts, failIfNotFound);
    }

    @Override
	public List<RolapMember> getMembersInLevel(
        RolapLevel level, TupleConstraint constraint)
    {
        return memberReader.getMembersInLevel(
            level, constraint);
    }

    @Override
	public int getLevelMemberCount(RolapLevel level) {
        return memberReader.getLevelMemberCount(level);
    }

    @Override
	public MemberBuilder getMemberBuilder() {
        return memberReader.getMemberBuilder();
    }

    @Override
	public RolapMember getDefaultMember() {
        return memberReader.getDefaultMember();
    }

    @Override
	public RolapMember getMemberParent(RolapMember member) {
        return memberReader.getMemberParent(member);
    }
}
