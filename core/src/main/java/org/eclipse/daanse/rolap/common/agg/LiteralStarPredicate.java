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
import java.util.List;

import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.StarPredicate;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;

/**
 * A constraint which always returns true or false.
 *
 * @author jhyde
 * @since Nov 2, 2006
 */
public class LiteralStarPredicate extends AbstractColumnPredicate {
    private final boolean value;

    public static final LiteralStarPredicate TRUE =
        new LiteralStarPredicate(null, true);
    public static final LiteralStarPredicate FALSE =
        new LiteralStarPredicate(null, false);

    /**
     * Creates a LiteralStarPredicate.
     *
     * @param column Constrained column
     * @param value Truth value
     */
    public LiteralStarPredicate(RolapStar.Column column, boolean value) {
        super(column);
        this.value = value;
    }


    @Override
	public int hashCode() {
        return value ? 2 : 1;
    }

    @Override
	public boolean equals(Object obj) {
        if (obj instanceof LiteralStarPredicate that) {
            return this.value == that.value;
        } else {
            return false;
        }
    }

    @Override
	public boolean evaluate(List<Object> valueList) {
        assert valueList.isEmpty();
        return value;
    }

    @Override
	public boolean equalConstraint(StarPredicate that) {
        throw new UnsupportedOperationException();
    }

    @Override
	public String toString() {
        return Boolean.toString(value);
    }

    @Override
	public void values(Collection<Object> collection) {
        collection.add(value);
    }

    @Override
	public boolean evaluate(Object value) {
        return this.value;
    }

    @Override
	public void describe(StringBuilder buf) {
        buf.append("=any");
    }

    @Override
	public Overlap intersect(
        StarColumnPredicate predicate)
    {
        return new Overlap(value, null, 0f);
    }

    @Override
	public boolean mightIntersect(StarPredicate other) {
        // FALSE intersects nothing
        // TRUE intersects everything except FALSE
        if (!value) {
            return false;
        } else if (other instanceof LiteralStarPredicate literalStarPredicate) {
            return literalStarPredicate.value;
        } else {
            return true;
        }
    }

    @Override
	public StarColumnPredicate minus(StarPredicate predicate) {
        assert predicate != null;
        if (value) {
            // We have no 'not' operator, so there's no shorter way to represent
            // "true - constraint".
            return new MinusStarPredicate(
                this, (StarColumnPredicate) predicate);
        } else {
            // "false - constraint" is "false"
            return this;
        }
    }

    @Override
	public StarColumnPredicate cloneWithColumn(RolapStar.Column column) {
        return this;
    }

    public boolean getValue() {
        return value;
    }

    @Override
	public void toSql(SqlQuery sqlQuery, StringBuilder buf) {
        // e.g. "true"
        buf.append(value);
    }
}
