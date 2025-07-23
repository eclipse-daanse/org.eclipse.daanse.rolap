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

package org.eclipse.daanse.rolap.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.NativeEvaluator;
import org.eclipse.daanse.olap.api.access.AccessHierarchy;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.calc.ResultStyle;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.DelegatingTupleList;
import org.eclipse.daanse.olap.common.DelegatingCatalogReader;
import org.eclipse.daanse.olap.common.ResultStyleException;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.common.TupleReader.MemberBuilder;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.cache.HardSmartCache;
import org.eclipse.daanse.rolap.common.cache.SmartCache;
import org.eclipse.daanse.rolap.common.cache.SoftSmartCache;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArgFactory;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.MemberListCrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
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
  protected abstract static class SetConstraint extends SqlContextConstraint {
    CrossJoinArg[] args;

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
	protected boolean isJoinRequired() {
      return args.length > 1 || super.isJoinRequired();
    }

    @Override
	public void addConstraint(
      SqlQuery sqlQuery,
      RolapCube baseCube,
      AggStar aggStar ) {
      super.addConstraint( sqlQuery, baseCube, aggStar );
      for ( CrossJoinArg arg : args ) {
        if ( canApplyCrossJoinArgConstraint( arg ) ) {
          RolapLevel level = arg.getLevel();
          if ( level == null || levelIsOnBaseCube( baseCube, level ) ) {
            arg.addConstraint( sqlQuery, baseCube, aggStar );
          }
        }
      }
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
      switch ( desiredResultStyle ) {
        case ITERABLE:
          return executeList(
              new SqlTupleReader( constraint ) );
        case MUTABLE_LIST, LIST:
          return executeList( new SqlTupleReader( constraint ) );
        default:
          throw ResultStyleException.generate(
            ResultStyle.ITERABLE_MUTABLELIST_LIST,
            Collections.singletonList( desiredResultStyle ) );
      }
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
        return new java.util.function.Predicate<Member>() {
          @Override
		public boolean test( Member member ) {
            Role role =
              constraint
                .getEvaluator().getCatalogReader().getRole();
            return member.isHidden() || !role.canAccess( member );
          }
        };
      }
      return new java.util.function.Predicate<Member>() {
        @Override
		public boolean test( Member member ) {
          return member.isHidden();
        }
      };
    }

	private java.util.function.Predicate<List<Member>> tupleAccessiblePredicate(
			final java.util.function.Predicate<Member> memberInaccessible) {
		return new java.util.function.Predicate<List<Member>>() {

			@Override
			public boolean test(List<Member> memberList) {
				return memberList.stream().noneMatch(memberInaccessible);
			}
		};
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
  protected void overrideContext(
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
          contextMember = new RestrictedMemberReader
            .MultiCardinalityDefaultMember(
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

