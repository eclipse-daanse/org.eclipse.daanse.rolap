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
import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.getExpression;

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
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.daanse.jdbc.db.dialect.api.BestFitColumnType;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.Execution;
import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.api.Statement;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.monitor.event.SqlStatementEvent;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.ArrayTupleList;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.ListTupleList;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.TupleCollections;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.UnaryTupleList;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.common.ResourceLimitExceededException;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.function.def.crossjoin.CrossJoinFunDef;
import org.eclipse.daanse.olap.key.BitKey;
import  org.eclipse.daanse.olap.server.ExecutionImpl;
import  org.eclipse.daanse.olap.server.LocusImpl;
import  org.eclipse.daanse.olap.util.CancellationChecker;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.DescendantsCrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.MemberListCrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.common.sql.TupleConstraint;
import org.eclipse.daanse.rolap.common.util.ExpressionUtil;
import org.eclipse.daanse.rolap.element.RolapBaseCubeMeasure;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;
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
 * {@link TupleConstraint#addLevelConstraint(org.eclipse.daanse.rolap.common.sql.SqlQuery, RolapCube, org.eclipse.daanse.rolap.common.aggmatcher.AggStar, RolapLevel)}
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
 * {@link TupleConstraint#addConstraint(org.eclipse.daanse.rolap.common.sql.SqlQuery, org.eclipse.daanse.rolap.element.RolapCube, org.eclipse.daanse.rolap.common.aggmatcher.AggStar)}
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
  private static final String UNION = "union";
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
          if ( !childLevel.getOrdinalExp().equals(
            childLevel.getKeyExp() ) ) {
            Object ordinal = accessors.get( column++ ).get();
            Object prevValue = rolapToOrdinalMap
              .put( member, ordinal );
            if ( prevValue != null
              && !Objects.equals( prevValue, ordinal ) ) {
              LOGGER.error(
                "Column expression for {} is inconsistent with ordinal or caption expression. It should have 1:1 relationship",
                  member.getUniqueName() );
            }
          }
          column += childLevel.getProperties().length;

          // Cache in our intermediate map the key/member pair
          // for later lookups of children.
          keyToMember.put( member.getKey(), member );

          if ( member != members.get( i ) ) {
            // Flush list we've been building.
            List<RolapMember> children = siblings.get( i + 1 );
            if ( children != null && children.size() > 0) {
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
        final Pair<String, List<BestFitColumnType>> pair =
          makeLevelMembersSql( context, targetGroup );
        String sql = pair.left;
        List<BestFitColumnType> types = pair.right;
        assert sql != null && !sql.equals( "" );
        stmt = RolapUtil.executeQuery(
          context, sql, types, maxRows, 0,
          new SqlStatement.StatementLocus(
        	getExecution(context),
            "SqlTupleReader.readTuples " + partialTargets,
            message,
            SqlStatementEvent.Purpose.TUPLES, 0 ),
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
          moreRows = resultSet.next();
          if ( moreRows ) {
            ++stmt.rowCount;
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
        if (LocusImpl.isEmpty()) {
            //need for virtual cubes need investigate
            final Statement statement = context.getConnectionWithDefaultRole().getInternalStatement();
            return new ExecutionImpl(statement, ExecuteDurationUtil.executeDurationValue(context));
        } else {
            return LocusImpl.peek().getExecution();
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

  Pair<String, List<BestFitColumnType>> makeLevelMembersSql(
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
      String prependString = "";
      final StringBuilder selectString = new StringBuilder();
      List<BestFitColumnType> types = null;

      final int savepoint =
        getEvaluator( constraint ).savepoint();

      SqlQuery unionQuery = SqlQuery.newQuery( context, "" );

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

          selectString.append( prependString );

          // Generate the select statement for the current base cube.
          // Make sure to pass WhichSelect.NOT_LAST if there are more
          // than one base cube and it isn't the last one so that
          // the order by clause is not added to unionized queries
          // (that would be illegal SQL)
          final Pair<String, List<BestFitColumnType>> pair =
            generateSelectForLevels(
              context, baseCube,
              fullyJoiningBaseCubes.size() == 1
                ? WhichSelect.ONLY
                : WhichSelect.NOT_LAST,
              targetGroup );
          selectString.append( pair.left );
          types = pair.right;
          prependString =
            context.getConfigValue(ConfigConstants.GENERATE_FORMATTED_SQL, ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class)
              ? new StringBuilder(Util.NL).append(UNION).append(Util.NL).toString()
              : new StringBuilder(" ").append(UNION).append(" ").toString();
        }
      } finally {
        // Restore the original measure member
        getEvaluator( constraint ).restore( savepoint );
      }

      if ( fullyJoiningBaseCubes.size() == 1 ) {
        // Because there is only one virtual cube to
        // join on, we can swap the union query by
        // the original one.
        return Pair.of( selectString.toString(), types );
      } else {
        // Add the subquery to the wrapper query.
        unionQuery.addFromQuery(
          selectString.toString(), "unionQuery", true );

        // Dont forget to select all columns.
        unionQuery.addSelect( "*", null, null );

        // Sort the union of the cubes.
        // The order by columns need to be numbers,
        // not column name strings or expressions.
        if ( fullyJoiningBaseCubes.size() > 1 ) {
          for ( int i = 0; i < types.size(); i++ ) {
            unionQuery.addOrderBy(
              new StringBuilder().append(i + 1),
              null,
              true,
              false,
              // We can't order the nulls
              // because column ordinals used as alias
              // are not supported by functions.
              // FIXME This dialect call is old and
              // has lost its meaning in the process.
              unionQuery.getDialect()
                .requiresUnionOrderByOrdinal(),
              true );
          }
        }
        return Pair.of( unionQuery.toSqlAndTypes().left, types );
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
      SqlConstraintUtils.getMembersNestedInMeasures( measures );
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

  Pair<String, List<BestFitColumnType>> sqlForEmptyTuple(
    final Context context,
    final Collection<RolapCube> baseCubes ) {
    final SqlQuery sqlQuery = SqlQuery.newQuery( context, null );
    sqlQuery.addSelect( "0", null );
    sqlQuery.addFrom( baseCubes.iterator().next().getFact(), null, true );
    sqlQuery.addWhere( "1 = 0" );
    return sqlQuery.toSqlAndTypes();
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
  Pair<String, List<BestFitColumnType>> generateSelectForLevels(
    Context<?> context,
    RolapCube baseCube,
    WhichSelect whichSelect, List<TargetBase> targetGroup ) {
    String s =
      "while generating query to retrieve members of level(s) " + targets;

    // Allow query to use optimization hints from the table definition
    SqlQuery sqlQuery = SqlQuery.newQuery( context, s );
    sqlQuery.setAllowHints( allowHints );


    Evaluator evaluator = getEvaluator( constraint );
    AggStar aggStar = chooseAggStar( constraint, evaluator, baseCube, context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class) );

    // add the selects for all levels to fetch
    for ( TargetBase target : targetGroup ) {
      // if we're going to be enumerating the values for this target,
      // then we don't need to generate sql for it
      if ( target.getSrcMembers() == null ) {
        addLevelMemberSql(
          sqlQuery,
          target.getLevel(),
          baseCube,
          whichSelect,
          aggStar );
      }
    }

    constraint.addConstraint( sqlQuery, baseCube, aggStar );

    return sqlQuery.toSqlAndTypes();
  }

  private boolean targetIsOnBaseCube( TargetBase target, RolapCube baseCube ) {
    return target.getLevel().isAll()
      || baseCube == null
      || baseCube.findBaseCubeHierarchy(
      target.getLevel().getHierarchy() ) != null;
  }

  /**
   * Generates the SQL statement to access members of level. For example,
   * SELECT "country", "state_province", "city"
   * FROM "customer"
   * GROUP BY "country", "state_province", "city", "init", "bar"
   * ORDER BY "country", "state_province", "city"
   *  accesses the "City" level of the "Customers"
   * hierarchy. Note that:
   *
   * "country", "state_province" are the parent keys;
   *
   * "city" is the level key;
   *
   * "init", "bar" are member properties.
   *
   *
   * @param sqlQuery    the query object being constructed
   * @param level       level to be added to the sql query
   * @param baseCube    this is the cube object for regular cubes, and the underlying base cube for virtual cubes
   * @param whichSelect describes whether this select belongs to a larger
   * @param aggStar     aggregate star if available
   */
  public void addLevelMemberSql(
    SqlQuery sqlQuery,
    RolapLevel level,
    RolapCube baseCube,
    WhichSelect whichSelect,
    AggStar aggStar ) {
    RolapHierarchy hierarchy = level.getHierarchy();

    // lookup RolapHierarchy of base cube that matches this hierarchy

    if ( !level.isAll() && hierarchy instanceof RolapCubeHierarchy cubeHierarchy
        && baseCube != null
        && !cubeHierarchy.getCube().equalsOlapElement( baseCube )) {
        // replace the hierarchy with the underlying base cube hierarchy
        // in the case of virtual cubes
        hierarchy = baseCube.findBaseCubeHierarchy( hierarchy );
    }

    List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
    int levelDepth = level.getDepth();

    boolean needsGroupBy =
      RolapUtil.isGroupByNeeded( hierarchy, levels, levelDepth );

    for ( int i = 0; i <= levelDepth; i++ ) {
      RolapLevel currLevel = levels.get( i );
      if ( currLevel.isAll() ) {
        continue;
      }

      // Determine if the aggregate table contains the collapsed level
      boolean levelCollapsed =
        ( aggStar != null )
          && SqlMemberSource.isLevelCollapsed(
          aggStar,
          (RolapCubeLevel) currLevel );
      boolean multipleCols =
        SqlMemberSource.levelContainsMultipleColumns( currLevel );

      if ( levelCollapsed && !multipleCols ) {
        // if this is a single column collapsed level, there is
        // no need to join it with dimension tables
        addAggColumnToSql(
          sqlQuery, whichSelect, aggStar, (RolapCubeLevel) currLevel );
        continue;
      }

      Map<SqlExpression, SqlExpression>
        targetExp = getLevelTargetExpMap( currLevel, aggStar );
      SqlExpression keyExp =
        targetExp.get( currLevel.getKeyExp() );
      SqlExpression ordinalExp =
        targetExp.get( currLevel.getOrdinalExp() );
      SqlExpression captionExp =
        targetExp.get( currLevel.getCaptionExp() );
      SqlExpression parentExp = currLevel.getParentExp();

      if ( parentExp != null ) {
        if ( !levelCollapsed ) {
          hierarchy.addToFrom( sqlQuery, parentExp );
        }
        if ( whichSelect == WhichSelect.LAST
          || whichSelect == WhichSelect.ONLY ) {
          final String parentSql =
            getExpression(parentExp, sqlQuery );
          final String parentAlias =
            sqlQuery.addSelectGroupBy(
              parentSql, currLevel.getInternalType() );
          if (level.getNullParentValue() == null) {
              sqlQuery.addOrderBy(
                      parentSql, parentAlias, true, false, true, false );
          } else {
              sqlQuery.addOrderBy(
                      parentSql, parentAlias, true, false, level.getNullParentValue(), level.getDatatype(), false);
          }
        }
      }
      String keySql = getExpression( keyExp, sqlQuery );

      if ( !levelCollapsed ) {
        hierarchy.addToFrom( sqlQuery, keyExp );
        hierarchy.addToFrom( sqlQuery, ordinalExp );
      }
      String captionSql = null;
      if ( captionExp != null ) {
        captionSql = getExpression( captionExp, sqlQuery );
        if ( !levelCollapsed ) {
          hierarchy.addToFrom( sqlQuery, captionExp );
        }
      }

      final String keyAlias =
        sqlQuery.addSelect( keySql, currLevel.getInternalType() );
      if ( needsGroupBy ) {
        // We pass both the expression and the alias.
        // The SQL query will figure out what to use.
        sqlQuery.addGroupBy( keySql, keyAlias );
      }
      if ( captionSql != null ) {
        final String captionAlias =
          sqlQuery.addSelect( captionSql, null );
        if ( needsGroupBy ) {
          // We pass both the expression and the alias.
          // The SQL query will figure out what to use.
          sqlQuery.addGroupBy( captionSql, captionAlias );
        }
      }

      // Figure out the order-by part
      final String orderByAlias;
      if ( !currLevel.getKeyExp().equals( currLevel.getOrdinalExp() ) ) {
        String ordinalSql = getExpression( ordinalExp, sqlQuery );
        orderByAlias = sqlQuery.addSelect( ordinalSql, null );
        if ( needsGroupBy ) {
          sqlQuery.addGroupBy( ordinalSql, orderByAlias );
        }
        if ( whichSelect == WhichSelect.ONLY ) {
          sqlQuery.addOrderBy(
            ordinalSql, orderByAlias, true, false, true, true );
        }
      } else {
        if ( whichSelect == WhichSelect.ONLY ) {
          sqlQuery.addOrderBy(
            keySql, keyAlias, true, false, true, true );
        }
      }

      // Add the contextual level constraints.
      constraint.addLevelConstraint(
        sqlQuery, baseCube, aggStar, currLevel );

      if ( levelCollapsed && requiresJoinToDim( targetExp ) ) {
        // add join between key and aggstar
        // join to dimension tables starting
        // at the lowest granularity and working
        // towards the fact table
        hierarchy.addToFromInverse( sqlQuery, currLevel.getKeyExp() );

        RolapStar.Column starColumn =
          ( (RolapCubeLevel) currLevel ).getStarKeyColumn();
        int bitPos = starColumn.getBitPosition();
        AggStar.Table.Column aggColumn = aggStar.lookupColumn( bitPos );
        RolapStar.Condition condition =
          new RolapStar.Condition(
            currLevel.getKeyExp(),
            aggColumn.getExpression() );
        sqlQuery.addWhere( condition.toString( sqlQuery ) );
        aggColumn.getTable().addToFrom( sqlQuery, false, true );
      } else if ( levelCollapsed ) {
        RolapStar.Column starColumn =
          ( (RolapCubeLevel) currLevel ).getStarKeyColumn();
        int bitPos = starColumn.getBitPosition();
        AggStar.Table.Column aggColumn = aggStar.lookupColumn( bitPos );
        aggColumn.getTable().addToFrom( sqlQuery, false, true );
      }

      RolapProperty[] properties = currLevel.getProperties();
      for ( RolapProperty property : properties ) {
        final SqlExpression propExp =
          targetExp.get( property.getExp() );
        final String propSql;
        if ( propExp instanceof org.eclipse.daanse.rolap.common.RolapColumn column) {
          // When dealing with a column, we must use the same table
          // alias as the one used by the level. We also assume that
          // the property lives in the same table as the level.
          propSql =
            sqlQuery.getDialect().quoteIdentifier(
                ExpressionUtil.getTableAlias(propExp),

                column.getName() );
        } else {
          propSql = getExpression( property.getExp(), sqlQuery );
        }
        final String propAlias = sqlQuery.addSelect(
          propSql,
          EnumConvertor.toBestFitColumnType(property.getType().getInternalType()) );
        if ( needsGroupBy && ( !sqlQuery.getDialect().allowsSelectNotInGroupBy()
            || !property.dependsOnLevelValue() )) {
            // Certain dialects allow us to eliminate properties
            // from the group by that are functionally dependent
            // on the level value
            sqlQuery.addGroupBy( propSql, propAlias );
        }
      }
    }
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
      if ( aggLevel.getOrdinalExp() != null ) {
        map.put( level.getOrdinalExp(), aggLevel.getOrdinalExp() );
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
    map.put( level.getOrdinalExp(), level.getOrdinalExp() );
    map.put( level.getCaptionExp(), level.getCaptionExp() );
    for ( RolapProperty prop : level.getProperties() ) {
      if ( !map.containsKey( prop.getExp() ) ) {
        map.put( prop.getExp(), prop.getExp() );
      }
    }
    return map;
  }


  private void addAggColumnToSql(
    SqlQuery sqlQuery, WhichSelect whichSelect, AggStar aggStar,
    RolapCubeLevel level ) {
    RolapStar.Column starColumn =
      level.getStarKeyColumn();
    AggStar.Table.Column aggColumn = getAggColumn( aggStar, level );
    String aggColExp = aggColumn.generateExprString( sqlQuery );
    final String colAlias =
      sqlQuery.addSelectGroupBy( aggColExp, starColumn.getInternalType() );
    if ( whichSelect == WhichSelect.ONLY ) {
      sqlQuery.addOrderBy(
        aggColExp, colAlias, true, false, true, true );
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
  AggStar chooseAggStar(
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
      SqlConstraintUtils.expandSupportedCalculatedMembers(
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

  void setMaxRows( int maxRows ) {
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
