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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
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
import org.eclipse.daanse.rolap.mapping.model.AccessCatalogGrant;
import org.eclipse.daanse.rolap.mapping.model.AccessCubeGrant;
import org.eclipse.daanse.rolap.mapping.model.AccessDimensionGrant;
import org.eclipse.daanse.rolap.mapping.model.AccessHierarchyGrant;
import org.eclipse.daanse.rolap.mapping.model.AccessMemberGrant;
import org.eclipse.daanse.rolap.mapping.model.CatalogAccess;
import org.eclipse.daanse.rolap.mapping.model.CubeAccess;
import org.eclipse.daanse.rolap.mapping.model.DimensionAccess;
import org.eclipse.daanse.rolap.mapping.model.ExplicitHierarchy;
import org.eclipse.daanse.rolap.mapping.model.HierarchyAccess;
import org.eclipse.daanse.rolap.mapping.model.MemberAccess;
import org.eclipse.daanse.rolap.mapping.model.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.PhysicalTable;
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;
import org.eclipse.daanse.rolap.mapping.model.RollupPolicy;
import org.eclipse.daanse.rolap.mapping.model.StandardDimension;
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

    @BeforeEach void beforeEach() {

        schemaSpy = spy(createSchema());
    }

    @AfterEach void afterEach() {
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
    void createUnionRoleThrowsExceptionWhenSchemaGrantsExist() {
    	org.eclipse.daanse.rolap.mapping.model.AccessRole role = RolapMappingFactory.eINSTANCE.createAccessRole();
    	org.eclipse.daanse.rolap.mapping.model.AccessCatalogGrant accessCatalogGrant = RolapMappingFactory.eINSTANCE.createAccessCatalogGrant();
    	role.getAccessCatalogGrants().add(accessCatalogGrant);

        try {
            createSchema().createUnionRole(role);
        } catch (OlapRuntimeException ex) {
            assertDaanseException(
                new RoleUnionGrantsException(), ex);
            return;
        }
        fail("Should fail if union and schema grants exist simultaneously");
    }

    @Test
    void createUnionRoleThrowsExceptionWhenRoleNameIsUnknown() {
        final String roleName = "non-existing role name";
        org.eclipse.daanse.rolap.mapping.model.AccessRole usage = RolapMappingFactory.eINSTANCE.createAccessRole();
        usage.setName(roleName);

        org.eclipse.daanse.rolap.mapping.model.AccessRole role = RolapMappingFactory.eINSTANCE.createAccessRole();
        role.getReferencedAccessRoles().add(usage);

        try {
            createSchema().createUnionRole(role);
        } catch (OlapRuntimeException ex) {
            assertDaanseException(
                new UnknownRoleException(roleName), ex);
            return;
        }
        fail("Should fail if union and schema grants exist simultaneously");
    }


    @Test
    void handleSchemaGrant() {
        RolapCatalog schema = createSchema();
        schema = spy(schema);
        doNothing().when(schema)
            .handleCubeGrant(
                any(RoleImpl.class), any( org.eclipse.daanse.rolap.mapping.model.AccessCubeGrant.class));

        AccessCubeGrant cubeGrant1 = RolapMappingFactory.eINSTANCE.createAccessCubeGrant();
        AccessCubeGrant cubeGrant2 = RolapMappingFactory.eINSTANCE.createAccessCubeGrant();
        
        AccessCatalogGrant grant = RolapMappingFactory.eINSTANCE.createAccessCatalogGrant();
        grant.setCatalogAccess(CatalogAccess.CUSTOM);
        grant.getCubeGrants().add(cubeGrant1);
        grant.getCubeGrants().add(cubeGrant2);

        org.eclipse.daanse.olap.access.RoleImpl role = new org.eclipse.daanse.olap.access.RoleImpl();

        schema.handleCatalogGrant(role, grant);
        assertThat(role.getAccess(schema)).isEqualTo(org.eclipse.daanse.olap.api.access.AccessCatalog.CUSTOM);
        verify(schema, times(2))
            .handleCubeGrant(eq(role), any(AccessCubeGrant.class));
    }


    @Test
    void handleCubeGrantThrowsExceptionWhenCubeIsUnknown() {
        RolapCatalog schema = createSchema();
        schema = spy(schema);
        doReturn(null).when(schema).lookupCube(anyString());

        PhysicalCube cube = RolapMappingFactory.eINSTANCE.createPhysicalCube();
        cube.setName("cube");
        
        AccessCubeGrant grant = RolapMappingFactory.eINSTANCE.createAccessCubeGrant();
        grant.setCube(cube); 

        try {
            schema.handleCubeGrant(new org.eclipse.daanse.olap.access.RoleImpl(), grant);
        } catch (OlapRuntimeException e) {
            String message = e.getMessage();
            assertThat(message.contains(grant.getCube().getName())).as(message).isTrue();
            return;
        }
        fail("Should fail if cube is unknown");
    }

    @Test
    void handleCubeGrantGrantsCubeDimensionsAndHierarchies() {
        RolapCatalog schema = createSchema();
        schema = spy(schema);
        doNothing().when(schema)
            .handleHierarchyGrant(
                any(org.eclipse.daanse.olap.access.RoleImpl.class),
                any(RolapCube.class),
                any(CatalogReader.class),
                any(AccessHierarchyGrant.class));

        final Dimension dimension = mock(Dimension.class);
        CatalogReader reader = mockCatalogReader(org.eclipse.daanse.olap.api.DataType.DIMENSION, dimension);

        RolapCube cube = mockCube(schema);
        when(cube.getCatalogReader(any())).thenReturn(reader);
        doReturn(cube).when(schema).lookupCube(any(org.eclipse.daanse.rolap.mapping.model.Cube.class));

        StandardDimension dim = RolapMappingFactory.eINSTANCE.createStandardDimension();
        dim.setName("dimension");

        AccessDimensionGrant dimensionGrant = RolapMappingFactory.eINSTANCE.createAccessDimensionGrant();

        dimensionGrant.setDimension(dim);
        dimensionGrant.setDimensionAccess(DimensionAccess.NONE);

        PhysicalCube c = RolapMappingFactory.eINSTANCE.createPhysicalCube();
        c.setName("cube");

        AccessCubeGrant grant = RolapMappingFactory.eINSTANCE.createAccessCubeGrant();
        grant.setCube(c);
        grant.setCubeAccess(CubeAccess.CUSTOM);
        
        AccessHierarchyGrant accessHierarchyGrant = RolapMappingFactory.eINSTANCE.createAccessHierarchyGrant();
        
        grant.getDimensionGrants().addAll(List.of(dimensionGrant));
        grant.getHierarchyGrants().addAll(List.of(accessHierarchyGrant));

        org.eclipse.daanse.olap.access.RoleImpl role = new org.eclipse.daanse.olap.access.RoleImpl();

        schema.handleCubeGrant(role, grant);

        assertThat(role.getAccess(cube)).isEqualTo(org.eclipse.daanse.olap.api.access.AccessCube.CUSTOM);
        assertThat(role.getAccess(dimension)).isEqualTo(org.eclipse.daanse.olap.api.access.AccessDimension.NONE);
        verify(schema, times(1))
            .handleHierarchyGrant(
                eq(role),
                eq(cube),
                eq(reader),
                any(AccessHierarchyGrant.class));
    }

    @Test
    void handleHierarchyGrantValidMembers() {
        doTestHandleHierarchyGrant(org.eclipse.daanse.olap.api.access.AccessHierarchy.CUSTOM, org.eclipse.daanse.olap.api.access.AccessMember.ALL);
    }

    @Test
    void handleHierarchyGrantNoValidMembers() {
        doTestHandleHierarchyGrant(org.eclipse.daanse.olap.api.access.AccessHierarchy.NONE, null);
    }

    @Test
    void emptyRolapStarRegistryCreatedForTheNewSchema()
        throws Exception {
      RolapCatalog schema = createSchema();
      RolapStarRegistry rolapStarRegistry = schema.getRolapStarRegistry();
        assertThat(rolapStarRegistry).isNotNull();
        assertThat(rolapStarRegistry.getStars().isEmpty()).isTrue();
    }

    @Test
    void getOrCreateStarStarCreatedAndUsed()
        throws Exception {
      //Create the test fact
    	PhysicalTable table = RolapMappingFactory.eINSTANCE.createPhysicalTable();
    	table.setName("getFactTable())");
    	
    	org.eclipse.daanse.rolap.mapping.model.SqlStatement sqlWhereExpression = RolapMappingFactory.eINSTANCE.createSqlStatement();
    	sqlWhereExpression.getDialects().add("mysql");
    	sqlWhereExpression.setSql("`TableAlias`.`promotion_id` = 112");
    	
    	org.eclipse.daanse.rolap.mapping.model.TableQuery fact = RolapMappingFactory.eINSTANCE.createTableQuery();
    	fact.setTable(table);
    	fact.setAlias("TableAlias");
    	fact.setSqlWhereExpression(sqlWhereExpression);
    	
      List<String> rolapStarKey = RolapUtil.makeRolapStarKey(fact);
      //Expected result star
      RolapStar expectedStar = rlStarMock;
      RolapStarRegistry rolapStarRegistry =
          getStarRegistryLinkedToRolapCatalogSpy(schemaSpy, fact);


      //Test that a new rolap star has created and put to the registry
      RolapStar actualStar = rolapStarRegistry.getOrCreateStar(fact);
        assertThat(actualStar).isSameAs(expectedStar);
        assertThat(rolapStarRegistry.getStars().size()).isEqualTo(1);
        assertThat(rolapStarRegistry.getStar(rolapStarKey)).isEqualTo(expectedStar);
      verify(rolapStarRegistry, times(1)).makeRolapStar(fact);
      //test that no new rolap star has created,
      //but extracted already existing one from the registry
      RolapStar actualStar2 = rolapStarRegistry.getOrCreateStar(fact);
      verify(rolapStarRegistry, times(1)).makeRolapStar(fact);
        assertThat(actualStar2).isSameAs(expectedStar);
        assertThat(rolapStarRegistry.getStars().size()).isEqualTo(1);
        assertThat(rolapStarRegistry.getStar(rolapStarKey)).isEqualTo(expectedStar);
    }

    @Test
    void getStarFromRegistryByStarKey() throws Exception {
      //Create the test fact
    	
    	PhysicalTable table = RolapMappingFactory.eINSTANCE.createPhysicalTable();
    	table.setName(getFactTable());
    	
    	org.eclipse.daanse.rolap.mapping.model.SqlStatement sqlWhereExpression = RolapMappingFactory.eINSTANCE.createSqlStatement();
    	sqlWhereExpression.getDialects().add("mysql");
    	sqlWhereExpression.setSql("`TableAlias`.`promotion_id` = 112");

    	org.eclipse.daanse.rolap.mapping.model.TableQuery fact = RolapMappingFactory.eINSTANCE.createTableQuery();
    	fact.setTable(table);
    	fact.setAlias("TableAlias");
    	fact.setSqlWhereExpression(sqlWhereExpression);
    
      List<String> rolapStarKey = RolapUtil.makeRolapStarKey(fact);
      //Expected result star
      RolapStarRegistry rolapStarRegistry =
          getStarRegistryLinkedToRolapCatalogSpy(schemaSpy, fact);
      //Put rolap star to the registry
      rolapStarRegistry.getOrCreateStar(fact);

      RolapStar actualStar = rolapStarRegistry.getStar(rolapStarKey);
        assertThat(actualStar).isSameAs(rlStarMock);
    }

    @Test
    void getStarFromRegistryByFactTableName() throws Exception {
      //Create the test fact
    	
    	PhysicalTable table = RolapMappingFactory.eINSTANCE.createPhysicalTable();
    	table.setName("getFactTable())");
    	
    	org.eclipse.daanse.rolap.mapping.model.TableQuery fact = RolapMappingFactory.eINSTANCE.createTableQuery();
    	fact.setTable(table);
    	fact.setAlias("TableAlias");

      //Expected result star
      RolapStarRegistry rolapStarRegistry =
          getStarRegistryLinkedToRolapCatalogSpy(schemaSpy, fact);
      //Put rolap star to the registry
      RolapStar actualStar = rolapStarRegistry.getOrCreateStar(fact);

        //RolapStar actualStar = schemaSpy.getRolapStarRegistry().getStar(RelationUtil.getAlias(fact));
        assertThat(actualStar).isSameAs(rlStarMock);
    }

    private static RolapStarRegistry getStarRegistryLinkedToRolapCatalogSpy(
        RolapCatalog schemaSpy, org.eclipse.daanse.rolap.mapping.model.RelationalQuery fact) throws Exception
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

        AccessMemberGrant memberGrant = RolapMappingFactory.eINSTANCE.createAccessMemberGrant();
        memberGrant.setMember("member");
        memberGrant.setMemberAccess(MemberAccess.ALL);

        ExplicitHierarchy h = RolapMappingFactory.eINSTANCE.createExplicitHierarchy();
        AccessHierarchyGrant grant = RolapMappingFactory.eINSTANCE.createAccessHierarchyGrant();
        grant.setHierarchyAccess(HierarchyAccess.CUSTOM);
        grant.setRollupPolicy(RollupPolicy.FULL);
        grant.setHierarchy(h);
        grant.getMemberGrants().add(memberGrant);

        Level level = mock(Level.class);
        Hierarchy hierarchy = mock(Hierarchy.class);
        when(hierarchy.getLevels()).thenAnswer(setupDummyListAnswer(level));
        when(level.getHierarchy()).thenReturn(hierarchy);
        when(cube.lookupHierarchy(any(org.eclipse.daanse.rolap.mapping.model.Hierarchy.class))).thenReturn(hierarchy);
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
        assertThat(role.getAccess(hierarchy)).isEqualTo(expectedHierarchyAccess);
        if (expectedMemberAccess != null) {
            assertThat(role.getAccess(member)).isEqualTo(expectedMemberAccess);
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

    private void assertDaanseException(
        Exception expected,
        Exception actual)
    {
        assertThat(actual.getMessage()).isEqualTo(expected.getMessage());
    }
}
