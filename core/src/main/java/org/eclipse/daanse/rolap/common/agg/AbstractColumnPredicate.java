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


package org.eclipse.daanse.rolap.common.agg;

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.genericExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.StarPredicate;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;

/**
 * A AbstractColumnPredicate is an abstract implementation for
 * {@link org.eclipse.daanse.rolap.common.StarColumnPredicate}.
 */
public abstract class AbstractColumnPredicate implements StarColumnPredicate {
    protected final RolapStar.Column constrainedColumn;
    private BitKey constrainedColumnBitKey;

    /**
     * Creates an AbstractColumnPredicate.
     *
     * @param constrainedColumn Constrained column
     */
    protected AbstractColumnPredicate(RolapStar.Column constrainedColumn) {
        this.constrainedColumn = constrainedColumn;
    }

    @Override
	public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(genericExpression(constrainedColumn.getExpression()));
        describe(buf);
        return buf.toString();
    }

    @Override
	public RolapStar.Column getConstrainedColumn() {
        return constrainedColumn;
    }

    @Override
	public List<RolapStar.Column> getConstrainedColumnList() {
        return Collections.singletonList(constrainedColumn);
    }

    @Override
	public BitKey getConstrainedColumnBitKey() {
        // Check whether constrainedColumn are null.
        // Example: FastBatchingCellReaderTest.testAggregateDistinctCount5().
        if (constrainedColumnBitKey == null
            && constrainedColumn != null
            && constrainedColumn.getTable() != null)
        {
            constrainedColumnBitKey =
                BitKey.Factory.makeBitKey(
                    constrainedColumn.getStar().getColumnCount());
            constrainedColumnBitKey.set(constrainedColumn.getBitPosition());
        }
        return constrainedColumnBitKey;
    }

    @Override
	public boolean evaluate(List<Object> valueList) {
        if (valueList.size() != 1) {
            throw new IllegalArgumentException("valueList.size() should be 1");
        }
        return evaluate(valueList.getFirst());
    }

    @Override
	public boolean equalConstraint(StarPredicate that) {
        return false;
    }

    @Override
	public StarPredicate or(StarPredicate predicate) {
        if (predicate instanceof StarColumnPredicate starColumnPredicate &&
            starColumnPredicate.getConstrainedColumn()
                == getConstrainedColumn()) {
            return orColumn(starColumnPredicate);
        }
        final List<StarPredicate> list = new ArrayList<>(2);
        list.add(this);
        list.add(predicate);
        return new OrPredicate(list);
    }

    @Override
	public StarColumnPredicate orColumn(StarColumnPredicate predicate) {
        if (predicate.getConstrainedColumn() != getConstrainedColumn()) {
            throw new IllegalArgumentException("orColumn: constrainedColumn have wrong value ");
        }

        if (predicate instanceof ListColumnPredicate that) {
            final List<StarColumnPredicate> list =
                new ArrayList<>();
            list.add(this);
            list.addAll(that.getPredicates());
            return new ListColumnPredicate(
                getConstrainedColumn(),
                list);
        } else {
            final List<StarColumnPredicate> list =
                new ArrayList<>(2);
            list.add(this);
            list.add(predicate);
            return new ListColumnPredicate(
                getConstrainedColumn(),
                list);
        }
    }

    @Override
	public StarPredicate and(StarPredicate predicate) {
        final List<StarPredicate> list = new ArrayList<>(2);
        list.add(this);
        list.add(predicate);
        return new AndPredicate(list);
    }

    @Override
	public void toSql(SqlQuery sqlQuery, StringBuilder buf) {
        throw Util.needToImplement(this);
    }

    protected static List<StarColumnPredicate> cloneListWithColumn(
        RolapStar.Column column,
        List<StarColumnPredicate> list)
    {
        List<StarColumnPredicate> newList =
            new ArrayList<>(list.size());
        for (StarColumnPredicate predicate : list) {
            newList.add(predicate.cloneWithColumn(column));
        }
        return newList;
    }

    /**
     * Factory for {@link org.eclipse.daanse.rolap.common.StarPredicate}s and
     * {@link org.eclipse.daanse.rolap.common.StarColumnPredicate}s.
     */
    public static class Factory {

        private Factory() {
            // constructor
        }

        /**
         * Returns a predicate which tests whether the column's
         * value is equal to a given constant.
         *
         * @param column Constrained column
         * @param value Value
         * @return Predicate which tests whether the column's value is equal
         *   to a column constraint's value
         */
        public static StarColumnPredicate equalColumnPredicate(
            RolapStar.Column column,
            Object value)
        {
            return new ValueColumnPredicate(column, value);
        }

        /**
         * Returns predicate which is the OR of a list of predicates.
         *
         * @param column Column being constrained
         * @param list List of predicates
         * @return Predicate which is an OR of the list of predicates
         */
        public static StarColumnPredicate or(
            RolapStar.Column column,
            List<StarColumnPredicate> list)
        {
            return new ListColumnPredicate(column, list);
        }

        /**
         * Returns a predicate which always evaluates to TRUE or FALSE.
         * @param b Truth value
         * @return Predicate which always evaluates to truth value
         */
        public static LiteralStarPredicate bool(boolean b) {
            return b ? LiteralStarPredicate.TRUE : LiteralStarPredicate.FALSE;
        }

        /**
         * Returns a predicate which tests whether the column's
         * value is equal to column predicate's value.
         *
         * @param predicate Column predicate
         * @return Predicate which tests whether the column's value is equal
         *   to a column predicate's value
         */
        public static StarColumnPredicate equalColumnPredicate(
            ValueColumnPredicate predicate)
        {
            return equalColumnPredicate(
                predicate.getConstrainedColumn(),
                predicate.getValue());
        }
    }
}
