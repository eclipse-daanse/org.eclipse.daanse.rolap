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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link java.util.List} where all methods throw
 * an UnsupportedOperationException exception except for the
 * isEmpty method. The iterator and
 * listIterator methods can be easily implemented in
 * derived classes by using the helper inner classes:
 * Itr and ListItr.
 * These iterators are all read only,
 * their remove, add and set
 * methods throw the
 * UnsupportedOperationException exception.
 * 
 * This class can be used for List implementations that only implement
 * a subset of all the methods.
 *
 * @author Richard Emberson
 * @since Jan 16, 2007
 */
public abstract class UnsupportedList<T> implements List<T> {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(UnsupportedList.class);

    protected UnsupportedList() {
    }

    @Override
	public boolean isEmpty() {
        return (size() == 0);
    }

    @Override
	public int size() {
        throw new UnsupportedOperationException(new StringBuilder(getClass().getName()).append(".size").toString());
    }

    @Override
	public T get(int index) {
        throw new UnsupportedOperationException(new StringBuilder(getClass().getName()).append(".get").toString());
    }

    @Override
	public T set(int index, T element) {
        throw new UnsupportedOperationException(new StringBuilder(getClass().getName()).append(".set").toString());
    }

    @Override
	public Object[] toArray() {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".toArray").toString());
    }

    @Override
	public void add(int index, T element) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".add").toString());
    }

    @Override
	public T remove(int index) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".remove").toString());
    }

    @Override
	public int indexOf(Object o) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".indexOf").toString());
    }

    @Override
	public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".lastIndexOf").toString());
    }

    @Override
	public List<T> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".subList").toString());
    }

    @Override
	public boolean contains(Object o) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".contains").toString());
    }

    @Override
	public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".toArray").toString());
    }

    @Override
	public boolean add(T o) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".add").toString());
    }

    @Override
	public boolean remove(Object o) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".remove").toString());
    }

    @Override
	public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".containsAll").toString());
    }

    @Override
	public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".addAll").toString());
    }

    @Override
	public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".addAll").toString());
    }

    @Override
	public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".removeAll").toString());
    }

    @Override
	public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".retainAll").toString());
    }

    @Override
	public void clear() {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".clear").toString());
    }

    @Override
	public boolean equals(Object o) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".equals").toString());
    }

    @Override
	public int hashCode() {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".hashCode").toString());
    }

    @Override
	public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".listIterator").toString());
    }

    @Override
	public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".listIterator").toString());
    }

    @Override
	public Iterator<T> iterator() {
        throw new UnsupportedOperationException(
            new StringBuilder(getClass().getName()).append(".iterator").toString());
    }



    protected class Itr implements Iterator<T> {
        protected int cursor;
        protected int lastRet;

        public Itr() {
            this.cursor = 0;
            this.lastRet = -1;
        }

        @Override
		public boolean hasNext() {
            return (cursor != size());
        }

        @Override
		public T next() {
            try {
                T next = get(cursor);
                lastRet = cursor++;
                return next;
            } catch (IndexOutOfBoundsException e) {
                LOGGER.error(
                    new StringBuilder("UnsupportedList.Itr.next: cursor=")
                        .append(cursor)
                        .append(", size=")
                        .append(size()).toString(), e);
                throw new NoSuchElementException();
            }
        }

        @Override
		public void remove() {
            throw new UnsupportedOperationException(
                new StringBuilder(getClass().getName()).append(".remove").toString());
        }
    }

    protected class ListItr extends Itr implements ListIterator<T> {
        public ListItr(int index) {
            this.cursor = index;
        }

        @Override
		public boolean hasPrevious() {
            return cursor != 0;
        }

        @Override
		public T previous() {
            try {
                int i = cursor - 1;
                T previous = get(i);
                lastRet = cursor = i;
                return previous;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }

        @Override
		public int nextIndex() {
            return cursor;
        }

        @Override
		public int previousIndex() {
            return cursor - 1;
        }

        @Override
		public void set(T o) {
/*
            if (lastRet == -1)
                throw new IllegalStateException();
            try {
                MemberList.this.set(lastRet, o);
            } catch (IndexOutOfBoundsException e) {
                throw new ConcurrentModificationException();
            }
*/
            throw new UnsupportedOperationException(
                new StringBuilder(getClass().getName()).append(".set").toString());
        }

        @Override
		public void add(T o) {
            throw new UnsupportedOperationException(
                new StringBuilder(getClass().getName()).append(".add").toString());
        }
    }

    /**
     * Iterator for arrays of a priori unknown size.
     */
    protected class ItrUnknownSize extends Itr {
        public ItrUnknownSize() {
            super();
        }

        @Override
		public boolean hasNext() {
            try {
                get(cursor);
                return true;
            } catch (IndexOutOfBoundsException e) {
                return false;
            }
        }
    }
}

// End UnsupportedList.java

