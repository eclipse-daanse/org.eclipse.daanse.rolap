/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2021 Hitachi Vantara and others
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.calc.todo.TupleIterable;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.type.SetType;
import org.eclipse.daanse.olap.api.type.Type;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.exceptions.UnsupportedCalculatedMemberException;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.agg.AndPredicate;
import org.eclipse.daanse.rolap.common.agg.ListColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.OrPredicate;
import org.eclipse.daanse.rolap.common.agg.ValueColumnPredicate;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapMeasure;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.eclipse.daanse.rolap.function.def.visualtotals.VisualTotalMember;

/**
 * Constructs a Pair with BitKey and StarPredicate based on an tuple list and measure, along with the string representation of
 * the predicate. Also sets the isSatisfiable flag based on whether a predicate is compatible with the measure.
 *
 * This logic was extracted from RolapAggregationManager and AggregationKey.
 */
public class CompoundPredicateInfo {

  private final Pair<BitKey, StarPredicate> predicate;
  private final String predicateString;
  private final RolapMeasure measure;
  private boolean satisfiable = true;

  public CompoundPredicateInfo( List<List<Member>> tupleList, RolapMeasure measure, Evaluator evaluator ) {
      if (measure == null) {
          throw new IllegalArgumentException("measure should not be null");
      }
      this.measure = measure;
      this.predicate = predicateFromTupleList( tupleList, measure, evaluator );
      this.predicateString = getPredicateString( getStar( measure ), getPredicate() );
  }

  public StarPredicate getPredicate() {
    return predicate == null ? null : predicate.right;
  }

  public BitKey getBitKey() {
    return predicate == null ? null : predicate.left;
  }

  public String getPredicateString() {
    return predicateString;
  }

  public boolean isSatisfiable() {
    return satisfiable;
  }

  public RolapCube getCube() {
    return measure.isCalculated() ? null : ( (RolapStoredMeasure) measure ).getCube();
  }

  /**
   * Returns a string representation of the predicate
   */
  public static String getPredicateString( RolapStar star, StarPredicate predicate ) {
    if ( star == null || predicate == null ) {
      return null;
    }
    final StringBuilder buf = new StringBuilder();
    SqlQuery query = new SqlQuery( star.getSqlQueryDialect(), star.getContext().getConfigValue(ConfigConstants.GENERATE_FORMATTED_SQL, ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class) );
    buf.setLength( 0 );
    predicate.toSql( query, buf );
    return buf.toString();
  }

  private static RolapStar getStar( RolapMeasure measure ) {
    if ( measure.isCalculated() ) {
      return null;
    }
    final RolapStoredMeasure storedMeasure = (RolapStoredMeasure) measure;
    final RolapStar.Measure starMeasure = (RolapStar.Measure) storedMeasure.getStarMeasure();
    assert starMeasure != null;
    return starMeasure.getStar();
  }

  private Pair<BitKey, StarPredicate> predicateFromTupleList( List<List<Member>> tupleList, RolapMeasure measure,
      Evaluator evaluator ) {
    if ( measure.isCalculated() ) {
      // need a base measure to build predicates
      return null;
    }
    RolapCube cube = ( (RolapStoredMeasure) measure ).getCube();

    BitKey compoundBitKey;
    StarPredicate compoundPredicate;
    Map<BitKey, List<RolapCubeMember[]>> compoundGroupMap;
    boolean unsatisfiable;
    RolapStar rolapStar = getStar( measure );
    int starColumnCount = rolapStar != null ? rolapStar.getColumnCount() : 0;

    compoundBitKey = BitKey.Factory.makeBitKey( starColumnCount );
    compoundBitKey.clear();
    compoundGroupMap = new LinkedHashMap<>();
    unsatisfiable = makeCompoundGroup( starColumnCount, cube, tupleList, compoundGroupMap );

    if ( unsatisfiable ) {
      satisfiable = false;
      return null;
    }
    compoundPredicate = makeCompoundPredicate( compoundGroupMap, cube, evaluator );
    if ( compoundPredicate != null ) {
      for ( BitKey bitKey : compoundGroupMap.keySet() ) {
        compoundBitKey = compoundBitKey.or( bitKey );
      }
    }
    return Pair.of( compoundBitKey, compoundPredicate );
  }

