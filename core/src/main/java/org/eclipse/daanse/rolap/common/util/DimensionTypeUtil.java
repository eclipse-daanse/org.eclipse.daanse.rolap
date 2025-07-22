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
import org.eclipse.daanse.rolap.mapping.api.model.DimensionMapping;
import org.eclipse.daanse.rolap.mapping.api.model.StandardDimensionMapping;
import org.eclipse.daanse.rolap.mapping.api.model.TimeDimensionMapping;

public class DimensionTypeUtil {

    private DimensionTypeUtil() {
        // constructor
    }

    // Return the dimension's enumerated type.
    public static DimensionType getDimensionType(DimensionMapping dimension) {
        if (dimension instanceof StandardDimensionMapping) {
            return DimensionType.STANDARD_DIMENSION;
        }
        if (dimension instanceof TimeDimensionMapping) {
        	return DimensionType.TIME_DIMENSION;
        }
        return null;
    }

}
