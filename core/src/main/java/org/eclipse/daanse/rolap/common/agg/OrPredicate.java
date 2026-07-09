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
import java.util.List;

import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarPredicate;

/**
 * Predicate which is the union of a list of predicates. It evaluates to
 * true if any of the predicates evaluates to true.
 *
 * @see OrPredicate
 *
 * @author jhyde
 */
public class OrPredicate extends ListPredicate {

    public OrPredicate(List<StarPredicate> predicateList) {
        super(predicateList);
    }

    @Override
	public boolean evaluate(List<Object> valueList) {
        // NOTE: If we know that every predicate in the list is a
        // ValueColumnPredicate, we could optimize the evaluate method by
        // building a value list at construction time. But it's a tradeoff,
        // considering the extra time and space required.
        for (StarPredicate childPredicate : children) {
            if (childPredicate.evaluate(valueList)) {
                return true;
            }
        }
        return false;
    }

    @Override
	public StarPredicate or(StarPredicate predicate) {
        if (predicate instanceof OrPredicate
            && predicate.getConstrainedColumnBitKey().equals(
                getConstrainedColumnBitKey()))
        {
            // Do not collapse OrPredicates with different number of columns.
            // Keeping them separate helps the SQL translation to IN-list.
            ListPredicate that = (ListPredicate) predicate;
            final List<StarPredicate> list =
                new ArrayList<>(children);
            list.addAll(that.children);
            return new OrPredicate(list);
        } else {
            final List<StarPredicate> list =
                new ArrayList<>(children);
            list.add(predicate);
            return new OrPredicate(list);
        }
    }

    @Override
	public StarPredicate and(StarPredicate predicate) {
        List<StarPredicate> list = new ArrayList<>();
        list.add(this);
        list.add(predicate);
        return new AndPredicate(list);
    }

    @Override
	protected String getOp() {
        return "or";
    }
}
