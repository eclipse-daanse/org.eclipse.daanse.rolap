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

public enum Updateable {
    MD_MASK_ENABLED (0x00000000), //The cell can be updated.
    MD_MASK_NOT_ENABLED (0x10000000), //The cell cannot be updated.
    CELL_UPDATE_ENABLED (0x00000001), //Cell can be updated in the cellset.
    CELL_UPDATE_ENABLED_WITH_UPDATE (0x00000002), //The cell can be updated with an update statement. The update may fail if a leaf cell is updated that is not write-enabled.
    CELL_UPDATE_NOT_ENABLED_FORMULA (0x10000001), //The cell cannot be updated because the cell has a calculated member among its coordinates; the cell was retrieved with a set in the where clause. A cell can be updated even though a formula affects, or a calculated cell is on, the value of a cell (is somewhere along the aggregation path). In this scenario, the final value of the cell may not be the updated value, because the calculation will affect the result
    CELL_UPDATE_NOT_ENABLED_NONSUM_MEASURE (0x10000002), //The cell cannot be updated because non-sum measures (count, min, max, distinct count, semi-additive) can not be updated.
    CELL_UPDATE_NOT_ENABLED_NACELL_VIRTUALCUBE (0x10000003), //The cell cannot be updated because the cell does not exist as it is at the intersection of a measure and a dimension member unrelated to the measure's measure group.
    CELL_UPDATE_NOT_ENABLED_SECURE (0x10000005), //The cell cannot be updated because the cell is secured.
    CELL_UPDATE_NOT_ENABLED_CALCLEVEL (0x10000006), //Reserved for future use.
    CELL_UPDATE_NOT_ENABLED_CANNOTUPDATE (0x10000007), //The cell cannot be updated because of internal reasons.
    CELL_UPDATE_NOT_ENABLED_INVALIDDIMENSIONTYPE (0x10000009); //The cell cannot be updated because update is not supported in mining model, indirect, or data mining dimensions.

    private final int updateable;

    Updateable(int updateable) {
        this.updateable = updateable;
    }

    public int getUpdateable() {
         return updateable;
    }
}
