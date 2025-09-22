/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2015-2017 Hitachi Vantara..  All rights reserved.
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

package org.eclipse.daanse.rolap.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.daanse.olap.access.RoleImpl;
import org.eclipse.daanse.olap.api.CatalogReader;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DataType;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.exceptions.RoleUnionGrantsException;
import org.eclipse.daanse.olap.exceptions.UnknownRoleException;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.agg.SegmentCacheManager;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.mapping.api.model.AccessCubeGrantMapping;
import org.eclipse.daanse.rolap.mapping.api.model.AccessHierarchyGrantMapping;
import org.eclipse.daanse.rolap.mapping.api.model.CubeMapping;
import org.eclipse.daanse.rolap.mapping.api.model.HierarchyMapping;
import org.eclipse.daanse.rolap.mapping.api.model.RelationalQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.enums.AccessCatalog;
import org.eclipse.daanse.rolap.mapping.api.model.enums.AccessCube;
import org.eclipse.daanse.rolap.mapping.api.model.enums.AccessDimension;
import org.eclipse.daanse.rolap.mapping.api.model.enums.AccessHierarchy;
import org.eclipse.daanse.rolap.mapping.api.model.enums.AccessMember;
import org.eclipse.daanse.rolap.mapping.api.model.enums.RollupPolicyType;
import org.eclipse.daanse.rolap.mapping.pojo.AccessCatalogGrantMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.AccessCubeGrantMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.AccessDimensionGrantMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.AccessHierarchyGrantMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.AccessMemberGrantMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.AccessRoleMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.ExplicitHierarchyMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.HierarchyMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.PhysicalCubeMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.PhysicalTableMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.SqlStatementMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.StandardDimensionMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.TableQueryMappingImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;


/**
 * @author Andrey Khayrutdinov
 */
class RolapCatalogTest {
  private RolapCatalog schemaSpy;
  private static RolapStar rlStarMock = mock(RolapStar.class);
  private static AbstractRolapContext contextMock;

    @BeforeEach
    public void beforeEach() {

        schemaSpy = spy(createSchema());
    }

    @AfterEach
    public void afterEach() {
        SystemWideProperties.instance().populateInitial();
    }

    private RolapCatalog createSchema() {
        RolapCatalogKey key = new RolapCatalogKey(
            new RolapCatalogContentKey("test", 1), new ConnectionKey(1, "1"));

        //noinspection deprecation
        //mock rolap connection to eliminate calls for cache loading
        contextMock = mock(AbstractRolapContext.class);
        Connection rolapConnectionMock = mock(Connection.class);
        AggregationManager aggManagerMock = mock(AggregationManager.class);
        SegmentCacheManager scManagerMock = mock(SegmentCacheManager.class);
        Context context = contextMock;
        when(rolapConnectionMock.getContext()).thenReturn(context);
        when(contextMock.getAggregationManager()).thenReturn(aggManagerMock);
        //when(contextMock.getConfig()).thenReturn(new TestConfig());
        when(contextMock.getConfigValue(ConfigConstants.ENABLE_NATIVE_CROSS_JOIN, ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE, Boolean.class)).thenReturn(true);
        when(contextMock.getConfigValue(ConfigConstants.ENABLE_NATIVE_TOP_COUNT, ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE, Boolean.class)).thenReturn(true);
        when(contextMock.getConfigValue(ConfigConstants.ENABLE_NATIVE_FILTER, ConfigConstants.ENABLE_NATIVE_FILTER_DEFAULT_VALUE, Boolean.class)).thenReturn(true);
        when(aggManagerMock.getCacheMgr(rolapConnectionMock)).thenReturn(scManagerMock);
        return new RolapCatalog(key,  rolapConnectionMock, contextMock);
    }

    private CatalogReader mockCatalogReader(DataType category, OlapElement element) {
        CatalogReader reader = mock(CatalogReader.class);
        when(reader.withLocus()).thenReturn(reader);
        when(reader.lookupCompound(
            any(OlapElement.class), anyList(),
            anyBoolean(), eq(category)))
            .thenReturn(element);
        return reader;
    }

    private RolapCube mockCube(RolapCatalog schema) {
        RolapCube cube = mock(RolapCube.class);
        when(cube.getCatalog()).thenReturn(schema);
        return cube;
    }

    @Test
    void testCreateUnionRole_ThrowsException_WhenSchemaGrantsExist() {
    	AccessRoleMappingImpl role = AccessRoleMappingImpl.builder()
    			.withAccessCatalogGrants(List.of(
    					AccessCatalogGrantMappingImpl.builder().build()))
    			.withReferencedAccessRoles(List.of())
    			.build();

        try {
            createSchema().createUnionRole(role);
        } catch (OlapRuntimeException ex) {
            assertMondrianException(
                new RoleUnionGrantsException(), ex);
            return;
        }
        fail("Should fail if union and schema grants exist simultaneously");
    }

