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

import java.util.HashMap;

public class RetrievableHashSet<E> extends HashMap<E, E> implements RetrievableSet<E> {
    public RetrievableHashSet(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public E getKey(E e) {
        return super.get(e);
    }

    @Override
    public boolean add(E e) {
        return super.put(e, e) == null;
    }
}