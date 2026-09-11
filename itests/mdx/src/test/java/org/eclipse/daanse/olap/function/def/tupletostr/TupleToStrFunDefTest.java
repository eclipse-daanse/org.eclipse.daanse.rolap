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
package org.eclipse.daanse.olap.function.def.tupletostr;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class TupleToStrFunDefTest {

    @Test
    void testTupleToStr(Context<?> context) {
        // Applied to a dimension (which becomes a member)
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "TupleToStr([Product])")
            .returns( "[Product].[Product].[All Products]" );

        // Applied to a dimension (invalid because has no default hierarchy)

        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "TupleToStr([Time])")
            .throwsMessage( "Could not Calculate the default hierarchy of the given dimension 'Time'. It may contains more than one hierarchy. Specify the hierarchy explicitly." );

        // Applied to a hierarchy
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "TupleToStr([Time].[Time])")
            .returns( "[Time].[Time].[1997]" );

        // Applied to a member
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "TupleToStr([Store].[USA].[OR])")
            .returns( "[Store].[Store].[USA].[OR]" );

        // Applied to a member (extra set of parens)
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "TupleToStr(([Store].[USA].[OR]))")
            .returns( "([Store].[Store].[USA].[OR])" );

        // Now, applied to a tuple
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "TupleToStr(([Marital Status], [Gender].[M]))")
            .returns( "([Marital Status].[Marital Status].[All Marital Status], [Gender].[Gender].[M])" );

        // Applied to a tuple containing a null member
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "TupleToStr(([Marital Status], [Gender].Parent))")
            .returns( "" );

        // Applied to a null member
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "TupleToStr([Marital Status].Parent)")
            .returns( "" );
    }

}
