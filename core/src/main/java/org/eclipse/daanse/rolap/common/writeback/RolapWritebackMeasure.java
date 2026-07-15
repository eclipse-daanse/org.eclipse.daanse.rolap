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


package org.eclipse.daanse.rolap.common.writeback;

import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.olap.api.element.Member;

public class RolapWritebackMeasure  extends RolapWritebackColumn {
    private final Member measure;
    private final Datatype datatype;

    public RolapWritebackMeasure(
            Member measure,
            org.eclipse.daanse.cwm.model.cwm.resource.relational.Column column,
            Datatype datatype
    ) {
        super(column);
        this.measure = measure;
        this.datatype = datatype;
    }

    public Member getMeasure() { return this.measure; }

    /**
     * The SQL datatype that should be bound when writing this measure back. Derived
     * from the OLAP measure's aggregator/data type at cube-init time:
     * a {@code TextAggMeasure} (ListAgg aggregator) yields {@link Datatype#VARCHAR};
     * every other measure yields {@link Datatype#NUMERIC}.
     */
    public Datatype getDatatype() { return this.datatype; }
}
