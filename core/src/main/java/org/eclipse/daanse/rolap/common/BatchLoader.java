/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common;

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.genericExpression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.Future;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.CacheCommand;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.ISegmentCacheManager;
import org.eclipse.daanse.olap.api.Locus;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.rolap.common.agg.AggregationKey;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.agg.ListColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.LiteralStarPredicate;
import org.eclipse.daanse.rolap.common.agg.Segment;
import org.eclipse.daanse.rolap.common.agg.SegmentBuilder;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager.SegmentCacheIndexRegistry;
import org.eclipse.daanse.rolap.common.agg.SegmentLoader;
import org.eclipse.daanse.rolap.common.agg.SegmentWithData;
import org.eclipse.daanse.rolap.common.agg.ValueColumnPredicate;
import org.eclipse.daanse.rolap.common.aggmatcher.AggGen;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.cache.SegmentCacheIndex;
import org.eclipse.daanse.rolap.common.cache.SegmentCacheIndexImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Context for processing a request to the cache manager for segments matching a
 * collection of cell requests. All methods except the constructor are executed
 * by the cache manager's dedicated thread.
 */
public class BatchLoader {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(BatchLoader.class);

    private final Locus locus;
    private final SegmentCacheManager cacheMgr;
    private final Dialect dialect;
    private final RolapCube cube;

    private final Map<AggregationKey, Batch> batches =
        new HashMap<>();

    private final Set<SegmentHeader> cacheHeaders =
        new LinkedHashSet<>();

    private final Map<SegmentHeader, Future<SegmentBody>> futures =
        new HashMap<>();

    private final List<RollupInfo> rollups = new ArrayList<>();

    private final Set<BitKey> rollupBitmaps = new HashSet<>();

    private final Map<List, SegmentBuilder.SegmentConverter> converterMap =
        new HashMap<>();

    public BatchLoader(
        Locus locus,
        ISegmentCacheManager cacheMgr,
        Dialect dialect,
        RolapCube cube)
    {
        this.locus = locus;
        this.cacheMgr = (SegmentCacheManager)cacheMgr;
        this.dialect = dialect;
        this.cube = cube;
    }

    public final boolean shouldUseGroupingFunction() {
        return cube.getCatalog().getInternalConnection().getContext()
                .getConfigValue(ConfigConstants.ENABLE_GROUPING_SETS, ConfigConstants.ENABLE_GROUPING_SETS_DEFAULT_VALUE, Boolean.class)
            && dialect.supportsGroupingSets();
    }

    private void recordCellRequest2(final CellRequest request) {
        // If there is a segment matching these criteria, write it to the list
        // of found segments, and remove the cell request from the list.
        final AggregationKey key = new AggregationKey(request);

        final SegmentBuilder.SegmentConverterImpl converter =
                new SegmentBuilder.SegmentConverterImpl(key, request);

        boolean success =
            loadFromCaches(request, key, converter);
        // Skip the batch if we already have a rollup for it.
        if (rollupBitmaps.contains(request.getConstrainedColumnsBitKey())) {
            return;
        }

        // As a last resort, we load from SQL.
        if (!success) {
            loadFromSql(request, key, converter);
        }
    }

