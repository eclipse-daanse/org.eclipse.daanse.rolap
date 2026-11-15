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
 * jhyde, 21 December, 2001
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

import java.util.List;

import org.eclipse.daanse.olap.api.query.Segment;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;

/**
 * A <code>MemberSource</code> has the basic operations to read the members of a
 * hierarchy: the root members, a member's children, a range on a level.
 *
 * <p>A source may also support writeback to a cache; the writeback methods
 * document that contract. The engine builds its sources itself
 * ({@link SqlMemberSource} wrapped in a caching or non-caching reader) -
 * the historical mechanism of naming an implementation class in the
 * mapping is not supported.
 */
public interface MemberSource {
    /**
     * Returns the hierarchy that this source is reading for.
     */
    RolapHierarchy getHierarchy();
    /**
     * Sets the cache which this MemberSource will write to.
     *
     * Cache-writeback is optional (for example, {@link SqlMemberSource}
     * supports it, and {@link ArrayMemberSource} does not), and the return
     * value from this method indicates whether this object supports it.
     *
     * If this method returns true, the {@link #getMembers},
     * {@link #getRootMembers} and {@link #getMemberChildren} methods must
     * write to the cache, in addition to returning members as a return value.
     *
     * @param cache The MemberCache which the caller would like
     *   this MemberSource to write to.
     * @return Whether this MemberSource supports cache-writeback.
     */
    boolean setCache(MemberCache cache);
    /**
     * Returns all members of this hierarchy, sorted by ordinal.
     *
     * If this object {@link #setCache supports cache-writeback}, also
     * writes these members to the cache.
     */
    List<RolapMember> getMembers();
    /**
     * Returns all members of this hierarchy which do not have a parent,
     * sorted by ordinal.
     *
     * If this object {@link #setCache supports cache-writeback}, also
     * writes these members to the cache.
     *
     * @return {@link List} of {@link RolapMember}s
     */
    List<RolapMember> getRootMembers();

    /**
     * Writes all children parentMember to children.
     *
     * If this object {@link #setCache supports cache-writeback}, also
     * writes these members to the cache.
     */
    void getMemberChildren(
        RolapMember parentMember,
        List<RolapMember> children);

    /**
     * Returns all members which are a child of one of the members in
     * parentMembers, sorted by ordinal.
     *
     * If this object {@link #setCache supports cache-writeback}, also
     * writes these members to the cache.
     */
    void getMemberChildren(
        List<RolapMember> parentMembers,
        List<RolapMember> children);

    /**
     * Returns an estimate of number of members in this hierarchy.
     */
    int getMemberCount();

    /**
     * Finds a member based upon its unique name.
     */
    RolapMember lookupMember(
        List<Segment> uniqueNameParts,
        boolean failIfNotFound);
}
