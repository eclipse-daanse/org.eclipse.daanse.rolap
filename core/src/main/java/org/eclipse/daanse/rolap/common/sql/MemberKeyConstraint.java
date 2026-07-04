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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.constraint.LevelConstraintGenerator;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;

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

    /**
     * Records one {@code constrainKeyValue} WHERE conjunct per key column on the fork.
     */
    @Override
    public QueryTape addConstraintOps(
        Dialect dialect, QueryRecorder.Fork fork, RolapCube baseCube, AggStar aggStar)
    {
        for (int i = 0; i < columnList.size(); i++) {
            SqlExpression expression = columnList.get(i);
            final Comparable value = valueList.get(i);
            final Datatype datatype = datatypeList.get(i);
            fork.addWhere(
                LevelConstraintGenerator.constrainKeyValue(
                    dialect,
                    expression,
                    datatype,
                    value));
        }
        return fork.ops();
    }

    /**
     * No per-level restriction: the contribution is the fork's empty tape.
     */
    @Override
    public QueryTape addLevelConstraintOps(
        Dialect dialect,
        QueryRecorder.Fork fork,
        RolapCube baseCube,
        AggStar aggStar,
        RolapLevel level)
    {
        return fork.ops();
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

    /**
     * The generic-builder counterpart of {@link #addConstraint}: each {@code column = value} (or
     * {@code IS NULL}) as a builder {@code WHERE} predicate, with no fact join (a dimension-only key
     * restriction). Mirrors {@link LevelConstraintGenerator#constrainKeyValue}.
     */
    @Override
    public java.util.Optional<ConstraintContribution> toContribution(RolapCube baseCube, AggStar aggStar) {
        java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> predicates =
            new java.util.ArrayList<>();
        for (int i = 0; i < columnList.size(); i++) {
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression col =
                org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(columnList.get(i));
            Comparable value = valueList.get(i);
            if (value == org.eclipse.daanse.olap.common.Util.sqlNullValue) {
                predicates.add(org.eclipse.daanse.sql.statement.api.Predicates.isNull(col));
            } else {
                predicates.add(org.eclipse.daanse.sql.statement.api.Predicates.comparison(col,
                    org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                    org.eclipse.daanse.sql.statement.api.Expressions.literal(value, datatypeList.get(i))));
            }
        }
        if (predicates.isEmpty()) {
            // No key columns to constrain — fall back rather than build unconstrained authoritatively.
            return java.util.Optional.empty();
        }
        org.eclipse.daanse.sql.statement.api.expression.Predicate where = (predicates.size() == 1)
            ? predicates.get(0)
            : org.eclipse.daanse.sql.statement.api.Predicates.and(predicates);
        return java.util.Optional.of(new ConstraintContribution(java.util.Optional.of(where), java.util.List.of()));
    }
}
