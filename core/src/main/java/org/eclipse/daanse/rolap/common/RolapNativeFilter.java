/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.NativeEvaluator;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.MdxVisitorImpl;
import org.eclipse.daanse.rolap.common.TupleReader.MemberBuilder;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;

/**
 * Computes a Filter(set, condition) in SQL.
 *
 * @author av
 * @since Nov 21, 2005
 */
public class RolapNativeFilter extends RolapNativeSet {

  public RolapNativeFilter(boolean enableNativeFilter) {
    super.setEnabled( enableNativeFilter );
  }

  static class FilterConstraint extends SetConstraint {
    Expression filterExpr;

    public FilterConstraint( CrossJoinArg[] args, RolapEvaluator evaluator, Expression filterExpr ) {
      super( args, evaluator, true );
      this.filterExpr = filterExpr;
    }

    /**
     * {@inheritDoc}
     *
     *
     * Overriding isJoinRequired() for native filters because we have to force a join to the fact table if the filter
     * expression references a measure.
     */
    @Override
	protected boolean isJoinRequired() {
      // Use a visitor and check all member expressions.
      // If any of them is a measure, we will have to
      // force the join to the fact table. If it is something
      // else then we don't really care. It will show up in
      // the evaluator as a non-all member and trigger the
      // join when we call RolapNativeSet.isJoinRequired().
      final AtomicBoolean mustJoin = new AtomicBoolean( false );
      filterExpr.accept( new MdxVisitorImpl() {
        @Override
		public Object visitMemberExpression( MemberExpression memberExpr ) {
          if ( memberExpr.getMember().isMeasure() ) {
            mustJoin.set( true );
            return null;
          }
          return super.visitMemberExpression( memberExpr );
        }
      } );
      return mustJoin.get() || ( getEvaluator().isNonEmpty() && super.isJoinRequired() );
    }

    @Override
	public void addConstraint( SqlQuery sqlQuery, RolapCube baseCube, AggStar aggStar ) {
      // Use aggregate table to generate filter condition
      RolapNativeSql sql = new RolapNativeSql( sqlQuery, aggStar, getEvaluator(), args[0].getLevel() );
      StringBuilder filterSql = sql.generateFilterCondition( filterExpr );
      if ( filterSql != null ) {
        sqlQuery.addHaving( filterSql.toString() );
      }

      if ( getEvaluator().isNonEmpty() || isJoinRequired() ) {
        // only apply context constraint if non empty, or
        // if a join is required to fulfill the filter condition
        super.addConstraint( sqlQuery, baseCube, aggStar );
      }
    }

    public boolean isSuported( Context<?> context ) {
      RolapEvaluator evaluator = this.getEvaluator();
      SqlQuery testQuery = SqlQuery.newQuery( context, "testQuery" );
      SqlTupleReader sqlTupleReader = new SqlTupleReader( this );

      Role role = evaluator.getCatalogReader().getRole();
      RolapCatalogReader reader = new RolapCatalogReader( context,role, (RolapCatalog) evaluator.getCatalogReader().getCatalog() );

      for ( CrossJoinArg arg : args ) {
        addLevel( sqlTupleReader, reader, arg );
      }

      RolapCube cube = (RolapCube) evaluator.getCube();
      this.addConstraint( testQuery, cube, sqlTupleReader.chooseAggStar( this, evaluator, cube,
          context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class) ) );
      return testQuery.isSupported();
    }

    private void addLevel( TupleReader tr, RolapCatalogReader schemaReader, CrossJoinArg arg ) {
      RolapLevel level = arg.getLevel();
      if ( level == null ) {
        // Level can be null if the CrossJoinArg represent
        // an empty set.
        // This is used to push down the "1 = 0" predicate
        // into the emerging CJ so that the entire CJ can
        // be natively evaluated.
        tr.incrementEmptySets();
        return;
      }

      RolapHierarchy hierarchy = level.getHierarchy();
      MemberReader mr = schemaReader.getMemberReader( hierarchy );
      MemberBuilder mb = mr.getMemberBuilder();
      Util.assertTrue( mb != null, "MemberBuilder not found" );

      tr.addLevelMembers( level, mb, null );
    }

