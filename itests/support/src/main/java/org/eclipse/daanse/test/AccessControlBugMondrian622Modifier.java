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
package org.eclipse.daanse.test;

import static org.eclipse.daanse.rolap.mapping.model.provider.util.Expressions.mdx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessCatalogGrant;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessRole;
import org.eclipse.daanse.rolap.mapping.model.access.common.CatalogAccess;
import org.eclipse.daanse.rolap.mapping.model.access.common.CommonFactory;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessCubeGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessHierarchyGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessMemberGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.CubeAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.HierarchyAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.MemberAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.OlapFactory;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.SumMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.StandardDimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ExplicitHierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.HierarchyFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.RollupPolicy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;

/**
 * Self-contained fixture for {@link AccessControlTest#testBugMondrian622}
 * (MONDRIAN-622, "Poor performance with large union role"): builds a "Sales
 * with multiple customers" cube with three duplicate Customers dimensions,
 * plus one {@code AccessRole} per distinct (country, state province, city)
 * grouping in the {@code customer} table -- unioned into a single "Test"
 * role -- to stress-test evaluating a large union role.
 *
 * <p>The original test derived that per-city role list from the positions
 * of a live {@code [Customers].[City].Members} query result, computed
 * against the plain FoodMart catalog immediately before swapping in this
 * modifier. The new testkit builds the catalog once, before any connection
 * exists, so there is no live query to draw on here; this reads the same
 * (country, state_province, city) triples directly out of the FoodMart
 * {@code customer.csv} fixture instead. The set of triples -- and so the
 * number and shape of the generated per-city roles -- is the same either
 * way, since both are sourced from the same underlying FoodMart customer
 * data. The test itself only asserts that the query completes, not on
 * specific values, so this fixture only needs to reproduce the shape of the
 * original schema, not the live-query mechanism used to build it.
 */
public class AccessControlBugMondrian622Modifier implements CatalogMappingSupplier {

    private final CatalogImpl catalog;

    private final ExplicitHierarchy hCustomers = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
    private final ExplicitHierarchy hCustomers2 = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
    private final ExplicitHierarchy hCustomers3 = HierarchyFactory.eINSTANCE.createExplicitHierarchy();

