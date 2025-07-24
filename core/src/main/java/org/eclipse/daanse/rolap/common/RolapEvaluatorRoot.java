/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2021 Hitachi Vantara..  All rights reserved.
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Connection;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.Execution;
import org.eclipse.daanse.olap.api.Statement;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.ResultStyle;
import org.eclipse.daanse.olap.api.calc.compiler.ParameterSlot;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.NamedSet;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.common.SolveOrderMode;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.eclipse.daanse.rolap.element.RolapMemberBase;

/**
 * Context at the root of a tree of evaluators.
 *
 *
 * Contains the context that does not change as evaluation context is pushed/popped.
 *
 * @author jhyde
 * @since Nov 11, 2008
 */
public class RolapEvaluatorRoot {
  final Map<Object, Object> expResultCache = new HashMap<>();
  final Map<Object, Object> tmpExpResultCache = new HashMap<>();
  final RolapCube cube;
  final Connection connection;
  final CatalogReader schemaReader;
  final Map<CompiledExpKey, Calc> compiledExps = new HashMap<>();
  final Statement statement;
  final Query query;
  private final Date queryStartTime;

  int expResultCacheHitCount;
  int expResultCacheMissCount;

  /**
   * Default members of each hierarchy, from the schema reader's perspective. Finding the default member is moderately
   * expensive, but happens very often.
   */
  public final RolapMember[] defaultMembers;
  final int[] nonAllPositions;
  int nonAllPositionCount;

  SolveOrderMode solveOrderMode;

  final Set<Expression> activeNativeExpansions = new HashSet<>();

  /**
   * The size of the command stack at which we will next check for recursion.
   */
  int recursionCheckCommandCount;
  public final Execution execution;

  /**
   * Creates a RolapEvaluatorRoot.
   *
   * @param statement
   *          statement
   * @deprecated
   */
  @Deprecated
public RolapEvaluatorRoot( Statement statement ) {
    this( statement, null );
  }

  public RolapEvaluatorRoot( Execution execution ) {
    this( execution.getMondrianStatement(), execution );
  }

  private RolapEvaluatorRoot( Statement statement, Execution execution ) {
    this.execution = execution;
    this.statement = statement;
    this.query = statement.getQuery();
    this.cube = (RolapCube) query.getCube();
    this.connection = statement.getMondrianConnection();
    this.solveOrderMode =
        Util.lookup( SolveOrderMode.class, connection.getContext()
                .getConfigValue(ConfigConstants.SOLVE_ORDER_MODE, ConfigConstants.SOLVE_ORDER_MODE_DEFAULT_VALUE, String.class)
                .toUpperCase(),
            SolveOrderMode.ABSOLUTE );
    this.schemaReader = query.getCatalogReader( true );
    this.queryStartTime = new Date();
    List<RolapMember> list = new ArrayList<>();
    nonAllPositions = new int[cube.getHierarchies().size()];
    nonAllPositionCount = 0;
    for ( Hierarchy hierarchy : cube.getHierarchies() ) {
      RolapMember defaultMember = (RolapMember) schemaReader.getHierarchyDefaultMember( hierarchy );
      assert defaultMember != null;

      if ( ScenarioImpl.isScenario( hierarchy ) && connection.getScenario() != null ) {
        defaultMember = (RolapMember) ( (ScenarioImpl) connection.getScenario() ).getMember();
      }

      // This fragment is a concurrency bottleneck, so use a cache of
      // hierarchy usages.
      final HierarchyUsage hierarchyUsage = cube.getFirstUsage( hierarchy );
      if ( hierarchyUsage != null ) {
        if ( defaultMember instanceof RolapMemberBase ) {
          ( (RolapMemberBase) defaultMember ).makeUniqueName( hierarchyUsage );
        }
      }

      list.add( defaultMember );
      if ( !defaultMember.isAll() ) {
        nonAllPositions[nonAllPositionCount] = hierarchy.getOrdinalInCube();
        nonAllPositionCount++;
      }
    }
    this.defaultMembers = list.toArray( new RolapMember[list.size()] );

    this.recursionCheckCommandCount = ( defaultMembers.length << 4 );
  }

