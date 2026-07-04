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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapAggregationManager;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.agg.AndPredicate;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.agg.MemberColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.OrPredicate;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Column;
import org.eclipse.daanse.rolap.common.star.RolapStar.Table;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.element.MultiCardinalityDefaultMember;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy.LimitedRollupMember;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Context (slicer) constraint writer used by implementations of
 * {@link org.eclipse.daanse.rolap.common.sql.SqlConstraint}: for every restricting member of the
 * current evaluator context, generates a WHERE condition and a join to the fact table, including
 * role-access constraints and the tuple-based slicer path.
 *
 * @author av
 * @since Nov 21, 2005
 */
public class ContextConstraintWriter {

    private static final Logger LOG = LoggerFactory.getLogger( ContextConstraintWriter.class );

    /** Utility class */
  private ContextConstraintWriter() {
  }


  /**
   * For every restricting member in the current context, generates a WHERE condition and a join to the fact table.
   *
   * @param sqlQuery
   *          the query to modify
   * @param aggStar
   *          Aggregate table, or null if query is against fact table
   * @param restrictMemberTypes
   *          defines the behavior if the current context contains calculated members. If true, thows an exception.
   * @param evaluator
   *          Evaluator
   */
  public static void addContextConstraint( Dialect dialect, QueryRecorder sqlQuery, AggStar aggStar, Evaluator evaluator, RolapCube baseCube,
      boolean restrictMemberTypes ) {
   if ( evaluator instanceof RolapEvaluator rEvaluator) {
      if (baseCube == null) {
          baseCube = rEvaluator.getCube();
      }

      // decide if we should use the tuple-based version instead
      TupleList slicerTuples = rEvaluator.getOptimizedSlicerTuples(baseCube);
      boolean disjointSlicerTuples = false;
      if (slicerTuples != null && !slicerTuples.isEmpty() && (SlicerAnalyzer.isDisjointTuple(slicerTuples)
          || rEvaluator.isMultiLevelSlicerTuple())) {
          disjointSlicerTuples = true;
      }

      TupleConstraintStruct expandedSet =
          makeContextConstraintSet(rEvaluator, restrictMemberTypes, disjointSlicerTuples);

      //This part is needed in case when constrain contains a calculated measure.
      Member[] members = expandedSet.getMembersArray();
      if (members.length > 0
          && !(members[0] instanceof RolapStoredMeasure)) {
          RolapStoredMeasure measure = (RolapStoredMeasure) baseCube.getMeasures().getFirst();
          ArrayList<Member> memberList = new ArrayList<>(Arrays.asList(members));
          memberList.add(0, measure);
          members = memberList.toArray(new Member[0]);
      }

      final CellRequest request = RolapAggregationManager.makeRequest(members);

      if (request == null) {
          if (restrictMemberTypes) {
              throw Util.newInternal("CellRequest is null - why?");
          }
          // One or more of the members was null or calculated, so the
          // request is impossible to satisfy.
          return;
      }

      List<TupleList> slicerTupleList = expandedSet.getDisjoinedTupleLists();

      if (disjointSlicerTuples) {
          slicerTupleList.add(slicerTuples);
      }

      // add slicer tuples from the expanded members
      if (!slicerTupleList.isEmpty()) {
          // Routing decision: disjoint or multi-level slicer tuples cannot be factored per column.
          LOG.debug("using tuple-based native slicer: {} tuple list(s)", slicerTupleList.size());
          for (TupleList tuple : slicerTupleList) {
              addContextConstraintTuples(dialect, sqlQuery, aggStar, rEvaluator, baseCube, restrictMemberTypes, request, tuple);
          }
          return;
      }

      RolapStar.Column[] columns = request.getConstrainedColumns();
      Object[] values = request.getSingleValues();

      if (columns.length > 0) {
          //First add fact table to From.
          if (aggStar != null) {
              aggStar.getFactTable().addToFrom(sqlQuery, false, false);
              sqlQuery.commentFrom(aggStar.getFactTable().getName(), "fact join (context)");
          } else {
              baseCube.getStar().getFactTable().addToFrom(sqlQuery, false, false);
              sqlQuery.commentFrom(baseCube.getStar().getFactTable().getAlias(), "fact join (context)");
          }
      }

      Map<SqlExpression, Set<RolapMember>> mapOfSlicerMembers = null;
      HashMap<SqlExpression, Boolean> done = new HashMap<>();

      for (int i = 0; i < columns.length; i++) {
          final RolapStar.Column column = columns[i];
          final String value = String.valueOf(values[i]);

          // choose from agg or regular star — dialect-free node; the rendered string only for the
          // computed-column fallback
          java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> colNode =
              getColumnNode(sqlQuery, aggStar, column);
          String expr = colNode.isPresent() ? null : getColumnExpr(sqlQuery, aggStar, column);

          if (mapOfSlicerMembers == null) {
              mapOfSlicerMembers = SlicerAnalyzer.getSlicerMemberMap(evaluator);
          }

          final SqlExpression keyForSlicerMap = column.getExpression();

          if (mapOfSlicerMembers.containsKey(keyForSlicerMap)) {
              if (!done.containsKey(keyForSlicerMap)) {
                  Set<RolapMember> slicerMembersSet = mapOfSlicerMembers.get(keyForSlicerMap);

                  // get only constraining members
                  // TODO: can we do this right at getSlicerMemberMap?
                  List<RolapMember> slicerMembers = getNonAllMembers(slicerMembersSet);

                  if (!slicerMembers.isEmpty()) {
                      // get level
                      final int levelIndex = slicerMembers.getFirst().getHierarchy().getLevels().size() - 1;
                      RolapLevel levelForWhere = (RolapLevel) slicerMembers.getFirst().getHierarchy().getLevels().get(levelIndex);
                      // build where constraint — dialect-free Predicate for the single plain level, else
                      // the raw-string path.
                      java.util.Optional<java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate>> wherePred =
                          MemberConstraintWriter.generateSingleValueInPredicate(dialect, sqlQuery, baseCube, aggStar, slicerMembers, levelForWhere,
                              restrictMemberTypes, false, false);
                      if (wherePred.isPresent()) {
                          // non-exclude: parts become separate AND conjuncts (no extra parens).
                          // Cap at the first member: never dump the member list into the comment.
                          final String comment = slicerMembers.size() == 1
                              ? "slicer " + slicerMembers.getFirst().getUniqueName()
                              : "slicer " + levelForWhere.getUniqueName()
                                  + " (" + slicerMembers.size() + " members)";
                          wherePred.get().forEach( p -> sqlQuery.addWhere( p, comment ) );
                      } else {
                          final String where =
                              MemberConstraintWriter.generateSingleValueInExpr(dialect, sqlQuery, baseCube, aggStar, slicerMembers, levelForWhere,
                                  restrictMemberTypes, false, false);
                          if (!where.equals("")) {
                              // The where clause might be null because if the
                              // list of members is greater than the limit
                              // permitted, we won't constraint.
                              sqlQuery.addWhere(where);
                          }
                      }
                  } else {
                      addSimpleColumnConstraint(sqlQuery, colNode, column, expr, value);
                  }
                  done.put(keyForSlicerMap, Boolean.TRUE);
              }
              // if done, no op
          } else {
              // column not constrained by slicer
              addSimpleColumnConstraint(sqlQuery, colNode, column, expr, value);
          }
      }

      // force Role based Access filtering
      addRoleAccessConstraints(dialect, sqlQuery, aggStar, restrictMemberTypes, baseCube, evaluator);
  }
  }

