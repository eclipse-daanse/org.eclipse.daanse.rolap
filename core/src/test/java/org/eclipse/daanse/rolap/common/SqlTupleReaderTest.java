/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2018 Hitachi Vantara and others
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
 */
package org.eclipse.daanse.rolap.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.api.element.Property;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.aggmatcher.JdbcSchema;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.rolap.element.TestPublicRolapProperty;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Created by Dmitriy Stepanov on 20.01.18.
 */
class SqlTupleReaderTest {


  @Test
  @Disabled
  void addLevelMemberSql() throws Exception {
    TupleConstraint constraint = mock( TupleConstraint.class );
    SqlQuery sqlQuery = mock( SqlQuery.class, Answers.RETURNS_MOCKS );
    RolapCube baseCube = mock( RolapCube.class );
    RolapLevel targetLevel = mock( RolapLevel.class );
    RolapColumn expression =  mock(org.eclipse.daanse.rolap.element.RolapColumn.class);
    RolapCubeLevel levelIter = mock( RolapCubeLevel.class, Answers.RETURNS_MOCKS );
    RolapProperty rolapProperty = mock( TestPublicRolapProperty.class, Answers.RETURNS_MOCKS );
    org.eclipse.daanse.rolap.mapping.model.Query queryMapping = mock( org.eclipse.daanse.rolap.mapping.model.Query.class, Answers.RETURNS_MOCKS );
    String propertyName = "property_1";
    Dialect dialect = mock( Dialect.class );
    when(dialect.getDialectName()).thenReturn( "generic" );
    when(dialect.quoteIdentifier(any(String.class), any(String.class))).thenReturn( "generic" );
    org.eclipse.daanse.rolap.mapping.model.SqlStatement sql = mock(org.eclipse.daanse.rolap.mapping.model.SqlStatement.class );
    when(sql.getDialects()).thenAnswer(setupDummyListAnswer("generic"));
    when(sql.getSql()).thenReturn( "SQL" );
    when(expression.getSqls()).thenAnswer(setupDummyListAnswer(sql));
    when(expression.getName()).thenReturn( "name" );
    when(expression.getTable()).thenReturn( "table" );
    when(sqlQuery.getDialect()).thenReturn( dialect );   
    when( rolapProperty.getName() ).thenReturn( propertyName );
    when( rolapProperty.getType() ).thenReturn(Property.Datatype.TYPE_STRING);
    when(rolapProperty.getExp()).thenReturn(expression);
    RolapProperty[] properties = { rolapProperty };
    when( levelIter.getProperties() ).thenReturn( properties );
    when( levelIter.getKeyExp() ).thenReturn( expression );
    when( levelIter.getOrdinalExp() ).thenReturn( expression );
    when( levelIter.getParentExp() ).thenReturn( null );
    RolapHierarchy hierarchy = mock( RolapHierarchy.class, Answers.RETURNS_MOCKS );
    when( hierarchy.getRelation() ).thenReturn( queryMapping );
    when( targetLevel.getHierarchy() ).thenReturn( hierarchy );
    when( hierarchy.getLevels() ).thenAnswer(setupDummyListAnswer(levelIter));
    SqlTupleReader.WhichSelect whichSelect = SqlTupleReader.WhichSelect.LAST;
    JdbcSchema.Table dbTable = mock( JdbcSchema.Table.class, Answers.RETURNS_MOCKS );
    when( dbTable.getColumnUsages( any() ) ).thenReturn( mock( Iterator.class ) );
    RolapStar star = mock( RolapStar.class );
    when( star.getColumnCount() ).thenReturn( 1 );    
    AggStar aggStar = spy( AggStar.makeAggStar( star, dbTable,  10 ) );
    AggStar.Table.Column column = mock( AggStar.Table.Column.class, Answers.RETURNS_MOCKS );
    when(column.getExpression()).thenReturn(expression);
    doReturn( column ).when( aggStar ).lookupColumn( 0 );
    RolapStar.Column starColumn = mock( RolapStar.Column.class, Answers.RETURNS_MOCKS );
    when( starColumn.getDatatype() ).thenReturn( Datatype.VARCHAR );
    when( starColumn.getBitPosition() ).thenReturn( 0 );
    doReturn( starColumn ).when( levelIter ).getStarKeyColumn();
    AggStar.FactTable factTable =
      (AggStar.FactTable) createInstance( "org.eclipse.daanse.rolap.common.aggmatcher.AggStar$FactTable",
        new Class[] { org.eclipse.daanse.rolap.common.aggmatcher.AggStar.class, JdbcSchema.Table.class },
        new Object[] { aggStar, dbTable }, AggStar.FactTable.class.getClassLoader() );
    factTable = spy( factTable );
    Map<String, RolapSqlExpression> propertiesAgg = new HashMap<>();
    propertiesAgg.put( propertyName, expression );
    Class[] constructorArgsClasses =
      { org.eclipse.daanse.rolap.common.aggmatcher.AggStar.Table.class, String.class, SqlExpression.class, int.class,
        RolapStar.Column.class, boolean.class,
        SqlExpression.class, SqlExpression.class, Map.class };
    Object[] constructorArgs =
      { factTable, "name", expression, 0, starColumn, true,
    	  expression, null,
        propertiesAgg };
    AggStar.Table.Level aggStarLevel =
      (AggStar.Table.Level) createInstance( "org.eclipse.daanse.rolap.common.aggmatcher.AggStar$Table$Level", constructorArgsClasses,
        constructorArgs, AggStar.Table.Level.class.getClassLoader() );
    when( aggStar.lookupLevel( 0 ) ).thenReturn( aggStarLevel );
    doReturn( factTable ).when( column ).getTable();
    SqlTupleReader reader = new SqlTupleReader( constraint );
    reader.addLevelMemberSql( sqlQuery, targetLevel, baseCube, whichSelect, aggStar );
    verify( factTable ).addToFrom( any(), eq( false ), eq( true ) );
  }

  private Object createInstance( String className, Class[] constructorArgsClasses, Object[] constructorArgs,
                                 ClassLoader classLoader )

    throws Exception {
    Class cl = classLoader.loadClass( className );
    Constructor constructor = cl.getDeclaredConstructor( constructorArgsClasses );
    constructor.setAccessible( true );
    return constructor.newInstance( constructorArgs );
  }

  private static  <N> Answer<List<N>> setupDummyListAnswer(N... values) {
      final List<N> someList = new LinkedList<>(Arrays.asList(values));

      Answer<List<N>> answer = new Answer<>() {
          @Override
			public List<N> answer(InvocationOnMock invocation) throws Throwable {
              return someList;
          }
      };
      return answer;
  }


}
