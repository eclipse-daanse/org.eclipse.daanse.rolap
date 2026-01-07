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


package org.eclipse.daanse.rolap.common;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.rolap.common.agg.GroupingSet;

/**
 * The GroupingSetsCollector collects the GroupinpSets and pass
 * the consolidated list to form group by grouping sets sql
 *
 * @author Thiyagu
 * @since 06-Jun-2007
 */
public class GroupingSetsCollector {

    private final boolean useGroupingSets;

    private ArrayList<GroupingSet> groupingSets = new ArrayList<>();

    public GroupingSetsCollector(boolean useGroupingSets) {
        this.useGroupingSets = useGroupingSets;
    }

    public boolean useGroupingSets() {
        return useGroupingSets;
    }

    public void add(GroupingSet aggInfo) {
        assert groupingSets.isEmpty()
            || groupingSets.getFirst().getColumns().length
            >= aggInfo.getColumns().length;
        groupingSets.add(aggInfo);
    }

    public List<GroupingSet> getGroupingSets() {
        return groupingSets;
    }
}
