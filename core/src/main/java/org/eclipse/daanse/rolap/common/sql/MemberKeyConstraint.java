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
package org.eclipse.daanse.rolap.common.sql;

import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Datatype;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.SqlExpression;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.RolapCube;
import org.eclipse.daanse.rolap.common.RolapLevel;
import org.eclipse.daanse.rolap.common.RolapMember;
import org.eclipse.daanse.rolap.common.SqlConstraintUtils;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;

/**
 * Restricts the SQL result set to members where particular columns have
 * particular values.
 *
 * @version $Id$
 */
public class MemberKeyConstraint
    implements TupleConstraint
{
    private final Pair<List<SqlExpression>, List<Comparable>> cacheKey;
    private final List<SqlExpression> columnList;
    private final List<Datatype> datatypeList;
    private final List<Comparable> valueList;

    public MemberKeyConstraint(
        List<SqlExpression> columnList,
        List<Datatype> datatypeList,
        List<Comparable> valueList)
    {
        this.columnList = columnList;
        this.datatypeList = datatypeList;
        this.valueList = valueList;
        cacheKey = Pair.of(columnList, valueList);
    }

    @Override
	public void addConstraint(
        SqlQuery sqlQuery, RolapCube baseCube, AggStar aggStar)
    {
        for (int i = 0; i < columnList.size(); i++) {
            SqlExpression expression = columnList.get(i);
            final Comparable value = valueList.get(i);
            final Datatype datatype = datatypeList.get(i);
            sqlQuery.addWhere(
                SqlConstraintUtils.constrainLevel2(
                    sqlQuery,
                    expression,
                    datatype,
                    value));
        }
    }

    @Override
	public void addLevelConstraint(
        SqlQuery sqlQuery,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level)
    {
    }

    @Override
	public MemberChildrenConstraint getMemberChildrenConstraint(
        RolapMember parent)
    {
        return null;
    }

    @Override
	public String toString() {
        return "MemberKeyConstraint";
    }


    @Override
	public Object getCacheKey() {
        return cacheKey;
    }

    @Override
	public Evaluator getEvaluator() {
        return null;
    }

    @Override
    public boolean supportsAggTables() {
        return true;
    }
}
