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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.daanse.olap.api.cache.CacheCommand;
import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.common.ExecutionConfig;
import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.util.ByteString;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.catalog.RolapCatalogCache;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Shutdown ordering: work still queued on the actor may submit store
 * operations, so the downstream pools must outlive the actor's drain.
 */
class SegmentCacheManagerShutdownDrainTest {

  @Mock private RolapContext context;
  private SegmentCacheManager man;

  @BeforeEach
  void beforeEach() throws Exception {
    MockitoAnnotations.openMocks( this );
    when( context.getConfig() ).thenReturn( ExecutionConfig.DEFAULTS );
    man = new SegmentCacheManager( context );
  }

  /**
   * A remove event still queued when shutdown starts must reach the
   * external store: the actor drains before the downstream pools are
   * signaled, so its store ops find a live cache executor.
   */
  @Test
  void shutdownDrainsTheActorBeforeTheDownstreamPools() throws Exception {
    final SegmentCache cache = mock( SegmentCache.class );
    when( cache.knownStars() ).thenReturn( Set.of() );
    final RolapCatalogCache catalogCache = mock( RolapCatalogCache.class );
    when( context.getCatalogCache() ).thenReturn( catalogCache );
    when( catalogCache.getCachedCatalogs() ).thenReturn( List.of() );
    man.addExternalCache( cache );

    final CountDownLatch running = new CountDownLatch( 1 );
    final CountDownLatch release = new CountDownLatch( 1 );
    final Thread busy = new Thread( () -> man.execute( command( () -> {
      running.countDown();
      await( release );
    } ) ) );
    busy.start();
    assertThat( running.await( 5, TimeUnit.SECONDS ) ).isTrue();

    final SegmentHeader header = mock( SegmentHeader.class );
    when( header.getUniqueID() ).thenReturn( new ByteString( new byte[] { 1 } ) );
    when( header.getConstrainedColumns() ).thenReturn( List.of() );
    final RolapStar star = mock( RolapStar.class, RETURNS_DEEP_STUBS );
    final Execution execution = mock( Execution.class, RETURNS_DEEP_STUBS );
    final ExecutionContext executionContext = mock( ExecutionContext.class );
    when( executionContext.getExecution() ).thenReturn( execution );
    // queue the remove event behind the busy command
    ExecutionContext.where( executionContext, (Runnable) () -> man.remove( star, header ) );

    final Thread shutter = new Thread( man::shutdown );
    shutter.start();
    // let shutdown reach its executor signals while the actor is still busy
    Thread.sleep( 300 );
    release.countDown();
    shutter.join( 35_000 );
    assertThat( shutter.isAlive() ).isFalse();
    busy.join( 5_000 );

    verify( cache ).remove( header );
  }

  private CacheCommand<Object> command( Runnable runnable ) {
    return ActorCommands.onActor( () -> {
      runnable.run();
      return "done";
    } );
  }

  private void await( CountDownLatch latch ) {
    try {
      if ( !latch.await( 10, TimeUnit.SECONDS ) ) {
        throw new IllegalStateException( "latch timed out" );
      }
    } catch ( InterruptedException e ) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException( e );
    }
  }
}
