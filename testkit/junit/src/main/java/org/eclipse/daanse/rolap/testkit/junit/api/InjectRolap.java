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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field of a {@link RolapContextTest}-annotated class for injection.
 * Supports the same types as parameter injection: {@code Context},
 * {@code Connection} (optionally {@link Roles}-qualified),
 * {@code ActiveDatabase}, {@code Dialect}, {@code DataSource}, and declared
 * {@link ContextModifier} types.
 *
 * <p>Static fields fill once in {@code beforeAll} and require
 * {@link ContextScope#PER_CLASS}; instance fields fill before each test.
 * Must not be {@code private} or {@code final}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectRolap {
}
