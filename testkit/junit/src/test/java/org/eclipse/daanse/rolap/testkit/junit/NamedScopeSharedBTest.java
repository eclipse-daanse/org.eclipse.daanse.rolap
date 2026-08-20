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

import javax.sql.DataSource;

import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.cube.minimal.MinimalCubeTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/** Half B of the NAMED-scope sharing proof (see {@link SharedDbHolder}). */
@RolapContextTest(value = MinimalCubeTestInstance.class, dbScope = DbScope.NAMED,
        scopeName = SharedDbHolder.SCOPE_NAME)
class NamedScopeSharedBTest {

    @Test
    void sharesTheNamedDatabase(DataSource dataSource) {
        SharedDbHolder.recordAndAssert(dataSource);
    }
}
