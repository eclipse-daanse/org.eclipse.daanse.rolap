/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2018 Hitachi Vantara..  All rights reserved.
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
package org.eclipse.daanse.rolap.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.common.RolapStar.Column;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.mapping.model.Query;
import org.eclipse.daanse.rolap.mapping.model.RelationalQuery;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link RolapStar}.
 *
 * @author pedrovale
 */
class RolapStarTest {

    static class RolapStarForTests extends RolapStar {
        public RolapStarForTests(
            final RolapCatalog schema,
            final Context<?> context,
            final RelationalQuery fact)
        {
            super(schema, context, fact);
        }

        public Query cloneRelationForTests(
            RelationalQuery rel,
            String possibleName)
        {
            return cloneRelation(rel, possibleName);
        }
    }

    RolapStar getRolapStar(Connection con, String starName) {
        RolapCube cube =
            (RolapCube) con.getCatalog().lookupCube(starName).orElseThrow();
        return cube.getStar();
    }

    RolapStarForTests getStar(Connection connection, String starName) {
        RolapStar rs =  getRolapStar(connection, starName);

        return new RolapStarForTests(
            rs.getCatalog(),
            rs.getContext(),
            rs.getFactTable().getRelation());
    }

   //Below there are tests for mondrian.rolap.RolapStar.ColumnComparator
   @Test
   void testTwoColumnsWithDifferentNamesNotEquals() {
     RolapStar.ColumnComparator colComparator =
         RolapStar.ColumnComparator.instance;
     Column column1 = getColumnMock("Column1", "Table1");
     Column column2 = getColumnMock("Column2", "Table1");
     assertNotSame(column1, column2);
     assertEquals(-1, colComparator.compare(column1, column2));
   }

   @Test
   void testTwoColumnsWithEqualsNamesButDifferentTablesNotEquals() {
     RolapStar.ColumnComparator colComparator =
         RolapStar.ColumnComparator.instance;
     Column column1 = getColumnMock("Column1", "Table1");
     Column column2 = getColumnMock("Column1", "Table2");
     assertNotSame(column1, column2);
     assertEquals(-1, colComparator.compare(column1, column2));
   }

   @Test
   void testTwoColumnsEquals() {
     RolapStar.ColumnComparator colComparator =
         RolapStar.ColumnComparator.instance;
     Column column1 = getColumnMock("Column1", "Table1");
     Column column2 = getColumnMock("Column1", "Table1");
     assertNotSame(column1, column2);
     assertEquals(0, colComparator.compare(column1, column2));
   }

   private static Column getColumnMock(
       String columnName,
       String tableName)
   {
     Column colMock = mock(Column.class);
     RolapStar.Table tableMock = mock(RolapStar.Table.class);
     when(colMock.getName()).thenReturn(columnName);
     when(colMock.getTable()).thenReturn(tableMock);
     when(tableMock.getAlias()).thenReturn(tableName);
    return colMock;
   }
}
