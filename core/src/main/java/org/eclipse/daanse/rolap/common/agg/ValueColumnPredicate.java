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


package org.eclipse.daanse.rolap.common.agg;

import java.util.Collection;

import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.StarPredicate;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;

/**
 * A constraint which requires a column to have a particular value.
 *
 * @author jhyde
 * @since Nov 2, 2006
 */
public class ValueColumnPredicate
    extends AbstractColumnPredicate
    implements Comparable
{
    private final Object value;

    /**
     * Creates a column constraint.
     *
     * @param value Value to constraint the column to. (We require that it is
     *   {@link Comparable} because we will sort the values in order to
     *   generate deterministic SQL.)
     */
    public ValueColumnPredicate(
        RolapStar.Column constrainedColumn,
        Object value)
    {
        super(constrainedColumn);
//        assert constrainedColumn != null;
        assert value != null;
        assert ! (value instanceof StarColumnPredicate);
        this.value = value;
    }

    /**
     * Returns the value which the column is compared to.
     */
    public Object getValue() {
        return value;
    }

    @Override
	public String toString() {
        return String.valueOf(value);
    }

    @Override
	public boolean equalConstraint(StarPredicate that) {
        return that instanceof ValueColumnPredicate
            && getConstrainedColumnBitKey().equals(
                that.getConstrainedColumnBitKey())
            && this.value.equals(((ValueColumnPredicate) that).value);
    }

    @Override
	public int compareTo(Object o) {
        ValueColumnPredicate that = (ValueColumnPredicate) o;
        int columnBitKeyComp =
            getConstrainedColumnBitKey().compareTo(
                that.getConstrainedColumnBitKey());

        // First compare the column bitkeys.
        if (columnBitKeyComp != 0) {
            return columnBitKeyComp;
        }

        if (this.value instanceof Comparable
            && that.value instanceof Comparable
            && this.value.getClass() == that.value.getClass())
        {
            return ((Comparable) this.value).compareTo(that.value);
        } else {
            String thisComp = String.valueOf(this.value);
            String thatComp = String.valueOf(that.value);
            return thisComp.compareTo(thatComp);
        }
    }

    @Override
	public boolean equals(Object other) {
        if (!(other instanceof ValueColumnPredicate that)) {
            return false;
        }
        // First compare the column bitkeys.
        if (!getConstrainedColumnBitKey().equals(
                that.getConstrainedColumnBitKey()))
        {
            return false;
        }

        if (value != null) {
            return value.equals(that.getValue());
        } else {
            return null == that.getValue();
        }
    }

    @Override
	public int hashCode() {
        int hashCode = getConstrainedColumnBitKey().hashCode();

        if (value != null) {
            hashCode = hashCode ^ value.hashCode();
        }

        return hashCode;
    }

    @Override
	public void values(Collection<Object> collection) {
        collection.add(value);
    }

    @Override
	public boolean evaluate(Object value) {
        return this.value.equals(value);
    }

    @Override
	public void describe(StringBuilder buf) {
        buf.append(value);
    }

    @Override
	public Overlap intersect(StarColumnPredicate predicate) {
        throw new UnsupportedOperationException();
    }

    @Override
	public boolean mightIntersect(StarPredicate other) {
        return ((StarColumnPredicate) other).evaluate(value);
    }

    @Override
	public StarColumnPredicate minus(StarPredicate predicate) {
        assert predicate != null;
        if (((StarColumnPredicate) predicate).evaluate(value)) {
            return LiteralStarPredicate.FALSE;
        } else {
            return this;
        }
    }

    @Override
	public StarColumnPredicate cloneWithColumn(RolapStar.Column column) {
        return new ValueColumnPredicate(column, value);
    }

    @Override
	public void toSql(SqlQuery sqlQuery, StringBuilder buf) {
        final RolapStar.Column column = getConstrainedColumn();
        String expr = column.generateExprString(sqlQuery);
        buf.append(expr);
        Object key = getValue();
        if (key == Util.sqlNullValue) {
            buf.append(" is null");
        } else {
            buf.append(" = ");
            sqlQuery.getDialect().quote(buf, key, column.getDatatype());
        }
    }

    public BitKey checkInList(BitKey inListLHSBitKey) {
        // ValueColumn predicate by itself is not using IN list; when it is
        // one of the children to an OR predicate, then using IN list
        // is helpful. The later is checked by passing in a bitmap that
        // represent the LHS or the IN list, i.e. the column that is
        // constrained by the OR.
        BitKey inListRHSBitKey = inListLHSBitKey.copy();

        if (!getConstrainedColumnBitKey().equals(inListLHSBitKey)
            || value == Util.sqlNullValue)
        {
            inListRHSBitKey.clear();
        }

        return inListRHSBitKey;
    }

    public void toInListSql(SqlQuery sqlQuery, StringBuilder buf) {
        sqlQuery.getDialect().quote(
            buf, value, getConstrainedColumn().getDatatype());
    }
}
