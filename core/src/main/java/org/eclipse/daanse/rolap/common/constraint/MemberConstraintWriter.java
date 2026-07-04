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

import static org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.render;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.agg.ListColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.LiteralStarPredicate;
import org.eclipse.daanse.rolap.common.agg.MemberColumnPredicate;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Member-set constraint writer used by implementations of
 * {@link org.eclipse.daanse.rolap.common.sql.SqlConstraint}: generates the "WHERE parent = value"
 * and "WHERE exp IN (...)" member constraints (single- and multi-level) into the query under
 * construction.
 *
 * @author av
 * @since Nov 21, 2005
 */
public class MemberConstraintWriter {

    private static final String ADD_MEMBER_CONSTRAINT_CANNOT_RESTRICT_SQL_TO_CALCULATED_MEMBER = "addMemberConstraint: cannot restrict SQL to calculated member :";
    private static final String AND = " and ";
    private static final Logger LOG = LoggerFactory.getLogger( MemberConstraintWriter.class );
    private final static String nativeSqlInClauseTooLarge = """
    Cannot use native aggregation constraints for level ''{0}'' because the number of members is larger than the value of ''daanse.rolap.maxConstraints'' ({1})
    """;

    /** Utility class */
  private MemberConstraintWriter() {
  }

  /** The dialect-free builder node for the (possibly aggregate-substituted) column — a plain column becomes a
   *  Column node, a computed one a RawVariant the renderer resolves per dialect at render.
   *  Empty only for a column-less column or a missing aggregate substitution. This helper
   *  has no FROM side effect, so routing a computed column here only swaps the column expression. */
  private static java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression>
      dialectFreeColumnNode( AggStar aggStar, RolapStar.Column column ) {
    if ( aggStar == null ) {
      return column.getExpression() == null ? java.util.Optional.empty()
          : java.util.Optional.of( org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor( column ) );
    }
    AggStar.Table.Column aggColumn = aggStar.lookupColumn( column.getBitPosition() );
    return aggColumn == null ? java.util.Optional.empty()
        : java.util.Optional.ofNullable( aggColumn.toSqlExpression() );
  }

  /** Like {@link #dialectFreeColumnNode} but resolves a level's base star column AND adds the needed table to
   *  FROM — mirroring the side effect of {@code getColumnString}. Empty for a computed-key level or computed
   *  (agg) column (caller falls back to the raw-string path). */
  private static java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression>
      columnNodeAddingFrom( QueryRecorder sqlQuery, AggStar aggStar, RolapCube baseCube, RolapLevel level ) {
    RolapStar.Column column = ( level instanceof RolapCubeLevel rcl ) ? rcl.getBaseStarKeyColumn( baseCube ) : null;
    if ( column == null ) {
      return java.util.Optional.empty();
    }
    if ( aggStar != null ) {
      AggStar.Table.Column aggColumn = aggStar.lookupColumn( column.getBitPosition() );
      if ( aggColumn == null ) {
        return java.util.Optional.empty();
      }
      org.eclipse.daanse.sql.statement.api.expression.SqlExpression node = aggColumn.toSqlExpression();
      if ( node == null ) {
        return java.util.Optional.empty();
      }
      aggColumn.getTable().addToFrom( sqlQuery, false, true );
      return java.util.Optional.of( node );
    }
    if ( column.getExpression() == null ) {
      return java.util.Optional.empty();
    }
    // Plain column -> Column node; computed -> RawVariant (renderer resolves per dialect). The
    // addToFrom (the column's table) is the same call either way, so the FROM is unchanged.
    level.getHierarchy().addToFrom( sqlQuery, column.getTable() );
    return java.util.Optional.of( org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor( column ) );
  }
  /**
   * Creates a "WHERE parent = value" constraint.
   *
   * @param sqlQuery
   *          the query to modify
   * @param baseCube
   *          base cube if virtual
   * @param aggStar
   *          Definition of the aggregate table, or null
   * @param parent
   *          the list of parent members
   * @param restrictMemberTypes
   *          defines the behavior if parent is a calculated member. If true, an exception is thrown
   */
  public static void addMemberConstraint( Dialect dialect, QueryRecorder sqlQuery, RolapCube baseCube, AggStar aggStar, RolapMember parent,
      boolean restrictMemberTypes ) {
    List<RolapMember> list = Collections.singletonList( parent );
    boolean exclude = false;
    addMemberConstraint( dialect, sqlQuery, baseCube, aggStar, list, restrictMemberTypes, false, exclude );
  }

