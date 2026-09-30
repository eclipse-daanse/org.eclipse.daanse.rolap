/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors: SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.common.agg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.daanse.olap.api.cache.CacheCommand;
import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.api.monitor.EventBus;
import org.eclipse.daanse.olap.api.monitor.event.CellCacheEvent;
import org.eclipse.daanse.olap.api.monitor.event.CellCacheSegmentCreateEvent;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.common.ExecutionConfig;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.rolap.common.cache.SegmentCacheIndex;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * The actor's one thread owns the segment index; both thread guards
 * (SegmentCacheIndexImpl asserts on it, SegmentCacheWorker rejects it)
 * compare against the reference captured at construction. These tests pin
 * that the thread survives whatever an event throws.
 */
class SegmentCacheManagerActorTest {

  @Mock private RolapContext context;
  private AutoCloseable mocks;
  private SegmentCacheManager man;

  @BeforeEach
  void beforeEach() {
    mocks = MockitoAnnotations.openMocks( this );
    when( context.getConfig() ).thenReturn( ExecutionConfig.DEFAULTS );
    man = new SegmentCacheManager( context );
  }

  @AfterEach
  void afterEach() throws Exception {
    man.shutdown();
    mocks.close();
  }

  /**
   * An Error thrown inside an event must not kill the actor thread: the
   * executor would replace the worker, but every guard keeps comparing
   * against the dead original — index asserts fire forever and store I/O
   * on the replacement is no longer rejected.
   */
  @Test
  void actorThreadSurvivesAnErrorInAnEvent() {
    assertThat( onActorThread() ).isSameAs( man.thread );

    final RolapStar poisoned = mock( RolapStar.class );
    when( poisoned.getCatalog() ).thenThrow( new AssertionError( "boom" ) );
    man.loadFailed( poisoned, mock( SegmentHeader.class ), new RuntimeException( "load failed" ) );

    // single-thread FIFO: this command runs after the poisoned event
    assertThat( onActorThread() ).isSameAs( man.thread );
  }

  /**
   * loadSucceeded is the only completer of a pending load slot: if it
   * throws, the slot must be failed with the cause (waiters get the error
   * instead of hanging until their query timeout).
   */
  @Test
  void aFailedPublicationFailsTheLoadSlot() {
    final SegmentCacheIndex index = mock( SegmentCacheIndex.class );
    final SegmentHeader header = mock( SegmentHeader.class );
    final SegmentBody body = mock( SegmentBody.class );
    final RuntimeException boom = new RuntimeException( "publish failed" );
    doThrow( boom ).when( index ).loadSucceeded( header, body );

    assertThatThrownBy( () -> SegmentCacheManager.publishToIndex( index, header, body ) )
        .isSameAs( boom );
    verify( index ).loadFailed( header, boom );
  }

  /** A throwing loadFailed (slot already gone) must not mask the original cause. */
  @Test
  void aFailingFallbackDoesNotMaskTheOriginalCause() {
    final SegmentCacheIndex index = mock( SegmentCacheIndex.class );
    final SegmentHeader header = mock( SegmentHeader.class );
    final RuntimeException boom = new RuntimeException( "publish failed" );
    doThrow( boom ).when( index ).loadSucceeded( header, null );
    doThrow( new IllegalArgumentException( "is not loading" ) )
        .when( index ).loadFailed( header, boom );

    assertThatThrownBy( () -> SegmentCacheManager.publishToIndex( index, header, null ) )
        .isSameAs( boom );
  }

  /**
   * The publication event reports where the segment came from: the SQL
   * load says SQL, the in-memory rollup says ROLLUP. EXTERNAL is reserved
   * for headers arriving from an external store.
   */
  @Test
  void publicationsReportTheirTrueSource() {
    assertThat( sourceReportedFor( CellCacheEvent.Source.SQL ) )
        .isEqualTo( CellCacheEvent.Source.SQL );
    assertThat( sourceReportedFor( CellCacheEvent.Source.ROLLUP ) )
        .isEqualTo( CellCacheEvent.Source.ROLLUP );
  }

  private CellCacheEvent.Source sourceReportedFor( CellCacheEvent.Source source ) {
    final RolapStar star = mock( RolapStar.class, RETURNS_DEEP_STUBS );
    final SegmentHeader header = mock( SegmentHeader.class );
    when( header.getConstrainedColumns() ).thenReturn( List.of() );
    final Execution execution = mock( Execution.class, RETURNS_DEEP_STUBS );
    final ExecutionContext executionContext = mock( ExecutionContext.class );
    when( executionContext.getExecution() ).thenReturn( execution );
    final EventBus monitor =
        execution.getDaanseStatement().getDaanseConnection().getContext().getMonitor();

    ExecutionContext.where( executionContext,
        (Runnable) () -> man.loadSucceeded( star, header, mock( SegmentBody.class ), source ) );

    final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass( Object.class );
    verify( monitor, timeout( 5000 ) ).accept( (org.eclipse.daanse.olap.api.monitor.event.Event) captor.capture() );
    return ( (CellCacheSegmentCreateEvent) captor.getValue() )
        .cellCacheEventCommon().source();
  }

  private Thread onActorThread() {
    return man.execute( ActorCommands.onActor( Thread::currentThread ) );
  }
}
