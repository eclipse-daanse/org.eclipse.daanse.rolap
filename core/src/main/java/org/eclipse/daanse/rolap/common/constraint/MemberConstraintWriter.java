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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.agg.ListColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.LiteralStarPredicate;
import org.eclipse.daanse.rolap.common.agg.MemberColumnPredicate;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
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
   * @param datatype
   *          data type of the member
   *
   * @return string value corresponding to the member
   */
  private static String getColumnValue( Object key, Datatype datatype ) {
    if ( key != Util.sqlNullValue ) {
      return key.toString();
    } else {
      return RolapUtil.mdxNullLiteral();
    }
  }





  /**
   * Pure (no FROM-mutation, no recorder, no {@code Dialect}) counterpart of the
   * {@code generateSingleValueInPredicate} recorder form for the non-aggregate (base column) case: the per-level value
   * constraints as builder {@link org.eclipse.daanse.sql.statement.api.expression.Predicate}s, for assembling
   * a {@link org.eclipse.daanse.rolap.common.sql.ConstraintContribution}. Column nodes are resolved via the
   * pure {@link #dialectFreeColumnNode} (no {@code addToFrom}) — valid only when the constrained columns
   * already live in the query's FROM (a dimension-only restriction on the level being read, e.g. an
   * exclude/Except on the target level), so it reports no extra join tables. Returns
   * {@link java.util.Optional#empty()} for an aggregate column (handled here by passing {@code aggStar==null}),
   * a computed-key level, the null/calculated short-circuits, or when no part is produced. Unlike the
   * query-mutating twin it does NOT apply the {@code supportsUnlimitedValueList} max-constraints gate (it has
   * no dialect): the pure form always emits the unchunked {@code IN}. On the corpus dialects (unlimited
   * value lists) that matches the recorder; a value-list-LIMITED dialect would need the
   * chunking decision moved behind the render boundary. Mirrors the twin's per-level
   * grouping and {@code exclude} shape ({@code not(p)} plus the null-key re-include
   * {@code (not(p) or (col is null))}) exactly, so a present result renders identically.
   */
  public static java.util.Optional<List<org.eclipse.daanse.sql.statement.api.expression.Predicate>>
      generateSingleValueInPredicatePure( RolapCube baseCube, List<RolapMember> members, RolapLevel fromLevel,
      boolean restrictMemberTypes, boolean exclude, boolean includeParentLevels ) {
    return generateSingleValueInPredicatePure( baseCube, null, members, fromLevel, restrictMemberTypes, exclude,
        includeParentLevels );
  }

  /**
   * The aggStar-threaded form of {@link #generateSingleValueInPredicatePure(RolapCube, List, RolapLevel,
   * boolean, boolean, boolean)} (the agg-join channel's member-set leg): with a
   * non-null {@code aggStar} every level's column node is its AGGREGATE substitution
   * ({@link #dialectFreeColumnNode} is agg-aware — {@code aggStar.lookupColumn(bitPos).toSqlExpression()}),
   * so the parts render against the agg table exactly like the recorder's
   * {@code generateSingleValueInExpr} agg branch. Returns empty additionally for an agg-unresolvable
   * column or a shared / non-cube level (no star key to substitute — the recorder's agg leg asserts
   * that shape away). A {@code null} aggStar is identical to the base form.
   */
  public static java.util.Optional<List<org.eclipse.daanse.sql.statement.api.expression.Predicate>>
      generateSingleValueInPredicatePure( RolapCube baseCube, AggStar aggStar, List<RolapMember> members,
      RolapLevel fromLevel, boolean restrictMemberTypes, boolean exclude, boolean includeParentLevels ) {
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
      org.eclipse.daanse.sql.statement.api.expression.SqlExpression col;
      if ( column != null ) {
        // Pure column node (no addToFrom), or its AGGREGATE substitution when an aggStar is
        // threaded; empty -> computed/column-less/agg-unresolvable -> string path.
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> colNode =
            dialectFreeColumnNode( aggStar, column );
        if ( colNode.isEmpty() ) {
          return mcBail( ( aggStar != null ? "agg-missing-column-at-" : "computed-column-at-" )
              + level.getName(), members );
        }
        col = colNode.get();
      } else {
        if ( aggStar != null ) {
          // A shared / non-cube level has no star key to agg-substitute (the recorder's agg leg
          // asserts aggStar == null in its key-expression branch) — bail to the recorder.
          return mcBail( "agg-no-star-key-at-" + level.getName(), members );
        }
        // Shared / non-cube level (no star key): constrain on the level key expression — the
        // string twin's else-branch (hierarchy.addToFrom(keyExp) + render(keyExp)); the key
        // column already lives in the plain level FROM, so no extra join table is reported.
        col = org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor( level.getKeyExp() );
      }
      StarColumnPredicate cc = getColumnPredicates( column, c );
      if ( !org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue( cc ) ) {
        org.eclipse.daanse.sql.statement.api.expression.Predicate p =
            org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toColumnPredicate( cc, col,
                level.getDatatype() );
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
   * Pure (no FROM-mutation, no {@code Dialect}) counterpart of the {@code addMemberConstraint} recorder
   * form for the common
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
  /** Debug-gated attribution of every empty return (the guard's fallback reasons stay explainable). */
  private static <T> java.util.Optional<T> mcBail( String reason, List<RolapMember> members ) {
    org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
        "memberConstraintContribution bail reason={} first={} n={}", reason,
        members.isEmpty() ? "-" : members.get( 0 ).getUniqueName(), members.size() );
    return java.util.Optional.empty();
  }

  /** The BENIGN twin of {@link #mcBail}: an adds-nothing set (all-member / calculated-only) —
   *  every consumer maps it to its own adds-nothing form (EMPTY contribution / arg skip), so this
   *  is NOT a recorder fallback. Logged under a distinct phrase so shape censuses over the gate
   *  logs do not count it as a builder gap. */
  private static <T> java.util.Optional<T> mcAddsNothing( String reason, List<RolapMember> members ) {
    org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
        "memberConstraintContribution adds-nothing reason={} first={} n={}", reason,
        members.isEmpty() ? "-" : members.get( 0 ).getUniqueName(), members.size() );
    return java.util.Optional.empty();
  }

  public static java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
      memberConstraintContribution( RolapCube baseCube, List<RolapMember> members, boolean restrictMemberTypes,
      boolean exclude ) {
    return memberConstraintContribution( baseCube, null, members, restrictMemberTypes, exclude, false );
  }

  /**
   * The aggStar-threaded form (the SetConstraint arg channel): with a non-null
   * {@code aggStar} the member-set predicate is built on the AGGREGATE column nodes (single-value
   * INs and the multi-level tuple IN alike), via the agg-aware pure
   * generators. The returned {@link org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate}
   * still carries the BASE star table (the base join channel — symmetric with the context
   * contribution's agg mode); the aggregate-table provenance for the {@code AggPlan} is resolved
   * separately via {@link #aggMemberTable}. A {@code null} aggStar is identical to the base
   * form; an agg-unresolvable column or a no-star-key level returns empty (→ recorder).
   */
  public static java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
      memberConstraintContribution( RolapCube baseCube, AggStar aggStar, List<RolapMember> members,
      boolean restrictMemberTypes, boolean exclude ) {
    return memberConstraintContribution( baseCube, aggStar, members, restrictMemberTypes, exclude, false );
  }

  /**
   * The agg-join-channel provenance of a member-set predicate produced with a non-null
   * {@code aggStar}: the aggregate table carrying the member level's substituted key column — the
   * table an {@code AggPlan.AggColumnPredicate} pairs with the predicate the aggStar-threaded
   * {@link #memberConstraintContribution(RolapCube, AggStar, List, boolean, boolean)} returned
   * (same first-member level, same star key column). Empty when the level has no star key or the
   * agg star has no column for it — in that case the threaded producer has already bailed, so
   * callers treat empty as a bail of their own.
   */
  public static java.util.Optional<AggStar.Table> aggMemberTable(
      RolapCube baseCube, AggStar aggStar, RolapLevel memberLevel ) {
    RolapStar.Column column = ( memberLevel instanceof RolapCubeLevel rcl )
        ? rcl.getBaseStarKeyColumn( baseCube ) : null;
    if ( column == null ) {
      return java.util.Optional.empty();
    }
    AggStar.Table.Column aggColumn = aggStar.lookupColumn( column.getBitPosition() );
    return aggColumn == null ? java.util.Optional.empty() : java.util.Optional.of( aggColumn.getTable() );
  }

  /**
   * Factored variant of {@link #memberConstraintContribution}: emits the
   * FACTORED per-level form
   * {@code (city in (..) and state in (..))} for a multi-member multi-level set even when the members do
   * NOT form a rectangle — exactly what the recorder's {@code addMemberConstraint} emits on the
   * NON-cross-join path ({@code crossJoin=false}, the descendants / member-children member restriction:
   * {@code generateSingleValueInExpr}'s per-level IN over {@code members} → {@code getUniqueParentMembers}
   * in first-encounter order, stopping at the first unique / {@code fromLevel} level). The factored AND of
   * per-level INs is the recorder's deliberate bounding-box over-approximation for that path; the exact
   * tuple IN the default producer emits is the CROSS-JOIN path's form and diverges byte-wise (h2 renders
   * it as OR-of-ANDs, mysql as {@code (c1,c2) in ((..))}). AUTHORITATIVE for the computed-expression
   * tuple route ({@code SqlTupleReader.computedTupleSql} via
   * {@code DescendantsConstraint.toContributionFactoredMemberForm}) — that recorder path IS the
   * non-cross-join form. Cross-join authoritative renders keep the
   * default producer. Bails (empty) exactly where the pure per-level producer bails; availability is a
   * superset of the default producer's (the tuple branch has extra bail shapes).
   */
  public static java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
      memberConstraintContributionFactored( RolapCube baseCube, List<RolapMember> members,
      boolean restrictMemberTypes, boolean exclude ) {
    return memberConstraintContribution( baseCube, null, members, restrictMemberTypes, exclude, true );
  }

  /**
   * The pure FACTORED-EXCLUDE twin: the recorder's non-crossjoin
   * {@code addMemberConstraint(members, restrictMemberTypes, crossJoin=false, exclude=true)} form — the
   * OR of per-level NOT/null-reinclude pairs
   * {@code ((not (city in (…)) or (city is null)) or (not (state = 'CA') or (state is null)))} — exactly
   * what {@code MemberExcludeConstraint.addLevelConstraintOps} emits for the excluded level (corpus
   * exemplar: AccessControlTest#testBugMondrian_1201_*, the TopCount completeWithNullValues
   * re-read). This is DELIBERATELY the factored per-level form, NOT the exclude-tuple-IN of
   * {@link #memberConstraintContribution} (that models the CROSS-JOIN exclude,
   * {@code constrainMultiLevelMembers}' negated tuple): the non-crossjoin string path never factors
   * into a tuple. Delegates to the factored producer with {@code exclude=true} — the {@code Or} wrap of
   * the per-level parts supplies the recorder's outer {@code "(" … ")"}. Bails (empty) exactly where the
   * pure per-level producer bails.
   */
  public static java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
      memberConstraintContributionFactoredExclude( RolapCube baseCube, List<RolapMember> members,
      boolean restrictMemberTypes ) {
    return memberConstraintContribution( baseCube, null, members, restrictMemberTypes, true, true );
  }

  private static java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
      memberConstraintContribution( RolapCube baseCube, AggStar aggStar, List<RolapMember> members,
      boolean restrictMemberTypes, boolean exclude, boolean factoredMemberForm ) {
    if ( members.isEmpty() ) {
      return mcBail( "empty-member-set", members );
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
        generateSingleValueInPredicatePure( baseCube, aggStar, members, firstUniqueParentLevel,
            restrictMemberTypes, exclude, true );
    // Accept a MULTI-level path ONLY for a SINGLE member (e.g. a member.Children / Descendants([City]) arg,
    // whose one member's key path is city AND its ancestor state) — the and-wrap below then produces the
    // single "(city = 'Spokane' and state_province = 'WA')" conjunct. For a MULTI-member set spanning
    // multiple levels the per-level parts are an IN PER LEVEL, whose AND is the CROSS-PRODUCT — semantically
    // wrong vs the tuple IN "(dept, family) in ((a,b),(c,d))"; bail there so the guarded tuple-IN
    // path handles it (single-level multi-member — parts.size()==1 — stays fine: one IN per level).
    if ( parts.isEmpty() ) {
      // Empty parts is almost always a SEMANTICALLY EMPTY set, not an inexpressible one: an
      // all-member set (each member is skipped as All) or a calculated-only set (each member is
      // skipped as calculated) restricts NOTHING — the member constraint adds no WHERE and no join
      // (verified against the reference: e.g. {[All Store Types]} / {[All Products]} / {[All Gender]}
      // sets render without any member restriction). This contract cannot carry a present-but-empty
      // ColumnPredicate (the predicate is mandatory), so "adds nothing" is DELIBERATELY the empty
      // Optional: every consumer already maps an all-member set to its own adds-nothing form
      // (ConstraintContribution.EMPTY, or its existence-join decision). The split reasons keep the
      // log attributable — an all-member/calculated-only return is benign, not a builder gap.
      if ( members.stream().allMatch( RolapMember::isAll ) ) {
        return mcAddsNothing( "all-member-set", members );
      }
      if ( members.stream().allMatch( m -> m.isCalculated() && !m.isParentChildLeaf() ) ) {
        return mcAddsNothing( "calculated-only-set", members );
      }
      return mcBail( "no-per-level-parts", members );
    }
    // No star key (shared / non-cube level): the parts already constrain the level KEY EXPRESSIONS,
    // which live in the plain level FROM — carry a table-less ColumnPredicate (consumers use the
    // table only for fact joins, which a dimension-only restriction never takes).
    RolapStar.Column column = ( memberLevel instanceof RolapCubeLevel rolapCubeLevel )
        ? rolapCubeLevel.getBaseStarKeyColumn( baseCube ) : null;
    // A MULTI-member set spanning MULTIPLE levels: the per-level parts are an IN-per-level whose AND is the
    // CROSS-PRODUCT ("dept in (a,c) and family in (b,d)" = 4 combos), semantically wrong vs the TUPLE
    // IN "(dept, family) in ((a,b),(c,d))" (2 combos). Emit the tuple IN via the pure multiValueInPredicatePure
    // instead — UNLESS the members form a RECTANGLE (the distinct per-level values cross EXACTLY to the member
    // set, e.g. 2 cities in the SAME state), in which case the per-level AND is both correct AND matches the
    // factored "(city IN (..) AND state = X)" form, so fall through to and(parts) below. (exclude — a
    // negated tuple IN — is not modelled here; leave it to the string fallback.)
    if ( !factoredMemberForm && parts.get().size() != 1 && members.size() != 1
        && !membersFormRectangle( members, firstUniqueParentLevel ) ) {
      if ( exclude ) {
        // exclude (NOT-IN) multi-level tuple — build not((cols) in (rows)) OR (c_leaf is null or ..),
        // the same negated-tuple-with-null-reinclude the recorder's constrainMultiLevelMembers emits (a
        // null key never matches a tuple IN, so it is re-included). Primitives: inTuple, and
        // not/or/isNull (the single-value pure exclude path). The top-level or supplies the
        // one outer paren the addMemberConstraint wrap emits. A no-star level, or an inexpressible part (a
        // null-ancestor compound tuple, for which the pure helpers return empty), still bails to the recorder.
        if ( column == null ) {
          return mcBail( "exclude-tuple-in", members );
        }
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> excludeTuple =
            multiValueInPredicatePure( baseCube, aggStar, members, firstUniqueParentLevel );
        java.util.Optional<List<org.eclipse.daanse.sql.statement.api.expression.Predicate>> excludeIsNulls =
            isNullExprsPure( baseCube, aggStar, members, firstUniqueParentLevel );
        if ( excludeTuple.isEmpty() || excludeIsNulls.isEmpty() ) {
          return mcBail( "exclude-tuple-in", members );
        }
        org.eclipse.daanse.sql.statement.api.expression.Predicate excludeWhere =
            org.eclipse.daanse.sql.statement.api.Predicates.or( java.util.List.of(
                org.eclipse.daanse.sql.statement.api.Predicates.not( excludeTuple.get() ),
                org.eclipse.daanse.sql.statement.api.Predicates.or( excludeIsNulls.get() ) ) );
        return java.util.Optional.of(
            new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate(
                column.getTable(), excludeWhere ) );
      }
      if ( column != null ) {
        final RolapStar.Column memberCol = column;
        // Wrap the tuple IN in a single-operand AND so it renders "((cols) in (rows))" — the extra outer parens
        // addMemberConstraint emits around the whole conjunct (same wrap the single-member and-path
        // uses for its "(city = .. and state = ..)").
        return multiValueInPredicatePure( baseCube, aggStar, members, firstUniqueParentLevel )
            .map( t -> new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate(
                memberCol.getTable(),
                org.eclipse.daanse.sql.statement.api.Predicates.and( java.util.List.of( t ) ) ) );
      }
      // No star key (shared / non-cube level): fall THROUGH to the factored per-level form below.
      // A no-star member set only arrives via the non-crossjoin descendants/children path (a
      // dimension-only members query), whose constraint form IS the per-level factored IN
      // "(city in (..) and state in (..))" — over-broad for a non-rectangle, but exactly that
      // path's contract; the tuple IN belongs to the crossjoin path, which always constrains
      // star (cube) levels. Should a no-star tuple shape ever appear, the factored form renders
      // differently and the byte guard falls back to the reference — never wrong SQL.
    }
    // A SINGLE member's multi-level key path (city AND state), or a single-level multi-member IN — and, on
    // the factored form, the recorder's non-crossjoin per-level factored INs
    // "(city in (..) and state in (..))" — wrap the per-level parts: exclude joins levels with OR,
    // otherwise AND (the And/Or wrap supplies the outer parens).
    org.eclipse.daanse.sql.statement.api.expression.Predicate where = exclude
        ? org.eclipse.daanse.sql.statement.api.Predicates.or( parts.get() )
        : org.eclipse.daanse.sql.statement.api.Predicates.and( parts.get() );
    return java.util.Optional.of(
        new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate(
            column != null ? column.getTable() : null, where ) );
  }

  /**
   * Pure (no recorder) twin of {@link #multiValueInPredicate}: the tuple IN
   * {@code (col_leaf, .., col_fromLevel) in ((v,..),..)} as a builder {@code Predicates.inTuple}, in the same
   * leaf-&gt;fromLevel column order, with dialect-free column nodes and literal values (the renderer quotes them
   * at render, matching the single-value pure path). The caller adds the member level's table to the
   * contribution's joinTables. A level without a star key column (shared / non-cube level) is constrained on
   * its level KEY expression instead — same fallback as the single-value pure path; the key lives in the
   * plain level FROM, so nothing extra to join. With a non-null {@code aggStar} (the agg arg channel)
   * each column node is its AGGREGATE substitution instead; an agg-unresolvable column or a
   * no-star-key level returns empty. Returns empty for a calculated member, a NULL key, a
   * column-less star column / key-less level, or a non-uniform level shape (the byte guard then falls back
   * to the reference).
   */
  private static java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate>
      multiValueInPredicatePure( RolapCube baseCube, AggStar aggStar, List<RolapMember> members,
      RolapLevel fromLevel ) {
    List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> colExprs = new ArrayList<>();
    for ( RolapMember m = members.get( 0 ); m != null; m = m.getParentMember() ) {
      if ( m.isAll() ) {
        continue;
      }
      RolapLevel level = m.getLevel();
      RolapStar.Column column = ( level instanceof RolapCubeLevel rcl ) ? rcl.getBaseStarKeyColumn( baseCube ) : null;
      if ( column != null ) {
        // Pure column node, or its aggregate substitution when an aggStar is threaded; empty ->
        // column-less / agg-unresolvable: nothing to render.
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> node =
            dialectFreeColumnNode( aggStar, column );
        if ( node.isEmpty() ) {
          return java.util.Optional.empty();
        }
        colExprs.add( node.get() );
      } else {
        if ( aggStar != null ) {
          // No star key to agg-substitute (see the single-value pure path) — string/recorder path.
          return java.util.Optional.empty();
        }
        // Shared / non-cube level (no star key): constrain on the level KEY expression, exactly like
        // the single-value pure path — the key already lives in the plain level FROM.
        if ( level.getKeyExp() == null ) {
          return java.util.Optional.empty();
        }
        colExprs.add( org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor( level.getKeyExp() ) );
      }
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
   * The per-level {@code col is null} predicates (leaf&#8594;{@code fromLevel}, one per column) over the SAME
   * column expressions {@link #multiValueInPredicatePure} builds — the null-reinclude group of the exclude
   * (NOT-IN) tuple form: {@code (c_leaf is null or .. or c_fromLevel is null)}. The caller {@code OR}s this
   * with {@code not((cols) in (rows))} so a member whose key is null (which a tuple IN never matches) is
   * re-included, exactly as the recorder's {@code generateMultiValueIsNullExprs} does. Returns empty for a
   * column-less star column / key-less level (same guard as {@code multiValueInPredicatePure}), so the caller
   * falls back to the reference.
   */
  private static java.util.Optional<List<org.eclipse.daanse.sql.statement.api.expression.Predicate>>
      isNullExprsPure( RolapCube baseCube, AggStar aggStar, List<RolapMember> members, RolapLevel fromLevel ) {
    List<org.eclipse.daanse.sql.statement.api.expression.Predicate> isNulls = new ArrayList<>();
    for ( RolapMember m = members.get( 0 ); m != null; m = m.getParentMember() ) {
      if ( m.isAll() ) {
        continue;
      }
      RolapLevel level = m.getLevel();
      RolapStar.Column column = ( level instanceof RolapCubeLevel rcl ) ? rcl.getBaseStarKeyColumn( baseCube ) : null;
      org.eclipse.daanse.sql.statement.api.expression.SqlExpression colExpr;
      if ( column != null ) {
        // Same column-node rule as multiValueInPredicatePure (agg-aware when threaded).
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> node =
            dialectFreeColumnNode( aggStar, column );
        if ( node.isEmpty() ) {
          return java.util.Optional.empty();
        }
        colExpr = node.get();
      } else {
        if ( aggStar != null || level.getKeyExp() == null ) {
          return java.util.Optional.empty();
        }
        colExpr = org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor( level.getKeyExp() );
      }
      isNulls.add( org.eclipse.daanse.sql.statement.api.Predicates.isNull( colExpr ) );
      if ( level == fromLevel ) {
        break;
      }
    }
    if ( isNulls.isEmpty() ) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of( isNulls );
  }

  /**
   * The NULL-PARENT MULTI-LEVEL COMPOUND form of the cross-join member
   * constraint — the shape {@code addMemberConstraint(crossJoin=true)} routes through
   * {@code constrainMultiLevelMembers} because a member's ANCESTOR KEY is the SQL null value
   * (the tuple-IN twin {@link #multiValueInPredicatePure} bails on it). Recorder emission
   * (corpus: NonEmptyTest#testMultiLevelMemberConstraintNullParent /
   * #testMultiLevelMemberConstraintMixedNullNonNullParent):
   * <pre>
   * (((`wa_address2` IS NULL ) and (`warehouse_name`, `wa_address1`) in (('Arnold and Sons', …))))
   * ((cols) in (nonNullRows) or (( `warehouse_fax` IS NULL ) and (cols2) in (rows2)))
   * </pre>
   * — the optional tuple IN over the members WITHOUT null ancestor values, OR-joined with one
   * parenthesised group per null parent (first-encounter order): the parent lineage's per-level
   * {@code col = value} / IS-NULL pieces AND-joined with the children's tuple IN (single-value IN
   * when the children sit on the member level itself). The {@code And}/{@code Or} wraps supply the
   * recorder's group parens and {@code addMemberConstraint}'s outer wrap (a sole group gets a
   * single-operand {@code And} for the outer pair).
   * <p>
   * PURE NODES throughout: the null
   * lineage piece is {@link LevelConstraintGenerator#constrainLevelPredicate} applied to the
   * {@code #null} literal, i.e. {@code Predicates.isNull(col)} — the renderer spells it
   * {@code col is null}, where the recorder's {@code constrainLevel} string spelled
   * {@code ( col IS NULL )}. That spelling delta is the ONLY expected byte divergence from the
   * recorder (semantically neutral; pinned normalized in
   * {@code MemberConstraintCompoundNullParentNodeFormTest}). The
   * multi-value IN itself is {@code Predicates.inTuple} — the per-dialect spelling
   * (incl. the no-multi-value-IN OR-of-AND degradation) lives behind the render boundary, so no
   * {@code Dialect} is threaded.
   * Bails (empty, {@code mcBail}-logged) for: a unique member level / cross-product set (not this
   * branch), no null-parent group (the plain tuple-IN twin owns it), or any unresolvable column.
   */
  public static java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate>
      memberConstraintContributionCompoundNullParent( RolapCube baseCube, List<RolapMember> members,
      boolean restrictMemberTypes ) {
    if ( members.isEmpty() ) {
      return mcBail( "compound-null-parent-empty-set", members );
    }
    RolapMember member = members.get( 0 );
    RolapLevel memberLevel = member.getLevel();
    if ( memberLevel.isUnique() || membersAreCrossProduct( members ) ) {
      // addMemberConstraint's else-branch (factored per-level INs) — not the compound shape.
      return mcBail( "compound-null-parent-not-compound-shape", members );
    }
    RolapStar.Column column = ( memberLevel instanceof RolapCubeLevel rcl )
        ? rcl.getBaseStarKeyColumn( baseCube ) : null;
    if ( column == null ) {
      return mcBail( "compound-null-parent-no-star-key", members );
    }
    RolapMember firstUniqueParent = member;
    for ( ; firstUniqueParent != null && !firstUniqueParent.getLevel().isUnique();
        firstUniqueParent = firstUniqueParent.getParentMember() ) {
      // advance to the first unique parent level, as addMemberConstraint does
    }
    RolapLevel fromLevel = ( firstUniqueParent != null ) ? firstUniqueParent.getLevel() : null;
    // condition1: the tuple IN over the members WITHOUT null ancestor values; the null-parented
    // members land in the side map (parent -> children), in first-encounter order.
    Map<RolapMember, List<RolapMember>> parentChildrenMap = new LinkedHashMap<>();
    java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nonNullTupleIn =
        compoundNonNullMembersTupleIn( baseCube, members, fromLevel, restrictMemberTypes,
            parentChildrenMap );
    if ( nonNullTupleIn == null ) {
      return mcBail( "compound-null-parent-columns-unresolved", members );
    }
    if ( parentChildrenMap.isEmpty() ) {
      // No null parent at all: the plain tuple-IN twin owns this shape (and the recorder emitted it).
      return mcBail( "compound-null-parent-no-null-group", members );
    }
    List<org.eclipse.daanse.sql.statement.api.expression.Predicate> pieces = new ArrayList<>();
    nonNullTupleIn.ifPresent( pieces::add );
    for ( Map.Entry<RolapMember, List<RolapMember>> group : parentChildrenMap.entrySet() ) {
      RolapMember p = group.getKey();
      List<org.eclipse.daanse.sql.statement.api.expression.Predicate> groupParts = new ArrayList<>();
      // The parent lineage: one constrainLevelPredicate piece per level p..fromLevel (All skipped).
      // The recorder gated the "#null" literal OUT of the node path (its byte contract was the
      // constrainLevel "( col IS NULL )" string); the node twin routes it THROUGH — the same
      // column resolution maps the null literal to Predicates.isNull (the sole spelling delta).
      for ( RolapMember gp = p; gp != null; gp = gp.getParentMember() ) {
        if ( gp.isAll() ) {
          continue;
        }
        RolapLevel level = gp.getLevel();
        String levelValue = getColumnValue( level.getNameExp() != null
            ? gp.getName() : gp.getKey(), level.getDatatype() );
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> levelPred =
            LevelConstraintGenerator.constrainLevelPredicate(
                level, baseCube, null, new String[] { levelValue }, false );
        if ( levelPred.isEmpty() ) {
          return mcBail( "compound-null-parent-lineage-unresolved", members );
        }
        groupParts.add( levelPred.get() );
        if ( gp.getLevel() == fromLevel ) {
          break;
        }
      }
      // The children of this null parent: multi-level -> tuple IN down to the parent's child level;
      // children on the member level itself -> the single-value IN parts (unwrapped, " and "-joined
      // by the group And exactly like the recorder's appended string).
      List<RolapMember> children = group.getValue();
      if ( !children.isEmpty() ) {
        RolapLevel childrenLevel = (RolapLevel) p.getLevel().getChildLevel();
        if ( childrenLevel != memberLevel ) {
          java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> childIn =
              multiValueInPredicatePure( baseCube, null, children, childrenLevel );
          if ( childIn.isEmpty() ) {
            return mcBail( "compound-null-parent-children-unresolved", members );
          }
          groupParts.add( childIn.get() );
        } else {
          java.util.Optional<List<org.eclipse.daanse.sql.statement.api.expression.Predicate>> childParts =
              generateSingleValueInPredicatePure( baseCube, children, childrenLevel,
                  restrictMemberTypes, false, true );
          if ( childParts.isEmpty() ) {
            return mcBail( "compound-null-parent-children-unresolved", members );
          }
          groupParts.addAll( childParts.get() );
        }
      }
      if ( groupParts.isEmpty() ) {
        return mcBail( "compound-null-parent-empty-group", members );
      }
      pieces.add( org.eclipse.daanse.sql.statement.api.Predicates.and( groupParts ) );
    }
    // A sole group needs the outer addMemberConstraint pair on top of its own parens (the
    // single-operand And); several pieces OR-join flat — the Or's parens ARE the outer pair.
    org.eclipse.daanse.sql.statement.api.expression.Predicate compound = pieces.size() == 1
        ? org.eclipse.daanse.sql.statement.api.Predicates.and( java.util.List.of( pieces.get( 0 ) ) )
        : org.eclipse.daanse.sql.statement.api.Predicates.or( pieces );
    return java.util.Optional.of(
        new org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate(
            column.getTable(), compound ) );
  }

  /**
   * The {@code condition1} piece of the compound form — pure twin of {@link #generateMultiValueInExpr}:
   * the tuple IN over the members whose lineage values (leaf..{@code fromLevel}) are all non-null; a
   * member hitting a null value is recorded into {@code parentWithNullToChildrenMap} (null parent →
   * children, first-encounter order — the member itself when its own key is null, with no child entry)
   * and skipped, exactly like the recorder's side map. Returns a present tuple IN when some row
   * survived, empty when none did, and {@code null} (bail) for an unresolvable column / non-uniform
   * level shape the string path would still render.
   */
  private static java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate>
      compoundNonNullMembersTupleIn( RolapCube baseCube, List<RolapMember> members,
      RolapLevel fromLevel, boolean restrictMemberTypes,
      Map<RolapMember, List<RolapMember>> parentWithNullToChildrenMap ) {
    List<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> colExprs = new ArrayList<>();
    for ( RolapMember m = members.get( 0 ); m != null; m = m.getParentMember() ) {
      if ( m.isAll() ) {
        continue;
      }
      RolapLevel level = m.getLevel();
      RolapStar.Column column = ( level instanceof RolapCubeLevel rcl )
          ? rcl.getBaseStarKeyColumn( baseCube ) : null;
      if ( column == null ) {
        return null; // no star key: getColumnString's name-exp fallback is not modelled — bail
      }
      java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.SqlExpression> node =
          dialectFreeColumnNode( null, column );
      if ( node.isEmpty() ) {
        return null;
      }
      colExprs.add( node.get() );
      if ( level == fromLevel ) {
        break;
      }
    }
    if ( colExprs.isEmpty() ) {
      return null;
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
      boolean containsNull = false;
      for ( RolapMember p = m; p != null; p = p.getParentMember() ) {
        if ( p.isAll() ) {
          continue;
        }
        RolapLevel level = p.getLevel();
        String value = getColumnValue( p.getKey(), level.getDatatype() );
        if ( RolapUtil.mdxNullLiteral().equalsIgnoreCase( value ) ) {
          List<RolapMember> childrenList =
              parentWithNullToChildrenMap.computeIfAbsent( p, k -> new ArrayList<>() );
          if ( m != p ) {
            childrenList.add( m );
          }
          containsNull = true;
          break;
        }
        row.add( org.eclipse.daanse.sql.statement.api.Expressions.literal( value, level.getDatatype() ) );
        if ( p.getLevel() == fromLevel ) {
          break;
        }
      }
      if ( !containsNull ) {
        if ( row.size() != colExprs.size() ) {
          return null; // non-uniform level shape
        }
        valueRows.add( row );
      }
    }
    return valueRows.isEmpty() ? java.util.Optional.empty()
        : java.util.Optional.of(
            org.eclipse.daanse.sql.statement.api.Predicates.inTuple( colExprs, valueRows ) );
  }

  /**
   * True if {@code members} form a full RECTANGLE over their per-level key values — the distinct values at each
   * level (leaf → {@code fromLevel}), multiplied, equal the member count. Then the per-level "IN per level" AND
   * represents EXACTLY the member set (not an over-broad cross-product) AND matches the factored form
   * (e.g. two cities in the same state → "city IN (..) AND state = X"). Non-rectangles need the tuple IN.
   */
  static boolean membersFormRectangle( List<RolapMember> members, RolapLevel fromLevel ) {
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

}