    @Override
	public Object getCacheKey() {
      List<Object> key = new ArrayList<>();
      key.add( super.getCacheKey() );
      // Note required to use string in order for caching to work
      if ( filterExpr != null ) {
        key.add( filterExpr.toString() );
      }
      key.add( getEvaluator().isNonEmpty() );

      if ( this.getEvaluator() instanceof RolapEvaluator ) {
        key.add( ( (RolapEvaluator) this.getEvaluator() ).getSlicerMembers() );
      }

      return key;
    }
  }

  @Override
protected boolean restrictMemberTypes() {
    return true;
  }

  @Override
NativeEvaluator createEvaluator( RolapEvaluator evaluator, FunctionDefinition fun, Expression[] args, final boolean enableNativeFilter ) {
    if ( !isEnabled() ) {
      return null;
    }
    if ( !FilterConstraint.isValidContext( evaluator, restrictMemberTypes() ) ) {
      return null;
    }
    // is this "Filter(<set>, <numeric expr>)"
    String funName = fun.getFunctionMetaData().operationAtom().name();
    if ( !"Filter".equalsIgnoreCase( funName ) ) {
      return null;
    }

    if ( args.length != 2 ) {
      return null;
    }

    // extract the set expression
    List<CrossJoinArg[]> allArgs = crossJoinArgFactory().checkCrossJoinArg( evaluator, args[0], enableNativeFilter );

    // checkCrossJoinArg returns a list of CrossJoinArg arrays. The first
    // array is the CrossJoin dimensions. The second array, if any,
    // contains additional constraints on the dimensions. If either the
    // list or the first array is null, then native cross join is not
    // feasible.
    if ( allArgs == null || allArgs.isEmpty() || allArgs.get( 0 ) == null ) {
      return null;
    }

    CrossJoinArg[] cjArgs = allArgs.get( 0 );
    if ( isPreferInterpreter( cjArgs, false ) ) {
      return null;
    }

    // extract "order by" expression
    CatalogReader schemaReader = evaluator.getCatalogReader();
    Context<?> context = schemaReader.getContext();
    // generate the WHERE condition
    // Need to generate where condition here to determine whether
    // or not the filter condition can be created. The filter
    // condition could change to use an aggregate table later in evaluation
    SqlQuery sqlQuery = SqlQuery.newQuery( context, "NativeFilter" );
    RolapNativeSql sql = new RolapNativeSql( sqlQuery, null, evaluator, cjArgs[0].getLevel() );
    final Expression filterExpr = args[1];
    StringBuilder filterExprStr = sql.generateFilterCondition( filterExpr );
    if ( filterExprStr == null ) {
      return null;
    }

    // Check to see if evaluator contains a calculated member that can't be
    // expanded. This is necessary due to the SqlConstraintsUtils.
    // addContextConstraint()
    // method which gets called when generating the native SQL.
    if ( SqlConstraintUtils.containsCalculatedMember( Arrays.asList( evaluator.getNonAllMembers() ), true ) ) {
      return null;
    }

    final int savepoint = evaluator.savepoint();
    try {
      overrideContext( evaluator, cjArgs, sql.getStoredMeasure() );

      // no need to have any context if there is no measure, we are doing
      // a filter only on the current dimension. This prevents
      // SqlContextConstraint from expanding unnecessary calculated
      // members on the
      // slicer calling expandSupportedCalculatedMembers
      if ( !evaluator.isNonEmpty() && sql.getStoredMeasure() == null ) {
        // No need to have anything on the context
        for ( Member m : evaluator.getMembers() ) {
          evaluator.setContext( m.getLevel().getHierarchy().getDefaultMember() );
        }
      }
      // Now construct the TupleConstraint that contains both the CJ
      // dimensions and the additional filter on them.
      CrossJoinArg[] combinedArgs = cjArgs;
      if ( allArgs.size() == 2 ) {
        CrossJoinArg[] predicateArgs = allArgs.get( 1 );
        if ( predicateArgs != null ) {
          // Combined the CJ and the additional predicate args.
          combinedArgs = Util.appendArrays( cjArgs, predicateArgs );
        }
      }

      FilterConstraint constraint = new FilterConstraint( combinedArgs, evaluator, filterExpr );

      if ( !constraint.isSuported( context ) ) {
        return null;
      }

      LOGGER.debug( "using native filter" );
      return new SetEvaluator( cjArgs, schemaReader, constraint );
    } finally {
      evaluator.restore( savepoint );
    }
  }
}
