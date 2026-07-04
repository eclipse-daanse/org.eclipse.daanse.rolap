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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.olap.api.calc.tuple.TupleIterable;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.TupleCollections;
import org.eclipse.daanse.olap.function.def.aggregate.AggregateFunDef;
import org.eclipse.daanse.olap.function.def.parentheses.ParenthesesFunDef;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.element.CompoundSlicerRolapMember;
import org.eclipse.daanse.rolap.element.RolapMemberBase;
import org.eclipse.daanse.rolap.util.FilteredIterableList;

/**
 * Expands supported calculated members (Aggregate, "+", parentheses, member expressions) into the
 * plain members and tuple lists a SQL constraint can be generated from, and removes calculated and
 * default members from constraint member sets.
 *
 * @author av
 * @since Nov 21, 2005
 */
public class CalculatedMemberExpander {

    /** Utility class */
  private CalculatedMemberExpander() {
  }

  public static TupleConstraintStruct expandSupportedCalculatedMembers( List<Member> members, Evaluator evaluator ) {
    return expandSupportedCalculatedMembers( members, evaluator, false );
  }

  public static TupleConstraintStruct expandSupportedCalculatedMembers( List<Member> members, Evaluator evaluator,
      boolean disjointSlicerTuples ) {
    TupleConstraintStruct expandedSet = new TupleConstraintStruct();
    for ( Member member : members ) {
      expandSupportedCalculatedMember( member, evaluator, disjointSlicerTuples, expandedSet );
    }
    return expandedSet;
  }

  public static void expandSupportedCalculatedMember( Member member, Evaluator evaluator,
      TupleConstraintStruct expandedSet ) {
    expandSupportedCalculatedMember( member, evaluator, false, expandedSet );
  }

  public static void expandSupportedCalculatedMember( Member member, Evaluator evaluator, boolean disjointSlicerTuples,
      TupleConstraintStruct expandedSet ) {
    if ( member.isCalculated() && isSupportedCalculatedMember( member ) ) {
      expandExpressions( member, null, evaluator, expandedSet );
    } else if ( member instanceof CompoundSlicerRolapMember ) {
      if ( !disjointSlicerTuples ) {
        expandedSet.addMember( replaceCompoundSlicerPlaceholder( member, (RolapEvaluator) evaluator ) );
      }
    } else {
      // just the member
      expandedSet.addMember( member );
    }
  }

  public static Member replaceCompoundSlicerPlaceholder( Member member, RolapEvaluator evaluator ) {
    Set<Member> slicerMembers = evaluator.getSlicerMembersByHierarchy().get( member.getHierarchy() );
    if ( slicerMembers != null && !slicerMembers.isEmpty() ) {
      return slicerMembers.iterator().next();
    }
    return member;
  }

  public static void expandExpressions( Member member, Expression expression, Evaluator evaluator,
      TupleConstraintStruct expandedSet ) {
    if ( expression == null ) {
      expression = member.getExpression();
    }
    if ( expression instanceof ResolvedFunCallImpl fun ) {
      if ( fun.getFunDef() instanceof ParenthesesFunDef ) {
        assert ( fun.getArgCount() == 1 );
        expandExpressions( member, fun.getArg( 0 ), evaluator, expandedSet );
      } else if ( fun.getOperationAtom().name().equals( "+" ) ) {
        Expression[] expressions = fun.getArgs();
        for ( Expression innerExp : expressions ) {
          expandExpressions( member, innerExp, evaluator, expandedSet );
        }
      } else {
        // Extract the list of members
        expandSetFromCalculatedMember( evaluator, member, expandedSet );
      }
    } else if ( expression instanceof MemberExpression memberExpr) {
      expandedSet.addMember( memberExpr.getMember() );
    } else {
      expandedSet.addMember( member );
    }
  }

  /**
   * Check to see if this is in a list of supported calculated members. Currently, only the Aggregate and the + function
   * is supported.
   *
   * @return <i>true</i> if the calculated member is supported for native evaluation
   */
  public static boolean isSupportedCalculatedMember( final Member member ) {
    // Is it a supported function?
    return isSupportedExpressionForCalculatedMember( member.getExpression() );
  }

  public static boolean isSupportedExpressionForCalculatedMember( final Expression expression ) {
    if ( expression instanceof ResolvedFunCallImpl fun ) {
      if ( fun.getFunDef() instanceof AggregateFunDef ) {
        return true;
      }

      if ( fun.getFunDef() instanceof ParenthesesFunDef ) {
        if ( fun.getArgs().length == 1 ) {
          for ( Expression argsExp : fun.getArgs() ) {
            if ( !isSupportedExpressionForCalculatedMember( argsExp ) ) {
              return false;
            }
          }
        }
        return true;
      }

      if ( fun.getFunDef().getFunctionMetaData().operationAtom().name().equals( "+" ) ) {
        for ( Expression argsExp : fun.getArgs() ) {
          if ( !isSupportedExpressionForCalculatedMember( argsExp ) ) {
            return false;
          }
        }
        return true;
      }
    }

    return  expression instanceof MemberExpression;
  }

