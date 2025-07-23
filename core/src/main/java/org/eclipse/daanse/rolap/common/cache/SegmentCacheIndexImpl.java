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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import org.eclipse.daanse.olap.api.Execution;
import org.eclipse.daanse.olap.common.QueryCanceledException;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentColumn;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.util.ByteString;
import org.eclipse.daanse.olap.util.CartesianProductList;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.agg.SegmentBuilder;
import org.eclipse.daanse.rolap.util.PartiallyOrderedSet;
import org.eclipse.daanse.rolap.util.SlotFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Data structure that identifies which segments contain cells.
 *
 * Not thread safe.
 *
 * @author Julian Hyde
 */
public class SegmentCacheIndexImpl implements SegmentCacheIndex {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SegmentCacheIndexImpl.class);
    public static final String SEGMENT_CACHE_INDEX_IMPL = "SegmentCacheIndexImpl(";

    private final Map<List, List<SegmentHeader>> bitkeyMap =
        new HashMap<>();

    /**
     * The fact map allows us to spot quickly which
     * segments have facts relating to a given header.
     */
    private final Map<List, FactInfo> factMap =
        new HashMap<>();

    /**
     * The fuzzy fact map allows us to spot quickly which
     * segments have facts relating to a given header, but doesn't
     * consider the compound predicates in the key. This allows
     * flush operations to be consistent.
     */
    // TODO Get rid of the fuzzy map once we have a way to parse
    // compound predicates into rich objects that can be serialized
    // as part of the SegmentHeader.
    private final Map<List, FuzzyFactInfo> fuzzyFactMap =
        new HashMap<>();

    private final Map<SegmentHeader, HeaderInfo> headerMap =
        new HashMap<>();

    private final Thread thread;

    /**
     * Creates a SegmentCacheIndexImpl.
     *
     * @param thread Thread that must be used to execute commands.
     */
    public SegmentCacheIndexImpl(Thread thread) {
        this.thread = thread;
        if (thread == null) {
            throw new IllegalArgumentException("SegmentCacheIndexImpl: thread should be not null");
        }
    }

    public static List makeConverterKey(SegmentHeader header) {
        return Arrays.asList(
            header.schemaName,
            header.schemaChecksum,
            header.cubeName,
            header.rolapStarFactTableName,
            header.measureName,
            header.compoundPredicates);
    }

    public static List makeConverterKey(CellRequest request)
    {
        return Arrays.asList(
            request.getMeasure().getStar().getCatalog().getName(),
            request.getMeasure().getStar().getCatalog().getChecksum(),
            request.getMeasure().getCubeName(),
            request.getMeasure().getStar().getFactTable().getAlias(),
            request.getMeasure().getName(),
            request.getCompoundPredicateStrings());
    }

    @Override
	public List<SegmentHeader> locate(
        String schemaName,
        ByteString schemaChecksum,
        String cubeName,
        String measureName,
        String rolapStarFactTableName,
        BitKey constrainedColsBitKey,
        Map<String, Comparable> coordinates,
        List<String> compoundPredicates)
    {
        checkThread();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                new StringBuilder(SEGMENT_CACHE_INDEX_IMPL)
                    .append(System.identityHashCode(this))
                    .append(")locate:")
                    .append("\nschemaName:").append(schemaName)
                    .append("\nschemaChecksum:").append(schemaChecksum)
                    .append("\ncubeName:").append(cubeName)
                    .append("\nmeasureName:").append(measureName)
                    .append("\nrolapStarFactTableName:").append(rolapStarFactTableName)
                    .append("\nconstrainedColsBitKey:").append(constrainedColsBitKey)
                    .append("\ncoordinates:").append(coordinates)
                    .append("\ncompoundPredicates:").append(compoundPredicates).toString());
        }

        List<SegmentHeader> list = Collections.emptyList();
        final List starKey =
            makeBitkeyKey(
                schemaName,
                schemaChecksum,
                cubeName,
                rolapStarFactTableName,
                constrainedColsBitKey,
                measureName,
                compoundPredicates);
        final List<SegmentHeader> headerList = bitkeyMap.get(starKey);
        if (headerList == null) {
            String msg = new StringBuilder(SEGMENT_CACHE_INDEX_IMPL)
                .append(System.identityHashCode(this))
                .append(").locate:NOMATCH").toString();
            LOGGER.trace(msg);
            return Collections.emptyList();
        }
        for (SegmentHeader header : headerList) {
            if (matches(header, coordinates, compoundPredicates)) {
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
        SegmentBuilder.SegmentConverter converter,
        boolean loading)
    {
        checkThread();
        String msg = new StringBuilder(SEGMENT_CACHE_INDEX_IMPL)
            .append(System.identityHashCode(this))
            .append(").add:\n")
            .append(header.toString()).toString();
        LOGGER.debug(msg);

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

        final List bitkeyKey = makeBitkeyKey(header);
        List<SegmentHeader> headerList = bitkeyMap.computeIfAbsent(bitkeyKey, k -> new ArrayList<>());
        if (!headerList.contains(header)) {
            headerList.add(header);
        }

        final List factKey = makeFactKey(header);
        FactInfo factInfo = factMap.computeIfAbsent(factKey, k -> new FactInfo());

        if (!factInfo.headerList.contains(header)) {
            factInfo.headerList.add(header);
        }
        if (!factInfo.bitkeyPoset
            .contains(header.getConstrainedColumnsBitKey()))
        {
            factInfo.bitkeyPoset.add(header.getConstrainedColumnsBitKey());
        }
        if (converter != null) {
            factInfo.converter = converter;
        }

        final List fuzzyFactKey = makeFuzzyFactKey(header);
        FuzzyFactInfo fuzzyFactInfo = fuzzyFactMap.computeIfAbsent(fuzzyFactKey, k -> new FuzzyFactInfo());
        if (!fuzzyFactInfo.headerList.contains(header)) {
            fuzzyFactInfo.headerList.add(header);
        }
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
        headerMap.remove(oldHeader);
        if(headerInfo != null) {
            headerMap.put(newHeader, headerInfo);
        }

        final List oldBitkeyKey = makeBitkeyKey(oldHeader);
        List<SegmentHeader> headerList = bitkeyMap.get(oldBitkeyKey);
        headerList.remove(oldHeader);
        headerList.add(newHeader);

        final List oldFactKey = makeFactKey(oldHeader);
        final FactInfo factInfo = factMap.get(oldFactKey);
        factInfo.headerList.remove(oldHeader);
        factInfo.headerList.add(newHeader);

        final List oldFuzzyFactKey = makeFuzzyFactKey(oldHeader);
        final FuzzyFactInfo fuzzyFactInfo = fuzzyFactMap.get(oldFuzzyFactKey);
        fuzzyFactInfo.headerList.remove(oldHeader);
        fuzzyFactInfo.headerList.add(newHeader);
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

        if (!headerInfo.slot.isDone()) {
            headerInfo.slot.put(body);
        }
        if (headerInfo.removeAfterLoad) {
            remove(header);
        }
        // Cleanup the HeaderInfo
        headerInfo.stmt = null;
        headerInfo.clients.clear();
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
            throw new IllegalArgumentException(
                new StringBuilder("segment header ").append(header.getUniqueID()).append(" is not loading").toString()
            );
        }
        headerInfo.slot.fail(throwable);
        remove(header);
        // Cleanup the HeaderInfo
        headerInfo.stmt = null;
        headerInfo.clients.clear();
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
            String msg = new StringBuilder(SEGMENT_CACHE_INDEX_IMPL)
                .append(System.identityHashCode(this))
                .append(").remove:UNKNOWN HEADER").toString();
            LOGGER.debug(msg);
            return;
        }
        if (headerInfo.slot != null && !headerInfo.slot.isDone()) {
            // Cannot remove while load is pending; flag for removal after load
            headerInfo.removeAfterLoad = true;
            String msg = new StringBuilder(SEGMENT_CACHE_INDEX_IMPL)
                .append(System.identityHashCode(this))
                .append(").remove:DEFFERED").toString();
            LOGGER.debug(msg);
            return;
        }

        headerMap.remove(header);

        final List factKey = makeFactKey(header);
        final FactInfo factInfo = factMap.get(factKey);
        if (factInfo != null) {
            factInfo.headerList.remove(header);
            factInfo.bitkeyPoset.remove(header.getConstrainedColumnsBitKey());
            if (factInfo.headerList.isEmpty()) {
                factMap.remove(factKey);
            }
        }

        final List fuzzyFactKey = makeFuzzyFactKey(header);
        final FuzzyFactInfo fuzzyFactInfo = fuzzyFactMap.get(fuzzyFactKey);
        if (fuzzyFactInfo != null) {
            fuzzyFactInfo.headerList.remove(header);
            if (fuzzyFactInfo.headerList.isEmpty()) {
                fuzzyFactMap.remove(fuzzyFactKey);
            }
        }

        final List bitkeyKey = makeBitkeyKey(header);
        final List<SegmentHeader> headerList = bitkeyMap.get(bitkeyKey);
        headerList.remove(header);
        if (headerList.isEmpty()) {
            bitkeyMap.remove(bitkeyKey);
        }
    }

    private void checkThread() {
        assert thread == Thread.currentThread()
            : new StringBuilder("expected ").append(thread).append(", but was ")
            .append(Thread.currentThread())
            .toString();
    }

    public static boolean matches(
        SegmentHeader header,
        Map<String, Comparable> coords,
        List<String> compoundPredicates)
    {
        if (!header.compoundPredicates.equals(compoundPredicates)) {
            return false;
        }
        for (Map.Entry<String, Comparable> entry : coords.entrySet()) {
            // Check if the segment explicitly excludes this coordinate.
            final SegmentColumn excludedColumn =
                header.getExcludedRegion(entry.getKey());
            if (excludedColumn != null) {
                final SortedSet<Comparable> values =
                    excludedColumn.getValues();
                if (values == null || values.contains(entry.getValue())) {
                    return false;
                }
            }
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
        String schemaName,
        ByteString schemaChecksum,
        String cubeName,
        String measureName,
        String rolapStarFactTableName,
        SegmentColumn[] region)
    {
        checkThread();

        final List factKey = makeFuzzyFactKey(
            schemaName,
            schemaChecksum,
            cubeName,
            rolapStarFactTableName,
            measureName);
        final FuzzyFactInfo factInfo = fuzzyFactMap.get(factKey);
        List<SegmentHeader> list = Collections.emptyList();
        if (factInfo == null) {
            return list;
        }
        for (SegmentHeader header : factInfo.headerList) {
            // Don't return stale segments.
            if (headerMap.get(header) != null && headerMap.get(header).removeAfterLoad) {
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
        for (SegmentColumn regionColumn : region) {
            final SegmentColumn headerColumn =
                header.getConstrainedColumn(regionColumn.getColumnExpression());
            if (headerColumn == null) {
                // If the segment header doesn't contain a column specified
                // by the region, then it always implicitly intersects.
                // This allows flush operations to be valid.
                return true;
            }
            final SortedSet<Comparable> regionValues =
                regionColumn.getValues();
            final SortedSet<Comparable> headerValues =
                headerColumn.getValues();
            if (headerValues == null || regionValues == null) {
                // This is a wildcard, so it always intersects.
                return true;
            }
            for (Comparable myValue : regionValues) {
                if (headerValues.contains(myValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
	public void printCacheState(PrintWriter pw) {
        checkThread();
        final List<List<SegmentHeader>> values =
            new ArrayList<>(
                bitkeyMap.values());
        Collections.sort(
            values,
            new Comparator<List<SegmentHeader>>() {
                @Override
				public int compare(
                    List<SegmentHeader> o1,
                    List<SegmentHeader> o2)
                {
                    if (o1.isEmpty()) {
                        return -1;
                    }
                    if (o2.isEmpty()) {
                        return 1;
                    }
                    return o1.get(0).getUniqueID()
                        .compareTo(o2.get(0).getUniqueID());
                }
            });
        for (List<SegmentHeader> key : values) {
            final List<SegmentHeader> headerList =
                new ArrayList<>(key);
            Collections.sort(
                headerList,
                new Comparator<SegmentHeader>() {
                    @Override
					public int compare(SegmentHeader o1, SegmentHeader o2) {
                        return o1.getUniqueID().compareTo(o2.getUniqueID());
                    }
                });
            for (SegmentHeader header : headerList) {
                pw.println(header.getDescription());
            }
        }
    }

    @Override
	public Future<SegmentBody> getFuture(Execution exec, SegmentHeader header) {
        checkThread();
        HeaderInfo hi = headerMap.get(header);
        if (!hi.clients.contains(exec)) {
            hi.clients.add(exec);
        }
        return hi.slot;
    }

    @Override
	public void linkSqlStatement(SegmentHeader header, Statement stmt) {
        checkThread();
        headerMap.get(header).stmt = stmt;
    }

    @Override
	public boolean contains(SegmentHeader header) {
        return headerMap.containsKey(header);
    }

    @Override
	public void cancel(Execution exec) {
        checkThread();
        List<SegmentHeader> toRemove = new ArrayList<>();
        for (Entry<SegmentHeader, HeaderInfo> entry : headerMap.entrySet()) {
            if (entry.getValue().clients.remove(exec)
                && entry.getValue().slot != null
                && !entry.getValue().slot.isDone()
                && entry.getValue().clients.isEmpty())
            {
                toRemove.add(entry.getKey());
            }
        }
        // Make sure to cleanup the orphaned segments.
        for (SegmentHeader header : toRemove) {
            final Statement stmt = headerMap.get(header).stmt;
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
            Util.cancelStatement(stmt);
        }
    }

    @Override
	public SegmentBuilder.SegmentConverter getConverter(
        String schemaName,
        ByteString schemaChecksum,
        String cubeName,
        String rolapStarFactTableName,
        String measureName,
        List<String> compoundPredicates)
    {
        checkThread();

        final List factKey = makeFactKey(
            schemaName,
            schemaChecksum,
            cubeName,
            rolapStarFactTableName,
            measureName,
            compoundPredicates);
        final FactInfo factInfo = factMap.get(factKey);
        if (factInfo == null) {
            return null;
        }
        return factInfo.converter;
    }

    @Override
	public void setConverter(
        String schemaName,
        ByteString schemaChecksum,
        String cubeName,
        String rolapStarFactTableName,
        String measureName,
        List<String> compoundPredicates,
        SegmentBuilder.SegmentConverter converter)
    {
        checkThread();

        final List factKey = makeFactKey(
            schemaName,
            schemaChecksum,
            cubeName,
            rolapStarFactTableName,
            measureName,
            compoundPredicates);
        final FactInfo factInfo = factMap.get(factKey);
        if (factInfo == null) {
            throw new IllegalArgumentException("setConverter: should have called 'add' first");
        }
        factInfo.converter = converter;
    }

    private List makeBitkeyKey(SegmentHeader header) {
        return makeBitkeyKey(
            header.schemaName,
            header.schemaChecksum,
            header.cubeName,
            header.rolapStarFactTableName,
            header.constrainedColsBitKey,
            header.measureName,
            header.compoundPredicates);
    }

    private List makeBitkeyKey(
        String schemaName,
        ByteString schemaChecksum,
        String cubeName,
        String rolapStarFactTableName,
        BitKey constrainedColsBitKey,
        String measureName,
        List<String> compoundPredicates)
    {
        return Arrays.asList(
            schemaName,
            schemaChecksum,
            cubeName,
            rolapStarFactTableName,
            constrainedColsBitKey,
            measureName,
            compoundPredicates);
    }

    private List makeFactKey(SegmentHeader header) {
        return makeFactKey(
            header.schemaName,
            header.schemaChecksum,
            header.cubeName,
            header.rolapStarFactTableName,
            header.measureName,
            header.compoundPredicates);
    }

    private List makeFactKey(
        String schemaName,
        ByteString schemaChecksum,
        String cubeName,
        String rolapStarFactTableName,
        String measureName,
        List<String> compoundPredicates)
    {
        return Arrays.asList(
            schemaName,
            schemaChecksum,
            cubeName,
            rolapStarFactTableName,
            measureName,
            compoundPredicates);
    }

    private List makeFuzzyFactKey(SegmentHeader header) {
        return makeFuzzyFactKey(
            header.schemaName,
            header.schemaChecksum,
            header.cubeName,
            header.rolapStarFactTableName,
            header.measureName);
    }

    private List makeFuzzyFactKey(
        String schemaName,
        ByteString schemaChecksum,
        String cubeName,
        String rolapStarFactTableName,
        String measureName)
    {
        return Arrays.asList(
            schemaName,
            schemaChecksum,
            cubeName,
            rolapStarFactTableName,
            measureName);
    }

    @Override
	public List<List<SegmentHeader>> findRollupCandidates(
        String schemaName,
        ByteString schemaChecksum,
        String cubeName,
        String measureName,
        String rolapStarFactTableName,
        BitKey constrainedColsBitKey,
        Map<String, Comparable> coordinates,
        List<String> compoundPredicates)
    {
        final List factKey = makeFactKey(
            schemaName,
            schemaChecksum,
            cubeName,
            rolapStarFactTableName,
            measureName,
            compoundPredicates);
        final FactInfo factInfo = factMap.get(factKey);
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
        final List<BitKey> ancestors =
            factInfo.bitkeyPoset.getAncestors(constrainedColsBitKey);
        for (BitKey bitKey : ancestors) {
            final List bitkeyKey = makeBitkeyKey(
                schemaName,
                schemaChecksum,
                cubeName,
                rolapStarFactTableName,
                bitKey,
                measureName,
                compoundPredicates);
            final List<SegmentHeader> headers = bitkeyMap.get(bitkeyKey);
            assert headers != null : "bitkeyPoset / bitkeyMap inconsistency";

            // For columns that are still present after roll up, make sure that
            // the required value is in the range covered by the segment.
            // Of the columns that are being aggregated away, are all of
            // them wildcarded? If so, this segment is a match. If not, we
            // will need to combine with other segments later.
            findRollupCandidatesAmong(coordinates, list, headers);
        }
        return list;
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
        List<SegmentHeader> headers)
    {
        final List<Pair<SegmentHeader, List<SegmentColumn>>> matchingHeaders =
            new ArrayList<>();
        headerLoop:
        for (SegmentHeader header : headers) {
            // Skip headers that have exclusions.
            //
            // TODO: This is a bit harsh.
            if (!header.getExcludedRegions().isEmpty()) {
                continue;
            }

            List<SegmentColumn> nonWildcards =
                new ArrayList<>();
            for (SegmentColumn column : header.getConstrainedColumns()) {
                final SegmentColumn constrainedColumn =
                    header.getConstrainedColumn(column.columnExpression);

                // REVIEW: How are null key values represented in coordinates?
                // Assuming that they are represented by null ref.
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
        for (Pair<SegmentHeader, List<SegmentColumn>> pair : matchingHeaders) {
            for (SegmentColumn column : pair.right) {
                if (!columnNameList.contains(column.columnExpression)) {
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
                new TreeMap<>(RolapUtil.ROLAP_COMPARATOR);

            int h = -1;
            for (SegmentHeader header : Pair.leftIter(matchingHeaders)) {
                ++h;
                final SegmentColumn column1 =
                    header.getConstrainedColumn(
                        column.columnExpression);
                if (column1.getValues() == null) {
                    // Wildcard. Mark all values as present.
                    for (Entry<Comparable, BitSet> entry : valueMap.entrySet())
                    {
                        for (int pos = 0;
                            pos < entry.getValue().cardinality();
                            pos++)
                        {
                            entry.getValue().set(pos);
                        }
                    }
                } else {
                    for (Comparable value : column1.getValues()) {
                        BitSet bitSet = valueMap.computeIfAbsent(value, k -> new BitSet());
                        bitSet.set(h);
                    }
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
                new HashMap<>();
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
            for (SegmentHeader segment : unusedSegments) {
                if (contains(segment, tuple, columnNameList)) {
                    unusedSegments.remove(segment);
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
        private static final PartiallyOrderedSet.Ordering<BitKey> ORDERING =
            new PartiallyOrderedSet.Ordering<>() {
                @Override
				public boolean lessThan(BitKey e1, BitKey e2) {
                    return e2.isSuperSetOf(e1);
                }
            };

        private final List<SegmentHeader> headerList =
            new ArrayList<>();

        private final PartiallyOrderedSet<BitKey> bitkeyPoset =
            new PartiallyOrderedSet<>(ORDERING);

        private SegmentBuilder.SegmentConverter converter;

        FactInfo() {
        }
    }

    private static class FuzzyFactInfo {
        private final List<SegmentHeader> headerList =
            new ArrayList<>();

        FuzzyFactInfo() {
        }
    }

    /**
     * A private class that we use in the index to track who was interested in
     * which headers, the SQL statement that is populating it and a future
     * object which we pass to clients.
     */
    private static class HeaderInfo {
        /**
         * The SQL statement populating this header.
         * Will be null until the SQL thread calls us back to register it.
         */
        private Statement stmt;
        /**
         * The future object to pass on to clients.
         */
        private SlotFuture<SegmentBody> slot;
        /**
         * A list of clients interested in this segment.
         */
        private final List<Execution> clients =
            new CopyOnWriteArrayList<>();
        /**
         * Whether this segment is already considered stale and must
         * be deleted after it is done loading. This can happen
         * when flushing.
         */
        private boolean removeAfterLoad;
    }
}
