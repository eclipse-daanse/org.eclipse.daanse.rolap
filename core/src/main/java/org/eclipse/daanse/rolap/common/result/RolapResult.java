/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.result;

import java.util.Arrays;

import org.eclipse.daanse.olap.api.access.HierarchyAccess;
import org.eclipse.daanse.olap.api.agg.OlapAggregationManager;
import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.CellReader;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.evaluator.CalculableMember;
import org.eclipse.daanse.olap.evaluator.EvaluatorImpl;
import org.eclipse.daanse.olap.result.CellInfo;
import org.eclipse.daanse.olap.result.ResultEvaluatorRoot;
import org.eclipse.daanse.olap.result.ResultImpl;
import org.eclipse.daanse.olap.result.ValueFormatter;
import org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.evaluator.RolapDependencyTestingEvaluator;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluatorRoot;
import org.eclipse.daanse.rolap.common.evaluator.RolapInterceptableEvaluator;
import org.eclipse.daanse.rolap.element.CompoundSlicerRolapMember;
import org.eclipse.daanse.rolap.element.RolapBaseCubeMeasure;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapMeasure;

/**
 * The relational result: {@link ResultImpl} with the answers only the
 * relational provider can give. Cells are read through a
 * {@link BatchingCellReader} that collects cell requests and loads them from
 * the aggregation manager between passes; rollup policies and compound
 * slicers are carried by ROLAP members; the cell is a {@link RolapCell}.
 */
public class RolapResult extends ResultImpl {

    private BatchingCellReader batchingReader;

    public RolapResult( final Execution execution, boolean execute ) {
        super( execution, execute );
    }

    private OlapAggregationManager aggregationManager() {
        AbstractBasicContext abc = (AbstractBasicContext) execution.getDaanseStatement().getDaanseConnection().getContext();
        return abc.getAggregationManager();
    }

    @Override
    protected EvaluatorImpl createEvaluator() {
        final int expDeps = execution.getDaanseStatement().getDaanseConnection().getContext().getConfig()
                .testExpDependencies();
        if ( expDeps > 0 ) {
            return new RolapDependencyTestingEvaluator( this, expDeps );
        }
        final RolapResultEvaluatorRoot root = new RolapResultEvaluatorRoot( this );
        if ( statement.getProfileHandler() != null ) {
            return new RolapInterceptableEvaluator( root );
        }
        return new RolapEvaluator( root );
    }

    @Override
    protected CellReader createCellReader( Cube cube ) {
        batchingReader = new BatchingCellReader( execution, (RolapCube) cube, aggregationManager() );
        return batchingReader;
    }

    @Override
    protected CellReader createAggregatingCellReader() {
        return ( (AggregationManager) aggregationManager() ).getCacheCellReader();
    }

    @Override
    protected Cell createCell( int[] pos, CellInfo ci ) {
        return new RolapCell( this, pos, ci );
    }

    @Override
    protected boolean loadPending() {
        return batchingReader.loadAggregations();
    }

    @Override
    protected void markDirty() {
        batchingReader.setDirty( true );
    }

    @Override
    protected void tracePhase() {
        execution.tracePhase( batchingReader.getHitCount(), batchingReader.getMissCount(),
                batchingReader.getPendingCount() );
    }

    @Override
    protected void publishCounters() {
        execution.setCellCacheHitCount( batchingReader.getHitCount() );
        execution.setCellCacheMissCount( batchingReader.getMissCount() );
        execution.setCellCachePendingCount( batchingReader.getPendingCount() );
    }

    @Override
    protected void beforeExecute( Cube cube ) {
        // only has an effect if caching has been disabled
        ( (RolapCube) cube ).clearCachedAggregations();
    }

    @Override
    protected Hierarchy measuresHierarchy( Cube cube ) {
        return ( (RolapCube) cube ).getMeasuresHierarchy();
    }

    @Override
    protected ValueFormatter formatterFor( Member measure ) {
        return measure instanceof RolapMeasure rolapMeasure ? rolapMeasure.getFormatter() : null;
    }

    @Override
    protected boolean needsDistinctRewrite( Member measure ) {
        return measure instanceof RolapBaseCubeMeasure baseCubeMeasure
                && baseCubeMeasure.getAggregator() == DistinctCountAggregator.INSTANCE;
    }

    @Override
    protected Member limitedRollupMember( Member source, Expression exp, HierarchyAccess access ) {
        return new RolapHierarchy.LimitedRollupMember( (RolapCubeMember) source, exp, access );
    }

    @Override
    protected Member compoundSlicerPlaceholder( Member member, Calc calc, ValueFormatter formatter, TupleList tuples,
            int solveOrder ) {
        CompoundSlicerRolapMember placeholder = new CompoundSlicerRolapMember(
                (RolapMember) member.getHierarchy().getNullMember(), calc, formatter, tuples, solveOrder );
        placeholder.setProperty( StandardProperty.FORMAT_STRING.getName(),
                member.getPropertyValue( StandardProperty.FORMAT_STRING.getName() ) );
        placeholder.setProperty( StandardProperty.FORMAT_EXP_PARSED.getName(),
                member.getPropertyValue( StandardProperty.FORMAT_EXP_PARSED.getName() ) );
        return placeholder;
    }

    @Override
    protected boolean onEvalDepthExceeded( EvaluatorImpl evaluator, int count ) {
        if ( evaluator instanceof RolapDependencyTestingEvaluator ) {
            // The dependency testing evaluator can trigger new requests every
            // cycle: run it as normal for the first N times, then disabled.
            ( (RolapDependencyTestingEvaluator.DteRoot) evaluator.getRoot() ).disabled = true;
            return count <= maxEvalDepth() * 2;
        }
        return false;
    }

    @Override
    public RolapCube getCube() {
        return (RolapCube) super.getCube();
    }

    @Override
    public RolapMember[] getCellMembers( int[] pos ) {
        final Member[] members = super.getCellMembers( pos );
        return Arrays.copyOf( members, members.length, RolapMember[].class );
    }

    /**
     * The result root with the relational answers: the scenario member and
     * the hierarchy-usage naming of default members.
     */
    public static class RolapResultEvaluatorRoot extends ResultEvaluatorRoot {

        public RolapResultEvaluatorRoot( RolapResult result ) {
            super( result );
        }

        @Override
        protected CalculableMember scenarioMemberFor( Hierarchy hierarchy ) {
            return RolapEvaluatorRoot.scenarioMember( connection, hierarchy );
        }

        @Override
        protected void nameDefaultMember( Cube cube, Hierarchy hierarchy, CalculableMember defaultMember ) {
            RolapEvaluatorRoot.nameByUsage( cube, hierarchy, defaultMember );
        }
    }
}
