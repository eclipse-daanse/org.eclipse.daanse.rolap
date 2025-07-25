/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2002 Julian Hyde
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2007-2008 StrateBI
 * Copyright (C) 2008-2017 Hitachi Vantara and others
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.common.TupleReader.MemberBuilder;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.eclipse.daanse.rolap.util.ConcatenableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NoCacheMemberReader implements {@link MemberReader} but
 * without doing any kind of caching and avoiding to read all members.
 *
 * @author jlopez, lcanals
 * @since 06 October, 2007
 */
public class NoCacheMemberReader implements MemberReader, MemberCache {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(NoCacheMemberReader.class);

    private final SqlConstraintFactory sqlConstraintFactory =
        SqlConstraintFactory.instance();

    private final MemberReader source;


    public NoCacheMemberReader(MemberReader source) {
        this.source = source;
        if (!source.setCache(this)) {
            throw Util.newInternal(
                new StringBuilder("MemberSource (").append(source).append(", ").append(source.getClass())
                .append(") does not support cache-writeback").toString());
        }
    }

    // implementes MemberCache
    @Override
	public boolean isMutable() {
        return false;
    }

    // implementes MemberCache
    @Override
	public RolapMember removeMember(Object key) {
        return null;
    }

    // implementes MemberCache
    @Override
	public RolapMember removeMemberAndDescendants(Object key) {
        return null;
    }

    // implement MemberReader
    @Override
	public RolapHierarchy getHierarchy() {
        return source.getHierarchy();
    }

    // implement MemberCache
    @Override
	public boolean setCache(MemberCache cache) {
        return false;
    }

    // implement MemberCache
    @Override
	public Object makeKey(final RolapMember parent, final Object key) {
        LOGGER.debug("Entering makeKey");
        return new MemberKey(parent, key);
    }

    @Override
	public synchronized RolapMember getMember(final Object key) {
        return getMember(key, true);
    }

    @Override
	public RolapMember getMember(
        final Object key,
        final boolean mustCheckCacheStatus)
    {
        LOGGER.debug("Returning null member: no cache");
        return null;
    }


    // implement MemberCache
    @Override
	public Object putMember(final Object key, final RolapMember value) {
        LOGGER.debug("putMember void for no caching");
        return value;
    }

    // implement MemberReader
    @Override
	public List<RolapMember> getMembers() {
        System.out.println("NoCache getMembers");
        List<RolapMember> v = new ArrayList<>();
        List<RolapLevel> levels =  (List<RolapLevel>) getHierarchy().getLevels();
        // todo: optimize by walking to children for members we know about
        for (RolapLevel level : levels) {
            List<RolapMember> membersInLevel =
                getMembersInLevel(level);
            v.addAll(membersInLevel);
        }
        return v;
    }

    @Override
	public List<RolapMember> getRootMembers() {
        LOGGER.debug("Getting root members");
        return source.getRootMembers();
    }

    @Override
	public List<RolapMember> getMembersInLevel(
        final RolapLevel level)
    {
        TupleConstraint constraint =
            sqlConstraintFactory.getLevelMembersConstraint(null);
        return getMembersInLevel(level, constraint);
    }

    @Override
	public List<RolapMember> getMembersInLevel(
        final RolapLevel level, final TupleConstraint constraint)
    {
        LOGGER.debug("Entering getMembersInLevel");
        return source.getMembersInLevel(
            level, constraint);
    }

    @Override
	public RolapMember getMemberByKey(
        RolapLevel level, List<Comparable> keyValues)
    {
        return source.getMemberByKey(level, keyValues);
    }

    @Override
	public void getMemberChildren(
        final RolapMember parentMember,
        final List<RolapMember> children)
    {
        MemberChildrenConstraint constraint =
                sqlConstraintFactory.getMemberChildrenConstraint(null);
        getMemberChildren(parentMember, children, constraint);
    }

