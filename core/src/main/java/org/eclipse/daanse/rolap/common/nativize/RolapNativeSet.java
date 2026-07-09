/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 TONBELLER AG
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2020 Hitachi Vantara and others
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.access.AccessHierarchy;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.calc.ResultStyle;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.NativeEvaluator;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.DelegatingTupleList;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.DelegatingCatalogReader;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.exceptions.ResultStyleException;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapAggregationManager;
import org.eclipse.daanse.rolap.common.SqlTupleReader;
import org.eclipse.daanse.rolap.common.TupleReader;
import org.eclipse.daanse.rolap.common.TupleReader.MemberBuilder;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.cache.HardSmartCache;
import org.eclipse.daanse.rolap.common.cache.SmartCache;
import org.eclipse.daanse.rolap.common.cache.SoftSmartCache;
import org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.member.MemberExcludeConstraint;
import org.eclipse.daanse.rolap.common.member.MemberReader;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArgFactory;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.MemberListCrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.element.MultiCardinalityDefaultMember;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyses set expressions and executes them in SQL if possible. Supports crossjoin, member.children, level.members and
 * member.descendants - all in non empty mode, i.e. there is a join to the fact table.
 *
 * TODO: the order of the result is different from the order of the
 * enumeration. Should sort.
 *
 * @author av
 * @since Nov 12, 2005
 */
public abstract class RolapNativeSet extends RolapNative {
  protected static final Logger LOGGER =
    LoggerFactory.getLogger( RolapNativeSet.class );

  private SmartCache<Object, TupleList> cache =
    new SoftSmartCache<>();

  /**
   * Returns whether certain member types (e.g. calculated members) should disable native SQL evaluation for
   * expressions
   * containing them.
   *
   * If true, expressions containing calculated members will be evaluated
   * by the interpreter, instead of using SQL.
   *
   * If false, calc members will be ignored and the computation will be
   * done in SQL, returning more members than requested.  This is ok, if the superflous members are filtered out in
   * java
   * code afterwards.
   *
   * @return whether certain member types should disable native SQL evaluation
   */
  protected abstract boolean restrictMemberTypes();

  protected CrossJoinArgFactory crossJoinArgFactory() {
    return new CrossJoinArgFactory( restrictMemberTypes() );
  }

  /**
   * Constraint for non empty {crossjoin, member.children, member.descendants, level.members}
   */
  public abstract static class SetConstraint extends SqlContextConstraint {
    public CrossJoinArg[] args;

    SetConstraint(
      CrossJoinArg[] args,
      RolapEvaluator evaluator,
      boolean strict ) {
      super( evaluator, strict );
      this.args = args;
    }

    /**
     * {@inheritDoc}
     *
     * If there is a crossjoin, we need to join the fact table - even if
     * the evaluator context is empty.
     */
    @Override
	public boolean isJoinRequired() {
      return args.length > 1 || super.isJoinRequired();
    }


    /** Diagnostic log paralleling {@code SqlContextConstraint.BAIL_LOG}: why THIS constraint's
     * contribution fell back — without it, silent early returns make fallbacks unattributable. */
    private static final org.slf4j.Logger SET_BAIL_LOG =
        org.slf4j.LoggerFactory.getLogger( "daanse.sql.gen.bail" );

    protected java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> bail( String reason ) {
      SET_BAIL_LOG.debug( "{} toContribution bail reason={}", getClass().getSimpleName(), reason );
      return java.util.Optional.empty();
    }

    /**
     * The SetConstraint/NECJ composition executes the expanded calc gate ({@link CalcLift#LIFTED}):
     * a supported calculated member in context or slicer is expanded in place rather than forcing
     * non-native evaluation. Routing parity lives in {@code SqlTupleReader.generateSelectForLevels}:
     * the SetConstraint-family multi-target reads execute through
     * {@code TupleSqlMapper.tupleLevelMembersSqlRecorderJoinOrder}.
     */
    @Override
    public CalcLift executedCalcLift() {
      return CalcLift.LIFTED;
    }

