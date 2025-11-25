/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2018 Hitachi Vantara
 * All Rights Reserved.
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
 *   Stefan Bischof (bipolis.org) - initial
 */

package org.eclipse.daanse.rolap.common.agg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.util.ByteString;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.StarPredicate;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GroupingSetsListTest {

  private static List<GroupingSet> groupingSetList;
  private static GroupingSetsList testObject;
  private static BitKey bitkeyMock = mock(BitKey.class);

  private static RolapStar starMock;
  private static RolapStar.Measure measureMock = mock(RolapStar.Measure.class);

  private static RolapStar.Column col1, col2, col3, col4;
  private static RolapStar.Column[] columns;

    @BeforeAll static void beforeAll() throws Exception {
    //Get star mock - just to allow creating Segment
    starMock = getStarMock();
    //Columns mocks
    col1 = getColumnMock("LV1_ID", "Table1");
    col2 = getColumnMock("LV1_ID", "Table2");
    col3 = getColumnMock("LV1_ID", "Table3");
    col4 = getColumnMock("LV1_ID", "Table4");
    columns = new RolapStar.Column[] {col1, col2, col3, col4 };

    groupingSetList = createGroupingSetList();
  }

  @Test
  void newGroupingSetsListRollupColumnsFoundCorrectly() {
    testObject = new GroupingSetsList(groupingSetList);
      assertThat(testObject).isNotNull();
      assertThat(testObject.getGroupingSets()).isSameAs(groupingSetList);
      assertThat(testObject.useGroupingSets()).isTrue();
      // verify count of grouping sets for columns
      assertThat(testObject.getGroupingSetsColumns().size()).isEqualTo(expectedGroupingSetsColumns().size());
    // verify columns in each of groups
    for (int i = 0; i < expectedGroupingSetsColumns().size(); i++) {
        assertThat(testObject.getGroupingSetsColumns().get(i).length).isEqualTo(expectedGroupingSetsColumns().get(i).length);
      for (int j = 0; j < expectedGroupingSetsColumns().get(i).length; j++) {
          assertThat(testObject.getGroupingSetsColumns().get(i)[j]).isEqualTo(expectedGroupingSetsColumns().get(i)[j]);
      }
    }
      assertThat(testObject.getRollupColumns().size()).isEqualTo(2);
      assertThat(testObject.getRollupColumns()).isEqualTo(Arrays.asList(col3, col4));
      assertThat(testObject.getGroupingBitKeyIndex()).isEqualTo(5);
  }

  private List<RolapStar.Column[]> expectedGroupingSetsColumns() {
    List<RolapStar.Column[]> ls = new ArrayList<>();
    ls.add(columns);
    ls.add(new RolapStar.Column[] {col1, col2, col3});
    ls.add(new RolapStar.Column[] {col1, col2});
    return ls;
  }
  private static List<GroupingSet> createGroupingSetList() {
    List<GroupingSet> grList = new ArrayList<>();
    //We have 3 grouping sets for testing
    //detailed grouping set - all columns used
    grList.add(createGroupingSet(4));
    //rolled-up grouping sets
    grList.add(createGroupingSet(3));
    grList.add(createGroupingSet(2));
    return grList;
  }
  private static GroupingSet createGroupingSet(int columnCount) {
    StarColumnPredicate[] predicates = new StarColumnPredicate[] {};
    List<StarPredicate> compPredicates = new ArrayList<>();
   RolapStar.Column[] c = new RolapStar.Column[columnCount];
    //Every grouping set will contain different count of columns
    for (int i = 0; i < columnCount; i++) {
      c[i] = columns[i];
    }
    Segment segment =
        new Segment(
            starMock, bitkeyMock, c, measureMock,
            predicates, null, compPredicates);
    GroupingSet grSet =
        new GroupingSet(
            Arrays.asList(segment), bitkeyMock,
            bitkeyMock, predicates, c);
    return grSet;
  }

  private static RolapStar.Column getColumnMock(
      String columnName,
      String tableName)
  {
    RolapStar.Column colMock = mock(RolapStar.Column.class);
    RolapStar.Table tableMock = mock(RolapStar.Table.class);
    when(colMock.getName()).thenReturn(columnName);
    when(colMock.getTable()).thenReturn(tableMock);
    when(tableMock.getAlias()).thenReturn(tableName);
   return colMock;
  }

  private static RolapStar getStarMock() {
    RolapStar mock = mock(RolapStar.class);

    RolapStar.Table tableMock = mock(RolapStar.Table.class);
    RolapCatalog schemaMock = mock(RolapCatalog.class);
    ByteString md5 = new ByteString("test schema".getBytes());
    when(mock.getCatalog()).thenReturn(schemaMock);
    when(schemaMock.getChecksum()).thenReturn(md5);
    when(mock.getFactTable()).thenReturn(tableMock);
    when(tableMock.getAlias()).thenReturn("Table Mock");
    return mock;
  }

}
