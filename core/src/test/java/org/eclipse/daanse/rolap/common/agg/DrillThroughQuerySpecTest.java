/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
 * All Rights Reserved.
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

package org.eclipse.daanse.rolap.common.agg;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class DrillThroughQuerySpecTest {

  private static DrillThroughCellRequest requestMock;
  private static StarPredicate starPredicateMock;
  private static QueryRecorder sqlQueryMock;
  private static DrillThroughQuerySpec drillThroughQuerySpec;
  private static RolapStar.Column includedColumn;
  private static RolapStar.Column excludedColumn;

    @BeforeEach void beforeAll() throws Exception {

    requestMock = mock(DrillThroughCellRequest.class);
    starPredicateMock = mock(StarPredicate.class);
    sqlQueryMock = mock(QueryRecorder.class);
    RolapStar.Measure measureMock = mock(RolapStar.Measure.class);
    includedColumn = mock(RolapStar.Column.class);
    excludedColumn = mock(RolapStar.Column.class);
    RolapStar starMock = mock(RolapStar.class);

    when(requestMock.includeInSelect(any(RolapStar.Column.class)))
      .thenReturn(true);
    when(requestMock.getMeasure()).thenReturn(measureMock);
    when(requestMock.getConstrainedColumns())
      .thenReturn(new RolapStar.Column[0]);
    when(measureMock.getStar()).thenReturn(starMock);
    when(starMock.getDialect()).thenReturn(mock(Dialect.class));
    when(starPredicateMock.getConstrainedColumnList())
      .thenReturn(Collections.singletonList(includedColumn));
    when(includedColumn.getTable()).thenReturn(mock(RolapStar.Table.class));
    when(excludedColumn.getTable()).thenReturn(mock(RolapStar.Table.class));
    drillThroughQuerySpec =
      new DrillThroughQuerySpec
        (requestMock, starPredicateMock, new ArrayList<OlapElement> (), false);
  }

  @Test
  @Disabled("Obsolete: DrillThroughQuerySpec.extraPredicates no longer adds the drill-through SELECT "
      + "columns (that moved to the builder AggregateSqlMapper.drillThrough); the residual toSql "
      + "fallback that let a bare StarPredicate mock pass is removed (translator is total). TCK-covered.")
  void emptyColumns() {
    List<RolapStar.Column> columns = Collections.emptyList();
    when(starPredicateMock.getConstrainedColumnList())
      .thenReturn(columns);
    drillThroughQuerySpec.extraPredicates(sqlQueryMock);
    verify(sqlQueryMock, times(0))
      .addSelect(anyString(), any(BestFitColumnType.class), anyString());
  }

  @Test
  @Disabled("Obsolete: DrillThroughQuerySpec.extraPredicates no longer adds the drill-through SELECT "
      + "columns (that moved to the builder AggregateSqlMapper.drillThrough); covered by the TCK.")
  void oneColumnExists() {
    drillThroughQuerySpec.extraPredicates(sqlQueryMock);
    verify(sqlQueryMock, times(1))
      .addSelect(isNull(), isNull(), anyString());
  }

  @Test
  @Disabled("Obsolete: drill-through SELECT columns moved to AggregateSqlMapper.drillThrough; TCK-covered.")
  void twoColumnsExist() {
    when(starPredicateMock.getConstrainedColumnList())
      .thenReturn(Arrays.asList(includedColumn, excludedColumn));
    drillThroughQuerySpec.extraPredicates(sqlQueryMock);
    verify(sqlQueryMock, times(2))
      .addSelect(isNull(), isNull(), anyString());
  }

  @Test
  @Disabled("Obsolete: DrillThroughQuerySpec.extraPredicates no longer adds the drill-through SELECT "
      + "columns (that moved to the builder AggregateSqlMapper.drillThrough); the residual toSql "
      + "fallback that let a bare StarPredicate mock pass is removed (translator is total). TCK-covered.")
  void columnsNotIncludedInSelect() {
    when(requestMock.includeInSelect(includedColumn)).thenReturn(false);
    drillThroughQuerySpec.extraPredicates(sqlQueryMock);
    verify(sqlQueryMock, times(0))
      .addSelect(anyString(), any(BestFitColumnType.class), anyString());

    when(starPredicateMock.getConstrainedColumnList())
      .thenReturn(Arrays.asList(includedColumn, excludedColumn));
    verify(sqlQueryMock, times(0))
      .addSelect(anyString(), any(BestFitColumnType.class), anyString());
  }

  @Test
  @Disabled("Obsolete: drill-through SELECT columns moved to AggregateSqlMapper.drillThrough; TCK-covered.")
  void columnsPartiallyIncludedInSelect() {
    when(requestMock.includeInSelect(excludedColumn)).thenReturn(false);
    when(requestMock.includeInSelect(includedColumn)).thenReturn(true);
    when(starPredicateMock.getConstrainedColumnList())
      .thenReturn(Arrays.asList(includedColumn, excludedColumn));

    drillThroughQuerySpec.extraPredicates(sqlQueryMock);
    verify(sqlQueryMock, times(1))
      .addSelect(isNull(), isNull(), anyString());
  }

  // ---- translateOrResidual: the segment path's residual fallback, mirrored for drill-through ----

  /** A translatable shape takes the plain StarPredicateTranslator path. */
  @Test
  void translateOrResidualKeepsTranslatorPathForValuePredicate() {
    RolapStar.Table table = mock(RolapStar.Table.class);
    when(table.getAlias()).thenReturn("schul_jahr");
    RolapStar.Column col = mock(RolapStar.Column.class);
    when(col.getTable()).thenReturn(table);
    when(col.getExpression())
      .thenReturn(new org.eclipse.daanse.rolap.element.RolapColumn("schul_jahr", "id"));
    when(col.getDatatype())
      .thenReturn(org.eclipse.daanse.jdbc.db.api.type.Datatype.INTEGER);
    ValueColumnPredicate value = mock(ValueColumnPredicate.class);
    when(value.getConstrainedColumn()).thenReturn(col);
    when(value.getValue()).thenReturn(4);

    org.eclipse.daanse.sql.statement.api.expression.Predicate p =
      DrillThroughQuerySpec.translateOrResidual(value, mock(Dialect.class));

    org.assertj.core.api.Assertions.assertThat(p).isEqualTo(
      org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toPredicate(value));
  }

  /** An always-true predicate adds no restriction — returns null. */
  @Test
  void translateOrResidualSkipsAlwaysTruePredicate() {
    org.assertj.core.api.Assertions.assertThat(
        DrillThroughQuerySpec.translateOrResidual(
            org.eclipse.daanse.rolap.common.agg.LiteralStarPredicate.TRUE, mock(Dialect.class)))
      .isNull();
  }
}
