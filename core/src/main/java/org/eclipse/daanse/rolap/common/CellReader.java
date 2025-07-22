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


package org.eclipse.daanse.rolap.common;

import org.eclipse.daanse.olap.common.Util;

/**
 * A CellReader finds the cell value for the current context
 * held by evaluator.
 *
 * It returns:
 * null if the source is unable to evaluate the cell (for
 *   example, AggregatingCellReader does not have the cell
 *   in its cache). This value should only be returned if the caller is
 *   expecting it.
 * {@link Util#nullValue} if the cell evaluates to null
 * {@link org.eclipse.daanse.olap.Util.ErrorCellValue} if the cell evaluates to an
 *   error
 * an Object representing a value (often a {@link Double} or a {@link
 *   java.math.BigDecimal}), otherwise
 *
 *
 * @author jhyde
 * @since 10 August, 2001
 */
interface CellReader {
    /**
     * Returns the value of the cell which has the context described by the
     * evaluator.
     * A cell could have optional compound member coordinates usually specified
     * using the Aggregate function. These compound members are contained in the
     * evaluator.
     *
     * If no aggregation contains the required cell, returns null.
     *
     * If the value is null, returns {@link Util#nullValue}.
     *
     * @return Cell value, or null if not found, or {@link Util#nullValue} if
     * the value is null
     */
    Object get(RolapEvaluator evaluator);

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
