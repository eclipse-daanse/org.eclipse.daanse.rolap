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
package org.eclipse.daanse.rolap.common.aggmatcher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/** A recorder logs each message once, as "context: message", on the level that fits its kind. */
class ListRecorderLoggingTest {

    @Test
    void eachKindIsLoggedOnItsOwnLevelOnce() {
        ListRecorder recorder = new ListRecorder();
        recorder.pushContextName("Ctx");
        recorder.reportInfo("i");
        recorder.reportWarning("w");
        recorder.reportError("e");
        Logger logger = mock(Logger.class);

        recorder.logInfoMessage(logger);
        recorder.logWarningMessage(logger);
        recorder.logErrorMessage(logger);

        verify(logger).info("Ctx: i");
        verify(logger).warn("Ctx: w");
        verify(logger).error("Ctx: e");
    }

    @Test
    void anEmptyRecorderLogsNothing() {
        ListRecorder recorder = new ListRecorder();
        Logger logger = mock(Logger.class);

        recorder.logInfoMessage(logger);
        recorder.logWarningMessage(logger);
        recorder.logErrorMessage(logger);

        verifyNoInteractions(logger);
    }

    @Test
    void theStaticFormPicksTheLevelOfTheKind() {
        Logger logger = mock(Logger.class);

        AbstractRecorder.logMessage("A:B", "text", AbstractRecorder.MessageType.WARN, logger);

        verify(logger).warn("A:B: text");
    }
}
