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

import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.mapping.api.model.CatalogMapping;
import org.eclipse.daanse.rolap.mapping.api.model.CubeMapping;
import org.eclipse.daanse.rolap.mapping.api.model.DimensionConnectorMapping;
import org.eclipse.daanse.rolap.mapping.api.model.DimensionMapping;

public class DimensionUtil {

    public static final String SCHEMA_NULL = "schema != null";

    private DimensionUtil() {
        // constructor
    }

    public static DimensionMapping getDimension(CatalogMapping schema, DimensionConnectorMapping dimension) {
    	/*
        if (dimension instanceof MappingDimensionUsage dimensionUsage) {
            Util.assertPrecondition(schema != null, SCHEMA_NULL);
            for (int i = 0; i < schema.dimensions().size(); i++) {
                if (schema.dimensions().get(i).name().equals(dimensionUsage.source())) {
                    return schema.dimensions().get(i);
                }
            }
            throw Util.newInternal(
                new StringBuilder("Cannot find shared dimension '")
                    .append(dimensionUsage.source()).append("'").toString());
        }
        */
        //if (dimension instanceof DimensionMapping privateDimension) {
            Util.assertPrecondition(schema != null, SCHEMA_NULL);
            return dimension.getDimension();
        //}
        /*    
        if (dimension instanceof DimensionMapping virtualCubeDimension) {
            Util.assertPrecondition(schema != null, SCHEMA_NULL);
            if (virtualCubeDimension.cubeName() == null) {
                return getPublicDimension(schema, dimension.getName());
            } else {
                MappingCube cube = getCube(schema, virtualCubeDimension.cubeName());
                return getDimension(cube, schema, dimension.getName());
            }
        }
        throw new RolapRuntimeException("getDimension error");
        */
    }

    /*
    private static MappingDimension  getPublicDimension(CatalogMapping schema, String dimensionName) {
        for (int i = 0; i < schema.dimensions().size(); i++) {
            if (schema.dimensions().get(i).name().equals(dimensionName)) {
                return schema.dimensions().get(i);
            }
        }
        throw Util.newInternal(
            new StringBuilder("Cannot find public dimension '").append(dimensionName).append("'").toString());
    }
    */


//    private static CubeMapping getCube(CatalogMapping schema, String cubeName) {
//        for (int i = 0; i < schema.getCubes().size(); i++) {
//            if (schema.getCubes().get(i).getName().equals(cubeName)) {
//                return schema.getCubes().get(i);
//            }
//        }
//        throw Util.newInternal(new StringBuilder("Cannot find cube '").append(cubeName).append("'").toString());
//    }

    public static DimensionMapping getDimension(CubeMapping cube, CatalogMapping schema, String dimensionName) {
        for (int i = 0; i < cube.getDimensionConnectors().size(); i++) {
            if (cube.getDimensionConnectors().get(i).getOverrideDimensionName().equals(dimensionName)) {
                return getDimension(schema, cube.getDimensionConnectors().get(i));
            }
        }
        throw Util.newInternal(
            new StringBuilder("Cannot find dimension '").append(dimensionName).append("' in cube '")
                .append(cube.getName()).append("'").toString());

    }
}
