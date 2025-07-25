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

import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.StarPredicate;

/**
 * Predicate constraining a column to be greater than or less than a given
 * bound, or between a pair of bounds.
 *
 * @author jhyde
 * @since Nov 26, 2006
 */
public class RangeColumnPredicate extends AbstractColumnPredicate {
    private final boolean lowerInclusive;
    private final ValueColumnPredicate lowerBound;
    private final boolean upperInclusive;
    private final ValueColumnPredicate upperBound;

    /**
     * Creates a RangeColumnPredicate.
     *
     * @param column Constrained column
     * @param lowerInclusive Whether range includes the lower bound;
     *   must be false if not bounded below
     * @param lowerBound Lower bound, or null if not bounded below
     * @param upperInclusive Whether range includes the upper bound;
     *   must be false if not bounded above
     * @param upperBound Upper bound, or null if not bounded above
     */
    public RangeColumnPredicate(
        RolapStar.Column column,
        boolean lowerInclusive,
        ValueColumnPredicate lowerBound,
        boolean upperInclusive,
        ValueColumnPredicate upperBound)
    {
        super(column);
        assert lowerBound == null
            || lowerBound.getConstrainedColumn() == column;
        assert !(lowerBound == null && lowerInclusive);
        assert upperBound == null
            || upperBound.getConstrainedColumn() == column;
        assert !(upperBound == null && upperInclusive);
        this.lowerInclusive = lowerInclusive;
        this.lowerBound = lowerBound;
        this.upperInclusive = upperInclusive;
        this.upperBound = upperBound;
    }

    @Override
	public int hashCode() {
        int h = lowerInclusive ? 2 : 1;
        h = 31 * h + lowerBound.hashCode();
        h = 31 * h + (upperInclusive ? 2 : 1);
        h = 31 * h + upperBound.hashCode();
        return h;
    }

    @Override
	public boolean equals(Object obj) {
        if (obj instanceof RangeColumnPredicate that) {
            return this.lowerInclusive == that.lowerInclusive
                && this.lowerBound.equals(that.lowerBound)
                && this.upperInclusive == that.upperInclusive
                && this.upperBound.equals(that.upperBound);
        } else {
            return false;
        }
    }

    @Override
	public void values(Collection<Object> collection) {
        // Besides the end points, don't know what values may be in the range.
        // FIXME: values() is only a half-useful method. Replace it?
        throw new UnsupportedOperationException();
    }

    @Override
	public boolean evaluate(Object value) {
        if (lowerBound != null) {
            int c =
                ((Comparable<Object>) lowerBound.getValue()).compareTo(value);
            if (lowerInclusive ? c > 0 : c >= 0) {
                return false;
            }
        }
        if (upperBound != null) {
            int c =
                ((Comparable<Object>) upperBound.getValue()).compareTo(value);
            if (upperInclusive ? c < 0 : c <= 0) {
                return false;
            }
        }
        return true;
    }

    @Override
	public void describe(StringBuilder buf) {
        buf.append("Range(");
        if (lowerBound == null) {
            buf.append("unbounded");
        } else {
            lowerBound.describe(buf);
            if (lowerInclusive) {
                buf.append(" inclusive");
            }
        }
        buf.append(" to ");
        if (upperBound == null) {
            buf.append("unbounded");
        } else {
            upperBound.describe(buf);
            if (upperInclusive) {
                buf.append(" inclusive");
            }
        }
        buf.append(")");
    }

    @Override
	public Overlap intersect(StarColumnPredicate predicate) {
        throw new UnsupportedOperationException();
    }

    @Override
	public boolean mightIntersect(StarPredicate other) {
        if (other instanceof ValueColumnPredicate) {
            return evaluate(((ValueColumnPredicate) other).getValue());
        } else {
            // It MIGHT intersect. (Might not.)
            // todo: Handle case 'other instanceof RangeColumnPredicate'
            return true;
        }
    }

    @Override
	public StarColumnPredicate minus(StarPredicate predicate) {
        assert predicate != null;
        // todo: Implement some common cases, such as Range minus Range, and
        // Range minus true/false
        return new MinusStarPredicate(
            this, (StarColumnPredicate) predicate);
    }

    @Override
	public StarColumnPredicate cloneWithColumn(RolapStar.Column column) {
        return new RangeColumnPredicate(
            column, lowerInclusive, lowerBound, upperInclusive, upperBound);
    }

    public ValueColumnPredicate getLowerBound() {
        return lowerBound;
    }

    public boolean getLowerInclusive() {
        return lowerInclusive;
    }

    public ValueColumnPredicate getUpperBound() {
        return upperBound;
    }

    public boolean getUpperInclusive() {
        return upperInclusive;
    }
}
