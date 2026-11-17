/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.cache;

import org.eclipse.daanse.olap.cache.SimpleCache;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * A {@link SimpleCache} with strong keys and softly referenced values on a
 * ConcurrentHashMap: reads and writes are lock-free, the garbage collector
 * may reclaim any value under memory pressure, and a reference queue purges
 * emptied entries on every operation. An entry lives exactly as long as its
 * value — the canonical store for member identity, where a key must answer
 * as long as the member is reachable anywhere.
 *
 * <p>
 * The keys must stay strong: lookups are equals-based (a fresh but equal key
 * must hit), which rules out identity-based weak/soft key schemes such as
 * Caffeine's {@code weakKeys()}. A key may transitively pin other values
 * (a member key holds the parent member); the prompt purge unpins them as
 * the garbage collector clears values, one generation per collection.
 */
public class SoftValueCache<K, V> implements SimpleCache<K, V> {

    private static final class ValueRef<K, V> extends SoftReference<V> {
        private final K key;

        private ValueRef(K key, V value, ReferenceQueue<V> queue) {
            super(value, queue);
            this.key = key;
        }
    }

    private final ConcurrentHashMap<K, ValueRef<K, V>> map = new ConcurrentHashMap<>();
    private final ReferenceQueue<V> queue = new ReferenceQueue<>();

    /** Drops the entries whose values the garbage collector reclaimed. */
    private void purge() {
        Reference<? extends V> cleared;
        while ((cleared = queue.poll()) != null) {
            @SuppressWarnings("unchecked")
            ValueRef<K, V> ref = (ValueRef<K, V>) cleared;
            map.remove(ref.key, ref);
        }
    }

    @Override
    public V put(K key, V value) {
        Objects.requireNonNull(value, "use remove(key) instead of put(key, null)");
        purge();
        ValueRef<K, V> previous = map.put(key, new ValueRef<>(key, value, queue));
        return previous == null ? null : previous.get();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        Objects.requireNonNull(value, "use remove(key) instead of put(key, null)");
        purge();
        ValueRef<K, V> offered = new ValueRef<>(key, value, queue);
        while (true) {
            ValueRef<K, V> existing = map.putIfAbsent(key, offered);
            if (existing == null) {
                return null;
            }
            V existingValue = existing.get();
            if (existingValue != null) {
                return existingValue;
            }
            // the existing entry is emptied; take its place
            if (map.replace(key, existing, offered)) {
                return null;
            }
        }
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remapping) {
        Objects.requireNonNull(value);
        purge();
        Object[] merged = new Object[1];
        map.compute(key, (k, existing) -> {
            V existingValue = existing == null ? null : existing.get();
            V newValue = existingValue == null ? value : remapping.apply(existingValue, value);
            merged[0] = newValue;
            if (newValue == null) {
                return null;
            }
            return newValue == existingValue ? existing : new ValueRef<>(k, newValue, queue);
        });
        @SuppressWarnings("unchecked")
        V result = (V) merged[0];
        return result;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Objects.requireNonNull(oldValue, "oldValue");
        Objects.requireNonNull(newValue, "use remove(key) instead of replacing with null");
        purge();
        ValueRef<K, V> existing = map.get(key);
        if (existing == null || !Objects.equals(existing.get(), oldValue)) {
            return false;
        }
        // CAS on the ref wrapper: only the exact entry we just read is swapped
        return map.replace(key, existing, new ValueRef<>(key, newValue, queue));
    }

    @Override
    public V get(K key) {
        purge();
        ValueRef<K, V> ref = map.get(key);
        if (ref == null) {
            return null;
        }
        V value = ref.get();
        if (value == null) {
            map.remove(key, ref);
        }
        return value;
    }

    @Override
    public V remove(K key) {
        purge();
        ValueRef<K, V> previous = map.remove(key);
        return previous == null ? null : previous.get();
    }

    @Override
    public void clear() {
        map.clear();
        while (queue.poll() != null) {
            // drain
        }
    }

    @Override
    public int size() {
        purge();
        return map.size();
    }

    @Override
    public void execute(Task<K, V> task) {
        purge();
        final Iterator<Map.Entry<K, ValueRef<K, V>>> raw = map.entrySet().iterator();
        task.execute(new Iterator<Map.Entry<K, V>>() {
            private Map.Entry<K, V> pending;
            private Map.Entry<K, ValueRef<K, V>> pendingRaw;
            private Map.Entry<K, ValueRef<K, V>> lastReturned;

            @Override
            public boolean hasNext() {
                while (pending == null && raw.hasNext()) {
                    Map.Entry<K, ValueRef<K, V>> candidate = raw.next();
                    V value = candidate.getValue().get();
                    if (value != null) {
                        pending = Map.entry(candidate.getKey(), value);
                        pendingRaw = candidate;
                    }
                }
                return pending != null;
            }

            @Override
            public Map.Entry<K, V> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Map.Entry<K, V> entry = pending;
                lastReturned = pendingRaw;
                pending = null;
                pendingRaw = null;
                return entry;
            }

            @Override
            public void remove() {
                if (lastReturned == null) {
                    throw new IllegalStateException();
                }
                // precise removal by (key, ref): unaffected by read-ahead
                map.remove(lastReturned.getKey(), lastReturned.getValue());
                lastReturned = null;
            }
        });
    }
}
