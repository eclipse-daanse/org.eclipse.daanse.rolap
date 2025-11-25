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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link ObjectPool}.
 *
 * @author Richard Emberson
 */
class MemoryMonitorTest {
    static final int PERCENT_100 = 100;

    protected static int convertThresholdToPercentage(
        final long threshold,
        final long maxMemory)
    {
        return (int) ((PERCENT_100 * threshold) / maxMemory);
    }




    protected boolean enabled = false;

    @Test
    void testDeltaUsage() throws Exception {
        if (!enabled) {
            return;
        }
        class Listener implements MemoryMonitor.Listener {
            boolean wasNotified = false;
            Listener() {
            }
            @Override
			public void memoryUsageNotification(long used, long max) {
                wasNotified = true;
            }
        }
        Listener listener = new Listener();
        MemoryMonitor mm = new NotificationMemoryMonitor();
        // we will set a percentage slightly above the current
        // used level, and then allocate some objects that will
        // force a notification.
        long maxMemory = mm.getMaxMemory();
        long usedMemory = mm.getUsedMemory();
        int currentPercentage =
            convertThresholdToPercentage(usedMemory, maxMemory);
        int delta = (int) (maxMemory - usedMemory) / 10;
        int percentage = convertThresholdToPercentage(delta, maxMemory);
        try {
            byte[][] bytes = new byte[10][];
            mm.addListener(listener, percentage + currentPercentage);
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = new byte[delta];
                if (listener.wasNotified) {
                    bytes = null;
                    break;
                }
            }
            if (! listener.wasNotified) {
                Assertions.fail("Listener callback not called");
            }
        } finally {
            mm.removeListener(listener);
        }
    }

}