    public AccessControlBugMondrian622Modifier(Catalog catalogMapping) {
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) catalogMapping);
        this.catalog = (CatalogImpl) copier.get(catalogMapping);

        // Customers / Customers2 / Customers3: three identically-shaped
        // dimensions over the same customer table, matching the original.
        buildCustomersHierarchy(hCustomers);
        buildCustomersHierarchy(hCustomers2);
        buildCustomersHierarchy(hCustomers3);

        StandardDimension dCustomers = DimensionFactory.eINSTANCE.createStandardDimension();
        dCustomers.setName("Customers");
        dCustomers.getHierarchies().add(hCustomers);

        StandardDimension dCustomers2 = DimensionFactory.eINSTANCE.createStandardDimension();
        dCustomers2.setName("Customers2");
        dCustomers2.getHierarchies().add(hCustomers2);

        StandardDimension dCustomers3 = DimensionFactory.eINSTANCE.createStandardDimension();
        dCustomers3.setName("Customers3");
        dCustomers3.getHierarchies().add(hCustomers3);

        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName("Sales with multiple customers");

        TableSource factTable = SourceFactory.eINSTANCE.createTableSource();
        factTable.setTable(CatalogSupplier.TABLE_SALES_FACT);
        cube.setSource(factTable);

        DimensionConnector dcTime = DimensionFactory.eINSTANCE.createDimensionConnector();
        dcTime.setOverrideDimensionName("Time");
        dcTime.setDimension(CatalogSupplier.DIMENSION_TIME);
        dcTime.setForeignKey(CatalogSupplier.COLUMN_TIME_ID_SALESFACT);

        DimensionConnector dcProduct = DimensionFactory.eINSTANCE.createDimensionConnector();
        dcProduct.setOverrideDimensionName("Product");
        dcProduct.setDimension(CatalogSupplier.DIMENSION_PRODUCT);
        dcProduct.setForeignKey(CatalogSupplier.COLUMN_PRODUCT_ID_SALESFACT);

        DimensionConnector dcCustomers = DimensionFactory.eINSTANCE.createDimensionConnector();
        dcCustomers.setOverrideDimensionName("Customers");
        dcCustomers.setDimension(dCustomers);
        dcCustomers.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);

        DimensionConnector dcCustomers2 = DimensionFactory.eINSTANCE.createDimensionConnector();
        dcCustomers2.setOverrideDimensionName("Customers2");
        dcCustomers2.setDimension(dCustomers2);
        dcCustomers2.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);

        DimensionConnector dcCustomers3 = DimensionFactory.eINSTANCE.createDimensionConnector();
        dcCustomers3.setOverrideDimensionName("Customers3");
        dcCustomers3.setDimension(dCustomers3);
        dcCustomers3.setForeignKey(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT);

        cube.getDimensionConnectors().add(dcTime);
        cube.getDimensionConnectors().add(dcProduct);
        cube.getDimensionConnectors().add(dcCustomers);
        cube.getDimensionConnectors().add(dcCustomers2);
        cube.getDimensionConnectors().add(dcCustomers3);

        MeasureGroup mg = CubeFactory.eINSTANCE.createMeasureGroup();
        SumMeasure measure = MeasureFactory.eINSTANCE.createSumMeasure();
        measure.setName("Unit Sales");
        measure.setColumn(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT);
        measure.setFormatString("Standard");
        mg.getMeasures().add(measure);

        cube.getMeasureGroups().add(mg);
        catalog.getImportedElement().add(cube);

        List<AccessRole> res = new ArrayList<>();
        List<AccessRole> roleUsages = new ArrayList<>();
        for (String[] triple : distinctCountryStateCityTriples()) {
            String country = triple[0];
            String stateProvince = triple[1];
            String city = triple[2];
            String name = stateProvince + "." + city; // e.g. "BC.Burnaby"
            String uniqueName = "[Customers].[Customers].[" + country + "].[" + stateProvince + "].[" + city + "]";
            String uniqueName2 = uniqueName.replace("Customers", "Customers2");
            String uniqueName3 = uniqueName.replace("Customers", "Customers3");

            AccessRole r = CommonFactory.eINSTANCE.createAccessRole();
            r.setName(name);

            AccessCatalogGrant catalogGrant = CommonFactory.eINSTANCE.createAccessCatalogGrant();
            catalogGrant.setCatalogAccess(CatalogAccess.NONE);

            AccessCubeGrant cubeGrant = OlapFactory.eINSTANCE.createAccessCubeGrant();
            cubeGrant.setCubeAccess(CubeAccess.ALL);
            cubeGrant.setCube(cube);

            AccessHierarchyGrant hg1 = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
            hg1.setHierarchyAccess(HierarchyAccess.CUSTOM);
            hg1.setHierarchy(hCustomers);
            hg1.setRollupPolicy(RollupPolicy.PARTIAL);

            AccessMemberGrant mg1 = OlapFactory.eINSTANCE.createAccessMemberGrant();
            mg1.setMemberAccess(MemberAccess.ALL);
            mg1.setMember(mdx(uniqueName));
            hg1.getMemberGrants().add(mg1);

            AccessHierarchyGrant hg2 = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
            hg2.setHierarchyAccess(HierarchyAccess.CUSTOM);
            hg2.setHierarchy(hCustomers2);
            hg2.setRollupPolicy(RollupPolicy.PARTIAL);

            AccessMemberGrant mg2 = OlapFactory.eINSTANCE.createAccessMemberGrant();
            mg2.setMemberAccess(MemberAccess.ALL);
            mg2.setMember(mdx(uniqueName2));
            hg2.getMemberGrants().add(mg2);

            AccessHierarchyGrant hg3 = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
            hg3.setHierarchyAccess(HierarchyAccess.CUSTOM);
            hg3.setHierarchy(hCustomers3);
            hg3.setRollupPolicy(RollupPolicy.PARTIAL);

            AccessMemberGrant mg3 = OlapFactory.eINSTANCE.createAccessMemberGrant();
            mg3.setMemberAccess(MemberAccess.ALL);
            mg3.setMember(mdx(uniqueName3));
            hg3.getMemberGrants().add(mg3);

            cubeGrant.getHierarchyGrants().add(hg1);
            cubeGrant.getHierarchyGrants().add(hg2);
            cubeGrant.getHierarchyGrants().add(hg3);

            catalogGrant.getCubeGrants().add(cubeGrant);
            r.getAccessCatalogGrants().add(catalogGrant);

            res.add(r);
            roleUsages.add(r);
        }

        AccessRole testRole = CommonFactory.eINSTANCE.createAccessRole();
        testRole.setName("Test");
        testRole.getReferencedAccessRoles().addAll(roleUsages);
        res.add(testRole);
        catalog.getImportedElement().addAll(res);
    }

    private void buildCustomersHierarchy(ExplicitHierarchy hierarchy) {
        hierarchy.setHasAll(true);
        hierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);
        TableSource tq = SourceFactory.eINSTANCE.createTableSource();
        tq.setTable(CatalogSupplier.TABLE_CUSTOMER);
        hierarchy.setSource(tq);

        Level levelCountry = LevelFactory.eINSTANCE.createLevel();
        levelCountry.setName("Country");
        levelCountry.setColumn(CatalogSupplier.COLUMN_COUNTRY_CUSTOMER);
        levelCountry.setUniqueMembers(true);

        Level levelStateProvince = LevelFactory.eINSTANCE.createLevel();
        levelStateProvince.setName("State Province");
        levelStateProvince.setColumn(CatalogSupplier.COLUMN_STATE_PROVINCE_CUSTOMER);
        levelStateProvince.setUniqueMembers(true);

        Level levelCity = LevelFactory.eINSTANCE.createLevel();
        levelCity.setName("City");
        levelCity.setColumn(CatalogSupplier.COLUMN_CITY_CUSTOMER);
        levelCity.setUniqueMembers(false);

        Level levelName = LevelFactory.eINSTANCE.createLevel();
        levelName.setName("Name");
        levelName.setColumn(CatalogSupplier.COLUMN_CUSTOMER_ID_CUSTOMER);
        levelName.setColumnType(ColumnInternalDataType.NUMERIC);
        levelName.setUniqueMembers(true);

        hierarchy.getLevels().add(levelCountry);
        hierarchy.getLevels().add(levelStateProvince);
        hierarchy.getLevels().add(levelCity);
        hierarchy.getLevels().add(levelName);
    }

    /** Distinct (country, state_province, city) triples from the FoodMart {@code customer.csv} fixture. */
    private static List<String[]> distinctCountryStateCityTriples() {
        URL csv = org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance.class
                .getResource("data/customer.csv");
        Set<String> seen = new LinkedHashSet<>();
        List<String[]> triples = new ArrayList<>();
        try (InputStream in = csv.openStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            List<String> columns = List.of(splitCsvLine(header));
            int cityIdx = columns.indexOf("city");
            int stateIdx = columns.indexOf("state_province");
            int countryIdx = columns.indexOf("country");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = splitCsvLine(line);
                String city = fields[cityIdx];
                String state = fields[stateIdx];
                String country = fields[countryIdx];
                String key = country + " " + state + " " + city;
                if (seen.add(key)) {
                    triples.add(new String[] {country, state, city});
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read FoodMart customer.csv fixture", e);
        }
        return triples;
    }

    /** Splits one CSV line on commas, honoring double-quoted fields (which may themselves contain commas). */
    private static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}
