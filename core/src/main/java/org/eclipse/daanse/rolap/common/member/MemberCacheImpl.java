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

package org.eclipse.daanse.rolap.common.member;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeSet;

import org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.cache.BoundedCache;
import org.eclipse.daanse.olap.cache.SimpleCache;
import org.eclipse.daanse.rolap.common.cache.SoftValueCache;
import org.eclipse.daanse.rolap.common.constraint.ChildByNameConstraint;
import org.eclipse.daanse.rolap.common.constraint.DefaultMemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.SqlConstraintFactory;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.rolap.element.RolapLevel;

/**
 * The four member maps of one hierarchy - member by key (soft values:
 * the canonical identity store, equal keys MUST resolve to one
 * instance), children by (member, constraint), named children, and
 * level members by (level, constraint).
 *
 * Threading and fencing: reads are lock-free; {@code flushCache} and
 * {@code removeMember} are synchronized and bump the load-registry
 * generation. EVERY list write is gated on
 * {@code loadRegistry.threadWritesAllowed()} - a writer whose load a
 * flush overtook is silently discarded (correct: it would resurrect
 * flushed members), and {@code putMember} reads the existing mapping
 * first so canonicalization survives the closed fence. flushCache also
 * resets the levels' measured row counts - a deliberate side effect
 * beyond the maps.
 */
public class MemberCacheImpl implements MemberCache {

    private final SqlConstraintFactory sqlConstraintFactory =
        SqlConstraintFactory.instance();

    /**
     * Config-independent, stable ordering for named-children sets.
     * RolapMember's NATURAL ordering depends on the thread-scoped
     * ExecutionConfig (caseSensitive): a TreeSet built under a bound query
     * thread and searched on an unbound flush thread used a different
     * comparator over a verbatim-copied tree - the search missed and a
     * deleted member survived the flush. uniqueName ordering is stable,
     * transitive, and class-agnostic (mixed-key-class members included).
     */
    private static final Comparator<RolapMember> NAMED_CHILDREN_ORDER =
        Comparator.comparing(RolapMember::getUniqueName)
            // INJECTIVE tiebreak: with a nameColumn, two siblings with
            // distinct keys share a uniqueName - a comparator collapsing
            // them made findNamedChildrenInCache report a HIT with one
            // member where it previously (correctly) missed. The key CLASS
            // separates renderings that collide across types (Integer 1 vs
            // "1", null vs "null" - divergent load paths can type the same
            // key column differently); within one class the value string is
            // stable and injective-enough that a remaining collapse is a
            // true duplicate. Deliberately NOT identityHashCode: equal keys
            // MUST collapse.
            .thenComparing(member -> member.getKey() == null
                ? "" : member.getKey().getClass().getName())
            .thenComparing(member -> String.valueOf(member.getKey()));

    private static TreeSet<RolapMember> namedChildrenSet(Collection<RolapMember> members) {
        TreeSet<RolapMember> set = new TreeSet<>(NAMED_CHILDREN_ORDER);
        set.addAll(members);
        return set;
    }

    /** maps a parent member and constraint to a list of its children */
    public final MemberListCache<RolapMember, List<RolapMember>>
        mapMemberToChildren;

    /** maps a parent member to the collection of named children that have
     * been cached.  The collection can grow over time as new children are
     * loaded.
     */
    public final IncrementalCollectionCache<RolapMember, Collection<RolapMember>>
        mapParentToNamedChildren;

    /** a cache for all members to ensure uniqueness */
    public final SimpleCache<Object, RolapMember> mapKeyToMember;
    final RolapHierarchy rolapHierarchy;

    /** fences list writes against flushes racing an in-flight load */
    private MemberLoadRegistry loadRegistry;

    /** maps a level to its members */
    public final MemberListCache<RolapLevel, List<RolapMember>>
        mapLevelToMembers;

    /**
     * Creates a MemberCacheImpl.
     *
     * @param rolapHierarchy Hierarchy
     */
    public MemberCacheImpl(RolapHierarchy rolapHierarchy) {
        this.rolapHierarchy = rolapHierarchy;
        long maxWeight = memberListCacheMaxWeight(rolapHierarchy);
        // all three list caches are weight-bounded against the same cap: an
        // entry weighs its member count (children lists one extra for the
        // entry itself, named-children entries their name-set size)
        this.mapLevelToMembers =
            new MemberListCache<>(BoundedCache.weighted(maxWeight, (k, v) -> v.size()));
        this.mapMemberToChildren =
            new MemberListCache<>(BoundedCache.weighted(maxWeight, (k, v) -> 1L + v.size()));
        this.mapParentToNamedChildren =
            new IncrementalCollectionCache<>(
                BoundedCache.weighted(maxWeight, (k, v) -> Math.max(1, v.size())),
                MemberCacheImpl::namedChildrenSet);
        // strong keys, soft values: guarantees one object per member for as
        // long as the member is reachable, sheds under memory pressure
        this.mapKeyToMember =
            new SoftValueCache<>();
    }

