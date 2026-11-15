/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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
 *
 */


package org.eclipse.daanse.rolap.common;

import org.eclipse.daanse.olap.cache.CachePolicy;
import org.eclipse.daanse.olap.api.execution.Statement;
import static org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.genericSql;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.eclipse.daanse.olap.api.cache.CacheControl;
import org.eclipse.daanse.olap.cache.region.CellRegionShape;
import org.eclipse.daanse.olap.cache.region.CellRegionVisitor;
import org.eclipse.daanse.olap.cache.region.CrossjoinRegion;
import org.eclipse.daanse.olap.cache.region.EmptyRegion;
import org.eclipse.daanse.olap.cache.region.MemberRegion;
import org.eclipse.daanse.olap.cache.region.UnionRegion;
import org.eclipse.daanse.olap.api.cache.OlapSegmentCacheManager;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.execution.Execution;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.NoExecutionContextException;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.olap.spi.SegmentColumn;
import org.eclipse.daanse.olap.util.ArraySortedSet;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.connection.AbstractRolapConnection;
import org.eclipse.daanse.rolap.common.constraint.ChildByNameConstraint;
import org.eclipse.daanse.rolap.common.constraint.DefaultMemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.constraint.DefaultTupleConstraint;
import org.eclipse.daanse.rolap.common.member.MemberCache;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.rolap.common.member.MemberReader;
import org.eclipse.daanse.rolap.common.member.CachingMemberReader;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapCubeMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMemberBase;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;

/**
 * Implementation of the {@link CacheControl} API.
 *
 * "Flush" means three different things here, by target:
 * cell regions ({@code flush(CellRegion)}) invalidate the segment
 * indexes AND wait (bounded by one {@link FlushDeadline} per operation)
 * on the external stores' removals; member sets
 * ({@code flush(MemberSet)}) only invalidate in-JVM member and native
 * tuple caches - no store I/O; {@code flushSchemaCache()} discards the
 * catalog pool wholesale.
 *
 * Lock order around MEMBER_CACHE_LOCK: {@code flush(MemberSet)} drops
 * the lock BEFORE its cell flush (store waits must not run under it);
 * {@code execute(MemberEditCommand)} keeps its cell flush INSIDE the
 * lock deliberately - a flush failure must fail the edit before commit.
 * Both are bounded by the single per-operation FlushDeadline, created
 * once the lock is held.
 */
public class CacheControlImpl implements CacheControl {
    private final Connection connection;

    /**
     * Object to lock before making changes to the member cache.
     *
     * The "member cache" is a figure of speech: each RolapHierarchy has its
     * own MemberCache object. But to provide transparently serialized access
     * to the "member cache" via the interface CacheControl, provide a common
     * lock here.
     *
     * NOTE: static member is a little too wide a scope for this lock,
     * because in theory a JVM can contain multiple independent engine
     * instances.
     */
    private static final Object MEMBER_CACHE_LOCK = new Object();
    private final static String cacheFlushRegionMustContainMembers =
        "Region of cells to be flushed must contain measures.";
    private final static String cacheFlushCrossjoinDimensionsInCommon =
        "Cannot crossjoin cell regions which have dimensions in common. (Dimensionalities are {0}.)";
    private final static String cacheFlushUnionDimensionalityMismatch =
        "Cannot union cell regions of different dimensionalities. (Dimensionalities are ''{0}'', ''{1}''.)";

    /**
     * Creates a CacheControlImpl.
     *
     * @param connection Connection
     */
    public CacheControlImpl(Connection connection) {
        super();
        this.connection = connection;
    }

    // cell cache control
    @Override
	public CellRegion createMemberRegion(Member member, boolean descendants) {
        if (member == null) {
            throw new NullPointerException();
        }
        final ArrayList<Member> list = new ArrayList<>();
        list.add(member);
        return new MemberRegion(list, descendants);
    }

    @Override
	public CellRegion createMemberRegion(
        boolean lowerInclusive,
        Member lowerMember,
        boolean upperInclusive,
        Member upperMember,
        boolean descendants)
    {
        if (lowerMember == null) {
            lowerInclusive = false;
        }
        if (upperMember == null) {
            upperInclusive = false;
        }
        return new MemberRangeCellRegion(
            (RolapMember) lowerMember, lowerInclusive,
            (RolapMember) upperMember, upperInclusive,
            descendants);
    }

    @Override
	public CellRegion createCrossjoinRegion(CellRegion... regions) {
        if (regions == null || regions.length < 2) {
            throw new IllegalArgumentException("regions should be not null and regions.length should be >=2");
        }
        final HashSet<Dimension> set = new HashSet<>();
        final List<CellRegionShape> list = new ArrayList<>();
        for (CellRegion region : regions) {
            int prevSize = set.size();
            List<Dimension> dimensionality = region.getDimensionality();
            set.addAll(dimensionality);
            if (set.size() < prevSize + dimensionality.size()) {
                throw new OlapRuntimeException(
                    MessageFormat.format(cacheFlushCrossjoinDimensionsInCommon, getDimensionalityList(regions)));
            }

            flattenCrossjoin((CellRegionShape) region, list);
        }
        return new CrossjoinRegion(list);
    }

    // Returns e.g. "'[[Product]]', '[[Time], [Product]]'"
    private String getDimensionalityList(CellRegion[] regions) {
        StringBuilder buf = new StringBuilder();
        int k = 0;
        for (CellRegion region : regions) {
            if (k++ > 0) {
                buf.append(", ");
            }
            buf.append("'");
            buf.append(region.getDimensionality().toString());
            buf.append("'");
        }
        return buf.toString();
    }

    @Override
	public CellRegion createUnionRegion(CellRegion... regions)
    {
        if (regions == null) {
            throw new NullPointerException();
        }
        if (regions.length < 2) {
            throw new IllegalArgumentException();
        }
        final List<CellRegionShape> list = new ArrayList<>();
        for (CellRegion region : regions) {
            if (!region.getDimensionality().equals(
                    regions[0].getDimensionality()))
            {
                throw new OlapRuntimeException(MessageFormat.format(
                    cacheFlushUnionDimensionalityMismatch,
                        regions[0].getDimensionality().toString(),
                        region.getDimensionality().toString()));
            }
            list.add((CellRegionShape) region);
        }
        return new UnionRegion(list);
    }

    @Override
	public CellRegion createMeasuresRegion(Cube cube) {
        Dimension measuresDimension = null;
        for (Dimension dim : cube.getDimensions()) {
            if (dim.isMeasures()) {
                measuresDimension = dim;
                break;
            }
        }
        if (measuresDimension == null) {
            throw new OlapRuntimeException(
                "No measures dimension found for cube "
                + cube.getName());
        }
        final List<Member> measures =
            cube.getCatalogReader(null).withLocus().getLevelMembers(
                measuresDimension.getHierarchy().getLevels().getFirst(),
                false);
        if (measures.isEmpty()) {
            return new EmptyRegion();
        }
        return new MemberRegion(measures, false);
    }

    /**
     * One store-wait budget for a WHOLE flush or member-edit operation.
     * Previously every store wait (the per-manager flush in
     * AggregationManager) started a fresh 30s deadline, so a member edit
     * multiplied the MEMBER_CACHE_LOCK hold time by
     * regions x cubes x union-parts x 2 managers against a hung store.
     */
    public static final class FlushDeadline {
        private final long deadlineNanos;

        private FlushDeadline(Duration budget) {
            this.deadlineNanos = System.nanoTime() + budget.toNanos();
        }

        /** Default budget for one operation. */
        public static FlushDeadline standard() {
            return new FlushDeadline(Duration.ofSeconds(30));
        }

