/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.olap.function.def.udf.currentdatemember;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.eclipse.daanse.rolap.SchemaModifiersEmf;


@RolapContextTest(FoodmartTestInstance.class)
class CurrentDateMemberFunDefTest {

    public static class FoodmartData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

    @Disabled //TODO: UserDefinedFunction
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.CurrentDateMemberUdfTestModifier1.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testCurrentDateMemberUdf(Context<?> context) {
        //TODO: context redesign
        //Assertions.fail("Handle comment , Context<?> redesign nedded");
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "SELECT NON EMPTY {[Measures].[Org Salary]} ON COLUMNS, "
            + "NON EMPTY {MockCurrentDateMember([Time].[Time], \"[yyyy]\")} ON ROWS "
            + "FROM [HR] ")
            .returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Org Salary]}\n"
            + "Axis #2:\n"
            + "{[Time].[1997]}\n"
            + "Row #0: $39,431.67\n");
    }

    /**
     * test for MONDRIAN-2256 issue. Tests if method returns member with
     * dimension info or not. To get a number as a result you should change
     * current year to 1997. In this case expected should be ended with
     * "266,773\n"
    */
    @Test
    void testGetReturnType(Context<?> context) {
        Connection connection=context.getConnectionWithDefaultRole();
        String query = "WITH MEMBER [Time].[Time].[YTD] AS SUM( YTD(CurrentDateMember"
             + "([Time].[Time], '[\"Time\"]\\.[\"Time\"]\\.[yyyy]\\.[Qq].[m]', EXACT)), Measures.[Unit Sales]) SELECT Time.Time.YTD on 0 FROM sales";
        String expected = "Axis #0:\n" + "{}\n" + "Axis #1:\n"
             + "{[Time].[Time].[YTD]}\n" + "Row #0: \n";
        assertThatQuery(connection, query).returnsGrid( expected );
    }

}