    /**
     * Loads a cell from caches. If the cell is successfully loaded,
     * we return true.
     */
    private boolean loadFromCaches(
        final CellRequest request,
        final AggregationKey key,
        final SegmentBuilder.SegmentConverterImpl converter)
    {
        if (cacheMgr.getContext().getConfigValue(ConfigConstants.DISABLE_CACHING, ConfigConstants.DISABLE_CACHING_DEFAULT_VALUE, Boolean.class)) {
            // Caching is disabled. Return always false.
            return false;
        }

        // Is request matched by one of the headers we intend to load?
        final Map<String, Comparable> mappedCellValues =
            request.getMappedCellValues();
        final List<String> compoundPredicates =
            request.getCompoundPredicateStrings();

        for (SegmentHeader header : cacheHeaders) {
            if (SegmentCacheIndexImpl.matches(
                    header,
                    mappedCellValues,
                    compoundPredicates))
            {
                // It's likely that the header will be in the cache, so this
                // request will be satisfied. If not, the header will be removed
                // from the segment index, and we'll be back.
                return true;
            }
        }
        final RolapStar.Measure measure = request.getMeasure();
        final RolapStar star = measure.getStar();
        final RolapCatalog catalog = star.getCatalog();
        final SegmentCacheIndex index =
            ((SegmentCacheIndexRegistry)cacheMgr.getIndexRegistry()).getIndex(star);
        final List<SegmentHeader> headersInCache =
            index.locate(
                catalog.getName(),
                catalog.getChecksum(),
                measure.getCubeName(),
                measure.getName(),
                star.getFactTable().getAlias(),
                request.getConstrainedColumnsBitKey(),
                mappedCellValues,
                compoundPredicates);

        // Ask for the first segment to be loaded from cache. (If it's no longer
        // in cache, we'll be back, and presumably we'll try the second
        // segment.)

        if (!headersInCache.isEmpty()) {
            for (SegmentHeader headerInCache : headersInCache) {
                final Future<SegmentBody> future =
                    index.getFuture(locus.getExecution(), headerInCache);

                if (future != null) {
                    // Segment header is in cache, body is being loaded.
                    // Worker will need to wait for load to complete.
                    futures.put(headerInCache, future);
                } else {
                    // Segment is in cache.
                    cacheHeaders.add(headerInCache);
                }

                index.setConverter(
                    headerInCache.schemaName,
                    headerInCache.schemaChecksum,
                    headerInCache.cubeName,
                    headerInCache.rolapStarFactTableName,
                    headerInCache.measureName,
                    headerInCache.compoundPredicates,
                    converter);

                converterMap.put(
                    SegmentCacheIndexImpl.makeConverterKey(request),
                    converter);
            }
            return true;
        }

        // Try to roll up if the measure's rollup aggregator supports
        // "fast" aggregation from raw objects.
        //
        // Do not try to roll up if this request has already chosen a rollup
        // with the same target dimensionality. It is quite likely that the
        // other rollup will satisfy this request, and it's complicated to be
        // 100% sure. If we're wrong, we'll be back.

        // Also make sure that we don't try to rollup a measure which
        // doesn't support rollup from raw data, like a distinct count
        // for example. Both the measure's aggregator and its rollup
        // aggregator must support raw data aggregation. We call
        // Aggregator.supportsFastAggregates() to verify.
        Boolean enableInMemoryRollup = cube.getCatalog().getInternalConnection().getContext()
                .getConfigValue(ConfigConstants.ENABLE_IN_MEMORY_ROLLUP, ConfigConstants.ENABLE_IN_MEMORY_ROLLUP_DEFAULT_VALUE ,Boolean.class);
        if (enableInMemoryRollup
            && measure.getAggregator().supportsFastAggregates(
                    EnumConvertor.toDataTypeJdbc(measure.getDatatype()))
            && measure.getAggregator().getRollup().supportsFastAggregates(
                    EnumConvertor.toDataTypeJdbc(measure.getDatatype()))
            && !isRequestCoveredByRollups(request))
        {
            // Don't even bother doing a segment lookup if we can't
            // rollup that measure.
            final List<List<SegmentHeader>> rollup =
                index.findRollupCandidates(
                    catalog.getName(),
                    catalog.getChecksum(),
                    measure.getCubeName(),
                    measure.getName(),
                    star.getFactTable().getAlias(),
                    request.getConstrainedColumnsBitKey(),
                    mappedCellValues,
                    request.getCompoundPredicateStrings());
            if (!rollup.isEmpty()) {
                rollups.add(
                    new RollupInfo(
                        request,
                        rollup));
                rollupBitmaps.add(request.getConstrainedColumnsBitKey());
                converterMap.put(
                    SegmentCacheIndexImpl.makeConverterKey(request),
                    new SegmentBuilder.StarSegmentConverter(
                        measure,
                        key.getCompoundPredicateList()));
                return true;
            }
        }
        return false;
    }

      /**
       * Checks if the request can be satisfied by a rollup already in place
       * and moves that rollup to the top of the list if not there.
       */
      private boolean isRequestCoveredByRollups(CellRequest request) {
          BitKey bitKey = request.getConstrainedColumnsBitKey();
          if (!rollupBitmaps.contains(bitKey)) {
              return false;
          }
          List<SegmentHeader> firstOkList = null;
          for (RollupInfo rollupInfo : rollups) {
              if (!rollupInfo.constrainedColumnsBitKey.equals(bitKey)) {
                  continue;
              }
              int candidateListsIdx = 0;
              // bitkey is the same, are the constrained values compatible?
              candidatesLoop:
                  for (List<SegmentHeader> candList
                      : rollupInfo.candidateLists)
                  {
                      for (SegmentHeader header : candList) {
                          if (headerCoversRequest(header, request)) {
                              firstOkList = candList;
                              break candidatesLoop;
                          }
                      }
                      candidateListsIdx++;
                  }
              if (firstOkList != null) {
                  if (candidateListsIdx > 0) {
                      // move good candidate list to first position
                      rollupInfo.candidateLists.remove(candidateListsIdx);
                      rollupInfo.candidateLists.set(0, firstOkList);
                  }
                  return true;
              }
          }
          return false;
      }

      /**
       * Check constraint compatibility
       */
      private boolean headerCoversRequest(
          SegmentHeader header,
          CellRequest request)
      {
          BitKey bitKey = request.getConstrainedColumnsBitKey();
          assert header.getConstrainedColumnsBitKey().cardinality()
                >= bitKey.cardinality();
          BitKey headerBitKey = header.getConstrainedColumnsBitKey();
          // get all constrained values for relevant bitKey positions
          List<SortedSet<Comparable>> headerValues =
              new ArrayList<>(bitKey.cardinality());
          Map<Integer, Integer> valueIndexes = new HashMap<>();
          int relevantCCIdx = 0;
          int keyValuesIdx = 0;
          for (int bitPos : headerBitKey) {
              if (bitKey.get(bitPos)) {
                  headerValues.add(
                      header.getConstrainedColumns().get(relevantCCIdx).values);
                  valueIndexes.put(bitPos, keyValuesIdx++);
              }
              relevantCCIdx++;
          }
          assert request.getConstrainedColumns().length
              == request.getSingleValues().length;
          // match header constraints against request values
          for (int i = 0; i < request.getConstrainedColumns().length; i++) {
              RolapStar.Column col = request.getConstrainedColumns()[i];
              Integer valueIdx = valueIndexes.get(col.getBitPosition());
              if (headerValues.get(valueIdx) != null
                  && !headerValues.get(valueIdx).contains(
                      request.getSingleValues()[i]))
              {
                return false;
              }
          }
          return true;
      }

