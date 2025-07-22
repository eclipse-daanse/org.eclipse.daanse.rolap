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


package org.eclipse.daanse.rolap.util;

import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.daanse.olap.common.Util;

/**
 * A limited Map implementation which supports waiting for a value
 * to be available when calling get(). Intended for use with
 * producer/consumer queues, where a producer thread puts a value into
 * the collection with a separate thread waiting to get that value.
 * Currently used by the Actor implementations in
 * SegmentCacheManager and MonitorImpl.
 *
 * Thread safety. BlockingHashMap is thread safe.  The class
 * delegates all get and put operations to a ConcurrentHashMap. 
 *
 * @param <K> request (key) type
 * @param <V> response (value) type
 */
public class BlockingHashMap<K, V> {
    private final ConcurrentHashMap<K, SlotFuture<V>> map;

    /**
     * Creates a BlockingHashMap with given capacity.
     *
     * @param capacity Capacity
     */
    public BlockingHashMap(int capacity) {
        map = new ConcurrentHashMap<>(capacity);
    }

    /**
     * Places a (request, response) pair onto the map.
     *
     * @param k key
     * @param v value
     */
    public void put(K k, V v) {
        map.putIfAbsent(k, new SlotFuture<>());
        map.get(k).put(v);
    }

    /**
     * Retrieves the response from the map matching the given key,
     * blocking until it is received.
     *
     * @param k key
     * @return value
     * @throws InterruptedException if interrupted while waiting
     */
    public V get(K k) throws InterruptedException {
        map.putIfAbsent(k, new SlotFuture<>());
        V v = Util.safeGet(
            map.get(k),
            "Waiting to retrieve a value from BlockingHashMap.");
        map.remove(k);
        return v;
    }
}
