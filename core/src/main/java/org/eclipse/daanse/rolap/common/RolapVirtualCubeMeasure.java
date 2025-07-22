/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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

import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.element.MetaData;
import org.eclipse.daanse.olap.api.element.VirtualCubeMeasure;

/**
 * Measure which is defined in a virtual cube, and based on a stored measure
 * in one of the virtual cube's base cubes.
 *
 * @author jhyde
 * @since Aug 18, 2006
 */
public class RolapVirtualCubeMeasure
    extends RolapMemberBase
    implements RolapStoredMeasure, VirtualCubeMeasure
{
    /**
     * The measure in the underlying cube.
     */
    private final RolapStoredMeasure cubeMeasure;
    private final MetaData metaData;

    public RolapVirtualCubeMeasure(
        RolapMember parentMember,
        RolapLevel level,
        RolapStoredMeasure cubeMeasure,
        MetaData metaData)
    {
        super(parentMember, level, cubeMeasure.getName());
        this.cubeMeasure = cubeMeasure;
        this.metaData = metaData;
    }

    @Override
	public Object getPropertyValue(String propertyName, boolean matchCase) {
        // Look first in this member (against the virtual cube), then
        // fallback on the base measure.
        // This allows, for instance, a measure to be invisible in a virtual
        // cube but visible in its base cube.
        Object value = super.getPropertyValue(propertyName, matchCase);
        if (value == null) {
            value = cubeMeasure.getPropertyValue(propertyName, matchCase);
        }
        return value;
    }

    @Override
	public RolapCube getCube() {
        return cubeMeasure.getCube();
    }

    @Override
	public Object getStarMeasure() {
        return cubeMeasure.getStarMeasure();
    }

    @Override
	public RolapSqlExpression getMondrianDefExpression() {
        return cubeMeasure.getMondrianDefExpression();
    }

    @Override
	public Aggregator getAggregator() {
        return cubeMeasure.getAggregator();
    }

    @Override
	public RolapResult.ValueFormatter getFormatter() {
        return cubeMeasure.getFormatter();
    }

    @Override
	public MetaData getMetaData() {
        return metaData;
    }

    @Override
    public String getAggregateFunction() {
        return getAggregator().getName();
    }
}
