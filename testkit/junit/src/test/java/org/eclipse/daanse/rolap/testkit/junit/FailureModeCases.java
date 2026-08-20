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

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.cube.minimal.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.cube.minimal.MinimalCubeTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.ContextScope;
import org.eclipse.daanse.rolap.testkit.junit.api.InjectRolap;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Deliberately misconfigured test classes, launched ONLY through
 * EngineTestKit by {@link FailureModesTest}. Tagged so surefire's
 * {@code excludedGroups} keeps them out of the direct run.
 */
final class FailureModeCases {

    static final String TAG = "failure-mode-case";

    private FailureModeCases() {
    }

    @Tag(TAG)
    @RolapContextTest(MinimalCubeTestInstance.class)
    static class UnknownRoleCase {
        @Test
        void test(@Roles("no-such-role") Connection connection) {
        }
    }

    @Tag(TAG)
    @RolapContextTest(MinimalCubeTestInstance.class)
    static class PrivateFieldCase {
        @InjectRolap
        private Connection connection;

        @Test
        void test() {
        }
    }

    @Tag(TAG)
    @RolapContextTest(MinimalCubeTestInstance.class) // default ContextScope.PER_TEST
    static class StaticFieldPerTestCase {
        @InjectRolap
        static Context<?> context;

        @Test
        void test() {
        }
    }

    @Tag(TAG)
    @RolapContextTest(value = MinimalCubeTestInstance.class, contextScope = ContextScope.PER_CLASS)
    static class MethodConfigPerClassCase {
        @Test
        @RolapConfig(key = "anyKey", value = "true", type = Boolean.class)
        void test() {
        }
    }

    @Tag(TAG)
    @RolapContextTest(value = MinimalCubeTestInstance.class, catalog = CatalogSupplier.class)
    static class BothFormsCase {
        @Test
        void test() {
        }
    }
}
