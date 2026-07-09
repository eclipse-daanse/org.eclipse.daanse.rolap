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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapAggregationManager;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution;
import org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner;
import org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Verifies the slicer-tuple lift in {@code SqlContextConstraint.contextContribution} (the
 * disjoined-tuples family — a slicer whose calc members each materialize a disjoint slicer
 * {@code TupleList}):
 * <ul>
 * <li>{@code toContribution} replays one tuple branch per list — the context columns NOT covered by
 *     that list's tuple bitkey re-emitted per list (no dedup), then the list's translated tuple
 *     predicate attached to its first join table;</li>
 * <li>the optimized slicer tuples append last after the disjoined lists, with the
 *     {@code disjointSlicerTuples} flag threaded into {@code makeContextConstraintSet} as its
 *     {@code isTuple} argument;</li>
 * <li>a throwing expansion maps to an empty contribution.</li>
 * </ul>
 *
 * <p>Harness as in {@code ContextContributionLazySlicerExpansionTest}: inline-mocked constraint
 * with reflectively injected state, static seams stubbed.
 */
class SqlContextConstraintSlicerTupleLiftTest {

    private final Predicate tuplePredicate1 = Predicates.raw("TUPLES_1");
    private final Predicate tuplePredicate2 = Predicates.raw("TUPLES_2");

    @Test
    void executedPathReplaysTheTupleBranchForDisjoinedLists() throws Exception {
        RolapEvaluator evaluator = mockEvaluator();
        SqlContextConstraint scc = constraintWith(evaluator);
        RolapCube cube = mock(RolapCube.class);
        RolapStar.Table timeTable = mock(RolapStar.Table.class);
        TupleList disjoined = tupleList(cube, timeTable, 3);

        try (MockedStatic<ContextConstraintWriter> writer = mockStatic(ContextConstraintWriter.class);
             MockedStatic<RolapAggregationManager> agg = mockStatic(RolapAggregationManager.class);
             MockedStatic<StarPredicateTranslator> translator = mockStatic(StarPredicateTranslator.class)) {
            stubSeams(writer, agg, structWith(disjoined), new RolapStar.Column[0], new Object[0]);
            stubTuplePredicate(writer, translator, cube, disjoined, tuplePredicate1, 3);

            Optional<ConstraintContribution> c = scc.toContribution(cube, null);
            assertThat(c)
                .as("executed reads now replay the disjoined tuple lists (no more bail)")
                .isPresent();
            assertThat(c.get().orderedPredicates()).hasSize(1);
            assertThat(c.get().orderedPredicates().get(0).predicate()).isSameAs(tuplePredicate1);
        }
    }

    @Test
    void liftedTwinReplaysOneTupleBranchPerListWithPerListColumnConjuncts() throws Exception {
        RolapEvaluator evaluator = mockEvaluator();
        SqlContextConstraint scc = constraintWith(evaluator);
        RolapCube cube = mock(RolapCube.class);
        RolapStar.Table timeTable = mock(RolapStar.Table.class);
        RolapStar.Table productTable = mock(RolapStar.Table.class);
        TupleList list1 = tupleList(cube, timeTable, 3);
        TupleList list2 = tupleList(cube, productTable, 4);

        // One single-valued context column NOT covered by either list's bitkey: its simple
        // conjunct is re-emitted per list (no dedup).
        RolapStar.Table statusTable = mock(RolapStar.Table.class);
        RolapStar.Column statusColumn = mock(RolapStar.Column.class);
        when(statusColumn.getDatatype()).thenReturn(Datatype.VARCHAR);
        when(statusColumn.getTable()).thenReturn(statusTable);
        when(statusColumn.getBitPosition()).thenReturn(7);

        try (MockedStatic<ContextConstraintWriter> writer = mockStatic(ContextConstraintWriter.class);
             MockedStatic<RolapAggregationManager> agg = mockStatic(RolapAggregationManager.class);
             MockedStatic<StarPredicateTranslator> translator = mockStatic(StarPredicateTranslator.class);
             MockedStatic<JoinPlanner> joinPlanner = mockStatic(JoinPlanner.class)) {
            stubSeams(writer, agg, structWith(list1, list2),
                new RolapStar.Column[] {statusColumn}, new Object[] {"Shipped"});
            stubTuplePredicate(writer, translator, cube, list1, tuplePredicate1, 3);
            stubTuplePredicate(writer, translator, cube, list2, tuplePredicate2, 4);
            joinPlanner.when(() -> JoinPlanner.expressionFor(statusColumn))
                .thenReturn(org.eclipse.daanse.sql.statement.api.Expressions.star());

            Optional<ConstraintContribution> lifted = scc.toContribution(cube, null);

            assertThat(lifted).isPresent();
            ConstraintContribution c = lifted.get();
            // Per-list replay order: [status col, list1 tuples, status col again, list2 tuples].
            assertThat(c.orderedPredicates()).hasSize(4);
            assertThat(c.orderedPredicates().get(0).table()).isSameAs(statusTable);
            assertThat(c.orderedPredicates().get(1).table()).isSameAs(timeTable);
            assertThat(c.orderedPredicates().get(1).predicate()).isSameAs(tuplePredicate1);
            assertThat(c.orderedPredicates().get(2).table()).isSameAs(statusTable);
            assertThat(c.orderedPredicates().get(3).table()).isSameAs(productTable);
            assertThat(c.orderedPredicates().get(3).predicate()).isSameAs(tuplePredicate2);
            assertThat(c.joinTables()).containsExactly(
                statusTable, timeTable, statusTable, productTable);
            assertThat(c.where()).isPresent();
        }
    }

