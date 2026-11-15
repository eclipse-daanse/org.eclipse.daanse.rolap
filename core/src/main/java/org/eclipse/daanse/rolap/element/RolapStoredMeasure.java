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

import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.element.StoredMeasure;
import org.eclipse.daanse.rolap.common.star.RolapSqlExpression;

/**
 * A measure which is implemented by a SQL column or SQL expression (as opposed
 * to a {@link RolapCalculatedMember}.
 *
 * Implemented by {@link RolapBaseCubeMeasure} and
 * {@link RolapVirtualCubeMeasure}.
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public interface RolapStoredMeasure extends RolapMeasure, StoredMeasure {

    /**
     * Returns the column which holds the value of the measure.
     */
    RolapSqlExpression getDaanseDefExpression();

    /**
     * Returns the aggregation function which rolls up this measure: "SUM",
     * "COUNT", etc.
     */
    Aggregator getAggregator();

    /**
     * Returns the {@link org.eclipse.daanse.rolap.common.star.RolapStar.Measure} from which this
     * member is computed. Untyped, because another implementation might store
     * it somewhere else.
     */
    Object getStarMeasure();
    
    /**
     * Returns the cube this measure belongs to.
     */
    RolapCube getCube();

    /**
     * What this measure reaches is what its cube joins to: a dimension the cube does not join is one this
     * measure says nothing about.
     */
    @Override
    default java.util.Set<org.eclipse.daanse.olap.api.element.Dimension> dimensionsNotReached(
            org.eclipse.daanse.olap.api.element.Member[] tuple) {
        return getCube().nonJoiningDimensions(tuple);
    }

    /**
     * Whether this measure adds its cells up. Which aggregators do is written down once, in the resolver's
     * own list; here the measure says which aggregator it has.
     */
    @Override
    default boolean addsUp() {
        return org.eclipse.daanse.rolap.common.updateable.SumLikeAggregators.contains(getAggregator());
    }

    /** The cube behind this measure counts its facts. */
    @Override
    default java.util.Optional<org.eclipse.daanse.olap.api.element.Member> factCount() {
        return java.util.Optional.ofNullable(getCube().getFactCountMeasure());
    }

    /**
     * The level of this measure's own cube that answers for the given one.
     * <p>
     * A level that is not one of a cube is one this measure cannot be asked about at all - it belongs to a
     * hierarchy as the catalog declared it, not to a cube's use of that hierarchy - so the answer is that
     * the measure does not reach it, rather than a failure.
     */
    @Override
    default java.util.Optional<org.eclipse.daanse.olap.api.element.Level> levelReached(
            org.eclipse.daanse.olap.api.element.Level level) {
        if (!(level instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel ofACube)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(getCube().findBaseCubeLevel(ofACube));
    }

}