  public static void expandSetFromCalculatedMember( Evaluator evaluator, Member member,
      TupleConstraintStruct expandedSet ) {
    if (!(member.getExpression() instanceof ResolvedFunCallImpl)) {
        throw new IllegalArgumentException("Expression should be instanceof ResolvedFunCall");
    }

    ResolvedFunCallImpl fun = (ResolvedFunCallImpl) member.getExpression();

    // Calling the main set evaluator to extend this.
    Expression exp = fun.getArg( 0 );
    TupleIterable tupleIterable = evaluator.getSetEvaluator( exp, true ).evaluateTupleIterable();

    TupleList tupleList = TupleCollections.materialize( tupleIterable, false );

    boolean disjointSlicerTuple = false;
    if ( tupleList != null ) {
      disjointSlicerTuple = SlicerAnalyzer.isDisjointTuple( tupleList );
    }

    if ( disjointSlicerTuple ) {
      if ( !tupleList.isEmpty() ) {
        expandedSet.addTupleList( tupleList );
      }
    } else {
      for (List<Member> element : tupleIterable) {
        expandedSet.addMembers( element );
      }
    }
  }

  /**
   * Gets a list of unique ordinal cube members to make sure our cell request isn't unsatisfiable, following the same
   * logic as RolapEvaluator
   *
   * @return Unique ordinal cube members
   */
  protected static List<Member> getUniqueOrdinalMembers( List<Member> members ) {
    ArrayList<Integer> currentOrdinals = new ArrayList<>();
    ArrayList<Member> uniqueMembers = new ArrayList<>();

    for ( Member member : members ) {
      final RolapMemberBase m = (RolapMemberBase) member;
      int ordinal = m.getHierarchyOrdinal();
      if ( !currentOrdinals.contains( ordinal ) ) {
        uniqueMembers.add( member );
        currentOrdinals.add( ordinal );
      }
    }

    return uniqueMembers;
  }

  protected static Member[] expandMultiPositionSlicerMembers( Member[] members, Evaluator evaluator ) {
    Map<Hierarchy, Set<Member>> mapOfSlicerMembers = null;
    if ( evaluator instanceof RolapEvaluator rolapEvaluator) {
      // get the slicer members from the evaluator
      mapOfSlicerMembers = rolapEvaluator.getSlicerMembersByHierarchy();
    }
    if ( mapOfSlicerMembers != null ) {
      List<Member> listOfMembers = new ArrayList<>();
      // Iterate the given list of members, removing any whose hierarchy
      // has multiple members on the slicer axis
      for ( Member member : members ) {
        Hierarchy hierarchy = member.getHierarchy();
        if ( !mapOfSlicerMembers.containsKey( hierarchy ) || mapOfSlicerMembers.get( hierarchy ).size() < 2
            || member instanceof CompoundSlicerRolapMember ) {
          listOfMembers.add( member );
        } else {
          listOfMembers.addAll( mapOfSlicerMembers.get( hierarchy ) );
        }
      }
      members = listOfMembers.toArray( new Member[listOfMembers.size()] );
    }
    return members;
  }

  /**
   * Removes calculated and default members from an array.
   *
   * 
   * This is required only if the default member is not the ALL member. The time dimension for example, has 1997 as
   * default member. When we evaluate the query
   *
   *
   *   select NON EMPTY crossjoin(
   *     {[Time].[1998]}, [Customer].[All].children
   *  ) on columns
   *   from [sales]
   *
   *
   * the [Customer].[All].children is evaluated with the default member [Time].[1997] in the
   * evaluator context. This is wrong because the NON EMPTY must filter out Customers with no rows in the fact table for
   * 1998 not 1997. So we do not restrict the time dimension and fetch all children.
   *
   * Package visibility level used for testing
   *
   * 
   * For calculated members, effect is the same as {@link #removeCalculatedMembers(java.util.List)}.
   *
   * @param members
   *          Array of members
   * @return Members with calculated members removed (except those that are leaves in a parent-child hierarchy) and with
   *         members that are the default member of their hierarchy
   */
  public static List<Member> removeCalculatedAndDefaultMembers( List<Member> members ) {
    List<Member> memberList = new ArrayList<>( members.size() );
    Iterator<Member> iterator = members.iterator();
    if ( iterator.hasNext() ) {
      Member firstMember = iterator.next();
      if ( !isMemberCalculated( firstMember ) ) {
        memberList.add( firstMember );
      }

      Member curMember;
      while ( iterator.hasNext() ) {
        curMember = iterator.next();
        if ( !isMemberCalculated( curMember ) && !isMemberDefault( curMember ) ) {
          memberList.add( curMember );
        }
      }
    }

    return memberList;
  }

  private static boolean isMemberCalculated( Member member ) {
    // Skip calculated members (except if leaf of parent-child hier)
    return member.isCalculated() && !member.isParentChildLeaf();
  }

  private static boolean isMemberDefault( Member member ) {
    // Remove members that are the default for their hierarchy,
    // except for the measures hierarchy.
    return member.getHierarchy().getDefaultMember().equals( member );
  }

  public static List<Member> removeCalculatedMembers( List<Member> members ) {
    return new FilteredIterableList<>( members, m -> !m.isCalculated() || m.isParentChildPhysicalMember() );
  }

  public static boolean containsCalculatedMember( List<Member> members ) {
    return containsCalculatedMember( members, false );
  }

  public static boolean containsCalculatedMember( List<Member> members, boolean allowExpandableMembers ) {
    for ( Member member : members ) {
      if ( member.isCalculated() ) {
        if ( allowExpandableMembers ) {
          if ( !isSupportedCalculatedMember( member ) ) {
            return true;
          }
        } else {
          return true;
        }
      }
    }
    return false;
  }
}