  /**
   * Creates a "WHERE exp IN (...)" condition containing the values of all parents. All parents must belong to the same
   * level.
   *
   * 
   * If this constraint is part of a native cross join, there are multiple constraining members, and the members
   * comprise the cross product of all unique member keys referenced at each level, then generating IN expressions would
   * result in incorrect results. In that case, "WHERE ((level1 = val1a AND level2 = val2a AND ...) OR (level1 = val1b
   * AND level2 = val2b AND ...) OR ..." is generated instead.
   *
   * @param sqlQuery
   *          the query to modify
   * @param baseCube
   *          base cube if virtual
   * @param aggStar
   *          (not used)
   * @param members
   *          the list of members for this constraint
   * @param restrictMemberTypes
   *          defines the behavior if parents contains calculated members. If true, and one of the members
   *          is calculated, an exception is thrown.
   * @param crossJoin
   *          true if constraint is being generated as part of a native crossjoin
   * @param exclude
   *          whether to exclude the members in the SQL predicate. e.g. not in { member list}.
   */
  public static void addMemberConstraint( Dialect dialect, QueryRecorder sqlQuery, RolapCube baseCube, AggStar aggStar,
      List<RolapMember> members, boolean restrictMemberTypes, boolean crossJoin, boolean exclude ) {
    if ( members.isEmpty() ) {
      // Generate a predicate which is always false in order to produce
      // the empty set. It would be smarter to avoid executing SQL at
      // all in this case, but doing it this way avoid special-case
      // evaluation code.
      String predicate = "(1 = 0)";
      if ( exclude ) {
        predicate = "(1 = 1)";
      }
      // Diagnostic comment only; the raw predicate renders its bare string byte-for-byte.
      final String comment = ( exclude ? "nonempty (exclude all)" : "nonempty (empty set)" );
      sqlQuery.addWhere(
          org.eclipse.daanse.sql.statement.api.Predicates.raw( predicate ), comment );
      return;
    }

    // Find out the first(lowest) unique parent level.
    // Only need to compare members up to that level.
    RolapMember member = members.get( 0 );
    RolapLevel memberLevel = member.getLevel();
    RolapMember firstUniqueParent = member;
    RolapLevel firstUniqueParentLevel = null;
    for ( ; firstUniqueParent != null && !firstUniqueParent.getLevel().isUnique(); firstUniqueParent =
        firstUniqueParent.getParentMember() ) {
    }

    if ( firstUniqueParent != null ) {
      // There's a unique parent along the hierarchy
      firstUniqueParentLevel = firstUniqueParent.getLevel();
    }

    String condition = "(";

    // If this constraint is part of a native cross join and there
    // are multiple values for the parent members, then we can't
    // use single value IN clauses
    if ( crossJoin && !memberLevel.isUnique() && !membersAreCrossProduct( members ) ) {
      assert ( member.getParentMember() != null );
      if ( !exclude ) {
        // Dialect-free tuple-IN for the common case (multi-value, no NULL, plain). and(List.of(p)) supplies
        // the outer "(" + ... + ")" wrap; otherwise fall back to the string compound.
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> tuplePred =
            multiValueInPredicate( dialect, sqlQuery, baseCube, aggStar, members, firstUniqueParentLevel, restrictMemberTypes );
        if ( tuplePred.isPresent() ) {
          sqlQuery.addWhere(
              org.eclipse.daanse.sql.statement.api.Predicates.and( java.util.List.of( tuplePred.get() ) ),
              "member set " + memberLevel.getUniqueName() + " (" + members.size() + " members)" );
          return;
        }
      }
      condition +=
          constrainMultiLevelMembers( dialect, sqlQuery, baseCube, aggStar, members, firstUniqueParentLevel,
              restrictMemberTypes, exclude );
    } else {
      java.util.Optional<java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate>> pred =
          generateSingleValueInPredicate( dialect, sqlQuery, baseCube, aggStar, members, firstUniqueParentLevel,
              restrictMemberTypes, exclude, true );
      if ( pred.isPresent() ) {
        // Wrap the per-level parts in one And/Or — its parens supply the outer "(" + ... + ")" wrap.
        // exclude joins levels with OR, otherwise AND.
        sqlQuery.addWhere( exclude
            ? org.eclipse.daanse.sql.statement.api.Predicates.or( pred.get() )
            : org.eclipse.daanse.sql.statement.api.Predicates.and( pred.get() ),
            ( exclude ? "member set (exclude) " : "member set " )
                + memberLevel.getUniqueName() + " (" + members.size() + " members)" );
        return;
      }
      condition +=
          generateSingleValueInExpr( dialect, sqlQuery, baseCube, aggStar, members, firstUniqueParentLevel, restrictMemberTypes,
              exclude, true );
    }

    if ( condition.length() > 1 ) {
      // condition is not empty
      condition += ")";
      sqlQuery.addWhere( condition );
    }
  }

  private static StarColumnPredicate getColumnPredicates( RolapStar.Column column, Collection<RolapMember> members ) {
    switch ( members.size() ) {
      case 0:
        return new LiteralStarPredicate( column, false );
      case 1:
        return new MemberColumnPredicate( column, members.iterator().next() );
      default:
        List<StarColumnPredicate> predicateList = new ArrayList<>();
        for ( RolapMember member : members ) {
          predicateList.add( new MemberColumnPredicate( column, member ) );
        }
        return new ListColumnPredicate( column, predicateList );
    }
  }

  private static LinkedHashSet<RolapMember> getUniqueParentMembers( Collection<RolapMember> members ) {
    LinkedHashSet<RolapMember> set = new LinkedHashSet<>();
    for ( RolapMember m : members ) {
      m = m.getParentMember();
      if ( m != null ) {
        set.add( m );
      }
    }
    return set;
  }

