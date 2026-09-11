/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2018 Hitachi Vantara
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

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.access.DelegatingRole;
import org.eclipse.daanse.olap.access.RoleImpl;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.access.AccessCatalog;
import org.eclipse.daanse.olap.api.access.AccessCube;
import org.eclipse.daanse.olap.api.access.AccessDimension;
import org.eclipse.daanse.olap.api.access.AccessHierarchy;
import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.HierarchyAccess;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.query.Quoting;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.rolap.element.RolapHierarchy.LimitedRollupMember;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.RollupPolicy;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import org.eclipse.daanse.rolap.SchemaModifiersEmf;

/**
 * <code>AccessControlTest</code> is a set of unit-tests for access-control.
 * For these tests, all of the roles are of type RoleImpl.
 *
 * <p>{@code SAME_THREAD}: many scenarios compose their own {@code CatalogSupplier}
 * (FoodMart mapping) instance -- like {@link mondrian.rolap.aggmatcher.ExplicitRecognizerTest},
 * this opts out of the module's default concurrent execution so those
 * constructions don't race across this class's own methods.
 *
 * @see Role
 *
 * @author jhyde
 * @since Feb 21, 2003
 */
@RolapContextTest(FoodmartTestInstance.class)
@Execution(ExecutionMode.SAME_THREAD)
class AccessControlTest {


    private static Optional<Cube> getCubeByNameFromArray(List<Cube> cubes, String name) {
        return cubes.stream().filter(c -> name.equals(c.getName())).findFirst();
    }

    /*
    private static final String BiServer1574Role1 =
        "<Role name=\"role1\">\n"
        + " <SchemaGrant access=\"none\">\n"
        + "  <CubeGrant cube=\"Warehouse\" access=\"all\">\n"
        + "   <HierarchyGrant hierarchy=\"[Store Size in SQFT]\" access=\"custom\" rollupPolicy=\"partial\">\n"
        + "    <MemberGrant member=\"[Store Size in SQFT].[20319]\" access=\"all\"/>\n"
        + "    <MemberGrant member=\"[Store Size in SQFT].[21215]\" access=\"none\"/>\n"
        + "   </HierarchyGrant>\n"
        + "   <HierarchyGrant hierarchy=\"[Store Type]\" access=\"custom\" rollupPolicy=\"partial\">\n"
        + "    <MemberGrant member=\"[Store Type].[Supermarket]\" access=\"all\"/>\n"
        + "   </HierarchyGrant>\n"
        + "  </CubeGrant>\n"
        + " </SchemaGrant>\n"
        + "</Role>";
    */

	@AfterEach
	public void afterEach() {
	}

    @Test
    void testCatalogReader(Context<?> foodMartContext) {
        final Connection connection = foodMartContext.getConnectionWithDefaultRole();
        org.eclipse.daanse.olap.api.element.Catalog schema = connection.getCatalog();
        Cube cube = schema.lookupCube("Sales").orElseThrow();
        final CatalogReader schemaReader =
            cube.getCatalogReader(connection.getRole());
        final CatalogReader schemaReader1 = schemaReader.withoutAccessControl();
        assertNotNull(schemaReader1);
        final CatalogReader schemaReader2 = schemaReader1.withoutAccessControl();
        assertNotNull(schemaReader2);
    }