        /** At least one nanosecond, so bounded waits never block forever. */
        public long remainingNanos() {
            return Math.max(1L, deadlineNanos - System.nanoTime());
        }
    }

    @Override
	public void flush(final CellRegion region) {
        // Create ExecutionImpl for flush operation
        final Statement statement = connection.getInternalStatement();
        final ExecutionImpl execution = new ExecutionImpl(statement,
            ExecuteDurationUtil.executeDurationValue(connection.getContext()));

        ExecutionContext.where(execution.asContext(), () -> {
            flushInternal(region, FlushDeadline.standard());
        });
    }

    /** As {@link #flush(CellRegion)}, under a caller-owned budget. */
    private void flushWithDeadline(final CellRegion region, FlushDeadline deadline) {
        final Statement statement = connection.getInternalStatement();
        final ExecutionImpl execution = new ExecutionImpl(statement,
            ExecuteDurationUtil.executeDurationValue(connection.getContext()));
        ExecutionContext.where(execution.asContext(), () -> {
            flushInternal(region, deadline);
        });
    }

    private void flushInternal(CellRegion region, FlushDeadline deadline) {
        if (region instanceof EmptyRegion) {
            return;
        }
        final List<Dimension> dimensionality = region.getDimensionality();
        boolean found = false;
        for (Dimension dimension : dimensionality) {
            if (dimension.isMeasures()) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new OlapRuntimeException(cacheFlushRegionMustContainMembers);
        }
        final UnionRegion union = normalize((CellRegionShape) region);
        for (CellRegionShape cellRegion : union.getRegions()) {
            // Figure out the bits.
            flushNonUnion(cellRegion, deadline);
        }
        // fact-derived tuple lists (NON EMPTY crossjoins, HAVING filters,
        // TopCount orderings) are stale once cells changed: a cell flush
        // reaches the native tuple caches too - AFTER the region flush,
        // and here in flushInternal so every entry point (XMLA Refresh,
        // writeback COMMIT, admin flush) passes it. Conservative full
        // clear; a native read still in flight can republish a pre-flush
        // list into the cleared cache - accepted, same as the loader's
        // rollup-overwrite window.
        if (connection instanceof AbstractRolapConnection rolapConnection) {
            rolapConnection.getCatalog().getNativeRegistry().flushNativeSetCaches();
        }
    }

    /**
     * Flushes a list of cell regions.
     *
     * @param cellRegionList List of cell regions
     */
    protected void flushRegionList(List<CellRegion> cellRegionList) {
        // ONE budget for the whole list: fresh per-call deadlines
        // multiplied the wait by the region and cube count
        flushRegionList(cellRegionList, FlushDeadline.standard());
    }

    private void flushRegionList(List<CellRegion> cellRegionList, FlushDeadline deadline) {
        final CellRegion cellRegion;
        switch (cellRegionList.size()) {
        case 0:
            return;
        case 1:
            cellRegion = cellRegionList.getFirst();
            break;
        default:
            final CellRegion[] cellRegions =
                cellRegionList.toArray(CellRegion[]::new);
            cellRegion = createUnionRegion(cellRegions);
            break;
        }
        if (!containsMeasures(cellRegion)) {
            for (RolapCube cube : ((AbstractRolapConnection)connection).getCatalog().getCubeList()) {
                flushWithDeadline(
                    createCrossjoinRegion(
                        createMeasuresRegion(cube),
                        cellRegion),
                    deadline);
            }
        } else {
            flushWithDeadline(cellRegion, deadline);
        }
    }

    private boolean containsMeasures(CellRegion cellRegion) {
        final List<Dimension> dimensionList = cellRegion.getDimensionality();
        for (Dimension dimension : dimensionList) {
            if (dimension.isMeasures()) {
                return true;
            }
        }
        return false;
    }

    @Override
	public void trace(String message) {
        // ignore message
    }

    @Override
	public boolean isTraceEnabled() {
        return false;
    }

    @Override
	public void flushSchemaCache() {
    	connection.getContext().getCatalogCache().clear();
        // In some cases, the request might originate from a reference
        // to the schema which isn't in the pool anymore. We must also call
        // the cleanup procedure on the current connection.
        if (connection != null
            && connection.getCatalog() != null)
        {
            ((AbstractRolapConnection)connection).getCatalog().finalCleanUp();
        }
    }


    protected void flushNonUnion(CellRegion region, FlushDeadline deadline) {
        throw new UnsupportedOperationException();
    }

    /**
     * Normalizes a CellRegion into a union of crossjoins of member regions.
     *
     * @param region Region
     * @return normalized region
     */
    public UnionRegion normalize(CellRegionShape region) {
        // Search for Union within a Crossjoin.
        //   Crossjoin(a1, a2, Union(r1, r2, r3), a4)
        // becomes
        //   Union(
        //     Crossjoin(a1, a2, r1, a4),
        //     Crossjoin(a1, a2, r2, a4),
        //     Crossjoin(a1, a2, r3, a4))

        // First, decompose into a flat list of non-union regions.
        List<CellRegionShape> nonUnionList = new LinkedList<>();
        flattenUnion(region, nonUnionList);

        for (int i = 0; i < nonUnionList.size(); i++) {
            while (true) {
                CellRegionShape nonUnionRegion = nonUnionList.get(i);
                UnionRegion firstUnion = findFirstUnion(nonUnionRegion);
                if (firstUnion == null) {
                    break;
                }
                List<CellRegionShape> list = new ArrayList<>();
                for (CellRegionShape unionComponent : firstUnion.getRegions()) {
                    // For each unionComponent in (r1, r2, r3),
                    // create Crossjoin(a1, a2, r1, a4).
                    CellRegionShape cj =
                        copyReplacing(
                            nonUnionRegion,
                            firstUnion,
                            unionComponent);
                    list.add(cj);
                }
                // Replace one element which contained a union with several
                // which contain one fewer union. (Double-linked list helps
                // here.)
                nonUnionList.remove(i);
                nonUnionList.addAll(i, list);
            }
        }
        return new UnionRegion(nonUnionList);
    }

    private CellRegionShape copyReplacing(
        CellRegionShape region,
        CellRegionShape seek,
        CellRegionShape replacement)
    {
        if (region == seek) {
            return replacement;
        }
        if (region instanceof UnionRegion union) {
            List<CellRegionShape> list = new ArrayList<>();
            for (CellRegionShape child : union.getRegions()) {
                list.add(copyReplacing(child, seek, replacement));
            }
            return new UnionRegion(list);
        }
        if (region instanceof CrossjoinRegion crossjoin) {
            List<CellRegionShape> list = new ArrayList<>();
            for (CellRegionShape child : crossjoin.getComponents()) {
                list.add(copyReplacing(child, seek, replacement));
            }
            return new CrossjoinRegion(list);
        }
        // This region is atomic, and since regions are immutable we don't need
        // to clone.
        return region;
    }

    /**
     * Flatten a region into a list of regions none of which are unions.
     *
     * @param region Cell region
     * @param list Target list
     */
    private void flattenUnion(
        CellRegionShape region,
        List<CellRegionShape> list)
    {
        if (region instanceof UnionRegion union) {
            for (CellRegionShape region1 : union.getRegions()) {
                flattenUnion(region1, list);
            }
        } else {
            list.add(region);
        }
    }

    /**
     * Flattens a region into a list of regions none of which are unions.
     *
     * @param region Cell region
     * @param list Target list
     */
    private void flattenCrossjoin(
        CellRegionShape region,
        List<CellRegionShape> list)
    {
        if (region instanceof CrossjoinRegion crossjoin) {
            for (CellRegionShape component : crossjoin.getComponents()) {
                flattenCrossjoin(component, list);
            }
        } else {
            list.add(region);
        }
    }

