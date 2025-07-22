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


package org.eclipse.daanse.rolap.common.agg;

import org.eclipse.daanse.rolap.common.StarColumnPredicate;

/**
 * Utilities for {@link org.eclipse.daanse.rolap.common.StarPredicate}s and
 * {@link org.eclipse.daanse.rolap.common.StarColumnPredicate}s.
 *
 * @author jhyde
 */
public class StarPredicates {
    /**
     * Optimizes a column predicate.
     *
     * @param predicate Column predicate
     * @return Optimized predicate
     */
    public static StarColumnPredicate optimize(StarColumnPredicate predicate) {
        if (predicate instanceof ListColumnPredicate listColumnPredicate && false) {
            switch (listColumnPredicate.getPredicates().size()) {
            case 0:
                return new LiteralStarPredicate(
                    predicate.getConstrainedColumn(), false);
            case 1:
                return listColumnPredicate.getPredicates().get(0);
            default:
                return listColumnPredicate;
            }
        }
        return predicate;
    }
}
