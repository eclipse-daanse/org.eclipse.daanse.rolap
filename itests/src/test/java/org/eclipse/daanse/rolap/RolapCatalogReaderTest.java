/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2004-2005 Julian Hyde
// Copyright (C) 2005-2017 Hitachi Vantara and others
// All Rights Reserved.
*/

package org.eclipse.daanse.rolap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessCatalogGrant;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessRole;
import org.eclipse.daanse.rolap.mapping.model.access.common.CatalogAccess;
import org.eclipse.daanse.rolap.mapping.model.access.common.CommonFactory;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessCubeGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessDimensionGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessHierarchyGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.CubeAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.DimensionAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.HierarchyAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.OlapFactory;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.Cube;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.Dimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.Hierarchy;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link CatalogReader}.
 *
 * <p>Context/connections come from the class-level {@code @RolapContextTest}
 * annotation; roles are declared via {@code @Roles}; the REG1 catalog rebuild
 * is a {@code catalog} composition on the test method.
 */
@RolapContextTest(FoodmartTestInstance.class)
class RolapCatalogReaderTest {

    @Test
    void testGetCubesWithNoHrCubes(@Roles("No HR Cube") Connection connection) {
        String[] expectedCubes = new String[] {
                "Sales", "Warehouse", "Warehouse and Sales", "Store",
                "Sales Ragged", "Sales 2"
        };

        CatalogReader reader = connection.getCatalogReader().withLocus();

        List<org.eclipse.daanse.olap.api.element.Cube> cubes = reader.getCubes();

        assertEquals(expectedCubes.length, cubes.size());

        assertCubeExists(expectedCubes, cubes);
    }

    @Test
    void testGetCubesWithNoRole(Connection connection) {
        String[] expectedCubes = new String[] {
                "Sales", "Warehouse", "Warehouse and Sales", "Store",
                "Sales Ragged", "Sales 2", "HR"
        };

        CatalogReader reader = connection.getCatalogReader().withLocus();

        List<org.eclipse.daanse.olap.api.element.Cube> cubes = reader.getCubes();

        assertEquals(expectedCubes.length, cubes.size());

        assertCubeExists(expectedCubes, cubes);
    }

    @Test
    void testGetCubesForCaliforniaManager(@Roles("California manager") Connection connection) {
        String[] expectedCubes = new String[] {
                "Sales"
        };

        CatalogReader reader = connection.getCatalogReader().withLocus();

        List<org.eclipse.daanse.olap.api.element.Cube> cubes = reader.getCubes();

        assertEquals(expectedCubes.length, cubes.size());

        assertCubeExists(expectedCubes, cubes);
    }

    @Test
    void testConnectUseContentChecksum(Context<?> context) {
        try {
            context.getConnectionWithDefaultRole();
        } catch (OlapRuntimeException e) {
            e.printStackTrace();
            fail("unexpected exception for UseContentChecksum");
        }
    }

    private void assertCubeExists(String[] expectedCubes, List<org.eclipse.daanse.olap.api.element.Cube> cubes) {
        List cubesAsList = Arrays.asList(expectedCubes);

        for (org.eclipse.daanse.olap.api.element.Cube cube : cubes) {
            String cubeName = cube.getName();
            assertTrue(cubesAsList.contains(cubeName), "Cube name not found: " + cubeName);
        }
    }