    private void loadFromSql(
        final CellRequest request,
        final AggregationKey key,
        final SegmentBuilder.SegmentConverterImpl converter)
    {
        // Finally, add to a batch. It will turn in to a SQL request.
        Batch batch = batches.get(key);
        if (batch == null) {
            batch = new Batch(request);
            batches.put(key, batch);
            converterMap.put(
                SegmentCacheIndexImpl.makeConverterKey(request),
                converter);

            if (LOGGER.isDebugEnabled()) {
                StringBuilder buf = new StringBuilder(100);
                buf.append("FastBatchingCellReader: bitkey=");
                buf.append(request.getConstrainedColumnsBitKey());
                buf.append(Util.NL);

                for (RolapStar.Column column
                    : request.getConstrainedColumns())
                {
                    buf.append("  ");
                    buf.append(column);
                    buf.append(Util.NL);
                }
                LOGGER.debug(buf.toString());
            }
        }
        batch.add(request);
    }

    /**
     * Determines which segments need to be loaded from external cache,
     * created using roll up, or created using SQL to satisfy a given list
     * of cell requests.
     *
     * @return List of segment futures. Each segment future may or may not be
     *    already present (it depends on the current location of the segment
     *    body). Each future will return a not-null segment (or throw).
     */
    LoadBatchResponse load(List<CellRequest> cellRequests) {
        // Check for cancel/timeout. The request might have been on the queue
        // for a while.
        if (locus.getExecution() != null) {
            locus.getExecution().checkCancelOrTimeout();
        }

        final long t1 = System.currentTimeMillis();

        // Now we're inside the cache manager, we can see which of our cell
        // requests can be answered from cache. Those that can will be added
        // to the segments list; those that can not will be converted into
        // batches and rolled up or loaded using SQL.
        for (CellRequest cellRequest : cellRequests) {
            recordCellRequest2(cellRequest);
        }

        // Sort the batches into deterministic order.
        List<Batch> batchList =
            new ArrayList<>(batches.values());
        Collections.sort(batchList, BatchComparator.instance);
        final List<Future<Map<Segment, SegmentWithData>>> segmentMapFutures =
            new ArrayList<>();
        if (shouldUseGroupingFunction()) {
            LOGGER.debug("Using grouping sets");
            List<CompositeBatch> groupedBatches = groupBatches(batchList);
            for (CompositeBatch batch : groupedBatches) {
                batch.load(segmentMapFutures);
            }
        } else {
            // Load batches in turn.
            for (Batch batch : batchList) {
                batch.loadAggregation(segmentMapFutures);
            }
        }

        if (LOGGER.isDebugEnabled()) {
            final long t2 = System.currentTimeMillis();
            LOGGER.debug("load (millis): {}", (t2 - t1));
        }

        // Create a response and return it to the client. The response is a
        // bunch of work to be done (waiting for segments to load from SQL, to
        // come from cache, and so forth) on the client's time. Some of the bets
        // may not come off, in which case, the client will send us another
        // request.
        return new LoadBatchResponse(
            cellRequests,
            new ArrayList<>(cacheHeaders),
            rollups,
            converterMap,
            segmentMapFutures,
            futures);
    }

    public static List<CompositeBatch> groupBatches(List<Batch> batchList) {
        Map<AggregationKey, CompositeBatch> batchGroups =
            new HashMap<>();
        for (int i = 0; i < batchList.size(); i++) {
            for (int j = i + 1; j < batchList.size();) {
                final Batch iBatch = batchList.get(i);
                final Batch jBatch = batchList.get(j);
                if (iBatch.canBatch(jBatch)) {
                    batchList.remove(j);
                    addToCompositeBatch(batchGroups, iBatch, jBatch);
                } else if (jBatch.canBatch(iBatch)) {
                    batchList.set(i, jBatch);
                    batchList.remove(j);
                    addToCompositeBatch(batchGroups, jBatch, iBatch);
                    j = i + 1;
                } else {
                    j++;
                }
            }
        }

        wrapNonBatchedBatchesWithCompositeBatches(batchList, batchGroups);
        final CompositeBatch[] compositeBatches =
            batchGroups.values().toArray(
                new CompositeBatch[batchGroups.size()]);
        Arrays.sort(compositeBatches, CompositeBatchComparator.instance);
        return Arrays.asList(compositeBatches);
    }

    private static void wrapNonBatchedBatchesWithCompositeBatches(
        List<Batch> batchList,
        Map<AggregationKey, CompositeBatch> batchGroups)
    {
        for (Batch batch : batchList) {
            if (batchGroups.get(batch.batchKey) == null) {
                batchGroups.put(batch.batchKey, new CompositeBatch(batch));
            }
        }
    }

    public static void addToCompositeBatch(
        Map<AggregationKey, CompositeBatch> batchGroups,
        Batch detailedBatch,
        Batch summaryBatch)
    {
        CompositeBatch compositeBatch = batchGroups.get(detailedBatch.batchKey);

        if (compositeBatch == null) {
            compositeBatch = new CompositeBatch(detailedBatch);
            batchGroups.put(detailedBatch.batchKey, compositeBatch);
        }

        CompositeBatch compositeBatchOfSummaryBatch =
            batchGroups.remove(summaryBatch.batchKey);

        if (compositeBatchOfSummaryBatch != null) {
            compositeBatch.merge(compositeBatchOfSummaryBatch);
        } else {
            compositeBatch.add(summaryBatch);
        }
    }