    @Test
    void testCreateUnionRole_ThrowsException_WhenRoleNameIsUnknown() {
        final String roleName = "non-existing role name";
        AccessRoleMappingImpl usage = AccessRoleMappingImpl.builder()
        		.withName(roleName)
        		.build();

        AccessRoleMappingImpl role = AccessRoleMappingImpl.builder().build();
        role.setReferencedAccessRoles(List.of(usage));

        try {
            createSchema().createUnionRole(role);
        } catch (OlapRuntimeException ex) {
            assertMondrianException(
                new UnknownRoleException(roleName), ex);
            return;
        }
        fail("Should fail if union and schema grants exist simultaneously");
    }


    @Test
    void testHandleSchemaGrant() {
        RolapCatalog schema = createSchema();
        schema = spy(schema);
        doNothing().when(schema)
            .handleCubeGrant(
                any(RoleImpl.class), any( AccessCubeGrantMapping.class));

        AccessCatalogGrantMappingImpl grant = AccessCatalogGrantMappingImpl.builder()
        		.withAccess(AccessCatalog.CUSTOM)
        		.withCubeGrant(null)
        		.build();

        grant.setCubeGrant(List.of(AccessCubeGrantMappingImpl.builder().build(), AccessCubeGrantMappingImpl.builder().build()));

        org.eclipse.daanse.olap.access.RoleImpl role = new org.eclipse.daanse.olap.access.RoleImpl();

        schema.handleCatalogGrant(role, grant);
        assertEquals(org.eclipse.daanse.olap.api.access.AccessCatalog.CUSTOM, role.getAccess(schema));
        verify(schema, times(2))
            .handleCubeGrant(eq(role), any(AccessCubeGrantMapping.class));
    }


    @Test
    void testHandleCubeGrant_ThrowsException_WhenCubeIsUnknown() {
        RolapCatalog schema = createSchema();
        schema = spy(schema);
        doReturn(null).when(schema).lookupCube(anyString());

        AccessCubeGrantMappingImpl grant = AccessCubeGrantMappingImpl.builder().build();
        grant.setCube(PhysicalCubeMappingImpl.builder().withName("cube").build());

        try {
            schema.handleCubeGrant(new org.eclipse.daanse.olap.access.RoleImpl(), grant);
        } catch (OlapRuntimeException e) {
            String message = e.getMessage();
            assertTrue(message.contains(grant.getCube().getName()), message);
            return;
        }
        fail("Should fail if cube is unknown");
    }

    @Test
    void testHandleCubeGrant_GrantsCubeDimensionsAndHierarchies() {
        RolapCatalog schema = createSchema();
        schema = spy(schema);
        doNothing().when(schema)
            .handleHierarchyGrant(
                any(org.eclipse.daanse.olap.access.RoleImpl.class),
                any(RolapCube.class),
                any(CatalogReader.class),
                any(AccessHierarchyGrantMappingImpl.class));

        final Dimension dimension = mock(Dimension.class);
        CatalogReader reader = mockCatalogReader(org.eclipse.daanse.olap.api.DataType.DIMENSION, dimension);

        RolapCube cube = mockCube(schema);
        when(cube.getCatalogReader(any())).thenReturn(reader);
        doReturn(cube).when(schema).lookupCube(any(CubeMapping.class));

        AccessDimensionGrantMappingImpl dimensionGrant =
        		AccessDimensionGrantMappingImpl.builder().build();
        dimensionGrant.setDimension(StandardDimensionMappingImpl.builder().withName("dimension").build());
        dimensionGrant.setAccess(AccessDimension.NONE);

        AccessCubeGrantMappingImpl grant = AccessCubeGrantMappingImpl.builder().build();
        grant.setCube(PhysicalCubeMappingImpl.builder().withName("cube").build());
        grant.setAccess(AccessCube.CUSTOM);
        grant.getDimensionGrants().addAll(List.of(dimensionGrant));
        grant.getHierarchyGrants().addAll(List.of(AccessHierarchyGrantMappingImpl.builder().build()));

        org.eclipse.daanse.olap.access.RoleImpl role = new org.eclipse.daanse.olap.access.RoleImpl();

        schema.handleCubeGrant(role, grant);

        assertEquals(org.eclipse.daanse.olap.api.access.AccessCube.CUSTOM, role.getAccess(cube));
        assertEquals(org.eclipse.daanse.olap.api.access.AccessDimension.NONE, role.getAccess(dimension));
        verify(schema, times(1))
            .handleHierarchyGrant(
                eq(role),
                eq(cube),
                eq(reader),
                any(AccessHierarchyGrantMapping.class));
    }

