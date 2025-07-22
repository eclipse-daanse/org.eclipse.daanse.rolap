/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
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


package org.eclipse.daanse.rolap.common;

import org.eclipse.daanse.olap.api.DrillThroughColumn;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.OlapElement;

public class RolapDrillThroughAttribute implements DrillThroughColumn {
    private final Dimension dimension;
    private final Hierarchy hierarchy;
    private final Level level;
    private final RolapProperty property;

    public RolapDrillThroughAttribute(
            Dimension dimension,
            Hierarchy hierarchy,
            Level level,
            RolapProperty property
    ) {
        this.dimension = dimension;
        this.hierarchy = hierarchy;
        this.level = level;
        this.property = property;
    }

    public Dimension getDimension() { return this.dimension; }

    public Hierarchy getHierarchy() { return this.hierarchy; }

    public Level getLevel() { return this.level; }

    @Override
	public OlapElement getOlapElement() {
        if(this.property != null) {
            return this.property;
        }
        if(this.level != null) {
            return this.level;
        }
        if(this.hierarchy != null) {
            return this.hierarchy;
        }
        return this.dimension;
    }
}

