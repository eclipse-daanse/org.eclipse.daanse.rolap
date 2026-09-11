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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.common.star.HierarchyUsage;
import org.eclipse.daanse.rolap.common.util.DimensionUtil;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapDimension;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * {@code DimensionConnector.overrideDimensionName} is optional; without it the
 * connector is named after the dimension it connects.
 *
 * <p>Regression guard for the NPE in {@code RolapCube.getUsageByName}: it needs
 * an absent override AND a hierarchy carrying the dimension's name, and hides
 * behind {@code isDebugEnabled()} in {@code lookupChild}. The assertions
 * therefore check the invariant, not the log path.
 */
@RolapContextTest(OverrideDimensionNameOptionalInstance.class)
class OverrideDimensionNameOptionalTest {

    @Test
    void connectorWithoutOverrideIsNamedAfterItsDimension() {
        Catalog catalog = new OverrideDimensionNameOptionalInstance().mappingSupplier().get();
        PhysicalCube cube = cubeMapping(catalog);

        for (DimensionConnector connector : cube.getDimensionConnectors()) {
            assertThat(connector.getOverrideDimensionName())
                    .as("fixture keeps the optional override unset")
                    .isNull();
            assertThat(RolapDimension.getDimensionName(connector))
                    .isEqualTo(connector.getDimension().getName());
        }
    }

    @Test
    void dimensionIsFoundByItsDerivedName() {
        Catalog catalog = new OverrideDimensionNameOptionalInstance().mappingSupplier().get();
        PhysicalCube cube = cubeMapping(catalog);

        assertThat(DimensionUtil.getDimension(cube, catalog, OverrideDimensionNameOptionalInstance.STORE).getName())
                .isEqualTo(OverrideDimensionNameOptionalInstance.STORE);
        assertThat(DimensionUtil.getDimension(cube, catalog, OverrideDimensionNameOptionalInstance.PRODUCT).getName())
                .isEqualTo(OverrideDimensionNameOptionalInstance.PRODUCT);
    }

    @Test
    void everyHierarchyUsageHasAFullName(Connection connection) {
        RolapCube cube = cube(connection);

        for (Hierarchy hierarchy : hierarchies(cube)) {
            HierarchyUsage[] usages = cube.getUsages(hierarchy);
            assertThat(usages).as("usages of %s", hierarchy.getUniqueName()).isNotEmpty();
            for (HierarchyUsage usage : usages) {
                assertThat(usage.getFullName())
                        .as("fullName of the usage of %s", hierarchy.getUniqueName())
                        .isNotNull();
                assertThat(usage.getName())
                        .as("name of the usage of %s", hierarchy.getUniqueName())
                        .isEqualTo(hierarchy.getDimension().getName());
            }
        }
    }

    @Test
    void everyHierarchyCanBePlacedOnAnAxis(Connection connection) {
        RolapCube cube = cube(connection);

        for (Hierarchy hierarchy : hierarchies(cube)) {
            String mdx = "SELECT {[Measures].[" + OverrideDimensionNameOptionalInstance.MEASURE_NAME
                    + "]} ON COLUMNS, " + hierarchy.getUniqueName() + ".Members ON ROWS FROM ["
                    + OverrideDimensionNameOptionalInstance.CUBE_NAME + "]";

            Result result = connection.execute(connection.parseQuery(mdx));

            assertThat(result.getAxes()[1].getPositions())
                    .as("rows of %s", hierarchy.getUniqueName())
                    .isNotEmpty();
        }
    }

    private static PhysicalCube cubeMapping(Catalog catalog) {
        return catalog.getOwnedElement().stream()
                .filter(PhysicalCube.class::isInstance)
                .map(PhysicalCube.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static RolapCube cube(Connection connection) {
        return (RolapCube) connection.getCatalog()
                .lookupCube(OverrideDimensionNameOptionalInstance.CUBE_NAME)
                .orElseThrow();
    }

    private static List<Hierarchy> hierarchies(RolapCube cube) {
        return cube.getHierarchies().stream()
                .filter(hierarchy -> !hierarchy.getDimension().isMeasures())
                .toList();
    }
}
