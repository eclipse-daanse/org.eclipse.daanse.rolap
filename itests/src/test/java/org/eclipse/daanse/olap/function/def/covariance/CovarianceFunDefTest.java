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
package org.eclipse.daanse.olap.function.def.covariance;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class CovarianceFunDefTest {

    @Test
    void testCovariance(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Covariance({[Store].[All Stores].[USA].children}, [Measures].[Unit Sales], [Measures].[Store Sales])").returns(
            "1,355,761,899" );
    }

    @Test
    void testCovarianceN(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "CovarianceN({[Store].[All Stores].[USA].children}, [Measures].[Unit Sales], [Measures].[Store Sales])").returns(
            "2,033,642,849" );
    }

}
