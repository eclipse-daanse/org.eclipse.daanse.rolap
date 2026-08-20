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

/**
 * Lifetime of the rolap context. Orthogonal to {@link DbScope}.
 *
 * <p>Default {@link #PER_TEST} gives a fresh context per test over the
 * (cached) database — cheap, since building only wires parsers/compilers, and
 * required for per-test {@code @RolapConfig} and catalog swaps to take effect.
 */
public enum ContextScope {

    /** Default: fresh context (and connections) per test method. */
    PER_TEST,

    /**
     * One context for the whole class — warm schema caches for read-only
     * suites, and the prerequisite for static {@link InjectRolap} fields.
     * Method-level {@link RolapConfig} is rejected under this scope.
     */
    PER_CLASS
}
