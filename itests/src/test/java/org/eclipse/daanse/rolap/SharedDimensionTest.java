/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2005-2005 Julian Hyde
// Copyright (C) 2005-2017 Hitachi Vantara and others
// All Rights Reserved.
*/
package org.eclipse.daanse.rolap;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import org.eclipse.daanse.rolap.CellKeyTest.FoodmartData;

/**
 * Unit test for shared dimensions.
 *
 * @author Rushan Chen
 */
@RolapContextTest(FoodmartTestInstance.class)
class SharedDimensionTest  {

    private static final String sharedDimension =
        "<Dimension name=\"Employee\">\n"
        + "  <Hierarchy hasAll=\"true\" primaryKey=\"employee_id\" primaryKeyTable=\"employee\">\n"
        + "    <Join leftKey=\"supervisor_id\" rightKey=\"employee_id\">\n"
        + "      <Table name=\"employee\" alias=\"employee\" />\n"
        + "      <Table name=\"employee\" alias=\"employee_manager\" />\n"
        + "    </Join>\n"
        + "    <Level name=\"Role\" table=\"employee_manager\" column=\"management_role\" uniqueMembers=\"true\"/>\n"
        + "    <Level name=\"Title\" table=\"employee_manager\" column=\"position_title\" uniqueMembers=\"false\"/>\n"
        + "  </Hierarchy>\n"
        + "</Dimension>";

    // Base Cube A: use product_id as foreign key for Employee dimension
    // because there exist rows satisfying the join condition
    // "employee.employee_id = inventory_fact_1997.product_id"
    private static final String cubeA =
        "<Cube name=\"Employee Store Analysis A\">\n"
        + "  <Table name=\"inventory_fact_1997\" alias=\"inventory\" />\n"
        + "  <DimensionUsage name=\"Employee\" source=\"Employee\" foreignKey=\"product_id\" />\n"
        + "  <DimensionUsage name=\"Store Type\" source=\"Store Type\" foreignKey=\"warehouse_id\" />\n"
        + "  <Measure name=\"Employee Store Sales\" aggregator=\"sum\" formatString=\"$#,##0\" column=\"warehouse_sales\" />\n"
        + "  <Measure name=\"Employee Store Cost\" aggregator=\"sum\" formatString=\"$#,##0\" column=\"warehouse_cost\" />\n"
        + "</Cube>";

    // Base Cube B: use time_id as foreign key for Employee diemnsion
    // because there exist rows satidfying the join condition
    // "employee.employee_id = inventory_fact_1997.time_id"
    private static final String cubeB =
        "<Cube name=\"Employee Store Analysis B\">\n"
        + "  <Table name=\"inventory_fact_1997\" alias=\"inventory\" />\n"
        + "  <DimensionUsage name=\"Employee\" source=\"Employee\" foreignKey=\"time_id\" />\n"
        + "  <DimensionUsage name=\"Store Type\" source=\"Store Type\" foreignKey=\"store_id\" />\n"
        + "  <Measure name=\"Employee Store Sales\" aggregator=\"sum\" formatString=\"$#,##0\" column=\"warehouse_sales\" />\n"
        + "  <Measure name=\"Employee Store Cost\" aggregator=\"sum\" formatString=\"$#,##0\" column=\"warehouse_cost\" />\n"
        + "</Cube>";

    // Some product_id's match store_id. Used to test MONDRIAN-1243
    // without having to alter fact table.
    private static final String cubeAltSales =
        "<Cube name=\"Alternate Sales\">\n"
        + "  <Table name=\"sales_fact_1997\"/>\n"
        + "  <DimensionUsage name=\"Store Type\" source=\"Store Type\" foreignKey=\"store_id\" />\n"
        + "  <DimensionUsage name=\"Store\" source=\"Store\" foreignKey=\"store_id\"/>\n"
        + "  <DimensionUsage name=\"Buyer\" source=\"Store\" visible=\"true\" foreignKey=\"product_id\" highCardinality=\"false\"/>\n"
        + "  <DimensionUsage name=\"BuyerTwo\" source=\"Store\" visible=\"true\" foreignKey=\"product_id\" highCardinality=\"false\"/>\n"
        + "  <DimensionUsage name=\"Store Size in SQFT\" source=\"Store Size in SQFT\"\n"
        + "      foreignKey=\"store_id\"/>\n"
        + "  <DimensionUsage name=\"Store Type\" source=\"Store Type\" foreignKey=\"store_id\"/>\n"
        + "  <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>\n"
        + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\" formatString=\"Standard\"/>\n"
        + "</Cube>";