    private UnionRegion findFirstUnion(CellRegion region) {
        final CellRegionVisitor visitor =
            new CellRegionVisitorImpl() {
                @Override
				public void visit(UnionRegion region) {
                    throw new FoundOne(region);
                }
            };
        try {
            ((CellRegionShape) region).accept(visitor);
            return null;
        } catch (FoundOne foundOne) {
            return foundOne.region;
        }
    }

    /**
     * Returns a list of members of the Measures dimension which are mentioned
     * somewhere in a region specification.
     *
     * @param region Cell region
     * @return List of members mentioned in cell region specification
     */
    public static List<Member> findMeasures(CellRegion region) {
        final List<Member> list = new ArrayList<>();
        final CellRegionVisitor visitor =
            new CellRegionVisitorImpl() {
                @Override
				public void visit(MemberRegion region) {
                    if (region.getDimensionality().getFirst().isMeasures()) {
                        list.addAll(region.getMembers());
                    }
                }

                @Override
				public void visit(MemberRangeCellRegion region) {
                    if (region.level.getDimension().isMeasures()) {
                        // hard throw, not assert: with -da (the production
                        // default) the assert was a silent pass-through and
                        // the malformed region flushed nothing predictable
                        throw new IllegalArgumentException(
                            "ranges on the measures dimension are not supported");
                    }
                }
            };
        ((CellRegionShape) region).accept(visitor);
        return list;
    }

    public static SegmentColumn[] findAxisValues(CellRegion region) {
        final List<SegmentColumn> list =
            new ArrayList<>();
        final CellRegionVisitor visitor =
            new CellRegionVisitorImpl() {
                @Override
				public void visit(MemberRegion region) {
                    if (region.getDimensionality().getFirst().isMeasures()) {
                        return;
                    }
                    final Map<String, Set<Comparable>> levels =
                        new HashMap<>();
                    for (Member member : region.getMembers()) {
                        while (true) {
                            if (member == null || member.isAll()) {
                                break;
                            }
                            final String ccName =
                                genericSql(((RolapLevel) member.getLevel()).getKeyExp());
                            if (!levels.containsKey(ccName)) {
                                levels.put(
                                    ccName, new HashSet<>());
                            }
                                levels.get(ccName).add(
                                    (Comparable)((RolapMember)member).getKey());
                            member = member.getParentMember();
                        }
                    }
                    for (Entry<String, Set<Comparable>> entry
                        : levels.entrySet())
                    {
                        // Now sort and convert to an ArraySortedSet.
                        final Comparable[] keys =
                            entry.getValue().toArray(
                                new Comparable[entry.getValue().size()]);
                        if (keys.length == 1 && keys[0].equals(true)) {
                            list.add(
                                new SegmentColumn(
                                    entry.getKey(),
                                    -1,
                                    null));
                        } else {
                            Arrays.sort(
                                keys,
                                RolapUtil.SqlNullSafeComparator.instance);
                            //noinspection unchecked
                            list.add(
                                new SegmentColumn(
                                    entry.getKey(),
                                    -1,
                                    new ArraySortedSet(keys)));
                        }
                    }
                }

                @Override
				public void visit(MemberRangeCellRegion region) {
                    // We translate all ranges into wildcards.
                    // FIXME Optimize this by resolving the list of members
                    // into an actual list of values for ConstrainedColumn
                    list.add(
                        new SegmentColumn(
                            genericSql(region.level.getKeyExp()),
                            -1,
                            null));
                }
            };
        ((CellRegionShape) region).accept(visitor);
        // A union region visits each part separately; merge same-column
        // entries into one (value union, wildcard wins), otherwise a
        // two-part flush on one level intersects with neither part.
        final Map<String, SegmentColumn> merged = new LinkedHashMap<>();
        for (SegmentColumn column : list) {
            merged.merge(column.columnExpression, column, (a, b) -> {
                if (a.values == null || b.values == null) {
                    return new SegmentColumn(a.columnExpression, -1, null);
                }
                final Set<Comparable> union = new HashSet<>(a.values);
                union.addAll(b.values);
                final Comparable[] keys = union.toArray(new Comparable[0]);
                Arrays.sort(keys, RolapUtil.SqlNullSafeComparator.instance);
                return new SegmentColumn(
                    a.columnExpression, -1, new ArraySortedSet(keys));
            });
        }
        return merged.values().toArray(SegmentColumn[]::new);
    }

    public static List<RolapStar> getStarList(CellRegion region) {
        // Figure out which measure (therefore star) it belongs to.
        List<RolapStar> starList = new ArrayList<>();
        final List<Member> measuresList = findMeasures(region);
        for (Member measure : measuresList) {
            if (measure instanceof RolapStoredMeasure storedMeasure) {
                final RolapStar.Measure starMeasure =
                    (RolapStar.Measure) storedMeasure.getStarMeasure();
                if (!starList.contains(starMeasure.getStar())) {
                    starList.add(starMeasure.getStar());
                }
            }
        }
        return starList;
    }

    @Override
	public void printCacheState(
        final PrintWriter pw,
        final CellRegion region)
    {
        final List<RolapStar> starList = getStarList(region);
        for (RolapStar star : starList) {
            star.print(pw, "", false);
        }

		AbstractBasicContext abc = (AbstractBasicContext) connection.getContext();
        final OlapSegmentCacheManager manager =
                ((org.eclipse.daanse.rolap.common.agg.AggregationManager) abc
                        .getAggregationManager()).peekSegmentCacheManager(this.connection);

        // Create ExecutionImpl for printCacheState operation
        final Statement statement = connection.getInternalStatement();
        final ExecutionImpl execution = new ExecutionImpl(statement,
            ExecuteDurationUtil.executeDurationValue(connection.getContext()));

        ExecutionContext.where(execution.asContext(), () -> {
            manager.printCacheState(region, pw, ExecutionContext.current());
        });
    }

    @Override
	public MemberSet createMemberSet(Member member, boolean descendants)
    {
        return new SimpleMemberSet(
            Collections.singletonList((RolapMember) member),
            descendants);
    }

    @Override
	public MemberSet createMemberSet(
        boolean lowerInclusive,
        Member lowerMember,
        boolean upperInclusive,
        Member upperMember,
        boolean descendants)
    {
        if (upperMember != null && lowerMember != null) {
            if (!upperMember.getLevel().equals(lowerMember.getLevel())) {
                throw new IllegalArgumentException("upper member level should be equals lower member level");
            }
        }
        if (lowerMember == null) {
            lowerInclusive = false;
        }
        if (upperMember == null) {
            upperInclusive = false;
        }
        return new RangeMemberSet(
            stripMember((RolapMember) lowerMember), lowerInclusive,
            stripMember((RolapMember) upperMember), upperInclusive,
            descendants);
    }

    @Override
	public MemberSet createUnionSet(MemberSet... args)
    {
        //noinspection unchecked
        return new UnionMemberSet((List) Arrays.asList(args));
    }

    @Override
	public MemberSet filter(Level level, MemberSet baseSet) {
        if (level instanceof RolapCubeLevel rolapCubeLevel) {
            // be forgiving
            level = rolapCubeLevel.getRolapLevel();
        }
        return ((MemberSetPlus) baseSet).filter((RolapLevel) level);
    }

