/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara and others
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


package org.eclipse.daanse.rolap.common.cache;

import java.util.Iterator;
import java.util.Map.Entry;

/**
 * Defines a cache API. Implementations exist for hard and soft references.
 *
 * To iterate over the contents of a cache, you must pass a
 * {@link #execute(SmartCacheTask)} instance. The code using the iterator
 * can be assured that it will be thread safe.
 *
 * Implementations are responsible of enforcing thread safety.
 * @author av
 * @since Nov 21, 2005
 */
public interface SmartCache <K, V> {
    /**
     * Places a key/value pair into the cache.
     *
     * @param key Key
     * @param value Value
     * @return the previous value of key or null
     */
    V put(K key, V value);

    /**
     * Looks up and returns a cache value according to a given key.
     * If the cache does not correspond an entry corresponding to the key,
     * null is returned.
     */
    V get(K key);

    /**
     * Removes a key from the cache.
     *
     * @param key Key
     *
     * @return Previous value associated with the key
     */
    V remove(K key);

    /**
     * Clears the contents of this cache.
     */
    void clear();

    /**
     * Returns the number of elements in cache.
     */
    int size();

    /**
     * Executes a task over the contents of the cache and guarantees
     * exclusive write access while processing.
     * @param task The task to execute.
     */
    void execute(SmartCacheTask<K, V> task);

    /**
     * Defines a task to be run over the entries of the cache.
     * Used in conjunction with {@link #execute(Iterator)}.
     */
    public interface SmartCacheTask<K, V> {
        void execute(
            Iterator<Entry<K, V>> iterator);
    }
}
