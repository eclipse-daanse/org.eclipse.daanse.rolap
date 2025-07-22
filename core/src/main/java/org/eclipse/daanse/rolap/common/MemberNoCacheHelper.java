/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2008-2008 TASecurity Group Spain
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

import java.util.List;

import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;

/**
 * Encapsulation of member caching for no caching.
 *
 * @author Luis F. Canals (lcanals@tasecurity.net)
 * @version 1.0
 */
public class MemberNoCacheHelper extends MemberCacheHelper {

    public MemberNoCacheHelper() {
        super(null);
    }

    // implement MemberCache
    @Override
	public RolapMember getMember(
        Object key,
        boolean mustCheckCacheStatus)
    {
        return null;
    }


    // implement MemberCache
    @Override
	public Object putMember(Object key, RolapMember value) {
        return value;
    }

    // implement MemberCache
    @Override
	public Object makeKey(RolapMember parent, Object key) {
        return new MemberKey(parent, key);
    }

    // implement MemberCache
    // synchronization: Must synchronize, because modifies mapKeyToMember
    @Override
	public synchronized RolapMember getMember(Object key) {
        return getMember(key, true);
    }



    @Override
	public void putLevelMembersInCache(
        RolapLevel level,
        TupleConstraint constraint,
        List<RolapMember> members)
    {
        //empty
    }

    @Override
	public List<RolapMember> getChildrenFromCache(
        RolapMember member,
        MemberChildrenConstraint constraint)
    {
        return null;
    }

    @Override
	public void putChildren(
        RolapMember member,
        MemberChildrenConstraint constraint,
        List<RolapMember> children)
    {
        //empty
    }

    @Override
	public List<RolapMember> getLevelMembersFromCache(
        RolapLevel level,
        TupleConstraint constraint)
    {
        return null;
    }



    @Override
	public boolean isMutable() {
        return true;
    }

    @Override
	public synchronized RolapMember removeMember(Object key) {
        return null;
    }

    @Override
	public RolapMember removeMemberAndDescendants(Object key) {
        return null;
    }
}
