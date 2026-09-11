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
package org.eclipse.daanse.olap.function.def.set.distinct;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class DistinctFunDefTest {

    @Test
    void testDistinctTwoMembers(Context<?> context) {
        //getTestContext().withCube( "HR" ).
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Distinct({[Employees].[All Employees].[Sheri Nowmer].[Donna Arnold],"
                + "[Employees].[Sheri Nowmer].[Donna Arnold]})")
            .returns(
            "[Employees].[Employees].[Sheri Nowmer].[Donna Arnold]" );
    }

    @Test
    void testDistinctThreeMembers(Context<?> context) {
        //getTestContext().withCube( "HR" ).
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Distinct({[Employees].[All Employees].[Sheri Nowmer].[Donna Arnold],"
                + "[Employees].[All Employees].[Sheri Nowmer].[Darren Stanz],"
                + "[Employees].[All Employees].[Sheri Nowmer].[Donna Arnold]})")
            .returns(
            "[Employees].[Employees].[Sheri Nowmer].[Donna Arnold]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Darren Stanz]" );
    }

    @Test
    void testDistinctFourMembers(Context<?> context) {
        //getTestContext().withCube( "HR" ).
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Distinct({[Employees].[All Employees].[Sheri Nowmer].[Donna Arnold],"
                + "[Employees].[All Employees].[Sheri Nowmer].[Darren Stanz],"
                + "[Employees].[All Employees].[Sheri Nowmer].[Donna Arnold],"
                + "[Employees].[All Employees].[Sheri Nowmer].[Darren Stanz]})")
            .returns(
            "[Employees].[Employees].[Sheri Nowmer].[Donna Arnold]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Darren Stanz]" );
    }

    @Test
    void testDistinctTwoTuples(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Distinct({([Time].[1997],[Store].[All Stores].[Mexico]), "
                + "([Time].[1997], [Store].[All Stores].[Mexico])})")
            .returns(
            "{[Time].[Time].[1997], [Store].[Store].[Mexico]}" );
    }

    @Test
    void testDistinctSomeTuples(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Distinct({([Time].[1997],[Store].[All Stores].[Mexico]), "
                + "crossjoin({[Time].[1997]},{[Store].[All Stores].children})})")
            .returns(
            "{[Time].[Time].[1997], [Store].[Store].[Mexico]}\n"
                + "{[Time].[Time].[1997], [Store].[Store].[Canada]}\n"
                + "{[Time].[Time].[1997], [Store].[Store].[USA]}" );
    }

}
