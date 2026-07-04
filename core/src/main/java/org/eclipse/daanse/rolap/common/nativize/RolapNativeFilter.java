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
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.evaluator.NativeEvaluator;
import org.eclipse.daanse.olap.api.function.FunctionDefinition;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.MdxVisitorImpl;
import org.eclipse.daanse.rolap.common.SqlTupleReader;
import org.eclipse.daanse.rolap.common.TupleReader;
import org.eclipse.daanse.rolap.common.TupleReader.MemberBuilder;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.catalog.RolapCatalogReader;
import org.eclipse.daanse.rolap.common.constraint.CalculatedMemberExpander;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.member.MemberReader;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.QueryTape;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
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

    /** Records the filter condition on the fork: the compiled filter predicate as a HAVING
     *  conjunct, on top of the inherited {@link SetConstraint} context. */
    @Override
    public QueryTape addConstraintOps( Dialect dialect, QueryRecorder.Fork fork, RolapCube baseCube,
        AggStar aggStar ) {
      // Use aggregate table to generate filter condition
      RolapNativeSql sql = new RolapNativeSql(
          NativeSqlContext.ofRecorder( fork, getEvaluator().getCatalogReader().getContext().getDialect() ),
          aggStar, getEvaluator(), args[0].getLevel() );
      org.eclipse.daanse.sql.statement.api.expression.Predicate filterPredicate =
          sql.generateFilterPredicate( filterExpr );
      if ( filterPredicate != null ) {
        // Always attach the provenance comment (it is only RENDERED when comments are enabled — never part
        // of the executed SQL — so it need not be gated on the comment log level here).
        String exprText = String.valueOf( filterExpr );
        if ( exprText.length() > 80 ) {
          exprText = exprText.substring( 0, 77 ) + "...";
        }
        fork.addHaving( filterPredicate, "native filter " + exprText );
      }

      if ( getEvaluator().isNonEmpty() || isJoinRequired() ) {
        // only apply context constraint if non empty, or
        // if a join is required to fulfill the filter condition
        super.addConstraintOps( dialect, fork, baseCube, aggStar );
      }
      return fork.ops();
    }

    /**
     * The builder counterpart of {@code addConstraint} for a SIMPLE measure-comparison filter
     * ({@code [Measures].<stored measure> <op> <numeric literal>}): the inherited {@link SetConstraint}
     * contribution plus a {@code nativeHaving} predicate {@code ((agg(measure) op value))}. The measure node
     * is built exactly as {@code AbstractQuerySpec.addMeasure}, so it matches
     * {@code generateFilterCondition}'s rendering; the double parens reproduce the compiler's infix
     * {@code (a op b)} wrapped by the filter. Returns {@link java.util.Optional#empty()} (recorder path) for any non-simple
     * filter (regex / boolean tree / calc), a non-stored-measure / non-literal operand, or a composite/
     * non-node aggregator.
     */
    @Override
    public java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> toContribution(
        RolapCube baseCube, AggStar aggStar ) {
      // A string regex filter (`member.Name|Caption MATCHES pattern`), base-fact only — the inherited
      // context plus a Regexp nativeHaving. The dialect renders the whole fragment (null-guard + UPPER +
      // operator) at render time; a divergence (agg table, cross-dimension, dialect without regex) falls
      // back via the guard.
      if ( aggStar == null ) {
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> regexHaving =
            tryRegexHaving();
        if ( regexHaving.isPresent() ) {
          // Mirror addConstraint's gate: apply the inherited context (the fact join for the slicer/measures)
          // ONLY when the filter is non-empty or join-required. A plain string filter (a Name/Caption regex,
          // not non-empty, no measure) ignores the context — the recorded path adds no fact join / no slicer
          // WHERE — so carrying it would over-restrict (e.g. join to the fact + `time.the_year=1997`) and
          // diverge.
          if ( getEvaluator().isNonEmpty() || isJoinRequired() ) {
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> rbase =
                super.toContribution( baseCube, aggStar );
            if ( rbase.isEmpty() ) {
              return bail( "filter-regex-base-context-empty" );
            }
            org.eclipse.daanse.rolap.common.sql.ConstraintContribution rc = rbase.get();
            // Carry the base's factJoinRequired — the re-wrap must not drop it, or the mapper's
            // same-dimension gate skips the fact join the HAVING references.
            return java.util.Optional.of( new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
                rc.where(), rc.joinTables(), rc.orderedPredicates(), rc.memberKeyGroup(), rc.nativeOrder(),
                regexHaving ).withFactJoinRequired( rc.factJoinRequired() ) );
          }
          // No context: just the regex HAVING over the plain level-members snowflake (no fact join).
          return java.util.Optional.of( new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
              java.util.Optional.empty(), java.util.List.of(), java.util.List.of(),
              java.util.Optional.empty(), java.util.Optional.empty(), regexHaving ) );
        }
      }
      if ( !( filterExpr instanceof org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl call )
          || call.getArgCount() != 2 ) {
        return bail( "filter-non-simple-shape" );
      }
      org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator op =
          mapComparison( call.getOperationAtom().name() );
      if ( op == null
          || !( call.getArg( 0 ) instanceof MemberExpression me
              && me.getMember() instanceof org.eclipse.daanse.rolap.element.RolapStoredMeasure storedMeasure
              && storedMeasure.getStarMeasure()
                  instanceof org.eclipse.daanse.rolap.common.star.RolapStar.Measure starMeasure )
          || !( call.getArg( 1 ) instanceof org.eclipse.daanse.olap.api.query.component.Literal<?> lit ) ) {
        return bail( "filter-non-measure-comparison" );
      }
      java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution> base =
          super.toContribution( baseCube, aggStar );
      if ( base.isEmpty() ) {
        return bail( "filter-base-context-empty" );
      }
      org.eclipse.daanse.sql.statement.api.expression.SqlExpression innerNode =
          starMeasure.getExpression() == null
              ? org.eclipse.daanse.sql.statement.api.Expressions.star()
              : ( starMeasure.getExpression() instanceof org.eclipse.daanse.rolap.element.RolapColumn
                  ? org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(
                      starMeasure.getExpression() )
                  : org.eclipse.daanse.sql.statement.api.Expressions.rawVariant(
                      org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.sqlVariants(
                          starMeasure.getExpression() ) ) );
      org.eclipse.daanse.sql.statement.api.expression.SqlExpression measureNode =
          ( starMeasure.getAggregator()
                  instanceof org.eclipse.daanse.rolap.aggregator.AbstractAggregator agg )
              ? agg.getExpression( innerNode ) : null;
      if ( measureNode == null ) {
        // Composite / non-node aggregator: the measure has no builder node form.
        return bail( "filter-measure-no-node" );
      }
      org.eclipse.daanse.sql.statement.api.expression.Predicate cmp =
          org.eclipse.daanse.sql.statement.api.Predicates.comparison( measureNode, op,
              org.eclipse.daanse.sql.statement.api.Expressions.literal( lit.getValue(),
                  org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype.NUMERIC ) );
      // Single paren = generateFilterCondition's infix `(a op b)` for a bare comparison filter.
      // A condition the MDX wrapped in extra parens renders with more parens than this node produces, so it
      // diverges and the guard falls back (a documented limitation of the node form vs the string compiler).
      org.eclipse.daanse.sql.statement.api.expression.Predicate having =
          org.eclipse.daanse.sql.statement.api.Predicates.and( java.util.List.of( cmp ) );
      org.eclipse.daanse.rolap.common.sql.ConstraintContribution c = base.get();
      // Carry the base's factJoinRequired — dropping it in this re-wrap made the mapper's same-dimension
      // gate skip the fact join while the measure HAVING (sum(fact.col)) references the fact (the
      // AccessControlTest store-role blockers: role WHEREs present, `join sales_fact_1997` missing).
      return java.util.Optional.of( new org.eclipse.daanse.rolap.common.sql.ConstraintContribution(
          c.where(), c.joinTables(), c.orderedPredicates(), c.memberKeyGroup(), c.nativeOrder(),
          java.util.Optional.of( having ) ).withFactJoinRequired( c.factJoinRequired() ) );
    }

    /**
     * A string regex filter as a {@link org.eclipse.daanse.sql.statement.api.expression.Predicate.Regexp}
     * nativeHaving, mirroring {@code RolapNativeSql.MatchingSqlCompiler}: {@code [NOT](
     * <filteredLevel>.CurrentMember.Name|Caption MATCHES <pattern>)}. The source is the level's
     * name/caption/key expression (same priority as the compiler); the pattern is the evaluated arg. Returns
     * empty for any other shape (boolean tree, calc, cross-dimension) — the guard then falls back.
     */
    private java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> tryRegexHaving() {
      org.eclipse.daanse.olap.api.query.component.Expression expr = filterExpr;
      boolean negated = false;
      if ( expr instanceof org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl notCall
          && notCall.getArgCount() == 1
          && "NOT".equalsIgnoreCase( notCall.getOperationAtom().name() ) ) {
        negated = true;
        expr = notCall.getArg( 0 );
      }
      if ( !( expr instanceof org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl call )
          || call.getArgCount() != 2
          || !"MATCHES".equals( call.getOperationAtom().name() ) ) {
        return java.util.Optional.empty();
      }
      if ( !( call.getArg( 0 ) instanceof org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl nameCall )
          || nameCall.getArgCount() != 1 ) {
        return java.util.Optional.empty();
      }
      boolean useCaption;
      if ( "Name".equals( nameCall.getOperationAtom().name() ) ) {
        useCaption = false;
      } else if ( "Caption".equals( nameCall.getOperationAtom().name() ) ) {
        useCaption = true;
      } else {
        return java.util.Optional.empty();
      }
      if ( !( nameCall.getArg( 0 ) instanceof org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl cmCall )
          || cmCall.getArgCount() != 1
          || !"CurrentMember".equals( cmCall.getOperationAtom().name() ) ) {
        return java.util.Optional.empty();
      }
      if ( !( args[ 0 ].getLevel() instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel level ) ) {
        return java.util.Optional.empty();
      }
      org.eclipse.daanse.olap.api.sql.SqlExpression nameExp = useCaption
          ? ( level.getCaptionExp() != null ? level.getCaptionExp()
              : ( level.getNameExp() != null ? level.getNameExp() : level.getKeyExp() ) )
          : ( level.getNameExp() != null ? level.getNameExp() : level.getKeyExp() );
      if ( nameExp == null ) {
        return java.util.Optional.empty();
      }
      org.eclipse.daanse.sql.statement.api.expression.SqlExpression sourceCol =
          org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor( nameExp );
      if ( sourceCol == null ) {
        return java.util.Optional.empty();
      }
      String pattern = String.valueOf( getEvaluator().getCachedResult(
          new org.eclipse.daanse.olap.common.ExpCacheDescriptorImpl( call.getArg( 1 ), getEvaluator() ) ) );
      return java.util.Optional.of(
          new org.eclipse.daanse.sql.statement.api.expression.Predicate.Regexp( sourceCol, pattern, negated ) );
    }

    private static org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator mapComparison(
        String name ) {
      if ( ">".equals( name ) ) {
        return org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.GT;
      } else if ( "<".equals( name ) ) {
        return org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.LT;
      } else if ( ">=".equals( name ) ) {
        return org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.GE;
      } else if ( "<=".equals( name ) ) {
        return org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.LE;
      } else if ( "=".equals( name ) ) {
        return org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ;
      } else if ( "<>".equals( name ) ) {
        return org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.NE;
      }
      return null;
    }

    /** The feasibility verdict is computed on a throwaway {@link QueryRecorder}
     *  (fork/append) — the SAME {@code addConstraintOps} code path the
     *  executing query takes — so the probe cannot diverge from execution. */
    public boolean isSupported( Context<?> context ) {
      RolapEvaluator evaluator = this.getEvaluator();
      QueryRecorder testQuery = QueryRecorder.newQuery( context, "testQuery" );
      SqlTupleReader sqlTupleReader = new SqlTupleReader( this );

      Role role = evaluator.getCatalogReader().getRole();
      RolapCatalogReader reader = new RolapCatalogReader( context,role, (RolapCatalog) evaluator.getCatalogReader().getCatalog() );

      for ( CrossJoinArg arg : args ) {
        addLevel( sqlTupleReader, reader, arg );
      }

      RolapCube cube = (RolapCube) evaluator.getCube();
      AggStar aggStar = sqlTupleReader.chooseAggStar( this, evaluator, cube,
          context.getConfigValue(ConfigConstants.USE_AGGREGATES, ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE ,Boolean.class) );
      QueryRecorder.Fork fork = testQuery.fork();
      testQuery.append( fork, this.addConstraintOps( context.getDialect(), fork, cube, aggStar ) );
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
    // Scratch context: this RolapNativeSql only generates the filter-condition node to test
    // convertibility; the node is discarded (the real HAVING is regenerated against the executing
    // query in FilterConstraint.addConstraintOps). No recorder is needed.
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
