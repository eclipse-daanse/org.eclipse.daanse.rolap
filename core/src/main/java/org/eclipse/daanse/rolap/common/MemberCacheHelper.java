/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeSet;

import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.cache.SmartCache;
import org.eclipse.daanse.rolap.common.cache.SoftSmartCache;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;

/**
 * Encapsulation of member caching.
 *
 * @author Will Gorman
 */
public class MemberCacheHelper implements MemberCache {

    private final SqlConstraintFactory sqlConstraintFactory =
        SqlConstraintFactory.instance();

    /** maps a parent member and constraint to a list of its children */
    public final SmartMemberListCache<RolapMember, List<RolapMember>>
        mapMemberToChildren;

    /** maps a parent member to the collection of named children that have
     * been cached.  The collection can grow over time as new children are
     * loaded.
     */
    public final SmartIncrementalCache<RolapMember, Collection<RolapMember>>
        mapParentToNamedChildren;

    /** a cache for all members to ensure uniqueness */
    public SmartCache<Object, RolapMember> mapKeyToMember;
    RolapHierarchy rolapHierarchy;

    /** maps a level to its members */
    public final SmartMemberListCache<RolapLevel, List<RolapMember>>
        mapLevelToMembers;

    /**
     * Creates a MemberCacheHelper.
     *
     * @param rolapHierarchy Hierarchy
     */
    public MemberCacheHelper(RolapHierarchy rolapHierarchy) {
        this.rolapHierarchy = rolapHierarchy;
        this.mapLevelToMembers =
            new SmartMemberListCache<>();
        this.mapKeyToMember =
            new SoftSmartCache<>();
        this.mapMemberToChildren =
            new SmartMemberListCache<>();
        this.mapParentToNamedChildren =
            new SmartIncrementalCache<>();

    }

    @Override
	public RolapMember getMember(
        Object key,
        boolean mustCheckCacheStatus)
    {
        return mapKeyToMember.get(key);
    }


    // implement MemberCache
    @Override
	public Object putMember(Object key, RolapMember value) {
        return mapKeyToMember.put(key, value);
    }

    // implement MemberCache
    @Override
	public Object makeKey(RolapMember parent, Object key) {
        return new MemberKey(parent, key);
    }

    // implement MemberCache
    @Override
	public RolapMember getMember(Object key) {
        return getMember(key, true);
    }



    /**
     * Deprecated in favor of
     * {@link #putChildren(RolapLevel, TupleConstraint, List)}
     */
    @Deprecated
    public void putLevelMembersInCache(
        RolapLevel level,
        TupleConstraint constraint,
        List<RolapMember> members)
    {
        putChildren(level, constraint, members);
    }

    @Override
	public void putChildren(
        RolapLevel level,
        TupleConstraint constraint,
        List<RolapMember> members)
    {
        mapLevelToMembers.put(level, constraint, members);
    }

    @Override
	public List<RolapMember> getChildrenFromCache(
        RolapMember member,
        MemberChildrenConstraint constraint)
    {
        if (constraint == null) {
            constraint =
                sqlConstraintFactory.getMemberChildrenConstraint(null);
        }
        if (constraint instanceof ChildByNameConstraint childByNameConstraint) {
            return findNamedChildrenInCache(
                member, childByNameConstraint.getChildNames());
        }
        return mapMemberToChildren.get(member, constraint);
    }

    /**
     * Attempts to find all children requested by the ChildByNameConstraint
     * in cache.  Returns null if the complete list is not found.
     */
    private List<RolapMember> findNamedChildrenInCache(
        final RolapMember parent, final List<String> childNames)
    {
        List<RolapMember> children =
            checkDefaultAndNamedChildrenCache(parent);
        if (children == null || childNames == null
            || childNames.size() > children.size())
        {
            return null;
        }

        children=    children.stream().filter(rolapMember->childNames.contains(
        		rolapMember.getName())).toList();

        boolean foundAll = children.size() == childNames.size();
        return !foundAll ? null : children;
    }

    private List<RolapMember> checkDefaultAndNamedChildrenCache(
        RolapMember parent)
    {
        Collection<RolapMember> children = mapMemberToChildren
            .get(parent, DefaultMemberChildrenConstraint.instance());
        if (children == null) {
            children = mapParentToNamedChildren.get(parent);
        }
        return children == null ? Collections.emptyList()
            : new ArrayList(children);
    }


    @Override
	public void putChildren(
        RolapMember member,
        MemberChildrenConstraint constraint,
        List<RolapMember> children)
    {
        if (constraint == null) {
            constraint =
                sqlConstraintFactory.getMemberChildrenConstraint(null);
        }
        if (constraint instanceof ChildByNameConstraint) {
            putChildrenInChildNameCache(member, children);
        } else {
            mapMemberToChildren.put(member, constraint, children);
        }
    }

