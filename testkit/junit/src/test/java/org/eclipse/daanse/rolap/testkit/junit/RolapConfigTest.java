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

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.cube.minimal.MinimalCubeTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * {@code @RolapConfig}: class-level applies to every test, method-level merges
 * on top — and because the default ContextScope is PER_TEST, a method's value
 * never leaks into a sibling test.
 */
@RolapContextTest(MinimalCubeTestInstance.class)
@RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
class RolapConfigTest {

    @Test
    @RolapConfig(key = ConfigConstants.ENABLE_TOTAL_COUNT, value = "true", type = Boolean.class)
    void methodConfigMergesOverClassConfig(Context<?> context) {
        assertThat(context.getConfigValue(ConfigConstants.GENERATE_FORMATTED_SQL,
                ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class))
                .as("Class configuration applies").isTrue();
        assertThat(context.getConfigValue(ConfigConstants.ENABLE_TOTAL_COUNT,
                ConfigConstants.ENABLE_TOTAL_COUNT_DEFAULT_VALUE, Boolean.class))
                .as("Method configuration also applies").isTrue();
    }

    @Test
    void siblingTestSeesAFreshContext(Context<?> context) {
        assertThat(context.getConfigValue(ConfigConstants.GENERATE_FORMATTED_SQL,
                ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class))
                .as("Class configuration still applies").isTrue();
        assertThat(context.getConfigValue(ConfigConstants.ENABLE_TOTAL_COUNT,
                ConfigConstants.ENABLE_TOTAL_COUNT_DEFAULT_VALUE, Boolean.class))
                .as("The neighbor's method configuration has NOT been leaked (fresh context)").isFalse();
    }
}