    @Test
    void columnCoveredByTheListBitKeyIsSkipped() throws Exception {
        RolapEvaluator evaluator = mockEvaluator();
        SqlContextConstraint scc = constraintWith(evaluator);
        RolapCube cube = mock(RolapCube.class);
        RolapStar.Table timeTable = mock(RolapStar.Table.class);
        TupleList list1 = tupleList(cube, timeTable, 3);

        RolapStar.Column coveredColumn = mock(RolapStar.Column.class);
        when(coveredColumn.getDatatype()).thenReturn(Datatype.VARCHAR);
        when(coveredColumn.getTable()).thenReturn(mock(RolapStar.Table.class));
        when(coveredColumn.getBitPosition()).thenReturn(3); // == the list's constrained bit

        try (MockedStatic<ContextConstraintWriter> writer = mockStatic(ContextConstraintWriter.class);
             MockedStatic<RolapAggregationManager> agg = mockStatic(RolapAggregationManager.class);
             MockedStatic<StarPredicateTranslator> translator = mockStatic(StarPredicateTranslator.class)) {
            stubSeams(writer, agg, structWith(list1),
                new RolapStar.Column[] {coveredColumn}, new Object[] {"1997"});
            stubTuplePredicate(writer, translator, cube, list1, tuplePredicate1, 3);

            Optional<ConstraintContribution> lifted = scc.toContribution(cube, null);

            assertThat(lifted).isPresent();
            // Only the tuple predicate: the covered column is constrained BY it, never twice.
            assertThat(lifted.get().orderedPredicates()).hasSize(1);
            assertThat(lifted.get().orderedPredicates().get(0).predicate())
                .isSameAs(tuplePredicate1);
        }
    }

    /**
     * When both sources are live: the disjoined lists first, the optimized slicer tuples appended
     * last, and the {@code disjointSlicerTuples} flag threaded as {@code makeContextConstraintSet}'s
     * {@code isTuple} argument.
     */
    @Test
    void optimizedSlicerTuplesAppendLastAndThreadTheIsTupleFlag() throws Exception {
        RolapEvaluator evaluator = mockEvaluator();
        RolapCube cube = mock(RolapCube.class);
        RolapStar.Table timeTable = mock(RolapStar.Table.class);
        RolapStar.Table marketTable = mock(RolapStar.Table.class);
        TupleList disjoined = tupleList(cube, timeTable, 3);
        TupleList optimized = tupleList(cube, marketTable, 4);
        when(evaluator.getOptimizedSlicerTuples(any())).thenReturn(optimized);
        when(optimized.isEmpty()).thenReturn(false);
        SqlContextConstraint scc = constraintWith(evaluator);

        try (MockedStatic<ContextConstraintWriter> writer = mockStatic(ContextConstraintWriter.class);
             MockedStatic<RolapAggregationManager> agg = mockStatic(RolapAggregationManager.class);
             MockedStatic<SlicerAnalyzer> slicer = mockStatic(SlicerAnalyzer.class);
             MockedStatic<StarPredicateTranslator> translator = mockStatic(StarPredicateTranslator.class)) {
            stubSeams(writer, agg, structWith(disjoined), new RolapStar.Column[0], new Object[0]);
            slicer.when(() -> SlicerAnalyzer.isDisjointTuple(optimized)).thenReturn(true);
            stubTuplePredicate(writer, translator, cube, disjoined, tuplePredicate1, 3);
            stubTuplePredicate(writer, translator, cube, optimized, tuplePredicate2, 4);

            Optional<ConstraintContribution> lifted = scc.toContribution(cube, null);

            assertThat(lifted).isPresent();
            assertThat(lifted.get().orderedPredicates()).hasSize(2);
            assertThat(lifted.get().orderedPredicates().get(0).predicate())
                .isSameAs(tuplePredicate1);
            assertThat(lifted.get().orderedPredicates().get(1).predicate())
                .isSameAs(tuplePredicate2);
            // isTuple = the disjointSlicerTuples flag.
            writer.verify(() -> ContextConstraintWriter.makeContextConstraintSet(
                any(), anyBoolean(), eq(true)));
        }
    }