    @Override
	public Map<? extends Member, AccessMember> getMemberChildren(
        final RolapMember parentMember,
        final List<RolapMember> children,
        final MemberChildrenConstraint constraint)
    {
        List<RolapMember> parentMembers = new ArrayList<>();
        parentMembers.add(parentMember);
        return getMemberChildren(parentMembers, children, constraint);
    }

    @Override
	public void getMemberChildren(
        final List<RolapMember> parentMembers,
        final List<RolapMember> children)
    {
        MemberChildrenConstraint constraint =
            sqlConstraintFactory.getMemberChildrenConstraint(null);
        getMemberChildren(parentMembers, children, constraint);
    }

    @Override
	public Map<? extends Member, AccessMember> getMemberChildren(
        final List<RolapMember> parentMembers,
        final List<RolapMember> children,
        final MemberChildrenConstraint constraint)
    {
        assert (constraint != null);
        LOGGER.debug("Entering getMemberChildren");
        return
            source.getMemberChildren(
                parentMembers, children, constraint);
    }

    @Override
	public RolapMember lookupMember(
        final List<Segment> uniqueNameParts,
        final boolean failIfNotFound)
    {
        return RolapUtil.lookupMember(this, uniqueNameParts, failIfNotFound);
    }

    @Override
	public List<RolapMember> getChildrenFromCache(
        final RolapMember member,
        final MemberChildrenConstraint constraint)
    {
        return null;
    }

    @Override
	public List<RolapMember> getLevelMembersFromCache(
        final RolapLevel level,
        final TupleConstraint constraint)
    {
        return null;
    }

    @Override
	public void putChildren(
        final RolapMember member,
        final MemberChildrenConstraint constraint,
        final List<RolapMember> children)
    {
    }

    @Override
	public void putChildren(
        final RolapLevel level,
        final TupleConstraint constraint,
        final List<RolapMember> children)
    {
    }

    @Override
	public RolapMember getLeadMember(RolapMember member, int n) {
        if (n == 0 || member.isNull()) {
            return member;
        } else {
            SiblingIterator iter = new SiblingIterator(this, member);
            if (n > 0) {
                RolapMember sibling = null;
                while (n-- > 0) {
                    if (!iter.hasNext()) {
                        return (RolapMember) member.getHierarchy()
                            .getNullMember();
                    }
                    sibling = iter.nextMember();
                }
                return sibling;
            } else {
                n = -n;
                RolapMember sibling = null;
                while (n-- > 0) {
                    if (!iter.hasPrevious()) {
                        return (RolapMember) member.getHierarchy()
                            .getNullMember();
                    }
                    sibling = iter.previousMember();
                }
                return sibling;
            }
        }
    }

    @Override
	public void getMemberRange(
        final RolapLevel level,
        final RolapMember startMember,
        final RolapMember endMember,
        final List<RolapMember> list)
    {
        assert startMember != null : "pre";
        assert endMember != null : "pre";
        assert startMember.getLevel() == endMember.getLevel()
            : "pre: startMember.getLevel() == endMember.getLevel()";

        if (compare(startMember, endMember, false) > 0) {
            return;
        }
        list.add(startMember);
        if (startMember.equals(endMember)) {
            return;
        }
        SiblingIterator siblings = new SiblingIterator(this, startMember);
        while (siblings.hasNext()) {
            final RolapMember member = siblings.nextMember();
            list.add(member);
            if (member.equals(endMember)) {
                return;
            }
        }
        throw Util.newInternal(
            new StringBuilder("sibling iterator did not hit end point, start=")
            .append(startMember).append(", end=").append(endMember).toString());
    }

    @Override
	public int getMemberCount() {
        return source.getMemberCount();
    }

