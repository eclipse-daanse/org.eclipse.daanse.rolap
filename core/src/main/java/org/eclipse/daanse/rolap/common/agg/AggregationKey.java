/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarPredicate;

/**
 * Column context that an Aggregation is computed for.
 *
 * Column context has two components:
 *
 * The column constraints which define the dimentionality of an
 *   Aggregation
 * An orthogonal context for which the measures are defined. This context
 *   is sometimes referred to as the compound member predicates, and usually of
 *   the shape:
 *      OR(AND(column predicates))
 *
 *
 * Any column is only used in either column context or compound context, not
 * both.
 *
 * @author Rushan Chen
 */
public class AggregationKey
{
    /**
     * This is needed because for a Virtual Cube: two CellRequests
     * could have the same BitKey but have different underlying
     * base cubes. Without this, one get the result in the
     * SegmentArrayQuerySpec addMeasure Util.assertTrue being
     * triggered (which is what happened).
     */
    private final RolapStar star;

    private final BitKey constrainedColumnsBitKey;

    /**
     * List of StarPredicate (representing the predicate
     * defining the compound member).
     *
     * In sorted order of BitKey. This ensures that the map is deternimistic
     * (otherwise different runs generate SQL statements in different orders),
     * and speeds up comparison.
     */
    final List<StarPredicate> compoundPredicateList;

    private int hashCode;

    /**
     * Creates an AggregationKey.
     *
     * @param request Cell request
     */
    public AggregationKey(CellRequest request) {
        this.constrainedColumnsBitKey = request.getConstrainedColumnsBitKey();
        this.star = request.getMeasure().getStar();
        Map<BitKey, StarPredicate> compoundPredicateMap =
            request.getCompoundPredicateMap();
        this.compoundPredicateList =
            compoundPredicateMap == null
                ? Collections.<StarPredicate>emptyList()
                : new ArrayList<>(compoundPredicateMap.values());
    }

    public final int computeHashCode() {
        return computeHashCode(
            constrainedColumnsBitKey,
            star,
            compoundPredicateList == null
                ? null
                : new AbstractList<BitKey>() {
                    @Override
					public BitKey get(int index) {
                        return compoundPredicateList.get(index)
                            .getConstrainedColumnBitKey();
                    }

                    @Override
					public int size() {
                        return compoundPredicateList.size();
                    }
                });
    }

    public static int computeHashCode(
        BitKey constrainedColumnsBitKey,
        RolapStar star,
        Collection<BitKey> compoundPredicateBitKeys)
    {
        int retCode = constrainedColumnsBitKey.hashCode();
        retCode = Util.hash(retCode, star);
        return Util.hash(retCode, compoundPredicateBitKeys);
    }

    @Override
	public int hashCode() {
        if (hashCode == 0) {
            // Compute hash code on first use. It is expensive to compute, and
            // not always required.
            hashCode = computeHashCode();
        }
        return hashCode;
    }

    @Override
	public boolean equals(Object other) {
        if (!(other instanceof AggregationKey that)) {
            return false;
        }
        return constrainedColumnsBitKey.equals(that.constrainedColumnsBitKey)
            && star.equals(that.star)
            && equalAggregationKey(compoundPredicateList, that.compoundPredicateList);
    }

    /**
     * Returns whether two lists of compound predicates are equal.
     *
     * @param list1 First compound predicate map
     * @param list2 Second compound predicate map
     * @return Whether compound predicate maps are equal
     */
    static boolean equalAggregationKey(
        final List<StarPredicate> list1,
        final List<StarPredicate> list2)
    {
        if (list1 == null) {
            return list2 == null;
        }
        if (list2 == null) {
            return false;
        }
        final int size = list1.size();
        if (size != list2.size()) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            StarPredicate pred1 = list1.get(i);
            StarPredicate pred2 = list2.get(i);
            if (!pred1.equalConstraint(pred2)) {
                return false;
            }
        }
        return true;
    }

    @Override
	public String toString() {
        return
            new StringBuilder(star.getFactTable().getTableName())
                .append(" ")
                .append(constrainedColumnsBitKey.toString())
                .append("\n")
                .append(
                    (compoundPredicateList == null ? "{}" : compoundPredicateList.toString())
                ).toString();
    }

    /**
     * Returns the bitkey of columns that constrain this aggregation.
     *
     * @return Bitkey of contraining columns
     */
    public final BitKey getConstrainedColumnsBitKey() {
        return constrainedColumnsBitKey;
    }

    /**
     * Returns the star.
     *
     * @return Star
     */
    public final RolapStar getStar() {
        return star;
    }

    /**
     * Returns the list of compound predicates.
     *
     * @return list of predicates
     */
    public List<StarPredicate> getCompoundPredicateList() {
        return compoundPredicateList;
    }
}