  // package-visible: reused by SqlContextConstraint.toContribution
  static TupleConstraintStruct makeContextConstraintSet( Evaluator evaluator, boolean restrictMemberTypes,
      boolean isTuple ) {
    // Add constraint using the current evaluator context
    List<Member> members = Arrays.asList( evaluator.getNonAllMembers() );

    // Expand the ones that can be expanded. For this particular code line,
    // since this will be used to build a cell request, we need to stay with
    // only one member per ordinal in cube.
    // This follows the same line of thought as the setContext in
    // RolapEvaluator.
    TupleConstraintStruct expandedSet = CalculatedMemberExpander.expandSupportedCalculatedMembers( members, evaluator, isTuple );
    members = expandedSet.getMembers();

    members = CalculatedMemberExpander.getUniqueOrdinalMembers( members );

    if ( restrictMemberTypes ) {
      if ( CalculatedMemberExpander.containsCalculatedMember( members, true ) ) {
        throw Util.newInternal( "can not restrict SQL to calculated Members" );
      }
    } else {
      members = CalculatedMemberExpander.removeCalculatedAndDefaultMembers( members );
    }

    expandedSet.setMembers( members );

    return expandedSet;
  }

  /**
   * Same as {@link addConstraint} but uses tuples
   */
  private static void addContextConstraintTuples( Dialect dialect, QueryRecorder sqlQuery, AggStar aggStar, RolapEvaluator evaluator,
      RolapCube baseCube, boolean restrictMemberTypes, final CellRequest request, TupleList slicerTuples ) {
    assert slicerTuples != null;
    assert !slicerTuples.isEmpty();

    StarPredicate tupleListPredicate =
        getSlicerTuplesPredicate( slicerTuples, baseCube, aggStar, sqlQuery, evaluator );

    // get columns constrained by slicer
    BitKey slicerBitKey = tupleListPredicate.getConstrainedColumnBitKey();
    // constrain context members not in slicer tuples
    RolapStar.Column[] columns = request.getConstrainedColumns();
    Object[] values = request.getSingleValues();
    for ( int i = 0; i < columns.length; i++ ) {
      final RolapStar.Column column = columns[i];
      final String value = String.valueOf( values[i] );
      if ( !slicerBitKey.get( column.getBitPosition() ) ) {
        // column not constrained by tupleSlicer — dialect-free node; the rendered string only for the
        // computed-column fallback
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> colNode =
            getColumnNode( sqlQuery, aggStar, column );
        String expr = colNode.isPresent() ? null : getColumnExpr( sqlQuery, aggStar, column );
        addSimpleColumnConstraint( sqlQuery, colNode, column, expr, value );
      }
      // ok to ignore otherwise, using optimizedSlicerTuples
      // that shouldn't have overridden tuples
    }

    // add our slicer tuples — dialect-free Predicate (the Or(And(MemberColumnPredicate)) shape is handled by
    // StarPredicateTranslator) when not aggregate-substituted; else the raw-string path.
    if ( aggStar == null ) {
      sqlQuery.addWhere(
          org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toPredicate( tupleListPredicate ),
          "slicer tuples (" + slicerTuples.size() + ")" );
    } else {
      StringBuilder buffer = new StringBuilder();
      tupleListPredicate.toSql( dialect, buffer );
      sqlQuery.addWhere( buffer.toString() );
    }

    // force Role based Access filtering
    addRoleAccessConstraints( dialect, sqlQuery, aggStar, restrictMemberTypes, baseCube, evaluator );
  }
  public static Map<RolapLevel, List<RolapMember>> getRolesConstraints( Evaluator evaluator ) {
    Member[] mm = evaluator.getMembers();
    CatalogReader schemaReader = evaluator.getCatalogReader();
    Map<RolapLevel, List<RolapMember>> roleConstraints = new LinkedHashMap<>( mm.length );
    for ( Member member : mm ) {
      boolean isRolesMember =
          ( member instanceof LimitedRollupMember ) || ( member instanceof MultiCardinalityDefaultMember );
      if ( isRolesMember ) {
        List<Level> hierarchyLevels = schemaReader.getHierarchyLevels( member.getHierarchy() );
        for ( Level affectedLevel : hierarchyLevels ) {
          List<Member> availableMembers = schemaReader.getLevelMembers( affectedLevel, false );
          List<RolapMember> slicerMembers = new ArrayList<>( availableMembers.size() );
          for ( Member available : availableMembers ) {
            if ( !available.isAll() ) {
              slicerMembers.add( (RolapMember) available );
            }
          }
          if ( !slicerMembers.isEmpty() ) {
            roleConstraints.put( (RolapLevel) affectedLevel, slicerMembers );
          }
        }
      }
    }

    return roleConstraints.isEmpty() ? Collections.<RolapLevel, List<RolapMember>> emptyMap() : roleConstraints;
  }

