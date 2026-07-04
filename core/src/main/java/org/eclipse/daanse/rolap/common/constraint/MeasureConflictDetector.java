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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.fun.MemberExtractingVisitor;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;

/**
 * Detects whether measure calculations reference dimension members that conflict with the members
 * a native SQL constraint would be constructed from.
 *
 * @author av
 * @since Nov 21, 2005
 */
public class MeasureConflictDetector {

    /** Utility class */
  private MeasureConflictDetector() {
  }

  /**
   * Returns true if any measure calculations in the first arg references a dimension member with corresponding members
   * in the second arg which conflict with that member. A member "conflicts" if the member referenced by the measure is
   * not equal to or children of the corresponding dimension member. For example, given ( [unit sales], [Time].[1997].Q1
   * ) in the measures set, if the member [Time].[1997].[Q2] is in the members set this would conflict, since Q1 is not
   * equal to or a child of Q2.
   *
   * This method is used in native evaluation to determine whether any measures in the query could conflict with the SQL
   * constraint being constructed.
   */
  public static boolean measuresConflictWithMembers( Set<Member> measures, Member[] members ) {
    Set<Member> membersNestedInMeasures = getMembersNestedInMeasures( measures );
    for ( Member memberInMeasure : membersNestedInMeasures ) {
      if ( !anyMemberOverlaps( members, memberInMeasure ) ) {
        return true;
      }
    }
    return false;
  }

  public static Set<Member> getMembersNestedInMeasures( Set<Member> measures ) {
    Set<Member> membersNestedInMeasures = new HashSet<>();
    for ( Member m : measures ) {
      if ( m.isCalculated() ) {
        Expression exp = m.getExpression();
        exp.accept( new MemberExtractingVisitor( membersNestedInMeasures, null, false ) );
      }
    }
    return membersNestedInMeasures;
  }

  public static boolean measuresConflictWithMembers( Set<Member> measuresMembers, CrossJoinArg[] cjArgs ) {
    return measuresConflictWithMembers( measuresMembers, getCJArgMembers( cjArgs ) );
  }

  /**
   * Compares the array of members against memberInMeasure, returning true if any of the members are of the same
   * hierarchy and is either [All] or a equal to or a child of memberInMeasure.
   *
   * This is used to identify whether whether the memberInMeasure is "overlapped" by any of the members. For native
   * evaluation we need to make sure that a member included as a result of a calculated measure does not fall outside of
   * the set of members that will be used to constrain the native query, in which case we may exclude members
   * incorrectly.
   */
  private static boolean anyMemberOverlaps( Member[] members, Member memberInMeasure ) {
    boolean memberIsCovered = false;
    boolean encounteredHierarchy = false;
    for ( Member memberCheckedForConflict : members ) {
      final boolean sameHierarchy = memberInMeasure.getHierarchy().equals( memberCheckedForConflict.getHierarchy() );
      boolean childOrEqual = false;
      if ( sameHierarchy ) {
        encounteredHierarchy = true;
        childOrEqual =
            memberCheckedForConflict.isAll() || memberInMeasure.isChildOrEqualTo( memberCheckedForConflict );
      }
      if ( sameHierarchy && childOrEqual ) {
        memberIsCovered = true;
        break;
      }
    }
    return !encounteredHierarchy || memberIsCovered;
  }

  private static Member[] getCJArgMembers( CrossJoinArg[] cjArgs ) {
    Set<Member> members = new HashSet<>();
    for ( CrossJoinArg arg : cjArgs ) {
      if ( arg.getMembers() != null ) {
        members.addAll( arg.getMembers() );
      }
    }
    return members.toArray( new Member[members.size()] );
  }
}
