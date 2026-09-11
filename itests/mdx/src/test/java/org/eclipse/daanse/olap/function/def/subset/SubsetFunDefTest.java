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
package org.eclipse.daanse.olap.function.def.subset;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class SubsetFunDefTest {


    @Test
    void testSubset(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Subset([Promotion Media].Children, 7, 2)")
            .returns(
            "[Promotion Media].[Promotion Media].[Product Attachment]\n"
                + "[Promotion Media].[Promotion Media].[Radio]" );
    }

    @Test
    void testSubsetNegativeCount(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Subset([Promotion Media].Children, 3, -1)")
            .returns(
            "" );
    }

    @Test
    void testSubsetNegativeStart(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Subset([Promotion Media].Children, -2, 4)")
            .returns(
            "" );
    }

    @Test
    void testSubsetDefault(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Subset([Promotion Media].Children, 11)")
            .returns(
            "[Promotion Media].[Promotion Media].[Sunday Paper, Radio]\n"
                + "[Promotion Media].[Promotion Media].[Sunday Paper, Radio, TV]\n"
                + "[Promotion Media].[Promotion Media].[TV]" );
    }

    @Test
    void testSubsetOvershoot(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Subset([Promotion Media].Children, 15)")
            .returns(
            "" );
    }

    @Test
    void testSubsetEmpty(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Subset([Gender].[F].Children, 1)")
            .returns(
            "" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Subset([Gender].[F].Children, 1, 3)")
            .returns(
            "" );
    }

}
