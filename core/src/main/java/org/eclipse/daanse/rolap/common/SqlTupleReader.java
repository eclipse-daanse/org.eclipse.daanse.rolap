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
package org.eclipse.daanse.rolap.common;

import static org.eclipse.daanse.olap.fun.sort.Sorter.hierarchizeTupleList;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.api.execution.Execution.Purpose;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.ArrayTupleList;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.ListTupleList;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.TupleCollections;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.UnaryTupleList;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.common.sql.BuiltSql;
import org.eclipse.daanse.rolap.common.sqlbuild.SqlBuildGuard;
import org.eclipse.daanse.rolap.common.sqlbuild.TupleSqlMapper;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.exceptions.ResourceLimitExceededException;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.function.def.crossjoin.CrossJoinFunDef;
import org.eclipse.daanse.olap.key.BitKey;
import  org.eclipse.daanse.olap.util.CancellationChecker;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.catalog.RolapCubeComparator;
import org.eclipse.daanse.rolap.common.constraint.DescendantsConstraint;
import org.eclipse.daanse.rolap.common.constraint.CalculatedMemberExpander;
import org.eclipse.daanse.rolap.common.constraint.MeasureConflictDetector;
import org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.member.MemberCache;
import org.eclipse.daanse.rolap.common.member.SqlMemberSource;
import org.eclipse.daanse.rolap.common.nativize.RolapNativeCrossJoin;
import org.eclipse.daanse.rolap.common.nativize.RolapNativeFilter;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.DescendantsCrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.MemberListCrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;

