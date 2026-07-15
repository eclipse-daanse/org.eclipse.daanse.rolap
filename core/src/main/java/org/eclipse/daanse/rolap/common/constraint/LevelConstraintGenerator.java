/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2020 Hitachi Vantara and others
 * Copyright (C) 2021 Sergei Semenkov
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

package org.eclipse.daanse.rolap.common.constraint;


import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates SQL expressions constraining a level (or a member key) by some value(s), as a raw
 * string or as a dialect-free builder {@code Predicate}.
 *
 * @author av
 * @since Nov 21, 2005
 */
public class LevelConstraintGenerator {

    private static final Logger LOG = LoggerFactory.getLogger( LevelConstraintGenerator.class );
    private final static String aggTableNoConstraintGenerated = """
    Aggregate star fact table ''{0}'':  A constraint will not be generated because name column is not the same as key column.
    """;

    /** Utility class */
  private LevelConstraintGenerator() {
  }


  /**
   * Resolves the datatype used to render a key-column value constraint. A level may declare a
   * logical String type over a physically numeric key column (e.g. {@code type="String"} on
   * {@code customer_id INTEGER}); rendering the comparison as VARCHAR then wraps both sides in
   * a case-fold ({@code UPPER(col) = UPPER('3')}), which is invalid SQL for numeric columns on
   * strict dialects (Derby, DuckDB, PostgreSQL, ClickHouse). When the physical key datatype is
   * numeric and every constrained value parses as a number, the comparison is rendered in the
   * column's numeric domain instead (no fold, unquoted literal). Non-numeric values keep the
   * declared datatype (they cannot match a numeric column either way).
   */
  private static Datatype keyDatatypeFor( RolapLevel level, Datatype declared, String[] columnValues ) {
    if ( declared != Datatype.VARCHAR ) {
      return declared;
    }
    Datatype physical = level.getKeyColumnPhysicalDatatype();
    if ( physical == null || !physical.isNumeric() ) {
      return declared;
    }
    for ( String columnValue : columnValues ) {
      if ( columnValue == null || RolapUtil.mdxNullLiteral().equalsIgnoreCase( columnValue ) ) {
        continue;
      }
      try {
        Double.valueOf( columnValue );
      } catch ( NumberFormatException e ) {
        return declared;
      }
    }
    return physical;
  }

  /**
   * Dialect-free node form of the level constraint: resolves the level's
   * name/key column to a builder node (computed -> RawVariant) and
   * builds a {@link org.eclipse.daanse.sql.statement.api.expression.Predicate}. Empty when no aggregate
   * substitution exists.
   */
  public static java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> constrainLevelPredicate(
      RolapLevel level, RolapCube baseCube, AggStar aggStar, String[] columnValue, boolean caseSensitive ) {
    RolapStar.Column column = null;
    if ( level instanceof RolapCubeLevel rolapCubeLevel ) {
      column = rolapCubeLevel.getBaseStarKeyColumn( baseCube );
    }
    org.eclipse.daanse.sql.statement.api.expression.SqlExpression colNode;
    Datatype datatype;
    if ( column != null ) {
      if ( column.getNameColumn() == null ) {
        datatype = keyDatatypeFor( level, level.getDatatype(), columnValue );
      } else {
        column = column.getNameColumn();
        datatype = Datatype.VARCHAR;
      }
      if ( aggStar != null ) {
        AggStar.Table.Column aggColumn = aggStar.lookupColumn( column.getBitPosition() );
        if ( aggColumn == null ) {
          return java.util.Optional.empty();
        }
        colNode = aggColumn.toSqlExpression();
      } else {
        colNode = org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor( column.getExpression() );
      }
    } else {
      assert ( aggStar == null );
      SqlExpression exp = level.getNameExp();
      if ( exp == null ) {
        exp = level.getKeyExp();
        datatype = keyDatatypeFor( level, level.getDatatype(), columnValue );
      } else {
        datatype = Datatype.VARCHAR;
      }
      colNode = org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor( exp );
    }
    if ( colNode == null ) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of( getColumnValuePredicate( columnValue, caseSensitive, colNode, datatype ) );
  }

  /**
   * Dialect-free node form of the column-value constraint: {@code col = value} / {@code col IS NULL} /
   * {@code col IN (...) [OR col IS NULL]}, with a {@link
   * org.eclipse.daanse.sql.statement.api.expression.SqlExpression.CaseFold} wrap of column and values for a
   * case-insensitive VARCHAR comparison. Values render via {@code Expressions.literal} (the renderer quotes
   * through {@code dialect.quote(value, datatype)}).
   */
  private static org.eclipse.daanse.sql.statement.api.expression.Predicate getColumnValuePredicate(
      String[] columnValues, boolean caseSensitive,
      org.eclipse.daanse.sql.statement.api.expression.SqlExpression colNode, Datatype datatype ) {
    boolean upper = caseSensitive && datatype == Datatype.VARCHAR && !SystemWideProperties.instance().CaseSensitive;
    org.eclipse.daanse.sql.statement.api.expression.SqlExpression col =
        upper ? new org.eclipse.daanse.sql.statement.api.expression.SqlExpression.CaseFold( colNode ) : colNode;
    List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> values = new ArrayList<>();
    boolean containsNull = false;
    for ( String columnValue : columnValues ) {
      if ( RolapUtil.mdxNullLiteral().equalsIgnoreCase( columnValue ) ) {
        containsNull = true;
      } else {
        if ( datatype.isNumeric() ) {
          Double.valueOf( columnValue );
        }
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression v =
            org.eclipse.daanse.sql.statement.api.Expressions.literal( columnValue, datatype );
        values.add( upper ? new org.eclipse.daanse.sql.statement.api.expression.SqlExpression.CaseFold( v ) : v );
      }
    }
    if ( values.size() == 1 && !containsNull ) {
      return org.eclipse.daanse.sql.statement.api.Predicates.comparison( col,
          org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ, values.get( 0 ) );
    }
    if ( values.isEmpty() && containsNull ) {
      return org.eclipse.daanse.sql.statement.api.Predicates.isNull( col );
    }
    List<org.eclipse.daanse.sql.statement.api.expression.Predicate> parts = new ArrayList<>();
    if ( !values.isEmpty() ) {
      parts.add( org.eclipse.daanse.sql.statement.api.Predicates.in( col, values ) );
    }
    if ( containsNull ) {
      parts.add( org.eclipse.daanse.sql.statement.api.Predicates.isNull( col ) );
    }
    return parts.size() == 1 ? parts.get( 0 )
        : org.eclipse.daanse.sql.statement.api.Predicates.or( parts );
  }


}
