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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager;
import org.eclipse.daanse.rolap.common.agg.SegmentWithData;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.junit.jupiter.api.Test;

/**
 * The rollup publication opens a load slot whose ONLY completer is
 * cacheLoaded - a throw between the registration and that call must fail
 * the slot, or the segment id serves an eternally pending future to
 * every later reader.
 */
class BatchingCellReaderRollupPublishTest {

    @Test
    void aThrowBetweenRegistrationAndCacheLoadedFailsTheSlot() {
        SegmentCacheManager cacheMgr = mock(SegmentCacheManager.class);
        RolapStar star = mock(RolapStar.class);
        SegmentWithData segmentWithData = mock(SegmentWithData.class);
        when(segmentWithData.getStar()).thenReturn(star);
        SegmentHeader header = mock(SegmentHeader.class);
        SegmentBody body = mock(SegmentBody.class);
        IllegalStateException boom = new IllegalStateException("event enqueue blew up");
        doThrow(boom).when(cacheMgr).cacheLoaded(same(star), same(header), same(body), any());

        org.eclipse.daanse.rolap.common.agg.SegmentCacheManager.SegmentCacheIndexRegistry registry =
            mock(org.eclipse.daanse.rolap.common.agg.SegmentCacheManager.SegmentCacheIndexRegistry.class);
        org.eclipse.daanse.rolap.common.cache.SegmentCacheIndex index =
            mock(org.eclipse.daanse.rolap.common.cache.SegmentCacheIndex.class);
        when(cacheMgr.getIndexRegistry()).thenReturn(registry);
        when(registry.getIndex(star)).thenReturn(index);
        when(segmentWithData.getHeader()).thenReturn(header);

        assertThatThrownBy(() -> org.eclipse.daanse.olap.api.execution.ExecutionContext.where(
                org.eclipse.daanse.olap.execution.ExecutionImpl.NONE.asContext(),
                (Runnable) () -> BatchingCellReader.publishRollup(
                    cacheMgr, segmentWithData, header, body)))
            .isSameAs(boom);

        // the recovery is a SYNCHRONOUS actor command (a second event would
        // be rejected identically in the dominant trigger): run the two
        // captured commands - the second must fail the slot on the index
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<org.eclipse.daanse.olap.api.cache.CacheCommand<Void>> commands =
            org.mockito.ArgumentCaptor.forClass(
                (Class) org.eclipse.daanse.olap.api.cache.CacheCommand.class);
        verify(cacheMgr, org.mockito.Mockito.times(2)).execute(commands.capture());
        for (var command : commands.getAllValues()) {
            try {
                command.call();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        verify(index).loadFailed(same(header), same(boom));
    }

    @Test
    void aCleanPublicationNeverFailsTheSlot() {
        SegmentCacheManager cacheMgr = mock(SegmentCacheManager.class);
        RolapStar star = mock(RolapStar.class);
        SegmentWithData segmentWithData = mock(SegmentWithData.class);
        when(segmentWithData.getStar()).thenReturn(star);
        SegmentHeader header = mock(SegmentHeader.class);
        SegmentBody body = mock(SegmentBody.class);

        org.eclipse.daanse.olap.api.execution.ExecutionContext.where(
            org.eclipse.daanse.olap.execution.ExecutionImpl.NONE.asContext(),
            (Runnable) () -> BatchingCellReader.publishRollup(
                cacheMgr, segmentWithData, header, body));

        verify(cacheMgr).cacheLoaded(same(star), same(header), same(body), any());
        verify(cacheMgr, never()).loadFailed(any(), any(), any());
    }
}