    /**
     * Command that loads the segments required for a collection of cell
     * requests. Returns the collection of segments.
     */
    public static class LoadBatchCommand implements CacheCommand<LoadBatchResponse>
    {
        private final Locus locus;
        private final SegmentCacheManager cacheMgr;
        private final Dialect dialect;
        private final RolapCube cube;
        private final List<CellRequest> cellRequests;

        public LoadBatchCommand(
            Locus locus,
            SegmentCacheManager cacheMgr,
            Dialect dialect,
            RolapCube cube,
            List<CellRequest> cellRequests)
        {
            this.locus = locus;
            this.cacheMgr = cacheMgr;
            this.dialect = dialect;
            this.cube = cube;
            this.cellRequests = cellRequests;
        }

        @Override
        public LoadBatchResponse call() {
            return new BatchLoader(locus, cacheMgr, dialect, cube)
                .load(cellRequests);
        }

        @Override
        public Locus getLocus() {
            return locus;
        }
    }

    /**
     * Set of Batches which can grouped together.
     */
    public static class CompositeBatch {
        /** Batch with most number of constraint columns */
        public final Batch detailedBatch;

        /** Batches whose data can be fetched using rollup on detailed batch */
        public final List<Batch> summaryBatches = new ArrayList<>();

        public CompositeBatch(Batch detailedBatch) {
            this.detailedBatch = detailedBatch;
        }

        public void add(Batch summaryBatch) {
            summaryBatches.add(summaryBatch);
        }

        void merge(CompositeBatch summaryBatch) {
            summaryBatches.add(summaryBatch.detailedBatch);
            summaryBatches.addAll(summaryBatch.summaryBatches);
        }

        public void load(
            List<Future<Map<Segment, SegmentWithData>>> segmentFutures)
        {
            GroupingSetsCollector batchCollector =
                new GroupingSetsCollector(true);
            this.detailedBatch.loadAggregation(batchCollector, segmentFutures);

            int cellRequestCount = 0;
            for (Batch batch : summaryBatches) {
                batch.loadAggregation(batchCollector, segmentFutures);
                cellRequestCount += batch.cellRequestCount;
            }

            getSegmentLoader().load(
                cellRequestCount,
                batchCollector.getGroupingSets(),
                detailedBatch.batchKey.getCompoundPredicateList(),
                segmentFutures);
        }

        SegmentLoader getSegmentLoader() {
            return new SegmentLoader(detailedBatch.getCacheMgr());
        }
    }

    private static final Logger BATCH_LOGGER = LoggerFactory.getLogger(BatchLoader.class);

    public static class RollupInfo {
        final RolapStar.Column[] constrainedColumns;
        final BitKey constrainedColumnsBitKey;
        final RolapStar.Measure measure;
        final List<List<SegmentHeader>> candidateLists;

        RollupInfo(
            CellRequest request,
            List<List<SegmentHeader>> candidateLists)
        {
            this.candidateLists = candidateLists;
            constrainedColumns = request.getConstrainedColumns();
            constrainedColumnsBitKey = request.getConstrainedColumnsBitKey();
            measure = request.getMeasure();
        }
    }

    /**
     * Request sent from cache manager to a worker to load segments into
     * the cache, create segments by rolling up, and to wait for segments
     * being loaded via SQL.
     */
    static class LoadBatchResponse {
        /**
         * List of segments that are being loaded using SQL.
         *
         * Other workers are executing the SQL. When done, they will write a
         * segment body or an error into the respective futures. The thread
         * processing this request will wait on those futures, once all segments
         * have successfully arrived from cache.
         */
        final List<Future<Map<Segment, SegmentWithData>>> sqlSegmentMapFutures;

        /**
         * List of segments we are trying to load from the cache.
         */
        final List<SegmentHeader> cacheSegments;

        /**
         * List of cell requests that will be satisfied by segments we are
         * trying to load from the cache (or create by rolling up).
         */
        final List<CellRequest> cellRequests;

        /**
         * List of segments to be created from segments in the cache, provided
         * that the cache segments come through.
         *
         * If they do not, we will need to tell the cache manager to remove
         * the pending segments.
         */
        final List<RollupInfo> rollups;

        final Map<List, SegmentBuilder.SegmentConverter> converterMap;

        final Map<SegmentHeader, Future<SegmentBody>> futures;

        LoadBatchResponse(
            List<CellRequest> cellRequests,
            List<SegmentHeader> cacheSegments,
            List<RollupInfo> rollups,
            Map<List, SegmentBuilder.SegmentConverter> converterMap,
            List<Future<Map<Segment, SegmentWithData>>> sqlSegmentMapFutures,
            Map<SegmentHeader, Future<SegmentBody>> futures)
        {
            this.cellRequests = cellRequests;
            this.sqlSegmentMapFutures = sqlSegmentMapFutures;
            this.cacheSegments = cacheSegments;
            this.rollups = rollups;
            this.converterMap = converterMap;
            this.futures = futures;
        }

