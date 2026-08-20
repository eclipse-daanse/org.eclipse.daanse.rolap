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
package org.eclipse.daanse.rolap.testkit.junit.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets one context configuration value before the test runs.
 *
 * <p>Keys are {@code ConfigConstants} keys. {@link #type()} is required for
 * non-String values — {@code getConfigValue} checks strictly with
 * {@code isInstance} and silently falls back to the default on a mismatch.
 *
 * <p>Class-level entries apply first, method-level entries merge per key on
 * top. Combining a method-level {@code @RolapConfig} with
 * {@link ContextScope#PER_CLASS} is a configuration error — it would leak
 * into sibling tests.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RolapConfigs.class)
@Documented
public @interface RolapConfig {

    /** The {@code ConfigConstants} key. */
    String key();

    /** The value in string form; coerced once according to {@link #type()}. */
    String value();

    /** One of String, Boolean, Integer, Long, Double. */
    Class<?> type() default String.class;
}
