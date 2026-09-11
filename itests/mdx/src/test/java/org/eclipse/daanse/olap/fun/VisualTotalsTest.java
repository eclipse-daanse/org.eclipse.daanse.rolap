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

package org.eclipse.daanse.olap.fun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.CellSet;
import org.eclipse.daanse.olap.api.result.CellSetAxis;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.impl.CellImpl;
import org.eclipse.daanse.olap.impl.CoordinateIterator;
import org.eclipse.daanse.rolap.function.def.visualtotals.VisualTotalsFunDef;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.MdxAssert;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * <code>VisualTotalsTest</code> tests the internal functions defined in
 * {@link VisualTotalsFunDef}. Right now, only tests substitute().
 *
 * @author efine
 */
@RolapContextTest(FoodmartTestInstance.class)
class VisualTotalsTest {

    /**
     * Test case for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-925">
     * MONDRIAN-925, "VisualTotals + drillthrough throws Exception"</a>.
     *
     * @throws java.sql.SQLException on error
     */
    @Test
    void testDrillthroughVisualTotal(Context<?> foodMartContext) throws SQLException {
        Connection conn = foodMartContext.getConnectionWithDefaultRole();
        CellSet cellSet =
    		executeQueryWithCellSetResult(conn,
                "select {[Measures].[Unit Sales]} on columns, "
                + "{VisualTotals("
                + "    {[Product].[Food].[Baked Goods].[Bread],"
                + "     [Product].[Food].[Baked Goods].[Bread].[Bagels],"
                + "     [Product].[Food].[Baked Goods].[Bread].[Muffins]},"
                + "     \"**Subtotal - *\")} on rows "
                + "from [Sales]");
        List<Position> positions = cellSet.getAxes().get(1).getPositions();
        Cell cell;
        ResultSet resultSet;
        Member member;

        cell = cellSet.getCell(Arrays.asList(0, 0));
        member = positions.get(0).getMembers().get(0);
        assertEquals("*Subtotal - Bread", member.getCaption());
        resultSet = ((CellImpl)cell).drillThrough();
        assertNull(resultSet);

        cell = cellSet.getCell(Arrays.asList(0, 1));
        member = positions.get(1).getMembers().get(0);
        assertEquals("Bagels", member.getName());
        resultSet = ((CellImpl)cell).drillThrough();
        assertNotNull(resultSet);
        resultSet.close();
    }

    /**
     * Test case for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-1279">
     * MONDRIAN-1279, "VisualTotals name only applies to member name not
     * caption"</a>.
     *
     * @throws java.sql.SQLException on error
     */
    @Test
    void testVisualTotalCaptionBug(Context<?> foodMartContext) throws SQLException {
        CellSet cellSet =
    		executeQueryWithCellSetResult(foodMartContext.getConnectionWithDefaultRole(),
                "select {[Measures].[Unit Sales]} on columns, "
                + "VisualTotals("
                + "    {[Product].[Food].[Baked Goods].[Bread],"
                + "     [Product].[Food].[Baked Goods].[Bread].[Bagels],"
                + "     [Product].[Food].[Baked Goods].[Bread].[Muffins]},"
                + "     \"**Subtotal - *\") on rows "
                + "from [Sales]");
        List<Position> positions = cellSet.getAxes().get(1).getPositions();
        Cell cell;
        Member member;

        cell = cellSet.getCell(Arrays.asList(0, 0));
        member = positions.get(0).getMembers().get(0);
        assertEquals("Bread", member.getName());
        assertEquals("*Subtotal - Bread", member.getCaption());
    }

    /**
     * Test case for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-939">
     * MONDRIAN-939, "VisualTotals returning incorrect values with aggregate members"</a>.
     *
     * @throws java.sql.SQLException on error
     */
    @Test
    void testVisualTotalsAggregatedMemberBug(Context<?> foodMartContext) throws SQLException {
        MdxAssert.assertThatQuery(foodMartContext.getConnectionWithDefaultRole(),
                " with  member [Gender].[YTD] as 'AGGREGATE(YTD(),[Gender].[M])'"
            	+ "  select "
            	+ " {[Time].[1997],"
            	+ " [Time].[1997].[Q1],[Time].[1997].[Q2],[Time].[1997].[Q3],[Time].[1997].[Q4]} ON COLUMNS, "
            	+ " {[Gender].[M],[Gender].[YTD]} ON ROWS"
            	+ " FROM [Sales]")
            .returnsGrid(
        	     "Axis #0:\n"
        	     + "{}\n"
        	     + "Axis #1:\n"
        	     + "{[Time].[Time].[1997]}\n"
        	     + "{[Time].[Time].[1997].[Q1]}\n"
        	     + "{[Time].[Time].[1997].[Q2]}\n"
        	     + "{[Time].[Time].[1997].[Q3]}\n"
        	     + "{[Time].[Time].[1997].[Q4]}\n"
        	     + "Axis #2:\n"
        	     + "{[Gender].[Gender].[M]}\n"
        	     + "{[Gender].[Gender].[YTD]}\n"
        	     + "Row #0: 135,215\n"
        	     + "Row #0: 33,381\n"
        	     + "Row #0: 31,618\n"
        	     + "Row #0: 33,249\n"
        	     + "Row #0: 36,967\n"
        	     + "Row #1: 135,215\n"
        	     + "Row #1: 33,381\n"
        	     + "Row #1: 64,999\n"
        	     + "Row #1: 98,248\n"
        	     + "Row #1: 135,215\n");
    }

    private static CellSet executeQueryWithCellSetResult(Connection connection, String queryString) throws SQLException {

        assertThat(connection).isNotNull();
        assertThat(queryString).isNotNull().isNotBlank();

        Statement stmt = connection.createStatement();

        assertThat(stmt).isNotNull();

        final CellSet cellSet = stmt.executeQuery(queryString);

        assertThat(cellSet).isNotNull();

        // If we're deep testing, check that we never return the dummy null
        // value when cells are null. TestExpDependencies isn't the perfect
        // switch to enable this, but it will do for now.
        //TODO: activate this for all tests
        if (connection.getContext().getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class) == 1) {
            assertCellSetValid(cellSet);
        }
        return cellSet;
    }

    /**
     * Checks that a {@link CellSet} is valid.
     */
    private static void assertCellSetValid(CellSet cellSet) {
        for (Cell cell : cellIter(cellSet)) {
            // A NULL cell surfaces as Java null, and only then:
            // (value == null) == isNull().
            if (cell.getValue() == null) {
                assertTrue(cell.isNull());
            } else {
                assertFalse(cell.isNull());
            }
        }
    }

    /**
     * Returns an iterator over cells in an olap4j cell set.
     */
    private static Iterable<Cell> cellIter(final CellSet cellSet) {
        return new Iterable<>() {
            @Override
            public Iterator<Cell> iterator() {
                int[] axisDimensions = new int[cellSet.getAxes().size()];
                int k = 0;
                for (CellSetAxis axis : cellSet.getAxes()) {
                    axisDimensions[k++] = axis.getPositions().size();
                }
                final CoordinateIterator coordIter = new CoordinateIterator(axisDimensions);
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return coordIter.hasNext();
                    }

                    @Override
                    public Cell next() {
                        final int[] ints = coordIter.next();
                        final List<Integer> list = new AbstractList<>() {
                            @Override
                            public Integer get(int index) {
                                return ints[index];
                            }

                            @Override
                            public int size() {
                                return ints.length;
                            }
                        };
                        return cellSet.getCell(list);
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }
}
