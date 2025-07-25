/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * jhyde, 22 December, 2001
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

import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;

/**
 * A MemberCache can retrieve members based upon their parent and
 * name.
 *
 * @author jhyde
 * @since 22 December, 2001
 */
public interface MemberCache {
    /**
     * Creates a key with which to {@link #getMember(Object)} or
     * {@link #putMember(Object, RolapMember)} the
     * {@link RolapMember} with a given parent and key.
     *
     * @param parent Parent member
     * @param key Key of member within parent
     * @return key with which to address this member in the cache
     */
    Object makeKey(RolapMember parent, Object key);

    /**
     * Retrieves the {@link RolapMember} with a given key.
     *
     * @param key cache key, created by {@link #makeKey}
     * @return member with a given cache key
     */
    RolapMember getMember(Object key);

    /**
     * Retrieves the {@link RolapMember} with a given key.
     *
     * @param key cache key, created by {@link #makeKey}
     * @param mustCheckCacheStatus If {@code true}, do not check cache status
     * @return member with a given cache key
     */
    RolapMember getMember(Object key, boolean mustCheckCacheStatus);

    /**
     * Replaces the {@link RolapMember} with a given key and returns the
     * previous member if any.
     *
     * @param key cache key, created by {@link #makeKey}
     * @param member new member
     * @return Previous member with that key, or null.
     */
    Object putMember(Object key, RolapMember member);

    /**
     * Returns whether the cache supports removing selected items. If it does,
     * it is valid to call the {@link #removeMember(Object)} and
     * {@link #removeMemberAndDescendants(Object)} methods.
     *
     * REVIEW: remove isMutable and move removeMember and
     * removeMemberAndDescendants to new interface MutableMemberCache
     *
     * @return true if the cache supports removing selected items.
     */
    boolean isMutable();

    /**
     * Removes the {@link RolapMember} with a given key from the cache.
     * Returns the previous member with that key, or null.
     * Optional operation: see {@link #isMutable}.
     *
     * @param key cache key, created by {@link #makeKey}
     * @return previous member with that key, or null
     */
    RolapMember removeMember(Object key);

    /**
     * Removes the designated {@link RolapMember} and all its descendants.
     * Returns the previous member with that key, or null.
     * Optional operation: see {@link #isMutable}.
     *
     * @param key cache key, created by {@link #makeKey}
     * @return previous member with that key, or null
     */
    RolapMember removeMemberAndDescendants(Object key);

    /**
     * Returns the children of member if they are currently in the
     * cache, otherwise returns null.
     *
     * The children may be garbage collected as
     * soon as the returned list may be garbage collected.
     *
     * @param parent the parent member
     * @param constraint the condition that was used when the members were
     *    fetched. May be null for all members (no constraint)
     * @return list of children, or null if not in cache
     */
    List<RolapMember> getChildrenFromCache(
        RolapMember parent,
        MemberChildrenConstraint constraint);

    /**
     * Returns the members of level if they are currently in the
     * cache, otherwise returns null.
     *
     * The members may be garbage collected as soon as the
     * returned list may be garbage collected.
     *
     * @param level the level whose members should be fetched
     * @param constraint the condition that was used when the members were
     *   fetched. May be null for all members (no constraint)
     * @return members of level, or null if not in cache
     */
    List<RolapMember> getLevelMembersFromCache(
        RolapLevel level,
        TupleConstraint constraint);

    /**
     * Registers that the children of member are
     * children (a list of {@link RolapMember}s).
     *
     * @param member the parent member
     * @param constraint the condition that was used when the members were
     *   fetched. May be null for all members (no constraint)
     * @param children list of children
     */
    void putChildren(
        RolapMember member,
        MemberChildrenConstraint constraint,
        List<RolapMember> children);

    /**
     * Registers that the children of level are
     * children (a list of {@link RolapMember}s).
     *
     * @param level the parent level
     * @param constraint the condition that was used when the members were
     *   fetched. May be null for all members (no constraint)
     * @param children list of children
     */
    void putChildren(
        RolapLevel level,
        TupleConstraint constraint,
        List<RolapMember> children);
}


// End MemberCache.java