    /**
     * Builds the constraint contribution, reached from the base 2-arg {@code toContribution}
     * dispatch with {@link #executedCalcLift()}: the inherited context contribution PLUS each
     * applicable cross-join arg's member constraint. Plain {@link MemberListCrossJoinArg}s and
     * descendants args whose member set is expressible as a single {@code ColumnPredicate} are
     * modelled; anything inexpressible (multi-value / multi-level member sets, no context
     * contribution) returns {@link java.util.Optional#empty()} so the caller falls back to the
     * reference query.
     */
    @Override
    protected java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> toContribution(
        RolapCube baseCube, AggStar aggStar, CalcLift lift ) {
      java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> base =
          super.toContribution( baseCube, aggStar, lift );
      if ( base.isEmpty() ) {
        // The inherited context could not be expressed (a deep composition the calc gate could
        // not lift); the context bail below logs its own precise reason.
        return bail( "set-base-context-empty" );
      }
      org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = base.get();
      if ( aggStar != null && c.aggPlan().isEmpty() ) {
        // Defensive: an agg-routed context contribution always carries its plan (possibly with
        // empty predicates); a plan-less one cannot feed the agg-join channel.
        throw dead( "set-agg-base-no-plan" );
      }
      // The agg-join channel: the base context's provenance extended per arg, in the same order
      // the base predicates/args are appended. Null when not agg-routed.
      List<org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate> aggPredicates =
          aggStar == null ? null : new ArrayList<>( c.aggPlan().get().orderedAggPredicates() );
      List<org.eclipse.daanse.sql.statement.api.expression.Predicate> wheres = new ArrayList<>();
      c.where().ifPresent( wheres::add );
      List<org.eclipse.daanse.rolap.common.star.RolapStar.Table> joinTables = new ArrayList<>( c.joinTables() );
      List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> ordered =
          new ArrayList<>( c.orderedPredicates() );
      for ( CrossJoinArg arg : args ) {
        if ( !canApplyCrossJoinArgConstraint( arg ) ) {
          continue; // a non-applicable (calc-member) arg contributes no constraint
        }
        RolapLevel level = arg.getLevel();
        // levelIsOnBaseCube needs a non-null baseCube; the contribution path passes null (non-virtual), where
        // the level is on the cube — only apply the skip when a baseCube is available.
        if ( level != null && baseCube != null && !levelIsOnBaseCube( baseCube, level ) ) {
          continue;
        }
        // The member set + flags applied per arg type:
        //   MemberListCrossJoinArg -> (members, its restrictMemberTypes, its exclude)
        //   DescendantsCrossJoinArg -> ([member], restrictMemberTypes=true, exclude=false)
        List<RolapMember> argMembers;
        boolean argRestrict;
        boolean argExclude;
        if ( arg instanceof MemberListCrossJoinArg mlArg ) {
          argMembers = mlArg.getMembers();
          argRestrict = mlArg.isRestrictMemberTypes();
          argExclude = mlArg.isExclude();
        } else if ( arg instanceof org.eclipse.daanse.rolap.common.sql.DescendantsCrossJoinArg ) {
          argMembers = arg.getMembers(); // [member] (or null when the arg has no member -> adds nothing)
          argRestrict = true;
          argExclude = false;
        } else {
          // Other arg types are not modelled. SKIP (not bail): if such an arg adds a real constraint the
          // contribution is partial and the caller falls back to the reference query; if it adds nothing
          // (an all-member or projection arg) skipping preserves the inherited context match.
          continue;
        }
        if ( argMembers == null ) {
          continue; // the arg has no member dimension -> the recorded path also adds nothing
        }
        if ( argMembers.isEmpty() ) {
          // An EMPTY member-set arg restricts the result to nothing: the member constraint is the
          // always-false "(1 = 0)" conjunct ("(1 = 1)" for an exclude arg) instead of any column
          // restriction. The conjunct references no dimension column, so it is carried on the FACT
          // table: a fact-rooted ColumnPredicate adds no join steps of its own (the fact join is the
          // cross join's existence join, forced anyway) — only the WHERE conjunct, in arg order.
          org.eclipse.daanse.rolap.common.star.RolapStar star =
              baseCube != null ? baseCube.getStar() : getEvaluator().getCube().getStar();
          if ( star == null ) {
            // No star to hang the conjunct on (virtual cube without a fact) — cannot express it.
            throw dead( "set-arg-empty-members-no-star" );
          }
          org.eclipse.daanse.sql.statement.api.expression.Predicate alwaysFalse =
              org.eclipse.daanse.sql.statement.api.Predicates.raw( argExclude ? "(1 = 1)" : "(1 = 0)" );
          ordered.add( new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate(
              star.getFactTable(), alwaysFalse ) );
          wheres.add( alwaysFalse );
          if ( aggPredicates != null ) {
            // Column-less conjunct: carried on the AGG fact table, matching the base fact.
            aggPredicates.add( new org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate(
                aggStar.getFactTable(), alwaysFalse ) );
          }
          continue;
        }
        if ( argMembers.stream().anyMatch( Member::isNull ) ) {
          // A NULL member in the set: whether the member constraint collapses to the always-false
          // "(1 = 0)" depends on which addMemberConstraint branch runs (single-value IN vs the
          // multi-level compound), which is not modelled here. memberConstraintContribution returns
          // empty for it, so the arg would fall to the all-member branch below and under-constrain
          // (a null member is not calculated, so it would even add a bare existence join). BAIL to
          // the reference instead.
          throw dead( "set-arg-null-member" );
        }
        // The aggStar-threaded arg channel: under an agg routing the member-set predicate is built
        // on the AGG column nodes (single-value INs and tuple INs alike); a null aggStar is the
        // base form.
        java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> argCp =
            org.eclipse.daanse.rolap.common.constraint.MemberConstraintWriter.memberConstraintContribution(
                baseCube, aggStar, argMembers, argRestrict, argExclude );
        if ( argCp.isEmpty() && aggStar == null && !argExclude ) {
          // Compound-null-parent retry: an arg whose member set memberConstraintContribution
          // cannot carry BECAUSE of a null ancestor key (the Warehouse2 NECJ shape,
          // [#null].[#null].[addr].[name]) is retried through the compound form of
          // constrainMultiLevelMembers' null-parent compound
          // (((a2 is null and (name, a1) in (…))) [or …]), built with Predicates.isNull +
          // Predicates.inTuple. Base star only: under an agg routing the compound form is not
          // modelled (no agg provenance), and an exclude arg follows the NOT-IN form — both keep
          // the bail below.
          argCp = org.eclipse.daanse.rolap.common.constraint.MemberConstraintWriter
              .memberConstraintContributionCompoundNullParent( baseCube, argMembers, argRestrict );
        }
        if ( argCp.isEmpty() ) {
          // memberConstraintContribution could not carry this arg as a single ColumnPredicate, so an
          // empty return must be classified before it is dropped:
          //
          //   - A set that genuinely ADDS NOTHING (an all-member co-arg such as [All Products], or a
          //     calculated-only set) needs no WHERE and no join (an [All Products] co-arg in a
          //     Warehouse level read leaves product/product_class out of the FROM). SKIP it —
          //     turning it into a join would over-join a foreign dimension.
          //
          //   - Any OTHER inexpressible-but-REAL restriction (an exclude/NOT-IN multi-level tuple,
          //     an agg-routed compound, or a rest-form even the compound retry above cannot carry)
          //     is a real WHERE conjunct. Skipping it here would UNDER-CONSTRAIN and silently produce
          //     wrong SQL. BAIL to the reference query instead.
          //
          // (The NON-EMPTY existence join to the fact is emitted for the TARGET level being read via
          // factJoinRequired, never through joinTables, so a skipped all-member arg still keeps the
          // cross join's existence semantics.)
          boolean addsNothing = argMembers.stream().allMatch( RolapMember::isAll )
              || argMembers.stream().allMatch( m -> m.isCalculated() && !m.isParentChildLeaf() );
          if ( addsNothing ) {
            continue;
          }
          return bail( "set-arg-inexpressible-member" );
        }
        ordered.add( argCp.get() );
        if ( argCp.get().table() != null ) {
          // A table-less predicate (shared / non-cube level, constrained via its key expression)
          // contributes only the WHERE conjunct.
          joinTables.add( argCp.get().table() );
        }
        wheres.add( argCp.get().predicate() );
        if ( aggPredicates != null ) {
          // Provenance for the agg-join channel: the agg table carrying the arg's substituted key
          // column (same first-member level the writer's ColumnPredicate table is derived from).
          java.util.Optional<org.eclipse.daanse.rolap.common.aggmatcher.AggStar.Table> aggTable =
              org.eclipse.daanse.rolap.common.constraint.MemberConstraintWriter.aggMemberTable(
                  baseCube, aggStar, argMembers.get( 0 ).getLevel() );
          if ( aggTable.isEmpty() ) {
            throw dead( "set-arg-agg-table-unresolved" );
          }
          aggPredicates.add( new org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate(
              aggTable.get(), argCp.get().predicate() ) );
        }
      }
      // An all-member crossjoin (EMPTY base + all-member args) yields no WHERE — leave it empty rather than
      // an empty AND. ALWAYS the And wrap otherwise: the mapper splits the top-level And, so a single
      // grouped member-set conjunct must sit one level below the split to keep its parentheses.
      // Carry the base's factJoinRequired so the target level's non-empty existence join
      // to the fact is still emitted (the whole point of a NonEmptyCrossJoin).
      java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> whereOpt =
          wheres.isEmpty() ? java.util.Optional.empty()
              : java.util.Optional.of( org.eclipse.daanse.sql.statement.api.Predicates.and( wheres ) );
      org.eclipse.daanse.rolap.common.sql.ConstraintContribution result =
          new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
              whereOpt, joinTables, ordered, c.memberKeyGroup() )
              .withFactJoinRequired( c.factJoinRequired() );
      if ( aggPredicates != null ) {
        // A present plan with EMPTY predicates stays present — the valid unconstrained agg
        // translation.
        result = result.withAggPlan(
            new org.eclipse.daanse.rolap.common.sql.AggPlan( aggStar, aggPredicates ) );
      }
      return java.util.Optional.of( result );
    }

