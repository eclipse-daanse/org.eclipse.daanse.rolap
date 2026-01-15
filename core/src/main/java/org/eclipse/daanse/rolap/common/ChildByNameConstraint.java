/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara
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

import java.util.Arrays;
import java.util.List;

import org.eclipse.daanse.olap.api.NameSegment;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;

/**
 * Constraint which optimizes the search for a child by name. This is used
 * whenever the string representation of a member is parsed, e.g.
 * [Customers].[USA].[CA]. Restricts the result to
 * the member we are searching for.
 *
 * @author avix
 */
public class ChildByNameConstraint extends DefaultMemberChildrenConstraint {
    private final String[] childNames;
    private final Object cacheKey;

    /**
     * Creates a ChildByNameConstraint.
     *
     * @param childName Name of child
     */
    public ChildByNameConstraint(NameSegment childName) {
        this.childNames = new String[]{childName.getName()};
        this.cacheKey = List.of(ChildByNameConstraint.class, childName);
    }

    public ChildByNameConstraint(List<NameSegment> childNames) {
        this.childNames = new String[childNames.size()];
        int i = 0;
        for (NameSegment name : childNames) {
            this.childNames[i++] = name.getName();
        }
        this.cacheKey = List.of(
            ChildByNameConstraint.class, this.childNames);
    }

    @Override
    public int hashCode() {
        return getCacheKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ChildByNameConstraint childByNameConstraint
            && getCacheKey().equals(childByNameConstraint.getCacheKey());
    }

    @Override
	public void addLevelConstraint(
        SqlQuery query,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level)
    {
        super.addLevelConstraint(query, baseCube, aggStar, level);
        query.addWhere(
            SqlConstraintUtils.constrainLevel(
                level, query, baseCube, aggStar, childNames, true).toString());
    }

    @Override
	public String toString() {
        return new StringBuilder("ChildByNameConstraint(").append(Arrays.toString(childNames)).append(")").toString();
    }

    @Override
	public Object getCacheKey() {
        return cacheKey;
    }

    public List<String> getChildNames() {
        return List.of(childNames);
    }

}
