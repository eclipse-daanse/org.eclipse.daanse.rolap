/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * jhyde, 10 August, 2001
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


package org.eclipse.daanse.rolap.common.result;

import org.eclipse.daanse.olap.api.result.CellValue;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;

/**
 * A CellReader finds the cell value for the current context
 * held by evaluator.
 *
 * It returns a non-null {@link CellValue} state:
 * {@link org.eclipse.daanse.olap.api.result.NullValue#INSTANCE} if the cell
 *   evaluates to MDX NULL (also used when the reader cannot evaluate the
 *   cell at all, e.g. the request is unsatisfiable or, for the aggregating
 *   reader, no aggregation contains the cell)
 * {@link org.eclipse.daanse.olap.api.result.NotLoaded#INSTANCE} if the cell
 *   is not in the cache yet (batching pass; the evaluation is marked dirty
 *   and repeated after the batch load)
 * {@link org.eclipse.daanse.olap.api.result.ErrorValue} if the cell
 *   evaluates to an error
 * an {@link org.eclipse.daanse.olap.api.result.ObjectValue} carrying the
 *   value (often a {@link Double} or a {@link java.math.BigDecimal}),
 *   otherwise
 *
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public interface CellReader {
    /**
     * Returns the value of the cell which has the context described by the
     * evaluator.
     * A cell could have optional compound member coordinates usually specified
     * using the Aggregate function. These compound members are contained in the
     * evaluator.
     *
     * @return Cell value state; never Java null
 */
    CellValue get(RolapEvaluator evaluator);

    /**
     * Returns the number of times this cell reader has told a lie
     * (since creation), because the required cell value is not in the
     * cache.
 */
    int getMissCount();

    /**
     * @return whether thus cell reader has any pending cell requests that are
     * not loaded yet.
 */
    boolean isDirty();
}