  /**
   * Adds to the where clause of a query expression matching a specified list of members
   *
   * @param sqlQuery
   *          query containing the where clause
   * @param baseCube
   *          base cube if virtual
   * @param aggStar
   *          aggregate star if available
   * @param members
   *          list of constraining members
   * @param fromLevel
   *          lowest parent level that is unique
   * @param restrictMemberTypes
   *          defines the behavior when calculated members are present
   * @param exclude
   *          whether to exclude the members. Default is false.
   *
   * @return a non-empty String if SQL is generated for the multi-level member list.
   */
  private static String constrainMultiLevelMembers( Dialect dialect, QueryRecorder sqlQuery, RolapCube baseCube, AggStar aggStar,
      List<RolapMember> members, RolapLevel fromLevel, boolean restrictMemberTypes, boolean exclude ) {
    // Use LinkedHashMap so that keySet() is deterministic.
    Map<RolapMember, List<RolapMember>> parentChildrenMap = new LinkedHashMap<>();
    StringBuilder condition = new StringBuilder();
    StringBuilder condition1 = new StringBuilder();
    if ( exclude ) {
      condition.append( "not (" );
    }

    // First try to generate IN list for all members
    if ( dialect.supportsMultiValueInExpr() ) {
      condition1.append( generateMultiValueInExpr( dialect, sqlQuery, baseCube, aggStar, members, fromLevel,
          restrictMemberTypes, parentChildrenMap ) );

      // The members list might contain NULL values in the member levels.
      //
      // e.g.
      // [USA].[CA].[San Jose]
      // [null].[null].[San Francisco]
      // [null].[null].[Los Angeles]
      // [null].[CA].[San Diego]
      // [null].[CA].[Sacramento]
      //
      // Pick out such members to generate SQL later.
      // These members are organized in a map that maps the parant levels
      // containing NULL to all its children members in the list. e.g.
      // the member list above becomes the following map, after SQL is
      // generated for [USA].[CA].[San Jose] in the call above.
      //
      // [null].[null]->([San Francisco], [Los Angeles])
      // [null]->([CA].[San Diego], [CA].[Sacramento])
      //
      if ( parentChildrenMap.isEmpty() ) {
        condition.append( condition1.toString() );
        if ( exclude ) {
          // If there are no NULL values in the member levels, then
          // we're done except we need to also explicitly include
          // members containing nulls across all levels.
          condition.append( ")" );
          condition.append( " or " );
          condition.append( generateMultiValueIsNullExprs( dialect, sqlQuery, baseCube, members.get( 0 ), fromLevel,
              aggStar ) );
        }
        return condition.toString();
      }
    } else {
      // Multi-value IN list not supported
      // Classify members into List that share the same parent.
      //
      // Using the same example as above, the resulting map will be
      // [USA].[CA]->[San Jose]
      // [null].[null]->([San Francisco], [Los Angesles])
      // [null].[CA]->([San Diego],[Sacramento])
      //
      // The idea is to be able to "compress" the original member list
      // into groups that can use single value IN list for part of the
      // comparison that does not involve NULLs
      //
      for ( RolapMember m : members ) {
        if ( m.isCalculated() ) {
          if ( restrictMemberTypes ) {
            throw Util.newInternal( new StringBuilder(ADD_MEMBER_CONSTRAINT_CANNOT_RESTRICT_SQL_TO_CALCULATED_MEMBER)
                .append(m).toString() );
          }
          continue;
        }
        RolapMember p = m.getParentMember();
        List<RolapMember> childrenList = parentChildrenMap.get( p );
        if ( childrenList == null ) {
          childrenList = new ArrayList<>();
          parentChildrenMap.put( p, childrenList );
        }
        childrenList.add( m );
      }
    }

    // Now we try to generate predicates for the remaining
    // parent-children group.

    // Note that NULLs are not used to enforce uniqueness
    // so we ignore the fromLevel here.
    boolean firstParent = true;
    StringBuilder condition2 = new StringBuilder();

    if ( condition1.length() > 0 ) {
      // Some members have already been translated into IN list.
      firstParent = false;
      condition.append( condition1.toString() );
      condition.append( " or " );
    }

    RolapLevel memberLevel = members.get( 0 ).getLevel();

    // The children for each parent are turned into IN list so they
    // should not contain null.
    for ( RolapMember p : parentChildrenMap.keySet() ) {
      assert p != null;
      if ( condition2.toString().length() > 0 ) {
        condition2.append( " or " );
      }

      condition2.append( "(" );

      // First generate ANDs for all members in the parent lineage of
      // this parent-children group
      int levelCount = 0;
      for ( RolapMember gp = p; gp != null; gp = gp.getParentMember() ) {
        if ( gp.isAll() ) {
          // Ignore All member
          // Get the next parent
          continue;
        }

        RolapLevel level = gp.getLevel();

        // add the level to the FROM clause if this is the
        // first parent-children group we're generating sql for
        if ( firstParent ) {
          RolapHierarchy hierarchy = level.getHierarchy();

          // this method can be called within the context of shared
          // members, outside of the normal rolap star, therefore
          // we need to check the level to see if it is a shared or
          // cube level.

          RolapStar.Column column = null;
          if ( level instanceof RolapCubeLevel rolapCubeLevel) {
            column = rolapCubeLevel.getBaseStarKeyColumn( baseCube );
          }
          if ( column != null ) {
            if ( aggStar != null ) {
              int bitPos = column.getBitPosition();
              AggStar.Table.Column aggColumn = aggStar.lookupColumn( bitPos );
              AggStar.Table table = aggColumn.getTable();
              table.addToFrom( sqlQuery, false, true );
            } else {
              RolapStar.Table targetTable = column.getTable();
              hierarchy.addToFrom( sqlQuery, targetTable );
            }
          } else {
            assert ( aggStar == null );
            hierarchy.addToFrom( sqlQuery, level.getKeyExp() );
          }
        }

        String levelValue = getColumnValue( level.getNameExp() != null
            ? gp.getName() : gp.getKey(), dialect, level.getDatatype() );
        // Dialect-free per-level name constraint: build it as a Predicate (computed columns -> RawVariant,
        // resolved per dialect by the renderer) and render it into the condition string — byte-identical
        // to constrainLevel for the value case. The NULL case stays on the string path ("col IS null"):
        // the renderer's isNull spells "is" lowercase, and a null parent value is always a plain key
        // column here.
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> levelPred =
            RolapUtil.mdxNullLiteral().equalsIgnoreCase( levelValue )
                ? java.util.Optional.empty()
                : LevelConstraintGenerator.constrainLevelPredicate( level, baseCube, aggStar, new String[] { levelValue }, false );
        String levelCondition =
            levelPred.map( lvlPred -> org.eclipse.daanse.rolap.common.SqlRender.renderPredicate( lvlPred, dialect ) )
                .orElseGet( () -> LevelConstraintGenerator.constrainLevel( dialect, level, sqlQuery, baseCube, aggStar, levelValue, false )
                    .toString() );
        // constrainLevel legitimately yields "" when the aggregate table lacks the level's name column
        // (it logs "A constraint will not be generated ..."): skip the piece entirely — appending the
        // " and " separator around an empty piece would render invalid SQL like "( and col = 10)".
        if ( !levelCondition.isEmpty() ) {
          if ( levelCount > 0 ) {
            condition2.append( AND );
          }
          ++levelCount;
          condition2.append( levelCondition );
        }
        if ( gp.getLevel() == fromLevel ) {
          // SQL is completely generated for this parent
          break;
        }
      }
      firstParent = false;

      // Next, generate children for this parent-children group
      List<RolapMember> children = parentChildrenMap.get( p );

      // If no children to be generated for this parent then we are done
      if ( !children.isEmpty() ) {
        Map<RolapMember, List<RolapMember>> tmpParentChildrenMap = new HashMap<>();

        if ( levelCount > 0 ) {
          condition2.append( AND );
        }
        RolapLevel childrenLevel = (RolapLevel) ( p.getLevel().getChildLevel() );

        if ( dialect.supportsMultiValueInExpr() && childrenLevel != memberLevel ) {
          // Multi-level children and multi-value IN list supported
          condition2.append( generateMultiValueInExpr( dialect, sqlQuery, baseCube, aggStar, children, childrenLevel,
              restrictMemberTypes, tmpParentChildrenMap ) );
          assert tmpParentChildrenMap.isEmpty();
        } else {
          // Can only be single level children
          // If multi-value IN list not supported, children will be on
          // the same level as members list. Only single value IN list
          // needs to be generated for this case.
          assert childrenLevel == memberLevel;
          condition2.append( generateSingleValueInExpr( dialect, sqlQuery, baseCube, aggStar, children, childrenLevel,
              restrictMemberTypes, false, true ) );
        }
      }
      // SQL is complete for this parent-children group.
      condition2.append( ")" );
    }

    // In the case where multi-value IN expressions are not generated,
    // condition2 contains the entire filter condition. In the
    // case of excludes, we also need to explicitly include null values,
    // minus the ones that are referenced in condition2. Therefore,
    // we OR on a condition that corresponds to an OR'ing of IS NULL
    // filters on each level PLUS an exclusion of condition2.
    //
    // Note that the expression generated is non-optimal in the case where
    // multi-value IN's cannot be used because we end up excluding
    // non-null values as well as the null ones. Ideally, we only need to
    // exclude the expressions corresponding to nulls, which is possible
    // in the multi-value IN case, since we have a map of the null values.
    condition.append( condition2.toString() );
    if ( exclude ) {
      condition.append( ") or (" );
      condition.append( generateMultiValueIsNullExprs( dialect, sqlQuery, baseCube, members.get( 0 ), fromLevel, aggStar ) );
      condition.append( " and not(" );
      condition.append( condition2.toString() );
      condition.append( "))" );
    }

    return condition.toString();
  }

  /**
   * @param members
   *          list of members
   *
   * @return true if the members comprise the cross product of all unique member keys referenced at each level
   */
  private static boolean membersAreCrossProduct( List<RolapMember> members ) {
    int crossProdSize = getNumUniqueMemberKeys( members );
    for ( Collection<RolapMember> parents = getUniqueParentMembers( members ); !parents.isEmpty(); parents =
        getUniqueParentMembers( parents ) ) {
      crossProdSize *= parents.size();
    }
    return ( crossProdSize == members.size() );
  }

