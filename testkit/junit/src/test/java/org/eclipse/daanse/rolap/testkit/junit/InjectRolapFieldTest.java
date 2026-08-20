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
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.cube.minimal.MinimalCubeTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.ContextScope;
import org.eclipse.daanse.rolap.testkit.junit.api.InjectRolap;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.modifier.FlushSchemaCacheModifier;
import org.junit.jupiter.api.Test;

/**
 * Field injection: static fields (beforeAll, requires PER_CLASS) and instance
 * fields (beforeEach) — the osgi-test/@TempDir mechanics.
 */
@RolapContextTest(value = MinimalCubeTestInstance.class, contextScope = ContextScope.PER_CLASS,
        modifiers = FlushSchemaCacheModifier.class)
class InjectRolapFieldTest {

    @InjectRolap
    static Context<?> staticContext;

    @InjectRolap
    Connection connection;

    @InjectRolap
    FlushSchemaCacheModifier flush;

    @Test
    void staticAndInstanceFieldsAreFilled(Context<?> parameterContext) {
        assertThat(staticContext).as("static field, filled in beforeAll").isNotNull();
        assertThat(parameterContext).as("Parameters and static fields point to the class context")
                .isSameAs(staticContext);
        assertThat(connection).as("Instance field, filled in beforeEach").isNotNull();
    }

    @Test
    void modifierIsInjectableAsField() {
        assertThat(flush).isNotNull();
        flush.flush(); // Mid-test flush over the injected modifier handle
    }
}
