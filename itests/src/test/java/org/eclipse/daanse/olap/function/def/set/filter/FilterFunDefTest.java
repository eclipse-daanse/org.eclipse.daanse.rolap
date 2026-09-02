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
package org.eclipse.daanse.olap.function.def.set.filter;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.net.URL;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.exceptions.QueryTimeoutException;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class FilterFunDefTest {

    public static class FoodmartData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

    /**
     * EMF version of TestFilterWillTimeoutModifier
     * Simple modifier for timeout testing - currently does not add UserDefinedFunction
     */
    public static class TestFilterWillTimeoutModifierEmf implements CatalogMappingSupplier {

        private CatalogImpl catalog;

        public TestFilterWillTimeoutModifierEmf(Catalog cat) {
            // Copy catalog using EcoreUtil
            catalog = EmfUtil.copy((CatalogImpl) cat);

            /* TODO: UserDefinedFunction
             * When UserDefinedFunction support is added to EMF mapping, implement:
             *
             * org.eclipse.daanse.rolap.mapping.emf.rolapmapping.UserDefinedFunction udf =
             *     org.eclipse.daanse.rolap.mapping.emf.rolapmapping.RolapMappingFactory.eINSTANCE.createUserDefinedFunction();
             * udf.setName("SleepUdf");
             * udf.setClassName(BasicQueryTest.SleepUdf.class.getName());
             * catalog.getUserDefinedFunctions().add(udf);
             */
        }

        @Override
        public Catalog get() {
            return catalog;
        }
    }

    /**
     * Make sure that slicer is in force when expression is applied on axis, E.g. select filter([Customers].members, [Unit
     * Sales] > 100) from sales where ([Time].[1998])
     */
    @Test
    void testFilterWithSlicer(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns,\n"
                + " filter([Customers].[USA].children,\n"
                + "        [Measures].[Unit Sales] > 20000) on rows\n"
                + "from Sales\n"
                + "where ([Time].[1997].[Q1])" );
        Axis rows = result.getAxes()[ 1 ];
        // if slicer were ignored, there would be 3 rows
        assertEquals( 1, rows.getPositions().size() );
        Cell cell = result.getCell( new int[] { 0, 0 } );
        assertEquals( "30,114", cell.getFormattedValue() );
    }

    @Test
    void testFilterCompound(Context<?> context) {
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns,\n"
                + "  Filter(\n"
                + "    CrossJoin(\n"
                + "      [Gender].Children,\n"
                + "      [Customers].[USA].Children),\n"
                + "    [Measures].[Unit Sales] > 9500) on rows\n"
                + "from Sales\n"
                + "where ([Time].[1997].[Q1])" );
        List<Position> rows = result.getAxes()[ 1 ].getPositions();
        assertEquals( 3, rows.size() );
        assertEquals( "F", rows.get( 0 ).get( 0 ).getName() );
        assertEquals( "WA", rows.get( 0 ).get( 1 ).getName() );
        assertEquals( "M", rows.get( 1 ).get( 0 ).getName() );
        assertEquals( "OR", rows.get( 1 ).get( 1 ).getName() );
        assertEquals( "M", rows.get( 2 ).get( 0 ).getName() );
        assertEquals( "WA", rows.get( 2 ).get( 1 ).getName() );
    }

    //TODO: reanable
    @Disabled //UserDefinedFunction
    @Test
    @RolapConfig(key = ConfigConstants.QUERY_TIMEOUT, value = "3", type = Integer.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "false", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, TestFilterWillTimeoutModifierEmf.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testFilterWillTimeout(Context<?> context) {
        try {
            executeQuery(context.getConnectionWithDefaultRole(), "select {"
                + "Filter("
                    + "Filter(CrossJoin([Customers].[Name].members, [Product].[Product Name].members), SleepUdf([Measures]"
                    + ".[Unit Sales]) > 0),"
                    + " SleepUdf([Measures].[Sales Count]) > 5) "
                + "} on columns from Sales" );
        } catch ( QueryTimeoutException e ) {
            return;
        }
        fail( "should have timed out" );
    }


    @Test
    void testFilterEmpty(Context<?> context) {
        // Unlike "Descendants(<set>, ...)", we do not need to know the precise
        // type of the set, therefore it is OK if the set is empty.
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Filter({}, 1=0)")
            .returns(
            "" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Filter({[Time].[Time].Children}, 1=0)")
            .returns(
            "" );
    }


    @Test
    void testFilterCalcSlicer(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member [Time].[Time].[Date Range] as \n"
                + "'Aggregate({[Time].[1997].[Q1]:[Time].[1997].[Q3]})'\n"
                + "select\n"
                + "{[Measures].[Unit Sales],[Measures].[Store Cost],\n"
                + "[Measures].[Store Sales]} ON columns,\n"
                + "NON EMPTY Filter ([Store].[Store State].members,\n"
                + "[Measures].[Store Cost] > 75000) ON rows\n"
                + "from [Sales] where [Time].[Date Range]")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Time].[Time].[Date Range]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Store Cost]}\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[USA].[WA]}\n"
                + "Row #0: 90,131\n"
                + "Row #0: 76,151.59\n"
                + "Row #0: 190,776.88\n" );
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member [Time].[Time].[Date Range] as \n"
                + "'Aggregate({[Time].[1997].[Q1]:[Time].[1997].[Q3]})'\n"
                + "select\n"
                + "{[Measures].[Unit Sales],[Measures].[Store Cost],\n"
                + "[Measures].[Store Sales]} ON columns,\n"
                + "NON EMPTY Order (Filter ([Store].[Store State].members,\n"
                + "[Measures].[Store Cost] > 100),[Measures].[Store Cost], DESC) ON rows\n"
                + "from [Sales] where [Time].[Date Range]")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Time].[Time].[Date Range]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Store Cost]}\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[USA].[WA]}\n"
                + "{[Store].[Store].[USA].[CA]}\n"
                + "{[Store].[Store].[USA].[OR]}\n"
                + "Row #0: 90,131\n"
                + "Row #0: 76,151.59\n"
                + "Row #0: 190,776.88\n"
                + "Row #1: 53,312\n"
                + "Row #1: 45,435.93\n"
                + "Row #1: 113,966.00\n"
                + "Row #2: 51,306\n"
                + "Row #2: 43,033.82\n"
                + "Row #2: 107,823.63\n" );
    }

}