    @Override
	public void flush(MemberSet memberSet) {
        // REVIEW How is flush(s) different to executing createDeleteCommand(s)?
        final List<CellRegion> cellRegionList = new ArrayList<>();
        synchronized (MEMBER_CACHE_LOCK) {
            ((MemberSetPlus) memberSet).accept(
                new MemberSetVisitorImpl() {
                    @Override
					public void visit(RolapMember member) {
                        flushMember(member, cellRegionList);
                    }
                }
           );
            // STUB: flush the set: another visitor

            // native tuple lists of the flushed hierarchies are stale now
            ((AbstractRolapConnection) connection).getCatalog().getNativeRegistry()
                .flushNativeSetCaches();
        }
        // finally, flush cells now invalid - OUTSIDE the member lock: the
        // cell flush waits on external store futures, and the lock guards
        // member-cache edits, not store I/O (queries never take it; only
        // sibling member operations do, and those must not queue behind a
        // slow or hung store)
        flushRegionList(cellRegionList);
    }

    @Override
	public MemberEditCommand createCompoundCommand(
        List<MemberEditCommand> commandList)
    {
        //noinspection unchecked
        return new CompoundCommand((List) commandList);
    }

    @Override
	public MemberEditCommand createCompoundCommand(
        MemberEditCommand... commands)
    {
        //noinspection unchecked
        return new CompoundCommand((List) Arrays.asList(commands));
    }

