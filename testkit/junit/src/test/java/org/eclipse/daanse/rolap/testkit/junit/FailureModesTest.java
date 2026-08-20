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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Fail-fast behavior, verified via EngineTestKit: misconfiguration is
 * reported loudly with a helpful message, never as a silently passing test.
 * The misconfigured classes live in {@link FailureModeCases}, whose file name
 * surefire does not pick up.
 */
class FailureModesTest {

    @Test
    void unknownRoleFailsWithRoleAndCandidates() {
        assertThat(failureText(FailureModeCases.UnknownRoleCase.class))
                .contains("no-such-role").contains("available roles");
    }

    @Test
    void privateFieldIsRejected() {
        assertThat(failureText(FailureModeCases.PrivateFieldCase.class)).contains("must not be private");
    }

    @Test
    void staticFieldWithoutPerClassScopeIsRejected() {
        assertThat(failureText(FailureModeCases.StaticFieldPerTestCase.class)).contains("ContextScope.PER_CLASS");
    }

    @Test
    void methodConfigUnderPerClassScopeIsRejected() {
        assertThat(failureText(FailureModeCases.MethodConfigPerClassCase.class)).contains("ContextScope.PER_TEST");
    }

    @Test
    void valueAndSupplierFormAreMutuallyExclusive() {
        assertThat(failureText(FailureModeCases.BothFormsCase.class)).contains("mutually exclusive");
    }

    private static String failureText(Class<?> testClass) {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(testClass)).execute()
                .allEvents().executions().failed().stream()
                .map(execution -> String.valueOf(
                        execution.getTerminationInfo().getExecutionResult().getThrowable().orElse(null)))
                .collect(Collectors.joining("\n"));
    }
}
