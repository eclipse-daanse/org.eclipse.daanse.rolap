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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.eclipse.daanse.olap.common.Util;
import  org.eclipse.daanse.olap.util.ArraySortedSet;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;

/**
 * Collection of values of one of the columns that parameterizes a
 * {@link Segment}.
 */
public class SegmentAxis {

    /**
     * Constraint on the keys in this Axis. Never null.
     */
    final StarColumnPredicate predicate;

    /**
     * Whether predicate is always true.
     */
    private final boolean predicateAlwaysTrue;

    private final Set<Object> predicateValues;

    /**
     * Map holding the position of each key value.
     *
     * TODO: Hold keys in a sorted array, then deduce ordinal by doing
     * binary search.
     */
    private final Map<Comparable, Integer> mapKeyToOffset;

    /**
     * Actual key values retrieved.
     */
    private final Comparable[] keys;

    private static final Integer ZERO = Integer.valueOf(0);
    private static final Integer ONE = Integer.valueOf(1);
    private static final Comparable[] NO_COMPARABLES = new Comparable[0];

    /**
     * Internal constructor.
     */
    private SegmentAxis(
        StarColumnPredicate predicate,
        Comparable[] keys,
        boolean safe)
    {
        this.predicate = predicate;
        this.predicateAlwaysTrue =
            predicate instanceof LiteralStarPredicate
            && ((LiteralStarPredicate) predicate).getValue();
        this.predicateValues = predicateValueSet(predicate);
        if (keys.length == 0) {
            // Optimize the case where axis is empty. Not that infrequent:
            // it records that mondrian has looked in the database and found
            // nothing.
            this.keys = NO_COMPARABLES;
            this.mapKeyToOffset = Collections.emptyMap();
        } else {
            this.keys = keys;
            mapKeyToOffset =
                new HashMap<>(keys.length * 3 / 2);
            for (int i = 0; i < keys.length; i++) {
                mapKeyToOffset.put(keys[i], i);
            }
        }
        assert predicate != null;
        assert safe || isSorted(keys);
    }

    /**
     * Returns whether a list is strictly sorted.
     *
     * @param array array
     * @return whether list is sorted
     */
    public static boolean isSorted(Comparable[] array) {
        for (int i = 0; i < array.length - 1; ++i) {
            if (array[i].compareTo(array[i + 1]) > 0)
                return false;
        }
        return true;
    }

    private static Set<Object> predicateValueSet(
        StarColumnPredicate predicate)
    {
        if (!(predicate instanceof ListColumnPredicate listColumnPredicate)) {
            return null;
        }
        final List<StarColumnPredicate> predicates =
            listColumnPredicate.getPredicates();
        if (predicates.size() < 10) {
            return null;
        }
        final HashSet<Object> set = new HashSet<>();
        for (StarColumnPredicate subPredicate : predicates) {
            if (subPredicate instanceof ValueColumnPredicate valueColumnPredicate) {
                valueColumnPredicate.values(set);
            } else {
                return null;
            }
        }
        return set;
    }

    /**
     * Creates a SegmentAxis populated with an array of key values. The key
     * values must be sorted.
     *
     * @param predicate Predicate defining which keys should appear on
     *                  axis. (If a key passes the predicate but
     *                  is not in the list, every cell with that
     *                  key is assumed to have a null value.)
     * @param keys      Keys
     */
    SegmentAxis(StarColumnPredicate predicate, Comparable[] keys) {
        this(predicate, keys, false);
    }

    /**
     * Creates a SegmentAxis populated with a set of key values.
     *
     * @param predicate Predicate defining which keys should appear on
     *                  axis. (If a key passes the predicate but
     *                  is not in the list, every cell with that
     *                  key is assumed to have a null value.)
     * @param keySet Set of distinct key values, sorted
     * @param hasNull  Whether the axis contains the null value, in addition
     *                 to the values in valueSet
     */
    public SegmentAxis(
        StarColumnPredicate predicate,
        SortedSet<Comparable> keySet,
        boolean hasNull)
    {
        this(predicate, toArray(keySet, hasNull), true);
    }

    private static Comparable[] toArray(
        SortedSet<Comparable> keySet,
        boolean hasNull)
    {
        int size = keySet.size();
        if (hasNull) {
            size++;
        }
        Comparable[] keys = keySet.toArray(new Comparable[size]);
        if (hasNull) {
            keys[size - 1] = Util.sqlNullValue;
        }
        return keys;
    }

    final StarColumnPredicate getPredicate() {
        return predicate;
    }

    public final Comparable[] getKeys() {
        return keys;
    }

    final int getOffset(Comparable key) {
        if (keys.length == 1) {
            return keys[0].equals(key) ? 0 : -1;
        }
        Integer ordinal = mapKeyToOffset.get(key);
        if (ordinal == null) {
            return -1;
        }
        return ordinal;
    }

    /**
     * Returns whether this axis contains a given key, or would contain it
     * if it existed.
     *
     * For example, if this axis is unconstrained, then this method
     * returns true for any value.
     *
     * @param key Key
     * @return Whether this axis would contain key
     */
    public final boolean wouldContain(Object key) {
        return predicateAlwaysTrue
            || (predicateValues != null
                ? predicateValues.contains(key)
                : predicate.evaluate(key));
    }

    /**
     * Returns how many of this SegmentAxis's keys match a given constraint.
     *
     * @param predicate Predicate
     * @return How many keys match constraint
     */
    public int getMatchCount(StarColumnPredicate predicate) {
        int matchCount = 0;
        for (Object key : keys) {
            if (predicate.evaluate(key)) {
                ++matchCount;
            }
        }
        return matchCount;
    }

    @SuppressWarnings({"unchecked"})
    public Pair<SortedSet<Comparable>, Boolean> getValuesAndIndicator() {
        if (keys.length > 0
            && keys[keys.length - 1] == Util.sqlNullValue)
        {
            return (Pair) Pair.of(
                new ArraySortedSet(keys, 0, keys.length - 1),
                Boolean.TRUE);
        } else {
            return (Pair) Pair.of(
                new ArraySortedSet(keys),
                Boolean.FALSE);
        }
    }
}