  /**
   * Creates a predicate for the slicer tuple list
   */
  static StarPredicate getSlicerTuplesPredicate( TupleList tupleList, RolapCube baseCube, AggStar aggStar,
      QueryRecorder sqlQuery, RolapEvaluator evaluator ) {
    List<StarPredicate> tupleListPredicate = new ArrayList<>();
    for ( List<Member> tuple : tupleList ) {
      tupleListPredicate.add( getTupleConstraint( tuple, baseCube, aggStar, sqlQuery ) );
    }
    return new OrPredicate( tupleListPredicate );
  }

  /**
   * Pure (no query mutation, base star only) twin of {@link #getSlicerTuplesPredicate}: builds the same
   * {@code Or(And(MemberColumnPredicate))} from the tuple list using each level's base star key column
   * directly, so a {@link org.eclipse.daanse.rolap.common.sql.ConstraintContribution} can carry it
   * ({@link org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator#toPredicate}) and add the
   * predicate's columns' tables to its join set, instead of {@code addToFrom}-mutating a query.
   */
  static StarPredicate getSlicerTuplesPredicatePure( TupleList tupleList, RolapCube baseCube ) {
    List<StarPredicate> tupleListPredicate = new ArrayList<>();
    for ( List<Member> tuple : tupleList ) {
      List<StarPredicate> predicateList = new ArrayList<>();
      for ( Member member : tuple ) {
        ArrayList<MemberColumnPredicate> memberList = new ArrayList<>();
        for ( RolapMember currMember = (RolapMember) member; currMember != null;
            currMember = currMember.getParentMember() ) {
          if ( currMember.isAll() ) {
            continue;
          }
          RolapLevel level = currMember.getLevel();
          RolapStar.Column column = ( (RolapCubeLevel) level ).getBaseStarKeyColumn( baseCube );
          memberList.add( new MemberColumnPredicate( column, currMember ) );
          if ( level.isUnique() ) {
            break;
          }
        }
        for ( int i = memberList.size() - 1; i >= 0; i-- ) {
          predicateList.add( memberList.get( i ) );
        }
      }
      tupleListPredicate.add( new AndPredicate( predicateList ) );
    }
    return new OrPredicate( tupleListPredicate );
  }
  /**
   * Creates constraint on a single tuple
   */
  private static AndPredicate getTupleConstraint( List<Member> tuple, RolapCube baseCube, AggStar aggStar,
      QueryRecorder sqlQuery ) {
    List<StarPredicate> predicateList = new ArrayList<>();
    for ( Member member : tuple ) {
      addMember( (RolapMember) member, predicateList, baseCube, aggStar, sqlQuery );
    }
    return new AndPredicate( predicateList );
  }

