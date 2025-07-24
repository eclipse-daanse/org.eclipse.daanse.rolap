/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.element;

import java.util.List;

import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.common.MemberBase;
import org.eclipse.daanse.olap.util.type.TypeWrapperExp;
import org.eclipse.daanse.rolap.common.RolapEvaluatorRoot;
import org.eclipse.daanse.rolap.common.RolapResult.ValueFormatter;

/**
 * Member which holds the AggregateCalc used when evaluating a compound slicer. This is used to better handle some
 * cases where calculated members elsewhere in the query can override the context of the slicer members. See
 * MONDRIAN-1226.
 */
public class CompoundSlicerRolapMember extends DelegatingRolapMember implements RolapMeasure {
  private final Calc calc;
  private final ValueFormatter valueFormatter;
  private final TupleList tupleList;
  private final int solveOrder;

  public CompoundSlicerRolapMember( RolapMember placeholderMember, Calc calc, ValueFormatter formatter,
      TupleList tupleList, int solveOrder ) {
    super( placeholderMember );
    this.calc = calc;
    valueFormatter = formatter;
    this.tupleList = tupleList;
    this.solveOrder = solveOrder;
  }

  @Override
  public boolean isEvaluated() {
    return true;
  }

  @Override
  public Expression getExpression() {
    return new TypeWrapperExp( calc.getType() );
  }

  @Override
  public Calc getCompiledExpression( RolapEvaluatorRoot root ) {
    return calc;
  }

  /**
   * CompoundSlicerRolapMember is always wrapped inside a CacheCalc.  To maximize the benefit
   * of the CacheCalc and the expression cache, the solve order of the CompoundSlicerRolapMember
   * should be lower than all other calculations.
   *
   */
  @Override
  public int getSolveOrder() {
    return solveOrder;
  }

  @Override
  public boolean isOnSameHierarchyChain( Member otherMember ) {
    return isOnSameHierarchyChainInternal( (MemberBase) otherMember );
  }

  @Override
  public boolean isOnSameHierarchyChainInternal( MemberBase member2 ) {
    // Stores the index of the corresponding member in each tuple
    int index = -1;
    for ( List<org.eclipse.daanse.olap.api.element.Member> subList : tupleList ) {
      if ( index == -1 ) {
        if (!subList.isEmpty() && member2.getHierarchy().equals( subList.get( 0 ).getHierarchy() ) ) {
                index = 0;
        }
        if ( index == -1 ) {
          return false; // member2's hierarchy not present in tuple
        }
      }
      if ( member2.isOnSameHierarchyChainInternal( (MemberBase) subList.get( index ) ) ) {
        return true;
      }
    }
    return false;
  }

  @Override
	public ValueFormatter getFormatter() {
    return valueFormatter;
  }
}

