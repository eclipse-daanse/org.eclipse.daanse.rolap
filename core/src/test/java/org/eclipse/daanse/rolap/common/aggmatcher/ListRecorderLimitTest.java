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
package org.eclipse.daanse.rolap.common.aggmatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The error that reaches the limit is still recorded, then the recorder throws naming context and count;
 * throwRTException throws only when errors are there.
 */
class ListRecorderLimitTest {

    @ParameterizedTest
    @ValueSource(ints = { 10, 3 })
    void theErrorThatReachesTheLimitIsRecordedAndThenTheRecorderThrows(int limit) {
        ListRecorder recorder = limit == AbstractRecorder.DEFAULT_MSG_LIMIT ? new ListRecorder() : new ListRecorder(limit);
        recorder.pushContextName("cube");
        for (int i = 1; i < limit; i++) {
            recorder.reportError("error " + i);
        }
        assertThat(recorder.getErrorCount()).isEqualTo(limit - 1);

        assertThatThrownBy(() -> recorder.reportError("the last one")).isInstanceOf(RecorderException.class)
                .hasMessageContaining("cube").hasMessageContaining("Exceeded number of allowed errors")
                .hasMessageContaining(String.valueOf(limit));
        assertThat(recorder.getErrorCount()).isEqualTo(limit);
        assertThat(recorder.getErrorEntries()).toIterable().last().extracting(ListRecorder.Entry::message)
                .isEqualTo("the last one");
    }

    @Test
    void throwRTExceptionThrowsOnlyWhenThereAreErrors() {
        ListRecorder recorder = new ListRecorder();
        recorder.pushContextName("cube");
        assertThatCode(recorder::throwRTException).doesNotThrowAnyException();
        recorder.reportWarning("only a warning");
        assertThatCode(recorder::throwRTException).doesNotThrowAnyException();

        recorder.reportError("one error");
        assertThatThrownBy(recorder::throwRTException).isInstanceOf(RecorderException.class)
                .hasMessageContaining("cube").hasMessageContaining("1");
    }
}
