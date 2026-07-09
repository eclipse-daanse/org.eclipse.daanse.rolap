/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2006-2017 Hitachi Vantara
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
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * An implementation of {@link SmartCacheImpl} backed by a Caffeine cache
 * configured with {@code softValues()}: only the <em>values</em> are held
 * through soft references, so they may be reclaimed by the garbage collector
 * under memory pressure; the keys are held strongly.
 *
 * This class does not enforce any synchronization, because
 * this is handled by SmartCacheImpl.
 *
 * @author av, lboudreau
 * @since Nov 3, 2005
 */
public class SoftSmartCache<K, V> extends SmartCacheImpl<K, V> {

    private final Cache<K, V> cache = Caffeine.newBuilder().softValues().build();

    @Override
	public V putImpl(K key, V value) {
        // Null values are the same as a 'remove'
        // Convert the operation because ReferenceMap doesn't
        // like null values.
        if (value == null) {
            return cache.asMap().remove(key);
        } else {
            return cache.asMap().put(key, value);
        }
    }

    @Override
	public V getImpl(K key) {
        return cache.asMap().get(key);
    }

    @Override
	public V removeImpl(K key) {
        return cache.asMap().remove(key);
    }

    @Override
	public void clearImpl() {
        cache.asMap().clear();
    }

    @Override
	public int sizeImpl() {
        return cache.asMap().size();
    }

    @Override
	public Iterator<Map.Entry<K, V>> iteratorImpl() {
        return cache.asMap().entrySet().iterator();
    }
}

// End SoftSmartCache.java

