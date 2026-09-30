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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentPredicate;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager;
import org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Two batches may only merge when their compound predicates agree: the
 * composite batch runs ONE SQL with the detailed batch's compound
 * predicate list, but every summary batch registers segment headers
 * claiming its OWN compound predicates - a merge across different
 * compounds publishes headers whose data was filtered by someone else's
 * WHERE clause. BatchKey.equals already separates such requests into
 * different batches; canBatch is the second gate that must not weld
 * them back together.
 */
class BatchCompoundPredicateTest {

    private final RolapStar star = mock(RolapStar.class);
    private final RolapStar.Measure measure =
        mock(RolapStar.Measure.class, Mockito.RETURNS_DEEP_STUBS);
    private final BitKey bitKey = BitKey.Factory.makeBitKey(4);
    private final BatchLoader loader;

    BatchCompoundPredicateTest() {
        when(star.getAggStars()).thenReturn(List.of());
        when(measure.getStar()).thenReturn(star);
        when(measure.getAggregator().isDistinct()).thenReturn(false);
        RolapCube cube = mock(RolapCube.class);
        // null = the virtual-cube punt: closure columns do not veto here
        when(cube.getClosureColumnBitKey()).thenReturn(null);
        loader = new BatchLoader(mock(ExecutionContext.class),
            mock(SegmentCacheManager.class),
            mock(SqlQueryCapabilities.class), cube);
    }

    private CellRequest request(List<SegmentPredicate> wire,
            List<StarPredicate> predicates) {
        CellRequest request = mock(CellRequest.class);
        when(request.getMeasure()).thenReturn(measure);
        when(request.getConstrainedColumnsBitKey()).thenReturn(bitKey);
        when(request.getConstrainedColumns())
            .thenReturn(new RolapStar.Column[0]);
        when(request.getNumValues()).thenReturn(0);
        when(request.getCompoundPredicates()).thenReturn(wire);
        when(request.getCompoundPredicateList()).thenReturn(predicates);
        return request;
    }

    private BatchLoader.Batch batch(List<SegmentPredicate> wire,
            List<StarPredicate> predicates) {
        CellRequest request = request(wire, predicates);
        BatchLoader.Batch batch = loader.new Batch(request);
        batch.add(request);
        return batch;
    }

    /** Red before: canBatch merged across differing compound predicates. */
    @Test
    void differingCompoundPredicatesDoNotBatch() {
        BatchLoader.Batch plain = batch(List.of(), List.of());
        BatchLoader.Batch compound = batch(
            List.of(new SegmentPredicate.Opaque("compound-a")),
            List.of(mock(StarPredicate.class)));

        assertThat(plain.canBatch(compound))
            .as("a compound-free batch must not absorb a compound one")
            .isFalse();
        assertThat(compound.canBatch(plain))
            .as("a compound batch must not absorb a compound-free one")
            .isFalse();

        BatchLoader.Batch otherCompound = batch(
            List.of(new SegmentPredicate.Opaque("compound-b")),
            List.of(mock(StarPredicate.class)));
        assertThat(compound.canBatch(otherCompound))
            .as("batches with different compound predicates must not merge")
            .isFalse();
    }

    /** Equal (here: absent) compounds keep batching - no over-restriction. */
    @Test
    void equalCompoundPredicatesStillBatch() {
        BatchLoader.Batch first = batch(List.of(), List.of());
        BatchLoader.Batch second = batch(List.of(), List.of());

        assertThat(first.canBatch(second)).isTrue();
        assertThat(second.canBatch(first)).isTrue();
    }
}
