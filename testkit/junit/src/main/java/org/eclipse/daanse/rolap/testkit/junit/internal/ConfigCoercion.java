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
package org.eclipse.daanse.rolap.testkit.junit.internal;

import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Coerces {@code @RolapConfig} string values once, at apply time. The
 * context's {@code getConfigValue} checks stored values strictly with
 * {@code isInstance}, so the stored type must match the reading type exactly.
 */
final class ConfigCoercion {

    private ConfigCoercion() {
    }

    static Object coerce(String key, String value, Class<?> type) {
        try {
            if (type == String.class) {
                return value;
            }
            if (type == Boolean.class) {
                return Boolean.valueOf(value);
            }
            if (type == Integer.class) {
                return Integer.valueOf(value);
            }
            if (type == Long.class) {
                return Long.valueOf(value);
            }
            if (type == Double.class) {
                return Double.valueOf(value);
            }
        } catch (NumberFormatException e) {
            throw new ExtensionConfigurationException(
                    "@RolapConfig(key = \"" + key + "\"): value \"" + value + "\" is not a valid "
                            + type.getSimpleName(), e);
        }
        throw new ExtensionConfigurationException("@RolapConfig(key = \"" + key + "\"): unsupported type "
                + type.getName() + " (supported: String, Boolean, Integer, Long, Double)");
    }
}