    @Override
	public int compare(
        final RolapMember m1,
        final RolapMember m2,
        final boolean siblingsAreEqual)
    {
        if (Objects.equals(m1, m2)) {
            return 0;
        }
        if (Objects.equals(m1.getParentMember(), m2.getParentMember())) {
            // including case where both parents are null
            if (siblingsAreEqual) {
                return 0;
            } else if (m1.getParentMember() == null) {
                // at this point we know that both parent members are null.
                int pos1 = -1, pos2 = -1;
                List<RolapMember> siblingList = getRootMembers();
                for (int i = 0, n = siblingList.size(); i < n; i++) {
                    RolapMember child = siblingList.get(i);
                    if (child.equals(m1)) {
                        pos1 = i;
                    }
                    if (child.equals(m2)) {
                        pos2 = i;
                    }
                }
                if (pos1 == -1) {
                    throw Util.newInternal(new StringBuilder().append(m1).append(" not found among siblings")
                        .toString());
                }
                if (pos2 == -1) {
                    throw Util.newInternal(new StringBuilder().append(m2).append(" not found among siblings")
                        .toString());
                }
                Util.assertTrue(pos1 != pos2);
                return pos1 < pos2 ? -1 : 1;
            } else {
                List<RolapMember> children = new ArrayList<>();
                getMemberChildren(m1.getParentMember(), children);
                int pos1 = -1, pos2 = -1;
                for (int i = 0, n = children.size(); i < n; i++) {
                    RolapMember child = children.get(i);
                    if (child.equals(m1)) {
                        pos1 = i;
                    }
                    if (child.equals(m2)) {
                        pos2 = i;
                    }
                }
                if (pos1 == -1) {
                    throw Util.newInternal(new StringBuilder().append(m1).append(" not found among siblings")
                        .toString());
                }
                if (pos2 == -1) {
                    throw Util.newInternal(new StringBuilder().append(m2).append(" not found among siblings")
                        .toString());
                }
                assert pos1 != pos2;
                return pos1 < pos2 ? -1 : 1;
            }
        }
        int levelDepth1 = m1.getLevel().getDepth();
        int levelDepth2 = m2.getLevel().getDepth();
        if (levelDepth1 < levelDepth2) {
            final int c = compare(m1, m2.getParentMember(), false);
            return (c == 0) ? -1 : c;

        } else if (levelDepth1 > levelDepth2) {
            final int c = compare(m1.getParentMember(), m2, false);
            return (c == 0) ? 1 : c;

        } else {
            return compare(m1.getParentMember(), m2.getParentMember(), false);
        }
    }

    /**
     * SiblingIterator helps traverse a hierarchy of members, by
     * remembering the position at each level. Each SiblingIterator has a
     * parent, to which it defers when the last child of the current member is
     * reached.
     */
    class SiblingIterator {
        private final MemberReader reader;
        private final SiblingIterator parentIterator;
        private List<? extends Member> siblings;
        private int position;

        SiblingIterator(MemberReader reader, RolapMember member) {
            this.reader = reader;
            RolapMember parent = member.getParentMember();
            List<RolapMember> siblingList;
            if (parent == null) {
                siblingList = reader.getRootMembers();
                this.parentIterator = null;
            } else {
                siblingList = new ArrayList<>();
                reader.getMemberChildren(parent, siblingList);
                this.parentIterator = new SiblingIterator(reader, parent);
            }
            this.siblings = siblingList;
            this.position = -1;
            for (int i = 0; i < this.siblings.size(); i++) {
                if (siblings.get(i).equals(member)) {
                    this.position = i;
                    break;
                }
            }
            if (this.position == -1) {
                throw Util.newInternal(
                    new StringBuilder("member ").append(member).append(" not found among its siblings").toString());
            }
        }

        boolean hasNext() {
            return (this.position < this.siblings.size() - 1)
                || (parentIterator != null)
                && parentIterator.hasNext();
        }

        Object next() {
            return nextMember();
        }

