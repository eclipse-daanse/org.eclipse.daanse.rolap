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


package org.eclipse.daanse.rolap.common;

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.genericExpression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.key.BitKey;

/**
 * A RolapCacheRegion represents a region of multidimensional space
 * in the cache.
 *
 * The region is represented in terms of the columns of a given
 * {@link org.eclipse.daanse.rolap.common.RolapStar}, and constraints on those columns.
 *
 * Compare with {@link org.eclipse.daanse.olap.api.CacheControl.CellRegion}: a
 * CellRegion is in terms of {@link org.eclipse.daanse.olap.api.element.Member} objects
 * (logical); whereas a RolapCacheRegion is in terms of columns
 * (physical).
 */
public class RolapCacheRegion {
    private final BitKey bitKey;
    private final Map<Integer, StarColumnPredicate> columnPredicates =
        new HashMap<>();
    private final Map<String, StarColumnPredicate> columnPredicatesByName =
        new HashMap<>();
    private Map<List<RolapStar.Column>, StarPredicate> predicates =
        new HashMap<>();

    public RolapCacheRegion(
        RolapStar star,
        List<RolapStar.Measure> starMeasureList)
    {
        bitKey = BitKey.Factory.makeBitKey(star.getColumnCount());
        for (RolapStar.Measure measure : starMeasureList) {
            bitKey.set(measure.getBitPosition());
        }
    }

    public BitKey getConstrainedColumnsBitKey() {
        return bitKey;
    }

    /**
     * Adds a predicate which applies to a single column.
     *
     * @param column Constrained column
     * @param predicate Predicate
     */
    public void addPredicate(
        RolapStar.Column column,
        StarColumnPredicate predicate)
    {
        int bitPosition = column.getBitPosition();
        assert !bitKey.get(bitPosition);
        bitKey.set(bitPosition);
        columnPredicates.put(bitPosition, predicate);
        columnPredicatesByName.put(
            genericExpression(column.getExpression()),
            predicate);
    }

    /**
     * Returns the predicate associated with the
     * columnOrdinalth column.
     *
     * @param columnOrdinal Column ordinal
     * @return Predicate, or null if not constrained
     */
    public StarColumnPredicate getPredicate(int columnOrdinal) {
        return columnPredicates.get(columnOrdinal);
    }

    /**
     * Returns the predicate associated with the
     * columnName, where column name is
     * the generic SQL expression in the form of:
     *
     * &nbsp;&nbsp;&nbsp;&nbsp;table.column
     *
     * @param columnName Column name
     * @return Predicate, or null if not constrained
     */
    public StarColumnPredicate getPredicate(String columnName) {
        return columnPredicatesByName.get(columnName);
    }

    /**
     * Adds a predicate which applies to multiple columns.
     *
     * The typical example of a multi-column predicate is a member
     * constraint. For example, the constraint "m between 1997.Q3 and
     * 1998.Q2" translates into "year = 1997 and quarter >= Q3 or year =
     * 1998 and quarter less or = Q2".
     *
     * @param predicate Predicate
     */
    public void addPredicate(StarPredicate predicate)
    {
        final List<RolapStar.Column> columnList =
            predicate.getConstrainedColumnList();
        predicates.put(
            new ArrayList<>(columnList),
            predicate);
        for (RolapStar.Column column : columnList) {
            bitKey.set(column.getBitPosition());
        }
    }

    /**
     * Returns a collection of all multi-column predicates.
     *
     * @return Collection of all multi-column constraints
     */
    public Collection<StarPredicate> getPredicates() {
        return predicates.values();
    }

    /**
     * Returns the list of all column predicates.
     */
    public Collection<StarColumnPredicate> getColumnPredicates() {
        return columnPredicates.values();
    }
}
