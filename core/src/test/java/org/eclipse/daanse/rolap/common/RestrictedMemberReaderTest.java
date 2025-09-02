/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2018 Hitachi Vantara..  All rights reserved.
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.access.AccessMember;
import org.eclipse.daanse.olap.api.access.HierarchyAccess;
import org.eclipse.daanse.olap.api.access.Role;
import org.eclipse.daanse.olap.api.element.Catalog;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.element.MultiCardinalityDefaultMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RestrictedMemberReaderTest {

  private RestrictedMemberReader rmr;
  private MemberReader mr;
  private Role role;

  private RolapMember anyRolapMember() {
    return Mockito.any(RolapMember.class);
  }

  private RolapMember mockMember() {
    return Mockito.mock(RolapMember.class);
  }

  @Test
  void testGetHierarchy_allAccess() {
    Catalog schema = Mockito.mock(Catalog.class);
    Dimension dimension = Mockito.mock(Dimension.class);
    RolapHierarchy hierarchy = Mockito.mock(RolapHierarchy.class);
    List<? extends Level> hierarchyAccessLevels = new ArrayList<>();
    hierarchyAccessLevels.add(null);

    MemberReader delegateMemberReader = Mockito.mock(MemberReader.class);
    HierarchyAccess roleAccess = null;
    Role role = Mockito.mock(Role.class);

    Mockito.doReturn(schema).when(dimension).getCatalog();
    Mockito.doReturn(dimension).when(hierarchy).getDimension();
    Mockito.doReturn(hierarchyAccessLevels).when(hierarchy).getLevels();
    Mockito.doReturn(true).when(hierarchy).isRagged();
    Mockito.doReturn(roleAccess).when(role)
        .getAccessDetails(Mockito.any(Hierarchy.class));
    Mockito.doReturn(hierarchy).when(delegateMemberReader).getHierarchy();

    rmr = new RestrictedMemberReader(delegateMemberReader, role);

    assertSame(hierarchy, rmr.getHierarchy());
  }

  @Test
  void testGetHierarchy_roleAccess() {
    RolapHierarchy hierarchy = Mockito.mock(RolapHierarchy.class);
    MemberReader delegateMemberReader = Mockito.mock(MemberReader.class);
    HierarchyAccess roleAccess = Mockito.mock(HierarchyAccess.class);
    Role role = Mockito.mock(Role.class);

    Mockito.doReturn(roleAccess).when(role)
        .getAccessDetails(Mockito.any(Hierarchy.class));
    Mockito.doReturn(hierarchy).when(delegateMemberReader).getHierarchy();

    rmr = new RestrictedMemberReader(delegateMemberReader, role);

    assertSame(hierarchy, rmr.getHierarchy());
  }

  @Test
  void testDefaultMember_allAccess() {
    Catalog schema = Mockito.mock(Catalog.class);
    Dimension dimension = Mockito.mock(Dimension.class);
    RolapHierarchy hierarchy = Mockito.mock(RolapHierarchy.class);
    List<? extends Level> hierarchyAccessLevels = new ArrayList<>();
    hierarchyAccessLevels.add(null);

    MemberReader delegateMemberReader = Mockito.mock(MemberReader.class);
    HierarchyAccess roleAccess = null;
    Role role = Mockito.mock(Role.class);

    Mockito.doReturn(schema).when(dimension).getCatalog();
    Mockito.doReturn(dimension).when(hierarchy).getDimension();
    Mockito.doReturn(hierarchyAccessLevels).when(hierarchy).getLevels();
    Mockito.doReturn(true).when(hierarchy).isRagged();
    Mockito.doReturn(roleAccess).when(role)
        .getAccessDetails(Mockito.any(Hierarchy.class));
    Mockito.doReturn(hierarchy).when(delegateMemberReader).getHierarchy();

    RolapMember hDefaultMember = mockMember();
    Mockito.doReturn(hDefaultMember).when(hierarchy).getDefaultMember();

    rmr = new RestrictedMemberReader(delegateMemberReader, role);

    assertSame(hDefaultMember, rmr.getDefaultMember());
  }

  @Test
  void testDefaultMember_roleAccess() {
    RolapHierarchy hierarchy = Mockito.mock(RolapHierarchy.class);
    MemberReader delegateMemberReader = Mockito.mock(MemberReader.class);
    HierarchyAccess roleAccess = Mockito.mock(HierarchyAccess.class);
    Role role = Mockito.mock(Role.class);
    RolapMember member0 = mockMember();
    List<RolapMember> rootMembers = Arrays.asList(new RolapMember[] {member0});
    RolapMember hierDefaultMember = member0;

    Mockito.doReturn(roleAccess).when(role)
        .getAccessDetails(Mockito.any(Hierarchy.class));
    Mockito.doReturn(hierarchy).when(delegateMemberReader).getHierarchy();

    Mockito.doReturn(hierDefaultMember).when(hierarchy).getDefaultMember();

    rmr = Mockito.spy(new RestrictedMemberReader(delegateMemberReader, role));
    Mockito.doReturn(rootMembers).when(rmr).getRootMembers();

    Mockito.doReturn(null).when(roleAccess).getAccess(anyRolapMember());
    assertSame(hierDefaultMember, rmr.getDefaultMember(), "on Access is null");

    Mockito.doReturn(AccessMember.ALL).when(roleAccess).getAccess(anyRolapMember());
    assertSame(hierDefaultMember, rmr.getDefaultMember(), "on Access.ALL");

    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess)
        .getAccess(anyRolapMember());
    assertSame(hierDefaultMember, rmr.getDefaultMember(), "on Access.CUSTOM");

    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(anyRolapMember());

    assertNotSame(hierDefaultMember, rmr.getDefaultMember(), "on Access.NONE");
    assertTrue(
        rmr.getDefaultMember() instanceof MultiCardinalityDefaultMember);
  }

  @Test
  void testDefaultMember_noDefaultMember_roleAccess() {
    RolapHierarchy hierarchy = Mockito.mock(RolapHierarchy.class);
    MemberReader delegateMemberReader = Mockito.mock(MemberReader.class);
    HierarchyAccess roleAccess = Mockito.mock(HierarchyAccess.class);
    Role role = Mockito.mock(Role.class);
    RolapMember member0 = mockMember();
    List<RolapMember> rootMembers = Arrays.asList(new RolapMember[] {member0});
    RolapMember hierDefaultMember = null;

    Mockito.doReturn(roleAccess).when(role)
        .getAccessDetails(Mockito.any(Hierarchy.class));
    Mockito.doReturn(hierarchy).when(delegateMemberReader).getHierarchy();

    Mockito.doReturn(hierDefaultMember).when(hierarchy).getDefaultMember();

    rmr = Mockito.spy(new RestrictedMemberReader(delegateMemberReader, role));
    Mockito.doReturn(rootMembers).when(rmr).getRootMembers();

    Mockito.doReturn(null).when(roleAccess).getAccess(anyRolapMember());
    assertSame(member0, rmr.getDefaultMember(), "on Access is null");

    Mockito.doReturn(AccessMember.ALL).when(roleAccess).getAccess(anyRolapMember());
    assertSame(member0, rmr.getDefaultMember(), "on Access.ALL");

    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess)
        .getAccess(anyRolapMember());
    assertSame(member0, rmr.getDefaultMember(), "on Access.CUSTOM");

    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(anyRolapMember());

    assertNotSame(member0, rmr.getDefaultMember(), "on Access.NONE");
    assertTrue(
        rmr.getDefaultMember() instanceof MultiCardinalityDefaultMember);
  }

  @Test
  void testDefaultMember_multiRoot() {
    RolapHierarchy hierarchy = Mockito.mock(RolapHierarchy.class);
    MemberReader delegateMemberReader = Mockito.mock(MemberReader.class);
    HierarchyAccess roleAccess = Mockito.mock(HierarchyAccess.class);
    Role role = Mockito.mock(Role.class);
    RolapMember member0 = mockMember();
    RolapMember member1 = mockMember();
    RolapMember member2 = mockMember();
    List<RolapMember> rootMembers = Arrays.asList(
        new RolapMember[] {member0, member1, member2});
    RolapMember hierDefaultMember = member1;

    Mockito.doReturn(roleAccess).when(role)
        .getAccessDetails(Mockito.any(Hierarchy.class));
    Mockito.doReturn(hierarchy).when(delegateMemberReader).getHierarchy();

    Mockito.doReturn(hierDefaultMember).when(hierarchy).getDefaultMember();

    rmr = Mockito.spy(new RestrictedMemberReader(delegateMemberReader, role));
    Mockito.doReturn(rootMembers).when(rmr).getRootMembers();

    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member0);
    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(member1);
    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(member2);
    assertSame(member0, rmr.getDefaultMember(), "on Access C-N-N");

    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(member0);
    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(member1);
    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member2);
    assertSame(member2, rmr.getDefaultMember(), "on Access N-N-C");

    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member0);
    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member1);
    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member2);
    assertSame(hierDefaultMember, rmr.getDefaultMember(), "on Access C-C-C");

    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member0);
    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(member1);
    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member2);
    assertTrue(rmr.getDefaultMember() instanceof MultiCardinalityDefaultMember, "on Access C-N-C");

    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(anyRolapMember());
    assertTrue(rmr.getDefaultMember() instanceof MultiCardinalityDefaultMember, "on Access C-N-C");
  }

  @Test
  void testDefaultMember_multiRootMeasure() {
    RolapHierarchy hierarchy = Mockito.mock(RolapHierarchy.class);
    MemberReader delegateMemberReader = Mockito.mock(MemberReader.class);
    HierarchyAccess roleAccess = Mockito.mock(HierarchyAccess.class);
    Role role = Mockito.mock(Role.class);
    RolapMember member0 = mockMember();
    RolapMember member1 = mockMember();
    RolapMember member2 = mockMember();
    List<RolapMember> rootMembers = Arrays.asList(
        new RolapMember[] {member0, member1, member2});
    RolapMember hierDefaultMember = member1;

    Mockito.doReturn(true).when(member0).isMeasure();
    Mockito.doReturn(true).when(member1).isMeasure();
    Mockito.doReturn(true).when(member1).isMeasure();

    Mockito.doReturn(roleAccess).when(role)
        .getAccessDetails(Mockito.any(Hierarchy.class));
    Mockito.doReturn(hierarchy).when(delegateMemberReader).getHierarchy();

    Mockito.doReturn(hierDefaultMember).when(hierarchy).getDefaultMember();

    rmr = Mockito.spy(new RestrictedMemberReader(delegateMemberReader, role));
    Mockito.doReturn(rootMembers).when(rmr).getRootMembers();

    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member0);
    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(member1);
    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(member2);
    assertSame(member0, rmr.getDefaultMember(), "on Access C-N-N");

    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(member0);
    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(member1);
    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member2);
    assertSame(member2, rmr.getDefaultMember(), "on Access N-N-C");

    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member0);
    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member1);
    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member2);
    assertSame(hierDefaultMember, rmr.getDefaultMember(), "on Access C-C-C");

    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member0);
    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(member1);
    Mockito.doReturn(AccessMember.CUSTOM).when(roleAccess).getAccess(member2);
    assertSame(member0, rmr.getDefaultMember(), "on Access C-N-C");

    Mockito.doReturn(AccessMember.NONE).when(roleAccess).getAccess(anyRolapMember());
    assertTrue(rmr.getDefaultMember() instanceof MultiCardinalityDefaultMember, "on Access.NONE");
  }

  private Cube findCubeByName(Cube[] cc, String cn) {
    for (Cube c : cc) {
      if (cn.equals(c.getName())) {
        return c;
      }
    }
    return null;
  }

  @Test
  void testProcessMemberChildren() {

      MemberReader delegateMemberReader = Mockito.mock(MemberReader.class);
      MemberChildrenConstraint constraint = Mockito.mock(MemberChildrenConstraint.class);
      Role role = Mockito.mock(Role.class);
      Catalog schema = Mockito.mock(Catalog.class);
      Dimension dimension = Mockito.mock(Dimension.class);
      RolapHierarchy hierarchy = Mockito.mock(RolapHierarchy.class);

      List<? extends Level> hierarchyAccessLevels = new ArrayList<>();
      hierarchyAccessLevels.add(null);

      Mockito.doReturn(schema).when(dimension).getCatalog();
      Mockito.doReturn(dimension).when(hierarchy).getDimension();
      Mockito.doReturn(hierarchyAccessLevels).when(hierarchy).getLevels();
      Mockito.doReturn(true).when(hierarchy).isRagged();
      Mockito.doReturn(hierarchy).when(delegateMemberReader).getHierarchy();

      List<RolapMember> children = new ArrayList<>();
      children.add(mockMember());

      List<RolapMember> fullChildren = new ArrayList<>();
      fullChildren.add(mockMember());
      fullChildren.add(mockMember());

      rmr = new RestrictedMemberReader(delegateMemberReader, role);
      final Map<RolapMember, AccessMember> testResult = rmr.processMemberChildren(fullChildren, children, constraint);

      assertEquals(2, testResult.size());
      assertTrue(testResult.containsValue(AccessMember.ALL));
  }
}
