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


package org.eclipse.daanse.rolap.common;

import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.cache.SmartCache;
import org.eclipse.daanse.rolap.common.cache.SoftSmartCache;
import org.eclipse.daanse.rolap.common.sql.SqlConstraint;

/**
 * Uses a {@link org.eclipse.daanse.rolap.common.cache.SmartCache} to store lists of members,
 * where the key depends on a {@link org.eclipse.daanse.rolap.common.sql.SqlConstraint}.
 *
 * Example 1:
 *
 *
 *   select ...
 *   [Customer].[Name].members on rows
 *   ...
 *
 *
 * Example 2:
 *
 *   select ...
 *   NON EMPTY [Customer].[Name].members on rows
 *   ...
 *   WHERE ([Store#14], [Product].[Product#1])
 *
 *
 * The first set, <em>all</em> customers are computed, in the second only
 * those, who have bought Product#1 in Store#14. We want to put both results
 * into the cache. Then the key for the cache entry is the Level that the
 * members belong to <em>plus</em> the costraint that restricted the amount of
 * members fetched. For Level.Members the key consists of the Level and the
 * cacheKey of the {@link org.eclipse.daanse.rolap.common.sql.SqlConstraint}.
 *
 * @see org.eclipse.daanse.rolap.common.sql.SqlConstraint#getCacheKey
 *
 * @author av
 * @since Nov 21, 2005
 */
public class SmartMemberListCache <K, V> {
    public SmartCache<Pair<K, Object>, V> cache;

    public SmartMemberListCache() {
        cache = new SoftSmartCache<>();
    }

    public Object put(K key, SqlConstraint constraint, V value) {
        Object cacheKey = constraint.getCacheKey();
        if (cacheKey == null) {
            return null;
        }
        Pair<K, Object> key2 = new Pair<>(key, cacheKey);
        return cache.put(key2, value);
    }

    public V get(K key, SqlConstraint constraint) {
        Pair<K, Object> key2 =
            new Pair<>(key, constraint.getCacheKey());
        return cache.get(key2);
    }

    public void clear() {
        cache.clear();
    }

    SmartCache<Pair<K, Object>, V> getCache() {
        return cache;
    }

    public void setCache(SmartCache<Pair<K, Object>, V> cache) {
        this.cache = cache;
    }
}