        public SegmentWithData convert(
            SegmentHeader header,
            SegmentBody body)
        {
            final SegmentBuilder.SegmentConverter converter =
                converterMap.get(
                    SegmentCacheIndexImpl.makeConverterKey(header));
            return converter.convert(header, body);
        }
    }

    public class Batch {
        // the CellRequest's constrained columns
        final RolapStar.Column[] columns;
        final List<RolapStar.Measure> measuresList =
            new ArrayList<>();
        final Set<StarColumnPredicate>[] valueSets;
        public final AggregationKey batchKey;
        // string representation; for debug; set lazily in toString
        private String string;
        private int cellRequestCount;
        private List<StarColumnPredicate[]> tuples =
            new ArrayList<>();

        public Batch(CellRequest request) {
            columns = request.getConstrainedColumns();
            valueSets = new HashSet[columns.length];
            for (int i = 0; i < valueSets.length; i++) {
                valueSets[i] = new HashSet<>();
            }
            batchKey = new AggregationKey(request);
        }

        @Override
        public String toString() {
            if (string == null) {
                final StringBuilder buf = new StringBuilder();
                buf.append("Batch {\n")
                    .append("  columns={").append(Arrays.toString(columns))
                    .append("}\n")
                    .append("  measures={").append(measuresList).append("}\n")
                    .append("  valueSets={").append(Arrays.toString(valueSets))
                    .append("}\n")
                    .append("  batchKey=").append(batchKey).append("}\n")
                    .append("}");
                string = buf.toString();
            }
            return string;
        }

        public final void add(CellRequest request) {
            ++cellRequestCount;
            final int valueCount = request.getNumValues();
            final StarColumnPredicate[] tuple =
                new StarColumnPredicate[valueCount];
            for (int j = 0; j < valueCount; j++) {
                final StarColumnPredicate value = request.getValueAt(j);
                valueSets[j].add(value);
                tuple[j] = value;
            }
            tuples.add(tuple);
            final RolapStar.Measure measure = request.getMeasure();
            if (!measuresList.contains(measure)) {
                assert (measuresList.isEmpty())
                       || (measure.getStar()
                           == (measuresList.get(0)).getStar())
                    : "Measure must belong to same star as other measures";
                measuresList.add(measure);
            }
        }

        /**
         * Returns the RolapStar associated with the Batch's first Measure.
         *
         * This method can only be called after the {@link #add} method has
         * been called.
         *
         * @return the RolapStar associated with the Batch's first Measure
         */
        private RolapStar getStar() {
            RolapStar.Measure measure = measuresList.get(0);
            return measure.getStar();
        }

        public BitKey getConstrainedColumnsBitKey() {
            return batchKey.getConstrainedColumnsBitKey();
        }

        public SegmentCacheManager getCacheMgr() {
            return cacheMgr;
        }

        public final void loadAggregation(
            List<Future<Map<Segment, SegmentWithData>>> segmentFutures)
        {
            GroupingSetsCollector collectorWithGroupingSetsTurnedOff =
                new GroupingSetsCollector(false);
            loadAggregation(collectorWithGroupingSetsTurnedOff, segmentFutures);
        }

        public final void loadAggregation(
            GroupingSetsCollector groupingSetsCollector,
            List<Future<Map<Segment, SegmentWithData>>> segmentFutures)
        {
            if (cube.getCatalog().getInternalConnection().getContext().getConfigValue(ConfigConstants.GENERATE_AGGREGATE_SQL, ConfigConstants.GENERATE_AGGREGATE_SQL_DEFAULT_VALUE, Boolean.class)) {
                generateAggregateSql();
            }
            boolean optimizePredicates =
                cube.getCatalog().getInternalConnection().getContext().getConfigValue(ConfigConstants.OPTIMIZE_PREDICATES, ConfigConstants.OPTIMIZE_PREDICATES_DEFAULT_VALUE, Boolean.class);
            final StarColumnPredicate[] predicates = initPredicates();
            final long t1 = System.currentTimeMillis();

            // TODO: optimize key sets; drop a constraint if more than x% of
            // the members are requested; whether we should get just the cells
            // requested or expand to a n-cube

            // If the database cannot execute "count(distinct ...)", split the
            // distinct aggregations out.
            int distinctMeasureCount = getDistinctMeasureCount(measuresList);
            boolean tooManyDistinctMeasures =
                distinctMeasureCount > 0
                && !dialect.allowsCountDistinct()
                || distinctMeasureCount > 1
                   && !dialect.allowsMultipleCountDistinct()
                || distinctMeasureCount > 0
                   && !dialect.allowsCountDistinctWithOtherAggs();

            if (tooManyDistinctMeasures) {
                doSpecialHandlingOfDistinctCountMeasures(
                    predicates,
                    groupingSetsCollector,
                    segmentFutures, optimizePredicates);
            }

            // Load agg(distinct <SQL expression>) measures individually
            // for DBs that does allow multiple distinct SQL measures.
            if (!dialect.allowsMultipleDistinctSqlMeasures()) {
                // Note that the intention was originally to capture the
                // subquery SQL measures and separate them out; However,
                // without parsing the SQL string, Mondrian cannot distinguish
                // between "col1" + "col2" and subquery. Here the measure list
                // contains both types.

                // See the test case testLoadDistinctSqlMeasure() in
                //  mondrian.rolap.FastBatchingCellReaderTest

                List<RolapStar.Measure> distinctSqlMeasureList =
                    getDistinctSqlMeasures(measuresList);
                for (RolapStar.Measure measure : distinctSqlMeasureList) {
                    AggregationManager.loadAggregation(
                        cacheMgr,
                        cellRequestCount,
                        Collections.singletonList(measure),
                        columns,
                        batchKey,
                        predicates,
                        groupingSetsCollector,
                        segmentFutures,
                        optimizePredicates);
                    measuresList.remove(measure);
                }
            }

            final int measureCount = measuresList.size();
            if (measureCount > 0) {
                AggregationManager.loadAggregation(
                    cacheMgr,
                    cellRequestCount,
                    measuresList,
                    columns,
                    batchKey,
                    predicates,
                    groupingSetsCollector,
                    segmentFutures,
                    optimizePredicates);
            }

            if (BATCH_LOGGER.isDebugEnabled()) {
                final long t2 = System.currentTimeMillis();
                BATCH_LOGGER.debug(
                    "Batch.load (millis) " + (t2 - t1));
            }
        }