    @Test
    void testGrantDimensionNone(Context<?> foodMartContext) {
        final Connection connection = foodMartContext.getConnectionWithDefaultRole();
        RoleImpl role = ((RoleImpl) connection.getRole()).makeMutableClone();
        org.eclipse.daanse.olap.api.element.Catalog schema = connection.getCatalog();
        Cube salesCube = schema.lookupCube("Sales").orElseThrow();
        // todo: add Schema.lookupDimension
        final CatalogReader schemaReader = salesCube.getCatalogReader(role);
        org.eclipse.daanse.olap.api.element.Dimension genderDimension =
            (org.eclipse.daanse.olap.api.element.Dimension) schemaReader.lookupCompound(
                salesCube, IdImpl.toList("Gender"), true,
                DataType.DIMENSION);
        role.grant(genderDimension, AccessDimension.NONE);
        role.makeImmutable();
        connection.setRole(role);
        assertThatAxis(connection, "Sales",
            "[Gender].children").throwsMessage(
            "MDX object '[Gender]' not found in cube 'Sales'");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier31.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRestrictMeasures(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnectionWithDefaultRole();

        assertThatQuery(connection,
            "SELECT {[Measures].Members} ON COLUMNS FROM [SALES]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "{[Measures].[Store Cost]}\n"
            + "{[Measures].[Store Sales]}\n"
            + "{[Measures].[Sales Count]}\n"
            + "{[Measures].[Customer Count]}\n"
            + "{[Measures].[Promotion Sales]}\n"
            + "Row #0: 266,773\n"
            + "Row #0: 225,627.23\n"
            + "Row #0: 565,238.13\n"
            + "Row #0: 86,837\n"
            + "Row #0: 5,581\n"
            + "Row #0: 151,211.21\n");

        props =new ConnectionProps(List.of("Role2"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	connection = foodMartContext.getConnection(props);
    	assertThatQuery(connection,
            "SELECT {[Measures].Members} ON COLUMNS FROM [SALES]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Row #0: 266,773\n");
    }

    /**Test for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-2603">MONDRIAN-2603</a>
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier32.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRestrictMeasuresHierarchy_InTwoRoles(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Administrator"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
      Connection connection = foodMartContext.getConnection(props);

      try {
	  assertThatQuery(connection,
          "SELECT {[Measures].Members} ON COLUMNS FROM [Warehouse2]").returnsGrid(
          "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n"
          + "{[Measures].[Measure2_0]}\n"
          + "{[Measures].[Measure2_1]}\n"
          + "{[Measures].[Fact Count]}\n"
          + "Row #0: 89,043.253\n"
          + "Row #0: 196,770.888\n"
          + "Row #0: 4,070\n");
      } catch (OlapRuntimeException e) {
        if (e.getCause().getLocalizedMessage()
            .contains(
                "MDX object '[Measures]' not found in cube 'Warehouse2'"))
        {
          fail(
              "[Measures] should be displayed in 'Warehouse2' cube but they are not! ");
        }
        throw e;
      }

      try {
	  assertThatQuery(connection,
          "SELECT {[Measures].Members} ON COLUMNS FROM [Warehouse1]").returnsGrid(
          "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n"
          + "{[Measures].[Measure1_0]}\n"
          + "{[Measures].[Measure1_1]}\n"
          + "{[Measures].[Fact Count]}\n"
          + "Row #0: 89,043.253\n"
          + "Row #0: 196,770.888\n"
          + "Row #0: 4,070\n");
      } catch (OlapRuntimeException e) {
        if (e.getCause().getLocalizedMessage()
            .contains(
                "MDX object '[Measures]' not found in cube 'Warehouse1'"))
        {
          fail(
              "[Measures] should be displayed in 'Warehouse1' cube but they are not! ");
        }
        throw e;
      }
  }

    @Disabled //TODO need investigate
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier33.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRestrictLevelsAnalyzer3283(Context<?> context) {
        ConnectionProps props =new ConnectionProps(List.of("MR", "DBPentUsers"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = context.getConnection(props);

        final HierarchyAccess hierarchyAccess =
          getHierarchyAccess(connection, "Sales1", "[Customers]");

        assertEquals(2, hierarchyAccess.getTopLevelDepth());
        assertEquals(3, hierarchyAccess.getBottomLevelDepth());
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier34.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRoleMemberAccessNonExistentMemberFails(Context<?> context) {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        // the grant on the non-existent member fails eagerly while the catalog
        // loads for the connection, before any query runs
        Throwable t = assertThrows(Exception.class, () -> assertThatQuery(context.getConnection(props),
            "select {[Store].[Store].Children} on 0 from [Sales]").returnsGrid(""));
        String expected = "Member '[Store].[Store].[USA].[Non Existent]' not found";
        while (t != null && (t.getMessage() == null || !t.getMessage().contains(expected))) {
            t = t.getCause();
        }
        assertNotNull(t, "no exception in the cause chain carries: " + expected);
    }

    @Test
    void testRoleMemberAccess(Context<?> context) {
    	context.getCatalogCache().clear();
        final Connection connection = getRestrictedConnection(context);
        // because CA has access
        assertMemberAccess(connection, AccessMember.CUSTOM, "[Store].[USA]");
        assertMemberAccess(connection, AccessMember.CUSTOM, "[Store].[Mexico]");
        assertMemberAccess(connection, AccessMember.NONE, "[Store].[Mexico].[DF]");
        assertMemberAccess(
            connection, AccessMember.NONE, "[Store].[Mexico].[DF].[Mexico City]");
        assertMemberAccess(connection, AccessMember.NONE, "[Store].[Canada]");
        assertMemberAccess(
            connection, AccessMember.NONE, "[Store].[Canada].[BC].[Vancouver]");
        assertMemberAccess(
            connection, AccessMember.ALL, "[Store].[USA].[CA].[Los Angeles]");
        assertMemberAccess(
            connection, AccessMember.NONE, "[Store].[USA].[CA].[San Diego]");
        // USA deny supercedes OR grant
        assertMemberAccess(
            connection, AccessMember.NONE, "[Store].[USA].[OR].[Portland]");
        assertMemberAccess(
            connection, AccessMember.NONE, "[Store].[USA].[WA].[Seattle]");
        assertMemberAccess(connection, AccessMember.NONE, "[Store].[USA].[WA]");
        // above top level
        assertMemberAccess(connection, AccessMember.NONE, "[Store].[All Stores]");
    }

    private void assertMemberAccess(
        final Connection connection,
        AccessMember expectedAccess,
        String memberName)
    {
        final Role role = connection.getRole(); // restricted
        org.eclipse.daanse.olap.api.element.Catalog schema = connection.getCatalog();
        final boolean fail = true;
        org.eclipse.daanse.olap.api.element.Cube salesCube = schema.lookupCube("Sales").orElseThrow();
        final CatalogReader schemaReader =
            salesCube.getCatalogReader(null).withLocus();
        final org.eclipse.daanse.olap.api.element.Member member =
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(memberName), true);
        final AccessMember actualAccess = role.getAccess(member);
        assertEquals(expectedAccess, actualAccess, memberName);
    }

    private void assertCubeAccess(
        final Connection connection,
        AccessCube expectedAccess,
        String cubeName)
    {
        final Role role = connection.getRole();
        org.eclipse.daanse.olap.api.element.Catalog schema = connection.getCatalog();
        org.eclipse.daanse.olap.api.element.Cube cube = schema.lookupCube(cubeName).orElseThrow();
        final AccessCube actualAccess = role.getAccess(cube);
        assertEquals(expectedAccess, actualAccess, cubeName);
    }

    private void assertHierarchyAccess(
        final Connection connection,
        AccessHierarchy expectedAccess,
        String cubeName,
        String hierarchyName)
    {
        final Role role = connection.getRole();
        org.eclipse.daanse.olap.api.element.Catalog schema = connection.getCatalog();
        final boolean fail = true;
        org.eclipse.daanse.olap.api.element.Cube cube = schema.lookupCube(cubeName).orElseThrow();
        final CatalogReader schemaReader =
            cube.getCatalogReader(null); // unrestricted
        final org.eclipse.daanse.olap.api.element.Hierarchy hierarchy =
            (org.eclipse.daanse.olap.api.element.Hierarchy) schemaReader.lookupCompound(
                cube, Util.parseIdentifier(hierarchyName), fail,
                DataType.HIERARCHY);

        final AccessHierarchy actualAccess = role.getAccess(hierarchy);
        assertEquals(expectedAccess, actualAccess, cubeName);
    }

    private HierarchyAccess getHierarchyAccess(
        final Connection connection,
        String cubeName,
        String hierarchyName)
    {
        final Role role = connection.getRole();
        org.eclipse.daanse.olap.api.element.Catalog schema = connection.getCatalog();
        final boolean fail = true;
        org.eclipse.daanse.olap.api.element.Cube cube = schema.lookupCube(cubeName).orElseThrow();
        final CatalogReader schemaReader =
            cube.getCatalogReader(null); // unrestricted
        final org.eclipse.daanse.olap.api.element.Hierarchy hierarchy =
            (org.eclipse.daanse.olap.api.element.Hierarchy) schemaReader.lookupCompound(
                cube, Util.parseIdentifier(hierarchyName), fail,
                DataType.HIERARCHY);

        return role.getAccessDetails(hierarchy);
    }

    @Test
    void testGrantHierarchy1a(Context<?> foodMartContext) {
        // assert: can access Mexico (explicitly granted)
        // assert: can not access Canada (explicitly denied)
        // assert: can access USA (rule 3 - parent of allowed member San
        // Francisco)
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales",
            "[Store].[Store].level.members").returns(
            "[Store].[Store].[Mexico]\n" + "[Store].[Store].[USA]");
    }

    @Test
    void testGrantHierarchy1aAllMembers(Context<?> foodMartContext) {
        // assert: can access Mexico (explicitly granted)
        // assert: can not access Canada (explicitly denied)
        // assert: can access USA (rule 3 - parent of allowed member San
        // Francisco)
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales",
            "[Store].level.allmembers").returns(
            "[Store].[Store].[Mexico]\n" + "[Store].[Store].[USA]");
    }

    @Test
    void testGrantHierarchy1b(Context<?> foodMartContext) {
        // can access Mexico (explicitly granted) which is the first accessible
        // one
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales",
            "[Store].defaultMember").returns(
            "[Store].[Store].[Mexico]");
    }

    @Test
    void testGrantHierarchy1c(Context<?> foodMartContext) {
        // the root element is All Customers
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales",
            "[Customers].defaultMember").returns(
            "[Customers].[Customers].[Canada].[BC]");
    }

    @Test
    void testGrantHierarchy2(Context<?> foodMartContext) {
        // assert: can access California (parent of allowed member)
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales",
            "[Store].[USA].children").returns(
            "[Store].[Store].[USA].[CA]");
        assertThatAxis(connection, "Sales",
            "[Store].[USA].children").returns(
            "[Store].[Store].[USA].[CA]");
        assertThatAxis(connection, "Sales",
            "[Store].[USA].[CA].children").returns(
            "[Store].[Store].[USA].[CA].[Los Angeles]\n"
            + "[Store].[Store].[USA].[CA].[San Francisco]");
    }

    @Test
    void testGrantHierarchy3(Context<?> foodMartContext) {
        // assert: can not access Washington (child of denied member)
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales", "[Store].[USA].[WA]").throwsMessage("not found");
    }

    @Test
    void testGrantHierarchy4(Context<?> foodMartContext) {
        // assert: can not access Oregon (rule 1 - order matters)
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales",
            "[Store].[USA].[OR].children").throwsMessage("not found");
    }

    @Test
    void testGrantHierarchy5(Context<?> foodMartContext) {
        // assert: can not access All (above top level)
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales", "[Store].[All Stores]").throwsMessage("not found");
        assertThatAxis(connection, "Sales",
            "[Store].members").returns(// note:
                // no: [All Stores] -- above top level
                // no: [Canada] -- not explicitly allowed
                // yes: [Mexico] -- explicitly allowed -- and all its children
                //      except [DF]
                // no: [Mexico].[DF]
                // yes: [USA] -- implicitly allowed
                // yes: [CA] -- implicitly allowed
                // no: [OR], [WA]
                // yes: [San Francisco] -- explicitly allowed
                // no: [San Diego]
            "[Store].[Store].[Mexico]\n"
            + "[Store].[Store].[Mexico].[Guerrero]\n"
            + "[Store].[Store].[Mexico].[Guerrero].[Acapulco]\n"
            + "[Store].[Store].[Mexico].[Guerrero].[Acapulco].[Store 1]\n"
            + "[Store].[Store].[Mexico].[Jalisco]\n"
            + "[Store].[Store].[Mexico].[Jalisco].[Guadalajara]\n"
            + "[Store].[Store].[Mexico].[Jalisco].[Guadalajara].[Store 5]\n"
            + "[Store].[Store].[Mexico].[Veracruz]\n"
            + "[Store].[Store].[Mexico].[Veracruz].[Orizaba]\n"
            + "[Store].[Store].[Mexico].[Veracruz].[Orizaba].[Store 10]\n"
            + "[Store].[Store].[Mexico].[Yucatan]\n"
            + "[Store].[Store].[Mexico].[Yucatan].[Merida]\n"
            + "[Store].[Store].[Mexico].[Yucatan].[Merida].[Store 8]\n"
            + "[Store].[Store].[Mexico].[Zacatecas]\n"
            + "[Store].[Store].[Mexico].[Zacatecas].[Camacho]\n"
            + "[Store].[Store].[Mexico].[Zacatecas].[Camacho].[Store 4]\n"
            + "[Store].[Store].[Mexico].[Zacatecas].[Hidalgo]\n"
            + "[Store].[Store].[Mexico].[Zacatecas].[Hidalgo].[Store 12]\n"
            + "[Store].[Store].[Mexico].[Zacatecas].[Hidalgo].[Store 18]\n"
            + "[Store].[Store].[USA]\n"
            + "[Store].[Store].[USA].[CA]\n"
            + "[Store].[Store].[USA].[CA].[Los Angeles]\n"
            + "[Store].[Store].[USA].[CA].[Los Angeles].[Store 7]\n"
            + "[Store].[Store].[USA].[CA].[San Francisco]\n"
            + "[Store].[Store].[USA].[CA].[San Francisco].[Store 14]");
    }

    @Test
    void testGrantHierarchy6(Context<?> foodMartContext) {
        // assert: parent if at top level is null
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales",
            "[Customers].[USA].[CA].parent").returns(
            "");
    }

    @Test
    void testGrantHierarchy7(Context<?> foodMartContext) {
        // assert: members above top level do not exist
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales",
    		"[Customers].[Canada].children").throwsMessage(
            "MDX object '[Customers].[Canada]' not found in cube 'Sales'");
    }

    @Test
    void testGrantHierarchy8(Context<?> foodMartContext) {
        // assert: can not access Catherine Abel in San Francisco (below bottom
        // level)
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales",
            "[Customers].[USA].[CA].[San Francisco].[Catherine Abel]").throwsMessage(
            "not found");
        assertThatAxis(connection, "Sales",
            "[Customers].[USA].[CA].[San Francisco].children").returns(
            "");
        Axis axis = executeQuery(connection, "select {[Customers].members} on columns from Sales").getAxes()[0];
        // 13 states, 109 cities
        assertEquals(122, axis.getPositions().size());
    }

    @Test
    void testGrantHierarchy8AllMembers(Context<?> foodMartContext) {
        // assert: can not access Catherine Abel in San Francisco (below bottom
        // level)
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatAxis(connection, "Sales",
            "[Customers].[USA].[CA].[San Francisco].[Catherine Abel]").throwsMessage(
            "not found");
        assertThatAxis(connection, "Sales",
            "[Customers].[USA].[CA].[San Francisco].children").returns(
            "");
        Axis axis = executeQuery(connection, "select {[Customers].allmembers} on columns from Sales").getAxes()[0];
        // 13 states, 109 cities
        assertEquals(122, axis.getPositions().size());
    }

    /**
     * Tests for Mondrian BUG 1201 - Native Rollups did not handle
     * access-control with more than one member where granted access=all
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier35.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian_1201_MultipleMembersInRoleAccessControl(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);

        // Must return only 2 [USA].[CA] stores
        assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
            + "  Filter( [Store].[USA].[CA].children,"
            + "          [Measures].[Unit Sales]>0) ON ROWS \n"
            + "from [Sales] \n"
            + "where ([Time].[1997].[Q1].[2])").returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1].[2]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "Row #0: 2,614\n"
            + "Row #1: 187\n");

        // Must return only 2 [USA].[CA] stores
        assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
            + "  TopCount( [Store].[USA].[CA].children, 20,"
            + "            [Measures].[Unit Sales]) ON ROWS \n"
            + "from [Sales] \n"
            + "where ([Time].[1997].[Q1].[2])").returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1].[2]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "Row #0: 2,614\n"
            + "Row #1: 187\n");


        // Partial Rollup: [USA].[CA] rolls up only up to 2.801
        assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
            + "  Filter( [Store].[Store State].Members,"
            + "          [Measures].[Unit Sales]>4000) ON ROWS \n"
            + "from [Sales] \n"
            + "where ([Time].[1997].[Q1].[2])").returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1].[2]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[OR]}\n"
            + "{[Store].[Store].[USA].[WA]}\n"
            + "Row #0: 4,617\n"
            + "Row #1: 10,319\n");

        props =new ConnectionProps(List.of("Role2"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        connection = foodMartContext.getConnection(props);

        // Full Rollup: [USA].[CA] rolls up to 6.021
        assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
            + "  Filter( [Store].[Store State].Members,"
            + "          [Measures].[Unit Sales]>4000) ON ROWS \n"
            + "from [Sales] \n"
            + "where ([Time].[1997].[Q1].[2])").returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1].[2]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[CA]}\n"
            + "{[Store].[Store].[USA].[OR]}\n"
            + "{[Store].[Store].[USA].[WA]}\n"
            + "Row #0: 6,021\n"
            + "Row #1: 4,617\n"
            + "Row #2: 10,319\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier38.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian_2586_RaggedDimMembersShouldBeVisible(Context<?> foodMartContext) {
    //[Geography].[Country]
      ConnectionProps props =new ConnectionProps(List.of("Sales Ragged"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
      Connection connection = foodMartContext.getConnection(props);
      assertThatQuery(connection,
        "select {[Measures].[Unit Sales]} ON COLUMNS, {[Geography].[Country].MEMBERS} ON ROWS from [Sales Ragged]").returnsGrid(
        "Axis #0:\n"
        + "{}\n"
        + "Axis #1:\n"
        + "{[Measures].[Unit Sales]}\n"
        + "Axis #2:\n"
        + "{[Geography].[Geography].[Canada]}\n"
        + "{[Geography].[Geography].[Israel]}\n"
        + "{[Geography].[Geography].[Mexico]}\n"
        + "{[Geography].[Geography].[USA]}\n"
        + "{[Geography].[Geography].[Vatican]}\n"
        + "Row #0: \n"
        + "Row #1: 13,694\n"
        + "Row #2: \n"
        + "Row #3: 217,822\n"
        + "Row #4: 35,257\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier36.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian_1201_CacheAwareOfRoleAccessControl(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);

        // Put query into cache
        assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
            + "  Filter( [Store].[USA].[CA].children,"
            + "          [Measures].[Unit Sales]>0) ON ROWS \n"
            + "from [Sales] \n"
            + "where ([Time].[1997].[Q1].[2])").returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1].[2]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "Row #0: 2,614\n"
            + "Row #1: 187\n");

        props =new ConnectionProps(List.of("Role2"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        connection = foodMartContext.getConnection(props);

        // Run same query using another role with different access controls
        assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
            + "  TopCount( [Store].[USA].[CA].children, 20,"
            + "            [Measures].[Unit Sales]) ON ROWS \n"
            + "from [Sales] \n"
            + "where ([Time].[1997].[Q1].[2])").returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1].[2]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "Row #0: 187\n");
    }

    /**
     * Tests for Mondrian BUG 1127 - Native Top Count was not taking into
     * account user roles
     */
    @Test
    void testBugMondrian1127OneSlicerOnly(Context<?> foodMartContext) {
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
            + "  TopCount([Store].[USA].[CA].Children, 10,"
            + "           [Measures].[Unit Sales]) ON ROWS \n"
            + "from [Sales] \n"
            + "where ([Time].[1997].[Q1].[2])").returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1].[2]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "Row #0: 2,614\n"
            + "Row #1: 187\n");

        assertThatQuery(foodMartContext.getConnectionWithDefaultRole(),
            "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
            + "  NON EMPTY TopCount([Store].[USA].[CA].Children, 10, "
            + "           [Measures].[Unit Sales]) ON ROWS \n"
            + "from [Sales] \n"
            + "where ([Time].[1997].[Q1].[2])").returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1].[2]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "Row #0: 2,614\n"
            + "Row #1: 1,879\n"
            + "Row #2: 1,341\n"
            + "Row #3: 187\n");
    }


    @Test
    void testBugMondrian1127MultipleSlicers(Context<?> foodMartContext) {
        Connection connection = getRestrictedConnection(foodMartContext);
        assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
            + "  TopCount([Store].[USA].[CA].Children, 10,"
            + "           [Measures].[Unit Sales]) ON ROWS \n"
            + "from [Sales] \n"
            + "where ([Time].[1997].[Q1].[2] : [Time].[1997].[Q1].[3])").returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1].[2]}\n"
            + "{[Time].[Time].[1997].[Q1].[3]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "Row #0: 4,497\n"
            + "Row #1: 337\n");

        assertThatQuery(foodMartContext.getConnectionWithDefaultRole(),
            "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
            + "  NON EMPTY TopCount([Store].[USA].[CA].Children, 10, "
            + "           [Measures].[Unit Sales]) ON ROWS \n"
            + "from [Sales] \n"
            + "where ([Time].[1997].[Q1].[2] : [Time].[1997].[Q1].[3])").returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997].[Q1].[2]}\n"
            + "{[Time].[Time].[1997].[Q1].[3]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "Row #0: 4,497\n"
            + "Row #1: 4,094\n"
            + "Row #2: 2,585\n"
            + "Row #3: 337\n");
    }

    /**
     * Tests that we only aggregate over SF, LA, even when called from
     * functions.
     */
    @Test
    void testGrantHierarchy9(Context<?> foodMartContext) {
        // Analysis services doesn't allow aggregation within calculated
        // measures, so use the following query to generate the results:
        //
        //   with member [Store].[SF LA] as
        //     'Aggregate({[USA].[CA].[San Francisco], [Store].[USA].[CA].[Los
        //     Angeles]})'
        //   select {[Measures].[Unit Sales]} on columns,
        //    {[Gender].children} on rows
        //   from Sales
        //   where ([Marital Status].[S], [Store].[SF LA])
    	final Connection connection = getRestrictedConnection(foodMartContext);
    	//Connection connection = foodMartContext.getConnection();
    	assertThatQuery(connection,
            "with member [Measures].[California Unit Sales] as "
            + " 'Aggregate({[Store].[USA].[CA].children}, [Measures].[Unit Sales])'\n"
            + "select {[Measures].[California Unit Sales]} on columns,\n"
            + " {[Gender].children} on rows\n"
            + "from Sales\n"
            + "where ([Marital Status].[S])").returnsGrid(
            "Axis #0:\n"
            + "{[Marital Status].[Marital Status].[S]}\n"
            + "Axis #1:\n"
            + "{[Measures].[California Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Gender].[Gender].[F]}\n"
            + "{[Gender].[Gender].[M]}\n"
            + "Row #0: 6,636\n"
            + "Row #1: 7,329\n");
    }

    @Test
    void testGrantHierarchyA(Context<?> foodMartContext) {
    	final Connection connection = getRestrictedConnection(foodMartContext);
        // assert: totals for USA include missing cells
    	assertThatQuery(connection,
            "select {[Unit Sales]} on columns,\n"
            + "{[Store].[USA], [Store].[USA].children} on rows\n"
            + "from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA]}\n"
            + "{[Store].[Store].[USA].[CA]}\n"
            + "Row #0: 266,773\n"
            + "Row #1: 74,748\n");
    }

    @Test
    public void _testSharedObjectsInGrantMappingsBug(Context<?> foodMartContext) {
        boolean mustGet = true;
        Connection connection = foodMartContext.getConnectionWithDefaultRole();
        org.eclipse.daanse.olap.api.element.Catalog schema = connection.getCatalog();
        org.eclipse.daanse.olap.api.element.Cube salesCube = schema.lookupCube("Sales").orElseThrow();
        org.eclipse.daanse.olap.api.element.Cube warehouseCube = schema.lookupCube("Warehouse").orElseThrow();
        Hierarchy measuresInSales = salesCube.lookupHierarchy(
            new IdImpl.NameSegmentImpl("Measures", Quoting.UNQUOTED), false);
        Hierarchy storeInWarehouse = warehouseCube.lookupHierarchy(
            new IdImpl.NameSegmentImpl("Store", Quoting.UNQUOTED), false);

        RoleImpl role = new RoleImpl();
        role.grant(schema, AccessCatalog.NONE);
        role.grant(salesCube, AccessCube.NONE);
        // For using hierarchy Measures in #assertExprThrows
        org.eclipse.daanse.olap.api.access.RollupPolicy rollupPolicy = org.eclipse.daanse.olap.api.access.RollupPolicy.FULL;
        role.grant(
            measuresInSales, AccessHierarchy.ALL, null, null, rollupPolicy);
        role.grant(warehouseCube, AccessCube.NONE);
        role.grant(storeInWarehouse.getDimension(), AccessDimension.ALL);

        role.makeImmutable();
        connection.setRole(role);

        assertThatExpr(connection, "Sales",
            "[Store].DefaultMember").throwsMessage(
            "cube 'Sales' not found");
    }

    @Test
    void testNoAccessToCube(Context<?> foodMartContext) {
        final Connection connection = getRestrictedConnection(foodMartContext);
        assertThatQuery(connection, "select from [HR]").throwsMessage("MDX cube 'HR' not found");
    }

    private Connection getRestrictedConnection(Context<?> foodMartContext) {
        return getRestrictedConnection(foodMartContext, true);
    }

    /**
     * Returns a connection with limited access to the schema.
     *
     * @param restrictCustomers true to restrict access to the customers
     * dimension. This will change the defaultMember of the dimension,
     * all cell values will be null because there are no sales data
     * for Canada
     *
     * @return restricted connection
     */
    private Connection getRestrictedConnection(Context<?> foodMartContext, boolean restrictCustomers) {
        Connection connection = foodMartContext.getConnectionWithDefaultRole();
        RoleImpl role = new RoleImpl();
        org.eclipse.daanse.olap.api.element.Catalog schema = connection.getCatalog();
        final boolean fail = true;
        org.eclipse.daanse.olap.api.element.Cube salesCube = schema.lookupCube("Sales").orElseThrow();
        final CatalogReader schemaReader =
            salesCube.getCatalogReader(null).withLocus();
        Hierarchy storeHierarchy = salesCube.lookupHierarchy(
            new IdImpl.NameSegmentImpl("Store", Quoting.UNQUOTED), false);
        role.grant(schema, AccessCatalog.ALL_DIMENSIONS);
        role.grant(salesCube, AccessCube.ALL);
        Level nationLevel =
            Util.lookupHierarchyLevel(storeHierarchy, "Store Country");
        org.eclipse.daanse.olap.api.access.RollupPolicy rollupPolicy = org.eclipse.daanse.olap.api.access.RollupPolicy.FULL;
        role.grant(
            storeHierarchy, AccessHierarchy.CUSTOM, nationLevel, null, rollupPolicy);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier("[Store].[All Stores].[USA].[OR]"), fail),
            AccessMember.ALL);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier("[Store].[All Stores].[USA]"), fail),
            AccessMember.CUSTOM);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(
                    "[Store].[All Stores].[USA].[CA].[San Francisco]"), fail),
            AccessMember.ALL);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(
                    "[Store].[All Stores].[USA].[CA].[Los Angeles]"), fail),
            AccessMember.ALL);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(
                    "[Store].[All Stores].[Mexico]"), fail),
            AccessMember.ALL);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(
                    "[Store].[All Stores].[Mexico].[DF]"), fail),
            AccessMember.NONE);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(
                    "[Store].[All Stores].[Canada]"), fail),
            AccessMember.NONE);
        if (restrictCustomers) {
            Hierarchy customersHierarchy =
                salesCube.lookupHierarchy(
                    new IdImpl.NameSegmentImpl("Customers", Quoting.UNQUOTED),
                    false);
            Level stateProvinceLevel =
                Util.lookupHierarchyLevel(customersHierarchy, "State Province");
            Level customersCityLevel =
                Util.lookupHierarchyLevel(customersHierarchy, "City");
            role.grant(
                customersHierarchy,
                AccessHierarchy.CUSTOM,
                stateProvinceLevel,
                customersCityLevel,
                rollupPolicy);
        }

        // No access to HR cube.
        Cube hrCube = schema.lookupCube("HR").orElseThrow();
        role.grant(hrCube, AccessCube.NONE);

        role.makeImmutable();
        connection.setRole(role);
        return connection;
    }

    /**
     * Basic test of partial rollup policy. [USA] = [OR] + [WA], not
     * the usual [CA] + [OR] + [WA].
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier37.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRollupPolicyBasic(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);
        assertThatQuery(connection,
            "select {[Store].[USA], [Store].[USA].Children} on 0\n"
            + "from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store].[Store].[USA]}\n"
            + "{[Store].[Store].[USA].[OR]}\n"
            + "{[Store].[Store].[USA].[WA]}\n"
            + "Row #0: 192,025\n"
            + "Row #0: 67,659\n"
            + "Row #0: 124,366\n");
    }

    /**
     * The total for [Store].[All Stores] is similarly reduced. All
     * children of [All Stores] are visible, but one grandchild is not.
     * Normally the total is 266,773.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier37.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRollupPolicyAll(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);
        assertThatExpr(connection, "Sales",
            "([Store].[All Stores])").returns(
            "192,025");
    }

    /**
     * Access [Store].[All Stores] implicitly as it is the default member
     * of the [Stores] hierarchy.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier37.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRollupPolicyAllAsDefault(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);
        assertThatExpr(connection, "Sales",
            "([Store])").returns(
            "192,025");
    }

    /**
     * Access [Store].[All Stores] via the Parent relationship (to check
     * that this doesn't circumvent access control).
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier37.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRollupPolicyAllAsParent(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);
        assertThatExpr(connection, "Sales",
            "([Store].[USA].Parent)").returns(
            "192,025");
    }

    /**
     * Tests that an access-controlled dimension affects results even if not
     * used in the query. Unit test for
     * <a href="http://jira.pentaho.com/browse/mondrian-1283">MONDRIAN-1283,
     * "Mondrian doesn't restrict dimension members when dimension isn't
     * included"</a>.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier37.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testUnusedAccessControlledDimension(Context<?> foodMartContext) {
        Connection connection = foodMartContext.getConnectionWithDefaultRole();
        assertThatQuery(connection,
            "select [Gender].Children on 0 from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Gender].[Gender].[F]}\n"
            + "{[Gender].[Gender].[M]}\n"
            + "Row #0: 131,558\n"
            + "Row #0: 135,215\n");

        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        connection = foodMartContext.getConnection(props);
        assertThatQuery(connection,
            "select [Gender].Children on 0 from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Gender].[Gender].[F]}\n"
            + "{[Gender].[Gender].[M]}\n"
            + "Row #0: 94,799\n"
            + "Row #0: 97,226\n");
    }

    /**
     * Tests that members below bottom level are regarded as visible.
     *
     * <p>The original test called this three times within one method,
     * resetting the catalog and supplying a different {@code RollupPolicy}
     * each time -- the new testkit builds the catalog once per test, so each
     * policy is now its own test method backed by its own instance in
     * {@link AccessControlRollupInstances}.
     */
    @Test
    @RolapContextTest(value = AccessControlRollupInstances.RollupBottomLevelFull.class)
    void testRollupBottomLevelFull(Context<?> context) {
        rollupPolicyBottom(context, "74,748", "36,759", "266,773");
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.RollupBottomLevelPartial.class)
    void testRollupBottomLevelPartial(Context<?> context) {
        rollupPolicyBottom(context, "72,739", "35,775", "264,764");
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.RollupBottomLevelHidden.class)
    void testRollupBottomLevelHidden(Context<?> context) {
        rollupPolicyBottom(context, "", "", "");
    }

    private void rollupPolicyBottom(
		Context<?> context,
        String v1,
        String v2,
        String v3)
    {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = context.getConnection(props);
        // All of the children of [San Francisco] are invisible, because [City]
        // is the bottom level, but that shouldn't affect the total.
    	assertThatExpr(connection, "Sales",
            "([Customers].[USA].[CA].[San Francisco])").returns( "88");
    	assertThatExpr(connection, "Sales",
			"([Customers].[USA].[CA].[Los Angeles])").throwsMessage(
            "MDX object '[Customers].[USA].[CA].[Los Angeles]' not found in cube 'Sales'");

    	assertThatExpr(connection, "Sales", "([Customers].[USA].[CA])").returns(v1);
    	assertThatExpr(connection, "Sales",
            "([Customers].[USA].[CA], [Gender].[F])").returns(v2);
    	assertThatExpr(connection, "Sales", "([Customers].[USA])").returns(v3);

    	checkQuery(
    			connection,
            "select [Customers].Children on 0, "
            + "[Gender].Members on 1 from [Sales]");
    }

    /**
     * Calls various {@link CatalogReader} methods on the members returned in
     * a result set.
     *
     * @param connection connection
     * @param mdx MDX query
     */
    private void checkQuery(Connection connection, String mdx) {
        Result result = executeQuery(connection, mdx);
        final CatalogReader schemaReader =
        		connection.getCatalogReader().withLocus();
        for (Axis axis : result.getAxes()) {
            for (Position position : axis.getPositions()) {
                for (Member member : position) {
                    final Member accessControlledParent =
                        schemaReader.getMemberParent(member);
                    if (member.getParentMember() == null) {
                        assertNull(accessControlledParent);
                    }
                    final List<Member> accessControlledChildren =
                        schemaReader.getMemberChildren(member);
                    assertNotNull(accessControlledChildren);
                }
            }
        }
    }

    /**
     * Tests that a bad value for the rollupPolicy attribute gives the
     * appropriate error.
     */
    @Disabled //TODO need investigate
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier1.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRollupPolicyNegative(Context<?> foodMartContext) {
//    	String schema = SchemaUtil.getSchema(baseSchema,
//                null, null, null, null, null,
//                "<Role name=\"Role1\">\n"
//                + "  <SchemaGrant access=\"none\">\n"
//                + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
//                + "      <HierarchyGrant hierarchy=\"[Customers]\" access=\"custom\" rollupPolicy=\"bad\" bottomLevel=\"[Customers].[City]\">\n"
//                + "        <MemberGrant member=\"[Customers].[USA]\" access=\"all\"/>\n"
//                + "        <MemberGrant member=\"[Customers].[USA].[CA].[Los Angeles]\" access=\"none\"/>\n"
//                + "      </HierarchyGrant>\n"
//                + "    </CubeGrant>\n"
//                + "  </SchemaGrant>\n"
//                + "</Role>");
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        assertThatQuery(foodMartContext, props, "select from [Sales]")
            .throwsMessage("Illegal rollupPolicy value 'bad'");
    }

    /**
     * Tests where all children are visible but a grandchild is not.
     *
     * <p>Split into one test method per {@code RollupPolicy}, same reasoning
     * as {@link #testRollupBottomLevelFull}.
     */
    @Test
    @RolapContextTest(value = AccessControlRollupInstances.GreatGrandchildInvisibleFull.class)
    void testRollupPolicyGreatGrandchildInvisibleFull(Context<?> context) {
        rollupPolicyGreatGrandchildInvisible(context, "266,773", "74,748");
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.GreatGrandchildInvisiblePartial.class)
    void testRollupPolicyGreatGrandchildInvisiblePartial(Context<?> context) {
        rollupPolicyGreatGrandchildInvisible(context, "266,767", "74,742");
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.GreatGrandchildInvisibleHidden.class)
    void testRollupPolicyGreatGrandchildInvisibleHidden(Context<?> context) {
        rollupPolicyGreatGrandchildInvisible(context, "", "");
    }

    private void rollupPolicyGreatGrandchildInvisible(
		Context<?> context,
        String v1,
        String v2)
    {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = context.getConnection(props);
    	assertThatExpr(connection, "Sales", "[Measures].[Unit Sales]").returns(v1);
    	assertThatExpr(connection, "Sales", "([Measures].[Unit Sales], [Customers].[USA])").returns(v1);
    	assertThatExpr(connection, "Sales",
            "([Measures].[Unit Sales], [Customers].[USA].[CA])").returns(v2);
    }

    /**
     * Tests where two hierarchies are simultaneously access-controlled.
     *
     * <p>Note that v2 is different for full vs partial, v3 is the same.
     * Split into one test method per {@code RollupPolicy}, same reasoning
     * as {@link #testRollupBottomLevelFull}.
     */
    @Test
    @RolapContextTest(value = AccessControlRollupInstances.SimultaneousFull.class)
    void testRollupPolicySimultaneousFull(Context<?> foodMartContext) {
        rollupPolicySimultaneous(foodMartContext, "266,773", "74,748", "25,635");
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.SimultaneousPartial.class)
    void testRollupPolicySimultaneousPartial(Context<?> foodMartContext) {
        rollupPolicySimultaneous(foodMartContext, "72,631", "72,631", "25,635");
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.SimultaneousHidden.class)
    void testRollupPolicySimultaneousHidden(Context<?> foodMartContext) {
        rollupPolicySimultaneous(foodMartContext, "", "", "");
    }

    private void rollupPolicySimultaneous(
		Context<?> context,
        String v1,
        String v2,
        String v3)
    {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = context.getConnection(props);
    	assertThatExpr(connection, "Sales", "[Measures].[Unit Sales]").returns(v1);
    	assertThatExpr(connection, "Sales",
            "([Measures].[Unit Sales], [Customers].[USA])").returns(v1);
    	assertThatExpr(connection, "Sales",
            "([Measures].[Unit Sales], [Customers].[USA].[CA])").returns(v2);
    	assertThatExpr(connection, "Sales",
            "([Measures].[Unit Sales], "
            + "[Customers].[USA].[CA], [Store].[USA].[CA])").returns(v2);
    	assertThatExpr(connection, "Sales",
            "([Measures].[Unit Sales], "
            + "[Customers].[USA].[CA], "
            + "[Store].[USA].[CA].[San Diego])").returns(v3);
    }

    // todo: performance test where 1 of 1000 children is not visible

    @Disabled //TODO need investigate
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier2.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testUnionRole(Context<?> foodMartContext) {
        Connection connection;

        try {
            ConnectionProps props =new ConnectionProps(List.of("Role3", "Role2"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        	connection = foodMartContext.getConnection(props);
        	fail("expected exception, got " + connection);
        } catch (RuntimeException e) {
            final String message = e.getMessage();
            assertTrue(message.indexOf("Role 'Role3' not found") >= 0, message);
        }

        try {
            ConnectionProps props =new ConnectionProps(List.of("Role1", "Role3"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        	connection = foodMartContext.getConnection(props);
            fail("expected exception, got " + connection);
        } catch (RuntimeException e) {
            final String message = e.getMessage();
            assertTrue(message.indexOf("Role 'Role3' not found") >= 0, message);
        }

        ConnectionProps props =new ConnectionProps(List.of("Role1", "Role2"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	connection = foodMartContext.getConnection(props);

        // Cube access:
        // Both can see [Sales]
        // Role1 only see [Warehouse]
        // Neither can see [Warehouse and Sales]
        assertCubeAccess(connection, AccessCube.ALL, "Sales");
        assertCubeAccess(connection, AccessCube.ALL, "Warehouse");
        assertCubeAccess(connection, AccessCube.NONE, "Warehouse and Sales");

        // Hierarchy access:
        // Both can see [Customers] with Custom access
        // Both can see [Store], Role1 with Custom access, Role2 with All access
        // Role1 can see [Promotion Media], Role2 cannot
        // Neither can see [Marital Status]
        assertHierarchyAccess(
            connection, AccessHierarchy.CUSTOM, "Sales", "[Customers]");
        assertHierarchyAccess(
            connection, AccessHierarchy.ALL, "Sales", "[Store]");
        assertHierarchyAccess(
            connection, AccessHierarchy.ALL, "Sales", "[Promotion Media]");
        assertHierarchyAccess(
            connection, AccessHierarchy.NONE, "Sales", "[Marital Status]");

        // Rollup policy is the greater of Role1's partian and Role2's hidden
        final HierarchyAccess hierarchyAccess =
            getHierarchyAccess(connection, "Sales", "[Store]");
        assertEquals(
            RollupPolicy.PARTIAL,
            hierarchyAccess.getRollupPolicy());
        // One of the roles is restricting the levels, so we
        // expect only the levels from 2 to 4 to be available.
        assertEquals(2, hierarchyAccess.getTopLevelDepth());
        assertEquals(4, hierarchyAccess.getBottomLevelDepth());

        // Member access:
        // both can see [USA]
        assertMemberAccess(connection, AccessMember.CUSTOM, "[Customers].[USA]");
        // Role1 can see [CA], Role2 cannot
        assertMemberAccess(connection, AccessMember.CUSTOM, "[Customers].[USA].[CA]");
        // Role1 cannoy see [USA].[OR].[Portland], Role2 can
        assertMemberAccess(
            connection, AccessMember.ALL, "[Customers].[USA].[OR].[Portland]");
        // Role1 cannot see [USA].[OR], Role2 can see it by virtue of [Portland]
        assertMemberAccess(
            connection, AccessMember.CUSTOM, "[Customers].[USA].[OR]");
        // Neither can see Beaverton
        assertMemberAccess(
            connection, AccessMember.NONE, "[Customers].[USA].[OR].[Beaverton]");

        // Rollup policy
        String mdx = "select Hierarchize(\n"
            + "{[Customers].[USA].Children,\n"
            + " [Customers].[USA].[OR].Children}) on 0\n"
            + "from [Sales]";
        connection = foodMartContext.getConnectionWithDefaultRole();
        assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Customers].[USA].[CA]}\n"
            + "{[Customers].[USA].[OR]}\n"
            + "{[Customers].[USA].[OR].[Albany]}\n"
            + "{[Customers].[USA].[OR].[Beaverton]}\n"
            + "{[Customers].[USA].[OR].[Corvallis]}\n"
            + "{[Customers].[USA].[OR].[Lake Oswego]}\n"
            + "{[Customers].[USA].[OR].[Lebanon]}\n"
            + "{[Customers].[USA].[OR].[Milwaukie]}\n"
            + "{[Customers].[USA].[OR].[Oregon City]}\n"
            + "{[Customers].[USA].[OR].[Portland]}\n"
            + "{[Customers].[USA].[OR].[Salem]}\n"
            + "{[Customers].[USA].[OR].[W. Linn]}\n"
            + "{[Customers].[USA].[OR].[Woodburn]}\n"
            + "{[Customers].[USA].[WA]}\n"
            + "Row #0: 74,748\n"
            + "Row #0: 67,659\n"
            + "Row #0: 6,806\n"
            + "Row #0: 4,558\n"
            + "Row #0: 9,539\n"
            + "Row #0: 4,910\n"
            + "Row #0: 9,596\n"
            + "Row #0: 5,145\n"
            + "Row #0: 3,708\n"
            + "Row #0: 3,583\n"
            + "Row #0: 7,678\n"
            + "Row #0: 4,175\n"
            + "Row #0: 7,961\n"
            + "Row #0: 124,366\n");

        props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	connection = foodMartContext.getConnection(props);
    	assertThatQuery(connection, mdx).throwsMessage(
            "MDX object '[Customers].[USA].[OR]' not found in cube 'Sales'");

        props =new ConnectionProps(List.of("Role2"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	connection = foodMartContext.getConnection(props);
    	assertThatQuery(connection, mdx).throwsMessage(
            "MDX cube 'Sales' not found");

        // Compared to above:
        // a. cities in Oregon are missing besides Portland
        // b. total for Oregon = total for Portland
        props =new ConnectionProps(List.of("Role1", "Role2"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	connection = foodMartContext.getConnection(props);
    	assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Customers].[USA].[CA]}\n"
            + "{[Customers].[USA].[OR]}\n"
            + "{[Customers].[USA].[OR].[Portland]}\n"
            + "{[Customers].[USA].[WA]}\n"
            + "Row #0: 74,742\n"
            + "Row #0: 3,583\n"
            + "Row #0: 3,583\n"
            + "Row #0: 124,366\n");
        checkQuery(connection, mdx);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier3.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testUnionOfUnionRole(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("grandparent of USA manager"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);

        // Can access [Sales]?
        assertCubeAccess(connection, AccessCube.ALL, "Sales");

        // Has custom access to [Customers]?
        assertHierarchyAccess
                (connection, AccessHierarchy.CUSTOM, "Sales", "[Customers]");

        final HierarchyAccess hierarchyAccess =
                getHierarchyAccess(connection, "Sales", "[Customers]");

        // Can access all levels of the [Customers] hierarchy?
        assertEquals(0, hierarchyAccess.getTopLevelDepth());
        assertEquals(4, hierarchyAccess.getBottomLevelDepth());

        // Can see [USA]?
        assertMemberAccess(connection, AccessMember.ALL, "[Customers].[USA]");

        // Cannot see [Mexico]?
        assertMemberAccess(connection, AccessMember.NONE, "[Customers].[Mexico]");
    }

    /**
     * This is a test for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1384">MONDRIAN-1384</a>
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier4.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testUnionRoleHasInaccessibleDescendants(Context<?> foodMartContext) throws Exception {
        ConnectionProps props =new ConnectionProps(List.of("Role1","Role2"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnection(props);
        final Cube cube =
            connection.getCatalog()
                .lookupCube("Sales").orElseThrow();
        final HierarchyAccess accessDetails =
            connection.getRole().getAccessDetails(
                cube.lookupHierarchy(
                    new IdImpl.NameSegmentImpl("Customers", Quoting.UNQUOTED),
                    false));
        final CatalogReader scr =
            cube.getCatalogReader(null).withLocus();
        assertEquals(
            true,
            accessDetails.hasInaccessibleDescendants(
                scr.getMemberByUniqueName(
                    Util.parseIdentifier("[Customers].[USA]"),
                    true)));
    }

    /**
     * This is a test for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1168">MONDRIAN-1168</a>
     * Union of roles would sometimes return levels which should be restricted
     * by ACL.
     */
    @Disabled //TODO need investigate
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier5.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testRoleUnionWithLevelRestrictions(Context<?> foodMartContext)  throws Exception {
        ConnectionProps props =new ConnectionProps(List.of("Role1","Role2"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnection(props);

    	assertThatQuery(connection,
            "select {[Customers].[State Province].Members} on columns from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Customers].[USA].[CA]}\n"
            + "Row #0: 74,748\n");

    	assertThatQuery(connection,
            "select {[Customers].[Country].Members} on columns from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n");

        CatalogReader reader =
        		connection.getCatalogReader().withLocus();
        Cube cube = null;
        for (Cube c : reader.getCubes()) {
            if (c.getName().equals("Sales")) {
                cube = c;
            }
        }
        assertNotNull(cube);
        reader =
            cube.getCatalogReader(connection.getRole());
        final List<Dimension> dimensions =
            reader.getCubeDimensions(cube);
        Dimension dimension = null;
        for (Dimension dim : dimensions) {
            if (dim.getName().equals("Customers")) {
                dimension = dim;
            }
        }
        assertNotNull(dimension);
        Hierarchy hierarchy =
            reader.getDimensionHierarchies(dimension).get(0);
        assertNotNull(hierarchy);
        final List<Level> levels =
            reader.getHierarchyLevels(hierarchy);

        // Do some tests
        assertEquals(1, levels.size());
        assertEquals(
            2,
            connection
                .getRole().getAccessDetails(hierarchy)
                    .getBottomLevelDepth());
        assertEquals(
            2,
            connection
                .getRole().getAccessDetails(hierarchy)
                    .getTopLevelDepth());
    }

    /**
     * Test to verify that non empty crossjoins enforce role access.
     * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-369">
     * MONDRIAN-369, "Non Empty Crossjoin fails to enforce role access".
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier6.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNonEmptyAccess(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnection(props);

        // regular crossjoin returns the correct list of product children
        final String expected =
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Gender].[Gender].[All Gender], [Product].[Product].[Drink]}\n"
            + "Row #0: 24,597\n";

        final String mdx =
            "select {[Measures].[Unit Sales]} ON COLUMNS, "
            + " Crossjoin({[Gender].[All Gender]}, "
            + "[Product].Children) ON ROWS "
            + "from [Sales]";
        assertThatQuery(connection,mdx).returnsGrid(expected);
        checkQuery(connection, mdx);

        // with bug MONDRIAN-397, non empty crossjoin did not return the correct
        // list
        final String mdx2 =
            "select {[Measures].[Unit Sales]} ON COLUMNS, "
            + "NON EMPTY Crossjoin({[Gender].[All Gender]}, "
            + "[Product].[All Products].Children) ON ROWS "
            + "from [Sales]";
        assertThatQuery(connection,mdx2).returnsGrid(expected);
        checkQuery(connection, mdx2);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier6.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testNonEmptyAccessLevelMembers(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnection(props);

        // <Level>.members inside regular crossjoin returns the correct list of
        // product members
        final String expected =
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Gender].[Gender].[All Gender], [Product].[Product].[Drink]}\n"
            + "Row #0: 24,597\n";

        final String mdx =
            "select {[Measures].[Unit Sales]} ON COLUMNS, "
            + " Crossjoin({[Gender].[All Gender]}, "
            + "[Product].[Product Family].Members) ON ROWS "
            + "from [Sales]";
        assertThatQuery(connection, mdx).returnsGrid(expected);
        checkQuery(connection, mdx);

        // with bug MONDRIAN-397, <Level>.members inside non empty crossjoin did
        // not return the correct list
        final String mdx2 =
            "select {[Measures].[Unit Sales]} ON COLUMNS, "
            + "NON EMPTY Crossjoin({[Gender].[All Gender]}, "
            + "[Product].[Product Family].Members) ON ROWS "
            + "from [Sales]";
        assertThatQuery(connection, mdx2).returnsGrid(expected);
        checkQuery(connection, mdx2);
    }

    private static final String GOODMAN_QUERY = "select {[Measures].[Unit Sales]} ON COLUMNS,\n"
        + "Hierarchize(Union(Union(Union({[Store].[All Stores]},"
        + " [Store].[All Stores].Children),"
        + " [Store].[All Stores].[USA].Children),"
        + " [Store].[All Stores].[USA].[CA].Children)) ON ROWS\n"
        + "from [Sales]\n"
        + "where [Time].[1997]";

    /**
     * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-406">
     * MONDRIAN-406, "Rollup policy doesn't work for members
     * that are implicitly visible"</a>.
     *
     * <p>Split into one test method per {@code RollupPolicy}, same reasoning
     * as {@link #testRollupBottomLevelFull}.
     */
    @Test
    @RolapContextTest(value = AccessControlRollupInstances.GoodmanPartial.class)
    void testGoodmanPartial(Context<?> foodMartContext) {
        // Note that total for [Store].[All Stores] and [Store].[USA] is sum
        // of visible children [Store].[CA] and [Store].[OR].[Portland].
        ConnectionProps props =new ConnectionProps(List.of("California manager"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);
        assertThatQuery(connection,
            GOODMAN_QUERY).returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[All Stores]}\n"
            + "{[Store].[Store].[USA]}\n"
            + "{[Store].[Store].[USA].[CA]}\n"
            + "{[Store].[Store].[USA].[CA].[Alameda]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[Store].[USA].[OR]}\n"
            + "Row #0: 100,827\n"
            + "Row #1: 100,827\n"
            + "Row #2: 74,748\n"
            + "Row #3: \n"
            + "Row #4: 21,333\n"
            + "Row #5: 25,663\n"
            + "Row #6: 25,635\n"
            + "Row #7: 2,117\n"
            + "Row #8: 26,079\n");
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.GoodmanFull.class)
    void testGoodmanFull(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("California manager"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);
        assertThatQuery(connection,
            GOODMAN_QUERY).returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[All Stores]}\n"
            + "{[Store].[Store].[USA]}\n"
            + "{[Store].[Store].[USA].[CA]}\n"
            + "{[Store].[Store].[USA].[CA].[Alameda]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[Store].[USA].[OR]}\n"
            + "Row #0: 266,773\n"
            + "Row #1: 266,773\n"
            + "Row #2: 74,748\n"
            + "Row #3: \n"
            + "Row #4: 21,333\n"
            + "Row #5: 25,663\n"
            + "Row #6: 25,635\n"
            + "Row #7: 2,117\n"
            + "Row #8: 67,659\n");
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.GoodmanHidden.class)
    void testGoodmanHidden(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("California manager"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);
        assertThatQuery(connection,
            GOODMAN_QUERY).returnsGrid(
            "Axis #0:\n"
            + "{[Time].[Time].[1997]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[All Stores]}\n"
            + "{[Store].[Store].[USA]}\n"
            + "{[Store].[Store].[USA].[CA]}\n"
            + "{[Store].[Store].[USA].[CA].[Alameda]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[Store].[USA].[OR]}\n"
            + "Row #0: \n"
            + "Row #1: \n"
            + "Row #2: 74,748\n"
            + "Row #3: \n"
            + "Row #4: 21,333\n"
            + "Row #5: 25,663\n"
            + "Row #6: 25,635\n"
            + "Row #7: 2,117\n"
            + "Row #8: \n");
        checkQuery(connection, GOODMAN_QUERY);
    }

    /**
     * Test case for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-402">
     * MONDRIAN-402, "Bug in RolapCubeHierarchy.hashCode() ?"</a>.
     * Access-control elements for hierarchies with
     * same name in different cubes could not be distinguished.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier7.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian402(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("California manager"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnection(props);
        assertHierarchyAccess(
    		connection, AccessHierarchy.NONE, "Sales", "Store");
        assertHierarchyAccess(
    		connection,
            AccessHierarchy.CUSTOM,
            "Sales Ragged",
            "Store");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier8.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testPartialRollupParentChildHierarchy(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("Buggy Role"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnection(props);

        final String mdx = "select\n"
            + "  {[Measures].[Number of Employees]} on columns,\n"
            + "  {[Store]} on rows\n"
            + "from HR";
        assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Number of Employees]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[All Stores]}\n"
            + "Row #0: 1\n");
        checkQuery(connection, mdx);

        final String mdx2 = "select\n"
            + "  {[Measures].[Number of Employees]} on columns,\n"
            + "  {[Employees]} on rows\n"
            + "from HR";
        assertThatQuery(connection,
            mdx2).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Number of Employees]}\n"
            + "Axis #2:\n"
            + "{[Employees].[Employees].[All Employees]}\n"
            + "Row #0: 1\n");
        checkQuery(connection, mdx2);
    }

    @Test
    void testParentChildUserDefinedRole(Context<?> foodMartContext)
    {
        final Connection connection = foodMartContext.getConnectionWithDefaultRole();
        final org.eclipse.daanse.olap.api.access.Role savedRole = connection.getRole();
        try {
            // Run queries as top-level employee.
            connection.setRole(
                new PeopleRole(
                    savedRole, connection.getCatalog(), "Sheri Nowmer"));
            assertThatExpr(connection,
        		"HR",
                "[Employees].Members.Count").returns(
                "1,156");

            // Level 2 employee
            connection.setRole(
                new PeopleRole(
                    savedRole, connection.getCatalog(), "Derrick Whelply"));
            assertThatExpr(connection,
        		"HR",
                "[Employees].Members.Count").returns(
                "605");
            assertThatAxis(connection,
        		"HR",
                "Head([Employees].Members, 4),"
                + "Tail([Employees].Members, 2)").returns(
                "[Employees].[Employees].[All Employees]\n"
                + "[Employees].[Employees].[Sheri Nowmer]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Beverly Baker]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Ed Young].[Gregory Whiting].[Merrill Steel]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Ed Young].[Gregory Whiting].[Melissa Marple]");

            // Leaf employee
            connection.setRole(
                new PeopleRole(
                    savedRole, connection.getCatalog(), "Ann Weyerhaeuser"));
            assertThatExpr(connection,
        		"HR",
                "[Employees].[Employees].Members.Count").returns(
                "7");
            assertThatAxis(connection,
        		"HR",
                "[Employees].Members").returns(
                "[Employees].[Employees].[All Employees]\n"
                + "[Employees].[Employees].[Sheri Nowmer]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Cody Goldey]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Cody Goldey].[Shanay Steelman]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Cody Goldey].[Shanay Steelman].[Ann Weyerhaeuser]");
        } finally {
            connection.setRole(savedRole);
        }
    }

    /**
     * Test case for
     * <a href="http://jira.pentaho.com/browse/BISERVER-1574">BISERVER-1574,
     * "Cube role rollupPolicy='partial' failure"</a>. The problem was a
     * NullPointerException in
     * {@link CatalogReader#getMemberParent(org.eclipse.daanse.olap.api.element.Member)} when called
     * on a members returned in a result set. JPivot calls that method but
     * Mondrian normally does not.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier9.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugBiserver1574(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnection(props);
        final String mdx =
            "select {([Measures].[Store Invoice], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs])} ON COLUMNS,\n"
            + "  {[Warehouse].[Warehouse].[All Warehouses]} ON ROWS\n"
            + "from [Warehouse]";
        checkQuery(connection, mdx);
        assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Store Invoice], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "Axis #2:\n"
            + "{[Warehouse].[Warehouse].[All Warehouses]}\n"
            + "Row #0: 4,042.96\n");
    }

    /**
     * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-435">
     * MONDRIAN-435, "Internal error in HierarchizeArrayComparator"</a>. Occurs
     * when apply Hierarchize function to tuples on a hierarchy with
     * partial-rollup.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier9.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian435(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnection(props);

        // minimal testcase
    	assertThatQuery(connection,
            "select hierarchize("
            + "    crossjoin({[Store Size in SQFT], [Store Size in SQFT].Children}, {[Product]})"
            + ") on 0,"
            + "[Store Type].Members on 1 from [Warehouse]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Product].[All Products]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[20319], [Product].[Product].[All Products]}\n"
            + "Axis #2:\n"
            + "{[Store Type].[Store Type].[All Store Types]}\n"
            + "{[Store Type].[Store Type].[Supermarket]}\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 4,042.96\n");

        // explicit tuples, not crossjoin
    	assertThatQuery(connection,
            "select hierarchize("
            + "    { ([Store Size in SQFT], [Product]),\n"
            + "      ([Store Size in SQFT].[20319], [Product].[Food]),\n"
            + "      ([Store Size in SQFT], [Product].[Drink].[Dairy]),\n"
            + "      ([Store Size in SQFT].[20319], [Product]) }\n"
            + ") on 0,"
            + "[Store Type].Members on 1 from [Warehouse]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Product].[All Products]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Product].[Drink].[Dairy]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[20319], [Product].[Product].[All Products]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[20319], [Product].[Product].[Food]}\n"
            + "Axis #2:\n"
            + "{[Store Type].[Store Type].[All Store Types]}\n"
            + "{[Store Type].[Store Type].[Supermarket]}\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 82.454\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 2,696.758\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 82.454\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 2,696.758\n");

        // extended testcase; note that [Store Size in SQFT].Parent is null,
        // so disappears
    	assertThatQuery(connection,
            "select non empty hierarchize("
            + "union("
            + "  union("
            + "    crossjoin({[Store Size in SQFT]}, {[Product]}),"
            + "    crossjoin({[Store Size in SQFT], [Store Size in SQFT].Children}, {[Product]}),"
            + "    all),"
            + "  union("
            + "    crossjoin({[Store Size in SQFT].Parent}, {[Product].[Drink]}),"
            + "    crossjoin({[Store Size in SQFT].Children}, {[Product].[Food]}),"
            + "    all),"
            + "  all)) on 0,"
            + "[Store Type].Members on 1 from [Warehouse]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Product].[All Products]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Product].[All Products]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[20319], [Product].[Product].[All Products]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[20319], [Product].[Product].[Food]}\n"
            + "Axis #2:\n"
            + "{[Store Type].[Store Type].[All Store Types]}\n"
            + "{[Store Type].[Store Type].[Supermarket]}\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 2,696.758\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 2,696.758\n");

    	assertThatQuery(connection,
            "select Hierarchize(\n"
            + "  CrossJoin\n("
            + "    CrossJoin(\n"
            + "      {[Product].[All Products], "
            + "       [Product].[Food],\n"
            + "       [Product].[Food].[Eggs],\n"
            + "       [Product].[Drink].[Dairy]},\n"
            + "      [Store Type].MEMBERS),\n"
            + "    [Store Size in SQFT].MEMBERS),\n"
            + "  PRE) on 0\n"
            + "from [Warehouse]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Product].[Product].[All Products], [Store Type].[Store Type].[All Store Types], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Product].[All Products], [Store Type].[Store Type].[All Store Types], [Store Size in SQFT].[Store Size in SQFT].[20319]}\n"
            + "{[Product].[Product].[All Products], [Store Type].[Store Type].[Supermarket], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Product].[All Products], [Store Type].[Store Type].[Supermarket], [Store Size in SQFT].[Store Size in SQFT].[20319]}\n"
            + "{[Product].[Product].[Drink].[Dairy], [Store Type].[Store Type].[All Store Types], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Product].[Drink].[Dairy], [Store Type].[Store Type].[All Store Types], [Store Size in SQFT].[Store Size in SQFT].[20319]}\n"
            + "{[Product].[Product].[Drink].[Dairy], [Store Type].[Store Type].[Supermarket], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Product].[Drink].[Dairy], [Store Type].[Store Type].[Supermarket], [Store Size in SQFT].[Store Size in SQFT].[20319]}\n"
            + "{[Product].[Product].[Food], [Store Type].[Store Type].[All Store Types], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Product].[Food], [Store Type].[Store Type].[All Store Types], [Store Size in SQFT].[Store Size in SQFT].[20319]}\n"
            + "{[Product].[Product].[Food], [Store Type].[Store Type].[Supermarket], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Product].[Food], [Store Type].[Store Type].[Supermarket], [Store Size in SQFT].[Store Size in SQFT].[20319]}\n"
            + "{[Product].[Product].[Food].[Eggs], [Store Type].[Store Type].[All Store Types], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Product].[Food].[Eggs], [Store Type].[Store Type].[All Store Types], [Store Size in SQFT].[Store Size in SQFT].[20319]}\n"
            + "{[Product].[Product].[Food].[Eggs], [Store Type].[Store Type].[Supermarket], [Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Product].[Food].[Eggs], [Store Type].[Store Type].[Supermarket], [Store Size in SQFT].[Store Size in SQFT].[20319]}\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 82.454\n"
            + "Row #0: 82.454\n"
            + "Row #0: 82.454\n"
            + "Row #0: 82.454\n"
            + "Row #0: 2,696.758\n"
            + "Row #0: 2,696.758\n"
            + "Row #0: 2,696.758\n"
            + "Row #0: 2,696.758\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n");
    }

    /**
     * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-436">
     * MONDRIAN-436, "SubstitutingMemberReader.getMemberBuilder gives
     * UnsupportedOperationException"</a>.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier9.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testBugMondrian436(Context<?> foodMartContext) {
        // Run twice: the original test re-ran the same assertion after
        // resetting the catalog to plain FoodMart and re-applying the same
        // modifier, which is a no-op under the new testkit (the catalog is
        // built once per test) -- kept for parity.
        checkBugMondrian436(foodMartContext);
        checkBugMondrian436(foodMartContext);
    }

    private void checkBugMondrian436(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("role1"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnection(props);

    	assertThatQuery(connection,
            "select non empty {[Measures].[Units Ordered],\n"
            + "            [Measures].[Units Shipped]} on 0,\n"
            + "non empty hierarchize(\n"
            + "    union(\n"
            + "        crossjoin(\n"
            + "            {[Store Size in SQFT]},\n"
            + "            {[Product].[Drink],\n"
            + "             [Product].[Food],\n"
            + "             [Product].[Drink].[Dairy]}),\n"
            + "        crossjoin(\n"
            + "            {[Store Size in SQFT].[20319]},\n"
            + "            {[Product].Children}))) on 1\n"
            + "from [Warehouse]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Units Ordered]}\n"
            + "{[Measures].[Units Shipped]}\n"
            + "Axis #2:\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Product].[Drink]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Product].[Drink].[Dairy]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Product].[Food]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[20319], [Product].[Product].[Drink]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[20319], [Product].[Product].[Food]}\n"
            + "{[Store Size in SQFT].[Store Size in SQFT].[20319], [Product].[Product].[Non-Consumable]}\n"
            + "Row #0: 865.0\n"
            + "Row #0: 767.0\n"
            + "Row #1: 195.0\n"
            + "Row #1: 182.0\n"
            + "Row #2: 6065.0\n"
            + "Row #2: 5723.0\n"
            + "Row #3: 865.0\n"
            + "Row #3: 767.0\n"
            + "Row #4: 6065.0\n"
            + "Row #4: 5723.0\n"
            + "Row #5: 2179.0\n"
            + "Row #5: 2025.0\n");
    }

    /**
     * Tests that hierarchy-level access control works on a virtual cube.
     * See bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-456">
     * MONDRIAN-456, "Roles and virtual cubes"</a>.
     */
    @Disabled //TODO need investigate
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier10.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testVirtualCube(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("VCRole"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
    	Connection connection = foodMartContext.getConnection(props);
    	assertThatQuery(connection,
            "select [Store].Members on 0 from [Warehouse and Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store].[All Stores]}\n"
            + "{[Store].[USA]}\n"
            + "{[Store].[USA].[CA]}\n"
            + "{[Store].[USA].[CA].[Alameda]}\n"
            + "{[Store].[USA].[CA].[Alameda].[HQ]}\n"
            + "{[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
            + "{[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[USA].[CA].[San Diego].[Store 24]}\n"
            + "{[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[USA].[CA].[San Francisco].[Store 14]}\n"
            + "Row #0: 159,167.84\n"
            + "Row #0: 159,167.84\n"
            + "Row #0: 159,167.84\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 45,750.24\n"
            + "Row #0: 45,750.24\n"
            + "Row #0: 54,431.14\n"
            + "Row #0: 54,431.14\n"
            + "Row #0: 4,441.18\n"
            + "Row #0: 4,441.18\n");
    }

    /**
     * this tests the fix for
     * http://jira.pentaho.com/browse/BISERVER-2491
     * rollupPolicy=partial and queries to upper members don't work
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier11.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugBiserver2491(Context<?> foodMartContext) {
        ConnectionProps props =new ConnectionProps(List.of("role2"), true, Locale.getDefault(), Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), Optional.empty());
        Connection connection = foodMartContext.getConnection(props);

        final String firstBrokenMdx =
            "select [Measures].[Unit Sales] ON COLUMNS, {[Store].[Store Country].Members} ON ROWS from [Sales]";

        checkQuery(connection, firstBrokenMdx);
        assertThatQuery(connection,
            firstBrokenMdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA]}\n"
            + "Row #0: 49,085\n");

        final String secondBrokenMdx =
            "select [Measures].[Unit Sales] ON COLUMNS, "
            + "Descendants([Store],[Store].[Store Name]) ON ROWS from [Sales]";
        checkQuery(connection, secondBrokenMdx);
        assertThatQuery(connection,
            secondBrokenMdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[USA].[CA].[Alameda].[HQ]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego].[Store 24]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco].[Store 14]}\n"
            + "Row #0: \n"
            + "Row #1: 21,333\n"
            + "Row #2: 25,635\n"
            + "Row #3: 2,117\n");
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-622">MONDRIAN-622,
     * "Poor performance with large union role"</a>.
     */
    @Test
    @RolapContextTest(value = AccessControlBugMondrian622Instance.class)
    void testBugMondrian622(@Roles("Test") Connection connection) {
        final String cubeName = "Sales with multiple customers";
        executeQuery(connection, "select from [" + cubeName + "]");
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-694">MONDRIAN-694,
     * "Incorrect handling of child/parent relationship with hierarchy
     * grants"</a>.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier14.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian694(@Roles("REG1") Connection connection) {
        // With bug MONDRIAN-694 returns 874.80, should return 79.20.
        // Test case is minimal: doesn't happen without the Crossjoin, or
        // without the NON EMPTY, or with [Employees] as opposed to
        // [Employees].[All Employees], or with [Department].[All Departments].
    	assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Org Salary]} ON COLUMNS,\n"
            + "NON EMPTY Crossjoin({[Department].[14]}, {[Employees].[All Employees]}) ON ROWS\n"
            + "from [HR]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Org Salary]}\n"
            + "Axis #2:\n"
            + "{[Department].[Department].[14], [Employees].[Employees].[All Employees]}\n"
            + "Row #0: $97.20\n");

        // This query gave the right answer, even with MONDRIAN-694.
    	assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Org Salary]} ON COLUMNS, \n"
            + "NON EMPTY Hierarchize(Crossjoin({[Department].[14]}, {[Employees].[All Employees], [Employees].Children})) ON ROWS \n"
            + "from [HR] ").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Org Salary]}\n"
            + "Axis #2:\n"
            + "{[Department].[Department].[14], [Employees].[Employees].[All Employees]}\n"
            + "{[Department].[Department].[14], [Employees].[Employees].[Sheri Nowmer]}\n"
            + "Row #0: $97.20\n"
            + "Row #1: $97.20\n");

        // Original test case, not quite minimal. With MONDRIAN-694, returns
        // $874.80 for [All Employees].
    	assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Org Salary]} ON COLUMNS, \n"
            + "NON EMPTY Hierarchize(Union(Crossjoin({[Department].[All Departments].[14]}, {[Employees].[All Employees]}), Crossjoin({[Department].[All Departments].[14]}, [Employees].[All Employees].Children))) ON ROWS \n"
            + "from [HR]  ").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Org Salary]}\n"
            + "Axis #2:\n"
            + "{[Department].[Department].[14], [Employees].[Employees].[All Employees]}\n"
            + "{[Department].[Department].[14], [Employees].[Employees].[Sheri Nowmer]}\n"
            + "Row #0: $97.20\n"
            + "Row #1: $97.20\n");

    	assertThatQuery(connection,
            "select NON EMPTY {[Measures].[Org Salary]} ON COLUMNS, \n"
            + "NON EMPTY Crossjoin(Hierarchize(Union({[Employees].[All Employees]}, [Employees].[All Employees].Children)), {[Department].[14]}) ON ROWS \n"
            + "from [HR] ").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Org Salary]}\n"
            + "Axis #2:\n"
            + "{[Employees].[Employees].[All Employees], [Department].[Department].[14]}\n"
            + "{[Employees].[Employees].[Sheri Nowmer], [Department].[Department].[14]}\n"
            + "Row #0: $97.20\n"
            + "Row #1: $97.20\n");
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-722">MONDRIAN-722, "If
     * ignoreInvalidMembers=true, should ignore grants with invalid
     * members"</a>.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier15.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @RolapConfig(key = ConfigConstants.IGNORE_INVALID_MEMBERS, value = "true", type = Boolean.class)
    void testBugMondrian722(@Roles("CTO") Connection connection) {
        assertThatQuery(connection,
                "select [Measures] on 0,\n"
                + " Hierarchize(\n"
                + "   {[Customers].[USA].Children,\n"
                + "    [Customers].[USA].[CA].Children}) on 1\n"
                + "from [Sales]").returnsGrid(
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Customers].[Customers].[USA].[CA]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Los Angeles]}\n"
                + "{[Customers].[Customers].[USA].[CA].[San Francisco]}\n"
                + "Row #0: 74,748\n"
                + "Row #1: 2,009\n"
                + "Row #2: 88\n");
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-746">MONDRIAN-746,
     * "Report returns stack trace when turning on subtotals on a hierarchy with
     * top level hidden"</a>.
     */
    @Test
    void testCalcMemberLevelDefaultRole(Connection connection) {
        checkCalcMemberLevel(connection);
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier16.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testCalcMemberLevelRole1(@Roles("Role1") Connection connection) {
        checkCalcMemberLevel(connection);
    }

    /**
     * Test for bug MONDRIAN-568. Grants on OLAP elements are validated
     * by name, thus granting implicit access to all cubes which have
     * a dimension with the same name.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier17.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian568(@Roles("Role1") Connection role1Connection,
            @Roles({"Role1", "Role2"}) Connection role1Role2Connection) {
        assertMemberAccess(
        		role1Connection,
                AccessMember.NONE,
                "[Measures].[Store Cost]");

        assertMemberAccess(
        		role1Role2Connection,
            AccessMember.NONE,
            "[Measures].[Store Cost]");
    }

    private void checkCalcMemberLevel(Connection connection) {
        Result result = executeQuery(
    		connection,
            "with member [Store].[USA].[CA].[Foo] as\n"
            + " 1\n"
            + "select {[Measures].[Unit Sales]} on columns,\n"
            + "{[Store].[USA].[CA],\n"
            + " [Store].[USA].[CA].[Los Angeles],\n"
            + " [Store].[USA].[CA].[Foo]} on rows\n"
            + "from [Sales]");
        final List<Position> rowPos = result.getAxes()[1].getPositions();
        final Member member0 = rowPos.get(0).get(0);
        assertEquals("CA", member0.getName());
        assertEquals("Store State", member0.getLevel().getName());
        final Member member1 = rowPos.get(1).get(0);
        assertEquals("Los Angeles", member1.getName());
        assertEquals("Store City", member1.getLevel().getName());
        final Member member2 = rowPos.get(2).get(0);
        assertEquals("Foo", member2.getName());
        assertEquals("Store City", member2.getLevel().getName());
    }

    /**
     * Testcase for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-935">MONDRIAN-935,
     * "no results when some level members in a member grant have no data"</a>.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier18.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian935(@Roles("Role1") Connection connection) {
    	assertThatQuery(connection,
            "select [Measures] on 0,\n"
            + "[Customers].[USA].Children * [Store Type].Children on 1\n"
            + "from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Customers].[Customers].[USA].[CA], [Store Type].[Store Type].[Supermarket]}\n"
            + "{[Customers].[Customers].[USA].[WA], [Store Type].[Store Type].[Supermarket]}\n"
            + "Row #0: 1,118\n"
            + "Row #1: 73,178\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier19.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testDimensionGrant(@Roles("Role1") Connection connection, @Roles("Role2") Connection role2Connection,
            @Roles("Role3") Connection role3Connection) throws Exception {
    	assertThatAxis(connection, "Sales",
            "[Education Level].[Education Level].Members").returns(
            "[Education Level].[Education Level].[All Education Levels]\n"
            + "[Education Level].[Education Level].[Bachelors Degree]\n"
            + "[Education Level].[Education Level].[Graduate Degree]\n"
            + "[Education Level].[Education Level].[High School Degree]\n"
            + "[Education Level].[Education Level].[Partial College]\n"
            + "[Education Level].[Education Level].[Partial High School]");
    	assertThatAxis(connection, "Sales",
            "[Customers].Members").throwsMessage(
            "MDX object '[Customers]' not found in cube 'Sales'");
    	assertThatQuery(connection,
            "select {[Education Level].Members} on columns, {[Measures].[Unit Sales]} on rows from Sales").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Education Level].[Education Level].[All Education Levels]}\n"
            + "{[Education Level].[Education Level].[Bachelors Degree]}\n"
            + "{[Education Level].[Education Level].[Graduate Degree]}\n"
            + "{[Education Level].[Education Level].[High School Degree]}\n"
            + "{[Education Level].[Education Level].[Partial College]}\n"
            + "{[Education Level].[Education Level].[Partial High School]}\n"
            + "Axis #2:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Row #0: 266,773\n"
            + "Row #0: 68,839\n"
            + "Row #0: 15,570\n"
            + "Row #0: 78,664\n"
            + "Row #0: 24,545\n"
            + "Row #0: 79,155\n");
    	assertThatAxis(role2Connection, "Sales",
            "[Customers].Members").throwsMessage(
            "MDX object '[Customers]' not found in cube 'Sales'");
    	assertThatQuery(role3Connection,
            "select {[Education Level].Members} on columns, {[Measures].[Unit Sales]} on rows from Sales").throwsMessage(
            "MDX object '[Measures].[Unit Sales]' not found in cube 'Sales'");
    }

    // ~ Inner classes =========================================================

    public static class PeopleRole extends DelegatingRole {
        private final String repName;

        public PeopleRole(Role role, org.eclipse.daanse.olap.api.element.Catalog schema, String repName) {
            super(((RoleImpl)role).makeMutableClone());
            this.repName = repName;
            defineGrantsForUser(schema);
        }

        private void defineGrantsForUser(org.eclipse.daanse.olap.api.element.Catalog schema) {
            RoleImpl role = (RoleImpl)this.role;
            role.grant(schema, AccessCatalog.NONE);

            Cube cube = schema.lookupCube("HR").orElseThrow();
            role.grant(cube, AccessCube.ALL);

            org.eclipse.daanse.olap.api.element.Hierarchy hierarchy = cube.lookupHierarchy(
                new IdImpl.NameSegmentImpl("Employees"), false);

            List<? extends Level> levels = hierarchy.getLevels();
            Level topLevel = levels.get(1);

            role.grant(hierarchy, AccessHierarchy.CUSTOM, null, null, org.eclipse.daanse.olap.api.access.RollupPolicy.FULL);
            role.grant(hierarchy.getAllMember(), org.eclipse.daanse.olap.api.access.AccessMember.NONE);

            boolean foundMember = false;

            List <Member> members =
                schema.getCatalogReaderWithDefaultRole().withLocus()
                    .getLevelMembers(topLevel, true);

            for (Member member : members) {
                if (member.getUniqueName().contains("[" + repName + "]")) {
                    foundMember = true;
                    role.grant(member, AccessMember.ALL);
                }
            }
            assertTrue(foundMember);
        }
    }

    /**
     * This is a test for MONDRIAN-1030. When the top level of a hierarchy
     * is not accessible and a partial rollup policy is used, the results would
     * be returned as those of the first member of those accessible only.
     *
     * <p>ie: If a union of roles give access to two two sibling root members
     * and the level to which they belong is not included in a query, the
     * returned cell data would be that of the first sibling and would exclude
     * those of the second.
     *
     * <p>This is because the RolapEvaluator cannot represent default members
     * as multiple members (only a single member is the default member) and
     * because the default member is not the 'all member', it adds a constrain
     * to the SQL for the first member only.
     *
     * <p>Currently, Mondrian disguises the root member in the evaluator as a
     * RestrictedMemberReader.MultiCardinalityDefaultMember. Later,
     * RolapHierarchy.LimitedRollupSubstitutingMemberReader will recognize it
     * and use the correct rollup policy on the parent member to generate
     * correct SQL.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier20.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMondrian1030(@Roles("Role1") Connection role1Connection, @Roles("Role2") Connection role2Connection,
            @Roles({"Role1", "Role2"}) Connection role1Role2Connection) throws Exception {
        final String mdx1 =
            "With\n"
            + "Set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Customers],[*BASE_MEMBERS_Product])'\n"
            + "Set [*SORTED_ROW_AXIS] as 'Order([*CJ_ROW_AXIS],[Customers].CurrentMember.OrderKey,BASC,[Education Level].CurrentMember.OrderKey,BASC)'\n"
            + "Set [*BASE_MEMBERS_Customers] as '[Customers].[City].Members'\n"
            + "Set [*BASE_MEMBERS_Product] as '[Education Level].Members'\n"
            + "Set [*BASE_MEMBERS_Measures] as '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
            + "Set [*CJ_ROW_AXIS] as 'Generate([*NATIVE_CJ_SET], {([Customers].currentMember,[Education Level].currentMember)})'\n"
            + "Set [*CJ_COL_AXIS] as '[*NATIVE_CJ_SET]'\n"
            + "Member [Measures].[*FORMATTED_MEASURE_0] as '[Measures].[Unit Sales]', FORMAT_STRING = '#,###', SOLVE_ORDER=400\n"
            + "Select\n"
            + "[*BASE_MEMBERS_Measures] on columns,\n"
            + "Non Empty [*SORTED_ROW_AXIS] on rows\n"
            + "From [Sales] \n";
        final String mdx2 =
            "With\n"
            + "Set [*BASE_MEMBERS_Product] as '[Education Level].Members'\n"
            + "Set [*BASE_MEMBERS_Measures] as '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
            + "Member [Measures].[*FORMATTED_MEASURE_0] as '[Measures].[Unit Sales]', FORMAT_STRING = '#,###', SOLVE_ORDER=400\n"
            + "Select\n"
            + "[*BASE_MEMBERS_Measures] on columns,\n"
            + "Non Empty [*BASE_MEMBERS_Product] on rows\n"
            + "From [Sales] \n";
        // Control tests
        assertThatQuery(role1Connection,
            mdx1).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
            + "Axis #2:\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[All Education Levels]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[Bachelors Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[Graduate Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[High School Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[Partial College]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[Partial High School]}\n"
            + "Row #0: 2,391\n"
            + "Row #1: 559\n"
            + "Row #2: 205\n"
            + "Row #3: 551\n"
            + "Row #4: 253\n"
            + "Row #5: 823\n");
        assertThatQuery(role2Connection,
            mdx1).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
            + "Axis #2:\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[All Education Levels]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[Bachelors Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[Graduate Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[High School Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[Partial College]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[Partial High School]}\n"
            + "Row #0: 3,086\n"
            + "Row #1: 914\n"
            + "Row #2: 126\n"
            + "Row #3: 1,029\n"
            + "Row #4: 286\n"
            + "Row #5: 731\n");
        assertThatQuery(role1Role2Connection,
            mdx1).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
            + "Axis #2:\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[All Education Levels]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[Bachelors Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[Graduate Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[High School Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[Partial College]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Burbank], [Education Level].[Education Level].[Partial High School]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[All Education Levels]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[Bachelors Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[Graduate Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[High School Degree]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[Partial College]}\n"
            + "{[Customers].[Customers].[USA].[CA].[Coronado], [Education Level].[Education Level].[Partial High School]}\n"
            + "Row #0: 3,086\n"
            + "Row #1: 914\n"
            + "Row #2: 126\n"
            + "Row #3: 1,029\n"
            + "Row #4: 286\n"
            + "Row #5: 731\n"
            + "Row #6: 2,391\n"
            + "Row #7: 559\n"
            + "Row #8: 205\n"
            + "Row #9: 551\n"
            + "Row #10: 253\n"
            + "Row #11: 823\n");
        // Actual tests
        assertThatQuery(role1Connection,
            mdx2).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
            + "Axis #2:\n"
            + "{[Education Level].[Education Level].[All Education Levels]}\n"
            + "{[Education Level].[Education Level].[Bachelors Degree]}\n"
            + "{[Education Level].[Education Level].[Graduate Degree]}\n"
            + "{[Education Level].[Education Level].[High School Degree]}\n"
            + "{[Education Level].[Education Level].[Partial College]}\n"
            + "{[Education Level].[Education Level].[Partial High School]}\n"
            + "Row #0: 2,391\n"
            + "Row #1: 559\n"
            + "Row #2: 205\n"
            + "Row #3: 551\n"
            + "Row #4: 253\n"
            + "Row #5: 823\n");

        assertThatQuery(role2Connection,
            mdx2).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
            + "Axis #2:\n"
            + "{[Education Level].[Education Level].[All Education Levels]}\n"
            + "{[Education Level].[Education Level].[Bachelors Degree]}\n"
            + "{[Education Level].[Education Level].[Graduate Degree]}\n"
            + "{[Education Level].[Education Level].[High School Degree]}\n"
            + "{[Education Level].[Education Level].[Partial College]}\n"
            + "{[Education Level].[Education Level].[Partial High School]}\n"
            + "Row #0: 3,086\n"
            + "Row #1: 914\n"
            + "Row #2: 126\n"
            + "Row #3: 1,029\n"
            + "Row #4: 286\n"
            + "Row #5: 731\n");
        assertThatQuery(role1Role2Connection,
            mdx2).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
            + "Axis #2:\n"
            + "{[Education Level].[Education Level].[All Education Levels]}\n"
            + "{[Education Level].[Education Level].[Bachelors Degree]}\n"
            + "{[Education Level].[Education Level].[Graduate Degree]}\n"
            + "{[Education Level].[Education Level].[High School Degree]}\n"
            + "{[Education Level].[Education Level].[Partial College]}\n"
            + "{[Education Level].[Education Level].[Partial High School]}\n"
            + "Row #0: 5,477\n"
            + "Row #1: 1,473\n"
            + "Row #2: 331\n"
            + "Row #3: 1,580\n"
            + "Row #4: 539\n"
            + "Row #5: 1,554\n");
    }

    /**
     * This is a test for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1030">MONDRIAN-1030</a>
     * When a query is based on a level higher than one in the same hierarchy
     * which has access controls, it would only constrain at the current level
     * if the rollup policy of partial is used.
     *
     * <p>Example. A query on USA where only Los-Angeles is accessible would
     * return the values for California instead of only LA.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier21.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testBugMondrian1030_2(@Roles("Bacon") Connection connection) {
    	assertThatQuery(connection,
                "select {[Measures].[Unit Sales]} on 0,\n"
                + "   {[Customers].[USA]} on 1\n"
                + "from [Sales]").returnsGrid(
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Customers].[Customers].[USA]}\n"
                + "Row #0: 2,009\n");
    }

    /**
     * Test for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1091">MONDRIAN-1091</a>
     * The RoleImpl would try to search for member grants by object identity
     * rather than unique name. When using the partial rollup policy, the
     * members are wrapped, so identity checks would fail.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier22.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMondrian1091(@Roles("Role1") Connection connection) throws Exception {
    	assertThatQuery(connection,
            "select {[Store].Members} on columns from [Sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store].[Store].[All Stores]}\n"
            + "{[Store].[Store].[USA]}\n"
            + "{[Store].[Store].[USA].[CA]}\n"
            + "{[Store].[Store].[USA].[CA].[Alameda]}\n"
            + "{[Store].[Store].[USA].[CA].[Alameda].[HQ]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles].[Store 7]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego].[Store 24]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco].[Store 14]}\n"
            + "Row #0: 74,748\n"
            + "Row #0: 74,748\n"
            + "Row #0: 74,748\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 21,333\n"
            + "Row #0: 21,333\n"
            + "Row #0: 25,663\n"
            + "Row #0: 25,663\n"
            + "Row #0: 25,635\n"
            + "Row #0: 25,635\n"
            + "Row #0: 2,117\n"
            + "Row #0: 2,117\n");
        Cube cube =
        		getCubeByNameFromArray(connection
                .getCatalog().getCubes(), "Sales").orElseThrow(() -> new RuntimeException("Cube with name \"Sales\" is absent"));

        Member allMember =
            cube.getCatalogReader(connection.getRole()).withLocus().getMemberByUniqueName(
                Util.parseIdentifier("[Store].[All Stores]"), false);
        //org.olap4j.metadata.Member allMember =
        //    cube.lookupMember(
        //        IdentifierNode.parseIdentifier("[Store].[All Stores]")
        ///            .getSegmentList());

        assertNotNull(allMember);
        assertNotNull(allMember.getHierarchy().getAllMember());
        assertEquals(
            "[Store].[Store].[All Stores]",
            allMember.getHierarchy().getAllMember().getUniqueName());
    }

    /**
     * Unit test for
     * <a href="http://jira.pentaho.com/browse/mondrian-1259">MONDRIAN-1259,
     * "Mondrian security: access leaks from one user to another"</a>.
     *
     * <p>Enhancements made to the SmartRestrictedMemberReader were causing
     * security leaks between roles and potential class cast exceptions.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier23.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMondrian1259(@Roles("Role1") Connection role1Connection, @Roles("Role2") Connection role2Connection)
            throws Exception {
        final String mdx =
            "select non empty {[Store].Members} on columns from [Sales]";
        assertThatQuery(role1Connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store].[Store].[All Stores]}\n"
            + "{[Store].[Store].[USA]}\n"
            + "{[Store].[Store].[USA].[CA]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles].[Store 7]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[Store].[USA].[CA].[San Diego].[Store 24]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco].[Store 14]}\n"
            + "Row #0: 74,748\n"
            + "Row #0: 74,748\n"
            + "Row #0: 74,748\n"
            + "Row #0: 21,333\n"
            + "Row #0: 21,333\n"
            + "Row #0: 25,663\n"
            + "Row #0: 25,663\n"
            + "Row #0: 25,635\n"
            + "Row #0: 25,635\n"
            + "Row #0: 2,117\n"
            + "Row #0: 2,117\n");
        assertThatQuery(role2Connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store].[Store].[All Stores]}\n"
            + "{[Store].[Store].[USA]}\n"
            + "{[Store].[Store].[USA].[OR]}\n"
            + "{[Store].[Store].[USA].[OR].[Portland]}\n"
            + "{[Store].[Store].[USA].[OR].[Portland].[Store 11]}\n"
            + "{[Store].[Store].[USA].[OR].[Salem]}\n"
            + "{[Store].[Store].[USA].[OR].[Salem].[Store 13]}\n"
            + "Row #0: 67,659\n"
            + "Row #0: 67,659\n"
            + "Row #0: 67,659\n"
            + "Row #0: 26,079\n"
            + "Row #0: 26,079\n"
            + "Row #0: 41,580\n"
            + "Row #0: 41,580\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier24.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMondrian1295(Connection defaultConnection, @Roles("Admin") Connection connection) throws Exception {
        final String mdx =
            "With\n"
            + "Set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Time],[*BASE_MEMBERS_Product])'\n"
            + "Set [*SORTED_ROW_AXIS] as 'Order([*CJ_ROW_AXIS],Ancestor([Time].[Time].CurrentMember, [Time].[Time].[Year]).OrderKey,BASC,Ancestor([Time].[Time].CurrentMember, [Time].[Time].[Quarter]).OrderKey,BASC,[Time].[Time].CurrentMember.OrderKey,BASC,[Product].[Product].CurrentMember.OrderKey,BASC)'\n"
            + "Set [*BASE_MEMBERS_Product] as '{[Product].[Product].[All Products]}'\n"
            + "Set [*BASE_MEMBERS_Measures] as '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
            + "Set [*CJ_ROW_AXIS] as 'Generate([*NATIVE_CJ_SET], {([Time].[Time].currentMember,[Product].[Product].currentMember)})'\n"
            + "Set [*BASE_MEMBERS_Time] as '[Time].[Time].[Year].Members'\n"
            + "Set [*CJ_COL_AXIS] as '[*NATIVE_CJ_SET]'\n"
            + "Member [Measures].[*FORMATTED_MEASURE_0] as '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', SOLVE_ORDER=400\n"
            + "Select\n"
            + "[*BASE_MEMBERS_Measures] on columns,\n"
            + "Non Empty [*SORTED_ROW_AXIS] on rows\n"
            + "From [Sales]\n";

        // Control
        assertThatQuery(defaultConnection,
                "select {[Measures].[Unit Sales]} on columns from [Sales]").returnsGrid(
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Row #0: 266,773\n");
        assertThatQuery(connection,
                "select {[Measures].[Unit Sales]} on columns from [Sales]").returnsGrid(
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Row #0: 74,748\n");

        // Test
        assertThatQuery(connection,
                mdx).returnsGrid(
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
                + "Axis #2:\n"
                + "{[Time].[Time].[1997], [Product].[Product].[All Products]}\n"
                + "Row #0: 74,748\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier25.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMondrian936(@Roles("test") Connection connection) throws Exception {
        assertThatQuery(connection,
            "select {[Measures].[Unit Sales]} on columns, "
            + "                 {[Product].[Food].[Baked Goods].[Bread]} on rows "
            + "                 from [Sales] "
            + " where { [Store].[USA].[OR], [Store].[USA].[CA]} ").returnsGrid( "Axis #0:\n"
            + "{[Store].[Store].[USA].[OR]}\n"
            + "{[Store].[Store].[USA].[CA]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Product].[Product].[Food].[Baked Goods].[Bread]}\n"
            + "Row #0: 4,163\n");

        // changing ordering of members in the slicer should not change
        // result
    	assertThatQuery(connection,
            "select {[Measures].[Unit Sales]} on columns, "
            + "                 {[Product].[Food].[Baked Goods].[Bread]} on rows "
            + "                 from [Sales] "
            + " where { [Store].[USA].[CA], [Store].[USA].[OR]} ").returnsGrid( "Axis #0:\n"
            + "{[Store].[Store].[USA].[CA]}\n"
            + "{[Store].[Store].[USA].[OR]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Product].[Product].[Food].[Baked Goods].[Bread]}\n"
            + "Row #0: 4,163\n");


        Result result = executeQuery(
    		connection,
            "with member store.aggCaliforniaOregon as "
            + "'aggregate({ [Store].[USA].[CA], [Store].[USA].[OR]})'"
            + " select store.aggCaliforniaOregon on 0 from sales");

        String valueAggMember = result
            .getCell(new int[] {0}).getFormattedValue();

        result = executeQuery(
    		connection,
            " select from sales where "
            + "{ [Store].[USA].[CA], [Store].[USA].[OR]}");

        String valueSlicerAgg = result
            .getCell(new int[] {}).getFormattedValue();

        // aggregating CA & OR in a calc member should produce same result
        // as aggregating in the slicer.
        assertTrue(valueAggMember.equals(valueSlicerAgg));
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier26.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMondrian1434Sales(@Roles("dev") Connection connection) {
        executeQuery(
    		connection,
            " select from [Sales] where {[Measures].[Unit Sales]}");
        // test is that there is no exception
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier27.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMondrian1434WarehouseAndSales(@Roles("dev") Connection connection) {
        executeQuery(
    		connection,
            " select from [Warehouse and Sales] where {[Measures].[Store Sales]}");
        // test is that there is no exception
    }

    /**
     * Fix for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-1486">MONDRIAN-1486</a>
     *
     * When NECJ was used, a call to RolapNativeCrossJoin.createEvaluator
     * would swap the {@link LimitedRollupMember} for the regular all member
     * of the hierarchy, effectively removing security constraints.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier28.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMondrian1486(@Roles("Admin") Connection connection) throws Exception {
        final String mdx =
            "With\n"
            + "Set [*NATIVE_CJ_SET] as 'NonEmptyCrossJoin([*BASE_MEMBERS_Gender],[*BASE_MEMBERS_Marital Status])'\n"
            + "Set [*SORTED_ROW_AXIS] as 'Order([*CJ_ROW_AXIS],[Gender].CurrentMember.OrderKey,BASC,[Marital Status].[Marital Status].CurrentMember.OrderKey,BASC)'\n"
            + "Set [*BASE_MEMBERS_Gender] as '[Gender].[Gender].[Gender].Members'\n"
            + "Set [*BASE_MEMBERS_Measures] as '{[Measures].[*FORMATTED_MEASURE_0]}'\n"
            + "Set [*CJ_ROW_AXIS] as 'Generate([*NATIVE_CJ_SET], {([Gender].[Gender].currentMember,[Marital Status].[Marital Status].currentMember)})'\n"
            + "Set [*BASE_MEMBERS_Marital Status] as '[Marital Status].[Marital Status].[Marital Status].Members'\n"
            + "Set [*CJ_COL_AXIS] as '[*NATIVE_CJ_SET]'\n"
            + "Member [Measures].[*FORMATTED_MEASURE_0] as '[Measures].[Unit Sales]', FORMAT_STRING = 'Standard', SOLVE_ORDER=400\n"
            + "Select\n"
            + "[*BASE_MEMBERS_Measures] on columns,\n"
            + "Non Empty [*SORTED_ROW_AXIS] on rows\n"
            + "From [Sales]\n";
        assertThatQuery(connection,
            mdx).returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[*FORMATTED_MEASURE_0]}\n"
            + "Axis #2:\n"
            + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
            + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S]}\n"
            + "Row #0: 65,336\n"
            + "Row #1: 66,222\n");
    }

    // Verifies limited role-restricted results using all variations of
    // rollup policy. Also verifies consistent results with a non-all
    // default member. Connected with MONDRIAN-1568: results should be the
    // same regardless of rollupPolicy, default member, and whether there is
    // an all member, since the rollup is not included in the test queries
    // and context is explicitly set for [Store2]. One test method per
    // (RollupPolicy x defaultMember x hasAll) combination, each backed by
    // its own catalog built from AccessControlTestModifier29 -- the original
    // test rebuilt the catalog for each combination in a loop.

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativeFullNonAllDefaultHasAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativeFullNonAllDefaultHasAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.FULL, "[Store2].[USA].[CA]", true);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativeFullNonAllDefaultNoAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativeFullNonAllDefaultNoAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.FULL, "[Store2].[USA].[CA]", false);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativeFullNoDefaultHasAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativeFullNoDefaultHasAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.FULL, null, true);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativeFullNoDefaultNoAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativeFullNoDefaultNoAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.FULL, null, false);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativePartialNonAllDefaultHasAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativePartialNonAllDefaultHasAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.PARTIAL, "[Store2].[USA].[CA]", true);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativePartialNonAllDefaultNoAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativePartialNonAllDefaultNoAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.PARTIAL, "[Store2].[USA].[CA]", false);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativePartialNoDefaultHasAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativePartialNoDefaultHasAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.PARTIAL, null, true);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativePartialNoDefaultNoAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativePartialNoDefaultNoAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.PARTIAL, null, false);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativeHiddenNonAllDefaultHasAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativeHiddenNonAllDefaultHasAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.HIDDEN, "[Store2].[USA].[CA]", true);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativeHiddenNonAllDefaultNoAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativeHiddenNonAllDefaultNoAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.HIDDEN, "[Store2].[USA].[CA]", false);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativeHiddenNoDefaultHasAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativeHiddenNoDefaultHasAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.HIDDEN, null, true);
    }

    @Test
    @RolapContextTest(value = AccessControlRollupInstances.WithNativeHiddenNoDefaultNoAll.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_FILTER, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_NON_EMPTY, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.ENABLE_NATIVE_TOP_COUNT, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.EXPAND_NON_NATIVE, value = "true", type = Boolean.class)
    void testRollupPolicyWithNativeHiddenNoDefaultNoAll(@Roles("test") Connection connection) {
        checkRollupPolicyWithNative(connection, RollupPolicy.HIDDEN, null, false);
    }

    private void checkRollupPolicyWithNative(Connection connection, RollupPolicy policy, String defaultMember,
            boolean hasAll) {
        // RolapNativeCrossjoin
        assertThatQuery(connection,
                "select NonEmptyCrossJoin([Store2].[Store State].MEMBERS,"
                + "[Product].[Product Family].MEMBERS) on 0 from tinysales").returnsGrid(
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store2].[Store2].[USA].[CA], [Product].[Product].[Drink]}\n"
                + "{[Store2].[Store2].[USA].[CA], [Product].[Product].[Food]}\n"
                + "{[Store2].[Store2].[USA].[CA], [Product].[Product].[Non-Consumable]}\n"
                + "{[Store2].[Store2].[USA].[OR], [Product].[Product].[Drink]}\n"
                + "{[Store2].[Store2].[USA].[OR], [Product].[Product].[Food]}\n"
                + "{[Store2].[Store2].[USA].[OR], [Product].[Product].[Non-Consumable]}\n"
                + "Row #0: 7,102\n"
                + "Row #0: 53,656\n"
                + "Row #0: 13,990\n"
                + "Row #0: 6,106\n"
                + "Row #0: 48,537\n"
                + "Row #0: 13,016\n");
        // RolapNativeFilter
        assertThatQuery(connection,
                "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
                + "  Filter( [Store2].[USA].children,"
                + "          [Measures].[Unit Sales]>0) ON ROWS \n"
                + "from [TinySales] \n").returnsGrid(
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Store2].[Store2].[USA].[CA]}\n"
                + "{[Store2].[Store2].[USA].[OR]}\n"
                + "Row #0: 74,748\n"
                + "Row #1: 67,659\n");
        // RolapNativeTopCount
        assertThatQuery(connection,
                "select NON EMPTY {[Measures].[Unit Sales]} ON COLUMNS, \n"
                + "  TopCount( [Store2].[USA].children,"
                + "          2) ON ROWS \n"
                + "from [TinySales] \n").returnsGrid(
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Store2].[Store2].[USA].[CA]}\n"
                + "{[Store2].[Store2].[USA].[OR]}\n"
                + "Row #0: 74,748\n"
                + "Row #1: 67,659\n");
    }


    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.AccessControlTestModifier30.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testValidMeasureWithRestrictedCubes(@Roles("noBaseCubes") Connection connection) {
        //http://jira.pentaho.com/browse/MONDRIAN-1616
        assertThatQuery(connection,
            "with member measures.vm as 'validmeasure(measures.[unit sales])' "
            + "select measures.vm on 0 from [warehouse and sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[vm]}\n"
            + "Row #0: 266,773\n");

        assertThatQuery(connection,
            "with member measures.vm as 'validmeasure(measures.[warehouse cost])' "
            + "select measures.vm * {gender.f} on 0 from [warehouse and sales]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[vm], [Gender].[Gender].[F]}\n"
            + "Row #0: 89,043.253\n");
    }

}
