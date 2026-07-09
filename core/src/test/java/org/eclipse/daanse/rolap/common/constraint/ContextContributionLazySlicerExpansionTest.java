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
package org.eclipse.daanse.rolap.common.constraint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;

import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.rolap.common.RolapAggregationManager;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Verifies the lazy slicer expansion in {@code SqlContextConstraint.contextContribution}: the
 * expansion ({@code SlicerAnalyzer.getSlicerMemberMap}) is computed only inside the
 * constrained-columns loop, at the first processed constrained column — never with zero
 * constrained columns, and at most once. This holds regardless of the injected result root.
 *
 * <p>Harness: the constraint is an inline Mockito mock (no evaluator-heavy constructor) whose
 * {@code evaluator}/{@code strict} fields are injected reflectively; the static seams the
 * translation walks ({@code ContextConstraintWriter}, {@code RolapAggregationManager},
 * {@code SlicerAnalyzer}, {@code JoinPlanner}) are {@code mockStatic}-stubbed, so the test observes
 * the expansion call itself, not a proxy.
 */
class ContextContributionLazySlicerExpansionTest {

    @Test
    void zeroConstrainedColumnsNeverExpandTheSlicerMap() throws Exception {
        SqlContextConstraint scc = constraintWith(mockEvaluator());

        try (MockedStatic<ContextConstraintWriter> writer = mockStatic(ContextConstraintWriter.class);
             MockedStatic<RolapAggregationManager> agg = mockStatic(RolapAggregationManager.class);
             MockedStatic<SlicerAnalyzer> slicer = mockStatic(SlicerAnalyzer.class)) {
            stubExpansionSeams(writer, agg, new RolapStar.Column[0], new Object[0]);

            assertThat(scc.toContribution(mock(RolapCube.class), null))
                .as("zero-column context translates to the EMPTY (present, unrestricted) contribution")
                .isPresent();

            // No constrained column was processed, so the slicer expansion never fired.
            slicer.verifyNoInteractions();
        }
    }

    @Test
    void firstConstrainedColumnStillExpandsTheSlicerMapOnce() throws Exception {
        SqlContextConstraint scc = constraintWith(mockEvaluator());

        RolapStar.Column column = mock(RolapStar.Column.class);
        when(column.getDatatype()).thenReturn(Datatype.VARCHAR);
        when(column.getTable()).thenReturn(mock(RolapStar.Table.class));

        try (MockedStatic<ContextConstraintWriter> writer = mockStatic(ContextConstraintWriter.class);
             MockedStatic<RolapAggregationManager> agg = mockStatic(RolapAggregationManager.class);
             MockedStatic<SlicerAnalyzer> slicer = mockStatic(SlicerAnalyzer.class);
             MockedStatic<JoinPlanner> joinPlanner = mockStatic(JoinPlanner.class)) {
            stubExpansionSeams(writer, agg, new RolapStar.Column[] {column}, new Object[] {"1997"});
            // Null-key-tolerant empty map (the loop probes it with the mock column's null
            // expression; Map.of() would NPE on containsKey(null)).
            slicer.when(() -> SlicerAnalyzer.getSlicerMemberMap(any()))
                .thenReturn(new java.util.HashMap<>());
            // A real node (the SqlExpression hierarchy is sealed — not mockable); the pin only
            // needs SOME column node for the value predicate to build on.
            joinPlanner.when(() -> JoinPlanner.expressionFor(column)).thenReturn(
                org.eclipse.daanse.sql.statement.api.Expressions.star());

            assertThat(scc.toContribution(mock(RolapCube.class), null)).isPresent();

            // The loop's first processed column fetches the map, exactly once.
            slicer.verify(() -> SlicerAnalyzer.getSlicerMemberMap(any()));
            slicer.verifyNoMoreInteractions();
        }
    }

