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
package org.eclipse.daanse.olap.function.def.hierarchize;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.SchemaModifiersEmf;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;



@RolapContextTest(FoodmartTestInstance.class)
class HierarchizeFunDefTest {

    public static class FoodmartData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }


    @Test
    void testHierarchize(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Hierarchize(\n"
                + "    {[Product].[All Products], "
                + "     [Product].[Food],\n"
                + "     [Product].[Drink],\n"
                + "     [Product].[Non-Consumable],\n"
                + "     [Product].[Food].[Eggs],\n"
                + "     [Product].[Drink].[Dairy]})")
            .returns(

            "[Product].[Product].[All Products]\n"
                + "[Product].[Product].[Drink]\n"
                + "[Product].[Product].[Drink].[Dairy]\n"
                + "[Product].[Product].[Food]\n"
                + "[Product].[Product].[Food].[Eggs]\n"
                + "[Product].[Product].[Non-Consumable]" );
    }

    @Test
    void testHierarchizePost(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Hierarchize(\n"
                + "    {[Product].[All Products], "
                + "     [Product].[Food],\n"
                + "     [Product].[Food].[Eggs],\n"
                + "     [Product].[Drink].[Dairy]},\n"
                + "  POST)")
            .returns(

            "[Product].[Product].[Drink].[Dairy]\n"
                + "[Product].[Product].[Food].[Eggs]\n"
                + "[Product].[Product].[Food]\n"
                + "[Product].[Product].[All Products]" );
    }

    @Test
    void testHierarchizePC(Context<?> context) {
        //getTestContext().withCube( "HR" ).
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Hierarchize(\n"
                + "   { Subset([Employees].Members, 90, 10),\n"
                + "     Head([Employees].Members, 5) })")
            .returns(
            "[Employees].[Employees].[All Employees]\n"
                + "[Employees].[Employees].[Sheri Nowmer]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Beverly Baker]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Beverly Baker].[Shauna Wyro]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Cheryl Thorton]"
                + ".[Leopoldo Renfro]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Cheryl Thorton]"
                + ".[Donna Brockett]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Cheryl Thorton]"
                + ".[Laurie Anderson]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Cheryl Thorton]"
                + ".[Louis Gomez]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Cheryl Thorton]"
                + ".[Melvin Glass]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Cheryl Thorton]"
                + ".[Kristin Cohen]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Cheryl Thorton]"
                + ".[Susan Kharman]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Cheryl Thorton]"
                + ".[Gordon Kirschner]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Cheryl Thorton]"
                + ".[Geneva Kouba]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Pedro Castillo].[Lin Conley].[Paul Tays].[Cheryl Thorton]"
                + ".[Tricia Clark]" );
    }

    @Test
    void testHierarchizeCrossJoinPre(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Hierarchize(\n"
                + "  CrossJoin(\n"
                + "    {[Product].[All Products], "
                + "     [Product].[Food],\n"
                + "     [Product].[Food].[Eggs],\n"
                + "     [Product].[Drink].[Dairy]},\n"
                + "    [Gender].MEMBERS),\n"
                + "  PRE)")
            .returns(

            "{[Product].[Product].[All Products], [Gender].[Gender].[All Gender]}\n"
                + "{[Product].[Product].[All Products], [Gender].[Gender].[F]}\n"
                + "{[Product].[Product].[All Products], [Gender].[Gender].[M]}\n"
                + "{[Product].[Product].[Drink].[Dairy], [Gender].[Gender].[All Gender]}\n"
                + "{[Product].[Product].[Drink].[Dairy], [Gender].[Gender].[F]}\n"
                + "{[Product].[Product].[Drink].[Dairy], [Gender].[Gender].[M]}\n"
                + "{[Product].[Product].[Food], [Gender].[Gender].[All Gender]}\n"
                + "{[Product].[Product].[Food], [Gender].[Gender].[F]}\n"
                + "{[Product].[Product].[Food], [Gender].[Gender].[M]}\n"
                + "{[Product].[Product].[Food].[Eggs], [Gender].[Gender].[All Gender]}\n"
                + "{[Product].[Product].[Food].[Eggs], [Gender].[Gender].[F]}\n"
                + "{[Product].[Product].[Food].[Eggs], [Gender].[Gender].[M]}" );
    }

    @Test
    void testHierarchizeCrossJoinPost(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Hierarchize(\n"
                + "  CrossJoin(\n"
                + "    {[Product].[All Products], "
                + "     [Product].[Food],\n"
                + "     [Product].[Food].[Eggs],\n"
                + "     [Product].[Drink].[Dairy]},\n"
                + "    [Gender].MEMBERS),\n"
                + "  POST)")
            .returns(

            "{[Product].[Product].[Drink].[Dairy], [Gender].[Gender].[F]}\n"
                + "{[Product].[Product].[Drink].[Dairy], [Gender].[Gender].[M]}\n"
                + "{[Product].[Product].[Drink].[Dairy], [Gender].[Gender].[All Gender]}\n"
                + "{[Product].[Product].[Food].[Eggs], [Gender].[Gender].[F]}\n"
                + "{[Product].[Product].[Food].[Eggs], [Gender].[Gender].[M]}\n"
                + "{[Product].[Product].[Food].[Eggs], [Gender].[Gender].[All Gender]}\n"
                + "{[Product].[Product].[Food], [Gender].[Gender].[F]}\n"
                + "{[Product].[Product].[Food], [Gender].[Gender].[M]}\n"
                + "{[Product].[Product].[Food], [Gender].[Gender].[All Gender]}\n"
                + "{[Product].[Product].[All Products], [Gender].[Gender].[F]}\n"
                + "{[Product].[Product].[All Products], [Gender].[Gender].[M]}\n"
                + "{[Product].[Product].[All Products], [Gender].[Gender].[All Gender]}" );
    }

    /**
     * Tests that the Hierarchize function works correctly when applied to a level whose ordering is determined by an
     * 'ordinal' property. TODO: fix this test (bug 1220787)
     * <p>
     * WG: Note that this is disabled right now due to its impact on other tests later on within the test suite,
     * specifically XMLA tests that return a list of cubes.  We could run this test after XMLA, or clear out the cache to
     * solve this.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.FunctionTestModifier3.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testHierarchizeOrdinal(Context<?> context) {
        //TestContext<?> testContext<?> = getTestContext().withCube( "[Sales_Hierarchize]" );
    /*
    connection.getSchema().createCube(
      "<Cube name=\"Sales_Hierarchize\">\n"
        + "  <Table name=\"sales_fact_1997\"/>\n"
        + "  <Dimension name=\"Time_Alphabetical\" type=\"TimeDimension\" foreignKey=\"time_id\">\n"
        + "    <Hierarchy hasAll=\"false\" primaryKey=\"time_id\">\n"
        + "      <Table name=\"time_by_day\"/>\n"
        + "      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"\n"
        + "          levelType=\"TimeYears\"/>\n"
        + "      <Level name=\"Quarter\" column=\"quarter\" uniqueMembers=\"false\"\n"
        + "          levelType=\"TimeQuarters\"/>\n"
        + "      <Level name=\"Month\" column=\"month_of_year\" uniqueMembers=\"false\" type=\"Numeric\"\n"
        + "          ordinalColumn=\"the_month\"\n"
        + "          levelType=\"TimeMonths\"/>\n"
        + "    </Hierarchy>\n"
        + "  </Dimension>\n"
        + "\n"
        + "  <Dimension name=\"Month_Alphabetical\" type=\"TimeDimension\" foreignKey=\"time_id\">\n"
        + "    <Hierarchy hasAll=\"false\" primaryKey=\"time_id\">\n"
        + "      <Table name=\"time_by_day\"/>\n"
        + "      <Level name=\"Month\" column=\"month_of_year\" uniqueMembers=\"false\" type=\"Numeric\"\n"
        + "          ordinalColumn=\"the_month\"\n"
        + "          levelType=\"TimeMonths\"/>\n"
        + "    </Hierarchy>\n"
        + "  </Dimension>\n"
        + "\n"
        + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
        + "      formatString=\"Standard\"/>\n"
        + "</Cube>" );
     */
        final Connection connection = context.getConnectionWithDefaultRole();

        // The [Time_Alphabetical] is ordered alphabetically by month
        assertThatAxis(connection, "[Sales_Hierarchize]",
            "Hierarchize([Time_Alphabetical].members)")
            .returns(
            "[Time_Alphabetical].[Time_Alphabetical].[1997]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q1]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q1].[2]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q1].[1]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q1].[3]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q2]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q2].[4]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q2].[6]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q2].[5]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q3]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q3].[8]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q3].[7]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q3].[9]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q4]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q4].[12]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q4].[11]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1997].[Q4].[10]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q1]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q1].[2]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q1].[1]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q1].[3]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q2]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q2].[4]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q2].[6]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q2].[5]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q3]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q3].[8]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q3].[7]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q3].[9]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q4]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q4].[12]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q4].[11]\n"
                + "[Time_Alphabetical].[Time_Alphabetical].[1998].[Q4].[10]" );

        // The [Month_Alphabetical] is a single-level hierarchy ordered
        // alphabetically by month.
        assertThatAxis(connection, "[Sales_Hierarchize]",
            "Hierarchize([Month_Alphabetical].members)")
            .returns(
            "[Month_Alphabetical].[Month_Alphabetical].[4]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[8]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[12]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[2]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[1]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[7]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[6]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[3]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[5]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[11]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[10]\n"
                + "[Month_Alphabetical].[Month_Alphabetical].[9]" );
    }

}
