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


package org.eclipse.daanse.rolap.common.cache;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.olap.spi.SegmentHeader;

/**
 * Implementation of org.eclipse.daanse.olap.spi.SegmentCache that stores segments
 * in memory.
 *
 * Segments are held via soft references, so the garbage collector can remove
 * them if it sees fit.
 *
 * @author Julian Hyde
 */
public class MemorySegmentCache implements SegmentCache {
    // Use a thread-safe map because the SegmentCache
    // interface requires thread safety.
    private final Map<SegmentHeader, SoftReference<SegmentBody>> map =
        new ConcurrentHashMap<>();
    private final List<SegmentCacheListener> listeners =
        new CopyOnWriteArrayList<>();

    @Override
	public SegmentBody get(SegmentHeader header) {
        final SoftReference<SegmentBody> ref = map.get(header);
        if (ref == null) {
            return null;
        }
        final SegmentBody body = ref.get();
        if (body == null) {
            map.remove(header);
        }
        return body;
    }

    public boolean contains(SegmentHeader header) {
        final SoftReference<SegmentBody> ref = map.get(header);
        if (ref == null) {
            return false;
        }
        final SegmentBody body = ref.get();
        if (body == null) {
            map.remove(header);
            return false;
        }
        return true;
    }

    @Override
	public List<SegmentHeader> getSegmentHeaders() {
        return new ArrayList<>(map.keySet());
    }

    @Override
	public boolean put(final SegmentHeader header, SegmentBody body) {
        // REVIEW: What's the difference between returning false
        // and throwing an exception?
        assert header != null;
        assert body != null;
        map.put(header, new SoftReference<>(body));
        fireSegmentCacheEvent(
            new SegmentCache.SegmentCacheListener.SegmentCacheEvent() {
                @Override
				public boolean isLocal() {
                    return true;
                }
                @Override
				public SegmentHeader getSource() {
                    return header;
                }
                @Override
				public EventType getEventType() {
                    return SegmentCacheListener.SegmentCacheEvent
                        .EventType.ENTRY_CREATED;
                }
            });
        return true; // success
    }

    @Override
	public boolean remove(final SegmentHeader header) {
        final boolean result =
            map.remove(header) != null;
        if (result) {
            fireSegmentCacheEvent(
                new SegmentCache.SegmentCacheListener.SegmentCacheEvent() {
                    @Override
					public boolean isLocal() {
                        return true;
                    }
                    @Override
					public SegmentHeader getSource() {
                        return header;
                    }
                    @Override
					public EventType getEventType() {
                        return
                            SegmentCacheListener.SegmentCacheEvent
                                .EventType.ENTRY_DELETED;
                    }
                });
        }
        return result;
    }

    @Override
	public void tearDown() {
        map.clear();
        listeners.clear();
    }

    @Override
	public void addListener(SegmentCacheListener listener) {
        listeners.add(listener);
    }

    @Override
	public void removeListener(SegmentCacheListener listener) {
        listeners.remove(listener);
    }

    @Override
	public boolean supportsRichIndex() {
        return true;
    }

    public void fireSegmentCacheEvent(
        SegmentCache.SegmentCacheListener.SegmentCacheEvent evt)
    {
        for (SegmentCacheListener listener : listeners) {
            listener.handle(evt);
        }
    }
}
