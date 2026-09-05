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

package org.eclipse.daanse.rolap.agg;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.olap.spi.SegmentHeader;

/**
 * Mock implementation of {@link SegmentCache} that is used for automated
 * testing.
 *
 * <P>It tries to marshall / unmarshall all {@link SegmentHeader} and
 * {@link SegmentBody} objects that are sent to it.
 *
 * @author LBoudreau
 */
public class MockSegmentCache implements SegmentCache {
    private static final Map<SegmentHeader, SegmentBody> cache =
        new ConcurrentHashMap<>();

    private final List<SegmentCacheListener> listeners =
        new CopyOnWriteArrayList<>();

    private Random rnd;

    private static final int maxElements = 100;

    public boolean contains(SegmentHeader header) {
        return cache.containsKey(header);
    }

    @Override
	public SegmentBody get(SegmentHeader header) {
        return cache.get(header);
    }

    @Override
	public boolean put(
        final SegmentHeader header,
        final SegmentBody body)
    {
        // Try to serialize back and forth. if the tests fail because of this,
        // then the objects could not be serialized properly.
        // First try with the header
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(out);
            oos.writeObject(header);
            oos.close();
            // deserialize
            byte[] pickled = out.toByteArray();
            InputStream in = new ByteArrayInputStream(pickled);
            ObjectInputStream ois = new ObjectInputStream(in);
            SegmentHeader o = (SegmentHeader) ois.readObject();
//            discard(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Now try it with the body.
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(out);
            oos.writeObject(body);
            oos.close();
            // deserialize
            byte[] pickled = out.toByteArray();
            InputStream in = new ByteArrayInputStream(pickled);
            ObjectInputStream ois = new ObjectInputStream(in);
            SegmentBody o = (SegmentBody) ois.readObject();
//            discard(o);
        } catch (NotSerializableException e) {
            throw new RuntimeException(
                "while serializing " + body,
                e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        cache.put(header, body);
        fireSegmentCacheEvent(
            new SegmentCacheListener.SegmentCacheEvent()
            {
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
                        EventType.ENTRY_CREATED;
                }
            });
        if (cache.size() > maxElements) {
            // Cache is full. pop one out at random.
            if (rnd == null) {
                rnd = new Random();
            }
            int index = rnd.nextInt(maxElements);
            for (Iterator<SegmentHeader> iterator = cache.keySet().iterator();
                 iterator.hasNext();)
            {
            	iterator.next();
//                discard(iterator.next());
                if (index-- == 0) {
                    iterator.remove();
                    break;
                }
            }
            fireSegmentCacheEvent(
                new SegmentCacheListener.SegmentCacheEvent()
                {
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
                            EventType.ENTRY_DELETED;
                    }
                });
        }
        return true;
    }

    @Override
	public List<SegmentHeader> getSegmentHeaders() {
        return new ArrayList<>(cache.keySet());
    }

    @Override
	public boolean remove(final SegmentHeader header) {
        cache.remove(header);
        fireSegmentCacheEvent(
            new SegmentCacheListener.SegmentCacheEvent()
            {
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
                        EventType.ENTRY_DELETED;
                }
            });
        return true;
    }

    @Override
	public void tearDown() {
        listeners.clear();
        cache.clear();
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
        SegmentCacheListener.SegmentCacheEvent event)
    {
        for (SegmentCacheListener listener : listeners) {
            listener.handle(event);
        }
    }
}