  /**
   * Implements a cheap-and-cheerful mapping from expressions to compiled expressions.
   *
   *
   * TODO: Save compiled expressions somewhere better.
   *
   * @param exp
   *          Expression
   * @param scalar
   *          Whether expression is scalar
   * @param resultStyle
   *          Preferred result style; if null, use query's default result style; ignored if expression is scalar
   * @return compiled expression
   */
  public final Calc getCompiled( Expression exp, boolean scalar, ResultStyle resultStyle ) {
    CompiledExpKey key = new CompiledExpKey( exp, scalar, resultStyle );
    Calc calc = compiledExps.get( key );
    if ( calc == null ) {
      calc = statement.getQuery().compileExpression( exp, scalar, resultStyle );
      compiledExps.put( key, calc );
    }
    return calc;
  }

  /**
   * Just a simple key of Exp/scalar/resultStyle, used for keeping compiled expressions. Previous to the introduction of
   * this class, the key was a list constructed as Arrays.asList(exp, scalar, resultStyle) and having poorer performance
   * on equals, hashCode, and construction.
   */
  private static class CompiledExpKey {
    private final Expression exp;
    private final boolean scalar;
    private final ResultStyle resultStyle;
    private int hashCode = Integer.MIN_VALUE;

    private CompiledExpKey( Expression exp, boolean scalar, ResultStyle resultStyle ) {
      this.exp = exp;
      this.scalar = scalar;
      this.resultStyle = resultStyle;
    }

    @Override
	public boolean equals( Object other ) {
      if ( this == other ) {
        return true;
      }
      if ( !( other instanceof CompiledExpKey otherKey ) ) {
        return false;
      }
      return this.scalar == otherKey.scalar && this.resultStyle == otherKey.resultStyle && this.exp.equals(
          otherKey.exp );
    }

    @Override
	public int hashCode() {
      if ( hashCode != Integer.MIN_VALUE ) {
        return hashCode;
      } else {
        int hash = 0;
        hash = Util.hash( hash, scalar );
        hash = Util.hash( hash, resultStyle );
        this.hashCode = Util.hash( hash, exp );
      }
      return this.hashCode;
    }
  }

  /**
   * Evaluates a named set.
   *
   *
   * The default implementation throws {@link UnsupportedOperationException}.
   *
   * @param namedSet
   *          Named set
   * @param create
   *          Whether to create named set evaluator if not found
   */
  protected Evaluator.NamedSetEvaluator evaluateNamedSet( NamedSet namedSet, boolean create ) {
    throw new UnsupportedOperationException();
  }

  /**
   * Evaluates a named set represented by an expression.
   *
   *
   * The default implementation throws {@link UnsupportedOperationException}.
   *
   * @param exp
   *          Expression
   * @param create
   *          Whether to create named set evaluator if not found
   */
  protected Evaluator.SetEvaluator evaluateSet( Expression exp, boolean create ) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the value of a parameter, evaluating its default expression if necessary.
   *
   *
   * The default implementation throws {@link UnsupportedOperationException}.
   */
  public Object getParameterValue( ParameterSlot slot ) {
    throw new UnsupportedOperationException();
  }

  /**
   * Puts result in cache.
   *
   * @param key
   *          key
   * @param result
   *          value to be cached
   * @param isValidResult
   *          indicate if this result is valid
   */
  public final void putCacheResult( Object key, Object result, boolean isValidResult ) {
    if ( isValidResult ) {
      expResultCache.put( key, result );
    } else {
      tmpExpResultCache.put( key, result );
    }
  }

  /**
   * Gets result from cache.
   *
   * @param key
   *          cache key
   * @return cached expression
   */
  public final Object getCacheResult( Object key ) {
    Object result = expResultCache.get( key );
    if ( result == null ) {
      result = tmpExpResultCache.get( key );
      expResultCacheMissCount++;
    } else {
      expResultCacheHitCount++; // Only count valid results
    }
    return result;
  }

  /**
   * Clears the expression result cache.
   *
   * @param clearValidResult
   *          whether to clear valid expression results
   */
  public final void clearResultCache( boolean clearValidResult ) {
    if ( clearValidResult ) {
      expResultCache.clear();
    }
    tmpExpResultCache.clear();
  }

  /**
   * Get query start time.
   *
   * @return the query start time
   */
  public Date getQueryStartTime() {
    return queryStartTime;
  }
}
