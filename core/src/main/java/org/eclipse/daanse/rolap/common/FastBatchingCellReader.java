/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2004-2005 Julian Hyde
// Copyright (C) 2005-2020 Hitachi Vantara and others
// All Rights Reserved.
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

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.genericExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.CacheCommand;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Execution;
import org.eclipse.daanse.olap.api.IAggregationManager;
import org.eclipse.daanse.olap.api.Locus;
import org.eclipse.daanse.olap.api.exception.CellRequestQuantumExceededException;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import  org.eclipse.daanse.olap.server.LocusImpl;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.agg.Segment;
import org.eclipse.daanse.rolap.common.agg.SegmentBuilder;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager.SegmentCacheIndexRegistry;
import org.eclipse.daanse.rolap.common.agg.SegmentWithData;
import org.eclipse.daanse.rolap.common.cache.SegmentCacheIndex;
import org.eclipse.daanse.rolap.common.cache.SegmentCacheIndexImpl;
import org.eclipse.daanse.rolap.element.RolapCube;

/**
 * A FastBatchingCellReader doesn't really Read cells: when asked
 * to look up the values of stored measures, it lies, and records the fact
 * that the value was asked for.  Later, we can look over the values which
 * are required, fetch them in an efficient way, and re-run the evaluation
 * with a real evaluator.
 *
 * NOTE: When it doesn't know the answer, it lies by returning an error
 * object.  The calling code must be able to deal with that.
 *
 * This class tries to minimize the amount of storage needed to record the
 * fact that a cell was requested.
 */
public class FastBatchingCellReader implements CellReader {

    private final int cellRequestLimit;

    private final RolapCube cube;

    /**
     * Records the number of requests. The field is used for correctness: if
     * the request count stays the same during an operation, you know that the
     * FastBatchingCellReader has not told any lies during that operation, and
     * therefore the result is true. The field is also useful for debugging.
     */
    private int missCount;

    /**
     * Number of occasions that a requested cell was already in cache.
     */
    private int hitCount;

    /**
     * Number of occasions that requested cell was in the process of being
     * loaded into cache but not ready.
     */
    private int pendingCount;

    private final AggregationManager aggMgr;

    private final boolean cacheEnabled;

    private final SegmentCacheManager cacheMgr;

    private final RolapAggregationManager.PinSet pinnedSegments;

    /**
     * Indicates that the reader has given incorrect results.
     */
    private boolean dirty;

    private final List<CellRequest> cellRequests = new ArrayList<>();

    private final Execution execution;

    /**
     * Creates a FastBatchingCellReader.
     *
     * @param execution Execution that calling statement belongs to. Allows us
     *                  to check for cancel
     * @param cube      Cube that requests belong to
     * @param aggMgr    Aggregation manager
     */
    public FastBatchingCellReader(
        Execution execution,
        RolapCube cube,
        IAggregationManager aggMgr)
    {
        this.execution = execution;
        if (cube == null || execution == null) {
            throw new IllegalArgumentException("FastBatchingCellReader: cube and execution should not be null");
        }
        this.cube = cube;
        this.aggMgr = (AggregationManager)aggMgr;
        cacheMgr = (SegmentCacheManager)aggMgr.getCacheMgr(execution.getMondrianStatement().getMondrianConnection());
        pinnedSegments = this.aggMgr.createPinSet();
        cacheEnabled = !cube.getCatalog().getInternalConnection().getContext().getConfigValue(ConfigConstants.DISABLE_CACHING, ConfigConstants.DISABLE_CACHING_DEFAULT_VALUE, Boolean.class);
        Integer cellBatchSize = cube.getCatalog().getInternalConnection().getContext()
                .getConfigValue(ConfigConstants.CELL_BATCH_SIZE, ConfigConstants.CELL_BATCH_SIZE_DEFAULT_VALUE ,Integer.class);
        cellRequestLimit =
            cellBatchSize <= 0
                ? 100000 // TODO Make this logic into a pluggable algorithm.
                : cellBatchSize;
    }

