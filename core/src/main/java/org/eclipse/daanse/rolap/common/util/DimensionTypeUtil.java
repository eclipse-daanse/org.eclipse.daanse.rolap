/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.util;

import org.eclipse.daanse.olap.api.element.DimensionType;

public class DimensionTypeUtil {

    private DimensionTypeUtil() {
        // constructor
    }

    // Return the dimension's enumerated type.
    public static DimensionType getDimensionType(org.eclipse.daanse.rolap.mapping.model.Dimension dimension) {
        if (dimension instanceof org.eclipse.daanse.rolap.mapping.model.StandardDimension) {
            return DimensionType.STANDARD_DIMENSION;
        }
        if (dimension instanceof org.eclipse.daanse.rolap.mapping.model.TimeDimension) {
        	return DimensionType.TIME_DIMENSION;
        }
        return null;
    }

}
