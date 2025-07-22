/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
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

package org.eclipse.daanse.rolap.common;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.daanse.rolap.common.cache.SmartCache;
import org.eclipse.daanse.rolap.common.cache.SoftSmartCache;

/**
 * Uses a SmartCache to store a collection of values.
 * Supplements put operations with an "addToEntry", which
 * supports incrementally adding to the collection associated
 * with key.
 */
public class SmartIncrementalCache<K, V extends Collection> {
    SmartCache<K, V> cache;

    public SmartIncrementalCache() {
        cache = new SoftSmartCache<>();
    }

    public V put(final K  key, final V value) {
        return cache.put(key, value);
    }

    public V get(K key) {
        return cache.get(key);
    }

    public void clear() {
        cache.clear();
    }

    public void addToEntry(final K key, final V value) {
        cache
            .execute(
                new SmartCache.SmartCacheTask
                    <K, V>() {
                    @Override
					public void execute(Iterator<Map.Entry<K, V>> iterator) {
                        // iterator is ignored,
                        // we're updating a single entry and
                        // we have the key.
                        if (cache.get(key) == null) {
                            cache.put(key, value);
                        } else {
                            cache.get(key).addAll(value);
                        }
                    } });
    }

    SmartCache<K, V> getCache() {
        return cache;
    }

    public void setCache(SmartCache<K, V> cache) {
        this.cache = cache;
    }

}
