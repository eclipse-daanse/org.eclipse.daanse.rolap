/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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


package org.eclipse.daanse.rolap.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link java.util.concurrent.Future} that completes
 * when a thread writes a value (or an exception) into a slot.
 */
public class SlotFuture<V> implements Future<V> {
    private V value;
    private Throwable throwable;
    private boolean done;
    private boolean cancelled;
    private final CountDownLatch dataGate = new CountDownLatch(1);
    private final ReentrantReadWriteLock stateLock =
        new ReentrantReadWriteLock();
    private static final Logger LOG = LoggerFactory.getLogger(SlotFuture.class);

    private final String thisString;

    /**
     * Creates a SlotFuture.
     */
    public SlotFuture() {
        thisString = super.toString();
    }

    @Override
    public String toString() {
        return thisString;
    }

    /**
     * {@inheritDoc}
     *
     * The SlotFuture does not know which thread is computing the result
     * and therefore the {@code mayInterruptIfRunning} parameter is ignored.
     */
    @Override
	public boolean cancel(boolean mayInterruptIfRunning) {
        stateLock.writeLock().lock();
        try {
            if (!done) {
                cancelled = true;
                done = true;
                dataGate.countDown();
                return true;
            } else {
                return false;
            }
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    @Override
	public boolean isCancelled() {
        stateLock.readLock().lock();
        try {
            return cancelled;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    @Override
	public boolean isDone() {
        stateLock.readLock().lock();
        try {
            return done || cancelled || throwable != null;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    @Override
	public V get() throws ExecutionException, InterruptedException {
        // Wait for a put, fail or cancel
        dataGate.await();

        // Now a put, fail or cancel has occurred, state does not change; we
        // don't need even a read lock.
        if (throwable != null) {
            throw new ExecutionException(throwable);
        }
        return value;
    }

    @Override
	public V get(long timeout, TimeUnit unit)
        throws ExecutionException, InterruptedException, TimeoutException
    {
        // Wait for a put, fail or cancel
        if (!dataGate.await(timeout, unit)) {
            throw new TimeoutException();
        }

        // Now a put, fail or cancel has occurred, state does not change; we
        // don't need even a read lock.
        if (throwable != null) {
            throw new ExecutionException(throwable);
        }
        return value;
    }

    /**
     * Writes a value into the slot, indicating that the task has completed
     * successfully.
     *
     * @param value Value to yield as the result of the computation
     *
     * @throws IllegalArgumentException if put, fail or cancel has already
     *     been invoked on this future
     */
    public void put(V value) {
        stateLock.writeLock().lock(); // need exclusive write access to state
        try {
            if (done) {
                final String message =
                    new StringBuilder("Future is already done (cancelled=").append(cancelled)
                    .append(", value=").append(this.value)
                    .append(", throwable=").append(throwable).append(")").toString();
                LOG.error(message);
                throw new IllegalArgumentException(
                    message);
            }
            this.value = value;
            this.done = true;
        } finally {
            stateLock.writeLock().unlock();
        }
        dataGate.countDown();
    }

    /**
     * Writes a throwable into the slot, indicating that the task has failed.
     *
     * @param throwable Exception that aborted the computation
     *
     * @throws IllegalArgumentException if put, fail or cancel has already
     *     been invoked on this future
     */
    public void fail(Throwable throwable) {
        stateLock.writeLock().lock(); // need exclusive write access to state
        try {
            if (done) {
                throw new IllegalArgumentException(
                    new StringBuilder("Future is already done (cancelled=").append(cancelled)
                    .append(", value=").append(value)
                    .append(", throwable=").append(this.throwable).append(")").toString());
            }
            this.throwable = throwable;
            this.done = true;
        } finally {
            stateLock.writeLock().unlock();
        }
        dataGate.countDown();
    }
}
