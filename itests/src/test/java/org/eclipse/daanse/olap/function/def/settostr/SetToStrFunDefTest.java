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
package org.eclipse.daanse.olap.function.def.settostr;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class SetToStrFunDefTest {


    @Test
    void testSetToStr(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "SetToStr([Time].[Time].children)")
            .returns( "{[Time].[Time].[1997].[Q1], [Time].[Time].[1997].[Q2], [Time].[Time].[1997].[Q3], [Time].[Time].[1997].[Q4]}" );

        // Now, applied to tuples
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "SetToStr({CrossJoin([Marital Status].children, {[Gender].[M]})})")
            .returns(
            "{"
                + "([Marital Status].[Marital Status].[M], [Gender].[Gender].[M]), "
                + "([Marital Status].[Marital Status].[S], [Gender].[Gender].[M])"
                + "}" );
    }

}
