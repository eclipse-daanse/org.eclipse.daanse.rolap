/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.test;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;


import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Arrays;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.result.AllocationPolicy;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.CellSet;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.api.result.Scenario;
import org.eclipse.daanse.olap.impl.TraditionalCellSetFormatter;
import org.eclipse.daanse.rolap.common.writeback.ScenarioImpl;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.MdxAssert;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.SchemaModifiersEmf;
import org.eclipse.daanse.test.FoodmartData;

/**
 * Test for writeback functionality.
 *
 * @author jhyde
 * @since 24 April, 2009
 */
@RolapContextTest(FoodmartTestInstance.class)
class ScenarioTest {
    /**
     * Tests creating a scenario and setting a connection's active scenario.
     */
    @Test
    void testCreateScenario(Context<?> context) throws SQLException {
        final Connection connection =
            context.getConnectionWithDefaultRole();
        try {
            assertNull(connection.getScenario());
            final Scenario scenario = connection.createScenario();
            assertNotNull(scenario);
            connection.setScenario(scenario);
            assertSame(scenario, connection.getScenario());
            connection.setScenario(null);
            assertNull(connection.getScenario());
            final Scenario scenario2 = connection.createScenario();
            assertNotNull(scenario2);
            connection.setScenario(scenario2);
        } finally {
            connection.setScenario(null);
        }
    }

    /**
     * Tests setting the value of one cell.
     */
    @Test
    void testSetCell(Context<?> context) throws SQLException {
        final Connection connection =
            context.getConnectionWithDefaultRole();
        try {
            assertNull(connection.getScenario());
            final Scenario scenario = connection.createScenario();
            connection.setScenario(scenario);
            Statement statement = connection.createStatement();
            statement.executeQuery(
                "select {[Measures].[Unit Sales]} on 0,\n"
                + "{[Product].Children} on 1\n"
                + "from [Sales]");
        } finally {
            connection.setScenario(null);
        }
    }

    /**
     * Tests that setting a cell's value without an active scenario is illegal.
     */
    @Test
    void testSetCellWithoutScenarioFails(Context<?> context) throws SQLException {
        final Connection connection =
            context.getConnectionWithDefaultRole();
        try {
            assertNull(connection.getScenario());
            final Statement pstmt = connection.createStatement();
            final CellSet result = pstmt.executeQuery(
                "select {[Measures].[Unit Sales]} on 0,\n"
                    + "{[Product].Children} on 1\n"
                    + "from [Sales]"
            );
            final Cell cell = result.getCell(Arrays.asList(0, 1));
            try {
                cell.setValue(connection.getScenario(), 123, AllocationPolicy.EQUAL_ALLOCATION);
                fail("expected error");
            } catch (RuntimeException e) {
                checkThrowable(e, "No active scenario");
            }
        } finally {
            connection.setScenario(null);
        }
    }

    /**
     * Tests that setting a calculated member is illegal.
     */
    @Test
    void testSetCellCalcError(Context<?> context) throws SQLException {
        final Connection connection = context.getConnectionWithDefaultRole();
        connection.setScenario(connection.createScenario());
        Statement pstmt = connection.createStatement();
        CellSet cellSet = pstmt.executeQuery(
            "with member [Measures].[Unit Sales Plus One]\n"
            + "   as ' [Measures].[Unit Sales] + 1 '\n"
            + "select {[Measures].[Unit Sales Plus One]} on 0,\n"
            + "{[Product].Children} on 1\n"
            + "from [Sales]");

        Cell cell = cellSet.getCell(Arrays.asList(0, 1));
        try {
            cell.setValue(connection.getScenario(), 123, AllocationPolicy.EQUAL_ALLOCATION);
            fail("expected exception");
        } catch (RuntimeException e) {
            checkThrowable(
                e,
                "Cannot write to cell: one of the coordinates "
                + "([Measures].[Unit Sales Plus One]) is a calculated member");
        }

        // Calc member on non-measures dimension
        cellSet = pstmt.executeQuery(
            "with member [Product].[FoodDrink]\n"
            + "   as Aggregate({[Product].[Food], [Product].[Drink]})\n"
            + "select {[Measures].[Unit Sales]} on 0,\n"
            + "{[Product].Children, [Product].[FoodDrink]} on 1\n"
            + "from [Sales]");
        // OK to set ([Measures].[Unit Sales], [Product].[Drink])
        cell = cellSet.getCell(Arrays.asList(0, 1));
        cell.setValue(connection.getScenario(), 123, AllocationPolicy.EQUAL_ALLOCATION);
        // Not OK to set ([Measures].[Unit Sales], [Product].[FoodDrink])
        cell = cellSet.getCell(Arrays.asList(0, 3));
        try {
            cell.setValue(connection.getScenario(),123, AllocationPolicy.EQUAL_ALLOCATION);
            fail("expected exception");
        } catch (RuntimeException e) {
            checkThrowable(
                e,
                "Cannot write to cell: one of the coordinates "
                + "([Product].[Product].[FoodDrink]) is a calculated member");
        }
    }