  /**
   * add single member constraint to predicate list
   */
  private static void addMember( RolapMember member, List<StarPredicate> predicateList, RolapCube baseCube,
      AggStar aggStar, QueryRecorder sqlQuery ) {
    ArrayList<MemberColumnPredicate> memberList = new ArrayList<>();
    // add parents until a unique level is reached
    for ( RolapMember currMember = member; currMember != null; currMember = currMember.getParentMember() ) {
      if ( currMember.isAll() ) {
        continue;
      }
      RolapLevel level = currMember.getLevel();
      RolapStar.Column column = getLevelColumn( level, baseCube, aggStar, sqlQuery );
      ( (RolapCubeLevel) level ).getBaseStarKeyColumn( baseCube );
      memberList.add( new MemberColumnPredicate( column, currMember ) );
      if ( level.isUnique() ) {
        break;
      }
    }
    for ( int i = memberList.size() - 1; i >= 0; i-- ) {
      predicateList.add( memberList.get( i ) );
    }
  }

  /**
   * Gets the column, using AggStar if available, and ensures the table is in the query.
   */
  private static RolapStar.Column getLevelColumn( RolapLevel level, RolapCube baseCube, AggStar aggStar,
      QueryRecorder sqlQuery ) {
    final RolapStar.Column column = ( (RolapCubeLevel) level ).getBaseStarKeyColumn( baseCube );
    if ( aggStar != null ) {
      int bitPos = column.getBitPosition();
      final AggStar.Table.Column aggColumn = aggStar.lookupColumn( bitPos );
      AggStar.Table table = aggColumn.getTable();
      table.addToFrom( sqlQuery, false, true );
      // create a delegate to use the aggregated column's expression
      return new Column( aggColumn.getDatatype() ) {
        @Override
        public String generateExprString( Dialect dialect ) {
          // Delegate to the aggregated column; reached when a predicate renders via toSql(Dialect),
          // otherwise the base method would emit the wrong expression.
          return aggColumn.generateExprString( dialect );
        }

        @Override
		public int getBitPosition() {
          // this is the same as the one in RolapStar.Column
          return aggColumn.getBitPosition();
        }

        @Override
		public Table getTable() {
          return column.getTable();
        }

        @Override
		public RolapStar getStar() {
          return column.getStar();
        }
      };
    } else {
      column.getTable().addToFrom( sqlQuery, false, true );
      return column;
    }
  }