  /**
   * @param members
   *          list of members
   *
   * @return number of unique member keys in a list of members
   */
  private static int getNumUniqueMemberKeys( List<RolapMember> members ) {
    final HashSet<Object> set = new HashSet<>();
    for ( RolapMember m : members ) {
      set.add( m.getKey() );
    }
    return set.size();
  }

  /**
   * @param key
   *          key corresponding to a member
   * @param dialect
   *          sql dialect being used
   * @param datatype
   *          data type of the member
   *
   * @return string value corresponding to the member
   */
  private static String getColumnValue( Object key, Dialect dialect, Datatype datatype ) {
    if ( key != Util.sqlNullValue ) {
      return key.toString();
    } else {
      return RolapUtil.mdxNullLiteral();
    }
  }
  /**
   * Dialect-free counterpart of the common {@link #generateMultiValueInExpr} case (multi-value IN supported,
   * no NULL-level member values, plain non-aggregate columns): {@code (c1, c2, …) IN ((v1a, v1b, …), …)} as a
   * builder {@code Predicates.inTuple}, in the same leaf→{@code fromLevel} column order — byte-equal to the
   * string form because the renderer emits {@code "(" cols ") in (" rows ")"} with the same {@code ", "}
   * separators and renders each literal via {@code dialect.quote(value, datatype)}. Returns empty (caller
   * uses the string path) for aggregate/computed columns, any NULL member value (handled by the NULL
   * side-map), an unsupported multi-value dialect, or a non-uniform level shape.
   */
  private static java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate>
      multiValueInPredicate( Dialect dialect, QueryRecorder sqlQuery, RolapCube baseCube, AggStar aggStar,
      List<RolapMember> members, RolapLevel fromLevel, boolean restrictMemberTypes ) {
    if ( !dialect.supportsMultiValueInExpr() ) {
      return java.util.Optional.empty();
    }
    List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> colExprs = new ArrayList<>();
    for ( RolapMember m = members.get( 0 ); m != null; m = m.getParentMember() ) {
      if ( m.isAll() ) {
        continue;
      }
      RolapLevel level = m.getLevel();
      java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> node =
          columnNodeAddingFrom( sqlQuery, aggStar, baseCube, level );
      if ( node.isEmpty() ) {
        return java.util.Optional.empty();
      }
      colExprs.add( node.get() );
      if ( level == fromLevel ) {
        break;
      }
    }
    if ( colExprs.isEmpty() ) {
      return java.util.Optional.empty();
    }
    List<List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression>> valueRows = new ArrayList<>();
    for ( RolapMember m : members ) {
      if ( m.isCalculated() ) {
        if ( restrictMemberTypes ) {
          throw Util.newInternal( new StringBuilder(ADD_MEMBER_CONSTRAINT_CANNOT_RESTRICT_SQL_TO_CALCULATED_MEMBER)
              .append(m).toString() );
        }
        continue;
      }
      List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> row = new ArrayList<>();
      for ( RolapMember p = m; p != null; p = p.getParentMember() ) {
        if ( p.isAll() ) {
          continue;
        }
        RolapLevel level = p.getLevel();
        String value = getColumnValue( p.getKey(), dialect, level.getDatatype() );
        if ( RolapUtil.mdxNullLiteral().equalsIgnoreCase( value ) ) {
          return java.util.Optional.empty(); // NULL member value -> string path (NULL side-map)
        }
        row.add( org.eclipse.daanse.sql.statement.api.Expressions.literal( value, level.getDatatype() ) );
        if ( p.getLevel() == fromLevel ) {
          break;
        }
      }
      if ( row.size() != colExprs.size() ) {
        return java.util.Optional.empty(); // non-uniform level shape
      }
      valueRows.add( row );
    }
    if ( valueRows.isEmpty() ) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(
        org.eclipse.daanse.sql.statement.api.Predicates.inTuple( colExprs, valueRows ) );
  }

  /**
   * Generates a multi-value IN expression corresponding to a list of member expressions, and adds the expression to the
   * WHERE clause of a query, provided the member values are all non-null
   *
   * @param sqlQuery
   *          query containing the where clause
   * @param baseCube
   *          base cube if virtual
   * @param aggStar
   *          aggregate star if available
   * @param members
   *          list of constraining members
   * @param fromLevel
   *          lowest parent level that is unique
   * @param restrictMemberTypes
   *          defines the behavior when calculated members are present
   * @param parentWithNullToChildrenMap
   *          upon return this map contains members that have Null values in its (parent) levels
   * @return a non-empty String if multi-value IN list was generated for some members
   */
  private static String generateMultiValueInExpr( Dialect dialect, QueryRecorder sqlQuery, RolapCube baseCube, AggStar aggStar,
      List<RolapMember> members, RolapLevel fromLevel, boolean restrictMemberTypes,
      Map<RolapMember, List<RolapMember>> parentWithNullToChildrenMap ) {
    final StringBuilder columnBuf = new StringBuilder();
    final StringBuilder valueBuf = new StringBuilder();
    final StringBuilder memberBuf = new StringBuilder();

    columnBuf.append( "(" );

    // generate the left-hand side of the IN expression
    int ordinalInMultiple = 0;
    for ( RolapMember m = members.get( 0 ); m != null; m = m.getParentMember() ) {
      if ( m.isAll() ) {
        continue;
      }
      String columnString = getColumnString( dialect, sqlQuery, aggStar, m.getLevel(), baseCube );

      if ( ordinalInMultiple++ > 0 ) {
        columnBuf.append( ", " );
      }

      columnBuf.append( columnString );

      // Only needs to compare up to the first(lowest) unique level.
      if ( m.getLevel() == fromLevel ) {
        break;
      }
    }

    columnBuf.append( ")" );

    // generate the RHS of the IN predicate
    valueBuf.append( "(" );
    int memberOrdinal = 0;
    for ( RolapMember m : members ) {
      if ( m.isCalculated() ) {
        if ( restrictMemberTypes ) {
          throw Util.newInternal( new StringBuilder(ADD_MEMBER_CONSTRAINT_CANNOT_RESTRICT_SQL_TO_CALCULATED_MEMBER)
              .append(m).toString() );
        }
        continue;
      }

      ordinalInMultiple = 0;
      memberBuf.setLength( 0 );
      memberBuf.append( "(" );

      boolean containsNull = false;
      for ( RolapMember p = m; p != null; p = p.getParentMember() ) {
        if ( p.isAll() ) {
          // Ignore the ALL level.
          // Generate SQL condition for the next level
          continue;
        }
        RolapLevel level = p.getLevel();

        String value = getColumnValue( p.getKey(), dialect, level.getDatatype() );

        // If parent at a level is NULL, record this parent and all
        // its children(if there's any)
        if ( RolapUtil.mdxNullLiteral().equalsIgnoreCase( value ) ) {
          // Add to the nullParent map
          List<RolapMember> childrenList = parentWithNullToChildrenMap.get( p );
          if ( childrenList == null ) {
            childrenList = new ArrayList<>();
            parentWithNullToChildrenMap.put( p, childrenList );
          }

          // If p has children
          if ( m != p ) {
            childrenList.add( m );
          }

          // Skip generating condition for this parent
          containsNull = true;
          break;
        }

        if ( ordinalInMultiple++ > 0 ) {
          memberBuf.append( ", " );
        }

        dialect.quote( memberBuf, value, level.getDatatype() );

        // Only needs to compare up to the first(lowest) unique level.
        if ( p.getLevel() == fromLevel ) {
          break;
        }
      }

      // Now check if sql string is sucessfully generated for this member.
      // If parent levels do not contain NULL then SQL must have been
      // generated successfully.
      if ( !containsNull ) {
        memberBuf.append( ")" );
        if ( memberOrdinal++ > 0 ) {
          valueBuf.append( ", " );
        }
        valueBuf.append( memberBuf );
      }
    }

    StringBuilder condition = new StringBuilder();
    if ( memberOrdinal > 0 ) {
      // SQLs are generated for some members.
      condition.append( columnBuf );
      condition.append( " in " );
      condition.append( valueBuf );
      condition.append( ")" );
    }

    return condition.toString();
  }

