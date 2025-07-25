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

import java.util.List;

import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;

/**
 * Restricts the SQL result of a MembersChildren query in SqlMemberSource.
 *
 * @see org.eclipse.daanse.rolap.common.SqlMemberSource
 *
 * @author av
 * @since Nov 2, 2005
 */
public interface MemberChildrenConstraint extends SqlConstraint {

    /**
     * Modifies a Member.Children query so that only the children
     * of parent will be returned in the result set.
     *
     * @param sqlQuery the query to modify
     * @param baseCube base cube for virtual members
     * @param aggStar Aggregate star, if we are reading from an aggregate table,
     * @param parent the parent member that restricts the returned children
     */
    public void addMemberConstraint(
        SqlQuery sqlQuery,
        RolapCube baseCube,
        AggStar aggStar,
        RolapMember parent);

    /**
     * Modifies a Member.Children query so that (all or some)
     * children of <em>all</em> parent members contained in parents
     * will be returned in the result set.
     *
     * @param sqlQuery Query to modify
     * @param baseCube Base cube for virtual members
     * @param aggStar Aggregate table, or null if query is against fact table
     * @param parents List of parent members that restrict the returned
     *        children
     */
    public void addMemberConstraint(
        SqlQuery sqlQuery,
        RolapCube baseCube,
        AggStar aggStar,
        List<RolapMember> parents);

    /**
     * Will be called once for the level that contains the
     * children of a Member.Children query. If the condition requires so,
     * it may join the levels table to the fact table.
     *
     * @param query the query to modify
     * @param baseCube base cube for virtual members
     * @param aggStar Aggregate table, or null if query is against fact table
     * @param level the level that contains the children
     */
    public void addLevelConstraint(
        SqlQuery query,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level);

}
