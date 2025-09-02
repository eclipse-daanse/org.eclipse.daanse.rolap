/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2020 Hitachi Vantara.
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

package org.eclipse.daanse.rolap.common.agg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.daanse.olap.api.CacheCommand;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Locus;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.server.ExecutionImpl;
import org.eclipse.daanse.olap.server.LocusImpl;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class SegmentCacheManagerTest {

  @Mock private RolapContext context;
  private Locus locus = new LocusImpl( new ExecutionImpl( null, Optional.empty() ), "component", "message" );
  private ExecutorService executor = Executors.newFixedThreadPool( 15 );

  @BeforeEach
  public void beforeEach() throws Exception {
    MockitoAnnotations.openMocks( this );
    //when(context.getConfig()).thenReturn(new TestConfig());
    when(context.
    getConfigValue(ConfigConstants.SEGMENT_CACHE_MANAGER_NUMBER_SQL_THREADS,
            ConfigConstants.SEGMENT_CACHE_MANAGER_NUMBER_SQL_THREADS_DEFAULT_VALUE, Integer.class)).thenReturn(100);
    when(context.getConfigValue(ConfigConstants.DISABLE_LOCAL_SEGMENT_CACHE, ConfigConstants.DISABLE_LOCAL_SEGMENT_CACHE_DEFAULT_VALUE, Boolean.class)).thenReturn(false);
    when(context.getConfigValue(ConfigConstants.SEGMENT_CACHE_MANAGER_NUMBER_CACHE_THREADS, ConfigConstants.SEGMENT_CACHE_MANAGER_NUMBER_CACHE_THREADS_DEFAULT_VALUE, Integer.class)).thenReturn(100);
    when(context.getConfigValue(ConfigConstants.DISABLE_CACHING, ConfigConstants.DISABLE_CACHING_DEFAULT_VALUE, Boolean.class)).thenReturn(false);
  }

  @Test
  void testCommandExecution() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch( 1 );
    SegmentCacheManager man = new SegmentCacheManager( context );
    man.execute( new MockCommand( latch::countDown ) );

    latch.await( 2000, TimeUnit.MILLISECONDS );
    assertEquals( latch.getCount(), 0 );
  }

  @Test
  void testShutdownEndOfQueue() throws InterruptedException {
    BlockingQueue execResults = new ArrayBlockingQueue( 10 );
    SegmentCacheManager man = new SegmentCacheManager( context );
    // add 10 commands to the exec queue
    executeNtimes( execResults, man, 10 );
    // then shut down
    executor.submit( man::shutdown );
    List<Object> results = new ArrayList<>();
    // collect the results.  All should have completed successfully with "done".
    for ( int i = 0; i < 10; i++ ) {
      Object val = execResults.poll( 200, TimeUnit.MILLISECONDS );
      assertEquals( "done", val );
      results.add( val );
    }
    assertEquals( 10, results.size() );

  }

  @Test
  void testShutdownMiddleOfQueue() throws InterruptedException {
    BlockingQueue<Object> execResults = new ArrayBlockingQueue( 20 );

    SegmentCacheManager man = new SegmentCacheManager( context );
    // submit 2 commands for exec
    executeNtimes( execResults, man, 2 );
    // submit shutdown
    executor.submit( man::shutdown );
    // submit 18 commands post-shutdown
    executeNtimes( execResults, man, 18 );

    // gather results.  There should be 20 full results, with those following shutdown containing an exception.
    List<Object> results = new ArrayList<>();
    for ( int i = 0; i < 20; i++ ) {
      results.add( execResults.poll( 2000, TimeUnit.MILLISECONDS ) );
    }
    assertEquals( 20, results.size() );
    assertEquals( "done", results.get( 0 ) );
    assertTrue( results.get( 19 ) instanceof OlapRuntimeException );
  }

  private void executeNtimes( BlockingQueue<Object> queue, SegmentCacheManager man, int n ) {
    for ( int i = 0; i < n; i++ ) {
      executor.submit( () ->
      {
        try {
          putInQueue( queue, man.execute( new MockCommand( this::sleep ) ) );
        } catch ( RuntimeException re ) {
          putInQueue( queue, re );
        }

      } );
    }
  }

  private void putInQueue( BlockingQueue<Object> queue, Object object ) {
    try {
      queue.put( object );
    } catch ( InterruptedException e ) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException( e );
    }
  }

  @SuppressWarnings( "java:S2925" ) // using sleep here to simulate long running command
  private void sleep() {
    try {
      Thread.sleep( 100 );
    } catch ( InterruptedException e ) {
      throw new IllegalStateException( e );
    }
  }

  private class MockCommand implements CacheCommand<Object> {
    private final Runnable runnable;

    MockCommand( Runnable runnable ) {
      this.runnable = runnable;
    }

    @Override public Locus getLocus() {
      return locus;
    }

    @Override public Object call() {
      runnable.run();
      return "done";
    }
  }
}