  /**
   * Returns the column expression for the level, assuring appropriate tables are added to the from clause of sqlQuery
   * if required. Determines the correct table and field based on the cube and whether an AggStar is present.
   */
  private static String getColumnString( Dialect dialect, QueryRecorder sqlQuery, AggStar aggStar, RolapLevel level, RolapCube baseCube ) {
    String columnString;
    RolapStar.Column column = null;
    if ( level instanceof RolapCubeLevel rolapCubeLevel) {
      // this method can be called within the context of shared members,
      // outside of the normal rolap star, therefore we need to
      // check the level to see if it is a shared or cube level.
      column = rolapCubeLevel.getBaseStarKeyColumn( baseCube );
    }

    // REVIEW: The following code mostly uses the name column (or name
    // expression) of the level. Shouldn't it use the key column (or key
    // expression)?
    RolapHierarchy hierarchy = level.getHierarchy();
    if ( column != null ) {
      if ( aggStar != null ) {
        // this assumes that the name column is identical to the
        // id column
        int bitPos = column.getBitPosition();
        AggStar.Table.Column aggColumn = aggStar.lookupColumn( bitPos );
        AggStar.Table table = aggColumn.getTable();
        table.addToFrom( sqlQuery, false, true );
        columnString = aggColumn.generateExprString( dialect );
      } else {
        RolapStar.Table targetTable = column.getTable();
        hierarchy.addToFrom( sqlQuery, targetTable );
        columnString = column.generateExprString( dialect );
      }
    } else {
      assert ( aggStar == null );
      hierarchy.addToFrom( sqlQuery, level.getKeyExp() );

      SqlExpression nameExp = level.getNameExp();
      if ( nameExp == null ) {
        nameExp = level.getKeyExp();
      }
      columnString = render( nameExp, dialect );
    }
    return columnString;
  }

  /**
   * Generates an expression that is an OR of IS NULL expressions, one per level in a RolapMember.
   *
   * @param sqlQuery
   *          query corresponding to the expression
   * @param baseCube
   *          base cube if virtual
   * @param member
   *          the RolapMember
   * @param fromLevel
   *          lowest parent level that is unique
   * @param aggStar
   *          aggregate star if available
   * @return the text of the expression
   */
  private static String generateMultiValueIsNullExprs( Dialect dialect, QueryRecorder sqlQuery, RolapCube baseCube, RolapMember member,
      RolapLevel fromLevel, AggStar aggStar ) {
    final StringBuilder conditionBuf = new StringBuilder();

    conditionBuf.append( "(" );

    // generate the left-hand side of the IN expression
    boolean isFirstLevelInMultiple = true;
    for ( RolapMember m = member; m != null; m = m.getParentMember() ) {
      if ( m.isAll() ) {
        continue;
      }

      String columnString = getColumnString( dialect, sqlQuery, aggStar, m.getLevel(), baseCube );

      if ( !isFirstLevelInMultiple ) {
        conditionBuf.append( " or " );
      } else {
        isFirstLevelInMultiple = false;
      }

      conditionBuf.append( columnString );
      conditionBuf.append( " is null" );

      // Only needs to compare up to the first(lowest) unique level.
      if ( m.getLevel() == fromLevel ) {
        break;
      }
    }

    conditionBuf.append( ")" );
    return conditionBuf.toString();
  }