    /** Wires the registry whose generation fences this cache's list writes. */
    public void setLoadRegistry(MemberLoadRegistry loadRegistry) {
        this.loadRegistry = loadRegistry;
    }

    private boolean listWritesAllowed() {
        return loadRegistry == null || loadRegistry.threadWritesAllowed();
    }

    private static long memberListCacheMaxWeight(RolapHierarchy hierarchy) {
        try {
            return hierarchy.getRolapCatalog().getInternalConnection()
                .getContext().getConfig().memberListCacheMaxWeight();
        } catch (RuntimeException e) {
            // hierarchies built before the internal connection exists fall
            // back to the default cap
            return ConfigConstants.MEMBER_LIST_CACHE_MAX_WEIGHT_DEFAULT_VALUE;
        }
    }

    @Override
	public RolapMember getMember(Object key) {
        return mapKeyToMember.get(key);
    }

    // implement MemberCache
    @Override
	public RolapMember putMember(Object key, RolapMember value) {
        if (!listWritesAllowed()) {
            // same fence as putChildren: a load overtaken by a flush must
            // not re-seed the identity of a just-removed member. But keep
            // canonicalization where possible: a member cached BEFORE the
            // fence closed stays the identity - returning the fresh copy
            // here handed callers a second object for the same member.
            RolapMember existing = mapKeyToMember.get(key);
            return existing != null ? existing : value;
        }
        RolapMember existing = mapKeyToMember.putIfAbsent(key, value);
        return existing != null ? existing : value;
    }

    // implement MemberCache
    @Override
	public Object makeKey(RolapMember parent, Object key) {
        return new MemberKeyR(parent, key);
    }


    @Override
	public void putChildren(
        RolapLevel level,
        TupleConstraint constraint,
        List<RolapMember> members)
    {
        if (!listWritesAllowed()) {
            return;
        }
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
        Collection<RolapMember> children = mapMemberToChildren
            .get(parent, DefaultMemberChildrenConstraint.instance());
        if (children == null) {
            children = mapParentToNamedChildren.get(parent);
        }
        if (children == null || childNames == null
            || childNames.size() > children.size())
        {
            return null;
        }
        // set-based filter over the live cached collection; the filtered
        // result is a fresh list, so the cache entry never leaks out
        final Set<String> names = new HashSet<>(childNames);
        final List<RolapMember> found = children.stream()
            .filter(rolapMember -> names.contains(rolapMember.getName()))
            .toList();
        // completeness needs BOTH checks: the count match (same-named
        // siblings inflate the hit list - a one-name lookup over two "John
        // Smith"s must reload, see the round-5 pins) AND name coverage (two
        // same-named hits otherwise mask a missing third name and the warm
        // cache serves a wrong "complete" hit)
        final Set<String> foundNames = new HashSet<>();
        for (RolapMember member : found) {
            foundNames.add(member.getName());
        }
        return found.size() == childNames.size() && foundNames.containsAll(names)
            ? found : null;
    }

