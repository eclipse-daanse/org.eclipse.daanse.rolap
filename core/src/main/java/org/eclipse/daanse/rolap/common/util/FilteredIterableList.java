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


package org.eclipse.daanse.rolap.common.util;

import java.util.AbstractSequentialList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Iterable list which filters undesirable elements.
 * To be used instead of removing elements from an iterable list.
 *
 * @author Luis F. Canals
 * @since december, 2007
 */
public class FilteredIterableList<T> extends AbstractSequentialList<T> {
    private List<T> plainList;
    private int size;
    private boolean isEmpty;
    private int lastIndex = 0;
    private int lastGetIndex = -1;
    private T lastGet = null;
    private ListIterator<T> lastListIterator;

    private final List<? extends T> internal;
    private final Predicate<T> filter;

    private final Cache<Integer, T> cached;

    public FilteredIterableList(
        final List<? extends T> list,
        final Predicate<T> filter)
    {
        super();
        this.plainList = null;
        this.filter = filter;
        this.internal = list;
        this.isEmpty = ! this.listIterator(0).hasNext();
        this.size = -1;
        this.cached = Caffeine.newBuilder().maximumSize(4).build();
    }

    @Override
	public T get(final int index) {
        if (this.plainList != null) {
            return this.plainList.get(index);
        }

        final T t = cached.getIfPresent(index);
        if (t != null) {
            return t;
        } else {
            if (index != this.lastGetIndex || index < 0) {
                // super.get names a position out of range itself; a null that passed the filter is
                // an element like any other, not an absence.
                this.lastGet = super.get(index);
                this.lastGetIndex = index;
            }
            if (this.lastGet != null) {
                cached.put(index, this.lastGet);
            }
            return this.lastGet;
        }
    }

    @Override
	public ListIterator<T> listIterator(final int index) {
        if (this.plainList == null) {
            // The kept walk serves only where it actually stands, and never as a walk of its own
            // from the front: two walkers must not be the same walker.
            if (this.lastListIterator != null
                && (index == 0 || this.lastListIterator.nextIndex() > index))
            {
                this.lastListIterator = null;
            }
            if (this.lastListIterator == null) {
                final Iterator<? extends T> it = internal.iterator();
                T nextTmp = null;
                boolean found = false;
                while (it.hasNext()) {
                    final T n = it.next();
                    if (filter.test(n)) {
                        nextTmp = n;
                        found = true;
                        break;
                    }
                }
                final T first = nextTmp;
                final boolean firstFound = found;
                this.lastListIterator = new ListIterator<>() {
                    private int idx = 0;
                    private T nxt = first;
                    private boolean hasNxt = firstFound;
                    private void postNext() {
                        while (it.hasNext()) {
                            final T n = it.next();
                            if (filter.test(n)) {
                                nxt = n;
                                hasNxt = true;
                                return;
                            }
                        }
                        nxt = null;
                        hasNxt = false;
                    }
                    @Override
					public boolean hasNext() {
                        // Whether there is one, not whether it is something: a null passed the filter.
                        return hasNxt;
                    }
                    @Override
					public T next() {
                        if(!hasNext()){
                            throw new NoSuchElementException();
                        }
                        idx++;
                        final T n = nxt;
                        if (n != null) {
                            cached.put(idx - 1, n);
                        }
                        postNext();
                        return n;
                    }
                    @Override
					public int nextIndex() {
                        return idx;
                    }
                    @Override
					public void add(final T t) {
                        throw new UnsupportedOperationException();
                    }
                    @Override
					public void set(final T t) {
                        throw new UnsupportedOperationException();
                    }
                    @Override
					public boolean hasPrevious() {
                        throw new UnsupportedOperationException();
                    }
                    @Override
					public T previous() {
                        throw new UnsupportedOperationException();
                    }
                    @Override
					public int previousIndex() {
                        throw new UnsupportedOperationException();
                    }
                    @Override
					public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            for (int i = this.lastListIterator.nextIndex(); i < index; i++) {
                if (!this.lastListIterator.hasNext()) {
                    throw new IndexOutOfBoundsException();
                }
                this.lastListIterator.next();
            }
            this.lastIndex = index;
            return this.lastListIterator;
        } else {
            return plainList.listIterator(index);
        }
    }

    @Override
	public boolean isEmpty() {
        return this.plainList != null ? this.plainList.isEmpty() : this.isEmpty;
    }

    @Override
	public int size() {
        if (this.size == -1) {
            int s = this.lastIndex;
            try {
                final ListIterator<T> it = this.listIterator(this.lastIndex);
                while (it.hasNext()) {
                    s++;
                    it.next();
                }
            } catch (IndexOutOfBoundsException ioobe) {
                // Subyacent list is no more present...
            }
            this.size = s;
        }
        this.lastListIterator = null;
        return this.size;
    }

    @Override
	public Object[] toArray() {
        ensurePlainList();
        return this.plainList.toArray();
    }

    @Override
    public <T> T[] toArray(T[] contents) {
        ensurePlainList();
        return this.plainList.toArray(contents);
    }

    private void ensurePlainList() {
        if (this.plainList == null) {
            final List<T> tmpPlainList = new ArrayList<>();
            for (final Iterator<T> it = this.listIterator(0); it.hasNext();) {
                tmpPlainList.add(it.next());
            }
            this.plainList = tmpPlainList;
        }
    }


/*
    public List<T> subList(final int start, final int end) {
        return new AbstractList<T>() {
            public T get(final int index) {
                return FilteredIterableList.this.get(index-start);
            }
            public int size() {
                return FilteredIterableList.this.size() - start;
            }
        };
    }
*/



}