    @Override
	public MemberEditCommand createDeleteCommand(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("cannot delete null member");
        }
        if (((RolapLevel) member.getLevel()).isParentChild()) {
            throw new IllegalArgumentException(
                "delete member not supported for parent-child hierarchy");
        }
        return createDeleteCommand(createMemberSet(member, false));
    }

    @Override
	public MemberEditCommand createDeleteCommand(MemberSet s) {
        return new DeleteMemberCommand((MemberSetPlus) s);
    }

    @Override
	public MemberEditCommand createAddCommand(
        Member member) throws IllegalArgumentException
    {
        if (member == null) {
            throw new IllegalArgumentException("cannot add null member");
        }
        if (((RolapLevel) member.getLevel()).isParentChild()) {
            throw new IllegalArgumentException(
                "add member not supported for parent-child hierarchy");
        }
        return new AddMemberCommand((RolapMember) member);
    }

    @Override
	public MemberEditCommand createMoveCommand(Member member, Member loc)
        throws IllegalArgumentException
    {
        if (member == null) {
            throw new IllegalArgumentException("cannot move null member");
        }
        if (((RolapLevel) member.getLevel()).isParentChild()) {
            throw new IllegalArgumentException(
                "move member not supported for parent-child hierarchy");
        }
        if (loc == null) {
            throw new IllegalArgumentException(
                "cannot move member to null location");
        }
        // TODO: check that MEMBER and LOC (its new parent) have appropriate
        // Levels
        return new MoveMemberCommand((RolapMember) member, (RolapMember) loc);
    }

    @Override
	public MemberEditCommand createSetPropertyCommand(
        Member member,
        String name,
        Object value)
        throws IllegalArgumentException
    {
        if (member == null) {
            throw new IllegalArgumentException(
                "cannot set properties on null member");
        }
        if (((RolapLevel) member.getLevel()).isParentChild()) {
            throw new IllegalArgumentException(
                "set properties not supported for parent-child hierarchy");
        }
        // TODO: validate that prop NAME exists for Level of MEMBER
        return new ChangeMemberPropsCommand(
            new SimpleMemberSet(
                Collections.singletonList((RolapMember) member),
                false),
            Collections.singletonMap(name, value));
    }

    @Override
	public MemberEditCommand createSetPropertyCommand(
        MemberSet members,
        Map<String, Object> propertyValues)
        throws IllegalArgumentException
    {
        // TODO: check that members all at same Level, and validate that props
        // exist
        validateSameLevel((MemberSetPlus) members);
        return new ChangeMemberPropsCommand(
            (MemberSetPlus) members,
            propertyValues);
    }

    /**
     * Validates that all members of a member set are the same level.
     *
     * @param memberSet Member set
     * @throws IllegalArgumentException if members are from more than one level
     */
    private void validateSameLevel(MemberSetPlus memberSet)
        throws IllegalArgumentException
    {
        memberSet.accept(
            new MemberSetVisitor() {
                final Set<RolapLevel> levelSet = new HashSet<>();

                private void visitMember(
                    RolapMember member,
                    boolean descendants)
                {
                    final String message =
                        "all members in set must belong to same level";
                    if (levelSet.add(member.getLevel())
                        && levelSet.size() > 1)
                    {
                        throw new IllegalArgumentException(message);
                    }
                    if (descendants
                        && member.getLevel().getChildLevel() != null)
                    {
                        throw new IllegalArgumentException(message);
                    }
                }

                @Override
				public void visit(SimpleMemberSet simpleMemberSet) {
                    for (RolapMember member : simpleMemberSet.members) {
                        visitMember(member, simpleMemberSet.descendants);
                    }
                }

                @Override
				public void visit(UnionMemberSet unionMemberSet) {
                    for (MemberSetPlus item : unionMemberSet.items) {
                        item.accept(this);
                    }
                }

                @Override
				public void visit(RangeMemberSet rangeMemberSet) {
                    visitMember(
                        rangeMemberSet.lowerMember,
                        rangeMemberSet.descendants);
                    visitMember(
                        rangeMemberSet.upperMember,
                        rangeMemberSet.descendants);
                }
            }
       );
    }

    @Override
	public void execute(MemberEditCommand cmd) {
        // member edits require effective members=off (hierarchy tag over
        // cube policy) on every cube that uses an affected hierarchy;
        // unrelated cubes may keep caching
        final Set<RolapHierarchy> affectedHierarchies = new LinkedHashSet<>();
        ((MemberEditCommandPlus) cmd).collectAffectedHierarchies(affectedHierarchies);
        for (RolapHierarchy hierarchy : affectedHierarchies) {
            for (Cube cube : connection.getCatalog().getCubes()) {
                if (cube instanceof org.eclipse.daanse.rolap.element.RolapCube rolapCube
                        && CachePolicy.membersFor(hierarchy.getMetaData(), rolapCube.getCachePolicy())
                        && rolapCube.usesSharedHierarchy(hierarchy)) {
                    throw new IllegalArgumentException(
                        "Member cache control operations are not allowed while cube '" + cube.getName()
                            + "' caches members of hierarchy '" + hierarchy.getUniqueName()
                            + "'; tag the cube daanse:cache.members=off or set "
                            + "daanse.rolap.EnableRolapCubeMemberCache to false");
                }
            }
        }
        // The cell flush below runs INSIDE this lock because commit() must
        // come after it (a flush failure leaves the un-edited, consistent
        // state behind). The store waits inside the flush share ONE budget
        // for the whole member edit (regions x cubes x union parts used to
        // multiply fresh 30s deadlines), so a hung store cannot hold the
        // member lock beyond it.
        synchronized (MEMBER_CACHE_LOCK) {
            // started AFTER the lock was won: a long wait for a sibling
            // edit must not consume the store-wait budget before any store
            // was asked - the flush then "succeeded" with zero confirmation
            final FlushDeadline editDeadline = FlushDeadline.standard();
            // Make sure that an ExecutionContext is bound,
            // since some operations might require DB access.
            Execution execution;
            try {
                execution =
                    ExecutionContext.current().getExecution();
            } catch (NoExecutionContextException e) {
                if (connection == null) {
                    throw new IllegalArgumentException("Connection required");
                }
                execution = new ExecutionImpl(connection.getInternalStatement(), ExecuteDurationUtil.executeDurationValue(connection.getContext()));
            }

            // Use ExecutionContext.where() instead of push/pop
            final Execution finalExecution = execution;
            ExecutionContext.where(finalExecution.asContext(), () -> {
                // Execute the command
                final List<CellRegion> cellRegionList =
                    new ArrayList<>();
                ((MemberEditCommandPlus) cmd).execute(cellRegionList);

                // Flush the cells touched by the regions
                for (CellRegion memberRegion : cellRegionList) {
                    // Iterate over the cubes, create a cross region with
                    // its measures, and flush the data cells.
                    // It is possible that some regions don't intersect
                    // with a cube. We will intercept the exceptions and
                    // skip to the next cube if necessary.
                    final List<Dimension> dimensions =
                        memberRegion.getDimensionality();
                    if (!dimensions.isEmpty()) {
                        for (Cube cube
                            : dimensions.getFirst() .getCatalog().getCubes())
                        {
                            try {
                                final List<CellRegionShape> crossList =
                                    new ArrayList<>();
                                crossList.add(
                                    (CellRegionShape)
                                        createMeasuresRegion(cube));
                                crossList.add((CellRegionShape) memberRegion);
                                final CellRegion crossRegion =
                                    new CrossjoinRegion(crossList);
                                flushWithDeadline(crossRegion, editDeadline);
                            } catch (UndeclaredThrowableException e) {
                                if (e.getCause()
                                    instanceof InvocationTargetException ite)
                                {
                                    if (ite.getTargetException()
                                        instanceof OlapRuntimeException me)
                                    {
                                        if (me.getMessage()
                                            .matches(
                                                "^Daanse Error:Member '\\[.*\\]' not found$"))
                                        {
                                            continue;
                                        }
                                    }
                                }
                                throw new OlapRuntimeException(e);
                            } catch (OlapRuntimeException e) {
                                if (e.getMessage()
                                    .matches(
                                        "^Daanse Error:Member '\\[.*\\]' not found$"))
                                {
                                    continue;
                                }
                                throw e;
                            }
                        }
                    }
                }
                // Apply it all.
                ((MemberEditCommandPlus) cmd).commit();
                // tuple flush LAST (bump/mutate first, clear last) - the
                // order the publisher's flush-epoch validation relies on
                ((AbstractRolapConnection) connection).getCatalog().getNativeRegistry()
                    .flushNativeSetCaches();
            });
        }
    }

    /**
     * Null when the hierarchy's reader does not cache (members=off) - and
     * execute(MemberEditCommand) guarantees exactly that (it throws while
     * any using cube caches members of the hierarchy), so the edit
     * commands' cache mutations never run in a legal configuration today.
     * They survive as the specification of a future members=on edit path.
     */
    private static MemberCache getMemberCache(RolapMember member) {
        final MemberReader memberReader =
            member.getHierarchy().getMemberReader();
        if (memberReader instanceof CachingMemberReader cachingMemberReader) {
            return cachingMemberReader.getMemberCache();
        }
        return null;
    }

    // cell cache control implementation

    /**
     * Cell region formed a range of members between a lower and upper bound.
     */
    public static class MemberRangeCellRegion implements CellRegionShape {
        private final RolapMember lowerMember;
        private final boolean lowerInclusive;
        private final RolapMember upperMember;
        private final boolean upperInclusive;
        private final boolean descendants;
        private final RolapLevel level;

        MemberRangeCellRegion(
            RolapMember lowerMember,
            boolean lowerInclusive,
            RolapMember upperMember,
            boolean upperInclusive,
            boolean descendants)
        {
            assert lowerMember != null || upperMember != null;
            assert lowerMember == null
                || upperMember == null
                || lowerMember.getLevel() == upperMember.getLevel();
            assert !(lowerMember == null && lowerInclusive);
            assert !(upperMember == null && upperInclusive);
            this.lowerMember = lowerMember;
            this.lowerInclusive = lowerInclusive;
            this.upperMember = upperMember;
            this.upperInclusive = upperInclusive;
            this.descendants = descendants;
            this.level =
                lowerMember == null
                ? upperMember.getLevel()
                : lowerMember.getLevel();
        }

        @Override
		public List<Dimension> getDimensionality() {
            return Collections.singletonList(level.getDimension());
        }

        public RolapLevel getLevel() {
            return level;
        }

        @Override
		public String toString() {
            final StringBuilder sb = new StringBuilder("Range(");
            if (lowerMember == null) {
                sb.append("null");
            } else {
                sb.append(lowerMember);
                if (lowerInclusive) {
                    sb.append(" inclusive");
                } else {
                    sb.append(" exclusive");
                }
            }
            sb.append(" to ");
            if (upperMember == null) {
                sb.append("null");
            } else {
                sb.append(upperMember);
                if (upperInclusive) {
                    sb.append(" inclusive");
                } else {
                    sb.append(" exclusive");
                }
            }
            sb.append(")");
            return sb.toString();
        }

        /**
         * A range says which members lie in it through the level it is on; whether one single cell lies
         * in it is a question this art answers by turning the range into a predicate, not cell by cell.
         */
        @Override
        public boolean covers(List<? extends Member> coordinate) {
            throw new UnsupportedOperationException(
                "A range of members is read as a predicate, not cell by cell");
        }

        @Override
        public void accept(CellRegionVisitor visitor) {
            visitor.visitOther(this);
        }

        public boolean getLowerInclusive() {
            return lowerInclusive;
        }

        public RolapMember getLowerBound() {
            return lowerMember;
        }

        public boolean getUpperInclusive() {
            return upperInclusive;
        }

        public RolapMember getUpperBound() {
            return upperMember;
        }
    }

    private static class FoundOne extends RuntimeException {
        private final transient UnionRegion region;

        public FoundOne(UnionRegion region) {
            this.region = region;
        }
    }

    /**
     * Reads a region shape by shape and does nothing with any of them; a caller overrides the shapes it
     * cares about. The range of members is this art's own shape, so it arrives through
     * {@link CellRegionVisitor#visitOther}.
     */
    private static class CellRegionVisitorImpl implements CellRegionVisitor {
        @Override
        public void visit(MemberRegion region) {
            // nothing to do
        }

        public void visit(MemberRangeCellRegion region) {
            // nothing to do
        }

        @Override
        public void visit(UnionRegion region) {
            // nothing to do
        }

        @Override
        public void visit(CrossjoinRegion region) {
            // nothing to do
        }

        @Override
        public void visit(EmptyRegion region) {
            // nothing to do
        }

        @Override
        public void visitOther(CellRegion region) {
            if (region instanceof MemberRangeCellRegion range) {
                visit(range);
            }
        }
    }



    // ~ member cache control implementation ----------------------------------

    /**
     * Implementation-specific extensions to the
     * {@link org.eclipse.daanse.olap.api.CacheControl.MemberEditCommand} interface.
     */
    interface MemberEditCommandPlus extends MemberEditCommand {
        /**
         * Executes this command, and gathers a list of cell regions affected
         * in the {@code cellRegionList} parameter. The caller will flush the
         * cell regions later.
         *
         * @param cellRegionList Populated with a list of cell regions which
         * are invalidated by this action
         */
        void execute(final List<CellRegion> cellRegionList);

        void commit();

        /** Adds the shared hierarchies this command touches to {@code out}. */
        void collectAffectedHierarchies(Set<RolapHierarchy> out);
    }

    /**
     * Implementation-specific extensions to the
     * {@link org.eclipse.daanse.olap.api.CacheControl.MemberSet} interface.
     */
    interface MemberSetPlus extends MemberSet {
        /**
         * Accepts a visitor.
         *
         * @param visitor Visitor
         */
        void accept(MemberSetVisitor visitor);

        /**
         * Filters this member set, returning a member set containing all
         * members at a given Level. When applicable, returns this member set
         * unchanged.
         *
         * @param level Level
         * @return Member set with members not at the given level removed
         */
        MemberSetPlus filter(RolapLevel level);
    }

    /**
     * Visits the subclasses of {@link MemberSetPlus}.
     */
    interface MemberSetVisitor {
        void visit(SimpleMemberSet s);
        void visit(UnionMemberSet s);
        void visit(RangeMemberSet s);
    }

    /**
     * Default implementation of {@link MemberSetVisitor}.
     *
     * The default implementation may not be efficient. For example, if
     * flushing a range of members from the cache, you may not wish to fetch
     * all of the members into the cache in order to flush them.
     */
    public abstract static class MemberSetVisitorImpl
        implements MemberSetVisitor
    {
        @Override
		public void visit(UnionMemberSet s) {
            for (MemberSetPlus item : s.items) {
                item.accept(this);
            }
        }

        @Override
		public void visit(RangeMemberSet s) {
            final MemberReader memberReader =
                s.level.getHierarchy().getMemberReader();
            visitRange(
                memberReader, s.level, s.lowerMember, s.upperMember,
                s.descendants);
        }

        protected void visitRange(
            MemberReader memberReader,
            RolapLevel level,
            RolapMember lowerMember,
            RolapMember upperMember,
            boolean recurse)
        {
            final List<RolapMember> list = new ArrayList<>();
            memberReader.getMemberRange(level, lowerMember, upperMember, list);
            for (RolapMember member : list) {
                visit(member);
            }
            if (recurse) {
                list.clear();
                memberReader.getMemberChildren(lowerMember, list);
                if (list.isEmpty()) {
                    return;
                }
                RolapMember lowerChild = list.getFirst();
                list.clear();
                memberReader.getMemberChildren(upperMember, list);
                if (list.isEmpty()) {
                    return;
                }
                RolapMember upperChild = list.getLast();
                visitRange(
                    memberReader, level, lowerChild, upperChild, recurse);
            }
        }

        @Override
		public void visit(SimpleMemberSet s) {
            for (RolapMember member : s.members) {
                visit(member);
            }
        }

        /**
         * Visits a single member.
         *
         * @param member Member
         */
        public abstract void visit(RolapMember member);
    }

    /**
     * Member set containing no members.
     */
    static class EmptyMemberSet implements MemberSetPlus {
        public static final EmptyMemberSet INSTANCE = new EmptyMemberSet();

        private EmptyMemberSet() {
            // prevent instantiation except for singleton
        }

        @Override
		public void accept(MemberSetVisitor visitor) {
            // nothing
        }

        @Override
		public MemberSetPlus filter(RolapLevel level) {
            return this;
        }

        @Override
		public String toString() {
            return "Empty";
        }
    }

    /**
     * Member set defined by a list of members from one hierarchy.
     */
    static class SimpleMemberSet implements MemberSetPlus {
        public final List<RolapMember> members;
        // the set includes the descendants of all members
        public final boolean descendants;
        public final RolapHierarchy hierarchy;

        SimpleMemberSet(List<RolapMember> members, boolean descendants) {
            this.members = new ArrayList<>(members);
            stripMemberList(this.members);
            this.descendants = descendants;
            // NOTE: derived from the UNSTRIPPED parameter members - can be a
            // RolapCubeHierarchy while this.members are shared; every consumer
            // re-derives via sharedHierarchy(...), do not trust this field raw

            this.hierarchy =
                members.isEmpty()
                    ? null
                    : members.getFirst().getHierarchy();
        }

        @Override
		public String toString() {
            return Util.commaList("Member", members);
        }

        @Override
		public void accept(MemberSetVisitor visitor) {
            // Don't descend the subtrees here: may not want to load them into
            // cache.
            visitor.visit(this);
        }

        @Override
		public MemberSetPlus filter(RolapLevel level) {
            List<RolapMember> filteredMembers = new ArrayList<>();
            for (RolapMember member : members) {
                if (member.getLevel().equalsOlapElement(level)) {
                    filteredMembers.add(member);
                }
            }
            if (filteredMembers.isEmpty()) {
                return EmptyMemberSet.INSTANCE;
            } else if (filteredMembers.equals(members)) {
                return this;
            } else {
                return new SimpleMemberSet(filteredMembers, false);
            }
        }
    }

    /**
     * Member set defined by the union of other member sets.
     */
    static class UnionMemberSet implements MemberSetPlus {
        private final List<MemberSetPlus> items;

        UnionMemberSet(List<MemberSetPlus> items) {
            this.items = items;
        }

        @Override
		public String toString() {
            final StringBuilder sb = new StringBuilder("Union(");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                MemberSetPlus item = items.get(i);
                sb.append(item.toString());
            }
            sb.append(")");
            return sb.toString();
        }

        @Override
		public void accept(MemberSetVisitor visitor) {
            visitor.visit(this);
        }

        @Override
		public MemberSetPlus filter(RolapLevel level) {
            final List<MemberSetPlus> filteredItems =
                new ArrayList<>();
            for (MemberSetPlus item : items) {
                final MemberSetPlus filteredItem = item.filter(level);
                if (filteredItem == EmptyMemberSet.INSTANCE) {
                    // skip it
                } else {
                    assert !(filteredItem instanceof EmptyMemberSet);
                    filteredItems.add(filteredItem);
                }
            }
            if (filteredItems.isEmpty()) {
                return EmptyMemberSet.INSTANCE;
            } else if (filteredItems.equals(items)) {
                return this;
            } else {
                return new UnionMemberSet(filteredItems);
            }
        }
    }

    /**
     * Member set defined by a range of members between a lower and upper
     * bound.
     */
    static class RangeMemberSet implements MemberSetPlus {
        private final RolapMember lowerMember;
        private final boolean lowerInclusive;
        private final RolapMember upperMember;
        private final boolean upperInclusive;
        private final boolean descendants;
        private final RolapLevel level;

        RangeMemberSet(
            RolapMember lowerMember,
            boolean lowerInclusive,
            RolapMember upperMember,
            boolean upperInclusive,
            boolean descendants)
        {
            assert lowerMember != null || upperMember != null;
            assert lowerMember == null
                || upperMember == null
                || lowerMember.getLevel() == upperMember.getLevel();
            assert !(lowerMember == null && lowerInclusive);
            assert !(upperMember == null && upperInclusive);
            assert !(lowerMember instanceof RolapCubeMember);
            assert !(upperMember instanceof RolapCubeMember);
            this.lowerMember = lowerMember;
            this.lowerInclusive = lowerInclusive;
            this.upperMember = upperMember;
            this.upperInclusive = upperInclusive;
            this.descendants = descendants;
            this.level =
                lowerMember == null
                ? upperMember.getLevel()
                : lowerMember.getLevel();
        }

        @Override
		public String toString() {
            final StringBuilder sb = new StringBuilder("Range(");
            if (lowerMember == null) {
                sb.append("null");
            } else {
                sb.append(lowerMember);
                if (lowerInclusive) {
                    sb.append(" inclusive");
                } else {
                    sb.append(" exclusive");
                }
            }
            sb.append(" to ");
            if (upperMember == null) {
                sb.append("null");
            } else {
                sb.append(upperMember);
                if (upperInclusive) {
                    sb.append(" inclusive");
                } else {
                    sb.append(" exclusive");
                }
            }
            sb.append(")");
            return sb.toString();
        }

        @Override
		public void accept(MemberSetVisitor visitor) {
            // Don't traverse the range here: may not want to load it into cache
            visitor.visit(this);
        }

        @Override
		public MemberSetPlus filter(RolapLevel level) {
            if (level == this.level) {
                return this;
            } else {
                return filter2(level, this.level, lowerMember, upperMember);
            }
        }

        public MemberSetPlus filter2(
            RolapLevel seekLevel,
            RolapLevel level,
            RolapMember lower,
            RolapMember upper)
        {
            if (level == seekLevel) {
                return new RangeMemberSet(
                    lower, lowerInclusive, upper, upperInclusive, false);
            } else if (descendants
                && level.getHierarchy() == seekLevel.getHierarchy()
                && level.getDepth() < seekLevel.getDepth())
            {
                final MemberReader memberReader =
                    level.getHierarchy().getMemberReader();
                final List<RolapMember> list = new ArrayList<>();
                memberReader.getMemberChildren(lower, list);
                if (list.isEmpty()) {
                    return EmptyMemberSet.INSTANCE;
                }
                RolapMember lowerChild = list.getFirst();
                list.clear();
                memberReader.getMemberChildren(upper, list);
                if (list.isEmpty()) {
                    return EmptyMemberSet.INSTANCE;
                }
                RolapMember upperChild = list.getLast();
                return filter2(
                    seekLevel, (RolapLevel) level.getChildLevel(),
                    lowerChild, upperChild);
            } else {
                return EmptyMemberSet.INSTANCE;
            }
        }
    }

    /**
     * Command consisting of a set of commands executed in sequence.
     */
    /** Shared hierarchies referenced by a member set, without loading members. */
    private static void collectHierarchies(MemberSetPlus set, Set<RolapHierarchy> out) {
        set.accept(new MemberSetVisitor() {
            @Override
            public void visit(SimpleMemberSet s) {
                for (RolapMember member : s.members) {
                    out.add(sharedHierarchy(member.getHierarchy()));
                }
            }

            @Override
            public void visit(UnionMemberSet s) {
                for (MemberSetPlus item : s.items) {
                    item.accept(this);
                }
            }

            @Override
            public void visit(RangeMemberSet s) {
                out.add(sharedHierarchy(s.level.getHierarchy()));
            }
        });
    }

    /** Members loaded through a cube can report the cube hierarchy; unwrap it. */
    private static RolapHierarchy sharedHierarchy(RolapHierarchy hierarchy) {
        return hierarchy instanceof org.eclipse.daanse.rolap.element.RolapCubeHierarchy cubeHierarchy
            ? cubeHierarchy.getRolapHierarchy()
            : hierarchy;
    }

    private static class CompoundCommand implements MemberEditCommandPlus {
        private final List<MemberEditCommandPlus> commandList;

        CompoundCommand(List<MemberEditCommandPlus> commandList) {
            this.commandList = commandList;
        }

        @Override
		public String toString() {
            return Util.commaList("Compound", commandList);
        }

        @Override
		public void execute(final List<CellRegion> cellRegionList) {
            for (MemberEditCommandPlus command : commandList) {
                command.execute(cellRegionList);
            }
        }

        @Override
		public void commit() {
            for (MemberEditCommandPlus command : commandList) {
                command.commit();
            }
        }

        @Override
        public void collectAffectedHierarchies(Set<RolapHierarchy> out) {
            for (MemberEditCommandPlus command : commandList) {
                command.collectAffectedHierarchies(out);
            }
        }
    }

    /**
     * Command that deletes a member and its descendants from the cache.
     */
    private class DeleteMemberCommand
        extends MemberSetVisitorImpl
        implements MemberEditCommandPlus
    {
        private final MemberSetPlus set;
        private List<CellRegion> cellRegionList;
        private Callable<Boolean> callable;

        DeleteMemberCommand(MemberSetPlus set) {
            this.set = set;
        }

        @Override
		public String toString() {
            return new StringBuilder("DeleteMemberCommand(").append(set).append(")").toString();
        }

        @Override
		public void execute(final List<CellRegion> cellRegionList) {
            // NOTE: use of cellRegionList makes this class non-reentrant
            this.cellRegionList = cellRegionList;
            set.accept(this);
            this.cellRegionList = null;
        }

        @Override
		public void visit(RolapMember member) {
            this.callable =
                deleteMember(member, member.getParentMember(), cellRegionList);
        }

        @Override
		public void commit() {
            try {
                callable.call();
            } catch (Exception e) {
                throw new OlapRuntimeException(e);
            }
        }

        @Override
        public void collectAffectedHierarchies(Set<RolapHierarchy> out) {
            collectHierarchies(set, out);
        }
    }

    /**
     * Command that adds a new member to the cache.
     */
    private class AddMemberCommand implements MemberEditCommandPlus {
        private final RolapMember member;
        private Callable<Boolean> callable;

        public AddMemberCommand(RolapMember member) {
            if (member == null) {
                throw new IllegalArgumentException("member should be not null");
            }
            this.member = stripMember(member);
        }

        @Override
		public String toString() {
            return new StringBuilder("AddMemberCommand(").append(member).append(")").toString();
        }

        @Override
		public void execute(List<CellRegion> cellRegionList) {
            this.callable =
                addMember(member, member.getParentMember(), cellRegionList);
        }

        @Override
		public void commit() {
            try {
                callable.call();
            } catch (Exception e) {
                throw new OlapRuntimeException(e);
            }
        }

        @Override
        public void collectAffectedHierarchies(Set<RolapHierarchy> out) {
            out.add(sharedHierarchy(member.getHierarchy()));
        }
    }

    /**
     * Command that moves a member to a new parent.
     */
    private class MoveMemberCommand implements MemberEditCommandPlus {
        private final RolapMember member;
        private final RolapMember newParent;
        private Callable<Boolean> callable1;
        private Callable<Boolean> callable2;

        MoveMemberCommand(RolapMember member, RolapMember newParent) {
            this.member = member;
            this.newParent = newParent;
        }

        @Override
		public String toString() {
            return new StringBuilder("MoveMemberCommand(").append(member).append(", ")
                .append(newParent).append(")").toString();
        }

        @Override
		public void execute(final List<CellRegion> cellRegionList) {
            this.callable1 =
                deleteMember(member, member.getParentMember(), cellRegionList);
            this.callable2 =
                addMember(member, newParent, cellRegionList);
        }

        @Override
		public void commit() {
            try {
                ((RolapMemberBase) member).setParentMember(newParent);
                callable1.call();
                ((RolapMemberBase) member).setUniqueName(member.getKey());
                callable2.call();
            } catch (Exception e) {
                throw new OlapRuntimeException(e);
            }
        }

        @Override
        public void collectAffectedHierarchies(Set<RolapHierarchy> out) {
            out.add(sharedHierarchy(stripMember(member).getHierarchy()));
            out.add(sharedHierarchy(stripMember(newParent).getHierarchy()));
        }
    }

    /**
     * Command that changes one or more properties of a member.
     */
    private static class ChangeMemberPropsCommand
        extends MemberSetVisitorImpl
        implements MemberEditCommandPlus
    {
        final MemberSetPlus memberSet;
        final Map<String, Object> propertyValues;
        final List<RolapMember> members =
            new ArrayList<>();

        ChangeMemberPropsCommand(
            MemberSetPlus memberSet,
            Map<String, Object> propertyValues)
        {
            this.memberSet = memberSet;
            this.propertyValues = propertyValues;
        }

        @Override
		public String toString() {
            return new StringBuilder("CreateMemberPropsCommand(").append(memberSet)
                .append(", ").append(propertyValues).append(")").toString();
        }

        @Override
		public void execute(List<CellRegion> cellRegionList) {
            // ignore cellRegionList - no changes to cell cache
            memberSet.accept(this);
        }

        @Override
		public void visit(RolapMember member) {
            members.add(member);
        }

        @Override
		public void commit() {
            for (RolapMember member : members) {
                // Change member's properties.
                member = stripMember(member);
                final MemberCache memberCache = getMemberCache(member);
                if (memberCache == null) {
                    continue;
                }
                final Object cacheKey =
                    memberCache.makeKey(
                        member.getParentMember(),
                        member.getKey());
                final RolapMember cacheMember = memberCache.getMember(cacheKey);
                if (cacheMember == null) {
                    // this member is not cached; the next one may be
                    continue;
                }
                for (Map.Entry<String, Object> entry
                    : propertyValues.entrySet())
                {
                    cacheMember.setProperty(entry.getKey(), entry.getValue());
                }
            }
        }

        @Override
        public void collectAffectedHierarchies(Set<RolapHierarchy> out) {
            collectHierarchies(memberSet, out);
        }
    }

    private static RolapMember stripMember(RolapMember member) {
        if (member instanceof RolapCubeMember rolapCubeMember) {
            member = rolapCubeMember.member;
        }
        return member;
    }

    private static void stripMemberList(List<RolapMember> members) {
        for (int i = 0; i < members.size(); i++) {
            RolapMember member = members.get(i);
            if (member instanceof RolapCubeMember rolapCubeMember) {
                members.set(i, rolapCubeMember.member);
            }
        }
    }

    private Callable<Boolean> deleteMember(
        final RolapMember member,
        final RolapMember previousParent,
        List<CellRegion> cellRegionList)
    {
        // Cells for member and its ancestors are now invalid.
        // It's sufficient to flush the member.
        cellRegionList.add(createMemberRegion(member, true));

        return new Callable<>() {
            @Override
			public Boolean call() throws Exception {
                final MemberCache memberCache = getMemberCache(member);
                if (memberCache == null) {
                    return true;
                }
                final MemberChildrenConstraint memberConstraint =
                    new ChildByNameConstraint(
                        new IdImpl.NameSegmentImpl(member.getName()));

                // Remove the member from its parent's lists. First try the
                // unconstrained cache.
                final List<RolapMember> childrenList =
                    memberCache.getChildrenFromCache(
                        previousParent,
                        DefaultMemberChildrenConstraint.instance());
                if (childrenList != null) {
                    // replace, never mutate: readers iterate the cached list
                    // outside any shared lock
                    final List<RolapMember> spliced =
                        new ArrayList<>(childrenList);
                    spliced.remove(member);
                    memberCache.putChildren(
                        previousParent,
                        DefaultMemberChildrenConstraint.instance(),
                        spliced);
                }

                // The old parent's NAMED-children entry may still list the
                // moved member. putChildren(parent, constraint, null) was a
                // silent no-op (the cache ignores null lists), and
                // MemberCache has no named-children removal primitive -
                // this gap is documented rather than papered over. The
                // whole command is unreachable in legal configurations
                // (see getMemberCache), so it records the members=on
                // specification, not live behavior.

                // Let's update the level members cache.
                final List<RolapMember> levelMembers =
                    memberCache
                        .getLevelMembersFromCache(
                            member.getLevel(),
                            DefaultTupleConstraint.instance());
                if (levelMembers != null) {
                    final List<RolapMember> remaining =
                        new ArrayList<>(levelMembers);
                    remaining.remove(member);
                    memberCache.putChildren(
                        member.getLevel(),
                        DefaultTupleConstraint.instance(),
                        remaining);
                }

                // Remove the member itself. The MemberCacheImpl takes care of
                // removing the member's children as well.
                final Object key =
                    memberCache.makeKey(previousParent, member.getKey());
                memberCache.removeMember(key);

                return true;
            }
        };
    }

    /**
     * Adds a member to cache.
     *
     * @param member Member
     * @param parent Member's parent (generally equals member.getParentMember)
     * @param cellRegionList List of cell regions to be flushed
     *
     * @return Callable that yields true when the member has been added to the
     * cache
     */
    private Callable<Boolean> addMember(
        final RolapMember member,
        final RolapMember parent,
        List<CellRegion> cellRegionList)
    {
        // Cells for all of member's ancestors are now invalid. It's sufficient
        // to flush its parent.
        cellRegionList.add(createMemberRegion(parent, false));

        return new Callable<>() {
            @Override
			public Boolean call() throws Exception {
                final MemberCache memberCache = getMemberCache(member);
                if (memberCache == null) {
                    return true;
                }
                final MemberChildrenConstraint memberConstraint =
                    new ChildByNameConstraint(
                        new IdImpl.NameSegmentImpl(member.getName()));

                // Check if there is already a list in cache
                // constrained by a wildcard.
                List<RolapMember> childrenList =
                    memberCache.getChildrenFromCache(
                        parent,
                        DefaultMemberChildrenConstraint.instance());
                if (childrenList != null) {
                    // A list existed before. We can save a SQL query.
                    // Replace, never mutate: readers hold the old list.
                    final List<RolapMember> extended =
                        new ArrayList<>(childrenList);
                    extended.add(member);
                    // under the SAME constraint the list was read with -
                    // writing the full sibling list under the ChildByName
                    // constraint poisoned the named-children cache while
                    // the wildcard entry stayed stale (deleteMember does
                    // this correctly)
                    memberCache.putChildren(
                        parent,
                        DefaultMemberChildrenConstraint.instance(),
                        extended);
                }

                final List<RolapMember> levelMembers =
                    memberCache
                        .getLevelMembersFromCache(
                            member.getLevel(),
                            DefaultTupleConstraint.instance());
                if (levelMembers != null) {
                    // There was already a cached list. Replace, never mutate.
                    final List<RolapMember> extended =
                        new ArrayList<>(levelMembers);
                    extended.add(member);
                    memberCache.putChildren(
                        member.getLevel(),
                        DefaultTupleConstraint.instance(),
                        extended);
                }

                // Now add the member itself into cache
                final Object memberKey =
                    memberCache.makeKey(
                        member.getParentMember(),
                        member.getKey());
                memberCache.putMember(memberKey, member);

                return true;
            }
        };
    }

    /**
     * Removes a member from cache.
     *
     * @param member Member
     * @param cellRegionList Populated with cell region to be flushed
     */
    private void flushMember(
        RolapMember member,
        List<CellRegion> cellRegionList)
    {
        final RolapMember stripped = stripMember(member);
        final MemberCache sharedCache = getMemberCache(stripped);
        if (sharedCache != null) {
            removeFromCache(sharedCache, stripped);
        }
        // member sets are stripped to shared members on creation, but every
        // cube usage of the hierarchy keeps its own caches: the wrapper
        // cache (cube-member keys) and the reader's inherited shared-member
        // cache. Member equality is shared/cube-agnostic, so the stripped
        // key hits the wrapper entries too.
        final Hierarchy sharedHierarchy = stripped.getHierarchy();
        for (RolapCube cube
                : ((AbstractRolapConnection) connection).getCatalog().getCubeList()) {
            for (Hierarchy hierarchy : cube.getHierarchies()) {
                if (hierarchy instanceof RolapCubeHierarchy cubeHierarchy
                        && cubeHierarchy.getRolapHierarchy().equals(sharedHierarchy)
                        && cubeHierarchy.getMemberReader()
                            instanceof RolapCubeHierarchy.RolapCubeHierarchyMemberReader cubeReader) {
                    removeFromCache(cubeReader.getRolapCubeMemberCache(), stripped);
                    if (cubeReader instanceof CachingMemberReader cachingReader
                            && cachingReader.getMemberCache() != sharedCache) {
                        removeFromCache(cachingReader.getMemberCache(), stripped);
                    }
                }
            }
        }
        cellRegionList.add(createMemberRegion(stripped, false));
    }

    private static void removeFromCache(MemberCache cache, RolapMember member) {
        cache.removeMember(
            cache.makeKey(member.getParentMember(), member.getKey()));
    }
}
