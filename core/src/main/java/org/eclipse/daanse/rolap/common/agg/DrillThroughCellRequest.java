/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara.
 * Copyright (C) 2021 Sergei Semenkov
 * All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.daanse.olap.api.element.OlapElement;
import org.eclipse.daanse.rolap.common.RolapStar;

/**
 * Subclass of {@link CellRequest} that allows to specify
 * which columns and measures to return as part of the ResultSet
 * which we return to the client.
 */
public class DrillThroughCellRequest extends CellRequest {

    private final List<RolapStar.Column> drillThroughColumns =
        new ArrayList<>();

    private final List<RolapStar.Measure> drillThroughMeasures =
        new ArrayList<>();
    private final List<OlapElement> nonApplicableMembers;

    public DrillThroughCellRequest(
        RolapStar.Measure measure,
        boolean extendedContext, List<OlapElement> nonApplicableFields)
    {
        super(measure, extendedContext, true);
        this.nonApplicableMembers = nonApplicableFields;
    }

    private int maxRowCount;

    public void setMaxRowCount(int maxRowCount) {
        this.maxRowCount = maxRowCount;
    }

    public int getMaxRowCount() {
        return this.maxRowCount;
    }

    public void addDrillThroughColumn(RolapStar.Column column) {
        this.drillThroughColumns.add(column);
    }

    public boolean includeInSelect(RolapStar.Column column) {
        if (drillThroughColumns.isEmpty()
            && drillThroughMeasures.isEmpty())
        {
            return true;
        }
        return drillThroughColumns.contains(column);
    }

    public void addDrillThroughMeasure(RolapStar.Measure measure) {
        this.drillThroughMeasures.add(measure);
    }

    public boolean includeInSelect(RolapStar.Measure measure) {
        if (drillThroughColumns.isEmpty()
            && drillThroughMeasures.isEmpty())
        {
            return true;
        }
        return drillThroughMeasures.contains(measure);
    }

    public List<RolapStar.Measure> getDrillThroughMeasures() {
        return Collections.unmodifiableList(drillThroughMeasures);
    }

    public List<OlapElement> getNonApplicableMembers() {
        return nonApplicableMembers;
    }
}
