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

import static org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.render;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
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

  public static StringBuilder constrainLevel( Dialect dialect, RolapLevel level, QueryRecorder query, RolapCube baseCube, AggStar aggStar,
      String columnValue, boolean caseSensitive ) {
    return constrainLevel( dialect, level, query, baseCube, aggStar, new String[] { columnValue }, caseSensitive );
  }

  /**
   * Generates a sql expression constraining a level by some value
   *
   * @param level
   *          the level
   * @param query
   *          the query that the sql expression will be added to
   * @param baseCube
   *          base cube for virtual levels
   * @param aggStar
   *          aggregate star if available
   * @param columnValue
   *          value constraining the level
   * @param caseSensitive
   *          if true, need to handle case sensitivity of the member value
   *
   * @return generated string corresponding to the expression
   */
  public static StringBuilder constrainLevel( Dialect dialect, RolapLevel level, QueryRecorder query, RolapCube baseCube, AggStar aggStar,
      String[] columnValue, boolean caseSensitive ) {
    // this method can be called within the context of shared members,
    // outside of the normal rolap star, therefore we need to
    // check the level to see if it is a shared or cube level.

    RolapStar.Column column = null;
    if ( level instanceof RolapCubeLevel rolapCubeLevel) {
      column = rolapCubeLevel.getBaseStarKeyColumn( baseCube );
    }

    String columnString;
    Datatype datatype;
    if ( column != null ) {
      if ( column.getNameColumn() == null ) {
        datatype = keyDatatypeFor( level, level.getDatatype(), columnValue );
      } else {
        column = column.getNameColumn();
        // The schema doesn't specify the datatype of the name column,
        // but we presume that it is a string.
        datatype = Datatype.VARCHAR;
      }
      if ( aggStar != null ) {
        // this makes the assumption that the name column is the same
        // as the key column
        int bitPos = column.getBitPosition();
        AggStar.Table.Column aggColumn = aggStar.lookupColumn( bitPos );

        if ( aggColumn == null ) {
          LOG.warn(MessageFormat.format(aggTableNoConstraintGenerated, aggStar.getFactTable().getName() ) );
          return new StringBuilder();
        }

        columnString = aggColumn.generateExprString( dialect );
      } else {
        columnString = column.generateExprString( dialect );
      }
    } else {
      assert ( aggStar == null );
      SqlExpression exp = level.getNameExp();
      if ( exp == null ) {
        exp = level.getKeyExp();
        datatype = keyDatatypeFor( level, level.getDatatype(), columnValue );
      } else {
        // The schema doesn't specify the datatype of the name column,
        // but we presume that it is a string.
        datatype = Datatype.VARCHAR;
      }
      columnString = render( exp, dialect );
    }

    return getColumnValueConstraint( dialect, columnValue, caseSensitive, columnString, datatype );
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
   * Dialect-free node form of {@link #constrainLevel(Dialect, RolapLevel, QueryRecorder, RolapCube, AggStar,
   * String[], boolean)}: resolves the level's name/key column to a builder node (computed -> RawVariant) and
   * builds a {@link org.eclipse.daanse.sql.statement.api.expression.Predicate}. Empty when no aggregate
   * substitution exists (caller falls back to the string form).
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
   * Dialect-free node form of {@link #getColumnValueConstraint}: {@code col = value} / {@code col IS NULL} /
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

  private static StringBuilder getColumnValueConstraint( Dialect dialect, String[] columnValues, boolean caseSensitive,
      String columnString, Datatype datatype ) {
      StringBuilder columnStringBuilder = new StringBuilder(columnString);
      StringBuilder constraintStringBuilder;
      List<CharSequence> values = new ArrayList();
    boolean containsNull = false;

    for ( String columnValue : columnValues ) {
      if ( RolapUtil.mdxNullLiteral().equalsIgnoreCase( columnValue ) ) {
        containsNull = true;
        // constraint = columnString + " is " + RolapUtil.sqlNullLiteral;
      } else {
        if ( datatype.isNumeric() ) {
          // make sure it can be parsed
          var d = Double.valueOf( columnValue );
          if (d == null) {
              throw new IllegalArgumentException("value should be parse to double");
          }

        }
        final StringBuilder buf = new StringBuilder();
        dialect.quote( buf, columnValue, datatype );
        CharSequence value = buf;
        if ( caseSensitive && datatype == Datatype.VARCHAR) {
          // Some databases (like DB2) compare case-sensitive.
          // We convert
          // the value to upper-case in the DBMS (e.g. UPPER('Foo'))
          // rather than in Java (e.g. 'FOO') in case the DBMS is
          // running a different locale.
          if ( !SystemWideProperties.instance().CaseSensitive ) {
            value = dialect.functionGenerator().wrapIntoSqlUpperCaseFunction( buf );
          }
        }
        values.add( value );
      }
    }

    if ( caseSensitive && datatype == Datatype.VARCHAR && !SystemWideProperties.instance().CaseSensitive ) {
        columnStringBuilder = dialect.functionGenerator().wrapIntoSqlUpperCaseFunction( columnStringBuilder );
    }

    if ( values.size() == 1 ) {
      if ( containsNull ) {
          constraintStringBuilder = columnStringBuilder.append(" IS ").append(RolapUtil.SQL_NULL_LITERAL);
      } else {
          constraintStringBuilder = columnStringBuilder.append(" = ").append(values.get( 0 ));
      }
    } else {
        constraintStringBuilder = new StringBuilder();
        constraintStringBuilder.append( "( " );
        if ( !values.isEmpty() ) {
            constraintStringBuilder.append( columnStringBuilder ).append( " IN (" );
            for ( int i = 0; i < values.size(); i++ ) {
                CharSequence value = values.get( i );
                constraintStringBuilder.append( value );
                if ( i < values.size() - 1 ) {
                    constraintStringBuilder.append( "," );
                }
            }
            constraintStringBuilder.append( ")" );
        }
        if ( containsNull ) {
            if ( !values.isEmpty() ) {
                constraintStringBuilder.append( " OR " );
            }
            constraintStringBuilder.append( columnStringBuilder ).append( " IS NULL " );
        }
        constraintStringBuilder.append( ")" );
    }
    return constraintStringBuilder;
  }

  /**
   * Generates a sql expression constraining a level by some value
   *
   * @param exp
   *          Key expression
   * @param datatype
   *          Key datatype
   * @param columnValue
   *          value constraining the level
   *
   * @return generated string corresponding to the expression
   */
  public static String constrainKeyValue(Dialect dialect, SqlExpression exp, Datatype datatype,
                                       Comparable columnValue ) {
    String columnString = render( exp, dialect );
    if ( columnValue == Util.sqlNullValue ) {
      return new StringBuilder(columnString).append(" is ").append(RolapUtil.SQL_NULL_LITERAL).toString();
    } else {
      final StringBuilder buf = new StringBuilder();
      buf.append( columnString );
      buf.append( " = " );
      dialect.quote( buf, columnValue, datatype );
      return buf.toString();
    }
  }
}