    /**
     * The aggStar collapsed level-members WHERE <em>including</em> this set's per-arg member
     * restrictions (consumed by {@code SqlTupleReader.aggCollapsedLevelMembersSql}) — a
     * {@code DescendantsCrossJoinArg} parent restriction ({@code the_year = 1997} under
     * {@code [Time].[Quarter]}) that the inherited context-only translation would misclassify as
     * UNCONSTRAINED. Composition emits the agg-substituted context conjuncts first, then per
     * applicable arg the agg-substituted single-parent key group ({@code aggMemberKeyGroup} — the
     * same parenthesised group {@code MemberConstraintWriter.addMemberConstraint} emits for one
     * member).
     * <p>
     * BAILs (never UNCONSTRAINED) for every arg shape not reproduced here: a
     * {@code MemberListCrossJoinArg} with calculated members (the constraint is skipped but the
     * members are read by enumeration — not the single-target collapsed shape), any other
     * {@code MemberListCrossJoinArg} (single-value IN / multi-level
     * {@code constrainMultiLevelMembers} compounds are not modelled), a calculated or null
     * descendants member, an off-cube level, or an unresolvable agg key column. Subclass gates:
     * {@code FilterConstraint} overrides for its context gate (no context/args unless non-empty or
     * join-required); {@code TopCountConstraint} needs none — its non-join branch never sees an
     * aggStar ({@code supportsAggTables() == isJoinRequired()}).
     * <p>
     * Routing: the level-members collapsed gate reads the live {@code levelMembersAggWhereState}
     * first and lets a non-bailed candidate decide the empty-WHERE case.
     */
    @Override
    public SqlContextConstraint.AggWhereResult levelMembersAggWhereCandidateState( AggStar aggStar ) {
      if ( aggStar == null ) {
        return SqlContextConstraint.AggWhereResult.BAIL;
      }
      // The inherited context leg — the same context translation, in LIST form so the per-arg
      // groups append behind the context conjuncts (emission order). null == a shape outside the
      // context translation (virtual cube, calc/slicer exotic, role access, missing agg node,
      // builder-time exception).
      List<org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate> ctx =
          aggContextColumnPredicates( aggStar, null );
      if ( ctx == null ) {
        return SqlContextConstraint.AggWhereResult.BAIL;
      }
      List<org.eclipse.daanse.sql.statement.api.expression.Predicate> all = new ArrayList<>();
      for ( org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate acp : ctx ) {
        all.add( acp.predicate() );
      }
      RolapCube cube = (RolapCube) getEvaluator().getCube();
      for ( CrossJoinArg arg : args ) {
        if ( !canApplyCrossJoinArgConstraint( arg ) ) {
          // Calc-member MemberListCrossJoinArg: the constraint is skipped AND the members are
          // enumerated (enum-target read) — not the shape this candidate serves. BAIL.
          return SqlContextConstraint.AggWhereResult.BAIL;
        }
        RolapLevel level = arg.getLevel();
        if ( level != null && !levelIsOnBaseCube( cube, level ) ) {
          // Such an off-cube arg is not reproduced here (virtual-cube composition already bailed
          // above). BAIL.
          return SqlContextConstraint.AggWhereResult.BAIL;
        }
        if ( arg instanceof org.eclipse.daanse.rolap.common.sql.DescendantsCrossJoinArg ) {
          List<RolapMember> argMembers = arg.getMembers();
          if ( argMembers == null ) {
            // No member dimension -> this arg adds nothing.
            continue;
          }
          RolapMember m = argMembers.get( 0 );
          if ( m.isCalculated() || m.isNull() ) {
            return SqlContextConstraint.AggWhereResult.BAIL;
          }
          java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> group =
              aggMemberKeyGroup( aggStar, cube, m );
          if ( group == null ) {
            return SqlContextConstraint.AggWhereResult.BAIL;
          }
          // Empty (all-member parent) adds nothing — a no-op key group.
          group.ifPresent( all::add );
        } else {
          // MemberListCrossJoinArg without calc members (single-value IN / multi-level
          // constrainMultiLevelMembers compounds) and any future arg type: not modelled. BAIL.
          return SqlContextConstraint.AggWhereResult.BAIL;
        }
      }
      if ( all.isEmpty() ) {
        return SqlContextConstraint.AggWhereResult.UNCONSTRAINED;
      }
      return SqlContextConstraint.AggWhereResult.of(
          org.eclipse.daanse.sql.statement.api.Predicates.and( all ) );
    }

