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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ListRecorderTest {

    @Test
    void countsWarningsAndErrorsAndKeepsTheContext() {
        ListRecorder recorder = new ListRecorder();
        recorder.pushContextName("catalog");
        recorder.reportInfo("loading");
        recorder.reportWarning("no key");
        recorder.pushContextName("table");
        recorder.reportError("missing column");
        recorder.popContextName();
        recorder.popContextName();

        assertThat(recorder.hasInformation()).isTrue();
        assertThat(recorder.hasWarnings()).isTrue();
        assertThat(recorder.hasErrors()).isTrue();
        assertThat(recorder.getErrorCount()).isEqualTo(1);

        List<String> errors = new ArrayList<>();
        recorder.getErrorEntries().forEachRemaining(e -> errors.add(e.context() + ": " + e.message()));
        assertThat(errors).containsExactly("catalog:table: missing column");
    }

    @Test
    void throwRTExceptionRaisesOnlyWhenAnErrorWasRecorded() {
        ListRecorder warned = new ListRecorder();
        warned.reportWarning("w");
        warned.throwRTException();

        ListRecorder failed = new ListRecorder();
        failed.reportError("bad");
        assertThatThrownBy(failed::throwRTException).isInstanceOf(RecorderException.class);
    }

    @Test
    void clearForgetsEverything() {
        ListRecorder recorder = new ListRecorder();
        recorder.reportWarning("w");
        recorder.clear();
        assertThat(recorder.hasWarnings()).isFalse();
        assertThat(recorder.getWarnEntries().hasNext()).isFalse();
    }
}
