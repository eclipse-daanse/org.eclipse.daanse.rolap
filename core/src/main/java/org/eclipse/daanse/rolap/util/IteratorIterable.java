/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (C) 2005-2005 Julian Hyde
* Copyright (C) 2005-2017 Hitachi Vantara
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

package org.eclipse.daanse.rolap.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterable over an iterator.
 *
 * It can be restarted. As you iterate, it stores elements in a backing
 * array. If you call {@link #iterator()} again, it will first replay elements
 * from that array.
 */
public class IteratorIterable<E> implements Iterable<E> {
    private final List<E> list = new ArrayList<>();
    private final Iterator<E> recordingIterator;

    /** Creates an IteratorIterable. */
    public IteratorIterable(final Iterator<E> iterator) {
        this.recordingIterator =
            new Iterator<>() {
                @Override
				public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
				public E next() {
                    final E e = iterator.next();
                    list.add(e);
                    return e;
                }

                @Override
				public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
    }

    @Override
	public Iterator<E> iterator() {
        // Return an iterator over the union of (1) the list, (2) the rest
        // of the iterator. The second part writes elements to the list as
        // it returns them.
        //noinspection unchecked
        return Composite.of(
            // Can't use ArrayList.iterator(). It throws
            // ConcurrentModificationException, because the list is growing
            // under its feet.
            new Iterator<E>() {
                int i = 0;

                @Override
				public boolean hasNext() {
                    return i < list.size();
                }

                @Override
				public E next() {
                    if(!hasNext()){
                        throw new NoSuchElementException();
                    }
                    return list.get(i++);
                }

                @Override
				public void remove() {
                    throw new UnsupportedOperationException();
                }
            },
            recordingIterator);
    }
}
