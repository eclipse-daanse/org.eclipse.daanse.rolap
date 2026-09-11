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
package org.eclipse.daanse.olap.function.def.operators.orx;

import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class OrOperatorDefTest {

    @Test
    void testOr2(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " 1=0 OR 0=0 ").isTrue();
    }

    @Test
    void testOrAssociativity1(Context<?> context) {
        // Would give 'false' if OR were stronger than AND (wrong!)
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " 1=1 AND 1=0 OR 1=1 ").isTrue();
    }

    @Test
    void testOrAssociativity2(Context<?> context) {
        // Would give 'false' if OR were stronger than AND (wrong!)
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " 1=1 OR 1=0 AND 1=1 ").isTrue();
    }

    @Test
    void testOrAssociativity3(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", " (1=0 OR 1=1) AND 1=1 ").isTrue();
    }


    @Test
    @RolapConfig(key = ConfigConstants.MAX_EVAL_DEPTH, value = "3", type = Integer.class)
    void testComplexOrExpr(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        switch (getDialect(connection).name()) {
            case "INFOBRIGHT":
                // Skip this test on Infobright, because [Promotion Sales] is
                // defined wrong.
                return;
        }

        // make sure all aggregates referenced in the OR expression are
        // processed in a single load request by setting the eval depth to
        // a value smaller than the number of measures
        assertThatQuery(connection,
            "with set [*NATIVE_CJ_SET] as '[Store].[Store Country].members' "
                + "set [*GENERATED_MEMBERS_Measures] as "
                + "    '{[Measures].[Unit Sales], [Measures].[Store Cost], "
                + "    [Measures].[Sales Count], [Measures].[Customer Count], "
                + "    [Measures].[Promotion Sales]}' "
                + "set [*GENERATED_MEMBERS] as "
                + "    'Generate([*NATIVE_CJ_SET], {[Store].CurrentMember})' "
                + "member [Store].[*SUBTOTAL_MEMBER_SEL~SUM] as 'Sum([*GENERATED_MEMBERS])' "
                + "select [*GENERATED_MEMBERS_Measures] ON COLUMNS, "
                + "NON EMPTY "
                + "    Filter("
                + "        Generate("
                + "        [*NATIVE_CJ_SET], "
                + "        {[Store].CurrentMember}), "
                + "        (((((NOT IsEmpty([Measures].[Unit Sales])) OR "
                + "            (NOT IsEmpty([Measures].[Store Cost]))) OR "
                + "            (NOT IsEmpty([Measures].[Sales Count]))) OR "
                + "            (NOT IsEmpty([Measures].[Customer Count]))) OR "
                + "            (NOT IsEmpty([Measures].[Promotion Sales])))) "
                + "on rows "
                + "from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Store Cost]}\n"
                + "{[Measures].[Sales Count]}\n"
                + "{[Measures].[Customer Count]}\n"
                + "{[Measures].[Promotion Sales]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[USA]}\n"
                + "Row #0: 266,773\n"
                + "Row #0: 225,627.23\n"
                + "Row #0: 86,837\n"
                + "Row #0: 5,581\n"
                + "Row #0: 151,211.21\n" );
    }

}