  /**
   * Groups members (or tuples) from the same compound (i.e. hierarchy) into groups that are constrained by the same set
   * of columns.
   *
   *
   * E.g.
   *
   *
   * Members
   *     [USA].[CA],
   *     [Canada].[BC],
   *     [USA].[CA].[San Francisco],
   *     [USA].[OR].[Portland]
   *
   *
   * will be grouped into
   *
   *
   * Group 1:
   *     {[USA].[CA], [Canada].[BC]}
   * Group 2:
   *     {[USA].[CA].[San Francisco], [USA].[OR].[Portland]}
   *
   *
   *
   * This helps with generating optimal form of sql.
   *
   *
   * In case of aggregating over a list of tuples, similar logic also applies.
   *
   *
   * For example:
   *
   *
   * Tuples:
   *     ([Gender].[M], [Store].[USA].[CA])
   *     ([Gender].[F], [Store].[USA].[CA])
   *     ([Gender].[M], [Store].[USA])
   *     ([Gender].[F], [Store].[Canada])
   *
   *
   * will be grouped into
   *
   *
   * Group 1:
   *     {([Gender].[M], [Store].[USA].[CA]),
   *      ([Gender].[F], [Store].[USA].[CA])}
   * Group 2:
   *     {([Gender].[M], [Store].[USA]),
   *      ([Gender].[F], [Store].[Canada])}
   *
   *
   *
   * This function returns a boolean value indicating if any constraint can be created from the aggregationList. It is
   * possible that only part of the aggregationList can be applied, which still leads to a (partial) constraint that is
   * represented by the compoundGroupMap.
   */
  private boolean makeCompoundGroup( int starColumnCount, RolapCube baseCube, List<List<Member>> aggregationList,
      Map<BitKey, List<RolapCubeMember[]>> compoundGroupMap ) {
    // The more generalized aggregation as aggregating over tuples.
    // The special case is a tuple defined by only one member.
    int unsatisfiableTupleCount = 0;
    for ( List<Member> aggregation : aggregationList ) {
      if ( !( !aggregation.isEmpty() && ( aggregation.get( 0 ) instanceof RolapCubeMember
          || aggregation.get( 0 ) instanceof VisualTotalMember ) ) ) {
        ++unsatisfiableTupleCount;
        continue;
      }

      BitKey bitKey = BitKey.Factory.makeBitKey( starColumnCount );
      RolapCubeMember[] tuple;

      tuple = new RolapCubeMember[aggregation.size()];
      int i = 0;
      for ( Member member : aggregation ) {
        if ( member instanceof VisualTotalMember ) {
          tuple[i] = (RolapCubeMember) ( (VisualTotalMember) member ).getMember();
        } else {
          tuple[i] = (RolapCubeMember) member;
        }
        i++;
      }

      boolean tupleUnsatisfiable = false;
      for ( RolapCubeMember member : tuple ) {
        // Tuple cannot be constrained if any of the member cannot be.
        tupleUnsatisfiable = makeCompoundGroupForMember( member, baseCube, bitKey );
        if ( tupleUnsatisfiable ) {
          // If this tuple is unsatisfiable, skip it and try to
          // constrain the next tuple.
          unsatisfiableTupleCount++;
          break;
        }
      }

      if ( !tupleUnsatisfiable && !bitKey.isEmpty() ) {
        // Found tuple(columns) to constrain,
        // now add it to the compoundGroupMap
        addTupleToCompoundGroupMap( tuple, bitKey, compoundGroupMap );
      }
    }
    return ( unsatisfiableTupleCount == aggregationList.size() );
  }

  private void addTupleToCompoundGroupMap( RolapCubeMember[] tuple, BitKey bitKey,
      Map<BitKey, List<RolapCubeMember[]>> compoundGroupMap ) {
      compoundGroupMap.computeIfAbsent(bitKey, k -> new ArrayList<>()).add( tuple );
  }

  private boolean makeCompoundGroupForMember( RolapCubeMember member, RolapCube baseCube, BitKey bitKey ) {
    RolapCubeMember levelMember = member;
    boolean memberUnsatisfiable = false;
    while ( levelMember != null ) {
      RolapCubeLevel level = levelMember.getLevel();
      // Only need to constrain the nonAll levels
      if ( !level.isAll() ) {
        RolapStar.Column column = level.getBaseStarKeyColumn( baseCube );
        if ( column != null ) {
          bitKey.set( column.getBitPosition() );
        } else {
          // One level in a member causes the member to be
          // unsatisfiable.
          memberUnsatisfiable = true;
          break;
        }
      }

      levelMember = levelMember.getParentMember();
    }
    return memberUnsatisfiable;
  }

  private StarPredicate makeCompoundPredicate( Map<BitKey, List<RolapCubeMember[]>> compoundGroupMap,
      RolapCube baseCube, Evaluator evaluator ) {
    List<StarPredicate> compoundPredicateList = new ArrayList<>();
    for ( List<RolapCubeMember[]> group : compoundGroupMap.values() ) {
      // e.g [USA].[CA], [Canada].[BC]
      StarPredicate compoundGroupPredicate = null;
      List<StarPredicate> tuplePredicateList = new ArrayList<>();
      for ( RolapCubeMember[] tuple : group ) {
        // [USA].[CA]
        StarPredicate tuplePredicate = null;

        for ( RolapCubeMember member : tuple ) {
          tuplePredicate = makePredicateForMember( member, baseCube, tuplePredicate, evaluator );
        }
        if ( tuplePredicate != null ) {
          tuplePredicateList.add( tuplePredicate );
        }
      }
      if ( tuplePredicateList.size() == 1 ) {
        compoundGroupPredicate = tuplePredicateList.get( 0 );
      } else if ( tuplePredicateList.size() > 1 ) {
        // All tuples in the same group will constrain the same set of columns so
        // when combining the tuple predicates we can optimize to create the
        // ListColumnPredicate or OrPredicate in a single batch. See MONDRIAN-2719
        if ( tuplePredicateList.get( 0 ) instanceof StarColumnPredicate scp ) {
          compoundGroupPredicate =
              new ListColumnPredicate( scp.getConstrainedColumn(), Util.cast( tuplePredicateList ) );
        } else {
          compoundGroupPredicate = new OrPredicate( tuplePredicateList );
        }
      }

      if ( compoundGroupPredicate != null ) {
        // Sometimes the compound member list does not constrain any
        // columns; for example, if only AllLevel is present.
        compoundPredicateList.add( compoundGroupPredicate );
      }
    }

    StarPredicate compoundPredicate = null;

    if ( compoundPredicateList.size() > 1 ) {
      compoundPredicate = new OrPredicate( compoundPredicateList );
    } else if ( compoundPredicateList.size() == 1 ) {
      compoundPredicate = compoundPredicateList.get( 0 );
    }

    return compoundPredicate;
  }