  /**
   * Get the column expression from the AggStar if provided or the regular table if not, and ensure table is in From
   */
  public static String getColumnExpr( QueryRecorder sqlQuery, AggStar aggStar, RolapStar.Column column ) {
    final String expr;
    if ( aggStar != null ) {
      int bitPos = column.getBitPosition();
      AggStar.Table.Column aggColumn = aggStar.lookupColumn( bitPos );
      AggStar.Table table = aggColumn.getTable();
      table.addToFrom( sqlQuery, false, true );
      expr = aggColumn.generateExprString( column.getStar().getDialect() );
    } else {
      RolapStar.Table table = column.getTable();
      table.addToFrom( sqlQuery, false, true );
      expr = column.generateExprString( column.getStar().getDialect() );
    }
    return expr;
  }

  /** Node twin of {@link #getColumnExpr}: the same agg-or-regular column choice and FROM side effect
   *  ({@code addToFrom}), but returning the dialect-free builder node — the aggregate column's own node
   *  ({@code AggStar.Table.Column#toSqlExpression}) or the star column via
   *  {@link org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner#expressionFor(RolapStar.Column)} — instead of a dialect-rendered
   *  string. Empty exactly when {@link MemberConstraintWriter#dialectFreeColumnNode} would be (column-less column / missing aggregate
   *  node), so the caller falls back to the string path. */
  private static java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression>
      getColumnNode( QueryRecorder sqlQuery, AggStar aggStar, RolapStar.Column column ) {
    if ( aggStar != null ) {
      int bitPos = column.getBitPosition();
      AggStar.Table.Column aggColumn = aggStar.lookupColumn( bitPos );
      AggStar.Table table = aggColumn.getTable();
      table.addToFrom( sqlQuery, false, true );
      return java.util.Optional.ofNullable( aggColumn.toSqlExpression() );
    }
    RolapStar.Table table = column.getTable();
    table.addToFrom( sqlQuery, false, true );
    return column.getExpression() == null ? java.util.Optional.empty()
        : java.util.Optional.of( org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor( column ) );
  }

  /**
   * @return only non-all members
   */
  static List<RolapMember> getNonAllMembers( Collection<RolapMember> slicerMembersSet ) {
    List<RolapMember> slicerMembers = new ArrayList<>( slicerMembersSet );

    // search and destroy [all](s)
    List<RolapMember> allMembers = new ArrayList<>();
    for ( RolapMember slicerMember : slicerMembers ) {
      if ( slicerMember.isAll() ) {
        allMembers.add( slicerMember );
      }
    }
    if ( !allMembers.isEmpty() ) {
      slicerMembers.removeAll( allMembers );
    }
    return slicerMembers;
  }
  /**
   * add 'expr = value' to where
   */
  private static void addSimpleColumnConstraint( QueryRecorder sqlQuery,
      java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> colNode,
      RolapStar.Column column, String expr, final String value ) {
    boolean isNull = RolapUtil.mdxNullLiteral().equalsIgnoreCase( value )
        || value.equalsIgnoreCase( Util.sqlNullValue.toString() );
    if ( !isNull && column.getDatatype().isNumeric() ) {
      // make sure it can be parsed
      var d = Double.valueOf( value );
      if (d == null) {
          throw new IllegalArgumentException("value should be parse to double");
      }
    }
    // Dialect-free path: the caller threads the column node (getColumnNode, the node twin of getColumnExpr);
    // a plain column (base, or its aggregate substitution) renders the same as the expr/quote string below —
    // the builder's Literal also renders via dialect.quote(value, datatype), and SQL_NULL_LITERAL is "null"
    // == the renderer's "is null". Computed columns keep the raw-string path.
    if ( colNode.isPresent() ) {
      org.eclipse.daanse.sql.statement.api.expression.SqlExpression col = colNode.get();
      sqlQuery.addWhere( isNull
          ? org.eclipse.daanse.sql.statement.api.Predicates.isNull( col )
          : org.eclipse.daanse.sql.statement.api.Predicates.comparison( col,
              org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
              org.eclipse.daanse.sql.statement.api.Expressions.literal( value, column.getDatatype() ) ),
          column.getName() != null ? "context " + column.getName() : "context" );
      return;
    }
    // residual: computed-column constraint string — see 07-fallback-policy
    if ( isNull ) {
      sqlQuery.addWhere( expr, " is ", RolapUtil.SQL_NULL_LITERAL);
    } else {
      // No extra slicers.... just use the = method
      final StringBuilder buf = new StringBuilder();
      column.getStar().getDialect().quote( buf, value, column.getDatatype() );
      sqlQuery.addWhere( expr, " = ", buf.toString() );
    }
  }

