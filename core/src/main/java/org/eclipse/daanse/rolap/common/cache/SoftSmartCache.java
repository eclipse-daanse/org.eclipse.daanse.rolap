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

import org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength;
import org.apache.commons.collections4.map.ReferenceMap;

/**
 * An implementation of {@link SmartCacheImpl} backed by a commons-collections
 * {@link ReferenceMap} whose keys AND values are soft references.
 *
 * <p>This is the reference semantics the original mondrian used
 * ({@code org.apache.commons.collections.map.ReferenceMap(SOFT, SOFT)}), now on
 * commons-collections4. Both the key and the value of an entry are soft, so the
 * garbage collector reclaims a member and the ancestor chain it points at as a
 * unit under memory pressure — nothing strong pins a soft value in place.</p>
 *
 * <p>The two intermediate attempts each broke this differently. Caffeine cannot
 * express soft <em>keys</em>, so {@code softValues()} with strong keys let a
 * strong {@code MemberKeyR} key pin its parent's soft value: whole ancestor
 * chains could never be reclaimed (an unbounded-retention leak). Replacing that
 * with a strong, {@code maximumSize(100_000)}-bounded cache plus a
 * memory-pressure clear-all fixed the leak but held up to 100k entries strongly
 * between clears, so the GC could not shed them incrementally and thrashed under
 * a full heap — measured at roughly 3x the full-suite wall time. The soft/soft
 * map restores the original per-entry behaviour without either trap.</p>
 *
 * This class does not enforce any synchronization, because
 * this is handled by SmartCacheImpl.
 *
 * @author av, lboudreau
 * @since Nov 3, 2005
 */
public class SoftSmartCache<K, V> extends SmartCacheImpl<K, V> {

    private final Map<K, V> cache =
        new ReferenceMap<>(ReferenceStrength.SOFT, ReferenceStrength.SOFT);

    @Override
	public V putImpl(K key, V value) {
        // Null values are the same as a 'remove'
        // Convert the operation because ReferenceMap doesn't
        // like null values.
        if (value == null) {
            return cache.remove(key);
        } else {
            return cache.put(key, value);
        }
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

// End SoftSmartCache.java

