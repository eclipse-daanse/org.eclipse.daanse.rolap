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

import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;

/**
 * Restricts the SQL result of {@link org.eclipse.daanse.rolap.common.TupleReader}. This is also
 * used by
 * SqlMemberSource#getMembersInLevel(RolapLevel, TupleConstraint).
 *
 * @see org.eclipse.daanse.rolap.common.TupleReader
 * @see org.eclipse.daanse.rolap.common.SqlMemberSource
 *
 * @author av
 */
public interface TupleConstraint extends SqlConstraint {
    /**
     * Modifies a Level.Members query.
     *
     * @param sqlQuery the query to modify
     * @param aggStar aggregate star to use
     * @param baseCube base cube for virtual cube constraints
     */
    public void addConstraint(
        SqlQuery sqlQuery,
        RolapCube baseCube,
        AggStar aggStar);

    /**
     * Will be called multiple times for every "group by" level in
     * Level.Members query, i.e. the level that contains the members and all
     * parent levels except All.
     * If the condition requires so,
     * it may join the levels table to the fact table.
     *
     * @param sqlQuery the query to modify
     * @param baseCube base cube for virtual cube constraints
     * @param aggStar Aggregate table, or null if query is against fact table
     * @param level the level which is accessed in the Level.Members query
     */
    public void addLevelConstraint(
        SqlQuery sqlQuery,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level);

    /**
     * When the members of a level are fetched, the result is grouped
     * by into parents and their children. These parent/children are
     * stored in the parent/children cache, whose key consists of the parent
     * and the MemberChildrenConstraint#hashKey(). So we need a matching
     * MemberChildrenConstraint to store the parent with its children into
     * the parent/children cache.
     *
     * The returned MemberChildrenConstraint must be one that would have
     * returned the same children for the given parent as the MemberLevel query
     * has found for that parent.
     *
     * If null is returned, the parent/children will not be cached (but the
     * level/members still will be).
     */
    MemberChildrenConstraint getMemberChildrenConstraint(RolapMember parent);

    /**
     * @return the evaluator currently associated with the constraint; null
     * if there is no associated evaluator
     */
    public Evaluator getEvaluator();

    /**
     * @return true if the constraint can leverage an aggregate table
     */
    public boolean supportsAggTables();
}