  public static Map<Level, List<RolapMember>> getRoleConstraintMembers( CatalogReader schemaReader, Member[] members ) {
    // LinkedHashMap keeps insert-order
    Map<Level, List<RolapMember>> roleMembers = new LinkedHashMap<>();
    Role role = schemaReader.getRole();
    for ( Member member : members ) {
      if ( member instanceof LimitedRollupMember || member instanceof MultiCardinalityDefaultMember ) {
        // iterate relevant levels to get accessible members
        List<Level> hierarchyLevels = schemaReader.getHierarchyLevels( member.getHierarchy() );
        for ( Level affectedLevel : hierarchyLevels ) {
          List<RolapMember> slicerMembers = new ArrayList<>();
          boolean hasCustom = false;
          List<Member> availableMembers = schemaReader.getLevelMembers( affectedLevel, false );
          for ( Member availableMember : availableMembers ) {
            if ( !availableMember.isAll() ) {
              slicerMembers.add( (RolapMember) availableMember );
            }
            hasCustom |= role.getAccess( availableMember ) == AccessMember.CUSTOM;
          }
          if ( !slicerMembers.isEmpty() ) {
            roleMembers.put( affectedLevel, slicerMembers );
          }
          if ( !hasCustom ) {
            // we don't have partial access, no need to go deeper
            break;
          }
        }
      }
    }
    return roleMembers;
  }

  private static void addRoleAccessConstraints( Dialect dialect, QueryRecorder sqlQuery, AggStar aggStar, boolean restrictMemberTypes,
      RolapCube baseCube, Evaluator evaluator ) {
    Map<Level, List<RolapMember>> roleMembers =
        getRoleConstraintMembers( evaluator.getCatalogReader(), evaluator.getMembers() );
    for ( Map.Entry<Level, List<RolapMember>> entry : roleMembers.entrySet() ) {
      // Where clauses generated by Roles will in all likelihood require the fact join, so join it
      // defensively.
      java.util.Optional<java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate>> wherePred =
          MemberConstraintWriter.generateSingleValueInPredicate( dialect, sqlQuery, baseCube, aggStar, entry.getValue(),
              (RolapCubeLevel) entry.getKey(), restrictMemberTypes, false, true );
      if ( wherePred.isPresent() ) {
        joinLevelTableToFactTable( sqlQuery, baseCube, aggStar, (RolapCubeLevel) entry.getKey() );
        final String comment = "role access " + entry.getKey().getUniqueName();
        wherePred.get().forEach( p -> sqlQuery.addWhere( p, comment ) );
      } else {
        final String where =
            MemberConstraintWriter.generateSingleValueInExpr( dialect, sqlQuery, baseCube, aggStar, entry.getValue(), (RolapCubeLevel) entry.getKey(),
                restrictMemberTypes, false, true );
        if ( where.length() > 1 ) {
          // The where clause might be null because if the list of members is greater than the limit
          // permitted, we won't constrain.
          joinLevelTableToFactTable( sqlQuery, baseCube, aggStar, (RolapCubeLevel) entry.getKey() );
          sqlQuery.addWhere( where );
        }
      }
    }
  }
  /**
   * Ensures that the table of level is joined to the fact table
   *
   * @param sqlQuery
   *          sql query under construction
   * @param baseCube
   *          baseCube
   * @param aggStar
   *          agg Star
   * @param level
   *          level to be added to query
   */
  public static void joinLevelTableToFactTable( QueryRecorder sqlQuery, RolapCube baseCube, AggStar aggStar,
      RolapCubeLevel level ) {
    RolapStar.Column starColumn = level.getBaseStarKeyColumn( baseCube );
    if ( aggStar != null ) {
      int bitPos = starColumn.getBitPosition();
      AggStar.Table.Column aggColumn = aggStar.lookupColumn( bitPos );
      AggStar.Table table = aggColumn.getTable();
      table.addToFrom( sqlQuery, false, true );
      sqlQuery.commentFrom( table.getName(), "fact join (nonempty)" );
    } else {
      RolapStar.Table table = starColumn.getTable();
      assert table != null;
      table.addToFrom( sqlQuery, false, true );
      sqlQuery.commentFrom( table.getAlias(), "fact join (nonempty)" );
    }
  }
}
