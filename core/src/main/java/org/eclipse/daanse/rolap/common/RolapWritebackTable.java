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

import java.util.ArrayList;
import java.util.List;

public class RolapWritebackTable {
    private final String name;
    private final String schema;
    private List<RolapWritebackColumn> columnList;

    public RolapWritebackTable (
            String name,
            String schema,
            List<RolapWritebackColumn> columnList
    ) {
        this.name = name;
        this.schema = schema;
        this.columnList = columnList;
        if(this.columnList == null) {
            this.columnList = new ArrayList<>();
        }
    }

    public String getName() { return this.name; }

    public String getSchema() { return this.schema; }

    public List<RolapWritebackColumn> getColumns() {
        return this.columnList;
    }

}