    private static final String virtualCube =
        "<VirtualCube name=\"Employee Store Analysis\">\n"
        + "  <VirtualCubeDimension name=\"Employee\"/>\n"
        + "  <VirtualCubeDimension name=\"Store Type\"/>\n"
        + "  <VirtualCubeMeasure cubeName=\"Employee Store Analysis A\" name=\"[Measures].[Employee Store Sales]\"/>\n"
        + "  <VirtualCubeMeasure cubeName=\"Employee Store Analysis B\" name=\"[Measures].[Employee Store Cost]\"/>\n"
        + "</VirtualCube>";

    private static final String queryCubeA =
        "with\n"
        + "  set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Employee], [*BASE_MEMBERS_Store Type])'\n"
        + "  set [*BASE_MEMBERS_Measures] as '{[Measures].[Employee Store Sales], [Measures].[Employee Store Cost]}'\n"
        + "  set [*BASE_MEMBERS_Employee] as '[Employee].[Employee].[Role].Members'\n"
        + "  set [*NATIVE_MEMBERS_Employee] as 'Generate([*NATIVE_CJ_SET], {[Employee].[Employee].CurrentMember})'\n"
        + "  set [*BASE_MEMBERS_Store Type] as '[Store Type].[Store Type].[Store Type].Members'\n"
        + "  set [*NATIVE_MEMBERS_Store Type] as 'Generate([*NATIVE_CJ_SET], {[Store Type].[Store Type].CurrentMember})'\n"
        + "select\n"
        + "  [*BASE_MEMBERS_Measures] ON COLUMNS,\n"
        + "  NON EMPTY Generate([*NATIVE_CJ_SET], {([Employee].[Employee].CurrentMember, [Store Type].[Store Type].CurrentMember)}) ON ROWS\n"
        + "from\n"
        + "  [Employee Store Analysis A]";

    private static final String queryCubeB =
        "with\n"
        + "  set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Employee], [*BASE_MEMBERS_Store Type])'\n"
        + "  set [*BASE_MEMBERS_Measures] as '{[Measures].[Employee Store Sales], [Measures].[Employee Store Cost]}'\n"
        + "  set [*BASE_MEMBERS_Employee] as '[Employee].[Employee].[Role].Members'\n"
        + "  set [*NATIVE_MEMBERS_Employee] as 'Generate([*NATIVE_CJ_SET], {[Employee].[Employee].CurrentMember})'\n"
        + "  set [*BASE_MEMBERS_Store Type] as '[Store Type].[Store Type].[Store Type].Members'\n"
        + "  set [*NATIVE_MEMBERS_Store Type] as 'Generate([*NATIVE_CJ_SET], {[Store Type].[Store Type].CurrentMember})'\n"
        + "select\n"
        + "  [*BASE_MEMBERS_Measures] ON COLUMNS,\n"
        + "  NON EMPTY Generate([*NATIVE_CJ_SET], {([Employee].[Employee].CurrentMember, [Store Type].[Store Type].CurrentMember)}) ON ROWS\n"
        + "from\n"
        + "  [Employee Store Analysis B]";

    private static final String queryVirtualCube =
        "with\n"
        + "  set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Employee], [*BASE_MEMBERS_Store Type])'\n"
        + "  set [*BASE_MEMBERS_Measures] as '{[Measures].[Employee Store Sales], [Measures].[Employee Store Cost]}'\n"
        + "  set [*BASE_MEMBERS_Employee] as '[Employee].[Employee].[Role].Members'\n"
        + "  set [*NATIVE_MEMBERS_Employee] as 'Generate([*NATIVE_CJ_SET], {[Employee].[Employee].CurrentMember})'\n"
        + "  set [*BASE_MEMBERS_Store Type] as '[Store Type].[Store Type].[Store Type].Members'\n"
        + "  set [*NATIVE_MEMBERS_Store Type] as 'Generate([*NATIVE_CJ_SET], {[Store Type].[Store Type].CurrentMember})'\n"
        + "select\n"
        + "  [*BASE_MEMBERS_Measures] ON COLUMNS,\n"
        + "  NON EMPTY Generate([*NATIVE_CJ_SET], {([Employee].[Employee].CurrentMember, [Store Type].[Store Type].CurrentMember)}) ON ROWS\n"
        + "from\n"
        + "  [Employee Store Analysis]";