    @Test
    void testHandleHierarchyGrant_ValidMembers() {
        doTestHandleHierarchyGrant(org.eclipse.daanse.olap.api.access.AccessHierarchy.CUSTOM, org.eclipse.daanse.olap.api.access.AccessMember.ALL);
    }

    @Test
    void testHandleHierarchyGrant_NoValidMembers() {
        doTestHandleHierarchyGrant(org.eclipse.daanse.olap.api.access.AccessHierarchy.NONE, null);
    }

    @Test
    void testEmptyRolapStarRegistryCreatedForTheNewSchema()
        throws Exception {
      RolapCatalog schema = createSchema();
      RolapStarRegistry rolapStarRegistry = schema.getRolapStarRegistry();
      assertNotNull(rolapStarRegistry);
      assertTrue(rolapStarRegistry.getStars().isEmpty());
    }

    @Test
    void testGetOrCreateStar_StarCreatedAndUsed()
        throws Exception {
      //Create the test fact
      RelationalQueryMapping fact = TableQueryMappingImpl.builder()
      		  .withTable(((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("getFactTable())")).build())
      		  .withAlias("TableAlias")
      		  .withSqlWhereExpression(SqlStatementMappingImpl.builder()
      				  .withDialects(List.of("mysql"))
      				  .withSql("`TableAlias`.`promotion_id` = 112")
      				  .build())
      		  .build();
      List<String> rolapStarKey = RolapUtil.makeRolapStarKey(fact);
      //Expected result star
      RolapStar expectedStar = rlStarMock;
      RolapStarRegistry rolapStarRegistry =
          getStarRegistryLinkedToRolapCatalogSpy(schemaSpy, fact);


      //Test that a new rolap star has created and put to the registry
      RolapStar actualStar = rolapStarRegistry.getOrCreateStar(fact);
      assertSame(expectedStar, actualStar);
      assertEquals(1, rolapStarRegistry.getStars().size());
      assertEquals(expectedStar, rolapStarRegistry.getStar(rolapStarKey));
      verify(rolapStarRegistry, times(1)).makeRolapStar(fact);
      //test that no new rolap star has created,
      //but extracted already existing one from the registry
      RolapStar actualStar2 = rolapStarRegistry.getOrCreateStar(fact);
      verify(rolapStarRegistry, times(1)).makeRolapStar(fact);
      assertSame(expectedStar, actualStar2);
      assertEquals(1, rolapStarRegistry.getStars().size());
      assertEquals(expectedStar, rolapStarRegistry.getStar(rolapStarKey));
    }

    @Test
    void testGetStarFromRegistryByStarKey() throws Exception {
      //Create the test fact
      RelationalQueryMapping fact = TableQueryMappingImpl.builder()
    		  .withTable(((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("getFactTable())")).build())
    		  .withAlias("TableAlias")
    		  .withSqlWhereExpression(SqlStatementMappingImpl.builder()
    				  .withDialects(List.of("mysql"))
    				  .withSql("`TableAlias`.`promotion_id` = 112")
    				  .build())
    		  .build();
      List<String> rolapStarKey = RolapUtil.makeRolapStarKey(fact);
      //Expected result star
      RolapStarRegistry rolapStarRegistry =
          getStarRegistryLinkedToRolapCatalogSpy(schemaSpy, fact);
      //Put rolap star to the registry
      rolapStarRegistry.getOrCreateStar(fact);

      RolapStar actualStar = rolapStarRegistry.getStar(rolapStarKey);
      assertSame(rlStarMock, actualStar);
    }

    @Test
    void testGetStarFromRegistryByFactTableName() throws Exception {
      //Create the test fact
      RelationalQueryMapping fact = TableQueryMappingImpl.builder()
    		  .withTable(((PhysicalTableMappingImpl.Builder) PhysicalTableMappingImpl.builder().withName("getFactTable())")).build())
    		  .withAlias("TableAlias")
    		  .build();

      //Expected result star
      RolapStarRegistry rolapStarRegistry =
          getStarRegistryLinkedToRolapCatalogSpy(schemaSpy, fact);
      //Put rolap star to the registry
      RolapStar actualStar = rolapStarRegistry.getOrCreateStar(fact);

      //RolapStar actualStar = schemaSpy.getRolapStarRegistry().getStar(RelationUtil.getAlias(fact));
      assertSame(rlStarMock, actualStar);
    }

    private static RolapStarRegistry getStarRegistryLinkedToRolapCatalogSpy(
        RolapCatalog schemaSpy, RelationalQueryMapping fact) throws Exception
    {
      //the rolap star registry is linked to the origin rolap schema,
      //not to the schemaSpy
      //RolapStarRegistry rolapStarRegistry = schemaSpy.getRolapStarRegistry();
      RolapStarRegistry rolapStarRegistry = spy(new RolapStarRegistry(schemaSpy, contextMock));
      //the star mock
      doReturn(rlStarMock).when(rolapStarRegistry).makeRolapStar(fact);
      //Set the schema spy to be linked with the rolap star registry
      //assertTrue(
      //        replaceRolapCatalogLinkedToStarRegistry(
      //        rolapStarRegistry,
      //        schemaSpy),
      //        "For testing purpose object this$0 in the inner class "
      //                + "should be replaced to the rolap schema spy "
      //                + "but this not happend");
      //verify(rolapStarRegistry, times(0)).makeRolapStar(fact);
      return rolapStarRegistry;
    }

     private static boolean replaceRolapCatalogLinkedToStarRegistry(
         RolapStarRegistry innerClass,
         RolapCatalog sSpy) throws Exception
     {
       Field field = innerClass.getClass().getDeclaredField("this$0");
       if (field != null) {
         field.setAccessible(true);
         field.set(innerClass, sSpy);
         RolapCatalog outerMocked = (RolapCatalog) field.get(innerClass);
         return outerMocked == sSpy;
       }
       return false;
      }

    private static String getFactTableWithSQLFilter() {
      String fact =
          "<Table name=\"sales_fact_1997\" alias=\"TableAlias\">\n"
          + " <SQL dialect=\"mysql\">\n"
          + "     `TableAlias`.`promotion_id` = 112\n"
          + " </SQL>\n"
          + "</Table>";
      return fact;
    }

    private static String getFactTable() {
      String fact =
          "<Table name=\"sales_fact_1997\" alias=\"TableAlias\"/>";
      return fact;
    }


    private void doTestHandleHierarchyGrant(
        org.eclipse.daanse.olap.api.access.AccessHierarchy expectedHierarchyAccess,
        org.eclipse.daanse.olap.api.access.AccessMember expectedMemberAccess)
    {
        RolapCatalog schema = createSchema();
        RolapCube cube = mockCube(schema);
        org.eclipse.daanse.olap.access.RoleImpl role = new org.eclipse.daanse.olap.access.RoleImpl();

        AccessMemberGrantMappingImpl memberGrant = AccessMemberGrantMappingImpl.builder().withMember("member").withAccess(AccessMember.ALL).build();

        HierarchyMappingImpl h = ExplicitHierarchyMappingImpl.builder().build();
        AccessHierarchyGrantMappingImpl grant = AccessHierarchyGrantMappingImpl.builder().build();
        grant.setAccess(AccessHierarchy.CUSTOM);
        grant.setRollupPolicyType(RollupPolicyType.FULL);
        grant.setHierarchy(h);
        grant.setMemberGrants(List.of(memberGrant));

        Level level = mock(Level.class);
        Hierarchy hierarchy = mock(Hierarchy.class);
        when(hierarchy.getLevels()).thenAnswer(setupDummyListAnswer(level));
        when(level.getHierarchy()).thenReturn(hierarchy);
        when(cube.lookupHierarchy(any(HierarchyMapping.class))).thenReturn(hierarchy);
        Dimension dimension = mock(Dimension.class);
        when(hierarchy.getDimension()).thenReturn(dimension);

        CatalogReader reader = mockCatalogReader(DataType.HIERARCHY, hierarchy);
        Context context = mock(Context.class);
        //TestConfig config = new TestConfig();
        //config.setIgnoreInvalidMembers(true);
        when(context.getConfigValue(ConfigConstants.IGNORE_INVALID_MEMBERS, ConfigConstants.IGNORE_INVALID_MEMBERS_DEFAULT_VALUE, Boolean.class)).thenReturn(true);
        //when(context.getConfig()).thenReturn(config);
        when(reader.getContext()).thenReturn(context);


        Member member = mock(Member.class);
        when(member.getHierarchy()).thenReturn(hierarchy);
        when(member.getLevel()).thenReturn(level);

        if (expectedMemberAccess != null) {
            when(reader.getMemberByUniqueName(
                anyList(), anyBoolean())).thenReturn(member);
        }

        schema.handleHierarchyGrant(role, cube, reader, grant);
        assertEquals(expectedHierarchyAccess, role.getAccess(hierarchy));
        if (expectedMemberAccess != null) {
            assertEquals(expectedMemberAccess, role.getAccess(member));
        }
    }

    private static  <N> Answer<List<N>> setupDummyListAnswer(N... values) {
        final List<N> someList = new LinkedList<>(Arrays.asList(values));

        Answer<List<N>> answer = new Answer<>() {
            @Override
              public List<N> answer(InvocationOnMock invocation) throws Throwable {
                return someList;
            }
        };
        return answer;
    }

    private void assertMondrianException(
        Exception expected,
        Exception actual)
    {
        assertEquals(expected.getMessage(), actual.getMessage());
    }
}
