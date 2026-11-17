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
package org.eclipse.daanse.rolap.common.member;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.util.SlotFuture;

/**
 * Coordinates concurrent member-list loads per cache key so a reader never
 * holds a lock across SQL: the first claimant of a key becomes the loader
 * and runs the SQL, everyone else awaits the same future. A load key pairs
 * the target with the constraint cache key, mirroring the member-list cache
 * keys.
 *
 * <p>
 * Cache writes are generation-fenced: a flush bumps the generation, and a
 * thread that entered its load under an older generation completes its
 * waiters but must not publish into the cache — the cache asks
 * {@link #threadWritesAllowed()} on every list write, which also covers the
 * writes the SQL tuple reader performs from inside the load.
 *
 * <p>
 * Loads triggered from inside a load on the same thread bypass the registry
 * ({@link #inLoad()}), because awaiting a future owned by the same thread
 * would self-deadlock.
 */
public final class MemberLoadRegistry {

    /** Loads members of one level under one constraint. */
    public record LevelKey(Object level, Object constraintKey) {
    }

    /** Loads children of one member under one constraint. */
    public record ChildrenKey(Object parent, Object constraintKey) {
    }

    /** One claim on a load key: {@code loader} says whether the caller owns the load. */
    public record Claim(Object key, SlotFuture<List<RolapMember>> future, boolean loader) {
    }

    private static final long AWAIT_TIMEOUT_MINUTES = 10;

    private final ConcurrentHashMap<Object, SlotFuture<List<RolapMember>>> inFlight =
        new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private final ThreadLocal<Deque<Long>> fences = ThreadLocal.withInitial(ArrayDeque::new);

    public Claim claim(Object key) {
        SlotFuture<List<RolapMember>> created = new SlotFuture<>();
        SlotFuture<List<RolapMember>> existing = inFlight.putIfAbsent(key, created);
        return existing == null
            ? new Claim(key, created, true)
            : new Claim(key, existing, false);
    }

    /** Publishes the result to the waiters and releases the key. Loader only. */
    public void complete(Claim claim, List<RolapMember> result) {
        assert claim.loader() : "complete() is the loader's call, not a waiter's";
        inFlight.remove(claim.key(), claim.future());
        claim.future().put(result);
    }

    /** Propagates the failure to the waiters and releases the key. Loader only. */
    public void fail(Claim claim, Throwable throwable) {
        assert claim.loader() : "fail() is the loader's call, not a waiter's";
        inFlight.remove(claim.key(), claim.future());
        claim.future().fail(throwable);
    }

    /**
     * Waits for another thread's load of the claimed key. The wait runs in
     * one-second slices with a cancel check between them: a canceled or
     * timed-out execution leaves the wait with its own exception instead
     * of sitting out the full timeout on someone else's load (the load
     * itself continues for its remaining waiters). The overall cap stays.
     */
    public List<RolapMember> await(Claim claim) {
        final long deadlineNanos =
            System.nanoTime() + TimeUnit.MINUTES.toNanos(AWAIT_TIMEOUT_MINUTES);
        while (true) {
            ExecutionContext executionContext =
                ExecutionContext.currentOrNull();
            if (executionContext != null) {
                // context state first (set by the tree cancel), then the
                // Execution (its state can flip before it propagates)
                executionContext.checkCancelOrTimeout();
                Execution execution =
                    executionContext.getExecution();
                if (execution != null) {
                    execution.checkCancelOrTimeout();
                }
            }
            try {
                return claim.future().get(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw Util.newInternal(e, "interrupted while awaiting a member load");
            } catch (TimeoutException e) {
                if (System.nanoTime() - deadlineNanos >= 0) {
                    throw Util.newInternal(e, "timed out awaiting a member load");
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw Util.newInternal(cause, "member load failed");
            }
        }
    }

    /**
     * The load registry governing a reader's member-cache writes: the
     * reader itself when it caches, else the caching reader beneath a
     * delegating chain - role-restricted (and ragged) hierarchies wrap
     * the caching reader in a RestrictedMemberReader, and a bare
     * instanceof CachingMemberReader missed them. Null when nothing on
     * the chain caches (members=off).
     */
    public static MemberLoadRegistry of(MemberReader reader) {
        if (registryOf(reader) instanceof MemberLoadRegistry registry) {
            return registry;
        }
        while (reader instanceof DelegatingMemberReader delegating) {
            reader = delegating.memberReader;
            if (registryOf(reader) instanceof MemberLoadRegistry registry) {
                return registry;
            }
        }
        return null;
    }

    private static MemberLoadRegistry registryOf(MemberReader reader) {
        if (reader instanceof CachingMemberReader caching) {
            return caching.loadRegistry();
        }
        if (reader instanceof NoCacheMemberReader noCache) {
            // members=off caches nothing itself, but readers stacked
            // above fence on its registry (K5)
            return noCache.loadRegistry();
        }
        return null;
    }

    /** Invalidates the cache writes of loads already in flight. Every flush calls this. */
    public void bumpGeneration() {
        generation.incrementAndGet();
    }

    /** The current flush generation — fences caches outside this reader. */
    public long generation() {
        return generation.get();
    }

    /**
     * Whether a list-cache write by the current thread may be published: it
     * is not inside a load, or no flush intervened since the load began.
     */
    public boolean threadWritesAllowed() {
        Deque<Long> stack = fences.get();
        return stack.isEmpty() || stack.peek() == generation.get();
    }

    /** A fenced read's value plus whether cache writes stayed allowed. */
    public record Fenced<T>(T value, boolean writesAllowed) {
    }

    /**
     * Runs {@code read} inside enterLoad/exitLoad brackets of EVERY given
     * registry, counting entries so a throw from the k-th enterLoad exits
     * exactly the k-1 already-entered fences (a leaked fence poisons the
     * pooled thread permanently: single-flight bypassed, writes silently
     * discarded against a frozen generation). {@code writesAllowed} is
     * decided INSIDE the bracket: false means a flush overtook the read
     * and its result must not be published to any cache.
     */
    public static <T> Fenced<T> withMemberLoadFences(
            List<MemberLoadRegistry> registries,
            java.util.function.Supplier<T> read) {
        int entered = 0;
        try {
            for (MemberLoadRegistry registry : registries) {
                registry.enterLoad();
                entered++;
            }
            T value = read.get();
            boolean writesAllowed = registries.stream()
                .allMatch(MemberLoadRegistry::threadWritesAllowed);
            return new Fenced<>(value, writesAllowed);
        } finally {
            for (int i = entered - 1; i >= 0; i--) {
                registries.get(i).exitLoad();
            }
        }
    }

    /** Whether the current thread is inside a load it owns. */
    public boolean inLoad() {
        return !fences.get().isEmpty();
    }

    /**
     * Opens a fence bracket on THIS thread. Contract: every enterLoad has
     * exactly one matching exitLoad on the same thread (try/finally, or
     * counted prefix-exit like RolapNativeSet when entering several
     * registries) - an unbalanced exit throws.
     */
    public void enterLoad() {
        fences.get().push(generation.get());
    }

    /** Closes the innermost bracket; throws on an unbalanced call. */
    public void exitLoad() {
        Deque<Long> stack = fences.get();
        if (stack.isEmpty()) {
            fences.remove();
            throw new IllegalStateException(
                "exitLoad without a matching enterLoad on this thread");
        }
        stack.pop();
        if (stack.isEmpty()) {
            fences.remove();
        }
    }
}