    private static final String queryStoreCube =
        "with set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Store Type], [*BASE_MEMBERS_Store])'\n"
        + "set [*BASE_MEMBERS_Measures] as '{[Measures].[Store Sqft]}'\n"
        + "set [*BASE_MEMBERS_Store Type] as '[Store Type].[Store Type].[Store Type].Members'\n"
        + "set [*BASE_MEMBERS_Store] as '[Store].[Store].[Store State].Members'\n"
        + "select [*BASE_MEMBERS_Measures] ON COLUMNS,\n"
        + "Non Empty Generate([*NATIVE_CJ_SET], {[Store Type].[Store Type].CurrentMember}) on rows from [Store]";

    private static final String queryNECJMemberList =
        "select {[Measures].[Employee Store Sales]} on columns,\n"
        + "NonEmptyCrossJoin([Store Type].[Store Type].[Store Type].Members,\n"
        + "{[Employee].[Employee].[All Employees].[Middle Management],\n"
        + " [Employee].[Employee].[All Employees].[Store Management]})\n"
        + "on rows from [Employee Store Analysis B]";

    private static final String queryNECJMultiLevelMemberList =
        "select {[Employee Store Sales]} on columns, "
        + "NonEmptyCrossJoin([Store Type].[Store Type].[Store Type].Members, "
        + "{[Employee].[Employee].[Store Management].[Store Manager], "
        + " [Employee].[Employee].[Senior Management].[President]}) "
        + "on rows from [Employee Store Analysis B]";

    private static final String querySF1711865 =
        "select NON EMPTY {[Product].[Product Family].Members} ON COLUMNS from [Sales 2]";

