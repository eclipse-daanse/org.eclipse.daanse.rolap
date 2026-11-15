/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2019 Hitachi Vantara.
 * All Rights Reserved.
 *
 * For more information please visit the Project: Hitachi Vantara - Mondrian
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
 *   Stefan Bischof (bipolis.org) - initial
 */

package org.eclipse.daanse.rolap.common.cache;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.api.execution.GuardedStatement;
import org.eclipse.daanse.olap.exceptions.QueryCanceledException;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentColumn;
import org.eclipse.daanse.rolap.common.star.BitKeyExplain;
import org.eclipse.daanse.olap.spi.SegmentRegion;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.spi.SegmentPredicate;
import org.eclipse.daanse.olap.spi.SegmentIdentity;
import org.eclipse.daanse.olap.util.CartesianProductList;
import org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.olap.util.SlotFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The actor-confined implementation of {@link SegmentCacheIndex}: five
 * coupled maps (headers by bitkey shape, by fact table, by region, the
 * per-header info with its load slot and removal flag, and the
 * registrations by client execution) plus the rollup-candidate search
 * with its generation-guarded ancestor memo. Every entry in one map has
 * its counterpart in the others - add/remove/update keep them in step,
 * which is exactly why all access runs on the ONE actor thread of the
 * owning {@code SegmentCacheManager} (checkThread guards it; direct
 * multi-threaded use is a design violation, not a missing lock).
 */