        RolapMember nextMember() {
            if (++this.position >= this.siblings.size()) {
                if (parentIterator == null) {
                    throw Util.newInternal("there is no next member");
                }
                RolapMember parent = parentIterator.nextMember();
                List<RolapMember> siblingList = new ArrayList<>();
                reader.getMemberChildren(parent, siblingList);
                this.siblings = siblingList;
                this.position = 0;
            }
            return (RolapMember) this.siblings.get(this.position);
        }

        boolean hasPrevious() {
            return (this.position > 0)
                || (parentIterator != null)
                && parentIterator.hasPrevious();
        }

        Object previous() {
            return previousMember();
        }

        RolapMember previousMember() {
            if (--this.position < 0) {
                if (parentIterator == null) {
                    throw Util.newInternal("there is no next member");
                }
                RolapMember parent = parentIterator.previousMember();
                List<RolapMember> siblingList = new ArrayList<>();
                reader.getMemberChildren(parent, siblingList);
                this.siblings = siblingList;
                this.position = this.siblings.size() - 1;
            }
            return (RolapMember) this.siblings.get(this.position);
        }
    }

    @Override
	public MemberBuilder getMemberBuilder() {
        return source.getMemberBuilder();
    }

    @Override
	public RolapMember getDefaultMember() {
        RolapMember defaultMember =
            (RolapMember) getHierarchy().getDefaultMember();
        if (defaultMember != null) {
            return defaultMember;
        }
        return getRootMembers().get(0);
    }

    @Override
	public int getLevelMemberCount(RolapLevel level) {
        // No need to cache the result: the caller saves the result by calling
        // RolapLevel.setApproxRowCount
        return source.getLevelMemberCount(level);
    }

    @Override
	public RolapMember desubstitute(RolapMember member) {
        return member;
    }

    @Override
	public RolapMember substitute(RolapMember member) {
        return member;
    }

    @Override
	public RolapMember getMemberParent(RolapMember member) {
        // This method deals with ragged hierarchies but not access-controlled
        // hierarchies - assume these have RestrictedMemberReader possibly
        // wrapped in a SubstitutingMemberReader.
        RolapMember parentMember = member.getParentMember();
        // Skip over hidden parents.
        while (parentMember != null && parentMember.isHidden()) {
            parentMember = parentMember.getParentMember();
        }
        return parentMember;
    }

    /**
     * Reads the children of member into result.
     *
     * @param result Children are written here, in order
     * @param members Members whose children to read
     * @param constraint restricts the returned members if possible (optional
     *             optimization)
     */
    protected void readMemberChildren(
        List<RolapMember> members,
        List<RolapMember> result,
        MemberChildrenConstraint constraint)
    {
        List<RolapMember> children = new ConcatenableList<>();
        source.getMemberChildren(members, children, constraint);
        // Put them in a temporary hash table first. Register them later, when
        // we know their size (hence their 'cost' to the cache pool).
        Map<RolapMember, List<RolapMember>> tempMap =
            new HashMap<>();
        for (RolapMember member1 : members) {
            //noinspection unchecked
            tempMap.put(member1, Collections.EMPTY_LIST);
        }
        for (final RolapMember child : children) {
            // todo: We could optimize here. If members.length is small, it's
            // more efficient to drive from members, rather than hashing
            // children.length times. We could also exploit the fact that the
            // result is sorted by ordinal and therefore, unless the "members"
            // contains members from different levels, children of the same
            // member will be contiguous.
            assert child != null : "child";
            final RolapMember parentMember = child.getParentMember();
            List<RolapMember> list = tempMap.get(parentMember);
            if (list == null) {
                // The list is null if, due to dropped constraints, we now
                // have a children list of a member we didn't explicitly
                // ask for it. Adding it to the cache would be viable, but
                // let's ignore it.
                continue;
            } else if (list == Collections.EMPTY_LIST) {
                list = new ArrayList<>();
                tempMap.put(parentMember, list);
            }
            list.add(child);
            result.add(child);
        }
    }
}