    /**
     * Test case for {@link CatalogReader#getCubeDimensions(Cube)}
     * and {@link CatalogReader#getDimensionHierarchies(Dimension)}
     * methods.
     *
     * <p>Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-691">MONDRIAN-691,
     * "RolapCatalogReader is not enforcing access control on two APIs"</a>.
     *
     * <p>Der Katalog mit der Rolle REG1 entsteht per Komposition
     * ({@code catalog = { FoodMart, Reg1Modifier }}); die Daten liefert die
     * benannte CSV-Brücke {@link FoodmartData} (der Schema-Load validiert
     * NamedSet-Formeln und braucht dafür die geladenen Dimensionen).
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, Reg1AccessRoleModifier.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testGetCubeDimensions(@Roles("REG1") Connection connection) {
        final String timeWeekly = "[Time].[Weekly]";

        CatalogReader reader = connection.getCatalogReader().withLocus();
        final Map<String, org.eclipse.daanse.olap.api.element.Cube> cubes = new HashMap<>();
        for (org.eclipse.daanse.olap.api.element.Cube cube : reader.getCubes()) {
            cubes.put(cube.getName(), cube);
        }
        assertTrue(cubes.containsKey("Sales")); // granted access
        assertFalse(cubes.containsKey("HR")); // denied access
        assertFalse(cubes.containsKey("Bad")); // not exist

        final org.eclipse.daanse.olap.api.element.Cube salesCube = cubes.get("Sales");
        final Map<String, org.eclipse.daanse.olap.api.element.Dimension> dimensions =
            new HashMap<>();
        final Map<String, org.eclipse.daanse.olap.api.element.Hierarchy> hierarchies =
            new HashMap<>();
        for (org.eclipse.daanse.olap.api.element.Dimension dimension : reader.getCubeDimensions(salesCube)) {
            dimensions.put(dimension.getName(), dimension);
            for (org.eclipse.daanse.olap.api.element.Hierarchy hierarchy
                : reader.getDimensionHierarchies(dimension))
            {
                hierarchies.put(hierarchy.getUniqueName(), hierarchy);
            }
        }
        assertFalse(dimensions.containsKey("Store")); // denied access
        assertTrue(dimensions.containsKey("Marital Status")); // implicit
        assertTrue(dimensions.containsKey("Time")); // implicit
        assertFalse(dimensions.containsKey("Bad dimension")); // not exist

        assertFalse(hierarchies.containsKey("[Foo]"));
        assertTrue(hierarchies.containsKey("[Product].[Product]"));
        assertTrue(hierarchies.containsKey(timeWeekly));
        assertFalse(hierarchies.containsKey("[Time].[Time]"));
    }

    /** Benannte Brücke auf die FoodMart-CSVs (für die data=-Supplier-Form). */
    public static class FoodmartData implements org.eclipse.daanse.cwm.testkit.api.DataSupplier {
        @Override
        public Map<String, java.net.URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

    /**
     * Erzeugt die Access-Rolle 'REG1' mit Dimension- und Hierarchy-Grants
     * über dem FoodMart-Katalog — der {@code (Catalog)}-Konstruktor macht die
     * Klasse kompositionsfähig für {@code @RolapContextTest(catalog = ...)}.
     */
    public static class Reg1AccessRoleModifier implements CatalogMappingSupplier {

        private final CatalogImpl catalog;

        public Reg1AccessRoleModifier(Catalog cat) {
            // Copy catalog using EcoreUtil
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) cat);
            catalog = (CatalogImpl) copier.get(cat);

            // Create dimension grant for Store (access = NONE)
            AccessDimensionGrant dimensionGrant =
                OlapFactory.eINSTANCE.createAccessDimensionGrant();
            dimensionGrant.setDimension((Dimension) copier.get(CatalogSupplier.DIMENSION_STORE));
            dimensionGrant.setDimensionAccess(DimensionAccess.NONE);

            // Create hierarchy grant for Time (access = NONE)
            AccessHierarchyGrant hierarchyGrant1 =
                OlapFactory.eINSTANCE.createAccessHierarchyGrant();
            hierarchyGrant1.setHierarchy((Hierarchy) copier.get(CatalogSupplier.HIERARCHY_TIME));
            hierarchyGrant1.setHierarchyAccess(HierarchyAccess.NONE);

            // Create hierarchy grant for Weekly (access = ALL)
            AccessHierarchyGrant hierarchyGrant2 =
                OlapFactory.eINSTANCE.createAccessHierarchyGrant();
            hierarchyGrant2.setHierarchy((Hierarchy) copier.get(CatalogSupplier.HIERARCHY_TIME2));
            hierarchyGrant2.setHierarchyAccess(HierarchyAccess.ALL);

            // Create cube grant
            AccessCubeGrant cubeGrant =
                OlapFactory.eINSTANCE.createAccessCubeGrant();
            cubeGrant.setCube((Cube) copier.get(CatalogSupplier.CUBE_SALES));
            cubeGrant.setCubeAccess(CubeAccess.ALL);
            cubeGrant.getDimensionGrants().add(dimensionGrant);
            cubeGrant.getHierarchyGrants().add(hierarchyGrant1);
            cubeGrant.getHierarchyGrants().add(hierarchyGrant2);

            // Create catalog grant
            AccessCatalogGrant catalogGrant =
                CommonFactory.eINSTANCE.createAccessCatalogGrant();
            catalogGrant.setCatalogAccess(CatalogAccess.NONE);
            catalogGrant.getCubeGrants().add(cubeGrant);

            // Create role
            AccessRole role =
                CommonFactory.eINSTANCE.createAccessRole();
            role.setName("REG1");
            role.getAccessCatalogGrants().add(catalogGrant);

            // Add role to catalog
            catalog.getImportedElement().add(role);
        }

        @Override
        public Catalog get() {
            return catalog;
        }
    }
}
