/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
 * All Rights Reserved.
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
package org.eclipse.daanse.test;

import static org.eclipse.daanse.rolap.mapping.model.provider.util.Expressions.mdx;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.net.URL;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.steelwheels.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.steelwheels.SteelWheelsDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.steelwheels.SteelWheelsTestInstance;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessCatalogGrant;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessRole;
import org.eclipse.daanse.rolap.mapping.model.access.common.CatalogAccess;
import org.eclipse.daanse.rolap.mapping.model.access.common.CommonFactory;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessCubeGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessDimensionGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessHierarchyGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessMemberGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.CubeAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.DimensionAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.HierarchyAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.MemberAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.OlapFactory;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
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
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.HideMemberIf;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelDefinition;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
/**
 * @author Andrey Khayrutdinov
 */
@RolapContextTest(SteelWheelsTestInstance.class)
class SteelWheelsAggregationTest {

    /** Named bridge onto the SteelWheels CSVs (for the {@code data =} supplier form). */
    public static class SteelWheelsData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new SteelWheelsTestInstance().dataSupplier().csvResources();
        }
    }

    private static final String QUERY = ""
            + "WITH\n"
            + "SET [*NATIVE_CJ_SET_WITH_SLICER] AS 'FILTER([*BASE_MEMBERS__Customer_DimUsage.Customers Hierarchy_], NOT ISEMPTY ([Measures].[Price Each]))'\n"
            + "SET [*NATIVE_CJ_SET] AS '[*NATIVE_CJ_SET_WITH_SLICER]'\n"
            + "SET [*SORTED_ROW_AXIS] AS 'ORDER([*CJ_ROW_AXIS],[Customer_DimUsage].[Customers Hierarchy].CURRENTMEMBER.ORDERKEY,"
            + "BASC,ANCESTOR([Customer_DimUsage].[Customers Hierarchy].CURRENTMEMBER,[Customer_DimUsage].[Customers Hierarchy].[Address]).ORDERKEY,BASC)'\n"
            + "SET [*BASE_MEMBERS__Measures_] AS '{[Measures].[Price Each]}'\n"
            + "SET [*BASE_MEMBERS__Customer_DimUsage.Customers Hierarchy_] AS '[Customer_DimUsage].[Customers Hierarchy].[Name].MEMBERS'\n"
            + "SET [*CJ_ROW_AXIS] AS 'GENERATE([*NATIVE_CJ_SET], {([Customer_DimUsage].[Customers Hierarchy].CURRENTMEMBER)})'\n"
            + "SELECT\n"
            + "[*BASE_MEMBERS__Measures_] ON COLUMNS\n"
            + ",[*SORTED_ROW_AXIS] ON ROWS\n"
            + "FROM [Customers Cube]\n";

    private static final String EXPECTED = ""
            + "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Price Each]}\n"
            + "Axis #2:\n"
            + "{[Customer_DimUsage].[Customers Hierarchy].[1 rue Alsace-Lorraine].[Roulet]}\n"
            + "Row #0: 1,701.95\n";

    private static final TableSource customerQuery = SourceFactory.eINSTANCE.createTableSource();
    private static final Level nameLevel = LevelFactory.eINSTANCE.createLevel();
    private static final Level addressLevel = LevelFactory.eINSTANCE.createLevel();
    private static final ExplicitHierarchy customersHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
    private static final StandardDimension customersDimension = DimensionFactory.eINSTANCE.createStandardDimension();
    private static final TableSource tq = SourceFactory.eINSTANCE.createTableSource();
    private static final DimensionConnector dcCustomer = DimensionFactory.eINSTANCE.createDimensionConnector();
    private static final SumMeasure measurePriceEach = MeasureFactory.eINSTANCE.createSumMeasure();
    private static final SumMeasure measureTotalPrice = MeasureFactory.eINSTANCE.createSumMeasure();
    private static final MeasureGroup mg = CubeFactory.eINSTANCE.createMeasureGroup();
    private static final PhysicalCube customersCube = CubeFactory.eINSTANCE.createPhysicalCube();

    static {
    customerQuery.setTable(CatalogSupplier.TABLE_CUSTOMER);


    nameLevel.setName("Name");
    nameLevel.setVisible(true);
    nameLevel.setColumn(CatalogSupplier.COLUMN_CONTACTLASTNAME_CUSTOMER);
    nameLevel.setColumnType(ColumnInternalDataType.STRING);
    nameLevel.setUniqueMembers(false);
    nameLevel.setType(LevelDefinition.REGULAR);
    nameLevel.setHideMemberIf(HideMemberIf.NEVER);
            //.withCaption("Contact Last Name")


    addressLevel.setName("Address");
    addressLevel.setVisible(true);
    addressLevel.setColumn(CatalogSupplier.COLUMN_ADDRESSLINE1_CUSTOMER);
    addressLevel.setColumnType(ColumnInternalDataType.STRING);
    addressLevel.setUniqueMembers(false);
    addressLevel.setType(LevelDefinition.REGULAR);
    addressLevel.setHideMemberIf(HideMemberIf.NEVER);
    //.withCaption("Address Line 1")



    customersHierarchy.setName("Customers Hierarchy");
    customersHierarchy.setVisible(true);
    customersHierarchy.setHasAll(true);
    customersHierarchy.setPrimaryKey(CatalogSupplier.COLUMN_CUSTOMERNUMBER_CUSTOMER);
    //.withCaption("Customer Hierarchy")
    customersHierarchy.setSource(customerQuery);
    customersHierarchy.getLevels().addAll(List.of(
                addressLevel,
                nameLevel
            ));


    customersDimension.setVisible(true);
    customersDimension.setName("Customers Dimension");
    customersDimension.getHierarchies().add(customersHierarchy);

    tq.setTable(CatalogSupplier.TABLE_ORDERFACT);

    dcCustomer.setDimension(customersDimension);
    dcCustomer.setOverrideDimensionName("Customer_DimUsage");
    dcCustomer.setVisible(true);
    dcCustomer.setForeignKey(CatalogSupplier.COLUMN_CUSTOMERNUMBER_ORDERFACT);

    measurePriceEach.setName("Price Each");
    measurePriceEach.setColumn(CatalogSupplier.COLUMN_PRICEEACH_ORDERFACT);
    measurePriceEach.setVisible(true);

    measureTotalPrice.setName("Total Price");
    measureTotalPrice.setColumn(CatalogSupplier.COLUMN_TOTALPRICE_ORDERFACT);
    measureTotalPrice.setVisible(true);

    mg.getMeasures().add(measurePriceEach);
    mg.getMeasures().add(measureTotalPrice);

    customersCube.setName("Customers Cube");
    customersCube.setVisible(true);
    customersCube.setCache(true);
    customersCube.setEnabled(true);
    customersCube.setSource(tq);
    customersCube.getDimensionConnectors().add(dcCustomer);
    customersCube.getMeasureGroups().add(mg);
    }

    @BeforeEach
    public void beforeEach() {
        //propertySaver.set(propertySaver.properties.UseAggregates, true);
        //propertySaver.set(propertySaver.properties.ReadAggregates, true);
    }

    @AfterEach
    public void afterEach() {
    }



    private static Catalog getSchemaWith(List<AccessRole> roles) {


        Catalog catalog = CatalogFactory.eINSTANCE.createCatalog();
        catalog.setName("SteelWheels");
        describe(catalog, catalog, "1 admin role, 1 user role. For testing MemberGrant with caching in 5.1.2");
        catalog.getImportedElement().add(customersCube);
        catalog.getImportedElement().addAll(roles);
        // Copy, don't steal: CatalogSupplier.DATABASE_SCHEMA_STEELWHEELS is contained by the
        // shared static CatalogSupplier singleton catalog; adding it directly here would move
        // it out from under every other user of that singleton for the life of the JVM.
        catalog.getImportedElement().add((Schema) EcoreUtil.copy(CatalogSupplier.DATABASE_SCHEMA_STEELWHEELS));
    	return catalog;
    }

    @Disabled //disabled for CI build
    @Test
    @RolapContextTest(catalog = { TestWithAggregationCatalogSupplier.class },
        database = SteelWheelsDatabaseSupplier.class, data = SteelWheelsData.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testWithAggregation(Context<?> context) throws Exception {
        assertThatQuery(context.getConnection(new ConnectionProps(List.of("Power User"))), QUERY).returnsGrid(EXPECTED);
    }

    public static class TestWithAggregationCatalogSupplier implements CatalogMappingSupplier {
        @Override
        public Catalog get() {
            AccessDimensionGrant dimensionGrant = OlapFactory.eINSTANCE.createAccessDimensionGrant();
            //.withDimension("Measures")
            dimensionGrant.setDimensionAccess(DimensionAccess.ALL);

            AccessMemberGrant memberGrant1 = OlapFactory.eINSTANCE.createAccessMemberGrant();
            memberGrant1.setMember(mdx("[Customer_DimUsage.Customers Hierarchy].[1 rue Alsace-Lorraine]"));
            memberGrant1.setMemberAccess(MemberAccess.NONE);

            AccessMemberGrant memberGrant2 = OlapFactory.eINSTANCE.createAccessMemberGrant();
            memberGrant2.setMember(mdx("[Customer_DimUsage.Customers Hierarchy].[1 rue Alsace-Lorraine].[Roulet]"));
            memberGrant2.setMemberAccess(MemberAccess.ALL);

            AccessHierarchyGrant hierarchyGrant = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
            hierarchyGrant.setHierarchy(customersHierarchy);
            hierarchyGrant.setTopLevel(nameLevel);
            hierarchyGrant.setRollupPolicy(RollupPolicy.PARTIAL);
            hierarchyGrant.setHierarchyAccess(HierarchyAccess.CUSTOM);
            hierarchyGrant.getMemberGrants().add(memberGrant1);
            hierarchyGrant.getMemberGrants().add(memberGrant2);

            AccessCubeGrant accessCubeGrant = OlapFactory.eINSTANCE.createAccessCubeGrant();
            accessCubeGrant.setCube(customersCube);
            accessCubeGrant.setCubeAccess(CubeAccess.ALL);
            accessCubeGrant.getDimensionGrants().add(dimensionGrant);
            accessCubeGrant.getHierarchyGrants().add(hierarchyGrant);

            final AccessCatalogGrant accessCatalogGrant = CommonFactory.eINSTANCE.createAccessCatalogGrant();
            accessCatalogGrant.setCatalogAccess(CatalogAccess.NONE);
            accessCatalogGrant.getCubeGrants().add(accessCubeGrant);

            final AccessRole powerUserRole = CommonFactory.eINSTANCE.createAccessRole();
            powerUserRole.setName("Power User");
            powerUserRole.getAccessCatalogGrants().add(accessCatalogGrant);

            return getSchemaWith(List.of(powerUserRole));
        }
    }

    @Test
    @RolapContextTest(catalog = { TestWithAggregationNoRestrictionsOnTopLevelCatalogSupplier.class },
        database = SteelWheelsDatabaseSupplier.class, data = SteelWheelsData.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testWithAggregationNoRestrictionsOnTopLevel(Context<?> context) throws Exception {
        assertThatQuery(context.getConnection(new ConnectionProps(List.of("Power User"))), QUERY).returnsGrid(EXPECTED);
    }

    public static class TestWithAggregationNoRestrictionsOnTopLevelCatalogSupplier implements CatalogMappingSupplier {
        @Override
        public Catalog get() {
            AccessDimensionGrant dimensionGrant = OlapFactory.eINSTANCE.createAccessDimensionGrant();
            //.withDimension("Measures")
            dimensionGrant.setDimensionAccess(DimensionAccess.ALL);

            AccessMemberGrant memberGrant = OlapFactory.eINSTANCE.createAccessMemberGrant();
            memberGrant.setMember(mdx("[Customer_DimUsage].[Customers Hierarchy].[1 rue Alsace-Lorraine]"));
            memberGrant.setMemberAccess(MemberAccess.ALL);

            AccessHierarchyGrant hierarchyGrant = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
            hierarchyGrant.setHierarchy(customersHierarchy);
            hierarchyGrant.setTopLevel(nameLevel);
            hierarchyGrant.setRollupPolicy(RollupPolicy.PARTIAL);
            hierarchyGrant.setHierarchyAccess(HierarchyAccess.CUSTOM);
            hierarchyGrant.getMemberGrants().add(memberGrant);

            AccessCubeGrant accessCubeGrant = OlapFactory.eINSTANCE.createAccessCubeGrant();
            accessCubeGrant.setCube(customersCube);
            accessCubeGrant.setCubeAccess(CubeAccess.ALL);
            accessCubeGrant.getDimensionGrants().add(dimensionGrant);
            accessCubeGrant.getHierarchyGrants().add(hierarchyGrant);

            final AccessCatalogGrant accessCatalogGrant = CommonFactory.eINSTANCE.createAccessCatalogGrant();
            accessCatalogGrant.setCatalogAccess(CatalogAccess.NONE);
            accessCatalogGrant.getCubeGrants().add(accessCubeGrant);

            final AccessRole powerUserRole = CommonFactory.eINSTANCE.createAccessRole();
            powerUserRole.setName("Power User");
            powerUserRole.getAccessCatalogGrants().add(accessCatalogGrant);

            return getSchemaWith(List.of(powerUserRole));
        }
    }

    @Disabled //disabled for CI build
    @Test
    @RolapContextTest(catalog = { TestUnionWithAggregationCatalogSupplier.class },
        database = SteelWheelsDatabaseSupplier.class, data = SteelWheelsData.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testUnionWithAggregation(Context<?> context) throws Exception {
        assertThatQuery(context.getConnection(new ConnectionProps(List.of("Power User Union"))), QUERY).returnsGrid(EXPECTED);
    }

    public static class TestUnionWithAggregationCatalogSupplier implements CatalogMappingSupplier {
        @Override
        public Catalog get() {
            AccessDimensionGrant dimensionGrant = OlapFactory.eINSTANCE.createAccessDimensionGrant();
            //.withDimension("Measures")
            dimensionGrant.setDimensionAccess(DimensionAccess.ALL);

            AccessMemberGrant memberGrant = OlapFactory.eINSTANCE.createAccessMemberGrant();
            memberGrant.setMember(mdx("[Customer_DimUsage.Customers Hierarchy].[1 rue Alsace-Lorraine].[Roulet]"));
            memberGrant.setMemberAccess(MemberAccess.NONE);

            AccessHierarchyGrant hierarchyGrant = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
            hierarchyGrant.setHierarchy(customersHierarchy);
            hierarchyGrant.setTopLevel(nameLevel);
            hierarchyGrant.setRollupPolicy(RollupPolicy.PARTIAL);
            hierarchyGrant.setHierarchyAccess(HierarchyAccess.CUSTOM);
            hierarchyGrant.getMemberGrants().add(memberGrant);

            AccessCubeGrant accessCubeGrant = OlapFactory.eINSTANCE.createAccessCubeGrant();
            accessCubeGrant.setCube(customersCube);
            accessCubeGrant.setCubeAccess(CubeAccess.ALL);
            accessCubeGrant.getDimensionGrants().add(dimensionGrant);
            accessCubeGrant.getHierarchyGrants().add(hierarchyGrant);

            final AccessCatalogGrant accessCatalogGrant = CommonFactory.eINSTANCE.createAccessCatalogGrant();
            accessCatalogGrant.setCatalogAccess(CatalogAccess.NONE);
            accessCatalogGrant.getCubeGrants().add(accessCubeGrant);

            final AccessCatalogGrant accessCatalogGrant1 = CommonFactory.eINSTANCE.createAccessCatalogGrant();
            accessCatalogGrant1.setCatalogAccess(CatalogAccess.NONE);

            final AccessRole fooRole = CommonFactory.eINSTANCE.createAccessRole();
            fooRole.setName("Foo");
            fooRole.getAccessCatalogGrants().add(accessCatalogGrant1);

            final AccessRole powerUserRole = CommonFactory.eINSTANCE.createAccessRole();
            powerUserRole.setName("Power User");
            powerUserRole.getAccessCatalogGrants().add(accessCatalogGrant);

            final AccessRole powerUserUnionRole = CommonFactory.eINSTANCE.createAccessRole();
            powerUserRole.setName("Power User Union");
            powerUserRole.getReferencedAccessRoles().add(powerUserUnionRole);
            powerUserRole.getReferencedAccessRoles().add(fooRole);

            return getSchemaWith(List.of(fooRole, powerUserRole, powerUserUnionRole));
        }
    }

    @Disabled //disabled for CI build
    @Test
    @RolapContextTest(catalog = { TestWithAggregationUnionRolesWithSameGrantsCatalogSupplier.class },
        database = SteelWheelsDatabaseSupplier.class, data = SteelWheelsData.class)
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    void testWithAggregationUnionRolesWithSameGrants(Context<?> context) throws Exception {
        assertThatQuery(context.getConnection(new ConnectionProps(List.of("Power User Union"))), QUERY).returnsGrid(EXPECTED);
    }

    public static class TestWithAggregationUnionRolesWithSameGrantsCatalogSupplier implements CatalogMappingSupplier {
        @Override
        public Catalog get() {
            AccessDimensionGrant dimensionGrant = OlapFactory.eINSTANCE.createAccessDimensionGrant();
            //.withDimension("Measures")
            dimensionGrant.setDimensionAccess(DimensionAccess.ALL);

            AccessDimensionGrant dimensionGrant1 = OlapFactory.eINSTANCE.createAccessDimensionGrant();
            //.withDimension("Measures")
            dimensionGrant1.setDimensionAccess(DimensionAccess.ALL);

            AccessMemberGrant memberGrant = OlapFactory.eINSTANCE.createAccessMemberGrant();
            memberGrant.setMember(mdx("[Customer_DimUsage.Customers Hierarchy].[1 rue Alsace-Lorraine].[Roulet]"));
            memberGrant.setMemberAccess(MemberAccess.NONE);

            AccessMemberGrant memberGrant1 = OlapFactory.eINSTANCE.createAccessMemberGrant();
            memberGrant1.setMember(mdx("[Customer_DimUsage.Customers Hierarchy].[1 rue Alsace-Lorraine].[Roulet]"));
            memberGrant1.setMemberAccess(MemberAccess.ALL);

            AccessHierarchyGrant hierarchyGrant = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
            hierarchyGrant.setHierarchy(customersHierarchy);
            hierarchyGrant.setTopLevel(nameLevel);
            hierarchyGrant.setRollupPolicy(RollupPolicy.PARTIAL);
            hierarchyGrant.setHierarchyAccess(HierarchyAccess.CUSTOM);
            hierarchyGrant.getMemberGrants().add(memberGrant);

            AccessHierarchyGrant hierarchyGrant1 = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
            hierarchyGrant1.setHierarchy(customersHierarchy);
            hierarchyGrant1.setTopLevel(nameLevel);
            hierarchyGrant1.setRollupPolicy(RollupPolicy.PARTIAL);
            hierarchyGrant1.setHierarchyAccess(HierarchyAccess.CUSTOM);
            hierarchyGrant1.getMemberGrants().add(memberGrant1);

            AccessCubeGrant accessCubeGrant = OlapFactory.eINSTANCE.createAccessCubeGrant();
            accessCubeGrant.setCube(customersCube);
            accessCubeGrant.setCubeAccess(CubeAccess.ALL);
            accessCubeGrant.getDimensionGrants().add(dimensionGrant);
            accessCubeGrant.getHierarchyGrants().add(hierarchyGrant);

            final AccessCatalogGrant accessCatalogGrant = CommonFactory.eINSTANCE.createAccessCatalogGrant();
            accessCatalogGrant.setCatalogAccess(CatalogAccess.NONE);
            accessCatalogGrant.getCubeGrants().add(accessCubeGrant);

            AccessCubeGrant accessCubeGrant1 = OlapFactory.eINSTANCE.createAccessCubeGrant();
            accessCubeGrant1.setCube(customersCube);
            accessCubeGrant1.setCubeAccess(CubeAccess.ALL);
            accessCubeGrant1.getDimensionGrants().add(dimensionGrant1);
            accessCubeGrant1.getHierarchyGrants().add(hierarchyGrant1);

            final AccessCatalogGrant accessCatalogGrant1 = CommonFactory.eINSTANCE.createAccessCatalogGrant();
            accessCatalogGrant1.setCatalogAccess(CatalogAccess.NONE);
            accessCatalogGrant1.getCubeGrants().add(accessCubeGrant1);

            final AccessRole fooRole = CommonFactory.eINSTANCE.createAccessRole();
            fooRole.setName("Foo");
            fooRole.getAccessCatalogGrants().add(accessCatalogGrant1);

            final AccessRole powerUserRole = CommonFactory.eINSTANCE.createAccessRole();
            powerUserRole.setName("Power User");
            powerUserRole.getAccessCatalogGrants().add(accessCatalogGrant);

            final AccessRole powerUserUnionRole = CommonFactory.eINSTANCE.createAccessRole();
            powerUserRole.setName("Power User Union");
            powerUserRole.getReferencedAccessRoles().add(powerUserUnionRole);
            powerUserRole.getReferencedAccessRoles().add(fooRole);

            return getSchemaWith(List.of(fooRole, powerUserRole, powerUserUnionRole));
        }
    }

    /**
     * Attaches a 'documentation' Description to the element, adopting its
     * root into the catalog if needed.
     */
    private static void describe(Catalog catalog,
            org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement element, String text) {
        try {
            org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions.describe(element,
                    org.eclipse.daanse.rolap.mapping.model.provider.util.CwmHelper.TYPE_DOCUMENTATION, null, text);
        } catch (IllegalStateException detached) {
            catalog.getOwnedElement().add((org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement)
                    EcoreUtil.getRootContainer(element));
            org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions.describe(element,
                    org.eclipse.daanse.rolap.mapping.model.provider.util.CwmHelper.TYPE_DOCUMENTATION, null, text);
        }
    }
}
