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

/**
 * API for Mondrian's memory monitors.
 *
 * For Java4, the available monitors
 * do nothing since there is no reliable way of detecting that
 * memory is running low using such a JVM (you are welcome to
 * try to create one, but I believe you will fail - some such candidates
 * only make it more likely that an OutOfMemory condition will occur).
 *
 * For Java5 one
 * can optionally enable a monitor which is based upon the Java5
 * memory classes locate in java.lang.management.
 *
 * A client must implement the MemoryMonitor.Listener interface
 * and register with the MemoryMonitor.
 *
 * The MemoryMonitor supports having multiple
 * Listener clients. The clients can have the same
 * threshold percentage or different values. The threshold percentage value
 * is used by the MemoryMonitor to determine when to
 * notify a client. It is the percentage of the total memory:
 *
 * 
 * 100 * free-memory / total-memory (0 &le; free-memory &le; total-memory).
 * 
 *
 *
 * @author Richard M. Emberson
 * @since Feb 01 2007
 */
public interface MemoryMonitor {

    /**
     * Adds a Listener to the MemoryMonitor with
     * a given threshold percentage.
     *
     * If the threshold is below the Java5 memory managment system's
     * threshold, then the Listener is notified from within this
     * method.
     *
     * @param listener the Listener to be added.
     * @param thresholdPercentage the threshold percentage for this
     *   Listener.
     * @return true if the Listener was
     *   added and false otherwise.
     */
    boolean addListener(Listener listener, int thresholdPercentage);

    /**
     * Changes the threshold percentage of a given Listener.
     *
     * If the new value is below the system's current value, then the
     * Listener will have its notification callback called
     * while in this method - so a client should always check if its
     * notification method was called immediately after calling this
     * method.
     *
     * This method can be used if, for example, an algorithm has
     * different approaches that result in different memory
     * usage profiles; one, large memory but fast and
     * a second which is low-memory but slow. The algorithm starts
     * with the large memory approach, receives a low memory
     * notification, switches to the low memory approach and changes
     * when it should be notified for this new approach. The first
     * approach need to be notified at a lower percentage because it
     * uses lots of memory, possibly quickly; while the second
     * approach, possibly a file based algorithm, has smaller memory
     * requirements and uses memory less quickly thus one can
     * live with a higher notification threshold percentage.
     *
     * @param listener the Listener being updated.
     * @param percentage new percentage threshold.
     */
    void updateListenerThreshold(Listener listener, int percentage);

    /**
     * Removes a Listener from the MemoryMonitor.
     * Returns true if listener was removed and
     * false otherwise.
     *
     * @param listener the listener to be removed
     * @return true if listener was removed.
     */
    boolean removeListener(Listener listener);

    /**
     * Clear out all Listeners and turnoff JVM
     * memory notification.
     */
    void removeAllListener();

    /**
     * Returns the maximum memory usage.
     *
     * @return the maximum memory usage.
     */
    long getMaxMemory();

    /**
     * Returns the current memory used.
     *
     * @return the current memory used.
     */
    long getUsedMemory();


    /**
     * A MemoryMonitor client implements the Listener
     * interface and registers with the MemoryMonitor.
     * When the MemoryMonitor detects that free memory is
     * low, it notifies the client by calling the client's
     * memoryUsageNotification method. It is important
     * that the client quickly return from this call, that the
     * memoryUsageNotification method does not do a lot of
     * work. It is best if it simply sets a flag. The flag should be
     * polled by an application thread and when it detects that the
     * flag was set, it should take immediate memory relinquishing operations.
     * In the case of Mondrian, the present query is aborted.
     */
    interface Listener {

        /**
         * When the MemoryMonitor determines that the
         * Listener's threshold is equal to or less than
         * the current available memory (post garbage collection),
         * then this method is called with the current memory usage,
         * usedMemory, and the maximum memory (which
         * is a constant per JVM invocation).
         * 
         * This method is called (in the case of Java5) by a system
         * thread associated with the garbage collection activity.
         * When this method is called, the client should quickly do what
         * it needs to to communicate with an application thread and
         * then return. Generally, quickly de-referencing some big objects
         * and setting a flag is the most that should be done by
         * implementations of this method. If the implementor chooses to
         * de-reference some objects, then the application code must
         * be written so that if will not throw a NullPointerException
         * when such de-referenced objects are accessed. If a flag
         * is set, then the application must be written to check the
         * flag periodically.
         *
         * @param usedMemory the current memory used.
         * @param maxMemory the maximum available memory.
         */
        void memoryUsageNotification(long usedMemory, long maxMemory);
    }

    /**
     * This is an interface that a MemoryMonitor may optionally
     * implement. These methods give the tester access to some of the
     * internal, white-box data.
     * 
     * During testing Mondrian has a default
     * MemoryMonitor which might be replaced with a test
     * MemoryMonitors using the ThreadLocal
     * mechanism. After the test using the test
     * MemoryMonitor finishes, a call to the
     * resetFromTest method allows
     * the default MemoryMonitor reset itself.
     * This is hook that should only be called as part of testing.
     */
    interface Test {

        /**
         * This should only be called when one is switching from a
         * test MemoryMonitor back to the default system
         * MemoryMonitor. In particular, look at
         * the MemoryMonitorFactory's
         * clearThreadLocalClassName() method for its
         * usage.
         */
        void resetFromTest();
    }
}
