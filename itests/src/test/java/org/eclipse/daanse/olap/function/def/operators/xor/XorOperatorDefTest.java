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
 *   SmartCity Jena - initial
 *
 */
package org.eclipse.daanse.olap.function.def.operators.xor;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class XorOperatorDefTest {

    @Test
    void testXor(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " 1=1 XOR 2=2 ").isFalse();
    }

    @Test
    void testXorAssociativity(Context<?> context) {
        // Would give 'false' if XOR were stronger than AND (wrong!)
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " 1 = 1 AND 1 = 1 XOR 1 = 0 ").isTrue();
    }

}
