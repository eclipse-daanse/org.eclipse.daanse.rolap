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
 */
package org.eclipse.daanse.rolap.testkit.assertions;

import org.eclipse.daanse.olap.api.relational.RelationalCell;
import org.eclipse.daanse.olap.api.result.Cell;

/** Reaches the relational side of a result cell in tests. */
public final class Cells {

    private Cells() {
    }

    /** The drill-through SQL of the cell; fails when the cell is not relational. */
    public static String drillThroughSql(Cell cell, boolean extendedContext) {
        if (cell instanceof RelationalCell relational) {
            return relational.getDrillThroughSQL(extendedContext);
        }
        throw new AssertionError("Cell is not relational: " + cell.getClass().getName());
    }
}
