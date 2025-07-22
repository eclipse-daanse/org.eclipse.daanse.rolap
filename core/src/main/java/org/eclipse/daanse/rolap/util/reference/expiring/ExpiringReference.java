/*
* Copyright (c) 2023 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.util.reference.expiring;

import java.lang.ref.SoftReference;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An expiring reference.
 *
 *
 * When the reference is accessed, it will be reset with a new timeout.
 *
 *
 * When the timeout is reached, the reference will be cleared.
 *
 * @param <T> The type of the reference.
 */
public class ExpiringReference<T> extends SoftReference<T> {

	/**
	 * A scheduler for the cleanup task.
	 */
	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	/**
	 * The hard reference.
	 */
	private T hardRef;

	/**
	 * A scheduled future for the cleanup task.
	 */
	private ScheduledFuture<?> scheduledFuture;

	/**
	 * The expiry time.
	 */
	private long expiry = Long.MIN_VALUE;

	/**
	 * Creates a new expiring reference.
	 *
	 * @param ref     The reference to be held.
	 * @param timeOut The timeout for the reference.
	 */
	public ExpiringReference(T ref, Duration timeOut) {
		super(ref);
		schedule(ref, timeOut);
	}

	private synchronized void schedule(T referent, Duration dTimeOut) {

		final long timeoutMillis = dTimeOut.toMillis();

		if (timeoutMillis == Long.MIN_VALUE && expiry != Long.MIN_VALUE) {
			// Reference was accessed through get().
			// Don't reset the expiry if it is active.
			return;
		}

		if (timeoutMillis < 0) {
			// Timeout is < 0. Act as a regular soft ref.
			this.hardRef = null;
		}

		if (timeoutMillis == 0) {
			// Permanent ref mode.
			expiry = Long.MAX_VALUE;
			// Set the reference
			this.hardRef = referent;
			return;
		}

		if (timeoutMillis > 0) {
			// A timeout must be enforced.
			long newExpiry = System.currentTimeMillis() + timeoutMillis;

			// Set the reference
			this.hardRef = referent;
			if (newExpiry > expiry) {

				if (scheduledFuture != null) {
					scheduledFuture.cancel(false);
				}

				expiry = newExpiry;

				// Schedule for cleanup.
				scheduledFuture = scheduler.schedule(() -> {
					hardRef = null;
				}, timeoutMillis + 10, TimeUnit.MILLISECONDS);
			}
			return;
		}
	}

	@Override
	public synchronized T get() {
		return getCatalogAndResetTimeout(Duration.ofMillis(Long.MIN_VALUE));
	}

	public synchronized T getCatalogAndResetTimeout(Duration timeOut) {

		final T weakRef = super.get();

		if (weakRef != null) {
			schedule(weakRef, timeOut);
		}
		return weakRef;
	}

	public T getHardRef() {
		return hardRef;
	}
}
