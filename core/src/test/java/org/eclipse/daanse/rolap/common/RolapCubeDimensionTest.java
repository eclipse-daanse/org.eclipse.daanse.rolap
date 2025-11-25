 /*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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

package org.eclipse.daanse.rolap.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.element.OlapMetaData;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeDimension;
import org.eclipse.daanse.rolap.element.RolapDimension;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapVirtualCube;
import org.eclipse.daanse.rolap.element.TestPublicRolapDimension;
import org.eclipse.daanse.rolap.mapping.model.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RolapCubeDimensionTest {

  private RolapCubeDimension stubRolapCubeDimension(boolean virtualCube) {
    RolapCube cube = mock(RolapVirtualCube.class);

    RolapDimension rolapDim = mock(TestPublicRolapDimension.class);
    List<? extends Hierarchy> rolapDim_hierarchies = new ArrayList<>();
    doReturn(rolapDim_hierarchies).when(rolapDim).getHierarchies();
    doReturn(OlapMetaData.empty()).when(rolapDim).getMetaData();
    
    org.eclipse.daanse.rolap.mapping.model.StandardDimension cubeDim = RolapMappingFactory.eINSTANCE.createStandardDimension();
    cubeDim.setName("StubCubeDimCaption");
    cubeDim.setDescription("StubCubeDimDescription");
    cubeDim.setVisible(true);

    String name = "StubCubeName";
    int cubeOrdinal = 0;
    List<RolapHierarchy> hierarchyList = null;
    org.eclipse.daanse.rolap.mapping.model.DimensionConnector dimensionConnector = RolapMappingFactory.eINSTANCE.createDimensionConnector();
    dimensionConnector.setDimension(cubeDim);
    
    return new RolapCubeDimension(
        cube,
        rolapDim,
        dimensionConnector,
        name,
        cubeOrdinal,
        hierarchyList);
  }

  @Test
  void lookupCubeNull() {
    RolapCubeDimension rcd = stubRolapCubeDimension(false);
    org.eclipse.daanse.rolap.mapping.model.DimensionConnector dimConnector = RolapMappingFactory.eINSTANCE.createDimensionConnector();
      assertThat(rcd.lookupFactCube(dimConnector, null)).isNull();
  }

  @Test
  void lookupCubeNotVirtual() {
    RolapCubeDimension rcd = stubRolapCubeDimension(false);
    org.eclipse.daanse.rolap.mapping.model.DimensionConnector cubeDim = RolapMappingFactory.eINSTANCE.createDimensionConnector();
    RolapCatalog schema = mock(RolapCatalog.class);

      assertThat(rcd.lookupFactCube(cubeDim, schema)).isNull();
    verify(schema, times(0)).lookupCube(anyString());
  }

  @Test
  void lookupCubeNoSuchCube() {
    RolapCubeDimension rcd = stubRolapCubeDimension(false);
    RolapCatalog schema = mock(RolapCatalog.class);
    final String cubeName = "TheCubeName";
    PhysicalCube cube = RolapMappingFactory.eINSTANCE.createPhysicalCube();
    cube.setName(cubeName);
    org.eclipse.daanse.rolap.mapping.model.DimensionConnector dimCon = RolapMappingFactory.eINSTANCE.createDimensionConnector();
    dimCon.setPhysicalCube(cube);

    // explicit doReturn - just to make it evident
    doReturn(null).when(schema).lookupCube(any(org.eclipse.daanse.rolap.mapping.model.Cube.class));

      assertThat(rcd.lookupFactCube(dimCon, schema)).isNull();
    Mockito.verify(schema).lookupCube(cube);
  }

  @Test
  void lookupCubeFound() {
    RolapCubeDimension rcd = stubRolapCubeDimension(false);
    final String cubeName = "TheCubeName";
    PhysicalCube cube = RolapMappingFactory.eINSTANCE.createPhysicalCube();
    cube.setName(cubeName);
    org.eclipse.daanse.rolap.mapping.model.DimensionConnector cubeCon = RolapMappingFactory.eINSTANCE.createDimensionConnector();
    cubeCon.setPhysicalCube(cube);

    RolapCatalog schema = mock(RolapCatalog.class);
    RolapCube factCube = mock(RolapCube.class);
    doReturn(factCube).when(schema).lookupCube(cube);

      assertThat(rcd.lookupFactCube(cubeCon, schema)).isEqualTo(factCube);
  }
}