import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.util.SqlExpressionResolver;
import org.eclipse.daanse.rolap.element.RolapBaseCubeMeasure;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMemberBase;
import org.eclipse.daanse.rolap.element.RolapParentChildMember;
import org.eclipse.daanse.rolap.element.RolapPhysicalCube;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.eclipse.daanse.rolap.element.RolapVirtualCube;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the members of a single level (level.members) or of multiple levels (crossjoin).
 *
 * Allows the result to be restricted by a {@link TupleConstraint}. So
 * the SqlTupleReader can also read Member.Descendants (which is level.members restricted to a common parent) and
 * member.children (which is a special case of member.descendants). Other constraints, especially for the current slicer
 * or evaluation context, are possible.
 *
 * Caching
 *
 * When a SqlTupleReader reads level.members, it groups the result into
 * parent/children pairs and puts them into the cache. In order that these can be found later when the children of a
 * parent are requested, a matching constraint must be provided for every parent.
 *
 *
 *
 * When reading members from a single level, then the constraint is not
 * required to join the fact table in
 * {@code TupleConstraint.addLevelConstraintOps}
 * although it may do so to restrict
 * the result. Also it is permitted to cache the parent/children from all
 * members in MemberCache, so
 * {@link TupleConstraint#getMemberChildrenConstraint(RolapMember)}
 * should not return null.
 *
 * When reading multiple levels (i.e. we are performing a crossjoin),
 * then we can not store the parent/child pairs in the MemberCache and
 * {@link TupleConstraint#getMemberChildrenConstraint(RolapMember)}
 * must return null. Also
 * {@code TupleConstraint.addConstraintOps}
 * is required to join the fact table for the levels table.
 *
 *
 * @author av
 * @since Nov 11, 2005
 */
public class SqlTupleReader implements TupleReader {
  private static final Logger LOGGER =
    LoggerFactory.getLogger( SqlTupleReader.class );
  protected final TupleConstraint constraint;
  List<TargetBase> targets = new ArrayList<>();
  protected int maxRows = 0;

  /**
   * How many members could not be instantiated in this iteration. This phenomenon occurs in a parent-child hierarchy,
   * where a member cannot be created before its parent. Populating the hierarchy will take multiple passes and will
   * terminate in success when missedMemberCount == 0 at the end of a pass, or failure if a pass generates failures but
   * does not manage to load any more members.
   */
  private int missedMemberCount;
  private int emptySets = 0;
  // allow hints by default
  private boolean allowHints = true;
  private HashMap<RolapMember, Object> rolapToOrdinalMap = new HashMap<>();

  public boolean isAllowHints() {
    return allowHints;
  }

  public void setAllowHints( boolean allowHints ) {
    this.allowHints = allowHints;
  }

  /**
   * Helper class for SqlTupleReader; keeps track of target levels and constraints for adding to sql query.
   */
  private class Target extends TargetBase {
    final MemberCache cache;

    List<RolapLevel> levels;
    int levelDepth;
    boolean parentChild;
    List<RolapMember> members;
    final HashMap<Object, RolapMember> keyToMember =
      new HashMap<>();
    List<List<RolapMember>> siblings;
      private final static String levelTableParentNotFound = """
        The level {0} makes use of the ''parentColumn'' attribute, but a parent member for key {1} is missing. This can be due to the usage of the NativizeSet MDX function with a list of members form a parent-child hierarchy that doesn''t include all parent members in its definition. Using NativizeSet with a parent-child hierarchy requires the parent members to be included in the set, or the hierarchy cannot be properly built natively.
      """;
      // if set, the rows for this target come from the array rather
    // than native sql
    // current member within the current result set row
    // for this target

    public Target(
      RolapLevel level,
      MemberBuilder memberBuilder,
      List<RolapMember> srcMembers ) {
      super( srcMembers, level, memberBuilder );
      this.cache = memberBuilder.getMemberCache();
    }

    @Override
	public void open() {
      levels = (List<RolapLevel>) level.getHierarchy().getLevels();
      setList( new ArrayList<>() );
      levelDepth = level.getDepth();
      parentChild = level.isParentChild();
      // members[i] is the current member of level#i, and siblings[i]
      // is the current member of level#i plus its siblings
      members =
        new ArrayList<>(
          Collections.<RolapMember>nCopies( levels.size(), null ) );
      siblings = new ArrayList<>();
      for ( int i = 0; i < levels.size() + 1; i++ ) {
        siblings.add( new ArrayList<>() );
      }
    }

    @Override
	int internalAddRow( SqlStatement stmt, int column )
      throws SQLException {
      RolapMember member = null;
      if ( getCurrMember() != null ) {
        setCurrMember( member );
      } else {
        boolean checkCacheStatus = true;
        for ( int i = 0; i <= levelDepth; i++ ) {
          RolapLevel childLevel = levels.get( i );
          if ( childLevel.isAll() ) {
            member = memberBuilder.allMember();
            continue;
          }
          RolapMember parentMember = member;
          final List<SqlStatement.Accessor> accessors =
            stmt.getAccessors();
          if ( parentChild || childLevel.isParentChild()) {
            Object parentValue =
              accessors.get( column++ ).get();
            if ( parentValue == null
              || parentValue.toString().equals(
              childLevel.getNullParentValue() ) ) {
              // member is at top of hierarchy; its parent is the
              // 'all' member. Convert null to placeholder value
              // for uniformity in hashmaps.
              parentValue = Util.sqlNullValue;
            } else {
              Object parentKey =
                cache.makeKey(
                  member,
                  parentValue );
              parentMember = cache.getMember( parentKey );
              if ( parentMember == null ) {
                // Maybe it wasn't caching.
                // We have an intermediate volatile map.
                parentMember = keyToMember.get( parentValue );
              }
              if ( parentMember == null ) {
                String msg = MessageFormat.format(levelTableParentNotFound,
                        childLevel.getUniqueName(),
                        String.valueOf( parentValue ) );
                LOGGER.warn(msg);
              }
            }
          }
          Object value = accessors.get( column++ ).get();
          if ( value == null ) {
            value = Util.sqlNullValue;
          }
          Object captionValue;
          if ( childLevel.hasCaptionColumn() ) {
            captionValue = accessors.get( column++ ).get();
          } else {
            captionValue = null;
          }
          Object key;
          if ( parentChild ) {
            key = cache.makeKey( member, value );
          } else {
            key = cache.makeKey( parentMember, value );
          }
          member = cache.getMember( key, checkCacheStatus );
          checkCacheStatus = false; // only check the first time
          if ( member == null ) {
            if ( constraint instanceof RolapNativeCrossJoin.NonEmptyCrossJoinConstraint
              && childLevel.isParentChild() ) {
              member =
                castToNonEmptyCJConstraint( constraint )
                  .findMember( value );
            }
            if ( member == null ) {
              if (parentMember  == null || !(parentMember instanceof RolapParentChildMember) || !parentMember.getKey().equals(value)) {
                if (parentMember  == null || !parentMember.getLevel().equals(childLevel) || !parentMember.getKey().equals(value)) {
                    member = memberBuilder.makeMember(
                            parentMember, childLevel, value, captionValue,
                            parentChild, stmt, key, column );
                }
              }
            }
          }

          if (member != null) {
          // Skip over the columns consumed by makeMember
          for (SqlExpression oe : childLevel.getOrdinalExps()) {
              if ( !oe.equals(
                  childLevel.getKeyExp() ) ) {
                  Object ordinal = accessors.get( column++ ).get();
                  Object prevValue = rolapToOrdinalMap.put( member, ordinal );
                  if ( prevValue != null
                      && !Objects.equals( prevValue, ordinal ) ) {
                      LOGGER.error(
                          "Column expression for {} is inconsistent with ordinal or caption expression. It should have 1:1 relationship",
                      member.getUniqueName() );
                  }
              }
          }
          column += childLevel.getProperties().length;

          // Cache in our intermediate map the key/member pair
          // for later lookups of children.
          keyToMember.put( member.getKey(), member );

          if ( member != members.get( i ) ) {
            // Flush list we've been building.
            List<RolapMember> children = siblings.get( i + 1 );
            if ( children != null && !children.isEmpty()) {
              MemberChildrenConstraint mcc =
                constraint.getMemberChildrenConstraint(
                  members.get( i ) );
              if ( mcc != null ) {
                cache.putChildren(
                  members.get( i ), mcc, children );
              }
            }
            // Start a new list, if the cache needs one. (We don't
            // synchronize, so it's possible that the cache will
            // have one by the time we complete it.)
            MemberChildrenConstraint mcc =
              constraint.getMemberChildrenConstraint( member );
            // we keep a reference to cachedChildren so they don't
            // get garbage-collected
            List<RolapMember> cachedChildren =
              cache.getChildrenFromCache( member, mcc );
            if ( i < levelDepth && cachedChildren == null ) {
              siblings.set( i + 1, new ArrayList<>() );
            } else {
              // don't bother building up a list
              siblings.set( i + 1, null );
            }
            // Record new current member of this level.
            members.set( i, member );
            // If we're building a list of siblings at this level,
            // we haven't seen this one before, so add it.
            if ( siblings.get( i ) != null ) {
              if ( value == Util.sqlNullValue ) {
                addAsOldestSibling( siblings.get( i ), member );
              } else {
                siblings.get( i ).add( member );
              }
            }
          }
        }
        setCurrMember( member );
        }
      }
      if (member != null) {
          getList().add( member );
      }
      return column;
    }

    @Override
	public List<Member> close() {
      synchronized ( cacheLock ) {
        return internalClose();
      }
    }

    /**
     * Cleans up after all rows have been processed, and returns the list of members.
     *
     * @return list of members
     */
    public List<Member> internalClose() {
      for ( int i = 0; i < members.size(); i++ ) {
        RolapMember member = members.get( i );
        final List<RolapMember> children = siblings.get( i + 1 );
        if ( member != null && children != null ) {
          // If we are finding the members of a particular level, and
          // we happen to find some of the children of an ancestor of
          // that level, we can't be sure that we have found all of
          // the children, so don't put them in the cache.
          if ( member.getDepth() < level.getDepth() ) {
            continue;
          }
          MemberChildrenConstraint mcc =
            constraint.getMemberChildrenConstraint( member );
          if ( mcc != null ) {
            cache.putChildren( member, mcc, children );
          }
        }
      }
      return Util.cast( getList() );
    }

    /**
     * Adds member just before the first element in
     * list which has the same parent.
     */
    private void addAsOldestSibling(
      List<RolapMember> list,
      RolapMember member ) {
      int i = list.size();
      while ( --i >= 0 ) {
        RolapMember sibling = list.get( i );
        if ( sibling.getParentMember() != member.getParentMember() ) {
          break;
        }
      }
      list.add( i + 1, member );
    }
  }

  public SqlTupleReader( TupleConstraint constraint ) {
    this.constraint = constraint;
  }

  @Override
public void incrementEmptySets() {
    emptySets++;
  }

  @Override
public void addLevelMembers(
    RolapLevel level,
    MemberBuilder memberBuilder,
    List<RolapMember> srcMembers ) {
    targets.add( new Target( level, memberBuilder, srcMembers ) );
  }

  @Override
public Object getCacheKey() {
    List<Object> key = new ArrayList<>();
    key.add( constraint.getCacheKey() );
    key.add( SqlTupleReader.class );
    for ( TargetBase target : targets ) {
      // don't include the level in the key if the target isn't
      // processed through native sql
      if ( target.srcMembers != null ) {
        key.add( target.getLevel() );
      }
    }
    if ( constraint.getEvaluator() != null ) {
      addTargetGroupsToKey( key );
    }
    return key;
  }

  /**
   * adds target group info to cacheKey if split target groups are needed for this query.
   */
  private void addTargetGroupsToKey( List<Object> cacheKey ) {
    List<List<TargetBase>> targetGroups = groupTargets(
      targets,
      constraint.getEvaluator().getQuery() );
    if ( targetGroups.size() > 1 ) {
      cacheKey.add( targetsToLevels( targetGroups ) );
    }
  }

  private List<List<RolapLevel>> targetsToLevels(
    List<List<TargetBase>> targetGroups ) {
    List<List<RolapLevel>> groupedLevelList = new ArrayList<>();
    for ( List<TargetBase> targetsInner : targetGroups ) {
      List<RolapLevel> levels = new ArrayList<>();
      for ( TargetBase target : targetsInner ) {
        levels.add( target.getLevel() );
      }
      groupedLevelList.add( levels );
    }
    return groupedLevelList;
  }

  /**
   * @return number of targets that contain enumerated sets with calculated members
   */
  public int getEnumTargetCount() {
    int enumTargetCount = 0;
    for ( TargetBase target : targets ) {
      if ( target.getSrcMembers() != null ) {
        enumTargetCount++;
      }
    }
    return enumTargetCount;
  }

  protected void prepareTuples(
    Context context,
    TupleList partialResult,
    List<List<RolapMember>> newPartialResult, List<TargetBase> targetGroup ) {
    String message = "Populating member cache with members for "
      + targetGroup;
    SqlStatement stmt = null;
    final ResultSet resultSet;
    boolean execQuery = ( partialResult == null );
    try {
      if ( execQuery ) {
        // we're only reading tuples from the targets that are
        // non-enum targets
        List<TargetBase> partialTargets = new ArrayList<>();
        for ( TargetBase target : targetGroup ) {
          if ( target.srcMembers == null ) {
            partialTargets.add( target );
          }
        }
        final BuiltSql guarded =
          makeLevelMembersSql( context, targetGroup );
        String sql = guarded.render().sql();
        List<BestFitColumnType> types = guarded.render().columnTypes();
        assert sql != null && !sql.equals( "" );
        ExecutionMetadata metadata = ExecutionMetadata.of(
          "SqlTupleReader.readTuples " + partialTargets,
          message,
          Purpose.TUPLES,
          0
        );
        ExecutionContext execContext = getExecution(context).asContext().createChild(metadata, Optional.empty());
        stmt = RolapUtil.executeQuery(
          context, sql, types, maxRows, 0,
          execContext,
          -1, -1, null );
        resultSet = stmt.getResultSet();
      } else {
        resultSet = null;
      }

      for ( TargetBase target : targetGroup ) {
        target.open();
      }

      int limit = SystemWideProperties.instance().ResultLimit;
      int fetchCount = 0;

      // determine how many enum targets we have
      int enumTargetCount = getEnumTargetCount();
      int[] srcMemberIdxes = null;
      if ( enumTargetCount > 0 ) {
        srcMemberIdxes = new int[ enumTargetCount ];
      }

      boolean moreRows;
      int currPartialResultIdx = 0;
      if ( execQuery ) {
        moreRows = resultSet.next();
        if ( moreRows ) {
          ++stmt.rowCount;
        }
      } else {
        moreRows = currPartialResultIdx < partialResult.size();
      }

      Execution execution = getExecution(context);
      while ( moreRows ) {
        // Check if the MDX query was canceled.
        CancellationChecker.checkCancelOrTimeout(
          stmt.rowCount, execution );

        if ( limit > 0 && limit < ++fetchCount ) {
          // result limit exceeded, throw an exception
          throw new ResourceLimitExceededException((long) limit );
        }

        if ( enumTargetCount == 0 ) {
          int column = 0;
          for ( TargetBase target : targetGroup ) {
            target.setCurrMember( null );
            column = target.addRow( stmt, column );
          }
        } else {
          // find the first enum target, then call addTargets()
          // to form the cross product of the row from resultSet
          // with each of the list of members corresponding to
          // the enumerated targets
          int firstEnumTarget = 0;
          for ( ; firstEnumTarget < targetGroup.size();
                firstEnumTarget++ ) {
            if ( targetGroup.get( firstEnumTarget )
              .srcMembers != null ) {
              break;
            }
          }
          List<RolapMember> partialRow;
          if ( execQuery ) {
            partialRow = null;
          } else {
            partialRow =
              Util.cast( partialResult.get( currPartialResultIdx ) );
          }
          resetCurrMembers( partialRow );
          addTargets(
            0, firstEnumTarget, enumTargetCount, srcMemberIdxes,
            stmt, message );
          if ( newPartialResult != null ) {
            savePartialResult( newPartialResult );
          }
        }

        if ( execQuery ) {
          if ( maxRows > 0 && stmt.rowCount >= maxRows ) {
            // Client-side enforcement of maxRows: some JDBC drivers (duckdb_jdbc)
            // silently ignore Statement.setMaxRows. The SQL is ordered, so the
            // first maxRows rows are exactly the requested result.
            moreRows = false;
          } else {
            moreRows = resultSet.next();
            if ( moreRows ) {
              ++stmt.rowCount;
            }
          }
        } else {
          currPartialResultIdx++;
          moreRows = currPartialResultIdx < partialResult.size();
        }
      }
    } catch ( SQLException e ) {
      if ( stmt == null ) {
        throw Util.newError( e, message );
      } else {
        throw stmt.handle( e );
      }
    } finally {
      if ( stmt != null ) {
        stmt.close();
      }
    }
  }

    private Execution getExecution(Context context) {
        ExecutionContext executionContext = ExecutionContext.currentOrNull();
        if (executionContext == null) {
            //need for virtual cubes need investigate
            final Statement statement = context.getConnectionWithDefaultRole().getInternalStatement();
            return new ExecutionImpl(statement, ExecuteDurationUtil.executeDurationValue(context));
        } else {
            return executionContext.getExecution();
        }
    }

    @Override
public TupleList readMembers(
    Context context,
    TupleList partialResult,
    List<List<RolapMember>> newPartialResult ) {
    int memberCount = countMembers();
    rolapToOrdinalMap = new HashMap<>();
    while ( true ) {
      missedMemberCount = 0;
      int memberCountBefore = memberCount;

      prepareTuples( context, partialResult, newPartialResult, targets );

      memberCount = countMembers();
      if ( missedMemberCount == 0 ) {
        // We have successfully read all members. This is always the
        // case in a regular hierarchy. In a parent-child hierarchy
        // it may take several passes, because we cannot create a member
        // before we create its parent.
        break;
      }
      if ( memberCount == memberCountBefore ) {
        // This pass made no progress. This must be because of a cycle.
        throw Util.newError(
          "Parent-child hierarchy contains cyclic data" );
      }
    }

    assert targets.size() == 1;

    return new UnaryTupleList(
      bumpNullMember(
        targets.get( 0 ).close() ) );
  }

  protected List<Member> bumpNullMember( List<Member> members ) {
    if ( !members.isEmpty()
      && ( (RolapMemberBase) members.get( members.size() - 1 ) ).getKey()
      == Util.sqlNullValue ) {
      Member removed = members.remove( members.size() - 1 );
      members.add( 0, removed );
    }
    return members;
  }

  /**
   * Returns the number of members that have been read from all targets.
   *
   * @return Number of members that have been read from all targets
   */
  private int countMembers() {
    int n = 0;
    for ( TargetBase target : targets ) {
      if ( target.getList() != null ) {
        n += target.getList().size();
      }
    }
    return n;
  }

  @Override
public TupleList readTuples(
    Context context,
    TupleList partialResult,
    List<List<RolapMember>> newPartialResult ) {
    // The following algorithm will first group targets based on the cubes
    // that are applicable to each.  This allows loading the tuple data
    // in groups that join correctly to the associated fact table.
    // Once each group has been loaded, results of each are
    // brought together in a crossjoin, and then projected into a new
    // tuple list based on the ordering originally specified by the
    // targets list.
    // For non-virtual cube queries there is only a single
    // targetGroup.
    List<List<TargetBase>> targetGroups = groupTargets(
      targets,
      constraint.getEvaluator().getQuery() );
    List<TupleList> tupleLists = new ArrayList<>();

    for ( List<TargetBase> targetGroup : targetGroups ) {
      boolean allTargetsAtAllLevel = targetGroup.stream()
        .allMatch( t -> t.getLevel().isAll() );

      if ( allTargetsAtAllLevel ) {
        continue;
      }

      prepareTuples(
        context, partialResult, newPartialResult, targetGroup );

      int size = targetGroup.size();
      final Iterator<Member>[] iter = new Iterator[ size ];
      for ( int i = 0; i < size; i++ ) {
        TargetBase t = targetGroup.get( i );
        iter[ i ] = t.close().iterator();
      }
      List<Member> members = new ArrayList<>();
      while ( iter[ 0 ].hasNext() ) {
        for ( int i = 0; i < size; i++ ) {
          members.add( iter[ i ].next() );
        }
      }
      tupleLists.add(
        size + emptySets == 1
          ? new UnaryTupleList( members )
          : new ListTupleList( size + emptySets, members ) );
    }

    if ( tupleLists.isEmpty() ) {
      return TupleCollections.emptyList( targets.size() );
    }

    TupleList tupleList = CrossJoinFunDef.mutableCrossJoin( tupleLists );
    if ( !tupleList.isEmpty() && targetGroups.size() > 1 ) {
      tupleList = projectTupleList( tupleList );
    }

    // need to hierarchize the columns from the enumerated targets
    // since we didn't necessarily add them in the order in which
    // they originally appeared in the cross product
    int enumTargetCount = getEnumTargetCount();
    if ( enumTargetCount > 0 ) {
      tupleList = hierarchizeTupleList( tupleList, false );
    }
    return tupleList;
  }

  /**
   * Projects the attributes using the original ordering in targets, then copies to a ArrayTupleList (the .project
   * method returns a basic TupleList without support for methods like .remove, which may be needed downstream).
   */
  private TupleList projectTupleList( TupleList tupleList ) {
    tupleList = tupleList.project( getLevelIndices( tupleList, targets ) );
    TupleList arrayTupleList = new ArrayTupleList(
      tupleList.getArity(),
      tupleList.size() );
    arrayTupleList.addAll( tupleList );
    return arrayTupleList;
  }

  /**
   * Gets an array of the indexes of tuple attributes as they appear in the target ordering.
   */
  private int[] getLevelIndices(
    TupleList tupleList, List<TargetBase> targets ) {
    assert !tupleList.isEmpty();
    assert targets.size() == tupleList.get( 0 ).size();

    int[] indices = new int[ targets.size() ];
    List<Member> tuple = tupleList.get( 0 );
    int i = 0;
    for ( TargetBase target : targets ) {
      indices[ i++ ] = getIndexOfLevel( target.getLevel(), tuple );
    }
    return indices;
  }

  /**
   * Find the index of level in the tuple, throwing if not found.
   */
  private int getIndexOfLevel( RolapLevel level, List<Member> tuple ) {
    for ( int i = 0; i < tuple.size(); i++ ) {
      if ( tuple.get( i ).getLevel().equals( level ) ) {
        return i;
      }
    }
    throw new IllegalArgumentException(
      new StringBuilder("Internal error: Couldn't find level ").append(level.getName()).append(" in tuple.").toString() );
  }


  /**
   * Groups targets into lists based on those which have common cube joins. For example, [Gender] and [Marital Status]
   * each join to the [Sales] cube exclusively and would fall into a single target group.  [Product] joins to both
   * [Sales] and [Warehouse], so would fall into a separate group.
   *
   * This grouping is used for native evaluation of virtual cubes, where we need to make sure that native SQL queries
   * include those fact tables which are applicable to the levels in a crossjoin
   */
  private List<List<TargetBase>> groupTargets(
    List<TargetBase> targets, Query query ) {
    List<List<TargetBase>> targetGroupList = new ArrayList<>();

    if ( ( (RolapCube) query.getCube() ) instanceof RolapPhysicalCube ) {
      targetGroupList.add( targets );
      return targetGroupList;
    }
    if ( inapplicableTargetsPresent( getBaseCubeCollection( query ), targets ) ) {
      return Collections.emptyList();
    }

    Map<TargetBase, List<RolapCube>> targetToCubeMap =
      getTargetToCubeMap( targets, query );

    List<TargetBase> addedTargets = new ArrayList<>();

    for ( TargetBase target : targets ) {
      if ( addedTargets.contains( target ) ) {
        continue;
      }
      addedTargets.add( target );
      List<TargetBase> groupList = new ArrayList<>();
      targetGroupList.add( groupList );
      groupList.add( target );

      for ( TargetBase compareTarget : targets ) {
        if ( target == compareTarget
          || addedTargets.contains( compareTarget ) ) {
          continue;
        }
        if ( targetToCubeMap.get( target ).equals(
          targetToCubeMap.get( compareTarget ) ) ) {
          groupList.add( compareTarget );
          addedTargets.add( compareTarget );
        }
      }
    }
    return targetGroupList;
  }

  /**
   * Constructs a map of targets to the list of applicable cubes. E.g. [Product] -> [ Sales, Warehouse ] [Gender] -> [
   * Sales ] It determines the list of cubes first based on the cubes in the query.  E.g. a query with [Unit Sales] as
   * the only measure would only have the [Sales] cube as potentially applicable.
   *
   * If the dimension has *no* cubes relevant to the current query, the method falls back to looking at all cubes in the
   * virtual cube.
   *
   * This method is only expected to be called when the cube associated with the current cube is virtual.
   */
  private Map<TargetBase, List<RolapCube>> getTargetToCubeMap(
    List<TargetBase> targets, Query query ) {
    assert ( (RolapCube) query.getCube() ) instanceof RolapVirtualCube;
    Collection<RolapCube> cubesFromQuery = query.getBaseCubes().stream().map( RolapCube.class::cast ).toList();
    assert cubesFromQuery != null;
    Collection<RolapCube> baseCubesAssociatedWithVirtual =
      getBaseCubeCollection( query );

    Map<TargetBase, List<RolapCube>> targetMap = new HashMap<>();

    for ( TargetBase target : targets ) {
      targetMap.put( target, new ArrayList<>() );
    }

    for ( TargetBase target : targets ) {
      // first try to map to cubes in the query
      mapTargetToCubes( cubesFromQuery, targetMap, target );
      if ( targetMap.get( target ).isEmpty() ) {
        // none found, map against all base cubes in the virtual
        // so we don't have an empty list.
        mapTargetToCubes(
          baseCubesAssociatedWithVirtual,
          targetMap, target );
      }
    }
    return targetMap;
  }

  private void mapTargetToCubes(
    Collection<RolapCube> cubes, Map<TargetBase, List<RolapCube>> targetMap,
    TargetBase target ) {
    for ( RolapCube cube : cubes ) {
      if ( targetIsOnBaseCube( target, cube ) ) {
        targetMap.get( target ).add( cube );
      }
    }
  }

  /**
   * Sets the current member for those targets that retrieve their column values from native sql
   *
   * @param partialRow if set, previously cached result set
   */
  private void resetCurrMembers( List<RolapMember> partialRow ) {
    int nativeTarget = 0;
    for ( TargetBase target : targets ) {
      if ( target.srcMembers == null ) {
        // if we have a previously cached row, use that by picking
        // out the column corresponding to this target; otherwise,
        // we need to retrieve a new column value from the current
        // result set
        if ( partialRow != null ) {
          target.setCurrMember( partialRow.get( nativeTarget++ ) );
        } else {
          target.setCurrMember( null );
        }
      }
    }
  }

  /**
   * Recursively forms the cross product of a row retrieved through sql with each of the targets that contains an
   * enumerated set of members.
   *
   * @param currEnumTargetIdx current enum target that recursion is being applied on
   * @param currTargetIdx     index within the list of a targets that currEnumTargetIdx corresponds to
   * @param nEnumTargets      number of targets that have enumerated members
   * @param srcMemberIdxes    for each enumerated target, the current member to be retrieved to form the current cross
   *                          product row
   * @param stmt              Statement containing the result set corresponding to rows retrieved through native SQL
   * @param message           Message to issue on failure
   */
  private void addTargets(
    int currEnumTargetIdx,
    int currTargetIdx,
    int nEnumTargets,
    int[] srcMemberIdxes,
    SqlStatement stmt,
    String message ) {
    // loop through the list of members for the current enum target
    TargetBase currTarget = targets.get( currTargetIdx );
    for ( int i = 0; i < currTarget.srcMembers.size(); i++ ) {
      srcMemberIdxes[ currEnumTargetIdx ] = i;
      // if we're not on the last enum target, recursively move
      // to the next one
      if ( currEnumTargetIdx < nEnumTargets - 1 ) {
        int nextTargetIdx = currTargetIdx + 1;
        for ( ; nextTargetIdx < targets.size(); nextTargetIdx++ ) {
          if ( targets.get( nextTargetIdx ).srcMembers != null ) {
            break;
          }
        }
        addTargets(
          currEnumTargetIdx + 1, nextTargetIdx, nEnumTargets,
          srcMemberIdxes, stmt, message );
      } else {
        // form a cross product using the columns from the current
        // result set row and the current members that recursion
        // has reached for the enum targets
        int column = 0;
        int enumTargetIdx = 0;
        for ( TargetBase target : targets ) {
          if ( target.srcMembers == null ) {
            try {
              column = target.addRow( stmt, column );
            } catch ( Exception e ) {
              throw Util.newError( e, message );
            }
          } else {
            RolapMember member =
              target.srcMembers.get(
                srcMemberIdxes[ enumTargetIdx++ ] );
            target.getList().add( member );
          }
        }
      }
    }
  }

  /**
   * Retrieves the current members fetched from the targets executed through sql and form tuples, adding them to
   * partialResult
   *
   * @param partialResult list containing the columns and rows corresponding to data fetched through sql
   */
  private void savePartialResult( List<List<RolapMember>> partialResult ) {
    List<RolapMember> row = new ArrayList<>();
    for ( TargetBase target : targets ) {
      if ( target.srcMembers == null ) {
        row.add( target.getCurrMember() );
      }
    }
    partialResult.add( row );
  }

  BuiltSql makeLevelMembersSql(
          Context<?> context, List<TargetBase> targetGroup ) {
    // In the case of a virtual cube, if we need to join to the fact
    // table, we do not necessarily have a single underlying fact table,
    // as the underlying base cubes in the virtual cube may all reference
    // different fact tables.
    //
    // Therefore, we need to gather the underlying fact tables by going
    // through the list of measures referenced in the query.  And then
    // we generate one sub-select per fact table, joining against each
    // underlying fact table, unioning the sub-selects.
    RolapCube cube = null;
    boolean virtualCube = false;
    if ( constraint instanceof SqlContextConstraint sqlConstraint ) {
      Query query = constraint.getEvaluator().getQuery();
      cube = (RolapCube) query.getCube();
      if ( sqlConstraint.isJoinRequired() ) {
        virtualCube = cube instanceof RolapVirtualCube;
      }
    }

    if ( virtualCube ) {
      Query query = constraint.getEvaluator().getQuery();

      // Make fact table appear in fixed sequence

      final Collection<RolapCube> baseCubes =
        getBaseCubeCollection( query );
      Collection<RolapCube> fullyJoiningBaseCubes =
        getFullyJoiningBaseCubes( baseCubes, targetGroup );
      if ( fullyJoiningBaseCubes.isEmpty() ) {
        return sqlForEmptyTuple( context, baseCubes );
      }
      // generate sub-selects, each one joining with one of
      // the fact table referenced
      List<BestFitColumnType> types = null;
      BuiltSql lastGuarded = null;
      final List<org.eclipse.daanse.sql.statement.api.model.Statement> unionInputs =
        new java.util.ArrayList<>();

      final int savepoint =
        getEvaluator( constraint ).savepoint();

      try {
        for ( RolapCube baseCube : fullyJoiningBaseCubes ) {
          // Use the measure from the corresponding base cube in the
          // context to find the correct join path to the base fact
          // table.
          //
          // The first non-calculated measure is fine since the
          // constraint logic only uses it
          // to find the correct fact table to join to.
          Member measureInCurrentbaseCube = null;
          for ( Member currMember : baseCube.getMeasures() ) {
            if ( !currMember.isCalculated() ) {
              measureInCurrentbaseCube = currMember;
              break;
            }
          }

          if ( measureInCurrentbaseCube == null ) {
            // Couldn't find a non-calculated member in this cube.
            // Pick any measure and the code will fallback to
            // the fact table.
            if ( LOGGER.isDebugEnabled() ) {
              LOGGER.debug(
                "No non-calculated member found in cube {}", baseCube.getName() );
            }
            measureInCurrentbaseCube =
              baseCube.getMeasures().get( 0 );
          }

          // Force the constraint evaluator's measure
          // to the one in the base cube.
          getEvaluator( constraint )
            .setContext( measureInCurrentbaseCube );

          // Generate the select statement for the current base cube.
          // Make sure to pass WhichSelect.NOT_LAST if there are more
          // than one base cube and it isn't the last one so that
          // the order by clause is not added to unionized queries
          // (that would be illegal SQL)
          final BuiltSql guarded =
            generateSelectForLevels(
              context, baseCube,
              fullyJoiningBaseCubes.size() == 1
                ? WhichSelect.ONLY
                : WhichSelect.NOT_LAST,
              targetGroup );
          lastGuarded = guarded;
          unionInputs.add( guarded.statement() );
          types = guarded.render().columnTypes();
        }
      } finally {
        // Restore the original measure member
        getEvaluator( constraint ).restore( savepoint );
      }

      if ( fullyJoiningBaseCubes.size() == 1 ) {
        // Because there is only one virtual cube to
        // join on, we can swap the union query by
        // the original one.
        return lastGuarded;
      } else {
        // The per-cube statements composed as a SetOperation node in the FROM:
        //   select * from (<sub1> union <sub2> ...) as unionQuery order by 1, 2, ...
        // The order-by columns need to be ordinals (1, 2, ...), not column names/expressions. The
        // per-dialect null-collation of an ordinal key is decided by the RENDERER
        // (requiresUnionOrderByOrdinal); the SortSpec.nullable passed
        // here is ignored for Ordinal keys.
        boolean nullable = false;
        org.eclipse.daanse.sql.statement.api.SelectStatementBuilder wrapper =
          org.eclipse.daanse.sql.statement.api.SelectStatementBuilder.create();
        // Diagnostic provenance on the WRAPPER select (rendered only when comments are on). The
        // union ARMS render compact per the renderer's set-input contract, so their own
        // header/projection comments are not emitted — the wrapper names the composition instead.
        wrapper.header( "virtual cube union (" + fullyJoiningBaseCubes.size() + " base cubes)" );
        wrapper.footerComment( "tuples via " + constraint.getClass().getSimpleName()
          + " (union of " + fullyJoiningBaseCubes.size() + " base cubes)" );
        wrapper.project( org.eclipse.daanse.sql.statement.api.Expressions.star(), null );
        wrapper.from( org.eclipse.daanse.sql.statement.api.From.set(
          new org.eclipse.daanse.sql.statement.api.model.SetOperation(
            org.eclipse.daanse.sql.statement.api.model.SetOperation.SetOp.UNION,
            unionInputs, java.util.List.of(), java.util.Optional.empty() ),
          org.eclipse.daanse.sql.statement.api.model.TableAlias.of( "unionQuery" ) ) );
        for ( int i = 0; i < types.size(); i++ ) {
          wrapper.orderOn(
            org.eclipse.daanse.sql.statement.api.Expressions.ordinal( i + 1 ),
            new org.eclipse.daanse.sql.statement.api.model.SortSpec(
              org.eclipse.daanse.sql.statement.api.model.SortDirection.ASC, nullable,
              org.eclipse.daanse.sql.statement.api.model.NullOrder.LAST, false ) );
        }
        org.eclipse.daanse.sql.statement.api.model.SelectStatement wrapperStatement = wrapper.build();
        String sql = SqlRender.render( wrapperStatement, context.getDialect() ).sql();
        return new BuiltSql( wrapperStatement, RenderedSql.of( sql, types ) );
      }

    } else {
      // This is the standard code path with regular single-fact table
      // cubes.
      return generateSelectForLevels(
        context, cube, WhichSelect.ONLY, targetGroup );
    }
  }

  /**
   * Returns true if one or more targets in targetGroup do not fully join to the set of base cubes.  False otherwise. If
   * there are inapplicable targets present then the tuple resulting from native evaluation would necessarily be empty.
   *
   * Special case is if we determine that one or more measures would shift the context of the given target, in which
   * case we cannot determine whether the target is truly inapplicable. (for example, if a measure is wrapped in
   * ValidMeasure then we the presence of the target won't necessarily result in an empty tuple.)
   */
  private boolean inapplicableTargetsPresent(
    Collection<RolapCube> baseCubes, List<TargetBase> targetGroup ) {
    List<TargetBase> targetListCopy = new ArrayList<>(targetGroup);
    for ( TargetBase target : targetGroup ) {
      if ( targetHasShiftedContext( target ) ) {
        targetListCopy.remove( target );
      }
    }
    return getFullyJoiningBaseCubes( baseCubes, targetListCopy ).isEmpty();
  }

  private Collection<RolapCube> getFullyJoiningBaseCubes(
    Collection<RolapCube> baseCubes, List<TargetBase> targetGroup ) {
    final Collection<RolapCube> fullyJoiningCubes =
      new ArrayList<>();

    for ( RolapCube baseCube : baseCubes ) {
      boolean allTargetsJoin = true;
      for ( TargetBase target : targetGroup ) {
        if ( !targetIsOnBaseCube( target, baseCube ) ) {
          allTargetsJoin = false;
        }
      }
      if ( allTargetsJoin ) {
        fullyJoiningCubes.add( baseCube );
      }
    }
    return fullyJoiningCubes;
  }

  private boolean targetHasShiftedContext( TargetBase target ) {
    Set<Member> measures = new HashSet<>(constraint.getEvaluator().getQuery().getMeasuresMembers());
    for ( Member measure : measures ) {
      if ( measure.isCalculated()
        && Util.containsValidMeasure(
        measure.getExpression() ) ) {
        return true;
      }
    }
    Set<Member> membersInMeasures =
      MeasureConflictDetector.getMembersNestedInMeasures( measures );
    return membersInMeasures.contains(
      target.getLevel().getHierarchy().getAllMember() );
  }

  /**
   * Retrieves all base cubes associated with the cube specified by query. (not just those applicable to the current
   * query)
   */
  Collection<RolapCube> getBaseCubeCollection( final Query query ) {
    if ( ( (RolapCube) query.getCube() ) instanceof RolapPhysicalCube ) {
      return Collections.singletonList( (RolapCube) query.getCube() );
    }
    Set<RolapCube> cubes = new TreeSet<>( new RolapCubeComparator() );
    for ( Member member : getMeasures( query ) ) {
      if ( member instanceof RolapStoredMeasure rolapStoredMeasure) {
        cubes.add( rolapStoredMeasure.getCube() );
      } else if ( member instanceof RolapHierarchy.RolapCalculatedMeasure rolapCalculatedMeasure ) {
        RolapCube baseCube = ( rolapCalculatedMeasure ).getBaseCube();
        if ( baseCube != null ) {
          cubes.add( baseCube );
        }
      }
    }
    return cubes;
  }

  private List<Member> getMeasures( Query query ) {
    return ( (RolapCube) query.getCube() ).getMeasures();
  }

  BuiltSql sqlForEmptyTuple(
    final Context context,
    final Collection<RolapCube> baseCubes ) {
    // "select 0 from <fact> where 1 = 0" — a never-matching query used when no base cube fully joins.
    var b = org.eclipse.daanse.sql.statement.api.SelectStatementBuilder.create();
    b.project( org.eclipse.daanse.sql.statement.api.Expressions.literal( 0, org.eclipse.daanse.jdbc.db.api.type.Datatype.INTEGER ), null );
    b.from( org.eclipse.daanse.rolap.common.sqlbuild.RelationFromMapper.from(baseCubes.iterator().next().getFact() ) );
    b.where( org.eclipse.daanse.sql.statement.api.Predicates.alwaysFalse() );
    org.eclipse.daanse.sql.statement.api.model.SelectStatement statement = b.build();
    return new BuiltSql( statement, SqlRender.render( statement, context.getDialect() ) );
  }

  /**
   * Generates the SQL string corresponding to the levels referenced.
   *
   * @param context  Ccontext
   * @param baseCube    this is the cube object for regular cubes, and the underlying base cube for virtual cubes
   * @param whichSelect Position of this select statement in a union
   * @param targetGroup the set of targets for which to generate a select
   * @return SQL statement string and types
   */
  BuiltSql generateSelectForLevels(
    Context<?> context,
    RolapCube baseCube,
    WhichSelect whichSelect, List<TargetBase> targetGroup ) {
    String s =
      "while generating query to retrieve members of level(s) " + targets;

    Evaluator evaluator = getEvaluator( constraint );
    AggStar aggStar = chooseAggStar( constraint, evaluator, baseCube, context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class) );

    // The route is decided up front, before any QueryRecorder work: the generic mapper is
    // authoritative for (a) a standalone single-target SELECT (no aggregate table, no enumerated
    // source members) on a level it can build, whose constraint translates to a contribution,
    // (b) a virtual-cube UNION ARM (whichSelect != ONLY — the same SELECT per base cube with the
    // ORDER BY suppressed; the union wrapper orders by ordinals) and (c) a MULTI-TARGET tuple read
    // (each target's levels in target order, non-first targets joined through their star chains).
    // Every other shape falls through to the QueryRecorder construction below — on the builder
    // routes no recorder is constructed at all.
    // A target with enumerated source members is answered Java-side (addTargets) and
    // the recorder SKIPS it in its addLevelMemberSql loop — the SQL-carrying target set is the
    // srcMembers==null subset. The routing decides on THAT subset: 0 SQL targets keep the recorder
    // (an all-enumerated group carries no level SQL to build), 1 routes the single-target read,
    // >1 the tuple read — the recorder projects exactly these targets (unit-pinned).
    final List<TargetBase> sqlTargetGroup = sqlTargets( targetGroup );
    // The T4 decline context ("tuple/arm shape outside the builder's authoritative
    // scope"), captured for the sub-census of the SURVIVING recorder events — logged only after
    // the builder routes below decline too. These three locals + the capture at
    // the reason assignment + the single logT4SubCensus call before the recorder construction
    // are the ONLY SqlTupleReader touch points of the sub-census (classifier lives in
    // TupleSqlMapper).
    List<RolapLevel> t4CensusLevels = null;
    org.eclipse.daanse.rolap.common.sql.ConstraintContribution t4CensusContribution = null;
    boolean t4CensusUnionArm = false;
    // The multi-target and single-target "recorder path" census lines are
    // DEFERRED into this Runnable and emitted only AFTER the builder routes below have had
    // their chance to rescue the read to the builder (noFactAdjacentTupleSql / computedTupleSql).
    // Emitting them inline would log phantom recorder-path lines for the reads that actually route
    // to the builder authoritatively. Log-only: no side effect, the rescued reads return before
    // this Runnable is invoked.
    Runnable recorderPathCensus = null;
    if ( aggStar != null ) {
      // AGG ROUTER: the authoritative
      // collapsed shortcut keeps precedence, then the classified labels build authoritatively
      // (agg-mt-collapsed, agg-mt-factjoin, agg-st-factjoin, agg-mt-dimjoin,
      // agg-st-neutral, agg-st-dimjoin and agg-descendants). The residue — union arms (agg-arm),
      // untranslatable contributions (agg-unavailable:*) and the agg-mixed gate — stays on
      // the recorder below.
      final java.util.Optional<BuiltSql> aggAuthoritative = aggAuthoritativeLevelMembersSql(
        context, baseCube, aggStar, whichSelect, targetGroup, sqlTargetGroup );
      if ( aggAuthoritative.isPresent() ) {
        return aggAuthoritative.get();
      }
      RolapUtil.SQL_GEN_LOGGER.debug(
        "level members: recorder path (whichSelect={} aggStar={} targets={})",
        whichSelect, true, targetGroup.size() );
    } else if ( sqlTargetGroup.isEmpty() ) {
      // Every target is enumerated: no level SQL to build — the recorder keeps the (degenerate)
      // read.
      RolapUtil.SQL_GEN_LOGGER.debug(
        "level members {}: recorder path (enumerated src members)",
        targetGroup.get( 0 ).getLevel().getUniqueName() );
    } else if ( whichSelect != WhichSelect.ONLY || sqlTargetGroup.size() > 1 ) {
      // Union arm (whichSelect=NOT_LAST — LAST never reaches this method: makeLevelMembersSql
      // passes ONLY or NOT_LAST for every arm) and/or multi-target tuple read. The recorder
      // semantics reproduced by the mapper (derived from addLevelMemberSql):
      //   - a non-ONLY arm emits NO ORDER BY at all (the ordinal order-by, the default key
      //     order-by and the parent-key order-by are all gated on whichSelect==ONLY/LAST) — the
      //     union wrapper orders by ordinals instead. The parent-key PROJECTION is also gated
      //     (LAST||ONLY): the mapper suppresses it together with the level ordering
      //     (emitOrderBy=false), so the parent-child arm (routed below) models it too.
      //   - a multi-target group emits each target's levels in target order; every non-first
      //     target joins to the fact through its star chain (the recorder's per-level
      //     joinLevelTableToFactTable), so the fact join is mandatory here.
      final boolean unionArm = whichSelect != WhichSelect.ONLY;
      final List<RolapLevel> targetLevels = sqlTargetGroup.stream()
        .map( TargetBase::getLevel ).toList();
      final java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution>
        contribution = constraint.toContribution( baseCube, aggStar );
      String reason = null;
      if ( contribution.isEmpty() ) {
        reason = "constraint " + constraint.getClass().getSimpleName()
          + " not expressible as a contribution";
      } else if ( !contribution.get().requiresFactJoin() ) {
        // A no-fact-join contribution is the
        // recorder's DISCONNECTED-COMPONENT assembly — the comma-product read
        // (TupleSqlMapper.productTupleLevelMembersSql: FROM = first target's subset + further
        // targets' tables comma-appended, their internal join equalities as trailing WHERE
        // conjuncts). Routed authoritatively when the shape gate passes; the gate requires no
        // native order (the recorder's arm/order interplay is not part of this shape) and logs
        // its grep-stable "product-tuple decline reason=" census line for the residue
        // (single-target arm, level-unsupported, projection/join/predicate outside the targets'
        // relations), which keeps the recorder via the reason below.
        final org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = contribution.get();
        if ( c.nativeOrder().isEmpty()
            && TupleSqlMapper.supportsProductTupleRead( targetLevels, c.joinTables(),
                c.orderedPredicates() ) ) {
          RolapUtil.SQL_GEN_LOGGER.debug(
            "level members: product tuple read (whichSelect={} targets={}) -> builder authoritative",
            whichSelect, sqlTargetGroup.size() );
          return SqlBuildGuard.build( context.getDialect(),
            context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
              ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class ),
            () -> TupleSqlMapper.productTupleLevelMembersSql( targetLevels, true,
              c.where(), c.orderedPredicates(), c.nativeOrder(), c.nativeHaving(), baseCube,
              !unionArm ) );
        }
        reason = "contribution carries no fact join";
        // A native TopCount measure
        // ORDER on a union arm is not a decline. The recorder emits the measure projection +
        // measure ORDER BY on every arm (addConstraintOps runs without arm knowledge —
        // illegal-but-emitted SQL inside the union, reproduced by the builder:
        // emitOrderBy=false suppresses only the LEVEL ordering, the native order is always
        // carried). A native HAVING alone is also fine.
      } else if ( targetLevels.stream().anyMatch( RolapLevel::isParentChild )
          && TupleSqlMapper.supportsTupleReadAllowingParentChild( targetLevels, baseCube ) ) {
        // Parent-child is the ONLY strict-gate
        // blocker here — the PC-relaxed gate routes the read through the SAME tupleLevelMembersSql call
        // below (reason stays null). The emission already exists in projectTargetLevels: parent
        // key ahead of the level key, nulls-first collation incl. non-null nullParentValue; a
        // NOT_LAST arm suppresses the parent PROJECTION with the level ordering (emitOrderBy=
        // false). Checked BEFORE the strict gate so its "level-unsupported … pc=true" census line
        // does not fire for this shape — it narrows to PC reads with a second blocker.
        RolapUtil.SQL_GEN_LOGGER.debug(
          "level members: pc-tuple (PC-relaxed gate, whichSelect={} targets={}) -> builder authoritative",
          whichSelect, sqlTargetGroup.size() );
      } else if ( !TupleSqlMapper.supportsTupleRead( targetLevels, baseCube ) ) {
        // Capability gate: the sub-reason (parent-child/computed/view level, no star
        // key on the base cube, chain reaching a different fact, out-of-scope projection) is logged
        // by supportsTupleRead itself on the gen channel for the census attribution.
        // The `projection-scope` route — the virtual-cube
        // alias-mapping route, authoritative BEFORE the T4 decline: strict per-level gates pass
        // but the star-scope check declines because the VIRTUAL-cube target levels carry OTHER
        // relation aliases than the base cube's star chains. The recorder base-maps the
        // hierarchy (findBaseCubeHierarchy in addLevelMemberSql) before projecting; the route
        // does the same — baseMappedTargetLevels, then the standard tuple emission over the
        // mapped levels (whose chains/subsets now resolve; quiet gate — the loud gate above
        // already attributed the shape). Routing parity: the SetConstraint family reads through
        // the chain-contiguous variant, everything else through the standard fold.
        final List<RolapLevel> mapped = targetLevels.stream().allMatch( TupleSqlMapper::supports )
          ? baseMappedTargetLevels( baseCube, targetLevels ) : null;
        if ( mapped != null && !mapped.equals( targetLevels )
            && TupleSqlMapper.supportsTupleReadQuiet( mapped, baseCube ) ) {
          final org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = contribution.get();
          final boolean setFamily =
            constraint instanceof org.eclipse.daanse.rolap.common.nativize.RolapNativeSet.SetConstraint;
          RolapUtil.SQL_GEN_LOGGER.debug(
            "level members: projection-scope base-mapped {} (whichSelect={} targets={}) -> builder authoritative",
            unionArm ? "union arm" : "tuple read", whichSelect, sqlTargetGroup.size() );
          return SqlBuildGuard.build( context.getDialect(),
            context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
              ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class ),
            () -> setFamily
              ? TupleSqlMapper.tupleLevelMembersSqlRecorderJoinOrder( mapped,
                  true, c.where(), c.joinTables(), c.orderedPredicates(),
                  c.nativeOrder(), c.nativeHaving(), c.factJoinRequired(), baseCube, !unionArm )
              : TupleSqlMapper.tupleLevelMembersSql( mapped, true,
                  c.where(), c.joinTables(), c.orderedPredicates(), c.nativeOrder(),
                  c.nativeHaving(), c.factJoinRequired(), baseCube, !unionArm ) );
        }
        reason = "tuple/arm shape outside the builder's authoritative scope";
        // T4 capture (census fires below only if no builder route takes the read).
        t4CensusLevels = targetLevels;
        t4CensusContribution = contribution.get();
        t4CensusUnionArm = unionArm;
      }
      if ( reason == null ) {
        final org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = contribution.get();
        // Routing parity: EVERY SetConstraint-family read through this
        // branch executes the CHAIN-CONTIGUOUS variant (tupleLevelMembersSqlRecorderJoinOrder —
        // the exact addToFrom replay reproducing the recorder's join sequence, including a
        // displaced snowflake table that the breadth-first fold would order differently). Routing
        // ALL SetConstraint reads — not only the calc-lifted ones — is
        // neutral for the SetConstraint events by construction: the two
        // variants share every emission except the fact-side join SEQUENCE ("same steps, same
        // ON conditions"), so wherever the breadth-first fold already matched the recorder,
        // the fold's sequence WAS the recorder's — which is what the chain-contiguous variant
        // replays (the recorder's order for computed-tuple and calc-set reads). Pinned
        // both ways on a matching shape in TupleSqlMapperTupleReadTest.
        final boolean setFamily =
          constraint instanceof org.eclipse.daanse.rolap.common.nativize.RolapNativeSet.SetConstraint;
        RolapUtil.SQL_GEN_LOGGER.debug(
          "level members: {} (whichSelect={} targets={}) -> builder authoritative",
          unionArm ? "union arm" : "tuple read", whichSelect, sqlTargetGroup.size() );
        return SqlBuildGuard.build( context.getDialect(),
          context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
            ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class ),
          () -> setFamily
            ? TupleSqlMapper.tupleLevelMembersSqlRecorderJoinOrder( targetLevels,
                true, c.where(), c.joinTables(), c.orderedPredicates(),
                c.nativeOrder(), c.nativeHaving(), c.factJoinRequired(), baseCube, !unionArm )
            : TupleSqlMapper.tupleLevelMembersSql( targetLevels, true,
                c.where(), c.joinTables(), c.orderedPredicates(), c.nativeOrder(), c.nativeHaving(),
                c.factJoinRequired(), baseCube, !unionArm ) );
      }
      // The first-target-no-star-key residue —
      // dominated by an (All) first/co-target on a virtual-cube arm — reads authoritatively
      // through the (All)-dropped tupleLevelMembersSql build. An empty
      // Optional keeps the recorder via the reason line below (documented decline).
      final java.util.Optional<BuiltSql> noStarKeyAuthoritative = firstTargetNoStarKeySql(
        context, baseCube, targetLevels, contribution, unionArm );
      if ( noStarKeyAuthoritative.isPresent() ) {
        return noStarKeyAuthoritative.get();
      }
      // The routing line, extended with the blocking reason (grep-stable prefix). Deferred
      // until after the builder routes below so a builder-rescued read does not log this phantom.
      final String reasonForCensus = reason;
      recorderPathCensus = () -> RolapUtil.SQL_GEN_LOGGER.debug(
        "level members: recorder path (whichSelect={} aggStar={} targets={} reason={})",
        whichSelect, false, sqlTargetGroup.size(), reasonForCensus );
    } else {
      TargetBase only = sqlTargetGroup.get( 0 );
        // Compute the contribution once; it steers every branch below. Same baseCube the recorded
        // ops would receive — the contribution must translate against the same star.
        java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> contribution =
          constraint.toContribution( baseCube, aggStar );
        // An unrestricted constraint (plain level members) on a shape the dialect overload renders:
        // supportsAllowingExpressions covers plain tables/joins, view/inline relations (incl. a view
        // nested in a join) and computed (expression) columns; the dialect overload renders each via
        // the dialect-aware subset/whole-relation + dialect-specific expression SQL, so all are built
        // authoritatively here (result-verified). supportsParentChildComputedParent adds the
        // `pc-parent-expr` shape: a PC level whose parent key is a
        // computed <SQL> expression (RTRIM(supervisor_id)) — the emission machinery already carries
        // the shape (RawVariant parent projection, non-null nullParentValue collation via
        // SortSpec.withNullSortValue).
        if ( contribution.isPresent() && contribution.get().producesPlainLevelMembers()
            && ( TupleSqlMapper.supportsAllowingExpressions( only.getLevel() )
                || TupleSqlMapper.supportsParentChild( only.getLevel() )
                || TupleSqlMapper.supportsParentChildComputedParent( only.getLevel() ) ) ) {
          RolapUtil.SQL_GEN_LOGGER.debug(
              "level-members {}: standalone unconstrained -> builder authoritative",
              only.getLevel().getUniqueName() );
          return SqlBuildGuard.build( context.getDialect(),
            context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
                ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class ),
            () -> TupleSqlMapper.levelMembersSql( only.getLevel(), true ) );
        }
        // Diagnostic: tag every level-members query with its constraint class so a builder/reference
        // divergence can be attributed to the constraint whose toContribution did not reproduce it.
        RolapUtil.SQL_GEN_LOGGER.debug(
          "level members {} constraint={} present={} restricted={}",
          only.getLevel().getUniqueName(), constraint.getClass().getSimpleName(),
          contribution.isPresent(), contribution.filter( cc -> !cc.producesPlainLevelMembers() ).isPresent() );
        if ( !TupleSqlMapper.supports( only.getLevel() ) ) {
          // A COMPUTED-EXPRESSION level (strict supports() rejects a computed key/caption/ordinal/
          // property column, supportsAllowingExpressions accepts it): route the CONSTRAINED level-members
          // read onto the builder. The constrained levelMembersSql overload renders each computed column
          // via the dialect-aware expression SQL and reproduces the recorder. A restricting contribution only
          // (!producesPlainLevelMembers): a PLAIN computed read is already built above via
          // supportsAllowingExpressions. GUARD: a multi-parent DescendantsConstraint whose parent set does
          // NOT form a rectangle (the distinct per-level key values cross WIDER than the set, e.g. cities
          // spread across several states) makes the recorder emit a factored bounding-box IN
          // ("city in (..) and state in (..)") while the builder emits the exact tuple form — that one
          // shape stays on the recorder.
          if ( TupleSqlMapper.supportsAllowingExpressions( only.getLevel() )
              && contribution.isPresent() && !contribution.get().producesPlainLevelMembers()
              && computedLevelContributionReproducesRecorder( constraint ) ) {
            final org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = contribution.get();
            final boolean formatted = context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
                ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class );
            RolapUtil.SQL_GEN_LOGGER.debug(
              "level-members {}: computed-expression constrained -> builder authoritative",
              only.getLevel().getUniqueName() );
            return SqlBuildGuard.build( context.getDialect(), formatted,
              () -> TupleSqlMapper.levelMembersSql( only.getLevel(), true, c.where(),
                c.joinTables(), c.orderedPredicates(), c.nativeOrder(), c.nativeHaving(),
                c.factJoinRequired(), baseCube ) );
          }
          // View/inline, guarded-computed and parent-child level shapes stay on the recorder:
          // the guarded non-rectangle descendants read diverges (see above); the builder is
          // not authoritative here.
          // Deferred until after the builder routes below so a builder-rescued read
          // (noFactAdjacentTupleSql / computedTupleSql) does not log this phantom.
          recorderPathCensus = () -> RolapUtil.SQL_GEN_LOGGER.debug(
            "level members {}: recorder path (level shape outside the builder's authoritative scope)",
            only.getLevel().getUniqueName() );
        } else if ( contribution.isPresent() ) {
          // A constraint that translates to a contribution is built authoritatively: unrestricted
          // (plain level members) compact or formatted per config; restricted through the constrained
          // SELECT (the 7-arg overload).
          final org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = contribution.get();
          final boolean formatted = context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
              ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class );
          if ( c.producesPlainLevelMembers() ) {
            return SqlBuildGuard.build( context.getDialect(), formatted,
              () -> TupleSqlMapper.levelMembersSql( only.getLevel(), true ) );
          }
          return SqlBuildGuard.build( context.getDialect(), formatted,
            () -> TupleSqlMapper.levelMembersSql( only.getLevel(), true, c.where(),
              c.joinTables(), c.orderedPredicates(), c.nativeOrder(), c.nativeHaving(),
              c.factJoinRequired(), baseCube ) );
        } else {
          // A constraint that does NOT translate (empty contribution) keeps the recorder: the
          // builder cannot express its restriction, and building the plain SELECT would silently
          // drop it.
          RolapUtil.SQL_GEN_LOGGER.debug(
            "level members {}: recorder path (constraint {} not expressible as a contribution)",
            only.getLevel().getUniqueName(), constraint.getClass().getSimpleName() );
        }
    }

    // The two remaining supportsTupleRead-declined tuple families (see the routing methods): the
    // no-fact-adjacent shapes ((All)-drop / same-table)
    // and the computed-expression tuple read. Both routes are recorder-independent,
    // so they are decided BEFORE any QueryRecorder work — the recorder is not built for these
    // families. Empty Optional = documented decline, recorder below.
    final java.util.Optional<BuiltSql> noFactAdjacentAuthoritative =
      noFactAdjacentTupleSql( context, baseCube, aggStar, whichSelect, targetGroup );
    if ( noFactAdjacentAuthoritative.isPresent() ) {
      return noFactAdjacentAuthoritative.get();
    }
    final java.util.Optional<BuiltSql> computedTupleAuthoritative =
      computedTupleSql( context, baseCube, aggStar, whichSelect, targetGroup );
    if ( computedTupleAuthoritative.isPresent() ) {
      return computedTupleAuthoritative.get();
    }

    // The read genuinely fell through to the recorder — the builder routes above declined. Emit
    // the deferred recorder-path census line now (no phantom for the builder-rescued reads).
    if ( recorderPathCensus != null ) {
      recorderPathCensus.run();
    }

    // The T4 sub-census for the SURVIVING recorder events (no builder route above took
    // the read). Census line only.
    if ( t4CensusLevels != null ) {
      TupleSqlMapper.logT4SubCensus( t4CensusLevels, baseCube, t4CensusUnionArm,
        t4CensusContribution.joinTables(), t4CensusContribution.orderedPredicates() );
    }

    // The tuple/level reader recorder is runtime-dead for the constraint SPI. Every
    // builder-authoritative route
    // above returned; reaching here means a read shape the builder cannot model — a
    // contribution-present TOPOLOGY decline (chain-other-fact / non-rectangle DescendantsConstraint)
    // or an inexpressible-constraint empty contribution — plus the enumerated-src-members
    // and aggStar residues. Fail loud rather than silently drop the WHERE via the recorder
    // path.
    final List<String> deadTargetLevels = targetGroup.stream()
      .filter( t -> t.getSrcMembers() == null )
      .map( t -> t.getLevel().getUniqueName() )
      .toList();
    throw new IllegalStateException(
      "tuple/member read shape not modellable by the builder: targets=" + deadTargetLevels
        + " constraint=" + constraint.getClass().getSimpleName() );
  }

  /**
   * The recorder's virtual-cube mapping ({@code addLevelMemberSql} /
   * {@code planAggReadFrom}'s twin) over a whole target list: each level whose cube hierarchy
   * is NOT of {@code baseCube} is replaced by the base cube hierarchy's level at the SAME depth
   * ({@code findBaseCubeHierarchy}). {@code null} when a target resolves no base twin — the read
   * is outside the projection-scope family then (the base-mapping route in
   * {@code generateSelectForLevels}'s T4 branch keeps the recorder).
   */
  private List<RolapLevel> baseMappedTargetLevels( RolapCube baseCube,
    List<RolapLevel> targetLevels ) {
    final List<RolapLevel> mapped = new ArrayList<>();
    for ( RolapLevel target : targetLevels ) {
      RolapHierarchy hierarchy = target.getHierarchy();
      if ( !target.isAll() && hierarchy instanceof RolapCubeHierarchy cubeHierarchy
          && baseCube != null
          && !cubeHierarchy.getCube().equalsOlapElement( baseCube ) ) {
        hierarchy = baseCube.findBaseCubeHierarchy( hierarchy );
      }
      if ( hierarchy == null ) {
        return null;
      }
      final List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
      if ( target.getDepth() >= levels.size() ) {
        return null;
      }
      mapped.add( levels.get( target.getDepth() ) );
    }
    return mapped;
  }

  /**
   * The SQL-carrying subset of a target group — the targets WITHOUT enumerated source members
   * (the recorder's {@code addLevelMemberSql} loop skips enumerated targets; they are answered
   * Java-side in {@code addTargets}). The builder/recorder route
   * is decided on this subset. Package-visible for the unit pin.
   */
  static List<TargetBase> sqlTargets( List<TargetBase> targetGroup ) {
    return targetGroup.stream().filter( t -> t.getSrcMembers() == null ).toList();
  }

  /**
   * Build the {@code first-target-no-star-key} declines authoritatively: a multi-target/union-arm
   * read whose FIRST target does
   * not resolve a star key on the base cube — dominated by an (All) first/co-target on a
   * virtual-cube arm ({@code whichSelect=NOT_LAST}, which the ONLY-gated all-dropped route never
   * reaches) plus the ONLY events that fell through its sub-shape gates. The build drops the
   * (All) targets (the recorder projects NOTHING for them) and reads the surviving subset through
   * {@code tupleLevelMembersSql} with the arm's ORDER-BY suppression; a single survivor whose
   * contribution carries no fact join anchors FROM at its hierarchy relation (the mapper's
   * fact==null branch — the single-target {@code levelMembersSql} shape).
   * <p>
   * The routing condition IS the guard (no runtime fallback): a level outside strict
   * {@code supports}, a first target that resolves its star key (or is no cube level), an
   * inexpressible constraint, an empty survivor set, or a MULTI-target survivor set that fails
   * {@code supportsTupleRead} (no resolvable fact join) keeps the recorder as a documented decline.
   */
  private java.util.Optional<BuiltSql> firstTargetNoStarKeySql(
    Context<?> context, RolapCube baseCube, List<RolapLevel> targetLevels,
    java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> contribution,
    boolean unionArm ) {
    if ( contribution.isEmpty()
        || targetLevels.stream().anyMatch( l -> !TupleSqlMapper.supports( l ) ) ) {
      return java.util.Optional.empty(); // inexpressible / level-unsupported family — not this shape
    }
    // Precisely the first-target-no-star-key sub-reason: a first target that resolves its star
    // key (or is no cube level at all) belongs to another decline family.
    if ( !( targetLevels.get( 0 ) instanceof RolapCubeLevel firstCube )
        || firstCube.getBaseStarKeyColumn( baseCube ) != null ) {
      return java.util.Optional.empty();
    }
    final List<RolapLevel> nonAll = targetLevels.stream().filter( l -> !l.isAll() ).toList();
    if ( nonAll.isEmpty() ) {
      return java.util.Optional.empty();
    }
    if ( nonAll.size() > 1 && !TupleSqlMapper.supportsTupleRead( nonAll, baseCube ) ) {
      RolapUtil.SQL_GEN_LOGGER.debug(
        "level members: recorder path (first-target-no-star-key survivor set not buildable)" );
      return java.util.Optional.empty();
    }
    final org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = contribution.get();
    final boolean formatted = context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
        ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class );
    RolapUtil.SQL_GEN_LOGGER.debug(
      "level members: first-target-no-star-key (all-dropped, targets={}) -> builder authoritative",
      targetLevels.size() );
    return java.util.Optional.of( SqlBuildGuard.build( context.getDialect(), formatted,
      () -> TupleSqlMapper.tupleLevelMembersSql( nonAll, true,
        c.where(), c.joinTables(), c.orderedPredicates(), c.nativeOrder(), c.nativeHaving(),
        c.factJoinRequired(), baseCube, !unionArm ) ) );
  }

  /**
   * The classified agg read of the agg router ({@link #aggAuthoritativeLevelMembersSql}): either a
   * HARVEST-only label (the fields
   * are {@code null} — union arm, untranslatable contribution, unclassifiable shape) or a
   * classified label with the channels the build needs. {@code targetLevels} carries
   * the BASE-cube-mapped target levels (the virtual-cube fix): a virtual-cube read's targets
   * are resolved through the base cube hierarchy exactly as {@code addLevelMemberSql} resolves
   * them, so every build projects the same levels the recorder projects.
   */
  private record AggReadPlan( String label, List<RolapLevel> targetLevels,
    List<List<RolapCubeLevel>> collapsedTargets,
    org.eclipse.daanse.rolap.common.sql.ConstraintContribution contribution,
    org.eclipse.daanse.rolap.common.sql.AggPlan aggPlan,
    java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> having,
    java.util.List<org.eclipse.daanse.rolap.common.sqlbuild.TupleSqlMapper.HavingJoin> havingJoins,
    java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> order ) {
    static AggReadPlan harvest( String label ) {
      return new AggReadPlan( label, null, null, null, null, null, null, null );
    }

    boolean classified() {
      return contribution != null;
    }
  }

  /**
   * Classification of an aggStar level/tuple read: NO pre-gate beyond {@code aggStar != null};
   * every unclassifiable/untranslatable read
   * yields a harvest label, never a silent skip. Virtual-cube targets are mapped to
   * their base cube levels before the scan (the recorder's {@code addLevelMemberSql} mapping) —
   * they carry NO star key column of their own; a mapped (virtual)
   * read always carries the {@code agg-mixed} label, never an authoritative one.
   * <ul>
   * <li>{@code whichSelect != ONLY} — {@code agg-arm} (the union-arm case);</li>
   * <li>empty contribution / absent {@code AggPlan} — {@code agg-unavailable:*}; the producer's
   *     precise bail reason is logged on the {@code daanse.sql.gen.bail} channel directly
   *     above;</li>
   * <li>plan present — {@link #classifyAggShape} derives the sub-shape label from the per-level
   *     collapsed/multi-column/dim-join predicates (the SAME
   *     {@code SqlMemberSource.isLevelCollapsed}/{@code levelContainsMultipleColumns} branch
   *     parity the mapper uses) plus the plan/contribution flags; {@code agg-unclassified}
   *     harvests.</li>
   * </ul>
   * HAVING/ORDER channel — ONE channel, mirroring the authoritative collapsed route
   * ({@code aggCollapsedLevelMembersSql}): a {@link SqlContextConstraint} (which includes the
   * whole {@code RolapNativeSet.SetConstraint} family) supplies
   * {@code levelMembersAggHavingWithJoins(aggStar)}/{@code levelMembersAggOrder(aggStar)} —
   * compiled against the AGG columns via the same {@code RolapNativeSql}+aggStar channel the
   * recorder uses, INCLUDING the compile's FROM side effects ({@code HavingJoin}s — the
   * agg-st-dimjoin case). Only a non-SCC constraint falls back to the contribution's
   * {@code nativeHaving}/{@code nativeOrder} (under an agg routing those are compiled
   * agg-substituted too, so the two channels coincide where both exist — the SCC twins are
   * preferred to keep parity with the collapsed route).
   */
  private AggReadPlan planAggRead( RolapCube baseCube, AggStar aggStar, WhichSelect whichSelect,
    List<RolapLevel> targetLevels ) {
    if ( whichSelect != WhichSelect.ONLY ) {
      return AggReadPlan.harvest( "agg-arm" );
    }
    if ( targetLevels.isEmpty() ) {
      return AggReadPlan.harvest( "agg-unclassified" );
    }
    final java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> contribution =
      constraint.toContribution( baseCube, aggStar );
    if ( contribution.isEmpty() ) {
      return AggReadPlan.harvest( "agg-unavailable:no-contribution" );
    }
    return planAggReadFrom( baseCube, aggStar, targetLevels, contribution.get() );
  }

  /**
   * The classification half of {@link #planAggRead}, over an ALREADY-OBTAINED contribution. The
   * lifted semantics run in {@code toContribution} itself, so {@link #planAggRead} is the only
   * caller.
   */
  private AggReadPlan planAggReadFrom( RolapCube baseCube, AggStar aggStar,
    List<RolapLevel> targetLevels, org.eclipse.daanse.rolap.common.sql.ConstraintContribution c ) {
    if ( c.aggPlan().isEmpty() ) {
      return AggReadPlan.harvest( "agg-unavailable:no-plan" );
    }
    final org.eclipse.daanse.rolap.common.sql.AggPlan plan = c.aggPlan().get();
    // Per-level shape scan — branch parity with the recorder/mapper predicates. Any structural
    // surprise (a non-cube level, an unresolvable star key/agg column) makes the read
    // unclassifiable, never a crash.
    // A VIRTUAL-cube read's target levels belong to the virtual cube and
    // carry NO star key column (getStarKeyColumn() == null), which would NPE the scan below. Map
    // each target through the base cube hierarchy EXACTLY as addLevelMemberSql does
    // (findBaseCubeHierarchy + same level depth), scan and build on the mapped levels.
    String label;
    boolean virtualMapped = false;
    final List<RolapLevel> mappedTargets = new ArrayList<>();
    final List<List<RolapCubeLevel>> collapsedTargets = new ArrayList<>();
    try {
      boolean allCollapsedSingle = true;
      boolean anyDimJoin = false;
      boolean anyCollapsed = false;
      for ( RolapLevel target : targetLevels ) {
        RolapHierarchy hierarchy = target.getHierarchy();
        if ( !target.isAll() && hierarchy instanceof RolapCubeHierarchy cubeHierarchy
            && baseCube != null
            && !cubeHierarchy.getCube().equalsOlapElement( baseCube ) ) {
          // Virtual-cube target: the recorder (addLevelMemberSql) walks the BASE cube hierarchy.
          hierarchy = baseCube.findBaseCubeHierarchy( hierarchy );
          virtualMapped = true;
        }
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        mappedTargets.add( levels.get( target.getDepth() ) );
        List<RolapCubeLevel> collapsed = new ArrayList<>();
        for ( int i = 0; i <= target.getDepth(); i++ ) {
          RolapLevel lvl = levels.get( i );
          if ( lvl.isAll() ) {
            continue;
          }
          RolapCubeLevel cubeLevel = (RolapCubeLevel) lvl;
          boolean isCollapsed = SqlMemberSource.isLevelCollapsed( aggStar, cubeLevel );
          boolean multipleCols = SqlMemberSource.levelContainsMultipleColumns( lvl );
          anyCollapsed |= isCollapsed;
          if ( !( isCollapsed && !multipleCols ) ) {
            allCollapsedSingle = false;
          }
          if ( isCollapsed && multipleCols
              && org.eclipse.daanse.rolap.common.sqlbuild.AggJoinPlanner.requiresJoinToDim(
                  org.eclipse.daanse.rolap.common.sqlbuild.AggJoinPlanner
                      .levelTargetExpMap( lvl, aggStar ) ) ) {
            anyDimJoin = true;
          }
          collapsed.add( cubeLevel );
        }
        collapsedTargets.add( collapsed );
      }
      // A predicate living on an agg DIM table forces the dim-side join the single-agg-table
      // collapsed build cannot express; a null table (key-expression predicate) or the agg
      // fact does not.
      boolean dimTablePredicate = plan.orderedAggPredicates().stream()
        .anyMatch( p -> p.table() != null && !( p.table() instanceof AggStar.FactTable ) );
      label = classifyAggShape( targetLevels.size() == 1, allCollapsedSingle, anyDimJoin,
        anyCollapsed, !plan.orderedAggPredicates().isEmpty(), dimTablePredicate,
        c.factJoinRequired() );
    } catch ( RuntimeException e ) {
      label = null;
    }
    if ( label == null ) {
      return AggReadPlan.harvest( "agg-unclassified" );
    }
    if ( virtualMapped ) {
      // Virtual-cube agg reads ride the agg-mixed gate (kept on the recorder).
      // The corpus-live shape (the MONDRIAN-1221 NECJ [Time].[Month] x [Store Country]) classifies
      // agg-mixed by its flags anyway; this override keeps corpus-dead virtual shapes from
      // routing to the builder without support.
      label = "agg-mixed";
    }
    // ONE HAVING/ORDER channel (see the method javadoc): the SCC agg twins, else the contribution.
    final java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> having;
    final java.util.List<org.eclipse.daanse.rolap.common.sqlbuild.TupleSqlMapper.HavingJoin> havingJoins;
    final java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> order;
    if ( constraint instanceof SqlContextConstraint scc ) {
      final SqlContextConstraint.AggHaving aggHaving = scc.levelMembersAggHavingWithJoins( aggStar );
      having = aggHaving.having();
      havingJoins = aggHaving.joins();
      order = scc.levelMembersAggOrder( aggStar );
    } else {
      having = c.nativeHaving();
      havingJoins = java.util.List.of();
      order = c.nativeOrder();
    }
    return new AggReadPlan( label, mappedTargets, collapsedTargets, c, plan, having, havingJoins,
      order );
  }

  /**
   * AGG ROUTER: the authoritative aggStar level/tuple
   * routes, in precedence order.
   * <ol>
   * <li>{@link #aggCollapsedLevelMembersSql} — the authoritative collapsed shortcut
   *     (it serves its shape already, incl. the candidate twin);</li>
   * <li>the classified labels ({@link #planAggRead} + {@link #classifyAggShape}):
   *     {@code agg-mt-collapsed} through
   *     {@code TupleSqlMapper.collapsedTupleLevelMembersSql}; {@code agg-st-factjoin},
   *     {@code agg-mt-factjoin}, {@code agg-mt-dimjoin}, {@code agg-st-neutral}
   *     and {@code agg-st-dimjoin}
   *     through {@code TupleSqlMapper.aggTupleLevelMembersSql}, with the constraint's
   *     {@code HavingJoin}s replayed.</li>
   * </ol>
   * Empty Optional = recorder: only the harvest labels remain ({@code agg-arm},
   * {@code agg-unavailable:*}, {@code agg-unclassified}). The multi-parent
   * {@link DescendantsConstraint} reads authoritatively (agg-descendants); the {@code agg-mixed}
   * mixed-collapsed multi-target and virtual-cube-mapped reads route authoritatively.
   */
  private java.util.Optional<BuiltSql> aggAuthoritativeLevelMembersSql(
    Context<?> context, RolapCube baseCube, AggStar aggStar, WhichSelect whichSelect,
    List<TargetBase> targetGroup, List<TargetBase> sqlTargetGroup ) {
    final java.util.Optional<BuiltSql> collapsedAuthoritative =
      aggCollapsedLevelMembersSql( context, baseCube, aggStar, whichSelect, targetGroup );
    if ( collapsedAuthoritative.isPresent() ) {
      return collapsedAuthoritative;
    }
    final List<RolapLevel> targetLevels = sqlTargetGroup.stream()
      .map( TargetBase::getLevel ).toList();
    final AggReadPlan plan = planAggRead( baseCube, aggStar, whichSelect, targetLevels );
    if ( !plan.classified() ) {
      return java.util.Optional.empty();
    }
    final org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = plan.contribution();
    final boolean formatted = context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
        ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class );
    RolapUtil.SQL_GEN_LOGGER.debug(
      "level members: {} (targets={}) -> builder authoritative", plan.label(), targetLevels.size() );
    if ( "agg-mt-collapsed".equals( plan.label() ) ) {
      return java.util.Optional.of( SqlBuildGuard.build( context.getDialect(), formatted,
        () -> TupleSqlMapper.collapsedTupleLevelMembersSql( plan.collapsedTargets(), aggStar,
          c.where(), plan.order(), plan.having() ) ) );
    }
    return java.util.Optional.of( SqlBuildGuard.build( context.getDialect(), formatted,
      () -> TupleSqlMapper.aggTupleLevelMembersSql( plan.targetLevels(), aggStar,
        true, c.where(), plan.aggPlan().orderedAggPredicates(),
        plan.havingJoins(), plan.order(), plan.having(), c.factJoinRequired(), baseCube, true ) ) );
  }

  /**
   * The shape classifier as a PURE decision table over the per-read flags —
   * package-visible for the unit pin. {@code null} = unclassifiable → the caller harvests
   * {@code agg-unclassified}. Order matters:
   * <ol>
   * <li>every projected level collapsed-single-column AND no agg-DIM-table predicate → the
   *     all-collapsed projection ({@code agg-mt-collapsed}; single-target reads landing here are
   *     the collapsed reads the authoritative {@code aggCollapsedLevelMembersSql} route declined);</li>
   * <li>any collapsed multi-column level whose extras need the dimension
   *     ({@code AggJoinPlanner.requiresJoinToDim}) → the dim-join family
   *     ({@code agg-st-dimjoin}/{@code agg-mt-dimjoin});</li>
   * <li>MULTI-target with a collapsed level but WITHOUT any agg-substituted predicate →
   *     {@code agg-mixed}: collapsed level(s) projected on the agg fact PLUS non-collapsed
   *     target(s) joined through it (NECJ
   *     {@code [Time].[Month] x [Store Country]}: allCollapsedSingle=false, anyDimJoin=false,
   *     no plan predicates, anyCollapsed=true, factJoinRequired=TRUE — a
   *     NonEmptyCrossJoin constraint ALWAYS carries the existence join
   *     ({@code SetConstraint.isJoinRequired}: {@code args.length > 1}), so this row must sit
   *     BEFORE the fact-join row or the shape can never reach it);</li>
   * <li>any agg-substituted predicate or a forced existence join → the fact-join family
   *     ({@code agg-st-factjoin}/{@code agg-mt-factjoin});</li>
   * <li>single target, no predicate, no collapsed level, no forced join → {@code agg-st-neutral}
   *     (the read is on the agg star but nothing about it is agg-shaped beyond the projection
   *     substitution);</li>
   * <li>everything else → {@code null} (harvest).</li>
   * </ol>
   */
  static String classifyAggShape( boolean singleTarget, boolean allCollapsedSingle,
    boolean anyDimJoin, boolean anyCollapsed, boolean hasPlanPredicates,
    boolean dimTablePredicate, boolean factJoinRequired ) {
    if ( allCollapsedSingle && !dimTablePredicate ) {
      return "agg-mt-collapsed";
    }
    if ( anyDimJoin ) {
      return singleTarget ? "agg-st-dimjoin" : "agg-mt-dimjoin";
    }
    if ( !singleTarget && anyCollapsed && !hasPlanPredicates ) {
      return "agg-mixed";
    }
    if ( hasPlanPredicates || factJoinRequired ) {
      return singleTarget ? "agg-st-factjoin" : "agg-mt-factjoin";
    }
    if ( singleTarget && !anyCollapsed ) {
      return "agg-st-neutral";
    }
    return null;
  }

  /**
   * Build the computed-expression tuple read authoritatively: a tuple read
   * declined by {@code supportsTupleRead} ONLY because a target level carries a computed
   * expression (strict {@code supports} false, {@code supportsAllowingExpressions} true, not
   * parent-child) — the family is dominated by {@code [Customers].[Name]} in multi-target reads.
   * The combination:
   * <ul>
   * <li>member-restriction compaction: a multi-parent {@link DescendantsConstraint} takes the
   *     FACTORED per-level IN form ({@link DescendantsConstraint#toContributionFactoredMemberForm}
   *     — the recorder's non-crossjoin addMemberConstraint bounding box
   *     {@code (city in (..) and state in (..))}) instead of the exact tuple IN, which diverges
   *     on a non-rectangle parent set; every other constraint keeps its normal
   *     {@code toContribution};</li>
   * <li>join ordering: {@link TupleSqlMapper#tupleLevelMembersSqlRecorderJoinOrder} emits each
   *     hierarchy's snowflake chain contiguously (the recorder's order) instead of the
   *     breadth-first fold.</li>
   * </ul>
   * The family also serves
   * a NOT_LAST/union-arm read ({@code whichSelect != ONLY}): the SAME
   * {@code tupleLevelMembersSqlRecorderJoinOrder} call with the arm's level-ORDER-BY suppression
   * ({@code emitOrderBy=false} — a native order/HAVING is STILL carried).
   * <p>
   * The routing condition IS the guard (no runtime fallback): an empty Optional (shape outside
   * the family, or an inexpressible constraint) keeps the recorder as a documented decline.
   */
  private java.util.Optional<BuiltSql> computedTupleSql(
    Context<?> context, RolapCube baseCube, AggStar aggStar, WhichSelect whichSelect,
    List<TargetBase> targetGroup ) {
    if ( aggStar != null ) {
      return java.util.Optional.empty();
    }
    if ( targetGroup.stream().anyMatch( t -> t.getSrcMembers() != null ) ) {
      return java.util.Optional.empty();
    }
    final List<RolapLevel> targetLevels = targetGroup.stream()
      .map( TargetBase::getLevel ).toList();
    if ( !isComputedExpressionTupleFamily( targetLevels ) ) {
      return java.util.Optional.empty();
    }
    final java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> contribution =
      ( constraint instanceof org.eclipse.daanse.rolap.common.constraint.DescendantsConstraint dc )
        ? dc.toContributionFactoredMemberForm( baseCube, aggStar )
        : constraint.toContribution( baseCube, aggStar );
    if ( contribution.isEmpty() ) {
      RolapUtil.SQL_GEN_LOGGER.debug(
        "level members: recorder path (computed-tuple constraint {} not expressible as a contribution)",
        constraint.getClass().getSimpleName() );
      return java.util.Optional.empty();
    }
    final org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = contribution.get();
    final boolean formatted = context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
        ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class );
    RolapUtil.SQL_GEN_LOGGER.debug(
      "level members: computed-expression tuple read (whichSelect={} targets={}) -> builder authoritative",
      whichSelect, targetGroup.size() );
    return java.util.Optional.of( SqlBuildGuard.build( context.getDialect(), formatted,
      () -> TupleSqlMapper.tupleLevelMembersSqlRecorderJoinOrder( targetLevels, true,
        c.where(), c.joinTables(), c.orderedPredicates(), c.nativeOrder(), c.nativeHaving(),
        c.factJoinRequired(), baseCube, whichSelect == WhichSelect.ONLY ) ) );
  }

  /**
   * The computed-expression tuple family gate:
   * every target level is expression-renderable ({@code supportsAllowingExpressions}) and not
   * parent-child, and at least one level fails the strict plain-column {@code supports} gate —
   * i.e. the read was declined by {@code supportsTupleRead} EXACTLY because of a computed
   * expression, not any other topology reason.
   */
  private boolean isComputedExpressionTupleFamily( List<RolapLevel> targetLevels ) {
    if ( targetLevels.stream().anyMatch( l -> l.isParentChild()
        || !TupleSqlMapper.supportsAllowingExpressions( l ) ) ) {
      return false;
    }
    // All levels strictly supported = not this family (a topology decline -> noFactAdjacentTupleSql).
    return !targetLevels.stream().allMatch( TupleSqlMapper::supports );
  }

  /**
   * Build the {@code no-fact-adjacent} tuple-read declines authoritatively: the
   * {@code supportsTupleRead} declines whose LEVELS are individually fine
   * (strict {@code supports} passes) but whose star topology fails, in two sub-shapes:
   * <ul>
   * <li><b>(All)-drop</b>: an (All) first/co-target contributes NOTHING to
   *     the recorder SQL (no projection, no key) — the surviving non-All subset reads normally
   *     (single-target incl. fact join, or multi-target when it regains a fact-adjacent first).
   *     A FROM relation carrying a schema-defined {@code sqlWhereExpression} (the raw table
   *     filter the recorder appends to WHERE, e.g. a fact declared with
   *     {@code <SQL> store_id in (select distinct …)}) is INCLUDED: the mappers carry the filter in
   *     the {@code FromTable.filter} slot and lift it into a leading WHERE conjunct
   *     ({@code RelationFromMapper.liftTableFilters} — the recorder's FROM-entry conjunct
   *     order).</li>
   * <li><b>same-table</b>: every target hierarchy
   *     degenerate on ONE shared single-table relation (the Store cube's store table, which IS
   *     the fact — degenerate self-join). {@link TupleSqlMapper#supportsSameTableTupleRead} gates,
   *     {@link TupleSqlMapper#sameTableTupleLevelMembersSql} builds the single-table SELECT.</li>
   * </ul>
   * The routing condition IS the guard (no runtime fallback): an empty Optional keeps the
   * recorder as a documented decline.
   */
  private java.util.Optional<BuiltSql> noFactAdjacentTupleSql(
    Context<?> context, RolapCube baseCube, AggStar aggStar, WhichSelect whichSelect,
    List<TargetBase> targetGroup ) {
    if ( aggStar != null || whichSelect != WhichSelect.ONLY ) {
      return java.util.Optional.empty();
    }
    if ( targetGroup.stream().anyMatch( t -> t.getSrcMembers() != null ) ) {
      return java.util.Optional.empty();
    }
    final List<RolapLevel> targetLevels = targetGroup.stream()
      .map( TargetBase::getLevel ).toList();
    if ( targetLevels.stream().anyMatch( l -> !TupleSqlMapper.supports( l ) ) ) {
      return java.util.Optional.empty(); // level-unsupported family = computedTupleSql / rump, not this route
    }
    if ( TupleSqlMapper.supportsTupleRead( targetLevels, baseCube ) ) {
      return java.util.Optional.empty(); // authoritative branch took it (or would have)
    }
    final java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> contribution =
      constraint.toContribution( baseCube, aggStar );
    if ( contribution.isEmpty() ) {
      RolapUtil.SQL_GEN_LOGGER.debug(
        "level members: recorder path (no-fact-adjacent constraint {} not expressible as a contribution)",
        constraint.getClass().getSimpleName() );
      return java.util.Optional.empty();
    }
    final org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = contribution.get();
    final boolean formatted = context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
        ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class );
    final List<RolapLevel> nonAll = targetLevels.stream().filter( l -> !l.isAll() ).toList();
    if ( nonAll.size() == 1 ) {
      RolapUtil.SQL_GEN_LOGGER.debug(
        "level members {}: no-fact-adjacent (all-dropped) -> builder authoritative",
        nonAll.get( 0 ).getUniqueName() );
      return java.util.Optional.of( SqlBuildGuard.build( context.getDialect(), formatted,
        () -> c.producesPlainLevelMembers()
          ? TupleSqlMapper.levelMembersSql( nonAll.get( 0 ), true )
          : TupleSqlMapper.levelMembersSql( nonAll.get( 0 ), true, c.where(),
              c.joinTables(), c.orderedPredicates(), c.nativeOrder(), c.nativeHaving(),
              c.factJoinRequired(), baseCube ) ) );
    }
    if ( nonAll.size() < targetLevels.size()
        && TupleSqlMapper.supportsTupleRead( nonAll, baseCube ) ) {
      RolapUtil.SQL_GEN_LOGGER.debug(
        "level members {}: no-fact-adjacent (all-dropped) tuple -> builder authoritative",
        nonAll.get( 0 ).getUniqueName() );
      return java.util.Optional.of( SqlBuildGuard.build( context.getDialect(), formatted,
        () -> TupleSqlMapper.tupleLevelMembersSql( nonAll, true,
          c.where(), c.joinTables(), c.orderedPredicates(), c.nativeOrder(), c.nativeHaving(),
          c.factJoinRequired(), baseCube, true ) ) );
    }
    // same-table sub-shape: the gate logs its own grep-stable decline reason; false = documented
    // decline (the recorder keeps any other no-fact-adjacent topology as the rump).
    if ( !TupleSqlMapper.supportsSameTableTupleRead( targetLevels, baseCube, c.joinTables(),
        c.orderedPredicates() ) ) {
      return java.util.Optional.empty();
    }
    RolapUtil.SQL_GEN_LOGGER.debug(
      "level members: no-fact-adjacent same-table (targets={}) -> builder authoritative",
      targetGroup.size() );
    return java.util.Optional.of( SqlBuildGuard.build( context.getDialect(), formatted,
      () -> TupleSqlMapper.sameTableTupleLevelMembersSql( targetLevels, true,
        c.where(), c.joinTables(), c.orderedPredicates(), c.nativeOrder(), c.nativeHaving(),
        baseCube, true ) ) );
  }

  /**
   * Build the {@code aggstar-collapsed-levelmembers} shape authoritatively (the tuple-reader twin
   * of the collapsed single-column aggStar member-children route in {@code SqlMemberSource}):
   * a standalone single-target level-members read against an aggregate table whose
   * every projected level (root..target) is a single-column collapsed level
   * ({@code isLevelCollapsed && !multipleCols}), so the recorder emits pure agg-column projection with
   * no dimension join. Its {@link SqlContextConstraint#levelMembersAggWhere} twin translates
   * the context WHERE against the agg columns, {@link SqlContextConstraint#levelMembersAggHaving}
   * / {@link SqlContextConstraint#levelMembersAggOrder} carry a nativised filter's HAVING / a
   * TopCount's measure ORDER (also agg-substituted), and {@link TupleSqlMapper#collapsedSingleColumnSql}
   * rebuilds the body; this renders that statement through {@link SqlBuildGuard} and returns it as the
   * executed SQL. Restricted to the aggStar
   * single-target standalone read ({@code WhichSelect.ONLY}, no union arm / multi-target / enumerated)
   * where every projected level is collapsed single-column AND the context WHERE is agg-expressible OR
   * the candidate twin resolves it (see the empty-WHERE branch below); any other shape
   * (agg-dim-join, candidate-bailed WHERE, multi-column) returns empty and the caller — the agg
   * router {@link #aggAuthoritativeLevelMembersSql}, where this shortcut keeps FIRST precedence —
   * classifies it next.
   */
  private java.util.Optional<BuiltSql> aggCollapsedLevelMembersSql(
    Context<?> context, RolapCube baseCube, AggStar aggStar, WhichSelect whichSelect,
    List<TargetBase> targetGroup ) {
    // Only the aggStar (recorder-taken) single-target standalone read — the shape addLevelMemberSql
    // reduces to pure addAggColumnToSql projection. A null aggStar is the ordinary non-agg recorder
    // fallback (already attributed by the routing lines above), not an agg-collapsed decline — no log.
    if ( aggStar == null ) {
      return java.util.Optional.empty();
    }
    // Every decline below carries one grep-stable
    // "agg-collapsed decline reason=" line on the gen channel (mirrors supportsTupleRead).
    if ( whichSelect != WhichSelect.ONLY || targetGroup.size() != 1 ) {
      RolapUtil.SQL_GEN_LOGGER.debug(
        "agg-collapsed decline reason=not-only-or-multi-target (whichSelect={} targets={})",
        whichSelect, targetGroup.size() );
      return java.util.Optional.empty();
    }
    final TargetBase only = targetGroup.get( 0 );
    if ( only.getSrcMembers() != null ) {
      RolapUtil.SQL_GEN_LOGGER.debug(
        "agg-collapsed decline reason=src-members level={}",
        only.getLevel().getUniqueName() );
      return java.util.Optional.empty();
    }
    if ( !( constraint instanceof SqlContextConstraint scc ) ) {
      RolapUtil.SQL_GEN_LOGGER.debug(
        "agg-collapsed decline reason=not-scc level={} constraint={}",
        only.getLevel().getUniqueName(), constraint.getClass().getSimpleName() );
      return java.util.Optional.empty();
    }
    final RolapLevel targetLevel = only.getLevel();
    // Resolve the level's hierarchy exactly as addLevelMemberSql does (a virtual-cube read maps to the
    // base cube hierarchy) so the projected-level enumeration matches the recorder.
    RolapHierarchy hierarchy = targetLevel.getHierarchy();
    if ( !targetLevel.isAll() && hierarchy instanceof RolapCubeHierarchy cubeHierarchy
        && baseCube != null
        && !cubeHierarchy.getCube().equalsOlapElement( baseCube ) ) {
      hierarchy = baseCube.findBaseCubeHierarchy( hierarchy );
    }
    final List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
    final int levelDepth = targetLevel.getDepth();
    // Every non-all level root..target must be single-column collapsed; otherwise the recorder query
    // carries a dimension join / non-agg projection this route does not model.
    final List<RolapCubeLevel> collapsedLevels = new java.util.ArrayList<>();
    for ( int i = 0; i <= levelDepth; i++ ) {
      RolapLevel lvl = levels.get( i );
      if ( lvl.isAll() ) {
        continue;
      }
      if ( !( lvl instanceof RolapCubeLevel cubeLevel )
          || !SqlMemberSource.isLevelCollapsed( aggStar, cubeLevel )
          || SqlMemberSource.levelContainsMultipleColumns( lvl ) ) {
        RolapUtil.SQL_GEN_LOGGER.debug(
          "agg-collapsed decline reason=level-not-collapsed-single-column level={} target={}",
          lvl.getUniqueName(), targetLevel.getUniqueName() );
        return java.util.Optional.empty();
      }
      collapsedLevels.add( cubeLevel );
    }
    if ( collapsedLevels.isEmpty() ) {
      RolapUtil.SQL_GEN_LOGGER.debug(
        "agg-collapsed decline reason=level-not-collapsed-single-column level={} target={}",
        targetLevel.getUniqueName(), targetLevel.getUniqueName() );
      return java.util.Optional.empty();
    }
    // The agg-substituted context WHERE (addConstraintOps -> addContextConstraint), TRI-STATE: a BAIL
    // (shape outside the twin — virtual cube, calc/slicer exotic, role access, missing agg node) keeps
    // the recorder. An EMPTY live WHERE is decided by the candidate twin (below).
    final SqlContextConstraint.AggWhereResult aggWhereState =
      scc.levelMembersAggWhereState( aggStar );
    final java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> aggWhere =
      aggWhereState.where();
    if ( aggWhere.isEmpty() ) {
      // When the LIVE tri-state carries no WHERE,
      // the candidate twin decides — levelMembersAggWhereCandidateState appends a
      // RolapNativeSet.SetConstraint's per-arg member restrictions, the recorder leg the live twin
      // does not model (it misclassifies e.g. a DescendantsCrossJoinArg parent's the_year = 1997
      // as UNCONSTRAINED); a plain context constraint delegates to the live state.
      // NOT bailed -> the candidate WHERE (possibly genuinely unconstrained = empty) is
      // authoritative through the SAME collapsedSingleColumnSql call as the non-empty path below,
      // with the constraint's agg-substituted nativeHaving + nativeOrder carried identically.
      // Bailed -> recorder; the "agg-collapsed decline reason=agg-where-empty-*" census line
      // narrows to that true residue.
      final SqlContextConstraint.AggWhereResult candidateState =
        scc.levelMembersAggWhereCandidateState( aggStar );
      if ( candidateState.bailed() ) {
        RolapUtil.SQL_GEN_LOGGER.debug(
          "agg-collapsed decline reason={} level={}",
          aggWhereState.bailed() ? "agg-where-empty-bail" : "agg-where-empty-unconstrained",
          targetLevel.getUniqueName() );
        return java.util.Optional.empty();
      }
      final java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> candidateWhere =
        candidateState.where();
      final java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> candidateHaving =
        scc.levelMembersAggHaving( aggStar );
      final java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> candidateOrder =
        scc.levelMembersAggOrder( aggStar );
      final boolean candidateFormatted = context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
          ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class );
      RolapUtil.SQL_GEN_LOGGER.debug(
        "level members {}: aggstar collapsed single-column (candidate twin) -> builder authoritative",
        targetLevel.getUniqueName() );
      return java.util.Optional.of( SqlBuildGuard.build( context.getDialect(), candidateFormatted,
        () -> TupleSqlMapper.collapsedSingleColumnSql( collapsedLevels, aggStar, candidateWhere,
          candidateOrder, candidateHaving ) ) );
    }
    // Every builder read MUST carry nativeHaving + nativeOrder or it is a guaranteed differ against a
    // nativised filter/topcount recorder read (which emits a native HAVING / measure ORDER on top of the
    // context WHERE). Both come from the constraint's agg-substituted helpers (the aggStar
    // toContribution bails, so it cannot resolve them). A plain context constraint returns both empty.
    final java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> aggHaving =
      scc.levelMembersAggHaving( aggStar );
    final java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> aggOrder =
      scc.levelMembersAggOrder( aggStar );
    // Authoritative render: TupleSqlMapper.collapsedSingleColumnSql rebuilds the pure agg-column
    // projection body; SqlBuildGuard.build wraps it into the executed BuiltSql (compact/formatted per
    // config), mirroring every other builder-authoritative tuple read above.
    final boolean formatted = context.getConfigValue( ConfigConstants.GENERATE_FORMATTED_SQL,
        ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class );
    RolapUtil.SQL_GEN_LOGGER.debug(
      "level members {}: aggstar collapsed single-column -> builder authoritative",
      targetLevel.getUniqueName() );
    return java.util.Optional.of( SqlBuildGuard.build( context.getDialect(), formatted,
      () -> TupleSqlMapper.collapsedSingleColumnSql( collapsedLevels, aggStar, aggWhere, aggOrder, aggHaving ) ) );
  }

  /**
   * GUARD for the computed-expression constrained level-members route: true when the constraint's
   * contribution WHERE reproduces the recorder, so the builder may be authoritative. Every
   * constraint class matches EXCEPT a multi-parent {@link DescendantsConstraint} whose
   * parent set does not form a rectangle — there the recorder factors the compound restriction into a
   * bounding-box per-level IN while the builder emits the exact tuple form, so that one shape stays on the
   * recorder (see {@link DescendantsConstraint#contributionReproducesRecorder()}).
   */
  private boolean computedLevelContributionReproducesRecorder( TupleConstraint constraint ) {
    return !( constraint instanceof DescendantsConstraint dc ) || dc.contributionReproducesRecorder();
  }

  private boolean targetIsOnBaseCube( TargetBase target, RolapCube baseCube ) {
    return target.getLevel().isAll()
      || baseCube == null
      || baseCube.findBaseCubeHierarchy(
      target.getLevel().getHierarchy() ) != null;
  }


  /**
   * Determines whether we need to join the agg table to one or more dimension tables to retrieve "extra" level columns
   * (like ordinal). If the targetExp map has any targets not on the agg table then we need to join.
   */
  private boolean requiresJoinToDim(
    Map<SqlExpression, SqlExpression> targetExp ) {
    for ( Map.Entry<SqlExpression, SqlExpression> entry
      : targetExp.entrySet() ) {
      if ( entry.getKey() != null
        && entry.getKey().equals( entry.getValue() ) ) {
        // this level expression does not have a corresponding
        // field on the aggregate table
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a map of the various RolapLevel expressions (keyExp, ordinalExp, captionExp, properties) to the
   * corresponding target expression to be used.  If there's no aggStar available then we'll just return an identity
   * map. If an AggStar is present the target Expression may be on the aggregate table.
   */
  private Map<SqlExpression, SqlExpression> getLevelTargetExpMap( RolapLevel level,
                                                                                    AggStar aggStar ) {
    Map<SqlExpression, SqlExpression> map =
      initializeIdentityMap( level );
    if ( aggStar == null ) {
      return Collections.unmodifiableMap( map );
    }
    AggStar.Table.Level aggLevel =
      getAggLevel( aggStar, (RolapCubeLevel) level );
    if ( aggLevel == null ) {
      // If no AggStar Level is defined, then the key exp is
      // a raw AggStar Column.  No extra columns.
      AggStar.Table.Column aggStarColumn =
        getAggColumn( aggStar, (RolapCubeLevel) level );
      assert aggStarColumn.getExpression() != null;
      map.put( level.getKeyExp(), aggStarColumn.getExpression() );
    } else {
      assert aggLevel.getExpression() != null;
      map.put( level.getKeyExp(), aggLevel.getExpression() );
      //put in target map elements where indexes are same.
      if ( aggLevel.getOrdinalExps() != null ) {
        int size = Math.min(aggLevel.getOrdinalExps().size(), level.getOrdinalExps().size());
        for (int i = 0; i < size; i++) {
            map.put( level.getOrdinalExps().get(i), aggLevel.getOrdinalExps().get(i) );
        }
      }
      if ( aggLevel.getCaptionExp() != null ) {
        map.put( level.getCaptionExp(), aggLevel.getCaptionExp() );
      }
      for ( RolapProperty prop : level.getProperties() ) {
        String propName = prop.getName();
        if ( aggLevel.getProperties().containsKey( propName ) ) {
          map.put(
            prop.getExp(), aggLevel.getProperties().get( propName ) );
        }
      }
    }
    return Collections.unmodifiableMap( map );
  }

  /**
   * Creates a map of the expressions from a RolapLevel to themselves.  This is the starting assumption of what the
   * target expression is.
   */
  private Map<SqlExpression, SqlExpression> initializeIdentityMap( RolapLevel level ) {
    Map<SqlExpression, SqlExpression> map = new HashMap<>();
    map.put( level.getKeyExp(), level.getKeyExp() );
    for (SqlExpression oe : level.getOrdinalExps()) { 
        map.put( oe, oe );
    }
    map.put( level.getCaptionExp(), level.getCaptionExp() );
    for ( RolapProperty prop : level.getProperties() ) {
      if ( !map.containsKey( prop.getExp() ) ) {
        map.put( prop.getExp(), prop.getExp() );
      }
    }
    return map;
  }


  private void addAggColumnToSql(
    QueryRecorder sqlQuery, WhichSelect whichSelect, AggStar aggStar,
    RolapCubeLevel level, Dialect dialect ) {
    RolapStar.Column starColumn =
      level.getStarKeyColumn();
    AggStar.Table.Column aggColumn = getAggColumn( aggStar, level );
    String aggColExp = aggColumn.generateExprString( dialect );
    final String colAlias =
      sqlQuery.addSelectGroupBy( aggColExp, starColumn.getInternalType() );
    if ( whichSelect == WhichSelect.ONLY ) {
      sqlQuery.addOrderBy(
        aggColExp, colAlias, SortingDirection.ASC, false, true, true );
    }
    aggColumn.getTable().addToFrom( sqlQuery, false, true );
  }


  private AggStar.Table.Level getAggLevel(
    AggStar aggStar, RolapCubeLevel level ) {
    RolapStar.Column starColumn =
      level.getStarKeyColumn();
    return aggStar.lookupLevel( starColumn.getBitPosition() );
  }


  private AggStar.Table.Column getAggColumn(
    AggStar aggStar, RolapCubeLevel level ) {
    RolapStar.Column starColumn =
      level.getStarKeyColumn();
    int bitPos = starColumn.getBitPosition();
    return aggStar.lookupColumn( bitPos );
  }

  /**
   * Obtains the evaluator used to find an aggregate table to support the Tuple constraint.
   *
   * @param constraint Constraint
   * @return evaluator for constraint
   */
  protected Evaluator getEvaluator( TupleConstraint constraint ) {
    if ( constraint instanceof SqlContextConstraint ) {
      return constraint.getEvaluator();
    }
    if ( constraint instanceof DescendantsConstraint descConstraint ) {
      MemberChildrenConstraint mcc =
        descConstraint.getMemberChildrenConstraint( null );
      if ( mcc instanceof SqlContextConstraint scc ) {
        return scc.getEvaluator();
      }
    }
    return null;
  }

  /**
   * Obtains the AggStar instance which corresponds to an aggregate table which can be used to support the member
   * constraint.
   *
   * @param constraint The tuple constraint to apply.
   * @param evaluator  the current evaluator to obtain the cube and members to be queried  @return AggStar for aggregate
   *                   table
   * @param baseCube   The base cube from which to choose an aggregation star. Can be null, in which case we use the
   *                   evaluator's cube.
   */
  public AggStar chooseAggStar(
    TupleConstraint constraint,
    Evaluator evaluator,
    RolapCube baseCube,
    boolean useAggregates) {
    if ( !useAggregates ) {
      return null;
    }

    if ( evaluator == null || !constraint.supportsAggTables() ) {
      return null;
    }

    if ( baseCube == null ) {
      baseCube = (RolapCube) evaluator.getCube();
    }

    // Current cannot support aggregate tables for virtual cubes
    if ( baseCube instanceof RolapVirtualCube ) {
      return null;
    }

    RolapStar star = baseCube.getStar();
    final int starColumnCount = star.getColumnCount();
    BitKey measureBitKey = BitKey.Factory.makeBitKey( starColumnCount );
    BitKey levelBitKey = BitKey.Factory.makeBitKey( starColumnCount );

    // Convert global ordinal to cube based ordinal (the 0th dimension
    // is always [Measures]). In the case of filter constraint this will
    // be the measure on which the filter will be done.

    // Since we support aggregated members as arguments, we'll expand
    // this too.
    // Failing to do so could result in chosing the wrong aggstar, as the
    // level would not be passed to the bitkeys
    final Member[] members =
      CalculatedMemberExpander.expandSupportedCalculatedMembers(
        Arrays.asList(
          evaluator.getNonAllMembers() ),
        evaluator )
        .getMembersArray();

    // if measure is calculated, we can't continue
    if ( !( members[ 0 ] instanceof RolapBaseCubeMeasure measure ) ) {
      return null;
    }

    int bitPosition =
      ( (RolapStar.Measure) measure.getStarMeasure() ).getBitPosition();

    // set a bit for each level which is constrained in the context
    final CellRequest request =
      RolapAggregationManager.makeRequest( members );
    if ( request == null ) {
      // One or more calculated members. Cannot use agg table.
      return null;
    }
    // TODO: RME why is this using the array of constrained columns
    // from the CellRequest rather than just the constrained columns
    // BitKey (method getConstrainedColumnsBitKey)?
    RolapStar.Column[] columns = request.getConstrainedColumns();
    for ( RolapStar.Column column1 : columns ) {
      levelBitKey.set( column1.getBitPosition() );
    }

    // set the masks
    for ( TargetBase target : targets ) {
      RolapLevel level = target.level;
      if ( !level.isAll() ) {
        RolapStar.Column column =
          ( (RolapCubeLevel) level ).getBaseStarKeyColumn( baseCube );
        if ( column != null ) {
          levelBitKey.set( column.getBitPosition() );
        }
      }
    }

    // Set the bits for limited rollup members
    RolapUtil.constraintBitkeyForLimitedMembers(
      evaluator, evaluator.getMembers(), baseCube, levelBitKey );

    measureBitKey.set( bitPosition );

    if ( constraint instanceof RolapNativeCrossJoin.NonEmptyCrossJoinConstraint necj ) {
      for ( CrossJoinArg arg : necj.args ) {
        if ( arg instanceof DescendantsCrossJoinArg
          || arg instanceof MemberListCrossJoinArg ) {
          final RolapLevel level = arg.getLevel();
          if ( level != null && !level.isAll() ) {
            RolapStar.Column column =
              ( (RolapCubeLevel) level )
                .getBaseStarKeyColumn( baseCube );
            if ( column == null ) {
              // this can happen if the constraint includes
              // levels that are not present in the current
              // target group.
              continue;
            }
            levelBitKey.set( column.getBitPosition() );
          }
        }
      }
    } else if ( constraint instanceof RolapNativeFilter.FilterConstraint ) {
      for ( Member slicer : ( (RolapEvaluator) evaluator ).getSlicerMembers() ) {
        final Level level = slicer.getLevel();
        if ( level != null && !level.isAll() ) {
          final RolapStar.Column column = ( (RolapCubeLevel) level )
            .getBaseStarKeyColumn( baseCube );
          levelBitKey.set( column.getBitPosition() );
        }
      }
    }

    // find the aggstar using the masks
    return AggregationManager.findAgg(
      star, levelBitKey, measureBitKey, new boolean[] { false } );
  }

  int getMaxRows() {
    return maxRows;
  }

  public void setMaxRows( int maxRows ) {
    this.maxRows = maxRows;
  }

  /**
   * Description of the position of a SELECT statement in a UNION. Queries on virtual cubes tend to generate unions.
   */
  public enum WhichSelect {
    /**
     * Select statement does not belong to a union.
     */
    ONLY,
    /**
     * Select statement belongs to a UNION, but is not the last. Typically this occurs when querying a virtual cube.
     */
    NOT_LAST,
    /**
     * Select statement is the last in a UNION. Typically this occurs when querying a virtual cube.
     */
    LAST
  }
}