    private static final String resultCubeA =
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Employee Store Sales]}\n"
        + "{[Measures].[Employee Store Cost]}\n"
        + "Axis #2:\n"
        + "{[Employee].[Employee].[Middle Management], [Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Employee].[Employee].[Middle Management], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Employee].[Employee].[Senior Management], [Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Employee].[Employee].[Senior Management], [Store Type].[Store Type].[Gourmet Supermarket]}\n"
        + "{[Employee].[Employee].[Senior Management], [Store Type].[Store Type].[Mid-Size Grocery]}\n"
        + "{[Employee].[Employee].[Senior Management], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Employee].[Employee].[Senior Management], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Gourmet Supermarket]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Mid-Size Grocery]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Supermarket]}\n"
        + "Row #0: $200\n"
        + "Row #0: $87\n"
        + "Row #1: $161\n"
        + "Row #1: $68\n"
        + "Row #2: $1,721\n"
        + "Row #2: $739\n"
        + "Row #3: $261\n"
        + "Row #3: $114\n"
        + "Row #4: $257\n"
        + "Row #4: $111\n"
        + "Row #5: $196\n"
        + "Row #5: $101\n"
        + "Row #6: $3,993\n"
        + "Row #6: $1,858\n"
        + "Row #7: $45,014\n"
        + "Row #7: $20,604\n"
        + "Row #8: $7,231\n"
        + "Row #8: $3,211\n"
        + "Row #9: $8,171\n"
        + "Row #9: $3,635\n"
        + "Row #10: $4,471\n"
        + "Row #10: $2,045\n"
        + "Row #11: $77,236\n"
        + "Row #11: $34,842\n";

    private static final String resultCubeB =
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Employee Store Sales]}\n"
        + "{[Measures].[Employee Store Cost]}\n"
        + "Axis #2:\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Gourmet Supermarket]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Mid-Size Grocery]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Supermarket]}\n"
        + "Row #0: $61,860\n"
        + "Row #0: $28,093\n"
        + "Row #1: $10,156\n"
        + "Row #1: $4,482\n"
        + "Row #2: $10,212\n"
        + "Row #2: $4,576\n"
        + "Row #3: $5,932\n"
        + "Row #3: $2,714\n"
        + "Row #4: $108,610\n"
        + "Row #4: $49,178\n";

    private static final String resultVirtualCube =
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Employee Store Sales]}\n"
        + "{[Measures].[Employee Store Cost]}\n"
        + "Axis #2:\n"
        + "{[Employee].[Employee].[Middle Management], [Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Employee].[Employee].[Middle Management], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Employee].[Employee].[Senior Management], [Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Employee].[Employee].[Senior Management], [Store Type].[Store Type].[Gourmet Supermarket]}\n"
        + "{[Employee].[Employee].[Senior Management], [Store Type].[Store Type].[Mid-Size Grocery]}\n"
        + "{[Employee].[Employee].[Senior Management], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Employee].[Employee].[Senior Management], [Store Type].[Store Type].[Supermarket]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Gourmet Supermarket]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Mid-Size Grocery]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Employee].[Employee].[Store Management], [Store Type].[Store Type].[Supermarket]}\n"
        + "Row #0: $200\n"
        + "Row #0: \n"
        + "Row #1: $161\n"
        + "Row #1: \n"
        + "Row #2: $1,721\n"
        + "Row #2: \n"
        + "Row #3: $261\n"
        + "Row #3: \n"
        + "Row #4: $257\n"
        + "Row #4: \n"
        + "Row #5: $196\n"
        + "Row #5: \n"
        + "Row #6: $3,993\n"
        + "Row #6: \n"
        + "Row #7: $45,014\n"
        + "Row #7: $28,093\n"
        + "Row #8: $7,231\n"
        + "Row #8: $4,482\n"
        + "Row #9: $8,171\n"
        + "Row #9: $4,576\n"
        + "Row #10: $4,471\n"
        + "Row #10: $2,714\n"
        + "Row #11: $77,236\n"
        + "Row #11: $49,178\n";

    // This result is actually incorrect for native evaluation.
    // Keep the test case here to test the SQL generation.
    private static final String resultStoreCube =
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Store Sqft]}\n"
        + "Axis #2:\n"
        + "{[Store Type].[Store Type].[Deluxe Supermarket]}\n"
        + "{[Store Type].[Store Type].[Gourmet Supermarket]}\n"
        + "{[Store Type].[Store Type].[Mid-Size Grocery]}\n"
        + "{[Store Type].[Store Type].[Small Grocery]}\n"
        + "{[Store Type].[Store Type].[Supermarket]}\n"
        + "Row #0: 146,045\n"
        + "Row #1: 47,447\n"
        + "Row #2: 109,343\n"
        + "Row #3: 75,281\n"
        + "Row #4: 193,480\n";

    private static final String resultNECJMemberList =
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Employee Store Sales]}\n"
        + "Axis #2:\n"
        + "{[Store Type].[Store Type].[Deluxe Supermarket], [Employee].[Employee].[Store Management]}\n"
        + "{[Store Type].[Store Type].[Gourmet Supermarket], [Employee].[Employee].[Store Management]}\n"
        + "{[Store Type].[Store Type].[Mid-Size Grocery], [Employee].[Employee].[Store Management]}\n"
        + "{[Store Type].[Store Type].[Small Grocery], [Employee].[Employee].[Store Management]}\n"
        + "{[Store Type].[Store Type].[Supermarket], [Employee].[Employee].[Store Management]}\n"
        + "Row #0: $61,860\n"
        + "Row #1: $10,156\n"
        + "Row #2: $10,212\n"
        + "Row #3: $5,932\n"
        + "Row #4: $108,610\n";

    private static final String resultNECJMultiLevelMemberList =
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Employee Store Sales]}\n"
        + "Axis #2:\n"
        + "{[Store Type].[Store Type].[Deluxe Supermarket], [Employee].[Employee].[Store Management].[Store Manager]}\n"
        + "{[Store Type].[Store Type].[Gourmet Supermarket], [Employee].[Employee].[Store Management].[Store Manager]}\n"
        + "{[Store Type].[Store Type].[Supermarket], [Employee].[Employee].[Store Management].[Store Manager]}\n"
        + "Row #0: $1,783\n"
        + "Row #1: $286\n"
        + "Row #2: $1,020\n";

    private static final String resultSF1711865 =
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Product].[Product].[Drink]}\n"
        + "{[Product].[Product].[Food]}\n"
        + "{[Product].[Product].[Non-Consumable]}\n"
        + "Row #0: 7,978\n"
        + "Row #0: 62,445\n"
        + "Row #0: 16,414\n";

    private static final String queryIssue1243 =
        "select [Measures].[Unit Sales] on columns,\n"
        + "non empty [Buyer].[USA].[OR].[Portland].children on rows\n"
        + "from [Alternate Sales]";

    private static final String resultIssue1243 =
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Buyer].[Store].[USA].[OR].[Portland].[Store 11]}\n"
        + "Row #0: 238\n";

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SharedDimensionTestModifier.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @DisabledIfSystemProperty(named = "test.disable.knownFails", matches = "true")
    //NOTE: issue with alias
    void testA(Context<?> context) {
        // Schema has two cubes sharing a dimension.
        // Query from the first cube.
        //getTestContextForSharedDimCubeACubeB(context);

        assertThatQuery(context.getConnectionWithDefaultRole(), queryCubeA).returnsGrid(resultCubeA);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SharedDimensionTestModifier.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @DisabledIfSystemProperty(named = "test.disable.knownFails", matches = "true")
    //NOTE: issue with alias
    void testB(Context<?> context) {
        // Schema has two cubes sharing a dimension.
        // Query from the second cube.
        //getTestContextForSharedDimCubeACubeB(context);

        assertThatQuery(context.getConnectionWithDefaultRole(), queryCubeB).returnsGrid(resultCubeB);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SharedDimensionTestModifier.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @DisabledIfSystemProperty(named = "test.disable.knownFails", matches = "true")
    //NOTE: issue with alias
    void testVirtualCube(Context<?> context) {
        // Schema has two cubes sharing a dimension, and a virtual cube built
        // over these two cubes.
        // Query from the virtual cube.
        //withSchemaEmf(context, SchemaModifiersEmf.SharedDimensionTestModifier::new);

        assertThatQuery(context.getConnectionWithDefaultRole(), queryVirtualCube).returnsGrid(resultVirtualCube);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SharedDimensionTestModifier.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @DisabledIfSystemProperty(named = "test.disable.knownFails", matches = "true")
    //NOTE: issue with alias
    void testNECJMemberList(Context<?> context) {
        // Schema has two cubes sharing a dimension.
        // Query from the second cube.
        //getTestContextForSharedDimCubeACubeB(context);

        assertThatQuery(context.getConnectionWithDefaultRole(),
            queryNECJMemberList).returnsGrid(
            resultNECJMemberList);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SharedDimensionTestModifier.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @DisabledIfSystemProperty(named = "test.disable.knownFails", matches = "true")
    //NOTE: issue with alias
    void testNECJMultiLevelMemberList(Context<?> context) {
        // Schema has two cubes sharing a dimension.
        // Query from the first cube.
        // This is a case where not using alias not only affects performance,
        // but also produces incorrect result.
        //getTestContextForSharedDimCubeACubeB(context);

        assertThatQuery(context.getConnectionWithDefaultRole(),
            queryNECJMultiLevelMemberList).returnsGrid(
            resultNECJMultiLevelMemberList);
    }

    /**
     * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-286">
     * MONDRIAN-286, "NullPointerException for certain mdx using [Sales 2]"</a>.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SharedDimensionTestModifier.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian286(Context<?> context) {
        // Test for sourceforge.net bug 1711865 (MONDRIAN-286).
        // Use the default FoodMart schema
        assertThatQuery(context.getConnectionWithDefaultRole(), querySF1711865).returnsGrid(resultSF1711865);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SharedDimensionTestModifier.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testStoreCube(Context<?> context) {
        // Use the default FoodMart schema
        assertThatQuery(context.getConnectionWithDefaultRole(), queryStoreCube).returnsGrid(resultStoreCube);
    }

    /**
     * Test case for <a href="http://jira.pentaho.com/browse/MONDRIAN-1243">
     * MONDRIAN-1243, "Wrong table alias in SQL generated to populate member
     * cache"</a>.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SharedDimensionTestModifier1.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian1243WrongAlias(Context<?> context) {
        //getTestContextForSharedDimCubeAltSales(context);
        assertThatQuery(context.getConnectionWithDefaultRole(),
            queryIssue1243).returnsGrid(
            resultIssue1243);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SharedDimensionTestModifier1.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMemberUniqueNameForSharedWithChangedName(Context<?> context) {
        //getTestContextForSharedDimCubeAltSales(context);
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with "
            + " member [BuyerTwo].[Mexico].[calc] as '[BuyerTwo].[Mexico]' "
            + "select [BuyerTwo].[Mexico].[calc] on 0 from [Alternate Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[BuyerTwo].[Store].[Mexico].[calc]}\n"
            + "Row #0: 1,389\n");
    }
}
