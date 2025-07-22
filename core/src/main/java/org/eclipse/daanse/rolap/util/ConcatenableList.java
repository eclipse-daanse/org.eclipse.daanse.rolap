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


package org.eclipse.daanse.rolap.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * List backed by a collection of sub-lists.
 *
 * @author Luis F. Canals
 * @since december, 2007
 */
public class ConcatenableList<T> extends AbstractList<T> {

    public static final String OUT_OF_CONCATENABLE_LIST_RANGE = " out of concatenable list range";
    public static final String INDEX = "Index ";
    private static int nextHashCode = 1000;

    // The backing collection of sublists
    private final List<List<T>> lists;

    // List containing all elements from backing lists, populated only after
    // consolidate()
    private List<T> plainList;
    private final int hashCode = nextHashCode++;
    private Iterator<T> getIterator = null;
    private int previousIndex = -200;
    private T previousElement = null;
    private T prePreviousElement = null;

    /**
     * Creates an empty ConcatenableList.
     */
    public ConcatenableList() {
        this.lists = new ArrayList<>();
        this.plainList = null;
    }

    @Override
	public <T2> T2[] toArray(T2[] a) {
        consolidate();
        //noinspection unchecked,SuspiciousToArrayCall
        return (T2[]) plainList.toArray((Object []) a);
    }

    @Override
	public Object[] toArray() {
        consolidate();
        return plainList.toArray();
    }

    /**
     * Performs a load of all elements into memory, removing sequential
     * access advantages.
     */
    public void consolidate() {
        if (this.plainList == null) {
            this.plainList = new ArrayList<>();
            for (final List<T> list : lists) {
                // REVIEW: List.addAll is probably more efficient.
                for (final T t : list) {
                    this.plainList.add(t);
                }
            }
        }
    }

    @Override
	public boolean addAll(final Collection<? extends T> collection) {
        if (this.plainList == null) {
            final List<T> list = (List<T>) collection;
            return this.lists.add(list);
        } else {
            for (final T e : collection) {
                this.plainList.add(e);
            }
            return true;
        }
    }

    @Override
	public T get(final int index) {
        if (this.plainList == null) {
            if (index == 0) {
                this.getIterator = this.iterator();
                this.previousIndex = index;
                if (this.getIterator.hasNext()) {
                    this.previousElement = this.getIterator.next();
                    return this.previousElement;
                } else {
                    this.getIterator = null;
                    this.previousIndex = -200;
                    throw new IndexOutOfBoundsException(
                        new StringBuilder(INDEX).append(index).append(OUT_OF_CONCATENABLE_LIST_RANGE).toString());
                }
            } else if (this.previousIndex + 1 == index
                && this.getIterator != null)
            {
                this.previousIndex = index;
                if (this.getIterator.hasNext()) {
                    this.prePreviousElement = this.previousElement;
                    this.previousElement = this.getIterator.next();
                    return this.previousElement;
                } else {
                    this.getIterator = null;
                    this.previousIndex = -200;
                    throw new IndexOutOfBoundsException(
                        new StringBuilder(INDEX).append(index)
                            .append(OUT_OF_CONCATENABLE_LIST_RANGE).toString());
                }
            } else if (this.previousIndex == index) {
                return this.previousElement;
            } else if (this.previousIndex - 1 == index) {
                return this.prePreviousElement;
            } else {
                this.previousIndex = -200;
                this.getIterator = null;
                final Iterator<T> it = this.iterator();
                if (!it.hasNext()) {
                    throw new IndexOutOfBoundsException(
                        new StringBuilder(INDEX).append(index).append(OUT_OF_CONCATENABLE_LIST_RANGE).toString());
                }
                for (int i = 0; i < index; i++) {
                    if (!it.hasNext()) {
                        throw new IndexOutOfBoundsException(
                            new StringBuilder(INDEX).append(index)
                            .append(OUT_OF_CONCATENABLE_LIST_RANGE).toString());
                    }
                    this.prePreviousElement = it.next();
                }
                this.previousElement = it.next();
                this.previousIndex = index;
                this.getIterator = it;
                return this.previousElement;
            }
        } else {
            this.previousElement = this.plainList.get(index);
            return this.previousElement;
        }
    }

    @Override
	public boolean add(final T t) {
        if (this.plainList == null) {
            return this.lists.add(Collections.singletonList(t));
        } else {
            return this.plainList.add(t);
        }
    }

    @Override
	public void add(final int index, final T t) {
        if (this.plainList == null) {
            throw new UnsupportedOperationException();
        } else {
            this.plainList.add(index, t);
        }
    }

    @Override
	public T set(final int index, final T t) {
        if (this.plainList == null) {
            throw new UnsupportedOperationException();
        } else {
            return this.plainList.set(index, t);
        }
    }

    @Override
	public int size() {
        if (this.plainList == null) {
            // REVIEW: Consider consolidating here. As it stands, this loop is
            // expensive if called often on a lot of small lists. Amortized cost
            // would be lower if we consolidated, or partially consolidated.
            int size = 0;
            for (final List<T> list : lists) {
                size += list.size();
            }
            return size;
        } else {
            return this.plainList.size();
        }
    }

    @Override
	public Iterator<T> iterator() {
        if (this.plainList == null) {
            return new Iterator<>() {
                private final Iterator<List<T>> listsIt = lists.iterator();
                private Iterator<T> currentListIt;

                @Override
				public boolean hasNext() {
                    if (currentListIt == null) {
                        if (listsIt.hasNext()) {
                            currentListIt = listsIt.next().iterator();
                        } else {
                            return false;
                        }
                    }

                    // If the current sub-list iterator has no next, grab the
                    // next sub-list's iterator, and continue until either a
                    // sub-list iterator with a next is found (at which point,
                    // the while loop terminates) or no more sub-lists exist (in
                    // which case, return false).
                    while (!currentListIt.hasNext()) {
                        if (listsIt.hasNext()) {
                            currentListIt = listsIt.next().iterator();
                        } else {
                            return false;
                        }
                    }
                    return currentListIt.hasNext();
                }

                @Override
				public T next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    } else {
                        return currentListIt.next();
                    }
                }

                @Override
				public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        } else {
            return this.plainList.iterator();
        }
    }

    @Override
	public boolean isEmpty() {
        if (this.plainList != null) {
            return this.plainList.isEmpty();
        }
        if (this.lists.isEmpty()) {
            return true;
        } else {
            for (final List<T> l : lists) {
                if (!l.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
	public void clear() {
        this.plainList = null;
        this.lists.clear();
    }

    @Override
	public int hashCode() {
        return this.hashCode;
    }
}
