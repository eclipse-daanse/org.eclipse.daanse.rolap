/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy.LimitedRollupMember;

/**
 * A {@link SmartRestrictedMemberReader} is a subclass of
 * {@link RestrictedMemberReader} which caches the access rights
 * per children's list. We place them in this throw-away object
 * to speed up partial rollup calculations.
 *
 * The speed improvement is noticeable when dealing with very
 * big dimensions with a lot of branches (like a parent-child
 * hierarchy) because the 'partial' rollup policy forces us to
 * navigate the tree and find the lowest level to rollup to and
 * then figure out all of the children on which to constraint
 * the SQL query.
 */
public class SmartRestrictedMemberReader extends RestrictedMemberReader {

    public SmartRestrictedMemberReader(
        final MemberReader memberReader,
        final Role role)
    {
        // We want to extend a RestrictedMemberReader with access details
        // that we cache.
        super(memberReader, role);
    }

    // Our little ad-hoc cache.
    final Map<RolapMember, AccessAwareMemberList>
        memberToChildren =
            new WeakHashMap<>();

    // The lock for cache access.
    final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public Map<? extends Member, AccessMember> getMemberChildren(
        RolapMember member,
        List<RolapMember> children,
        MemberChildrenConstraint constraint)
    {
        // Strip off the rollup wrapper.
        if (member instanceof LimitedRollupMember) {
            member = ((LimitedRollupMember)member).member;
        }
        try {
            // Get the read lock.
            lock.readLock().lock();

            AccessAwareMemberList memberList =
                memberToChildren.get(member);

            if (memberList != null) {
                // Sadly, we need to do a hard cast here,
                // but since we know what it is, it's fine.
                children.addAll(
                    memberList.children);

                return memberList.accessMap;
            }
        } finally {
            lock.readLock().unlock();
        }

        // No cache data.
        try {
            // Get a write lock.
            lock.writeLock().lock();

            Map<? extends Member, AccessMember> membersWithAccessDetails =
                super.getMemberChildren(
                    member,
                    children,
                    constraint);

            memberToChildren.put(
                member,
                new AccessAwareMemberList(
                    membersWithAccessDetails,
                    new ArrayList(membersWithAccessDetails.keySet())));

            return membersWithAccessDetails;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static class AccessAwareMemberList {
        private final Map<? extends Member, AccessMember> accessMap;
        private final Collection<RolapMember> children;
        public AccessAwareMemberList(
            Map<? extends Member, AccessMember> accessMap,
            Collection<RolapMember> children)
        {
            this.accessMap = accessMap;
            this.children = children;
        }
    }
}
