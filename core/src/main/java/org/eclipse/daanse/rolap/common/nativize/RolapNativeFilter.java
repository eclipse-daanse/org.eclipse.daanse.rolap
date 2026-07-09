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

package org.eclipse.daanse.rolap.common.nativize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.NativeEvaluator;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.MdxVisitorImpl;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.constraint.CalculatedMemberExpander;
import org.eclipse.daanse.rolap.common.constraint.ContextConstraintWriter;
import org.eclipse.daanse.rolap.common.constraint.SlicerAnalyzer;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.element.RolapCube;

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

  public static class FilterConstraint extends SetConstraint {
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
	public boolean isJoinRequired() {
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

    /**
     * The filter composition runs the expanded calc gate ({@link CalcLift#LIFTED}): a supported
     * calculated member in context or slicer is expanded in place rather than forcing non-native
     * evaluation. The {@code filter-base-context-empty} bail still applies to non-calc base shapes
     * (disjoined tuples, expression failures).
     */
    @Override
    public CalcLift executedCalcLift() {
      return CalcLift.LIFTED;
    }

    /**
     * Builds the filter's constraint contribution, reached from the base 2-arg
     * {@code toContribution} dispatch with {@link #executedCalcLift()}: the filter condition is
     * compiled through {@link RolapNativeSql#generateFilterPredicate}, so every condition shape
     * that channel supports (measure comparisons, {@code IsEmpty}, {@code NOT}, parentheses,
     * {@code AND}/{@code OR} trees, arithmetic over measures, calculated-member expansion,
     * {@code Name}/{@code Caption MATCHES}) yields a predicate node carried as {@code nativeHaving}
     * on top of the inherited {@link SetConstraint} contribution. The context is composed only when
     * the filter is non-empty or the condition forces the fact join; a pure dimension filter carries
     * the HAVING alone over the plain level snowflake (no fact join, no slicer WHERE — carrying them
     * would over-restrict). A null condition predicate lets the contribution proceed without a HAVING.
     * Returns {@link java.util.Optional#empty()} when the inherited context is required but not
     * expressible, or the condition compiler fails outright.
     */
    @Override
    protected java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> toContribution(
        RolapCube baseCube, AggStar aggStar, CalcLift lift ) {
      java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> having;
      try {
        // Compile against a scratch seam: the emitted node is self-contained (columns carry their table
        // aliases; a HAVING-alias dialect resolves the SELECT alias at render time), and the FROM side
        // effects are owned by the contribution (joinTables / factJoinRequired below).
        RolapNativeSql sql = new RolapNativeSql(
            NativeSqlContext.scratch( getEvaluator().getCatalogReader().getContext().getDialect() ),
            aggStar, getEvaluator(), args[0].getLevel() );
        having = java.util.Optional.ofNullable( sql.generateFilterPredicate( filterExpr ) );
      } catch ( RuntimeException e ) {
        LOGGER.debug( "FilterConstraint condition compile failed", e );
        return bail( "filter-condition-error" );
      }
      if ( getEvaluator().isNonEmpty() || isJoinRequired() ) {
        java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> base =
            super.toContribution( baseCube, aggStar, lift );
        if ( base.isEmpty() ) {
          return bail( "filter-base-context-empty" );
        }
        org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = base.get();
        // Carry the base's factJoinRequired — dropping it in this re-wrap would let the mapper's
        // same-dimension gate skip the fact join while the measure HAVING (sum(fact.col)) references
        // the fact. Carry the base's aggPlan too: the re-wrap must not strip the agg-join channel
        // of an agg-routed base contribution.
        org.eclipse.daanse.rolap.common.sql.ConstraintContribution rewrapped =
            new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
                c.where(), c.joinTables(), c.orderedPredicates(), c.memberKeyGroup(), c.nativeOrder(),
                having ).withFactJoinRequired( c.factJoinRequired() );
        return java.util.Optional.of( c.aggPlan().map( rewrapped::withAggPlan ).orElse( rewrapped ) );
      }
      // No context: just the condition HAVING over the plain level-members snowflake. Under an agg
      // routing the HAVING above was compiled agg-substituted (RolapNativeSql + aggStar), so the
      // translation is complete with no context predicates — a PRESENT, empty-predicates AggPlan
      // (the valid unconstrained plan), never an absent one.
      org.eclipse.daanse.rolap.common.sql.ConstraintContribution pureHaving =
          new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
              java.util.Optional.empty(), java.util.List.of(), java.util.List.of(),
              java.util.Optional.empty(), java.util.Optional.empty(), having );
      return java.util.Optional.of( aggStar == null ? pureHaving
          : pureHaving.withAggPlan( new org.eclipse.daanse.rolap.common.sql.AggPlan(
              aggStar, java.util.List.of() ) ) );
    }

    /**
     * The aggStar collapsed level-members WHERE gate (see {@code SetConstraint}): the inherited
     * context/args apply ONLY when the filter is non-empty or the condition forces the fact join;
     * otherwise the WHERE is genuinely empty (UNCONSTRAINED, the filter condition rides
     * {@link #levelMembersAggHaving} instead).
     */
    @Override
    public org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint.AggWhereResult
        levelMembersAggWhereCandidateState( AggStar aggStar ) {
      if ( !( getEvaluator().isNonEmpty() || isJoinRequired() ) ) {
        return org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint
            .AggWhereResult.UNCONSTRAINED;
      }
      return super.levelMembersAggWhereCandidateState( aggStar );
    }

    /**
     * The filter condition compiled against the AGG columns, so an aggStar collapsed level-members
     * read carries the native HAVING. Uses the same {@link RolapNativeSql#generateFilterPredicate}
     * channel + {@code aggStar} as {@link #toContribution}, for the collapsed level-members consumers
     * that call it directly. A null condition predicate yields {@link java.util.Optional#empty()}.
     */
    @Override
    public java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate>
        levelMembersAggHaving( AggStar aggStar ) {
      return levelMembersAggHavingWithJoins( aggStar ).having();
    }

    /**
     * As {@link #levelMembersAggHaving}, but compiled through a COLLECTING scratch context
     * ({@link NativeSqlContext#scratchCollecting}) so the compile's {@code addToFrom} side effects
     * — the filter-referenced dimension table's snowflake subset joined into the FROM whenever the
     * Caption/Name source does not resolve to an agg column — are returned alongside the predicate
     * for the agg mapper to replay. The predicate node is identical to the plain scratch compile's.
     * A failed compile yields the empty pair.
     */
    @Override
    public org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint.AggHaving
        levelMembersAggHavingWithJoins( AggStar aggStar ) {
      final java.util.List<org.eclipse.daanse.rolap.common.sqlbuild.TupleSqlMapper.HavingJoin> joins =
          new java.util.ArrayList<>();
      try {
        RolapNativeSql sql = new RolapNativeSql(
            NativeSqlContext.scratchCollecting(
                getEvaluator().getCatalogReader().getContext().getDialect(),
                ( hierarchy, expression ) -> joins.add(
                    new org.eclipse.daanse.rolap.common.sqlbuild.TupleSqlMapper.HavingJoin(
                        hierarchy, expression ) ) ),
            aggStar, getEvaluator(), args[0].getLevel() );
        return new org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint.AggHaving(
            java.util.Optional.ofNullable( sql.generateFilterPredicate( filterExpr ) ),
            java.util.List.copyOf( joins ) );
      } catch ( RuntimeException e ) {
        LOGGER.debug( "FilterConstraint agg HAVING compile failed", e );
        return new org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint.AggHaving(
            java.util.Optional.empty(), java.util.List.of() );
      }
    }

    /**
     * Whether this filter can be evaluated natively: it can, unless a member/context IN-list would
     * exceed the dialect's value-list limit.
     *
     * CONTRACT — the complete {@code setSupported(false)} inventory (the whole main tree has
     * exactly ONE producer):
     * <ul>
     *   <li>{@code MemberConstraintWriter.generateSingleValueInExpr} (the IN-list-limit gate):
     *       disables native evaluation iff {@code !dialect.supportsUnlimitedValueList()} and a
     *       per-level member group is a {@code ListColumnPredicate} with more than
     *       {@code SystemWideProperties.MaxConstraints} values (one predicate per member, so group
     *       size == member-list size; parent groups shrink walking up, so the INITIAL lists bound
     *       every group).</li>
     * </ul>
     * {@code RolapNativeSql.generateFilterPredicate} never touches this (a null predicate merely
     * skips the HAVING; condition compilability is pre-checked on a scratch context in
     * {@code createEvaluator}). The limit gate is reachable (under the same nonEmpty-or-join gate
     * as below) via exactly three member-list sources:
     * <ol>
     *   <li>each applicable cross-join arg's member list
     *       ({@code MemberListCrossJoinArg}/{@code DescendantsCrossJoinArg} →
     *       {@code addMemberConstraint}; the multi-level compound partitions sublists, so the arg
     *       list size bounds them),</li>
     *   <li>the per-column slicer member sets ({@code SlicerAnalyzer.getSlicerMemberMap} — the
     *       exact sets {@code ContextConstraintWriter.addContextConstraint} constrains),</li>
     *   <li>the role-access member lists ({@code ContextConstraintWriter.getRoleConstraintMembers}).</li>
     * </ol>
     * Hence: on unlimited-value-list dialects, and whenever every such list is within
     * {@code MaxConstraints}, this returns true. On a limited dialect with an oversized list it is
     * conservatively false even where the list could occasionally still be expressed (tuple-IN
     * branch, all/calculated skip groups, the disjoint-slicer tuple path that bypasses the
     * per-column loop) — those fall back to non-native evaluation: always correct, at most slower.
     */
    public boolean isSupported( Context<?> context ) {
      if ( context.getDialect().supportsUnlimitedValueList() ) {
        // The single limit producer cannot fire on an unlimited-value-list dialect.
        return true;
      }
      if ( !( getEvaluator().isNonEmpty() || isJoinRequired() ) ) {
        // Only the HAVING condition applies here — no member/context IN lists.
        return true;
      }
      final int maxConstraints = SystemWideProperties.instance().MaxConstraints;
      for ( CrossJoinArg arg : args ) {
        if ( canApplyCrossJoinArgConstraint( arg ) ) {
          List<RolapMember> argMembers = arg.getMembers();
          if ( argMembers != null && argMembers.size() > maxConstraints ) {
            return false;
          }
        }
      }
      RolapEvaluator evaluator = getEvaluator();
      for ( Set<RolapMember> slicerSet : SlicerAnalyzer.getSlicerMemberMap( evaluator ).values() ) {
        if ( slicerSet.size() > maxConstraints ) {
          return false;
        }
      }
      for ( List<RolapMember> roleMembers : ContextConstraintWriter.getRoleConstraintMembers(
          evaluator.getCatalogReader(), evaluator.getMembers() ).values() ) {
        if ( roleMembers.size() > maxConstraints ) {
          return false;
        }
      }
      return true;
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
    // Scratch context: this RolapNativeSql only generates the filter-condition node to test
    // convertibility; the node is discarded (the real HAVING is regenerated in
    // FilterConstraint.toContribution).
    RolapNativeSql sql = new RolapNativeSql(
        NativeSqlContext.scratch( context.getDialect() ), null, evaluator, cjArgs[0].getLevel() );
    final Expression filterExpr = args[1];
    if ( sql.generateFilterPredicate( filterExpr ) == null ) {
      return null;
    }

    // Check to see if evaluator contains a calculated member that can't be
    // expanded. This is necessary due to the SqlConstraintsUtils.
    // addContextConstraint()
    // method which gets called when generating the native SQL.
    if ( CalculatedMemberExpander.containsCalculatedMember( Arrays.asList( evaluator.getNonAllMembers() ), true ) ) {
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

      if ( !constraint.isSupported( context ) ) {
        return null;
      }

      LOGGER.debug( "using native filter" );
      return new SetEvaluator( cjArgs, schemaReader, constraint );
    } finally {
      evaluator.restore( savepoint );
    }
  }
}
