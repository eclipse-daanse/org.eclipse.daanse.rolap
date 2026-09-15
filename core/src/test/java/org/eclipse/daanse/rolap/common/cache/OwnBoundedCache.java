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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.ToLongBiFunction;

/**
 * Library-free weight-bounded {@link SimpleCache} contender: sampled LRU
 * over a ConcurrentHashMap. Reads are lock-free (a CHM get plus an opaque
 * access-timestamp write); eviction runs under a tryLock by advancing a
 * persistent "hand" iterator over the map, sampling M entries and evicting
 * the least recently used until the weight account is under the cap. An
 * entry heavier than the whole cap is not kept, matching Caffeine.
 *
 * <p>
 * The weight account stays correct under put/remove/evict races through
 * {@code retire}: a CAS on the node's retired flag debits each node's
 * weight exactly once, whichever path drops it.
 */
final class OwnBoundedCache<K, V> implements SimpleCache<K, V> {

    private static final int SAMPLE = 8;
    private static final VarHandle ACCESS;
    private static final VarHandle RETIRED;

    static {
        try {
            ACCESS = MethodHandles.lookup().findVarHandle(Node.class, "access", long.class);
            RETIRED = MethodHandles.lookup().findVarHandle(Node.class, "retired", boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static final class Node<V> {
        final V value;
        final long weight;
        @SuppressWarnings("unused")
        volatile long access;
        @SuppressWarnings("unused")
        volatile boolean retired;

        Node(V value, long weight) {
            this.value = value;
            this.weight = weight;
            this.access = System.nanoTime();
        }
    }

    private final ConcurrentHashMap<K, Node<V>> map = new ConcurrentHashMap<>();
    private final AtomicLong weightUsed = new AtomicLong();
    private final ReentrantLock evictionLock = new ReentrantLock();
    private final long maxWeight;
    private final ToLongBiFunction<K, V> weigher;
    private Iterator<Map.Entry<K, Node<V>>> hand; // only touched under evictionLock

    private OwnBoundedCache(long maxWeight, ToLongBiFunction<K, V> weigher) {
        this.maxWeight = maxWeight;
        this.weigher = weigher;
    }

    static <K, V> OwnBoundedCache<K, V> ofEntries(long maxEntries) {
        return new OwnBoundedCache<>(maxEntries, (k, v) -> 1L);
    }

    static <K, V> OwnBoundedCache<K, V> weighted(long maxWeight, ToLongBiFunction<K, V> weigher) {
        return new OwnBoundedCache<>(maxWeight, weigher);
    }

    /** Debits the node's weight exactly once, whichever path drops it. */
    private void retire(Node<V> node) {
        if (node != null && RETIRED.compareAndSet(node, false, true)) {
            weightUsed.addAndGet(-node.weight);
        }
    }

    private long weightOf(K key, V value) {
        return Math.max(0, weigher.applyAsLong(key, value));
    }

    @Override
    public V put(K key, V value) {
        Objects.requireNonNull(value, "use remove(key) instead of put(key, null)");
        long weight = weightOf(key, value);
        if (weight > maxWeight) {
            // an entry heavier than the cap is not kept
            Node<V> previous = map.remove(key);
            retire(previous);
            return previous == null ? null : previous.value;
        }
        Node<V> node = new Node<>(value, weight);
        weightUsed.addAndGet(weight);
        Node<V> previous = map.put(key, node);
        retire(previous);
        evictIfNeeded();
        return previous == null ? null : previous.value;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        Objects.requireNonNull(value, "use remove(key) instead of put(key, null)");
        Node<V> existing = map.get(key);
        if (existing != null && !(boolean) RETIRED.getVolatile(existing)) {
            ACCESS.setOpaque(existing, System.nanoTime());
            return existing.value;
        }
        long weight = weightOf(key, value);
        if (weight > maxWeight) {
            return existing == null ? null : existing.value;
        }
        Node<V> node = new Node<>(value, weight);
        Node<V> raced = map.putIfAbsent(key, node);
        if (raced != null) {
            return raced.value;
        }
        weightUsed.addAndGet(weight);
        evictIfNeeded();
        return null;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remapping) {
        Objects.requireNonNull(value);
        Object[] result = new Object[1];
        map.compute(key, (k, existing) -> {
            V existingValue = existing == null ? null : existing.value;
            V newValue = existingValue == null ? value : remapping.apply(existingValue, value);
            result[0] = newValue;
            if (newValue == null) {
                retire(existing);
                return null;
            }
            if (newValue == existingValue) {
                return existing;
            }
            retire(existing);
            Node<V> node = new Node<>(newValue, weightOf(k, newValue));
            weightUsed.addAndGet(node.weight);
            return node;
        });
        evictIfNeeded();
        @SuppressWarnings("unchecked")
        V merged = (V) result[0];
        return merged;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Objects.requireNonNull(oldValue, "oldValue");
        Objects.requireNonNull(newValue, "use remove(key) instead of replacing with null");
        Node<V> existing = map.get(key);
        if (existing == null || !Objects.equals(existing.value, oldValue)) {
            return false;
        }
        long weight = weightOf(key, newValue);
        if (weight > maxWeight) {
            // an entry heavier than the cap is not kept
            if (map.remove(key, existing)) {
                retire(existing);
            }
            return false;
        }
        Node<V> node = new Node<>(newValue, weight);
        weightUsed.addAndGet(weight);
        if (!map.replace(key, existing, node)) {
            // lost the race: undo the tentative credit for the new node
            weightUsed.addAndGet(-weight);
            return false;
        }
        retire(existing);
        evictIfNeeded();
        return true;
    }

    @Override
    public V get(K key) {
        Node<V> node = map.get(key);
        if (node == null) {
            return null;
        }
        ACCESS.setOpaque(node, System.nanoTime());
        return node.value;
    }

    @Override
    public V remove(K key) {
        Node<V> previous = map.remove(key);
        retire(previous);
        return previous == null ? null : previous.value;
    }

    @Override
    public void clear() {
        Iterator<Map.Entry<K, Node<V>>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, Node<V>> entry = iterator.next();
            iterator.remove();
            retire(entry.getValue());
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    private void evictIfNeeded() {
        if (weightUsed.get() <= maxWeight || !evictionLock.tryLock()) {
            return; // a loser returns; overshoot is bounded by in-flight puts
        }
        try {
            while (weightUsed.get() > maxWeight && !map.isEmpty()) {
                K victimKey = null;
                Node<V> victim = null;
                long oldest = Long.MAX_VALUE;
                for (int i = 0; i < SAMPLE; i++) {
                    if (hand == null || !hand.hasNext()) {
                        hand = map.entrySet().iterator();
                        if (!hand.hasNext()) {
                            return;
                        }
                    }
                    Map.Entry<K, Node<V>> candidate = hand.next();
                    Node<V> node = candidate.getValue();
                    if ((boolean) RETIRED.getVolatile(node)) {
                        continue;
                    }
                    long access = (long) ACCESS.getOpaque(node);
                    if (access < oldest) {
                        oldest = access;
                        victimKey = candidate.getKey();
                        victim = node;
                    }
                }
                if (victim == null) {
                    continue; // sample hit only retired nodes; advance again
                }
                if (map.remove(victimKey, victim)) {
                    retire(victim);
                } else {
                    retire(victim); // replaced concurrently; debit our view
                }
            }
        } finally {
            evictionLock.unlock();
        }
    }

    @Override
    public void execute(Task<K, V> task) {
        final Iterator<Map.Entry<K, Node<V>>> raw = map.entrySet().iterator();
        task.execute(new Iterator<Map.Entry<K, V>>() {
            private Map.Entry<K, V> pending;
            private Map.Entry<K, Node<V>> pendingRaw;
            private Map.Entry<K, Node<V>> lastReturned;

            @Override
            public boolean hasNext() {
                while (pending == null && raw.hasNext()) {
                    Map.Entry<K, Node<V>> candidate = raw.next();
                    pending = new AbstractMap.SimpleImmutableEntry<>(
                        candidate.getKey(), candidate.getValue().value);
                    pendingRaw = candidate;
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
                if (map.remove(lastReturned.getKey(), lastReturned.getValue())) {
                    retire(lastReturned.getValue());
                }
                lastReturned = null;
            }
        });
    }

    /** Current weight account, for the bench's drift invariant. */
    long weightUsed() {
        return weightUsed.get();
    }
}