    /**
     * The lifted read expands at its first processed constrained column regardless of the
     * per-query set-evaluation state — here with a {@code RolapResult} root injected, so a
     * regression re-introducing root-dependent bailing on this route fails here.
     */
    @Test
    void executedLiftedReadExpandsWithAResultRootInjected() throws Exception {
        RolapEvaluator evaluator = mockEvaluator();
        Field rootField = RolapEvaluator.class.getDeclaredField("root");
        rootField.setAccessible(true);
        rootField.set(evaluator,
            mock(org.eclipse.daanse.rolap.common.result.RolapResult.RolapResultEvaluatorRoot.class));
        SqlContextConstraint scc = constraintWith(evaluator);

        RolapStar.Column column = mock(RolapStar.Column.class);
        when(column.getDatatype()).thenReturn(Datatype.VARCHAR);
        when(column.getTable()).thenReturn(mock(RolapStar.Table.class));

        try (MockedStatic<ContextConstraintWriter> writer = mockStatic(ContextConstraintWriter.class);
             MockedStatic<RolapAggregationManager> agg = mockStatic(RolapAggregationManager.class);
             MockedStatic<SlicerAnalyzer> slicer = mockStatic(SlicerAnalyzer.class);
             MockedStatic<JoinPlanner> joinPlanner = mockStatic(JoinPlanner.class)) {
            stubExpansionSeams(writer, agg, new RolapStar.Column[] {column}, new Object[] {"1997"});
            slicer.when(() -> SlicerAnalyzer.getSlicerMemberMap(any()))
                .thenReturn(new java.util.HashMap<>());
            joinPlanner.when(() -> JoinPlanner.expressionFor(column)).thenReturn(
                org.eclipse.daanse.sql.statement.api.Expressions.star());

            assertThat(scc.toContribution(mock(RolapCube.class), null))
                .as("the executed LIFTED dispatch expands and translates under a result root")
                .isPresent();

            slicer.verify(() -> SlicerAnalyzer.getSlicerMemberMap(any()));
        }
    }

    /** A context evaluator whose only member is a stored measure: no calc gate, no slicer
     *  members, no slicer tuples. */
    private static RolapEvaluator mockEvaluator() {
        RolapEvaluator evaluator = mock(RolapEvaluator.class);
        when(evaluator.push()).thenReturn(evaluator);
        when(evaluator.getMembers()).thenReturn(new Member[] {mock(RolapStoredMeasure.class)});
        return evaluator;
    }

    /** The 2-arg dispatch on a constructor-less mock: real routing methods, injected
     *  {@code evaluator}/{@code strict} state. */
    private static SqlContextConstraint constraintWith(RolapEvaluator evaluator) throws Exception {
        SqlContextConstraint scc = mock(SqlContextConstraint.class);
        when(scc.toContribution(any(), any())).thenCallRealMethod();
        when(scc.toContribution(any(), any(),
            any(SqlContextConstraint.CalcLift.class))).thenCallRealMethod();
        when(scc.executedCalcLift()).thenCallRealMethod();
        when(scc.getEvaluator()).thenReturn(evaluator);
        inject(scc, "evaluator", evaluator);
        inject(scc, "strict", false);
        return scc;
    }

    /** The non-slicer static seams of the translation: an already-expanded empty context set and
     *  a cell request carrying exactly {@code columns}/{@code values}. */
    private static void stubExpansionSeams(MockedStatic<ContextConstraintWriter> writer,
        MockedStatic<RolapAggregationManager> agg, RolapStar.Column[] columns, Object[] values) {
        writer.when(() -> ContextConstraintWriter.makeContextConstraintSet(
                any(), anyBoolean(), anyBoolean()))
            .thenReturn(new TupleConstraintStruct());
        writer.when(() -> ContextConstraintWriter.getRoleConstraintMembers(any(), any()))
            .thenReturn(Map.of());
        CellRequest request = mock(CellRequest.class);
        when(request.getConstrainedColumns()).thenReturn(columns);
        when(request.getSingleValues()).thenReturn(values);
        agg.when(() -> RolapAggregationManager.makeRequest(any(Member[].class)))
            .thenReturn(request);
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = SqlContextConstraint.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
