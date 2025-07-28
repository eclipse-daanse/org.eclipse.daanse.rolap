/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2021 Hitachi Vantara and others
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

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.daanse.mdx.model.api.expression.operation.InternalOperationAtom;
import org.eclipse.daanse.mdx.model.api.expression.operation.OperationAtom;
import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.Execution;
import org.eclipse.daanse.olap.api.IAggregationManager;
import org.eclipse.daanse.olap.api.Locus;
import org.eclipse.daanse.olap.api.NameSegment;
import org.eclipse.daanse.olap.api.Parameter;
import org.eclipse.daanse.olap.api.access.HierarchyAccess;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.compiler.ParameterSlot;
import org.eclipse.daanse.olap.api.calc.todo.TupleCursor;
import org.eclipse.daanse.olap.api.calc.todo.TupleIterable;
import org.eclipse.daanse.olap.api.calc.todo.TupleIterator;
import org.eclipse.daanse.olap.api.calc.todo.TupleIteratorCalc;
import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.DimensionType;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.NamedSet;
import org.eclipse.daanse.olap.api.exception.CellRequestQuantumExceededException;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.formatter.CellFormatter;
import org.eclipse.daanse.olap.api.function.FunctionMetaData;
import org.eclipse.daanse.olap.api.query.component.DimensionExpression;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.HierarchyExpression;
import org.eclipse.daanse.olap.api.query.component.MemberExpression;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.query.component.QueryAxis;
import org.eclipse.daanse.olap.api.query.component.QueryComponent;
import org.eclipse.daanse.olap.api.query.component.ResolvedFunCall;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.type.NumericType;
import org.eclipse.daanse.olap.api.type.ScalarType;
import org.eclipse.daanse.olap.api.type.SetType;
import org.eclipse.daanse.olap.calc.base.cache.CacheCalc;
import org.eclipse.daanse.olap.calc.base.nested.AbstractProfilingNestedUnknownCalc;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.DelegatingTupleList;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.ListTupleList;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.TupleCollections;
import org.eclipse.daanse.olap.calc.base.value.CurrentValueUnknownCalc;
import org.eclipse.daanse.olap.common.ExpCacheDescriptorImpl;
import org.eclipse.daanse.olap.common.ResourceLimitExceededException;
import org.eclipse.daanse.olap.common.ResultBase;
import org.eclipse.daanse.olap.common.ResultLimitExceededException;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.fun.MondrianEvaluationException;
import org.eclipse.daanse.olap.fun.sort.Sorter;
import org.eclipse.daanse.olap.function.core.FunctionMetaDataR;
import org.eclipse.daanse.olap.function.core.FunctionParameterR;
import org.eclipse.daanse.olap.function.def.aggregate.AbstractAggregateFunDef;
import org.eclipse.daanse.olap.function.def.aggregate.AggregateCalc;
import org.eclipse.daanse.olap.key.CellKey;
import org.eclipse.daanse.olap.query.component.MdxVisitorImpl;
import org.eclipse.daanse.olap.query.component.ResolvedFunCallImpl;
import  org.eclipse.daanse.olap.server.LocusImpl;
import  org.eclipse.daanse.olap.util.CancellationChecker;
import  org.eclipse.daanse.olap.util.Format;
import org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.element.CompoundSlicerRolapMember;
import org.eclipse.daanse.rolap.element.RolapBaseCubeMeasure;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapMeasure;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.eclipse.daanse.rolap.element.VisualTotalMember;
import org.eclipse.daanse.rolap.util.ObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RolapResult is the result of running a query.
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public class RolapResult extends ResultBase {

    static final Logger LOGGER = LoggerFactory.getLogger( RolapResult.class );
    public static final String MONDRIAN_EXCEPTION_IN_EXECUTE_STRIPE = "Mondrian: exception in executeStripe.";

  private RolapEvaluator evaluator;
  RolapEvaluator slicerEvaluator;
  private final CellKey point;

  private CellInfoContainer cellInfos;
  private FastBatchingCellReader batchingReader;
  private final CellReader aggregatingReader;
  private Modulos modulos = null;
  private final int maxEvalDepth;
  private final Map<Integer, Boolean> positionsHighCardinality = new HashMap<>();
  private final Map<Integer, TupleCursor> positionsIterators = new HashMap<>();
  private final Map<Integer, Integer> positionsIndexes = new HashMap<>();
  private final Map<Integer, List<List<Member>>> positionsCurrent = new HashMap<>();

  /**
   * Creates a RolapResult.
   *
   * @param execution
   *          Execution of a statement
   * @param execute
   *          Whether to execute the query
   */
  public RolapResult( final Execution execution, boolean execute ) {
    super( execution, null );
    this.maxEvalDepth = query.getConnection().getContext().getConfigValue(ConfigConstants.MAX_EVAL_DEPTH, ConfigConstants.MAX_EVAL_DEPTH_DEFAULT_VALUE, Integer.class);
    int solveOrder = execution
        .getMondrianStatement().getMondrianConnection()
        .getContext().getConfigValue(ConfigConstants.COMPOUND_SLICER_MEMBER_SOLVE_ORDER, ConfigConstants.COMPOUND_SLICER_MEMBER_SOLVE_ORDER_DEFAULT_VALUE, Integer.class);
    this.point = CellKey.Generator.newCellKey( axes.length );
    AbstractBasicContext abc = (AbstractBasicContext) execution.getMondrianStatement().getMondrianConnection().getContext();
    final IAggregationManager aggMgr = abc.getAggregationManager();
    this.aggregatingReader = ((AggregationManager)aggMgr).getCacheCellReader();
    final int expDeps = execution.getMondrianStatement().getMondrianConnection().getContext().getConfigValue(ConfigConstants.TEST_EXP_DEPENDENCIES, ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE, Integer.class);
    if ( expDeps > 0 ) {
      this.evaluator = new RolapDependencyTestingEvaluator( this, expDeps );
    } else {
      final RolapEvaluatorRoot root = new RolapResultEvaluatorRoot( this );
      if ( statement.getProfileHandler() != null ) {
        this.evaluator = new RolapProfilingEvaluator( root );
      } else {
        this.evaluator = new RolapEvaluator( root );
      }
    }
    RolapCube cube = (RolapCube) query.getCube();

    this.batchingReader = new FastBatchingCellReader( execution, cube, aggMgr );

    this.cellInfos = ( query.getAxes().length > 4 ) ? new CellInfoMap( point ) : new CellInfoPool( query.getAxes().length );

    if ( !execute ) {
      return;
    }

    boolean normalExecution = true;
    try {
      // This call to clear the cube's cache only has an
      // effect if caching has been disabled, otherwise
      // nothing happens.
      // Clear the local cache before a query has run
      cube.clearCachedAggregations();

      /////////////////////////////////////////////////////////////////
      //
      // Evaluation Algorithm
      //
      // There are three basic steps to the evaluation algorithm:
      // 1) Determine all Members for each axis but do not save
      // information (do not build the RolapAxis),
      // 2) Save all Members for each axis (build RolapAxis).
      // 3) Evaluate and store each Cell determined by the Members
      // of the axes.
      // Step 1 converges on the stable set of Members pre axis.
      // Steps 1 and 2 make sure that the data has been loaded.
      //
      // More detail follows.
      //
      // Explicit and Implicit Members:
      // A Member is said to be 'explicit' if it appears on one of
      // the Axes (one of the RolapAxis Position List of Members).
      // A Member is 'implicit' if it is in the query but does not
      // end up on any Axes (its usage, for example, is in a function).
      // When for a Dimension none of its Members are explicit in the
      // query, then the default Member is used which is like putting
      // the Member in the Slicer.
      //
      // Special Dimensions:
      // There are 2 special dimensions.
      // The first is the Time dimension. If in a schema there is
      // no ALL Member, then Whatever happens to be the default
      // Member is used if Time Members are not explicitly set
      // in the query.
      // The second is the Measures dimension. This dimension
      // NEVER has an ALL Member. A cube's default Measure is set
      // by convention - its simply the first Measure defined in the
      // cube.
      //
      // First a RolapEvaluator is created. During its creation,
      // it gets a Member from each Hierarchy. Each Member is the
      // default Member of the Hierarchy. For most Hierarchies this
      // Member is the ALL Member, but there are cases where 1)
      // a Hierarchy does not have an ALL Member or 2) the Hierarchy
      // has an ALL Member but that Member is not the default Member.
      // In these cases, the default Member is still used, but its
      // use can cause evaluation issues (seemingly strange evaluation
      // results).
      //
      // Next, load all root Members for Hierarchies that have no ALL
      // Member and load ALL Members that are not the default Member.
      //
      // Determine the Members of the Slicer axis (Step 1 above). Any
      // Members found are added to the AxisMember object. If one of these
      // Members happens to be a Measure, then the Slicer is explicitly
      // specifying the query's Measure and this should be put into the
      // evaluator's context (replacing the default Measure which just
      // happens to be the first Measure defined in the cube). Other
      // Members found in the AxisMember object are also placed into the
      // evaluator's context since these also are explicitly specified.
      // Also, any other Members in the AxisMember object which have the
      // same Hierarchy as Members in the list of root Members for
      // Hierarchies that have no ALL Member, replace those Members - they
      // Slicer has explicitly determined which ones to use. The
      // AxisMember object is now cleared.
      // The Slicer does not depend upon the other Axes, but the other
      // Axes depend upon both the Slicer and each other.
      //
      // The AxisMember object also checks if the number of Members
      // exceeds the ResultLimit property throwing a
      // TotalMembersLimitExceeded Exception if it does.
      //
      // For all non-Slicer axes, the Members are determined (Step 1
      // above). If a Measure is found in the AxisMember, then an
      // Axis is explicitly specifying a Measure.
      // If any Members in the AxisMember object have the same Hierarchy
      // as a Member in the set of root Members for Hierarchies that have
      // no ALL Member, then replace those root Members with the Member
      // from the AxisMember object. In this case, again, a Member
      // was explicitly specified in an Axis. If this replacement
      // occurs, then one must redo this step with the new Members.
      //
      // Now Step 3 above is done. First to the Slicer Axis and then
      // to the other Axes. Here the Axes are actually generated.
      // If a Member of an Axis is an Calculated Member (and the
      // Calculated Member is not a Member of the Measure Hierarchy),
      // then find the Dimension associated with the Calculated
      // Member and remove Members with the same Dimension in the set of
      // root Members for Hierarchies that have no ALL Member.
      // This is done because via the Calculated Member the Member
      // was implicitly specified in the query. If this removal occurs,
      // then the Axes must be re-evaluated repeating Step 3.
      //
      /////////////////////////////////////////////////////////////////

      // The AxisMember object is used to hold Members that are found
      // during Step 1 when the Axes are determined.
      final AxisMemberList axisMembers = new AxisMemberList();

      // list of ALL Members that are not default Members
      final List<Member> nonDefaultAllMembers = new ArrayList<>();

      // List of Members of Hierarchies that do not have an ALL Member
      List<List<Member>> nonAllMembers = new ArrayList<>();

      // List of Measures
      final List<Member> measureMembers = new ArrayList<>();

      /////////////////////////////////////////////////////////////////
      // Determine Subcube
      //
      HashMap<Hierarchy, HashMap<Member, Member>> subcubeHierarchies = new HashMap<>();

      for(Map.Entry<Hierarchy, Calc> entry : query.getSubcubeHierarchyCalcs().entrySet()) {
        Hierarchy hierarchy = entry.getKey();
        List<? extends org.eclipse.daanse.olap.api.element.Level> levels = hierarchy.getLevels();
        org.eclipse.daanse.olap.api.element.Level lastLevel = levels.getLast();

        Calc calc = entry.getValue();

        HashMap<Member, Member> subcubeHierarchyMembers = new HashMap<>();

        org.eclipse.daanse.olap.api.type.Type memberType1 =
                new org.eclipse.daanse.olap.api.type.MemberType(
                        hierarchy.getDimension(),
                        hierarchy,
                        null,
                        null);
        SetType setType = new SetType(memberType1);
        org.eclipse.daanse.olap.api.calc.todo.TupleListCalc tupleListCalc =
                new org.eclipse.daanse.olap.calc.base.type.tuplebase.AbstractProfilingNestedTupleListCalc(
                        setType, new Calc[0])
                {
                  @Override
				public TupleList evaluate(
                          Evaluator evaluator)
                  {
                    ArrayList<Member> children = new ArrayList<>();
                    Member expandingMember = ((RolapEvaluator) evaluator).getExpanding();

                    if(subcubeHierarchyMembers.containsKey(expandingMember)) {
                      for(Map.Entry<Member, Member> memberEntry : subcubeHierarchyMembers.entrySet()) {
                        Member childMember = memberEntry.getValue();
                        if(childMember.getParentUniqueName() != null &&
                                childMember.getParentUniqueName().equals(expandingMember.getUniqueName())) {
                          children.add(childMember);
                        }
                      }
                    }

                    return new org.eclipse.daanse.olap.calc.base.type.tuplebase.UnaryTupleList(children);
                  }

                  @Override
				public boolean dependsOn(Hierarchy hierarchy) {
                    return true;
                  }
                };
        final org.eclipse.daanse.olap.api.type.NumericType returnType =NumericType.INSTANCE;
        final Calc partialCalc =
                new RolapHierarchy.LimitedRollupAggregateCalc(returnType, tupleListCalc);

        OperationAtom internalOperationAtom = new InternalOperationAtom("$x");

        FunctionMetaData functionMetaData = new FunctionMetaDataR(internalOperationAtom, "x",
                DataType.NUMERIC, new FunctionParameterR[] { });
        Expression partialExp =
                new ResolvedFunCallImpl(
                        new org.eclipse.daanse.olap.function.def.AbstractFunctionDefinition(functionMetaData) {
                          @Override
						public Calc compileCall(
                                  ResolvedFunCall call, org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler compiler)
                          {
                            return partialCalc;
                          }

                          @Override
						public void unparse(Expression[] args, PrintWriter pw) {
                            pw.print("$RollupAccessibleChildren()");
                          }
                        },
                        new Expression[0],
                        returnType);

        final TupleIterable iterable =  ( (TupleIteratorCalc<?>) calc ).evaluate( evaluator );
        TupleCursor cursor;
        if ( iterable instanceof TupleList list ) {
          cursor = list.tupleCursor();
        } else {
          // Iterable
          cursor = iterable.tupleCursor();
        }
        HierarchyAccess hierarchyAccess = org.eclipse.daanse.olap.common.RoleImpl.createAllAccess(hierarchy);
        int currentIteration = 0;
        while ( cursor.forward() ) {
          CancellationChecker.checkCancelOrTimeout( currentIteration++, execution );
          Member member = cursor.member(0);
          //must be not isLeaf()
          if(member.getLevel().getDepth() < lastLevel.getDepth()) {
            if(member instanceof org.eclipse.daanse.rolap.element.RolapHierarchy.LimitedRollupMember limitedRollupMember){
              //it could happen if there is Roles
              member = limitedRollupMember.getSourceMember();
            }
            member = new org.eclipse.daanse.rolap.element.RolapHierarchy.LimitedRollupMember(
                    (RolapCubeMember)member,
                    partialExp,
                    hierarchyAccess
            );
          }
          subcubeHierarchyMembers.put(member, member);
        }
        subcubeHierarchies.put(hierarchy, subcubeHierarchyMembers);

      }
      query.setSubcubeHierarchies(subcubeHierarchies);

      query.replaceSubcubeMembers();
      query.resolve();

      //Create evaluator once more. It collected default members before subcube calculation.
      if ( expDeps > 0 ) {
        this.evaluator = new RolapDependencyTestingEvaluator( this, expDeps );
      } else {
        final RolapEvaluatorRoot root = new RolapResultEvaluatorRoot( this );
        if ( statement.getProfileHandler() != null ) {
          this.evaluator = new RolapProfilingEvaluator( root );
        } else {
          this.evaluator = new RolapEvaluator( root );
        }
      }

      // load all root Members for Hierarchies that have no ALL
      // Member and load ALL Members that are not the default Member.
      // Also, all Measures are are gathered.
      loadSpecialMembers( nonDefaultAllMembers, nonAllMembers, measureMembers );

      // clear evaluation cache
      query.clearEvalCache();

      // Save, may be needed by some Expression Calc's
      query.putEvalCache( "ALL_MEMBER_LIST", nonDefaultAllMembers );

      final List<List<Member>> emptyNonAllMembers = Collections.emptyList();

      // Initial evaluator, to execute slicer.
      // Used by named sets in slicer
      slicerEvaluator = evaluator.push();

      /////////////////////////////////////////////////////////////////
      // Determine Slicer
      //
      axisMembers.setSlicer( true );
      loadMembers( emptyNonAllMembers, evaluator, query.getSlicerAxis(), query.getSlicerCalc(), axisMembers );
      axisMembers.setSlicer( false );

      // Save unadulterated context for the next time we need to evaluate
      // the slicer.
      final RolapEvaluator savedEvaluator = evaluator.push();

      if ( !axisMembers.isEmpty() ) {
        evaluator.setSlicerContext( axisMembers.getMembers(), axisMembers.getMembersByHierarchy() );
        for ( Hierarchy h : axisMembers.getMembersByHierarchy().keySet() ) {
          if ( h.getDimension().isMeasures() ) {
            // A Measure was explicitly declared in the
            // Slicer, don't need to worry about Measures
            // for this query.
            measureMembers.clear();
            break;
          }
        }
        replaceNonAllMembers( nonAllMembers, axisMembers );
        axisMembers.clearMembers();
      }

      // Save evaluator that has slicer as its context.
      slicerEvaluator = evaluator.push();

      /////////////////////////////////////////////////////////////////
      // Execute Slicer
      //
      Axis savedSlicerAxis;
      RolapEvaluator internalSlicerEvaluator;
      do {
        TupleIterable tupleIterable =
            evalExecute( nonAllMembers, nonAllMembers.size() - 1, savedEvaluator, query.getSlicerAxis(),
                query.getSlicerCalc() );
        // Materialize the iterable as a list. Although it may take
        // memory, we need the first member below, and besides, slicer
        // axes are generally small.
        TupleList tupleList = TupleCollections.materialize( tupleIterable, true );

        this.slicerAxis = new RolapAxis( tupleList );
        // the slicerAxis may be overwritten during slicer execution
        // if there is a compound slicer. Save it so that it can be
        // reverted before completing result construction.
        savedSlicerAxis = this.slicerAxis;

        // Use the context created by the slicer for the other
        // axes. For example, "select filter([Customers], [Store
        // Sales] > 100) on columns from Sales where
        // ([Time].[1998])" should show customers whose 1998 (not
        // total) purchases exceeded 100.
        internalSlicerEvaluator = this.evaluator;
        if ( tupleList.size() > 1 ) {
          tupleList = removeUnaryMembersFromTupleList( tupleList, evaluator );
          tupleList = AggregateCalc.optimizeTupleList( evaluator, tupleList, false );
          evaluator.setSlicerTuples( tupleList );

          final Calc valueCalc = new CurrentValueUnknownCalc( ScalarType.INSTANCE ) ;

          final List<Member> prevSlicerMembers = new ArrayList<>();

          final Calc calcCached = new AbstractProfilingNestedUnknownCalc( query.getSlicerCalc().getType() ) {
            @Override
			public Object evaluate( Evaluator evaluator ) {
              try {
                evaluator.getTiming().markStart( "EvalForSlicer" );
                TupleList list =
                    AbstractAggregateFunDef.processUnrelatedDimensions( ( (RolapEvaluator) evaluator )
                        .getOptimizedSlicerTuples( null ), evaluator );
                for ( Member member : prevSlicerMembers ) {
                  if ( evaluator.getContext( member.getHierarchy() ) instanceof CompoundSlicerRolapMember ) {
                    evaluator.setContext( member );
                  }
                }
                return AggregateCalc.aggregate( valueCalc, evaluator, list );
              } finally {
                evaluator.getTiming().markEnd( "EvalForSlicer" );
              }

            }

            // depend on the full evaluation context
            @Override
			public boolean dependsOn( Hierarchy hierarchy ) {
              return true;
            }
          };

          final ExpCacheDescriptorImpl cacheDescriptor =
              new ExpCacheDescriptorImpl( query.getSlicerAxis().getSet(), calcCached, evaluator );
          // Generate a cached calculation for slicer aggregation
          // This is so critical for performance that we should consider creating an
          // optimized query level slicer cache.
          final Calc calc = new CacheCalc( query.getSlicerAxis().getSet().getType(), cacheDescriptor );

          // replace the slicer set with a placeholder to avoid
          // interaction between the aggregate calc we just created
          // and any calculated members that might be present in
          // the slicer.
          // Arbitrarily picks the first dim of the first tuple
          // to use as placeholder.
          if ( tupleList.get( 0 ).size() > 1 ) {
            for ( int i = 1; i < tupleList.get( 0 ).size(); i++ ) {
              Member placeholder =
                  setPlaceholderSlicerAxis( (RolapMember) tupleList.get( 0 ).get( i ), calc, false, tupleList, solveOrder );
              prevSlicerMembers.add( evaluator.setContext( placeholder ) );
            }
          }

          Member placeholder =
              setPlaceholderSlicerAxis( (RolapMember) tupleList.get( 0 ).get( 0 ), calc, true, tupleList, solveOrder );

          Util.explain( evaluator.root.statement.getProfileHandler(), "Axis (FILTER):", query.getSlicerCalc(), evaluator
              .getTiming() );

          evaluator.setContext( placeholder );
        }
      } while ( phase() );

      // final slicerEvaluator
      slicerEvaluator = evaluator.push();

      /////////////////////////////////////////////////////////////////
      // Determine Axes
      //
      boolean changed = false;

      // reset to total member count
      axisMembers.clearTotalCellCount();

      for ( int i = 0; i < axes.length; i++ ) {
        final QueryAxis axis = query.getAxes()[i];
        final Calc calc = query.getAxisCalcs()[i];
        loadMembers( emptyNonAllMembers, evaluator, axis, calc, axisMembers );
      }

      if ( !axisMembers.isEmpty() ) {
        for ( Member m : axisMembers ) {
          if ( m.isMeasure() ) {
            // A Measure was explicitly declared on an
            // axis, don't need to worry about Measures
            // for this query.
            measureMembers.clear();
          }
        }
        changed = replaceNonAllMembers( nonAllMembers, axisMembers );
        axisMembers.clearMembers();
      }

      if ( changed ) {
        // only count number of members, do not collect any
        axisMembers.countOnly( true );
        // reset to total member count
        axisMembers.clearTotalCellCount();

        final int savepoint = evaluator.savepoint();
        try {
          for ( int i = 0; i < axes.length; i++ ) {
            final QueryAxis axis = query.getAxes()[i];
            final Calc calc = query.getAxisCalcs()[i];
            loadMembers( nonAllMembers, evaluator, axis, calc, axisMembers );
            evaluator.restore( savepoint );
          }
        } finally {
          evaluator.restore( savepoint );
        }
      }

      // throws exception if number of members exceeds limit
      axisMembers.checkLimit();

      /////////////////////////////////////////////////////////////////
      // Execute Axes
      //
      final int savepoint = evaluator.savepoint();
      do {
        try {
          boolean redo;
          do {
            evaluator.restore( savepoint );
            redo = false;
            for ( int i = 0; i < axes.length; i++ ) {
              QueryAxis axis = query.getAxes()[i];
              final Calc calc = query.getAxisCalcs()[i];
              TupleIterable tupleIterable =
                  evalExecute( nonAllMembers, nonAllMembers.size() - 1, evaluator, axis, calc );

              if ( !nonAllMembers.isEmpty() ) {
                final TupleIterator tupleIterator = tupleIterable.tupleIterator();
                if ( tupleIterator.hasNext() ) {
                  List<Member> tuple0 = tupleIterator.next();
                  // Only need to process the first tuple on
                  // the axis.
                  for ( Member m : tuple0 ) {
                    if ( m.isCalculated() ) {
                      CalculatedMeasureVisitor visitor = new CalculatedMeasureVisitor();
                      m.getExpression().accept( visitor );
                      Dimension dimension = visitor.dimension;
                      if ( removeDimension( dimension, nonAllMembers ) ) {
                        redo = true;
                      }
                    }
                  }
                }
              }

              if ( !redo ) {
                Util.explain(
                    evaluator.root.statement.getProfileHandler(),
                    new StringBuilder("Axis (").append(axis.getAxisName()).append("):").toString(),
                    calc,
                    evaluator.getTiming() );
              }

              this.axes[i] = new RolapAxis( TupleCollections.materialize( tupleIterable, false ) );
            }
          } while ( redo );
        } catch ( CellRequestQuantumExceededException e ) {
          // Safe to ignore. Need to call 'phase' and loop again.
        }
      } while ( phase() );

      evaluator.restore( savepoint );

      // Get value for each Cell
      // Cells will not be calculated if only CELL_ORDINAL requested.
      QueryComponent[] cellProperties = query.getCellProperties();
      if(!(cellProperties.length == 1
              && ((NameSegment)
              org.eclipse.daanse.olap.common.Util.parseIdentifier(cellProperties[0].toString()).get(0)).getName().equalsIgnoreCase(
            		  StandardProperty.CELL_ORDINAL.getName()    ))) {
        final Locus locus = new LocusImpl( execution, null, "Loading cells" );
        LocusImpl.push( locus );
        try {
          executeBody( internalSlicerEvaluator, query, new int[axes.length] );
        } finally {
          Util.explain( evaluator.root.statement.getProfileHandler(), "QueryBody:", null, evaluator.getTiming() );LocusImpl.pop( locus );
        }
      }

      // If you are very close to running out of memory due to
      // the number of CellInfo's in cellInfos, then calling this
      // may cause the out of memory one is trying to aviod.
      // On the other hand, calling this can reduce the size of
      // the ObjectPool's internal storage by half (but, of course,
      // it will not reduce the size of the stored objects themselves).
      // Only call this if there are lots of CellInfo.
      if ( this.cellInfos.size() > 10000 ) {
        this.cellInfos.trimToSize();
      }
      // revert the slicer axis so that the original slicer
      // can be included in the result.
      this.slicerAxis = savedSlicerAxis;
    } catch ( ResultLimitExceededException ex ) {
      // If one gets a ResultLimitExceededException, then
      // don't count on anything being worth caching.
      normalExecution = false;

      // De-reference data structures that might be holding
      // partial results but surely are taking up memory.
      evaluator = null;
      slicerEvaluator = null;
      cellInfos = null;
      batchingReader = null;
      for ( int i = 0; i < axes.length; i++ ) {
        axes[i] = null;
      }
      slicerAxis = null;

      query.clearEvalCache();

      throw ex;
    } finally {
      if ( normalExecution ) {
        // Expression cache duration is for each query. It is time to
        // clear out the whole expression cache at the end of a query.
        evaluator.clearExpResultCache( true );
        execution.setExpCacheCounts( evaluator.root.expResultCacheHitCount, evaluator.root.expResultCacheMissCount );
      }
      if ( LOGGER.isDebugEnabled() ) {
        LOGGER.debug( "RolapResult<init>: {}", Util.printMemory());
      }
    }
  }

  /**
   * Sets slicerAxis to a dummy placeholder RolapAxis containing a single item TupleList with the null member of
   * hierarchy. This is used with compound slicer evaluation to avoid the slicer tuple list from interacting with the
   * aggregate calc which rolls up the set. This member will contain the AggregateCalc which rolls up the set on the
   * slicer.
   */
  private Member setPlaceholderSlicerAxis( final RolapMember member, final Calc calc, boolean setAxis,
      TupleList tupleList, int solveOrder ) {
    ValueFormatter formatter;
    if ( member.getDimension().isMeasures() ) {
      formatter = ( (RolapMeasure) member ).getFormatter();
    } else {
      formatter = null;
    }

    CompoundSlicerRolapMember placeholderMember =
        new CompoundSlicerRolapMember( (RolapMember) member.getHierarchy().getNullMember(), calc, formatter,
            tupleList, solveOrder );

    placeholderMember.setProperty( StandardProperty.FORMAT_STRING.getName(), member.getPropertyValue( StandardProperty.FORMAT_STRING
        .getName() ) );
    placeholderMember.setProperty( StandardProperty.FORMAT_EXP_PARSED.getName(), member.getPropertyValue(
    		StandardProperty.FORMAT_EXP_PARSED.getName() ) );

    if ( setAxis ) {
      TupleList dummyList = TupleCollections.createList( 1 );
      dummyList.addTuple( placeholderMember );
      this.slicerAxis = new RolapAxis( dummyList );
    }
    return placeholderMember;
  }

  private boolean phase() {
    if ( batchingReader.isDirty() ) {
      execution.tracePhase( batchingReader.getHitCount(), batchingReader.getMissCount(), batchingReader
          .getPendingCount() );
      // flush the expression cache during each
      // phase of loading aggregations
      evaluator.clearExpResultCache( false );
      return batchingReader.loadAggregations();
    } else {
      execution.setCellCacheHitCount( batchingReader.getHitCount() );
      execution.setCellCacheMissCount( batchingReader.getMissCount() );
      execution.setCellCachePendingCount( batchingReader.getPendingCount() );
      return false;
    }
  }

  /**
   * This function removes single instance members from the compound slicer, enabling more regular slicer behavior for
   * those members. For instance, calculated members can override the context of these members correctly.
   *
   * @param tupleList
   *          The list to shrink.
   * @param evaluator
   *          The slicer evaluator.
   * @return a new list of tuples reduced in size.
   */
  private TupleList removeUnaryMembersFromTupleList( TupleList tupleList, RolapEvaluator evaluator ) {
    // we can remove any unary coordinates from the compound slicer, and
    // account for them in the slicer evaluator.

    // First, determine if there are any unary members within the tuples.
    List<Member> first = null;
    boolean[] unary = null;
    for ( List<Member> tuple : tupleList ) {
      if ( first == null ) {
        first = tuple;
        unary = new boolean[tuple.size()];
        for ( int i = 0; i < unary.length; i++ ) {
          unary[i] = true;
        }
      } else {
        for ( int i = 0; i < tuple.size(); i++ ) {
          if ( unary[i] && !tuple.get( i ).equals( first.get( i ) ) ) {
            unary[i] = false;
          }
        }
      }
    }
    int toRemove = 0;
    for ( int i = 0; i < unary.length; i++ ) {
      if ( unary[i] ) {
        evaluator.setContext( first.get( i ) );
        toRemove++;
      }
    }

    // remove the unnecessary members from the compound slicer
    if ( toRemove > 0 ) {
      TupleList newList = new ListTupleList( tupleList.getArity() - toRemove, new ArrayList<>() );
      for ( List<Member> tuple : tupleList ) {
        List<Member> ntuple = new ArrayList<>();
        for ( int i = 0; i < tuple.size(); i++ ) {
          if ( !unary[i] ) {
            ntuple.add( tuple.get( i ) );
          }
        }
        newList.add( ntuple );
      }
      tupleList = newList;
    }
    return tupleList;
  }

  protected boolean removeDimension( Dimension dimension, List<List<Member>> memberLists ) {
    for ( int i = 0; i < memberLists.size(); i++ ) {
      List<Member> memberList = memberLists.get( i );
      if ( memberList.get( 0 ).getDimension().equals( dimension ) ) {
        memberLists.remove( i );
        return true;
      }
    }
    return false;
  }

  @Override
  public final Execution getExecution() {
    return execution;
  }

  private static class CalculatedMeasureVisitor extends MdxVisitorImpl {
    Dimension dimension;

    CalculatedMeasureVisitor() {
    }

    @Override
	public Object visitDimensionExpression( DimensionExpression dimensionExpr ) {
      dimension = dimensionExpr.getDimension();
      return null;
    }

    @Override
	public Object visitHierarchyExpression( HierarchyExpression hierarchyExpr ) {
      Hierarchy hierarchy = hierarchyExpr.getHierarchy();
      dimension = hierarchy.getDimension();
      return null;
    }

    @Override
	public Object visitMemberExpression( MemberExpression memberExpr ) {
      Member member = memberExpr.getMember();
      dimension = member.getHierarchy().getDimension();
      return null;
    }
  }

  protected boolean replaceNonAllMembers( List<List<Member>> nonAllMembers, AxisMemberList axisMembers ) {
    boolean changed = false;
    List<Member> mList = new ArrayList<>();
    for ( ListIterator<List<Member>> it = nonAllMembers.listIterator(); it.hasNext(); ) {
      List<Member> ms = it.next();
      Hierarchy h = ms.get( 0 ).getHierarchy();
      mList.clear();
      for ( Member m : axisMembers ) {
        if ( m.getHierarchy().equals( h ) ) {
          mList.add( m );
        }
      }
      if ( !mList.isEmpty() ) {
        changed = true;
        it.set( new ArrayList<>( mList ) );
      }
    }
    return changed;
  }

  protected void loadMembers(List<List<Member>> nonAllMembers, RolapEvaluator evaluator, QueryAxis axis, Calc calc,
                             AxisMemberList axisMembers ) {
    int attempt = 0;
    evaluator.setCellReader( batchingReader );
    while ( true ) {
      axisMembers.clearAxisCount();
      final int savepoint = evaluator.savepoint();
      try {
        evalLoad( nonAllMembers, nonAllMembers.size() - 1, evaluator, axis, calc, axisMembers );
      } catch ( CellRequestQuantumExceededException e ) {
        // Safe to ignore. Need to call 'phase' and loop again.
        // Decrement count because it wasn't a recursive formula that
        // caused the iteration.
        --attempt;
      } finally {
        evaluator.restore( savepoint );
      }

      if ( !phase() ) {
        break;
      } else {
        // Clear invalid expression result so that the next evaluation
        // will pick up the newly loaded aggregates.
        evaluator.clearExpResultCache( false );
      }

      if ( attempt++ > maxEvalDepth ) {
        throw Util.newInternal( new StringBuilder("Failed to load all aggregations after ")
            .append(maxEvalDepth).append(" passes; there's probably a cycle").toString() );
      }
    }
  }

  void evalLoad( List<List<Member>> nonAllMembers, int cnt, Evaluator evaluator, QueryAxis axis, Calc calc,
      AxisMemberList axisMembers ) {
    final int savepoint = evaluator.savepoint();
    try {
      if ( cnt < 0 ) {
        executeAxis( evaluator, axis, calc, false, axisMembers );
      } else {
        for ( Member m : nonAllMembers.get( cnt ) ) {
          evaluator.setContext( m );
          evalLoad( nonAllMembers, cnt - 1, evaluator, axis, calc, axisMembers );
        }
      }
    } finally {
      evaluator.restore( savepoint );
    }
  }

  TupleIterable evalExecute( List<List<Member>> nonAllMembers, int cnt, RolapEvaluator evaluator, QueryAxis queryAxis,
      Calc calc ) {
    final int savepoint = evaluator.savepoint();
    final int arity = calc == null ? 0 : calc.getType().getArity();
    if ( cnt < 0 ) {
      try {
        return executeAxis( evaluator, queryAxis, calc, true, null );
      } finally {
        evaluator.restore( savepoint );
      }
      // No need to clear expression cache here as no new aggregates are
      // loaded(aggregatingReader reads from cache).
    } else {
      try {
        TupleList axisResult = TupleCollections.emptyList( arity );
        for ( Member m : nonAllMembers.get( cnt ) ) {
          evaluator.setContext( m );
          TupleIterable axis = evalExecute( nonAllMembers, cnt - 1, evaluator, queryAxis, calc );
          boolean ordered = false;
          if ( queryAxis != null ) {
            ordered = queryAxis.isOrdered();
          }
          axisResult = mergeAxes( axisResult, axis, ordered );
        }
        return axisResult;
      } finally {
        evaluator.restore( savepoint );
      }
    }
  }

  /**
   * Finds all root Members 1) whose Hierarchy does not have an ALL Member, 2) whose default Member is not the ALL
   * Member and 3) all Measures.
   *
   * @param nonDefaultAllMembers
   *          List of all root Members for Hierarchies whose default Member is not the ALL Member.
   * @param nonAllMembers
   *          List of root Members for Hierarchies that have no ALL Member.
   * @param measureMembers
   *          List all Measures
   */
  protected void loadSpecialMembers( List<Member> nonDefaultAllMembers, List<List<Member>> nonAllMembers,
      List<Member> measureMembers ) {
    CatalogReader schemaReader = evaluator.getCatalogReader();
    Member[] evalMembers = evaluator.getMembers();
    for ( Member em : evalMembers ) {
      if ( em.isCalculated() ) {
        continue;
      }
      Hierarchy h = em.getHierarchy();
      Dimension d = h.getDimension();
      if ( d.getDimensionType() == DimensionType.TIME_DIMENSION) {
        continue;
      }
      if ( !em.isAll() ) {
        List<Member> rootMembers = schemaReader.getHierarchyRootMembers( h );
        if ( em.isMeasure() ) {
          for ( Member mm : rootMembers ) {
            measureMembers.add( mm );
          }
        } else {
          if ( h.hasAll() ) {
            for ( Member m : rootMembers ) {
              if ( m.isAll() ) {
                nonDefaultAllMembers.add( m );
                break;
              }
            }
          } else {
            nonAllMembers.add( rootMembers );
          }
        }
      }
    }
  }

  @Override
protected Logger getLogger() {
    return LOGGER;
  }

  public final RolapCube getCube() {
    return evaluator.getCube();
  }

  // implement Result
  @Override
public Axis[] getAxes() {
    return axes;
  }

  /**
   * Get the Cell for the given Cell position.
   *
   * @param pos
   *          Cell position.
   * @return the Cell associated with the Cell position.
   */
  @Override
public Cell getCell( int[] pos ) {
    if ( pos.length != point.size() ) {
      throw Util.newError( "coordinates should have dimension " + point.size() );
    }

    for ( int i = 0; i < pos.length; i++ ) {
      if ( positionsHighCardinality.containsKey(i) && Boolean.TRUE.equals( positionsHighCardinality.get( i ) ) ) {
        final Locus locus = new LocusImpl( execution, null, "Loading cells" );
        LocusImpl.push( locus );
        try {
          executeBody( evaluator, statement.getQuery(), pos );
        } finally {
          LocusImpl.pop( locus );
        }
        break;
      }
    }

    CellInfo ci = cellInfos.lookup( pos );
    if ( ci.value == null ) {
      for ( int i = 0; i < pos.length; i++ ) {
        int po = pos[i];
        if ( po < 0 || po >= axes[i].getPositions().size() ) {
          throw Util.newError( "coordinates out of range" );
        }
      }
      ci.value = Util.nullValue;
    }

    return new RolapCell( this, pos.clone(), ci );
  }

  private TupleIterable executeAxis( Evaluator evaluator, QueryAxis queryAxis, Calc axisCalc, boolean construct,
      AxisMemberList axisMembers ) {
    if ( queryAxis == null ) {
      // Create an axis containing one position with no members (not
      // the same as an empty axis).
      return new DelegatingTupleList( 0, Collections.singletonList( Collections.<Member> emptyList() ) );
    }
    final int savepoint = evaluator.savepoint();
    try {
      evaluator.setNonEmpty( queryAxis.isNonEmpty() );
      evaluator.setEvalAxes( true );
      final TupleIterable iterable = ( (TupleIteratorCalc<?>) axisCalc ).evaluate( evaluator );
      if ( axisCalc.getClass().getName().indexOf( "OrderFunDef" ) != -1 ) {
        queryAxis.setOrdered( true );
      }
      if ( iterable instanceof TupleList list ) {
        if (!construct && axisMembers != null ) {
          axisMembers.mergeTupleList( list );
        }
      } else {
        // Iterable
        TupleCursor cursor = iterable.tupleCursor();
        if (!construct && axisMembers != null ) {
          axisMembers.mergeTupleIter( cursor );
        }
      }
      return iterable;
    } finally {
      evaluator.restore( savepoint );
    }
  }

  private void executeBody(RolapEvaluator evaluator, Query query, final int[] pos ) {
    // Compute the cells several times. The first time, use a dummy
    // evaluator which collects requests.
    int count = 0;
    final int savepoint = evaluator.savepoint();
    while ( true ) {
      evaluator.setCellReader( batchingReader );
      try {
        executeStripe( query.getAxes().length - 1, evaluator, pos );
      } catch ( CellRequestQuantumExceededException e ) {
        // Safe to ignore. Need to call 'phase' and loop again.
        // Decrement count because it wasn't a recursive formula that
        // caused the iteration.
        --count;
      }
      evaluator.restore( savepoint );

      // Retrieve the aggregations collected.
      //
      if ( !phase() ) {
        // We got all of the cells we needed, so the result must be
        // correct.
        return;
      } else {
        // Clear invalid expression result so that the next evaluation
        // will pick up the newly loaded aggregates.
        evaluator.clearExpResultCache( false );
      }

      if ( count++ > maxEvalDepth ) {
        if ( evaluator instanceof RolapDependencyTestingEvaluator ) {
          // The dependency testing evaluator can trigger new
          // requests every cycle. So let is run as normal for
          // the first N times, then run it disabled.
          ( (RolapDependencyTestingEvaluator.DteRoot) evaluator.root ).disabled = true;
          if ( count > maxEvalDepth * 2 ) {
            throw Util.newInternal( new StringBuilder("Query required more than ")
                .append(count).append(" iterations").toString() );
          }
        } else {
          throw Util.newInternal( new StringBuilder("Query required more than ").append(count)
              .append(" iterations").toString() );
        }
      }

      cellInfos.clear();
    }
  }

  boolean isDirty() {
    return batchingReader.isDirty();
  }

  /**
   * Evaluates an expression. Intended for evaluating named sets.
   *
   *
   * Does not modify the contents of the evaluator.
   *
   * @param calc
   *          Compiled expression
   * @param slicerEvaluator
   *          Evaluation context for slicers
   * @param contextEvaluator
   *          Evaluation context (optional)
   * @return Result
   */
  Object evaluateExp( Calc calc, RolapEvaluator slicerEvaluator, Evaluator contextEvaluator ) {
    int attempt = 0;

    RolapEvaluator evaluatorInner = slicerEvaluator.push();
    if ( contextEvaluator != null && contextEvaluator.isEvalAxes() ) {
      evaluatorInner.setEvalAxes( true );
    }

    final int savepoint = evaluatorInner.savepoint();
    boolean dirty = batchingReader.isDirty();
    try {
      while ( true ) {
        evaluatorInner.restore( savepoint );

        evaluatorInner.setCellReader( batchingReader );
        Object preliminaryValue = calc.evaluate( evaluatorInner );

        if ( preliminaryValue instanceof TupleIterable iterable ) {
          final TupleCursor cursor = iterable.tupleCursor();
          while ( cursor.forward() ) {
            // ignore
          }
        }

        if ( !phase() ) {
          break;
        } else {
          // Clear invalid expression result so that the next
          // evaluation will pick up the newly loaded aggregates.
          evaluatorInner.clearExpResultCache( false );
        }

        if ( attempt++ > maxEvalDepth ) {
          throw Util.newInternal( new StringBuilder("Failed to load all aggregations after ")
              .append(maxEvalDepth)
              .append("passes; there's probably a cycle").toString() );
        }
      }

      // If there were pending reads when we entered, some of the other
      // expressions may have been evaluated incorrectly. Set the
      // reader's 'dirty' flag so that the caller knows that it must
      // re-evaluate them.
      if ( dirty ) {
        batchingReader.setDirty( true );
      }

      evaluatorInner.restore( savepoint );
      evaluatorInner.setCellReader( aggregatingReader );
      return calc.evaluate( evaluatorInner );
    } finally {
      evaluatorInner.restore( savepoint );
    }
  }

  private void executeStripe( int axisOrdinal, RolapEvaluator revaluator, final int[] pos ) {
    if ( axisOrdinal < 0 ) {
      RolapAxis axis = (RolapAxis) slicerAxis;
      TupleList tupleList = axis.getTupleList();
      final Iterator<List<Member>> tupleIterator = tupleList.iterator();
      if ( tupleIterator.hasNext() ) {
        final List<Member> members = tupleIterator.next();
        execution.checkCancelOrTimeout();
        final int savepoint = revaluator.savepoint();
        revaluator.setContext( members );
        Object o;
        try {
          o = revaluator.evaluateCurrent();
        } catch ( MondrianEvaluationException e ) {
          LOGGER.warn(MONDRIAN_EXCEPTION_IN_EXECUTE_STRIPE, e );
          o = e;
        } finally {
          revaluator.restore( savepoint );
        }

        CellInfo ci = null;

        // Get the Cell's format string and value formatting
        // Object.
        try {
          // This code is a combination of the code found in
          // the old RolapResult
          // getCellNoDefaultFormatString method and
          // the old RolapCell getFormattedValue method.

          // Create a CellInfo object for the given position
          // integer array.
          ci = cellInfos.create( point.getOrdinals() );

          String cachedFormatString = null;

          // Determine if there is a CellFormatter registered for
          // the current Cube's Measure's Dimension. If so,
          // then find or create a CellFormatterValueFormatter
          // for it. If not, then find or create a Locale based
          // FormatValueFormatter.
          final RolapCube cube = getCube();
          Hierarchy measuresHierarchy = cube.getMeasuresHierarchy();
          RolapMeasure m = (RolapMeasure) revaluator.getContext( measuresHierarchy );
          ValueFormatter valueFormatter = m.getFormatter();
          if ( valueFormatter == null ) {
            cachedFormatString = revaluator.getFormatString();
            Locale locale = statement.getMondrianConnection().getLocale();
            valueFormatter = formatValueFormatters.get( locale );
            if ( valueFormatter == null ) {
              valueFormatter = new FormatValueFormatter( locale );
              formatValueFormatters.put( locale, valueFormatter );
            }
          }

          ci.formatString = cachedFormatString;
          ci.valueFormatter = valueFormatter;
        } catch ( ResultLimitExceededException | CellRequestQuantumExceededException e) {
          // Do NOT ignore a ResultLimitExceededException!!!
          // or We need to throw this so another phase happens.
          // or Errors indicate fatal JVM problems; do not discard
          throw e;
        } catch ( MondrianEvaluationException e ) {
          // ignore but warn
          LOGGER.warn( MONDRIAN_EXCEPTION_IN_EXECUTE_STRIPE, e );
        } catch ( Exception e ) {
          LOGGER.warn( MONDRIAN_EXCEPTION_IN_EXECUTE_STRIPE, e );
//            discard( e );
        }

        if (ci != null && o != Util.valueNotReadyException ) {
          ci.value = o;
        }
      }
    } else {
      RolapAxis axis = (RolapAxis) axes[axisOrdinal];
      TupleList tupleList = axis.getTupleList();
     tupleList.size();  // force materialize

        for ( List<Member> tuple : tupleList ) {
          List<Member> measures = new ArrayList<>( statement.getQuery().getMeasuresMembers() );
          for ( Member measure : measures ) {
            if ( measure instanceof RolapBaseCubeMeasure baseCubeMeasure
                && baseCubeMeasure.getAggregator() == DistinctCountAggregator.INSTANCE) {
                processDistinctMeasureExpr( tuple, baseCubeMeasure );
            }
          }
        }

        int tupleIndex = 0;
        for ( final List<Member> tuple : tupleList ) {
          point.setAxis( axisOrdinal, tupleIndex );
          final int savepoint = revaluator.savepoint();
          try {
            revaluator.setEvalAxes( true );
            revaluator.setContext( tuple );
            execution.checkCancelOrTimeout();
            executeStripe( axisOrdinal - 1, revaluator, pos );
          } finally {
            revaluator.restore( savepoint );
          }
          tupleIndex++;

      }
    }
  }



  /**
   * Distinct counts are aggregated separately from other measures. We need to apply filters to each level in the query.
   *
   *
   * Replace VisualTotalMember expressions with new expressions where all leaf level members are included.
   * 
   *
   *
   * Example. For MDX query:
   *
   *
   *
   *
   * WITH SET [XL_Row_Dim_0] AS
   *         VisualTotals(
   *           Distinct(
   *             Hierarchize(
   *               {Ascendants([Store].[All Stores].[USA].[CA]),
   *                Descendants([Store].[All Stores].[USA].[CA])})))
   *        select NON EMPTY
   *          Hierarchize(
   *            Intersect(
   *              {DrilldownLevel({[Store].[All Stores]})},
   *              [XL_Row_Dim_0])) ON COLUMNS
   *        from [HR]
   *        where [Measures].[Number of Employees]
   *
   *
   *
   *
   *
   * For member [Store].[All Stores], we replace aggregate expression
   *
   *
   *
   *
   * Aggregate({[Store].[All Stores].[USA]})
   *
   *
   *
   *
   * with
   *
   *
   *
   *
   * Aggregate({[Store].[All Stores].[USA].[CA].[Alameda].[HQ],
   *               [Store].[All Stores].[USA].[CA].[Beverly Hills].[Store 6],
   *               [Store].[All Stores].[USA].[CA].[Los Angeles].[Store 7],
   *               [Store].[All Stores].[USA].[CA].[San Diego].[Store 24],
   *               [Store].[All Stores].[USA].[CA].[San Francisco].[Store 14]
   *              })
   *
   *
   *
   *
   *
   * TODO: Can be optimized. For that particular query we don't need to go to the lowest level. We can simply replace it
   * with:
   *
   *
   * Aggregate({[Store].[All Stores].[USA].[CA]})
   *
   *
   * Because all children of [Store].[All Stores].[USA].[CA] are included.
   * 
   */
  private List<Member> processDistinctMeasureExpr( List<Member> tuple, RolapBaseCubeMeasure measure ) {
    for ( Member member : tuple ) {
      if ( !( member instanceof VisualTotalMember ) ) {
        continue;
      }
      evaluator.setContext( measure );
      List<Member> exprMembers = new ArrayList<>();
      processMemberExpr( member, exprMembers );
      ( (VisualTotalMember) member ).setExpression( evaluator, exprMembers );
    }
    return tuple;
  }

  private static void processMemberExpr( Object o, List<Member> exprMembers ) {
    if ( o instanceof Member member && o instanceof RolapCubeMember ) {
      exprMembers.add( member );
    } else if ( o instanceof VisualTotalMember member ) {
      Expression exp = member.getExpression();
      processMemberExpr( exp, exprMembers );
    } else if ( o instanceof Expression exp && !( o instanceof MemberExpression) ) {
      ResolvedFunCallImpl funCall = (ResolvedFunCallImpl) exp;
      Expression[] exps = funCall.getArgs();
      processMemberExpr( exps, exprMembers );
    } else if ( o instanceof Expression[] exps ) {
      for ( Expression exp : exps ) {
        processMemberExpr( exp, exprMembers );
      }
    } else if ( o instanceof MemberExpression memberExp ) {
      Member member = memberExp.getMember();
      processMemberExpr( member, exprMembers );
    }
  }

  /**
   * Converts a set of cell coordinates to a cell ordinal.
   *
   *
   * This method can be expensive, because the ordinal is computed from the length of the axes, and therefore the axes
   * need to be instantiated.
   */
  public int getCellOrdinal( int[] pos ) {
    if ( modulos == null ) {
      makeModulos();
    }
    return modulos.getCellOrdinal( pos );
  }

  /**
   * Instantiates the calculator to convert cell coordinates to a cell ordinal and vice versa.
   *
   *
   * To create the calculator, any axis that is based upon an Iterable is converted into a List - thus increasing memory
   * usage.
   */
  protected void makeModulos() {
    modulos = Modulos.Generator.create( axes );
  }

  /**
   * Called only by RolapCell. Use this when creating an Evaluator is not required.
   *
   * @param pos
   *          Coordinates of cell
   * @return Members which form the context of the given cell
   */
  public RolapMember[] getCellMembers( int[] pos ) {
    RolapMember[] members = (RolapMember[]) evaluator.getMembers().clone();
    for ( int i = 0; i < pos.length; i++ ) {
      Position position = axes[i].getPositions().get( pos[i] );
      for ( Member member : position ) {
        RolapMember m = (RolapMember) member;
        int ordinal = m.getHierarchy().getOrdinalInCube();
        members[ordinal] = m;
      }
    }
    return members;
  }

  public Evaluator getRootEvaluator() {
    return evaluator;
  }

  public Evaluator getEvaluator( int[] pos ) {
    // Set up evaluator's context, so that context-dependent format
    // strings work properly.
    Evaluator cellEvaluator = evaluator.push();
    populateEvaluator( cellEvaluator, pos );
    return cellEvaluator;
  }

  public void populateEvaluator( Evaluator evaluator, int[] pos ) {
    for ( int i = -1; i < axes.length; i++ ) {
      Axis axis;
      int index;
      if ( i < 0 ) {
        axis = slicerAxis;
        if ( axis.getPositions().isEmpty() ) {
          continue;
        }
        index = 0;
      } else {
        axis = axes[i];
        index = pos[i];
      }
      Position position = axis.getPositions().get( index );
      evaluator.setContext( position );
    }
  }

  /**
   * Collection of members found on an axis.
   *
   *
   * The behavior depends on the mode (i.e. the kind of axis). If it collects, it generally eliminates duplicates. It
   * also has a mode where it only counts members, does not collect them.
   * 
   *
   *
   * This class does two things. First it collects all Members found during the Member-Determination phase. Second, it
   * counts how many Members are on each axis and forms the product, the totalCellCount which is checked against the
   * ResultLimit property value.
   * 
   */
  private static class AxisMemberList implements Iterable<Member> {
    private final List<Member> members;
    // Also store members by hierarchy for faster de-duplication and also reuse in RolapEvaluator
    private final Map<Hierarchy, Set<Member>> membersByHierarchy;
    private final int limit;
    private boolean isSlicer;
    private int totalCellCount;
    private int axisCount;
    private boolean countOnly;
    private final static String totalMembersLimitExceeded = "Total number of Members in result ({0,number}) exceeded limit ({1,number})";

      AxisMemberList() {
      this.countOnly = false;
      this.members = new ArrayList<>();
      this.membersByHierarchy = new HashMap<>();
      this.totalCellCount = 1;
      this.axisCount = 0;
      // Now that the axes are evaluated, make sure that the number of
      // cells does not exceed the result limit.
      this.limit = SystemWideProperties.instance().ResultLimit;
    }

    @Override
	public Iterator<Member> iterator() {
      return members.iterator();
    }

    void setSlicer( final boolean isSlicer ) {
      this.isSlicer = isSlicer;
    }

    boolean isEmpty() {
      return this.members.isEmpty();
    }

    void countOnly( boolean countOnly ) {
      this.countOnly = countOnly;
    }

    void checkLimit() {
      if ( this.limit > 0 ) {
        this.totalCellCount *= this.axisCount;
        if ( this.totalCellCount > this.limit ) {
          throw new ResourceLimitExceededException(MessageFormat.format(totalMembersLimitExceeded, String.valueOf(this.totalCellCount), String.valueOf(this.limit) ));
        }
        this.axisCount = 0;
      }
    }

    void clearAxisCount() {
      this.axisCount = 0;
    }

    void clearTotalCellCount() {
      this.totalCellCount = 1;
    }

    void clearMembers() {
      this.members.clear();
      this.membersByHierarchy.clear();
      this.axisCount = 0;
      this.totalCellCount = 1;
    }

    void mergeTupleList( TupleList list ) {
      mergeTupleIter( list.tupleCursor() );
    }

    private void mergeTupleIter( TupleCursor cursor ) {
      int currentIteration = 0;
      Execution execution = LocusImpl.peek().getExecution();
      while ( cursor.forward() ) {
        CancellationChecker.checkCancelOrTimeout( currentIteration++, execution );
        mergeTuple( cursor );
      }
    }

    private Member getTopParent( Member m ) {
      while ( true ) {
        Member parent = m.getParentMember();
        if ( parent == null ) {
          return m;
        }
        m = parent;
      }
    }

    private void mergeTuple( final TupleCursor cursor ) {
      final int arity = cursor.getArity();
      for ( int i = 0; i < arity; i++ ) {
        mergeMember( cursor.member( i ) );
      }
    }

    private void mergeMember( final Member member ) {
      this.axisCount++;
      if ( !countOnly ) {
        if ( isSlicer ) {
          if ( !contains( member ) ) {
            addMember( member );
          }
        } else {
          if ( member.isNull() || member.isMeasure() || member.isCalculated() || member.isAll()) {
            return;
          }
          Member topParent = getTopParent( member );
          if ( !contains( topParent ) ) {
            addMember( topParent );
          }
        }
      }
    }

    private boolean contains( Member member ) {
      if ( !membersByHierarchy.containsKey( member.getHierarchy() ) ) {
        return false;
      }
      return membersByHierarchy.get( member.getHierarchy() ).contains( member );
    }

    private void addMember( Member member ) {
      members.add( member );
      Hierarchy hierarchy = member.getHierarchy();
      membersByHierarchy.computeIfAbsent(hierarchy, k -> new HashSet<>()).add( member );
    }

    public List<Member> getMembers() {
      return members;
    }

    public Map<Hierarchy, Set<Member>> getMembersByHierarchy() {
      return membersByHierarchy;
    }
  }

  /**
   * Extension to {@link RolapEvaluatorRoot} which is capable of evaluating sets and named sets.
   *
   * 
   * A given set is only evaluated once each time a query is executed; the result is added to the
   * {@link #namedSetEvaluators} cache on first execution and re-used.
   *
   *
   * 
   * Named sets are always evaluated in the context of the slicer.
   *
   */
  protected static class RolapResultEvaluatorRoot extends RolapEvaluatorRoot {
    /**
     * Maps the names of sets to their values. Populated on demand.
     */
    private final Map<String, RolapSetEvaluator> setEvaluators = new HashMap<>();
    private final Map<String, RolapNamedSetEvaluator> namedSetEvaluators =
        new HashMap<>();

    final RolapResult result;
    private static final Object CycleSentinel = new Object();
    private static final Object NullSentinel = new Object();
      private final static String cycleDuringParameterEvaluation = "Cycle occurred while evaluating parameter ''{0}''";

      public RolapResultEvaluatorRoot( RolapResult result ) {
      super( result.execution );
      this.result = result;
    }

    @Override
	protected Evaluator.NamedSetEvaluator evaluateNamedSet( final NamedSet namedSet, boolean create ) {
      final String name = namedSet.getNameUniqueWithinQuery();
      if ( namedSet.isDynamic() && !create ) {
          RolapNamedSetEvaluator value = new RolapNamedSetEvaluator( this, namedSet );
          namedSetEvaluators.put( name, value );
          return value;

      } else {
        return namedSetEvaluators.computeIfAbsent(name, k -> new RolapNamedSetEvaluator( this, namedSet ));
      }
    }

    @Override
	protected Evaluator.SetEvaluator evaluateSet( final Expression exp, boolean create ) {
      // Sanity check: This expression HAS to return a set.
      if ( !( exp.getType() instanceof SetType ) ) {
        throw Util.newInternal( "Trying to evaluate set but expression does not return a set" );
      }

      // Should be acceptable to use the string representation of the
      // expression as the name
      final String name = exp.toString();

      // pedro, 20120914 - I don't quite understand the !create, I was
      // kind'a expecting the opposite here. But I'll maintain the same
      // logic
      if ( !create ) {
          RolapSetEvaluator value = new RolapSetEvaluator( this, exp );
          setEvaluators.put( name, value );
          return value;
      } else {
          return setEvaluators.computeIfAbsent(name, k -> new RolapSetEvaluator( this, exp ));
      }
    }

    @Override
	public Object getParameterValue( ParameterSlot slot ) {
      if ( slot.isParameterSet() ) {
        return slot.getParameterValue();
      }

      // Look in other places for the value. Which places we look depends
      // on the scope of the parameter.
      Parameter.Scope scope = slot.getParameter().getScope();
      switch ( scope ) {
        case System:
          // TODO: implement system params

          // fall through
        case Schema:
          // TODO: implement schema params

          // fall through
        case Connection:
          // if it's set in the session, return that value

          // fall through
        case Statement:
          break;

        default:
          throw Util.badValue( scope );
      }

      // Not set in any accessible scope. Evaluate the default value,
      // then cache it.
      Object liftedValue = slot.getCachedDefaultValue();
      Object value;
      if ( liftedValue != null ) {
        if ( liftedValue == CycleSentinel ) {
          throw new OlapRuntimeException(MessageFormat.format(cycleDuringParameterEvaluation, slot.getParameter().getName() ));
        }
        if ( liftedValue == NullSentinel ) {
          value = null;
        } else {
          value = liftedValue;
        }
        return value;
      }
      // Set value to a sentinel, so we can detect cyclic evaluation.
      slot.setCachedDefaultValue( CycleSentinel );
      value = result.evaluateExp( slot.getDefaultValueCalc(), result.slicerEvaluator, null );
      if ( value == null ) {
        liftedValue = NullSentinel;
      } else {
        liftedValue = value;
      }
      slot.setCachedDefaultValue( liftedValue );
      return value;
    }
  }

  /**
   * Formatter to convert values into formatted strings.
   *
   * 
   * Every Cell has a value, a format string (or CellFormatter) and a formatted value string. There are a wide range of
   * possible values (pick a Double, any Double - its a value). Because there are lots of possible values, there are
   * also lots of possible formatted value strings. On the other hand, there are only a very small number of format
   * strings and CellFormatter's. These formatters are to be cached in a synchronized HashMaps in order to limit how
   * many copies need to be kept around.
   *
   * 
   * There are two implementations of the ValueFormatter interface:
   *
   * {@link CellFormatterValueFormatter}, which formats using a user-registered {@link CellFormatter}; and
   * {@link FormatValueFormatter}, which takes the {@link Locale} object.
   *
   */
  public interface ValueFormatter {
    /**
     * Formats a value according to a format string.
     *
     * @param value
     *          Value
     * @param formatString
     *          Format string
     * @return Formatted value
     */
    String format( Object value, String formatString );

    /**
     * Formatter that always returns the empty string.
     */
    public static final ValueFormatter EMPTY = new ValueFormatter() {
      @Override
	public String format( Object value, String formatString ) {
        return "";
      }
    };
  }

  /**
   * A CellFormatterValueFormatter uses a user-defined {@link CellFormatter} to format values.
   */
  public static class CellFormatterValueFormatter implements ValueFormatter {
    final CellFormatter cf;

    /**
     * Creates a CellFormatterValueFormatter
     *
     * @param cf
     *          Cell formatter
     */
    public CellFormatterValueFormatter( CellFormatter cf ) {
      this.cf = cf;
    }

    @Override
	public String format( Object value, String formatString ) {
      return cf.format( value );
    }
  }

  /**
   * A FormatValueFormatter takes a {@link Locale} as a parameter and uses it to get the {@link mondrian.util.Format} to
   * be used in formatting an Object value with a given format string.
   */
  static class FormatValueFormatter implements ValueFormatter {
    final Locale locale;

    /**
     * Creates a FormatValueFormatter.
     *
     * @param locale
     *          Locale
     */
    FormatValueFormatter( Locale locale ) {
      this.locale = locale;
    }

    @Override
	public String format( Object value, String formatString ) {
      if ( Objects.equals( value ,Util.nullValue )) {
        value = null;
      }
      if ( value instanceof Throwable ) {
        return "#ERR: " + value.toString();
      }
      Format format = getFormat( formatString );
      return format.format( value );
    }

    private Format getFormat( String formatString ) {
      return Format.get( formatString, locale );
    }
  }

  /**
   * Synchronized Map from Locale to ValueFormatter. It is expected that there will be only a small number of Locale's.
   * Should these be a WeakHashMap?
   */
  protected static final Map<Locale, ValueFormatter> formatValueFormatters =
      Collections.synchronizedMap( new HashMap<Locale, ValueFormatter>() );

  /**
   * A CellInfo contains all of the information that a Cell requires. It is placed in the cellInfos map during
   * evaluation and serves as a constructor parameter for {@link RolapCell}.
   *
   * 
   * During the evaluation stage they are mutable but after evaluation has finished they are not changed.
   */
  static public class CellInfo {
    public Object value;
    public String formatString;
    public ValueFormatter valueFormatter;
    public long key;

    /**
     * Creates a CellInfo representing the position of a cell.
     *
     * @param key
     *          Ordinal representing the position of a cell
     */
    CellInfo( long key ) {
      this( key, null, null, ValueFormatter.EMPTY );
    }

    /**
     * Creates a CellInfo with position, value, format string and formatter of a cell.
     *
     * @param key
     *          Ordinal representing the position of a cell
     * @param value
     *          Value of cell, or null if not yet known
     * @param formatString
     *          Format string of cell, or null
     * @param valueFormatter
     *          Formatter for cell, or null
     */
    CellInfo( long key, Object value, String formatString, ValueFormatter valueFormatter ) {
      this.key = key;
      this.value = value;
      this.formatString = formatString;
      this.valueFormatter = valueFormatter;
    }

    @Override
	public int hashCode() {
      // Combine the upper 32 bits of the key with the lower 32 bits.
      // We used to use 'key ^ (key >>> 32)' but that was bad, because
      // CellKey.Two encodes (i, j) as
      // (i * Integer.MAX_VALUE + j), which is practically the same as
      // (i << 32, j). If i and j were
      // both k bits long, all of the hashcodes were k bits long too!
      return (int) ( key ^ ( key >>> 11 ) ^ ( key >>> 24 ) );
    }

    @Override
	public boolean equals( Object o ) {
      if ( o instanceof CellInfo that ) {
        return that.key == this.key;
      } else {
        return false;
      }
    }

    /**
     * Returns the formatted value of the Cell
     *
     * @return formatted value of the Cell
     */
    public String getFormatValue() {
      return valueFormatter.format( value, formatString );
    }
  }

  /**
   * API for the creation and lookup of {@link CellInfo} objects. There are two implementations, one that uses a Map for
   * storage and the other uses an ObjectPool.
   */
  interface CellInfoContainer {
    /**
     * Returns the number of CellInfo objects in this container.
     *
     * @return the number of CellInfo objects.
     */
    int size();

    /**
     * Reduces the size of the internal data structures needed to support the current entries. This should be called
     * after all CellInfo objects have been added to container.
     */
    void trimToSize();

    /**
     * Removes all CellInfo objects from container. Does not change the size of the internal data structures.
     */
    void clear();

    /**
     * Creates a new CellInfo object, adds it to the container a location pos and returns it.
     *
     * @param pos
     *          where to store CellInfo object.
     * @return the newly create CellInfo object.
     */
    CellInfo create( int[] pos );

    /**
     * Gets the CellInfo object at the location pos.
     *
     * @param pos
     *          where to find the CellInfo object.
     * @return the CellInfo found or null.
     */
    CellInfo lookup( int[] pos );
  }

  /**
   * Implementation of {@link CellInfoContainer} which uses a {@link Map} to store CellInfo Objects.
   *
   * 
   * Note that the CellKey point instance variable is the same Object (NOT a copy) that is used and modified during the
   * recursive calls to executeStripe - the create method relies on this fact.
   */
  static class CellInfoMap implements CellInfoContainer {
    private final Map<CellKey, CellInfo> map;
    private final CellKey point;

    /**
     * Creates a CellInfoMap
     *
     * @param point
     *          Cell position
     */
    CellInfoMap( CellKey point ) {
      this.point = point;
      this.map = new HashMap<>();
    }

    @Override
	public int size() {
      return this.map.size();
    }

    @Override
	public void trimToSize() {
      // empty
    }

    @Override
	public void clear() {
      this.map.clear();
    }

    @Override
	public CellInfo create( int[] pos ) {
      CellKey key = this.point.copy();
      return map.computeIfAbsent(key, k -> new CellInfo( 0 ));
    }

    @Override
	public CellInfo lookup( int[] pos ) {
      CellKey key = CellKey.Generator.newCellKey( pos );
      return this.map.get( key );
    }
  }

  /**
   * Implementation of {@link CellInfoContainer} which uses an {@link ObjectPool} to store {@link CellInfo} Objects.
   *
   * 
   * There is an inner interface (CellKeyMaker) and implementations for 0 through 4 axes that convert the
   * Cell position integer array into a long.
   *
   * 
   * It should be noted that there is an alternate approach. As the executeStripe method is recursively
   * called, at each call it is known which axis is being iterated across and it is known whether or not the Position
   * object for that axis is a List or just an Iterable. It it is a List, then one knows the real size of the axis. If
   * it is an Iterable, then one has to use one of the MAX_AXIS_SIZE values. Given that this information is available
   * when one recursives down to the next executeStripe call, the Cell ordinal, the position integer array
   * could converted to an long, could be generated on the call stack!! Just a thought for the future.
   */
  static class CellInfoPool implements CellInfoContainer {
    /**
     * The maximum number of Members, 2,147,483,647, that can be any given Axis when the number of Axes is 2.
     */
    protected static final long MAX_AXIS_SIZE_2 = 2147483647;
    /**
     * The maximum number of Members, 2,000,000, that can be any given Axis when the number of Axes is 3.
     */
    protected static final long MAX_AXIS_SIZE_3 = 2000000;
    /**
     * The maximum number of Members, 50,000, that can be any given Axis when the number of Axes is 4.
     */
    protected static final long MAX_AXIS_SIZE_4 = 50000;

    /**
     * Implementations of CellKeyMaker convert the Cell position integer array to a long.
     *
     * 
     * Generates a long ordinal based upon the values of the integers stored in the cell position array. With this
     * mechanism, the Cell information can be stored using a long key (rather than the array integer of positions) thus
     * saving memory. The trick is to use a 'large number' per axis in order to convert from position array to long key
     * where the 'large number' is greater than the number of members in the axis. The largest 'long' is
     * java.lang.Long.MAX_VALUE which is 9,223,372,036,854,776,000. The product of the maximum number of members per
     * axis must be less than this maximum 'long' value (otherwise one gets hashing collisions).
     * 
     *
     * 
     * For a single axis, the maximum number of members is equal to the max 'long' number, 9,223,372,036,854,776,000.
     *
     * 
     * For two axes, the maximum number of members is the square root of the max 'long' number,
     * 9,223,372,036,854,776,000, which is slightly bigger than 2,147,483,647 (which is the maximum integer).
     *
     * 
     * For three axes, the maximum number of members per axis is the cube root of the max 'long' which is about
     * 2,000,000.
     *
     * 
     * For four axes the forth root is about 50,000.
     *
     * 
     * For five or more axes, the maximum number of members per axis based upon the root of the maximum 'long' number,
     * start getting too small to guarantee that it will be smaller than the number of members on a given axis and so we
     * must resort to the Map-base Cell container.
     */
    interface CellKeyMaker {
      long generate( int[] pos );
    }

    /**
     * For axis of size 0.
     */
    static class Zero implements CellKeyMaker {
      @Override
	public long generate( int[] pos ) {
        return 0;
      }
    }

    /**
     * For axis of size 1.
     */
    static class One implements CellKeyMaker {
      @Override
	public long generate( int[] pos ) {
        return pos[0];
      }
    }

    /**
     * For axis of size 2.
     */
    static class Two implements CellKeyMaker {
      @Override
	public long generate( int[] pos ) {
        long l = pos[0];
        l += ( MAX_AXIS_SIZE_2 * pos[1] );
        return l;
      }
    }

    /**
     * For axis of size 3.
     */
    static class Three implements CellKeyMaker {
      @Override
	public long generate( int[] pos ) {
        long l = pos[0];
        l += ( MAX_AXIS_SIZE_3 * pos[1] );
        l += ( MAX_AXIS_SIZE_3 * MAX_AXIS_SIZE_3 * pos[2] );
        return l;
      }
    }

    /**
     * For axis of size 4.
     */
    static class Four implements CellKeyMaker {
      @Override
	public long generate( int[] pos ) {
        long l = pos[0];
        l += ( MAX_AXIS_SIZE_4 * pos[1] );
        l += ( MAX_AXIS_SIZE_4 * MAX_AXIS_SIZE_4 * pos[2] );
        l += ( MAX_AXIS_SIZE_4 * MAX_AXIS_SIZE_4 * MAX_AXIS_SIZE_4 * pos[3] );
        return l;
      }
    }

    private final ObjectPool<CellInfo> cellInfoPool;
    private final CellKeyMaker cellKeyMaker;

    CellInfoPool( int axisLength ) {
      this.cellInfoPool = new ObjectPool<>();
      this.cellKeyMaker = createCellKeyMaker( axisLength );
    }

    CellInfoPool( int axisLength, int initialSize ) {
      this.cellInfoPool = new ObjectPool<>( initialSize );
      this.cellKeyMaker = createCellKeyMaker( axisLength );
    }

    private static CellKeyMaker createCellKeyMaker( int axisLength ) {
      switch ( axisLength ) {
        case 0:
          return new Zero();
        case 1:
          return new One();
        case 2:
          return new Two();
        case 3:
          return new Three();
        case 4:
          return new Four();
        default:
          throw new RolapRuntimeException( "Creating CellInfoPool with axisLength=" + axisLength );
      }
    }

    @Override
	public int size() {
      return this.cellInfoPool.size();
    }

    @Override
	public void trimToSize() {
      this.cellInfoPool.trimToSize();
    }

    @Override
	public void clear() {
      this.cellInfoPool.clear();
    }

    @Override
	public CellInfo create( int[] pos ) {
      long key = this.cellKeyMaker.generate( pos );
      return this.cellInfoPool.add( new CellInfo( key ) );
    }

    @Override
	public CellInfo lookup( int[] pos ) {
      return create(pos);
    }
  }

  static TupleList mergeAxes( TupleList axis1, TupleIterable axis2, boolean ordered ) {
    if ( axis1.isEmpty() && axis2 instanceof TupleList tupleList ) {
      return tupleList;
    }
    Set<List<Member>> set = new HashSet<>();
    TupleList list = TupleCollections.createList( axis2.getArity() );
    for ( List<Member> tuple : axis1 ) {
      if ( set.add( tuple ) ) {
        list.add( tuple );
      }
    }
    int halfWay = list.size();
    for ( List<Member> tuple : axis2 ) {
      if ( set.add( tuple ) ) {
        list.add( tuple );
      }
    }

    // if there are unique members on both axes and no order function,
    // sort the list to ensure default order
    if ( halfWay > 0 && halfWay < list.size() && !ordered ) {
      list = Sorter.hierarchizeTupleList( list, false );
    }

    return list;
  }

}