    @Override
	public Object get(RolapEvaluator evaluator) {
        final CellRequest request =
            RolapAggregationManager.makeRequest(evaluator);

        if (request == null || request.isUnsatisfiable()) {
            return Util.nullValue; // request not satisfiable.
        }

        // Try to retrieve a cell and simultaneously pin the segment which
        // contains it.
        final Object o = aggMgr.getCellFromCache(request, pinnedSegments);

        assert o != Boolean.TRUE : "getCellFromCache no longer returns TRUE";
        if (o != null) {
            ++hitCount;
            return o;
        }

        // If this query has not had any cache misses, it's worth doing a
        // synchronous request for the cell segment. If it is in the cache, it
        // will be worth the wait, because we can avoid the effort of batching
        // up requests that could have been satisfied by the same segment.
        if (cacheEnabled
            && missCount == 0)
        {
            SegmentWithData segmentWithData = cacheMgr.peek(request);
            if (segmentWithData != null) {
                segmentWithData.getStar().register(segmentWithData);
                final Object o2 =
                    aggMgr.getCellFromCache(request, pinnedSegments);
                if (o2 != null) {
                    ++hitCount;
                    return o2;
                }
            }
        }

        // if there is no such cell, record that we need to fetch it, and
        // return 'error'
        recordCellRequest(request);
        return Util.valueNotReadyException;
    }

    @Override
	public int getMissCount() {
        return missCount;
    }