    /**
     * If the cross join argument has calculated members in its enumerated set, ignore the constraint since we won't
     * produce that set through the native sql and instead will simply enumerate through the members in the set
     */
    protected boolean canApplyCrossJoinArgConstraint( CrossJoinArg arg ) {
      return !( arg instanceof MemberListCrossJoinArg memberListCrossJoinArg)
        || !memberListCrossJoinArg.hasCalcMembers();
    }

    private boolean levelIsOnBaseCube(
      final RolapCube baseCube, final RolapLevel level ) {
      return baseCube.findBaseCubeHierarchy( level.getHierarchy() ) != null;
    }

    /**
     * Returns null to prevent the member/childern from being cached. There exists no valid MemberChildrenConstraint
     * that would fetch those children that were extracted as a side effect from evaluating a non empty crossjoin
     */
    @Override
	public MemberChildrenConstraint getMemberChildrenConstraint(
      RolapMember parent ) {
      return null;
    }

    /**
     * returns a key to cache the result
     */
    @Override
	public Object getCacheKey() {
      List<Object> key = new ArrayList<>();
      key.add( super.getCacheKey() );
      // only add args that will be retrieved through native sql;
      // args that are sets with calculated members aren't executed
      // natively
      for ( CrossJoinArg arg : args ) {
        if ( canApplyCrossJoinArgConstraint( arg ) ) {
          key.add( arg );
        }
      }
      return key;
    }
  }

  protected class SetEvaluator implements NativeEvaluator {
    private final CrossJoinArg[] args;
    private final CatalogReaderWithMemberReaderAvailable schemaReader;
    private final TupleConstraint constraint;
    private int maxRows = 0;
    private boolean completeWithNullValues;

    public SetEvaluator(
      CrossJoinArg[] args,
      CatalogReader schemaReader,
      TupleConstraint constraint ) {
      this.args = args;
      if ( schemaReader instanceof CatalogReaderWithMemberReaderAvailable schemaReaderWithMemberReaderAvailable ) {
        this.schemaReader =
            schemaReaderWithMemberReaderAvailable;
      } else {
        this.schemaReader =
          new CatalogReaderWithMemberReaderCache( schemaReader );
      }
      this.constraint = constraint;
    }

    public void setCompleteWithNullValues( boolean completeWithNullValues ) {
      this.completeWithNullValues = completeWithNullValues;
    }

    @Override
	public Object execute( ResultStyle desiredResultStyle ) {
      return switch (desiredResultStyle) {
      case ITERABLE -> executeList(
                  new SqlTupleReader( constraint ) );
      case MUTABLE_LIST, LIST -> executeList( new SqlTupleReader( constraint ) );
      default -> throw ResultStyleException.generate(
          ResultStyle.ITERABLE_MUTABLELIST_LIST,
          Collections.singletonList( desiredResultStyle ) );
      };
    }

    protected TupleList executeList( final SqlTupleReader tr ) {
      tr.setMaxRows( maxRows );
      for ( CrossJoinArg arg : args ) {
        addLevel( tr, arg );
      }

      // Look up the result in cache; we can't return the cached
      // result if the tuple reader contains a target with calculated
      // members because the cached result does not include those
      // members; so we still need to cross join the cached result
      // with those enumerated members.
      //
      // The key needs to include the arguments (projection) as well as
      // the constraint, because it's possible (see bug MONDRIAN-902)
      // that independent axes have identical constraints but different
      // args (i.e. projections). REVIEW: In this case, should we use the
      // same cached result and project different columns?
      //
      // [MONDRIAN-2411] adds the roles to the key. Normally, the
      // schemaReader would apply the roles, but we cache the lists over
      // its head.
      List<Object> key = new ArrayList<>();
      key.add( tr.getCacheKey() );
      key.addAll( Arrays.asList( args ) );
      key.add( maxRows );
      key.add( schemaReader.getRole() );

      TupleList result = cache.get( key );
      boolean hasEnumTargets = ( tr.getEnumTargetCount() > 0 );
      if ( result != null && !hasEnumTargets ) {
        if ( listener != null ) {
          TupleEvent e = new TupleEvent( this, tr );
          listener.foundInCache( e );
        }
        return new DelegatingTupleList(
          args.length, Util.<List<Member>>cast( filterInaccessibleTuples(result) ) );
      }

      // execute sql and store the result
      if ( result == null && listener != null ) {
        TupleEvent e = new TupleEvent( this, tr );
        listener.executingSql( e );
      }

      // if we don't have a cached result in the case where we have
      // enumerated targets, then retrieve and cache that partial result
      TupleList partialResult = result;
      List<List<RolapMember>> newPartialResult = null;
      if ( hasEnumTargets && partialResult == null ) {
        newPartialResult = new ArrayList<>();
      }
      Context context= schemaReader.getContext();
      if ( args.length == 1 ) {
        result =
          tr.readMembers(
              context, partialResult, newPartialResult );
      } else {
        result =
          tr.readTuples(
              context, partialResult, newPartialResult );
      }

      // Check limit of result size already is too large
      Util.checkCJResultLimit( result.size() );

      // Did not get as many members as expected - try to complete using
      // less constraints
      if ( completeWithNullValues && result.size() < maxRows ) {
        RolapLevel l = args[ 0 ].getLevel();
        List<RolapMember> list = new ArrayList<>();
        for ( List<Member> lm : result ) {
          for ( Member m : lm ) {
            list.add( (RolapMember) m );
          }
        }
        SqlTupleReader str = new SqlTupleReader(
          new MemberExcludeConstraint(
            list, l,
            constraint instanceof SetConstraint setConstraint
              ? setConstraint : null ) );
        str.setAllowHints( false );
        for ( CrossJoinArg arg : args ) {
          addLevel( str, arg );
        }

        str.setMaxRows( maxRows - result.size() );
        result.addAll(
          str.readMembers(
            context, null, new ArrayList<>() ) );
      }

      if ( !schemaReader.getContext().getConfigValue(ConfigConstants.DISABLE_CACHING, ConfigConstants.DISABLE_CACHING_DEFAULT_VALUE, Boolean.class) ) {
        if ( hasEnumTargets ) {
          if ( newPartialResult != null ) {
            cache.put(
              key,
              new DelegatingTupleList(
                args.length,
                Util.<List<Member>>cast( newPartialResult ) ) );
          }
        } else {
          cache.put( key, result );
        }
      }
      return filterInaccessibleTuples( result );
    }

    /**
     * Checks access rights and hidden status on the members in each tuple in tupleList.
     */
    private TupleList filterInaccessibleTuples( TupleList tupleList ) {
      if ( needsFiltering( tupleList ) ) {
        final java.util.function.Predicate<Member> memberInaccessible =
          memberInaccessiblePredicate();
        List<List<Member>> ret=    tupleList.stream().filter( tupleAccessiblePredicate( memberInaccessible ) ).toList();
        return new DelegatingTupleList(tupleList.getArity(), ret);
      }
      return tupleList;
    }

    private boolean needsFiltering( TupleList tupleList ) {
      return !tupleList.isEmpty()
        &&  tupleList.get( 0 ).stream().anyMatch( needsFilterPredicate() );
    }

    private java.util.function.Predicate<Member> needsFilterPredicate() {
      return member -> isRaggedLevel( member.getLevel() )
			|| isCustomAccess( member.getHierarchy() );

    }

    private boolean isRaggedLevel( Level level ) {
      if ( level instanceof RolapLevel rolapLevel) {
        return rolapLevel.getHideMemberCondition()
          != RolapLevel.HideMemberCondition.Never;
      }
      // don't know if it's ragged, so assume it is.
      // should not reach here
      return true;
    }

    private boolean isCustomAccess( Hierarchy hierarchy ) {
      if ( constraint.getEvaluator() == null ) {
        return false;
      }
      AccessHierarchy access =
        constraint
          .getEvaluator()
          .getCatalogReader()
          .getRole()
          .getAccess( hierarchy );
      return access == AccessHierarchy.CUSTOM;
    }

    private java.util.function.Predicate<Member> memberInaccessiblePredicate() {
      if ( constraint.getEvaluator() != null ) {
        return member -> {
            Role role =
              constraint
                .getEvaluator().getCatalogReader().getRole();
            return member.isHidden() || !role.canAccess( member );
          };
      }
      return Member::isHidden;
    }

	private java.util.function.Predicate<List<Member>> tupleAccessiblePredicate(
			final java.util.function.Predicate<Member> memberInaccessible) {
		return memberList -> memberList.stream().noneMatch(memberInaccessible);
	}

    private void addLevel( TupleReader tr, CrossJoinArg arg ) {
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

      //check if that is one of parent child levels and use first.
      Optional<Level> ol = getParentParentChildLevel(level);
      if (ol.isPresent() && ol.get() instanceof RolapLevel rl) {
          level = rl;
      }

      RolapHierarchy hierarchy = level.getHierarchy();
      MemberReader mr = schemaReader.getMemberReader( hierarchy );
      MemberBuilder mb = mr.getMemberBuilder();
      Util.assertTrue( mb != null, "MemberBuilder not found" );

      if ( arg instanceof MemberListCrossJoinArg memberListCrossJoinArg
        && memberListCrossJoinArg.hasCalcMembers() ) {
        // only need to keep track of the members in the case
        // where there are calculated members since in that case,
        // we produce the values by enumerating through the list
        // rather than generating the values through native sql
        tr.addLevelMembers( level, mb, arg.getMembers() );
      } else {
        tr.addLevelMembers( level, mb, null );
      }
    }

    private Optional<Level> getParentParentChildLevel(Level level) {
        if (level.getParentLevel() != null) {
            if (level.getParentLevel().isParentChild()) {
                return Optional.of(level.getParentLevel());
            } else {
                return getParentParentChildLevel(level.getParentLevel());
            }
        }
        return Optional.empty();
    }

    int getMaxRows() {
      return maxRows;
    }

    void setMaxRows( int maxRows ) {
      this.maxRows = maxRows;
    }
  }

  /**
   * Tests whether non-native evaluation is preferred for the given arguments.
   *
   * @param joinArg true if evaluating a cross-join; false if evaluating a single-input expression such as filter
   * @return true if <em>all</em> args prefer the interpreter
   */
  protected boolean isPreferInterpreter(
    CrossJoinArg[] args,
    boolean joinArg ) {
    for ( CrossJoinArg arg : args ) {
      if ( !arg.isPreferInterpreter( joinArg ) ) {
        return false;
      }
    }
    return true;
  }

  /**
   * disable garbage collection for test
   */
  @Override