        private void doSpecialHandlingOfDistinctCountMeasures(
            StarColumnPredicate[] predicates,
            GroupingSetsCollector groupingSetsCollector,
            List<Future<Map<Segment, SegmentWithData>>> segmentFutures,
            boolean optimizePredicates)
        {
            while (true) {
                // Scan for a measure based upon a distinct aggregation.
                final RolapStar.Measure distinctMeasure =
                    getFirstDistinctMeasure(measuresList);
                if (distinctMeasure == null) {
                    break;
                }
                final String expr =
                    genericExpression(distinctMeasure.getExpression());
                final List<RolapStar.Measure> distinctMeasuresList =
                    new ArrayList<>();
                for (int i = 0; i < measuresList.size();) {
                    final RolapStar.Measure measure = measuresList.get(i);
                    if (measure.getAggregator().isDistinct()
                        && genericExpression(measure.getExpression())
                        .equals(expr))
                    {
                        measuresList.remove(i);
                        distinctMeasuresList.add(distinctMeasure);
                    } else {
                        i++;
                    }
                }

                // Load all the distinct measures based on the same expression
                // together
                AggregationManager.loadAggregation(
                    cacheMgr,
                    cellRequestCount,
                    distinctMeasuresList,
                    columns,
                    batchKey,
                    predicates,
                    groupingSetsCollector,
                    segmentFutures,
                    optimizePredicates);
            }
        }

        private StarColumnPredicate[] initPredicates() {
            StarColumnPredicate[] predicates =
                new StarColumnPredicate[columns.length];
            for (int j = 0; j < columns.length; j++) {
                Set<StarColumnPredicate> valueSet = valueSets[j];

                StarColumnPredicate predicate;
                if (valueSet == null) {
                    predicate = LiteralStarPredicate.FALSE;
                } else {
                    ValueColumnPredicate[] values =
                        valueSet.toArray(
                            new ValueColumnPredicate[valueSet.size()]);
                    // Sort array to achieve determinism in generated SQL.
                    Arrays.sort(
                        values,
                        ValueColumnConstraintComparator.instance);

                    predicate =
                        new ListColumnPredicate(
                            columns[j],
                            Arrays.asList((StarColumnPredicate[]) values));
                }

                predicates[j] = predicate;
            }
            return predicates;
        }

        private void generateAggregateSql() {
            if (cube == null || cube instanceof RolapVirtualCube) {
                final StringBuilder buf = new StringBuilder(64);
                buf.append(
                    "AggGen: Sorry, can not create SQL for virtual Cube \"")
                    .append(cube == null ? null : cube.getName())
                    .append("\", operation not currently supported");
                String msg = buf.toString();
                BATCH_LOGGER.error(msg);

            } else {
                final AggGen aggGen =
                    new AggGen(cube.getName(), cube.getStar(), columns);
                if (aggGen.isReady()) {
                    // PRINT TO STDOUT - DO NOT USE BATCH_LOGGER
                    LOGGER.debug(
                        "createLost:{}{}", Util.NL, aggGen.createLost());
                    LOGGER.debug(
                        "insertIntoLost:{}{}", Util.NL, aggGen.insertIntoLost());
                    LOGGER.debug(
                        "createCollapsed:{}{}", Util.NL, aggGen.createCollapsed());
                    LOGGER.debug("insertIntoCollapsed: {}{}", Util.NL, aggGen.insertIntoCollapsed());
                } else {
                    BATCH_LOGGER.error("AggGen failed");
                }
            }
        }

        /**
         * Returns the first measure based upon a distinct aggregation, or null
         * if there is none.
         */
        final RolapStar.Measure getFirstDistinctMeasure(
            List<RolapStar.Measure> measuresList)
        {
            for (RolapStar.Measure measure : measuresList) {
                if (measure.getAggregator().isDistinct()) {
                    return measure;
                }
            }
            return null;
        }

        /**
         * Returns the number of the measures based upon a distinct
         * aggregation.
         */
        private int getDistinctMeasureCount(
            List<RolapStar.Measure> measuresList)
        {
            int count = 0;
            for (RolapStar.Measure measure : measuresList) {
                if (measure.getAggregator().isDistinct()) {
                    ++count;
                }
            }
            return count;
        }

