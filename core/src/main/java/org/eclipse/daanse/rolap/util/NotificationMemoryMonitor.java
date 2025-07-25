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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The NotificationMemoryMonitor class uses the Java5
 * memory management system to detect system low memory events.
 * 
 * This code is loosely based on the code taken from The Java
 * Specialists' Newsletter,
 * <a href="http://www.javaspecialists.co.za/archive/newsletter.do?issue=092"
 *  >issue 92</a> authored by Dr. Heinz M. Kabutz.
 * As a note, his on-line newletters are a good source of Java information,
 * take a look.
 * 
 *  For more information one should review the Java5 classes in
 *  java.lang.management.
 *
 *
 * @author <a>Richard M. Emberson</a>
 * @since Feb 03 2007
 */
public class NotificationMemoryMonitor extends AbstractMemoryMonitor {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(NotificationMemoryMonitor.class);


    protected static final MemoryPoolMXBean TENURED_POOL;

    static {
        TENURED_POOL = findTenuredGenPool();
    }

    private static MemoryPoolMXBean findTenuredGenPool() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            // I don't know whether this approach is better, or whether
            // we should rather check for the pool name "Tenured Gen"?
            if (pool.getType() == MemoryType.HEAP
                && pool.isUsageThresholdSupported())
            {
                return pool;
            }
        }
        throw new AssertionError("Could not find tenured space");
    }

    /**
     * The NotificationHandler implements the Java memory
     * notification listener, NotificationListener,
     * and is used to take notifications from Java and pass them on
     * to the NotificationMemoryMonitor's
     * Listerners.
     */
    private class NotificationHandler implements NotificationListener {

        /**
         * This method is called by the Java5 code to notify clients
         * registered with the JVM that the JVM memory threshold
         * has been exceeded.
         *
         * @param notification
         * @param unused
         */
        @Override
		public void handleNotification(
            final Notification notification,
            final Object unused)
        {
            final String type = notification.getType();

            if (type.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {
                final MemoryUsage usage = TENURED_POOL.getUsage();

                notifyListeners(usage.getUsed(), usage.getMax());
            }
        }
    }


    /**
     * Construct a NotificationMemoryMonitor instance and
     * register it with the Java5 memory management system.
     */
    public NotificationMemoryMonitor() {
        super();
        final MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        final NotificationEmitter emitter = (NotificationEmitter) mbean;

        // register with the Java5 memory management system.
        emitter.addNotificationListener(new NotificationHandler(), null, null);
    }

    /**
     * Get the Logger.
     *
     * @return the Logger.
     */
    @Override
	protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Notify the Java5 memory management system that there is a new
     * low threshold.
     *
     * @param newLowThreshold the new threshold.
     */
    @Override
	protected void notifyNewLowThreshold(final long newLowThreshold) {
        if (newLowThreshold == Long.MAX_VALUE) {
            TENURED_POOL.setUsageThreshold(0);
        } else {
            TENURED_POOL.setUsageThreshold(newLowThreshold);
        }
    }

    /**
     * Get the maximum possible memory usage for this JVM instance.
     *
     * @return maximum memory that can be used.
     */
    @Override
	public long getMaxMemory() {
        return TENURED_POOL.getUsage().getMax();
    }
    /**
     * Get the current memory usage for this JVM instance.
     *
     * @return current memory used.
     */
    @Override
	public long getUsedMemory() {
        return TENURED_POOL.getUsage().getUsed();
    }
}