    /**
     * Tests that allocation policies that are not supported give an error.
     */
    @Test
    void testUnsupportedAllocationPolicyFails(Context<?> context) throws SQLException {
        final Connection connection = context.getConnectionWithDefaultRole();
        connection.setScenario(connection.createScenario());
        final Statement pstmt = connection.createStatement();
        final CellSet cellSet = pstmt.executeQuery(
            "select {[Measures].[Unit Sales]} on 0,\n"
                + "{[Product].Children} on 1\n"
                + "from [Sales]");
        final Cell cell = cellSet.getCell(Arrays.asList(0, 1));
        for (AllocationPolicy policy : AllocationPolicy.values()) {
            switch (policy) {
            case EQUAL_ALLOCATION:
            case EQUAL_INCREMENT:
                continue;
            }
            try {
                cell.setValue(connection.getScenario(), 123, policy);
                fail("expected error");
            } catch (RuntimeException e) {
                checkThrowable(
                    e,
                    "Allocation policy " + policy + " is not supported");
            }
        }
        try {
            cell.setValue(connection.getScenario(),123, null);
            fail("expected error");
        } catch (RuntimeException e) {
            checkThrowable(
                e, "Allocation policy must not be null");
        }
    }

    /**
     * Tests setting cells by the "equal increment" allocation policy.
     */
    @Disabled //disabled by reason wrong Scenario with InlineTabl foo
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.ScenarioTestModifier1.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testEqualIncrement(Context<?> context) throws SQLException {
        assertAllocation(context, AllocationPolicy.EQUAL_INCREMENT);
    }

    /**
     * Tests setting cells by the "equal allocation" allocation policy.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.ScenarioTestModifier1.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testEqualAllocation(Context<?> context) throws SQLException {
        assertAllocation(context, AllocationPolicy.EQUAL_ALLOCATION);
    }

    private void assertAllocation(Context<?> context,
        final AllocationPolicy allocationPolicy) throws SQLException
    {
        // TODO: Should not need to explicitly create a scenario. Add element
        //  <Writeback enabled="true"/>
        // to cube definition, and [Scenario] dimension will appear. Also, need
        // more elegant way for users to create dimensions that only contain
        // calculated members.
        /*
        ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
                "Sales",
                "<Dimension name='Scenario' foreignKey='time_id'>\n"
                + "  <Hierarchy primaryKey='time_id' hasAll='true'>\n"
                + "    <InlineTable alias='foo'>\n"
                + "      <ColumnDefs>\n"
                + "        <ColumnDef name='foo' type='Numeric'/>\n"
                + "      </ColumnDefs>\n"
                + "      <Rows/>\n"
                + "    </InlineTable>\n"
                + "    <Level name='Scenario' column='foo'/>\n"
                + "  </Hierarchy>\n"
                + "</Dimension>",
                "<Measure name='Atomic Cell Count' aggregator='count'/>"));
        */

        final Connection connection = context.getConnectionWithDefaultRole();
        connection.setScenario(connection.createScenario());
        final Scenario scenario = connection.getScenario();
        // createScenario() does not register with the catalog; the
        // [Scenario].[{id}] member exists only after an explicit register
        ((ScenarioImpl) scenario).register((RolapCatalog) connection.getCatalog());
        String id = scenario.getId();
        final Statement pstmt = connection.createStatement();
        CellSet cellSet = pstmt.executeQuery(
            "select {[Measures].[Unit Sales]} on 0,\n"
                + "{[Product].Children} on 1\n"
                + "from [Sales]\n"
                + "where [Scenario].["
                + id
                + "]");