@SuppressWarnings( { "unchecked", "rawtypes" } )
  void useHardCache( boolean hard ) {
    if ( hard ) {
      cache = new HardSmartCache();
    } else {
      cache = new SoftSmartCache();
    }
  }

  /**
   * Overrides current members in position by default members in hierarchies which are involved in this
   * filter/topcount.
   * Stores the RolapStoredMeasure into the context because that is needed to generate a cell request to constraint
   * the
   * sql.
   *
   * The current context may contain a calculated measure, this measure
   * was translated into an sql condition (filter/topcount). The measure is not used to constrain the result but
   * only to
   * access the star.
   *
   * @param evaluator     Evaluation context to modify
   * @param cargs         Cross join arguments
   * @param storedMeasure Stored measure
   * @see RolapAggregationManager#makeRequest(RolapEvaluator)
   */
  public void overrideContext(
    RolapEvaluator evaluator,
    CrossJoinArg[] cargs,
    RolapStoredMeasure storedMeasure ) {
    CatalogReader schemaReader = evaluator.getCatalogReader();
    for ( CrossJoinArg carg : cargs ) {
      RolapLevel level = carg.getLevel();
      if ( level != null ) {
        RolapHierarchy hierarchy = level.getHierarchy();

        final Member contextMember;
        if ( hierarchy.hasAll()
          || schemaReader.getRole()
          .getAccess( hierarchy ) == AccessHierarchy.ALL ) {
          // The hierarchy may have access restrictions.
          // If it does, calling .substitute() will retrieve an
          // appropriate LimitedRollupMember.
          contextMember =
            schemaReader.substitute( hierarchy.getAllMember() );
        } else {
          // If there is no All member on a role restricted hierarchy,
          // use a restricted member based on the set of accessible
          // root members.
          contextMember = new MultiCardinalityDefaultMember(
            hierarchy.getMemberReader()
              .getRootMembers().get( 0 ) );
        }
        evaluator.setContext( contextMember );
      }
    }
    if ( storedMeasure != null ) {
      evaluator.setContext( storedMeasure );
    }
  }


  public interface CatalogReaderWithMemberReaderAvailable
    extends CatalogReader {
    MemberReader getMemberReader( Hierarchy hierarchy );
  }

  private static class CatalogReaderWithMemberReaderCache
    extends DelegatingCatalogReader
    implements CatalogReaderWithMemberReaderAvailable {
    private final Map<Hierarchy, MemberReader> hierarchyReaders =
      new HashMap<>();

    CatalogReaderWithMemberReaderCache( CatalogReader schemaReader ) {
      super( schemaReader );
    }

    @Override
	public synchronized MemberReader getMemberReader( Hierarchy hierarchy ) {
      return hierarchyReaders.computeIfAbsent(hierarchy,
          k -> ( (RolapHierarchy) hierarchy ).createMemberReader(schemaReader.getRole() ));
    }

    @Override
    public Context getContext() {
        return schemaReader.getContext();
    }
  }

  public void flushCache() {
    cache.clear();
  }
}

// End RolapNativeSet.java

