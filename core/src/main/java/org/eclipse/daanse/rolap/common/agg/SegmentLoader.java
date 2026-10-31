/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2002-2005 Julian Hyde
 * Copyright (C) 2005-2021 Hitachi Vantara and others
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

import org.eclipse.daanse.olap.api.monitor.event.CellCacheEvent;
import org.eclipse.daanse.olap.common.ExecutionConfig;
import static org.eclipse.daanse.rolap.common.SqlStatement.JAVA_DOUBLE_OVERFLOW;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.olap.api.cache.CacheCommand;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.api.execution.Execution.Purpose;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.GuardedStatement;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.api.result.NullValue;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.exceptions.ResourceLimitExceededException;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.util.CancellationChecker;
import org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.SqlStatement;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager.AbortException;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager.SegmentCacheIndexRegistry;
import org.eclipse.daanse.rolap.common.cache.SegmentCacheIndex;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The SegmentLoader queries the database and loads the data into the given
 * set of segments: a segment of a measure, where columns are constrained to
 * values. Each constraint can be null (don't constrain) or carry several
 * values (e.g. State in {"CA", "OR", "WA"}).
 *
 * Loaded segments go to the manager's composite cache: the local in-memory
 * store and every attached external SegmentCache.
 *
 * @author Thiyagu, LBoudreau
 * @since 24 May 2007
 */
public class SegmentLoader {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentLoader.class);

  private final SegmentCacheManager cacheMgr;
    private static final String SEGMENT_FETCH_LIMIT_EXCEEDED = "Number of cell results to be read exceeded limit of ({0,number})";

    /**
   * Creates a SegmentLoader.
   *
   * @param cacheMgr
   *          Cache manager
   */
  public SegmentLoader( SegmentCacheManager cacheMgr ) {
    this.cacheMgr = cacheMgr;
  }

  /**
   * Loads data for all the segments of the GroupingSets. If the grouping sets list contains more than one Grouping Set
   * then data is loaded using the GROUP BY GROUPING SETS sql. Else if only one grouping set is passed in the list data
   * is loaded without using GROUP BY GROUPING SETS sql. If the database does not support grouping sets
   * ({@code Dialect#supportsGroupingSets}) then the grouping sets list should always have only one element in
   * it.
   *
   * For example, if list has 2 grouping sets with columns A, B, C and B, C respectively, then the SQL will be
   * "GROUP BY GROUPING SETS ((A, B, C), (B, C))".
   *
   * Else if the list has only one grouping set then sql would be without grouping sets.
   *
   * The groupingSets list should be topological order, with more detailed higher-level grouping sets
   * occurring first. In other words, the first element of the list should always be the detailed grouping set (default
   * grouping set), followed by grouping sets which can be rolled-up on this detailed grouping set. In the example (A,
   * B, C) is the detailed grouping set and (B, C) is rolled-up using the detailed.
   *
   * Grouping sets are removed from the {@code groupingSets} list as they are loaded.
   *
   *
   * @param cellRequestCount
   *          Number of missed cells that led to this request
   * @param groupingSets
   *          List of grouping sets whose segments are loaded
   * @param compoundPredicateList
   *          Compound predicates
   * @param segmentFutures
   *          List of futures wherein each statement will place a list of the segments it has loaded, when it completes
   */
  public void load( int cellRequestCount, List<GroupingSet> groupingSets, List<StarPredicate> compoundPredicateList,
      List<Future<Map<Segment, SegmentWithData>>> segmentFutures ) {
    if ( !cacheMgr.getContext().getConfig().disableCaching() ) {
      for ( GroupingSet groupingSet : groupingSets ) {
        for ( Segment segment : groupingSet.getSegments() ) {
          if ( !segment.star.getCatalog().isCellCachingEnabled( segment.getHeader().cubeName ) ) {
            // cells=off: publishSegment never calls loadSucceeded for this
            // cube, so a load slot would leave every peek waiting forever
            continue;
          }
          final SegmentCacheIndex index = ((SegmentCacheIndexRegistry)cacheMgr.getIndexRegistry()).getIndex( segment.star );
          index.add( segment.getHeader(), true );
          // Make sure that we are registered as a client of
          // the segment by invoking getFuture.
          index.getFuture( ExecutionContext.current().getExecution(), segment.getHeader() ) ;
        }
      }
    }
    try {
      segmentFutures.add( cacheMgr.sqlExecutor.submit( new SegmentLoadCommand( ExecutionContext.current(), this, cellRequestCount,
          groupingSets, compoundPredicateList ) ) );
    } catch (RejectedExecutionException e) {
      throw new OlapRuntimeException(e);
    }
  }

  private static class SegmentLoadCommand implements Callable<Map<Segment, SegmentWithData>> {
    private final ExecutionContext executionContext;
    private final SegmentLoader segmentLoader;
    private final int cellRequestCount;
    private final List<GroupingSet> groupingSets;
    private final List<StarPredicate> compoundPredicateList;

    public SegmentLoadCommand( ExecutionContext executionContext, SegmentLoader segmentLoader, int cellRequestCount,
        List<GroupingSet> groupingSets, List<StarPredicate> compoundPredicateList ) {
      this.executionContext = executionContext;
      this.segmentLoader = segmentLoader;
      this.cellRequestCount = cellRequestCount;
      this.groupingSets = groupingSets;
      this.compoundPredicateList = compoundPredicateList;
    }

    @Override
	public Map<Segment, SegmentWithData> call() throws Exception {
      return ExecutionContext.where(executionContext, () -> {
          boolean useAggregates = executionContext.getExecution().getDaanseStatement().getDaanseConnection().getContext().getConfig().useAggregates();
        return segmentLoader.loadImpl( cellRequestCount, groupingSets, compoundPredicateList, useAggregates,
            executionContext.getExecution().getDaanseStatement().getDaanseConnection().getContext().getConfig().sparseSegmentCountThreshold(),
            executionContext.getExecution().getDaanseStatement().getDaanseConnection().getContext().getConfig().sparseSegmentDensityThreshold());
      });
    }
  }

  private Map<Segment, SegmentWithData> loadImpl( int cellRequestCount, List<GroupingSet> groupingSets,
      List<StarPredicate> compoundPredicateList, boolean useAggregates, int sparseSegmentCountThreshold,
                                                  double sparseSegmentDensityThreshold) {
    SqlStatement stmt = null;
    GroupingSetsList groupingSetsList = new GroupingSetsList( groupingSets );
    RolapStar.Column[] defaultColumns = groupingSetsList.getDefaultColumns();

    final Map<Segment, SegmentWithData> segmentMap = new HashMap<>();
    Throwable throwable = null;
    try {
      int arity = defaultColumns.length;
      SortedSet<Comparable>[] axisValueSets = getDistinctValueWorkspace( arity );

      stmt = createExecuteSql( cellRequestCount, groupingSetsList, compoundPredicateList, useAggregates );

      if ( stmt == null ) {
        // Nothing to do. We're done here.
        return segmentMap;
      }

      boolean[] axisContainsNull = new boolean[arity];

      RowList rows = processData( stmt, axisContainsNull, axisValueSets, groupingSetsList );

      boolean[] sparse = setAxisDataAndDecideSparseUse( axisValueSets, axisContainsNull, groupingSetsList, rows,
          sparseSegmentCountThreshold, sparseSegmentDensityThreshold);

      final Map<BitKey, GroupingSetsList.Cohort> groupingDataSetsMap =
          createDataSetsForGroupingSets( groupingSetsList, sparse, rows.getTypes().subList( arity, rows.getTypes()
              .size() ) );

      loadDataToDataSets( groupingSetsList, rows, groupingDataSetsMap );

      setDataToSegments( groupingSetsList, groupingDataSetsMap, segmentMap );

      return segmentMap;
    } catch ( Throwable e ) {
      throwable = e;
      if ( stmt == null ) {
        throw new OlapRuntimeException( e );
      }
      throw stmt.handle( e );
    } finally {
      if ( stmt != null ) {
        stmt.close();
      }
      setFailOnStillLoadingSegments( segmentMap, groupingSetsList, throwable );
    }
  }

  /** Publishes a segment loaded from SQL: slot release, events, cache put. */
  private void publishSegment( RolapStar star, SegmentHeader header, SegmentBody body ) {
    cacheMgr.cacheLoaded( star, header, body, CellCacheEvent.Source.SQL );
  }

  private boolean setFailOnStillLoadingSegments( Map<Segment, SegmentWithData> segmentMap,
      GroupingSetsList groupingSetsList, Throwable throwable ) {
    int n = 0;
    for ( GroupingSet groupingSet : groupingSetsList.getGroupingSets() ) {
      for ( Segment segment : groupingSet.getSegments() ) {
        if ( !segmentMap.containsKey( segment ) ) {
          if ( throwable == null ) {
            throwable = new RuntimeException( "Segment failed to load" );
          }
          final SegmentHeader header = segment.getHeader();
          cacheMgr.loadFailed( segment.star, header, throwable );
          ++n;
        }
      }
    }
    return n > 0;
  }

  /**
   * Loads data to the datasets. If the grouping sets is used, dataset is fetched from groupingDataSetMap using grouping
   * bit keys of the row data. If grouping sets is not used, data is loaded on to nonGroupingDataSets.
   */
  void loadDataToDataSets( GroupingSetsList groupingSetsList, RowList rows,
      Map<BitKey, GroupingSetsList.Cohort> groupingDataSetMap ) {
    int arity = groupingSetsList.getDefaultColumns().length;
    SegmentAxis[] axes = groupingSetsList.getDefaultAxes();
    int segmentLength = groupingSetsList.getDefaultSegments().size();

    final List<BestFitColumnType> types = rows.getTypes();
    final boolean useGroupingSet = groupingSetsList.useGroupingSets();
    for ( rows.first(); rows.next(); ) {
      final BitKey groupingBitKey;
      final GroupingSetsList.Cohort cohort;
      if ( useGroupingSet ) {
        groupingBitKey = (BitKey) rows.getObject( groupingSetsList.getGroupingBitKeyIndex() );
        cohort = groupingDataSetMap.get( groupingBitKey );
      } else {
        groupingBitKey = null;
        cohort = groupingDataSetMap.get( BitKey.EMPTY );
      }
      final int[] pos = cohort.pos;
      for ( int j = 0, k = 0; j < arity; j++ ) {
        final BestFitColumnType type = types.get( j );
        switch ( type ) {
          // TODO: different treatment for INT, LONG, DOUBLE
          case OBJECT:
          case STRING:
          case DATE:
          case TIMESTAMP:
          case INT:
          case LONG:
          case DOUBLE:
          case DECIMAL:
            Object o = rows.getObject( j );
            if ( useGroupingSet && ( o == null || o == Util.sqlNullValue ) && groupingBitKey.get( groupingSetsList
                .findGroupingFunctionIndex( j ) ) ) {
              continue;
            }
            SegmentAxis axis = axes[j];
            if ( o == null ) {
              o = Util.sqlNullValue;
            }
            // Note: We believe that all value types are Comparable.
            // In JDK 1.4, Boolean did not implement Comparable, but
            // that's too minor/long ago to worry about.
            int offset = axis.getOffset( (Comparable) o );
            pos[k++] = offset;
            break;
          default:
            throw Util.unexpected( type );
        }
      }

      for ( int j = 0; j < segmentLength; j++ ) {
        cohort.segmentDatasetList.get( j ).populateFrom( pos, rows, arity + j );
      }
    }
  }

  /**
   * Builds all axes and decides the representation per grouping set, index-
   * parallel to {@code groupingSetsList.getGroupingSets()}: each set's
   * possible-value count comes from its own axes and its actual count from
   * the rows carrying its grouping bit key — a coarse rollup cohort can be
   * dense while the detail cohort in the same load is sparse.
   */
  boolean[] setAxisDataAndDecideSparseUse( SortedSet<Comparable>[] axisValueSets, boolean[] axisContainsNull,
      GroupingSetsList groupingSetsList, RowList rows, int sparseSegmentCountThreshold,
                                                 double sparseSegmentDensityThreshold) {
    SegmentAxis[] axes = groupingSetsList.getDefaultAxes();
    RolapStar.Column[] allColumns = groupingSetsList.getDefaultColumns();
    for ( int i = 0; i < axes.length; i++ ) {
      SortedSet<Comparable> valueSet = axisValueSets[i];
      axes[i] = new SegmentAxis( groupingSetsList.getDefaultPredicates()[i], valueSet, axisContainsNull[i] );
      setAxisDataToGroupableList( groupingSetsList, valueSet, axisContainsNull[i], allColumns[i] );
    }
    if ( !groupingSetsList.useGroupingSets() ) {
      return new boolean[] {
          decideSparse( axes, rows.size(), sparseSegmentCountThreshold, sparseSegmentDensityThreshold ) };
    }
    final Map<BitKey, Integer> rowCounts = rows.groupingKeyCounts();
    final List<GroupingSet> groupingSets = groupingSetsList.getGroupingSets();
    final List<BitKey> bitKeys = groupingSetsList.getRollupColumnsBitKeyList();
    final boolean[] sparse = new boolean[groupingSets.size()];
    for ( int i = 0; i < groupingSets.size(); i++ ) {
      sparse[i] = decideSparse( groupingSets.get( i ).getAxes(), rowCounts.getOrDefault( bitKeys.get( i ), 0 ),
          sparseSegmentCountThreshold, sparseSegmentDensityThreshold );
    }
    return sparse;
  }

  /**
   * Density decision for one axis set: dense-size overflow or the threshold
   * formula. Overridable so tests can force a representation per cohort.
   */
  protected boolean decideSparse( SegmentAxis[] axes, int actualCount, int sparseSegmentCountThreshold,
      double sparseSegmentDensityThreshold ) {
    int n = 1;
    for ( SegmentAxis axis : axes ) {
      int size = axis.getKeys().length;
      int previous = n;
      n *= size;
      if ( ( n < previous ) || ( n < size ) ) {
        return true;
      }
    }
    return useSparse( n, actualCount, sparseSegmentCountThreshold, sparseSegmentDensityThreshold );
  }

  private void setDataToSegments( GroupingSetsList groupingSetsList, Map<BitKey, GroupingSetsList.Cohort> datasetsMap,
      Map<Segment, SegmentWithData> segmentSlotMap ) {
    List<GroupingSet> groupingSets = groupingSetsList.getGroupingSets();
    for ( int i = 0; i < groupingSets.size(); i++ ) {
      List<Segment> segments = groupingSets.get( i ).getSegments();
      GroupingSetsList.Cohort cohort = datasetsMap.get( groupingSetsList.getRollupColumnsBitKeyList().get( i ) );
      for ( int j = 0; j < segments.size(); j++ ) {
        Segment segment = segments.get( j );
        final SegmentDataset segmentDataset = cohort.segmentDatasetList.get( j );
        final SegmentWithData segmentWithData = new SegmentWithData( segment, segmentDataset, cohort.axes );

        segmentSlotMap.put( segment, segmentWithData );

        final SegmentHeader header = segmentWithData.getHeader();
        final SegmentBody body =
            segmentWithData.getData().createSegmentBody( new AbstractList<Pair<SortedSet<Comparable>, Boolean>>() {
              @Override
			public Pair<SortedSet<Comparable>, Boolean> get( int index ) {
                return segmentWithData.axes[index].getValuesAndIndicator();
              }

              @Override
			public int size() {
                return segmentWithData.axes.length;
              }
            } );

        // Send a message to the agg manager. It will place the segment
        // in the index.
        publishSegment( segment.star, header, body );
      }
    }
  }

  Map<BitKey, GroupingSetsList.Cohort> createDataSetsForGroupingSets( GroupingSetsList groupingSetsList,
      boolean[] sparse, List<BestFitColumnType> types ) {
    if ( !groupingSetsList.useGroupingSets() ) {
      final GroupingSetsList.Cohort datasets =
          createDataSets( sparse[0], groupingSetsList.getDefaultSegments(), groupingSetsList.getDefaultAxes(), types );
      return Collections.singletonMap( BitKey.EMPTY, datasets );
    }
    Map<BitKey, GroupingSetsList.Cohort> datasetsMap = new HashMap<>();
    List<GroupingSet> groupingSets = groupingSetsList.getGroupingSets();
    List<BitKey> groupingColumnsBitKeyList = groupingSetsList.getRollupColumnsBitKeyList();
    for ( int i = 0; i < groupingSets.size(); i++ ) {
      GroupingSet groupingSet = groupingSets.get( i );
      GroupingSetsList.Cohort cohort =
          createDataSets( sparse[i], groupingSet.getSegments(), groupingSet.getAxes(), types );
      datasetsMap.put( groupingColumnsBitKeyList.get( i ), cohort );
    }
    return datasetsMap;
  }

  private int calculateMaxDataSize( SegmentAxis[] axes ) {
    int n = 1;
    for ( SegmentAxis axis : axes ) {
      n *= axis.getKeys().length;
    }
    return n;
  }

  private GroupingSetsList.Cohort createDataSets( boolean sparse, List<Segment> segments, SegmentAxis[] axes,
      List<BestFitColumnType> types ) {
    final List<SegmentDataset> datasets = new ArrayList<>( segments.size() );
    final int n;
    if ( sparse ) {
      n = 0;
    } else {
      n = calculateMaxDataSize( axes );
    }
    for ( int i = 0; i < segments.size(); i++ ) {
      final Segment segment = segments.get( i );
      datasets.add( segment.createDataset( axes, sparse, types.get( i ), n ) );
    }
    return new GroupingSetsList.Cohort( datasets, axes );
  }

  private void setAxisDataToGroupableList( GroupingSetsList groupingSetsList, SortedSet<Comparable> valueSet,
      boolean axisContainsNull, RolapStar.Column column ) {
    for ( GroupingSet groupingSet : groupingSetsList.getRollupGroupingSets() ) {
      RolapStar.Column[] columns = groupingSet.getColumns();
      for ( int i = 0; i < columns.length; i++ ) {
        if ( columns[i].equals( column ) ) {
          groupingSet.getAxes()[i] = new SegmentAxis( groupingSet.getPredicates()[i], valueSet, axisContainsNull );
        }
      }
    }
  }

  /**
   * Creates and executes a SQL statement to retrieve the set of cells specified by a GroupingSetsList.
   *
   * This method may be overridden in tests.
   *
   * @param cellRequestCount
   *          Number of missed cells that led to this request
   * @param groupingSetsList
   *          Grouping
   * @param compoundPredicateList
   *          Compound predicate list
   * @return An executed SQL statement, or null
   */
  public SqlStatement createExecuteSql( int cellRequestCount, final GroupingSetsList groupingSetsList,
      List<StarPredicate> compoundPredicateList, boolean useAggregates ) {
    RolapStar star = groupingSetsList.getStar();
    RenderedSql pair =
        AggregationManager.generateSql( groupingSetsList, compoundPredicateList, useAggregates );
    ExecutionMetadata metadata = ExecutionMetadata.of(
        "Segment.load",
        "Error while loading segment",
        Purpose.CELL_SEGMENT,
        cellRequestCount
    );
    final ExecutionContext executionContext = ExecutionContext.current().createChild(metadata, Optional.empty());

    // cells=off segments are never index-registered (load() skips them):
    // they must not count against the abort check - the query itself is
    // their interested party - and an all-uncached load registers its
    // statement on the execution context like a disableCaching load.
    // Without this the caching callback aborted every cells=off query as
    // "no interested party left" and the cube was unqueryable.
    boolean anySegmentCached = false;
    boolean anySegmentUncached = false;
    for ( Segment seg : groupingSetsList.getDefaultSegments() ) {
      if ( seg.star.getCatalog().isCellCachingEnabled( seg.getHeader().cubeName ) ) {
        anySegmentCached = true;
      } else {
        anySegmentUncached = true;
      }
    }
    final boolean useCachingCallback =
        !cacheMgr.getContext().getConfig().disableCaching() && anySegmentCached;
    final boolean mixedBatch = useCachingCallback && anySegmentUncached;

    // When caching is enabled, we must register the SQL statement
    // in the index. We don't want to cancel SQL statements that are shared
    // across threads unless it is safe.
    final Consumer<GuardedStatement> callbackWithCaching = new Consumer<> () {
      @Override
	public void accept( final GuardedStatement stmt ) {
        if ( mixedBatch ) {
          // the uncached side has no index slot, so ExecutionContext.cancel
          // could never reach this JDBC statement: register it on the
          // context TOO (close() unregisters unconditionally). Runs on the
          // SQL thread, before the actor round-trip below.
          executionContext.registerStatement( stmt );
        }
        cacheMgr.execute( new CacheCommand<Void>() {
          @Override
		public Void call() throws Exception {
            boolean atLeastOneActive = false;
            // a dead query is nobody's interested party: without this, the
            // constant "uncached counts as active" kept a cancelled mixed
            // batch's SQL burning until it finished on its own
            boolean queryAlive = true;
            try {
              executionContext.checkCancelOrTimeout();
            } catch ( RuntimeException cancelledOrTimedOut ) {
              queryAlive = false;
            }
            for ( Segment seg : groupingSetsList.getDefaultSegments() ) {
              if ( !seg.star.getCatalog().isCellCachingEnabled( seg.getHeader().cubeName ) ) {
                // never registered - the (live) query is the interested party
                atLeastOneActive |= queryAlive;
                continue;
              }
              final SegmentCacheIndex index = ((SegmentCacheIndexRegistry)cacheMgr.getIndexRegistry()).getIndex( seg.star );
              // Make sure to check if the segment still
              // exists in the index. It could have been
              // removed by a cancellation request since
              // then.
              // hasInterestedParties, not contains: a flush that flags a
              // still-loading header for removal usually leaves nobody
              // behind, and then the SQL should stop rather than burn on to
              // feed the late-put ghost. But peers may already be parked on
              // the slot - they are handed the body before the flag is acted
              // on, so abandoning the load here failed THEIR queries for a
              // flush they had no part in.
              if ( index.hasInterestedParties( seg.getHeader() ) ) {
                index.linkSqlStatement( seg.getHeader(), stmt );
                atLeastOneActive = true;
              }
            }
            if ( !atLeastOneActive ) {
              // Every segment of this load is gone from the index
              // (cancelled or flushed): tell the segment thread to stop.
              throw new AbortException();
            }
            return null;
          }

          @Override
		public ExecutionContext getExecutionContext() {
            return executionContext;
          }
        } );
      }
    };

    // When using no cache, we register the SQL statement directly
    // with the execution instance for cleanup - the default callback.
    final Consumer<GuardedStatement> callbackNoCaching =
        RolapUtil.getDefaultCallback( executionContext );

    try {
      return RolapUtil.executeQuery( star.getContext(), pair.sql(), pair.columnTypes(), 0, 0, executionContext, -1, -1,
          // Only one of the two callbacks are required, depending if we
          // cache the segments or not.
          useCachingCallback ? callbackWithCaching : callbackNoCaching );
    } catch ( Throwable t ) {
      if ( Util.getMatchingCause( t, AbortException.class ) != null ) {
        return null;
      } else {
        throw new OlapRuntimeException( "Failed to load segment form SQL", t );
      }
    }
  }

  public RowList processData( SqlStatement stmt, final boolean[] axisContainsNull,
      final SortedSet<Comparable>[] axisValueSets, final GroupingSetsList groupingSetsList ) throws SQLException {
    List<Segment> segments = groupingSetsList.getDefaultSegments();
    int measureCount = segments.size();
    ResultSet rawRows = loadData( stmt, groupingSetsList );
    assert stmt != null;
    final List<BestFitColumnType> types = stmt.guessTypes();
    int arity = axisValueSets.length;
    final int groupingColumnStartIndex = arity + measureCount;

    // If we're using grouping sets, the SQL query will have a number of
    // indicator columns, and we roll these into a single BitSet column in
    // the processed data set.
    final List<BestFitColumnType> processedTypes;
    if ( groupingSetsList.useGroupingSets() ) {
      processedTypes = new ArrayList<>( types.subList( 0, groupingColumnStartIndex ) );
      processedTypes.add( BestFitColumnType.OBJECT );
    } else {
      processedTypes = types;
    }
    final RowList processedRows = new RowList( processedTypes, 100 );

    Execution execution = ExecutionContext.current().getExecution();
    final boolean[] numeric = new boolean[measureCount];
    {
      int k = 0;
      for ( Segment segment : segments ) {
        numeric[k++] = segment.measure.getDatatype().isNumeric();
      }
    }
    while ( rawRows.next() ) {
      // Check if the MDX query was canceled.
      CancellationChecker.checkCancelOrTimeout( ++stmt.rowCount, execution );

      checkResultLimit( stmt.rowCount );
      processedRows.createRow();

      // get the columns
      int columnIndex = 0;
      for ( int axisIndex = 0; axisIndex < arity; axisIndex++, columnIndex++ ) {
        final BestFitColumnType type = types.get( columnIndex );
        switch ( type ) {
          case OBJECT:
          case STRING:
          case DATE:
          case TIMESTAMP:
            Object o = type == BestFitColumnType.DATE || type == BestFitColumnType.TIMESTAMP
                ? SqlStatement.temporal( rawRows, columnIndex + 1, type ) : rawRows.getObject( columnIndex + 1 );
            if ( o == null ) {
              o = Util.sqlNullValue;
              if ( !groupingSetsList.useGroupingSets() || !isAggregateNull( rawRows, groupingColumnStartIndex,
                  groupingSetsList, axisIndex ) ) {
                axisContainsNull[axisIndex] = true;
              }
            } else {
              // We assume that all values are Comparable. Boolean
              // wasn't Comparable until JDK 1.5, but we can live with
              // that bug because JDK 1.4 is no longer important.

              // byte [] is not Comparable.
              // For our case it can be binary array. It was typed as String.
              // So it can be processing (comparing and displaying) correctly as String
              if ( o instanceof byte[] ) {
                o = new String( (byte[]) o );
              }
              axisValueSets[axisIndex].add( (Comparable) o );
            }
            processedRows.setObject( columnIndex, o );
            break;
          case INT:
            final int intValue = rawRows.getInt( columnIndex + 1 );
            if ( intValue == 0 && rawRows.wasNull() ) {
              if ( !groupingSetsList.useGroupingSets() || !isAggregateNull( rawRows, groupingColumnStartIndex,
                  groupingSetsList, axisIndex ) ) {
                axisContainsNull[axisIndex] = true;
              }
              processedRows.setNull( columnIndex, true );
            } else {
              axisValueSets[axisIndex].add( intValue );
              processedRows.setInt( columnIndex, intValue );
            }
            break;
          case LONG:
            final long longValue = rawRows.getLong( columnIndex + 1 );
            if ( longValue == 0 && rawRows.wasNull() ) {
              if ( !groupingSetsList.useGroupingSets() || !isAggregateNull( rawRows, groupingColumnStartIndex,
                  groupingSetsList, axisIndex ) ) {
                axisContainsNull[axisIndex] = true;
              }
              processedRows.setNull( columnIndex, true );
            } else {
              axisValueSets[axisIndex].add( longValue );
              processedRows.setLong( columnIndex, longValue );
            }
            break;
          case DOUBLE:
            final double doubleValue = rawRows.getDouble( columnIndex + 1 );
            if ( doubleValue == 0 && rawRows.wasNull() ) {
              if ( !groupingSetsList.useGroupingSets() || !isAggregateNull( rawRows, groupingColumnStartIndex,
                  groupingSetsList, axisIndex ) ) {
                axisContainsNull[axisIndex] = true;
              }
              processedRows.setNull( columnIndex, true );
            } else {
              axisValueSets[axisIndex].add( doubleValue );
              processedRows.setDouble( columnIndex, doubleValue );
            }
            break;
          case DECIMAL:
            final BigDecimal decimal = rawRows.getBigDecimal( columnIndex + 1 );
            if ( decimal == null ) {
              if ( !groupingSetsList.useGroupingSets() || !isAggregateNull( rawRows, groupingColumnStartIndex,
                  groupingSetsList, axisIndex ) ) {
                axisContainsNull[axisIndex] = true;
              }
              processedRows.setNull( columnIndex, true );
            } else {
              final double val = decimal.doubleValue();
              if ( val == Double.NEGATIVE_INFINITY || val == Double.POSITIVE_INFINITY ) {
                throw new SQLDataException(MessageFormat.format(JAVA_DOUBLE_OVERFLOW, rawRows.getMetaData().getColumnName(
                    columnIndex + 1 ) ));
              }
              axisValueSets[axisIndex].add( val );
              processedRows.setDouble( columnIndex, val );
            }
            break;
          default:
            throw Util.unexpected( type );
        }
      }

      // pre-compute which measures are numeric

      // get the measure
      for ( int i = 0; i < measureCount; i++, columnIndex++ ) {
        final BestFitColumnType type = types.get( columnIndex );
        switch ( type ) {
          case OBJECT:
          case STRING:
          case DATE:
          case TIMESTAMP:
            Object o = type == BestFitColumnType.DATE || type == BestFitColumnType.TIMESTAMP
                ? SqlStatement.temporal( rawRows, columnIndex + 1, type ) : rawRows.getObject( columnIndex + 1 );
            if ( o == null ) {
              o = NullValue.INSTANCE; // convert to placeholder
            } else if ( numeric[i] ) {
              if ( o instanceof Double ) {
                // nothing to do
              } else if ( o instanceof BigDecimal ) {
                // nothing to do // PDI-16761 if we cast it to double type we lose precision
              } else if ( o instanceof Number ) {
                o = ( (Number) o ).doubleValue();
              } else if ( o instanceof byte[] ) {
                // On MySQL 5.0 in German locale, values can come
                // out as byte arrays. Don't know why. Bug 1594119.
                o = Double.parseDouble( new String( (byte[]) o ) );
              } else {
                  try {
                      o = Double.parseDouble( o.toString() );
                  }
                  catch (NumberFormatException e) {
                      LOGGER.warn("Failed to parse numeric value: {}", o, e);
                  }
              }
            }
            processedRows.setObject( columnIndex, o );
            break;
          case INT:
            final int intValue = rawRows.getInt( columnIndex + 1 );
            processedRows.setInt( columnIndex, intValue );
            if ( intValue == 0 && rawRows.wasNull() ) {
              processedRows.setNull( columnIndex, true );
            }
            break;
          case LONG:
            final long longValue = rawRows.getLong( columnIndex + 1 );
            processedRows.setLong( columnIndex, longValue );
            if ( longValue == 0 && rawRows.wasNull() ) {
              processedRows.setNull( columnIndex, true );
            }
            break;
          case DOUBLE:
            final double doubleValue = rawRows.getDouble( columnIndex + 1 );
            processedRows.setDouble( columnIndex, doubleValue );
            if ( doubleValue == 0 && rawRows.wasNull() ) {
              processedRows.setNull( columnIndex, true );
            }
            break;
          case DECIMAL:
            final BigDecimal decimal = rawRows.getBigDecimal( columnIndex + 1 );
            if ( decimal == null ) {
              processedRows.setNull( columnIndex, true );
            } else {
              final double val = decimal.doubleValue();
              if ( val == Double.NEGATIVE_INFINITY || val == Double.POSITIVE_INFINITY ) {
                throw new SQLDataException(MessageFormat.format(JAVA_DOUBLE_OVERFLOW, rawRows.getMetaData().getColumnName(
                    columnIndex + 1 ) ));
              }
              processedRows.setDouble( columnIndex, val );
            }
            break;
          default:
            throw Util.unexpected( type );
        }
      }

      if ( groupingSetsList.useGroupingSets() ) {
        processedRows.setGroupingKey( columnIndex, getRollupBitKey( groupingSetsList.getRollupColumns().size(), rawRows,
            columnIndex ) );
      }
    }
    return processedRows;
  }

  private void checkResultLimit( int currentCount ) {
    final int limit = ExecutionConfig.current().resultLimit();
    if ( limit > 0 && currentCount > limit ) {
      throw new ResourceLimitExceededException(MessageFormat.format(SEGMENT_FETCH_LIMIT_EXCEEDED, limit ));
    }
  }

  /**
   * Generates bit key representing roll up columns
   */
  public BitKey getRollupBitKey( int arity, ResultSet rowList, int k ) throws SQLException {
    BitKey groupingBitKey = BitKey.Factory.makeBitKey( arity );
    for ( int i = 0; i < arity; i++ ) {
      int o = rowList.getInt( k + i + 1 );
      if ( o == 1 ) {
        groupingBitKey.set( i );
      }
    }
    return groupingBitKey;
  }

  private boolean isAggregateNull( ResultSet rowList, int groupingColumnStartIndex, GroupingSetsList groupingSetsList,
      int axisIndex ) throws SQLException {
    int groupingFunctionIndex = groupingSetsList.findGroupingFunctionIndex( axisIndex );
    if ( groupingFunctionIndex == -1 ) {
      // Not a rollup column
      return false;
    }
    return rowList.getInt( groupingColumnStartIndex + groupingFunctionIndex + 1 ) == 1;
  }

  ResultSet loadData( SqlStatement stmt, GroupingSetsList groupingSetsList ) throws SQLException {
    assert groupingSetsList.getDefaultColumns().length
        + groupingSetsList.getDefaultSegments().size()
        + groupingSetsList.getRollupColumns().size() == stmt.guessTypes().size();
    return stmt.getResultSet();
  }

  public SortedSet<Comparable>[] getDistinctValueWorkspace( int arity ) {
    // Workspace to build up lists of distinct values for each axis.
    SortedSet<Comparable>[] axisValueSets = new SortedSet[arity];
    for ( int i = 0; i < axisValueSets.length; i++ ) {
      axisValueSets[i] = new TreeSet<>();
    }
    return axisValueSets;
  }

  /**
   * Decides whether to use a sparse representation for this segment, using the formula described
   * SparseSegmentCountThreshold here.
   *
   * @param possibleCount
   *          Number of values in the space.
   * @param actualCount
   *          Actual number of values.
   * @return Whether to use a sparse representation.
   */
  static boolean useSparse( final double possibleCount, final double actualCount,
                            int sparseSegmentCountThreshold,
                            double sparseSegmentDensityThreshold) {
    if ( sparseSegmentDensityThreshold < 0 ) {
        sparseSegmentDensityThreshold = 0;
    }
    if ( sparseSegmentDensityThreshold > 1 ) {
        sparseSegmentDensityThreshold = 1;
    }
    int countThreshold = sparseSegmentCountThreshold;
    if ( countThreshold < 0 ) {
      countThreshold = 0;
    }
    boolean sparse = ( possibleCount - countThreshold ) * sparseSegmentDensityThreshold > actualCount;
    if ( possibleCount < countThreshold ) {
      assert !sparse : new StringBuilder("Should never use sparse if count is less than threshold, possibleCount=")
          .append(possibleCount).append(", actualCount=").append(actualCount).append(", countThreshold=")
          .append(countThreshold).append(", densityThreshold=").append(sparseSegmentDensityThreshold).toString();
    }
    if ( possibleCount == actualCount ) {
      assert !sparse : new StringBuilder("Should never use sparse if result is 100% dense: possibleCount=")
          .append(possibleCount).append(", actualCount=").append(actualCount).append(", countThreshold=")
          .append(countThreshold).append(", densityThreshold=").append(sparseSegmentDensityThreshold).toString();
    }
    return sparse;
  }

  /**
   * Collection of rows, each with a set of columns of type Object, double, or int. Native types are not boxed.
   */
  public static class RowList {
    private final Column[] columns;
    private final Map<BitKey, Integer> groupingKeyCounts = new HashMap<>();
    private int rowCount = 0;
    private int capacity = 0;
    private int currentRow = -1;

    /**
     * Creates a RowList.
     *
     * @param types
     *          Column types
     */
    public RowList( List<BestFitColumnType> types ) {
      this( types, 100 );
    }

    /**
     * Creates a RowList with a specified initial capacity.
     *
     * @param types
     *          Column types
     * @param capacity
     *          Initial capacity
     */
    RowList( List<BestFitColumnType> types, int capacity ) {
      this.columns = new Column[types.size()];
      this.capacity = capacity;
      for ( int i = 0; i < columns.length; i++ ) {
        columns[i] = Column.forType( i, types.get( i ), capacity );
      }
    }

    public void createRow() {
      currentRow = rowCount++;
      if ( rowCount > capacity ) {
        capacity *= 3;
        for ( Column column : columns ) {
          column.resize( capacity );
        }
      }
    }

    public void setObject( int column, Object value ) {
      columns[column].setObject( currentRow, value );
    }

    /**
     * Writes the row's grouping bit key AND tallies it: the per-set
     * density decision reads the tally instead of re-walking every row.
     */
    public void setGroupingKey( int column, BitKey key ) {
      setObject( column, key );
      groupingKeyCounts.merge( key, 1, Integer::sum );
    }

    /** Rows per grouping bit key, tallied while the rows were written. */
    public Map<BitKey, Integer> groupingKeyCounts() {
      return groupingKeyCounts;
    }

    void setDouble( int column, double value ) {
      columns[column].setDouble( currentRow, value );
    }

    void setInt( int column, int value ) {
      columns[column].setInt( currentRow, value );
    }

    void setLong( int column, long value ) {
      columns[column].setLong( currentRow, value );
    }

    public int size() {
      return rowCount;
    }

    public void createRow( ResultSet resultSet ) throws SQLException {
      createRow();
      for ( Column column : columns ) {
        column.populateFrom( currentRow, resultSet );
      }
    }

    public List<BestFitColumnType> getTypes() {
      return new AbstractList<>() {
        @Override
		public BestFitColumnType get( int index ) {
          return columns[index].type;
        }

        @Override
		public int size() {
          return columns.length;
        }
      };
    }

    /**
     * Moves to before the first row.
     */
    public void first() {
      currentRow = -1;
    }

    /**
     * Moves forward one row, or returns false if at the last row.
     *
     * @return whether moved forward
     */
    public boolean next() {
      if ( currentRow < rowCount - 1 ) {
        ++currentRow;
        return true;
      }
      return false;
    }

    /**
     * Returns the object in the given column of the current row.
     *
     * @param columnIndex
     *          Column index
     * @return Value of the column
     */
    public Object getObject( int columnIndex ) {
      return columns[columnIndex].getObject( currentRow );
    }

    public int getInt( int columnIndex ) {
      return columns[columnIndex].getInt( currentRow );
    }

    public double getDouble( int columnIndex ) {
      return columns[columnIndex].getDouble( currentRow );
    }

    public boolean isNull( int columnIndex ) {
      return columns[columnIndex].isNull( currentRow );
    }

    public void setNull( int columnIndex, boolean b ) {
      columns[columnIndex].setNull( currentRow, b );
    }

    static abstract class Column {
      final int ordinal;
      final BestFitColumnType type;

      protected Column( int ordinal, BestFitColumnType type ) {
        this.ordinal = ordinal;
        this.type = type;
      }

      static Column forType( int ordinal, BestFitColumnType type, int capacity ) {
        switch ( type ) {
          case OBJECT:
          case STRING:
          case DATE:
          case TIMESTAMP:
            return new ObjectColumn( ordinal, type, capacity );
          case INT:
            return new IntColumn( ordinal, type, capacity );
          case LONG:
            return new LongColumn( ordinal, type, capacity );
          case DOUBLE:
          case DECIMAL:
            return new DoubleColumn( ordinal, type, capacity );
          default:
            throw Util.unexpected( type );
        }
      }

      public abstract void resize( int newSize );

      public void setObject( int row, Object value ) {
        throw new UnsupportedOperationException();
      }

      public void setDouble( int row, double value ) {
        throw new UnsupportedOperationException();
      }

      public void setInt( int row, int value ) {
        throw new UnsupportedOperationException();
      }

      public void setLong( int row, long value ) {
        throw new UnsupportedOperationException();
      }

      public void setNull( int row, boolean b ) {
        throw new UnsupportedOperationException();
      }

      public abstract void populateFrom( int row, ResultSet resultSet ) throws SQLException;

      public Object getObject( int row ) {
        throw new UnsupportedOperationException();
      }

      public int getInt( int row ) {
        throw new UnsupportedOperationException();
      }

      public double getDouble( int row ) {
        throw new UnsupportedOperationException();
      }

      protected abstract int getCapacity();

      public abstract boolean isNull( int row );
    }

    static class ObjectColumn extends Column {
      private Object[] objects;

      ObjectColumn( int ordinal, BestFitColumnType type, int size ) {
        super( ordinal, type );
        objects = new Object[size];
      }

      @Override
	protected int getCapacity() {
        return objects.length;
      }

      @Override
	public boolean isNull( int row ) {
        return objects[row] == null;
      }

      @Override
	public void resize( int newSize ) {
        objects = Arrays.copyOf( objects, newSize );
      }

      @Override
	public void populateFrom( int row, ResultSet resultSet ) throws SQLException {
        objects[row] = resultSet.getObject( ordinal + 1 );
      }

      @Override
	public void setObject( int row, Object value ) {
        objects[row] = value;
      }

      @Override
	public Object getObject( int row ) {
        return objects[row];
      }
    }

    static abstract class NativeColumn extends Column {
      protected BitSet nullIndicators;

      NativeColumn( int ordinal, BestFitColumnType type ) {
        super( ordinal, type );
      }

      @Override
	public void setNull( int row, boolean b ) {
        getNullIndicators().set( row, b );
      }

      protected BitSet getNullIndicators() {
        if ( nullIndicators == null ) {
          nullIndicators = new BitSet( getCapacity() );
        }
        return nullIndicators;
      }
    }

    static class IntColumn extends NativeColumn {
      private int[] ints;

      IntColumn( int ordinal, BestFitColumnType type, int size ) {
        super( ordinal, type );
        ints = new int[size];
      }

      @Override
	public void resize( int newSize ) {
        ints = Util.copyOf( ints, newSize );
      }

      @Override
	public void populateFrom( int row, ResultSet resultSet ) throws SQLException {
        int i = ints[row] = resultSet.getInt( ordinal + 1 );
        if ( i == 0 ) {
          getNullIndicators().set( row, resultSet.wasNull() );
        }
      }

      @Override
	public void setInt( int row, int value ) {
        ints[row] = value;
      }

      @Override
	public int getInt( int row ) {
        return ints[row];
      }

      @Override
	public boolean isNull( int row ) {
        return ints[row] == 0 && nullIndicators != null && nullIndicators.get( row );
      }

      @Override
	protected int getCapacity() {
        return ints.length;
      }

      @Override
	public Integer getObject( int row ) {
        return isNull( row ) ? null : ints[row];
      }
    }

    static class LongColumn extends NativeColumn {
      private long[] longs;

      LongColumn( int ordinal, BestFitColumnType type, int size ) {
        super( ordinal, type );
        longs = new long[size];
      }

      @Override
	public void resize( int newSize ) {
        longs = Util.copyOf( longs, newSize );
      }

      @Override
	public void populateFrom( int row, ResultSet resultSet ) throws SQLException {
        long i = longs[row] = resultSet.getLong( ordinal + 1 );
        if ( i == 0 ) {
          getNullIndicators().set( row, resultSet.wasNull() );
        }
      }

      @Override
	public void setLong( int row, long value ) {
        longs[row] = value;
      }

      @Override
	public boolean isNull( int row ) {
        return longs[row] == 0 && nullIndicators != null && nullIndicators.get( row );
      }

      @Override
	protected int getCapacity() {
        return longs.length;
      }

      @Override
	public Long getObject( int row ) {
        return isNull( row ) ? null : longs[row];
      }
    }

    static class DoubleColumn extends NativeColumn {
      private double[] doubles;

      DoubleColumn( int ordinal, BestFitColumnType type, int size ) {
        super( ordinal, type );
        doubles = new double[size];
      }

      @Override
	public void resize( int newSize ) {
        doubles = Util.copyOf( doubles, newSize );
      }

      @Override
	public void populateFrom( int row, ResultSet resultSet ) throws SQLException {
        double d = doubles[row] = resultSet.getDouble( ordinal + 1 );
        if ( d == 0d ) {
          getNullIndicators().set( row, resultSet.wasNull() );
        }
      }

      @Override
	public void setDouble( int row, double value ) {
        doubles[row] = value;
      }

      @Override
	public double getDouble( int row ) {
        return doubles[row];
      }

      @Override
	protected int getCapacity() {
        return doubles.length;
      }

      @Override
	public boolean isNull( int row ) {
        return doubles[row] == 0d && nullIndicators != null && nullIndicators.get( row );
      }

      @Override
	public Double getObject( int row ) {
        return isNull( row ) ? null : doubles[row];
      }
    }

  }
}