  /**
   * Dialect-free counterpart of {@link #generateSingleValueInExpr}: the per-level value constraints as builder
   * {@link org.eclipse.daanse.sql.statement.api.expression.Predicate}s (via
   * {@code StarPredicateTranslator.toPredicate}), so callers {@code addWhere} dialect-free predicates instead
   * of a raw {@code createInExpr} string. Returns the parts IN ORDER and lets the caller combine them — the
   * generic builder's {@code And}/{@code Or} both wrap in parens, so the standalone slicer/role callers add
   * the parts as separate (paren-free) {@code AND} conjuncts while the member-constraint caller wraps them in
   * one {@code Predicates.and}/{@code or} to supply the outer {@code "(" + … + ")"}. For
   * {@code exclude}, each level is {@code not(p)} plus the null-key augmentation {@code (not(p) or (col is
   * null))}. Returns {@link java.util.Optional#empty()} (caller falls back to the string
   * path) for an aggregate or computed column, a max-value overflow, the null/calculated short-circuits, or
   * when no part is produced. The table-into-FROM side effect matches the string path (idempotent
   * {@code addToFrom}).
   */
  static java.util.Optional<List<org.eclipse.daanse.sql.statement.api.expression.Predicate>>
      generateSingleValueInPredicate( Dialect dialect, QueryRecorder sqlQuery, RolapCube baseCube, AggStar aggStar,
      List<RolapMember> members, RolapLevel fromLevel, boolean restrictMemberTypes, boolean exclude,
      boolean includeParentLevels ) {
    int maxConstraints = SystemWideProperties.instance().MaxConstraints;
    List<org.eclipse.daanse.sql.statement.api.expression.Predicate> parts = new ArrayList<>();
    for ( Collection<RolapMember> c = members; !c.isEmpty(); c = getUniqueParentMembers( c ) ) {
      RolapMember m = c.iterator().next();
      if ( m.isAll() ) {
        continue;
      }
      if ( m.isNull() ) {
        return java.util.Optional.empty();
      }
      if ( m.isCalculated() && !m.isParentChildLeaf() ) {
        if ( restrictMemberTypes ) {
          throw Util.newInternal( new StringBuilder(ADD_MEMBER_CONSTRAINT_CANNOT_RESTRICT_SQL_TO_CALCULATED_MEMBER)
              .append(m).toString() );
        }
        continue;
      }
      boolean containsNullKey = false;
      for ( RolapMember element : c ) {
        if ( element.getKey() == Util.sqlNullValue ) {
          containsNullKey = true;
        }
      }
      RolapLevel level = m.getLevel();
      RolapStar.Column column = ( level instanceof RolapCubeLevel rolapCubeLevel )
          ? rolapCubeLevel.getBaseStarKeyColumn( baseCube ) : null;
      if ( column == null ) {
        return java.util.Optional.empty(); // computed-key level -> string path
      }
      StarColumnPredicate cc = getColumnPredicates( column, c );
      if ( !dialect.supportsUnlimitedValueList() && cc instanceof ListColumnPredicate listColumnPredicate
          && listColumnPredicate.getPredicates().size() > maxConstraints ) {
        return java.util.Optional.empty(); // setSupported(false) path -> string version
      }
      // base column node, or its aggregate substitution (adds the right table to FROM); empty -> computed.
      java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> colNode =
          columnNodeAddingFrom( sqlQuery, aggStar, baseCube, level );
      if ( colNode.isEmpty() ) {
        return java.util.Optional.empty();
      }
      org.eclipse.daanse.sql.statement.api.expression.SqlExpression col = colNode.get();
      if ( !org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue( cc ) ) {
        org.eclipse.daanse.sql.statement.api.expression.Predicate p =
            org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toColumnPredicate( cc, col );
        if ( exclude ) {
          p = org.eclipse.daanse.sql.statement.api.Predicates.not( p ); // not (where)
          if ( !containsNullKey ) {
            // null key fails all filters, so re-include it: (not(where) or (col is null)).
            p = org.eclipse.daanse.sql.statement.api.Predicates.or( java.util.List.of( p,
                org.eclipse.daanse.sql.statement.api.Predicates.and( java.util.List.of(
                    org.eclipse.daanse.sql.statement.api.Predicates.isNull( col ) ) ) ) );
          }
        }
        parts.add( p );
      }
      if ( m.getLevel().isUnique() || m.getLevel() == fromLevel || !includeParentLevels ) {
        break;
      }
    }
    return parts.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of( parts );
  }

  /**
   * Pure (no FROM-mutation, no recorder, no {@link Dialect}) counterpart of
   * {@link #generateSingleValueInPredicate} for the non-aggregate (base column) case: the per-level value
   * constraints as builder {@link org.eclipse.daanse.sql.statement.api.expression.Predicate}s, for assembling
   * a {@link org.eclipse.daanse.rolap.common.sql.ConstraintContribution}. Column nodes are resolved via the
   * pure {@link #dialectFreeColumnNode} (no {@code addToFrom}) — valid only when the constrained columns
   * already live in the query's FROM (a dimension-only restriction on the level being read, e.g. an
   * exclude/Except on the target level), so it reports no extra join tables. Returns
   * {@link java.util.Optional#empty()} for an aggregate column (handled here by passing {@code aggStar==null}),
   * a computed-key level, the null/calculated short-circuits, or when no part is produced. Unlike the
   * query-mutating twin it does NOT apply the {@code supportsUnlimitedValueList} max-constraints gate (it has
   * no dialect): when the string path would have chunked the IN list the rendered SQL diverges and the
   * {@code SqlBuildGuard.orReference} guard simply falls back to the reference query. Mirrors the twin's per-level
   * grouping and {@code exclude} shape ({@code not(p)} plus the null-key re-include
   * {@code (not(p) or (col is null))}) exactly, so a present result renders byte-identically.
   */
  public static java.util.Optional<List<org.eclipse.daanse.sql.statement.api.expression.Predicate>>
      generateSingleValueInPredicatePure( RolapCube baseCube, List<RolapMember> members, RolapLevel fromLevel,
      boolean restrictMemberTypes, boolean exclude, boolean includeParentLevels ) {
    List<org.eclipse.daanse.sql.statement.api.expression.Predicate> parts = new ArrayList<>();
    for ( Collection<RolapMember> c = members; !c.isEmpty(); c = getUniqueParentMembers( c ) ) {
      RolapMember m = c.iterator().next();
      if ( m.isAll() ) {
        continue;
      }
      if ( m.isNull() ) {
        return java.util.Optional.empty();
      }
      if ( m.isCalculated() && !m.isParentChildLeaf() ) {
        if ( restrictMemberTypes ) {
          throw Util.newInternal( new StringBuilder(ADD_MEMBER_CONSTRAINT_CANNOT_RESTRICT_SQL_TO_CALCULATED_MEMBER)
              .append(m).toString() );
        }
        continue;
      }
      boolean containsNullKey = false;
      for ( RolapMember element : c ) {
        if ( element.getKey() == Util.sqlNullValue ) {
          containsNullKey = true;
        }
      }
      RolapLevel level = m.getLevel();
      RolapStar.Column column = ( level instanceof RolapCubeLevel rolapCubeLevel )
          ? rolapCubeLevel.getBaseStarKeyColumn( baseCube ) : null;
      if ( column == null ) {
        return java.util.Optional.empty(); // computed-key / virtual-without-baseCube level -> string path
      }
      StarColumnPredicate cc = getColumnPredicates( column, c );
      // Pure column node (no addToFrom); empty -> computed/column-less -> string path.
      java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> colNode =
          dialectFreeColumnNode( null, column );
      if ( colNode.isEmpty() ) {
        return java.util.Optional.empty();
      }
      org.eclipse.daanse.sql.statement.api.expression.SqlExpression col = colNode.get();
      if ( !org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue( cc ) ) {
        org.eclipse.daanse.sql.statement.api.expression.Predicate p =
            org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toColumnPredicate( cc, col );
        if ( exclude ) {
          p = org.eclipse.daanse.sql.statement.api.Predicates.not( p ); // not (where)
          if ( !containsNullKey ) {
            // null key fails all filters, so re-include it: (not(where) or (col is null)).
            p = org.eclipse.daanse.sql.statement.api.Predicates.or( java.util.List.of( p,
                org.eclipse.daanse.sql.statement.api.Predicates.and( java.util.List.of(
                    org.eclipse.daanse.sql.statement.api.Predicates.isNull( col ) ) ) ) );
          }
        }
        parts.add( p );
      }
      if ( m.getLevel().isUnique() || m.getLevel() == fromLevel || !includeParentLevels ) {
        break;
      }
    }
    return parts.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of( parts );
  }

