/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.rolap.common;

public class RolapOrderedColumn extends RolapSqlExpression {

    private RolapColumn column;
    private boolean ascend;


	public RolapOrderedColumn(RolapColumn column, boolean ascend) {
        this.column = column;
        this.ascend = ascend;
    }

    public RolapColumn getColumn() {
        return column;
    }


    public void setColumn(RolapColumn column) {
        this.column = column;
    }


    public boolean isAscend() {
        return ascend;
    }


    public void setAscend(boolean ascend) {
        this.ascend = ascend;
    }
}