        /**
         * Returns the list of measures based upon a distinct aggregation
         * containing SQL measure expressions(as opposed to column expressions).
         *
         * This method was initially intended for only those measures that are
         * defined using subqueries(for DBs that support them). However, since
         * Mondrian does not parse the SQL string, the method will count both
         * queries as well as some non query SQL expressions.
         */
        private List<RolapStar.Measure> getDistinctSqlMeasures(
            List<RolapStar.Measure> measuresList)
        {
            List<RolapStar.Measure> distinctSqlMeasureList =
                new ArrayList<>();
            for (RolapStar.Measure measure : measuresList) {
                if (measure.getAggregator().isDistinct()
                    && measure.getExpression() instanceof
                    RolapSqlExpression measureExpr)
                {
                    org.eclipse.daanse.olap.api.SqlStatement measureSql = measureExpr.getSqls().get(0);
                    // Checks if the SQL contains "SELECT" to detect the case a
                    // subquery is used to define the measure. This is not a
                    // perfect check, because a SQL expression on column names
                    // containing "SELECT" will also be detected. e,g,
                    // count("select beef" + "regular beef").
                    if (measureSql.getSql().toUpperCase().contains("SELECT")) {
                        distinctSqlMeasureList.add(measure);
                    }
                }
            }
            return distinctSqlMeasureList;
        }

        /**
         * Returns whether another Batch can be batched to this Batch.
         *
         * This is possible if:
         * columns list is super set of other batch's constraint columns;
         *     and
         * both have same Fact Table; and
         * matching columns of this and other batch has the same value; and
         * non matching columns of this batch have ALL VALUES
         *
         */
        public boolean canBatch(Batch other) {
            return hasOverlappingBitKeys(other)
                && constraintsMatch(other)
                && hasSameMeasureList(other)
                && !hasDistinctCountMeasure()
                && !other.hasDistinctCountMeasure()
                && haveSameStarAndAggregation(other)
                && haveSameClosureColumns(other);
        }

        /**
         * Returns whether the constraints on this Batch subsume the constraints
         * on another Batch and therefore the other Batch can be subsumed into
         * this one for GROUPING SETS purposes. Not symmetric.
         *
         * @param other Other batch
         * @return Whether other batch can be subsumed into this one
         */
        private boolean constraintsMatch(Batch other) {
            if (areBothDistinctCountBatches(other)) {
                if (getConstrainedColumnsBitKey().equals(
                        other.getConstrainedColumnsBitKey()))
                {
                    return hasSameCompoundPredicate(other)
                        && haveSameValues(other);
                } else {
                    return hasSameCompoundPredicate(other)
                        || (other.batchKey.getCompoundPredicateList().isEmpty()
                            || equalConstraint(
                                batchKey.getCompoundPredicateList(),
                                other.batchKey.getCompoundPredicateList()))
                        && haveSameValues(other);
                }
            } else {
                return haveSameValues(other);
            }
        }

        private boolean equalConstraint(
            List<StarPredicate> predList1,
            List<StarPredicate> predList2)
        {
            if (predList1.size() != predList2.size()) {
                return false;
            }
            for (int i = 0; i < predList1.size(); i++) {
                StarPredicate pred1 = predList1.get(i);
                StarPredicate pred2 = predList2.get(i);
                if (!pred1.equalConstraint(pred2)) {
                    return false;
                }
            }
            return true;
        }

        private boolean areBothDistinctCountBatches(Batch other) {
            return this.hasDistinctCountMeasure()
                && !this.hasNormalMeasures()
                && other.hasDistinctCountMeasure()
                && !other.hasNormalMeasures();
        }

        private boolean hasNormalMeasures() {
            return getDistinctMeasureCount(measuresList)
                !=  measuresList.size();
        }

        private boolean hasSameMeasureList(Batch other) {
            return this.measuresList.size() == other.measuresList.size()
                   && this.measuresList.containsAll(other.measuresList);
        }

        boolean hasOverlappingBitKeys(Batch other) {
            return getConstrainedColumnsBitKey()
                .isSuperSetOf(other.getConstrainedColumnsBitKey());
        }

        boolean hasDistinctCountMeasure() {
            return getDistinctMeasureCount(measuresList) > 0;
        }

        boolean hasSameCompoundPredicate(Batch other) {
            final StarPredicate starPredicate = compoundPredicate();
            final StarPredicate otherStarPredicate = other.compoundPredicate();
            if (starPredicate == null && otherStarPredicate == null) {
                return true;
            } else if (starPredicate != null && otherStarPredicate != null) {
                return starPredicate.equalConstraint(otherStarPredicate);
            }
            return false;
        }

        private StarPredicate compoundPredicate() {
            StarPredicate predicate = null;
            for (Set<StarColumnPredicate> valueSet : valueSets) {
                StarPredicate orPredicate = null;
                for (StarColumnPredicate starColumnPredicate : valueSet) {
                    if (orPredicate == null) {
                        orPredicate = starColumnPredicate;
                    } else {
                        orPredicate = orPredicate.or(starColumnPredicate);
                    }
                }
                if (predicate == null) {
                    predicate = orPredicate;
                } else {
                    predicate = predicate.and(orPredicate);
                }
            }
            for (StarPredicate starPredicate
                : batchKey.getCompoundPredicateList())
            {
                if (predicate == null) {
                    predicate = starPredicate;
                } else {
                    predicate = predicate.and(starPredicate);
                }
            }
            return predicate;
        }

