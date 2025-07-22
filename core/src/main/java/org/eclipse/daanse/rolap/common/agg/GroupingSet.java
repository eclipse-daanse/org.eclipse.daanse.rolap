/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 Julian Hyde
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


package org.eclipse.daanse.rolap.common.agg;

import java.util.List;

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;

/**
 * A collection
 * of {@link org.eclipse.daanse.rolap.common.agg.Segment}s that can be represented
 * as a GROUP BY GROUPING SET in a SQL query.
 *
 * @author Thiyagu
 * @since 05-Jun-2007
 */
public class GroupingSet {
    private final List<Segment> segments;
    final Segment segment0;
    private final BitKey levelBitKey;
    private final BitKey measureBitKey;
    private final StarColumnPredicate[] predicates;
    private final SegmentAxis[] axes;
    private final RolapStar.Column[] columns;

    /**
     * Creates a GroupingSet.
     *
     * @param segments Constituent segments
     * @param levelBitKey Levels
     * @param measureBitKey Measures
     * @param predicates Predicates
     * @param columns Columns
     */
    public GroupingSet(
        List<Segment> segments,
        BitKey levelBitKey,
        BitKey measureBitKey,
        StarColumnPredicate[] predicates,
        RolapStar.Column[] columns)
    {
        this.segments = segments;
        this.segment0 = segments.get(0);
        this.levelBitKey = levelBitKey;
        this.measureBitKey = measureBitKey;
        this.predicates = predicates;
        this.axes = new SegmentAxis[predicates.length];
        this.columns = columns;
    }


    public List<Segment> getSegments() {
        return segments;
    }

    public BitKey getLevelBitKey() {
        return levelBitKey;
    }

    public BitKey getMeasureBitKey() {
        return measureBitKey;
    }

    public SegmentAxis[] getAxes() {
        return axes;
    }

    public StarColumnPredicate[] getPredicates() {
        return predicates;
    }

    public RolapStar.Column[] getColumns() {
        return columns;
    }

    /**
     * Sets all the segments which are in loading state as failed
     */
    public void setSegmentsFailed() {
        for (Segment segment : segments) {
            // TODO: segment.setFailIfStillLoading();
        }
    }
}
