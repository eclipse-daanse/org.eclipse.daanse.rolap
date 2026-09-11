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
package org.eclipse.daanse.olap.function.def.numeric.value;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.FunDependencies;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.olap.function.TestResources;

@RolapContextTest(FoodmartTestInstance.class)
class ValueFunDefTest {

    @Test
    void testValue(Context<?> context) {
        // VALUE is usually a cell property, not a member property.
        // We allow it because MS documents it as a function, <Member>.VALUE.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Measures].[Store Sales].VALUE").returns( "565,238.13" );

        // Depends upon almost everything.
        FunDependencies.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Measures].[Store Sales].VALUE")
            .dependsOn( TestResources.hiersExcept( "[Measures]" ) );

        // We do not allow FORMATTED_VALUE.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Measures].[Store Sales].FORMATTED_VALUE")
            .throwsMessage( "MDX object '[Measures].[Store Sales].FORMATTED_VALUE' not found in cube 'Sales'" );

        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Measures].[Store Sales].NAME").returns( "Store Sales" );
        // MS says that ID and KEY are standard member properties for
        // OLE DB for OLAP, but not for XML/A. We don't support them.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Measures].[Store Sales].ID")
            .throwsMessage( "MDX object '[Measures].[Store Sales].ID' not found in cube 'Sales'" );

        // Error for KEY is slightly different than for ID. It doesn't matter
        // very much.
        //
        // The error is different because KEY is registered as a Mondrian
        // builtin property, but ID isn't. KEY cannot be evaluated in
        // "<MEMBER>.KEY" syntax because there is not function defined. For
        // other builtin properties, such as NAME, CAPTION there is a builtin
        // function.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Measures].[Store Sales].KEY")
            .throwsMessage( "No function matches signature '<Member>.KEY'" );

        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Measures].[Store Sales].CAPTION").returns( "Store Sales" );
    }

}
