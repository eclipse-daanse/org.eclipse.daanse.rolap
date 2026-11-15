/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.updateable;

import java.util.Set;

import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.rolap.aggregator.SumAggregator;
import org.eclipse.daanse.rolap.aggregator.extra.ListAggAggregator;

/**
 * Which aggregators add their cells up, so that a value written above them can be spread over them.
 * <p>
 * Summing does, and so does collecting into a list, because both can be taken apart again. Counting
 * distinct things, taking a minimum or averaging cannot: there is no share of the result that belongs to
 * one cell.
 */
public final class SumLikeAggregators {

    private static final Set<Class<?>> ADDING = Set.of(SumAggregator.class, ListAggAggregator.class);

    private SumLikeAggregators() {
        // static access only
    }

    public static boolean contains(Aggregator aggregator) {
        return aggregator != null && ADDING.contains(aggregator.getClass());
    }
}
