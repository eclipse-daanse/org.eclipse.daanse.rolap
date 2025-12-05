/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2018 Hitachi Vantara
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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.access.RoleImpl;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.HierarchyAccess;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.MultiCardinalityDefaultMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;


/**
 * A RestrictedMemberReader reads only the members of a hierarchy
 * allowed by a role's access profile.
 *
 * @author jhyde
 * @since Feb 26, 2003
 */
public class RestrictedMemberReader extends DelegatingMemberReader {

    private final HierarchyAccess hierarchyAccess;
    private final boolean ragged;
    private final SqlConstraintFactory sqlConstraintFactory =
        SqlConstraintFactory.instance();
    final Role role;
    private final static String hierarchyHasNoAccessibleMembers = "Hierarchy ''{0}'' has no accessible members.";

    /**
     * Creates a RestrictedMemberReader.
     *
     * There's no filtering to be done unless
     * either the role has restrictions on this hierarchy,
     * or the hierarchy is ragged; there's a pre-condition to this effect.
     *
     * @param memberReader Underlying (presumably unrestricted) member reader
     * @param role Role whose access profile to obey. The role must have
     *   restrictions on this hierarchy
     *  role.getAccessDetails(memberReader.getHierarchy()) != null ||
     *   memberReader.getHierarchy().isRagged()
     */
    public RestrictedMemberReader(MemberReader memberReader, Role role) {
        super(memberReader);
        this.role = role;
        RolapHierarchy hierarchy = memberReader.getHierarchy();
        ragged = hierarchy.isRagged();
        if (role.getAccessDetails(hierarchy) == null) {
            assert ragged;
            hierarchyAccess = RoleImpl.createAllAccess(hierarchy);
        } else {
            hierarchyAccess = role.getAccessDetails(hierarchy);
        }
    }

    @Override
	public boolean setCache(MemberCache cache) {
        // Don't support cache-writeback. It would confuse the cache!
        return false;
    }

    @Override
	public RolapMember getLeadMember(RolapMember member, int n) {
        int i = 0;
        int increment = 1;
        if (n < 0) {
            increment = -1;
            n = -n;
        }
        while (i < n) {
            member = memberReader.getLeadMember(member, increment);
            if (member.isNull()) {
                return member;
            }
            if (canSee(member)) {
                ++i;
            }
        }
        return member;
    }

    @Override
	public void getMemberChildren(
        RolapMember parentMember,
        List<RolapMember> children)
    {
        MemberChildrenConstraint constraint =
            sqlConstraintFactory.getMemberChildrenConstraint(null);
        getMemberChildren(parentMember, children, constraint);
    }

    @Override
	public Map<? extends Member, AccessMember> getMemberChildren(
        RolapMember parentMember,
        List<RolapMember> children,
        MemberChildrenConstraint constraint)
    {
        List<RolapMember> fullChildren = new ArrayList<>();
        memberReader.getMemberChildren
          (parentMember, fullChildren, constraint);
        return processMemberChildren(fullChildren, children, constraint);
    }

    @Override
	public void getMemberChildren(
        List<RolapMember> parentMembers,
        List<RolapMember> children)
    {
        MemberChildrenConstraint constraint =
            sqlConstraintFactory.getMemberChildrenConstraint(null);
        getMemberChildren(parentMembers, children, constraint);
    }

    @Override
	public Map<? extends Member, AccessMember> getMemberChildren(
        List<RolapMember> parentMembers,
        List<RolapMember> children,
        MemberChildrenConstraint constraint)
    {
        List<RolapMember> fullChildren = new ArrayList<>();
        memberReader.getMemberChildren(parentMembers, fullChildren, constraint);
        return processMemberChildren(fullChildren, children, constraint);
    }

    public Map<RolapMember, AccessMember> processMemberChildren(
        List<RolapMember> fullChildren,
        List<RolapMember> children,
        MemberChildrenConstraint constraint)
    {
        // todo: optimize if parentMember is beyond last level
        List<RolapMember> grandChildren = null;
        Map<RolapMember, AccessMember> memberToAccessMap =
            new LinkedHashMap<>();
        for (int i = 0; i < fullChildren.size(); i++) {
            RolapMember member = fullChildren.get(i);

            // If a child is hidden (due to raggedness)
            // This must be done before applying access-control.
            if ((ragged && member.isHidden())) {
                // Replace this member with all of its children.
                // They might be hidden too, but we'll get to them in due
                // course. They also might be access-controlled; that's why
                // we deal with raggedness before we apply access-control.
                fullChildren.remove(i);
                if (grandChildren == null) {
                    grandChildren = new ArrayList<>();
                } else {
                    grandChildren.clear();
                }
                memberReader.getMemberChildren
                  (member, grandChildren, constraint);
                fullChildren.addAll(i, grandChildren);
                // Step back to before the first child we just inserted,
                // and go through the loop again.
                --i;
                continue;
            }

            // Filter out children which are invisible because of
            // access-control.
            final AccessMember access;
            if (hierarchyAccess != null) {
                access = hierarchyAccess.getAccess(member);
            } else {
                access = AccessMember.ALL;
            }
            switch (access) {
            case NONE:
                break;
            default:
                children.add(member);
                memberToAccessMap.put(member,  access);
                break;
            }
        }
        return memberToAccessMap;
    }