        boolean haveSameStarAndAggregation(Batch other) {
            boolean[] rollup = {false};
            boolean[] otherRollup = {false};

            boolean hasSameAggregation =
                getAgg(rollup) == other.getAgg(otherRollup);
            boolean hasSameRollupOption = rollup[0] == otherRollup[0];

            boolean hasSameStar = getStar().equals(other.getStar());
            return hasSameStar && hasSameAggregation && hasSameRollupOption;
        }

        /**
         * Returns whether this batch has the same closure columns as another.
         *
         * Ensures that we do not group together a batch that includes a
         * level of a parent-child closure dimension with a batch that does not.
         * It is not safe to roll up from a parent-child closure level; due to
         * multiple accounting, the 'all' level is less than the sum of the
         * members of the closure level.
         *
         * @param other Other batch
         * @return Whether batches have the same closure columns
         */
        boolean haveSameClosureColumns(Batch other) {
            final BitKey cubeClosureColumnBitKey = cube.closureColumnBitKey;
            if (cubeClosureColumnBitKey == null) {
                // Virtual cubes have a null bitkey. For now, punt; should do
                // better.
                return true;
            }
            final BitKey closureColumns =
                this.batchKey.getConstrainedColumnsBitKey()
                    .and(cubeClosureColumnBitKey);
            final BitKey otherClosureColumns =
                other.batchKey.getConstrainedColumnsBitKey()
                    .and(cubeClosureColumnBitKey);
            return closureColumns.equals(otherClosureColumns);
        }

        /**
         * @param rollup Out parameter
         * @return AggStar
         */
        private AggStar getAgg(boolean[] rollup) {
            return AggregationManager.findAgg(
                getStar(),
                getConstrainedColumnsBitKey(),
                makeMeasureBitKey(),
                rollup);
        }

        private BitKey makeMeasureBitKey() {
            BitKey bitKey = getConstrainedColumnsBitKey().emptyCopy();
            for (RolapStar.Measure measure : measuresList) {
                bitKey.set(measure.getBitPosition());
            }
            return bitKey;
        }

        /**
         * Return whether have same values for overlapping columns or
         * has all children for others.
         */
        boolean haveSameValues(
            Batch other)
        {
            for (int j = 0; j < columns.length; j++) {
                boolean isCommonColumn = false;
                for (int i = 0; i < other.columns.length; i++) {
                    if (areSameColumns(other.columns[i], columns[j])) {
                        if (hasSameValues(other.valueSets[i], valueSets[j])) {
                            isCommonColumn = true;
                            break;
                        } else {
                            return false;
                        }
                    }
                }
                if (!isCommonColumn
                    && !hasAllValues(columns[j], valueSets[j]))
                {
                    return false;
                }
            }
            return true;
        }

        private boolean hasAllValues(
            RolapStar.Column column,
            Set<StarColumnPredicate> valueSet)
        {
            return column.getCardinality() == valueSet.size();
        }

        private boolean areSameColumns(
            RolapStar.Column otherColumn,
            RolapStar.Column thisColumn)
        {
            return otherColumn.equals(thisColumn);
        }

        private boolean hasSameValues(
            Set<StarColumnPredicate> otherValueSet,
            Set<StarColumnPredicate> thisValueSet)
        {
            return otherValueSet.equals(thisValueSet);
        }
    }

    public static class CompositeBatchComparator
        implements Comparator<CompositeBatch>
    {
        static final CompositeBatchComparator instance =
            new CompositeBatchComparator();

        @Override
        public int compare(CompositeBatch o1, CompositeBatch o2) {
            return BatchComparator.instance.compare(
                o1.detailedBatch,
                o2.detailedBatch);
        }
    }

    public static class BatchComparator implements Comparator<Batch> {
        static final BatchComparator instance = new BatchComparator();

        private BatchComparator() {
        }

        @Override
        public int compare(
            Batch o1, Batch o2)
        {
            if (o1.columns.length != o2.columns.length) {
                return o1.columns.length - o2.columns.length;
            }
            for (int i = 0; i < o1.columns.length; i++) {
                int c = o1.columns[i].getName().compareTo(
                    o2.columns[i].getName());
                if (c != 0) {
                    return c;
                }
            }
            for (int i = 0; i < o1.columns.length; i++) {
                int c = compare(o1.valueSets[i], o2.valueSets[i]);
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        }

        <T> int compare(Set<T> set1, Set<T> set2) {
            if (set1.size() != set2.size()) {
                return set1.size() - set2.size();
            }
            Iterator<T> iter1 = set1.iterator();
            Iterator<T> iter2 = set2.iterator();
            while (iter1.hasNext()) {
                T v1 = iter1.next();
                T v2 = iter2.next();
                int c = Util.compareKey(v1, v2);
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        }
    }

    public static class ValueColumnConstraintComparator
        implements Comparator<ValueColumnPredicate>
    {
        static final ValueColumnConstraintComparator instance =
            new ValueColumnConstraintComparator();

        private ValueColumnConstraintComparator() {
        }

        @Override
        public int compare(
            ValueColumnPredicate o1,
            ValueColumnPredicate o2)
        {
            Object v1 = o1.getValue();
            Object v2 = o2.getValue();
            if (v1.getClass() == v2.getClass()
                && v1 instanceof Comparable comparable)
            {
                return comparable.compareTo(v2);
            } else {
                return v1.toString().compareTo(v2.toString());
            }
        }
    }

}