    private void putChildrenInChildNameCache(
        final RolapMember parent,
        final List<RolapMember> children)
    {
        if (children == null || children.isEmpty()) {
            return;
        }
        Collection<RolapMember> cachedChildren =
            mapParentToNamedChildren.get(parent);
        if (cachedChildren == null) {
            // initialize with a sorted set
            mapParentToNamedChildren.put(
                parent, new TreeSet<>(children));
        } else {
            mapParentToNamedChildren.addToEntry(parent, children);
        }
    }

    @Override
	public List<RolapMember> getLevelMembersFromCache(
        RolapLevel level,
        TupleConstraint constraint)
    {
        if (constraint == null) {
            constraint = sqlConstraintFactory.getLevelMembersConstraint(null);
        }
        return mapLevelToMembers.get(level, constraint);
    }

    // Must sync here because we want the three maps to be modified together.
    public synchronized void flushCache() {
        mapMemberToChildren.clear();
        mapKeyToMember.clear();
        mapLevelToMembers.clear();
        mapParentToNamedChildren.clear();

        // We also need (why?) to clear the approxRowCount of each level.
        // But it leads to losing of approxRowCount value from schema
//        for (Level level : rolapHierarchy.getLevels()) {
//            ((RolapLevel)level).setApproxRowCount(Integer.MIN_VALUE);
//        }
    }


    @Override
	public boolean isMutable()
    {
        return true;
    }

    @Override
	public synchronized RolapMember removeMember(Object key)
    {
        // Flush entries from the level-to-members map
        // for member's level and all child levels.
        // Important: Do this even if the member is apparently not in the cache.
        RolapLevel level = ((MemberKey) key).getLevel();
        if (level == null) {
            level = (RolapLevel) this.rolapHierarchy.getLevels().getFirst();
        }
        final RolapLevel levelRef = level;
        mapLevelToMembers.getCache().execute(
            new SmartCache.SmartCacheTask
                <Pair<RolapLevel, Object>, List<RolapMember>>()
            {
                @Override
				public void execute(
                    Iterator<Entry<Pair
                        <RolapLevel, Object>, List<RolapMember>>> iterator)
                {
                    while (iterator.hasNext()) {
                        Map.Entry<Pair
                            <RolapLevel, Object>, List<RolapMember>> entry =
                            iterator.next();
                        final RolapLevel cacheLevel = entry.getKey().left;
                        if (cacheLevel.equalsOlapElement(levelRef)
                            || (cacheLevel.getHierarchy()
                            .equalsOlapElement(levelRef.getHierarchy())
                            && cacheLevel.getDepth()
                            >= levelRef.getDepth()))
                        {
                            iterator.remove();
                        }
                    }
                }
            });

        final RolapMember member = getMember(key);
        if (member == null) {
            // not in cache
            return null;
        }

        // Drop member from the member-to-children map, wherever it occurs as
        // a parent or as a child, regardless of the constraint.
        final RolapMember parent = member.getParentMember();
        mapMemberToChildren.cache.execute(
            new SmartCache.SmartCacheTask
                <Pair<RolapMember, Object>, List<RolapMember>>()
            {
                @Override
				public void execute(
                    Iterator<Entry
                        <Pair<RolapMember, Object>, List<RolapMember>>> iter)
                {
                    while (iter.hasNext()) {
                        Map.Entry<Pair
                            <RolapMember, Object>, List<RolapMember>> entry =
                                iter.next();
                        final RolapMember member1 = entry.getKey().left;
                        final Object constraint = entry.getKey().right;

                        // Cache key is (member's parent, constraint);
                        // cache value is a list of member's siblings;
                        // If constraint is trivial remove member from list
                        // of siblings; otherwise it's safer to nuke the cache
                        // entry
                        if (Objects.equals(member1, parent)) {
                            if (constraint
                                == DefaultMemberChildrenConstraint.instance())
                            {
                                List<RolapMember> siblings = entry.getValue();
                                boolean removedIt = siblings.remove(member);
//                                discard(removedIt);
                            } else {
                                iter.remove();
                            }
                        }

                        // cache is (member, some constraint);
                        // cache value is list of member's children;
                        // remove cache entry
                        if (Objects.equals(member1, member)) {
                            iter.remove();
                        }
                    }
                }
            });

        mapParentToNamedChildren.getCache().execute(
            new SmartCache.SmartCacheTask<RolapMember,
                Collection<RolapMember>>()
            {
            @Override
			public void execute(
                Iterator<Entry<RolapMember, Collection<RolapMember>>> iterator)
            {
                while (iterator.hasNext()) {
                    Entry<RolapMember, Collection<RolapMember>> entry =
                        iterator.next();
                    RolapMember currentMember = entry.getKey();
                    if (member.equals(currentMember)) {
                        iterator.remove();
                    } else if (parent.equals(currentMember)) {
                        entry.getValue().remove(member);
                    }
                }
            } });
            // drop it from the lookup-cache
            return mapKeyToMember.put(key, null);
        }

    @Override
	public RolapMember removeMemberAndDescendants(Object key) {
        // Can use mapMemberToChildren recursively. No need to update inferior
        // lists of children. Do need to update inferior lists of level-peers.
        return null; // STUB
    }
}

// End MemberCacheHelper.java

