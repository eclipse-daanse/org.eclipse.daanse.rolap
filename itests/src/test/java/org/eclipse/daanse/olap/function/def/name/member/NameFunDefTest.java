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
package org.eclipse.daanse.olap.function.def.name.member;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class NameFunDefTest {


    @Test
    void testMemberName(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Time].[1997].Name").returns( "1997" );
        // dimension name
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Store].Name").returns( "Store" );
        // member name
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Store].DefaultMember.Name").returns( "All Stores" );
        // name of null member
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "[Store].Parent.Name").returns( "#null" );
    }

}
