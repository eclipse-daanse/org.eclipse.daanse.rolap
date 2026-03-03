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


//   Copyright (c) 1999-2007 CERN - European Organization for Nuclear Research.
//   Permission to use, copy, modify, distribute and sell this software
//   and its documentation for any purpose is hereby granted without fee,
//   provided that the above copyright notice appear in all copies and
//   that both that copyright notice and this permission notice appear in
//   supporting documentation. CERN makes no representations about the
//   suitability of this software for any purpose. It is provided "as is"
//   without expressed or implied warranty.

// Created from package cern.colt.map by Richard Emberson, 2007/1/23.
// For the source to the Colt project, go to:
// http://dsd.lbl.gov/~hoschek/colt/
package org.eclipse.daanse.rolap.util;

import java.util.HashMap;
import java.util.Iterator;

public class ObjectPool<T> {

    protected static final int DEFAULT_CAPACITY = 277;

    /**
     *  The number of distinct associations in the map; its "size()".
     */
    protected int distinct;

    protected int highWaterMark;

    /**
     * The minimum load factor for the hashtable.
     */
    protected double minLoadFactor;

    /**
     * The maximum load factor for the hashtable.
     */
    protected double maxLoadFactor;

    /**
     * The number of table entries in state==FREE.
     */
    protected int freeEntries;
    
    private final HashMap<T, T> map;

    public ObjectPool() {
        this(DEFAULT_CAPACITY);
    }

    public ObjectPool(int initialCapacity) {
        map = new HashMap<>(initialCapacity);
    }
    /**
     * Return the number of entries in the ObjectPool.
     *
     * @return number of entries.
     */
    public int size() {
        return map.size();
    }

    /**
     * Returns true it the Object is already in the ObjectPool and false
     * otherwise.
     *
     * @param key Object to test if member already or not.
     * @return true is already member
     */
    public boolean contains(T key) {
        return map.containsKey(key);
    }

    /**
     * Adds an object to the ObjectPool if it is not
     * already in the pool or returns the object that is already in the
     * pool that matches the object being added.
     *
     * @param key Object to add to pool
     * @return Equivalent object, if it exists, otherwise key
     */
    public T add(T key) {
        T existing = map.putIfAbsent(key, key);
    	return existing != null ? existing : key;
    }

    /**
     * Removes all objects from the pool but keeps the current size of
     * the internal storage.
     */
    public void clear() {
        map.clear();
    }

    /**
     * Returns an Iterator of this ObjectPool. The order of
     * the Objects returned by the Iterator can not be
     * counted on to be in the same order as they were inserted
     * into the ObjectPool.  The
     * Iterator returned does not
     * support the removal of ObjectPool members.
     */
    public Iterator<T> iterator() {
        return map.values().iterator();
    }
}
