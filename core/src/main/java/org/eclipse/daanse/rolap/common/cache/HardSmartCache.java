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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * An implementation of {@link SmartCache} that uses hard
 * references. Used for testing.
 */
public class HardSmartCache <K, V> extends SmartCacheImpl<K, V> {
    Map<K, V> cache = new HashMap<>();

    @Override
	public V putImpl(K key, V value) {
        return cache.put(key, value);
    }

    @Override
	public V getImpl(K key) {
        return cache.get(key);
    }

    @Override
	public V removeImpl(K key) {
        return cache.remove(key);
    }

    @Override
	public void clearImpl() {
        cache.clear();
    }

    @Override
	public int sizeImpl() {
        return cache.size();
    }

    @Override
	public Iterator<Map.Entry<K, V>> iteratorImpl() {
        return cache.entrySet().iterator();
    }
}
