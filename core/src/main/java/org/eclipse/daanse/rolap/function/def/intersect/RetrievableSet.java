/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.function.def.intersect;

import java.util.Set;

/**
 * Interface similar to the Set interface that allows key values to be
 * returned.
 *
 * Useful if multiple objects can compare equal (using
 * {@link Object#equals(Object)} and {@link Object#hashCode()}, per the
 * set contract) and you wish to distinguish them after they have been added
 * to the set.
 *
 * @param <E> element type
 */
public interface RetrievableSet<E> {
    /**
     * Returns the key in this set that compares equal to a given object,
     * or null if there is no such key.
     *
     * @param e Key value
     * @return Key in the set equal to given key value
     */
    E getKey(E e);

    /**
     * Analogous to {@link Set#add(Object)}.
     *
     * @param e element to be added to this set
     * @return true if this set did not already contain the
     *         specified element
     */
    boolean add(E e);
}