        // Update ([Product].[Drink], [Measures].[Unit Sales])
        // from 24,597 to 23,597.
        final Cell cell = cellSet.getCell(Arrays.asList(0, 0));
        cell.setValue(connection.getScenario(),23597, allocationPolicy);

        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on 0,\n"
            + "{[Product].[Drink]} on 1\n"
            + "from [Sales]"
            + "where [Scenario].[" + id + "]").returnsGrid(
            "Axis #0:\n"
            + "{[Scenario].[Scenario].[" + id + "]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Product].[Product].[Drink]}\n"
            + "Row #0: 23,597\n");

        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on 0,\n"
            + "{[Product].Children,\n"
            + " [Product].[Drink].Children,\n"
            + " [Product].[Drink].[Beverages].[Carbonated Beverages].[Soda],\n"
            + " [Product].[Drink].[Beverages].[Carbonated Beverages].[Soda].Children} on 1\n"
            + "from [Sales]"
            + "where [Scenario].[" + id + "]").returnsGrid(
            "Axis #0:\n"
            + "{[Scenario].[Scenario].[" + id + "]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Product].[Product].[Drink]}\n"
            + "{[Product].[Product].[Food]}\n"
            + "{[Product].[Product].[Non-Consumable]}\n"
            + "{[Product].[Product].[Drink].[Alcoholic Beverages]}\n"
            + "{[Product].[Product].[Drink].[Beverages]}\n"
            + "{[Product].[Product].[Drink].[Dairy]}\n"
            + "{[Product].[Product].[Drink].[Beverages].[Carbonated Beverages].[Soda]}\n"
            + "{[Product].[Product].[Drink].[Beverages].[Carbonated Beverages].[Soda].[Excellent]}\n"
            + "{[Product].[Product].[Drink].[Beverages].[Carbonated Beverages].[Soda].[Fabulous]}\n"
            + "{[Product].[Product].[Drink].[Beverages].[Carbonated Beverages].[Soda].[Skinner]}\n"
            + "{[Product].[Product].[Drink].[Beverages].[Carbonated Beverages].[Soda].[Token]}\n"
            + "{[Product].[Product].[Drink].[Beverages].[Carbonated Beverages].[Soda].[Washington]}\n"
            + (allocationPolicy == AllocationPolicy.EQUAL_INCREMENT
                ? "Row #0: 23,597\n"
                  + "Row #1: 191,940\n"
                  + "Row #2: 50,236\n"
                  + "Row #3: 6,560\n"
                  + "Row #4: 13,022\n"
                  + "Row #5: 4,015\n"
                  + "Row #6: 3,268\n"
                  + "Row #7: 708\n"
                  + "Row #8: 606\n"
                  + "Row #9: 629\n"
                  + "Row #10: 705\n"
                  + "Row #11: 620\n"
                : "Row #0: 23,597\n"
                  + "Row #1: 191,940\n"
                  + "Row #2: 50,236\n"
                  + "Row #3: 6,563\n"
                  + "Row #4: 12,990\n"
                  + "Row #5: 4,043\n"
                  + "Row #6: 3,274\n"
                  + "Row #7: 704\n"
                  + "Row #8: 612\n"
                  + "Row #9: 603\n"
                  + "Row #10: 716\n"
                  + "Row #11: 639\n"));

        // For reference here are the original values:
        // Row #0: 24,597
        // Row #1: 191,940
        // Row #2: 50,236
        // Row #3: 6,838
        // Row #4: 13,573
        // Row #5: 4,186
        // Row #6: 3,407
        // Row #7: 738
        // Row #8: 632
        // Row #9: 655
        // Row #10: 735
        // Row #11: 647

        // Create a new scenario, and show that the scenario in the slicer
        // overrides.
        final Scenario scenario2 = connection.createScenario();
        ((ScenarioImpl) scenario2).register((RolapCatalog) connection.getCatalog());
        final String id2 = scenario2.getId();

        // Connection has scenario1,
        // slicer has scenario2,
        // slicer wins.
        String value;
        final Statement stmt = connection.createStatement();
        cellSet =
            stmt.executeQuery(
                "select {[Measures].[Unit Sales]} on 0,\n"
                + "{[Product].Children} on 1\n"
                + "from [Sales]\n"
                + "where [Scenario].["
                + id2
                + "]");
        cellSet.getCell(Arrays.asList(0, 0)).setValue(connection.getScenario(),100, allocationPolicy);

        // With slicer=scenario1, value as per scenario1.
        cellSet =
            stmt.executeQuery(
                "select {[Measures].[Unit Sales]} on 0,\n"
                + "{[Product].Children} on 1\n"
                + "from [Sales]\n"
                + "where [Scenario].["
                + id
                + "]");
        value = cellSet.getCell(Arrays.asList(0, 0)).getFormattedValue();
        assertEquals("23,597", value);

        // With slicer=scenario2, value as per scenario2.
        cellSet =
            stmt.executeQuery(
                "select {[Measures].[Unit Sales]} on 0,\n"
                + "{[Product].Children} on 1\n"
                + "from [Sales]\n"
                + "where [Scenario].["
                + id2
                + "]");
        value = cellSet.getCell(Arrays.asList(0, 0)).getFormattedValue();
        assertEquals("100", value);

        // With no slicer, value as per connection's scenario, scenario1.
        assert connection.getScenario() == scenario;
        cellSet =
            stmt.executeQuery(
                "select {[Measures].[Unit Sales]} on 0,\n"
                + "{[Product].Children} on 1\n"
                + "from [Sales]\n");
        value = cellSet.getCell(Arrays.asList(0, 0)).getFormattedValue();
        assertEquals("23,597", value);

        // Set connection's scenario to null, and we get the unmodified value.
        connection.setScenario(null);
        cellSet =
            stmt.executeQuery(
                "select {[Measures].[Unit Sales]} on 0,\n"
                + "{[Product].Children} on 1\n"
                + "from [Sales]\n");
        value = cellSet.getCell(Arrays.asList(0, 0)).getFormattedValue();
        assertEquals("24,597", value);
    }

    /**
     * Test case for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-815">MONDRIAN-815</a>,
     * "NPE from query if use a scenario and one of the cells is empty/null".
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.ScenarioTestModifier1.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian815(Context<?> context) throws SQLException {
        /*
        ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
                "Sales",
                "<Dimension name='Scenario' foreignKey='time_id'>\n"
                + "  <Hierarchy primaryKey='time_id' hasAll='true'>\n"
                + "    <InlineTable alias='foo'>\n"
                + "      <ColumnDefs>\n"
                + "        <ColumnDef name='foo' type='Numeric'/>\n"
                + "      </ColumnDefs>\n"
                + "      <Rows/>\n"
                + "    </InlineTable>\n"
                + "    <Level name='Scenario' column='foo'/>\n"
                + "  </Hierarchy>\n"
                + "</Dimension>",
                "<Measure name='Atomic Cell Count' aggregator='count'/>"));
         */

        final Connection connection = context.getConnectionWithDefaultRole();
        connection.setScenario(connection.createScenario());
        final Scenario scenario = connection.createScenario();
        connection.setScenario(scenario);
        ((ScenarioImpl) scenario).register((RolapCatalog) connection.getCatalog());
        final String id = scenario.getId();
        final String scenarioUniqueName = "[Scenario].[Scenario].[" + id + "]";
        final Statement pstmt = connection.createStatement();

        // With bug MONDRIAN-815, got an NPE here, because cell (0, 1) has a
        // null value.
        final CellSet cellSet = pstmt.executeQuery(
            "select NON EMPTY [Gender].Members ON COLUMNS,\n"
                + "NON EMPTY Order([Product].[All Products].[Drink].Children,\n"
                + "[Gender].[All Gender].[F], ASC) ON ROWS\n"
                + "from [Sales]\n"
                + "where ([Customers].[All Customers].[USA].[CA].[San Francisco],\n"
                + " [Time].[1997], " + scenarioUniqueName + ")");
        assertEquals(
            "Axis #0:\n"
            + "{[Customers].[Customers].[USA].[CA].[San Francisco], [Time].[Time].[1997], "
            + scenarioUniqueName
            + "}\n"
            + "Axis #1:\n"
            + "{[Gender].[Gender].[All Gender]}\n"
            + "{[Gender].[Gender].[F]}\n"
            + "{[Gender].[Gender].[M]}\n"
            + "Axis #2:\n"
            + "{[Product].[Product].[Drink].[Beverages]}\n"
            + "{[Product].[Product].[Drink].[Alcoholic Beverages]}\n"
            + "Row #0: 2\n"
            + "Row #0: \n"
            + "Row #0: 2\n"
            + "Row #1: 4\n"
            + "Row #1: 2\n"
            + "Row #1: 2\n",
            toString(cellSet));
        cellSet.getCell(Arrays.asList(0, 1))
            .setValue(scenario, 10, AllocationPolicy.EQUAL_ALLOCATION);
        cellSet.getCell(Arrays.asList(1, 0))
            .setValue(scenario, 999, AllocationPolicy.EQUAL_ALLOCATION);
        MdxAssert.assertThatQuery(connection,
            "select NON EMPTY [Gender].Members ON COLUMNS,\n"
                + "NON EMPTY Order([Product].[All Products].[Drink].Children,\n"
                + "[Gender].[All Gender].[F], ASC) ON ROWS\n"
                + "from [Sales]\n"
                + "where ([Customers].[All Customers].[USA].[CA].[San Francisco],\n"
                + " [Time].[1997], " + scenarioUniqueName + ")")
            .returnsGrid(
                "Axis #0:\n"
                + "{[Customers].[Customers].[USA].[CA].[San Francisco], [Time].[Time].[1997], "
                + scenarioUniqueName
                + "}\n"
                + "Axis #1:\n"
                + "{[Gender].[Gender].[All Gender]}\n"
                + "{[Gender].[Gender].[F]}\n"
                + "{[Gender].[Gender].[M]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages]}\n"
                + "{[Product].[Product].[Drink].[Beverages]}\n"
                + "Row #0: 10\n"
                + "Row #0: 5\n"
                + "Row #0: 5\n"
                + "Row #1: 1,001\n"
                + "Row #1: 999\n"
                + "Row #1: 2\n");
    }

    @Test
    void testScenarioPropertyBug1496(Context<?> context) {
        // looking up the $scenario property for a non ScenarioCalc member
        // causes class cast exception
        // http://jira.pentaho.com/browse/MONDRIAN-1496
        Result result = executeQuery(context.getConnectionWithDefaultRole(),
            "select {[Gender].[Gender].members} on columns from Sales");

        // non calc member, should return null
        Object o = result.getAxes()[0].getPositions().get(0).get(0)
            .getPropertyValue("$scenario");
        assertEquals(null, o);

        result = executeQuery(context.getConnectionWithDefaultRole(),
            "with member gender.cal as '1' "
            + "select {[Gender].cal} on 0 from Sales");
        // calc member, should return null
        o = result.getAxes()[0].getPositions().get(0).get(0)
            .getPropertyValue("$scenario");
        assertEquals(null, o);
    }


    // TODO: test whether it is valid for two connections to have the same
    // active scenario

    // TODO: test that assigning a string to a numeric cell succeeds only if
    // the string contains a valid number

    // TODO: test that assigning a double to an integer cell succeeds; and some
    // other data types

    // TODO: test that EQUAL_ALLOCATION assigns to (a) cells that were
    // already empty, (b) cells that were null, (c) cells that are not visible
    // to the caller. I'm not sure that (c) works right now.

    private static void checkThrowable(Throwable throwable, String pattern) {
        if (throwable == null) {
            fail("query did not yield an exception");
        }
        String stackTrace = getStackTrace(throwable);
        if (stackTrace.indexOf(pattern) < 0) {
            fail(
                "query's error does not match pattern '" + pattern
                + "'; error is [" + stackTrace + "]");
        }
    }

    /**
     * Converts a {@link Throwable} to a stack trace.
     */
    private static String getStackTrace(Throwable e) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(out));
        return new String(out.toByteArray());
    }

    private static String toString(CellSet cellSet) {
        final StringWriter sw = new StringWriter();
        new TraditionalCellSetFormatter().format(
            cellSet,
            new PrintWriter(sw));
        return sw.toString();
    }
}
