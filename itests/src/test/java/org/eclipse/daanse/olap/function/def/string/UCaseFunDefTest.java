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
package org.eclipse.daanse.olap.function.def.string;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.fun.DaanseEvaluationException;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class UCaseFunDefTest {


    @Test
    void testUCaseWithNonEmptyString(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select filter([Store].MEMBERS, "
                + " UCase([Store].CURRENTMEMBER.Name) = \"BELLINGHAM\") "
                + "on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
                + "Row #0: 2,237\n" );
    }

    @Test
    void testUCaseWithEmptyString(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select filter([Store].MEMBERS, "
                + " UCase(\"\") = \"\" "
                + "And [Store].CURRENTMEMBER.Name = \"Bellingham\") "
                + "on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
                + "Row #0: 2,237\n" );
    }

    @Test
    void testUCaseWithNullString(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select filter([Store].MEMBERS, "
                + " UCase(\"NULL\") = \"\" "
                + "And [Store].CURRENTMEMBER.Name = \"Bellingham\") "
                + "on 0 from sales")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n" );
    }

    @Test
    void testUCaseWithNull(Context<?> context) {
        try {
            executeQuery(context.getConnectionWithDefaultRole(),
                "select filter([Store].MEMBERS, "
                    + " UCase(NULL) = \"\" "
                    + "And [Store].CURRENTMEMBER.Name = \"Bellingham\") "
                    + "on 0 from sales" );
        } catch ( Exception e ) {
            assertEquals( "No method with the signature UCase(NULL) matches known functions.",
                e.getCause().getMessage() );
            return;
        }
        fail( "DaanseException is expected here" );
    }

}
