/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.olap.function.def.operators.not;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class NotPrefixOperatorDefTest {

    @Test
    void testNot(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " NOT 1=1 ").isFalse();
    }

    @Test
    void testNotNot(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " NOT NOT 1=1 ").isTrue();
    }

    @Test
    void testNotAssociativity(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " 1=1 AND NOT 1=1 OR NOT 1=1 AND 1=1 ").isFalse();
    }

}
