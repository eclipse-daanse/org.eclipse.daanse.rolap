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
package org.eclipse.daanse.olap.function.def.percentile;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;


import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class PercentileFunDefTest {

    @Test
    void testPercentile(Context<?> context) {
        // same result as median
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Percentile({[Store].[All Stores].[USA].children}, [Measures].[Store Sales], 50)").returns(
            "159,167.84" );
        // same result as min
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Percentile({[Store].[All Stores].[USA].children}, [Measures].[Store Sales], 0)").returns(
            "142,277.07" );
        // same result as max
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Percentile({[Store].[All Stores].[USA].children}, [Measures].[Store Sales], 100)").returns(
            "263,793.22" );
        // check some real percentile cases
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Percentile({[Store].[All Stores].[USA].[WA].children}, [Measures].[Store Sales], 50)").returns(
            "49,634.46" );
        // the next two results correspond to MS Excel 2013.
        // See MONDRIAN-2343 jira issue.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Percentile({[Store].[All Stores].[USA].[WA].children}, [Measures].[Store Sales], 100/7*2)").returns(
            "18,732.09" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Percentile({[Store].[All Stores].[USA].[WA].children}, [Measures].[Store Sales], 95)").returns(
            "68,259.66" );
    }

    /**
     * Testcase for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1045">MONDRIAN-1045,
     * "When I use the Percentile function it cracks when there's only 1 register"</a>.
     */
    @Test
    void testPercentileBugMondrian1045(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Percentile({[Store].[All Stores].[USA]}, [Measures].[Store Sales], 50)").returns(
            "565,238.13" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Percentile({[Store].[All Stores].[USA]}, [Measures].[Store Sales], 40)").returns(
            "565,238.13" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Percentile({[Store].[All Stores].[USA]}, [Measures].[Store Sales], 95)").returns(
            "565,238.13" );
    }

}