    /**
     * Writes to members which we can see.
     * @param members Input list
     * @param filteredMembers Output list
     */
    private void filterMembers(
        List<RolapMember> members,
        List<RolapMember> filteredMembers)
    {
        for (RolapMember member : members) {
            if (canSee(member)) {
                filteredMembers.add(member);
            }
        }
    }

    private boolean canSee(final RolapMember member) {
        if (ragged && member.isHidden()) {
            return false;
        }
        if (hierarchyAccess != null) {
            final AccessMember access = hierarchyAccess.getAccess(member);
            return access != AccessMember.NONE;
        }
        return true;
    }

    @Override
	public List<RolapMember> getRootMembers() {
        int topLevelDepth = hierarchyAccess.getTopLevelDepth();
        if (topLevelDepth > 0) {
            RolapLevel topLevel =
                (RolapLevel) getHierarchy().getLevels().get(topLevelDepth);
            final List<RolapMember> memberList =
                getMembersInLevel(topLevel);
            if (memberList.isEmpty()) {
                throw new OlapRuntimeException(MessageFormat.format(
                    hierarchyHasNoAccessibleMembers,
                        getHierarchy().getUniqueName()));
            }
            return memberList;
        }
        return super.getRootMembers();
    }

    @Override
	public List<RolapMember> getMembersInLevel(
        RolapLevel level)
    {
        TupleConstraint constraint =
            sqlConstraintFactory.getLevelMembersConstraint(null);
        return getMembersInLevel(level, constraint);
    }

    @Override
	public List<RolapMember> getMembersInLevel(
        RolapLevel level, TupleConstraint constraint)
    {
        if (hierarchyAccess != null) {
            final int depth = level.getDepth();
            if (depth < hierarchyAccess.getTopLevelDepth()) {
                return Collections.emptyList();
            }
            if (depth > hierarchyAccess.getBottomLevelDepth()) {
                return Collections.emptyList();
            }
        }
        final List<RolapMember> membersInLevel =
            memberReader.getMembersInLevel(
                level, constraint);
        List<RolapMember> filteredMembers = new ArrayList<>();
        filterMembers(membersInLevel, filteredMembers);
        return filteredMembers;
    }

    @Override
	public RolapMember getDefaultMember() {
        RolapMember defaultMember =
            (RolapMember) getHierarchy().getDefaultMember();
        if (defaultMember != null) {
            AccessMember i = hierarchyAccess.getAccess(defaultMember);
            if (i != AccessMember.NONE) {
                return defaultMember;
            }
        }
        final List<RolapMember> rootMembers = getRootMembers();

        RolapMember firstAvailableRootMember = null;
        boolean singleAvailableRootMember = false;
        for (RolapMember rootMember : rootMembers) {
            AccessMember i = hierarchyAccess.getAccess(rootMember);
            if (i != AccessMember.NONE) {
                if (firstAvailableRootMember == null) {
                    firstAvailableRootMember = rootMember;
                    singleAvailableRootMember = true;
                } else {
                    singleAvailableRootMember = false;
                    break;
                }
            }
        }
        if (singleAvailableRootMember) {
            return firstAvailableRootMember;
        }
        if (
            firstAvailableRootMember != null
            && firstAvailableRootMember.isMeasure())
        {
            return firstAvailableRootMember;
        }
        return new MultiCardinalityDefaultMember(rootMembers.get(0));
    }

    

    @Override
	public RolapMember getMemberParent(RolapMember member) {
        RolapMember parentMember = member.getParentMember();
        // Skip over hidden parents.
        while (parentMember != null && parentMember.isHidden()) {
            parentMember = parentMember.getParentMember();
        }
        // Skip over non-accessible parents.
        if (parentMember != null) {
            if (hierarchyAccess.getAccess(parentMember) == AccessMember.NONE) {
                return null;
            }
        }
        return parentMember;
    }
}