    @Override
	public void putChildren(
        RolapMember member,
        MemberChildrenConstraint constraint,
        List<RolapMember> children)
    {
        if (!listWritesAllowed()) {
            return;
        }
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
        // always a sorted set: addToEntry may race the get and would
        // otherwise install the raw list, accumulating duplicates
        mapParentToNamedChildren.addToEntry(parent, namedChildrenSet(children));
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
    @Override
    public synchronized void flushCache() {
        if (loadRegistry != null) {
            loadRegistry.bumpGeneration();
        }
        mapMemberToChildren.clear();
        mapKeyToMember.clear();
        mapLevelToMembers.clear();
        mapParentToNamedChildren.clear();

        // a member flush invalidates measured cardinalities; the
        // mapping-declared approxRowCount survives
        for (Level level : rolapHierarchy.getLevels()) {
            if (level instanceof RolapLevel rolapLevel) {
                rolapLevel.resetMeasuredRowCount();
            }
        }
    }

    @Override
	public synchronized RolapMember removeMember(Object key)
    {
        if (loadRegistry != null) {
            loadRegistry.bumpGeneration();
        }
        // Flush entries from the level-to-members map
        // for member's level and all child levels.
        // Important: Do this even if the member is apparently not in the cache.
        RolapLevel level = ((MemberKeyR) key).getLevel();
        if (level == null) {
            level = (RolapLevel) this.rolapHierarchy.getLevels().getFirst();
        }
        pruneLevelMembers(level);

        final RolapMember member = getMember(key);
        if (member == null) {
            // not in cache
            return null;
        }

        final RolapMember parent = member.getParentMember();
        pruneMemberChildren(member, parent);
        pruneNamedChildren(member, parent);

        // drop it from the lookup-cache
        return mapKeyToMember.remove(key);
    }

    /**
     * Drops every level-members entry of the member's level and below.
     * Both sides are compared through their SHARED level: the flush key
     * always carries a plain RolapLevel (stripped shared member), while a
     * cube member cache keys by RolapCubeLevel - and equalsOlapElement
     * requires class equality, so the direct comparison never matched and
     * cube-level lists survived every member flush.
     */
    private void pruneLevelMembers(final RolapLevel level) {
        final RolapLevel target = sharedLevel(level);
        mapLevelToMembers.getCache().execute(iterator -> {
            while (iterator.hasNext()) {
                Map.Entry<Pair<RolapLevel, Object>, List<RolapMember>> entry =
                    iterator.next();
                final RolapLevel cacheLevel = sharedLevel(entry.getKey().left);
                if (cacheLevel.equalsOlapElement(target)
                    || (sharedHierarchyOf(cacheLevel)
                    .equalsOlapElement(sharedHierarchyOf(target))
                    && cacheLevel.getDepth()
                    >= target.getDepth()))
                {
                    iterator.remove();
                }
            }
        });
    }

    private static RolapLevel sharedLevel(RolapLevel level) {
        return level instanceof RolapCubeLevel cubeLevel
            ? cubeLevel.getRolapLevel()
            : level;
    }

    private static RolapHierarchy sharedHierarchyOf(RolapLevel level) {
        RolapHierarchy hierarchy = level.getHierarchy();
        return hierarchy instanceof RolapCubeHierarchy cubeHierarchy
            ? cubeHierarchy.getRolapHierarchy()
            : hierarchy;
    }

    /**
     * Drops the member from the member-to-children map, wherever it occurs
     * as a parent or as a child, regardless of the constraint.
     */
    private void pruneMemberChildren(final RolapMember member, final RolapMember parent) {
        mapMemberToChildren.getCache().execute(iter -> {
            while (iter.hasNext()) {
                Map.Entry<Pair<RolapMember, Object>, List<RolapMember>> entry =
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
                        // replace, never mutate: readers iterate
                        // the cached list without a lock, and the
                        // rewrite re-weighs the entry. CAS against
                        // the iterated snapshot: an unfenced
                        // query-side putChildren may refresh the
                        // entry mid-iteration, and an unconditional
                        // setValue clobbered its fresh list with
                        // this stale shrink - on a lost race, drop
                        // the entry and let the next read reload.
                        List<RolapMember> siblings =
                            new ArrayList<>(entry.getValue());
                        if (siblings.remove(member)
                            && !mapMemberToChildren.getCache().replace(
                                entry.getKey(),
                                entry.getValue(),
                                Collections.unmodifiableList(siblings))) {
                            iter.remove();
                        }
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
        });
    }

    /** Drops the member's own entry and shrinks its parent's entry. */
    private void pruneNamedChildren(final RolapMember member, final RolapMember parent) {
        mapParentToNamedChildren.getCache().execute(iterator -> {
            while (iterator.hasNext()) {
                Entry<RolapMember, Collection<RolapMember>> entry =
                    iterator.next();
                RolapMember currentMember = entry.getKey();
                if (member.equals(currentMember)) {
                    iterator.remove();
                } else if (Objects.equals(parent, currentMember)) {
                    // replace, never mutate (unlocked readers); CAS
                    // against the iterated snapshot - lost race means
                    // a concurrent writer refreshed the entry, drop it.
                    // The copy keeps the sorted-set invariant under the
                    // stable comparator; removal is equals-based so it
                    // never depends on any ordering at all.
                    Collection<RolapMember> reduced =
                        namedChildrenSet(entry.getValue());
                    if (reduced.removeIf(member::equals)
                        && !mapParentToNamedChildren.getCache().replace(
                            entry.getKey(), entry.getValue(), reduced)) {
                        iterator.remove();
                    }
                }
            }
        });
    }

}

// End MemberCacheImpl.java