    public int getHitCount() {
        return hitCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public final void recordCellRequest(CellRequest request) {
        if (request.isUnsatisfiable()) {
            throw new IllegalArgumentException("request.isUnsatisfiable is true");
        }
        ++missCount;
        cellRequests.add(request);
        if (cellRequests.size() % cellRequestLimit == 0) {
            // Signal that it's time to ask the cache manager if it has cells
            // we need in the cache. Not really an exception.
            throw CellRequestQuantumExceededException.INSTANCE;
        }
    }

    /**
     * Returns whether this reader has told a lie. This is the case if there
     * are pending batches to load or if {@link #setDirty(boolean)} has been
     * called.
     */
    @Override
	public boolean isDirty() {
        return dirty || !cellRequests.isEmpty();
    }

    /**
     * Resolves any pending cell reads using the cache. After calling this
     * method, all cells requested in a given batch are loaded into this
     * statement's local cache.
     *
     * The method is implemented by making an asynchronous call to the cache
     * manager. The result is a list of segments that satisfies every cell
     * request.
     *
     * The client should put the resulting segments into its "query local"
     * cache, to ensure that future cells in that segment can be answered
     * without a call to the cache manager. (That is probably 1000x faster.)
     *
     * The cache manager does not inform where client where each segment
     * came from. There are several possibilities:
     *
     *
     *     Segment was already in cache (header and body)
     *     Segment is in the process of being loaded by executing a SQL
     *     statement (probably due to a request from another client)
     *     Segment is in an external cache (that is, header is in the cache,
     *        body is not yet)
     *     Segment can be created by rolling up one or more cache segments.
     *        (And of course each of these segments might be "paged out".)
     *     By executing a SQL {@code GROUP BY} statement
     *
     *
     * Furthermore, segments in external cache may take some time to retrieve
     * (a LAN round trip, say 1 millisecond, is a reasonable guess); and the
     * request may fail. (It depends on the cache, but caches are at liberty
     * to 'forget' segments.) So, any strategy that relies on cache segments
     * should be able to fall back. Even if there are fall backs, only one call
     * needs to be made to the cache manager.
     *
     * @return Whether any aggregations were loaded.
     */
    public boolean loadAggregations() {
        if (!isDirty()) {
            return false;
        }

        // List of futures yielding segments populated by SQL statements. If
        // loading requires several iterations, we just append to the list. We
        // don't mind if it takes a while for SQL statements to return.
        final List<Future<Map<Segment, SegmentWithData>>> sqlSegmentMapFutures =
            new ArrayList<>();

        final List<CellRequest> cellRequests1 =
            new ArrayList<>(cellRequests);

        preloadColumnCardinality(cellRequests1);

        for (int iteration = 0;; ++iteration) {
            final BatchLoader.LoadBatchResponse response =
                cacheMgr.execute(
                    new BatchLoader.LoadBatchCommand(
                        LocusImpl.peek(),
                        cacheMgr,
                        getDialect(),
                        cube,
                        Collections.unmodifiableList(cellRequests1)));

            int failureCount = 0;

            // Segments that have been retrieved from cache this cycle. Allows
            // us to reduce calls to the external cache.
            Map<SegmentHeader, SegmentBody> headerBodies =
                new HashMap<>();

            // Load each suggested segment from cache, and place it in
            // thread-local cache. Note that this step can't be done by the
            // cacheMgr -- it's our cache.
            for (SegmentHeader header : response.cacheSegments) {
                final SegmentBody body = cacheMgr.compositeCache.get(header);
                if (body == null) {
                    // REVIEW: This is an async call. It will return before the
                    // index is informed that this header is there,
                    // so a LoadBatchCommand might still return
                    // it on the next iteration.
                    if (cube.getStar() != null) {
                        cacheMgr.remove(cube.getStar(), header);
                    }
                    ++failureCount;
                    continue;
                }
                headerBodies.put(header, body);
                final SegmentWithData segmentWithData =
                    response.convert(header, body);
                segmentWithData.getStar().register(segmentWithData);
            }

            // Perform each suggested rollup.
            //
            // TODO this could be improved.
            // See http://jira.pentaho.com/browse/MONDRIAN-1195

            // Rollups that succeeded. Will tell cache mgr to put the headers
            // into the index and the header/bodies in cache.
            final Map<SegmentHeader, SegmentBody> succeededRollups =
                new HashMap<>();

            for (final BatchLoader.RollupInfo rollup : response.rollups) {
                // Gather the required segments.
                Map<SegmentHeader, SegmentBody> map =
                    findResidentRollupCandidate(headerBodies, rollup);
                if (map == null) {
                    // None of the candidate segment-sets for this rollup was
                    // all present in the cache.
                    continue;
                }

                final Set<String> keepColumns = new HashSet<>();
                for (RolapStar.Column column : rollup.constrainedColumns) {
                    keepColumns.add(
                        genericExpression(column.getExpression()));
                }
                Pair<SegmentHeader, SegmentBody> rollupHeaderBody =
                    SegmentBuilder.rollup(
                        map,
                        keepColumns,
                        rollup.constrainedColumnsBitKey,
                        rollup.measure.getAggregator().getRollup(),
                        rollup.measure.getDatatype(),
                        cacheMgr.getContext().getConfigValue(ConfigConstants.SPARSE_SEGMENT_COUNT_THRESHOLD, ConfigConstants.SPARSE_SEGMENT_COUNT_THRESHOLD_DEFAULT_VALUE ,Integer.class),
                        cacheMgr.getContext().getConfigValue(ConfigConstants.SPARSE_SEGMENT_DENSITY_THRESHOLD, ConfigConstants.SPARSE_SEGMENT_DENSITY_THRESHOLD_DEFAULT_VALUE ,Double.class));

                final SegmentHeader header = rollupHeaderBody.left;
                final SegmentBody body = rollupHeaderBody.right;

                if (headerBodies.containsKey(header)) {
                    // We had already created this segment, somehow.
                    continue;
                }

                headerBodies.put(header, body);
                succeededRollups.put(header, body);

                final SegmentWithData segmentWithData =
                    response.convert(header, body);

                // Register this segment with the local star.
                segmentWithData.getStar().register(segmentWithData);

                // Make sure that the cache manager knows about this new
                // segment. First thing we do is to add it to the index.
                // Then we insert the segment body into the SlotFuture.
                // This has to be done on the SegmentCacheManager's
                // Actor thread to ensure thread safety.
                if (!cacheMgr.getContext().getConfigValue(ConfigConstants.DISABLE_CACHING, ConfigConstants.DISABLE_CACHING_DEFAULT_VALUE, Boolean.class)) {
                    final Locus locus = LocusImpl.peek();
                    cacheMgr.execute(
                        new CacheCommand<Void>() {
                            @Override
							public Void call() throws Exception {
                                SegmentCacheIndex index =
                                        ((SegmentCacheIndexRegistry)cacheMgr.getIndexRegistry())
                                    .getIndex(segmentWithData.getStar());
                                index.add(
                                    segmentWithData.getHeader(),
                                    response.converterMap.get(
                                        SegmentCacheIndexImpl
                                            .makeConverterKey(
                                                segmentWithData.getHeader())),
                                    true);
                                index.loadSucceeded(
                                    segmentWithData.getHeader(), body);
                                return null;
                            }
                            @Override
							public Locus getLocus() {
                                return locus;
                            }
                        });
                }
            }

            // Wait for SQL statements to end -- but only if there are no
            // failures.
            //
            // If there are failures, and its the first iteration, it's more
            // urgent that we create and execute a follow-up request. We will
            // wait for the pending SQL statements at the end of that.
            //
            // If there are failures on later iterations, wait for SQL
            // statements to end. The cache might be porous. SQL might be the
            // only way to make progress.
            sqlSegmentMapFutures.addAll(response.sqlSegmentMapFutures);
            if (failureCount == 0 || iteration > 0) {
                // Wait on segments being loaded by someone else.
                for (Map.Entry<SegmentHeader, Future<SegmentBody>> entry
                    : response.futures.entrySet())
                {
                    final SegmentHeader header = entry.getKey();
                    final Future<SegmentBody> bodyFuture = entry.getValue();
                    final SegmentBody body = Util.safeGet(
                        bodyFuture,
                        "Waiting for someone else's segment to load via SQL");
                    final SegmentWithData segmentWithData =
                        response.convert(header, body);
                    segmentWithData.getStar().register(segmentWithData);
                }

                // Wait on segments being loaded by SQL statements we asked for.
                for (Future<Map<Segment, SegmentWithData>> sqlSegmentMapFuture
                    : sqlSegmentMapFutures)
                {
                    final Map<Segment, SegmentWithData> segmentMap =
                        Util.safeGet(
                            sqlSegmentMapFuture,
                            "Waiting for segment to load via SQL");
                    for (SegmentWithData segmentWithData : segmentMap.values())
                    {
                        segmentWithData.getStar().register(segmentWithData);
                    }
                    // TODO: also pass back SegmentHeader and SegmentBody,
                    // and add these to headerBodies. Might help?
                }
            }

            if (failureCount == 0) {
                break;
            }

            // Figure out which cell requests are not satisfied by any of the
            // segments retrieved.
            @SuppressWarnings("unchecked")
            List<CellRequest> old = new ArrayList<>(cellRequests1);
            cellRequests1.clear();
            for (CellRequest cellRequest : old) {
                if (cellRequest.getMeasure().getStar()
                    .getCellFromCache(cellRequest, null) == null)
                {
                    cellRequests1.add(cellRequest);
                }
            }

            if (cellRequests1.isEmpty()) {
                break;
            }

            if (cellRequests1.size() >= old.size()
                && iteration > 10)
            {
                throw Util.newError(
                    new StringBuilder("Cache round-trip did not resolve any cell requests. ")
                        .append("Iteration #").append(iteration)
                        .append("; request count ").append(cellRequests1.size())
                        .append("; requested headers: ").append(response.cacheSegments.size())
                        .append("; requested rollups: ").append(response.rollups.size())
                        .append("; requested SQL: ")
                        .append(response.sqlSegmentMapFutures.size()).toString());
            }

            // Continue loop; form and execute a new request with the smaller
            // set of cell requests.
        }

        dirty = false;
        cellRequests.clear();
        return true;
    }

    /**
     * Iterates through cell requests and makes sure .getCardinality has
     * been called on all constrained columns.  This is a  workaround
     * to an issue in which cardinality queries can be fired on the Actor
     * thread, potentially causing a deadlock when interleaved with
     * other threads that depend both on db connections and Actor responses.
     *
     */
    private void preloadColumnCardinality(List<CellRequest> cellRequests) {
        List<BitKey> loaded = new ArrayList<>();
        for (CellRequest req : cellRequests) {
            if (!loaded.contains(req.getConstrainedColumnsBitKey())) {
                for (RolapStar.Column col : req.getConstrainedColumns()) {
                    col.getCardinality();
                }
                loaded.add(req.getConstrainedColumnsBitKey());
            }
        }
    }

    /**
     * Finds a segment-list among a list of candidate segment-lists
     * for which the bodies of all segments are in cache. Returns a map
     * from segment-to-body if found, or null if not found.
     *
     * @param headerBodies Cache of bodies previously retrieved from external
     *                     cache
     *
     * @param rollup       Specifies what segments to roll up, and the
     *                     target dimensionality
     *
     * @return Collection of segment headers and bodies suitable for rollup,
     * or null
     */
    private Map<SegmentHeader, SegmentBody> findResidentRollupCandidate(
        Map<SegmentHeader, SegmentBody> headerBodies,
        BatchLoader.RollupInfo rollup)
    {
        candidateLoop:
        for (List<SegmentHeader> headers : rollup.candidateLists) {
            final Map<SegmentHeader, SegmentBody> map =
                new HashMap<>();
            for (SegmentHeader header : headers) {
                SegmentBody body = loadSegmentFromCache(headerBodies, header);
                if (body == null) {
                    // To proceed with a candidate, require all headers to
                    // be in cache.
                    continue candidateLoop;
                }
                map.put(header, body);
            }
            return map;
        }
        return null;
    }

    private SegmentBody loadSegmentFromCache(
        Map<SegmentHeader, SegmentBody> headerBodies,
        SegmentHeader header)
    {
        SegmentBody body = headerBodies.get(header);
        if (body != null) {
            return body;
        }
        body = cacheMgr.compositeCache.get(header);
        if (body == null) {
            if (cube.getStar() != null) {
                cacheMgr.remove(cube.getStar(), header);
            }
            return null;
        }
        headerBodies.put(header, body);
        return body;
    }

    /**
     * Returns the SQL dialect. Overridden in some unit tests.
     *
     * @return Dialect
     */
    public Dialect getDialect() {

        final RolapStar star = cube.getStar();
        if (star != null) {
            return star.getSqlQueryDialect();
        } else {
            return execution.getMondrianStatement().getMondrianConnection().getContext().getDialect();
        }
    }

    /**
     * Sets the flag indicating that the reader has told a lie.
     */
    void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

}

