/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * Copyright (C) 2021 Sergei Semenkov
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

package org.eclipse.daanse.rolap.element;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.api.element.Catalog;
import org.eclipse.daanse.olap.api.element.DimensionType;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.common.HierarchyBase;

/**
 * RolapCubeDimension wraps a RolapDimension for a specific Cube.
 *
 * @author Will Gorman, 19 October 2007
 */
public class RolapCubeDimension extends RolapDimension {

    RolapCube cube;

    RolapDimension rolapDimension;
    int cubeOrdinal;
    org.eclipse.daanse.rolap.mapping.model.DimensionConnector xmlDimension;

    /**
     * Creates a RolapCubeDimension.
     *
     * @param cube Cube
     * @param rolapDim Dimension wrapped by this dimension
     * @param cubeDim XML element definition
     * @param name Name of dimension
     * @param cubeOrdinal Ordinal of dimension within cube
     * @param hierarchyList hierarchyList
     */
    public RolapCubeDimension(
        RolapCube cube,
        RolapDimension rolapDim,
        org.eclipse.daanse.rolap.mapping.model.DimensionConnector cubeDim,
        String name,
        int cubeOrdinal,
        List<RolapHierarchy> hierarchyList)
    {
        super(
            null,
            name,
            cubeDim.getOverrideDimensionName() != null
                ? cubeDim.getOverrideDimensionName()
                : rolapDim.getCaption(),
            cubeDim.isVisible(),
            cubeDim.getDimension() != null
                ? cubeDim.getDimension().getDescription()
                : rolapDim.getDescription(),
            null,
            (cubeDim.getDimension() != null && !cubeDim.getDimension().getAnnotations().isEmpty())
                ? RolapMetaData.createMetaData(cubeDim.getDimension().getAnnotations())
                : rolapDim.getMetaData());
        this.xmlDimension = cubeDim;
        this.rolapDimension = rolapDim;
        this.cubeOrdinal = cubeOrdinal;
        this.cube = cube;
        this.caption = cubeDim.getOverrideDimensionName();

        // create new hierarchies
        hierarchies = new ArrayList<>();

        RolapCube factCube = null;
        if (cube instanceof RolapVirtualCube) {
          factCube = lookupFactCube(cubeDim, cube.getCatalog());
        }
        for (Hierarchy h : rolapDim.getHierarchies()) {
            final RolapCubeHierarchy cubeHierarchy =
                new RolapCubeHierarchy(
                    this,
                    cubeDim,
                    (RolapHierarchy) h,
                    ((HierarchyBase) h).getSubName(),
                    hierarchyList.size(),
                    factCube);
            hierarchies.add(cubeHierarchy);
            hierarchyList.add(cubeHierarchy);
        }
    }

    public RolapCube lookupFactCube(
    		org.eclipse.daanse.rolap.mapping.model.DimensionConnector cubeDim, RolapCatalog schema)
    {

      if (cubeDim.getPhysicalCube() != null && cubeDim.getPhysicalCube().getName() != null) {
          return schema.lookupCube(cubeDim.getPhysicalCube());
      }
      return null;
    }

    public RolapCube getCube() {
        return cube;
    }

    @Override
	public Catalog getCatalog() {
        return rolapDimension.getCatalog();
    }

    // this method should eventually replace the call below
    public int getOrdinal() {
        return cubeOrdinal;
    }

    @Override
	public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RolapCubeDimension that)) {
            return false;
        }

        if (!cube.equalsOlapElement(that.cube)) {
            return false;
        }
        return getUniqueName().equals(that.getUniqueName());
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
	RolapCubeHierarchy newHierarchy(
        String subName, boolean hasAll, RolapHierarchy closureFor)
    {
        throw new UnsupportedOperationException();
    }

    @Override
	public String getCaption() {
        if (caption != null) {
            return caption;
        }
        return this.name;
    }

    @Override
	public void setCaption(String caption) {
        if (true) {
            throw new UnsupportedOperationException();
        }
        rolapDimension.setCaption(caption);
    }

    @Override
	public DimensionType getDimensionType() {
        return rolapDimension.getDimensionType();
    }

    public org.eclipse.daanse.rolap.mapping.model.DimensionConnector getDimensionConnector() {
        return xmlDimension;
    }

}
