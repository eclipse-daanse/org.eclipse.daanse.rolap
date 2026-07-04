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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.element.RolapLevel;

/**
 * Analyzes the slicer of an evaluator context: disjoint / multi-level / multi-position tuple
 * detection and the slicer member map, used to route between the member-based and tuple-based
 * constraint paths.
 *
 * @author av
 * @since Nov 21, 2005
 */
public class SlicerAnalyzer {

    /** Utility class */
  private SlicerAnalyzer() {
  }

  public static boolean useTupleSlicer( RolapEvaluator evaluator ) {
    return evaluator.isDisjointSlicerTuple() || evaluator.isMultiLevelSlicerTuple();
  }
  public static boolean isDisjointTuple( TupleList tupleList ) {
    //This assumes the same level for each hierarchy;
    //won't work if the level restriction is eliminated
    List<Set<Member>> counters = new ArrayList<>( tupleList.getArity() );
    for ( int i = 0; i < tupleList.size(); i++ ) {
      final List<Member> tuple = tupleList.get( i );
      for ( int j = 0; j < tupleList.getArity(); j++ ) {
        final Member member = tuple.get( j );
        if ( i == 0 ) {
          counters.add( new HashSet<>() );
        }
        counters.get( j ).add( member );
      }
    }
    int piatory = 1;
    for ( Set<Member> counter : counters ) {
      piatory *= counter.size();
    }
    return tupleList.size() < piatory;
  }

  public static boolean hasMultipleLevelSlicer( Evaluator evaluator ) {
    Map<Dimension, Level> levels = new HashMap<>();
    List<Member> slicerMembers =
        CalculatedMemberExpander.expandSupportedCalculatedMembers( ( (RolapEvaluator) evaluator ).getSlicerMembers(), evaluator ).getMembers();
    for ( Member member : slicerMembers ) {
      if ( member.isAll() ) {
        continue;
      }
      Level before = levels.put( member.getDimension(), member.getLevel() );
      if ( before != null && !before.equals( member.getLevel() ) ) {
        return true;
      }
    }
    return false;
  }
  /**
   * Gets a map of Expression to the set of sliced members associated with each expression.
   *
   * This map is used by addContextConstraint() to get the set of slicer members associated with each column in the cell
   * request's constrained columns array, {@link CellRequest#getConstrainedColumns}
   */
  // package-visible: reused by SqlContextConstraint.toContribution
  static Map<SqlExpression, Set<RolapMember>> getSlicerMemberMap( Evaluator evaluator ) {
    Map<SqlExpression, Set<RolapMember>> mapOfSlicerMembers =
        new HashMap<>();
    List<Member> slicerMembers = ( (RolapEvaluator) evaluator ).getSlicerMembers();
    List<Member> expandedSlicers =
        evaluator.isEvalAxes() ? CalculatedMemberExpander.expandSupportedCalculatedMembers( slicerMembers, evaluator.push() ).getMembers()
            : slicerMembers;

    if ( hasMultiPositionSlicer( expandedSlicers ) ) {
      for ( Member slicerMember : expandedSlicers ) {
        if ( slicerMember.isMeasure() ) {
          continue;
        }
        addSlicedMemberToMap( mapOfSlicerMembers, slicerMember );
      }
    }
    return mapOfSlicerMembers;
  }

  /**
   * Adds the slicer member and all parent members to mapOfSlicerMembers capturing the sliced members associated with an
   * Expression.
   *
   */
  private static void addSlicedMemberToMap( Map<SqlExpression, Set<RolapMember>> mapOfSlicerMembers,
      Member slicerMember ) {
    if ( slicerMember == null || slicerMember.isAll() || slicerMember.isNull() ) {
      return;
    }
    assert slicerMember instanceof RolapMember;
    SqlExpression expression = ( (RolapLevel) slicerMember.getLevel() ).getKeyExp();
    mapOfSlicerMembers.computeIfAbsent(expression, k -> new LinkedHashSet<RolapMember>()).add( (RolapMember) slicerMember );
    addSlicedMemberToMap( mapOfSlicerMembers, slicerMember.getParentMember() );
  }

  public static boolean hasMultiPositionSlicer( List<Member> slicerMembers ) {
    Map<Hierarchy, Member> mapOfSlicerMembers = new HashMap<>();
    for ( Member slicerMember : slicerMembers ) {
      Hierarchy hierarchy = slicerMember.getHierarchy();
      if ( mapOfSlicerMembers.containsKey( hierarchy ) ) {
        // We have found a second member in this hierarchy
        return true;
      }
      mapOfSlicerMembers.put( hierarchy, slicerMember );
    }
    return false;
  }
}
