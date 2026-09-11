/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.test;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.expressivenames.ExpressiveNamesTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(value = ExpressiveNamesTestInstance.class)
class DemoTest {

    private static final QueryAndResult[] sampleQueries = {
        // 0
        new QueryAndResult("select {[Measures].[Measure1]} on columns\n" + " from [Cube1]",
            "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Measures].[Measure1]}\n" + "Row #0: 54\n")
    };

    @Test
    void testSample0(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(), sampleQueries[0].query).returnsGrid(sampleQueries[0].result );
    }

}