  private StarPredicate makePredicateForMember( RolapCubeMember member, RolapCube baseCube,
      StarPredicate memberPredicate, Evaluator evaluator ) {
    while ( member != null ) {
      RolapCubeLevel level = member.getLevel();
      if ( !level.isAll() ) {
        RolapStar.Column column = level.getBaseStarKeyColumn( baseCube );
        StarPredicate addPredicate = null;
        if ( !member.isCalculated() ) {
          addPredicate = new ValueColumnPredicate( column, member.getKey() );
        } else {
          addPredicate = makeCalculatedMemberPredicate( member, baseCube, evaluator );
        }
        if ( memberPredicate == null ) {
          memberPredicate = addPredicate;
        } else {
          memberPredicate = memberPredicate.and( addPredicate );
        }
      }
      // Don't need to constrain USA if CA is unique
      if ( member.getLevel().isUnique() ) {
        break;
      }
      member = member.getParentMember();
    }
    return memberPredicate;
  }

  private StarPredicate makeCalculatedMemberPredicate( RolapCubeMember member, RolapCube baseCube,
      Evaluator evaluator ) {
    assert member.getExpression() instanceof ResolvedFunCallImpl;

    ResolvedFunCallImpl fun = (ResolvedFunCallImpl) member.getExpression();

    final Expression exp = fun.getArg( 0 );
    final Type type = exp.getType();

    if ( type instanceof SetType ) {
      return makeSetPredicate( exp, evaluator );
    } else if ( type.getArity() == 1 ) {
      return makeUnaryPredicate( member, baseCube, evaluator );
    } else {
      throw new UnsupportedCalculatedMemberException( member.getName() );
    }
  }

  private StarPredicate makeUnaryPredicate( RolapCubeMember member, RolapCube baseCube, Evaluator evaluator ) {
    TupleConstraintStruct constraint = new TupleConstraintStruct();
    SqlConstraintUtils.expandSupportedCalculatedMember( member, evaluator, constraint );
    List<Member> expandedMemberList = constraint.getMembers();
    for ( Member checkMember : expandedMemberList ) {
      if ( checkMember == null || checkMember.isCalculated() || !( checkMember instanceof RolapCubeMember ) ) {
        throw new UnsupportedCalculatedMemberException( member.getName() );
      }
    }
    List<StarPredicate> predicates = new ArrayList<>( expandedMemberList.size() );
    for ( Member iMember : expandedMemberList ) {
      RolapCubeMember iCubeMember = ( (RolapCubeMember) iMember );
      RolapCubeLevel iLevel = iCubeMember.getLevel();
      RolapStar.Column iColumn = iLevel.getBaseStarKeyColumn( baseCube );
      Object iKey = iCubeMember.getKey();
      StarPredicate iPredicate = new ValueColumnPredicate( iColumn, iKey );
      predicates.add( iPredicate );
    }
    StarPredicate r = null;
    if ( predicates.size() == 1 ) {
      r = predicates.get( 0 );
    } else {
      r = new OrPredicate( predicates );
    }
    return r;
  }

  private StarPredicate makeSetPredicate( final Expression exp, Evaluator evaluator ) {
    TupleIterable evaluatedSet = evaluator.getSetEvaluator( exp, true ).evaluateTupleIterable();
    ArrayList<StarPredicate> orList = new ArrayList<>();
    OrPredicate orPredicate = null;
    for ( List<Member> complexSetItem : evaluatedSet ) {
      List<StarPredicate> andList = new ArrayList<>();
      for ( Member singleSetItem : complexSetItem ) {
        final List<List<Member>> singleItemList =
            Collections.singletonList( Collections.singletonList( singleSetItem ) );
        StarPredicate singlePredicate = predicateFromTupleList( singleItemList, measure, evaluator ).getValue();
        andList.add( singlePredicate );
      }
      AndPredicate andPredicate = new AndPredicate( andList );
      orList.add( andPredicate );
      orPredicate = new OrPredicate( orList );
    }
    return orPredicate;
  }
}