    @Test
    void aThrowingExpansionMapsToAnEmptyContribution() throws Exception {
        RolapEvaluator evaluator = mockEvaluator();
        SqlContextConstraint scc = constraintWith(evaluator);
        RolapCube cube = mock(RolapCube.class);

        try (MockedStatic<ContextConstraintWriter> writer = mockStatic(ContextConstraintWriter.class)) {
            // A throwing expansion: the shared catch maps it to an empty contribution, never
            // propagating into the read.
            writer.when(() -> ContextConstraintWriter.makeContextConstraintSet(
                    any(), anyBoolean(), anyBoolean()))
                .thenThrow(new IllegalStateException("boom"));

            assertThat(scc.toContribution(cube, null)).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------------------------

    /** A context evaluator whose only member is a stored measure (no calc gate, no slicer
     *  members); slicer tuples per test. */
    private static RolapEvaluator mockEvaluator() {
        RolapEvaluator evaluator = mock(RolapEvaluator.class);
        when(evaluator.push()).thenReturn(evaluator);
        when(evaluator.getMembers()).thenReturn(new Member[] {mock(RolapStoredMeasure.class)});
        return evaluator;
    }

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

    /** The non-slicer static seams of the translation: the (disjoined-lists-carrying) expanded
     *  context set, no role constraints, and a cell request carrying exactly
     *  {@code columns}/{@code values}. */
    private static void stubSeams(MockedStatic<ContextConstraintWriter> writer,
        MockedStatic<RolapAggregationManager> agg, TupleConstraintStruct expanded,
        RolapStar.Column[] columns, Object[] values) {
        writer.when(() -> ContextConstraintWriter.makeContextConstraintSet(
                any(), anyBoolean(), anyBoolean()))
            .thenReturn(expanded);
        writer.when(() -> ContextConstraintWriter.getRoleConstraintMembers(any(), any()))
            .thenReturn(Map.of());
        CellRequest request = mock(CellRequest.class);
        when(request.getConstrainedColumns()).thenReturn(columns);
        when(request.getSingleValues()).thenReturn(values);
        agg.when(() -> RolapAggregationManager.makeRequest(any(Member[].class)))
            .thenReturn(request);
    }

    private static TupleConstraintStruct structWith(TupleList... lists) {
        TupleConstraintStruct struct = new TupleConstraintStruct();
        for (TupleList list : lists) {
            struct.addTupleList(list);
        }
        return struct;
    }

    /** A one-tuple/one-member slicer TupleList whose member's level key column (bit {@code bit})
     *  lives on {@code table} — enough for the join-table walk and the bitkey coverage checks. */
    private static TupleList tupleList(RolapCube cube, RolapStar.Table table, int bit) {
        RolapStar.Column column = mock(RolapStar.Column.class);
        when(column.getTable()).thenReturn(table);
        when(column.getBitPosition()).thenReturn(bit);
        RolapCubeLevel level = mock(RolapCubeLevel.class);
        when(level.getBaseStarKeyColumn(cube)).thenReturn(column);
        when(level.isUnique()).thenReturn(true);
        RolapMember member = mock(RolapMember.class);
        when(member.getLevel()).thenReturn(level);
        TupleList list = mock(TupleList.class);
        when(list.iterator()).thenAnswer(
            inv -> java.util.List.of(java.util.List.<Member>of(member)).iterator());
        return list;
    }

    /** The list's pure StarPredicate (bitkey covering exactly {@code coveredBit} — the list
     *  member's key column bit, as {@code tupleList} was built with) and its dialect-free
     *  translation. */
    private void stubTuplePredicate(MockedStatic<ContextConstraintWriter> writer,
        MockedStatic<StarPredicateTranslator> translator, RolapCube cube, TupleList list,
        Predicate translated, int coveredBit) {
        StarPredicate starPredicate = mock(StarPredicate.class);
        BitKey bitKey = mock(BitKey.class);
        when(bitKey.get(anyInt())).thenAnswer(inv -> (int) inv.getArgument(0) == coveredBit);
        when(starPredicate.getConstrainedColumnBitKey()).thenReturn(bitKey);
        writer.when(() -> ContextConstraintWriter.getSlicerTuplesPredicatePure(same(list), same(cube)))
            .thenReturn(starPredicate);
        translator.when(() -> StarPredicateTranslator.toPredicate(starPredicate))
            .thenReturn(translated);
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = SqlContextConstraint.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
