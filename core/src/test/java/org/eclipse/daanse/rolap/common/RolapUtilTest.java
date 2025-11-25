/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.daanse.rolap.mapping.model.PhysicalTable;
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;
import org.eclipse.daanse.rolap.mapping.model.SqlStatement;
import org.eclipse.daanse.rolap.mapping.model.TableQuery;
import org.junit.jupiter.api.Test;

class RolapUtilTest {

  private static final String FILTER_QUERY =
      "`TableAlias`.`promotion_id` = 112";
  private static final String FILTER_DIALECT = "mysql";
  private static final String TABLE_ALIAS = "TableAlias";
  private static final String RELATION_ALIAS = "RelationAlias";
  private static final String FACT_NAME = "order_fact";
  private TableQuery fact;

  @Test
  void makeRolapStarKeyUnmodifiable() throws Exception {
      assertThatThrownBy(() -> {
          PhysicalTable t = RolapMappingFactory.eINSTANCE.createPhysicalTable();
          t.setName("getFactTable())");
          SqlStatement sqlStatement = RolapMappingFactory.eINSTANCE.createSqlStatement();
          sqlStatement.getDialects().add("mysql");
          sqlStatement.setSql("`TableAlias`.`promotion_id` = 112");
          fact = RolapMappingFactory.eINSTANCE.createTableQuery();
          fact.setTable(t);
          fact.setAlias("TableAlias");
          fact.setSqlWhereExpression(sqlStatement);
          List<String> polapStarKey = RolapStarRegistry.makeRolapStarKey(FACT_NAME);
          assertThat(polapStarKey).isNotNull();
          polapStarKey.add("OneMore");
      }).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void makeRolapStarKeyByFactTableName() throws Exception {
    //fact = SchemaUtil.parse(getFactTableWithSQLFilter(), TableQueryMappingImpl.class);
      PhysicalTable t = RolapMappingFactory.eINSTANCE.createPhysicalTable();
      t.setName("getFactTable())");
      SqlStatement sqlStatement = RolapMappingFactory.eINSTANCE.createSqlStatement();
      sqlStatement.getDialects().add("mysql");
      sqlStatement.setSql("`TableAlias`.`promotion_id` = 112");
      
      fact = RolapMappingFactory.eINSTANCE.createTableQuery();
      fact.setTable(t);
      fact.setAlias("TableAlias");
      fact.setSqlWhereExpression(sqlStatement);

    List<String> polapStarKey = RolapStarRegistry.makeRolapStarKey(FACT_NAME);
      assertThat(polapStarKey).isNotNull();
      assertThat(polapStarKey.size()).isEqualTo(1);
      assertThat(polapStarKey.get(0)).isEqualTo(FACT_NAME);
  }

  @Test
  void makeRolapStarKeyFactTableWithSQLFilter() throws Exception {
    //fact = SchemaUtil.parse(getFactTableWithSQLFilter(), TableQueryMappingImpl.class);
      PhysicalTable t = RolapMappingFactory.eINSTANCE.createPhysicalTable();
      t.setName("getFactTable())");
      SqlStatement sqlStatement = RolapMappingFactory.eINSTANCE.createSqlStatement();
      sqlStatement.getDialects().add("mysql");
      sqlStatement.setSql("`TableAlias`.`promotion_id` = 112");
      
      fact = RolapMappingFactory.eINSTANCE.createTableQuery();
      fact.setTable(t);
      fact.setAlias("TableAlias");
      fact.setSqlWhereExpression(sqlStatement);

    List<String> polapStarKey = RolapUtil.makeRolapStarKey(fact);
      assertThat(polapStarKey).isNotNull();
      assertThat(polapStarKey.size()).isEqualTo(3);
      assertThat(polapStarKey.get(0)).isEqualTo(TABLE_ALIAS);
      assertThat(polapStarKey.get(1)).isEqualTo(FILTER_DIALECT);
      assertThat(polapStarKey.get(2)).isEqualTo(FILTER_QUERY);
  }

  @Test
  void makeRolapStarKeyFactTableWithEmptyFilter()
      throws Exception {
    //fact = SchemaUtil.parse(getFactTableWithEmptySQLFilter(), TableQueryMappingImpl.class);
      PhysicalTable t = RolapMappingFactory.eINSTANCE.createPhysicalTable();
      t.setName("getFactTable())");
      SqlStatement sqlStatement = RolapMappingFactory.eINSTANCE.createSqlStatement();
      sqlStatement.getDialects().add("mysql");
      
      fact = RolapMappingFactory.eINSTANCE.createTableQuery();
      fact.setTable(t);
      fact.setAlias("TableAlias");
      fact.setSqlWhereExpression(sqlStatement);


    List<String> polapStarKey = RolapUtil.makeRolapStarKey(fact);
      assertThat(polapStarKey).isNotNull();
      assertThat(polapStarKey.size()).isEqualTo(1);
      assertThat(polapStarKey.get(0)).isEqualTo(TABLE_ALIAS);
  }

  @Test
  void makeRolapStarKeyFactTableWithoutSQLFilter()
      throws Exception {
    //fact = SchemaUtil.parse(getFactTableWithoutSQLFilter(), TableQueryMappingImpl.class);
      PhysicalTable t = RolapMappingFactory.eINSTANCE.createPhysicalTable();
      t.setName("getFactTable())");
      
      fact = RolapMappingFactory.eINSTANCE.createTableQuery();
      fact.setTable(t);
      fact.setAlias("TableAlias");

    List<String> polapStarKey = RolapUtil.makeRolapStarKey(fact);
      assertThat(polapStarKey).isNotNull();
      assertThat(polapStarKey.size()).isEqualTo(1);
      assertThat(polapStarKey.get(0)).isEqualTo(TABLE_ALIAS);
  }

  @Test
  void makeRolapStarKeyFactRelation() throws Exception {
    List<String> polapStarKey = RolapUtil.makeRolapStarKey(
        getFactRelationMock());
      assertThat(polapStarKey).isNotNull();
      assertThat(polapStarKey.size()).isEqualTo(1);
      assertThat(polapStarKey.get(0)).isEqualTo(RELATION_ALIAS);
  }

  private static org.eclipse.daanse.rolap.mapping.model.RelationalQuery getFactRelationMock() throws Exception {
	  org.eclipse.daanse.rolap.mapping.model.RelationalQuery factMock = mock(org.eclipse.daanse.rolap.mapping.model.InlineTableQuery.class);
    when(factMock.getAlias()).thenReturn(RELATION_ALIAS);
    return factMock;
  }

}
