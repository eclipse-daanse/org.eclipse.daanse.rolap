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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.daanse.rolap.mapping.api.model.InlineTableQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.RelationalQueryMapping;
import org.eclipse.daanse.rolap.mapping.pojo.PhysicalTableMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.SqlStatementMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.TableQueryMappingImpl;
import org.junit.jupiter.api.Test;

class RolapUtilTest {

  private static final String FILTER_QUERY =
      "`TableAlias`.`promotion_id` = 112";
  private static final String FILTER_DIALECT = "mysql";
  private static final String TABLE_ALIAS = "TableAlias";
  private static final String RELATION_ALIAS = "RelationAlias";
  private static final String FACT_NAME = "order_fact";
  private TableQueryMappingImpl fact;

  @Test
  void testMakeRolapStarKeyUnmodifiable() throws Exception {
    try {
      //fact = SchemaUtil.parse(getFactTableWithSQLFilter(), TableQueryMappingImpl.class);
      fact = TableQueryMappingImpl.builder()
    		  .withTable(((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("getFactTable())")).build())
    		  .withAlias("TableAlias")
    		  .withSqlWhereExpression(SqlStatementMappingImpl.builder()
    				  .withDialects(List.of("mysql"))
    				  .withSql("`TableAlias`.`promotion_id` = 112")
    				  .build())
    		  .build();
      List<String> polapStarKey = RolapStarRegistry.makeRolapStarKey(FACT_NAME);
      assertNotNull(polapStarKey);
      polapStarKey.add("OneMore");
      fail(
          "It should not be allowed to change the rolap star key."
          + "UnsupportedOperationException expected but was not  been appeared.");
      } catch (UnsupportedOperationException e) {
      assertTrue(true);
    }
  }

  @Test
  void testMakeRolapStarKey_ByFactTableName() throws Exception {
    //fact = SchemaUtil.parse(getFactTableWithSQLFilter(), TableQueryMappingImpl.class);
    fact = TableQueryMappingImpl.builder()
    		.withTable(((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("getFactTable())")).build())
    		  .withAlias("TableAlias")
    		  .withSqlWhereExpression(SqlStatementMappingImpl.builder()
    				  .withDialects(List.of("mysql"))
    				  .withSql("`TableAlias`.`promotion_id` = 112")
    				  .build())
    		  .build();
    List<String> polapStarKey = RolapStarRegistry.makeRolapStarKey(FACT_NAME);
    assertNotNull(polapStarKey);
    assertEquals(1, polapStarKey.size());
    assertEquals(FACT_NAME, polapStarKey.get(0));
  }

  @Test
  void testMakeRolapStarKey_FactTableWithSQLFilter() throws Exception {
    //fact = SchemaUtil.parse(getFactTableWithSQLFilter(), TableQueryMappingImpl.class);
    fact = TableQueryMappingImpl.builder()
          .withTable(((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("getFactTable())")).build())
  		  .withAlias("TableAlias")
  		  .withSqlWhereExpression(SqlStatementMappingImpl.builder()
  				  .withDialects(List.of("mysql"))
  				  .withSql("`TableAlias`.`promotion_id` = 112")
  				  .build())
  		  .build();
    List<String> polapStarKey = RolapUtil.makeRolapStarKey(fact);
    assertNotNull(polapStarKey);
    assertEquals(3, polapStarKey.size());
    assertEquals(TABLE_ALIAS, polapStarKey.get(0));
    assertEquals(FILTER_DIALECT, polapStarKey.get(1));
    assertEquals(FILTER_QUERY, polapStarKey.get(2));
  }

  @Test
  void testMakeRolapStarKey_FactTableWithEmptyFilter()
      throws Exception {
    //fact = SchemaUtil.parse(getFactTableWithEmptySQLFilter(), TableQueryMappingImpl.class);
    fact = TableQueryMappingImpl.builder()
          .withTable(((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("getFactTable())")).build())
  		  .withAlias("TableAlias")
  		  .withSqlWhereExpression(SqlStatementMappingImpl.builder()
  				  .withDialects(List.of("mysql"))
  				  .build())
  		  .build();

    List<String> polapStarKey = RolapUtil.makeRolapStarKey(fact);
    assertNotNull(polapStarKey);
    assertEquals(1, polapStarKey.size());
    assertEquals(TABLE_ALIAS, polapStarKey.get(0));
  }

  @Test
  void testMakeRolapStarKey_FactTableWithoutSQLFilter()
      throws Exception {
    //fact = SchemaUtil.parse(getFactTableWithoutSQLFilter(), TableQueryMappingImpl.class);
    fact = TableQueryMappingImpl.builder()
              .withTable(((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("getFactTable())")).build())
    		  .withAlias("TableAlias")
    		  .build();
    List<String> polapStarKey = RolapUtil.makeRolapStarKey(fact);
    assertNotNull(polapStarKey);
    assertEquals(1, polapStarKey.size());
    assertEquals(TABLE_ALIAS, polapStarKey.get(0));
  }

  @Test
  void testMakeRolapStarKey_FactRelation() throws Exception {
    List<String> polapStarKey = RolapUtil.makeRolapStarKey(
        getFactRelationMock());
    assertNotNull(polapStarKey);
    assertEquals(1, polapStarKey.size());
    assertEquals(RELATION_ALIAS, polapStarKey.get(0));
  }

  private static RelationalQueryMapping getFactRelationMock() throws Exception {
    RelationalQueryMapping factMock = mock(InlineTableQueryMapping.class);
    when(factMock.getAlias()).thenReturn(RELATION_ALIAS);
    return factMock;
  }

}
