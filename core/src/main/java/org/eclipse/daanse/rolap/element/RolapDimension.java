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
 * jhyde, 10 August, 2001
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
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.DimensionType;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.common.DimensionBase;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.element.OlapMetaData;
import org.eclipse.daanse.olap.exceptions.NonTimeLevelInTimeHierarchyException;
import org.eclipse.daanse.olap.exceptions.TimeLevelInNonTimeHierarchyException;
import org.eclipse.daanse.rolap.common.MemberReader;
import org.eclipse.daanse.rolap.common.RolapEvaluator;
import org.eclipse.daanse.rolap.common.util.DimensionTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RolapDimension implements {@link Dimension}for a ROLAP
 * database.
 *
 * Topic: Dimension ordinals
 *
 * {@link RolapEvaluator} needs each dimension to have an ordinal, so that it
 * can store the evaluation context as an array of members.
 *
 * 
 * A dimension may be either shared or private to a particular cube. The
 * dimension object doesn't actually know which; {@link Catalog} has a list of
 * shared hierarchies Catalog#getSharedHierarchies, and {@link Cube}
 * has a list of dimensions ({@link Cube#getDimensions}).
 *
 * 
 * If a dimension is shared between several cubes, the {@link Dimension}objects
 * which represent them may (or may not be) the same. (That's why there's no
 * getCube() method.)
 *
 * 
 * Furthermore, since members are created by a {@link MemberReader}which
 * belongs to the {@link RolapHierarchy}, you will the members will be the same
 * too. For example, if you query [Product].[Beer] from the
 * Sales and Warehouse cubes, you will get the
 * same {@link RolapMember}object.
 * ({@link RolapCatalog#mapSharedHierarchyToReader} holds the mapping. I don't
 * know whether it's still necessary.)
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public class RolapDimension extends DimensionBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(RolapDimension.class);

    private final Catalog schema;
    private final MetaData metaData;

    public RolapDimension(
        Catalog schema,
        String name,
        String caption,
        boolean visible,
        String description,
        DimensionType dimensionType,
        MetaData metadata)
    {
        // todo: recognition of a time dimension should be improved
        // allow multiple time dimensions
        super(
            name,
            caption,
            visible,
            description,
            dimensionType);
        assert metadata != null;
        this.schema = schema;
        this.metaData = metadata;
        this.hierarchies = new ArrayList<>();
    }

    /**
     * Creates a dimension from an XML definition.
     *
     *  schema != null
     */
    public RolapDimension(
        RolapCatalog schema,
        RolapCube cube,
        org.eclipse.daanse.rolap.mapping.model.Dimension mappingDimension,
        org.eclipse.daanse.rolap.mapping.model.DimensionConnector dimensionConnector)
    {
        this(
            schema,
            getDimensionName(dimensionConnector),
            getDimensionName(dimensionConnector),
            dimensionConnector.isVisible(),
            mappingDimension != null ? mappingDimension.getDescription() : null,
            DimensionTypeUtil.getDimensionType(mappingDimension),
            RolapMetaData.createMetaData(mappingDimension != null ? mappingDimension.getAnnotations() : null));

        Util.assertPrecondition(schema != null);

        if (cube != null) {
            Util.assertTrue(cube.getCatalog() == schema);
        }

        if (mappingDimension != null) {
        	this.hierarchies = new ArrayList<>();
        	for (int i = 0; i < mappingDimension.getHierarchies().size(); i++) {
        		RolapHierarchy hierarchy = new RolapHierarchy(
        				cube, this, mappingDimension.getHierarchies().get(i), dimensionConnector);
        		hierarchies.add(hierarchy);
        	}
        }        

        // if there was no dimension type assigned, determine now.
        if (dimensionType == null) {
            for (Hierarchy h : hierarchies) {
                List<? extends Level> levels = h.getLevels();
                LevLoop:
                for (Level lev : levels) {
                    if (lev.isAll()) {
                        continue LevLoop;
                    }
                    if (dimensionType == null) {
                        // not set yet - set it according to current level
                        dimensionType = (lev.getLevelType().isTime())
                            ? DimensionType.TIME_DIMENSION
                            : isMeasures()
                            ? DimensionType.MEASURES_DIMENSION
                            : DimensionType.STANDARD_DIMENSION;

                    } else {
                        // Dimension type was set according to first level.
                        // Make sure that other levels fit to definition.
                        if (dimensionType == DimensionType.TIME_DIMENSION
                            && !lev.getLevelType().isTime()
                            && !lev.isAll())
                        {
                            throw new NonTimeLevelInTimeHierarchyException(
                                    getUniqueName());
                        }
                        if (dimensionType != DimensionType.TIME_DIMENSION
                            && lev.getLevelType().isTime())
                        {
                            throw new TimeLevelInNonTimeHierarchyException(
                                    getUniqueName());
                        }
                    }
                }
            }
        }
    }

    public static String getDimensionName(org.eclipse.daanse.rolap.mapping.model.DimensionConnector mappingCubeDimension) {
        return  mappingCubeDimension.getOverrideDimensionName() != null ? mappingCubeDimension.getOverrideDimensionName() : mappingCubeDimension.getDimension().getName();
    }

    @Override
	protected Logger getLogger() {
        return LOGGER;
    }

    /**
     * Initializes a dimension within the context of a cube.
     */
    void init(org.eclipse.daanse.rolap.mapping.model.DimensionConnector mappingDimension) {
        for (Hierarchy h : hierarchies) {
            if (h != null) {
                ((RolapHierarchy) h).init(mappingDimension);
            }
        }
    }

    /**
     * Creates a hierarchy.
     *
     * @param subName Name of this hierarchy.
     * @param hasAll Whether hierarchy has an 'all' member
     * @param closureFor Hierarchy for which the new hierarchy is a closure;
     *     null for regular hierarchies
     * @return Hierarchy
     */
    RolapHierarchy newHierarchy(
        String subName,
        boolean hasAll,
        RolapHierarchy closureFor)
    {
        RolapHierarchy hierarchy =
            new RolapHierarchy(
                this, subName,
                caption, visible, description, null, hasAll, closureFor,
                OlapMetaData.empty());
        this.hierarchies.add(hierarchy);
        return hierarchy;
    }

    /**
     * Returns the hierarchy of an expression.
     *
     * In this case, the expression is a dimension, so the hierarchy is the
     * dimension's default hierarchy (its first).
     */
    @Override
	public Hierarchy getHierarchy() {
        return hierarchies.getFirst();
    }

    @Override
	public Catalog getCatalog() {
        return schema;
    }

    @Override
	public MetaData getMetaData() {
        return metaData;
    }

    @Override
    protected int computeHashCode() {
      if (isMeasuresDimension()) {
        return System.identityHashCode(this);
      }
      return super.computeHashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
    if (!(o instanceof RolapDimension that)) {
        return false;
    }
      if (isMeasuresDimension()) {
        return this == that;
      }
      return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    private boolean isMeasuresDimension() {
      return this.getDimensionType() == DimensionType.MEASURES_DIMENSION;
    }

    @Override
    public Cube getCube() {
        return null;
    }

}
