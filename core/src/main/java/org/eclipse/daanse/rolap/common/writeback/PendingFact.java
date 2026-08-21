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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.writeback;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One cube's writeback fact, held still for the length of one statement.
 * <p>
 * A cube's fact source is not something a caller owns a copy of: rewriting it
 * to include a session's pending values rewrites what <em>every</em> query of
 * that cube reads, and the star built from it along with it. Two sessions doing
 * that at once read each other's uncommitted values, and whichever restores
 * last decides what the cube looks like afterwards - so the values one client
 * never committed become what the next client sees.
 * <p>
 * XMLA has no answer to this, because nothing in the protocol says a session's
 * pending writeback is private; it is [MS-SSAS] 3.1.3 saying a session is a
 * session, and a client that opens a transaction and then queries plainly
 * expecting its own values back. The engine's answer here is the blunt one:
 * only one statement at a time may have this cube's fact rewritten. Statements
 * on a writeback-enabled cube therefore queue behind each other. That is the
 * price, and it is paid only by cubes that declare a writeback table -
 * {@link #run} is never reached for the others.
 * <p>
 * The lock is reentrant because the work frequently runs further statements on
 * the same cube and the same thread: {@code UPDATE CUBE} resolves its tuple and
 * its value with MDX queries while the pending values are already in place.
 */
public final class PendingFact {

    /** Rewrites the cube's fact, or puts it back. */
    @FunctionalInterface
    public interface FactChange {
        void apply();
    }

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Runs {@code work} between {@code modify} and {@code restore}, with no other
     * caller in between.
     * <p>
     * {@code restore} runs however {@code work} ends - a statement that fails must
     * not leave the cube describing the session it failed in.
     */
    public <T> T run(FactChange modify, Supplier<T> work, FactChange restore) {
        lock.lock();
        try {
            modify.apply();
            try {
                return work.get();
            } finally {
                restore.apply();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Whether some thread currently has the fact rewritten. Diagnostics only. */
    public boolean isHeld() {
        return lock.isLocked();
    }
}
