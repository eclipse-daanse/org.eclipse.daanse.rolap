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
import org.eclipse.daanse.rolap.common.RolapSqlExpression;

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
    RolapSqlExpression getMondrianDefExpression();

    /**
     * Returns the aggregation function which rolls up this measure: "SUM",
     * "COUNT", etc.
     */
    Aggregator getAggregator();

    /**
     * Returns the {@link org.eclipse.daanse.rolap.common.RolapStar.Measure} from which this
     * member is computed. Untyped, because another implementation might store
     * it somewhere else.
     */
    Object getStarMeasure();
    
    /**
     * Returns the cube this measure belongs to.
     */
    RolapCube getCube();

}
