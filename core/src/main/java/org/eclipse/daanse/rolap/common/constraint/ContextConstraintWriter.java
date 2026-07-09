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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.agg.AndPredicate;
import org.eclipse.daanse.rolap.common.agg.MemberColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.OrPredicate;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.element.MultiCardinalityDefaultMember;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy.LimitedRollupMember;
import org.eclipse.daanse.rolap.element.RolapLevel;
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

}