public class SegmentCacheIndexImpl implements SegmentCacheIndex {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SegmentCacheIndexImpl.class);
    private static final String SEGMENT_CACHE_INDEX_IMPL = "SegmentCacheIndexImpl(";

    private final Map<SegmentIdentity, Set<SegmentHeader>> bitkeyMap =
        new HashMap<>();

    /**
     * The fact map allows us to spot quickly which
     * segments have facts relating to a given header.
     */
    private final Map<SegmentIdentity.FactKey, FactInfo> factMap =
        new HashMap<>();

    /**
     * The region fact map allows us to spot quickly which
     * segments have facts relating to a given header, but doesn't
     * consider the compound predicates in the key. This allows
     * flush operations to be consistent.
     */
    // The region map stays: it gives the flush an O(1) lookup instead of
    // an O(#fact keys) scan, and RegionKey has a second user (groupByStar
    // priming). Folding it into the fact map would require every compound
    // predicate to be re-parseable from its wire form, which Not and
    // Opaque predicates are not.
    private final Map<SegmentIdentity.RegionKey, RegionFactInfo> regionFactMap =
        new HashMap<>();

    /** Reverse of HeaderInfo.clients: every query end cancels in O(own headers). */
    private final Map<Execution, Set<SegmentHeader>> headersByClient =
        new HashMap<>();

    private final Map<SegmentHeader, HeaderInfo> headerMap =
        new HashMap<>();

    private final Thread thread;
    private final Executor statementCancelExecutor;

    /**
     * Creates a SegmentCacheIndexImpl. Statement cancels run inline on the
     * caller (tests); production passes an executor.
     *
     * @param thread Thread that must be used to execute commands.
     */
    public SegmentCacheIndexImpl(Thread thread) {
        this(thread, Runnable::run);
    }

    /**
     * @param thread Thread that must be used to execute commands.
     * @param statementCancelExecutor Where JDBC Statement.cancel runs -
     *        a network round-trip that must never run on the actor thread
     */
    public SegmentCacheIndexImpl(Thread thread, Executor statementCancelExecutor) {
        this.thread = thread;
        this.statementCancelExecutor = statementCancelExecutor;
        if (thread == null) {
            throw new IllegalArgumentException("SegmentCacheIndexImpl: thread should be not null");
        }
    }

    public static SegmentIdentity.FactKey makeConverterKey(SegmentHeader header) {
        return header.factKey();
    }

    public static SegmentIdentity.FactKey makeConverterKey(CellRequest request)
    {
        return new SegmentIdentity.FactKey(
            request.getMeasure().getStar().getCatalog().getName(),
            request.getMeasure().getStar().getCatalog().getChecksum(),
            request.getMeasure().getCubeName(),
            request.getMeasure().getStar().getFactTable().getAlias(),
            request.getMeasure().getName(),
            request.getCompoundPredicates());
    }

    @Override
	public List<SegmentHeader> locate(
        SegmentIdentity identity,
        Map<String, Comparable> coordinates)
    {
        checkThread();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                new StringBuilder(SEGMENT_CACHE_INDEX_IMPL)
                    .append(System.identityHashCode(this))
                    .append(")locate:")
                    .append("\nidentity:").append(identity)
                    .append("\ncoordinates:").append(coordinates).toString());
        }

        List<SegmentHeader> list = Collections.emptyList();
        final Set<SegmentHeader> headers = bitkeyMap.get(identity);
        if (headers == null) {
            LOGGER.trace("locate:NOMATCH index={}", System.identityHashCode(this));
            return Collections.emptyList();
        }
        for (SegmentHeader header : headers) {
            final HeaderInfo headerInfo = headerMap.get(header);
            if (headerInfo != null && headerInfo.removeAfterLoad) {
                // flagged stale by a flush; gone once its load finishes
                continue;
            }
            if (matchesCoordinates(header, coordinates)) {
                // Be lazy. Don't allocate a list unless there is at least one
                // entry.
                if (list.isEmpty()) {
                    list = new ArrayList<>();
                }
                list.add(header);
            }
        }
        if (LOGGER.isTraceEnabled()) {
            final StringBuilder sb =
                new StringBuilder(
                    new StringBuilder(SEGMENT_CACHE_INDEX_IMPL)
                        .append(System.identityHashCode(this))
                        .append(").locate:MATCH").toString());
            for (SegmentHeader header : list) {
                sb.append("\n");
                sb.append(header.toString());
            }
            LOGGER.trace(sb.toString());
        }
        return list;
    }

    @Override
	public void add(
        SegmentHeader header,
        boolean loading)
    {
        checkThread();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(new StringBuilder(SEGMENT_CACHE_INDEX_IMPL)
                .append(System.identityHashCode(this))
                .append(").add:\n")
                .append(header).toString());
        }

        HeaderInfo headerInfo = headerMap.get(header);
        if (headerInfo == null) {
            headerInfo = new HeaderInfo();
            if (loading) {
                // We are currently loading this segment. It isnt' in cache.
                // We put a slot into which the data will become available.
                headerInfo.slot = new SlotFuture<>();
            }
            headerMap.put(header, headerInfo);
        }

        final var bitkeyKey = header.identity();
        bitkeyMap.computeIfAbsent(bitkeyKey, k -> new LinkedHashSet<>()).add(header);

        final var factKey = header.factKey();
        FactInfo factInfo = factMap.computeIfAbsent(factKey, k -> new FactInfo());

        if (factInfo.headers.add(header)) {
            final BitKey bitKey = header.getConstrainedColumnsBitKey();
            if (factInfo.bitkeyCounts.merge(bitKey, 1, Integer::sum) == 1) {
                factInfo.ancestorMemo.clear();
            }
        }
        final var regionFactKey = header.regionKey();
        regionFactMap.computeIfAbsent(regionFactKey, k -> new RegionFactInfo())
            .headers.add(header);
    }

    @Override
	public void update(
        SegmentHeader oldHeader,
        SegmentHeader newHeader)
    {
        checkThread();

        LOGGER.trace(
            "SegmentCacheIndexImpl.update: Updating header from:\n{}\n\nto\n\n{}",
            oldHeader, newHeader);
        final HeaderInfo headerInfo = headerMap.get(oldHeader);
        if (headerInfo != null && headerInfo.slot != null && !headerInfo.slot.isDone()) {
            // the segment is STILL LOADING: moving the HeaderInfo would
            // strand its open slot under the new key - loadSucceeded(old)
            // runs into 'data arrived late' and the waiters hang until
            // their query timeout. Fail the slot (waiters reload) and drop
            // the header; the late load result is discarded.
            headerInfo.slot.fail(new IllegalStateException(
                "segment constrained by a flush while loading"));
            remove(oldHeader);
            return;
        }
        headerMap.remove(oldHeader);
        if (headerInfo == null) {
            // nothing to move: seeding the side maps without a headerMap
            // entry would create an unremovable ghost (remove() bails on
            // the missing HeaderInfo and locate would serve it forever)
            return;
        }
        final HeaderInfo displaced = headerMap.put(newHeader, headerInfo);
        if (displaced != null && displaced.slot != null && !displaced.slot.isDone()) {
            // the target header was already loading: its waiters would be
            // orphaned by the overwrite - fail their slot with the cause
            displaced.slot.fail(new IllegalStateException(
                "segment replaced by a constrained flush result while loading"));
        }

        final var oldBitkeyKey = oldHeader.identity();
        final Set<SegmentHeader> headers = bitkeyMap.get(oldBitkeyKey);
        if (headers != null) {
            headers.remove(oldHeader);
            headers.add(newHeader);
        }

        final var oldFactKey = oldHeader.factKey();
        final FactInfo factInfo = factMap.get(oldFactKey);
        if (factInfo != null) {
            factInfo.headers.remove(oldHeader);
            factInfo.headers.add(newHeader);
        }

        final var oldRegionFactKey = oldHeader.regionKey();
        final RegionFactInfo regionFactInfo = regionFactMap.get(oldRegionFactKey);
        if (regionFactInfo != null) {
            regionFactInfo.headers.remove(oldHeader);
            regionFactInfo.headers.add(newHeader);
        }
    }

    @Override
	public void loadSucceeded(SegmentHeader header, SegmentBody body) {
        checkThread();

        final HeaderInfo headerInfo = headerMap.get(header);

        if (headerInfo == null) {
            LOGGER.trace(
                "loadSucceeded: Discarding data for header {}. Data arrived late.",
                header.getUniqueID());
            return;
        }

        if (headerInfo.slot != null && !headerInfo.slot.isDone()) {
            headerInfo.slot.put(body);
        }
        if (headerInfo.removeAfterLoad) {
            remove(header);
        }
        // Cleanup the HeaderInfo. Waiting clients hold the future object
        // itself; keeping the slot here would pin the body strong and defeat
        // the memory cache's soft eviction.
        headerInfo.stmt = null;
        dropClientLinks(header, headerInfo);
        headerInfo.slot = null;
    }

    @Override
	public void loadFailed(SegmentHeader header, Throwable throwable) {
        checkThread();

        final HeaderInfo headerInfo = headerMap.get(header);
        if (headerInfo == null) {
            LOGGER.trace("loadFailed: Missing header {}", header);
            return;
        }
        if (headerInfo.slot == null) {
            // registered but not loading: a rollup already completed this
            // header's slot while the SQL load also owning it failed later.
            // Throwing here (on the actor) skipped the remove and left a
            // registered header with no body anywhere - degrade like the
            // unknown-header case instead.
            LOGGER.debug("loadFailed: header {} is not loading", header.getUniqueID());
            return;
        }
        headerInfo.slot.fail(throwable);
        remove(header);
        // Cleanup the HeaderInfo
        headerInfo.stmt = null;
        dropClientLinks(header, headerInfo);
    }

    @Override
	public void remove(SegmentHeader header) {
        checkThread();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                new StringBuilder(SEGMENT_CACHE_INDEX_IMPL)
                    .append(System.identityHashCode(this))
                    .append(").remove:\n")
                    .append(header.toString()).toString(),
                new Throwable("Removal."));
        } else {
            LOGGER.debug(
                "SegmentCacheIndexImpl.remove:\n{}",
                header);
        }

        final HeaderInfo headerInfo = headerMap.get(header);
        if (headerInfo == null) {
            LOGGER.debug("remove:UNKNOWN HEADER index={}", System.identityHashCode(this));
            return;
        }
        if (headerInfo.slot != null && !headerInfo.slot.isDone()) {
            // Cannot remove while load is pending; flag for removal after load
            headerInfo.removeAfterLoad = true;
            LOGGER.debug("remove:DEFERRED index={}", System.identityHashCode(this));
            return;
        }

        headerMap.remove(header);
        dropClientLinks(header, headerInfo);

        final var factKey = header.factKey();
        final FactInfo factInfo = factMap.get(factKey);
        if (factInfo != null && factInfo.headers.remove(header)) {
            final BitKey bitKey = header.getConstrainedColumnsBitKey();
            final Integer left = factInfo.bitkeyCounts.merge(bitKey, -1, Integer::sum);
            if (left != null && left <= 0) {
                factInfo.bitkeyCounts.remove(bitKey);
                factInfo.ancestorMemo.clear();
            }
            if (factInfo.headers.isEmpty()) {
                factMap.remove(factKey);
            }
        }

        final var regionFactKey = header.regionKey();
        final RegionFactInfo regionFactInfo = regionFactMap.get(regionFactKey);
        if (regionFactInfo != null) {
            regionFactInfo.headers.remove(header);
            if (regionFactInfo.headers.isEmpty()) {
                regionFactMap.remove(regionFactKey);
            }
        }

        final var bitkeyKey = header.identity();
        final Set<SegmentHeader> headers = bitkeyMap.get(bitkeyKey);
        if (headers != null) {
            headers.remove(header);
            if (headers.isEmpty()) {
                bitkeyMap.remove(bitkeyKey);
            }
        }
    }

    private void dropClientLinks(SegmentHeader header, HeaderInfo headerInfo) {
        for (Execution client : headerInfo.clients) {
            final Set<SegmentHeader> headers = headersByClient.get(client);
            if (headers != null) {
                headers.remove(header);
                if (headers.isEmpty()) {
                    headersByClient.remove(client);
                }
            }
        }
        headerInfo.clients.clear();
    }

    /** Test probe: the ancestor memo of the fact the header belongs to. */
    Map<BitKey, List<BitKey>> ancestorMemoForTests(SegmentHeader header) {
        final FactInfo factInfo = factMap.get(header.factKey());
        return factInfo == null ? null : factInfo.ancestorMemo;
    }

    private void checkThread() {
        assert thread == Thread.currentThread()
            : new StringBuilder("expected ").append(thread).append(", but was ")
            .append(Thread.currentThread())
            .toString();
    }

    /**
     * Coordinate containment only — for headers whose compound predicates
     * are already known to match (the bitkey map keys on them).
     */
    public static boolean matchesCoordinates(
        SegmentHeader header,
        Map<String, Comparable> coords)
    {
        // a cell inside one of the excluded region boxes is not served
        if (header.isCellExcluded(coords)) {
            return false;
        }
        for (Map.Entry<String, Comparable> entry : coords.entrySet()) {
            // Check if the dimensionality of the segment intersects
            // with the coordinate.
            final SegmentColumn constrainedColumn =
                header.getConstrainedColumn(entry.getKey());
            if (constrainedColumn == null) {
                // One of the required column/value pairs is not a constraining
                // column for the header. This will not happen if the header
                // has been acquired from bitkeyMap, but may happen if a list
                // of mixed-dimensionality headers is being scanned.
                return false;
            }
            final SortedSet<Comparable> values =
                constrainedColumn.getValues();
            if (values != null
                && !values.contains(entry.getValue()))
            {
                return false;
            }
        }
        return true;
    }

    @Override
	public List<SegmentHeader> intersectRegion(
        SegmentIdentity.RegionKey key,
        SegmentColumn[] region)
    {
        checkThread();

        final RegionFactInfo factInfo = regionFactMap.get(key);
        List<SegmentHeader> list = Collections.emptyList();
        if (factInfo == null) {
            return list;
        }
        for (SegmentHeader header : factInfo.headers) {
            // Don't return stale segments.
            final HeaderInfo headerInfo = headerMap.get(header);
            if (headerInfo != null && headerInfo.removeAfterLoad) {
                continue;
            }
            if (intersects(header, region)) {
                // Be lazy. Don't allocate a list unless there is at least one
                // entry.
                if (list.isEmpty()) {
                    list = new ArrayList<>();
                }
                list.add(header);
            }
        }
        return list;
    }

    private boolean intersects(
        SegmentHeader header,
        SegmentColumn[] region)
    {
        // most selective condition first
        if (region.length == 0) {
            return true;
        }
        // the region is a box: the segment intersects it only when EVERY box
        // column either is absent from the segment (covers all its values),
        // is a wildcard on either side, or shares at least one value
        for (SegmentColumn regionColumn : region) {
            final SegmentColumn headerColumn =
                header.getConstrainedColumn(regionColumn.getColumnExpression());
            if (headerColumn == null) {
                continue;
            }
            final SortedSet<Comparable> regionValues =
                regionColumn.getValues();
            final SortedSet<Comparable> headerValues =
                headerColumn.getValues();
            if (headerValues == null || regionValues == null) {
                continue;
            }
            boolean overlap = false;
            for (Comparable myValue : regionValues) {
                if (headerValues.contains(myValue)) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) {
                return false;
            }
        }
        return true;
    }

    @Override
	public void printCacheState(PrintWriter pw) {
        checkThread();
        // buffered: the caller's writer may be network- or file-bound, and
        // this runs on the actor - foreign I/O must not stall it
        final StringWriter buffer = new StringWriter();
        final PrintWriter out = new PrintWriter(buffer);
        final List<Set<SegmentHeader>> values =
            new ArrayList<>(
                bitkeyMap.values());
        values.sort((o1, o2) -> {
            if (o1.isEmpty()) {
                return -1;
            }
            if (o2.isEmpty()) {
                return 1;
            }
            return o1.iterator().next().getUniqueID()
                .compareTo(o2.iterator().next().getUniqueID());
        });
        for (Set<SegmentHeader> key : values) {
            final List<SegmentHeader> headers =
                new ArrayList<>(key);
            headers.sort(Comparator.comparing(SegmentHeader::getUniqueID));
            for (SegmentHeader header : headers) {
                out.println(header.getDescription());
            }
        }
        out.flush();
        pw.print(buffer);
    }

    @Override
	public Future<SegmentBody> getFuture(Execution exec, SegmentHeader header) {
        checkThread();
        HeaderInfo hi = headerMap.get(header);
        if (hi == null) {
            return null;
        }
        // link the client only while a load is pending: load completion (or
        // remove) drops the links, and an already-loaded header would keep
        // the Execution graph pinned forever
        if (hi.slot != null && hi.clients.add(exec)) {
            headersByClient.computeIfAbsent(exec, k -> new LinkedHashSet<>()).add(header);
        }
        return hi.slot;
    }

    @Override
	public void linkSqlStatement(SegmentHeader header, GuardedStatement stmt) {
        checkThread();
        HeaderInfo hi = headerMap.get(header);
        if (hi != null) {
            hi.stmt = stmt;
        }
    }

    @Override
	public boolean contains(SegmentHeader header) {
        checkThread();
        return headerMap.containsKey(header);
    }

    @Override
	public boolean isRegistered(SegmentHeader header) {
        checkThread();
        final HeaderInfo headerInfo = headerMap.get(header);
        return headerInfo != null && !headerInfo.removeAfterLoad;
    }

    @Override
	public boolean hasInterestedParties(SegmentHeader header) {
        checkThread();
        final HeaderInfo headerInfo = headerMap.get(header);
        if (headerInfo == null) {
            return false;
        }
        // loadSucceeded hands the body to the parked clients before it acts
        // on the flag, so a flagged header with clients is still owed a load
        return !headerInfo.removeAfterLoad || !headerInfo.clients.isEmpty();
    }

    @Override
	public void cancel(Execution exec) {
        checkThread();
        final Set<SegmentHeader> mine = headersByClient.remove(exec);
        if (mine == null) {
            return;
        }
        final List<SegmentHeader> toRemove = new ArrayList<>();
        for (SegmentHeader header : mine) {
            final HeaderInfo headerInfo = headerMap.get(header);
            if (headerInfo == null) {
                continue;
            }
            if (headerInfo.clients.remove(exec)
                && headerInfo.slot != null
                && !headerInfo.slot.isDone()
                && headerInfo.clients.isEmpty())
            {
                toRemove.add(header);
            }
        }
        // Make sure to cleanup the orphaned segments.
        for (SegmentHeader header : toRemove) {
            // the guard is captured BEFORE loadFailed - loadFailed nulls
            // hi.stmt, so a task reading the field at run time would always
            // see null and silently never cancel. The guard's handshake
            // makes the late cancel safe: the loader marks it closed before
            // its pooled connection is recycled, and a cancel after that is
            // a guaranteed no-op with no JDBC call (see GuardedStatement -
            // an isClosed() pre-check would block on most drivers).
            final GuardedStatement stmt = headerMap.get(header).stmt;
            loadFailed(
                header,
                new QueryCanceledException(
                    "Canceling due to an absence of interested parties."));
            // We only want to cancel the statement, but we can't close it.
            // Some drivers will not notice the interruption flag on their
            // own thread before a considerable time has passed. If we were
            // using a pooling layer, calling close() would make the
            // underlying connection available again, despite the first
            // statement still being processed. Some drivers will fail
            // there. It is therefore important to close and release the
            // resources on the proper thread, namely, the thread which
            // runs the actual statement.
            //
            // cancel() itself is a JDBC NETWORK round-trip (PG/MySQL open a
            // new connection for it) - fired off-thread so a dead database
            // cannot stall the actor for n x driver timeout per cancel.
            // stmt is null when the load registered but the SQL thread has
            // not linked its statement yet.
            if (stmt != null) {
                try {
                    statementCancelExecutor.execute(stmt::cancel);
                } catch (RuntimeException e) {
                    // a rejected submit (executor shut down) must not abort
                    // the cleanup loop - the remaining headers still need
                    // their loadFailed
                    LOGGER.warn("statement cancel task rejected", e);
                }
            }
        }
    }


    @Override
	public List<List<SegmentHeader>> findRollupCandidates(
        SegmentIdentity identity,
        Map<String, Comparable> coordinates)
    {
        checkThread();
        final BitKey constrainedColsBitKey = identity.constrainedColsBitKey();
        final List<SegmentPredicate> compoundPredicates = identity.compoundPredicates();
        final FactInfo factInfo = factMap.get(identity.factKey());
        if (factInfo == null) {
            return Collections.emptyList();
        }

        // Iterate over all dimensionalities that are a superset of the desired
        // columns and for which a segment is known to exist.
        //
        // It helps that getAncestors returns dimensionalities with fewer bits
        // set first. These will contain fewer cells, and therefore be less
        // effort to roll up.

        final List<List<SegmentHeader>> list =
            new ArrayList<>();
        final List<BitKey> ancestors = factInfo.ancestorMemo
            .computeIfAbsent(constrainedColsBitKey, factInfo::ancestorsOf);
        for (BitKey bitKey : ancestors) {
            final var bitkeyKey = new SegmentIdentity(
                identity.schemaName(),
                identity.schemaChecksum(),
                identity.cubeName(),
                identity.rolapStarFactTableName(),
                identity.measureName(),
                compoundPredicates,
                bitKey);
            final Set<SegmentHeader> headers = bitkeyMap.get(bitkeyKey);
            if (headers == null) {
                // dimensionality exists for other compound predicates only
                continue;
            }

            // For columns that are still present after roll up, make sure that
            // the required value is in the range covered by the segment.
            // Of the columns that are being aggregated away, are all of
            // them wildcarded? If so, this segment is a match. If not, we
            // will need to combine with other segments later.
            findRollupCandidatesAmong(coordinates, list, headers);
        }
        if (BitKeyExplain.enabled()) {
            BitKeyExplain.EXPLAIN.debug(
                "rollup search for {} over {} ancestor dimensionalities: {} candidate set(s)",
                BitKeyExplain.explain(coordinates), ancestors.size(), list.size());
            for (List<SegmentHeader> candidates : list) {
                for (SegmentHeader header : candidates) {
                    BitKeyExplain.EXPLAIN.debug("  candidate {}",
                        BitKeyExplain.explain(header));
                }
            }
        }
        return list;
    }

    /**
     * Whether one of the header's excluded regions, widened to the columns
     * the rollup keeps, contains the requested coordinates. Region columns
     * missing from the coordinates are aggregated away and no longer
     * constrain the box.
     */
    private static boolean excludedRegionHitsRequest(
        SegmentHeader header,
        Map<String, Comparable> coordinates)
    {
        regionLoop:
        for (SegmentRegion region : header.getExcludedRegions()) {
            for (SegmentColumn column : region.columns()) {
                if (!coordinates.containsKey(column.columnExpression)) {
                    continue;
                }
                Comparable value = coordinates.get(column.columnExpression);
                if (value == null) {
                    value = Util.sqlNullValue;
                }
                if (column.values != null && !column.values.contains(value)) {
                    continue regionLoop;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Finds rollup candidates among a list of headers with the same
     * dimensionality.
     *
     * For each column that is being aggregated away, we need to ensure that
     * we have all values of that column. If the column is wildcarded, it's
     * easy. For example, if we wish to roll up to create Segment1:
     *
     * Segment1(Year=1997, MaritalStatus=*)
     *
     * then clearly Segment2:
     *
     * Segment2(Year=1997, MaritalStatus=*, Gender=*, Nation=*)
     *
     * has all gender and Nation values. If the values are specified as a
     * list:
     *
     * Segment3(Year=1997, MaritalStatus=*, Gender={M, F}, Nation=*)
     *
     * then we need to check the metadata. We see that Gender has two
     * distinct values in the database, and we have two values, therefore we
     * have all of them.
     *
     * What if we have multiple non-wildcard columns? Consider:
     *
     *
     *     Segment4(Year=1997, MaritalStatus=*, Gender={M},
                    Nation={Mexico, USA})
     *     Segment5(Year=1997, MaritalStatus=*, Gender={F},
                    Nation={USA})
     *     Segment6(Year=1997, MaritalStatus=*, Gender={F, M},
                    Nation={Canada, Mexico, Honduras, Belize})
     *
     *
     * The problem is similar to finding whether a collection of rectangular
     * regions covers a rectangle (or, generalizing to n dimensions, an
     * n-cube). Or better, find a minimal collection of regions.
     *
     * Our algorithm solves it by iterating over all combinations of values.
     * Those combinations are exponential in theory, but tractible in practice,
     * using the following trick. The algorithm reduces the number of
     * combinations by looking for values that are always treated the same. In
     * the above, Canada, Honduras and Belize are always treated the same, so to
     * prove covering, it is sufficient to prove that all combinations involving
     * Canada are covered.
     *
     * @param coordinates Coordinates
     * @param list List to write candidates to
     * @param headers Headers of candidate segments
     */
    private void findRollupCandidatesAmong(
        Map<String, Comparable> coordinates,
        List<List<SegmentHeader>> list,
        Collection<SegmentHeader> headers)
    {
        final List<Pair<SegmentHeader, List<SegmentColumn>>> matchingHeaders =
            new ArrayList<>();
        headerLoop:
        for (SegmentHeader header : headers) {
            // mirror locate/intersectRegion: a header flagged for removal
            // after its load (flushed while loading) must not seed a
            // rollup - its store body still exists in the flush window,
            // and the rollup would publish the flushed cells permanently.
            // A header whose load is still OPEN is no candidate either:
            // its body exists nowhere yet, and the reader's store miss
            // would remove the loading segment (store-without-index ghost
            // plus the foreign load's work lost).
            final HeaderInfo stale = headerMap.get(header);
            if (stale != null
                && (stale.removeAfterLoad
                    || (stale.slot != null && !stale.slot.isDone()))) {
                continue;
            }
            // A header with excluded regions can still roll up: the
            // regions ride into the target header widened to the kept
            // columns (SegmentBuilder.rollup sums the physically present
            // flushed cells into exactly those boxes and keeps refusing
            // them). Only a region whose kept columns all hit the requested
            // coordinates makes the rollup useless: the requested cell
            // itself would be excluded from the result.
            if (excludedRegionHitsRequest(header, coordinates)) {
                continue;
            }

            List<SegmentColumn> nonWildcards =
                new ArrayList<>();
            for (SegmentColumn column : header.getConstrainedColumns()) {
                final SegmentColumn constrainedColumn = column;

                if (coordinates.containsKey(column.columnExpression)) {
                    // Matching column. Will not be aggregated away. Needs
                    // to be in range.
                    Comparable value =
                        coordinates.get(column.columnExpression);
                    if (value == null) {
                        value = Util.sqlNullValue;
                    }
                    if (constrainedColumn.values != null
                        && !constrainedColumn.values.contains(value))
                    {
                        continue headerLoop;
                    }
                } else {
                    // Non-matching column. Will be aggregated away. Needs
                    // to be wildcarded (or some more complicated conditions
                    // to be dealt with later).
                    if (constrainedColumn.values != null) {
                        nonWildcards.add(constrainedColumn);
                    }
                }
            }

            if (nonWildcards.isEmpty()) {
                list.add(Collections.singletonList(header));
            } else {
                matchingHeaders.add(Pair.of(header, nonWildcards));
            }
        }

        // Find combinations of segments that can roll up. Need at least two.
        if (matchingHeaders.size() < 2) {
            return;
        }

        // Collect the list of non-wildcarded columns.
        final List<SegmentColumn> columnList = new ArrayList<>();
        final List<String> columnNameList = new ArrayList<>();
        final Set<String> columnNames = new HashSet<>();
        for (Pair<SegmentHeader, List<SegmentColumn>> pair : matchingHeaders) {
            for (SegmentColumn column : pair.right) {
                if (columnNames.add(column.columnExpression)) {
                    final long valueCount = column.getValueCount();
                    if (valueCount <= 0) {
                        // Impossible to safely roll up. If we don't know the
                        // number of values, we don't know that we have all of
                        // them.
                        return;
                    }
                    columnList.add(column);
                    columnNameList.add(column.columnExpression);
                }
            }
        }

        // Gather known values of each column. For each value, remember which
        // segments refer to it.
        final List<List<Comparable>> valueLists =
            new ArrayList<>();
        for (SegmentColumn column : columnList) {
            // For each value, which equivalence class it belongs to.
            final SortedMap<Comparable, BitSet> valueMap =
                new TreeMap<>(org.eclipse.daanse.olap.common.Util.OLAP_COMPARATOR);

            // Two passes: listed values first (they create the entries),
            // then wildcard headers mark THEIR bit on every value — a
            // wildcard covers each value, not values 0..n, and must also
            // cover entries created after its iteration position.
            int h = -1;
            final BitSet wildcardHeaders = new BitSet();
            for (SegmentHeader header : Pair.leftIter(matchingHeaders)) {
                ++h;
                final SegmentColumn column1 =
                    header.getConstrainedColumn(
                        column.columnExpression);
                if (column1.getValues() == null) {
                    wildcardHeaders.set(h);
                } else {
                    for (Comparable value : column1.getValues()) {
                        BitSet bitSet = valueMap.computeIfAbsent(value, k -> new BitSet());
                        bitSet.set(h);
                    }
                }
            }
            if (!wildcardHeaders.isEmpty()) {
                for (Entry<Comparable, BitSet> entry : valueMap.entrySet()) {
                    entry.getValue().or(wildcardHeaders);
                }
            }

            // Is the number of values discovered equal to the known cardinality
            // of the column? If not, we can't cover the space.
            if (valueMap.size() < column.valueCount) {
                return;
            }

            // Build equivalence sets of values. These group together values
            // that are used identically in segments.
            //
            // For instance, given segments Sx over column c,
            //
            // S1: c = {1, 2, 3, 4}
            // S2: c = {3, 4, 5}
            // S3: c = {3, 6, 7, 8}
            //
            // the equivalence classes are:
            //
            // E1 = {1, 2} used in {S1}
            // E2 = {3} used in {S1, S2, S3}
            // E3 = {4} used in {S1, S2}
            // E4 = {6, 7, 8} used in {S3}
            //
            // The equivalence classes reduce the size of the search space. (In
            // this case, from 8 values to 4 classes.) We can use any value in a
            // class to stand for all values.
            final Map<BitSet, Comparable> eqclassPrimaryValues =
                new LinkedHashMap<>();
            for (Map.Entry<Comparable, BitSet> entry : valueMap.entrySet()) {
                final BitSet bitSet = entry.getValue();
                eqclassPrimaryValues.computeIfAbsent(bitSet, k -> entry.getKey());
            }
            valueLists.add(
                new ArrayList<>(
                    eqclassPrimaryValues.values()));
        }

        // Iterate over every combination of values, and make sure that some
        // segment can satisfy each.
        //
        // TODO: A greedy algorithm would probably be better. Rather than adding
        // the first segment that contains a particular value combination, add
        // the segment that contains the most value combinations that we are are
        // not currently covering.
        final CartesianProductList<Comparable> tuples =
            new CartesianProductList<>(valueLists);
        final List<SegmentHeader> usedSegments = new ArrayList<>();
        final List<SegmentHeader> unusedSegments =
            new ArrayList<>(Pair.left(matchingHeaders));
        tupleLoop:
        for (List<Comparable> tuple : tuples) {
            // If the value combination is handled by one of the used segments,
            // great!
            for (SegmentHeader segment : usedSegments) {
                if (contains(segment, tuple, columnNameList)) {
                    continue tupleLoop;
                }
            }
            // Does one of the unused segments contain it? Use the first one we
            // find.
            for (int i = 0; i < unusedSegments.size(); i++) {
                final SegmentHeader segment = unusedSegments.get(i);
                if (contains(segment, tuple, columnNameList)) {
                    unusedSegments.remove(i);
                    usedSegments.add(segment);
                    continue tupleLoop;
                }
            }
            // There was a value combination not contained in any of the
            // segments. Fail.
            return;
        }
        list.add(usedSegments);
    }

    private boolean contains(
        SegmentHeader segment,
        List<Comparable> values,
        List<String> columns)
    {
        for (int i = 0; i < columns.size(); i++) {
            String columnName = columns.get(i);
            final SegmentColumn column =
                segment.getConstrainedColumn(columnName);
            final SortedSet<Comparable> valueSet = column.getValues();
            if (valueSet != null && !valueSet.contains(values.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static class FactInfo {
        private final Set<SegmentHeader> headers =
            new LinkedHashSet<>();

        /** Headers per dimensionality; a bit key drops out at zero. */
        private final Map<BitKey, Integer> bitkeyCounts = new HashMap<>();

        /**
         * Ancestor lists per requested dimensionality, cleared whenever a
         * dimensionality appears or disappears. Keys are frozen request bit
         * keys; cached lists are never mutated. Bounded by the distinct
         * dimensionalities requested between changes.
         */
        private final Map<BitKey, List<BitKey>> ancestorMemo = new HashMap<>();

        /**
         * The dimensionalities a request can roll up FROM: every known bit
         * key that is a strict superset, fewest bits first — cheaper
         * rollups (fewer columns to aggregate away) are tried first. A
         * linear filter over the known dimensionalities; the counts are
         * few and the result is memoized per request key.
         */
        private List<BitKey> ancestorsOf(BitKey request) {
            final List<BitKey> result = new ArrayList<>();
            for (BitKey candidate : bitkeyCounts.keySet()) {
                if (!candidate.equals(request)
                        && candidate.isSuperSetOf(request)) {
                    result.add(candidate);
                }
            }
            result.sort(Comparator.comparingInt(BitKey::cardinality));
            return result;
        }

        FactInfo() {
        }
    }

    private static class RegionFactInfo {
        private final Set<SegmentHeader> headers =
            new LinkedHashSet<>();

        RegionFactInfo() {
        }
    }

    /**
     * A private class that we use in the index to track who was interested in
     * which headers, the SQL statement that is populating it and a future
     * object which we pass to clients.
     */
    private static class HeaderInfo {
        /**
         * In-flight guarded SQL statement of a loading segment; null until
         * the SQL thread calls us back to register it. Actor-confined
         * (written and read on the cache manager thread), handed to the
         * cancel executor only as a captured reference - the guard's
         * handshake makes a late cancel a safe no-op.
         */
        private GuardedStatement stmt;
        /**
         * The future object to pass on to clients.
         */
        private SlotFuture<SegmentBody> slot;
        /**
         * The clients interested in this segment.
         */
        private final Set<Execution> clients =
            new LinkedHashSet<>();
        /**
         * Whether this segment is already considered stale and must
         * be deleted after it is done loading. This can happen
         * when flushing.
         */
        private boolean removeAfterLoad;
    }
}
