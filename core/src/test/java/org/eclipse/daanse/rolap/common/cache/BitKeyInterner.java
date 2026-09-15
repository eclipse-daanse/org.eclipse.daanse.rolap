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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.daanse.olap.key.BitKey;

/**
 * Canonicalizes frozen {@link BitKey}s: value-equal keys share one instance,
 * so map lookups and equals short-circuit on identity and each dimensionality
 * is retained once. Only keys that are never mutated again may be interned —
 * the same contract the wide BitKey's hash cache rests on. One interner per
 * star: value equality crosses BitKey variants, and emptyCopy() carries the
 * capacity, so keys of different stars must not canonicalize to each other.
 * Past the cap, keys pass through uninterned.
 *
 * Test-scope measurement fixture: the intern bench shows the extra lookup
 * costs 35-60% cpu per op while the allocation rate stays identical (the
 * fresh key exists before interning; only retention would shrink).
 */
final class BitKeyInterner {

    private static final int CAP = 1024;

    private final ConcurrentMap<BitKey, BitKey> map = new ConcurrentHashMap<>();

    /** The canonical instance for the key's value; the key itself if it is first. */
    BitKey intern(BitKey key) {
        BitKey canonical = map.get(key);
        if (canonical != null) {
            return canonical;
        }
        if (map.size() >= CAP) {
            return key;
        }
        canonical = map.putIfAbsent(key, key);
        return canonical != null ? canonical : key;
    }

    /** Number of canonical keys held. */
    int size() {
        return map.size();
    }
}
