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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.StarPredicate;

/**
 * A StarPredicate which evaluates to true if its
 * first child evaluates to true and its second child evaluates to false.
 *
 * @author jhyde
 * @since Nov 6, 2006
 */
public class MinusStarPredicate extends AbstractColumnPredicate {
    private final StarColumnPredicate plus;
    private final StarColumnPredicate minus;

    /**
     * Creates a MinusStarPredicate.
     *
     * @param plus Positive predicate
     * @param minus Negative predicate
     *  plus != null
     *  minus != null
     */
    public MinusStarPredicate(
        StarColumnPredicate plus,
        StarColumnPredicate minus)
    {
        super(plus.getConstrainedColumn());
        assert minus != null;
        this.plus = plus;
        this.minus = minus;
    }


    @Override
	public boolean equals(Object obj) {
        if (obj instanceof MinusStarPredicate that) {
            return this.plus.equals(that.plus)
                && this.minus.equals(that.minus);
        } else {
            return false;
        }
    }

    @Override
	public int hashCode() {
        return plus.hashCode() * 31
            + minus.hashCode();
    }

    @Override
	public RolapStar.Column getConstrainedColumn() {
        return plus.getConstrainedColumn();
    }

    @Override
	public void values(Collection<Object> collection) {
        Set<Object> plusValues = new HashSet<>();
        plus.values(plusValues);
        List<Object> minusValues = new ArrayList<>();
        minus.values(minusValues);
        plusValues.removeAll(minusValues);
        collection.addAll(plusValues);
    }

    @Override
	public boolean evaluate(Object value) {
        return plus.evaluate(value)
            && !minus.evaluate(value);
    }

    @Override
	public void describe(StringBuilder buf) {
        buf.append("(").append(plus).append(" - ").append(minus).append(")");
    }

    @Override
	public Overlap intersect(StarColumnPredicate predicate) {
        throw new UnsupportedOperationException();
    }

    @Override
	public boolean mightIntersect(StarPredicate other) {
        // Approximately, this constraint might intersect if it intersects
        // with the 'plus' side. It's possible that the 'minus' side might
        // wipe out all of those intersections, but we don't consider that.
        return plus.mightIntersect(other);
    }

    @Override
	public StarColumnPredicate minus(StarPredicate predicate) {
        assert predicate != null;
        if (predicate instanceof ValueColumnPredicate valuePredicate) {
            if (!evaluate(valuePredicate.getValue())) {
                // Case 3: 'minus' is a list, 'constraint' is a value
                // which is not matched by this
                return this;
            }
        }
        if (minus instanceof ListColumnPredicate minusList) {
            RolapStar.Column column = plus.getConstrainedColumn();
            if (predicate instanceof ListColumnPredicate list) {
                List<StarColumnPredicate> unionList =
                    new ArrayList<>(minusList.getPredicates());
                unionList.addAll(list.getPredicates());
                return new MinusStarPredicate(
                    plus,
                    new ListColumnPredicate(
                        column,
                        unionList));
            }
            if (predicate instanceof ValueColumnPredicate valuePredicate) {
                if (!evaluate(valuePredicate.getValue())) {
                    // Case 3: 'minus' is a list, 'constraint' is a value
                    // which is not matched by this
                    return this;
                }
                // Case 2: 'minus' is a list, 'constraint' is a value.
                List<StarColumnPredicate> unionList =
                    new ArrayList<>(minusList.getPredicates());
                unionList.add(
                    new ValueColumnPredicate(
                        column, valuePredicate.getValue()));
                return new MinusStarPredicate(
                    plus,
                    new ListColumnPredicate(column, unionList));
            }
        }
        return new MinusStarPredicate(
            this,
            (StarColumnPredicate) predicate);
    }

    @Override
	public StarColumnPredicate cloneWithColumn(RolapStar.Column column) {
        return new MinusStarPredicate(
            plus.cloneWithColumn(column),
            minus.cloneWithColumn(column));
    }
}
