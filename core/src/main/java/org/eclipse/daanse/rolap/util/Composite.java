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

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Composite collections.
 *
 * @author jhyde
 */
public abstract class Composite {

    private Composite() {
        // constructor
    }



    /**
     * Creates a composite iterable, inferring the element type from the
     * arguments.
     *
     * @param iterables One or more iterables
     * @param <T> element type
     * @return composite iterable
     */
    public static <T> Iterable<T> of(
        Iterable<? extends T>... iterables)
    {
        return new CompositeIterable<>(iterables);
    }

    /**
     * Creates a composite list, inferring the element type from the arguments.
     *
     * @param iterators One or more iterators
     * @param <T> element type
     * @return composite list
     */
    public static <T> Iterator<T> of(
        Iterator<? extends T>... iterators)
    {
        final Iterator[] iterators1 = iterators;
        return new CompositeIterator<>(iterators1);
    }

    private static class CompositeIterable<T> implements Iterable<T> {
        private final Iterable<? extends T>[] iterables;

        public CompositeIterable(Iterable<? extends T>[] iterables) {
            this.iterables = iterables;
        }

        @Override
		public Iterator<T> iterator() {
            return new CompositeIterator(iterables);
        }
    }

    private static class CompositeIterator<T> implements Iterator<T> {
        private final Iterator<Iterator<T>> iteratorIterator;
        private boolean hasNext;
        private T next;
        private Iterator<T> iterator;

        public CompositeIterator(Iterator<T>[] iterables) {
            this.iteratorIterator = Arrays.asList(iterables).iterator();
            this.iterator = Collections.<T>emptyList().iterator();
            this.hasNext = true;
            advance();
        }

        public CompositeIterator(final Iterable<T>[] iterables) {
            this.iteratorIterator =
                new IterableIterator<>(iterables);
            this.iterator = Collections.<T>emptyList().iterator();
            this.hasNext = true;
            advance();
        }

        private void advance() {
            for (;;) {
                if (iterator.hasNext()) {
                    next = iterator.next();
                    return;
                }
                if (!iteratorIterator.hasNext()) {
                    hasNext = false;
                    break;
                }
                iterator = iteratorIterator.next();
            }
        }

        @Override
		public boolean hasNext() {
            return hasNext;
        }

        @Override
		public T next() {
            if(!hasNext()){
                throw new NoSuchElementException();
            }
            final T next1 = next;
            advance();
            return next1;
        }

        @Override
		public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class IterableIterator<T>
        implements Iterator<Iterator<T>>
    {
        private int i;
        private final Iterable<T>[] iterables;

        public IterableIterator(Iterable<T>[] iterables) {
            this.iterables = iterables;
            i = 0;
        }

        @Override
		public boolean hasNext() {
            return i < iterables.length;
        }

        @Override
		public Iterator<T> next() {
            if(!hasNext()){
                throw new NoSuchElementException();
            }
            return iterables[i++].iterator();
        }

        @Override
		public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