  /**
   * Pure (no FROM-mutation, no {@link Dialect}) counterpart of {@link #addMemberConstraint} for the common
   * single-value, single-part member set (e.g. a {@code {[Customers].[CA]}} cross-join arg): the member value
   * restriction as one {@link org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate}
   * {@code (table, predicate)} — the predicate is the per-level part(s) AND-combined ({@code OR} for
   * {@code exclude}), wrapped exactly like {@code addMemberConstraint}'s outer {@code "(" … ")"},
   * and the table is the member level's star table so the mapper joins it to the fact. Returns
   * {@link java.util.Optional#empty()} (caller falls back via the guard) for an empty set, a multi-part /
   * multi-level constraint (one {@code ColumnPredicate} cannot carry it), the cross-join multi-value
   * tuple-IN path, or a computed / unresolvable column. Mirrors the single-value else-branch of
   * {@link #addMemberConstraint}.
   */
  public static java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
      memberConstraintContribution( RolapCube baseCube, List<RolapMember> members, boolean restrictMemberTypes,
      boolean exclude ) {
    if ( members.isEmpty() ) {
      return java.util.Optional.empty();
    }
    RolapMember member = members.get( 0 );
    RolapLevel memberLevel = member.getLevel();
    RolapMember firstUniqueParent = member;
    for ( ; firstUniqueParent != null && !firstUniqueParent.getLevel().isUnique();
        firstUniqueParent = firstUniqueParent.getParentMember() ) {
      // advance to the first unique parent level, as addMemberConstraint does
    }
    RolapLevel firstUniqueParentLevel = ( firstUniqueParent != null ) ? firstUniqueParent.getLevel() : null;
    java.util.Optional<List<org.eclipse.daanse.sql.statement.api.expression.Predicate>> parts =
        generateSingleValueInPredicatePure( baseCube, members, firstUniqueParentLevel, restrictMemberTypes,
            exclude, true );
    // Accept a MULTI-level path ONLY for a SINGLE member (e.g. a member.Children / Descendants([City]) arg,
    // whose one member's key path is city AND its ancestor state) — the and-wrap below then produces the
    // single "(city = 'Spokane' and state_province = 'WA')" conjunct. For a MULTI-member set spanning
    // multiple levels the per-level parts are an IN PER LEVEL, whose AND is the CROSS-PRODUCT — semantically
    // wrong vs the tuple IN "(dept, family) in ((a,b),(c,d))"; bail there so the guarded tuple-IN
    // path handles it (single-level multi-member — parts.size()==1 — stays fine: one IN per level).
    if ( parts.isEmpty() ) {
      return java.util.Optional.empty();
    }
    RolapStar.Column column = ( memberLevel instanceof RolapCubeLevel rolapCubeLevel )
        ? rolapCubeLevel.getBaseStarKeyColumn( baseCube ) : null;
    if ( column == null ) {
      return java.util.Optional.empty();
    }
    // A MULTI-member set spanning MULTIPLE levels: the per-level parts are an IN-per-level whose AND is the
    // CROSS-PRODUCT ("dept in (a,c) and family in (b,d)" = 4 combos), semantically wrong vs the TUPLE
    // IN "(dept, family) in ((a,b),(c,d))" (2 combos). Emit the tuple IN via the pure multiValueInPredicatePure
    // instead — UNLESS the members form a RECTANGLE (the distinct per-level values cross EXACTLY to the member
    // set, e.g. 2 cities in the SAME state), in which case the per-level AND is both correct AND matches the
    // factored "(city IN (..) AND state = X)" form, so fall through to and(parts) below. (exclude — a
    // negated tuple IN — is not modelled here; leave it to the string fallback.)
    if ( parts.get().size() != 1 && members.size() != 1
        && !membersFormRectangle( members, firstUniqueParentLevel ) ) {
      if ( exclude ) {
        return java.util.Optional.empty();
      }
      final RolapStar.Column memberCol = column;
      // Wrap the tuple IN in a single-operand AND so it renders "((cols) in (rows))" — the extra outer parens
      // addMemberConstraint emits around the whole conjunct (same wrap the single-member and-path
      // uses for its "(city = .. and state = ..)").
      return multiValueInPredicatePure( baseCube, members, firstUniqueParentLevel )
          .map( t -> new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate(
              memberCol.getTable(),
              org.eclipse.daanse.sql.statement.api.Predicates.and( java.util.List.of( t ) ) ) );
    }
    // A SINGLE member's multi-level key path (city AND state), or a single-level multi-member IN — wrap the
    // per-level parts: exclude joins levels with OR, otherwise AND (the And/Or wrap supplies the outer parens).
    org.eclipse.daanse.sql.statement.api.expression.Predicate where = exclude
        ? org.eclipse.daanse.sql.statement.api.Predicates.or( parts.get() )
        : org.eclipse.daanse.sql.statement.api.Predicates.and( parts.get() );
    return java.util.Optional.of(
        new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate( column.getTable(), where ) );
  }

  /**
   * Pure (no recorder) twin of {@link #multiValueInPredicate}: the tuple IN
   * {@code (col_leaf, .., col_fromLevel) in ((v,..),..)} as a builder {@code Predicates.inTuple}, in the same
   * leaf-&gt;fromLevel column order, with dialect-free column nodes and literal values (the renderer quotes them
   * at render, matching the single-value pure path). The caller adds the member level's table to the
   * contribution's joinTables. Returns empty for a calculated member, a NULL key, a computed/absent key
   * column, or a non-uniform level shape (the byte guard then falls back to the reference).
   */
  private static java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate>
      multiValueInPredicatePure( RolapCube baseCube, List<RolapMember> members, RolapLevel fromLevel ) {
    List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> colExprs = new ArrayList<>();
    for ( RolapMember m = members.get( 0 ); m != null; m = m.getParentMember() ) {
      if ( m.isAll() ) {
        continue;
      }
      RolapLevel level = m.getLevel();
      RolapStar.Column column = ( level instanceof RolapCubeLevel rcl ) ? rcl.getBaseStarKeyColumn( baseCube ) : null;
      if ( column == null || column.getExpression() == null ) {
        return java.util.Optional.empty();
      }
      colExprs.add( org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor( column ) );
      if ( level == fromLevel ) {
        break;
      }
    }
    if ( colExprs.isEmpty() ) {
      return java.util.Optional.empty();
    }
    List<List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression>> valueRows = new ArrayList<>();
    for ( RolapMember m : members ) {
      if ( m.isCalculated() ) {
        return java.util.Optional.empty();
      }
      List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> row = new ArrayList<>();
      for ( RolapMember p = m; p != null; p = p.getParentMember() ) {
        if ( p.isAll() ) {
          continue;
        }
        RolapLevel level = p.getLevel();
        Object key = p.getKey();
        if ( key == null || key == Util.sqlNullValue ) {
          return java.util.Optional.empty();
        }
        row.add( org.eclipse.daanse.sql.statement.api.Expressions.literal( key, level.getDatatype() ) );
        if ( level == fromLevel ) {
          break;
        }
      }
      if ( row.size() != colExprs.size() ) {
        return java.util.Optional.empty();
      }
      valueRows.add( row );
    }
    if ( valueRows.isEmpty() ) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(
        org.eclipse.daanse.sql.statement.api.Predicates.inTuple( colExprs, valueRows ) );
  }

