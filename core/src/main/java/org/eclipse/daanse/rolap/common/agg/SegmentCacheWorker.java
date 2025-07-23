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
 */


package org.eclipse.daanse.rolap.common.agg;

import static org.eclipse.daanse.olap.exceptions.SegmentCacheFailedToInstanciateException.segmentCacheFailedToInstanciate;
import static org.eclipse.daanse.olap.exceptions.SegmentCacheFailedToLoadSegmentException.segmentCacheFailedToLoadSegment;
import static org.eclipse.daanse.olap.exceptions.SegmentCacheFailedToSaveSegmentException.segmentCacheFailedToSaveSegment;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.exceptions.SegmentCacheFailedToInstanciateException;
import org.eclipse.daanse.olap.exceptions.SegmentCacheFailedToLoadSegmentException;
import org.eclipse.daanse.olap.exceptions.SegmentCacheFailedToSaveSegmentException;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.rolap.util.ClassResolver;
import org.eclipse.daanse.rolap.util.ServiceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to interact with the {@link SegmentCache}.
 *
 * @author LBoudreau
 * @see SegmentCache
 */
public final class SegmentCacheWorker {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SegmentCacheWorker.class);
    private final static String segmentCacheIsNotImplementingInterface = """
    The mondrian.rolap.SegmentCache property points to a class name which is not an
            implementation of mondrian.spi.SegmentCache.
    """;

    final SegmentCache cache;
    private final Thread cacheMgrThread;
    private final boolean supportsRichIndex;
    private final static String segmentCacheFailedToDeleteSegment =
        "An exception was encountered while deleting a segment from the SegmentCache.";
    private final static String segmentCacheFailedToScanSegments =
        "An exception was encountered while getting a list of segment headers in the SegmentCache.";

    /**
     * Creates a worker.
     *
     * @param cache Cache managed by this worker
     * @param cacheMgrThread Thread that the cache manager actor is running on,
     *                       and which therefore should not be used for
     *                       potentially long-running calls this this cache.
     *                       Pass null if methods can be called from any thread.
     */
    public SegmentCacheWorker(SegmentCache cache, Thread cacheMgrThread) {
        this.cache = cache;
        this.cacheMgrThread = cacheMgrThread;

        // no need to call checkThread(): supportsRichIndex is a fast call
        this.supportsRichIndex = cache.supportsRichIndex();

        LOGGER.debug(
            "Segment cache initialized: "
            + cache.getClass().getName());
    }

    /**
     * Instantiates a cache. Returns null if there is no external cache defined.
     *
     * @return Cache
     */
    public static List<SegmentCache> initCache(final String segmentCache) {
        final List<SegmentCache> caches =
            new ArrayList<>();
        // First try to get the segmentcache impl class from
        // mondrian properties.
        final String cacheName = segmentCache;
        if (cacheName != null) {
            caches.add(instantiateCache(cacheName));
        }

        // There was no property set. Let's look for Java services.
        final List<Class<SegmentCache>> implementors =
            ServiceDiscovery.forClass(SegmentCache.class).getImplementor();
        if (implementors.size() > 0) {
            // The contract is to use the first implementation found.
            SegmentCache cache =
                instantiateCache(implementors.get(0).getName());
            if (cache != null) {
                caches.add(cache);
            }
        }

        // Check the SegmentCacheInjector
        // People might have sent instances into this thing.
        caches.addAll(SegmentCache.SegmentCacheInjector.getCaches());

        // Done.
        return caches;
    }

    /**
     * Instantiates a cache, given the name of the cache class.
     *
     * @param cacheName Name of class that implements the
     *     {@link mondrian.spi.SegmentCache} SPI
     *
     * @return Cache instance, or null on error
     */
    private static SegmentCache instantiateCache(String cacheName) {
        try {
            LOGGER.debug("Starting cache instance: " + cacheName);
            return ClassResolver.INSTANCE.instantiateSafe(cacheName);
        } catch (ClassCastException e) {
            throw new OlapRuntimeException(segmentCacheIsNotImplementingInterface);
        } catch (Exception e) {
            LOGGER.error(
                    segmentCacheFailedToInstanciate,
                e);
            throw new SegmentCacheFailedToInstanciateException(e);
        }
    }

    /**
     * Returns a segment body corresponding to a header.
     *
     * If no cache is configured or there is an error while
     * querying the cache, null is returned none the less.
     *
     * @param header Header to search.
     * @return Either a segment body object or null if there
     * was no cache configured or no segment could be found
     * for the passed header.
     */
    public SegmentBody get(SegmentHeader header) {
        checkThread();
        try {
            return cache.get(header);
        } catch (Throwable t) {
            LOGGER.error(segmentCacheFailedToLoadSegment,
                t);
            throw new SegmentCacheFailedToLoadSegmentException(t);
        }
    }

    /**
     * Places a segment in the cache. Returns true or false
     * if the operation succeeds.
     *
     * @param header A header to search for in the segment cache.
     * @param body The segment body to cache.
     */
    public void put(SegmentHeader header, SegmentBody body) {
        checkThread();
        try {
            final boolean result = cache.put(header, body);
            if (!result) {
                LOGGER.error(segmentCacheFailedToSaveSegment);
                throw new SegmentCacheFailedToSaveSegmentException();
            }
        } catch (Throwable t) {
            LOGGER.error(
                    segmentCacheFailedToSaveSegment,
                t);
            throw new SegmentCacheFailedToSaveSegmentException(t);
        }
    }

    /**
     * Removes a segment from the cache.
     *
     * @param header A header to remove in the segment cache.
     * @return Whether a segment was removed
     */
    public boolean remove(SegmentHeader header) {
        checkThread();
        try {
            return cache.remove(header);
        } catch (Throwable t) {
            LOGGER.error(segmentCacheFailedToDeleteSegment,
                t);
            throw new OlapRuntimeException(segmentCacheFailedToDeleteSegment, t);
        }
    }

    /**
     * Returns a list of segments present in the cache.
     *
     * @return List of headers in the cache
     */
    public List<SegmentHeader> getSegmentHeaders() {
        checkThread();
        try {
            return cache.getSegmentHeaders();
        } catch (Throwable t) {
            LOGGER.error("Failed to get a list of segment headers.", t);
            throw new OlapRuntimeException(
                segmentCacheFailedToScanSegments, t);
        }
    }

    public boolean supportsRichIndex() {
        return supportsRichIndex;
    }

    public void shutdown() {
        checkThread();
        cache.tearDown();
    }

    private void checkThread() {
        assert cacheMgrThread != Thread.currentThread()
            : new StringBuilder("this method is potentially slow; you should not call it from ")
            .append("the cache manager thread, ").append(cacheMgrThread);
    }
}