  /**
   * True if {@code members} form a full RECTANGLE over their per-level key values — the distinct values at each
   * level (leaf → {@code fromLevel}), multiplied, equal the member count. Then the per-level "IN per level" AND
   * represents EXACTLY the member set (not an over-broad cross-product) AND matches the factored form
   * (e.g. two cities in the same state → "city IN (..) AND state = X"). Non-rectangles need the tuple IN.
   */
  private static boolean membersFormRectangle( List<RolapMember> members, RolapLevel fromLevel ) {
    java.util.Map<RolapLevel, java.util.Set<Object>> perLevel = new java.util.LinkedHashMap<>();
    for ( RolapMember m : members ) {
      for ( RolapMember p = m; p != null; p = p.getParentMember() ) {
        if ( p.isAll() ) {
          continue;
        }
        perLevel.computeIfAbsent( p.getLevel(), k -> new java.util.HashSet<>() ).add( p.getKey() );
        if ( p.getLevel() == fromLevel ) {
          break;
        }
      }
    }
    long product = 1L;
    for ( java.util.Set<Object> vals : perLevel.values() ) {
      product *= vals.size();
    }
    return product == members.size();
  }

  /**
   * The string form of the per-level single-value IN constraint: one {@code IN}/equality piece per
   * level from the members' level up to {@code fromLevel} (or the first unique level), joined with
   * {@code AND} ({@code OR} plus {@code not(...)}/null re-include for {@code exclude}). Adds the
   * needed tables to the FROM as a side effect.
   *
   * @param members             list of constraining members (all on the same level)
   * @param fromLevel           lowest parent level that is unique
   * @param restrictMemberTypes if true, a calculated member raises an error
   * @param exclude             whether to exclude the members ({@code not in})
   * @param includeParentLevels whether to also constrain parent levels
   * @return a non-empty String if an IN list was generated for the members
   */
  static String generateSingleValueInExpr( Dialect dialect, QueryRecorder sqlQuery, RolapCube baseCube, AggStar aggStar,
      List<RolapMember> members, RolapLevel fromLevel, boolean restrictMemberTypes, boolean exclude,
      boolean includeParentLevels ) {
    int maxConstraints = SystemWideProperties.instance().MaxConstraints;

    StringBuilder condition = new StringBuilder();
    boolean firstLevel = true;
    for ( Collection<RolapMember> c = members; !c.isEmpty(); c = getUniqueParentMembers( c ) ) {
      RolapMember m = c.iterator().next();
      if ( m.isAll() ) {
        continue;
      }
      if ( m.isNull() ) {
        return "1 = 0";
      }
      if ( m.isCalculated() && !m.isParentChildLeaf() ) {
        if ( restrictMemberTypes ) {
          throw Util.newInternal( new StringBuilder(ADD_MEMBER_CONSTRAINT_CANNOT_RESTRICT_SQL_TO_CALCULATED_MEMBER)
              .append(m).toString() );
        }
        continue;
      }

      boolean containsNullKey = false;
      for (RolapMember element : c) {
        m = element;
        if ( m.getKey() == Util.sqlNullValue ) {
          containsNullKey = true;
        }
      }

      RolapLevel level = m.getLevel();
      RolapHierarchy hierarchy = level.getHierarchy();

      // this method can be called within the context of shared members,
      // outside of the normal rolap star, therefore we need to
      // check the level to see if it is a shared or cube level.

      RolapStar.Column column = null;
      if ( level instanceof RolapCubeLevel rolapCubeLevel) {
        column = rolapCubeLevel.getBaseStarKeyColumn( baseCube );
      }

      String q;
      if ( column != null ) {
        if ( aggStar != null ) {
          int bitPos = column.getBitPosition();
          AggStar.Table.Column aggColumn = aggStar.lookupColumn( bitPos );
          if ( aggColumn == null ) {
            throw Util.newInternal( new StringBuilder("AggStar ").append(aggStar).append(" has no column for ")
                .append(column).append(" (bitPos ").append(bitPos)
                .append(")").toString() );
          }
          AggStar.Table table = aggColumn.getTable();
          table.addToFrom( sqlQuery, false, true );
          q = aggColumn.generateExprString( dialect );
        } else {
          RolapStar.Table targetTable = column.getTable();
          hierarchy.addToFrom( sqlQuery, targetTable );
          q = column.generateExprString( dialect );
        }
      } else {
        assert ( aggStar == null );
        hierarchy.addToFrom( sqlQuery, level.getKeyExp() );
        q = render( level.getKeyExp(), dialect );
      }

      StarColumnPredicate cc = getColumnPredicates( column, c );

      if ( !dialect.supportsUnlimitedValueList() && cc instanceof ListColumnPredicate listColumnPredicate && listColumnPredicate
          .getPredicates().size() > maxConstraints ) {
        // Simply get them all, do not create where-clause.
        // Below are two alternative approaches (and code). They
        // both have problems.
        LOG.debug(MessageFormat.format(nativeSqlInClauseTooLarge, level.getUniqueName(), maxConstraints ) );
        sqlQuery.setSupported( false );
      } else {
        String where = RolapStar.Column.createInExpr( q, cc, level.getDatatype(), dialect );
        if ( !where.equals( "true" ) ) {
          if ( !firstLevel ) {
            if ( exclude ) {
              condition.append(" or ");
            } else {
              condition.append(AND);
            }
          } else {
            firstLevel = false;
          }
          if ( exclude ) {
            where = new StringBuilder("not (").append(where).append(")").toString();
            if ( !containsNullKey ) {
              // Null key fails all filters so should add it here
              // if not already excluded. E.g., if the original
              // exclusion filter is :
              //
              // not(year = '1997' and quarter in ('Q1','Q3'))
              //
              // then with IS NULL checks added, the filter
              // becomes:
              //
              // (not(year = '1997') or year is null) or
              // (not(quarter in ('Q1','Q3')) or quarter is null)
              where = new StringBuilder("(").append(where).append(" or ").append("(").append(q).append(" is null))").toString();
            }
          }
          condition.append(where);
        }
      }

      if ( m.getLevel().isUnique() || m.getLevel() == fromLevel || !includeParentLevels ) {
        break; // no further qualification needed
      }
    }

    return condition.toString();
  }
}
